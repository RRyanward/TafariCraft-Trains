/* Step 8.4.5 Batch B6: GWR 42xx Steam Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class Gwr42xxSteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.0065D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.2931D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 3.4259D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.6411D;
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
     * GWR 42xx 2-8-0T representative: ~82.9 t, ~139.9 kN starting effort. 64.4 km/h is a conservative heavy-freight gameplay/service cap.
     * Acceleration is gameplay/service calibration. Traction reference is
     * derived from representative starting tractive effort divided by the
     * selected base acceleration, preserving the shared mass-aware model.
     */
    @Override
    protected double getRealismLocomotiveMassTons() {
        return 82.9D;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return 349.7D;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return 0.400D;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return 64.4D / 72.0D;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return 64.4D / 72.0D;
    }
    public Gwr42xxSteamLocomotive(EntityType<? extends Gwr42xxSteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "GWR 42xx Steam Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_GWR_42XX.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_GWR_42XX.get());
    }
}
