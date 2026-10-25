package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
import java.util.*;

/** DEV58B10F7: exact user-approved Oct-23 viewer positions and vectors.
 * Unaltered angles, pitch, reach and model Y orientation. Switchable integrated
 * client/runtime TEST only; no model assets, worldgen, physics, or rods touched. */
public final class Dev58B10F7SixViewerLights {
    private Dev58B10F7SixViewerLights() {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV58B10F7-SIX");
    private record Lamp(String type,String role,double x,double y,double z,String axis,
        double yaw,double pitch,double reach,double depth,double side,double height,int downSign){}
    private static final Map<String,List<Lamp>> MODELS=new HashMap<>();
    private static final Set<String> WARNED=new HashSet<>();
    private static void add(String model,Lamp lamp){MODELS.computeIfAbsent(model,k->new ArrayList<>()).add(lamp);}
    static {
        add("locomotive_electric_high_speed_zeroed",new Lamp("headlight","center",-2.25D,-1.2386857342978121D,-0.019109322532481904D,"nx",0D,7D,12D,0D,0D,0D,1));
        add("locomotive_electric_high_speed_zeroed",new Lamp("tail","tail_center",2.25D,-0.483683896049033D,0.0026255298488880793D,"px",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_electric_vl10",new Lamp("headlight","center",-1.5D,-1.9335025139923632D,-0.011975634535255852D,"nx",0D,10.5D,12D,0D,0D,0D,1));
        add("locomotive_electric_vl10",new Lamp("headlight","left",-1.6875D,-1.0607998591855676D,0.34587000158865261D,"nx",-10.5D,7D,10D,0D,0D,0D,1));
        add("locomotive_electric_vl10",new Lamp("headlight","right",-1.6875D,-1.0569087240730137D,-0.3543374818142897D,"nx",10.5D,7D,10D,0D,0D,0D,1));
        add("locomotive_diesel_sd70",new Lamp("headlight","center",0.0066837189369164662D,-1.8720078295395064D,-1.6875D,"nz",0D,10.5D,12D,0D,0D,0D,1));
        add("locomotive_diesel_sd70",new Lamp("headlight","left",-0.2413448263673442D,-0.70697620684780194D,-2.25D,"nz",-10.5D,6D,10D,0D,0D,0D,1));
        add("locomotive_diesel_sd70",new Lamp("headlight","right",0.25426475617069122D,-0.70688676416920748D,-2.25D,"nz",10.5D,6D,10D,0D,0D,0D,1));
        add("locomotive_diesel_sd70",new Lamp("tail","tail_center",0.00093359827426539241D,-1.7305147703876156D,2.1875D,"nz",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_sd70",new Lamp("tail","tail_center",-0.0031234907909747436D,-0.43783426511438939D,2.4375D,"pz",0D,0D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("headlight","center",-1.3374999761581421D,0.38741264733765907D,-0.0084983302368591904D,"nx",0D,10.5D,12D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("headlight","right",-1.3374999761581421D,-0.49433161524668517D,-0.24436891870530028D,"nx",10.5D,7D,10D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("headlight","right",-1.3374999761581419D,-0.68258874202426856D,-0.24436891870530028D,"nx",10.5D,6D,10D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("headlight","left",-1.3374999761581421D,-0.50141226216204426D,0.2345198518215347D,"nx",-10.5D,7D,10D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("headlight","left",-1.3374999761581421D,-0.69205273063345685D,0.23213732062488374D,"nx",-10.5D,6D,10D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("tail","tail_center",2.4625000953674316D,0.38023478379327935D,0.010540536957631193D,"nx",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("tail","tail_left",2.4625000953674316D,-0.50534815928049659D,0.24462199671638465D,"nx",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("tail","tail_left",2.4625000953674321D,-0.68090728158091796D,0.24072063905373878D,"nx",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("tail","tail_right",2.4625000953674316D,-0.50537684924046999D,-0.23134363812641434D,"nx",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_cd742",new Lamp("tail","tail_right",2.4625000953674316D,-0.68873835358336577D,-0.23134363812641434D,"px",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_chme3",new Lamp("tail","tail_center",1.4375D,-1.8883705519006366D,0.0093555620384462301D,"px",0D,10.5D,1.5D,0D,0D,0D,1));
        add("locomotive_diesel_chme3",new Lamp("tail","tail_left",1.4374999999999998D,-1.0028305730307441D,0.18008425607966011D,"px",0D,10.5D,1.5D,0D,0D,0D,1));
        add("locomotive_diesel_chme3",new Lamp("tail","tail_right",1.4375D,-1.0058008772294627D,-0.17909374308345188D,"px",0D,10.5D,1.5D,0D,0D,0D,1));
        add("locomotive_diesel_chme3",new Lamp("headlight","left",-1.4375D,-1.0058076993626024D,0.16740109721917201D,"nx",-10.5D,7D,10D,0D,0D,0D,1));
        add("locomotive_diesel_chme3",new Lamp("headlight","right",-1.4375000000000002D,-1.0034330955239914D,-0.18632356779986134D,"nx",10.5D,7D,10D,0D,0D,0D,1));
        add("locomotive_diesel_chme3",new Lamp("headlight","center",-1.4374999999999998D,-1.8945230378086151D,-0.0070858726722745624D,"nx",0D,10.5D,12D,0D,0D,0D,1));
        add("locomotive_diesel_sd40",new Lamp("tail","tail_center",0.0068700622886793926D,-1.8987259168969901D,-1.6250000000000002D,"nz",0D,10.5D,1.5D,0D,0D,0D,-1));
        add("locomotive_diesel_sd40",new Lamp("tail","tail_right",-0.24149015154840517D,-0.71497495855457571D,-2.25D,"nz",0D,10.5D,1.5D,0D,0D,0D,1));
        add("locomotive_diesel_sd40",new Lamp("tail","tail_left",0.25587747902146757D,-0.71019776558364178D,-2.25D,"nz",0D,10.5D,1.5D,0D,0D,0D,1));
        add("locomotive_diesel_sd40",new Lamp("headlight","center",-0.008185789840463531D,-1.7261869691058109D,2.1875D,"pz",0D,9.5D,12D,0D,0D,0D,1));
    }
    private static boolean switchOn(){return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV58B10F7-SIX-LIGHTS-TEST-ON.flag"));}
    public static boolean active(Entity e,String rawId){return e!=null && e.isAlive()
        && switchOn()
        && MODELS.containsKey(Dev55L19Settings.model(rawId));}
    // Exports and runtime deliberately share this exact algebra:
    // dx=baseX*cos(yaw)-baseZ*sin(yaw), dz=baseX*sin(yaw)+baseZ*cos(yaw)
    // start=(x+dx*depth-dz*side,y+height,z+dz*depth+dx*side)
    // end=start+(dx*cos(pitch),downSign*sin(pitch),dz*cos(pitch))*reach.
    private static float[] axis(String a){return switch(a){
        case "px" -> new float[]{1F,0F};case "nx" -> new float[]{-1F,0F};
        case "pz" -> new float[]{0F,1F};case "nz" -> new float[]{0F,-1F};
        default -> throw new IllegalArgumentException("invalid axis: "+a);};}
    // F7R4: lens planes never borrow a beam's yaw or pitch.
    // Lens axes are local-to-body, not world-facing, so native rotations work.
    // Projected beams still steer independently using each viewer marker's axis/yaw.
    private static float[] straightLensAxis(String model,boolean tail){
        if(model.equals("locomotive_diesel_sd70"))return axis(tail?"pz":"nz");
        if(model.equals("locomotive_diesel_sd40"))return axis(tail?"nz":"pz");
        return axis(tail?"px":"nx");
    }
    private static Vector3f point(Lamp l){return new Vector3f((float)l.x,(float)l.y,(float)l.z);}
    private static Vec3 world(Entity e,Matrix4f world,Vector3f p){
        Vector3f v=world.transformPosition(new Vector3f(p));
        return e.position().add(v.x,v.y,v.z);
    }
    private static void vertex(VertexConsumer v,Matrix4f pose,Vector3f p,int red,int green,int blue,int alpha){
        v.vertex(pose,p.x,p.y,p.z).color(red,green,blue,alpha).endVertex();
    }
    private static void quad(VertexConsumer v,Matrix4f pose,Vector3f a,Vector3f b,Vector3f c,Vector3f d,
            int r,int g,int bl,int alphaA,int alphaB){
        vertex(v,pose,a,r,g,bl,alphaA);vertex(v,pose,b,r,g,bl,alphaA);
        vertex(v,pose,c,r,g,bl,alphaB);vertex(v,pose,d,r,g,bl,alphaB);
        vertex(v,pose,d,r,g,bl,alphaB);vertex(v,pose,c,r,g,bl,alphaB);
        vertex(v,pose,b,r,g,bl,alphaA);vertex(v,pose,a,r,g,bl,alphaA);
    }
    private static void lampFace(VertexConsumer v,Matrix4f pose,Vector3f origin,float dirX,float dirZ,
            boolean red,float brightness){
        float h=.067F;Vector3f p=new Vector3f(origin).add(dirX*.014F,0F,dirZ*.014F);
        Vector3f right=new Vector3f(-dirZ*h,0F,dirX*h);
        Vector3f up=new Vector3f(0,h,0);
        Vector3f a=new Vector3f(p).sub(right).sub(up),b=new Vector3f(p).add(right).sub(up);
        Vector3f c=new Vector3f(p).add(right).add(up),d=new Vector3f(p).sub(right).add(up);
        quad(v,pose,a,b,c,d,red?255:255,red?58:232,red?50:161,(int)(240*brightness),(int)(240*brightness));
    }
    private static double fraction(Entity e,Vec3 start,Vec3 end,double dx,double dz){
        double f=1D;double length=start.distanceTo(end);
        if(length<1e-5D)return 0D;
        for(double offset:new double[]{0D,-.26D,.26D}){
            Vec3 a=start.add(-dz*offset,0,dx*offset),b=end.add(-dz*offset,0,dx*offset);
            BlockHitResult block=e.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(block.getType()==HitResult.Type.BLOCK)
                f=Math.min(f,a.distanceTo(block.getLocation())/Math.max(.001,a.distanceTo(b)));
            AABB scan=new AABB(a,b).inflate(.6D);
            for(Entity other:e.level().getEntities(e,scan,o->o!=e&&o.isAlive()&&!o.isSpectator()
                    && o instanceof LivingEntity&&!o.isPassenger())){
                var clip=other.getBoundingBox().inflate(.08D).clip(a,b);
                if(clip.isPresent())f=Math.min(f,a.distanceTo(clip.get())/Math.max(.001,a.distanceTo(b)));
            }
        }
        return Math.max(.015,Math.min(1D,f));
    }
    public static boolean render(Entity entity,String rawId,VertexConsumer v,Matrix4f pose,
            Matrix4f modelWorld,float brightness){
        String id=Dev55L19Settings.model(rawId);
        if(!active(entity,id))return false;
        if(modelWorld==null){warn(id,"missing model-to-world matrix");return false;}
        try{
            List<Dev55L23RayExport.Ray> publish=new ArrayList<>();
            Set<String> used=new HashSet<>();int headCount=0,tailCount=0;
            int frontIndex=0,rearIndex=0;
            for(Lamp lamp:MODELS.get(id)){
                boolean red=lamp.type.equals("tail");
                int lampIndex=red?++rearIndex:++frontIndex;
                float[] faceBasis=straightLensAxis(id,red);
                float liveDepth=traincraft.lighting.Dev58B10F7R4DepthSettings.offset(
                    id,red?"rear":"front",lampIndex);
                float[] basis=axis(lamp.axis);
                float yaw=(float)Math.toRadians(lamp.yaw);
                float dx=(float)(basis[0]*Math.cos(yaw)-basis[1]*Math.sin(yaw));
                float dz=(float)(basis[0]*Math.sin(yaw)+basis[1]*Math.cos(yaw));
                // F7R4: only beam ribbon gets steering yaw.
                // Both lens placement/depth and lens face normal stay on the
                // locomotive's unsteered front or rear body axis.
                float absoluteDepth=(float)lamp.depth+liveDepth;
                float ox=(float)(lamp.x+faceBasis[0]*absoluteDepth-faceBasis[1]*lamp.side);
                // DEV58B10F7R2 native-model viewer uses pixel Y DOWN and
                // displays it as viewer Y UP (dev58World: -(pixelY)/16).
                // ModelPart.render() consumes native Java Y, so undo ONLY
                // that viewer-space Y inversion before pose transforms.
                // No change to user-exported marker records, pitch or yaw.
                float oy=(float)(-lamp.y-lamp.height);
                float oz=(float)(lamp.z+faceBasis[1]*absoluteDepth+faceBasis[0]*lamp.side);
                Vector3f from=new Vector3f(ox,oy,oz);
                if(entity.tickCount%160==0){
                    LOG.info("[DEV58B10F7R4-LENS] model={} end={} index={} axis={}/{} " +
                        "offset={} square=STRAIGHT beamYaw={} status=OK",id,red?"rear":"front",
                        lampIndex,faceBasis[0],faceBasis[1],liveDepth,lamp.yaw);
                    Vector3f localWorldPoint=modelWorld.transformPosition(new Vector3f(from));
                    LOG.info("[DEV58B10F7R2-ORIGIN] model={} type={} role={} " +
                        "viewerY={} nativeY={} worldY={} coordinate=JAVA_PIXEL_Y " +
                        "status=MAPPED",id,lamp.type,lamp.role,lamp.y,oy,
                        entity.position().y+localWorldPoint.y);
                }
                float pitch=(float)Math.toRadians(lamp.pitch);
                float reach=(float)lamp.reach;
                // DEV58B10F7R1: native recovered models have different world
                // Y parity (CD742's 180-degree X rotation, SD40's reversed
                // scale, and the ordinary upright models). The viewer's
                // beamDownSign is a *preview-coordinate hint*, not Minecraft's
                // world-space Y parity. Applying it directly sent beams UP.
                //
                // Preserve exported lamp XYZ, yaw, pitch and reach verbatim.
                // Convert their downward pitch through this specific model's
                // actual render matrix, making WORLD Y decrease. Account for
                // per-model scaling so the world-space pitch remains correct.
                float hdx=dx*reach*(float)Math.cos(pitch);
                float hdz=dz*reach*(float)Math.cos(pitch);
                Vector3f worldHorizontal=modelWorld.transformDirection(
                    new Vector3f(hdx,0F,hdz));
                float worldRun=(float)Math.hypot(worldHorizontal.x,worldHorizontal.z);
                float localUpWorldY=modelWorld.transformDirection(
                    new Vector3f(0F,1F,0F)).y;
                if(!Float.isFinite(worldRun) || !Float.isFinite(localUpWorldY)
                        || Math.abs(localUpWorldY)<0.25F)
                    throw new IllegalStateException("invalid native beam Y transform, model="+id);
                float downWorld=worldRun*(float)Math.tan(pitch);
                float modelYOffset=-downWorld/localUpWorldY;
                Vector3f end=new Vector3f(ox+hdx,oy+modelYOffset,oz+hdz);
                if(!red && entity.tickCount%160==0){
                    float actualWorldDeltaY=modelWorld.transformDirection(
                        new Vector3f(hdx,modelYOffset,hdz)).y;
                    LOG.info("[DEV58B10F7R1-AIM] model={} role={} markerDownSign={} worldUpY={} modelDeltaY={} worldDeltaY={} pitch={} reach={} status={}",
                        id,lamp.role,lamp.downSign,localUpWorldY,modelYOffset,
                        actualWorldDeltaY,lamp.pitch,lamp.reach,
                        actualWorldDeltaY<=0.001F?"DOWN_PASS":"DIRECTION_HOLD");
                }
                // DEV58B10F4R1: tail lamps remain lens-anchored RED SQUARES.
                // Do not raycast or render a rear beam, even if a viewer tailReach
                // was recorded for its former preview. Preserve exact lamp XYZ.
                if(red){
                    lampFace(v,pose,from,faceBasis[0],faceBasis[1],true,brightness);
                    tailCount++;
                    continue;
                }
                Vec3 wStart=world(entity,modelWorld,from),wEnd=world(entity,modelWorld,end);
                Vector3f dirWorld=modelWorld.transformDirection(new Vector3f(dx,0,dz));
                double mag=Math.hypot(dirWorld.x,dirWorld.z);
                double f=fraction(entity,wStart,wEnd,mag>.001?dirWorld.x/mag:0,mag>.001?dirWorld.z/mag:0);
                Vector3f clipped=new Vector3f(from).lerp(end,(float)f);
                lampFace(v,pose,from,faceBasis[0],faceBasis[1],false,brightness);
                {
                    headCount++;
                    Dev55L13SoftBeam.ribbon(v,pose,from.x,from.y,from.z,clipped.x,clipped.y,clipped.z,
                        -dz,dx,lamp.role.equals("center")?1.05F:.90F,brightness);
                    // L23's guarded singleplayer endpoint protocol has at most 3 roles.
                    // Draw ALL real headlamps, including stacked centers, but publish
                    // each role only once to prevent malformed multi-center ray files.
                    if(used.add(lamp.role) && Set.of("center","left","right").contains(lamp.role))
                        publish.add(new Dev55L23RayExport.Ray(lamp.role,wStart,wStart.lerp(wEnd,f)));
                }
            }
            Dev55L23RayExport.publish(entity,id,publish);
            if(entity.tickCount%100==0)
                LOG.info("[DEV58B10F7-SIX-LIGHTS-RUNTIME] model={} entity={} headlamps={} tails={} rays={} settings=EXACT_VIEWER pitch=EXPORTED reach=EXPORTED center12Override=OFF",
                    id,entity.getId(),headCount,tailCount,publish.size());
            return true;
        }catch(Exception e){warn(id,e.toString());return false;}
    }
    private static synchronized void warn(String id,String why){if(WARNED.add(id+why))
        LOG.error("[DEV58B10F7-SIX-LIGHTS-HOLD] model={} reason={} (no unsafe override)",id,why);}
    public static int headlightCount(String id){return (int)MODELS.getOrDefault(Dev55L19Settings.model(id),List.of()).stream().filter(l->l.type.equals("headlight")).count();}
    public static int tailCount(String id){return (int)MODELS.getOrDefault(Dev55L19Settings.model(id),List.of()).stream().filter(l->l.type.equals("tail")).count();}

    /** Isolated test overlay for the six newly reviewed native Java models. */
    public static boolean draw(traincraft.entity.train.power.LegacyPoweredLocomotive e,
            com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers,float partial,
            double tx,double ty,double tz,float rx,float ry,float rz,float sx,float sy,float sz){
        if(e==null || !active(e,e.variantId())
                || !traincraft.lighting.LocomotiveLightMode.enabled(e))return false;
        Matrix4f modelWorld=Dev55L19FleetBeams.worldMatrix(
            e.getRenderTrainFacingYaw(partial),e.getPoweredModelLift(),0F,
            tx,ty,tz,rx,ry,rz,sx,sy,sz);
        VertexConsumer lightBuffer=buffers.getBuffer(Dev44TrainLightVisuals.creativeBeamType());
        return render(e,e.variantId(),lightBuffer,pose.last().pose(),modelWorld,
            traincraft.lighting.LocomotiveLightMode.brightness(e));
    }
}
