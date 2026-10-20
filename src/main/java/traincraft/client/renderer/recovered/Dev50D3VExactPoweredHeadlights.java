package traincraft.client.renderer.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** DEV50d3X2: exact-powered lamps with depth-safe beam, downward ground aim and matched entity/block raycasts.
 * Source markers from verified DEV44 CSV; no real block-light changes. The accepted DEV44 steam
 * helper is not altered. Short DEV50d3V beam is REPLACED rather than drawn a second time.
 */
public final class Dev50D3VExactPoweredHeadlights {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV50D3X2-LIGHT");
    private record Marker(float x, float y, float z, boolean front) { }
    private record Spec(String sha256, Marker[] markers) { }
    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final Map<String, Boolean> VALID = new HashMap<>();
    private static final Map<UUID, Long> LAST_LOG = new HashMap<>();
    // Match the accepted DEV44 Fix 13a beam layer: translucent COLOR writes only.
    // RenderType.lightning() writes depth and can hide mobs behind a transparent beam.
    private static final class NonOccludingBeamStates extends RenderStateShard {
        private NonOccludingBeamStates() { super("tc_d3x2_beam_state", () -> {}, () -> {}); }
        static RenderType.CompositeState state() {
            return RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false);
        }
    }
    private static final RenderType LIGHT_PASS = RenderType.create(
            "traincraft_powered_beam_non_occluding_d3x2", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, NonOccludingBeamStates.state());
    static {
        SPECS.put("minetrain", new Spec("416ABD4B7AA4F0649CA7BCB62EA27FB820EA19DC3B82FE810454EB5DA6738378", new Marker[]{new Marker(-0.625000F,1.196559F,-0.006078F,true), new Marker(-0.750000F,0.223344F,-0.005876F,true)}));
        SPECS.put("kof", new Spec("485257F00C3A82A95D60E1C375A69DEA97AEA32AC7E8A05DBC3FBF4F11CB13FE", new Marker[]{new Marker(1.500000F,1.379651F,0.002504F,false), new Marker(1.500000F,0.815910F,0.487693F,false), new Marker(1.500000F,0.815844F,-0.498823F,false), new Marker(-1.000000F,1.507300F,-0.000605F,true), new Marker(-1.250000F,0.818896F,-0.499554F,true), new Marker(-1.250000F,0.825700F,0.499806F,true)}));
        SPECS.put("gp7", new Spec("4EC69B70405C9811E41F3AA158AEF127D544F8E340ACE2927EBED7227A60E3B5", new Marker[]{new Marker(-1.500000F,1.695231F,-0.003727F,true), new Marker(-1.687500F,0.753299F,-0.311989F,true), new Marker(-1.687500F,0.746498F,0.312270F,true), new Marker(1.500000F,1.702026F,-0.003675F,false), new Marker(1.687500F,0.750225F,0.319723F,false), new Marker(1.687500F,0.746855F,-0.310319F,false)}));
        SPECS.put("br_e69", new Spec("D1CF68200D8093B14C1A3A1F0B9124CA28CC1C2A11107D5C71D32CCD5E127909", new Marker[]{new Marker(1.375000F,0.817025F,0.373818F,true), new Marker(1.375000F,0.817775F,-0.379921F,true), new Marker(1.437500F,0.543959F,0.345223F,true), new Marker(1.437500F,0.538652F,-0.349339F,true), new Marker(-1.375000F,0.816324F,-0.373059F,false), new Marker(-1.437500F,0.535451F,-0.348094F,false), new Marker(-1.437500F,0.537129F,0.346730F,false), new Marker(-1.375000F,0.817893F,0.374734F,false)}));
        SPECS.put("shunter", new Spec("7822BB80666998AC63430B329FA45732F4A26783EB107BC6EDE28CA46F96F254", new Marker[]{new Marker(1.812500F,1.789094F,0.069604F,false), new Marker(1.812500F,0.849066F,0.505696F,false), new Marker(1.812500F,0.846226F,0.069604F,false), new Marker(1.812500F,0.847593F,-0.494979F,false), new Marker(-1.750000F,1.850112F,-0.003245F,true), new Marker(-1.750000F,0.788023F,-0.322426F,true), new Marker(-1.750000F,0.781919F,0.312044F,true)}));
    }
    private Dev50D3VExactPoweredHeadlights() { }

    private static boolean verified(String id, Spec spec) {
        Boolean cached = VALID.get(id);
        if (cached != null) return cached;
        boolean pass = false;
        try (InputStream in = Dev50D3VExactPoweredHeadlights.class.getResourceAsStream(
                "/assets/traincraft/legacy_exact/" + id + ".tcm")) {
            if (in != null) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) digest.update(b,0,n);
                pass = HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(spec.sha256());
            }
        } catch (Exception ex) { LOG.warn("[TC-D3W] mesh verification {} failed: {}",id,ex.toString()); }
        VALID.put(id,pass);
        LOG.info("[TC-D3W] exact mesh={} shaVerified={} markers={}",id,pass,spec.markers().length);
        return pass;
    }
    private static float night(long dayTime) {
        long t = Math.floorMod(dayTime,24000L);
        if (t < 12000L) return 0F;
        if (t < 13000L) return (t-12000L)/1000F;
        if (t < 23000L) return 1F;
        return (24000L-t)/1000F;
    }
    private static boolean powered(LegacyPoweredLocomotive e) {
        if (e.isDiesel()) return e.getPowerAmount() > 0;
        if (e.isElectric()) return e.isOnPoweredEnergyTrack() || e.hasPortableBackupRedstone();
        return false;
    }
    private record Ray(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up, float worldPerLocal) { }
    private record BeamPath(Vec3 delta, float maxWorldLength, float dropLocal) { }
    // All five powered exact meshes are Y-up. Aim full-length beams at an entity-relative
    // rail/ground height, using model-space Y so render vertices and world raycasts agree.
    private static BeamPath beamPath(LegacyPoweredLocomotive source, Ray ray,
                                     float localMax, float lateral) {
        final double targetY = source.getY() + 0.18D;
        final double upY = ray.up().y;
        // If a future model is non-Y-up, stop the beam rather than project through the floor.
        float drop = upY > 0.00001D
                ? (float)Math.max(0D, Math.min(2.5D, (ray.origin().y-targetY)/upY)) : 0F;
        Vec3 delta = ray.forward().scale(ray.worldPerLocal()*localMax)
                .add(ray.right().scale(ray.worldPerLocal()*localMax*lateral*0.509525D))
                .add(ray.up().scale(-drop));
        float length=(float)Math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z);
        return new BeamPath(delta,length,drop);
    }

    /** Apply the SAME train + authored exact-model transforms as ExactPoweredLocomotiveRenderer,
     * with no camera transforms. This is needed for correct block/entity collision tests.
     */
    private static Ray rayFrom(Marker lamp, float dir, LegacyPoweredLocomotive e,
                               double tx,double ty,double tz,float rx,float ry,float rz,
                               float sx,float sy,float sz,float pt) {
        Matrix4f modelToWorld = new Matrix4f()
                .rotateY((float)Math.toRadians(270.0F - e.getRenderTrainFacingYaw(pt)))
                .translate((float)tx,(float)ty,(float)tz)
                .rotateX((float)Math.toRadians(rx))
                .rotateY((float)Math.toRadians(ry))
                .rotateZ((float)Math.toRadians(rz))
                .scale(sx,sy,sz);
        Vector3f start = modelToWorld.transformPosition(new Vector3f(lamp.x(),lamp.y(),lamp.z()));
        Vector3f along = modelToWorld.transformDirection(new Vector3f(dir,0F,0F));
        float scale = along.length();
        if (scale < 0.0001F || !Float.isFinite(scale)) return null;
        along.div(scale);
        Vector3f lateral = modelToWorld.transformDirection(new Vector3f(0F,0F,1F)).normalize();
        Vector3f up = modelToWorld.transformDirection(new Vector3f(0F,1F,0F));
        Vec3 from = new Vec3(e.getX()+start.x,e.getY()+start.y,e.getZ()+start.z);
        return new Ray(from,new Vec3(along.x,along.y,along.z),
                       new Vec3(lateral.x,lateral.y,lateral.z),
                       new Vec3(up.x,up.y,up.z),scale);
    }

    /** DEV44-style independent entity and block stop. Probe the SAME downhill/fanned
     * endpoint as the visible ribbon, including its width. Only LivingEntity (players,
     * mobs) and other Traincraft vehicles block; spectators/passengers are excluded.
     */
    private static float localReach(LegacyPoweredLocomotive source, Ray ray, BeamPath path,
                                    float localMax, String label, boolean log) {
        if(path.maxWorldLength()<0.001F) return 0.28F;
        float capped=localMax;
        String blockStop="NONE",entityStop="NONE";
        int blockHits=0,entityHits=0;
        final Vec3 direction=path.delta().normalize();
        try {
            // Multiple narrow rays avoid a single centre ray overlooking the beam edges.
            // No downward offset is added to the already ground-aimed endpoint.
            for(double sideways : new double[]{-0.42D,0D,0.42D}) {
                for(double height : new double[]{0.12D,0D,-0.12D}) {
                    Vec3 from=ray.origin().add(ray.right().scale(sideways)).add(0D,height,0D)
                                          .add(direction.scale(0.04D));
                    Vec3 to=from.add(path.delta());
                    BlockHitResult block=source.level().clip(new ClipContext(from,to,
                           ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
                    if(block.getType()==HitResult.Type.BLOCK) {
                        float fraction=(float)(from.distanceTo(block.getLocation())/path.maxWorldLength());
                        float candidate=Math.max(0.28F,Math.min(localMax,localMax*fraction-0.16F));
                        if(candidate<capped){capped=candidate;blockStop=String.valueOf(block.getLocation());}
                        blockHits++;
                    }
                }
            }
            for(double sideways : new double[]{-0.52D,0D,0.52D}) {
                for(double height : new double[]{0.14D,0D,-0.14D}) {
                    Vec3 from=ray.origin().add(ray.right().scale(sideways)).add(0D,height,0D)
                                          .add(direction.scale(0.04D));
                    Vec3 to=from.add(path.delta());
                    AABB region=new AABB(from,to).inflate(1.05D);
                    for(Entity target : source.level().getEntities(source,region,other ->
                            other.isAlive() && !other.isSpectator() && other!=source &&
                            (other instanceof LivingEntity ||
                             (ForgeRegistries.ENTITY_TYPES.getKey(other.getType()) != null &&
                              "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(other.getType()).getNamespace()))))) {
                        if(target.isPassengerOfSameVehicle(source)) continue;
                        java.util.Optional<Vec3> hit=target.getBoundingBox().inflate(0.08D).clip(from,to);
                        if(hit.isEmpty()) continue;
                        float fraction=(float)(from.distanceTo(hit.get())/path.maxWorldLength());
                        float candidate=Math.max(0.28F,Math.min(localMax,localMax*fraction-0.30F));
                        if(candidate<capped) {capped=candidate;entityStop=target.getType()+"#"+target.getId();}
                        entityHits++;
                    }
                }
            }
        } catch(Exception ex) {
            LOG.warn("[TC-D3X2] ray error engine={} beam={} error={}",source.getId(),label,ex.toString());
            return 0.28F;
        }
        if(log) LOG.info("[TC-D3X2-RAY] engine={} beam={} origin={} end={} localMax={} localReach={} "
                        +"worldLength={} dropLocal={} blockHits={} entityHits={} blockStop={} entityStop={}",
               source.getId(),label,ray.origin(),ray.origin().add(path.delta()),localMax,capped,
               path.maxWorldLength(),path.dropLocal(),blockHits,entityHits,blockStop,entityStop);
        return capped;
    }

    public static void draw(String meshId, LegacyPoweredLocomotive e,
                            PoseStack pose, MultiBufferSource buffers,
                            double tx,double ty,double tz,float rx,float ry,float rz,
                            float sx,float sy,float sz,float pt) {
        Spec spec = SPECS.get(meshId);
        if (spec == null || !e.isAlive() || !verified(meshId,spec)) return;
        float brightness = night(e.level().getDayTime());
        // DEV55L3: only preview entities have this synced hidden name.
        // Gameplay locomotives still require actual diesel/electric power.
        boolean tcDev55L3ReviewLamp = e.getCustomName() != null
                && "DEV55L3_TEST_LAMPS".equals(e.getCustomName().getString());
        boolean active = (powered(e) || tcDev55L3ReviewLamp) && brightness > 0.005F;
        long bucket=e.level().getGameTime()/60L;
        boolean log = !java.util.Objects.equals(LAST_LOG.put(e.getUUID(),bucket),bucket);
        if (log) LOG.info("[TC-D3X1-GATE] mesh={} entity={} variant={} diesel={} electric={} "
                        + "power={} energyTrack={} backup={} night={} active={} markers={} gameTick={}",
                meshId,e.getId(),e.variantId(),e.isDiesel(),e.isElectric(),
                e.getPowerAmount(),e.isOnPoweredEnergyTrack(),e.hasPortableBackupRedstone(),
                brightness,active,spec.markers().length,e.level().getGameTime());
        if (!active) return;
        VertexConsumer v=buffers.getBuffer(LIGHT_PASS);
        Matrix4f renderPose=pose.last().pose();
        Marker[] ms=spec.markers();
        // DEV55L19: pilot-only powered front lamps, preserving the original red tails.
        String tcL19Id=(meshId.equals("gp7")||meshId.equals("kof")||meshId.equals("shunter"))
                ? "locomotive_diesel_"+meshId : "locomotive_electric_"+meshId;
        if (traincraft.client.renderer.legacy.Dev55L19FleetBeams.active(e,tcL19Id)) {
            double[][] tcL19Marks=new double[ms.length][4];
            for(int i=0;i<ms.length;i++){
                Marker lamp=ms[i];boolean front=meshId.equals("br_e69")?!lamp.front():lamp.front();
                // DEV55L26: new Creative-review red tail face only;
                // preserve reviewed lamp marker XYZ and ALL white beam paths.
                // The rear of these exact powered meshes faces +X; negative
                // depth sinks the red face inward in model coordinates.
                float redDepth=front?0F:traincraft.debug.Dev55L19Settings.getTailDepth(tcL19Id);
                Marker reviewLens=redDepth==0F?lamp:new Marker(lamp.x()+redDepth,lamp.y(),lamp.z(),false);
                face(v,renderPose,reviewLens,front?-1F:1F,brightness,front);
                tcL19Marks[i][0]=lamp.x();tcL19Marks[i][1]=lamp.y();
                tcL19Marks[i][2]=lamp.z();tcL19Marks[i][3]=front?1D:0D;
            }
            traincraft.client.renderer.legacy.Dev55L19FleetBeams.setWorld(
                traincraft.client.renderer.legacy.Dev55L19FleetBeams.worldMatrix(
                    e.getRenderTrainFacingYaw(pt),0D,0F,tx,ty,tz,rx,ry,rz,sx,sy,sz));
            if(traincraft.client.renderer.legacy.Dev55L19FleetBeams.render(
                    e,tcL19Id,v,renderPose,tcL19Marks,brightness))return;
        }
        // DEV50d3X2: keep all verified lens positions and assignments from X1. The accepted Shunter and
        // Minetrain dispatch below is deliberately not changed.
        if (meshId.equals("kof") || meshId.equals("gp7") || meshId.equals("br_e69")) {
            drawMappedPerLamp(meshId,e,v,renderPose,ms,brightness,log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
            return;
        }
        float frontDir=meshId.equals("br_e69") ? 1F : -1F;
        // All original CSV points stay intact. No marker is moved to fake more clearance.
        int main=-1; float best=Float.POSITIVE_INFINITY;
        for(int i=0;i<ms.length;i++) {
            Marker m=ms[i];
            if (!m.front()) continue;
            float score=Math.abs(m.z())*4F - m.y()*0.06F;
            if (score<best) {best=score;main=i;}
        }
        // Red tails are lamp faces only. They never project white beams.
        for(Marker m:ms) face(v,renderPose,m, m.front()?frontDir:-frontDir,
                brightness,m.front());
        // Cart Hauler / Minetrain has TWO distinct marked white lamps at nearly the
        // same Z coordinate. The old center/left/right selector collapsed them into
        // just one. Draw BOTH as independently clipped, straight-ahead beams.
        if (meshId.equals("minetrain")) {
            int projected=0;
            for (int i=0;i<ms.length;i++) {
                if (!ms[i].front()) continue;
                float[] g=cap(e,ms[i],frontDir,0F,12F,"MINETRAIN_"+(i+1),log,
                              tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
                beam(v,renderPose,ms[i],frontDir,0F,brightness,true,g[0],g[1]);
                projected++;
            }
            if(log) LOG.info("[TC-D3X2-BEAMS] mesh=minetrain markedWhite=2 projected={} straight=true", projected);
            return;
        }
        if (main<0) return;
        Marker central=ms[main];
        int left=-1,right=-1;
        float leftSpan=0.22F,rightSpan=0.22F;
        for(int i=0;i<ms.length;i++) {
            if (i==main || !ms[i].front()) continue;
            float dz=ms[i].z()-central.z();
            if (dz < -leftSpan) {left=i;leftSpan=-dz;}
            if (dz > rightSpan) {right=i;rightSpan=dz;}
        }
        float[] centerGeom=cap(e,central,frontDir,0F,12F,"CENTER",log,
                tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        float centerReach=centerGeom[0];
        beam(v,renderPose,central,frontDir,0F,brightness,true,centerReach,centerGeom[1]);
        if(left>=0) {
            float[] g=cap(e,ms[left],frontDir,-1F,10.8F,"LEFT",log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
            beam(v,renderPose,ms[left],frontDir,-1F,brightness,false,g[0],g[1]);
            bridge(v,renderPose,e,central,ms[left],frontDir,0F,-1F,
                    brightness,centerReach,g[0],centerGeom[1],g[1],12F,10.8F,
                    "CENTER_LEFT",log,tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        }
        if(right>=0) {
            float[] g=cap(e,ms[right],frontDir,1F,10.8F,"RIGHT",log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
            beam(v,renderPose,ms[right],frontDir,1F,brightness,false,g[0],g[1]);
            bridge(v,renderPose,e,central,ms[right],frontDir,0F,1F,
                    brightness,centerReach,g[0],centerGeom[1],g[1],12F,10.8F,
                    "CENTER_RIGHT",log,tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        }
    }
    /** DEV50d3X correction: every *marked* headlamp has an independent beam and
     * independent wall / entity obstruction ray. Do not constrain side lamps to
     * the center lamp reach; that previously hid otherwise valid beams.
     *
     * The owner's visual check established that BR E69 front/rear labels in the
     * DEV44 CSV were reversed. Swap only BR E69 labels here; original CSV remains
     * an unmodified source of marker coordinates.
     */
    private static void drawMappedPerLamp(String meshId,LegacyPoweredLocomotive e,
                    VertexConsumer v,Matrix4f renderPose,Marker[] original,
                    float brightness,boolean log,
                    double tx,double ty,double tz,float rx,float ry,float rz,
                    float sx,float sy,float sz,float pt) {
        boolean brE69=meshId.equals("br_e69");
        final float frontDir=-1F; // actual front end for these three exact meshes
        Marker[] placed=new Marker[original.length];
        int frontCount=0,tailCount=0;
        for (int i=0;i<original.length;i++) {
            Marker m=original[i];
            boolean head=brE69 ? !m.front() : m.front();
            // DEV50d3X4 BR E69: retain the runtime-confirmed front/rear role swap,
            // but use the ORIGINAL verified exact-mesh marker position for BOTH
            // the luminous face and its independently ray-clipped beam. X3 had
            // added 0.110-0.205 model units along X, visibly floating rear reds
            // off the panel and shifting white beam origins away from the lenses.
            // Other powered locomotives and all mesh/CSV coordinates are unchanged.
            float x=m.x();
            placed[i]=new Marker(x,m.y(),m.z(),head);
            if (log) LOG.info("[TC-D3X1-MARKER] mesh={} entity={} index={} whiteHead={} "
                             + "originalXYZ=({},{},{}) placedXYZ=({},{},{}) faceDir={} e69Swapped={}",
                    meshId,e.getId(),i+1,head,m.x(),m.y(),m.z(),x,m.y(),m.z(),
                    head?frontDir:-frontDir,brE69);
            // DEV55L26: Creative-review rear RED lens shift ONLY.
            // The accepted powered marker, and every white collision ray, stays frozen.
            // -X is the front for these meshes; the red tail faces +X.
            float sink=(!head && traincraft.debug.Dev55L19Settings.enabled(e))
                ? traincraft.debug.Dev55L19Settings.getTailDepth(e.variantId()) : 0F;
            Marker lens=(!head && sink!=0F)?new Marker(x+sink,m.y(),m.z(),false):placed[i];
            face(v,renderPose,lens,head?frontDir:-frontDir,brightness,head);
            if (head) frontCount++; else tailCount++;
        }
        // The user-reviewed pattern is 3+3 for GP7/Kof, and 4+4 for BR E69.
        int expected=brE69?4:3;
        if (frontCount!=expected || tailCount!=expected) {
            LOG.warn("[TC-D3X] unexpected marker split mesh={} front={} tail={} expected={}; beams disabled",
                     meshId,frontCount,tailCount,expected);
            return;
        }
        int drawn=0;
        float[] reach=new float[placed.length], drop=new float[placed.length];
        float[] fan=new float[placed.length], maxes=new float[placed.length];
        int mid=-1,left=-1,right=-1;
        for (int i=0;i<placed.length;i++) {
            Marker m=placed[i];
            if (!m.front()) continue;
            // Center projects 12 blocks; left/right auxiliary lamps fan outward
            // approximately 27 degrees and reach at most 10.8 blocks.
            float lateral=m.z() < -0.16F ? -1F : (m.z()>0.16F ? 1F:0F);
            boolean central=lateral==0F;
            float max=central?12F:10.8F;
            String label="HEAD_"+(i+1)+"_"+meshId;
            float[] geometry=cap(e,m,frontDir,lateral,max,label,log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
            beam(v,renderPose,m,frontDir,lateral,brightness,central,geometry[0],geometry[1]);
            reach[i]=geometry[0];drop[i]=geometry[1];fan[i]=lateral;maxes[i]=max;
            if(central && (mid<0 || m.y()>placed[mid].y()))mid=i;
            if(lateral<0 && (left<0 || m.y()<placed[left].y()))left=i;
            if(lateral>0 && (right<0 || m.y()<placed[right].y()))right=i;
            drawn++;
            if (log) LOG.info("[TC-D3X2-BEAMS] headlamp={} mesh={} reachLocal={} sideFan={} frontDir={}",
                    i+1,meshId,geometry[0],lateral,frontDir);
        }
        // Faint bridge pieces close the wedge gaps. Every bridge is limited by
        // BOTH adjacent lamps AND an independent midpoint ray, so it does not
        // silently extend through an entity/wall where the main beams have stopped.
        if(mid>=0) {
            if(left>=0) bridge(v,renderPose,e,placed[mid],placed[left],frontDir,
                    fan[mid],fan[left],brightness,reach[mid],reach[left],
                    drop[mid],drop[left],maxes[mid],maxes[left],"MID_LEFT",log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
            if(right>=0) bridge(v,renderPose,e,placed[mid],placed[right],frontDir,
                    fan[mid],fan[right],brightness,reach[mid],reach[right],
                    drop[mid],drop[right],maxes[mid],maxes[right],"MID_RIGHT",log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        } else if(left>=0 && right>=0) {
            // BR E69 has paired lamps on each side, no central marker.
            bridge(v,renderPose,e,placed[left],placed[right],frontDir,
                    fan[left],fan[right],brightness,reach[left],reach[right],
                    drop[left],drop[right],maxes[left],maxes[right],"LEFT_RIGHT",log,
                    tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        }
        if (log) LOG.info("[TC-D3X2-BEAMS] active mesh={} projected={} white={} red={} brE69Swapped={}",
                meshId,drawn,frontCount,tailCount,brE69);
    }

    // Returns {cappedLocalReach, fullLengthLocalDrop}. Geometry scales full drop by
    // cappedReach/max reach; the collision ray uses that same full-length slope.
    private static float[] cap(LegacyPoweredLocomotive e, Marker m,float dir,float side,
                             float max,String name,boolean log,
                             double tx,double ty,double tz,float rx,float ry,float rz,
                             float sx,float sy,float sz,float pt) {
        Ray r=rayFrom(m,dir,e,tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        if(r==null) return new float[]{0.28F,0F};
        BeamPath path=beamPath(e,r,max,side);
        float local=localReach(e,r,path,max,name,log);
        if(log) LOG.info("[TC-D3X2-TRANSFORM] model={} entity={} lamp={} yaw={} origin={} "
                        +"worldEnd={} faceSign={} sideFan={} localCap={} groundDrop={} "
                        +"modelT=({},{},{}) modelRot=({},{},{}) modelScale=({},{},{})",
                e.variantId(),e.getId(),name,e.getRenderTrainFacingYaw(pt),r.origin(),
                r.origin().add(path.delta()),dir,side,local,path.dropLocal(),
                tx,ty,tz,rx,ry,rz,sx,sy,sz);
        return new float[]{local,path.dropLocal()};
    }
    private static void face(VertexConsumer v,Matrix4f mat,Marker m,float dir,float strength,boolean front) {
        float x=m.x()+dir*0.014F,y=m.y(),z=m.z();
        float h=front?0.073F:0.063F;
        int r=front?255:255,g=front?231:36,b=front?172:28,a=(int)(235F*strength);
        quad(v,mat,x,y-h,z-h,x,y-h,z+h,x,y+h,z+h,x,y+h,z-h,r,g,b,a);
        quad(v,mat,x,y+h,z-h,x,y+h,z+h,x,y-h,z+h,x,y-h,z-h,r,g,b,a);
    }
    // Geometry follows the accepted DEV44 double-sided ribbon profile, now grounded at
    // exact powered model Y-up (legacy steam models used inverse-Y).
    private static void beam(VertexConsumer v,Matrix4f mat,Marker m,float dir,float lateral,
                             float brightness,boolean primary,float length,float fullDropLocal) {
        float max=primary?12F:10.8F;
        float reach=Math.max(0.28F,Math.min(max,length));
        float x=m.x(),y=m.y(),z=m.z();
        float side=primary?0F:lateral*reach*0.509525F;
        float endX=x+dir*reach,endZ=z+side;
        float endY=y-fullDropLocal*(reach/max);
        float midY=y+(endY-y)*0.65F;
        float xNear=x+dir*0.035F,xMid=x+dir*reach*0.65F,zMid=z+side*0.65F;
        float width=(primary?1.15F:1.05F)*Math.min(1F,0.3F+reach*0.10F);
        float nearWidth=0.065F;
        float norm=(float)Math.sqrt(reach*reach+side*side);
        float nx=-side/norm,nz=dir*reach/norm;
        int alpha=(int)((primary?39F:30F)*brightness);
        doubleSide(v,mat,
                xNear+nx*nearWidth,y,z+nz*nearWidth,
                xNear-nx*nearWidth,y,z-nz*nearWidth,
                xMid-nx*width*0.78F,midY,zMid-nz*width*0.78F,
                xMid+nx*width*0.78F,midY,zMid+nz*width*0.78F,alpha);
        doubleSide(v,mat,
                xMid+nx*width*0.78F,midY,zMid+nz*width*0.78F,
                xMid-nx*width*0.78F,midY,zMid-nz*width*0.78F,
                endX-nx*width,endY,endZ-nz*width,
                endX+nx*width,endY,endZ+nz*width,(int)(alpha*0.66F));
        doubleSide(v,mat,xNear,y-0.06F,z,xNear,y+0.06F,z,
                endX,endY+0.18F,endZ,endX,endY-0.18F,endZ,(int)(alpha*0.58F));
        if(reach<max-0.10F) {
            float h=0.11F;
            doubleSide(v,mat,endX-dir*0.10F,endY-h,endZ-h,
                    endX-dir*0.10F,endY+h,endZ-h,
                    endX-dir*0.10F,endY+h,endZ+h,
                    endX-dir*0.10F,endY-h,endZ+h,(int)(40F*brightness));
        }
    }
    /** Thin fill between adjacent cone ribbons. Unlike the old decorative fill,
     * this never reaches beyond the shortest neighboring lamp or its own central
     * ground-aimed block/entity ray. It is translucent color-only and cannot hide mobs.
     */
    private static void bridge(VertexConsumer v,Matrix4f mat,LegacyPoweredLocomotive source,
                               Marker a,Marker b,float dir,float fanA,float fanB,float brightness,
                               float reachA,float reachB,float dropA,float dropB,
                               float maxA,float maxB,String label,boolean log,
                               double tx,double ty,double tz,float rx,float ry,float rz,
                               float sx,float sy,float sz,float pt) {
        float span=Math.abs(a.z()-b.z());
        // Avoid bridging two lamps stacked vertically at the same lateral position.
        if(span<0.18F || span>1.8F)return;
        Marker midpoint=new Marker((a.x()+b.x())*0.5F,(a.y()+b.y())*0.5F,
                                   (a.z()+b.z())*0.5F,true);
        float halfMax=Math.min(maxA,maxB);
        float fanMid=(fanA+fanB)*0.5F;
        float[] middle=cap(source,midpoint,dir,fanMid,halfMax,"FILL_"+label,log,
                           tx,ty,tz,rx,ry,rz,sx,sy,sz,pt);
        float distance=Math.min(Math.min(reachA,reachB),middle[0]);
        if(distance<0.42F) return;
        float ax=a.x()+dir*0.12F,bx=b.x()+dir*0.12F;
        float farAx=a.x()+dir*distance,farBx=b.x()+dir*distance;
        float farAz=a.z()+fanA*distance*0.509525F;
        float farBz=b.z()+fanB*distance*0.509525F;
        float farAy=a.y()-dropA*distance/maxA;
        float farBy=b.y()-dropB*distance/maxB;
        // If a near obstacle clips one of the beams, make the bridge end there too.
        doubleSide(v,mat,ax,a.y(),a.z(),bx,b.y(),b.z(),
                   farBx,farBy,farBz,farAx,farAy,farAz,(int)(17F*brightness));
        if(log) LOG.info("[TC-D3X2-FILL] engine={} id={} localReach={} midpointCap={} sideA={} sideB={}",
                         source.getId(),label,distance,middle[0],fanA,fanB);
    }
    private static void doubleSide(VertexConsumer v,Matrix4f m,
                    float ax,float ay,float az,float bx,float by,float bz,
                    float cx,float cy,float cz,float dx,float dy,float dz,int alpha) {
        quad(v,m,ax,ay,az,bx,by,bz,cx,cy,cz,dx,dy,dz,255,224,155,alpha);
        quad(v,m,dx,dy,dz,cx,cy,cz,bx,by,bz,ax,ay,az,255,224,155,alpha);
    }
    private static void quad(VertexConsumer v,Matrix4f m,
                    float ax,float ay,float az,float bx,float by,float bz,
                    float cx,float cy,float cz,float dx,float dy,float dz,
                    int r,int g,int b,int a) {
        vertex(v,m,ax,ay,az,r,g,b,a);
        vertex(v,m,bx,by,bz,r,g,b,a);
        vertex(v,m,cx,cy,cz,r,g,b,a);
        vertex(v,m,dx,dy,dz,r,g,b,a);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,float x,float y,float z,
                               int r,int g,int b,int a) {
        v.vertex(m,x,y,z).color(r,g,b,a).endVertex();
    }
}
