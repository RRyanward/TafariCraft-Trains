/* Step 8.4.2 Batch B3: USATC S100 US Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class S100UsSteamLocomotive extends SmallSteamLocomotive {

    /*
     * STEP_8_5_12A_R1_REALISM
     * Step 8.5.12a-r1 Batch E per-locomotive realism.
     * USATC S100 representative: about 45.7 t service mass and 96.1 kN starting tractive effort; 50 km/h service calibration.
     * Acceleration is gameplay/service calibration. Traction reference mass
     * preserves the shared mass-aware tractive-effort model.
     */
    private static final double REALISM_MASS_T = 45.7D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 240.2D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.400D;
    private static final double REALISM_MAX_FORWARD_BPT = 50.0D / 72.0D;
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
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.3775D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2300D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 2.1085D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 1.7881D;
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
    public S100UsSteamLocomotive(EntityType<? extends S100UsSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "USATC S100 US Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_S100_US.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_S100_US.get());
    }
}
