/* Step 8.4.2 Batch B3: A4 Mallard Tender. Debug intentionally enabled. */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;

import traincraft.registry.TCItems;

public class A4MallardSteamTender extends SteamTender {
    public A4MallardSteamTender(EntityType<? extends A4MallardSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "A4 Mallard Tender");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_A4_MALLARD.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_A4_MALLARD.get());
    }
}
