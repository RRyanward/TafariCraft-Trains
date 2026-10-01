/* Step 8.4.0 Batch B1: Pannier Steam Locomotive. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class PannierSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.5966D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.4800D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 3.4219D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.2503D;
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
     * Traincraft identifies a generic 0-6-0 Pannier; representative GWR 5700-style calibration uses ~48.3 t and ~100.2 kN starting effort. 72.4 km/h (45 mph) is a gameplay/service cap.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 48.3D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 235.7D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.425D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 72.4D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 72.4D / 72.0D;
    }
    public PannierSteamLocomotive(EntityType<? extends PannierSteamLocomotive> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "Pannier Steam Locomotive"); }
    @Override protected Item getDropItem() { return TCItems.LOCOMOTIVE_STEAM_PANNIER.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.LOCOMOTIVE_STEAM_PANNIER.get()); }
}
