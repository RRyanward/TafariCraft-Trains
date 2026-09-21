package traincraft.item.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.block.track.LegacyStraightAssemblyRailBlock;
import traincraft.debug.BatchTrackDiagnostics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One-click placement + endpoint snapping shared by the Step 9.3a batched
 * dedicated track pieces.
 *
 * Step 9.3d-t8/t9 keeps the runtime-proven TC4.5 Medium-45 <-> diagonal
 * shared-gag handoff. Step 9.3e-t2-r3 restores the Diagonal Crossing itself to
 * the original centered owner topology: one real visual/owner rail at part 0,
 * eight linked proxy rails around it, and four physical corner endpoints.
 * Crossing placement no longer depends on displaced virtual endpoints, broad
 * ground-ownership halos, or crossing-specific Small-Diagonal render shifts.
 */
public final class LegacyBatchTrackItem extends Item {
    private static final double CONNECTOR_RADIUS = 0.90D;
    private static final int CORNER_HANDOFF_PENALTY = 10;
    private static final String TARGET_45_MEDIUM_LEFT_SWITCH =
            "track_switch_45_medium_left";
    private static final String TARGET_45_MEDIUM_RIGHT_SWITCH =
            "track_switch_45_medium_right";

    // STEP_9_3E_T2_R3_OG_CENTERED_CROSSING_TOPOLOGY
    // The Diagonal Crossing now has a physical centered 3x3 TC4.5 topology, so
    // endpoint ownership comes from real crossing proxy cells. No virtual
    // endpoint displacement or broad support-ground ownership is required.
    private static final String TARGET_DIAGONAL_CROSSING =
            "track_diagonal_crossing";

    // STEP_9_3D_T9_MEDIUM45_DIAGONAL_FAMILY_OWNERSHIP
    // STEP_9_3D_T9_R1_ORIGINAL_DIAGONAL_FACING_AND_CORNER_SNAP
    // t8v3 is runtime-confirmed for Medium Left 45 -> Small Diagonal.
    // t9 mirrors that exact TC4.5 8-way tangent/shared-gag ownership to the
    // Medium Right 45 and extends visual ownership to Medium Diagonal.
    // Logical roots/path math stay at t8; only source-derived tangent metadata
    // and render ownership are generalized.

    // STEP_9_3D_T8_ORIGINAL_TRAINCRAFT_SHARED_GAG_PROXY_OWNERSHIP
    // Original Traincraft TC4.5 ItemTCRail gave diagonal pieces their own
    // four direction values (4-7). RouteHeading8 is the 1.20.1 equivalent:
    // connector compatibility can now preserve a real diagonal tangent while
    // the underlying guide cells remain on Minecraft's integer block grid.
    private enum RouteHeading8 {
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        EAST(1, 0),
        SOUTH_EAST(1, 1),
        SOUTH(0, 1),
        SOUTH_WEST(-1, 1),
        WEST(-1, 0),
        NORTH_WEST(-1, -1);

        private final int dx;
        private final int dz;

        RouteHeading8(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        private RouteHeading8 opposite() {
            return fromStep(-dx, -dz);
        }

        private boolean isDiagonal() {
            return dx != 0 && dz != 0;
        }

        private RouteHeading8 rotateClockwise() {
            return fromStep(-dz, dx);
        }

        private static RouteHeading8 fromDirection(Direction direction) {
            return switch (direction) {
                case NORTH -> NORTH;
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                default -> throw new IllegalArgumentException(
                        "Horizontal direction required: " + direction);
            };
        }

        private static RouteHeading8 fromStep(int dx, int dz) {
            int sx = Integer.compare(dx, 0);
            int sz = Integer.compare(dz, 0);
            for (RouteHeading8 heading : values()) {
                if (heading.dx == sx && heading.dz == sz) {
                    return heading;
                }
            }
            throw new IllegalArgumentException(
                    "Non-zero horizontal route step required: dx=" + dx + " dz=" + dz);
        }
    }

    private final LegacyBatchTrackSpec spec;
    private final Supplier<? extends AbstractLegacyBatchRailBlock> blockSupplier;

    private static final class Connector {
        private final BlockPos segment;
        private final Direction outward;
        private final RouteHeading8 routeHeading;
        private final double distanceSq;
        private final String sourceId;

        private Connector(BlockPos segment,
                          Direction outward,
                          RouteHeading8 routeHeading,
                          double distanceSq,
                          String sourceId) {
            this.segment = segment;
            this.outward = outward;
            this.routeHeading = routeHeading;
            this.distanceSq = distanceSq;
            this.sourceId = sourceId;
        }
    }

    private static final class Candidate {
        private final BlockPos root;
        private final Direction facing;
        private final int endpointPart;
        private final int penalty;

        private Candidate(BlockPos root, Direction facing,
                          int endpointPart, int penalty) {
            this.root = root;
            this.facing = facing;
            this.endpointPart = endpointPart;
            this.penalty = penalty;
        }
    }

    public LegacyBatchTrackItem(Properties properties,
                                LegacyBatchTrackSpec spec,
                                Supplier<? extends AbstractLegacyBatchRailBlock> blockSupplier) {
        super(properties);
        this.spec = spec;
        this.blockSupplier = blockSupplier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        Direction facing = player.getDirection();
        List<String> oneShot = BatchTrackDiagnostics.isPlacementDebugArmed(player)
                ? new ArrayList<>()
                : null;
        BlockPos debugCenter = context.getClickedPos();
        if (oneShot != null) {
            oneShot.add("TRAINCRAFT STEP 9.3e-t2-r3 OG CENTERED CROSSING TOPOLOGY TRACE");
            oneShot.add("spec=" + spec.id());
            oneShot.add("clickedPos=" + context.getClickedPos()
                    + " clickedFace=" + context.getClickedFace()
                    + " clickLocation=" + context.getClickLocation());
            oneShot.add("clickedState=" + level.getBlockState(context.getClickedPos()));
            oneShot.add("player=" + player.getName().getString()
                    + " pos=" + player.position()
                    + " direction=" + player.getDirection()
                    + " yaw=" + player.getYRot()
                    + " sneaking=" + player.isShiftKeyDown());
            oneShot.add("held=" + stack);
            oneShot.add("connectorRadius=" + CONNECTOR_RADIUS
                    + " cornerHandoffPenalty=" + CORNER_HANDOFF_PENALTY);
            oneShot.add("");
        }

        // Manual placement normally remains anchored to the physical first endpoint.
        // The restored TC4.5 Diagonal Crossing is the deliberate exception:
        // its part 0 is the centered visual/owner rail, so a free placement
        // anchors that owner directly above the clicked support block.
        LegacyBatchTrackSpec.Endpoint primaryEndpoint = spec.endpoints().get(0);
        BlockPos manualAnchor = context.getClickedPos().above();
        BlockPos root = TARGET_DIAGONAL_CROSSING.equals(spec.id())
                ? manualAnchor
                : manualAnchor.subtract(
                        spec.offsetForPart(BlockPos.ZERO, facing, primaryEndpoint.part()));
        if (oneShot != null) {
            oneShot.add("MANUAL_BASELINE primaryEndpointPart=" + primaryEndpoint.part()
                    + " manualAnchor=" + manualAnchor
                    + " initialFacing=" + facing
                    + " initialRoot=" + root);
            oneShot.add("");
        }

        BlockState clickedPlacementState =
                level.getBlockState(context.getClickedPos());
        boolean clickedOgSmallRail =
                "track_diagonal_straight_small".equals(spec.id())
                        && clickedPlacementState.getBlock()
                                instanceof AbstractLegacyBatchRailBlock clickedBatch
                        && "track_diagonal_straight_small".equals(
                                clickedBatch.getSpec().id());

        Candidate chosen = findOgSmallLogicalExtensionCandidate(
                level, context, player, stack, oneShot);

        // Step 9.3e-t5-r6: a click on an existing Small rail is an extension
        // request, never a free/manual placement request.  If neither route end
        // can accept another Small, fail cleanly instead of using player yaw and
        // trying to build one block above or sideways from the clicked rail.
        if (chosen == null && clickedOgSmallRail) {
            String failure = "OG_SMALL_EXTENSION_NO_OPEN_END";
            if (oneShot != null) {
                oneShot.add("OUTCOME=FAIL reason=" + failure
                        + " clickedPos=" + context.getClickedPos());
                BatchTrackDiagnostics.finishPlacementDebug(
                        player, debugCenter, oneShot);
            }
            writePlacementFailure(level, player, stack, root, facing, failure);
            return InteractionResult.FAIL;
        }

        Connector connector = chosen == null
                ? findConnector(level, context, oneShot)
                : null;

        // STEP_9_3G_T2_WHOLE_SWITCH_FAMILY_DIRECT_RAIL_SNAP
        //
        // Project-wide switch placement contract:
        //   - clicking an existing rail means SNAP, never free-yaw placement;
        //   - no valid/open connector means fail cleanly;
        //   - orientation comes from connector/tangent geometry, not player yaw.
        //
        // This now covers the whole classic turnout family while preserving the
        // already-accepted Small-switch p5-specific topology below.
        boolean directRailSnapRequest =
                isWholeSwitchFamilySnapSpec(spec.id())
                && BaseRailBlock.isRail(clickedPlacementState);
        if (chosen == null && directRailSnapRequest && connector == null) {
            String failure = "SWITCH_FAMILY_DIRECT_RAIL_SNAP_NO_OPEN_CONNECTOR";
            if (oneShot != null) {
                oneShot.add("OUTCOME=FAIL reason=" + failure
                        + " clickedPos=" + context.getClickedPos()
                        + " clickedState=" + clickedPlacementState);
                BatchTrackDiagnostics.finishPlacementDebug(
                        player, debugCenter, oneShot);
            }
            writePlacementFailure(level, player, stack, root, facing, failure);
            return InteractionResult.FAIL;
        }

        if (chosen != null) {
            root = chosen.root;
            facing = chosen.facing;
            if (oneShot != null) {
                oneShot.add("OG_SMALL_EXTENSION_SNAP root=" + root
                        + " facing=" + facing
                        + " endpointPart=" + chosen.endpointPart);
            }
        }

        if (oneShot != null) {
            oneShot.add("SELECTED_SOURCE_CONNECTOR " + formatConnector(connector));
        }

        if (connector != null) {
            List<Candidate> candidates = candidatesFor(
                    level, player, stack, connector, oneShot);

            if (candidates.isEmpty()) {
                String failure = "NO_SNAP_CANDIDATE connectorSegment=" + connector.segment
                        + " connectorOutward=" + connector.outward;
                if (oneShot != null) {
                    oneShot.add("OUTCOME=FAIL reason=" + failure);
                    BatchTrackDiagnostics.finishPlacementDebug(
                            player, debugCenter, oneShot);
                }
                writePlacementFailure(level, player, stack, root, facing, failure);
                return InteractionResult.FAIL;
            }

            candidates.sort(Comparator
                    .comparingInt((Candidate c) -> c.penalty)
                    .thenComparingInt(c -> c.endpointPart)
                    .thenComparingInt(c -> c.facing.get2DDataValue()));

            chosen = player.isShiftKeyDown() && candidates.size() > 1
                    ? candidates.get(1)
                    : candidates.get(0);

            root = chosen.root;
            facing = chosen.facing;
            if (oneShot != null) {
                oneShot.add("CHOSEN_CANDIDATE root=" + chosen.root
                        + " facing=" + chosen.facing
                        + " endpointPart=" + chosen.endpointPart
                        + " penalty=" + chosen.penalty
                        + " candidateCount=" + candidates.size()
                        + " shiftedSelection=" + player.isShiftKeyDown());
            }

            BlockPos requiredEndpointCell =
                    requiredEndpointCellFor(spec.id(), connector);
            BlockPos placedEndpointCell =
                    spec.endpointPositionForPart(root, facing, chosen.endpointPart);
            boolean exactLogical = placedEndpointCell.equals(requiredEndpointCell);
            if (oneShot != null) {
                oneShot.add("VIRTUAL_ENDPOINT_METADATA_CHECK requiredEndpointCell="
                        + requiredEndpointCell
                        + " placedLogicalEndpoint=" + placedEndpointCell
                        + " physicalEndpointPart="
                        + spec.offsetForPart(root, facing, chosen.endpointPart)
                        + " logicalMatch=" + exactLogical
                        + " sourceSegment=" + connector.segment
                        + " sourceOutward=" + connector.outward
                        + " sourceId=" + connector.sourceId);
            }
            if (!exactLogical) {
                String failure = "CONNECTOR_ALIGNMENT_FAILED expected="
                        + requiredEndpointCell
                        + " actualLogicalEndpoint=" + placedEndpointCell
                        + " physicalEndpointPart="
                        + spec.offsetForPart(root, facing, chosen.endpointPart)
                        + " sourceSegment=" + connector.segment
                        + " sourceOutward=" + connector.outward
                        + " endpointPart=" + chosen.endpointPart;
                if (oneShot != null) {
                    oneShot.add("OUTCOME=FAIL reason=" + failure);
                    BatchTrackDiagnostics.finishPlacementDebug(
                            player, debugCenter, oneShot);
                }
                writePlacementFailure(level, player, stack, root, facing, failure);
                return InteractionResult.FAIL;
            }
        }

        if (!canPlaceAssembly(level, player, stack, root, facing)) {
            String failure = connector == null
                    ? "MANUAL_PLACEMENT_REJECTED"
                    : "SNAPPED_PLACEMENT_REJECTED";
            if (oneShot != null) {
                oneShot.add("FINAL_PLACEMENT_CHECK root=" + root
                        + " facing=" + facing
                        + " placeable=false reason="
                        + placementRejectReason(level, player, stack, root, facing));
                oneShot.add("OUTCOME=FAIL reason=" + failure);
                BatchTrackDiagnostics.finishPlacementDebug(
                        player, debugCenter, oneShot);
            }
            writePlacementFailure(level, player, stack, root, facing, failure);
            return InteractionResult.FAIL;
        }

        if (oneShot != null) {
            oneShot.add("FINAL_PLACEMENT_CHECK root=" + root
                    + " facing=" + facing
                    + " placeable=true");
        }

        AbstractLegacyBatchRailBlock block = blockSupplier.get();
        boolean active = false;

        // Step 9.3d-t9: keep the runtime-proven t8 logical root exactly where
        // movement already works, but render diagonal straights from the real
        // TC4.5 TileTCRail/root cell. For the canonical Left45 corridor this is
        // Small part 2 ({2,4}) and Medium part 6 ({4,6}); Right45 is the X-mirror.
        boolean originalVisualRootOwnership =
                isOriginalDiagonalVisualOwnership(connector, chosen);
        // STEP_9_3D_T9_R1A_STATE_SPACE_HOTFIX
        // Keep the exact TC4.5 4..7 value for diagnostics only. Do NOT
        // store it as another BlockState property: the existing cardinal
        // assembly facing already maps 1:1 to TC4.5 4/5/6/7 for the
        // shared-gag visual root, and a 4-value property multiplied every
        // generic batch block state during model baking.
        int originalVisualFacing = originalVisualRootOwnership
                ? originalDiagonalVisualFacing(connector)
                : 7;
        if (oneShot != null && originalVisualRootOwnership) {
            int visualPart = originalDiagonalVisualPart();
            oneShot.add("ORIGINAL_TC45_DIAGONAL_VISUAL_ROOT logicalRoot="
                    + root
                    + " visualPart=" + visualPart
                    + " visualRoot="
                    + spec.offsetForPart(root, facing, visualPart)
                    + " facing=" + facing
                    + " originalTcFacing=" + originalVisualFacing
                    + " sourceRouteHeading=" + connector.routeHeading
                    + " source=" + connector.sourceId);
        }

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos railPos = spec.offsetForPart(root, facing, part);
            BlockState state = stateForPlacement(
                    block, facing, part, active, originalVisualRootOwnership);
            level.setBlock(railPos, state, 3);
        }

        // Lock the assembly-owned canonical path after vanilla rail neighbor
        // negotiation has seen the endpoints. Preserve the same render-only
        // ownership marker across every guide state in this assembly.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos railPos = spec.offsetForPart(root, facing, part);
            BlockState current = level.getBlockState(railPos);
            if (current.getBlock() == block) {
                level.setBlock(railPos,
                        stateForPlacement(
                                block, facing, part, active,
                                originalVisualRootOwnership),
                        2);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, manualAnchor,
                SoundEvents.METAL_PLACE, SoundSource.BLOCKS,
                0.8F, 1.0F);

        if (oneShot != null) {
            oneShot.add("");
            oneShot.add("=== ACTUAL PLACED PARTS ===");
            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos railPos = spec.offsetForPart(root, facing, part);
                BlockState actual = level.getBlockState(railPos);
                oneShot.add("PLACED_PART part=" + part
                        + " pos=" + railPos
                        + " expectedShape="
                        + spec.shapeForPart(facing, part, false).getSerializedName()
                        + " actual=" + actual);
            }
            if (connector != null && chosen != null) {
                BlockPos requiredEndpointCell =
                        requiredEndpointCellFor(spec.id(), connector);
                BlockPos physicalEndpointCell =
                        spec.offsetForPart(root, facing, chosen.endpointPart);
                BlockPos logicalEndpointCell =
                        spec.endpointPositionForPart(root, facing, chosen.endpointPart);
                oneShot.add("POST_LOGICAL_CONNECTOR_MATCH="
                        + logicalEndpointCell.equals(requiredEndpointCell)
                        + " required=" + requiredEndpointCell
                        + " logical=" + logicalEndpointCell
                        + " physicalPart=" + physicalEndpointCell);
            }
            oneShot.add("OUTCOME=PLACED root=" + root + " facing=" + facing);
            BatchTrackDiagnostics.finishPlacementDebug(
                    player, debugCenter, oneShot);
        }

        return InteractionResult.CONSUME;
    }

    // STEP_9_3E_T5_R6_OG_SMALL_TERMINAL_CLICK_CAPTURE
    //
    // OG Traincraft 4.5 Small Diagonal:
    // physical L footprint = root + two gag cells
    // logical continuation = the EMPTY diagonal cell.
    //
    // t5-r2 restored that logical endpoint. This placement-only gate makes
    // extending a Small use that empty logical cell as the NEXT Small root,
    // and inherits the owning Small's facing so player yaw cannot kick the
    // extension sideways. All other track families keep the existing snap path.
    private Candidate findOgSmallLogicalExtensionCandidate(Level level,
                                                            UseOnContext context,
                                                            Player player,
                                                            ItemStack stack,
                                                            List<String> oneShot) {
        if (!"track_diagonal_straight_small".equals(spec.id())) {
            return null;
        }

        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        boolean railClick = BaseRailBlock.isRail(clickedState);
        BlockPos requestedCell = railClick ? null : clicked.above();
        Vec3 click = context.getClickLocation();

        BlockPos clickedSmallRoot = null;
        Direction clickedSmallFacing = null;
        if (railClick
                && clickedState.getBlock() instanceof AbstractLegacyBatchRailBlock clickedBatch
                && "track_diagonal_straight_small".equals(clickedBatch.getSpec().id())) {
            clickedSmallRoot = clickedBatch.getAssemblyRoot(clicked, clickedState);
            clickedSmallFacing = clickedBatch.getAssemblyFacing(clickedState);
        }

        Candidate best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        List<String> seenAssemblies = new ArrayList<>();

        // Step 9.3e-t5-r5:
        // OG Small has TWO usable route ends even though only its far endpoint
        // carries a logicalOffset in the compatibility spec.
        //
        //   start = physical/root cell
        //   end   = empty diagonal logical cell
        //
        // t5-r3 handled only the logicalOffset/end side.  That made the first
        // extension work on one orientation, but the next exposed end could be
        // the physical/root side and then fell back to manual/player-facing
        // placement.  Resolve the full logical diagonal step once and mirror it
        // across the source root for the start-side extension.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos probe = clicked.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }
                    if (!"track_diagonal_straight_small".equals(batch.getSpec().id())) {
                        continue;
                    }

                    BlockPos sourceRoot = batch.getAssemblyRoot(probe, state);
                    Direction sourceFacing = batch.getAssemblyFacing(state);
                    String key = sourceRoot.asLong() + "|" + sourceFacing.ordinal();
                    if (seenAssemblies.contains(key)) {
                        continue;
                    }
                    seenAssemblies.add(key);

                    BlockPos logicalStep = null;
                    for (LegacyBatchTrackSpec.Endpoint candidateEndpoint
                            : batch.getSpec().endpoints()) {
                        if (!candidateEndpoint.hasLogicalOffset()) {
                            continue;
                        }
                        BlockPos farLogical = batch.getSpec().endpointPosition(
                                sourceRoot, sourceFacing, candidateEndpoint);
                        BlockPos step = farLogical.subtract(sourceRoot);
                        if (step.getX() != 0 || step.getZ() != 0) {
                            logicalStep = step;
                            break;
                        }
                    }
                    if (logicalStep == null) {
                        continue;
                    }

                    for (LegacyBatchTrackSpec.Endpoint endpoint : batch.getSpec().endpoints()) {
                        BlockPos physicalEndpoint = batch.getSpec().offsetForPart(
                                sourceRoot, sourceFacing, endpoint.part());
                        BlockPos logicalEndpoint = batch.getSpec().endpointPosition(
                                sourceRoot, sourceFacing, endpoint);

                        // Far/logical side: next root IS the empty logical end.
                        // Start/root side: mirror one full diagonal step backward
                        // so the new piece's far logical end terminates at this root.
                        BlockPos candidateRoot = endpoint.hasLogicalOffset()
                                ? logicalEndpoint
                                : sourceRoot.subtract(logicalStep);

                        boolean selectedEnd;
                        double distanceSq;

                        if (railClick) {
                            // Step 9.3e-t5-r6:
                            // Clicking ANY rail cell of the terminal Small assembly
                            // is enough to request an extension.  Pick the open end
                            // nearest the actual hit point; placement validity below
                            // naturally rejects the already-connected inner end.
                            selectedEnd = clickedSmallRoot != null
                                    && clickedSmallRoot.equals(sourceRoot)
                                    && clickedSmallFacing == sourceFacing;
                            double px = physicalEndpoint.getX() + 0.5D;
                            double pz = physicalEndpoint.getZ() + 0.5D;
                            double ddx = click.x - px;
                            double ddz = click.z - pz;
                            distanceSq = ddx * ddx + ddz * ddz;
                        } else {
                            // Ground placement remains exact: point at the center
                            // support block where the next Small should begin.
                            selectedEnd = candidateRoot.equals(requestedCell);
                            double cx = candidateRoot.getX() + 0.5D;
                            double cz = candidateRoot.getZ() + 0.5D;
                            double ddx = click.x - cx;
                            double ddz = click.z - cz;
                            distanceSq = ddx * ddx + ddz * ddz;
                        }
                        if (!selectedEnd) {
                            continue;
                        }

                        String reject = placementRejectReason(
                                level, player, stack, candidateRoot, sourceFacing);

                        if (oneShot != null) {
                            oneShot.add("OG_SMALL_EXTENSION_PROBE sourceRoot=" + sourceRoot
                                    + " sourceFacing=" + sourceFacing
                                    + " sourceEndpointPart=" + endpoint.part()
                                    + " physicalEndpoint=" + physicalEndpoint
                                    + " logicalEndpoint=" + logicalEndpoint
                                    + " logicalStep=" + logicalStep
                                    + " candidateRoot=" + candidateRoot
                                    + " side=" + (endpoint.hasLogicalOffset()
                                            ? "LOGICAL_END" : "ROOT_END")
                                    + " railClick=" + railClick
                                    + " requestedCell=" + requestedCell
                                    + " placeable=" + (reject == null)
                                    + " reject=" + (reject == null ? "NONE" : reject)
                                    + " clickDistance=" + Math.sqrt(distanceSq));
                        }

                        if (reject != null || distanceSq >= bestDistanceSq) {
                            continue;
                        }

                        best = new Candidate(
                                candidateRoot, sourceFacing, endpoint.part(), 0);
                        bestDistanceSq = distanceSq;
                    }
                }
            }
        }

        return best;
    }
    private List<Candidate> candidatesFor(Level level,
                                          Player player,
                                          ItemStack stack,
                                          Connector connector,
                                          List<String> oneShot) {
        List<Candidate> out = new ArrayList<>();
        BlockPos requiredEndpointCell =
                requiredEndpointCellFor(spec.id(), connector);
        Direction requiredNewOutward =
                connector.outward.getOpposite();

        if (oneShot != null) {
            oneShot.add("");
            oneShot.add("=== INCOMING ENDPOINT CANDIDATE MATRIX ===");
            oneShot.add("SOURCE_CONNECTOR sourceId=" + connector.sourceId
                    + " segment=" + connector.segment
                    + " outward=" + connector.outward
                    + " requiredEndpointCell=" + requiredEndpointCell
                    + " requiredNewOutward=" + requiredNewOutward
                    + " sourceRouteHeading=" + connector.routeHeading
                    + " requiredNewRouteHeading=" + connector.routeHeading.opposite()
                    + " originalTraincraftSharedProxyAnchor="
                    + isOriginalEightWaySnapPair(spec.id(), connector.sourceId)
                    + " clickDistance=" + Math.sqrt(connector.distanceSq));
            if (isOriginalEightWaySnapPair(spec.id(), connector.sourceId)
                    && connector.routeHeading.isDiagonal()) {
                BlockPos sharedRoot = connector.segment.relative(connector.outward);
                BlockPos sharedForward = sharedRoot.relative(connector.outward);
                BlockPos sharedSide = sharedRoot.offset(
                        connector.routeHeading.dx - connector.outward.getStepX(),
                        0,
                        connector.routeHeading.dz - connector.outward.getStepZ());
                oneShot.add("ORIGINAL_TC45_SHARED_GAG_CORRIDOR root=" + sharedRoot
                        + " forwardGag=" + sharedForward
                        + " sideGag=" + sharedSide);
            }
        }

        for (Direction candidateFacing : Direction.Plane.HORIZONTAL) {
            for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                Direction newOutward =
                        placementEndpointOutward(
                                spec, candidateFacing, endpoint);

                BlockPos offset = spec.endpointPosition(
                        BlockPos.ZERO, candidateFacing, endpoint);
                BlockPos candidateRoot = requiredEndpointCell.subtract(offset);
                BlockPos placedEndpoint = spec.endpointPosition(
                        candidateRoot, candidateFacing, endpoint);

                RouteHeading8 candidateHeading = routeHeadingForEndpoint(
                        spec, candidateRoot, candidateFacing, endpoint);
                boolean originalEightWayPair = isOriginalEightWaySnapPair(
                        spec.id(), connector.sourceId);
                boolean originalCrossingPair = isOriginalDiagonalCrossingSnapPair(
                        spec.id(), connector.sourceId);
                boolean exactTraincraftTangentPair =
                        originalEightWayPair || originalCrossingPair;
                boolean tangentFaceToFace = candidateHeading
                        == connector.routeHeading.opposite();

                // STEP_9_3E_T4_R4_OG_SMALL_ROOT_AT_CROSSING
                //
                // Original Small Diagonal has one REAL rail/model owner at p0
                // and two gag cells.  When attaching Small directly to the
                // centered Diagonal Crossing, the crossing-adjacent endpoint
                // must therefore be p0.  The opposite p2 orientation is the
                // same tangent geometrically but shifts the owner/model root
                // to the wrong side and reintroduces player-facing placement.
                boolean originalCrossingSmallRoot =
                        originalCrossingPair
                        && TARGET_DIAGONAL_CROSSING.equals(connector.sourceId)
                        && "track_diagonal_straight_small".equals(spec.id());
                boolean correctOriginalSmallRoot =
                        !originalCrossingSmallRoot || endpoint.part() == 0;

                boolean faceToFace = newOutward == requiredNewOutward;
                boolean diagonalFamily = isDiagonalConnectorFamily(spec.id())
                        || isDiagonalConnectorFamily(connector.sourceId);
                boolean cornerHandoff = !exactTraincraftTangentPair
                        && diagonalFamily
                        && newOutward.getAxis() != connector.outward.getAxis();
                boolean relationAllowed = exactTraincraftTangentPair
                        ? (tangentFaceToFace && correctOriginalSmallRoot)
                        : (faceToFace || cornerHandoff);

                String reject = relationAllowed
                        ? placementRejectReason(level, player, stack,
                                candidateRoot, candidateFacing)
                        : (exactTraincraftTangentPair
                        ? "TRAINCRAFT_8WAY_TANGENT_REJECTED"
                        : "DIRECTION_RELATION_REJECTED");
                boolean placeable = relationAllowed && reject == null;
                int connectorPenalty = exactTraincraftTangentPair || faceToFace
                        ? 0
                        : CORNER_HANDOFF_PENALTY;

                // STEP_9_3F_T1A_R6_R1_SMALL_SWITCH_COMMON_TOE_PRIORITY
                //
                // True Small-switch topology:
                //   p5 = common/toe on the outside mainline
                //   p2 = opposite outside-straight endpoint
                //   p0 = inside/diverging endpoint
                //
                // When the user snaps a new Small switch onto the track they are
                // currently building, default to p5 so the switch grows forward
                // from that existing line instead of choosing p0/p2 merely from
                // endpoint-number or player-yaw tie breaks.
                int switchEndpointPenalty = 0;
                if (isTargetSmallSwitchSpec(spec.id())) {
                    switchEndpointPenalty = switch (endpoint.part()) {
                        case 5 -> 0;
                        case 2 -> 8;
                        case 0 -> 16;
                        default -> 24;
                    };
                } else if (isWholeSwitchFamilySnapSpec(spec.id())) {
                    // All remaining classic switch families use p0 as their
                    // placement/common end. Prefer that end when extending an
                    // existing line. If p0 cannot legally mate, the other declared
                    // endpoints remain available through the normal connector
                    // geometry checks.
                    switchEndpointPenalty =
                            endpoint.part() == 0 ? 0 : 8;
                }

                int totalPenalty = connectorPenalty
                        + switchEndpointPenalty
                        + facingPenalty(candidateFacing, player.getDirection());

                if (oneShot != null) {
                    oneShot.add("CANDIDATE facing=" + candidateFacing
                            + " endpointPart=" + endpoint.part()
                            + " endpointDeclaredOutward=" + endpoint.outward()
                            + " endpointWorldOutward=" + newOutward
                            + " sourceRouteHeading=" + connector.routeHeading
                            + " candidateRouteHeading=" + candidateHeading
                            + " originalTraincraft8WayPair=" + originalEightWayPair
                            + " originalTraincraftCrossingPair=" + originalCrossingPair
                            + " originalCrossingSmallRoot=" + originalCrossingSmallRoot
                            + " correctOriginalSmallRoot=" + correctOriginalSmallRoot
                            + " tangentFaceToFace=" + tangentFaceToFace
                            + " faceToFace=" + faceToFace
                            + " cornerHandoff=" + cornerHandoff
                            + " relationAllowed=" + relationAllowed
                            + " offset=" + offset
                            + " root=" + candidateRoot
                            + " placedEndpoint=" + placedEndpoint
                            + " logicalExact=" + placedEndpoint.equals(requiredEndpointCell)
                            + " placeable=" + placeable
                            + " reject=" + (reject == null ? "NONE" : reject)
                            + " penalty=" + totalPenalty);
                }

                if (!placeable) {
                    continue;
                }

                out.add(new Candidate(candidateRoot, candidateFacing,
                        endpoint.part(), totalPenalty));
            }
        }

        return out;
    }

    private boolean canPlaceAssembly(Level level,
                                     Player player,
                                     ItemStack stack,
                                     BlockPos root,
                                     Direction facing) {
        return placementRejectReason(level, player, stack, root, facing) == null;
    }

    private String placementRejectReason(Level level,
                                         Player player,
                                         ItemStack stack,
                                         BlockPos root,
                                         Direction facing) {
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos railPos = spec.offsetForPart(root, facing, part);
            BlockPos supportPos = spec.supportForPart(root, facing, part);

            BlockState existing = level.getBlockState(railPos);
            if (!canReplaceRailCell(level, railPos, existing)) {
                return "BLOCKED part=" + part
                        + " railPos=" + railPos
                        + " state=" + existing;
            }

            BlockState support = level.getBlockState(supportPos);
            if (!support.isFaceSturdy(level, supportPos, Direction.UP)) {
                return "NO_SUPPORT part=" + part
                        + " supportPos=" + supportPos
                        + " state=" + support;
            }

            if (!player.mayUseItemAt(railPos, Direction.UP, stack)) {
                return "MAY_USE_DENIED part=" + part
                        + " railPos=" + railPos;
            }
        }

        return null;
    }

    /**
     * Batch placement may clear ordinary replaceable vegetation and stale
     * orphan guide cells. Live rail assemblies are never replaceable.
     */
    private static boolean canReplaceRailCell(Level level,
                                              BlockPos pos,
                                              BlockState state) {
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }

        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            return batch.isOrphanSegment(level, pos, state);
        }

        return false;
    }


    /** Placement failure report retained from d1 and updated for the r2 slope support footprint. */
    private void writePlacementFailure(Level level,
                                       Player player,
                                       ItemStack stack,
                                       BlockPos root,
                                       Direction facing,
                                       String reason) {
        try {
            List<String> report = new ArrayList<>();
            report.add("TRAINCRAFT STEP 9.3a-r2 PLACEMENT DIAGNOSTICS");
            report.add("spec=" + spec.id());
            report.add("reason=" + reason);
            report.add("root=" + root + " facing=" + facing + " parts=" + spec.partCount());
            report.add("player=" + player.getName().getString()
                    + " pos=" + player.position()
                    + " direction=" + player.getDirection());
            report.add("held=" + stack);
            report.add("READ ONLY: this report does not place or change blocks.");
            report.add("");

            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos railPos = spec.offsetForPart(root, facing, part);
                BlockPos supportPos = spec.supportForPart(root, facing, part);
                BlockState railState = level.getBlockState(railPos);
                BlockState supportState = level.getBlockState(supportPos);
                boolean air = railState.isAir();
                boolean sturdy = supportState.isFaceSturdy(level, supportPos, Direction.UP);
                boolean mayUse = player.mayUseItemAt(railPos, Direction.UP, stack);

                report.add("PART " + part
                        + " railPos=" + railPos
                        + " point=" + spec.pointForPart(part)
                        + " primary=" + spec.shapeForPart(facing, part, false).getSerializedName()
                        + " alternate=" + spec.shapeForPart(facing, part, true).getSerializedName()
                        + " railAir=" + air
                        + " railState=" + railState
                        + " supportPos=" + supportPos
                        + " supportSturdy=" + sturdy
                        + " supportState=" + supportState
                        + " mayUse=" + mayUse);
            }

            Path dir = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path file = dir.resolve("placement-" + spec.id() + "-" + stamp + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".txt");
            Files.write(file, report, StandardCharsets.UTF_8);
            player.sendSystemMessage(Component.literal(
                    "Track placement rejected. Diagnostic report: " + file.toAbsolutePath()));
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal(
                    "Track placement rejected; diagnostic write failed: " + ex.getMessage()));
        }
    }

    private static int facingPenalty(Direction candidate,
                                     Direction playerFacing) {
        if (candidate == playerFacing) return 0;
        if (candidate.getOpposite() == playerFacing) return 2;
        return 1;
    }

    private Connector findConnector(Level level,
                                    UseOnContext context,
                                    List<String> oneShot) {
        Vec3 click = context.getClickLocation();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);

        if (oneShot != null) {
            oneShot.add("");
            oneShot.add("=== SOURCE CONNECTOR SEARCH ===");
            oneShot.add("SEARCH clicked=" + clicked
                    + " clickedState=" + clickedState
                    + " click=" + click
                    + " requestedGroundCell=" + clicked.above());
        }

        // Step 9.3c-t4-r1: when a rail cell itself is clicked, the clicked
        // assembly owns the interaction. Ordinary assemblies retain the tight
        // local-radius gate.
        //
        // STEP_9_3E_T2_R3_DIRECT_CROSSING_SELECTION
        // A direct click on any of the centered crossing's nine owned cells is
        // allowed to select the nearest open physical corner endpoint. This is
        // assembly-local only; ground clicks still use exact/one-cell acquisition.
        if (BaseRailBlock.isRail(clickedState)) {
            Connector clickedAssembly =
                    findClickedAssemblyConnector(level, clicked, click);

            boolean directOriginalCrossingClick =
                    clickedAssembly != null
                    && TARGET_DIAGONAL_CROSSING.equals(clickedAssembly.sourceId)
                    && (isDiagonalStraightFamily(spec.id())
                        || isMedium45SwitchSource(spec.id()));

            if (oneShot != null) {
                oneShot.add("RAIL_CLICK connector=" + formatConnector(clickedAssembly)
                        + " directOriginalCrossingClick=" + directOriginalCrossingClick
                        + " ordinaryWithinRadius=" + (clickedAssembly != null
                        && clickedAssembly.distanceSq
                        <= CONNECTOR_RADIUS * CONNECTOR_RADIUS));
            }

            if (directOriginalCrossingClick) {
                if (oneShot != null) {
                    oneShot.add("OG_DIAGONAL_CROSSING_DIRECT_CLICK_ACCEPT"
                            + " clickedSegment=" + clicked
                            + " selected=" + formatConnector(clickedAssembly)
                            + " reason=CLICKED_ASSEMBLY_OWNS_LINKED_ENDPOINT");
                }
                return clickedAssembly;
            }

            if (clickedAssembly != null
                    && clickedAssembly.distanceSq
                    <= CONNECTOR_RADIUS * CONNECTOR_RADIUS) {
                return clickedAssembly;
            }
            return null;
        }

        // Ground/support placement first honors the exact requested cell.
        // Some original Traincraft 45-degree / diagonal OBJ anchors are
        // visually displaced from their logical guide endpoint by one block.
        // If the exact cell has no source connector, inspect ONLY the four
        // horizontally-adjacent requested cells. Accept the recovery only when
        // exactly one open connector is found in that local ring. This fixes
        // the measured one-cell visual handoff without restoring the old
        // broad endpoint search that caused remote/random placements.
        BlockPos requestedCell = clicked.above();
        Connector result = findConnectorForGroundRequest(
                level, requestedCell, click, oneShot);
        result = preferRouteSemanticConnector(level, result, click, oneShot);
        if (oneShot != null) {
            oneShot.add("GROUND_CLICK requestedCell=" + requestedCell
                    + " selected=" + formatConnector(result));
        }
        return result;
    }

    /**
     * Step 9.3c-t4-r5: a 45-degree switch exposes both a straight continuation
     * and a diagonal/curved branch. The legacy guide cells can make the
     * straight endpoint one cell closer to the natural ground click even when
     * the incoming piece is a diagonal-family track. Keep r2's local search as
     * the acquisition gate, then inspect ONLY the same source assembly for an
     * immediately-adjacent open endpoint whose live rail shape is diagonal.
     * This prevents the part-4 straight endpoint from stealing the part-2
     * diagonal branch without reintroducing any global/remote endpoint search.
     */
    private Connector preferRouteSemanticConnector(Level level,
                                                    Connector selected,
                                                    Vec3 click,
                                                    List<String> oneShot) {
        if (selected == null || !isDiagonalConnectorFamily(spec.id())) {
            return selected;
        }
        if (!is45DegreeSwitchFamily(selected.sourceId)) {
            return selected;
        }

        BlockState selectedState = level.getBlockState(selected.segment);
        if (!(selectedState.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
            return selected;
        }
        if (!batch.getSpec().id().equals(selected.sourceId)) {
            return selected;
        }

        RailShape selectedShape = liveRailShape(level, selected.segment, selectedState);
        boolean selectedDiagonal = isDiagonalRailShape(selectedShape);
        if (oneShot != null) {
            oneShot.add("ROUTE_SEMANTIC_START incoming=" + spec.id()
                    + " source=" + selected.sourceId
                    + " selectedSegment=" + selected.segment
                    + " selectedOutward=" + selected.outward
                    + " selectedShape=" + (selectedShape == null ? "null" : selectedShape.getSerializedName())
                    + " selectedDiagonal=" + selectedDiagonal);
        }
        if (selectedDiagonal) {
            if (oneShot != null) {
                oneShot.add("ROUTE_SEMANTIC_KEEP reason=SELECTED_ALREADY_DIAGONAL");
            }
            return selected;
        }

        BlockPos root = batch.getAssemblyRoot(selected.segment, selectedState);
        Direction facing = batch.getAssemblyFacing(selectedState);
        Connector bestDiagonal = null;
        int bestManhattan = Integer.MAX_VALUE;

        for (LegacyBatchTrackSpec.Endpoint endpoint : batch.getSpec().endpoints()) {
            BlockPos segment = batch.getSpec().endpointPosition(root, facing, endpoint);
            int manhattan = Math.abs(segment.getX() - selected.segment.getX())
                    + Math.abs(segment.getY() - selected.segment.getY())
                    + Math.abs(segment.getZ() - selected.segment.getZ());
            if (manhattan > 1) {
                continue;
            }

            Direction outward = placementEndpointOutward(
                    batch.getSpec(), facing, endpoint);
            if (hasRailNeighbor(level, segment, outward)) {
                if (oneShot != null) {
                    oneShot.add("ROUTE_SEMANTIC_CANDIDATE endpointPart=" + endpoint.part()
                            + " segment=" + segment + " outward=" + outward
                            + " manhattanFromInitial=" + manhattan
                            + " open=false reason=RAIL_NEIGHBOR");
                }
                continue;
            }

            BlockState endpointState = level.getBlockState(segment);
            RailShape shape = liveRailShape(level, segment, endpointState);
            boolean diagonal = isDiagonalRailShape(shape);
            if (oneShot != null) {
                oneShot.add("ROUTE_SEMANTIC_CANDIDATE endpointPart=" + endpoint.part()
                        + " segment=" + segment + " outward=" + outward
                        + " manhattanFromInitial=" + manhattan
                        + " open=true shape=" + (shape == null ? "null" : shape.getSerializedName())
                        + " diagonal=" + diagonal);
            }
            if (!diagonal) {
                continue;
            }

            Connector candidate = connectorAt(
                    segment, outward,
                    routeHeadingForEndpoint(
                            batch.getSpec(), root, facing, endpoint),
                    click, selected.sourceId);
            if (bestDiagonal == null
                    || manhattan < bestManhattan
                    || (manhattan == bestManhattan
                    && candidate.distanceSq < bestDiagonal.distanceSq)) {
                bestDiagonal = candidate;
                bestManhattan = manhattan;
            }
        }

        if (bestDiagonal != null) {
            if (oneShot != null) {
                oneShot.add("ROUTE_SEMANTIC_OVERRIDE from=" + formatConnector(selected)
                        + " to=" + formatConnector(bestDiagonal)
                        + " reason=DIAGONAL_INCOMING_PREFERS_ADJACENT_DIAGONAL_SWITCH_BRANCH");
            }
            return bestDiagonal;
        }

        if (oneShot != null) {
            oneShot.add("ROUTE_SEMANTIC_KEEP reason=NO_ADJACENT_OPEN_DIAGONAL_ENDPOINT");
        }
        return selected;
    }

    private static boolean is45DegreeSwitchFamily(String id) {
        return id != null && id.startsWith("track_switch_45_");
    }

    private static boolean isDiagonalRailShape(RailShape shape) {
        return shape == RailShape.NORTH_EAST
                || shape == RailShape.NORTH_WEST
                || shape == RailShape.SOUTH_EAST
                || shape == RailShape.SOUTH_WEST;
    }

    private static RailShape liveRailShape(Level level,
                                           BlockPos pos,
                                           BlockState state) {
        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }
        try {
            return rail.getRailDirection(state, level, pos, null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Connector findConnectorForGroundRequest(Level level,
                                                    BlockPos requestedCell,
                                                    Vec3 click,
                                                    List<String> oneShot) {
        Connector exact = findConnectorForExactRequiredCell(
                level, requestedCell, click, oneShot);
        if (exact != null) {
            if (oneShot != null) {
                oneShot.add("LOCAL_CELL_ACQUISITION mode=EXACT requestedCell="
                        + requestedCell
                        + " selected=" + formatConnector(exact));
            }
            return exact;
        }

        List<Connector> local = new ArrayList<>();
        for (Direction offsetDirection : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentRequiredCell =
                    requestedCell.relative(offsetDirection);
            Connector candidate = findConnectorForExactRequiredCell(
                    level, adjacentRequiredCell, click, oneShot);

            if (oneShot != null) {
                oneShot.add("LOCAL_CELL_PROBE base=" + requestedCell
                        + " offsetDirection=" + offsetDirection
                        + " adjacentRequiredCell=" + adjacentRequiredCell
                        + " candidate=" + formatConnector(candidate));
            }

            if (candidate == null) {
                continue;
            }

            boolean duplicate = false;
            for (Connector existing : local) {
                if (existing.segment.equals(candidate.segment)
                        && existing.outward == candidate.outward
                        && java.util.Objects.equals(
                                existing.sourceId, candidate.sourceId)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                local.add(candidate);
            }
        }

        if (local.size() == 1) {
            Connector selected = local.get(0);
            if (oneShot != null) {
                oneShot.add("LOCAL_CELL_ACQUISITION mode=ADJACENT_ONE_CELL"
                        + " requestedCell=" + requestedCell
                        + " candidateCount=1 selected="
                        + formatConnector(selected));
            }
            return selected;
        }

        // STEP_9_3E_T4_R4_CROSSING_PRIORITY_IN_LOCAL_AMBIGUITY
        //
        // With the OG Small L footprint restored, rebuilding one of the four
        // immediate crossing Smalls can legitimately expose TWO one-cell
        // source connectors at the clicked support cell:
        //   - the centered crossing endpoint being rebuilt, and
        //   - an already-present outer Small continuation.
        //
        // For an incoming Small only, a UNIQUE centered-crossing connector
        // owns that immediate attachment.  This removes the old dependence on
        // which side the player stands on while remaining local to one cell.
        if ("track_diagonal_straight_small".equals(spec.id())
                && local.size() > 1) {
            Connector centeredCrossing = null;
            int centeredCrossingCount = 0;
            for (Connector candidate : local) {
                if (TARGET_DIAGONAL_CROSSING.equals(candidate.sourceId)) {
                    centeredCrossing = candidate;
                    centeredCrossingCount++;
                }
            }
            if (centeredCrossingCount == 1) {
                if (oneShot != null) {
                    oneShot.add("LOCAL_CELL_ACQUISITION mode=OG_CROSSING_PRIORITY"
                            + " requestedCell=" + requestedCell
                            + " candidateCount=" + local.size()
                            + " selected=" + formatConnector(centeredCrossing));
                }
                return centeredCrossing;
            }
        }

        // Step 9.3d-t9-r1 / Step 9.3e-t2-r4:
        // TC4.5 diagonal straights can meet either a Medium-45 branch or one of
        // the centered crossing's four corner proxy endpoints through a diagonal
        // one-cell support handoff. Probe ONLY the four diagonal neighbor cells
        // and accept ONLY one unique diagonal-route connector from those two
        // explicitly supported source families. This stays local and does not
        // restore any broad/remote crossing ownership search.
        if (isDiagonalStraightFamily(spec.id())) {
            List<Connector> diagonalLocal = new ArrayList<>();
            for (int dx : new int[]{-1, 1}) {
                for (int dz : new int[]{-1, 1}) {
                    BlockPos diagonalRequiredCell = requestedCell.offset(dx, 0, dz);
                    Connector candidate = findConnectorForExactRequiredCell(
                            level, diagonalRequiredCell, click, oneShot);
                    if (oneShot != null) {
                        oneShot.add("DIAGONAL_CORNER_PROBE base=" + requestedCell
                                + " offset=(" + dx + "," + dz + ")"
                                + " diagonalRequiredCell=" + diagonalRequiredCell
                                + " candidate=" + formatConnector(candidate));
                    }
                    boolean acceptedDiagonalCornerSource =
                            candidate != null
                            && (isMedium45SwitchSource(candidate.sourceId)
                                || TARGET_DIAGONAL_CROSSING.equals(candidate.sourceId));
                    if (!acceptedDiagonalCornerSource
                            || !candidate.routeHeading.isDiagonal()) {
                        continue;
                    }
                    boolean duplicate = false;
                    for (Connector existing : diagonalLocal) {
                        if (existing.segment.equals(candidate.segment)
                                && existing.outward == candidate.outward
                                && java.util.Objects.equals(
                                        existing.sourceId, candidate.sourceId)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        diagonalLocal.add(candidate);
                    }
                }
            }
            if (diagonalLocal.size() == 1) {
                Connector selected = diagonalLocal.get(0);
                if (oneShot != null) {
                    oneShot.add("LOCAL_CELL_ACQUISITION mode=DIAGONAL_CORNER_ONE_CELL"
                            + " requestedCell=" + requestedCell
                            + " candidateCount=1 selected="
                            + formatConnector(selected));
                }
                return selected;
            }
            if (oneShot != null && !diagonalLocal.isEmpty()) {
                oneShot.add("LOCAL_CELL_ACQUISITION mode=DIAGONAL_CORNER_AMBIGUOUS_REJECT"
                        + " requestedCell=" + requestedCell
                        + " candidateCount=" + diagonalLocal.size());
            }
        }

        if (oneShot != null) {
            oneShot.add("LOCAL_CELL_ACQUISITION mode="
                    + (local.isEmpty() ? "NONE" : "AMBIGUOUS_REJECT")
                    + " requestedCell=" + requestedCell
                    + " candidateCount=" + local.size());
            for (int i = 0; i < local.size(); i++) {
                oneShot.add("LOCAL_CELL_CANDIDATE index=" + i + " "
                        + formatConnector(local.get(i)));
            }
        }
        return null;
    }

    private Connector findConnectorForExactRequiredCell(Level level,
                                                         BlockPos requestedCell,
                                                         Vec3 click,
                                                         List<String> oneShot) {
        Connector best = null;

        for (Direction outward : Direction.Plane.HORIZONTAL) {
            BlockPos segment =
                    requestedCell.relative(outward.getOpposite());
            BlockState state = level.getBlockState(segment);
            List<Direction> opens = openDirections(level, segment, state);
            if (oneShot != null) {
                oneShot.add("SOURCE_PROBE requestedCell=" + requestedCell
                        + " expectedOutward=" + outward
                        + " segment=" + segment
                        + " state=" + state
                        + " sourceId=" + connectorSourceId(state)
                        + " openDirections=" + opens);
            }

            for (Direction open : opens) {
                if (open != outward) {
                    continue;
                }

                Connector candidate = connectorAt(
                        segment, open,
                        routeHeadingForSource(segment, state, open),
                        click, connectorSourceId(state));
                best = nearerConnector(best, candidate);
            }
        }

        Connector virtual = findVirtualBatchConnectorForRequiredCell(
                level, requestedCell, click, oneShot);
        return nearerConnector(best, virtual);
    }

    /**
     * Resolve endpoint metadata whose logical connector segment is not itself a
     * placed rail block. Search remains strictly local to the requested cell so
     * the old remote/random endpoint capture cannot return.
     */
    private Connector findVirtualBatchConnectorForRequiredCell(Level level,
                                                                BlockPos requestedCell,
                                                                Vec3 click,
                                                                List<String> oneShot) {
        Connector best = null;
        List<String> seenAssemblies = new ArrayList<>();

        // Virtual endpoint metadata remains available for the older accepted
        // families that still need it. The Step 9.3e-t2-r3 Diagonal Crossing
        // has no virtual endpoints: its four connector segments are real proxy
        // cells in the centered 3x3 topology.
        int virtualProbeRadius = isDiagonalConnectorFamily(spec.id()) ? 3 : 2;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -virtualProbeRadius; dx <= virtualProbeRadius; dx++) {
                for (int dz = -virtualProbeRadius; dz <= virtualProbeRadius; dz++) {
                    BlockPos probe = requestedCell.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    BlockPos root = batch.getAssemblyRoot(probe, state);
                    Direction facing = batch.getAssemblyFacing(state);
                    String assemblyKey = batch.getSpec().id() + "|" + root.asLong()
                            + "|" + facing.ordinal();
                    if (seenAssemblies.contains(assemblyKey)) {
                        continue;
                    }
                    seenAssemblies.add(assemblyKey);

                    for (LegacyBatchTrackSpec.Endpoint endpoint : batch.getSpec().endpoints()) {
                        if (!endpoint.hasLogicalOffset()) {
                            continue;
                        }
                        BlockPos logicalSegment = batch.getSpec().endpointPosition(
                                root, facing, endpoint);
                        Direction outward = placementEndpointOutward(
                                batch.getSpec(), facing, endpoint);
                        BlockPos required = logicalSegment.relative(outward);
                        if (!required.equals(requestedCell)) {
                            continue;
                        }
                        if (hasRailNeighbor(level, logicalSegment, outward)) {
                            continue;
                        }

                        Connector candidate = connectorAt(
                                logicalSegment, outward,
                                routeHeadingForEndpoint(
                                        batch.getSpec(), root, facing, endpoint),
                                click, batch.getSpec().id());
                        best = nearerConnector(best, candidate);
                        if (oneShot != null) {
                            oneShot.add("VIRTUAL_SOURCE_PROBE sourceId="
                                    + batch.getSpec().id()
                                    + " root=" + root
                                    + " facing=" + facing
                                    + " endpointPart=" + endpoint.part()
                                    + " logicalSegment=" + logicalSegment
                                    + " outward=" + outward
                                    + " requestedCell=" + requestedCell
                                    + " selectedCandidate=" + formatConnector(candidate));
                        }
                    }
                }
            }
        }

        return best;
    }

    private Connector findClickedAssemblyConnector(Level level,
                                                    BlockPos clicked,
                                                    Vec3 click) {
        BlockState state = level.getBlockState(clicked);
        Connector best = null;

        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            BlockPos root = batch.getAssemblyRoot(clicked, state);
            Direction facing = batch.getAssemblyFacing(state);

            for (LegacyBatchTrackSpec.Endpoint endpoint : batch.getSpec().endpoints()) {
                BlockPos segment = batch.getSpec().endpointPosition(
                        root, facing, endpoint);
                Direction outward = placementEndpointOutward(
                        batch.getSpec(), facing, endpoint);

                if (!hasRailNeighbor(level, segment, outward)) {
                    Connector candidate = connectorAt(
                            segment, outward,
                            routeHeadingForEndpoint(
                                    batch.getSpec(), root, facing, endpoint),
                            click, batch.getSpec().id());
                    if (segment.equals(clicked)) {
                        return candidate;
                    }
                    best = nearerConnector(best, candidate);
                }
            }
            return best;
        }

        if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
            int part = state.getValue(LegacyMediumCurveRailBlock.PART);
            Direction facing =
                    state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            BlockPos root = clicked.subtract(
                    LegacyMediumCurveRailBlock.offsetForPart(
                            BlockPos.ZERO, facing, part));

            BlockPos first = LegacyMediumCurveRailBlock.offsetForPart(
                    root, facing, 0);
            Direction firstOut = facing.getCounterClockWise();
            if (!hasRailNeighbor(level, first, firstOut)) {
                Connector candidate = connectorAt(
                        first, firstOut, click, "legacy_medium_curve");
                if (first.equals(clicked)) {
                    return candidate;
                }
                best = nearerConnector(best, candidate);
            }

            int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
            BlockPos last = LegacyMediumCurveRailBlock.offsetForPart(
                    root, facing, lastPart);
            Direction lastOut = facing.getOpposite();
            if (!hasRailNeighbor(level, last, lastOut)) {
                Connector candidate = connectorAt(
                        last, lastOut, click, "legacy_medium_curve");
                if (last.equals(clicked)) {
                    return candidate;
                }
                best = nearerConnector(best, candidate);
            }
            return best;
        }

        if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock straight) {
            int part = state.getValue(LegacyStraightAssemblyRailBlock.PART);
            Direction facing =
                    state.getValue(LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
            BlockPos root = clicked.relative(facing.getOpposite(), part);

            Direction firstOut = facing.getOpposite();
            if (!hasRailNeighbor(level, root, firstOut)) {
                Connector candidate = connectorAt(
                        root, firstOut, click, "legacy_straight");
                if (root.equals(clicked)) {
                    return candidate;
                }
                best = nearerConnector(best, candidate);
            }

            BlockPos last = root.relative(facing, straight.getAssemblyLength() - 1);
            Direction lastOut = facing;
            if (!hasRailNeighbor(level, last, lastOut)) {
                Connector candidate = connectorAt(
                        last, lastOut, click, "legacy_straight");
                if (last.equals(clicked)) {
                    return candidate;
                }
                best = nearerConnector(best, candidate);
            }
            return best;
        }

        if (state.getBlock() instanceof BaseRailBlock) {
            for (Direction outward : openDirections(level, clicked, state)) {
                best = nearerConnector(best,
                        connectorAt(clicked, outward, click, "vanilla_rail"));
            }
        }

        return best;
    }

    /**
     * Recover the original Traincraft 8-way route direction for a live source
     * endpoint. Most modern guide endpoints remain cardinal; the active branch
     * end of the Medium 45-degree Left switch is the focused exception for t2.
     */
    private static RouteHeading8 routeHeadingForSource(BlockPos segment,
                                                       BlockState state,
                                                       Direction outward) {
        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            BlockPos root = batch.getAssemblyRoot(segment, state);
            Direction facing = batch.getAssemblyFacing(state);
            for (LegacyBatchTrackSpec.Endpoint endpoint :
                    batch.getSpec().endpoints()) {
                BlockPos logical = batch.getSpec().endpointPosition(
                        root, facing, endpoint);
                Direction endpointOutward = placementEndpointOutward(
                        batch.getSpec(), facing, endpoint);
                if (logical.equals(segment) && endpointOutward == outward) {
                    return routeHeadingForEndpoint(
                            batch.getSpec(), root, facing, endpoint);
                }
            }
        }
        return RouteHeading8.fromDirection(outward);
    }

    /**
     * Original TC4.5 diagonal straights carried one of four dedicated diagonal
     * facings. Reconstruct that facing from the two logical endpoints instead
     * of from the Manhattan guide-cell RailShape. For the Medium 45-degree Left
     * switch, part 4 is the diverging end proven by the placement diagnostics;
     * canonical NORTH exits northwest and rotates with the assembly facing.
     */
    // STEP_9_3F_T1A_R2_SMALL_SWITCH_TRUE_MAINLINE_CONNECTOR
    //
    // The Small-switch source spec still carries the older p0<->p2 "straight"
    // endpoint declaration. Runtime proof + the accepted screenshot establish
    // the real topology as:
    //   p5 <-> p2 = outside/mainline straight
    //   p5 <-> p0 = inside/diverging curve
    //
    // Keep LegacyBatchTrackSpecs byte-identical for this correction and override
    // ONLY Small-switch p2's connector direction in the placement layer.
    // This makes newly snapped continuation track face forward along the outside
    // mainline without touching any other switch family or the proven t5-r6
    // Small-diagonal placement behavior.
    private static boolean isTargetSmallSwitchSpec(String id) {
        return "track_switch_small_left".equals(id)
                || "track_switch_small_right".equals(id);
    }

    // STEP_9_3G_T2_WHOLE_SWITCH_FAMILY_DIRECT_RAIL_SNAP
    private static boolean isWholeSwitchFamilySnapSpec(String id) {
        return isTargetSmallSwitchSpec(id)
                || "track_switch_medium_left".equals(id)
                || "track_switch_medium_right".equals(id)
                || "track_switch_large_left".equals(id)
                || "track_switch_large_right".equals(id)
                || "track_switch_very_large_left".equals(id)
                || "track_switch_very_large_right".equals(id)
                || "track_switch_45_medium_left".equals(id)
                || "track_switch_45_medium_right".equals(id)
                || "track_switch_parallel_left".equals(id)
                || "track_switch_parallel_right".equals(id);
    }

    private static Direction placementEndpointOutward(
            LegacyBatchTrackSpec endpointSpec,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        String id = endpointSpec.id();
        if (endpoint.part() == 2) {
            if ("track_switch_small_left".equals(id)) {
                return endpointSpec.rotateDirection(facing, Direction.EAST);
            }
            if ("track_switch_small_right".equals(id)) {
                return endpointSpec.rotateDirection(facing, Direction.WEST);
            }
        }
        return endpointSpec.rotateDirection(facing, endpoint.outward());
    }

    private static RouteHeading8 routeHeadingForEndpoint(
            LegacyBatchTrackSpec endpointSpec,
            BlockPos root,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        if (endpoint.part() == 4) {
            if (TARGET_45_MEDIUM_LEFT_SWITCH.equals(endpointSpec.id())) {
                return rotateRouteHeading(
                        RouteHeading8.NORTH_WEST, facing);
            }
            if (TARGET_45_MEDIUM_RIGHT_SWITCH.equals(endpointSpec.id())) {
                return rotateRouteHeading(
                        RouteHeading8.NORTH_EAST, facing);
            }
        }

        if (TARGET_DIAGONAL_CROSSING.equals(endpointSpec.id())) {
            RouteHeading8 crossingHeading = originalDiagonalCrossingEndpointHeading(
                    endpointSpec, root, facing, endpoint);
            if (crossingHeading != null) {
                return crossingHeading;
            }
        }

        // STEP_9_3E_T4_R4_OG_SMALL_LOGICAL_ROUTE_HEADING
        //
        // t4-r3 restored the actual TC4.5 Small-Diagonal L footprint:
        //   p0 owner (0,0), p1 gag (0,-1), p2 gag (1,0),
        // while the visible/movement diagonal still runs to the EMPTY
        // logical corner (1,-1).
        //
        // Therefore p0/p2 PHYSICAL positions are no longer allowed to define
        // the placement tangent.  Keep the original logical diagonal:
        //   p0 outward = SOUTH_WEST
        //   p2 outward = NORTH_EAST
        // then rotate that 8-way heading with the assembly facing.
        if ("track_diagonal_straight_small".equals(endpointSpec.id())) {
            if (endpoint.part() == 0) {
                return rotateRouteHeading(RouteHeading8.SOUTH_WEST, facing);
            }
            if (endpoint.part() == 2) {
                return rotateRouteHeading(RouteHeading8.NORTH_EAST, facing);
            }
        }

        if (isDiagonalStraightFamily(endpointSpec.id())
                && endpointSpec.endpoints().size() == 2) {
            LegacyBatchTrackSpec.Endpoint other = endpointSpec.endpoints().get(0);
            if (other.part() == endpoint.part()) {
                other = endpointSpec.endpoints().get(1);
            }

            BlockPos current = endpointSpec.endpointPosition(
                    root, facing, endpoint);
            BlockPos inside = endpointSpec.endpointPosition(
                    root, facing, other);
            int outwardDx = current.getX() - inside.getX();
            int outwardDz = current.getZ() - inside.getZ();
            if (outwardDx != 0 || outwardDz != 0) {
                return RouteHeading8.fromStep(outwardDx, outwardDz);
            }
        }

        return RouteHeading8.fromDirection(
                placementEndpointOutward(endpointSpec, facing, endpoint));
    }

    private static RouteHeading8 originalDiagonalCrossingEndpointHeading(
            LegacyBatchTrackSpec crossingSpec,
            BlockPos root,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        int routeIndex = originalDiagonalCrossingRouteIndex(
                crossingSpec, endpoint.part());
        if (routeIndex < 0) {
            return null;
        }

        LegacyBatchTrackSpec.Endpoint partner = null;
        for (LegacyBatchTrackSpec.Endpoint candidate : crossingSpec.endpoints()) {
            if (candidate.part() == endpoint.part()) {
                continue;
            }
            if (originalDiagonalCrossingRouteIndex(
                    crossingSpec, candidate.part()) == routeIndex) {
                partner = candidate;
                break;
            }
        }
        if (partner == null) {
            return null;
        }

        BlockPos current = crossingSpec.endpointPosition(root, facing, endpoint);
        BlockPos opposite = crossingSpec.endpointPosition(root, facing, partner);
        int dx = current.getX() - opposite.getX();
        int dz = current.getZ() - opposite.getZ();
        if (dx == 0 || dz == 0) {
            return null;
        }
        return RouteHeading8.fromStep(dx, dz);
    }

    private static int originalDiagonalCrossingRouteIndex(
            LegacyBatchTrackSpec crossingSpec, int endpointPart) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int part = 0; part < crossingSpec.partCount(); part++) {
            LegacyBatchTrackSpec.Point point = crossingSpec.pointForPart(part);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }

        LegacyBatchTrackSpec.Point point = crossingSpec.pointForPart(endpointPart);
        if ((point.x == minX && point.z == maxZ)
                || (point.x == maxX && point.z == minZ)) {
            return 0;
        }
        if ((point.x == maxX && point.z == maxZ)
                || (point.x == minX && point.z == minZ)) {
            return 1;
        }
        return -1;
    }

    private static RouteHeading8 rotateRouteHeading(RouteHeading8 canonical,
                                                     Direction facing) {
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException(
                    "Horizontal facing required: " + facing);
        };

        RouteHeading8 result = canonical;
        for (int i = 0; i < turns; i++) {
            result = result.rotateClockwise();
        }
        return result;
    }

    /**
     * Original TC4.5 shared-gag placement, with modern t8 logical ownership
     * kept separate from the original physical/render root.
     *
     * Canonical south-facing Medium Left 45:
     *   curve TileTCRail = {1,2}; switch gags = {1,3},{1,4},{2,3}.
     *   Small Diagonal physical root = {2,4}; shared gags = {1,4},{2,3}.
     *   Medium Diagonal physical root = {4,6}; its rear gag footprint also
     *   shares {1,4},{2,3} without replacing any real switch TileTCRail.
     * Medium Right 45 is the exact X-mirror: {-2,4} and {-4,6}.
     *
     * t8 logical movement ownership remains part 0 in the first shared-proxy
     * cell. The blockstate moves only the full visual mesh to part 2 (Small) or
     * part 6 (Medium).
     */
    // STEP_9_3D_T9_ORIGINAL_DIAGONAL_VISUAL_OWNERSHIP
    // Step 9.3e-t4-r5 keeps the centered crossing and the Small's restored OG
    // physical footprint untouched. For crossing -> Small only, the visible
    // mesh is shifted to Small part 2 because that gag cell is the actual
    // diagonal continuation cell. Medium45 ownership remains unchanged.
    private boolean isOriginalDiagonalVisualOwnership(Connector connector,
                                                       Candidate chosen) {
        if (!isDiagonalStraightFamily(spec.id())
                || connector == null
                || chosen == null
                || !connector.routeHeading.isDiagonal()) {
            return false;
        }

        // STEP_9_3E_T4_R5_CROSSING_SMALL_DIAGONAL_VISUAL_OWNER
        //
        // t4-r4 now places the restored TC4.5 Small L-footprint correctly:
        // the crossing-adjacent real/root cell is p0, while p2 is the second
        // invisible gag cell at the actual DIAGONAL continuation location.
        //
        // The F3 proof on the west side was:
        //   p0 = (127,137,-430)
        //   p2 = (127,137,-431)
        // The crossing route continues through the p2 diagonal cell, so keep
        // every placement/movement cell exactly where r4 put it, but render
        // the full Small OBJ from p2 via the already-existing
        // original_visual_root blockstate.
        if (TARGET_DIAGONAL_CROSSING.equals(connector.sourceId)
                && "track_diagonal_straight_small".equals(spec.id())) {
            return chosen.endpointPart == 0;
        }

        // Preserve the already-accepted Medium-Diagonal shared-gag visual-root
        // behavior for Medium45 unchanged. Small/Medium45 remains exactly as
        // frozen by t4-r3; this patch is crossing -> Small only.
        if (isMedium45SwitchSource(connector.sourceId)) {
            if ("track_diagonal_straight_small".equals(spec.id())) {
                return false;
            }
            return chosen.endpointPart == 0
                    && chosen.root.equals(
                            connector.segment.relative(connector.outward));
        }

        return false;
    }

    private int originalDiagonalVisualPart() {
        if ("track_diagonal_straight_small".equals(spec.id())) {
            return 2;
        }
        if ("track_diagonal_straight_medium".equals(spec.id())) {
            return 6;
        }
        throw new IllegalStateException(
                "No TC4.5 diagonal visual part for " + spec.id());
    }

    /**
     * TC4.5 diagonal metadata is an actual 8-way facing, not a cardinal
     * surrogate. The visual TileTCRail at the far physical root points back
     * toward the switch, i.e. opposite the switch branch travel heading.
     *
     * Original metadata: 4=SW, 5=NW, 6=NE, 7=SE.
     */
    private static int originalDiagonalVisualFacing(Connector connector) {
        RouteHeading8 towardSwitch = connector.routeHeading.opposite();
        return switch (towardSwitch) {
            case SOUTH_WEST -> 4;
            case NORTH_WEST -> 5;
            case NORTH_EAST -> 6;
            case SOUTH_EAST -> 7;
            default -> throw new IllegalStateException(
                    "TC4.5 diagonal visual facing requires a diagonal route: "
                            + connector.routeHeading);
        };
    }

    private static BlockState stateForPlacement(
            AbstractLegacyBatchRailBlock block,
            Direction facing,
            int part,
            boolean active,
            boolean originalVisualRootOwnership) {
        if (block instanceof LegacyBatchRailBlock legacyBatch) {
            BlockState state = legacyBatch.stateForPart(
                    facing, part, active, originalVisualRootOwnership);
            return applySmallSwitchP2GuideRotation(
                    state, legacyBatch.getSpec().id(), facing, part);
        }

        // STEP_9_3F_T1A_R5_SWITCH_SUBCLASS_PLACEMENT_PATH
        //
        // r4 runtime F3 proof showed Small-switch p2 still EAST_WEST after the
        // assemblies were broken and replaced. The reason is now source-proven:
        // r4 applied the rotation only inside the LegacyBatchRailBlock branch.
        // Switch segments use the generic AbstractLegacyBatchRailBlock placement
        // branch below, so the helper was never called for them.
        //
        // Apply the same narrow Small Left/Right p2 correction to that generic
        // branch. All non-Small-switch states return byte-for-byte equivalent
        // values because applySmallSwitchP2GuideRotation() is a no-op for them.
        BlockState state = block.stateForPart(facing, part, active);
        return applySmallSwitchP2GuideRotation(
                state, block.getSpec().id(), facing, part);
    }

    // STEP_9_3F_T1A_R4_SMALL_SWITCH_P2_GUIDE_ROTATION
    //
    // r3 runtime proof reaches the corrected p2 endpoint, but the actual p2
    // proxy rail is still stored with the old crosswise shape. On the user's
    // east/west-facing Small switches that appears as an EAST_WEST rail across
    // a NORTH_SOUTH outside mainline. As soon as root-owned movement releases,
    // vanilla minecart logic follows that crosswise proxy and turns sideways.
    //
    // Rotate ONLY Small Left/Right part 2 to the real outside-mainline tangent.
    // Canonically the p5<->p2 mainline is EAST_WEST; normal facing rotation then
    // produces NORTH_SOUTH for the east/west-facing test switches shown in the
    // screenshot. p4 remains untouched because it belongs to the visible branch.
    private static BlockState applySmallSwitchP2GuideRotation(
            BlockState state,
            String specId,
            Direction facing,
            int part) {
        if (part != 2
                || (!"track_switch_small_left".equals(specId)
                && !"track_switch_small_right".equals(specId))) {
            return state;
        }

        RailShape mainline = rotateCanonicalRailShape(
                RailShape.EAST_WEST, facing);
        if (state.hasProperty(RailBlock.SHAPE)) {
            return state.setValue(RailBlock.SHAPE, mainline);
        }
        return state;
    }

    private static RailShape rotateCanonicalRailShape(
            RailShape canonical,
            Direction facing) {
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException(
                    "Horizontal facing required: " + facing);
        };

        RailShape result = canonical;
        for (int i = 0; i < turns; i++) {
            result = switch (result) {
                case NORTH_SOUTH -> RailShape.EAST_WEST;
                case EAST_WEST -> RailShape.NORTH_SOUTH;
                case SOUTH_EAST -> RailShape.SOUTH_WEST;
                case SOUTH_WEST -> RailShape.NORTH_WEST;
                case NORTH_WEST -> RailShape.NORTH_EAST;
                case NORTH_EAST -> RailShape.SOUTH_EAST;
                case ASCENDING_NORTH -> RailShape.ASCENDING_EAST;
                case ASCENDING_EAST -> RailShape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> RailShape.ASCENDING_WEST;
                case ASCENDING_WEST -> RailShape.ASCENDING_NORTH;
            };
        }
        return result;
    }

    private static BlockPos requiredEndpointCellFor(String incomingId,
                                                     Connector connector) {
        if (isOriginalEightWaySnapPair(incomingId, connector.sourceId)
                && connector.routeHeading.isDiagonal()) {
            return connector.segment.relative(connector.outward);
        }
        return connector.segment.relative(connector.outward);
    }

    private static boolean isOriginalEightWaySnapPair(String incomingId,
                                                       String sourceId) {
        return isMedium45SwitchSource(sourceId)
                && isDiagonalStraightFamily(incomingId);
    }

    private static boolean isOriginalDiagonalCrossingSnapPair(
            String incomingId, String sourceId) {
        if (TARGET_DIAGONAL_CROSSING.equals(sourceId)) {
            return isDiagonalStraightFamily(incomingId)
                    || isMedium45SwitchSource(incomingId);
        }
        if (TARGET_DIAGONAL_CROSSING.equals(incomingId)) {
            return isDiagonalStraightFamily(sourceId)
                    || isMedium45SwitchSource(sourceId);
        }
        return false;
    }

    private static boolean isMedium45SwitchSource(String sourceId) {
        return TARGET_45_MEDIUM_LEFT_SWITCH.equals(sourceId)
                || TARGET_45_MEDIUM_RIGHT_SWITCH.equals(sourceId);
    }

    private static boolean isDiagonalStraightFamily(String id) {
        return "track_diagonal_straight_small".equals(id)
                || "track_diagonal_straight_medium".equals(id);
    }

    private static Connector connectorAt(BlockPos segment,
                                         Direction outward,
                                         Vec3 click,
                                         String sourceId) {
        return connectorAt(
                segment, outward, RouteHeading8.fromDirection(outward),
                click, sourceId);
    }

    private static Connector connectorAt(BlockPos segment,
                                         Direction outward,
                                         RouteHeading8 routeHeading,
                                         Vec3 click,
                                         String sourceId) {
        double connectorX =
                segment.getX() + 0.5D + outward.getStepX() * 0.5D;
        double connectorY =
                segment.getY() + 0.0625D;
        double connectorZ =
                segment.getZ() + 0.5D + outward.getStepZ() * 0.5D;

        double dx = click.x - connectorX;
        double dz = click.z - connectorZ;
        return new Connector(
                segment, outward, routeHeading,
                dx * dx + dz * dz, sourceId);
    }

    private static String formatConnector(Connector connector) {
        if (connector == null) {
            return "NONE";
        }
        return "sourceId=" + connector.sourceId
                + ",segment=" + connector.segment
                + ",outward=" + connector.outward
                + ",routeHeading=" + connector.routeHeading
                + ",requiredCell=" + connector.segment.relative(connector.outward)
                + ",distance=" + Math.sqrt(connector.distanceSq);
    }

    private static Connector nearerConnector(Connector current,
                                             Connector candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.distanceSq < current.distanceSq
                ? candidate
                : current;
    }

    /**
     * Step 9.3c-t4: legacy diagonal families use Manhattan guide cells to
     * approximate a visually diagonal route. Their intended handoff can
     * therefore meet a perpendicular endpoint one cell away. Ordinary track
     * keeps the stricter face-to-face rule.
     */
    private static boolean isDiagonalConnectorFamily(String id) {
        if (id == null) {
            return false;
        }
        return id.contains("diagonal")
                || id.contains("diamond")
                || id.contains("universal")
                || id.contains("_45_")
                || id.startsWith("track_curve_45");
    }

    private static String connectorSourceId(BlockState state) {
        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            return batch.getSpec().id();
        }
        if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
            return "legacy_medium_curve";
        }
        if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock) {
            return "legacy_straight";
        }
        if (state.getBlock() instanceof BaseRailBlock) {
            return "vanilla_rail";
        }
        return null;
    }

    private static List<Direction> openDirections(Level level,
                                                  BlockPos pos,
                                                  BlockState state) {
        List<Direction> out = new ArrayList<>();

        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            int part = batch.getPart(state);
            Direction facing = batch.getAssemblyFacing(state);

            BlockPos root = batch.getAssemblyRoot(pos, state);
            for (LegacyBatchTrackSpec.Endpoint endpoint :
                    batch.getSpec().endpoints()) {
                if (endpoint.part() != part) {
                    continue;
                }

                BlockPos logicalSegment = batch.getSpec().endpointPosition(
                        root, facing, endpoint);
                if (!logicalSegment.equals(pos)) {
                    continue;
                }

                Direction outward =
                        placementEndpointOutward(
                                batch.getSpec(), facing, endpoint);
                if (!hasRailNeighbor(level, logicalSegment, outward)) {
                    out.add(outward);
                }
            }

            return out;
        }

        if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
            int part = state.getValue(LegacyMediumCurveRailBlock.PART);
            Direction facing =
                    state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            int last = LegacyMediumCurveRailBlock.PART_COUNT - 1;

            if (part == 0) {
                Direction outward = facing.getCounterClockWise();
                if (!hasRailNeighbor(level, pos, outward)) {
                    out.add(outward);
                }
            } else if (part == last) {
                Direction outward = facing.getOpposite();
                if (!hasRailNeighbor(level, pos, outward)) {
                    out.add(outward);
                }
            }

            return out;
        }

        if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock straight) {
            int part = state.getValue(LegacyStraightAssemblyRailBlock.PART);
            Direction facing =
                    state.getValue(LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
            int last = straight.getAssemblyLength() - 1;

            if (part == 0) {
                Direction outward = facing.getOpposite();
                if (!hasRailNeighbor(level, pos, outward)) {
                    out.add(outward);
                }
            } else if (part == last) {
                Direction outward = facing;
                if (!hasRailNeighbor(level, pos, outward)) {
                    out.add(outward);
                }
            }

            return out;
        }

        if (state.getBlock() instanceof BaseRailBlock rail) {
            RailShape shape;
            try {
                shape = rail.getRailDirection(state, level, pos, null);
            } catch (RuntimeException ex) {
                return out;
            }

            for (Direction direction : directionsFor(shape)) {
                if (!hasRailNeighbor(level, pos, direction)) {
                    out.add(direction);
                }
            }
        }

        return out;
    }

    private static List<Direction> directionsFor(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH ->
                    List.of(Direction.NORTH, Direction.SOUTH);
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST ->
                    List.of(Direction.WEST, Direction.EAST);
            case NORTH_EAST ->
                    List.of(Direction.NORTH, Direction.EAST);
            case NORTH_WEST ->
                    List.of(Direction.NORTH, Direction.WEST);
            case SOUTH_EAST ->
                    List.of(Direction.SOUTH, Direction.EAST);
            case SOUTH_WEST ->
                    List.of(Direction.SOUTH, Direction.WEST);
        };
    }

    private static boolean hasRailNeighbor(Level level,
                                           BlockPos pos,
                                           Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (BaseRailBlock.isRail(level.getBlockState(neighbor))) {
            return true;
        }
        if (BaseRailBlock.isRail(level.getBlockState(neighbor.above()))) {
            return true;
        }
        return BaseRailBlock.isRail(level.getBlockState(neighbor.below()));
    }
}
