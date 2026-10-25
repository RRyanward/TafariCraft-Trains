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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

public final class TraincraftLocomotiveHud {
    private static final ResourceLocation STEAM_HUD =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/loco_hud_steam.png");
    // TC_POWERED_THIRD_PERSON_HUD_R2
    private static final ResourceLocation POWERED_HUD =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/locohud.png");

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
        // G12R4R2: Instruments remain an optional third-person overlay.
        // First-person drivers use existing R-key locomotive GUIs.
        if (minecraft.options.getCameraType().isFirstPerson()
                || !ClientInstrumentSettings.isEnabled()) {
            return;
        }
        var tcVehicle = minecraft.player.getVehicle();

        // TC_POWERED_THIRD_PERSON_HUD_R2
        if (tcVehicle instanceof LegacyPoweredLocomotive powered) {
            if (!powered.isBuilder()) {
                gui.setupOverlayRenderState(true, false);
                drawClassicPoweredHud(
                        graphics,
                        minecraft,
                        powered,
                        ClientInstrumentSettings.isRightSide()
                                ? Math.max(0, screenWidth - 156 - 10) : 10,
                        screenHeight - 128);
                drawLightMode(graphics, minecraft, powered,
                        ClientInstrumentSettings.isRightSide()
                                ? Math.max(0, screenWidth - 156 - 10) : 10,
                        screenHeight - 28);
            }
            return;
        }

        if (!(tcVehicle instanceof SmallSteamLocomotive locomotive)) {
            return;
        }

        gui.setupOverlayRenderState(true, false);

        // Traincraft 1.7.10 HUDloco used a fixed left offset of 10 and
        // windowHeight = scaledHeight - 100.
        // TC_DEV37_R1D8_HUD_FAMILY - clean steam third-person cab panel.
        int panelX = ClientInstrumentSettings.isRightSide()
                ? Math.max(0, screenWidth - 174 - 10) : 10;
        int panelY = screenHeight - 135;
        drawSteamThirdPersonHud(graphics, minecraft, locomotive, panelX, panelY);
        drawLightMode(graphics, minecraft, locomotive, panelX, screenHeight - 28);
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
    // TC_DEV37_R1D7_DIESEL_HUD
    // Diesel now has its own industrial control-stand style third-person HUD.
    // Electric deliberately keeps the classic non-steam Traincraft locohud.png.
    // TC_DEV37_R1D8_HUD_FAMILY
    // Diesel remains the accepted r1d7 industrial control stand.
    // Electric now gets its own clean traction-control desk.
    private static void drawClassicPoweredHud(
            GuiGraphics graphics,
            Minecraft minecraft,
            LegacyPoweredLocomotive locomotive,
            int panelX,
            int panelY) {
        if (locomotive.isDiesel()) {
            drawDieselThirdPersonHud(graphics, minecraft, locomotive, panelX, panelY);
            return;
        }
        drawElectricThirdPersonHud(graphics, minecraft, locomotive, panelX, panelY);
    }

    private static void drawElectricThirdPersonHud(
            GuiGraphics graphics,
            Minecraft minecraft,
            LegacyPoweredLocomotive locomotive,
            int panelX,
            int panelY) {
        final int width = 156;
        final int height = 88;

        // Shadow + dark traction desk.
        graphics.fill(panelX + 3, panelY + 3, panelX + width + 3, panelY + height + 3, 0x66000000);
        graphics.fill(panelX, panelY, panelX + width, panelY + height, 0xE010171C);
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + height - 2, 0xE019252C);

        // Electric blue header and lower bus-bar trim.
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + 14, 0xFF47B6D6);
        graphics.fill(panelX + 2, panelY + height - 5, panelX + width - 2, panelY + height - 2, 0xFF246A80);
        graphics.drawString(minecraft.font, "ELECTRIC CONTROL DESK", panelX + 8, panelY + 4, 0xFF0A171B, false);

        // Panel fasteners.
        graphics.fill(panelX + 4, panelY + 18, panelX + 6, panelY + 20, 0xFF83949C);
        graphics.fill(panelX + width - 6, panelY + 18, panelX + width - 4, panelY + 20, 0xFF83949C);
        graphics.fill(panelX + 4, panelY + height - 10, panelX + 6, panelY + height - 8, 0xFF83949C);
        graphics.fill(panelX + width - 6, panelY + height - 10, panelX + width - 4, panelY + height - 8, 0xFF83949C);

        // Keep r1d8 HUD display-only: use the already-public line-power query
        // from the frozen powered backend. Do not add a new backend/HUD-sync API.
        boolean linePower = locomotive.isOnPoweredEnergyTrack();
        String source = linePower ? "LINE POWER" : "NO POWER";
        int sourceColor = linePower ? 0xFF55D985 : 0xFFD85B52;

        // Source annunciator.
        int sourceX = panelX + 10;
        graphics.drawString(minecraft.font, "SUPPLY", sourceX, panelY + 20, 0xFFBECBD0, false);
        graphics.fill(sourceX, panelY + 32, sourceX + 45, panelY + 53, 0xFF080C0E);
        graphics.fill(sourceX + 2, panelY + 34, sourceX + 43, panelY + 51, sourceColor);
        graphics.drawString(minecraft.font, source, sourceX + 4, panelY + 38, 0xFFFFFFFF, false);

        // Digital speed display.
        int speed = (int)Math.abs(locomotive.getSpeedKmh());
        int speedX = panelX + 64;
        graphics.drawString(minecraft.font, "SPEED", speedX, panelY + 20, 0xFFBECBD0, false);
        graphics.fill(speedX - 2, panelY + 31, speedX + 50, panelY + 57, 0xFF06090B);
        graphics.fill(speedX, panelY + 33, speedX + 48, panelY + 55, 0xFF0E161A);
        graphics.drawString(minecraft.font, Integer.toString(speed), speedX + 5, panelY + 37, 0xFF7FDBF2, false);
        graphics.drawString(minecraft.font, "KM/H", speedX + 25, panelY + 42, 0xFFC9D6DA, false);

        // Brake status lamp.
        int brakeX = panelX + 121;
        graphics.drawString(minecraft.font, "BRAKE", brakeX, panelY + 20, 0xFFBECBD0, false);
        int brakeLamp = locomotive.isHandBrakeApplied() ? 0xFFC7473D : 0xFF43A563;
        graphics.fill(brakeX, panelY + 33, brakeX + 27, panelY + 49, 0xFF080B0D);
        graphics.fill(brakeX + 2, panelY + 35, brakeX + 25, panelY + 47, brakeLamp);
        graphics.drawString(
                minecraft.font,
                locomotive.isHandBrakeApplied() ? "SET" : "OFF",
                brakeX + 4,
                panelY + 37,
                0xFFFFFFFF,
                false);

        graphics.drawString(minecraft.font, "TRACTION SUPPLY", panelX + 10, panelY + 65, 0xFF98AAB1, false);
        graphics.drawString(minecraft.font, source, panelX + 96, panelY + 65, sourceColor, false);
    }
    private static void drawDieselThirdPersonHud(
            GuiGraphics graphics,
            Minecraft minecraft,
            LegacyPoweredLocomotive locomotive,
            int panelX,
            int panelY) {
        final int width = 156;
        final int height = 88;

        // Drop shadow + graphite steel control stand.
        graphics.fill(panelX + 3, panelY + 3, panelX + width + 3, panelY + height + 3, 0x66000000);
        graphics.fill(panelX, panelY, panelX + width, panelY + height, 0xE0191C1F);
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + height - 2, 0xE02A2E32);

        // Diesel hazard/industrial header and lower trim.
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + 14, 0xFFE09A22);
        graphics.fill(panelX + 2, panelY + height - 5, panelX + width - 2, panelY + height - 2, 0xFF835A18);
        graphics.drawString(minecraft.font, "DIESEL CONTROL STAND", panelX + 8, panelY + 4, 0xFF171717, false);

        // Four small bolt heads make the panel read as a metal cab instrument.
        graphics.fill(panelX + 4, panelY + 18, panelX + 6, panelY + 20, 0xFF8D9498);
        graphics.fill(panelX + width - 6, panelY + 18, panelX + width - 4, panelY + 20, 0xFF8D9498);
        graphics.fill(panelX + 4, panelY + height - 10, panelX + 6, panelY + height - 8, 0xFF8D9498);
        graphics.fill(panelX + width - 6, panelY + height - 10, panelX + width - 4, panelY + height - 8, 0xFF8D9498);

        int cap = Math.max(1, locomotive.getPowerCapacity());
        int amount = Mth.clamp(locomotive.getPowerAmount(), 0, cap);
        int fuelPercent = Mth.clamp((int)((long)amount * 100L / cap), 0, 100);
        int fuelPixels = Mth.clamp((int)((long)fuelPercent * 44L / 100L), 0, 44);

        // Fuel sight glass.
        int fuelX = panelX + 12;
        int fuelY = panelY + 27;
        graphics.drawString(minecraft.font, "FUEL", fuelX - 1, panelY + 18, 0xFFE8E8E8, false);
        graphics.fill(fuelX, fuelY, fuelX + 18, fuelY + 48, 0xFF0D0F10);
        graphics.fill(fuelX + 2, fuelY + 2, fuelX + 16, fuelY + 46, 0xFF3C4145);
        if (fuelPixels > 0) {
            graphics.fill(
                    fuelX + 3,
                    fuelY + 45 - fuelPixels,
                    fuelX + 15,
                    fuelY + 45,
                    0xFFD89420);
        }
        graphics.drawString(minecraft.font, fuelPercent + "%", fuelX - 1, fuelY + 51, 0xFFFFFFFF, false);

        // Digital speed block, deliberately unlike the Electric analog plate.
        int speed = (int)Math.abs(locomotive.getSpeedKmh());
        int speedX = panelX + 44;
        graphics.drawString(minecraft.font, "SPEED", speedX, panelY + 20, 0xFFBFC5C8, false);
        graphics.fill(speedX - 2, panelY + 31, speedX + 61, panelY + 57, 0xFF0A0C0D);
        graphics.fill(speedX, panelY + 33, speedX + 59, panelY + 55, 0xFF15191B);
        graphics.drawString(minecraft.font, Integer.toString(speed), speedX + 5, panelY + 37, 0xFFF4B13B, false);
        graphics.drawString(minecraft.font, "KM/H", speedX + 31, panelY + 42, 0xFFC5C9CB, false);

        // Status annunciator / brake lamp.
        int statusX = panelX + 113;
        graphics.drawString(minecraft.font, "BRAKE", statusX, panelY + 20, 0xFFBFC5C8, false);
        int lamp = locomotive.isHandBrakeApplied() ? 0xFFB83A2E : 0xFF4D8D45;
        graphics.fill(statusX, panelY + 33, statusX + 30, panelY + 49, 0xFF0C0D0E);
        graphics.fill(statusX + 2, panelY + 35, statusX + 28, panelY + 47, lamp);
        graphics.drawString(
                minecraft.font,
                locomotive.isHandBrakeApplied() ? "SET" : "OFF",
                statusX + 5,
                panelY + 37,
                0xFFFFFFFF,
                false);

        graphics.drawString(minecraft.font, "FUEL SYSTEM", panelX + 44, panelY + 64, 0xFF9EA5A8, false);
        graphics.drawString(minecraft.font, "DIESEL", panelX + 113, panelY + 64, 0xFFF4B13B, false);
    }
    private static void drawSteamThirdPersonHud(
            GuiGraphics graphics,
            Minecraft minecraft,
            SmallSteamLocomotive locomotive,
            int panelX,
            int panelY) {
        final int width = 174;
        final int height = 96;

        // Shadow + dark riveted steam-cab panel.
        graphics.fill(panelX + 3, panelY + 3, panelX + width + 3, panelY + height + 3, 0x66000000);
        graphics.fill(panelX, panelY, panelX + width, panelY + height, 0xE0171513);
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + height - 2, 0xE02B2722);

        // Warm brass/copper header.
        graphics.fill(panelX + 2, panelY + 2, panelX + width - 2, panelY + 14, 0xFFC68138);
        graphics.fill(panelX + 2, panelY + height - 5, panelX + width - 2, panelY + height - 2, 0xFF74451F);
        graphics.drawString(minecraft.font, "STEAM CAB INSTRUMENTS", panelX + 8, panelY + 4, 0xFF21130A, false);

        // Rivets.
        graphics.fill(panelX + 4, panelY + 18, panelX + 6, panelY + 20, 0xFF968B7E);
        graphics.fill(panelX + width - 6, panelY + 18, panelX + width - 4, panelY + 20, 0xFF968B7E);
        graphics.fill(panelX + 4, panelY + height - 10, panelX + 6, panelY + height - 8, 0xFF968B7E);
        graphics.fill(panelX + width - 6, panelY + height - 10, panelX + width - 4, panelY + height - 8, 0xFF968B7E);

        int burn = Math.max(0, locomotive.getBurnTime());
        int maxBurn = Math.max(1, locomotive.getMaxBurnTime());
        int fuelPercent = Mth.clamp((int)((long)burn * 100L / maxBurn), 0, 100);
        int waterPercent = Mth.clamp(
                (int)((long)locomotive.getWaterAmount() * 100L / SmallSteamLocomotive.WATER_CAPACITY_MB),
                0,
                100);
        int heatPercent = Mth.clamp(locomotive.getBoilerHeatPercent(), 0, 100);

        drawSteamBar(graphics, minecraft, "FUEL", panelX + 10, panelY + 20, fuelPercent, 0xFFD4953F);
        drawSteamBar(graphics, minecraft, "WATER", panelX + 37, panelY + 20, waterPercent, 0xFF4CAAD8);
        drawSteamBar(graphics, minecraft, "HEAT", panelX + 67, panelY + 20, heatPercent, 0xFFD85B45);

        // Digital speed block.
        int speed = (int)Math.abs(locomotive.getSpeedKmh());
        int speedX = panelX + 99;
        graphics.drawString(minecraft.font, "SPEED", speedX, panelY + 20, 0xFFD5CEC4, false);
        graphics.fill(speedX - 2, panelY + 31, speedX + 47, panelY + 57, 0xFF090806);
        graphics.fill(speedX, panelY + 33, speedX + 45, panelY + 55, 0xFF17130F);
        graphics.drawString(minecraft.font, Integer.toString(speed), speedX + 5, panelY + 37, 0xFFFFC765, false);
        graphics.drawString(minecraft.font, "KM/H", speedX + 23, panelY + 42, 0xFFD6CEC3, false);

        // Brake lamp.
        int brakeX = panelX + 149;
        graphics.drawString(minecraft.font, "BRK", brakeX, panelY + 20, 0xFFD5CEC4, false);
        int lamp = locomotive.isHandBrakeApplied() ? 0xFFB94338 : 0xFF4B9856;
        graphics.fill(brakeX, panelY + 33, brakeX + 17, panelY + 49, 0xFF090806);
        graphics.fill(brakeX + 2, panelY + 35, brakeX + 15, panelY + 47, lamp);

        int tempC = (int)Math.round(locomotive.getBoilerTemperatureC());
        graphics.drawString(minecraft.font, "BOILER " + tempC + " C", panelX + 99, panelY + 64, 0xFFE4D6C5, false);
        graphics.drawString(
                minecraft.font,
                locomotive.isHandBrakeApplied() ? "HAND BRAKE" : classicState(locomotive),
                panelX + 99,
                panelY + 76,
                locomotive.isHandBrakeApplied() ? 0xFFFF8A73 : 0xFFC7B9A7,
                false);
    }

    private static void drawSteamBar(
            GuiGraphics graphics,
            Minecraft minecraft,
            String label,
            int x,
            int y,
            int percent,
            int fillColor) {
        int p = Mth.clamp(percent, 0, 100);
        int fillPixels = Mth.clamp((int)((long)p * 42L / 100L), 0, 42);
        graphics.drawString(minecraft.font, label, x - 1, y, 0xFFD5CEC4, false);
        graphics.fill(x, y + 11, x + 16, y + 57, 0xFF090806);
        graphics.fill(x + 2, y + 13, x + 14, y + 55, 0xFF4A4238);
        if(fillPixels > 0){
            graphics.fill(x + 3, y + 54 - fillPixels, x + 13, y + 54, fillColor);
        }
        graphics.drawString(minecraft.font, p + "%", x - 1, y + 60, 0xFFFFFFFF, false);
    }
    // The standard third-person play view has a captured mouse pointer, so
    // a registered L key changes this displayed mode without opening a screen.
    private static void drawLightMode(GuiGraphics graphics, Minecraft minecraft,
            net.minecraft.world.entity.Entity locomotive, int x, int y) {
        graphics.fill(x,y,x+156,y+18,0xD0181E23);
        String mode=traincraft.lighting.LocomotiveLightMode.label(locomotive);
        int shade=mode.equals("OFF")?0xFFE68C8C:mode.equals("ON")?0xFF9DE8AC:0xFFDAE8FE;
        graphics.drawString(minecraft.font,"LIGHTS: "+mode+"   [L] Cycle",x+7,y+5,shade,false);
    }

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
