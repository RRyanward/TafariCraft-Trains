/*
 * Step 8.3.0 BR01 tender foundation.
 * Reuses the proven SteamTender inventory/transfer/rail baseline.
 */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.registry.TCItems;

public class Br01SteamTender extends SteamTender {
    /*
     * Step 8.5.2b - BR01 2'2' T32 tender specification.
     *
     * Traincraft operating mass: 75.0 t
     * Water capacity: 32 m3 -> 32,000 mB
     *
     * The 140 km/h straight catch-up ceiling is controller headroom only.
     * It lets the tender recover a small spacing error behind a 130 km/h
     * BR01 without changing the locomotive's historical speed limit.
     */
    private static final double BR01_T32_MASS_T = 75.0D;
    private static final int BR01_T32_WATER_CAPACITY_MB = 32000;
    private static final double BR01_T32_STRAIGHT_CATCHUP_BPT = 140.0D / 72.0D;

    @Override
    public double getRealismMassOverrideTons() {
        return BR01_T32_MASS_T;
    }

    @Override
    public int getWaterCapacity() {
        return BR01_T32_WATER_CAPACITY_MB;
    }

    @Override
    protected double getCoupledStraightCatchupSpeedCap() {
        return BR01_T32_STRAIGHT_CATCHUP_BPT;
    }
    public Br01SteamTender(EntityType<? extends Br01SteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_BR01.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_BR01.get());
    }
}
