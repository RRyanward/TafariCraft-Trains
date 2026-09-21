/*
 * Traincraft Heavy Steam Locomotive renderer for Minecraft 1.20.1.
 * Production visual baseline recovered from Traincraft 4.4.1_020 CE 7.1.
 * Distributed under LGPL-v3.0.
 */
package traincraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.client.model.HeavySteamLocomotiveModel;
import traincraft.entity.train.steam.heavy.HeavySteamLocomotive;

/**
 * Renders the production CE 7.1 Heavy Steam model.
 *
 * Legacy RenderEnum.locoHeavySteam used:
 *   texture prefix: heavysteam_
 *   default livery: Black
 *   model translation: (0, -0.42, 0)
 *   model rotation: none
 *   model scale: none
 *
 * The recovered production model is authored longitudinally on X with the
 * smokebox/front at negative X. The fixed 270-degree model-space correction
 * maps that front to modern Traincraft's -Z facing when train yaw is zero.
 */
public class HeavySteamLocomotiveRenderer extends EntityRenderer<HeavySteamLocomotive> {
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/heavysteam_black.png");

    private final ModelPart model;

    public HeavySteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = HeavySteamLocomotiveModel.root(
                context.bakeLayer(HeavySteamLocomotiveModel.LAYER));
        this.shadowRadius = 1.25F;
    }

    @Override
    public void render(HeavySteamLocomotive entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);

        // Preserve the working 8.1.0 front-axis conversion, then apply the
        // exact production CE 7.1 model offset. Production Heavy Steam had
        // no additional model scale and no per-model rotation.
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));
        poseStack.translate(0.0D, 0.0000D, 0.0D);

        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE_BLACK));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HeavySteamLocomotive entity) {
        return TEXTURE_BLACK;
    }
}
