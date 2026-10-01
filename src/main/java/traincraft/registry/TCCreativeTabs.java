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
 *   TafariCraft - Trains : Steam locomotives/tenders -> Diesel -> Electric -> rolling stock.
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
                                    && shown.add(item)) {
                                output.accept(item);
                            }
                        }
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
                        // 1) Pinned top row: Track Builder + modular crossing pieces.
                        acceptIds(output, shown, TRACKSIDE_OPTIONS);

                        // 2) Functional/specialty rails stay directly at the top.
                        acceptIds(output, shown, SPECIALTY_TRACK_OPTIONS);

                        // 3) Main track families in a cleaner build-oriented order.
                        acceptIds(output, shown, BASIC_TRACKS);
                        acceptIds(output, shown, BASIC_ENERGY_TRACKS);
                        acceptIds(output, shown, CURVE_OPTIONS);
                        acceptIds(output, shown, SWITCH_OPTIONS);
                        acceptIds(output, shown, SLOPE_OPTIONS);
                        acceptIds(output, shown, CROSSING_OPTIONS);
                        acceptIds(output, shown, DIAGONAL_OPTIONS);

                        // 4) Future/new track options not known to this build are appended
                        //    automatically instead of disappearing from the tab.
                        for (Item item : allTraincraftItems()) {
                            String id = itemId(item);
                            if (isTrackContent(id)
                                    && !isUnifiedHiddenLeftTrack(id)
                                    && !id.endsWith("_segment")
                                    && !id.contains("_gag")
                                    && shown.add(item)) {
                                output.accept(item);
                            }
                        }
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

                        // Steam locomotives first.
                        acceptPrefix(output, shown, items, "locomotive_steam_");

                        // Steam tenders directly after steam locomotives.
                        acceptRollingStockContaining(output, shown, items, "tender");

                        // Then diesel locomotives.
                        acceptPrefix(output, shown, items, "locomotive_diesel_");

                        // Then electric locomotives.
                        acceptPrefix(output, shown, items, "locomotive_electric_");

                        // Then the familiar core non-powered cars.
                        acceptIds(output, shown, ROLLING_STOCK_ORDER);

                        // Append every other rolling-stock placement item automatically.
                        // This removes legacy tenders/cars that were leaking into Misc.
                        for (Item item : items) {
                            String id = itemId(item);
                            if (isTrainContent(item, id) && shown.add(item)) {
                                output.accept(item);
                            }
                        }
                    })
                    .build());

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

