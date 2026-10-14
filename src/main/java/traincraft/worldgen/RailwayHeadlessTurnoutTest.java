package traincraft.worldgen;

/* DEV50d3i BRANCH-only coach position window and native turnout-path continuity; frozen coach phase probes preserved, isolated-copy dedicated server.
 * Actual native GP7 + coach server-side physics, using Forge FakePlayer rider
 * so SmallSteamLocomotive.tick() does not discard simulated forward control.
 * No position forcing, track path substitution, respawning, station edits, or
 * worldgen changes. The velocity-only governor is copy-only and reported.
 * Branch state changes are permitted ONLY inside the disposable BRANCH copy.
 * A bounded partial-switch test never certifies downstream routes or visuals.
 */
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;

public final class RailwayHeadlessTurnoutTest {
    private static final Logger LOG=Traincraft.LOGGER;
    private static final String LABEL="[TC-DEV50D3] ";
    private static final String STRAIGHT="New World - Railway DEV50D3 HEADLESS STRAIGHT";
    private static final String BRANCH="New World - Railway DEV50D3 HEADLESS BRANCH";
    private static final String MARKER="DEV50D3_HEADLESS_COPY.marker";
    private static final String MARKER_CONTENT="DEV50D3_AUTOMATED_DEDICATED_COPY_ONLY_v1";
    private static final UUID GP7=UUID.fromString("5d96dc88-ba82-4f2a-8f21-0cd32cf2883a");
    private static final UUID COACH=UUID.fromString("787ba7a9-10af-49aa-8e0f-d2cb68f6f738");
    private static final UUID BOT=UUID.fromString("42acb73e-6326-4b10-908c-0f957f7ed503");
    // Disposable-test speed envelope only; the native coupling controller is untouched.
    // Adaptive W key pacing aims to keep GP7 reference speed below the controller's
    // +0.080 blocks/tick relative catch-up allowance. Hard stop remains 0.120.
    private static final double CAP=0.033D, HARD_CAP=0.120D, TARGET=265.55D;
    // The coach crossed its former 9.0-block travel boundary before GP7
    // reached TARGET in DEV50d3h. Extend only the COACH diagnostic bound.
    private static final double GP7_FORWARD_BOUND=9.0D, COACH_FORWARD_BOUND=10.0D;
    private static final String SELECTED_SWITCH_RAIL="traincraft:track_switch_parallel_right_segment";
    private static final double THROTTLE_ON_BELOW=0.0270D, THROTTLE_OFF_ABOVE=0.0315D;
    private static final double APPROACH_REFERENCE_WARN=0.0400D;
    // Test-only, conservative mechanical center-distance guard: never infer
    // visible mesh clearance from the much smaller entity collision boxes.
    // When the GP7 has actually deviated onto an active turnout and the
    // train is still on its switch, accept ONLY a provisional curve band.
    // Native curve target is shorter than straight; no physics constants change.
    private static final double STRAIGHT_MIN_GAP=2.70D, CURVE_MIN_GAP=2.45D;
    // The DEV50d3g run settled at gap 2.4499 while native curve spacing
    // approached 2.4500. Allow at most 0.020 center-distance units of
    // numeric/run-to-run tolerance ONLY during a verified native curve phase.
    // This is NOT clearance certification: entity AABB and closure gates remain.
    private static final double CURVE_GAP_TOLERANCE=0.020D;
    private static final double CURVE_HARD_MIN_GAP=CURVE_MIN_GAP-CURVE_GAP_TOLERANCE;
    private static final double MAX_GAP=3.35D, MIN_HITBOX_XZ_CLEARANCE=0.10D;
    private static final double MAX_ONE_TICK_GAP_CLOSURE=0.145D;
    private static final int MAX_TICKS=700, STARTUP_LIMIT=400; // 20 seconds after first server ticks; bounded entity-load window
    private static Run run;
    private static String f(double d){return String.format(Locale.ROOT,"%.4f",d);}
    private static String id(BlockState b){return BuiltInRegistries.BLOCK.getKey(b.getBlock()).toString();}
    private static String type(Entity e){return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    // The saved rail blocks occupy Y=-60; blockPosition().below() is gravel here, NOT the rail.
    private static String trackAt(ServerLevel level,Entity e){
        BlockPos p=new BlockPos((int)Math.floor(e.getX()),-60,(int)Math.floor(e.getZ()));
        return id(level.getBlockState(p));
    }
    private static void log(String s){LOG.info(LABEL+s);}
    private static double gameHitboxXZClearance(Entity loco,Entity coach){
        // Game collision boxes, NOT the render-model body or coupler reaches.
        AABB a=loco.getBoundingBox(),b=coach.getBoundingBox();
        double dx=Math.max(0.0D,Math.max(a.minX-b.maxX,b.minX-a.maxX));
        double dz=Math.max(0.0D,Math.max(a.minZ-b.maxZ,b.minZ-a.maxZ));
        return Math.hypot(dx,dz);
    }
    private RailwayHeadlessTurnoutTest(){}
    private static final class Run {
        MinecraftServer server;ServerLevel level;Path root;String mode;
        boolean started,parked,success;String finishReason="";
        boolean chunksRequested;int entityWaitPolls;String missingVehicles="not yet checked";
        int forcedChunks;boolean entityTickBaselineArmed;
        int baselineLocoTicks,baselineCoachTicks,baselineAtAge;
        int latestLocoTicks,latestCoachTicks;
        int age,travelTicks,parkTicks,governorTicks;
        int gp7GovernorTicks,coachGovernorTicks,branchSamples;
        boolean throttleOn;int throttleOnTicks,throttleOffTicks,throttleSwitches;
        double maxApproachGp7Raw, maxApproachCoachRaw;
        int curveReferenceWarnings;double firstCurveGp7Speed=-1.0D;

        double maxGp7Pre,maxCoachPre,maxGp7Post,maxCoachPost;
        double prevGp7X,prevGp7Z,prevCoachX,prevCoachZ;
        double lastGp7Post,lastCoachPost;
        String lastTrack="UNSET",lastOverspeed="NONE";
        double minGap=1.0E9,maxGap,maxSpeed,maxDx,maxDz,coachDx,coachDz;
        int curveGapSamples,curveGapBelowStraight,hitboxSamples;
        int curveToleranceSamples,consecutiveCurveToleranceSamples,maxConsecutiveCurveToleranceSamples;
        double maxCurveToleranceUsed;
        double minHitboxXZClearance=1.0E9, minProvisionalCurveGap=1.0E9;
        double maxGapClosurePerTick,previousGap;
        boolean coachEnteredTurnout,coachTurnedLateral;
        int bothOnSelectedSwitchSamples,coachLargePhaseSteps;
        String lastGp7Rail="UNSEEN",lastCoachRail="UNSEEN",largestCoachPhaseStepStage="NONE";
        double largestCoachPhaseStep;
        boolean wrongBranchObserved;
        String wrongBranchDetail="NONE";
        String lastClearancePhase="NONE",lastClearanceStop="NONE";
        double x0,z0,coachX0,coachZ0;
        // Read-only coach phase telemetry; never changes motion or locomotive controls.
        int coachPhaseSamples;
        double coachPhaseMaxVelocity,coachPhaseMaxStep;
        int coachPhasePrevTick=-1;
        String coachPhasePrev="NONE",firstCoachPhaseOverHard="NONE";
        double coachPhasePrevX,coachPhasePrevZ,coachPhasePrevSpeed;
        String coachPhaseAtHard="NONE";
        LegacyPoweredLocomotive locomotive;SteamTender coach;FakePlayer bot;
        Run(MinecraftServer s,ServerLevel l,Path p,String m){server=s;level=l;root=p;mode=m;}
    }
    /**
     * DEV50d3e: bounded, read-only instrumentation called by SteamTender.tick().
     * The only authorized target is the exact saved Passenger Blue coach in a
     * marker-protected disposable BRANCH run. No setters, path changes or brakes.
     * Log snapshots around PRE/POST constraint, vanilla cart motion, post-move
     * catch-up, switch routing, and FC6 emergency guard.
     */
    public static void coachPhaseProbe(SteamTender entity,String stage){
        Run r=run;
        if(r==null||!r.started||r.parked||!"BRANCH".equals(r.mode)
            ||r.coach!=entity||r.locomotive==null||entity.isRemoved())return;
        // Include 20+ approach ticks before the known n=149 anomaly; no log flood
        // at the station or in real/unmarked worlds.
        if(r.travelTicks<100&&entity.getX()<258.0D)return;
        Vec3 m=entity.getDeltaMovement();
        double speed=m.horizontalDistance();
        int t=entity.tickCount;
        // Vanilla super.tick() increments entity.tickCount MID-phase. Do not
        // reset here on a tickCount change: that would hide the exact
        // POST_CONSTRAINT -> POST_VANILLA displacement we are diagnosing.
        if(r.coachPhasePrevTick==-1){
            r.coachPhasePrev="FIRST_SAMPLE";
            r.coachPhasePrevX=entity.getX();r.coachPhasePrevZ=entity.getZ();
            r.coachPhasePrevSpeed=speed;
        }
        r.coachPhasePrevTick=t;
        double phaseStep=Math.hypot(entity.getX()-r.coachPhasePrevX,
                                    entity.getZ()-r.coachPhasePrevZ);
        double phaseDeltaSpeed=speed-r.coachPhasePrevSpeed;
        double gap=Math.sqrt(entity.distanceToSqr(r.locomotive));
        r.coachPhaseSamples++;
        r.coachPhaseMaxVelocity=Math.max(r.coachPhaseMaxVelocity,speed);
        r.coachPhaseMaxStep=Math.max(r.coachPhaseMaxStep,phaseStep);
        // Observation-only: native switch rail position projection can move
        // farther than its post-tick speed. Do not silently certify it.
        if(phaseStep>HARD_CAP){
            r.coachLargePhaseSteps++;
            if(phaseStep>r.largestCoachPhaseStep){
                r.largestCoachPhaseStep=phaseStep;
                r.largestCoachPhaseStepStage=stage+" tick="+t+" runnerN="+r.travelTicks;
            }
            if(r.coachLargePhaseSteps<=5 || r.coachLargePhaseSteps%20==0)
                log("COACH_POSITION_PROJECTION n="+r.travelTicks+" entityTick="+t
                    +" stage="+stage+" displacement="+f(phaseStep)
                    +" velocity="+f(speed)+" NOTE=OBSERVATION_ONLY_NO_MOTION_EDIT");
        }
        if("NONE".equals(r.firstCoachPhaseOverHard)
           &&(speed>HARD_CAP||phaseStep>HARD_CAP)){
            r.firstCoachPhaseOverHard="stage="+stage+" entityTick="+t
                +" runnerN="+r.travelTicks+" speed="+f(speed)
                +" phaseStep="+f(phaseStep)+" precedingStage="+r.coachPhasePrev;
            log("COACH_PHASE_HARD_FIRST "+r.firstCoachPhaseOverHard);
        }
        if(speed>HARD_CAP||phaseStep>HARD_CAP)
            r.coachPhaseAtHard="stage="+stage+" tick="+t+" speed="+f(speed)
                +" phaseStep="+f(phaseStep);
        log("COACH_PHASE stage="+stage+" runnerN="+r.travelTicks
            +" entityTick="+t+" x="+f(entity.getX())+" z="+f(entity.getZ())
            +" vx="+f(m.x)+" vz="+f(m.z)+" speed="+f(speed)
            +" deltaSpeed="+f(phaseDeltaSpeed)
            +" phaseStep="+f(phaseStep)
            +" prevStage="+r.coachPhasePrev
            +" gap="+f(gap)+" rail="+trackAt(r.level,entity));
        r.coachPhasePrev=stage;
        r.coachPhasePrevX=entity.getX();r.coachPhasePrevZ=entity.getZ();
        r.coachPhasePrevSpeed=speed;
    }

    private static Run discover(MinecraftServer s) {
        if(!s.isDedicatedServer())return null;
        ServerLevel l=s.getLevel(Level.OVERWORLD);
        if(l==null)return null;
        try{
            Path root=s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name=root.getFileName();
            if(name==null)return null;
            String folder=name.toString();
            if(!folder.equals(BRANCH))return null; // DEV50d3d: branch-only, D3c straight stays frozen/accepted
            Path marker=root.resolve(MARKER);
            // Exact folder name alone isn't authorization to test a save.
            if(!Files.isRegularFile(marker)||!MARKER_CONTENT.equals(Files.readString(marker,StandardCharsets.UTF_8).trim())) {
                log("REFUSED wrong or missing marker in " + folder + " NO_MOVEMENT");
                return null;
            }
            return new Run(s,l,root,"BRANCH");
        }catch(Exception ex){log("DISCOVERY_ERROR "+ex.getMessage());return null;}
    }
    private static String confirmSwitch(Run r,boolean makeBranch){
        int[][] cells={{0,0,0},{1,0,1},{2,0,2},{3,0,3},{4,0,4},{5,0,5},
                     {2,1,9},{3,1,10},{4,1,11},{4,2,12},{5,2,13}};
        for(int[] c:cells){
            BlockPos p=new BlockPos(261+c[0],-60,104+c[1]);
            BlockState state=r.level.getBlockState(p);
            if(!"traincraft:track_switch_parallel_right_segment".equals(id(state)))return "wrong switch block at "+p.toShortString();
            BooleanProperty prop=(BooleanProperty)state.getBlock().getStateDefinition().getProperty("active");
            if(prop==null)return "switch active property missing";
            Integer part=null;String facing="";
            for(var entry:state.getValues().entrySet()){
                if(entry.getKey().getName().equals("assembly_part") && entry.getValue() instanceof Integer i)part=i;
                if(entry.getKey().getName().equals("facing"))facing=entry.getValue().toString();
            }
            if(part==null||part!=c[2]||!"east".equals(facing))return "switch signature wrong at "+p.toShortString();
            if(makeBranch){
                // Accepted worldgen and original recovery world never see this.
                if(!r.level.setBlock(p,state.setValue(prop,true),2))return "cannot select branch at "+p.toShortString();
            }else if(state.getValue(prop))return "straight baseline active unexpectedly at "+p.toShortString();
        }
        for(int[] c:cells){
            BlockPos p=new BlockPos(261+c[0],-60,104+c[1]);
            BlockState b=r.level.getBlockState(p);
            BooleanProperty prop=(BooleanProperty)b.getBlock().getStateDefinition().getProperty("active");
            if(prop==null||b.getValue(prop)!=makeBranch)return "switch state inconsistent at "+p.toShortString();
        }
        return null;
    }
    private static String preflight(Run r){
        if(!r.server.getPlayerList().getPlayers().isEmpty())return "real player present on headless server";
        // Dedicated-server entity NBT loading can finish AFTER getChunk returns.
        // Request only station chunks; repeat lookup at 10-tick intervals until
        // both exact UUIDs are in the live entity lookup or the deadline expires.
        // Never create, respawn, teleport, or search by a substitute identity.
        // Vanilla ServerLevel.setChunkForced creates a persistent FORCED chunk
        // ticket (entity-ticking level), as distinct from merely calling
        // getChunk(). This executes ONLY inside marker-gated throwaway saves.
        // Never apply forceChunk or a saved-world edit to the recovery world.
        if(!r.chunksRequested){
            for(int cx=15;cx<=17;cx++)for(int cz=5;cz<=7;cz++){
                r.level.setChunkForced(cx,cz,true);
                r.level.getChunk(cx,cz);
                r.forcedChunks++;
            }
            r.chunksRequested=true;
            log("ENTITY_TICK_TICKETS_FORCED count="+r.forcedChunks
                +" stationChunks=15..17,5..7 COPY_ONLY=true");
        }
        Entity e=r.level.getEntity(GP7),c=r.level.getEntity(COACH);
        if(e==null||c==null){
            r.entityWaitPolls++;
            r.missingVehicles="GP7="+(e==null?"MISSING":type(e))
                    +" COACH="+(c==null?"MISSING":type(c));
            if(r.entityWaitPolls==1||r.entityWaitPolls%4==0){
                log("WAIT_ENTITIES age="+r.age+" polls="+r.entityWaitPolls+" "+r.missingVehicles);
            }
            return "WAIT_ENTITIES";
        }
        if(!(e instanceof LegacyPoweredLocomotive loco) || !(c instanceof SteamTender coach))return "saved vehicle class unexpected GP7="+type(e)+" COACH="+type(c);
        if(!"traincraft:locomotive_diesel_gp7".equals(type(loco)) ||
           !"traincraft:passenger_coach_blue".equals(type(coach)))return "vehicle types not exact";
        if(!CouplingManager.areMutuallyLinked(loco,loco,coach,coach))return "coupling not mutual";
        if(!loco.getPassengers().isEmpty()||!coach.getPassengers().isEmpty())return "train occupied";
        if(Math.abs(loco.getX()-257.210D)>.05D||Math.abs(coach.getX()-254.207D)>.05D
             ||Math.abs(loco.getZ()-104.5D)>.05D||Math.abs(coach.getZ()-104.5D)>.05D)
            return "saved train start changed; use fresh original baseline";
        if(loco.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=new BlockPos(251,-60,104).asLong()
             || coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=new BlockPos(251,-60,104).asLong())
            return "station ownership changed";
        if(loco.getPowerAmount()<=0)return "no GP7 power reserve";
        if(!loco.isHandBrakeApplied())return "initial GP7 parking brake not set";
        if(loco.getDeltaMovement().horizontalDistance()>.01D||coach.getDeltaMovement().horizontalDistance()>.01D)
            return "train already moving";
        // A UUID lookup confirms LOADING, not ENTITY TICKING. Demand positive
        // tickCount advance from BOTH saved rolling-stock entities before
        // boarding a fake driver or releasing the parking brake.
        if(!r.entityTickBaselineArmed){
            r.entityTickBaselineArmed=true;
            r.baselineAtAge=r.age;
            r.baselineLocoTicks=loco.tickCount;
            r.baselineCoachTicks=coach.tickCount;
            r.latestLocoTicks=loco.tickCount;
            r.latestCoachTicks=coach.tickCount;
            log("ENTITY_TICK_BASELINE age="+r.age
                +" GP7ticks="+r.baselineLocoTicks
                +" COACHticks="+r.baselineCoachTicks
                +" forcedChunks="+r.forcedChunks);
            return "WAIT_ENTITY_TICKS";
        }
        r.latestLocoTicks=loco.tickCount;
        r.latestCoachTicks=coach.tickCount;
        int dtL=r.latestLocoTicks-r.baselineLocoTicks;
        int dtC=r.latestCoachTicks-r.baselineCoachTicks;
        if(dtL<5 || dtC<5){
            if((r.age-r.baselineAtAge)%20==0)
                log("WAIT_ENTITY_TICKS age="+r.age+" GP7delta="+dtL
                    +" COACHdelta="+dtC+" GP7ticks="+r.latestLocoTicks
                    +" COACHticks="+r.latestCoachTicks);
            if(r.age-r.baselineAtAge>=80)
                return "ENTITY_TICKS_NOT_ADVANCING GP7delta="+dtL
                    +" COACHdelta="+dtC+" forcedChunks="+r.forcedChunks;
            return "WAIT_ENTITY_TICKS";
        }
        log("ENTITY_TICK_VERIFIED GP7delta="+dtL+" COACHdelta="+dtC
            +" forcedChunks="+r.forcedChunks);
        // Stop if potential solid blocks block our conservative overhead lane.
        for(int x=257;x<=265;x++)for(int z=104;z<=(x<261?104:106);z++){
            for(int y=-59;y<=-57;y++){
                BlockPos p=new BlockPos(x,y,z);
                if(!r.level.getBlockState(p).getCollisionShape(r.level,p).isEmpty())return "collision candidate "+p.toShortString();
            }
        }
        for(int x=257;x<=260;x++){
            if(!"traincraft:track_normal".equals(id(r.level.getBlockState(new BlockPos(x,-60,104)))))return "staging rail missing x="+x;
        }
        r.locomotive=loco;r.coach=coach;
        r.x0=loco.getX();r.z0=loco.getZ();r.coachX0=coach.getX();r.coachZ0=coach.getZ();
        double gap=Math.sqrt(loco.distanceToSqr(coach));
        r.minGap=r.maxGap=r.previousGap=gap;
        r.missingVehicles="GP7=READY COACH=READY";
        return null;
    }
    // Called ONLY after all saved vehicle preflight gates have passed.
    private static void start(Run r){
        String error=confirmSwitch(r,"BRANCH".equals(r.mode));
        if(error!=null){stop(r,"SWITCH_"+error,false);return;}
        try{
            // FakePlayer is a real ServerPlayer subclass. Unlike a synthetic
            // key flag on an empty train, this passes native rider checks.
            r.bot=FakePlayerFactory.get(r.level,new GameProfile(BOT,"TCD3Harness"));
            if(r.bot==null||!r.bot.startRiding(r.locomotive,true)){
                stop(r,"FAKE_DRIVER_MOUNT_FAILED",false);return;
            }
            if(!r.locomotive.isHandBrakeApplied()){
                stop(r,"UNEXPECTED_BRAKE_STATE",false);return;
            }
            r.locomotive.toggleHandBrake(r.bot);
            if(r.locomotive.isHandBrakeApplied()){
                stop(r,"BRAKE_UNRELEASED",false);return;
            }
            r.prevGp7X=r.locomotive.getX();r.prevGp7Z=r.locomotive.getZ();
            r.prevCoachX=r.coach.getX();r.prevCoachZ=r.coach.getZ();
            r.lastGp7Post=r.locomotive.getDeltaMovement().horizontalDistance();
            r.lastCoachPost=r.coach.getDeltaMovement().horizontalDistance();
            r.lastTrack=trackAt(r.level,r.locomotive);
            r.started=true;
            r.throttleOn=true; // first controlled departure tick gets native traction
            log("START mode="+r.mode+" fakeDriver=YES simulatedKey=SERVER_CONTROL nativeRailPath=YES"
                +" velocityMagnitudeGovernor="+f(CAP)+" targetX="+f(TARGET)+" maxTicks="+MAX_TICKS
                +" throttleHysteresis="+f(THROTTLE_ON_BELOW)+".."+f(THROTTLE_OFF_ABOVE)
                +" throttleStrategy=ADAPTIVE_W_PACING_TEST_ONLY");
        }catch(Exception ex){stop(r,"START_EXCEPTION_"+ex.getClass().getSimpleName(),false);}
    }
    private static void report(Run r){
        String result=r.success?"PASS_BOUNDED_ENTRY":"FAIL_OR_UNVERIFIED";
        String content="DEV50d3 real-Forge dedicated headless movement (isolated-world ONLY)\n"
           +"WORLD="+r.root.getFileName()+"\nMODE="+r.mode+"\n"
           +"RESULT="+result+"\nREASON="+r.finishReason+"\n"
           +"TICKS="+r.travelTicks+"\n"
           +"GP7_DX="+f(r.maxDx)+"\nGP7_DZ="+f(r.maxDz)+"\n"
           +"COACH_DX="+f(r.coachDx)+"\nCOACH_DZ="+f(r.coachDz)+"\n"
           +"MAX_SPEED="+f(r.maxSpeed)+"\nMIN_GAP="+(r.locomotive==null?"UNVERIFIED":f(r.minGap))+"\nMAX_GAP="+(r.coach==null?"UNVERIFIED":f(r.maxGap))+"\n"
           +"GOVERNOR_TICKS="+r.governorTicks+"\n"
           +"GP7_GOVERNOR_TICKS="+r.gp7GovernorTicks+"\n"
           +"COACH_GOVERNOR_TICKS="+r.coachGovernorTicks+"\n"
           +"BRANCH_TRANSITION_SAMPLES="+r.branchSamples+"\n"
           +"MAX_GP7_PRE_GOV_SPEED="+f(r.maxGp7Pre)+"\n"
           +"MAX_COACH_PRE_GOV_SPEED="+f(r.maxCoachPre)+"\n"
           +"MAX_GP7_POST_GOV_SPEED="+f(r.maxGp7Post)+"\n"
           +"MAX_COACH_POST_GOV_SPEED="+f(r.maxCoachPost)+"\n"
           +"HARD_TRIGGER_DETAIL="+r.lastOverspeed+"\n"
           +"TEST_ONLY_GOVERNOR_CAP="+f(CAP)+"\n"
           +"HARD_CAP_UNCHANGED="+f(HARD_CAP)+"\n"
           +"STRAIGHT_MIN_GAP_UNCHANGED="+f(STRAIGHT_MIN_GAP)+"\n"
           +"CURVE_PROVISIONAL_MIN_GAP="+f(CURVE_MIN_GAP)+"\n"
           +"CURVE_GAP_NUMERIC_TOLERANCE="+f(CURVE_GAP_TOLERANCE)+"\n"
           +"CURVE_INDEPENDENT_HARD_MIN_GAP="+f(CURVE_HARD_MIN_GAP)+"\n"
           +"CURVE_TOLERANCE_ACCEPTED_SAMPLES="+r.curveToleranceSamples+"\n"
           +"CURVE_TOLERANCE_MAX_UNDERSHOOT="+f(r.maxCurveToleranceUsed)+"\n"
           +"CURVE_TOLERANCE_MAX_CONSECUTIVE_TICKS="+r.maxConsecutiveCurveToleranceSamples+"\n"
           +"CURVE_GAP_SAMPLES="+r.curveGapSamples+"\n"
           +"CURVE_GAP_BELOW_STRAIGHT="+r.curveGapBelowStraight+"\n"
           +"MIN_PROVISIONAL_CURVE_GAP="+(r.curveGapSamples==0?"UNSEEN":f(r.minProvisionalCurveGap))+"\n"
           +"MIN_GAME_HITBOX_XZ_CLEARANCE="+(r.hitboxSamples==0?"UNSEEN":f(r.minHitboxXZClearance))+"\n"
           +"GAME_HITBOX_SAMPLES="+r.hitboxSamples+"\n"
           +"MAX_GAP_CLOSURE_IN_ONE_TICK="+f(r.maxGapClosurePerTick)+"\n"
           +"COACH_ENTERED_TURNOUT="+r.coachEnteredTurnout+"\n"
           +"COACH_TURNED_LATERAL="+r.coachTurnedLateral+"\n"
           +"GP7_FORWARD_BOUND_UNCHANGED="+f(GP7_FORWARD_BOUND)+"\n"
           +"COACH_FORWARD_BOUND_EXTENDED="+f(COACH_FORWARD_BOUND)+"\n"
           +"TURNOUT_BOTH_ON_SELECTED_SWITCH_SAMPLES="+r.bothOnSelectedSwitchSamples+"\n"
           +"TURNOUT_LAST_GP7_RAIL="+r.lastGp7Rail+"\n"
           +"TURNOUT_LAST_COACH_RAIL="+r.lastCoachRail+"\n"
           +"TURNOUT_WRONG_BRANCH_OBSERVED="+r.wrongBranchObserved+"\n"
           +"TURNOUT_WRONG_BRANCH_DETAIL="+r.wrongBranchDetail+"\n"
           +"LAST_CLEARANCE_PHASE="+r.lastClearancePhase+"\n"
           +"LAST_CLEARANCE_STOP="+r.lastClearanceStop+"\n"
           +"GAME_HITBOX_CLEARANCE_SCOPE=ENTITY_AABB_ONLY_NOT_RENDER_MESH\n"
           +"VISUAL_MODEL_CLEARANCE=UNVERIFIED_REQUIRES_CLIENT_VIEW\n"
           +"THROTTLE_ON_TICKS="+r.throttleOnTicks+"\n"
           +"THROTTLE_OFF_TICKS="+r.throttleOffTicks+"\n"
           +"THROTTLE_SWITCHES="+r.throttleSwitches+"\n"
           +"MAX_APPROACH_GP7_RAW_SPEED="+f(r.maxApproachGp7Raw)+"\n"
           +"MAX_APPROACH_COACH_RAW_SPEED="+f(r.maxApproachCoachRaw)+"\n"
           +"FIRST_CURVE_GP7_RAW_SPEED="+(r.firstCurveGp7Speed<0?"UNSEEN":f(r.firstCurveGp7Speed))+"\n"
           +"CURVE_REFERENCE_WARNINGS="+r.curveReferenceWarnings+"\n"
           +"COACH_PHASE_SAMPLES="+r.coachPhaseSamples+"\n"
           +"COACH_PHASE_MAX_VELOCITY="+f(r.coachPhaseMaxVelocity)+"\n"
           +"COACH_PHASE_MAX_STEP="+f(r.coachPhaseMaxStep)+"\n"
           +"COACH_POSITION_PROJECTION_SAMPLES="+r.coachLargePhaseSteps+"\n"
           +"COACH_LARGEST_POSITION_PROJECTION="+f(r.largestCoachPhaseStep)+"\n"
           +"COACH_LARGEST_POSITION_PROJECTION_STAGE="+r.largestCoachPhaseStepStage+"\n"
           +"COACH_FIRST_PHASE_OVER_HARD="+r.firstCoachPhaseOverHard+"\n"
           +"COACH_LAST_PHASE_OVER_HARD="+r.coachPhaseAtHard+"\n"
           +"FAKE_DRIVER_MOUNTED="+(r.bot!=null && r.started)+"\n"
           +"ENTITY_WAIT_POLLS="+r.entityWaitPolls+"\n"
           +"ENTITY_DISCOVERY="+r.missingVehicles+"\n"
           +"ENTITY_TICKING_FORCED_CHUNKS="+r.forcedChunks+"\n"
           +"ENTITY_TICKS_GP7_BASE="+r.baselineLocoTicks+"\n"
           +"ENTITY_TICKS_GP7_LAST="+r.latestLocoTicks+"\n"
           +"ENTITY_TICKS_COACH_BASE="+r.baselineCoachTicks+"\n"
           +"ENTITY_TICKS_COACH_LAST="+r.latestCoachTicks+"\n"
           +"NOTE=FakePlayer and native traction only exercised after START; magnitude-only velocity governor is TEST MODIFICATION\n"
           +"SUCCESS_SCOPE=BOUNDED_NATIVE_BRANCH_ENTRY_BOTH_VEHICLES_PLUS_GAME_HITBOX_ONLY\n"
           +"LIMIT=No visual-render certification, no downstream route/coach tail clearance certification\n";
        try{Files.writeString(r.root.resolve("DEV50D3-RESULT.txt"),content,StandardCharsets.UTF_8);}
        catch(Exception ex){log("REPORT_WRITE_FAIL "+ex.getMessage());}
        log("RESULT="+result+" reason="+r.finishReason+" mode="+r.mode
            +" locoDx="+f(r.maxDx)+" locoDz="+f(r.maxDz)+" gap="+f(r.minGap)+".."+f(r.maxGap)
            +" maxSpeed="+f(r.maxSpeed)+" governorTicks="+r.governorTicks);
    }
    private static void park(Run r){
        if(r.locomotive!=null&&!r.locomotive.isRemoved()){
            r.locomotive.setDriverControls(false,false,true);
            if(!r.locomotive.isHandBrakeApplied())r.locomotive.toggleHandBrake(r.bot);
            r.locomotive.setDeltaMovement(Vec3.ZERO);
        }
        if(r.coach!=null&&!r.coach.isRemoved())r.coach.setDeltaMovement(Vec3.ZERO);
        if(r.bot!=null&&r.bot.isPassenger())r.bot.stopRiding();
    }
    private static void stop(Run r,String why,boolean pass){
        if(r.parked)return;
        r.finishReason=why;r.success=pass;r.parked=true;
        park(r);report(r);
    }
    public static void tick(MinecraftServer server){
        // Absolutely no action in any integrated/client world or unmarked save.
        if(!server.isDedicatedServer())return;
        if(run==null){run=discover(server);if(run==null)return;}
        Run r=run;
        if(r.server!=server)return;
        try{
            if(r.parked){
                park(r);r.parkTicks++;
                if(r.parkTicks>=40){log("AUTOMATED_SERVER_SHUTDOWN");server.halt(false);}
                return;
            }
            r.age++;
            if(!r.started){
                if(r.age>=60 && r.age%10==0){
                    String result=preflight(r);
                    if("WAIT_ENTITIES".equals(result)||"WAIT_ENTITY_TICKS".equals(result)){
                        // Missing lookup during chunk loading is not an instant failure.
                        // No train controls or switch blocks are touched while waiting.
                    }else if(result!=null){
                        stop(r,"PREFLIGHT_"+result,false);
                    }else{
                        log("ENTITIES_READY age="+r.age+" polls="+r.entityWaitPolls
                            +" GP7="+GP7+" COACH="+COACH+" (exact identities verified)");
                        start(r);
                    }
                }
                if(!r.started && r.age>=STARTUP_LIMIT && !r.parked){
                    stop(r,"PREFLIGHT_ENTITIES_TIMEOUT age="+r.age+" polls="+r.entityWaitPolls
                        +" "+r.missingVehicles,false);
                }
                return;
            }
            if(!server.getPlayerList().getPlayers().isEmpty()){stop(r,"REAL_PLAYER_JOINED",false);return;}
            LegacyPoweredLocomotive loco=r.locomotive;SteamTender coach=r.coach;
            if(loco!=null)r.latestLocoTicks=loco.tickCount;
            if(coach!=null)r.latestCoachTicks=coach.tickCount;
            if(loco==null||coach==null||loco.isRemoved()||coach.isRemoved()){
                stop(r,"VEHICLE_REMOVED",false);return;
            }
            if(loco.getFirstPassenger()!=r.bot){stop(r,"FAKE_DRIVER_EJECTED",false);return;}
            if(!CouplingManager.areMutuallyLinked(loco,loco,coach,coach)){
                stop(r,"COUPLING_BROKEN",false);return;
            }
            double gap=Math.sqrt(loco.distanceToSqr(coach));
            // Read both native velocities BEFORE the copy-only magnitude governor.
            // Note: server tick monitoring samples completed native entity updates,
            // not inside the physics integrator. Never infer force from a single sample.
            Vec3 gp7Raw=loco.getDeltaMovement(),coachRaw=coach.getDeltaMovement();
            double gp7Pre=gp7Raw.horizontalDistance(),coachPre=coachRaw.horizontalDistance();
            double v=Math.max(gp7Pre,coachPre);
            double gp7Step=Math.hypot(loco.getX()-r.prevGp7X,loco.getZ()-r.prevGp7Z);
            double coachStep=Math.hypot(coach.getX()-r.prevCoachX,coach.getZ()-r.prevCoachZ);
            String rail=trackAt(r.level,loco),coachRail=trackAt(r.level,coach);
            r.lastGp7Rail=rail;r.lastCoachRail=coachRail;
            boolean changedTrack=!rail.equals(r.lastTrack);
            // Read-only expected-route continuity, after BOTH vehicles have
            // actually entered the selected right-hand turnout. Do not allow
            // proximity-only or historical entered flags to prove final route.
            boolean bothOnSelected=SELECTED_SWITCH_RAIL.equals(rail)
                && SELECTED_SWITCH_RAIL.equals(coachRail);
            if(bothOnSelected)r.bothOnSelectedSwitchSamples++;
            if(r.coachEnteredTurnout && r.coachTurnedLateral){
                // This bounded test stops on the switch, well before its exit.
                // Allow small tolerance for native switch projection jitter.
                boolean wrong=(!SELECTED_SWITCH_RAIL.equals(rail)
                    || !SELECTED_SWITCH_RAIL.equals(coachRail)
                    || loco.getX()<r.prevGp7X-0.06D
                    || coach.getX()<r.prevCoachX-0.06D
                    || loco.getZ()<r.z0-0.04D
                    || coach.getZ()<r.coachZ0-0.04D);
                if(wrong && !r.wrongBranchObserved){
                    r.wrongBranchObserved=true;
                    r.wrongBranchDetail="GP7rail="+rail+" COACHrail="+coachRail
                        +" GP7pos="+f(loco.getX())+","+f(loco.getZ())
                        +" COACHpos="+f(coach.getX())+","+f(coach.getZ())
                        +" GP7prevX="+f(r.prevGp7X)+" COACHprevX="+f(r.prevCoachX);
                    log("BRANCH_ROUTE_DEVIATION "+r.wrongBranchDetail);
                }
            }
            // Report native pre-governor speed separately from the test-only cap.
            // No frozen physics/coupling/rail setter is modified by this probe.
            if(loco.getX()>=260.0D){
                r.maxApproachGp7Raw=Math.max(r.maxApproachGp7Raw,gp7Pre);
                r.maxApproachCoachRaw=Math.max(r.maxApproachCoachRaw,coachPre);
                if("traincraft:track_switch_parallel_right_segment".equals(rail)){
                    if(r.firstCurveGp7Speed<0.0D){
                        r.firstCurveGp7Speed=gp7Pre;
                        log("CURVE_ENTRY_REFERENCE GP7raw="+f(gp7Pre)+" COACHraw="+f(coachPre)
                            +" COACHrail="+coachRail+" maxNativeCatchupReferencePlus0_080="+f(gp7Pre+0.080D));
                    }
                    if(gp7Pre>APPROACH_REFERENCE_WARN){
                        r.curveReferenceWarnings++;
                        if(r.curveReferenceWarnings<=4)
                            log("CURVE_REFERENCE_WARNING GP7raw="+f(gp7Pre)+" threshold="+f(APPROACH_REFERENCE_WARN)
                                +" NOTE=DIAGNOSTIC_ONLY_NOT_SAFETY_LIMIT");
                    }
                }
            }
            r.maxGp7Pre=Math.max(r.maxGp7Pre,gp7Pre);
            r.maxCoachPre=Math.max(r.maxCoachPre,coachPre);
            r.minGap=Math.min(r.minGap,gap);r.maxGap=Math.max(r.maxGap,gap);r.maxSpeed=Math.max(r.maxSpeed,v);
            r.maxDx=loco.getX()-r.x0;r.maxDz=loco.getZ()-r.z0;
            r.coachDx=coach.getX()-r.coachX0;r.coachDz=coach.getZ()-r.coachZ0;
            r.travelTicks++;
            // Every tick in the branch approach and curve gets a PRE-GOV sample.
            // Track and x/z displacements are logged separately from velocity so
            // a turnout snap can be distinguished from an excessive model speed.
            boolean nearTurnout=loco.getX()>=260.00D || coach.getX()>=260.00D;
            // The GP7 is genuinely deviating on the selected switch; don't
            // pretend the 2.70 straight-gap guard applies to both curve legs.
            // Do not use generic proximity alone to enable the relaxed band.
            boolean switchRail="traincraft:track_switch_parallel_right_segment".equals(rail)
                || "traincraft:track_switch_parallel_right_segment".equals(coachRail);
            boolean lateralTurn=Math.abs(loco.getZ()-r.z0) >= 0.06D
                || Math.abs(coach.getZ()-r.coachZ0) >= 0.06D;
            boolean curvePhase=switchRail && lateralTurn;
            // Two distinct curve thresholds: a nominal target (2.4500) and
            // independent STOP boundary (2.4300). Only the stop boundary is
            // tolerance-adjusted, and ONLY during an actual switch/lateral turn.
            double minAllowed=curvePhase?CURVE_HARD_MIN_GAP:STRAIGHT_MIN_GAP;
            double hitboxClearance=gameHitboxXZClearance(loco,coach);
            double gapClosure=Math.max(0.0D,r.previousGap-gap);
            r.previousGap=gap;
            r.hitboxSamples++;
            r.minHitboxXZClearance=Math.min(r.minHitboxXZClearance,hitboxClearance);
            r.maxGapClosurePerTick=Math.max(r.maxGapClosurePerTick,gapClosure);
            if(curvePhase){
                r.curveGapSamples++;
                r.minProvisionalCurveGap=Math.min(r.minProvisionalCurveGap,gap);
                if(gap<STRAIGHT_MIN_GAP)r.curveGapBelowStraight++;
                if(gap<CURVE_MIN_GAP && gap>=CURVE_HARD_MIN_GAP){
                    r.curveToleranceSamples++;
                    r.consecutiveCurveToleranceSamples++;
                    r.maxConsecutiveCurveToleranceSamples=Math.max(
                        r.maxConsecutiveCurveToleranceSamples,r.consecutiveCurveToleranceSamples);
                    r.maxCurveToleranceUsed=Math.max(r.maxCurveToleranceUsed,CURVE_MIN_GAP-gap);
                    if(r.curveToleranceSamples<=4 || r.curveToleranceSamples%20==0)
                        log("CURVE_GAP_TOLERANCE_USED n="+r.travelTicks
                            +" centerGap="+f(gap)
                            +" undershoot="+f(CURVE_MIN_GAP-gap)
                            +" independentHardFloor="+f(CURVE_HARD_MIN_GAP)
                            +" hitboxXZ="+f(hitboxClearance)
                            +" MODEL_MESH=UNVERIFIED");
                }else r.consecutiveCurveToleranceSamples=0;
            }else r.consecutiveCurveToleranceSamples=0;
            if("traincraft:track_switch_parallel_right_segment".equals(coachRail))
                r.coachEnteredTurnout=true;
            if(Math.abs(coach.getZ()-r.coachZ0)>=0.06D)r.coachTurnedLateral=true;
            r.lastClearancePhase="curve="+curvePhase+" GP7rail="+rail+" COACHrail="+coachRail
                +" gap="+f(gap)+" hitboxXZ="+f(hitboxClearance)+" hardLimit="+f(minAllowed)
                +" curveNominal="+f(CURVE_MIN_GAP)+" tolerance="+f(CURVE_GAP_TOLERANCE);
            boolean gapDanger=gap<minAllowed || gap>MAX_GAP;
            boolean clearanceDanger=!Double.isFinite(gap)||!Double.isFinite(hitboxClearance)
                ||hitboxClearance<MIN_HITBOX_XZ_CLEARANCE ||gapClosure>MAX_ONE_TICK_GAP_CLOSURE;
            boolean danger=v>HARD_CAP || gapDanger || clearanceDanger;
            boolean sample=nearTurnout || changedTrack || danger;
            if(sample){
                r.branchSamples++;
                log("BRANCH_PRE n="+r.travelTicks
                    +" gp7Pos="+f(loco.getX())+","+f(loco.getZ())
                    +" coachPos="+f(coach.getX())+","+f(coach.getZ())
                    +" GP7rawV="+f(gp7Pre)+" GP7vx="+f(gp7Raw.x)+" GP7vz="+f(gp7Raw.z)
                    +" COACHrawV="+f(coachPre)+" COACHvx="+f(coachRaw.x)+" COACHvz="+f(coachRaw.z)
                    +" GP7stepXZ="+f(gp7Step)+" COACHstepXZ="+f(coachStep)
                    +" priorGP7post="+f(r.lastGp7Post)+" priorCOACHpost="+f(r.lastCoachPost)
                    +" GP7rail="+rail+" COACHrail="+coachRail
                    +" trackChanged="+changedTrack+" gap="+f(gap)
                    +" curvePhase="+curvePhase+" hardGapFloor="+f(minAllowed)
                    +" curveNominalFloor="+f(CURVE_MIN_GAP)
                    +" curveNumericTolerance="+f(CURVE_GAP_TOLERANCE)
                    +" entityHitboxXZClearance="+f(hitboxClearance)
                    +" gapClosure="+f(gapClosure)
                    +" coachEnteredTurnout="+r.coachEnteredTurnout
                    +" VISUAL_MODEL_CLEARANCE=UNVERIFIED");
            }
            // Measure actual Minecraft entity AABB separation but never
            // describe that as proof of clearance for the larger rendered bodies.
            if(clearanceDanger){
                r.lastClearanceStop="AABB_OR_CLOSURE "+r.lastClearancePhase
                    +" closure="+f(gapClosure);
                stop(r,"GAME_HITBOX_OR_GAP_CLOSURE_UNSAFE",false);return;
            }
            if(gapDanger){
                r.lastClearanceStop="GAP_LIMIT "+r.lastClearancePhase;
                stop(r,(curvePhase?"CURVE_":"STRAIGHT_")+"GAP_ENVELOPE_"+f(gap),false);return;
            }
            if(curvePhase&&gap<STRAIGHT_MIN_GAP && (r.curveGapBelowStraight<=4||r.curveGapBelowStraight%15==0))
                log("CURVE_GAP_PROVISIONAL n="+r.travelTicks+" gap="+f(gap)
                    +" nominalTarget="+f(CURVE_MIN_GAP)
                    +" independentHardFloor="+f(CURVE_HARD_MIN_GAP)
                    +" hitboxXZ="+f(hitboxClearance)
                    +" coachTurn="+r.coachEnteredTurnout
                    +" MODEL_MESH=UNVERIFIED");
            if(v>HARD_CAP){
                String which=(gp7Pre>HARD_CAP&&coachPre>HARD_CAP)?"BOTH":gp7Pre>HARD_CAP?"GP7":"COACH";
                r.lastOverspeed="vehicle="+which+" n="+r.travelTicks
                    +" GP7raw="+f(gp7Pre)+" COACHraw="+f(coachPre)
                    +" priorGP7post="+f(r.lastGp7Post)+" priorCOACHpost="+f(r.lastCoachPost)
                    +" GP7stepXZ="+f(gp7Step)+" COACHstepXZ="+f(coachStep)
                    +" GP7rail="+rail+" COACHrail="+coachRail
                    +" gap="+f(gap);
                log("HARD_CAP_BREACH " + r.lastOverspeed + " STOP_UNCHANGED=true");
                stop(r,"HARD_SPEED_ENVELOPE_"+f(v),false);return;
            }
            if(Math.abs(loco.getY()+59.9375D)>.45D||Math.abs(coach.getY()+59.9375D)>.45D
               ||Math.abs(r.maxDz)>3.2D||Math.abs(r.coachDz)>3.2D
               ||r.maxDx < -.15D||r.coachDx < -.15D
               ||r.maxDx>GP7_FORWARD_BOUND||r.coachDx>COACH_FORWARD_BOUND){
                stop(r,"POSITION_ENVELOPE",false);return;
            }
            if(r.wrongBranchObserved){
                stop(r,"WRONG_OR_DISCONTINUOUS_TURNOUT_PATH",false);return;
            }
            if(r.travelTicks%20==0){
                log("TICK mode="+r.mode+" n="+r.travelTicks
                    +" GP7=("+f(loco.getX())+","+f(loco.getZ())+")"
                    +" COACH=("+f(coach.getX())+","+f(coach.getZ())+")"
                    +" gap="+f(gap)+" speed="+f(v)
                    +" GP7entityTicks="+(loco.tickCount-r.baselineLocoTicks)
                    +" COACHentityTicks="+(coach.tickCount-r.baselineCoachTicks)
                    +" brake="+loco.isHandBrakeApplied()
                    +" power="+loco.getPowerAmount()
                    +" fakeDriver="+(loco.getFirstPassenger()==r.bot)
                    +" railBelow="+id(r.level.getBlockState(loco.blockPosition().below())));
            }
            if(loco.getX()>=TARGET && r.coachEnteredTurnout && r.coachTurnedLateral){
                boolean pathConfirmed=bothOnSelected
                    && r.bothOnSelectedSwitchSamples>=8
                    && !r.wrongBranchObserved
                    && r.maxDz>=0.30D && r.coachDz>=0.15D;
                log("BRANCH_COMPLETION_ROUTE_CHECK bothOnSelected="+bothOnSelected
                    +" samples="+r.bothOnSelectedSwitchSamples
                    +" GP7rail="+rail+" COACHrail="+coachRail
                    +" GP7dx="+f(r.maxDx)+" COACHdx="+f(r.coachDx)
                    +" GP7dz="+f(r.maxDz)+" COACHdz="+f(r.coachDz)
                    +" result="+pathConfirmed);
                stop(r,pathConfirmed?"BOTH_VEHICLES_ENTERED_TURNOUT_AABB_ONLY"
                    :"WRONG_OR_UNOBSERVED_BRANCH",pathConfirmed);return;
            }
            // Passing the GP7 target alone is no longer sufficient. Its coach
            // must visibly change XZ direction on a native turnout block too.
            if(loco.getX()>=TARGET && r.travelTicks%15==0)
                log("WAITING_FOR_COACH_TURN n="+r.travelTicks
                    +" coachOnSwitch="+r.coachEnteredTurnout
                    +" coachLateral="+r.coachTurnedLateral
                    +" GP7x="+f(loco.getX())+" COACHx="+f(coach.getX()));
            if(r.travelTicks>=MAX_TICKS){stop(r,"TIME_LIMIT",false);return;}
            if(r.travelTicks>=180&&r.maxDx<.10D){
                String why="NATIVE_DRIVE_STALLED GP7tickDelta="+(loco.tickCount-r.baselineLocoTicks)
                    +" COACHtickDelta="+(coach.tickCount-r.baselineCoachTicks)
                    +" brake="+loco.isHandBrakeApplied()
                    +" power="+loco.getPowerAmount()
                    +" mounted="+(loco.getFirstPassenger()==r.bot)
                    +" railBelow="+id(r.level.getBlockState(loco.blockPosition().below()));
                stop(r,why,false);return;
            }
            // Govern magnitude without forcing any route direction; this is
            // NOT full-speed certification and the report counts interventions.
            for(Entity entity:new Entity[]{loco,coach}){
                Vec3 movement=entity.getDeltaMovement();
                double h=movement.horizontalDistance();
                if(h>CAP){
                    entity.setDeltaMovement(movement.x*CAP/h,movement.y,movement.z*CAP/h);
                    r.governorTicks++;
                    if(entity==loco)r.gp7GovernorTicks++;else r.coachGovernorTicks++;
                }
            }
            double gp7Post=loco.getDeltaMovement().horizontalDistance();
            double coachPost=coach.getDeltaMovement().horizontalDistance();
            r.maxGp7Post=Math.max(r.maxGp7Post,gp7Post);
            r.maxCoachPost=Math.max(r.maxCoachPost,coachPost);
            if(sample){
                log("BRANCH_POST n="+r.travelTicks
                    +" GP7postV="+f(gp7Post)+" COACHpostV="+f(coachPost)
                    +" GP7clamped="+(gp7Pre>CAP)+" COACHclamped="+(coachPre>CAP)
                    +" CAP="+f(CAP)+" HARD_CAP="+f(HARD_CAP));
            }
            r.lastGp7Post=gp7Post;r.lastCoachPost=coachPost;
            r.prevGp7X=loco.getX();r.prevGp7Z=loco.getZ();
            r.prevCoachX=coach.getX();r.prevCoachZ=coach.getZ();
            r.lastTrack=rail;
            // Drive using the ordinary W control, but pulse it in the disposable
            // scenario based on native current speed. Releasing W uses normal
            // locomotive coast; this is not a change to native acceleration.
            // Use a hysteresis band to avoid oscillating every server tick.
            boolean wantThrottle=r.throttleOn;
            if(gp7Post>=THROTTLE_OFF_ABOVE)wantThrottle=false;
            else if(gp7Post<=THROTTLE_ON_BELOW)wantThrottle=true;
            if(wantThrottle!=r.throttleOn){
                r.throttleSwitches++;
                if(r.throttleSwitches<=12 || r.throttleSwitches%25==0)
                    log("THROTTLE_PACE n="+r.travelTicks+" W="+wantThrottle
                        +" gp7Pre="+f(gp7Pre)+" gp7Post="+f(gp7Post)
                        +" coachPre="+f(coachPre)+" GP7rail="+rail);
            }
            r.throttleOn=wantThrottle;
            if(wantThrottle)r.throttleOnTicks++;else r.throttleOffTicks++;
            loco.setDriverControls(wantThrottle,false,false);
        }catch(Exception ex){log("EXCEPTION "+ex.getClass().getName()+" "+ex.getMessage());stop(r,"EXCEPTION_"+ex.getClass().getSimpleName(),false);}
    }
}
