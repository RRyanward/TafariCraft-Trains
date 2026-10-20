package traincraft.entity.train.freight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import traincraft.item.ConnectorItem;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCEntities;

/** DEV47: accepted DEV46 tanker rail/coupler geometry, with persistent Forge fluid storage. */
public final class TctTanker extends FreightCart {
    public static final int FLUID_CAPACITY_MB = 64000;

    // Vanilla water and lava have dedicated cars. Universal accepts any Forge fluid.
    private final FluidTank fluidStorage = new FluidTank(FLUID_CAPACITY_MB, this::acceptsFluid);
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> fluidStorage);

    public TctTanker(EntityType<? extends TctTanker> type, Level level) {
        super(type, level);
    }

    private boolean acceptsFluid(FluidStack candidate) {
        if (candidate.isEmpty()) return false;
        if (this.getType() == TCEntities.TCT_TANKER_WATER.get()) {
            return candidate.getFluid() == Fluids.WATER;
        }
        if (this.getType() == TCEntities.TCT_TANKER_LAVA.get()) {
            return candidate.getFluid() == Fluids.LAVA;
        }
        return true;
    }

    public IFluidHandler getFluidHandler() { return fluidStorage; }
    public int getStoredFluidAmount() { return fluidStorage.getFluidAmount(); }
    public FluidStack getStoredFluid() { return fluidStorage.getFluid().copy(); }

    /**
     * DEV50d3T CANDIDATE: live loaded mass for the existing shared consist mass hook.
     * 1,000 mB represents one cubic metre. Density comes from the actual Forge
     * FluidType (kg/m3); vanilla water and lava therefore differ naturally.
     * Does not change coupling, path steering, braking code or fluid capacity.
     * Empty mass is the previous fallback for an unrecognised freight subtype.
     */
    public static final double EMPTY_MASS_TONNES = 20.0D;

    @Override
    public double getRealismMassOverrideTons() {
        FluidStack stack = this.fluidStorage.getFluid();
        int amount = this.fluidStorage.getFluidAmount();
        if (amount <= 0 || stack.isEmpty()) return EMPTY_MASS_TONNES;
        int densityKgPerCubicMetre = stack.getFluid().getFluidType().getDensity(stack);
        // Reject invalid fluid-type values without corrupting consist physics.
        // 10,000 kg/m3 is a conservative safety ceiling for modded fluids.
        if (densityKgPerCubicMetre <= 0 || densityKgPerCubicMetre > 10000) {
            return EMPTY_MASS_TONNES;
        }
        return EMPTY_MASS_TONNES + ((double) amount * densityKgPerCubicMetre / 1000000.0D);
    }


    /** Explain rejected transfers for pipe docks and players, without mutating storage. */
    @Nullable
    public Component fluidRejectionMessage(FluidStack offered) {
        if (offered.isEmpty()) return null;
        String label = getDisplayName().getString();
        String offeredName = offered.getDisplayName().getString();
        if (getType() == TCEntities.TCT_TANKER_WATER.get() && offered.getFluid() != Fluids.WATER) {
            return Component.literal(label + " only takes Water. Wrong fluid: " + offeredName + ".");
        }
        if (getType() == TCEntities.TCT_TANKER_LAVA.get() && offered.getFluid() != Fluids.LAVA) {
            return Component.literal(label + " only takes Lava. Wrong fluid: " + offeredName + ".");
        }
        FluidStack current = fluidStorage.getFluid();
        if (!current.isEmpty() && !current.isFluidEqual(offered)) {
            return Component.literal(label + " contains " + current.getDisplayName().getString()
                    + ". Empty the tank before loading " + offeredName + ".");
        }
        if (fluidStorage.getFluidAmount() >= FLUID_CAPACITY_MB) {
            return Component.literal(label + " is full (64,000 mB).");
        }
        return null;
    }

    /** Manual fill: crouch + right-click with one filled Forge fluid container. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // Never intercept the frozen rolling-stock coupling tool.
        if (held.getItem() instanceof ConnectorItem) return super.interact(player, hand);
        if (!player.isShiftKeyDown() || held.isEmpty()) return super.interact(player, hand);

        IFluidHandlerItem itemTank = FluidUtil.getFluidHandler(held).resolve().orElse(null);
        if (itemTank == null) return super.interact(player, hand);
        FluidStack offered = itemTank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return super.interact(player, hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;

        Component rejection = fluidRejectionMessage(offered);
        if (rejection != null) {
            player.displayClientMessage(rejection, true);
            return InteractionResult.CONSUME;
        }
        if (held.getCount() != 1) {
            player.displayClientMessage(Component.literal("Hold one filled fluid container to manually load this tanker."), true);
            return InteractionResult.CONSUME;
        }
        int room = FLUID_CAPACITY_MB - fluidStorage.getFluidAmount();
        if (offered.getAmount() > room) {
            player.displayClientMessage(Component.literal("Not enough space: needs " + offered.getAmount()
                    + " mB; available " + room + " mB."), true);
            return InteractionResult.CONSUME;
        }
        int before = fluidStorage.getFluidAmount();
        // Forge handles bucket and modded container conversion safely.
        FluidActionResult result = FluidUtil.tryEmptyContainer(held, fluidStorage, room, player, true);
        if (!result.isSuccess()) {
            player.displayClientMessage(Component.literal("Cannot transfer fluid from this container."), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, result.getResult());
        int moved = fluidStorage.getFluidAmount() - before;
        // DEV55S1: manual bucket/canister fill also counts as successful pumping.
        if (moved > 0) traincraft.audio.Dev55OperationalSounds.pump(level(), this, true);
        player.displayClientMessage(Component.literal("Loaded " + moved + " mB "
                + offered.getDisplayName().getString() + ". Tank: " + fluidStorage.getFluidAmount()
                + "/" + FLUID_CAPACITY_MB + " mB."), true);
        return InteractionResult.CONSUME;
    }

    // 6.93-block original OBJ end-to-end including draft gear; FC12 rail/coupling unchanged.
    @Override protected double getLeaderFacingCouplerReach() { return 3.465D; }
    @Override protected double getTrailingCouplerReach() { return 3.465D; }
    @Override public Component getDisplayName() { return getType().getDescription(); }

    // DEV47b: read-only tank display. Parent SteamTender opens this via NetworkHooks.
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new traincraft.menu.TctFluidMenu(id, inventory, this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag fluidTag = new CompoundTag();
        fluidStorage.writeToNBT(fluidTag);
        tag.put("TctStoredFluid", fluidTag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TctStoredFluid", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            fluidStorage.readFromNBT(tag.getCompound("TctStoredFluid"));
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCap = LazyOptional.of(() -> fluidStorage);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidCap.cast();
        return super.getCapability(cap, side);
    }
}
