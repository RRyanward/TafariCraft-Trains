package traincraft.worldgen;

/**
 * DEV51B R27d: dedicated GP7 + Blue Coach staging gap policy.
 * Pure Java: decision only. Actual coupler link and train motion remain
 * owned by the frozen SteamTender/CouplingManager implementation.
 * Nominal existing center target 3.015 + 0.780 = 3.795 blocks.
 * GP7 half extent 1.875, rendered coach body 1.620: estimated gap 0.300.
 * This margin is deliberately conservative and must be visually accepted.
 */
public final class Dev51BStageSpacingR27d {
    public static final String SCOPE = "GP7_BLUE_EASTBOUND_V1";
    public static final double EXTRA_STAGED_GAP = 0.780D;
    public static final double BASE_CENTER_GAP_WITNESS = 3.015D;
    public static final double TARGET_CENTER_GAP = BASE_CENTER_GAP_WITNESS + EXTRA_STAGED_GAP;
    public static final double MIN_CENTER_GAP = 3.615D;
    public static final double MAX_CENTER_GAP = 3.970D;
    private Dev51BStageSpacingR27d() {}

    public static boolean scoped(String coachScope, String leadScope,
                                 String coachStage, String leadStage,
                                 String coachRole, String leadRole,
                                 long coachStation, long leadStation,
                                 boolean bothHaveStation, boolean actualGp7,
                                 boolean connected, boolean sameLevel) {
        return SCOPE.equals(coachScope) && SCOPE.equals(leadScope)
                && "DEV50c4".equals(coachStage) && "DEV50c4".equals(leadStage)
                && "coach_1".equals(coachRole) && "locomotive".equals(leadRole)
                && bothHaveStation && coachStation == leadStation
                && actualGp7 && connected && sameLevel;
    }

    public static double touchTarget(double baseTarget, boolean scoped) {
        if (!Double.isFinite(baseTarget) || baseTarget <= 0) return baseTarget;
        return scoped ? baseTarget + EXTRA_STAGED_GAP : baseTarget;
    }

    /** Staging-only fail-closed envelope, not train movement approval. */
    public static boolean acceptFinalPosition(double gp7x, double coachx,
                                              float gp7yaw, float coachyaw) {
        if (!Double.isFinite(gp7x) || !Double.isFinite(coachx)
                || !Float.isFinite(gp7yaw) || !Float.isFinite(coachyaw)) return false;
        double centerGap = gp7x - coachx;
        // Preserve the R27 +6.2 lead-room / 0.75-block test.
        return centerGap >= MIN_CENTER_GAP && centerGap <= MAX_CENTER_GAP
            && Math.abs(Math.sin(Math.toRadians(gp7yaw)) + 1.0D) < 0.12D
            && Math.abs(Math.sin(Math.toRadians(coachyaw)) + 1.0D) < 0.12D;
    }
}
