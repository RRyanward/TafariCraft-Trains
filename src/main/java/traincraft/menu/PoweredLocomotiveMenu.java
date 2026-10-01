package traincraft.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.SlotItemHandler;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.item.TrackBlueprintItem;
import traincraft.registry.TCMenus;
import traincraft.registry.TCItems;

public final class PoweredLocomotiveMenu extends AbstractContainerMenu {
    private final LegacyPoweredLocomotive locomotive;
    private final int machineSlots;

    public PoweredLocomotiveMenu(int id, Inventory inventory) { this(id, inventory, resolve(inventory)); }

    public PoweredLocomotiveMenu(int id, Inventory playerInventory, LegacyPoweredLocomotive locomotive) {
        super(TCMenus.POWERED_LOCOMOTIVE.get(), id);
        this.locomotive = locomotive;
        if (locomotive.isBuilder()) {
            machineSlots = 53;
            addBuilderSlots();
            addPlayerInventory(playerInventory, 47, 174, 232);
        } else {
            addSlot(new SlotItemHandler(locomotive.getPowerInventory(), 0, 8, 53) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if (!locomotive.isElectric()) return true;
                    return stack.is(Items.REDSTONE) || stack.is(Items.REDSTONE_BLOCK);
                }
            });
            int handlerSlot = 1;
            for (int row=0;row<3;row++) for (int col=0;col<5;col++)
                addSlot(new SlotItemHandler(locomotive.getPowerInventory(),handlerSlot++,80+col*18,18+row*18));
            machineSlots = 16;
            addPlayerInventory(playerInventory,8,84,142);
        }
    }

    private void addBuilderSlots() {
        var inv = locomotive.getPowerInventory();

        // TC_BATCH8_R4A4_BLUEPRINT_FIRST_BUILDER_GUI
        // Core: fuel + Blueprint.
        // TC_BATCH8_R4A4F_BUILDER_SHARED_INPUT_SEPARATE_STORES
        addSlot(new SlotItemHandler(inv, 0, 12, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.COAL)
                        || stack.is(TCItems.DIESEL_FUEL.get());
            }
        });

        // Preserve slots 1..19 because current Builder foundation/support/
        // tunnel/material logic still consumes them. They are now presented
        // generically as Supplies instead of old manual-builder categories.
        int supplySlot = 1;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5 && supplySlot <= 19; col++) {
                addSlot(new SlotItemHandler(
                        inv,
                        supplySlot++,
                        12 + col * 18,
                        92 + row * 18));
            }
        }

        // Blueprint material/storage rack: slots 20..51.
        int storageSlot = 20;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 8; col++) {
                addSlot(new SlotItemHandler(
                        inv,
                        storageSlot++,
                        106 + col * 18,
                        50 + row * 18));
            }
        }

        // Dedicated Blueprint slot remains handler slot 52.
        addSlot(new SlotItemHandler(inv, 52, -2000, -2000) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TCItems.TRACK_BLUEPRINT.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
    }
    private void addPlayerInventory(Inventory inventory,int x,int y,int hotbarY){
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,x+col*18,y+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,x+col*18,hotbarY));
    }
    private static LegacyPoweredLocomotive resolve(Inventory inventory) {
        if (inventory.player.getVehicle()
                instanceof LegacyPoweredLocomotive locomotive) {
            return locomotive;
        }

        LegacyPoweredLocomotive nearest = null;
        double nearestDistanceSq = 64.0D;

        for (LegacyPoweredLocomotive candidate
                : inventory.player.level().getEntitiesOfClass(
                        LegacyPoweredLocomotive.class,
                        inventory.player.getBoundingBox().inflate(8.0D),
                        entity -> entity.isAlive() && entity.isBuilder())) {
            double distanceSq =
                    inventory.player.distanceToSqr(candidate);

            if (distanceSq <= nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = candidate;
            }
        }

        if (nearest != null) {
            return nearest;
        }

        throw new IllegalStateException(
                "Powered locomotive menu opened without a ride or nearby Tracks Builder");
    }
    public LegacyPoweredLocomotive getLocomotive(){return locomotive;}
    public int getPowerAmount(){return locomotive.getPowerAmount();}
    public int getPowerCapacity(){return locomotive.getPowerCapacity();}
    public boolean hasBackupRedstone(){
        if(!locomotive.isElectric() || slots.isEmpty())return false;
        ItemStack s=slots.get(0).getItem();return s.is(Items.REDSTONE)||s.is(Items.REDSTONE_BLOCK);
    }
    public String getBackupLabel(){
        if(slots.isEmpty())return "EMPTY";
        ItemStack s=slots.get(0).getItem();
        if(s.is(Items.REDSTONE_BLOCK))return "Redstone Block x"+s.getCount();
        if(s.is(Items.REDSTONE))return "Redstone x"+s.getCount();
        return "EMPTY";
    }
    @Override public boolean stillValid(Player player){return locomotive.isAlive()&&player.distanceToSqr(locomotive)<=64.0D;}

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 90 && locomotive.isBuilder()) {
            locomotive.resetBuilderBlueprintProgress();
            return true;
        }
        return super.clickMenuButton(player, id);
    }
    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index) {
        if (index < 0
                || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot =
                slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source =
                slot.getItem();

        ItemStack copy =
                source.copy();

        if (index < machineSlots) {
            if (!moveItemStackTo(
                    source,
                    machineSlots,
                    slots.size(),
                    true)) {
                return ItemStack.EMPTY;
            }
        } else if (locomotive.isBuilder()
                && source.is(
                        TCItems.TRACK_BLUEPRINT.get())) {
            if (!moveItemStackTo(
                    source,
                    52,
                    53,
                    false)) {
                return ItemStack.EMPTY;
            }
        } else if (locomotive.isBuilder()
                && (source.is(Items.COAL)
                        || source.is(
                                TCItems.DIESEL_FUEL.get()))) {
            // TC_BATCH8_R4A4F_BUILDER_FUEL_SLOT_PRIORITY
            // Both fuels use the same physical input, but remain separate
            // internally after insertion.
            if (!moveItemStackTo(
                    source,
                    0,
                    1,
                    false)) {
                return ItemStack.EMPTY;
            }
        } else if (locomotive.isBuilder()
                && TrackBlueprintItem
                        .isTrackStack(source)) {
            // TC_BATCH8_R3ZZZ_BLUEPRINT_STORAGE_SHIFTCLICK
            if (!moveItemStackTo(
                    source,
                    20,
                    52,
                    false)) {
                return ItemStack.EMPTY;
            }
        } else if (locomotive.isBuilder()) {
            if (!moveItemStackTo(
                    source,
                    1,
                    52,
                    false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(
                source,
                0,
                machineSlots,
                false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(
                    ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }
}
