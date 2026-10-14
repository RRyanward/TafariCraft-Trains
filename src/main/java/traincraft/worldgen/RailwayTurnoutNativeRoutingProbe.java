/* DEV50d2a: disposable-copy *real rider* native-controller turnout observation.
 * The earlier DEV50d2 stalled because the riderless locomotive clears W/S
 * in SmallSteamLocomotive.tick(); this helper only observes actual driver input.
 * No direction vector, teleport, switch writes, or route certification.
 * A genuine branch can be observed only if actual track locomotion turns.
 */
package traincraft.worldgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;

public final class RailwayTurnoutNativeRoutingProbe {
    private static final Logger LOG=Traincraft.LOGGER;
    private static final String PREFIX="[TC-DEV50D2] ";
    private static final BlockPos STATION=new BlockPos(251,-60,104);
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final String STRAIGHT="New World - Railway DEV50D2 STRAIGHT Test";
    private static final String BRANCH="New World - Railway DEV50D2 BRANCH Test";
    private static final String MARKER="DEV50D2_DISPOSABLE_COPY.marker";
    private static final String CONTENT="DEV50D2_ISOLATED_COPY_ONLY_v1";
    private static final double TARGET=265.700D;
    private static final double SPEED_STOP=0.065D;
    private static final double MAX_TRAVEL=8.60D;
    private static final int MAX_TICKS=600;
    private static final int STALL_TICKS=220;
    private static final double MAX_GAP=3.25D;
    private static final double MIN_GAP=2.78D;
    private static Session active;
    private RailwayTurnoutNativeRoutingProbe(){}
    private static class Session {
        final ServerLevel level;final UUID operator,locoId,coachId;final String mode;
        final long started; final double x0,coachX0;
        double minGap,maxGap,maxSpeed,maxLocoZ,maxCoachZ,minLocoZ,minCoachZ,maxYawDelta;
        double prevLocoX,prevLocoZ; int ticks,stall;
        Session(ServerLevel level,UUID operator,UUID locoId,UUID coachId,String mode,
                LegacyPoweredLocomotive loco,SteamTender coach,double gap){
            this.level=level;this.operator=operator;this.locoId=locoId;this.coachId=coachId;
            this.mode=mode;this.started=level.getGameTime();this.x0=loco.getX();this.coachX0=coach.getX();
            minGap=gap;maxGap=gap;minLocoZ=maxLocoZ=loco.getZ();
            minCoachZ=maxCoachZ=coach.getZ();prevLocoX=loco.getX();prevLocoZ=loco.getZ();
        }
    }
    private static String f(double v){return String.format(Locale.ROOT,"%.3f",v);}
    private static String block(BlockState b){return BuiltInRegistries.BLOCK.getKey(b.getBlock()).toString();}
    private static String type(Entity e){return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    private static void log(String s){LOG.info(PREFIX+s);}
    private static void msg(ServerPlayer p,String s){if(p!=null)p.sendSystemMessage(Component.literal("[TC DEV50d2] "+s));}
    private static String worldMode(ServerLevel l){
        try{
            if(l.getServer().isDedicatedServer())return null;
            Path root=l.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if(root.getFileName()==null)return null;
            String folder=root.getFileName().toString();
            if(!STRAIGHT.equals(folder)&&!BRANCH.equals(folder))return null;
            Path marker=root.resolve(MARKER);
            if(!Files.isRegularFile(marker)||!CONTENT.equals(Files.readString(marker).trim()))return null;
            return STRAIGHT.equals(folder)?"STRAIGHT":"BRANCH";
        }catch(Exception ex){return null;}
    }
    public static boolean isActive(){return active!=null;}
    public static void clearAfterServerStop(){active=null;}
    private static Entity[] getPair(ServerLevel l,UUID a,UUID b){return new Entity[]{l.getEntity(a),l.getEntity(b)};}
    private static String baseline(ServerLevel l,UUID a,UUID b,boolean start,UUID driverId){
        if(!GP7.equals(a)||!COACH.equals(b))return "saved pair UUID changed";
        Entity[] e=getPair(l,a,b);
        if(!(e[0] instanceof LegacyPoweredLocomotive loco)||!(e[1] instanceof SteamTender coach)
                ||e[0].isRemoved()||e[1].isRemoved())return "saved GP7/coach not both loaded";
        if(!"traincraft:locomotive_diesel_gp7".equals(type(loco))
                ||!"traincraft:passenger_coach_blue".equals(type(coach)))return "train entity types changed";
        if(loco.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=STATION.asLong()
                ||coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=STATION.asLong()
                ||!"locomotive".equals(loco.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                ||!"coach_1".equals(coach.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE")))
            return "station ownership mismatch";
        if(!CouplingManager.areMutuallyLinked(loco,loco,coach,coach))return "mutual coupling failed";
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        if(gap<MIN_GAP||gap>MAX_GAP)return "coupling gap outside bounds "+f(gap);
        // DEV50d2a: SmallSteamLocomotive.tick() clears driver controls when no
        // ServerPlayer is the first passenger. The packet handler likewise
        // accepts W/S only from the actual mounted driver. No fake inputs.
        if (!(loco.getFirstPassenger() instanceof ServerPlayer pilot)
                || !driverId.equals(pilot.getUUID())
                || pilot.getVehicle()!=loco
                || loco.getPassengers().size()!=1
                || !coach.getPassengers().isEmpty())
            return "mount the GP7 as the only driver before starting the test";
        if(loco.getPowerAmount()<=0)return "GP7 reserve empty";
        if(start){
            if(Math.abs(loco.getX()-257.210D)>0.035D||Math.abs(coach.getX()-254.207D)>0.035D
                    ||Math.abs(loco.getZ()-104.5D)>0.07D||Math.abs(coach.getZ()-104.5D)>0.07D)
                return "this test copy has been moved; CREATE A FRESH COPY; do not reposition trains";
            if(!loco.isHandBrakeApplied()||loco.getDeltaMovement().horizontalDistance()>0.01D
                    ||coach.getDeltaMovement().horizontalDistance()>0.01D)return "train not parked";
        }
        return null;
    }
    private static Boolean switchState(ServerLevel l){
        int[][] c={{0,0,0},{1,0,1},{2,0,2},{3,0,3},{4,0,4},{5,0,5},
                   {2,1,9},{3,1,10},{4,1,11},{4,2,12},{5,2,13}};
        Boolean value=null;
        for(int[] a:c){
            BlockPos p=new BlockPos(261+a[0],-60,104+a[1]);
            if(!l.hasChunkAt(p))return null;
            BlockState state=l.getBlockState(p);
            if(!"traincraft:track_switch_parallel_right_segment".equals(block(state)))return null;
            Integer part=null;String facing=null;Boolean current=null;
            for(var v:state.getValues().entrySet()){
                if("assembly_part".equals(v.getKey().getName())&&v.getValue() instanceof Integer i)part=i;
                if("facing".equals(v.getKey().getName()))facing=String.valueOf(v.getValue());
                if("active".equals(v.getKey().getName())&&v.getValue() instanceof Boolean b)current=b;
            }
            if(part==null||part!=a[2]||!"east".equals(facing)||current==null)return null;
            if(value==null)value=current;
            if(!value.equals(current))return null;
        }
        for(int x=257;x<=260;x++){
            BlockPos p=new BlockPos(x,-60,104);
            if(!l.hasChunkAt(p)||!"traincraft:track_normal".equals(block(l.getBlockState(p))))return null;
        }
        return value;
    }
    private static boolean nearKnownRail(ServerLevel l,Entity vehicle){
        int y=-60; int x=(int)Math.floor(vehicle.getX()); int z=(int)Math.floor(vehicle.getZ());
        for(int xx=x-1;xx<=x+1;xx++)for(int zz=z-1;zz<=z+1;zz++){
            BlockPos p=new BlockPos(xx,y,zz);
            if(l.hasChunkAt(p)&&block(l.getBlockState(p)).startsWith("traincraft:track_"))return true;
        }
        return false;
    }
    private static String preflight(ServerLevel l,LegacyPoweredLocomotive loco){
        // Only the native exit from the initial switch is under test, up to
        // X<265.7. Do not assume rails after X266 or a mesh-safe turnout.
        for(int x=257;x<=266;x++){
            for(int z=(x<261?104:103);z<=(x<261?104:107);z++){
                BlockPos p=new BlockPos(x,-60,z);
                if(!l.hasChunkAt(p))return "unloaded path area "+p.toShortString();
            }
        }
        // Only static hazards in the narrow approach and switch envelope.
        // Shape overlaps are checked against an entity-sized corridor, not
        // rendered mesh. Fence at x258,z106 is deliberately outside it.
        for(int x=257;x<=265;x++){
            int minZ=x<261?104:104, maxZ=x<261?104:106;
            for(int z=minZ;z<=maxZ;z++){
                BlockPos rail=new BlockPos(x,-60,z);
                if(!l.hasChunkAt(rail))return "unloaded corridor";
                for(int y=-59;y<=-57;y++){
                    BlockPos p=new BlockPos(x,y,z);
                    BlockState state=l.getBlockState(p);
                    if(!state.getCollisionShape(l,p).isEmpty()){
                        return "obstruction inside branch candidate cell "+p.toShortString()+" "+block(state);
                    }
                }
            }
        }
        return null;
    }
    public static int command(CommandSourceStack src,ServerLevel l,BlockPos station,
                 UUID locoId,UUID coachId,boolean otherActive,String action){
        if(!(src.getEntity() instanceof ServerPlayer player)||!src.hasPermission(2)){
            src.sendFailure(Component.literal("DEV50d2 requires authorized in-world player. NO CHANGES"));return 0;
        }
        String mode=worldMode(l);
        if(mode==null){log("REFUSED action="+action+" WRONG_WORLD_OR_MARKER NO_CHANGES");
            src.sendFailure(Component.literal("DEV50d2 REFUSED: WRONG WORLD FOLDER/MARKER. Original protected. NO CHANGES"));return 0;}
        if(!STATION.equals(station)){
            src.sendFailure(Component.literal("DEV50d2: station changed. NO CHANGES"));return 0;}
        Boolean actual=switchState(l);
        if("status".equals(action)){
            Entity driverVehicle=player.getVehicle();
            String info="COPY_VALID=true mode="+mode+" switchActive="+(actual==null?"UNKNOWN":actual)
                +" expected="+("BRANCH".equals(mode)?"true":"false")+" state="+(active==null?"IDLE":"ACTIVE")
                +" mountedGP7="+(driverVehicle!=null&&GP7.equals(driverVehicle.getUUID()))
                +"; hold W when monitor is armed; branch mapping NOT YET CERTIFIED";
            log("STATUS "+info);src.sendSuccess(()->Component.literal("[TC DEV50d2] "+info),false);return 1;
        }
        if("stop".equals(action)){
            if(active!=null)halt(l,"MANUAL_STOP");
            else src.sendSuccess(()->Component.literal("[TC DEV50d2] IDLE NO_CHANGES"),false);
            return 1;
        }
        if(!"start".equals(action))return 0;
        if(active!=null||otherActive){src.sendFailure(Component.literal("DEV50d2 other test/departure active. NO CHANGES"));return 0;}
        if(actual==null||actual.booleanValue()!= "BRANCH".equals(mode)){
            log("REFUSED SWITCH_STATE mode="+mode+" actual="+actual+" NO_CHANGES");
            src.sendFailure(Component.literal("DEV50d2 REFUSED: switch setting does not match "+mode+" copy. Toggle with NORMAL switch interaction ONLY in TEST COPY; then recheck status. NO CHANGES"));return 0;
        }
        String issue=baseline(l,locoId,coachId,true,player.getUUID());
        Entity[] e=getPair(l,locoId,coachId);
        if(issue==null)issue=preflight(l,(LegacyPoweredLocomotive)e[0]);
        if(issue!=null){log("REFUSED START "+issue+" NO_CHANGES");src.sendFailure(Component.literal("DEV50d2 REFUSED: "+issue+". NO CHANGES"));return 0;}
        LegacyPoweredLocomotive loco=(LegacyPoweredLocomotive)e[0];SteamTender coach=(SteamTender)e[1];
        // Let the real rider/network packets own W/S. Only release the
        // previously applied parking brake when the test starts.
        loco.toggleHandBrake(player);
        if(loco.isHandBrakeApplied()){
            src.sendFailure(Component.literal("DEV50d2a brake release failed; NO MOVEMENT"));return 0;
        }
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        active=new Session(l,player.getUUID(),locoId,coachId,mode,loco,coach,gap);
        log("START copy=YES mode="+mode+" realMountedDriver=YES nativeControlPackets=YES constantXVelocity=NO"
            +" switchActive="+actual+" maxTicks="+MAX_TICKS+" capSpeed="+f(SPEED_STOP)
            +" targetX="+f(TARGET)+" locomotionPath=UNVERIFIED");
        src.sendSuccess(()->Component.literal("[TC DEV50d2a] MONITOR ARMED: hold W to drive the GP7 normally. Auto-stop by X265.7 or safety bounds. Release W at stop; /tcrailway service turnoutbranch stop"),false);
        return 1;
    }
    private static void park(ServerLevel l,Session s){
        Entity[] e=getPair(l,s.locoId,s.coachId);
        if(e[0] instanceof LegacyPoweredLocomotive loco&&!loco.isRemoved()){
            loco.setDriverControls(false,false,true);
            if(!loco.isHandBrakeApplied()){
                ServerPlayer player=l.getServer().getPlayerList().getPlayer(s.operator);
                loco.toggleHandBrake(player);
            }
            loco.setDeltaMovement(Vec3.ZERO);
        }
        if(e[1] instanceof SteamTender coach&&!coach.isRemoved())coach.setDeltaMovement(Vec3.ZERO);
    }
    private static void halt(ServerLevel l,String why){
        Session s=active;if(s==null)return;
        park(l,s);
        Entity[] e=getPair(l,s.locoId,s.coachId);
        double locoZ=e[0]==null?Double.NaN:e[0].getZ();
        double coachZ=e[1]==null?Double.NaN:e[1].getZ();
        boolean diverged=s.maxLocoZ>104.75D||s.minLocoZ<104.25D;
        String result="STOP reason="+why+" mode="+s.mode+" ticks="+s.ticks
           +" xGP7="+(e[0]==null?"UNLOADED":f(e[0].getX()))
           +" xCOACH="+(e[1]==null?"UNLOADED":f(e[1].getX()))
           +" zGP7="+f(locoZ)+" zCOACH="+f(coachZ)
           +" maxLocoZ="+f(s.maxLocoZ)+" minLocoZ="+f(s.minLocoZ)
           +" maxCoachZ="+f(s.maxCoachZ)+" minCoachZ="+f(s.minCoachZ)
           +" maxSpeed="+f(s.maxSpeed)+" minGap="+f(s.minGap)+" maxGap="+f(s.maxGap)
           +" maxYawDelta="+f(s.maxYawDelta)+" lateralBranchObserved="+diverged
           +" brake="+(e[0] instanceof LegacyPoweredLocomotive ll?ll.isHandBrakeApplied():"UNVERIFIED")
           +" PASSENGER_ROUTE_CERTIFICATION=NOT_CERTIFIED";
        log(result);msg(l.getServer().getPlayerList().getPlayer(s.operator),result);active=null;
    }
    public static void stopForShutdown(ServerLevel l){if(active!=null)halt(l,"SERVER_STOPPING");}
    public static void tick(ServerLevel l){
        Session s=active;if(s==null)return;
        try{
            if(l!=s.level||!s.mode.equals(worldMode(l))){halt(s.level,"WORLD_GATE_LOST");return;}
            ServerPlayer p=l.getServer().getPlayerList().getPlayer(s.operator);
            if(p==null||p.level()!=l||p.distanceToSqr(251.5,p.getY(),104.5)>96*96){halt(l,"OPERATOR_LEFT");return;}
            Entity[] e=getPair(l,s.locoId,s.coachId);
            if(!(e[0] instanceof LegacyPoweredLocomotive loco)||!(e[1] instanceof SteamTender coach)){
                halt(l,"VEHICLES_UNLOADED");return;
            }
            String issue=baseline(l,s.locoId,s.coachId,false,s.operator);
            if(issue!=null){halt(l,"BASELINE_"+issue);return;}
            Boolean sw=switchState(l);
            if(sw==null||sw.booleanValue()!="BRANCH".equals(s.mode)){
                halt(l,"SWITCH_STATE_CHANGED");return;
            }
            double speed=Math.max(loco.getDeltaMovement().horizontalDistance(),coach.getDeltaMovement().horizontalDistance());
            double gap=Math.sqrt(loco.distanceToSqr(coach));
            s.minGap=Math.min(s.minGap,gap);s.maxGap=Math.max(s.maxGap,gap);
            s.maxSpeed=Math.max(s.maxSpeed,speed);
            s.minLocoZ=Math.min(s.minLocoZ,loco.getZ());s.maxLocoZ=Math.max(s.maxLocoZ,loco.getZ());
            s.minCoachZ=Math.min(s.minCoachZ,coach.getZ());s.maxCoachZ=Math.max(s.maxCoachZ,coach.getZ());
            s.maxYawDelta=Math.max(s.maxYawDelta,Math.abs(loco.getYRot()-90.0D));
            s.ticks++;
            // Genuine turnout progress can be in Z as well as X.
            double stepDist=Math.hypot(loco.getX()-s.prevLocoX,loco.getZ()-s.prevLocoZ);
            if(stepDist<0.001D)s.stall++;else s.stall=0;
            s.prevLocoX=loco.getX();s.prevLocoZ=loco.getZ();
            if(speed>SPEED_STOP||Math.abs(loco.getY()+59.9375D)>0.40D
                    ||Math.abs(coach.getY()+59.9375D)>0.40D
                    ||loco.getZ()<103.3D||loco.getZ()>106.65D
                    ||coach.getZ()<103.3D||coach.getZ()>106.65D
                    ||loco.getX()-s.x0>MAX_TRAVEL||coach.getX()-s.coachX0>MAX_TRAVEL
                    ||loco.getX()<s.x0-0.10D||coach.getX()<s.coachX0-0.10D) {
                halt(l,"SAFETY_ENVELOPE speed="+f(speed));return;
            }
            if(!nearKnownRail(l,loco)||!nearKnownRail(l,coach)){
                halt(l,"RAIL_SUPPORT_LOST");return;
            }
            AABB humanLane=new AABB(loco.getX()+0.45D,loco.getY()-0.40D,loco.getZ()-0.85D,
                    loco.getX()+1.65D,loco.getY()+2.20D,loco.getZ()+0.85D);
            if(!l.getEntitiesOfClass(LivingEntity.class,humanLane,
                    human->!human.isRemoved()&&human!=p).isEmpty()){
                halt(l,"LIVING_ENTITY_AHEAD");return;
            }
            if(loco.getX()>=TARGET-0.10D){halt(l,"TARGET_REACHED");return;}
            if(s.ticks>=MAX_TICKS){halt(l,"TIME_LIMIT");return;}
            if(s.stall>=STALL_TICKS){halt(l,"NATIVE_DRIVE_STALLED");return;}
            // DEV50d2a: Never set driver controls here. The actual seated
            // player must hold W and send the normal validated control packet.
            // No synthetic vector or throttle; accepted physics owns the turn.
            if(s.ticks==40&&s.stall>=40)
                msg(p,"No movement yet: close chat and HOLD W while seated in the GP7.");
            if(s.ticks%10==0)log("TICK mode="+s.mode+" n="+s.ticks
                    +" gp7=("+f(loco.getX())+","+f(loco.getZ())+")"
                    +" coach=("+f(coach.getX())+","+f(coach.getZ())+")"
                    +" gap="+f(gap)+" speed="+f(speed)+" yaw="+f(loco.getYRot())
                    +" sw="+sw+" native=true");
        }catch(Exception ex){log("ERROR "+ex.getClass().getSimpleName()+" "+ex.getMessage());halt(l,"EXCEPTION_FAIL_CLOSED");}
    }
}
