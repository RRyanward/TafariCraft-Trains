package traincraft.worldgen;

/** DEV51B R27b: Pure-Java, fail-closed GP7 eastbound orientation witness.
 * The GP7's visible front follows vanilla Minecraft yaw: -sin(yaw) for X,
 * +cos(yaw) for Z. Position +90 is west-facing, -90 is east-facing.
 * No world access, speed changes, path routing or driver commands.
 */
public final class Dev51BStagingFacingR27b {
    private Dev51BStagingFacingR27b() {}
    public static final float EASTBOUND_STAGE_YAW = -90.0F;
    private static boolean eastbound(float yaw) {
        if (!Float.isFinite(yaw)) return false;
        double radians = Math.toRadians(yaw);
        return (-Math.sin(radians) >= 0.94D) && (Math.abs(Math.cos(radians)) <= 0.35D);
    }
    /** Return a reason to block ALL launch paths, null only if correctly posed. */
    public static String blocker(double locomotiveX, double coachX,
                                 float actualYaw, float trainFacingYaw) {
        if (!Double.isFinite(locomotiveX) || !Double.isFinite(coachX)
                || locomotiveX <= coachX + 1.95D) {
            return "R27b heading guard: coach is not safely WEST of GP7; NO MOVEMENT";
        }
        if (!eastbound(actualYaw) || !eastbound(trainFacingYaw)) {
            return "R27b heading guard: GP7 is not nose-EAST (visualYaw="
                    + actualYaw + ", trackedYaw=" + trainFacingYaw
                    + "). Correctly RESTAGE in disposable copy; NO MOVEMENT";
        }
        return null;
    }
}
