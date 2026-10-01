package traincraft.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TC_BATCH7_R4B_ACTUAL_LICENSED_TRAIN_AUDIO_VOLUME_REBALANCE
 *
 * Client-side state-aware audio owner for the long Batch 7 licensed recordings.
 *
 * Why client-side:
 * - long recordings must not be re-fired every 15/40 ticks
 * - EntityBoundSoundInstance follows the moving train
 * - completed recordings are restarted only after they actually finish
 * - state changes (idle/run/accel/decel) swap cleanly without overlap
 * - the accepted r3a interior attenuation is mirrored for these direct
 *   SoundManager instances and is refreshed immediately on board/dismount
 *
 * Horns/whistles remain server-authoritative through the existing H-key packet.
 *
 * No movement, path, coupling, traction, boiler, inventory, or track math is
 * changed here.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRollingStockAudioEvents {

    private static final double HEAR_RADIUS = 96.0D;
    private static final double RUN_THRESHOLD = 0.030D;
    private static final double RAIL_THRESHOLD = 0.050D;

    private static final Map<Integer, Active> PROPULSION = new HashMap<>();
    private static final Map<Integer, Active> RAIL = new HashMap<>();
    private static final Map<Integer, Active> STEAM_VENT = new HashMap<>();
    private static final Map<Integer, Double> LAST_SPEED = new HashMap<>();

    private static long clientTicks;
    private static Object lastLevel;
    private static float lastListenerMultiplier = 1.0F;

    private ClientRollingStockAudioEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        clientTicks++;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopAll(minecraft);
            lastLevel = null;
            return;
        }

        if (lastLevel != minecraft.level) {
            stopAll(minecraft);
            lastLevel = minecraft.level;
        }

        float listenerMultiplier = listenerMultiplier(minecraft.player);
        if (Math.abs(listenerMultiplier - lastListenerMultiplier) > 0.001F) {
            /*
             * EntityBoundSoundInstance volume is fixed when created. Restart the
             * managed layers once on board/dismount so r3a-style muffling takes
             * effect immediately for long streamed recordings too.
             */
            stopAllManaged(minecraft);
            lastListenerMultiplier = listenerMultiplier;
        }

        AABB box = minecraft.player.getBoundingBox().inflate(HEAR_RADIUS);
        List<Entity> nearby = new ArrayList<>(
                minecraft.level.getEntities(
                        minecraft.player,
                        box,
                        ClientRollingStockAudioEvents::isRollingStock));

        Entity ridden = minecraft.player.getVehicle();
        if (ridden != null && isRollingStock(ridden) && !nearby.contains(ridden)) {
            nearby.add(ridden);
        }

        Set<Integer> seen = new HashSet<>();

        for (Entity entity : nearby) {
            int entityId = entity.getId();
            seen.add(entityId);

            String id = registryPath(entity);
            if (id == null) {
                continue;
            }

            double speed = horizontalSpeed(entity);
            double oldSpeed = LAST_SPEED.getOrDefault(entityId, speed);
            LAST_SPEED.put(entityId, speed);
            double deltaSpeed = speed - oldSpeed;

            Choice propulsion = propulsionChoice(entity, id, speed, deltaSpeed);
            updateManaged(
                    minecraft,
                    PROPULSION,
                    entity,
                    propulsion,
                    listenerMultiplier);

            Choice rail = railChoice(entity, id, speed);
            updateManaged(
                    minecraft,
                    RAIL,
                    entity,
                    rail,
                    listenerMultiplier);

            Choice vent = steamVentChoice(entity);
            updateManaged(
                    minecraft,
                    STEAM_VENT,
                    entity,
                    vent,
                    listenerMultiplier);
        }

        cleanupNotSeen(minecraft, PROPULSION, seen);
        cleanupNotSeen(minecraft, RAIL, seen);
        cleanupNotSeen(minecraft, STEAM_VENT, seen);

        Iterator<Integer> speedIt = LAST_SPEED.keySet().iterator();
        while (speedIt.hasNext()) {
            if (!seen.contains(speedIt.next())) {
                speedIt.remove();
            }
        }
    }

    private static Choice propulsionChoice(
            Entity entity,
            String id,
            double speed,
            double deltaSpeed) {

        if (entity instanceof LegacyPoweredLocomotive powered) {
            if (powered.getPowerAmount() <= 0) {
                return null;
            }

            if (id.startsWith("locomotive_diesel_") || id.equals("tracks_builder")) {
                if (id.equals("locomotive_diesel_cd742")) {
                    return speed > RUN_THRESHOLD
                            ? choice("cd742-run", TCSounds.DIESEL_742_MOTOR.get(), 0.88F, dieselPitch(speed))
                            : choice("cd742-idle", TCSounds.DIESEL_742_SLOW.get(), 0.72F, 1.0F);
                }

                if (id.equals("locomotive_diesel_chme3")) {
                    return speed > RUN_THRESHOLD
                            ? choice("chme3-run", TCSounds.CHME3_RUN.get(), 0.88F, dieselPitch(speed))
                            : choice("chme3-idle", TCSounds.CHME3_IDLE.get(), 0.72F, 1.0F);
                }

                if (id.equals("locomotive_diesel_kof")
                        || id.equals("locomotive_diesel_v60")
                        || id.equals("tracks_builder")) {
                    return speed > RUN_THRESHOLD
                            ? choice("db-small-run-" + speedBand(speed),
                                    TCSounds.B7_DIESEL_DB_KLEINLOK_MOTOR.get(),
                                    0.92F,
                                    dieselPitch(speed))
                            : choice("db-small-idle",
                                    TCSounds.B7_DIESEL_DB_KLEINLOK_MOTOR.get(),
                                    0.75F,
                                    0.88F);
                }

                return speed > RUN_THRESHOLD
                        ? choice("diesel-run-" + speedBand(speed),
                                TCSounds.B7_DIESEL_INDUSTRIAL_RUN.get(),
                                0.92F,
                                dieselPitch(speed))
                        : choice("diesel-idle",
                                TCSounds.B7_DIESEL_INDUSTRIAL_IDLE.get(),
                                0.75F,
                                1.0F);
            }

            if (id.equals("locomotive_electric_vl10")) {
                return speed > RUN_THRESHOLD
                        ? choice("vl10-run-" + speedBand(speed),
                                TCSounds.VL10_RUN.get(),
                                0.82F,
                                electricPitch(speed))
                        : choice("vl10-idle",
                                TCSounds.VL10_IDLE.get(),
                                0.62F,
                                1.0F);
            }

            if (id.equals("locomotive_electric_tram")) {
                return speed > RUN_THRESHOLD
                        ? choice("tram-classic",
                                TCSounds.B7_TRAM_CLASSIC.get(),
                                0.82F,
                                electricPitch(speed))
                        : null;
            }

            if (id.equals("locomotive_electric_tram_ny")) {
                return speed > RUN_THRESHOLD
                        ? choice("tram-102n",
                                TCSounds.B7_TRAM_102N.get(),
                                0.82F,
                                electricPitch(speed))
                        : null;
            }

            if (id.startsWith("locomotive_electric_")) {
                if (speed <= RUN_THRESHOLD) {
                    return null;
                }

                if (deltaSpeed > 0.0015D) {
                    return choice("electric-accelerating",
                            TCSounds.B7_ELECTRIC_ACCELERATION.get(),
                            0.85F,
                            electricPitch(speed));
                }

                if (deltaSpeed < -0.0015D) {
                    return choice("electric-decelerating",
                            TCSounds.B7_ELECTRIC_DECELERATION.get(),
                            0.80F,
                            electricPitch(speed));
                }

                return choice("electric-cruise-" + speedBand(speed),
                        TCSounds.B7_ELECTRIC_RUN.get(),
                        0.78F,
                        electricPitch(speed));
            }

            return null;
        }

        if (entity instanceof SmallSteamLocomotive steam) {
            if (speed > RUN_THRESHOLD && steam.getSteamAmount() > 0) {
                return choice(
                        "steam-run-" + speedBand(speed),
                        TCSounds.B7_STEAM_RUN_ANNA6.get(),
                        0.92F,
                        steamPitch(speed));
            }

            if (steam.getBurnTime() > 0) {
                return choice(
                        "steam-idle-air-pump",
                        TCSounds.B7_STEAM_AIR_PUMP.get(),
                        0.60F,
                        1.0F);
            }
        }

        return null;
    }

    private static Choice steamVentChoice(Entity entity) {
        if (entity instanceof LegacyPoweredLocomotive) {
            return null;
        }

        if (entity instanceof SmallSteamLocomotive steam
                && steam.isBoilerOverheating()) {
            return choice(
                    "steam-overheat-release",
                    TCSounds.B7_STEAM_RELEASE.get(),
                    0.90F,
                    1.0F);
        }

        return null;
    }

    private static Choice railChoice(Entity entity, String id, double speed) {
        if (speed <= RAIL_THRESHOLD) {
            return null;
        }

        float baseVolume = id.startsWith("locomotive_") || id.equals("tracks_builder")
                ? 0.36F
                : 0.46F;

        return choice(
                "rail-" + speedBand(speed),
                TCSounds.B7_RAIL_WHEELS.get(),
                baseVolume,
                railPitch(speed));
    }

    private static void updateManaged(
            Minecraft minecraft,
            Map<Integer, Active> map,
            Entity entity,
            Choice choice,
            float listenerMultiplier) {

        int entityId = entity.getId();
        Active active = map.get(entityId);
        SoundManager soundManager = minecraft.getSoundManager();

        if (choice == null) {
            if (active != null) {
                soundManager.stop(active.sound);
                map.remove(entityId);
            }
            return;
        }

        if (active != null && !active.key.equals(choice.key)) {
            soundManager.stop(active.sound);
            map.remove(entityId);
            active = null;
        }

        if (active != null) {
            /*
             * A short grace period avoids duplicate starts while the streaming
             * channel is being attached to the SoundEngine.
             */
            if (soundManager.isActive(active.sound)
                    || clientTicks - active.startedTick <= 12L) {
                return;
            }

            map.remove(entityId);
        }

        float volume = choice.volume * listenerMultiplier;

        EntityBoundSoundInstance sound = new EntityBoundSoundInstance(
                choice.event,
                SoundSource.NEUTRAL,
                volume,
                choice.pitch,
                entity,
                (((long) entityId) << 32) ^ clientTicks);

        soundManager.play(sound);
        map.put(entityId, new Active(choice.key, sound, clientTicks));
    }

    private static void cleanupNotSeen(
            Minecraft minecraft,
            Map<Integer, Active> map,
            Set<Integer> seen) {

        Iterator<Map.Entry<Integer, Active>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Active> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                minecraft.getSoundManager().stop(entry.getValue().sound);
                it.remove();
            }
        }
    }

    private static void stopAllManaged(Minecraft minecraft) {
        if (minecraft == null) {
            PROPULSION.clear();
            RAIL.clear();
            STEAM_VENT.clear();
            return;
        }

        SoundManager soundManager = minecraft.getSoundManager();

        for (Active active : PROPULSION.values()) {
            soundManager.stop(active.sound);
        }
        for (Active active : RAIL.values()) {
            soundManager.stop(active.sound);
        }
        for (Active active : STEAM_VENT.values()) {
            soundManager.stop(active.sound);
        }

        PROPULSION.clear();
        RAIL.clear();
        STEAM_VENT.clear();
    }

    private static void stopAll(Minecraft minecraft) {
        stopAllManaged(minecraft);
        LAST_SPEED.clear();
        lastListenerMultiplier = 1.0F;
    }

    private static Choice choice(
            String key,
            SoundEvent event,
            float volume,
            float pitch) {
        return new Choice(key, event, volume, pitch);
    }

    private static String speedBand(double speed) {
        if (speed < 0.10D) {
            return "low";
        }
        if (speed < 0.24D) {
            return "mid";
        }
        return "high";
    }

    private static float dieselPitch(double speed) {
        return clampPitch(0.88D + speed * 0.95D, 0.88D, 1.18D);
    }

    private static float electricPitch(double speed) {
        return clampPitch(0.92D + speed * 0.75D, 0.92D, 1.20D);
    }

    private static float steamPitch(double speed) {
        return clampPitch(0.90D + speed * 0.70D, 0.90D, 1.16D);
    }

    private static float railPitch(double speed) {
        return clampPitch(0.88D + speed * 0.85D, 0.88D, 1.18D);
    }

    private static float clampPitch(double value, double min, double max) {
        return (float) Math.max(min, Math.min(max, value));
    }

    private static double horizontalSpeed(Entity entity) {
        double x = entity.getDeltaMovement().x;
        double z = entity.getDeltaMovement().z;
        return Math.sqrt(x * x + z * z);
    }

    private static String registryPath(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !Traincraft.MOD_ID.equals(key.getNamespace())) {
            return null;
        }
        return key.getPath();
    }

    private static boolean isRollingStock(Entity entity) {
        String path = registryPath(entity);
        if (path == null) {
            return false;
        }

        return path.startsWith("locomotive_")
                || path.startsWith("steam_tender")
                || path.equals("tracks_builder")
                || path.equals("freight_cart")
                || path.equals("caboose")
                || path.equals("passenger_coach_blue")
                || path.equals("livestock_car")
                || path.equals("mob_transport_car")
                || path.endsWith("_passenger_car")
                || path.endsWith("_baggage_car")
                || path.endsWith("_observatory_car")
                || path.endsWith("_tail_car")
                || path.endsWith("_coach")
                || path.endsWith("_car");
    }

    private static float listenerMultiplier(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            return 1.0F;
        }

        String path = registryPath(vehicle);
        if (path == null) {
            return 1.0F;
        }

        if (path.equals("tracks_builder")) {
            return 0.58F;
        }

        if (path.startsWith("locomotive_")) {
            return 0.56F;
        }

        if (path.equals("caboose")
                || path.equals("passenger_coach_blue")
                || path.endsWith("_passenger_car")
                || path.endsWith("_baggage_car")
                || path.endsWith("_observatory_car")
                || path.endsWith("_tail_car")
                || path.endsWith("_coach")) {
            return 0.42F;
        }

        if (path.startsWith("steam_tender")
                || path.equals("freight_cart")
                || path.equals("livestock_car")
                || path.equals("mob_transport_car")) {
            return 0.72F;
        }

        return 0.60F;
    }

    private record Choice(
            String key,
            SoundEvent event,
            float volume,
            float pitch) {
    }

    private static final class Active {
        private final String key;
        private final EntityBoundSoundInstance sound;
        private final long startedTick;

        private Active(
                String key,
                EntityBoundSoundInstance sound,
                long startedTick) {
            this.key = key;
            this.sound = sound;
            this.startedTick = startedTick;
        }
    }
}