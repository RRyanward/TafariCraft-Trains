package traincraft.client.renderer.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/**
 * Temporary calibration wrapper used only during Final Cleanup.
 *
 * It deliberately does NOT replace a locomotive's original renderer, model,
 * texture, scale, rotation, translation, or visual recipe. It delegates every
 * visual decision to the original renderer and adds only the live calibration
 * Y delta outside that renderer.
 *
 * This is the safe path for models whose pre-r1f10 visuals were correct but
 * whose original renderer did not call getPoweredModelLift().
 */
public final class PoweredCalibrationOffsetRenderer
        extends EntityRenderer<LegacyPoweredLocomotive> {

    private final EntityRenderer<LegacyPoweredLocomotive> delegate;
    private int lastDebugTick = Integer.MIN_VALUE;

    public PoweredCalibrationOffsetRenderer(
            EntityRendererProvider.Context context,
            EntityRenderer<LegacyPoweredLocomotive> delegate) {
        super(context);
        this.delegate = delegate;
    }

    @Override
    public void render(
            LegacyPoweredLocomotive entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {

        final double adjustment = entity.tcCalibrationHeightAdjustment();

        poseStack.pushPose();
        poseStack.translate(0.0D, adjustment, 0.0D);

        if (entity.tcIsCalibrationSelected()
                && entity.tickCount != lastDebugTick
                && entity.tickCount % 20 == 0) {
            lastDebugTick = entity.tickCount;
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationVisualWrapper").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-VISUAL-WRAP] variant=%s id=%d tick=%d adjustment=%.4f delegate=%s",
                            entity.variantId(),
                            entity.getId(),
                            entity.tickCount,
                            adjustment,
                            delegate.getClass().getName()));
        }

        delegate.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LegacyPoweredLocomotive entity) {
        return delegate.getTextureLocation(entity);
    }
}