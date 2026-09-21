package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import traincraft.client.model.legacy.LegacyStaticMesh;
import traincraft.debug.LegacyRollingStockDebug;

public abstract class LegacyMeshRenderer<T extends Entity> extends EntityRenderer<T> {
    private final String stockName;
    private final ResourceLocation texture;
    private final LegacyStaticMesh mesh;
    private final double tx, ty, tz;
    private final float rx, ry, rz, sx, sy, sz;
    private final float renderedMinY, renderedMaxY;

    protected LegacyMeshRenderer(EntityRendererProvider.Context context,
                                 String stockName, ResourceLocation meshLocation, ResourceLocation texture,
                                 double tx, double ty, double tz,
                                 float rx, float ry, float rz,
                                 float sx, float sy, float sz,
                                 float shadowRadius) {
        super(context);
        this.stockName = stockName;
        this.texture = texture;
        this.mesh = LegacyStaticMesh.load(meshLocation);
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        this.sx = sx; this.sy = sy; this.sz = sz;
        float[] y = transformedYBounds(mesh, ty, rx, ry, rz, sx, sy, sz);
        this.renderedMinY = y[0]; this.renderedMaxY = y[1];
        this.shadowRadius = shadowRadius;
    }

    protected abstract float trainYaw(T entity, float partialTicks);

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float trainYaw = trainYaw(entity, partialTicks);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));
        poseStack.translate(tx, ty, tz);
        if (rx != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0.0F) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        if (sx != 1.0F || sy != 1.0F || sz != 1.0F) poseStack.scale(sx, sy, sz);

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentCull(texture));
        mesh.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
        LegacyRollingStockDebug.logRender(entity, stockName, trainYaw, mesh,
                tx, ty, tz, rx, ry, rz, sx, sy, sz, renderedMinY, renderedMaxY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return texture; }

    private static float[] transformedYBounds(LegacyStaticMesh m, double ty,
                                               float rx, float ry, float rz,
                                               float sx, float sy, float sz) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        float[] xs = {m.minX(), m.maxX()}, ys = {m.minY(), m.maxY()}, zs = {m.minZ(), m.maxZ()};
        for (float x0 : xs) for (float y0 : ys) for (float z0 : zs) {
            double x=x0*sx, y=y0*sy, z=z0*sz;
            double a=Math.toRadians(rz), c=Math.cos(a), s=Math.sin(a); double nx=x*c-y*s, ny=x*s+y*c; x=nx; y=ny;
            a=Math.toRadians(ry); c=Math.cos(a); s=Math.sin(a); nx=x*c+z*s; double nz=-x*s+z*c; x=nx; z=nz;
            a=Math.toRadians(rx); c=Math.cos(a); s=Math.sin(a); ny=y*c-z*s; nz=y*s+z*c; y=ny; z=nz;
            y += ty;
            min=Math.min(min,(float)y); max=Math.max(max,(float)y);
        }
        return new float[]{min,max};
    }
}
