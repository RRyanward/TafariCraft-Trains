package traincraft.block.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCBlockEntities;

public final class TCBatteryBlockEntity extends BlockEntity {
    public static final int CAPACITY = 500_000;
    public static final int MAX_TRANSFER = 10_000;

    private final ManagedEnergyStorage energy = new ManagedEnergyStorage();
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public TCBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.TC_BATTERY.get(), pos, state);
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setStored(tag.getInt("Energy"));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    private final class ManagedEnergyStorage extends EnergyStorage {
        private ManagedEnergyStorage() {
            super(CAPACITY, MAX_TRANSFER, MAX_TRANSFER);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int changed = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && changed > 0) setChanged();
            return changed;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int changed = super.extractEnergy(maxExtract, simulate);
            if (!simulate && changed > 0) setChanged();
            return changed;
        }

        private void setStored(int value) {
            energy = Math.max(0, Math.min(CAPACITY, value));
            setChanged();
        }
    }
}
