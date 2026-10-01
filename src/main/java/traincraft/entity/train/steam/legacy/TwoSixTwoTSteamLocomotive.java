/* Step 8.4.7 Batch B8: 2-6-2T Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class TwoSixTwoTSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.0362D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.4181D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 2.6437D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.3131D;
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
     * STEP_8_5_10A_R1_REALISM
     * Traincraft/source history identifies only a generic 2-6-2T, not a specific prototype. Representative light British Prairie calibration uses LMS Ivatt Class 2 values: ~64.3 t and ~77.4 kN starting effort; 96.6 km/h is a gameplay/service cap.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 64.3D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 193.5D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.400D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 96.6D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 96.6D / 72.0D;
    }
    public TwoSixTwoTSteamLocomotive(EntityType<? extends TwoSixTwoTSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "2-6-2T Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_262T.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_262T.get());
    }
}
