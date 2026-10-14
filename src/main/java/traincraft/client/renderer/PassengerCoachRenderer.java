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

    // TC_DEV50D3Q_RENDER_ONLY_BODY_END_CLEARANCE_CANDIDATE
    // The classic 36-pixel body half-reach was 1.80 world blocks. Reducing
    // ONLY longitudinal body rendering to 90% retracts each visible end by
    // about 0.18 world block (1.80 -> 1.62). The eight native wheel meshes,
    // all wheel pivots/phases, entity positions and coupling remain UNCHANGED.
    // This is a trial calibration, NOT a clearance/physics certification.
    private static final float BODY_LENGTH_RENDER_SCALE = 0.90F;
    private static final String[] WHEELS = {
            "box1", "box16", "box19", "box21", "box35", "box4", "box5", "box63"};
    private final ModelPart model;
    private final ModelPart[] tcAllParts = new ModelPart[96];
    private final boolean[] tcWheel = new boolean[96];

    public PassengerCoachRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = PassengerCoachModel.root(context.bakeLayer(PassengerCoachModel.LAYER));
        // ModelPassenger6 root has exactly one 'box' and 'box0'..'box94'.
        // Fail during construction if the protected model shape ever changes.
        this.tcAllParts[0] = model.getChild("box");
        for (int i = 0; i < 95; ++i) {
            this.tcAllParts[i + 1] = model.getChild("box" + i);
        }
        for (String part : WHEELS) {
            for (int i = 0; i < tcAllParts.length; ++i) {
                if ((i == 0 ? "box" : "box" + (i - 1)).equals(part)) {
                    tcWheel[i] = true;
                }
            }
        }
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

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE)));
        // TC_DEV38_R1G1X_PASSENGER_NATIVE_WHEEL_PHASE
        // Use the same frozen utility-car signed-distance phase convention as
        // the accepted Freight native Z-axis wheels. No shared phase tuning.
        PassengerCoachModel.animateWheels(
                this.model,
                (float) traincraft.client.renderer.animation.RollingStockWheelAnimation
                        .sampleNativePhase(entity, partialTicks));
        renderBodyAndWheelsSeparated(poseStack, vertices, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }


    /**
     * Render coach shell shortened ONLY along the authored local X axis.
     * Restore each original part visibility even if rendering fails. This
     * leaves wheel geometry, animation, rail contact and all physics intact.
     * The second pass renders ONLY the eight wheels without body compression.
     */
    private void renderBodyAndWheelsSeparated(PoseStack poseStack,
                                              VertexConsumer vertices, int packedLight) {
        boolean[] savedVisibility = new boolean[tcAllParts.length];
        for (int i = 0; i < tcAllParts.length; ++i) {
            savedVisibility[i] = tcAllParts[i].visible;
        }
        try {
            for (int i = 0; i < tcAllParts.length; ++i) {
                tcAllParts[i].visible = savedVisibility[i] && !tcWheel[i];
            }
            poseStack.pushPose();
            try {
                poseStack.scale(BODY_LENGTH_RENDER_SCALE, 1.0F, 1.0F);
                this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
            } finally {
                poseStack.popPose();
            }
            for (int i = 0; i < tcAllParts.length; ++i) {
                tcAllParts[i].visible = savedVisibility[i] && tcWheel[i];
            }
            this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            for (int i = 0; i < tcAllParts.length; ++i) {
                tcAllParts[i].visible = savedVisibility[i];
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PassengerCoachBlue entity) {
        return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, TEXTURE);
    }
}
