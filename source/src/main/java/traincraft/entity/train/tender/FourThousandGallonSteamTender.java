/* Step 8.4.3 Batch B4: 4000 Gallon Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.tender.SteamTender;
import traincraft.registry.TCItems;

public class FourThousandGallonSteamTender extends SteamTender {
    public FourThousandGallonSteamTender(EntityType<? extends FourThousandGallonSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "4000 Gallon Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_4000_GALLON.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_4000_GALLON.get());
    }
}
