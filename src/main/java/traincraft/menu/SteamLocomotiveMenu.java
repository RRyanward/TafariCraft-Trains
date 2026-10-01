/*
 * Traincraft steam-locomotive menu for Minecraft 1.20.1.
 * Based on the original ContainerLocomotiveSteam layout.
 */
package traincraft.menu;

import net.minecraft.util.Mth;
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
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCMenus;

public final class SteamLocomotiveMenu extends AbstractContainerMenu {
    public static final int LOCOMOTIVE_SLOT_COUNT = 11;

    private final SmallSteamLocomotive locomotive;
    private final ContainerData data;

    /**
     * Client-side constructor used by the vanilla MenuType. The locomotive does
     * not need to be sent as extra menu data because this GUI can only be opened
     * while the local player is physically riding the steam locomotive.
     */
    public SteamLocomotiveMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, resolveRiddenLocomotive(playerInventory), new SimpleContainerData(6));
    }

    /** Server-side constructor used by the locomotive MenuProvider. */
    public SteamLocomotiveMenu(int containerId, Inventory playerInventory, SmallSteamLocomotive locomotive) {
        this(containerId, playerInventory, locomotive, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> locomotive.getBurnTime();
                    case 1 -> locomotive.getMaxBurnTime();
                    case 2 -> locomotive.getWaterAmount();
                    case 3 -> locomotive.getSteamAmount();
                    case 4 -> locomotive.isHandBrakeApplied() ? 1 : 0;
                    case 5 -> locomotive.getBoilerTemperatureTenthsC();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server owns these values; client receives them through menu data sync.
            }

            @Override
            public int getCount() {
                return 6;
            }
        });
    }

    private SteamLocomotiveMenu(int containerId, Inventory playerInventory,
                                SmallSteamLocomotive locomotive, ContainerData data) {
        super(TCMenus.STEAM_LOCOMOTIVE.get(), containerId);
        this.locomotive = locomotive;
        this.data = data;

        IItemHandler inventory = locomotive.getSteamInventory();

        // Original Traincraft layout: fuel, water container, then 3x3 storage.
        this.addSlot(new SlotItemHandler(inventory, SmallSteamLocomotive.BURN_SLOT, 8, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isFuel(stack);
            }
        });
        this.addSlot(new SlotItemHandler(inventory, SmallSteamLocomotive.WATER_SLOT, 32, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isWaterContainer(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slot = 2 + row * 3 + column;
                this.addSlot(new SlotItemHandler(inventory, slot, 80 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isFuel(stack) || isWaterContainer(stack);
                    }
                });
            }
        }

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(
                        playerInventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(
                    playerInventory, column, 8 + column * 18, 142));
        }

        this.addDataSlots(data);
    }

    private static SmallSteamLocomotive resolveRiddenLocomotive(Inventory inventory) {
        Entity vehicle = inventory.player.getVehicle();
        if (vehicle instanceof SmallSteamLocomotive locomotive) {
            return locomotive;
        }
        throw new IllegalStateException("Traincraft steam locomotive GUI opened while the client player is not riding a steam locomotive");
    }

    public SmallSteamLocomotive getLocomotive() {
        return this.locomotive;
    }

    public int getBurnTime() {
        return this.data.get(0);
    }

    public int getMaxBurnTime() {
        return this.data.get(1);
    }

    public int getWaterAmount() {
        return this.data.get(2);
    }

    public int getSteamAmount() {
        return this.data.get(3);
    }

    /** Server-synced parking/hand-brake state shown by the locomotive GUI. */
    public boolean isHandBrakeApplied() {
        return this.data.get(4) != 0;
    }

    public int getBoilerTemperatureTenthsC() {
        return this.data.get(5);
    }

    public int getBoilerTemperatureC() {
        return Math.round(this.getBoilerTemperatureTenthsC() / 10.0F);
    }

    public boolean isBoilerOverheating() {
        return this.getBoilerTemperatureTenthsC() > 2000;
    }

    public boolean isBoilerCritical() {
        return this.getBoilerTemperatureTenthsC() >= 2200;
    }

    /** 0% at 20 C resting temperature, 100% at the 200 C overheat line. */
    public int getBoilerHeatPercent() {
        return Mth.clamp(Math.round(
                (this.getBoilerTemperatureTenthsC() - 200) * 100.0F / 1800.0F),
                0, 100);
    }

    public int getWaterCapacity() {
        return SmallSteamLocomotive.WATER_CAPACITY_MB;
    }

    public int getSteamCapacity() {
        return SmallSteamLocomotive.STEAM_CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.locomotive.isRemoved() && player.distanceToSqr(this.locomotive) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        if (index < 0 || index >= this.slots.size()) {
            return empty;
        }

        var slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return empty;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();

        if (index < LOCOMOTIVE_SLOT_COUNT) {
            if (!this.moveItemStackTo(source, LOCOMOTIVE_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isFuel(source)) {
            if (!this.moveItemStackTo(source, SmallSteamLocomotive.BURN_SLOT,
                    SmallSteamLocomotive.BURN_SLOT + 1, false)
                    && !this.moveItemStackTo(source, 2, LOCOMOTIVE_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isWaterContainer(source)) {
            if (!this.moveItemStackTo(source, SmallSteamLocomotive.WATER_SLOT,
                    SmallSteamLocomotive.WATER_SLOT + 1, false)
                    && !this.moveItemStackTo(source, 2, LOCOMOTIVE_SLOT_COUNT, false)) {
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

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    public static boolean isWaterContainer(ItemStack stack) {
        // Step 6.0 deliberately starts with the vanilla water bucket. General
        // Forge fluid containers/canisters are added after this GUI is validated.
        return stack.is(Items.WATER_BUCKET);
    }
}
