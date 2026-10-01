/* Step 8.4.4 Batch B5: Star Class Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class StarClassSteamTender extends SteamTender {
    public StarClassSteamTender(EntityType<? extends StarClassSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Star Class Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_STAR_CLASS.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_STAR_CLASS.get());
    }
}
