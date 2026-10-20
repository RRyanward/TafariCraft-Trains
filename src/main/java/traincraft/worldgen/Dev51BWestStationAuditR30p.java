package traincraft.worldgen;
/** R30p read-only station stopping and R30n session-lock audit. Never authorizes driving. */
public final class Dev51BWestStationAuditR30p {
 private Dev51BWestStationAuditR30p(){}
 public static final int STATION_X=41;
 public record Evidence(boolean disposable,boolean sourceDisk,boolean built,boolean stopper,
    boolean westStationId,boolean westStationClass,boolean stationEW,Boolean westActive,
    boolean westNeighborSignal,int westBestSignal,boolean sourceStationId,
    Boolean sourceActive,int sourceBestSignal,int testLocos,boolean handbrake,
    boolean stopped,double x,double z,double minX,double maxX,boolean r30nSessionLock,
    boolean r30nTrialActive,String r30nLast){}
 public record Verdict(String status,boolean stationOverlap,boolean railUnpowered,
    boolean sourceHandlerApplicable,boolean lockIsSessionOnly,boolean mayMove,
    boolean mayModifyRail,boolean mayReleaseLock,boolean collisionCertified){}
 public static Verdict inspect(Evidence e){
  if(e==null)throw new IllegalArgumentException("null station evidence");
  boolean overlap=e.testLocos()==1&&Double.isFinite(e.minX())&&Double.isFinite(e.maxX())
    &&e.minX()<42.0&&e.maxX()>41.0;
  boolean unpowered=!e.westNeighborSignal()&&e.westBestSignal()==0;
  boolean handlerApplicable=e.westStationId()&&e.westStationClass()&&unpowered;
  String status;
  if(!e.disposable())status="HOLD_DISPOSABLE_COPY_ONLY";
  else if(!e.sourceDisk()||!e.built()||!e.stopper())status="HOLD_SOURCE_RAILS_OR_BUFFER";
  else if(!e.westStationId()||!e.westStationClass()||!e.stationEW()||!e.sourceStationId())status="HOLD_STATION_ID_CLASS_OR_SHAPE";
  else if(e.westActive()==null||e.sourceActive()==null)status="HOLD_ACTIVE_PROPERTY_MISSING";
  else if(e.westNeighborSignal()!=(e.westBestSignal()>0))status="HOLD_INCONSISTENT_REDSTONE_READINGS";
  else if(e.westActive()!=e.westNeighborSignal())status="HOLD_ACTIVE_REDSTONE_MISMATCH";
  else if(e.testLocos()!=1||!Double.isFinite(e.x())||!Double.isFinite(e.z())||e.x()<37.0||e.x()>52.0||Math.abs(e.z()+2.5)>.8)
   status="HOLD_TEST_GP7_COUNT_OR_POSITION";
  else if(!e.handbrake()||!e.stopped())status="HOLD_TEST_GP7_HAND_BRAKE_AND_STOP";
  else if(e.r30nTrialActive())status="HOLD_R30N_TRIAL_STILL_ACTIVE";
  else if(!unpowered)status="HOLD_STATION_HAS_REDSTONE_CAUSE_UNCONFIRMED";
  else if(overlap)status="HOLD_UNPOWERED_STATION_STOP_HANDLER_APPLICABLE_LOCK_SESSION_ONLY";
  else status="HOLD_UNPOWERED_STATION_NEARBY_HANDLER_APPLICABLE_STOP_NOT_PROVEN";
  return new Verdict(status,overlap,unpowered,handlerApplicable,true,false,false,false,false);
 }
}
