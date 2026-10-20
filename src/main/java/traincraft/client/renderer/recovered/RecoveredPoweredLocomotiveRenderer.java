package traincraft.client.renderer.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

public final class RecoveredPoweredLocomotiveRenderer<T extends LegacyPoweredLocomotive> extends EntityRenderer<T> {
    private final EntityModel<LegacyPoweredLocomotive> model;
    private final ResourceLocation texture;
    private final double tx,ty,tz;
    private final float rx,ry,rz,sx,sy,sz;

    private int tcLastCalibrationDebugTick = Integer.MIN_VALUE;

    public RecoveredPoweredLocomotiveRenderer(EntityRendererProvider.Context c,
            EntityModel<LegacyPoweredLocomotive> model, ResourceLocation texture,
            double tx,double ty,double tz,float rx,float ry,float rz,float sx,float sy,float sz,float shadow){
        super(c);
        this.model=model;
        this.texture=texture;
        this.tx=tx;
        this.ty=ty;
        this.tz=tz;
        this.rx=rx;
        this.ry=ry;
        this.rz=rz;
        this.sx=sx;
        this.sy=sy;
        this.sz=sz;
        this.shadowRadius=shadow;
    }

    @Override
    public void render(T e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
        p.pushPose();
        p.mulPose(Axis.YP.rotationDegrees(270.0F-e.getRenderTrainFacingYaw(partial)));
        p.translate(tx, ty + e.getPoweredModelLift(), tz);
        if ((e.tcIsCalibrationSelected() || Math.abs(e.tcCalibrationHeightAdjustment()) > 0.0001D)
                && e.tickCount != tcLastCalibrationDebugTick
                && e.tickCount % 20 == 0) {
            tcLastCalibrationDebugTick = e.tickCount;
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationRender").info(
                    String.format(java.util.Locale.ROOT,
                            "[TC-CAL-RENDER] variant=%s id=%d tick=%d entityY=%.4f rendererTy=%.4f syncedAdj=%.4f effectiveAdj=%.4f totalLift=%.4f appliedTranslateY=%.4f",
                            e.variantId(), e.getId(), e.tickCount, e.getY(), ty,
                            e.tcCalibrationSyncedHeightAdjustment(),
                            e.tcCalibrationHeightAdjustment(),
                            e.getPoweredModelLift(),
                            ty + e.getPoweredModelLift()));
        }
        if(rx!=0)p.mulPose(Axis.XP.rotationDegrees(rx));
        if(ry!=0)p.mulPose(Axis.YP.rotationDegrees(ry));
        if(rz!=0)p.mulPose(Axis.ZP.rotationDegrees(rz));
        p.scale(sx,sy,sz);
        VertexConsumer v=b.getBuffer(RenderType.entityTranslucentCull(traincraft.client.skin.ClientRollingStockSkinManager.texture(e, texture)));
        model.renderToBuffer(p,v,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        // TC_DEV38_R1G1Z7W5_RENDERER_WHEEL_FACE_HOOK
        traincraft.client.renderer.animation.StructuralWheelFaceOverlayRenderer.renderNative(
                model, p, v, light, OverlayTexture.NO_OVERLAY);
        // DEV55L25R2: review-only lamp geometry for the TWO recovered trams.
        // The L19 preview is Creative-only and does NOT require electric
        // traction/backup redstone. Ordinary trains remain untouched.
        final String tramId=e.variantId();
        if (("locomotive_electric_tram".equals(tramId)
             || "locomotive_electric_tram_ny".equals(tramId))
                && traincraft.client.renderer.legacy.Dev55L19FleetBeams.active(e,tramId)) {
            // Provisional model-space front lens positions (16 model units per
            // block). Viewer-approved per-lamp coordinates were not in capture;
            // keep these as review candidates, editable using /tclightfleet.
            final double[][] front = "locomotive_electric_tram".equals(tramId)
                    ? new double[][]{{-1.875D,1.59375D,0D,1D}}
                    : new double[][]{{-2.09375D,1.8125D,0D,1D}};
            traincraft.client.renderer.legacy.Dev55L19FleetBeams.setWorld(
                    traincraft.client.renderer.legacy.Dev55L19FleetBeams.worldMatrix(
                        e.getRenderTrainFacingYaw(partial),e.getPoweredModelLift(),0F,
                        tx,ty,tz,rx,ry,rz,sx,sy,sz));
            try {
                var lightBuffer=b.getBuffer(traincraft.client.renderer.legacy.Dev44TrainLightVisuals.creativeBeamType());
                boolean drawn=traincraft.client.renderer.legacy.Dev55L19FleetBeams.render(
                        e,tramId,lightBuffer,p.last().pose(),front,1F);
                if(traincraft.debug.Dev55L19Settings.diagnostic() && e.tickCount%80==0)
                    org.slf4j.LoggerFactory.getLogger("DEV55L25R2-TRAM-PILOT").info(
                      "[DEV55L25R2-TRAM] model={} entity={} drawn={} pilot=true linePower={} backup={} tractionUnchanged=true",
                      tramId,e.getId(),drawn,e.isOnPoweredEnergyTrack(),e.hasPortableBackupRedstone());
            } finally {
                traincraft.client.renderer.legacy.Dev55L19FleetBeams.clearWorld();
            }
        }
        p.popPose();
        super.render(e,yaw,partial,p,b,light);
    }

    @Override
    public ResourceLocation getTextureLocation(T e){
        return traincraft.client.skin.ClientRollingStockSkinManager.texture(e, texture);
    }
}