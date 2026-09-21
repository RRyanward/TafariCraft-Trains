/* Step 8.4.7 Batch B8: Alco SC4 Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class AlcoSc4SteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.2915D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2300D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 3.6250D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 1.8816D;
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
     * STEP_8_5_11A_R1_REALISM
     * LS&I/ALCO SC-4 representative: ~85.9 t locomotive and ~186.8 kN starting tractive effort; ~88.5 km/h forward cap. Reverse is gameplay/service calibration.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the frozen shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 85.9D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 467.0D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.400D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 88.5D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 50.0D / 72.0D;
    }
    public AlcoSc4SteamLocomotive(EntityType<? extends AlcoSc4SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Alco SC4 Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_ALCO_SC4.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_ALCO_SC4.get());
    }
}
