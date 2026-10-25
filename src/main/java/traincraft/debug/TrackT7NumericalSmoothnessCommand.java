package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Step 9.3g-t7-ns1 — numerical smoothness flight recorder.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * Reuses the exact frozen c2-r3 course builder through reflection rather than
 * duplicating placement/topology logic. c2-r3 still places every Traincraft
 * section through the REAL registered item/useOn path.
 *
 * Then this diagnostic runs a powered Small Steam locomotive:
 *   FORWARD  = starter south -> full 12-section course -> runout end
 *   RETURN   = runout end -> same full course in reverse order -> starter south
 *
 * RETURN uses forward throttle with the locomotive physically facing inward.
 * This stage validates bidirectional connected-course routing only. The later
 * full-consist stage is where actual reverse/S input and coupling are gated.
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class TrackT7NumericalSmoothnessCommand {
    private static final String ENV = "TC_T7_SMOOTHNESS_HEADLESS";

    private static final double START_SPEED = 0.12D;
    private static final double STALL_SPEED = 0.008D;
    private static final int STALL_TICKS = 14;
    private static final int OFF_RAIL_TICKS = 8;
    private static final int MAX_TICKS = 1800;
    private static final double END_PROJECTION_TOLERANCE = -0.35D;
    private static final double RAIL_SURFACE_Y = 0.0625D;
    private static final double SMOOTHNESS_OUTLIER_THRESHOLD = 0.75D;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final Pattern BLOCK_POS = Pattern.compile(
            "BlockPos\\{x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)\\}");

    private static final List<String> EXPECTED_FORWARD = List.of(
            "track_switch_medium_left",
            "track_curve_big",
            "track_switch_45_medium_right",
            "track_diagonal_straight_medium",
            "track_diamond_crossing",
            "track_diagonal_straight_medium",
            "track_diagonal_crossing",
            "track_diagonal_straight_medium",
            "track_curve_45_medium_left",
            "track_switch_parallel_right",
            "track_parallel_curve_medium_left",
            "track_slope_long"
    );

    private record CourseInfo(
            BlockPos courseStart,
            Direction starterForward,
            BlockPos courseEnd,
            Direction runoutOutward,
            Path c2Report) {
    }

    private record RunResult(
            String label,
            boolean pass,
            String classification,
            int ticks,
            boolean enteredTraincraft,
            boolean reachedExit,
            double minSpeed,
            double maxHorizontalStep,
            double maxVerticalStep,
            double verticalTravel,
            int smoothnessOutlierCount,
            List<String> ownerSequence,
            Vec3 finalPosition,
            Vec3 finalMotion) {
    }

    private TrackT7NumericalSmoothnessCommand() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String enabled = System.getenv(ENV);
        if (!"1".equals(enabled)
                && !"true".equalsIgnoreCase(enabled)) {
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

        BlockPos starterSouth = chooseCleanStarter(level);
        positionBuilderPlayer(fake, starterSouth);

        Path c2Dir = Path.of(
                "logs",
                "traincraft-track-debug")
                .toAbsolutePath();

        Path beforeReport = newest(
                c2Dir,
                "t7-full-test-track-");

        int buildResult = invokeC2Builder(fake);

        if (buildResult <= 0) {
            throw new IllegalStateException(
                    "C2_R3_BUILD_RETURNED_" + buildResult);
        }

        Path c2Report = newest(
                c2Dir,
                "t7-full-test-track-");

        if (c2Report == null
                || c2Report.equals(beforeReport)) {
            throw new IllegalStateException(
                    "C2_R3_DID_NOT_PRODUCE_NEW_REPORT");
        }

        List<String> c2Lines =
                Files.readAllLines(
                        c2Report,
                        StandardCharsets.UTF_8);

        if (c2Lines.stream().noneMatch(
                line -> line.equals(
                        "RESULT=T7_C2_R3_FULL_TEST_TRACK_READY"))) {
            throw new IllegalStateException(
                    "C2_R3_REPORT_NOT_READY_"
                            + c2Report);
        }

        CourseInfo course =
                parseCourseInfo(
                        c2Lines,
                        c2Report);

        Set<ChunkPos> forced =
                forceCourseChunks(
                        level,
                        course);

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-ns1");
        report.add("NUMERICAL SMOOTHNESS FLIGHT RECORDER");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("COURSE_BUILDER=EXACT_C2_R3_REFLECTION");
        report.add("COURSE_PLACEMENT=REAL_REGISTERED_ITEMS_USE_ON");
        report.add("ENTITY_PATH=POWERED_SMALL_STEAM_DIRECT_SERVERLEVEL_TICKNONPASSENGER");
        report.add("TEST_CLOCK=EXPLICIT_GAME_TIME_PLUS_ONE_PER_PROBE_TICK");
        report.add("FORWARD_ROUTE_SECTION_COUNT=" + EXPECTED_FORWARD.size());
        report.add(String.format(
                Locale.ROOT,
                "SMOOTHNESS_OUTLIER_THRESHOLD=%.3f",
                SMOOTHNESS_OUTLIER_THRESHOLD));
        report.add("SMOOTHNESS_SCOPE=ONE_TICK_HORIZONTAL_DISPLACEMENT_WHILE_PRE_OR_POST_OWNER_IS_TRAINCRAFT");
        report.add("SESSION_CAPTURE=LINEAR_AND_CURVE_PERSISTENT_KEYS_PRE_AND_POST_TICK");
        report.add("C2_REPORT=" + c2Report);
        report.add("COURSE_START=" + course.courseStart());
        report.add("STARTER_FORWARD=" + course.starterForward());
        report.add("COURSE_END=" + course.courseEnd());
        report.add("RUNOUT_OUTWARD=" + course.runoutOutward());
        report.add("");

        RunResult forward = runTraversal(
                level,
                fake,
                "FORWARD",
                course.courseStart(),
                course.starterForward(),
                course.courseEnd(),
                course.runoutOutward(),
                EXPECTED_FORWARD,
                report);

        report.add("");

        List<String> expectedReturn =
                new ArrayList<>(EXPECTED_FORWARD);
        Collections.reverse(expectedReturn);

        RunResult back = runTraversal(
                level,
                fake,
                "RETURN",
                course.courseEnd(),
                course.runoutOutward().getOpposite(),
                course.courseStart(),
                course.starterForward().getOpposite(),
                expectedReturn,
                report);

        report.add("");
        report.add("SUMMARY");
        int routePassCount =
                (forward.pass() ? 1 : 0)
                + (back.pass() ? 1 : 0);
        int routeFailCount = 2 - routePassCount;
        int smoothnessOutlierCount =
                forward.smoothnessOutlierCount()
                + back.smoothnessOutlierCount();

        report.add("ROUTE_PASS_COUNT=" + routePassCount);
        report.add("ROUTE_FAIL_COUNT=" + routeFailCount);
        report.add("SMOOTHNESS_OUTLIER_COUNT=" + smoothnessOutlierCount);
        report.add("SMOOTHNESS_PASS="
                + Boolean.toString(
                        routeFailCount == 0
                                && smoothnessOutlierCount == 0));

        report.add(summaryLine(forward));
        report.add(summaryLine(back));
        report.add("");
        report.add("RESULT="
                + (routeFailCount != 0
                ? "T7_NS1_ROUTE_REGRESSION"
                : (smoothnessOutlierCount == 0
                ? "T7_NS1_NUMERICAL_SMOOTHNESS_PASS"
                : "T7_NS1_NUMERICAL_SMOOTHNESS_OUTLIERS_FOUND")));

        Path output = Path.of(
                "logs",
                "traincraft-track-debug",
                "t7-ns1-numerical-smoothness-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(
                output,
                report,
                StandardCharsets.UTF_8);

        System.out.println(
                "[TC-T7-C3] complete forward="
                        + forward.pass()
                        + " return="
                        + back.pass()
                        + " report="
                        + output);

        fake.stopRiding();

        for (ChunkPos chunk : forced) {
            level.setChunkForced(
                    chunk.x,
                    chunk.z,
                    false);
        }

        server.halt(false);
    }

    private static int invokeC2Builder(
            FakePlayer fake) throws Exception {

        Class<?> clazz =
                Class.forName(
                        "traincraft.debug.TrackT7FullCourseCommand");

        Method method =
                clazz.getDeclaredMethod(
                        "buildCourse",
                        ServerPlayer.class);

        method.setAccessible(true);

        Object value =
                method.invoke(
                        null,
                        fake);

        if (!(value instanceof Integer result)) {
            throw new IllegalStateException(
                    "C2_R3_BUILD_RETURN_NOT_INTEGER_"
                            + value);
        }

        return result;
    }

    private static BlockPos chooseCleanStarter(
            ServerLevel level) {

        BlockPos spawn =
                level.getSharedSpawnPos();

        int railY = Math.max(
                level.getMinBuildHeight() + 36,
                Math.min(
                        level.getMaxBuildHeight() - 48,
                        spawn.getY() + 70));

        BlockPos desired =
                new BlockPos(
                        spawn.getX() + 5200,
                        railY,
                        spawn.getZ() + 5200);

        for (int attempt = 0;
             attempt < 48;
             attempt++) {

            BlockPos candidate =
                    desired.offset(
                            0,
                            0,
                            attempt * 180);

            if (courseEnvelopeIsAir(
                    level,
                    candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "NO_CLEAN_C3_COURSE_GRID_NEAR_"
                        + desired);
    }

    private static boolean courseEnvelopeIsAir(
            ServerLevel level,
            BlockPos starterSouth) {

        // c2-r3's proven route is roughly 82 blocks west from the starter with
        // a modest Z excursion. Give it a deliberately generous empty prism.
        for (int dx = -120;
             dx <= 30;
             dx++) {
            for (int dz = -50;
                 dz <= 35;
                 dz++) {
                for (int dy = -2;
                     dy <= 5;
                     dy++) {
                    if (!level.getBlockState(
                            starterSouth.offset(
                                    dx,
                                    dy,
                                    dz))
                            .isAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static void positionBuilderPlayer(
            FakePlayer fake,
            BlockPos starterSouth) {

        // c2-r3 computes:
        // starterSouth = player.block + (+48, +20 rail Y, +48)
        fake.setPos(
                starterSouth.getX()
                        - 48
                        + 0.5D,
                starterSouth.getY()
                        - 20,
                starterSouth.getZ()
                        - 48
                        + 0.5D);

        fake.setYRot(180.0F);
        fake.setXRot(20.0F);
        fake.setShiftKeyDown(false);
    }

    private static CourseInfo parseCourseInfo(
            List<String> lines,
            Path report) {

        BlockPos start = null;
        BlockPos end = null;
        Direction starterForward = null;
        Direction runoutOutward = null;

        for (String line : lines) {
            if (line.startsWith("COURSE_START=")) {
                start = parseBlockPos(line);
            } else if (line.startsWith("COURSE_END=")) {
                end = parseBlockPos(line);
            } else if (line.startsWith("STARTER northEnd=")) {
                int at = line.indexOf("outward=");
                if (at >= 0) {
                    starterForward = Direction.byName(
                            line.substring(
                                    at + "outward=".length())
                                    .trim()
                                    .toLowerCase(Locale.ROOT));
                }
            } else if (line.startsWith("RUNOUT start=")) {
                int at = line.indexOf("outward=");
                if (at >= 0) {
                    runoutOutward = Direction.byName(
                            line.substring(
                                    at + "outward=".length())
                                    .trim()
                                    .toLowerCase(Locale.ROOT));
                }
            }
        }

        if (start == null
                || end == null
                || starterForward == null
                || runoutOutward == null) {
            throw new IllegalStateException(
                    "INCOMPLETE_C2_COURSE_INFO"
                            + "_start=" + start
                            + "_end=" + end
                            + "_starterForward="
                            + starterForward
                            + "_runoutOutward="
                            + runoutOutward);
        }

        return new CourseInfo(
                start,
                starterForward,
                end,
                runoutOutward,
                report);
    }

    private static BlockPos parseBlockPos(
            String line) {

        Matcher matcher =
                BLOCK_POS.matcher(line);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "NO_BLOCKPOS_IN_LINE_"
                            + line);
        }

        return new BlockPos(
                Integer.parseInt(
                        matcher.group(1)),
                Integer.parseInt(
                        matcher.group(2)),
                Integer.parseInt(
                        matcher.group(3)));
    }

    private static Set<ChunkPos> forceCourseChunks(
            ServerLevel level,
            CourseInfo course) {

        int minX =
                Math.min(
                        course.courseStart().getX(),
                        course.courseEnd().getX())
                        - 64;

        int maxX =
                Math.max(
                        course.courseStart().getX(),
                        course.courseEnd().getX())
                        + 64;

        int minZ =
                Math.min(
                        course.courseStart().getZ(),
                        course.courseEnd().getZ())
                        - 64;

        int maxZ =
                Math.max(
                        course.courseStart().getZ(),
                        course.courseEnd().getZ())
                        + 64;

        Set<ChunkPos> forced =
                new HashSet<>();

        for (int x = minX >> 4;
             x <= maxX >> 4;
             x++) {
            for (int z = minZ >> 4;
                 z <= maxZ >> 4;
                 z++) {
                level.setChunkForced(
                        x,
                        z,
                        true);

                forced.add(
                        new ChunkPos(
                                x,
                                z));
            }
        }

        return forced;
    }

    private static RunResult runTraversal(
            ServerLevel level,
            FakePlayer fake,
            String label,
            BlockPos startRail,
            Direction initialTravel,
            BlockPos exitRail,
            Direction exitOutward,
            List<String> expectedSequence,
            List<String> report) {

        SmallSteamLocomotive loco = null;

        try {
            fake.stopRiding();

            Vec3 start =
                    railCenter(startRail);

            Vec3 travel =
                    new Vec3(
                            initialTravel.getStepX(),
                            0.0D,
                            initialTravel.getStepZ());

            EntityType<?> type =
                    ForgeRegistries.ENTITY_TYPES.getValue(
                            new ResourceLocation(
                                    "traincraft",
                                    "locomotive_steam_small"));

            if (type == null) {
                return setupFailure(
                        label,
                        "MISSING_LOCOMOTIVE_ENTITY_TYPE");
            }

            Entity raw =
                    type.create(level);

            if (!(raw
                    instanceof SmallSteamLocomotive steam)) {
                if (raw != null) {
                    raw.discard();
                }

                return setupFailure(
                        label,
                        "LOCOMOTIVE_NOT_SMALL_STEAM");
            }

            loco = steam;
            loco.setPos(
                    start.x,
                    start.y,
                    start.z);

            loco.setYRot(
                    bodyYawForTravel(travel));

            loco.setDeltaMovement(
                    travel.x * START_SPEED,
                    0.0D,
                    travel.z * START_SPEED);

            setIntField(
                    loco,
                    "steamAmount",
                    1000);

            if (!level.addFreshEntity(loco)) {
                loco.discard();
                loco = null;

                return setupFailure(
                        label,
                        "ADD_LOCOMOTIVE_FAILED");
            }

            fake.setPos(
                    start.x,
                    start.y + 1.0D,
                    start.z);

            if (!fake.startRiding(
                    loco,
                    true)) {
                return setupFailure(
                        label,
                        "FAKE_DRIVER_COULD_NOT_RIDE");
            }

            loco.setDriverControls(
                    true,
                    false,
                    false);

            report.add(
                    "RUN_BEGIN label="
                            + label
                            + " startRail="
                            + startRail
                            + " initialTravel="
                            + initialTravel
                            + " exitRail="
                            + exitRail
                            + " exitOutward="
                            + exitOutward
                            + " expectedSequence="
                            + String.join(
                                    ">",
                                    expectedSequence));

            List<String> ownerSequence =
                    new ArrayList<>();

            String lastTraincraftOwner = null;
            boolean enteredTraincraft = false;
            boolean reachedExit = false;

            double minSpeed =
                    Double.POSITIVE_INFINITY;
            double maxHorizontalStep = 0.0D;
            double maxVerticalStep = 0.0D;
            int smoothnessOutliers = 0;

            double minY =
                    start.y;
            double maxY =
                    start.y;

            int stallTicks = 0;
            int offRailTicks = 0;
            int ticks = 0;
            String classification = "TIMEOUT";

            for (int tick = 1;
                 tick <= MAX_TICKS;
                 tick++) {

                ticks = tick;

                if (loco.isRemoved()) {
                    classification =
                            "LOCOMOTIVE_REMOVED";
                    break;
                }

                Vec3 prePos =
                        loco.position();

                Vec3 preMotion =
                        loco.getDeltaMovement();

                String preOwner =
                        ownerAt(
                                level,
                                loco);
                String preSession =
                        sessionSnapshot(loco);

                loco.setDriverControls(
                        true,
                        false,
                        false);

                long gameTimeBefore =
                        level.getGameTime();

                advanceDeterministicGameTime(level);

                long gameTimeForTick =
                        level.getGameTime();

                level.tickNonPassenger(loco);

                if (loco.isRemoved()) {
                    classification =
                            "LOCOMOTIVE_REMOVED";

                    report.add(
                            "TRACE label="
                                    + label
                                    + " tick="
                                    + tick
                                    + " gameTimeBefore="
                                    + gameTimeBefore
                                    + " gameTimeForTick="
                                    + gameTimeForTick
                                    + " owner=REMOVED"
                                    + " prePos="
                                    + vec(prePos)
                                    + " preMotion="
                                    + vec(preMotion));
                    break;
                }

                Vec3 postPos =
                        loco.position();

                Vec3 postMotion =
                        loco.getDeltaMovement();

                double speed =
                        horizontalSpeed(postMotion);

                minSpeed =
                        Math.min(
                                minSpeed,
                                speed);

                double horizontalStep =
                        Math.hypot(
                                postPos.x - prePos.x,
                                postPos.z - prePos.z);
                double verticalStep =
                        Math.abs(
                                postPos.y - prePos.y);

                maxHorizontalStep =
                        Math.max(
                                maxHorizontalStep,
                                horizontalStep);

                maxVerticalStep =
                        Math.max(
                                maxVerticalStep,
                                verticalStep);

                minY =
                        Math.min(
                                minY,
                                postPos.y);

                maxY =
                        Math.max(
                                maxY,
                                postPos.y);

                String owner =
                        ownerAt(
                                level,
                                loco);

                boolean isTraincraft =
                        owner.startsWith(
                                "track_");
                boolean preTraincraft =
                        preOwner.startsWith(
                                "track_");
                String postSession =
                        sessionSnapshot(loco);

                if (preTraincraft || isTraincraft) {
                    report.add(String.format(
                            Locale.ROOT,
                            "SMOOTH_TRACE label=%s tick=%d preOwner=%s postOwner=%s stepH=%.9f stepV=%.9f prePos=%s postPos=%s preMotion=%s postMotion=%s preSession=%s postSession=%s",
                            label,
                            tick,
                            preOwner,
                            owner,
                            horizontalStep,
                            verticalStep,
                            vec(prePos),
                            vec(postPos),
                            vec(preMotion),
                            vec(postMotion),
                            preSession,
                            postSession));

                    if (horizontalStep > SMOOTHNESS_OUTLIER_THRESHOLD) {
                        smoothnessOutliers++;
                        report.add(String.format(
                                Locale.ROOT,
                                "SMOOTHNESS_OUTLIER label=%s index=%d tick=%d preOwner=%s postOwner=%s stepH=%.9f threshold=%.9f prePos=%s postPos=%s preMotion=%s postMotion=%s preSession=%s postSession=%s",
                                label,
                                smoothnessOutliers,
                                tick,
                                preOwner,
                                owner,
                                horizontalStep,
                                SMOOTHNESS_OUTLIER_THRESHOLD,
                                vec(prePos),
                                vec(postPos),
                                vec(preMotion),
                                vec(postMotion),
                                preSession,
                                postSession));
                    }
                }

                if (isTraincraft) {
                    enteredTraincraft = true;
                    offRailTicks = 0;

                    if (!owner.equals(
                            lastTraincraftOwner)) {
                        ownerSequence.add(owner);
                        lastTraincraftOwner = owner;

                        report.add(
                                "OWNER_EVENT label="
                                        + label
                                        + " tick="
                                        + tick
                                        + " owner="
                                        + owner
                                        + " pos="
                                        + vec(postPos)
                                        + " motion="
                                        + vec(postMotion));
                    }
                } else if ("minecraft:rail".equals(owner)) {
                    offRailTicks = 0;
                } else if (enteredTraincraft) {
                    offRailTicks++;
                }

                if (offRailTicks
                        >= OFF_RAIL_TICKS) {
                    classification =
                            "OFF_RAIL_OWNER_NONE";
                    break;
                }

                if (enteredTraincraft
                        && speed < STALL_SPEED) {
                    stallTicks++;
                } else {
                    stallTicks = 0;
                }

                if (stallTicks
                        >= STALL_TICKS) {
                    classification =
                            "POWERED_STALL";
                    break;
                }

                double exitProjection =
                        (postPos.x
                                - (exitRail.getX()
                                + 0.5D))
                                * exitOutward.getStepX()
                        + (postPos.z
                                - (exitRail.getZ()
                                + 0.5D))
                                * exitOutward.getStepZ();

                if (enteredTraincraft
                        && "minecraft:rail".equals(owner)
                        && exitProjection
                                >= END_PROJECTION_TOLERANCE) {

                    reachedExit = true;

                    if (!ownerSequence.equals(
                            expectedSequence)) {
                        classification =
                                "OWNER_SEQUENCE_MISMATCH";
                    } else if (speed
                            <= STALL_SPEED) {
                        classification =
                                "EXIT_WITHOUT_MOTION";
                    } else {
                        classification =
                                "FULL_COURSE_PASS";
                    }

                    report.add(
                            "EXIT_EVENT label="
                                    + label
                                    + " tick="
                                    + tick
                                    + " exitProjection="
                                    + fmt(exitProjection)
                                    + " pos="
                                    + vec(postPos)
                                    + " motion="
                                    + vec(postMotion)
                                    + " ownerSequence="
                                    + String.join(
                                            ">",
                                            ownerSequence));
                    break;
                }

                if (tick == 1
                        || tick % 20 == 0
                        || isTraincraft
                        || offRailTicks > 0) {

                    report.add(String.format(
                            Locale.ROOT,
                            "TRACE label=%s tick=%d gameTimeBefore=%d gameTimeForTick=%d owner=%s speed=%.9f pos=%s motion=%s offRailTicks=%d",
                            label,
                            tick,
                            gameTimeBefore,
                            gameTimeForTick,
                            owner,
                            speed,
                            vec(postPos),
                            vec(postMotion),
                            offRailTicks));
                }
            }

            boolean pass =
                    "FULL_COURSE_PASS"
                            .equals(classification);

            if (!Double.isFinite(minSpeed)) {
                minSpeed = 0.0D;
            }

            double verticalTravel =
                    maxY - minY;

            RunResult result =
                    new RunResult(
                            label,
                            pass,
                            classification,
                            ticks,
                            enteredTraincraft,
                            reachedExit,
                            minSpeed,
                            maxHorizontalStep,
                            maxVerticalStep,
                            verticalTravel,
                            smoothnessOutliers,
                            List.copyOf(
                                    ownerSequence),
                            loco.position(),
                            loco.getDeltaMovement());

            report.add(summaryLine(result));

            return result;

        } catch (Throwable t) {
            report.add(
                    "RUN_EXCEPTION label="
                            + label
                            + " type="
                            + t.getClass().getName()
                            + " message="
                            + sanitize(
                                    String.valueOf(
                                            t.getMessage())));

            return new RunResult(
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
                    0,
                    List.of(),
                    loco == null
                            ? Vec3.ZERO
                            : loco.position(),
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

            if (loco != null
                    && !loco.isRemoved()) {
                loco.discard();
            }
        }
    }

    private static Vec3 railCenter(
            BlockPos rail) {

        return new Vec3(
                rail.getX() + 0.5D,
                rail.getY() + RAIL_SURFACE_Y,
                rail.getZ() + 0.5D);
    }

    private static String ownerAt(
            ServerLevel level,
            SmallSteamLocomotive loco) {

        BlockPos base =
                loco.blockPosition();

        BlockPos[] candidates =
                new BlockPos[]{
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

    private static String sessionSnapshot(
            SmallSteamLocomotive loco) {

        var data = loco.getPersistentData();

        return "{"
                + "lc=" + tagString(data, "tc_cont_diag_context")
                + ",lr=" + tagInt(data, "tc_cont_diag_route")
                + ",lp=" + tagDouble(data, "tc_cont_diag_progress")
                + ",lt=" + tagLong(data, "tc_cont_diag_tick")
                + ",ls=" + tagDouble(data, "tc_cont_diag_sign")
                + ",lbs=" + tagDouble(data, "tc_cont_diag_body_sign")
                + ",hc=" + tagString(data, "tc_cont_diag_handoff_context")
                + ",hr=" + tagInt(data, "tc_cont_diag_handoff_route")
                + ",hp=" + tagDouble(data, "tc_cont_diag_handoff_progress")
                + ",ht=" + tagLong(data, "tc_cont_diag_handoff_tick")
                + ",asc=" + tagString(data, "tc_cont_diag_atomic_settle_context")
                + ",ast=" + tagLong(data, "tc_cont_diag_atomic_settle_tick")
                + ",cc=" + tagString(data, "tc_cont_curve_context")
                + ",cp=" + tagDouble(data, "tc_cont_curve_progress")
                + ",ct=" + tagLong(data, "tc_cont_curve_tick")
                + "}";
    }

    private static String tagString(
            net.minecraft.nbt.CompoundTag data,
            String key) {
        return data.contains(key)
                ? sanitize(data.getString(key))
                : "-";
    }

    private static String tagInt(
            net.minecraft.nbt.CompoundTag data,
            String key) {
        return data.contains(key)
                ? Integer.toString(data.getInt(key))
                : "-";
    }

    private static String tagLong(
            net.minecraft.nbt.CompoundTag data,
            String key) {
        return data.contains(key)
                ? Long.toString(data.getLong(key))
                : "-";
    }

    private static String tagDouble(
            net.minecraft.nbt.CompoundTag data,
            String key) {
        return data.contains(key)
                ? String.format(
                        Locale.ROOT,
                        "%.9f",
                        data.getDouble(key))
                : "-";
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

            method.invoke(
                    data,
                    next);
        } catch (ReflectiveOperationException publicFailure) {
            try {
                Method method =
                        data.getClass()
                                .getDeclaredMethod(
                                        "setGameTime",
                                        long.class);

                method.setAccessible(true);

                method.invoke(
                        data,
                        next);
            } catch (ReflectiveOperationException declaredFailure) {
                throw new IllegalStateException(
                        "NS1_CANNOT_ADVANCE_GAME_TIME_type_"
                                + data.getClass().getName(),
                        declaredFailure);
            }
        }

        if (level.getGameTime()
                != next) {
            throw new IllegalStateException(
                    "NS1_GAME_TIME_DID_NOT_ADVANCE_actual_"
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

    private static String summaryLine(
            RunResult result) {

        return String.format(
                Locale.ROOT,
                "RUN_SUMMARY label=%s ROUTE_PASS=%s classification=%s ticks=%d enteredTraincraft=%s reachedExit=%s minHorizontalSpeed=%.9f maxOneTickHorizontalDisplacement=%.9f maxOneTickVerticalDelta=%.9f verticalTravel=%.9f smoothnessOutliers=%d ownerSequence=%s finalPos=(%.6f,%.6f,%.6f) finalMotion=(%.6f,%.6f,%.6f)",
                result.label(),
                Boolean.toString(
                        result.pass()),
                result.classification(),
                result.ticks(),
                Boolean.toString(
                        result.enteredTraincraft()),
                Boolean.toString(
                        result.reachedExit()),
                result.minSpeed(),
                result.maxHorizontalStep(),
                result.maxVerticalStep(),
                result.verticalTravel(),
                result.smoothnessOutlierCount(),
                String.join(
                        ">",
                        result.ownerSequence()),
                result.finalPosition().x,
                result.finalPosition().y,
                result.finalPosition().z,
                result.finalMotion().x,
                result.finalMotion().y,
                result.finalMotion().z);
    }

    private static RunResult setupFailure(
            String label,
            String reason) {

        return new RunResult(
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
                0,
                List.of(),
                Vec3.ZERO,
                Vec3.ZERO);
    }

    private static Path newest(
            Path dir,
            String prefix) throws Exception {

        if (!Files.isDirectory(dir)) {
            return null;
        }

        try (Stream<Path> stream =
                     Files.list(dir)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .startsWith(prefix))
                    .max(Comparator.comparingLong(
                            path -> {
                                try {
                                    return Files.getLastModifiedTime(
                                            path)
                                            .toMillis();
                                } catch (Exception ex) {
                                    return Long.MIN_VALUE;
                                }
                            }))
                    .orElse(null);
        }
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

    private static String fmt(
            double value) {

        return String.format(
                Locale.ROOT,
                "%.9f",
                value);
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

        List<String> lines =
                List.of(
                        "TAFARICRAFT - TRAINS STEP 9.3g-t7-ns1",
                        "NUMERICAL SMOOTHNESS FLIGHT RECORDER",
                        "HARNESS_EXCEPTION="
                                + t.getClass().getName(),
                        "HARNESS_MESSAGE="
                                + String.valueOf(
                                        t.getMessage()),
                        "RESULT=T7_NS1_NUMERICAL_SMOOTHNESS_HARNESS_FAILURE");

        Path output = Path.of(
                "logs",
                "traincraft-track-debug",
                "t7-ns1-numerical-smoothness-"
                        + STAMP.format(
                                LocalDateTime.now())
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
