/* Step 8.4.2 Batch B3: C62 Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;

import traincraft.registry.TCItems;

public class C62SteamTender extends SteamTender {
    public C62SteamTender(EntityType<? extends C62SteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "C62 Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_C62.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_C62.get());
    }
}
