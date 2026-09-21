/*
 * Traincraft USSR 0-5-0 Tender renderer for Minecraft 1.20.1.
 * Production visual transform recovered from Traincraft 4.4.1_020 CE 7.1.
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
import traincraft.client.model.UssrSteamTenderModel;
import traincraft.entity.train.tender.UssrSteamTender;

/**
 * Original CE 7.1 RenderEnum.tenderEr_Ussr:
 *   model: ModelTenderEr_Ussr
 *   texture: tenderEr_Ussr.png
 *   translation: (0.06, -0.44, 0)
 *   model rotation: (0, 180, 0)
 *   extra scale: none
 *
 * 90 - trainYaw is the legacy X-axis conversion combined with the tender's
 * original 180-degree Y correction.
 */
public class UssrSteamTenderRenderer extends EntityRenderer<UssrSteamTender> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/tenderer_ussr.png");

    private final ModelPart model;

    public UssrSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = UssrSteamTenderModel.root(
                context.bakeLayer(UssrSteamTenderModel.LAYER));
        this.shadowRadius = 0.95F;
    }

    @Override
    public void render(UssrSteamTender entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - trainYaw));

        // Exact production CE 7.1 tender translation.
        poseStack.translate(0.06D, -0.44D, 0.0D);

        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(UssrSteamTender entity) {
        return TEXTURE;
    }
}
