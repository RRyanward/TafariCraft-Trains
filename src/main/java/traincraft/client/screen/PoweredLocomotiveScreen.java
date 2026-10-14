package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import traincraft.Traincraft;
import traincraft.client.TCKeyMappings;
import traincraft.menu.PoweredLocomotiveMenu;
import traincraft.network.TCNetwork;
import traincraft.network.packet.LocomotiveControlPacket;
import traincraft.network.packet.LocomotiveWhistlePacket;
import traincraft.network.packet.ToggleHandBrakePacket;

public final class PoweredLocomotiveScreen extends AbstractContainerScreen<PoweredLocomotiveMenu> {
    // TC_DEV36_R1K29R_MANUAL_TUNNEL_BUTTON
    private net.minecraft.client.gui.components.Button
            tcDev36ManualTunnelButton;
    private static final ResourceLocation BUILDER_FURNACE_OFF =
            new ResourceLocation(
                    Traincraft.MOD_ID,
                    "textures/gui/builder_furnace_indicator_off.png");

    private static final ResourceLocation BUILDER_FURNACE_ON =
            new ResourceLocation(
                    Traincraft.MOD_ID,
                    "textures/gui/builder_furnace_indicator_on.png");
    private static final ResourceLocation DIESEL=new ResourceLocation(Traincraft.MOD_ID,"textures/gui/gui_loco_diesel.png");
    private static final ResourceLocation ELECTRIC=new ResourceLocation(Traincraft.MOD_ID,"textures/gui/gui_tram.png");
    private static final ResourceLocation BUILDER=new ResourceLocation(Traincraft.MOD_ID,"textures/gui/gui_builder2.png");
    private static final ResourceLocation BUTTONS=new ResourceLocation(Traincraft.MOD_ID,"textures/gui/custombutton.png");
    private boolean forward,reverse,brake,handHeld,hornHeld;

    public PoweredLocomotiveScreen(PoweredLocomotiveMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        if(menu.getLocomotive().isBuilder()){// TC_DEV36_R1K12_FIFTH_MATERIAL_ROWS
            imageWidth=391;imageHeight=272;}else{imageWidth=176;imageHeight=166;}
    }
    @Override
    protected void init() {
        super.init();
        if (menu.getLocomotive().isBuilder()) {
            addRenderableWidget(
                    net.minecraft.client.gui.components.Button.builder(
                                    Component.literal("Restart Plan"),
                                    button -> {
                                        if (minecraft != null
                                                && minecraft.gameMode != null) {
                                            minecraft.gameMode.handleInventoryButtonClick(
                                                    menu.containerId,
                                                    90);
                                        }
                                    })
                            .bounds(leftPos + 159, topPos + 7, 88, 18)
                            .build());
        }
    
        if (menu.getLocomotive().isBuilder()) {
            tcDev36ManualTunnelButton =
                    addRenderableWidget(
                            net.minecraft.client.gui.components.Button.builder(
                                            Component.literal(
                                                    "MANUAL TUNNEL: "
                                                            + (menu.getLocomotive()
                                                                            .isBuilderManualTunnelMode()
                                                                    ? "ON"
                                                                    : "OFF")),
                                            button -> {
                                                if (minecraft != null
                                                        && minecraft.gameMode
                                                                != null) {
                                                    minecraft.gameMode
                                                            .handleInventoryButtonClick(
                                                                    menu.containerId,
                                                                    91);
                                                }
                                            })
                                    .bounds(
                                            leftPos - 132,
                                            topPos + 178,
                                            126,
                                            18)
                                    .build());
        }}

    // TC_BATCH8_R4A4E_BUILDER_TEXT_FIRE_DUALFUEL_TUBE
    private void renderBlueprintBuilderBackground(
            GuiGraphics graphics) {
        // TC_DEV36_R1K12_NO_DIESEL_UI
        // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
        int x =
                leftPos;
        int y =
                topPos;

        graphics.fill(
                x,
                y,
                x + 255,
                y + 272,
                0xFFB9BDC1);

        graphics.fill(
                x + 2,
                y + 2,
                x + 253,
                y + 270,
                0xFFDFE2E4);

        graphics.fill(
                x + 4,
                y + 4,
                x + 251,
                y + 28,
                0xFF252A31);

        graphics.fill(
                x + 4,
                y + 28,
                x + 251,
                y + 30,
                0xFF3CBCD6);

        // Top material band: equal bottom edge for both sides.
        panel(
                graphics,
                x + 6,
                y + 31,
                94,
                150);

        panel(
                graphics,
                x + 102,
                y + 31,
                147,
                150);

        // Clean continuous divider between material band and Player Inventory.
        graphics.fill(
                x + 6,
                y + 180,
                x + 249,
                y + 182,
                0xFF8B9297);

        panel(
                graphics,
                x + 43,
                y + 182,
                206,
                86);

        // Coal only. The old small Blueprint slot/frame is intentionally gone.
        slotFrame(
                graphics,
                x + 12,
                y + 58,
                0xFFCF8B3C);

        drawBuilderFire(
                graphics,
                x + 13,
                y + 37);

        // This is now the only visible Blueprint holder.
        drawBuilderBlueprintDisplay(
                graphics,
                x + 55,
                y + 35);

        // SUPPLIES: exactly 4x5.
        for (int row = 0;
             row < 4;
             row++) {
            for (int col = 0;
                 col < 5;
                 col++) {
                slotFrame(
                        graphics,
                        x + 12 + col * 18,
                        y + 92 + row * 18,
                        0xFF6F7479);
            }
        }

        // BLUEPRINT MATERIALS: full 7x8, filling the former empty lower area.
        for (int row = 0;
             row < 7;
             row++) {
            for (int col = 0;
                 col < 8;
                 col++) {
                slotFrame(
                        graphics,
                        x + 106 + col * 18,
                        y + 50 + row * 18,
                        0xFF626A70);
            }
        }

        // Player inventory + hotbar unchanged from r1k12.
        for (int row = 0;
             row < 3;
             row++) {
            for (int col = 0;
                 col < 9;
                 col++) {
                slotFrame(
                        graphics,
                        x + 47 + col * 18,
                        y + 192 + row * 18,
                        0xFF777D82);
            }
        }

        for (int col = 0;
             col < 9;
             col++) {
            slotFrame(
                    graphics,
                    x + 47 + col * 18,
                    y + 250,
                    0xFF777D82);
        }
    }

    private void drawOutlinedText(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int color) {
        int outline = 0xFF101010;

        graphics.drawString(font, text, x - 1, y - 1, outline, false);
        graphics.drawString(font, text, x,     y - 1, outline, false);
        graphics.drawString(font, text, x + 1, y - 1, outline, false);
        graphics.drawString(font, text, x - 1, y,     outline, false);
        graphics.drawString(font, text, x + 1, y,     outline, false);
        graphics.drawString(font, text, x - 1, y + 1, outline, false);
        graphics.drawString(font, text, x,     y + 1, outline, false);
        graphics.drawString(font, text, x + 1, y + 1, outline, false);

        graphics.drawString(
                font,
                text,
                x,
                y,
                color,
                false);
    }

    private void drawBuilderFire(
            GuiGraphics graphics,
            int x,
            int y) {
        // TC_BATCH8_R4A4G4_CLASSIC_TRACKS_BUILDER_FURNACE
        //
        // Original Traincraft GuiBuilder behavior:
        //   l = scaleMaxFuel(12)
        //   draw flame at y + 12 - l
        //   source V = 250 - l
        //   width = 14, height = l + 2
        //
        // We retain that exact 12-step visual math and map the modern
        // Builder's separate coal timer (1600 -> 0) onto it.
        ResourceLocation classicBuilderGui =
                new ResourceLocation(
                        Traincraft.MOD_ID,
                        "textures/gui/gui_builder2.png");

        // Original idle/off artwork from the old Tracks Builder background:
        // gray smoke/wisps at GUI source position 8,36.
        graphics.blit(
                classicBuilderGui,
                x,
                y,
                8.0F,
                36.0F,
                14,
                14,
                256,
                256);

        int remaining =
                Math.max(
                        0,
                        Math.min(
                                1600,
                                menu.getLocomotive()
                                        .getBuilderCoalBurnTicks()));

        if (remaining <= 0) {
            return;
        }

        // Exact classic Builder scaleMaxFuel(12)-style integer scaling.
        int level =
                (remaining * 12) / 1600;

        // The old GuiBuilder intentionally keeps a 2-pixel ember at the
        // bottom while fuel is still above zero.
        graphics.blit(
                classicBuilderGui,
                x,
                y + 12 - level,
                0.0F,
                (float) (250 - level),
                14,
                level + 2,
                256,
                256);
    }

    private void drawBuilderFuelTube(
            GuiGraphics graphics,
            int x,
            int y) {
        int capacity =
                Math.max(
                        1,
                        menu.getPowerCapacity());

        int amount =
                Math.max(
                        0,
                        Math.min(
                                capacity,
                                menu.getPowerAmount()));

        int innerHeight = 25;

        int filled =
                Math.max(
                        0,
                        Math.min(
                                innerHeight,
                                amount * innerHeight / capacity));

        // Black backing / steel tube frame.
        graphics.fill(
                x,
                y,
                x + 13,
                y + 33,
                0xFF111315);

        graphics.fill(
                x + 1,
                y + 1,
                x + 12,
                y + 32,
                0xFF646A6E);

        graphics.fill(
                x + 3,
                y + 4,
                x + 10,
                y + 29,
                0xFF171A1D);

        // Amber Diesel Fuel reserve rises from the bottom.
        if (filled > 0) {
            int fillTop =
                    y + 29 - filled;

            graphics.fill(
                    x + 4,
                    fillTop,
                    x + 9,
                    y + 29,
                    0xFFD88928);

            graphics.fill(
                    x + 5,
                    fillTop,
                    x + 6,
                    y + 29,
                    0xFFFFC45C);
        }

        // Metallic center highlight.
        graphics.fill(
                x + 9,
                y + 5,
                x + 10,
                y + 28,
                0xFFBFC4C7);

        // Gauge ticks on the left edge.
        for (int tick = 0; tick <= 4; tick++) {
            int ty =
                    y + 6 + tick * 5;

            graphics.fill(
                    x + 1,
                    ty,
                    x + 4,
                    ty + 1,
                    0xFF2A2E31);
        }
    }

    // TC_BATCH8_R4A4G_BLUEPRINT_DISPLAY_HELPERS
    private void drawBuilderBlueprintDisplay(
            GuiGraphics graphics,
            int x,
            int y) {
        // Outer industrial bevel, modeled after the classic Blueprint holder.
        graphics.fill(
                x,
                y,
                x + 44,
                y + 45,
                0xFF3A3F43);

        graphics.fill(
                x + 1,
                y + 1,
                x + 43,
                y + 44,
                0xFFBFC4C7);

        graphics.fill(
                x + 3,
                y + 3,
                x + 41,
                y + 43,
                0xFF767D82);

        // Small title strip behind the blue/black outlined Blueprint name.
        graphics.fill(
                x + 4,
                y + 4,
                x + 40,
                y + 12,
                0xFFD8DCDE);

        // Deep center well for the enlarged item preview.
        graphics.fill(
                x + 5,
                y + 13,
                x + 39,
                y + 42,
                0xFF30363A);

        graphics.fill(
                x + 7,
                y + 15,
                x + 37,
                y + 40,
                0xFF697177);

        // Cyan top accent ties the holder to the Blueprint system.
        graphics.fill(
                x + 4,
                y + 3,
                x + 40,
                y + 4,
                0xFF49CFE7);

        net.minecraft.world.item.ItemStack blueprint =
                menu.getSlot(52).getItem();

        if (blueprint.isEmpty()) {
            return;
        }

        // The regular slot remains the click target. This is a 2x live visual
        // copy of that exact stack so custom model data and custom names match.
        graphics.pose().pushPose();
        graphics.pose().translate(
                x + 6,
                y + 12,
                120.0F);
        graphics.pose().scale(
                2.0F,
                2.0F,
                1.0F);

        graphics.renderItem(
                blueprint,
                0,
                0);

        graphics.pose().popPose();
    }

    private void drawScaledOutlinedText(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            float scale,
            int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(
                x,
                y,
                0.0F);
        graphics.pose().scale(
                scale,
                scale,
                1.0F);

        drawOutlinedText(
                graphics,
                text,
                0,
                0,
                color);

        graphics.pose().popPose();
    }

    private void drawScaledOutlinedCenteredText(
            GuiGraphics graphics,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color) {
        if (text == null
                || text.isBlank()) {
            return;
        }

        int rawWidth =
                Math.max(
                        1,
                        font.width(text));

        float scale =
                Math.min(
                        0.72F,
                        maxWidth / (float) rawWidth);

        scale =
                Math.max(
                        0.50F,
                        scale);

        int unscaledMax =
                Math.max(
                        1,
                        (int) (maxWidth / scale));

        String fitted =
                font.plainSubstrByWidth(
                        text,
                        unscaledMax);

        int fittedWidth =
                font.width(fitted);

        graphics.pose().pushPose();
        graphics.pose().translate(
                centerX,
                y,
                0.0F);
        graphics.pose().scale(
                scale,
                scale,
                1.0F);

        drawOutlinedText(
                graphics,
                fitted,
                -fittedWidth / 2,
                0,
                color);

        graphics.pose().popPose();
    }

    private static void panel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height) {
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                0xFF8B9297);

        graphics.fill(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                0xFFCDD1D4);
    }

    private static void slotFrame(
            GuiGraphics graphics,
            int x,
            int y,
            int accent) {
        graphics.fill(
                x - 1,
                y - 1,
                x + 17,
                y + 17,
                0xFF555B60);

        graphics.fill(
                x,
                y,
                x + 16,
                y + 16,
                0xFF8F969B);

        graphics.fill(
                x + 1,
                y + 1,
                x + 15,
                y + 15,
                0xFF6F767B);

        graphics.fill(
                x - 1,
                y - 1,
                x + 17,
                y,
                accent);
    }

    // TC_DEV34_R1C1A_INFRASTRUCTURE_PANEL
    // TC_DEV34_R1C1A_INFRASTRUCTURE_PANEL
    // TC_DEV36_R1E_INFRASTRUCTURE_CENTERED
    // TC_DEV34_R1C1A_INFRASTRUCTURE_PANEL
    // TC_DEV36_R1F_INFRASTRUCTURE_LABEL_CLEARANCE
    private void renderBuilderInfrastructurePanel(
            GuiGraphics graphics) {
        int x =
                leftPos + 257;
        int y =
                topPos + 4;
        int panelWidth =
                132;
        int panelCenter =
                x + panelWidth / 2;

        graphics.fill(
                x,
                y,
                x + panelWidth,
                y + 204,
                0xFFB9BDC1);

        graphics.fill(
                x + 2,
                y + 2,
                x + panelWidth - 2,
                y + 202,
                0xFF242A31);

        graphics.drawString(
                font,
                "ROADBED / TUNNEL",
                panelCenter
                        - font.width(
                                "ROADBED / TUNNEL") / 2,
                y + 8,
                0xFF65E6FF,
                false);

        graphics.drawString(
                font,
                "4.75 BLOCK CLEAR",
                panelCenter
                        - font.width(
                                "4.75 BLOCK CLEAR") / 2,
                y + 19,
                0xFF86F58F,
                false);

        graphics.drawString(
                font,
                "ROADBED",
                panelCenter
                        - font.width(
                                "ROADBED") / 2,
                y + 35,
                0xFFFFCC72,
                false);

        String[] roadbedLabels = {
                "L3", "L2", "L1", "C", "R1", "R2", "R3"
        };
        int roadbedX =
                leftPos + 261;
        for (int i = 0;
             i < roadbedLabels.length;
             i++) {
            String label =
                    roadbedLabels[i];
            int center =
                    roadbedX
                            + i * 18
                            + 8;
            graphics.drawString(
                    font,
                    label,
                    center
                            - font.width(
                                    label) / 2,
                    y + 44,
                    0xFFD9D9D9,
                    false);
        }

        graphics.drawString(
                font,
                "WALLS",
                panelCenter
                        - font.width(
                                "WALLS") / 2,
                y + 88,
                0xFFFFCC72,
                false);

        String[] wallLabels = {
                "L", "R"
        };
        int[] wallX = {
                leftPos + 306,
                leftPos + 324
        };
        for (int i = 0;
             i < wallLabels.length;
             i++) {
            String label =
                    wallLabels[i];
            graphics.drawString(
                    font,
                    label,
                    wallX[i]
                            + 8
                            - font.width(
                                    label) / 2,
                    y + 97,
                    0xFFD9D9D9,
                    false);
        }

        graphics.drawString(
                font,
                "ROOF",
                panelCenter
                        - font.width(
                                "ROOF") / 2,
                y + 141,
                0xFFFFCC72,
                false);

        String[] roofLabels = {
                "L", "C", "R"
        };
        int roofX =
                leftPos + 297;
        for (int i = 0;
             i < roofLabels.length;
             i++) {
            String label =
                    roofLabels[i];
            int center =
                    roofX
                            + i * 18
                            + 8;
            graphics.drawString(
                    font,
                    label,
                    center
                            - font.width(
                                    label) / 2,
                    y + 150,
                    0xFFD9D9D9,
                    false);
        }

        graphics.drawString(
                font,
                "CENTER required",
                panelCenter
                        - font.width(
                                "CENTER required") / 2,
                y + 183,
                0xFFFF7777,
                false);

        graphics.drawString(
                font,
                "empty side = CENTER",
                panelCenter
                        - font.width(
                                "empty side = CENTER") / 2,
                y + 192,
                0xFFBFC5CA,
                false);

        // Actual filter frames remain exactly aligned to the accepted
        // r1e centered menu-slot coordinates.
        for (int i = 0; i < 7; i++) {
            drawInfrastructureSlotFrame(
                    graphics,
                    leftPos + 261 + i * 18,
                    topPos + 58);
        }

        drawInfrastructureSlotFrame(
                graphics,
                leftPos + 306,
                topPos + 111);

        drawInfrastructureSlotFrame(
                graphics,
                leftPos + 324,
                topPos + 111);

        for (int i = 0; i < 3; i++) {
            drawInfrastructureSlotFrame(
                    graphics,
                    leftPos + 297 + i * 18,
                    topPos + 164);
        }
    }

    private void drawInfrastructureSlotFrame(
            GuiGraphics graphics,
            int x,
            int y) {
        graphics.fill(
                x - 1,
                y - 1,
                x + 17,
                y + 17,
                0xFF777C82);

        graphics.fill(
                x,
                y,
                x + 16,
                y + 16,
                0xFF171A1F);
    }
    // TC_DEV37_R1D6_GUI_TYPE
    private static boolean tcDev37ElectricGui(traincraft.entity.train.power.LegacyPoweredLocomotive locomotive) {
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(locomotive.getType());
        return key != null && key.getPath().startsWith("locomotive_electric_");
    }
    private ResourceLocation texture(){var l=menu.getLocomotive();if(l.isBuilder())return BUILDER;return tcDev37ElectricGui(l)?ELECTRIC:DIESEL;}
    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        if (menu.getLocomotive().isBuilder()) {
            renderBlueprintBuilderBackground(graphics);
            renderBuilderInfrastructurePanel(graphics);
            return;
        }

        graphics.blit(
                texture(),
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight,
                256,
                256);

        int capacity =
                Math.max(
                        1,
                        menu.getPowerCapacity());

        int amount =
                Math.max(
                        0,
                        Math.min(
                                capacity,
                                menu.getPowerAmount()));

        int height =
                Math.max(
                        0,
                        Math.min(
                                48,
                                amount * 48 / capacity));

        graphics.fill(
                leftPos + 145,
                topPos + 67 - height,
                leftPos + 158,
                topPos + 67,
                0xFF42D85A);
    }
    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        var locomotive =
                menu.getLocomotive();

        if (locomotive.isBuilder()) {
            graphics.drawString(
                    font,
                    "TRACKS BUILDER",
                    8,
                    8,
                    0xFFFFFFFF,
                    false);

            graphics.drawString(
                    font,
                    (locomotive.isBuilderManualTunnelMode() ? "MANUAL TUNNEL" : "BLUEPRINT CONSTRUCTION"),
                    8,
                    18,
                    0xFF65E6FF,
                    false);

            // TC_BATCH8_R4A4G_BLUEPRINT_DISPLAY_LABELS
            // The old small BLUEPRINT label beside the furnace is removed.
            // The large display shows the actual Blueprint hover/custom name.
            net.minecraft.world.item.ItemStack blueprint =
                    menu.getSlot(52).getItem();

            if (!blueprint.isEmpty()) {
                drawScaledOutlinedCenteredText(
                        graphics,
                        blueprint.getHoverName().getString(),
                        77,
                        37,
                        40,
                        0xFF69C8FF);
            }

            drawOutlinedText(
                    graphics,
                    "SUPPLIES",
                    11,
                    81,
                    0xFFFFC857);

            drawOutlinedText(
                    graphics,
                    "BLUEPRINT MATERIALS",
                    106,
                    38,
                    0xFF62E7FF);

            drawScaledOutlinedText(graphics, "PLAYER INVENTORY", 47, 183, 0.70F,
                    0xFFE8E8E8);

            // TC_DEV36_R1K12_NO_DIESEL_UI

            return;
        }

        graphics.drawString(
                font,
                title,
                40,
                6,
                0xD3A900,
                false);

        if (!tcDev37ElectricGui(locomotive)) {
            graphics.drawString(
                    font,
                    "Fuel: "
                            + menu.getPowerAmount()
                            + "/"
                            + menu.getPowerCapacity(),
                    8,
                    19,
                    0x404040,
                    false);
        }
    }
    private void renderStatusPanel(GuiGraphics g){
        var l=menu.getLocomotive();if(l.isBuilder())return;
        int y=topPos-49;g.fill(leftPos,y,leftPos+imageWidth,topPos-18,0xD8202020);
        g.drawCenteredString(font,title,leftPos+imageWidth/2,y+3,0xFFE070);
        if(tcDev37ElectricGui(l)){
            boolean line=l.isOnPoweredEnergyTrack();boolean backup=menu.hasBackupRedstone();
            String status=line?"LINE POWER":backup?"BACKUP":"NO POWER";
            int color=line?0x55FF77:backup?0xFFCC55:0xFF5555;
            g.drawCenteredString(font,status,leftPos+imageWidth/2,y+13,color);
            String detail=line?"Energy Track primary | backup preserved":backup?menu.getBackupLabel()+" (consumed while moving)":"Put Redstone in left backup slot";
            g.drawCenteredString(font,detail,leftPos+imageWidth/2,y+22,0xE0E0E0);
        }else{
            g.drawCenteredString(font,"Diesel Fuel: "+menu.getPowerAmount()+" / "+menu.getPowerCapacity(),leftPos+imageWidth/2,y+17,0xE0E0E0);
        }
    }
    // TC_DEV36_R1K19_RESPONSIVE_BUILDER_SCREEN
    // Match the accepted status-overlay philosophy:
    // natural 1.0 scale when there is room, exact-fit scale only when needed.
    private float tcDev36R1k19BuilderScale() {
        // TC_DEV36_R1K29I_COMBINED_CLUSTER
        // Visible Builder width = 126 status + 6 gap + 391 main = 523.
        if (!menu.getLocomotive().isBuilder()) {
            return 1.0F;
        }

        float availableWidth =
                Math.max(
                        1.0F,
                        this.width - 8.0F);

        float availableHeight =
                Math.max(
                        1.0F,
                        this.height - 8.0F);

        float fit =
                Math.min(
                        availableWidth / 523.0F,
                        availableHeight / 272.0F);

        fit =
                Math.min(
                        1.0F,
                        fit);

        // Same principle as the already-accepted responsive side overlay:
        // never enforce a large minimum that would make a tiny window overlap.
        return Math.max(
                1.0F / 523.0F,
                fit);
    }

    // TC_DEV36_R1K29I_CLUSTER_CENTER_SHIFT
    private float tcDev36R1k29hClusterShiftX() {
        if (!menu.getLocomotive().isBuilder()) {
            return 0.0F;
        }

        // Half of the 126px status panel + 6px gap, scaled with the cluster.
        return 66.0F
                * tcDev36R1k19BuilderScale();
    }

    private double tcDev36R1k19LogicalMouseX(
            double physicalX) {
        float scale =
                tcDev36R1k19BuilderScale();

        double center =
                this.width / 2.0D;

        double shift =
                tcDev36R1k29hClusterShiftX();

        return center
                + (physicalX
                        - center
                        - shift)
                        / scale;
    }

    private double tcDev36R1k19LogicalMouseY(
            double physicalY) {
        float scale =
                tcDev36R1k19BuilderScale();

        if (scale >= 0.9999F) {
            return physicalY;
        }

        double center =
                this.height / 2.0D;

        return center
                + (physicalY - center)
                        / scale;
    }
    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (tcDev36ManualTunnelButton != null
                && menu.getLocomotive().isBuilder()) {
            tcDev36ManualTunnelButton.setMessage(
                    Component.literal(
                            "MANUAL TUNNEL: "
                                    + (menu.getLocomotive()
                                                    .isBuilderManualTunnelMode()
                                            ? "ON"
                                            : "OFF")));
        }
        if (!menu.getLocomotive().isBuilder()) {
            renderBackground(
                    graphics);
            super.render(
                    graphics,
                    mouseX,
                    mouseY,
                    partialTick);
            renderBrake(
                    graphics);
            renderTooltip(
                    graphics,
                    mouseX,
                    mouseY);
            return;
        }

        renderBackground(
                graphics);

        float scale =
                tcDev36R1k19BuilderScale();

        int logicalMouseX =
                (int) Math.floor(
                        tcDev36R1k19LogicalMouseX(
                                mouseX));

        int logicalMouseY =
                (int) Math.floor(
                        tcDev36R1k19LogicalMouseY(
                                mouseY));

        graphics.pose()
                .pushPose();

        graphics.pose()
                .translate(
                        this.width / 2.0F
                                + tcDev36R1k29hClusterShiftX(),
                        this.height / 2.0F,
                        0.0F);

        graphics.pose()
                .scale(
                        scale,
                        scale,
                        1.0F);

        graphics.pose()
                .translate(
                        -this.width / 2.0F,
                        -this.height / 2.0F,
                        0.0F);

        super.render(
                graphics,
                logicalMouseX,
                logicalMouseY,
                partialTick);

        renderBrake(
                graphics);

        renderTooltip(
                graphics,
                logicalMouseX,
                logicalMouseY);

        graphics.pose()
                .popPose();
    }
    private void renderBrake(
            GuiGraphics graphics) {
        // TC_BATCH8_R4A4G2_BUILDER_NO_TOP_SWITCH
        // Tracks Builder uses Shift + right-click for autonomous START/STOP,
        // so the locomotive handbrake switch is intentionally hidden here.
        if (menu.getLocomotive().isBuilder()) {
            return;
        }

        boolean on =
                menu.getLocomotive()
                        .isHandBrakeApplied();

        int x =
                leftPos
                        + (on ? 0 : 31);

        graphics.blit(
                BUTTONS,
                x,
                topPos - 13,
                on ? 0 : 41,
                13,
                40,
                13,
                256,
                256);
    }
        // TC_BATCH8_R4A5G4_BLUEPRINT_BOX_TOOLTIP
    // Slot 52 is the real Blueprint inventory slot but is hidden off-screen.
    // Make the large visible box behave like a normal inventory slot for hover
    // help by rendering the exact ItemStack tooltip from slot 52.
    private void renderBuilderBlueprintBoxTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        if (!menu.getLocomotive().isBuilder()) {
            return;
        }

        if (mouseX < leftPos + 55
                || mouseX >= leftPos + 99
                || mouseY < topPos + 35
                || mouseY >= topPos + 80) {
            return;
        }

        net.minecraft.world.item.ItemStack blueprint =
                menu.getSlot(52).getItem();

        if (blueprint.isEmpty()) {
            return;
        }

        graphics.renderTooltip(
                font,
                blueprint,
                mouseX,
                mouseY);
    }
@Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button) {
        if (menu.getLocomotive().isBuilder()) {
            double logicalX =
                    tcDev36R1k19LogicalMouseX(
                            mouseX);

            double logicalY =
                    tcDev36R1k19LogicalMouseY(
                            mouseY);

            // TC_BATCH8_R4A4G2_LARGE_BLUEPRINT_CLICK_TARGET
            // TC_DEV36_R1K14_LARGE_BLUEPRINT_HOLDER
            // TC_DEV36_R1K19_RESPONSIVE_BUILDER_SCREEN
            if ((button == 0
                            || button == 1)
                    && logicalX >= leftPos + 55
                    && logicalX < leftPos + 99
                    && logicalY >= topPos + 35
                    && logicalY < topPos + 80) {
                net.minecraft.world.inventory.ClickType type =
                        hasShiftDown()
                                ? net.minecraft.world.inventory.ClickType.QUICK_MOVE
                                : net.minecraft.world.inventory.ClickType.PICKUP;

                slotClicked(
                        menu.getSlot(52),
                        52,
                        button,
                        type);
                return true;
            }

            return super.mouseClicked(
                    logicalX,
                    logicalY,
                    button);
        }

        boolean on =
                menu.getLocomotive()
                        .isHandBrakeApplied();

        int bx =
                leftPos
                        + (on ? 0 : 31);

        if (button == 0
                && mouseX >= bx
                && mouseX < bx + 40
                && mouseY >= topPos - 13
                && mouseY < topPos) {
            TCNetwork.CHANNEL.sendToServer(
                    new ToggleHandBrakePacket(
                            menu.getLocomotive().getId()));
            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button);
    }
    // TC_DEV36_R1K19_RESPONSIVE_BUILDER_INPUT
    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button) {
        if (menu.getLocomotive().isBuilder()) {
            return super.mouseReleased(
                    tcDev36R1k19LogicalMouseX(
                            mouseX),
                    tcDev36R1k19LogicalMouseY(
                            mouseY),
                    button);
        }

        return super.mouseReleased(
                mouseX,
                mouseY,
                button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (menu.getLocomotive().isBuilder()) {
            float scale =
                    tcDev36R1k19BuilderScale();

            return super.mouseDragged(
                    tcDev36R1k19LogicalMouseX(
                            mouseX),
                    tcDev36R1k19LogicalMouseY(
                            mouseY),
                    button,
                    dragX / scale,
                    dragY / scale);
        }

        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta) {
        if (menu.getLocomotive().isBuilder()) {
            return super.mouseScrolled(
                    tcDev36R1k19LogicalMouseX(
                            mouseX),
                    tcDev36R1k19LogicalMouseY(
                            mouseY),
                    delta);
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                delta);
    }
    private void sendControls(){TCNetwork.CHANNEL.sendToServer(new LocomotiveControlPacket(menu.getLocomotive().getId(),forward,reverse,brake));}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(minecraft!=null){
            if(minecraft.options.keyUp.matches(key,scan)){if(!forward){forward=true;sendControls();}return true;}
            if(minecraft.options.keyDown.matches(key,scan)){if(!reverse){reverse=true;sendControls();}return true;}
            if(minecraft.options.keyJump.matches(key,scan)){if(!brake){brake=true;sendControls();}return true;}
            if(TCKeyMappings.HAND_BRAKE.matches(key,scan)){if(!handHeld){handHeld=true;TCNetwork.CHANNEL.sendToServer(new ToggleHandBrakePacket(menu.getLocomotive().getId()));}return true;}
            if(TCKeyMappings.WHISTLE.matches(key,scan)){if(!hornHeld){hornHeld=true;TCNetwork.CHANNEL.sendToServer(new LocomotiveWhistlePacket(menu.getLocomotive().getId()));}return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){
        if(minecraft!=null){
            if(minecraft.options.keyUp.matches(key,scan)){if(forward){forward=false;sendControls();}return true;}
            if(minecraft.options.keyDown.matches(key,scan)){if(reverse){reverse=false;sendControls();}return true;}
            if(minecraft.options.keyJump.matches(key,scan)){if(brake){brake=false;sendControls();}return true;}
            if(TCKeyMappings.HAND_BRAKE.matches(key,scan)){handHeld=false;return true;}
            if(TCKeyMappings.WHISTLE.matches(key,scan)){hornHeld=false;return true;}
        }
        return super.keyReleased(key,scan,modifiers);
    }
}
