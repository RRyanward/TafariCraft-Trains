/* Step 8.4.6 Batch B7: 4-4-0 Steam Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class FourFourZeroSteamTender extends SteamTender {
    public FourFourZeroSteamTender(EntityType<? extends FourFourZeroSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "4-4-0 Steam Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_4_4_0.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_4_4_0.get());
    }
}
