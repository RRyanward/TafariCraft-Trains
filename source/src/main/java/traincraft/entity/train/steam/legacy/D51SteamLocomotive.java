/* Step 8.4.2 Batch B3: D51 Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class D51SteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = -0.3913D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2925D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 4.8750D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.2500D;
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
     * JNR D51: ~78.4 t locomotive service weight, ~181.9 kN starting effort, 85 km/h max; tender-loco reverse capped at 50 km/h.
     * Acceleration is gameplay/service calibration; traction reference preserves
     * the shared mass-aware tractive-effort model without changing core physics.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 78.4D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 404.1D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.450D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 85.0D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 50.0D / 72.0D;
    }
    public D51SteamLocomotive(EntityType<? extends D51SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "D51 Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_D51.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_D51.get());
    }
}
