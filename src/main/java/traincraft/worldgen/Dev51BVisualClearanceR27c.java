package traincraft.worldgen;

/**
 * R27c conservative, read-only staging-body clearance witness.
 * GP7 half-extent along the rail (1.875) is observed from the runtime
 * exact:gp7 baked mesh log (2026-10-13 21:45:57, chunk 15).
 * Coach estimated half-body (1.62) follows PassengerCoachRenderer's
 * accepted body-scale comment. These values are an approximate witness,
 * NOT collision shapes, not an authorization to move or to change coupling.
 * Refuse on uncertainty. No Minecraft dependencies, no writes.
 */
public final class Dev51BVisualClearanceR27c {
    public static final double GP7_RAIL_HALF_EXTENT = 1.875D;
    public static final double COACH_BODY_HALF_EXTENT_ESTIMATE = 1.62D;
    public static final double MIN_VISIBLE_GAP = 0.12D;
    private static final double Z_TOLERANCE = 0.35D;
    private Dev51BVisualClearanceR27c() {}
    public record Observation(boolean clear, double centerGap, double estimatedSeam, String reason) {}
    public static Observation observe(double gx,double gz,double cx,double cz,float yaw,
                                      float trackedYaw,float coachYaw) {
        if (!Double.isFinite(gx)||!Double.isFinite(gz)||!Double.isFinite(cx)||!Double.isFinite(cz)
                || !Float.isFinite(yaw)||!Float.isFinite(trackedYaw)||!Float.isFinite(coachYaw))
            return new Observation(false,Double.NaN,Double.NaN,"INVALID_POSE_OR_COORDS: MOVEMENT BLOCKED");
        double gap=Math.hypot(gx-cx,gz-cz);
        double seam=gap-GP7_RAIL_HALF_EXTENT-COACH_BODY_HALF_EXTENT_ESTIMATE;
        // Only axis-aligned E/W, GP7 ahead of coach, exact heading proven
        // by predecessor R27b in the shared manualDepartureGate. Never
        // claim complete visual clearance for unsupported orientations.
        if (gx <= cx || Math.abs(gz-cz)>Z_TOLERANCE)
            return new Observation(false,gap,seam,"NON_EASTBOUND_ALIGNMENT: MOVEMENT BLOCKED");
        if (Math.abs(Math.cos(Math.toRadians(yaw)))>0.20D
                || Math.abs(Math.cos(Math.toRadians(trackedYaw)))>0.20D
                || Math.abs(Math.cos(Math.toRadians(coachYaw)))>0.20D)
            return new Observation(false,gap,seam,"UNVERIFIED_ORIENTATION: MOVEMENT BLOCKED");
        if (!(seam >= MIN_VISIBLE_GAP))
            return new Observation(false,gap,seam,"ESTIMATED_MODEL_OVERLAP_OR_NO_MARGIN: MOVEMENT BLOCKED");
        // This is an observational geometric threshold, not a train-driving
        // approval. All other safety gates remain mandatory.
        return new Observation(true,gap,seam,"ESTIMATED_MODEL_GAP_ONLY: NOT DRIVER_CERTIFIED");
    }
    public static String blocker(double gx,double gz,double cx,double cz,float yaw,
                                 float trackedYaw,float coachYaw) {
        Observation o=observe(gx,gz,cx,cz,yaw,trackedYaw,coachYaw);
        return o.clear()?null:"R27c " + o.reason();
    }
}
