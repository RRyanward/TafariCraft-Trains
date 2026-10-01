package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 9.3g-t7-rs2-h1
 *
 * Dedicated-server validation for rail-safe heavy rolling-stock collision rules.
 *
 * Tests the exact push(Entity) hook with:
 *  - Forge FakePlayer (player type)
 *  - vanilla cow (passive animal)
 *  - vanilla zombie (hostile mob)
 *
 * For every current rolling-stock family:
 *  - parked stock must acquire zero horizontal drift/speed from LivingEntity pushes;
 *  - normal setDeltaMovement control must remain writable;
 *  - while already moving, a cow collision must leave the vehicle's own motion
 *    unchanged and must displace the cow instead.
 *
 * DIAGNOSTIC ONLY.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7Rs2LivingEntityShoveHeadlessCommand {
    private static final String ENV = "TC_T7_RS2_LIVING_SHOVE_HEADLESS";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final double EPS_POS = 1.0E-5D;
    private static final double EPS_SPEED = 1.0E-6D;
    private static final double CONTROL_SPEED = 0.18D;
    private static final int SETTLE_TICKS = 4;
    private static final int COLLISION_ROUNDS = 32;

    private static final List<String> PROBES = List.of(
            "locomotive_steam_small",
            "steam_tender",
            "freight_cart",
            "passenger_coach_blue",
            "caboose"
    );

    private TrackT7Rs2LivingEntityShoveHeadlessCommand() {
    }

    private record CollisionResult(
            boolean pass,
            double drift,
            double finalHorizontalSpeed,
            String reason) {
    }

    private record ProbeResult(
            String id,
            boolean pass,
            CollisionResult player,
            CollisionResult cow,
            CollisionResult zombie,
            boolean controlMotionWriteAccepted,
            boolean movingVehicleMotionPreserved,
            boolean movingCowPushedAway,
            double movingVehicleSpeedBefore,
            double movingVehicleSpeedAfter,
            double movingCowSpeedAfter,
            String reason) {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String enabled = System.getenv(ENV);
        if (!"1".equals(enabled) && !"true".equalsIgnoreCase(enabled)) {
            return;
        }

        MinecraftServer server = event.getServer();

        try {
            run(server);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                writeFailureReport(t);
            } catch (Throwable ignored) {
            }
            server.halt(false);
        }
    }

    private static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();

        int railY = Math.max(
                level.getMinBuildHeight() + 12,
                Math.min(level.getMaxBuildHeight() - 12, spawn.getY() + 24));

        BlockPos origin = new BlockPos(
                spawn.getX() + 192,
                railY,
                spawn.getZ() + 192);

        ChunkPos chunk = new ChunkPos(origin);
        level.setChunkForced(chunk.x, chunk.z, true);

        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        List<ProbeResult> results = new ArrayList<>();

        try {
            for (int i = 0; i < PROBES.size(); i++) {
                BlockPos railCenter = origin.offset(0, 0, i * 4);
                results.add(testProbe(
                        level,
                        fakePlayer,
                        railCenter,
                        PROBES.get(i)));
            }

            Path report = writeReport(results);
            long passCount = results.stream().filter(ProbeResult::pass).count();

            System.out.println(
                    "[TC-T7-RS2-H1] complete pass="
                            + passCount + "/" + results.size()
                            + " report=" + report);

        } finally {
            cleanup(level, origin);
            level.setChunkForced(chunk.x, chunk.z, false);
        }

        server.halt(false);
    }

    private static ProbeResult testProbe(
            ServerLevel level,
            FakePlayer fakePlayer,
            BlockPos center,
            String entityId) {

        buildFlatRail(level, center);

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("traincraft", entityId));

        if (type == null) {
            return failedProbe(entityId, "MISSING_ENTITY_TYPE");
        }

        Entity raw = type.create(level);
        if (!(raw instanceof AbstractMinecart probe)) {
            if (raw != null) {
                raw.discard();
            }
            return failedProbe(entityId, "NOT_ABSTRACT_MINECART");
        }

        LivingEntity cow = EntityType.COW.create(level);
        LivingEntity zombie = EntityType.ZOMBIE.create(level);

        if (cow == null || zombie == null) {
            probe.discard();
            if (cow != null) cow.discard();
            if (zombie != null) zombie.discard();
            return failedProbe(entityId, "VANILLA_LIVING_ENTITY_CREATE_FAILED");
        }

        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.0625D;
        double z = center.getZ() + 0.5D;

        probe.moveTo(x, y, z, 90.0F, 0.0F);
        probe.setDeltaMovement(Vec3.ZERO);

        if (!level.addFreshEntity(probe)) {
            probe.discard();
            cow.discard();
            zombie.discard();
            return failedProbe(entityId, "ADD_ENTITY_FAILED");
        }

        try {
            fakePlayer.setPos(x, y, z);
            cow.moveTo(x, y, z, 0.0F, 0.0F);
            zombie.moveTo(x, y, z, 0.0F, 0.0F);

            CollisionResult playerResult =
                    stationaryCollisionTest(level, probe, fakePlayer, x, y, z);

            CollisionResult cowResult =
                    stationaryCollisionTest(level, probe, cow, x, y, z);

            CollisionResult zombieResult =
                    stationaryCollisionTest(level, probe, zombie, x, y, z);

            resetProbe(level, probe, x, y, z);

            probe.setDeltaMovement(CONTROL_SPEED, 0.0D, 0.0D);
            Vec3 control = probe.getDeltaMovement();
            boolean controlAccepted =
                    Math.abs(control.x - CONTROL_SPEED) <= EPS_SPEED
                            && Math.abs(control.z) <= EPS_SPEED;

            resetProbe(level, probe, x, y, z);

            cow.setDeltaMovement(Vec3.ZERO);
            cow.moveTo(x + 0.15D, y, z, 0.0F, 0.0F);

            probe.setDeltaMovement(CONTROL_SPEED, 0.0D, 0.0D);
            Vec3 movingBefore = probe.getDeltaMovement();
            double speedBefore = horizontalSpeed(movingBefore);

            probe.push(cow);

            Vec3 movingAfter = probe.getDeltaMovement();
            double speedAfter = horizontalSpeed(movingAfter);
            double cowSpeedAfter = horizontalSpeed(cow.getDeltaMovement());

            boolean movingVehicleMotionPreserved =
                    Math.abs(movingAfter.x - movingBefore.x) <= EPS_SPEED
                            && Math.abs(movingAfter.z - movingBefore.z) <= EPS_SPEED;

            boolean movingCowPushedAway =
                    cowSpeedAfter > EPS_SPEED
                            || Math.abs(cow.getDeltaMovement().y) > EPS_SPEED;

            boolean pass =
                    playerResult.pass()
                            && cowResult.pass()
                            && zombieResult.pass()
                            && controlAccepted
                            && movingVehicleMotionPreserved
                            && movingCowPushedAway;

            String reason;
            if (!playerResult.pass()) {
                reason = "PLAYER_CAN_SHOVE_STOCK:" + playerResult.reason();
            } else if (!cowResult.pass()) {
                reason = "ANIMAL_CAN_SHOVE_STOCK:" + cowResult.reason();
            } else if (!zombieResult.pass()) {
                reason = "MOB_CAN_SHOVE_STOCK:" + zombieResult.reason();
            } else if (!controlAccepted) {
                reason = "CONTROL_SETDELTAMOVEMENT_BLOCKED";
            } else if (!movingVehicleMotionPreserved) {
                reason = "MOVING_LIVING_COLLISION_CHANGED_VEHICLE_MOTION";
            } else if (!movingCowPushedAway) {
                reason = "MOVING_STOCK_DID_NOT_PUSH_LIVING_ENTITY";
            } else {
                reason = "PASS";
            }

            return new ProbeResult(
                    entityId,
                    pass,
                    playerResult,
                    cowResult,
                    zombieResult,
                    controlAccepted,
                    movingVehicleMotionPreserved,
                    movingCowPushedAway,
                    speedBefore,
                    speedAfter,
                    cowSpeedAfter,
                    reason);

        } finally {
            probe.discard();
            cow.discard();
            zombie.discard();
        }
    }

    private static CollisionResult stationaryCollisionTest(
            ServerLevel level,
            AbstractMinecart probe,
            Entity living,
            double x,
            double y,
            double z) {

        resetProbe(level, probe, x, y, z);

        double baseX = probe.getX();
        double baseZ = probe.getZ();

        for (int i = 0; i < COLLISION_ROUNDS; i++) {
            living.setPos(probe.getX(), probe.getY(), probe.getZ());

            probe.push(living);
            level.tickNonPassenger(probe);

            if (probe.isRemoved()) {
                return new CollisionResult(
                        false,
                        horizontalDistance(baseX, baseZ, probe.getX(), probe.getZ()),
                        horizontalSpeed(probe.getDeltaMovement()),
                        "PROBE_REMOVED");
            }
        }

        double drift =
                horizontalDistance(
                        baseX, baseZ,
                        probe.getX(), probe.getZ());

        double finalSpeed =
                horizontalSpeed(probe.getDeltaMovement());

        boolean pass =
                drift <= EPS_POS
                        && finalSpeed <= EPS_SPEED;

        String reason;
        if (drift > EPS_POS) {
            reason = "PARKED_STOCK_DRIFTED";
        } else if (finalSpeed > EPS_SPEED) {
            reason = "PARKED_STOCK_ACQUIRED_SPEED";
        } else {
            reason = "PASS";
        }

        return new CollisionResult(
                pass,
                drift,
                finalSpeed,
                reason);
    }

    private static void resetProbe(
            ServerLevel level,
            AbstractMinecart probe,
            double x,
            double y,
            double z) {

        probe.moveTo(x, y, z, 90.0F, 0.0F);
        probe.setDeltaMovement(Vec3.ZERO);

        for (int i = 0; i < SETTLE_TICKS; i++) {
            probe.setDeltaMovement(Vec3.ZERO);
            level.tickNonPassenger(probe);
        }

        probe.moveTo(x, y, z, 90.0F, 0.0F);
        probe.setDeltaMovement(Vec3.ZERO);
    }

    private static ProbeResult failedProbe(String id, String reason) {
        CollisionResult failed =
                new CollisionResult(false, 0.0D, 0.0D, reason);

        return new ProbeResult(
                id,
                false,
                failed,
                failed,
                failed,
                false,
                false,
                false,
                0.0D,
                0.0D,
                0.0D,
                reason);
    }

    private static void buildFlatRail(ServerLevel level, BlockPos center) {
        BlockState rail = Blocks.RAIL.defaultBlockState()
                .setValue(RailBlock.SHAPE, RailShape.EAST_WEST);

        for (int dx = -2; dx <= 2; dx++) {
            BlockPos railPos = center.offset(dx, 0, 0);
            level.setBlock(railPos.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(railPos, rail, 2);
            level.setBlock(railPos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void cleanup(ServerLevel level, BlockPos origin) {
        for (int i = 0; i < PROBES.size(); i++) {
            BlockPos center = origin.offset(0, 0, i * 4);

            for (int dx = -2; dx <= 2; dx++) {
                BlockPos railPos = center.offset(dx, 0, 0);
                level.setBlock(railPos.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(railPos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(railPos.below(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static Path writeReport(List<ProbeResult> results) throws Exception {
        List<String> lines = new ArrayList<>();

        lines.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-rs2-h1");
        lines.add("HEADLESS LIVINGENTITY SHOVE IMMUNITY GATE");
        lines.add("TEST_HOOK=push(Entity)");
        lines.add("PLAYER_ENTITY=Forge FakePlayerFactory.getMinecraft");
        lines.add("ANIMAL_ENTITY=minecraft:cow");
        lines.add("HOSTILE_MOB_ENTITY=minecraft:zombie");
        lines.add("REAL_SERVERLEVEL_TICKNONPASSENGER=YES");
        lines.add("PROBE_COUNT=" + results.size());
        lines.add("");

        long passCount = 0;

        for (ProbeResult r : results) {
            if (r.pass()) {
                passCount++;
            }

            lines.add(String.format(
                    Locale.ROOT,
                    "PROBE id=%s PASS=%s "
                            + "playerPass=%s playerDrift=%.9f playerSpeed=%.9f "
                            + "cowPass=%s cowDrift=%.9f cowSpeed=%.9f "
                            + "zombiePass=%s zombieDrift=%.9f zombieSpeed=%.9f "
                            + "controlMotionWriteAccepted=%s "
                            + "movingVehicleMotionPreserved=%s "
                            + "movingCowPushedAway=%s "
                            + "movingVehicleSpeedBefore=%.9f "
                            + "movingVehicleSpeedAfter=%.9f "
                            + "movingCowSpeedAfter=%.9f "
                            + "reason=%s",
                    r.id(),
                    Boolean.toString(r.pass()),
                    Boolean.toString(r.player().pass()),
                    r.player().drift(),
                    r.player().finalHorizontalSpeed(),
                    Boolean.toString(r.cow().pass()),
                    r.cow().drift(),
                    r.cow().finalHorizontalSpeed(),
                    Boolean.toString(r.zombie().pass()),
                    r.zombie().drift(),
                    r.zombie().finalHorizontalSpeed(),
                    Boolean.toString(r.controlMotionWriteAccepted()),
                    Boolean.toString(r.movingVehicleMotionPreserved()),
                    Boolean.toString(r.movingCowPushedAway()),
                    r.movingVehicleSpeedBefore(),
                    r.movingVehicleSpeedAfter(),
                    r.movingCowSpeedAfter(),
                    r.reason()));
        }

        boolean pass = passCount == results.size();

        lines.add("");
        lines.add("PASS_COUNT=" + passCount);
        lines.add("FAIL_COUNT=" + (results.size() - passCount));
        lines.add("PLAYER_SHOVE_IMMUNITY_ALL_CURRENT_STOCK=" + pass);
        lines.add("ANIMAL_SHOVE_IMMUNITY_ALL_CURRENT_STOCK=" + pass);
        lines.add("MOB_SHOVE_IMMUNITY_ALL_CURRENT_STOCK=" + pass);
        lines.add("RESULT="
                + (pass
                ? "T7_RS2_LIVINGENTITY_SHOVE_HEADLESS_PASS"
                : "T7_RS2_LIVINGENTITY_SHOVE_HEADLESS_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rs2-living-shove-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        return output;
    }

    private static void writeFailureReport(Throwable t) throws Exception {
        List<String> lines = List.of(
                "TAFARICRAFT - TRAINS STEP 9.3g-t7-rs2-h1",
                "HEADLESS LIVINGENTITY SHOVE IMMUNITY GATE",
                "HARNESS_EXCEPTION=" + t.getClass().getName(),
                "HARNESS_MESSAGE=" + String.valueOf(t.getMessage()),
                "RESULT=T7_RS2_LIVINGENTITY_SHOVE_HEADLESS_HARNESS_FAILURE"
        );

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rs2-living-shove-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private static double horizontalSpeed(Vec3 motion) {
        return Math.sqrt(
                motion.x * motion.x
                        + motion.z * motion.z);
    }

    private static double horizontalDistance(
            double ax,
            double az,
            double bx,
            double bz) {

        double dx = bx - ax;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
