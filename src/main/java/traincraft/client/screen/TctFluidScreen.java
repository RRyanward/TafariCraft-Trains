package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;
import traincraft.menu.TctFluidMenu;

/** Standalone tanker gauge: current fluid, fill level, and 64-bucket capacity only. */
public final class TctFluidScreen extends AbstractContainerScreen<TctFluidMenu> {
    public TctFluidScreen(TctFluidMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 226;
        this.imageHeight = 138;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = this.leftPos, y = this.topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF151C24);
        g.fill(x+2, y+2, x+imageWidth-2, y+imageHeight-2, 0xFF303E4A);
        g.fill(x+6, y+6, x+imageWidth-6, y+imageHeight-6, 0xFF1B2630);
        g.fill(x+17, y+44, x+51, y+113, 0xFF090F15);
        g.fill(x+20, y+47, x+48, y+110, 0xFF4B5762);
        int amt = Math.max(0, Math.min(menu.amountMb(), menu.capacityMb()));
        int height = (int)((long)amt * 61L / menu.capacityMb());
        if (height > 0) {
            int color = amt > 0 && menu.fluidType().isSame(net.minecraft.world.level.material.Fluids.LAVA)
                    ? 0xFFF09B35 : 0xFF55B8D5;
            g.fill(x+22, y+108-height, x+46, y+108, color);
        }
        for (int i=0;i<=4;i++) {
            int h = y+108-i*15;
            g.fill(x+50, h, x+56, h+1, 0xFF9FAFBC);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        int amount = menu.amountMb();
        int capacity = menu.capacityMb();
        String fluidName = amount == 0 ? "Empty" :
                new FluidStack(menu.fluidType(), 1000).getDisplayName().getString();
        int percent = (int) ((long) amount * 100 / capacity);
        g.drawString(font, menu.tankerName(), 16, 13, 0xFFFFFF, false);
        g.drawString(font, "FLUID CONTENTS", 67, 46, 0x88CEDF, false);
        g.drawString(font, fluidName, 67, 61, 0xFFFFFF, false);
        g.drawString(font, String.format("%,d / %,d mB", amount, capacity), 67, 82, 0xE4E6E9, false);
        g.drawString(font, percent+"% full", 67, 98, 0x88CEDF, false);
        g.drawString(font, "Read-only  |  Transfer via fluid dock", 14, 122, 0xB2C0C9, false);
    }
}
