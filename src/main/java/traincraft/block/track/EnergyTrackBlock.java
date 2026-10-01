package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * TafariCraft - Trains Energy Track.
 *
 * One directly-redstone-powered Energy Track feeds a continuous connected
 * Energy Track run for 20 rail blocks total (the source + 19 connected rails).
 * It never accelerates vanilla minecarts; it only exposes line power to
 * TafariCraft electric rolling stock.
 */
public final class EnergyTrackBlock extends RailBlock {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final int POWERED_RUN_LENGTH = 20;

    private record SearchNode(BlockPos pos, int distance) {}

    public EnergyTrackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            refreshConnectedRun(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            refreshConnectedRun(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            for (BlockPos neighbor : neighborPositions(pos)) {
                if (isEnergy(level, neighbor)) {
                    refreshConnectedRun(level, neighbor);
                }
            }
        }
    }

    private static void refreshConnectedRun(Level level, BlockPos origin) {
        Set<BlockPos> candidates = connectedWithin(level, origin, POWERED_RUN_LENGTH * 2);

        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof EnergyTrackBlock)) {
                continue;
            }

            boolean shouldBePowered = hasDirectSourceWithinRun(level, pos);
            boolean current = state.getValue(POWERED);

            if (current != shouldBePowered) {
                level.setBlock(pos, state.setValue(POWERED, shouldBePowered), 2);
            }
        }
    }

    private static boolean hasDirectSourceWithinRun(Level level, BlockPos start) {
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos startPos = start.immutable();
        queue.add(new SearchNode(startPos, 0));
        visited.add(startPos);

        while (!queue.isEmpty()) {
            SearchNode node = queue.removeFirst();

            if (level.hasNeighborSignal(node.pos())) {
                return true;
            }

            // Source rail counts as block 1, so 19 steps away is rail 20.
            if (node.distance() >= POWERED_RUN_LENGTH - 1) {
                continue;
            }

            for (BlockPos next : neighborPositions(node.pos())) {
                BlockPos immutable = next.immutable();
                if (visited.add(immutable) && isEnergy(level, immutable)) {
                    queue.addLast(new SearchNode(immutable, node.distance() + 1));
                }
            }
        }

        return false;
    }

    private static Set<BlockPos> connectedWithin(Level level, BlockPos start, int maxDistance) {
        Set<BlockPos> out = new HashSet<>();

        if (!isEnergy(level, start)) {
            for (BlockPos neighbor : neighborPositions(start)) {
                if (isEnergy(level, neighbor)) {
                    out.addAll(connectedWithin(level, neighbor, maxDistance));
                }
            }
            return out;
        }

        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        BlockPos startPos = start.immutable();
        queue.add(new SearchNode(startPos, 0));
        out.add(startPos);

        while (!queue.isEmpty()) {
            SearchNode node = queue.removeFirst();
            if (node.distance() >= maxDistance) {
                continue;
            }

            for (BlockPos next : neighborPositions(node.pos())) {
                BlockPos immutable = next.immutable();
                if (isEnergy(level, immutable) && out.add(immutable)) {
                    queue.addLast(new SearchNode(immutable, node.distance() + 1));
                }
            }
        }

        return out;
    }

    private static boolean isEnergy(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof EnergyTrackBlock;
    }

    private static BlockPos[] neighborPositions(BlockPos pos) {
        return new BlockPos[] {
                pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
        };
    }
}