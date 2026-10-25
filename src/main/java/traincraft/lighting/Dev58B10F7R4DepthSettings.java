package traincraft.lighting;

import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** F7R4 six native-model test offsets. Local singleplayer file-backed only. */
public final class Dev58B10F7R4DepthSettings {
    private Dev58B10F7R4DepthSettings(){}
    private static final Map<String,int[]> COUNTS=Map.of(
        "locomotive_electric_high_speed_zeroed",new int[]{1,1},
        "locomotive_electric_vl10",new int[]{3,0},
        "locomotive_diesel_sd70",new int[]{3,2},
        "locomotive_diesel_cd742",new int[]{5,5},
        "locomotive_diesel_chme3",new int[]{3,3},
        "locomotive_diesel_sd40",new int[]{1,3});
    private static final String HEADER="model,end,index,offset_blocks";
    private static volatile Map<String,Float> values=Map.of();
    private static volatile long refreshAt=0L;
    private static final float LIMIT=0.75F;
    public static Set<String> models(){return COUNTS.keySet();}
    public static int count(String model,String end){
        int[] c=COUNTS.get(model);return c==null?0:("front".equals(end)?c[0]:"rear".equals(end)?c[1]:0);
    }
    private static String key(String model,String end,int idx){return model+"|"+end+"|"+idx;}
    public static Path path(){return FMLPaths.GAMEDIR.get().resolve("DEV58B10F7R4-SIX-DEPTH.csv");}
    private static boolean valid(String model,String end,int idx){return COUNTS.containsKey(model)
        && ("front".equals(end)||"rear".equals(end)) && idx>=0 && idx<=count(model,end)
        && count(model,end)>0;}
    private static Map<String,Float> load(){
        Map<String,Float> result=new TreeMap<>();Path p=path();
        if(!Files.isRegularFile(p))return result;
        try{
            for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)){
                if(line.isBlank()||line.startsWith("#")||line.equals(HEADER))continue;
                String[] cols=line.split(",",-1);
                if(cols.length!=4)continue;
                int idx=Integer.parseInt(cols[2]);float value=Float.parseFloat(cols[3]);
                if(valid(cols[0],cols[1],idx)&&Float.isFinite(value)&&Math.abs(value)<=LIMIT)
                    result.put(key(cols[0],cols[1],idx),value);
            }
        }catch(Exception ex){throw new IllegalStateException("F7R4 invalid depth CSV: "+p,ex);}
        return result;
    }
    private static void reload(){
        long now=System.nanoTime();if(now<refreshAt)return;
        synchronized(Dev58B10F7R4DepthSettings.class){
            if(now<refreshAt)return;
            try{values=Map.copyOf(load());}
            catch(Exception ex){System.err.println("[DEV58B10F7R4-HOLD] "+ex);}
            refreshAt=now+300_000_000L;
        }
    }
    public static float offset(String model,String end,int index){
        if(!valid(model,end,index)||index==0)return 0F;
        reload();Map<String,Float> v=values;
        return v.getOrDefault(key(model,end,0),0F)+v.getOrDefault(key(model,end,index),0F);
    }
    private static void write(Map<String,Float> next) throws Exception {
        StringBuilder sb=new StringBuilder(HEADER+"\r\n");
        for(Map.Entry<String,Float> x:new TreeMap<>(next).entrySet()){
            String[] k=x.getKey().split("\\|",3);
            sb.append(k[0]).append(',').append(k[1]).append(',').append(k[2]).append(',')
                .append(String.format(Locale.ROOT,"%.5f",x.getValue())).append("\r\n");
        }
        Path p=path();Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".pending");
        Files.writeString(tmp,sb.toString(),StandardCharsets.UTF_8);
        try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
        values=Map.copyOf(next);refreshAt=System.nanoTime()+300_000_000L;
    }
    /** Delta moves the existing location, never changes pitch, yaw, or original marker XYZ. */
    public static synchronized float adjust(String model,String end,int index,float delta) throws Exception {
        if(!valid(model,end,index)||!Float.isFinite(delta)||Math.abs(delta)>0.25001F)
            throw new IllegalArgumentException("Invalid model/end/number, or step exceeds 0.25 blocks");
        Map<String,Float> map=load();String k=key(model,end,index);
        float next=map.getOrDefault(k,0F)+delta;
        next=Math.round(next*10000F)/10000F;
        if(Math.abs(next)>LIMIT)throw new IllegalArgumentException("Individual depth limit is +/-0.75 blocks");
        map.put(k,next);
        for(int i=1;i<=count(model,end);i++){
            float effective=map.getOrDefault(key(model,end,0),0F)+map.getOrDefault(key(model,end,i),0F);
            if(Math.abs(effective)>LIMIT)throw new IllegalArgumentException("Combined depth would exceed +/-0.75 blocks at lamp "+i);
        }
        write(map);return next;
    }
    public static synchronized void reset(String model,String end,int index) throws Exception {
        if(!valid(model,end,index))throw new IllegalArgumentException("No such train/side/lamp");
        Map<String,Float> map=load();map.remove(key(model,end,index));
        if(index==0)for(int i=1;i<=count(model,end);i++)map.remove(key(model,end,i));
        write(map);
    }
    public static synchronized String status(String model,String end){
        if(!COUNTS.containsKey(model)||count(model,end)==0)return model+" / "+end+" NO LAMPS";
        reload();StringBuilder s=new StringBuilder(model+" / "+end+": ");
        for(int i=1;i<=count(model,end);i++){
            if(i>1)s.append(" | ");
            s.append('#').append(i).append('=')
              .append(String.format(Locale.ROOT,"%+.3f",offset(model,end,i)));
        }
        return s.toString();
    }
    public static synchronized Path export() throws Exception {
        reload();Path p=path().getParent().resolve("DEV58B10F7R4-DEPTH-EXPORT-"+
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".csv");
        List<String> rows=new ArrayList<>();rows.add("model,end,lamp_number,group_depth,individual_depth,effective_depth");
        Map<String,Float> v=values;
        for(String model:new TreeSet<>(COUNTS.keySet()))for(String end:List.of("front","rear"))
            for(int idx=1;idx<=count(model,end);idx++){
                float g=v.getOrDefault(key(model,end,0),0F),one=v.getOrDefault(key(model,end,idx),0F);
                rows.add(String.format(Locale.ROOT,"%s,%s,%d,%.5f,%.5f,%.5f",model,end,idx,g,one,g+one));
            }
        Files.write(p,rows,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return p;
    }
}
