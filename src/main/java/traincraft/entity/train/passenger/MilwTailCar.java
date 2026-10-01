/* Step 8.4.2 Batch B3: MILW Tail Car. Debug intentionally enabled. */
package traincraft.entity.train.passenger;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;

import traincraft.registry.TCItems;

public class MilwTailCar extends PassengerCoachBlue {
    public MilwTailCar(EntityType<? extends MilwTailCar> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "MILW Tail Car");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.MILW_TAIL_CAR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.MILW_TAIL_CAR.get());
    }
}
