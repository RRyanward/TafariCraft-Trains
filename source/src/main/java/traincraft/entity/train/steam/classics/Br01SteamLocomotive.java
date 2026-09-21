/*
 * Step 8.3.0 BR01 Steam Locomotive foundation.
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

public class Br01SteamLocomotive extends SmallSteamLocomotive {
    /*
     * Step 8.5.3b-r1 - BR01 model-derived visual anchors.
     *
     * Renderer local-X translation: -1.0000 block.
     *
     * Cab floor model geometry:
     *   X 4..20, Y 12..14, Z -11..11
     *   -> center X = 12 / 16 - 1.0 = -0.25 block
     *   -> floor Y = 12 / 16 = 0.75 block
     *
     * Chimney model geometry:
     *   X -42..-36, Y 10..35, Z -3..3
     *   -> center X = -39 / 16 - 1.0 = -3.4375 block
     *   -> chimney top Y = 35 / 16 = 2.1875 block
     *
     * The shared Small Steam anchor defaults remain frozen.
     */
    private static final double BR01_DRIVER_SEAT_BACK_OFFSET = -0.25D;
    private static final double BR01_DRIVER_SEAT_Y_OFFSET = 0.75D;
    private static final double BR01_DRIVER_SEAT_SIDE_OFFSET = 0.0D;

    private static final double BR01_CHIMNEY_FRONT_OFFSET = 3.4375D;
    private static final double BR01_CHIMNEY_TOP_Y_OFFSET = 2.1875D;
    private static final double BR01_CHIMNEY_SIDE_OFFSET = 0.0D;

    @Override
    protected double getDriverSeatBackOffset() {
        return BR01_DRIVER_SEAT_BACK_OFFSET;
    }

    @Override
    protected double getDriverSeatYOffset() {
        return BR01_DRIVER_SEAT_Y_OFFSET;
    }

    @Override
    protected double getDriverSeatSideOffset() {
        return BR01_DRIVER_SEAT_SIDE_OFFSET;
    }

    @Override
    protected double getChimneyFrontOffset() {
        return BR01_CHIMNEY_FRONT_OFFSET;
    }

    @Override
    protected double getChimneyTopYOffset() {
        return BR01_CHIMNEY_TOP_Y_OFFSET;
    }

    @Override
    protected double getChimneySideOffset() {
        return BR01_CHIMNEY_SIDE_OFFSET;
    }

    /*
     * Step 8.5.1 - BR01 realism specification.
     *
     * locomotive service mass: 108.9 t
     * maximum forward speed: 130 km/h
     * reverse limit: 50 km/h
     *
     * 300 t traction-reference mass represents approximately
     * 150 kN starting tractive effort at the frozen
     * 0.500 m/s^2 Traincraft base acceleration.
     */
    private static final double BR01_MASS_T = 108.9D;
    private static final double BR01_TRACTION_REFERENCE_MASS_T = 300.0D;
    private static final double BR01_BASE_ACCELERATION_MPS2 = 0.500D;
    private static final double BR01_MAX_FORWARD_BPT = 130.0D / 72.0D;
    private static final double BR01_MAX_REVERSE_BPT = 50.0D / 72.0D;

    @Override
    protected double getRealismLocomotiveMassTons() {
        return BR01_MASS_T;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return BR01_TRACTION_REFERENCE_MASS_T;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return BR01_BASE_ACCELERATION_MPS2;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return BR01_MAX_FORWARD_BPT;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return BR01_MAX_REVERSE_BPT;
    }
    public Br01SteamLocomotive(EntityType<? extends Br01SteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_BR01.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_BR01.get());
    }
}
