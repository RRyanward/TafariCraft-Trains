package traincraft.worldgen;
/** R30m bounded read-only west-buffer telemetry evaluator. NEVER certifies collision from kinematics alone. */
public final class Dev51BWestStopSafetyR30m {
  private Dev51BWestStopSafetyR30m(){}
  public static final int MAX_TICKS=600;
  public static final double MIN_START_X=44.0, MAX_START_X=51.0;
  public static final double BUFFER_CENTER_X=36.5, VIOLATION_X=36.5, APPROACH_X=39.0;
  public static final double ARM_SPEED_MAX=0.005, OBSERVED_SPEED_MAX=0.12, STOP_SPEED=0.002;
  public record Gate(boolean originalNine,boolean sourceDisk,boolean blocksBuilt,boolean stopperEast,boolean collisionShape,
      int testLocos,int otherVehicles,boolean testParked,boolean testBraked,double testX,double testZ) {}
  public record Verdict(String status,boolean armAllowed,boolean mayDrive,boolean mayDispatch,boolean stopCertified) {}
  public static Verdict evaluate(Gate g) {
    if(g==null)throw new IllegalArgumentException("Missing gate");
    if(!g.originalNine()||!g.sourceDisk())return hold("HOLD_R30I_SOURCE_ORIGINAL_9");
    if(!g.blocksBuilt()||!g.stopperEast())return hold("HOLD_WEST_AS_BUILT_OR_STOPPER_FACING");
    if(!g.collisionShape())return hold("HOLD_STOPPER_COLLISION_SHAPE_EMPTY");
    if(g.testLocos()==0)return hold("HOLD_SEPARATE_TEST_LOCO_ABSENT");
    if(g.testLocos()!=1||g.otherVehicles()!=0)return hold("HOLD_TEST_ENTITY_CONFLICT");
    if(g.testX()<MIN_START_X||g.testX()>MAX_START_X||Math.abs(g.testZ()+2.5)>0.75)
      return hold("HOLD_TEST_LOCO_START_POSITION");
    if(!g.testParked()||!g.testBraked())return hold("HOLD_TEST_LOCO_PARK_AND_HANDBRAKE");
    return new Verdict("READY_OBSERVATION_ONLY_NO_DRIVE_AUTHORITY",true,false,false,false);
  }
  public static Verdict hold(String status){return new Verdict(status,false,false,false,false);}
  public record Sample(long ticks,double x,double z,double speed,boolean present,boolean otherVehicle,
      boolean sourceValid,boolean blockValid){}
  public record Track(boolean breached,boolean approachObserved,boolean motionObserved,boolean speedExceeded,
      double minX,double maxSpeed,long samples){}
  public static Track step(Track before,Sample s){
    if(before==null||s==null)throw new IllegalArgumentException("Missing telemetry");
    boolean bad=!s.present()||s.otherVehicle()||!s.sourceValid()||!s.blockValid()
       ||s.x()<VIOLATION_X||Math.abs(s.z()+2.5)>1.5;
    return new Track(before.breached()||bad, before.approachObserved()||s.x()<=APPROACH_X,
       before.motionObserved()||s.speed()>STOP_SPEED, before.speedExceeded()||s.speed()>OBSERVED_SPEED_MAX,
       Math.min(before.minX(),s.x()),Math.max(before.maxSpeed(),s.speed()),before.samples()+1);
  }
  public static String report(Track t){
    if(t==null)return "HOLD_NOT_ARMED";
    if(t.breached())return "FAILED_BUFFER_OR_ISOLATION_BREACH_NO_ROUTE_AUTHORITY";
    if(t.speedExceeded())return "HOLD_TEST_TOO_FAST_NO_ROUTE_AUTHORITY";
    if(!t.motionObserved())return "HOLD_NO_MOVEMENT_OBSERVED";
    if(!t.approachObserved())return "HOLD_STOPPER_NOT_APPROACHED";
    return "OBSERVED_APPROACH_ONLY_COLLISION_CAUSE_NOT_CERTIFIED";
  }
}
