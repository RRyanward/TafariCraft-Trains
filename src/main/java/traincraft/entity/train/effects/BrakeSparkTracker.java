/*
 * TafariCraft - Trains Final Cleanup Batch 5 r1f16b
 * TC_BRAKE_SPARKS_R1F16
 * TC_HANDBRAKE_BURST_R1F16B
 *
 * Visual-only braking sparks:
 *  - Space = sustained service-brake sparks above 4 km/h.
 *  - C = dense four-tick parking-brake burst captured from pre-stop speed.
 * Electric locomotives retain their separate live-Energy-Track contact sparks.
 */
package traincraft.entity.train.effects;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BrakeSparkTracker {
    private static final double MIN_SPARK_SPEED_BPT = 4.0D / 72.0D;
    private static final int HAND_BRAKE_BURST_TICKS = 4;

    private static final Map<UUID, SmallSteamLocomotive> ACTIVE_BRAKES = new HashMap<>();
    private static final Map<UUID, HandBrakeBurst> HAND_BRAKE_BURSTS = new HashMap<>();

    private BrakeSparkTracker() {
    }

    private static final class HandBrakeBurst {
        private final SmallSteamLocomotive locomotive;
        private final double capturedSpeedKmh;
        private int ticksRemaining;

        private HandBrakeBurst(SmallSteamLocomotive locomotive, double capturedSpeedKmh) {
            this.locomotive = locomotive;
            this.capturedSpeedKmh = capturedSpeedKmh;
            this.ticksRemaining = HAND_BRAKE_BURST_TICKS;
        }
    }

    public static void setBrakeState(SmallSteamLocomotive locomotive, boolean brakeHeld) {
        if (locomotive == null || locomotive.level().isClientSide) {
            return;
        }

        if (!supportsBrakeSparks(locomotive)) {
            ACTIVE_BRAKES.remove(locomotive.getUUID());
            return;
        }

        if (brakeHeld) {
            ACTIVE_BRAKES.put(locomotive.getUUID(), locomotive);
        } else {
            ACTIVE_BRAKES.remove(locomotive.getUUID());
        }
    }

    public static void triggerHandBrakeBurst(SmallSteamLocomotive locomotive) {
        if (locomotive == null
                || locomotive.level().isClientSide
                || !supportsBrakeSparks(locomotive)
                || locomotive.isHandBrakeApplied()) {
            return;
        }

        Vec3 motion = locomotive.getDeltaMovement();
        double speedBpt = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speedBpt < MIN_SPARK_SPEED_BPT) {
            return;
        }

        HAND_BRAKE_BURSTS.put(
                locomotive.getUUID(),
                new HandBrakeBurst(locomotive, speedBpt * 72.0D));
    }

    private static boolean supportsBrakeSparks(SmallSteamLocomotive locomotive) {
        if (locomotive instanceof LegacyPoweredLocomotive powered) {
            return powered.isDiesel();
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickServiceBrakeSparks();
        tickHandBrakeBursts();
    }

    private static void tickServiceBrakeSparks() {
        if (ACTIVE_BRAKES.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, SmallSteamLocomotive>> it =
                ACTIVE_BRAKES.entrySet().iterator();

        while (it.hasNext()) {
            SmallSteamLocomotive locomotive = it.next().getValue();

            if (locomotive == null
                    || !locomotive.isAlive()
                    || locomotive.isRemoved()
                    || locomotive.getFirstPassenger() == null
                    || !(locomotive.level() instanceof ServerLevel server)
                    || !supportsBrakeSparks(locomotive)) {
                it.remove();
                continue;
            }

            Vec3 motion = locomotive.getDeltaMovement();
            double speedBpt = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            if (speedBpt < MIN_SPARK_SPEED_BPT) {
                continue;
            }

            double speedKmh = speedBpt * 72.0D;
            int interval = speedKmh >= 18.0D ? 1 : 2;
            if (locomotive.tickCount % interval != 0) {
                continue;
            }

            int particlesPerSide =
                    speedKmh >= 50.0D ? 3 :
                    speedKmh >= 30.0D ? 2 : 1;

            double along = ((locomotive.tickCount / interval) & 1) == 0 ? 0.72D : -0.72D;
            emitWheelGroup(server, locomotive, along, particlesPerSide, 0.10D, 0.018D);
        }
    }

    private static void tickHandBrakeBursts() {
        if (HAND_BRAKE_BURSTS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, HandBrakeBurst>> it =
                HAND_BRAKE_BURSTS.entrySet().iterator();

        while (it.hasNext()) {
            HandBrakeBurst burst = it.next().getValue();
            SmallSteamLocomotive locomotive = burst.locomotive;

            if (locomotive == null
                    || !locomotive.isAlive()
                    || locomotive.isRemoved()
                    || !(locomotive.level() instanceof ServerLevel server)
                    || !supportsBrakeSparks(locomotive)
                    || burst.ticksRemaining <= 0) {
                it.remove();
                continue;
            }

            int particlesPerPoint =
                    burst.capturedSpeedKmh >= 40.0D ? 7 :
                    burst.capturedSpeedKmh >= 20.0D ? 6 : 5;

            emitWheelGroup(server, locomotive,  0.78D, particlesPerPoint, 0.14D, 0.035D);
            emitWheelGroup(server, locomotive, -0.78D, particlesPerPoint, 0.14D, 0.035D);

            burst.ticksRemaining--;
            if (burst.ticksRemaining <= 0) {
                it.remove();
            }
        }
    }

    private static void emitWheelGroup(
            ServerLevel server,
            SmallSteamLocomotive locomotive,
            double along,
            int particlesPerSide,
            double spread,
            double speed) {

        double yaw = Math.toRadians(locomotive.getTrainFacingYaw());
        double frontX = Math.sin(yaw);
        double frontZ = -Math.cos(yaw);
        double sideX = Math.cos(yaw);
        double sideZ = Math.sin(yaw);

        double gaugeSide = 0.43D;
        double y = locomotive.getY() + 0.10D;

        for (int sideSign : new int[] { -1, 1 }) {
            double x = locomotive.getX()
                    + frontX * along
                    + sideX * gaugeSide * sideSign;
            double z = locomotive.getZ()
                    + frontZ * along
                    + sideZ * gaugeSide * sideSign;

            server.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    x,
                    y,
                    z,
                    particlesPerSide,
                    spread,
                    0.035D,
                    spread,
                    speed);
        }
    }
}