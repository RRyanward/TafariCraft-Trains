package traincraft.villager;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.registry.TCItems;

/** One home for the six railway eggs: Minecraft's vanilla Spawn Eggs tab. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RailwayEggCreativeTab {
    private RailwayEggCreativeTab() {}

    @SubscribeEvent
    public static void fillSpawnEggTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.SPAWN_EGGS.equals(event.getTabKey())) return;
        event.accept(TCItems.STATION_CHIEF_SPAWN_EGG.get());
        event.accept(TCItems.RAILWAY_ENGINEER_SPAWN_EGG.get());
        event.accept(TCItems.TRACK_WORKER_SPAWN_EGG.get());
        event.accept(TCItems.TRAIN_CONDUCTOR_SPAWN_EGG.get());
        event.accept(TCItems.FUEL_DEPOT_OPERATOR_SPAWN_EGG.get());
        event.accept(TCItems.FREIGHT_YARD_CLERK_SPAWN_EGG.get());
    }
}
