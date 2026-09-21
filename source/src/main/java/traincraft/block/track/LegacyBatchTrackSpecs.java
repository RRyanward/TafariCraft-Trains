package traincraft.block.track;

import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 9.3a batched original-track definitions.
 *
 * The frozen Step 9.2b medium curve remains on its dedicated proven class.
 * These definitions cover the other captured classic families in one build.
 */
public final class LegacyBatchTrackSpecs {
    private LegacyBatchTrackSpecs() {}

    // 90-degree large-radius curves.
    public static final LegacyBatchTrackSpec CURVE_BIG =
            LegacyBatchTrackSpec.oneRoute("track_curve_big", quarter(4, -1));
    public static final LegacyBatchTrackSpec CURVE_VERY_BIG =
            LegacyBatchTrackSpec.oneRoute("track_curve_very_big", quarter(9, -1));
    public static final LegacyBatchTrackSpec CURVE_SUPER_BIG =
            LegacyBatchTrackSpec.oneRoute("track_curve_super_big", quarter(15, -1));
    public static final LegacyBatchTrackSpec CURVE_29X =
            LegacyBatchTrackSpec.oneRoute("track_curve_29x", quarter(28, -1));
    public static final LegacyBatchTrackSpec CURVE_32X =
            LegacyBatchTrackSpec.oneRoute("track_curve_32x", quarter(31, -1));

    // 45-degree curve family.
    public static final LegacyBatchTrackSpec CURVE_45_MEDIUM_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_medium_right", offsetCurve(1, 2));
    public static final LegacyBatchTrackSpec CURVE_45_MEDIUM_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_medium_left", offsetCurve(-1, 2));
    public static final LegacyBatchTrackSpec CURVE_45_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_large_right", offsetCurve(2, 5));
    public static final LegacyBatchTrackSpec CURVE_45_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_large_left", offsetCurve(-2, 5));
    public static final LegacyBatchTrackSpec CURVE_45_VERY_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_very_large_right", offsetCurve(3, 7));
    public static final LegacyBatchTrackSpec CURVE_45_VERY_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_very_large_left", offsetCurve(-3, 7));
    public static final LegacyBatchTrackSpec CURVE_45_SUPER_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_super_large_right", offsetCurve(4, 10));
    public static final LegacyBatchTrackSpec CURVE_45_SUPER_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_curve_45_super_large_left", offsetCurve(-4, 10));

    // Parallel/S curves.
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_SMALL_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_small_right", offsetCurve(1, 7));
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_SMALL_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_small_left", offsetCurve(-1, 7));
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_MEDIUM_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_medium_right", offsetCurve(2, 11));
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_MEDIUM_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_medium_left", offsetCurve(-2, 11));
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_large_right", offsetCurve(3, 15));
    public static final LegacyBatchTrackSpec PARALLEL_CURVE_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_parallel_curve_large_left", offsetCurve(-3, 15));

    // Original straight slope family. Step 9.3b-t1 keeps the hidden vanilla
    // one-block handoff at the far end; locomotive motion is projected onto the
    // classic mesh's continuous one-block rise across the complete footprint.
    // Curved slopes intentionally keep the proven r3 guide until their dedicated
    // continuous-path pass.
    public static final LegacyBatchTrackSpec SLOPE_SMALL =
            LegacyBatchTrackSpec.oneRoute("track_slope_small", slope(6));
    public static final LegacyBatchTrackSpec SLOPE_LONG =
            LegacyBatchTrackSpec.oneRoute("track_slope_long", slope(12));
    public static final LegacyBatchTrackSpec SLOPE_VERY_LONG =
            LegacyBatchTrackSpec.oneRoute("track_slope_very_long", slope(18));

    // Curved slopes.
    public static final LegacyBatchTrackSpec SLOPE_CURVE_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_large_right", gradualRiseGuide(quarter(4, 1)));
    public static final LegacyBatchTrackSpec SLOPE_CURVE_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_large_left", gradualRiseGuide(quarter(4, -1)));
    public static final LegacyBatchTrackSpec SLOPE_CURVE_VERY_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_very_large_right", gradualRiseGuide(quarter(8, 1)));
    public static final LegacyBatchTrackSpec SLOPE_CURVE_VERY_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_very_large_left", gradualRiseGuide(quarter(8, -1)));
    public static final LegacyBatchTrackSpec SLOPE_CURVE_SUPER_LARGE_RIGHT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_super_large_right", gradualRiseGuide(quarter(15, 1)));
    public static final LegacyBatchTrackSpec SLOPE_CURVE_SUPER_LARGE_LEFT =
            LegacyBatchTrackSpec.oneRoute("track_slope_curve_super_large_left", gradualRiseGuide(quarter(15, -1)));

    // Crossings. Dynamic routing selects whichever canonical path best aligns
    // with the cart's incoming horizontal motion.
    public static final LegacyBatchTrackSpec CROSSING_X =
            LegacyBatchTrackSpec.singleDynamic("track_crossing_x",
                    RailShape.NORTH_SOUTH, RailShape.EAST_WEST);
    public static final LegacyBatchTrackSpec DIAMOND_CROSSING =
            crossing("track_diamond_crossing", 4);
    public static final LegacyBatchTrackSpec DIAMOND_CROSSING_LEFT =
            crossing("track_diamond_crossing_left", 4);
    public static final LegacyBatchTrackSpec DOUBLE_DIAMOND_CROSSING =
            crossing("track_double_diamond_crossing", 4);
    // TC4.5 DIAGONAL_TWO_WAYS_CROSSING: one centered owner/model rail plus
    // eight linked Small-Diagonal proxy/gag rails in a 3x3 footprint.
    public static final LegacyBatchTrackSpec DIAGONAL_CROSSING =
            LegacyBatchTrackSpec.originalDiagonalCrossing("track_diagonal_crossing");
    public static final LegacyBatchTrackSpec DIAGONAL_TWO_WAYS_CROSSING =
            crossing("track_diagonal_two_ways_crossing", 2);
    public static final LegacyBatchTrackSpec DIAGONAL_FOUR_WAYS_CROSSING =
            crossing("track_diagonal_four_ways_crossing", 2);
    public static final LegacyBatchTrackSpec UNIVERSAL_CROSSING =
            crossing("track_universal_crossing", 4);

    // Toggleable switches/turnouts: inactive = straight route,
    // active = diverging route. Empty-hand right click toggles the assembly.
    public static final LegacyBatchTrackSpec SWITCH_SMALL_RIGHT =
            turnout("track_switch_small_right", 2, 1);
    public static final LegacyBatchTrackSpec SWITCH_SMALL_LEFT =
            turnout("track_switch_small_left", 2, -1);
    public static final LegacyBatchTrackSpec SWITCH_MEDIUM_RIGHT =
            turnout("track_switch_medium_right", 4, 1);
    public static final LegacyBatchTrackSpec SWITCH_MEDIUM_LEFT =
            turnout("track_switch_medium_left", 4, -1);
    public static final LegacyBatchTrackSpec SWITCH_LARGE_RIGHT =
            turnout("track_switch_large_right", 4, 1);
    public static final LegacyBatchTrackSpec SWITCH_LARGE_LEFT =
            turnout("track_switch_large_left", 4, -1);
    public static final LegacyBatchTrackSpec SWITCH_VERY_LARGE_RIGHT =
            turnout("track_switch_very_large_right", 9, 1);
    public static final LegacyBatchTrackSpec SWITCH_VERY_LARGE_LEFT =
            turnout("track_switch_very_large_left", 9, -1);
    public static final LegacyBatchTrackSpec SWITCH_45_MEDIUM_RIGHT =
            parallelTurnout("track_switch_45_medium_right", 1, 2);
    public static final LegacyBatchTrackSpec SWITCH_45_MEDIUM_LEFT =
            parallelTurnout("track_switch_45_medium_left", -1, 2);
    public static final LegacyBatchTrackSpec SWITCH_PARALLEL_RIGHT =
            parallelTurnout("track_switch_parallel_right", 3, 8);
    public static final LegacyBatchTrackSpec SWITCH_PARALLEL_LEFT =
            parallelTurnout("track_switch_parallel_left", -3, 8);

    // Diagonal straight bridge pieces used with 45-degree/crossing geometry.
    public static final LegacyBatchTrackSpec DIAGONAL_STRAIGHT_SMALL =
            LegacyBatchTrackSpec.originalSmallDiagonal("track_diagonal_straight_small");
    public static final LegacyBatchTrackSpec DIAGONAL_STRAIGHT_MEDIUM =
            LegacyBatchTrackSpec.oneRoute("track_diagonal_straight_medium", offsetCurve(3, 3));

    private static LegacyBatchTrackSpec crossing(String id, int size) {
        int n = size - 1;
        List<LegacyBatchTrackSpec.Point> a =
                diagonalStair(0, 0, n, -n, true);
        List<LegacyBatchTrackSpec.Point> b =
                diagonalStair(n, 0, 0, -n, true);
        return LegacyBatchTrackSpec.crossingRoutes(id, a, b);
    }

    private static LegacyBatchTrackSpec turnout(String id, int run, int branchSign) {
        List<LegacyBatchTrackSpec.Point> straight = straightNorth(run + 1);
        List<LegacyBatchTrackSpec.Point> branch = quarter(run, branchSign);
        return LegacyBatchTrackSpec.switchRoutes(id, straight, branch);
    }

    private static LegacyBatchTrackSpec parallelTurnout(String id, int lateral, int run) {
        List<LegacyBatchTrackSpec.Point> straight = straightNorth(run + 1);
        List<LegacyBatchTrackSpec.Point> branch = offsetCurve(lateral, run);
        return LegacyBatchTrackSpec.switchRoutes(id, straight, branch);
    }

    /** r is endpoint displacement in both horizontal axes. */
    private static List<LegacyBatchTrackSpec.Point> quarter(int r, int xSign) {
        if (r < 1) {
            throw new IllegalArgumentException("quarter radius must be >= 1");
        }
        if (xSign != -1 && xSign != 1) {
            throw new IllegalArgumentException("xSign must be +/-1");
        }

        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>();
        int x = 0;
        int z = 0;
        int targetX = xSign * r;
        int targetZ = -r;
        out.add(p(x, 0, z));

        while (x != targetX || z != targetZ) {
            if (x == targetX) {
                z--;
            } else if (z == targetZ) {
                x += xSign;
            } else {
                int candidateX = x + xSign;
                int candidateZ = z - 1;

                double errX = circleError(candidateX, z, targetX, r);
                double errZ = circleError(x, candidateZ, targetX, r);

                if (Math.abs(errZ) <= Math.abs(errX)) {
                    z = candidateZ;
                } else {
                    x = candidateX;
                }
            }
            out.add(p(x, 0, z));
        }

        return out;
    }

    private static double circleError(int x, int z, int centerX, int radius) {
        double dx = x - centerX;
        return dx * dx + z * z - radius * radius;
    }

    /**
     * Smooth lateral offset while keeping the first and final guide step
     * longitudinal so adjacent pieces see a clean external connector.
     */
    private static List<LegacyBatchTrackSpec.Point> offsetCurve(int lateral, int forward) {
        if (forward < 1) {
            throw new IllegalArgumentException("forward must be >= 1");
        }

        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>();
        int x = 0;
        int z = 0;
        out.add(p(0, 0, 0));

        if (forward == 1) {
            z--;
            out.add(p(x, 0, z));
            while (x != lateral) {
                x += Integer.signum(lateral - x);
                out.add(p(x, 0, z));
            }
            return out;
        }

        for (int i = 1; i <= forward; i++) {
            z--;
            out.add(p(x, 0, z));

            if (i >= forward) {
                continue;
            }

            double t = (double) i / (double) (forward - 1);
            double smooth = t * t * (3.0D - 2.0D * t);
            int targetX = (int) Math.round(lateral * smooth);

            while (x != targetX) {
                x += Integer.signum(targetX - x);
                out.add(p(x, 0, z));
            }
        }

        while (x != lateral) {
            x += Integer.signum(lateral - x);
            // Complete any rounding remainder BEFORE adding a final
            // longitudinal tail cell.
            out.add(p(x, 0, z));
        }

        // If rounding forced a lateral move after the nominal final forward
        // step, add one more longitudinal cell so the exposed endpoint tangent
        // remains north/south.
        if (out.size() >= 2) {
            LegacyBatchTrackSpec.Point last = out.get(out.size() - 1);
            LegacyBatchTrackSpec.Point prev = out.get(out.size() - 2);
            if (last.x != prev.x) {
                z--;
                out.add(p(x, 0, z));
            }
        }

        return out;
    }

    private static List<LegacyBatchTrackSpec.Point> slope(int blockCount) {
        if (blockCount < 2) {
            throw new IllegalArgumentException("slope blockCount must be >= 2");
        }

        List<LegacyBatchTrackSpec.Point> flat = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            flat.add(p(0, 0, -i));
        }
        return straightSlopeRiseGuide(flat);
    }

    /**
     * Step 9.3b-t1 straight-slope guide. Vanilla RailShape cannot encode a
     * fractional rise across many cells, so keep the hidden guide on the lower
     * level until the final handoff. SmallSteamLocomotive is then free to follow
     * the classic mesh continuously without losing rail detection halfway up.
     */
    private static List<LegacyBatchTrackSpec.Point> straightSlopeRiseGuide(
            List<LegacyBatchTrackSpec.Point> flat) {
        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>(flat.size());
        for (int i = 0; i < flat.size(); i++) {
            LegacyBatchTrackSpec.Point point = flat.get(i);
            out.add(i == flat.size() - 1 ? point.withY(1) : point.withY(0));
        }
        return out;
    }

    /**
     * Vanilla RailShape can only rise a whole block inside one guide cell,
     * while the original Traincraft slope OBJ rises gradually over the complete
     * footprint. Put that one unavoidable vanilla ascent near the midpoint so
     * the locomotive stays substantially closer to the visible classic mesh
     * instead of remaining below it until the final block.
     */
    private static List<LegacyBatchTrackSpec.Point> gradualRiseGuide(
            List<LegacyBatchTrackSpec.Point> flat) {
        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>(flat.size());

        // An ASCENDING_* RailShape cannot also be a horizontal corner. Pick a
        // straight-through guide cell as the lower half of the one-block rise,
        // as close to the midpoint as the staircase allows. On a tie, prefer
        // the later cell so rolling stock does not float above the classic
        // gradual mesh for most of the curve.
        int target = Math.max(1, (flat.size() - 2) / 2);
        int ascentCell = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 1; i + 1 < flat.size(); i++) {
            LegacyBatchTrackSpec.Point prev = flat.get(i - 1);
            LegacyBatchTrackSpec.Point cur = flat.get(i);
            LegacyBatchTrackSpec.Point next = flat.get(i + 1);
            int inX = cur.x - prev.x;
            int inZ = cur.z - prev.z;
            int outX = next.x - cur.x;
            int outZ = next.z - cur.z;
            boolean straightThrough = inX == outX && inZ == outZ;
            if (!straightThrough) {
                continue;
            }

            int distance = Math.abs(i - target);
            if (distance < bestDistance || (distance == bestDistance && i > ascentCell)) {
                ascentCell = i;
                bestDistance = distance;
            }
        }

        // Straight slopes always have a safe middle cell. Very compact curved
        // shapes may not; in that unlikely case use the final approach rather
        // than creating an ascending corner.
        if (ascentCell < 0) {
            ascentCell = Math.max(0, flat.size() - 2);
        }
        int raisedFrom = Math.min(flat.size() - 1, ascentCell + 1);

        for (int i = 0; i < flat.size(); i++) {
            LegacyBatchTrackSpec.Point point = flat.get(i);
            out.add(i >= raisedFrom ? point.withY(1) : point.withY(0));
        }
        return out;
    }

    private static List<LegacyBatchTrackSpec.Point> straightNorth(int blockCount) {
        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            out.add(p(0, 0, -i));
        }
        return out;
    }

    private static List<LegacyBatchTrackSpec.Point> diagonalStair(
            int startX, int startZ, int endX, int endZ, boolean zFirst) {
        List<LegacyBatchTrackSpec.Point> out = new ArrayList<>();
        int x = startX;
        int z = startZ;
        out.add(p(x, 0, z));

        boolean doZ = zFirst;
        while (x != endX || z != endZ) {
            boolean canX = x != endX;
            boolean canZ = z != endZ;

            if (canX && canZ) {
                if (doZ) {
                    z += Integer.signum(endZ - z);
                } else {
                    x += Integer.signum(endX - x);
                }
                doZ = !doZ;
            } else if (canZ) {
                z += Integer.signum(endZ - z);
            } else {
                x += Integer.signum(endX - x);
            }

            out.add(p(x, 0, z));
        }

        return out;
    }

    private static LegacyBatchTrackSpec.Point p(int x, int y, int z) {
        return new LegacyBatchTrackSpec.Point(x, y, z);
    }
}
