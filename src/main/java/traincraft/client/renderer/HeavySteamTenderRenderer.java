/*
 * Traincraft Heavy Steam Tender renderer for Minecraft 1.20.1.
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
import traincraft.client.model.HeavySteamTenderModel;
import traincraft.entity.train.tender.HeavySteamTender;

/**
 * Renders the production CE 7.1 Heavy Steam tender model.
 *
 * Legacy RenderEnum.tenderHeavy used:
 *   texture prefix: heavytender_
 *   default livery: Black
 *   model translation: (0, -0.40, 0)
 *   model rotation: (0, 180, 0)
 *   model scale: none
 *
 * The extra 180-degree Y correction is intentionally preserved so the
 * tender's locomotive-facing end matches the original Traincraft model.
 */
public class HeavySteamTenderRenderer extends EntityRenderer<HeavySteamTender> {
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/heavytender_black.png");

    private final ModelPart model;

    public HeavySteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = HeavySteamTenderModel.root(
                context.bakeLayer(HeavySteamTenderModel.LAYER));
        this.shadowRadius = 0.9F;
    }

    @Override
    public void render(HeavySteamTender entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);

        // Same X-to-Z conversion used by the locomotive, plus the exact
        // production tender's 180-degree Y model rotation.
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - trainYaw));
        poseStack.translate(0.0D, -0.40D, 0.0D);

        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE_BLACK));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HeavySteamTender entity) {
        return TEXTURE_BLACK;
    }
}
