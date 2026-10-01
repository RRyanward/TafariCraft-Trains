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
import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.tender.SteamTender;

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
 * Step 9.3g-t7-fc1 — headless five-vehicle full-consist forward/reverse gate.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * Reuses the exact frozen c2-r3 real-placement course builder. Spawns:
 *   Small Steam -> Steam Tender -> Freight Cart -> Passenger Blue -> Caboose
 *
 * Coupling is established through the production CouplingManager.link() path.
 *
 * FORWARD uses real W/forward input and pulls the consist.
 * RETURN uses real S/reverse input with the locomotive nose facing outward,
 * genuinely pushing the same consist back through the full course.
 *
 * Every vehicle must visit the same exact 12 Traincraft route owners in order,
 * all four coupling links must remain mutual, pair spacing must remain bounded,
 * and the whole consist must reach the opposite vanilla runout.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7FullConsistHeadlessCommand {
    private static final String ENV = "TC_T7_FULL_CONSIST_HEADLESS";

    private static final double START_SPEED = 0.12D;
    private static final double STALL_SPEED = 0.008D;
    private static final int STALL_TICKS = 14;
    private static final int OFF_RAIL_TICKS = 8;
    private static final int MAX_TICKS = 2600;
    private static final double MIN_PAIR_DISTANCE = 0.40D;
    private static final double MAX_PAIR_DISTANCE = 6.00D;
    private static final int FORWARD_LOCO_INSET_BLOCKS = 13;
    private static final double END_PROJECTION_TOLERANCE = -0.35D;
    private static final double RAIL_SURFACE_Y = 0.0625D;

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
            List<String> ownerSequence,
            Vec3 finalPosition,
            Vec3 finalMotion) {
    }

    private TrackT7FullConsistHeadlessCommand() {
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
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-fc1");
        report.add("HEADLESS FIVE-VEHICLE FULL-CONSIST FORWARD-REVERSE GATE");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("COURSE_BUILDER=EXACT_C2_R3_REFLECTION");
        report.add("COURSE_PLACEMENT=REAL_REGISTERED_ITEMS_USE_ON");
        report.add("ENTITY_PATH=FIVE_VEHICLE_COUPLED_CONSIST_DIRECT_SERVERLEVEL_TICKNONPASSENGER");
        report.add("TEST_CLOCK=EXPLICIT_GAME_TIME_PLUS_ONE_PER_PROBE_TICK");
        report.add("FORWARD_ROUTE_SECTION_COUNT=" + EXPECTED_FORWARD.size());
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
                false,
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
                true,
                course.courseEnd(),
                course.runoutOutward().getOpposite(),
                course.courseStart(),
                course.starterForward().getOpposite(),
                expectedReturn,
                report);

        report.add("");
        report.add("SUMMARY");
        report.add("PASS_COUNT="
                + ((forward.pass() ? 1 : 0)
                + (back.pass() ? 1 : 0)));
        report.add("FAIL_COUNT="
                + ((!forward.pass() ? 1 : 0)
                + (!back.pass() ? 1 : 0)));

        report.add(summaryLine(forward));
        report.add(summaryLine(back));
        report.add("");
        report.add("RESULT="
                + (forward.pass() && back.pass()
                ? "T7_FC1_FULL_CONSIST_FORWARD_REVERSE_PASS"
                : "T7_FC1_FULL_CONSIST_FORWARD_REVERSE_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs",
                "traincraft-track-debug",
                "t7-fc1-full-consist-forward-reverse-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(
                output,
                report,
                StandardCharsets.UTF_8);

        System.out.println(
                "[TC-T7-FC1] complete forward="
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
                "NO_CLEAN_FC1_COURSE_GRID_NEAR_"
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
            boolean reverseDrive,
            BlockPos startRail,
            Direction initialTravel,
            BlockPos exitRail,
            Direction exitOutward,
            List<String> expectedSequence,
            List<String> report) {

        SmallSteamLocomotive loco = null;
        List<Entity> chain = new ArrayList<>();

        try {
            fake.stopRiding();

            Vec3 travel =
                    new Vec3(
                            initialTravel.getStepX(),
                            0.0D,
                            initialTravel.getStepZ());

            Vec3 nose =
                    reverseDrive
                            ? travel.scale(-1.0D)
                            : travel;

            Vec3 start =
                    railCenter(startRail);

            if (!reverseDrive) {
                start = start.add(
                        travel.x * FORWARD_LOCO_INSET_BLOCKS,
                        0.0D,
                        travel.z * FORWARD_LOCO_INSET_BLOCKS);
            }

            loco = createSmallSteam(level);
            loco.setPos(start.x, start.y, start.z);
            loco.setYRot(bodyYawForTravel(nose));
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
            chain.add(loco);

            String[] followerIds = {
                    "steam_tender",
                    "freight_cart",
                    "passenger_coach_blue",
                    "caboose"
            };

            String[] roles = {
                    "LOCO",
                    "TENDER",
                    "FREIGHT",
                    "PASSENGER",
                    "CABOOSE"
            };

            Entity leader = loco;
            CoupleableRollingStock leaderStock =
                    (CoupleableRollingStock) loco;

            for (int i = 0; i < followerIds.length; i++) {
                Entity follower =
                        createRollingStock(
                                level,
                                followerIds[i]);

                double seedSign =
                        reverseDrive ? 1.0D : -1.0D;
                double seedDistance =
                        2.0D;

                follower.setPos(
                        leader.getX() + travel.x * seedSign * seedDistance,
                        start.y,
                        leader.getZ() + travel.z * seedSign * seedDistance);
                follower.setYRot(bodyYawForTravel(nose));
                follower.setDeltaMovement(
                        travel.x * START_SPEED,
                        0.0D,
                        travel.z * START_SPEED);

                if (!level.addFreshEntity(follower)) {
                    follower.discard();
                    return setupFailure(
                            label,
                            "ADD_" + roles[i + 1] + "_FAILED");
                }

                chain.add(follower);

                if (!(follower instanceof CoupleableRollingStock followerStock)) {
                    return setupFailure(
                            label,
                            roles[i + 1] + "_NOT_COUPLEABLE");
                }

                if (!CouplingManager.link(
                        leader,
                        leaderStock,
                        follower,
                        followerStock)) {
                    return setupFailure(
                            label,
                            "COUPLING_FAILED_"
                                    + roles[i]
                                    + "_TO_"
                                    + roles[i + 1]);
                }

                leader = follower;
                leaderStock = followerStock;
            }

            if (chain.size() != 5) {
                return setupFailure(label, "CHAIN_SIZE_" + chain.size());
            }

            fake.setPos(
                    loco.getX(),
                    loco.getY() + 1.0D,
                    loco.getZ());

            if (!fake.startRiding(loco, true)) {
                return setupFailure(
                        label,
                        "FAKE_DRIVER_COULD_NOT_RIDE");
            }

            setControls(loco, reverseDrive);

            report.add(
                    "RUN_BEGIN label="
                            + label
                            + " drive="
                            + (reverseDrive ? "REVERSE_S" : "FORWARD_W")
                            + " startRail="
                            + startRail
                            + " locoStart="
                            + vec(loco.position())
                            + " initialTravel="
                            + initialTravel
                            + " noseYaw="
                            + fmt(loco.getYRot())
                            + " exitRail="
                            + exitRail
                            + " exitOutward="
                            + exitOutward
                            + " expectedSequence="
                            + String.join(">", expectedSequence));

            List<List<String>> vehicleSequences =
                    new ArrayList<>();
            List<String> lastOwners =
                    new ArrayList<>();
            boolean[] entered =
                    new boolean[chain.size()];
            int[] offRail =
                    new int[chain.size()];

            for (int i = 0; i < chain.size(); i++) {
                vehicleSequences.add(new ArrayList<>());
                lastOwners.add(null);
            }

            double minSpeed = Double.POSITIVE_INFINITY;
            double maxHorizontalStep = 0.0D;
            double maxVerticalStep = 0.0D;
            double minY = loco.getY();
            double maxY = loco.getY();
            double minPairDistance = Double.POSITIVE_INFINITY;
            double maxPairDistance = 0.0D;

            int stallTicks = 0;
            int ticks = 0;
            boolean reachedExit = false;
            String classification = "TIMEOUT";

            for (int tick = 1; tick <= MAX_TICKS; tick++) {
                ticks = tick;

                for (int i = 0; i < chain.size(); i++) {
                    Entity entity = chain.get(i);
                    if (entity.isRemoved()) {
                        classification =
                                roles[i] + "_REMOVED";
                        break;
                    }
                }
                if (!"TIMEOUT".equals(classification)) {
                    break;
                }

                Vec3 preLoco = loco.position();

                setControls(loco, reverseDrive);

                long gameTimeBefore = level.getGameTime();
                advanceDeterministicGameTime(level);
                long gameTimeForTick = level.getGameTime();

                for (Entity entity : chain) {
                    if (!entity.isRemoved()) {
                        level.tickNonPassenger(entity);
                    }
                }

                Vec3 postLoco = loco.position();
                Vec3 postMotion = loco.getDeltaMovement();
                double speed = horizontalSpeed(postMotion);

                minSpeed = Math.min(minSpeed, speed);
                maxHorizontalStep = Math.max(
                        maxHorizontalStep,
                        Math.hypot(
                                postLoco.x - preLoco.x,
                                postLoco.z - preLoco.z));
                maxVerticalStep = Math.max(
                        maxVerticalStep,
                        Math.abs(postLoco.y - preLoco.y));
                minY = Math.min(minY, postLoco.y);
                maxY = Math.max(maxY, postLoco.y);

                boolean linksOk = true;

                for (int i = 0; i < chain.size() - 1; i++) {
                    Entity first = chain.get(i);
                    Entity second = chain.get(i + 1);

                    if (!(first instanceof CoupleableRollingStock firstStock)
                            || !(second instanceof CoupleableRollingStock secondStock)
                            || !CouplingManager.areMutuallyLinked(
                                    first,
                                    firstStock,
                                    second,
                                    secondStock)) {
                        classification =
                                "COUPLING_LINK_BROKEN_"
                                        + roles[i]
                                        + "_TO_"
                                        + roles[i + 1];
                        linksOk = false;
                        break;
                    }

                    double distance =
                            Math.sqrt(first.distanceToSqr(second));
                    minPairDistance =
                            Math.min(minPairDistance, distance);
                    maxPairDistance =
                            Math.max(maxPairDistance, distance);

                    if (distance < MIN_PAIR_DISTANCE) {
                        classification =
                                "PAIR_COLLAPSE_"
                                        + roles[i]
                                        + "_TO_"
                                        + roles[i + 1]
                                        + "_DIST_"
                                        + fmt(distance);
                        linksOk = false;
                        break;
                    }

                    if (distance > MAX_PAIR_DISTANCE) {
                        classification =
                                "PAIR_STRETCH_"
                                        + roles[i]
                                        + "_TO_"
                                        + roles[i + 1]
                                        + "_DIST_"
                                        + fmt(distance);
                        linksOk = false;
                        break;
                    }
                }

                if (!linksOk) {
                    break;
                }

                boolean allOnExitRunout = true;

                for (int i = 0; i < chain.size(); i++) {
                    Entity entity = chain.get(i);
                    String owner = ownerAt(level, entity);
                    boolean isTraincraft =
                            owner.startsWith("track_");

                    if (isTraincraft) {
                        entered[i] = true;
                        offRail[i] = 0;

                        if (!owner.equals(lastOwners.get(i))) {
                            vehicleSequences.get(i).add(owner);
                            lastOwners.set(i, owner);
                            report.add(
                                    "OWNER_EVENT label="
                                            + label
                                            + " role="
                                            + roles[i]
                                            + " tick="
                                            + tick
                                            + " owner="
                                            + owner
                                            + " pos="
                                            + vec(entity.position()));
                        }
                    } else if ("minecraft:rail".equals(owner)) {
                        offRail[i] = 0;
                    } else if (entered[i]) {
                        offRail[i]++;
                    }

                    if (offRail[i] >= OFF_RAIL_TICKS) {
                        classification =
                                "OFF_RAIL_" + roles[i];
                        allOnExitRunout = false;
                        break;
                    }

                    double exitProjection =
                            (entity.getX()
                                    - (exitRail.getX() + 0.5D))
                                    * exitOutward.getStepX()
                            + (entity.getZ()
                                    - (exitRail.getZ() + 0.5D))
                                    * exitOutward.getStepZ();

                    if (!entered[i]
                            || !"minecraft:rail".equals(owner)
                            || exitProjection
                                    < END_PROJECTION_TOLERANCE) {
                        allOnExitRunout = false;
                    }
                }

                if (!"TIMEOUT".equals(classification)) {
                    break;
                }

                if (speed < STALL_SPEED) {
                    stallTicks++;
                } else {
                    stallTicks = 0;
                }

                if (stallTicks >= STALL_TICKS) {
                    classification = "POWERED_STALL";
                    break;
                }

                if (allOnExitRunout) {
                    reachedExit = true;

                    boolean sequencesOk = true;
                    for (int i = 0; i < chain.size(); i++) {
                        if (!vehicleSequences.get(i).equals(
                                expectedSequence)) {
                            sequencesOk = false;
                            classification =
                                    "OWNER_SEQUENCE_MISMATCH_"
                                            + roles[i];
                            break;
                        }
                    }

                    if (sequencesOk
                            && speed > STALL_SPEED) {
                        classification = "FULL_CONSIST_PASS";
                    } else if (sequencesOk) {
                        classification =
                                "EXIT_WITHOUT_MOTION";
                    }

                    break;
                }

                if (tick == 1
                        || tick % 20 == 0) {
                    StringBuilder distances =
                            new StringBuilder();

                    for (int i = 0; i < chain.size() - 1; i++) {
                        if (i > 0) {
                            distances.append(",");
                        }
                        distances.append(roles[i])
                                .append("->")
                                .append(roles[i + 1])
                                .append("=")
                                .append(fmt(Math.sqrt(
                                        chain.get(i)
                                                .distanceToSqr(
                                                        chain.get(i + 1)))));
                    }

                    report.add(
                            "TRACE label="
                                    + label
                                    + " tick="
                                    + tick
                                    + " gameTimeBefore="
                                    + gameTimeBefore
                                    + " gameTimeForTick="
                                    + gameTimeForTick
                                    + " locoSpeed="
                                    + fmt(speed)
                                    + " locoPos="
                                    + vec(postLoco)
                                    + " pairDistances={"
                                    + distances
                                    + "}");
                }
            }

            boolean pass =
                    "FULL_CONSIST_PASS".equals(classification);

            if (!Double.isFinite(minSpeed)) {
                minSpeed = 0.0D;
            }
            if (!Double.isFinite(minPairDistance)) {
                minPairDistance = 0.0D;
            }

            for (int i = 0; i < chain.size(); i++) {
                report.add(
                        "VEHICLE_SUMMARY label="
                                + label
                                + " role="
                                + roles[i]
                                + " enteredTraincraft="
                                + entered[i]
                                + " ownerSequence="
                                + String.join(
                                        ">",
                                        vehicleSequences.get(i))
                                + " finalOwner="
                                + ownerAt(level, chain.get(i))
                                + " finalPos="
                                + vec(chain.get(i).position())
                                + " finalMotion="
                                + vec(chain.get(i).getDeltaMovement()));
            }

            report.add(
                    "CONSIST_METRICS label="
                            + label
                            + " minPairDistance="
                            + fmt(minPairDistance)
                            + " maxPairDistance="
                            + fmt(maxPairDistance));

            RunResult result =
                    new RunResult(
                            label,
                            pass,
                            classification,
                            ticks,
                            true,
                            reachedExit,
                            minSpeed,
                            maxHorizontalStep,
                            maxVerticalStep,
                            maxY - minY,
                            List.copyOf(
                                    vehicleSequences.get(0)),
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

            for (int i = chain.size() - 1; i >= 0; i--) {
                Entity entity = chain.get(i);
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
    }

    private static void setControls(
            SmallSteamLocomotive loco,
            boolean reverseDrive) {

        if (reverseDrive) {
            loco.setDriverControls(
                    false,
                    true,
                    false);
        } else {
            loco.setDriverControls(
                    true,
                    false,
                    false);
        }
    }

    private static SmallSteamLocomotive createSmallSteam(
            ServerLevel level) {

        Entity entity =
                createRollingStock(
                        level,
                        "locomotive_steam_small");

        if (!(entity instanceof SmallSteamLocomotive steam)) {
            entity.discard();
            throw new IllegalStateException(
                    "LOCOMOTIVE_NOT_SMALL_STEAM");
        }

        return steam;
    }

    private static Entity createRollingStock(
            ServerLevel level,
            String entityId) {

        EntityType<?> type =
                ForgeRegistries.ENTITY_TYPES.getValue(
                        new ResourceLocation(
                                "traincraft",
                                entityId));

        if (type == null) {
            throw new IllegalStateException(
                    "MISSING_ENTITY_TYPE_"
                            + entityId);
        }

        Entity entity =
                type.create(level);

        if (entity == null) {
            throw new IllegalStateException(
                    "ENTITY_CREATE_RETURNED_NULL_"
                            + entityId);
        }

        return entity;
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
            Entity entity) {

        BlockPos base =
                entity.blockPosition();

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
                        "FC1_CANNOT_ADVANCE_GAME_TIME_type_"
                                + data.getClass().getName(),
                        declaredFailure);
            }
        }

        if (level.getGameTime()
                != next) {
            throw new IllegalStateException(
                    "FC1_GAME_TIME_DID_NOT_ADVANCE_actual_"
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
                "RUN_SUMMARY label=%s PASS=%s classification=%s ticks=%d enteredTraincraft=%s reachedExit=%s minHorizontalSpeed=%.9f maxOneTickHorizontalDisplacement=%.9f maxOneTickVerticalDelta=%.9f verticalTravel=%.9f ownerSequence=%s finalPos=(%.6f,%.6f,%.6f) finalMotion=(%.6f,%.6f,%.6f)",
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
                        "TAFARICRAFT - TRAINS STEP 9.3g-t7-fc1",
                        "HEADLESS FIVE-VEHICLE FULL-CONSIST FORWARD-REVERSE GATE",
                        "HARNESS_EXCEPTION="
                                + t.getClass().getName(),
                        "HARNESS_MESSAGE="
                                + String.valueOf(
                                        t.getMessage()),
                        "RESULT=T7_FC1_FULL_CONSIST_FORWARD_REVERSE_HARNESS_FAILURE");

        Path output = Path.of(
                "logs",
                "traincraft-track-debug",
                "t7-fc1-full-consist-forward-reverse-"
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
