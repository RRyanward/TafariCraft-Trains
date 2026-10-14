package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.block.track.LegacyContinuousTrackPath;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Step 9.3g-t7-d7 — headless real rolling-stock traversal matrix.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * This diagnostic deliberately does NOT retune locomotive acceleration,
 * braking, coupling spacing, speed caps, track topology, or track visuals.
 *
 * It drives real Traincraft rolling-stock entity probes through the already-frozen physical
 * assemblies through their normal entity tick path. No diagnostic movement adapter is called after tick.
 *
 * Every declared endpoint is tested as an entry. Switches are tested in both
 * inactive and active states. The follower routing entry point is additionally
 * tested for every switch endpoint/state. Cases run in small parallel batches
 * so even the largest 31-block-radius curves do not create one enormous loaded
 * gallery.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7HeadlessMatrixCommand {
    private static final int EXPECTED_SPECS = 50;

    private static final int BATCH_SIZE = 8;
    private static final int BATCH_COLUMNS = 4;
    private static final int SLOT_SPACING = 80;
    private static final int ROOT_INSET = 36;

    private static final double TEST_SPEED = 0.18D;
    private static final double MIN_SUSTAIN_SPEED = 0.035D;
    private static final int MAX_CASE_TICKS = 420;
    private static final int MIN_EXIT_TICKS = 3;
    private static final double ENDPOINT_CAPTURE_DISTANCE = 0.78D;
    private static final double SHARED_CORE_EXIT_PROGRESS = 0.58D;
    private static final int START_FEEDER_LENGTH = 2;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final String HEADLESS_ENV = "TC_T7_D7_HEADLESS";
    private static final String PROBE_ENV = "TC_T7_D7_PROBE";
    private static final String DEFAULT_PROBE = "locomotive_steam_small";
    private static final double HARD_SPEED_KILL_THRESHOLD = 0.010D;


    private enum ProbeMode {
        LEADER,
        FOLLOWER_SWITCH
    }

    private record SpecEntry(
            String fieldName,
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block,
            boolean isSwitch) {
    }

    private record CaseDef(
            int ordinal,
            SpecEntry entry,
            ProbeMode mode,
            boolean active,
            int startEndpointIndex) {
    }

    private record CaseResult(
            CaseDef def,
            boolean pass,
            int ticks,
            int exitEndpointIndex,
            String reason) {
    }

    private static final class ActiveCase {
        final CaseDef def;
        final BlockPos root;
        final List<BlockPos> ownedBlocks;
        final Map<Integer, List<BlockPos>> externalLanes;
        final AbstractMinecart probe;
        final Vec3 startCenter;
        final Set<ChunkPos> forcedChunks;
        final int spawnEntityTickCount;

        int tickCountAtServerStart;
        int serverWrapperFallbackTicks;

        Vec3 preMotion;
        Vec3 lastHorizontalDirection;
        int ticks;
        boolean done;
        boolean enteredAssembly;

        final Set<Integer> visitedParts = new LinkedHashSet<>();
        double maxDistanceFromStart;
        int nearestEndpointIndex = -1;
        double nearestEndpointDistance = Double.POSITIVE_INFINITY;
        Vec3 lastPosition = Vec3.ZERO;
        Vec3 lastMotion = Vec3.ZERO;

        ActiveCase(
                CaseDef def,
                BlockPos root,
                List<BlockPos> ownedBlocks,
                Map<Integer, List<BlockPos>> externalLanes,
                AbstractMinecart probe,
                Vec3 startCenter,
                Vec3 initialDirection,
                Set<ChunkPos> forcedChunks) {
            this.def = def;
            this.root = root;
            this.ownedBlocks = ownedBlocks;
            this.externalLanes = externalLanes;
            this.probe = probe;
            this.startCenter = startCenter;
            this.forcedChunks = Set.copyOf(forcedChunks);
            this.spawnEntityTickCount = probe.tickCount;
            this.preMotion = probe.getDeltaMovement();
            this.lastHorizontalDirection = initialDirection;
        }
    }

    private static final class RunState {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos origin;
        final List<CaseDef> cases;
        final String probeEntityId;
        final boolean headless;
        final List<CaseResult> results = new ArrayList<>();
        final List<ActiveCase> active = new ArrayList<>();

        int nextCaseIndex;
        int batchNumber;
        boolean setupNeeded = true;
        boolean finished;

        RunState(
                ServerPlayer player,
                ServerLevel level,
                BlockPos origin,
                List<CaseDef> cases,
                String probeEntityId,
                boolean headless) {
            this.player = player;
            this.level = level;
            this.origin = origin;
            this.cases = cases;
            this.probeEntityId = probeEntityId;
            this.headless = headless;
        }
    }

    private static RunState RUN;

    private TrackT7HeadlessMatrixCommand() {
    }

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_headless_matrix")
                        .executes(context -> {
                            try {
                                return start(
                                        context.getSource().getPlayerOrException());
                            } catch (Exception e) {
                                context.getSource().sendFailure(
                                        Component.literal(
                                                "TC t7 traversal sweep could not start: "
                                                        + e.getClass().getSimpleName()
                                                        + ": "
                                                        + String.valueOf(e.getMessage())));
                                return 0;
                            }
                        })
        );

        event.getDispatcher().register(
                Commands.literal("tc_t7_headless_status")
                        .executes(context ->
                                status(context.getSource().getPlayerOrException()))
        );

        event.getDispatcher().register(
                Commands.literal("tc_t7_headless_abort")
                        .executes(context ->
                                abort(context.getSource().getPlayerOrException()))
        );
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String enabled = System.getenv(HEADLESS_ENV);
        if (!"1".equals(enabled) && !"true".equalsIgnoreCase(enabled)) {
            return;
        }
        try {
            MinecraftServer server = event.getServer();
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int rootY = Math.max(level.getMinBuildHeight() + 12,
                    Math.min(level.getMaxBuildHeight() - 24, spawn.getY() + 24));
            BlockPos origin = new BlockPos(spawn.getX() + 128, rootY, spawn.getZ() + 128);
            startInternal(null, level, origin, true);
            System.out.println("[TC-T7-D7] Headless matrix started for probe=" + RUN.probeEntityId);
        } catch (Throwable t) {
            t.printStackTrace();
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        RunState run = RUN;
        if (run == null || run.finished) {
            return;
        }

        try {
            if (event.phase == TickEvent.Phase.START) {
                tickStart(run);
            } else if (event.phase == TickEvent.Phase.END) {
                tickEnd(run);
            }
        } catch (Throwable t) {
            failWholeRun(run, t);
        }
    }

    private static int start(ServerPlayer player) throws Exception {
        ServerLevel level = player.serverLevel();
        int rootY = Math.max(level.getMinBuildHeight() + 12,
                Math.min(level.getMaxBuildHeight() - 24, player.getBlockY() + 24));
        BlockPos origin = new BlockPos(player.getBlockX() + 48, rootY, player.getBlockZ() + 48);
        return startInternal(player, level, origin, false);
    }

    private static int startInternal(ServerPlayer player, ServerLevel level,
                                     BlockPos origin, boolean headless) throws Exception {
        if (RUN != null && !RUN.finished) {
            if (player != null) player.sendSystemMessage(Component.literal("TC t7 d7 matrix is already running."));
            return 0;
        }
        List<SpecEntry> specs = collectSpecs();
        if (specs.size() != EXPECTED_SPECS) {
            throw new IllegalStateException("Expected " + EXPECTED_SPECS + " specs, found " + specs.size());
        }
        List<CaseDef> cases = buildCases(specs);
        String probeId = System.getenv(PROBE_ENV);
        if (probeId == null || probeId.isBlank()) probeId = DEFAULT_PROBE;
        RUN = new RunState(player, level, origin, cases, probeId, headless);
        if (player != null) {
            player.sendSystemMessage(Component.literal("TC t7 d7 real-entity matrix started: probe=" + probeId
                    + ", cases=" + cases.size() + "."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int status(ServerPlayer player) {
        RunState run = RUN;
        if (run == null) {
            player.sendSystemMessage(Component.literal(
                    "No TC t7 traversal sweep has been started."));
            return 0;
        }

        long pass = run.results.stream().filter(CaseResult::pass).count();
        long fail = run.results.size() - pass;

        player.sendSystemMessage(Component.literal(
                "TC t7 traversal status: completed="
                        + run.results.size() + "/" + run.cases.size()
                        + ", pass=" + pass
                        + ", fail=" + fail
                        + ", active=" + run.active.size()
                        + ", nextBatch=" + run.batchNumber));

        return Command.SINGLE_SUCCESS;
    }

    private static int abort(ServerPlayer player) {
        RunState run = RUN;
        if (run == null || run.finished) {
            player.sendSystemMessage(Component.literal(
                    "No active TC t7 traversal sweep."));
            return 0;
        }

        cleanupActive(run);
        run.finished = true;
        RUN = null;

        player.sendSystemMessage(Component.literal(
                "TC t7 traversal sweep aborted and current probe batch removed."));

        return Command.SINGLE_SUCCESS;
    }

    private static void tickStart(RunState run) throws Exception {
        if (run.setupNeeded) {
            if (run.nextCaseIndex >= run.cases.size()) {
                finish(run);
                return;
            }

            setupNextBatch(run);
            run.setupNeeded = false;
        }

        for (ActiveCase active : run.active) {
            if (active.done || active.probe.isRemoved()) {
                continue;
            }

            // STEP_9_3G_T7_D7_R2_REAL_ENTITY_HEADLESS_TICK_FALLBACK
            // Capture the probe tick counter before the normal server world tick.
            // At END we can tell whether this no-player server actually ticked it.
            active.tickCountAtServerStart = active.probe.tickCount;

            Vec3 motion = active.probe.getDeltaMovement();
            Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
            double speed = horizontal.length();

            Vec3 direction;
            if (speed >= MIN_SUSTAIN_SPEED) {
                direction = horizontal.scale(1.0D / speed);
                active.lastHorizontalDirection = direction;
            } else {
                direction = active.lastHorizontalDirection;
            }

            if (direction.horizontalDistanceSqr() < 1.0E-8D) {
                direction = initialInwardDirection(active.def);
                active.lastHorizontalDirection = direction;
            }

            active.probe.setDeltaMovement(
                    direction.x * TEST_SPEED,
                    motion.y,
                    direction.z * TEST_SPEED);

            active.preMotion = active.probe.getDeltaMovement();
        }
    }

    private static void tickEnd(RunState run) throws Exception {
        boolean allDone = true;

        for (ActiveCase active : run.active) {
            if (active.done) {
                continue;
            }

            allDone = false;
            active.ticks++;

            if (active.probe.isRemoved()) {
                complete(
                        run,
                        active,
                        false,
                        -1,
                        "PROBE_REMOVED");
                continue;
            }

            // STEP_9_3G_T7_D7_R3_SERVERLEVEL_TICKNONPASSENGER_FALLBACK
            //
            // d7-r2 called probe.tick() directly when a forced chunk was loaded
            // but not in the server's entity-ticking range. That skipped the
            // ServerLevel entity-tick wrapper, which is where Minecraft advances
            // tickCount and performs the normal non-passenger tick framing.
            //
            // If the world did not tick this probe between START and END, invoke
            // the exact ServerLevel non-passenger path once. That path then calls
            // the entity's real SmallSteamLocomotive/SteamTender/etc. tick().
            // We still do NOT call LegacyContinuousTrackPath separately.
            if (run.headless
                    && !active.probe.isRemoved()
                    && active.probe.tickCount == active.tickCountAtServerStart) {
                run.level.tickNonPassenger(active.probe);
                active.serverWrapperFallbackTicks++;
            }

            if (run.headless
                    && !active.probe.isRemoved()
                    && active.probe.tickCount <= active.tickCountAtServerStart) {
                complete(
                        run,
                        active,
                        false,
                        -1,
                        "HARNESS_SERVERLEVEL_TICK_FAILED tickBefore="
                                + active.tickCountAtServerStart
                                + " tickAfter="
                                + active.probe.tickCount);
                continue;
            }

            // d7-r3: the rolling-stock entity has run through the real server
            // non-passenger tick path. Do not call LegacyContinuousTrackPath
            // a second time here.

            Vec3 post = active.probe.getDeltaMovement();
            Vec3 horizontal = new Vec3(post.x, 0.0D, post.z);
            double postSpeed = horizontal.length();
            if (active.enteredAssembly
                    && Math.hypot(active.preMotion.x, active.preMotion.z) >= MIN_SUSTAIN_SPEED
                    && postSpeed < HARD_SPEED_KILL_THRESHOLD) {
                complete(run, active, false, -1,
                        "HARD_SPEED_KILL pre="
                                + String.format(Locale.ROOT, "%.6f", Math.hypot(active.preMotion.x, active.preMotion.z))
                                + " post=" + String.format(Locale.ROOT, "%.6f", postSpeed));
                continue;
            }
            if (horizontal.horizontalDistanceSqr() > 1.0E-8D) {
                active.lastHorizontalDirection =
                        horizontal.normalize();
            }

            sampleTelemetry(active);

            int exit = detectExit(active);
            if (exit >= 0) {
                complete(
                        run,
                        active,
                        true,
                        exit,
                        "DECLARED_ENDPOINT_REACHED");
                continue;
            }

            if (active.ticks >= MAX_CASE_TICKS) {
                complete(
                        run,
                        active,
                        false,
                        -1,
                        "TIMEOUT");
            }
        }

        if (!allDone) {
            allDone = run.active.stream().allMatch(a -> a.done);
        }

        if (allDone) {
            cleanupActive(run);
            run.setupNeeded = true;
        }
    }

    private static void setupNextBatch(RunState run) throws Exception {
        run.active.clear();
        run.batchNumber++;

        int remaining = run.cases.size() - run.nextCaseIndex;
        int count = Math.min(BATCH_SIZE, remaining);

        for (int slot = 0; slot < count; slot++) {
            CaseDef def = run.cases.get(run.nextCaseIndex++);
            ActiveCase active = createCase(run, def, slot);
            run.active.add(active);
        }

        if (run.player != null && !run.active.isEmpty()) {
            BlockPos firstRoot = run.active.get(0).root;
            double centerX = firstRoot.getX()
                    + ((BATCH_COLUMNS - 1) * SLOT_SPACING) / 2.0D;
            double centerZ = firstRoot.getZ() + SLOT_SPACING / 2.0D;
            run.player.teleportTo(run.level, centerX, firstRoot.getY() + 48.0D,
                    centerZ, 180.0F, 80.0F);
            run.player.sendSystemMessage(Component.literal(
                    "TC t7 d7 batch " + run.batchNumber + " started; completed "
                            + run.results.size() + "/" + run.cases.size() + "."));
        } else if (run.headless) {
            System.out.println("[TC-T7-D7] batch=" + run.batchNumber
                    + " completed=" + run.results.size() + "/" + run.cases.size());
        }
    }

    private static ActiveCase createCase(
            RunState run,
            CaseDef def,
            int slot) throws Exception {

        int col = slot % BATCH_COLUMNS;
        int row = slot / BATCH_COLUMNS;

        BlockPos slotOrigin = run.origin.offset(
                col * SLOT_SPACING,
                0,
                row * SLOT_SPACING);

        BlockPos root = slotOrigin.offset(
                ROOT_INSET,
                0,
                ROOT_INSET);

        LegacyBatchTrackSpec spec = def.entry().spec();
        AbstractLegacyBatchRailBlock block = def.entry().block();

        List<BlockPos> owned = new ArrayList<>();
        Map<Integer, List<BlockPos>> externalLanes =
                new LinkedHashMap<>();
        Set<BlockPos> assemblyRails = new LinkedHashSet<>();
        Set<BlockPos> plannedRails = new LinkedHashSet<>();
        Set<BlockPos> plannedSupports = new LinkedHashSet<>();
        Set<ChunkPos> forcedChunks = new LinkedHashSet<>();

        // Assembly footprint/supports.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    part);
            assemblyRails.add(rail);
            plannedRails.add(rail);

            BlockPos supportBottom = spec.supportForPart(
                    root,
                    Direction.NORTH,
                    part);

            for (BlockPos cursor = supportBottom;
                 cursor.getY() <= rail.getY() - 1;
                 cursor = cursor.above()) {
                plannedSupports.add(cursor);
            }
        }

        // Add one real vanilla feeder rail outside EVERY exposed endpoint.
        // The selected start endpoint gets a second rail so the probe approaches
        // from outside instead of being spawned directly on the batch core.
        for (int endpointIndex = 0;
             endpointIndex < spec.endpoints().size();
             endpointIndex++) {

            LegacyBatchTrackSpec.Endpoint endpoint =
                    spec.endpoints().get(endpointIndex);

            BlockPos core = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    endpoint.part());

            Direction outward = probeEndpointOutward(
                    spec,
                    endpoint);

            int laneLength =
                    endpointIndex == def.startEndpointIndex()
                            ? START_FEEDER_LENGTH
                            : 1;

            List<BlockPos> lane = new ArrayList<>();

            for (int distance = 1;
                 distance <= laneLength;
                 distance++) {

                BlockPos rail = core.relative(
                        outward,
                        distance);

                if (assemblyRails.contains(rail)) {
                    throw new IllegalStateException(
                            "Endpoint feeder overlaps assembly: id="
                                    + spec.id()
                                    + " endpoint="
                                    + endpointIndex
                                    + " rail="
                                    + rail);
                }

                lane.add(rail);
                plannedRails.add(rail);
                plannedSupports.add(rail.below());
            }

            externalLanes.put(
                    endpointIndex,
                    List.copyOf(lane));
        }

        // Verify every cell before touching the world.
        for (BlockPos rail : plannedRails) {
            run.level.getChunkAt(rail);

            if (!run.level.getBlockState(rail).isAir()) {
                throw new IllegalStateException(
                        "Traversal slot is not clear at rail "
                                + rail
                                + ". Move to a fresh open area and rerun.");
            }
        }

        for (BlockPos support : plannedSupports) {
            if (plannedRails.contains(support)) {
                continue;
            }

            run.level.getChunkAt(support);

            if (!run.level.getBlockState(support).isAir()) {
                throw new IllegalStateException(
                        "Traversal slot is not clear at support "
                                + support
                                + ". Move to a fresh open area and rerun.");
            }
        }

        // Supports first.
        for (BlockPos support : plannedSupports) {
            if (plannedRails.contains(support)) {
                continue;
            }

            run.level.setBlock(
                    support,
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    2);
            owned.add(support);
        }

        // Frozen batch assembly.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    part);

            BlockState state = block.stateForPart(
                    Direction.NORTH,
                    part,
                    def.active());

            run.level.setBlock(
                    rail,
                    state,
                    2);
            owned.add(rail);
        }

        // Real external feeder/exit rails. These are deliberately ordinary
        // vanilla rails: they exercise the same entry/exit capture boundary
        // that the production path code must bridge.
        for (int endpointIndex = 0;
             endpointIndex < spec.endpoints().size();
             endpointIndex++) {

            LegacyBatchTrackSpec.Endpoint endpoint =
                    spec.endpoints().get(endpointIndex);

            Direction outward = probeEndpointOutward(
                    spec,
                    endpoint);

            RailShape shape =
                    outward.getAxis() == Direction.Axis.X
                            ? RailShape.EAST_WEST
                            : RailShape.NORTH_SOUTH;

            for (BlockPos rail : externalLanes.get(endpointIndex)) {
                run.level.setBlock(
                        rail,
                        Blocks.RAIL.defaultBlockState()
                                .setValue(
                                        RailBlock.SHAPE,
                                        shape),
                        2);
                owned.add(rail);
            }
        }

        // Re-lock batch states after neighbor rails exist so vanilla shape
        // updates cannot alter the frozen assembly state.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    part);

            run.level.setBlock(
                    rail,
                    block.stateForPart(
                            Direction.NORTH,
                            part,
                            def.active()),
                    2);
        }

        LegacyBatchTrackSpec.Endpoint startEndpoint =
                spec.endpoints().get(def.startEndpointIndex());

        Direction startOutward = probeEndpointOutward(
                spec,
                startEndpoint);

        List<BlockPos> startLane =
                externalLanes.get(def.startEndpointIndex());

        if (startLane == null
                || startLane.size() < START_FEEDER_LENGTH) {
            throw new IllegalStateException(
                    "Missing two-block start feeder for "
                            + spec.id());
        }

        BlockPos spawnRail =
                startLane.get(START_FEEDER_LENGTH - 1);

        Vec3 startCenter = railCenter(spawnRail);

        Vec3 inward = new Vec3(
                -startOutward.getStepX(),
                0.0D,
                -startOutward.getStepZ()).normalize();

        EntityType<?> probeType = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("traincraft", run.probeEntityId));
        if (probeType == null) {
            throw new IllegalStateException("Missing Traincraft probe entity type: " + run.probeEntityId);
        }
        Entity rawProbe = probeType.create(run.level);
        if (!(rawProbe instanceof AbstractMinecart probe)) {
            if (rawProbe != null) rawProbe.discard();
            throw new IllegalStateException("Probe entity is not AbstractMinecart: " + run.probeEntityId);
        }

        probe.setPos(
                startCenter.x,
                startCenter.y,
                startCenter.z);

        probe.setDeltaMovement(
                inward.x * TEST_SPEED,
                0.0D,
                inward.z * TEST_SPEED);

        probe.setYRot(yawFromVector(inward));

        if (!run.level.addFreshEntity(probe)) {
            throw new IllegalStateException(
                    "Could not add real Traincraft probe " + run.probeEntityId + " for " + def);
        }

        // STEP_9_3G_T7_D7_R1_FORCE_HEADLESS_ENTITY_TICK_CHUNKS
        // getChunkAt()/setBlock() loads chunks but does not make them entity-
        // ticking on a no-player dedicated server. Force-load every chunk that
        // contains this isolated assembly or one of its real feeder/exit rails.
        // Cleanup always releases these exact chunks before the next batch.
        try {
            for (BlockPos rail : plannedRails) {
                forcedChunks.add(
                        new ChunkPos(
                                rail.getX() >> 4,
                                rail.getZ() >> 4));
            }

            for (ChunkPos chunk : forcedChunks) {
                run.level.setChunkForced(
                        chunk.x,
                        chunk.z,
                        true);
            }
        } catch (Throwable t) {
            for (ChunkPos chunk : forcedChunks) {
                try {
                    run.level.setChunkForced(
                            chunk.x,
                            chunk.z,
                            false);
                } catch (Throwable ignored) {
                }
            }
            probe.discard();
            throw t;
        }

        return new ActiveCase(
                def,
                root,
                owned,
                Map.copyOf(externalLanes),
                probe,
                startCenter,
                inward,
                forcedChunks);
    }

    private static Direction probeEndpointOutward(
            LegacyBatchTrackSpec spec,
            LegacyBatchTrackSpec.Endpoint endpoint) {

        // Mirrors the already-proven Small-switch p2 connector correction used
        // by both placement and LegacyContinuousTrackPath.
        if (endpoint.part() == 2) {
            if ("track_switch_small_left".equals(spec.id())) {
                return spec.rotateDirection(
                        Direction.NORTH,
                        Direction.EAST);
            }

            if ("track_switch_small_right".equals(spec.id())) {
                return spec.rotateDirection(
                        Direction.NORTH,
                        Direction.WEST);
            }
        }

        // STEP_9_3G_T7_D4A_STANDARD_MEDIUM_P4_FEEDER
        //
        // Standard Medium's runtime route ownership intentionally reinterprets
        // the old p4 endpoint metadata. The proven physical straight is
        // p10 <-> p4, so an external connector at p4 must continue away from
        // p10 along that exact centerline. d4 still used endpoint.outward()
        // here, which put the test feeder on the legacy metadata side and made
        // the inactive common->straight route stop even though production's
        // runtime handoff looks along the p10->p4 tangent.
        //
        // Derive the canonical outward direction from p10 -> p4 rather than
        // hard-coding Left/Right. Facing rotation below keeps all four assembly
        // facings correct.
        if (endpoint.part() == 4
                && ("track_switch_medium_left".equals(spec.id())
                        || "track_switch_medium_right".equals(spec.id()))) {
            LegacyBatchTrackSpec.Point common =
                    spec.pointForPart(10);
            LegacyBatchTrackSpec.Point straight =
                    spec.pointForPart(4);

            int dx = Integer.compare(
                    straight.x,
                    common.x);
            int dz = Integer.compare(
                    straight.z,
                    common.z);

            Direction canonical;
            if (dx > 0 && dz == 0) {
                canonical = Direction.EAST;
            } else if (dx < 0 && dz == 0) {
                canonical = Direction.WEST;
            } else if (dz > 0 && dx == 0) {
                canonical = Direction.SOUTH;
            } else if (dz < 0 && dx == 0) {
                canonical = Direction.NORTH;
            } else {
                throw new IllegalStateException(
                        "Standard Medium p10->p4 is not cardinal: "
                                + spec.id()
                                + " dx="
                                + dx
                                + " dz="
                                + dz);
            }

            return spec.rotateDirection(
                    Direction.NORTH,
                    canonical);
        }

        return spec.rotateDirection(
                Direction.NORTH,
                endpoint.outward());
    }

    private static int detectExit(ActiveCase active) {
        if (active.ticks < MIN_EXIT_TICKS
                || !active.enteredAssembly) {
            return -1;
        }

        BlockPos base = active.probe.blockPosition();

        for (Map.Entry<Integer, List<BlockPos>> entry
                : active.externalLanes.entrySet()) {

            int endpointIndex = entry.getKey();

            if (endpointIndex
                    == active.def.startEndpointIndex()) {
                continue;
            }

            // Only the immediate external rail is required for a valid handoff.
            // Checking the actual rail cell avoids all ambiguity around diagonal
            // endpoint tangents and shared visual/core geometry.
            BlockPos exitRail = entry.getValue().get(0);

            if (base.equals(exitRail)
                    || base.below().equals(exitRail)
                    || base.above().equals(exitRail)) {
                return endpointIndex;
            }
        }

        return -1;
    }

    private static void complete(
            RunState run,
            ActiveCase active,
            boolean pass,
            int exitEndpointIndex,
            String reason) {

        if (active.done) {
            return;
        }

        active.done = true;

        String detailedReason =
                reason
                        + ";finalPos=" + vec(active.lastPosition)
                        + ";finalMotion=" + vec(active.lastMotion)
                        + ";maxFromStart=" + f(active.maxDistanceFromStart)
                        + ";enteredAssembly=" + active.enteredAssembly
                        + ";nearestEndpoint=" + active.nearestEndpointIndex
                        + ";nearestEndpointDistance=" + f(active.nearestEndpointDistance)
                        + ";visitedParts=" + active.visitedParts
                        + ";entityTickDelta="
                        + (active.probe.tickCount
                                - active.spawnEntityTickCount)
                        + ";serverWrapperFallbackTicks=" + active.serverWrapperFallbackTicks
                        + ";forcedChunks=" + active.forcedChunks.size();

        run.results.add(
                new CaseResult(
                        active.def,
                        pass,
                        active.ticks,
                        exitEndpointIndex,
                        detailedReason));

        if (!active.probe.isRemoved()) {
            active.probe.discard();
        }
    }

    private static void sampleTelemetry(ActiveCase active) {
        Vec3 position = active.probe.position();
        Vec3 motion = active.probe.getDeltaMovement();

        active.lastPosition = position;
        active.lastMotion = motion;
        active.maxDistanceFromStart = Math.max(
                active.maxDistanceFromStart,
                horizontalDistance(position, active.startCenter));

        LegacyBatchTrackSpec spec = active.def.entry().spec();

        for (int index = 0; index < spec.endpoints().size(); index++) {
            LegacyBatchTrackSpec.Endpoint endpoint =
                    spec.endpoints().get(index);

            BlockPos core = spec.offsetForPart(
                    active.root,
                    Direction.NORTH,
                    endpoint.part());

            double distance = horizontalDistance(
                    position,
                    railCenter(core));

            if (distance < active.nearestEndpointDistance) {
                active.nearestEndpointDistance = distance;
                active.nearestEndpointIndex = index;
            }
        }

        BlockPos base = active.probe.blockPosition();
        for (BlockPos pos : List.of(base, base.below(), base.above())) {
            BlockState state = active.probe.level().getBlockState(pos);
            if (state.getBlock() == active.def.entry().block()) {
                try {
                    active.visitedParts.add(
                            active.def.entry().block().getPart(state));
                    active.enteredAssembly = true;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static double horizontalDistance(
            Vec3 a,
            Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.hypot(dx, dz);
    }

    private static String f(double value) {
        if (!Double.isFinite(value)) {
            return "INF";
        }

        return String.format(
                Locale.ROOT,
                "%.6f",
                value);
    }

    private static String vec(Vec3 vec) {
        return "("
                + f(vec.x)
                + ","
                + f(vec.y)
                + ","
                + f(vec.z)
                + ")";
    }

    private static void cleanupActive(RunState run) {
        for (ActiveCase active : run.active) {
            if (!active.probe.isRemoved()) {
                active.probe.discard();
            }

            for (BlockPos pos : active.ownedBlocks) {
                run.level.setBlock(
                        pos,
                        Blocks.AIR.defaultBlockState(),
                        2);
            }

            for (ChunkPos chunk : active.forcedChunks) {
                run.level.setChunkForced(
                        chunk.x,
                        chunk.z,
                        false);
            }
        }

        run.active.clear();
    }

    private static void finish(RunState run) throws Exception {
        cleanupActive(run);

        int leaderCases = 0;
        int leaderPass = 0;
        int followerCases = 0;
        int followerPass = 0;

        for (CaseResult result : run.results) {
            if (result.def().mode() == ProbeMode.LEADER) {
                leaderCases++;
                if (result.pass()) {
                    leaderPass++;
                }
            } else {
                followerCases++;
                if (result.pass()) {
                    followerPass++;
                }
            }
        }

        int reciprocityChecks = 0;
        int reciprocityFailures = 0;

        Map<String, CaseResult> leaderLookup = new HashMap<>();
        for (CaseResult result : run.results) {
            if (result.def().mode() != ProbeMode.LEADER
                    || !result.pass()) {
                continue;
            }

            leaderLookup.put(
                    leaderKey(
                            result.def().entry().spec().id(),
                            result.def().active(),
                            result.def().startEndpointIndex()),
                    result);
        }

        for (CaseResult result : run.results) {
            if (result.def().mode() != ProbeMode.LEADER
                    || !result.pass()
                    || result.exitEndpointIndex() < 0
                    || result.def().entry().isSwitch()) {
                continue;
            }

            String reverseKey = leaderKey(
                    result.def().entry().spec().id(),
                    result.def().active(),
                    result.exitEndpointIndex());

            CaseResult reverse = leaderLookup.get(reverseKey);
            if (reverse == null || !reverse.pass()) {
                continue;
            }

            reciprocityChecks++;
            if (reverse.exitEndpointIndex()
                    != result.def().startEndpointIndex()) {
                reciprocityFailures++;
            }
        }

        int switchSensitivityChecks = 0;
        int switchSensitivityFailures = 0;

        Map<String, Map<ProbeMode, Map<Integer, Map<Boolean, CaseResult>>>>
                switchMaps = new LinkedHashMap<>();

        for (CaseResult result : run.results) {
            if (!result.def().entry().isSwitch()
                    || !result.pass()) {
                continue;
            }

            switchMaps
                    .computeIfAbsent(
                            result.def().entry().spec().id(),
                            k -> new LinkedHashMap<>())
                    .computeIfAbsent(
                            result.def().mode(),
                            k -> new LinkedHashMap<>())
                    .computeIfAbsent(
                            result.def().startEndpointIndex(),
                            k -> new LinkedHashMap<>())
                    .put(
                            result.def().active(),
                            result);
        }

        for (var switchEntry : switchMaps.entrySet()) {
            for (var modeEntry : switchEntry.getValue().entrySet()) {
                boolean foundStateSensitiveStart = false;

                for (var startEntry : modeEntry.getValue().entrySet()) {
                    CaseResult inactive = startEntry.getValue().get(false);
                    CaseResult active = startEntry.getValue().get(true);

                    if (inactive == null || active == null) {
                        continue;
                    }

                    if (inactive.exitEndpointIndex()
                            != active.exitEndpointIndex()) {
                        foundStateSensitiveStart = true;
                        break;
                    }
                }

                switchSensitivityChecks++;
                if (!foundStateSensitiveStart) {
                    switchSensitivityFailures++;
                }
            }
        }

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-d7");
        report.add("HEADLESS REAL ROLLING-STOCK TRAVERSAL MATRIX");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PROBE_ENTITY=traincraft:" + run.probeEntityId);
        report.add("SPEC_COUNT=" + EXPECTED_SPECS);
        report.add("PRODUCTION_PATH=REAL_ENTITY_TICK_NO_POST_TICK_DIAGNOSTIC_ADAPTER");
        report.add("START_FEEDER_LENGTH=" + START_FEEDER_LENGTH);
        report.add("ENTRY_MODE=TWO_BLOCK_VANILLA_FEEDER");
        report.add("HEADLESS_CHUNK_FORCELOAD=ENABLED");
        report.add("HEADLESS_SERVERLEVEL_TICKNONPASSENGER_FALLBACK=ENABLED");
        report.add("ENTITY_TICK_WATCHDOG=R3_WORLD_OR_SERVERLEVEL_WRAPPER");
        report.add("EXIT_MODE=IMMEDIATE_EXTERNAL_RAIL_CELL");
        report.add("STANDARD_MEDIUM_P4_FEEDER=RUNTIME_TANGENT_MATCH");
        report.add("TOTAL_CASES=" + run.cases.size());
        report.add("LEADER_CASES=" + leaderCases);
        report.add("LEADER_PASS=" + leaderPass);
        report.add("FOLLOWER_SWITCH_CASES=" + followerCases);
        report.add("FOLLOWER_SWITCH_PASS=" + followerPass);
        report.add("RECIPROCITY_CHECKS=" + reciprocityChecks);
        report.add("RECIPROCITY_FAILURES=" + reciprocityFailures);
        report.add("SWITCH_STATE_SENSITIVITY_CHECKS=" + switchSensitivityChecks);
        report.add("SWITCH_STATE_SENSITIVITY_FAILURES=" + switchSensitivityFailures);
        report.add("");

        for (CaseResult result : run.results) {
            report.add(
                    "CASE ordinal=" + result.def().ordinal()
                            + " id=" + result.def().entry().spec().id()
                            + " field=" + result.def().entry().fieldName()
                            + " mode=" + result.def().mode()
                            + " active=" + result.def().active()
                            + " start=" + result.def().startEndpointIndex()
                            + " exit=" + result.exitEndpointIndex()
                            + " ticks=" + result.ticks()
                            + " PASS=" + result.pass()
                            + " reason=" + result.reason());
        }

        report.add("");
        report.add("SWITCH_MAPS:");

        for (var switchEntry : switchMaps.entrySet()) {
            report.add("SWITCH id=" + switchEntry.getKey());

            for (var modeEntry : switchEntry.getValue().entrySet()) {
                report.add("  MODE=" + modeEntry.getKey());

                for (var startEntry : modeEntry.getValue().entrySet()) {
                    CaseResult inactive = startEntry.getValue().get(false);
                    CaseResult active = startEntry.getValue().get(true);

                    report.add(
                            "    start=" + startEntry.getKey()
                                    + " inactiveExit="
                                    + exitText(inactive)
                                    + " activeExit="
                                    + exitText(active));
                }
            }
        }

        boolean allCasesPassed =
                leaderPass == leaderCases
                        && followerPass == followerCases;

        boolean pass =
                allCasesPassed
                        && reciprocityFailures == 0
                        && switchSensitivityFailures == 0;

        report.add("");
        report.add("ALL_CASES_PASS=" + allCasesPassed);
        report.add("RESULT="
                + (pass
                ? "T7_D7_REAL_ENTITY_MATRIX_PASS"
                : "T7_D7_REAL_ENTITY_MATRIX_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-headless-matrix-" + run.probeEntityId + "-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(
                output,
                report,
                StandardCharsets.UTF_8);

        run.finished = true;
        RUN = null;

        String summary = "TC t7 d7 complete probe=" + run.probeEntityId
                + " leader=" + leaderPass + "/" + leaderCases
                + " reciprocityFailures=" + reciprocityFailures
                + " report=" + output;
        if (run.player != null) {
            run.player.sendSystemMessage(Component.literal(summary));
        } else {
            System.out.println("[TC-T7-D7] " + summary);
        }
        if (run.headless) {
            run.level.getServer().halt(false);
        }
    }

    private static String exitText(CaseResult result) {
        if (result == null) {
            return "MISSING";
        }
        if (!result.pass()) {
            return "FAIL";
        }
        return Integer.toString(result.exitEndpointIndex());
    }

    private static String leaderKey(
            String id,
            boolean active,
            int start) {
        return id + "|" + active + "|" + start;
    }

    private static void failWholeRun(RunState run, Throwable t) {
        try { cleanupActive(run); } catch (Throwable ignored) { }
        run.finished = true;
        RUN = null;
        String message = "TC t7 d7 matrix failed probe=" + run.probeEntityId + ": "
                + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        if (run.player != null) run.player.sendSystemMessage(Component.literal(message));
        System.err.println("[TC-T7-D7] " + message);
        t.printStackTrace();
        if (run.headless) run.level.getServer().halt(false);
    }

    private static List<SpecEntry> collectSpecs()
            throws IllegalAccessException {

        List<SpecEntry> specs = new ArrayList<>();

        for (Field field : LegacyBatchTrackSpecs.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !LegacyBatchTrackSpec.class
                            .isAssignableFrom(field.getType())) {
                continue;
            }

            field.setAccessible(true);

            LegacyBatchTrackSpec spec =
                    (LegacyBatchTrackSpec) field.get(null);

            if (spec == null) {
                continue;
            }

            Block rawBlock =
                    ForgeRegistries.BLOCKS.getValue(
                            new ResourceLocation(
                                    "traincraft",
                                    spec.id() + "_segment"));

            if (!(rawBlock
                    instanceof AbstractLegacyBatchRailBlock block)) {
                throw new IllegalStateException(
                        "Missing batch rail block for "
                                + spec.id()
                                + ": "
                                + rawBlock);
            }

            specs.add(
                    new SpecEntry(
                            field.getName(),
                            spec,
                            block,
                            block instanceof LegacyBatchSwitchRailBlock));
        }

        specs.sort(
                Comparator.comparing(
                        entry -> entry.spec().id()));

        return specs;
    }

    private static List<CaseDef> buildCases(
            List<SpecEntry> specs) {

        List<CaseDef> cases = new ArrayList<>();
        int ordinal = 0;

        for (SpecEntry entry : specs) {
            List<Boolean> states = entry.isSwitch()
                    ? List.of(false, true)
                    : List.of(false);

            for (boolean active : states) {
                for (int start = 0;
                     start < entry.spec().endpoints().size();
                     start++) {

                    cases.add(
                            new CaseDef(
                                    ordinal++,
                                    entry,
                                    ProbeMode.LEADER,
                                    active,
                                    start));
                }
            }

        }

        return cases;
    }

    private static Vec3 initialInwardDirection(
            CaseDef def) {

        LegacyBatchTrackSpec.Endpoint endpoint =
                def.entry().spec().endpoints()
                        .get(def.startEndpointIndex());

        Direction outward =
                def.entry().spec().rotateDirection(
                        Direction.NORTH,
                        endpoint.outward());

        return new Vec3(
                -outward.getStepX(),
                0.0D,
                -outward.getStepZ()).normalize();
    }

    private static Vec3 railCenter(BlockPos pos) {
        return new Vec3(
                pos.getX() + 0.5D,
                pos.getY() + 0.0625D,
                pos.getZ() + 0.5D);
    }

    private static float yawFromVector(Vec3 direction) {
        return (float) Math.toDegrees(
                Math.atan2(
                        -direction.x,
                        direction.z));
    }
}
