package traincraft.client.renderer.recovered;

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
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.lighting.Dev58B9ElectricLampMarkers;

/**
 * DEV58B9: production electric headlights for ONLY two previously unlit
 * recovered renderer models. Exact viewer-proven lens coordinates; no changes
 * to accepted older locomotive beams or block registrations. Rendering-only.
 * Server ground-light cells are separately owned by Dev58B9RecoveredElectricGroundLights.
 */
public final class Dev58B9RecoveredElectricHeadlights {
    private Dev58B9RecoveredElectricHeadlights() {}
    private static final float MAX_REACH = 12F;

    private static float night(long time) {
        long t = Math.floorMod(time, 24000L);
        if (t < 12000L) return 0F;
        if (t < 13000L) return (t - 12000L) / 1000F;
        if (t < 23000L) return 1F;
        return (24000L - t) / 1000F;
    }

    private static Vec3 world(LegacyPoweredLocomotive e, Matrix4f matrix,
                              float x, float y, float z) {
        Vector3f p = matrix.transformPosition(new Vector3f(x,y,z));
        return e.position().add(p.x(),p.y(),p.z());
    }

    private static void vertex(VertexConsumer v, Matrix4f mat, float x, float y,
                               float z, int a) {
        v.vertex(mat,x,y,z).color(255,232,167,a).endVertex();
    }
    private static void lens(VertexConsumer v, Matrix4f mat,
                             float x,float y,float z,float strength) {
        float h=0.072F;
        int a=(int)(232F*strength);
        // Opposite-facing faces: correct visibility around the lens without
        // moving or erasing any original model cube/UV.
        vertex(v,mat,x,y-h,z-h,a);vertex(v,mat,x,y-h,z+h,a);
        vertex(v,mat,x,y+h,z+h,a);vertex(v,mat,x,y+h,z-h,a);
        vertex(v,mat,x,y+h,z-h,a);vertex(v,mat,x,y+h,z+h,a);
        vertex(v,mat,x,y-h,z+h,a);vertex(v,mat,x,y-h,z-h,a);
    }

    public static void draw(LegacyPoweredLocomotive e, PoseStack pose,
                            MultiBufferSource buffers, float partial,
                            double tx,double ty,double tz,
                            float rx,float ry,float rz,float sx,float sy,float sz) {
        if (e==null || !e.isAlive() || !e.isElectric()) return;
        String id=e.variantId();
        if (!Dev58B9ElectricLampMarkers.supported(id)) return;
        // Preserve the accepted electric operating gates; no always-on creative
        // shortcuts or dependency on retired DEV55 review flags.
        if (!e.isOnPoweredEnergyTrack() && !e.hasPortableBackupRedstone()) return;
        float brightness=traincraft.lighting.LocomotiveLightMode.brightness(e);
        if (brightness<=0.005F) return;
        Matrix4f world=Dev55L19FleetBeams.worldMatrix(
            e.getRenderTrainFacingYaw(partial),e.getPoweredModelLift(),0F,
            tx,ty,tz,rx,ry,rz,sx,sy,sz);
        Vector3f up=world.transformDirection(new Vector3f(0F,1F,0F));
        if (Math.abs(up.y)<0.2F) return;
        Matrix4f screen=pose.last().pose();
        VertexConsumer out=buffers.getBuffer(Dev44TrainLightVisuals.creativeBeamType());
        for (double[] m : Dev58B9ElectricLampMarkers.front(id)) {
            float x=(float)m[0]-0.022F; // outside the selected -X lens face
            float y=(float)m[1], z=(float)m[2];
            Vec3 start=world(e,world,x,y,z);
            float endX=x-MAX_REACH;
            Vec3 noDrop=world(e,world,endX,y,z);
            double floor=e.getY()+0.18D;
            Vec3 downStart=new Vec3(noDrop.x,e.getY()+0.25D,noDrop.z);
            Vec3 downEnd=new Vec3(noDrop.x,e.getY()-6.0D,noDrop.z);
            BlockHitResult terrain=e.level().clip(new ClipContext(
                downStart,downEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if (terrain.getType()==HitResult.Type.BLOCK) {
                double f=terrain.getLocation().y;
                if (f<=e.getY()+0.30D && f>=e.getY()-5.95D) floor=f+0.065D;
            }
            float endY=y+(float)((floor-noDrop.y)/up.y);
            Vec3 end=world(e,world,endX,endY,z);
            double fraction=1D;
            BlockHitResult block=e.level().clip(new ClipContext(
                start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if (block.getType()==HitResult.Type.BLOCK)
                fraction=Math.min(fraction,start.distanceTo(block.getLocation())/
                        Math.max(0.001D,start.distanceTo(end)));
            AABB swept=new AABB(start,end).inflate(0.9D);
            for (Entity target:e.level().getEntities(e,swept,
                other->other!=e && other.isAlive() && !other.isSpectator()
                    && other instanceof LivingEntity && !other.isPassengerOfSameVehicle(e))) {
                var hit=target.getBoundingBox().inflate(0.10D).clip(start,end);
                if (hit.isPresent()) fraction=Math.min(fraction,
                    start.distanceTo(hit.get())/Math.max(0.001D,start.distanceTo(end)));
            }
            fraction=Math.max(0.0233D,Math.min(1D,fraction-0.007D));
            float clippedX=x+(endX-x)*(float)fraction;
            float clippedY=y+(endY-y)*(float)fraction;
            float clippedZ=z;
            lens(out,screen,x,y,z,brightness);
            Dev55L13SoftBeam.ribbon(out,screen,x-0.030F,y,z,
                clippedX,clippedY,clippedZ,0F,1F,1.10F,brightness);
        }
    }
}
