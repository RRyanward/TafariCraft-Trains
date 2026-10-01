package traincraft.entity.train.freight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Classic Traincraft Stock Car adapted as a dedicated farm-animal carrier. */
public class LivestockCar extends FreightCart {
    private static final int MAX_ANIMALS = 3;
    /*
     * TC_TRANSPORT_3_VISIBLE_R1F20A
     * Maximum three real animals, arranged front / center / rear so the
     * loaded animals themselves are visible through the open cage.
     */
    /*
     * TC_TRANSPORT_4_CORNERS_R1F18
     * Four stable floor positions: front-left, front-right,
     * rear-left, rear-right.  Keep animals clear of the center aisle
     * so heads/bodies remain visible through the side bars.
     */
    private static final double OCCUPANT_LONGITUDINAL = 0.72D;
    private static final double OCCUPANT_LATERAL = 0.16D;
    private static final double OCCUPANT_FLOOR_Y = 0.18D;

    /*
     * TC_TRANSPORT_COUPLER_SPACING_R1F18
     * These long transport bodies were inheriting the shorter classic freight
     * reach (1.20/1.15), which allowed visible body overlap when coupled.
     * Shift only this car's existing frozen spacing bands; global FC12 math is
     * untouched.
     */
    /* TC_BATCH7_R5C_MODEL_AWARE_TRANSPORT_SPACING */
    private static final double TRANSPORT_COUPLER_REACH = 1.90D;

    public LivestockCar(EntityType<? extends LivestockCar> type, Level level) { super(type, level); }
    @Override
    protected double getLeaderFacingCouplerReach() {
        return TRANSPORT_COUPLER_REACH;
    }

    @Override
    protected double getTrailingCouplerReach() {
        return TRANSPORT_COUPLER_REACH;
    }

    /*
     * TC_BATCH7_R5D_BERKSHIRE_PRIMARY_CLEARANCE
     *
     * Runtime telemetry showed the first direct link as:
     *   Berkshire 1225 -> transport = 3.000 blocks
     * while the now-correct transport -> transport link is 3.800 blocks.
     *
     * SteamTender's shared coupling base treats every SmallSteamLocomotive
     * leader as the original small-loco 1.10-block rear coupler reach. That is
     * correct for the frozen Small Steam baseline but too short for the much
     * larger Berkshire 1225/765 visual body.
     *
     * Override ONLY this follower's direct touch target for those two identical
     * Berkshire meshes. All other leaders continue through the accepted base
     * model-aware coupling logic.
     */
    @Override
    public double getCouplerTouchTargetDistance(Entity leader) {
        if (leader != null) {
            net.minecraft.resources.ResourceLocation key =
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(leader.getType());

            if (key != null && "traincraft".equals(key.getNamespace())) {
                String path = key.getPath();
                if ("locomotive_steam_berkshire_1225".equals(path)
                        || "locomotive_steam_berkshire_765".equals(path)) {
                    return 3.70D;
                }
            }
        }

        return super.getCouplerTouchTargetDistance(leader);
    }

    @Override
    public void alignCouplerSpacingToLeader(Entity leader) {
        // Same rail-only correction already used by long coaches; transport-only.
        for (int pass = 0; pass < 3; pass++) {
            super.alignCouplerSpacingToLeader(leader);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Animal && passenger.isAlive() && this.getPassengers().size() < MAX_ANIMALS;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Shift + Lead = load an incoming leashed animal or unload one.
        if (hand == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && player.getItemInHand(hand).is(Items.LEAD)) {

            if (!this.level().isClientSide) {
                Animal leashed = this.level().getEntitiesOfClass(
                                Animal.class,
                                this.getBoundingBox().inflate(8.0D),
                                animal -> animal.isAlive()
                                        && !animal.isPassenger()
                                        && animal.isLeashed()
                                        && animal.getLeashHolder() == player)
                        .stream()
                        .min(Comparator.comparingDouble(this::distanceToSqr))
                        .orElse(null);

                if (leashed != null) {
                    if (this.getPassengers().size() >= MAX_ANIMALS) {
                        player.displayClientMessage(
                                Component.literal("Livestock Car full (3/3)."),
                                true);
                        return InteractionResult.CONSUME;
                    }

                    if (leashed.startRiding(this, true)) {
                        /*
                         * r1f20c used dropLeash(false,false), which changed the
                         * server state without sending the detach packet. That
                         * is why the rope line could remain visible.
                         *
                         * Send the leash update, but do NOT drop another Lead.
                         */
                        leashed.dropLeash(true, false);

                        int seat = this.getPassengers().indexOf(leashed) + 1;
                        tcEnsureCalibrationSeat(seat);

                        player.displayClientMessage(
                                Component.literal("Livestock Car: loaded "
                                        + leashed.getName().getString()
                                        + " (" + this.getPassengers().size() + "/3)"),
                                true);
                        return InteractionResult.CONSUME;
                    }
                }

                if (!this.getPassengers().isEmpty()) {
                    tcManifestUnloadSlot(
                            player,
                            this.getPassengers().size() - 1);
                    return InteractionResult.CONSUME;
                }

                player.displayClientMessage(
                        Component.literal("Lead an animal to the car, then Sneak + right-click with the Lead."),
                        true);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Empty-hand fallback retained.
        if (hand == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && player.getItemInHand(hand).isEmpty()) {

            if (!this.level().isClientSide) {
                if (this.getPassengers().size() < MAX_ANIMALS) {
                    Animal animal = this.level().getEntitiesOfClass(
                                    Animal.class,
                                    this.getBoundingBox().inflate(3.0D),
                                    entity -> entity.isAlive()
                                            && !entity.isPassenger())
                            .stream()
                            .min(Comparator.comparingDouble(
                                    entity -> this.distanceToSqr(entity)))
                            .orElse(null);

                    if (animal != null && animal.startRiding(this, true)) {
                        if (animal.isLeashed()) {
                            animal.dropLeash(true, false);
                        }

                        int seat = this.getPassengers().indexOf(animal) + 1;
                        tcEnsureCalibrationSeat(seat);

                        player.displayClientMessage(
                                Component.literal("Livestock Car: loaded "
                                        + animal.getName().getString()
                                        + " (" + this.getPassengers().size() + "/3)"),
                                true);
                        return InteractionResult.CONSUME;
                    }
                }

                for (Entity passenger :
                        new ArrayList<>(this.getPassengers())) {
                    passenger.stopRiding();
                }
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        /*
         * All ordinary interaction paths now inherit FreightCart's open-screen
         * behavior, but createMenu() above guarantees our three-slot manifest.
         */
        return super.interact(player, hand);
    }

    /*
     * TC_TRANSPORT_MANIFEST_LIVESTOCK_R1F20C
     * GUI unload always returns the animal onto a visible leash to the player.
     */
    public boolean tcManifestUnloadSlot(Player player, int slot) {
        if (slot < 0 || slot >= this.getPassengers().size()) {
            return false;
        }

        Entity passenger = this.getPassengers().get(slot);
        passenger.stopRiding();

        if (passenger instanceof Animal animal) {
            animal.setLeashedTo(player, true);
        }

        player.displayClientMessage(
                Component.literal("Livestock Car: unloaded "
                        + passenger.getName().getString()
                        + " (" + this.getPassengers().size() + "/3)"),
                true);
        return true;
    }

    public int tcManifestUnloadAll(Player player) {
        int unloaded = 0;

        for (int slot = this.getPassengers().size() - 1; slot >= 0; slot--) {
            if (tcManifestUnloadSlot(player, slot)) {
                unloaded++;
            }
        }

        return unloaded;
    }

    /*
     * TC_TRANSPORT_MANIFEST_ROUTING_R1F20D
     * FreightCart's inherited interaction can still open a menu. Override the
     * provider itself so this car can NEVER fall back to the old Freight Cart
     * inventory screen.
     */
    @Override
    public Component getDisplayName() {
        return Component.literal("Livestock Car");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId,
            net.minecraft.world.entity.player.Inventory inventory,
            Player player) {
        return new traincraft.menu.TransportCarMenu(
                containerId, inventory, this);
    }

    /*
     * TC_TRANSPORT_OCCUPANT_SEAT_VISUAL_R1F20D
     * Same /tc visual seat 1|2|3 controls used for players now control the
     * three real animal positions too.
     */
    private static double tcDefaultSeatLong(int seat) {
        return switch (seat) {
            case 1 -> 0.72D;
            case 2 -> 0.00D;
            default -> -0.72D;
        };
    }

    private static double tcDefaultSeatY(int seat) {
        return 0.18D;
    }

    private static double tcDefaultSeatSide(int seat) {
        return switch (seat) {
            case 1 -> -0.16D;
            case 2 -> 0.16D;
            default -> -0.16D;
        };
    }

    public void tcEnsureCalibrationSeat(int seat) {
        int s = Math.max(1, Math.min(3, seat));
        String id = "livestock_car";

        if (!traincraft.config.RollingStockCalibrationConfig.hasSeat(id, s)) {
            traincraft.config.RollingStockCalibrationConfig.setSeat(
                    id,
                    s,
                    true,
                    tcDefaultSeatLong(s),
                    tcDefaultSeatY(s),
                    tcDefaultSeatSide(s),
                    false);
        }
    }

    /*
     * TC_GUI_LOAD_LIVESTOCK_R1F20G
     * GUI "Load" finds the nearest animal currently leashed to the viewing
     * player. The leash is removed only after successful boarding.
     */
    /*
     * TC_ROBUST_TRANSPORT_LOAD_R1F20H
     *
     * Find the player's actually-leashed animal up to 16 blocks away.
     * Detach before boarding, because a leashed Mob can refuse/interrupt the
     * ride transition on some entity paths. Restore leash if boarding fails.
     */
        /*
     * TC_TRANSPORT_LOAD_FINALIZE_R1F20I
     *
     * Contextual GUI "Load" calls this same method. After a successful board,
     * force-remove any remaining visible leash state so the lead is no longer
     * attached to the animal in the world.
     */
    public boolean tcManifestLoadOne(Player player) {
        if (this.getPassengers().size() >= MAX_ANIMALS) {
            player.displayClientMessage(
                    Component.literal("Livestock Car full (3/3)."),
                    true);
            return false;
        }

        Animal leashed = this.level().getEntitiesOfClass(
                        Animal.class,
                        this.getBoundingBox().inflate(16.0D),
                        animal -> animal.isAlive()
                                && !animal.isPassenger()
                                && animal.isLeashed()
                                && animal.getLeashHolder() == player)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        if (leashed == null) {
            player.displayClientMessage(
                    Component.literal("No animal on your Lead was found within 16 blocks."),
                    true);
            return false;
        }

        leashed.dropLeash(true, false);

        if (!leashed.startRiding(this, true)) {
            leashed.setLeashedTo(player, true);
            player.displayClientMessage(
                    Component.literal(leashed.getName().getString()
                            + " could not board; Lead restored."),
                    true);
            return false;
        }

        if (leashed.isLeashed()) {
            leashed.dropLeash(true, false);
        }

        int seat = this.getPassengers().indexOf(leashed) + 1;
        tcEnsureCalibrationSeat(seat);

        player.displayClientMessage(
                Component.literal("Livestock Car: loaded "
                        + leashed.getName().getString()
                        + " (" + this.getPassengers().size() + "/3)"),
                true);
        return true;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) return;

        int seat = Math.max(
                1,
                Math.min(
                        3,
                        this.getPassengers().indexOf(passenger) + 1));

        String id = "livestock_car";

        double longitudinal =
                traincraft.config.RollingStockCalibrationConfig.seatLong(
                        id, seat, tcDefaultSeatLong(seat));
        double footY =
                traincraft.config.RollingStockCalibrationConfig.seatY(
                        id, seat, tcDefaultSeatY(seat));
        double side =
                traincraft.config.RollingStockCalibrationConfig.seatSide(
                        id, seat, tcDefaultSeatSide(seat));

        /*
         * Use the SAME local axes as the /tc visual player-seat calibrator:
         * +long = front, +side = right.
         */
        double r = Math.toRadians(this.getYRot());
        double frontX = Math.sin(r);
        double frontZ = -Math.cos(r);
        double rightX = Math.cos(r);
        double rightZ = Math.sin(r);

        moveFunction.accept(
                passenger,
                this.getX() + frontX * longitudinal + rightX * side,
                this.getY() + footY,
                this.getZ() + frontZ * longitudinal + rightZ * side);
    }
}