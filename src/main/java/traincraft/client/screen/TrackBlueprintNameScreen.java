package traincraft.client.screen;

// TC_BATCH8_R3ZU_BLUEPRINT_NAMING_POPUP_SCREEN

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import traincraft.network.TrackBlueprintSelectionNetwork;

public final class TrackBlueprintNameScreen
        extends Screen {
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 104;

    private final String initialName;
    private EditBox nameBox;

    public TrackBlueprintNameScreen(
            String initialName) {
        super(Component.literal("Name Blueprint"));
        this.initialName =
                initialName == null
                        || initialName.isBlank()
                        ? "Blueprint"
                        : initialName;
    }

    @Override
    protected void init() {
        int fieldX =
                (this.width - FIELD_WIDTH) / 2;
        int fieldY =
                this.height / 2 - 14;

        this.nameBox =
                new EditBox(
                        this.font,
                        fieldX,
                        fieldY,
                        FIELD_WIDTH,
                        FIELD_HEIGHT,
                        Component.literal("Blueprint name"));

        this.nameBox.setMaxLength(40);
        this.nameBox.setValue(this.initialName);
        this.nameBox.setFocused(true);
        this.nameBox.setHighlightPos(
                this.nameBox.getValue().length());

        this.addRenderableWidget(
                this.nameBox);

        int buttonY =
                fieldY + 34;

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("Save Name"),
                                button -> saveAndClose())
                        .bounds(
                                this.width / 2
                                        - BUTTON_WIDTH
                                        - 4,
                                buttonY,
                                BUTTON_WIDTH,
                                20)
                        .build());

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("Keep Blueprint"),
                                button -> keepDefaultAndClose())
                        .bounds(
                                this.width / 2 + 4,
                                buttonY,
                                BUTTON_WIDTH,
                                20)
                        .build());

        this.setInitialFocus(
                this.nameBox);
    }

    private void saveAndClose() {
        TrackBlueprintSelectionNetwork.sendRename(
                this.nameBox == null
                        ? ""
                        : this.nameBox.getValue());

        this.onClose();
    }

    private void keepDefaultAndClose() {
        TrackBlueprintSelectionNetwork.sendRename(
                "Blueprint");

        this.onClose();
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers) {
        if (keyCode
                        == InputConstants.KEY_RETURN
                || keyCode
                        == InputConstants.KEY_NUMPADENTER) {
            saveAndClose();
            return true;
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                this.height / 2 - 54,
                0x55FFFF);

        graphics.drawCenteredString(
                this.font,
                "Give this completed track plan a name.",
                this.width / 2,
                this.height / 2 - 38,
                0xD0D0D0);

        graphics.drawCenteredString(
                this.font,
                "Leave it as Blueprint to use the default cyan name.",
                this.width / 2,
                this.height / 2 + 48,
                0x8A8A8A);

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
