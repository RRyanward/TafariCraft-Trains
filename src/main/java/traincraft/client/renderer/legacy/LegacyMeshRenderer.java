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
    private final ResourceLocation tcDev44LightMeshId;
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
        this.tcDev44LightMeshId = meshLocation;
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        this.sx = sx; this.sy = sy; this.sz = sz;
        float[] y = transformedYBounds(mesh, ty, rx, ry, rz, sx, sy, sz);
        this.renderedMinY = y[0]; this.renderedMaxY = y[1];
        this.shadowRadius = shadowRadius;
    }

    protected abstract float trainYaw(T entity, float partialTicks);


    /*
     * TC_BATCH7_R6E_CURVED_SLOPE_VISUAL_POSE
     *
     * Use Traincraft's assembly-owned virtual bogies for visual pitch/height.
     * This deliberately replaces r6c's vanilla getPosOffs() sampling, which saw
     * the hidden ASCENDING_* guide as a 45-degree body angle.
     */
    private static traincraft.block.track.LegacyContinuousTrackPath.VirtualBogiePose
    tcVisualRailPose(net.minecraft.world.entity.Entity entity, float trainYaw) {
        if (!(entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart cart)) {
            return null;
        }
        return traincraft.block.track.LegacyContinuousTrackPath.virtualBogiePose(
                cart, trainYaw, 1.50D);
    }
    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float trainYaw = trainYaw(entity, partialTicks);
        traincraft.block.track.LegacyContinuousTrackPath.VirtualBogiePose railPose =
                tcVisualRailPose(entity, trainYaw);
        float railPitch = railPose == null
                ? 0.0F
                : net.minecraft.util.Mth.clamp(railPose.pitch(), -20.0F, 20.0F);
        double railLift = railPose == null
                ? 0.0D
                : net.minecraft.util.Mth.clamp(
                        railPose.center().y - entity.getY(),
                        -1.25D,
                        1.25D);
        poseStack.pushPose();
        if (Math.abs(railLift) > 0.0001D) {
            poseStack.translate(0.0D, railLift, 0.0D);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - trainYaw));
        if (Math.abs(railPitch) > 0.001F) {
            // TC_BATCH7_R6F2_LEGACY_PITCH_SIGN
            // LegacyMeshRenderer aligns the physical train nose with local -X,
            // so VirtualBogiePose nose-up pitch must rotate around -Z.
            poseStack.mulPose(Axis.ZP.rotationDegrees(-railPitch));
        }
        poseStack.translate(tx, ty, tz);
        if (rx != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0.0F) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        if (sx != 1.0F || sy != 1.0F || sz != 1.0F) poseStack.scale(sx, sy, sz);

        VertexConsumer vertices = buffers.getBuffer(tcRenderType(getTextureLocation(entity)));
        mesh.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
        Dev55L19FleetBeams.setWorld(Dev55L19FleetBeams.worldMatrix(trainYaw,railLift,railPitch,tx,ty,tz,rx,ry,rz,sx,sy,sz));
        Dev44TrainLightVisuals.draw(tcDev44LightMeshId, entity, poseStack, buffers, mesh.minX(), mesh.maxX(), mesh.minY());
        LegacyRollingStockDebug.logRender(entity, stockName, trainYaw, mesh,
                tx, ty, tz, rx, ry, rz, sx, sy, sz, renderedMinY, renderedMaxY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return traincraft.client.skin.ClientRollingStockSkinManager.texture(entity, texture); }

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

    /*
     * TC_A4_RENDER_PASS_R1F16C
     * Preserve the current translucent-cull pass for every legacy renderer by
     * default. Individual opaque stock may override this without changing the
     * rest of the legacy roster.
     */
    protected RenderType tcRenderType(ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
