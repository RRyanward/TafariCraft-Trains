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
    // DEV58G10: read-only per-model wall-clock distribution. OFF by default.
    private static final boolean DEV58G10_METRICS_ON=java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G10-METRICS-ON.flag"));
    private static final org.slf4j.Logger DEV58G10_LOG=com.mojang.logging.LogUtils.getLogger();
    private long dev58g10WindowStart=0L,dev58g10Calls=0L;
    private long dev58g10MeshNanos=0L,dev58g10LightsNanos=0L;
    private long dev58g10DebugNanos=0L,dev58g10SuperNanos=0L,dev58g10TotalNanos=0L;
    private long[] dev58g10LastCounters=null;
    private static double dev58g10Ms(long ns) {
        return Math.round(ns/10000.0D)/100.0D;
    }
    private void dev58g10Report(long start,long meshDone,long lightsDone,long debugDone,long finish) {
        if(dev58g10WindowStart==0L) dev58g10WindowStart=start;
        dev58g10Calls++;
        dev58g10MeshNanos+=Math.max(0L,meshDone-start);
        dev58g10LightsNanos+=Math.max(0L,lightsDone-meshDone);
        dev58g10DebugNanos+=Math.max(0L,debugDone-lightsDone);
        dev58g10SuperNanos+=Math.max(0L,finish-debugDone);
        dev58g10TotalNanos+=Math.max(0L,finish-start);
        if(finish-dev58g10WindowStart<10_000_000_000L)return;
        final long[] snapshot=mesh.dev58g10Counters();
        final long[] change=new long[snapshot.length];
        for(int i=0;i<snapshot.length;i++)
            change[i]=snapshot[i]-(dev58g10LastCounters==null?0L:dev58g10LastCounters[i]);
        dev58g10LastCounters=snapshot;
        DEV58G10_LOG.info("[DEV58G10-RENDER] model={} calls={} window_ms={} mesh_ms={} "
            +"lights_ms={} debug_ms={} super_ms={} total_ms={} mesh_ms_per_call={} "
            +"legacy_vertices={} known_motion_vertices_lower_bound={} cpu_packet_vertices={} "
            +"gpu_skipped_vertices={} restored_vertices={} packet_hits={} packet_rebuilds={} "
            +"gpu_draws={} gpu_uploads={} gpu_fallbacks={} gpu_upload_ms={} "
            +"measurement=RENDERER_WALL_TIME_NOT_GPU_TIME",
            mesh.source(),dev58g10Calls,dev58g10Ms(finish-dev58g10WindowStart),
            dev58g10Ms(dev58g10MeshNanos),dev58g10Ms(dev58g10LightsNanos),
            dev58g10Ms(dev58g10DebugNanos),dev58g10Ms(dev58g10SuperNanos),
            dev58g10Ms(dev58g10TotalNanos),
            dev58g10Ms(dev58g10MeshNanos)/Math.max(1L,dev58g10Calls),
            change[0],change[1],change[2],change[3],change[4],change[5],
            change[6],change[7],change[8],change[9],dev58g10Ms(change[10]));
        dev58g10WindowStart=finish;
        dev58g10Calls=0;dev58g10MeshNanos=0L;dev58g10LightsNanos=0L;
        dev58g10DebugNanos=0L;dev58g10SuperNanos=0L;dev58g10TotalNanos=0L;
    }

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

        final long dev58g10Start=DEV58G10_METRICS_ON?System.nanoTime():0L;
        // DEV58G9S2: OFF by default. The mesh itself verifies the exact
        // MILW pilot, source-certified static chunks, buffer and shader state.
        // Any mismatch uses the unchanged CPU vertex renderer.
        mesh.dev58g9s2Render(poseStack,buffers,tcRenderType(getTextureLocation(entity)),
                packedLight,OverlayTexture.NO_OVERLAY);
        final long dev58g10AfterMesh=DEV58G10_METRICS_ON?System.nanoTime():0L;
        Dev55L19FleetBeams.setWorld(Dev55L19FleetBeams.worldMatrix(trainYaw,railLift,railPitch,tx,ty,tz,rx,ry,rz,sx,sy,sz));
        Dev44TrainLightVisuals.draw(tcDev44LightMeshId, entity, poseStack, buffers, mesh.minX(), mesh.maxX(), mesh.minY());
        final long dev58g10AfterLights=DEV58G10_METRICS_ON?System.nanoTime():0L;
        LegacyRollingStockDebug.logRender(entity, stockName, trainYaw, mesh,
                tx, ty, tz, rx, ry, rz, sx, sy, sz, renderedMinY, renderedMaxY);
        final long dev58g10AfterDebug=DEV58G10_METRICS_ON?System.nanoTime():0L;
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
        if(DEV58G10_METRICS_ON)
            dev58g10Report(dev58g10Start,dev58g10AfterMesh,
                dev58g10AfterLights,dev58g10AfterDebug,System.nanoTime());
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
