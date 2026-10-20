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


    // TC_DEV38_R1G1R3A_NATIVE_AXLE_SPACING
    // Native wheel centers are 12 model pixels apart along Z.
    // 12 / 16 * MODEL_SCALE(0.8) = 0.60 blocks.
    private static final double NATIVE_AXLE_SPACING =
            (12.0D / 16.0D) * MODEL_SCALE;
    // DEV54H15AK: native Small Steam connector ONLY; accepted wheel phase intact.
    private static final boolean DEV54H15AK_NATIVE_LINKS_ON = h15akNativeLinksMarker();
    private static boolean h15akNativeLinksMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15AK-SMALL-STEAM-LINKS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equals("DEV54H15AK-SMALL-ON-V1");
        }catch(Exception ignored){return false;}
    }
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


        // TC_DEV38_R1G1T2_RIGID_BODY_YAW

        // Use the safe native-axle chord yaw for the visible rigid shell.

        float tcBodyYaw = trainYaw;
        // STEP_9_3C_T5_R7_VISUAL_BOGIE_MIDPOINT
        // Renderer-only correction: the physical entity/couplers remain on the proven r6
        // centerline while the visible rigid body is centered between the two read-only
        // virtual bogies. Y is intentionally untouched so slope height/physics remain frozen.
        traincraft.block.track.LegacyContinuousTrackPath.VirtualBogiePose tcVisualPose =
                traincraft.block.track.LegacyContinuousTrackPath.virtualBogiePose(entity, trainYaw, NATIVE_AXLE_SPACING);
        if (tcVisualPose != null) {
            double tcRenderDx = tcVisualPose.center().x - entity.getX();
            double tcRenderDz = tcVisualPose.center().z - entity.getZ();
            double tcRenderOffsetSq = tcRenderDx * tcRenderDx + tcRenderDz * tcRenderDz;
            // Expected Big Curve correction is about 0.07 blocks. Never render-shift more
            // than 0.20 blocks; a larger value means the sampler is not in a safe local pose.
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

        // Keep the train aligned to the rail, then correct only the vertical
        // orientation of the legacy JTMT geometry. Driver controls/physics are
        // untouched by this renderer-only fix.
        // TC_DEV38_R1G1U2_MEDIUM_CURVE_RENDER_FALLBACK
        // r1g1u deep sweep proved the assembly-owned visual pose can be null on
        // track_medium_curve_segment.  Fall back ONLY there to vanilla's read-only
        // rail-position sampler.  This changes PoseStack only: no entity/path state.
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
                final double tcHalfSpacing = NATIVE_AXLE_SPACING * 0.5D;
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

                    // Preserve r1g1t2's absolute visual safety ceiling.
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
        poseStack.translate(0.0D, MODEL_RENDERED_HEIGHT, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - tcBodyYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        // TC_DEV38_R1G1R3A_NATIVE_WHEEL_DIRECTION
        // Native Small Steam wheels rotate around X. Reverse the legacy
        // steam-phase polarity locally; Freight/shared phase remain frozen.
        float h15akAcceptedWheelPhase =
                -(float) traincraft.client.renderer.animation.RollingStockWheelAnimation
                        .sampleNativePhase(entity, partialTicks);
        SmallSteamLocomotiveModel.animateWheels(this.model,h15akAcceptedWheelPhase);
        SmallSteamLocomotiveModel.animateFrontCrankLinks(
                this.model,h15akAcceptedWheelPhase,DEV54H15AK_NATIVE_LINKS_ON);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE)));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SmallSteamLocomotive entity) {
        return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE);
    }
}
