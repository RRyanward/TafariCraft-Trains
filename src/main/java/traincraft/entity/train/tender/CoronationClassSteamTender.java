/* Step 8.4.5 Batch B6: Coronation Class Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class CoronationClassSteamTender extends SteamTender {
    public CoronationClassSteamTender(EntityType<? extends CoronationClassSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Coronation Class Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_CORONATION_CLASS.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_CORONATION_CLASS.get());
    }
}
