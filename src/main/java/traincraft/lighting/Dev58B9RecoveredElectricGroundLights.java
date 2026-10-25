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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/**
 * DEV58B9: server-side night ground illumination for VL10 and High Speed Zeroed.
 * Reuses the already registered, short-lived DEV55 fleet air-light block and
 * its existing safe placement/expiry API. Does NOT introduce a new block ID,
 * replace vegetation/solid blocks, or modify legacy locomotives.
 */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev58B9RecoveredElectricGroundLights {
    private Dev58B9RecoveredElectricGroundLights() {}
    private static final double REACH=12D;
    private static final double[] SAMPLES={3D,6D,9D};

    private static boolean dark(ServerLevel world) {
        long t=Math.floorMod(world.getDayTime(),24000L);
        return t>=12200L && t<=23800L;
    }

    private static Vec3 modelToWorld(LegacyPoweredLocomotive e,
                                     String id,double x,double y,double z) {
        double a=Math.toRadians(270D-e.getRenderTrainFacingYaw(0F));
        double cs=Math.cos(a),sn=Math.sin(a);
        double mx=x+Dev58B9ElectricLampMarkers.tx(id);
        double mz=z;
        return new Vec3(e.getX()+cs*mx+sn*mz,
            e.getY()+Dev58B9ElectricLampMarkers.ty(id)+e.getPoweredModelLift()+y,
            e.getZ()-sn*mx+cs*mz);
    }

    private static void apply(ServerLevel world,LegacyPoweredLocomotive e,String id) {
        for(double[] m:Dev58B9ElectricLampMarkers.front(id)) {
            Vec3 from=modelToWorld(e,id,m[0]-0.022D,m[1],m[2]);
            Vec3 far=modelToWorld(e,id,m[0]-REACH-0.022D,m[1],m[2]);
            double floor=e.getY()+0.18D;
            BlockHitResult terrain=world.clip(new ClipContext(
                new Vec3(far.x,e.getY()+0.25D,far.z),
                new Vec3(far.x,e.getY()-6D,far.z),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(terrain.getType()==HitResult.Type.BLOCK) {
                double y=terrain.getLocation().y;
                if(y<=e.getY()+0.30D && y>=e.getY()-5.95D) floor=y+0.065D;
            }
            Vec3 end=new Vec3(far.x,floor,far.z);
            double limit=1D;
            BlockHitResult block=world.clip(new ClipContext(
                from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(block.getType()==HitResult.Type.BLOCK)
                limit=Math.min(limit,from.distanceTo(block.getLocation())/
                    Math.max(0.001D,from.distanceTo(end)));
            AABB box=new AABB(from,end).inflate(0.9D);
            for(Entity other:world.getEntities(e,box, target->
                target!=e && target.isAlive() && !target.isSpectator()
                && target instanceof LivingEntity && !target.isPassengerOfSameVehicle(e))) {
                var hit=other.getBoundingBox().inflate(0.10D).clip(from,end);
                if(hit.isPresent())limit=Math.min(limit,
                    from.distanceTo(hit.get())/Math.max(0.001D,from.distanceTo(end)));
            }
            for(double distance:SAMPLES) {
                if(distance>REACH*limit-0.25D)break;
                Vec3 place=from.add(end.subtract(from).scale(distance/REACH));
                BlockPos cell=BlockPos.containing(place.x,place.y+0.70D,place.z);
                // The shared helper accepts only air or its OWN light block;
                // expired cells are cleaned automatically by block ticks.
                Dev55FleetGroundLights.tcDev55L12Place(world,cell);
            }
        }
    }

    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        // Disable ONLY old B9 air-light cells during the six-model viewer test.
        // Turning OFF the F7 flag restores the already accepted B9 behavior.
        if(java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV58B10F7-SIX-LIGHTS-TEST-ON.flag")))return;
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel world))return;
        if(world.getGameTime()%10L!=0L)return;
        int count=0;
        for(Entity entity:world.getAllEntities()) {
            if(count>=16)break;
            if(!(entity instanceof LegacyPoweredLocomotive e) || !e.isAlive()
                || !e.isElectric() || !Dev58B9ElectricLampMarkers.supported(e.variantId()))continue;
            if(!LocomotiveLightMode.enabled(e))continue;
            count++;
            apply(world,e,e.variantId());
        }
    }
}
