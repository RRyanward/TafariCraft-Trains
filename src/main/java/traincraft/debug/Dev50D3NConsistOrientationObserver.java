package traincraft.debug;

/* DEV50d3N - diagnostic-only forward-end / consist ordering snapshot.
 * Marker-locked to two disposable integrated-world snapshots:
 * BEFORE = accepted DEV50d3i headless end-state (before DEV50d3L followthrough)
 * AFTER = saved DEV50d3L auto-drive end-state.
 * Does not mount a driver, write to train entities, or change coupling physics.
 * The GP7 model's *visible* nose is NOT assumed to equal its yaw axis.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
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

@Mod.EventBusSubscriber(modid=Traincraft.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev50D3NConsistOrientationObserver {
    private Dev50D3NConsistOrientationObserver() {}
    private static final String PREFIX="New World - Railway DEV50D3N ";
    private static final String MARKER="DEV50D3N_ORIENTATION_COPY.marker";
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final int SAMPLE_TICKS=90, MAX_WAIT=1200;
    private static Session active;
    private static final class Session {
        final MinecraftServer server;final ServerLevel level;final String role,copyName;
        int polls,ticks;boolean ready,done,viewerPlaced;
        double firstProjection,firstGap,lastProjection,lastGap,maxYawChange,maxSpeed;
        float firstYaw,firstCoachYaw;
        Session(MinecraftServer server,ServerLevel level,String role,String name){
            this.server=server;this.level=level;this.role=role;this.copyName=name;
        }
    }
    private static String f(double x){return String.format(Locale.ROOT,"%.5f",x);}
    private static void note(String text){Traincraft.LOGGER.info("[TC-DEV50D3N] "+text);}
    private static Session find(MinecraftServer server){
        if(server.isDedicatedServer())return null;
        ServerLevel level=server.getLevel(Level.OVERWORLD);if(level==null)return null;
        try{
            Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path parent=root.getParent();
            if(parent==null||parent.getFileName()==null||!parent.getFileName().toString().equalsIgnoreCase("saves"))return null;
            String name=root.getFileName().toString();
            if(!name.startsWith(PREFIX))return null;
            String tag=name.substring(PREFIX.length());
            String role=tag.startsWith("BEFORE ")?"BEFORE":tag.startsWith("AFTER ")?"AFTER":null;
            if(role==null || !tag.equals(role+" "+tag.substring(role.length()+1))
                ||!tag.substring(role.length()+1).matches("[0-9]{8}-[0-9]{6}-[0-9]{4}"))return null;
            if(Files.isSymbolicLink(root)||Files.isSymbolicLink(parent))return null;
            Path marker=root.resolve(MARKER);
            String expected="DEV50D3N_"+role+"_DISPOSABLE_ORIENTATION_COPY_v1";
            if(!Files.isRegularFile(marker)||Files.isSymbolicLink(marker)
                ||!expected.equals(Files.readString(marker,StandardCharsets.US_ASCII).trim()))return null;
            note("DISPOSABLE_WORLD_VERIFIED role="+role+" name="+name+" TRAIN_ENTITY_MUTATIONS=NONE");
            return new Session(server,level,role,name);
        }catch(Exception ex){note("REFUSED_WORLD "+ex);return null;}
    }
    private static double projection(Entity gp,Entity coach,double degrees){
        double x=coach.getX()-gp.getX(),z=coach.getZ()-gp.getZ();
        double gap=Math.hypot(x,z);
        if(gap<0.01)return Double.NaN;
        double rad=Math.toRadians(degrees);
        return (Math.sin(rad)*x-Math.cos(rad)*z)/gap;
    }
    private static String side(double dot){
        if(!Double.isFinite(dot))return "UNRESOLVED";
        if(dot>0.30)return "IN_FRONT_OF_GP7_YAW_AXIS";
        if(dot<-.30)return "BEHIND_GP7_YAW_AXIS";
        return "SIDE_ON_CURVE_AXIS_UNRESOLVED";
    }
    private static String validate(Session s,Entity a,Entity b){
        if(!(a instanceof LegacyPoweredLocomotive)||!(b instanceof SteamTender))return "MISSING_TRAIN_ENTITIES";
        if(!"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString()))return "GP7_TYPE_MISMATCH";
        if(!"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).toString()))return "COACH_TYPE_MISMATCH";
        if(!CouplingManager.areMutuallyLinked(a,(LegacyPoweredLocomotive)a,b,(SteamTender)b))return "COUPLING_NOT_MUTUAL";
        return null;
    }
    private static void sample(Session s,LegacyPoweredLocomotive gp,SteamTender coach,boolean first){
        // Raw entity yaw, rail-owned yaw and smoothed render yaw are distinct.
        float gpYaw=gp.getTrainFacingYaw(), coachYaw=coach.getTrainFacingYaw();
        float gpRenderYaw=gp.getRenderTrainFacingYaw(1F),coachRenderYaw=coach.getRenderTrainFacingYaw(1F);
        double gap=Math.hypot(gp.getX()-coach.getX(),gp.getZ()-coach.getZ());
        double gpDot=projection(gp,coach,gpRenderYaw);
        double cpDot=projection(coach,gp,coachRenderYaw);
        double speed=Math.max(gp.getDeltaMovement().horizontalDistance(),coach.getDeltaMovement().horizontalDistance());
        if(first){s.firstProjection=gpDot;s.firstGap=gap;s.firstYaw=gpRenderYaw;s.firstCoachYaw=coachRenderYaw;}
        s.lastProjection=gpDot;s.lastGap=gap;s.maxSpeed=Math.max(s.maxSpeed,speed);
        s.maxYawChange=Math.max(s.maxYawChange,Math.abs(Math.IEEEremainder(gpRenderYaw-s.firstYaw,360)));
        note("ORIENTATION role="+s.role+" tick="+s.ticks
            +" gp7Pos="+f(gp.getX())+","+f(gp.getZ())
            +" coachPos="+f(coach.getX())+","+f(coach.getZ())
            +" gap="+f(gap)
            +" gp7RawYaw="+f(gp.getYRot())+" gp7RailYaw="+f(gpYaw)+" gp7RenderYaw="+f(gpRenderYaw)
            +" coachRawYaw="+f(coach.getYRot())+" coachRailYaw="+f(coachYaw)+" coachRenderYaw="+f(coachRenderYaw)
            +" coachDotGP7RenderAxis="+f(gpDot)+" gp7DotCoachRenderAxis="+f(cpDot)
            +" yawAxisSide="+side(gpDot)+" speed="+f(speed));
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=e.getServer();
        if(active==null||active.server!=server)active=find(server);
        Session s=active;if(s==null||s.done)return;
        try{
            if(server.getPlayerList().getPlayers().size()!=1)return;
            ServerPlayer viewer=server.getPlayerList().getPlayers().get(0);
            if(!s.viewerPlaced && viewer.level()==s.level){
                viewer.setGameMode(GameType.SPECTATOR);
                viewer.teleportTo(s.level,263.4D,-56.8D,109.8D,180F,16F);
                s.viewerPlaced=true;
                note("VIEWER_CAMERA_ONLY role="+s.role+" noTrainEntityMutation=true");
            }
            Entity a=s.level.getEntity(GP7),b=s.level.getEntity(COACH);
            if(a==null||b==null){
                s.polls++;
                if(s.polls%100==0)note("WAIT_FOR_EXACT_ENTITY_UUIDS role="+s.role+" polls="+s.polls);
                if(s.polls>=MAX_WAIT){s.done=true;note("RESULT=SNAPSHOT_UNVERIFIED role="+s.role+" reason=ENTITIES_NOT_LOADED");}
                return;
            }
            String err=validate(s,a,b);
            if(err!=null){s.done=true;note("RESULT=SNAPSHOT_UNVERIFIED role="+s.role+" reason="+err);return;}
            LegacyPoweredLocomotive gp=(LegacyPoweredLocomotive)a;SteamTender coach=(SteamTender)b;
            if(!s.ready){s.ready=true;note("SNAPSHOT_START role="+s.role+" exactEntities=YES coupled=YES SPECTATOR_ONLY=YES");}
            if(s.ticks==0 || s.ticks%10==0 || s.ticks==SAMPLE_TICKS-1)sample(s,gp,coach,s.ticks==0);
            s.ticks++;
            if(s.ticks>=SAMPLE_TICKS){
                s.done=true;
                note("RESULT=ORIENTATION_SNAPSHOT_COMPLETE role="+s.role+" ticks="+s.ticks
                    +" startDot="+f(s.firstProjection)+" endDot="+f(s.lastProjection)
                    +" startYawAxisSide="+side(s.firstProjection)
                    +" endYawAxisSide="+side(s.lastProjection)
                    +" gapStart="+f(s.firstGap)+" gapEnd="+f(s.lastGap)
                    +" maxSpeed="+f(s.maxSpeed)+" maxGP7RenderYawChange="+f(s.maxYawChange)
                    +" VISIBLE_NOSE_POLARITY=UNVERIFIED_BY_SERVER_YAW_ALONE"
                    +" PHYSICS_AND_COUPLING_UNCHANGED=TRUE");
                note("Save & Quit and inspect other DEV50D3N snapshot (BEFORE / AFTER). No driving.");
            }
        }catch(Exception ex){s.done=true;note("RESULT=SNAPSHOT_UNVERIFIED role="+s.role+" reason=EXCEPTION_"+ex.getClass().getSimpleName()+" "+ex);}
    }
}