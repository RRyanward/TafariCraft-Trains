/*
 * Step 8.3.0 Forney Steam Locomotive foundation.
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

public class ForneySteamLocomotive extends SmallSteamLocomotive {
    /*
     * Step 8.5.3d-r1 - Forney model-derived visual anchors.
     *
     * Renderer:
     *   local X translation = -1.3000 block
     *   model Y rotation    = +90 degrees
     *
     * Because of the 90-degree model rotation, the recovered model Z
     * axis supplies the locomotive's longitudinal front/rear placement.
     *
     * These are model-derived first-pass visual calibration values.
     * Shared Small Steam anchor defaults remain frozen.
     */
    private static final double FORNEY_DRIVER_SEAT_BACK_OFFSET = -0.15625D;
    private static final double FORNEY_DRIVER_SEAT_Y_OFFSET = 0.5625D;
    private static final double FORNEY_DRIVER_SEAT_SIDE_OFFSET = 0.0D;

    private static final double FORNEY_CHIMNEY_FRONT_OFFSET = 2.64375D;
    private static final double FORNEY_CHIMNEY_TOP_Y_OFFSET = 2.0000D;
    private static final double FORNEY_CHIMNEY_SIDE_OFFSET = 0.0D;

    @Override
    protected double getDriverSeatBackOffset() {
        return FORNEY_DRIVER_SEAT_BACK_OFFSET;
    }

    @Override
    protected double getDriverSeatYOffset() {
        return FORNEY_DRIVER_SEAT_Y_OFFSET;
    }

    @Override
    protected double getDriverSeatSideOffset() {
        return FORNEY_DRIVER_SEAT_SIDE_OFFSET;
    }

    @Override
    protected double getChimneyFrontOffset() {
        return FORNEY_CHIMNEY_FRONT_OFFSET;
    }

    @Override
    protected double getChimneyTopYOffset() {
        return FORNEY_CHIMNEY_TOP_Y_OFFSET;
    }

    @Override
    protected double getChimneySideOffset() {
        return FORNEY_CHIMNEY_SIDE_OFFSET;
    }
    /*
     * Step 8.5.6 - Forney realism/spec calibration.
     *
     * Representative prototype: Sandy River & Rangeley Lakes No. 9,
     * Baldwin works no. 33550, outside-frame 2-4-4RT, built July 1909.
     * Published representative dimensions:
     * - service weight: 28 short tons (approximately 25.4 metric tonnes)
     * - boiler pressure: 180 psi
     * - cylinders: 11.5 x 14 in
     * - drivers: 35 in
     *
     * Calculated starting tractive-effort basis is approximately 36.0 kN
     * (approximately 8,094 lbf) from the published dimensions using a
     * conventional 0.85 cylinder-force factor. This is an engineering
     * estimate, not a quoted Baldwin builder tractive-effort rating.
     *
     * FORNEY_REALISM_BASE_ACCELERATION_MPS2 and the 48.3 km/h caps are
     * Traincraft gameplay/service calibrations, not claims of measured
     * historical acceleration or a documented prototype maximum speed.
     *
     * The 80.0 t traction reference preserves approximately 36.0 kN
     * under the shared constant-tractive-effort mass scaling:
     * 80.0 t * 0.450 m/s^2 = 36.0 kN.
     *
     * Boiler/water/fuel tuning remains deliberately deferred.
     */
    private static final double FORNEY_REALISM_MASS_T = 25.4D;
    private static final double FORNEY_REALISM_TRACTION_REFERENCE_MASS_T = 80.0D;
    private static final double FORNEY_REALISM_BASE_ACCELERATION_MPS2 = 0.450D;
    private static final double FORNEY_REALISM_MAX_FORWARD_BPT = 48.3D / 72.0D;
    private static final double FORNEY_REALISM_MAX_REVERSE_BPT = 48.3D / 72.0D;

    @Override
    protected double getRealismLocomotiveMassTons() {
        return FORNEY_REALISM_MASS_T;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return FORNEY_REALISM_TRACTION_REFERENCE_MASS_T;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return FORNEY_REALISM_BASE_ACCELERATION_MPS2;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return FORNEY_REALISM_MAX_FORWARD_BPT;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return FORNEY_REALISM_MAX_REVERSE_BPT;
    }

    public ForneySteamLocomotive(EntityType<? extends ForneySteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_FORNEY.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_FORNEY.get());
    }
}
