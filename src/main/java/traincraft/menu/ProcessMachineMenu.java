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
import net.minecraft.world.item.Items;
import traincraft.registry.TCItems;
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
                return switch (index) {
                    case 0 -> machine.getProgress();
                    case 1 -> machine.getMaxProgress();
                    case 2 -> machine.getBurnRemaining();
                    case 3 -> machine.getBurnTotal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> machine.setProgress(value);
                    case 2 -> machine.setBurnRemaining(value);
                    case 3 -> machine.setBurnTotal(value);
                    default -> { }
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        addDataSlots(this.data);

        if (machine.isDistillery()) {
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_A, 56, 17) {
                @Override public boolean mayPlace(ItemStack stack) { return machine.isValidDistillInput(stack); }
            });
            addSlot(fuelSlot(machine, 56, 53));
            addSlot(outputSlot(machine, TraincraftProcessBlockEntity.OUTPUT, 116, 60));
            addSlot(new SlotItemHandler(machine.getPlasticItems(), TraincraftProcessBlockEntity.PLASTIC_OUTPUT, 123, 35) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        } else {
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_A, 56, 17) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.IRON_INGOT); }
            });
            addSlot(new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.INPUT_B, 35, 17) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(TCItems.GRAPHITE.get()); }
            });
            addSlot(fuelSlot(machine, 47, 53));
            addSlot(outputSlot(machine, TraincraftProcessBlockEntity.OUTPUT, 116, 35));
        }

        int machineSlotCount = 4;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private SlotItemHandler fuelSlot(TraincraftProcessBlockEntity machine, int x, int y) {
        return new SlotItemHandler(machine.getItems(), TraincraftProcessBlockEntity.FUEL, x, y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return machine.acceptsFuel(stack);
            }
        };
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

    public boolean isBurning() {
        return data.get(2) > 0;
    }

    public int getBurnScaled(int pixels) {
        if (!isBurning()) return 0;
        return Math.max(1, Math.min(pixels, data.get(2) * pixels / Math.max(1, data.get(3))));
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
        final int machineSlots = 4;
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Route fuel to the lower fuel slot first. Neither machine should
            // place coal into the top recipe inputs via shift-click.
            int target = -1;
            if (isDistillery()) {
                if (machine.isValidDistillInput(source)) target = 0;
                else if (machine.acceptsFuel(source)) target = 1;
            } else {
                if (source.is(Items.IRON_INGOT)) target = 0;
                else if (source.is(TCItems.GRAPHITE.get())) target = 1;
                else if (machine.acceptsFuel(source)) target = 2;
            }
            if (target < 0 || !moveItemStackTo(source, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}