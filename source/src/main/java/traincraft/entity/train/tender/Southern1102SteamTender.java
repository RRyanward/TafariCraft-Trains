/* Step 8.4.1 Batch B2: Southern 1102 Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class Southern1102SteamTender extends SteamTender {
    public Southern1102SteamTender(EntityType<? extends Southern1102SteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "Southern 1102 Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_SOUTHERN1102.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_SOUTHERN1102.get());
    }
}
