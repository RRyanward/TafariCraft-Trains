/* Step 8.4.0 Batch B1: U57 Tender. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class U57SteamTender extends SteamTender {
    public U57SteamTender(EntityType<? extends U57SteamTender> type, Level level) { super(type, level); }
    @Override public void tick() { super.tick(); LegacyRollingStockDebug.logEntity(this, "U57 Tender"); }
    @Override protected Item getDropItem() { return TCItems.STEAM_TENDER_U57.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(TCItems.STEAM_TENDER_U57.get()); }
}
