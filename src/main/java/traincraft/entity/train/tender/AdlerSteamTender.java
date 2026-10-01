/* Step 8.4.0 Batch B1: Adler Tender. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class AdlerSteamTender extends SteamTender {
    public AdlerSteamTender(EntityType<? extends AdlerSteamTender> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "Adler Tender"); }
    @Override protected Item getDropItem() { return TCItems.STEAM_TENDER_ADLER.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.STEAM_TENDER_ADLER.get()); }
}
