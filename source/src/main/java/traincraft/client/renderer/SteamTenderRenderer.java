/* Traincraft 1.20.1 classic steam tender renderer. */
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
import traincraft.client.model.SteamTenderModel;
import traincraft.entity.train.tender.SteamTender;

/** Renders the legacy normal steam tender with its original Traincraft texture. */
public class SteamTenderRenderer extends EntityRenderer<SteamTender> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/steam_tender_red.png");
    private static final float MODEL_SCALE = 0.8F;

    private final ModelPart model;

    public SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = SteamTenderModel.root(context.bakeLayer(SteamTenderModel.LAYER));
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(SteamTender entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        // The legacy normal steam tender was authored with its running gear at
        // Y=0 and the tank/coal load above it. Unlike the JTMT small locomotive,
        // this model must not receive the locomotive's 180-degree Z flip; doing
        // so puts the bogies above the body and makes the tender appear upside down.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getRenderTrainFacingYaw(partialTicks)));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SteamTender entity) {
        return TEXTURE;
    }
}
