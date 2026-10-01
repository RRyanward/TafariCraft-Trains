package traincraft.client.event;

// TC_BATCH8_R4A2_CREATIVE_BADGE_CLEANUP

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * Minimal, less-busy corner badge style for Creative/inventory track icons.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrackIconBadgeDecorator {
    private static final IItemDecorator DECORATOR =
            TrackIconBadgeDecorator::render;

    private record BadgePath(
            String basePath,
            boolean energy) {}

    @SubscribeEvent
    public static void register(
            RegisterItemDecorationsEvent event) {
        // TC_BATCH8_R4A3C_DISABLE_AUTOMATIC_TRACK_BADGES
        // Deliberately register nothing.
        // Track icons stay clean in Creative, inventory and the hotbar.
        // badgeForStack()/renderBadge() remain available for dedicated GUIs.
    }

    private static boolean render(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int x,
            int y) {
        String badge = badgeForStack(stack);
        if (badge.isEmpty()) {
            return false;
        }
        renderBadge(graphics, font, badge, x, y);
        return false;
    }

    public static String badgeForStack(
            ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !Traincraft.MOD_ID.equals(key.getNamespace())) {
            return "";
        }
        return badgeForPath(key.getPath());
    }

    public static void renderBadge(
            GuiGraphics graphics,
            Font font,
            String badge,
            int x,
            int y) {
        // TC_BATCH8_R4A2_MINI_BADGE
        if (badge == null || badge.isEmpty()) {
            return;
        }

        int rawWidth = Math.max(1, font.width(badge));
        float scale = Math.min(0.56F, 10.0F / rawWidth);
        int boxWidth = Math.max(8, (int)Math.ceil(rawWidth * scale) + 3);

        int left = x + 1;
        int top = y + 1;
        int right = left + boxWidth;
        int bottom = top + 7;

        graphics.fill(left, top, right, bottom, 0xCC171A1E);
        graphics.fill(left, top, right, top + 1, 0xCC6C737C);
        graphics.fill(left, top, left + 1, bottom, 0xCC6C737C);
        graphics.fill(left, bottom - 1, right, bottom, 0xCC050608);
        graphics.fill(right - 1, top, right, bottom, 0xCC050608);

        graphics.pose().pushPose();
        graphics.pose().translate(left + 2.0F, top + 1.0F, 300.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, badge, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    public static String badgeForPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "";
        }
        if ("track_steel".equals(rawPath)
                || "track_copper".equals(rawPath)
                || "track_snow".equals(rawPath)
                || "track_snow_steel".equals(rawPath)
                || "track_snow_copper".equals(rawPath)) {
            return "N";
        }
        if ("track_energy".equals(rawPath)) {
            return "EN";
        }

        BadgePath parsed = stripMaterialSuffix(rawPath);
        String code = baseBadge(parsed.basePath());
        if (code.isEmpty()) {
            return "";
        }
        return parsed.energy() ? "E" + code : code;
    }

    private static BadgePath stripMaterialSuffix(String rawPath) {
        String path = rawPath;
        boolean energy = false;
        boolean changed;
        do {
            changed = false;
            if (path.endsWith("_energy")) {
                path = path.substring(0, path.length() - "_energy".length());
                energy = true;
                changed = true;
            } else if (path.endsWith("_steel")) {
                path = path.substring(0, path.length() - "_steel".length());
                changed = true;
            } else if (path.endsWith("_copper")) {
                path = path.substring(0, path.length() - "_copper".length());
                changed = true;
            } else if (path.endsWith("_snow")) {
                path = path.substring(0, path.length() - "_snow".length());
                changed = true;
            }
        } while (changed);
        return new BadgePath(path, energy);
    }

    private static String baseBadge(String path) {
        return switch (path) {
            case "track_normal" -> "N";
            case "track_medium_straight" -> "MST";
            case "track_long_straight" -> "LST";
            case "track_very_long_straight" -> "VLST";

            case "track_medium_curve" -> "MC";
            case "track_curve_big" -> "LC";
            case "track_curve_very_big" -> "VLC";
            case "track_curve_super_big" -> "SLC";
            case "track_curve_29x" -> "29C";
            case "track_curve_32x" -> "32C";

            case "track_curve_45_medium_right",
                 "track_curve_45_medium_left" -> "MC\u00B0";
            case "track_curve_45_large_right",
                 "track_curve_45_large_left" -> "LC\u00B0";
            case "track_curve_45_very_large_right",
                 "track_curve_45_very_large_left" -> "VLC\u00B0";
            case "track_curve_45_super_large_right",
                 "track_curve_45_super_large_left" -> "SLC\u00B0";

            case "track_parallel_curve_small_right",
                 "track_parallel_curve_small_left" -> "SPC";
            case "track_parallel_curve_medium_right",
                 "track_parallel_curve_medium_left" -> "MPC";
            case "track_parallel_curve_large_right",
                 "track_parallel_curve_large_left" -> "LPC";

            case "track_slope_small" -> "SS";
            case "track_slope_long" -> "LS";
            case "track_slope_very_long" -> "VLS";

            case "track_slope_curve_medium_right",
                 "track_slope_curve_medium_left" -> "MSC";
            case "track_slope_curve_large_right",
                 "track_slope_curve_large_left" -> "LSC";
            case "track_slope_curve_very_large_right",
                 "track_slope_curve_very_large_left" -> "VLSC";
            case "track_slope_curve_super_large_right",
                 "track_slope_curve_super_large_left" -> "SLSC";

            case "track_switch_small_right",
                 "track_switch_small_left" -> "SSW";
            case "track_switch_medium_right",
                 "track_switch_medium_left" -> "MSW";
            case "track_switch_large_right",
                 "track_switch_large_left" -> "LSW";
            case "track_switch_very_large_right",
                 "track_switch_very_large_left" -> "VLSW";
            case "track_switch_45_medium_right",
                 "track_switch_45_medium_left" -> "MSW\u00B0";
            case "track_switch_parallel_right",
                 "track_switch_parallel_left" -> "PSW";

            case "track_crossing_x" -> "XC";
            case "track_diamond_crossing",
                 "track_diamond_crossing_left" -> "DC";
            case "track_double_diamond_crossing" -> "DDC";
            case "track_diagonal_crossing" -> "DGC";
            case "track_diagonal_two_ways_crossing" -> "D2C";
            case "track_diagonal_four_ways_crossing" -> "D4C";
            case "track_universal_crossing" -> "UC";

            case "track_diagonal_straight_small" -> "SDS";
            case "track_diagonal_straight_medium" -> "MDS";
            default -> "";
        };
    }

    private TrackIconBadgeDecorator() {}
}
