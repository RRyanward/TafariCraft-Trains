/*
 * Traincraft 1.20.1 classic caboose.
 * Step 7.3.2 ports the original rideable EntityCaboose while preserving the
 * confirmed Step 7.2.1m consist controller and all 7.3.x rolling-stock fixes.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.caboose;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import traincraft.entity.train.tender.SteamTender;
import traincraft.item.ConnectorItem;
import traincraft.registry.TCItems;

/** Classic Traincraft black/gold caboose using the original ModelCaboose art. */
public class ClassicCaboose extends SteamTender {
    private static final double MODEL_SCALE_BLOCKS_PER_PIXEL = 0.8D / 16.0D;

    /* Original ModelCaboose spans about x=-19..+20.  Use those visible end
     * reaches directly so mixed consists settle by the rendered body ends
     * without changing any global tender/freight/passenger spacing constants. */
    private static final double CABOOSE_LEADER_COUPLER_REACH =
            20.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.00 blocks
    private static final double CABOOSE_TRAILING_COUPLER_REACH =
            19.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 0.95 blocks


    /* Runtime telemetry from the full five-car consist showed the caboose
     * stalling on a curve chord at center=2.021 while its model-aware touch
     * target was 2.900.  The actual rail-path distance was still valid; only the
     * straight-line chord was short.  Relax the caboose-only curve stop/near
     * band slightly so reverse travel does not dead-stop before the corner. */
    private static final double CABOOSE_CURVE_CHORD_RELIEF = 0.20D;

    /* At the next handoff the lead locomotive was almost perpendicular to the
     * caboose rail.  A tiny projection changed sign and flipped the caboose's
     * directed tangent, sending it away from the consist.  Require a meaningful
     * projection before replacing the proven previous rail-path direction. */
    private static final double CABOOSE_DIRECTION_PROJECTION_RATIO = 0.35D;

    /* The small steam locomotive tops out well below this.  Keeping the caboose
     * catch-up below 0.70 blocks/tick (~50.4 km/h) gives it enough reserve to
     * close a gap without allowing a one-car runaway to accelerate into the
     * 60+ km/h global emergency ceiling. */
    private static final double CABOOSE_MAX_CATCHUP_SPEED = 0.70D;

    /* The physical minecart box stays compact for rail behavior; this only makes
     * the rendered ends of the caboose easy to right-click. */
    private static final float CABOOSE_MOUSE_PICK_RADIUS = 0.70F;

    /* Match the rider-height baseline proven by Passenger Blue 7.3.1d/e. */
    private static final double CABOOSE_RIDER_HEIGHT = 0.00D;

    public ClassicCaboose(EntityType<? extends ClassicCaboose> type, Level level) {
        super(type, level);
    }

    @Override
    public float getPickRadius() {
        return CABOOSE_MOUSE_PICK_RADIUS;
    }

    @Override
    protected double getLeaderFacingCouplerReach() {
        return CABOOSE_LEADER_COUPLER_REACH;
    }

    @Override
    protected double getTrailingCouplerReach() {
        return CABOOSE_TRAILING_COUPLER_REACH;
    }


    @Override
    protected double getChainedCurveNearDistance(Entity leader) {
        return Math.max(0.25D, super.getChainedCurveNearDistance(leader)
                - CABOOSE_CURVE_CHORD_RELIEF);
    }

    @Override
    protected double getChainedCurveStopDistance(Entity leader) {
        return Math.max(0.20D, super.getChainedCurveStopDistance(leader)
                - CABOOSE_CURVE_CHORD_RELIEF);
    }

    @Override
    protected double getChainedDirectionProjectionRatio(Entity leader) {
        return CABOOSE_DIRECTION_PROJECTION_RATIO;
    }

    @Override
    protected double getChainedMaxCatchupSpeed(Entity leader) {
        return CABOOSE_MAX_CATCHUP_SPEED;
    }

    /** Cabooses never feed coal into the locomotive. */
    @Override
    public ItemStack extractFuelStackForLocomotive() {
        return ItemStack.EMPTY;
    }

    /** Cabooses have no tender water reservoir. */
    @Override
    public int drainWaterForLocomotive(int maxAmountMb) {
        return 0;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ConnectorItem) {
            return ConnectorItem.handleRollingStockClick(this, player, hand);
        }

        if (!this.level().isClientSide && !this.getPassengers().contains(player)) {
            if (this.getPassengers().isEmpty()) {
                player.startRiding(this);
            } else {
                player.displayClientMessage(Component.literal("Caboose seat is occupied."), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** Original EntityCaboose carried one centered rider. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        moveFunction.accept(
                passenger,
                this.getX(),
                this.getY() + CABOOSE_RIDER_HEIGHT + passenger.getMyRidingOffset(),
                this.getZ());
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Caboose");
    }

    /** The original standard caboose has no inventory screen. */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected Item getDropItem() {
        return TCItems.CABOOSE.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.CABOOSE.get());
    }
}
