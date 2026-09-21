/* Step 8.4.4 Batch B5: BR Black 5 Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class BrBlack5SteamTender extends SteamTender {
    public BrBlack5SteamTender(EntityType<? extends BrBlack5SteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "BR Black 5 Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_BR_BLACK_5.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_BR_BLACK_5.get());
    }
}
