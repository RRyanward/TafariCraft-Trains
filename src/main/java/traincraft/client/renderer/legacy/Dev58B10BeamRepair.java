package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DEV58B10R4: shared-world-pose correction to the R2 exact-face visual repair for ordinary (non-review)
 * U57 and WWCP. The existing DEV55L19 Creative review path is authoritative
 * for review-labelled locomotives and remains byte-for-byte unchanged.
 * Draw geometry uses the original model PoseStack; clipping uses the renderer world pose.
 * No changes to the existing transient ground-light block system, assets,
 * wheel/rod systems, red rear-lamp reviews or train save data.
 */
public final class Dev58B10BeamRepair {
    private Dev58B10BeamRepair() {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV58B10R4-LIGHT");
    private static final Set<String> NOTED=new HashSet<>();
    public static boolean handles(String id) {
        return "locomotive_steam_u57".equals(id)
            || "locomotive_steam_wwcp_062t".equals(id);
    }
    private record Segment(Vector3f from,Vector3f to,float lateralX,float lateralZ,float width) {}

    /** DEV58B10R4: use the same renderer-supplied MODEL-to-WORLD pose as the
     * DEV55L19 preview. Entity-render PoseStack may already include camera/view
     * translation, so adding the camera location to it is not authoritative.
     * Do NOT use screen positions in world collision, wall or floor queries. */
    private static Vec3 worldPoint(Entity engine,Matrix4f modelWorld,float x,float y,float z) {
        Vector3f v=modelWorld.transformPosition(new Vector3f(x,y,z));
        return engine.position().add(v.x(),v.y(),v.z());
    }
    private static double collisionFraction(Entity engine,Vec3 from,Vec3 to,Vec3 transverse) {
        double nearest=1D;
        for(double side:new double[]{0D,-0.28D,0.28D}) {
            Vec3 a=from.add(transverse.scale(side)),b=to.add(transverse.scale(side));
            double distance=a.distanceTo(b);
            if(distance<0.08D)continue;
            BlockHitResult wall=engine.level().clip(new ClipContext(a,b,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
            if(wall.getType()==HitResult.Type.BLOCK)
                nearest=Math.min(nearest,a.distanceTo(wall.getLocation())/distance);
            AABB broad=new AABB(a,b).inflate(.75D);
            for(Entity other:engine.level().getEntities(engine,broad,
                    e->e instanceof LivingEntity && e.isAlive()
                       && !e.isSpectator() && !e.isPassenger())) {
                var point=other.getBoundingBox().inflate(.08D).clip(a,b);
                if(point.isPresent())nearest=Math.min(nearest,a.distanceTo(point.get())/distance);
            }
        }
        return Math.max(.018D,Math.min(1D,nearest));
    }
    private static boolean hasExactLenses(String id,double[][] lamps) {
        if("locomotive_steam_u57".equals(id)) {
            double[][] expected={
                {-.007045D,1.942146D,4.375312D},
                {-.373836D,1.005580D,4.750312D},
                {.367022D,.969763D,4.750312D}};
            return matches(lamps,expected);
        }
        if("locomotive_steam_wwcp_062t".equals(id)) {
            // Two outward-facing -X FRONT lamps. +X pair belongs to the
            // existing DEV55L18 red rear-lamp candidate system, NOT white beams.
            double[][] expected={
                {-2.312813D,-.088706D,-.475094D},
                {-2.312813D,-.090777D,.462587D}};
            return matches(lamps,expected);
        }
        return false;
    }
    private static boolean matches(double[][] actual,double[][] expected) {
        if(actual==null||actual.length!=expected.length)return false;
        boolean[] found=new boolean[actual.length];
        for(double[] e:expected) {
            boolean match=false;
            for(int i=0;i<actual.length;i++) {
                if(found[i]||actual[i]==null||actual[i].length!=3)continue;
                double[] a=actual[i];
                if(Double.isFinite(a[0])&&Double.isFinite(a[1])&&Double.isFinite(a[2])
                   && Math.abs(a[0]-e[0])<.0002D
                   && Math.abs(a[1]-e[1])<.0002D
                   && Math.abs(a[2]-e[2])<.0002D) {
                    found[i]=true;match=true;break;
                }
            }
            if(!match)return false;
        }
        return true;
    }
    private static synchronized void note(String id,String message,boolean failed) {
        if(NOTED.add(id+":"+message)) {
            if(failed)LOG.warn("[DEV58B10R4-BEAM-HOLD] model={} issue={} incorrectLegacyFallback=SUPPRESSED",id,message);
            else LOG.info("[DEV58B10R4-BEAM-ACTIVE] model={} sourceFaces=verified modelWorldClip=ON frameComparison=REMOVED legacyFallback=OFF",id);
        }
    }
    public static boolean draw(Entity engine,String id,VertexConsumer v,
                               Matrix4f modelPose,double[][] lenses,float brightness) {
        if(engine==null||modelPose==null||!handles(id))return false;
        if(!hasExactLenses(id,lenses)) {note(id,"lens count or positions changed",true);return false;}
        try {
            // LegacyMeshRenderer calls setWorld(worldMatrix(...)) immediately
            // before Dev44TrainLightVisuals.draw for THIS entity. Use that
            // absolute model-to-world rotation/translation for collision.
            Matrix4f modelWorld=Dev55L19FleetBeams.currentModelToWorld();
            if(modelWorld==null)
                throw new IllegalStateException("renderer model-to-world pose unavailable");
            Vector3f worldUp=modelWorld.transformDirection(new Vector3f(0F,1F,0F));
            if(!Float.isFinite(worldUp.y())||Math.abs(worldUp.y())<.20F)
                throw new IllegalStateException("model Y not vertical");
            boolean u57="locomotive_steam_u57".equals(id);
            float fx=u57?0F:-1F, fz=u57?1F:0F;
            Vector3f worldForward=modelWorld.transformDirection(new Vector3f(fx,0F,fz));
            double horizontal=Math.hypot(worldForward.x(),worldForward.z());
            if(!Double.isFinite(horizontal)||horizontal<.20D)
                throw new IllegalStateException("invalid model forward axis");
            // DEV58B10R4: DO NOT compare screenPose and modelWorld headings.
            // The screen matrix is in the active camera/render coordinate frame,
            // while modelWorld is camera-independent. The previous .97 dot
            // threshold rejected valid beams when the player moved the camera.
            // Both outputs are still computed from the SAME *local* lamp and
            // local face-normal direction (U57 +Z / WWCP -X). Use modelWorld
            // exclusively for wall, entity and floor tracing, and screenPose
            // exclusively for beam vertices. Preserve all other safety guards.
            Vector3f worldLateral=modelWorld.transformDirection(new Vector3f(-fz,0F,fx));
            Vec3 transverse=new Vec3(worldLateral.x(),0D,worldLateral.z());
            if(transverse.lengthSqr()<.01D)throw new IllegalStateException("bad lateral axis");
            transverse=transverse.normalize();
            List<Segment> segments=new ArrayList<>();
            for(double[] p:lenses) {
                float x=(float)p[0],y=(float)p[1],z=(float)p[2];
                boolean main=u57 && y>1.5F;
                // Both lower U57 lenses and WWCP's two face-normal -X lamps
                // are straight, not arbitrary +/-27 degree crossed rays.
                float reach=main?12F:10.8F;
                float startOffset=.035F/(float)horizontal;
                float length=reach/(float)horizontal;
                float x0=x+fx*startOffset,z0=z+fz*startOffset;
                float x1=x0+fx*length,z1=z0+fz*length;
                Vec3 a=worldPoint(engine,modelWorld,x0,y,z0);
                Vec3 far=worldPoint(engine,modelWorld,x1,y,z1);
                if(!Double.isFinite(a.x)||!Double.isFinite(a.y)||!Double.isFinite(a.z)
                    ||a.distanceTo(engine.position())>10D || !Double.isFinite(far.y))
                    throw new IllegalStateException("model-world lamp outside locomotive vicinity");
                // The actual target ground is probed beneath the far ray.
                double targetY=engine.getY()+.18D;
                BlockHitResult floor=engine.level().clip(new ClipContext(
                    new Vec3(far.x,engine.getY()+.30D,far.z),
                    new Vec3(far.x,engine.getY()-6D,far.z),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
                if(floor.getType()==HitResult.Type.BLOCK
                    && floor.getLocation().y<=engine.getY()+.35D
                    && floor.getLocation().y>=engine.getY()-5.95D)
                    targetY=floor.getLocation().y+.065D;
                float y1=y+(float)((targetY-far.y)/worldUp.y());
                Vec3 b=worldPoint(engine,modelWorld,x1,y1,z1);
                if(!Double.isFinite(b.x)||!Double.isFinite(b.y)||!Double.isFinite(b.z)
                    || a.distanceTo(b)>19D)
                    throw new IllegalStateException("invalid ground-contact ray");
                double clipped=collisionFraction(engine,a,b,transverse);
                segments.add(new Segment(new Vector3f(x0,y,z0),
                    new Vector3f(x0+(x1-x0)*(float)clipped,
                                 y+(y1-y)*(float)clipped,
                                 z0+(z1-z0)*(float)clipped),
                    -fz,fx,main?1.10F:.95F));
            }
            // Draw nothing until ALL rays have been validated and clipped.
            for(Segment s:segments) {
                Vector3f a=s.from(),b=s.to();
                Dev55L13SoftBeam.ribbon(v,modelPose,a.x,a.y,a.z,b.x,b.y,b.z,
                    s.lateralX(),s.lateralZ(),s.width(),brightness);
            }
            note(id,"active",false);
            return true;
        }catch(Exception error) {
            note(id,error.getClass().getSimpleName()+": "+error.getMessage(),true);
            return false;
        }
    }
}
