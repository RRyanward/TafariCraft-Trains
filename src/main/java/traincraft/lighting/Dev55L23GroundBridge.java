package traincraft.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.debug.Dev55L19Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** DEV55L23 local Creative-review ground-light bridge.
 * The client supplies the same *actual* clipped endpoints used by its visual ribbons.
 * The server validates all positions, clips them again, and owns transient blocks.
 * No requests can alter arbitrary blocks; only the pre-existing, invisible fleet light
 * may be placed in AIR. Non-review trains and dedicated-server worlds never enter. */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55L23GroundBridge {
    private Dev55L23GroundBridge(){}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L23-GROUND");
    private record Owned(Set<BlockPos> cells,long tick){}
    private static final Map<String,Owned> OWNED=new HashMap<>();
    private static final Map<String,Long> LAST_WARNING=new HashMap<>();
    private static final Set<String> ROLES=Set.of("center","left","right");
    private static Path folder(){return FMLPaths.GAMEDIR.get().resolve("DEV55L23-RAYS");}
    private static boolean enabled(){return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L23-LIVE-GROUND-ON.flag"))
        && Files.isRegularFile(Dev55L19Settings.switchPath());}
    private static boolean isPreview(Entity e){return e!=null&&e.isAlive()&&e.getCustomName()!=null
        && Dev55L19Settings.TAG.equals(e.getCustomName().getString())
        && e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW");}
    public static boolean claims(Entity e,String raw){return enabled()&&isPreview(e)&&Dev55L19Settings.supported(raw)
        && !"locomotive_steam_4_4_0".equals(raw);}
    private static String owner(ServerLevel level,Entity e){return level.dimension().location()+"/"+e.getUUID();}
    private static void warning(String key,long tick,String model,String why){
        if(tick-LAST_WARNING.getOrDefault(key,Long.MIN_VALUE/2)>=60L){
            LOG.warn("[DEV55L23-HOLD] model={} owner={} cause={}",model,key,why);LAST_WARNING.put(key,tick);
        }
    }
    private static void retire(ServerLevel level,String key,Set<BlockPos> keep){
        Owned old=OWNED.get(key);if(old==null)return;
        for(BlockPos oldCell:old.cells()){
            if(keep.contains(oldCell))continue;
            boolean shared=false;
            for(var row:OWNED.entrySet()){
                if(row.getKey().equals(key))continue;
                if(row.getKey().startsWith(level.dimension().location()+"/") && row.getValue().cells().contains(oldCell)){
                    shared=true;break;
                }
            }
            if(!shared)Dev55FleetGroundLights.tcDev55L13Retire(level,oldCell);
        }
        if(keep.isEmpty())OWNED.remove(key);
    }
    private static Set<BlockPos> apply(ServerLevel level,Entity engine,String model,String key) throws Exception{
        Path rayFile=folder().resolve(engine.getUUID()+".csv");
        if(!Files.isRegularFile(rayFile))throw new IllegalStateException("no client ray export; verify integrated client preview");
        if(System.currentTimeMillis()-Files.getLastModifiedTime(rayFile).toMillis()>1800L)
            throw new IllegalStateException("stale client ray data (>1800 ms)");
        List<String> lines=Files.readAllLines(rayFile,StandardCharsets.UTF_8);
        if(lines.isEmpty()||lines.size()>4)throw new IllegalStateException("unexpected ray count");
        String[] header=lines.get(0).split(",",-1);
        if(header.length!=5||!"DEV55L23".equals(header[0])||!"1".equals(header[1])||
            !header[2].equals(engine.getUUID().toString())||
            !header[3].equals(level.dimension().location().toString())||!header[4].equals(model))
            throw new IllegalStateException("ray owner/dimension/model header mismatch");
        Set<BlockPos> next=new HashSet<>(), prior=OWNED.containsKey(key)?OWNED.get(key).cells():Set.of();
        for(int i=1;i<lines.size();i++){
            String[] parts=lines.get(i).split(",",-1);
            if(parts.length!=7||!ROLES.contains(parts[0]))throw new IllegalStateException("bad beam role/columns");
            double[] a=new double[6];for(int j=0;j<6;j++){a[j]=Double.parseDouble(parts[j+1]);if(!Double.isFinite(a[j]))throw new IllegalStateException("nonfinite coordinate");}
            Vec3 from=new Vec3(a[0],a[1],a[2]),to=new Vec3(a[3],a[4],a[5]);
            // Validate independently against the server entity position, plus bounded reach.
            if(from.distanceTo(engine.position())>9D||to.distanceTo(from)>23D||
                to.distanceTo(engine.position())>26D||from.distanceTo(to)<.01D)
                throw new IllegalStateException("out-of-bounds beam geometry");
            BlockHitResult hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
            if(hit.getType()==HitResult.Type.BLOCK && from.distanceTo(hit.getLocation())+0.10D<from.distanceTo(to))
                to=hit.getLocation();
            AABB extent=new AABB(from,to).inflate(0.9D);
            for(Entity other:level.getEntities(engine,extent,c->c!=engine&&c.isAlive()&&c instanceof LivingEntity&&!c.isSpectator()&&!c.isPassenger())){
                var contact=other.getBoundingBox().inflate(.10D).clip(from,to);
                if(contact.isPresent() && from.distanceTo(contact.get())+0.02D<from.distanceTo(to))to=contact.get();
            }
            BlockPos cell=BlockPos.containing(to.x,to.y+.70D,to.z);
            if(level.hasChunkAt(cell))next.add(cell.immutable());
            if(Dev55L19Settings.diagnostic() && engine.tickCount%40==0)
                LOG.info("[DEV55L23-RAY] model={} beam={} entity={} from={} clippedEnd={} cell={}",model,parts[0],engine.getId(),from,to,cell);
        }
        // Place next first to avoid unnecessary flicker on a shared/unchanged cell.
        Set<BlockPos> placed=new HashSet<>();
        for(BlockPos cell:next){if(Dev55FleetGroundLights.tcDev55L12Place(level,cell))placed.add(cell);}
        retire(level,key,placed);
        OWNED.put(key,new Owned(Set.copyOf(placed),level.getGameTime()));
        if(Dev55L19Settings.diagnostic()&&engine.tickCount%40==0)
            LOG.info("[DEV55L23-GROUND] model={} entity={} newCells={} oldCells={} skipped={} reviewOnly=true",
                model,engine.getId(),placed,prior,next.size()-placed.size());
        return placed;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent ev){
        if(ev.phase!=TickEvent.Phase.END||!(ev.level instanceof ServerLevel level))return;
        final long now=level.getGameTime(); String dim=level.dimension().location()+"/";
        if(!enabled() || now%2L==0L){
            if(!enabled())cleanup(level,dim,now);return;
        }
        Set<String> encountered=new HashSet<>();int count=0;
        for(Entity e:level.getAllEntities()){
            if(count>=32)break;
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if(id==null||!"traincraft".equals(id.getNamespace())||!claims(e,id.getPath()))continue;
            count++;String key=owner(level,e);encountered.add(key);
            try{apply(level,e,id.getPath(),key);}catch(Exception error){
                retire(level,key,Set.of());warning(key,now,id.getPath(),error.toString());
            }
        }
        cleanupMissing(level,dim,now,encountered);
    }
    private static void cleanupMissing(ServerLevel level,String dim,long now,Set<String> seen){
        for(String key:new ArrayList<>(OWNED.keySet())){
            if(!key.startsWith(dim)||seen.contains(key))continue;
            Owned owned=OWNED.get(key);
            if(owned!=null&&now-owned.tick()>3L){retire(level,key,Set.of());
                if(Dev55L19Settings.diagnostic())LOG.info("[DEV55L23-RELEASE] owner={} reason=preview-absent",key);}
        }
    }
    private static void cleanup(ServerLevel level,String dim,long now){
        for(String key:new ArrayList<>(OWNED.keySet())){
            if(key.startsWith(dim)){retire(level,key,Set.of());
                if(Dev55L19Settings.diagnostic())LOG.info("[DEV55L23-RELEASE] owner={} reason=pilot-off",key);}
        }
    }
}
