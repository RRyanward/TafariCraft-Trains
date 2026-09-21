/*

 * Traincraft

 * Copyright (c) 2011-2020.

 *

 * Minecraft 1.20.1 port registry layer.

 * Original project: https://github.com/Traincraft/Traincraft

 * Distributed under LGPL-v3.0.

 */

package traincraft.registry;



import net.minecraft.world.item.Item;

import net.minecraftforge.registries.DeferredRegister;

import net.minecraftforge.registries.ForgeRegistries;

import net.minecraftforge.registries.RegistryObject;

import traincraft.Traincraft;

import traincraft.item.RollingStockItem;

import traincraft.item.ConnectorItem;



import java.util.function.Supplier;



import java.util.ArrayList;

import java.util.Collections;

import java.util.List;



/** Modern 1.20.1 item registry. Advanced item behavior is restored in later phases. */

public final class TCItems {

    public static final DeferredRegister<Item> ITEMS =

            DeferredRegister.create(ForgeRegistries.ITEMS, Traincraft.MOD_ID);



    private static final List<RegistryObject<Item>> CREATIVE_ITEMS = new ArrayList<>();



    public static final RegistryObject<Item> WRENCH = simple("wrench", new Item.Properties().stacksTo(1));

    public static final RegistryObject<Item> SKIN_CHANGER = simple("skin_changer", new Item.Properties().stacksTo(1));

    public static final RegistryObject<Item> CANISTER = simple("canister", new Item.Properties().stacksTo(16));

    public static final RegistryObject<Item> CONNECTOR = custom(

            "connector",

            () -> new ConnectorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GUIDE = simple("guide", new Item.Properties().stacksTo(1));



    /** First functional rolling-stock placement item in the 1.20.1 port. */

    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SMALL = custom(

            "locomotive_steam_small",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SMALL));



    /** Step 8.1.0 Heavy Steam locomotive placement item. */

    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_HEAVY = custom(

            "locomotive_steam_heavy",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_HEAVY));



    /** Step 8.2.1 USSR 0-5-0 steam locomotive placement item. */

    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_USSR = custom(

            "locomotive_steam_ussr",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_USSR));



    /** Step 7.0 classic tender placement item. */

    public static final RegistryObject<Item> STEAM_TENDER = custom(

            "steam_tender",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER));



    /** Step 8.1.0 Heavy Steam tender placement item. */

    public static final RegistryObject<Item> STEAM_TENDER_HEAVY = custom(

            "steam_tender_heavy",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_HEAVY));



    /** Step 8.2.1 USSR 0-5-0 tender placement item. */

    public static final RegistryObject<Item> STEAM_TENDER_USSR = custom(

            "steam_tender_ussr",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_USSR));



    /** Step 8.3.0 BR01 Steam Locomotive placement item. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BR01 = custom(
            "locomotive_steam_br01",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BR01));

    /** Step 8.3.0 BR01 Tender placement item. */
    public static final RegistryObject<Item> STEAM_TENDER_BR01 = custom(
            "steam_tender_br01",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_BR01));

    /** Step 8.3.0 Forney Steam Locomotive placement item. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_FORNEY = custom(
            "locomotive_steam_forney",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_FORNEY));

    /** Step 8.3.0 Mogul Steam Locomotive placement item. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_MOGUL = custom(
            "locomotive_steam_mogul",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_MOGUL));

    /** Step 8.3.0 Shay Steam Locomotive placement item. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SHAY = custom(
            "locomotive_steam_shay",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SHAY));

    /** Step 7.3.0 classic 36-slot Freight Cart placement item. */

    public static final RegistryObject<Item> FREIGHT_CART = custom(

            "freight_cart",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.FREIGHT_CART));



    /** Step 7.3.1 classic Passenger Blue coach placement item. */

    public static final RegistryObject<Item> PASSENGER_COACH_BLUE = custom(

            "passenger_coach_blue",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.PASSENGER_COACH_BLUE));



    /** Step 7.3.2 classic caboose placement item. */

    public static final RegistryObject<Item> CABOOSE = custom(

            "caboose",

            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.CABOOSE));



    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_ADLER = custom(
            "locomotive_steam_adler",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_ADLER, false, "Adler Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_ADLER = custom(
            "steam_tender_adler",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_ADLER, false, "Adler Tender"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BR80 = custom(
            "locomotive_steam_br80",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BR80, false, "BR80 Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_U57 = custom(
            "locomotive_steam_u57",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_U57, false, "U57 Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_U57 = custom(
            "steam_tender_u57",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_U57, false, "U57 Tender"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_CHEREPANOV = custom(
            "locomotive_steam_cherepanov",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_CHEREPANOV, false, "Cherepanov Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_PANNIER = custom(
            "locomotive_steam_pannier",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_PANNIER, false, "Pannier Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_CLIMAX = custom(
            "locomotive_steam_climax",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_CLIMAX, false, "Climax Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_C41 = custom(
            "locomotive_steam_c41",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_C41, false, "C41 Steam Locomotive"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_C41 = custom(
            "steam_tender_c41",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_C41, false, "C41 Tender"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_FOWLER = custom(
            "locomotive_steam_fowler",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_FOWLER, false, "Fowler 4F Steam Locomotive"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_FOWLER = custom(
            "steam_tender_fowler",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_FOWLER, false, "Fowler 4F Tender"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SOUTHERN1102 = custom(
            "locomotive_steam_southern1102",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SOUTHERN1102, false, "Southern 1102 Steam Locomotive"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_SOUTHERN1102 = custom(
            "steam_tender_southern1102",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_SOUTHERN1102, false, "Southern 1102 Tender"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_HALL_CLASS = custom(
            "locomotive_steam_hall_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_HALL_CLASS, false, "Hall Class Steam Locomotive"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_KING_CLASS = custom(
            "locomotive_steam_king_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_KING_CLASS, false, "King Class Steam Locomotive"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_MILW_CLASS_A = custom(
            "locomotive_steam_milw_class_a",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_MILW_CLASS_A, false, "MILW Class A Steam Locomotive"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_MILW = custom(
            "steam_tender_milw",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_MILW, false, "MILW Tender"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> MILW_BAGGAGE_CAR = custom(
            "milw_baggage_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.MILW_BAGGAGE_CAR, false, "MILW Baggage Car"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<Item> MILW_PASSENGER_CAR = custom(
            "milw_passenger_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.MILW_PASSENGER_CAR, false, "MILW Passenger Car"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_A4_MALLARD = custom(
            "locomotive_steam_a4_mallard",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_A4_MALLARD, false, "A4 Mallard Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_A4_MALLARD = custom(
            "steam_tender_a4_mallard",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_A4_MALLARD, false, "A4 Mallard Tender"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_C62 = custom(
            "locomotive_steam_c62",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_C62, false, "C62 Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_C62 = custom(
            "steam_tender_c62",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_C62, false, "C62 Tender"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_D51 = custom(
            "locomotive_steam_d51",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_D51, false, "D51 Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_D51_LONG = custom(
            "locomotive_steam_d51_long",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_D51_LONG, false, "D51 Long Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_D51 = custom(
            "steam_tender_d51",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_D51, false, "D51 Tender"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_S100_UK = custom(
            "locomotive_steam_s100_uk",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_S100_UK, false, "USATC S100 UK Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_S100_US = custom(
            "locomotive_steam_s100_us",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_S100_US, false, "USATC S100 US Steam Locomotive"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<Item> MILW_TAIL_CAR = custom(
            "milw_tail_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.MILW_TAIL_CAR, false, "MILW Tail Car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_GS4 = custom(
            "locomotive_steam_gs4",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_GS4, false, "GS4 Steam Locomotive"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_GS4 = custom(
            "steam_tender_gs4",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_GS4, false, "GS4 Tender"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> GS4_BAGGAGE_CAR = custom(
            "gs4_baggage_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.GS4_BAGGAGE_CAR, false, "GS4 Baggage Car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> GS4_PASSENGER_CAR = custom(
            "gs4_passenger_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.GS4_PASSENGER_CAR, false, "GS4 Passenger Car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> GS4_OBSERVATORY_CAR = custom(
            "gs4_observatory_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.GS4_OBSERVATORY_CAR, false, "GS4 Observatory Car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> GS4_TAIL_CAR = custom(
            "gs4_tail_car",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.GS4_TAIL_CAR, false, "GS4 Tail Car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BERKSHIRE_1225 = custom(
            "locomotive_steam_berkshire_1225",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_1225, false, "Berkshire 1225 Steam Locomotive"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BERKSHIRE_765 = custom(
            "locomotive_steam_berkshire_765",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_765, false, "Berkshire 765 Steam Locomotive"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_BERKSHIRE = custom(
            "steam_tender_berkshire",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_BERKSHIRE, false, "Berkshire Tender"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_4000_GALLON = custom(
            "steam_tender_4000_gallon",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_4000_GALLON, false, "4000 Gallon Tender"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SHAY_3_TRUCK = custom(
            "locomotive_steam_shay_3_truck",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SHAY_3_TRUCK, false, "Shay 3-Truck Steam Locomotive"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_SHAY_3_TRUCK = custom(
            "steam_tender_shay_3_truck",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_SHAY_3_TRUCK, false, "Shay 3-Truck Tender"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BR_BLACK_5 = custom(
            "locomotive_steam_br_black_5",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BR_BLACK_5, false, "BR Black 5 Steam Locomotive"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_BR_BLACK_5 = custom(
            "steam_tender_br_black_5",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_BR_BLACK_5, false, "BR Black 5 Tender"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_BR_BRITANNIA = custom(
            "locomotive_steam_br_britannia",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_BR_BRITANNIA, false, "BR Britannia Steam Locomotive"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_BR1 = custom(
            "steam_tender_br1",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_BR1, false, "BR1 Tender"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_MIDLAND_COMPOUND = custom(
            "locomotive_steam_midland_compound",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_MIDLAND_COMPOUND, false, "Midland Compound Steam Locomotive"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_MIDLAND_COMPOUND = custom(
            "steam_tender_midland_compound",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_MIDLAND_COMPOUND, false, "Midland Compound Tender"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_STAR_CLASS = custom(
            "locomotive_steam_star_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_STAR_CLASS, false, "Star Class Steam Locomotive"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_STAR_CLASS = custom(
            "steam_tender_star_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_STAR_CLASS, false, "Star Class Tender"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_CORONATION_CLASS = custom(
            "locomotive_steam_coronation_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_CORONATION_CLASS, false, "Coronation Class Steam Locomotive"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_CORONATION_CLASS = custom(
            "steam_tender_coronation_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_CORONATION_CLASS, false, "Coronation Class Tender"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_RW_TYPE_2 = custom(
            "locomotive_steam_rw_type_2",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_2, false, "RW Type 2 Steam Locomotive"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_RW_TYPE_2 = custom(
            "steam_tender_rw_type_2",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_RW_TYPE_2, false, "RW Type 2 Tender"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_PE = custom(
            "locomotive_steam_pe",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_PE, false, "PE Steam Locomotive"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_PE = custom(
            "steam_tender_pe",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_PE, false, "PE Tender"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SKOOKUM = custom(
            "locomotive_steam_skookum",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SKOOKUM, false, "Skookum Steam Locomotive"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_SKOOKUM = custom(
            "steam_tender_skookum",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_SKOOKUM, false, "Skookum Tender"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_GWR_42XX = custom(
            "locomotive_steam_gwr_42xx",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_GWR_42XX, false, "GWR 42xx Steam Locomotive"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_GWR_72XX = custom(
            "locomotive_steam_gwr_72xx",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_GWR_72XX, false, "GWR 72xx Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_RW_TYPE_3 = custom(
            "locomotive_steam_rw_type_3",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_3, false, "RW Type 3 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_GWR_101_CLASS = custom(
            "locomotive_steam_gwr_101_class",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_GWR_101_CLASS, false, "GWR 101 Class Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_WWCP_062T = custom(
            "locomotive_steam_wwcp_062t",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_WWCP_062T, false, "WWCP Class 0-6-2T Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_J50 = custom(
            "locomotive_steam_j50",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_J50, false, "J50 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SENTINEL_Y3 = custom(
            "locomotive_steam_sentinel_y3",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SENTINEL_Y3, false, "Sentinel Y3 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_4_4_0 = custom(
            "locomotive_steam_4_4_0",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_4_4_0, false, "4-4-0 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> STEAM_TENDER_4_4_0 = custom(
            "steam_tender_4_4_0",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.STEAM_TENDER_4_4_0, false, "4-4-0 Steam Tender"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_LSSP7 = custom(
            "locomotive_steam_lssp7",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_LSSP7, false, "LSSP7 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_ALICE_0_4_0 = custom(
            "locomotive_steam_alice_0_4_0",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_ALICE_0_4_0, false, "Alice 0-4-0 Steam Locomotive"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_VB_0_4_0 = custom(
            "locomotive_steam_vb_0_4_0",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_VB_0_4_0, false, "VB 0-4-0 Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_C41_080 = custom(
            "locomotive_steam_c41_080",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_C41_080, false, "C41 0-8-0 Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_ALCO_SC4 = custom(
            "locomotive_steam_alco_sc4",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_ALCO_SC4, false, "Alco SC4 Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_C41T = custom(
            "locomotive_steam_c41t",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_C41T, false, "C41T Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_VB_SHAY = custom(
            "locomotive_steam_vb_shay",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_VB_SHAY, false, "VB Shay Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_GLYN_042T = custom(
            "locomotive_steam_glyn_042t",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_GLYN_042T, false, "GLYN 0-4-2T Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_262T = custom(
            "locomotive_steam_262t",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_262T, false, "2-6-2T Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_SNOW_PLOW = custom(
            "locomotive_steam_snow_plow",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_SNOW_PLOW, false, "Steam Snow Plow Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_VB_SHAY_2 = custom(
            "locomotive_steam_vb_shay_2",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_VB_SHAY_2, false, "VB Shay 2 Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_CLIMAX_2 = custom(
            "locomotive_steam_climax_2",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_CLIMAX_2, false, "Climax 2 Steam Locomotive"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<Item> LOCOMOTIVE_STEAM_C11 = custom(
            "locomotive_steam_c11",
            () -> new RollingStockItem<>(new Item.Properties().stacksTo(1), TCEntities.LOCOMOTIVE_STEAM_C11, false, "C11 Steam Locomotive"));

    public static final RegistryObject<Item> STEEL_INGOT = simple("steel_ingot");

    public static final RegistryObject<Item> STEEL_DUST = simple("steel_dust");

    public static final RegistryObject<Item> COAL_DUST = simple("coal_dust");

    public static final RegistryObject<Item> GRAPHITE = simple("graphite");

    public static final RegistryObject<Item> STEEL_FIREBOX = simple("steel_firebox");

    public static final RegistryObject<Item> STEEL_BOGIE = simple("steel_bogie");

    public static final RegistryObject<Item> STEEL_FRAME = simple("steel_frame");

    public static final RegistryObject<Item> STEEL_CABIN = simple("steel_cabin");

    public static final RegistryObject<Item> STEEL_CHIMNEY = simple("steel_chimney");

    public static final RegistryObject<Item> PLASTIC = simple("plastic");

    public static final RegistryObject<Item> COPPER_INGOT = simple("copper_ingot");



    public static final RegistryObject<Item> BALLOON = simple("balloon");

    public static final RegistryObject<Item> BOGIE_IRON = simple("bogie_iron");

    public static final RegistryObject<Item> BOGIE_WOOD = simple("bogie_wood");

    public static final RegistryObject<Item> BOILER_IRON = simple("boiler_iron");

    public static final RegistryObject<Item> BOILER_STEEL = simple("boiler_steel");

    public static final RegistryObject<Item> CAB_IRON = simple("cab_iron");

    public static final RegistryObject<Item> CAB_WOOD = simple("cab_wood");

    public static final RegistryObject<Item> CAMSHAFT = simple("camshaft");

    public static final RegistryObject<Item> CHIMNEY_IRON = simple("chimney_iron");

    public static final RegistryObject<Item> CIRCUIT = simple("circuit");

    public static final RegistryObject<Item> CONTROLS = simple("controls");

    public static final RegistryObject<Item> CYLINDER = simple("cylinder");

    public static final RegistryObject<Item> ENGINE_DIESEL = simple("engine_diesel");

    public static final RegistryObject<Item> ENGINE_ELECTRIC = simple("engine_electric");

    public static final RegistryObject<Item> ENGINE_STEAM = simple("engine_steam");

    public static final RegistryObject<Item> FIBERGLASS_PLATE = simple("fiberglass_plate");

    public static final RegistryObject<Item> FIREBOX_IRON = simple("firebox_iron");

    public static final RegistryObject<Item> FRAME_IRON = simple("frame_iron");

    public static final RegistryObject<Item> FRAME_WOOD = simple("frame_wood");

    public static final RegistryObject<Item> PISTON = simple("piston");

    public static final RegistryObject<Item> PROPELLER = simple("propeller");

    public static final RegistryObject<Item> RAIL_COPPER = simple("rail_copper");

    public static final RegistryObject<Item> RAIL_STEEL = simple("rail_steel");

    public static final RegistryObject<Item> REINFORCED_PLATE = simple("reinforced_plate");

    public static final RegistryObject<Item> SEATS = simple("seats");

    public static final RegistryObject<Item> SIGNAL = simple("signal");

    public static final RegistryObject<Item> TRANSFORMER = simple("transformer");

    public static final RegistryObject<Item> TRANSMISSION = simple("transmission");

    public static final RegistryObject<Item> FINE_COPPER_WIRE = simple("fine_copper_wire");

    public static final RegistryObject<Item> CHUNK_LOADER_ACTIVATOR =

            simple("chunk_loader_activator", new Item.Properties().stacksTo(1));



    private TCItems() {

    }



    public static List<RegistryObject<Item>> creativeItems() {

        return Collections.unmodifiableList(CREATIVE_ITEMS);

    }



    private static RegistryObject<Item> simple(String name) {

        return simple(name, new Item.Properties());

    }



    private static RegistryObject<Item> simple(String name, Item.Properties properties) {

        return custom(name, () -> new Item(properties));

    }



    private static RegistryObject<Item> custom(String name, Supplier<? extends Item> supplier) {

        RegistryObject<Item> item = ITEMS.register(name, supplier);

        CREATIVE_ITEMS.add(item);

        return item;

    }


    // Step 9.2a-r1 - original Traincraft multi-length straight track pieces.
    public static final RegistryObject<Item> TRACK_MEDIUM_STRAIGHT = custom("track_medium_straight",
            () -> new traincraft.item.track.LegacyStraightTrackItem(new Item.Properties().stacksTo(64), 3));
    public static final RegistryObject<Item> TRACK_LONG_STRAIGHT = custom("track_long_straight",
            () -> new traincraft.item.track.LegacyStraightTrackItem(new Item.Properties().stacksTo(64), 6));
    public static final RegistryObject<Item> TRACK_VERY_LONG_STRAIGHT = custom("track_very_long_straight",
            () -> new traincraft.item.track.LegacyStraightTrackItem(new Item.Properties().stacksTo(64), 12));
    public static final RegistryObject<Item> TRACK_MEDIUM_CURVE = custom("track_medium_curve",
            () -> new traincraft.item.track.LegacyMediumCurveTrackItem(new Item.Properties().stacksTo(64)));

    // Step 9.3a - batched original Traincraft dedicated track placement items.
    public static final RegistryObject<Item> CURVE_BIG = custom("track_curve_big",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_BIG,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_BIG_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_VERY_BIG = custom("track_curve_very_big",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_VERY_BIG,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_VERY_BIG_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_SUPER_BIG = custom("track_curve_super_big",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_SUPER_BIG,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_SUPER_BIG_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_29X = custom("track_curve_29x",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_29X,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_29X_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_32X = custom("track_curve_32x",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_32X,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_32X_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_MEDIUM_RIGHT = custom("track_curve_45_medium_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_MEDIUM_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_MEDIUM_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_MEDIUM_LEFT = custom("track_curve_45_medium_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_MEDIUM_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_MEDIUM_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_LARGE_RIGHT = custom("track_curve_45_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_LARGE_LEFT = custom("track_curve_45_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_VERY_LARGE_RIGHT = custom("track_curve_45_very_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_VERY_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_VERY_LARGE_LEFT = custom("track_curve_45_very_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_VERY_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_SUPER_LARGE_RIGHT = custom("track_curve_45_super_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_SUPER_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> CURVE_45_SUPER_LARGE_LEFT = custom("track_curve_45_super_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CURVE_45_SUPER_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_SMALL_RIGHT = custom("track_parallel_curve_small_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_SMALL_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_SMALL_LEFT = custom("track_parallel_curve_small_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_SMALL_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_MEDIUM_RIGHT = custom("track_parallel_curve_medium_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_MEDIUM_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_MEDIUM_LEFT = custom("track_parallel_curve_medium_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_MEDIUM_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_LARGE_RIGHT = custom("track_parallel_curve_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> PARALLEL_CURVE_LARGE_LEFT = custom("track_parallel_curve_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.PARALLEL_CURVE_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_SMALL = custom("track_slope_small",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_SMALL,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_SMALL_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_LONG = custom("track_slope_long",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_LONG,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_LONG_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_VERY_LONG = custom("track_slope_very_long",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_VERY_LONG,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_VERY_LONG_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_LARGE_RIGHT = custom("track_slope_curve_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_LARGE_LEFT = custom("track_slope_curve_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_VERY_LARGE_RIGHT = custom("track_slope_curve_very_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_VERY_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_VERY_LARGE_LEFT = custom("track_slope_curve_very_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_VERY_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_SUPER_LARGE_RIGHT = custom("track_slope_curve_super_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_SUPER_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SLOPE_CURVE_SUPER_LARGE_LEFT = custom("track_slope_curve_super_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SLOPE_CURVE_SUPER_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> CROSSING_X = custom("track_crossing_x",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.CROSSING_X,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.CROSSING_X_SEGMENT.get()));
    public static final RegistryObject<Item> DIAMOND_CROSSING = custom("track_diamond_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAMOND_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAMOND_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> DIAMOND_CROSSING_LEFT = custom("track_diamond_crossing_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAMOND_CROSSING_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAMOND_CROSSING_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> DOUBLE_DIAMOND_CROSSING = custom("track_double_diamond_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DOUBLE_DIAMOND_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DOUBLE_DIAMOND_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> DIAGONAL_CROSSING = custom("track_diagonal_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAGONAL_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> DIAGONAL_TWO_WAYS_CROSSING = custom("track_diagonal_two_ways_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_TWO_WAYS_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAGONAL_TWO_WAYS_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> DIAGONAL_FOUR_WAYS_CROSSING = custom("track_diagonal_four_ways_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_FOUR_WAYS_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAGONAL_FOUR_WAYS_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> UNIVERSAL_CROSSING = custom("track_universal_crossing",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.UNIVERSAL_CROSSING,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.UNIVERSAL_CROSSING_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_SMALL_RIGHT = custom("track_switch_small_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_SMALL_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_SMALL_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_SMALL_LEFT = custom("track_switch_small_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_SMALL_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_SMALL_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_MEDIUM_RIGHT = custom("track_switch_medium_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_MEDIUM_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_MEDIUM_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_MEDIUM_LEFT = custom("track_switch_medium_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_MEDIUM_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_MEDIUM_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_LARGE_RIGHT = custom("track_switch_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_LARGE_LEFT = custom("track_switch_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_VERY_LARGE_RIGHT = custom("track_switch_very_large_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_VERY_LARGE_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_VERY_LARGE_LEFT = custom("track_switch_very_large_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_VERY_LARGE_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_45_MEDIUM_RIGHT = custom("track_switch_45_medium_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_45_MEDIUM_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_45_MEDIUM_LEFT = custom("track_switch_45_medium_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_45_MEDIUM_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_PARALLEL_RIGHT = custom("track_switch_parallel_right",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_PARALLEL_RIGHT_SEGMENT.get()));
    public static final RegistryObject<Item> SWITCH_PARALLEL_LEFT = custom("track_switch_parallel_left",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.SWITCH_PARALLEL_LEFT_SEGMENT.get()));
    public static final RegistryObject<Item> DIAGONAL_STRAIGHT_SMALL = custom("track_diagonal_straight_small",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_SMALL,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAGONAL_STRAIGHT_SMALL_SEGMENT.get()));
    public static final RegistryObject<Item> DIAGONAL_STRAIGHT_MEDIUM = custom("track_diagonal_straight_medium",
            () -> new traincraft.item.track.LegacyBatchTrackItem(
                    new Item.Properties().stacksTo(64),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM,
                    () -> (traincraft.block.track.AbstractLegacyBatchRailBlock) TCBlocks.DIAGONAL_STRAIGHT_MEDIUM_SEGMENT.get()));
}
