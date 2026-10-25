package traincraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import traincraft.client.hud.ClientInstrumentSettings;

/** Player-accessible Minecraft Options page for third-person train gauges. */
public final class TrainInstrumentSettingsScreen extends Screen {
    private final Screen parent;

    public TrainInstrumentSettingsScreen(Screen parent) {
        super(Component.literal("TafariCraft - Train Instruments"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 105;
        int y = this.height / 2 - 27;
        addRenderableWidget(Button.builder(enabledText(), button -> {
            ClientInstrumentSettings.setEnabled(!ClientInstrumentSettings.isEnabled());
            button.setMessage(enabledText());
        }).bounds(x, y, 210, 20).build());
        addRenderableWidget(Button.builder(positionText(), button -> {
            ClientInstrumentSettings.setRightSide(!ClientInstrumentSettings.isRightSide());
            button.setMessage(positionText());
        }).bounds(x, y + 26, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x + 40, y + 62, 130, 20).build());
    }

    private static Component enabledText() {
        return Component.literal("Instrument Panel: "
                + (ClientInstrumentSettings.isEnabled() ? "Enabled" : "Disabled"));
    }

    private static Component positionText() {
        return Component.literal("Position: "
                + (ClientInstrumentSettings.isRightSide() ? "Right" : "Left"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2,
                this.height / 2 - 66, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Visible only in third-person train view",
                this.width / 2, this.height / 2 - 48, 0xA0A0A0);
        graphics.drawCenteredString(this.font, "Each train's R-key GUI stays available",
                this.width / 2, this.height / 2 + 66, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
