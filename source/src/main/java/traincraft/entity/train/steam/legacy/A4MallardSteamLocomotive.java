/* Step 8.4.2 Batch B3: A4 Mallard Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class A4MallardSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_8A_R1_REALISM
     * Step 8.5.8a-r1 Batch A per-locomotive realism calibration.
     * LNER A4 Mallard: 104.6 t locomotive, 157.7 kN starting TE; 126 mph record basis.
     * Traction reference mass = historical starting TE / gameplay base acceleration.
     * Forward/reverse caps are gameplay/service limits; shared physics remain frozen.
     */
    private static final double REALISM_MASS_T = 104.6D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 315.4D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.500D;
    private static final double REALISM_MAX_FORWARD_BPT = 203.0D / 72.0D;
    private static final double REALISM_MAX_REVERSE_BPT = 50.0D / 72.0D;

    @Override
    protected double getRealismLocomotiveMassTons() {
        return REALISM_MASS_T;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return REALISM_TRACTION_REFERENCE_MASS_T;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return REALISM_BASE_ACCELERATION_MPS2;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return REALISM_MAX_FORWARD_BPT;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return REALISM_MAX_REVERSE_BPT;
    }
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.2730D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.4847D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 4.6453D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.3156D;
    private static final double VISUAL_CHIMNEY_SIDE_OFFSET = 0.0000D;

    @Override
    protected double getDriverSeatBackOffset() {
        return VISUAL_DRIVER_SEAT_BACK_OFFSET;
    }

    @Override
    protected double getDriverSeatYOffset() {
        return VISUAL_DRIVER_SEAT_Y_OFFSET;
    }

    @Override
    protected double getDriverSeatSideOffset() {
        return VISUAL_DRIVER_SEAT_SIDE_OFFSET;
    }

    @Override
    protected double getChimneyFrontOffset() {
        return VISUAL_CHIMNEY_FRONT_OFFSET;
    }

    @Override
    protected double getChimneyTopYOffset() {
        return VISUAL_CHIMNEY_TOP_Y_OFFSET;
    }

    @Override
    protected double getChimneySideOffset() {
        return VISUAL_CHIMNEY_SIDE_OFFSET;
    }
    /*
     * Step 8.5.4b-r3 - A4 model-aware snow-clearing origin.
     * Runtime renderer transform places the visible nose about 6.1993
     * blocks forward of the entity origin. With the shared first sample
     * at +0.9000, the calibrated origin offset is 5.2993.
     */
    private static final double A4_SNOW_PLOW_ORIGIN_FORWARD_OFFSET = 5.2993D;

    @Override
    protected double getFrontSnowPlowOriginForwardOffset() {
        return A4_SNOW_PLOW_ORIGIN_FORWARD_OFFSET;
    }

    public A4MallardSteamLocomotive(EntityType<? extends A4MallardSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "A4 Mallard Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_A4_MALLARD.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_A4_MALLARD.get());
    }
}
