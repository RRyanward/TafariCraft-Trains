/*
 * Step 8.3.0 Shay Steam Locomotive foundation.
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

public class ShaySteamLocomotive extends SmallSteamLocomotive {
    /*
     * Step 8.5.3c-r1 - Shay model-derived visual anchors.
     *
     * The Shay renderer applies a -0.4000 block local-X translation
     * followed by a 180 degree model rotation, so these values are
     * intentionally Shay-specific.
     *
     * r1 is a visual calibration pass. Shared Small Steam defaults
     * remain frozen.
     */
    private static final double SHAY_DRIVER_SEAT_BACK_OFFSET = 0.0375D;
    private static final double SHAY_DRIVER_SEAT_Y_OFFSET = 0.6875D;
    private static final double SHAY_DRIVER_SEAT_SIDE_OFFSET = 0.0D;

    private static final double SHAY_CHIMNEY_FRONT_OFFSET = 1.7750D;
    private static final double SHAY_CHIMNEY_TOP_Y_OFFSET = 2.0625D;
    private static final double SHAY_CHIMNEY_SIDE_OFFSET = -0.1875D;

    @Override
    protected double getDriverSeatBackOffset() {
        return SHAY_DRIVER_SEAT_BACK_OFFSET;
    }

    @Override
    protected double getDriverSeatYOffset() {
        return SHAY_DRIVER_SEAT_Y_OFFSET;
    }

    @Override
    protected double getDriverSeatSideOffset() {
        return SHAY_DRIVER_SEAT_SIDE_OFFSET;
    }

    @Override
    protected double getChimneyFrontOffset() {
        return SHAY_CHIMNEY_FRONT_OFFSET;
    }

    @Override
    protected double getChimneyTopYOffset() {
        return SHAY_CHIMNEY_TOP_Y_OFFSET;
    }

    @Override
    protected double getChimneySideOffset() {
        return SHAY_CHIMNEY_SIDE_OFFSET;
    }
    /*
     * Step 8.5.5 - Shay realism/spec calibration.
     *
     * Representative prototype: Lima Class B 50-2 two-truck Shay.
     * Historical/spec basis:
     * - service mass: approximately 54.4 metric tonnes (120,000 lb)
     * - starting tractive effort: approximately 100.4 kN (22,563 lbf)
     * - maximum safe speed: 17.2 mph (approximately 27.7 km/h)
     *
     * SHAY_REALISM_BASE_ACCELERATION_MPS2 is a Traincraft gameplay
     * calibration, not a historical acceleration measurement.
     * The 286.8 t traction reference preserves approximately 100.4 kN
     * under the shared constant-tractive-effort mass scaling:
     * 286.8 t * 0.350 m/s^2 ~= 100.4 kN.
     *
     * Boiler/water/fuel tuning remains deliberately deferred.
     */
    private static final double SHAY_REALISM_MASS_T = 54.4D;
    private static final double SHAY_REALISM_TRACTION_REFERENCE_MASS_T = 286.8D;
    private static final double SHAY_REALISM_BASE_ACCELERATION_MPS2 = 0.350D;
    private static final double SHAY_REALISM_MAX_FORWARD_BPT = 27.7D / 72.0D;
    private static final double SHAY_REALISM_MAX_REVERSE_BPT = 27.7D / 72.0D;

    @Override
    protected double getRealismLocomotiveMassTons() {
        return SHAY_REALISM_MASS_T;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return SHAY_REALISM_TRACTION_REFERENCE_MASS_T;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return SHAY_REALISM_BASE_ACCELERATION_MPS2;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return SHAY_REALISM_MAX_FORWARD_BPT;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return SHAY_REALISM_MAX_REVERSE_BPT;
    }

    public ShaySteamLocomotive(EntityType<? extends ShaySteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_SHAY.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_SHAY.get());
    }
}
