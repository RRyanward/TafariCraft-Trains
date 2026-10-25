package traincraft.client.renderer.legacy;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

/** DEV58F1: opt-in integrated-client endpoint bridge. File transport is
 * intentionally NOT multiplayer networking; server independently validates. */
public final class Dev55L23RayExport {
    private Dev55L23RayExport() {}
    public record Ray(String role,Vec3 from,Vec3 to) {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L23-RAY-EXPORT");
    private static final Map<String,Long> LAST=new ConcurrentHashMap<>();
    // DEV58G6: File creation/formatting/atomic rename must never block the
    // Minecraft render thread. A single daemon writer coalesces snapshots by
    // entity UUID, keeping the most recent ray in each pending slot. No entity,
    // world, matrix or mutable list is accessed from this worker thread.
    private record Snapshot(String key, Path folder, String model, String dimension,
                            List<Ray> rays, int entityTick) {}
    private static final Object WRITE_LOCK=new Object();
    private static final LinkedHashMap<String,Snapshot> PENDING=new LinkedHashMap<>();
    private static final int MAX_PENDING=128;
    private static boolean writing=false;
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"DEV58G6-Ray-Export-IO");
        thread.setDaemon(true);
        return thread;
    });
    private static void enqueue(Snapshot snapshot){
        boolean start=false;
        synchronized(WRITE_LOCK){
            if(!PENDING.containsKey(snapshot.key()) && PENDING.size()>=MAX_PENDING){
                Iterator<String> oldest=PENDING.keySet().iterator();
                if(oldest.hasNext()){oldest.next();oldest.remove();}
            }
            PENDING.put(snapshot.key(),snapshot);
            if(!writing){writing=true;start=true;}
        }
        if(start){
            try {WRITER.execute(Dev55L23RayExport::drain);}
            catch(RuntimeException ex){
                synchronized(WRITE_LOCK){writing=false;PENDING.clear();}
                LOG.error("[DEV58G6-QUEUE-HOLD] Async writer unavailable",ex);
            }
        }
    }
    private static void drain(){
        for(;;){
            Snapshot snapshot;
            synchronized(WRITE_LOCK){
                Iterator<Map.Entry<String,Snapshot>> it=PENDING.entrySet().iterator();
                if(!it.hasNext()){writing=false;return;}
                snapshot=it.next().getValue();
                it.remove();
            }
            writeSnapshot(snapshot);
        }
    }
    private static void writeSnapshot(Snapshot snapshot){
        try{
            Path folder=snapshot.folder();
            Files.createDirectories(folder);
            Path target=folder.resolve(snapshot.key()+".csv");
            StringBuilder out=new StringBuilder("DEV55L23,1,").append(snapshot.key()).append(',')
                    .append(snapshot.dimension()).append(',').append(snapshot.model()).append('\n');
            for(Ray ray:snapshot.rays()){
                Vec3 a=ray.from(),b=ray.to();
                if(!Double.isFinite(a.x)||!Double.isFinite(a.y)||!Double.isFinite(a.z)||
                    !Double.isFinite(b.x)||!Double.isFinite(b.y)||!Double.isFinite(b.z))continue;
                out.append(String.format(Locale.ROOT,"%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    ray.role(),a.x,a.y,a.z,b.x,b.y,b.z));
            }
            Path tmp=folder.resolve(snapshot.key()+".writing");
            Files.writeString(tmp,out.toString(),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try {Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception ex){
            if(snapshot.entityTick()%80==0)
                LOG.warn("[DEV55L23-EXPORT-HOLD] model={} engine={} error={}",
                    snapshot.model(),snapshot.key(),ex.toString());
        }
    }
    // F7R3: Allow only these six native-model test lamps to publish their actual
    // clipped rays. Ground lighting is a separate opt-in; neither old preview
    // gating nor the six-train headlight marker/beam rendering is modified.
    private static final java.util.Set<String> SIX=java.util.Set.of(
        "locomotive_electric_high_speed_zeroed","locomotive_electric_vl10",
        "locomotive_diesel_sd70","locomotive_diesel_cd742",
        "locomotive_diesel_chme3","locomotive_diesel_sd40");
    private static boolean sixGround(Entity engine,String id){
        java.nio.file.Path root=FMLPaths.GAMEDIR.get();
        return engine!=null && engine.isAlive() && SIX.contains(id)
            && Files.isRegularFile(root.resolve("DEV58B10F7-SIX-LIGHTS-TEST-ON.flag"))
            && Files.isRegularFile(root.resolve("DEV58B10F7R3-SIX-GROUND-ON.flag"));
    }
    public static void publish(Entity engine,String id,List<Ray> rays){
        // Retain the original 44-fleet and independent six-train admission,
        // as well as the existing DEV58G3 125ms per-locomotive cadence.
        // These checks stay on the render thread just as in the accepted build.
        boolean oldEnabled=traincraft.debug.Dev55L19Settings.enabled(engine) &&
                Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L23-LIVE-GROUND-ON.flag"));
        if(rays.isEmpty() || !(oldEnabled || sixGround(engine,id)))return;
        long now=System.nanoTime(); String key=engine.getUUID().toString();
        if(now-LAST.getOrDefault(key,0L)<125_000_000L)return;
        LAST.put(key,now);
        // Freeze client-derived data before enqueue. Vec3 and Ray are records
        // with immutable components; List.copyOf prevents list mutation. The
        // worker only formats and writes these immutable values to disk.
        enqueue(new Snapshot(key,FMLPaths.GAMEDIR.get().resolve("DEV55L23-RAYS"),
                id,engine.level().dimension().location().toString(),List.copyOf(rays),engine.tickCount));
    }
}
