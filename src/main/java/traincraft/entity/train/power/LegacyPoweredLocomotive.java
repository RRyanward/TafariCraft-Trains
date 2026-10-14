/*
 * TafariCraft - Trains final cleanup: classic diesel/electric powered roster.
 * Uses the proven SmallSteam movement/coupling/track path as a shared vehicle
 * chassis while replacing steam generation with diesel/electric power.
 */
package traincraft.entity.train.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import traincraft.block.track.EnergyTrackBlock;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.menu.PoweredLocomotiveMenu;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCItems;
import traincraft.registry.TCSounds;

import java.util.Locale;

public class LegacyPoweredLocomotive extends SmallSteamLocomotive {
    // r2e4b: short electric line-power continuity across multipart track handoffs.
    private static final int ELECTRIC_LINE_POWER_GRACE_TICKS = 40;
    private int electricLinePowerGraceTicks;

    public enum DriveType { DIESEL, ELECTRIC, BUILDER }

    private static final EntityDataAccessor<Integer> DATA_POWER =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BUILDER_COAL_BURN =
            SynchedEntityData.defineId(
                    LegacyPoweredLocomotive.class,
                    EntityDataSerializers.INT);
    // TC_BATCH8_R3_TRACK_BLUEPRINT
    private static final EntityDataAccessor<Integer> DATA_BLUEPRINT_PROGRESS =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BLUEPRINT_TOTAL =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_BLUEPRINT_NEED =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.STRING);

        private static final EntityDataAccessor<Float> DATA_CALIBRATION_LIFT =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_CALIBRATION_SELECTED =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.BOOLEAN);
    // r1f9 temporary calibration transport.
    // Uses Minecraft's built-in entity-event packet so the exact tracked
    // client entity receives height changes even when custom SynchedEntityData
    // failed to arrive for Cart Hauler.
    private static final byte TC_CAL_HEIGHT_EVENT_UP    = 110;
    private static final byte TC_CAL_HEIGHT_EVENT_DOWN  = 111;
    private static final byte TC_CAL_HEIGHT_EVENT_RESET = 112;

    private double tcClientCalibrationHeightOverride = 0.0D;
    private boolean tcClientCalibrationHeightOverrideValid = false;
    // r1f8 temporary dev-calibration bridge.
    // Shared by integrated-server + render threads in runClient only.
    // Removed when final per-variant values are baked.
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, Double>
            TC_CALIBRATION_HEIGHT_BRIDGE =
                    new java.util.concurrent.ConcurrentHashMap<>();
    private static final EntityDataAccessor<Boolean> DATA_CALIBRATION_SEAT_PIN =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_CALIBRATION_SEAT_LONG =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CALIBRATION_SEAT_Y =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CALIBRATION_SEAT_SIDE =
            SynchedEntityData.defineId(LegacyPoweredLocomotive.class, EntityDataSerializers.FLOAT);
    // TC_DEV36_R1K29E_BUILDER_EXCAVATION
    // Classic Traincraft's Builder exposed a synchronized "working" state that
    // drove its seven-piece front rotor. Keep authoritative run state server-side,
    // but synchronize only the visual drill-active bit to clients.
    private static final EntityDataAccessor<Boolean> DATA_BUILDER_DRILL_ACTIVE =
            SynchedEntityData.defineId(
                    LegacyPoweredLocomotive.class,
                    EntityDataSerializers.BOOLEAN);

    private boolean tcDev36ExcavationInventoryFullLatched = false;
    // TC_DEV36_R1K29R_MANUAL_TUNNEL
    // Separate from Blueprint mode: this bit is synchronized so the GUI switch
    // and left status panel always show the server-authoritative mode.
    private static final EntityDataAccessor<Boolean> DATA_BUILDER_MANUAL_TUNNEL =
            SynchedEntityData.defineId(
                    LegacyPoweredLocomotive.class,
                    EntityDataSerializers.BOOLEAN);

    private boolean tcDev36ManualTunnelWarningLatched = false;
private final ItemStackHandler powerInventory = new ItemStackHandler(78);
    // TC_DEV34_R1C1A_BUILDER_STRUCTURE_PATTERNS
    // One-item template/filter slots. Actual construction material continues
    // to live in rack slots 20..51.
    private final ItemStackHandler builderStructurePatterns =
            new ItemStackHandler(
                    traincraft.builder.BuilderInfrastructureSystem.PATTERN_COUNT) {
                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }

                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return stack != null
                            && !stack.isEmpty()
                            && stack.getItem()
                                    instanceof net.minecraft.world.item.BlockItem;
                }
            };
    private int powerAmount;
    private int poweredWhistleTick = -100;
    @Nullable
    private BlockPos builderBlueprintAnchor;
    private int builderBlueprintRotationQuarter;
    private int builderBlueprintProgress;
    private String builderBlueprintId = "";
    private String builderBlueprintNeed = "";
    // TC_BATCH8_R4A5G24_BUILDER_LIVE_CONNECTOR_PLACEMENT
    // The stored Blueprint X/Y/Z remains design intent. Once a piece is really
    // placed, its resolved Blueprint anchor/yaw becomes the source geometry for
    // the next piece.
    @Nullable
    private BlockPos builderBlueprintLastPlacedAnchor;
    private float builderBlueprintLastPlacedYaw;

    private int builderCoalBurnTicks;

    public LegacyPoweredLocomotive(EntityType<? extends LegacyPoweredLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_POWER, 0);
        this.entityData.define(DATA_BUILDER_DRILL_ACTIVE, false);
        this.entityData.define(DATA_BUILDER_MANUAL_TUNNEL, false);
        this.entityData.define(DATA_BUILDER_COAL_BURN, 0);
        this.entityData.define(DATA_BLUEPRINT_PROGRESS, 0);
        this.entityData.define(DATA_BLUEPRINT_TOTAL, 0);
        this.entityData.define(DATA_BLUEPRINT_NEED, "");
        this.entityData.define(DATA_CALIBRATION_LIFT, 0.0F);
        this.entityData.define(DATA_CALIBRATION_SELECTED, false);
        this.entityData.define(DATA_CALIBRATION_SEAT_PIN, false);
        this.entityData.define(DATA_CALIBRATION_SEAT_LONG, 0.0F);
        this.entityData.define(DATA_CALIBRATION_SEAT_Y, 0.0F);
        this.entityData.define(DATA_CALIBRATION_SEAT_SIDE, 0.0F);
    }

    public String variantId() {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(this.getType());
        return key == null ? "unknown" : key.getPath();
    }

    public DriveType driveType() {
        String id = variantId();
        if ("tracks_builder".equals(id)) return DriveType.BUILDER;
        return id.startsWith("locomotive_electric_") ? DriveType.ELECTRIC : DriveType.DIESEL;
    }

    public boolean isDiesel() { return driveType() == DriveType.DIESEL; }
    public boolean isElectric() { return driveType() == DriveType.ELECTRIC; }
    public boolean isBuilder() { return driveType() == DriveType.BUILDER; }

    /** Client-safe visual state for the classic Builder rotor. */
    public boolean isBuilderDrillActive() {
        return isBuilder() && this.entityData.get(DATA_BUILDER_DRILL_ACTIVE);
    }

    public boolean isBuilderManualTunnelMode() {
        return isBuilder()
                && this.entityData.get(
                        DATA_BUILDER_MANUAL_TUNNEL);
    }

    /**
     * GUI/server control for the simple straight-ahead tunnel-borer mode.
     * Mode changes always stop the machine first; Shift-right-click remains
     * the deliberate START/STOP control after the mode is selected.
     */
    public void toggleBuilderManualTunnelMode() {
        if (!isBuilder()
                || level().isClientSide) {
            return;
        }

        setBuilderRunning(false);

        boolean enabled =
                !this.entityData.get(
                        DATA_BUILDER_MANUAL_TUNNEL);

        this.entityData.set(
                DATA_BUILDER_MANUAL_TUNNEL,
                enabled);

        tcDev36ManualTunnelWarningLatched =
                false;

        tcDev36SetBuilderWorkStatus(
                enabled
                        ? "MANUAL TUNNEL READY"
                        : "");

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29R][MANUAL] event=MODE entity={} tick={} enabled={} blueprintProgress={}",
                getId(),
                tickCount,
                enabled,
                builderBlueprintProgress);
    }

    public IItemHandler getPowerInventory() { return powerInventory; }
    public IItemHandler getBuilderStructurePatterns() {
        return builderStructurePatterns;
    }

    public String getBuilderInfrastructureMode() {
        return traincraft.builder.BuilderInfrastructureSystem.modeName(this);
    }
    public int getPowerAmount() {
        return level().isClientSide ? this.entityData.get(DATA_POWER) : powerAmount;
    }
    public int getBuilderCoalBurnTicks() {
        if (!isBuilder()) return 0;
        return level().isClientSide
                ? this.entityData.get(DATA_BUILDER_COAL_BURN)
                : builderCoalBurnTicks;
    }

    public boolean isBuilderCoalBurning() {
        return isBuilder()
                && getBuilderCoalBurnTicks() > 0;
    }
    public int getPowerCapacity() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 5000;
            case "locomotive_diesel_cd742" -> 10000;
            case "locomotive_diesel_chme3" -> 5000;
            case "locomotive_diesel_gp7" -> 20000;
            case "locomotive_diesel_sd40", "locomotive_diesel_sd70" -> 20000;
            case "locomotive_diesel_shunter", "locomotive_diesel_v60" -> 8000;
            case "tracks_builder" -> 8000;
            default -> 12000;
        };
    }

    @Override
    protected boolean hasTractionPower() {
        if (isElectric()) {
            return isOnPoweredEnergyTrack()
                    || hasPortableBackupRedstone()
                    || electricLinePowerGraceTicks > 0;
        }
        if (isBuilder()) {
            // TC_DEV36_R1K12_COAL_ONLY_BUILDER
            return getBuilderCoalBurnTicks() > 0;
        }

        return getPowerAmount() > 0;
    }

    public boolean isOnPoweredEnergyTrack() {
        if (!isElectric()) return false;

        BlockPos center = blockPosition();

        // r2e6:
        // Use the existing material-aware Energy network rather than checking
        // any obsolete single-block Energy rail.
        //
        // Each restored LegacyBatch segment can resolve its assembly root, where
        // TrackMaterialBlockEntity stores TrackMaterial.ENERGY. This makes line
        // power follow the actual Energy assembly across 45-degree curves,
        // switches, slopes, crossings, diagonals, long curves and related
        // multipart layouts without modifying train movement/path geometry.

        // Exact vehicle cell + vertical handoff cells first.
        for (int dy = -2; dy <= 1; dy++) {
            BlockPos pos = center.offset(0, dy, 0);
            if (traincraft.block.track.LightweightEnergyTrackNetwork.isPowered(level(), pos)) {
                return true;
            }
        }

        // Small local footprint covers entity-center / rail-cell rounding at
        // multipart handoffs. The helper itself decides whether the cell belongs
        // to an Energy assembly and whether that assembly/network is powered.
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (traincraft.block.track.LightweightEnergyTrackNetwork.isPowered(level(), pos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean hasPortableBackupRedstone() {
        if (!isElectric()) return false;
        ItemStack cell = powerInventory.getStackInSlot(0);
        return cell.is(Items.REDSTONE) || cell.is(Items.REDSTONE_BLOCK);
    }
    @Override
    protected void tickPowerSystem() {
        if (!level().isClientSide && isBuilder()) tickBuilderAutonomousDrive();
        syncPoweredCalibrationState();
        if (level().isClientSide) return;

        // TC_R2E4_LINE_POWER_CONTINUITY
        // Power-state continuity only; movement/pathing stays inherited.
        if (isElectric()) {
            if (isOnPoweredEnergyTrack()) {
                electricLinePowerGraceTicks = ELECTRIC_LINE_POWER_GRACE_TICKS;
            } else if (electricLinePowerGraceTicks > 0) {
                electricLinePowerGraceTicks--;
            }
        } else {
            electricLinePowerGraceTicks = 0;
        }

        if (isElectric()) {
            // r1d converted redstone into an invisible stored charge. Do not do that.
            // Electric backup exists only while actual Redstone/Redstone Block remains
            // physically in service slot 0. Clear any legacy transient charge value.
            powerAmount = 0;
                    if (isBuilder()
                && builderCoalBurnTicks > 0) {
            builderCoalBurnTicks--;
        }
double speed = getDeltaMovement().horizontalDistance();
            if (!isOnPoweredEnergyTrack()) {
                consumePortableBackupRedstone(speed);
            }
        } else {
            refuelDiesel();
        }

        double speed = getDeltaMovement().horizontalDistance();
        if (!isElectric()
                && powerAmount > 0
                && speed > 0.01D
                && getFirstPassenger() != null
                && tickCount % 4 == 0) {
            powerAmount = Math.max(0, powerAmount - 1);
        }

        if (isBuilder() && hasTractionPower() && isBuilderRunning()
                && tickCount % 4 == 0) {
            // TC_BATCH8_R3ZB_BLUEPRINT_ZERO_SPEED_DEADLOCK_FIX
            ItemStack tcBuildPlan = powerInventory.getStackInSlot(52);
            boolean tcBlueprintMode =
                    tcBuildPlan.getItem() instanceof traincraft.item.TrackBlueprintItem
                            && traincraft.item.TrackBlueprintItem.isLocked(tcBuildPlan)
                            && traincraft.item.TrackBlueprintItem.segmentCount(tcBuildPlan) > 0;

            // A locked Blueprint must be allowed to place its next planned rail
            // while the Builder is temporarily stopped. That next rail can put
            // the autonomous machine back onto a valid route and let it move on.
            //
            // Classic free-lay mode retains the old speed requirement.
            if (tcBlueprintMode || isBuilderManualTunnelMode() || speed > 0.015D) {
                tryLayBuilderStep();
            }
        }

                if (isBuilder() && tickCount % 20 == 0) {
            ItemStack debugBlueprint = powerInventory.getStackInSlot(52);
            double debugBuilderSpeed =
                    getDeltaMovement().horizontalDistance();

            org.slf4j.LoggerFactory.getLogger("TafariCraftBuilderBlueprint").info(
                    "[TC-BUILDER-TICK] id={} tick={} running={} power={}/{} speed={} pos=({},{},{}) yaw={} motion=({},{},{}) blueprint={} locked={} segments={} progress={}/{} need={}",
                    getId(),
                    tickCount,
                    isBuilderRunning(),
                    powerAmount,
                    getPowerCapacity(),
                    String.format(java.util.Locale.ROOT, "%.5f", debugBuilderSpeed),
                    String.format(java.util.Locale.ROOT, "%.3f", getX()),
                    String.format(java.util.Locale.ROOT, "%.3f", getY()),
                    String.format(java.util.Locale.ROOT, "%.3f", getZ()),
                    String.format(java.util.Locale.ROOT, "%.2f", getYRot()),
                    String.format(java.util.Locale.ROOT, "%.5f", getDeltaMovement().x),
                    String.format(java.util.Locale.ROOT, "%.5f", getDeltaMovement().y),
                    String.format(java.util.Locale.ROOT, "%.5f", getDeltaMovement().z),
                    debugBlueprint.isEmpty()
                            ? "EMPTY"
                            : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(debugBlueprint.getItem())),
                    debugBlueprint.getItem() instanceof traincraft.item.TrackBlueprintItem
                            && traincraft.item.TrackBlueprintItem.isLocked(debugBlueprint),
                    debugBlueprint.getItem() instanceof traincraft.item.TrackBlueprintItem
                            ? traincraft.item.TrackBlueprintItem.segmentCount(debugBlueprint)
                            : 0,
                    builderBlueprintProgress,
                    this.entityData.get(DATA_BLUEPRINT_TOTAL),
                    builderBlueprintNeed);
        }
        // TC_DEV36_R1G_STRUCTURE_DEEP_TRACE
        tcDev36R1gTickTrace();
tcDev36SyncBuilderDrillState();
        entityData.set(DATA_POWER, powerAmount);
        entityData.set(DATA_BUILDER_COAL_BURN, builderCoalBurnTicks);
        debugPoweredRuntime();
    }


    private void consumePortableBackupRedstone(double speed) {
        if (!isElectric() || isOnPoweredEnergyTrack()) return;
        if (speed <= 0.01D || getFirstPassenger() == null) return;

        ItemStack cell = powerInventory.getStackInSlot(0);
        int period;
        if (cell.is(Items.REDSTONE_BLOCK)) {
            period = 720; // one block lasts nine times one dust unit
        } else if (cell.is(Items.REDSTONE)) {
            period = 80;
        } else {
            return;
        }

        if (tickCount % period != 0) return;
        ItemStack left = cell.copy();
        left.shrink(1);
        powerInventory.setStackInSlot(0, left);
    }

    private void debugPoweredRuntime() {
        if (getFirstPassenger() == null || tickCount % 100 != 0) return;
        var rider = getFirstPassenger();
        ItemStack backup = powerInventory.getStackInSlot(0);
        String backupName = backup.is(Items.REDSTONE_BLOCK) ? "redstone_block"
                : backup.is(Items.REDSTONE) ? "redstone" : "empty";
        System.out.printf(Locale.ROOT,
                "[TC-POWERED-RIDER] variant=%s entity=(%.4f,%.4f,%.4f) rider=(%.4f,%.4f,%.4f) rel=(%.4f,%.4f,%.4f) yaw=%.2f speed=%.5f electric=%s line=%s backup=%s x%d stored=%d%n",
                variantId(), getX(), getY(), getZ(),
                rider.getX(), rider.getY(), rider.getZ(),
                rider.getX()-getX(), rider.getY()-getY(), rider.getZ()-getZ(),
                getTrainFacingYaw(), getDeltaMovement().horizontalDistance(),
                isElectric(), isOnPoweredEnergyTrack(), backupName, backup.getCount(), powerAmount);
    }
    // r1e11 diagnostic only: preserve vanilla/current rider placement exactly,
    // then log the resulting position. No seat correction is applied here.
    // TC_R2E5_STABLE_POWERED_SEAT
    // Powered locomotives share the proven SmallSteam chassis/path, so rider
    // placement uses that same authoritative frame. Do not independently rotate
    // a powered-seat pin around render/calibration yaw here; that can put the
    // player beside the locomotive after compound/long turns.
    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger,
                                 net.minecraft.world.entity.Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
    }

    private void refuelDiesel() {
        ItemStack fuel =
                powerInventory.getStackInSlot(0);

        if (isBuilder()) {
            // TC_DEV36_R1K12_COAL_ONLY_BUILDER
            // Builder has no diesel reserve anymore.
            powerAmount = 0;

            if (fuel.is(
                            net.minecraft.world.item.Items.COAL)
                    && builderCoalBurnTicks <= 0) {
                ItemStack left =
                        fuel.copy();
                left.shrink(
                        1);
                powerInventory.setStackInSlot(
                        0,
                        left);

                // Vanilla-furnace-style coal burn: 80 seconds at 20 TPS.
                builderCoalBurnTicks =
                        1600;
            }

            return;
        }

        if (powerAmount
                > getPowerCapacity() - 1000) {
            return;
        }

        int value =
                0;

        if (fuel.is(
                TCItems.DIESEL_FUEL.get())) {
            value =
                    4000;
        }
        else if (fuel.is(
                        net.minecraft.world.item.Items.COAL)
                || fuel.is(
                        net.minecraft.world.item.Items.CHARCOAL)) {
            value =
                    800;
        }

        if (value <= 0) {
            return;
        }

        ItemStack left =
                fuel.copy();
        left.shrink(
                1);
        powerInventory.setStackInSlot(
                0,
                left);

        powerAmount =
                Math.min(
                        getPowerCapacity(),
                        powerAmount + value);
    }



    // TC_BATCH8_R3_TRACK_BLUEPRINT

    public int getBuilderBlueprintProgress() { return this.entityData.get(DATA_BLUEPRINT_PROGRESS); }
    public int getBuilderBlueprintTotal() { return this.entityData.get(DATA_BLUEPRINT_TOTAL); }
    public String getBuilderBlueprintNeed() { return this.entityData.get(DATA_BLUEPRINT_NEED); }

    public void resetBuilderBlueprintProgress() {
        builderBlueprintAnchor = null;
        builderBlueprintRotationQuarter = 0;
        builderBlueprintProgress = 0;
        builderBlueprintId = "";
        builderBlueprintNeed = "";
        builderBlueprintLastPlacedAnchor = null;
        builderBlueprintLastPlacedYaw = 0.0F;
        this.entityData.set(DATA_BLUEPRINT_PROGRESS, 0);
        this.entityData.set(DATA_BLUEPRINT_TOTAL, 0);
        this.entityData.set(DATA_BLUEPRINT_NEED, "");
    }

    private void syncBuilderBlueprint(int total, String need) {
        String previousBuilderBlueprintNeed = builderBlueprintNeed;
        builderBlueprintNeed = need == null ? "" : need;
        this.entityData.set(DATA_BLUEPRINT_PROGRESS, builderBlueprintProgress);
        this.entityData.set(DATA_BLUEPRINT_TOTAL, Math.max(0, total));
        this.entityData.set(DATA_BLUEPRINT_NEED, builderBlueprintNeed);
        // TC_BATCH8_R4A5G25H6_BUILDER_COMPLETE_POPUP
        // Fire exactly once on the transition into COMPLETE. This also stops
        // autonomous Builder mode so a finished plan cannot keep producing
        // post-completion off-rail/drive telemetry.
        if (!"COMPLETE".equals(previousBuilderBlueprintNeed)
                && "COMPLETE".equals(builderBlueprintNeed)
                && level() instanceof net.minecraft.server.level.ServerLevel server) {
            finishBuilderBlueprintCompletion(server, total);
        }

        // TC_BATCH8_R3W_BUILDER_ROUTE_DEBUG
        if (!level().isClientSide && isBuilder()) {
            String lastNeed =
                    getPersistentData().getString("TCBuilderDebugLastNeed");
            int lastProgress =
                    getPersistentData().getInt("TCBuilderDebugLastProgress");

            if (lastProgress != builderBlueprintProgress
                    || !lastNeed.equals(builderBlueprintNeed)) {
                org.slf4j.LoggerFactory.getLogger("TafariCraftBuilderBlueprint").info(
                        "[TC-BUILDER-BP-STATUS] id={} tick={} running={} power={} progress={}/{} need={} pos=({},{},{}) yaw={} trainYaw={} anchor={} rotQ={}",
                        getId(),
                        tickCount,
                        isBuilderRunning(),
                        powerAmount,
                        builderBlueprintProgress,
                        Math.max(0, total),
                        builderBlueprintNeed,
                        String.format(java.util.Locale.ROOT, "%.3f", getX()),
                        String.format(java.util.Locale.ROOT, "%.3f", getY()),
                        String.format(java.util.Locale.ROOT, "%.3f", getZ()),
                        String.format(java.util.Locale.ROOT, "%.2f", getYRot()),
                        String.format(java.util.Locale.ROOT, "%.2f", getTrainFacingYaw()),
                        String.valueOf(builderBlueprintAnchor),
                        builderBlueprintRotationQuarter);

                getPersistentData().putString(
                        "TCBuilderDebugLastNeed",
                        builderBlueprintNeed);
                getPersistentData().putInt(
                        "TCBuilderDebugLastProgress",
                        builderBlueprintProgress);
            }
        }
    }

    // TC_BATCH8_R4A5G25H6_BUILDER_COMPLETE_POPUP
    private void finishBuilderBlueprintCompletion(
            net.minecraft.server.level.ServerLevel server,
            int total) {

        net.minecraft.world.item.ItemStack blueprint =
                powerInventory.getStackInSlot(52);

        net.minecraft.world.phys.AABB completionGrid =
                builderBlueprintCompletionGrid(blueprint, total);

        int notified = 0;

        for (net.minecraft.server.level.ServerPlayer player : server.players()) {
            if (!completionGrid.contains(player.position())) {
                continue;
            }

            // TC_BATCH8_R4A5G25H7_RESPONSIVE_COMPLETE_POPUP
            traincraft.network.BuilderCompletionNetwork.sendToPlayer(
                    player,
                    total);

            notified++;
        }

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-COMPLETE-POPUP] id={} tick={} total={} notified={} grid={} pos=({},{},{})",
                        getId(),
                        tickCount,
                        total,
                        notified,
                        completionGrid,
                        String.format(java.util.Locale.ROOT, "%.3f", getX()),
                        String.format(java.util.Locale.ROOT, "%.3f", getY()),
                        String.format(java.util.Locale.ROOT, "%.3f", getZ()));

        setDeltaMovement(
                0.0D,
                getDeltaMovement().y,
                0.0D);

        setBuilderRunning(false);
    }

    // The popup range follows the recorded Blueprint grid rather than a fixed
    // radius around the Builder. A 16-block horizontal margin covers the
    // physical footprint of large multipart track pieces at the grid edges.
    private net.minecraft.world.phys.AABB builderBlueprintCompletionGrid(
            net.minecraft.world.item.ItemStack blueprint,
            int total) {

        if (builderBlueprintAnchor == null
                || !(blueprint.getItem()
                        instanceof traincraft.item.TrackBlueprintItem)
                || !traincraft.item.TrackBlueprintItem.isLocked(blueprint)
                || total <= 0) {

            return getBoundingBox().inflate(24.0D, 8.0D, 24.0D);
        }

        int minX = builderBlueprintAnchor.getX();
        int minY = builderBlueprintAnchor.getY();
        int minZ = builderBlueprintAnchor.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        for (int index = 0; index < total; index++) {
            net.minecraft.nbt.CompoundTag entry =
                    traincraft.item.TrackBlueprintItem.segment(
                            blueprint,
                            index);

            int dx = entry.getInt("X");
            int dy = entry.getInt("Y");
            int dz = entry.getInt("Z");

            int rx;
            int rz;

            switch (builderBlueprintRotationQuarter) {
                case 1 -> {
                    rx = -dz;
                    rz = dx;
                }
                case 2 -> {
                    rx = -dx;
                    rz = -dz;
                }
                case 3 -> {
                    rx = dz;
                    rz = -dx;
                }
                default -> {
                    rx = dx;
                    rz = dz;
                }
            }

            net.minecraft.core.BlockPos planned =
                    builderBlueprintAnchor.offset(
                            rx,
                            dy,
                            rz);

            minX = Math.min(minX, planned.getX());
            minY = Math.min(minY, planned.getY());
            minZ = Math.min(minZ, planned.getZ());
            maxX = Math.max(maxX, planned.getX());
            maxY = Math.max(maxY, planned.getY());
            maxZ = Math.max(maxZ, planned.getZ());
        }

        return new net.minecraft.world.phys.AABB(
                minX - 16.0D,
                minY - 8.0D,
                minZ - 16.0D,
                maxX + 17.0D,
                maxY + 9.0D,
                maxZ + 17.0D);
    }
    private void tryLayBuilderStep() {
        // TC_DEV36_R1K29R_MANUAL_TUNNEL_STEP_OWNER
        // Manual tunnel mode does NOT mutate Blueprint anchor/rotation/progress.
        if (isBuilderManualTunnelMode()) {
            tcDev36ManualTunnelStep();
            return;
        }
        ItemStack blueprint = powerInventory.getStackInSlot(52);
        // TC_BATCH8_R4A5A2_BUILDER_LAYING_DIAGNOSTICS
        if (tickCount % 20 == 0) {
            boolean tcDebugLockedBlueprint =
                    blueprint.getItem()
                                    instanceof traincraft.item.TrackBlueprintItem
                            && traincraft.item.TrackBlueprintItem
                                    .isLocked(blueprint);

            int tcDebugTotal =
                    tcDebugLockedBlueprint
                            ? traincraft.item.TrackBlueprintItem
                                    .segmentCount(blueprint)
                            : 0;

            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-LAY-STEP] id={} tick={} mode={} progress={}/{} need={} coalTicks={} diesel={} pos=({},{},{})",
                            getId(),
                            tickCount,
                            tcDebugLockedBlueprint ? "BLUEPRINT" : "LEGACY",
                            builderBlueprintProgress,
                            tcDebugTotal,
                            builderBlueprintNeed,
                            getBuilderCoalBurnTicks(),
                            getPowerAmount(),
                            String.format(java.util.Locale.ROOT, "%.3f", getX()),
                            String.format(java.util.Locale.ROOT, "%.3f", getY()),
                            String.format(java.util.Locale.ROOT, "%.3f", getZ()));
        }
        if (!(blueprint.getItem() instanceof traincraft.item.TrackBlueprintItem)
                || !traincraft.item.TrackBlueprintItem.isLocked(blueprint)
                || traincraft.item.TrackBlueprintItem.segmentCount(blueprint) <= 0) {
            if (!builderBlueprintId.isEmpty() || builderBlueprintAnchor != null || builderBlueprintProgress != 0) {
                resetBuilderBlueprintProgress();
            }
            tryLayBuilderRail();
            return;
        }
        // TC_DEV36_R1A_SAVED_STRUCTURE_EXECUTION
        // TC_DEV36_R1G_STRUCTURE_DEEP_TRACE
        tcDev36R1gTraceCurrent(
                "STEP_ENTER",
                blueprint,
                "before structure; trackProgress="
                        + builderBlueprintProgress);

        // Build one saved custom block per normal Blueprint work tick.
        // If that cell cannot proceed, stop before advancing the track route.
        // TC_DEV36_R1K27B_LATCH_TRACK_TRANSFORM_BEFORE_STRUCTURE
        //
        // Saved BuildBlocks must never mutate the world under the fallback
        // pre-start transform. The accepted Blueprint track auto-start owns the
        // authoritative Build Start anchor + rotation. If that transform has
        // not been committed yet, let exactly that existing track path run once
        // and defer structure work until the next normal Builder work tick.
        if (builderBlueprintAnchor == null) {
            int tcDev36R1k27TrackBefore =
                    builderBlueprintProgress;

            tryLayBuilderBlueprint(
                    blueprint);

            tcDev36R1gTraceCurrent(
                    "STEP_TRACK_LATCH",
                    blueprint,
                    "trackProgress="
                            + tcDev36R1k27TrackBefore
                            + "->"
                            + builderBlueprintProgress
                            + " anchor="
                            + String.valueOf(
                                    builderBlueprintAnchor)
                            + " rotQ="
                            + builderBlueprintRotationQuarter);

            tcDev36RefreshStructureStatus(
                    blueprint);

            tcDev36R1gTraceCurrent(
                    "STEP_STATUS_AFTER",
                    blueprint,
                    "status="
                            + builderBlueprintNeed);
            return;
        }
        if (!tcDev36BuildOneSavedStructureBlock(
                blueprint)) {
            tcDev36R1gTraceCurrent(
                    "STEP_HOLD",
                    blueprint,
                    "structure pass returned false; track not advanced");
            return;
        }

        int tcDev36R1gTrackBefore =
                builderBlueprintProgress;

        tryLayBuilderBlueprint(
                blueprint);

        tcDev36R1gTraceCurrent(
                "STEP_TRACK_AFTER",
                blueprint,
                "trackProgress="
                        + tcDev36R1gTrackBefore
                        + "->"
                        + builderBlueprintProgress);

        // Once track replay is complete, keep the existing Builder running
        // until the saved structure plan also reaches COMPLETE.
        tcDev36RefreshStructureStatus(
                blueprint);

        tcDev36R1gTraceCurrent(
                "STEP_STATUS_AFTER",
                blueprint,
                "status="
                        + builderBlueprintNeed);
    }

    // TC_BATCH8_R4A5G24_BUILDER_LIVE_CONNECTOR_PLACEMENT
    @Nullable
    private traincraft.item.TrackBlueprintItem.BlueprintPlacement
            resolveBuilderConnectedBlueprintPlacement(
                    ItemStack sourceBlueprint,
                    int previousIndex,
                    BlockPos previousActualAnchor,
                    float previousActualYaw,
                    ItemStack selectedTrack,
                    boolean selectedLeft,
                    BlockPos recordedTarget,
                    float recordedYaw) {
        if (sourceBlueprint == null
                || sourceBlueprint.isEmpty()
                || previousActualAnchor == null
                || selectedTrack == null
                || selectedTrack.isEmpty()
                || previousIndex < 0
                || previousIndex
                        >= traincraft.item.TrackBlueprintItem
                                .segmentCount(sourceBlueprint)) {
            return null;
        }

        CompoundTag previous =
                traincraft.item.TrackBlueprintItem
                        .segment(
                                sourceBlueprint,
                                previousIndex)
                        .copy();

        int recordedPreviousX =
                previous.getInt("X");
        int recordedPreviousY =
                previous.getInt("Y");
        int recordedPreviousZ =
                previous.getInt("Z");
        float recordedPreviousYaw =
                previous.getFloat("Yaw");

        int tcYawQuarter =
                Math.floorMod(
                        quarterYaw(previousActualYaw)
                                - quarterYaw(recordedPreviousYaw),
                        4);

        if (previous.contains("RouteExitX")
                && previous.contains("RouteExitY")
                && previous.contains("RouteExitZ")
                && previous.contains("RouteExitDirection")) {
            int tcExitDx =
                    previous.getInt("RouteExitX")
                            - recordedPreviousX;
            int tcExitDy =
                    previous.getInt("RouteExitY")
                            - recordedPreviousY;
            int tcExitDz =
                    previous.getInt("RouteExitZ")
                            - recordedPreviousZ;

            int tcRotatedExitDx;
            int tcRotatedExitDz;

            switch (tcYawQuarter) {
                case 1 -> {
                    tcRotatedExitDx =
                            -tcExitDz;
                    tcRotatedExitDz =
                            tcExitDx;
                }
                case 2 -> {
                    tcRotatedExitDx =
                            -tcExitDx;
                    tcRotatedExitDz =
                            -tcExitDz;
                }
                case 3 -> {
                    tcRotatedExitDx =
                            tcExitDz;
                    tcRotatedExitDz =
                            -tcExitDx;
                }
                default -> {
                    tcRotatedExitDx =
                            tcExitDx;
                    tcRotatedExitDz =
                            tcExitDz;
                }
            }

            previous.putInt(
                    "RouteExitX",
                    previousActualAnchor.getX()
                            + tcRotatedExitDx);
            previous.putInt(
                    "RouteExitY",
                    previousActualAnchor.getY()
                            + tcExitDy);
            previous.putInt(
                    "RouteExitZ",
                    previousActualAnchor.getZ()
                            + tcRotatedExitDz);

            net.minecraft.core.Direction tcExitDirection =
                    net.minecraft.core.Direction
                            .from3DDataValue(
                                    previous.getInt(
                                            "RouteExitDirection"));

            for (int tcTurn = 0;
                    tcTurn < tcYawQuarter;
                    tcTurn++) {
                tcExitDirection =
                        tcExitDirection.getClockWise();
            }

            previous.putInt(
                    "RouteExitDirection",
                    tcExitDirection.get3DDataValue());
        }

        previous.putInt(
                "X",
                previousActualAnchor.getX());
        previous.putInt(
                "Y",
                previousActualAnchor.getY());
        previous.putInt(
                "Z",
                previousActualAnchor.getZ());
        previous.putFloat(
                "Yaw",
                previousActualYaw);

        ItemStack tcSyntheticBlueprint =
                sourceBlueprint.copy();

        CompoundTag tcSyntheticRoot =
                tcSyntheticBlueprint.getOrCreateTag();
        CompoundTag tcSyntheticData =
                new CompoundTag();

        tcSyntheticData.putLong(
                "Origin",
                BlockPos.ZERO.asLong());
        tcSyntheticData.putFloat(
                "BaseYaw",
                0.0F);
        tcSyntheticData.putBoolean(
                "SelectedLeft",
                selectedLeft);

        net.minecraft.resources.ResourceLocation tcSelectedKey =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(selectedTrack.getItem());
        if (tcSelectedKey != null) {
            tcSyntheticData.putString(
                    "SelectedTrack",
                    tcSelectedKey.toString());
        }

        net.minecraft.nbt.ListTag tcSyntheticSegments =
                new net.minecraft.nbt.ListTag();
        tcSyntheticSegments.add(previous);
        tcSyntheticData.put(
                "Segments",
                tcSyntheticSegments);

        tcSyntheticRoot.put(
                "TafariTrackBlueprint",
                tcSyntheticData);

        ItemStack tcSelected =
                selectedTrack.copy();
        tcSelected.setCount(1);
        traincraft.item.TrackBlueprintItem
                .applyUnifiedDirection(
                        tcSelected,
                        selectedLeft);

        traincraft.item.TrackBlueprintItem.BlueprintPlacement result =
                traincraft.item.TrackBlueprintItem
                        .previewPlacement(
                                tcSyntheticBlueprint,
                                tcSelected,
                                recordedTarget,
                                recordedYaw);

        if (result != null) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-CONNECTOR-RESOLVED] id={} tick={} previousIndex={} previousRecorded=({}, {}, {}) previousRecordedYaw={} previousActual={} previousActualYaw={} yawQuarter={} requested={} requestedYaw={} result={} resultYaw={}",
                            getId(),
                            tickCount,
                            previousIndex,
                            recordedPreviousX,
                            recordedPreviousY,
                            recordedPreviousZ,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    recordedPreviousYaw),
                            previousActualAnchor,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    previousActualYaw),
                            tcYawQuarter,
                            recordedTarget,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    recordedYaw),
                            result.anchor(),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    result.yaw()));
        }

        return result;
    }
    // TC_DEV36_R1A_SAVED_STRUCTURE_EXECUTION
    private static final String TC_DEV36_STRUCTURE_KEY =
            "TCDev36StructureKey";
    private static final String TC_DEV36_STRUCTURE_PROGRESS =
            "TCDev36StructureProgress";

    private int tcDev36StructurePlanSignature(
            ItemStack blueprint) {
        int signature = 1;
        int total =
                traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint);

        for (int i = 0;
             i < total;
             i++) {
            net.minecraft.nbt.CompoundTag entry =
                    traincraft.blueprint.BuilderStructurePlan.entry(
                            blueprint,
                            i);

            signature =
                    31 * signature
                            + entry.toString().hashCode();
        }

        return signature;
    }

    private BlockPos tcDev36StructureAnchor(
            ItemStack blueprint) {
        if (traincraft.item.TrackBlueprintItem.hasPreviewAnchor(
                blueprint)) {
            return traincraft.item.TrackBlueprintItem.previewAnchor(
                    blueprint);
        }

        if (builderBlueprintAnchor != null) {
            return builderBlueprintAnchor;
        }

        return builderForwardBlueprintAnchor();
    }

    private int tcDev36StructureRotation(
            ItemStack blueprint) {
        if (traincraft.item.TrackBlueprintItem.hasPreviewAnchor(
                blueprint)) {
            return Math.floorMod(
                    traincraft.item.TrackBlueprintItem.previewRotationQuarter(
                            blueprint),
                    4);
        }

        if (builderBlueprintAnchor != null) {
            return Math.floorMod(
                    builderBlueprintRotationQuarter,
                    4);
        }

        int recordedQuarter =
                quarterYaw(
                        traincraft.item.TrackBlueprintItem.baseYaw(
                                blueprint));
        int builderQuarter =
                quarterYaw(
                        getYRot());

        return Math.floorMod(
                builderQuarter - recordedQuarter,
                4);
    }

    private String tcDev36StructureRunKey(
            ItemStack blueprint,
            BlockPos anchor,
            int rotation) {
        return traincraft.item.TrackBlueprintItem.blueprintId(
                        blueprint)
                + "|"
                + anchor.asLong()
                + "|"
                + Math.floorMod(rotation, 4)
                + "|"
                + traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint)
                + "|"
                + tcDev36StructurePlanSignature(
                        blueprint);
    }

    private int tcDev36StructureProgress(
            ItemStack blueprint,
            BlockPos anchor,
            int rotation) {
        net.minecraft.nbt.CompoundTag persistent =
                getPersistentData();

        String key =
                tcDev36StructureRunKey(
                        blueprint,
                        anchor,
                        rotation);

        if (!key.equals(
                persistent.getString(
                        TC_DEV36_STRUCTURE_KEY))) {
            persistent.putString(
                    TC_DEV36_STRUCTURE_KEY,
                    key);
            persistent.putInt(
                    TC_DEV36_STRUCTURE_PROGRESS,
                    0);
            return 0;
        }

        return Math.max(
                0,
                persistent.getInt(
                        TC_DEV36_STRUCTURE_PROGRESS));
    }

    private void tcDev36SetStructureProgress(
            ItemStack blueprint,
            BlockPos anchor,
            int rotation,
            int progress) {
        net.minecraft.nbt.CompoundTag persistent =
                getPersistentData();

        persistent.putString(
                TC_DEV36_STRUCTURE_KEY,
                tcDev36StructureRunKey(
                        blueprint,
                        anchor,
                        rotation));
        persistent.putInt(
                TC_DEV36_STRUCTURE_PROGRESS,
                Math.max(
                        0,
                        progress));
    }

    private BlockPos tcDev36RotateStructureRelative(
            BlockPos relative,
            int rotation) {
        return switch (Math.floorMod(
                rotation,
                4)) {
            case 1 ->
                    new BlockPos(
                            -relative.getZ(),
                            relative.getY(),
                            relative.getX());
            case 2 ->
                    new BlockPos(
                            -relative.getX(),
                            relative.getY(),
                            -relative.getZ());
            case 3 ->
                    new BlockPos(
                            relative.getZ(),
                            relative.getY(),
                            -relative.getX());
            default ->
                    relative;
        };
    }

    private net.minecraft.world.level.block.state.BlockState
            tcDev36RotateStructureState(
                    net.minecraft.world.level.block.state.BlockState source,
                    int rotation) {
        return switch (Math.floorMod(
                rotation,
                4)) {
            case 1 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            case 2 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
            case 3 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
            default ->
                    source;
        };
    }

    // TC_DEV36_R1H_STRUCTURE_MATERIAL_SOURCES
    // TC_DEV36_R1H_STRUCTURE_MATERIAL_SOURCES
    // TC_DEV36_R1K12_EXPANDED_BUILDER_MATERIALS
    // TC_DEV36_R1H_STRUCTURE_MATERIAL_SOURCES
    // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
    private int tcDev36FindStructureMaterial(
            net.minecraft.world.level.block.Block block) {
        net.minecraft.world.item.Item wanted =
                block.asItem();

        if (wanted == net.minecraft.world.item.Items.AIR) {
            return -1;
        }

        // BLUEPRINT MATERIALS:
        // historical 20..51 + appended 54..77.
        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (slot == 52
                    || slot == 53) {
                continue;
            }

            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (!stack.isEmpty()
                    && stack.getItem() == wanted) {
                return slot;
            }
        }

        // SUPPLIES: historical 1..19 + one appended cell 53.
        for (int slot = 1;
             slot <= 53;
             slot++) {
            if ((slot >= 20
                            && slot <= 52)) {
                continue;
            }

            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (!stack.isEmpty()
                    && stack.getItem() == wanted) {
                return slot;
            }
        }

        return -1;
    }

    // TC_DEV36_R1G_STRUCTURE_DEEP_TRACE
    private String tcDev36R1gLastTraceSignature =
            "";
    private long tcDev36R1gLastTraceAt =
            0L;

    private String tcDev36R1gRackSummary() {
        StringBuilder out =
                new StringBuilder();

        // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
        for (int slot = 1;
             slot <= 77;
             slot++) {
            if (slot == 52) {
                continue;
            }

            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            if (out.length() > 0) {
                out.append(",");
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(
                                    stack.getItem());

            out.append(slot)
                    .append("=")
                    .append(
                            key == null
                                    ? stack.getHoverName().getString()
                                    : key.toString())
                    .append("x")
                    .append(
                            stack.getCount());
        }

        return out.length() == 0
                ? "<empty>"
                : out.toString();
    }

    private void tcDev36R1gTrace(
            String event,
            ItemStack blueprint,
            int structureTotal,
            int structureProgress,
            BlockPos anchor,
            int rotation,
            String detail) {
        if (level().isClientSide) {
            return;
        }

        try {
            String blueprintId =
                    blueprint.getItem()
                            instanceof traincraft.item.TrackBlueprintItem
                            ? traincraft.item.TrackBlueprintItem.blueprintId(
                                    blueprint)
                            : "<not-blueprint>";

            int trackTotal =
                    blueprint.getItem()
                            instanceof traincraft.item.TrackBlueprintItem
                            ? traincraft.item.TrackBlueprintItem.segmentCount(
                                    blueprint)
                            : 0;

            String preview =
                    blueprint.getItem()
                                    instanceof traincraft.item.TrackBlueprintItem
                            && traincraft.item.TrackBlueprintItem.hasPreviewAnchor(
                                    blueprint)
                            ? traincraft.item.TrackBlueprintItem.previewAnchor(
                                    blueprint).toShortString()
                                    + "/"
                                    + traincraft.item.TrackBlueprintItem.previewRotationQuarter(
                                            blueprint)
                            : "none";

            String safeDetail =
                    detail == null
                            ? ""
                            : detail;

            String signature =
                    event
                            + "|"
                            + blueprintId
                            + "|"
                            + builderBlueprintProgress
                            + "|"
                            + structureProgress
                            + "|"
                            + structureTotal
                            + "|"
                            + String.valueOf(
                                    anchor)
                            + "|"
                            + rotation
                            + "|"
                            + builderBlueprintNeed
                            + "|"
                            + safeDetail;

            long now =
                    System.currentTimeMillis();

            if (!signature.equals(
                    tcDev36R1gLastTraceSignature)
                    || now - tcDev36R1gLastTraceAt >= 1500L) {
                tcDev36R1gLastTraceSignature =
                        signature;
                tcDev36R1gLastTraceAt =
                        now;

                org.slf4j.LoggerFactory
                        .getLogger(
                                "TafariCraftBuilderStructure")
                        .info(
                                "[TC-DEV36-R1G][STRUCT] event={} entity={} tick={} running={} power={}/{} speed={} bpId={} locked={} trackProgress={}/{} structProgress={}/{} anchor={} rotQ={} preview={} builderAnchor={} builderRotQ={} status={} rack={} detail={}",
                                event,
                                getId(),
                                tickCount,
                                isBuilderRunning(),
                                powerAmount,
                                getPowerCapacity(),
                                String.format(
                                        java.util.Locale.ROOT,
                                        "%.5f",
                                        getDeltaMovement()
                                                .horizontalDistance()),
                                blueprintId,
                                blueprint.getItem()
                                                instanceof traincraft.item.TrackBlueprintItem
                                        && traincraft.item.TrackBlueprintItem.isLocked(
                                                blueprint),
                                builderBlueprintProgress,
                                trackTotal,
                                structureProgress,
                                structureTotal,
                                anchor == null
                                        ? "none"
                                        : anchor.toShortString(),
                                rotation,
                                preview,
                                String.valueOf(
                                        builderBlueprintAnchor),
                                builderBlueprintRotationQuarter,
                                builderBlueprintNeed,
                                tcDev36R1gRackSummary(),
                                safeDetail);
            }
        }
        catch (Throwable debugError) {
            traincraft.Traincraft.LOGGER.warn(
                    "[TC-DEV36-R1G][STRUCT] trace failed event={}: {}",
                    event,
                    debugError.toString());
        }
    }

    private void tcDev36R1gTraceCurrent(
            String event,
            ItemStack blueprint,
            String detail) {
        int total =
                blueprint.getItem()
                                instanceof traincraft.item.TrackBlueprintItem
                        ? traincraft.blueprint.BuilderStructurePlan.count(
                                blueprint)
                        : 0;

        int progress =
                getPersistentData()
                        .getInt(
                                TC_DEV36_STRUCTURE_PROGRESS);

        BlockPos anchor =
                total > 0
                        ? tcDev36StructureAnchor(
                                blueprint)
                        : null;

        int rotation =
                total > 0
                        ? tcDev36StructureRotation(
                                blueprint)
                        : -1;

        tcDev36R1gTrace(
                event,
                blueprint,
                total,
                progress,
                anchor,
                rotation,
                detail);
    }

    private void tcDev36R1gTickTrace() {
        if (level().isClientSide
                || !isBuilder()
                || tickCount % 20 != 0) {
            return;
        }

        ItemStack blueprint =
                powerInventory.getStackInSlot(
                        52);

        tcDev36R1gTraceCurrent(
                "TICK",
                blueprint,
                "slot52="
                        + (blueprint.isEmpty()
                                ? "EMPTY"
                                : String.valueOf(
                                        net.minecraftforge.registries.ForgeRegistries.ITEMS
                                                .getKey(
                                                        blueprint.getItem())))
                        + " pos="
                        + String.format(
                                java.util.Locale.ROOT,
                                "%.3f,%.3f,%.3f",
                                getX(),
                                getY(),
                                getZ())
                        + " yaw="
                        + String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                getYRot()));
    }
    private boolean tcDev36BuildOneSavedStructureBlock(
            ItemStack blueprint) {
        if (!(level()
                instanceof ServerLevel server)) {
            return true;
        }

        int total =
                traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint);

        if (total <= 0) {
            tcDev36R1gTrace(
                    "NO_STRUCTURE_PLAN",
                    blueprint,
                    total,
                    0,
                    null,
                    -1,
                    "BuildBlocks empty");

            getPersistentData()
                    .remove(
                            TC_DEV36_STRUCTURE_KEY);
            getPersistentData()
                    .remove(
                            TC_DEV36_STRUCTURE_PROGRESS);
            return true;
        }

        BlockPos anchor =
                tcDev36StructureAnchor(
                        blueprint);
        int rotation =
                tcDev36StructureRotation(
                        blueprint);

        int progress =
                tcDev36StructureProgress(
                        blueprint,
                        anchor,
                        rotation);

        tcDev36R1gTrace(
                "PASS_ENTER",
                blueprint,
                total,
                progress,
                anchor,
                rotation,
                "runKey="
                        + tcDev36StructureRunKey(
                                blueprint,
                                anchor,
                                rotation));

        if (progress > total) {
            tcDev36R1gTrace(
                    "PROGRESS_RESET",
                    blueprint,
                    total,
                    progress,
                    anchor,
                    rotation,
                    "progress exceeded total");

            progress = 0;
            tcDev36SetStructureProgress(
                    blueprint,
                    anchor,
                    rotation,
                    progress);
        }

        // Skip already-built cells without consuming materials.
        while (progress < total) {
            net.minecraft.nbt.CompoundTag entry =
                    traincraft.blueprint.BuilderStructurePlan.entry(
                            blueprint,
                            progress);

            net.minecraft.world.level.block.state.BlockState planned =
                    traincraft.blueprint.BuilderStructurePlan.blockState(
                            entry);

            if (planned.isAir()) {
                tcDev36R1gTrace(
                        "SKIP_AIR",
                        blueprint,
                        total,
                        progress,
                        anchor,
                        rotation,
                        "entry="
                                + progress
                                + " nbt="
                                + entry);

                progress++;
                tcDev36SetStructureProgress(
                        blueprint,
                        anchor,
                        rotation,
                        progress);
                continue;
            }

            BlockPos relative =
                    traincraft.blueprint.BuilderStructurePlan.relativePos(
                            entry);

            // TC_DEV36_R1K24_SKIP_STALE_CLEARANCE_BUILD_BLOCK
            //
            // BuildBlocks are persistent Blueprint data. A plan can survive from
            // an older edit/run even when a saved block now falls inside the
            // authoritative train-clearance volume. The r1k19 runtime used to
            // HOLD forever on such an entry before the track route could advance.
            //
            // Keep the clearance rule authoritative: NEVER place or consume an
            // unsafe saved block. But do not deadlock the Builder on stale data.
            // Advance only the saved-structure progress and continue looking for
            // the next safe structure entry. Roadbed/foundation belongs to the
            // separate BuilderInfrastructureSystem, not BuildBlocks.
            if (traincraft.blueprint.BuilderStructurePlan.tooCloseRelative(
                    blueprint,
                    relative)) {
                tcDev36R1gTrace(
                        "SKIP_CLEARANCE_SAVED_BLOCK",
                        blueprint,
                        total,
                        progress,
                        anchor,
                        rotation,
                        "entry="
                                + progress
                                + " relative="
                                + relative.toShortString()
                                + " planned="
                                + planned
                                + " action=SKIP_NO_PLACE_NO_CONSUME");

                progress++;
                tcDev36SetStructureProgress(
                        blueprint,
                        anchor,
                        rotation,
                        progress);

                syncBuilderBlueprint(
                        traincraft.item.TrackBlueprintItem.segmentCount(
                                blueprint),
                        "SKIP CLEARANCE "
                                + progress
                                + "/"
                                + total);

                continue;
            }

            BlockPos rotated =
                    tcDev36RotateStructureRelative(
                            relative,
                            rotation);
            BlockPos target =
                    anchor.offset(
                            rotated);

            planned =
                    tcDev36RotateStructureState(
                            planned,
                            rotation);

            net.minecraft.world.level.block.state.BlockState current =
                    server.getBlockState(
                            target);

            tcDev36R1gTrace(
                    "TARGET",
                    blueprint,
                    total,
                    progress,
                    anchor,
                    rotation,
                    "entry="
                            + progress
                            + " relative="
                            + relative.toShortString()
                            + " rotated="
                            + rotated.toShortString()
                            + " target="
                            + target.toShortString()
                            + " planned="
                            + planned
                            + " current="
                            + current);

            if (current.equals(
                    planned)) {
                tcDev36R1gTrace(
                        "SKIP_WORLD_MATCH",
                        blueprint,
                        total,
                        progress,
                        anchor,
                        rotation,
                        "target="
                                + target.toShortString()
                                + " state="
                                + current);

                progress++;
                tcDev36SetStructureProgress(
                        blueprint,
                        anchor,
                        rotation,
                        progress);
                continue;
            }

            // DEV45: no structure work may replace a protected world block.
            if (current.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                    || current.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
                syncBuilderBlueprint(traincraft.item.TrackBlueprintItem.segmentCount(blueprint),
                        "STRUCT PROTECTED " + target.toShortString());
                return false;
            }

            // Dev36 r1b/r1f remains deliberately non-destructive.
            if (!current.isAir()) {
                tcDev36R1gTrace(
                        "HOLD_BLOCKED",
                        blueprint,
                        total,
                        progress,
                        anchor,
                        rotation,
                        "target="
                                + target.toShortString()
                                + " planned="
                                + planned
                                + " current="
                                + current);

                syncBuilderBlueprint(
                        traincraft.item.TrackBlueprintItem.segmentCount(
                                blueprint),
                        "STRUCT BLOCKED "
                                + target.getX()
                                + " "
                                + target.getY()
                                + " "
                                + target.getZ());
                setDeltaMovement(
                        getDeltaMovement()
                                .multiply(
                                        0.0D,
                                        1.0D,
                                        0.0D));
                return false;
            }

            int materialSlot =
                    tcDev36FindStructureMaterial(
                            planned.getBlock());

            if (materialSlot < 0) {
                net.minecraft.resources.ResourceLocation blockKey =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getKey(
                                        planned.getBlock());

                String shortName =
                        blockKey == null
                                ? planned.getBlock().getName().getString()
                                : blockKey.getPath();

                tcDev36R1gTrace(
                        "HOLD_MISSING",
                        blueprint,
                        total,
                        progress,
                        anchor,
                        rotation,
                        "target="
                                + target.toShortString()
                                + " wanted="
                                + String.valueOf(
                                        blockKey)
                                + " planned="
                                + planned);

                syncBuilderBlueprint(
                        traincraft.item.TrackBlueprintItem.segmentCount(
                                blueprint),
                        "STRUCT NEED "
                                + shortName);
                setDeltaMovement(
                        getDeltaMovement()
                                .multiply(
                                        0.0D,
                                        1.0D,
                                        0.0D));
                return false;
            }

            ItemStack materialBeforeStack =
                    powerInventory.getStackInSlot(
                            materialSlot);
            int materialBefore =
                    materialBeforeStack.getCount();

            boolean setResult =
                    server.setBlock(
                            target,
                            planned,
                            3);

            net.minecraft.world.level.block.state.BlockState worldAfterSet =
                    server.getBlockState(
                            target);

            consumeOne(
                    materialSlot);

            int materialAfter =
                    powerInventory.getStackInSlot(
                            materialSlot)
                            .getCount();

            progress++;
            tcDev36SetStructureProgress(
                    blueprint,
                    anchor,
                    rotation,
                    progress);

            tcDev36R1gTrace(
                    "PLACE",
                    blueprint,
                    total,
                    progress,
                    anchor,
                    rotation,
                    "target="
                            + target.toShortString()
                            + " planned="
                            + planned
                            + " setResult="
                            + setResult
                            + " worldAfter="
                            + worldAfterSet
                            + " materialSlot="
                            + materialSlot
                            + " count="
                            + materialBefore
                            + "->"
                            + materialAfter);

            server.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    target.getX() + 0.5D,
                    target.getY() + 0.5D,
                    target.getZ() + 0.5D,
                    2,
                    0.10D,
                    0.10D,
                    0.10D,
                    0.0D);

            // One real saved block per normal Builder work tick.
            return true;
        }

        tcDev36R1gTrace(
                "STRUCTURE_COMPLETE",
                blueprint,
                total,
                progress,
                anchor,
                rotation,
                "all BuildBlocks processed");

        return true;
    }

    private void tcDev36RefreshStructureStatus(
            ItemStack blueprint) {
        int trackTotal =
                traincraft.item.TrackBlueprintItem.segmentCount(
                        blueprint);

        // While tracks are still active, preserve the accepted track status.
        if (builderBlueprintProgress < trackTotal) {
            return;
        }

        int total =
                traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint);

        if (total <= 0) {
            return;
        }

        BlockPos anchor =
                tcDev36StructureAnchor(
                        blueprint);
        int rotation =
                tcDev36StructureRotation(
                        blueprint);
        int progress =
                tcDev36StructureProgress(
                        blueprint,
                        anchor,
                        rotation);

        if (progress < total) {
            syncBuilderBlueprint(
                    trackTotal,
                    "STRUCTURE "
                            + progress
                            + "/"
                            + total);
        }
        else {
            syncBuilderBlueprint(
                    trackTotal,
                    "COMPLETE");
        }
    }
    private void tryLayBuilderBlueprint(ItemStack blueprint) {
        if (!(level() instanceof ServerLevel server)) return;

        int total = traincraft.item.TrackBlueprintItem.segmentCount(blueprint);
        String blueprintId = traincraft.item.TrackBlueprintItem.blueprintId(blueprint);

        if (!blueprintId.equals(builderBlueprintId) || builderBlueprintAnchor == null) {
            builderBlueprintId = blueprintId;
            builderBlueprintProgress = 0;
            builderBlueprintNeed = "";
            builderBlueprintLastPlacedAnchor = null;
            builderBlueprintLastPlacedYaw = 0.0F;
                        if (traincraft.item.TrackBlueprintItem.hasPreviewAnchor(blueprint)) {
                // TC_BATCH8_R3E_BLUEPRINT_START_AND_GUI
                // The first recorded route placement is anchored exactly to
                // the completed Blueprint's player-clicked Build Start.
                builderBlueprintAnchor =
                        traincraft.item.TrackBlueprintItem.previewAnchor(blueprint);
                builderBlueprintRotationQuarter =
                        traincraft.item.TrackBlueprintItem.previewRotationQuarter(blueprint);
                syncBuilderBlueprint(total, "BUILD START SET");
            } else {
                builderBlueprintAnchor = builderForwardBlueprintAnchor();
                int recordedQuarter =
                        quarterYaw(traincraft.item.TrackBlueprintItem.baseYaw(blueprint));
                int builderQuarter = quarterYaw(getYRot());
                builderBlueprintRotationQuarter =
                    Math.floorMod(
                            (builderQuarter - recordedQuarter) + 2,
                            4);

            // TC_BATCH8_R4A5G11_AUTOSTART_FRONT_ROUTE_CONTIGUOUS
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-AUTO-START-ROTATION] id={} tick={} entityYaw={} oldExpr={} rotQ={}",
                            getId(),
                            tickCount,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    getYRot()),
                            "builderQuarter - recordedQuarter",
                            builderBlueprintRotationQuarter);
                syncBuilderBlueprint(total, "AUTO START");
            }
        }

        if (builderBlueprintProgress >= total) {
            syncBuilderBlueprint(total, "COMPLETE");
            setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            return;
        }

        // TC_BATCH8_R3ZR_BLUEPRINT_ROUTE_ORDER_LOCK
        // Only the exact next saved Blueprint segment may be attempted.
        // The accepted track item's useOn() performs its normal endpoint snap.
        CompoundTag entry = traincraft.item.TrackBlueprintItem.segment(blueprint, builderBlueprintProgress);
        int dx = entry.getInt("X");
        int dy = entry.getInt("Y");
        int dz = entry.getInt("Z");

        int rx;
        int rz;
        switch (builderBlueprintRotationQuarter) {
            case 1 -> { rx = -dz; rz = dx; }
            case 2 -> { rx = -dx; rz = -dz; }
            case 3 -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }

        BlockPos target = builderBlueprintAnchor.offset(rx, dy, rz);
        if (tickCount % 20 == 0) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-LAY-TARGET] id={} tick={} step={}/{} target={} anchor={} rotQ={} builderPos=({},{},{})",
                            getId(),
                            tickCount,
                            builderBlueprintProgress + 1,
                            total,
                            String.valueOf(target),
                            String.valueOf(builderBlueprintAnchor),
                            builderBlueprintRotationQuarter,
                            String.format(java.util.Locale.ROOT, "%.3f", getX()),
                            String.format(java.util.Locale.ROOT, "%.3f", getY()),
                            String.format(java.util.Locale.ROOT, "%.3f", getZ()));
        }
        if (tickCount % 20 == 0) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftBuilderBlueprint").info(
                    "[TC-BUILDER-BP-TARGET] id={} tick={} step={}/{} item={} raw=({},{},{}) rawYaw={} rotQ={} rotated=({},{},{}) anchor={} target={} builderPos=({},{},{}) builderYaw={}",
                    getId(),
                    tickCount,
                    builderBlueprintProgress + 1,
                    total,
                    entry.getString("Item"),
                    dx,
                    dy,
                    dz,
                    String.format(java.util.Locale.ROOT, "%.2f", entry.getFloat("Yaw")),
                    builderBlueprintRotationQuarter,
                    rx,
                    dy,
                    rz,
                    String.valueOf(builderBlueprintAnchor),
                    String.valueOf(target),
                    String.format(java.util.Locale.ROOT, "%.3f", getX()),
                    String.format(java.util.Locale.ROOT, "%.3f", getY()),
                    String.format(java.util.Locale.ROOT, "%.3f", getZ()),
                    String.format(java.util.Locale.ROOT, "%.2f", getYRot()));
        }
        // TC_BATCH8_R4A5B_FOOTPRINT_PROXIMITY
        // TC_BATCH8_R4A5G25H10D_STABLE_LOOKAHEAD
        // Restore the proven 7-block exact-footprint look-ahead.
        // Whole-piece visual placement is accepted; continuity wins over close-lay presentation.
        // Visual construction timing only: wait until the exact next-piece footprint
        // is within 2 blocks, instead of the old 7-block look-ahead.
        // The old target/root distance gate was intentionally removed here.
        // Multipart roots can be tens of blocks from the physical connector.
        // Proximity is checked later against the exact placement footprint.
        // TC_BATCH8_R4A5G24_BUILDER_LIVE_CONNECTOR_PLACEMENT
        // Raw target occupancy is intentionally NOT authoritative here.
        // The exact connector resolver below may move the real placement root.

        // TC_BATCH8_R3ZZY_BUILDER_BLUEPRINT_FOOTPRINT_FOUNDATION
        // Support is prepared later from the exact planned track footprint.


        // TC_BATCH8_R3ZR_BLUEPRINT2_CANONICAL_CONSUMPTION
        // Old blueprints with *_left IDs are translated on read; new
        // blueprints store the canonical family item + a separate hand bit.
        String itemId =
                traincraft.item.TrackBlueprintItem
                        .segmentCanonicalItemId(entry);
        boolean tcLeftHand =
                traincraft.item.TrackBlueprintItem
                        .segmentIsLeft(entry);
        int railSlot = findBuilderExactTrack(itemId);
        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-LAY-TRACK-SLOT] id={} tick={} step={}/{} item={} left={} slot={} count={} target={}",
                        getId(),
                        tickCount,
                        builderBlueprintProgress + 1,
                        total,
                        itemId,
                        tcLeftHand,
                        railSlot,
                        railSlot >= 0
                                ? powerInventory.getStackInSlot(railSlot).getCount()
                                : 0,
                        String.valueOf(target));
        if (railSlot < 0) {
            String shortName =
                    traincraft.item.TrackBlueprintItem
                            .displayTrackName(itemId);
            syncBuilderBlueprint(
                    total,
                    "NEED " + shortName);
            setDeltaMovement(
                    getDeltaMovement()
                            .multiply(0.0D, 1.0D, 0.0D));
            return;
        }

        ItemStack railStack = powerInventory.getStackInSlot(railSlot);
        float placementYaw = entry.getFloat("Yaw") + builderBlueprintRotationQuarter * 90.0F;
        ItemStack plannedTrackStack =
                railStack.copy();
        plannedTrackStack.setCount(1);
        applyBuilderBlueprintHand(plannedTrackStack, tcLeftHand);
        // TC_BATCH8_R4A5G24_BUILDER_LIVE_CONNECTOR_PLACEMENT
        BlockPos tcBuilderRecordedTarget =
                target;
        float tcBuilderRecordedYaw =
                placementYaw;

        if (builderBlueprintProgress > 0) {
            if (builderBlueprintLastPlacedAnchor == null) {
                syncBuilderBlueprint(
                        total,
                        "NO CONNECTOR STATE "
                                + (builderBlueprintProgress + 1)
                                + "/"
                                + total);
                setDeltaMovement(
                        getDeltaMovement()
                                .multiply(0.0D, 1.0D, 0.0D));
                return;
            }

            traincraft.item.TrackBlueprintItem.BlueprintPlacement
                    tcBuilderConnectedPlacement =
                            resolveBuilderConnectedBlueprintPlacement(
                                    blueprint,
                                    builderBlueprintProgress - 1,
                                    builderBlueprintLastPlacedAnchor,
                                    builderBlueprintLastPlacedYaw,
                                    plannedTrackStack,
                                    tcLeftHand,
                                    tcBuilderRecordedTarget,
                                    tcBuilderRecordedYaw);

            if (tcBuilderConnectedPlacement == null) {
                org.slf4j.LoggerFactory
                        .getLogger("TafariCraftBuilderLay")
                        .warn(
                                "[TC-BUILDER-CONNECTOR-REJECT] id={} tick={} step={}/{} previousActual={} previousYaw={} recordedTarget={} recordedYaw={} item={} left={}",
                                getId(),
                                tickCount,
                                builderBlueprintProgress + 1,
                                total,
                                builderBlueprintLastPlacedAnchor,
                                String.format(
                                        java.util.Locale.ROOT,
                                        "%.2f",
                                        builderBlueprintLastPlacedYaw),
                                tcBuilderRecordedTarget,
                                String.format(
                                        java.util.Locale.ROOT,
                                        "%.2f",
                                        tcBuilderRecordedYaw),
                                net.minecraftforge.registries.ForgeRegistries.ITEMS
                                        .getKey(plannedTrackStack.getItem()),
                                tcLeftHand);

                syncBuilderBlueprint(
                        total,
                        "NO CONNECTOR "
                                + (builderBlueprintProgress + 1)
                                + "/"
                                + total);
                setDeltaMovement(
                        getDeltaMovement()
                                .multiply(0.0D, 1.0D, 0.0D));
                return;
            }

            target =
                    tcBuilderConnectedPlacement.anchor();
            placementYaw =
                    tcBuilderConnectedPlacement.yaw();

            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-CONNECTOR-PLACEMENT] id={} tick={} step={}/{} previousActual={} previousYaw={} recordedTarget={} recordedYaw={} resolvedTarget={} resolvedYaw={} item={} left={}",
                            getId(),
                            tickCount,
                            builderBlueprintProgress + 1,
                            total,
                            builderBlueprintLastPlacedAnchor,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    builderBlueprintLastPlacedYaw),
                            tcBuilderRecordedTarget,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    tcBuilderRecordedYaw),
                            target,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    placementYaw),
                            net.minecraftforge.registries.ForgeRegistries.ITEMS
                                    .getKey(plannedTrackStack.getItem()),
                            tcLeftHand);
        }

        java.util.List<BlockPos> tcBuilderPlacementCells =
                traincraft.item.TrackBlueprintItem
                        .placementCells(
                                plannedTrackStack,
                                target,
                                placementYaw);

        BlockPos tcNearestBuildCell =
                target;

        double tcNearestBuildDistanceSq =
                distanceToSqr(
                        target.getX() + 0.5D,
                        target.getY() + 0.5D,
                        target.getZ() + 0.5D);

        for (BlockPos tcBuildCell : tcBuilderPlacementCells) {
            double tcCellDistanceSq =
                    distanceToSqr(
                            tcBuildCell.getX() + 0.5D,
                            tcBuildCell.getY() + 0.5D,
                            tcBuildCell.getZ() + 0.5D);

            if (tcCellDistanceSq
                    < tcNearestBuildDistanceSq) {
                tcNearestBuildDistanceSq =
                        tcCellDistanceSq;
                tcNearestBuildCell =
                        tcBuildCell;
            }
        }

        if (tickCount % 20 == 0
                || tcNearestBuildDistanceSq <= 49.0D) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-LAY-PROXIMITY] id={} tick={} step={}/{} root={} nearestCell={} nearestDistanceSq={} rootDistanceSq={} footprintCells={}",
                            getId(),
                            tickCount,
                            builderBlueprintProgress + 1,
                            total,
                            String.valueOf(target),
                            String.valueOf(tcNearestBuildCell),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.3f",
                                    tcNearestBuildDistanceSq),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.3f",
                                    distanceToSqr(
                                            target.getX() + 0.5D,
                                            target.getY() + 0.5D,
                                            target.getZ() + 0.5D)),
                            tcBuilderPlacementCells.size());
        }

        if (tcNearestBuildDistanceSq > 49.0D) {
            syncBuilderBlueprint(
                    total,
                    "NEXT "
                            + (builderBlueprintProgress + 1)
                            + "/"
                            + total);
            return;
        }
        String foundationProblem =
                prepareBuilderBlueprintFoundation(
                        server,
                        plannedTrackStack,
                        target,
                        placementYaw);

                org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-LAY-FOUNDATION-RESULT] id={} tick={} step={}/{} target={} result={}",
                        getId(),
                        tickCount,
                        builderBlueprintProgress + 1,
                        total,
                        String.valueOf(target),
                        foundationProblem.isEmpty()
                                ? "OK"
                                : foundationProblem);
if (!foundationProblem.isEmpty()) {
            syncBuilderBlueprint(
                    total,
                    foundationProblem);
            setDeltaMovement(
                    getDeltaMovement()
                            .multiply(0.0D, 1.0D, 0.0D));
            return;
        }

                // TC_BATCH8_R4A5G13_BUILDER_MEDIUM_PART0_USEON
        //
        // Blueprint target is the hidden Medium assembly root used by
        // placementCells(). Accepted LegacyMediumCurveTrackItem manual
        // placement instead treats clickedPos.above() as physical PART 0.
        // Therefore delegate Medium placement at footprint cell 0 so the
        // real Item.useOn() produces exactly the footprint we already used
        // for proximity/foundation checks. All other track items still
        // receive the original Blueprint target unchanged.
        BlockPos tcBuilderDelegatedTarget =
                target;

        if (railStack.getItem()
                        instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                && tcBuilderPlacementCells != null
                && !tcBuilderPlacementCells.isEmpty()) {

            tcBuilderDelegatedTarget =
                    tcBuilderPlacementCells.get(0);

            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-MEDIUM-PART0-USEON] id={} tick={} blueprintRoot={} delegatedPart0={} placementYaw={} footprint={}",
                            getId(),
                            tickCount,
                            target,
                            tcBuilderDelegatedTarget,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    placementYaw),
                            tcBuilderPlacementCells);
        }

        if (!tryLayBuilderBlueprintTrackItem(
                server,
                tcBuilderDelegatedTarget,
                railStack,
                placementYaw,
                tcLeftHand)) {
            syncBuilderBlueprint(total, "PLACEMENT FAILED");
            setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            return;
        }

        // TC_BATCH8_R4A5G9A_BUILDER_QUARTER_INWARD_PRIME
        //
        // r4a5g8 runtime proved the new 29x can be placed while the Builder is
        // sitting on its terminal tangent. Immediately point motion toward the
        // adjacent interior cell so autonomous drive enters the newly built
        // quarter curve instead of preserving the old outward straight vector.
        // TC_BATCH8_R4A5G12_MEDIUM_HANDOFF_WORLDSTATE_DEBUG
        debugBuilderPlacedMediumCurveWorld(
                server,
                railStack,
                target,
                placementYaw,
                tcBuilderPlacementCells);
        // TC_BATCH8_R4A5G24_BUILDER_LIVE_CONNECTOR_PLACEMENT
        builderBlueprintLastPlacedAnchor =
                target;
        builderBlueprintLastPlacedYaw =
                placementYaw;

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-CONNECTOR-COMMIT] id={} tick={} step={}/{} actualAnchor={} actualYaw={} recordedTarget={} recordedYaw={}",
                        getId(),
                        tickCount,
                        builderBlueprintProgress + 1,
                        total,
                        builderBlueprintLastPlacedAnchor,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                builderBlueprintLastPlacedYaw),
                        tcBuilderRecordedTarget,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                tcBuilderRecordedYaw));
        primeBuilderIntoPlacedQuarterCurve(
                railStack,
                tcBuilderPlacementCells);

        consumeOne(railSlot);
        builderBlueprintProgress++;
        syncBuilderBlueprint(total, builderBlueprintProgress >= total ? "COMPLETE" : "PLACED " + builderBlueprintProgress + "/" + total);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5D, target.getY() + 0.3D, target.getZ() + 0.5D, 3, 0.12D, 0.05D, 0.12D, 0.0D);
    }

    private String prepareBuilderBlueprintFoundation(
            ServerLevel server,
            ItemStack plannedTrackStack,
            BlockPos anchor,
            float placementYaw) {
        // TC_DEV34_R1C1A_BUILDER_INFRASTRUCTURE
        if (traincraft.builder.BuilderInfrastructureSystem
                .isEnabled(this)) {
            return traincraft.builder.BuilderInfrastructureSystem
                    .prepare(
                            server,
                            this,
                            plannedTrackStack,
                            anchor,
                            placementYaw);
        }

        // Accepted r3zzy classic foundation fallback.
        java.util.LinkedHashSet<BlockPos> plannedCells =
                new java.util.LinkedHashSet<>(
                        traincraft.item.TrackBlueprintItem
                                .placementCells(
                                        plannedTrackStack,
                                        anchor,
                                        placementYaw));

        if (plannedCells.isEmpty()) {
            plannedCells.add(anchor);
        }

        int missingSupport = 0;

        for (BlockPos railCell : plannedCells) {
            BlockState railState =
                    server.getBlockState(railCell);

            if (!railState.isAir()) {
                var blockKey =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getKey(
                                        railState.getBlock());

                boolean existingTrack =
                        railState.is(
                                        net.minecraft.tags.BlockTags.RAILS)
                                || (blockKey != null
                                        && "traincraft".equals(
                                                blockKey.getNamespace())
                                        && blockKey.getPath()
                                                .startsWith("track_"));

                if (!existingTrack) {
                    return "BLOCKED "
                            + railCell.getX()
                            + " "
                            + railCell.getY()
                            + " "
                            + railCell.getZ();
                }
            }

            if (server.getBlockState(
                            railCell.below())
                    .isAir()) {
                missingSupport++;
            }
        }

        if (countBuilderSupportItems()
                < missingSupport) {
            return "NEED BALLAST/SUPPORT x"
                    + missingSupport;
        }

        for (BlockPos railCell : plannedCells) {
            BlockPos supportPos =
                    railCell.below();

            if (server.getBlockState(
                            supportPos)
                    .isAir()) {
                int supportSlot =
                        findBuilderSupport();

                if (supportSlot < 0) {
                    return "NEED BALLAST/SUPPORT";
                }

                ItemStack supportStack =
                        powerInventory.getStackInSlot(
                                supportSlot);

                if (!(supportStack.getItem()
                        instanceof net.minecraft.world.item.BlockItem blockItem)) {
                    return "NEED BALLAST/SUPPORT";
                }

                server.setBlock(
                        supportPos,
                        blockItem.getBlock()
                                .defaultBlockState(),
                        3);

                consumeOne(
                        supportSlot);
            }

            BlockPos lowerSupportPos =
                    supportPos.below();

            if (server.getBlockState(
                            lowerSupportPos)
                    .isAir()) {
                int lowerSlot =
                        findBuilderLowerSupport();

                if (lowerSlot >= 0) {
                    ItemStack lowerStack =
                            powerInventory.getStackInSlot(
                                    lowerSlot);

                    if (lowerStack.getItem()
                            instanceof net.minecraft.world.item.BlockItem blockItem) {
                        server.setBlock(
                                lowerSupportPos,
                                blockItem.getBlock()
                                        .defaultBlockState(),
                                3);

                        consumeOne(
                                lowerSlot);
                    }
                }
            }
        }

        return "";
    }

    private int countBuilderSupportItems() {
        int total = 0;

        int[] fixedSlots = {3, 11, 12, 13};
        for (int slot : fixedSlots) {
            ItemStack stack =
                    powerInventory.getStackInSlot(slot);
            if (isBuilderBlock(stack)) {
                total += stack.getCount();
            }
        }

        for (int slot = 20;
             slot <= 51;
             slot++) {
            ItemStack stack =
                    powerInventory.getStackInSlot(slot);
            if (isBuilderBlock(stack)) {
                total += stack.getCount();
            }
        }

        return total;
    }
    private BlockPos builderForwardBlueprintAnchor() {
        // TC_BATCH8_R4A5G11_AUTOSTART_FRONT_ROUTE_CONTIGUOUS
        //
        // The established entity/model front convention is:
        //   X = sin(yaw)
        //   Z = -cos(yaw)
        //
        // AUTO START must anchor the first Blueprint rail in the immediately
        // adjacent block. The old 1.75D offset could skip one whole rail cell
        // between the Builder and step 1.
        double yaw =
                Math.toRadians(
                        getTrainFacingYaw());

        double fx =
                Math.sin(yaw);

        double fz =
                -Math.cos(yaw);

        BlockPos anchor =
                BlockPos.containing(
                        getX() + fx * 1.0D,
                        getY(),
                        getZ() + fz * 1.0D);

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-AUTO-START-CONTIGUOUS] id={} tick={} trainYaw={} pos=({},{},{}) modelFront=({},{}) anchor={}",
                        getId(),
                        tickCount,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                getTrainFacingYaw()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getX()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getY()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getZ()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                fx),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                fz),
                        anchor);

        return anchor;
    }

    private static int quarterYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0F), 4);
    }

    private int findBuilderExactTrack(String itemId) {
        // TC_BATCH8_R4A5G4_BUILDER_29X32X_MATERIAL_LOOKUP
        // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
        String normalized =
                itemId == null
                        ? ""
                        : itemId.trim();

        if (!normalized.isEmpty()
                && normalized.indexOf(':') < 0) {
            normalized =
                    "traincraft:" + normalized;
        }

        String wantedCanonical =
                traincraft.item.TrackBlueprintItem
                        .canonicalTrackId(
                                normalized);

        int[] legacyTrackSlots = {
                1, 8, 9, 10
        };

        // Exact/canonical match: all visible BLUEPRINT MATERIALS.
        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (slot == 52
                    || slot == 53) {
                continue;
            }

            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(
                                    stack.getItem());

            if (key != null
                    && wantedCanonical.equals(
                            traincraft.item.TrackBlueprintItem
                                    .canonicalTrackId(
                                            key.toString()))) {
                return slot;
            }
        }

        for (int slot :
                legacyTrackSlots) {
            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(
                                    stack.getItem());

            if (key != null
                    && wantedCanonical.equals(
                            traincraft.item.TrackBlueprintItem
                                    .canonicalTrackId(
                                            key.toString()))) {
                return slot;
            }
        }

        net.minecraft.resources.ResourceLocation wantedKey =
                net.minecraft.resources.ResourceLocation
                        .tryParse(
                                wantedCanonical);

        String wantedQuarterBase =
                wantedKey == null
                        ? ""
                        : builderSingleIdQuarterBase(
                                wantedKey.getPath());

        boolean allowMaterialFamily =
                wantedKey != null
                        && "traincraft".equals(
                                wantedKey.getNamespace())
                        && !wantedQuarterBase.isEmpty()
                        && wantedKey.getPath()
                                .equals(
                                        wantedQuarterBase);

        if (!allowMaterialFamily) {
            return -1;
        }

        // Preserve accepted 29x/32x material-family fallback.
        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (slot == 52
                    || slot == 53) {
                continue;
            }

            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(
                                    stack.getItem());

            if (key != null
                    && "traincraft".equals(
                            key.getNamespace())
                    && wantedQuarterBase.equals(
                            builderSingleIdQuarterBase(
                                    key.getPath()))) {
                org.slf4j.LoggerFactory
                        .getLogger(
                                "TafariCraftBuilderLay")
                        .info(
                                "[TC-BUILDER-LAY-MATERIAL-FALLBACK] id={} tick={} requested={} supplied={} slot={}",
                                getId(),
                                tickCount,
                                wantedCanonical,
                                key,
                                slot);
                return slot;
            }
        }

        for (int slot :
                legacyTrackSlots) {
            ItemStack stack =
                    powerInventory.getStackInSlot(
                            slot);

            if (stack.isEmpty()) {
                continue;
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(
                                    stack.getItem());

            if (key != null
                    && "traincraft".equals(
                            key.getNamespace())
                    && wantedQuarterBase.equals(
                            builderSingleIdQuarterBase(
                                    key.getPath()))) {
                org.slf4j.LoggerFactory
                        .getLogger(
                                "TafariCraftBuilderLay")
                        .info(
                                "[TC-BUILDER-LAY-MATERIAL-FALLBACK] id={} tick={} requested={} supplied={} slot={}",
                                getId(),
                                tickCount,
                                wantedCanonical,
                                key,
                                slot);
                return slot;
            }
        }

        return -1;
    }

    // TC_BATCH8_R4A5G4_BUILDER_SINGLE_ID_QUARTER_MATERIAL_HAND
    //
    // 29x/32x are single registry-ID directional families. Accepted material
    // variants keep the same geometry ID plus one suffix. The Builder writes
    // the Blueprint hand onto the temporary placement stack, never the rack
    // stack, so one supplied material stack can build either hand safely.
    private static String builderSingleIdQuarterBase(
            String path) {
        if (path == null) {
            return "";
        }

        String[] bases = {
                "track_curve_29x",
                "track_curve_32x"
        };

        String[] suffixes = {
                "",
                "_steel",
                "_copper",
                "_snow",
                "_energy"
        };

        for (String base : bases) {
            for (String suffix : suffixes) {
                if (path.equals(base + suffix)) {
                    return base;
                }
            }
        }

        return "";
    }

    private void applyBuilderBlueprintHand(
            ItemStack stack,
            boolean leftHand) {
        traincraft.item.TrackBlueprintItem
                .applyUnifiedDirection(
                        stack,
                        leftHand);

        if (stack == null
                || stack.isEmpty()) {
            return;
        }

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(
                                stack.getItem());

        if (key == null
                || !"traincraft".equals(
                        key.getNamespace())
                || builderSingleIdQuarterBase(
                        key.getPath()).isEmpty()) {
            return;
        }

        stack.getOrCreateTag()
                .putBoolean(
                        "TafariCraftUnifiedTrackLeft",
                        leftHand);

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-LAY-HAND] id={} tick={} item={} blueprintHand={} appliedTag={}",
                        getId(),
                        tickCount,
                        key,
                        leftHand ? "LEFT" : "RIGHT",
                        stack.getOrCreateTag()
                                .getBoolean(
                                        "TafariCraftUnifiedTrackLeft"));
    }
    // TC_BATCH8_R4A5G12_MEDIUM_HANDOFF_WORLDSTATE_DEBUG
    private void debugBuilderPlacedMediumCurveWorld(
            net.minecraft.server.level.ServerLevel server,
            ItemStack placedStack,
            BlockPos target,
            float placementYaw,
            java.util.List<BlockPos> expectedCells) {

        if (server == null
                || placedStack == null
                || !(placedStack.getItem()
                        instanceof traincraft.item.track.LegacyMediumCurveTrackItem)) {
            return;
        }

        net.minecraft.core.Direction expectedFacing =
                net.minecraft.core.Direction.fromYRot(
                        placementYaw);

        StringBuilder cells =
                new StringBuilder();

        if (expectedCells != null) {
            for (int i = 0;
                 i < expectedCells.size();
                 i++) {

                BlockPos pos =
                        expectedCells.get(i);

                net.minecraft.world.level.block.state.BlockState state =
                        server.getBlockState(pos);

                net.minecraft.resources.ResourceLocation key =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getKey(
                                        state.getBlock());

                if (cells.length() > 0) {
                    cells.append(" | ");
                }

                cells.append(i)
                        .append("@")
                        .append(pos)
                        .append("=")
                        .append(key)
                        .append(" ")
                        .append(state);
            }
        }

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-MEDIUM-WORLD-AFTER-PLACE] id={} tick={} target={} placementYaw={} expectedFacing={} cells={}",
                        getId(),
                        tickCount,
                        target,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                placementYaw),
                        expectedFacing,
                        cells);
    }

    private void debugBuilderOffRailWorld() {
        if (!(level()
                instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }

        BlockPos center =
                BlockPos.containing(
                        getX(),
                        getY(),
                        getZ());

        StringBuilder found =
                new StringBuilder();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos =
                            center.offset(
                                    dx,
                                    dy,
                                    dz);

                    net.minecraft.world.level.block.state.BlockState state =
                            server.getBlockState(pos);

                    net.minecraft.resources.ResourceLocation key =
                            net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                    .getKey(
                                            state.getBlock());

                    boolean rail =
                            state.getBlock()
                                    instanceof net.minecraft.world.level.block.BaseRailBlock;

                    boolean tcTrack =
                            key != null
                                    && "traincraft".equals(
                                            key.getNamespace())
                                    && key.getPath().startsWith(
                                            "track_");

                    if (!rail
                            && !tcTrack) {
                        continue;
                    }

                    if (found.length() > 0) {
                        found.append(" | ");
                    }

                    found.append(pos)
                            .append("=")
                            .append(key)
                            .append(" ")
                            .append(state);
                }
            }
        }

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-OFFRAIL-WORLD] id={} tick={} pos=({},{},{}) center={} yaw={} motion=({},{},{}) nearby={}",
                        getId(),
                        tickCount,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getX()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getY()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                getZ()),
                        center,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.2f",
                                getYRot()),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                getDeltaMovement().x),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                getDeltaMovement().y),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                getDeltaMovement().z),
                        found);
    }
    private void primeBuilderIntoPlacedQuarterCurve(
            ItemStack placedStack,
            java.util.List<BlockPos> placementCells) {

        if (placedStack == null
                || placedStack.isEmpty()
                || placementCells == null
                || placementCells.size() < 2) {
            return;
        }

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(
                                placedStack.getItem());

        if (key == null
                || !"traincraft".equals(
                        key.getNamespace())) {
            return;
        }

        String quarterBase =
                builderSingleIdQuarterBase(
                        key.getPath());

        if (quarterBase.isEmpty()) {
            return;
        }

        BlockPos first =
                placementCells.get(0);

        BlockPos second =
                placementCells.get(1);

        int lastIndex =
                placementCells.size() - 1;

        BlockPos last =
                placementCells.get(lastIndex);

        BlockPos previous =
                placementCells.get(lastIndex - 1);

        double firstDistanceSq =
                distanceToSqr(
                        first.getX() + 0.5D,
                        first.getY() + 0.5D,
                        first.getZ() + 0.5D);

        double lastDistanceSq =
                distanceToSqr(
                        last.getX() + 0.5D,
                        last.getY() + 0.5D,
                        last.getZ() + 0.5D);

        boolean enterFromFirst =
                firstDistanceSq <= lastDistanceSq;

        BlockPos entry =
                enterFromFirst
                        ? first
                        : last;

        BlockPos interior =
                enterFromFirst
                        ? second
                        : previous;

        double dx =
                interior.getX() - entry.getX();

        double dz =
                interior.getZ() - entry.getZ();

        double directionLength =
                Math.sqrt(
                        dx * dx
                                + dz * dz);

        if (directionLength <= 1.0E-6D) {
            return;
        }

        dx /= directionLength;
        dz /= directionLength;

        net.minecraft.world.phys.Vec3 oldMotion =
                getDeltaMovement();

        double horizontalSpeed =
                Math.sqrt(
                        oldMotion.x * oldMotion.x
                                + oldMotion.z * oldMotion.z);

        final double builderTargetSpeed =
                10.0D / 72.0D;

        double primeSpeed =
                horizontalSpeed > 0.005D
                        ? Math.min(
                                horizontalSpeed,
                                builderTargetSpeed)
                        : 0.060D;

        setDeltaMovement(
                dx * primeSpeed,
                oldMotion.y,
                dz * primeSpeed);

        org.slf4j.LoggerFactory
                .getLogger("TafariCraftBuilderLay")
                .info(
                        "[TC-BUILDER-QUARTER-INWARD-PRIME] id={} tick={} item={} family={} endpoint={} entry={} interior={} firstDistanceSq={} lastDistanceSq={} oldMotion=({},{}) newMotion=({},{}) speed={}",
                        getId(),
                        tickCount,
                        key,
                        quarterBase,
                        enterFromFirst ? "FIRST" : "LAST",
                        entry,
                        interior,
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                firstDistanceSq),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.3f",
                                lastDistanceSq),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                oldMotion.x),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                oldMotion.z),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                dx * primeSpeed),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                dz * primeSpeed),
                        String.format(
                                java.util.Locale.ROOT,
                                "%.5f",
                                primeSpeed));
    }
    private boolean tryLayBuilderBlueprintTrackItem(ServerLevel server, BlockPos railPos, ItemStack sourceStack, float placementYaw, boolean leftHand) {
        if (sourceStack.isEmpty() || !traincraft.item.TrackBlueprintItem.isTrackStack(sourceStack)) return false;
        net.minecraft.world.item.Item item = sourceStack.getItem();
        net.minecraftforge.common.util.FakePlayer fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server);
        fake.setPos(getX(), getY(), getZ());
        fake.setXRot(0.0F);
        fake.setYRot(placementYaw);
        // TC_BATCH8_R4A5G14_BUILDER_MEDIUM_SAVED_HAND
        //
        // Accepted LegacyMediumCurveTrackItem uses real-player Shift to choose
        // the alternate continuation. Mirror the Blueprint's saved LEFT/RIGHT
        // hand onto the delegated FakePlayer so Item.useOn() cannot silently
        // replace a saved LEFT Medium with its default C-bend.
        boolean tcBuilderDelegatedMedium =
                item instanceof traincraft.item.track.LegacyMediumCurveTrackItem;

        boolean tcBuilderMediumAlternateHand =
                tcBuilderDelegatedMedium
                        && leftHand;

        // FakePlayerFactory reuses the Minecraft fake player. Always write
        // false for non-Medium/right-hand calls so Shift state cannot leak
        // between Blueprint segments.
        fake.setShiftKeyDown(
                tcBuilderMediumAlternateHand);

        if (tcBuilderDelegatedMedium) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-MEDIUM-HAND-DELEGATE] id={} tick={} leftHand={} fakeShift={} railPos={} placementYaw={}",
                            getId(),
                            tickCount,
                            leftHand,
                            tcBuilderMediumAlternateHand,
                            railPos,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    placementYaw));
        }
        ItemStack one = sourceStack.copy();
        one.setCount(1);
        applyBuilderBlueprintHand(one, leftHand);

        // TC_BATCH8_R4A5G8_BUILDER_ENDPOINT_OVERLAP_TAG
        // Private marker on the one-item delegated Builder stack only.
        // The rack stack and all manual player-held track stacks are unchanged.
        one.getOrCreateTag().putBoolean(
                "TafariCraftBuilderBlueprintPlacement",
                true);
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, one);
        BlockPos support = railPos.below();
        net.minecraft.world.phys.BlockHitResult hit =
                new net.minecraft.world.phys.BlockHitResult(
                        new net.minecraft.world.phys.Vec3(support.getX() + 0.5D, support.getY() + 1.0D, support.getZ() + 0.5D),
                        net.minecraft.core.Direction.UP,
                        support,
                        false);
        net.minecraft.world.InteractionResult result =
                item.useOn(new net.minecraft.world.item.context.UseOnContext(fake, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean consumed = result.consumesAction();
        org.slf4j.LoggerFactory.getLogger("TafariCraftBuilderBlueprint").info(
                "[TC-BUILDER-BP-USEON] id={} tick={} item={} railPos={} support={} placementYaw={} result={} consumes={} sourceCount={}",
                getId(),
                tickCount,
                String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item)),
                String.valueOf(railPos),
                String.valueOf(support),
                String.format(java.util.Locale.ROOT, "%.2f", placementYaw),
                result,
                consumed,
                sourceStack.getCount());
        return consumed;
    }
    private void tryLayBuilderRail() {
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount % 20 == 0) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-LAY-LEGACY] id={} tick={} pos=({},{},{}) motion=({},{},{})",
                            getId(),
                            tickCount,
                            String.format(java.util.Locale.ROOT, "%.3f", getX()),
                            String.format(java.util.Locale.ROOT, "%.3f", getY()),
                            String.format(java.util.Locale.ROOT, "%.3f", getZ()),
                            String.format(java.util.Locale.ROOT, "%.4f", getDeltaMovement().x),
                            String.format(java.util.Locale.ROOT, "%.4f", getDeltaMovement().y),
                            String.format(java.util.Locale.ROOT, "%.4f", getDeltaMovement().z));
        }

        double yaw = Math.toRadians(getTrainFacingYaw());
        double fx = Math.sin(yaw);
        double fz = -Math.cos(yaw);

        Vec3 motion = getDeltaMovement();
        double dot = motion.x * fx + motion.z * fz;
        if (dot < 0.0D) {
            fx = -fx;
            fz = -fz;
        }

        BlockPos railPos = BlockPos.containing(
                getX() + fx * 1.75D,
                getY(),
                getZ() + fz * 1.75D);

        if (!server.getBlockState(railPos).isAir()) {
            return;
        }

        BlockPos supportPos = railPos.below();

        if (server.getBlockState(supportPos).isAir()) {
            int supportSlot = findBuilderSupport();
            if (supportSlot < 0) return;

            ItemStack supportStack = powerInventory.getStackInSlot(supportSlot);
            if (!(supportStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
                return;
            }

            server.setBlock(supportPos, blockItem.getBlock().defaultBlockState(), 3);
            consumeOne(supportSlot);
        }

        BlockPos lowerSupportPos = supportPos.below();
        if (server.getBlockState(lowerSupportPos).isAir()) {
            int lowerSlot = findBuilderLowerSupport();
            if (lowerSlot >= 0) {
                ItemStack lowerStack = powerInventory.getStackInSlot(lowerSlot);
                if (lowerStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                    server.setBlock(lowerSupportPos, blockItem.getBlock().defaultBlockState(), 3);
                    consumeOne(lowerSlot);
                }
            }
        }

        int railSlot = findBuilderRail();
        if (railSlot < 0) return;

        ItemStack railStack = powerInventory.getStackInSlot(railSlot);
        if (tryLayBuilderTrackItem(server, railPos, railStack)) {
            consumeOne(railSlot);
        }
    }

    @Nullable
    private BlockState builderRailState(ItemStack stack) {
        if (stack.is(Items.RAIL)) {
            return net.minecraft.world.level.block.Blocks.RAIL.defaultBlockState();
        }
        if (stack.is(TCBlocks.TRACK_NORMAL.get().asItem())) {
            return TCBlocks.TRACK_NORMAL.get().defaultBlockState();
        }
        return null;
    }


    /**
     * r2b Tracks Builder delegates to the real item placement path.
     * Curves/switches/crossings therefore keep the accepted LegacyBatchTrackItem
     * snap/geometry logic rather than duplicating it here.
     */
    private boolean tryLayBuilderTrackItem(ServerLevel server,
                                           BlockPos railPos,
                                           ItemStack sourceStack) {
        if (sourceStack.isEmpty()) return false;

        net.minecraft.world.item.Item item = sourceStack.getItem();

        boolean vanillaRail =
                sourceStack.is(Items.RAIL)
                        || sourceStack.is(Items.POWERED_RAIL)
                        || sourceStack.is(Items.DETECTOR_RAIL)
                        || sourceStack.is(Items.ACTIVATOR_RAIL);

        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        boolean tafariTrack = key != null
                && "traincraft".equals(key.getNamespace())
                && key.getPath().startsWith("track_");

        if (!vanillaRail && !tafariTrack) return false;

        net.minecraftforge.common.util.FakePlayer fake =
                net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server);

        fake.setPos(getX(), getY(), getZ());
        fake.setXRot(0.0F);
        fake.setYRot(getYRot());

        ItemStack one = sourceStack.copy();
        one.setCount(1);
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, one);

        BlockPos support = railPos.below();
        net.minecraft.world.phys.BlockHitResult hit =
                new net.minecraft.world.phys.BlockHitResult(
                        new net.minecraft.world.phys.Vec3(
                                support.getX() + 0.5D,
                                support.getY() + 1.0D,
                                support.getZ() + 0.5D),
                        net.minecraft.core.Direction.UP,
                        support,
                        false);

        net.minecraft.world.InteractionResult result =
                item.useOn(new net.minecraft.world.item.context.UseOnContext(
                        fake,
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        hit));

        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);

        return result.consumesAction();
    }
    private int findBuilderRail() {
        if (isRailStack(powerInventory.getStackInSlot(1))) return 1;
        for (int i = 8; i <= 10; i++) {
            if (isRailStack(powerInventory.getStackInSlot(i))) return i;
        }
        for (int i = 20; i < powerInventory.getSlots(); i++) {
            if (isRailStack(powerInventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private boolean isRailStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.is(Items.RAIL)
                || stack.is(Items.POWERED_RAIL)
                || stack.is(Items.DETECTOR_RAIL)
                || stack.is(Items.ACTIVATOR_RAIL)) {
            return true;
        }

        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null
                && "traincraft".equals(key.getNamespace())
                && key.getPath().startsWith("track_");
    }

    private int findBuilderSupport() {
        if (isBuilderBlock(powerInventory.getStackInSlot(3))) return 3;
        for (int i = 11; i <= 13; i++) {
            if (isBuilderBlock(powerInventory.getStackInSlot(i))) return i;
        }
        for (int i = 20; i < powerInventory.getSlots(); i++) {
            if (isBuilderBlock(powerInventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private int findBuilderLowerSupport() {
        if (isBuilderBlock(powerInventory.getStackInSlot(2))) return 2;
        for (int i = 14; i <= 16; i++) {
            if (isBuilderBlock(powerInventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private boolean isBuilderBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem;
    }

    private void consumeOne(int slot) {
        ItemStack stack = powerInventory.getStackInSlot(slot).copy();
        stack.shrink(1);
        powerInventory.setStackInSlot(slot, stack);
    }
    @Override
    protected void tickPowerEffects() {
        if (!(level() instanceof ServerLevel server)) return;
        double speed = getDeltaMovement().horizontalDistance();

        boolean electricPowered = isElectric() && (isOnPoweredEnergyTrack() || hasPortableBackupRedstone());
        boolean combustionPowered = (isDiesel() || isBuilder()) && getPowerAmount() > 0;
        // TC_BATCH7_R4A_CLIENT_STREAMED_POWERED_AUDIO
        // Propulsion is now played client-side as entity-bound streamed audio.


        if ((isDiesel() || isBuilder()) && getPowerAmount() > 0 && tickCount % poweredSmokeInterval() == 0) {
            for (double[] exhaust : poweredExhaustOffsets()) {
                Vec3 world = trainLocalPoint(exhaust[0], exhaust[1], exhaust[2]);
                /*
                 * TC_DIESEL_SMOKE_SCALE_R1F17D
                 * Visual-only density progression. Particle dimensions remain
                 * the same; larger road diesels emit more particles.
                 */
                int count = switch (variantId()) {
                    case "locomotive_diesel_kof",
                         "locomotive_diesel_cd742",
                         "locomotive_diesel_v60",
                         "locomotive_diesel_shunter" -> 1;
                    case "locomotive_diesel_chme3",
                         "locomotive_diesel_gp7",
                         "tracks_builder" -> 2;
                    case "locomotive_diesel_sd40",
                         "locomotive_diesel_sd70" -> 3;
                    default -> 1;
                };
                server.sendParticles(
                        ParticleTypes.SMOKE,
                        world.x,world.y,world.z,count,0.045D,0.03D,0.045D,0.016D);
            }
        }

        if (isElectric() && isOnPoweredEnergyTrack() && speed > 0.015D && tickCount % 3 == 0) {
            double yaw = Math.toRadians(getTrainFacingYaw());
            double fx = Math.sin(yaw), fz = -Math.cos(yaw);
            double rightX = -fz, rightZ = fx;
            double bogie = electricSparkLongitudinalOffset();
            for (double longitudinal : new double[]{-bogie,bogie}) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        getX()+fx*longitudinal+rightX*0.34D,
                        getY()+0.18D,
                        getZ()+fz*longitudinal+rightZ*0.34D,
                        1,0.06D,0.025D,0.06D,0.012D);
            }
        }
    }


    private int dieselSmokeInterval() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 2;
            case "locomotive_diesel_chme3", "locomotive_diesel_gp7", "locomotive_diesel_v60" -> 4;
            default -> 5;
        };
    }

    private double[][] dieselExhaustOffsets() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> new double[][] {
                    { 0.9D, 1.10D, 0.0D }
            };
            case "locomotive_diesel_cd742" -> new double[][] {
                    { 1.2D, 1.23D, 0.0D }
            };
            case "locomotive_diesel_chme3" -> new double[][] {
                    { 0.6D, 1.70D, 0.0D }
            };
            case "locomotive_diesel_gp7" -> new double[][] {
                    { 0.0D, 1.60D, 0.0D },
                    { 0.5D, 1.60D, 0.0D }
            };
            case "locomotive_diesel_sd40" -> new double[][] {
                    { -0.1D, 1.40D, 0.0D },
                    { 0.6D, 1.40D, 0.0D }
            };
            case "locomotive_diesel_sd70" -> new double[][] {
                    { -0.5D, 1.40D, 0.0D },
                    { -0.5D, 1.40D, 0.0D }
            };
            case "locomotive_diesel_shunter" -> new double[][] {
                    { 2.6D, 1.84D, 0.0D }
            };
            case "locomotive_diesel_v60" -> new double[][] {
                    { 0.7D, 1.64D, 0.0D }
            };
            default -> new double[0][0];
        };
    }

    private int poweredSmokeInterval() {
        // r1f17d: small switchers breathe lightly; large road diesels emit more often.
        // Tracks Builder keeps its established 4-tick exhaust cadence.
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 6;
            case "locomotive_diesel_cd742",
                 "locomotive_diesel_v60",
                 "locomotive_diesel_shunter" -> 5;
            case "locomotive_diesel_chme3",
                 "locomotive_diesel_gp7" -> 4;
            case "locomotive_diesel_sd40",
                 "locomotive_diesel_sd70" -> 3;
            case "tracks_builder" -> 4;
            default -> 5;
        };
    }

    private double[][] poweredExhaustOffsets() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> new double[][]{{0.90D,1.10D,0.0D}};
            case "locomotive_diesel_cd742" -> new double[][]{{1.20D,1.23D,0.0D}};
            case "locomotive_diesel_chme3" -> new double[][]{{0.60D,1.70D,0.0D}};
            case "locomotive_diesel_gp7" -> new double[][]{{0.00D,1.60D,0.0D},{0.50D,1.60D,0.0D}};
            case "locomotive_diesel_sd40" -> new double[][]{{-0.10D,1.40D,0.0D},{0.60D,1.40D,0.0D}};
            case "locomotive_diesel_sd70" -> new double[][]{{-0.50D,1.40D,-0.10D},{-0.50D,1.40D,0.10D}};
            case "locomotive_diesel_shunter" -> new double[][]{{2.60D,1.84D,0.0D}};
            case "locomotive_diesel_v60" -> new double[][]{{0.70D,1.64D,0.0D}};
            case "tracks_builder" -> new double[][]{{-1.30D,2.10D,-0.12D},{-1.30D,2.10D,0.12D}};
            default -> new double[0][0];
        };
    }

    private int electricSparkLongitudinalOffsetInt() { return (int)Math.round(electricSparkLongitudinalOffset()*100.0D); }
    private double electricSparkLongitudinalOffset() {
        return switch (variantId()) {
            case "locomotive_electric_high_speed_zeroed" -> 1.75D;
            case "locomotive_electric_vl10", "locomotive_electric_tram_ny" -> 1.35D;
            case "locomotive_electric_tram" -> 0.95D;
            default -> 0.75D;
        };
    }

    private Vec3 trainLocalPoint(double longitudinal, double height, double lateral) {
        double radians = Math.toRadians(getTrainFacingYaw());
        double frontX = Math.sin(radians), frontZ = -Math.cos(radians);
        double rightX = -frontZ, rightZ = frontX;
        return new Vec3(getX()+frontX*longitudinal+rightX*lateral,
                getY()+height,
                getZ()+frontZ*longitudinal+rightZ*lateral);
    }
    @Nullable
    private SoundEvent idleSound() {
        return switch (variantId()) {
            case "tracks_builder" -> null;
            case "locomotive_diesel_cd742" -> TCSounds.DIESEL_742_SLOW.get();
            case "locomotive_electric_vl10",
                 "locomotive_electric_br_e69",
                 "locomotive_electric_minetrain",
                 "locomotive_electric_high_speed_zeroed",
                 "locomotive_electric_tram",
                 "locomotive_electric_tram_ny" -> TCSounds.VL10_IDLE.get();
            default -> TCSounds.CHME3_IDLE.get();
        };
    }

    @Nullable
    private SoundEvent runSound() {
        return switch (variantId()) {
            case "tracks_builder" -> null;
            case "locomotive_diesel_cd742" -> TCSounds.DIESEL_742_MOTOR.get();
            case "locomotive_electric_vl10",
                 "locomotive_electric_br_e69",
                 "locomotive_electric_minetrain",
                 "locomotive_electric_high_speed_zeroed",
                 "locomotive_electric_tram",
                 "locomotive_electric_tram_ny" -> TCSounds.VL10_RUN.get();
            default -> TCSounds.CHME3_RUN.get();
        };
    }

    private int soundInterval() {
        return isElectric() ? 50 : 40;
    }

    private float soundVolume() {
        return isElectric() ? 0.60F : 0.65F;
    }

    @Nullable
    private SoundEvent hornSound() {
        return switch (variantId()) {
            case "tracks_builder",
                 "locomotive_diesel_kof",
                 "locomotive_diesel_v60" -> TCSounds.B7_HORN_DB_KLEINLOK.get();

            case "locomotive_diesel_cd742" -> TCSounds.DIESEL_742_HORN.get();
            case "locomotive_diesel_chme3" -> TCSounds.CHME3_HORN.get();

            case "locomotive_diesel_gp7" -> TCSounds.B7_HORN_LESLIE_S3L.get();
            case "locomotive_diesel_sd40" -> TCSounds.B7_HORN_NATHAN_P5.get();
            case "locomotive_diesel_sd70" -> TCSounds.B7_HORN_AIRCHIME_K5LA.get();
            case "locomotive_diesel_shunter" -> TCSounds.BRITISH_TWO_TONE.get();

            case "locomotive_electric_vl10" -> TCSounds.VL10_HORN.get();
            case "locomotive_electric_br_e69" -> TCSounds.B7_HORN_DB_KLEINLOK.get();
            case "locomotive_electric_minetrain",
                 "locomotive_electric_tram" -> TCSounds.TRAM_HORN.get();
            case "locomotive_electric_high_speed_zeroed" -> TCSounds.HIGH_SPEED_HORN.get();
            case "locomotive_electric_tram_ny" -> TCSounds.SUBWAY_HORN.get();

            default -> TCSounds.SHUNTER_HORN.get();
        };
    }

    private float hornVolume() {
        return switch (variantId()) {
            case "locomotive_electric_tram", "locomotive_electric_tram_ny" -> 1.0F;
            default -> 0.8F;
        };
    }

    public void playAutomaticHorn() {
        if (tickCount - poweredWhistleTick < 8) return;

        SoundEvent horn = hornSound();
        if (horn == null) return;

        poweredWhistleTick = tickCount;

        if (level() instanceof ServerLevel server) {
            server.playSound(
                    null,
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    horn,
                    SoundSource.NEUTRAL,
                    hornVolume(),
                    1.0F);
        }
    }

    @Override
    public void playWhistle(ServerPlayer player) {
        if (tickCount - poweredWhistleTick < 8) return;

        SoundEvent horn = hornSound();
        if (horn == null) return;

        poweredWhistleTick = tickCount;

        if (level() instanceof ServerLevel server) {
            server.playSound(
                    null,
                    getX(),
                    getY() + 1.0D,
                    getZ(),
                    horn,
                    SoundSource.NEUTRAL,
                    hornVolume(),
                    1.0F);
        }
    }
    // r1f1 measured renderer lift.
    // Values are the exact amount needed to move the recovered model's rendered
    // minimum Y to the powered entity plane. This changes rendering only; rail
    // motion/path physics remain untouched.
    private double poweredBaseModelLift() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 0.4400D;
            case "locomotive_diesel_cd742" -> 0.4500D;
            case "locomotive_diesel_chme3" -> 0.4700D;
            case "locomotive_diesel_gp7" -> 0.4700D;
            case "locomotive_diesel_sd40" -> 0.4700D;
            case "locomotive_diesel_sd70" -> 0.4400D;
            case "locomotive_diesel_shunter" -> 0.4200D;
            case "locomotive_diesel_v60" -> 0.4400D;
            case "locomotive_electric_vl10" -> 0.4700D;
            case "locomotive_electric_br_e69" -> 0.4200D;
            case "locomotive_electric_minetrain" -> 0.4700D;
            case "locomotive_electric_high_speed_zeroed" -> 0.4400D;
            case "locomotive_electric_tram" -> 0.4400D;
            case "locomotive_electric_tram_ny" -> 0.4400D;
            case "tracks_builder" -> 0.4365D;
            default -> 0.0000D;
        };
    }

    public double getPoweredModelLift() {
        return poweredBaseModelLift() + poweredHeightAdjustment();
    }

    // Cab positions are model-specific. Longitudinal values locate the driver's
    // cab along the train axis. The Y values preserve the prior per-model cab
    // calibration relative to the recovered model while adding the measured
    // r1f1 lift delta so the rider follows the model upward instead of remaining
    // below the rails.

    // ---------------------------------------------------------------------
    // r1f2 temporary live calibration.
    //
    // Sneak + EMPTY HAND on a powered vehicle:
    //   Pin the DRIVER'S FEET to the exact point clicked on the entity.
    //
    // Sneak + STICK:
    //   Pin the seat to the player's current feet position relative to the
    //   locomotive. This is useful when the visual cab lies outside the small
    //   entity hitbox: stand/fly where the driver's feet should be, then click.
    //
    // Sneak + PAPER:
    //   Clear this entity's seat pin and return to inherited stable seating.
    //
    // Sneak + IRON NUGGET:
    //   Raise only this powered model +0.05 block for live visual calibration.
    //
    // Sneak + COAL:
    //   Lower only this powered model -0.05 block.
    //
    // Sneak + GOLD NUGGET:
    //   Reset this entity's extra model-height adjustment to 0.
    //
    // Nothing is consumed. Results are written to latest.log as
    // [TC-SEAT-PIN] and [TC-HEIGHT-PIN] so the approved values can be baked
    // per variant afterward.
    // ---------------------------------------------------------------------

    private boolean hasPoweredSeatPin() {
        if (level().isClientSide) {
            return this.entityData.get(DATA_CALIBRATION_SEAT_PIN)
                    || hasBakedPoweredSeat();
        }
        return getPersistentData().getBoolean("TafariPoweredSeatPin")
                || hasBakedPoweredSeat();
    }

    private double poweredSeatPinLong() {
        if (level().isClientSide) {
            if (this.entityData.get(DATA_CALIBRATION_SEAT_PIN)) {
                return this.entityData.get(DATA_CALIBRATION_SEAT_LONG);
            }
            return bakedSeatLong();
        }
        if (getPersistentData().getBoolean("TafariPoweredSeatPin")) {
            return getPersistentData().getDouble("TafariPoweredSeatLong");
        }
        return bakedSeatLong();
    }

    private double poweredSeatPinFootY() {
        if (level().isClientSide) {
            if (this.entityData.get(DATA_CALIBRATION_SEAT_PIN)) {
                return this.entityData.get(DATA_CALIBRATION_SEAT_Y);
            }
            return bakedSeatFootY();
        }
        if (getPersistentData().getBoolean("TafariPoweredSeatPin")) {
            return getPersistentData().getDouble("TafariPoweredSeatFootY");
        }
        return bakedSeatFootY();
    }

    private double poweredSeatPinLateral() {
        if (level().isClientSide) {
            if (this.entityData.get(DATA_CALIBRATION_SEAT_PIN)) {
                return this.entityData.get(DATA_CALIBRATION_SEAT_SIDE);
            }
            return bakedSeatLateral();
        }
        if (getPersistentData().getBoolean("TafariPoweredSeatPin")) {
            return getPersistentData().getDouble("TafariPoweredSeatLateral");
        }
        return bakedSeatLateral();
    }

    private double poweredHeightAdjustment() {
        if (level().isClientSide) {
            if (tcClientCalibrationHeightOverrideValid) {
                return tcClientCalibrationHeightOverride;
            }
            return this.entityData.get(DATA_CALIBRATION_LIFT);
        }

        if (getPersistentData().contains("TafariPoweredHeightAdj")) {
            return getPersistentData().getDouble("TafariPoweredHeightAdj");
        }
        return bakedPoweredHeightAdjustment();
    }

    private void setPoweredHeightAdjustment(double adjustment) {
        double clamped = net.minecraft.util.Mth.clamp(adjustment, -3.0D, 3.0D);
        getPersistentData().putDouble("TafariPoweredHeightAdj", clamped);

        if (!level().isClientSide) {
            this.entityData.set(DATA_CALIBRATION_LIFT, (float)clamped);
        }
    }

    private void setPoweredSeatPinFromRelativePoint(double dx, double dy, double dz,
                                                     net.minecraft.world.entity.player.Player player,
                                                     String mode) {
        // Use the exact visible-body facing branch and seed the continuity
        // tracker at the instant the player chooses the seat.
        double facingYaw = net.minecraft.util.Mth.wrapDegrees(
                getRenderTrainFacingYaw(1.0F));
        double yaw = Math.toRadians(facingYaw);
        double frontX = Math.sin(yaw);
        double frontZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double longitudinal = dx * frontX + dz * frontZ;
        double lateral = dx * rightX + dz * rightZ;

        getPersistentData().putBoolean("TafariPoweredSeatPin", true);
        getPersistentData().putDouble("TafariPoweredSeatLong", longitudinal);
        getPersistentData().putDouble("TafariPoweredSeatFootY", dy);
        getPersistentData().putDouble("TafariPoweredSeatLateral", lateral);
        getPersistentData().putDouble("TafariPoweredSeatLastYaw", facingYaw);

        if (!level().isClientSide) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftPoweredSeatPin").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-SEAT-PIN] variant=%s mode=%s long=%.4f footY=%.4f lateral=%.4f relative=(%.4f,%.4f,%.4f) trainYaw=%.2f renderYaw=%.2f",
                            variantId(), mode, longitudinal, dy, lateral,
                            dx, dy, dz, getTrainFacingYaw(), facingYaw));

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "Seat pinned: %s  long %.2f  Y %.2f  side %.2f",
                                    variantId(), longitudinal, dy, lateral)),
                    false);
        }
    }

    private void setPoweredSeatPinFromPlayer(net.minecraft.world.entity.player.Player player) {
        setPoweredSeatPinFromRelativePoint(
                player.getX() - getX(),
                player.getY() - getY(),
                player.getZ() - getZ(),
                player,
                "PLAYER_FEET");
    }

    @Override
    public net.minecraft.world.InteractionResult interactAt(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.Vec3 hitPos,
            net.minecraft.world.InteractionHand hand) {

        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);

            // Exact click-point seat pin when the model is inside the real hitbox.
            if (held.isEmpty()) {
                                // TC_BATCH8_R4A4F_BUILDER_INTERACTAT_DELEGATE
            // Builder click behavior is centralized in interact():
            // normal = GUI, Shift = START/STOP.
            if (isBuilder()) {
                return interact(player, hand);
            }
setPoweredSeatPinFromRelativePoint(hitPos.x, hitPos.y, hitPos.z, player, "CLICK");
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }

            // Player-position seat pin. Remote air-click version is provided by
            // PoweredCalibrationEvents for models whose visual cab is outside
            // the entity hitbox.
            if (held.is(net.minecraft.world.item.Items.STICK)) {
                tcRemotePinSeatFromPlayer(player);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }

            // Seat vertical fine tune.
            if (held.is(net.minecraft.world.item.Items.FEATHER)) {
                tcRemoteAdjustSeatY(player, +0.05D);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(net.minecraft.world.item.Items.FLINT)) {
                tcRemoteAdjustSeatY(player, -0.05D);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }

            if (held.is(net.minecraft.world.item.Items.PAPER)) {
                tcRemoteClearSeat(player);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }

            // Model visual height fine tune.
            if (held.is(net.minecraft.world.item.Items.IRON_NUGGET)) {
                tcRemoteAdjustHeight(player, +0.05D);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(net.minecraft.world.item.Items.COAL)) {
                tcRemoteAdjustHeight(player, -0.05D);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (held.is(net.minecraft.world.item.Items.GOLD_NUGGET)) {
                tcRemoteResetHeight(player);
                return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        return super.interactAt(player, hitPos, hand);
    }
    private void syncPoweredCalibrationState() {
        if (level().isClientSide) return;

        double heightDouble = getPersistentData().contains("TafariPoweredHeightAdj")
                ? getPersistentData().getDouble("TafariPoweredHeightAdj")
                : bakedPoweredHeightAdjustment();
        float heightValue = (float)heightDouble;

        if (Math.abs(this.entityData.get(DATA_CALIBRATION_LIFT) - heightValue) > 0.0001F) {
            this.entityData.set(DATA_CALIBRATION_LIFT, heightValue);
        }

        if (tickCount % 100 == 0 && Math.abs(heightDouble) > 0.0001D) {
            tcPushHeightCalibrationToClient();
        }

        boolean persistentSeat =
                getPersistentData().getBoolean("TafariPoweredSeatPin");
        boolean seatPin = persistentSeat || hasBakedPoweredSeat();

        if (this.entityData.get(DATA_CALIBRATION_SEAT_PIN) != seatPin) {
            this.entityData.set(DATA_CALIBRATION_SEAT_PIN, seatPin);
        }

        float seatLong = seatPin
                ? (float)(persistentSeat
                    ? getPersistentData().getDouble("TafariPoweredSeatLong")
                    : bakedSeatLong())
                : 0.0F;
        float seatY = seatPin
                ? (float)(persistentSeat
                    ? getPersistentData().getDouble("TafariPoweredSeatFootY")
                    : bakedSeatFootY())
                : 0.0F;
        float seatSide = seatPin
                ? (float)(persistentSeat
                    ? getPersistentData().getDouble("TafariPoweredSeatLateral")
                    : bakedSeatLateral())
                : 0.0F;

        if (Math.abs(this.entityData.get(DATA_CALIBRATION_SEAT_LONG) - seatLong) > 0.0001F) {
            this.entityData.set(DATA_CALIBRATION_SEAT_LONG, seatLong);
        }
        if (Math.abs(this.entityData.get(DATA_CALIBRATION_SEAT_Y) - seatY) > 0.0001F) {
            this.entityData.set(DATA_CALIBRATION_SEAT_Y, seatY);
        }
        if (Math.abs(this.entityData.get(DATA_CALIBRATION_SEAT_SIDE) - seatSide) > 0.0001F) {
            this.entityData.set(DATA_CALIBRATION_SEAT_SIDE, seatSide);
        }
    }




    public void tcRemotePinSeatFromPlayer(net.minecraft.world.entity.player.Player player) {
        setPoweredSeatPinFromPlayer(player);
    }

    public void tcRemoteAdjustSeatY(net.minecraft.world.entity.player.Player player, double delta) {
        if (isBuilder()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Tracks Builder is excluded from train calibration."),
                    false);
            return;
        }

        if (!hasPoweredSeatPin()) {
            setPoweredSeatPinFromPlayer(player);
        }

        double before = poweredSeatPinFootY();
        double y = net.minecraft.util.Mth.clamp(before + delta, -5.0D, 5.0D);
        getPersistentData().putBoolean("TafariPoweredSeatPin", true);
        getPersistentData().putDouble("TafariPoweredSeatFootY", y);
        boolean changed = Math.abs(y - before) > 0.00001D;

        if (!level().isClientSide) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-SEAT-Y] variant=%s id=%d before=%.4f request=%+.4f after=%.4f long=%.4f lateral=%.4f changed=%s limit=%s",
                            variantId(), getId(), before, delta, y,
                            poweredSeatPinLong(), poweredSeatPinLateral(),
                            Boolean.toString(changed),
                            changed ? "NO" : "YES"));

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "[TC CAL] %s #%d SEAT Y  %.3f -> %.3f%s",
                                    variantId(), getId(), before, y,
                                    changed ? "" : "  [NO CHANGE / LIMIT]")),
                    false);
        }
    }

    public void tcRemoteClearSeat(net.minecraft.world.entity.player.Player player) {
        getPersistentData().remove("TafariPoweredSeatPin");
        getPersistentData().remove("TafariPoweredSeatLong");
        getPersistentData().remove("TafariPoweredSeatFootY");
        getPersistentData().remove("TafariPoweredSeatLateral");
        getPersistentData().remove("TafariPoweredSeatLastYaw");

        if (!level().isClientSide) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftPoweredSeatPin").info(
                    "[TC-SEAT-PIN] variant={} mode=RESET", variantId());
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Seat pin cleared for " + variantId() + "."),
                    false);
        }
    }

    public void tcRemoteAdjustHeight(net.minecraft.world.entity.player.Player player, double delta) {
        if (isBuilder()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Tracks Builder is excluded from train calibration."),
                    false);
            return;
        }

        double before = poweredHeightAdjustment();
        setPoweredHeightAdjustment(before + delta);
        double after = poweredHeightAdjustment();
        double synced = this.entityData.get(DATA_CALIBRATION_LIFT);
        double total = poweredBaseModelLift() + after;
        boolean changed = Math.abs(after - before) > 0.00001D;

        if (!level().isClientSide) {
            // Force the absolute value to the actual tracked client entity using
            // vanilla entity-event packets.
            tcPushHeightCalibrationToClient();

            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-HEIGHT] variant=%s id=%d before=%.4f request=%+.4f after=%.4f synced=%.4f base=%.4f total=%.4f changed=%s limit=%s transport=ENTITY_EVENT",
                            variantId(), getId(), before, delta, after, synced,
                            poweredBaseModelLift(), total,
                            Boolean.toString(changed),
                            changed ? "NO" : "YES"));

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "[TC CAL] %s #%d MODEL Y %.3f -> %.3f total %.3f",
                                    variantId(), getId(), before, after, total)),
                    false);
        }
    }

    public void tcRemoteResetHeight(net.minecraft.world.entity.player.Player player) {
        setPoweredHeightAdjustment(0.0D);

        if (!level().isClientSide) {
            tcPushHeightCalibrationToClient();

            org.slf4j.LoggerFactory.getLogger("TafariCraftPoweredHeightPin").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-HEIGHT-PIN] variant=%s action=RESET base=%.4f adj=0.0000 total=%.4f transport=ENTITY_EVENT",
                            variantId(), poweredBaseModelLift(), poweredBaseModelLift()));

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "%s height reset to %.3f",
                                    variantId(), poweredBaseModelLift())),
                    false);
        }
    }
    // r1f4: user-approved calibration values baked from the latest.log.
    // Only variants that produced an approved final calibration and were NOT
    // in the user's "still cannot calibrate" list are baked here.
    private boolean hasBakedPoweredSeat() {
        if (traincraft.config.RollingStockCalibrationConfig.hasSeat(variantId())) {
            return true;
        }
        return switch (variantId()) {
            case "locomotive_diesel_cd742",
                 "locomotive_diesel_v60",
                 "locomotive_electric_vl10",
                 "locomotive_electric_high_speed_zeroed",
                 "locomotive_electric_tram",
                 "locomotive_electric_tram_ny" -> true;
            default -> false;
        };
    }

    private double bakedSeatLong() {
        double fallback = switch (variantId()) {
            case "locomotive_diesel_cd742" -> 0.4900D;
            case "locomotive_diesel_v60" -> 0.1093D;
            case "locomotive_electric_vl10" -> 2.5130D;
            case "locomotive_electric_high_speed_zeroed" -> 2.3008D;
            case "locomotive_electric_tram" -> 0.0090D;
            case "locomotive_electric_tram_ny" -> 0.4900D;
            default -> 0.0D;
        };
        return traincraft.config.RollingStockCalibrationConfig.seatLong(
                variantId(), fallback);
    }

    private double bakedSeatFootY() {
        double fallback = switch (variantId()) {
            case "locomotive_diesel_cd742" -> -1.3491D;
            case "locomotive_diesel_v60" -> -0.1425D;
            case "locomotive_electric_vl10" -> -0.1000D;
            case "locomotive_electric_high_speed_zeroed" -> 0.1375D;
            case "locomotive_electric_tram" -> -0.7425D;
            case "locomotive_electric_tram_ny" -> -0.0239D;
            default -> 0.0D;
        };
        return traincraft.config.RollingStockCalibrationConfig.seatY(
                variantId(), fallback);
    }

    private double bakedSeatLateral() {
        double fallback = switch (variantId()) {
            case "locomotive_diesel_cd742" -> 0.0115D;
            case "locomotive_diesel_v60" -> -0.0189D;
            case "locomotive_electric_vl10" -> 0.0289D;
            case "locomotive_electric_high_speed_zeroed" -> 0.0568D;
            case "locomotive_electric_tram" -> -0.0173D;
            case "locomotive_electric_tram_ny" -> 0.0586D;
            default -> 0.0D;
        };
        return traincraft.config.RollingStockCalibrationConfig.seatSide(
                variantId(), fallback);
    }
    private boolean isBuilderRunning() {
        return isBuilder() && getPersistentData().getBoolean("TafariBuilderRunning");
    }

    private void setBuilderRunning(boolean running) {
        if (!isBuilder()) return;
        getPersistentData().putBoolean("TafariBuilderRunning", running);
        this.entityData.set(
                DATA_BUILDER_DRILL_ACTIVE,
                running && hasTractionPower());
        if (!running) {
            // TC_BATCH8_R4A5G25_BUILDER_AUTONOMOUS_TRACTION_HOLD
            this.setDriverControls(false, false, false);
            net.minecraft.world.phys.Vec3 motion = getDeltaMovement();
            setDeltaMovement(0.0D, motion.y, 0.0D);
        }
    }

    // TC_BATCH8_R4A5G25H5_BUILDER_RAISED_RAIL_PRESENCE
    // Builder-only fallback for accepted multipart curved slopes. The
    // continuous path can leave the minecart origin in the floored cell while
    // the real rail segment is one block above that floor position.
    private boolean hasBuilderAutonomousRailPresence() {
        if (this.isOnRails()) {
            return true;
        }

        net.minecraft.core.BlockPos center = this.blockPosition();
        net.minecraft.core.BlockPos[] probes = new net.minecraft.core.BlockPos[] {
                center,
                center.above()
        };

        for (net.minecraft.core.BlockPos probe : probes) {
            net.minecraft.world.level.block.state.BlockState state =
                    this.level().getBlockState(probe);
            if (net.minecraft.world.level.block.BaseRailBlock.isRail(state)) {
                if (tickCount % 20 == 0) {
                    org.slf4j.LoggerFactory.getLogger("TafariCraftBuilderLay").info(
                            "[TC-BUILDER-RAISED-RAIL-PRESENCE] id={} tick={} pos=({},{},{}) center={} acceptedProbe={} state={}",
                            getId(),
                            tickCount,
                            String.format(java.util.Locale.ROOT, "%.3f", getX()),
                            String.format(java.util.Locale.ROOT, "%.3f", getY()),
                            String.format(java.util.Locale.ROOT, "%.3f", getZ()),
                            center,
                            probe,
                            state);
                }
                return true;
            }
        }
        return false;
    }
    private void tickBuilderAutonomousDrive() {
        // TC_DEV36_R1K29E_DIG_BEFORE_AUTODRIVE
        // The helper is self-gating for Builder/running/power state.
        // Atomic preflight guarantees no terrain is changed unless all drops fit.
        if (!tcDev36ExcavateAhead()) {
            return;
        }
        if (!isBuilder()
                || !isBuilderRunning()) {
            return;
        }

        net.minecraft.world.phys.Vec3 motion =
                getDeltaMovement();

        if (!hasTractionPower()
                || !hasBuilderAutonomousRailPresence()) {
            // TC_BATCH8_R4A5G25_BUILDER_AUTONOMOUS_TRACTION_HOLD
            // Clear synthetic autonomous command whenever Builder cannot drive.
            this.setDriverControls(false, false, false);
            // TC_BATCH8_R4A5G12_MEDIUM_HANDOFF_WORLDSTATE_DEBUG
            if (hasTractionPower()
                    && tickCount % 4 == 0) {
                debugBuilderOffRailWorld();
            }

            setDeltaMovement(
                    0.0D,
                    motion.y,
                    0.0D);
            return;
        }

        // TC_BATCH8_R4A5G25_BUILDER_AUTONOMOUS_TRACTION_HOLD
        // Driverless construction is a real forward traction command.
        this.setDriverControls(true, false, false);

        final double targetSpeed =
                10.0D / 72.0D;

        double horizontal =
                Math.sqrt(
                        motion.x * motion.x
                                + motion.z * motion.z);

        double dirX;
        double dirZ;

        if (horizontal > 0.005D) {
            dirX = motion.x / horizontal;
            dirZ = motion.z / horizontal;
        } else {
            double yaw =
                    Math.toRadians(
                            getTrainFacingYaw());

            // TC_BATCH8_R4A5G11_AUTOSTART_FRONT_ROUTE_CONTIGUOUS
            // Use the entity's established model/front convention.
            // Model/placement forward, not the old opposite vector.
            dirX = Math.sin(yaw);
            dirZ = -Math.cos(yaw);

            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-AUTODRIVE-FORWARD] id={} tick={} trainYaw={} dir=({},{})",
                            getId(),
                            tickCount,
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f",
                                    getTrainFacingYaw()),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.5f",
                                    dirX),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.5f",
                                    dirZ));
        }

        double nextSpeed =
                targetSpeed;

        // TC_BATCH8_R4A5G25_BUILDER_AUTONOMOUS_TRACTION_HOLD
        // Restore the existing 10 km/h autonomous target immediately
        // after a rail/path stage hands us a heavily reduced magnitude.
        if (tickCount % 10 == 0
                && horizontal < targetSpeed * 0.75D) {
            org.slf4j.LoggerFactory
                    .getLogger("TafariCraftBuilderLay")
                    .info(
                            "[TC-BUILDER-AUTODRIVE-HOLD] id={} tick={} oldHorizontal={} target={} reverseRequested={} pos=({},{},{})",
                            getId(),
                            tickCount,
                            String.format(java.util.Locale.ROOT, "%.5f", horizontal),
                            String.format(java.util.Locale.ROOT, "%.5f", targetSpeed),
                            isReverseDriveRequested(),
                            String.format(java.util.Locale.ROOT, "%.3f", getX()),
                            String.format(java.util.Locale.ROOT, "%.3f", getY()),
                            String.format(java.util.Locale.ROOT, "%.3f", getZ()));
        }

        setDeltaMovement(
                dirX * nextSpeed,
                motion.y,
                dirZ * nextSpeed);

        // Coal is consumed by its own furnace timer. Diesel is only consumed
        // when there is no active coal burn, so the two stores stay separate.
        if (tickCount % 4 == 0
                && builderCoalBurnTicks <= 0
                && powerAmount > 0) {
            powerAmount =
                    Math.max(
                            0,
                            powerAmount - 1);
        }
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return !isBuilder() && super.canAddPassenger(passenger);
    }

    @Override
    public net.minecraft.world.InteractionResult interact(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {

        if (player.getItemInHand(hand).getItem()
                instanceof traincraft.item.ConnectorItem) {
            return super.interact(
                    player,
                    hand);
        }

        if (!isBuilder()) {
            return super.interact(
                    player,
                    hand);
        }

        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        // TC_BATCH8_R4A4F_BUILDER_NORMAL_RIGHT_CLICK_GUI
        // Normal right-click is now the expected GUI interaction.
        if (!player.isShiftKeyDown()) {
            if (!level().isClientSide
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.openMenu(this);
            }

            return net.minecraft.world.InteractionResult.sidedSuccess(
                    level().isClientSide);
        }

        // Preserve autonomous operation without requiring a new GUI button:
        // Shift + right-click now starts/stops the Builder crawl.
        if (!level().isClientSide) {
            boolean running =
                    !isBuilderRunning();

            setBuilderRunning(running);

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            running
                                    ? "Tracks Builder STARTED - autonomous 10 km/h crawl."
                                    : "Tracks Builder STOPPED."),
                    true);
        }

        return net.minecraft.world.InteractionResult.sidedSuccess(
                level().isClientSide);
    }
    public void tcSetCalibrationSelected(boolean selected) {
        if (isBuilder()) return;
        if (!level().isClientSide) {
            this.entityData.set(DATA_CALIBRATION_SELECTED, selected);
            this.setGlowingTag(selected);

            if (selected) {
                tcPushHeightCalibrationToClient();
            }
        }
    }

    public boolean tcIsCalibrationSelected() {
        return !isBuilder() && this.entityData.get(DATA_CALIBRATION_SELECTED);
    }

    public double tcCalibrationHeightAdjustment() {
        return poweredHeightAdjustment();
    }

    public float tcCalibrationSyncedHeightAdjustment() {
        return this.entityData.get(DATA_CALIBRATION_LIFT);
    }

    public double tcCalibrationBaseModelLift() {
        return poweredBaseModelLift();
    }

    public double tcCalibrationTotalModelLift() {
        return getPoweredModelLift();
    }

    public boolean tcCalibrationHasSeat() {
        return hasPoweredSeatPin();
    }

    public double tcCalibrationSeatLong() {
        return poweredSeatPinLong();
    }

    public double tcCalibrationSeatFootY() {
        return poweredSeatPinFootY();
    }

    public double tcCalibrationSeatLateral() {
        return poweredSeatPinLateral();
    }
    private double resolvedPoweredSeatFacingYaw() {
        // The powered renderer uses getRenderTrainFacingYaw(), while the old
        // seat code used raw getTrainFacingYaw(). On long stock the rail-owned
        // facing can choose the opposite 180-degree axis even though the visible
        // body has not changed ends. Preserve one continuous seat-facing branch.
        double current = net.minecraft.util.Mth.wrapDegrees(
                getRenderTrainFacingYaw(1.0F));

        if (!getPersistentData().contains("TafariPoweredSeatLastYaw")) {
            getPersistentData().putDouble("TafariPoweredSeatLastYaw", current);
            return current;
        }

        double last = net.minecraft.util.Mth.wrapDegrees(
                getPersistentData().getDouble("TafariPoweredSeatLastYaw"));
        double a = current;
        double b = net.minecraft.util.Mth.wrapDegrees((float)(current + 180.0D));

        double da = Math.abs(net.minecraft.util.Mth.wrapDegrees((float)(a - last)));
        double db = Math.abs(net.minecraft.util.Mth.wrapDegrees((float)(b - last)));
        double resolved = db < da ? b : a;

        if (!level().isClientSide) {
            getPersistentData().putDouble(
                    "TafariPoweredSeatLastYaw",
                    net.minecraft.util.Mth.wrapDegrees((float)resolved));
        }
        return resolved;
    }
    public void tcRemoteAdjustSeatLong(net.minecraft.world.entity.player.Player player, double delta) {
        if (isBuilder()) return;
        if (!hasPoweredSeatPin()) {
            setPoweredSeatPinFromPlayer(player);
        }

        double before = poweredSeatPinLong();
        double after = net.minecraft.util.Mth.clamp(before + delta, -12.0D, 12.0D);
        getPersistentData().putBoolean("TafariPoweredSeatPin", true);
        getPersistentData().putDouble("TafariPoweredSeatLong", after);

        if (!level().isClientSide) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-SEAT-LONG] variant=%s id=%d before=%.4f request=%+.4f after=%.4f renderYaw=%.2f",
                            variantId(), getId(), before, delta, after,
                            getRenderTrainFacingYaw(1.0F)));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "[TC CAL] %s #%d SEAT LONG %.3f -> %.3f",
                                    variantId(), getId(), before, after)),
                    false);
        }
    }

    public void tcRemoteAdjustSeatLateral(net.minecraft.world.entity.player.Player player, double delta) {
        if (isBuilder()) return;
        if (!hasPoweredSeatPin()) {
            setPoweredSeatPinFromPlayer(player);
        }

        double before = poweredSeatPinLateral();
        double after = net.minecraft.util.Mth.clamp(before + delta, -4.0D, 4.0D);
        getPersistentData().putBoolean("TafariPoweredSeatPin", true);
        getPersistentData().putDouble("TafariPoweredSeatLateral", after);

        if (!level().isClientSide) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-SEAT-SIDE] variant=%s id=%d before=%.4f request=%+.4f after=%.4f renderYaw=%.2f",
                            variantId(), getId(), before, delta, after,
                            getRenderTrainFacingYaw(1.0F)));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format(java.util.Locale.ROOT,
                                    "[TC CAL] %s #%d SEAT SIDE %.3f -> %.3f",
                                    variantId(), getId(), before, after)),
                    false);
        }
    }
    private double bakedPoweredHeightAdjustment() {
        double fallback = switch (variantId()) {
            case "locomotive_electric_vl10" -> 0.0000D;
            case "locomotive_electric_high_speed_zeroed" -> -0.0500D;
            default -> 0.0000D;
        };
        return traincraft.config.RollingStockCalibrationConfig.modelY(
                variantId(), fallback);
    }
    public boolean tcHasClientCalibrationHeightOverride() {
        return level().isClientSide && tcClientCalibrationHeightOverrideValid;
    }

    public double tcClientCalibrationHeightOverride() {
        return tcClientCalibrationHeightOverrideValid
                ? tcClientCalibrationHeightOverride
                : this.entityData.get(DATA_CALIBRATION_LIFT);
    }

    public void tcPushHeightCalibrationToClient() {
        if (level().isClientSide) return;

        double value = net.minecraft.util.Mth.clamp(
                poweredHeightAdjustment(), -3.0D, 3.0D);

        // First reset client override, then replay the absolute 0.05-step value.
        // This avoids cumulative drift and also restores persisted calibration
        // after relaunch or when a train is re-selected.
        level().broadcastEntityEvent(this, TC_CAL_HEIGHT_EVENT_RESET);

        int steps = (int)Math.round(Math.abs(value) / 0.05D);
        byte eventId = value >= 0.0D
                ? TC_CAL_HEIGHT_EVENT_UP
                : TC_CAL_HEIGHT_EVENT_DOWN;

        for (int i = 0; i < steps; i++) {
            level().broadcastEntityEvent(this, eventId);
        }

        org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                String.format(java.util.Locale.ROOT,
                        "[TC-CAL-HEIGHT-PUSH] variant=%s id=%d value=%.4f steps=%d event=%d",
                        variantId(), getId(), value, steps, (int)eventId));
    }

    @Override
    public void handleEntityEvent(byte eventId) {
        if (eventId == TC_CAL_HEIGHT_EVENT_RESET) {
            tcClientCalibrationHeightOverride = 0.0D;
            tcClientCalibrationHeightOverrideValid = true;

            if (level().isClientSide) {
                org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                        "[TC-CAL-HEIGHT-EVENT] side=CLIENT variant={} id={} event=RESET value=0.0000",
                        variantId(), getId());
            }
            return;
        }

        if (eventId == TC_CAL_HEIGHT_EVENT_UP
                || eventId == TC_CAL_HEIGHT_EVENT_DOWN) {

            double delta = eventId == TC_CAL_HEIGHT_EVENT_UP ? 0.05D : -0.05D;
            tcClientCalibrationHeightOverride =
                    net.minecraft.util.Mth.clamp(
                            tcClientCalibrationHeightOverride + delta,
                            -3.0D, 3.0D);
            tcClientCalibrationHeightOverrideValid = true;

            if (level().isClientSide) {
                org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationDebug").info(
                        String.format(java.util.Locale.ROOT,
                                "[TC-CAL-HEIGHT-EVENT] side=CLIENT variant=%s id=%d event=%s value=%.4f",
                                variantId(), getId(),
                                delta > 0.0D ? "UP" : "DOWN",
                                tcClientCalibrationHeightOverride));
            }
            return;
        }

        super.handleEntityEvent(eventId);
    }
    public void tcLockCalibrationToConfig(net.minecraft.world.entity.player.Player player) {
        if (isBuilder()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Tracks Builder is excluded from train calibration."),
                    false);
            return;
        }
        if (level().isClientSide) return;

        boolean seatEnabled = hasPoweredSeatPin();
        double longValue = seatEnabled ? poweredSeatPinLong() : 0.0D;
        double yValue = seatEnabled ? poweredSeatPinFootY() : 0.0D;
        double sideValue = seatEnabled ? poweredSeatPinLateral() : 0.0D;
        double heightValue = poweredHeightAdjustment();

        traincraft.config.RollingStockCalibrationConfig.save(
                variantId(),
                heightValue,
                seatEnabled,
                longValue,
                yValue,
                sideValue,
                true);

        // The config now becomes the canonical per-variant default.
        // Drop this entity's temporary calibration NBT and immediately reapply
        // from the config so the same train proves the persisted values work.
        getPersistentData().remove("TafariPoweredHeightAdj");
        getPersistentData().remove("TafariPoweredSeatPin");
        getPersistentData().remove("TafariPoweredSeatLong");
        getPersistentData().remove("TafariPoweredSeatFootY");
        getPersistentData().remove("TafariPoweredSeatLateral");
        getPersistentData().remove("TafariPoweredSeatLastYaw");

        syncPoweredCalibrationState();
        tcPushHeightCalibrationToClient();

        org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationConfig").info(
                String.format(java.util.Locale.ROOT,
                        "[TC-CAL-LOCK] variant=%s modelY=%.4f seat=%s long=%.4f y=%.4f side=%.4f file=%s",
                        variantId(), heightValue, Boolean.toString(seatEnabled),
                        longValue, yValue, sideValue,
                        traincraft.config.RollingStockCalibrationConfig.pathString()));

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        String.format(java.util.Locale.ROOT,
                                "[TC CAL LOCKED] %s  modelY %.3f  seat %.3f / %.3f / %.3f",
                                variantId(), heightValue,
                                longValue, yValue, sideValue)),
                false);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "[TC CAL CONFIG] " +
                        traincraft.config.RollingStockCalibrationConfig.pathString()),
                false);
    }

    public void tcApplyCalibrationFromConfig(net.minecraft.world.entity.player.Player player) {
        if (isBuilder()) return;
        if (level().isClientSide) return;

        if (!traincraft.config.RollingStockCalibrationConfig.hasEntry(variantId())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "[TC CAL] No config entry for " + variantId()),
                    false);
            return;
        }

        getPersistentData().remove("TafariPoweredHeightAdj");
        getPersistentData().remove("TafariPoweredSeatPin");
        getPersistentData().remove("TafariPoweredSeatLong");
        getPersistentData().remove("TafariPoweredSeatFootY");
        getPersistentData().remove("TafariPoweredSeatLateral");
        getPersistentData().remove("TafariPoweredSeatLastYaw");

        syncPoweredCalibrationState();
        tcPushHeightCalibrationToClient();

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "[TC CAL] Applied config defaults to " + variantId()),
                false);
    }
        // ======================================================================
    // DEV36 r1k29e -- classic Builder excavation
    // ======================================================================

    // ======================================================================
    // DEV36 r1k29r -- MANUAL TUNNEL
    //
    // Straight-ahead tunnel borer:
    //   accepted r1k29g front cutter / rotor
    //   -> accepted BuilderInfrastructureSystem.prepare(...)
    //      using the GUI's L3/L2/L1/C/R1/R2/R3 + walls + roof filters
    //   -> one Normal Track
    //   -> autonomous forward crawl continues
    //
    // Empty optional roadbed/wall/roof filters remain empty/unused exactly as
    // they do in Blueprint mode. FOUNDATION_CENTER is still required because
    // it is the accepted infrastructure enable/safety anchor.
    //
    // Blueprint route/anchor/rotation/progress are intentionally untouched.
    // ======================================================================

    private void tcDev36ManualTunnelStep() {
        if (!isBuilderManualTunnelMode()
                || !isBuilderRunning()
                || !hasTractionPower()
                || !(level()
                        instanceof ServerLevel server)) {
            return;
        }

        tcDev36ManualTunnelWarningLatched =
                false;

        // TC_DEV36_R1K29R_MANUAL_CUT_BEFORE_BUILD
        // Deterministic work order: cutter/collection always happens before
        // roadbed/wall/roof/track placement, regardless of tick ordering.
        if (!tcDev36ExcavateAhead()) {
            return;
        }

        BlockPos railPos =
                builderForwardBlueprintAnchor();

        BlockState railState =
                server.getBlockState(
                        railPos);

        if (tcDev36ManualIsTrack(
                railState)) {
            tcDev36SetBuilderWorkStatus(
                    "MANUAL TUNNEL");
            return;
        }

        if (railState.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                || railState.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
            tcDev36ManualTunnelStop(server, "TRACK BUILDER PROTECTED BLOCK",
                    "BEDROCK/OBSIDIAN", "MANUAL BLOCKED");
            return;
        }

        if (!railState.isAir()
                && !railState.canBeReplaced()) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER BLOCKED",
                    "CLEAR BLOCK AHEAD",
                    "MANUAL BLOCKED");
            return;
        }

        ItemStack centerTemplate =
                builderStructurePatterns
                        .getStackInSlot(
                                traincraft.builder.BuilderInfrastructureSystem
                                        .FOUNDATION_CENTER);

        if (!(centerTemplate.getItem()
                instanceof net.minecraft.world.item.BlockItem)) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER NEEDS MATERIAL",
                    "SET CENTER SUPPORT",
                    "NEED FOUNDATION CENTER");
            return;
        }

        net.minecraft.world.item.Item normalTrackItem =
                traincraft.registry.TCBlocks.TRACK_NORMAL
                        .get()
                        .asItem();

        int trackSlot =
                tcDev36ManualFindMaterial(
                        normalTrackItem);

        if (trackSlot < 0) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER NEEDS MATERIAL",
                    "ADD NORMAL TRACK",
                    "NEED NORMAL TRACK");
            return;
        }

        ItemStack railStack =
                powerInventory
                        .getStackInSlot(
                                trackSlot);

        ItemStack plannedTrackStack =
                railStack.copy();

        plannedTrackStack.setCount(
                1);

        traincraft.item.TrackBlueprintItem
                .applyUnifiedDirection(
                        plannedTrackStack,
                        false);

        // TC_DEV36_R1K29R_MANUAL_FULL_INFRASTRUCTURE
        // Reuse the exact accepted Blueprint roadbed/tunnel authority. This is
        // what makes the existing GUI filters work in MANUAL TUNNEL too.
        String infrastructureProblem =
                traincraft.builder.BuilderInfrastructureSystem
                        .prepare(
                                server,
                                this,
                                plannedTrackStack,
                                railPos,
                                getTrainFacingYaw());

        if (!infrastructureProblem.isEmpty()) {
            tcDev36ManualInfrastructureStop(
                    server,
                    infrastructureProblem);
            return;
        }

        boolean railPlaced =
                tryLayBuilderBlueprintTrackItem(
                        server,
                        railPos,
                        railStack,
                        getTrainFacingYaw(),
                        false);

        if (!railPlaced) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER BLOCKED",
                    "NORMAL TRACK FAILED",
                    "MANUAL TRACK FAILED");
            return;
        }

        consumeOne(
                trackSlot);

        tcDev36SetBuilderWorkStatus(
                "MANUAL TUNNEL");

        server.sendParticles(
                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                railPos.getX() + 0.5D,
                railPos.getY() + 0.3D,
                railPos.getZ() + 0.5D,
                3,
                0.12D,
                0.05D,
                0.12D,
                0.0D);

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29R][MANUAL] event=PLACE entity={} tick={} rail={} infraMode={} trackSlot={} blueprintProgress={}",
                getId(),
                tickCount,
                railPos,
                traincraft.builder.BuilderInfrastructureSystem.modeName(this),
                trackSlot,
                builderBlueprintProgress);
    }

    private void tcDev36ManualInfrastructureStop(
            ServerLevel server,
            String problem) {

        String upper =
                problem == null
                        ? ""
                        : problem.toUpperCase(
                                java.util.Locale.ROOT);

        if (upper.contains(
                "INVENTORY FULL")) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER INVENTORY FULL",
                    "CLEAR INVENTORY TO CONTINUE",
                    "INVENTORY FULL");
            return;
        }

        if (upper.contains(
                "FOUNDATION CENTER")) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER NEEDS MATERIAL",
                    "SET CENTER SUPPORT",
                    problem);
            return;
        }

        if (upper.startsWith(
                        "NEED ")
                || upper.contains(
                        "NEED MATERIAL")) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER NEEDS MATERIAL",
                    "CHECK ROADBED / TUNNEL MATERIALS",
                    problem);
            return;
        }

        if (upper.startsWith(
                        "BLOCKED")
                || upper.contains(
                        "BLOCKED")) {
            tcDev36ManualTunnelStop(
                    server,
                    "TRACK BUILDER BLOCKED",
                    "CLEAR TUNNEL PATH",
                    problem);
            return;
        }

        tcDev36ManualTunnelStop(
                server,
                "TRACK BUILDER STOPPED",
                "CHECK ROADBED / TUNNEL SETUP",
                problem);
    }

    private boolean tcDev36ManualIsTrack(
            BlockState state) {

        if (state.is(
                net.minecraft.tags.BlockTags.RAILS)) {
            return true;
        }

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getKey(
                                state.getBlock());

        return key != null
                && "traincraft".equals(
                        key.getNamespace())
                && key.getPath()
                        .startsWith(
                                "track_");
    }

    private int tcDev36ManualFindMaterial(
            net.minecraft.world.item.Item wanted) {

        if (wanted == null
                || wanted == net.minecraft.world.item.Items.AIR) {
            return -1;
        }

        for (int slot = 1;
             slot < powerInventory.getSlots();
             slot++) {

            // Dedicated Blueprint slot is never manual-tunnel material.
            if (slot == 52) {
                continue;
            }

            ItemStack stack =
                    powerInventory
                            .getStackInSlot(
                                    slot);

            if (!stack.isEmpty()
                    && stack.getItem()
                            == wanted) {
                return slot;
            }
        }

        return -1;
    }

    private void tcDev36ManualTunnelStop(
            ServerLevel server,
            String titleText,
            String subtitleText,
            String status) {

        tcDev36SetBuilderWorkStatus(
                status);

        setBuilderRunning(false);

        net.minecraft.world.phys.Vec3 motion =
                getDeltaMovement();

        setDeltaMovement(
                0.0D,
                motion.y,
                0.0D);

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29R][MANUAL] event=STOP entity={} tick={} title={} subtitle={} status={} blueprintProgress={}",
                getId(),
                tickCount,
                titleText,
                subtitleText,
                status,
                builderBlueprintProgress);

        if (tcDev36ManualTunnelWarningLatched) {
            return;
        }

        tcDev36ManualTunnelWarningLatched =
                true;

        net.minecraft.network.chat.Component title =
                net.minecraft.network.chat.Component
                        .literal(
                                titleText)
                        .withStyle(
                                net.minecraft.ChatFormatting.RED,
                                net.minecraft.ChatFormatting.BOLD);

        net.minecraft.network.chat.Component subtitle =
                net.minecraft.network.chat.Component
                        .literal(
                                subtitleText)
                        .withStyle(
                                net.minecraft.ChatFormatting.GOLD);

        for (net.minecraft.server.level.ServerPlayer player
                : server.players()) {

            if (player.distanceToSqr(this)
                    > 4096.0D) {
                continue;
            }

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                            5,
                            50,
                            10));

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            title));

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                            subtitle));
        }
    }
    private void tcDev36SyncBuilderDrillState() {
        if (!isBuilder() || level().isClientSide) {
            return;
        }

        boolean active =
                isBuilderRunning() && hasTractionPower();

        boolean before =
                this.entityData.get(
                        DATA_BUILDER_DRILL_ACTIVE);

        if (before != active) {
            this.entityData.set(
                    DATA_BUILDER_DRILL_ACTIVE,
                    active);

            traincraft.Traincraft.LOGGER.info(
                    "[TC-DEV36-R1K29E][DIG] event=DRILL_SYNC entity={} tick={} active={} traction={} coalTicks={} dieselPower={} speed={}",
                    getId(),
                    tickCount,
                    active,
                    hasTractionPower(),
                    getBuilderCoalBurnTicks(),
                    powerAmount,
                    getDeltaMovement().horizontalDistance());
        }
    }

    /**
     * Clears the classic Builder cutter face immediately ahead of the machine.
     *
     * The old Traincraft Builder dug a three-wide / three-high face. This port
     * keeps that classic envelope but performs a modern atomic loot preflight:
     * every drop from the whole face must fit in Blueprint Materials storage
     * before any block is changed.
     */
    private boolean tcDev36ExcavateAhead() {
        if (!isBuilder()
                || !isBuilderRunning()
                || !hasTractionPower()) {
            return true;
        }

        if (!(level() instanceof ServerLevel server)) {
            return true;
        }

        // TC_DEV36_R1K29G_COAL_TRACTION_DIG_RUNTIME
        if (tickCount % 20 == 0) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC-DEV36-R1K29G][DIG] event=ACTIVE entity={} tick={} running={} traction={} coalTicks={} dieselPower={} pos=({},{},{})",
                    getId(),
                    tickCount,
                    isBuilderRunning(),
                    hasTractionPower(),
                    getBuilderCoalBurnTicks(),
                    powerAmount,
                    String.format(java.util.Locale.ROOT, "%.3f", getX()),
                    String.format(java.util.Locale.ROOT, "%.3f", getY()),
                    String.format(java.util.Locale.ROOT, "%.3f", getZ()));
        }

        net.minecraft.world.phys.Vec3 motion =
                getDeltaMovement();

        double fx = motion.x;
        double fz = motion.z;
        double horizontal =
                Math.sqrt(fx * fx + fz * fz);

        if (horizontal > 0.005D) {
            fx /= horizontal;
            fz /= horizontal;
        } else {
            double yaw =
                    Math.toRadians(
                            getTrainFacingYaw());
            fx = Math.sin(yaw);
            fz = -Math.cos(yaw);
        }

        // Perpendicular vector for the three-wide cutter.
        double lx = -fz;
        double lz = fx;

        int baseY =
                net.minecraft.util.Mth.floor(
                        getY());

        java.util.LinkedHashSet<BlockPos> cells =
                new java.util.LinkedHashSet<>();

        // Two cells deep keeps the cutter ahead of the moving entity while
        // remaining tightly scoped to the classic 3x3 tunnel envelope.
        for (int depth = 0;
             depth < 2;
             depth++) {

            double distance =
                    1.35D + depth;

            double centerX =
                    getX() + fx * distance;
            double centerZ =
                    getZ() + fz * distance;

            for (int lateral = -1;
                 lateral <= 1;
                 lateral++) {

                double sampleX =
                        centerX
                                + lx * lateral;
                double sampleZ =
                        centerZ
                                + lz * lateral;

                for (int up = 0;
                     up <= 2;
                     up++) {

                    cells.add(
                            BlockPos.containing(
                                    sampleX,
                                    baseY + up,
                                    sampleZ));
                }
            }
        }

        java.util.LinkedHashMap<
                BlockPos,
                java.util.List<ItemStack>> work =
                new java.util.LinkedHashMap<>();

        net.minecraft.world.item.ItemStack cutter =
                new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.IRON_PICKAXE);

        for (BlockPos pos : cells) {
            BlockState state =
                    server.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            // DEV45: hard-stop the entire cutter face before any removal.
            // Obsidian is normally mineable but must never be excavated.
            if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                    || state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
                tcDev36StopForDigBlock(server, pos, state, "PROTECTED_BEDROCK_OBSIDIAN");
                return false;
            }

            // Match classic safety: do not vacuum liquids.
            if (!state.getFluidState().isEmpty()) {
                continue;
            }

            if (state.is(
                    net.minecraft.tags.BlockTags.RAILS)) {
                continue;
            }

            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(
                            state.getBlock());

            if (key != null
                    && "traincraft".equals(
                            key.getNamespace())
                    && key.getPath().startsWith(
                            "track_")) {
                continue;
            }

            // Containers/machines are not terrain and may hold inventory.
            if (server.getBlockEntity(pos) != null) {
                tcDev36StopForDigBlock(
                        server,
                        pos,
                        state,
                        "BLOCK_ENTITY");
                return false;
            }

            if (state.getDestroySpeed(
                            server,
                            pos)
                    < 0.0F) {
                tcDev36StopForDigBlock(
                        server,
                        pos,
                        state,
                        "UNBREAKABLE");
                return false;
            }

            java.util.List<ItemStack> drops =
                    net.minecraft.world.level.block.Block.getDrops(
                            state,
                            server,
                            pos,
                            null,
                            this,
                            cutter);

            java.util.ArrayList<ItemStack> copied =
                    new java.util.ArrayList<>();

            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    copied.add(
                            drop.copy());
                }
            }

            work.put(pos, copied);
        }

        if (work.isEmpty()) {
            tcDev36ExcavationInventoryFullLatched =
                    false;
            return true;
        }

        // Simulate ALL drops against a complete copy of the actual handler.
        // The simulation copy may mutate freely; world + real inventory remain
        // untouched until the entire cutter face is known to fit.
        ItemStackHandler simulated =
                new ItemStackHandler(
                        powerInventory.getSlots());

        for (int slot = 0;
             slot < powerInventory.getSlots();
             slot++) {
            simulated.setStackInSlot(
                    slot,
                    powerInventory
                            .getStackInSlot(slot)
                            .copy());
        }

        int totalDropStacks = 0;
        int totalDropItems = 0;

        for (java.util.List<ItemStack> drops
                : work.values()) {

            for (ItemStack drop : drops) {
                totalDropStacks++;
                totalDropItems +=
                        drop.getCount();

                ItemStack remainder =
                        tcDev36InsertExcavated(
                                simulated,
                                drop.copy());

                if (!remainder.isEmpty()) {
                    tcDev36StopInventoryFull(
                            server,
                            work.size(),
                            totalDropItems,
                            remainder);

                    return false;
                }
            }
        }

        tcDev36ExcavationInventoryFullLatched =
                false;

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29E][DIG] event=SCAN_OK entity={} tick={} cells={} terrainBlocks={} dropStacks={} dropItems={} pos=({},{},{})",
                getId(),
                tickCount,
                cells.size(),
                work.size(),
                totalDropStacks,
                totalDropItems,
                String.format(
                        java.util.Locale.ROOT,
                        "%.3f",
                        getX()),
                String.format(
                        java.util.Locale.ROOT,
                        "%.3f",
                        getY()),
                String.format(
                        java.util.Locale.ROOT,
                        "%.3f",
                        getZ()));

        // Commit only after the full-face storage simulation has succeeded.
        for (java.util.Map.Entry<
                BlockPos,
                java.util.List<ItemStack>> entry
                : work.entrySet()) {

            BlockPos pos =
                    entry.getKey();

            BlockState before =
                    server.getBlockState(pos);

            if (before.isAir()) {
                continue;
            }

            boolean removed =
                    server.setBlock(
                            pos,
                            net.minecraft.world.level.block.Blocks.AIR
                                    .defaultBlockState(),
                            3);

            if (!removed) {
                tcDev36StopForDigBlock(
                        server,
                        pos,
                        before,
                        "SET_BLOCK_FAILED");
                return false;
            }

            server.levelEvent(
                    2001,
                    pos,
                    net.minecraft.world.level.block.Block.getId(
                            before));

            for (ItemStack drop : entry.getValue()) {
                ItemStack remainder =
                        tcDev36InsertExcavated(
                                powerInventory,
                                drop.copy());

                if (!remainder.isEmpty()) {
                    // This is deliberately a hard invariant. The exact same
                    // deterministic insertion order just passed on the copy.
                    traincraft.Traincraft.LOGGER.error(
                            "[TC-DEV36-R1K29E][DIG] event=ATOMIC_INSERT_INVARIANT_FAILED entity={} tick={} pos={} remainder={}",
                            getId(),
                            tickCount,
                            pos,
                            remainder);

                    tcDev36SetBuilderWorkStatus(
                            "DIG STORAGE ERROR");

                    setBuilderRunning(false);
                    return false;
                }
            }

            net.minecraft.resources.ResourceLocation blockKey =
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(
                            before.getBlock());

            traincraft.Traincraft.LOGGER.info(
                    "[TC-DEV36-R1K29E][DIG] event=EXCAVATE entity={} tick={} pos={} block={} drops={}",
                    getId(),
                    tickCount,
                    pos,
                    blockKey,
                    entry.getValue());
        }

        return true;
    }

    /**
     * Mining output is intentionally restricted to the accepted r1k14+
     * Blueprint Materials rack: slots 20..51 and 54..77.
     * Fuel slot 0, Blueprint slot 52, and Supplies slots 1..19 + 53
     * are never touched by excavation.
     */
    private ItemStack tcDev36InsertExcavated(
            ItemStackHandler handler,
            ItemStack incoming) {

        ItemStack remainder =
                incoming;

        for (int slot = 20;
             slot <= 51
                     && slot < handler.getSlots();
             slot++) {

            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }

            remainder =
                    handler.insertItem(
                            slot,
                            remainder,
                            false);
        }

        for (int slot = 54;
             slot <= 77
                     && slot < handler.getSlots();
             slot++) {

            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }

            remainder =
                    handler.insertItem(
                            slot,
                            remainder,
                            false);
        }

        return remainder;
    }

    private void tcDev36StopInventoryFull(
            ServerLevel server,
            int terrainBlocks,
            int simulatedDropItems,
            ItemStack remainder) {

        tcDev36SetBuilderWorkStatus(
                "INVENTORY FULL");

        setBuilderRunning(false);

        net.minecraft.world.phys.Vec3 motion =
                getDeltaMovement();

        setDeltaMovement(
                0.0D,
                motion.y,
                0.0D);

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29E][DIG] event=INVENTORY_FULL entity={} tick={} terrainBlocks={} simulatedDropItems={} remainder={} trackProgress={}",
                getId(),
                tickCount,
                terrainBlocks,
                simulatedDropItems,
                remainder,
                builderBlueprintProgress);

        if (tcDev36ExcavationInventoryFullLatched) {
            return;
        }

        tcDev36ExcavationInventoryFullLatched =
                true;

        net.minecraft.network.chat.Component title =
                net.minecraft.network.chat.Component
                        .literal(
                                "TRACK BUILDER INVENTORY FULL")
                        .withStyle(
                                net.minecraft.ChatFormatting.RED,
                                net.minecraft.ChatFormatting.BOLD);

        net.minecraft.network.chat.Component subtitle =
                net.minecraft.network.chat.Component
                        .literal(
                                "CLEAR INVENTORY TO CONTINUE")
                        .withStyle(
                                net.minecraft.ChatFormatting.GOLD);

        for (net.minecraft.server.level.ServerPlayer player
                : server.players()) {

            if (player.distanceToSqr(this)
                    > 4096.0D) {
                continue;
            }

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                            5,
                            50,
                            10));

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            title));

            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                            subtitle));
        }
    }

    private void tcDev36StopForDigBlock(
            ServerLevel server,
            BlockPos pos,
            BlockState state,
            String reason) {

        tcDev36SetBuilderWorkStatus(
                "DIG BLOCKED "
                        + pos.getX()
                        + " "
                        + pos.getY()
                        + " "
                        + pos.getZ());

        setBuilderRunning(false);

        net.minecraft.world.phys.Vec3 motion =
                getDeltaMovement();

        setDeltaMovement(
                0.0D,
                motion.y,
                0.0D);

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(
                        state.getBlock());

        traincraft.Traincraft.LOGGER.info(
                "[TC-DEV36-R1K29E][DIG] event=BLOCKED entity={} tick={} reason={} pos={} block={}",
                getId(),
                tickCount,
                reason,
                pos,
                key);
    }

    private void tcDev36SetBuilderWorkStatus(
            String status) {

        ItemStack blueprint =
                powerInventory.getStackInSlot(52);

        int total =
                blueprint.getItem()
                                instanceof traincraft.item.TrackBlueprintItem
                        ? traincraft.item.TrackBlueprintItem.segmentCount(
                                blueprint)
                        : 0;

        syncBuilderBlueprint(
                total,
                status);
    }
@Override
    protected double getRealismBaseAccelerationMps2() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 0.66D;
            case "locomotive_diesel_cd742" -> 0.80D;
            case "locomotive_diesel_chme3" -> 0.66D;
            case "locomotive_diesel_gp7" -> 0.74D;
            case "locomotive_diesel_sd40", "locomotive_diesel_sd70" -> 0.80D;
            case "locomotive_diesel_shunter" -> 0.60D;
            case "locomotive_diesel_v60" -> 0.66D;
            case "locomotive_electric_vl10" -> 1.10D;
            case "locomotive_electric_br_e69" -> 0.90D;
            case "locomotive_electric_minetrain", "locomotive_electric_tram" -> 0.50D;
            case "locomotive_electric_high_speed_zeroed" -> 1.40D;
            case "locomotive_electric_tram_ny" -> 0.70D;
            case "tracks_builder" -> 0.45D;
            default -> super.getRealismBaseAccelerationMps2();
        };
    }

    private double maxKmh() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> 57D;
            case "locomotive_diesel_cd742", "locomotive_diesel_gp7", "locomotive_diesel_shunter" -> 70D;
            case "locomotive_diesel_chme3" -> 95D;
            case "locomotive_diesel_sd40", "locomotive_diesel_sd70", "locomotive_diesel_v60" -> 80D;
            case "locomotive_electric_vl10" -> 150D;
            case "locomotive_electric_br_e69" -> 50D;
            case "locomotive_electric_minetrain" -> 40D;
            case "locomotive_electric_high_speed_zeroed" -> 237D;
            case "locomotive_electric_tram" -> 55D;
            case "locomotive_electric_tram_ny" -> 65D;
            case "tracks_builder" -> 35D;
            default -> 45D;
        };
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() { return maxKmh()/72.0D; }
    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() { return Math.min(maxKmh(),70D)/72.0D; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.traincraft."+variantId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player player) {
        return new PoweredLocomotiveMenu(id, inv, this);
    }

    @Override
    protected Item getDropItem() {
        return switch (variantId()) {
            case "locomotive_diesel_kof" -> TCItems.LOCOMOTIVE_DIESEL_KOF.get();
            case "locomotive_diesel_cd742" -> TCItems.LOCOMOTIVE_DIESEL_CD742.get();
            case "locomotive_diesel_chme3" -> TCItems.LOCOMOTIVE_DIESEL_CHME3.get();
            case "locomotive_diesel_gp7" -> TCItems.LOCOMOTIVE_DIESEL_GP7.get();
            case "locomotive_diesel_sd40" -> TCItems.LOCOMOTIVE_DIESEL_SD40.get();
            case "locomotive_diesel_sd70" -> TCItems.LOCOMOTIVE_DIESEL_SD70.get();
            case "locomotive_diesel_shunter" -> TCItems.LOCOMOTIVE_DIESEL_SHUNTER.get();
            case "locomotive_diesel_v60" -> TCItems.LOCOMOTIVE_DIESEL_V60.get();
            case "locomotive_electric_vl10" -> TCItems.LOCOMOTIVE_ELECTRIC_VL10.get();
            case "locomotive_electric_br_e69" -> TCItems.LOCOMOTIVE_ELECTRIC_BR_E69.get();
            case "locomotive_electric_minetrain" -> TCItems.LOCOMOTIVE_ELECTRIC_MINETRAIN.get();
            case "locomotive_electric_high_speed_zeroed" -> TCItems.LOCOMOTIVE_ELECTRIC_HIGH_SPEED_ZEROED.get();
            case "locomotive_electric_tram" -> TCItems.LOCOMOTIVE_ELECTRIC_TRAM.get();
            case "locomotive_electric_tram_ny" -> TCItems.LOCOMOTIVE_ELECTRIC_TRAM_NY.get();
            case "tracks_builder" -> TCItems.TRACKS_BUILDER.get();
            default -> TCItems.LOCOMOTIVE_STEAM_SMALL.get();
        };
    }

    @Override
    public ItemStack getPickResult() { return new ItemStack(getDropItem()); }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LegacyPower", powerAmount);
        tag.putBoolean(
                "BuilderManualTunnel",
                isBuilderManualTunnelMode());
        tag.putBoolean("BuilderFuelSplitV2", true);
        tag.putInt("BuilderCoalBurn", builderCoalBurnTicks);
        tag.put("LegacyPowerInventory",powerInventory.serializeNBT());
        if (isBuilder()) tag.put("BuilderStructurePatterns", builderStructurePatterns.serializeNBT());
        tag.putInt("BuilderBlueprintProgress", builderBlueprintProgress);
        tag.putInt("BuilderBlueprintRotation", builderBlueprintRotationQuarter);
        tag.putString("BuilderBlueprintId", builderBlueprintId);
        tag.putString("BuilderBlueprintNeed", builderBlueprintNeed);
        if (builderBlueprintAnchor != null) tag.putLong("BuilderBlueprintAnchor", builderBlueprintAnchor.asLong());
        if (builderBlueprintLastPlacedAnchor != null) {
            tag.putLong(
                    "BuilderBlueprintLastPlacedAnchor",
                    builderBlueprintLastPlacedAnchor.asLong());
            tag.putFloat(
                    "BuilderBlueprintLastPlacedYaw",
                    builderBlueprintLastPlacedYaw);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        powerAmount=Math.max(0,Math.min(getPowerCapacity(),tag.getInt("LegacyPower")));
        if (isBuilder() && !tag.getBoolean("BuilderFuelSplitV2")) {
            powerAmount = 0;
        }
        builderCoalBurnTicks =
                isBuilder()
                        ? Math.max(0, tag.getInt("BuilderCoalBurn"))
                        : 0;
        if(tag.contains("LegacyPowerInventory")) {
            ItemStackHandler loadedInventory = new ItemStackHandler(1);
            loadedInventory.deserializeNBT(tag.getCompound("LegacyPowerInventory"));
            int copySlots = Math.min(powerInventory.getSlots(), loadedInventory.getSlots());
            for (int slot = 0; slot < copySlots; slot++) {
                powerInventory.setStackInSlot(slot, loadedInventory.getStackInSlot(slot).copy());
            }
        }
        builderBlueprintProgress = Math.max(0, tag.getInt("BuilderBlueprintProgress"));
        builderBlueprintRotationQuarter = Math.floorMod(tag.getInt("BuilderBlueprintRotation"), 4);
        builderBlueprintId = tag.getString("BuilderBlueprintId");
        builderBlueprintNeed = tag.getString("BuilderBlueprintNeed");
        builderBlueprintAnchor = tag.contains("BuilderBlueprintAnchor") ? BlockPos.of(tag.getLong("BuilderBlueprintAnchor")) : null;
        builderBlueprintLastPlacedAnchor =
                tag.contains("BuilderBlueprintLastPlacedAnchor")
                        ? BlockPos.of(
                                tag.getLong(
                                        "BuilderBlueprintLastPlacedAnchor"))
                        : null;
        builderBlueprintLastPlacedYaw =
                tag.contains("BuilderBlueprintLastPlacedYaw")
                        ? tag.getFloat(
                                "BuilderBlueprintLastPlacedYaw")
                        : 0.0F;
        entityData.set(DATA_BLUEPRINT_PROGRESS, builderBlueprintProgress);
        entityData.set(DATA_BLUEPRINT_TOTAL, 0);
        entityData.set(DATA_BLUEPRINT_NEED, builderBlueprintNeed);
                // TC_DEV36_R1K12_COAL_ONLY_BUILDER
        if (isBuilder()) {
            powerAmount = 0;
        }
entityData.set(DATA_POWER, powerAmount);
        entityData.set(
                DATA_BUILDER_MANUAL_TUNNEL,
                isBuilder()
                        && tag.getBoolean(
                                "BuilderManualTunnel"));
        entityData.set(DATA_BUILDER_COAL_BURN, builderCoalBurnTicks);
    
        // TC_DEV34_R1C1D_STRUCTURE_PATTERN_LOAD
        if (isBuilder() && tag.contains("BuilderStructurePatterns")) {
            builderStructurePatterns.deserializeNBT(
                    tag.getCompound("BuilderStructurePatterns"));
        }
}
}
