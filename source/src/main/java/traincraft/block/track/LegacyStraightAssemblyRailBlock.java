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
 * Step 9.2a-r2 - ownership wrapper for the original Traincraft multi-length
 * straight pieces.
 *
 * Every occupied block remains a RailBlock so the frozen 1.20.1 locomotive
 * and consist code continue to see a normal BaseRailBlock path. PART/FACING
 * preserve assembly ownership so breaking any segment removes the complete
 * placed piece and returns one original multi-length item.
 */
public final class LegacyStraightAssemblyRailBlock extends RailBlock {

    // Flat full-block footprint, matching what the classic track model visually occupies.
    // This keeps selection/break targeting aligned with the rendered rail assembly.
    private static final VoxelShape ASSEMBLY_SELECTION_SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public static final IntegerProperty PART = IntegerProperty.create("assembly_part", 0, 11);
    public static final DirectionProperty ASSEMBLY_FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final ThreadLocal<Boolean> INTERNAL_REMOVAL =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final int assemblyLength;
    private final ResourceLocation dropItemId;

    public LegacyStraightAssemblyRailBlock(BlockBehaviour.Properties properties,
                                           int assemblyLength,
                                           String dropItemId) {
        super(properties);

        if (assemblyLength != 3 && assemblyLength != 6 && assemblyLength != 12) {
            throw new IllegalArgumentException("Unsupported legacy straight length: " + assemblyLength);
        }

        this.assemblyLength = assemblyLength;
        this.dropItemId = new ResourceLocation(dropItemId);

        this.registerDefaultState(this.defaultBlockState()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(PART, 0)
                .setValue(ASSEMBLY_FACING, Direction.NORTH));
    }

    public int getAssemblyLength() {
        return assemblyLength;
    }

    @Override
    public VoxelShape getShape(BlockState state,
                               BlockGetter level,
                               BlockPos pos,
                               CollisionContext context) {
        return ASSEMBLY_SELECTION_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, ASSEMBLY_FACING);
    }

    private BlockPos getAssemblyRoot(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ASSEMBLY_FACING);
        int part = state.getValue(PART);
        return pos.relative(facing.getOpposite(), part);
    }

    private boolean isExpectedSegment(BlockState state, Direction facing, int part) {
        return state.getBlock() == this
                && state.hasProperty(PART)
                && state.hasProperty(ASSEMBLY_FACING)
                && state.getValue(PART) == part
                && state.getValue(ASSEMBLY_FACING) == facing;
    }

    private void removeOtherAssemblySegments(Level level,
                                             BlockPos brokenPos,
                                             BlockState brokenState,
                                             boolean dropAssemblyItem) {
        if (level.isClientSide || INTERNAL_REMOVAL.get()) {
            return;
        }

        Direction facing = brokenState.getValue(ASSEMBLY_FACING);
        BlockPos root = getAssemblyRoot(brokenPos, brokenState);

        INTERNAL_REMOVAL.set(Boolean.TRUE);
        try {
            for (int i = 0; i < assemblyLength; i++) {
                BlockPos segmentPos = root.relative(facing, i);

                if (segmentPos.equals(brokenPos)) {
                    continue;
                }

                BlockState segmentState = level.getBlockState(segmentPos);
                if (isExpectedSegment(segmentState, facing, i)) {
                    level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }

            if (dropAssemblyItem) {
                Item item = ForgeRegistries.ITEMS.getValue(dropItemId);
                if (item != null) {
                    Block.popResource(level, brokenPos, new ItemStack(item));
                }
            }
        }
        finally {
            INTERNAL_REMOVAL.set(Boolean.FALSE);
        }
    }

    @Override
    public void playerWillDestroy(Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  Player player) {
        if (!level.isClientSide && !INTERNAL_REMOVAL.get()) {
            removeOtherAssemblySegments(level, pos, state, !player.getAbilities().instabuild);
        }

        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state,
                         Level level,
                         BlockPos pos,
                         BlockState newState,
                         boolean isMoving) {
        if (!level.isClientSide
                && !INTERNAL_REMOVAL.get()
                && state.getBlock() != newState.getBlock()) {
            // Non-player removal/support loss still clears the linked footprint.
            // No duplicate item is emitted here; playerWillDestroy owns survival drops.
            removeOtherAssemblySegments(level, pos, state, false);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level,
                                       BlockPos pos,
                                       BlockState state) {
        Item item = ForgeRegistries.ITEMS.getValue(dropItemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
