package traincraft.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import traincraft.entity.train.freight.TctTanker;
import traincraft.registry.TCMenus;

/** Read-only, server-synchronized TCT fluid indicator. No inventory slots or transfers. */
public final class TctFluidMenu extends AbstractContainerMenu {
    // Four signed-short data slots encode 32-bit fluid amount and 32-bit registry ID.
    private final ContainerData readout;
    private final TctTanker tanker;

    public TctFluidMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        super(TCMenus.TCT_TANKER.get(), id);
        Entity entity = inventory.player.level().getEntity(extra.readInt());
        if (!(entity instanceof TctTanker tct)) {
            throw new IllegalArgumentException("TCT fluid menu requires a tanker entity");
        }
        this.tanker = tct;
        this.readout = new SimpleContainerData(4);
        addDataSlots(readout);
    }

    public TctFluidMenu(int id, Inventory inventory, TctTanker tanker) {
        super(TCMenus.TCT_TANKER.get(), id);
        this.tanker = tanker;
        this.readout = new ContainerData() {
            @Override public int get(int index) {
                FluidStack fluid = tanker.getStoredFluid();
                int amount = fluid.getAmount();
                int registryId = fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
                return switch (index) {
                    case 0 -> amount & 0xFFFF;
                    case 1 -> (amount >>> 16) & 0xFFFF;
                    case 2 -> registryId & 0xFFFF;
                    case 3 -> (registryId >>> 16) & 0xFFFF;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 4; }
        };
        addDataSlots(readout);
    }

    public int amountMb() {
        return (readout.get(0) & 0xFFFF) | ((readout.get(1) & 0xFFFF) << 16);
    }

    public int capacityMb() { return TctTanker.FLUID_CAPACITY_MB; }

    public Fluid fluidType() {
        int registryId = (readout.get(2) & 0xFFFF) | ((readout.get(3) & 0xFFFF) << 16);
        return BuiltInRegistries.FLUID.byId(registryId);
    }

    public String tankerName() { return tanker.getDisplayName().getString(); }

    @Override public boolean stillValid(Player player) {
        return tanker.isAlive() && !tanker.isRemoved() && player.distanceToSqr(tanker) <= 64.0D;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
