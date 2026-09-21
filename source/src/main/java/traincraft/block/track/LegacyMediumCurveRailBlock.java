package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Step 9.2b - internal guide rails for the original medium multi-block curve.
 * Five ordinary RailBlock samples trace the approximately 3x3 visual model,
 * while PART/FACING make the complete curve behave as one owned assembly.
 */
public final class LegacyMediumCurveRailBlock extends RailBlock {
    public static final int PART_COUNT = 5;

    // Keep 0..6 loadable while r3h is being runtime-tested so an r3f dev world
    // containing old seven-part test curves can still open. New placements use 0..4.
    public static final IntegerProperty PART = IntegerProperty.create("assembly_part", 0, 6);
    public static final DirectionProperty ASSEMBLY_FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SELECTION_SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final ResourceLocation DROP_ITEM =
            new ResourceLocation("traincraft", "track_medium_curve");
    private static final ThreadLocal<Boolean> INTERNAL_REMOVAL =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public LegacyMediumCurveRailBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(PART, 0)
                .setValue(ASSEMBLY_FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SELECTION_SHAPE;
    }

    /**
     * r3h: rolling stock in this port asks BaseRailBlock#getRailDirection directly.
     * Return the assembly-owned canonical shape instead of trusting RailBlock.SHAPE,
     * because vanilla neighbor negotiation can rewrite that property after placement.
     */
    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos,
                                      net.minecraft.world.entity.vehicle.AbstractMinecart cart) {
        if (state.getBlock() == this
                && state.hasProperty(PART)
                && state.hasProperty(ASSEMBLY_FACING)) {
            int part = state.getValue(PART);
            if (part >= 0 && part < PART_COUNT) {
                return shapeForPart(state.getValue(ASSEMBLY_FACING), part);
            }
        }
        return super.getRailDirection(state, level, pos, cart);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, ASSEMBLY_FACING);
    }

    public static BlockPos offsetForPart(BlockPos root, Direction facing, int part) {
        Direction right = facing.getClockWise();
        return switch (part) {
            // r3h: five guide blocks centered under the original ~3x3 mesh.
            // NORTH canonical positions relative to root:
            //   0=(+1,-3), 1=(+1,-2), 2=(+2,-2), 3=(+2,-1), 4=(+3,-1)
            case 0 -> root.relative(facing, 3).relative(right, 1);
            case 1 -> root.relative(facing, 3).relative(right, 2);
            case 2 -> root.relative(facing, 2).relative(right, 2);
            case 3 -> root.relative(facing, 2).relative(right, 3);
            case 4 -> root.relative(facing, 1).relative(right, 3);

            // r3f-load compatibility only. New r3h placement never creates parts 5/6.
            case 5 -> root.relative(right, 2);
            case 6 -> root.relative(right, 3);
            default -> throw new IllegalArgumentException("Invalid medium curve part: " + part);
        };
    }

    public static RailShape shapeForPart(Direction facing, int part) {
        RailShape canonical = switch (part) {
            // r3h-r2 NORTH canonical path:
            // SW -> NE -> SW -> NE -> SW
            //
            // This is the same five-block staircase, but now both OUTER ends
            // actually point at the adjacent straight rails:
            //   part 0: WEST external + SOUTH internal
            //   part 4: WEST internal + SOUTH external
            //
            // Parts 1/2/3 were already correct in r3h-r1.
            case 0 -> RailShape.EAST_WEST;
            case 1, 3 -> RailShape.SOUTH_WEST;
            case 2 -> RailShape.NORTH_EAST;
            case 4 -> RailShape.NORTH_SOUTH;

            // r3f-world compatibility only; new r3h-r2 placement never creates 5/6.
            case 5, 6 -> RailShape.EAST_WEST;
            default -> throw new IllegalArgumentException("Invalid medium curve part: " + part);
        };
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };
        for (int i = 0; i < turns; i++) {
            canonical = rotateClockwise(canonical);
        }
        return canonical;
    }

    private static RailShape rotateClockwise(RailShape shape) {
        return switch (shape) {
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

    private BlockPos rootFor(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ASSEMBLY_FACING);
        int part = state.getValue(PART);
        BlockPos canonicalOffset = offsetForPart(BlockPos.ZERO, facing, part);
        return pos.subtract(canonicalOffset);
    }

    private boolean isExpected(BlockState state, Direction facing, int part) {
        return state.getBlock() == this
                && state.getValue(PART) == part
                && state.getValue(ASSEMBLY_FACING) == facing;
    }

    private void removeAssembly(Level level, BlockPos brokenPos, BlockState brokenState,
                                boolean dropItem) {
        if (level.isClientSide || INTERNAL_REMOVAL.get()) {
            return;
        }
        Direction facing = brokenState.getValue(ASSEMBLY_FACING);
        BlockPos root = rootFor(brokenPos, brokenState);
        INTERNAL_REMOVAL.set(Boolean.TRUE);
        try {
            for (int part = 0; part < PART_COUNT; part++) {
                BlockPos segmentPos = offsetForPart(root, facing, part);
                if (segmentPos.equals(brokenPos)) {
                    continue;
                }
                BlockState segment = level.getBlockState(segmentPos);
                if (isExpected(segment, facing, part)) {
                    level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            if (dropItem) {
                Item item = ForgeRegistries.ITEMS.getValue(DROP_ITEM);
                if (item != null) {
                    Block.popResource(level, brokenPos, new ItemStack(item));
                }
            }
        } finally {
            INTERNAL_REMOVAL.set(Boolean.FALSE);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !INTERNAL_REMOVAL.get()) {
            removeAssembly(level, pos, state, !player.getAbilities().instabuild);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !INTERNAL_REMOVAL.get()
                && state.getBlock() != newState.getBlock()) {
            removeAssembly(level, pos, state, false);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        Item item = ForgeRegistries.ITEMS.getValue(DROP_ITEM);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
