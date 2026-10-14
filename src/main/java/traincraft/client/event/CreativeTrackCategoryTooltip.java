package traincraft.client.event;

// TC_FINAL_RELEASE_R1AH_CREATIVE_TRACK_CATEGORY_COLORS

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreativeTrackCategoryTooltip {
    private CreativeTrackCategoryTooltip() {}

    private record Category(
            String name,
            ChatFormatting color) {}

    @SubscribeEvent
    public static void onTooltip(
            ItemTooltipEvent event) {
        Minecraft mc =
                Minecraft.getInstance();

        if (!(mc.screen
                instanceof CreativeModeInventoryScreen)) {
            return;
        }

        ItemStack stack =
                event.getItemStack();

        ResourceLocation key =
                ForgeRegistries.ITEMS.getKey(
                        stack.getItem());

        if (key == null
                || !Traincraft.MOD_ID.equals(
                        key.getNamespace())) {
            return;
        }

        Category category =
                categoryFor(
                        key.getPath());

        if (category == null) {
            return;
        }

        List<Component> tooltip =
                event.getToolTip();

        int insertAt =
                Math.min(
                        1,
                        tooltip.size());

        tooltip.add(
                insertAt,
                Component.literal(
                                "Category: "
                                        + category.name())
                        .withStyle(
                                category.color()));
    }

    private static Category categoryFor(
            String id) {
        if (isTrackside(
                id)) {
            return new Category(
                    "Trackside / Tools",
                    ChatFormatting.GRAY);
        }

        String base =
                geometryBaseId(
                        id);

        if (isBasicStraight(
                        base,
                        id)) {
            return new Category(
                    "Straight",
                    ChatFormatting.GREEN);
        }

        if (base.startsWith(
                "track_diagonal_straight")) {
            return new Category(
                    "Diagonal",
                    ChatFormatting.AQUA);
        }

        if (base.startsWith(
                "track_curve_45_")) {
            return new Category(
                    "45 Curves",
                    ChatFormatting.GOLD);
        }

        if ("track_curve_29x".equals(base)
                || "track_curve_32x".equals(base)) {
            return new Category(
                    "29x / 32x Curves",
                    ChatFormatting.GOLD);
        }

        if (base.startsWith(
                "track_parallel_curve_")) {
            return new Category(
                    "Parallel Curves",
                    ChatFormatting.GOLD);
        }

        if ("track_medium_curve".equals(base)
                || base.startsWith(
                        "track_curve_")) {
            return new Category(
                    "Curves",
                    ChatFormatting.GOLD);
        }

        if (base.startsWith(
                "track_switch_45_")) {
            return new Category(
                    "45 Switches",
                    ChatFormatting.YELLOW);
        }

        if (base.startsWith(
                "track_switch_parallel_")) {
            return new Category(
                    "Parallel Switches",
                    ChatFormatting.YELLOW);
        }

        if (base.startsWith(
                "track_switch_")) {
            return new Category(
                    "Switches",
                    ChatFormatting.YELLOW);
        }

        if (base.startsWith(
                "track_slope_curve_")) {
            return new Category(
                    "Curved Slopes",
                    ChatFormatting.LIGHT_PURPLE);
        }

        if (base.startsWith(
                "track_slope_")) {
            return new Category(
                    "Slopes",
                    ChatFormatting.LIGHT_PURPLE);
        }

        if (base.contains(
                        "crossing")
                && !"track_crossing_approach".equals(
                        base)) {
            return new Category(
                    "Crossings",
                    ChatFormatting.RED);
        }

        if (id.startsWith(
                        "track_")) {
            return new Category(
                    "Specialty",
                    ChatFormatting.GRAY);
        }

        return null;
    }

    private static String geometryBaseId(
            String id) {
        String basic =
                basicMaterialGeometry(
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

    private static boolean isBasicStraight(
            String base,
            String id) {
        return "track_normal".equals(base)
                || "track_normal".equals(
                        basicMaterialGeometry(
                                id))
                || (base.contains("_straight")
                        && !base.startsWith(
                                "track_diagonal_"));
    }

    private static String basicMaterialGeometry(
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

    private static boolean isTrackside(
            String id) {
        return "tracks_builder".equals(id)
                || "crossing_gate_base".equals(id)
                || "crossing_gate_pole".equals(id)
                || "crossing_gate_lamps".equals(id)
                || "crossing_gate_crossbuck".equals(id)
                || "crossing_gate_gate".equals(id);
    }
}