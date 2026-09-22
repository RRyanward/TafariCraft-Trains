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
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Step 9.3g-t7-d2 — all-rails traversal / follower-switch routing probe.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * This diagnostic deliberately does NOT retune locomotive acceleration,
 * braking, coupling spacing, speed caps, track topology, or track visuals.
 *
 * It drives vanilla Minecart probes through the already-frozen physical
 * assemblies while calling the same public Traincraft continuous-path entry
 * points used by the locomotive and follower rolling stock:
 *
 *   leader mode   -> LegacyContinuousTrackPath.apply(...)
 *   follower mode -> LegacyContinuousTrackPath.applyFollowerSwitchRouting(...)
 *
 * Every declared endpoint is tested as an entry. Switches are tested in both
 * inactive and active states. The follower routing entry point is additionally
 * tested for every switch endpoint/state. Cases run in small parallel batches
 * so even the largest 31-block-radius curves do not create one enormous loaded
 * gallery.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7TraversalSweepCommand {
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

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

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
        final Minecart probe;
        final Vec3 startCenter;

        Vec3 preMotion;
        Vec3 lastHorizontalDirection;
        int ticks;
        boolean done;

        ActiveCase(
                CaseDef def,
                BlockPos root,
                List<BlockPos> ownedBlocks,
                Minecart probe,
                Vec3 startCenter,
                Vec3 initialDirection) {
            this.def = def;
            this.root = root;
            this.ownedBlocks = ownedBlocks;
            this.probe = probe;
            this.startCenter = startCenter;
            this.preMotion = probe.getDeltaMovement();
            this.lastHorizontalDirection = initialDirection;
        }
    }

    private static final class RunState {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos origin;
        final List<CaseDef> cases;
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
                List<CaseDef> cases) {
            this.player = player;
            this.level = level;
            this.origin = origin;
            this.cases = cases;
        }
    }

    private static RunState RUN;

    private TrackT7TraversalSweepCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_traversal_sweep")
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
                Commands.literal("tc_t7_traversal_status")
                        .executes(context ->
                                status(context.getSource().getPlayerOrException()))
        );

        event.getDispatcher().register(
                Commands.literal("tc_t7_traversal_abort")
                        .executes(context ->
                                abort(context.getSource().getPlayerOrException()))
        );
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
        if (RUN != null && !RUN.finished) {
            player.sendSystemMessage(Component.literal(
                    "TC t7 traversal sweep is already running. "
                            + "Use /tc_t7_traversal_status or /tc_t7_traversal_abort."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        List<SpecEntry> specs = collectSpecs();

        if (specs.size() != EXPECTED_SPECS) {
            throw new IllegalStateException(
                    "Expected " + EXPECTED_SPECS
                            + " specs, found " + specs.size());
        }

        List<CaseDef> cases = buildCases(specs);

        int rootY = Math.max(
                level.getMinBuildHeight() + 12,
                Math.min(
                        level.getMaxBuildHeight() - 24,
                        player.getBlockY() + 24));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 48,
                rootY,
                player.getBlockZ() + 48);

        RUN = new RunState(player, level, origin, cases);

        player.sendSystemMessage(Component.literal(
                "TC t7 traversal sweep started: "
                        + cases.size()
                        + " cases in batches of "
                        + BATCH_SIZE + "."));
        player.sendSystemMessage(Component.literal(
                "Leader mode covers every declared endpoint. "
                        + "Follower mode additionally covers every switch endpoint/state."));
        player.sendSystemMessage(Component.literal(
                "This can take several minutes. Use /tc_t7_traversal_status for progress."));

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

            if (active.def.mode() == ProbeMode.LEADER) {
                LegacyContinuousTrackPath.apply(
                        active.probe,
                        active.preMotion,
                        true,
                        false,
                        false,
                        0.30D,
                        0,
                        active.probe.getYRot());
            } else {
                LegacyContinuousTrackPath.applyFollowerSwitchRouting(
                        active.probe,
                        active.preMotion);
            }

            Vec3 post = active.probe.getDeltaMovement();
            Vec3 horizontal = new Vec3(post.x, 0.0D, post.z);
            if (horizontal.horizontalDistanceSqr() > 1.0E-8D) {
                active.lastHorizontalDirection =
                        horizontal.normalize();
            }

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

        if (!run.active.isEmpty()) {
            BlockPos firstRoot = run.active.get(0).root;
            double centerX = firstRoot.getX()
                    + ((BATCH_COLUMNS - 1) * SLOT_SPACING) / 2.0D;
            double centerZ = firstRoot.getZ()
                    + SLOT_SPACING / 2.0D;

            run.player.teleportTo(
                    run.level,
                    centerX,
                    firstRoot.getY() + 48.0D,
                    centerZ,
                    180.0F,
                    80.0F);
        }

        run.player.sendSystemMessage(Component.literal(
                "TC t7 traversal batch "
                        + run.batchNumber
                        + " started; cases completed so far "
                        + run.results.size()
                        + "/" + run.cases.size() + "."));
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

        // Verify target cells are clear before this batch touches them.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    part);

            BlockPos supportBottom = spec.supportForPart(
                    root,
                    Direction.NORTH,
                    part);

            for (BlockPos cursor = supportBottom;
                 cursor.getY() <= rail.getY() - 1;
                 cursor = cursor.above()) {

                run.level.getChunkAt(cursor);

                if (!run.level.getBlockState(cursor).isAir()) {
                    throw new IllegalStateException(
                            "Traversal slot is not clear at support "
                                    + cursor
                                    + ". Move to a fresh open area and rerun.");
                }
            }

            run.level.getChunkAt(rail);
            if (!run.level.getBlockState(rail).isAir()) {
                throw new IllegalStateException(
                        "Traversal slot is not clear at rail "
                                + rail
                                + ". Move to a fresh open area and rerun.");
            }
        }

        // Build only the assembly and its required supports.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(
                    root,
                    Direction.NORTH,
                    part);

            BlockPos supportBottom = spec.supportForPart(
                    root,
                    Direction.NORTH,
                    part);

            for (BlockPos cursor = supportBottom;
                 cursor.getY() <= rail.getY() - 1;
                 cursor = cursor.above()) {

                run.level.setBlock(
                        cursor,
                        Blocks.SMOOTH_STONE.defaultBlockState(),
                        2);
                owned.add(cursor);
            }

            BlockState state = block.stateForPart(
                    Direction.NORTH,
                    part,
                    def.active());

            run.level.setBlock(rail, state, 2);
            owned.add(rail);
        }

        LegacyBatchTrackSpec.Endpoint startEndpoint =
                spec.endpoints().get(def.startEndpointIndex());

        BlockPos startCore = spec.offsetForPart(
                root,
                Direction.NORTH,
                startEndpoint.part());

        Vec3 startCenter = railCenter(startCore);
        Vec3 inward = initialInwardDirection(def);

        Minecart probe = EntityType.MINECART.create(run.level);
        if (probe == null) {
            throw new IllegalStateException("Could not create vanilla Minecart probe.");
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
                    "Could not add Minecart probe for " + def);
        }

        return new ActiveCase(
                def,
                root,
                owned,
                probe,
                startCenter,
                inward);
    }

    private static int detectExit(ActiveCase active) {
        if (active.ticks < MIN_EXIT_TICKS) {
            return -1;
        }

        LegacyBatchTrackSpec spec = active.def.entry().spec();
        Vec3 position = active.probe.position();
        Vec3 motion = active.probe.getDeltaMovement();
        Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);

        Vec3 moveDir = horizontal.horizontalDistanceSqr() > 1.0E-8D
                ? horizontal.normalize()
                : active.lastHorizontalDirection;

        for (int index = 0; index < spec.endpoints().size(); index++) {
            if (index == active.def.startEndpointIndex()) {
                continue;
            }

            LegacyBatchTrackSpec.Endpoint endpoint =
                    spec.endpoints().get(index);

            BlockPos core = spec.offsetForPart(
                    active.root,
                    Direction.NORTH,
                    endpoint.part());

            Vec3 center = railCenter(core);

            Direction outwardDirection =
                    spec.rotateDirection(
                            Direction.NORTH,
                            endpoint.outward());

            Vec3 outward = new Vec3(
                    outwardDirection.getStepX(),
                    0.0D,
                    outwardDirection.getStepZ());

            double motionOutwardDot =
                    moveDir.x * outward.x
                            + moveDir.z * outward.z;

            boolean sharedCore =
                    core.equals(
                            spec.offsetForPart(
                                    active.root,
                                    Direction.NORTH,
                                    spec.endpoints()
                                            .get(active.def.startEndpointIndex())
                                            .part()));

            if (sharedCore) {
                Vec3 fromStart = position.subtract(active.startCenter);
                double progress =
                        fromStart.x * outward.x
                                + fromStart.z * outward.z;

                double lateralSq =
                        fromStart.horizontalDistanceSqr()
                                - progress * progress;

                if (progress >= SHARED_CORE_EXIT_PROGRESS
                        && lateralSq <= 0.50D * 0.50D
                        && motionOutwardDot > 0.20D) {
                    return index;
                }

                continue;
            }

            double distance = position.distanceTo(center);
            if (distance <= ENDPOINT_CAPTURE_DISTANCE
                    && motionOutwardDot > 0.10D) {
                return index;
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

        run.results.add(
                new CaseResult(
                        active.def,
                        pass,
                        active.ticks,
                        exitEndpointIndex,
                        reason));

        if (!active.probe.isRemoved()) {
            active.probe.discard();
        }
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
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-d2");
        report.add("ALL-RAILS TRAVERSAL / FOLLOWER-SWITCH ROUTING SWEEP");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("SPECS=50");
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
                ? "T7_D2_TRAVERSAL_AND_FOLLOWER_ROUTING_PASS"
                : "T7_D2_TRAVERSAL_AND_FOLLOWER_ROUTING_FAIL"));

        Path output = Path.of(
                "t7-all-rails-traversal-sweep-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.write(
                output,
                report,
                StandardCharsets.UTF_8);

        run.finished = true;
        RUN = null;

        run.player.sendSystemMessage(Component.literal(
                "TC t7 traversal sweep complete: leader "
                        + leaderPass + "/" + leaderCases
                        + ", follower-switch "
                        + followerPass + "/" + followerCases
                        + ", reciprocityFailures="
                        + reciprocityFailures
                        + ", switchSensitivityFailures="
                        + switchSensitivityFailures + "."));

        run.player.sendSystemMessage(Component.literal(
                "Traversal report: " + output));

        run.player.sendSystemMessage(Component.literal(
                "Upload the t7-all-rails-traversal-sweep TXT here."));
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

    private static void failWholeRun(
            RunState run,
            Throwable t) {

        try {
            cleanupActive(run);
        } catch (Throwable ignored) {
        }

        run.finished = true;
        RUN = null;

        run.player.sendSystemMessage(Component.literal(
                "TC t7 traversal sweep failed: "
                        + t.getClass().getSimpleName()
                        + ": "
                        + String.valueOf(t.getMessage())));
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

            if (entry.isSwitch()) {
                for (boolean active : List.of(false, true)) {
                    for (int start = 0;
                         start < entry.spec().endpoints().size();
                         start++) {

                        cases.add(
                                new CaseDef(
                                        ordinal++,
                                        entry,
                                        ProbeMode.FOLLOWER_SWITCH,
                                        active,
                                        start));
                    }
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
