package traincraft.service;

/**
 * DEV51B R32: fail-closed, world-inert railway service decision core.
 * This class neither registers a tick listener nor controls a train or world.
 * Its only live adapter in R32 is an explicitly isolated native Forge GameTest.
 * Production auto-dispatch, chunk tickets and save-world train driving stay OFF.
 */
public final class GuardedRailwayServiceController {
    public enum Phase { LOCKED, WESTBOUND, WEST_DWELL, EASTBOUND, EAST_APPROACH, EAST_DWELL, COMPLETE, HOLD }
    public enum Instruction { NONE, DEPART_WEST, RELEASE_AND_RETURN_EAST, ARM_EAST_STOP, COMPLETE, EMERGENCY_HOLD }
    public record Gate(boolean nativeTestAuthorized, boolean routeConnected,
                       boolean stationsValid, boolean supportsPresent, boolean chunksLoaded,
                       boolean corridorClear, boolean switchAligned, boolean coupled,
                       boolean brakeReleased, boolean parked, boolean signalWestStop,
                       boolean signalEastPass) {
        public boolean pass() {
            return nativeTestAuthorized && routeConnected && stationsValid && supportsPresent
                && chunksLoaded && corridorClear && switchAligned && coupled
                && brakeReleased && parked && signalWestStop && signalEastPass;
        }
    }
    public record Observation(double engineX, double engineDx, double coachX,
                              double coachRearX, double engineFrontX,
                              double westRailStartX, double eastRailStartX,
                              boolean coupled, boolean corridorClear, boolean trackContinuous,
                              boolean chunksLoaded, boolean withinBounds, boolean speedBounded,
                              boolean handbrakeReleased, boolean westPowered, boolean eastPowered) {
        public boolean safe() {
            return Double.isFinite(engineX) && Double.isFinite(engineDx)
                && Double.isFinite(coachX) && Double.isFinite(coachRearX)
                && Double.isFinite(engineFrontX)
                && Double.isFinite(westRailStartX) && Double.isFinite(eastRailStartX)
                && coupled && corridorClear && trackContinuous && chunksLoaded
                && withinBounds && speedBounded && handbrakeReleased
                && coachX > engineX + 2.3 && coachX < engineX + 3.8;
        }
    }
    private Phase phase = Phase.LOCKED;
    private String holdReason = "NOT_STARTED";
    private int ticks, dwell;
    private double initialX, westMin, eastMax;
    private boolean stopArmed;

    public Phase phase() { return phase; }
    public String holdReason() { return holdReason; }
    public boolean isActive() { return phase != Phase.LOCKED && phase != Phase.HOLD && phase != Phase.COMPLETE; }
    private Instruction hold(String reason) {
        phase = Phase.HOLD;
        holdReason = reason;
        return Instruction.EMERGENCY_HOLD;
    }
    public Instruction start(Gate gate, Observation observed) {
        if (phase != Phase.LOCKED) return hold("RESTART_DENIED");
        if (gate == null || !gate.pass()) return hold("DISPATCH_GATE_DENIED");
        if (observed == null || !observed.safe()) return hold("START_TELEMETRY_UNSAFE");
        if (Math.abs(observed.engineDx()) > 0.001) return hold("NOT_PARKED");
        if (observed.westPowered() || !observed.eastPowered()) return hold("WRONG_DEPARTURE_SIGNALS");
        if (observed.eastRailStartX() - observed.westRailStartX() < 4.0)
            return hold("STATIONS_TOO_CLOSE");
        initialX = observed.engineX();
        westMin = initialX;
        eastMax = initialX;
        ticks = dwell = 0;
        phase = Phase.WESTBOUND;
        holdReason = "";
        return Instruction.DEPART_WEST;
    }
    public Instruction advance(Observation observed) {
        if (!isActive()) return Instruction.NONE;
        if (++ticks > 115) return hold("JOURNEY_TIMEOUT");
        if (observed == null || !observed.safe()) return hold("LIVE_ROUTE_OR_CONSIST_UNSAFE");
        final double x = observed.engineX();
        final double speed = Math.abs(observed.engineDx());
        if (x < observed.westRailStartX()-1.0 || x > observed.eastRailStartX()+1.6)
            return hold("ROUTE_END_EXCEEDED");
        switch (phase) {
            case WESTBOUND -> {
                if (observed.westPowered() || !observed.eastPowered()) return hold("OUTBOUND_SIGNAL_CHANGED");
                westMin = Math.min(westMin,x);
                if (ticks > 53) return hold("WEST_ARRIVAL_TIMEOUT");
                if (initialX - westMin > .09 && speed < .0005
                    && x > observed.westRailStartX()-.5 && x < observed.westRailStartX()+1.6) {
                    phase = Phase.WEST_DWELL;
                    dwell = 0;
                }
            }
            case WEST_DWELL -> {
                if (observed.westPowered() || speed >= .0005) return hold("WEST_STOP_LOST");
                if (++dwell >= 4) {
                    phase = Phase.EASTBOUND;
                    return Instruction.RELEASE_AND_RETURN_EAST;
                }
            }
            case EASTBOUND -> {
                if (!observed.westPowered() || !observed.eastPowered())
                    return hold("RETURN_PREARM_SIGNAL_CHANGED");
                eastMax = Math.max(eastMax,x);
                double east = observed.eastRailStartX();
                if (observed.coachRearX() > east+1.12 && observed.engineFrontX() < east-.12) {
                    stopArmed = true;
                    phase = Phase.EAST_APPROACH;
                    return Instruction.ARM_EAST_STOP;
                }
                if (observed.engineFrontX() >= east-.12)
                    return hold("SAFE_STATION_ARMING_WINDOW_MISSED");
            }
            case EAST_APPROACH -> {
                if (!observed.westPowered() || observed.eastPowered())
                    return hold("EAST_STOP_NOT_ARMED");
                eastMax = Math.max(eastMax,x);
                double east = observed.eastRailStartX();
                if (stopArmed && eastMax - westMin > 1.0 && speed < .0005
                    && observed.engineFrontX() >= east-.08
                    && x > east-.85 && x < east+1.85) {
                    phase = Phase.EAST_DWELL;
                    dwell = 0;
                }
            }
            case EAST_DWELL -> {
                if (observed.eastPowered() || speed >= .0005 || !stopArmed)
                    return hold("EAST_STATION_DWELL_UNSAFE");
                if (++dwell >= 4) {
                    phase = Phase.COMPLETE;
                    return Instruction.COMPLETE;
                }
            }
            default -> { return hold("INVALID_CONTROLLER_STATE"); }
        }
        return Instruction.NONE;
    }
}
