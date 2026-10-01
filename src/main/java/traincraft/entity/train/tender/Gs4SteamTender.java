/* Step 8.4.3 Batch B4: GS4 Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class Gs4SteamTender extends SteamTender {
    public Gs4SteamTender(EntityType<? extends Gs4SteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "GS4 Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_GS4.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_GS4.get());
    }
}
