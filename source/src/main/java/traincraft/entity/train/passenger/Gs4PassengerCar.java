/* Step 8.4.3 Batch B4: GS4 Passenger Car. Debug intentionally enabled. */
package traincraft.entity.train.passenger;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.debug.LegacyRollingStockDebug;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.registry.TCItems;

public class Gs4PassengerCar extends PassengerCoachBlue {
    public Gs4PassengerCar(EntityType<? extends Gs4PassengerCar> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        LegacyRollingStockDebug.logEntity(this, "GS4 Passenger Car");
    }

    @Override
    protected Item getDropItem() {
        return TCItems.GS4_PASSENGER_CAR.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TCItems.GS4_PASSENGER_CAR.get());
    }
}
