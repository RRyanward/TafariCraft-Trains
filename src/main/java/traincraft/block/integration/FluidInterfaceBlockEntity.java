package traincraft.block.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCBlockEntities;

/** The pipe-facing, persistent 64-bucket dock buffer, one block below a Fluid Transfer Track. */
public final class FluidInterfaceBlockEntity extends BlockEntity {
    public static final int CAPACITY_MB = 64000;

    private final FluidTank fluidStorage = new FluidTank(CAPACITY_MB) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> fluidStorage);

    public FluidInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.FLUID_INTERFACE.get(), pos, state);
    }

    public IFluidHandler getFluidHandler() { return fluidStorage; }
    public FluidTank getTank() { return fluidStorage; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag fluidTag = new CompoundTag();
        fluidStorage.writeToNBT(fluidTag);
        tag.put("BufferFluid", fluidTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("BufferFluid", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            fluidStorage.readFromNBT(tag.getCompound("BufferFluid"));
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
