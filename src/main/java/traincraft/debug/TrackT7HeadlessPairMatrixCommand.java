package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 9.3g-t7-p1 — flat connected handoff matrix.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * Purpose:
 *   Exercise real piece-to-piece ownership transfer with a real Traincraft
 *   rolling-stock entity, while excluding isolated locomotive cases already
 *   known to fail in d7-r3.
 *
 * The incoming piece is placed through the real registered track item so the
 * exact production snap/root/facing selection is used.  The probe starts just
 * inside the source endpoint core and must enter the incoming assembly without
 * removal, timeout, or a one-tick horizontal speed kill.
 *
 * p1 intentionally excludes slope specs.  Slopes use raised support footprints
 * and get their own connected matrix after this flat handoff foundation.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7HeadlessPairMatrixCommand {
    private static final int EXPECTED_SPECS = 50;

    private static final int BATCH_SIZE = 4;
    private static final int BATCH_COLUMNS = 2;
    private static final int SLOT_SPACING = 240;
    private static final int ROOT_INSET = 104;

    private static final double TEST_SPEED = 0.18D;
    private static final double MIN_SUSTAIN_SPEED = 0.035D;
    private static final double HARD_SPEED_KILL_THRESHOLD = 0.010D;
    private static final int MAX_CASE_TICKS = 80;
    private static final int INCOMING_SCAN_RADIUS = 24;

    private static final String HEADLESS_ENV = "TC_T7_D7_PAIR_HEADLESS";
    private static final String PROBE_ENV = "TC_T7_D7_PAIR_PROBE";
    private static final String FILTER_ENV = "TC_T7_D7_PAIR_FILTER";
    private static final String DEFAULT_PROBE = "locomotive_steam_small";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /**
     * Exact isolated locomotive failures from d7-r3.
     *
     * p1 excludes source endpoint/state cases with these keys and also skips an
     * incoming connection endpoint when its inactive isolated case is one of
     * these failures.  This keeps pair failures attributable to the seam rather
     * than knowingly re-testing an already-broken isolated endpoint.
     */
    private static final Set<String> KNOWN_ISOLATED_LOCO_FAILURES = Set.of(
            key("track_curve_32x", false, 0),
            key("track_diagonal_four_ways_crossing", false, 2),
            key("track_diagonal_straight_small", false, 0),
            key("track_diamond_crossing", false, 0),
            key("track_diamond_crossing", false, 1),
            key("track_diamond_crossing", false, 2),
            key("track_diamond_crossing", false, 3),
            key("track_parallel_curve_medium_left", false, 1),
            key("track_slope_curve_very_large_left", false, 1),
            key("track_slope_very_long", false, 1),
            key("track_switch_large_left", false, 2),
            key("track_switch_very_large_left", true, 2)
    );

    private record SpecEntry(
            String fieldName,
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block,
            boolean isSwitch) {
    }

    private record PairDef(
            int ordinal,
            SpecEntry source,
            boolean sourceActive,
            int sourceEndpointIndex,
            SpecEntry incoming) {
    }

    private record AssemblyInfo(
            AbstractLegacyBatchRailBlock block,
            BlockPos root,
            Direction facing) {
    }

    private enum ResultKind {
        PASS,
        FAIL,
        SKIP_NOT_COMPATIBLE,
        SKIP_INCOMING_ISOLATED_FAIL,
        SKIP_SETUP_REJECT
    }

    private record PairResult(
            PairDef def,
            ResultKind kind,
            int ticks,
            int incomingEndpointIndex,
            String reason) {
    }

    private static final class ActiveCase {
        final PairDef def;
        final AssemblyInfo sourceAssembly;
        final AssemblyInfo incomingAssembly;
        final int incomingEndpointIndex;
        final BlockPos sourceCore;
        final BlockPos incomingCore;
        final Set<BlockPos> ownedBlocks;
        final Set<ChunkPos> forcedChunks;
        final AbstractMinecart probe;
        final Vec3 startCenter;
        final Vec3 initialDirection;
        final int spawnEntityTickCount;

        int tickCountAtServerStart;
        int serverWrapperFallbackTicks;
        int ticks;
        boolean done;
        boolean enteredSource;
        boolean enteredIncoming;

        Vec3 preMotion;
        Vec3 lastDirection;
        Vec3 lastPosition = Vec3.ZERO;
        Vec3 lastMotion = Vec3.ZERO;
        double maxDistanceFromStart;

        ActiveCase(
                PairDef def,
                AssemblyInfo sourceAssembly,
                AssemblyInfo incomingAssembly,
                int incomingEndpointIndex,
                BlockPos sourceCore,
                BlockPos incomingCore,
                Set<BlockPos> ownedBlocks,
                Set<ChunkPos> forcedChunks,
                AbstractMinecart probe,
                Vec3 startCenter,
                Vec3 initialDirection) {
            this.def = def;
            this.sourceAssembly = sourceAssembly;
            this.incomingAssembly = incomingAssembly;
            this.incomingEndpointIndex = incomingEndpointIndex;
            this.sourceCore = sourceCore;
            this.incomingCore = incomingCore;
            this.ownedBlocks = Set.copyOf(ownedBlocks);
            this.forcedChunks = Set.copyOf(forcedChunks);
            this.probe = probe;
            this.startCenter = startCenter;
            this.initialDirection = initialDirection;
            this.spawnEntityTickCount = probe.tickCount;
            this.preMotion = probe.getDeltaMovement();
            this.lastDirection = initialDirection;
        }
    }

    private static final class RunState {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos origin;
        final String probeEntityId;
        final boolean headless;
        final List<PairDef> candidates;
        final List<PairResult> results = new ArrayList<>();
        final List<ActiveCase> active = new ArrayList<>();

        int nextCandidateIndex;
        int batchNumber;
        boolean setupNeeded = true;
        boolean finished;

        RunState(
                ServerPlayer player,
                ServerLevel level,
                BlockPos origin,
                String probeEntityId,
                boolean headless,
                List<PairDef> candidates) {
            this.player = player;
            this.level = level;
            this.origin = origin;
            this.probeEntityId = probeEntityId;
            this.headless = headless;
            this.candidates = candidates;
        }
    }

    private static RunState RUN;

    private TrackT7HeadlessPairMatrixCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_headless_pair_matrix")
                        .executes(context -> {
                            try {
                                return start(
                                        context.getSource().getPlayerOrException());
                            } catch (Exception e) {
                                context.getSource().sendFailure(
                                        Component.literal(
                                                "TC t7 p1 could not start: "
                                                        + e.getClass().getSimpleName()
                                                        + ": "
                                                        + String.valueOf(e.getMessage())));
                                return 0;
                            }
                        })
                        .then(Commands.literal("status")
                                .executes(context ->
                                        status(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("abort")
                                .executes(context ->
                                        abort(context.getSource().getPlayerOrException()))));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String raw = System.getenv(HEADLESS_ENV);
        if (!"1".equals(raw)) {
            return;
        }

        try {
            ServerLevel level = event.getServer().overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int rootY = Math.max(
                    level.getMinBuildHeight() + 12,
                    Math.min(level.getMaxBuildHeight() - 24, spawn.getY() + 24));
            BlockPos origin = new BlockPos(
                    spawn.getX() + 512,
                    rootY,
                    spawn.getZ() + 512);

            startInternal(null, level, origin, true);
            System.out.println(
                    "[TC-T7-P1] Headless flat pair matrix started probe="
                            + RUN.probeEntityId
                            + " candidates="
                            + RUN.candidates.size());
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
        int rootY = Math.max(
                level.getMinBuildHeight() + 12,
                Math.min(level.getMaxBuildHeight() - 24, player.getBlockY() + 24));
        BlockPos origin = new BlockPos(
                player.getBlockX() + 256,
                rootY,
                player.getBlockZ() + 256);
        return startInternal(player, level, origin, false);
    }

    private static int startInternal(
            ServerPlayer player,
            ServerLevel level,
            BlockPos origin,
            boolean headless) throws Exception {

        if (RUN != null && !RUN.finished) {
            if (player != null) {
                player.sendSystemMessage(
                        Component.literal("TC t7 p1 is already running."));
            }
            return 0;
        }

        List<SpecEntry> specs = collectSpecs();
        if (specs.size() != EXPECTED_SPECS) {
            throw new IllegalStateException(
                    "Expected " + EXPECTED_SPECS + " specs, found " + specs.size());
        }

        String filter = System.getenv(FILTER_ENV);
        if (filter == null || filter.isBlank()) {
            filter = "full";
        }

        List<PairDef> candidates = buildCandidates(specs, filter);

        String probeId = System.getenv(PROBE_ENV);
        if (probeId == null || probeId.isBlank()) {
            probeId = DEFAULT_PROBE;
        }

        RUN = new RunState(
                player,
                level,
                origin,
                probeId,
                headless,
                candidates);

        if (player != null) {
            player.sendSystemMessage(
                    Component.literal(
                            "TC t7 p1 flat pair matrix started: probe="
                                    + probeId
                                    + ", candidates="
                                    + candidates.size()
                                    + ", filter="
                                    + filter));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int status(ServerPlayer player) {
        RunState run = RUN;
        if (run == null) {
            player.sendSystemMessage(Component.literal("No TC t7 p1 run."));
            return 0;
        }

        long pass = run.results.stream()
                .filter(r -> r.kind() == ResultKind.PASS)
                .count();
        long fail = run.results.stream()
                .filter(r -> r.kind() == ResultKind.FAIL)
                .count();

        player.sendSystemMessage(
                Component.literal(
                        "TC t7 p1 status completed="
                                + run.results.size()
                                + "/"
                                + run.candidates.size()
                                + " pairPass="
                                + pass
                                + " pairFail="
                                + fail
                                + " active="
                                + run.active.size()));

        return Command.SINGLE_SUCCESS;
    }

    private static int abort(ServerPlayer player) {
        RunState run = RUN;
        if (run == null || run.finished) {
            player.sendSystemMessage(Component.literal("No active TC t7 p1 run."));
            return 0;
        }

        cleanupActive(run);
        run.finished = true;
        RUN = null;
        player.sendSystemMessage(
                Component.literal("TC t7 p1 aborted and current batch removed."));
        return Command.SINGLE_SUCCESS;
    }

    private static void tickStart(RunState run) throws Exception {
        if (run.setupNeeded) {
            setupNextBatch(run);
            run.setupNeeded = false;

            if (run.active.isEmpty()
                    && run.nextCandidateIndex >= run.candidates.size()) {
                finish(run);
                return;
            }
        }

        for (ActiveCase active : run.active) {
            if (active.done || active.probe.isRemoved()) {
                continue;
            }

            active.tickCountAtServerStart = active.probe.tickCount;

            Vec3 motion = active.probe.getDeltaMovement();
            Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
            double speed = horizontal.length();

            Vec3 direction = speed >= MIN_SUSTAIN_SPEED
                    ? horizontal.scale(1.0D / speed)
                    : active.lastDirection;

            if (direction.horizontalDistanceSqr() < 1.0E-8D) {
                direction = active.initialDirection;
            }

            active.lastDirection = direction;
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
                        "PROBE_REMOVED");
                continue;
            }

            if (run.headless
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
                        "HARNESS_SERVERLEVEL_TICK_FAILED tickBefore="
                                + active.tickCountAtServerStart
                                + " tickAfter="
                                + active.probe.tickCount);
                continue;
            }

            sampleTelemetry(active);

            if (active.enteredIncoming) {
                complete(
                        run,
                        active,
                        true,
                        "INCOMING_ASSEMBLY_ENTERED");
                continue;
            }

            Vec3 post = active.probe.getDeltaMovement();
            double postSpeed = Math.hypot(post.x, post.z);

            if (active.enteredSource
                    && Math.hypot(
                            active.preMotion.x,
                            active.preMotion.z) >= MIN_SUSTAIN_SPEED
                    && postSpeed < HARD_SPEED_KILL_THRESHOLD) {
                complete(
                        run,
                        active,
                        false,
                        "HARD_SPEED_KILL pre="
                                + f(Math.hypot(
                                        active.preMotion.x,
                                        active.preMotion.z))
                                + " post="
                                + f(postSpeed));
                continue;
            }

            if (postSpeed >= MIN_SUSTAIN_SPEED) {
                active.lastDirection =
                        new Vec3(post.x, 0.0D, post.z).normalize();
            }

            if (active.ticks >= MAX_CASE_TICKS) {
                complete(
                        run,
                        active,
                        false,
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

        int slot = 0;

        while (slot < BATCH_SIZE
                && run.nextCandidateIndex < run.candidates.size()) {

            PairDef def = run.candidates.get(run.nextCandidateIndex++);
            ActiveCase active = createCase(run, def, slot);

            if (active != null) {
                run.active.add(active);
                slot++;
            }
        }

        if (run.headless) {
            System.out.println(
                    "[TC-T7-P1] batch="
                            + run.batchNumber
                            + " completed="
                            + run.results.size()
                            + "/"
                            + run.candidates.size()
                            + " active="
                            + run.active.size());
        }

        if (run.active.isEmpty()
                && run.nextCandidateIndex < run.candidates.size()) {
            setupNextBatch(run);
        }
    }

    private static ActiveCase createCase(
            RunState run,
            PairDef def,
            int slot) throws Exception {

        int col = slot % BATCH_COLUMNS;
        int row = slot / BATCH_COLUMNS;

        BlockPos slotOrigin = run.origin.offset(
                col * SLOT_SPACING,
                0,
                row * SLOT_SPACING);

        BlockPos sourceRoot = slotOrigin.offset(
                ROOT_INSET,
                0,
                ROOT_INSET);

        LegacyBatchTrackSpec sourceSpec = def.source().spec();
        AbstractLegacyBatchRailBlock sourceBlock = def.source().block();

        Set<BlockPos> owned = new LinkedHashSet<>();
        Set<BlockPos> sourceRails = new LinkedHashSet<>();
        Set<ChunkPos> forced = new LinkedHashSet<>();

        // Exact source supports.
        for (int part = 0; part < sourceSpec.partCount(); part++) {
            BlockPos support = sourceSpec.supportForPart(
                    sourceRoot,
                    Direction.NORTH,
                    part);

            if (!run.level.getBlockState(support).isAir()) {
                recordSkip(
                        run,
                        def,
                        ResultKind.SKIP_SETUP_REJECT,
                        "SOURCE_SUPPORT_OCCUPIED pos=" + support);
                clearOwned(run.level, owned);
                return null;
            }

            run.level.setBlock(
                    support,
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    2);
            owned.add(support);
        }

        // Frozen source assembly.
        for (int part = 0; part < sourceSpec.partCount(); part++) {
            BlockPos rail = sourceSpec.offsetForPart(
                    sourceRoot,
                    Direction.NORTH,
                    part);

            if (!run.level.getBlockState(rail).isAir()) {
                recordSkip(
                        run,
                        def,
                        ResultKind.SKIP_SETUP_REJECT,
                        "SOURCE_RAIL_OCCUPIED pos=" + rail);
                clearOwned(run.level, owned);
                return null;
            }

            run.level.setBlock(
                    rail,
                    sourceBlock.stateForPart(
                            Direction.NORTH,
                            part,
                            def.sourceActive()),
                    2);
            owned.add(rail);
            sourceRails.add(rail);
        }

        LegacyBatchTrackSpec.Endpoint sourceEndpoint =
                sourceSpec.endpoints().get(def.sourceEndpointIndex());

        BlockPos sourceLogicalEndpoint =
                sourceSpec.endpointPosition(
                        sourceRoot,
                        Direction.NORTH,
                        sourceEndpoint);

        Direction sourceOutward =
                endpointOutward(
                        sourceSpec,
                        Direction.NORTH,
                        sourceEndpoint);

        BlockPos requiredIncomingEndpoint =
                sourceLogicalEndpoint.relative(sourceOutward);

        // STEP_9_3G_T7_P1_R1_DIRECT_ONE_CORE_ENDPOINT_ALIGNMENT
        //
        // p1 originally tried to place the incoming assembly through item.useOn.
        // That tested PLAYER SNAP PLACEMENT rather than the runtime handoff itself.
        // After the t6 one-core rebuild, the known 45-Medium -> Medium-Diagonal
        // seam is a valid adjacent endpoint-core connection even though the item
        // snap finder rejects this synthetic support click.  The real c2 course
        // already builds connected assemblies directly from their exact specs.
        //
        // p1-r1 therefore aligns the incoming assembly by the frozen one-core
        // invariant:
        //   incoming logical endpoint == source logical endpoint + source outward
        //   incoming outward == opposite(source outward)
        // and then writes the exact normal segment states.  This isolates the
        // RUNTIME OWNERSHIP TRANSFER we actually need to certify.  Production
        // placement code is not called or changed by this diagnostic.
        LegacyBatchTrackSpec incomingSpec = def.incoming().spec();

        BlockPos incomingRoot = null;
        Direction incomingFacing = null;
        int incomingEndpointIndex = -1;

        List<Direction> facingOrder = List.of(
                Direction.NORTH,
                Direction.EAST,
                Direction.SOUTH,
                Direction.WEST);

        candidateSearch:
        for (Direction facing : facingOrder) {
            for (int endpointIndex = 0;
                 endpointIndex < incomingSpec.endpoints().size();
                 endpointIndex++) {

                LegacyBatchTrackSpec.Endpoint endpoint =
                        incomingSpec.endpoints().get(endpointIndex);

                if (endpointOutward(
                        incomingSpec,
                        facing,
                        endpoint)
                        != sourceOutward.getOpposite()) {
                    continue;
                }

                BlockPos offset =
                        incomingSpec.endpointPosition(
                                BlockPos.ZERO,
                                facing,
                                endpoint);

                BlockPos candidateRoot =
                        requiredIncomingEndpoint.subtract(offset);

                boolean clear = true;

                for (int part = 0;
                     part < incomingSpec.partCount();
                     part++) {

                    BlockPos rail =
                            incomingSpec.offsetForPart(
                                    candidateRoot,
                                    facing,
                                    part);

                    if (!run.level.getBlockState(rail).isAir()) {
                        clear = false;
                        break;
                    }
                }

                if (!clear) {
                    continue;
                }

                incomingRoot = candidateRoot;
                incomingFacing = facing;
                incomingEndpointIndex = endpointIndex;
                break candidateSearch;
            }
        }

        if (incomingRoot == null
                || incomingFacing == null
                || incomingEndpointIndex < 0) {
            recordSkip(
                    run,
                    def,
                    ResultKind.SKIP_NOT_COMPATIBLE,
                    "NO_DIRECT_ONE_CORE_MATCH sourceLogical="
                            + sourceLogicalEndpoint
                            + " sourceOutward="
                            + sourceOutward
                            + " required="
                            + requiredIncomingEndpoint);
            clearOwned(run.level, owned);
            return null;
        }

        if (KNOWN_ISOLATED_LOCO_FAILURES.contains(
                key(
                        incomingSpec.id(),
                        false,
                        incomingEndpointIndex))) {
            recordSkip(
                    run,
                    def,
                    ResultKind.SKIP_INCOMING_ISOLATED_FAIL,
                    "INCOMING_ISOLATED_FAIL endpoint="
                            + incomingEndpointIndex);
            clearOwned(run.level, owned);
            return null;
        }

        // Sturdy supports for the chosen exact incoming assembly only.
        for (int part = 0;
             part < incomingSpec.partCount();
             part++) {

            BlockPos support =
                    incomingSpec.supportForPart(
                            incomingRoot,
                            incomingFacing,
                            part);

            if (run.level.getBlockState(support).isAir()) {
                run.level.setBlock(
                        support,
                        Blocks.SMOOTH_STONE.defaultBlockState(),
                        2);
                owned.add(support);
            }
        }

        AbstractLegacyBatchRailBlock incomingBlock =
                def.incoming().block();

        for (int part = 0;
             part < incomingSpec.partCount();
             part++) {

            BlockPos rail =
                    incomingSpec.offsetForPart(
                            incomingRoot,
                            incomingFacing,
                            part);

            if (!run.level.getBlockState(rail).isAir()) {
                recordSkip(
                        run,
                        def,
                        ResultKind.SKIP_SETUP_REJECT,
                        "DIRECT_INCOMING_RAIL_OCCUPIED pos=" + rail);
                clearOwned(run.level, owned);
                return null;
            }

            run.level.setBlock(
                    rail,
                    incomingBlock.stateForPart(
                            incomingFacing,
                            part,
                            false),
                    2);
            owned.add(rail);
        }

        AssemblyInfo incoming =
                new AssemblyInfo(
                        incomingBlock,
                        incomingRoot,
                        incomingFacing);

        addIncomingOwned(owned, incoming);

        // Re-lock both assemblies after neighbor placement.
        for (int part = 0; part < sourceSpec.partCount(); part++) {
            BlockPos rail = sourceSpec.offsetForPart(
                    sourceRoot,
                    Direction.NORTH,
                    part);

            run.level.setBlock(
                    rail,
                    sourceBlock.stateForPart(
                            Direction.NORTH,
                            part,
                            def.sourceActive()),
                    2);
        }

        for (int part = 0; part < incomingSpec.partCount(); part++) {
            BlockPos rail = incomingSpec.offsetForPart(
                    incoming.root(),
                    incoming.facing(),
                    part);

            run.level.setBlock(
                    rail,
                    incoming.block().stateForPart(
                            incoming.facing(),
                            part,
                            false),
                    2);
        }

        BlockPos sourceCore =
                sourceSpec.offsetForPart(
                        sourceRoot,
                        Direction.NORTH,
                        sourceEndpoint.part());

        LegacyBatchTrackSpec.Endpoint incomingEndpoint =
                incomingSpec.endpoints().get(incomingEndpointIndex);

        BlockPos incomingCore =
                incomingSpec.offsetForPart(
                        incoming.root(),
                        incoming.facing(),
                        incomingEndpoint.part());

        Vec3 sourceCenter = railCenter(sourceCore);
        Vec3 incomingCenter = railCenter(incomingCore);

        Vec3 direction = new Vec3(
                incomingCenter.x - sourceCenter.x,
                0.0D,
                incomingCenter.z - sourceCenter.z);

        if (direction.horizontalDistanceSqr() < 1.0E-8D) {
            Vec3 requiredCenter = railCenter(requiredIncomingEndpoint);
            direction = new Vec3(
                    requiredCenter.x - sourceCenter.x,
                    0.0D,
                    requiredCenter.z - sourceCenter.z);
        }

        if (direction.horizontalDistanceSqr() < 1.0E-8D) {
            recordSkip(
                    run,
                    def,
                    ResultKind.SKIP_SETUP_REJECT,
                    "ZERO_CONNECTION_VECTOR sourceCore="
                            + sourceCore
                            + " incomingCore="
                            + incomingCore);
            clearOwned(run.level, owned);
            return null;
        }

        direction = direction.normalize();

        Vec3 startCenter = sourceCenter.subtract(
                direction.x * 0.20D,
                0.0D,
                direction.z * 0.20D);

        EntityType<?> probeType =
                ForgeRegistries.ENTITY_TYPES.getValue(
                        new ResourceLocation(
                                "traincraft",
                                run.probeEntityId));

        if (probeType == null) {
            throw new IllegalStateException(
                    "Missing probe entity: " + run.probeEntityId);
        }

        Entity raw = probeType.create(run.level);
        if (!(raw instanceof AbstractMinecart probe)) {
            if (raw != null) {
                raw.discard();
            }
            throw new IllegalStateException(
                    "Probe is not AbstractMinecart: "
                            + run.probeEntityId);
        }

        probe.setPos(
                startCenter.x,
                startCenter.y,
                startCenter.z);
        probe.setDeltaMovement(
                direction.x * TEST_SPEED,
                0.0D,
                direction.z * TEST_SPEED);
        probe.setYRot(yawFromVector(direction));

        if (!run.level.addFreshEntity(probe)) {
            probe.discard();
            throw new IllegalStateException(
                    "Could not add probe entity for pair ordinal="
                            + def.ordinal());
        }

        for (BlockPos pos : owned) {
            forced.add(
                    new ChunkPos(
                            pos.getX() >> 4,
                            pos.getZ() >> 4));
        }

        for (ChunkPos chunk : forced) {
            run.level.setChunkForced(
                    chunk.x,
                    chunk.z,
                    true);
        }

        AssemblyInfo sourceAssembly =
                new AssemblyInfo(
                        sourceBlock,
                        sourceRoot,
                        Direction.NORTH);

        ActiveCase active =
                new ActiveCase(
                        def,
                        sourceAssembly,
                        incoming,
                        incomingEndpointIndex,
                        sourceCore,
                        incomingCore,
                        owned,
                        forced,
                        probe,
                        startCenter,
                        direction);

        sampleTelemetry(active);
        return active;
    }

    private static void addIncomingOwned(
            Set<BlockPos> owned,
            AssemblyInfo incoming) {

        LegacyBatchTrackSpec spec =
                incoming.block().getSpec();

        for (int part = 0; part < spec.partCount(); part++) {
            owned.add(
                    spec.supportForPart(
                            incoming.root(),
                            incoming.facing(),
                            part));

            owned.add(
                    spec.offsetForPart(
                            incoming.root(),
                            incoming.facing(),
                            part));
        }
    }

    private static boolean placeIncomingAtSupport(
            ServerLevel level,
            FakePlayer fake,
            String id,
            BlockPos support) {

        Item item =
                ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(
                                "traincraft",
                                id));

        if (item == null || item == Items.AIR) {
            return false;
        }

        fake.setPos(
                support.getX() + 0.5D,
                support.getY() + 2.0D,
                support.getZ() + 4.5D);
        fake.setXRot(0.0F);
        fake.setYRot(180.0F);

        ItemStack stack = new ItemStack(item);
        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                stack);

        BlockHitResult hit =
                new BlockHitResult(
                        new Vec3(
                                support.getX() + 0.5D,
                                support.getY() + 1.0D,
                                support.getZ() + 0.5D),
                        Direction.UP,
                        support,
                        false);

        InteractionResult result =
                item.useOn(
                        new UseOnContext(
                                fake,
                                InteractionHand.MAIN_HAND,
                                hit));

        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);

        return result.consumesAction();
    }

    private static AssemblyInfo findAssembly(
            ServerLevel level,
            BlockPos center,
            String id) {

        AssemblyInfo best = null;
        double bestDistanceSq =
                Double.POSITIVE_INFINITY;

        for (int dx = -INCOMING_SCAN_RADIUS;
             dx <= INCOMING_SCAN_RADIUS;
             dx++) {
            for (int dz = -INCOMING_SCAN_RADIUS;
                 dz <= INCOMING_SCAN_RADIUS;
                 dz++) {
                for (int dy = -2; dy <= 2; dy++) {

                    BlockPos pos =
                            center.offset(
                                    dx,
                                    dy,
                                    dz);

                    BlockState state =
                            level.getBlockState(pos);

                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    if (!id.equals(batch.getSpec().id())) {
                        continue;
                    }

                    BlockPos root =
                            batch.getAssemblyRoot(
                                    pos,
                                    state);

                    Direction facing =
                            batch.getAssemblyFacing(state);

                    double distanceSq =
                            root.distSqr(center);

                    if (distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        best = new AssemblyInfo(
                                batch,
                                root,
                                facing);
                    }
                }
            }
        }

        return best;
    }

    private static int findMatchingEndpointIndex(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing,
            BlockPos required) {

        int match = -1;

        for (int i = 0;
             i < spec.endpoints().size();
             i++) {

            LegacyBatchTrackSpec.Endpoint endpoint =
                    spec.endpoints().get(i);

            if (spec.endpointPosition(
                    root,
                    facing,
                    endpoint).equals(required)) {

                if (match >= 0) {
                    return -1;
                }

                match = i;
            }
        }

        return match;
    }

    private static Direction endpointOutward(
            LegacyBatchTrackSpec spec,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint) {

        if (endpoint.part() == 2) {
            if ("track_switch_small_left".equals(spec.id())) {
                return spec.rotateDirection(
                        facing,
                        Direction.EAST);
            }

            if ("track_switch_small_right".equals(spec.id())) {
                return spec.rotateDirection(
                        facing,
                        Direction.WEST);
            }
        }

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
                                + spec.id());
            }

            return spec.rotateDirection(
                    facing,
                    canonical);
        }

        return spec.rotateDirection(
                facing,
                endpoint.outward());
    }

    private static void sampleTelemetry(
            ActiveCase active) {

        active.lastPosition =
                active.probe.position();

        active.lastMotion =
                active.probe.getDeltaMovement();

        active.maxDistanceFromStart =
                Math.max(
                        active.maxDistanceFromStart,
                        horizontalDistance(
                                active.lastPosition,
                                active.startCenter));

        if (onAssembly(
                active.probe,
                active.sourceAssembly)) {
            active.enteredSource = true;
        }

        if (onAssembly(
                active.probe,
                active.incomingAssembly)) {
            active.enteredIncoming = true;
        }
    }

    private static boolean onAssembly(
            AbstractMinecart probe,
            AssemblyInfo assembly) {

        BlockPos base = probe.blockPosition();

        for (BlockPos pos
                : List.of(
                        base,
                        base.below(),
                        base.above())) {

            BlockState state =
                    probe.level().getBlockState(pos);

            if (state.getBlock()
                    != assembly.block()) {
                continue;
            }

            BlockPos root =
                    assembly.block()
                            .getAssemblyRoot(
                                    pos,
                                    state);

            if (assembly.root().equals(root)) {
                return true;
            }
        }

        return false;
    }

    private static void complete(
            RunState run,
            ActiveCase active,
            boolean pass,
            String reason) {

        if (active.done) {
            return;
        }

        active.done = true;

        String detailed =
                reason
                        + ";sourcePart="
                        + active.def.source()
                                .spec()
                                .endpoints()
                                .get(active.def.sourceEndpointIndex())
                                .part()
                        + ";incomingEndpoint="
                        + active.incomingEndpointIndex
                        + ";incomingPart="
                        + active.def.incoming()
                                .spec()
                                .endpoints()
                                .get(active.incomingEndpointIndex)
                                .part()
                        + ";incomingFacing="
                        + active.incomingAssembly.facing()
                        + ";sourceCore="
                        + active.sourceCore
                        + ";incomingCore="
                        + active.incomingCore
                        + ";finalPos="
                        + vec(active.lastPosition)
                        + ";finalMotion="
                        + vec(active.lastMotion)
                        + ";enteredSource="
                        + active.enteredSource
                        + ";enteredIncoming="
                        + active.enteredIncoming
                        + ";maxFromStart="
                        + f(active.maxDistanceFromStart)
                        + ";entityTickDelta="
                        + (active.probe.tickCount
                                - active.spawnEntityTickCount)
                        + ";serverWrapperFallbackTicks="
                        + active.serverWrapperFallbackTicks;

        run.results.add(
                new PairResult(
                        active.def,
                        pass
                                ? ResultKind.PASS
                                : ResultKind.FAIL,
                        active.ticks,
                        active.incomingEndpointIndex,
                        detailed));

        if (!active.probe.isRemoved()) {
            active.probe.discard();
        }
    }

    private static void recordSkip(
            RunState run,
            PairDef def,
            ResultKind kind,
            String reason) {

        run.results.add(
                new PairResult(
                        def,
                        kind,
                        0,
                        -1,
                        reason));
    }

    private static void cleanupActive(
            RunState run) {

        for (ActiveCase active : run.active) {
            if (!active.probe.isRemoved()) {
                active.probe.discard();
            }

            for (ChunkPos chunk : active.forcedChunks) {
                try {
                    run.level.setChunkForced(
                            chunk.x,
                            chunk.z,
                            false);
                } catch (Throwable ignored) {
                }
            }

            clearOwned(
                    run.level,
                    active.ownedBlocks);
        }

        run.active.clear();
    }

    private static void clearOwned(
            ServerLevel level,
            Set<BlockPos> owned) {

        List<BlockPos> reverse =
                new ArrayList<>(owned);

        for (int i = reverse.size() - 1;
             i >= 0;
             i--) {

            BlockPos pos = reverse.get(i);

            level.setBlock(
                    pos,
                    Blocks.AIR.defaultBlockState(),
                    2);
        }
    }

    private static void finish(
            RunState run) throws Exception {

        cleanupActive(run);

        long pass = run.results.stream()
                .filter(r -> r.kind() == ResultKind.PASS)
                .count();

        long fail = run.results.stream()
                .filter(r -> r.kind() == ResultKind.FAIL)
                .count();

        long incompatible = run.results.stream()
                .filter(r ->
                        r.kind()
                                == ResultKind.SKIP_NOT_COMPATIBLE)
                .count();

        long incomingIsolatedSkip = run.results.stream()
                .filter(r ->
                        r.kind()
                                == ResultKind.SKIP_INCOMING_ISOLATED_FAIL)
                .count();

        long setupSkip = run.results.stream()
                .filter(r ->
                        r.kind()
                                == ResultKind.SKIP_SETUP_REJECT)
                .count();

        List<String> report = new ArrayList<>();

        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-p1-r1");
        report.add("HEADLESS FLAT CONNECTED HANDOFF MATRIX");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PROBE_ENTITY=traincraft:" + run.probeEntityId);
        report.add("PAIR_FILTER=" + String.valueOf(System.getenv(FILTER_ENV)));
        report.add("SPEC_COUNT=" + EXPECTED_SPECS);
        report.add("SLOPE_SPECS_EXCLUDED=YES");
        report.add("SOURCE_KNOWN_ISOLATED_FAILURES_EXCLUDED=YES");
        report.add("INCOMING_KNOWN_ISOLATED_FAILURES_SKIPPED=YES");
        report.add("PAIR_SETUP=DIRECT_ONE_CORE_ENDPOINT_ALIGNMENT");
        report.add("PRODUCTION_PLACEMENT_CODE_CALLED=NO");
        report.add("ENTITY_PATH=REAL_SERVERLEVEL_TICKNONPASSENGER_OR_WORLD_TICK");
        report.add("CANDIDATE_PAIR_CASES=" + run.candidates.size());
        report.add("PAIR_PASS=" + pass);
        report.add("PAIR_FAIL=" + fail);
        report.add("SKIP_NOT_COMPATIBLE=" + incompatible);
        report.add("SKIP_INCOMING_ISOLATED_FAIL=" + incomingIsolatedSkip);
        report.add("SKIP_SETUP_REJECT=" + setupSkip);
        report.add("");

        for (PairResult result : run.results) {
            PairDef def = result.def();
            LegacyBatchTrackSpec.Endpoint sourceEndpoint =
                    def.source().spec()
                            .endpoints()
                            .get(def.sourceEndpointIndex());

            report.add(
                    "PAIR ordinal="
                            + def.ordinal()
                            + " source="
                            + def.source().spec().id()
                            + " sourceActive="
                            + def.sourceActive()
                            + " sourceEndpointIndex="
                            + def.sourceEndpointIndex()
                            + " sourcePart="
                            + sourceEndpoint.part()
                            + " incoming="
                            + def.incoming().spec().id()
                            + " kind="
                            + result.kind()
                            + " ticks="
                            + result.ticks()
                            + " incomingEndpoint="
                            + result.incomingEndpointIndex()
                            + " reason="
                            + result.reason());
        }

        report.add("");
        report.add("KNOWN_SEAM_FILTER=source=track_switch_45_medium_right sourceActive=true incoming=track_diagonal_straight_medium");

        for (PairResult result : run.results) {
            PairDef def = result.def();

            if (!"track_switch_45_medium_right"
                    .equals(def.source().spec().id())
                    || !def.sourceActive()
                    || !"track_diagonal_straight_medium"
                    .equals(def.incoming().spec().id())) {
                continue;
            }

            LegacyBatchTrackSpec.Endpoint sourceEndpoint =
                    def.source().spec()
                            .endpoints()
                            .get(def.sourceEndpointIndex());

            report.add(
                    "KNOWN_SEAM_CASE ordinal="
                            + def.ordinal()
                            + " sourceEndpointIndex="
                            + def.sourceEndpointIndex()
                            + " sourcePart="
                            + sourceEndpoint.part()
                            + " kind="
                            + result.kind()
                            + " incomingEndpoint="
                            + result.incomingEndpointIndex()
                            + " reason="
                            + result.reason());
        }

        report.add("");
        long executed = pass + fail;
        report.add("EXECUTED_PAIR_CASES=" + executed);
        report.add(
                "RESULT="
                        + (executed == 0
                        ? "T7_P1_R1_NO_EXECUTED_PAIRS"
                        : (fail == 0 && setupSkip == 0
                        ? "T7_P1_R1_FLAT_HANDOFF_MATRIX_PASS"
                        : "T7_P1_R1_FLAT_HANDOFF_MATRIX_REVIEW_REQUIRED")));

        Path output =
                Path.of(
                        "logs/traincraft-track-debug/t7-headless-pair-matrix-"
                                + run.probeEntityId
                                + "-"
                                + STAMP.format(
                                        LocalDateTime.now())
                                + ".txt")
                        .toAbsolutePath();

        Files.createDirectories(
                output.getParent());

        Files.write(
                output,
                report,
                StandardCharsets.UTF_8);

        run.finished = true;
        RUN = null;

        String summary =
                "TC t7 p1 complete probe="
                        + run.probeEntityId
                        + " pass="
                        + pass
                        + " fail="
                        + fail
                        + " incompatible="
                        + incompatible
                        + " incomingIsolatedSkip="
                        + incomingIsolatedSkip
                        + " setupSkip="
                        + setupSkip
                        + " report="
                        + output;

        if (run.player != null) {
            run.player.sendSystemMessage(
                    Component.literal(summary));
        } else {
            System.out.println(
                    "[TC-T7-P1] " + summary);
        }

        if (run.headless) {
            run.level.getServer().halt(false);
        }
    }

    private static void failWholeRun(
            RunState run,
            Throwable t) {

        try {
            cleanupActive(run);
        } catch (Throwable ignored) {
        }

        run.finished = true;
        RUN = null;

        String message =
                "TC t7 p1 failed probe="
                        + run.probeEntityId
                        + ": "
                        + t.getClass().getSimpleName()
                        + ": "
                        + String.valueOf(t.getMessage());

        if (run.player != null) {
            run.player.sendSystemMessage(
                    Component.literal(message));
        }

        System.err.println(
                "[TC-T7-P1] " + message);

        t.printStackTrace();

        if (run.headless) {
            run.level.getServer().halt(false);
        }
    }

    private static List<SpecEntry> collectSpecs()
            throws IllegalAccessException {

        List<SpecEntry> specs =
                new ArrayList<>();

        for (Field field
                : LegacyBatchTrackSpecs.class
                        .getDeclaredFields()) {

            if (!Modifier.isStatic(
                    field.getModifiers())
                    || !LegacyBatchTrackSpec.class
                            .isAssignableFrom(
                                    field.getType())) {
                continue;
            }

            field.setAccessible(true);

            LegacyBatchTrackSpec spec =
                    (LegacyBatchTrackSpec)
                            field.get(null);

            if (spec == null) {
                continue;
            }

            Block rawBlock =
                    ForgeRegistries.BLOCKS.getValue(
                            new ResourceLocation(
                                    "traincraft",
                                    spec.id()
                                            + "_segment"));

            if (!(rawBlock
                    instanceof AbstractLegacyBatchRailBlock block)) {
                throw new IllegalStateException(
                        "Missing batch block for "
                                + spec.id()
                                + ": "
                                + rawBlock);
            }

            specs.add(
                    new SpecEntry(
                            field.getName(),
                            spec,
                            block,
                            block
                                    instanceof LegacyBatchSwitchRailBlock));
        }

        specs.sort(
                Comparator.comparing(
                        entry ->
                                entry.spec().id()));

        return specs;
    }

    private static List<PairDef> buildCandidates(
            List<SpecEntry> specs,
            String filter) {

        List<PairDef> out =
                new ArrayList<>();

        int ordinal = 0;

        for (SpecEntry source : specs) {
            if (source.spec().isSlope()) {
                continue;
            }

            List<Boolean> states =
                    source.isSwitch()
                            ? List.of(false, true)
                            : List.of(false);

            for (boolean active : states) {
                for (int sourceEndpointIndex = 0;
                     sourceEndpointIndex
                             < source.spec()
                                     .endpoints()
                                     .size();
                     sourceEndpointIndex++) {

                    if (KNOWN_ISOLATED_LOCO_FAILURES.contains(
                            key(
                                    source.spec().id(),
                                    active,
                                    sourceEndpointIndex))) {
                        continue;
                    }

                    for (SpecEntry incoming : specs) {
                        if (incoming.spec().isSlope()) {
                            continue;
                        }

                        if ("known".equalsIgnoreCase(filter)
                                && (!"track_switch_45_medium_right".equals(source.spec().id())
                                || !active
                                || !"track_diagonal_straight_medium".equals(incoming.spec().id()))) {
                            continue;
                        }

                        out.add(
                                new PairDef(
                                        ordinal++,
                                        source,
                                        active,
                                        sourceEndpointIndex,
                                        incoming));
                    }
                }
            }
        }

        return out;
    }

    private static String key(
            String id,
            boolean active,
            int endpointIndex) {

        return id
                + "|"
                + active
                + "|"
                + endpointIndex;
    }

    private static Vec3 railCenter(
            BlockPos pos) {

        return new Vec3(
                pos.getX() + 0.5D,
                pos.getY() + 0.0625D,
                pos.getZ() + 0.5D);
    }

    private static float yawFromVector(
            Vec3 direction) {

        return (float)
                Math.toDegrees(
                        Math.atan2(
                                -direction.x,
                                direction.z));
    }

    private static double horizontalDistance(
            Vec3 a,
            Vec3 b) {

        return Math.hypot(
                a.x - b.x,
                a.z - b.z);
    }

    private static String f(
            double value) {

        if (!Double.isFinite(value)) {
            return "INF";
        }

        return String.format(
                Locale.ROOT,
                "%.6f",
                value);
    }

    private static String vec(
            Vec3 vec) {

        return "("
                + f(vec.x)
                + ","
                + f(vec.y)
                + ","
                + f(vec.z)
                + ")";
    }
}
