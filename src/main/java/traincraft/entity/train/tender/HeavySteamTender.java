/*
 * Traincraft Heavy Steam Tender foundation for Minecraft 1.20.1.
 * Step 8.1.0 reuses the proven SteamTender inventory/supply/rail controller,
 * while giving the legacy heavy tender its own item, entity and model-aware
 * coupler reach.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.entity.train.steam.heavy.HeavySteamLocomotive;
import traincraft.registry.TCItems;

public class HeavySteamTender extends SteamTender {
    private static final double MODEL_SCALE_BLOCKS_PER_PIXEL = 0.8D / 16.0D;

    /*
     * The legacy Heavy Steam model is authored along X. Its rendered rear
     * coupler reaches 24 model pixels from the locomotive origin. The Heavy
     * Tender reaches 31 pixels toward its leader and 25 toward its trailing end.
     * These values shift only the existing frozen coupling spacing bands.
     */
    private static final double HEAVY_LOCO_REAR_NUB_REACH =
            24.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.20 blocks
    private static final double HEAVY_TENDER_LEADER_NUB_REACH =
            31.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.55 blocks
    private static final double HEAVY_TENDER_TRAILING_NUB_REACH =
            25.0D * MODEL_SCALE_BLOCKS_PER_PIXEL; // 1.25 blocks

    public HeavySteamTender(EntityType<? extends HeavySteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    protected double getLeaderFacingCouplerReach() {
        return HEAVY_TENDER_LEADER_NUB_REACH;
    }

    @Override
    protected double getTrailingCouplerReach() {
        return HEAVY_TENDER_TRAILING_NUB_REACH;
    }

    @Override
    public double getCouplerTouchTargetDistance(Entity leader) {
        if (leader instanceof HeavySteamLocomotive) {
            return HEAVY_LOCO_REAR_NUB_REACH + this.getLeaderFacingCouplerReach();
        }
        return super.getCouplerTouchTargetDistance(leader);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_HEAVY.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_HEAVY.get());
    }
}
