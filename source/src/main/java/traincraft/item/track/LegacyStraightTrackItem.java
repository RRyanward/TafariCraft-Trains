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

    private final int length;

    public LegacyStraightTrackItem(Properties properties, int length) {
        super(properties);
        if (length < 2) {
            throw new IllegalArgumentException("Legacy straight length must be >= 2");
        }
        this.length = length;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Direction forward = player.getDirection();
        BlockPos start = context.getClickedPos().above();
        ItemStack stack = context.getItemInHand();

        // Step 9.3b-t4: S/M/L straights now understand the same assembly
        // endpoints as the batch-track placer. Clicking any owned guide cell
        // on a switch/curve/straight extends from the nearest OPEN endpoint.
        SnapTarget snap = findSnapTarget(level, context);
        if (snap != null) {
            forward = snap.outward;
            start = snap.segment.relative(snap.outward);
        }

        // Validate the complete footprint before changing the world.
        for (int i = 0; i < length; i++) {
            BlockPos railPos = start.relative(forward, i);
            BlockPos supportPos = railPos.below();

            BlockState existing = level.getBlockState(railPos);
            if (!existing.isAir() && !existing.canBeReplaced()) {
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

        for (int i = 0; i < length; i++) {
            BlockPos railPos = start.relative(forward, i);

            BlockState railState = assemblyBlock.defaultBlockState()
                    .setValue(RailBlock.SHAPE, shape)
                    .setValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING, forward)
                    .setValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART, i);

            level.setBlock(railPos, railState, 3);
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
            return null;
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

}
