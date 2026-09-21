/* Step 8.4.1 Batch B2: MILW Baggage Car. Debug intentionally enabled. */
package traincraft.entity.train.freight;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class MilwBaggageCar extends FreightCart {
    public MilwBaggageCar(EntityType<? extends MilwBaggageCar> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "MILW Baggage Car");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.MILW_BAGGAGE_CAR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.MILW_BAGGAGE_CAR.get());
    }
}
