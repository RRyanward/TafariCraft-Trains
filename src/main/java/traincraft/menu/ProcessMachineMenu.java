package traincraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import traincraft.block.machine.TraincraftProcessBlockEntity;
import traincraft.registry.TCMenus;

public final class ProcessMachineMenu extends AbstractContainerMenu {
    private final TraincraftProcessBlockEntity machine;
    private final ContainerData data;

    public ProcessMachineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolve(inventory, buffer.readBlockPos()));
    }

    public ProcessMachineMenu(int id, Inventory inventory, TraincraftProcessBlockEntity machine) {
        super(TCMenus.PROCESS_MACHINE.get(), id);
        this.machine = machine;

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? machine.getProgress() : machine.getMaxProgress();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) machine.setProgress(value);
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
        addDataSlots(this.data);

        if (machine.isDistillery()) {
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_A, 56, 17));
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.FUEL, 56, 53));
            addSlot(outputSlot(machine, TraincraftProcessBlockEntity.OUTPUT, 116, 60));
        } else {
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_A, 56, 17));
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_B, 35, 17));
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.FUEL, 47, 53));
            addSlot(outputSlot(machine, TraincraftProcessBlockEntity.OUTPUT, 116, 35));
        }

        int machineSlotCount = machine.isDistillery() ? 3 : 4;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private SlotItemHandler outputSlot(TraincraftProcessBlockEntity machine, int slot, int x, int y) {
        return new SlotItemHandler(machine.getItems(), slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    private static TraincraftProcessBlockEntity resolve(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof TraincraftProcessBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("Process machine block entity missing at " + pos);
    }

    public boolean isDistillery() {
        return machine.isDistillery();
    }

    public int getProgressScaled(int pixels) {
        int max = Math.max(1, data.get(1));
        return Math.min(pixels, data.get(0) * pixels / max);
    }

    @Override
    public boolean stillValid(Player player) {
        return !machine.isRemoved()
                && player.distanceToSqr(
                        machine.getBlockPos().getX() + 0.5D,
                        machine.getBlockPos().getY() + 0.5D,
                        machine.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int machineSlots = isDistillery() ? 3 : 4;
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;

        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, machineSlots - 1, false)) return ItemStack.EMPTY;
        }

        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }
}