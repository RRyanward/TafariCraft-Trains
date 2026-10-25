package traincraft.debug;

/* DEV50d3M - passive post-park recovery sequencing DIAGNOSTIC.
 * No native train code edits, no position/motion/coupling writes.
 * Runs only within a specially marked disposable integrated world cloned
 * from the saved DEV50d3L parked visual-test copy.
 * This can prove whether a position set by recouple recovery is subsequently
 * reverted, and narrow the stage. It cannot name a setter not itself logged.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class Dev50D3MParkedRecoveryDiagnostic {
    private Dev50D3MParkedRecoveryDiagnostic() { }
    private static final String PREFIX="New World - Railway DEV50D3M PARKED ";
    private static final String MARKER="DEV50D3M_PARKED_COPY.marker";
    private static final String MARKER_TEXT="DEV50D3M_PARKED_DISPOSABLE_COPY_ONLY_v1";
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final int MAX_TICKS=260;
    private static final String APPENDER_NAME="TC-DEV50D3M-RECOVERY-PROBE";
    private static Session active;
    private static final class Session {
        final MinecraftServer server;
        final ServerLevel level;
        final String copyName;
        boolean done,listenerInstalled,ready,inObservedTick;
        int waitPolls;
        int ticks,validSamples,missingSamples,recoveryEvents,returnToOld,largeSteps;
        double largestSampleStep, largestRecoveryToEndStep;
        Vec3 priorSample, lastRecoveryPos;
        int lastRecoveryTick=-10000;
        String lastRecoveryMessage="NONE";
        Logger logger;
        RecoveryProbe probe;
        Session(MinecraftServer server,ServerLevel level,String copyName){this.server=server;this.level=level;this.copyName=copyName;}
    }
    private static final class RecoveryProbe extends AbstractAppender {
        final Session s;
        RecoveryProbe(Session s){super(APPENDER_NAME,null,null,true,Property.EMPTY_ARRAY);this.s=s;}
        @Override public void append(LogEvent event){
            if(s.done || event==null || event.getMessage()==null)return;
            String message=event.getMessage().getFormattedMessage();
            if(!message.startsWith("[TC-RECOUPLE-RECOVERY]"))return;
            Entity coach=s.level.getEntity(COACH);
            if(coach==null||coach.isRemoved())return;
            Vec3 pos=coach.position();
            s.recoveryEvents++;
            s.lastRecoveryPos=pos;
            s.lastRecoveryTick=s.ticks;
            s.lastRecoveryMessage=message;
            // Registered only for TraincraftCouplerDebug. This samples the coach
            // INSIDE the native recovery log call, AFTER recovery setPos() ran.
            note("RECOVERY_AT_LOG tick="+s.ticks+" id="+coach.getId()+" pos="+xyz(pos)
                +" speed="+num(coach.getDeltaMovement().horizontalDistance())
                +" raw="+message);
        }
    }
    private static String num(double x){return String.format(Locale.ROOT,"%.5f",x);}
    private static String xyz(Vec3 p){return "("+num(p.x)+","+num(p.y)+","+num(p.z)+")";}
    private static void note(String s){Traincraft.LOGGER.info("[TC-DEV50D3M] "+s);}
    private static Session find(MinecraftServer server){
        if(server.isDedicatedServer())return null;
        ServerLevel level=server.getLevel(Level.OVERWORLD);
        if(level==null)return null;
        try{
            Path root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path parent=root.getParent();
            if(parent==null||parent.getFileName()==null||!parent.getFileName().toString().equalsIgnoreCase("saves"))return null;
            String name=root.getFileName().toString();
            if(!name.startsWith(PREFIX)||!name.substring(PREFIX.length()).matches("[0-9]{8}-[0-9]{6}-[0-9]{4}"))return null;
            if(Files.isSymbolicLink(root)||Files.isSymbolicLink(parent))return null;
            Path marker=root.resolve(MARKER);
            if(!Files.isRegularFile(marker)||Files.isSymbolicLink(marker)
                ||!MARKER_TEXT.equals(Files.readString(marker,StandardCharsets.US_ASCII).trim()))return null;
            note("DISPOSABLE_POST_PARK_WORLD_VERIFIED "+name+" DIAGNOSTIC_READ_ONLY=YES");
            return new Session(server,level,name);
        }catch(Exception ex){note("REFUSED_WORLD "+ex.toString());return null;}
    }
    private static void addProbe(Session s){
        try{
            LoggerContext context=(LoggerContext)LogManager.getContext(false);
            Logger logger=context.getLogger("TraincraftCouplerDebug");
            if(logger.getAppenders().containsKey(APPENDER_NAME)){
                note("PROBE_REFUSED_DUPLICATE_NAME");return;
            }
            RecoveryProbe p=new RecoveryProbe(s);p.start();logger.addAppender(p);
            s.logger=logger;s.probe=p;s.listenerInstalled=true;
            note("NATIVE_RECOVERY_LOG_TAP=ATTACHED logger=TraincraftCouplerDebug");
        }catch(RuntimeException ex){note("NATIVE_RECOVERY_LOG_TAP=UNAVAILABLE "+ex.getClass().getSimpleName());}
    }
    private static void removeProbe(Session s){
        if(s.logger!=null&&s.probe!=null){
            try{s.logger.removeAppender(s.probe);s.probe.stop();}
            catch(RuntimeException ex){note("PROBE_DETACH_EXCEPTION "+ex.getClass().getSimpleName());}
        }
    }
    private static void sample(Session s,String phase){
        if(s.done)return;
        Entity a=s.level.getEntity(GP7),b=s.level.getEntity(COACH);
        if(a==null||b==null||a.isRemoved()||b.isRemoved()){
            s.missingSamples++;
            if(s.missingSamples==1||s.missingSamples%40==0)note("ENTITY_WAIT tick="+s.ticks+" phase="+phase);
            return;
        }
        Vec3 cp=b.position(), gp=a.position();
        double step=s.priorSample==null?0:cp.distanceTo(s.priorSample);
        s.largestSampleStep=Math.max(s.largestSampleStep,step);
        if(step>0.12)s.largeSteps++;
        if(s.lastRecoveryTick==s.ticks && phase.equals("LEVEL_END") && s.lastRecoveryPos!=null){
            double back=cp.distanceTo(s.lastRecoveryPos);
            s.largestRecoveryToEndStep=Math.max(back,s.largestRecoveryToEndStep);
            if(back>=0.10){
                s.returnToOld++;
                note("POST_RECOVERY_POSITION_CHANGE tick="+s.ticks+" recoveryPos="+xyz(s.lastRecoveryPos)
                     +" levelEndPos="+xyz(cp)+" distance="+num(back)
                     +" NATIVE_SETTER_NOT_IDENTIFIED");
            }
        }
        s.validSamples++;
        // Every stage is recorded in diagnostic world; no gameplay writes.
        note("PHASE tick="+s.ticks+" stage="+phase+" coach="+xyz(cp)+" gp7="+xyz(gp)
             +" gap="+num(cp.distanceTo(gp))+" coachSpeed="+num(b.getDeltaMovement().horizontalDistance())
             +" gp7Speed="+num(a.getDeltaMovement().horizontalDistance())
             +" sincePriorStage="+num(step)+" recoverySeenThisTick="+(s.lastRecoveryTick==s.ticks));
        s.priorSample=cp;
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){
        MinecraftServer server=e.getServer();
        if(e.phase==TickEvent.Phase.START){
            if(active==null||active.server!=server)active=find(server);
            Session s=active;
            if(s==null||s.done)return;
            if(!s.ready){
                // Do not burn the observation window during integrated-world
                // loading or before the user enters this disposable copy.
                if(server.getPlayerList().getPlayers().size()!=1
                     ||s.level.getEntity(GP7)==null||s.level.getEntity(COACH)==null){
                    s.waitPolls++;
                    if(s.waitPolls%100==0)note("WAIT_FOR_ONE_VIEWER_AND_EXACT_TRAINS polls="+s.waitPolls);
                    return;
                }
                s.ready=true;
                addProbe(s);
                note("OBSERVATION_START exactTrainUUIDs=YES playerCount=1 windowTicks="+MAX_TICKS);
            }
            s.inObservedTick=true;
            sample(s,"SERVER_START");
        }else{
            Session s=active;
            if(s==null||s.server!=server||s.done||!s.inObservedTick)return;
            sample(s,"SERVER_END");
            s.inObservedTick=false;
            s.ticks++;
            if(s.ticks>=MAX_TICKS){
                s.done=true;
                removeProbe(s);
                note("RESULT=OBSERVATION_COMPLETE ticks="+s.ticks
                   +" validStageSamples="+s.validSamples
                   +" missingStageSamples="+s.missingSamples
                   +" recoveryEvents="+s.recoveryEvents
                   +" recoveryToLevelEndReturns="+s.returnToOld
                   +" largeSampleSteps="+s.largeSteps
                   +" largestStageStep="+num(s.largestSampleStep)
                   +" largestRecoveryToLevelEnd="+num(s.largestRecoveryToEndStep)
                   +" nativeCouplingUnchanged=true"
                   +" SOURCE_OF_REVERSION=UNVERIFIED_UNLESS_PHASE_LOGS_IDENTIFY_WINDOW");
                note("Diagnostics complete; Save & Quit this disposable world. No auto-driver or position writes were made.");
            }
        }
    }
    @SubscribeEvent public static void levelTick(TickEvent.LevelTickEvent e){
        Session s=active;
        if(s==null||s.done||!s.inObservedTick||e.level!=s.level)return;
        sample(s,e.phase==TickEvent.Phase.START?"LEVEL_START":"LEVEL_END");
    }
}
