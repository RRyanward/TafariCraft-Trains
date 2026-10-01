package traincraft.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;

public final class TraincraftCraftingBlock extends CraftingTableBlock {
    public TraincraftCraftingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MenuProvider getMenuProvider(net.minecraft.world.level.block.state.BlockState state,
                                        Level level,
                                        BlockPos pos) {
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