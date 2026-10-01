/*
 * Traincraft 1.20.1 classic freight cart.
 * Step 7.3.0 ports the original 36-slot Freight Cart while deliberately reusing
 * the frozen Step 7.2.1m tender rail/coupling controller.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.freight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import traincraft.entity.train.tender.SteamTender;
import traincraft.menu.FreightCartMenu;
import traincraft.registry.TCItems;

/** Original Traincraft yellow freight cart with four rows / 36 cargo slots. */
public class FreightCart extends SteamTender {
    public static final int CARGO_SLOTS = 36;

    /* ModelFreightCart2 reaches 24 px toward its leader-facing end and 23 px
     * toward its trailing end at the same 0.8 scale used by the renderer. */
    private static final double MODEL_SCALE_BLOCKS_PER_PIXEL = 0.8D / 16.0D;
    private static final double FREIGHT_LEADER_NUB_REACH = 24.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.20
    private static final double FREIGHT_TRAILING_NUB_REACH = 23.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.15

    private final ItemStackHandler cargoInventory = new ItemStackHandler(CARGO_SLOTS);
    private boolean cargoInventoryDropped;

    public FreightCart(EntityType<? extends FreightCart> type, Level level) {
        super(type, level);
    }

    public IItemHandler getCargoInventory() {
        return this.cargoInventory;
    }

    @Override
    protected double getLeaderFacingCouplerReach() {
        return FREIGHT_LEADER_NUB_REACH;
    }

    @Override
    protected double getTrailingCouplerReach() {
        return FREIGHT_TRAILING_NUB_REACH;
    }

    /** Freight cargo is never eligible as reserve locomotive fuel. */
    @Override
    public ItemStack extractFuelStackForLocomotive() {
        return ItemStack.EMPTY;
    }

    /** A freight cart has no tender water reservoir. */
    @Override
    public int drainWaterForLocomotive(int maxAmountMb) {
        return 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Freight cart");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FreightCartMenu(containerId, inventory, this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("FreightInventory", this.cargoInventory.serializeNBT());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FreightInventory")) {
            this.cargoInventory.deserializeNBT(tag.getCompound("FreightInventory"));
        }
    }

    @Override
    public void destroy(DamageSource damageSource) {
        this.dropCargoInventory();
        super.destroy(damageSource);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            this.dropCargoInventory();
        }
        super.remove(reason);
    }

    private void dropCargoInventory() {
        if (this.level().isClientSide || this.cargoInventoryDropped) {
            return;
        }
        this.cargoInventoryDropped = true;

        for (int slot = 0; slot < CARGO_SLOTS; slot++) {
            ItemStack stack = this.cargoInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack dropped = this.cargoInventory.extractItem(slot, stack.getCount(), false);
            if (!dropped.isEmpty()) {
                Containers.dropItemStack(
                        this.level(),
                        this.getX(), this.getY() + 0.35D, this.getZ(),
                        dropped);
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return TCItems.FREIGHT_CART.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.FREIGHT_CART.get());
    }
}
