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
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;
import traincraft.entity.train.steam.classics.MogulSteamLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

/**
 * DEV58B9R2: limited, temporary ground cells for Forney/Mogul only.
 * Reuses accepted registered Dev55 light block and expiry/tick path.
 * Does not touch terrain or other locomotive classes.
 */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev58B9R2SteamGroundLights {
    private Dev58B9R2SteamGroundLights() {}
    private static final double REACH=12D;
    private static final double[] SAMPLES={3D,6D,9D};
    private record Mark(double y,double z,double tx) {}
    private static Mark mark(SmallSteamLocomotive e) {
        if(e instanceof ForneySteamLocomotive)return new Mark(26D/16D,-25.04D/16D,-1.3D);
        if(e instanceof MogulSteamLocomotive)return new Mark(27D/16D,-27.04D/16D,-0.6D);
        return null;
    }
    private static boolean dark(ServerLevel level) {
        long t=Math.floorMod(level.getDayTime(),24000L);
        return t>=12200L && t<=23800L;
    }
    private static Vec3 world(SmallSteamLocomotive e,Mark m,double y,double z) {
        // Authored model's -Z front becomes -X after renderer local Y+90.
        // Apply the accepted yaw and X-translation in the same order as renderer.
        double x=z+m.tx();
        double a=Math.toRadians(270D-e.getRenderTrainFacingYaw(0F));
        double c=Math.cos(a),s=Math.sin(a);
        return new Vec3(e.getX()+c*x,e.getY()+y,e.getZ()-s*x);
    }
    private static void illuminate(ServerLevel level,SmallSteamLocomotive e,Mark m) {
        Vec3 from=world(e,m,m.y(),m.z());
        Vec3 far=world(e,m,m.y(),m.z()-REACH);
        double floor=e.getY()+0.18D;
        BlockHitResult terrain=level.clip(new ClipContext(
            new Vec3(far.x,e.getY()+0.25D,far.z),
            new Vec3(far.x,e.getY()-6D,far.z),
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
        if(terrain.getType()==HitResult.Type.BLOCK) {
            double y=terrain.getLocation().y;
            if(y<=e.getY()+0.30D && y>=e.getY()-5.95D)floor=y+0.065D;
        }
        Vec3 end=new Vec3(far.x,floor,far.z);
        double fraction=1D;
        BlockHitResult block=level.clip(new ClipContext(
            from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
        if(block.getType()==HitResult.Type.BLOCK)
            fraction=Math.min(fraction,from.distanceTo(block.getLocation())/
                Math.max(0.001D,from.distanceTo(end)));
        AABB swept=new AABB(from,end).inflate(0.9D);
        for(Entity other:level.getEntities(e,swept,
            target->target!=e && target.isAlive() && !target.isSpectator()
                && target instanceof LivingEntity && !target.isPassengerOfSameVehicle(e))) {
            var hit=other.getBoundingBox().inflate(0.10D).clip(from,end);
            if(hit.isPresent())fraction=Math.min(fraction,
                from.distanceTo(hit.get())/Math.max(0.001D,from.distanceTo(end)));
        }
        for(double distance:SAMPLES) {
            if(distance>REACH*fraction-0.25D)break;
            Vec3 spot=from.add(end.subtract(from).scale(distance/REACH));
            BlockPos pos=BlockPos.containing(spot.x,spot.y+0.70D,spot.z);
            Dev55FleetGroundLights.tcDev55L12Place(level,pos);
        }
    }
    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        if(level.getGameTime()%10L!=0L)return;
        int checked=0;
        for(Entity e:level.getAllEntities()) {
            if(checked>=16)break;
            if(!(e instanceof SmallSteamLocomotive steam) || !e.isAlive())continue;
            Mark m=mark(steam);
            if(m==null || !LocomotiveLightMode.enabled(e))continue;
            checked++;
            illuminate(level,steam,m);
        }
    }
}
