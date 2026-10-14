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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;

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
import java.util.UUID;

/**
 * Step 9.3g-t7-c2-r3 — real-snap-aware continuous consist test course.
 *
 * Diagnostic only. It builds ONE connected driveable course through the
 * representative route families required for c1 real-consist validation.
 * Every Traincraft piece is placed through its real registered item/useOn()
 * path. The command supplies support blocks only; it does not bypass placement.
 *
 * Command:
 *   /tc_t7_full_test_track
 *
 * Course:
 *   vanilla starter
 *   -> Standard Medium curved branch (LEFT p0->p10; reverse p10->p0)
 *   -> quarter curve
 *   -> 45 Medium switch (branch/active)
 *   -> Medium diagonal
 *   -> Diamond crossing
 *   -> Medium diagonal
 *   -> original Diagonal crossing
 *   -> Medium diagonal
 *   -> standalone 45 curve
 *   -> Parallel switch (branch/active)
 *   -> Parallel/S curve
 *   -> Long straight slope
 *   -> vanilla runout
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7FullCourseCommand {
    private static final int STARTER_LENGTH = 24;
    private static final int RUNOUT_LENGTH = 18;
    private static final int COURSE_X_OFFSET = 48;
    private static final int COURSE_Z_OFFSET = 48;
    private static final int COURSE_Y_OFFSET = 20;
    private static final int SEARCH_RADIUS = 24;
    private static final int SUPPORT_PROBE_RADIUS = 2;

    private enum ExitMode {
        OTHER,
        EXACT_PART,
        ROUTE_OPPOSITE
    }

    private record CourseStep(
            String label,
            String id,
            LegacyBatchTrackSpec spec,
            ExitMode exitMode,
            int exitPart,
            Integer expectedAttachedPart,
            Boolean active) {
    }

    private record Assembly(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing) {
    }

    private record CourseEnd(
            BlockPos physical,
            BlockPos logical,
            Direction outward,
            String sourceId) {
    }

    private static final List<CourseStep> STEPS = List.of(
            // c2-r3: the real item snap from the vanilla starter approaches
            // Standard Medium LEFT at its proven branch endpoint p0. Do not
            // fight that real placement choice. Traverse the valid trailing
            // branch p0 -> common p10 with ACTIVE=true. On the reverse return,
            // ACTIVE=true selects the same common p10 -> branch p0 route, so
            // this single piece exercises the full curved Standard Medium
            // branch in BOTH directions without manufacturing a fake p10 entry.
            new CourseStep(
                    "Standard Medium LEFT - real snap p0, curved branch to common p10",
                    "track_switch_medium_left",
                    LegacyBatchTrackSpecs.SWITCH_MEDIUM_LEFT,
                    ExitMode.EXACT_PART,
                    10,
                    0,
                    Boolean.TRUE),
            new CourseStep(
                    "Quarter curve",
                    "track_curve_big",
                    LegacyBatchTrackSpecs.CURVE_BIG,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "45 Medium switch - common p0 to diverging p4",
                    "track_switch_45_medium_right",
                    LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_RIGHT,
                    ExitMode.EXACT_PART,
                    4,
                    0,
                    Boolean.TRUE),
            new CourseStep(
                    "Medium diagonal after 45 switch",
                    "track_diagonal_straight_medium",
                    LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Diamond crossing - stay on incoming diagonal route",
                    "track_diamond_crossing",
                    LegacyBatchTrackSpecs.DIAMOND_CROSSING,
                    ExitMode.ROUTE_OPPOSITE,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Medium diagonal between crossings",
                    "track_diagonal_straight_medium",
                    LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Original centered Diagonal crossing - stay on incoming route",
                    "track_diagonal_crossing",
                    LegacyBatchTrackSpecs.DIAGONAL_CROSSING,
                    ExitMode.ROUTE_OPPOSITE,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Medium diagonal leaving crossing",
                    "track_diagonal_straight_medium",
                    LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Standalone 45 curve back to cardinal",
                    "track_curve_45_medium_left",
                    LegacyBatchTrackSpecs.CURVE_45_MEDIUM_LEFT,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Parallel switch - common p0 to diverging far branch",
                    "track_switch_parallel_right",
                    LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT,
                    ExitMode.EXACT_PART,
                    17,
                    0,
                    Boolean.TRUE),
            new CourseStep(
                    "Parallel/S curve",
                    "track_parallel_curve_medium_left",
                    LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null),
            new CourseStep(
                    "Long straight slope",
                    "track_slope_long",
                    LegacyBatchTrackSpecs.SLOPE_LONG,
                    ExitMode.OTHER,
                    -1,
                    null,
                    null)
    );

    private TrackT7FullCourseCommand() {
    }

    @SubscribeEvent
    // TC_DEV38_R1G1Z3C_T7_REENABLED - TEMP WHEEL TEST ONLY
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_full_test_track")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> buildCourse(
                                context.getSource().getPlayerOrException()))
        );
    }

    private static int buildCourse(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 16;
        int railY = Math.max(
                minY,
                Math.min(maxY, player.getBlockY() + COURSE_Y_OFFSET));

        BlockPos starterSouth = new BlockPos(
                player.getBlockX() + COURSE_X_OFFSET,
                railY,
                player.getBlockZ() + COURSE_Z_OFFSET);

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-c2-r3");
        report.add("ONE CONTINUOUS ROUTE-AWARE REAL-PLACEMENT CONSIST TEST COURSE");
        report.add("PROJECT_SOURCE_MUTATION=DIAGNOSTIC_ONLY");
        report.add("TRAINCRAFT_PLACEMENT_MODE=REAL_REGISTERED_ITEMS_USE_ON");
        report.add("COURSE_STEP_COUNT=" + STEPS.size());
        report.add("STARTER_LENGTH=" + STARTER_LENGTH);
        report.add("RUNOUT_LENGTH=" + RUNOUT_LENGTH);
        report.add("STARTER_SOUTH=" + starterSouth);
        report.add("");

        try {
            CourseEnd current = placeStarter(level, starterSouth, report);
            FakePlayer fake = FakePlayerFactory.getMinecraft(level);
            fake.setShiftKeyDown(false);

            int placed = 0;

            for (int i = 0; i < STEPS.size(); i++) {
                CourseStep step = STEPS.get(i);
                prepareSupports(level, current, step.spec());

                Item item = ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation("traincraft", step.id()));
                if (item == null || item == Items.AIR) {
                    throw new IllegalStateException(
                            "Missing registered item for " + step.id());
                }

                setFakePlayerFor(fake, current);
                ItemStack stack = new ItemStack(item);
                fake.setItemInHand(InteractionHand.MAIN_HAND, stack);

                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(current.physical()),
                        Direction.UP,
                        current.physical(),
                        false);

                InteractionResult result = item.useOn(
                        new UseOnContext(
                                fake,
                                InteractionHand.MAIN_HAND,
                                hit));

                fake.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        ItemStack.EMPTY);

                if (!result.consumesAction()) {
                    throw new IllegalStateException(
                            "Placement did not consume action for "
                                    + step.id()
                                    + " result="
                                    + result);
                }

                Assembly assembly = findPlacedAssembly(
                        level,
                        current,
                        step.spec());

                int validParts = validateAssembly(level, assembly);
                if (validParts != step.spec().partCount()) {
                    throw new IllegalStateException(
                            step.id()
                                    + " valid parts "
                                    + validParts
                                    + "/"
                                    + step.spec().partCount());
                }

                int attachedIndex = findAttachedEndpointIndex(
                        assembly,
                        current);
                LegacyBatchTrackSpec.Endpoint attached =
                        step.spec().endpoints().get(attachedIndex);

                if (step.expectedAttachedPart() != null
                        && attached.part() != step.expectedAttachedPart()) {
                    throw new IllegalStateException(
                            step.id()
                                    + " attached at p"
                                    + attached.part()
                                    + " but course requires p"
                                    + step.expectedAttachedPart());
                }

                if (step.active() != null) {
                    setSwitchActive(
                            level,
                            assembly,
                            step.active());
                }

                int exitIndex = chooseExitEndpointIndex(
                        assembly,
                        attachedIndex,
                        step.exitMode(),
                        step.exitPart());

                LegacyBatchTrackSpec.Endpoint exit =
                        step.spec().endpoints().get(exitIndex);

                CourseEnd next = endFor(
                        assembly,
                        exit);

                report.add(String.format(
                        Locale.ROOT,
                        "STEP index=%02d label=%s id=%s root=%s facing=%s"
                                + " validParts=%d attachedPart=%d exitPart=%d"
                                + " active=%s result=%s"
                                + " sourcePhysical=%s sourceLogical=%s"
                                + " exitPhysical=%s exitLogical=%s exitOutward=%s",
                        i,
                        step.label(),
                        step.id(),
                        assembly.root(),
                        assembly.facing(),
                        validParts,
                        attached.part(),
                        exit.part(),
                        String.valueOf(step.active()),
                        result,
                        current.physical(),
                        current.logical(),
                        next.physical(),
                        next.logical(),
                        next.outward()));

                current = next;
                placed++;
            }

            BlockPos runoutEnd = placeRunout(
                    level,
                    current,
                    report);

            report.add("");
            report.add("PLACED_STEPS=" + placed);
            report.add("EXPECTED_STEPS=" + STEPS.size());
            report.add("COURSE_START=" + starterSouth);
            report.add("COURSE_END=" + runoutEnd);
            report.add("RESULT="
                    + (placed == STEPS.size()
                    ? "T7_C2_R3_FULL_TEST_TRACK_READY"
                    : "T7_C2_R3_REVIEW_REQUIRED"));

            Path reportFile = writeReport(report);

            player.teleportTo(
                    level,
                    starterSouth.getX() + 0.5D,
                    starterSouth.getY() + 3.0D,
                    starterSouth.getZ() + 2.5D,
                    180.0F,
                    20.0F);

            player.sendSystemMessage(Component.literal(
                    "TC full consist test track placed: "
                            + placed
                            + "/"
                            + STEPS.size()
                            + " Traincraft sections."));
            player.sendSystemMessage(Component.literal(
                    "Start is the 24-block vanilla straight beside you."));
            player.sendSystemMessage(Component.literal(
                    "Place/couple your locomotive + tender at the start, then run "
                            + "/tc_t7_consist_trace before driving."));
            player.sendSystemMessage(Component.literal(
                    "Course report: "
                            + reportFile.toAbsolutePath()));

            return placed == STEPS.size()
                    ? Command.SINGLE_SUCCESS
                    : 0;
        } catch (Exception ex) {
            report.add("");
            report.add("RESULT=T7_C2_R3_BUILD_FAILED");
            report.add("ERROR=" + ex.getClass().getName() + ": " + ex.getMessage());

            Path reportFile = writeReport(report);
            player.sendSystemMessage(Component.literal(
                    "TC full test track build stopped: "
                            + ex.getMessage()));
            player.sendSystemMessage(Component.literal(
                    "Failure report: "
                            + reportFile.toAbsolutePath()));
            return 0;
        }
    }

    private static CourseEnd placeStarter(
            ServerLevel level,
            BlockPos south,
            List<String> report) {
        BlockState railState = Blocks.RAIL
                .defaultBlockState()
                .setValue(
                        RailBlock.SHAPE,
                        RailShape.NORTH_SOUTH);

        for (int i = 0; i < STARTER_LENGTH; i++) {
            BlockPos rail = south.north(i);
            BlockPos support = rail.below();

            BlockState existing = level.getBlockState(rail);
            if (!existing.isAir()) {
                throw new IllegalStateException(
                        "Starter rail cell is not empty at " + rail
                                + " state=" + existing);
            }

            ensureSupport(level, support);
            level.setBlock(rail, railState, 2);
        }

        BlockPos northEnd = south.north(STARTER_LENGTH - 1);
        report.add("STARTER northEnd=" + northEnd + " outward=NORTH");

        return new CourseEnd(
                northEnd,
                northEnd,
                Direction.NORTH,
                "minecraft:rail");
    }

    private static void prepareSupports(
            ServerLevel level,
            CourseEnd source,
            LegacyBatchTrackSpec target) {
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
                    requiredCells.add(center.offset(dx, 0, dz));
                }
            }
        }

        Set<BlockPos> supports = new HashSet<>();

        for (BlockPos required : requiredCells) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (LegacyBatchTrackSpec.Endpoint endpoint
                        : target.endpoints()) {
                    BlockPos zeroEndpoint = target.endpointPosition(
                            BlockPos.ZERO,
                            facing,
                            endpoint);
                    BlockPos root = required.subtract(zeroEndpoint);

                    for (int part = 0;
                         part < target.partCount();
                         part++) {
                        supports.add(target.supportForPart(
                                root,
                                facing,
                                part));
                    }
                }
            }
        }

        for (BlockPos support : supports) {
            ensureSupport(level, support);
        }
    }

    private static void ensureSupport(
            ServerLevel level,
            BlockPos support) {
        BlockState state = level.getBlockState(support);

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
            return;
        }

        // Never overwrite an existing build merely to force the diagnostic.
        // The real placement path will reject the candidate if this cell is
        // actually required.
    }

    private static void setFakePlayerFor(
            FakePlayer fake,
            CourseEnd source) {
        Direction back = source.outward().getOpposite();
        BlockPos base = source.physical()
                .relative(back, 3);

        fake.setPos(
                base.getX() + 0.5D,
                base.getY() + 2.0D,
                base.getZ() + 0.5D);
        fake.setYRot(yawFor(source.outward()));
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
        Map<String, Assembly> candidates = new LinkedHashMap<>();

        BlockPos center = source.logical();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);

                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    if (!target.id().equals(batch.getSpec().id())) {
                        continue;
                    }

                    BlockPos root = batch.getAssemblyRoot(
                            pos,
                            state);
                    Direction facing = batch.getAssemblyFacing(state);
                    String key = root.asLong()
                            + "|"
                            + facing.ordinal();

                    candidates.putIfAbsent(
                            key,
                            new Assembly(
                                    target,
                                    root,
                                    facing));
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "Could not locate newly placed assembly "
                            + target.id()
                            + " near "
                            + source.logical());
        }

        List<Assembly> ordered = new ArrayList<>(
                candidates.values());

        ordered.sort(Comparator.comparingLong(
                assembly -> minEndpointDistanceSq(
                        assembly,
                        source.logical())));

        Assembly best = ordered.get(0);

        if (ordered.size() > 1) {
            long first = minEndpointDistanceSq(
                    ordered.get(0),
                    source.logical());
            long second = minEndpointDistanceSq(
                    ordered.get(1),
                    source.logical());

            if (first == second) {
                throw new IllegalStateException(
                        "Ambiguous target assembly lookup for "
                                + target.id()
                                + " near "
                                + source.logical());
            }
        }

        return best;
    }

    private static int validateAssembly(
            ServerLevel level,
            Assembly assembly) {
        int valid = 0;

        for (int part = 0;
             part < assembly.spec().partCount();
             part++) {
            BlockPos pos = assembly.spec().offsetForPart(
                    assembly.root(),
                    assembly.facing(),
                    part);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch)) {
                continue;
            }

            if (!assembly.spec().id().equals(
                    batch.getSpec().id())) {
                continue;
            }

            BlockPos actualRoot = batch.getAssemblyRoot(
                    pos,
                    state);
            Direction actualFacing =
                    batch.getAssemblyFacing(state);

            if (actualRoot.equals(assembly.root())
                    && actualFacing == assembly.facing()) {
                valid++;
            }
        }

        return valid;
    }

    private static long minEndpointDistanceSq(
            Assembly assembly,
            BlockPos source) {
        long best = Long.MAX_VALUE;

        for (LegacyBatchTrackSpec.Endpoint endpoint
                : assembly.spec().endpoints()) {
            BlockPos logical = assembly.spec().endpointPosition(
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
            BlockPos logical = assembly.spec().endpointPosition(
                    assembly.root(),
                    assembly.facing(),
                    endpoint);
            long distance = distanceSq(
                    logical,
                    source.logical());

            Direction worldOutward =
                    assembly.spec().rotateDirection(
                            assembly.facing(),
                            endpoint.outward());
            int directionPenalty =
                    worldOutward == source.outward().getOpposite()
                            ? 0
                            : 1;

            if (distance < bestDistance
                    || (distance == bestDistance
                    && directionPenalty < bestDirectionPenalty)) {
                bestDistance = distance;
                bestDirectionPenalty = directionPenalty;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) {
            throw new IllegalStateException(
                    "No attached endpoint found for "
                            + assembly.spec().id());
        }

        return bestIndex;
    }

    private static int chooseExitEndpointIndex(
            Assembly assembly,
            int attachedIndex,
            ExitMode mode,
            int exactPart) {
        List<LegacyBatchTrackSpec.Endpoint> endpoints =
                assembly.spec().endpoints();

        if (endpoints.size() < 2) {
            throw new IllegalStateException(
                    assembly.spec().id()
                            + " has fewer than two endpoints.");
        }

        if (mode == ExitMode.OTHER) {
            if (endpoints.size() != 2) {
                throw new IllegalStateException(
                        assembly.spec().id()
                                + " OTHER exit expected two endpoints, found "
                                + endpoints.size());
            }
            return attachedIndex == 0 ? 1 : 0;
        }

        if (mode == ExitMode.EXACT_PART) {
            for (int i = 0; i < endpoints.size(); i++) {
                if (i == attachedIndex) {
                    continue;
                }
                if (endpoints.get(i).part() == exactPart) {
                    return i;
                }
            }
            throw new IllegalStateException(
                    assembly.spec().id()
                            + " has no non-attached endpoint p"
                            + exactPart);
        }

        if (mode == ExitMode.ROUTE_OPPOSITE) {
            LegacyBatchTrackSpec.Endpoint attached =
                    endpoints.get(attachedIndex);
            Direction attachedOutward =
                    assembly.spec().rotateDirection(
                            assembly.facing(),
                            attached.outward());
            Direction requiredOutward =
                    attachedOutward.getOpposite();

            int best = -1;
            long bestDistance = Long.MIN_VALUE;
            BlockPos attachedLogical =
                    assembly.spec().endpointPosition(
                            assembly.root(),
                            assembly.facing(),
                            attached);

            for (int i = 0; i < endpoints.size(); i++) {
                if (i == attachedIndex) {
                    continue;
                }

                LegacyBatchTrackSpec.Endpoint candidate =
                        endpoints.get(i);
                Direction candidateOutward =
                        assembly.spec().rotateDirection(
                                assembly.facing(),
                                candidate.outward());
                if (candidateOutward != requiredOutward) {
                    continue;
                }

                BlockPos logical =
                        assembly.spec().endpointPosition(
                                assembly.root(),
                                assembly.facing(),
                                candidate);
                long distance =
                        distanceSq(logical, attachedLogical);
                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }

            if (best >= 0) {
                return best;
            }

            throw new IllegalStateException(
                    assembly.spec().id()
                            + " has no route-opposite endpoint for attached p"
                            + attached.part()
                            + " outward="
                            + attachedOutward);
        }

        throw new IllegalStateException(
                "Unhandled exit mode "
                        + mode);
    }

    private static int farthestExit(
            Assembly assembly,
            int attachedIndex) {
        List<LegacyBatchTrackSpec.Endpoint> endpoints =
                assembly.spec().endpoints();

        BlockPos attached =
                assembly.spec().endpointPosition(
                        assembly.root(),
                        assembly.facing(),
                        endpoints.get(attachedIndex));

        int best = -1;
        long bestDistance = Long.MIN_VALUE;

        for (int i = 0; i < endpoints.size(); i++) {
            if (i == attachedIndex) {
                continue;
            }

            BlockPos logical =
                    assembly.spec().endpointPosition(
                            assembly.root(),
                            assembly.facing(),
                            endpoints.get(i));
            long distance = distanceSq(
                    logical,
                    attached);

            if (distance > bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        if (best < 0) {
            throw new IllegalStateException(
                    "No far exit for "
                            + assembly.spec().id());
        }

        return best;
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

    private static void setSwitchActive(
            ServerLevel level,
            Assembly assembly,
            boolean active) {
        int found = 0;

        for (int part = 0;
             part < assembly.spec().partCount();
             part++) {
            BlockPos pos = assembly.spec().offsetForPart(
                    assembly.root(),
                    assembly.facing(),
                    part);
            BlockState state = level.getBlockState(pos);

            BooleanProperty activeProperty = null;

            for (Property<?> property : state.getProperties()) {
                if ("active".equals(property.getName())
                        && property instanceof BooleanProperty booleanProperty) {
                    activeProperty = booleanProperty;
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

            if (updated != state) {
                level.setBlock(
                        pos,
                        updated,
                        2);
            }
        }

        if (found != assembly.spec().partCount()) {
            throw new IllegalStateException(
                    "Switch active property found on "
                            + found
                            + "/"
                            + assembly.spec().partCount()
                            + " parts for "
                            + assembly.spec().id());
        }
    }

    private static BlockPos placeRunout(
            ServerLevel level,
            CourseEnd source,
            List<String> report) {
        RailShape shape =
                source.outward().getAxis()
                        == Direction.Axis.Z
                        ? RailShape.NORTH_SOUTH
                        : RailShape.EAST_WEST;

        BlockState railState = Blocks.RAIL
                .defaultBlockState()
                .setValue(
                        RailBlock.SHAPE,
                        shape);

        BlockPos end = source.logical();

        for (int i = 1; i <= RUNOUT_LENGTH; i++) {
            BlockPos rail = source.logical()
                    .relative(
                            source.outward(),
                            i);
            BlockPos support = rail.below();

            BlockState existing =
                    level.getBlockState(rail);
            if (!existing.isAir()) {
                throw new IllegalStateException(
                        "Runout rail cell is not empty at "
                                + rail
                                + " state="
                                + existing);
            }

            ensureSupport(level, support);
            level.setBlock(
                    rail,
                    railState,
                    2);
            end = rail;
        }

        report.add("RUNOUT start="
                + source.logical().relative(source.outward())
                + " end="
                + end
                + " outward="
                + source.outward());

        return end;
    }

    private static long distanceSq(
            BlockPos a,
            BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Path writeReport(
            List<String> report) {
        try {
            Path dir = Path.of(
                    "logs",
                    "traincraft-track-debug");
            Files.createDirectories(dir);

            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern(
                            "yyyyMMdd-HHmmss-SSS"));
            Path file = dir.resolve(
                    "t7-full-test-track-"
                            + stamp
                            + "-"
                            + UUID.randomUUID()
                                    .toString()
                                    .substring(0, 8)
                            + ".txt");

            Files.write(
                    file,
                    report,
                    StandardCharsets.UTF_8);
            return file;
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Could not write full-course report",
                    ex);
        }
    }
}
