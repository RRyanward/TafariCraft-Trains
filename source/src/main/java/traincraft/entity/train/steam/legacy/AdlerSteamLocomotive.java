/* Step 8.4.0 Batch B1: Adler Steam Locomotive. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class AdlerSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = -0.1359D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2300D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 1.5875D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 1.8750D;
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
     * STEP_8_5_9A_R1_REALISM
     * Adler: 14.3 t working order, ~5.4 kN starting effort, 65 km/h historical max; reverse cap is gameplay/service calibration.
     * Acceleration is gameplay/service calibration; traction reference preserves
     * the shared mass-aware tractive-effort model without changing core physics.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 14.3D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 18.0D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.300D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 65.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 30.0D / 72.0D;
    }
    public AdlerSteamLocomotive(EntityType<? extends AdlerSteamLocomotive> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "Adler Steam Locomotive"); }
    @Override protected Item getDropItem() { return TCItems.LOCOMOTIVE_STEAM_ADLER.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.LOCOMOTIVE_STEAM_ADLER.get()); }
}
