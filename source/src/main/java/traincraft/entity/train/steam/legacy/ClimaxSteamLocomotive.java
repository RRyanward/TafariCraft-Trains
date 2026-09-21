/* Step 8.4.0 Batch B1: Climax Steam Locomotive. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class ClimaxSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.6955D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.4178D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 2.0250D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.2506D;
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
     * Representative 25-short-ton Class B Climax: ~22.7 t; ~15 mph class speed. Traction reference is an engineering/gameplay estimate for geared logging service.
     * Acceleration is gameplay/service calibration; traction reference preserves
     * the shared mass-aware tractive-effort model without changing core physics.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 22.7D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 165.0D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.300D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 24.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 24.0D / 72.0D;
    }
    public ClimaxSteamLocomotive(EntityType<? extends ClimaxSteamLocomotive> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "Climax Steam Locomotive"); }
    @Override protected Item getDropItem() { return TCItems.LOCOMOTIVE_STEAM_CLIMAX.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.LOCOMOTIVE_STEAM_CLIMAX.get()); }
}
