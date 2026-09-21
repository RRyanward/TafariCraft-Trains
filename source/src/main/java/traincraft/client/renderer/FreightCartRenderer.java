/* Traincraft 1.20.1 renderer for the classic yellow Freight Cart. */
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
import traincraft.client.model.FreightCartModel;
import traincraft.entity.train.freight.FreightCart;

/** Renders the original ModelFreightCart2 with freightcart.png. */
public final class FreightCartRenderer extends EntityRenderer<FreightCart> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/freightcart.png");
    private static final float MODEL_SCALE = 0.8F;

    private final ModelPart model;

    public FreightCartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = FreightCartModel.root(context.bakeLayer(FreightCartModel.LAYER));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(FreightCart entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        /* ModelFreightCart2 is authored lengthwise on X rather than Z. A 90-degree
         * base turn maps that classic axis onto the same rail-facing yaw used by
         * the frozen tender coupling controller.
         *
         * Important 1.20.1 porting note: the old 1.7.10 RenderEnum stored a
         * -0.32 Y render offset for this cart, but applying that value literally
         * in the modern PoseStack coordinate system sinks the running gear into
         * the rail bed. The converted ModelPart geometry already sits correctly
         * around the entity rail origin, so no extra vertical translation is
         * applied here. This is render-only; entity position/coupling physics
         * remain untouched. */
        poseStack.mulPose(Axis.YP.rotationDegrees(
                90.0F - entity.getRenderTrainFacingYaw(partialTicks)));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FreightCart entity) {
        return TEXTURE;
    }
}
