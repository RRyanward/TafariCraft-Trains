package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
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
 * Step 9.3g-t7-rs1-h1
 *
 * Dedicated-server headless validation for the rs1 heavy rolling-stock
 * generic-collision impulse override.
 *
 * DIAGNOSTIC ONLY.  No production state is mutated except temporary test rails
 * in a forced test chunk; those rails and entities are removed before shutdown.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7Rs1PlayerShoveHeadlessCommand {
    private static final String ENV = "TC_T7_RS1_SHOVE_HEADLESS";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final double EPS_POS = 1.0E-5D;
    private static final double EPS_SPEED = 1.0E-6D;
    private static final double CONTROL_SPEED = 0.18D;
    private static final int SETTLE_TICKS = 4;
    private static final int SHOVE_ROUNDS = 32;

    private static final List<String> PROBES = List.of(
            "locomotive_steam_small",
            "steam_tender",
            "freight_cart",
            "passenger_coach_blue",
            "caboose"
    );

    private TrackT7Rs1PlayerShoveHeadlessCommand() {
    }

    private record ProbeResult(
            String id,
            boolean pass,
            double drift,
            double finalHorizontalSpeed,
            boolean directImpulseBlocked,
            boolean controlMotionWriteAccepted,
            int entityTickDelta,
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

        int rawX = spawn.getX() + 160;
        int rawZ = spawn.getZ() + 160;

        // Keep the entire five-probe gallery inside one forced chunk.
        int originX = ((rawX >> 4) << 4) + 8;
        int originZ = ((rawZ >> 4) << 4) + 1;

        BlockPos origin = new BlockPos(
                originX,
                railY,
                originZ);

        ChunkPos chunk = new ChunkPos(origin);
        level.setChunkForced(chunk.x, chunk.z, true);

        List<ProbeResult> results = new ArrayList<>();

        try {
            for (int i = 0; i < PROBES.size(); i++) {
                BlockPos railCenter = origin.offset(0, 0, i * 3);
                results.add(testProbe(level, railCenter, PROBES.get(i)));
            }

            Path report = writeReport(results);

            long passCount = results.stream().filter(ProbeResult::pass).count();

            System.out.println(
                    "[TC-T7-RS1-H1] complete pass="
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
            BlockPos center,
            String entityId) throws Exception {

        buildFlatRail(level, center);

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("traincraft", entityId));

        if (type == null) {
            return new ProbeResult(
                    entityId, false, 0.0D, 0.0D, false, false, 0,
                    "MISSING_ENTITY_TYPE");
        }

        Entity raw = type.create(level);
        if (!(raw instanceof AbstractMinecart probe)) {
            if (raw != null) raw.discard();
            return new ProbeResult(
                    entityId, false, 0.0D, 0.0D, false, false, 0,
                    "NOT_ABSTRACT_MINECART");
        }

        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.0625D;
        double z = center.getZ() + 0.5D;

        probe.moveTo(x, y, z, 90.0F, 0.0F);
        probe.setDeltaMovement(Vec3.ZERO);

        if (!level.addFreshEntity(probe)) {
            probe.discard();
            return new ProbeResult(
                    entityId, false, 0.0D, 0.0D, false, false, 0,
                    "ADD_ENTITY_FAILED");
        }

        try {
            for (int i = 0; i < SETTLE_TICKS; i++) {
                probe.setDeltaMovement(Vec3.ZERO);
                level.tickNonPassenger(probe);
                if (probe.isRemoved()) {
                    return new ProbeResult(
                            entityId, false, 0.0D, 0.0D, false, false,
                            probe.tickCount,
                            "REMOVED_DURING_SETTLE");
                }
            }

            probe.setDeltaMovement(Vec3.ZERO);

            double baseX = probe.getX();
            double baseZ = probe.getZ();
            int tickStart = probe.tickCount;

            // Directly exercise the exact generic collision-impulse hook that
            // vanilla entity/body collisions use to shove the vehicle.
            probe.push(0.35D, 0.0D, 0.0D);
            probe.push(0.0D, 0.0D, 0.35D);

            Vec3 immediate = probe.getDeltaMovement();
            boolean directBlocked =
                    horizontalSpeed(immediate) <= EPS_SPEED;

            // Repeated alternating body-style impulses plus real server entity
            // ticks.  A parked heavy vehicle must not accumulate motion or walk
            // away from its baseline rail position.
            for (int i = 0; i < SHOVE_ROUNDS; i++) {
                double sx = ((i & 1) == 0) ? 0.31D : -0.27D;
                double sz = ((i & 2) == 0) ? 0.19D : -0.23D;

                probe.push(sx, 0.0D, sz);
                level.tickNonPassenger(probe);

                if (probe.isRemoved()) {
                    return new ProbeResult(
                            entityId, false,
                            horizontalDistance(baseX, baseZ, probe.getX(), probe.getZ()),
                            horizontalSpeed(probe.getDeltaMovement()),
                            directBlocked, false,
                            probe.tickCount - tickStart,
                            "REMOVED_DURING_SHOVE_TEST");
                }
            }

            double drift =
                    horizontalDistance(
                            baseX, baseZ,
                            probe.getX(), probe.getZ());

            double finalSpeed =
                    horizontalSpeed(probe.getDeltaMovement());

            // Control sanity: rs1 must not disable the normal movement write used
            // by the existing traction/rail/coupling code.
            probe.setDeltaMovement(CONTROL_SPEED, 0.0D, 0.0D);
            Vec3 control = probe.getDeltaMovement();
            boolean controlAccepted =
                    Math.abs(control.x - CONTROL_SPEED) <= EPS_SPEED
                            && Math.abs(control.z) <= EPS_SPEED;

            probe.setDeltaMovement(Vec3.ZERO);

            boolean pass =
                    directBlocked
                            && drift <= EPS_POS
                            && finalSpeed <= EPS_SPEED
                            && controlAccepted;

            String reason;
            if (!directBlocked) {
                reason = "DIRECT_GENERIC_IMPULSE_CHANGED_MOTION";
            } else if (drift > EPS_POS) {
                reason = "PARKED_STOCK_DRIFTED";
            } else if (finalSpeed > EPS_SPEED) {
                reason = "PARKED_STOCK_ACQUIRED_SPEED";
            } else if (!controlAccepted) {
                reason = "CONTROL_SETDELTAMOVEMENT_BLOCKED";
            } else {
                reason = "PASS";
            }

            return new ProbeResult(
                    entityId,
                    pass,
                    drift,
                    finalSpeed,
                    directBlocked,
                    controlAccepted,
                    probe.tickCount - tickStart,
                    reason);

        } finally {
            probe.discard();
        }
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
            BlockPos center = origin.offset(0, 0, i * 3);
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

        lines.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-rs1-h1");
        lines.add("HEADLESS HEAVY ROLLING STOCK PLAYER-SHOVE GATE");
        lines.add("TEST=GENERIC_COLLISION_IMPULSE_IMMUNITY");
        lines.add("REAL_SERVERLEVEL_TICKNONPASSENGER=YES");
        lines.add("PROBE_COUNT=" + results.size());
        lines.add("");

        long passCount = 0;

        for (ProbeResult r : results) {
            if (r.pass()) passCount++;

            lines.add(String.format(
                    Locale.ROOT,
                    "PROBE id=%s PASS=%s drift=%.9f finalHorizontalSpeed=%.9f "
                            + "directImpulseBlocked=%s controlMotionWriteAccepted=%s "
                            + "entityTickDelta=%d reason=%s",
                    r.id(),
                    Boolean.toString(r.pass()),
                    r.drift(),
                    r.finalHorizontalSpeed(),
                    Boolean.toString(r.directImpulseBlocked()),
                    Boolean.toString(r.controlMotionWriteAccepted()),
                    r.entityTickDelta(),
                    r.reason()));
        }

        boolean pass = passCount == results.size();

        lines.add("");
        lines.add("PASS_COUNT=" + passCount);
        lines.add("FAIL_COUNT=" + (results.size() - passCount));
        lines.add("ALL_CURRENT_ROLLING_STOCK_PLAYER_SHOVE_IMMUNE=" + pass);
        lines.add("RESULT="
                + (pass
                ? "T7_RS1_PLAYER_SHOVE_HEADLESS_PASS"
                : "T7_RS1_PLAYER_SHOVE_HEADLESS_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rs1-player-shove-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        return output;
    }

    private static void writeFailureReport(Throwable t) throws Exception {
        List<String> lines = List.of(
                "TAFARICRAFT - TRAINS STEP 9.3g-t7-rs1-h1",
                "HEADLESS HEAVY ROLLING STOCK PLAYER-SHOVE GATE",
                "HARNESS_EXCEPTION=" + t.getClass().getName(),
                "HARNESS_MESSAGE=" + String.valueOf(t.getMessage()),
                "RESULT=T7_RS1_PLAYER_SHOVE_HEADLESS_HARNESS_FAILURE"
        );

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rs1-player-shove-"
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
            double ax, double az,
            double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
