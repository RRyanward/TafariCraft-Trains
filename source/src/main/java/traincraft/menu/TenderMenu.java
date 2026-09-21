/*
 * Traincraft classic steam-tender menu for Minecraft 1.20.1.
 * Slot coordinates are taken directly from the original 1.7.10 InventoryTender.
 */
package traincraft.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCMenus;

public final class TenderMenu extends AbstractContainerMenu {
    public static final int TENDER_SLOT_COUNT = 16; // 1 water-container + 15 fuel

    private final SteamTender tender;
    private final ContainerData data;

    /** Client constructor used by IForgeMenuType. */
    public TenderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                resolveTender(playerInventory, buffer.readInt()),
                new SimpleContainerData(1));
    }

    /** Server constructor used by SteamTender's MenuProvider. */
    public TenderMenu(int containerId, Inventory playerInventory, SteamTender tender) {
        this(containerId, playerInventory, tender, new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? tender.getWaterAmount() : 0;
            }

            @Override
            public void set(int index, int value) {
                // Server owns tender water; client only receives the synchronized value.
            }

            @Override
            public int getCount() {
                return 1;
            }
        });
    }

    private TenderMenu(int containerId, Inventory playerInventory,
                       SteamTender tender, ContainerData data) {
        super(TCMenus.TENDER.get(), containerId);
        this.tender = tender;
        this.data = data;

        /* Original 1.7.10 liquid-container slot: x=8, y=53. */
        this.addSlot(new SlotItemHandler(tender.getWaterContainerInventory(), 0, 8, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.WATER_BUCKET);
            }
        });

        /* Original 1.7.10 tender fuel grid: 5 columns x 3 rows. */
        IItemHandler fuel = tender.getFuelInventory();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                int slot = row * 5 + column;
                this.addSlot(new SlotItemHandler(
                        fuel, slot,
                        44 + column * 18,
                        18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isFuel(stack) || stack.is(Items.BUCKET);
                    }
                });
            }
        }

        /* Original player inventory starts at y=84, hotbar y=142. */
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(
                    playerInventory, column,
                    8 + column * 18, 142));
        }

        this.addDataSlots(data);
    }

    private static SteamTender resolveTender(Inventory inventory, int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        if (entity instanceof SteamTender tender) {
            return tender;
        }
        throw new IllegalStateException(
                "Traincraft tender GUI opened without a valid SteamTender entity: " + entityId);
    }

    public SteamTender getTender() {
        return this.tender;
    }

    public int getWaterAmount() {
        return this.data.get(0);
    }

    public int getWaterCapacity() {
        return this.tender.getWaterCapacity();
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.tender.isRemoved() && player.distanceToSqr(this.tender) <= 64.0D;
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

        if (index < TENDER_SLOT_COUNT) {
            if (!this.moveItemStackTo(source, TENDER_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.is(Items.WATER_BUCKET)) {
            if (!this.moveItemStackTo(source, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isFuel(source) || source.is(Items.BUCKET)) {
            if (!this.moveItemStackTo(source, 1, TENDER_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty()
                && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }
}
