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
        if(menu.getLocomotive().isBuilder()){imageWidth=255;imageHeight=254;}else{imageWidth=176;imageHeight=166;}
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
    }

    // TC_BATCH8_R4A4E_BUILDER_TEXT_FIRE_DUALFUEL_TUBE
    private void renderBlueprintBuilderBackground(
            GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;

        // Main Builder shell.
        graphics.fill(
                x,
                y,
                x + 255,
                y + 254,
                0xFFB9BDC1);

        graphics.fill(
                x + 2,
                y + 2,
                x + 253,
                y + 252,
                0xFFDFE2E4);

        // Header.
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

        // Core / supplies panel.
        panel(
                graphics,
                x + 6,
                y + 31,
                94,
                132);

        // Blueprint material rack panel.
        panel(
                graphics,
                x + 102,
                y + 31,
                147,
                132);

        // Player inventory panel.
        panel(
                graphics,
                x + 43,
                y + 164,
                206,
                86);

        // Shared physical fuel input + real Blueprint insertion slot.
        slotFrame(
                graphics,
                x + 12,
                y + 58,
                0xFFCF8B3C);

        // TC_BATCH8_R4A4G2_OLD_BLUEPRINT_SLOT_VISUAL_REMOVED

        // Coal furnace indicator above slot 0.
        drawBuilderFire(
                graphics,
                x + 13,
                y + 37);

        // TC_BATCH8_R4A4G_LARGE_BLUEPRINT_DISPLAY
        // Live large display of the same stack held by handler slot 52.
        drawBuilderBlueprintDisplay(
                graphics,
                x + 55,
                y + 35);

        // Supplies: handler slots 1..19.
        int supplySlot = 1;
        for (int row = 0; row < 4; row++) {
            for (int col = 0;
                 col < 5 && supplySlot <= 19;
                 col++) {
                slotFrame(
                        graphics,
                        x + 12 + col * 18,
                        y + 92 + row * 18,
                        0xFF6F7479);
                supplySlot++;
            }
        }

        // Blueprint material rack: handler slots 20..51.
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 8; col++) {
                slotFrame(
                        graphics,
                        x + 106 + col * 18,
                        y + 50 + row * 18,
                        0xFF626A70);
            }
        }

        // Bottom-right DIESEL tube. Coal never feeds this reserve.
        drawBuilderFuelTube(
                graphics,
                x + 232,
                y + 127);

        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(
                        graphics,
                        x + 47 + col * 18,
                        y + 174 + row * 18,
                        0xFF777D82);
            }
        }

        for (int col = 0; col < 9; col++) {
            slotFrame(
                    graphics,
                    x + 47 + col * 18,
                    y + 232,
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

    private ResourceLocation texture(){var l=menu.getLocomotive();if(l.isBuilder())return BUILDER;return l.isElectric()?ELECTRIC:DIESEL;}
    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        if (menu.getLocomotive().isBuilder()) {
            renderBlueprintBuilderBackground(graphics);
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
                    "BLUEPRINT CONSTRUCTION",
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

            drawScaledOutlinedText(graphics, "PLAYER INVENTORY", 47, 167, 0.70F,
                    0xFFE8E8E8);

            drawOutlinedText(
                    graphics,
                    "DIESEL",
                    194,
                    143,
                    0xFFFFB347);

            return;
        }

        graphics.drawString(
                font,
                title,
                40,
                6,
                0xD3A900,
                false);

        if (!locomotive.isElectric()) {
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
        if(l.isElectric()){
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
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderStatusPanel(g);renderBrake(g);renderTooltip(g,mx,my);
        renderBuilderBlueprintBoxTooltip(g, mx, my);
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
            // TC_BATCH8_R4A4G2_LARGE_BLUEPRINT_CLICK_TARGET
            // Large display: x=55..99, y=35..80 relative to GUI origin.
            if ((button == 0 || button == 1)
                    && mouseX >= leftPos + 55
                    && mouseX < leftPos + 99
                    && mouseY >= topPos + 35
                    && mouseY < topPos + 80) {
                slotClicked(
                        menu.getSlot(52),
                        52,
                        button,
                        net.minecraft.world.inventory.ClickType.PICKUP);
                return true;
            }

            return super.mouseClicked(
                    mouseX,
                    mouseY,
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
