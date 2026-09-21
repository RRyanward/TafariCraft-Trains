/* Step 8.4.3 Batch B4: GS4 Baggage Car. Debug intentionally enabled. */
package traincraft.entity.train.freight;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.freight.FreightCart;
import traincraft.registry.TCItems;

public class Gs4BaggageCar extends FreightCart {
    public Gs4BaggageCar(EntityType<? extends Gs4BaggageCar> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "GS4 Baggage Car");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.GS4_BAGGAGE_CAR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.GS4_BAGGAGE_CAR.get());
    }
}
