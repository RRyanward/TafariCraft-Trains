/* Step 8.4.6 Batch B7: GWR 101 Class Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class Gwr101ClassSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.1815D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2616D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 2.6875D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.0944D;
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
     * GWR 101 experimental 0-4-0T: ~28.4 t full, ~54.1 kN starting effort. No reliable historical maximum-speed figure was found; 40 km/h is a conservative gameplay/service cap.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 28.4D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 154.6D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.350D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 40.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 40.0D / 72.0D;
    }
    public Gwr101ClassSteamLocomotive(EntityType<? extends Gwr101ClassSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "GWR 101 Class Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_GWR_101_CLASS.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_GWR_101_CLASS.get());
    }
}
