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
import net.minecraft.util.Mth;
import traincraft.client.model.recovered.exact.ExactLegacyMesh;
import traincraft.entity.train.freight.FreightCart;

/** v1j transport-car renderer. It changes no rail/coupling state. */
public final class ExactFreightCarRenderer<T extends FreightCart> extends EntityRenderer<T> {
    private final ExactLegacyMesh mesh;
    private final ResourceLocation texture;
    private final double tx, ty, tz;
    private final float rx, ry, rz, sx, sy, sz;

    public ExactFreightCarRenderer(EntityRendererProvider.Context context, String meshId,
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
        float railYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(270.0F - railYaw));
        pose.translate(tx, ty, tz);
        if (rx != 0F) pose.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0F) pose.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0F) pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(sx, sy, sz);
        /*
         * TC_TRANSPORT_CAGE_CUTOUT_R1F18
         * Cage/slat textures use hard transparent openings.  Cutout/no-cull
         * makes those openings truly see-through from either side and avoids
         * translucent depth sorting hiding occupants behind the bars.
         */
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, texture)));
        mesh.render(pose, out, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, packedLight);
    }

    @Override public ResourceLocation getTextureLocation(T entity) { return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, texture); }
}