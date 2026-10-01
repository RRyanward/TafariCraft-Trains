/* Step 8.4.7 Batch B8: Steam Snow Plow Locomotive. Debug intentionally enabled. */
package traincraft.entity.train.steam.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class SteamSnowPlowLocomotive extends SmallSteamLocomotive {
    /*
     * Step 8.5.3f-r1 - Steam Snow Plow model-derived visual anchors.
     *
     * Frozen renderer transform:
     *   translate = (-2.000000, +0.562813, 0.000000)
     *   rotate    = (0, 180, 180)
     *
     * The supplied TCM resolves the centered exhaust stack to transformed
     * X -3.15625 with a top Y of 2.187813.  The cab seat is placed near
     * the rear cab center as a runtime-calibration candidate.
     *
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double SNOW_PLOW_DRIVER_SEAT_BACK_OFFSET = -0.1250D;
    private static final double SNOW_PLOW_DRIVER_SEAT_Y_OFFSET = 0.3000D;
    private static final double SNOW_PLOW_DRIVER_SEAT_SIDE_OFFSET = 0.0D;

    private static final double SNOW_PLOW_CHIMNEY_FRONT_OFFSET = -3.15625D;
    private static final double SNOW_PLOW_CHIMNEY_TOP_Y_OFFSET = 2.187813D;
    private static final double SNOW_PLOW_CHIMNEY_SIDE_OFFSET = 0.0D;

    /* Step 8.5.4a-r1 - dedicated 3 x 2 x 3 snow-clearance envelope. */
    private static final double[] SNOW_PLOW_CLEAR_FORWARD_SAMPLES =
            {0.90D, 1.65D, 2.40D, 3.00D};
    private static final double[] SNOW_PLOW_CLEAR_SIDE_SAMPLES =
            {-1.0D, 0.0D, 1.0D};

    @Override
    protected double getDriverSeatBackOffset() {
        return SNOW_PLOW_DRIVER_SEAT_BACK_OFFSET;
    }

    @Override
    protected double getDriverSeatYOffset() {
        return SNOW_PLOW_DRIVER_SEAT_Y_OFFSET;
    }

    @Override
    protected double getDriverSeatSideOffset() {
        return SNOW_PLOW_DRIVER_SEAT_SIDE_OFFSET;
    }

    @Override
    protected double getChimneyFrontOffset() {
        return SNOW_PLOW_CHIMNEY_FRONT_OFFSET;
    }

    @Override
    protected double getChimneyTopYOffset() {
        return SNOW_PLOW_CHIMNEY_TOP_Y_OFFSET;
    }

    @Override
    protected double getChimneySideOffset() {
        return SNOW_PLOW_CHIMNEY_SIDE_OFFSET;
    }
    @Override
    protected double[] getFrontSnowPlowForwardSamples() {
        return SNOW_PLOW_CLEAR_FORWARD_SAMPLES;
    }

    @Override
    protected double[] getFrontSnowPlowSideSamples() {
        return SNOW_PLOW_CLEAR_SIDE_SAMPLES;
    }

    @Override
    protected int getFrontSnowPlowClearHeightBlocks() {
        return 2;
    }

    public SteamSnowPlowLocomotive(EntityType<? extends SteamSnowPlowLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Steam Snow Plow Locomotive");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_SNOW_PLOW.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_SNOW_PLOW.get());
    }
}
