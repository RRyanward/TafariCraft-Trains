/*
 * Traincraft USSR 0-5-0 Steam Locomotive renderer for Minecraft 1.20.1.
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
import traincraft.client.model.UssrSteamLocomotiveModel;
import traincraft.entity.train.steam.ussr.UssrSteamLocomotive;

/**
 * Original CE 7.1 RenderEnum.locoSteamEr_USSR:
 *   model: ModelLocoEr_Ussr
 *   texture: locoEr_Ussr.png
 *   translation: (-0.75, -0.44, 0)
 *   extra rotation: none
 *   extra scale: none
 *
 * The fixed 270-degree correction converts the legacy X-longitudinal model
 * into the modern Traincraft -Z-forward render convention.
 */
public class UssrSteamLocomotiveRenderer extends EntityRenderer<UssrSteamLocomotive> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/locoer_ussr.png");

    private final ModelPart model;

    public UssrSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = UssrSteamLocomotiveModel.root(
                context.bakeLayer(UssrSteamLocomotiveModel.LAYER));
        this.shadowRadius = 1.35F;
    }

    @Override
    public void render(UssrSteamLocomotive entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));

        // Exact production CE 7.1 model translation.
        poseStack.translate(-0.75D, 0.0000D, 0.0D);

        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(UssrSteamLocomotive entity) {
        return TEXTURE;
    }
}
