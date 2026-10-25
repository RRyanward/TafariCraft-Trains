package traincraft.debug;

/* DEV50d3O -- one-time, marker-locked, disposable-world-only end swap.
 * Static preview ONLY. Never runs in dedicated, recovery, headless, or DEV50d3N worlds.
 * Never modifies coupling links, train yaw, native physics, blocks, renderer or track path.
 * Repositions only two exact saved train entities in a disposable cloned world.
 * A successfully swapped yaw-axis side is NOT proof of visual front or drivable safety.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;

// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class Dev50D3OVisibleEndSetup {
    private Dev50D3OVisibleEndSetup() { }
    private static final String PREFIX="New World - Railway DEV50D3O STATIC ";
    private static final String MARKER="DEV50D3O_STATIC_COPY.marker";
    private static final String PENDING="DEV50D3O_STATIC_DISPOSABLE_PENDING_v1";
    private static final String IN_PROGRESS="DEV50D3O_STATIC_DISPOSABLE_IN_PROGRESS_v1";
    private static final String APPLIED="DEV50D3O_STATIC_DISPOSABLE_APPLIED_v1";
    private static final UUID GP_ID=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH_ID=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final double X_GP=265.539D, Z_GP=106.039D, X_COACH=263.391D, Z_COACH=104.923D;
    private static final int MAX_WAIT=1200, OBSERVE=120;
    private static Session session;
    private static final class Session {
        final MinecraftServer server;final ServerLevel world;final Path marker;final String mode;
        int wait,ticks;boolean started,finished,camera;
        double firstSide=Double.NaN,minGap=Double.POSITIVE_INFINITY,maxSpeed,maxDrift;
        double x1,z1,x2,z2;
        Session(MinecraftServer s,ServerLevel w,Path p,String m){server=s;world=w;marker=p;mode=m;}
    }
    private static String f(double v){return String.format(Locale.ROOT,"%.5f",v);}
    private static void log(String s){Traincraft.LOGGER.info("[TC-DEV50D3O] "+s);}
    private static void tell(ServerPlayer p,String s){
        log(s);
        if(p!=null && !p.isRemoved())p.sendSystemMessage(Component.literal("[DEV50d3O] "+s));
    }
    private static Session find(MinecraftServer server){
        if(server.isDedicatedServer())return null;
        ServerLevel w=server.getLevel(Level.OVERWORLD);if(w==null)return null;
        try{
            Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path parent=root.getParent();
            if(parent==null || parent.getFileName()==null || !parent.getFileName().toString().equalsIgnoreCase("saves"))return null;
            String name=root.getFileName().toString();
            if(!name.startsWith(PREFIX) || !name.substring(PREFIX.length()).matches("[0-9]{8}-[0-9]{6}-[0-9]{4}"))return null;
            if(Files.isSymbolicLink(root)||Files.isSymbolicLink(parent))return null;
            Path marker=root.resolve(MARKER);
            if(!Files.isRegularFile(marker)||Files.isSymbolicLink(marker))return null;
            String content=Files.readString(marker,StandardCharsets.US_ASCII).trim();
            if(!content.equals(PENDING)&&!content.equals(APPLIED)){
                log("REFUSED_WORLD bad_marker_state="+content);return null;
            }
            log("DISPOSABLE_WORLD_VERIFIED name="+name+" state="+content+" TRAIN_REORDER_STATIC_ONLY=TRUE");
            return new Session(server,w,marker,content);
        }catch(Exception e){log("REFUSED_WORLD "+e);return null;}
    }
    private static double side(LegacyPoweredLocomotive gp,SteamTender coach){
        double x=coach.getX()-gp.getX(),z=coach.getZ()-gp.getZ();
        double gap=Math.hypot(x,z);if(gap<0.01D)return Double.NaN;
        double rad=Math.toRadians(gp.getRenderTrainFacingYaw(1F));
        return (Math.sin(rad)*x-Math.cos(rad)*z)/gap;
    }
    private static void writeMarker(Session s,String state) throws Exception{
        // This file lives inside the disposable world only; never the source world.
        Files.writeString(s.marker,state+System.lineSeparator(),StandardCharsets.US_ASCII,
                StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        if(!state.equals(Files.readString(s.marker,StandardCharsets.US_ASCII).trim()))
            throw new IllegalStateException("MARKER_WRITE_VERIFY_FAILED");
    }
    private static String guard(Session s,Entity a,Entity b){
        if(!(a instanceof LegacyPoweredLocomotive gp)||!(b instanceof SteamTender coach))return "EXACT_ENTITY_MISSING";
        if(!"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(gp.getType()).toString())
            ||!"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(coach.getType()).toString()))return "TYPE_MISMATCH";
        if(!CouplingManager.areMutuallyLinked(gp,gp,coach,coach))return "LINK_NOT_MUTUAL";
        if(gp.isRemoved()||coach.isRemoved()||!gp.getPassengers().isEmpty()||!coach.getPassengers().isEmpty())return "OCCUPIED_OR_REMOVED";
        if(gp.getDeltaMovement().horizontalDistance()>0.010D||coach.getDeltaMovement().horizontalDistance()>0.010D)return "MOVING_BEFORE_TEST";
        double gap=Math.hypot(gp.getX()-coach.getX(),gp.getZ()-coach.getZ());
        if(gap<2.35D||gap>3.20D)return "GAP_OUTSIDE_SNAPSHOT";
        if(!gp.isHandBrakeApplied())return "GP7_HANDBRAKE_NOT_SET";
        return null;
    }
    private static void stop(Session s,ServerPlayer viewer,String reason){
        s.finished=true;
        tell(viewer,"RESULT=STATIC_PREVIEW_STOP_OR_UNVERIFIED reason="+reason+
                " originalWorldsUntouched=true NO_AUTOMATIC_DRIVING=true");
    }
    @SubscribeEvent public static void onTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=e.getServer();
        if(session==null||session.server!=server)session=find(server);
        Session s=session;if(s==null||s.finished)return;
        ServerPlayer viewer=null;
        try{
            if(server.getPlayerList().getPlayers().size()!=1)return;
            viewer=server.getPlayerList().getPlayers().get(0);
            if(viewer.level()!=s.world)return;
            if(!s.camera){
                viewer.setGameMode(GameType.SPECTATOR);
                viewer.teleportTo(s.world,263.4D,-56.8D,109.8D,180F,16F);
                s.camera=true;
            }
            Entity a=s.world.getEntity(GP_ID),b=s.world.getEntity(COACH_ID);
            if(a==null||b==null){
                if(++s.wait>=MAX_WAIT)stop(s,viewer,"EXACT_ENTITIES_NOT_LOADED");
                return;
            }
            String err=guard(s,a,b);
            if(err!=null && !s.started){stop(s,viewer,"PREFLIGHT_"+err);return;}
            LegacyPoweredLocomotive gp=(LegacyPoweredLocomotive)a;
            SteamTender coach=(SteamTender)b;
            if(!s.started){
                double before=side(gp,coach);
                tell(viewer,"START originalSideDot="+f(before)+" gp7Yaw="+f(gp.getRenderTrainFacingYaw(1F))+
                        " coachSide=PREVIOUS_TEST_GEOMETRY");
                if(s.mode.equals(PENDING)){
                    if(Math.abs(gp.getX()-X_GP)>0.32D || Math.abs(gp.getZ()-Z_GP)>0.32D
                        ||Math.abs(coach.getX()-X_COACH)>0.32D||Math.abs(coach.getZ()-Z_COACH)>0.32D){
                        stop(s,viewer,"SAVED_POSITIONS_DIFFER");return;
                    }
                    if(!Double.isFinite(before)||before>-.90D){stop(s,viewer,"EXPECTED_OLD_SIDE_NOT_FOUND");return;}
                    // One-way marker state prevents accidental second swap after a crash.
                    writeMarker(s,IN_PROGRESS);
                    Vec3 gpAt=gp.position(),coachAt=coach.position();
                    gp.setDeltaMovement(Vec3.ZERO);coach.setDeltaMovement(Vec3.ZERO);
                    gp.setPos(coachAt.x,coachAt.y,coachAt.z);
                    coach.setPos(gpAt.x,gpAt.y,gpAt.z);
                    writeMarker(s,APPLIED);
                    tell(viewer,"ONE_TIME_STATIC_SWAP_APPLIED; no yaw, link, renderer, or motion changes. DO NOT DRIVE.");
                }else{
                    tell(viewer,"Previously applied test world reopened; never swap twice.");
                }
                s.x1=gp.getX();s.z1=gp.getZ();s.x2=coach.getX();s.z2=coach.getZ();
                s.firstSide=side(gp,coach);
                s.started=true;
            }
            double gap=Math.hypot(gp.getX()-coach.getX(),gp.getZ()-coach.getZ());
            double drift=Math.max(Math.hypot(gp.getX()-s.x1,gp.getZ()-s.z1),
                                  Math.hypot(coach.getX()-s.x2,coach.getZ()-s.z2));
            double speed=Math.max(gp.getDeltaMovement().horizontalDistance(),coach.getDeltaMovement().horizontalDistance());
            s.maxSpeed=Math.max(speed,s.maxSpeed);s.maxDrift=Math.max(drift,s.maxDrift);s.minGap=Math.min(gap,s.minGap);
            double dot=side(gp,coach);
            if(s.ticks%10==0)log("SAMPLE n="+s.ticks+" sideDot="+f(dot)+" gap="+f(gap)+
                    " speed="+f(speed)+" drift="+f(drift)+" gp7X="+f(gp.getX())+
                    " coachX="+f(coach.getX())+" marker=APPLIED");
            if(!CouplingManager.areMutuallyLinked(gp,gp,coach,coach)){
                stop(s,viewer,"LINK_LOST_AFTER_SWAP");return;
            }
            if(!Double.isFinite(dot)||dot<.80D){stop(s,viewer,"COACH_NOT_ON_OPPOSITE_YAW_SIDE");return;}
            if(gap<2.20D||gap>3.35D){stop(s,viewer,"GAP_OUT_OF_STATIC_ENVELOPE");return;}
            if(speed>0.120D||drift>0.35D){stop(s,viewer,"UNSTABLE_STATIC_PLACEMENT");return;}
            if(++s.ticks>=OBSERVE){
                s.finished=true;
                tell(viewer,"RESULT=STATIC_OPPOSITE_END_CANDIDATE_ONLY ticks="+s.ticks+
                        " coachDotYaw="+f(dot)+" minGap="+f(s.minGap)+
                        " maxDrift="+f(s.maxDrift)+" maxSpeed="+f(s.maxSpeed)+
                        " VISIBLE_NOSE=REQUIRES_SCREENSHOT MOVING_TEST=NOT_PERFORMED");
                tell(viewer,"Take F2 screenshot of locomotive cab end and coach. Save & Quit. DO NOT DRIVE.");
            }
        }catch(Exception ex){stop(s,viewer,"EXCEPTION_"+ex.getClass().getSimpleName()+"_"+ex.getMessage());}
    }
}
