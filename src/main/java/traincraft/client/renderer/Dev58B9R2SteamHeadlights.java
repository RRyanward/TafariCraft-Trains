package traincraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import traincraft.client.renderer.legacy.Dev44TrainLightVisuals;
import traincraft.client.renderer.legacy.Dev55L13SoftBeam;
import traincraft.client.renderer.legacy.Dev55L19FleetBeams;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

/**
 * DEV58B9R2: first-pass front lens/beam for ONLY Forney and Mogul.
 * Front housing box60 is the 4x4x1 part at native model coordinates:
 *  Forney x=-2..2,y=24..28,z=-25..-24; Mogul x=-2..2,y=25..29,z=-27..-26.
 * Its external -Z face is used for the initial lamp candidate. Requires visual
 * in-game confirmation; do not change frozen model/rod/wheel meshes.
 * Server ground illumination is in Dev58B9R2SteamGroundLights.
 */
public final class Dev58B9R2SteamHeadlights {
    private Dev58B9R2SteamHeadlights() {}
    private static final float REACH=12F;
    private record Mark(float x,float y,float z,double tx) {}
    private static Mark mark(String kind) {
        return switch (kind) {
            case "forney" -> new Mark(0F,26F/16F,-25.04F/16F,-1.3D);
            case "mogul" -> new Mark(0F,27F/16F,-27.04F/16F,-0.6D);
            default -> null;
        };
    }
    private static float night(long time) {
        long t=Math.floorMod(time,24000L);
        if(t<12000L)return 0F;
        if(t<13000L)return (t-12000L)/1000F;
        if(t<23000L)return 1F;
        return (24000L-t)/1000F;
    }
    private static Vec3 world(SmallSteamLocomotive entity,Matrix4f m,float x,float y,float z) {
        Vector3f q=m.transformPosition(new Vector3f(x,y,z));
        return entity.position().add(q.x(),q.y(),q.z());
    }
    private static void vertex(VertexConsumer v,Matrix4f m,float x,float y,float z,int a) {
        v.vertex(m,x,y,z).color(255,232,167,a).endVertex();
    }
    private static void lens(VertexConsumer v,Matrix4f m,float x,float y,float z,float brightness) {
        float h=0.088F;int a=(int)(232F*brightness);
        vertex(v,m,x-h,y-h,z,a);vertex(v,m,x+h,y-h,z,a);
        vertex(v,m,x+h,y+h,z,a);vertex(v,m,x-h,y+h,z,a);
        vertex(v,m,x-h,y+h,z,a);vertex(v,m,x+h,y+h,z,a);
        vertex(v,m,x+h,y-h,z,a);vertex(v,m,x-h,y-h,z,a);
    }
    public static void draw(SmallSteamLocomotive e,PoseStack pose,MultiBufferSource buffers,
                            float partial,String kind) {
        if(e==null || !e.isAlive())return;
        Mark mark=mark(kind);
        if(mark==null)return;
        float brightness=traincraft.lighting.LocomotiveLightMode.brightness(e);
        if(brightness<=0.005F)return;
        // Matches the exact accepted renderer pose: rail yaw, X shift, Y+90.
        Matrix4f m=Dev55L19FleetBeams.worldMatrix(
            e.getRenderTrainFacingYaw(partial),0D,0F,
            mark.tx(),0D,0D,0F,90F,0F,1F,1F,1F);
        Vector3f up=m.transformDirection(new Vector3f(0F,1F,0F));
        if(Math.abs(up.y)<0.2F)return;
        float x=mark.x(),y=mark.y(),z=mark.z();
        Vec3 start=world(e,m,x,y,z);
        float farZ=z-REACH;
        Vec3 far=world(e,m,x,y,farZ);
        double floor=e.getY()+0.18D;
        BlockHitResult terrain=e.level().clip(new ClipContext(
            new Vec3(far.x,e.getY()+0.25D,far.z),
            new Vec3(far.x,e.getY()-6D,far.z),
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
        if(terrain.getType()==HitResult.Type.BLOCK) {
            double ground=terrain.getLocation().y;
            if(ground<=e.getY()+0.30D && ground>=e.getY()-5.95D)floor=ground+0.065D;
        }
        float endY=y+(float)((floor-far.y)/up.y);
        Vec3 end=world(e,m,x,endY,farZ);
        double fraction=1D;
        BlockHitResult block=e.level().clip(new ClipContext(
            start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
        if(block.getType()==HitResult.Type.BLOCK)
            fraction=Math.min(fraction,start.distanceTo(block.getLocation())/
                Math.max(0.001D,start.distanceTo(end)));
        AABB swept=new AABB(start,end).inflate(0.9D);
        for(Entity other:e.level().getEntities(e,swept,
            target->target!=e && target.isAlive() && !target.isSpectator()
                && target instanceof LivingEntity && !target.isPassengerOfSameVehicle(e))) {
            var hit=other.getBoundingBox().inflate(0.10D).clip(start,end);
            if(hit.isPresent())fraction=Math.min(fraction,
                start.distanceTo(hit.get())/Math.max(0.001D,start.distanceTo(end)));
        }
        fraction=Math.max(0.0233D,Math.min(1D,fraction-0.007D));
        float clippedZ=z+(farZ-z)*(float)fraction;
        float clippedY=y+(endY-y)*(float)fraction;
        Matrix4f screen=pose.last().pose();
        VertexConsumer v=buffers.getBuffer(Dev44TrainLightVisuals.creativeBeamType());
        lens(v,screen,x,y,z,brightness);
        Dev55L13SoftBeam.ribbon(v,screen,x,y,z-0.03F,
            x,clippedY,clippedZ,1F,0F,1.10F,brightness);
    }
}
