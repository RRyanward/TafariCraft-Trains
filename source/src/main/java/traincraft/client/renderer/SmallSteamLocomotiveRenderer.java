/*
 * Traincraft Small Steam Locomotive renderer for Minecraft 1.20.1.
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
import traincraft.client.model.SmallSteamLocomotiveModel;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

/** Renders the original JTMT small-steam geometry using the original red skin. */
public class SmallSteamLocomotiveRenderer extends EntityRenderer<SmallSteamLocomotive> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/rolling_stock/trains/steam/small_red.png");

    private static final float MODEL_SCALE = 0.8F;

    /*
     * The baked JTMT conversion spans Y=0..37 model pixels. At 1/16 block per
     * model pixel and the 0.8 render scale, its rendered height is 1.85 blocks.
     * Step 5.1 changed the locomotive's rail-facing yaw source and exposed the
     * model-space Y orientation again. Flip only the model vertically around Z,
     * then lift it by its rendered height so its running gear remains on rail.
     * Z rotation preserves the locomotive's longitudinal/front-back axis.
     */
    private static final float MODEL_RENDERED_HEIGHT = (37.0F / 16.0F) * MODEL_SCALE;

    private final ModelPart model;

    public SmallSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = SmallSteamLocomotiveModel.body(context.bakeLayer(SmallSteamLocomotiveModel.LAYER));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(SmallSteamLocomotive entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);

        // STEP_9_3C_T5_R7_VISUAL_BOGIE_MIDPOINT
        // Renderer-only correction: the physical entity/couplers remain on the proven r6
        // centerline while the visible rigid body is centered between the two read-only
        // virtual bogies. Y is intentionally untouched so slope height/physics remain frozen.
        traincraft.block.track.LegacyContinuousTrackPath.VirtualBogiePose tcVisualPose =
                traincraft.block.track.LegacyContinuousTrackPath.virtualBogiePose(entity, trainYaw, 1.50D);
        if (tcVisualPose != null) {
            double tcRenderDx = tcVisualPose.center().x - entity.getX();
            double tcRenderDz = tcVisualPose.center().z - entity.getZ();
            double tcRenderOffsetSq = tcRenderDx * tcRenderDx + tcRenderDz * tcRenderDz;
            // Expected Big Curve correction is about 0.07 blocks. Never render-shift more
            // than 0.20 blocks; a larger value means the sampler is not in a safe local pose.
            if (tcRenderOffsetSq > 1.0E-10D && tcRenderOffsetSq <= 0.04D) {
                poseStack.translate(tcRenderDx, 0.0D, tcRenderDz);
            }
        }

        // Keep the train aligned to the rail, then correct only the vertical
        // orientation of the legacy JTMT geometry. Driver controls/physics are
        // untouched by this renderer-only fix.
        poseStack.translate(0.0D, MODEL_RENDERED_HEIGHT, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - trainYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SmallSteamLocomotive entity) {
        return TEXTURE;
    }
}
