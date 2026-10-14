package traincraft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;
import traincraft.block.track.LightweightEnergyTrackNetwork;

/**
 * DEV38 r1f2c Train Buffer.
 *
 * One public stopper item is used for both Normal and Energy track ends. The
 * block automatically selects its Energy visual when any horizontal adjacent
 * rail belongs to the accepted lightweight Energy material family. No train
 * movement/path/coupler code is involved.
 */
public final class TrainBufferBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ENERGY = BooleanProperty.create("energy");

    public TrainBufferBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENERGY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENERGY);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction fallback = context.getHorizontalDirection().getOpposite();
        Direction facing = facingTowardAdjacentRail(level, pos, fallback);
        boolean energy = hasAdjacentEnergy(level, pos);
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ENERGY, energy);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) return;

        boolean energy = hasAdjacentEnergy(level, pos);
        if (state.getValue(ENERGY) != energy) {
            level.setBlock(pos, state.setValue(ENERGY, energy), 3);
        }
    }

    private static Direction facingTowardAdjacentRail(Level level, BlockPos pos, Direction fallback) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.getBlock() instanceof BaseRailBlock) {
                return direction;
            }
        }
        return fallback;
    }

    private static boolean hasAdjacentEnergy(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (LightweightEnergyTrackNetwork.isEnergy(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }
}
