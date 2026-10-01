package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.menu.ProcessMachineMenu;

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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.isDistillery() ? DISTILLERY : HEARTH,
                leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int progress = menu.getProgressScaled(24);
        if (progress > 0) {
            graphics.fill(leftPos + 79, topPos + 34, leftPos + 79 + progress, topPos + 49, 0xFFE7A52D);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = menu.isDistillery() ? "Distillation Tower" : "Open Hearth Furnace";
        graphics.drawString(font, name, 8, 6, 0x404040, false);
        graphics.drawString(font, "Inventory", 8, imageHeight - 94, 0x404040, false);

        if (menu.isDistillery()) {
            graphics.drawString(font, "Input", 50, 7, 0x404040, false);
            graphics.drawString(font, "Fuel", 52, 43, 0x404040, false);
        } else {
            graphics.drawString(font, "Iron + Graphite", 25, 7, 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}