package traincraft.block.integration;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCBlockEntities;

public class RailPowerInterfaceBlockEntity extends BlockEntity {
    public static final int CAPACITY = 100_000;
    private static final int FE_PER_TICK = 40;
    private static final double AE_PER_TICK = 1.0D;

    private static Method ae2DrawMethod;
    private static boolean ae2LookupDone;

    private final ManagedEnergyStorage energy = new ManagedEnergyStorage();
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    private boolean lastPoweredByME;

    public RailPowerInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.RAIL_POWER_INTERFACE.get(), pos, state);
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    public boolean wasLastPoweredByME() {
        return lastPoweredByME;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RailPowerInterfaceBlockEntity be) {
        boolean mePower = be.drawFromME(level, pos);
        boolean fePower = false;

        if (!mePower) {
            be.pullAdjacentForgeEnergy(level, pos);
            fePower = be.energy.extractEnergy(FE_PER_TICK, true) >= FE_PER_TICK;
            if (fePower) {
                be.energy.extractEnergy(FE_PER_TICK, false);
            }
        }

        boolean powered = mePower || fePower;
        be.lastPoweredByME = mePower;

        if (state.hasProperty(RailPowerInterfaceBlock.POWERED)
                && state.getValue(RailPowerInterfaceBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(RailPowerInterfaceBlock.POWERED, powered), 3);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    private boolean drawFromME(Level level, BlockPos pos) {
        if (!ModList.get().isLoaded("ae2")) return false;

        try {
            if (!ae2LookupDone) {
                ae2LookupDone = true;
                Class<?> bridge = Class.forName("traincraft.compat.ae2.AE2RailPowerBridge");
                ae2DrawMethod = bridge.getMethod(
                        "drawPower",
                        Level.class,
                        BlockPos.class,
                        double.class);
            }
            if (ae2DrawMethod == null) return false;
            return Boolean.TRUE.equals(ae2DrawMethod.invoke(null, level, pos, AE_PER_TICK));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional integration: FE fallback remains fully functional.
            ae2DrawMethod = null;
            return false;
        }
    }

    private void pullAdjacentForgeEnergy(Level level, BlockPos pos) {
        int needed = Math.min(FE_PER_TICK, CAPACITY - energy.getEnergyStored());
        if (needed <= 0) return;

        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (neighbor == null) continue;

            var cap = neighbor.getCapability(
                    ForgeCapabilities.ENERGY,
                    direction.getOpposite());

            IEnergyStorage source = cap.orElse(null);
            if (source == null || !source.canExtract()) continue;

            int pulled = source.extractEnergy(needed, false);
            if (pulled > 0) {
                energy.receiveEnergy(pulled, false);
                return;
            }
        }
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
            super(CAPACITY, 4_000, FE_PER_TICK);
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
