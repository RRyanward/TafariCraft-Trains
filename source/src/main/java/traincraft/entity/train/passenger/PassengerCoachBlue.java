/*
 * Traincraft 1.20.1 classic Passenger Blue coach.
 * Step 7.3.1 ports the first classic rideable passenger vehicle while reusing
 * the frozen Step 7.2.1m / Step 7.3.0a coupling controller unchanged.
 * Step 7.3.1b adds long-coach-only curve clearance plus three usable classic
 * seating bays without changing the global tender/freight movement baseline.
 * Step 7.3.1c added focused seat telemetry and a first rider-height tune.
 * Step 7.3.1d uses the actual entity hit point to choose the clicked window bay
 * and lowers the rider again so the seated player remains inside the roof line.
 * Step 7.3.1e expands only the mouse-pick radius to the full coach body and
 * synchronizes clicked seat occupancy so the client camera/render uses the
 * exact front/middle/rear bay selected on the server.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.passenger;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import traincraft.entity.train.tender.SteamTender;
import traincraft.item.ConnectorItem;
import traincraft.registry.TCItems;

/**
 * Classic Traincraft "Passenger Blue" coach.
 *
 * The original 1.7.10 EntityPassengerBlue is a rideable, non-storage,
 * non-powered rolling-stock vehicle.  Minecraft 1.20.1's normal passenger
 * mounting is used here, while the proven SteamTender rail-following class is
 * reused only as the coupling/movement base.
 */
public class PassengerCoachBlue extends SteamTender {
    /* ModelPassenger6 reaches 36 px from the entity origin to either visible
     * end coupler: 1.80 blocks at the classic 0.8 render scale.  The moving
     * chained controller intentionally allows a small amount of classic slack,
     * so an exact 1.80 reach can let this much longer coach visually intrude into
     * the preceding freight car while the consist is pulling.  Keep the model
     * measurement, then add only 0.10 block of passenger-coach operating
     * clearance.  This changes no global tender/freight coupling constants. */
    private static final double MODEL_SCALE_BLOCKS_PER_PIXEL = 0.8D / 16.0D;
    private static final double PASSENGER_MODEL_COUPLER_REACH =
            36.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.80 blocks
    private static final double PASSENGER_COUPLER_CLEARANCE = 0.10D;
    private static final double PASSENGER_COUPLER_REACH =
            PASSENGER_MODEL_COUPLER_REACH + PASSENGER_COUPLER_CLEARANCE; // 1.90 blocks

    /* The long rigid coach swings much farther toward the inside of a vanilla
     * 90-degree curve than the tender/freight bodies.  Keep straight spacing
     * exactly as Step 7.3.1a, but add a passenger-only corner buffer so its body
     * cannot visually pass through the car ahead while the couplers negotiate
     * opposite legs of the turn. */
    private static final double PASSENGER_CURVE_CLEARANCE = 0.35D;

    /* Three visible seating bays in ModelPassenger6.  Offsets are measured from
     * the coach center along its server-synchronized longitudinal axis. */
    private static final Logger SEAT_LOGGER = LogUtils.getLogger();
    private static final int PASSENGER_SEAT_COUNT = 3;
    private static final double[] PASSENGER_SEAT_FORWARD = {-1.05D, 0.0D, 1.05D};

    /* EntityType width stays narrow so rolling-stock collision/rail behavior is
     * unchanged.  getPickRadius() expands only mouse selection.  The rendered
     * coach reaches roughly 1.8 blocks from center, while the 1.05-wide entity
     * box reaches only 0.525; +1.40 comfortably covers both end window bays. */
    private static final float PASSENGER_MOUSE_PICK_RADIUS = 1.40F;

    /* Plain Java seat maps are server-local.  7.3.1c/d therefore selected the
     * intended seat on the server but the client could still render/camera the
     * first passenger in seat 1.  Sync the occupant entity id of every bay. */
    private static final EntityDataAccessor<Integer> DATA_SEAT_1_ENTITY_ID =
            SynchedEntityData.defineId(PassengerCoachBlue.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_2_ENTITY_ID =
            SynchedEntityData.defineId(PassengerCoachBlue.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_3_ENTITY_ID =
            SynchedEntityData.defineId(PassengerCoachBlue.class, EntityDataSerializers.INT);

    /* 7.3.1c used 0.42.  Runtime telemetry placed the rider anchor at only
     * +0.07 above coach Y after Minecraft's -0.35 riding offset, yet the player
     * model still protruded through the roof.  Lower the anchor another 0.42 so
     * the final rider position is coachY-0.35.  This is visual seating only. */
    private static final double PASSENGER_SEAT_HEIGHT = 0.00D;

    private final Map<UUID, Integer> passengerSeatAssignments = new HashMap<>();
    private final Set<UUID> passengerSeatPositionLogged = new HashSet<>();

    public PassengerCoachBlue(EntityType<? extends PassengerCoachBlue> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SEAT_1_ENTITY_ID, -1);
        this.entityData.define(DATA_SEAT_2_ENTITY_ID, -1);
        this.entityData.define(DATA_SEAT_3_ENTITY_ID, -1);
    }

    /**
     * Expand only the client/server interaction ray target, not the physical
     * rolling-stock bounding box.  This makes the front and rear window bays
     * clickable without making the long coach collide as a 3.8-block square.
     */
    @Override
    public float getPickRadius() {
        return PASSENGER_MOUSE_PICK_RADIUS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.cleanupSeatAssignments();
        }
    }

    @Override
    protected double getLeaderFacingCouplerReach() {
        return PASSENGER_COUPLER_REACH;
    }

    @Override
    protected double getTrailingCouplerReach() {
        return PASSENGER_COUPLER_REACH;
    }

    /** Passenger-only 90-degree clearance; tender/freight curve bands are untouched. */
    @Override
    protected double getChainedCurveNearDistance(Entity leader) {
        return super.getChainedCurveNearDistance(leader) + PASSENGER_CURVE_CLEARANCE;
    }

    @Override
    protected double getChainedCurveStopDistance(Entity leader) {
        return super.getChainedCurveStopDistance(leader) + PASSENGER_CURVE_CLEARANCE;
    }

    @Override
    protected double getChainedCurveFarDistance(Entity leader) {
        // Keep the proven catch-up threshold unchanged so the longer coach still
        // closes promptly after the corner; only the minimum body clearance grows.
        return super.getChainedCurveFarDistance(leader);
    }

    /**
     * The frozen 7.2.1m connector snap was deliberately capped at 0.85 blocks
     * because the short tender only needed about half a block of correction.
     * A 3.6-block-long passenger coach can be placed with more than 0.85 blocks
     * of initial overlap.  Re-run the exact same rail-only base correction a few
     * times so the coach reaches its model-aware target immediately instead of
     * remaining inside the freight cart until the train starts moving.
     */
    @Override
    public void alignCouplerSpacingToLeader(Entity leader) {
        for (int pass = 0; pass < 3; pass++) {
            super.alignCouplerSpacingToLeader(leader);
            if (leader == null || leader.isRemoved()) {
                return;
            }
            double error = Math.abs(
                    this.position().distanceTo(leader.position())
                            - this.getCouplerTouchTargetDistance(leader));
            if (error <= 0.025D) {
                return;
            }
        }
    }

    /** Passenger coaches never feed coal into a locomotive. */
    @Override
    public ItemStack extractFuelStackForLocomotive() {
        return ItemStack.EMPTY;
    }

    /** Passenger coaches have no locomotive-supply water reservoir. */
    @Override
    public int drainWaterForLocomotive(int maxAmountMb) {
        return 0;
    }

    /**
     * Minecraft supplies the exact point on the entity that was right-clicked
     * through interactAt.  Use that hit point instead of the player's feet so
     * the three visible window bays remain independently selectable even when
     * the player stands in roughly the same place and only moves the crosshair.
     */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hitPos, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ConnectorItem) {
            // Let the normal interact path keep the proven connector behavior.
            return InteractionResult.PASS;
        }
        return tryBoardPassenger(player, hitPos);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ConnectorItem) {
            return ConnectorItem.handleRollingStockClick(this, player, hand);
        }

        // Fallback for interaction paths that do not provide an entity hit point.
        return tryBoardPassenger(player, null);
    }

    private InteractionResult tryBoardPassenger(Player player, @Nullable Vec3 hitPos) {
        if (!this.level().isClientSide && !this.getPassengers().contains(player)) {
            if (this.getPassengers().size() < PASSENGER_SEAT_COUNT) {
                double along = hitPos != null
                        ? hitAlongCoach(hitPos)
                        : playerAlongCoach(player);
                int seat = seatForClickedAlong(along);

                if (isSeatOccupied(seat)) {
                    player.displayClientMessage(Component.literal(
                            "That passenger seat is occupied."), true);
                    return InteractionResult.CONSUME;
                }

                this.passengerSeatAssignments.put(player.getUUID(), seat);
                this.setSyncedSeatOccupant(seat, player.getId());
                this.passengerSeatPositionLogged.remove(player.getUUID());

                String source = hitPos != null ? "hit" : "player-fallback";
                String hitText = hitPos == null
                        ? "n/a"
                        : "(" + fmt(hitPos.x) + "," + fmt(hitPos.y) + "," + fmt(hitPos.z) + ")";

                SEAT_LOGGER.info(
                        "[TC-SEAT] BOARD coach={} player={} source={} hit={} along={} requestedSeat={} seatOffset={} coachY={} seatHeight={} playerY={}",
                        this.getId(), player.getGameProfile().getName(), source, hitText,
                        fmt(along), seat + 1, fmt(PASSENGER_SEAT_FORWARD[seat]),
                        fmt(this.getY()), fmt(PASSENGER_SEAT_HEIGHT), fmt(player.getY()));

                player.displayClientMessage(Component.literal(
                        "Passenger seat: " + (seat + 1) + "/3 ("
                                + seatName(seat) + ")"), true);

                if (!player.startRiding(this)) {
                    this.passengerSeatAssignments.remove(player.getUUID());
                    this.setSyncedSeatOccupant(seat, -1);
                }
            } else {
                player.displayClientMessage(Component.literal("Passenger coach is full."), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < PASSENGER_SEAT_COUNT;
    }

    /**
     * Place each rider in one of the three visible seating bays instead of
     * stacking every passenger at the minecart entity origin.  The offsets use
     * the synchronized train-facing yaw, so riders stay in their bay through
     * reverse movement and curves.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        int fallbackSeat = this.getPassengers().indexOf(passenger);
        if (fallbackSeat < 0) {
            return;
        }
        fallbackSeat = Math.min(fallbackSeat, PASSENGER_SEAT_COUNT - 1);
        int seat = this.getSyncedSeatForPassenger(passenger);
        if (seat < 0) {
            seat = this.passengerSeatAssignments.getOrDefault(passenger.getUUID(), fallbackSeat);
        }
        seat = Math.max(0, Math.min(seat, PASSENGER_SEAT_COUNT - 1));

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double forwardX = Math.sin(radians);
        double forwardZ = -Math.cos(radians);
        double forwardOffset = PASSENGER_SEAT_FORWARD[seat];

        double seatX = this.getX() + forwardX * forwardOffset;
        double seatY = this.getY() + PASSENGER_SEAT_HEIGHT + passenger.getMyRidingOffset();
        double seatZ = this.getZ() + forwardZ * forwardOffset;

        moveFunction.accept(passenger, seatX, seatY, seatZ);

        if (!this.level().isClientSide && this.passengerSeatPositionLogged.add(passenger.getUUID())) {
            SEAT_LOGGER.info(
                    "[TC-SEAT] POSITION coach={} passenger={} seat={} offset={} facingYaw={} seatWorld=({},{},{}) coachWorld=({},{},{}) ridingOffset={}",
                    this.getId(), passenger.getName().getString(), seat + 1,
                    fmt(forwardOffset), fmt(this.getTrainFacingYaw()),
                    fmt(seatX), fmt(seatY), fmt(seatZ),
                    fmt(this.getX()), fmt(this.getY()), fmt(this.getZ()),
                    fmt(passenger.getMyRidingOffset()));
        }
    }

    /** Project the player's standing position onto the coach's long axis. */
    private double playerAlongCoach(Player player) {
        double radians = Math.toRadians(this.getTrainFacingYaw());
        double forwardX = Math.sin(radians);
        double forwardZ = -Math.cos(radians);
        return (player.getX() - this.getX()) * forwardX
                + (player.getZ() - this.getZ()) * forwardZ;
    }

    /**
     * Project the entity-interaction hit point onto the same long axis used by
     * the three seat offsets.  In 1.20.1 interactAt supplies an entity-relative
     * hit vector; the large-value fallback also tolerates an absolute vector.
     */
    private double hitAlongCoach(Vec3 hitPos) {
        double localX = hitPos.x;
        double localZ = hitPos.z;
        if (Math.abs(localX) > 6.0D || Math.abs(localZ) > 6.0D) {
            localX -= this.getX();
            localZ -= this.getZ();
        }

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double forwardX = Math.sin(radians);
        double forwardZ = -Math.cos(radians);
        return localX * forwardX + localZ * forwardZ;
    }

    /**
     * Map the clicked longitudinal coordinate directly to one visible window
     * bay.  Boundaries are halfway between the three classic seat centers.
     * We intentionally do not redirect an occupied click to another seat:
     * where the player clicks is the seat being requested.
     */
    private int seatForClickedAlong(double along) {
        double boundary = (PASSENGER_SEAT_FORWARD[2] - PASSENGER_SEAT_FORWARD[1]) * 0.5D;
        if (along < -boundary) {
            return 0;
        }
        if (along > boundary) {
            return 2;
        }
        return 1;
    }

    private boolean isSeatOccupied(int seat) {
        for (Entity current : this.getPassengers()) {
            int assigned = this.getSyncedSeatForPassenger(current);
            if (assigned < 0) {
                assigned = this.passengerSeatAssignments.getOrDefault(current.getUUID(), -1);
            }
            if (assigned == seat) {
                return true;
            }
        }
        return false;
    }

    private void setSyncedSeatOccupant(int seat, int entityId) {
        switch (seat) {
            case 0 -> this.entityData.set(DATA_SEAT_1_ENTITY_ID, entityId);
            case 1 -> this.entityData.set(DATA_SEAT_2_ENTITY_ID, entityId);
            case 2 -> this.entityData.set(DATA_SEAT_3_ENTITY_ID, entityId);
            default -> { }
        }
    }

    private int getSyncedSeatForPassenger(Entity passenger) {
        int id = passenger.getId();
        if (this.entityData.get(DATA_SEAT_1_ENTITY_ID) == id) {
            return 0;
        }
        if (this.entityData.get(DATA_SEAT_2_ENTITY_ID) == id) {
            return 1;
        }
        if (this.entityData.get(DATA_SEAT_3_ENTITY_ID) == id) {
            return 2;
        }
        return -1;
    }

    /** Clear synced seat ids as soon as their rider dismounts. */
    private void cleanupSeatAssignments() {
        Set<Integer> passengerIds = new HashSet<>();
        Set<UUID> passengerUuids = new HashSet<>();
        for (Entity passenger : this.getPassengers()) {
            passengerIds.add(passenger.getId());
            passengerUuids.add(passenger.getUUID());
        }

        if (!passengerIds.contains(this.entityData.get(DATA_SEAT_1_ENTITY_ID))) {
            this.entityData.set(DATA_SEAT_1_ENTITY_ID, -1);
        }
        if (!passengerIds.contains(this.entityData.get(DATA_SEAT_2_ENTITY_ID))) {
            this.entityData.set(DATA_SEAT_2_ENTITY_ID, -1);
        }
        if (!passengerIds.contains(this.entityData.get(DATA_SEAT_3_ENTITY_ID))) {
            this.entityData.set(DATA_SEAT_3_ENTITY_ID, -1);
        }
        this.passengerSeatAssignments.keySet().removeIf(uuid -> !passengerUuids.contains(uuid));
        this.passengerSeatPositionLogged.removeIf(uuid -> !passengerUuids.contains(uuid));
    }

    private static String seatName(int seat) {
        return switch (seat) {
            case 0 -> "front";
            case 1 -> "middle";
            case 2 -> "rear";
            default -> "unknown";
        };
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Passenger Blue");
    }

    /** There is no inventory screen for the classic passenger coach. */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected Item getDropItem() {
        return TCItems.PASSENGER_COACH_BLUE.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.PASSENGER_COACH_BLUE.get());
    }
}
