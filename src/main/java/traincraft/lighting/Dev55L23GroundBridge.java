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
    // G2: file freshness is evaluated BEFORE admission to the limited update budget.
    // Only the existing bridge owns transient ground-light cells; no renderer changes.
    private record Fresh(Entity engine,String model,String key){}
    private static final int MAX_FRESH_PER_TICK=32;
    private static final long MAX_RAY_AGE_MS=1800L;
    private static final Map<String,Owned> OWNED=new HashMap<>();
    private static final Map<String,Long> LAST_WARNING=new HashMap<>();
    // Per-dimension rotating cursor prevents a large stable scene from starving
    // locomotives late in Level#getAllEntities iteration order.
    private static final Map<String,Integer> CURSOR=new HashMap<>();
    private static final Set<String> ROLES=Set.of("center","left","right");
    private static Path folder(){return FMLPaths.GAMEDIR.get().resolve("DEV55L23-RAYS");}
    private static boolean oldEnabled(){return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L23-LIVE-GROUND-ON.flag"))
        && Files.isRegularFile(Dev55L19Settings.switchPath());}
    // F7R3: explicit separate singleplayer six-train ground test. The existing
    // accepted 44 fleet still uses ONLY its original oldEnabled() branch.
    private static final Set<String> SIX=Set.of(
        "locomotive_electric_high_speed_zeroed","locomotive_electric_vl10",
        "locomotive_diesel_sd70","locomotive_diesel_cd742",
        "locomotive_diesel_chme3","locomotive_diesel_sd40");
    private static boolean sixEnabled(){
        Path root=FMLPaths.GAMEDIR.get();
        return Files.isRegularFile(root.resolve("DEV58B10F7-SIX-LIGHTS-TEST-ON.flag"))
            && Files.isRegularFile(root.resolve("DEV58B10F7R3-SIX-GROUND-ON.flag"));
    }
    private static boolean enabled(){return oldEnabled()||sixEnabled();}
    private static boolean isPreview(Entity e){return e!=null&&e.isAlive()&&e.getCustomName()!=null
        && Dev55L19Settings.TAG.equals(e.getCustomName().getString())
        && e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW");}
    public static boolean claims(Entity e,String raw){
        if(e==null || !e.isAlive())return false;
        // F7R3 ground lights are client-calibrated and permitted only in local
        // singleplayer, only for six mapped native-model locomotives, and only
        // with BOTH F7 render and F7R3 ground flags active. Red tail lamps never
        // publish rays, so no tail light can produce a ground-light cell.
        if(sixEnabled() && SIX.contains(raw) &&
                e instanceof traincraft.entity.train.power.LegacyPoweredLocomotive &&
                e.level() instanceof ServerLevel local && local.getServer().isSingleplayer())
            return true;
        if(!oldEnabled() || !Dev55L19Settings.supported(raw))return false;
        // Client ray files may only create light in the integrated singleplayer
        // test session, NEVER on a multiplayer/dedicated server.
        if(Dev55L19Settings.fleetMode(e))return e.level() instanceof ServerLevel s
            && s.getServer().isSingleplayer();
        return isPreview(e)&&!"locomotive_steam_4_4_0".equals(raw);
    }
    private static String owner(ServerLevel level,Entity e){return level.dimension().location()+"/"+e.getUUID();}
    private static void warning(String key,long tick,String model,String why){
        if(tick-LAST_WARNING.getOrDefault(key,Long.MIN_VALUE/2)>=60L){
            LOG.warn("[DEV55L23-HOLD] model={} owner={} cause={}",model,key,why);LAST_WARNING.put(key,tick);
        }
    }
    /** G2: do not accept cached beam rays from trains that are no longer rendered.
     * A client atomic rename may happen just before apply(), which rechecks all
     * owner/dimension/model/geometry details under its existing safety policy.
     * Missing and stale rays never generate fabricated ground illumination. */
    private static boolean freshRay(Entity engine,long wallNow){
        try {
            Path file=folder().resolve(engine.getUUID()+".csv");
            if(!Files.isRegularFile(file))return false;
            long age=wallNow-Files.getLastModifiedTime(file).toMillis();
            return age>=-2000L && age<=MAX_RAY_AGE_MS;
        }catch(Exception unreadable){return false;}
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
        Set<BlockPos> next=new HashSet<>();
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
        }
        // Place next first to avoid unnecessary flicker on a shared/unchanged cell.
        Set<BlockPos> placed=new HashSet<>();
        for(BlockPos cell:next){if(Dev55FleetGroundLights.tcDev55L12Place(level,cell))placed.add(cell);}
        retire(level,key,placed);
        OWNED.put(key,new Owned(Set.copyOf(placed),level.getGameTime()));
        return placed;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent ev){
        if(ev.phase!=TickEvent.Phase.END||!(ev.level instanceof ServerLevel level))return;
        final long now=level.getGameTime(); String dim=level.dimension().location()+"/";
        if(!enabled()){cleanup(level,dim,now);return;}
        // DEV58G3: ground light is a block update, not a visual beam.
        // Update it at 10 Hz rather than doing per-engine disk reads,
        // collision tracing, and block refreshes on every 20 Hz server tick.
        // A two-tick cadence keeps the original 1.8s stale-ray deadline,
        // owner validation and 32-FRESH fair scheduling unchanged.
        if((now & 1L)!=0L)return;
        // DEV58G2: evaluate ALL eligible engines, not just the first 32.
        // An offscreen/missing/stale export has no authority to consume an
        // update slot, or to keep old world-light cells alive indefinitely.
        long wallNow=System.currentTimeMillis();
        Set<String> encountered=new HashSet<>();
        List<Fresh> ready=new ArrayList<>();
        int missingOrStale=0, retiredStale=0;
        for(Entity e:level.getAllEntities()){
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if(id==null||!"traincraft".equals(id.getNamespace())||!claims(e,id.getPath()))continue;
            String key=owner(level,e);
            encountered.add(key); // the budget must NOT trigger cleanupMissing
            if(!LocomotiveLightMode.enabled(e)) {
                retire(level,key,Set.of()); // OFF / engine stopped: revoke current cell ownership
                continue;
            }
            if(freshRay(e,wallNow)){
                ready.add(new Fresh(e,id.getPath(),key));
            }else{
                missingOrStale++;
                // Never retain an old hotspot after its client export stops.
                // This also preserves original DEV58F1 no-stale-beam semantics.
                if(OWNED.containsKey(key)){
                    retire(level,key,Set.of());
                    retiredStale++;
                }
            }
        }
        int updated=0, placedCells=0;
        if(!ready.isEmpty()){
            // Deterministic ordering plus rotation ensures all fresh engines
            // get a turn, even when a scene contains >32 valid exporters.
            ready.sort(Comparator.comparing(Fresh::key));
            int start=Math.floorMod(CURSOR.getOrDefault(dim,0),ready.size());
            int budget=Math.min(MAX_FRESH_PER_TICK,ready.size());
            for(int i=0;i<budget;i++){
                Fresh candidate=ready.get((start+i)%ready.size());
                try{
                    placedCells+=apply(level,candidate.engine(),candidate.model(),candidate.key()).size();
                    updated++;
                }catch(Exception error){
                    retire(level,candidate.key(),Set.of());
                    warning(candidate.key(),now,candidate.model(),error.toString());
                }
            }
            CURSOR.put(dim,(start+budget)%ready.size());
        }else{
            CURSOR.remove(dim);
        }
        // Tracks ALL eligible engines rather than merely the updated subset;
        // prevents the former 32-engine cap from revoking valid, deferred rays.
        cleanupMissing(level,dim,now,encountered);
        // One bounded summary/5 seconds, not per-engine offscreen warning spam.
        if(now%100L==0L && !encountered.isEmpty()){
            LOG.info("[DEV58G2-GROUND] dim={} eligible={} fresh={} updated={} cells={} missingOrStale={} retiredStale={} deferredFresh={}",
                level.dimension().location(),encountered.size(),ready.size(),updated,placedCells,
                missingOrStale,retiredStale,Math.max(0,ready.size()-MAX_FRESH_PER_TICK));
        }
    }
    private static void cleanupMissing(ServerLevel level,String dim,long now,Set<String> seen){
        for(String key:new ArrayList<>(OWNED.keySet())){
            if(!key.startsWith(dim)||seen.contains(key))continue;
            Owned owned=OWNED.get(key);
            if(owned!=null&&now-owned.tick()>3L){retire(level,key,Set.of());
            }
        }
    }
    private static void cleanup(ServerLevel level,String dim,long now){
        CURSOR.remove(dim);
        for(String key:new ArrayList<>(OWNED.keySet())){
            if(key.startsWith(dim)){retire(level,key,Set.of());
            }
        }
    }
}
