/* Step 8.4.1 Batch B2: MILW Class A Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class MilwClassASteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = -0.0186D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2300D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 4.5844D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 1.7724D;
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
     * Milwaukee Road Class A Hiawatha representative: ~131.5 t locomotive, ~136.5 kN starting tractive effort and 181 km/h documented maximum speed. Reverse is gameplay/service calibration.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the frozen shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 131.5D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 273.0D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.500D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 181.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 50.0D / 72.0D;
    }
    public MilwClassASteamLocomotive(EntityType<? extends MilwClassASteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "MILW Class A Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_MILW_CLASS_A.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_MILW_CLASS_A.get());
    }
}
