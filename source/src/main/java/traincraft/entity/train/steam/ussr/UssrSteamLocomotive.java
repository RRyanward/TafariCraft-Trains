/*
 * Traincraft USSR 0-5-0 Steam Locomotive foundation for Minecraft 1.20.1.
 * Step 8.2.1 creates the original production USSR/ER locomotive as its own
 * rolling-stock type while reusing the proven Small Steam steam/control/
 * GUI/HUD/rail baseline. Heavy Steam remains a separate entity.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.steam.ussr;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class UssrSteamLocomotive extends SmallSteamLocomotive {

    /*
     * STEP_8_5_13A_R1_REALISM
     * Step 8.5.13a-r1 Final Batch F per-locomotive realism.
     * Traincraft identifies this as the USSR 0-5-0 E-type. Representative late Russian E/Er-family calibration uses ~85.8 t and ~225.6 kN starting effort; Traincraft documents an 80 km/h forward cap. Reverse cap is gameplay/service calibration.
     * Acceleration is gameplay/service calibration. Traction reference mass
     * preserves the shared mass-aware tractive-effort model.
     */
    private static final double REALISM_MASS_T = 85.8D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 563.9D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.400D;
    private static final double REALISM_MAX_FORWARD_BPT = 80.0D / 72.0D;
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
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.0138D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.6500D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 3.4375D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.3125D;
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
    public UssrSteamLocomotive(EntityType<? extends UssrSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_USSR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_USSR.get());
    }
}
