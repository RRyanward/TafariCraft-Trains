/*
 * Step 8.3.2 frozen production renderer for Forney Steam Locomotive.
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
import traincraft.client.model.ForneySteamLocomotiveModel;
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;

public class ForneySteamLocomotiveRenderer extends EntityRenderer<ForneySteamLocomotive> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/locoforney_red.png");

    private final ModelPart model;

    public ForneySteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = ForneySteamLocomotiveModel.root(context.bakeLayer(ForneySteamLocomotiveModel.LAYER));
        this.shadowRadius = 1.10F;
    }

    @Override
    public void render(ForneySteamLocomotive entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);
        // Step 8.3.1 exact CE 7.1 transform order:
        // global rail yaw -> model translation -> model rotation.
        // Batch A had folded model rotation into the first yaw, which
        // rotated the translation axis and visually shifted some stock
        // sideways off the rail centerline.
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));

        // The recovered model geometry has exact minimum Y = 0.
        // The accepted Small Steam baseline also resolves its running-
        // gear bottom to entity-relative Y = 0, so the old 1.7.10
        // -0.44/-0.45 entity-origin compensation is removed here.
        poseStack.translate(-1.3000D, 0.0000D, 0.0000D);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ForneySteamLocomotive entity) {
        return TEXTURE;
    }
}
