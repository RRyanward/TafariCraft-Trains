/*
 * Traincraft Heavy Steam Locomotive foundation for Minecraft 1.20.1.
 * Step 8.1.0 deliberately reuses the proven SmallSteamLocomotive steam,
 * controls, braking, GUI/HUD, rail logic and coupling behavior while creating
 * a distinct Heavy Steam entity/item.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.steam.heavy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.registry.TCItems;

public class HeavySteamLocomotive extends SmallSteamLocomotive {
    /*
     * STEP_8_5_3E_VISUAL_ANCHORS
     * Step 8.5.3e rollout candidate - model/mesh-derived visual anchors.
     * Shared Small Steam defaults and renderer transforms remain frozen.
     */
    private static final double VISUAL_DRIVER_SEAT_BACK_OFFSET = 0.3712D;
    private static final double VISUAL_DRIVER_SEAT_Y_OFFSET = 0.6500D;
    private static final double VISUAL_DRIVER_SEAT_SIDE_OFFSET = 0.0000D;

    private static final double VISUAL_CHIMNEY_FRONT_OFFSET = 2.8750D;
    private static final double VISUAL_CHIMNEY_TOP_Y_OFFSET = 2.0625D;
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
     * STEP_8_5_7_R1_REALISM
     * Representative heavy-freight 4-6-0 / Ten-Wheeler calibration.
     * Mass / TE basis are representative; acceleration and speed caps are gameplay/service calibrations.
     */
    private static final double HEAVY_REALISM_MASS_T = 78.0D;
    private static final double HEAVY_REALISM_TRACTION_REFERENCE_MASS_T = 344.8D;
    private static final double HEAVY_REALISM_BASE_ACCELERATION_MPS2 = 0.400D;
    private static final double HEAVY_REALISM_MAX_FORWARD_BPT = 64.4D / 72.0D;
    private static final double HEAVY_REALISM_MAX_REVERSE_BPT = 64.4D / 72.0D;

    @Override
    protected double getRealismLocomotiveMassTons() {
        return HEAVY_REALISM_MASS_T;
    }

    @Override
    protected double getRealismTractionReferenceMassTons() {
        return HEAVY_REALISM_TRACTION_REFERENCE_MASS_T;
    }

    @Override
    protected double getRealismBaseAccelerationMps2() {
        return HEAVY_REALISM_BASE_ACCELERATION_MPS2;
    }

    @Override
    protected double getRealismMaxForwardSpeedBlocksPerTick() {
        return HEAVY_REALISM_MAX_FORWARD_BPT;
    }

    @Override
    protected double getRealismMaxReverseSpeedBlocksPerTick() {
        return HEAVY_REALISM_MAX_REVERSE_BPT;
    }

    public HeavySteamLocomotive(EntityType<? extends HeavySteamLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.LOCOMOTIVE_STEAM_HEAVY.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.LOCOMOTIVE_STEAM_HEAVY.get());
    }
}
