package traincraft.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.menu.ProcessMachineMenu;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCBlockEntities;
import traincraft.registry.TCItems;

public final class TraincraftProcessBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_A = 0;
    public static final int INPUT_B = 1;
    public static final int FUEL = 2;
    public static final int OUTPUT = 3;

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private LazyOptional<ItemStackHandler> itemCap = LazyOptional.of(() -> items);
    private int progress;

    public TraincraftProcessBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.PROCESS_MACHINE.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public boolean isDistillery() {
        return getBlockState().is(TCBlocks.DISTILLERY.get());
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int value) {
        progress = Math.max(0, value);
    }

    public int getMaxProgress() {
        return isDistillery() ? 100 : 1000;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TraincraftProcessBlockEntity be) {
        if (level.isClientSide) return;

        boolean valid = be.isDistillery() ? be.canDistill() : be.canHearth();
        if (!valid) {
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }

        be.progress++;
        if (be.progress >= be.getMaxProgress()) {
            if (be.isDistillery()) {
                be.finishDistill();
            } else {
                be.finishHearth();
            }
            be.progress = 0;
            be.setChanged();
        }
    }

    private boolean canDistill() {
        ItemStack input = items.getStackInSlot(INPUT_A);
        ItemStack fuel = items.getStackInSlot(FUEL);
        if (distillAmount(input) <= 0 || !isFuel(fuel)) return false;
        return canOutput(new ItemStack(TCItems.DIESEL_FUEL.get(), distillAmount(input)));
    }

    private boolean canHearth() {
        ItemStack iron = items.getStackInSlot(INPUT_A);
        ItemStack graphite = items.getStackInSlot(INPUT_B);
        ItemStack fuel = items.getStackInSlot(FUEL);
        if (!iron.is(Items.IRON_INGOT) || !graphite.is(TCItems.GRAPHITE.get()) || !isFuel(fuel)) {
            return false;
        }
        return canOutput(new ItemStack(TCItems.STEEL_INGOT.get()));
    }

    private boolean canOutput(ItemStack produced) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        if (out.isEmpty()) return true;
        if (!ItemStack.isSameItemSameTags(out, produced)) return false;
        return out.getCount() + produced.getCount() <= out.getMaxStackSize();
    }

    private void finishDistill() {
        ItemStack input = items.getStackInSlot(INPUT_A);
        int amount = distillAmount(input);
        if (amount <= 0) return;
        items.extractItem(INPUT_A, 1, false);
        items.extractItem(FUEL, 1, false);
        pushOutput(new ItemStack(TCItems.DIESEL_FUEL.get(), amount));
    }

    private void finishHearth() {
        items.extractItem(INPUT_A, 1, false);
        items.extractItem(INPUT_B, 1, false);
        items.extractItem(FUEL, 1, false);
        pushOutput(new ItemStack(TCItems.STEEL_INGOT.get()));
    }

    private void pushOutput(ItemStack produced) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(OUTPUT, produced);
        } else {
            ItemStack copy = out.copy();
            copy.grow(produced.getCount());
            items.setStackInSlot(OUTPUT, copy);
        }
    }

    private int distillAmount(ItemStack input) {
        if (input.is(TCBlocks.OIL_SAND.get().asItem())
                || input.is(TCBlocks.PETROL_ORE.get().asItem())) {
            return 2;
        }
        if (input.is(Items.SUGAR_CANE)
                || input.is(Items.WHEAT)
                || isLeaves(input)) {
            return 1;
        }
        return 0;
    }

    private boolean isLeaves(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        return blockItem.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES);
    }

    private boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL)
                || stack.is(Items.CHARCOAL)
                || stack.is(TCItems.COAL_DUST.get());
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(isDistillery() ? "Distillation Tower" : "Open Hearth Furnace");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ProcessMachineMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", items.serializeNBT());
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            items.deserializeNBT(tag.getCompound("Inventory"));
        }
        progress = tag.getInt("Progress");
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> items);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }
}