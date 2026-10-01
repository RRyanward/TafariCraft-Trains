package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import traincraft.menu.TransportCarMenu;

/**
 * TC_TRANSPORT_GUI_PORTRAITS_R1F20I
 *
 * Uses live mini-portraits for occupants instead of spawn eggs so the GUI
 * shows the actual mob / villager / player loaded inside the car.
 */
public final class TransportCarScreen
        extends AbstractContainerScreen<TransportCarMenu> {

    private final Button[] rowButtons = new Button[3];
    private Button unloadAllButton;

    public TransportCarScreen(
            TransportCarMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 146;
    }

    @Override
    protected void init() {
        super.init();

        for (int slot = 0; slot < 3; slot++) {
            final int buttonId = slot;
            int y = this.topPos + 35 + slot * 30;

            this.rowButtons[slot] = this.addRenderableWidget(
                    Button.builder(
                                    Component.literal("Load"),
                                    button -> sendButton(buttonId))
                            .bounds(this.leftPos + 159, y, 62, 20)
                            .build());
        }

        this.unloadAllButton = this.addRenderableWidget(
                Button.builder(
                                Component.literal("Unload All"),
                                button -> sendButton(TransportCarMenu.BUTTON_UNLOAD_ALL))
                        .bounds(this.leftPos + 70, this.topPos + 126, 96, 20)
                        .build());
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {

        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF252525);
        graphics.fill(x + 3, y + 3, x + this.imageWidth - 3, y + this.imageHeight - 3, 0xFFC6C6C6);
        graphics.fill(x + 7, y + 25, x + this.imageWidth - 7, y + 119, 0xFF4B4B4B);

        for (int slot = 0; slot < 3; slot++) {
            int sy = y + 32 + slot * 30;
            boolean occupied = this.menu.hasPassenger(slot);

            graphics.fill(x + 14, sy, x + 151, sy + 24, occupied ? 0xFFDADADA : 0xFF777777);
            graphics.fill(x + 18, sy + 4, x + 38, sy + 24, occupied ? 0xFF5E8C5E : 0xFF555555);
            graphics.fill(x + 19, sy + 5, x + 37, sy + 23, occupied ? 0xFFAFCFAF : 0xFF666666);

            if (occupied) {
                renderPortrait(graphics, slot, x + 28, sy + 23);
            }
        }
    }

    private void renderPortrait(
            GuiGraphics graphics,
            int slot,
            int centerX,
            int baseY) {

        Entity entity = this.menu.passengerEntity(slot);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        int scale = 10;
        if (entity.getBbHeight() > 1.5F) {
            scale = 9;
        }
        if (entity.getBbHeight() < 1.0F) {
            scale = 11;
        }

        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics,
                centerX,
                baseY,
                scale,
                0.0F,
                0.0F,
                living);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {

        graphics.drawString(this.font, this.title, 10, 8, 0x202020, false);

        if (this.menu.locked()) {
            graphics.drawString(this.font, "LOCKED - owner Wrench required", 10, 19, 0xA00000, false);
        } else {
            graphics.drawString(
                    this.font,
                    this.menu.isPrisonerCar() ? "Transport Rope manifest" : "Lead manifest",
                    10,
                    19,
                    0x404040,
                    false);
        }

        for (int slot = 0; slot < 3; slot++) {
            int y = 39 + slot * 30;
            boolean occupied = this.menu.hasPassenger(slot);

            graphics.drawString(this.font, "Slot " + (slot + 1), 44, y, 0x202020, false);
            graphics.drawString(
                    this.font,
                    this.menu.passengerName(slot),
                    44,
                    y + 10,
                    occupied ? 0x202020 : 0x606060,
                    false);
        }
    }

    private void updateButtons() {
        boolean locked = this.menu.locked();
        boolean any = false;

        for (int slot = 0; slot < 3; slot++) {
            boolean occupied = this.menu.hasPassenger(slot);
            this.rowButtons[slot].active = !locked;
            this.rowButtons[slot].setMessage(Component.literal(occupied ? "Unload" : "Load"));
            any |= occupied;
        }

        this.unloadAllButton.active = any && !locked;
    }

    private void renderPortraitTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int slot = 0; slot < 3; slot++) {
            if (!this.menu.hasPassenger(slot)) {
                continue;
            }

            int iconX = this.leftPos + 19;
            int iconY = this.topPos + 37 + slot * 30;
            if (mouseX >= iconX && mouseX < iconX + 20 && mouseY >= iconY && mouseY < iconY + 20) {
                graphics.renderTooltip(this.font, this.menu.passengerName(slot), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {

        this.updateButtons();
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderPortraitTooltip(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}