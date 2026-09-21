/*
 * Step 8.3.2-r5 locked production renderer for Shay Steam Locomotive.
 * RenderEnum translation/rotation values are recovered from CE 7.1 bytecode.
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
import traincraft.client.model.ShaySteamLocomotiveModel;
import traincraft.entity.train.steam.classics.ShaySteamLocomotive;

public class ShaySteamLocomotiveRenderer extends EntityRenderer<ShaySteamLocomotive> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/locoshay.png");

    private final ModelPart model;

    public ShaySteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = ShaySteamLocomotiveModel.root(context.bakeLayer(ShaySteamLocomotiveModel.LAYER));
        this.shadowRadius = 1.10F;
    }

    @Override
    public void render(ShaySteamLocomotive entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);
        // Step 8.3.1 exact CE 7.1 transform order:
        // global rail yaw -> model translation -> model rotation.
        // Batch A had folded model rotation into the first yaw, which
        // rotated the translation axis and visually shifted some stock
        // sideways off the rail centerline.
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));

        // Step 8.3.2-r5: renderer-aware validation established the modern
        // ModelPart rail-origin plane at Y = 0.0000, matching the good Forney/Mogul stock.
        poseStack.translate(-0.4000D, 0.0000D, 0.0000D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ShaySteamLocomotive entity) {
        return TEXTURE;
    }
}
