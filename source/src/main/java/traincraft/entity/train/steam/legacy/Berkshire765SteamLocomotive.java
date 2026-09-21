/* Step 8.4.3 Batch B4: Berkshire 765 Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class Berkshire765SteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_8A_R1_REALISM
     * Step 8.5.8a-r1 Batch A per-locomotive realism calibration.
     * Nickel Plate S-2 765 representative: 199.9 t locomotive, 285.3 kN starting TE; 70 mph class/service cap.
     * Traction reference mass = historical starting TE / gameplay base acceleration.
     * Forward/reverse caps are gameplay/service limits; shared physics remain frozen.
     */
    private static final double REALISM_MASS_T = 199.9D;
    private static final double REALISM_TRACTION_REFERENCE_MASS_T = 713.2D;
    private static final double REALISM_BASE_ACCELERATION_MPS2 = 0.400D;
    private static final double REALISM_MAX_FORWARD_BPT = 112.7D / 72.0D;
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
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.7137D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.3309D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 3.0578D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.2602D;
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
    public Berkshire765SteamLocomotive(EntityType<? extends Berkshire765SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Berkshire 765 Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_BERKSHIRE_765.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_BERKSHIRE_765.get());
    }
}
