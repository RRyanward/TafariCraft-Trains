/*
 * Traincraft steam-locomotive screen for Minecraft 1.20.1.
 * Step 7.1.2 is the exact 1.7.10 visual restoration pass.
 * The original Traincraft artwork, title coordinates, water column, flame sprite,
 * and parking-brake sprite placement are preserved while the 1.20.1 backend stays intact.
 */
package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.client.TCKeyMappings;
import traincraft.menu.SteamLocomotiveMenu;
import traincraft.network.TCNetwork;
import traincraft.network.packet.LocomotiveControlPacket;
import traincraft.network.packet.LocomotiveWhistlePacket;
import traincraft.network.packet.ToggleHandBrakePacket;

public final class SteamLocomotiveScreen extends AbstractContainerScreen<SteamLocomotiveMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_loco_steam.png");
    private static final ResourceLocation CLASSIC_BUTTONS =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/custombutton.png");

    /* Exact 1.7.10 steam-locomotive parking-brake sprite regions. */
    private static final int BRAKE_WIDTH = 40;
    private static final int BRAKE_HEIGHT = 13;
    private static final int BRAKE_TEXTURE_Y = 13;

    private boolean guiForward;
    private boolean guiReverse;
    private boolean guiBrake;
    private boolean handBrakeKeyHeld;
    private boolean whistleKeyHeld;

    public SteamLocomotiveScreen(SteamLocomotiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = -1000;
        Traincraft.LOGGER.debug("Traincraft classic SteamLocomotiveScreen constructed for entity {}",
                menu.getLocomotive().getId());
    }

    /** Keep classic driving controls active while the locomotive GUI is open. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null) {
            if (this.minecraft.options.keyUp.matches(keyCode, scanCode)) {
                if (!this.guiForward) {
                    this.guiForward = true;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (this.minecraft.options.keyDown.matches(keyCode, scanCode)) {
                if (!this.guiReverse) {
                    this.guiReverse = true;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (this.minecraft.options.keyJump.matches(keyCode, scanCode)) {
                if (!this.guiBrake) {
                    this.guiBrake = true;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (TCKeyMappings.HAND_BRAKE.matches(keyCode, scanCode)) {
                if (!this.handBrakeKeyHeld) {
                    this.handBrakeKeyHeld = true;
                    TCNetwork.CHANNEL.sendToServer(
                            new ToggleHandBrakePacket(this.menu.getLocomotive().getId()));
                }
                return true;
            }

            if (TCKeyMappings.WHISTLE.matches(keyCode, scanCode)) {
                if (!this.whistleKeyHeld) {
                    this.whistleKeyHeld = true;
                    TCNetwork.CHANNEL.sendToServer(
                            new LocomotiveWhistlePacket(this.menu.getLocomotive().getId()));
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null) {
            if (this.minecraft.options.keyUp.matches(keyCode, scanCode)) {
                if (this.guiForward) {
                    this.guiForward = false;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (this.minecraft.options.keyDown.matches(keyCode, scanCode)) {
                if (this.guiReverse) {
                    this.guiReverse = false;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (this.minecraft.options.keyJump.matches(keyCode, scanCode)) {
                if (this.guiBrake) {
                    this.guiBrake = false;
                    sendGuiDrivingState();
                }
                return true;
            }

            if (TCKeyMappings.HAND_BRAKE.matches(keyCode, scanCode)) {
                this.handBrakeKeyHeld = false;
                return true;
            }

            if (TCKeyMappings.WHISTLE.matches(keyCode, scanCode)) {
                this.whistleKeyHeld = false;
                return true;
            }
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void sendGuiDrivingState() {
        TCNetwork.CHANNEL.sendToServer(new LocomotiveControlPacket(
                this.menu.getLocomotive().getId(),
                this.guiForward,
                this.guiReverse,
                this.guiBrake));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        /* The original parking-brake button sits just above the panel. */
        renderClassicBrakeButton(graphics);
        this.renderTooltip(graphics, mouseX, mouseY);

        /* Match GuiLoco2's original 1.7.10 water tooltip wording exactly. */
        if (mouseX > this.leftPos + 143 && mouseX < this.leftPos + 161
                && mouseY > this.topPos + 18 && mouseY < this.topPos + 68) {
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

        /* Exact classic 50-pixel water-column math from GuiLoco2. */
        int waterCapacity = Math.max(1, this.menu.getWaterCapacity());
        int waterHeight = Mth.clamp(
                this.menu.getWaterAmount() * 50 / waterCapacity,
                0, 50);
        if (waterHeight > 0) {
            graphics.blit(TEXTURE,
                    this.leftPos + 143,
                    this.topPos + 68 - waterHeight,
                    190,
                    69 - waterHeight,
                    18,
                    waterHeight + 1);
        }

        /* Exact classic 12-step fire sprite placement from GuiLoco2. */
        int maxBurn = this.menu.getMaxBurnTime();
        if (this.menu.getBurnTime() > 0 && maxBurn > 0) {
            int fuelDiv = Mth.clamp(
                    this.menu.getBurnTime() * 12 / maxBurn,
                    0, 12);
            graphics.blit(TEXTURE,
                    this.leftPos + 8,
                    this.topPos + 36 + 12 - fuelDiv,
                    176,
                    12 - fuelDiv,
                    14,
                    fuelDiv + 2);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        /*
         * GuiLoco2.func_146979_b from 1.7.10 drew the locomotive name at these
         * exact nine positions: eight black outline pixels around a gold center.
         */
        String text = this.title.getString();
        graphics.drawString(this.font, text, 39, 7, 0x000000, false);
        graphics.drawString(this.font, text, 41, 5, 0x000000, false);
        graphics.drawString(this.font, text, 39, 5, 0x000000, false);
        graphics.drawString(this.font, text, 41, 7, 0x000000, false);
        graphics.drawString(this.font, text, 39, 6, 0x000000, false);
        graphics.drawString(this.font, text, 41, 6, 0x000000, false);
        graphics.drawString(this.font, text, 40, 7, 0x000000, false);
        graphics.drawString(this.font, text, 40, 5, 0x000000, false);
        graphics.drawString(this.font, text, 40, 6, 0xD3A900, false);
    }

    private int brakeButtonX() {
        /* Original GuiLoco2: ON at x+0, OFF at x+31. */
        return this.leftPos + (this.menu.isHandBrakeApplied() ? 0 : 31);
    }

    private int brakeTextureX() {
        /* Original customButton.png regions: ON x=0, OFF x=41, both y=13. */
        return this.menu.isHandBrakeApplied() ? 0 : 41;
    }

    private void renderClassicBrakeButton(GuiGraphics graphics) {
        graphics.blit(CLASSIC_BUTTONS,
                brakeButtonX(), this.topPos - 13,
                brakeTextureX(), BRAKE_TEXTURE_Y,
                BRAKE_WIDTH, BRAKE_HEIGHT,
                256, 256);
    }

    private boolean isBrakeButtonHovered(double mouseX, double mouseY) {
        int x = brakeButtonX();
        int y = this.topPos - 13;
        return mouseX >= x && mouseX < x + BRAKE_WIDTH
                && mouseY >= y && mouseY < y + BRAKE_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isBrakeButtonHovered(mouseX, mouseY)) {
            TCNetwork.CHANNEL.sendToServer(
                    new ToggleHandBrakePacket(this.menu.getLocomotive().getId()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
