package traincraft.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public final class TraincraftCraftingBlock extends CraftingTableBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TraincraftCraftingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // r1d5 runtime evidence showed the visible front 180 degrees from the
        // player's expected side. Use the placement direction directly here.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        return new SimpleMenuProvider(
                (id, inventory, player) -> new CraftingMenu(id, inventory, access) {
                    @Override
                    public boolean stillValid(Player checkingPlayer) {
                        return level.getBlockState(pos).getBlock() == TraincraftCraftingBlock.this
                                && checkingPlayer.distanceToSqr(
                                        pos.getX() + 0.5D,
                                        pos.getY() + 0.5D,
                                        pos.getZ() + 0.5D) <= 64.0D;
                    }
                },
                Component.translatable(getDescriptionId()));
    }
}
