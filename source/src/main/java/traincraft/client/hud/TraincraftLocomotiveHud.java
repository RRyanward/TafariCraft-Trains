/*
 * Traincraft 1.20.1 classic locomotive HUD.
 *
 * Visual layout and sprite coordinates are matched to Traincraft 1.7.10
 * train.client.gui.HUDloco using the original loco_hud_steam.png artwork.
 * Modern locomotive backend values are only mapped onto the classic gauges;
 * movement, coupling, boiler, fuel and tender logic are not changed here.
 */
package traincraft.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import traincraft.Traincraft;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

public final class TraincraftLocomotiveHud {
    private static final ResourceLocation STEAM_HUD =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/loco_hud_steam.png");

    // Exact 1.7.10 steam HUD background region.
    private static final int PANEL_U = 0;
    private static final int PANEL_V = 150;
    private static final int PANEL_WIDTH = 137;
    private static final int PANEL_HEIGHT = 90;

    private TraincraftLocomotiveHud() {
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        if (!(minecraft.player.getVehicle() instanceof SmallSteamLocomotive locomotive)) {
            return;
        }

        gui.setupOverlayRenderState(true, false);

        // Traincraft 1.7.10 HUDloco used a fixed left offset of 10 and
        // windowHeight = scaledHeight - 100.
        int panelX = 10;
        int panelY = screenHeight - 100;

        // Original 1.7.10 steam HUD plate, unchanged.
        graphics.blit(STEAM_HUD,
                panelX, panelY,
                PANEL_U, PANEL_V,
                PANEL_WIDTH, PANEL_HEIGHT,
                256, 256);

        drawClassicFuelBar(graphics, locomotive, panelY);
        drawClassicHeatBar(graphics, locomotive, panelY);
        drawClassicWaterBar(graphics, locomotive, panelY);
        drawClassicSpeedNeedle(graphics, locomotive, panelY);
        drawClassicText(graphics, minecraft, locomotive, panelY);
    };

    /**
     * 1.7.10 HUDloco.renderFuelBar() steam coordinates:
     * x=34, y=windowHeight+17, u=154, v=170+fuel, w=9, h=70-fuel.
     *
     * The old locomotive stored a 0..1200 fuel counter. The 1.20.1 port uses
     * burnTime/maxBurnTime, so only that value mapping is modern; the artwork
     * and draw geometry are the original Traincraft layout.
     */
    private static void drawClassicFuelBar(net.minecraft.client.gui.GuiGraphics graphics,
                                           SmallSteamLocomotive locomotive,
                                           int panelY) {
        int burn = Math.max(0, locomotive.getBurnTime());
        int maxBurn = Math.max(0, locomotive.getMaxBurnTime());
        int fuelPixels = maxBurn <= 0
                ? 0
                : Mth.clamp((int) ((long) burn * 70L / (long) maxBurn), 0, 70);

        int height = 70 - fuelPixels;
        if (height > 0) {
            graphics.blit(STEAM_HUD,
                    34, panelY + 17,
                    154, 170 + fuelPixels,
                    9, height,
                    256, 256);
        }
    }

    /** Exact classic overheat/temperature column artwork and geometry. */
    private static void drawClassicHeatBar(net.minecraft.client.gui.GuiGraphics graphics,
                                           SmallSteamLocomotive locomotive,
                                           int panelY) {
        int heatPixels = Mth.clamp(
                locomotive.getBoilerHeatPercent() * 49 / 100,
                0, 49);

        int height = 49 - heatPixels;
        if (height > 0) {
            graphics.blit(STEAM_HUD,
                    56, panelY + 17,
                    176, 169 + heatPixels,
                    5, height,
                    256, 256);
        }
    }

    /** Exact 1.7.10 water-column artwork and geometry. */
    private static void drawClassicWaterBar(net.minecraft.client.gui.GuiGraphics graphics,
                                            SmallSteamLocomotive locomotive,
                                            int panelY) {
        int waterPixels = Mth.clamp(
                locomotive.getWaterAmount() * 49 / SmallSteamLocomotive.WATER_CAPACITY_MB,
                0, 49);

        int height = 49 - waterPixels;
        if (height > 0) {
            graphics.blit(STEAM_HUD,
                    70, panelY + 17,
                    190, 169 + waterPixels,
                    6, height,
                    256, 256);
        }
    }

    /**
     * Exact 1.7.10 speedometer needle geometry. HUDloco scaled its km/h value
     * against 280 km/h and moved the original 16x8 sprite vertically.
     */
    private static void drawClassicSpeedNeedle(net.minecraft.client.gui.GuiGraphics graphics,
                                               SmallSteamLocomotive locomotive,
                                               int panelY) {
        double speedKmh = Math.abs(locomotive.getSpeedKmh());
        int speedPixels = Mth.clamp((int) (speedKmh * 49.0D / 280.0D), 0, 49);

        graphics.blit(STEAM_HUD,
                84, panelY + 57 - speedPixels,
                177, 149,
                16, 8,
                256, 256);
    }

    /** Exact classic text placement: no centering and no modern drop shadow. */
    private static void drawClassicText(net.minecraft.client.gui.GuiGraphics graphics,
                                        Minecraft minecraft,
                                        SmallSteamLocomotive locomotive,
                                        int panelY) {
        int speed = (int) Math.abs(locomotive.getSpeedKmh());

        graphics.drawString(minecraft.font, "Speed:", 106, panelY + 22, 0xFFFFFF, false);
        graphics.drawString(minecraft.font, Integer.toString(speed), 106, panelY + 33, 0xFFFFFF, false);
        graphics.drawString(minecraft.font, " Km/h", 106, panelY + 44, 0xFFFFFF, false);

        graphics.drawString(minecraft.font,
                "State:" + classicState(locomotive),
                50, panelY + 80,
                0xFFFFFF, false);
    }

    /**
     * The original HUD rendered "State:" followed by the locomotive state
     * string. Leading spaces are kept here to reproduce that old formatting.
     */
    private static String classicState(SmallSteamLocomotive locomotive) {
        if (locomotive.isBoilerCritical()) {
            return " DANGER";
        }
        if (locomotive.isBoilerOverheating()) {
            return " overheat";
        }
        if (locomotive.isHandBrakeApplied()) {
            return " brake";
        }
        if (locomotive.isBoilerHot()) {
            return " hot";
        }
        return " cold";
    }
}
