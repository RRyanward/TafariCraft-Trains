/* Traincraft 1.20.1 renderer for the classic caboose. */
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
import traincraft.client.model.ClassicCabooseModel;
import traincraft.entity.train.caboose.ClassicCaboose;

/** Renders the original ModelCaboose geometry with the classic caboose.png art. */
public final class ClassicCabooseRenderer extends EntityRenderer<ClassicCaboose> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/caboose.png");
    private static final float MODEL_SCALE = 0.8F;

    private final ModelPart model;

    public ClassicCabooseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = ClassicCabooseModel.root(context.bakeLayer(ClassicCabooseModel.LAYER));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(ClassicCaboose entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        /* ModelCaboose is authored lengthwise on X.  Use the same axis conversion
         * as the confirmed Freight Cart and Passenger Blue renderers.  The old
         * 1.7.10 RenderEnum used a -0.32 GL Y translation; Step 7.3.0a proved that
         * copying that legacy offset into PoseStack buries wheels in the rails,
         * so no extra modern Y translation is applied here. */
        poseStack.mulPose(Axis.YP.rotationDegrees(
                90.0F - entity.getRenderTrainFacingYaw(partialTicks)));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ClassicCaboose entity) {
        return TEXTURE;
    }
}
