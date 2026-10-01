package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Toggleable turnout. Empty-hand right click changes the entire owned assembly
 * between the original inactive/straight route and active/diverging route.
 */
public final class LegacyBatchSwitchRailBlock extends LegacyBatchRailBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    // Step 9.3b-t7: keep the live guide rails clickable without exposing a
    // full 1x1 outline for every hidden switch cell. These narrow shapes hug
    // the logical rail centerline, so snapping/toggling still works while the
    // old "invisible block" squares disappear.
    private static final VoxelShape NS = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 2.0D, 16.0D);
    private static final VoxelShape EW = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 2.0D, 10.0D);
    private static final VoxelShape N_E = Shapes.or(
            Block.box(6.0D, 0.0D, 0.0D, 10.0D, 2.0D, 10.0D),
            Block.box(6.0D, 0.0D, 6.0D, 16.0D, 2.0D, 10.0D));
    private static final VoxelShape N_W = Shapes.or(
            Block.box(6.0D, 0.0D, 0.0D, 10.0D, 2.0D, 10.0D),
            Block.box(0.0D, 0.0D, 6.0D, 10.0D, 2.0D, 10.0D));
    private static final VoxelShape S_E = Shapes.or(
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 2.0D, 16.0D),
            Block.box(6.0D, 0.0D, 6.0D, 16.0D, 2.0D, 10.0D));
    private static final VoxelShape S_W = Shapes.or(
            Block.box(6.0D, 0.0D, 6.0D, 10.0D, 2.0D, 16.0D),
            Block.box(0.0D, 0.0D, 6.0D, 10.0D, 2.0D, 10.0D));
    public LegacyBatchSwitchRailBlock(BlockBehaviour.Properties properties,
                                      LegacyBatchTrackSpec spec,
                                      String dropItemId) {
        super(properties, spec, dropItemId);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    /**
     * Step 9.3b-t7: switch-only rail-shaped selection outline. The hidden
     * guide cells remain real RailBlocks for routing and endpoint snapping,
     * but their selection no longer looks like a full invisible block.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        RailShape shape = state.hasProperty(SHAPE)
                ? state.getValue(SHAPE)
                : RailShape.NORTH_SOUTH;
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> NS;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> EW;
            case NORTH_EAST -> N_E;
            case NORTH_WEST -> N_W;
            case SOUTH_EAST -> S_E;
            case SOUTH_WEST -> S_W;
        };
    }

    @Override
    protected boolean alternateState(BlockState state) {
        return state.hasProperty(ACTIVE) && state.getValue(ACTIVE);
    }

    @Override
    public BlockState stateForPart(net.minecraft.core.Direction facing, int part, boolean alternate) {
        return super.stateForPart(facing, part, alternate).setValue(ACTIVE, alternate);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        // Non-empty main-hand interactions must reach the shared batch block
        // first so Step 9.3b-t4 can snap the one-block Normal track to any
        // open switch endpoint. S/M/L straight items return PASS there and
        // continue into their own endpoint-aware item placement.
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean next = !state.getValue(ACTIVE);
        net.minecraft.core.Direction facing = getAssemblyFacing(state);
        BlockPos root = getAssemblyRoot(pos, state);

        for (int part = 0; part < getSpec().partCount(); part++) {
            BlockPos segmentPos = getSpec().offsetForPart(root, facing, part);
            BlockState segment = level.getBlockState(segmentPos);
            if (segment.getBlock() != this
                    || getPart(segment) != part
                    || getAssemblyFacing(segment) != facing) {
                continue;
            }

            level.setBlock(segmentPos,
                    stateForPart(facing, part, next),
                    3);
        }

        level.playSound(null, pos, SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS, 0.6F, next ? 0.6F : 0.5F);
        player.displayClientMessage(Component.literal(
                next ? "Switch route: DIVERGING" : "Switch route: STRAIGHT"), true);
        return InteractionResult.CONSUME;
    }
}
