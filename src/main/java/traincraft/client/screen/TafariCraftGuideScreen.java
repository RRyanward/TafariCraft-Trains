package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import traincraft.menu.GuideMenu;

import java.util.List;

public final class TafariCraftGuideScreen extends AbstractContainerScreen<GuideMenu> {
    private int page = 0;

    private static final String[] TITLES = {
            "Welcome",
            "Survival Progression",
            "Steam Trains",
            "Diesel & Electric",
            "Tracks Builder",
            "Track Power & Controls"
    };

    private static final String[] BODY = {
            "Welcome to TafariCraft - Trains. This guide covers the restored Traincraft survival progression, train controls, fuel and power systems, workshop machines, and track tools.",
            "Use the Train Workbench and Assembly Tables for train crafting. The Open Hearth turns Iron + Graphite + fuel into Steel. The Distillation Tower turns Oil Sand, Petroleum, Sugar Cane, Wheat, or Leaves into Diesel Fuel.",
            "Steam locomotives use their classic boiler system. Keep water and fuel supplied. R opens the locomotive interface while riding. Space applies the service brake, C toggles the handbrake, and the horn/whistle key sounds the locomotive whistle.",
            "Diesels consume Diesel Fuel and never use Energy Track line power. Electric locomotives prefer live Energy Track power; one redstone source feeds up to 20 connected Energy Track blocks. Off-line electric traction requires Redstone Dust or a Redstone Block physically present in the left backup slot, and that item is consumed while moving.",
            "Tracks Builder uses its classic large inventory. Fuel goes in the fuel slot, track in the track slot, and ballast/foundation blocks in their marked slots. While powered and moving it lays the selected track/support material.",
            "Energy Track must receive a live redstone signal. The electric locomotive GUI shows LINE POWER ON/OFF. If OFF, verify the locomotive is on Energy Track and that the rail block is receiving redstone power. Horn Track triggers the locomotive horn automatically."
    };

    public TafariCraftGuideScreen(GuideMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
        }).bounds(leftPos + 12, topPos + 158, 28, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = Math.min(TITLES.length - 1, page + 1);
        }).bounds(leftPos + imageWidth - 40, topPos + 158, 28, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3B2C20);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, 0xFFF0E1B8);
        graphics.fill(leftPos + 10, topPos + 29, leftPos + imageWidth - 10, topPos + 31, 0xFF8B5A2B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, "TafariCraft - Trains Guide", imageWidth / 2, 9, 0x5B321C);
        graphics.drawCenteredString(font, TITLES[page], imageWidth / 2, 18, 0x8A4F21);

        List<FormattedCharSequence> lines =
                font.split(Component.literal(BODY[page]), imageWidth - 32);
        int y = 42;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, 16, y, 0x2B2018, false);
            y += 11;
        }

        graphics.drawCenteredString(
                font,
                (page + 1) + " / " + TITLES.length,
                imageWidth / 2,
                164,
                0x5B321C);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}