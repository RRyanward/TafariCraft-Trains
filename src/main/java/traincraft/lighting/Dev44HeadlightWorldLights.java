package traincraft.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import java.util.HashMap;
import java.util.Map;

/**
 * DEV44 Fix 11: cautious server-side *real vanilla block lighting* pilot.
 * Berkshire-only. Places this mod's invisible, non-colliding block only in
 * vanilla air in loaded chunks. Never displaces solids, rails, fluids, etc.
 * A scheduled vanilla block tick removes it after motion or engine shutdown.
 * No renderer, entity physics or persistent save data is changed.
 */
@Mod.EventBusSubscriber(modid = "traincraft")
public final class Dev44HeadlightWorldLights {
    private Dev44HeadlightWorldLights() {}
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV44-LIGHT-WORLD11");
    private static final ResourceLocation ID = new ResourceLocation("traincraft", "transient_headlight_light");
    private static final Map<String, Long> EXPIRES = new HashMap<>();
    private static final int KEEP_TICKS = 20;
    private static final double[] REACHES = {3.0D,6.0D,9.0D};
    public static Block lightBlock() { return ForgeRegistries.BLOCKS.getValue(ID); }

    @Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, ID, Dev44TransientHeadlightBlock::new);
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    public static void expire(ServerLevel level, BlockPos pos, BlockState expected) {
        if (expected.getBlock() != lightBlock()) return;
        String k=key(level,pos);
        Long until=EXPIRES.get(k);
        long now=level.getGameTime();
        if (until != null && now < until) {
            // Refresh the removal tick without removing a still-active lamp.
            level.scheduleTick(pos, expected.getBlock(), (int)Math.max(1,Math.min(KEEP_TICKS,until-now)));
            return;
        }
        EXPIRES.remove(k);
        if (level.getBlockState(pos).getBlock()==expected.getBlock()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime()%5L != 0L) return;
        float night=nightStrength(level.getDayTime());
        if (night<=0.05F) return;
        Block light=lightBlock();
        if (light == null || light==Blocks.AIR) return;
        int engines=0, placed=0;
        for (Entity ent : level.getAllEntities()) {
            if (!(ent instanceof SmallSteamLocomotive engine) || !ent.isAlive()) continue;
            ResourceLocation type=ForgeRegistries.ENTITY_TYPES.getKey(ent.getType());
            if (type==null || !type.getNamespace().equals("traincraft")) continue;
            String id=type.getPath();
            if (!id.equals("locomotive_steam_berkshire_765") && !id.equals("locomotive_steam_berkshire_1225")) continue;
            // DEV55L23: L19 preview Berkshire beams have their own exact
            // clipped endpoint. Keep the old three block placers in production only.
            if (Dev55L23GroundBridge.claims(ent,id)) continue;
            if (engine.getBurnTime()<=0 && engine.getSteamAmount()<=0) continue;
            engines++;
            double yaw=Math.toRadians(ent.getYRot());
            // Same Berkshire forward projection as accepted Fix 7/Fix 8.
            double dx=Math.sin(yaw),dz=-Math.cos(yaw);
            Vec3 source=new Vec3(ent.getX()+dx*3.2D,ent.getY()+1.18D,ent.getZ()+dz*3.2D);
            for (double distance : REACHES) {
                Vec3 target=source.add(dx*distance,-0.30D,dz*distance);
                BlockHitResult hit=level.clip(new ClipContext(source,target,
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ent));
                if (hit.getType()!=HitResult.Type.MISS && source.distanceToSqr(hit.getLocation()) < source.distanceToSqr(target)-0.01D) break;
                // Fix 12: do not place light sources beyond a player, mob or train.
                // This does not change blocks or collision and never replaces air.
                AABB volume=new AABB(source,target).inflate(0.60D);
                boolean entityBlocked=false;
                for(Entity other : level.getEntities(ent,volume,e -> e.isAlive() && !e.isSpectator() &&
                        (e instanceof LivingEntity ||
                         (ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) != null &&
                          "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()))))) {
                    if(other.isPassengerOfSameVehicle(ent)) continue;
                    if(other.getBoundingBox().inflate(0.15D).clip(source,target).isPresent()) {
                        entityBlocked=true;
                        break;
                    }
                }
                if(entityBlocked) break;
                BlockPos pos=BlockPos.containing(target);
                if (!level.hasChunkAt(pos)) continue;
                BlockState state=level.getBlockState(pos);
                if (state.getBlock()!=light && !state.is(Blocks.AIR)) continue;
                if (state.getBlock()!=light) {
                    if (!level.setBlock(pos,light.defaultBlockState(),3)) continue;
                    placed++;
                }
                EXPIRES.put(key(level,pos),level.getGameTime()+KEEP_TICKS);
                level.scheduleTick(pos,light,KEEP_TICKS);
            }
        }
        if (engines>0 && level.getGameTime()%100L==0L)
            LOG.info("[TC-DEV44-LIGHT-WORLD11] dimension={} activeBerkshires={} newAirLightBlocks={} expiryTicks={} nightStrength={}",
                    level.dimension().location(),engines,placed,KEEP_TICKS,night);
    }

    private static float nightStrength(long time) {
        long t=Math.floorMod(time,24000L);
        if (t<12000L) return 0F;
        if (t<13000L) return (t-12000L)/1000F;
        if (t<23000L) return 1F;
        return (24000L-t)/1000F;
    }
}
