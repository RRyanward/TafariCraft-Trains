/* TafariCraft - Trains DEV51B R27. Pure Java 17 supervisory evidence policy.
 * THIS CLASS NEVER issues motion, edits worlds or certifies automatic services.
 */
package traincraft.worldgen;
public final class Dev51BSupervisedShuntR27 {
    private Dev51BSupervisedShuntR27() {}
    public static final int MAX_TICKS=42;
    public static final double MAX_FORWARD=0.82;
    public static final double MAX_SPEED_BLOCKS_PER_TICK=0.08;
    public record Sample(long elapsedTicks, double dx, double dz, double speedBpt,
                         boolean handbrakeApplied, boolean departureActive,
                         boolean locomotiveLoaded, boolean otherDepartureActive) {}
    public record Verdict(String state, boolean finished, boolean passed,
                          boolean emergencyStopRequired, String reason) {}
    public static Verdict audit(Sample s) {
        if(s==null || s.elapsedTicks()<0 || !Double.isFinite(s.dx()) ||
           !Double.isFinite(s.dz()) || !Double.isFinite(s.speedBpt()))
            return new Verdict("FAILED",true,false,true,"INVALID_OBSERVATION");
        if(!s.locomotiveLoaded())return new Verdict("FAILED",true,false,true,"LOCOMOTIVE_MISSING");
        if(s.otherDepartureActive())return new Verdict("FAILED",true,false,true,"UNRELATED_DEPARTURE_ACTIVE");
        if(s.dx() < -0.05 || s.dx()>MAX_FORWARD || Math.abs(s.dz())>0.35)
            return new Verdict("FAILED",true,false,true,"TRAVEL_OUTSIDE_SHORT_TEST_ENVELOPE");
        if(s.speedBpt()>MAX_SPEED_BLOCKS_PER_TICK || s.speedBpt()<0)
            return new Verdict("FAILED",true,false,true,"SPEED_OUTSIDE_TEST_LIMIT");
        if(s.elapsedTicks()>MAX_TICKS || (s.departureActive() && s.elapsedTicks()>=MAX_TICKS))
            return new Verdict("FAILED",true,false,true,"TEST_TIMEOUT");
        if(s.departureActive()) {
            if(s.handbrakeApplied())return new Verdict("FAILED",true,false,true,"BRAKE_DURING_ACTIVE_SHUNT");
            return new Verdict("OBSERVING",false,false,false,"MANUAL_DEPARTURE_STILL_ACTIVE");
        }
        if(!s.handbrakeApplied())return new Verdict("FAILED",true,false,true,"FINAL_HAND_BRAKE_NOT_APPLIED");
        if(s.speedBpt()>0.012)return new Verdict("FAILED",true,false,true,"FINAL_MOTION_NOT_STOPPED");
        if(s.dx()<0.10)return new Verdict("FAILED",true,false,false,"NO_MEASURABLE_CONTROLLED_MOVEMENT");
        if(s.dx()>0.80 || Math.abs(s.dz())>0.15)
            return new Verdict("FAILED",true,false,true,"FINAL_STOP_OUTSIDE_ENVELOPE");
        return new Verdict("SUPERVISED_SHUNT_PASS",true,true,false,"PARKED_WITHIN_TEST_ENVELOPE");
    }
}
