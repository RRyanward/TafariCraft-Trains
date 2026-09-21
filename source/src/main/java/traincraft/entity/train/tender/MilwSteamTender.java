/* Step 8.4.1 Batch B2: MILW Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class MilwSteamTender extends SteamTender {
    public MilwSteamTender(EntityType<? extends MilwSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "MILW Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_MILW.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_MILW.get());
    }
}
