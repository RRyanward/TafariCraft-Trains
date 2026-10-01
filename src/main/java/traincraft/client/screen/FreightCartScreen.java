/* Exact classic Traincraft freight-container presentation for 1.20.1. */
package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.menu.FreightCartMenu;

/** Four-row classic freight GUI using the original textures/gui/container.png. */
public final class FreightCartScreen extends AbstractContainerScreen<FreightCartMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/container.png");
    private static final int CARGO_ROWS = 4;
    private static final int UPPER_HEIGHT = CARGO_ROWS * 18 + 17; // 89

    public FreightCartScreen(FreightCartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + CARGO_ROWS * 18; // classic GuiFreight = 186
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE,
                this.leftPos, this.topPos,
                0, 0,
                this.imageWidth, UPPER_HEIGHT,
                256, 256);
        graphics.blit(TEXTURE,
                this.leftPos, this.topPos + UPPER_HEIGHT,
                0, 126,
                this.imageWidth, 96,
                256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        /* Original GuiFreight positions/colors. The legacy lock button is omitted
         * until the old ownership/locking system is ported. */
        graphics.drawString(this.font, this.title, 10, 6, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                8, this.imageHeight - 96 + 2, 0x404040, false);
    }
}
