package traincraft.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** DEV58B10F8: purely client-side GUI label resolution; no entity/menu changes. */
final class TrainGuiNames {
    private TrainGuiNames() {}

    static Component fromEntity(Entity locomotive) {
        if (locomotive == null) return Component.empty();
        Component custom = locomotive.getCustomName();
        if (custom != null && !custom.getString().isBlank()) return custom;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(locomotive.getType());
        if (type != null) {
            Item item = ForgeRegistries.ITEMS.getValue(type);
            // Forge's registry can default unknown keys to AIR; verify identity.
            if (item != null && type.equals(ForgeRegistries.ITEMS.getKey(item))) {
                return new ItemStack(item).getHoverName();
            }
        }
        return locomotive.getType().getDescription();
    }

    // Keep long names legible without changing or replacing the classic GUI textures.
    static void drawFit(GuiGraphics graphics, Font font, Component label,
                        int x, int y, int maxWidth, int color, boolean centered) {
        float scale = Math.min(1.0F,
                maxWidth / (float)Math.max(1, font.width(label)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        if (centered) graphics.drawCenteredString(font, label, 0, 0, color);
        else graphics.drawString(font, label, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * Classic Traincraft title: eight black outline pixels and a golden center.
     * Draw in ABSOLUTE screen coordinates, above the texture, after super.render().
     * Font scale is capped at 1 and automatically fits long/custom train names.
     */
    static void drawClassicHeader(GuiGraphics graphics, Font font, Component name,
                                  int left, int top, int panelWidth) {
        String label = name == null ? "" : name.getString();
        if (label.isBlank()) return;
        float scale = Math.min(1.0F, (panelWidth - 8.0F) /
                (float) Math.max(1, font.width(label) + 2));
        graphics.pose().pushPose();
        graphics.pose().translate(left + panelWidth / 2.0F, Math.max(3, top - 40), 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int x = -font.width(label) / 2;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    graphics.drawString(font, label, x + dx, dy, 0xFF000000, false);
                }
            }
        }
        graphics.drawString(font, label, x, 0, 0xFFD3A900, false);
        graphics.pose().popPose();
    }

}
