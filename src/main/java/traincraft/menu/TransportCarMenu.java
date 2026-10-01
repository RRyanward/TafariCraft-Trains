package traincraft.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import traincraft.entity.train.freight.LivestockCar;
import traincraft.entity.train.freight.MobTransportCar;
import traincraft.registry.TCMenus;

/**
 * TC_TRANSPORT_GUI_PORTRAITS_R1F20I
 *
 * Compact manifest used by both transport cars.
 * Row buttons are contextual:
 *   - occupied row -> Unload that row
 *   - empty row    -> Load next valid target into the car
 */
public final class TransportCarMenu extends net.minecraft.world.inventory.AbstractContainerMenu {
    public static final int BUTTON_ROW_1 = 0;
    public static final int BUTTON_ROW_2 = 1;
    public static final int BUTTON_ROW_3 = 2;
    public static final int BUTTON_UNLOAD_ALL = 3;

    private final Player viewingPlayer;
    private final int targetEntityId;

    public TransportCarMenu(
            int containerId,
            Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readInt());
    }

    public TransportCarMenu(
            int containerId,
            Inventory playerInventory,
            Entity target) {
        this(containerId, playerInventory, target.getId());
    }

    private TransportCarMenu(
            int containerId,
            Inventory playerInventory,
            int targetEntityId) {
        super(TCMenus.TRANSPORT_CAR_MANIFEST.get(), containerId);
        this.viewingPlayer = playerInventory.player;
        this.targetEntityId = targetEntityId;
    }

    public Entity target() {
        return this.viewingPlayer.level().getEntity(this.targetEntityId);
    }

    public int passengerCount() {
        Entity target = target();
        return target == null ? 0 : Math.min(3, target.getPassengers().size());
    }

    public boolean hasPassenger(int slot) {
        return slot >= 0 && slot < passengerCount();
    }

    public Entity passengerEntity(int slot) {
        Entity target = target();
        if (target == null || slot < 0 || slot >= target.getPassengers().size()) {
            return null;
        }
        return target.getPassengers().get(slot);
    }

    public Component passengerName(int slot) {
        Entity passenger = passengerEntity(slot);
        return passenger == null
                ? Component.literal("Empty")
                : passenger.getDisplayName();
    }

    public boolean locked() {
        Entity target = target();
        return target instanceof MobTransportCar mob && mob.tcManifestLocked();
    }

    public boolean isPrisonerCar() {
        return target() instanceof MobTransportCar;
    }

    public boolean rowOccupied(int slot) {
        return hasPassenger(slot);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        Entity target = target();

        if (target instanceof LivestockCar livestock) {
            if (buttonId >= BUTTON_ROW_1 && buttonId <= BUTTON_ROW_3) {
                int slot = buttonId;
                return rowOccupied(slot)
                        ? livestock.tcManifestUnloadSlot(player, slot)
                        : livestock.tcManifestLoadOne(player);
            }

            if (buttonId == BUTTON_UNLOAD_ALL) {
                livestock.tcManifestUnloadAll(player);
                return true;
            }
            return false;
        }

        if (target instanceof MobTransportCar mob) {
            if (mob.tcManifestLocked()) {
                player.displayClientMessage(
                        Component.literal("Mob / Prisoner Car is locked. Unlock it with the owner's Wrench."),
                        true);
                return false;
            }

            if (buttonId >= BUTTON_ROW_1 && buttonId <= BUTTON_ROW_3) {
                int slot = buttonId;
                return rowOccupied(slot)
                        ? mob.tcManifestUnloadSlot(player, slot)
                        : mob.tcManifestLoadOne(player);
            }

            if (buttonId == BUTTON_UNLOAD_ALL) {
                mob.tcManifestUnloadAll(player);
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        Entity target = target();
        return target != null
                && target.isAlive()
                && player.distanceToSqr(target) <= 64.0D;
    }
}