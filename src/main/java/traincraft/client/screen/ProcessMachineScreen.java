package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.menu.ProcessMachineMenu;

/**
 * Classic machine art with a safe title strip above the original texture.
 * Keeps all accepted menu/slot coordinates unchanged.
 */
public final class ProcessMachineScreen extends AbstractContainerScreen<ProcessMachineMenu> {
    private static final ResourceLocation DISTILLERY =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_distillation_tower2.png");
    private static final ResourceLocation HEARTH =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_open_hearth_furnace.png");

    public ProcessMachineScreen(ProcessMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // Reserve the title strip instead of painting over slots/text baked into
        // the legacy machine GUI.
        topPos = Math.max(22, (height - imageHeight) / 2);
        leftPos = Math.max(0, (width - imageWidth) / 2);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.isDistillery() ? DISTILLERY : HEARTH,
                leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        ResourceLocation art = menu.isDistillery() ? DISTILLERY : HEARTH;
        if (menu.isBurning()) {
            int flame = menu.getBurnScaled(12);
            // Restore original Traincraft GUI flame sprites. Top source
            // texture includes the complete 14px flame at UV (176, 0).
            int top = topPos + 48 - flame;
            int texV = 12 - flame;
            graphics.blit(art, leftPos + 56, top, 176, texV, 14, flame + 2, 256, 256);
            if (!menu.isDistillery()) {
                graphics.blit(art, leftPos + 36, top, 176, texV, 14, flame + 2, 256, 256);
            }
        }

        int progress = menu.getProgressScaled(menu.isDistillery() ? 22 : 24);
        if (progress > 0) {
            // Original Traincraft animated progress-arrow sprites are already
            // stored in these 256x256 machine atlases. Do not substitute a
            // flat rectangle; that caused the orange square seen in DEV48a.
            if (menu.isDistillery()) {
                // The distillery's legacy arrow begins at atlas (184,32).
                graphics.blit(art, leftPos + 87, topPos + 36,
                        184, 32, progress, 20, 256, 256);
            } else {
                // The Open Hearth arrow begins at atlas (176,14).
                graphics.blit(art, leftPos + 79, topPos + 34,
                        176, 14, progress, 16, 256, 256);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The old texture already communicates its slot layout.  Do not draw a
        // second "Input/Fuel/Iron + Graphite" label over it.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String name = menu.isDistillery() ? "Distillation Tower" : "Open Hearth Furnace";
        graphics.fill(leftPos, topPos - 20, leftPos + imageWidth, topPos - 4, 0xE0202020);
        graphics.drawCenteredString(font, name, leftPos + imageWidth / 2, topPos - 16, 0xFFFFFF);

        if (menu.isDistillery() && isHovering(123, 35, 18, 18, mouseX, mouseY)
                && !menu.getSlot(3).hasItem()) {
            graphics.renderTooltip(font, Component.literal("Raw Plastic byproduct"), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }
}
