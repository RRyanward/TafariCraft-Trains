/*
 * Traincraft 1.20.1 Small Steam Locomotive.
 * Ported from the original 1.12.2 LocomotiveSteamSmall/LocomotiveSteam logic.
 * Step 7.2.1l retunes only the consist governor thresholds to the measured
 * coupler-nub spacing; propulsion, boiler, traction and braking remain unchanged.
 * Step 7.3.2c is DIAGNOSTIC ONLY: it records per-tick locomotive movement and
 * consist-governor decisions for reverse/curve tuning.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.steam.small;

import traincraft.Traincraft;
import traincraft.debug.TrainMovementDebug;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import traincraft.menu.SteamLocomotiveMenu;
import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.tender.SteamTender;
import traincraft.item.ConnectorItem;
import traincraft.registry.TCItems;
import traincraft.registry.TCSounds;

/** First powered Traincraft rolling-stock entity in the 1.20.1 port. */
public class SmallSteamLocomotive extends Minecart implements MenuProvider, CoupleableRollingStock {
    public static final int BURN_SLOT = 0;
    public static final int WATER_SLOT = 1;
    public static final int INVENTORY_SIZE = 11;
    public static final int WATER_CAPACITY_MB = 5000;
    public static final int STEAM_CAPACITY = 1000;

    private static final EntityDataAccessor<Integer> DATA_BURN_TIME = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_BURN_TIME = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WATER_AMOUNT = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STEAM_AMOUNT = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BOILER_TEMPERATURE_K = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HAND_BRAKE = SynchedEntityData.defineId(SmallSteamLocomotive.class, EntityDataSerializers.BOOLEAN);

    /** Original Traincraft maximum forward speed: 45 km/h = 0.625 blocks/tick. */
    public static final double MAX_SPEED_BLOCKS_PER_TICK = 0.625D;
    public static final double MAX_REVERSE_SPEED_BLOCKS_PER_TICK = 0.625D;
    public static final double ACCELERATION_METRES_PER_SECOND_SQ = 0.5D;
    public static final double BRAKE_METRES_PER_SECOND_SQ = 0.968D;

    /* Step 7.0.3 consist governor. The locomotive must not be able to run away
     * from a coupled tender while vanilla minecart slope/curve resolution is
     * temporarily slowing the follower. */
    /* Step 7.2.1j: the visible loco/tender coupler nubs meet around a larger
     * center-to-center gap than the original minecart-derived spacing. Start the
     * governor just beyond that normal gap so it does not fight the new rest point. */
    // Step 7.2.1l: first-tender working distance is now centered on the
    // measured/model-derived 2.10-block nub touch point, so the governor must
    // start beyond that normal gap instead of trimming the locomotive constantly.
    private static final double COUPLED_GOVERNOR_START_DISTANCE = 2.22D;
    private static final double COUPLED_GOVERNOR_FULL_DISTANCE = 2.90D;
    private static final double COUPLED_CURVE_GOVERNOR_START_DISTANCE = 1.48D;
    private static final double COUPLED_CURVE_GOVERNOR_FULL_DISTANCE = 2.15D;
    private static final double COUPLED_GOVERNOR_MIN_RETAIN = 0.35D;
    private static final double COUPLED_GOVERNOR_TENDER_MARGIN = 0.06D;

    /* Step 7.2.1f: the original governor only watches the first tender. A
     * three-car consist can therefore keep locomotive->tender #1 healthy while
     * tender #2 is being left behind. Step 7.2.1j starts this tail governor a
     * little sooner on grades so the third cart holds the coupler-nub gap. */
    // Step 7.2.1l: tender-to-tender normal gap is ~2.05 blocks.  The tail
    // governor only intervenes after the visible nubs have actually stretched.
    private static final double CHAINED_GOVERNOR_START_DISTANCE = 2.18D;
    private static final double CHAINED_GOVERNOR_FULL_DISTANCE = 2.70D;
    private static final double CHAINED_GOVERNOR_MIN_RETAIN = 0.40D;
    private static final double CHAINED_GOVERNOR_FOLLOWER_MARGIN = 0.05D;
    private static final double CHAINED_GOVERNOR_MIN_MOVING_SPEED = 0.06D;
    private static final int CHAINED_GOVERNOR_MAX_HOPS = 12;

    /*
     * Step 7.3.2b: the original governor only protected stretched couplers.
     * In reverse the locomotive is pushing the consist, so compression is the
     * dangerous case instead: if a follower is slow on a hill/corner, the
     * locomotive must ease off before physically driving through it.
     */
    private static final double REVERSE_COMPRESSION_GOVERNOR_START = 0.08D;
    private static final double REVERSE_COMPRESSION_GOVERNOR_FULL = 0.32D;
    private static final double REVERSE_COMPRESSION_PARTNER_MARGIN = 0.025D;
    private static final double REVERSE_COMPRESSION_MIN_SPEED = 0.020D;

    private static final float VISUAL_FACING_MAX_TURN_DEGREES = 18.0F;

    /* Original 1.12 Traincraft steam-temperature limits. The rewrite exposed
     * these values through ITemperatureSupplier but never actually advanced the
     * temperature. Step 6.9 gives those original limits live boiler behaviour. */
    public static final double MIN_BOILER_TEMPERATURE_K = 253.15D;     // -20 C
    public static final double DEFAULT_BOILER_TEMPERATURE_K = 293.15D; // 20 C
    public static final double HOT_BOILER_TEMPERATURE_K = 373.15D;     // 100 C
    public static final double MAX_BOILER_TEMPERATURE_K = 473.15D;     // 200 C
    public static final double CRITICAL_BOILER_TEMPERATURE_K = 493.15D; // 220 C
    private static final double ABSOLUTE_BOILER_TEMPERATURE_K = 513.15D; // 240 C safety cap

    /* Step 6.9.4: a watered boiler now settles around a controllable working
     * temperature instead of climbing forever just because fuel is burning.
     * The dangerous runaway case is intentionally a low/dry boiler, matching
     * classic Traincraft's "water first, then fuel" behaviour. */
    private static final double NORMAL_OPERATING_TEMPERATURE_K = 438.15D; // 165 C
    private static final double LOW_WATER_OPERATING_TEMPERATURE_K = 468.15D; // 195 C
    private static final double BOILER_WET_HEAT_PER_TICK = 0.06D;
    private static final double BOILER_LOW_WATER_HEAT_PER_TICK = 0.09D;
    private static final double BOILER_DRY_FIRE_HEAT_PER_TICK = 0.18D;
    private static final double BOILER_ACTIVE_COOL_PER_TICK = 0.04D;
    private static final double BOILER_COOL_PER_TICK = 0.035D;
    private static final double BOILER_SAFETY_VALVE_COOL_PER_TICK = 0.10D;
    private static final int BOILER_WARNING_INTERVAL_TICKS = 100;
    private static final int BOILER_VALVE_INTERVAL_TICKS = 10;

    /* Step 6.9.1: once the boiler reaches the critical 220 C range, it may no
     * longer be ignored forever. A wet critical boiler gets about ten seconds
     * of reaction time; a dry, still-fired boiler consumes the failure timer
     * twice as fast. Cooling below 220 C immediately starts recovering the timer. */
    private static final int BOILER_FAILURE_LIMIT_TICKS = 200;
    private static final int BOILER_FAILURE_RECOVERY_PER_TICK = 4;
    private static final int BOILER_CRITICAL_WARNING_INTERVAL_TICKS = 40;
    private static final float BOILER_FAILURE_EXPLOSION_STRENGTH = 3.5F;

    private static final double ACCELERATION_PER_TICK = ACCELERATION_METRES_PER_SECOND_SQ / 400.0D;
    private static final double BRAKE_PER_TICK = BRAKE_METRES_PER_SECOND_SQ / 400.0D;
    private static final double STOP_EPSILON = 0.003D;

    /*
     * Step 7.3.2n: vanilla ascending-rail projection can trap a powered reverse
     * locomotive below STOP_EPSILON at a crest. This floor is deliberately only
     * for active reverse on an ascending rail and exists to overcome that numeric
     * rail deadlock; later realism/tractive-effort limits remain separate.
     */
    private static final double REVERSE_UPHILL_MIN_TRACTION_SPEED = 0.012D;
    private static final double FACING_MOTION_EPSILON = 0.01D;
    private static final float FACING_TIE_DEGREES = 12.0F;
    private static final double NO_STEAM_DEAD_STOP_SPEED = 0.03D;
    private static final double POWERED_ROLLING_RESISTANCE = 0.999D;
    private static final double COAST_ROLLING_RESISTANCE = 0.995D;

    /* Step 6.9.3: vanilla one-block curves become unstable when a full-size
     * locomotive enters them at near-straight-line top speed. Keep the 45 km/h
     * straight-track speed, but cap the four sharp vanilla curve shapes to a
     * safer 21.6 km/h while the cart is physically on the bend. */
    private static final double CURVE_MAX_SPEED_BLOCKS_PER_TICK = 0.30D;

    /*
     * Step 7.4.0b-r1 - curve overspeed telemetry only.
     * Step 7.4.0b-r2a - logged telemetry.
     *
     * IMPORTANT: these values DO NOT alter movement. The existing 0.30 b/t
     * curve cap remains unchanged. We remember the locomotive's last straight
     * speed and report that as curve-entry speed so we can tune future realism
     * without touching the frozen-good consist controller.
     */
    private static final double REALISM_CURVE_WARNING_KMH = 24.0D;
    /*
     * Step 7.4.0c - locomotive curve derailment.
     *
     * Runtime calibration from 7.4.0b-r2a:
     * repeated fastest stable south_west entries were 27.161 and 27.197 km/h.
     * Use 27 km/h as the first physical derail threshold while keeping the
     * existing 24 km/h warning threshold.
     */
    private static final double REALISM_CURVE_WOULD_DERAIL_KMH = 27.0D;
    private static final double REALISM_CURVE_DERAIL_SIDE_OFFSET = 0.68D;
    private static final double REALISM_CURVE_DERAIL_LATERAL_KICK = 0.14D;
    private static final double REALISM_CURVE_DERAIL_VERTICAL_KICK = 0.14D;
    /*
     * Step 7.4.1a - consist mass telemetry only.
     *
     * Provisional operating masses in metric tonnes. These values are LOGGING
     * INPUTS ONLY in 7.4.1a. They do not yet alter acceleration, traction,
     * braking, grade behavior, speed limits, coupling, or derailment.
     */
    private static final double REALISM_MASS_SMALL_STEAM_LOCO_T = 36.0D;
    private static final double REALISM_MASS_STEAM_TENDER_T = 24.0D;
    private static final double REALISM_MASS_FREIGHT_CART_T = 18.0D;
    private static final double REALISM_MASS_PASSENGER_BLUE_T = 28.0D;
    private static final double REALISM_MASS_CABOOSE_T = 16.0D;
    private static final double REALISM_MASS_UNKNOWN_CAR_T = 20.0D;
    private static final int REALISM_MASS_TELEMETRY_INTERVAL_TICKS = 20;
    /*
     * Step 7.4.1b - mass-aware tractive effort.
     *
     * The confirmed locomotive+tender pair is the traction reference:
     * 36 t + 24 t = 60 t. At or below 60 t, preserve the existing
     * 0.5 m/s^2 Traincraft acceleration exactly.
     *
     * Above 60 t, acceleration scales as 60 / consistMass. This models a
     * locomotive with approximately fixed available tractive effort without
     * touching the rail/coupler controller. Vanilla grade resistance therefore
     * becomes increasingly significant as consist mass rises.
     */
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 60.0D;
    private static final double REALISM_TRACTION_MIN_FACTOR = 0.05D;
    /*
     * Step 7.4.2a - grade stall telemetry only.
     *
     * Minecraft's one-block ascending rail is treated as an abstract railway
     * grade for realism tuning rather than a literal 1:1 real-world slope.
     * 0.200 m/s^2 corresponds roughly to a 2% gravity component.
     *
     * IMPORTANT: these values are observation/calibration inputs only in
     * 7.4.2a. They DO NOT alter movement.
     */
    private static final double REALISM_GRADE_HOLD_ACCEL_MPS2 = 0.200D;
    private static final double REALISM_GRADE_MARGIN_MARGINAL_MPS2 = 0.050D;
    private static final int REALISM_GRADE_TELEMETRY_INTERVAL_TICKS = 10;
    /*
     * Step 7.4.2b - physical grade stall and rollback.
     *
     * Only negative tractive-margin uphill commands activate this behavior.
     * The rollback seed is intentionally tiny; once the locomotive starts
     * moving downhill, Minecraft's ascending-rail gravity owns the continuing
     * rollback rather than a Traincraft artificial speed floor.
     */
    private static final double REALISM_GRADE_STALL_SPEED_BPT = 0.004D;
    private static final double REALISM_GRADE_ROLLBACK_SEED_BPT = 0.003D;
    /*
     * Step 7.4.2b-r3 - low-speed stall latch.
     *
     * 7.4.2b proved that a negative-margin 158 t consist can naturally roll
     * downhill, but with continuous uphill input the coupled consist can hover
     * around 2-3 km/h instead of crossing the final stall threshold.
     *
     * Preserve momentum-based climbing. Only latch the stall after the train
     * remains below 3.24 km/h for 1.5 seconds while genuinely overloaded and
     * commanding uphill.
     */
    private static final double REALISM_GRADE_LOW_SPEED_STALL_BPT = 0.045D;
    private static final int REALISM_GRADE_LOW_SPEED_STALL_TICKS = 30;
    /*
     * Step 7.4.2b-r4 - rollback release assist.
     *
     * The r3 stall latch can stop the overloaded train correctly, but the
     * original one-tick 0.003 b/t rollback seed can be cancelled by the
     * coupled-consist controller at dead stop. For 12 ticks after a confirmed
     * rollback event, hold a tiny 0.012 b/t downhill release speed. Then stop
     * assisting and let normal rail gravity own the rollback.
     */
    private static final double REALISM_GRADE_ROLLBACK_ASSIST_BPT = 0.012D;
    private static final int REALISM_GRADE_ROLLBACK_ASSIST_TICKS = 12;
    /*
     * Step 7.4.3a - downhill and braking telemetry only.
     *
     * No movement writes. Sample every 5 ticks (0.25 s) so we can measure
     * actual downhill acceleration/deceleration before changing any physics.
     */
    private static final int REALISM_DOWNHILL_BRAKE_TELEMETRY_INTERVAL_TICKS = 5;
    private static final double REALISM_MPS2_TO_BPT_PER_TICK = 1.0D / 400.0D;
    private static final int REALISM_CURVE_MESSAGE_COOLDOWN_TICKS = 20;

    /* Feedwater automation is deliberately conservative. Storage buckets wait
     * until the tank is at/below 40%, then top it just above that threshold.
     * If the boiler is already overheating, every queued bucket that can fit is
     * injected immediately as an emergency quench. */
    private static final int AUTO_WATER_REFILL_THRESHOLD_MB = 2000;
    private static final double WATER_BUCKET_COOLING_K = 20.0D;

    /* Train impact behaviour. The locomotive should move living entities, not be
     * bounced backward by sheep/mobs. Damage scales with speed, and a high-speed
     * impact is lethal just like the classic heavy Traincraft rolling stock. */
    private static final double IMPACT_MIN_SPEED_KMH = 3.0D;
    private static final double IMPACT_INSTANT_KILL_SPEED_KMH = 35.0D;
    private static final int IMPACT_DAMAGE_COOLDOWN_TICKS = 10;

    /* Step 6.9.6: a full-size locomotive should be removable in Survival without
     * requiring the player to race vanilla minecart damage decay. Three normal
     * player hits within five seconds deliberately remove it from the rails. */
    private static final int SURVIVAL_REMOVAL_HITS = 3;
    private static final int SURVIVAL_REMOVAL_RESET_TICKS = 100;

    private static final double DRIVER_SEAT_BACK_OFFSET = 0.62D;
    private static final double DRIVER_SEAT_Y_OFFSET = 0.18D;

    /* JTMT/model-derived chimney top: centered about 0.75 blocks toward the front. */
    private static final double CHIMNEY_FRONT_OFFSET = 0.75D;
    private static final double CHIMNEY_TOP_Y = 1.82D;
    private static final double RUN_SOUND_SPEED_THRESHOLD = 0.02D;

    /* Rear/firebox click zone measured along the locomotive's front/rear axis. */
    private static final double FIREBOX_REAR = -1.15D;
    private static final double FIREBOX_FRONT = 0.30D;
    private static final double FIREBOX_MIN_Y = 0.10D;
    private static final double FIREBOX_MAX_Y = 1.70D;

    private final ItemStackHandler steamInventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == BURN_SLOT) {
                return isFuel(stack);
            }
            if (slot == WATER_SLOT) {
                return isWaterContainer(stack);
            }
            // Storage slots may also retain the empty buckets produced by the
            // locomotive's automatic feedwater system.
            return isFuel(stack) || isWaterContainer(stack) || stack.is(Items.BUCKET);
        }

        @Override
        protected void onContentsChanged(int slot) {
            SmallSteamLocomotive.this.setChangedForSync();
        }
    };

    private boolean forwardInput;
    private boolean reverseInput;
    private boolean brakeInput;
    private boolean handBrakeApplied;

    private float trainFacingYaw;
    private boolean trainFacingInitialized;
    private int travelDirectionSign = 1;
    private float visualFacingYaw;
    private float previousVisualFacingYaw;
    private boolean visualFacingInitialized;

    // Step 7.4.0b-r1 - curve overspeed telemetry only.
    private double realismLastNonCurveSpeedBpt;
    private RailShape realismLastRailShape;
    private int realismCurveMessageCooldown;
    private boolean realismCurveDerailmentTriggered;
    private int realismMassTelemetryTicks;
    private int realismGradeTelemetryTicks;
    private String realismLastGradeClass = "NONE";
    private String realismGradePhysicsState = "NONE";
    private int realismGradeLowSpeedStallTicks;
    private int realismGradeRollbackAssistTicks;
    private int realismDownhillBrakeTelemetryTicks;
    private double realismDownhillBrakeLastSpeedBpt = -1.0D;
    private long realismDownhillBrakeLastSampleTick = -1L;
    /*
     * Step 7.4.4b - minimal service-brake clamp state.
     *
     * The frozen 7.4.3d fix needs only the horizontal speed that the normal
     * service brake already produced earlier in this tick plus a validity flag.
     * Temporary 7.4.3c pipeline diagnostics are removed.
     */
    private double realismBrakePipelinePostDriverBpt;
    private boolean realismBrakePipelineArmed;

/*
 * Step 7.4.6 - environmental compatibility telemetry state.
 */
private String realismEnvironmentFluidClass = "NONE";

    private int burnTime;
    private int maxBurnTime;
    private int waterAmount;
    private int steamAmount;
    private int steamGenerationTicker;
    private double boilerTemperatureK = DEFAULT_BOILER_TEMPERATURE_K;
    private int boilerOverheatTicks;
    private int boilerFailureTicks;
    private int lastWhistleTick = -100;
    private boolean boilerFailureInProgress;
    private boolean steamInventoryDropped;
    private UUID coupledRollingStockUuid;
    // Step 7.2.0: reserved second coupler for future longer consists.
    // Existing Step 7.0.3 movement continues to use coupledRollingStockUuid only.
    private UUID secondaryCoupledRollingStockUuid;
    private int survivalRemovalHits;
    private int lastSurvivalRemovalHitTick = -SURVIVAL_REMOVAL_RESET_TICKS;
    private final Map<Integer, Integer> lastImpactTicks = new HashMap<>();

    public SmallSteamLocomotive(EntityType<? extends SmallSteamLocomotive> type, Level level) {
        super(type, level);
    }

    /*
     * Step 8.5.0a - per-locomotive realism specification hooks.
     *
     * Defaults intentionally reproduce the frozen Small Steam baseline exactly.
     * Individual locomotive subclasses may override these values without replacing
     * the shared movement, grade, coupling, derailment, boiler, or braking logic.
     */
    protected double getRealismLocomotiveMassTons() {
        return REALISM_MASS_SMALL_STEAM_LOCO_T;
    }

    protected double getRealismTractionReferenceMassTons() {
        return REALISM_TRACTION_REFERENCE_MASS_T;
    }

    protected double getRealismBaseAccelerationMps2() {
        return ACCELERATION_METRES_PER_SECOND_SQ;
    }

    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return MAX_SPEED_BLOCKS_PER_TICK;
    }

    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return MAX_REVERSE_SPEED_BLOCKS_PER_TICK;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_BURN_TIME, 0);
        this.entityData.define(DATA_MAX_BURN_TIME, 0);
        this.entityData.define(DATA_WATER_AMOUNT, 0);
        this.entityData.define(DATA_STEAM_AMOUNT, 0);
        this.entityData.define(DATA_BOILER_TEMPERATURE_K, (float) DEFAULT_BOILER_TEMPERATURE_K);
        this.entityData.define(DATA_HAND_BRAKE, false);
    }

    @Override
    public void tick() {
        if (!this.trainFacingInitialized) {
            this.trainFacingYaw = this.getYRot();
            this.trainFacingInitialized = true;
        }

        if (!this.level().isClientSide) {
            this.tickSteamSystem();
            if (this.isRemoved()) {
                return;
            }

            if (!(this.getFirstPassenger() instanceof ServerPlayer)) {
                this.forwardInput = false;
                this.reverseInput = false;
                this.brakeInput = false;
            }

            this.realismBrakePipelineArmed = this.brakeInput;
            this.applyDriverInput();
            if (this.realismBrakePipelineArmed) {
                Vec3 realismBrakePostDriverMotion = this.getDeltaMovement();
                this.realismBrakePipelinePostDriverBpt =
                        Math.sqrt(
                                realismBrakePostDriverMotion.x * realismBrakePostDriverMotion.x
                                        + realismBrakePostDriverMotion.z * realismBrakePostDriverMotion.z);
            }
            this.applyCoupledConsistGovernor();
            this.tickFrontSnowPlow();
            this.tickEnvironmentalCompatibility();
            this.tickSteamEffects();
            this.syncTelemetryToClient();
        }

        Vec3 tcStep93bPreRailMotion = this.getDeltaMovement();
        super.tick();
        if (!this.level().isClientSide) {
            int tcStep93cRequestedDirection =
                    this.forwardInput ^ this.reverseInput
                            ? (this.forwardInput ? 1 : -1)
                            : 0;
            traincraft.block.track.LegacyContinuousTrackPath.apply(
                    this,
                    tcStep93bPreRailMotion,
                    (this.forwardInput ^ this.reverseInput) && this.steamAmount > 0,
                    this.brakeInput,
                    this.handBrakeApplied,
                    CURVE_MAX_SPEED_BLOCKS_PER_TICK,
                    tcStep93cRequestedDirection,
                    this.getTrainFacingYaw());
        } else {
            // Step 9.3c-t1: mirror only flat continuous-curve geometry on the
            // client so dedicated-server interpolation does not expose the
            // hidden Manhattan RailShape staircase between server sync packets.
            // The server remains authoritative for throttle/brakes/speed, and
            // the already accepted straight-slope client behavior is untouched.
            traincraft.block.track.LegacyContinuousTrackPath.applyClientCurveVisual(
                    this,
                    tcStep93bPreRailMotion,
                    CURVE_MAX_SPEED_BLOCKS_PER_TICK);
        }

/*
         * Step 7.4.3d - post-rail service brake clamp.
         *
         * 7.4.3c proved:
         * - brakeTowardStop() applies the configured service brake correctly;
         * - the consist governor does not undo it;
         * - vanilla minecart super.tick() can re-add downhill horizontal speed
         *   on an ascending rail even when getSlopeAdjustment() returns 0.
         *
         * Let vanilla finish rail position/direction resolution, then only cap
         * horizontal speed back to the already-braked post-driver target if the
         * rail tick increased it. If vanilla slowed the locomotive further, keep
         * the lower speed. This is NOT a second brake application.
         */
        if (!this.level().isClientSide
                && this.brakeInput
                && this.realismBrakePipelineArmed) {
            Vec3 realismPostRailBrakeMotion = this.getDeltaMovement();
            double realismPostRailBrakeSpeed =
                    Math.sqrt(
                            realismPostRailBrakeMotion.x * realismPostRailBrakeMotion.x
                                    + realismPostRailBrakeMotion.z * realismPostRailBrakeMotion.z);
            double realismPostRailBrakeTarget =
                    Math.max(0.0D, this.realismBrakePipelinePostDriverBpt);

            if (realismPostRailBrakeSpeed > realismPostRailBrakeTarget + 1.0E-9D) {
                if (realismPostRailBrakeTarget <= STOP_EPSILON
                        || realismPostRailBrakeSpeed <= 1.0E-9D) {
                    this.setDeltaMovement(
                            0.0D,
                            realismPostRailBrakeMotion.y,
                            0.0D);
                } else {
                    double realismPostRailBrakeScale =
                            realismPostRailBrakeTarget / realismPostRailBrakeSpeed;
                    this.setDeltaMovement(
                            realismPostRailBrakeMotion.x * realismPostRailBrakeScale,
                            realismPostRailBrakeMotion.y,
                            realismPostRailBrakeMotion.z * realismPostRailBrakeScale);
                }

                if ((this.tickCount % 5) == 0) {
                    org.apache.logging.log4j.LogManager
                            .getLogger("TraincraftBrakeClamp")
                            .info(
                                    "[TC-BRAKE-CLAMP]"
                                            + " tick=" + this.level().getGameTime()
                                            + " rawPostRailKmh="
                                            + String.format(
                                                    java.util.Locale.ROOT,
                                                    "%.3f",
                                                    realismPostRailBrakeSpeed * 72.0D)
                                            + " targetKmh="
                                            + String.format(
                                                    java.util.Locale.ROOT,
                                                    "%.3f",
                                                    realismPostRailBrakeTarget * 72.0D)
                                            + " removedKmh="
                                            + String.format(
                                                    java.util.Locale.ROOT,
                                                    "%.3f",
                                                    (realismPostRailBrakeSpeed
                                                                    - realismPostRailBrakeTarget)
                                                            * 72.0D)
                                            + " rail=" + this.getCurrentRailShape()
                                            + " massT="
                                            + String.format(
                                                    java.util.Locale.ROOT,
                                                    "%.1f",
                                                    this.getRealismConsistMassTons()));
                }
            }
        }

        // With an empty boiler the locomotive may still receive tiny motion from
        // minecart rail resolution, a previous tick, or a very small slope. Clamp
        // that low-speed creep to zero so a cold/unfired engine stays visibly dead.
        if (!this.level().isClientSide && this.steamAmount <= 0 && !this.handBrakeApplied) {
            Vec3 motion = this.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontalSpeed < NO_STEAM_DEAD_STOP_SPEED) {
                this.setDeltaMovement(0.0D, motion.y, 0.0D);
            }
        }

        // A set hand brake is a parking brake: vanilla minecart slope physics must
        // not be allowed to creep the locomotive after our control step.
        if (!this.level().isClientSide && this.handBrakeApplied) {
            // A parking brake must kill the slope's vertical component too. Keeping
            // Y motion allowed an uphill cart to keep creeping after C was applied.
            this.setDeltaMovement(Vec3.ZERO);
        }

        if (!this.level().isClientSide) {
            this.tickCurveOverspeedTelemetry();
            this.tickConsistMassTelemetry();
            this.tickGradeRealismTelemetry();
            this.tickDownhillBrakeRealismTelemetry();
        }

        this.updateTrainFacingFromRail();
        Float tcStep93bFacingYaw =
                traincraft.block.track.LegacyContinuousTrackPath.continuousFacingYaw(
                        this, this.getTrainFacingYaw());
        if (tcStep93bFacingYaw != null) {
            this.trainFacingYaw = tcStep93bFacingYaw;
            this.trainFacingInitialized = true;
            this.setYRot(this.trainFacingYaw);
            this.yRotO = this.trainFacingYaw;
        }
        this.updateVisualTrainFacing();
    }

    /**
     * Step 7.4.0b-r1 - curve overspeed telemetry only.
     *
     * This method intentionally has NO setDeltaMovement(), setPos(), coupling,
     * braking, throttle, speed-cap, or derailment writes.
     *
     * The current curve tick may already be subject to the established 0.30 b/t
     * curve cap, so the useful operating number is the last speed observed while
     * the locomotive was still on non-curve rail immediately before entry.
     */
    private void tickCurveOverspeedTelemetry() {
        if (this.realismCurveMessageCooldown > 0) {
            this.realismCurveMessageCooldown--;
        }

        RailShape shape = this.getCurrentRailShape();
        boolean onCurve = isSharpVanillaCurve(shape);
        boolean wasOnCurve =
                this.realismLastRailShape != null
                        && isSharpVanillaCurve(this.realismLastRailShape);

        Vec3 motion = this.getDeltaMovement();
        double currentSpeedBpt =
                Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        /*
         * Step 7.4.0b-r2a - logged telemetry.
         * Record every detected rail-shape transition so we can prove exactly
         * what the locomotive sees at curve entry without changing physics.
         */
        if (shape != this.realismLastRailShape) {
            org.apache.logging.log4j.LogManager
                    .getLogger("TraincraftCurveRealism")
                    .info(
                            "[TC-RAIL-SHAPE]"
                                    + " tick=" + this.level().getGameTime()
                                    + " from=" + this.realismLastRailShape
                                    + " to=" + shape
                                    + " speedKmh="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            currentSpeedBpt * 72.0D)
                                    + " x="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            this.getX())
                                    + " y="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            this.getY())
                                    + " z="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            this.getZ()));
        }

        if (!onCurve) {
            this.realismLastNonCurveSpeedBpt = currentSpeedBpt;
            this.realismLastRailShape = shape;
            return;
        }

        if (!wasOnCurve) {
            double entrySpeedBpt =
                    Math.max(currentSpeedBpt, this.realismLastNonCurveSpeedBpt);
            double entrySpeedKmh = entrySpeedBpt * 72.0D;

            String classification;
            if (entrySpeedKmh >= REALISM_CURVE_WOULD_DERAIL_KMH) {
                classification = "WOULD_DERAIL";
            } else if (entrySpeedKmh >= REALISM_CURVE_WARNING_KMH) {
                classification = "WARNING";
            } else {
                classification = "SAFE";
            }

            org.apache.logging.log4j.LogManager.getLogger("TraincraftCurveRealism").info(
                    "[TC-CURVE-REALISM]"
                            + " tick=" + this.level().getGameTime()
                            + " class=" + classification
                            + " entryKmh=" + String.format(java.util.Locale.ROOT, "%.3f", entrySpeedKmh)
                            + " currentKmh=" + String.format(java.util.Locale.ROOT, "%.3f", currentSpeedBpt * 72.0D)
                            + " rail=" + shape
                            + " reverseInput=" + this.isReverseDriveRequested()
                            + " reverseTravel=" + this.isReverseConsistTravel()
                            + " x=" + String.format(java.util.Locale.ROOT, "%.3f", this.getX())
                            + " y=" + String.format(java.util.Locale.ROOT, "%.3f", this.getY())
                            + " z=" + String.format(java.util.Locale.ROOT, "%.3f", this.getZ()));

            if (entrySpeedKmh >= REALISM_CURVE_WARNING_KMH
                    && this.realismCurveMessageCooldown <= 0
                    && this.getFirstPassenger()
                            instanceof net.minecraft.server.level.ServerPlayer driver) {

                String message =
                        entrySpeedKmh >= REALISM_CURVE_WOULD_DERAIL_KMH
                                ? "Curve entry "
                                    + Math.round(entrySpeedKmh)
                                    + " km/h - WOULD DERAIL"
                                : "Curve overspeed "
                                    + Math.round(entrySpeedKmh)
                                    + " km/h";

                driver.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(message),
                        true);

                this.realismCurveMessageCooldown =
                        REALISM_CURVE_MESSAGE_COOLDOWN_TICKS;
            }

            if (entrySpeedKmh >= REALISM_CURVE_WOULD_DERAIL_KMH) {
                this.triggerCurveOverspeedDerailment(entrySpeedKmh, motion);
            }
        }

        this.realismLastRailShape = shape;
    }
    /**
     * Step 7.4.0c - locomotive curve derailment.
     *
     * IMPORTANT:
     * - Runs only from the locomotive after normal movement resolution.
     * - Does not modify SteamTender or any chained rolling-stock controller.
     * - Does not individually reposition/unlink freight/passenger/caboose cars.
     * - Releases only the locomotive's own couplings, leaving the trailing
     *   consist in its established order.
     */
    /**
     * Step 7.4.0d-r1 - full consist scatter derailment.
     *
     * The proven curve detector still fires only once from the locomotive.
     * Before any coupling is broken, snapshot every SteamTender-derived rolling
     * stock entity whose lead locomotive is THIS locomotive. That includes the
     * functional tender and the current freight/passenger/caboose family.
     *
     * Then break all links and kick each vehicle off the track with a slightly
     * different deterministic vector. The result is deliberate wreck scatter:
     * cars no longer preserve order after a derailment, but normal running and
     * the shared rolling-stock movement controller remain untouched.
     */
    // Step 7.4.0d-r2b - wreck scatter visual tuning.
    private void triggerCurveOverspeedDerailment(double entrySpeedKmh, Vec3 motion) {
        if (this.realismCurveDerailmentTriggered || this.level().isClientSide) {
            return;
        }

        this.realismCurveDerailmentTriggered = true;

        java.util.List<traincraft.entity.train.tender.SteamTender> wreckCars =
                new java.util.ArrayList<>(
                        this.level().getEntitiesOfClass(
                                traincraft.entity.train.tender.SteamTender.class,
                                this.getBoundingBox().inflate(32.0D),
                                car -> CouplingManager.findLeadLocomotive(car) == this));

        /*
         * Sort nearest-to-farthest only so the deterministic kick pattern is
         * repeatable across test runs. The wreck itself intentionally does not
         * preserve consist order after impact.
         */
        wreckCars.sort(
                java.util.Comparator.comparingDouble(
                        car -> car.distanceToSqr(this)));

        org.apache.logging.log4j.LogManager
                .getLogger("TraincraftCurveRealism")
                .info(
                        "[TC-DERAILMENT]"
                                + " tick=" + this.level().getGameTime()
                                + " entryKmh="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        entrySpeedKmh)
                                + " rail=" + this.getCurrentRailShape()
                                + " trailingCars=" + wreckCars.size()
                                + " x="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        this.getX())
                                + " y="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        this.getY())
                                + " z="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        this.getZ()));

        if (this.getFirstPassenger()
                instanceof net.minecraft.server.level.ServerPlayer driver) {
            driver.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "DERAILMENT - consist wreck at "
                                    + Math.round(entrySpeedKmh)
                                    + " km/h"),
                    true);
        }

        /*
         * Snapshot is complete, so now completely release the train.
         */
        CouplingManager.unlinkAll(this, this);
        for (traincraft.entity.train.tender.SteamTender car : wreckCars) {
            CouplingManager.unlinkAll(car, car);
        }

        /*
         * Locomotive kick.
         */
        double horizontalSpeed =
                Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        double forwardX;
        double forwardZ;
        if (horizontalSpeed > 1.0E-6D) {
            double invSpeed = 1.0D / horizontalSpeed;
            forwardX = motion.x * invSpeed;
            forwardZ = motion.z * invSpeed;
        } else {
            double yawRad = Math.toRadians(this.getYRot());
            forwardX = -Math.sin(yawRad);
            forwardZ = Math.cos(yawRad);
        }

        double locoSide = (this.getId() & 1) == 0 ? 1.0D : -1.0D;
        double locoLateralX = -forwardZ * locoSide;
        double locoLateralZ = forwardX * locoSide;

        this.setPos(
                this.getX() + locoLateralX * REALISM_CURVE_DERAIL_SIDE_OFFSET,
                this.getY() + 0.22D,
                this.getZ() + locoLateralZ * REALISM_CURVE_DERAIL_SIDE_OFFSET);

        this.setDeltaMovement(
                motion.x * 0.72D
                        + locoLateralX * REALISM_CURVE_DERAIL_LATERAL_KICK,
                Math.max(
                        motion.y,
                        REALISM_CURVE_DERAIL_VERTICAL_KICK),
                motion.z * 0.72D
                        + locoLateralZ * REALISM_CURVE_DERAIL_LATERAL_KICK);

        /*
         * Trailing-vehicle scatter.
         *
         * Each car uses its OWN velocity direction where possible so a vehicle
         * already entering a curve or grade does not receive an identical kick
         * to the car ahead of it.
         *
         * Pattern:
         *   - sides alternate left/right
         *   - lateral strength cycles across four values
         *   - a small forward/back component varies by index
         *   - upward kick also varies slightly
         *
         * This is intentionally "messy" once derailment occurs.
         */
        for (int i = 0; i < wreckCars.size(); i++) {
            traincraft.entity.train.tender.SteamTender car = wreckCars.get(i);
            Vec3 carMotion = car.getDeltaMovement();

            double carHorizontal =
                    Math.sqrt(
                            carMotion.x * carMotion.x
                                    + carMotion.z * carMotion.z);

            double carForwardX;
            double carForwardZ;

            if (carHorizontal > 1.0E-6D) {
                double invCarSpeed = 1.0D / carHorizontal;
                carForwardX = carMotion.x * invCarSpeed;
                carForwardZ = carMotion.z * invCarSpeed;
            } else {
                carForwardX = forwardX;
                carForwardZ = forwardZ;
            }

            double side = (i & 1) == 0 ? -1.0D : 1.0D;
            double lateralX = -carForwardZ * side;
            double lateralZ = carForwardX * side;

            double lateralOffset = 0.42D + (i % 4) * 0.13D;
            double lateralKick = 0.095D + (i % 4) * 0.026D;
            double forwardKick = ((i % 3) - 1) * 0.035D;
            double upwardKick = 0.100D + (i % 3) * 0.035D;

            car.setPos(
                    car.getX()
                            + lateralX * lateralOffset
                            + carForwardX * forwardKick * 2.0D,
                    car.getY() + 0.12D + (i % 3) * 0.07D,
                    car.getZ()
                            + lateralZ * lateralOffset
                            + carForwardZ * forwardKick * 2.0D);

            car.setDeltaMovement(
                    carMotion.x * 0.66D
                            + lateralX * lateralKick
                            + carForwardX * forwardKick,
                    Math.max(carMotion.y, upwardKick),
                    carMotion.z * 0.66D
                            + lateralZ * lateralKick
                            + carForwardZ * forwardKick);

            org.apache.logging.log4j.LogManager
                    .getLogger("TraincraftCurveRealism")
                    .info(
                            "[TC-WRECK-CAR]"
                                    + " tick=" + this.level().getGameTime()
                                    + " index=" + i
                                    + " id=" + car.getId()
                                    + " type=" + car.getClass().getSimpleName()
                                    + " side=" + side
                                    + " lateralKick="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            lateralKick)
                                    + " forwardKick="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            forwardKick)
                                    + " upwardKick="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            upwardKick));
        }
    }
    /**
     * Step 7.4.1a - consist mass telemetry only.
     *
     * Snapshot the currently coupled rolling stock once per second and report
     * its provisional operating mass. This method contains no physics writes.
     */
    /**
     * Step 7.4.1b - return the currently coupled consist operating mass.
     *
     * This deliberately uses the same proven lead-locomotive ownership test
     * as Step 7.4.1a telemetry. After a derailment/uncouple, released cars stop
     * contributing immediately.
     */
    private double getRealismConsistMassTons() {
        double consistMassT = this.getRealismLocomotiveMassTons();

        java.util.List<traincraft.entity.train.tender.SteamTender> cars =
                this.level().getEntitiesOfClass(
                        traincraft.entity.train.tender.SteamTender.class,
                        this.getBoundingBox().inflate(64.0D),
                        car -> CouplingManager.findLeadLocomotive(car) == this);

        for (traincraft.entity.train.tender.SteamTender car : cars) {
            consistMassT += this.getRealismVehicleMassTons(car);
        }

        return Math.max(
                this.getRealismLocomotiveMassTons(),
                consistMassT);
    }

    /**
     * Fixed-tractive-effort approximation.
     *
     * <= 60 t: preserve original acceleration.
     * > 60 t: acceleration = original * (60 / mass).
     */
    private double getRealismTractionFactor(double consistMassT) {
        double referenceMassT = this.getRealismTractionReferenceMassTons();
        if (consistMassT <= referenceMassT) {
            return 1.0D;
        }

        return Mth.clamp(
                referenceMassT / consistMassT,
                REALISM_TRACTION_MIN_FACTOR,
                1.0D);
    }
    /**
     * Step 7.4.2a - grade stall telemetry only.
     *
     * Observe available mass-aware acceleration against a provisional
     * real-world-equivalent grade holding requirement. No movement is written.
     */
    /**
     * Step 7.4.2b - true only when the driver's requested direction points
     * uphill AND the current consist has negative calculated tractive margin.
     */
    private boolean isRealismOverloadedUphillCommand(
            int requestedDirection,
            double frontX,
            double frontZ,
            @Nullable RailShape shape) {
        if (requestedDirection == 0 || shape == null) {
            return false;
        }

        Vec3 uphill = this.getRealismRailUphillVector(shape);
        if (uphill == null) {
            return false;
        }

        double commandDotUphill =
                (frontX * requestedDirection) * uphill.x
                        + (frontZ * requestedDirection) * uphill.z;

        if (commandDotUphill <= 0.25D) {
            return false;
        }

        double consistMassT = this.getRealismConsistMassTons();
        double availableAccelMps2 =
                this.getRealismBaseAccelerationMps2()
                        * this.getRealismTractionFactor(consistMassT);

        return availableAccelMps2
                < REALISM_GRADE_HOLD_ACCEL_MPS2 - 1.0E-6D;
    }

    /**
     * Seed a stalled overloaded train downhill.
     *
     * The seed is deliberately small. Once rollback has begun, the normal
     * minecart/rail slope physics is allowed to continue it.
     */
    private void beginRealismGradeRollback(
            Vec3 uphill,
            Vec3 motion,
            double consistMassT,
            double availableAccelMps2) {
        this.realismGradeRollbackAssistTicks =
                REALISM_GRADE_ROLLBACK_ASSIST_TICKS;

        this.setDeltaMovement(
                -uphill.x * REALISM_GRADE_ROLLBACK_SEED_BPT,
                motion.y,
                -uphill.z * REALISM_GRADE_ROLLBACK_SEED_BPT);

        if (!"ROLLBACK".equals(this.realismGradePhysicsState)) {
            this.realismGradePhysicsState = "ROLLBACK";

            org.apache.logging.log4j.LogManager
                    .getLogger("TraincraftGradeRealism")
                    .info(
                            "[TC-GRADE-PHYSICS]"
                                    + " event=ROLLBACK"
                                    + " tick=" + this.level().getGameTime()
                                    + " massT="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.1f",
                                            consistMassT)
                                    + " availableMps2="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            availableAccelMps2)
                                    + " holdMps2="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            REALISM_GRADE_HOLD_ACCEL_MPS2)
                                    + " deficitMps2="
                                    + String.format(
                                            java.util.Locale.ROOT,
                                            "%.3f",
                                            REALISM_GRADE_HOLD_ACCEL_MPS2
                                                    - availableAccelMps2)
                                    + " rail=" + this.getCurrentRailShape());
        }
    }

    private void logRealismGradeStall(
            double consistMassT,
            double availableAccelMps2,
            double speedKmh) {
        if ("STALLING".equals(this.realismGradePhysicsState)) {
            return;
        }

        this.realismGradePhysicsState = "STALLING";

        org.apache.logging.log4j.LogManager
                .getLogger("TraincraftGradeRealism")
                .info(
                        "[TC-GRADE-PHYSICS]"
                                + " event=STALLING"
                                + " tick=" + this.level().getGameTime()
                                + " massT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        consistMassT)
                                + " availableMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        availableAccelMps2)
                                + " holdMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        REALISM_GRADE_HOLD_ACCEL_MPS2)
                                + " deficitMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        REALISM_GRADE_HOLD_ACCEL_MPS2
                                                - availableAccelMps2)
                                + " speedKmh="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        speedKmh)
                                + " rail=" + this.getCurrentRailShape());
    }
    /**
     * Step 7.4.3a - telemetry only.
     * Step 7.4.3a-r1 - compiler repair: reverseTravel is computed inline
     * from locomotive facing versus current motion; no physics writes.
     *
     * Measures actual horizontal speed change on ascending rails while
     * distinguishing downhill/coast/service-brake/parking-brake states.
     * This method intentionally contains no motion or position writes.
     */
    private void tickDownhillBrakeRealismTelemetry() {
        if (this.level().isClientSide) {
            return;
        }

        this.realismDownhillBrakeTelemetryTicks++;
        if (this.realismDownhillBrakeTelemetryTicks
                < REALISM_DOWNHILL_BRAKE_TELEMETRY_INTERVAL_TICKS) {
            return;
        }
        this.realismDownhillBrakeTelemetryTicks = 0;

        long nowTick = this.level().getGameTime();
        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed =
                Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        RailShape shape = this.getCurrentRailShape();
        Vec3 uphill = this.getRealismRailUphillVector(shape);

        double motionUp = 0.0D;
        boolean onGrade = uphill != null;
        if (onGrade) {
            motionUp = motion.x * uphill.x + motion.z * uphill.z;
        }

        boolean downhillTravel = onGrade && motionUp < -STOP_EPSILON;
        boolean uphillTravel = onGrade && motionUp > STOP_EPSILON;

        String brakeState;
        if (this.handBrakeApplied) {
            brakeState = "PARKING";
        } else if (this.brakeInput) {
            brakeState = "SERVICE";
        } else if (this.forwardInput && this.reverseInput) {
            brakeState = "OPPOSED_INPUT";
        } else {
            brakeState = "NONE";
        }

        double measuredAccelMps2 = 0.0D;
        double measuredDecelMps2 = 0.0D;
        long sampleTicks = 0L;

        if (this.realismDownhillBrakeLastSpeedBpt >= 0.0D
                && this.realismDownhillBrakeLastSampleTick >= 0L) {
            sampleTicks =
                    Math.max(
                            1L,
                            nowTick - this.realismDownhillBrakeLastSampleTick);

            measuredAccelMps2 =
                    (horizontalSpeed
                                    - this.realismDownhillBrakeLastSpeedBpt)
                            * 400.0D
                            / (double) sampleTicks;

            measuredDecelMps2 = -measuredAccelMps2;
        }

        this.realismDownhillBrakeLastSpeedBpt = horizontalSpeed;
        this.realismDownhillBrakeLastSampleTick = nowTick;

        /*
         * Log grade travel and every active braking sample. Flat unbraked
         * cruising is intentionally omitted to keep the log readable.
         */
        if (!onGrade && "NONE".equals(brakeState)) {
            return;
        }

        double consistMassT = this.getRealismConsistMassTons();

        String travelState =
                downhillTravel
                        ? "DOWNHILL"
                        : (uphillTravel ? "UPHILL" : (onGrade ? "GRADE_STOP" : "FLAT"));

        org.apache.logging.log4j.LogManager
                .getLogger("TraincraftDownhillBrakeRealism")
                .info(
                        "[TC-DOWNHILL-BRAKE]"
                                + " tick=" + nowTick
                                + " travel=" + travelState
                                + " brake=" + brakeState
                                + " massT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        consistMassT)
                                + " speedKmh="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        horizontalSpeed * 72.0D)
                                + " motionUp="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.5f",
                                        motionUp)
                                + " measuredAccelMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        measuredAccelMps2)
                                + " measuredDecelMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        measuredDecelMps2)
                                + " configuredBrakeMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        BRAKE_METRES_PER_SECOND_SQ)
                                + " sampleTicks=" + sampleTicks
                                + " rail=" + shape
                                + " forwardInput=" + this.forwardInput
                                + " reverseInput=" + this.reverseInput
                                + " reverseTravel=" + (horizontalSpeed > STOP_EPSILON
                                        && (motion.x
                                                        * Math.sin(
                                                                Math.toRadians(
                                                                        this.getTrainFacingYaw()))
                                                - motion.z
                                                        * Math.cos(
                                                                Math.toRadians(
                                                                        this.getTrainFacingYaw())))
                                                < 0.0D));
    }
    private void tickGradeRealismTelemetry() {
        if (this.level().isClientSide) {
            return;
        }

        RailShape shape = this.getCurrentRailShape();
        Vec3 uphill = this.getRealismRailUphillVector(shape);

        if (uphill == null) {
            this.realismGradeTelemetryTicks = 0;
            this.realismLastGradeClass = "NONE";
            return;
        }

        this.realismGradeTelemetryTicks++;

        int requestedDirection =
                this.forwardInput ? 1 : (this.reverseInput ? -1 : 0);

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);

        double commandX = frontX * requestedDirection;
        double commandZ = frontZ * requestedDirection;
        double commandDotUphill =
                commandX * uphill.x + commandZ * uphill.z;

        Vec3 motion = this.getDeltaMovement();
        double motionAlongUphill =
                motion.x * uphill.x + motion.z * uphill.z;

        double consistMassT = this.getRealismConsistMassTons();
        double tractionFactor =
                this.getRealismTractionFactor(consistMassT);
        double availableAccelMps2 =
                this.getRealismBaseAccelerationMps2() * tractionFactor;
        double gradeMarginMps2 =
                availableAccelMps2 - REALISM_GRADE_HOLD_ACCEL_MPS2;

        boolean commandingUphill =
                requestedDirection != 0 && commandDotUphill > 0.25D;

        String gradeClass;
        if (!commandingUphill) {
            gradeClass = "NOT_UPHILL_COMMAND";
        } else if (gradeMarginMps2 < -1.0E-6D) {
            gradeClass = "WOULD_STALL_ROLLBACK";
        } else if (gradeMarginMps2
                <= REALISM_GRADE_MARGIN_MARGINAL_MPS2) {
            gradeClass = "MARGINAL_CLIMB";
        } else {
            gradeClass = "CAN_CLIMB";
        }

        boolean classChanged =
                !gradeClass.equals(this.realismLastGradeClass);

        if (!classChanged
                && this.realismGradeTelemetryTicks
                        < REALISM_GRADE_TELEMETRY_INTERVAL_TICKS) {
            return;
        }

        this.realismGradeTelemetryTicks = 0;
        this.realismLastGradeClass = gradeClass;

        org.apache.logging.log4j.LogManager
                .getLogger("TraincraftGradeRealism")
                .info(
                        "[TC-GRADE]"
                                + " tick=" + this.level().getGameTime()
                                + " class=" + gradeClass
                                + " massT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        consistMassT)
                                + " tractionFactor="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        tractionFactor)
                                + " availableMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        availableAccelMps2)
                                + " holdMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        REALISM_GRADE_HOLD_ACCEL_MPS2)
                                + " marginMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        gradeMarginMps2)
                                + " speedKmh="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        this.getSpeedKmh())
                                + " motionUp="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.5f",
                                        motionAlongUphill)
                                + " commandDotUp="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        commandDotUphill)
                                + " rail=" + shape
                                + " reverseInput=" + this.isReverseDriveRequested()
                                + " reverseTravel=" + this.isReverseConsistTravel());
    }

    /**
     * Unit horizontal vector pointing toward the high end of an ascending rail.
     */
    @Nullable
    private Vec3 getRealismRailUphillVector(@Nullable RailShape shape) {
        if (shape == null) {
            return null;
        }

        return switch (shape) {
            case ASCENDING_NORTH -> new Vec3(0.0D, 0.0D, -1.0D);
            case ASCENDING_SOUTH -> new Vec3(0.0D, 0.0D, 1.0D);
            case ASCENDING_EAST -> new Vec3(1.0D, 0.0D, 0.0D);
            case ASCENDING_WEST -> new Vec3(-1.0D, 0.0D, 0.0D);
            default -> null;
        };
    }
    private void tickConsistMassTelemetry() {
        if (this.level().isClientSide) {
            return;
        }

        this.realismMassTelemetryTicks++;
        if (this.realismMassTelemetryTicks
                < REALISM_MASS_TELEMETRY_INTERVAL_TICKS) {
            return;
        }
        this.realismMassTelemetryTicks = 0;

        java.util.List<traincraft.entity.train.tender.SteamTender> cars =
                new java.util.ArrayList<>(
                        this.level().getEntitiesOfClass(
                                traincraft.entity.train.tender.SteamTender.class,
                                this.getBoundingBox().inflate(64.0D),
                                car -> CouplingManager.findLeadLocomotive(car) == this));

        cars.sort(
                java.util.Comparator.comparingDouble(
                        car -> car.distanceToSqr(this)));

        double trailingMassT = 0.0D;
        int unknownCars = 0;
        StringBuilder breakdown = new StringBuilder();

        for (traincraft.entity.train.tender.SteamTender car : cars) {
            double massT = this.getRealismVehicleMassTons(car);
            trailingMassT += massT;

            String type = car.getClass().getSimpleName();
            if (!this.isKnownRealismMassType(car)) {
                unknownCars++;
            }

            if (breakdown.length() > 0) {
                breakdown.append("|");
            }
            breakdown.append(type)
                    .append(":")
                    .append(
                            String.format(
                                    java.util.Locale.ROOT,
                                    "%.1f",
                                    massT));
        }

        double consistMassT =
                this.getRealismLocomotiveMassTons() + trailingMassT;
        double tractionFactor =
                this.getRealismTractionFactor(consistMassT);
        double effectiveAccelerationMps2 =
                this.getRealismBaseAccelerationMps2() * tractionFactor;

        RailShape railShape = this.getCurrentRailShape();

        org.apache.logging.log4j.LogManager
                .getLogger("TraincraftMassRealism")
                .info(
                        "[TC-MASS]"
                                + " tick=" + this.level().getGameTime()
                                + " locoT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        this.getRealismLocomotiveMassTons())
                                + " trailingT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        trailingMassT)
                                + " consistT="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.1f",
                                        consistMassT)
                                + " tractionFactor="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        tractionFactor)
                                + " accelMps2="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        effectiveAccelerationMps2)
                                + " cars=" + cars.size()
                                + " unknownCars=" + unknownCars
                                + " speedKmh="
                                + String.format(
                                        java.util.Locale.ROOT,
                                        "%.3f",
                                        this.getSpeedKmh())
                                + " rail=" + railShape
                                + " reverseInput=" + this.isReverseDriveRequested()
                                + " reverseTravel=" + this.isReverseConsistTravel()
                                + " breakdown=" + breakdown);
    }

    /**
     * Provisional per-vehicle mass table for Step 7.4.1a.
     * No inventory/cargo payload mass is included yet.
     */
    private double getRealismVehicleMassTons(
            traincraft.entity.train.tender.SteamTender car) {
        double overrideMassT = car.getRealismMassOverrideTons();
        if (Double.isFinite(overrideMassT) && overrideMassT > 0.0D) {
            return overrideMassT;
        }

        String type = car.getClass().getSimpleName();

        return switch (type) {
            case "SteamTender" -> REALISM_MASS_STEAM_TENDER_T;
            case "FreightCart" -> REALISM_MASS_FREIGHT_CART_T;
            case "PassengerCoachBlue" -> REALISM_MASS_PASSENGER_BLUE_T;
            case "ClassicCaboose" -> REALISM_MASS_CABOOSE_T;
            default -> REALISM_MASS_UNKNOWN_CAR_T;
        };
    }

    private boolean isKnownRealismMassType(
            traincraft.entity.train.tender.SteamTender car) {
        double overrideMassT = car.getRealismMassOverrideTons();
        if (Double.isFinite(overrideMassT) && overrideMassT > 0.0D) {
            return true;
        }

        String type = car.getClass().getSimpleName();
        return "SteamTender".equals(type)
                || "FreightCart".equals(type)
                || "PassengerCoachBlue".equals(type)
                || "ClassicCaboose".equals(type);
    }
    private void tickSteamSystem() {
        // Step 6.9.8: fuel and feedwater reserves are serviced independently.
        // Normal reserve water stays untouched until the tank reaches the low-water
        // threshold. Once overheating begins, every queued water bucket is used as
        // an emergency quench even if the tank itself is already full; excess water
        // is treated as overflow while its cooling effect is still applied.
        boolean emergencyWaterDump = this.boilerTemperatureK > MAX_BOILER_TEMPERATURE_K;
        this.refillPrimarySlotsFromStorage();
        if (emergencyWaterDump) {
            this.consumePrimaryWaterContainer(true);
            this.dumpQueuedWaterForEmergencyCooling();
            if (this.boilerTemperatureK > MAX_BOILER_TEMPERATURE_K) {
                this.quenchFromCoupledTender();
            }
        } else {
            this.consumePrimaryWaterContainer(false);
            this.autoRefillWaterFromStorage();
            this.autoRefillWaterFromCoupledTender();
        }

        if (this.burnTime <= 0 && this.waterAmount > 0 && this.steamAmount < STEAM_CAPACITY) {
            ItemStack fuel = this.steamInventory.getStackInSlot(BURN_SLOT);
            int burn = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
            if (burn > 0) {
                this.maxBurnTime = burn;
                this.burnTime = burn;
                ItemStack remaining = fuel.copy();
                remaining.shrink(1);
                this.steamInventory.setStackInSlot(BURN_SLOT, remaining);
            }
        }

        boolean fireLit = this.burnTime > 0;
        if (fireLit) {
            this.burnTime--;

            // Step 6.0 boiler model: fire + water builds a buffered steam reserve.
            // At full steam the boiler stops consuming water until steam is used.
            if (this.waterAmount > 0 && this.steamAmount < STEAM_CAPACITY) {
                this.steamGenerationTicker++;
                if (this.steamGenerationTicker >= 2) {
                    this.steamGenerationTicker = 0;
                    this.waterAmount = Math.max(0, this.waterAmount - 1);
                    this.steamAmount = Math.min(STEAM_CAPACITY, this.steamAmount + 2);
                }
            }
        } else {
            this.steamGenerationTicker = 0;
        }

        this.tickBoilerTemperature(fireLit);
    }

    private void tickBoilerTemperature(boolean fireLit) {
        if (fireLit) {
            if (this.waterAmount <= 0) {
                // Dry firing is the true runaway condition. It will continue
                // climbing through overheat/critical even against the safety valve.
                this.boilerTemperatureK += BOILER_DRY_FIRE_HEAT_PER_TICK;
            } else {
                boolean lowWater = this.waterAmount <= AUTO_WATER_REFILL_THRESHOLD_MB;
                double targetTemperature = lowWater
                        ? LOW_WATER_OPERATING_TEMPERATURE_K
                        : NORMAL_OPERATING_TEMPERATURE_K;
                double heatRate = lowWater
                        ? BOILER_LOW_WATER_HEAT_PER_TICK
                        : BOILER_WET_HEAT_PER_TICK;

                // A healthy watered boiler reaches a stable working temperature
                // rather than inevitably overheating. Running low on water raises
                // that equilibrium close to the danger line, giving the player a
                // clear reason to refill before the tank actually runs dry.
                if (this.boilerTemperatureK < targetTemperature) {
                    this.boilerTemperatureK = Math.min(
                            targetTemperature,
                            this.boilerTemperatureK + heatRate);
                } else if (this.boilerTemperatureK > targetTemperature) {
                    this.boilerTemperatureK = Math.max(
                            targetTemperature,
                            this.boilerTemperatureK - BOILER_ACTIVE_COOL_PER_TICK);
                }
            }
        } else if (this.boilerTemperatureK > DEFAULT_BOILER_TEMPERATURE_K) {
            this.boilerTemperatureK = Math.max(
                    DEFAULT_BOILER_TEMPERATURE_K,
                    this.boilerTemperatureK - BOILER_COOL_PER_TICK);
        } else if (this.boilerTemperatureK < DEFAULT_BOILER_TEMPERATURE_K) {
            // Worlds upgraded from a future colder-environment system should
            // naturally settle back toward the classic 20 C resting temperature.
            this.boilerTemperatureK = Math.min(
                    DEFAULT_BOILER_TEMPERATURE_K,
                    this.boilerTemperatureK + BOILER_COOL_PER_TICK);
        }

        if (this.boilerTemperatureK > MAX_BOILER_TEMPERATURE_K) {
            this.boilerOverheatTicks++;

            // Classic-style safety-valve behaviour: dump a little stored steam
            // while above 200 C and take a small amount of heat with it.
            this.boilerTemperatureK -= BOILER_SAFETY_VALVE_COOL_PER_TICK;
            if (this.boilerOverheatTicks % BOILER_VALVE_INTERVAL_TICKS == 0
                    && this.steamAmount > 0) {
                this.steamAmount = Math.max(0, this.steamAmount - 6);
            }

            if (this.boilerOverheatTicks == 1
                    || this.boilerOverheatTicks % BOILER_WARNING_INTERVAL_TICKS == 0) {
                if (this.getFirstPassenger() instanceof ServerPlayer driver) {
                    driver.displayClientMessage(
                            Component.translatable(
                                    "message.traincraft.boiler.overheating",
                                    Math.round(this.getBoilerTemperatureC())),
                            true);
                }
            }
        } else {
            this.boilerOverheatTicks = 0;
        }

        if (this.boilerTemperatureK >= CRITICAL_BOILER_TEMPERATURE_K) {
            // Dry firing is the dangerous case: with no water to absorb heat the
            // critical timer advances twice as quickly.
            this.boilerFailureTicks += this.waterAmount > 0 ? 1 : 2;

            if (this.boilerFailureTicks == 1
                    || this.boilerFailureTicks % BOILER_CRITICAL_WARNING_INTERVAL_TICKS == 0) {
                if (this.getFirstPassenger() instanceof ServerPlayer driver) {
                    int secondsLeft = Math.max(0,
                            (BOILER_FAILURE_LIMIT_TICKS - this.boilerFailureTicks + 19) / 20);
                    driver.displayClientMessage(
                            Component.translatable(
                                    "message.traincraft.boiler.critical",
                                    Math.round(this.getBoilerTemperatureC()),
                                    secondsLeft),
                            true);
                }
            }

            if (this.boilerFailureTicks >= BOILER_FAILURE_LIMIT_TICKS) {
                this.failBoiler();
                return;
            }
        } else if (this.boilerFailureTicks > 0) {
            // Dropping back below the critical range quickly restores the safety
            // margin. Below 200 C the timer is completely cleared.
            if (this.boilerTemperatureK <= MAX_BOILER_TEMPERATURE_K) {
                this.boilerFailureTicks = 0;
            } else {
                this.boilerFailureTicks = Math.max(
                        0, this.boilerFailureTicks - BOILER_FAILURE_RECOVERY_PER_TICK);
            }
        }

        this.boilerTemperatureK = Mth.clamp(
                this.boilerTemperatureK,
                MIN_BOILER_TEMPERATURE_K,
                ABSOLUTE_BOILER_TEMPERATURE_K);
    }

    private void failBoiler() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.getFirstPassenger() instanceof ServerPlayer driver) {
            driver.displayClientMessage(
                    Component.translatable("message.traincraft.boiler.failure"),
                    true);
        }

        // A destroyed locomotive must release its tender before the entity is
        // discarded so the surviving rolling stock does not retain a stale link.
        CouplingManager.unlink(this, this);

        // Step 6.9.5: preserve the locomotive inventory across the blast. The
        // explosion happens first, then the stored fuel/water/items are scattered
        // so the blast itself cannot immediately delete the recovery drops.
        this.boilerFailureInProgress = true;
        try {
            serverLevel.explode(
                    this,
                    this.getX(), this.getY() + 0.8D, this.getZ(),
                    BOILER_FAILURE_EXPLOSION_STRENGTH,
                    Level.ExplosionInteraction.TNT);
        } finally {
            this.boilerFailureInProgress = false;
        }

        this.dropSteamInventory();
        this.discard();
    }

    /**
     * Drop every internal locomotive inventory stack exactly once. This is used
     * both when the player breaks/removes the locomotive from the rails and when
     * an overheated boiler destroys it.
     */
    private void dropSteamInventory() {
        if (this.level().isClientSide || this.steamInventoryDropped) {
            return;
        }

        this.steamInventoryDropped = true;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack stack = this.steamInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack dropped = this.steamInventory.extractItem(slot, stack.getCount(), false);
            if (!dropped.isEmpty()) {
                Containers.dropItemStack(
                        this.level(),
                        this.getX(), this.getY() + 0.35D, this.getZ(),
                        dropped);
            }
        }
    }

    /**
     * Survival removal helper. Vanilla minecart damage decays every tick, which
     * makes a large Traincraft locomotive frustrating to remove with ordinary
     * survival attacks. Keep vanilla damage/animation handling, but guarantee
     * that three successful player hits within five seconds remove the locomotive.
     * The normal destroy path below then returns the locomotive item and spills
     * its internal inventory. Creative mode keeps vanilla instant-removal rules.
     */
    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        Entity attacker = damageSource.getEntity();
        // Creative-mode minecart removal may discard the entity during
        // super.hurt(). Release the tender first so it cannot retain a stale
        // locomotive UUID.
        if (!this.level().isClientSide
                && attacker instanceof Player creativePlayer
                && creativePlayer.getAbilities().instabuild) {
            CouplingManager.unlink(this, this);
        }

        boolean handled = super.hurt(damageSource, amount);
        if (!handled || this.level().isClientSide || this.isRemoved()) {
            return handled;
        }

        if (!(attacker instanceof Player player) || player.getAbilities().instabuild) {
            return handled;
        }

        if (this.tickCount - this.lastSurvivalRemovalHitTick > SURVIVAL_REMOVAL_RESET_TICKS) {
            this.survivalRemovalHits = 0;
        }
        this.lastSurvivalRemovalHitTick = this.tickCount;
        this.survivalRemovalHits++;

        if (this.survivalRemovalHits >= SURVIVAL_REMOVAL_HITS) {
            this.ejectPassengers();
            this.destroy(damageSource);
        }

        return true;
    }

    /**
     * Normal minecart destruction still drops the locomotive item through the
     * vanilla Minecart path, but Traincraft's internal inventory must be spilled
     * first. During the boiler's own explosion we suppress that vanilla destroy
     * callback so the engine itself is destroyed while its cargo is dropped after
     * the blast.
     */
    @Override
    public void destroy(DamageSource damageSource) {
        if (this.boilerFailureInProgress) {
            return;
        }

        CouplingManager.unlink(this, this);
        this.dropSteamInventory();
        super.destroy(damageSource);
    }

    /**
     * Permanent-removal safety net matching the tender. This catches destructive
     * removal paths that do not pass through the normal minecart destroy callback
     * while deliberately preserving couplers during chunk unloads.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            CouplingManager.unlinkAll(this, this);
        }
        super.remove(reason);
    }

    private void tickSteamEffects() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        boolean runningOnSteam = horizontalSpeed > RUN_SOUND_SPEED_THRESHOLD && this.steamAmount > 0;
        boolean fireLit = this.burnTime > 0;

        if (!runningOnSteam && !fireLit && !this.isBoilerOverheating()) {
            return;
        }

        Vec3 chimney = this.getChimneyTop();

        if (this.isBoilerOverheating() && this.tickCount % BOILER_VALVE_INTERVAL_TICKS == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.POOF,
                    chimney.x, chimney.y, chimney.z,
                    this.isBoilerCritical() ? 6 : 4,
                    0.10D, 0.05D, 0.10D,
                    this.isBoilerCritical() ? 0.09D : 0.06D
            );
            if (this.isBoilerCritical()) {
                serverLevel.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        chimney.x, chimney.y, chimney.z,
                        2,
                        0.07D, 0.04D, 0.07D,
                        0.025D
                );
            }
        }

        int particlePeriod = runningOnSteam ? 2 : 6;
        if (this.tickCount % particlePeriod == 0) {
            int count = runningOnSteam ? 2 : 1;
            serverLevel.sendParticles(
                    ParticleTypes.POOF,
                    chimney.x, chimney.y, chimney.z,
                    count,
                    0.045D, 0.025D, 0.045D,
                    runningOnSteam ? 0.035D : 0.018D
            );

            // The main steam plume uses the vanilla POOF particle for a greyish-white
            // steam color. A coal fire still adds an occasional darker puff.
            if (fireLit && this.tickCount % 12 == 0) {
                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        chimney.x, chimney.y, chimney.z,
                        1,
                        0.03D, 0.02D, 0.03D,
                        0.012D
                );
            }
        }

        // These are the original Traincraft steam_idle.ogg / steam_run.ogg assets.
        // Their lengths are about 31 and 15 ticks respectively, so replaying at
        // those intervals produces a continuous engine loop without new audio.
        if (runningOnSteam && this.tickCount % 15 == 0) {
            serverLevel.playSound(
                    null, chimney.x, chimney.y, chimney.z,
                    TCSounds.STEAM_RUN.get(), SoundSource.NEUTRAL,
                    0.75F, 0.96F + this.random.nextFloat() * 0.08F
            );
        } else if (!runningOnSteam && fireLit && this.tickCount % 31 == 0) {
            serverLevel.playSound(
                    null, chimney.x, chimney.y, chimney.z,
                    TCSounds.STEAM_IDLE.get(), SoundSource.NEUTRAL,
                    0.55F, 0.98F + this.random.nextFloat() * 0.04F
            );
        }
    }

    /*
     * Step 8.5.3a - model-specific steam locomotive visual anchors.
     *
     * Defaults intentionally reproduce the confirmed Small Steam positions.
     * Individual locomotive subclasses may override these without changing
     * renderer transforms or shared movement/physics behavior.
     */
    protected double getDriverSeatBackOffset() {
        return DRIVER_SEAT_BACK_OFFSET;
    }

    protected double getDriverSeatYOffset() {
        return DRIVER_SEAT_Y_OFFSET;
    }

    protected double getDriverSeatSideOffset() {
        return 0.0D;
    }

    protected double getChimneyFrontOffset() {
        return CHIMNEY_FRONT_OFFSET;
    }

    protected double getChimneyTopYOffset() {
        return CHIMNEY_TOP_Y;
    }

    protected double getChimneySideOffset() {
        return 0.0D;
    }
    private Vec3 getChimneyTop() {
        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        double rightX = -frontZ;
        double rightZ = frontX;

        return new Vec3(
                this.getX()
                        + frontX * this.getChimneyFrontOffset()
                        + rightX * this.getChimneySideOffset(),
                this.getY() + this.getChimneyTopYOffset(),
                this.getZ()
                        + frontZ * this.getChimneyFrontOffset()
                        + rightZ * this.getChimneySideOffset()
        );
    }

    private void syncTelemetryToClient() {
        this.entityData.set(DATA_BURN_TIME, this.burnTime);
        this.entityData.set(DATA_MAX_BURN_TIME, this.maxBurnTime);
        this.entityData.set(DATA_WATER_AMOUNT, this.waterAmount);
        this.entityData.set(DATA_STEAM_AMOUNT, this.steamAmount);
        this.entityData.set(DATA_BOILER_TEMPERATURE_K, (float) this.boilerTemperatureK);
        this.entityData.set(DATA_HAND_BRAKE, this.handBrakeApplied);
    }

    private void refillPrimarySlotsFromStorage() {
        // Step 6.9.2 and earlier could leave a returned empty bucket sitting in the
        // primary water slot. Move that legacy/output bucket back into storage when
        // possible so a manually loaded water bucket can use the dedicated slot.
        ItemStack primaryWaterStack = this.steamInventory.getStackInSlot(WATER_SLOT);
        if (primaryWaterStack.is(Items.BUCKET)) {
            int bucketOutputSlot = this.findEmptyBucketOutputSlot();
            if (bucketOutputSlot >= 0) {
                this.steamInventory.setStackInSlot(WATER_SLOT, ItemStack.EMPTY);
                this.steamInventory.insertItem(
                        bucketOutputSlot, new ItemStack(Items.BUCKET), false);
            }
        }

        // Step 7.3.4-r3 - demand-driven firebox feed: one item only when burnTime is zero.
        // Reserve fuel: when the dedicated firebox slot runs empty, pull the next
        // valid furnace-fuel stack from the locomotive's 3x3 storage area.
        if (this.burnTime <= 0 && this.steamInventory.getStackInSlot(BURN_SLOT).isEmpty()) {
            for (int slot = 2; slot < INVENTORY_SIZE; slot++) {
                ItemStack stack = this.steamInventory.getStackInSlot(slot);
                if (isFuel(stack)) {
                    ItemStack suppliedFuel = this.steamInventory.extractItem(slot, 1, false);
                    if (!suppliedFuel.isEmpty()) {
                        this.steamInventory.setStackInSlot(BURN_SLOT, suppliedFuel);
                    }
                    break;
                }
            }
        }

        // Step 7.1: the locomotive's own 3x3 reserve remains first priority. Only
        // when it is exhausted does a physically coupled tender hand over its next
        // fuel stack. Burn timing and fuel validation remain locomotive-owned.
        if (this.burnTime <= 0 && this.steamInventory.getStackInSlot(BURN_SLOT).isEmpty()) {
            SteamTender tender = this.getCoupledSteamTender();
            if (tender != null) {
                ItemStack suppliedFuel = tender.extractFuelStackForLocomotive();
                if (!suppliedFuel.isEmpty()) {
                    this.steamInventory.setStackInSlot(BURN_SLOT, suppliedFuel);
                }
            }
        }
    }

    @Nullable
    private SteamTender getCoupledSteamTender() {
        Entity linked = CouplingManager.resolve(this, this);
        if (!(linked instanceof SteamTender tender) || tender.isRemoved()) {
            return null;
        }

        // Step 7.3.3 - accept reciprocal tender link in either coupler slot.
        //
        // Step 7.1 was written when the tender effectively had one locomotive
        // coupling. Multi-car consists now legitimately use both tender coupling
        // UUID slots. Depending on coupling order/relinking, the locomotive can be
        // stored in either slot. Requiring only the primary slot rejects a real,
        // physically resolved tender and prevents coal auto-feed.
        //
        // Keep the original safety property: CouplingManager.resolve() above must
        // first resolve THIS locomotive to THIS tender, and the tender must also
        // point back to this locomotive in one of its two actual coupler slots.
        UUID locomotiveUuid = this.getUUID();
        boolean reciprocalPrimary =
                locomotiveUuid.equals(tender.getCoupledRollingStockUuid());
        boolean reciprocalSecondary =
                locomotiveUuid.equals(tender.getSecondaryCoupledRollingStockUuid());

        return reciprocalPrimary || reciprocalSecondary
                ? tender
                : null;
    }

    /**
     * Step 7.1 tender feedwater. Local locomotive buckets are always consumed
     * first; the tender only tops the boiler tank after those reserves cannot lift
     * it above the existing 2000 mB automatic-refill threshold.
     */
    private void autoRefillWaterFromCoupledTender() {
        /*
         * Step 7.3.4-r3 - demand-driven tender feedwater.
         *
         * The tender is bulk storage. Normal automatic feedwater only replaces
         * the amount actually missing below the existing 2000 mB locomotive
         * working threshold, with at most 1000 mB transferred per service tick.
         * Emergency overheat quenching remains separate and unchanged.
         */
        if (this.waterAmount >= AUTO_WATER_REFILL_THRESHOLD_MB) {
            return;
        }

        SteamTender tender = this.getCoupledSteamTender();
        if (tender == null) {
            return;
        }

        int room = WATER_CAPACITY_MB - this.waterAmount;
        int deficit = AUTO_WATER_REFILL_THRESHOLD_MB - this.waterAmount;
        int requested = Math.min(1000, Math.min(room, deficit));
        if (requested <= 0) {
            return;
        }

        int supplied = tender.drainWaterForLocomotive(requested);
        if (supplied <= 0) {
            return;
        }

        this.waterAmount += supplied;
        this.applyFeedwaterCooling(supplied);
    }
    /**
     * A still-overheating boiler may take one emergency bucket-equivalent from the
     * tender. This mirrors the locomotive's existing emergency bucket quench while
     * avoiding a one-tick dump of the tender's entire reservoir.
     */
    private void quenchFromCoupledTender() {
        SteamTender tender = this.getCoupledSteamTender();
        if (tender == null) {
            return;
        }

        int supplied = tender.drainWaterForLocomotive(1000);
        if (supplied <= 0) {
            return;
        }
        this.waterAmount = Math.min(WATER_CAPACITY_MB, this.waterAmount + supplied);
        this.applyFeedwaterCooling(supplied);
    }

    /**
     * Normal reserve feedwater is demand-driven: once the tank reaches 2000 mB or
     * lower, pull queued storage buckets until it climbs back above the threshold.
     * Buckets are consumed directly from their 3x3 storage slots so the automation
     * cannot be blocked by the dedicated water slot.
     */
    private void autoRefillWaterFromStorage() {
        int guard = INVENTORY_SIZE;
        while (guard-- > 0 && this.waterAmount <= AUTO_WATER_REFILL_THRESHOLD_MB) {
            int slot = this.findStoredWaterBucketSlot();
            if (slot < 0 || !this.consumeStoredWaterBucket(slot, false)) {
                break;
            }
        }
    }

    /**
     * During an overheat, use every reserve water bucket in the 3x3 locomotive
     * inventory as emergency cooling. A full tank does not block this response:
     * the additional water is treated as overflow, but each bucket still removes
     * heat and returns an empty bucket to the same storage slot.
     */
    private void dumpQueuedWaterForEmergencyCooling() {
        for (int slot = 2; slot < INVENTORY_SIZE; slot++) {
            if (this.steamInventory.getStackInSlot(slot).is(Items.WATER_BUCKET)) {
                this.consumeStoredWaterBucket(slot, true);
            }
        }
    }

    private int findStoredWaterBucketSlot() {
        for (int slot = 2; slot < INVENTORY_SIZE; slot++) {
            if (this.steamInventory.getStackInSlot(slot).is(Items.WATER_BUCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean consumeStoredWaterBucket(int slot, boolean emergencyQuench) {
        if (slot < 2 || slot >= INVENTORY_SIZE) {
            return false;
        }
        if (!this.steamInventory.getStackInSlot(slot).is(Items.WATER_BUCKET)) {
            return false;
        }
        if (!emergencyQuench && this.waterAmount > WATER_CAPACITY_MB - 1000) {
            return false;
        }

        this.waterAmount = Math.min(WATER_CAPACITY_MB, this.waterAmount + 1000);
        this.applyFeedwaterCooling();

        // A vanilla water bucket is non-stackable, so the exact source storage
        // slot can safely become the returned empty bucket.
        this.steamInventory.setStackInSlot(slot, new ItemStack(Items.BUCKET));
        return true;
    }

    private boolean consumePrimaryWaterContainer(boolean emergencyQuench) {
        ItemStack water = this.steamInventory.getStackInSlot(WATER_SLOT);
        if (!water.is(Items.WATER_BUCKET)) {
            return false;
        }
        if (!emergencyQuench && this.waterAmount > WATER_CAPACITY_MB - 1000) {
            return false;
        }

        this.waterAmount = Math.min(WATER_CAPACITY_MB, this.waterAmount + 1000);
        this.applyFeedwaterCooling();

        int bucketOutputSlot = this.findEmptyBucketOutputSlot();
        if (bucketOutputSlot >= 0) {
            this.steamInventory.setStackInSlot(WATER_SLOT, ItemStack.EMPTY);
            this.steamInventory.insertItem(
                    bucketOutputSlot, new ItemStack(Items.BUCKET), false);
        } else {
            // If storage is completely full, keep the returned empty bucket in the
            // dedicated slot rather than refusing an emergency quench.
            this.steamInventory.setStackInSlot(WATER_SLOT, new ItemStack(Items.BUCKET));
        }
        return true;
    }

    private void applyFeedwaterCooling() {
        this.applyFeedwaterCooling(1000);
    }

    private void applyFeedwaterCooling(int suppliedWaterMb) {
        if (this.boilerTemperatureK > DEFAULT_BOILER_TEMPERATURE_K && suppliedWaterMb > 0) {
            double cooling = WATER_BUCKET_COOLING_K * Math.min(1000, suppliedWaterMb) / 1000.0D;
            this.boilerTemperatureK = Math.max(
                    DEFAULT_BOILER_TEMPERATURE_K,
                    this.boilerTemperatureK - cooling);
        }
    }

    private int findEmptyBucketOutputSlot() {
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        for (int slot = 2; slot < INVENTORY_SIZE; slot++) {
            ItemStack remainder = this.steamInventory.insertItem(slot, emptyBucket, true);
            if (remainder.isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Called only after networking verifies the sender is the driver. */
    public void setDriverControls(boolean forward, boolean reverse, boolean brake) {
        this.forwardInput = forward;
        this.reverseInput = reverse;
        this.brakeInput = brake;
    }

    /**
     * Server-side consist helpers use this to distinguish pulling from pushing.
     * Reverse input is authoritative for the current powered travel direction.
     */
    public boolean isReverseDriveRequested() {
        return this.reverseInput && !this.forwardInput;
    }

    /**
     * Stable physical consist direction used by coupling controllers.
     *
     * Driver key state is not enough here: when the player releases S the train
     * can keep coasting backwards for many ticks.  The old reverse controller
     * immediately fell back to forward/pulling semantics during that coast,
     * which is exactly what the Step 7.3.2c flight recorder caught.
     *
     * While moving, determine direction from actual locomotive motion relative
     * to the locomotive nose and retain the last reliable rail-facing travel
     * sign through the brief perpendicular part of a curve.  Only when nearly
     * stopped do live W/S controls choose the next intended direction.
     */
    public boolean isReverseConsistTravel() {
        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(
                motion.x * motion.x + motion.z * motion.z);

        if (horizontalSpeed > STOP_EPSILON) {
            double radians = Math.toRadians(this.getTrainFacingYaw());
            double frontX = Math.sin(radians);
            double frontZ = -Math.cos(radians);
            double motionDotFront = motion.x * frontX + motion.z * frontZ;

            if (motionDotFront > horizontalSpeed * 0.20D) {
                return false;
            }
            if (motionDotFront < -horizontalSpeed * 0.20D) {
                return true;
            }

            // On the center of a 90-degree curve the motion can be nearly
            // perpendicular to body yaw.  travelDirectionSign is deliberately
            // latched by updateTrainFacingFromRail() for this exact case.
            return this.travelDirectionSign < 0;
        }

        if (this.forwardInput ^ this.reverseInput) {
            return this.reverseInput;
        }

        return this.travelDirectionSign < 0;
    }

    /** Toggles the classic C-key parking/hand brake. Called after driver validation. */
    public void toggleHandBrake(ServerPlayer player) {
        this.handBrakeApplied = !this.handBrakeApplied;
        this.entityData.set(DATA_HAND_BRAKE, this.handBrakeApplied);
        if (this.handBrakeApplied) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        player.displayClientMessage(
                Component.translatable(this.handBrakeApplied
                        ? "message.traincraft.hand_brake.applied"
                        : "message.traincraft.hand_brake.released"),
                true
        );
    }

    /** Plays this locomotive's original Traincraft steam whistle for nearby players. */
    public void playWhistle(ServerPlayer player) {
        // Keep the server authoritative and prevent accidental key-repeat spam.
        if (this.tickCount - this.lastWhistleTick < 8) {
            return;
        }
        this.lastWhistleTick = this.tickCount;

        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 whistlePos = this.getChimneyTop();
            serverLevel.playSound(
                    null, whistlePos.x, whistlePos.y, whistlePos.z,
                    TCSounds.STEAM_HORN.get(), SoundSource.NEUTRAL,
                    1.35F, 1.0F
            );
        }
    }

    public boolean isHandBrakeApplied() {
        return this.level().isClientSide ? this.entityData.get(DATA_HAND_BRAKE) : this.handBrakeApplied;
    }

    public float getTrainFacingYaw() {
        return this.trainFacingInitialized ? this.trainFacingYaw : this.getYRot();
    }

    /*
     * Step 7.4.6 - environmental compatibility (7.4.5a snow behavior frozen).
     *
     * World interaction only: this does not alter speed, traction, braking,
     * grade physics, coupling, derailment, or consist mass.
     *
     * The locomotive clears only vanilla snow layers and powder snow in a
     * three-block-wide path immediately ahead of its physical front while
     * actually moving forward. Reversing does not plow from the rear.
     */
    private static final double[] DEFAULT_FRONT_SNOW_PLOW_FORWARD_SAMPLES =
            {0.90D, 1.65D};
    private static final double[] DEFAULT_FRONT_SNOW_PLOW_SIDE_SAMPLES =
            {-1.0D, 0.0D, 1.0D};

    /*
     * Step 8.5.4a-r1 - model-aware snow-clearance envelope hooks.
     * Defaults preserve the frozen shared behavior exactly.
     */
    protected double[] getFrontSnowPlowForwardSamples() {
        return DEFAULT_FRONT_SNOW_PLOW_FORWARD_SAMPLES;
    }

    protected double[] getFrontSnowPlowSideSamples() {
        return DEFAULT_FRONT_SNOW_PLOW_SIDE_SAMPLES;
    }

    protected int getFrontSnowPlowClearHeightBlocks() {
        return 2;
    }

    /*
     * Step 8.5.4c - universal model-aware front interaction origin.
     *
     * Values are renderer/mesh-derived gameplay calibrations. They intentionally
     * target the visible nose approximately rather than changing any renderer.
     * A4 keeps its accepted subclass override; its switch entry is a fallback.
     */
    protected double getFrontSnowPlowOriginForwardOffset() {
        return switch (this.getClass().getSimpleName()) {
            case "A4MallardSteamLocomotive" -> 5.2993D;
            case "AdlerSteamLocomotive" -> 1.0307D;
            case "AlcoSc4SteamLocomotive" -> 3.6906D;
            case "Alice040SteamLocomotive" -> 1.9128D;
            case "Berkshire1225SteamLocomotive" -> 4.9441D;
            case "Berkshire765SteamLocomotive" -> 4.9441D;
            case "Br80SteamLocomotive" -> 1.4753D;
            case "BrBlack5SteamLocomotive" -> 3.0750D;
            case "BrBritanniaSteamLocomotive" -> 3.6906D;
            case "C11SteamLocomotive" -> 3.0378D;
            case "C41080SteamLocomotive" -> 3.3500D;
            case "C41SteamLocomotive" -> 3.6656D;
            case "C41TSteamLocomotive" -> 3.6003D;
            case "C62SteamLocomotive" -> 5.4125D;
            case "CherepanovSteamLocomotive" -> 1.0378D;
            case "Climax2SteamLocomotive" -> 1.4514D;
            case "ClimaxSteamLocomotive" -> 2.0815D;
            case "CoronationClassSteamLocomotive" -> 5.9313D;
            case "D51LongSteamLocomotive" -> 5.3500D;
            case "D51SteamLocomotive" -> 5.3500D;
            case "FourFourZeroSteamLocomotive" -> 1.5910D;
            case "FowlerSteamLocomotive" -> 2.9156D;
            case "Glyn042tSteamLocomotive" -> 2.0253D;
            case "Gs4SteamLocomotive" -> 6.1063D;
            case "Gwr101ClassSteamLocomotive" -> 2.4128D;
            case "Gwr42xxSteamLocomotive" -> 4.2003D;
            case "Gwr72xxSteamLocomotive" -> 4.9128D;
            case "HallClassSteamLocomotive" -> 4.6253D;
            case "J50SteamLocomotive" -> 2.8188D;
            case "KingClassSteamLocomotive" -> 5.3503D;
            case "Lssp7SteamLocomotive" -> 0.7878D;
            case "MidlandCompoundSteamLocomotive" -> 3.6128D;
            case "MilwClassASteamLocomotive" -> 4.4157D;
            case "MogulSteamLocomotive" -> 1.5310D;
            case "PannierSteamLocomotive" -> 3.5344D;
            case "PeSteamLocomotive" -> 3.1003D;
            case "RwType2SteamLocomotive" -> 1.9128D;
            case "RwType3SteamLocomotive" -> 2.4753D;
            case "S100UkSteamLocomotive" -> 2.1656D;
            case "S100UsSteamLocomotive" -> 2.1625D;
            case "SentinelY3SteamLocomotive" -> 1.1316D;
            case "Shay3TruckSteamLocomotive" -> 1.8503D;
            case "SkookumSteamLocomotive" -> 1.8503D;
            case "Southern1102SteamLocomotive" -> 3.6031D;
            case "StarClassSteamLocomotive" -> 1.4625D;
            case "TwoSixTwoTSteamLocomotive" -> 2.6440D;
            case "Vb040SteamLocomotive" -> 0.8440D;
            case "VbShay2SteamLocomotive" -> 2.2017D;
            case "VbShaySteamLocomotive" -> 1.8565D;
            case "Wwcp062tSteamLocomotive" -> 4.1003D;
            case "Br01SteamLocomotive" -> 3.6000D;
            case "ShaySteamLocomotive" -> 1.3750D;
            case "ForneySteamLocomotive" -> 2.1060D;
            case "SteamSnowPlowLocomotive" -> 4.0375D;
            case "HeavySteamLocomotive" -> 2.8750D;
            case "UssrSteamLocomotive" -> 3.4375D;
            default -> 0.0D;
        };
    }

    /*
     * Snow, soft-vegetation clearing, and mob/player impacts share one calibrated
     * visible-front origin. Subclasses may split these later if a model needs it.
     */
    protected double getFrontInteractionOriginForwardOffset() {
        return this.getFrontSnowPlowOriginForwardOffset();
    }

    private void tickFrontSnowPlow() {
        if (this.level().isClientSide) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontalSpeed < 0.005D) {
            return;
        }

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        double motionDotFront = motion.x * frontX + motion.z * frontZ;

        // Physical forward travel only; held controls do not count during rollback.
        if (motionDotFront <= 0.001D) {
            return;
        }

        double sideX = -frontZ;
        double sideZ = frontX;
        // Model-aware envelope; shared defaults remain the original 3-wide / 1.65 reach.
        double[] forwardSamples = this.getFrontSnowPlowForwardSamples();
        double[] sideSamples = this.getFrontSnowPlowSideSamples();
        double originForward = this.getFrontSnowPlowOriginForwardOffset();

        for (double forward : forwardSamples) {
            for (double side : sideSamples) {
                double effectiveForward = originForward + forward;
                double sampleX = this.getX() + frontX * effectiveForward + sideX * side;
                double sampleZ = this.getZ() + frontZ * effectiveForward + sideZ * side;
                net.minecraft.core.BlockPos base =
                        net.minecraft.core.BlockPos.containing(sampleX, this.getY(), sampleZ);

                // Shared default remains two blocks high; subclasses may override.
                for (int yOffset = 0;
                        yOffset < this.getFrontSnowPlowClearHeightBlocks();
                        yOffset++) {
                    net.minecraft.core.BlockPos pos = base.above(yOffset);
                    net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(pos);

                    if (state.is(net.minecraft.world.level.block.Blocks.SNOW)
                            || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                        // No item drops: the pilot is physically throwing snow aside.
                    this.level().setBlock(
                            pos,
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            3);
                    }
                }
            }
        }
    }
    /*
     * Step 7.4.6 - environmental compatibility.
     * Isolated from frozen locomotive movement physics.
     */
    private void tickEnvironmentalCompatibility() {
        if (this.level().isClientSide) {
            return;
        }
    
        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        double motionDotFront = motion.x * frontX + motion.z * frontZ;
    
        if (horizontalSpeed >= 0.005D && motionDotFront > 0.001D) {
            this.clearSoftVegetationAhead(frontX, frontZ);
            this.handleMobImpactsAhead(frontX, frontZ, horizontalSpeed);
        }
    
        this.probeEnvironmentalFluid();
    }
    
    private void clearSoftVegetationAhead(double frontX, double frontZ) {
        double sideX = -frontZ;
        double sideZ = frontX;
        double[] forwardSamples = {0.90D, 1.65D};
        double[] sideSamples = {-1.0D, 0.0D, 1.0D};
        double originForward = this.getFrontInteractionOriginForwardOffset();
    
        for (double forward : forwardSamples) {
            for (double side : sideSamples) {
                double effectiveForward = originForward + forward;
                double sampleX = this.getX() + frontX * effectiveForward + sideX * side;
                double sampleZ = this.getZ() + frontZ * effectiveForward + sideZ * side;
                net.minecraft.core.BlockPos base =
                        net.minecraft.core.BlockPos.containing(sampleX, this.getY(), sampleZ);
    
                for (int yOffset = 0; yOffset <= 2; yOffset++) {
                    net.minecraft.core.BlockPos pos = base.above(yOffset);
                    net.minecraft.world.level.block.state.BlockState state =
                            this.level().getBlockState(pos);
    
                    if (this.isPlowableVegetation(state)) {
                        this.level().setBlock(
                                pos,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                3);
                    }
                }
            }
        }
    }
    
    private boolean isPlowableVegetation(
            net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir()) {
            return false;
        }
    
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
            return true;
        }
    
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
    
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        if (path.contains("leaves") || path.endsWith("_leaf")) {
            return true;
        }
    
        return path.equals("grass")
                || path.equals("tall_grass")
                || path.contains("fern")
                || path.contains("vine")
                || path.contains("bush")
                || path.contains("shrub");
    }
    
    private void handleMobImpactsAhead(
            double frontX,
            double frontZ,
            double horizontalSpeed) {
        double sideX = -frontZ;
        double sideZ = frontX;
        double originForward = this.getFrontInteractionOriginForwardOffset();
    
        net.minecraft.world.phys.AABB searchBox =
                this.getBoundingBox()
                        .move(frontX * originForward, 0.0D, frontZ * originForward)
                        .inflate(2.4D, 1.2D, 2.4D);
    
        java.util.List<net.minecraft.world.entity.LivingEntity> targets =
                this.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class,
                        searchBox,
                        target ->

                                target.isAlive()

                                        && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)

                                        // Step 7.4.7a-r2: do not strike this locomotive's own rider/passengers.

                                        && target.getVehicle() != this

                                        // Survival/adventure players are impact targets.

                                        // Creative and spectator players remain immune.

                                        && (!(target instanceof net.minecraft.world.entity.player.Player)

                                                || (!((net.minecraft.world.entity.player.Player) target)

                                                                .getAbilities().instabuild

                                                        && !((net.minecraft.world.entity.player.Player) target)

                                                                .isSpectator())));
    
        double speedKmh = horizontalSpeed * 72.0D;
    
        for (net.minecraft.world.entity.LivingEntity target : targets) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double forward = dx * frontX + dz * frontZ - originForward;
            double side = dx * sideX + dz * sideZ;
    
            if (forward < 0.35D || forward > 2.35D || Math.abs(side) > 1.45D) {
                continue;
            }
    
            double sideSign =
                    Math.abs(side) > 0.08D
                            ? Math.signum(side)
                            : ((target.getId() & 1) == 0 ? 1.0D : -1.0D);
    
            double sideKick = Math.min(0.42D, 0.16D + horizontalSpeed * 0.90D);
            double frontKick = Math.min(0.25D, 0.08D + horizontalSpeed * 0.55D);
            target.push(
                    frontX * frontKick + sideX * sideKick * sideSign,
                    0.08D,
                    frontZ * frontKick + sideZ * sideKick * sideSign);
    
            /*

             * Step 7.4.7a-r2 - speed-scaled locomotive impacts.

             *

             * < 5 km/h   : push only

             * 5-10 km/h  : light injury, 2-4 hp

             * 10-15 km/h : moderate injury, 4-8 hp

             * 15-20 km/h : heavy injury, 8-14 hp

             * 20-25 km/h : very heavy injury, 14-20 hp

             * >= 25 km/h : lethal-class impact

             *

             * Use the normal Minecraft damage path so standard death handling,

             * drops, statistics, totems, and mod hooks remain in charge.

             */

            if (speedKmh >= 25.0D) {

                target.hurt(this.damageSources().generic(), Float.MAX_VALUE);

            } else if (speedKmh >= 5.0D) {

                double impactDamage;

                if (speedKmh < 10.0D) {

                    impactDamage = 2.0D + (speedKmh - 5.0D) * 0.40D;

                } else if (speedKmh < 15.0D) {

                    impactDamage = 4.0D + (speedKmh - 10.0D) * 0.80D;

                } else if (speedKmh < 20.0D) {

                    impactDamage = 8.0D + (speedKmh - 15.0D) * 1.20D;

                } else {

                    impactDamage = 14.0D + (speedKmh - 20.0D) * 1.20D;

                }



                target.hurt(this.damageSources().generic(), (float) impactDamage);

            }
        }
    }
    
    private void probeEnvironmentalFluid() {
        net.minecraft.core.BlockPos base =
                net.minecraft.core.BlockPos.containing(this.getX(), this.getY(), this.getZ());
    
        String detected = "NONE";
        net.minecraft.resources.ResourceLocation fluidId = null;
        net.minecraft.core.BlockPos[] samples = {base, base.below(), base.above()};
    
        for (net.minecraft.core.BlockPos pos : samples) {
            net.minecraft.world.level.block.state.BlockState blockState =
                    this.level().getBlockState(pos);
            net.minecraft.resources.ResourceLocation blockId =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                            blockState.getBlock());

            // Step 7.4.6b-r3 - Reborn modded liquid blocks can exist even when
            // their vanilla/Forge FluidState is empty. Check the registered
            // block id before the FluidState early-out.
            if (this.isOilFamilyBlock(blockId)) {
                detected = "OIL";
                fluidId = blockId;
                break;
            }

            net.minecraft.world.level.material.FluidState fluidState =
                    this.level().getFluidState(pos);
    
            if (fluidState.isEmpty()) {
                continue;
            }
    
            fluidId =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(
                            fluidState.getType());
    
            if (fluidState.is(net.minecraft.tags.FluidTags.LAVA)) {
                detected = "LAVA";
                break;
            }
            if (this.isOilFamilyFluid(fluidState, fluidId)) {
                detected = "OIL";
                break;
            }
            if (fluidState.is(net.minecraft.tags.FluidTags.WATER)) {
                detected = "WATER";
                break;
            }
        }
    
        if (!detected.equals(this.realismEnvironmentFluidClass)) {
            if (!"NONE".equals(detected)) {
                org.apache.logging.log4j.LogManager
                        .getLogger("TraincraftEnvironment")
                        .info(
                                "[TC-ENV-FLUID] tick=" + this.level().getGameTime()
                                        + " entered=" + detected
                                        + " fluid=" + String.valueOf(fluidId)
                                        + " speedKmh="
                                        + String.format(
                                                java.util.Locale.ROOT,
                                                "%.3f",
                                                this.getDeltaMovement().horizontalDistance() * 72.0D)
                                        + " rail=" + this.getCurrentRailShape());
            }
            this.realismEnvironmentFluidClass = detected;
        }
    }
    
    private boolean isOilFamilyFluid(
            net.minecraft.world.level.material.FluidState fluidState,
            net.minecraft.resources.ResourceLocation fluidId) {
        String[] forgeTags = {
            "oil",
            "crude_oil",
            "diesel",
            "diesel_sulfur",
            "petroleum_gas",
            "plantoil",
            "biodiesel"
        };
    
        for (String tagPath : forgeTags) {
            net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag =
                    net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.FLUID,
                            new net.minecraft.resources.ResourceLocation("forge", tagPath));
            if (fluidState.is(tag)) {
                return true;
            }
        }
    
        if (fluidId == null) {
            return false;
        }
    
        String namespace = fluidId.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String path = fluidId.getPath().toLowerCase(java.util.Locale.ROOT);
        boolean petroleumNamespace =
                namespace.contains("buildcraft")
                        || namespace.equals("immersivepetroleum")
                        || namespace.equals("immersiveengineering");
    
        return petroleumNamespace
                && (path.contains("oil")
                        || path.contains("diesel")
                        || path.contains("petroleum")
                        || path.contains("biodiesel")
                        || path.contains("fuel"));
    }

    private boolean isOilFamilyBlock(net.minecraft.resources.ResourceLocation blockId) {
        if (blockId == null) {
            return false;
        }

        String namespace = blockId.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String path = blockId.getPath().toLowerCase(java.util.Locale.ROOT);
        boolean petroleumNamespace =
                namespace.contains("buildcraft")
                        || namespace.equals("immersivepetroleum")
                        || namespace.equals("immersiveengineering");

        return petroleumNamespace
                && (path.contains("oil")
                        || path.contains("diesel")
                        || path.contains("petroleum")
                        || path.contains("biodiesel")
                        || path.contains("fuel"));
    }

        private void applyDriverInput() {
        if (!this.isOnRails()) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        if (this.handBrakeApplied) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (this.brakeInput || (this.forwardInput && this.reverseInput)) {
            this.brakeTowardStop(motion, horizontalSpeed);
            return;
        }

        if (!this.forwardInput && !this.reverseInput) {
            return;
        }

        // No steam = no traction. W/S must never act like a powered minecart
        // control when the boiler is empty. If the locomotive already has
        // momentum, the attempted drive input only brings it toward a stop.
        if (this.steamAmount <= 0) {
            if (horizontalSpeed > STOP_EPSILON) {
                this.brakeTowardStop(motion, horizontalSpeed);
            } else {
                this.setDeltaMovement(0.0D, motion.y, 0.0D);
            }
            return;
        }

        int requestedDirection =
                this.forwardInput ? 1 : -1;
        double radians =
                Math.toRadians(this.getTrainFacingYaw());
        double frontX =
                Math.sin(radians);
        double frontZ =
                -Math.cos(radians);

        RailShape realismDriveRailShape = this.getCurrentRailShape();
        Vec3 realismUphill =
                this.getRealismRailUphillVector(realismDriveRailShape);
        boolean realismOverloadedUphill =
                this.isRealismOverloadedUphillCommand(
                        requestedDirection,
                        frontX,
                        frontZ,
                        realismDriveRailShape);

        double realismConsistMassForGradeT =
                realismOverloadedUphill
                        ? this.getRealismConsistMassTons()
                        : this.getRealismLocomotiveMassTons();
        double realismAvailableGradeAccelMps2 =
                realismOverloadedUphill
                        ? this.getRealismBaseAccelerationMps2()
                                * this.getRealismTractionFactor(
                                        realismConsistMassForGradeT)
                        : this.getRealismBaseAccelerationMps2();

        if (!realismOverloadedUphill
                && !"NONE".equals(this.realismGradePhysicsState)) {
            this.realismGradePhysicsState = "NONE";
        }

        if (!realismOverloadedUphill) {
            this.realismGradeLowSpeedStallTicks = 0;
        }
        if (!realismOverloadedUphill) {
            this.realismGradeRollbackAssistTicks = 0;
        }
        if (realismOverloadedUphill
                && realismUphill != null
                && this.realismGradeRollbackAssistTicks > 0
                && "ROLLBACK".equals(this.realismGradePhysicsState)) {
            this.setDeltaMovement(
                    -realismUphill.x * REALISM_GRADE_ROLLBACK_ASSIST_BPT,
                    motion.y,
                    -realismUphill.z * REALISM_GRADE_ROLLBACK_ASSIST_BPT);

            this.realismGradeRollbackAssistTicks--;
            this.steamAmount = Math.max(0, this.steamAmount - 1);
            return;
        }

        if (horizontalSpeed > STOP_EPSILON) {
            double motionDotFront =
                    motion.x * frontX + motion.z * frontZ;
            int currentDirection =
                    motionDotFront >= 0.0D ? 1 : -1;
            if (currentDirection != requestedDirection) {
                double motionAlongUphill =
                        realismUphill == null
                                ? 0.0D
                                : motion.x * realismUphill.x
                                        + motion.z * realismUphill.z;

                if (realismOverloadedUphill
                        && realismUphill != null
                        && motionAlongUphill < -STOP_EPSILON) {
                    /*
                     * Step 7.4.2b: the train has already rolled backward.
                     * Do not let the normal command-direction brake pin it
                     * motionless on the grade. Keep its natural downhill
                     * motion; normal rail physics continues the rollback.
                     */
                    this.steamAmount = Math.max(0, this.steamAmount - 1);
                    return;
                }

                this.brakeTowardStop(motion, horizontalSpeed);
                return;
            }
        }

        double directionX;
        double directionZ;

        if (horizontalSpeed > STOP_EPSILON) {
            directionX = motion.x / horizontalSpeed;
            directionZ = motion.z / horizontalSpeed;
        } else {
            directionX = frontX * requestedDirection;
            directionZ = frontZ * requestedDirection;
        }

        double maxSpeed = requestedDirection > 0
                ? this.getRealismMaxForwardSpeedBlocksPerTick()
                : this.getRealismMaxReverseSpeedBlocksPerTick();
        /*
         * Step 7.4.1b - mass-aware tractive effort.
         *
         * Scale ONLY locomotive-powered acceleration. Maximum speed, braking,
         * coupler governors, derailment, and the 7.3.2n reverse crest escape
         * floor below remain unchanged.
         */
        double realismConsistMassT = this.getRealismConsistMassTons();
        double realismTractionFactor =
                this.getRealismTractionFactor(realismConsistMassT);
        double effectiveAccelerationPerTick =
                (this.getRealismBaseAccelerationMps2() / 400.0D)
                        * realismTractionFactor;
        double newSpeed =
                Math.min(
                        maxSpeed,
                        horizontalSpeed + effectiveAccelerationPerTick);

        if (realismOverloadedUphill && realismUphill != null) {
            double deficitMps2 =
                    REALISM_GRADE_HOLD_ACCEL_MPS2
                            - realismAvailableGradeAccelMps2;
            double deficitPerTick =
                    Math.max(0.0D, deficitMps2)
                            * REALISM_MPS2_TO_BPT_PER_TICK;

            /*
             * Fixed tractive effort is now below the grade requirement.
             * The locomotive may keep its existing uphill momentum, but it
             * cannot accelerate uphill. Its calculated negative margin eats
             * speed until the stall threshold is reached.
             */
            newSpeed =
                    Math.max(
                            0.0D,
                            horizontalSpeed - deficitPerTick);

            this.logRealismGradeStall(
                    realismConsistMassForGradeT,
                    realismAvailableGradeAccelMps2,
                    horizontalSpeed * 72.0D);

            double motionAlongUphill =
                    motion.x * realismUphill.x
                            + motion.z * realismUphill.z;

            if (horizontalSpeed <= REALISM_GRADE_LOW_SPEED_STALL_BPT
                    && motionAlongUphill > STOP_EPSILON) {
                this.realismGradeLowSpeedStallTicks++;
            } else if (horizontalSpeed > REALISM_GRADE_LOW_SPEED_STALL_BPT) {
                this.realismGradeLowSpeedStallTicks = 0;
            }

            boolean realismGradeLowSpeedStallLatched =
                    this.realismGradeLowSpeedStallTicks
                            >= REALISM_GRADE_LOW_SPEED_STALL_TICKS;

            if (horizontalSpeed <= REALISM_GRADE_STALL_SPEED_BPT
                    || motionAlongUphill <= STOP_EPSILON
                    || realismGradeLowSpeedStallLatched) {
                this.beginRealismGradeRollback(
                        realismUphill,
                        motion,
                        realismConsistMassForGradeT,
                        realismAvailableGradeAccelMps2);

                this.realismGradeLowSpeedStallTicks = 0;

                /*
                 * Driver is still working steam against an impossible load,
                 * so retain the existing one-unit/tick steam consumption.
                 */
                this.steamAmount = Math.max(0, this.steamAmount - 1);
                return;
            }
        }

        /*
         * Step 7.3.2n - reverse uphill crest anti-deadlock.
         *
         * The 7.3.2m crest recorder caught the locomotive with reverse held,
         * steam still available and no governor limit, yet vanilla ascending-rail
         * projection repeatedly reduced horizontal motion to about 0.00065 b/t.
         * Normal Traincraft acceleration could not escape that equilibrium.
         *
         * While S is actively commanding reverse on an ascending rail, guarantee
         * only enough speed to escape that numeric deadlock. Flat reverse and all
         * forward movement remain unchanged.
         */
        RailShape driveRailShape = this.getCurrentRailShape();
        boolean reverseUphill =
                requestedDirection < 0
                        && (driveRailShape == RailShape.ASCENDING_NORTH
                        || driveRailShape == RailShape.ASCENDING_SOUTH
                        || driveRailShape == RailShape.ASCENDING_EAST
                        || driveRailShape == RailShape.ASCENDING_WEST);

        if (reverseUphill && !realismOverloadedUphill && newSpeed < REVERSE_UPHILL_MIN_TRACTION_SPEED) {
            newSpeed = REVERSE_UPHILL_MIN_TRACTION_SPEED;
        }
        this.setDeltaMovement(directionX * newSpeed, motion.y, directionZ * newSpeed);

        // Powered movement consumes the boiler's buffered steam.
        this.steamAmount = Math.max(0, this.steamAmount - 1);
    }

    /**
     * Keep the locomotive from stretching a coupled tender out on fast downhill
     * transitions. Instead of allowing the leader to keep accelerating while the
     * follower is still resolving the previous slope/curve, progressively trim the
     * locomotive speed until the tender closes the coupler gap again.
     */
    private void applyCoupledConsistGovernor() {
        Entity linked = CouplingManager.resolve(this, this);
        if (!(linked instanceof SteamTender tender) || linked.isRemoved()) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontalSpeed <= STOP_EPSILON) {
            return;
        }

        /*
         * Preserve the original Step 7.0.3 locomotive->first-tender governor.
         * The control flow is written without an early return so the new chained
         * tail governor can also be evaluated when tender #1 itself is healthy.
         */
        double allowedSpeed = horizontalSpeed;
        double distance = this.position().distanceTo(tender.position());
        boolean curveTransition = this.isOnSharpCurve() || tender.isOnSharpCurve();

        /*
         * Step 7.3.0: preserve every Step 7.2.1m tender threshold exactly, but
         * shift the governor by the follower model's real coupler-touch target.
         * The normal tender target is still 2.10 blocks, so its delta is zero.
         */
        double primaryTouchTarget = tender.getCouplerTouchTargetDistance(this);

        // Step 7.3.2c diagnostic-only governor summary state.
        String governorReason = "NONE";
        int governorHop = 0;
        double governorDistance = distance;
        double governorTarget = primaryTouchTarget;
        double governorCandidate = allowedSpeed;

        double primaryTargetDelta = primaryTouchTarget - 2.10D;
        double governorStart = (curveTransition
                ? COUPLED_CURVE_GOVERNOR_START_DISTANCE
                : COUPLED_GOVERNOR_START_DISTANCE) + primaryTargetDelta;
        double governorFull = (curveTransition
                ? COUPLED_CURVE_GOVERNOR_FULL_DISTANCE
                : COUPLED_GOVERNOR_FULL_DISTANCE) + primaryTargetDelta;

        /*
         * Step 7.3.2k - forward stretch governors are pull-only.
         * Reverse push uses the dedicated compression governor below;
         * applying the forward stretch clamp in reverse can hard-brake
         * the locomotive during a legitimate curve/handoff separation.
         */
        if (!this.isReverseConsistTravel() && distance > governorStart) {
            Vec3 tenderMotion = tender.getDeltaMovement();
            double tenderHorizontalSpeed = Math.sqrt(
                    tenderMotion.x * tenderMotion.x + tenderMotion.z * tenderMotion.z);

            double stretch = Mth.clamp(
                    (distance - governorStart) / (governorFull - governorStart),
                    0.0D, 1.0D);
            double retainedFraction = 1.0D - stretch * (1.0D - COUPLED_GOVERNOR_MIN_RETAIN);
            double distanceCap = this.getRealismMaxForwardSpeedBlocksPerTick() * retainedFraction;
            double partnerCap = tenderHorizontalSpeed + COUPLED_GOVERNOR_TENDER_MARGIN;
            double firstTenderAllowed = Math.max(distanceCap, partnerCap);

            if (distance >= governorFull) {
                firstTenderAllowed = Math.max(0.08D, partnerCap);
            }
            if (firstTenderAllowed < allowedSpeed) {
                governorReason = "PRIMARY_STRETCH";
                governorHop = 0;
                governorDistance = distance;
                governorTarget = primaryTouchTarget;
                governorCandidate = firstTenderAllowed;
            }
            allowedSpeed = Math.min(allowedSpeed, firstTenderAllowed);
        }

        /*
         * Step 7.3.2b reverse push governor.  Pulling stretches a coupler;
         * reversing compresses it.  If the first tender cannot clear a hill or
         * corner as quickly as the locomotive, trim reverse power before the
         * locomotive physically enters the tender model.
         */
        /*
         * Step 7.3.2j - curve-aware reverse compression target.
         *
         * Straight primary touch target is 2.10 blocks, but opposite legs
         * of a vanilla curve legitimately produce much shorter Euclidean
         * center distance. Reuse the existing model-adjusted 1.48 curve
         * governor reference so normal curve geometry is not classified as
         * severe reverse compression.
         */
        double reverseCompressionTarget = curveTransition
                ? COUPLED_CURVE_GOVERNOR_START_DISTANCE + primaryTargetDelta
                : primaryTouchTarget;

        if (this.isReverseConsistTravel()
                && distance < reverseCompressionTarget - REVERSE_COMPRESSION_GOVERNOR_START) {
            Vec3 tenderMotion = tender.getDeltaMovement();
            double tenderHorizontalSpeed = Math.sqrt(
                    tenderMotion.x * tenderMotion.x + tenderMotion.z * tenderMotion.z);
            double compression = reverseCompressionTarget - distance;
            double severity = Mth.clamp(
                    (compression - REVERSE_COMPRESSION_GOVERNOR_START)
                            / (REVERSE_COMPRESSION_GOVERNOR_FULL
                            - REVERSE_COMPRESSION_GOVERNOR_START),
                    0.0D, 1.0D);
            double retainedFraction = 1.0D - severity * 0.90D;
            double distanceCap = this.getRealismMaxReverseSpeedBlocksPerTick() * retainedFraction;
            double partnerCap = tenderHorizontalSpeed + REVERSE_COMPRESSION_PARTNER_MARGIN;
            double compressionAllowed = Math.max(distanceCap, partnerCap);
            if (severity >= 1.0D) {
                compressionAllowed = Math.max(REVERSE_COMPRESSION_MIN_SPEED, partnerCap);
            }
            if (compressionAllowed < allowedSpeed) {
                governorReason = "PRIMARY_REVERSE_COMPRESSION";
                governorHop = 0;
                governorDistance = distance;
                governorTarget = reverseCompressionTarget;
                governorCandidate = compressionAllowed;
            }
            allowedSpeed = Math.min(allowedSpeed, compressionAllowed);
        }

        /*
         * Step 7.2.1f: walk the rear/secondary chain and watch every tender gap.
         * Full power or downhill gravity can no longer let the locomotive keep
         * accelerating merely because tender #1 is still close.  If any rear
         * segment stretches, the locomotive eases down toward that follower's
         * speed until the rail-owned catch-up controller closes the gap again.
         */
        SteamTender segmentLeader = tender;
        for (int hop = 0; hop < CHAINED_GOVERNOR_MAX_HOPS; hop++) {
            Entity rear = CouplingManager.resolveSecondary(segmentLeader, segmentLeader);
            if (!(rear instanceof SteamTender follower) || follower.isRemoved()) {
                break;
            }

            double segmentDistance = segmentLeader.position().distanceTo(follower.position());
            double chainedTargetDelta =
                    follower.getCouplerTouchTargetDistance(segmentLeader) - 2.05D;
            double chainedGovernorStart =
                    CHAINED_GOVERNOR_START_DISTANCE + chainedTargetDelta;
            double chainedGovernorFull =
                    CHAINED_GOVERNOR_FULL_DISTANCE + chainedTargetDelta;

            if (!this.isReverseConsistTravel() && segmentDistance > chainedGovernorStart) {
                Vec3 followerMotion = follower.getDeltaMovement();
                double followerHorizontalSpeed = Math.sqrt(
                        followerMotion.x * followerMotion.x
                                + followerMotion.z * followerMotion.z);

                double stretch = Mth.clamp(
                        (segmentDistance - chainedGovernorStart)
                                / (chainedGovernorFull - chainedGovernorStart),
                        0.0D, 1.0D);
                double retainedFraction = 1.0D
                        - stretch * (1.0D - CHAINED_GOVERNOR_MIN_RETAIN);
                double distanceCap = this.getRealismMaxForwardSpeedBlocksPerTick() * retainedFraction;
                double followerCap = followerHorizontalSpeed
                        + CHAINED_GOVERNOR_FOLLOWER_MARGIN;
                double segmentAllowed = Math.max(distanceCap, followerCap);

                if (segmentDistance >= chainedGovernorFull) {
                    segmentAllowed = Math.max(
                            CHAINED_GOVERNOR_MIN_MOVING_SPEED,
                            followerCap);
                }

                if (segmentAllowed < allowedSpeed) {
                    governorReason = "CHAIN_STRETCH";
                    governorHop = hop + 1;
                    governorDistance = segmentDistance;
                    governorTarget = follower.getCouplerTouchTargetDistance(segmentLeader);
                    governorCandidate = segmentAllowed;
                }
                allowedSpeed = Math.min(allowedSpeed, segmentAllowed);
            }

            /*
             * In reverse every later vehicle is being pushed.  Watch the exact
             * model-aware touch target and slow the locomotive whenever any
             * segment compresses instead of waiting for model collision.
             */
            if (this.isReverseConsistTravel()) {
                double segmentTouchTarget =
                        follower.getCouplerTouchTargetDistance(segmentLeader);
                if (segmentDistance
                        < segmentTouchTarget - REVERSE_COMPRESSION_GOVERNOR_START) {
                    Vec3 followerMotion = follower.getDeltaMovement();
                    double followerHorizontalSpeed = Math.sqrt(
                            followerMotion.x * followerMotion.x
                                    + followerMotion.z * followerMotion.z);
                    double compression = segmentTouchTarget - segmentDistance;
                    double severity = Mth.clamp(
                            (compression - REVERSE_COMPRESSION_GOVERNOR_START)
                                    / (REVERSE_COMPRESSION_GOVERNOR_FULL
                                    - REVERSE_COMPRESSION_GOVERNOR_START),
                            0.0D, 1.0D);
                    double retainedFraction = 1.0D - severity * 0.90D;
                    double distanceCap =
                            this.getRealismMaxReverseSpeedBlocksPerTick() * retainedFraction;
                    double partnerCap =
                            followerHorizontalSpeed + REVERSE_COMPRESSION_PARTNER_MARGIN;
                    double compressionAllowed = Math.max(distanceCap, partnerCap);
                    if (severity >= 1.0D) {
                        compressionAllowed = Math.max(
                                REVERSE_COMPRESSION_MIN_SPEED, partnerCap);
                    }
                    if (compressionAllowed < allowedSpeed) {
                        governorReason = "CHAIN_REVERSE_COMPRESSION";
                        governorHop = hop + 1;
                        governorDistance = segmentDistance;
                        governorTarget = segmentTouchTarget;
                        governorCandidate = compressionAllowed;
                    }
                    allowedSpeed = Math.min(allowedSpeed, compressionAllowed);
                }
            }

            segmentLeader = follower;
        }

        this.logLocomotiveGovernorDecision(
                tender, horizontalSpeed, allowedSpeed,
                governorReason, governorHop,
                governorDistance, governorTarget, governorCandidate);

        if (horizontalSpeed > allowedSpeed) {
            double scale = allowedSpeed / horizontalSpeed;
            this.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
        }
    }


    /**
     * Step 7.3.2c diagnostic-only locomotive governor recorder.  One row is
     * written per coupled server tick and identifies which segment, if any,
     * actually limited locomotive speed.
     */
    private void logLocomotiveGovernorDecision(
            SteamTender firstTender, double inputSpeed, double allowedSpeed,
            String reason, int hop, double limitingDistance,
            double limitingTarget, double candidateAllowed) {

        Vec3 motion = this.getDeltaMovement();
        Vec3 tenderMotion = firstTender.getDeltaMovement();
        double firstDistance = this.position().distanceTo(firstTender.position());
        double firstTarget = firstTender.getCouplerTouchTargetDistance(this);
        double firstTenderSpeed = Math.sqrt(
                tenderMotion.x * tenderMotion.x + tenderMotion.z * tenderMotion.z);

        String header =
                "gameTick,entityTick,locoId,forwardInput,reverseInput,reverseRequested,reverseTravel,brakeInput,handBrake,"
                        + "steam,inputSpeedBpt,inputSpeedKmh,allowedSpeedBpt,allowedSpeedKmh,"
                        + "reason,hop,limitingDistance,limitingTarget,limitingError,candidateAllowed,"
                        + "firstTenderId,firstTenderType,firstDistance,firstTarget,firstError,firstTenderSpeedBpt,firstTenderSpeedKmh,"
                        + "locoX,locoY,locoZ,locoDx,locoDy,locoDz,"
                        + "tenderX,tenderY,tenderZ,tenderDx,tenderDy,tenderDz";

        String row = String.format(java.util.Locale.ROOT,
                "%d,%d,%d,%s,%s,%s,%s,%s,%s,"
                        + "%d,%.6f,%.3f,%.6f,%.3f,"
                        + "%s,%d,%.6f,%.6f,%.6f,%.6f,"
                        + "%d,%s,%.6f,%.6f,%.6f,%.6f,%.3f,"
                        + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
                        + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                this.level().getGameTime(), this.tickCount, this.getId(),
                Boolean.toString(this.forwardInput), Boolean.toString(this.reverseInput),
                Boolean.toString(this.isReverseDriveRequested()),
                Boolean.toString(this.isReverseConsistTravel()),
                Boolean.toString(this.brakeInput), Boolean.toString(this.handBrakeApplied),
                this.steamAmount,
                inputSpeed, inputSpeed * 72.0D,
                allowedSpeed, allowedSpeed * 72.0D,
                reason, hop,
                limitingDistance, limitingTarget, limitingDistance - limitingTarget,
                candidateAllowed,
                firstTender.getId(), firstTender.getClass().getSimpleName(),
                firstDistance, firstTarget, firstDistance - firstTarget,
                firstTenderSpeed, firstTenderSpeed * 72.0D,
                this.getX(), this.getY(), this.getZ(),
                motion.x, motion.y, motion.z,
                firstTender.getX(), firstTender.getY(), firstTender.getZ(),
                tenderMotion.x, tenderMotion.y, tenderMotion.z);

        TrainMovementDebug.append(TrainMovementDebug.GOVERNOR_FILE, header, row);
    }

    private void brakeTowardStop(Vec3 motion, double horizontalSpeed) {
        if (horizontalSpeed <= BRAKE_PER_TICK + STOP_EPSILON) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double newSpeed = Math.max(0.0D, horizontalSpeed - BRAKE_PER_TICK);
        double scale = newSpeed / horizontalSpeed;
        this.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
    }

    private void updateTrainFacingFromRail() {
        RailShape shape = this.getCurrentRailShape();
        if (shape == null) {
            return;
        }

        /*
         * STEP_9_3F_T1A_R11_SMALL_SWITCH_CONTINUOUS_FACING_OWNER
         *
         * r10 trace proof showed that physical motion remained continuous
         * while proxy RailShape facing changed trainFacingYaw from -180 to 0
         * on the first Small-switch tick. applyDriverInput() runs before this
         * method on the next tick, so that 180-degree polarity change makes
         * the same held driver command oppose the current motion and pull the
         * locomotive back toward its start.
         *
         * Give the assembly-owned continuous path first refusal. The r10 path
         * helper already resolves the two equivalent 180-degree yaw solutions
         * against the previously authoritative locomotive nose. If it returns
         * a pose, do NOT let the local gag/proxy RailShape overwrite it.
         */
        float tcContinuousPrevious = this.getTrainFacingYaw();
        Float tcContinuousYaw =
                traincraft.block.track.LegacyContinuousTrackPath
                        .continuousFacingYaw(this, tcContinuousPrevious);
        if (tcContinuousYaw != null) {
            Vec3 tcContinuousMotion = this.getDeltaMovement();
            double tcContinuousSpeed = Math.sqrt(
                    tcContinuousMotion.x * tcContinuousMotion.x
                            + tcContinuousMotion.z * tcContinuousMotion.z);

            if (tcContinuousSpeed > FACING_MOTION_EPSILON) {
                double tcRadians = Math.toRadians(tcContinuousPrevious);
                double tcFrontX = Math.sin(tcRadians);
                double tcFrontZ = -Math.cos(tcRadians);
                double tcDot =
                        tcContinuousMotion.x * tcFrontX
                                + tcContinuousMotion.z * tcFrontZ;

                if (tcDot > tcContinuousSpeed * 0.20D) {
                    this.travelDirectionSign = 1;
                } else if (tcDot < -tcContinuousSpeed * 0.20D) {
                    this.travelDirectionSign = -1;
                }
            }

            this.trainFacingYaw = Mth.wrapDegrees(tcContinuousYaw);
            this.trainFacingInitialized = true;
            this.setYRot(this.trainFacingYaw);
            this.yRotO = this.trainFacingYaw;
            return;
        }

        float axisYaw = switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0.0F;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 90.0F;
            case SOUTH_EAST, NORTH_WEST -> 45.0F;
            case NORTH_EAST, SOUTH_WEST -> -45.0F;
        };

        float first = Mth.wrapDegrees(axisYaw);
        float opposite = Mth.wrapDegrees(axisYaw + 180.0F);
        float previous = this.getTrainFacingYaw();

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float expectedFromMotion = previous;

        if (horizontalSpeed > FACING_MOTION_EPSILON) {
            double radians = Math.toRadians(previous);
            double frontX = Math.sin(radians);
            double frontZ = -Math.cos(radians);
            double dot = motion.x * frontX + motion.z * frontZ;

            // Remember whether the locomotive is travelling nose-first or in
            // reverse. Around a curve the instantaneous motion can become nearly
            // perpendicular to the previous body yaw, so keep the last reliable
            // sign rather than interpreting that moment as a 180-degree reversal.
            if (dot > horizontalSpeed * 0.20D) {
                this.travelDirectionSign = 1;
            } else if (dot < -horizontalSpeed * 0.20D) {
                this.travelDirectionSign = -1;
            }

            float motionYaw = (float) Math.toDegrees(Math.atan2(motion.x, -motion.z));
            expectedFromMotion = Mth.wrapDegrees(
                    motionYaw + (this.travelDirectionSign < 0 ? 180.0F : 0.0F));
        }

        float firstDistance = Math.abs(Mth.wrapDegrees(first - previous));
        float oppositeDistance = Math.abs(Mth.wrapDegrees(opposite - previous));

        // Normally rail continuity is enough to choose the locomotive's nose.
        // On a fast downhill transition Minecraft can move far enough in one tick
        // that we effectively jump from one rail axis to another. That produces a
        // 90/90 tie and the old code could pick the wrong end, visually flipping
        // the locomotive on slopes or curves. Resolve only those ambiguous cases
        // using actual travel direction; otherwise preserve the established nose.
        if (horizontalSpeed > FACING_MOTION_EPSILON
                && Math.abs(firstDistance - oppositeDistance) <= FACING_TIE_DEGREES) {
            float firstMotionDistance = Math.abs(Mth.wrapDegrees(first - expectedFromMotion));
            float oppositeMotionDistance = Math.abs(Mth.wrapDegrees(opposite - expectedFromMotion));
            this.trainFacingYaw = firstMotionDistance <= oppositeMotionDistance ? first : opposite;
        } else {
            this.trainFacingYaw = firstDistance <= oppositeDistance ? first : opposite;
        }

        this.trainFacingInitialized = true;
        this.setYRot(this.trainFacingYaw);
        this.yRotO = this.trainFacingYaw;
    }

    /**
     * The rail-facing value intentionally changes in 45-degree steps on vanilla
     * curve blocks. Rendering that value directly makes a long locomotive snap
     * sideways at speed. Keep physics on the exact rail axis, but let the visible
     * body turn toward it over several ticks so a 90-degree bend reads as one
     * continuous turn instead of a pair of instant rotations.
     */
    private void updateVisualTrainFacing() {
        if (!this.visualFacingInitialized) {
            this.visualFacingYaw = this.getTrainFacingYaw();
            this.previousVisualFacingYaw = this.visualFacingYaw;
            this.visualFacingInitialized = true;
            return;
        }

        this.previousVisualFacingYaw = this.visualFacingYaw;
        float delta = Mth.wrapDegrees(this.getTrainFacingYaw() - this.visualFacingYaw);
        float step = Mth.clamp(delta,
                -VISUAL_FACING_MAX_TURN_DEGREES,
                VISUAL_FACING_MAX_TURN_DEGREES);
        this.visualFacingYaw = Mth.wrapDegrees(this.visualFacingYaw + step);
    }

    public float getRenderTrainFacingYaw(float partialTicks) {
        if (!this.visualFacingInitialized) {
            return this.getTrainFacingYaw();
        }
        return Mth.rotLerp(partialTicks, this.previousVisualFacingYaw, this.visualFacingYaw);
    }

    public boolean isOnSharpCurve() {
        return isSharpVanillaCurve(this.getCurrentRailShape());
    }

    private RailShape getCurrentRailShape() {
        BlockPos railPos = this.blockPosition();
        BlockState state = this.level().getBlockState(railPos);

        if (!BaseRailBlock.isRail(state)) {
            railPos = railPos.below();
            state = this.level().getBlockState(railPos);
        }

        if (!BaseRailBlock.isRail(state)) {
            return null;
        }

        BaseRailBlock rail = (BaseRailBlock) state.getBlock();
        return rail.getRailDirection(state, this.level(), railPos, this);
    }

    /**
     * Firebox hotspot for the legacy left-click GUI interaction. The hit is
     * transformed onto the locomotive's longitudinal axis, so it follows rail
     * orientation and does not depend on the player's camera direction.
     */
    public boolean isFireboxHit(Vec3 hitLocation) {
        double dx = hitLocation.x - this.getX();
        double dz = hitLocation.z - this.getZ();
        double localY = hitLocation.y - this.getY();

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        double longitudinal = dx * frontX + dz * frontZ;

        return longitudinal >= FIREBOX_REAR
                && longitudinal <= FIREBOX_FRONT
                && localY >= FIREBOX_MIN_Y
                && localY <= FIREBOX_MAX_Y;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ConnectorItem) {
            return ConnectorItem.handleRollingStockClick(this, player, hand);
        }
        return super.interact(player, hand);
    }

    /**
     * Heavy rolling stock collision handling. Living entities are shoved away
     * from the locomotive instead of transferring their tiny collision impulse
     * back into the train. Faster impacts also deal proportionally more damage.
     */
    @Override
    public void push(Entity entity) {
        // Coupled stock is kept at coupler spacing by the Step 7.0 constraint;
        // vanilla minecart collision impulses would make the pair bounce apart.
        if (this.isCoupledTo(entity.getUUID())) {
            return;
        }

        if (entity instanceof LivingEntity living && !this.hasPassenger(entity)) {
            if (!this.level().isClientSide) {
                this.hitLivingEntity(living);
            }
            return;
        }

        super.push(entity);
    }

    private void hitLivingEntity(LivingEntity living) {
        if (!living.isAlive() || living.isRemoved()) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontalSpeed <= 1.0E-5D) {
            return;
        }

        double speedKmh = horizontalSpeed * 20.0D * 3.6D;
        double directionX = motion.x / horizontalSpeed;
        double directionZ = motion.z / horizontalSpeed;

        // Even a slow-moving locomotive has enough mass to move an animal/mob
        // out of the way instead of being stopped by it.
        double pushStrength = 0.18D + Math.min(0.75D, speedKmh / 60.0D);
        living.push(directionX * pushStrength, 0.12D + Math.min(0.18D, speedKmh / 150.0D), directionZ * pushStrength);
        living.hurtMarked = true;

        if (speedKmh < IMPACT_MIN_SPEED_KMH) {
            return;
        }

        int lastImpact = this.lastImpactTicks.getOrDefault(living.getId(), -IMPACT_DAMAGE_COOLDOWN_TICKS);
        if (this.tickCount - lastImpact < IMPACT_DAMAGE_COOLDOWN_TICKS) {
            return;
        }
        this.lastImpactTicks.put(living.getId(), this.tickCount);

        float damage;
        if (speedKmh >= IMPACT_INSTANT_KILL_SPEED_KMH) {
            damage = living.getMaxHealth() + living.getAbsorptionAmount() + 100.0F;
        } else {
            damage = Math.max(1.0F, (float)(speedKmh * 0.25D));
        }

        living.hurt(this.damageSources().generic(), damage);

        // Keep the small cooldown map bounded during long sessions.
        if (this.lastImpactTicks.size() > 64) {
            this.lastImpactTicks.entrySet().removeIf(entry -> this.tickCount - entry.getValue() > 200);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        double radians = Math.toRadians(this.getTrainFacingYaw());
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        double rightX = -frontZ;
        double rightZ = frontX;

        double seatBack = this.getDriverSeatBackOffset();
        double seatSide = this.getDriverSeatSideOffset();

        double seatX = this.getX()
                - frontX * seatBack
                + rightX * seatSide;

        double seatY = this.getY()
                + this.getDriverSeatYOffset()
                + passenger.getMyRidingOffset();

        double seatZ = this.getZ()
                - frontZ * seatBack
                + rightZ * seatSide;

        moveFunction.accept(passenger, seatX, seatY, seatZ);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        // Free-look while seated.
    }

    @Override
    protected void applyNaturalSlowdown() {
        Vec3 motion = this.getDeltaMovement();
        if (this.handBrakeApplied) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        boolean powered = (this.forwardInput ^ this.reverseInput) && this.steamAmount > 0;
        double resistance = powered && !this.brakeInput
                ? POWERED_ROLLING_RESISTANCE
                : COAST_ROLLING_RESISTANCE;
        this.setDeltaMovement(motion.x * resistance, 0.0D, motion.z * resistance);
    }

    /**
     * A vanilla minecart loses more speed to each ascending-rail step than this
     * small locomotive's traction adds in one tick. That made a powered Traincraft
     * engine stall and roll backward on even a normal one-block rail slope.
     *
     * While the driver is actively applying steam, neutralize vanilla's minecart
     * slope penalty and let Traincraft's own acceleration provide the tractive
     * effort. Release the throttle (or run out of steam) and normal gravity/slope
     * behaviour returns, so an unsecured locomotive can still roll downhill.
     */
    @Override
    public double getSlopeAdjustment() {
        // Both brakes oppose gravity while they are actually being held/applied.
        // Space is the active/service brake, so holding it on an incline should
        // bring the locomotive to a stop rather than let vanilla slope gravity
        // accelerate it backward. C is the parking brake and must hold absolutely.
        if (this.handBrakeApplied || this.brakeInput || (this.forwardInput && this.reverseInput)) {
            return 0.0D;
        }

        boolean applyingSteam = (this.forwardInput ^ this.reverseInput)
                && this.steamAmount > 0;
        return applyingSteam ? 0.0D : super.getSlopeAdjustment();
    }

    /**
     * Traincraft locomotives provide their own traction. Vanilla powered rails
     * must not add minecart propulsion, otherwise an unfired steam engine can
     * move with 0% steam. Detector/Traincraft rail behaviour will be handled
     * explicitly as the rail system is ported.
     */
    @Override
    public boolean shouldDoRailFunctions() {
        return false;
    }

    @Override
    protected double getMaxSpeed() {
        if (this.handBrakeApplied) {
            return 0.0D;
        }

        RailShape shape = this.getCurrentRailShape();
        if (isSharpVanillaCurve(shape)) {
            return CURVE_MAX_SPEED_BLOCKS_PER_TICK;
        }

        return Math.max(
                this.getRealismMaxForwardSpeedBlocksPerTick(),
                this.getRealismMaxReverseSpeedBlocksPerTick());
    }

    private static boolean isSharpVanillaCurve(@Nullable RailShape shape) {
        return shape == RailShape.NORTH_EAST
                || shape == RailShape.NORTH_WEST
                || shape == RailShape.SOUTH_EAST
                || shape == RailShape.SOUTH_WEST;
    }

    public double getSpeedKmh() {
        Vec3 motion = this.getDeltaMovement();
        double blocksPerTick = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return blocksPerTick * 20.0D * 3.6D;
    }

    public IItemHandler getSteamInventory() {
        return this.steamInventory;
    }

    public int getBurnTime() {
        return this.level().isClientSide ? this.entityData.get(DATA_BURN_TIME) : this.burnTime;
    }

    public int getMaxBurnTime() {
        return this.level().isClientSide ? this.entityData.get(DATA_MAX_BURN_TIME) : this.maxBurnTime;
    }

    public int getWaterAmount() {
        return this.level().isClientSide ? this.entityData.get(DATA_WATER_AMOUNT) : this.waterAmount;
    }

    public int getSteamAmount() {
        return this.level().isClientSide ? this.entityData.get(DATA_STEAM_AMOUNT) : this.steamAmount;
    }

    public double getBoilerTemperatureK() {
        return this.level().isClientSide
                ? this.entityData.get(DATA_BOILER_TEMPERATURE_K)
                : this.boilerTemperatureK;
    }

    public double getBoilerTemperatureC() {
        return this.getBoilerTemperatureK() - 273.15D;
    }

    public int getBoilerTemperatureTenthsC() {
        return (int) Math.round(this.getBoilerTemperatureC() * 10.0D);
    }

    /** 0% at the classic 20 C resting temperature, 100% at 200 C overheat. */
    public int getBoilerHeatPercent() {
        double temperature = this.getBoilerTemperatureK();
        double range = MAX_BOILER_TEMPERATURE_K - DEFAULT_BOILER_TEMPERATURE_K;
        return Mth.clamp((int) Math.round(
                (temperature - DEFAULT_BOILER_TEMPERATURE_K) * 100.0D / range), 0, 100);
    }

    public boolean isBoilerHot() {
        return this.getBoilerTemperatureK() >= HOT_BOILER_TEMPERATURE_K;
    }

    public boolean isBoilerOverheating() {
        return this.getBoilerTemperatureK() > MAX_BOILER_TEMPERATURE_K;
    }

    public boolean isBoilerCritical() {
        return this.getBoilerTemperatureK() >= CRITICAL_BOILER_TEMPERATURE_K;
    }

    private static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    private static boolean isWaterContainer(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET);
    }

    private void setChangedForSync() {
        // Placeholder hook for later direct entity-data sync. Menu slot packets
        // already synchronize inventory contents while the GUI is open.
    }

    public void openSteamMenu(ServerPlayer player) {
        double distanceSqr = player.distanceToSqr(this);
        if (distanceSqr > 64.0D) {
            Traincraft.LOGGER.debug(
                    "Traincraft steam GUI open rejected by distance check for {}: distanceSq={}",
                    player.getGameProfile().getName(), distanceSqr);
            return;
        }

        // Step 6.8.6 deliberately uses Minecraft's vanilla ServerPlayer menu
        // opening path. The previous Forge extra-data path was accepting the R
        // request server-side but the client never displayed the screen. Since R
        // can only be used while riding this locomotive, the client can resolve
        // the locomotive directly from its current vehicle and no extra buffer is
        // required.
        var opened = player.openMenu(this);
        Traincraft.LOGGER.debug(
                "Traincraft vanilla steam GUI open result for {}: opened={}, serverContainer={}",
                player.getGameProfile().getName(), opened.isPresent(),
                player.containerMenu == null ? "null" : player.containerMenu.getClass().getSimpleName());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.traincraft.steam_locomotive");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SteamLocomotiveMenu(containerId, inventory, this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("SteamInventory", this.steamInventory.serializeNBT());
        tag.putInt("BurnTime", this.burnTime);
        tag.putInt("MaxBurnTime", this.maxBurnTime);
        tag.putInt("WaterAmount", this.waterAmount);
        tag.putInt("SteamAmount", this.steamAmount);
        tag.putDouble("BoilerTemperatureK", this.boilerTemperatureK);
        tag.putInt("BoilerFailureTicks", this.boilerFailureTicks);
        tag.putBoolean("HandBrakeApplied", this.handBrakeApplied);
        if (this.coupledRollingStockUuid != null) {
            tag.putUUID("CoupledRollingStock", this.coupledRollingStockUuid);
        }
        if (this.secondaryCoupledRollingStockUuid != null) {
            tag.putUUID("SecondaryCoupledRollingStock", this.secondaryCoupledRollingStockUuid);
        }
        tag.putFloat("TrainFacingYaw", this.getTrainFacingYaw());
        tag.putBoolean("TrainFacingInitialized", this.trainFacingInitialized);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SteamInventory")) {
            this.steamInventory.deserializeNBT(tag.getCompound("SteamInventory"));
        }
        this.burnTime = Math.max(0, tag.getInt("BurnTime"));
        this.maxBurnTime = Math.max(0, tag.getInt("MaxBurnTime"));
        this.waterAmount = Mth.clamp(tag.getInt("WaterAmount"), 0, WATER_CAPACITY_MB);
        this.steamAmount = Mth.clamp(tag.getInt("SteamAmount"), 0, STEAM_CAPACITY);
        this.boilerTemperatureK = tag.contains("BoilerTemperatureK")
                ? Mth.clamp(tag.getDouble("BoilerTemperatureK"),
                        MIN_BOILER_TEMPERATURE_K, ABSOLUTE_BOILER_TEMPERATURE_K)
                : DEFAULT_BOILER_TEMPERATURE_K;
        this.boilerFailureTicks = Mth.clamp(
                tag.getInt("BoilerFailureTicks"), 0, BOILER_FAILURE_LIMIT_TICKS);
        this.handBrakeApplied = tag.getBoolean("HandBrakeApplied");
        this.coupledRollingStockUuid = tag.hasUUID("CoupledRollingStock")
                ? tag.getUUID("CoupledRollingStock")
                : null;
        this.secondaryCoupledRollingStockUuid = tag.hasUUID("SecondaryCoupledRollingStock")
                ? tag.getUUID("SecondaryCoupledRollingStock")
                : null;
        this.syncTelemetryToClient();
        if (tag.contains("TrainFacingYaw")) {
            this.trainFacingYaw = tag.getFloat("TrainFacingYaw");
            this.trainFacingInitialized = tag.getBoolean("TrainFacingInitialized");
        }
    }

    @Nullable
    @Override
    public UUID getCoupledRollingStockUuid() {
        return this.coupledRollingStockUuid;
    }

    @Override
    public void setCoupledRollingStockUuid(@Nullable UUID uuid) {
        this.coupledRollingStockUuid = uuid;
    }

    @Nullable
    @Override
    public UUID getSecondaryCoupledRollingStockUuid() {
        return this.secondaryCoupledRollingStockUuid;
    }

    @Override
    public void setSecondaryCoupledRollingStockUuid(@Nullable UUID uuid) {
        this.secondaryCoupledRollingStockUuid = uuid;
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_SMALL.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_SMALL.get());
    }
}
