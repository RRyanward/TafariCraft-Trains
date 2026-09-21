/*
 * Step 8.3.0 Mogul Steam Locomotive foundation.
 * Reuses the proven Small Steam steam/control/GUI/HUD/rail baseline.
 * Locomotive-specific mass, power and boiler tuning is deferred until
 * production model alignment is runtime-confirmed.
 */
package traincraft.entity.train.steam.classics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class MogulSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.4337D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.3122D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 1.6000D;
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
    /*
     * STEP_8_5_11A_R1_REALISM
     * Traincraft identifies this as generic Mogul (US). Representative US 2-6-0 basis uses ~59 t and ~104 kN starting effort; original Traincraft documentation gives a 65 km/h maximum. Reverse is gameplay calibration.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the frozen shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 59.0D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 260.0D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.400D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 65.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 40.0D / 72.0D;
    }
    public MogulSteamLocomotive(EntityType<? extends MogulSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_MOGUL.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_MOGUL.get());
    }
}
