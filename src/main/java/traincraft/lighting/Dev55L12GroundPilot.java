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
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.debug.Dev55L4AimTuner;
import traincraft.debug.Dev55L5Optics;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * DEV55L12: FIRST-ENGINE GROUND-LIGHT PILOT ONLY.
 * The world ray uses the camera-independent math proven by DEV55L10 for the
 * UNRAILED /tclightreview 4-4-0. Never used for the two accepted Berkshires,
 * 262T, the VB Shays, ordinary trains, or any other fleet member.
 *
 * Both the geometry and calibration are read on the server, from the same
 * DEV55L4/L5 CSVs as the preview renderer. No client classes loaded here.
 */
public final class Dev55L12GroundPilot {
    private Dev55L12GroundPilot() {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L12-HOTSPOT");
    private static final String ID="locomotive_steam_4_4_0";
    private static final String FLAG="DEV55L12-HOTSPOT-ON.flag";
    private static final String MARK="DEV55L3_TEST_LAMPS";
    private static final double MARK_X=-0.006162D, MARK_Y=1.634150D, MARK_Z=-1.687500D;
    private static final float DIR=-1F;
    private static final double[] EDGES={0D,-0.38D,0.38D};
    private record Owned(BlockPos pos,long tick) {}
    private static final Map<String,Owned> LAST_HOTSPOT=new HashMap<>();
    private static boolean smoothEnabled(){
        return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L13-SMOOTH-ON.flag"));
    }


    private record Segment(Vec3 from,Vec3 to) {}
    private static Vec3 world44(Entity engine,float yaw,double x,double y,double z){
        // Exact L10: Axis.YP(270-renderYaw), translate(-0.66,0,0), Axis.YP(90).
        double a=Math.toRadians(270.0D-yaw);
        double localX=-0.660000D+z,localZ=-x;
        return engine.position().add(Math.cos(a)*localX+Math.sin(a)*localZ,y,
                -Math.sin(a)*localX+Math.cos(a)*localZ);
    }
    private static float renderYaw(Entity e) throws Exception{
        Object value=e.getClass().getMethod("getRenderTrainFacingYaw",float.class).invoke(e,0F);
        float yaw=((Number)value).floatValue();
        if(!Float.isFinite(yaw))throw new IllegalStateException("nonfinite yaw");
        return yaw;
    }
    private static Segment segment(Entity engine,float yaw,double max,
                                   Dev55L5Optics.Setting optics,float aim,double edge){
        double slope=Math.tan(Math.toRadians(optics.angle()));
        // Match L6: the first quarter-turn rotates the actual beam forward axis.
        int axis=optics.axisQuarterTurns();
        double along=axis==0?1D:(axis==1?-slope:slope);
        double sideways=axis==0?slope:(axis==1?1D:-1D);
        double norm=Math.hypot(along,sideways);
        if(norm<0.001D)throw new IllegalStateException("zero beam heading");
        double x0=MARK_X+DIR*optics.forward();
        double z0=MARK_Z+optics.sideways();
        double nx=-sideways/norm,nz=DIR*along/norm;
        double startX=x0+DIR*along*0.035D+nx*edge;
        double startZ=z0+(axis!=0?sideways*0.035D:0D)+nz*edge;
        double endX=x0+DIR*along*max+nx*edge;
        double endZ=z0+sideways*max+nz*edge;
        // The original 4-4-0 model is world-Y-up. L10 uses this exact term.
        double ground=Math.max(MARK_Y+0.40D,Math.max(1.35D,engine.getBbHeight())+0.028D);
        double endY=ground-max*Math.tan(Math.toRadians(aim));
        return new Segment(world44(engine,yaw,startX,MARK_Y,startZ),
                world44(engine,yaw,endX,endY,endZ));
    }
    private static float hitReach(ServerLevel level,Entity engine,float max,
                                  Segment ray,boolean mobs){
        double length=ray.from.distanceTo(ray.to);
        if(!Double.isFinite(length)||length<0.1D||length>max*3D+4D)
            throw new IllegalStateException("invalid pilot segment="+length);
        if(!mobs){
            BlockHitResult hit=level.clip(new ClipContext(ray.from,ray.to,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
            if(hit.getType()!=HitResult.Type.BLOCK)return max;
            double fraction=ray.from.distanceTo(hit.getLocation())/length;
            return (float)Math.max(0.28D,Math.min(max,max*fraction-0.12D));
        }
        float reach=max;
        AABB box=new AABB(ray.from,ray.to).inflate(1.1D);
        for(Entity other:level.getEntities(engine,box,e -> e.isAlive()&&!e.isSpectator()&&e!=engine&&
                (e instanceof LivingEntity || (ForgeRegistries.ENTITY_TYPES.getKey(e.getType())!=null&&
                 "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()))))){
            if(other.isPassengerOfSameVehicle(engine))continue;
            var contact=other.getBoundingBox().inflate(0.16D).clip(ray.from,ray.to);
            if(contact.isEmpty())continue;
            double fraction=ray.from.distanceTo(contact.get())/length;
            reach=Math.min(reach,(float)Math.max(0.28D,Math.min(max,max*fraction-0.30D)));
        }
        return reach;
    }
    /** TRUE means the pilot owns this specific engine this tick; do not place old 7.5-block lights. */
    public static boolean apply(ServerLevel level,Entity engine,String id){
        if(!ID.equals(id) || engine.getCustomName()==null ||
                !MARK.equals(engine.getCustomName().getString()) ||
                !engine.getPersistentData().getBoolean("DEV55LIGHT_REVIEW") ||
                !Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve(FLAG)))return false;
        try{
            Dev55L5Optics.Setting settings=Dev55L5Optics.get(ID,"center");
            float aim=Dev55L4AimTuner.get(ID);
            // Never interpret an uncalibrated sideways profile as accepted world geometry.
            if(settings.axisQuarterTurns()!=-1||aim<=0.1F){
                LOG.warn("[DEV55L12 HOLD] 4-4-0 requires saved axisleft and positive down-aim, got axis={} aim={}",
                        settings.axisQuarterTurns(),aim);
                return true;
            }
            float max=settings.reach();
            float yaw=renderYaw(engine);
            Segment center=segment(engine,yaw,max,settings,aim,0D);
            if(center.from.distanceToSqr(engine.position())>25D)
                throw new IllegalStateException("pilot lamp not near locomotive");
            float reach=max;
            for(double edge:EDGES){
                Segment ray=segment(engine,yaw,max,settings,aim,edge);
                reach=Math.min(reach,hitReach(level,engine,max,ray,false));
                reach=Math.min(reach,hitReach(level,engine,max,ray,true));
            }
            double fraction=Math.max(0D,Math.min(1D,reach/max));
            Vec3 furthest=center.from.lerp(center.to,fraction);
            // One level-13 light at the actual clipped endpoint. No earlier
            // equally bright 2.5/5/7.5 blocks competing with the hotspot.
            BlockPos point=BlockPos.containing(furthest.x,furthest.y+0.70D,furthest.z);
            int placed=Dev55FleetGroundLights.tcDev55L12Place(level,point)?1:0;
            // DEV55L13: the previous pilot source is no longer allowed to shine
            // beyond the current collision point. Only retire OUR last placed cell.
            final String owner=level.dimension().location().toString()+"/"+engine.getUUID();
            if(smoothEnabled()){
                Owned previous=LAST_HOTSPOT.get(owner);
                if(placed!=0) LAST_HOTSPOT.put(owner,new Owned(point,level.getGameTime()));
                else LAST_HOTSPOT.remove(owner);
                if(previous!=null && !previous.pos().equals(point)
                        && level.getGameTime()-previous.tick()<=60L)
                    Dev55FleetGroundLights.tcDev55L13Retire(level,previous.pos());
            } else LAST_HOTSPOT.remove(owner);
            if(engine.tickCount%60==0)
                LOG.info("[DEV55L12-ALIGN] id={} yaw={} aim={} axis={} source={} end={} reach={}/{} lights={} cameraIndependent=true",
                        engine.getId(),yaw,aim,settings.axisQuarterTurns(),center.from,furthest,reach,max,placed);
            return true;
        }catch(Exception e){
            if(engine.tickCount%60==0)LOG.warn("[DEV55L12 HOLD] Pilot failed closed id={} cause={}",engine.getId(),e.toString());
            return true; // Never fall through to uncalibrated 7.5-block placement while pilot is ON.
        }
    }
}
