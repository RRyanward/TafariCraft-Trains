package traincraft.client.renderer.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import traincraft.client.model.recovered.exact.ExactLegacyMesh;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/** v1j: exact old CustomModelRenderer geometry/UV path for the five broken powered models. */
public final class ExactPoweredLocomotiveRenderer<T extends LegacyPoweredLocomotive> extends EntityRenderer<T> {
    private final ExactLegacyMesh mesh;
    private final ResourceLocation texture;
    private final double tx, ty, tz;
    private final float rx, ry, rz, sx, sy, sz;

    public ExactPoweredLocomotiveRenderer(EntityRendererProvider.Context context, String meshId,
            ResourceLocation texture, double tx, double ty, double tz,
            float rx, float ry, float rz, float sx, float sy, float sz, float shadow) {
        super(context);
        this.mesh = ExactLegacyMesh.load(meshId);
        this.texture = texture;
        this.tx=tx;this.ty=ty;this.tz=tz;this.rx=rx;this.ry=ry;this.rz=rz;this.sx=sx;this.sy=sy;this.sz=sz;
        this.shadowRadius = shadow;
    }

    @Override
    public void render(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(270.0F - entity.getRenderTrainFacingYaw(partialTicks)));
        pose.translate(tx, ty + entity.tcCalibrationHeightAdjustment(), tz);
        if (entity.tcIsCalibrationSelected() && entity.tickCount % 20 == 0) {
            org.slf4j.LoggerFactory.getLogger("TafariCraftExactPoweredRenderer").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-EXACT-RENDER] variant=%s id=%d meshTy=%.4f liveAdj=%.4f appliedY=%.4f",
                            entity.variantId(),
                            entity.getId(),
                            ty,
                            entity.tcCalibrationHeightAdjustment(),
                            ty + entity.tcCalibrationHeightAdjustment()));
        }
        if (rx != 0F) pose.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0F) pose.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0F) pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(sx, sy, sz);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, texture)));
        mesh.render(pose, out, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, packedLight);
    }

    @Override public ResourceLocation getTextureLocation(T entity) { return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, texture); }
}