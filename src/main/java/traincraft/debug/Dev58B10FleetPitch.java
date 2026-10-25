package traincraft.debug;

import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DEV58B10F1 per-lamp downward pitch for the Creative fleet calibration.
 * Separate file leaves every accepted DEV55L19 beam profile byte-for-byte intact.
 * Unset pitch (NaN) retains the existing automatic terrain-ground behavior. */
public final class Dev58B10FleetPitch {
    private Dev58B10FleetPitch(){}
    private static final Logger LOG=LoggerFactory.getLogger("DEV58B10-FLEET-PITCH");
    private static volatile Map<String,Float> values=Map.of();
    private static volatile long refreshNext=0L;
    private static Path path(){return FMLPaths.GAMEDIR.get().resolve("DEV58B10-FLEET-PITCH.csv");}
    private static String key(String model,String role){return Dev55L19Settings.model(model)+"|"+role.toLowerCase(Locale.ROOT);}
    private static void refresh(){
        long now=System.nanoTime();if(now<refreshNext)return;
        synchronized(Dev58B10FleetPitch.class){if(now<refreshNext)return;
            Map<String,Float> rows=new HashMap<>();
            try{if(Files.isRegularFile(path()))for(String line:Files.readAllLines(path(),StandardCharsets.UTF_8)){
                if(line.isBlank()||line.startsWith("model,"))continue;
                String[] cells=line.split(",",-1);if(cells.length!=3)continue;
                String m=Dev55L19Settings.model(cells[0]),r=cells[1].toLowerCase(Locale.ROOT);
                float pitch=Float.parseFloat(cells[2]);
                if(Dev55L19Settings.supported(m)&&Set.of("center","left","right").contains(r)
                    && Float.isFinite(pitch)&&pitch>=-30F&&pitch<=75F)rows.put(key(m,r),pitch);
            }}catch(Exception err){LOG.warn("[DEV58F1-PITCH-READ-HOLD] {}",err.toString());}
            values=Map.copyOf(rows);refreshNext=now+350_000_000L;
        }
    }
    public static float get(String model,String role){refresh();return values.getOrDefault(key(model,role),Float.NaN);}
    public static synchronized boolean set(String model,String role,float pitch){
        String m=Dev55L19Settings.model(model),r=role.toLowerCase(Locale.ROOT);
        if(!Dev55L19Settings.supported(m)||!Set.of("center","left","right").contains(r)
            ||(Float.isFinite(pitch)&&(pitch< -30F||pitch>75F)))return false;
        refresh();Map<String,Float> updated=new HashMap<>(values);
        if(Float.isNaN(pitch))updated.remove(key(m,r));
        else if(Float.isFinite(pitch))updated.put(key(m,r),pitch);
        else return false;
        try{
            StringBuilder csv=new StringBuilder("model,beam,downward_degrees\r\n");
            updated.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
                String[] k=entry.getKey().split("\\|",2);
                csv.append(String.format(Locale.ROOT,"%s,%s,%.3f\r\n",k[0],k[1],entry.getValue()));
            });
            Files.createDirectories(path().getParent());Path temp=path().resolveSibling(path().getFileName()+".writing");
            Files.writeString(temp,csv.toString(),StandardCharsets.UTF_8);
            try{Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException err){Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);}
            values=Map.copyOf(updated);refreshNext=System.nanoTime()+350_000_000L;
            return true;
        }catch(Exception err){LOG.warn("[DEV58F1-PITCH-WRITE-HOLD] {}",err.toString());return false;}
    }
}
