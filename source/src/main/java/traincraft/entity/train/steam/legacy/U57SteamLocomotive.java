/* Step 8.4.0 Batch B1: U57 Steam Locomotive. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class U57SteamLocomotive extends SmallSteamLocomotive {

    /*
     * STEP_8_5_12A_R1_REALISM
     * Step 8.5.12a-r1 Batch E per-locomotive realism.
     * Representative Skoda/JDZ narrow-gauge U57.001 family: about 34.5 t service mass, 23,212 lbf tractive effort, 19 mph top speed.
     * Acceleration is gameplay/service calibration. Traction reference mass
     * preserves the shared mass-aware tractive-effort model.
     */
    private static final double REALISM_MASS_T = 34.5D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 295.0D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.350D;
    private static final double REALISM_MAX_FORWARD_BPT = 30.6D / 72.0D;
    private static final double REALISM_MAX_REVERSE_BPT = 30.6D / 72.0D;

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
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 2.1602D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.9008D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 1.8750D;
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
    public U57SteamLocomotive(EntityType<? extends U57SteamLocomotive> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "U57 Steam Locomotive"); }
    @Override protected Item getDropItem() { return TCItems.LOCOMOTIVE_STEAM_U57.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.LOCOMOTIVE_STEAM_U57.get()); }
}
