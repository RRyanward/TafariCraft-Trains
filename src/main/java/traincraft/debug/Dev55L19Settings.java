package traincraft.debug;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DEV55L19: per-lamp CSV calibration. Opt-in, review-name only, never edits mesh assets. */
public final class Dev55L19Settings {
    private Dev55L19Settings(){}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L19-FLEET");
    public static final String TAG="DEV55L3_TEST_LAMPS";
    public record Beam(String mode, float angle, float depth, float sideways, float height,
                       float reach, String face) {}
    private static volatile Map<String,Beam> values=Map.of();
    private static volatile long refreshNext=0;
    private static final String HEADER="model,beam,mode,horizontal_degrees,depth_blocks,side_blocks,height_blocks,reach_blocks,face";
    public static Path path(){return FMLPaths.GAMEDIR.get().resolve("DEV55L19-BEAM-PROFILES.csv");}
    public static Path switchPath(){return FMLPaths.GAMEDIR.get().resolve("DEV55L19-FLEET-ON.flag");}
    public static Path debugPath(){return FMLPaths.GAMEDIR.get().resolve("DEV55L19-DIAGNOSTICS.flag");}
    // DEV58B10F1: opt-in, per-project full-fleet calibration. Original Creative
    // preview remains available; normal trains enter ONLY when this explicit
    // switch and the existing DEV55L19 switch are both present.
    public static Path fleetPath(){return FMLPaths.GAMEDIR.get().resolve("DEV58B10-FLEET-CALIBRATION-ON.flag");}
    public static boolean fleetMode(Entity e){return e!=null && e.isAlive()
        && Files.isRegularFile(fleetPath()) && Files.isRegularFile(switchPath());}
    public static boolean enabled(Entity e){return e!=null && Files.isRegularFile(switchPath())
        && (fleetMode(e) || (e.getCustomName()!=null && TAG.equals(e.getCustomName().getString())));}

    public static boolean diagnostic(){return Files.isRegularFile(debugPath());}
    /** DEV55L22 review-only: forbid fallback to the old visible beam renderer.
     * Normal locomotives and protected models never enter the cutover. */
    public static Path newOnlyPath(){return FMLPaths.GAMEDIR.get().resolve("DEV55L22-NEW-BEAMS-ONLY.flag");}
    public static boolean newOnly(){return Files.isRegularFile(newOnlyPath()) || Files.isRegularFile(fleetPath());}

    public static String model(String input){
        String s=input.toLowerCase(Locale.ROOT).trim();
        if(s.startsWith("traincraft:"))s=s.substring(11);
        if(s.startsWith("locomotive_"))return s;
        if(s.equals("britannia"))s="br_britannia";
        if(s.equals("wwcp_062"))s="wwcp_062t";
        if(s.equals("snowplow"))s="snow_plow";
        if(s.equals("wooden_tram") || s.equals("tram"))return "locomotive_electric_tram";
        if(s.equals("ny_tram") || s.equals("tram_ny"))return "locomotive_electric_tram_ny";
        if(s.equals("gp7")||s.equals("kof")||s.equals("shunter"))return "locomotive_diesel_"+s;
        if(s.equals("br_e69")||s.equals("minetrain"))return "locomotive_electric_"+s;
        return "locomotive_steam_"+s;
    }
    public static boolean supported(String model){
        String m=model(model);
        // DEV58 fleet opt-in includes the formerly protected engines so their
        // individual settings can be repaired; original geometry is unchanged.
        if(m.equals("locomotive_steam_vb_shay_2")) return java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58B10F3-VIEWER-TEST-ON.flag")); // guarded viewer test only
        if(m.equals("locomotive_steam_c40"))return false;
        return m.startsWith("locomotive_steam_") || m.equals("locomotive_diesel_gp7") || m.equals("locomotive_diesel_kof") || m.equals("locomotive_diesel_shunter") || m.equals("locomotive_electric_minetrain") || m.equals("locomotive_electric_br_e69")
            || m.equals("locomotive_electric_tram") || m.equals("locomotive_electric_tram_ny");
    }
    public static String name(String side){return side.toLowerCase(Locale.ROOT);}
    // DEV55L26: rear RED lens depth ONLY; a tiny negative offset sinks it.
    // All other beam properties keep their accepted defaults.
    private static Beam defaults(String side){
        if("tail".equals(side))return new Beam("straight",0F,-0.04F,0F,0F,12F,"auto");
        boolean c="center".equals(side);
        return new Beam(c?"straight":"angled",0F,0F,0F,0F,c?12F:10.8F,"auto");
    }
    /** Visual-only, never modifies ray origins, collisions or block light. */
    public static float getTailDepth(String model){return get(model,"tail").depth();}
    private static boolean validTail(Beam b){
        return b.mode().equals("straight") && b.angle()==0F
            && Float.isFinite(b.depth()) && Math.abs(b.depth())<=0.25F
            && b.sideways()==0F && b.height()==0F && b.reach()==12F
            && b.face().equals("auto");
    }
    private static boolean valid(Beam b){return (b.mode.equals("straight")||b.mode.equals("angled")) && Float.isFinite(b.angle) && Math.abs(b.angle)<=85F && Float.isFinite(b.depth)&&Math.abs(b.depth)<=3F && Float.isFinite(b.sideways)&&Math.abs(b.sideways)<=3F && Float.isFinite(b.height)&&Math.abs(b.height)<=3F && Float.isFinite(b.reach)&&b.reach>=2F&&b.reach<=16F && Set.of("auto","+x","-x","+z","-z").contains(b.face);}
    private static void read(){
        long now=System.nanoTime();if(now<refreshNext)return;
        synchronized(Dev55L19Settings.class){if(now<refreshNext)return;
            Map<String,Beam> v=new HashMap<>();
            try {if(Files.isRegularFile(path())){
                for(String line:Files.readAllLines(path(),StandardCharsets.UTF_8)){
                    if(line.isBlank()||line.startsWith("#")||line.startsWith("model,"))continue;
                    String[] c=line.split(",",-1);if(c.length!=9)continue;
                    String m=model(c[0]),s=name(c[1]);if(!supported(m)||!Set.of("center","left","right","tail").contains(s))continue;
                    Beam b=new Beam(c[2],Float.parseFloat(c[3]),Float.parseFloat(c[4]),Float.parseFloat(c[5]),Float.parseFloat(c[6]),Float.parseFloat(c[7]),c[8]);
                    if((s.equals("tail")?validTail(b):valid(b)))v.put(m+"|"+s,b);
                }
            }} catch(Exception err){LOG.warn("[DEV55L19-CONFIG] Read failure: {}",err.toString());}
            values=Map.copyOf(v);refreshNext=now+350_000_000L;
        }
    }
    public static Beam get(String model,String side){read();return values.getOrDefault(model(model)+"|"+name(side),defaults(name(side)));}
    public static synchronized boolean set(String model,String side,Beam value){
        String m=model(model),s=name(side);if(!supported(m)||!Set.of("center","left","right","tail").contains(s)||(s.equals("tail")?!validTail(value):!valid(value)))return false;
        read();Map<String,Beam> v=new HashMap<>(values);v.put(m+"|"+s,value);
        try {StringBuilder data=new StringBuilder(HEADER+"\r\n");
            v.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{String[] k=e.getKey().split("\\|",2);Beam b=e.getValue();data.append(String.format(Locale.ROOT,"%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%s\r\n",k[0],k[1],b.mode,b.angle,b.depth,b.sideways,b.height,b.reach,b.face));});
            Path file=path(),temp=file.resolveSibling(file.getFileName()+".pending");Files.createDirectories(file.getParent());Files.writeString(temp,data.toString(),StandardCharsets.UTF_8);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
            values=Map.copyOf(v);refreshNext=System.nanoTime()+350_000_000L;LOG.info("[DEV55L19-EDIT] model={} beam={} profile={}",m,s,value);return true;
        }catch(Exception err){LOG.warn("[DEV55L19-CONFIG] Write failure: {}",err.toString());return false;}
    }
}
