package traincraft.worldgen;
/** R30n deterministic low-speed manual-approach evaluator. NO buffer impact or route certification. */
public final class Dev51BWestLowSpeedR30n {
 private Dev51BWestLowSpeedR30n(){}
 public static final long MAX_TICKS=800;
 public static final double MIN_START_X=44,MAX_START_X=51, CENTER_Z=-2.5;
 public static final double MAX_BPT=0.075, WATCHDOG_X=38.0, APPROACH_X=40.0, STOP_BPT=0.003;
 public record Gate(boolean disposable,boolean sourceDisk,boolean built,boolean stopper,boolean collision,
    boolean backup,int testLocos,int otherVehicles,boolean parked,boolean handbrake,
    double x,double z){}
 public record Verdict(String status,boolean armAllowed,boolean driveAuthority,boolean certified){}
 public static Verdict gate(Gate g){
    if(g==null)throw new IllegalArgumentException("missing gate");
    if(!g.disposable)return hold("HOLD_DISPOSABLE_COPY_ONLY");
    if(!g.backup)return hold("HOLD_FRESH_R30N_BACKUP_REQUIRED");
    if(!g.sourceDisk||!g.built||!g.stopper||!g.collision)return hold("HOLD_SOURCE_OR_BUFFER_INVALID");
    if(g.testLocos()!=1||g.otherVehicles()!=0)return hold("HOLD_TEST_LOCO_ISOLATION");
    if(g.x()<MIN_START_X||g.x()>MAX_START_X||Math.abs(g.z()-CENTER_Z)>0.6)return hold("HOLD_START_POSITION");
    if(!g.parked()||!g.handbrake())return hold("HOLD_TEST_LOCO_HAND_BRAKE");
    return new Verdict("READY_GUARDED_MANUAL_APPROACH_CONFIRM_REQUIRED",true,false,false);
 }
 public static Verdict hold(String s){return new Verdict(s,false,false,false);}
 public record Sample(long ticks,double x,double z,double speed,boolean present,boolean isolated,
    boolean sourceValid,boolean railsValid,boolean bufferValid){}
 public record Track(double startX,double minX,double maxSpeed,long samples,
    boolean moved,boolean approached,boolean watchdog,boolean overspeed,
    boolean reverse,boolean isolationBreach,boolean settled,int stillTicks){}
 public static Track start(double x){return new Track(x,x,0,0,false,false,false,false,false,false,false,0);}
 public static Track step(Track t,Sample s){
    if(t==null||s==null)throw new IllegalArgumentException("missing telemetry");
    boolean breach=!s.present()||!s.isolated()||!s.sourceValid()||!s.railsValid()||!s.bufferValid()
         ||Math.abs(s.z()-CENTER_Z)>0.75||!Double.isFinite(s.x())||!Double.isFinite(s.speed());
    boolean moved=t.moved()||s.x()<=t.startX()-0.50;
    boolean approached=t.approached()||s.x()<=APPROACH_X;
    boolean speed=s.speed()>MAX_BPT;
    boolean reverse=s.x()>t.startX()+0.5;
    boolean watchdog=s.x()<=WATCHDOG_X;
    int still=(moved&&s.speed()<STOP_BPT)?t.stillTicks()+1:0;
    return new Track(t.startX(),Math.min(t.minX(),s.x()),Math.max(t.maxSpeed(),s.speed()),t.samples()+1,
      moved,approached,t.watchdog()||watchdog,t.overspeed()||speed,
      t.reverse()||reverse,t.isolationBreach()||breach,still>=40,still);
 }
 public static boolean emergency(Track t){return t.watchdog()||t.overspeed()||t.reverse()||t.isolationBreach();}
 public static String report(Track t,long ticks){
    if(t==null)return "HOLD_NOT_ARMED";
    if(t.isolationBreach())return "FAILED_ISOLATION_OR_SOURCE_CHANGED_WATCHDOG";
    if(t.watchdog())return "HOLD_WATCHDOG_INTERVENED_NO_NATIVE_STOP_CLAIM";
    if(t.overspeed())return "HOLD_SPEED_EXCEEDED_WATCHDOG";
    if(t.reverse())return "HOLD_WRONG_DIRECTION_WATCHDOG";
    if(t.settled())return t.approached()?"OBSERVED_LOW_SPEED_APPROACH_AND_MANUAL_STOP_NOT_BUFFER_CERTIFIED":"HOLD_STOPPED_BEFORE_APPROACH";
    if(ticks>=MAX_TICKS)return t.moved()?"HOLD_TIMEOUT_MOVEMENT_INCOMPLETE":"HOLD_NO_MOVEMENT_OBSERVED";
    return "OBSERVATION_ACTIVE";
 }
}
