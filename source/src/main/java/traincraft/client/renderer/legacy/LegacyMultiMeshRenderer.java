/* Step 8.4.2 Batch B3: multi-part legacy static-mesh renderer.
 * New helper only; frozen Small Steam / tender / coupling / single-mesh renderer code is untouched.
 */
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

public abstract class LegacyMultiMeshRenderer<T extends Entity> extends EntityRenderer<T> {
    protected static final class Part {
        final ResourceLocation texture;
        final LegacyStaticMesh mesh;
        final double tx, ty, tz;
        final float rx, ry, rz, sx, sy, sz;

        Part(ResourceLocation meshLocation, ResourceLocation texture,
             double tx, double ty, double tz,
             float rx, float ry, float rz,
             float sx, float sy, float sz) {
            this.mesh = LegacyStaticMesh.load(meshLocation);
            this.texture = texture;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.sx = sx; this.sy = sy; this.sz = sz;
        }
    }

    protected static Part part(ResourceLocation meshLocation, ResourceLocation texture,
                               double tx, double ty, double tz,
                               float rx, float ry, float rz,
                               float sx, float sy, float sz) {
        return new Part(meshLocation, texture, tx, ty, tz, rx, ry, rz, sx, sy, sz);
    }

    private final String stockName;
    private final Part[] parts;
    private final double tx, ty, tz;
    private final float rx, ry, rz, sx, sy, sz;
    private final float renderedMinY, renderedMaxY;

    protected LegacyMultiMeshRenderer(EntityRendererProvider.Context context,
                                      String stockName, Part[] parts,
                                      double tx, double ty, double tz,
                                      float rx, float ry, float rz,
                                      float sx, float sy, float sz,
                                      float shadowRadius) {
        super(context);
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("LegacyMultiMeshRenderer requires at least one part");
        }
        this.stockName = stockName;
        this.parts = parts;
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        this.sx = sx; this.sy = sy; this.sz = sz;
        float[] y = transformedYBounds(parts, tx, ty, tz, rx, ry, rz, sx, sy, sz);
        this.renderedMinY = y[0];
        this.renderedMaxY = y[1];
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
        rotate(poseStack, rx, ry, rz);
        if (sx != 1.0F || sy != 1.0F || sz != 1.0F) {
            poseStack.scale(sx, sy, sz);
        }

        for (Part p : parts) {
            poseStack.pushPose();
            poseStack.translate(p.tx, p.ty, p.tz);
            rotate(poseStack, p.rx, p.ry, p.rz);
            if (p.sx != 1.0F || p.sy != 1.0F || p.sz != 1.0F) {
                poseStack.scale(p.sx, p.sy, p.sz);
            }
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentCull(p.texture));
            p.mesh.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        Part primary = parts[0];
        LegacyRollingStockDebug.logRender(entity, stockName, trainYaw, primary.mesh,
                tx, ty, tz, rx, ry, rz, sx, sy, sz, renderedMinY, renderedMaxY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return parts[0].texture;
    }

    private static void rotate(PoseStack poseStack, float rx, float ry, float rz) {
        if (rx != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0.0F) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
    }

    private static float[] transformedYBounds(Part[] parts,
                                               double gtx, double gty, double gtz,
                                               float grx, float gry, float grz,
                                               float gsx, float gsy, float gsz) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;

        for (Part p : parts) {
            float[] xs = {p.mesh.minX(), p.mesh.maxX()};
            float[] ys = {p.mesh.minY(), p.mesh.maxY()};
            float[] zs = {p.mesh.minZ(), p.mesh.maxZ()};

            for (float x0 : xs) for (float y0 : ys) for (float z0 : zs) {
                double[] q = transform(x0, y0, z0,
                        p.tx, p.ty, p.tz, p.rx, p.ry, p.rz, p.sx, p.sy, p.sz);
                q = transform(q[0], q[1], q[2],
                        gtx, gty, gtz, grx, gry, grz, gsx, gsy, gsz);
                min = Math.min(min, (float) q[1]);
                max = Math.max(max, (float) q[1]);
            }
        }
        return new float[] { min, max };
    }

    private static double[] transform(double x, double y, double z,
                                      double tx, double ty, double tz,
                                      float rx, float ry, float rz,
                                      float sx, float sy, float sz) {
        x *= sx; y *= sy; z *= sz;

        double a = Math.toRadians(rz), c = Math.cos(a), s = Math.sin(a);
        double nx = x * c - y * s;
        double ny = x * s + y * c;
        x = nx; y = ny;

        a = Math.toRadians(ry); c = Math.cos(a); s = Math.sin(a);
        nx = x * c + z * s;
        double nz = -x * s + z * c;
        x = nx; z = nz;

        a = Math.toRadians(rx); c = Math.cos(a); s = Math.sin(a);
        ny = y * c - z * s;
        nz = y * s + z * c;
        y = ny; z = nz;

        return new double[] { x + tx, y + ty, z + tz };
    }
}
