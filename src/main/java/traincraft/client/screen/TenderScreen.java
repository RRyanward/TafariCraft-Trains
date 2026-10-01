/*
 * Exact Traincraft 1.7.10 tender GUI presentation for the 1.20.1 port.
 * Step 7.1.2 changes presentation only; the working tender supply backend is untouched.
 */
package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.menu.TenderMenu;

public final class TenderScreen extends AbstractContainerScreen<TenderMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_tender.png");

    public TenderScreen(TenderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = -1000;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        if (mouseX > this.leftPos + 143 && mouseX < this.leftPos + 161
                && mouseY > this.topPos + 18 && mouseY < this.topPos + 68) {
            /* Original GuiTender 1.7.10 tooltip wording. */
            graphics.renderTooltip(this.font,
                    Component.literal("Water: " + this.menu.getWaterAmount()
                            + "mb / " + this.menu.getWaterCapacity() + "mb"),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos,
                0, 0, this.imageWidth, this.imageHeight);

        /* Exact classic GuiTender 50-pixel water gauge. */
        int capacity = Math.max(1, this.menu.getWaterCapacity());
        int waterHeight = Mth.clamp(
                this.menu.getWaterAmount() * 50 / capacity,
                0, 50);
        if (waterHeight > 0) {
            graphics.blit(TEXTURE,
                    this.leftPos + 143,
                    this.topPos + 69 - waterHeight,
                    190,
                    69 - waterHeight,
                    18,
                    waterHeight);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String text = this.title.getString();
        /* Exact GuiTender.func_146979_b coordinates from 1.7.10. */
        graphics.drawString(this.font, text, 34, 1, 0x000000, false);
        graphics.drawString(this.font, text, 36, 3, 0x000000, false);
        graphics.drawString(this.font, text, 34, 3, 0x000000, false);
        graphics.drawString(this.font, text, 36, 1, 0x000000, false);
        graphics.drawString(this.font, text, 34, 2, 0x000000, false);
        graphics.drawString(this.font, text, 36, 2, 0x000000, false);
        graphics.drawString(this.font, text, 35, 3, 0x000000, false);
        graphics.drawString(this.font, text, 35, 1, 0x000000, false);
        graphics.drawString(this.font, text, 35, 2, 0xD3A900, false);
    }
}
