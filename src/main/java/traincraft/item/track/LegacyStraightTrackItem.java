package traincraft.item.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.block.track.LegacyStraightAssemblyRailBlock;
import traincraft.registry.TCBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 9.2a - legacy Traincraft multi-length straight placement.
 *
 * Original Traincraft used assembled straight pieces sized 1x3, 1x6 and 1x12.
 * This 1.20.1 foundation preserves that one-click placement behavior while
 * using the runtime-confirmed traincraft:track_normal RailBlock for each
 * traversable block. No locomotive/coupling physics are modified here.
 *
 * Step 9.3c-t4 refuses remote endpoint snapping when the player clicked the
 * middle of a multi-endpoint assembly; only an endpoint connector within the
 * same local click radius may capture the placement.
 */
public final class LegacyStraightTrackItem extends Item {
    private static final double CONNECTOR_RADIUS = 0.90D;

    // TC_BATCH8_R3ZI_STRAIGHT_NEAR_END_CAPTURE
    // Natural placement clicks can be several blocks from the hidden guide end
    // of a legacy curve. Acquisition may search this far, but the straight is
    // always relocated onto the actual OPEN endpoint before any block is placed.
    private static final double NEAR_END_CAPTURE_RADIUS = 4.25D;
    private static final int NEAR_END_SCAN_RADIUS = 6;

    private final int length;
    // TC_R2F3C_CLASSIC_STRAIGHT_MATERIAL
    private final traincraft.block.track.TrackMaterial material;

    public LegacyStraightTrackItem(Properties properties, int length) {
        this(properties, length, traincraft.block.track.TrackMaterial.NORMAL);
    }

    public LegacyStraightTrackItem(Properties properties,
                                   int length,
                                   traincraft.block.track.TrackMaterial material) {
        super(properties);
        if (length < 2) {
            throw new IllegalArgumentException("Legacy straight length must be >= 2");
        }
        this.length = length;
        this.material = material == null
                ? traincraft.block.track.TrackMaterial.NORMAL
                : material;
    }

    // DEV58B8R3B: ghost and native item use the identical physical start/connector.
    public record PlacementPreview(BlockPos start, Direction forward) {}

    public PlacementPreview previewPlacement(Level level, Player player,
            ItemStack stack, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (level == null || player == null || stack == null || hit == null) return null;
        boolean clickedRail = BaseRailBlock.isRail(level.getBlockState(hit.getBlockPos()));
        if (hit.getDirection() != Direction.UP && !tcIsPlantCell(level, hit.getBlockPos())
                && !clickedRail) return null;
        UseOnContext context = new UseOnContext(player, hand, hit);
        BlockPos start = clickedRail ? hit.getBlockPos()
                : tcManualAnchor(level, hit.getBlockPos());
        Direction forward = resolvePlacementForward(level, start, player.getDirection());
        SnapTarget snap = findSnapTarget(level, context);
        if (snap != null) {
            forward = snap.outward;
            start = snap.segment.relative(snap.outward);
        }
        // G12R3: return the intended footprint even if blocked, for a red
        // ghost. Native useOn() still prevents occupied-cell replacement.
        return new PlacementPreview(start, forward);
    }

    private static boolean tcIsPlantCell(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return TrackPlacementVegetation.isPlant(state);
    }

    private static BlockPos tcManualAnchor(Level level, BlockPos clicked) {
        return tcIsPlantCell(level, clicked) ? clicked : clicked.above();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        // DEV58G12R2: the preview already allows rail side-clicks.  Native
        // placement must honor that exact same targeting contract.
        if (context.getClickedFace() != Direction.UP
                && !tcIsPlantCell(level, context.getClickedPos())
                && !BaseRailBlock.isRail(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos start = tcManualAnchor(level, context.getClickedPos());
        Direction forward = resolvePlacementForward(
                level, start, player.getDirection());
        ItemStack stack = context.getItemInHand();

        // Step 9.3b-t4: S/M/L straights now understand the same assembly
        // endpoints as the batch-track placer. Clicking any owned guide cell
        // on a switch/curve/straight extends from the nearest OPEN endpoint.
        SnapTarget snap = findSnapTarget(level, context);
        if (snap != null) {
            forward = snap.outward;
            start = snap.segment.relative(snap.outward);
        } else if (BaseRailBlock.isRail(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.FAIL;
        }

        // Validate the complete footprint before changing the world.
        for (int i = 0; i < length; i++) {
            BlockPos railPos = start.relative(forward, i);
            BlockPos supportPos = railPos.below();

            BlockState existing = level.getBlockState(railPos);
            if (!TrackPlacementVegetation.canReplace(existing)) {
                return InteractionResult.FAIL;
            }

            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
                return InteractionResult.FAIL;
            }

            if (!player.mayUseItemAt(railPos, Direction.UP, stack)) {
                return InteractionResult.FAIL;
            }
        }

        RailShape shape = forward.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;

        traincraft.block.track.LegacyStraightAssemblyRailBlock assemblyBlock;
        if (length == 3) {
            assemblyBlock = (traincraft.block.track.LegacyStraightAssemblyRailBlock)
                    TCBlocks.TRACK_MEDIUM_STRAIGHT_SEGMENT.get();
        }
        else if (length == 6) {
            assemblyBlock = (traincraft.block.track.LegacyStraightAssemblyRailBlock)
                    TCBlocks.TRACK_LONG_STRAIGHT_SEGMENT.get();
        }
        else if (length == 12) {
            assemblyBlock = (traincraft.block.track.LegacyStraightAssemblyRailBlock)
                    TCBlocks.TRACK_VERY_LONG_STRAIGHT_SEGMENT.get();
        }
        else {
            return InteractionResult.FAIL;
        }

        // R7: never clear vegetation until every track cell validates.
        for (int i = 0; i < length; i++) {
            BlockPos plantPos = start.relative(forward, i);
            if (TrackPlacementVegetation.isPlant(level.getBlockState(plantPos))) {
                level.destroyBlock(plantPos, !player.getAbilities().instabuild, player);
            }
        }
        for (int i = 0; i < length; i++) {
            BlockPos railPos = start.relative(forward, i);

            BlockState railState = assemblyBlock.defaultBlockState()
                    .setValue(RailBlock.SHAPE, shape)
                    .setValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING, forward)
                    .setValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART, i);

            level.setBlock(railPos, railState, 3);
        }

        // TC_R2F3C_CLASSIC_STRAIGHT_MATERIAL
        // TC_R2F3E_SEGMENT_MODELDATA
        // Mirror the assembly material to every visible segment so the shared
        // child model receives the correct Forge ModelData on every block.
        // PART 0 at 'start' remains the authoritative assembly root.
        for (int i = 0; i < length; i++) {
            BlockPos materialPos = start.relative(forward, i);
            net.minecraft.world.level.block.entity.BlockEntity materialEntity =
                    level.getBlockEntity(materialPos);

            if (materialEntity instanceof traincraft.block.track.TrackMaterialBlockEntity materialData) {
                materialData.setMaterial(material);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(
                null,
                start,
                SoundEvents.METAL_PLACE,
                SoundSource.BLOCKS,
                0.8F,
                1.0F
        );

        return InteractionResult.CONSUME;
    }
    private static final class SnapTarget {
        private final BlockPos segment;
        private final Direction outward;
        private final double distanceSq;

        private SnapTarget(BlockPos segment, Direction outward, double distanceSq) {
            this.segment = segment;
            this.outward = outward;
            this.distanceSq = distanceSq;
        }
    }

    private static SnapTarget findSnapTarget(Level level, UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        Vec3 click = context.getClickLocation();

        if (!BaseRailBlock.isRail(state)) {
            BlockPos requestedCell = tcManualAnchor(level, clicked);
            SnapTarget nearby = findNearbyOpenEndpoint(
                    level, requestedCell, click);
            // DEV58G12R2: broad endpoint acquisition must not steal a ground
            // click laterally beside an existing railway.  Continuations stay
            // available on the endpoint's *forward axis* up to the old radius.
            if (nearby != null && !tcOnOutwardAxis(nearby, requestedCell)) {
                nearby = null;
            }
            if (nearby != null) {
                traincraft.Traincraft.LOGGER.info(
                        "[TC-R3ZI-STRAIGHT-CAPTURE] requested={} sourceSegment={} outward={} distance={}",
                        clicked.above(),
                        nearby.segment,
                        nearby.outward,
                        Math.sqrt(nearby.distanceSq));
            }
            return nearby;
        }

        SnapTarget best = null;

        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            BlockPos root = batch.getAssemblyRoot(clicked, state);
            Direction facing = batch.getAssemblyFacing(state);

            for (LegacyBatchTrackSpec.Endpoint endpoint : batch.getSpec().endpoints()) {
                BlockPos segment = batch.getSpec().offsetForPart(
                        root, facing, endpoint.part());
                Direction outward = batch.getSpec().rotateDirection(
                        facing, endpoint.outward());
                if (!hasRailNeighbor(level, segment, outward)) {
                    best = nearer(best, targetAt(segment, outward, click));
                }
            }
            return withinSnapRadius(best);
        }

        if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
            int part = state.getValue(LegacyMediumCurveRailBlock.PART);
            Direction facing = state.getValue(
                    LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            BlockPos root = clicked.subtract(
                    LegacyMediumCurveRailBlock.offsetForPart(
                            BlockPos.ZERO, facing, part));

            BlockPos first = LegacyMediumCurveRailBlock.offsetForPart(root, facing, 0);
            Direction firstOut = facing.getCounterClockWise();
            if (!hasRailNeighbor(level, first, firstOut)) {
                best = nearer(best, targetAt(first, firstOut, click));
            }

            int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
            BlockPos last = LegacyMediumCurveRailBlock.offsetForPart(root, facing, lastPart);
            Direction lastOut = facing.getOpposite();
            if (!hasRailNeighbor(level, last, lastOut)) {
                best = nearer(best, targetAt(last, lastOut, click));
            }
            return withinSnapRadius(best);
        }

        if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock straight) {
            int part = state.getValue(LegacyStraightAssemblyRailBlock.PART);
            Direction facing = state.getValue(
                    LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
            BlockPos root = clicked.relative(facing.getOpposite(), part);

            Direction firstOut = facing.getOpposite();
            if (!hasRailNeighbor(level, root, firstOut)) {
                best = nearer(best, targetAt(root, firstOut, click));
            }

            BlockPos last = root.relative(facing, straight.getAssemblyLength() - 1);
            Direction lastOut = facing;
            if (!hasRailNeighbor(level, last, lastOut)) {
                best = nearer(best, targetAt(last, lastOut, click));
            }
            return withinSnapRadius(best);
        }

        if (state.getBlock() instanceof BaseRailBlock rail) {
            RailShape shape;
            try {
                shape = rail.getRailDirection(state, level, clicked, null);
            } catch (RuntimeException ex) {
                return null;
            }

            for (Direction outward : directionsFor(shape)) {
                if (!hasRailNeighbor(level, clicked, outward)) {
                    best = nearer(best, targetAt(clicked, outward, click));
                }
            }
        }

        return withinSnapRadius(best);
    }

    // DEV58G12R2: forward-only ground capture; never treat a side-by-side
    // parallel alignment as a request to extend the adjacent rail.
    private static boolean tcOnOutwardAxis(SnapTarget target, BlockPos requested) {
        if (target == null || requested == null) return false;
        int dx = requested.getX() - target.segment.getX();
        int dz = requested.getZ() - target.segment.getZ();
        int longitudinal = dx * target.outward.getStepX()
                + dz * target.outward.getStepZ();
        int lateral = dx * target.outward.getStepZ()
                - dz * target.outward.getStepX();
        return longitudinal >= 1 && lateral == 0
                && Math.abs(requested.getY() - target.segment.getY()) <= 1;
    }

    private static SnapTarget findNearbyOpenEndpoint(
            Level level,
            BlockPos requestedCell,
            Vec3 click) {
        SnapTarget best = null;
        java.util.HashSet<String> seenAssemblies = new java.util.HashSet<>();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -NEAR_END_SCAN_RADIUS;
                 dx <= NEAR_END_SCAN_RADIUS; dx++) {
                for (int dz = -NEAR_END_SCAN_RADIUS;
                     dz <= NEAR_END_SCAN_RADIUS; dz++) {

                    BlockPos probe = requestedCell.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);
                    if (!BaseRailBlock.isRail(state)) {
                        continue;
                    }

                    if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
                        BlockPos root = batch.getAssemblyRoot(probe, state);
                        Direction facing = batch.getAssemblyFacing(state);
                        String key = "batch|" + batch.getSpec().id()
                                + "|" + root.asLong()
                                + "|" + facing.ordinal();
                        if (!seenAssemblies.add(key)) {
                            continue;
                        }

                        for (LegacyBatchTrackSpec.Endpoint endpoint
                                : batch.getSpec().endpoints()) {
                            BlockPos segment = batch.getSpec().endpointPosition(
                                    root, facing, endpoint);
                            Direction outward = batch.getSpec().rotateDirection(
                                    facing, endpoint.outward());

                            if (hasRailNeighbor(level, segment, outward)) {
                                continue;
                            }

                            SnapTarget candidate = targetAt(
                                    segment, outward, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }
                        continue;
                    }

                    if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
                        int part = state.getValue(LegacyMediumCurveRailBlock.PART);
                        Direction facing = state.getValue(
                                LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                        BlockPos root = probe.subtract(
                                LegacyMediumCurveRailBlock.offsetForPart(
                                        BlockPos.ZERO, facing, part));
                        String key = "medium|" + root.asLong()
                                + "|" + facing.ordinal();
                        if (!seenAssemblies.add(key)) {
                            continue;
                        }

                        BlockPos first = LegacyMediumCurveRailBlock.offsetForPart(
                                root, facing, 0);
                        Direction firstOut = facing.getCounterClockWise();
                        if (!hasRailNeighbor(level, first, firstOut)) {
                            SnapTarget candidate = targetAt(
                                    first, firstOut, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }

                        int lastPart =
                                LegacyMediumCurveRailBlock.PART_COUNT - 1;
                        BlockPos last = LegacyMediumCurveRailBlock.offsetForPart(
                                root, facing, lastPart);
                        Direction lastOut = facing.getOpposite();
                        if (!hasRailNeighbor(level, last, lastOut)) {
                            SnapTarget candidate = targetAt(
                                    last, lastOut, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }
                        continue;
                    }

                    if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock straight) {
                        int part = state.getValue(
                                LegacyStraightAssemblyRailBlock.PART);
                        Direction facing = state.getValue(
                                LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
                        BlockPos root = probe.relative(
                                facing.getOpposite(), part);
                        String key = "straight|" + root.asLong()
                                + "|" + facing.ordinal()
                                + "|" + straight.getAssemblyLength();
                        if (!seenAssemblies.add(key)) {
                            continue;
                        }

                        Direction firstOut = facing.getOpposite();
                        if (!hasRailNeighbor(level, root, firstOut)) {
                            SnapTarget candidate = targetAt(
                                    root, firstOut, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }

                        BlockPos last = root.relative(
                                facing, straight.getAssemblyLength() - 1);
                        Direction lastOut = facing;
                        if (!hasRailNeighbor(level, last, lastOut)) {
                            SnapTarget candidate = targetAt(
                                    last, lastOut, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }
                        continue;
                    }

                    if (state.getBlock() instanceof BaseRailBlock rail) {
                        RailShape shape;
                        try {
                            shape = rail.getRailDirection(
                                    state, level, probe, null);
                        } catch (RuntimeException ex) {
                            continue;
                        }

                        for (Direction outward : directionsFor(shape)) {
                            if (hasRailNeighbor(level, probe, outward)) {
                                continue;
                            }
                            SnapTarget candidate = targetAt(
                                    probe, outward, click);
                            if (candidate.distanceSq
                                    <= NEAR_END_CAPTURE_RADIUS
                                    * NEAR_END_CAPTURE_RADIUS) {
                                best = nearer(best, candidate);
                            }
                        }
                    }
                }
            }
        }

        return best;
    }
    private static SnapTarget withinSnapRadius(SnapTarget target) {
        if (target == null) {
            return null;
        }
        return target.distanceSq <= CONNECTOR_RADIUS * CONNECTOR_RADIUS
                ? target
                : null;
    }

    private static SnapTarget targetAt(BlockPos segment, Direction outward, Vec3 click) {
        double cx = segment.getX() + 0.5D + outward.getStepX() * 0.5D;
        double cz = segment.getZ() + 0.5D + outward.getStepZ() * 0.5D;
        double dx = click.x - cx;
        double dz = click.z - cz;
        return new SnapTarget(segment, outward, dx * dx + dz * dz);
    }

    private static SnapTarget nearer(SnapTarget current, SnapTarget candidate) {
        return current == null || candidate.distanceSq < current.distanceSq
                ? candidate : current;
    }

    private static List<Direction> directionsFor(RailShape shape) {
        List<Direction> out = new ArrayList<>(2);
        switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> {
                out.add(Direction.NORTH);
                out.add(Direction.SOUTH);
            }
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> {
                out.add(Direction.WEST);
                out.add(Direction.EAST);
            }
            case NORTH_EAST -> {
                out.add(Direction.NORTH);
                out.add(Direction.EAST);
            }
            case NORTH_WEST -> {
                out.add(Direction.NORTH);
                out.add(Direction.WEST);
            }
            case SOUTH_EAST -> {
                out.add(Direction.SOUTH);
                out.add(Direction.EAST);
            }
            case SOUTH_WEST -> {
                out.add(Direction.SOUTH);
                out.add(Direction.WEST);
            }
        }
        return out;
    }

    private static boolean hasRailNeighbor(Level level, BlockPos pos, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (BaseRailBlock.isRail(level.getBlockState(neighbor))) {
            return true;
        }
        if (BaseRailBlock.isRail(level.getBlockState(neighbor.above()))) {
            return true;
        }
        return BaseRailBlock.isRail(level.getBlockState(neighbor.below()));
    }


    // TC_BATCH8_R3ZE_STRAIGHT_ENDPOINT_CONTINUATION
    //
    // Free placement still follows player facing.  When the first cell of a
    // 3/6/12 classic straight is immediately beyond a live rail endpoint, the
    // existing rail owns the continuation direction instead.  This prevents a
    // straight placed after a curve from growing backward across the curve when
    // the player happens to face the opposite way.
    private static Direction resolvePlacementForward(
            Level level,
            BlockPos start,
            Direction fallback) {
        Direction resolved = null;

        for (Direction fromStartToSource : Direction.Plane.HORIZONTAL) {
            Direction sourceToStart = fromStartToSource.getOpposite();

            // dy -1..+1 lets a straight continue naturally from the top/bottom
            // end of a slope while keeping the new straight on the clicked Y.
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos sourcePos =
                        start.relative(fromStartToSource).offset(0, dy, 0);
                BlockState sourceState = level.getBlockState(sourcePos);

                if (!net.minecraft.world.level.block.BaseRailBlock.isRail(sourceState)
                        || !sourceState.hasProperty(RailBlock.SHAPE)) {
                    continue;
                }

                RailShape sourceShape =
                        sourceState.getValue(RailBlock.SHAPE);

                if (!railShapeConnectsToward(
                        sourceShape, sourceToStart)) {
                    continue;
                }

                if (resolved != null
                        && resolved != sourceToStart) {
                    // Junction/ambiguous neighborhood: preserve manual control
                    // rather than guessing a branch.
                    return fallback;
                }

                resolved = sourceToStart;
            }
        }

        return resolved == null ? fallback : resolved;
    }

    private static boolean railShapeConnectsToward(
            RailShape shape,
            Direction direction) {
        return switch (shape) {
            case NORTH_SOUTH,
                 ASCENDING_NORTH,
                 ASCENDING_SOUTH ->
                    direction == Direction.NORTH
                            || direction == Direction.SOUTH;

            case EAST_WEST,
                 ASCENDING_EAST,
                 ASCENDING_WEST ->
                    direction == Direction.EAST
                            || direction == Direction.WEST;

            case NORTH_EAST ->
                    direction == Direction.NORTH
                            || direction == Direction.EAST;

            case NORTH_WEST ->
                    direction == Direction.NORTH
                            || direction == Direction.WEST;

            case SOUTH_EAST ->
                    direction == Direction.SOUTH
                            || direction == Direction.EAST;

            case SOUTH_WEST ->
                    direction == Direction.SOUTH
                            || direction == Direction.WEST;
        };
    }}
