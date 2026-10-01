/*
 * Traincraft USSR 0-5-0 Tender foundation for Minecraft 1.20.1.
 * Step 8.2.1 gives the production ER/USSR tender its own item/entity while
 * preserving the proven SteamTender inventory, transfer and rail controller.
 * Model-specific coupling tuning is intentionally deferred until the visual
 * orientation and rail alignment are runtime-confirmed.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train.tender;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.registry.TCItems;

public class UssrSteamTender extends SteamTender {
    public UssrSteamTender(EntityType<? extends UssrSteamTender> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDropItem() {
        return TCItems.STEAM_TENDER_USSR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.STEAM_TENDER_USSR.get());
    }
}
