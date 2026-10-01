package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class LightweightEnergyTrackNetwork {
    public static final int POWERED_RUN_LENGTH = 20;

    private record Node(BlockPos pos, int distance) {}

    private LightweightEnergyTrackNetwork() {}

    public static boolean isPowered(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // r2g2c: lightweight/small Energy is the only Energy rail family.

if (!isLightweightEnergy(level, pos)) {
            return false;
        }

        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos start = pos.immutable();
        queue.add(new Node(start, 0));
        visited.add(start);

        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();

            if (level.hasNeighborSignal(node.pos())) {
                return true;
            }

            if (node.distance() >= POWERED_RUN_LENGTH - 1) {
                continue;
            }

            for (BlockPos next : neighbors(node.pos())) {
                BlockPos immutable = next.immutable();
                if (visited.add(immutable) && isLightweightEnergy(level, immutable)) {
                    queue.addLast(new Node(immutable, node.distance() + 1));
                }
            }
        }

        return false;
    }

    public static boolean isEnergy(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isLightweightEnergy(level, pos);
    }

    // TC_BATCH8_R4A3F_BASIC_ENERGY_PARITY
    public static boolean isLightweightEnergy(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos root = null;

        if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            root = batch.getAssemblyRoot(pos, state);
        }
        else if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock) {
            Direction facing =
                    state.getValue(LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
            int part =
                    state.getValue(LegacyStraightAssemblyRailBlock.PART);
            root = pos.relative(facing.getOpposite(), part);
        }
        else if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
            int part = state.getValue(LegacyMediumCurveRailBlock.PART);
            if (part < 0 || part >= LegacyMediumCurveRailBlock.PART_COUNT) {
                return false;
            }
            Direction facing =
                    state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            BlockPos offset =
                    LegacyMediumCurveRailBlock.offsetForPart(
                            BlockPos.ZERO, facing, part);
            root = pos.subtract(offset);
        }
        else if (state.getBlock()
                == traincraft.registry.TCBlocks.TRACK_NORMAL.get()) {
            root = pos;
        }

        if (root == null) {
            return false;
        }

        BlockEntity entity = level.getBlockEntity(root);
        return entity instanceof TrackMaterialBlockEntity materialData
                && materialData.getMaterial() == TrackMaterial.ENERGY;
    }


    private static Set<BlockPos> neighbors(BlockPos pos) {
        Set<BlockPos> out = new HashSet<>();

        // Cardinal guide cells.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            out.add(pos.relative(direction));
            out.add(pos.relative(direction).above());
            out.add(pos.relative(direction).below());
        }

        // Slopes.
        out.add(pos.above());
        out.add(pos.below());

        // Restored 45-degree/diagonal families use Manhattan guide cells, but
        // endpoint handoffs can legitimately land on a diagonal neighbor.
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                out.add(pos.offset(dx, 0, dz));
                out.add(pos.offset(dx, 1, dz));
                out.add(pos.offset(dx, -1, dz));
            }
        }

        return out;
    }
}
