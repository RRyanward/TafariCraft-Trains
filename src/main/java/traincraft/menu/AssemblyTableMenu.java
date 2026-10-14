package traincraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import traincraft.block.machine.AssemblyTableBlockEntity;
import traincraft.registry.TCMenus;

public final class AssemblyTableMenu extends AbstractContainerMenu {
    public static final int PAGE_PREVIOUS = 0;
    public static final int PAGE_NEXT = 1;

    private final AssemblyTableBlockEntity table;

    public AssemblyTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolve(inventory, buffer.readBlockPos()));
    }

    public AssemblyTableMenu(int id, Inventory playerInventory, AssemblyTableBlockEntity table) {
        super(TCMenus.ASSEMBLY_TABLE.get(), id);
        this.table = table;

        int[][] input = {
                {25,27}, {79,27}, {115,27}, {145,27},
                {25,61}, {79,61}, {115,61},
                {43,93}, {79,93}, {145,93}
        };
        for (int i = 0; i < 10; i++) {
            addSlot(new SlotItemHandler(table.getItems(), i, input[i][0], input[i][1]));
        }

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                addSlot(new SlotItemHandler(
                        table.getItems(),
                        10 + col + row * 4,
                        8 + col * 18,
                        128 + row * 18));
            }
        }

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                int slot = 18 + col + row * 4;
                addSlot(new SlotItemHandler(table.getItems(), slot, 92 + col * 18, 128 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public void onTake(Player player, ItemStack stack) {
                        super.onTake(player, stack);
                        if (!player.level().isClientSide) {
                            table.takeCraft(getSlotIndex() - AssemblyTableBlockEntity.OUTPUT_START);
                        }
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        174 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 232));
        }
    }

    private static AssemblyTableBlockEntity resolve(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof AssemblyTableBlockEntity table) {
            return table;
        }
        throw new IllegalStateException("Assembly Table block entity missing at " + pos);
    }

    public int getTier() {
        return table.getTier();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == PAGE_PREVIOUS) {
            table.changeOutputPage(-1);
            return true;
        }
        if (id == PAGE_NEXT) {
            table.changeOutputPage(1);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean stillValid(Player player) {
        return table.getLevel() != null
                && table.getLevel().getBlockEntity(table.getBlockPos()) == table
                && player.distanceToSqr(
                        table.getBlockPos().getX() + 0.5D,
                        table.getBlockPos().getY() + 0.5D,
                        table.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) return empty;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return empty;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        final int machineSlots = 26;

        if (index >= 18 && index <= 25) {
            if (!moveItemStackTo(stack, machineSlots, machineSlots + 36, true)) return ItemStack.EMPTY;
        } else if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, machineSlots + 36, false)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, 18, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
