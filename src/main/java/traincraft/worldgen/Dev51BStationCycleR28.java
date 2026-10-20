package traincraft.worldgen;

/**
 * DEV51B R28, pure Java 17 supervision witness. NO locomotive control and
 * NO permission to dispatch. An "arrival" here means a parked checkpoint
 * inside the SAME 17-rail station, not a route to a second station.
 */
public final class Dev51BStationCycleR28 {
    public static final double MAX_EAST_BLOCKS = 0.750D;
    public static final int MAX_MOTION_TICKS = 38;
    private Dev51BStationCycleR28() {}

    public record Arrival(long ticks, double east, double lateral,
                          double locoBpt, double coachBpt, double modelSeam,
                          boolean exactWorld, boolean stagedPairOwned,
                          boolean coupled, boolean handbrake,
                          boolean loaded, boolean noOtherDeparture,
                          boolean r27Success, boolean automaticDriverDisabled) {}
    public record Verdict(boolean passed, String reason) {}

    public static Verdict audit(Arrival a) {
        if (a == null) return new Verdict(false, "NULL_SAMPLE");
        if (!a.exactWorld()) return new Verdict(false, "DISPOSABLE_WORLD_LOST");
        if (!a.stagedPairOwned()) return new Verdict(false, "PAIR_OWNERSHIP_CHANGED");
        if (!a.loaded()) return new Verdict(false, "PAIR_UNLOADED");
        if (!a.noOtherDeparture()) return new Verdict(false, "OTHER_DEPARTURE_ACTIVE");
        if (!a.automaticDriverDisabled()) return new Verdict(false, "AUTOMATIC_DRIVER_NOT_DISABLED");
        if (!a.r27Success()) return new Verdict(false, "R27_STOP_NOT_VERIFIED");
        if (!a.coupled()) return new Verdict(false, "COUPLING_NOT_MUTUAL");
        if (!a.handbrake()) return new Verdict(false, "HANDBRAKE_NOT_APPLIED");
        if (!Double.isFinite(a.east()) || a.east() < 0.10D
                || a.east() > MAX_EAST_BLOCKS + 0.00001D)
            return new Verdict(false, "DISPLACEMENT_OUTSIDE_MICRO_CYCLE");
        if (!Double.isFinite(a.lateral()) || Math.abs(a.lateral()) > 0.08D)
            return new Verdict(false, "LATERAL_DEVIATION");
        if (!Double.isFinite(a.locoBpt()) || a.locoBpt() > 0.003D || a.locoBpt() < 0)
            return new Verdict(false, "GP7_NOT_PARKED");
        if (!Double.isFinite(a.coachBpt()) || a.coachBpt() > 0.003D || a.coachBpt() < 0)
            return new Verdict(false, "COACH_NOT_PARKED");
        if (!Double.isFinite(a.modelSeam()) || a.modelSeam() < 0.10D)
            return new Verdict(false, "VISIBLE_CLEARANCE_INSUFFICIENT");
        if (a.ticks() < 1 || a.ticks() > MAX_MOTION_TICKS + 8)
            return new Verdict(false, "TIMING_OUTSIDE_SUPERVISED_WINDOW");
        return new Verdict(true, "SAME_STATION_PARKED_ARRIVAL_VERIFIED");
    }
}
