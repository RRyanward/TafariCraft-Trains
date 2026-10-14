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
            machineSlots = 78;
            addBuilderSlots();
            addPlayerInventory(playerInventory, 47, 192, 250);
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
        var inv =
                locomotive.getPowerInventory();

        // TC_BATCH8_R4A4_BLUEPRINT_FIRST_BUILDER_GUI
        // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
        addSlot(new SlotItemHandler(
                inv,
                0,
                12,
                58) {
            // TC_DEV36_R1K12_COAL_ONLY_BUILDER
            @Override
            public boolean mayPlace(
                    ItemStack stack) {
                return stack.is(
                        net.minecraft.world.item.Items.COAL);
            }
        });

        // Historical SUPPLIES 1..19.
        int supplySlot =
                1;
        for (int row = 0;
             row < 4;
             row++) {
            for (int col = 0;
                 col < 5
                         && supplySlot <= 19;
                 col++) {
                addSlot(new SlotItemHandler(
                        inv,
                        supplySlot++,
                        12 + col * 18,
                        92 + row * 18));
            }
        }

        // Historical BLUEPRINT MATERIALS 20..51.
        int storageSlot =
                20;
        for (int row = 0;
             row < 4;
             row++) {
            for (int col = 0;
                 col < 8;
                 col++) {
                addSlot(new SlotItemHandler(
                        inv,
                        storageSlot++,
                        106 + col * 18,
                        50 + row * 18));
            }
        }

        // Handler/menu slot 52 stays the real Blueprint storage slot, but is
        // intentionally offscreen. The large Blueprint holder is its UI target.
        addSlot(new SlotItemHandler(
                inv,
                52,
                -1000,
                -1000) {
            @Override
            public boolean mayPlace(
                    ItemStack stack) {
                return stack.is(
                        TCItems.TRACK_BLUEPRINT.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // One appended SUPPLIES cell completes the clean 4x5 grid.
        addSlot(new SlotItemHandler(
                inv,
                53,
                84,
                146));

        // Preserve r1k12 row-5 material data exactly where it already lived.
        // handler 59..66 -> visible material row 5.
        for (int extra = 0;
             extra < 8;
             extra++) {
            addSlot(new SlotItemHandler(
                    inv,
                    59 + extra,
                    106 + extra * 18,
                    122));
        }

        // Reuse former extra-SUPPLIES storage 54..58, then 67..69,
        // as visible material row 6 without losing saved stacks.
        for (int extra = 0;
             extra < 8;
             extra++) {
            int handlerSlot =
                    extra < 5
                            ? 54 + extra
                            : 67 + (extra - 5);

            addSlot(new SlotItemHandler(
                    inv,
                    handlerSlot,
                    106 + extra * 18,
                    140));
        }

        // New handler 70..77 -> visible material row 7.
        for (int extra = 0;
             extra < 8;
             extra++) {
            addSlot(new SlotItemHandler(
                    inv,
                    70 + extra,
                    106 + extra * 18,
                    158));
        }

        // Pattern/filter menu slots now follow all 78 Builder main slots.
        var structure =
                locomotive.getBuilderStructurePatterns();

        for (int i = 0;
             i < 7;
             i++) {
            addSlot(new SlotItemHandler(
                    structure,
                    i,
                    263 + i * 18,
                    58) {
                @Override
                public boolean mayPlace(
                        ItemStack stack) {
                    return stack.getItem()
                            instanceof net.minecraft.world.item.BlockItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        addSlot(new SlotItemHandler(
                structure,
                7,
                306,
                111) {
            @Override
            public boolean mayPlace(
                    ItemStack stack) {
                return stack.getItem()
                        instanceof net.minecraft.world.item.BlockItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addSlot(new SlotItemHandler(
                structure,
                8,
                324,
                111) {
            @Override
            public boolean mayPlace(
                    ItemStack stack) {
                return stack.getItem()
                        instanceof net.minecraft.world.item.BlockItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int i = 0;
             i < 3;
             i++) {
            addSlot(new SlotItemHandler(
                    structure,
                    9 + i,
                    297 + i * 18,
                    164) {
                @Override
                public boolean mayPlace(
                        ItemStack stack) {
                    return stack.getItem()
                            instanceof net.minecraft.world.item.BlockItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
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
        // TC_DEV36_R1K29R_MANUAL_TUNNEL_MENU
        if (id == 91
                && locomotive.isBuilder()) {
            locomotive.toggleBuilderManualTunnelMode();
            return true;
        }
        if (id == 90 && locomotive.isBuilder()) {
            locomotive.resetBuilderBlueprintProgress();
            return true;
        }
        return super.clickMenuButton(player, id);
    }
    // TC_DEV36_R1K4_PATTERN_SWAP_SHIFT
    @Override
    public void clicked(
            int slotId,
            int button,
            net.minecraft.world.inventory.ClickType clickType,
            Player player) {
        // TC_DEV36_R1K14_LARGE_BLUEPRINT_HOLDER
        // TC_DEV36_R1K4_PATTERN_SWAP_SHIFT
        int patternStart =
                locomotive.isBuilder()
                        ? 78
                        : -1;

        int patternEnd =
                locomotive.isBuilder()
                        ? 90
                        : -1;

        if (locomotive.isBuilder()
                && slotId == 52
                && (clickType
                                == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                        || (clickType
                                        == net.minecraft.world.inventory.ClickType.PICKUP
                                && getCarried().isEmpty()))) {
            ItemStack moved =
                    quickMoveStack(
                            player,
                            slotId);

            if (!moved.isEmpty()) {
                broadcastChanges();
                return;
            }
        }

        if (locomotive.isBuilder()
                && slotId >= patternStart
                && slotId < patternEnd
                && clickType
                        == net.minecraft.world.inventory.ClickType.PICKUP
                && (button == 0
                        || button == 1)
                && slotId >= 0
                && slotId < slots.size()) {
            Slot patternSlot =
                    slots.get(
                            slotId);

            ItemStack carried =
                    getCarried();

            ItemStack oldTemplate =
                    patternSlot.getItem();

            if (!carried.isEmpty()
                    && carried.getItem()
                            instanceof net.minecraft.world.item.BlockItem
                    && patternSlot.mayPlace(
                            carried)
                    && !oldTemplate.isEmpty()
                    && !ItemStack.isSameItemSameTags(
                            carried,
                            oldTemplate)) {
                ItemStack replacement =
                        carried.copy();
                replacement.setCount(
                        1);

                ItemStack returned =
                        oldTemplate.copy();

                patternSlot.set(
                        replacement);
                patternSlot.setChanged();

                if (!player.getAbilities()
                        .instabuild) {
                    carried.shrink(
                            1);
                }

                if (carried.isEmpty()) {
                    setCarried(
                            returned);
                }
                else if (!player.getInventory()
                        .add(
                                returned)) {
                    player.drop(
                            returned,
                            false);
                }

                broadcastChanges();
                return;
            }
        }

        super.clicked(
                slotId,
                button,
                clickType,
                player);
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

        int patternStart =
                locomotive.isBuilder()
                        ? 78
                        : -1;

        int patternEnd =
                locomotive.isBuilder()
                        ? 90
                        : -1;

        int playerStart =
                locomotive.isBuilder()
                        ? patternEnd
                        : machineSlots;

        // TC_DEV36_R1K14_LARGE_BLUEPRINT_HOLDER
        if (locomotive.isBuilder()
                && index == 52) {
            if (!moveItemStackTo(
                    source,
                    playerStart,
                    slots.size(),
                    true)) {
                return ItemStack.EMPTY;
            }
        }
        else if (locomotive.isBuilder()
                && index >= patternStart
                && index < patternEnd) {
            if (!moveItemStackTo(
                    source,
                    playerStart,
                    slots.size(),
                    true)) {
                return ItemStack.EMPTY;
            }
        }
        else if (index < machineSlots) {
            if (!moveItemStackTo(
                    source,
                    playerStart,
                    slots.size(),
                    true)) {
                return ItemStack.EMPTY;
            }
        }
        else if (locomotive.isBuilder()
                && source.is(
                        TCItems.TRACK_BLUEPRINT.get())) {
            if (!moveItemStackTo(
                    source,
                    52,
                    53,
                    false)) {
                return ItemStack.EMPTY;
            }
        }
        else if (locomotive.isBuilder()
                && source.is(
                        net.minecraft.world.item.Items.COAL)) {
            if (!moveItemStackTo(
                    source,
                    0,
                    1,
                    false)) {
                return ItemStack.EMPTY;
            }
        }
        else if (locomotive.isBuilder()
                && source.is(
                        TCItems.DIESEL_FUEL.get())) {
            return ItemStack.EMPTY;
        }
        else if (locomotive.isBuilder()
                && TrackBlueprintItem
                        .isTrackStack(
                                source)) {
            boolean moved =
                    moveItemStackTo(
                            source,
                            20,
                            52,
                            false);

            if (!source.isEmpty()) {
                moved =
                        moveItemStackTo(
                                source,
                                54,
                                78,
                                false)
                                || moved;
            }

            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        else if (locomotive.isBuilder()) {
            boolean moved =
                    moveItemStackTo(
                            source,
                            1,
                            52,
                            false);

            if (!source.isEmpty()) {
                moved =
                        moveItemStackTo(
                                source,
                                53,
                                78,
                                false)
                                || moved;
            }

            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        else if (!moveItemStackTo(
                source,
                0,
                machineSlots,
                false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(
                    ItemStack.EMPTY);
        }
        else {
            slot.setChanged();
        }

        return copy;
    }
}
