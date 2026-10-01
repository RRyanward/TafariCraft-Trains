package traincraft.entity.train.freight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import traincraft.registry.TCItems;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import traincraft.item.TransportRopeItem;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** Secure companion to the Stock Car for non-animal mobs. */
public class MobTransportCar extends FreightCart {
    private static final int MAX_MOBS = 3;
    /*
     * TC_TRANSPORT_3_VISIBLE_R1F20A
     * Maximum three real mobs/players. A locked passenger cannot dismount,
     * operate the wrench lock from inside, or break this car in survival.
     */
    /*
     * TC_TRANSPORT_4_CORNERS_R1F18
     * Mob / prisoner occupants use the same four-corner layout as livestock.
     */
    private static final double OCCUPANT_LONGITUDINAL = 0.72D;
    private static final double OCCUPANT_LATERAL = 0.16D;
    private static final double OCCUPANT_FLOOR_Y = 0.18D;

    /* TC_BATCH7_R5C_MODEL_AWARE_TRANSPORT_SPACING */
    private static final double TRANSPORT_COUPLER_REACH = 1.90D;
    /*
     * TC_PRISONER_LOCK_R1F18A
     *
     * Sneak + Traincraft Wrench toggles the prisoner door lock.
     * The state is server-authoritative and persisted with the car.
     * Locked occupants stay mounted until the car is unlocked, destroyed,
     * or the occupant is no longer alive.
     */
    private static final String TAG_PRISON_LOCKED = "TcPrisonLocked";

    /*
     * TC_PRISON_OWNER_LOCK_R1F18C
     * The first player who locks the car becomes its persistent owner.
     * Only that exact UUID can operate this car's lock with the wrench.
     * No operator / creative wrench bypass is intentionally provided.
     */
    private static final String TAG_PRISON_OWNER = "TcPrisonOwner";
    private static final String TAG_PRISON_OWNER_NAME = "TcPrisonOwnerName";

    private boolean prisonLocked;
    private UUID prisonOwner;
    private String prisonOwnerName = "";
    private boolean allowingLockedDismount;

    public MobTransportCar(EntityType<? extends MobTransportCar> type, Level level) { super(type, level); }
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
        for (int pass = 0; pass < 3; pass++) {
            super.alignCouplerSpacingToLeader(leader);
        }
    }

    /*
     * TC_BLUE_CAR_LOAD_RELIABILITY_R1F20G
     *
     * Blue car accepts any living non-animal Mob (hostiles, villagers,
     * traders, etc.) plus real Player prisoners.
     */
    private static boolean isTransportMob(Entity entity) {
        return entity != null
                && entity.isAlive()
                && entity instanceof LivingEntity
                && !(entity instanceof Animal)
                && (entity instanceof Mob || entity instanceof Player);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isTransportMob(passenger)
                && this.getPassengers().size() < MAX_MOBS;
    }


    public boolean isPrisonLocked() {
        return this.prisonLocked;
    }

    private void setPrisonLocked(boolean locked) {
        this.prisonLocked = locked;
    }

    private boolean isPrisonOwner(Player player) {
        return this.prisonOwner != null && this.prisonOwner.equals(player.getUUID());
    }

    private void claimPrisonOwner(Player player) {
        if (this.prisonOwner == null) {
            this.prisonOwner = player.getUUID();
            this.prisonOwnerName = player.getGameProfile().getName();
        }
    }

    private String prisonOwnerDisplayName() {
        return this.prisonOwnerName == null || this.prisonOwnerName.isBlank()
                ? "another player"
                : this.prisonOwnerName;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_PRISON_LOCKED, this.prisonLocked);
        if (this.prisonOwner != null) {
            tag.putUUID(TAG_PRISON_OWNER, this.prisonOwner);
            tag.putString(TAG_PRISON_OWNER_NAME, this.prisonOwnerName == null ? "" : this.prisonOwnerName);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.prisonLocked = tag.getBoolean(TAG_PRISON_LOCKED);
        this.prisonOwner = tag.hasUUID(TAG_PRISON_OWNER) ? tag.getUUID(TAG_PRISON_OWNER) : null;
        this.prisonOwnerName = tag.contains(TAG_PRISON_OWNER_NAME)
                ? tag.getString(TAG_PRISON_OWNER_NAME)
                : "";
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (this.prisonLocked
                && !this.allowingLockedDismount
                && !this.isRemoved()
                && passenger.isAlive()) {
            return;
        }
        super.removePassenger(passenger);
    }

    private void unlockAndEjectAll() {
        this.allowingLockedDismount = true;
        try {
            for (Entity passenger : new ArrayList<>(this.getPassengers())) {
                passenger.stopRiding();
            }
        } finally {
            this.allowingLockedDismount = false;
        }
    }
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Locked prisoner cannot use outside controls from inside.
        if (this.prisonLocked && this.hasPassenger(player)) {
            if (!this.level().isClientSide) {
                player.displayClientMessage(
                        Component.literal("Mob / Prisoner Car is locked from the outside."),
                        true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Owner-only Wrench lock.
        if (hand == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && player.getItemInHand(hand).is(TCItems.WRENCH.get())) {

            if (!this.level().isClientSide) {
                this.claimPrisonOwner(player);

                if (!this.isPrisonOwner(player)) {
                    player.displayClientMessage(
                            Component.literal("Mob / Prisoner Car is owned by "
                                    + this.prisonOwnerDisplayName()
                                    + ". Your wrench cannot operate this lock."),
                            true);
                    return InteractionResult.CONSUME;
                }

                boolean next = !this.prisonLocked;
                this.setPrisonLocked(next);

                this.level().playSound(
                        null,
                        this.blockPosition(),
                        next ? SoundEvents.IRON_DOOR_CLOSE
                                : SoundEvents.IRON_DOOR_OPEN,
                        SoundSource.BLOCKS,
                        0.75F,
                        next ? 0.80F : 1.05F);

                player.displayClientMessage(
                        Component.literal(next
                                ? "Mob / Prisoner Car: LOCKED (Owner: "
                                        + this.prisonOwnerDisplayName() + ")"
                                : "Mob / Prisoner Car: UNLOCKED (Owner: "
                                        + this.prisonOwnerDisplayName() + ")"),
                        true);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Shift + Transport Rope = quick Load. If nothing is bound, quick-unload
        // one passenger back onto the rope.
        if (hand == InteractionHand.MAIN_HAND
                && player.isShiftKeyDown()
                && player.getItemInHand(hand).is(TCItems.TRANSPORT_ROPE.get())) {

            if (!this.level().isClientSide) {
                if (this.prisonLocked) {
                    player.displayClientMessage(
                            Component.literal("Unlock the Mob / Prisoner Car before loading or unloading."),
                            true);
                    return InteractionResult.CONSUME;
                }

                net.minecraft.world.item.ItemStack rope =
                        player.getItemInHand(hand);

                if (!traincraft.item.TransportRopeItem.boundTargets(rope).isEmpty()) {
                    tcManifestLoadOne(player);
                    return InteractionResult.CONSUME;
                }

                if (!this.getPassengers().isEmpty()) {
                    tcManifestUnloadSlot(
                            player,
                            this.getPassengers().size() - 1);
                    return InteractionResult.CONSUME;
                }

                player.displayClientMessage(
                        Component.literal("Bind a mob, villager, or player with the Transport Rope first."),
                        true);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        /*
         * Ordinary interaction opens the three-slot manifest through the
         * r1f20d createMenu() override. Holding the Transport Rope is no
         * longer required just to view/use the GUI.
         */
        if (hand == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown()
                && player.getItemInHand(hand).is(TCItems.TRANSPORT_ROPE.get())) {
            return super.interact(player, hand);
        }

        if (this.prisonLocked) {
            if (hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).isEmpty()) {
                if (!this.level().isClientSide) {
                    player.displayClientMessage(
                            Component.literal("Mob / Prisoner Car is locked by "
                                    + this.prisonOwnerDisplayName() + "."),
                            true);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return super.interact(player, hand);
        }

        // Empty-hand fallback: nearby non-animal mob.
        if (hand == InteractionHand.MAIN_HAND
                && player.getItemInHand(hand).isEmpty()) {

            if (player.isShiftKeyDown()) {
                if (!this.level().isClientSide) {
                    if (this.getPassengers().size() < MAX_MOBS) {
                        Mob nearby = this.level().getEntitiesOfClass(
                                        Mob.class,
                                        this.getBoundingBox().inflate(3.0D),
                                        entity -> !(entity instanceof Animal)
                                                && entity.isAlive()
                                                && !entity.isPassenger())
                                .stream()
                                .min(Comparator.comparingDouble(
                                        entity -> this.distanceToSqr(entity)))
                                .orElse(null);

                        if (nearby != null && nearby.startRiding(this, true)) {
                            if (nearby.isLeashed()) {
                                nearby.dropLeash(true, false);
                            }

                            int seat =
                                    this.getPassengers().indexOf(nearby) + 1;
                            tcEnsureCalibrationSeat(seat);

                            player.displayClientMessage(
                                    Component.literal("Mob / Prisoner Car: loaded "
                                            + nearby.getName().getString()
                                            + " (" + this.getPassengers().size() + "/3)"),
                                    true);
                            return InteractionResult.CONSUME;
                        }
                    }

                    this.unlockAndEjectAll();
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Players can still voluntarily enter an unlocked prisoner car.
            if (this.getPassengers().size() < MAX_MOBS
                    && !player.isPassenger()) {
                if (!this.level().isClientSide
                        && player.startRiding(this, true)) {
                    int seat =
                            this.getPassengers().indexOf(player) + 1;
                    tcEnsureCalibrationSeat(seat);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        return super.interact(player, hand);
    }

    /*
     * TC_LOCKED_PRISONER_SURVIVAL_PROTECTION_R1F20A
     * A survival-mode player imprisoned inside this locked car cannot punch
     * the car out from underneath themselves. Creative/admin removal remains
     * available as an emergency/server-management escape path.
     */
    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        Entity attacker = damageSource.getEntity();

        if (this.prisonLocked
                && attacker instanceof Player attackingPlayer
                && this.hasPassenger(attackingPlayer)
                && !attackingPlayer.getAbilities().instabuild) {
            if (!this.level().isClientSide) {
                attackingPlayer.displayClientMessage(
                        Component.literal("You cannot break the locked Mob / Prisoner Car from inside."),
                        true);
            }
            return false;
        }

        return super.hurt(damageSource, amount);
    }

    /*
     * TC_TRANSPORT_MANIFEST_PRISONER_R1F20C
     * Unloading from the GUI immediately returns that passenger to the
     * Transport Rope. Unload All can bind all three targets to one rope.
     */
    private net.minecraft.world.item.ItemStack tcFindTransportRope(
            Player player) {

        if (player.getMainHandItem().is(TCItems.TRANSPORT_ROPE.get())) {
            return player.getMainHandItem();
        }

        if (player.getOffhandItem().is(TCItems.TRANSPORT_ROPE.get())) {
            return player.getOffhandItem();
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack =
                    player.getInventory().getItem(i);

            if (stack.is(TCItems.TRANSPORT_ROPE.get())) {
                return stack;
            }
        }

        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    public boolean tcManifestLocked() {
        return this.prisonLocked;
    }

    public boolean tcManifestUnloadSlot(Player player, int slot) {
        if (this.prisonLocked) {
            player.displayClientMessage(
                    Component.literal("Unlock the Mob / Prisoner Car first."),
                    true);
            return false;
        }

        if (slot < 0 || slot >= this.getPassengers().size()) {
            return false;
        }

        net.minecraft.world.item.ItemStack rope =
                tcFindTransportRope(player);

        if (rope.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("You need a Transport Rope to unload prisoners."),
                    true);
            return false;
        }

        if (TransportRopeItem.freeBindings(rope) < 1) {
            player.displayClientMessage(
                    Component.literal("Transport Rope is full (3/3)."),
                    true);
            return false;
        }

        Entity passenger = this.getPassengers().get(slot);
        passenger.stopRiding();

        if (!TransportRopeItem.bind(rope, player, passenger)) {
            // Put the passenger back if we could not safely return the rope.
            passenger.startRiding(this, true);
            return false;
        }

        player.displayClientMessage(
                Component.literal("Mob / Prisoner Car: unloaded "
                        + passenger.getName().getString()
                        + " (" + this.getPassengers().size() + "/3)"),
                true);
        return true;
    }

    public int tcManifestUnloadAll(Player player) {
        if (this.prisonLocked) {
            player.displayClientMessage(
                    Component.literal("Unlock the Mob / Prisoner Car first."),
                    true);
            return 0;
        }

        net.minecraft.world.item.ItemStack rope =
                tcFindTransportRope(player);

        if (rope.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("You need a Transport Rope to unload prisoners."),
                    true);
            return 0;
        }

        int count = this.getPassengers().size();

        if (TransportRopeItem.freeBindings(rope) < count) {
            player.displayClientMessage(
                    Component.literal("Transport Rope needs "
                            + count
                            + " free binding slot"
                            + (count == 1 ? "." : "s.")),
                    true);
            return 0;
        }

        int unloaded = 0;

        for (int slot = this.getPassengers().size() - 1;
                slot >= 0;
                slot--) {
            if (tcManifestUnloadSlot(player, slot)) {
                unloaded++;
            }
        }

        return unloaded;
    }

    /*
     * TC_TRANSPORT_MANIFEST_ROUTING_R1F20D
     * Override FreightCart's menu provider so inherited interaction can never
     * open the 4-row Freight Cart inventory for this entity.
     */
    @Override
    public Component getDisplayName() {
        return Component.literal("Mob / Prisoner Car");
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
     * These are the same three calibration slots used by player riders.
     * Loaded mobs and players now visibly follow /tc visual seat commands.
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
        String id = "mob_transport_car";

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
     * TC_GUI_LOAD_BLUE_CAR_R1F20G
     *
     * Resolve Transport Rope UUIDs directly from ServerLevel. This includes
     * hostile mobs, villagers and bound players, and avoids depending on a
     * generic nearby entity-class search to rediscover the target.
     */
        private LivingEntity tcFindBoundTransportTarget(Player player) {
        net.minecraft.world.item.ItemStack rope = tcFindTransportRope(player);

        if (rope.isEmpty()) {
            return null;
        }

        LivingEntity closest = null;
        double closestDistance = 24.0D * 24.0D;

        if (this.level() instanceof ServerLevel serverLevel) {
            List<UUID> boundIds = traincraft.item.TransportRopeItem.boundTargets(rope);
            for (UUID id : boundIds) {
                Entity entity = serverLevel.getEntity(id);
                if (!(entity instanceof LivingEntity living)
                        || !isTransportMob(living)
                        || living.isPassenger()) {
                    continue;
                }

                double distance = this.distanceToSqr(living);
                if (distance <= closestDistance) {
                    closestDistance = distance;
                    closest = living;
                }
            }
        }

        if (closest != null) {
            return closest;
        }

        return this.level().getEntitiesOfClass(
                        Mob.class,
                        this.getBoundingBox().inflate(24.0D),
                        entity -> isTransportMob(entity)
                                && !entity.isPassenger()
                                && entity.isLeashed()
                                && entity.getLeashHolder() == player)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /*
     * TC_ROBUST_TRANSPORT_LOAD_R1F20H
     *
     * Supports both persistent Transport Rope UUID binding and the physical
     * leash already visible between player and mob.
     */
        /*
     * TC_TRANSPORT_LOAD_FINALIZE_R1F20I
     *
     * GUI Load and rope-load both land here.
     */
    public boolean tcManifestLoadOne(Player player) {
        if (this.prisonLocked) {
            player.displayClientMessage(
                    Component.literal("Unlock the Mob / Prisoner Car first."),
                    true);
            return false;
        }

        if (this.getPassengers().size() >= MAX_MOBS) {
            player.displayClientMessage(
                    Component.literal("Mob / Prisoner Car full (3/3)."),
                    true);
            return false;
        }

        net.minecraft.world.item.ItemStack rope = tcFindTransportRope(player);
        if (rope.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("Hold or carry a Transport Rope first."),
                    true);
            return false;
        }

        LivingEntity target = tcFindBoundTransportTarget(player);
        if (target == null) {
            int stored = traincraft.item.TransportRopeItem.bindingCount(rope);
            player.displayClientMessage(
                    Component.literal("No bound/leashed mob, villager, or player found within 24 blocks. Rope bindings: "
                            + stored + "/3."),
                    true);
            return false;
        }

        traincraft.item.TransportRopeItem.releaseMobLeash(target, player);

        if (!target.startRiding(this, true)) {
            traincraft.item.TransportRopeItem.bind(rope, player, target);
            player.displayClientMessage(
                    Component.literal(target.getName().getString()
                            + " could not board; Transport Rope restored."),
                    true);
            return false;
        }

        traincraft.item.TransportRopeItem.releaseMobLeash(target, player);
        traincraft.item.TransportRopeItem.removeBinding(rope, target.getUUID());

        int seat = this.getPassengers().indexOf(target) + 1;
        tcEnsureCalibrationSeat(seat);

        player.displayClientMessage(
                Component.literal("Mob / Prisoner Car: loaded "
                        + target.getName().getString()
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

        String id = "mob_transport_car";

        double longitudinal =
                traincraft.config.RollingStockCalibrationConfig.seatLong(
                        id, seat, tcDefaultSeatLong(seat));
        double footY =
                traincraft.config.RollingStockCalibrationConfig.seatY(
                        id, seat, tcDefaultSeatY(seat));
        double side =
                traincraft.config.RollingStockCalibrationConfig.seatSide(
                        id, seat, tcDefaultSeatSide(seat));

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