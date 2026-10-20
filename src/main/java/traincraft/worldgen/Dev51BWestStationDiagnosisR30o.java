package traincraft.worldgen;
/** Pure R30o station-stop evidence evaluator; read-only and never licenses movement. */
public final class Dev51BWestStationDiagnosisR30o {
 private Dev51BWestStationDiagnosisR30o(){}
 public static final int STATION_X=41, STATION_Y=-60, STATION_Z=-3;
 public static final double PREVIOUS_START_X=48.5, PREVIOUS_MIN_X=42.42952825689375;
 public record Evidence(boolean disposable,boolean sourceDisk,boolean built,boolean bufferEast,
   boolean stationPresent,boolean stationShapeEW,Boolean powered,boolean directSignal,
   int bestNeighborSignal,int locoCount,boolean handbrake,boolean stopped,
   double locoX,double locoZ,double bboxMinX,double bboxMaxX,boolean guardLock,
   String lastR30nStatus){}
 public record Verdict(String status,boolean overlap,boolean redstoneObserved,
   boolean mayModifyBlocks,boolean mayReleaseLock,boolean mayDrive,boolean certified){}
 public static Verdict inspect(Evidence e){
   if(e==null)throw new IllegalArgumentException("missing evidence");
   boolean intersection=e.stationPresent()&&Double.isFinite(e.bboxMinX())&&Double.isFinite(e.bboxMaxX())
      &&e.bboxMinX()<STATION_X+1&&e.bboxMaxX()>STATION_X;
   boolean signal=e.directSignal()||e.bestNeighborSignal()>0||Boolean.TRUE.equals(e.powered());
   String status;
   if(!e.disposable())status="HOLD_DISPOSABLE_COPY_ONLY";
   else if(!e.sourceDisk()||!e.built()||!e.bufferEast())status="HOLD_RAILWAY_SOURCE_OR_BUFFER_CHANGED";
   else if(!e.stationPresent()||!e.stationShapeEW())status="HOLD_STATION_RAIL_ID_OR_SHAPE_MISMATCH";
   else if(e.locoCount()!=1||!Double.isFinite(e.locoX())||!Double.isFinite(e.locoZ())
         ||e.locoX()<37||e.locoX()>52||Math.abs(e.locoZ()+2.5)>0.8)status="HOLD_SEPARATE_GP7_POSITION_OR_ID";
   else if(!e.handbrake()||!e.stopped())status="HOLD_TEST_GP7_MUST_BE_BRAKED_AND_STOPPED";
   else if(e.powered()==null)status="HOLD_STATION_HAS_NO_POWERED_PROPERTY_REVIEW_LOGS";
   else if(signal)status="HOLD_POWERED_STATION_STOP_CAUSE_UNCONFIRMED";
   else if(intersection)status="HOLD_UNPOWERED_STATION_INTERCEPT_SUSPECTED";
   else status="HOLD_UNPOWERED_STATION_NEARBY_STOP_CAUSE_UNPROVEN";
   return new Verdict(status,intersection,signal,false,false,false,false);
 }
}
