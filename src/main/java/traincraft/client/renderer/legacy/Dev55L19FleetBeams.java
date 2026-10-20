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
import traincraft.debug.Dev55L19Settings;
import java.util.*;

/** DEV55L19: preview-only, model-relative to world-relative SINGLE beam path.
 * Both the drawn ribbon and the block/entity rays derive from this segment.
 * Never touches DEV55L13 4-4-0 or any production entity. */
public final class Dev55L19FleetBeams {
    private Dev55L19FleetBeams(){}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L19-FLEET-RAY");
    private static final ThreadLocal<Matrix4f> WORLD=new ThreadLocal<>();
    private static final Set<String> WARNED=new HashSet<>();
    // DEV55L24: renderer-only beam footprint change for NEW preview beams.
    // This does NOT affect rays, collision, reach, lens placement, or ground cells.
    private static final float DEV55L24_VISUAL_WIDTH_SCALE=1.15F;
    // DEV55L25: front yellow LENS quad for a Climax 2 Creative-review ray.
    // It is coplanar with the true beam start/direction in MODEL space, so a
    // player-adjusted aim (including downward pitch, lateral steering and
    // origin depth) rotates the square with the ray. We NEVER alter mesh faces.
    // This is a rendering-only face: no block lights, rays, or collisions change.
    private static void climax2Lens(VertexConsumer v,Matrix4f pose,Vector3f start,
                                     Vector3f end,float brightness){
        float dx=end.x-start.x,dy=end.y-start.y,dz=end.z-start.z;
        float horizontal=(float)Math.hypot(dx,dz);
        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(horizontal<0.0001F || length<0.0001F)return;
        float nx=dx/length,ny=dy/length,nz=dz/length;
        float rightX=dz/horizontal,rightZ=-dx/horizontal;
        // vertical=cross(normal,right), orthogonal to the tilted ray.
        float upX=-ny*dx/horizontal,upY=horizontal/length,upZ=-ny*dz/horizontal;
        float h=0.072F,px=start.x+nx*0.012F,py=start.y+ny*0.012F,pz=start.z+nz*0.012F;
        int alpha=(int)(236F*brightness);
        float ax=px-rightX*h-upX*h, ay=py-upY*h, az=pz-rightZ*h-upZ*h;
        float bx=px+rightX*h-upX*h, by=py-upY*h, bz=pz+rightZ*h-upZ*h;
        float cx=px+rightX*h+upX*h, cy=py+upY*h, cz=pz+rightZ*h+upZ*h;
        float ex=px-rightX*h+upX*h, ey=py+upY*h, ez=pz-rightZ*h+upZ*h;
        climax2LensVertex(v,pose,ax,ay,az,alpha);
        climax2LensVertex(v,pose,bx,by,bz,alpha);
        climax2LensVertex(v,pose,cx,cy,cz,alpha);
        climax2LensVertex(v,pose,ex,ey,ez,alpha);
        climax2LensVertex(v,pose,ex,ey,ez,alpha);
        climax2LensVertex(v,pose,cx,cy,cz,alpha);
        climax2LensVertex(v,pose,bx,by,bz,alpha);
        climax2LensVertex(v,pose,ax,ay,az,alpha);
    }
    private static void climax2LensVertex(VertexConsumer v,Matrix4f pose,
                                           float x,float y,float z,int alpha){
        v.vertex(pose,x,y,z).color(255,232,161,alpha).endVertex();
    }
    public static void setWorld(Matrix4f transform){WORLD.set(transform);}
    // Recovered trams set their own model pose each render; do not leak that
    // ThreadLocal transform into another renderer after finishing their beams.
    public static void clearWorld(){WORLD.remove();}
    public static Matrix4f worldMatrix(float trainYaw,double lift,float pitch,
            double tx,double ty,double tz,float rx,float ry,float rz,float sx,float sy,float sz){
        Matrix4f m=new Matrix4f().translate(0F,(float)lift,0F)
            .rotateY((float)Math.toRadians(270F-trainYaw))
            .rotateZ((float)Math.toRadians(-pitch))
            .translate((float)tx,(float)ty,(float)tz)
            .rotateX((float)Math.toRadians(rx))
            .rotateY((float)Math.toRadians(ry))
            .rotateZ((float)Math.toRadians(rz))
            .scale(sx,sy,sz);
        return m;
    }
    public static boolean active(Entity e,String model){return Dev55L19Settings.enabled(e)&&Dev55L19Settings.supported(model);}
    private static Vector3f vec(float x,float y,float z){return new Vector3f(x,y,z);}
    private static Vec3 world(Entity e,Matrix4f m,Vector3f v){Vector3f q=m.transformPosition(new Vector3f(v));return e.position().add(q.x(),q.y(),q.z());}
    private static double d(Vector3f v,int axis){return axis==0?v.x:axis==2?v.z:v.y;}
    private record Hit(double fraction,int entityId,String block){}
    private static Hit trace(Entity source,Vec3 start,Vec3 end,Vec3 transverse){
        double nearest=1D;int nearestId=-1;String block="none";
        for(double edge : new double[]{0D,-0.42D,0.42D}){
            Vec3 a=start.add(transverse.scale(edge)),b=end.add(transverse.scale(edge));
            BlockHitResult br=source.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
            if(br.getType()==HitResult.Type.BLOCK){
                double fraction=a.distanceTo(br.getLocation())/Math.max(0.001D,a.distanceTo(b));
                if(fraction<nearest){nearest=fraction;block=br.getBlockPos().toShortString();nearestId=-1;}
            }
            AABB region=new AABB(a,b).inflate(1.2D);
            for(Entity other : source.level().getEntities(source,region,
                     entity->entity!=source && entity.isAlive() && entity instanceof LivingEntity
                         && !entity.isSpectator() && !entity.isPassenger())) {
                var point=other.getBoundingBox().inflate(0.12D).clip(a,b);
                if(point.isPresent()){
                    double fraction=a.distanceTo(point.get())/Math.max(0.001D,a.distanceTo(b));
                    if(fraction<nearest){nearest=fraction;nearestId=other.getId();block="none";}
                }
            }
        }
        return new Hit(Math.max(0.0233D,Math.min(1D,nearest)),nearestId,block);
    }
    /** markers [x,y,z,enabled-front (1/0)]; expected complete verified marker lists. */
    public static boolean render(Entity entity,String rawId,VertexConsumer v,Matrix4f screenPose,
                                 double[][] markers,float brightness){
        String id=Dev55L19Settings.model(rawId);
        if(!active(entity,id))return false;
        Matrix4f model=WORLD.get();
        if(model==null){warn(id,"model-to-world matrix not set; review beam unavailable");return false;}
        try{
            List<Dev55L23RayExport.Ray> published=new ArrayList<>();
            List<Vector3f> points=new ArrayList<>();
            for(double[] a:markers)if(a.length>=3 && (a.length<4||a[3]>0.5D))points.add(vec((float)a[0],(float)a[1],(float)a[2]));
            if(points.isEmpty())return false;
            // The actual BODY is either X-long or Z-long. Front-end marker groups may be
            // on Z-long models (Adler, U57). Don't assume all models are X-forward.
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(Vector3f p:points){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
            double meanX=points.stream().mapToDouble(p->p.x).average().orElse(0D);
            double meanZ=points.stream().mapToDouble(p->p.z).average().orElse(0D);
            // 2-lamp crossbar: the coordinates with smaller spread are longitudinal.
            boolean zForward=(maxX-minX)>1.30D*(maxZ-minZ);
            // Otherwise prefer X longitudinal, except known Z-forward two-lamp mesh.
            if(id.equals("locomotive_steam_adler")||id.equals("locomotive_steam_u57"))zForward=true;
            // DEV55L26: Minetrain's two height-separated original -X headlamps
            // are NOT a Z-forward crossbar. Use their verified -X facing and
            // expose center/left/right independently (previously center twice).
            if(id.equals("locomotive_electric_minetrain"))zForward=false;
            float frontSign=zForward?(meanZ>=0?1F:-1F):(meanX>=0?1F:-1F);
            // If marked front is near negative X but the entire engine has its X origin
            // translated, the actual renderer-specific face override remains available.
            float fX=zForward?0F:frontSign, fZ=zForward?frontSign:0F;
            // Match left/right label to physical lateral position, not CSV ordering.
            double meanSide=zForward?meanX:meanZ;
            List<String> names=new ArrayList<>();
            if(points.size()==1){names.add("center");}
            else {
                for(Vector3f p:points){
                    double sep=(zForward?p.x:p.z)-meanSide;
                    names.add(sep < -0.16D?"left":sep > 0.16D?"right":"center");
                }
            }
            // On 765, two verified markers lie on the centerline. They are real
            // lens references, not two independently aimable CENTER beams.
            // Draw only one center ray, preserving the left/right lamps.
            final boolean berkshire=id.equals("locomotive_steam_berkshire_1225")
                || id.equals("locomotive_steam_berkshire_765");
            // DEV55L22: each Berkshire has one forward lamp cluster represented
            // by THREE independently configurable rays, not new physical lamps.
            // DEV55L24: Climax 2 also has just ONE verified front marker. L23R2
            // previously rendered CENTER only, so edits to LEFT/RIGHT had no
            // visible effect even though /tclightfleet saved those profiles.
            // Expand ONLY this confirmed one-marker Climax 2 review locomotive;
            // all other one-lamp locomotives retain their existing geometry.
            final boolean climax2VirtualTriple=(id.equals("locomotive_steam_climax_2")
                || id.equals("locomotive_electric_tram")
                || id.equals("locomotive_electric_tram_ny")) && points.size()==1;
            if(berkshire || climax2VirtualTriple){
                Vector3f lens=points.get(0);
                if(berkshire){
                    for(Vector3f p:points)
                        if(Math.abs((zForward?p.x:p.z)-meanSide)<Math.abs((zForward?lens.x:lens.z)-meanSide)) lens=p;
                }
                points=new ArrayList<>(List.of(lens,lens,lens));
                names=new ArrayList<>(List.of("center","left","right"));
            }
            // DEV55L26: retain BOTH physically reviewed Minetrain lamps:
            // upper marker drives CENTER, lower marker anchors the separately
            // configurable LEFT and RIGHT virtual rays. No mesh parts moved.
            // Sideways/angle/height/depth now reach the visible renderer and
            // its matching L23 ground-light bridge for all three named rays.
            if(id.equals("locomotive_electric_minetrain") && points.size()==2){
                Vector3f upper=points.get(0).y>=points.get(1).y?points.get(0):points.get(1);
                Vector3f lower=points.get(0).y<points.get(1).y?points.get(0):points.get(1);
                points=new ArrayList<>(List.of(upper,lower,lower));
                names=new ArrayList<>(List.of("center","left","right"));
            }
            Set<String> renderedChannels=new HashSet<>();
            for(int i=0;i<points.size();i++){
                Vector3f marker=points.get(i);String name=names.get(i);
                // DEV55L25: GS4 has TWO front centerline markers and one each
                // left/right. A duplicate center ray made four CSV rows, which
                // L23GroundBridge rejects (maximum THREE roles). The higher
                // of the GS4 center markers is the first and remains selected.
                if((berkshire || id.equals("locomotive_steam_gs4")) && !renderedChannels.add(name))continue;
                Dev55L19Settings.Beam config=Dev55L19Settings.get(id,name);
                float alongX=fX,alongZ=fZ;
                float sideX=zForward?1F:0F,sideZ=zForward?0F:1F;
                if(!config.face().equals("auto")){
                    alongX=config.face().equals("+x")?1F:config.face().equals("-x")?-1F:0F;
                    alongZ=config.face().equals("+z")?1F:config.face().equals("-z")?-1F:0F;
                    sideX=Math.abs(alongX)>0.5F?0F:1F;
                    sideZ=Math.abs(alongZ)>0.5F?0F:1F;
                }
                float defaultAngle=config.mode().equals("angled")?(name.equals("left")?-27F:name.equals("right")?27F:0F):0F;
                double effectiveAngle=defaultAngle+config.angle();
                double radians=Math.toRadians(effectiveAngle);
                double cos=Math.cos(radians),sin=Math.sin(radians);
                float dirX=(float)(alongX*cos+sideX*sin),dirZ=(float)(alongZ*cos+sideZ*sin);
                // Origin depth, lateral and height use ACTUAL WORLD BLOCK units,
                // and the world matrix's physical up sign handles inverted-Y meshes.
                Vector3f forwardWorld=model.transformDirection(vec(alongX,0,alongZ));
                Vector3f sideWorld=model.transformDirection(vec(sideX,0,sideZ));
                Vector3f upWorld=model.transformDirection(vec(0,1,0));
                double forwardScale=Math.max(0.05,Math.hypot(forwardWorld.x(),forwardWorld.z()));
                double lateralScale=Math.max(0.05,Math.hypot(sideWorld.x(),sideWorld.z()));
                if(Math.abs(upWorld.y())<0.2F)throw new IllegalStateException("model Y axis not vertical");
                float ox=marker.x+(float)(alongX*config.depth()/forwardScale+sideX*config.sideways()/lateralScale);
                float oy=marker.y+(float)(config.height()/upWorld.y());
                float oz=marker.z+(float)(alongZ*config.depth()/forwardScale+sideZ*config.sideways()/lateralScale);
                Vector3f from=vec(ox,oy,oz);
                Vector3f localDir=vec(dirX,0,dirZ);
                Vector3f worldDir=model.transformDirection(new Vector3f(localDir));
                double horizontalSpeed=Math.hypot(worldDir.x(),worldDir.z());
                if(horizontalSpeed<0.05D)throw new IllegalStateException("zero horizontal beam direction");
                double localDistance=config.reach()/horizontalSpeed;
                Vector3f end=vec(ox+(float)(dirX*localDistance),oy,oz+(float)(dirZ*localDistance));
                Vec3 startWorld=world(entity,model,from);
                Vec3 endWorldPre=world(entity,model,end);
                // Find actual block-ground elevation at the intended endpoint.
                // Limit to terrain BELOW the engine so a wall/roof never becomes
                // a bogus uphill target. Fall back to near-rail height in voids.
                double desiredY=entity.getY()+0.18D;
                Vec3 downStart=new Vec3(endWorldPre.x,entity.getY()+0.25D,endWorldPre.z);
                Vec3 downEnd=new Vec3(endWorldPre.x,entity.getY()-6.0D,endWorldPre.z);
                BlockHitResult footing=entity.level().clip(new ClipContext(downStart,downEnd,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity));
                if(footing.getType()==HitResult.Type.BLOCK){
                    double floorY=footing.getLocation().y;
                    if(floorY<=entity.getY()+0.30D && floorY>=entity.getY()-5.95D)
                        desiredY=floorY+0.065D;
                }
                // At maximum reach, the visible ribbon END and collision END
                // use this exact same ground-contact coordinate.
                end.y+=(float)((desiredY-endWorldPre.y)/upWorld.y());
                Vec3 endWorld=world(entity,model,end);
                Vector3f sideDir=model.transformDirection(vec(-dirZ,0,dirX));
                Vec3 transverse=new Vec3(sideDir.x(),0D,sideDir.z()).normalize();
                Hit hit=trace(entity,startWorld,endWorld,transverse);
                float fraction=(float)hit.fraction();
                Vector3f clipped=vec(from.x+(end.x-from.x)*fraction,from.y+(end.y-from.y)*fraction,from.z+(end.z-from.z)*fraction);
                Vector3f width=vec(-dirZ,0,dirX);
                // DEV55L24: increase the existing softly feathered ribbon's
                // visual footprint by 15% fleet-wide in the NEW opt-in path.
                // No changes to the actual ray or endpoint passed to L23.
                float visualWidth=(name.equals("center")?1.10F:0.95F)*DEV55L24_VISUAL_WIDTH_SCALE;
                Dev55L13SoftBeam.ribbon(v,screenPose,from.x,from.y,from.z,clipped.x,clipped.y,clipped.z,
                    width.x,width.z,visualWidth,brightness);
                // DEV55L25: draw one small face at the center lens only.
                // This replaces only Climax 2's fixed legacy yellow quad.
                if((id.equals("locomotive_steam_climax_2")
                    || id.equals("locomotive_electric_tram")
                    || id.equals("locomotive_electric_tram_ny")) && name.equals("center"))
                    climax2Lens(v,screenPose,from,end,brightness);
                // DEV55L23: bridge the EXACT world ray used by the visible beam,
                // not a guessed parallel line from the old ground-light renderer.
                published.add(new Dev55L23RayExport.Ray(name,startWorld,startWorld.lerp(endWorld,fraction)));
                if(Dev55L19Settings.diagnostic() && entity.tickCount%40==0
                        && (berkshire || id.equals("locomotive_steam_climax")
                            || id.equals("locomotive_steam_climax_2")))
                    LOG.info("[DEV55L21R2-PATH] id={} entity={} beam={} marker=({},{},{}) upWorldY={} mode={} effectiveHorizontal={} startWorld={} endWorld={} clipped={} block={} living={} renderer=L19_PILOT",
                        id,entity.getId(),name,marker.x,marker.y,marker.z,upWorld.y(),
                        config.mode(),effectiveAngle,startWorld,endWorld,config.reach()*fraction,hit.block(),hit.entityId());
                if(id.equals("locomotive_electric_minetrain") && Dev55L19Settings.diagnostic()
                        && entity.tickCount%80==0)
                    LOG.info("[DEV55L26-MINETRAIN] id={} entity={} beam={} facing=verifiedMinusX effectiveAngle={} sideways={} actualOrigin={} endpoint={} hitBlock={}",
                        id,entity.getId(),name,effectiveAngle,config.sideways(),startWorld,endWorld,hit.block());
                if(id.equals("locomotive_steam_adler") && Dev55L19Settings.diagnostic()
                        && entity.tickCount%40==0)
                    LOG.info("[DEV55L21-ADLER-RAY] entity={} beam={} mode={} effectiveHorizontal={} steering={} preset={} pilotActive=true clipped={} hitBlock={} hitEntity={}",
                        entity.getId(),name,config.mode(),effectiveAngle,config.angle(),defaultAngle,
                        config.reach()*fraction,hit.block(),hit.entityId());
                if(Dev55L19Settings.diagnostic() && entity.tickCount%40==0)
                    LOG.info("[DEV55L19-RAY] id={} entity={} beam={} mode={} maxBlocks={} direction=({}, {}) origin={} end={} fraction={} hitEntity={} hitBlock={} clipBlocks={} depth={} pitchAutoGround={}",
                        id,entity.getId(),name,config.mode(),config.reach(),dirX,dirZ,startWorld,endWorld,
                        fraction,hit.entityId(),hit.block(),config.reach()*fraction,config.depth(),desiredY);
            }
            Dev55L23RayExport.publish(entity,id,published);
            return true;
        }catch(Exception err){warn(id,err.toString());return false;}
    }
    private static synchronized void warn(String id,String reason){if(WARNED.add(id+reason))LOG.warn("[DEV55L19-GUARD] model={} error={} -- returning to original beams",id,reason);}
}
