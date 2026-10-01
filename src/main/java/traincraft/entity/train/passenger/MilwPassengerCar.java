/* Step 8.4.1 Batch B2: MILW Passenger Car. Debug intentionally enabled. */
package traincraft.entity.train.passenger;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.registry.TCItems;

public class MilwPassengerCar extends PassengerCoachBlue {
    public MilwPassengerCar(EntityType<? extends MilwPassengerCar> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "MILW Passenger Car");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.MILW_PASSENGER_CAR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.MILW_PASSENGER_CAR.get());
    }
}
