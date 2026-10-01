package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 9.3g-t7-sl1 — connected straight-slope headless gate.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * Tests the already-accepted straight slope family:
 *   track_slope_small      (6 cells)
 *   track_slope_long       (12 cells)
 *   track_slope_very_long  (18 cells)
 *
 * Each slope is placed through its REAL registered item/useOn() path.
 * Vanilla flat rail is connected to BOTH physical ends at the proper Y levels.
 * A powered Small Steam locomotive then traverses each course:
 *   uphill  low flat -> slope -> high flat
 *   downhill high flat -> slope -> low flat
 *
 * Six total cases. The test advances ServerLevel gameTime exactly +1 before
 * each direct tickNonPassenger call, matching the corrected rp1-r2/r3 clock.
 *
 * Production movement/topology/placement/resources are never changed here.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7StraightSlopeHeadlessCommand {
    private static final String ENV = "TC_T7_SLOPE1_HEADLESS";

    private static final int RUNOUT = 8;
    private static final int MAX_TICKS = 240;
    private static final double START_SPEED = 0.12D;
    private static final double STALL_SPEED = 0.008D;
    private static final int STALL_TICKS = 10;
    private static final double REQUIRED_VERTICAL_TRAVEL = 0.70D;
    private static final double EXIT_ADVANCE = 1.15D;
    private static final double MAX_LATERAL_ERROR = 1.10D;
    private static final double RAIL_SURFACE_Y = 0.0625D;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private record SlopeDef(
            String id,
            LegacyBatchTrackSpec spec,
            int blocks) {
    }

    private record CaseDef(
            SlopeDef slope,
            boolean uphill) {

        String label() {
            return slope.id() + "_" + (uphill ? "uphill" : "downhill");
        }
    }

    private record Course(
            BlockPos root,
            BlockPos lowEnd,
            BlockPos highEnd,
            Set<BlockPos> owned,
            Set<ChunkPos> forced) {
    }

    private record CaseResult(
            String label,
            boolean pass,
            String classification,
            int ticks,
            boolean enteredSlope,
            boolean reachedExitFlat,
            double minHorizontalSpeed,
            double maxHorizontalStep,
            double maxVerticalStep,
            double verticalTravel,
            double maxLateralError,
            Vec3 finalPosition,
            Vec3 finalMotion) {
    }

    private static final List<SlopeDef> SLOPES = List.of(
            new SlopeDef(
                    "track_slope_small",
                    LegacyBatchTrackSpecs.SLOPE_SMALL,
                    6),
            new SlopeDef(
                    "track_slope_long",
                    LegacyBatchTrackSpecs.SLOPE_LONG,
                    12),
            new SlopeDef(
                    "track_slope_very_long",
                    LegacyBatchTrackSpecs.SLOPE_VERY_LONG,
                    18)
    );

    private static final List<CaseDef> CASES = List.of(
            new CaseDef(SLOPES.get(0), true),
            new CaseDef(SLOPES.get(0), false),
            new CaseDef(SLOPES.get(1), true),
            new CaseDef(SLOPES.get(1), false),
            new CaseDef(SLOPES.get(2), true),
            new CaseDef(SLOPES.get(2), false)
    );

    private TrackT7StraightSlopeHeadlessCommand() {
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
                writeHarnessFailure(t);
            } catch (Throwable ignored) {
            }
            server.halt(false);
        }
    }

    private static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.stopRiding();

        BlockPos spawn = level.getSharedSpawnPos();
        int supportY = Math.max(
                level.getMinBuildHeight() + 24,
                Math.min(
                        level.getMaxBuildHeight() - 32,
                        spawn.getY() + 34));

        BlockPos origin = new BlockPos(
                spawn.getX() + 2300,
                supportY,
                spawn.getZ() + 2300);

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-sl1");
        report.add("CONNECTED STRAIGHT-SLOPE HEADLESS GATE");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PLACEMENT_PATH=REAL_REGISTERED_SLOPE_ITEM_USEON");
        report.add("ENTITY_PATH=POWERED_SMALL_STEAM_DIRECT_SERVERLEVEL_TICKNONPASSENGER");
        report.add("TEST_CLOCK=EXPLICIT_GAME_TIME_PLUS_ONE_PER_PROBE_TICK");
        report.add("SLOPE_FAMILY=SMALL_LONG_VERY_LONG");
        report.add("DIRECTIONS=UPHILL_AND_DOWNHILL");
        report.add("CASE_COUNT=" + CASES.size());
        report.add("");

        List<CaseResult> results = new ArrayList<>();

        for (int i = 0; i < CASES.size(); i++) {
            CaseDef def = CASES.get(i);
            BlockPos desired = origin.offset(i * 110, 0, 0);
            BlockPos support = findCleanSupportAnchor(level, desired);

            if (!support.equals(desired)) {
                report.add(
                        "CASE_GRID_RELOCATED label=" + def.label()
                                + " desired=" + desired
                                + " actual=" + support);
            }

            CaseResult result = runCase(
                    level,
                    fake,
                    support,
                    def,
                    report);

            results.add(result);
            report.add("");
        }

        long passCount = results.stream()
                .filter(CaseResult::pass)
                .count();

        report.add("SUMMARY");
        report.add("PASS_COUNT=" + passCount);
        report.add("FAIL_COUNT=" + (results.size() - passCount));

        for (CaseResult r : results) {
            report.add(String.format(
                    Locale.ROOT,
                    "CASE_SUMMARY label=%s PASS=%s classification=%s ticks=%d enteredSlope=%s reachedExitFlat=%s minHorizontalSpeed=%.9f maxOneTickHorizontalDisplacement=%.9f maxOneTickVerticalDelta=%.9f verticalTravel=%.9f maxLateralError=%.9f finalPos=(%.6f,%.6f,%.6f) finalMotion=(%.6f,%.6f,%.6f)",
                    r.label(),
                    Boolean.toString(r.pass()),
                    r.classification(),
                    r.ticks(),
                    Boolean.toString(r.enteredSlope()),
                    Boolean.toString(r.reachedExitFlat()),
                    r.minHorizontalSpeed(),
                    r.maxHorizontalStep(),
                    r.maxVerticalStep(),
                    r.verticalTravel(),
                    r.maxLateralError(),
                    r.finalPosition().x,
                    r.finalPosition().y,
                    r.finalPosition().z,
                    r.finalMotion().x,
                    r.finalMotion().y,
                    r.finalMotion().z));
        }

        report.add("");
        report.add("RESULT="
                + (passCount == results.size()
                ? "T7_SL1_CONNECTED_STRAIGHT_SLOPES_PASS"
                : "T7_SL1_CONNECTED_STRAIGHT_SLOPES_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-sl1-connected-straight-slopes-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, report, StandardCharsets.UTF_8);

        System.out.println(
                "[TC-T7-SL1] complete pass="
                        + passCount + "/" + results.size()
                        + " report=" + output);

        fake.stopRiding();
        server.halt(false);
    }

    private static CaseResult runCase(
            ServerLevel level,
            FakePlayer fake,
            BlockPos support,
            CaseDef def,
            List<String> report) {

        String label = def.label();
        report.add("CASE_BEGIN label=" + label);

        Course course = null;
        SmallSteamLocomotive loco = null;

        try {
            fake.stopRiding();

            course = buildCourse(
                    level,
                    fake,
                    support,
                    def.slope());

            Vec3 travel = def.uphill()
                    ? new Vec3(0.0D, 0.0D, -1.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);

            BlockPos startRail = def.uphill()
                    ? course.lowEnd().south(RUNOUT - 2)
                    : course.highEnd().north(RUNOUT - 2);

            Vec3 start = new Vec3(
                    startRail.getX() + 0.5D,
                    startRail.getY() + RAIL_SURFACE_Y,
                    startRail.getZ() + 0.5D);

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation(
                            "traincraft",
                            "locomotive_steam_small"));

            if (type == null) {
                return setupFailure(
                        label,
                        "MISSING_LOCOMOTIVE_ENTITY_TYPE");
            }

            Entity raw = type.create(level);
            if (!(raw instanceof SmallSteamLocomotive steam)) {
                if (raw != null) {
                    raw.discard();
                }
                return setupFailure(
                        label,
                        "LOCOMOTIVE_NOT_SMALL_STEAM");
            }

            loco = steam;
            loco.setPos(start.x, start.y, start.z);
            loco.setYRot(bodyYawForTravel(travel));
            loco.setDeltaMovement(
                    travel.x * START_SPEED,
                    0.0D,
                    travel.z * START_SPEED);

            setIntField(loco, "steamAmount", 1000);

            if (!level.addFreshEntity(loco)) {
                loco.discard();
                loco = null;
                return setupFailure(label, "ADD_LOCOMOTIVE_FAILED");
            }

            fake.setPos(start.x, start.y + 1.0D, start.z);
            if (!fake.startRiding(loco, true)) {
                return setupFailure(label, "FAKE_DRIVER_COULD_NOT_RIDE");
            }

            loco.setDriverControls(true, false, false);

            report.add(
                    "SETUP label=" + label
                            + " slope=" + def.slope().id()
                            + " blocks=" + def.slope().blocks()
                            + " direction="
                            + (def.uphill() ? "UPHILL" : "DOWNHILL")
                            + " root=" + course.root()
                            + " lowEnd=" + course.lowEnd()
                            + " highEnd=" + course.highEnd()
                            + " startRail=" + startRail
                            + " start=" + vec(start)
                            + " initialMotion="
                            + vec(loco.getDeltaMovement()));

            double minSpeed = Double.POSITIVE_INFINITY;
            double maxHorizontalStep = 0.0D;
            double maxVerticalStep = 0.0D;
            double maxLateralError = 0.0D;
            double verticalTravel = 0.0D;

            boolean enteredSlope = false;
            boolean reachedExitFlat = false;
            String classification = "TIMEOUT";
            int ticks = 0;
            int stallTicks = 0;

            Vec3 previous = loco.position();

            for (int tick = 1; tick <= MAX_TICKS; tick++) {
                ticks = tick;

                if (loco.isRemoved()) {
                    classification = "LOCOMOTIVE_REMOVED";
                    break;
                }

                Vec3 prePos = loco.position();
                Vec3 preMotion = loco.getDeltaMovement();

                loco.setDriverControls(true, false, false);

                long gameTimeBefore = level.getGameTime();
                advanceDeterministicGameTime(level);
                long gameTimeForTick = level.getGameTime();

                level.tickNonPassenger(loco);

                if (loco.isRemoved()) {
                    report.add(
                            "TRACE label=" + label
                                    + " tick=" + tick
                                    + " gameTimeBefore=" + gameTimeBefore
                                    + " gameTimeForTick=" + gameTimeForTick
                                    + " postOwner=REMOVED"
                                    + " prePos=" + vec(prePos)
                                    + " preMotion=" + vec(preMotion));
                    classification = "LOCOMOTIVE_REMOVED";
                    break;
                }

                Vec3 postPos = loco.position();
                Vec3 postMotion = loco.getDeltaMovement();

                double speed = horizontalSpeed(postMotion);
                minSpeed = Math.min(minSpeed, speed);

                double horizontalStep = Math.hypot(
                        postPos.x - prePos.x,
                        postPos.z - prePos.z);
                double verticalStep = Math.abs(
                        postPos.y - prePos.y);

                maxHorizontalStep = Math.max(
                        maxHorizontalStep,
                        horizontalStep);
                maxVerticalStep = Math.max(
                        maxVerticalStep,
                        verticalStep);

                double lateralError =
                        Math.abs(postPos.x
                                - (course.root().getX() + 0.5D));
                maxLateralError = Math.max(
                        maxLateralError,
                        lateralError);

                if (lateralError > MAX_LATERAL_ERROR) {
                    classification = "LATERAL_DERAIL";
                    break;
                }

                boolean onSlope =
                        isOnSlope(level, loco, def.slope().id());

                if (onSlope) {
                    enteredSlope = true;
                }

                verticalTravel = def.uphill()
                        ? Math.max(
                                verticalTravel,
                                postPos.y - start.y)
                        : Math.max(
                                verticalTravel,
                                start.y - postPos.y);

                boolean passedExitCoordinate = def.uphill()
                        ? postPos.z
                                <= course.highEnd().getZ()
                                        + 0.5D
                                        - EXIT_ADVANCE
                        : postPos.z
                                >= course.lowEnd().getZ()
                                        + 0.5D
                                        + EXIT_ADVANCE;

                String owner = ownerAt(level, loco);

                boolean onExitVanilla =
                        "minecraft:rail".equals(owner);

                if (enteredSlope
                        && passedExitCoordinate
                        && onExitVanilla) {
                    reachedExitFlat = true;

                    if (verticalTravel
                            < REQUIRED_VERTICAL_TRAVEL) {
                        classification =
                                "EXIT_WITHOUT_FULL_VERTICAL_TRAVEL";
                    } else if (speed <= STALL_SPEED) {
                        classification =
                                "EXIT_WITHOUT_MOTION";
                    } else {
                        classification =
                                "SLOPE_HANDOFF_PASS";
                    }

                    report.add(
                            traceLine(
                                    label,
                                    tick,
                                    gameTimeBefore,
                                    gameTimeForTick,
                                    owner,
                                    onSlope,
                                    true,
                                    prePos,
                                    postPos,
                                    preMotion,
                                    postMotion,
                                    verticalTravel,
                                    lateralError));
                    break;
                }

                if (enteredSlope && speed < STALL_SPEED) {
                    stallTicks++;
                } else {
                    stallTicks = 0;
                }

                if (stallTicks >= STALL_TICKS) {
                    classification = "POWERED_STALL";
                    report.add(
                            traceLine(
                                    label,
                                    tick,
                                    gameTimeBefore,
                                    gameTimeForTick,
                                    owner,
                                    onSlope,
                                    false,
                                    prePos,
                                    postPos,
                                    preMotion,
                                    postMotion,
                                    verticalTravel,
                                    lateralError));
                    break;
                }

                if (postPos.y
                        < Math.min(
                                course.lowEnd().getY(),
                                course.highEnd().getY())
                                - 1.25D) {
                    classification = "OFF_RAIL_VERTICAL_DROP";
                    report.add(
                            traceLine(
                                    label,
                                    tick,
                                    gameTimeBefore,
                                    gameTimeForTick,
                                    owner,
                                    onSlope,
                                    false,
                                    prePos,
                                    postPos,
                                    preMotion,
                                    postMotion,
                                    verticalTravel,
                                    lateralError));
                    break;
                }

                report.add(
                        traceLine(
                                label,
                                tick,
                                gameTimeBefore,
                                gameTimeForTick,
                                owner,
                                onSlope,
                                false,
                                prePos,
                                postPos,
                                preMotion,
                                postMotion,
                                verticalTravel,
                                lateralError));

                previous = postPos;
            }

            boolean pass =
                    "SLOPE_HANDOFF_PASS".equals(classification);

            if (!Double.isFinite(minSpeed)) {
                minSpeed = 0.0D;
            }

            CaseResult result = new CaseResult(
                    label,
                    pass,
                    classification,
                    ticks,
                    enteredSlope,
                    reachedExitFlat,
                    minSpeed,
                    maxHorizontalStep,
                    maxVerticalStep,
                    verticalTravel,
                    maxLateralError,
                    loco == null ? Vec3.ZERO : loco.position(),
                    loco == null
                            ? Vec3.ZERO
                            : loco.getDeltaMovement());

            report.add(String.format(
                    Locale.ROOT,
                    "CASE_END label=%s PASS=%s classification=%s ticks=%d enteredSlope=%s reachedExitFlat=%s minHorizontalSpeed=%.9f maxOneTickHorizontalDisplacement=%.9f maxOneTickVerticalDelta=%.9f verticalTravel=%.9f maxLateralError=%.9f",
                    result.label(),
                    Boolean.toString(result.pass()),
                    result.classification(),
                    result.ticks(),
                    Boolean.toString(result.enteredSlope()),
                    Boolean.toString(result.reachedExitFlat()),
                    result.minHorizontalSpeed(),
                    result.maxHorizontalStep(),
                    result.maxVerticalStep(),
                    result.verticalTravel(),
                    result.maxLateralError()));

            return result;

        } catch (Throwable t) {
            report.add(
                    "CASE_EXCEPTION label=" + label
                            + " type=" + t.getClass().getName()
                            + " message="
                            + sanitize(String.valueOf(t.getMessage())));

            return new CaseResult(
                    label,
                    false,
                    "HARNESS_EXCEPTION_"
                            + t.getClass().getSimpleName(),
                    0,
                    false,
                    false,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    loco == null ? Vec3.ZERO : loco.position(),
                    loco == null
                            ? Vec3.ZERO
                            : loco.getDeltaMovement());
        } finally {
            if (loco != null) {
                try {
                    loco.setDriverControls(
                            false,
                            false,
                            false);
                } catch (Throwable ignored) {
                }
            }

            try {
                fake.stopRiding();
            } catch (Throwable ignored) {
            }

            if (loco != null && !loco.isRemoved()) {
                loco.discard();
            }

            if (course != null) {
                cleanupCourse(level, course);
            }
        }
    }

    private static Course buildCourse(
            ServerLevel level,
            FakePlayer fake,
            BlockPos supportAnchor,
            SlopeDef def) {

        LegacyBatchTrackSpec spec = def.spec();

        if (spec.partCount() != def.blocks()) {
            throw new IllegalStateException(
                    "SLOPE_PART_COUNT_MISMATCH_"
                            + def.id()
                            + "_actual_"
                            + spec.partCount()
                            + "_expected_"
                            + def.blocks());
        }

        BlockPos root = supportAnchor.above();
        BlockPos lowEnd = root;
        BlockPos highEnd =
                root.offset(
                        0,
                        1,
                        -(def.blocks() - 1));

        Set<BlockPos> owned = new HashSet<>();
        Set<ChunkPos> forced = new HashSet<>();

        for (int part = 0;
             part < spec.partCount();
             part++) {
            BlockPos support =
                    spec.supportForPart(
                            root,
                            Direction.NORTH,
                            part);
            putOwnedBlock(
                    level,
                    support,
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    owned);
        }

        for (int i = 1; i <= RUNOUT; i++) {
            BlockPos lowRail = lowEnd.south(i);
            putOwnedBlock(
                    level,
                    lowRail.below(),
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    owned);

            BlockPos highRail = highEnd.north(i);
            putOwnedBlock(
                    level,
                    highRail.below(),
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    owned);
        }

        Item item =
                ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(
                                "traincraft",
                                def.id()));

        if (item == null || item == Items.AIR) {
            throw new IllegalStateException(
                    "MISSING_SLOPE_ITEM_" + def.id());
        }

        fake.setPos(
                supportAnchor.getX() + 0.5D,
                supportAnchor.getY() + 2.0D,
                supportAnchor.getZ() + 4.5D);
        fake.setXRot(0.0F);
        fake.setYRot(180.0F);

        ItemStack stack = new ItemStack(item);
        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                stack);

        BlockHitResult hit =
                new BlockHitResult(
                        new Vec3(
                                supportAnchor.getX() + 0.5D,
                                supportAnchor.getY() + 1.0D,
                                supportAnchor.getZ() + 0.5D),
                        Direction.UP,
                        supportAnchor,
                        false);

        InteractionResult placement =
                item.useOn(
                        new UseOnContext(
                                fake,
                                InteractionHand.MAIN_HAND,
                                hit));

        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);

        if (!placement.consumesAction()) {
            throw new IllegalStateException(
                    "REAL_SLOPE_PLACEMENT_REJECTED_"
                            + def.id()
                            + "_result_"
                            + placement);
        }

        for (int part = 0;
             part < spec.partCount();
             part++) {
            BlockPos pos =
                    spec.offsetForPart(
                            root,
                            Direction.NORTH,
                            part);
            BlockState state =
                    level.getBlockState(pos);

            if (!(state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch)
                    || !def.id().equals(
                            batch.getSpec().id())) {
                throw new IllegalStateException(
                        "SLOPE_ASSEMBLY_INVALID_"
                                + def.id()
                                + "_p"
                                + part
                                + "_at_"
                                + pos
                                + "_state_"
                                + state);
            }

            owned.add(pos);
        }

        BlockState flatRail =
                Blocks.RAIL.defaultBlockState()
                        .setValue(
                                RailBlock.SHAPE,
                                RailShape.NORTH_SOUTH);

        for (int i = 1; i <= RUNOUT; i++) {
            BlockPos lowRail =
                    lowEnd.south(i);
            putOwnedBlock(
                    level,
                    lowRail,
                    flatRail,
                    owned);

            BlockPos highRail =
                    highEnd.north(i);
            putOwnedBlock(
                    level,
                    highRail,
                    flatRail,
                    owned);
        }

        for (BlockPos pos : owned) {
            ChunkPos chunk =
                    new ChunkPos(
                            pos.getX() >> 4,
                            pos.getZ() >> 4);
            forced.add(chunk);
        }

        for (ChunkPos chunk : forced) {
            level.setChunkForced(
                    chunk.x,
                    chunk.z,
                    true);
        }

        return new Course(
                root,
                lowEnd,
                highEnd,
                owned,
                forced);
    }

    private static void putOwnedBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Set<BlockPos> owned) {

        if (!level.getBlockState(pos).isAir()) {
            throw new IllegalStateException(
                    "TEST_CELL_NOT_AIR_"
                            + pos
                            + "_state_"
                            + level.getBlockState(pos));
        }

        level.setBlock(pos, state, 2);
        owned.add(pos);
    }

    private static BlockPos findCleanSupportAnchor(
            ServerLevel level,
            BlockPos desired) {

        final int maxAttempts = 64;
        final int stride = 72;

        for (int attempt = 0;
             attempt < maxAttempts;
             attempt++) {

            BlockPos candidate =
                    desired.offset(
                            0,
                            0,
                            attempt * stride);

            boolean clean = true;

            for (int dx = -4;
                 dx <= 4 && clean;
                 dx++) {
                for (int dz = -32;
                     dz <= 12 && clean;
                     dz++) {
                    for (int dy = -1;
                         dy <= 4;
                         dy++) {
                        if (!level.getBlockState(
                                candidate.offset(
                                        dx,
                                        dy,
                                        dz))
                                .isAir()) {
                            clean = false;
                            break;
                        }
                    }
                }
            }

            if (clean) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "NO_CLEAN_SLOPE_TEST_GRID_NEAR_"
                        + desired);
    }

    private static void cleanupCourse(
            ServerLevel level,
            Course course) {

        List<BlockPos> positions =
                new ArrayList<>(course.owned());

        positions.sort(
                (a, b) -> Integer.compare(
                        b.getY(),
                        a.getY()));

        for (BlockPos pos : positions) {
            level.setBlock(
                    pos,
                    Blocks.AIR.defaultBlockState(),
                    2);
        }

        for (ChunkPos chunk : course.forced()) {
            level.setChunkForced(
                    chunk.x,
                    chunk.z,
                    false);
        }
    }

    private static boolean isOnSlope(
            ServerLevel level,
            SmallSteamLocomotive loco,
            String slopeId) {

        BlockPos base = loco.blockPosition();

        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = base.offset(0, dy, 0);
            BlockState state =
                    level.getBlockState(pos);

            if (state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch
                    && slopeId.equals(
                            batch.getSpec().id())) {
                return true;
            }
        }

        return false;
    }

    private static String ownerAt(
            ServerLevel level,
            SmallSteamLocomotive loco) {

        BlockPos base = loco.blockPosition();

        BlockPos[] candidates = new BlockPos[]{
                base,
                base.below(),
                base.above()
        };

        for (BlockPos pos : candidates) {
            BlockState state =
                    level.getBlockState(pos);

            if (state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch) {
                return batch.getSpec().id();
            }

            if (state.is(Blocks.RAIL)) {
                return "minecraft:rail";
            }
        }

        return "NONE";
    }

    private static void advanceDeterministicGameTime(
            ServerLevel level) {

        long next =
                level.getGameTime() + 1L;

        Object data =
                level.getLevelData();

        try {
            Method method =
                    data.getClass()
                            .getMethod(
                                    "setGameTime",
                                    long.class);
            method.invoke(data, next);
        } catch (ReflectiveOperationException publicFailure) {
            try {
                Method method =
                        data.getClass()
                                .getDeclaredMethod(
                                        "setGameTime",
                                        long.class);
                method.setAccessible(true);
                method.invoke(data, next);
            } catch (ReflectiveOperationException declaredFailure) {
                throw new IllegalStateException(
                        "SL1_CANNOT_ADVANCE_GAME_TIME_type_"
                                + data.getClass().getName(),
                        declaredFailure);
            }
        }

        if (level.getGameTime() != next) {
            throw new IllegalStateException(
                    "SL1_GAME_TIME_DID_NOT_ADVANCE_actual_"
                            + level.getGameTime()
                            + "_expected_"
                            + next);
        }
    }

    private static void setIntField(
            Object target,
            String fieldName,
            int value) {

        Class<?> type =
                target.getClass();

        while (type != null) {
            try {
                Field field =
                        type.getDeclaredField(
                                fieldName);
                field.setAccessible(true);
                field.setInt(
                        target,
                        value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "CANNOT_SET_FIELD_"
                                + fieldName,
                        e);
            }
        }

        throw new IllegalStateException(
                "FIELD_NOT_FOUND_"
                        + fieldName);
    }

    private static float bodyYawForTravel(
            Vec3 direction) {

        return (float)
                Math.toDegrees(
                        Math.atan2(
                                direction.x,
                                -direction.z));
    }

    private static double horizontalSpeed(
            Vec3 motion) {

        return Math.hypot(
                motion.x,
                motion.z);
    }

    private static String traceLine(
            String label,
            int tick,
            long gameTimeBefore,
            long gameTimeForTick,
            String owner,
            boolean onSlope,
            boolean exit,
            Vec3 prePos,
            Vec3 postPos,
            Vec3 preMotion,
            Vec3 postMotion,
            double verticalTravel,
            double lateralError) {

        return String.format(
                Locale.ROOT,
                "TRACE label=%s tick=%d gameTimeBefore=%d gameTimeForTick=%d owner=%s onSlope=%s exitFlat=%s prePos=%s postPos=%s preMotion=%s postMotion=%s speed=%.9f verticalTravel=%.9f lateralError=%.9f yaw=%.3f",
                label,
                tick,
                gameTimeBefore,
                gameTimeForTick,
                owner,
                Boolean.toString(onSlope),
                Boolean.toString(exit),
                vec(prePos),
                vec(postPos),
                vec(preMotion),
                vec(postMotion),
                horizontalSpeed(postMotion),
                verticalTravel,
                lateralError,
                0.0D);
    }

    private static CaseResult setupFailure(
            String label,
            String reason) {

        return new CaseResult(
                label,
                false,
                reason,
                0,
                false,
                false,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                Vec3.ZERO,
                Vec3.ZERO);
    }

    private static String vec(
            Vec3 v) {

        return String.format(
                Locale.ROOT,
                "(%.6f,%.6f,%.6f)",
                v.x,
                v.y,
                v.z);
    }

    private static String sanitize(
            String value) {

        if (value == null) {
            return "null";
        }

        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace(' ', '_');
    }

    private static void writeHarnessFailure(
            Throwable t) throws Exception {

        List<String> lines = List.of(
                "TAFARICRAFT - TRAINS STEP 9.3g-t7-sl1",
                "CONNECTED STRAIGHT-SLOPE HEADLESS GATE",
                "HARNESS_EXCEPTION="
                        + t.getClass().getName(),
                "HARNESS_MESSAGE="
                        + String.valueOf(t.getMessage()),
                "RESULT=T7_SL1_CONNECTED_STRAIGHT_SLOPES_HARNESS_FAILURE"
        );

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-sl1-connected-straight-slopes-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(
                output.getParent());
        Files.write(
                output,
                lines,
                StandardCharsets.UTF_8);
    }
}
