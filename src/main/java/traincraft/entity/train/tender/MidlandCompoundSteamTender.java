/* Step 8.4.4 Batch B5: Midland Compound Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class MidlandCompoundSteamTender extends SteamTender {
    public MidlandCompoundSteamTender(EntityType<? extends MidlandCompoundSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Midland Compound Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_MIDLAND_COMPOUND.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_MIDLAND_COMPOUND.get());
    }
}
