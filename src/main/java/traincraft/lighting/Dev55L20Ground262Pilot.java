package traincraft.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.debug.Dev55L19Settings;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** DEV55L20: 262T ONLY review-world ground hotspot follows the L19 beam's
 * calibrated marker, 12-block destination and clipped ray every SERVER tick.
 * Does not alter normal/fuel-gated fleet lights or the frozen 4-4-0 pilot.
 * This is scoped until other locomotive renderer poses are individually verified. */
public final class Dev55L20Ground262Pilot {
    private Dev55L20Ground262Pilot(){}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L20-GROUND");
    private static final String ID="locomotive_steam_262t";
    private static final float X=-3.375313F,Y=-0.752790F,Z=0.032171F;
    private record Owned(BlockPos pos,long lastSeenTick) {}
    private static final Map<String,Owned> OWNED=new HashMap<>();
    private static boolean hasFlag(String name){return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve(name));}
    public static boolean enabled(){return hasFlag("DEV55L20-262T-GROUND-ON.flag") && hasFlag("DEV55L19-FLEET-ON.flag");}
    public static boolean eligible(Entity e,String id){return ID.equals(id) && enabled() && e!=null
        && e.getCustomName()!=null && Dev55L19Settings.TAG.equals(e.getCustomName().getString())
        && e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW");}
    private static Vec3 w(Entity e,Matrix4f model,Vector3f v){
        Vector3f result=model.transformPosition(new Vector3f(v));
        return e.position().add(result.x(),result.y(),result.z());
    }
    private static float yaw(Entity engine)throws Exception{
        Object value=engine.getClass().getMethod("getRenderTrainFacingYaw",float.class).invoke(engine,0F);
        return ((Number)value).floatValue();
    }
    private static double clip(ServerLevel level,Entity engine,Vec3 from,Vec3 to,Vec3 across){
        double fraction=1D;
        for(double shift:new double[]{0D,-0.42D,0.42D}){
            Vec3 a=from.add(across.scale(shift)),b=to.add(across.scale(shift));
            BlockHitResult wall=level.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
            if(wall.getType()==HitResult.Type.BLOCK)
                fraction=Math.min(fraction,a.distanceTo(wall.getLocation())/Math.max(.001D,a.distanceTo(b)));
            AABB box=new AABB(a,b).inflate(1.2D);
            for(Entity other:level.getEntities(engine,box,ent->ent!=engine && ent.isAlive()
                    && ent instanceof LivingEntity && !ent.isSpectator() && !ent.isPassenger())){
                var contact=other.getBoundingBox().inflate(.12D).clip(a,b);
                if(contact.isPresent())
                    fraction=Math.min(fraction,a.distanceTo(contact.get())/Math.max(.001D,a.distanceTo(b)));
            }
        }
        return Math.max(.0233D,Math.min(1D,fraction));
    }
    private static String owner(ServerLevel level,Entity e){return level.dimension().location()+"/"+e.getUUID();}
    public static boolean apply(ServerLevel level,Entity engine,String id){
        if(!eligible(engine,id))return false;
        String owner=owner(level,engine);
        try {
            Dev55L19Settings.Beam b=Dev55L19Settings.get(ID,"center");
            float heading=yaw(engine);
            if(!Float.isFinite(heading))throw new IllegalStateException("Nonfinite engine yaw");
            // These EXACT 262T renderer transforms come from TwoSixTwoTSteamLocomotiveRenderer.
            Matrix4f pose=new Matrix4f()
                .rotateY((float)Math.toRadians(270F-heading))
                .translate(0F,0.625313F,0F)
                .rotateY((float)Math.PI).rotateZ((float)Math.PI)
                .scale(0.9F,1.0F,0.9F);
            float forwardX=b.face().equals("+x")?1F:-1F;
            float forwardZ=b.face().equals("+z")?1F:b.face().equals("-z")?-1F:0F;
            if(forwardZ!=0F)forwardX=0F;
            double theta=Math.toRadians(b.angle());
            float directionX=(float)(forwardX*Math.cos(theta)-forwardZ*Math.sin(theta));
            float directionZ=(float)(forwardZ*Math.cos(theta)+forwardX*Math.sin(theta));
            Vector3f localForward=new Vector3f(forwardX,0F,forwardZ);
            Vector3f worldForward=pose.transformDirection(new Vector3f(localForward));
            Vector3f worldSide=pose.transformDirection(new Vector3f(-forwardZ,0F,forwardX));
            Vector3f worldUp=pose.transformDirection(new Vector3f(0F,1F,0F));
            double fscale=Math.hypot(worldForward.x(),worldForward.z());
            double sscale=Math.hypot(worldSide.x(),worldSide.z());
            if(fscale<.05D||sscale<.05D||Math.abs(worldUp.y())<.2D)
                throw new IllegalStateException("Invalid 262T pose axes");
            float ox=X+(float)(forwardX*b.depth()/fscale-forwardZ*b.sideways()/sscale);
            float oy=Y+(float)(b.height()/worldUp.y());
            float oz=Z+(float)(forwardZ*b.depth()/fscale+forwardX*b.sideways()/sscale);
            Vector3f localStart=new Vector3f(ox,oy,oz);
            Vector3f dirWorld=pose.transformDirection(new Vector3f(directionX,0F,directionZ));
            double ds=Math.hypot(dirWorld.x(),dirWorld.z());
            if(ds<.05D)throw new IllegalStateException("Zero 262T ray direction");
            float reach=b.reach();
            Vector3f localEnd=new Vector3f(ox+(float)(directionX*reach/ds),oy,oz+(float)(directionZ*reach/ds));
            Vec3 from=w(engine,pose,localStart);
            Vec3 endpointBefore=w(engine,pose,localEnd);
            double floor=engine.getY()+.18D;
            Vec3 downStart=new Vec3(endpointBefore.x,engine.getY()+.25D,endpointBefore.z);
            Vec3 downEnd=new Vec3(endpointBefore.x,engine.getY()-6D,endpointBefore.z);
            BlockHitResult footing=level.clip(new ClipContext(downStart,downEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
            if(footing.getType()==HitResult.Type.BLOCK && footing.getLocation().y<=engine.getY()+.30D
                    && footing.getLocation().y>=engine.getY()-5.95D)floor=footing.getLocation().y+.065D;
            localEnd.y+=(float)((floor-endpointBefore.y)/worldUp.y());
            Vec3 intended=w(engine,pose,localEnd);
            Vector3f side=pose.transformDirection(new Vector3f(-directionZ,0F,directionX));
            Vec3 across=new Vec3(side.x(),0D,side.z()).normalize();
            double fraction=clip(level,engine,from,intended,across);
            Vec3 clipped=from.lerp(intended,fraction);
            BlockPos next=BlockPos.containing(clipped.x,clipped.y+.70D,clipped.z);
            Owned previous=OWNED.get(owner);
            BlockPos before=previous==null?null:previous.pos();
            // Retire first on any move: NEVER let stale endpoint blocks trail the new ray.
            boolean changed=before!=null&&!before.equals(next);
            if(changed){Dev55FleetGroundLights.tcDev55L13Retire(level,before);OWNED.remove(owner);}
            boolean placed=Dev55FleetGroundLights.tcDev55L12Place(level,next);
            if(placed)OWNED.put(owner,new Owned(next,level.getGameTime()));
            else if(!changed && before!=null){Dev55FleetGroundLights.tcDev55L13Retire(level,before);OWNED.remove(owner);}
            if(Dev55L19Settings.diagnostic()&&engine.tickCount%20==0)
                LOG.info("[DEV55L20-GROUND] entity={} origin={} desired={} clipped={} fraction={} oldCell={} newCell={} retired={} placed={} reach={} depth={} cameraIndependent=true",
                    engine.getId(),from,intended,clipped,fraction,before,next,changed,placed,reach,b.depth());
        }catch(Exception ex){
            Owned old=OWNED.remove(owner);
            if(old!=null)Dev55FleetGroundLights.tcDev55L13Retire(level,old.pos());
            if(engine.tickCount%40==0)LOG.warn("[DEV55L20-HOLD] entity={} error={} oldCellRetired={}",engine.getId(),ex.toString(),old!=null);
        }
        return true; // No old 45-tick legacy lights for this marked 262T while ON.
    }
    /** Safely retire only cells owned by this pilot when OFF or the preview disappears. */
    public static void cleanup(ServerLevel level){
        if(OWNED.isEmpty())return;
        boolean on=enabled();
        String prefix=level.dimension().location()+"/";
        long now=level.getGameTime();
        var iterator=OWNED.entrySet().iterator();
        while(iterator.hasNext()){
            var row=iterator.next();
            if(!row.getKey().startsWith(prefix))continue;
            Owned value=row.getValue();
            if(!on || now-value.lastSeenTick()>3L){
                boolean retired=Dev55FleetGroundLights.tcDev55L13Retire(level,value.pos());
                if(Dev55L19Settings.diagnostic())
                    LOG.info("[DEV55L20-RELEASE] owner={} cell={} retired={} reason={}",
                        row.getKey(),value.pos(),retired,on?"preview-absent":"pilot-off");
                iterator.remove();
            }
        }
    }
}
