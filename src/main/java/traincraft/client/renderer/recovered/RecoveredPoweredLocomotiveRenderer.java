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
        p.popPose();
        super.render(e,yaw,partial,p,b,light);
    }

    @Override
    public ResourceLocation getTextureLocation(T e){
        return traincraft.client.skin.ClientRollingStockSkinManager.texture(e, texture);
    }
}