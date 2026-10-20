package traincraft.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.debug.Dev55L4AimTuner;
import traincraft.debug.Dev55L5Optics;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * DEV55L14 provisional fleet REVIEW light placement. NEVER enabled for normal
 * trains, frozen models, or the exact 4-4-0 L12 pilot. Uses locomotive yaw,
 * bounded optical steering and shared player/mob/block clips. The estimated
 * lens height/offset MUST be accepted per model before production use.
 */
public final class Dev55L14GroundPilot {
    private Dev55L14GroundPilot() {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L14-FLEET");
    private static final String MARK="DEV55L3_TEST_LAMPS";
    private static final Set<String> CANDIDATES=Set.of(
        "locomotive_steam_adler",
        "locomotive_steam_br80",
        "locomotive_steam_br_britannia",
        "locomotive_steam_c11",
        "locomotive_steam_c41_080",
        "locomotive_steam_c41t",
        "locomotive_steam_cherepanov",
        "locomotive_steam_climax",
        "locomotive_steam_climax_2",
        "locomotive_steam_fowler",
        "locomotive_steam_glyn_042t",
        "locomotive_steam_gs4",
        "locomotive_steam_gwr_101_class",
        "locomotive_steam_gwr_42xx",
        "locomotive_steam_gwr_72xx",
        "locomotive_steam_j50",
        "locomotive_steam_lssp7",
        "locomotive_steam_pe",
        "locomotive_steam_rw_type_2",
        "locomotive_steam_rw_type_3",
        "locomotive_steam_s100_uk",
        "locomotive_steam_s100_us",
        "locomotive_steam_shay_3_truck",
        "locomotive_steam_skookum",
        "locomotive_steam_snow_plow",
        "locomotive_steam_southern1102",
        "locomotive_steam_star_class",
        "locomotive_steam_u57",
        "locomotive_steam_wwcp_062t",
        "locomotive_electric_br_e69",
        "locomotive_diesel_gp7",
        "locomotive_diesel_kof",
        "locomotive_electric_minetrain",
        "locomotive_diesel_shunter"
    );
    private record Owned(BlockPos pos,long tick) {}
    private static final Map<String,Owned> OWNED=new HashMap<>();
    private static boolean enabled(){return Files.isRegularFile(
            FMLPaths.GAMEDIR.get().resolve("DEV55L14-FLEET-ON.flag"));}
    private static float facing(Entity engine){
        if(engine instanceof LegacyPoweredLocomotive powered)
            return powered.getRenderTrainFacingYaw(0F);
        if(engine instanceof SmallSteamLocomotive steam)
            return steam.getTrainFacingYaw();
        return engine.getYRot();
    }
    /** Return true only when this exact review preview was handled. */
    public static boolean apply(ServerLevel level,Entity e,String id){
        if(!CANDIDATES.contains(id) || !enabled() || e.getCustomName()==null ||
                !MARK.equals(e.getCustomName().getString()) ||
                !e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW"))return false;
        try {
            float yaw=facing(e);
            if(!Float.isFinite(yaw))throw new IllegalStateException("Nonfinite facing yaw");
            Dev55L5Optics.Setting optics=Dev55L5Optics.get(id,"center");
            float reach=optics.reach();
            // Approximate emitter for uncalibrated locomotives. This is a
            // review candidate, not a fabricated exact headlamp marker.
            double a=Math.toRadians(yaw);
            double fX=Math.sin(a),fZ=-Math.cos(a);
            double rX=Math.cos(a),rZ=Math.sin(a);
            Vec3 from=e.position().add(fX*(1.5D+optics.forward())+
                rX*optics.sideways(),1.55D,
                fZ*(1.5D+optics.forward())+rZ*optics.sideways());
            double degrees=optics.angle();
            double along=Math.cos(Math.toRadians(degrees));
            double side=Math.sin(Math.toRadians(degrees));
            int axis=optics.axisQuarterTurns();
            if(axis!=0){double prev=along;along=axis==1?-side:side;side=axis==1?prev:-prev;}
            Vec3 horiz=new Vec3(fX*along+rX*side,0D,fZ*along+rZ*side);
            double drop=reach*Math.tan(Math.toRadians(7.5D+Dev55L4AimTuner.get(id)));
            Vec3 to=from.add(horiz.x*reach,-drop,horiz.z*reach);
            double rayLength=from.distanceTo(to);
            if(!Double.isFinite(rayLength)||rayLength<1D||rayLength>40D||
                from.distanceTo(e.position())>5.5D)throw new IllegalStateException("Invalid approximate ray");
            double clipped=1D;
            BlockHitResult hit=level.clip(new ClipContext(from,to,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(hit.getType()==HitResult.Type.BLOCK)
                clipped=Math.min(clipped,Math.max(0D,from.distanceTo(hit.getLocation())/rayLength-0.02D));
            AABB query=new AABB(from,to).inflate(0.8D);
            for(Entity other:level.getEntities(e,query,c->c.isAlive() && !c.isSpectator()&&
                (c instanceof LivingEntity || (ForgeRegistries.ENTITY_TYPES.getKey(c.getType())!=null&&
                 "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(c.getType()).getNamespace()))))){
                if(other.isPassengerOfSameVehicle(e))continue;
                var intersection=other.getBoundingBox().inflate(0.14D).clip(from,to);
                if(intersection.isPresent())clipped=Math.min(clipped,
                    Math.max(0D,from.distanceTo(intersection.get())/rayLength-0.025D));
            }
            Vec3 end=from.lerp(to,Math.max(0D,Math.min(1D,clipped)));
            BlockPos point=BlockPos.containing(end.x,end.y+0.60D,end.z);
            String owner=level.dimension().location()+":"+e.getUUID();
            Owned old=OWNED.get(owner);
            boolean placed=Dev55FleetGroundLights.tcDev55L12Place(level,point);
            if(placed)OWNED.put(owner,new Owned(point,level.getGameTime()));
            else OWNED.remove(owner);
            if(old!=null && !old.pos().equals(point))
                Dev55FleetGroundLights.tcDev55L13Retire(level,old.pos());
            if(e.tickCount%80==0)LOG.info("[DEV55L14-REVIEW] model={} id={} source={} endpoint={} reach={} clippedFraction={} placed={} approximate=true",
                id,e.getId(),from,end,reach,clipped,placed);
        }catch(Exception ex){
            if(e.tickCount%80==0)LOG.warn("[DEV55L14-HOLD] model={} cause={}",id,ex.toString());
        }
        return true; // No legacy 3-light fallthrough for this preview while L14 active.
    }
}
