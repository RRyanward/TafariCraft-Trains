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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.util.RandomSource;
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

    // Isolated inventory ensures old four-slot world saves stay compatible.
    public static final int PLASTIC_OUTPUT = 0;

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // Keep the existing four-slot handler and NBT unchanged for world compatibility.
    private final ItemStackHandler plasticItems = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private LazyOptional<ItemStackHandler> itemCap = LazyOptional.of(() -> items);
    private int progress;
    private int burnRemaining;
    private int burnTotal;

    public TraincraftProcessBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.PROCESS_MACHINE.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public ItemStackHandler getPlasticItems() {
        return plasticItems;
    }

    public boolean isBurning() {
        return burnRemaining > 0;
    }

    public int getBurnRemaining() {
        return burnRemaining;
    }

    public void setBurnRemaining(int ticks) {
        burnRemaining = Math.max(0, ticks);
    }

    public int getBurnTotal() {
        return burnTotal;
    }

    public void setBurnTotal(int ticks) {
        burnTotal = Math.max(0, ticks);
    }

    public static boolean isValidFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(TCItems.COAL_DUST.get())) return true;
        return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    public boolean acceptsFuel(ItemStack stack) {
        return isValidFuel(stack) || (!isDistillery() && (
                stack.is(TCBlocks.OIL_SAND.get().asItem())
                || stack.is(TCBlocks.PETROL_ORE.get().asItem())
                || stack.is(TCItems.DIESEL_FUEL.get())));
    }

    private int fuelTicks(ItemStack stack) {
        if (stack.is(TCItems.COAL_DUST.get())) return 1600;
        if (!isDistillery()) {
            if (stack.is(TCBlocks.OIL_SAND.get().asItem())
                    || stack.is(TCBlocks.PETROL_ORE.get().asItem())) return 2500;
            if (stack.is(TCItems.DIESEL_FUEL.get())) return 4000;
        }
        return Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
    }

    public boolean isValidDistillInput(ItemStack input) {
        return distillAmount(input) > 0;
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

        // OG Traincraft fuel timing: one consumed fuel item supplies many ticks
        // of heat, not one fuel item per finished recipe.
        boolean valid = be.isDistillery() ? be.canDistill() : be.canHearth();
        boolean changed = false;
        if (be.burnRemaining > 0) {
            be.burnRemaining--;
            changed = true;
        }
        if (valid && be.burnRemaining <= 0) {
            ItemStack fuel = be.items.getStackInSlot(FUEL);
            int ticks = be.fuelTicks(fuel);
            if (ticks > 0) {
                be.burnRemaining = ticks;
                be.burnTotal = ticks;
                ItemStack result = be.items.extractItem(FUEL, 1, false);
                // Preserve a container returned by fluid-based furnace fuels.
                if (result.hasCraftingRemainingItem() && be.items.getStackInSlot(FUEL).isEmpty()) {
                    be.items.setStackInSlot(FUEL, result.getCraftingRemainingItem());
                }
                changed = true;
            }
        }
        if (!valid || be.burnRemaining <= 0) {
            if (be.progress != 0) {
                be.progress = 0;
                changed = true;
            }
        } else {
            be.progress++;
            changed = true;
            if (be.progress >= be.getMaxProgress()) {
                if (be.isDistillery()) {
                    be.finishDistill(level.random);
                } else {
                    be.finishHearth();
                }
                be.progress = 0;
            }
        }
        // Exterior flame particles are VISUAL ONLY. The accepted processing
        // state, recipes, fuel consumption, progress and NBT are untouched.
        // Emit from the front door when burnRemaining > 0, without altering
        // blockstate variants, world saves, or the existing model assets.
        if (be.burnRemaining > 0 && level.getGameTime() % 6L == 0L
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Direction front = state.getValue(TraincraftProcessBlock.FACING);
            double px = pos.getX() + 0.5D + front.getStepX() * 0.515D;
            double py = pos.getY() + 0.30D;
            double pz = pos.getZ() + 0.5D + front.getStepZ() * 0.515D;
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    px, py, pz, 2, 0.085D, 0.065D, 0.085D, 0.002D);
        }
        if (changed) {
            be.setChanged();
            // Client uses container-data synchronization while menu is open.
        }
    }

    private boolean canDistill() {
        ItemStack input = items.getStackInSlot(INPUT_A);
        if (distillAmount(input) <= 0) return false;
        if (!canOutput(new ItemStack(TCItems.DIESEL_FUEL.get(), distillAmount(input)))) return false;
        // The original tower needs an available plastic byproduct slot,
        // even if a given process only has a chance to create plastic.
        int plasticCount = plasticPerProcess(input);
        return plasticCount > 0 && canOutputPlastic(plasticCount);
    }

    private boolean canHearth() {
        ItemStack iron = items.getStackInSlot(INPUT_A);
        ItemStack graphite = items.getStackInSlot(INPUT_B);
        if (!iron.is(Items.IRON_INGOT) || !graphite.is(TCItems.GRAPHITE.get())) {
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

    private void finishDistill(RandomSource random) {
        ItemStack input = items.getStackInSlot(INPUT_A);
        int amount = distillAmount(input);
        if (amount <= 0 || !canOutput(new ItemStack(TCItems.DIESEL_FUEL.get(), amount))) return;
        int plasticCount = plasticPerProcess(input);
        if (!canOutputPlastic(plasticCount)) return;
        int chance = plasticChanceDenominator(input);
        if (chance > 0 && random.nextInt(chance) == 0) {
            pushPlastic(new ItemStack(TCItems.PLASTIC.get(), plasticCount));
        }
        items.extractItem(INPUT_A, 1, false);
        pushOutput(new ItemStack(TCItems.DIESEL_FUEL.get(), amount));
    }

    private void finishHearth() {
        items.extractItem(INPUT_A, 1, false);
        items.extractItem(INPUT_B, 1, false);
        pushOutput(new ItemStack(TCItems.STEEL_INGOT.get()));
    }

    private boolean canOutputPlastic(int count) {
        if (count <= 0) return false;
        ItemStack plastic = plasticItems.getStackInSlot(PLASTIC_OUTPUT);
        if (plastic.isEmpty()) return true;
        return plastic.is(TCItems.PLASTIC.get()) && plastic.getCount() + count <= plastic.getMaxStackSize();
    }

    private void pushPlastic(ItemStack produced) {
        ItemStack held = plasticItems.getStackInSlot(PLASTIC_OUTPUT);
        if (held.isEmpty()) {
            plasticItems.setStackInSlot(PLASTIC_OUTPUT, produced);
        } else {
            ItemStack updated = held.copy();
            updated.grow(produced.getCount());
            plasticItems.setStackInSlot(PLASTIC_OUTPUT, updated);
        }
    }

    private int plasticPerProcess(ItemStack input) {
        return isLeaves(input) ? 2 : 1;
    }

    private int plasticChanceDenominator(ItemStack input) {
        if (input.is(TCBlocks.OIL_SAND.get().asItem()) || input.is(TCBlocks.PETROL_ORE.get().asItem())) return 1;
        if (input.is(Items.SUGAR_CANE) || input.is(Items.WHEAT)) return 4;
        if (isLeaves(input)) return 6;
        return 0;
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
        tag.put("PlasticInventory", plasticItems.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putInt("BurnRemaining", burnRemaining);
        tag.putInt("BurnTotal", burnTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            items.deserializeNBT(tag.getCompound("Inventory"));
        }
        if (tag.contains("PlasticInventory")) {
            plasticItems.deserializeNBT(tag.getCompound("PlasticInventory"));
        }
        progress = tag.getInt("Progress");
        burnRemaining = Math.max(0, tag.getInt("BurnRemaining"));
        burnTotal = Math.max(burnRemaining, tag.getInt("BurnTotal"));
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