package traincraft.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import traincraft.registry.TCMenus;

public final class GuideMenu extends AbstractContainerMenu {
    public GuideMenu(int id, Inventory inventory) {
        super(TCMenus.GUIDE.get(), id);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}