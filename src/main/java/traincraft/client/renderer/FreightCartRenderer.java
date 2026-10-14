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

    // TC_DEV38_R1G1T2_FREIGHT_BOGIE_CHORD
    // Native axle X centers: -17, -10, +9, +16 model px.
    // Bogie centers: -13.5 and +12.5 -> 26 px = 1.30 blocks rendered.
    private static final double BOGIE_CENTER_SPACING =
            (26.0D / 16.0D) * MODEL_SCALE;

    // Exact model midpoint is X=-0.5 px; recenter by +0.025 block.
    private static final double MODEL_LONGITUDINAL_CENTER_CORRECTION =
            (0.5D / 16.0D) * MODEL_SCALE;
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
                float trainYaw = entity.getRenderTrainFacingYaw(partialTicks);
        float tcBodyYaw = trainYaw;

        traincraft.block.track.LegacyContinuousTrackPath.VirtualBogiePose tcVisualPose =
                traincraft.block.track.LegacyContinuousTrackPath.virtualBogiePose(
                        entity, trainYaw, BOGIE_CENTER_SPACING);

        if (tcVisualPose != null) {
            double tcRenderDx = tcVisualPose.center().x - entity.getX();
            double tcRenderDz = tcVisualPose.center().z - entity.getZ();
            double tcRenderOffsetSq =
                    tcRenderDx * tcRenderDx + tcRenderDz * tcRenderDz;

            if (tcRenderOffsetSq <= 0.04D) {
                if (tcRenderOffsetSq > 1.0E-10D) {
                    poseStack.translate(tcRenderDx, 0.0D, tcRenderDz);
                }

                float tcPoseYaw = tcVisualPose.yaw();
                float tcYawDelta = Math.abs(
                        net.minecraft.util.Mth.wrapDegrees(tcPoseYaw - trainYaw));
                if (tcYawDelta <= 20.0F) {
                    tcBodyYaw = tcPoseYaw;
                }
            }
        }

        // TC_DEV38_R1G1U2_MEDIUM_CURVE_RENDER_FALLBACK
        // Same visual-only fallback as Small Steam, using Freight's measured
        // 1.30-block bogie-center chord.  It runs only when the accepted
        // assembly-owned pose is unavailable on track_medium_curve_segment.
        if (tcVisualPose == null) {
            net.minecraft.core.BlockPos tcRailPos = entity.blockPosition();
            net.minecraft.world.level.block.state.BlockState tcRailState =
                    entity.level().getBlockState(tcRailPos);
            if (!net.minecraft.world.level.block.BaseRailBlock.isRail(tcRailState)) {
                tcRailPos = tcRailPos.below();
                tcRailState = entity.level().getBlockState(tcRailPos);
            }

            net.minecraft.resources.ResourceLocation tcRailId =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(tcRailState.getBlock());
            if (tcRailId != null
                    && "traincraft".equals(tcRailId.getNamespace())
                    && "track_medium_curve_segment".equals(tcRailId.getPath())) {
                final double tcHalfSpacing = BOGIE_CENTER_SPACING * 0.5D;
                net.minecraft.world.phys.Vec3 tcFront =
                        entity.getPosOffs(
                                entity.getX(), entity.getY(), entity.getZ(), tcHalfSpacing);
                net.minecraft.world.phys.Vec3 tcRear =
                        entity.getPosOffs(
                                entity.getX(), entity.getY(), entity.getZ(), -tcHalfSpacing);

                if (tcFront != null && tcRear != null) {
                    double tcFallbackDx = tcFront.x - tcRear.x;
                    double tcFallbackDz = tcFront.z - tcRear.z;
                    double tcFallbackHorizontal = Math.hypot(tcFallbackDx, tcFallbackDz);
                    double tcFallbackCenterX = (tcFront.x + tcRear.x) * 0.5D;
                    double tcFallbackCenterZ = (tcFront.z + tcRear.z) * 0.5D;
                    double tcRenderDx = tcFallbackCenterX - entity.getX();
                    double tcRenderDz = tcFallbackCenterZ - entity.getZ();
                    double tcRenderOffsetSq =
                            tcRenderDx * tcRenderDx + tcRenderDz * tcRenderDz;

                    if (tcFallbackHorizontal > 1.0E-6D && tcRenderOffsetSq <= 0.04D) {
                        if (tcRenderOffsetSq > 1.0E-10D) {
                            poseStack.translate(tcRenderDx, 0.0D, tcRenderDz);
                        }

                        float tcSampledYaw = net.minecraft.util.Mth.wrapDegrees(
                                (float) Math.toDegrees(
                                        Math.atan2(tcFallbackDx, -tcFallbackDz)));
                        float tcOppositeYaw =
                                net.minecraft.util.Mth.wrapDegrees(tcSampledYaw + 180.0F);
                        float tcSampledDelta = Math.abs(
                                net.minecraft.util.Mth.wrapDegrees(tcSampledYaw - trainYaw));
                        float tcOppositeDelta = Math.abs(
                                net.minecraft.util.Mth.wrapDegrees(tcOppositeYaw - trainYaw));
                        float tcFallbackYaw =
                                tcOppositeDelta < tcSampledDelta ? tcOppositeYaw : tcSampledYaw;
                        float tcYawDelta = Math.abs(
                                net.minecraft.util.Mth.wrapDegrees(tcFallbackYaw - trainYaw));
                        if (tcYawDelta <= 20.0F) {
                            tcBodyYaw = tcFallbackYaw;
                        }
                    }
                }
            }
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - tcBodyYaw));

        // Renderer-only local longitudinal recentering from the measured
        // -0.5 model-pixel native bogie/body midpoint.
        poseStack.translate(
                MODEL_LONGITUDINAL_CENTER_CORRECTION, 0.0D, 0.0D);

        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        // TC_DEV38_R1G1R2_NATIVE_WHEEL_PHASE
        FreightCartModel.animateWheels(
                this.model,
                (float) traincraft.client.renderer.animation.RollingStockWheelAnimation
                        .sampleNativePhase(entity, partialTicks));

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE)));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FreightCart entity) {
        return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE);
    }
}
