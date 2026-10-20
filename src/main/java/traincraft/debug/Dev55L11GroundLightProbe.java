package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.lighting.Dev55FleetGroundLights;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV55L11: read-only ground-light POSITION diagnostic for authorized fleet
 * review previews. No block placement, replacement, deletion, or changes to
 * the collision and rendered headlight paths. Logs actual DEV55L1 transient
 * light blocks so they can be compared with existing DEV55L9-RAY logs.
 */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55L11GroundLightProbe {
    private static final Logger LOG=LoggerFactory.getLogger("DEV55L11-GROUND-PROBE");
    private static final String FLAG="DEV55L11-GROUND-DEBUG-ON.flag";
    private static final int MAX_ENGINES=6;
    private static final int MAX_BLOCKS_PER_ENGINE=24;
    private Dev55L11GroundLightProbe(){}

    private static boolean enabled(){
        return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve(FLAG));
    }
    private static boolean review(Entity e){
        return e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW")
                && e.getCustomName()!=null
                && "DEV55L3_TEST_LAMPS".equals(e.getCustomName().getString());
    }
    private static float facing(Entity e){
        if(e instanceof LegacyPoweredLocomotive powered) return powered.getRenderTrainFacingYaw(0F);
        if(e instanceof SmallSteamLocomotive steam) return steam.getTrainFacingYaw();
        return e.getYRot();
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        if(level.getGameTime()%20L!=0L || !enabled())return;
        Block light=Dev55FleetGroundLights.lightBlock();
        if(light==null)return;
        int engines=0;
        for(Entity engine:level.getAllEntities()){
            if(engines>=MAX_ENGINES)break;
            if(!engine.isAlive() || !review(engine))continue;
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(engine.getType());
            if(id==null || !"traincraft".equals(id.getNamespace()) || !Dev55FleetGroundLights.mapped(id.getPath()))continue;
            engines++;
            double yaw=Math.toRadians(facing(engine));
            double dx=Math.sin(yaw),dz=-Math.cos(yaw);
            // These are DEV55L1's existing approximate ground-light positions,
            // deliberately NOT the calibrated visual beam's true origin/end.
            Vec3 predictedSource=engine.position().add(dx*1.5D,1.40D,dz*1.5D);
            Vec3 predictedNear=predictedSource.add(dx*2.5D,-0.06D*2.5D,dz*2.5D);
            Vec3 predictedFar=predictedSource.add(dx*7.5D,-0.06D*7.5D,dz*7.5D);
            List<String> actual=new ArrayList<>();
            List<BlockPos> positions=new ArrayList<>();
            BlockPos center=BlockPos.containing(predictedSource);
            for(int x=-12;x<=12 && positions.size()<MAX_BLOCKS_PER_ENGINE;x++){
                for(int z=-12;z<=12 && positions.size()<MAX_BLOCKS_PER_ENGINE;z++){
                    if(x*x+z*z>144)continue;
                    for(int y=-4;y<=4 && positions.size()<MAX_BLOCKS_PER_ENGINE;y++){
                        BlockPos pos=center.offset(x,y,z);
                        if(!level.hasChunkAt(pos) || level.getBlockState(pos).getBlock()!=light)continue;
                        positions.add(pos.immutable());
                        Vec3 middle=Vec3.atCenterOf(pos);
                        actual.add(pos.toShortString()+":light="+level.getBrightness(LightLayer.BLOCK,pos)
                                +":distToOldFar="+String.format(java.util.Locale.ROOT,"%.2f",middle.distanceTo(predictedFar)));
                    }
                }
            }
            LOG.info("[DEV55L11-GROUND] model={} engine={} trainPos={} yaw={} oldSource={} oldFirst={} oldLast={} actualLightCount={} positions={}",
                    id.getPath(),engine.getId(),engine.position(),facing(engine),
                    predictedSource,predictedNear,predictedFar,positions.size(),actual);
            // Particles are visual diagnostics ONLY. No light or ray decisions change.
            level.sendParticles(ParticleTypes.FLAME,predictedSource.x,predictedSource.y+0.15D,predictedSource.z,
                    1,0D,0D,0D,0D); // orange: old estimated source
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,predictedFar.x,predictedFar.y+0.15D,predictedFar.z,
                    2,0D,0D,0D,0D); // blue: old final fixed-distance light target
            for(BlockPos p:positions){
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,p.getX()+0.5D,p.getY()+0.85D,p.getZ()+0.5D,
                        1,0D,0D,0D,0D); // green: ACTUAL DEV55L1 placed light block
            }
        }
    }
}
