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
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 9.3g-t7-rp1 — focused real-placement seam gate.
 *
 * TEMPORARY DIAGNOSTIC ONLY.
 *
 * Every Traincraft source and incoming piece is placed through its real
 * registered item/useOn() path. The locomotive is then spawned just inside
 * the selected source endpoint and is ticked directly through
 * ServerLevel.tickNonPassenger so world/fallback scheduling cannot alter the
 * result.
 *
 * Cases:
 *  1. Diamond Crossing Left endpoint index 2 / p3 -> Medium Diagonal.
 *  2. Small Diagonal endpoint index 1 / p2 -> 45 Medium Right switch.
 *  3. Crossing X endpoint index 0 / p0 -> Medium Diagonal.
 *  4. Very Large Left switch endpoint index 1 / p9 -> 45 Large Left curve.
 *  5. Known c2 seam: 45 Medium Right active endpoint index 2 / p4
 *     -> Medium Diagonal p0.
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class TrackT7RealPlacementSeamCommand {
    private static final String ENV = "TC_T7_RP1_HEADLESS";
    private static final double TEST_SPEED = 0.18D;
    private static final double HARD_SPEED_KILL = 0.010D;
    private static final int MAX_TICKS = 100;
    private static final int STARTER_LENGTH = 8;
    private static final int SEARCH_RADIUS = 28;
    private static final int SUPPORT_PROBE_RADIUS = 3;
    private static final int CASE_SPACING = 180;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private record CaseDef(
            String label,
            String sourceId,
            int sourceEndpointIndex,
            int expectedSourcePart,
            Boolean sourceActive,
            String incomingId,
            int expectedIncomingEndpointIndex,
            Boolean incomingActive) {
    }

    private record Assembly(
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block,
            BlockPos root,
            Direction facing) {
    }

    private record CourseEnd(
            BlockPos physical,
            BlockPos logical,
            Direction outward,
            String sourceId) {
    }

    private record CaseResult(
            String label,
            boolean pass,
            String classification,
            int ticks,
            int actualIncomingEndpointIndex,
            boolean expectedIncomingEndpointMatched,
            int ownerTransitionTick,
            double minHorizontalSpeed,
            double maxOneTickHorizontalDisplacement,
            double maxOneTickVerticalDelta,
            String sourceRootFacing,
            String incomingRootFacing,
            String finalOwner,
            Vec3 finalPosition,
            Vec3 finalMotion) {
    }

    private static final List<CaseDef> CASES = List.of(
            // STEP_9_3G_T7_RP1_R3_VALID_DIAMOND_DIAGONAL_ROUTE
            //
            // Diamond Left endpoints are paired by route:
            //   index 0 p1 <-> index 1 p4 = diagonal route
            //   index 2 p3 <-> index 3 p2 = cardinal N/S route
            //
            // rp1-r1/r2 incorrectly asked Medium Diagonal to attach to p3.
            // NO_SNAP_CANDIDATE was therefore correct. Test the real diagonal
            // p1 route instead. s4c placement intentionally prefers Medium p6,
            // which is endpoint index 1.
            new CaseDef(
                    "diamond_left_p1_to_medium_diagonal",
                    "track_diamond_crossing_left",
                    0,
                    1,
                    null,
                    "track_diagonal_straight_medium",
                    1,
                    null),
            new CaseDef(
                    "small_diagonal_p2_to_45_medium_right",
                    "track_diagonal_straight_small",
                    1,
                    2,
                    null,
                    "track_switch_45_medium_right",
                    0,
                    Boolean.TRUE),
            new CaseDef(
                    "crossing_x_e0_to_medium_diagonal",
                    "track_crossing_x",
                    0,
                    0,
                    null,
                    "track_diagonal_straight_medium",
                    0,
                    null),
            new CaseDef(
                    "very_large_left_p9_to_45_large_left",
                    "track_switch_very_large_left",
                    1,
                    9,
                    Boolean.FALSE,
                    "track_curve_45_large_left",
                    0,
                    null),
            new CaseDef(
                    "c2_45_medium_right_p4_to_medium_diagonal",
                    "track_switch_45_medium_right",
                    2,
                    4,
                    Boolean.TRUE,
                    "track_diagonal_straight_medium",
                    0,
                    null)
    );

    private TrackT7RealPlacementSeamCommand() {
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
        BlockPos spawn = level.getSharedSpawnPos();

        int railY = Math.max(
                level.getMinBuildHeight() + 20,
                Math.min(level.getMaxBuildHeight() - 24, spawn.getY() + 28));

        // STEP_9_3G_T7_RP1_R2_DETERMINISTIC_GAMETIME
        // Use a fresh diagnostic grid so earlier rp1 runs cannot leave stale
        // starter rails/supports in the same coordinates.
        BlockPos origin = new BlockPos(
                spawn.getX() + 1640,
                railY,
                spawn.getZ() + 1640);

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setShiftKeyDown(false);

        List<String> report = new ArrayList<>();
        List<CaseResult> results = new ArrayList<>();

        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-rp1-r3");
        report.add("FOCUSED REAL-PLACEMENT SEAM GATE");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PLACEMENT_PATH=REAL_REGISTERED_ITEM_USEON");
        report.add("ENTITY_PATH=DIRECT_SERVERLEVEL_TICKNONPASSENGER");
        report.add("TEST_CLOCK=EXPLICIT_GAME_TIME_PLUS_ONE_PER_PROBE_TICK");
        report.add("CASE_GRID=CLEAN_AIR_SCAN_NO_DESTRUCTIVE_PRECLEAR");
        report.add("DIAMOND_LEFT_ROUTE=P1_TO_P4_DIAGONAL_P3_TO_P2_CARDINAL");
        report.add("CASE_COUNT=" + CASES.size());
        report.add("");

        for (int i = 0; i < CASES.size(); i++) {
            CaseDef def = CASES.get(i);
            BlockPos desiredSouth =
                    origin.offset(i * CASE_SPACING, 0, 0);
            BlockPos starterSouth =
                    findCleanCaseStarter(level, desiredSouth);

            if (!starterSouth.equals(desiredSouth)) {
                report.add("CASE_GRID_RELOCATED label="
                        + def.label()
                        + " desired=" + desiredSouth
                        + " actual=" + starterSouth);
            }

            CaseResult result = runCase(
                    level,
                    fake,
                    starterSouth,
                    def,
                    report);

            results.add(result);
            report.add("");
        }

        long passCount = results.stream()
                .filter(CaseResult::pass)
                .count();

        long expectedMatchCount = results.stream()
                .filter(CaseResult::expectedIncomingEndpointMatched)
                .count();

        report.add("SUMMARY");
        report.add("PASS_COUNT=" + passCount);
        report.add("FAIL_COUNT=" + (results.size() - passCount));
        report.add("EXPECTED_INCOMING_ENDPOINT_MATCH_COUNT=" + expectedMatchCount);

        for (CaseResult r : results) {
            report.add(String.format(
                    Locale.ROOT,
                    "CASE_SUMMARY label=%s PASS=%s classification=%s ticks=%d"
                            + " actualIncomingEndpoint=%d expectedIncomingEndpointMatched=%s"
                            + " ownerTransitionTick=%d minHorizontalSpeed=%.9f"
                            + " maxOneTickHorizontalDisplacement=%.9f"
                            + " maxOneTickVerticalDelta=%.9f"
                            + " source=%s incoming=%s finalOwner=%s"
                            + " finalPos=(%.6f,%.6f,%.6f)"
                            + " finalMotion=(%.6f,%.6f,%.6f)",
                    r.label(),
                    Boolean.toString(r.pass()),
                    r.classification(),
                    r.ticks(),
                    r.actualIncomingEndpointIndex(),
                    Boolean.toString(r.expectedIncomingEndpointMatched()),
                    r.ownerTransitionTick(),
                    r.minHorizontalSpeed(),
                    r.maxOneTickHorizontalDisplacement(),
                    r.maxOneTickVerticalDelta(),
                    r.sourceRootFacing(),
                    r.incomingRootFacing(),
                    r.finalOwner(),
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
                ? "T7_RP1_R3_REAL_PLACEMENT_SEAMS_PASS"
                : "T7_RP1_R3_REAL_PLACEMENT_SEAMS_REVIEW_REQUIRED"));

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rp1-r3-real-placement-seams-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, report, StandardCharsets.UTF_8);

        System.out.println(
                "[TC-T7-RP1] complete pass="
                        + passCount + "/" + results.size()
                        + " report=" + output);

        server.halt(false);
    }

    // STEP_9_3G_T7_RP1_R3_CLEAN_CASE_GRID
    //
    // Never destructively clear arbitrary world blocks. If the preferred
    // diagnostic cell was used by an earlier run, scan forward in isolated
    // 64-block Z strides until the whole rail/overhead plane is empty.
    // Existing support blocks below the rail plane are allowed because
    // prepareSupports()/ensureSupport() can safely reuse sturdy supports.
    private static BlockPos findCleanCaseStarter(
            ServerLevel level,
            BlockPos desired) {
        final int radius = 24;
        final int maxAttempts = 64;
        final int stride = 64;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            BlockPos candidate =
                    desired.offset(0, 0, attempt * stride);

            boolean clean = true;
            for (int dx = -radius; dx <= radius && clean; dx++) {
                for (int dz = -radius; dz <= radius && clean; dz++) {
                    BlockPos railPlane =
                            candidate.offset(dx, 0, dz);
                    BlockPos overhead = railPlane.above();

                    if (!level.getBlockState(railPlane).isAir()
                            || !level.getBlockState(overhead).isAir()) {
                        clean = false;
                    }
                }
            }

            if (clean) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "NO_CLEAN_RP1_R3_CASE_GRID_NEAR_" + desired);
    }

    private static CaseResult runCase(
            ServerLevel level,
            FakePlayer fake,
            BlockPos starterSouth,
            CaseDef def,
            List<String> report) {

        Set<BlockPos> owned = new HashSet<>();
        Set<ChunkPos> forced = new HashSet<>();
        AbstractMinecart probe = null;

        report.add("CASE_BEGIN label=" + def.label());

        try {
            LegacyBatchTrackSpec sourceSpec = findSpec(def.sourceId());
            LegacyBatchTrackSpec incomingSpec = findSpec(def.incomingId());

            CourseEnd starter = placeStarter(
                    level,
                    starterSouth,
                    owned);

            Assembly source = placeRealAssembly(
                    level,
                    fake,
                    starter,
                    sourceSpec,
                    def.sourceId(),
                    owned);

            int sourceValid = validateAssembly(level, source);
            if (sourceValid != sourceSpec.partCount()) {
                return setupFailure(
                        def,
                        "SOURCE_ASSEMBLY_INVALID_"
                                + sourceValid + "_OF_" + sourceSpec.partCount(),
                        source);
            }

            int starterAttachedIndex =
                    findAttachedEndpointIndex(source, starter);

            if (def.sourceEndpointIndex() < 0
                    || def.sourceEndpointIndex() >= sourceSpec.endpoints().size()) {
                return setupFailure(def, "SOURCE_ENDPOINT_INDEX_OUT_OF_RANGE", source);
            }

            LegacyBatchTrackSpec.Endpoint selectedSourceEndpoint =
                    sourceSpec.endpoints().get(def.sourceEndpointIndex());

            if (selectedSourceEndpoint.part() != def.expectedSourcePart()) {
                return setupFailure(
                        def,
                        "SOURCE_PART_MISMATCH_actual_p"
                                + selectedSourceEndpoint.part()
                                + "_expected_p"
                                + def.expectedSourcePart(),
                        source);
            }

            if (starterAttachedIndex == def.sourceEndpointIndex()) {
                return setupFailure(
                        def,
                        "SOURCE_TEST_ENDPOINT_OCCUPIED_BY_STARTER_ATTACHMENT",
                        source);
            }

            if (def.sourceActive() != null) {
                setSwitchActive(level, source, def.sourceActive());
            }

            CourseEnd selectedSourceEnd =
                    endFor(source, selectedSourceEndpoint);

            Assembly incoming = placeRealAssembly(
                    level,
                    fake,
                    selectedSourceEnd,
                    incomingSpec,
                    def.incomingId(),
                    owned);

            int incomingValid = validateAssembly(level, incoming);
            if (incomingValid != incomingSpec.partCount()) {
                return setupFailure(
                        def,
                        "INCOMING_ASSEMBLY_INVALID_"
                                + incomingValid + "_OF_" + incomingSpec.partCount(),
                        source);
            }

            int actualIncomingEndpointIndex =
                    findAttachedEndpointIndex(incoming, selectedSourceEnd);

            boolean expectedIncomingMatched =
                    actualIncomingEndpointIndex
                            == def.expectedIncomingEndpointIndex();

            if (def.incomingActive() != null) {
                setSwitchActive(level, incoming, def.incomingActive());
            }

            addAssemblyToOwned(source, owned);
            addAssemblyToOwned(incoming, owned);

            for (BlockPos pos : owned) {
                forced.add(new ChunkPos(
                        pos.getX() >> 4,
                        pos.getZ() >> 4));
            }

            for (ChunkPos chunk : forced) {
                level.setChunkForced(chunk.x, chunk.z, true);
            }

            LegacyBatchTrackSpec.Endpoint incomingAttached =
                    incomingSpec.endpoints().get(actualIncomingEndpointIndex);

            BlockPos sourceCore =
                    sourceSpec.offsetForPart(
                            source.root(),
                            source.facing(),
                            selectedSourceEndpoint.part());

            BlockPos incomingCore =
                    incomingSpec.offsetForPart(
                            incoming.root(),
                            incoming.facing(),
                            incomingAttached.part());

            Vec3 sourceCenter = railCenter(sourceCore);
            Vec3 incomingCenter = railCenter(incomingCore);

            Vec3 direction = new Vec3(
                    incomingCenter.x - sourceCenter.x,
                    0.0D,
                    incomingCenter.z - sourceCenter.z);

            if (direction.horizontalDistanceSqr() < 1.0E-8D) {
                BlockPos required =
                        selectedSourceEnd.logical()
                                .relative(selectedSourceEnd.outward());
                Vec3 requiredCenter = railCenter(required);

                direction = new Vec3(
                        requiredCenter.x - sourceCenter.x,
                        0.0D,
                        requiredCenter.z - sourceCenter.z);
            }

            if (direction.horizontalDistanceSqr() < 1.0E-8D) {
                return setupFailure(def, "ZERO_HANDOFF_VECTOR", source);
            }

            direction = direction.normalize();

            Vec3 start = sourceCenter.subtract(
                    direction.x * 0.20D,
                    0.0D,
                    direction.z * 0.20D);

            EntityType<?> probeType =
                    ForgeRegistries.ENTITY_TYPES.getValue(
                            new ResourceLocation(
                                    "traincraft",
                                    "locomotive_steam_small"));

            if (probeType == null) {
                return setupFailure(def, "MISSING_LOCOMOTIVE_ENTITY_TYPE", source);
            }

            Entity raw = probeType.create(level);
            if (!(raw instanceof AbstractMinecart minecart)) {
                if (raw != null) {
                    raw.discard();
                }
                return setupFailure(def, "LOCOMOTIVE_NOT_ABSTRACT_MINECART", source);
            }

            probe = minecart;
            probe.setPos(start.x, start.y, start.z);
            probe.setDeltaMovement(
                    direction.x * TEST_SPEED,
                    0.0D,
                    direction.z * TEST_SPEED);
            probe.setYRot(yawFromVector(direction));

            if (!level.addFreshEntity(probe)) {
                probe.discard();
                probe = null;
                return setupFailure(def, "ADD_PROBE_FAILED", source);
            }

            fake.setPos(
                    starterSouth.getX() + 0.5D,
                    starterSouth.getY() + 40.0D,
                    starterSouth.getZ() + 0.5D);

            String sourceRootFacing =
                    source.spec().id()
                            + "@"
                            + source.root()
                            + "/"
                            + source.facing().getName();

            String incomingRootFacing =
                    incoming.spec().id()
                            + "@"
                            + incoming.root()
                            + "/"
                            + incoming.facing().getName();

            report.add(
                    "SETUP label="
                            + def.label()
                            + " starterAttachedEndpoint="
                            + starterAttachedIndex
                            + " sourceEndpointIndex="
                            + def.sourceEndpointIndex()
                            + " sourcePart="
                            + selectedSourceEndpoint.part()
                            + " sourceActive="
                            + String.valueOf(def.sourceActive())
                            + " incomingAttachedEndpoint="
                            + actualIncomingEndpointIndex
                            + " expectedIncomingEndpoint="
                            + def.expectedIncomingEndpointIndex()
                            + " expectedIncomingEndpointMatched="
                            + expectedIncomingMatched
                            + " incomingActive="
                            + String.valueOf(def.incomingActive())
                            + " sourceCore="
                            + sourceCore
                            + " incomingCore="
                            + incomingCore);

            double minSpeed = Double.POSITIVE_INFINITY;
            double maxHorizontalStep = 0.0D;
            double maxVerticalStep = 0.0D;
            int ownerTransitionTick = -1;
            boolean enteredIncoming = false;
            String classification = "TIMEOUT";
            int ticks = 0;

            String previousOwner = ownerAt(probe);
            Vec3 previousPosition = probe.position();

            for (int tick = 1; tick <= MAX_TICKS; tick++) {
                ticks = tick;

                if (probe.isRemoved()) {
                    classification = "PROBE_REMOVED";
                    break;
                }

                Vec3 prePos = probe.position();
                Vec3 preMotion = probe.getDeltaMovement();
                double preSpeed = horizontalSpeed(preMotion);

                // STEP_9_3G_T7_RP1_R2_DETERMINISTIC_GAMETIME
                //
                // LegacyContinuousTrackPath advances latched route progress from
                // (level.getGameTime() - lastTick). rp1-r1 called
                // tickNonPassenger() repeatedly while ServerStartedEvent still
                // owned the server thread, so the world clock never advanced.
                // That made deltaTicks=0 after the first probe tick and could
                // pin a cart on a continuous route while velocity decayed.
                //
                // Advance ONLY the test world's gameTime by one immediately
                // before each direct entity tick. This preserves one single
                // deterministic entity-tick path while giving session logic the
                // same +1 clock progression it receives on a real server tick.
                long gameTimeBefore = level.getGameTime();
                advanceDeterministicGameTime(level);
                long gameTimeForTick = level.getGameTime();

                level.tickNonPassenger(probe);

                if (probe.isRemoved()) {
                    report.add(
                            "TRACE label=" + def.label()
                                    + " tick=" + tick
                                    + " gameTimeBefore=" + gameTimeBefore
                                    + " gameTimeForTick=" + gameTimeForTick
                                    + " preOwner=" + previousOwner
                                    + " postOwner=REMOVED"
                                    + " prePos=" + vec(prePos)
                                    + " preMotion=" + vec(preMotion));
                    classification = "PROBE_REMOVED";
                    break;
                }

                Vec3 postPos = probe.position();
                Vec3 postMotion = probe.getDeltaMovement();
                double postSpeed = horizontalSpeed(postMotion);

                minSpeed = Math.min(minSpeed, postSpeed);

                double horizontalStep =
                        Math.sqrt(
                                Math.pow(postPos.x - prePos.x, 2.0D)
                                        + Math.pow(postPos.z - prePos.z, 2.0D));
                double verticalStep =
                        Math.abs(postPos.y - prePos.y);

                maxHorizontalStep =
                        Math.max(maxHorizontalStep, horizontalStep);
                maxVerticalStep =
                        Math.max(maxVerticalStep, verticalStep);

                String postOwner = ownerAt(probe);
                boolean onSource = onAssembly(probe, source);
                boolean onIncoming = onAssembly(probe, incoming);

                if (onIncoming && ownerTransitionTick < 0) {
                    ownerTransitionTick = tick;
                }

                report.add(
                        "TRACE label=" + def.label()
                                + " tick=" + tick
                                + " gameTimeBefore=" + gameTimeBefore
                                + " gameTimeForTick=" + gameTimeForTick
                                + " preOwner=" + previousOwner
                                + " postOwner=" + postOwner
                                + " onSource=" + onSource
                                + " onIncoming=" + onIncoming
                                + " prePos=" + vec(prePos)
                                + " postPos=" + vec(postPos)
                                + " preMotion=" + vec(preMotion)
                                + " postMotion=" + vec(postMotion)
                                + " yaw="
                                + String.format(Locale.ROOT, "%.3f", probe.getYRot()));

                if (onIncoming) {
                    enteredIncoming = true;
                    if (postSpeed > HARD_SPEED_KILL) {
                        classification = expectedIncomingMatched
                                ? "HANDOFF_PASS_EXPECTED_SEAM"
                                : "HANDOFF_PASS_REAL_SNAP_DIFFERENT_ENDPOINT";
                        break;
                    }
                }

                if (preSpeed >= 0.050D
                        && postSpeed < HARD_SPEED_KILL
                        && !onIncoming) {
                    classification = "HANDOFF_SPEED_KILL";
                    break;
                }

                if (postPos.y < start.y - 1.25D) {
                    classification = "OFF_RAIL_VERTICAL_DROP";
                    break;
                }

                if (!"NONE".equals(postOwner)
                        && !"minecraft:rail".equals(postOwner)
                        && !postOwner.startsWith(source.spec().id() + "@")
                        && !postOwner.startsWith(incoming.spec().id() + "@")) {
                    classification = "WRONG_OWNER_" + sanitize(postOwner);
                    break;
                }

                previousOwner = postOwner;
                previousPosition = postPos;
            }

            if (Double.isInfinite(minSpeed)) {
                minSpeed = horizontalSpeed(probe.getDeltaMovement());
            }

            boolean pass = enteredIncoming
                    && classification.startsWith("HANDOFF_PASS");

            report.add(
                    "CASE_END label="
                            + def.label()
                            + " PASS="
                            + pass
                            + " classification="
                            + classification
                            + " ticks="
                            + ticks
                            + " ownerTransitionTick="
                            + ownerTransitionTick
                            + " minHorizontalSpeed="
                            + String.format(Locale.ROOT, "%.9f", minSpeed)
                            + " maxOneTickHorizontalDisplacement="
                            + String.format(Locale.ROOT, "%.9f", maxHorizontalStep)
                            + " maxOneTickVerticalDelta="
                            + String.format(Locale.ROOT, "%.9f", maxVerticalStep)
                            + " finalOwner="
                            + ownerAt(probe));

            return new CaseResult(
                    def.label(),
                    pass,
                    classification,
                    ticks,
                    actualIncomingEndpointIndex,
                    expectedIncomingMatched,
                    ownerTransitionTick,
                    minSpeed,
                    maxHorizontalStep,
                    maxVerticalStep,
                    sourceRootFacing,
                    incomingRootFacing,
                    ownerAt(probe),
                    probe.position(),
                    probe.getDeltaMovement());

        } catch (Throwable t) {
            report.add(
                    "CASE_EXCEPTION label="
                            + def.label()
                            + " type="
                            + t.getClass().getName()
                            + " message="
                            + String.valueOf(t.getMessage()));

            return new CaseResult(
                    def.label(),
                    false,
                    "HARNESS_EXCEPTION_" + sanitize(t.getClass().getSimpleName()),
                    0,
                    -1,
                    false,
                    -1,
                    0.0D,
                    0.0D,
                    0.0D,
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    Vec3.ZERO,
                    Vec3.ZERO);

        } finally {
            if (probe != null && !probe.isRemoved()) {
                probe.discard();
            }

            for (ChunkPos chunk : forced) {
                try {
                    level.setChunkForced(chunk.x, chunk.z, false);
                } catch (Throwable ignored) {
                }
            }

            clearOwned(level, owned);
        }
    }

    private static CaseResult setupFailure(
            CaseDef def,
            String classification,
            Assembly source) {

        String sourceRootFacing =
                source == null
                        ? "UNKNOWN"
                        : source.spec().id()
                                + "@"
                                + source.root()
                                + "/"
                                + source.facing().getName();

        return new CaseResult(
                def.label(),
                false,
                classification,
                0,
                -1,
                false,
                -1,
                0.0D,
                0.0D,
                0.0D,
                sourceRootFacing,
                "UNKNOWN",
                "NONE",
                Vec3.ZERO,
                Vec3.ZERO);
    }

    private static CourseEnd placeStarter(
            ServerLevel level,
            BlockPos south,
            Set<BlockPos> owned) {

        BlockState railState =
                Blocks.RAIL.defaultBlockState()
                        .setValue(
                                RailBlock.SHAPE,
                                RailShape.NORTH_SOUTH);

        for (int i = 0; i < STARTER_LENGTH; i++) {
            BlockPos rail = south.north(i);

            if (!level.getBlockState(rail).isAir()) {
                throw new IllegalStateException(
                        "STARTER_RAIL_OCCUPIED_" + rail);
            }

            ensureSupport(level, rail.below(), owned);
            level.setBlock(rail, railState, 2);
            owned.add(rail);
        }

        BlockPos northEnd = south.north(STARTER_LENGTH - 1);

        return new CourseEnd(
                northEnd,
                northEnd,
                Direction.NORTH,
                "minecraft:rail");
    }

    private static Assembly placeRealAssembly(
            ServerLevel level,
            FakePlayer fake,
            CourseEnd source,
            LegacyBatchTrackSpec spec,
            String itemId,
            Set<BlockPos> owned) {

        prepareSupports(level, source, spec, owned);

        Item item =
                ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation("traincraft", itemId));

        if (item == null || item == Items.AIR) {
            throw new IllegalStateException(
                    "MISSING_ITEM_" + itemId);
        }

        setFakePlayerFor(fake, source);

        ItemStack stack = new ItemStack(item);
        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                stack);

        BlockHitResult hit =
                new BlockHitResult(
                        Vec3.atCenterOf(source.physical()),
                        Direction.UP,
                        source.physical(),
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

        if (!result.consumesAction()) {
            throw new IllegalStateException(
                    "REAL_PLACEMENT_REJECTED_"
                            + itemId
                            + "_result_"
                            + result);
        }

        Assembly assembly =
                findPlacedAssembly(
                        level,
                        source,
                        spec);

        addAssemblyToOwned(assembly, owned);

        return assembly;
    }

    private static void prepareSupports(
            ServerLevel level,
            CourseEnd source,
            LegacyBatchTrackSpec target,
            Set<BlockPos> owned) {

        Set<BlockPos> requiredCells = new HashSet<>();

        BlockPos[] centers = new BlockPos[]{
                source.logical(),
                source.logical().relative(source.outward()),
                source.physical(),
                source.physical().relative(source.outward())
        };

        for (BlockPos center : centers) {
            for (int dx = -SUPPORT_PROBE_RADIUS;
                 dx <= SUPPORT_PROBE_RADIUS;
                 dx++) {
                for (int dz = -SUPPORT_PROBE_RADIUS;
                     dz <= SUPPORT_PROBE_RADIUS;
                     dz++) {
                    requiredCells.add(
                            center.offset(dx, 0, dz));
                }
            }
        }

        Set<BlockPos> supports = new HashSet<>();

        for (BlockPos required : requiredCells) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (LegacyBatchTrackSpec.Endpoint endpoint
                        : target.endpoints()) {

                    BlockPos zeroEndpoint =
                            target.endpointPosition(
                                    BlockPos.ZERO,
                                    facing,
                                    endpoint);

                    BlockPos root =
                            required.subtract(zeroEndpoint);

                    for (int part = 0;
                         part < target.partCount();
                         part++) {

                        supports.add(
                                target.supportForPart(
                                        root,
                                        facing,
                                        part));
                    }
                }
            }
        }

        for (BlockPos support : supports) {
            ensureSupport(level, support, owned);
        }
    }

    private static void ensureSupport(
            ServerLevel level,
            BlockPos support,
            Set<BlockPos> owned) {

        BlockState state =
                level.getBlockState(support);

        if (state.isFaceSturdy(
                level,
                support,
                Direction.UP)) {
            return;
        }

        if (state.isAir() || state.canBeReplaced()) {
            level.setBlock(
                    support,
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    2);
            owned.add(support);
        }
    }

    private static void setFakePlayerFor(
            FakePlayer fake,
            CourseEnd source) {

        Direction back =
                source.outward().getOpposite();

        BlockPos base =
                source.physical().relative(back, 3);

        fake.setPos(
                base.getX() + 0.5D,
                base.getY() + 2.0D,
                base.getZ() + 0.5D);

        fake.setYRot(
                yawFor(source.outward()));
        fake.setXRot(20.0F);
        fake.setShiftKeyDown(false);
    }

    private static float yawFor(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 180.0F;
        };
    }

    private static Assembly findPlacedAssembly(
            ServerLevel level,
            CourseEnd source,
            LegacyBatchTrackSpec target) {

        Map<String, Assembly> candidates =
                new LinkedHashMap<>();

        BlockPos center = source.logical();

        for (int dx = -SEARCH_RADIUS;
             dx <= SEARCH_RADIUS;
             dx++) {
            for (int dy = -3;
                 dy <= 3;
                 dy++) {
                for (int dz = -SEARCH_RADIUS;
                     dz <= SEARCH_RADIUS;
                     dz++) {

                    BlockPos pos =
                            center.offset(dx, dy, dz);

                    BlockState state =
                            level.getBlockState(pos);

                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    if (!target.id().equals(
                            batch.getSpec().id())) {
                        continue;
                    }

                    BlockPos root =
                            batch.getAssemblyRoot(pos, state);

                    Direction facing =
                            batch.getAssemblyFacing(state);

                    String key =
                            root.asLong()
                                    + "|"
                                    + facing.ordinal();

                    candidates.putIfAbsent(
                            key,
                            new Assembly(
                                    target,
                                    batch,
                                    root,
                                    facing));
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "PLACED_ASSEMBLY_NOT_FOUND_"
                            + target.id());
        }

        List<Assembly> ordered =
                new ArrayList<>(candidates.values());

        ordered.sort(
                Comparator.comparingLong(
                        assembly ->
                                minEndpointDistanceSq(
                                        assembly,
                                        source.logical())));

        Assembly best = ordered.get(0);

        if (ordered.size() > 1) {
            long first =
                    minEndpointDistanceSq(
                            ordered.get(0),
                            source.logical());

            long second =
                    minEndpointDistanceSq(
                            ordered.get(1),
                            source.logical());

            if (first == second) {
                throw new IllegalStateException(
                        "AMBIGUOUS_PLACED_ASSEMBLY_"
                                + target.id());
            }
        }

        return best;
    }

    private static long minEndpointDistanceSq(
            Assembly assembly,
            BlockPos source) {

        long best = Long.MAX_VALUE;

        for (LegacyBatchTrackSpec.Endpoint endpoint
                : assembly.spec().endpoints()) {

            BlockPos logical =
                    assembly.spec().endpointPosition(
                            assembly.root(),
                            assembly.facing(),
                            endpoint);

            best = Math.min(
                    best,
                    distanceSq(logical, source));
        }

        return best;
    }

    private static int findAttachedEndpointIndex(
            Assembly assembly,
            CourseEnd source) {

        List<LegacyBatchTrackSpec.Endpoint> endpoints =
                assembly.spec().endpoints();

        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        int bestDirectionPenalty = Integer.MAX_VALUE;

        for (int i = 0; i < endpoints.size(); i++) {
            LegacyBatchTrackSpec.Endpoint endpoint =
                    endpoints.get(i);

            BlockPos logical =
                    assembly.spec().endpointPosition(
                            assembly.root(),
                            assembly.facing(),
                            endpoint);

            long distance =
                    distanceSq(
                            logical,
                            source.logical());

            Direction worldOutward =
                    assembly.spec().rotateDirection(
                            assembly.facing(),
                            endpoint.outward());

            int directionPenalty =
                    worldOutward
                            == source.outward().getOpposite()
                            ? 0
                            : 1;

            if (distance < bestDistance
                    || (distance == bestDistance
                    && directionPenalty
                    < bestDirectionPenalty)) {

                bestDistance = distance;
                bestDirectionPenalty = directionPenalty;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) {
            throw new IllegalStateException(
                    "NO_ATTACHED_ENDPOINT_"
                            + assembly.spec().id());
        }

        return bestIndex;
    }

    private static CourseEnd endFor(
            Assembly assembly,
            LegacyBatchTrackSpec.Endpoint endpoint) {

        BlockPos physical =
                assembly.spec().offsetForPart(
                        assembly.root(),
                        assembly.facing(),
                        endpoint.part());

        BlockPos logical =
                assembly.spec().endpointPosition(
                        assembly.root(),
                        assembly.facing(),
                        endpoint);

        Direction outward =
                assembly.spec().rotateDirection(
                        assembly.facing(),
                        endpoint.outward());

        return new CourseEnd(
                physical,
                logical,
                outward,
                assembly.spec().id());
    }

    private static int validateAssembly(
            ServerLevel level,
            Assembly assembly) {

        int valid = 0;

        for (int part = 0;
             part < assembly.spec().partCount();
             part++) {

            BlockPos pos =
                    assembly.spec().offsetForPart(
                            assembly.root(),
                            assembly.facing(),
                            part);

            BlockState state =
                    level.getBlockState(pos);

            if (!(state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch)) {
                continue;
            }

            if (!assembly.spec().id().equals(
                    batch.getSpec().id())) {
                continue;
            }

            BlockPos actualRoot =
                    batch.getAssemblyRoot(pos, state);

            Direction actualFacing =
                    batch.getAssemblyFacing(state);

            if (actualRoot.equals(assembly.root())
                    && actualFacing == assembly.facing()) {
                valid++;
            }
        }

        return valid;
    }

    private static void setSwitchActive(
            ServerLevel level,
            Assembly assembly,
            boolean active) {

        int found = 0;

        for (int part = 0;
             part < assembly.spec().partCount();
             part++) {

            BlockPos pos =
                    assembly.spec().offsetForPart(
                            assembly.root(),
                            assembly.facing(),
                            part);

            BlockState state =
                    level.getBlockState(pos);

            BooleanProperty activeProperty = null;

            for (Property<?> property
                    : state.getProperties()) {

                if ("active".equals(property.getName())
                        && property
                        instanceof BooleanProperty booleanProperty) {

                    activeProperty =
                            booleanProperty;
                    break;
                }
            }

            if (activeProperty == null) {
                continue;
            }

            found++;

            BlockState updated =
                    state.setValue(
                            activeProperty,
                            active);

            level.setBlock(
                    pos,
                    updated,
                    2);
        }

        if (found != assembly.spec().partCount()) {
            throw new IllegalStateException(
                    "SWITCH_ACTIVE_PROPERTY_COUNT_"
                            + found
                            + "_OF_"
                            + assembly.spec().partCount()
                            + "_"
                            + assembly.spec().id());
        }
    }

    private static void addAssemblyToOwned(
            Assembly assembly,
            Set<BlockPos> owned) {

        for (int part = 0;
             part < assembly.spec().partCount();
             part++) {

            owned.add(
                    assembly.spec().offsetForPart(
                            assembly.root(),
                            assembly.facing(),
                            part));
        }
    }

    private static boolean onAssembly(
            AbstractMinecart probe,
            Assembly assembly) {

        BlockPos base = probe.blockPosition();

        for (BlockPos pos
                : List.of(
                        base,
                        base.below(),
                        base.above())) {

            BlockState state =
                    probe.level().getBlockState(pos);

            if (state.getBlock() != assembly.block()) {
                continue;
            }

            BlockPos root =
                    assembly.block()
                            .getAssemblyRoot(pos, state);

            if (assembly.root().equals(root)) {
                return true;
            }
        }

        return false;
    }

    private static String ownerAt(
            AbstractMinecart probe) {

        BlockPos base = probe.blockPosition();

        for (BlockPos pos
                : List.of(
                        base,
                        base.below(),
                        base.above())) {

            BlockState state =
                    probe.level().getBlockState(pos);

            Block block = state.getBlock();

            if (block == Blocks.RAIL) {
                return "minecraft:rail";
            }

            if (block
                    instanceof AbstractLegacyBatchRailBlock batch) {

                BlockPos root =
                        batch.getAssemblyRoot(pos, state);

                Direction facing =
                        batch.getAssemblyFacing(state);

                return batch.getSpec().id()
                        + "@"
                        + root
                        + "/"
                        + facing.getName();
            }
        }

        return "NONE";
    }

    private static LegacyBatchTrackSpec findSpec(
            String id) {

        try {
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

                if (spec != null
                        && id.equals(spec.id())) {
                    return spec;
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "SPEC_REFLECTION_FAILED_" + id,
                    e);
        }

        throw new IllegalStateException(
                "SPEC_NOT_FOUND_" + id);
    }

    // STEP_9_3G_T7_RP1_R2_DETERMINISTIC_GAMETIME
    private static void advanceDeterministicGameTime(ServerLevel level) {
        long next = level.getGameTime() + 1L;
        Object data = level.getLevelData();

        try {
            java.lang.reflect.Method method =
                    data.getClass().getMethod("setGameTime", long.class);
            method.invoke(data, next);
        } catch (ReflectiveOperationException publicLookupFailure) {
            try {
                java.lang.reflect.Method method =
                        data.getClass().getDeclaredMethod(
                                "setGameTime", long.class);
                method.setAccessible(true);
                method.invoke(data, next);
            } catch (ReflectiveOperationException declaredLookupFailure) {
                throw new IllegalStateException(
                        "RP1_R2_CANNOT_ADVANCE_GAME_TIME type="
                                + data.getClass().getName()
                                + " current="
                                + level.getGameTime()
                                + " requested="
                                + next,
                        declaredLookupFailure);
            }
        }

        if (level.getGameTime() != next) {
            throw new IllegalStateException(
                    "RP1_R2_GAME_TIME_DID_NOT_ADVANCE current="
                            + level.getGameTime()
                            + " expected="
                            + next);
        }
    }

    private static void clearOwned(
            ServerLevel level,
            Set<BlockPos> owned) {

        List<BlockPos> reverse =
                new ArrayList<>(owned);

        reverse.sort(
                Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .reversed());

        for (BlockPos pos : reverse) {
            level.setBlock(
                    pos,
                    Blocks.AIR.defaultBlockState(),
                    2);
        }
    }

    private static long distanceSq(
            BlockPos a,
            BlockPos b) {

        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();

        return dx * dx + dy * dy + dz * dz;
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

    private static double horizontalSpeed(
            Vec3 motion) {

        return Math.sqrt(
                motion.x * motion.x
                        + motion.z * motion.z);
    }

    private static String vec(Vec3 v) {
        return String.format(
                Locale.ROOT,
                "(%.6f,%.6f,%.6f)",
                v.x,
                v.y,
                v.z);
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "NULL";
        }

        return text.replaceAll(
                "[^A-Za-z0-9_\\-@./]",
                "_");
    }

    private static void writeHarnessFailure(
            Throwable t) throws Exception {

        List<String> lines = List.of(
                "TAFARICRAFT - TRAINS STEP 9.3g-t7-rp1-r3",
                "FOCUSED REAL-PLACEMENT SEAM GATE",
                "HARNESS_EXCEPTION=" + t.getClass().getName(),
                "HARNESS_MESSAGE=" + String.valueOf(t.getMessage()),
                "RESULT=T7_RP1_REAL_PLACEMENT_SEAMS_HARNESS_FAILURE"
        );

        Path output = Path.of(
                "logs/traincraft-track-debug/t7-rp1-r3-real-placement-seams-"
                        + STAMP.format(LocalDateTime.now())
                        + ".txt")
                .toAbsolutePath();

        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
    }
}
