package traincraft.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

/**
 * DEV55L7, REVIEW PREVIEWS ONLY. Uses the actual live, camera-relative renderer
 * PoseStack for both beam geometry and client collision checks. It never moves
 * a headlamp marker and never modifies production train lighting or physics.
 */
public final class Dev55L7PoseClip {
    private static final Logger LOG = LoggerFactory.getLogger("DEV55L7-POSE-CLIP");
    private Dev55L7PoseClip() {}

    /** 262T and accepted VB Shay profiles are deliberately excluded. */
    public static boolean applies(Entity e, String mesh) {
        return e != null && e.getCustomName() != null
                && "DEV55L3_TEST_LAMPS".equals(e.getCustomName().getString())
                && Dev55L5Optics.supported(mesh)
                && !Dev55L4AimTuner.frozen(mesh)
                && !mesh.equals("locomotive_steam_vb_shay")
                && !mesh.equals("locomotive_steam_vb_shay_2");
    }

    /** Same conversion as accepted DEV50d3X6a Shay2 exact-pose ray logic. */
    private static Vec3 worldPoint(Entity e, Matrix4f model, double x,double y,double z) {
        Vector3f v = new Vector3f((float)x,(float)y,(float)z);
        model.transformPosition(v);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 p = camera.add(v.x(), v.y(), v.z());
        // DEV55L8: the far endpoint may legitimately lie beyond the old
        // 25-block *entity-origin* limit after a quarter-turn. Verify numeric
        // coordinates here; validate the true beam start/length as a PAIR
        // in segment(), without rejecting long, correctly aimed endpoints.
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) {
            throw new IllegalStateException("DEV55L8 non-finite renderer point: " + p);
        }
        return p;
    }

    /** Exact 4-4-0 legacy mesh render transform for its unrailed review preview.
     * Unlike the older general camera/PoseStack conversion, this starts from
     * the locomotive WORLD position, not the moving spectator camera.
     * The regular fleet path is unchanged until separately verified.
     */
    private static Vec3 fourFourZeroWorld(Entity entity,double x,double y,double z) {
        try {
            // Renderer: Axis.YP(270 - getRenderTrainFacingYaw), translate(-0.66,0,0), Axis.YP(90).
            java.lang.reflect.Method orientation=entity.getClass().getMethod("getRenderTrainFacingYaw",float.class);
            float trainYaw=((Number)orientation.invoke(entity,0.0F)).floatValue();
            if (!Float.isFinite(trainYaw)) throw new IllegalStateException("non-finite train yaw");
            double a=Math.toRadians(270.0D-trainYaw);
            double localX=-0.660000D + z;  // model +90-degree yaw maps local Z onto local X
            double localZ=-x;
            double worldX=Math.cos(a)*localX+Math.sin(a)*localZ;
            double worldZ=-Math.sin(a)*localX+Math.cos(a)*localZ;
            return entity.position().add(worldX,y,worldZ);
        } catch(Exception ex) {
            throw new IllegalStateException("4-4-0 world pose unavailable: "+ex.toString(),ex);
        }
    }

    /** Exact path of Dev44TrainLightVisuals.beam(), including L4 pitch and L6 axis rotation. */
    private static Segment segment(Entity e, String mesh, Matrix4f model, double x,double y,double z,
                                   float dir,float lateral,float ground,float max,double edge) {
        double along = Dev55L5Optics.forwardFactor(lateral);
        double sideways = Dev55L5Optics.sidewaysFactor(lateral);
        double x0 = x + dir*Dev55L5Optics.originForward(lateral);
        double z0 = z + Dev55L5Optics.originSide(lateral);
        double norm = Math.hypot(along,sideways);
        if (norm < 0.001D) throw new IllegalStateException("zero beam heading");
        // Width is measured orthogonally to the same ribbon direction.
        double nx = -sideways/norm, nz = dir*along/norm;
        double xn = x0 + dir*along*0.035D + nx*edge;
        double zn = z0 + (Dev55L5Optics.turned(lateral) ? sideways*0.035D : 0D) + nz*edge;
        double xf = x0 + dir*along*max + nx*edge;
        double zf = z0 + sideways*max + nz*edge;
        double endY = Math.max(y+0.40D,ground) + max*Dev55L4AimTuner.deltaModelYPerBlock();
        // L10 pilot ONLY for the 4-4-0: camera-independent exact locomotive world transform.
        // All other preview engines retain their previous L9 read-only diagnostic path.
        boolean exact44="locomotive_steam_4_4_0".equals(mesh);
        Vec3 from = exact44 ? fourFourZeroWorld(e,xn,y,zn) : worldPoint(e,model,xn,y,zn);
        Vec3 to = exact44 ? fourFourZeroWorld(e,xf,endY,zf) : worldPoint(e,model,xf,endY,zf);
        if (exact44 && from.distanceToSqr(e.position())>25.0D) {
            throw new IllegalStateException("4-4-0 world lamp outside five-block safety envelope: "+from+" train="+e.position());
        }
        // Validate the lamp's start independently of its far endpoint.
        // This is REVIEW-ONLY and cannot affect accepted production beams.
        double pathLength = from.distanceTo(to);
        if (from.distanceToSqr(e.position()) > 1296.0D) {
            throw new IllegalStateException("DEV55L8 preview lamp start outside entity vicinity start="
                    + from + " entity=" + e.position() + " length=" + pathLength);
        }
        if (!Double.isFinite(pathLength) || pathLength < 0.1D || pathLength > max * 3.0D + 4.0D) {
            throw new IllegalStateException("DEV55L8 invalid renderer beam segment start="
                    + from + " end=" + to + " length=" + pathLength + " max=" + max);
        }
        return new Segment(from,to);
    }
    private record Segment(Vec3 start, Vec3 end) {}
    // Throttle pose warnings: a defective viewer must not generate thousands of
    // identical messages each minute (as L7 did in the 16:35-16:37 capture).
    private static final java.util.concurrent.atomic.AtomicLong NEXT_WARN_NS = new java.util.concurrent.atomic.AtomicLong();
    private static boolean shouldWarn() {
        long now = System.nanoTime(), next = NEXT_WARN_NS.get();
        return now >= next && NEXT_WARN_NS.compareAndSet(next, now + 2_000_000_000L);
    }
    private static boolean traceLog(Entity e) { return e.tickCount % 60 == 0; }

    // DEV55L9 read-only diagnosis. This does not change any collision decisions.
    // Produces at most one detailed sample per 1.5 s and one visual sample per 0.3 s.
    private static final java.util.concurrent.atomic.AtomicLong NEXT_DETAIL_NS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_VISUAL_NS = new java.util.concurrent.atomic.AtomicLong();
    private static boolean due(java.util.concurrent.atomic.AtomicLong next, long nanos) {
        long now=System.nanoTime(), until=next.get();
        return now>=until && next.compareAndSet(until,now+nanos);
    }
    private static boolean sampleDetails() {return due(NEXT_DETAIL_NS,1_500_000_000L);}
    private static boolean sampleVisual() {
        if(!due(NEXT_VISUAL_NS,300_000_000L))return false;
        try {
            return java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV55L9-SHOW-RAYS.flag"));
        }catch(Exception ignore){return false;}
    }
    private static double distPointSegment(Vec3 point, Segment s) {
        Vec3 path=s.end.subtract(s.start);
        double denom=path.lengthSqr();
        double t=denom<1.0e-9?0D:Math.max(0D,Math.min(1D,
                point.subtract(s.start).dot(path)/denom));
        return point.distanceTo(s.start.add(path.scale(t)));
    }
    private static void drawTrace(Segment s,double edge) {
        try {
            net.minecraft.client.multiplayer.ClientLevel world=Minecraft.getInstance().level;
            if(world==null)return;
            for(int k=0;k<=12;k++) {
                Vec3 at=s.start.lerp(s.end,k/12D);
                // Purple-blue spark particles are the COLLISION ray, not the rendered beam.
                world.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        at.x,at.y,at.z,0D,0D,0D);
            }
            if(Math.abs(edge)<0.01D) {
                world.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                        s.start.x,s.start.y,s.start.z,0D,0D,0D);
                world.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        s.end.x,s.end.y,s.end.z,0D,0D,0D);
            }
        }catch(Exception err){if(shouldWarn())LOG.warn("[DEV55L9-VISUAL] {}",err.toString());}
    }
    private static void dumpDetail(Entity source,String mesh,float lateral,float max,
                                   java.util.List<Segment> rays,int nearestId,float reach) {
        try {
            String beam=Dev55L5Optics.beam(lateral);
            Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            LOG.info("[DEV55L9-SCENE] model={} source={} pos={} yaw={} camera={} beam={} requested={} chosen={} hitEntity={}",
                    mesh,source.getId(),source.position(),source.getYRot(),camera,beam,max,reach,nearestId);
            if ("locomotive_steam_4_4_0".equals(mesh) && !rays.isEmpty()) {
                LOG.info("[DEV55L10-WORLD44] source={} start={} distanceFromLocomotive={} cameraIndependent=true chosenReach={} hitEntity={}",
                    source.getId(),rays.get(0).start(),rays.get(0).start().distanceTo(source.position()),reach,nearestId);
            }
            for(int i=0;i<rays.size();i++) {
                Segment ray=rays.get(i);
                LOG.info("[DEV55L9-RAY] index={} edge={} from={} to={} physicalLength={} engineStartDistance={}",
                        i,i==0?0D:i==1?-0.38D:0.38D,ray.start,ray.end,
                        ray.start.distanceTo(ray.end),ray.start.distanceTo(source.position()));
            }
            java.util.List<Entity> nearby=source.level().getEntities(source,
                    new AABB(source.position(),source.position()).inflate(30D),
                    other->other.isAlive() && other!=source && other instanceof LivingEntity);
            nearby.sort(java.util.Comparator.comparingDouble(other->other.distanceToSqr(source)));
            LOG.info("[DEV55L9-NEARBY] source={} livingWithin30={} (first 8 below)",
                    source.getId(),nearby.size());
            for(int i=0;i<Math.min(8,nearby.size());i++) dumpTarget("NEARBY",nearby.get(i),rays);
            net.minecraft.client.player.LocalPlayer player=Minecraft.getInstance().player;
            if(player!=null) {
                LOG.info("[DEV55L9-CLIENT-PLAYER] source={} localPlayerId={} pos={} inLevelCandidates={}",
                        source.getId(),player.getId(),player.position(),
                        nearby.stream().anyMatch(e->e.getId()==player.getId()));
                dumpTarget("CLIENT-PLAYER",player,rays);
            }
        }catch(Exception err){LOG.warn("[DEV55L9-DIAGNOSTIC-ERROR] {}",err.toString());}
    }
    private static void dumpTarget(String category,Entity target,java.util.List<Segment> rays) {
        Vec3 mid=target.getBoundingBox().getCenter();
        java.util.ArrayList<String> probes=new java.util.ArrayList<>();
        for(int i=0;i<rays.size();i++) {
            Segment ray=rays.get(i);
            boolean exact=target.getBoundingBox().clip(ray.start,ray.end).isPresent();
            boolean narrow=target.getBoundingBox().inflate(0.16D).clip(ray.start,ray.end).isPresent();
            boolean wide=target.getBoundingBox().inflate(0.85D).clip(ray.start,ray.end).isPresent();
            probes.add(i+":centerDist="+String.format(java.util.Locale.ROOT,"%.2f",distPointSegment(mid,ray))
                    +" exact="+exact+" narrow="+narrow+" wide="+wide);
        }
        LOG.info("[DEV55L9-TARGET] category={} id={} type={} pos={} bbox={} probes={}",
                category,target.getId(),target.getType(),target.position(),target.getBoundingBox(),probes);
    }

    public static float blocks(Entity e,String mesh,Matrix4f model,double x,double y,double z,
                               float dir,float lateral,float ground,float max) {
        try {
            float reach=max;
            BlockPos nearest=null;
            for(double edge : new double[]{0.0D,-0.38D,0.38D}) {
                Segment ray=segment(e,mesh,model,x,y,z,dir,lateral,ground,max,edge);
                BlockHitResult hit=e.level().clip(new ClipContext(ray.start,ray.end,
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
                if(hit.getType()!=HitResult.Type.BLOCK)continue;
                double fraction=ray.start.distanceTo(hit.getLocation())/
                        Math.max(0.0001D,ray.start.distanceTo(ray.end));
                float candidate=(float)Math.max(0.28D,Math.min(max,max*fraction-0.12D));
                if(candidate<reach){reach=candidate;nearest=hit.getBlockPos();}
            }
            if(traceLog(e))LOG.info("[DEV55L7-POSE-BLOCK] mesh={} id={} side={} block={} reach={} max={}",
                    mesh,e.getId(),Dev55L5Optics.beam(lateral),nearest,reach,max);
            return reach;
        }catch(Exception ex){
            if (shouldWarn()) LOG.warn("[DEV55L8-POSE-BLOCK] FAIL CLOSED mesh={} id={} side={} error={}",
                    mesh,e.getId(),lateral,ex.toString());
            return 0.28F;
        }
    }
    public static float entities(Entity e,String mesh,Matrix4f model,double x,double y,double z,
                                 float dir,float lateral,float ground,float max) {
        try {
            float reach=max;
            int nearestId=-1;
            // L9 observes the existing collision rays without changing their shape,
            // candidate predicate, hitbox inflation, or returned reach.
            boolean detail=sampleDetails();
            boolean visual=sampleVisual();
            java.util.ArrayList<Segment> rays=detail ? new java.util.ArrayList<>() : null;
            for(double edge:new double[]{0.0D,-0.38D,0.38D}) {
                Segment ray=segment(e,mesh,model,x,y,z,dir,lateral,ground,max,edge);
                if(detail)rays.add(ray);
                if(visual)drawTrace(ray,edge);
                AABB bounds=new AABB(ray.start,ray.end).inflate(1.1D);
                for(Entity target:e.level().getEntities(e,bounds,t ->
                        t.isAlive() && !t.isSpectator() && t!=e &&
                        (t instanceof LivingEntity ||
                         (ForgeRegistries.ENTITY_TYPES.getKey(t.getType())!=null &&
                          "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(t.getType()).getNamespace()))))) {
                    if(target.isPassengerOfSameVehicle(e))continue;
                    java.util.Optional<Vec3> hit=target.getBoundingBox().inflate(0.16D)
                            .clip(ray.start,ray.end);
                    if(hit.isEmpty())continue;
                    double fraction=ray.start.distanceTo(hit.get())/
                            Math.max(0.0001D,ray.start.distanceTo(ray.end));
                    float candidate=(float)Math.max(0.28D,Math.min(max,max*fraction-0.30D));
                    if(candidate<reach){reach=candidate;nearestId=target.getId();}
                }
            }
            if(detail)dumpDetail(e,mesh,lateral,max,rays,nearestId,reach);
            if(traceLog(e))LOG.info("[DEV55L7-POSE-ENTITY] mesh={} id={} side={} hitEntity={} reach={} max={}",
                    mesh,e.getId(),Dev55L5Optics.beam(lateral),nearestId,reach,max);
            return reach;
        }catch(Exception ex){
            if (shouldWarn()) LOG.warn("[DEV55L8-POSE-ENTITY] FAIL CLOSED mesh={} id={} side={} error={}",
                    mesh,e.getId(),lateral,ex.toString());
            return 0.28F;
        }
    }
}
