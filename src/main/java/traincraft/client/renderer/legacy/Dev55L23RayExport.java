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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Creative-only integrated-client endpoint bridge. File transport is intentionally
 * NOT multiplayer networking and never enables lighting for a real train. */
public final class Dev55L23RayExport {
    private Dev55L23RayExport() {}
    public record Ray(String role,Vec3 from,Vec3 to) {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L23-RAY-EXPORT");
    private static final Map<String,Long> LAST=new ConcurrentHashMap<>();
    public static void publish(Entity engine,String id,List<Ray> rays){
        if(rays.isEmpty() || !traincraft.debug.Dev55L19Settings.enabled(engine) ||
                !Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L23-LIVE-GROUND-ON.flag")))return;
        // Preview NBT is server-only; client customName is synchronized.
        // The server independently checks DEV55LIGHT_REVIEW before placing light.
        long now=System.nanoTime(); String key=engine.getUUID().toString();
        if(now-LAST.getOrDefault(key,0L)<80_000_000L)return;
        LAST.put(key,now);
        try {
            Path folder=FMLPaths.GAMEDIR.get().resolve("DEV55L23-RAYS");Files.createDirectories(folder);
            Path target=folder.resolve(key+".csv");
            StringBuilder out=new StringBuilder("DEV55L23,1,").append(key).append(',')
                    .append(engine.level().dimension().location()).append(',').append(id).append('\n');
            for(Ray ray:rays){
                Vec3 a=ray.from(),b=ray.to();
                if(!Double.isFinite(a.x)||!Double.isFinite(a.y)||!Double.isFinite(a.z)||
                    !Double.isFinite(b.x)||!Double.isFinite(b.y)||!Double.isFinite(b.z))continue;
                out.append(String.format(Locale.ROOT,"%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    ray.role(),a.x,a.y,a.z,b.x,b.y,b.z));
            }
            Path tmp=folder.resolve(key+".writing");
            Files.writeString(tmp,out.toString(),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try {Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        } catch(Exception ex){if(engine.tickCount%80==0)LOG.warn("[DEV55L23-EXPORT-HOLD] model={} engine={} error={}",id,key,ex.toString());}
    }
}
