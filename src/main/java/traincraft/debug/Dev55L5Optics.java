package traincraft.debug;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** DEV55L5: preview-only optical controls. Does not modify train meshes or power. */
public final class Dev55L5Optics {
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L5-OPTICS");
    private static final String TAG="DEV55L3_TEST_LAMPS";
    private static final float AUX_SIDE_SLOPE=0.509525F;
    private static final ThreadLocal<String> ACTIVE=ThreadLocal.withInitial(()->"");
    private static volatile Map<String,Setting> settings=Map.of();
    private static volatile long refreshAfter=0L;
    private static final long REFRESH_NS=350_000_000L;
    private Dev55L5Optics() {}
    public record Setting(float angle,float forward,float sideways,float reach,int axisQuarterTurns) {}
    public static String beam(float lateral){return lateral< -0.1F?"left":lateral>0.1F?"right":"center";}
    private static String key(String model,String beam){return Dev55L4AimTuner.normalize(model)+"|"+beam;}
    public static float defaultAngle(String beam){return beam.equals("left")?-27F:beam.equals("right")?27F:0F;}
    public static float defaultReach(String beam){return beam.equals("center")?12F:10.8F;}
    public static boolean validBeam(String name){return "center".equals(name)||"left".equals(name)||"right".equals(name);}
    private static Path file(){return FMLPaths.GAMEDIR.get().resolve("DEV55L5-LIGHT-GEOMETRY-TEST.csv");}
    public static Path outputFile(){return file();}
    public static boolean supported(String model){return Dev55L4AimTuner.supported(model) && !Dev55L4AimTuner.frozen(model);}
    public static void enter(Entity e,String mesh){
        String model=Dev55L4AimTuner.normalize(mesh);
        ACTIVE.set(e!=null && e.getCustomName()!=null && TAG.equals(e.getCustomName().getString()) && supported(model) ? model : "");
    }
    private static void refresh(){
        long now=System.nanoTime();
        if(refreshAfter!=0L && now<refreshAfter)return;
        synchronized(Dev55L5Optics.class){
            if(refreshAfter!=0L && now<refreshAfter)return;
            Map<String,Setting> copy=new HashMap<>();
            try{
                if(Files.isRegularFile(file()))for(String line:Files.readAllLines(file(),StandardCharsets.UTF_8)){
                    if(line.isBlank()||line.startsWith("#")||line.startsWith("model,"))continue;
                    String[] c=line.split(",",-1);
                    if(c.length!=6 && c.length!=7)continue;
                    String model=Dev55L4AimTuner.normalize(c[0]);String beam=c[1];
                    if(!validBeam(beam)||!supported(model))continue;
                    Setting s=new Setting(Float.parseFloat(c[2]),Float.parseFloat(c[3]),Float.parseFloat(c[4]),Float.parseFloat(c[5]),c.length==7?Integer.parseInt(c[6]):0);
                    if(valid(beam,s))copy.put(key(model,beam),s);
                }
            }catch(Exception ex){LOG.warn("[DEV55L5] optics CSV read: {}",ex.toString());}
            settings=Map.copyOf(copy);refreshAfter=now+REFRESH_NS;
        }
    }
    private static boolean valid(String beam,Setting s){
        return Float.isFinite(s.angle)&&s.angle>=-65F&&s.angle<=65F &&
            Float.isFinite(s.forward)&&Math.abs(s.forward)<=2.0F &&
            Float.isFinite(s.sideways)&&Math.abs(s.sideways)<=2.0F &&
            Float.isFinite(s.reach)&&s.reach>=2F&&s.reach<=16F &&
            s.axisQuarterTurns>=-1&&s.axisQuarterTurns<=1;
    }
    public static Setting get(String model,String beam){
        refresh();return settings.getOrDefault(key(model,beam),new Setting(defaultAngle(beam),0F,0F,defaultReach(beam),0));
    }
    public static synchronized boolean set(String model,String beam,Setting next){
        model=Dev55L4AimTuner.normalize(model);
        if(!validBeam(beam)||!supported(model)||!valid(beam,next))return false;
        refresh();Map<String,Setting> copy=new HashMap<>(settings);
        if(next.equals(new Setting(defaultAngle(beam),0F,0F,defaultReach(beam),0)))copy.remove(key(model,beam));
        else copy.put(key(model,beam),next);
        try{
            Path target=file();Files.createDirectories(target.getParent());
            StringBuilder sb=new StringBuilder("model,beam,horizontal_degrees,origin_forward_blocks,origin_side_blocks,reach_blocks,axis_quarter_turns\r\n");
            copy.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
                String[] id=e.getKey().split("\\|",2); Setting s=e.getValue();
                sb.append(id[0]).append(',').append(id[1]).append(',').append(String.format(Locale.ROOT,"%.3f,%.3f,%.3f,%.3f,%d\r\n",s.angle,s.forward,s.sideways,s.reach,s.axisQuarterTurns));
            });
            Path temp=target.resolveSibling(target.getFileName()+".pending");
            Files.writeString(temp,sb.toString(),StandardCharsets.UTF_8);
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            settings=Map.copyOf(copy);refreshAfter=System.nanoTime()+REFRESH_NS;
            LOG.info("[DEV55L5] model={} beam={} horizontal={} forward={} lateral={} reach={}",model,beam,next.angle,next.forward,next.sideways,next.reach);
            return true;
        }catch(Exception ex){LOG.warn("[DEV55L5] optics CSV write: {}",ex.toString());return false;}
    }
    private static Setting active(float lateral){
        String model=ACTIVE.get();return model.isEmpty()?new Setting(defaultAngle(beam(lateral)),0F,0F,defaultReach(beam(lateral)),0):get(model,beam(lateral));
    }
    public static float reach(float lateral,float original){return ACTIVE.get().isEmpty()?original:active(lateral).reach();}
    public static float originForward(float lateral){return ACTIVE.get().isEmpty()?0F:active(lateral).forward();}
    public static float originSide(float lateral){return ACTIVE.get().isEmpty()?0F:active(lateral).sideways();}
    /** Rotates the entire rendered beam direction; zero is original L5 unchanged. */
    public static boolean turned(float lateral){return !ACTIVE.get().isEmpty() && active(lateral).axisQuarterTurns()!=0;}
    public static float forwardFactor(float lateral){
        if(!turned(lateral))return 1F;
        return active(lateral).axisQuarterTurns()==1 ? -sideSlope(lateral) : sideSlope(lateral);
    }
    public static float sidewaysFactor(float lateral){
        if(!turned(lateral))return sideSlope(lateral);
        return active(lateral).axisQuarterTurns()==1 ? 1F : -1F;
    }
    public static float sideSlope(float lateral){
        if(ACTIVE.get().isEmpty())return lateral*AUX_SIDE_SLOPE;
        Setting s=active(lateral); float degrees=defaultAngle(beam(lateral));
        return Math.abs(s.angle()-degrees)<0.0001F ? lateral*AUX_SIDE_SLOPE
            : (float)Math.tan(Math.toRadians(s.angle()));
    }
}
