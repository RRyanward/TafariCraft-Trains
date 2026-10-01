package traincraft.client.event;

// TC_BATCH8_R4A5G25H7_RESPONSIVE_BUILDER_COMPLETE_POPUP

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

/**
 * Responsive Tracks Builder completion popup.
 *
 * Unlike Minecraft's fixed-scale title renderer, this overlay continuously
 * measures the current GUI-scaled width/height. It shrinks when the game is
 * running in a small window and grows when the game is maximized, while always
 * fitting the title safely inside the visible GUI.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuilderCompletionOverlay {
    private static final long DISPLAY_MS = 5000L;
    private static final long FADE_IN_MS = 250L;
    private static final long FADE_OUT_MS = 700L;

    private static volatile long shownAtMs = -1L;
    private static volatile int completedTotal = 0;

    public static void show(int total) {
        completedTotal = Math.max(0, total);
        shownAtMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        long started = shownAtMs;
        if (started < 0L) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - started;

        if (elapsed >= DISPLAY_MS) {
            shownAtMs = -1L;
            return;
        }

        float alpha = 1.0F;

        if (elapsed < FADE_IN_MS) {
            alpha = clamp(
                    elapsed / (float) FADE_IN_MS,
                    0.0F,
                    1.0F);
        } else if (elapsed > DISPLAY_MS - FADE_OUT_MS) {
            alpha = clamp(
                    (DISPLAY_MS - elapsed)
                            / (float) FADE_OUT_MS,
                    0.0F,
                    1.0F);
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth =
                mc.getWindow().getGuiScaledWidth();
        int screenHeight =
                mc.getWindow().getGuiScaledHeight();

        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        String title = "TRACK BUILDER COMPLETE";
        String subtitle =
                completedTotal
                        + " / "
                        + completedTotal
                        + " TRACK SECTIONS PLACED";

        // Reference viewport: 427 x 240 GUI pixels. This makes the popup
        // smoothly shrink/grow with window size while keeping sane limits.
        float viewportScale =
                Math.min(
                        screenWidth / 427.0F,
                        screenHeight / 240.0F);

        float titleScale =
                clamp(
                        2.35F * viewportScale,
                        1.00F,
                        3.25F);

        float subtitleScale =
                clamp(
                        1.15F * viewportScale,
                        0.78F,
                        1.65F);

        // Width fit always wins. Even a very narrow window cannot clip text.
        float titleFit =
                (screenWidth * 0.82F)
                        / Math.max(1.0F, font.width(title));

        float subtitleFit =
                (screenWidth * 0.88F)
                        / Math.max(1.0F, font.width(subtitle));

        titleScale =
                Math.min(titleScale, titleFit);

        subtitleScale =
                Math.min(subtitleScale, subtitleFit);

        // Absolute safety floor for tiny windowed sizes.
        titleScale = Math.max(0.72F, titleScale);
        subtitleScale = Math.max(0.62F, subtitleScale);

        float titleWidth =
                font.width(title) * titleScale;

        float subtitleWidth =
                font.width(subtitle) * subtitleScale;

        float contentWidth =
                Math.max(titleWidth, subtitleWidth);

        float titleHeight =
                font.lineHeight * titleScale;

        float subtitleHeight =
                font.lineHeight * subtitleScale;

        int horizontalPadding =
                Math.max(
                        8,
                        Math.round(10.0F * viewportScale));

        int verticalPadding =
                Math.max(
                        6,
                        Math.round(7.0F * viewportScale));

        int lineGap =
                Math.max(
                        3,
                        Math.round(4.0F * viewportScale));

        int panelWidth =
                Math.min(
                        screenWidth - 8,
                        Math.round(
                                contentWidth
                                        + horizontalPadding * 2.0F));

        int panelHeight =
                Math.round(
                        titleHeight
                                + subtitleHeight
                                + lineGap
                                + verticalPadding * 2.0F);

        int panelX =
                Math.max(
                        4,
                        (screenWidth - panelWidth) / 2);

        int preferredY =
                Math.round(screenHeight * 0.24F);

        int panelY =
                Math.max(
                        6,
                        Math.min(
                                preferredY,
                                Math.max(
                                        6,
                                        screenHeight
                                                - panelHeight
                                                - 12)));

        int a = Math.round(alpha * 255.0F);
        int bgAlpha = Math.round(alpha * 185.0F);
        int borderAlpha = Math.round(alpha * 230.0F);

        int background =
                (bgAlpha << 24)
                        | 0x00101510;

        int border =
                (borderAlpha << 24)
                        | 0x0028AA4A;

        int green =
                (a << 24)
                        | 0x0055FF55;

        int gold =
                (a << 24)
                        | 0x00FFAA00;

        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                background);

        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + 2,
                border);

        graphics.fill(
                panelX,
                panelY + panelHeight - 2,
                panelX + panelWidth,
                panelY + panelHeight,
                border);

        float centerX = screenWidth * 0.5F;
        float titleY = panelY + verticalPadding;
        float subtitleY =
                titleY
                        + titleHeight
                        + lineGap;

        PoseStack pose = graphics.pose();

        pose.pushPose();
        pose.translate(
                centerX,
                titleY,
                0.0F);
        pose.scale(
                titleScale,
                titleScale,
                1.0F);

        graphics.drawString(
                font,
                title,
                -font.width(title) / 2,
                0,
                green,
                true);

        pose.popPose();

        pose.pushPose();
        pose.translate(
                centerX,
                subtitleY,
                0.0F);
        pose.scale(
                subtitleScale,
                subtitleScale,
                1.0F);

        graphics.drawString(
                font,
                subtitle,
                -font.width(subtitle) / 2,
                0,
                gold,
                true);

        pose.popPose();
    }

    private static float clamp(
            float value,
            float min,
            float max) {

        return Math.max(
                min,
                Math.min(max, value));
    }

    private BuilderCompletionOverlay() {}
}
