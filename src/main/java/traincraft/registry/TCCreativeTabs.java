package traincraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.item.RollingStockItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TafariCraft creative inventory organization.
 *
 * Tab layout:
 *   TafariCraft - Misc   : Wrench -> Connector -> Skin Changer -> Chunk Loader (if registered),
 *                          then workshop/crafting blocks, machines, support blocks, parts/materials.
 *   TafariCraft - Tracks : proven basic track pieces first, then curve/slope/crossing/switch
 *                          options, specialty functional rails and trackside crossing equipment.
 *   TafariCraft - Trains : locomotives short->long, tenders short->long, cars,
 *                          then animal/mob carriers and TCT tankers.
 *
 * Ordering only. Registry IDs, placement logic, track math, physics and entity behavior are untouched.
 */
public final class TCCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Traincraft.MOD_ID);

    private static final List<String> MISC_START = List.of(
            "wrench",
            "connector",
            "skin_changer"
    );

    // DEV50a3r: railway workstations are pinned to the end of Misc.
    // Spawn eggs deliberately appear only in vanilla Spawn Eggs.
    private static final List<String> RAILWAY_WORKSTATIONS = List.of(
            "station_office_desk", "railway_toolbench", "track_maintenance_bench",
            "ticket_booth", "fuel_depot_counter", "freight_office_desk"
    );

    private static boolean isRailwayVillagerEgg(String id) {
        return "station_chief_spawn_egg".equals(id)
                || "railway_engineer_spawn_egg".equals(id)
                || "track_worker_spawn_egg".equals(id)
                || "train_conductor_spawn_egg".equals(id)
                || "fuel_depot_operator_spawn_egg".equals(id)
                || "freight_yard_clerk_spawn_egg".equals(id);
    }

    private static final List<String> MISC_WORKSHOP = List.of(
            "train_workbench",
            "assembly_table_1",
            "assembly_table_2",
            "assembly_table_3",
            "hearth_furnace",
            "distillery"
    );

    private static final List<String> MISC_POWER_AND_UTILITY = List.of(
            "battery"
    );

    private static final List<String> MISC_SUPPORT_BLOCKS = List.of(
            "stopper",
            "bridge_pillar",
            "ballast"
    );

    private static final List<String> BASIC_TRACKS = List.of(
            "track_normal",
            "track_medium_straight",
            "track_long_straight",
            "track_very_long_straight",
            "track_medium_curve"
    );
    // TC_BATCH8_R4A3F_BASIC_ENERGY_PARITY
    // Explicitly pin the five basic Energy counterparts so none can be buried
    // or omitted by fallback ordering.
    private static final List<String> BASIC_ENERGY_TRACKS = List.of(
            "track_energy",
            "track_medium_straight_energy",
            "track_long_straight_energy",
            "track_very_long_straight_energy",
            "track_medium_curve_energy"
    );

    private static final List<String> CURVE_OPTIONS = List.of(
            "track_curve_big",
            "track_curve_very_big",
            "track_curve_super_big",
            "track_curve_29x",
            "track_curve_32x",

            "track_curve_45_medium_right",
            "track_curve_45_large_right",
            "track_curve_45_very_large_right",
            "track_curve_45_super_large_right",
            "track_parallel_curve_small_right",
            "track_parallel_curve_medium_right",
            "track_parallel_curve_large_right"
    );

    private static final List<String> SLOPE_OPTIONS = List.of(
            "track_slope_small",
            "track_slope_long",
            "track_slope_very_long",
            "track_slope_curve_large_right",
            "track_slope_curve_very_large_right",
            "track_slope_curve_super_large_right"
    );

    private static final List<String> CROSSING_OPTIONS = List.of(
            "track_crossing_x",
            "track_diamond_crossing",
            "track_double_diamond_crossing",
            "track_diagonal_crossing",
            "track_diagonal_two_ways_crossing",
            "track_diagonal_four_ways_crossing",
            "track_universal_crossing"
    );

    private static final List<String> SWITCH_OPTIONS = List.of(
            "track_switch_small_right",
            "track_switch_medium_right",
            "track_switch_large_right",
            "track_switch_very_large_right",
            "track_switch_45_medium_right",
            "track_switch_parallel_right"
    );

    private static final List<String> DIAGONAL_OPTIONS = List.of(
            "track_diagonal_straight_small",
            "track_diagonal_straight_medium"
    );

    private static final List<String> SPECIALTY_TRACK_OPTIONS = List.of(

            "track_detector_freight",
            "track_detector_passenger",
            "track_detector_locomotive",
            "track_detector_steam",
            "track_detector_diesel",
            "track_detector_tank",

            "track_coupler",
            "track_boarding_animal",
            "track_boarding_mob",
            "track_disembark",
            "track_locomotive_speed_controller",
            "track_locomotive_holding",
            "track_station",
            "track_horn_locomotive",
            "track_crossing_approach"

    );

    private static final List<String> TRACKSIDE_OPTIONS = List.of(
            "tracks_builder",
            "crossing_gate_lamps",
            "crossing_gate_crossbuck",
            "crossing_gate_gate",
            "crossing_gate_base",
            "crossing_gate_pole"
    );

    private static final List<String> ROLLING_STOCK_ORDER = List.of(
            "steam_tender",
            "freight_cart",
            "livestock_car",
            "mob_transport_car",
            "passenger_coach_blue",
            "caboose"
    );

    public static final RegistryObject<CreativeModeTab> TRAINCRAFT = TABS.register("traincraft", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.traincraft"))
                    .icon(() -> new ItemStack(TCItems.WRENCH.get()))
                    .displayItems((parameters, output) -> {
                        Set<Item> shown = new HashSet<>();
                        List<Item> items = allTraincraftItems();

                        // Pinned first row: user tools first.
                        acceptIds(output, shown, MISC_START);

                        // Requested fourth slot. Ordering only: use an existing
                        // chunk-loader item if one is already registered.
                        acceptFirstPresent(output, shown, List.of(
                                "chunk_loader",
                                "chunkloader",
                                "chunk_loader_activator"
                        ));
        // TC_BATCH8_R3_TRACK_BLUEPRINT - fifth Misc utility.
        acceptFirstPresent(output, shown, List.of(
                "track_blueprint"
        ));

                        // Crafting/workshop blocks stay near the front.
                        acceptIds(output, shown, MISC_WORKSHOP);

                        // Utility machines, then railway support/decor blocks.
                        acceptIds(output, shown, MISC_POWER_AND_UTILITY);
                        acceptIds(output, shown, MISC_SUPPORT_BLOCKS);

                        // Remaining non-track/non-train items follow in stable ID order.
                        for (Item item : items) {
                            String id = itemId(item);
                            if (!isTrackContent(id)
                                    && !isTrainContent(item, id)
                                    && !isRetiredMiscContent(id)
                                    && !isRailwayVillagerEgg(id)
                                    && !RAILWAY_WORKSTATIONS.contains(id)
                                    && shown.add(item)) {
                                output.accept(item);
                            }
                        }
                        // Keep all six workstations together as the final Misc entries.
                        acceptIds(output, shown, RAILWAY_WORKSTATIONS);
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> TRACKS = TABS.register("tracks", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.traincraft.tracks"))
                    .icon(() -> iconFor("track_normal", TCItems.WRENCH.get()))
                    // Force Misc immediately before Tracks instead of relying on alphabetical registry ordering.
                    .withTabsBefore(new ResourceLocation(Traincraft.MOD_ID, "traincraft"))
                    .displayItems((parameters, output) -> {
                        Set<Item> shown = new HashSet<>();
                        // DEV58B8R3A: stable normal-first, Energy-second geometry order.
                        // Trackside Builder and crossing equipment are pinned at the END.
                        List<Item> trackItems = allTraincraftItems();
                        List<String> families = List.of(
                                "STRAIGHTS", "DIAGONALS", "CURVES", "CURVES_45",
                                "CURVES_29_32", "PARALLEL_CURVES", "SWITCHES",
                                "SWITCHES_45", "PARALLEL_SWITCHES", "SLOPES",
                                "CURVED_SLOPES", "CROSSINGS");

                        // NORMAL / Steel / Copper / Snow first, matching track geometry order.
                        for (String family : families) {
                            acceptTrackFamily(output, shown, trackItems, family, false);
                        }
                        // Then ENERGY tracks in precisely the same family sequence.
                        for (String family : families) {
                            acceptTrackFamily(output, shown, trackItems, family, true);
                        }
                        // Retain unclassified registered track items, with normal
                        // before Energy and no duplicates or invisible segments.
                        for (boolean energy : new boolean[]{false, true}) {
                            for (Item item : trackItems) {
                                String id = itemId(item);
                                if (isTrackContent(id)
                                        && isEnergyVariant(id) == energy
                                        && !TRACKSIDE_OPTIONS.contains(id)
                                        && !isSpecialTrackId(id)
                                        && !isUnifiedHiddenLeftTrack(id)
                                        && !id.endsWith("_segment")
                                        && !id.contains("_gag")
                                        && shown.add(item)) {
                                    output.accept(item);
                                }
                            }
                        }
                        // Functional specialty rails follow ALL physical Normal/Energy tracks.
                        acceptIds(output, shown, SPECIALTY_TRACK_OPTIONS);
                        // Future functional rails are also grouped here, never among ordinary geometry.
                        for (Item item : trackItems) {
                            String id = itemId(item);
                            if (isSpecialTrackId(id) && shown.add(item)) {
                                output.accept(item);
                            }
                        }
                        // Builder + lamp, crossbuck, arm, base, pole LAST.
                        acceptIds(output, shown, TRACKSIDE_OPTIONS);
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> TRAINS = TABS.register("trains", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.traincraft.trains"))
                    .icon(() -> new ItemStack(TCItems.LOCOMOTIVE_STEAM_SMALL.get()))
                    // Force Tracks immediately before Trains.
                    .withTabsBefore(new ResourceLocation(Traincraft.MOD_ID, "tracks"))
                    .displayItems((parameters, output) -> {
                        Set<Item> shown = new HashSet<>();
                        List<Item> items = allTraincraftItems();

                        // DEV58B10F8: ALL locomotives from compact to long, regardless of power type.
                        // Then tenders; then ordinary coaches/wagons; finally animal/mob and TCT tankers.
                        // Only changes Creative inventory presentation (no entity behavior).
                        List<Item> trains = new ArrayList<>();
                        for (Item item : items) {
                            if (isTrainContent(item, itemId(item))) {
                                trains.add(item);
                            }
                        }
                        trains.sort(Comparator
                                .comparingInt((Item item) -> trainGroup(itemId(item)))
                                .thenComparingDouble((Item item) -> trainLengthHint(itemId(item)))
                                .thenComparing(TCCreativeTabs::itemId));
                        for (Item item : trains) {
                            if (shown.add(item)) {
                                output.accept(item);
                            }
                        }
                    })
                    .build());

    // DEV58B10F8. Sizes are horizontal extents in model blocks. The 37 TCM-backed
    // steam models are measured from immutable captured mesh headers; other models
    // have conservative manual display-order hints, NOT authoritative geometry.
    // Unknown future items use category-specific fallback and stable ID order.
    private static final java.util.Map<String, Double> TRAIN_DISPLAY_LENGTHS = java.util.Map.ofEntries(
            java.util.Map.entry("caboose", 3.1000D),
            java.util.Map.entry("freight_cart", 2.0000D),
            java.util.Map.entry("gs4_baggage_car", 5.0000D),
            java.util.Map.entry("gs4_observatory_car", 5.0000D),
            java.util.Map.entry("gs4_passenger_car", 5.0000D),
            java.util.Map.entry("gs4_tail_car", 5.0000D),
            java.util.Map.entry("livestock_car", 2.8000D),
            java.util.Map.entry("locomotive_diesel_cd742", 4.1000D),
            java.util.Map.entry("locomotive_diesel_chme3", 4.5000D),
            java.util.Map.entry("locomotive_diesel_gp7", 5.4000D),
            java.util.Map.entry("locomotive_diesel_kof", 2.1000D),
            java.util.Map.entry("locomotive_diesel_sd40", 5.9000D),
            java.util.Map.entry("locomotive_diesel_sd70", 6.4000D),
            java.util.Map.entry("locomotive_diesel_shunter", 3.0000D),
            java.util.Map.entry("locomotive_diesel_v60", 3.2000D),
            java.util.Map.entry("locomotive_electric_br_e69", 3.0000D),
            java.util.Map.entry("locomotive_electric_high_speed_zeroed", 5.6000D),
            java.util.Map.entry("locomotive_electric_minetrain", 1.9000D),
            java.util.Map.entry("locomotive_electric_tram", 4.1000D),
            java.util.Map.entry("locomotive_electric_tram_ny", 4.5000D),
            java.util.Map.entry("locomotive_electric_vl10", 5.8000D),
            java.util.Map.entry("locomotive_steam_262t", 4.6256D),
            java.util.Map.entry("locomotive_steam_4_4_0", 3.3310D),
            java.util.Map.entry("locomotive_steam_a4_mallard", 7.1000D),
            java.util.Map.entry("locomotive_steam_adler", 2.3188D),
            java.util.Map.entry("locomotive_steam_alco_sc4", 5.0000D),
            java.util.Map.entry("locomotive_steam_alice_0_4_0", 2.4000D),
            java.util.Map.entry("locomotive_steam_berkshire_1225", 7.6253D),
            java.util.Map.entry("locomotive_steam_berkshire_765", 7.6253D),
            java.util.Map.entry("locomotive_steam_br01", 6.5000D),
            java.util.Map.entry("locomotive_steam_br80", 3.5006D),
            java.util.Map.entry("locomotive_steam_br_black_5", 6.0000D),
            java.util.Map.entry("locomotive_steam_br_britannia", 5.4156D),
            java.util.Map.entry("locomotive_steam_c11", 4.8441D),
            java.util.Map.entry("locomotive_steam_c41", 5.3159D),
            java.util.Map.entry("locomotive_steam_c41_080", 5.4258D),
            java.util.Map.entry("locomotive_steam_c41t", 5.6761D),
            java.util.Map.entry("locomotive_steam_c62", 6.4000D),
            java.util.Map.entry("locomotive_steam_cherepanov", 2.3756D),
            java.util.Map.entry("locomotive_steam_climax", 4.7506D),
            java.util.Map.entry("locomotive_steam_climax_2", 4.6531D),
            java.util.Map.entry("locomotive_steam_coronation_class", 7.5000D),
            java.util.Map.entry("locomotive_steam_d51", 6.2000D),
            java.util.Map.entry("locomotive_steam_d51_long", 7.6000D),
            java.util.Map.entry("locomotive_steam_forney", 3.2000D),
            java.util.Map.entry("locomotive_steam_fowler", 4.3159D),
            java.util.Map.entry("locomotive_steam_glyn_042t", 4.0006D),
            java.util.Map.entry("locomotive_steam_gs4", 7.9378D),
            java.util.Map.entry("locomotive_steam_gwr_101_class", 4.0631D),
            java.util.Map.entry("locomotive_steam_gwr_42xx", 5.9381D),
            java.util.Map.entry("locomotive_steam_gwr_72xx", 6.6881D),
            java.util.Map.entry("locomotive_steam_hall_class", 6.2000D),
            java.util.Map.entry("locomotive_steam_heavy", 5.7000D),
            java.util.Map.entry("locomotive_steam_j50", 4.8441D),
            java.util.Map.entry("locomotive_steam_king_class", 7.2000D),
            java.util.Map.entry("locomotive_steam_lssp7", 2.1256D),
            java.util.Map.entry("locomotive_steam_midland_compound", 6.6000D),
            java.util.Map.entry("locomotive_steam_milw_class_a", 6.8438D),
            java.util.Map.entry("locomotive_steam_mogul", 4.8000D),
            java.util.Map.entry("locomotive_steam_pannier", 3.6000D),
            java.util.Map.entry("locomotive_steam_pe", 5.1256D),
            java.util.Map.entry("locomotive_steam_rw_type_2", 3.3131D),
            java.util.Map.entry("locomotive_steam_rw_type_3", 4.2506D),
            java.util.Map.entry("locomotive_steam_s100_uk", 4.0062D),
            java.util.Map.entry("locomotive_steam_s100_us", 4.0000D),
            java.util.Map.entry("locomotive_steam_sentinel_y3", 2.1000D),
            java.util.Map.entry("locomotive_steam_shay", 4.0000D),
            java.util.Map.entry("locomotive_steam_shay_3_truck", 3.6253D),
            java.util.Map.entry("locomotive_steam_skookum", 5.7506D),
            java.util.Map.entry("locomotive_steam_small", 2.8000D),
            java.util.Map.entry("locomotive_steam_snow_plow", 5.7500D),
            java.util.Map.entry("locomotive_steam_southern1102", 5.2534D),
            java.util.Map.entry("locomotive_steam_star_class", 3.1406D),
            java.util.Map.entry("locomotive_steam_u57", 6.0006D),
            java.util.Map.entry("locomotive_steam_ussr", 5.6000D),
            java.util.Map.entry("locomotive_steam_vb_0_4_0", 2.7000D),
            java.util.Map.entry("locomotive_steam_vb_shay", 5.5006D),
            java.util.Map.entry("locomotive_steam_vb_shay_2", 4.5770D),
            java.util.Map.entry("locomotive_steam_wwcp_062t", 5.8131D),
            java.util.Map.entry("milw_baggage_car", 4.5000D),
            java.util.Map.entry("milw_passenger_car", 4.5000D),
            java.util.Map.entry("milw_tail_car", 4.5000D),
            java.util.Map.entry("mob_transport_car", 3.0000D),
            java.util.Map.entry("passenger_coach_blue", 3.6000D),
            java.util.Map.entry("steam_tender", 2.0000D),
            java.util.Map.entry("steam_tender_4000_gallon", 3.8000D),
            java.util.Map.entry("steam_tender_4_4_0", 2.2000D),
            java.util.Map.entry("steam_tender_a4_mallard", 3.6000D),
            java.util.Map.entry("steam_tender_adler", 1.4000D),
            java.util.Map.entry("steam_tender_berkshire", 3.7000D),
            java.util.Map.entry("steam_tender_br01", 3.0000D),
            java.util.Map.entry("steam_tender_br1", 2.8000D),
            java.util.Map.entry("steam_tender_br_black_5", 3.0000D),
            java.util.Map.entry("steam_tender_c41", 2.3000D),
            java.util.Map.entry("steam_tender_c62", 3.1000D),
            java.util.Map.entry("steam_tender_coronation_class", 3.8000D),
            java.util.Map.entry("steam_tender_d51", 3.0000D),
            java.util.Map.entry("steam_tender_fowler", 2.6000D),
            java.util.Map.entry("steam_tender_gs4", 4.0000D),
            java.util.Map.entry("steam_tender_heavy", 3.4000D),
            java.util.Map.entry("steam_tender_midland_compound", 3.0000D),
            java.util.Map.entry("steam_tender_milw", 3.0000D),
            java.util.Map.entry("steam_tender_pe", 3.1000D),
            java.util.Map.entry("steam_tender_rw_type_2", 2.4000D),
            java.util.Map.entry("steam_tender_shay_3_truck", 2.2000D),
            java.util.Map.entry("steam_tender_skookum", 3.5000D),
            java.util.Map.entry("steam_tender_southern1102", 2.8000D),
            java.util.Map.entry("steam_tender_star_class", 2.8000D),
            java.util.Map.entry("steam_tender_u57", 2.5000D),
            java.util.Map.entry("steam_tender_ussr", 3.0000D),
            java.util.Map.entry("tct_tanker_lava", 3.0000D),
            java.util.Map.entry("tct_tanker_universal", 3.0000D),
            java.util.Map.entry("tct_tanker_water", 3.0000D)
    );

    private static int trainGroup(String id) {
        if (id.startsWith("locomotive_")) return 0;
        if (id.contains("tender")) return 1;
        if (id.contains("mob_transport") || id.contains("livestock")
                || id.contains("animal_transport")) return 3;
        if (id.startsWith("tct_tanker") || id.contains("tanker") || id.contains("tank_car")) return 4;
        return 2;
    }

    private static double trainLengthHint(String id) {
        return TRAIN_DISPLAY_LENGTHS.getOrDefault(id, 999.0D);
    }

    // TC_FINAL_RELEASE_R1AH_FORMAL_TRACK_FAMILIES
    //
    // Creative ordering is geometry-first. Material variants stay immediately
    // with the geometry they belong to, while the accepted unified-family
    // hidden-LEFT filter remains authoritative.
    private static void acceptTrackFamily(
            CreativeModeTab.Output output,
            Set<Item> shown,
            List<Item> items,
            String family,
            boolean energyOnly) {
        List<Item> matches =
                new ArrayList<>();

        for (Item item : items) {
            String id =
                    itemId(
                            item);

            // DEV58B8R3C: crossing-gate hardware is equipment, not a CROSSINGS rail.
            // Keep it out of geometry families so it is emitted ONLY at the tab end.
            if (!isTrackContent(
                            id)
                    || TRACKSIDE_OPTIONS.contains(id)
                    || isSpecialTrackId(id)
                    || id.endsWith(
                            "_segment")
                    || id.contains(
                            "_gag")
                    || isUnifiedHiddenLeftTrack(
                            id)
                    || isEnergyVariant(id) != energyOnly) {
                continue;
            }

            String base =
                    trackGeometryBaseId(
                            id);

            if (trackFamilyMatches(
                    base,
                    id,
                    family)) {
                matches.add(
                        item);
            }
        }

        matches.sort(
                Comparator.comparingInt(
                                (Item item) ->
                                        trackGeometryPriority(
                                                trackGeometryBaseId(itemId(item))))
                        .thenComparing(
                                (Item item) -> trackGeometryBaseId(itemId(item)))
                        .thenComparingInt(
                                item ->
                                        trackMaterialRank(
                                                itemId(
                                                        item)))
                        .thenComparing(
                                TCCreativeTabs::itemId));

        for (Item item : matches) {
            if (shown.add(
                    item)) {
                output.accept(
                        item);
            }
        }
    }

    private static boolean isSpecialTrackId(String id) {
        return SPECIALTY_TRACK_OPTIONS.contains(id)
                || id.startsWith("track_detector_")
                || id.startsWith("track_boarding_")
                || id.startsWith("track_disembark")
                || id.startsWith("track_station")
                || id.startsWith("track_coupler")
                || id.startsWith("track_locomotive_")
                || id.startsWith("track_horn_")
                || id.startsWith("track_crossing_approach");
    }

    private static boolean isEnergyVariant(String id) {
        return "track_energy".equals(id) || id.endsWith("_energy");
    }

    // Keep the four straight lengths in the familiar length order, not ID order.
    private static int trackGeometryPriority(String id) {
        return switch (id) {
            case "track_normal" -> 0;
            case "track_medium_straight" -> 1;
            case "track_long_straight" -> 2;
            case "track_very_long_straight" -> 3;
            case "track_medium_curve" -> 4;
            default -> 100;
        };
    }

    private static boolean trackFamilyMatches(
            String base,
            String originalId,
            String family) {
        return switch (family) {
            case "STRAIGHTS" ->
                    "track_normal".equals(base)
                            || "track_normal".equals(
                                    trackBasicMaterialGeometry(
                                            originalId))
                            || (base.contains("_straight")
                                    && !base.startsWith(
                                            "track_diagonal_"));

            case "DIAGONALS" ->
                    base.startsWith(
                            "track_diagonal_straight");

            case "CURVES" ->
                    "track_medium_curve".equals(base)
                            || (base.startsWith(
                                            "track_curve_")
                                    && !base.startsWith(
                                            "track_curve_45_")
                                    && !"track_curve_29x".equals(
                                            base)
                                    && !"track_curve_32x".equals(
                                            base));

            case "CURVES_45" ->
                    base.startsWith(
                            "track_curve_45_");

            case "CURVES_29_32" ->
                    "track_curve_29x".equals(base)
                            || "track_curve_32x".equals(base);

            case "PARALLEL_CURVES" ->
                    base.startsWith(
                            "track_parallel_curve_");

            case "SWITCHES" ->
                    base.startsWith(
                            "track_switch_")
                            && !base.startsWith(
                                    "track_switch_45_")
                            && !base.startsWith(
                                    "track_switch_parallel_");

            case "SWITCHES_45" ->
                    base.startsWith(
                            "track_switch_45_");

            case "PARALLEL_SWITCHES" ->
                    base.startsWith(
                            "track_switch_parallel_");

            case "SLOPES" ->
                    base.startsWith(
                            "track_slope_")
                            && !base.startsWith(
                                    "track_slope_curve_");

            case "CURVED_SLOPES" ->
                    base.startsWith(
                            "track_slope_curve_");

            case "CROSSINGS" ->
                    (base.contains(
                                    "crossing")
                            && !"track_crossing_approach".equals(
                                    base));

            default -> false;
        };
    }

    private static String trackGeometryBaseId(
            String id) {
        String basic =
                trackBasicMaterialGeometry(
                        id);

        if (!basic.equals(
                id)) {
            return basic;
        }

        for (String suffix : List.of(
                "_energy",
                "_steel",
                "_copper",
                "_snow")) {
            if (id.endsWith(
                    suffix)) {
                String candidate =
                        id.substring(
                                0,
                                id.length()
                                        - suffix.length());

                if (candidate.startsWith(
                        "track_")) {
                    return candidate;
                }
            }
        }

        return id;
    }

    private static String trackBasicMaterialGeometry(
            String id) {
        return switch (id) {
            case "track_normal",
                 "track_energy",
                 "track_steel",
                 "track_copper",
                 "track_snow",
                 "track_snow_steel",
                 "track_snow_copper" ->
                    "track_normal";
            default -> id;
        };
    }

    private static int trackMaterialRank(
            String id) {
        if (id.endsWith("_energy")
                || "track_energy".equals(id)) {
            return 4;
        }
        if (id.endsWith("_snow")
                || id.startsWith("track_snow")) {
            return 3;
        }
        if (id.endsWith("_copper")
                || "track_copper".equals(id)) {
            return 2;
        }
        if (id.endsWith("_steel")
                || "track_steel".equals(id)) {
            return 1;
        }
        return 0;
    }
    private static void acceptPrefix(
            CreativeModeTab.Output output,
            Set<Item> shown,
            List<Item> items,
            String prefix) {
        for (Item item : items) {
            String id = itemId(item);
            if (id.startsWith(prefix) && shown.add(item)) {
                output.accept(item);
            }
        }
    }

    private static void acceptFirstPresent(
            CreativeModeTab.Output output,
            Set<Item> shown,
            List<String> ids) {
        for (String id : ids) {
            Item item = itemById(id);
            if (item != null) {
                if (shown.add(item)) {
                    output.accept(item);
                }
                return;
            }
        }
    }

    private static void acceptRollingStockContaining(
            CreativeModeTab.Output output,
            Set<Item> shown,
            List<Item> items,
            String token) {
        for (Item item : items) {
            String id = itemId(item);
            if (item instanceof RollingStockItem<?>
                    && !"crossing_gate_base".equals(id)
                    && !"crossing_gate_pole".equals(id)
                    && !"crossing_gate_lamps".equals(id)
                    && !"crossing_gate_crossbuck".equals(id)
                    && !"crossing_gate_gate".equals(id)
                    && !"tracks_builder".equals(id)
                    && id.contains(token)
                    && shown.add(item)) {
                output.accept(item);
            }
        }
    }

    private static void acceptIds(
            CreativeModeTab.Output output,
            Set<Item> shown,
            List<String> ids) {
        for (String id : ids) {
            acceptId(output, shown, id);
        }
    }

    private static void acceptId(
            CreativeModeTab.Output output,
            Set<Item> shown,
            String id) {
        Item item = itemById(id);
        if (item != null && shown.add(item)) {
            output.accept(item);
        }
    }

    private static Item itemById(String id) {
        ResourceLocation requested = new ResourceLocation(Traincraft.MOD_ID, id);
        Item item = ForgeRegistries.ITEMS.getValue(requested);

        // Forge's defaulted item registry can return minecraft:air for a missing key.
        // AIR produces an empty ItemStack (count 0), which CreativeModeTab rejects
        // with "The stack count must be 1".  Only return an item when the registry
        // key we got back is exactly the Traincraft key we requested.
        if (item == null) {
            return null;
        }
        ResourceLocation actual = ForgeRegistries.ITEMS.getKey(item);
        return requested.equals(actual) ? item : null;
    }

    private static ItemStack iconFor(String id, Item fallback) {
        Item item = itemById(id);
        return new ItemStack(item == null ? fallback : item);
    }

    private static List<Item> allTraincraftItems() {
        List<Item> out = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && Traincraft.MOD_ID.equals(key.getNamespace())) {
                out.add(item);
            }
        }
        out.sort(Comparator.comparing(TCCreativeTabs::itemId));
        return out;
    }

    private static String itemId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.getPath();
    }

    private static boolean isRetiredMiscContent(String id) {
        return "generator_diesel".equals(id)
                || "water_wheel".equals(id)
                || "wind_mill".equals(id)
                || "signal".equals(id)
                || "lantern".equals(id);
    }
    // TC_BATCH8_R3ZK_UNIFIED_TRACK_FAMILIES
    // Paired LEFT registry items remain available internally/through commands,
    // but Creative exposes only one neutral family item. This also hides any
    // left-handed Steel/Copper/Snow/Energy aliases appended by fallback.
    private static boolean isUnifiedHiddenLeftTrack(String id) {
        if (id == null || !id.contains("_left")) {
            return false;
        }

        return id.startsWith("track_curve_45_")
                || id.startsWith("track_parallel_curve_")
                || id.startsWith("track_slope_curve_")
                // TC_BATCH8_R3ZL_DIAMOND_CROSSING_HIDE
                || id.startsWith("track_diamond_crossing_left")
                || id.startsWith("track_switch_");
    }
    private static boolean isTrackContent(String id) {
        // DEV50a4: railway workstation and egg belong to Misc and vanilla Spawn Eggs.
        // They happen to start with 'track_' but are not rail pieces.
        if ("track_maintenance_bench".equals(id) || "track_worker_spawn_egg".equals(id)) {
            return false;
        }
        // TC_BATCH8_R4A3D_BLUEPRINT_MISC_ONLY
        // Track Blueprint is a Misc utility. Keep it out of the Tracks fallback.
        if ("track_blueprint".equals(id)) {
            return false;
        }

        return id.startsWith("track_")
                || ("crossing_gate_base".equals(id)
                || "crossing_gate_pole".equals(id)
                || "crossing_gate_lamps".equals(id)
                || "crossing_gate_crossbuck".equals(id)
                || "crossing_gate_gate".equals(id)
                || "tracks_builder".equals(id));
    }

    private static boolean isTrainContent(Item item, String id) {
        if ("crossing_gate_base".equals(id)
                || "crossing_gate_pole".equals(id)
                || "crossing_gate_lamps".equals(id)
                || "crossing_gate_crossbuck".equals(id)
                || "crossing_gate_gate".equals(id)
                || "tracks_builder".equals(id)) {
            return false;
        }
        if (id.startsWith("locomotive_")) {
            return true;
        }
        // RollingStockItem catches recovered legacy tenders, freight/passenger cars,
        // cabooses and future train-placement items without a huge hard-coded ID list.
        return item instanceof RollingStockItem<?> || ROLLING_STOCK_ORDER.contains(id);
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }

    private TCCreativeTabs() {}
}

