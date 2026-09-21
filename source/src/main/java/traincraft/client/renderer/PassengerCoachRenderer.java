/* Traincraft 1.20.1 renderer for the classic Passenger Blue coach. */
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
import traincraft.client.model.PassengerCoachModel;
import traincraft.entity.train.passenger.PassengerCoachBlue;

/** Renders the original ModelPassenger6 geometry with passenger_Blue.png. */
public final class PassengerCoachRenderer extends EntityRenderer<PassengerCoachBlue> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/passenger_blue.png");
    private static final float MODEL_SCALE = 0.8F;

    private final ModelPart model;

    public PassengerCoachRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = PassengerCoachModel.root(context.bakeLayer(PassengerCoachModel.LAYER));
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(PassengerCoachBlue entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        /* ModelPassenger6, like the freight model, is authored lengthwise on X.
         * Use the same axis conversion and the server-synchronized train-facing
         * yaw as the confirmed freight/tender consist.  The old RenderEnum -0.47
         * Y offset is not copied literally because modern ModelPart coordinates
         * already render around the rail entity origin; freight 7.3.0a proved
         * that the legacy GL translation is not a direct PoseStack equivalent. */
        poseStack.mulPose(Axis.YP.rotationDegrees(
                90.0F - entity.getRenderTrainFacingYaw(partialTicks)));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PassengerCoachBlue entity) {
        return TEXTURE;
    }
}
