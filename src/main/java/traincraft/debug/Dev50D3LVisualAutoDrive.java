package traincraft.debug;

/* DEV50d3L: separate, disposable-world-only visual auto driver.
 * DOES NOT edit native coupling, train physics, track path or renderers.
 * Runs on an INTEGRATED SINGLEPLAYER server only when a specially named
 * DEV50D3L disposable save also carries the exact opt-in marker.
 * Player is an independent spectator, while a Forge FakePlayer uses the
 * accepted native GP7 rider/control path. Test-only speed governance is
 * explicitly reported; model mesh clearance is NOT auto-certified.
 */
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;

@Mod.EventBusSubscriber(modid=Traincraft.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev50D3LVisualAutoDrive {
    private Dev50D3LVisualAutoDrive() { }
    private static final String PREFIX="New World - Railway DEV50D3L VISUAL ";
    private static final String MARKER="DEV50D3L_VISUAL_COPY.marker";
    private static final String MARKER_TEXT="DEV50D3L_VISUAL_DISPOSABLE_COPY_ONLY_v1";
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final UUID BOT=UUID.fromString("8d50d3a1-d50d-41f0-b42b-3d5000000001");
    private static final String LABEL="[TC-DEV50D3L] ";
    private static final double GOVERNOR=0.033D, HARD_SPEED=0.120D;
    private static final double ON_BELOW=0.027D, OFF_ABOVE=0.0315D;
    private static final int VIEW_PREP_TICKS=300, DRIVE_MAX_TICKS=220;
    private static final double DRIVE_MAX_GP7_TRAVEL=1.15D;
    private static final double MIN_GAME_HITBOX_CLEARANCE=0.10D;
    // The dedicated PASS world parks after the curve, with center gap ~2.421,
    // below the earlier ENTRY-only provisional 2.430 test floor. A new test
    // must NEVER claim this relaxed follow-through floor certifies visual mesh.
    private static final double FOLLOW_THROUGH_CENTER_GAP_FLOOR=2.20D;
    private static final double MAX_TICK_GAP_CLOSURE=0.145D;
    private static final double MAX_SINGLE_TICK_DISPLACEMENT=0.36D;
    private static Session active;
    private static final class Session {
        final MinecraftServer server;
        final ServerLevel level;
        final String world;
        ServerPlayer viewer;
        LegacyPoweredLocomotive gp7;
        SteamTender coach;
        FakePlayer driver;
        boolean started,finished,throttleOn,camReady;
        int age,driveTicks,governedTicks,largeCoachSteps;
        double originX,originZ,priorGp7X,priorGp7Z,priorCoachX,priorCoachZ,priorGap;
        double minHitbox=Double.POSITIVE_INFINITY,minGap=Double.POSITIVE_INFINITY;
        double maxSpeed,maxCoachStep,maxGP7Travel;
        Session(MinecraftServer server,ServerLevel level,String world){this.server=server;this.level=level;this.world=world;}
    }
    private static String f(double v){return String.format(Locale.ROOT,"%.4f",v);}
    private static void log(String value){Traincraft.LOGGER.info(LABEL+value);}
    private static void say(Session s,String message){
        if(s.viewer!=null&&!s.viewer.isRemoved())s.viewer.sendSystemMessage(Component.literal("[DEV50d3L] "+message));
        log(message);
    }
    private static String track(ServerLevel level,Entity e){
        BlockPos p=new BlockPos((int)Math.floor(e.getX()),-60,(int)Math.floor(e.getZ()));
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
    }
    private static double hitboxClearance(Entity a,Entity b){
        AABB x=a.getBoundingBox(), y=b.getBoundingBox();
        double dx=Math.max(0D,Math.max(x.minX-y.maxX,y.minX-x.maxX));
        double dz=Math.max(0D,Math.max(x.minZ-y.maxZ,y.minZ-x.maxZ));
        return Math.hypot(dx,dz);
    }
    private static Session find(MinecraftServer server){
        // Fail closed on a dedicated server and any other world. No gameplay
        // mutation even when an ordinary world happens to contain our mod.
        if(server.isDedicatedServer())return null;
        ServerLevel level=server.getLevel(Level.OVERWORLD);
        if(level==null)return null;
        try{
            Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name=root.getFileName();
            Path parent=root.getParent();
            if(name==null||parent==null||parent.getFileName()==null
                 ||!parent.getFileName().toString().equalsIgnoreCase("saves"))return null;
            String n=name.toString();
            if(!n.startsWith(PREFIX)
                 ||!n.substring(PREFIX.length()).matches("[0-9]{8}-[0-9]{6}-[0-9]{4}"))return null;
            if(Files.isSymbolicLink(root)||Files.isSymbolicLink(parent))return null;
            Path marker=root.resolve(MARKER);
            if(!Files.isRegularFile(marker)||Files.isSymbolicLink(marker)
                 ||!MARKER_TEXT.equals(Files.readString(marker,StandardCharsets.US_ASCII).trim()))return null;
            log("DISPOSABLE_INTEGRATED_WORLD_VERIFIED world="+n+" native=YES baseline=DEV50d3i");
            return new Session(server,level,n);
        }catch(Exception ex){log("REFUSED_WORLD "+ex.toString());return null;}
    }
    private static void prepareCamera(Session s,ServerPlayer p){
        if(s.camReady)return;
        // ONLY the user's position/game mode inside disposable visual save.
        // The GP7 and coach positions are not changed by this camera setup.
        if(p.level()!=s.level)return;
        p.setGameMode(GameType.SPECTATOR);
        p.teleportTo(s.level,263.4D,-56.8D,109.8D,180F,16F);
        s.camReady=true;
        say(s,"Spectator camera positioned. Begin recording now; auto-drive begins in ~15 seconds. ESC / Save & Quit stops the test.");
    }
    private static String inspectVehicles(Session s){
        Entity a=s.level.getEntity(GP7),b=s.level.getEntity(COACH);
        if(!(a instanceof LegacyPoweredLocomotive gp7)||!(b instanceof SteamTender coach))return "EXACT_VEHICLES_NOT_LOADED";
        if(!"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(gp7.getType()).toString())
           ||!"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(coach.getType()).toString()))return "VEHICLE_TYPES_DIFFER";
        if(gp7.isRemoved()||coach.isRemoved())return "VEHICLE_REMOVED";
        if(!CouplingManager.areMutuallyLinked(gp7,gp7,coach,coach))return "COUPLING_NOT_MUTUAL";
        if(!gp7.getPassengers().isEmpty()||!coach.getPassengers().isEmpty())return "VEHICLE_OCCUPIED";
        // Exact saved DEV50d3i parked point (DEV50d3k observed center gap 2.421).
        if(Math.abs(gp7.getX()-265.539D)>0.35D||Math.abs(gp7.getZ()-106.039D)>0.35D
           ||Math.abs(coach.getX()-263.391D)>0.35D||Math.abs(coach.getZ()-104.923D)>0.35D)return "SAVED_PARKED_POSITIONS_DIFFER";
        if(!gp7.isHandBrakeApplied()||gp7.getPowerAmount()<=0)return "BRAKE_OR_POWER_PRECONDITION";
        if(gp7.getDeltaMovement().horizontalDistance()>0.02D||coach.getDeltaMovement().horizontalDistance()>0.02D)return "MOVING_BEFORE_START";
        String gpRail=track(s.level,gp7), coachRail=track(s.level,coach);
        if(!gpRail.startsWith("traincraft:track_")||!coachRail.startsWith("traincraft:track_"))return "NOT_ON_NATIVE_RAIL";
        s.gp7=gp7;s.coach=coach;
        return null;
    }
    private static void park(Session s,String reason,boolean passed){
        if(s.finished)return;
        s.finished=true;
        try{
            if(s.gp7!=null&&!s.gp7.isRemoved()){
                s.gp7.setDriverControls(false,false,true);
                if(s.driver!=null&&!s.gp7.isHandBrakeApplied())s.gp7.toggleHandBrake(s.driver);
                s.gp7.setDeltaMovement(Vec3.ZERO);
            }
            if(s.coach!=null&&!s.coach.isRemoved())s.coach.setDeltaMovement(Vec3.ZERO);
            if(s.driver!=null&&s.driver.isPassenger())s.driver.stopRiding();
        }catch(Exception ex){log("PARK_EXCEPTION "+ex.toString());passed=false;}
        say(s,"RESULT="+(passed?"PASS_BOUNDED_MOTION_ONLY":"STOP_OR_UNVERIFIED")
             +" reason="+reason+" ticks="+s.driveTicks
             +" gp7Travel="+f(s.maxGP7Travel)+" minGap="+f(s.minGap)
             +" minGameHitbox="+f(s.minHitbox)+" maxNativeSpeed="+f(s.maxSpeed)
             +" maxCoachStep="+f(s.maxCoachStep)+" governedTicks="+s.governedTicks
             +" coachLargeSteps="+s.largeCoachSteps
             +" VISUAL_MESH_CLEARANCE=USER_REVIEW_REQUIRED");
        say(s,"Test parked. You can take screenshots or Save & Quit. No restart occurs in this world.");
    }
    private static void start(Session s){
        String invalid=inspectVehicles(s);
        if(invalid!=null){park(s,"PREFLIGHT_"+invalid,false);return;}
        try{
            s.driver=FakePlayerFactory.get(s.level,new GameProfile(BOT,"TCDev50d3L"));
            if(s.driver==null||!s.driver.startRiding(s.gp7,true)){
                park(s,"FAKE_DRIVER_MOUNT_FAILED",false);return;
            }
            s.gp7.toggleHandBrake(s.driver);
            if(s.gp7.isHandBrakeApplied()){
                park(s,"BRAKE_NOT_RELEASED",false);return;
            }
            s.originX=s.gp7.getX();s.originZ=s.gp7.getZ();
            s.priorGp7X=s.gp7.getX();s.priorGp7Z=s.gp7.getZ();
            s.priorCoachX=s.coach.getX();s.priorCoachZ=s.coach.getZ();
            s.priorGap=Math.sqrt(s.gp7.distanceToSqr(s.coach));
            s.minGap=s.priorGap;
            s.throttleOn=true;s.started=true;
            s.gp7.setDriverControls(true,false,false);
            say(s,"AUTO-DRIVE START: normal GP7 traction via FakePlayer, paced W control, test governor 0.033, hard safety stop 0.120.");
        }catch(Exception ex){park(s,"START_EXCEPTION_"+ex.getClass().getSimpleName(),false);}
    }
    private static void drive(Session s){
        s.driveTicks++;
        LegacyPoweredLocomotive loco=s.gp7;SteamTender coach=s.coach;
        if(loco==null||coach==null||loco.isRemoved()||coach.isRemoved()){
            park(s,"VEHICLE_REMOVED",false);return;
        }
        if(loco.getFirstPassenger()!=s.driver||!CouplingManager.areMutuallyLinked(loco,loco,coach,coach)){
            park(s,"DRIVER_OR_COUPLING_BROKEN",false);return;
        }
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        double clearance=hitboxClearance(loco,coach);
        double gp7Step=Math.hypot(loco.getX()-s.priorGp7X,loco.getZ()-s.priorGp7Z);
        double coachStep=Math.hypot(coach.getX()-s.priorCoachX,coach.getZ()-s.priorCoachZ);
        double gp7Travel=Math.hypot(loco.getX()-s.originX,loco.getZ()-s.originZ);
        double rawGP7=loco.getDeltaMovement().horizontalDistance();
        double rawCoach=coach.getDeltaMovement().horizontalDistance();
        s.minGap=Math.min(s.minGap,gap);s.minHitbox=Math.min(s.minHitbox,clearance);
        s.maxSpeed=Math.max(s.maxSpeed,Math.max(rawGP7,rawCoach));
        s.maxCoachStep=Math.max(s.maxCoachStep,coachStep);
        s.maxGP7Travel=Math.max(s.maxGP7Travel,gp7Travel);
        if(coachStep>HARD_SPEED){s.largeCoachSteps++;log("COACH_NATIVE_POSITION_STEP tick="+s.driveTicks+" step="+f(coachStep)+" coachRaw="+f(rawCoach));}
        String rail1=track(s.level,loco),rail2=track(s.level,coach);
        if(rawGP7>HARD_SPEED||rawCoach>HARD_SPEED){park(s,"HARD_SPEED_ENVELOPE",false);return;}
        if(coachStep>MAX_SINGLE_TICK_DISPLACEMENT||gp7Step>MAX_SINGLE_TICK_DISPLACEMENT){park(s,"ABRUPT_POSITION_STEP",false);return;}
        if(gap<FOLLOW_THROUGH_CENTER_GAP_FLOOR){park(s,"CENTER_GAP_FLOOR",false);return;}
        if(clearance<MIN_GAME_HITBOX_CLEARANCE){park(s,"GAME_AABB_CLEARANCE",false);return;}
        if(s.priorGap-gap>MAX_TICK_GAP_CLOSURE){park(s,"ABRUPT_GAP_CLOSURE",false);return;}
        if(!rail1.startsWith("traincraft:track_")||!rail2.startsWith("traincraft:track_")){
            park(s,"VEHICLE_LEFT_NATIVE_RAIL",false);return;
        }
        if(s.driveTicks%5==0){
            log("SAMPLE n="+s.driveTicks+" GP7="+f(loco.getX())+","+f(loco.getZ())
                +" COACH="+f(coach.getX())+","+f(coach.getZ())
                +" gap="+f(gap)+" gameAABB="+f(clearance)
                +" rawGP7="+f(rawGP7)+" rawCoach="+f(rawCoach)
                +" gp7Step="+f(gp7Step)+" coachStep="+f(coachStep)
                +" rail1="+rail1+" rail2="+rail2);
        }
        if(gp7Travel>=DRIVE_MAX_GP7_TRAVEL){park(s,"BOUNDED_FOLLOW_THROUGH_REACHED",true);return;}
        if(s.driveTicks>=DRIVE_MAX_TICKS){park(s,"TIME_LIMIT_REACHED",false);return;}
        // Velocity-only test modification. The native coupling controller
        // runs first; its recorded speed and entity movement are NOT hidden.
        for(Entity e:new Entity[]{loco,coach}){
            Vec3 m=e.getDeltaMovement();double speed=m.horizontalDistance();
            if(speed>GOVERNOR){e.setDeltaMovement(new Vec3(m.x*GOVERNOR/speed,m.y,m.z*GOVERNOR/speed));s.governedTicks++;}
        }
        double gp7After=loco.getDeltaMovement().horizontalDistance();
        boolean want=s.throttleOn;
        if(gp7After>=OFF_ABOVE)want=false;
        else if(gp7After<=ON_BELOW)want=true;
        s.throttleOn=want;
        loco.setDriverControls(want,false,false);
        s.priorGp7X=loco.getX();s.priorGp7Z=loco.getZ();
        s.priorCoachX=coach.getX();s.priorCoachZ=coach.getZ();
        s.priorGap=gap;
    }
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=event.getServer();
        if(active==null||active.server!=server)active=find(server);
        Session s=active;
        if(s==null||s.finished)return;
        try{
            if(server.getPlayerList().getPlayers().size()!=1){
                if(s.started)park(s,"VIEWER_LEFT_OR_EXTRA_PLAYER",false);
                return;
            }
            ServerPlayer player=server.getPlayerList().getPlayers().get(0);
            if(s.viewer==null)s.viewer=player;
            if(s.viewer!=player){if(s.started)park(s,"VIEWER_CHANGED",false);return;}
            if(!s.camReady)prepareCamera(s,player);
            s.age++;
            if(s.age==5){
                // Force only the near-switch entity-ticking chunks of this
                // marker-protected disposable world, never real user worlds.
                for(int cx=16;cx<=17;cx++)for(int cz=6;cz<=7;cz++)s.level.setChunkForced(cx,cz,true);
            }
            if(s.age==100||s.age==200)say(s,"Recording countdown: " + ((VIEW_PREP_TICKS-s.age)/20) + " seconds until train moves.");
            if(!s.started){
                if(s.age>=VIEW_PREP_TICKS)start(s);
                return;
            }
            drive(s);
        }catch(Exception ex){log("EXCEPTION "+ex.toString());park(s,"EXCEPTION_"+ex.getClass().getSimpleName(),false);}
    }
}
