package traincraft.villager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/** Forge-native trade tables; the tiered railway offers persist in vanilla save data. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RailwayTrades {
    private RailwayTrades() {}

    private static Item registeredItem(String id) {
        ResourceLocation key = new ResourceLocation(Traincraft.MOD_ID, id);
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item != null && key.equals(ForgeRegistries.ITEMS.getKey(item)) ? item : null;
    }

    private static void buy(VillagerTradesEvent event, int tier, Item item, int amount) {
        event.getTrades().get(tier).add((entity, rand) ->
                new MerchantOffer(new ItemStack(item, amount), new ItemStack(Items.EMERALD),
                        16, tier * 2, 0.05F));
    }

    private static void sell(VillagerTradesEvent event, int tier, String id, int emeralds, int amount) {
        Item item = registeredItem(id);
        if (item == null) return;
        event.getTrades().get(tier).add((entity, rand) ->
                new MerchantOffer(new ItemStack(Items.EMERALD, emeralds),
                        new ItemStack(item, amount), 12, tier * 4, 0.05F));
    }

    @SubscribeEvent
    public static void trades(VillagerTradesEvent event) {
        VillagerProfession p = event.getType();
        if (p == RailwayVillagers.STATION_CHIEF.get()) {
            buy(event, 1, Items.PAPER, 24);
            sell(event, 1, "transport_rope", 3, 1);
            sell(event, 2, "station_office_desk", 6, 1);
            sell(event, 3, "track_station", 8, 1);
            sell(event, 4, "connector", 8, 1);
            sell(event, 5, "guide", 10, 1);
        } else if (p == RailwayVillagers.RAILWAY_ENGINEER.get()) {
            buy(event, 1, Items.IRON_INGOT, 6);
            sell(event, 1, "wrench", 4, 1);
            sell(event, 2, "railway_toolbench", 7, 1);
            sell(event, 3, "canister", 6, 2);
            sell(event, 4, "connector", 7, 1);
            sell(event, 5, "train_workbench", 12, 1);
        } else if (p == RailwayVillagers.TRACK_WORKER.get()) {
            buy(event, 1, Items.RAIL, 12);
            sell(event, 1, "track_blueprint", 3, 1);
            sell(event, 2, "track_maintenance_bench", 5, 1);
            sell(event, 3, "track_normal", 5, 4);
            sell(event, 4, "tracks_builder", 11, 1);
            sell(event, 5, "track_medium_curve", 10, 2);
        } else if (p == RailwayVillagers.TRAIN_CONDUCTOR.get()) {
            buy(event, 1, Items.PAPER, 24);
            sell(event, 1, "transport_rope", 3, 1);
            sell(event, 2, "ticket_booth", 5, 1);
            sell(event, 3, "connector", 8, 1);
            sell(event, 4, "guide", 8, 1);
            sell(event, 5, "track_station", 11, 1);
        } else if (p == RailwayVillagers.FUEL_DEPOT_OPERATOR.get()) {
            buy(event, 1, Items.COAL, 12);
            sell(event, 1, "canister", 2, 1);
            sell(event, 2, "fuel_depot_counter", 5, 1);
            sell(event, 3, "canister", 6, 4);
            sell(event, 4, "battery", 9, 1);
            sell(event, 5, "track_energy", 10, 2);
        } else if (p == RailwayVillagers.FREIGHT_YARD_CLERK.get()) {
            buy(event, 1, Items.CHEST, 4);
            sell(event, 1, "transport_rope", 3, 1);
            sell(event, 2, "freight_office_desk", 5, 1);
            sell(event, 3, "connector", 6, 1);
            sell(event, 4, "cargo_interface", 8, 1);
            sell(event, 5, "transport_rope", 10, 8);
        }
    }

    /** No trades? Supply a minimal native merchant offer until event tables load. */
    public static void ensureNoviceOffers(Villager villager) {
        if (!villager.getOffers().isEmpty()) return;
        VillagerProfession p = villager.getVillagerData().getProfession();
        Item payment = Items.PAPER;
        String sale = "transport_rope";
        if (p == RailwayVillagers.RAILWAY_ENGINEER.get()) { payment = Items.IRON_INGOT; sale = "wrench"; }
        else if (p == RailwayVillagers.TRACK_WORKER.get()) { payment = Items.RAIL; sale = "track_blueprint"; }
        else if (p == RailwayVillagers.FUEL_DEPOT_OPERATOR.get()) { payment = Items.COAL; sale = "canister"; }
        else if (p == RailwayVillagers.FREIGHT_YARD_CLERK.get()) { payment = Items.CHEST; sale = "transport_rope"; }
        if (p == VillagerProfession.NONE) return;
        villager.getOffers().add(new MerchantOffer(new ItemStack(payment, 8),
                new ItemStack(Items.EMERALD), 16, 2, 0.05F));
        Item saleItem = registeredItem(sale);
        if (saleItem != null) villager.getOffers().add(new MerchantOffer(
                new ItemStack(Items.EMERALD, 3), new ItemStack(saleItem), 12, 2, 0.05F));
    }
}
