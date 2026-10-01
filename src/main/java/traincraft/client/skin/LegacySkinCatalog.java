package traincraft.client.skin;

// TC_BATCH6_R2_LEGACY_SKIN_GUI
// TC_BATCH6_R3_COLOR_WHEEL
// TC_BATCH6_R3E_REMAINING_TRAINS
// TC_BATCH6_R3F_BR01_TENDER

import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit Traincraft 1.7.10 livery map.
 *
 * The legacy Paintbrush used SkinRegistry entries per rolling-stock type rather
 * than guessing filenames.  This 1.20.1 catalog follows the same rule: only
 * known-compatible legacy textures are exposed for each renderer base texture.
 */
public final class LegacySkinCatalog {
    public record Entry(String key, String displayName, ResourceLocation texture) {}

    private static final Map<String, List<Entry>> BY_BASE = new HashMap<>();

    static {
                put("textures/trains/heavysteam_black.png", List.of(
                new Entry("black", "Black", rl("textures/skins/heavy_steam/black.png")),
                new Entry("brown", "Brown", rl("textures/skins/heavy_steam/brown.png")),
                new Entry("white", "White", rl("textures/skins/heavy_steam/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/heavy_steam/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/heavy_steam/orange.png")),
                new Entry("red", "Red", rl("textures/skins/heavy_steam/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/heavy_steam/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/heavy_steam/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/heavy_steam/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/heavy_steam/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/heavy_steam/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/heavy_steam/green.png"))
        ));
                put("textures/trains/heavytender_black.png", List.of(
                new Entry("black", "Black", rl("textures/skins/heavy_tender/black.png")),
                new Entry("brown", "Brown", rl("textures/skins/heavy_tender/brown.png")),
                new Entry("white", "White", rl("textures/skins/heavy_tender/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/heavy_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/heavy_tender/orange.png")),
                new Entry("red", "Red", rl("textures/skins/heavy_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/heavy_tender/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/heavy_tender/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/heavy_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/heavy_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/heavy_tender/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/heavy_tender/green.png"))
        ));
                        put("textures/trains/locomogul_blue.png", List.of(
                new Entry("blue", "Blue", rl("textures/skins/mogul/blue.png")),
                new Entry("red", "Red", rl("textures/skins/mogul/red.png")),
                new Entry("black", "Black", rl("textures/skins/mogul/black.png")),
                new Entry("white", "White", rl("textures/skins/mogul/white.png")),
                new Entry("green", "Green", rl("textures/skins/mogul/green.png")),
                new Entry("brown", "Brown", rl("textures/skins/mogul/brown.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/mogul/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/mogul/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/mogul/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/mogul/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/mogul/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/mogul/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/mogul/pink.png"))
        ));
                        put("textures/trains/locoforney_red.png", List.of(
                new Entry("red", "Red", rl("textures/skins/forney/red.png")),
                new Entry("blue", "Blue", rl("textures/skins/forney/blue.png")),
                new Entry("grey", "Grey", rl("textures/skins/forney/grey.png")),
                new Entry("green", "Green", rl("textures/skins/forney/green.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/forney/yellow.png")),
                new Entry("brown", "Brown", rl("textures/skins/forney/brown.png")),
                new Entry("white", "White", rl("textures/skins/forney/white.png")),
                new Entry("orange", "Orange", rl("textures/skins/forney/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/forney/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/forney/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/forney/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/forney/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/forney/pink.png"))
        ));
                put("textures/trains/passenger_blue.png", List.of(
                new Entry("blue", "Blue", rl("textures/skins/passenger/blue.png")),
                new Entry("black", "Black", rl("textures/skins/passenger/black.png")),
                new Entry("white", "White", rl("textures/skins/passenger/white.png")),
                new Entry("red", "Red", rl("textures/skins/passenger/red.png")),
                new Entry("green", "Green", rl("textures/skins/passenger/green.png")),
                new Entry("orange", "Orange", rl("textures/skins/passenger/orange.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/passenger/yellow.png")),
                new Entry("brown", "Brown", rl("textures/skins/passenger/brown.png")),
                new Entry("grey", "Grey", rl("textures/skins/passenger/grey.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/passenger/light_grey.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/passenger/light_blue.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/passenger/cyan.png")),
                new Entry("lime", "Lime", rl("textures/skins/passenger/lime.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/passenger/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/passenger/pink.png")),
                new Entry("purple", "Purple", rl("textures/skins/passenger/purple.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/passenger/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/passenger/indigo.png"))
        ));
                        put("textures/trains/locobr01_db_black.png", List.of(
                new Entry("black", "Black", rl("textures/skins/br01/black.png")),
                new Entry("grey", "Grey", rl("textures/skins/br01/grey.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/br01/light_grey.png")),
                new Entry("winter", "Winter", rl("textures/skins/br01/winter.png")),
                new Entry("white", "White", rl("textures/skins/br01/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br01/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/br01/orange.png")),
                new Entry("red", "Red", rl("textures/skins/br01/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br01/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br01/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br01/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br01/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/br01/green.png")),
                new Entry("pink", "Pink", rl("textures/skins/br01/pink.png"))
        ));
                put("textures/trains/tenderbr01_db.png", List.of(
                new Entry("winter", "Winter", rl("textures/skins/br01_tender/winter.png")),
                new Entry("white", "White", rl("textures/skins/br01_tender/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br01_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/br01_tender/orange.png")),
                new Entry("red", "Red", rl("textures/skins/br01_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br01_tender/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/br01_tender/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br01_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br01_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br01_tender/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/br01_tender/green.png"))
        ));
                        put("textures/trains/loco_kof_db_red.png", List.of(
                new Entry("red", "Red", rl("textures/skins/kof/red.png")),
                new Entry("blue", "Blue", rl("textures/skins/kof/blue.png")),
                new Entry("black", "Black", rl("textures/skins/kof/black.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/kof/yellow.png")),
                new Entry("green", "Green", rl("textures/skins/kof/green.png")),
                new Entry("white", "White", rl("textures/skins/kof/white.png")),
                new Entry("orange", "Orange", rl("textures/skins/kof/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/kof/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/kof/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/kof/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/kof/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/kof/pink.png"))
        ));
                        put("textures/trains/cd742_yellow.png", List.of(
                new Entry("yellow", "Yellow", rl("textures/skins/cd742/yellow.png")),
                new Entry("white", "White", rl("textures/skins/cd742/white.png")),
                new Entry("blue", "Blue", rl("textures/skins/cd742/blue.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/cd742/light_blue.png")),
                new Entry("orange", "Orange", rl("textures/skins/cd742/orange.png")),
                new Entry("red", "Red", rl("textures/skins/cd742/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/cd742/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/cd742/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/cd742/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/cd742/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/cd742/green.png")),
                new Entry("pink", "Pink", rl("textures/skins/cd742/pink.png"))
        ));
                put("textures/trains/gp7_red.png", List.of(
                new Entry("red", "Red", rl("textures/skins/gp7/red.png")),
                new Entry("brown", "Brown", rl("textures/skins/gp7/brown.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gp7/yellow.png")),
                new Entry("black", "Black", rl("textures/skins/gp7/black.png")),
                new Entry("grey", "Grey", rl("textures/skins/gp7/grey.png")),
                new Entry("orange", "Orange", rl("textures/skins/gp7/orange.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/gp7/light_grey.png")),
                new Entry("pink", "Pink", rl("textures/skins/gp7/pink.png")),
                new Entry("purple", "Purple", rl("textures/skins/gp7/purple.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gp7/cyan.png")),
                new Entry("white", "White", rl("textures/skins/gp7/white.png")),
                new Entry("green", "Green", rl("textures/skins/gp7/green.png")),
                new Entry("blue", "Blue", rl("textures/skins/gp7/blue.png")),
                new Entry("lime", "Lime", rl("textures/skins/gp7/lime.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gp7/magenta.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/gp7/light_blue.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gp7/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gp7/indigo.png"))
        ));
                put("textures/trains/loco_sd40_red.png", List.of(
                new Entry("red", "Red", rl("textures/skins/sd40/red.png")),
                new Entry("green", "Green", rl("textures/skins/sd40/green.png")),
                new Entry("black", "Black", rl("textures/skins/sd40/black.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/sd40/magenta.png")),
                new Entry("orange", "Orange", rl("textures/skins/sd40/orange.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/sd40/yellow.png")),
                new Entry("pink", "Pink", rl("textures/skins/sd40/pink.png")),
                new Entry("blue", "Blue", rl("textures/skins/sd40/blue.png")),
                new Entry("white", "White", rl("textures/skins/sd40/white.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/sd40/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/sd40/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/sd40/cyan.png"))
        ));
                put("textures/trains/loco_sd70_orange.png", List.of(
                new Entry("orange", "Orange", rl("textures/skins/sd70/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/sd70/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/sd70/pink.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/sd70/yellow.png")),
                new Entry("red", "Red", rl("textures/skins/sd70/red.png")),
                new Entry("blue", "Blue", rl("textures/skins/sd70/blue.png")),
                new Entry("black", "Black", rl("textures/skins/sd70/black.png")),
                new Entry("white", "White", rl("textures/skins/sd70/white.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/sd70/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/sd70/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/sd70/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/sd70/green.png"))
        ));
                        put("textures/trains/shunter_blue.png", List.of(
                new Entry("blue", "Blue", rl("textures/skins/shunter/blue.png")),
                new Entry("red", "Red", rl("textures/skins/shunter/red.png")),
                new Entry("black", "Black", rl("textures/skins/shunter/black.png")),
                new Entry("green", "Green", rl("textures/skins/shunter/green.png")),
                new Entry("white", "White", rl("textures/skins/shunter/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/shunter/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/shunter/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/shunter/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/shunter/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/shunter/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/shunter/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/shunter/pink.png"))
        ));
                        put("textures/trains/loco_v60_db_red.png", List.of(
                new Entry("red", "Red", rl("textures/skins/v60/red.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/v60/yellow.png")),
                new Entry("green", "Green", rl("textures/skins/v60/green.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/v60/cyan.png")),
                new Entry("white", "White", rl("textures/skins/v60/white.png")),
                new Entry("orange", "Orange", rl("textures/skins/v60/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/v60/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/v60/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/v60/indigo.png")),
                new Entry("pink", "Pink", rl("textures/skins/v60/pink.png"))
        ));
                        put("textures/trains/loco_br_e69_green.png", List.of(
                new Entry("green", "Green", rl("textures/skins/br_e69/green.png")),
                new Entry("black", "Black", rl("textures/skins/br_e69/black.png")),
                new Entry("red", "Red", rl("textures/skins/br_e69/red.png")),
                new Entry("grey", "Grey", rl("textures/skins/br_e69/grey.png")),
                new Entry("white", "White", rl("textures/skins/br_e69/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br_e69/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/br_e69/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br_e69/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br_e69/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br_e69/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br_e69/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/br_e69/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_br_black_5.png", List.of(
                new Entry("black", "Black", rl("textures/skins/br_black_5/black.png")),
                new Entry("red", "Red", rl("textures/skins/br_black_5/red.png")),
                new Entry("grey", "Grey", rl("textures/skins/br_black_5/grey.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br_black_5/yellow.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br_black_5/magenta.png")),
                new Entry("white", "White", rl("textures/skins/br_black_5/white.png")),
                new Entry("purple", "Purple", rl("textures/skins/br_black_5/purple.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/br_black_5/light_grey.png")),
                new Entry("orange", "Orange", rl("textures/skins/br_black_5/orange.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br_black_5/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br_black_5/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br_black_5/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/br_black_5/green.png")),
                new Entry("pink", "Pink", rl("textures/skins/br_black_5/pink.png"))
        ));
                        put("textures/trains/legacy/steam_tender_br_black_5.png", List.of(
                new Entry("blue", "Blue", rl("textures/skins/br_black_5_tender/blue.png")),
                new Entry("purple", "Purple", rl("textures/skins/br_black_5_tender/purple.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br_black_5_tender/yellow.png")),
                new Entry("green", "Green", rl("textures/skins/br_black_5_tender/green.png")),
                new Entry("grey", "Grey", rl("textures/skins/br_black_5_tender/grey.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/br_black_5_tender/light_grey.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br_black_5_tender/cyan.png")),
                new Entry("red", "Red", rl("textures/skins/br_black_5_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br_black_5_tender/magenta.png")),
                new Entry("white", "White", rl("textures/skins/br_black_5_tender/white.png")),
                new Entry("orange", "Orange", rl("textures/skins/br_black_5_tender/orange.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br_black_5_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br_black_5_tender/indigo.png")),
                new Entry("pink", "Pink", rl("textures/skins/br_black_5_tender/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_br_britannia.png", List.of(
                new Entry("black", "Black", rl("textures/skins/br_britannia/black.png")),
                new Entry("grey", "Grey", rl("textures/skins/br_britannia/grey.png")),
                new Entry("blue", "Blue", rl("textures/skins/br_britannia/blue.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br_britannia/yellow.png")),
                new Entry("green", "Green", rl("textures/skins/br_britannia/green.png")),
                new Entry("white", "White", rl("textures/skins/br_britannia/white.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br_britannia/magenta.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br_britannia/cyan.png")),
                new Entry("red", "Red", rl("textures/skins/br_britannia/red.png")),
                new Entry("orange", "Orange", rl("textures/skins/br_britannia/orange.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br_britannia/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br_britannia/indigo.png")),
                new Entry("pink", "Pink", rl("textures/skins/br_britannia/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_a4_mallard.png", List.of(
                new Entry("black", "Black", rl("textures/skins/a4_mallard/black.png")),
                new Entry("blue", "Blue", rl("textures/skins/a4_mallard/blue.png")),
                new Entry("white", "White", rl("textures/skins/a4_mallard/white.png")),
                new Entry("lime", "Lime", rl("textures/skins/a4_mallard/lime.png")),
                new Entry("green", "Green", rl("textures/skins/a4_mallard/green.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/a4_mallard/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/a4_mallard/orange.png")),
                new Entry("red", "Red", rl("textures/skins/a4_mallard/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/a4_mallard/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/a4_mallard/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/a4_mallard/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/a4_mallard/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/a4_mallard/pink.png"))
        ));
                        put("textures/trains/legacy/steam_tender_a4_mallard.png", List.of(
                new Entry("black", "Black", rl("textures/skins/a4_mallard_tender/black.png")),
                new Entry("blue", "Blue", rl("textures/skins/a4_mallard_tender/blue.png")),
                new Entry("white", "White", rl("textures/skins/a4_mallard_tender/white.png")),
                new Entry("lime", "Lime", rl("textures/skins/a4_mallard_tender/lime.png")),
                new Entry("green", "Green", rl("textures/skins/a4_mallard_tender/green.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/a4_mallard_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/a4_mallard_tender/orange.png")),
                new Entry("red", "Red", rl("textures/skins/a4_mallard_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/a4_mallard_tender/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/a4_mallard_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/a4_mallard_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/a4_mallard_tender/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/a4_mallard_tender/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_king_class.png", List.of(
                new Entry("blue", "Blue", rl("textures/skins/king_class/blue.png")),
                new Entry("lime", "Lime", rl("textures/skins/king_class/lime.png")),
                new Entry("green", "Green", rl("textures/skins/king_class/green.png")),
                new Entry("white", "White", rl("textures/skins/king_class/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/king_class/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/king_class/orange.png")),
                new Entry("red", "Red", rl("textures/skins/king_class/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/king_class/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/king_class/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/king_class/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/king_class/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/king_class/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_sentinel_y3.png", List.of(
                new Entry("grey", "Grey", rl("textures/skins/sentinel_y3/grey.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/sentinel_y3/light_grey.png")),
                new Entry("green", "Green", rl("textures/skins/sentinel_y3/green.png")),
                new Entry("blue", "Blue", rl("textures/skins/sentinel_y3/blue.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/sentinel_y3/cyan.png")),
                new Entry("lime", "Lime", rl("textures/skins/sentinel_y3/lime.png")),
                new Entry("red", "Red", rl("textures/skins/sentinel_y3/red.png")),
                new Entry("black", "Black", rl("textures/skins/sentinel_y3/black.png")),
                new Entry("white", "White", rl("textures/skins/sentinel_y3/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/sentinel_y3/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/sentinel_y3/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/sentinel_y3/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/sentinel_y3/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/sentinel_y3/indigo.png")),
                new Entry("pink", "Pink", rl("textures/skins/sentinel_y3/pink.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_j50.png", List.of(
                new Entry("grey", "Grey", rl("textures/skins/j50/grey.png")),
                new Entry("red", "Red", rl("textures/skins/j50/red.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/j50/cyan.png")),
                new Entry("black", "Black", rl("textures/skins/j50/black.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/j50/light_blue.png")),
                new Entry("orange", "Orange", rl("textures/skins/j50/orange.png")),
                new Entry("pink", "Pink", rl("textures/skins/j50/pink.png")),
                new Entry("blue", "Blue", rl("textures/skins/j50/blue.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/j50/light_grey.png")),
                new Entry("lime", "Lime", rl("textures/skins/j50/lime.png")),
                new Entry("brown", "Brown", rl("textures/skins/j50/brown.png")),
                new Entry("green", "Green", rl("textures/skins/j50/green.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/j50/yellow.png")),
                new Entry("white", "White", rl("textures/skins/j50/white.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/j50/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/j50/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/j50/indigo.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_rw_type_3.png", List.of(
                new Entry("lime", "Lime", rl("textures/skins/rw_type_3/lime.png")),
                new Entry("green", "Green", rl("textures/skins/rw_type_3/green.png")),
                new Entry("red", "Red", rl("textures/skins/rw_type_3/red.png")),
                new Entry("blue", "Blue", rl("textures/skins/rw_type_3/blue.png")),
                new Entry("pink", "Pink", rl("textures/skins/rw_type_3/pink.png")),
                new Entry("black", "Black", rl("textures/skins/rw_type_3/black.png")),
                new Entry("white", "White", rl("textures/skins/rw_type_3/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/rw_type_3/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/rw_type_3/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/rw_type_3/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/rw_type_3/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/rw_type_3/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/rw_type_3/cyan.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_rw_type_2.png", List.of(
                new Entry("green", "Green", rl("textures/skins/rw_type_2/green.png")),
                new Entry("brown", "Brown", rl("textures/skins/rw_type_2/brown.png")),
                new Entry("pink", "Pink", rl("textures/skins/rw_type_2/pink.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/rw_type_2/light_grey.png")),
                new Entry("black", "Black", rl("textures/skins/rw_type_2/black.png")),
                new Entry("purple", "Purple", rl("textures/skins/rw_type_2/purple.png")),
                new Entry("grey", "Grey", rl("textures/skins/rw_type_2/grey.png")),
                new Entry("red", "Red", rl("textures/skins/rw_type_2/red.png")),
                new Entry("white", "White", rl("textures/skins/rw_type_2/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/rw_type_2/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/rw_type_2/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/rw_type_2/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/rw_type_2/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/rw_type_2/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/rw_type_2/cyan.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_gwr_101_class.png", List.of(
                new Entry("brown", "Brown", rl("textures/skins/gwr_101/brown.png")),
                new Entry("green", "Green", rl("textures/skins/gwr_101/green.png")),
                new Entry("grey", "Grey", rl("textures/skins/gwr_101/grey.png")),
                new Entry("lime", "Lime", rl("textures/skins/gwr_101/lime.png")),
                new Entry("red", "Red", rl("textures/skins/gwr_101/red.png")),
                new Entry("white", "White", rl("textures/skins/gwr_101/white.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gwr_101/magenta.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/gwr_101/light_grey.png")),
                new Entry("purple", "Purple", rl("textures/skins/gwr_101/purple.png")),
                new Entry("black", "Black", rl("textures/skins/gwr_101/black.png")),
                new Entry("blue", "Blue", rl("textures/skins/gwr_101/blue.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gwr_101/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/gwr_101/orange.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gwr_101/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gwr_101/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gwr_101/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/gwr_101/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_gwr_72xx.png", List.of(
                new Entry("lime", "Lime", rl("textures/skins/gwr_72xx/lime.png")),
                new Entry("black", "Black", rl("textures/skins/gwr_72xx/black.png")),
                new Entry("blue", "Blue", rl("textures/skins/gwr_72xx/blue.png")),
                new Entry("green", "Green", rl("textures/skins/gwr_72xx/green.png")),
                new Entry("white", "White", rl("textures/skins/gwr_72xx/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gwr_72xx/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/gwr_72xx/orange.png")),
                new Entry("red", "Red", rl("textures/skins/gwr_72xx/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gwr_72xx/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gwr_72xx/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gwr_72xx/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gwr_72xx/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/gwr_72xx/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_hall_class.png", List.of(
                new Entry("red", "Red", rl("textures/skins/hall_class/red.png")),
                new Entry("lime", "Lime", rl("textures/skins/hall_class/lime.png")),
                new Entry("green", "Green", rl("textures/skins/hall_class/green.png")),
                new Entry("white", "White", rl("textures/skins/hall_class/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/hall_class/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/hall_class/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/hall_class/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/hall_class/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/hall_class/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/hall_class/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/hall_class/pink.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_d51_long_body.png", List.of(
                new Entry("grey", "Grey", rl("textures/skins/d51_long/grey.png")),
                new Entry("black", "Black", rl("textures/skins/d51_long/black.png")),
                new Entry("white", "White", rl("textures/skins/d51_long/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/d51_long/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/d51_long/orange.png")),
                new Entry("red", "Red", rl("textures/skins/d51_long/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/d51_long/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/d51_long/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/d51_long/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/d51_long/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/d51_long/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/d51_long/green.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_br80.png", List.of(
                new Entry("black", "Black", rl("textures/skins/br80/black.png")),
                new Entry("green", "Green", rl("textures/skins/br80/green.png")),
                new Entry("winter", "Winter", rl("textures/skins/br80/winter.png")),
                new Entry("white", "White", rl("textures/skins/br80/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br80/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/br80/orange.png")),
                new Entry("red", "Red", rl("textures/skins/br80/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br80/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/br80/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br80/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br80/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br80/cyan.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_midland_compound.png", List.of(
                new Entry("grey", "Grey", rl("textures/skins/midland_compound/grey.png")),
                new Entry("black", "Black", rl("textures/skins/midland_compound/black.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/midland_compound/magenta.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/midland_compound/yellow.png")),
                new Entry("red", "Red", rl("textures/skins/midland_compound/red.png")),
                new Entry("pink", "Pink", rl("textures/skins/midland_compound/pink.png")),
                new Entry("white", "White", rl("textures/skins/midland_compound/white.png")),
                new Entry("orange", "Orange", rl("textures/skins/midland_compound/orange.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/midland_compound/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/midland_compound/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/midland_compound/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/midland_compound/green.png"))
        ));
                put("textures/trains/legacy/locomotive_steam_gs4.png", List.of(
                new Entry("orange", "Orange", rl("textures/skins/gs4_engine/orange.png")),
                new Entry("white", "White", rl("textures/skins/gs4_engine/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gs4_engine/yellow.png")),
                new Entry("red", "Red", rl("textures/skins/gs4_engine/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gs4_engine/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/gs4_engine/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gs4_engine/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gs4_engine/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gs4_engine/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/gs4_engine/green.png"))
        ));
                put("textures/trains/legacy/steam_tender_gs4.png", List.of(
                new Entry("orange", "Orange", rl("textures/skins/gs4_tender/orange.png")),
                new Entry("white", "White", rl("textures/skins/gs4_tender/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gs4_tender/yellow.png")),
                new Entry("red", "Red", rl("textures/skins/gs4_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gs4_tender/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/gs4_tender/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gs4_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gs4_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gs4_tender/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/gs4_tender/green.png"))
        ));
                        put("textures/trains/legacy/gs4_passenger_car.png", List.of(
                new Entry("black", "Black", rl("textures/skins/gs4_passenger/black.png")),
                new Entry("blue", "Blue", rl("textures/skins/gs4_passenger/blue.png")),
                new Entry("brown", "Brown", rl("textures/skins/gs4_passenger/brown.png")),
                new Entry("orange", "Orange", rl("textures/skins/gs4_passenger/orange.png")),
                new Entry("grey", "Grey", rl("textures/skins/gs4_passenger/grey.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gs4_passenger/yellow.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/gs4_passenger/light_blue.png")),
                new Entry("lime", "Lime", rl("textures/skins/gs4_passenger/lime.png")),
                new Entry("red", "Red", rl("textures/skins/gs4_passenger/red.png")),
                new Entry("green", "Green", rl("textures/skins/gs4_passenger/green.png")),
                new Entry("white", "White", rl("textures/skins/gs4_passenger/white.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/gs4_passenger/light_grey.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gs4_passenger/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gs4_passenger/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gs4_passenger/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gs4_passenger/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/gs4_passenger/pink.png"))
        ));
                        put("textures/trains/legacy/gs4_tail_car.png", List.of(
                new Entry("brown", "Brown", rl("textures/skins/gs4_tail/brown.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/gs4_tail/light_grey.png")),
                new Entry("lime", "Lime", rl("textures/skins/gs4_tail/lime.png")),
                new Entry("green", "Green", rl("textures/skins/gs4_tail/green.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/gs4_tail/light_blue.png")),
                new Entry("white", "White", rl("textures/skins/gs4_tail/white.png")),
                new Entry("red", "Red", rl("textures/skins/gs4_tail/red.png")),
                new Entry("black", "Black", rl("textures/skins/gs4_tail/black.png")),
                new Entry("orange", "Orange", rl("textures/skins/gs4_tail/orange.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gs4_tail/yellow.png")),
                new Entry("grey", "Grey", rl("textures/skins/gs4_tail/grey.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gs4_tail/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gs4_tail/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gs4_tail/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gs4_tail/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/gs4_tail/pink.png"))
        ));
                        put("textures/trains/legacy/gs4_baggage_car.png", List.of(
                new Entry("orange", "Orange", rl("textures/skins/gs4_baggage/orange.png")),
                new Entry("grey", "Grey", rl("textures/skins/gs4_baggage/grey.png")),
                new Entry("white", "White", rl("textures/skins/gs4_baggage/white.png")),
                new Entry("brown", "Brown", rl("textures/skins/gs4_baggage/brown.png")),
                new Entry("light_blue", "Light Blue", rl("textures/skins/gs4_baggage/light_blue.png")),
                new Entry("black", "Black", rl("textures/skins/gs4_baggage/black.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/gs4_baggage/yellow.png")),
                new Entry("lime", "Lime", rl("textures/skins/gs4_baggage/lime.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/gs4_baggage/light_grey.png")),
                new Entry("green", "Green", rl("textures/skins/gs4_baggage/green.png")),
                new Entry("red", "Red", rl("textures/skins/gs4_baggage/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/gs4_baggage/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/gs4_baggage/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/gs4_baggage/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/gs4_baggage/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/gs4_baggage/pink.png"))
        ));
                        put("textures/trains/legacy/locomotive_steam_4_4_0.png", List.of(
                new Entry("green", "Green", rl("textures/skins/four_four_zero/green.png")),
                new Entry("blue", "Blue", rl("textures/skins/four_four_zero/blue.png")),
                new Entry("red", "Red", rl("textures/skins/four_four_zero/red.png")),
                new Entry("purple", "Purple", rl("textures/skins/four_four_zero/purple.png")),
                new Entry("black", "Black", rl("textures/skins/four_four_zero/black.png")),
                new Entry("white", "White", rl("textures/skins/four_four_zero/white.png")),
                new Entry("brown", "Brown", rl("textures/skins/four_four_zero/brown.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/four_four_zero/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/four_four_zero/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/four_four_zero/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/four_four_zero/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/four_four_zero/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/four_four_zero/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/four_four_zero/pink.png"))
        ));
                        put("textures/trains/legacy/steam_tender_4_4_0.png", List.of(
                new Entry("purple", "Purple", rl("textures/skins/four_four_zero_tender/purple.png")),
                new Entry("blue", "Blue", rl("textures/skins/four_four_zero_tender/blue.png")),
                new Entry("white", "White", rl("textures/skins/four_four_zero_tender/white.png")),
                new Entry("black", "Black", rl("textures/skins/four_four_zero_tender/black.png")),
                new Entry("green", "Green", rl("textures/skins/four_four_zero_tender/green.png")),
                new Entry("red", "Red", rl("textures/skins/four_four_zero_tender/red.png")),
                new Entry("brown", "Brown", rl("textures/skins/four_four_zero_tender/brown.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/four_four_zero_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/four_four_zero_tender/orange.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/four_four_zero_tender/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/four_four_zero_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/four_four_zero_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/four_four_zero_tender/cyan.png")),
                new Entry("pink", "Pink", rl("textures/skins/four_four_zero_tender/pink.png"))
        ));
                        put("textures/trains/legacy/steam_tender_br1.png", List.of(
                new Entry("black", "Black", rl("textures/skins/br1_tender/black.png")),
                new Entry("grey", "Grey", rl("textures/skins/br1_tender/grey.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/br1_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/br1_tender/orange.png")),
                new Entry("blue", "Blue", rl("textures/skins/br1_tender/blue.png")),
                new Entry("light_grey", "Light Grey", rl("textures/skins/br1_tender/light_grey.png")),
                new Entry("green", "Green", rl("textures/skins/br1_tender/green.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/br1_tender/cyan.png")),
                new Entry("white", "White", rl("textures/skins/br1_tender/white.png")),
                new Entry("red", "Red", rl("textures/skins/br1_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/br1_tender/magenta.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/br1_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/br1_tender/indigo.png")),
                new Entry("pink", "Pink", rl("textures/skins/br1_tender/pink.png"))
        ));
                put("textures/trains/legacy/steam_tender_berkshire.png", List.of(
                new Entry("black", "Black", rl("textures/skins/berkshire_tender/black.png")),
                new Entry("grey", "Grey", rl("textures/skins/berkshire_tender/grey.png")),
                new Entry("white", "White", rl("textures/skins/berkshire_tender/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/berkshire_tender/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/berkshire_tender/orange.png")),
                new Entry("red", "Red", rl("textures/skins/berkshire_tender/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/berkshire_tender/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/berkshire_tender/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/berkshire_tender/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/berkshire_tender/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/berkshire_tender/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/berkshire_tender/green.png"))
        ));
        putFullRosterWheel("textures/trains/caboose.png", "caboose");
        putFullRosterWheel("textures/trains/chme3.png", "chme3");
        putFullRosterWheel("textures/trains/freightcart.png", "freightcart");
        putFullRosterWheel("textures/trains/legacy/gs4_observatory_car.png", "legacy_gs4_observatory_car");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_262t.png", "legacy_locomotive_steam_262t");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_adler.png", "legacy_locomotive_steam_adler");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_alco_sc4.png", "legacy_locomotive_steam_alco_sc4");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_alice_0_4_0.png", "legacy_locomotive_steam_alice_0_4_0");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_berkshire_1225.png", "legacy_locomotive_steam_berkshire_1225");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_berkshire_765.png", "legacy_locomotive_steam_berkshire_765");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c11.png", "legacy_locomotive_steam_c11");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c11_front_truck.png", "legacy_locomotive_steam_c11_front_truck");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c11_rear_truck.png", "legacy_locomotive_steam_c11_rear_truck");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c41.png", "legacy_locomotive_steam_c41");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c41_080.png", "legacy_locomotive_steam_c41_080");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c41t.png", "legacy_locomotive_steam_c41t");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c62_body.png", "legacy_locomotive_steam_c62_body");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c62_front_bogie.png", "legacy_locomotive_steam_c62_front_bogie");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_c62_rear_bogie.png", "legacy_locomotive_steam_c62_rear_bogie");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_cherepanov.png", "legacy_locomotive_steam_cherepanov");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_climax.png", "legacy_locomotive_steam_climax");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_climax_2.png", "legacy_locomotive_steam_climax_2");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_coronation_class.png", "legacy_locomotive_steam_coronation_class");
        putFullRosterD51("textures/trains/legacy/locomotive_steam_d51_body.png", "legacy_locomotive_steam_d51_body");
        putFullRosterD51("textures/trains/legacy/locomotive_steam_d51_front_bogie.png", "legacy_locomotive_steam_d51_front_bogie");
        putFullRosterD51("textures/trains/legacy/locomotive_steam_d51_rear_bogie.png", "legacy_locomotive_steam_d51_rear_bogie");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_fowler.png", "legacy_locomotive_steam_fowler");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_glyn_042t.png", "legacy_locomotive_steam_glyn_042t");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_gwr_42xx.png", "legacy_locomotive_steam_gwr_42xx");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_lssp7.png", "legacy_locomotive_steam_lssp7");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_milw_class_a.png", "legacy_locomotive_steam_milw_class_a");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_pannier.png", "legacy_locomotive_steam_pannier");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_pe.png", "legacy_locomotive_steam_pe");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_pe_front_truck.png", "legacy_locomotive_steam_pe_front_truck");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_pe_rear_truck.png", "legacy_locomotive_steam_pe_rear_truck");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_s100_uk.png", "legacy_locomotive_steam_s100_uk");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_s100_us.png", "legacy_locomotive_steam_s100_us");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_shay_3_truck.png", "legacy_locomotive_steam_shay_3_truck");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_skookum.png", "legacy_locomotive_steam_skookum");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_snow_plow.png", "legacy_locomotive_steam_snow_plow");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_southern1102.png", "legacy_locomotive_steam_southern1102");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_star_class.png", "legacy_locomotive_steam_star_class");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_u57.png", "legacy_locomotive_steam_u57");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_vb_0_4_0.png", "legacy_locomotive_steam_vb_0_4_0");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_vb_shay.png", "legacy_locomotive_steam_vb_shay");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_vb_shay_2.png", "legacy_locomotive_steam_vb_shay_2");
        putFullRosterWheel("textures/trains/legacy/locomotive_steam_wwcp_062t.png", "legacy_locomotive_steam_wwcp_062t");
        putFullRosterWheel("textures/trains/legacy/milw_baggage_car.png", "legacy_milw_baggage_car");
        putFullRosterWheel("textures/trains/legacy/milw_passenger_car.png", "legacy_milw_passenger_car");
        putFullRosterWheel("textures/trains/legacy/milw_tail_car.png", "legacy_milw_tail_car");
        putFullRosterWheel("textures/trains/legacy/steam_tender_4000_gallon.png", "legacy_steam_tender_4000_gallon");
        putFullRosterWheel("textures/trains/legacy/steam_tender_adler.png", "legacy_steam_tender_adler");
        putFullRosterWheel("textures/trains/legacy/steam_tender_c41.png", "legacy_steam_tender_c41");
        putFullRosterWheel("textures/trains/legacy/steam_tender_c62_body.png", "legacy_steam_tender_c62_body");
        putFullRosterWheel("textures/trains/legacy/steam_tender_c62_bogie.png", "legacy_steam_tender_c62_bogie");
        putFullRosterWheel("textures/trains/legacy/steam_tender_coronation_class.png", "legacy_steam_tender_coronation_class");
        putFullRosterWheel("textures/trains/legacy/steam_tender_d51_body.png", "legacy_steam_tender_d51_body");
        putFullRosterWheel("textures/trains/legacy/steam_tender_fowler.png", "legacy_steam_tender_fowler");
        putFullRosterWheel("textures/trains/legacy/steam_tender_midland_compound.png", "legacy_steam_tender_midland_compound");
        putFullRosterWheel("textures/trains/legacy/steam_tender_milw.png", "legacy_steam_tender_milw");
        putFullRosterWheel("textures/trains/legacy/steam_tender_pe.png", "legacy_steam_tender_pe");
        putFullRosterWheel("textures/trains/legacy/steam_tender_rw_type_2.png", "legacy_steam_tender_rw_type_2");
        putFullRosterWheel("textures/trains/legacy/steam_tender_shay_3_truck.png", "legacy_steam_tender_shay_3_truck");
        putFullRosterWheel("textures/trains/legacy/steam_tender_skookum.png", "legacy_steam_tender_skookum");
        putFullRosterWheel("textures/trains/legacy/steam_tender_southern1102.png", "legacy_steam_tender_southern1102");
        putFullRosterWheel("textures/trains/legacy/steam_tender_star_class.png", "legacy_steam_tender_star_class");
        putFullRosterWheel("textures/trains/legacy/steam_tender_u57.png", "legacy_steam_tender_u57");
        putFullRosterWheel("textures/trains/loco_high_speed_zeroed.png", "loco_high_speed_zeroed");
        putFullRosterWheel("textures/trains/loco_minetrain.png", "loco_minetrain");
        putFullRosterWheel("textures/trains/loco_tram_ny.png", "loco_tram_ny");
        putFullRosterWheel("textures/trains/locoer_ussr.png", "locoer_ussr");
        putFullRosterWheel("textures/trains/locoshay.png", "locoshay");
        putFullRosterWheel("textures/trains/steam_tender_red.png", "steam_tender_red");
        putFullRosterWheel("textures/trains/tenderer_ussr.png", "tenderer_ussr");
        putFullRosterWheel("textures/trains/tram.png", "tram");
        putFullRosterWheel("textures/trains/vl10.png", "vl10");
    }

    // TC_BATCH6_R5_FULL_ROSTER_COLORS
    private static void putFullRosterWheel(String basePath, String family) {
        put(basePath, List.of(
                new Entry("white", "White", rl("textures/skins/full_roster/" + family + "/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/full_roster/" + family + "/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/full_roster/" + family + "/orange.png")),
                new Entry("red", "Red", rl("textures/skins/full_roster/" + family + "/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/full_roster/" + family + "/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/full_roster/" + family + "/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/full_roster/" + family + "/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/full_roster/" + family + "/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/full_roster/" + family + "/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/full_roster/" + family + "/green.png"))
        ));
    }

    private static void putFullRosterD51(String basePath, String family) {
        put(basePath, List.of(
                new Entry("grey", "Grey", rl("textures/skins/full_roster/" + family + "/grey.png")),
                new Entry("black", "Black", rl("textures/skins/full_roster/" + family + "/black.png")),
                new Entry("white", "White", rl("textures/skins/full_roster/" + family + "/white.png")),
                new Entry("yellow", "Yellow", rl("textures/skins/full_roster/" + family + "/yellow.png")),
                new Entry("orange", "Orange", rl("textures/skins/full_roster/" + family + "/orange.png")),
                new Entry("red", "Red", rl("textures/skins/full_roster/" + family + "/red.png")),
                new Entry("magenta", "Magenta", rl("textures/skins/full_roster/" + family + "/magenta.png")),
                new Entry("pink", "Pink", rl("textures/skins/full_roster/" + family + "/pink.png")),
                new Entry("blue_violet", "Blue-Violet", rl("textures/skins/full_roster/" + family + "/blue_violet.png")),
                new Entry("indigo", "Indigo", rl("textures/skins/full_roster/" + family + "/indigo.png")),
                new Entry("cyan", "Cyan", rl("textures/skins/full_roster/" + family + "/cyan.png")),
                new Entry("green", "Green", rl("textures/skins/full_roster/" + family + "/green.png"))
        ));
    }
    private static void put(String basePath, List<Entry> entries) {
        BY_BASE.put(basePath, entries);
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(Traincraft.MOD_ID, path);
    }

    public static List<Entry> entries(ResourceLocation baseTexture) {
        if (baseTexture == null || !Traincraft.MOD_ID.equals(baseTexture.getNamespace())) {
            return List.of();
        }
        return BY_BASE.getOrDefault(baseTexture.getPath(), List.of());
    }

    public static ResourceLocation resolve(ResourceLocation baseTexture, String key) {
        if (key == null || key.isEmpty()) {
            return baseTexture;
        }
        for (Entry entry : entries(baseTexture)) {
            if (entry.key().equals(key)) {
                return entry.texture();
            }
        }
        return baseTexture;
    }

    private LegacySkinCatalog() {}
}
