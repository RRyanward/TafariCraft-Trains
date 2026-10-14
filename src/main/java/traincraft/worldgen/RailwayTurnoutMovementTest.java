/* TafariCraft - Trains DEV50d1 / disposable-world ONLY controlled test.
 * TEST PROBE, NOT release driver/autopilot or turnout route certification.
 * NEVER change original recovery world. No chunk tickets, teleport, switching,
 * coupling changes, new entities, staff edits, schedule or route auto-start.
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
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;

public final class RailwayTurnoutMovementTest {
    private static final Logger LOG=Traincraft.LOGGER;
    private static final String PREFIX="[TC-DEV50D1] ";
    private static final String WORLD_FOLDER="New World - Railway DEV50D1 Turnout Test";
    private static final String MARKER_NAME="DEV50D1_DISPOSABLE_TEST_COPY.marker";
    private static final String MARKER_TEXT="DEV50D1_DISPOSABLE_WORLD_ONLY_v1";
    private static final BlockPos STATION=new BlockPos(251,-60,104);
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    // Stop once both vehicles have entered the first switch cells (not a full
    // branch/route traversal). Dedicated world only; slow low-energy tick.
    private static final double BPT=0.025D;
    private static final double TARGET_X=265.080D;
    private static final int MAX_TICKS=520;
    private static final double MAX_SPEED=0.072D;
    private static Session active;
    private RailwayTurnoutMovementTest() {}
    private record Session(ServerLevel level,UUID operator,UUID locoId,UUID coachId,
                           long startTick,double locoStart,double coachStart,
                           double locoYaw,double coachYaw,
                           double maxSpeed,double minGap,double maxGap,double maxZDev,int ticks) {
        Session measure(ServerLevel level,LegacyPoweredLocomotive loco,SteamTender coach,
                        double gap,double speed,double zdev) {
            return new Session(this.level,operator,locoId,coachId,startTick,locoStart,coachStart,
                   locoYaw,coachYaw,Math.max(maxSpeed,speed),Math.min(minGap,gap),
                   Math.max(maxGap,gap),Math.max(maxZDev,zdev),ticks+1);
        }
    }
    private static String fmt(double n){return String.format(Locale.ROOT,"%.3f",n);}
    private static String type(Entity e){return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    private static String block(BlockState s){return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();}
    private static void log(String msg){LOG.info(PREFIX+msg);}
    private static void msg(ServerPlayer p,String content){if(p!=null) p.sendSystemMessage(Component.literal("[TC DEV50d1] "+content));}

    /** Check the actual opened save folder, not the display name shown in menu.
     * Both exact folder AND a marker made only by the copy script are required. */
    private static String testCopyGate(ServerLevel level) {
        if(level.getServer().isDedicatedServer()) return "dedicated server not allowed";
        final Path root;
        try{root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();}
        catch(Exception e){return "cannot resolve actual save path";}
        if(root.getFileName()==null || !WORLD_FOLDER.equals(root.getFileName().toString()))
            return "WRONG WORLD FOLDER "+root.getFileName()+" (original is PROTECTED)";
        Path marker=root.resolve(MARKER_NAME);
        try {
            if(!Files.isRegularFile(marker) || !MARKER_TEXT.equals(Files.readString(marker).trim()))
                return "missing/mismatched disposable test marker";
        } catch(Exception e){return "test marker cannot be read";}
        return null;
    }
    public static boolean isActive(){return active!=null;}
    public static void clearAfterServerStop(){active=null;}

    private static Entity[] pair(ServerLevel l,UUID a,UUID b){return new Entity[]{l.getEntity(a),l.getEntity(b)};}
    private static String baseline(ServerLevel l,UUID a,UUID b,boolean requireStartingPose) {
        if(!GP7.equals(a)||!COACH.equals(b))return "recorded train UUIDs differ from accepted saved pair";
        Entity[] e=pair(l,a,b);
        if(!(e[0] instanceof LegacyPoweredLocomotive loco)||!(e[1] instanceof SteamTender coach)
                ||e[0].isRemoved()||e[1].isRemoved())return "staged pair unloaded/missing";
        if(!"traincraft:locomotive_diesel_gp7".equals(type(loco))
                ||!"traincraft:passenger_coach_blue".equals(type(coach)))return "train type mismatch";
        if(loco.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=STATION.asLong()
                ||coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=STATION.asLong()
                ||!"locomotive".equals(loco.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                ||!"coach_1".equals(coach.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE")))
            return "staged ownership mismatch";
        if(!CouplingManager.areMutuallyLinked(loco,loco,coach,coach))return "coupling not mutual";
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        if(gap<2.80D||gap>3.25D)return "spacing outside 2.80..3.25 envelope="+fmt(gap);
        if(!loco.getPassengers().isEmpty()||!coach.getPassengers().isEmpty())return "train occupied";
        if(loco.getPowerAmount()<=0)return "GP7 traction reserve depleted";
        if(requireStartingPose){
            if(Math.abs(loco.getX()-257.210D)>0.035D||Math.abs(coach.getX()-254.207D)>0.035D
                    ||Math.abs(loco.getZ()-104.5D)>0.07D||Math.abs(coach.getZ()-104.5D)>0.07D)
                return "saved start positions differ; no repositioning allowed";
            if(!loco.isHandBrakeApplied()||loco.getDeltaMovement().horizontalDistance()>0.01D
                    ||coach.getDeltaMovement().horizontalDistance()>0.01D)return "pair not parked";
        }
        return null;
    }
    private static String routeSignature(ServerLevel level) {
        for(int x=257;x<=260;x++) {
            BlockPos p=new BlockPos(x,STATION.getY(),STATION.getZ());
            if(!level.hasChunkAt(p))return "unloaded straight cell "+p.toShortString();
            BlockState s=level.getBlockState(p);
            if(!"traincraft:track_normal".equals(block(s))||!s.hasProperty(RailBlock.SHAPE)
                    ||s.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)
                return "changed straight rail at "+p.toShortString();
        }
        int[][] c={{0,0,0},{1,0,1},{2,0,2},{3,0,3},{4,0,4},{5,0,5},
                   {2,1,9},{3,1,10},{4,1,11},{4,2,12},{5,2,13}};
        Boolean active=null;
        for(int[] spec:c) {
            BlockPos pos=new BlockPos(261+spec[0],STATION.getY(),104+spec[1]);
            if(!level.hasChunkAt(pos))return "unloaded turnout cell "+pos.toShortString();
            BlockState s=level.getBlockState(pos);
            if(!"traincraft:track_switch_parallel_right_segment".equals(block(s)))
                return "turnout block mismatch at "+pos.toShortString();
            String properties=s.getValues().toString();
            // Properties are present as strongly typed keys; string on value
            // listing is diagnostic only, fail-closed on absent/inconsistent.
            Integer part=null;Boolean stateActive=null;String facing=null;
            for(var p:s.getValues().entrySet()) {
                if("assembly_part".equals(p.getKey().getName()) && p.getValue() instanceof Integer i)part=i;
                if("active".equals(p.getKey().getName()) && p.getValue() instanceof Boolean b)stateActive=b;
                if("facing".equals(p.getKey().getName()))facing=String.valueOf(p.getValue());
            }
            if(part==null||part!=spec[2]||stateActive==null||!"east".equals(facing))
                return "turnout part/facing/active mismatch "+pos.toShortString()+" "+properties;
            if(active==null)active=stateActive;
            if(!active.equals(stateActive))return "inconsistent switch-active state";
        }
        // The previously audited, exact layout used active=false. We don't
        // assume its branch mapping; any change demands separate review.
        if(Boolean.TRUE.equals(active))return "switch active=true; original state changed";
        return null;
    }
    private static String clearance(ServerLevel level,LegacyPoweredLocomotive loco,SteamTender coach) {
        // Conservative axis-aligned SERVER entity box sweep to X=265.2.
        // Does NOT certify mesh/rod/bogie animation or real turn transitions.
        for(Entity entity:new Entity[]{loco,coach}) {
            AABB body=entity.getBoundingBox();
            double dx=265.20D-entity.getX();
            AABB scan=new AABB(body.minX,body.minY,body.minZ,body.maxX+dx,body.maxY,body.maxZ);
            for(int x=(int)Math.floor(scan.minX);x<(int)Math.ceil(scan.maxX);x++)
                for(int y=(int)Math.floor(scan.minY);y<(int)Math.ceil(scan.maxY);y++)
                    for(int z=(int)Math.floor(scan.minZ);z<(int)Math.ceil(scan.maxZ);z++) {
                        BlockPos p=new BlockPos(x,y,z);
                        if(!level.hasChunkAt(p))return "unloaded corridor at "+p.toShortString();
                        BlockState s=level.getBlockState(p);
                        if(block(s).startsWith("traincraft:track_"))continue;
                        VoxelShape shape=s.getCollisionShape(level,p);
                        if(!shape.isEmpty())for(AABB piece:shape.toAabbs())
                            if(piece.move(p).intersects(scan))
                                return "server box corridor obstacle at "+p.toShortString()+" "+block(s);
                    }
        }
        AABB forward=new AABB(loco.getX()+0.5D,loco.getY()-0.35D,loco.getZ()-0.48D,
                265.30D,loco.getY()+1.9D,loco.getZ()+0.48D);
        if(!level.getEntitiesOfClass(LivingEntity.class,forward,e->!e.isRemoved()).isEmpty())
            return "person/animal ahead of GP7";
        return null;
    }
    public static int command(CommandSourceStack src,ServerLevel level,BlockPos station,
                              UUID locoUuid,UUID coachUuid,boolean departureActive,String action){
        if(!(src.getEntity() instanceof ServerPlayer player)||!src.hasPermission(2)){
            src.sendFailure(Component.literal("DEV50d1: authorized player required. NO CHANGES"));return 0;
        }
        String copy=testCopyGate(level);
        if(copy!=null) {
            log("COPY_GATE_REFUSED action="+action+" reason="+copy+" NO_CHANGES");
            src.sendFailure(Component.literal("DEV50d1 REFUSED: "+copy+". NO CHANGES."));return 0;
        }
        if(!STATION.equals(station)) {
            src.sendFailure(Component.literal("DEV50d1: station changed. NO CHANGES"));return 0;
        }
        if("status".equals(action)) {
            String details=active==null?"IDLE":("ACTIVE since "+active.startTick()+" ticks="+active.ticks());
            src.sendSuccess(()->Component.literal("[TC DEV50d1] VERIFIED DISPOSABLE COPY; "+details
                    +". Original recovery world is untouched by this test."),false);
            log("STATUS COPY_VALID=true state="+details);return 1;
        }
        if("stop".equals(action)) {
            if(active!=null) halt(level,"manual stop by "+player.getGameProfile().getName());
            else {
                // Idle stop does not touch any vehicle; refuses to make edits.
                src.sendSuccess(()->Component.literal("[TC DEV50d1] IDLE: NO CHANGES"),false);
            }
            return 1;
        }
        if(!"start".equals(action))return 0;
        if(active!=null||departureActive){src.sendFailure(Component.literal("DEV50d1 test already active / manual shunt active. NO CHANGES"));return 0;}
        String pre=baseline(level,locoUuid,coachUuid,true);
        if(pre==null)pre=routeSignature(level);
        if(pre==null){Entity[] e=pair(level,locoUuid,coachUuid);
            pre=clearance(level,(LegacyPoweredLocomotive)e[0],(SteamTender)e[1]);}
        if(pre!=null) {
            log("START_REFUSED reason="+pre+" NO_CHANGES");
            src.sendFailure(Component.literal("DEV50d1 START REFUSED: "+pre+". NO CHANGES"));return 0;
        }
        LegacyPoweredLocomotive loco=(LegacyPoweredLocomotive)level.getEntity(locoUuid);
        SteamTender coach=(SteamTender)level.getEntity(coachUuid);
        // Only this explicitly commanded copy-only probe can release brakes.
        loco.setDriverControls(false,false,false);
        loco.toggleHandBrake(player);
        if(loco.isHandBrakeApplied()) {
            loco.setDriverControls(false,false,true);
            src.sendFailure(Component.literal("DEV50d1 brake release refused. NO MOVEMENT"));return 0;
        }
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        active=new Session(level,player.getUUID(),locoUuid,coachUuid,level.getGameTime(),
                loco.getX(),coach.getX(),loco.getYRot(),coach.getYRot(),0D,gap,gap,0D,0);
        log("START copy=YES station="+station.toShortString()+" gp7="+locoUuid+" coach="+coachUuid
                +" x0="+fmt(loco.getX())+" xTarget="+fmt(TARGET_X)
                +" bpt="+fmt(BPT)+" maxTicks="+MAX_TICKS+" manualOnly=true");
        src.sendSuccess(()->Component.literal("[TC DEV50d1] TEST COPY ONLY: bounded turnout entry begun."
                +" Automatic stop before X=265.08; emergency: /tcrailway service turnouttest stop."
                +" No timetable, no teleport, no route certification."),true);
        return 1;
    }
    private static void park(ServerLevel l,Session s) {
        Entity[] entities=pair(l,s.locoId(),s.coachId());
        if(entities[0] instanceof LegacyPoweredLocomotive loco && !loco.isRemoved()) {
            loco.setDriverControls(false,false,true);
            if(!loco.isHandBrakeApplied()) {
                ServerPlayer p=l.getServer().getPlayerList().getPlayer(s.operator());
                loco.toggleHandBrake(p);
            }
            loco.setDeltaMovement(Vec3.ZERO);
        }
        if(entities[1] instanceof SteamTender coach && !coach.isRemoved())coach.setDeltaMovement(Vec3.ZERO);
    }
    private static void halt(ServerLevel l,String reason){
        Session s=active;if(s==null)return;
        park(l,s);
        Entity[] e=pair(l,s.locoId(),s.coachId());
        String report="STOP reason="+reason+" ticks="+s.ticks()+" maxSpeed="+fmt(s.maxSpeed())
                +" minGap="+fmt(s.minGap())+" maxGap="+fmt(s.maxGap())
                +" maxZDeviation="+fmt(s.maxZDev())
                +" GP7="+(e[0]!=null?e[0].position():"unloaded")
                +" COACH="+(e[1]!=null?e[1].position():"unloaded")
                +" brake="+(e[0] instanceof LegacyPoweredLocomotive loco?loco.isHandBrakeApplied():"unknown")
                +" ROUTE_CERTIFICATION=NOT_CERTIFIED";
        log(report);
        ServerPlayer p=l.getServer().getPlayerList().getPlayer(s.operator());
        msg(p,report);
        active=null;
    }
    public static void stopForShutdown(ServerLevel level){if(active!=null)halt(level,"server stopping");}
    public static void tick(ServerLevel level){
        Session s=active;if(s==null)return;
        try {
            // Stop even when world changed or player departed. No resume
            // without new command, and no motion ever scheduled on world load.
            if(level!=s.level()){halt(s.level(),"world instance changed");return;}
            String copy=testCopyGate(level);
            if(copy!=null){halt(level,"copy-only gate lost: "+copy);return;}
            ServerPlayer p=level.getServer().getPlayerList().getPlayer(s.operator());
            if(p==null||p.level()!=level||p.distanceToSqr(251.5,p.getY(),104.5)>96*96){halt(level,"operator left");return;}
            Entity[] e=pair(level,s.locoId(),s.coachId());
            if(!(e[0] instanceof LegacyPoweredLocomotive loco)||!(e[1] instanceof SteamTender coach)){
                halt(level,"staged vehicles unloaded");return;
            }
            String check=baseline(level,s.locoId(),s.coachId(),false);
            if(check!=null){halt(level,check);return;}
            double moved=loco.getX()-s.locoStart();
            double coachMoved=coach.getX()-s.coachStart();
            double gap=Math.sqrt(loco.distanceToSqr(coach));
            double speed=Math.max(loco.getDeltaMovement().horizontalDistance(),coach.getDeltaMovement().horizontalDistance());
            double zDev=Math.max(Math.abs(loco.getZ()-104.5D),Math.abs(coach.getZ()-104.5D));
            active=s.measure(level,loco,coach,gap,speed,zDev);s=active;
            if(moved< -0.10D||moved>8.00D||coachMoved< -0.10D||coachMoved>8.00D
                    ||speed>MAX_SPEED||zDev>0.20D
                    ||Math.abs(loco.getY()+59.9375D)>0.35D
                    ||Math.abs(coach.getY()+59.9375D)>0.35D) {
                halt(level,"motion envelope violated (moved="+fmt(moved)+" coach="+fmt(coachMoved)
                        +" speed="+fmt(speed)+" zDev="+fmt(zDev)+")");return;
            }
            if(!CouplingManager.areMutuallyLinked(loco,loco,coach,coach)){
                halt(level,"coupling broke");return;
            }
            if(moved>=TARGET_X-s.locoStart()-2*BPT) {
                halt(level,"TARGET_REACHED bounded turnout-entry probe (GP7+coach on switch area)");return;
            }
            if(level.getGameTime()-s.startTick()>=MAX_TICKS){halt(level,"time limit reached");return;}
            if(routeSignature(level)!=null){halt(level,"switch/track state changed while moving");return;}
            BlockPos locoRail=new BlockPos((int)Math.floor(loco.getX()),-60,104);
            BlockPos coachRail=new BlockPos((int)Math.floor(coach.getX()),-60,104);
            if(!level.hasChunkAt(locoRail)||!level.hasChunkAt(coachRail)){
                halt(level,"train rail chunk unloaded");return;
            }
            // Reject derailment or unexpected rail type; NEVER drive a switch
            // branch by inferring shape=east_west from its blockstate.
            if(!block(level.getBlockState(locoRail)).startsWith("traincraft:track_")
                    ||!block(level.getBlockState(coachRail)).startsWith("traincraft:track_")){
                halt(level,"train left recognized track");return;
            }
            AABB lane=new AABB(loco.getX()+0.5D,loco.getY()-0.35D,loco.getZ()-0.48D,
                    loco.getX()+1.9D,loco.getY()+1.9D,loco.getZ()+0.48D);
            if(!level.getEntitiesOfClass(LivingEntity.class,lane,living->!living.isRemoved()).isEmpty()){
                halt(level,"person/animal entered lane");return;
            }
            // Existing locomotive/FC12 coupler engine handles coach; no
            // teleport, setPos, native path modification, or link changes.
            Vec3 velocity=loco.getDeltaMovement();
            loco.setDriverControls(false,false,false);
            loco.setDeltaMovement(BPT,velocity.y,0D);
            if(s.ticks()%10==0)
                log("TICK n="+s.ticks()+" locoX="+fmt(loco.getX())
                   +" coachX="+fmt(coach.getX())+" gap="+fmt(gap)
                   +" zDev="+fmt(zDev)+" speed="+fmt(speed)
                   +" locoYaw="+fmt(loco.getYRot())+" coachYaw="+fmt(coach.getYRot())
                   +" rail="+block(level.getBlockState(locoRail)));
        }catch(Exception ex){
            log("EXCEPTION "+ex.getClass().getSimpleName()+" "+ex.getMessage());
            halt(level,"exception fail closed");
        }
    }
}
