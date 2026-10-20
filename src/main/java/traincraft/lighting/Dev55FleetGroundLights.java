package traincraft.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * DEV55L1 review candidate: real block illumination for mapped locomotives.
 * Existing DEV44 visual beams remain untouched; Berkshire world-light pilot
 * stays authoritative for those two models. Default OFF; exact roster only.
 * The block light positions are provisional until the user passes the in-game
 * lamp-alignment review; nothing is placed in solids or unloaded chunks.
 */
@Mod.EventBusSubscriber(modid = "traincraft")
public final class Dev55FleetGroundLights {
    private Dev55FleetGroundLights() {}
    private static final ResourceLocation ID = new ResourceLocation("traincraft","dev55_fleet_light_air");
    private static final Set<String> MAPPED = Set.of(
        "locomotive_steam_262t",
        "locomotive_steam_4_4_0",
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
        "locomotive_steam_vb_shay",
        "locomotive_steam_wwcp_062t",
        // Review-only: Berkshire production world lights remain under DEV44 control.
        "locomotive_steam_berkshire_1225",
        "locomotive_steam_berkshire_765",
        "locomotive_electric_br_e69",
        "locomotive_diesel_gp7",
        "locomotive_diesel_kof",
        "locomotive_electric_minetrain",
        "locomotive_diesel_shunter"
    );
    private static final Map<String,Long> EXPIRES = new HashMap<>();
    private static final int KEEP_TICKS=45;
    private static final int MAX_ENGINES_PER_DIMENSION=24;
    private static final double[] DISTANCES={2.5D,5.0D,7.5D};
    public static Block lightBlock(){return ForgeRegistries.BLOCKS.getValue(ID);}

    @Mod.EventBusSubscriber(modid="traincraft",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Register {
        private Register(){}
        @SubscribeEvent public static void register(RegisterEvent event){
            event.register(Registries.BLOCK,ID,Dev55FleetLightBlock::new);
        }
    }
    private static String key(ServerLevel level,BlockPos pos){return level.dimension().location()+":"+pos.asLong();}
    public static void expire(ServerLevel level,BlockPos pos,BlockState expected){
        if(expected.getBlock()!=lightBlock()) return;
        String key=key(level,pos);
        long now=level.getGameTime();
        Long expiry=EXPIRES.get(key);
        if(expiry!=null && now<expiry){
            level.scheduleTick(pos,expected.getBlock(),(int)Math.max(1,Math.min(KEEP_TICKS,expiry-now)));
            return;
        }
        EXPIRES.remove(key);
        if(level.getBlockState(pos).getBlock()==expected.getBlock())
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    private static boolean enabled(){
        return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L1-LIGHT-ON.flag"));
    }
    private static boolean dark(ServerLevel level){
        long t=Math.floorMod(level.getDayTime(),24000L);
        return t>=12200L && t<=23800L;
    }
    private static boolean active(Entity entity){
        // LegacyPoweredLocomotive EXTENDS SmallSteamLocomotive: order matters!
        if(entity instanceof LegacyPoweredLocomotive powered){
            if(powered.isDiesel()) return powered.getPowerAmount()>0;
            if(powered.isElectric()) return powered.isOnPoweredEnergyTrack() || powered.hasPortableBackupRedstone();
            return false;
        }
        if(entity instanceof SmallSteamLocomotive steam)
            return steam.getBurnTime()>0 || steam.getSteamAmount()>0;
        return false;
    }
    private static float facing(Entity e){
        if(e instanceof LegacyPoweredLocomotive powered) return powered.getRenderTrainFacingYaw(0F);
        if(e instanceof SmallSteamLocomotive steam) return steam.getTrainFacingYaw();
        return e.getYRot();
    }
    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Dev55L20Ground262Pilot.cleanup(level);
        // DEV55L3: night remains mandatory, but review preview lamps do
        // not require the normal fleet ground-light opt-in flag.
        // DEV55L20: update only the opt-in Creative 262T L19 ground hotspot
        // every server tick. All other fleet sources keep the accepted 10-tick cadence.
        final boolean tcL20NormalPulse=(level.getGameTime()%10L==0L);
        final boolean tcL20FastPreview=Dev55L20Ground262Pilot.enabled();
        if((!tcL20NormalPulse && !tcL20FastPreview) || !dark(level))return;
        final boolean tcDev55L3NormalGroundLights = enabled();
        Block block=lightBlock();
        if(block==null||block==Blocks.AIR)return;
        int count=0;
        for(Entity engine:level.getAllEntities()){
            if(count>=MAX_ENGINES_PER_DIMENSION)break;
            if(!engine.isAlive())continue;
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(engine.getType());
            if(id==null||!"traincraft".equals(id.getNamespace()) || !MAPPED.contains(id.getPath()))continue;
            // DEV55L3: review previews are marked only by the authorized
            // Creative viewer. Keep real trains on their original fuel gates.
            boolean tcDev55L3Review = engine.getPersistentData().getBoolean("DEV55LIGHT_REVIEW")
                    && engine.getCustomName()!=null
                    && "DEV55L3_TEST_LAMPS".equals(engine.getCustomName().getString());
            if(!tcDev55L3NormalGroundLights && !tcDev55L3Review)continue;
            // On intervening ticks, never evaluate or refresh ANY non-pilot light.
            if(!tcL20NormalPulse && !Dev55L20Ground262Pilot.eligible(engine,id.getPath()))continue;
            if((id.getPath().equals("locomotive_steam_berkshire_1225")
               || id.getPath().equals("locomotive_steam_berkshire_765")) && !tcDev55L3Review)continue;
            if(!tcDev55L3Review && !active(engine))continue;
            count++;
            // The LIVE L19 262T beam must own ground lighting before either
            // the 7.5-block legacy source or L14's independent approximate ray.
            if (Dev55L20Ground262Pilot.apply(level,engine,id.getPath())) continue;
            if(!tcL20NormalPulse)continue;
            // DEV55L23: the Creative review ray-bridge owns ground lights.
            // Stop all older L12/L14/L20/default world lights for this preview.
            // Normal/fuel-gated trains still follow their previous path.
            if (Dev55L23GroundBridge.claims(engine,id.getPath())) continue;
            if (Dev55L12GroundPilot.apply(level,engine,id.getPath())) continue;
            if (Dev55L14GroundPilot.apply(level,engine,id.getPath())) continue;
            double yaw=Math.toRadians(facing(engine));
            double dx=Math.sin(yaw), dz=-Math.cos(yaw);
            Vec3 source=new Vec3(engine.getX()+dx*1.5D,engine.getY()+1.40D,engine.getZ()+dz*1.5D);
            for(double distance:DISTANCES){
                Vec3 to=source.add(dx*distance,-0.06D*distance,dz*distance);
                BlockHitResult solid=level.clip(new ClipContext(source,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,engine));
                if(solid.getType()!=HitResult.Type.MISS && source.distanceToSqr(solid.getLocation())<source.distanceToSqr(to)-0.01D)break;
                AABB segment=new AABB(source,to).inflate(0.55D);
                boolean entityBlocked=false;
                for(Entity other:level.getEntities(engine,segment,entity -> entity.isAlive()&&!entity.isSpectator()
                    && (entity instanceof LivingEntity || (
                        ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())!=null &&
                        "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).getNamespace()))))){
                    if(other.isPassengerOfSameVehicle(engine))continue;
                    if(other.getBoundingBox().inflate(0.1D).clip(source,to).isPresent()) {entityBlocked=true;break;}
                }
                if(entityBlocked)break;
                BlockPos position=BlockPos.containing(to);
                if(!level.hasChunkAt(position))continue;
                BlockState state=level.getBlockState(position);
                if(state.getBlock()!=block && !state.is(Blocks.AIR))continue;
                if(state.getBlock()!=block && !level.setBlock(position,block.defaultBlockState(),3))continue;
                EXPIRES.put(key(level,position),level.getGameTime()+KEEP_TICKS);
                level.scheduleTick(position,block,KEEP_TICKS);
            }
        }
    }

    /** DEV55L12 preview-only: refresh one light at the verified 4-4-0 beam endpoint. */
    /** DEV55L13: revoke only a still-registered temporary source owned by the L12 pilot. */
    public static boolean tcDev55L13Retire(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return false;
        Block block=lightBlock();
        if(block==null || block==Blocks.AIR)return false;
        String identifier=key(level,pos);
        if(!EXPIRES.containsKey(identifier))return false;
        EXPIRES.remove(identifier);
        return level.getBlockState(pos).getBlock()==block
                && level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    public static boolean tcDev55L12Place(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return false;
        Block block=lightBlock();
        if(block==null||block==Blocks.AIR)return false;
        BlockState before=level.getBlockState(pos);
        if(before.getBlock()!=block&&!before.is(Blocks.AIR))return false;
        if(before.getBlock()!=block&&!level.setBlock(pos,block.defaultBlockState(),3))return false;
        EXPIRES.put(key(level,pos),level.getGameTime()+KEEP_TICKS);
        level.scheduleTick(pos,block,KEEP_TICKS);
        return true;
    }
    public static boolean mapped(String id){return MAPPED.contains(id)
         || "locomotive_steam_berkshire_1225".equals(id)
         || "locomotive_steam_berkshire_765".equals(id);}
}
