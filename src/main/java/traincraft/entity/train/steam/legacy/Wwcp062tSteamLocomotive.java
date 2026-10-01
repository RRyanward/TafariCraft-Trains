/* Step 8.4.6 Batch B7: WWCP Class 0-6-2T Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class Wwcp062tSteamLocomotive extends SmallSteamLocomotive {

    /*
     * STEP_8_5_13A_R1_REALISM
     * Step 8.5.13a-r1 Final Batch F per-locomotive realism.
     * Exact WWCP prototype is unresolved. Representative 0-6-2T tank-locomotive calibration uses ~69.7 t and ~114.8 kN starting effort (GWR 56xx-like reference); 64.4 km/h symmetric service cap is conservative gameplay calibration, not an exact historical claim.
     * Acceleration is gameplay/service calibration. Traction reference mass
     * preserves the shared mass-aware tractive-effort model.
     */
    private static final double REALISM_MASS_T = 69.7D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 286.9D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.400D;
    private static final double REALISM_MAX_FORWARD_BPT = 64.4D / 72.0D;
    private static final double REALISM_MAX_REVERSE_BPT = 64.4D / 72.0D;

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
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = -0.0010D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2300D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 4.3125D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 1.9381D;
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
    public Wwcp062tSteamLocomotive(EntityType<? extends Wwcp062tSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "WWCP Class 0-6-2T Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_WWCP_062T.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_WWCP_062T.get());
    }
}
