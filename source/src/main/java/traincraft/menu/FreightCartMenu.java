/* Traincraft 1.7.10-style freight inventory menu for Minecraft 1.20.1. */
package traincraft.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import traincraft.entity.train.freight.FreightCart;
import traincraft.registry.TCMenus;

/** Exact four-row / 36-slot layout used by the classic Freight Cart. */
public final class FreightCartMenu extends AbstractContainerMenu {
    public static final int CARGO_SLOT_COUNT = FreightCart.CARGO_SLOTS;

    private final FreightCart freightCart;

    /** Client constructor used by IForgeMenuType. */
    public FreightCartMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                resolveFreightCart(playerInventory, buffer.readInt()));
    }

    /** Server constructor used by FreightCart's MenuProvider. */
    public FreightCartMenu(int containerId, Inventory playerInventory, FreightCart freightCart) {
        super(TCMenus.FREIGHT_CART.get(), containerId);
        this.freightCart = freightCart;

        /* Original InventoryFreight: 9 columns x 4 rows, x=8, y=18. */
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                this.addSlot(new SlotItemHandler(
                        freightCart.getCargoInventory(), slot,
                        8 + column * 18,
                        18 + row * 18));
            }
        }

        /* Original four-row freight player inventory: y=103, hotbar y=161. */
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        103 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(
                    playerInventory, column,
                    8 + column * 18, 161));
        }
    }

    private static FreightCart resolveFreightCart(Inventory inventory, int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        if (entity instanceof FreightCart freightCart) {
            return freightCart;
        }
        throw new IllegalStateException(
                "Traincraft freight GUI opened without a valid FreightCart entity: " + entityId);
    }

    public FreightCart getFreightCart() {
        return this.freightCart;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.freightCart.isRemoved()
                && player.distanceToSqr(this.freightCart) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        var slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();

        if (index < CARGO_SLOT_COUNT) {
            if (!this.moveItemStackTo(source, CARGO_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(source, 0, CARGO_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
