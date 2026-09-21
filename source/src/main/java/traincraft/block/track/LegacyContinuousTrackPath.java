package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Step 9.3d-t10-r2 runtime path adapter for classic Traincraft pieces whose visible
 * geometry cannot be represented by vanilla RailShape alone.
 *
 * t5 preserves the accepted r7e placement ownership, r7b rail-to-rail visual alignment, motion-gated
 * virtual handoff, t3 continuous curve/slope work and r7 virtual endpoint
 * placement. It replaces the r7c nearest-guide recovery dependency with a
 * traversal-stable diagonal route direction, keeps progress advancing by real
 * horizontal speed instead of a Manhattan guide projection, and hands the far
 * crossing endpoint directly to snapped diagonal straight pieces. It does not
 * retune locomotive acceleration, braking, coupling, derailment or speed caps.
 *
 * The original t3 foundation replaced t2's
 * hard-coded diagonal-crossing projection with a generalized diagonal route
 * foundation. The diagonal centerline is derived from each installed
 * LegacyBatchTrackSpec footprint, route identity is latched for one traversal,
 * and progress is advanced from authoritative pre-rail motion instead of being
 * re-derived from vanilla's Manhattan guide position every tick.
 *
 * Covered diagonal families:
 * - Diamond / Double Diamond / Diagonal / Two-Way / Four-Way / Universal crossings;
 * - the dedicated diagonal straight bridge pieces;
 * - handoff capture from the 45-degree curve family and active 45-degree
 *   medium switches so an adjacent diagonal crossing can take ownership before
 *   vanilla snaps to the crossing's staircase endpoint.
 *
 * The hidden guide rails, specs, placement, switch toggles, OBJ resources and
 * Dedicated RC remain untouched.
 */
public final class LegacyContinuousTrackPath {
    private static final double RAIL_SURFACE_Y = 0.0625D;
    private static final double MOTION_EPSILON = 1.0E-7D;
    private static final double POWERED_SPEED_RETAIN = 0.995D;
    private static final double ENDPOINT_RELEASE_DISTANCE = 0.10D;
    private static final double NEARBY_CONTEXT_MAX_DISTANCE_SQ = 2.25D;

    // Step 9.3c-t3 diagonal-route handoff/session tuning.
    private static final double LINEAR_ENDPOINT_EXTENSION = 1.50D;
    private static final double LINEAR_INITIAL_CAPTURE_MAX_PERP = 0.90D;
    private static final double LINEAR_INITIAL_LATERAL_STEP = 0.18D;
    private static final double LINEAR_PROGRESS_CORRECTION_LIMIT = 0.45D;
    private static final double FEEDER_HANDOFF_DISTANCE = 1.35D;
    // Step 9.3e-t5-r8a: the first Small placed outward from a crossing-owned
    // visual-root Small is one cardinal cell off the ordinary shared-logical
    // endpoint. Only arm that bridge very near the active Small endpoint.
    private static final double SMALL_CROSSING_BRIDGE_HANDOFF_DISTANCE = 0.22D;
    private static final double FEEDER_TO_LINEAR_MAX_PERP = 0.60D;
    private static final long LINEAR_SESSION_MAX_TICK_GAP = 5L;

    // Step 9.3c-t4-r7d: while a diagonal route session is active, keep the
    // selected travel sign unless the driver actually commands a reversal and
    // the locomotive has slowed to a near-stop. This prevents a hidden cardinal
    // guide cell from turning one continuous 45-degree route into a bounce.
    private static final double LINEAR_DIRECTION_REVERSAL_SPEED = 0.012D;
    private static final double LINEAR_ENDPOINT_HOLD_INSET = 0.04D;

    // Step 9.3c-t4-r7c: r7b aligned the rendered crossing to the switch, which
    // means the selected visible diagonal can be more than one block away from
    // the legacy hidden staircase guide before reaching the far rail head. A
    // live t3 route session already owns the route identity and progress, so
    // recover that exact assembly by its latched context key instead of letting
    // vanilla drop the cart to rail=null mid-crossing. This search is used only
    // when no direct rail owns the cart and only for a recent live session.
    private static final int LATCHED_LINEAR_CONTEXT_HORIZONTAL_RADIUS = 3;
    private static final int LATCHED_LINEAR_CONTEXT_VERTICAL_RADIUS = 1;

    private static final String LINEAR_CONTEXT_KEY = "tc_cont_diag_context";
    private static final String LINEAR_ROUTE_KEY = "tc_cont_diag_route";
    private static final String LINEAR_PROGRESS_KEY = "tc_cont_diag_progress";
    private static final String LINEAR_TICK_KEY = "tc_cont_diag_tick";
    private static final String LINEAR_SIGN_KEY = "tc_cont_diag_sign";

    // STEP_9_3E_T1_R1_STRICT_LASTTRACK_CROSSING_CONTINUITY
    // Original Traincraft's DIAGONAL_CROSSING never became a new movement
    // owner, so its internal gag/proxy cells could not flip the body orientation
    // or tug progress toward a different guide-cell center. Preserve the
    // locomotive's body-side relative to the selected diagonal for the whole
    // crossing session, including a genuine forward<->reverse command.
    private static final String LINEAR_BODY_SIGN_KEY = "tc_cont_diag_body_sign";

    // Step 9.3c-t4-r7b: a connector can be logically adjacent even when the
    // legacy crossing guide part lives one cell away from the rendered endpoint.
    // The placement layer records that mismatch in LegacyBatchTrackSpec endpoint
    // metadata. These short-lived keys carry the exact route selected by that
    // topology into the continuous diagonal sampler without moving the model or
    // changing the hidden guide footprint.
    private static final String LINEAR_HANDOFF_CONTEXT_KEY = "tc_cont_diag_handoff_context";
    private static final String LINEAR_HANDOFF_ROUTE_KEY = "tc_cont_diag_handoff_route";
    private static final String LINEAR_HANDOFF_PROGRESS_KEY = "tc_cont_diag_handoff_progress";
    private static final String LINEAR_HANDOFF_TICK_KEY = "tc_cont_diag_handoff_tick";
    private static final long LINEAR_HANDOFF_MAX_TICK_GAP = 5L;

    // Step 9.3e-t5-r8d: r8c removed the cardinal gap, but flight-recorder
    // evidence still showed a one-time along-route replay on the first tick
    // after an ordinary Small->Small atomic ownership transfer. Keep a tiny
    // next-context marker so that first same-session tick seeds progress from
    // the cart's real projected world position instead of advancing stored
    // progress a second time.
    private static final String LINEAR_ATOMIC_SETTLE_CONTEXT_KEY =
            "tc_cont_diag_atomic_settle_context";
    private static final String LINEAR_ATOMIC_SETTLE_TICK_KEY =
            "tc_cont_diag_atomic_settle_tick";
    private static final long LINEAR_ATOMIC_SETTLE_MAX_TICK_GAP = 2L;

    // Runtime evidence from r7a showed a zero-speed cart could be captured by
    // the virtual crossing route one tick after placement. Handoffs are now
    // armed only after real horizontal motion exists and, for an active 45°
    // switch, only while that motion is toward the diverging endpoint.
    private static final double VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED = 1.0E-4D;

    // Step 9.3c-t5: old Traincraft did not orient a long vehicle from one rail
    // cell. It followed the track at two bogie locations and derived the body
    // pose from the chord between them. Keep that idea virtual here: movement
    // still uses the existing authoritative centerline, while rendering/facing
    // samples two points on the SAME assembly-owned path.
    private static final double DEFAULT_VIRTUAL_BOGIE_SPACING = 1.50D;
    private static final double MIN_VIRTUAL_BOGIE_SPACING = 0.50D;
    private static final double MAX_VIRTUAL_BOGIE_SPACING = 6.00D;

    // Step 9.3c-t5-r4: visual-only endpoint straddle. These values are used
    // exclusively by the read-only virtual-bogie pose sampler. They never
    // transfer movement ownership from vanilla straight rail to a curve.
    private static final double POSE_ENDPOINT_EXTENSION = 0.90D;
    private static final int POSE_CONTEXT_SEARCH_RADIUS = 2;
    private static final double POSE_ENDPOINT_MAX_PERP = 0.40D;

    // Step 9.3c-t5-r2: the 90-degree quarter curve keeps one authoritative
    // arc-distance progress value for the active traversal. Runtime t5-r1 proof
    // showed that re-projecting progress from vanilla's post-rail world position
    // could visually stall through the middle of the arc while velocity kept
    // rising, followed by a catch-up jump. Advance progress by real horizontal
    // speed instead, matching the already-stable continuous diagonal strategy.
    private static final long CURVE_SESSION_MAX_TICK_GAP = 5L;
    private static final double CURVE_PROGRESS_CORRECTION_LIMIT = 0.20D;

    // Step 9.3c-t5-r6: mirrored forward START + preserved r5 reverse END entry bridges. Runtime r4/r5
    // proved each direction can first acquire the Big Curve guide cell roughly
    // half a block before the mathematical arc endpoint. Preserve that real
    // already-owned in-cell position and consume it along the exact endpoint
    // tangent before entering the circle. Neither bridge captures normal rail
    // early: r5 handles reverse at the END; r6 mirrors it for forward at START.
    private static final double CURVE_FORWARD_ENTRY_TANGENT_EXTENSION = 0.75D;
    private static final double CURVE_FORWARD_ENTRY_MAX_PERP = 0.20D;
    private static final double CURVE_REVERSE_ENTRY_TANGENT_EXTENSION = 0.75D;
    private static final double CURVE_REVERSE_ENTRY_MAX_PERP = 0.20D;
    private static final String CURVE_CONTEXT_KEY = "tc_cont_curve_context";
    private static final String CURVE_PROGRESS_KEY = "tc_cont_curve_progress";
    private static final String CURVE_TICK_KEY = "tc_cont_curve_tick";

    // STEP_9_3D_T10_ORIGINAL_TRAINCRAFT_ROOT_OWNED_45_DIAGONAL_MOVEMENT
    // STEP_9_3D_T10_R2_ORIGINAL_SHARED_GAG_ROOT_REACH
    // TC4.5 stores the mathematical 3.75-radius route on the switch-owned
    // TileTCRail and lets gag/proxy cells resolve back to that owner. Treat both
    // handed Medium 45 switches identically here; the sign of the lateral
    // offset is the only difference between Left and Right.
    private static final String TARGET_45_MEDIUM_LEFT_SWITCH =
            "track_switch_45_medium_left";
    private static final String TARGET_45_MEDIUM_RIGHT_SWITCH =
            "track_switch_45_medium_right";

    // STEP_9_3F_T1A_SMALL_SWITCH_TRUE_MAINLINE_TOPOLOGY
    //
    // t1 correctly made the Small switch root-owned, but the runtime screenshot
    // exposed a deeper topology mistake: the OUTSIDE rail is the mainline.
    // Canonical Small-switch endpoints are:
    //   p0 = (0,0)       diverging/inside exit
    //   p2 = (0,-2)      one end of the outside straight
    //   p5 = (+/-2,-2)   common/toe end on the outside straight
    //
    // Therefore:
    //   INACTIVE / STRAIGHT = p5 <-> p2
    //   ACTIVE / DIVERGING  = p5 <-> p0
    //
    // The branch remains the radius-2 quarter circle already proven by t1.
    // RIGHT uses +X and LEFT uses -X. The whole assembly still owns the route,
    // so individual Manhattan proxy RailShapes have zero steering authority.
    private static final String TARGET_SMALL_LEFT_SWITCH =
            "track_switch_small_left";
    private static final String TARGET_SMALL_RIGHT_SWITCH =
            "track_switch_small_right";
    private static final int SMALL_SWITCH_ROUTE_STRAIGHT = 0;
    private static final int SMALL_SWITCH_ROUTE_BRANCH = 1;
    private static final double SMALL_SWITCH_RADIUS = 2.0D;
    private static final double SMALL_SWITCH_ROUTE_LENGTH = 2.0D;
    private static final double SMALL_SWITCH_ROUTE_SELECTION_EPSILON = 0.08D;
    private static final long SMALL_SWITCH_SESSION_MAX_TICK_GAP = 5L;
    private static final String SMALL_SWITCH_CONTEXT_KEY =
            "tc_small_switch_context";
    private static final String SMALL_SWITCH_ROUTE_KEY =
            "tc_small_switch_route";
    private static final String SMALL_SWITCH_PROGRESS_KEY =
            "tc_small_switch_progress";
    private static final String SMALL_SWITCH_TICK_KEY =
            "tc_small_switch_tick";

    // STEP_9_3F_T1A_R8_SMALL_SWITCH_BODY_SIDE_CONTINUITY
    //
    // Route direction and locomotive nose direction are independent. A train
    // may reverse through the same switch without its body turning 180 degrees.
    // Latch which side of the selected route the locomotive nose occupied when
    // this switch session began and keep that body-side until ownership ends.
    private static final String SMALL_SWITCH_BODY_SIGN_KEY =
            "tc_small_switch_body_sign";

    // STEP_9_3F_T1A_R7_P2_ATOMIC_EXIT_HANDOFF
    // Root-owned Small-switch movement must remain authoritative until the cart
    // has actually crossed from p2 into the attached external rail block.
    private static final String SMALL_SWITCH_EXIT_HANDOFF_KEY =
            "tc_small_switch_exit_handoff";
    private static final double SMALL_SWITCH_EXIT_HANDOFF_CLEARANCE = 0.55D;

    // STEP_9_3F_T1A_R9_BIDIRECTIONAL_ENDPOINT_BRIDGE
    //
    // Runtime r8 traces proved the remaining "pop" is physical, not merely
    // visual: ownership can be acquired while the cart center is still roughly
    // half a block outside p2/p5, and the clamped route then teleports the cart
    // to the endpoint center. Keep a short root-owned tangent bridge on BOTH
    // ends so entry/exit remains continuous through the endpoint block.
    private static final double SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE = 0.75D;
    private static final double SMALL_SWITCH_ENDPOINT_BRIDGE_RELEASE = 0.60D;
    private static final double SMALL_SWITCH_ENDPOINT_BRIDGE_MAX_PERP = 0.20D;

    // STEP_9_3G_T4_R1_WHOLE_SWITCH_FAMILY_ROOT_OWNED_ROUTING
    //
    // Small is deliberately excluded: Step 9.3f-t1a-r11 remains the frozen
    // reference implementation. The remaining classic turnouts all use p0 as
    // their common/toe endpoint. Route identity is latched once per traversal;
    // compatibility guide RailShapes then have zero steering authority.
    private static final int FAMILY_SWITCH_ROUTE_STRAIGHT = 0;
    private static final int FAMILY_SWITCH_ROUTE_BRANCH = 1;
    private static final double FAMILY_SWITCH_ROUTE_SELECTION_EPSILON = 0.08D;
    private static final long FAMILY_SWITCH_SESSION_MAX_TICK_GAP = 5L;
    private static final String FAMILY_SWITCH_CONTEXT_KEY =
            "tc_family_switch_context";
    private static final String FAMILY_SWITCH_ROUTE_KEY =
            "tc_family_switch_route";
    private static final String FAMILY_SWITCH_PROGRESS_KEY =
            "tc_family_switch_progress";
    private static final String FAMILY_SWITCH_TICK_KEY =
            "tc_family_switch_tick";
    private static final String FAMILY_SWITCH_BODY_SIGN_KEY =
            "tc_family_switch_body_sign";
    private static final double FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE = 0.75D;
    private static final double FAMILY_SWITCH_ENDPOINT_BRIDGE_RELEASE = 0.60D;
    private static final double FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP = 0.24D;
    // STEP_9_3G_T4_R3_FRESH_ENTRY_TANGENT_GATE
    //
    // A physical turnout gag block may sit next to an unrelated perpendicular
    // rail. t4-r2 admitted every family gag cell unconditionally, so a cart
    // approaching the side of p4 could be rotated onto the turnout even though
    // that side is not one of the turnout's three connectors. Fresh ownership
    // must therefore be acquired only through a real external endpoint and only
    // when the incoming axis agrees with that endpoint tangent.
    private static final double FAMILY_SWITCH_FRESH_ENTRY_MIN_TANGENT_DOT = 0.72D;
    private static final int FAMILY_SWITCH_LATCH_SEARCH_RADIUS = 2;

    // STEP_9_3E_T1_ORIGINAL_DIAGONAL_CROSSING_LASTTRACK_OWNERSHIP
    // TC4.5 deliberately did NOT replace EntityBogie.lastTrack when the bogie
    // entered a DIAGONAL_CROSSING root. The incoming diagonal owner therefore
    // kept the bogie on the same 45-degree line all the way through the
    // crossing. The modern 12-cell compatibility footprint must do the same:
    // select one of the two diagonal routes once, ignore per-cell RailShape
    // steering, and release only at the opposite endpoint of THAT route.
    private static final String TARGET_DIAGONAL_CROSSING =
            "track_diagonal_crossing";

    // STEP_9_3D_T8_ORIGINAL_TRAINCRAFT_SHARED_GAG_PROXY_OWNERSHIP
    // Preserves Step 9.3d-t5 original 45-degree math.
    // TC4.5 ItemTCRail.mediumLeft45DegreeSwitch() stores a 3.75-block
    // curve radius. The curved route starts half a block after the switch root
    // and turns exactly 45 degrees before Traincraft's gag/proxy corridor hands
    // the bogie to a DIAGONAL rail. Keep those values explicit instead of
    // fitting a smaller circle through the modern Manhattan guide cells.
    private static final double ORIGINAL_TC45_SWITCH_RADIUS = 3.75D;
    private static final double ORIGINAL_TC45_SWITCH_LEAD = 0.50D;
    private static final double ORIGINAL_TC45_SWITCH_ANGLE = Math.PI * 0.25D;
    private static final double ORIGINAL_TC45_HANDOFF_MAX_PERP = 0.20D;
    private static final double ORIGINAL_TC45_HANDOFF_MIN_TANGENT_DOT = 0.995D;
    // t10-r1: keep the switch owner through the true arc endpoint and a very
    // short tangent overlap. TC4.5 changed TileTCRail ownership by topology; it
    // did not release 0.10 blocks early and let a cardinal guide move the bogie.
    private static final double ORIGINAL_TC45_FORWARD_EXIT_TANGENT_EXTENSION = 0.25D;
    // Step 9.3d-t10-r2: the original Medium-45 shared gag/proxy root is three
    // cardinal cells from the switch logical root for this family. t10-r1's
    // +/-2 source scan therefore could not rediscover the owning switch once
    // Minecraft entered the diagonal root cell. Keep the scan bounded to the
    // exact family reach; sharedRoot equality below remains authoritative.
    private static final int ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS = 3;

    // Step 9.3d-t10-r4: TC4.5 did not teleport a bogie from the final
    // 3.75-radius switch point onto an independently re-centered diagonal
    // centerline. moveOnTCDiagonal()/centerDiagonal() consumed the already-owned
    // gag-cell position and corrected it while travel continued. The modern
    // switch endpoint and diagonal root centerline are parallel but differ by
    // only a few hundredths of a block, so preserve the exact switch endpoint
    // and smoothly recenter over one block instead of snapping in one tick.
    private static final double ORIGINAL_TC45_DIAGONAL_RECENTER_LENGTH = 1.00D;

    // Step 9.3e-t1 removes the old r7 visual-route offsets for this crossing.
    // TC4.5 movement is owned by the incoming diagonal/lastTrack, so the two
    // mathematical crossing routes are the two true corner-to-corner diagonals.
    // The 12 modern guide cells remain footprint/render compatibility only.

    private LegacyContinuousTrackPath() {
    }

    private static final class Context {
        private final AbstractLegacyBatchRailBlock block;
        private final BlockState state;
        private final BlockPos railPos;
        private final BlockPos root;
        private final Direction facing;
        private final LegacyBatchTrackSpec spec;

        private Context(AbstractLegacyBatchRailBlock block,
                        BlockState state,
                        BlockPos railPos) {
            this.block = block;
            this.state = state;
            this.railPos = railPos;
            this.root = block.getAssemblyRoot(railPos, state);
            this.facing = block.getAssemblyFacing(state);
            this.spec = block.getSpec();
        }
    }

    private static final class CurveSample {
        private final Vec3 targetHorizontal;
        private final Vec3 tangent;
        private final double distanceFromStart;
        private final double distanceToEnd;

        private CurveSample(Vec3 targetHorizontal,
                            Vec3 tangent,
                            double distanceFromStart,
                            double distanceToEnd) {
            this.targetHorizontal = targetHorizontal;
            this.tangent = tangent;
            this.distanceFromStart = Math.max(0.0D, distanceFromStart);
            this.distanceToEnd = Math.max(0.0D, distanceToEnd);
        }
    }

    private static final class OffsetDefinition {
        private final double lateral;
        private final double forward;

        private OffsetDefinition(double lateral, double forward) {
            this.lateral = lateral;
            this.forward = forward;
        }
    }

    private static final class SmallSwitchSample {
        private final Vec3 targetHorizontal;
        private final Vec3 tangent;
        private final double progress;
        private final double length;
        private final int routeIndex;

        private SmallSwitchSample(Vec3 targetHorizontal,
                                  Vec3 tangent,
                                  double progress,
                                  double length,
                                  int routeIndex) {
            this.targetHorizontal = targetHorizontal;
            this.tangent = tangent;
            this.progress = progress;
            this.length = length;
            this.routeIndex = routeIndex;
        }
    }

    private static final class FamilySwitchSample {
        private final Vec3 targetHorizontal;
        private final Vec3 tangent;
        private final double progress;
        private final double length;
        private final int routeIndex;
        private final double progressPerBlock;

        private FamilySwitchSample(Vec3 targetHorizontal,
                                   Vec3 tangent,
                                   double progress,
                                   double length,
                                   int routeIndex,
                                   double progressPerBlock) {
            this.targetHorizontal = targetHorizontal;
            this.tangent = tangent;
            this.progress = progress;
            this.length = length;
            this.routeIndex = routeIndex;
            this.progressPerBlock = progressPerBlock;
        }
    }

    private static final class FamilySwitchEndpoints {
        private final LegacyBatchTrackSpec.Endpoint common;
        private final LegacyBatchTrackSpec.Endpoint straight;
        private final LegacyBatchTrackSpec.Endpoint branch;
        private final double straightX;
        private final double straightZ;
        private final double branchX;
        private final double branchZ;

        private FamilySwitchEndpoints(LegacyBatchTrackSpec.Endpoint common,
                                      LegacyBatchTrackSpec.Endpoint straight,
                                      LegacyBatchTrackSpec.Endpoint branch,
                                      double straightX,
                                      double straightZ,
                                      double branchX,
                                      double branchZ) {
            this.common = common;
            this.straight = straight;
            this.branch = branch;
            this.straightX = straightX;
            this.straightZ = straightZ;
            this.branchX = branchX;
            this.branchZ = branchZ;
        }
    }


    private static final class LinearRoute {
        private final Vec3 startHorizontal;
        private final Vec3 tangent;
        private final double length;
        private final int routeIndex;

        private LinearRoute(Vec3 startHorizontal,
                            Vec3 tangent,
                            double length,
                            int routeIndex) {
            this.startHorizontal = startHorizontal;
            this.tangent = tangent;
            this.length = length;
            this.routeIndex = routeIndex;
        }

        private double projectedProgress(double worldX, double worldZ) {
            return (worldX - startHorizontal.x) * tangent.x
                    + (worldZ - startHorizontal.z) * tangent.z;
        }

        private double perpendicularDistance(double worldX, double worldZ) {
            double progress = projectedProgress(worldX, worldZ);
            double targetX = startHorizontal.x + tangent.x * progress;
            double targetZ = startHorizontal.z + tangent.z * progress;
            return Math.hypot(worldX - targetX, worldZ - targetZ);
        }

        private Vec3 pointAt(double progress) {
            return new Vec3(
                    startHorizontal.x + tangent.x * progress,
                    startHorizontal.y,
                    startHorizontal.z + tangent.z * progress);
        }
    }

    private static final class LinearSample {
        private final LinearRoute route;
        private final Vec3 targetHorizontal;
        private final double progress;
        private final double travelSign;

        private LinearSample(LinearRoute route,
                             Vec3 targetHorizontal,
                             double progress,
                             double travelSign) {
            this.route = route;
            this.targetHorizontal = targetHorizontal;
            this.progress = progress;
            this.travelSign = travelSign;
        }
    }

    /**
     * Step 9.3c-t5 virtual two-bogie body pose.
     *
     * The points are sampled from one authoritative LegacyBatchTrackSpec assembly,
     * never from neighboring vanilla RailShape cells. The locomotive may use yaw
     * immediately while renderers can consume pitch/center later without changing
     * coupling or tractive-effort physics.
     */
    public static final class VirtualBogiePose {
        private final Vec3 front;
        private final Vec3 rear;
        private final Vec3 center;
        private final float yaw;
        private final float pitch;

        private VirtualBogiePose(Vec3 front,
                                 Vec3 rear,
                                 Vec3 center,
                                 float yaw,
                                 float pitch) {
            this.front = front;
            this.rear = rear;
            this.center = center;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Vec3 front() {
            return front;
        }

        public Vec3 rear() {
            return rear;
        }

        public Vec3 center() {
            return center;
        }

        public float yaw() {
            return yaw;
        }

        public float pitch() {
            return pitch;
        }
    }

    public static void apply(AbstractMinecart cart,
                             Vec3 preRailMotion,
                             boolean powered,
                             boolean serviceBrake,
                             boolean handBrake,
                             double curveMaxSpeed,
                             int requestedDirection,
                             float trainFacingYaw) {
        Context context = findContext(cart, preRailMotion);
        if (context == null) {
            return;
        }

        String id = context.spec.id();
        if (isTargetSmallSwitch(context)) {
            applySmallSwitch(cart, context, preRailMotion, powered,
                    serviceBrake, handBrake, curveMaxSpeed, true);
            return;
        }

        if (isTargetClassicFamilySwitch(context)) {
            applyFamilySwitch(cart, context, preRailMotion, powered,
                    serviceBrake, handBrake, curveMaxSpeed, true);
            return;
        }

        if (isContinuousCurve(context)) {
            applyCurve(cart, context, preRailMotion, powered,
                    serviceBrake, handBrake, curveMaxSpeed);
            return;
        }

        if (isContinuousLinearDiagonal(id)) {
            applyLinearDiagonal(cart, context, preRailMotion, powered,
                    serviceBrake, handBrake, curveMaxSpeed,
                    requestedDirection, trainFacingYaw);
            return;
        }

        if (isStraightSlope(id)) {
            applyStraightSlope(cart, context, preRailMotion, powered,
                    serviceBrake, handBrake);
        }
    }

    /**
     * Dedicated-server client smoothing for flat continuous curves and diagonal
     * routes. The server remains authoritative; the client mirrors the same
     * projected centerline between entity sync packets. Straight slopes remain
     * server-only to preserve their already accepted behavior.
     */
    public static void applyClientCurveVisual(AbstractMinecart cart,
                                              Vec3 preRailMotion,
                                              double curveMaxSpeed) {
        Context context = findContext(cart, preRailMotion);
        if (context == null) {
            return;
        }

        // Retain pre-rail horizontal speed on the client. The authoritative
        // server still owns throttle, braking and final velocity.
        if (isTargetSmallSwitch(context)) {
            applySmallSwitch(cart, context, preRailMotion, true,
                    false, false, curveMaxSpeed, true);
        } else if (isTargetClassicFamilySwitch(context)) {
            applyFamilySwitch(cart, context, preRailMotion, true,
                    false, false, curveMaxSpeed, true);
        } else if (isContinuousCurve(context)) {
            applyCurve(cart, context, preRailMotion, true,
                    false, false, curveMaxSpeed);
        } else if (isContinuousLinearDiagonal(context.spec.id())) {
            applyLinearDiagonal(cart, context, preRailMotion, true,
                    false, false, curveMaxSpeed, 0, cart.getYRot());
        }
    }

    /**
     * STEP_9_3G_T4_R8_CONSIST_SWITCH_ROUTE_OWNERSHIP
     *
     * Applies only switch-root routing to follower rolling stock. Each tender,
     * freight car, passenger coach and caboose receives its own route latch when
     * that vehicle reaches a switch. Common/toe entry obeys the current switch
     * state; trailing straight/branch entry follows the physical leg regardless
     * of state. The latch remains owned by that vehicle until external handoff.
     *
     * Follower speed/spacing remain coupling-owned: unlike locomotive routing,
     * this entry point does not restore pre-rail speed.
     */
    public static void applyFollowerSwitchRouting(AbstractMinecart cart,
                                                   Vec3 preRailMotion) {
        Context context = findContext(cart, preRailMotion);
        if (context == null) {
            return;
        }

        if (isTargetSmallSwitch(context)) {
            applySmallSwitch(cart, context, preRailMotion, false,
                    false, false, Double.POSITIVE_INFINITY, false);
            return;
        }

        if (isTargetClassicFamilySwitch(context)) {
            // STEP_9_3G_T4_R8H_MEDIUM_FOLLOWER_SPEED_RETENTION
            //
            // Standard Medium contains compatibility guide cells whose local
            // RailShape does not match the selected physical route (notably
            // p4=east_west while the proven straight route is north/south).
            // Preserve the coupling-selected pre-rail speed magnitude across
            // vanilla's guide-cell tick, then reapply it on the root-owned
            // tangent. Other family turnouts retain the frozen r8 behavior
            // until they are tested individually.
            boolean retainFollowerSpeed =
                    isTargetStandardMediumFamilySwitch(context);
            applyFamilySwitch(cart, context, preRailMotion, false,
                    false, false, Double.POSITIVE_INFINITY,
                    retainFollowerSpeed);
        }
    }

    /**
     * Returns the continuous track axis nearest the cart's current body facing.
     * The caller owns the final entity/body yaw field; returning null means use
     * the existing discrete RailShape facing unchanged.
     */
    public static Float continuousFacingYaw(AbstractMinecart cart, float currentBodyYaw) {
        VirtualBogiePose pose = virtualBogiePose(
                cart, currentBodyYaw, DEFAULT_VIRTUAL_BOGIE_SPACING);
        if (pose == null) {
            return null;
        }

        // STEP_9_3F_T1A_R10_SMALL_SWITCH_YAW_POLARITY_CONTINUITY
        //
        // r9 runtime proof:
        //   normal rail tick 58 : motion +Z, yaw -180
        //   Small p2 tick 60    : motion +Z, yaw   0
        //
        // Position/motion stayed continuous, but the body polarity flipped
        // exactly 180 degrees. Driver input is resolved from trainFacingYaw, so
        // holding the same command then opposes the still-forward motion,
        // decelerates it through zero and pulls the locomotive back.
        //
        // The Small-switch virtual-bogie chord has two geometrically equivalent
        // yaw solutions 180 degrees apart. Always choose the one closest to the
        // caller's previously authoritative body yaw. This preserves the
        // locomotive's physical nose through forward travel, reverse travel,
        // branch rotation and endpoint handoff.
        Context context = findPoseContext(cart, cart.getDeltaMovement());
        if (context != null && isTargetSmallSwitch(context)) {
            float first = Mth.wrapDegrees(pose.yaw());
            float opposite = Mth.wrapDegrees(first + 180.0F);

            float firstError =
                    Math.abs(Mth.wrapDegrees(currentBodyYaw - first));
            float oppositeError =
                    Math.abs(Mth.wrapDegrees(currentBodyYaw - opposite));

            float chosen =
                    firstError <= oppositeError ? first : opposite;

            // Repair the r8 session sign as well. The first ownership tick can
            // occur after vanilla has already changed the raw Minecart yaw, so
            // the old cart.getYRot()-based latch may contain the wrong polarity.
            // Re-latch from the continuity-preserved yaw used by Traincraft.
            Vec3 axis = pathTangentForPose(cart, context, chosen);
            if (axis != null
                    && axis.horizontalDistanceSqr() > MOTION_EPSILON) {
                cart.getPersistentData().putDouble(
                        SMALL_SWITCH_BODY_SIGN_KEY,
                        bodySignRelativeToTangent(chosen, axis));
            }

            return chosen;
        }

        // STEP_9_3G_T4_R1_WHOLE_FAMILY_YAW_POLARITY_CONTINUITY
        // Keep the exact r10/r11 Small branch above untouched. The same
        // geometrical ambiguity exists on the newly root-owned classic turnout
        // routes, so choose the 180-degree equivalent closest to the previously
        // authoritative locomotive body yaw and latch that body side separately.
        if (context != null && isTargetClassicFamilySwitch(context)) {
            float first = Mth.wrapDegrees(pose.yaw());
            float opposite = Mth.wrapDegrees(first + 180.0F);
            float firstError =
                    Math.abs(Mth.wrapDegrees(currentBodyYaw - first));
            float oppositeError =
                    Math.abs(Mth.wrapDegrees(currentBodyYaw - opposite));
            float chosen =
                    firstError <= oppositeError ? first : opposite;

            Vec3 axis = pathTangentForPose(cart, context, chosen);
            if (axis != null
                    && axis.horizontalDistanceSqr() > MOTION_EPSILON) {
                cart.getPersistentData().putDouble(
                        FAMILY_SWITCH_BODY_SIGN_KEY,
                        bodySignRelativeToTangent(chosen, axis));
            }
            return chosen;
        }

        return pose.yaw();
    }

    /**
     * Step 9.3g-t4-r8h: Medium-only continuous follower body yaw.
     *
     * This is intentionally narrower than continuousFacingYaw(). Rolling-stock
     * followers may ask for this every tick without changing any curve,
     * diagonal, Small, Large, Very Large, 45 Medium or Parallel behavior.
     * The pose lookup honors the live family-switch session across compatibility
     * guide gaps, so p4/p8 cannot rotate a follower sideways.
     */
    public static Float continuousStandardMediumFacingYaw(
            AbstractMinecart cart,
            float currentBodyYaw) {
        Context context = findPoseContext(cart, cart.getDeltaMovement());
        if (context == null
                || !isTargetStandardMediumFamilySwitch(context)) {
            return null;
        }
        return continuousFacingYaw(cart, currentBodyYaw);
    }

    /**
     * STEP_9_3E_T5_R1_SMALL_DIAGONAL_PRESPAWN_ALIGNMENT
     *
     * RollingStockItem historically spawns every vehicle at the center of the
     * clicked physical rail block. The restored TC4.5 Small Diagonal uses an
     * L-shaped physical gag footprint, so the center of a clicked gag block is
     * not necessarily on the mathematical 45-degree centerline. The continuous
     * path helper then corrected that lateral error after the entity appeared,
     * which looked like the locomotive moved by itself even though its speed was
     * exactly zero.
     *
     * This read-only pose helper lets RollingStockItem project a freshly-created
     * minecart onto the Small Diagonal centerline BEFORE it is added to the
     * world. Runtime movement math and session ownership are untouched.
     */
    public static PlacementPose smallDiagonalPlacementPose(
            AbstractMinecart cart,
            float currentBodyYaw) {
        Context context = findContext(cart, Vec3.ZERO);
        if (context == null
                || !"track_diagonal_straight_small".equals(context.spec.id())) {
            return null;
        }

        LinearRoute[] routes = linearRoutes(context);
        if (routes.length == 0) {
            return null;
        }

        int routeIndex = chooseFreshLinearRoute(
                routes,
                cart.getX(),
                cart.getZ(),
                Vec3.ZERO,
                currentBodyYaw);
        if (routeIndex < 0 || routeIndex >= routes.length) {
            return null;
        }

        LinearRoute route = routes[routeIndex];
        double progress = Mth.clamp(
                route.projectedProgress(cart.getX(), cart.getZ()),
                0.0D,
                route.length);
        Vec3 target = route.pointAt(progress);

        float routeYaw = (float) Math.toDegrees(
                Math.atan2(route.tangent.x, -route.tangent.z));
        routeYaw = Mth.wrapDegrees(routeYaw);
        float oppositeYaw = Mth.wrapDegrees(routeYaw + 180.0F);

        float routeError = Math.abs(Mth.wrapDegrees(currentBodyYaw - routeYaw));
        float oppositeError =
                Math.abs(Mth.wrapDegrees(currentBodyYaw - oppositeYaw));
        float chosenYaw = routeError <= oppositeError ? routeYaw : oppositeYaw;

        return new PlacementPose(
                new Vec3(
                        target.x,
                        context.root.getY() + RAIL_SURFACE_Y,
                        target.z),
                chosenYaw);
    }

    public record PlacementPose(Vec3 position, float yaw) {
    }

    /**
     * Samples a virtual front and rear bogie from one assembly-owned continuous
     * path and derives the body pose from their chord. This is deliberately a
     * read-only sampler: it does not move the cart, change speed, touch coupling,
     * or alter derailment/tractive-effort state.
     */
    public static VirtualBogiePose virtualBogiePose(AbstractMinecart cart,
                                                     float currentBodyYaw,
                                                     double requestedSpacing) {
        Context context = findPoseContext(cart, cart.getDeltaMovement());
        if (context == null) {
            return null;
        }

        double spacing = Mth.clamp(
                Math.abs(requestedSpacing),
                MIN_VIRTUAL_BOGIE_SPACING,
                MAX_VIRTUAL_BOGIE_SPACING);
        double halfSpacing = spacing * 0.5D;

        Vec3 axis = pathTangentForPose(cart, context, currentBodyYaw);
        if (axis == null || axis.horizontalDistanceSqr() <= MOTION_EPSILON) {
            return null;
        }

        double yawRadians = Math.toRadians(currentBodyYaw);
        double bodyForwardX = Math.sin(yawRadians);
        double bodyForwardZ = -Math.cos(yawRadians);
        double bodyDotAxis = bodyForwardX * axis.x + bodyForwardZ * axis.z;
        double frontSign = bodyDotAxis >= 0.0D ? 1.0D : -1.0D;

        // STEP_9_3E_T1_R1_STRICT_LASTTRACK_CROSSING_CONTINUITY
        // The t1 trace proved the center stayed on the correct 45-degree line,
        // but vanilla changed the local guide shape from part 2 to part 4 and
        // currentBodyYaw flipped -135 -> +45 while motion continued unchanged.
        // Original Traincraft's lastTrack owner would never rotate the vehicle
        // because a gag cell changed. Use the body-side latched when this exact
        // crossing session began; travel may reverse without changing it.
        if (TARGET_DIAGONAL_CROSSING.equals(context.spec.id())) {
            var data = cart.getPersistentData();
            String contextKey = linearContextKey(context);
            if (data.contains(LINEAR_CONTEXT_KEY)
                    && contextKey.equals(data.getString(LINEAR_CONTEXT_KEY))
                    && data.contains(LINEAR_BODY_SIGN_KEY)) {
                double storedBodySign = data.getDouble(LINEAR_BODY_SIGN_KEY);
                if (Math.abs(storedBodySign) > 0.5D) {
                    frontSign = storedBodySign >= 0.0D ? 1.0D : -1.0D;
                }
            }
        }

        // STEP_9_3F_T1A_R8_SMALL_SWITCH_BODY_SIDE_CONTINUITY
        //
        // r7 runtime traces showed motion remained on the correct route while
        // yaw alone flipped 180 degrees at Small-switch ownership changes.
        // Preserve the body-side captured when the route session began. Reverse
        // travel changes motion sign, not which physical end of the locomotive
        // is the nose.
        if (isTargetSmallSwitch(context)) {
            var data = cart.getPersistentData();
            String contextKey = linearContextKey(context);
            if (data.contains(SMALL_SWITCH_CONTEXT_KEY)
                    && contextKey.equals(
                            data.getString(SMALL_SWITCH_CONTEXT_KEY))
                    && data.contains(SMALL_SWITCH_BODY_SIGN_KEY)) {
                double storedBodySign =
                        data.getDouble(SMALL_SWITCH_BODY_SIGN_KEY);
                if (Math.abs(storedBodySign) > 0.5D) {
                    frontSign = storedBodySign >= 0.0D ? 1.0D : -1.0D;
                }
            }
        }

        if (isTargetClassicFamilySwitch(context)) {
            var data = cart.getPersistentData();
            String contextKey = linearContextKey(context);
            if (data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                    && contextKey.equals(
                            data.getString(FAMILY_SWITCH_CONTEXT_KEY))
                    && data.contains(FAMILY_SWITCH_BODY_SIGN_KEY)) {
                double storedBodySign =
                        data.getDouble(FAMILY_SWITCH_BODY_SIGN_KEY);
                if (Math.abs(storedBodySign) > 0.5D) {
                    frontSign = storedBodySign >= 0.0D ? 1.0D : -1.0D;
                }
            }
        }

        Vec3 front = samplePathPointAtOffset(
                cart, context, currentBodyYaw, halfSpacing * frontSign);
        Vec3 rear = samplePathPointAtOffset(
                cart, context, currentBodyYaw, -halfSpacing * frontSign);
        if (front == null || rear == null) {
            return null;
        }

        double dx = front.x - rear.x;
        double dy = front.y - rear.y;
        double dz = front.z - rear.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal <= MOTION_EPSILON) {
            return null;
        }

        Vec3 center = new Vec3(
                (front.x + rear.x) * 0.5D,
                (front.y + rear.y) * 0.5D,
                (front.z + rear.z) * 0.5D);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dz)));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, horizontal));
        return new VirtualBogiePose(front, rear, center, yaw, pitch);
    }

    private static Vec3 pathTangentForPose(AbstractMinecart cart,
                                           Context context,
                                           float fallbackYaw) {
        String id = context.spec.id();

        if (isTargetSmallSwitch(context)) {
            int routeIndex = smallSwitchRouteForPose(
                    cart, context, fallbackYaw);
            SmallSwitchSample sample = projectSmallSwitch(
                    context, routeIndex, cart.getX(), cart.getZ());
            return sample == null ? null : sample.tangent;
        }

        if (isTargetClassicFamilySwitch(context)) {
            int routeIndex = familySwitchRouteForPose(
                    cart, context, fallbackYaw);
            FamilySwitchSample sample = projectFamilySwitch(
                    context, routeIndex, cart.getX(), cart.getZ());
            return sample == null ? null : sample.tangent;
        }

        double quarterRadius = quarterCurveRadius(id);
        if (quarterRadius > 0.0D) {
            double progress = projectedQuarterCurvePoseProgress(
                    context, cart.getX(), cart.getZ(), quarterRadius);
            CurveSample sample = sampleQuarterCurveAtPoseProgress(
                    context, progress, quarterRadius);
            return sample.tangent;
        }
        OffsetDefinition targetFortyFive = activeTargetFortyFiveSwitchDefinition(context);
        if (targetFortyFive != null) {
            double progress = projectedFortyFivePoseProgress(
                    context, cart.getX(), cart.getZ(),
                    targetFortyFive.lateral, targetFortyFive.forward);
            CurveSample sample = sampleFortyFiveCurveAtPoseProgress(
                    context, progress,
                    targetFortyFive.lateral, targetFortyFive.forward);
            return sample == null ? null : sample.tangent;
        }
        if (isContinuousCurve(context)) {
            CurveSample sample = sampleCurve(context, cart.getX(), cart.getZ());
            return sample == null ? null : sample.tangent;
        }
        if (isContinuousLinearDiagonal(id)) {
            LinearRoute route = linearRouteForFacing(cart, context, fallbackYaw);
            return route == null ? null : route.tangent;
        }
        if (isStraightSlope(id)) {
            return rotateCanonicalVector(context.facing, 0.0D, -1.0D);
        }
        return null;
    }

    private static Vec3 samplePathPointAtOffset(AbstractMinecart cart,
                                                Context context,
                                                float fallbackYaw,
                                                double pathOffset) {
        String id = context.spec.id();

        if (isTargetSmallSwitch(context)) {
            int routeIndex = smallSwitchRouteForPose(
                    cart, context, fallbackYaw);
            SmallSwitchSample center = projectSmallSwitch(
                    context, routeIndex, cart.getX(), cart.getZ());
            if (center == null) {
                return null;
            }
            // STEP_9_3F_T1A_R8_SMALL_SWITCH_POSE_STRADDLE
            //
            // Do not clamp a virtual bogie to the switch endpoint while the
            // other bogie has already entered the switch. That collapses the
            // virtual wheelbase and makes the locomotive visibly pop/clip as
            // soon as ownership changes. The pose-only sampler may straddle a
            // short distance onto the endpoint tangent; movement remains fully
            // clamped/root-owned by applySmallSwitch().
            SmallSwitchSample sampled = sampleSmallSwitchPoseAtProgress(
                    context,
                    routeIndex,
                    center.progress + pathOffset);
            return sampled == null ? null : new Vec3(
                    sampled.targetHorizontal.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    sampled.targetHorizontal.z);
        }

        if (isTargetClassicFamilySwitch(context)) {
            int routeIndex = familySwitchRouteForPose(
                    cart, context, fallbackYaw);
            FamilySwitchSample center = projectFamilySwitch(
                    context, routeIndex, cart.getX(), cart.getZ());
            if (center == null) {
                return null;
            }
            double progressOffset = pathOffset * center.progressPerBlock;
            FamilySwitchSample sampled = sampleFamilySwitchPoseAtProgress(
                    context, routeIndex, center.progress + progressOffset);
            return sampled == null ? null : new Vec3(
                    sampled.targetHorizontal.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    sampled.targetHorizontal.z);
        }

        double quarterRadius = quarterCurveRadius(id);
        if (quarterRadius > 0.0D) {
            double progress = projectedQuarterCurvePoseProgress(
                    context, cart.getX(), cart.getZ(), quarterRadius);
            CurveSample sampled = sampleQuarterCurveAtPoseProgress(
                    context, progress + pathOffset, quarterRadius);
            return new Vec3(
                    sampled.targetHorizontal.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    sampled.targetHorizontal.z);
        }

        OffsetDefinition targetFortyFive = activeTargetFortyFiveSwitchDefinition(context);
        if (targetFortyFive != null) {
            double progress = projectedFortyFivePoseProgress(
                    context, cart.getX(), cart.getZ(),
                    targetFortyFive.lateral, targetFortyFive.forward);
            CurveSample sampled = sampleFortyFiveCurveAtPoseProgress(
                    context, progress + pathOffset,
                    targetFortyFive.lateral, targetFortyFive.forward);
            if (sampled == null) {
                return null;
            }
            return new Vec3(
                    sampled.targetHorizontal.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    sampled.targetHorizontal.z);
        }

        if (isContinuousCurve(context)) {
            CurveSample center = sampleCurve(context, cart.getX(), cart.getZ());
            if (center == null) {
                return null;
            }

            // Probe along the local tangent then project back onto the exact
            // analytic curve. This gives both virtual bogies the same circle /
            // fitted arc / smoothstep path as the cart center itself.
            double probeX = center.targetHorizontal.x + center.tangent.x * pathOffset;
            double probeZ = center.targetHorizontal.z + center.tangent.z * pathOffset;
            CurveSample sampled = sampleCurve(context, probeX, probeZ);
            if (sampled == null) {
                return null;
            }
            return new Vec3(
                    sampled.targetHorizontal.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    sampled.targetHorizontal.z);
        }

        if (isContinuousLinearDiagonal(id)) {
            LinearRoute route = linearRouteForFacing(cart, context, fallbackYaw);
            if (route == null) {
                return null;
            }
            double progress = Mth.clamp(
                    route.projectedProgress(cart.getX(), cart.getZ()) + pathOffset,
                    0.0D,
                    route.length);
            Vec3 point = route.pointAt(progress);
            return new Vec3(
                    point.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    point.z);
        }

        if (isStraightSlope(id)) {
            double[] local = toCanonical(
                    context.root, context.facing, cart.getX(), cart.getZ());
            double length = Math.max(1.0D, context.spec.partCount() - 1.0D);
            double progress = Mth.clamp(-local[1] + pathOffset, 0.0D, length);
            double fraction = progress / length;
            Vec3 point = fromCanonical(
                    context.root, context.facing, 0.0D, -progress);
            return new Vec3(
                    point.x,
                    context.root.getY() + RAIL_SURFACE_Y + fraction,
                    point.z);
        }

        return null;
    }

    // STEP_9_3G_T4_R1_WHOLE_SWITCH_FAMILY_ROOT_OWNED_ROUTING
    //
    // Standard Medium/Large/Very Large, Medium-45 and Parallel turnouts all
    // share one ownership policy: p0 is the common/toe, the straight and branch
    // endpoints are discovered from the installed spec, and the selected route
    // owns movement until a real external connector is crossed. Hidden guide
    // RailShapes are compatibility/placement data only.
    private static void applyFamilySwitch(AbstractMinecart cart,
                                          Context context,
                                          Vec3 preRailMotion,
                                          boolean powered,
                                          boolean serviceBrake,
                                          boolean handBrake,
                                          double maxSpeed,
                                          boolean retainPreRailSpeed) {
        int routeIndex = selectFamilySwitchRoute(cart, context, preRailMotion);
        FamilySwitchSample projected = projectFamilySwitch(
                context, routeIndex, cart.getX(), cart.getZ());
        if (projected == null) {
            clearFamilySwitchSession(cart, context);
            return;
        }

        Vec3 postRailMotion = cart.getDeltaMovement();
        double sign = travelSign(
                preRailMotion, postRailMotion,
                projected.tangent, cart.getYRot());
        FamilySwitchSample sample = latchedFamilySwitchSample(
                cart, context, projected, preRailMotion,
                postRailMotion, sign);
        if (sample == null) {
            clearFamilySwitchSession(cart, context);
            return;
        }

        double speed = correctedSpeed(
                preRailMotion, postRailMotion, sample.tangent,
                powered, retainPreRailSpeed, serviceBrake, handBrake, maxSpeed);

        // Preserve the accepted Small/diagonal brake rule: once braking reaches
        // zero horizontal speed, route ownership may remain latched but no
        // centerline correction may creep the locomotive.
        if ((serviceBrake || handBrake) && speed <= MOTION_EPSILON) {
            var data = cart.getPersistentData();
            data.putString(FAMILY_SWITCH_CONTEXT_KEY, linearContextKey(context));
            data.putInt(FAMILY_SWITCH_ROUTE_KEY, sample.routeIndex);
            data.putDouble(FAMILY_SWITCH_PROGRESS_KEY, sample.progress);
            data.putLong(FAMILY_SWITCH_TICK_KEY, cart.level().getGameTime());
            cart.setDeltaMovement(0.0D, postRailMotion.y, 0.0D);
            return;
        }

        boolean leavingStart =
                sample.progress <= ENDPOINT_RELEASE_DISTANCE && sign < 0.0D;
        boolean leavingEnd =
                sample.length - sample.progress <= ENDPOINT_RELEASE_DISTANCE
                        && sign > 0.0D;

        if (leavingStart || leavingEnd) {
            boolean atEnd = leavingEnd;
            boolean hasExternal = hasExternalRailAtFamilySwitchEndpoint(
                    cart, context, sample.routeIndex, atEnd);

            if (hasExternal) {
                // Medium-45 keeps the already-proven TC4.5 branch handoff. Its
                // mathematical 3.75-radius endpoint lives beyond the short gag
                // footprint, so arm the attached diagonal at the exact analytic
                // branch endpoint rather than waiting for a generic 0.60 bridge.
                if (atEnd
                        && sample.routeIndex == FAMILY_SWITCH_ROUTE_BRANCH
                        && isTargetFortyFiveFamilySwitch(context.spec.id())
                        && sample.progress >= sample.length) {
                    Context diagonal = findOriginalTraincraftSharedDiagonal(
                            cart, context);
                    if (diagonal != null) {
                        LinearRoute[] routes = linearRoutes(diagonal);
                        if (routes.length > 0) {
                            int route = virtualEndpointHandoffRoute(context, diagonal);
                            if (route < 0 || route >= routes.length) {
                                route = 0;
                            }
                            double handoffProgress = routes[route].projectedProgress(
                                    sample.targetHorizontal.x,
                                    sample.targetHorizontal.z);
                            armPendingVirtualHandoff(
                                    cart, diagonal, route, handoffProgress);
                            cart.setPos(
                                    sample.targetHorizontal.x,
                                    context.root.getY() + RAIL_SURFACE_Y,
                                    sample.targetHorizontal.z);
                            cart.setDeltaMovement(
                                    sample.tangent.x * sign * speed,
                                    postRailMotion.y,
                                    sample.tangent.z * sign * speed);
                            clearFamilySwitchSession(cart, context);
                            return;
                        }
                    }
                }

                boolean clear = atEnd
                        ? sample.progress
                                >= sample.length
                                        + FAMILY_SWITCH_ENDPOINT_BRIDGE_RELEASE
                        : sample.progress
                                <= -FAMILY_SWITCH_ENDPOINT_BRIDGE_RELEASE;
                if (clear) {
                    cart.setPos(
                            sample.targetHorizontal.x,
                            context.root.getY() + RAIL_SURFACE_Y,
                            sample.targetHorizontal.z);
                    cart.setDeltaMovement(
                            sample.tangent.x * sign * speed,
                            postRailMotion.y,
                            sample.tangent.z * sign * speed);
                    clearFamilySwitchSession(cart, context);
                    return;
                }
            } else {
                double heldProgress = atEnd
                        ? Math.max(0.0D,
                                sample.length - LINEAR_ENDPOINT_HOLD_INSET)
                        : Math.min(sample.length,
                                LINEAR_ENDPOINT_HOLD_INSET);
                FamilySwitchSample held = sampleFamilySwitchAtProgress(
                        context, sample.routeIndex, heldProgress);
                if (held == null) {
                    clearFamilySwitchSession(cart, context);
                    return;
                }
                var data = cart.getPersistentData();
                data.putDouble(FAMILY_SWITCH_PROGRESS_KEY, heldProgress);
                data.putLong(FAMILY_SWITCH_TICK_KEY, cart.level().getGameTime());
                cart.setPos(
                        held.targetHorizontal.x,
                        context.root.getY() + RAIL_SURFACE_Y,
                        held.targetHorizontal.z);
                cart.setDeltaMovement(0.0D, postRailMotion.y, 0.0D);
                return;
            }
        }

        cart.setPos(
                sample.targetHorizontal.x,
                context.root.getY() + RAIL_SURFACE_Y,
                sample.targetHorizontal.z);
        cart.setDeltaMovement(
                sample.tangent.x * sign * speed,
                postRailMotion.y,
                sample.tangent.z * sign * speed);
    }

    private static int selectFamilySwitchRoute(AbstractMinecart cart,
                                               Context context,
                                               Vec3 preRailMotion) {
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = linearContextKey(context);

        if (data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                && data.contains(FAMILY_SWITCH_ROUTE_KEY)
                && data.contains(FAMILY_SWITCH_PROGRESS_KEY)
                && data.contains(FAMILY_SWITCH_TICK_KEY)
                && contextKey.equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))) {
            long lastTick = data.getLong(FAMILY_SWITCH_TICK_KEY);
            int storedRoute = data.getInt(FAMILY_SWITCH_ROUTE_KEY);
            if (storedRoute >= FAMILY_SWITCH_ROUTE_STRAIGHT
                    && storedRoute <= FAMILY_SWITCH_ROUTE_BRANCH
                    && now >= lastTick
                    && now - lastTick <= FAMILY_SWITCH_SESSION_MAX_TICK_GAP) {
                return storedRoute;
            }
        }

        // STEP_9_3G_T4_R5_TRAILING_ENDPOINT_ROUTE_OWNERSHIP
        //
        // Do NOT choose a fresh trailing route by nearest centerline. Medium and
        // larger turnouts have a broad shared/overlapping approach region where
        // the straight and branch projections can be equally close. That made a
        // train entering from the physical trailing leg obey switch state -- the
        // exact regression Ryan caught in the connected Medium test.
        //
        // Match frozen Small r11 semantics instead:
        //   * common/toe entry -> switch state chooses the outgoing route;
        //   * straight trailing endpoint -> straight route, state ignored;
        //   * branch trailing endpoint -> branch route, state ignored.
        //
        // The endpoint helper is topology/tangent based, so this also survives
        // mirrored Left/Right pieces and directly connected family turnouts.
        int routeIndex = freshFamilySwitchEntryRoute(
                cart, context, preRailMotion);

        if (routeIndex < FAMILY_SWITCH_ROUTE_STRAIGHT
                || routeIndex > FAMILY_SWITCH_ROUTE_BRANCH) {
            // Internal/spawn fallback only. Normal traversal is decided above by
            // the physical endpoint. Preserve the prior projection behavior for
            // carts created inside a turnout rather than entering through one.
            FamilySwitchSample straight = projectFamilySwitch(
                    context, FAMILY_SWITCH_ROUTE_STRAIGHT,
                    cart.getX(), cart.getZ());
            FamilySwitchSample branch = projectFamilySwitch(
                    context, FAMILY_SWITCH_ROUTE_BRANCH,
                    cart.getX(), cart.getZ());
            double straightDistance = straight == null
                    ? Double.POSITIVE_INFINITY
                    : Math.sqrt(horizontalDistanceSq(
                            cart.getX(), cart.getZ(),
                            straight.targetHorizontal));
            double branchDistance = branch == null
                    ? Double.POSITIVE_INFINITY
                    : Math.sqrt(horizontalDistanceSq(
                            cart.getX(), cart.getZ(),
                            branch.targetHorizontal));

            if (branchDistance + FAMILY_SWITCH_ROUTE_SELECTION_EPSILON
                    < straightDistance) {
                routeIndex = FAMILY_SWITCH_ROUTE_BRANCH;
            } else if (straightDistance + FAMILY_SWITCH_ROUTE_SELECTION_EPSILON
                    < branchDistance) {
                routeIndex = FAMILY_SWITCH_ROUTE_STRAIGHT;
            } else {
                routeIndex = familySwitchStateSelectedRoute(context);
            }
        }

        double initialProgress = initialFamilySwitchProgress(
                cart, context, routeIndex, cart.getX(), cart.getZ());
        data.putString(FAMILY_SWITCH_CONTEXT_KEY, contextKey);
        data.putInt(FAMILY_SWITCH_ROUTE_KEY, routeIndex);
        data.putDouble(FAMILY_SWITCH_PROGRESS_KEY, initialProgress);
        data.putLong(FAMILY_SWITCH_TICK_KEY, now);

        FamilySwitchSample bodySample = projectFamilySwitch(
                context, routeIndex, cart.getX(), cart.getZ());
        if (bodySample != null
                && bodySample.tangent.horizontalDistanceSqr()
                        > MOTION_EPSILON) {
            data.putDouble(
                    FAMILY_SWITCH_BODY_SIGN_KEY,
                    bodySignRelativeToTangent(
                            cart.getYRot(), bodySample.tangent));
        }
        return routeIndex;
    }

    /**
     * Step 9.3g-t4-r5 fresh-entry selector. Returns the route that owns a NEW
     * family-turnout traversal. A trailing endpoint is authoritative regardless
     * of switch state; only the common/toe may consult ACTIVE.
     *
     * Return -1 when the cart is not in a valid endpoint/tangent corridor; the
     * caller may then use the legacy projection fallback for spawn/internal cases.
     */
    private static int freshFamilySwitchEntryRoute(
            AbstractMinecart cart,
            Context context,
            Vec3 handoffMotion) {
        if (context == null || !isTargetClassicFamilySwitch(context)) {
            return -1;
        }

        double axisX = handoffMotion.x;
        double axisZ = handoffMotion.z;
        double axisLength = Math.hypot(axisX, axisZ);
        if (axisLength <= MOTION_EPSILON) {
            Vec3 current = cart.getDeltaMovement();
            axisX = current.x;
            axisZ = current.z;
            axisLength = Math.hypot(axisX, axisZ);
        }
        if (axisLength <= MOTION_EPSILON) {
            double yawRadians = Math.toRadians(cart.getYRot());
            axisX = Math.sin(yawRadians);
            axisZ = -Math.cos(yawRadians);
            axisLength = Math.hypot(axisX, axisZ);
        }
        if (axisLength <= MOTION_EPSILON) {
            return -1;
        }
        axisX /= axisLength;
        axisZ /= axisLength;

        // Trailing endpoints are tested BEFORE the shared common endpoint. This
        // is the critical r5 rule: arriving from a physical branch/straight leg
        // cannot be reinterpreted by switch state.
        boolean branchTrailing = freshFamilySwitchEndpointMatches(
                cart, context, FAMILY_SWITCH_ROUTE_BRANCH, true,
                axisX, axisZ);
        boolean straightTrailing = freshFamilySwitchEndpointMatches(
                cart, context, FAMILY_SWITCH_ROUTE_STRAIGHT, true,
                axisX, axisZ);

        if (branchTrailing && !straightTrailing) {
            return FAMILY_SWITCH_ROUTE_BRANCH;
        }
        if (straightTrailing && !branchTrailing) {
            return FAMILY_SWITCH_ROUTE_STRAIGHT;
        }

        // If two endpoint corridors ever overlap numerically, the physical gag
        // cell under the cart breaks the tie before any distance heuristic can.
        if (branchTrailing && straightTrailing) {
            FamilySwitchEndpoints endpoints = familySwitchEndpoints(context);
            if (endpoints != null) {
                BlockPos branchPos = context.spec.endpointPosition(
                        context.root, context.facing, endpoints.branch);
                BlockPos straightPos = context.spec.endpointPosition(
                        context.root, context.facing, endpoints.straight);
                if (context.railPos.equals(branchPos)
                        && !context.railPos.equals(straightPos)) {
                    return FAMILY_SWITCH_ROUTE_BRANCH;
                }
                if (context.railPos.equals(straightPos)
                        && !context.railPos.equals(branchPos)) {
                    return FAMILY_SWITCH_ROUTE_STRAIGHT;
                }
            }
        }

        boolean common = freshFamilySwitchEndpointMatches(
                cart, context, FAMILY_SWITCH_ROUTE_STRAIGHT, false,
                axisX, axisZ)
                || freshFamilySwitchEndpointMatches(
                        cart, context, FAMILY_SWITCH_ROUTE_BRANCH, false,
                        axisX, axisZ);
        if (common) {
            return familySwitchStateSelectedRoute(context);
        }

        return -1;
    }

    private static FamilySwitchSample latchedFamilySwitchSample(
            AbstractMinecart cart,
            Context context,
            FamilySwitchSample projected,
            Vec3 preRailMotion,
            Vec3 postRailMotion,
            double sign) {
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = linearContextKey(context);

        boolean sameSession = data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                && data.contains(FAMILY_SWITCH_ROUTE_KEY)
                && data.contains(FAMILY_SWITCH_PROGRESS_KEY)
                && data.contains(FAMILY_SWITCH_TICK_KEY)
                && contextKey.equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))
                && data.getInt(FAMILY_SWITCH_ROUTE_KEY)
                        == projected.routeIndex;
        long lastTick = sameSession
                ? data.getLong(FAMILY_SWITCH_TICK_KEY)
                : Long.MIN_VALUE;
        sameSession = sameSession
                && now >= lastTick
                && now - lastTick <= FAMILY_SWITCH_SESSION_MAX_TICK_GAP;

        double progress;
        if (sameSession) {
            double horizontalSpeed =
                    Math.hypot(preRailMotion.x, preRailMotion.z);
            if (horizontalSpeed <= MOTION_EPSILON) {
                horizontalSpeed =
                        Math.hypot(postRailMotion.x, postRailMotion.z);
            }
            long deltaTicks = Math.max(0L, Math.min(2L, now - lastTick));
            double storedProgress =
                    data.getDouble(FAMILY_SWITCH_PROGRESS_KEY);
            FamilySwitchSample storedSample = sampleFamilySwitchAtProgress(
                    context, projected.routeIndex, storedProgress);
            double progressPerBlock = storedSample == null
                    ? 1.0D
                    : Math.max(0.05D, storedSample.progressPerBlock);
            progress = storedProgress
                    + sign * horizontalSpeed
                            * (double) deltaTicks * progressPerBlock;

            boolean startExternal = hasExternalRailAtFamilySwitchEndpoint(
                    cart, context, projected.routeIndex, false);
            boolean endExternal = hasExternalRailAtFamilySwitchEndpoint(
                    cart, context, projected.routeIndex, true);
            double minProgress = startExternal
                    ? -FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                    : 0.0D;
            double maxProgress = endExternal
                    ? projected.length + FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                    : projected.length;
            progress = Mth.clamp(progress, minProgress, maxProgress);
        } else {
            progress = initialFamilySwitchProgress(
                    cart, context, projected.routeIndex,
                    cart.getX(), cart.getZ());
        }

        FamilySwitchSample sample = sampleFamilySwitchBridgeAtProgress(
                context, projected.routeIndex, progress);
        if (sample == null) {
            return null;
        }
        data.putString(FAMILY_SWITCH_CONTEXT_KEY, contextKey);
        data.putInt(FAMILY_SWITCH_ROUTE_KEY, projected.routeIndex);
        data.putDouble(FAMILY_SWITCH_PROGRESS_KEY, progress);
        data.putLong(FAMILY_SWITCH_TICK_KEY, now);
        return sample;
    }

    private static int familySwitchRouteForPose(AbstractMinecart cart,
                                                Context context,
                                                float fallbackYaw) {
        var data = cart.getPersistentData();
        String contextKey = linearContextKey(context);
        if (data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                && data.contains(FAMILY_SWITCH_ROUTE_KEY)
                && contextKey.equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))) {
            int routeIndex = data.getInt(FAMILY_SWITCH_ROUTE_KEY);
            if (routeIndex >= FAMILY_SWITCH_ROUTE_STRAIGHT
                    && routeIndex <= FAMILY_SWITCH_ROUTE_BRANCH) {
                return routeIndex;
            }
        }

        FamilySwitchSample straight = projectFamilySwitch(
                context, FAMILY_SWITCH_ROUTE_STRAIGHT,
                cart.getX(), cart.getZ());
        FamilySwitchSample branch = projectFamilySwitch(
                context, FAMILY_SWITCH_ROUTE_BRANCH,
                cart.getX(), cart.getZ());
        double straightDistance = straight == null
                ? Double.POSITIVE_INFINITY
                : horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        straight.targetHorizontal);
        double branchDistance = branch == null
                ? Double.POSITIVE_INFINITY
                : horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        branch.targetHorizontal);
        double epsilonSq = FAMILY_SWITCH_ROUTE_SELECTION_EPSILON
                * FAMILY_SWITCH_ROUTE_SELECTION_EPSILON;
        if (branchDistance + epsilonSq < straightDistance) {
            return FAMILY_SWITCH_ROUTE_BRANCH;
        }
        if (straightDistance + epsilonSq < branchDistance) {
            return FAMILY_SWITCH_ROUTE_STRAIGHT;
        }
        return familySwitchStateSelectedRoute(context);
    }

    private static double initialFamilySwitchProgress(
            AbstractMinecart cart,
            Context context,
            int routeIndex,
            double worldX,
            double worldZ) {
        FamilySwitchSample projected = projectFamilySwitch(
                context, routeIndex, worldX, worldZ);
        if (projected == null) {
            return 0.0D;
        }

        FamilySwitchSample start = sampleFamilySwitchAtProgress(
                context, routeIndex, 0.0D);
        FamilySwitchSample end = sampleFamilySwitchAtProgress(
                context, routeIndex, projected.length);
        if (start == null || end == null) {
            return projected.progress;
        }

        double startDx = worldX - start.targetHorizontal.x;
        double startDz = worldZ - start.targetHorizontal.z;
        double startAlong =
                startDx * start.tangent.x + startDz * start.tangent.z;
        double startPerpSq = Math.max(
                0.0D,
                startDx * startDx + startDz * startDz
                        - startAlong * startAlong);
        if (startAlong < 0.0D
                && -startAlong <= FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                && startPerpSq
                        <= FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                                * FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                && hasExternalRailAtFamilySwitchEndpoint(
                        cart, context, routeIndex, false)) {
            return startAlong;
        }

        double endDx = worldX - end.targetHorizontal.x;
        double endDz = worldZ - end.targetHorizontal.z;
        double endAlong =
                endDx * end.tangent.x + endDz * end.tangent.z;
        double endPerpSq = Math.max(
                0.0D,
                endDx * endDx + endDz * endDz
                        - endAlong * endAlong);
        if (endAlong > 0.0D
                && endAlong <= FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                && endPerpSq
                        <= FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                                * FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                && hasExternalRailAtFamilySwitchEndpoint(
                        cart, context, routeIndex, true)) {
            return projected.length + endAlong;
        }
        return projected.progress;
    }

    private static FamilySwitchSample projectFamilySwitch(
            Context context,
            int routeIndex,
            double worldX,
            double worldZ) {
        FamilySwitchEndpoints endpoints = familySwitchEndpoints(context);
        if (endpoints == null) {
            return null;
        }
        if (isTargetStandardMediumFamilySwitch(context)) {
            return projectStandardMediumFamilySwitch(
                    context, endpoints, routeIndex, worldX, worldZ);
        }
        double[] local = toCanonical(
                context.root, context.facing, worldX, worldZ);

        double progress;
        if (routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT) {
            double length = Math.hypot(
                    endpoints.straightX, endpoints.straightZ);
            if (length <= 1.0E-9D) {
                return null;
            }
            double ux = endpoints.straightX / length;
            double uz = endpoints.straightZ / length;
            progress = Mth.clamp(
                    local[0] * ux + local[1] * uz,
                    0.0D, length);
        } else if (isTargetFortyFiveFamilySwitch(context.spec.id())) {
            OffsetDefinition definition =
                    switchFortyFiveDefinition(context.spec.id());
            progress = projectFortyFiveCoreProgress(
                    context, local[0], local[1],
                    definition.lateral, definition.forward);
        } else if (isTargetParallelFamilySwitch(context.spec.id())) {
            double forward = -endpoints.branchZ;
            if (forward <= 1.0E-9D) {
                return null;
            }
            progress = Mth.clamp(-local[1], 0.0D, forward);
        } else {
            double radius = Math.abs(endpoints.branchX);
            if (radius <= 1.0E-9D) {
                return null;
            }
            double side = Math.signum(endpoints.branchX);
            double t = Mth.clamp(
                    Math.atan2(-local[1], radius - side * local[0]),
                    0.0D, Math.PI * 0.5D);
            progress = radius * t;
        }
        return sampleFamilySwitchAtProgress(context, routeIndex, progress);
    }

    private static FamilySwitchSample sampleFamilySwitchAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        FamilySwitchEndpoints endpoints = familySwitchEndpoints(context);
        if (endpoints == null) {
            return null;
        }
        if (isTargetStandardMediumFamilySwitch(context)) {
            return sampleStandardMediumFamilySwitchAtProgress(
                    context, endpoints, routeIndex, progress);
        }

        if (routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT) {
            double length = Math.hypot(
                    endpoints.straightX, endpoints.straightZ);
            if (length <= 1.0E-9D) {
                return null;
            }
            double p = Mth.clamp(progress, 0.0D, length);
            double ux = endpoints.straightX / length;
            double uz = endpoints.straightZ / length;
            Vec3 target = fromCanonical(
                    context.root, context.facing,
                    ux * p, uz * p);
            Vec3 tangent = rotateCanonicalVector(
                    context.facing, ux, uz);
            return new FamilySwitchSample(
                    target, tangent, p, length,
                    FAMILY_SWITCH_ROUTE_STRAIGHT, 1.0D);
        }

        if (isTargetFortyFiveFamilySwitch(context.spec.id())) {
            OffsetDefinition definition =
                    switchFortyFiveDefinition(context.spec.id());
            double total = fortyFiveTotalLength(
                    context, definition.lateral, definition.forward);
            CurveSample curve = sampleFortyFiveCurveAtProgress(
                    context, progress,
                    definition.lateral, definition.forward);
            if (curve == null || total <= 0.0D) {
                return null;
            }
            double p = Mth.clamp(progress, 0.0D, total);
            return new FamilySwitchSample(
                    curve.targetHorizontal, curve.tangent,
                    p, total, FAMILY_SWITCH_ROUTE_BRANCH, 1.0D);
        }

        if (isTargetParallelFamilySwitch(context.spec.id())) {
            double lateral = endpoints.branchX;
            double forward = -endpoints.branchZ;
            if (Math.abs(lateral) <= 1.0E-9D
                    || forward <= 1.0E-9D) {
                return null;
            }
            double p = Mth.clamp(progress, 0.0D, forward);
            double u = p / forward;
            double smooth = u * u * (3.0D - 2.0D * u);
            double localX = lateral * smooth;
            double localZ = -p;
            double dxDp = lateral * 6.0D * u * (1.0D - u) / forward;
            double scale = Math.hypot(dxDp, 1.0D);
            double tx = dxDp / scale;
            double tz = -1.0D / scale;
            Vec3 target = fromCanonical(
                    context.root, context.facing, localX, localZ);
            Vec3 tangent = rotateCanonicalVector(
                    context.facing, tx, tz);
            return new FamilySwitchSample(
                    target, tangent, p, forward,
                    FAMILY_SWITCH_ROUTE_BRANCH, 1.0D / scale);
        }

        double radius = Math.abs(endpoints.branchX);
        if (radius <= 1.0E-9D) {
            return null;
        }
        double total = radius * Math.PI * 0.5D;
        double p = Mth.clamp(progress, 0.0D, total);
        double t = p / radius;
        double side = Math.signum(endpoints.branchX);
        double localX = side * radius * (1.0D - Math.cos(t));
        double localZ = -radius * Math.sin(t);
        Vec3 target = fromCanonical(
                context.root, context.facing, localX, localZ);
        Vec3 tangent = rotateCanonicalVector(
                context.facing,
                side * Math.sin(t), -Math.cos(t));
        return new FamilySwitchSample(
                target, tangent, p, total,
                FAMILY_SWITCH_ROUTE_BRANCH, 1.0D);
    }

    private static FamilySwitchSample sampleFamilySwitchBridgeAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        FamilySwitchSample start = sampleFamilySwitchAtProgress(
                context, routeIndex, 0.0D);
        if (start == null) {
            return null;
        }
        if (progress < 0.0D) {
            double extension = Math.max(
                    -FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE, progress);
            Vec3 target = start.targetHorizontal.add(
                    start.tangent.scale(extension));
            return new FamilySwitchSample(
                    target, start.tangent, progress, start.length,
                    routeIndex, 1.0D);
        }

        FamilySwitchSample end = sampleFamilySwitchAtProgress(
                context, routeIndex, start.length);
        if (end == null) {
            return null;
        }
        if (progress > end.length) {
            double extension = Math.min(
                    FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE,
                    progress - end.length);
            Vec3 target = end.targetHorizontal.add(
                    end.tangent.scale(extension));
            return new FamilySwitchSample(
                    target, end.tangent, progress, end.length,
                    routeIndex, 1.0D);
        }
        return sampleFamilySwitchAtProgress(context, routeIndex, progress);
    }

    private static FamilySwitchSample sampleFamilySwitchPoseAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        FamilySwitchSample start = sampleFamilySwitchAtProgress(
                context, routeIndex, 0.0D);
        if (start == null) {
            return null;
        }
        if (progress < 0.0D) {
            double extension = Math.max(-POSE_ENDPOINT_EXTENSION, progress);
            return new FamilySwitchSample(
                    start.targetHorizontal.add(start.tangent.scale(extension)),
                    start.tangent, progress, start.length,
                    routeIndex, 1.0D);
        }
        FamilySwitchSample end = sampleFamilySwitchAtProgress(
                context, routeIndex, start.length);
        if (end == null) {
            return null;
        }
        if (progress > end.length) {
            double extension = Math.min(
                    POSE_ENDPOINT_EXTENSION, progress - end.length);
            return new FamilySwitchSample(
                    end.targetHorizontal.add(end.tangent.scale(extension)),
                    end.tangent, progress, end.length,
                    routeIndex, 1.0D);
        }
        return sampleFamilySwitchAtProgress(context, routeIndex, progress);
    }

    /**
     * STEP_9_3G_T4_R8D_MEDIUM_TRUE_PHYSICAL_TOPOLOGY
     *
     * The Standard Medium pair's real three-end topology is:
     *
     *   p10 = common/toe / outside approach
     *   p4  = straight continuation
     *   p0  = crossover/diverting endpoint
     *
     * That means a train arriving on the outside vertical leg at p10 is making
     * the route choice there. Straight runs p10<->p4. Divert runs p10<->p0.
     *
     * The two paired Medium switches meet p0<->p0. Because p0 is a BRANCH
     * endpoint on both pieces, once a train has entered the crossover the
     * destination turnout sees a trailing branch entry and MUST carry it
     * branch->common/p10 regardless of the destination switch state. This is
     * the realistic entry-owner / exit-follower behavior requested for the
     * paired crossover.
     */
    private static FamilySwitchSample projectStandardMediumFamilySwitch(
            Context context,
            FamilySwitchEndpoints endpoints,
            int routeIndex,
            double worldX,
            double worldZ) {
        double[] local = toCanonical(
                context.root, context.facing, worldX, worldZ);
        double[] commonLocal = familyEndpointLocal(context, endpoints.common);

        if (routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT) {
            double[] straightLocal = familyEndpointLocal(
                    context, endpoints.straight);
            double dx = straightLocal[0] - commonLocal[0];
            double dz = straightLocal[1] - commonLocal[1];
            double length = Math.hypot(dx, dz);
            if (length <= 1.0E-9D) {
                return null;
            }
            double ux = dx / length;
            double uz = dz / length;
            double progress = Mth.clamp(
                    (local[0] - commonLocal[0]) * ux
                            + (local[1] - commonLocal[1]) * uz,
                    0.0D, length);
            return sampleStandardMediumFamilySwitchAtProgress(
                    context, endpoints, routeIndex, progress);
        }

        // Branch p10 -> p0 is the existing quarter-circle p0 -> p10
        // traversed in reverse, so progress 0 is the true common/toe p10.
        double radius = Math.abs(commonLocal[0]);
        if (radius <= 1.0E-9D) {
            return null;
        }
        double side = Math.signum(commonLocal[0]);
        double oldT = Mth.clamp(
                Math.atan2(-local[1], radius - side * local[0]),
                0.0D, Math.PI * 0.5D);
        double total = radius * Math.PI * 0.5D;
        double oldProgress = radius * oldT;
        double progress = total - oldProgress;
        return sampleStandardMediumFamilySwitchAtProgress(
                context, endpoints, routeIndex, progress);
    }

    private static FamilySwitchSample sampleStandardMediumFamilySwitchAtProgress(
            Context context,
            FamilySwitchEndpoints endpoints,
            int routeIndex,
            double progress) {
        double[] commonLocal = familyEndpointLocal(context, endpoints.common);

        if (routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT) {
            double[] straightLocal = familyEndpointLocal(
                    context, endpoints.straight);
            double dx = straightLocal[0] - commonLocal[0];
            double dz = straightLocal[1] - commonLocal[1];
            double length = Math.hypot(dx, dz);
            if (length <= 1.0E-9D) {
                return null;
            }
            double p = Mth.clamp(progress, 0.0D, length);
            double ux = dx / length;
            double uz = dz / length;
            Vec3 target = fromCanonical(
                    context.root, context.facing,
                    commonLocal[0] + ux * p,
                    commonLocal[1] + uz * p);
            Vec3 tangent = rotateCanonicalVector(
                    context.facing, ux, uz);
            return new FamilySwitchSample(
                    target, tangent, p, length,
                    FAMILY_SWITCH_ROUTE_STRAIGHT, 1.0D);
        }

        double radius = Math.abs(commonLocal[0]);
        if (radius <= 1.0E-9D) {
            return null;
        }
        double total = radius * Math.PI * 0.5D;
        double p = Mth.clamp(progress, 0.0D, total);
        double oldProgress = total - p;
        double t = oldProgress / radius;
        double side = Math.signum(commonLocal[0]);
        double localX = side * radius * (1.0D - Math.cos(t));
        double localZ = -radius * Math.sin(t);
        Vec3 target = fromCanonical(
                context.root, context.facing, localX, localZ);

        // Negate the original p0->p10 tangent because this route is p10->p0.
        Vec3 tangent = rotateCanonicalVector(
                context.facing,
                -side * Math.sin(t), Math.cos(t));
        return new FamilySwitchSample(
                target, tangent, p, total,
                FAMILY_SWITCH_ROUTE_BRANCH, 1.0D);
    }

    private static FamilySwitchEndpoints familySwitchEndpoints(Context context) {
        if (context == null || !isTargetClassicFamilySwitch(context)) {
            return null;
        }

        // STEP_9_3G_T4_R8D_MEDIUM_TRUE_PHYSICAL_TOPOLOGY
        //
        // Standard Medium's outside approach/mainline is p10. Its straight
        // continuation is p4 and its crossover/diverting connector is p0.
        // These are LOCAL roles; facing rotation handles N/S/E/W placement.
        if (isTargetStandardMediumFamilySwitch(context)) {
            LegacyBatchTrackSpec.Endpoint common = null;
            LegacyBatchTrackSpec.Endpoint straight = null;
            LegacyBatchTrackSpec.Endpoint branch = null;
            for (LegacyBatchTrackSpec.Endpoint endpoint : context.spec.endpoints()) {
                if (endpoint.part() == 10) {
                    common = endpoint;
                } else if (endpoint.part() == 4) {
                    straight = endpoint;
                } else if (endpoint.part() == 0) {
                    branch = endpoint;
                }
            }
            if (common == null || straight == null || branch == null) {
                return null;
            }
            double[] straightLocal = familyEndpointLocal(context, straight);
            double[] branchLocal = familyEndpointLocal(context, branch);
            return new FamilySwitchEndpoints(
                    common, straight, branch,
                    straightLocal[0], straightLocal[1],
                    branchLocal[0], branchLocal[1]);
        }

        LegacyBatchTrackSpec.Endpoint common = null;
        LegacyBatchTrackSpec.Endpoint first = null;
        LegacyBatchTrackSpec.Endpoint second = null;
        for (LegacyBatchTrackSpec.Endpoint endpoint : context.spec.endpoints()) {
            if (endpoint.part() == 0) {
                common = endpoint;
            } else if (first == null) {
                first = endpoint;
            } else if (second == null) {
                second = endpoint;
            }
        }
        if (common == null || first == null || second == null) {
            return null;
        }

        double[] firstLocal = familyEndpointLocal(context, first);
        double[] secondLocal = familyEndpointLocal(context, second);
        LegacyBatchTrackSpec.Endpoint straight;
        LegacyBatchTrackSpec.Endpoint branch;
        double[] straightLocal;
        double[] branchLocal;
        double firstLateral = Math.abs(firstLocal[0]);
        double secondLateral = Math.abs(secondLocal[0]);
        if (firstLateral < secondLateral - 1.0E-9D
                || (Math.abs(firstLateral - secondLateral) <= 1.0E-9D
                        && first.part() < second.part())) {
            straight = first;
            straightLocal = firstLocal;
            branch = second;
            branchLocal = secondLocal;
        } else {
            straight = second;
            straightLocal = secondLocal;
            branch = first;
            branchLocal = firstLocal;
        }

        return new FamilySwitchEndpoints(
                common, straight, branch,
                straightLocal[0], straightLocal[1],
                branchLocal[0], branchLocal[1]);
    }

    private static double[] familyEndpointLocal(
            Context context,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        BlockPos pos = context.spec.endpointPosition(
                context.root, context.facing, endpoint);
        return toCanonical(
                context.root, context.facing,
                pos.getX() + 0.5D, pos.getZ() + 0.5D);
    }

    private static boolean hasExternalRailAtFamilySwitchEndpoint(
            AbstractMinecart cart,
            Context context,
            int routeIndex,
            boolean atEnd) {
        FamilySwitchEndpoints endpoints = familySwitchEndpoints(context);
        if (endpoints == null) {
            return false;
        }

        // The original Medium-45 branch connects through a shared gag/proxy
        // root whose mathematical endpoint lies beyond p4. Prefer that exact
        // topology when present.
        if (atEnd
                && routeIndex == FAMILY_SWITCH_ROUTE_BRANCH
                && isTargetFortyFiveFamilySwitch(context.spec.id())
                && findOriginalTraincraftSharedDiagonal(cart, context) != null) {
            return true;
        }

        LegacyBatchTrackSpec.Endpoint endpoint = atEnd
                ? (routeIndex == FAMILY_SWITCH_ROUTE_BRANCH
                        ? endpoints.branch : endpoints.straight)
                : endpoints.common;
        BlockPos endpointPos = context.spec.endpointPosition(
                context.root, context.facing, endpoint);
        Direction outward;

        // STEP_9_3G_T4_R8F_MEDIUM_STRAIGHT_ENDPOINT_HANDOFF
        //
        // Standard Medium reuses p4 as the end of the visual/physical straight
        // p10->p4 route. The legacy endpoint metadata on p4 still describes the
        // old east/west turnout leg, so using endpoint.outward() here looks for
        // an external rail on the wrong side and causes the cart to stop at p4.
        //
        // Only this one endpoint needs an override. Derive its outward direction
        // from the proven continuous straight-route tangent. This keeps the fix
        // local/rotation-safe for both Left and Right and for N/S/E/W placement.
        //
        // p10/common and p0/divert keep their original endpoint metadata because
        // those physical connectors are still correct.
        if (isTargetStandardMediumFamilySwitch(context)
                && atEnd
                && routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT) {
            FamilySwitchSample routeStart = sampleFamilySwitchAtProgress(
                    context, routeIndex, 0.0D);
            FamilySwitchSample routeEnd = routeStart == null
                    ? null
                    : sampleFamilySwitchAtProgress(
                            context, routeIndex, routeStart.length);
            if (routeEnd == null
                    || routeEnd.tangent.horizontalDistanceSqr()
                            <= MOTION_EPSILON) {
                return false;
            }

            double tx = routeEnd.tangent.x;
            double tz = routeEnd.tangent.z;
            if (Math.abs(tx) >= Math.abs(tz)) {
                outward = tx >= 0.0D
                        ? Direction.EAST
                        : Direction.WEST;
            } else {
                outward = tz >= 0.0D
                        ? Direction.SOUTH
                        : Direction.NORTH;
            }
        } else {
            outward = context.spec.rotateDirection(
                    context.facing, endpoint.outward());
        }

        BlockPos next = endpointPos.relative(outward);
        BlockState state = cart.level().getBlockState(next);
        if (BaseRailBlock.isRail(state)) {
            return true;
        }
        return BaseRailBlock.isRail(
                cart.level().getBlockState(next.below()));
    }

    private static boolean canBeginFreshFamilySwitchOwnership(
            AbstractMinecart cart,
            Context context,
            Vec3 handoffMotion) {
        if (context == null || !isTargetClassicFamilySwitch(context)) {
            return false;
        }

        // A persisted same-root session is already authoritative. This also
        // makes save/reload or a brief stationary pause resume the selected
        // route instead of falling back to per-cell RailShape steering.
        var data = cart.getPersistentData();
        String contextKey = linearContextKey(context);
        if (data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                && data.contains(FAMILY_SWITCH_ROUTE_KEY)
                && contextKey.equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))) {
            int route = data.getInt(FAMILY_SWITCH_ROUTE_KEY);
            if (route >= FAMILY_SWITCH_ROUTE_STRAIGHT
                    && route <= FAMILY_SWITCH_ROUTE_BRANCH) {
                return true;
            }
        }

        // r5 uses the SAME endpoint classifier for admission and route choice.
        // This prevents findContext() from admitting one physical leg and then
        // selectFamilySwitchRoute() reinterpreting it through switch state.
        return freshFamilySwitchEntryRoute(cart, context, handoffMotion) >= 0;
    }

    private static boolean freshFamilySwitchEndpointMatches(
            AbstractMinecart cart,
            Context context,
            int routeIndex,
            boolean atEnd,
            double axisX,
            double axisZ) {
        if (!hasExternalRailAtFamilySwitchEndpoint(
                cart, context, routeIndex, atEnd)) {
            return false;
        }

        FamilySwitchSample routeStart = sampleFamilySwitchAtProgress(
                context, routeIndex, 0.0D);
        if (routeStart == null) {
            return false;
        }
        FamilySwitchSample endpoint = atEnd
                ? sampleFamilySwitchAtProgress(
                        context, routeIndex, routeStart.length)
                : routeStart;
        if (endpoint == null
                || endpoint.tangent.horizontalDistanceSqr()
                        <= MOTION_EPSILON) {
            return false;
        }

        double dx = cart.getX() - endpoint.targetHorizontal.x;
        double dz = cart.getZ() - endpoint.targetHorizontal.z;
        double along = dx * endpoint.tangent.x
                + dz * endpoint.tangent.z;
        double perpSq = Math.max(
                0.0D,
                dx * dx + dz * dz - along * along);

        // A fresh capture is allowed only in the short endpoint bridge corridor.
        // In particular, a cart one block north/south of an east/west p4 is
        // rejected even if Minecraft's nearest-rail lookup has already selected
        // that p4 gag block.
        if (Math.abs(along) > FAMILY_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                || perpSq
                        > FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                                * FAMILY_SWITCH_ENDPOINT_BRIDGE_MAX_PERP) {
            return false;
        }

        double tangentLength = Math.hypot(
                endpoint.tangent.x, endpoint.tangent.z);
        if (tangentLength <= MOTION_EPSILON) {
            return false;
        }
        double tangentDot = Math.abs(
                axisX * endpoint.tangent.x / tangentLength
                        + axisZ * endpoint.tangent.z / tangentLength);
        return tangentDot >= FAMILY_SWITCH_FRESH_ENTRY_MIN_TANGENT_DOT;
    }

    /**
     * STEP_9_3G_T4_R4_CONNECTED_FAMILY_HANDOFF
     *
     * Preserve the confirmed Small-switch rule for directly connected turnouts:
     * once the selected source endpoint is being crossed and the cart is already
     * physically inside a DIFFERENT family turnout's reciprocal endpoint block,
     * the destination turnout becomes owner immediately. Its own route selector
     * then applies destination switch state at a common/toe entry, or the physical
     * route at a trailing straight/branch entry.
     *
     * This is deliberately topology-exact. Nearby or overlapping family pieces
     * cannot steal ownership: both endpoint cells must be reciprocal one-block
     * connectors and their mathematical tangents must agree.
     */
    private static Context findConnectedFamilySwitchHandoff(
            AbstractMinecart cart,
            BlockPos[] direct,
            Context source,
            Vec3 handoffMotion) {
        if (source == null || !isTargetClassicFamilySwitch(source)) {
            return null;
        }

        var data = cart.getPersistentData();
        String sourceKey = linearContextKey(source);
        if (!data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                || !data.contains(FAMILY_SWITCH_ROUTE_KEY)
                || !data.contains(FAMILY_SWITCH_PROGRESS_KEY)
                || !sourceKey.equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))) {
            return null;
        }

        int routeIndex = data.getInt(FAMILY_SWITCH_ROUTE_KEY);
        if (routeIndex < FAMILY_SWITCH_ROUTE_STRAIGHT
                || routeIndex > FAMILY_SWITCH_ROUTE_BRANCH) {
            return null;
        }

        FamilySwitchSample routeStart = sampleFamilySwitchAtProgress(
                source, routeIndex, 0.0D);
        if (routeStart == null) {
            return null;
        }
        double progress = data.getDouble(FAMILY_SWITCH_PROGRESS_KEY);
        FamilySwitchSample sourceSample = sampleFamilySwitchBridgeAtProgress(
                source, routeIndex, progress);
        if (sourceSample == null
                || sourceSample.tangent.horizontalDistanceSqr()
                        <= MOTION_EPSILON) {
            return null;
        }

        double motionX = handoffMotion.x;
        double motionZ = handoffMotion.z;
        double motionLength = Math.hypot(motionX, motionZ);
        if (motionLength <= MOTION_EPSILON) {
            Vec3 current = cart.getDeltaMovement();
            motionX = current.x;
            motionZ = current.z;
            motionLength = Math.hypot(motionX, motionZ);
        }
        if (motionLength <= MOTION_EPSILON) {
            return null;
        }

        double alongSource = motionX * sourceSample.tangent.x
                + motionZ * sourceSample.tangent.z;
        boolean leavingStart =
                progress <= ENDPOINT_RELEASE_DISTANCE
                        && alongSource < -MOTION_EPSILON;
        boolean leavingEnd =
                progress >= routeStart.length - ENDPOINT_RELEASE_DISTANCE
                        && alongSource > MOTION_EPSILON;
        if (!leavingStart && !leavingEnd) {
            return null;
        }

        FamilySwitchEndpoints sourceEndpoints = familySwitchEndpoints(source);
        if (sourceEndpoints == null) {
            return null;
        }
        LegacyBatchTrackSpec.Endpoint sourceEndpoint = leavingStart
                ? sourceEndpoints.common
                : (routeIndex == FAMILY_SWITCH_ROUTE_STRAIGHT
                        ? sourceEndpoints.straight
                        : sourceEndpoints.branch);
        BlockPos sourceEndpointPos = source.spec.endpointPosition(
                source.root, source.facing, sourceEndpoint);
        Direction sourceOutward = source.spec.rotateDirection(
                source.facing, sourceEndpoint.outward());
        BlockPos expectedDestinationEndpoint =
                sourceEndpointPos.relative(sourceOutward);

        double sourceTangentLength = Math.hypot(
                sourceSample.tangent.x, sourceSample.tangent.z);
        if (sourceTangentLength <= MOTION_EPSILON) {
            return null;
        }

        for (BlockPos pos : direct) {
            BlockState state = cart.level().getBlockState(pos);
            if (!(state.getBlock()
                    instanceof AbstractLegacyBatchRailBlock batch)) {
                continue;
            }
            Context destination = new Context(batch, state, pos);
            if (!isTargetClassicFamilySwitch(destination)
                    || sourceKey.equals(linearContextKey(destination))) {
                continue;
            }

            FamilySwitchEndpoints destinationEndpoints =
                    familySwitchEndpoints(destination);
            if (destinationEndpoints == null) {
                continue;
            }
            LegacyBatchTrackSpec.Endpoint[] candidates =
                    new LegacyBatchTrackSpec.Endpoint[] {
                            destinationEndpoints.common,
                            destinationEndpoints.straight,
                            destinationEndpoints.branch
                    };
            for (LegacyBatchTrackSpec.Endpoint destinationEndpoint
                    : candidates) {
                BlockPos destinationEndpointPos =
                        destination.spec.endpointPosition(
                                destination.root, destination.facing,
                                destinationEndpoint);
                if (!destinationEndpointPos.equals(
                        expectedDestinationEndpoint)
                        || !destination.railPos.equals(
                                destinationEndpointPos)) {
                    continue;
                }

                Direction destinationOutward =
                        destination.spec.rotateDirection(
                                destination.facing,
                                destinationEndpoint.outward());
                if (!destinationEndpointPos
                        .relative(destinationOutward)
                        .equals(sourceEndpointPos)) {
                    continue;
                }

                int destinationRoute =
                        destinationEndpoint == destinationEndpoints.branch
                                ? FAMILY_SWITCH_ROUTE_BRANCH
                                : FAMILY_SWITCH_ROUTE_STRAIGHT;
                FamilySwitchSample destinationStart =
                        sampleFamilySwitchAtProgress(
                                destination, destinationRoute, 0.0D);
                if (destinationStart == null) {
                    continue;
                }
                FamilySwitchSample destinationSample =
                        destinationEndpoint == destinationEndpoints.common
                                ? destinationStart
                                : sampleFamilySwitchAtProgress(
                                        destination, destinationRoute,
                                        destinationStart.length);
                if (destinationSample == null
                        || destinationSample.tangent
                                .horizontalDistanceSqr()
                                <= MOTION_EPSILON) {
                    continue;
                }

                double destinationTangentLength = Math.hypot(
                        destinationSample.tangent.x,
                        destinationSample.tangent.z);
                double tangentDot = Math.abs(
                        sourceSample.tangent.x
                                * destinationSample.tangent.x
                                    / (sourceTangentLength
                                            * destinationTangentLength)
                        + sourceSample.tangent.z
                                * destinationSample.tangent.z
                                    / (sourceTangentLength
                                            * destinationTangentLength));
                if (tangentDot
                        < FAMILY_SWITCH_FRESH_ENTRY_MIN_TANGENT_DOT) {
                    continue;
                }

                return destination;
            }
        }

        return null;
    }

    private static Context findLatchedFamilySwitchContext(
            AbstractMinecart cart,
            BlockPos base) {
        var data = cart.getPersistentData();
        if (!data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                || !data.contains(FAMILY_SWITCH_ROUTE_KEY)
                || !data.contains(FAMILY_SWITCH_PROGRESS_KEY)
                || !data.contains(FAMILY_SWITCH_TICK_KEY)) {
            return null;
        }
        long now = cart.level().getGameTime();
        long lastTick = data.getLong(FAMILY_SWITCH_TICK_KEY);
        if (now < lastTick
                || now - lastTick > FAMILY_SWITCH_SESSION_MAX_TICK_GAP) {
            return null;
        }
        int routeIndex = data.getInt(FAMILY_SWITCH_ROUTE_KEY);
        if (routeIndex < FAMILY_SWITCH_ROUTE_STRAIGHT
                || routeIndex > FAMILY_SWITCH_ROUTE_BRANCH) {
            return null;
        }
        String expected = data.getString(FAMILY_SWITCH_CONTEXT_KEY);
        Context best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -FAMILY_SWITCH_LATCH_SEARCH_RADIUS;
                 dx <= FAMILY_SWITCH_LATCH_SEARCH_RADIUS; dx++) {
                for (int dz = -FAMILY_SWITCH_LATCH_SEARCH_RADIUS;
                     dz <= FAMILY_SWITCH_LATCH_SEARCH_RADIUS; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }
                    Context candidate = new Context(batch, state, pos);
                    if (!isTargetClassicFamilySwitch(candidate)
                            || !expected.equals(linearContextKey(candidate))) {
                        continue;
                    }
                    double cx = pos.getX() + 0.5D;
                    double cz = pos.getZ() + 0.5D;
                    double distSq = (cart.getX() - cx) * (cart.getX() - cx)
                            + (cart.getZ() - cz) * (cart.getZ() - cz);
                    if (distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static void clearFamilySwitchSession(
            AbstractMinecart cart,
            Context context) {
        var data = cart.getPersistentData();
        if (data.contains(FAMILY_SWITCH_CONTEXT_KEY)
                && !linearContextKey(context).equals(
                        data.getString(FAMILY_SWITCH_CONTEXT_KEY))) {
            return;
        }
        data.remove(FAMILY_SWITCH_CONTEXT_KEY);
        data.remove(FAMILY_SWITCH_ROUTE_KEY);
        data.remove(FAMILY_SWITCH_PROGRESS_KEY);
        data.remove(FAMILY_SWITCH_TICK_KEY);
        data.remove(FAMILY_SWITCH_BODY_SIGN_KEY);
    }

    private static void applySmallSwitch(AbstractMinecart cart,
                                         Context context,
                                         Vec3 preRailMotion,
                                         boolean powered,
                                         boolean serviceBrake,
                                         boolean handBrake,
                                         double maxSpeed,
                                         boolean retainPreRailSpeed) {
        int routeIndex = selectSmallSwitchRoute(cart, context, preRailMotion);
        SmallSwitchSample projected = projectSmallSwitch(
                context, routeIndex, cart.getX(), cart.getZ());
        if (projected == null) {
            clearSmallSwitchSession(cart, context);
            return;
        }

        Vec3 postRailMotion = cart.getDeltaMovement();
        double sign = travelSign(
                preRailMotion, postRailMotion,
                projected.tangent, cart.getYRot());

        SmallSwitchSample sample = latchedSmallSwitchSample(
                cart, context, projected, preRailMotion,
                postRailMotion, sign);

        double speed = correctedSpeed(
                preRailMotion, postRailMotion, sample.tangent,
                powered, retainPreRailSpeed, serviceBrake, handBrake, maxSpeed);

        // Preserve the accepted r8e brake/handbrake behavior: once braking has
        // reduced horizontal speed to zero, no route-centering code may creep
        // the locomotive afterward.
        if ((serviceBrake || handBrake) && speed <= MOTION_EPSILON) {
            var data = cart.getPersistentData();
            data.putString(
                    SMALL_SWITCH_CONTEXT_KEY, linearContextKey(context));
            data.putInt(SMALL_SWITCH_ROUTE_KEY, sample.routeIndex);
            data.putDouble(
                    SMALL_SWITCH_PROGRESS_KEY,
                    sample.progress);
            data.putLong(
                    SMALL_SWITCH_TICK_KEY, cart.level().getGameTime());
            cart.setDeltaMovement(0.0D, postRailMotion.y, 0.0D);
            return;
        }

        boolean leavingStart =
                sample.progress <= ENDPOINT_RELEASE_DISTANCE && sign < 0.0D;
        boolean leavingEnd =
                sample.length - sample.progress <= ENDPOINT_RELEASE_DISTANCE
                        && sign > 0.0D;

        if (leavingStart || leavingEnd) {
            boolean atEnd = leavingEnd;
            boolean hasExternal = hasExternalRailAtSmallSwitchEndpoint(
                    cart, context, sample.routeIndex, atEnd);

            if (hasExternal) {
                // STEP_9_3F_T1A_R9_BIDIRECTIONAL_ENDPOINT_BRIDGE
                //
                // Stay root-owned until the cart center is safely inside the
                // attached external rail block. Unlike r7's p2-only handoff,
                // this works at p0/p2/p5 and in BOTH travel directions. The
                // stored progress itself crosses below 0 or above route length,
                // so there is no endpoint-center teleport and no negative
                // handoff accumulator that can deadlock after vanilla kills
                // motion on a proxy RailShape.
                boolean clear = atEnd
                        ? sample.progress
                                >= sample.length
                                        + SMALL_SWITCH_ENDPOINT_BRIDGE_RELEASE
                        : sample.progress
                                <= -SMALL_SWITCH_ENDPOINT_BRIDGE_RELEASE;
                if (clear) {
                    cart.setPos(
                            sample.targetHorizontal.x,
                            context.root.getY() + RAIL_SURFACE_Y,
                            sample.targetHorizontal.z);
                    cart.setDeltaMovement(
                            sample.tangent.x * sign * speed,
                            postRailMotion.y,
                            sample.tangent.z * sign * speed);
                    clearSmallSwitchSession(cart, context);
                    return;
                }
                // Continue below. sampleSmallSwitchBridgeAtProgress() already
                // supplies the exact endpoint tangent while progress is outside
                // the mathematical p0/p2/p5 route.
            } else {
                double heldProgress = atEnd
                        ? Math.max(0.0D,
                                sample.length - LINEAR_ENDPOINT_HOLD_INSET)
                        : Math.min(sample.length,
                                LINEAR_ENDPOINT_HOLD_INSET);
                SmallSwitchSample held = sampleSmallSwitchAtProgress(
                        context, sample.routeIndex, heldProgress);
                var data = cart.getPersistentData();
                data.putDouble(SMALL_SWITCH_PROGRESS_KEY, heldProgress);
                data.putLong(
                        SMALL_SWITCH_TICK_KEY, cart.level().getGameTime());
                cart.setPos(
                        held.targetHorizontal.x,
                        context.root.getY() + RAIL_SURFACE_Y,
                        held.targetHorizontal.z);
                cart.setDeltaMovement(
                        0.0D, postRailMotion.y, 0.0D);
                return;
            }
        }

        cart.setPos(
                sample.targetHorizontal.x,
                context.root.getY() + RAIL_SURFACE_Y,
                sample.targetHorizontal.z);
        cart.setDeltaMovement(
                sample.tangent.x * sign * speed,
                postRailMotion.y,
                sample.tangent.z * sign * speed);
    }

    /**
     * Selects the route once when a Small switch is entered.
     *
     * t1a true topology:
     *   p5 = common/toe on the outside mainline
     *   p2 = opposite outside-straight endpoint
     *   p0 = inside/diverging endpoint
     *
     * Trailing entry is determined by the physical endpoint:
     *   p2 -> p5 stays STRAIGHT regardless of switch state
     *   p0 -> p5 stays BRANCH regardless of switch state
     *
     * At p5 both routes share the same point, so ACTIVE chooses the branch and
     * INACTIVE chooses the outside straight. Once selected, route identity is
     * sticky for the whole assembly and gag/proxy cells cannot change it.
     */
    private static int selectSmallSwitchRoute(AbstractMinecart cart,
                                              Context context,
                                              Vec3 preRailMotion) {
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = linearContextKey(context);

        if (data.contains(SMALL_SWITCH_CONTEXT_KEY)
                && data.contains(SMALL_SWITCH_ROUTE_KEY)
                && data.contains(SMALL_SWITCH_PROGRESS_KEY)
                && data.contains(SMALL_SWITCH_TICK_KEY)
                && contextKey.equals(
                        data.getString(SMALL_SWITCH_CONTEXT_KEY))) {
            long lastTick = data.getLong(SMALL_SWITCH_TICK_KEY);
            int storedRoute = data.getInt(SMALL_SWITCH_ROUTE_KEY);
            if (storedRoute >= SMALL_SWITCH_ROUTE_STRAIGHT
                    && storedRoute <= SMALL_SWITCH_ROUTE_BRANCH
                    && now >= lastTick
                    && now - lastTick <= SMALL_SWITCH_SESSION_MAX_TICK_GAP) {
                return storedRoute;
            }
        }

        SmallSwitchSample straight = projectSmallSwitch(
                context, SMALL_SWITCH_ROUTE_STRAIGHT,
                cart.getX(), cart.getZ());
        SmallSwitchSample branch = projectSmallSwitch(
                context, SMALL_SWITCH_ROUTE_BRANCH,
                cart.getX(), cart.getZ());

        double straightDistance = straight == null
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        straight.targetHorizontal));
        double branchDistance = branch == null
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        branch.targetHorizontal));

        int routeIndex;
        if (branchDistance + SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                < straightDistance) {
            routeIndex = SMALL_SWITCH_ROUTE_BRANCH;
        } else if (straightDistance + SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                < branchDistance) {
            routeIndex = SMALL_SWITCH_ROUTE_STRAIGHT;
        } else {
            // Common/toe ambiguity: switch state owns the outgoing choice.
            routeIndex = stateActive(context.state)
                    ? SMALL_SWITCH_ROUTE_BRANCH
                    : SMALL_SWITCH_ROUTE_STRAIGHT;
        }

        data.putString(SMALL_SWITCH_CONTEXT_KEY, contextKey);
        data.putInt(SMALL_SWITCH_ROUTE_KEY, routeIndex);
        data.putDouble(
                SMALL_SWITCH_PROGRESS_KEY,
                initialSmallSwitchProgress(
                        cart, context, routeIndex,
                        cart.getX(), cart.getZ()));
        data.putLong(SMALL_SWITCH_TICK_KEY, now);

        // STEP_9_3F_T1A_R8_SMALL_SWITCH_BODY_SIDE_CONTINUITY
        // Capture the nose side once, before proxy-cell facing can influence
        // body yaw. This sign is intentionally NOT changed by later reversal.
        SmallSwitchSample bodySample = projectSmallSwitch(
                context, routeIndex, cart.getX(), cart.getZ());
        if (bodySample != null
                && bodySample.tangent.horizontalDistanceSqr()
                        > MOTION_EPSILON) {
            data.putDouble(
                    SMALL_SWITCH_BODY_SIGN_KEY,
                    bodySignRelativeToTangent(
                            cart.getYRot(), bodySample.tangent));
        }

        return routeIndex;
    }

    private static SmallSwitchSample latchedSmallSwitchSample(
            AbstractMinecart cart,
            Context context,
            SmallSwitchSample projected,
            Vec3 preRailMotion,
            Vec3 postRailMotion,
            double sign) {
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = linearContextKey(context);

        boolean sameSession = data.contains(SMALL_SWITCH_CONTEXT_KEY)
                && data.contains(SMALL_SWITCH_ROUTE_KEY)
                && data.contains(SMALL_SWITCH_PROGRESS_KEY)
                && data.contains(SMALL_SWITCH_TICK_KEY)
                && contextKey.equals(
                        data.getString(SMALL_SWITCH_CONTEXT_KEY))
                && data.getInt(SMALL_SWITCH_ROUTE_KEY)
                        == projected.routeIndex;

        long lastTick = sameSession
                ? data.getLong(SMALL_SWITCH_TICK_KEY)
                : Long.MIN_VALUE;
        sameSession = sameSession
                && now >= lastTick
                && now - lastTick <= SMALL_SWITCH_SESSION_MAX_TICK_GAP;

        double progress;
        if (sameSession) {
            double horizontalSpeed =
                    Math.hypot(preRailMotion.x, preRailMotion.z);
            if (horizontalSpeed <= MOTION_EPSILON) {
                horizontalSpeed =
                        Math.hypot(postRailMotion.x, postRailMotion.z);
            }
            long deltaTicks =
                    Math.max(0L, Math.min(2L, now - lastTick));
            progress = data.getDouble(SMALL_SWITCH_PROGRESS_KEY)
                    + sign * horizontalSpeed * (double) deltaTicks;

            // Root-owned means the compatibility cells have ZERO authority over
            // progress once a route is selected. Do not blend the per-cell
            // vanilla position back in; that is the exact sideways bug t1 fixes.
            boolean startExternal = hasExternalRailAtSmallSwitchEndpoint(
                    cart, context, projected.routeIndex, false);
            boolean endExternal = hasExternalRailAtSmallSwitchEndpoint(
                    cart, context, projected.routeIndex, true);
            double minProgress = startExternal
                    ? -SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                    : 0.0D;
            double maxProgress = endExternal
                    ? projected.length + SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                    : projected.length;
            progress = Mth.clamp(
                    progress, minProgress, maxProgress);
        } else {
            progress = initialSmallSwitchProgress(
                    cart, context, projected.routeIndex,
                    cart.getX(), cart.getZ());
        }

        SmallSwitchSample sample = sampleSmallSwitchBridgeAtProgress(
                context, projected.routeIndex, progress);
        data.putString(SMALL_SWITCH_CONTEXT_KEY, contextKey);
        data.putInt(SMALL_SWITCH_ROUTE_KEY, projected.routeIndex);
        data.putDouble(SMALL_SWITCH_PROGRESS_KEY, progress);
        data.putLong(SMALL_SWITCH_TICK_KEY, now);
        return sample;
    }

    private static int smallSwitchRouteForPose(AbstractMinecart cart,
                                               Context context,
                                               float fallbackYaw) {
        var data = cart.getPersistentData();
        String contextKey = linearContextKey(context);
        if (data.contains(SMALL_SWITCH_CONTEXT_KEY)
                && data.contains(SMALL_SWITCH_ROUTE_KEY)
                && contextKey.equals(
                        data.getString(SMALL_SWITCH_CONTEXT_KEY))) {
            int routeIndex = data.getInt(SMALL_SWITCH_ROUTE_KEY);
            if (routeIndex >= SMALL_SWITCH_ROUTE_STRAIGHT
                    && routeIndex <= SMALL_SWITCH_ROUTE_BRANCH) {
                return routeIndex;
            }
        }

        SmallSwitchSample straight = projectSmallSwitch(
                context, SMALL_SWITCH_ROUTE_STRAIGHT,
                cart.getX(), cart.getZ());
        SmallSwitchSample branch = projectSmallSwitch(
                context, SMALL_SWITCH_ROUTE_BRANCH,
                cart.getX(), cart.getZ());
        double straightDistance = straight == null
                ? Double.POSITIVE_INFINITY
                : horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        straight.targetHorizontal);
        double branchDistance = branch == null
                ? Double.POSITIVE_INFINITY
                : horizontalDistanceSq(
                        cart.getX(), cart.getZ(),
                        branch.targetHorizontal);

        if (branchDistance
                + SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                        * SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                < straightDistance) {
            return SMALL_SWITCH_ROUTE_BRANCH;
        }
        if (straightDistance
                + SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                        * SMALL_SWITCH_ROUTE_SELECTION_EPSILON
                < branchDistance) {
            return SMALL_SWITCH_ROUTE_STRAIGHT;
        }
        return stateActive(context.state)
                ? SMALL_SWITCH_ROUTE_BRANCH
                : SMALL_SWITCH_ROUTE_STRAIGHT;
    }

    /**
     * STEP_9_3F_T1A_R9_BIDIRECTIONAL_ENDPOINT_BRIDGE
     *
     * Recover unclamped progress when a fresh Small-switch session begins while
     * the cart center is already inside an endpoint guide block but has not yet
     * crossed the mathematical p0/p2/p5 center. This is the exact r8 pop seen in
     * both directions: the old projection returned 0/length and setPos() jumped
     * roughly 0.5-0.6 blocks in one tick.
     */
    private static double initialSmallSwitchProgress(
            AbstractMinecart cart,
            Context context,
            int routeIndex,
            double worldX,
            double worldZ) {
        SmallSwitchSample projected = projectSmallSwitch(
                context, routeIndex, worldX, worldZ);
        if (projected == null) {
            return 0.0D;
        }

        SmallSwitchSample start = sampleSmallSwitchAtProgress(
                context, routeIndex, 0.0D);
        SmallSwitchSample end = sampleSmallSwitchAtProgress(
                context, routeIndex, projected.length);
        if (start == null || end == null) {
            return projected.progress;
        }

        double startDx = worldX - start.targetHorizontal.x;
        double startDz = worldZ - start.targetHorizontal.z;
        double startAlong =
                startDx * start.tangent.x + startDz * start.tangent.z;
        double startPerpSq = Math.max(
                0.0D,
                startDx * startDx + startDz * startDz
                        - startAlong * startAlong);

        if (startAlong < 0.0D
                && -startAlong <= SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                && startPerpSq
                        <= SMALL_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                                * SMALL_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                && hasExternalRailAtSmallSwitchEndpoint(
                        cart, context, routeIndex, false)) {
            return startAlong;
        }

        double endDx = worldX - end.targetHorizontal.x;
        double endDz = worldZ - end.targetHorizontal.z;
        double endAlong =
                endDx * end.tangent.x + endDz * end.tangent.z;
        double endPerpSq = Math.max(
                0.0D,
                endDx * endDx + endDz * endDz
                        - endAlong * endAlong);

        if (endAlong > 0.0D
                && endAlong <= SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE
                && endPerpSq
                        <= SMALL_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                                * SMALL_SWITCH_ENDPOINT_BRIDGE_MAX_PERP
                && hasExternalRailAtSmallSwitchEndpoint(
                        cart, context, routeIndex, true)) {
            return projected.length + endAlong;
        }

        return projected.progress;
    }

    /**
     * Movement-owned endpoint tangent bridge. The mathematical switch route is
     * unchanged; only progress immediately outside an endpoint is represented
     * instead of being clamped to the endpoint center.
     */
    private static SmallSwitchSample sampleSmallSwitchBridgeAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        SmallSwitchSample start = sampleSmallSwitchAtProgress(
                context, routeIndex, 0.0D);
        if (start == null) {
            return null;
        }

        if (progress < 0.0D) {
            double extension = Math.max(
                    -SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE, progress);
            Vec3 target = start.targetHorizontal.add(
                    start.tangent.scale(extension));
            return new SmallSwitchSample(
                    target,
                    start.tangent,
                    progress,
                    start.length,
                    routeIndex);
        }

        SmallSwitchSample end = sampleSmallSwitchAtProgress(
                context, routeIndex, start.length);
        if (end == null) {
            return null;
        }

        if (progress > end.length) {
            double extension = Math.min(
                    SMALL_SWITCH_ENDPOINT_BRIDGE_CAPTURE,
                    progress - end.length);
            Vec3 target = end.targetHorizontal.add(
                    end.tangent.scale(extension));
            return new SmallSwitchSample(
                    target,
                    end.tangent,
                    progress,
                    end.length,
                    routeIndex);
        }

        return sampleSmallSwitchAtProgress(
                context, routeIndex, progress);
    }

    private static double bodySignRelativeToTangent(
            float bodyYaw,
            Vec3 tangent) {
        double yawRadians = Math.toRadians(bodyYaw);
        double frontX = Math.sin(yawRadians);
        double frontZ = -Math.cos(yawRadians);
        double dot = frontX * tangent.x + frontZ * tangent.z;
        return dot >= 0.0D ? 1.0D : -1.0D;
    }

    /**
     * STEP_9_3F_T1A_R8_SMALL_SWITCH_POSE_STRADDLE
     *
     * Visual/facing sampling only. Movement samples remain clamped to the switch
     * route. When a virtual bogie lies just outside p0/p2/p5, extend from the
     * analytic endpoint along its exact tangent instead of pinning that bogie to
     * the endpoint. This preserves wheelbase/chord continuity and removes the
     * entry "pop" caused by an instantly shortened virtual bogie span.
     */
    private static SmallSwitchSample sampleSmallSwitchPoseAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        SmallSwitchSample start = sampleSmallSwitchAtProgress(
                context, routeIndex, 0.0D);
        if (start == null) {
            return null;
        }

        if (progress < 0.0D) {
            double extension = Math.max(
                    -POSE_ENDPOINT_EXTENSION, progress);
            Vec3 target = start.targetHorizontal.add(
                    start.tangent.scale(extension));
            return new SmallSwitchSample(
                    target,
                    start.tangent,
                    progress,
                    start.length,
                    routeIndex);
        }

        SmallSwitchSample end = sampleSmallSwitchAtProgress(
                context, routeIndex, start.length);
        if (end == null) {
            return null;
        }

        if (progress > end.length) {
            double extension = Math.min(
                    POSE_ENDPOINT_EXTENSION,
                    progress - end.length);
            Vec3 target = end.targetHorizontal.add(
                    end.tangent.scale(extension));
            return new SmallSwitchSample(
                    target,
                    end.tangent,
                    progress,
                    end.length,
                    routeIndex);
        }

        return sampleSmallSwitchAtProgress(
                context, routeIndex, progress);
    }

    private static SmallSwitchSample projectSmallSwitch(
            Context context,
            int routeIndex,
            double worldX,
            double worldZ) {
        double progress = projectedSmallSwitchProgress(
                context, routeIndex, worldX, worldZ);
        return sampleSmallSwitchAtProgress(
                context, routeIndex, progress);
    }

    private static double projectedSmallSwitchProgress(
            Context context,
            int routeIndex,
            double worldX,
            double worldZ) {
        double[] local = toCanonical(
                context.root, context.facing, worldX, worldZ);

        if (routeIndex == SMALL_SWITCH_ROUTE_STRAIGHT) {
            double side = smallSwitchSide(context.spec.id());
            // True mainline is p2=(0,-2) -> p5=(side*2,-2).
            return Mth.clamp(
                    side * local[0],
                    0.0D,
                    SMALL_SWITCH_ROUTE_LENGTH);
        }

        double side = smallSwitchSide(context.spec.id());
        double t = Mth.clamp(
                Math.atan2(
                        -local[1],
                        SMALL_SWITCH_RADIUS - side * local[0]),
                0.0D,
                Math.PI * 0.5D);
        return SMALL_SWITCH_RADIUS * t;
    }

    private static SmallSwitchSample sampleSmallSwitchAtProgress(
            Context context,
            int routeIndex,
            double progress) {
        if (routeIndex == SMALL_SWITCH_ROUTE_STRAIGHT) {
            double p = Mth.clamp(
                    progress, 0.0D, SMALL_SWITCH_ROUTE_LENGTH);
            double side = smallSwitchSide(context.spec.id());

            // Outside/mainline straight: p2=(0,-2) -> p5=(side*2,-2).
            Vec3 target = fromCanonical(
                    context.root,
                    context.facing,
                    side * p,
                    -2.0D);
            Vec3 tangent = rotateCanonicalVector(
                    context.facing,
                    side,
                    0.0D);
            return new SmallSwitchSample(
                    target, tangent, p,
                    SMALL_SWITCH_ROUTE_LENGTH,
                    SMALL_SWITCH_ROUTE_STRAIGHT);
        }

        double total = SMALL_SWITCH_RADIUS * Math.PI * 0.5D;
        double p = Mth.clamp(progress, 0.0D, total);
        double t = p / SMALL_SWITCH_RADIUS;
        double side = smallSwitchSide(context.spec.id());

        double localX =
                side * SMALL_SWITCH_RADIUS * (1.0D - Math.cos(t));
        double localZ =
                -SMALL_SWITCH_RADIUS * Math.sin(t);
        Vec3 target = fromCanonical(
                context.root, context.facing, localX, localZ);
        Vec3 tangent = rotateCanonicalVector(
                context.facing,
                side * Math.sin(t),
                -Math.cos(t));
        return new SmallSwitchSample(
                target, tangent, p, total,
                SMALL_SWITCH_ROUTE_BRANCH);
    }

    private static double smallSwitchSide(String id) {
        if (TARGET_SMALL_RIGHT_SWITCH.equals(id)) {
            return 1.0D;
        }
        if (TARGET_SMALL_LEFT_SWITCH.equals(id)) {
            return -1.0D;
        }
        throw new IllegalArgumentException(
                "Not a target Small switch: " + id);
    }

    private static void applySmallSwitchP2ExitHandoff(
            AbstractMinecart cart,
            Context context,
            SmallSwitchSample sample,
            double speed,
            Vec3 postRailMotion) {
        var data = cart.getPersistentData();

        // p2 is progress 0 on the true outside/mainline straight. When the
        // release window first opens the cart may still be a few hundredths of
        // a block before the exact endpoint, so seed the extension with the
        // negative remaining route progress instead of jumping to p2.
        double extension = data.contains(SMALL_SWITCH_EXIT_HANDOFF_KEY)
                ? data.getDouble(SMALL_SWITCH_EXIT_HANDOFF_KEY)
                : -Math.max(0.0D, sample.progress);

        extension += Math.max(0.0D, speed);

        SmallSwitchSample endpoint = sampleSmallSwitchAtProgress(
                context, SMALL_SWITCH_ROUTE_STRAIGHT, 0.0D);

        Direction outward = null;
        for (LegacyBatchTrackSpec.Endpoint candidate
                : context.spec.endpoints()) {
            if (candidate.part() == 2) {
                outward = smallSwitchEndpointOutward(context, candidate);
                break;
            }
        }

        if (endpoint == null || outward == null) {
            clearSmallSwitchSession(cart, context);
            return;
        }

        double applied = Math.min(
                extension, SMALL_SWITCH_EXIT_HANDOFF_CLEARANCE);
        double stepX = outward.getStepX();
        double stepZ = outward.getStepZ();

        cart.setPos(
                endpoint.targetHorizontal.x + stepX * applied,
                context.root.getY() + RAIL_SURFACE_Y,
                endpoint.targetHorizontal.z + stepZ * applied);
        cart.setDeltaMovement(
                stepX * speed,
                postRailMotion.y,
                stepZ * speed);

        if (extension >= SMALL_SWITCH_EXIT_HANDOFF_CLEARANCE) {
            clearSmallSwitchSession(cart, context);
            return;
        }

        data.putString(
                SMALL_SWITCH_CONTEXT_KEY, linearContextKey(context));
        data.putInt(
                SMALL_SWITCH_ROUTE_KEY, SMALL_SWITCH_ROUTE_STRAIGHT);
        data.putDouble(SMALL_SWITCH_PROGRESS_KEY, 0.0D);
        data.putDouble(SMALL_SWITCH_EXIT_HANDOFF_KEY, extension);
        data.putLong(
                SMALL_SWITCH_TICK_KEY, cart.level().getGameTime());
    }

    private static boolean hasExternalRailAtSmallSwitchEndpoint(
            AbstractMinecart cart,
            Context context,
            int routeIndex,
            boolean atEnd) {
        int endpointPart;
        if (routeIndex == SMALL_SWITCH_ROUTE_STRAIGHT) {
            // Straight route is p2 (progress 0) <-> p5 (progress length).
            endpointPart = atEnd ? 5 : 2;
        } else {
            // Diverging route remains p0 (progress 0) <-> p5 (progress length).
            endpointPart = atEnd ? 5 : 0;
        }

        for (LegacyBatchTrackSpec.Endpoint endpoint
                : context.spec.endpoints()) {
            if (endpoint.part() != endpointPart) {
                continue;
            }
            BlockPos endpointPos = context.spec.endpointPosition(
                    context.root, context.facing, endpoint);
            // STEP_9_3F_T1A_R3_P2_EXIT_HANDOFF
            //
            // t1a-r2 corrected the movement line to the real outside
            // p5<->p2 mainline and corrected placement snapping at p2.
            // The endpoint-release check was still using the OLD spec p2
            // outward direction, so both Left and Right reached p2 correctly
            // and then stopped as if they hit a wall.
            //
            // Mirror the same narrow p2 connector correction used by the
            // placement layer. p5 and p0 keep their existing spec metadata.
            Direction outward = smallSwitchEndpointOutward(
                    context, endpoint);
            BlockPos next = endpointPos.relative(outward);
            BlockState state = cart.level().getBlockState(next);
            if (BaseRailBlock.isRail(state)) {
                return true;
            }
            return BaseRailBlock.isRail(
                    cart.level().getBlockState(next.below()));
        }
        return false;
    }

    private static Direction smallSwitchEndpointOutward(
            Context context,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        if (endpoint.part() == 2) {
            String id = context.spec.id();
            if (TARGET_SMALL_LEFT_SWITCH.equals(id)) {
                return context.spec.rotateDirection(
                        context.facing, Direction.EAST);
            }
            if (TARGET_SMALL_RIGHT_SWITCH.equals(id)) {
                return context.spec.rotateDirection(
                        context.facing, Direction.WEST);
            }
        }
        return context.spec.rotateDirection(
                context.facing, endpoint.outward());
    }

    private static void clearSmallSwitchSession(
            AbstractMinecart cart,
            Context context) {
        var data = cart.getPersistentData();
        if (data.contains(SMALL_SWITCH_CONTEXT_KEY)
                && !linearContextKey(context).equals(
                        data.getString(SMALL_SWITCH_CONTEXT_KEY))) {
            return;
        }
        data.remove(SMALL_SWITCH_CONTEXT_KEY);
        data.remove(SMALL_SWITCH_ROUTE_KEY);
        data.remove(SMALL_SWITCH_PROGRESS_KEY);
        data.remove(SMALL_SWITCH_TICK_KEY);
        data.remove(SMALL_SWITCH_BODY_SIGN_KEY);
        data.remove(SMALL_SWITCH_EXIT_HANDOFF_KEY);
    }

    private static void applyCurve(AbstractMinecart cart,
                                   Context context,
                                   Vec3 preRailMotion,
                                   boolean powered,
                                   boolean serviceBrake,
                                   boolean handBrake,
                                   double maxSpeed) {
        CurveSample projectedSample = sampleCurve(context, cart.getX(), cart.getZ());
        if (projectedSample == null) {
            clearCurveSession(cart, context);
            return;
        }

        Vec3 postRailMotion = cart.getDeltaMovement();
        double speed = correctedSpeed(
                preRailMotion, postRailMotion, projectedSample.tangent,
                powered, false, serviceBrake, handBrake, maxSpeed);
        double sign = travelSign(
                preRailMotion, postRailMotion, projectedSample.tangent, cart.getYRot());

        CurveSample sample = projectedSample;
        double quarterRadius = quarterCurveRadius(context.spec.id());
        if (quarterRadius > 0.0D) {
            sample = latchedQuarterCurveSample(
                    cart, context, projectedSample, preRailMotion,
                    postRailMotion, sign, quarterRadius);
        } else {
            OffsetDefinition targetFortyFive =
                    activeTargetFortyFiveSwitchDefinition(context);
            if (targetFortyFive != null) {
                sample = latchedFortyFiveCurveSample(
                        cart, context, projectedSample, preRailMotion,
                        postRailMotion, sign,
                        targetFortyFive.lateral, targetFortyFive.forward);
            } else {
                clearCurveSession(cart, context);
            }
        }

        if (sample == null) {
            return;
        }

        // Hand the locomotive back at an external connector. For the original
        // TC4.5 Medium 45 switch, do NOT release 0.10 blocks before the real
        // mathematical endpoint: that one vanilla-guide tick was the remaining
        // visible pop in t10. Carry the root-owned arc through the exact endpoint,
        // seed the attached diagonal with the same continuous progress, then let
        // the diagonal root take over on the following tick.
        boolean originalFortyFive = usesOriginalTraincraftFortyFiveMath(context);
        boolean leavingStart = sample.distanceFromStart <= ENDPOINT_RELEASE_DISTANCE
                && sign < 0.0D;
        boolean leavingEnd = sign > 0.0D
                && (originalFortyFive
                        ? sample.distanceToEnd <= 0.0D
                        : sample.distanceToEnd <= ENDPOINT_RELEASE_DISTANCE);

        if (leavingStart || leavingEnd) {
            if (originalFortyFive && leavingEnd) {
                Context diagonal = findOriginalTraincraftSharedDiagonal(cart, context);
                if (diagonal != null) {
                    LinearRoute[] routes = linearRoutes(diagonal);
                    if (routes.length > 0) {
                        int routeIndex = virtualEndpointHandoffRoute(context, diagonal);
                        if (routeIndex < 0 || routeIndex >= routes.length) {
                            routeIndex = 0;
                        }
                        double handoffProgress = routes[routeIndex].projectedProgress(
                                sample.targetHorizontal.x,
                                sample.targetHorizontal.z);
                        armPendingVirtualHandoff(
                                cart, diagonal, routeIndex, handoffProgress);

                        // Preserve this tick's exact root-owned position/motion.
                        // The next diagonal tick advances from handoffProgress
                        // instead of re-projecting vanilla's cardinal guide snap.
                        cart.setPos(sample.targetHorizontal.x,
                                context.root.getY() + RAIL_SURFACE_Y,
                                sample.targetHorizontal.z);
                        cart.setDeltaMovement(
                                sample.tangent.x * sign * speed,
                                postRailMotion.y,
                                sample.tangent.z * sign * speed);
                    }
                }
            }
            clearCurveSession(cart, context);
            return;
        }

        cart.setPos(sample.targetHorizontal.x,
                context.root.getY() + RAIL_SURFACE_Y,
                sample.targetHorizontal.z);
        cart.setDeltaMovement(
                sample.tangent.x * sign * speed,
                postRailMotion.y,
                sample.tangent.z * sign * speed);
    }

    private static CurveSample latchedQuarterCurveSample(AbstractMinecart cart,
                                                         Context context,
                                                         CurveSample projectedSample,
                                                         Vec3 preRailMotion,
                                                         Vec3 postRailMotion,
                                                         double sign,
                                                         double radius) {
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = curveContextKey(context);

        boolean sameSession = data.contains(CURVE_CONTEXT_KEY)
                && data.contains(CURVE_PROGRESS_KEY)
                && data.contains(CURVE_TICK_KEY)
                && contextKey.equals(data.getString(CURVE_CONTEXT_KEY));

        long lastTick = sameSession ? data.getLong(CURVE_TICK_KEY) : Long.MIN_VALUE;
        sameSession = sameSession
                && now >= lastTick
                && now - lastTick <= CURVE_SESSION_MAX_TICK_GAP;

        double total = radius * Math.PI * 0.5D;
        double progress = projectedSample.distanceFromStart;

        // Forward-only first-tick bridge at the START connector. Runtime r5
        // showed the first Big Curve guide cell can own the cart while its real
        // center is still roughly half a block before the mathematical arc
        // start. Mirror the proven reverse bridge: retain that real in-cell
        // position on the exact start tangent and advance negative progress
        // smoothly toward zero before entering the circle.
        if (!sameSession && sign > 0.0D) {
            CurveSample start = sampleQuarterCurveAtProgress(
                    context, 0.0D, radius);
            double dx = cart.getX() - start.targetHorizontal.x;
            double dz = cart.getZ() - start.targetHorizontal.z;
            double along = dx * start.tangent.x + dz * start.tangent.z;
            double perpX = dx - start.tangent.x * along;
            double perpZ = dz - start.tangent.z * along;
            double perp = Math.hypot(perpX, perpZ);

            if (along < 0.0D
                    && along >= -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION
                    && perp <= CURVE_FORWARD_ENTRY_MAX_PERP) {
                progress = along;
            }
        }

        // Reverse-only first-tick bridge at the END connector. The hidden final
        // guide cell becomes the direct rail slightly before the locomotive
        // reaches the mathematical arc endpoint. r4 therefore snapped from
        // about X=133.03 to X=132.50 at only ~0.035 blocks/tick. Once the batch
        // cell actually owns the cart, retain its real in-cell position along
        // the endpoint tangent instead of clamping immediately to the circle.
        if (!sameSession && sign < 0.0D) {
            CurveSample end = sampleQuarterCurveAtProgress(
                    context, total, radius);
            double dx = cart.getX() - end.targetHorizontal.x;
            double dz = cart.getZ() - end.targetHorizontal.z;
            double along = dx * end.tangent.x + dz * end.tangent.z;
            double perpX = dx - end.tangent.x * along;
            double perpZ = dz - end.tangent.z * along;
            double perp = Math.hypot(perpX, perpZ);

            if (along > 0.0D
                    && along <= CURVE_REVERSE_ENTRY_TANGENT_EXTENSION
                    && perp <= CURVE_REVERSE_ENTRY_MAX_PERP) {
                progress = total + along;
            }
        }

        if (sameSession) {
            double storedProgress = data.getDouble(CURVE_PROGRESS_KEY);
            long deltaTicks = Math.max(0L, Math.min(2L, now - lastTick));

            double horizontalSpeed = Math.hypot(preRailMotion.x, preRailMotion.z);
            if (horizontalSpeed <= MOTION_EPSILON) {
                horizontalSpeed = Math.hypot(postRailMotion.x, postRailMotion.z);
            }

            double predicted = storedProgress
                    + sign * horizontalSpeed * (double) deltaTicks;

            // While either endpoint bridge is being consumed, the clamped
            // circle projection reports 0/start or total/end. Do not let that
            // projection drag authoritative progress to the arc endpoint. Once
            // inside the circle, retain the r2/r4 correction behavior.
            if (storedProgress < 0.0D || storedProgress > total) {
                progress = predicted;
            } else {
                double projected = projectedSample.distanceFromStart;
                if (Math.abs(projected - predicted)
                        <= CURVE_PROGRESS_CORRECTION_LIMIT) {
                    progress = predicted * 0.95D + projected * 0.05D;
                } else {
                    progress = predicted;
                }
            }
        }

        if (progress < -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION
                || progress > total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION) {
            clearCurveSession(cart, context);
            return projectedSample;
        }

        progress = Mth.clamp(
                progress, -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION,
                total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION);
        data.putString(CURVE_CONTEXT_KEY, contextKey);
        data.putDouble(CURVE_PROGRESS_KEY, progress);
        data.putLong(CURVE_TICK_KEY, now);

        return sampleQuarterCurveAtMovementProgress(
                context, progress, radius);
    }

    private static CurveSample latchedFortyFiveCurveSample(
            AbstractMinecart cart,
            Context context,
            CurveSample projectedSample,
            Vec3 preRailMotion,
            Vec3 postRailMotion,
            double sign,
            double lateral,
            double forward) {
        double absLateral = Math.abs(lateral);
        if (absLateral < 1.0E-9D || forward <= 0.0D) {
            clearCurveSession(cart, context);
            return projectedSample;
        }

        double total = fortyFiveTotalLength(context, lateral, forward);

        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = curveContextKey(context);

        boolean sameSession = data.contains(CURVE_CONTEXT_KEY)
                && data.contains(CURVE_PROGRESS_KEY)
                && data.contains(CURVE_TICK_KEY)
                && contextKey.equals(data.getString(CURVE_CONTEXT_KEY));
        long lastTick = sameSession ? data.getLong(CURVE_TICK_KEY) : Long.MIN_VALUE;
        sameSession = sameSession
                && now >= lastTick
                && now - lastTick <= CURVE_SESSION_MAX_TICK_GAP;

        double progress = projectedSample.distanceFromStart;

        if (!sameSession && sign > 0.0D) {
            CurveSample start = sampleFortyFiveCurveAtProgress(
                    context, 0.0D, lateral, forward);
            double dx = cart.getX() - start.targetHorizontal.x;
            double dz = cart.getZ() - start.targetHorizontal.z;
            double along = dx * start.tangent.x + dz * start.tangent.z;
            double perpX = dx - start.tangent.x * along;
            double perpZ = dz - start.tangent.z * along;
            double perp = Math.hypot(perpX, perpZ);
            if (along < 0.0D
                    && along >= -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION
                    && perp <= CURVE_FORWARD_ENTRY_MAX_PERP) {
                progress = along;
            }
        }

        if (!sameSession && sign < 0.0D) {
            CurveSample end = sampleFortyFiveCurveAtProgress(
                    context, total, lateral, forward);
            double dx = cart.getX() - end.targetHorizontal.x;
            double dz = cart.getZ() - end.targetHorizontal.z;
            double along = dx * end.tangent.x + dz * end.tangent.z;
            double perpX = dx - end.tangent.x * along;
            double perpZ = dz - end.tangent.z * along;
            double perp = Math.hypot(perpX, perpZ);
            if (along > 0.0D
                    && along <= CURVE_REVERSE_ENTRY_TANGENT_EXTENSION
                    && perp <= CURVE_REVERSE_ENTRY_MAX_PERP) {
                progress = total + along;
            }
        }

        if (sameSession) {
            double storedProgress = data.getDouble(CURVE_PROGRESS_KEY);
            long deltaTicks = Math.max(0L, Math.min(2L, now - lastTick));
            double horizontalSpeed = Math.hypot(preRailMotion.x, preRailMotion.z);
            if (horizontalSpeed <= MOTION_EPSILON) {
                horizontalSpeed = Math.hypot(postRailMotion.x, postRailMotion.z);
            }
            double predicted = storedProgress
                    + sign * horizontalSpeed * (double) deltaTicks;

            if (storedProgress < 0.0D || storedProgress > total) {
                progress = predicted;
            } else {
                double projected = projectedSample.distanceFromStart;
                if (Math.abs(projected - predicted)
                        <= CURVE_PROGRESS_CORRECTION_LIMIT) {
                    progress = predicted * 0.95D + projected * 0.05D;
                } else {
                    progress = predicted;
                }
            }
        }

        if (progress < -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION
                || progress > total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION) {
            clearCurveSession(cart, context);
            return projectedSample;
        }

        progress = Mth.clamp(
                progress,
                -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION,
                total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION);
        data.putString(CURVE_CONTEXT_KEY, contextKey);
        data.putDouble(CURVE_PROGRESS_KEY, progress);
        data.putLong(CURVE_TICK_KEY, now);

        return sampleFortyFiveCurveAtMovementProgress(
                context, progress, lateral, forward);
    }

    private static String curveContextKey(Context context) {
        return context.spec.id()
                + "|" + context.root.asLong()
                + "|" + context.facing.ordinal();
    }

    private static void clearCurveSession(AbstractMinecart cart, Context context) {
        var data = cart.getPersistentData();
        if (!data.contains(CURVE_CONTEXT_KEY)) {
            return;
        }

        String current = data.getString(CURVE_CONTEXT_KEY);
        if (!current.equals(curveContextKey(context))) {
            return;
        }

        data.remove(CURVE_CONTEXT_KEY);
        data.remove(CURVE_PROGRESS_KEY);
        data.remove(CURVE_TICK_KEY);
    }

    /**
     * Step 9.3c-t3 generalized continuous diagonal path.
     *
     * t2 selected the correct diagonal axis but re-projected from vanilla's
     * stepped guide position every tick. Runtime proof showed that this could
     * pin a locomotive near the first crossing cell and could choose the other
     * diagonal after a stop/reverse. t3 therefore latches the selected route and
     * advances a continuous progress value from pre-rail motion. Vanilla guide
     * positions are used only as a small correction when they agree with the
     * predicted continuous position.
     */
    private static void applyLinearDiagonal(AbstractMinecart cart,
                                            Context context,
                                            Vec3 preRailMotion,
                                            boolean powered,
                                            boolean serviceBrake,
                                            boolean handBrake,
                                            double maxSpeed,
                                            int requestedDirection,
                                            float trainFacingYaw) {
        LinearSample sample = sampleLinearDiagonal(
                cart, context, preRailMotion, trainFacingYaw,
                requestedDirection, powered);
        if (sample == null) {
            return;
        }

        Vec3 postRailMotion = cart.getDeltaMovement();
        double speed = correctedSpeed(
                preRailMotion, postRailMotion, sample.route.tangent,
                powered, false, serviceBrake, handBrake, maxSpeed);
        double sign = sample.travelSign;

        // STEP_9_3E_T5_R8E_BRAKE_ZERO_DRIFT_LOCK
        //
        // r8d flight-recorder proof showed the brake pipeline itself is working:
        // horizontal motion becomes exactly 0.000000. The remaining "slow
        // movement" is positional only: sampleLinearDiagonal's 90/10 progress
        // correction keeps nudging setPos() toward the projected guide position
        // for several ticks even though delta movement is already zero.
        //
        // When either service brake or handbrake has reduced the custom linear
        // speed to zero, freeze X/Z exactly where the locomotive is now and
        // synchronize the latched progress to that real world position. Do not
        // perform endpoint handoff or any guide correction until the brake is
        // released and horizontal speed becomes non-zero again.
        if ((serviceBrake || handBrake) && speed <= MOTION_EPSILON) {
            double frozenProgress = Mth.clamp(
                    sample.route.projectedProgress(cart.getX(), cart.getZ()),
                    0.0D,
                    sample.route.length);

            var brakeData = cart.getPersistentData();
            brakeData.putString(LINEAR_CONTEXT_KEY, linearContextKey(context));
            brakeData.putInt(LINEAR_ROUTE_KEY, sample.route.routeIndex);
            brakeData.putDouble(LINEAR_PROGRESS_KEY, frozenProgress);
            brakeData.putLong(LINEAR_TICK_KEY, cart.level().getGameTime());

            // A stopped locomotive must not carry a deferred one-tick handoff
            // settle into the later brake-release tick.
            brakeData.remove(LINEAR_ATOMIC_SETTLE_CONTEXT_KEY);
            brakeData.remove(LINEAR_ATOMIC_SETTLE_TICK_KEY);

            cart.setDeltaMovement(0.0D, postRailMotion.y, 0.0D);
            return;
        }

        boolean leavingStart = sample.progress <= ENDPOINT_RELEASE_DISTANCE && sign < 0.0D;
        boolean leavingEnd = sample.progress >= sample.route.length - ENDPOINT_RELEASE_DISTANCE
                && sign > 0.0D;

        // Step 9.3c-t4-r7d: release only when a real adjoining rail exists at
        // the route's logical endpoint. If the user has not attached the next
        // diagonal piece yet, hold just inside the rendered rail head instead of
        // dropping to vanilla/air and bouncing back through the hidden guide.
        if (leavingStart || leavingEnd) {
            boolean atEnd = leavingEnd;
            if (hasExternalRailAtLinearEndpoint(cart, context, sample.route, atEnd)) {
                // STEP_9_3E_T5_R8C_ATOMIC_LINEAR_HANDOFF
                //
                // r8b proved the mathematical routes are now correct, but the
                // old endpoint release still cleared the current continuous
                // session and returned to vanilla rail motion for one or more
                // ticks. The modern compatibility cells at the crossing ends are
                // cardinal (east_west / north_south), so that gap produced the
                // identical sideways pull on all four arms:
                //
                //   crossing diagonal -> cardinal guide -> next diagonal
                //
                // TC4.5 never had that gap: lastTrack ownership handed directly
                // from one Traincraft rail owner to the next. Where the attached
                // rail is another continuous batch diagonal/crossing, perform
                // that ownership transfer in THIS SAME tick. Vanilla remains the
                // fallback only for a genuinely non-continuous adjoining rail.
                Context handoff = findNearbyLinearContext(
                        cart, cart.blockPosition(), context, preRailMotion);
                if (handoff != null
                        && isContinuousLinearDiagonal(handoff.spec.id())) {
                    boolean ordinarySmallToSmall =
                            "track_diagonal_straight_small".equals(context.spec.id())
                            && "track_diagonal_straight_small".equals(
                                    handoff.spec.id())
                            && !stateBooleanByName(
                                    context.state, "original_visual_root")
                            && !stateBooleanByName(
                                    handoff.state, "original_visual_root");

                    clearLinearSession(cart, context);

                    if (ordinarySmallToSmall) {
                        var handoffData = cart.getPersistentData();
                        handoffData.putString(
                                LINEAR_ATOMIC_SETTLE_CONTEXT_KEY,
                                linearContextKey(handoff));
                        handoffData.putLong(
                                LINEAR_ATOMIC_SETTLE_TICK_KEY,
                                cart.level().getGameTime());
                    }

                    LinearSample next = sampleLinearDiagonal(
                            cart, handoff, preRailMotion, trainFacingYaw,
                            requestedDirection, powered);
                    if (next != null) {
                        cart.setPos(
                                next.targetHorizontal.x,
                                handoff.root.getY() + RAIL_SURFACE_Y,
                                next.targetHorizontal.z);
                        cart.setDeltaMovement(
                                next.route.tangent.x * next.travelSign * speed,
                                postRailMotion.y,
                                next.route.tangent.z * next.travelSign * speed);
                        return;
                    }

                    // The topology matcher already proved the next continuous
                    // owner. If sampling unexpectedly cannot initialize, do not
                    // let the cardinal compatibility RailShape steer the cart in
                    // the same tick; preserve the outgoing 45-degree tangent and
                    // let normal context acquisition retry on the next tick.
                    cart.setPos(
                            sample.targetHorizontal.x,
                            context.root.getY() + RAIL_SURFACE_Y,
                            sample.targetHorizontal.z);
                    cart.setDeltaMovement(
                            sample.route.tangent.x * sign * speed,
                            postRailMotion.y,
                            sample.route.tangent.z * sign * speed);
                    return;
                }

                clearLinearSession(cart, context);
                return;
            }

            double heldProgress = atEnd
                    ? Math.max(0.0D, sample.route.length - LINEAR_ENDPOINT_HOLD_INSET)
                    : Math.min(sample.route.length, LINEAR_ENDPOINT_HOLD_INSET);
            Vec3 held = sample.route.pointAt(heldProgress);
            cart.getPersistentData().putDouble(LINEAR_PROGRESS_KEY, heldProgress);
            cart.setPos(held.x,
                    context.root.getY() + RAIL_SURFACE_Y,
                    held.z);
            cart.setDeltaMovement(0.0D, postRailMotion.y, 0.0D);
            return;
        }

        cart.setPos(sample.targetHorizontal.x,
                context.root.getY() + RAIL_SURFACE_Y,
                sample.targetHorizontal.z);
        cart.setDeltaMovement(
                sample.route.tangent.x * sign * speed,
                postRailMotion.y,
                sample.route.tangent.z * sign * speed);
    }

    private static LinearSample sampleLinearDiagonal(AbstractMinecart cart,
                                                     Context context,
                                                     Vec3 preRailMotion,
                                                     float fallbackYaw,
                                                     int requestedDirection,
                                                     boolean powered) {
        LinearRoute[] routes = linearRoutes(context);
        if (routes.length == 0) {
            return null;
        }

        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        String contextKey = linearContextKey(context);

        boolean sameSession = data.contains(LINEAR_CONTEXT_KEY)
                && data.contains(LINEAR_ROUTE_KEY)
                && data.contains(LINEAR_PROGRESS_KEY)
                && data.contains(LINEAR_TICK_KEY)
                && contextKey.equals(data.getString(LINEAR_CONTEXT_KEY));

        long lastTick = sameSession ? data.getLong(LINEAR_TICK_KEY) : Long.MIN_VALUE;
        int storedRouteIndex = sameSession ? data.getInt(LINEAR_ROUTE_KEY) : -1;
        sameSession = sameSession
                && storedRouteIndex >= 0
                && storedRouteIndex < routes.length
                && now >= lastTick
                && now - lastTick <= LINEAR_SESSION_MAX_TICK_GAP;

        int pendingHandoffRoute = !sameSession
                ? pendingVirtualHandoffRoute(cart, context, now, routes.length)
                : -1;

        int routeIndex;
        if (sameSession) {
            routeIndex = storedRouteIndex;
        } else if (pendingHandoffRoute >= 0) {
            routeIndex = pendingHandoffRoute;
        } else {
            routeIndex = chooseFreshLinearRoute(
                    routes, cart.getX(), cart.getZ(), preRailMotion, fallbackYaw);
        }

        LinearRoute route = routes[routeIndex];
        double projected = route.projectedProgress(cart.getX(), cart.getZ());
        double perpendicular = route.perpendicularDistance(cart.getX(), cart.getZ());

        // A topology-confirmed virtual endpoint can capture even when the old
        // Manhattan guide is outside the normal acquisition band. The initial
        // lateral correction below still prevents a one-tick visual teleport.
        if (!sameSession
                && pendingHandoffRoute < 0
                && perpendicular > LINEAR_INITIAL_CAPTURE_MAX_PERP) {
            clearLinearSession(cart, context);
            return null;
        }

        double routeSign = resolveLinearTravelSign(
                cart, route, preRailMotion, fallbackYaw,
                requestedDirection, powered, sameSession);

        double pendingHandoffProgress = !sameSession && pendingHandoffRoute >= 0
                ? pendingVirtualHandoffProgress(cart, context, now)
                : Double.NaN;

        double progress;
        if (sameSession) {
            boolean atomicSettle = false;
            if (data.contains(LINEAR_ATOMIC_SETTLE_CONTEXT_KEY)
                    && data.contains(LINEAR_ATOMIC_SETTLE_TICK_KEY)
                    && contextKey.equals(
                            data.getString(LINEAR_ATOMIC_SETTLE_CONTEXT_KEY))) {
                long settleTick = data.getLong(LINEAR_ATOMIC_SETTLE_TICK_KEY);
                atomicSettle = now >= settleTick
                        && now - settleTick <= LINEAR_ATOMIC_SETTLE_MAX_TICK_GAP;
                data.remove(LINEAR_ATOMIC_SETTLE_CONTEXT_KEY);
                data.remove(LINEAR_ATOMIC_SETTLE_TICK_KEY);
            }

            if (atomicSettle) {
                // STEP_9_3E_T5_R8D_ONE_TICK_SMALL_HANDOFF_SETTLE
                //
                // r8c already moved ownership atomically, so this cart position
                // is the real post-vanilla/post-handoff world position on the
                // next Small. Replaying storedProgress + preRailMotion here was
                // the remaining little forward/back pop. Project once onto the
                // already-selected 45-degree route, then resume normal latched
                // progress on following ticks.
                progress = projected;
            } else {
                double storedProgress = data.getDouble(LINEAR_PROGRESS_KEY);
                long deltaTicks = Math.max(0L, Math.min(2L, now - lastTick));

                // r7d advances the continuous centerline by real horizontal speed.
                // Using a dot-product magnitude against the hidden cardinal guide
                // shortened every diagonal tick and let guide-side corrections win.
                double horizontalSpeed = Math.hypot(preRailMotion.x, preRailMotion.z);
                if (horizontalSpeed <= MOTION_EPSILON) {
                    Vec3 postRailMotion = cart.getDeltaMovement();
                    horizontalSpeed = Math.hypot(postRailMotion.x, postRailMotion.z);
                }

                double predicted = storedProgress
                        + routeSign * horizontalSpeed * (double) deltaTicks;

                // STEP_9_3E_T1_R1_STRICT_LASTTRACK_CROSSING_CONTINUITY
                // TC4.5 DIAGONAL_CROSSING cells were gags/proxies only: once
                // lastTrack selected the incoming 45-degree route, internal cells
                // had ZERO authority over progress. t1 still accepted a 10% guide
                // projection correction, which made the locomotive visibly surge
                // and hesitate as the nearest compatibility part changed. Crossing
                // sessions therefore advance only from their latched progress and
                // real horizontal speed. Other diagonal families keep the proven
                // correction behavior.
                if (TARGET_DIAGONAL_CROSSING.equals(context.spec.id())) {
                    progress = predicted;
                } else if (Math.abs(projected - predicted)
                        <= LINEAR_PROGRESS_CORRECTION_LIMIT) {
                    progress = predicted * 0.90D + projected * 0.10D;
                } else {
                    progress = predicted;
                }
            }
        } else if (Double.isFinite(pendingHandoffProgress)) {
            // STEP_9_3D_T10_R4_ORIGINAL_CENTERDIAGONAL_HANDOFF
            // TC4.5 entered moveOnTCDiagonal() from the bogie's CURRENT world
            // position. It did not replay an additional tick of travel from a
            // stored switch endpoint. The t10-r3 pending handoff could therefore
            // advance once in vanilla and a second time from the armed progress,
            // producing the remaining small forward pop. The route is already
            // topology-confirmed here, so seed progress from the cart's real
            // projected position and let the shared-junction recenter blend below
            // remove only the tiny perpendicular endpoint mismatch.
            progress = projected;
        } else {
            progress = projected;
        }

        if (progress < -LINEAR_ENDPOINT_EXTENSION
                || progress > route.length + LINEAR_ENDPOINT_EXTENSION) {
            clearLinearSession(cart, context);
            return null;
        }

        progress = Mth.clamp(
                progress,
                -LINEAR_ENDPOINT_EXTENSION,
                route.length + LINEAR_ENDPOINT_EXTENSION);

        data.putString(LINEAR_CONTEXT_KEY, contextKey);
        data.putInt(LINEAR_ROUTE_KEY, route.routeIndex);
        data.putDouble(LINEAR_PROGRESS_KEY, progress);
        data.putLong(LINEAR_TICK_KEY, now);
        data.putDouble(LINEAR_SIGN_KEY, routeSign);

        // Latch which end of the locomotive body points along the selected
        // crossing diagonal. Do not recompute this from vanilla RailShape yaw
        // while inside the crossing: reversing travel must make the locomotive
        // back through the crossing, not rotate the body 180 degrees.
        if (TARGET_DIAGONAL_CROSSING.equals(context.spec.id())
                && (!sameSession || !data.contains(LINEAR_BODY_SIGN_KEY))) {
            double yaw = Math.toRadians(fallbackYaw);
            double bodyForwardX = Math.sin(yaw);
            double bodyForwardZ = -Math.cos(yaw);
            double bodyDotRoute = bodyForwardX * route.tangent.x
                    + bodyForwardZ * route.tangent.z;
            data.putDouble(
                    LINEAR_BODY_SIGN_KEY,
                    bodyDotRoute >= 0.0D ? 1.0D : -1.0D);
        }

        clearPendingVirtualHandoff(cart, context);

        Vec3 target = route.pointAt(progress);
        target = originalTraincraftSharedJunctionRecenterTarget(
                cart, context, route, progress, target);
        if (!sameSession) {
            double correctionX = target.x - cart.getX();
            double correctionZ = target.z - cart.getZ();
            double correction = Math.hypot(correctionX, correctionZ);
            if (correction > LINEAR_INITIAL_LATERAL_STEP) {
                double scale = LINEAR_INITIAL_LATERAL_STEP / correction;
                target = new Vec3(
                        cart.getX() + correctionX * scale,
                        target.y,
                        cart.getZ() + correctionZ * scale);
            }
        }

        return new LinearSample(route, target, progress, routeSign);
    }

    /**
     * Step 9.3d-t10-r4 OG centerDiagonal continuity bridge.
     *
     * The exact TC4.5 3.75-radius endpoint is tangent-parallel to the attached
     * diagonal but is not numerically identical to the modern diagonal block
     * centerline. Original Traincraft let the gag-owned bogie carry that position
     * into moveOnTCDiagonal()/centerDiagonal(); it did not teleport to a second
     * independently projected path. Reproduce that ownership continuity with a
     * deterministic C1-smooth lateral recenter over the first block of the
     * connected diagonal. The offset is zero outside this tiny junction zone and
     * applies only when an ACTIVE original Medium-45 switch shares this exact
     * diagonal root.
     */
    private static Vec3 originalTraincraftSharedJunctionRecenterTarget(
            AbstractMinecart cart,
            Context diagonal,
            LinearRoute route,
            double progress,
            Vec3 canonicalTarget) {
        if (!isDiagonalStraightFamily(diagonal.spec.id())) {
            return canonicalTarget;
        }

        Context source = findOriginalTraincraftSharedSwitchForDiagonal(
                cart, diagonal);
        if (source == null) {
            return canonicalTarget;
        }

        int routeIndex = virtualEndpointHandoffRoute(source, diagonal);
        if (routeIndex < 0) {
            routeIndex = 0;
        }
        if (route.routeIndex != routeIndex) {
            return canonicalTarget;
        }

        OffsetDefinition definition =
                activeTargetFortyFiveSwitchDefinition(source);
        if (definition == null) {
            return canonicalTarget;
        }
        double total = fortyFiveTotalLength(
                source, definition.lateral, definition.forward);
        CurveSample end = sampleFortyFiveCurveAtProgress(
                source, total, definition.lateral, definition.forward);
        if (end == null) {
            return canonicalTarget;
        }

        double tangentDot = end.tangent.x * route.tangent.x
                + end.tangent.z * route.tangent.z;
        if (Math.abs(tangentDot) < ORIGINAL_TC45_HANDOFF_MIN_TANGENT_DOT) {
            return canonicalTarget;
        }

        double junctionProgress = route.projectedProgress(
                end.targetHorizontal.x, end.targetHorizontal.z);
        Vec3 projectedJunction = route.pointAt(junctionProgress);
        double offsetX = end.targetHorizontal.x - projectedJunction.x;
        double offsetZ = end.targetHorizontal.z - projectedJunction.z;
        double offsetMagnitude = Math.hypot(offsetX, offsetZ);
        if (offsetMagnitude <= MOTION_EPSILON
                || offsetMagnitude > ORIGINAL_TC45_HANDOFF_MAX_PERP) {
            return canonicalTarget;
        }

        double outwardSign = tangentDot >= 0.0D ? 1.0D : -1.0D;
        double distanceFromJunction =
                (progress - junctionProgress) * outwardSign;
        if (distanceFromJunction < 0.0D
                || distanceFromJunction > ORIGINAL_TC45_DIAGONAL_RECENTER_LENGTH) {
            return canonicalTarget;
        }

        double t = Mth.clamp(
                distanceFromJunction / ORIGINAL_TC45_DIAGONAL_RECENTER_LENGTH,
                0.0D, 1.0D);
        // Cubic smoothstep: weight=1 at the switch endpoint and 0 at the
        // canonical diagonal, with zero derivative at both ends. That preserves
        // the exact 45-degree endpoint tangent in both forward and reverse.
        double weight = 1.0D - (3.0D * t * t - 2.0D * t * t * t);
        return new Vec3(
                canonicalTarget.x + offsetX * weight,
                canonicalTarget.y,
                canonicalTarget.z + offsetZ * weight);
    }

    private static Context findOriginalTraincraftSharedSwitchForDiagonal(
            AbstractMinecart cart, Context diagonal) {
        BlockPos base = diagonal.root;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS;
                    dx <= ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS; dx++) {
                for (int dz = -ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS;
                        dz <= ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }
                    Context source = new Context(batch, state, pos);
                    if (!isOriginalTraincraftMediumFortyFiveSwitch(
                                    source.spec.id())
                            || !stateActive(source.state)) {
                        continue;
                    }
                    BlockPos sharedRoot = originalTraincraftSharedProxyRoot(source);
                    if (sharedRoot != null && sharedRoot.equals(diagonal.root)) {
                        return source;
                    }
                }
            }
        }
        return null;
    }

    private static double resolveLinearTravelSign(AbstractMinecart cart,
                                                  LinearRoute route,
                                                  Vec3 preRailMotion,
                                                  float fallbackYaw,
                                                  int requestedDirection,
                                                  boolean powered,
                                                  boolean sameSession) {
        Vec3 postRailMotion = cart.getDeltaMovement();
        double motionSign = travelSign(
                preRailMotion, postRailMotion, route.tangent, fallbackYaw);
        double commandedSign = commandedRouteSign(
                route, requestedDirection, fallbackYaw);
        double horizontalSpeed = Math.hypot(preRailMotion.x, preRailMotion.z);

        var data = cart.getPersistentData();
        double storedSign = 0.0D;
        if (sameSession && data.contains(LINEAR_SIGN_KEY)) {
            double raw = data.getDouble(LINEAR_SIGN_KEY);
            if (Math.abs(raw) > 0.5D) {
                storedSign = raw >= 0.0D ? 1.0D : -1.0D;
            }
        }

        if (storedSign == 0.0D) {
            return commandedSign != 0.0D ? commandedSign : motionSign;
        }

        if (powered && commandedSign != 0.0D) {
            if (commandedSign == storedSign) {
                // Same driver command: a one-tick vanilla guide reversal is not
                // allowed to flip the selected diagonal route.
                return storedSign;
            }

            // Genuine W<->S reversal: keep the old sign while the normal driver
            // controller brakes toward zero, then arm the opposite traversal.
            if (horizontalSpeed <= LINEAR_DIRECTION_REVERSAL_SPEED
                    || motionSign == commandedSign) {
                return commandedSign;
            }
            return storedSign;
        }

        // Coasting/no command retains the established route sign unless real
        // motion has crossed through a near-stop first.
        if (horizontalSpeed > LINEAR_DIRECTION_REVERSAL_SPEED
                && motionSign != storedSign) {
            return storedSign;
        }
        return motionSign;
    }

    private static double commandedRouteSign(LinearRoute route,
                                             int requestedDirection,
                                             float trainFacingYaw) {
        if (requestedDirection == 0) {
            return 0.0D;
        }

        double yaw = Math.toRadians(trainFacingYaw);
        double frontX = Math.sin(yaw);
        double frontZ = -Math.cos(yaw);
        double noseDot = frontX * route.tangent.x + frontZ * route.tangent.z;
        double noseSign = noseDot >= 0.0D ? 1.0D : -1.0D;
        return noseSign * (requestedDirection > 0 ? 1.0D : -1.0D);
    }

    private static boolean hasExternalRailAtLinearEndpoint(AbstractMinecart cart,
                                                           Context context,
                                                           LinearRoute route,
                                                           boolean atEnd) {
        int endpointPart = linearEndpointPart(context, route.routeIndex, atEnd);
        if (endpointPart < 0) {
            // Preserve pre-r7d release behavior for families without an explicit
            // route-to-endpoint map.
            return true;
        }

        for (LegacyBatchTrackSpec.Endpoint endpoint : context.spec.endpoints()) {
            if (endpoint.part() != endpointPart) {
                continue;
            }

            // STEP_9_3E_T5_R7_EXACT_OG_SMALL_ENDPOINT_RELEASE
            //
            // t5-r3/r5 placement restored the TC4.5 Small topology: adjacent
            // Small pieces meet at the SAME logical diagonal endpoint, with no
            // extra cardinal connector cell between them. The older r7d release
            // gate still checked only logicalEndpoint.relative(outward), so a
            // perfectly connected Small chain looked terminal and the locomotive
            // was clamped back to LINEAR_ENDPOINT_HOLD_INSET with zero motion.
            //
            // Check the exact endpoint topology first. This is endpoint-specific,
            // so a Small attached at the opposite end cannot make a true terminal
            // end release into air.
            if ("track_diagonal_straight_small".equals(context.spec.id())
                    && hasExactSmallNeighborAtEndpoint(cart, context, endpoint)) {
                return true;
            }

            // Crossing and every pre-existing non-Small connection retain the
            // established one-cardinal-cell connector semantics.
            BlockPos logicalSegment = context.spec.endpointPosition(
                    context.root, context.facing, endpoint);
            Direction outward = context.spec.rotateDirection(
                    context.facing, endpoint.outward());
            BlockPos next = logicalSegment.relative(outward);
            BlockState nextState = cart.level().getBlockState(next);
            if (BaseRailBlock.isRail(nextState)) {
                return true;
            }
            BlockState below = cart.level().getBlockState(next.below());
            return BaseRailBlock.isRail(below);
        }
        return false;
    }

    private static boolean hasExactSmallNeighborAtEndpoint(
            AbstractMinecart cart,
            Context source,
            LegacyBatchTrackSpec.Endpoint sourceEndpoint) {
        BlockPos sourceLogical = source.spec.endpointPosition(
                source.root, source.facing, sourceEndpoint);
        Direction sourceOutward = source.spec.rotateDirection(
                source.facing, sourceEndpoint.outward());

        // The OG Small is a 2x2 logical diagonal with only three physical cells.
        // A radius of two is sufficient to discover either neighboring L-footprint
        // from the shared logical endpoint without considering unrelated branches.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = sourceLogical.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)
                            || !"track_diagonal_straight_small".equals(
                                    batch.getSpec().id())) {
                        continue;
                    }

                    Context candidate = new Context(batch, state, pos);
                    if (candidate.root.equals(source.root)
                            && candidate.facing == source.facing) {
                        continue;
                    }

                    for (LegacyBatchTrackSpec.Endpoint candidateEndpoint
                            : candidate.spec.endpoints()) {
                        Direction candidateOutward =
                                candidate.spec.rotateDirection(
                                        candidate.facing,
                                        candidateEndpoint.outward());
                        if (candidateOutward != sourceOutward.getOpposite()) {
                            continue;
                        }

                        BlockPos candidateLogical =
                                candidate.spec.endpointPosition(
                                        candidate.root,
                                        candidate.facing,
                                        candidateEndpoint);
                        if (candidateLogical.equals(sourceLogical)
                                || isOriginalSmallCrossingFirstExtensionPair(
                                        source, sourceEndpoint,
                                        candidate, candidateEndpoint)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static int linearEndpointPart(Context context,
                                          int routeIndex,
                                          boolean atEnd) {
        String id = context.spec.id();
        if (isDiagonalStraightFamily(id)) {
            return atEnd ? context.spec.partCount() - 1 : 0;
        }

        if (TARGET_DIAGONAL_CROSSING.equals(id)) {
            return originalDiagonalCrossingEndpointPart(
                    context, routeIndex, atEnd);
        }

        return -1;
    }

    /**
     * Step 9.3e-t1 TC4.5 lastTrack endpoint pairing for Diagonal Crossing.
     *
     * Original Traincraft's diagonal crossing root did not become the bogie's
     * new movement owner. Whichever diagonal feeder owned lastTrack before the
     * center remained authoritative until the bogie reached the opposite side.
     * The 1.20.1 compatibility footprint has four logical endpoints, so pair
     * them by the already-selected continuous route instead of by Manhattan
     * segment order. This resolves the historical part-11 cardinal snap: route
     * 0 owns one diagonal pair and route 1 owns the other, in every assembly
     * facing. Virtual endpoint metadata is respected through endpointPosition().
     */
    private static int originalDiagonalCrossingEndpointPart(
            Context context, int routeIndex, boolean atEnd) {
        LinearRoute[] routes = linearRoutes(context);
        if (routeIndex < 0 || routeIndex >= routes.length) {
            return -1;
        }

        LinearRoute route = routes[routeIndex];
        int startPart = -1;
        int endPart = -1;
        double startProgress = Double.POSITIVE_INFINITY;
        double endProgress = Double.NEGATIVE_INFINITY;
        int matched = 0;

        for (LegacyBatchTrackSpec.Endpoint endpoint : context.spec.endpoints()) {
            if (crossingRouteIndexForEndpoint(context, endpoint.part())
                    != routeIndex) {
                continue;
            }

            BlockPos logical = context.spec.endpointPosition(
                    context.root, context.facing, endpoint);
            double progress = route.projectedProgress(
                    logical.getX() + 0.5D, logical.getZ() + 0.5D);
            matched++;

            if (progress < startProgress) {
                startProgress = progress;
                startPart = endpoint.part();
            }
            if (progress > endProgress) {
                endProgress = progress;
                endPart = endpoint.part();
            }
        }

        // A valid two-way diagonal crossing has exactly two logical endpoints
        // per route. Fail open to the pre-t1 behavior if a future spec changes
        // that topology rather than inventing a new connection.
        if (matched != 2 || startPart < 0 || endPart < 0
                || startPart == endPart) {
            return -1;
        }
        return atEnd ? endPart : startPart;
    }

    private static int chooseFreshLinearRoute(LinearRoute[] routes,
                                              double worldX,
                                              double worldZ,
                                              Vec3 motion,
                                              float fallbackYaw) {
        if (routes.length <= 1) {
            return 0;
        }

        double horizontal = Math.hypot(motion.x, motion.z);
        if (horizontal > MOTION_EPSILON) {
            int best = 0;
            double bestAlignment = -1.0D;
            double secondAlignment = -1.0D;

            for (int i = 0; i < routes.length; i++) {
                LinearRoute route = routes[i];
                double alignment = Math.abs(
                        (motion.x * route.tangent.x + motion.z * route.tangent.z)
                                / horizontal);
                if (alignment > bestAlignment) {
                    secondAlignment = bestAlignment;
                    bestAlignment = alignment;
                    best = i;
                } else if (alignment > secondAlignment) {
                    secondAlignment = alignment;
                }
            }

            if (bestAlignment - secondAlignment > 0.08D) {
                return best;
            }
        }

        // Cardinal feeder motion can be equally aligned to both diagonals. In
        // that tie, choose the actual route line nearest the current position.
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < routes.length; i++) {
            double distance = routes[i].perpendicularDistance(worldX, worldZ);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }

        if (routes.length == 2) {
            int other = nearest == 0 ? 1 : 0;
            double otherDistance = routes[other].perpendicularDistance(worldX, worldZ);
            if (otherDistance - nearestDistance > 0.10D) {
                return nearest;
            }
        }

        double yaw = Math.toRadians(fallbackYaw);
        double facingX = Math.sin(yaw);
        double facingZ = -Math.cos(yaw);
        int best = 0;
        double bestAlignment = -1.0D;
        for (int i = 0; i < routes.length; i++) {
            LinearRoute route = routes[i];
            double alignment = Math.abs(
                    facingX * route.tangent.x + facingZ * route.tangent.z);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = i;
            }
        }
        return best;
    }

    private static Vec3 linearTangentForFacing(AbstractMinecart cart,
                                               Context context,
                                               float fallbackYaw) {
        LinearRoute route = linearRouteForFacing(cart, context, fallbackYaw);
        return route == null ? null : route.tangent;
    }

    private static LinearRoute linearRouteForFacing(AbstractMinecart cart,
                                                    Context context,
                                                    float fallbackYaw) {
        LinearRoute[] routes = linearRoutes(context);
        if (routes.length == 0) {
            return null;
        }

        var data = cart.getPersistentData();
        String contextKey = linearContextKey(context);
        if (data.contains(LINEAR_CONTEXT_KEY)
                && data.contains(LINEAR_ROUTE_KEY)
                && contextKey.equals(data.getString(LINEAR_CONTEXT_KEY))) {
            int routeIndex = data.getInt(LINEAR_ROUTE_KEY);
            if (routeIndex >= 0 && routeIndex < routes.length) {
                return routes[routeIndex];
            }
        }

        int routeIndex = chooseFreshLinearRoute(
                routes,
                cart.getX(),
                cart.getZ(),
                cart.getDeltaMovement(),
                fallbackYaw);
        return routes[routeIndex];
    }

    private static LinearRoute[] linearRoutes(Context context) {
        String id = context.spec.id();

        if (isCrossingDiagonalFamily(id)) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (int part = 0; part < context.spec.partCount(); part++) {
                LegacyBatchTrackSpec.Point point = context.spec.pointForPart(part);
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minZ = Math.min(minZ, point.z);
                maxZ = Math.max(maxZ, point.z);
            }

            LinearRoute first = makeLinearRoute(
                    context, minX, maxZ, maxX, minZ, 0);

            LinearRoute second = makeLinearRoute(
                    context, maxX, maxZ, minX, minZ, 1);
            return new LinearRoute[] { first, second };
        }

        if ("track_diagonal_straight_small".equals(id)) {
            LegacyBatchTrackSpec.Point start = context.spec.pointForPart(0);

            // STEP_9_3E_T5_R8B_CROSSING_VISUAL_ROOT_CENTERLINE
            //
            // Fresh-world r8a traces proved the crossing-owned Small is not an
            // ordinary one-block Small for movement. Its full OBJ is rendered
            // from physical part 2 (original_visual_root=true), and that p2 cell
            // is exactly the midpoint between:
            //
            //   the selected diagonal-crossing route endpoint, and
            //   the first normal r6 Small extension centerline.
            //
            // Treating this assembly as the generic root -> empty-corner route
            // forced a one-block CARDINAL lateral transfer. Runtime proof showed
            // that directly:
            //   crossing -> west Small: motion became pure X / yaw 90
            //   crossing -> east Small: motion became pure X / yaw 90
            // and the north arm bled speed while trying to recenter between two
            // parallel one-block routes.
            //
            // TC4.5 movement is a continuously centered 45-degree trajectory.
            // For the crossing-owned visual-root Small, use the actual rendered
            // p2 cell as the midpoint of a two-diagonal-block route:
            //
            //   canonical p2 = (1,0)
            //   route start  = p2 + (-1,+1) = (0,+1)
            //   route end    = p2 + (+1,-1) = (2,-1)
            //
            // This joins the crossing and the first ordinary Small with one
            // continuous diagonal and removes the artificial cardinal bridge.
            if (stateBooleanByName(context.state, "original_visual_root")) {
                LegacyBatchTrackSpec.Point visual =
                        context.spec.pointForPart(2);
                return new LinearRoute[] {
                        makeLinearRoute(
                                context,
                                visual.x - 1.0D,
                                visual.z + 1.0D,
                                visual.x + 1.0D,
                                visual.z - 1.0D,
                                0)
                };
            }

            // STEP_9_3E_T4_R3_OG_SMALL_DIAGONAL_FOOTPRINT
            //
            // Ordinary r6 Small chains keep the frozen one-block route:
            // owner/root (0,0) -> empty logical diagonal corner (1,-1).
            return new LinearRoute[] {
                    makeLinearRoute(
                            context,
                            start.x,
                            start.z,
                            1.0D,
                            -1.0D,
                            0)
            };
        }

        if (isDiagonalStraightFamily(id)) {
            LegacyBatchTrackSpec.Point start = context.spec.pointForPart(0);
            LegacyBatchTrackSpec.Point end =
                    context.spec.pointForPart(context.spec.partCount() - 1);
            return new LinearRoute[] {
                    makeLinearRoute(context, start.x, start.z, end.x, end.z, 0)
            };
        }

        return new LinearRoute[0];
    }

    private static LinearRoute makeLinearRoute(Context context,
                                               double startLocalX,
                                               double startLocalZ,
                                               double endLocalX,
                                               double endLocalZ,
                                               int routeIndex) {
        Vec3 start = fromCanonical(
                context.root, context.facing, startLocalX, startLocalZ);
        Vec3 end = fromCanonical(
                context.root, context.facing, endLocalX, endLocalZ);

        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double length = Math.hypot(dx, dz);
        if (length <= MOTION_EPSILON) {
            throw new IllegalStateException(
                    "Continuous diagonal route has zero length: " + context.spec.id());
        }

        return new LinearRoute(
                start,
                new Vec3(dx / length, 0.0D, dz / length),
                length,
                routeIndex);
    }

    private static String linearContextKey(Context context) {
        return context.spec.id()
                + "|" + context.root.asLong()
                + "|" + context.facing.ordinal();
    }

    private static void clearLinearSession(AbstractMinecart cart, Context context) {
        var data = cart.getPersistentData();
        if (!data.contains(LINEAR_CONTEXT_KEY)) {
            return;
        }

        String current = data.getString(LINEAR_CONTEXT_KEY);
        if (!current.equals(linearContextKey(context))) {
            return;
        }

        data.remove(LINEAR_CONTEXT_KEY);
        data.remove(LINEAR_ROUTE_KEY);
        data.remove(LINEAR_PROGRESS_KEY);
        data.remove(LINEAR_TICK_KEY);
        data.remove(LINEAR_SIGN_KEY);
        data.remove(LINEAR_BODY_SIGN_KEY);
    }

    private static CurveSample sampleCurve(Context context,
                                           double worldX,
                                           double worldZ) {
        String id = context.spec.id();
        double[] local = toCanonical(context.root, context.facing, worldX, worldZ);

        double quarterRadius = quarterCurveRadius(id);
        if (quarterRadius > 0.0D) {
            return sampleQuarterCurve(context, local[0], local[1], quarterRadius);
        }

        OffsetDefinition fortyFive = fortyFiveDefinition(id);
        if (fortyFive == null) {
            fortyFive = activeTargetFortyFiveSwitchDefinition(context);
        }
        if (fortyFive != null) {
            return sampleFortyFiveCurve(
                    context, local[0], local[1], fortyFive.lateral, fortyFive.forward);
        }

        OffsetDefinition parallel = parallelDefinition(id);
        if (parallel != null) {
            return sampleParallelCurve(
                    context, local[0], local[1], parallel.lateral, parallel.forward);
        }

        return null;
    }

    /** Quarter-circle centerline matching the already accepted Big Curve math. */
    private static CurveSample sampleQuarterCurve(Context context,
                                                  double localX,
                                                  double localZ,
                                                  double radius) {
        double centerVectorX = localX + radius;
        double t = Mth.clamp(
                Math.atan2(-localZ, centerVectorX),
                0.0D,
                Math.PI * 0.5D);
        return sampleQuarterCurveAtProgress(context, radius * t, radius);
    }

    private static CurveSample sampleQuarterCurveAtProgress(Context context,
                                                            double progress,
                                                            double radius) {
        double total = radius * Math.PI * 0.5D;
        double clampedProgress = Mth.clamp(progress, 0.0D, total);
        double t = clampedProgress / radius;

        double targetLocalX = -radius + radius * Math.cos(t);
        double targetLocalZ = -radius * Math.sin(t);
        Vec3 targetHorizontal = fromCanonical(
                context.root, context.facing, targetLocalX, targetLocalZ);
        Vec3 tangent = rotateCanonicalVector(
                context.facing, -Math.sin(t), -Math.cos(t));

        return new CurveSample(
                targetHorizontal,
                tangent,
                clampedProgress,
                total - clampedProgress);
    }

    /**
     * Movement sample for the endpoint entry bridges. Values inside the
     * mathematical quarter circle use the exact r2/r4 arc unchanged. Negative
     * progress stays on the START tangent for forward entry; progress beyond
     * the arc end stays on the END tangent for reverse entry.
     */
    private static CurveSample sampleQuarterCurveAtMovementProgress(
            Context context,
            double progress,
            double radius) {
        double total = radius * Math.PI * 0.5D;
        if (progress < 0.0D) {
            CurveSample start = sampleQuarterCurveAtProgress(
                    context, 0.0D, radius);
            double p = Math.max(
                    -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION, progress);
            return new CurveSample(
                    start.targetHorizontal.add(start.tangent.scale(p)),
                    start.tangent,
                    p,
                    total - p);
        }
        if (progress <= total) {
            return sampleQuarterCurveAtProgress(context, progress, radius);
        }

        CurveSample end = sampleQuarterCurveAtProgress(
                context, total, radius);
        double p = Math.min(
                total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION, progress);
        return new CurveSample(
                end.targetHorizontal.add(
                        end.tangent.scale(p - total)),
                end.tangent,
                p,
                total - p);
    }

    /**
     * Visual-only quarter-curve projection for the two virtual bogies.
     *
     * Unlike movement pathing, this may extend a short distance beyond the two
     * rail-head endpoints along the exact endpoint tangents. The closest of the
     * arc, start tangent, and end tangent is selected so an adjoining straight
     * cannot accidentally map to the opposite end of the curve.
     */
    private static double projectedQuarterCurvePoseProgress(Context context,
                                                            double worldX,
                                                            double worldZ,
                                                            double radius) {
        double[] local = toCanonical(
                context.root, context.facing, worldX, worldZ);
        double total = radius * Math.PI * 0.5D;

        double arcProgress = radius * Mth.clamp(
                Math.atan2(-local[1], local[0] + radius),
                0.0D,
                Math.PI * 0.5D);
        CurveSample arc = sampleQuarterCurveAtProgress(
                context, arcProgress, radius);
        double bestProgress = arcProgress;
        double bestDistanceSq = horizontalDistanceSq(
                worldX, worldZ, arc.targetHorizontal);

        CurveSample start = sampleQuarterCurveAtProgress(
                context, 0.0D, radius);
        double startAlong = (worldX - start.targetHorizontal.x) * start.tangent.x
                + (worldZ - start.targetHorizontal.z) * start.tangent.z;
        double startProgress = Mth.clamp(
                startAlong, -POSE_ENDPOINT_EXTENSION, 0.0D);
        Vec3 startPoint = start.targetHorizontal.add(
                start.tangent.scale(startProgress));
        double startDistanceSq = horizontalDistanceSq(
                worldX, worldZ, startPoint);
        if (startDistanceSq < bestDistanceSq) {
            bestDistanceSq = startDistanceSq;
            bestProgress = startProgress;
        }

        CurveSample end = sampleQuarterCurveAtProgress(
                context, total, radius);
        double endAlong = (worldX - end.targetHorizontal.x) * end.tangent.x
                + (worldZ - end.targetHorizontal.z) * end.tangent.z;
        double endProgress = total + Mth.clamp(
                endAlong, 0.0D, POSE_ENDPOINT_EXTENSION);
        Vec3 endPoint = end.targetHorizontal.add(
                end.tangent.scale(endProgress - total));
        double endDistanceSq = horizontalDistanceSq(
                worldX, worldZ, endPoint);
        if (endDistanceSq < bestDistanceSq) {
            bestProgress = endProgress;
        }

        return bestProgress;
    }

    private static CurveSample sampleQuarterCurveAtPoseProgress(Context context,
                                                                double progress,
                                                                double radius) {
        double total = radius * Math.PI * 0.5D;
        if (progress < 0.0D) {
            CurveSample start = sampleQuarterCurveAtProgress(
                    context, 0.0D, radius);
            double p = Math.max(-POSE_ENDPOINT_EXTENSION, progress);
            return new CurveSample(
                    start.targetHorizontal.add(start.tangent.scale(p)),
                    start.tangent,
                    p,
                    total - p);
        }
        if (progress > total) {
            CurveSample end = sampleQuarterCurveAtProgress(
                    context, total, radius);
            double p = Math.min(
                    total + POSE_ENDPOINT_EXTENSION, progress);
            return new CurveSample(
                    end.targetHorizontal.add(
                            end.tangent.scale(p - total)),
                    end.tangent,
                    p,
                    total - p);
        }
        return sampleQuarterCurveAtProgress(context, progress, radius);
    }

    private static double horizontalDistanceSq(double worldX,
                                               double worldZ,
                                               Vec3 point) {
        double dx = worldX - point.x;
        double dz = worldZ - point.z;
        return dx * dx + dz * dz;
    }

    /**
     * Circular arc fitted through the exact integer endpoint of each classic
     * 45-degree piece. The source footprints are integer approximations, so the
     * fitted terminal angle is close to 45 degrees while still landing exactly
     * on the existing external connector.
     */
    private static boolean usesOriginalTraincraftFortyFiveMath(Context context) {
        return context != null
                && isOriginalTraincraftMediumFortyFiveSwitch(context.spec.id())
                && stateActive(context.state);
    }

    private static double fortyFiveTotalLength(Context context,
                                               double lateral,
                                               double forward) {
        if (usesOriginalTraincraftFortyFiveMath(context)) {
            return ORIGINAL_TC45_SWITCH_LEAD
                    + ORIGINAL_TC45_SWITCH_RADIUS * ORIGINAL_TC45_SWITCH_ANGLE;
        }

        double absLateral = Math.abs(lateral);
        if (absLateral < 1.0E-9D || forward <= 0.0D) {
            return 0.0D;
        }
        double radius = (absLateral * absLateral + forward * forward)
                / (2.0D * absLateral);
        double thetaEnd = 2.0D * Math.atan2(absLateral, forward);
        return radius * thetaEnd;
    }

    private static double projectFortyFiveCoreProgress(Context context,
                                                        double localX,
                                                        double localZ,
                                                        double lateral,
                                                        double forward) {
        double absLateral = Math.abs(lateral);
        if (absLateral < 1.0E-9D || forward <= 0.0D) {
            return 0.0D;
        }

        if (usesOriginalTraincraftFortyFiveMath(context)) {
            double side = Math.signum(lateral);

            // Original TC4.5 switch: straight lead from the root center to the
            // curve start, then a true 45-degree / 3.75-radius arc. Compare
            // both primitives and keep whichever is geometrically closer.
            double leadProgress = Mth.clamp(
                    -localZ, 0.0D, ORIGINAL_TC45_SWITCH_LEAD);
            double leadX = 0.0D;
            double leadZ = -leadProgress;
            double leadDx = localX - leadX;
            double leadDz = localZ - leadZ;
            double leadDistSq = leadDx * leadDx + leadDz * leadDz;

            double signedX = side * localX;
            double theta = Mth.clamp(
                    Math.atan2(
                            -(localZ + ORIGINAL_TC45_SWITCH_LEAD),
                            ORIGINAL_TC45_SWITCH_RADIUS - signedX),
                    0.0D,
                    ORIGINAL_TC45_SWITCH_ANGLE);
            double arcProgress = ORIGINAL_TC45_SWITCH_LEAD
                    + ORIGINAL_TC45_SWITCH_RADIUS * theta;
            double arcX = side * (ORIGINAL_TC45_SWITCH_RADIUS
                    - ORIGINAL_TC45_SWITCH_RADIUS * Math.cos(theta));
            double arcZ = -ORIGINAL_TC45_SWITCH_LEAD
                    - ORIGINAL_TC45_SWITCH_RADIUS * Math.sin(theta);
            double arcDx = localX - arcX;
            double arcDz = localZ - arcZ;
            double arcDistSq = arcDx * arcDx + arcDz * arcDz;

            return arcDistSq < leadDistSq ? arcProgress : leadProgress;
        }

        double side = Math.signum(lateral);
        double radius = (absLateral * absLateral + forward * forward)
                / (2.0D * absLateral);
        double thetaEnd = 2.0D * Math.atan2(absLateral, forward);
        double signedX = side * localX;
        return radius * Mth.clamp(
                Math.atan2(-localZ, radius - signedX),
                0.0D,
                thetaEnd);
    }

    private static CurveSample sampleFortyFiveCurve(Context context,
                                                    double localX,
                                                    double localZ,
                                                    double lateral,
                                                    double forward) {
        double total = fortyFiveTotalLength(context, lateral, forward);
        if (total <= 0.0D) {
            return null;
        }
        double progress = projectFortyFiveCoreProgress(
                context, localX, localZ, lateral, forward);
        return sampleFortyFiveCurveAtProgress(
                context, progress, lateral, forward);
    }

    private static CurveSample sampleFortyFiveCurveAtProgress(Context context,
                                                              double progress,
                                                              double lateral,
                                                              double forward) {
        double absLateral = Math.abs(lateral);
        if (absLateral < 1.0E-9D || forward <= 0.0D) {
            return null;
        }

        double side = Math.signum(lateral);
        double total = fortyFiveTotalLength(context, lateral, forward);
        double clampedProgress = Mth.clamp(progress, 0.0D, total);

        if (usesOriginalTraincraftFortyFiveMath(context)) {
            if (clampedProgress <= ORIGINAL_TC45_SWITCH_LEAD) {
                Vec3 targetHorizontal = fromCanonical(
                        context.root, context.facing,
                        0.0D, -clampedProgress);
                Vec3 tangent = rotateCanonicalVector(
                        context.facing, 0.0D, -1.0D);
                return new CurveSample(
                        targetHorizontal, tangent,
                        clampedProgress, total - clampedProgress);
            }

            double arcProgress = clampedProgress - ORIGINAL_TC45_SWITCH_LEAD;
            double t = arcProgress / ORIGINAL_TC45_SWITCH_RADIUS;
            double targetLocalX = side * (ORIGINAL_TC45_SWITCH_RADIUS
                    - ORIGINAL_TC45_SWITCH_RADIUS * Math.cos(t));
            double targetLocalZ = -ORIGINAL_TC45_SWITCH_LEAD
                    - ORIGINAL_TC45_SWITCH_RADIUS * Math.sin(t);
            Vec3 targetHorizontal = fromCanonical(
                    context.root, context.facing, targetLocalX, targetLocalZ);
            Vec3 tangent = rotateCanonicalVector(
                    context.facing, side * Math.sin(t), -Math.cos(t));
            return new CurveSample(
                    targetHorizontal, tangent,
                    clampedProgress, total - clampedProgress);
        }

        double radius = (absLateral * absLateral + forward * forward)
                / (2.0D * absLateral);
        double thetaEnd = 2.0D * Math.atan2(absLateral, forward);
        double t = Mth.clamp(clampedProgress / radius, 0.0D, thetaEnd);

        double targetLocalX = side * (radius - radius * Math.cos(t));
        double targetLocalZ = -radius * Math.sin(t);
        Vec3 targetHorizontal = fromCanonical(
                context.root, context.facing, targetLocalX, targetLocalZ);
        Vec3 tangent = rotateCanonicalVector(
                context.facing, side * Math.sin(t), -Math.cos(t));

        return new CurveSample(
                targetHorizontal, tangent,
                clampedProgress, total - clampedProgress);
    }

    /**
     * Step 9.3d-t10 movement sampler for the active 45-degree Medium Left/Right
     * switch. It mirrors the proven t5-r6 endpoint policy: the analytic arc is
     * authoritative in the middle, while a newly owned endpoint cell may begin
     * on a short exact tangent extension instead of snapping immediately to the
     * mathematical endpoint.
     */
    private static CurveSample sampleFortyFiveCurveAtMovementProgress(
            Context context,
            double progress,
            double lateral,
            double forward) {
        double total = fortyFiveTotalLength(context, lateral, forward);
        if (total <= 0.0D) {
            return null;
        }

        if (progress < 0.0D) {
            CurveSample start = sampleFortyFiveCurveAtProgress(
                    context, 0.0D, lateral, forward);
            double p = Math.max(
                    -CURVE_FORWARD_ENTRY_TANGENT_EXTENSION, progress);
            return new CurveSample(
                    start.targetHorizontal.add(start.tangent.scale(p)),
                    start.tangent,
                    p,
                    total - p);
        }
        if (progress <= total) {
            return sampleFortyFiveCurveAtProgress(
                    context, progress, lateral, forward);
        }

        CurveSample end = sampleFortyFiveCurveAtProgress(
                context, total, lateral, forward);
        double p = Math.min(
                total + CURVE_REVERSE_ENTRY_TANGENT_EXTENSION, progress);
        return new CurveSample(
                end.targetHorizontal.add(end.tangent.scale(p - total)),
                end.tangent,
                p,
                total - p);
    }

    private static double projectedFortyFivePoseProgress(Context context,
                                                         double worldX,
                                                         double worldZ,
                                                         double lateral,
                                                         double forward) {
        double[] local = toCanonical(
                context.root, context.facing, worldX, worldZ);
        double total = fortyFiveTotalLength(context, lateral, forward);
        if (total <= 0.0D) {
            return 0.0D;
        }

        double coreProgress = projectFortyFiveCoreProgress(
                context, local[0], local[1], lateral, forward);
        CurveSample core = sampleFortyFiveCurveAtProgress(
                context, coreProgress, lateral, forward);
        double bestProgress = coreProgress;
        double bestDistanceSq = horizontalDistanceSq(
                worldX, worldZ, core.targetHorizontal);

        CurveSample start = sampleFortyFiveCurveAtProgress(
                context, 0.0D, lateral, forward);
        double startAlong = (worldX - start.targetHorizontal.x) * start.tangent.x
                + (worldZ - start.targetHorizontal.z) * start.tangent.z;
        double startProgress = Mth.clamp(
                startAlong, -POSE_ENDPOINT_EXTENSION, 0.0D);
        Vec3 startPoint = start.targetHorizontal.add(
                start.tangent.scale(startProgress));
        double startDistanceSq = horizontalDistanceSq(
                worldX, worldZ, startPoint);
        if (startDistanceSq < bestDistanceSq) {
            bestDistanceSq = startDistanceSq;
            bestProgress = startProgress;
        }

        CurveSample end = sampleFortyFiveCurveAtProgress(
                context, total, lateral, forward);
        double endAlong = (worldX - end.targetHorizontal.x) * end.tangent.x
                + (worldZ - end.targetHorizontal.z) * end.tangent.z;
        double endProgress = total + Mth.clamp(
                endAlong, 0.0D, POSE_ENDPOINT_EXTENSION);
        Vec3 endPoint = end.targetHorizontal.add(
                end.tangent.scale(endProgress - total));
        double endDistanceSq = horizontalDistanceSq(
                worldX, worldZ, endPoint);
        if (endDistanceSq < bestDistanceSq) {
            bestProgress = endProgress;
        }

        return bestProgress;
    }

    private static CurveSample sampleFortyFiveCurveAtPoseProgress(
            Context context,
            double progress,
            double lateral,
            double forward) {
        double total = fortyFiveTotalLength(context, lateral, forward);
        if (total <= 0.0D) {
            return null;
        }

        if (progress < 0.0D) {
            CurveSample start = sampleFortyFiveCurveAtProgress(
                    context, 0.0D, lateral, forward);
            double p = Math.max(-POSE_ENDPOINT_EXTENSION, progress);
            return new CurveSample(
                    start.targetHorizontal.add(start.tangent.scale(p)),
                    start.tangent,
                    p,
                    total - p);
        }
        if (progress > total) {
            CurveSample end = sampleFortyFiveCurveAtProgress(
                    context, total, lateral, forward);
            double p = Math.min(total + POSE_ENDPOINT_EXTENSION, progress);
            return new CurveSample(
                    end.targetHorizontal.add(end.tangent.scale(p - total)),
                    end.tangent,
                    p,
                    total - p);
        }
        return sampleFortyFiveCurveAtProgress(
                context, progress, lateral, forward);
    }

    /**
     * Continuous form of LegacyBatchTrackSpecs.offsetCurve(): X follows the same
     * cubic smoothstep used when the hidden integer guide was generated, while Z
     * advances monotonically. Start and end tangents are both north/south, which
     * matches the parallel/S-curve external connectors.
     */
    private static CurveSample sampleParallelCurve(Context context,
                                                   double localX,
                                                   double localZ,
                                                   double lateral,
                                                   double forward) {
        if (forward <= 0.0D) {
            return null;
        }

        double progress = Mth.clamp(-localZ, 0.0D, forward);
        double s = progress / forward;
        double smooth = s * s * (3.0D - 2.0D * s);
        double targetLocalX = lateral * smooth;
        double targetLocalZ = -progress;

        double dxDProgress = lateral * 6.0D * s * (1.0D - s) / forward;
        double dzDProgress = -1.0D;
        double tangentLength = Math.hypot(dxDProgress, dzDProgress);
        double tangentLocalX = dxDProgress / tangentLength;
        double tangentLocalZ = dzDProgress / tangentLength;

        Vec3 targetHorizontal = fromCanonical(
                context.root, context.facing, targetLocalX, targetLocalZ);
        Vec3 tangent = rotateCanonicalVector(
                context.facing, tangentLocalX, tangentLocalZ);

        return new CurveSample(
                targetHorizontal,
                tangent,
                progress,
                forward - progress);
    }

    private static void applyStraightSlope(AbstractMinecart cart,
                                           Context context,
                                           Vec3 preRailMotion,
                                           boolean powered,
                                           boolean serviceBrake,
                                           boolean handBrake) {
        double[] local = toCanonical(context.root, context.facing, cart.getX(), cart.getZ());
        double length = Math.max(1.0D, context.spec.partCount() - 1.0D);
        double rawProgress = -local[1];
        double progress = Mth.clamp(rawProgress, 0.0D, length);
        double fraction = progress / length;

        Vec3 targetHorizontal = fromCanonical(
                context.root, context.facing, 0.0D, -progress);
        Vec3 tangent = rotateCanonicalVector(context.facing, 0.0D, -1.0D);
        Vec3 postRailMotion = cart.getDeltaMovement();
        double sign = travelSign(preRailMotion, postRailMotion, tangent, cart.getYRot());

        if ((progress <= ENDPOINT_RELEASE_DISTANCE && sign < 0.0D)
                || ((length - progress) <= ENDPOINT_RELEASE_DISTANCE && sign > 0.0D)) {
            return;
        }

        double speed = correctedSpeed(
                preRailMotion, postRailMotion, tangent,
                powered, true, serviceBrake, handBrake, Double.POSITIVE_INFINITY);

        cart.setPos(targetHorizontal.x,
                context.root.getY() + RAIL_SURFACE_Y + fraction,
                targetHorizontal.z);
        cart.setDeltaMovement(
                tangent.x * sign * speed,
                postRailMotion.y,
                tangent.z * sign * speed);
    }

    private static double correctedSpeed(Vec3 preRailMotion,
                                         Vec3 postRailMotion,
                                         Vec3 tangent,
                                         boolean powered,
                                         boolean retainPreRailSpeed,
                                         boolean serviceBrake,
                                         boolean handBrake,
                                         double maxSpeed) {
        double postAlong = Math.abs(postRailMotion.x * tangent.x + postRailMotion.z * tangent.z);
        double postHorizontal = Math.hypot(postRailMotion.x, postRailMotion.z);
        double speed = Math.max(postAlong, postHorizontal);

        if ((powered || retainPreRailSpeed) && !serviceBrake && !handBrake) {
            double preAlong = Math.abs(preRailMotion.x * tangent.x + preRailMotion.z * tangent.z);
            double preHorizontal = Math.hypot(preRailMotion.x, preRailMotion.z);
            double retained = Math.max(preAlong, preHorizontal) * POWERED_SPEED_RETAIN;
            speed = Math.max(speed, retained);
        }

        if (Double.isFinite(maxSpeed)) {
            speed = Math.min(speed, Math.max(0.0D, maxSpeed));
        }
        return Math.max(0.0D, speed);
    }

    private static double travelSign(Vec3 preRailMotion,
                                     Vec3 postRailMotion,
                                     Vec3 tangent,
                                     float fallbackYaw) {
        double preHorizontal = Math.hypot(preRailMotion.x, preRailMotion.z);
        if (preHorizontal > MOTION_EPSILON) {
            double dot = preRailMotion.x * tangent.x + preRailMotion.z * tangent.z;
            if (Math.abs(dot) > MOTION_EPSILON) {
                return dot >= 0.0D ? 1.0D : -1.0D;
            }
        }

        double postHorizontal = Math.hypot(postRailMotion.x, postRailMotion.z);
        if (postHorizontal > MOTION_EPSILON) {
            double dot = postRailMotion.x * tangent.x + postRailMotion.z * tangent.z;
            if (Math.abs(dot) > MOTION_EPSILON) {
                return dot >= 0.0D ? 1.0D : -1.0D;
            }
        }

        double yaw = Math.toRadians(fallbackYaw);
        double facingX = Math.sin(yaw);
        double facingZ = -Math.cos(yaw);
        return facingX * tangent.x + facingZ * tangent.z >= 0.0D ? 1.0D : -1.0D;
    }


    /**
     * Visual-only context lookup for body yaw/pitch.
     *
     * Movement still uses findContext(), which deliberately gives a real normal
     * rail under the cart priority. For pose only, an immediately connected
     * quarter curve may be sampled for up to POSE_ENDPOINT_EXTENSION blocks
     * beyond its endpoint so the virtual bogies can straddle straight and arc.
     */
    private static Context findPoseContext(AbstractMinecart cart,
                                           Vec3 handoffMotion) {
        Context movementContext = findContext(cart, handoffMotion);
        if (movementContext != null) {
            return movementContext;
        }

        BlockPos base = cart.blockPosition();
        BlockPos[] direct = new BlockPos[] {
                base,
                base.below(),
                base.above()
        };

        Context best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (BlockPos directRailPos : direct) {
            BlockState directState = cart.level().getBlockState(directRailPos);
            if (!(directState.getBlock() instanceof BaseRailBlock)) {
                continue;
            }
            if (directState.getBlock() instanceof AbstractLegacyBatchRailBlock) {
                continue;
            }

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -POSE_CONTEXT_SEARCH_RADIUS;
                     dx <= POSE_CONTEXT_SEARCH_RADIUS;
                     dx++) {
                    for (int dz = -POSE_CONTEXT_SEARCH_RADIUS;
                         dz <= POSE_CONTEXT_SEARCH_RADIUS;
                         dz++) {
                        BlockPos pos = directRailPos.offset(dx, dy, dz);
                        BlockState state = cart.level().getBlockState(pos);
                        if (!(state.getBlock()
                                instanceof AbstractLegacyBatchRailBlock batch)) {
                            continue;
                        }

                        Context candidate = new Context(batch, state, pos);
                        double radius = quarterCurveRadius(candidate.spec.id());
                        OffsetDefinition targetFortyFive =
                                activeTargetFortyFiveSwitchDefinition(candidate);
                        if (radius <= 0.0D && targetFortyFive == null) {
                            continue;
                        }

                        double total;
                        if (radius > 0.0D) {
                            total = radius * Math.PI * 0.5D;
                        } else {
                            total = fortyFiveTotalLength(
                                    candidate,
                                    targetFortyFive.lateral,
                                    targetFortyFive.forward);
                        }
                        for (LegacyBatchTrackSpec.Endpoint endpoint
                                : candidate.spec.endpoints()) {
                            boolean startEndpoint = endpoint.part() == 0;
                            boolean endEndpoint =
                                    endpoint.part()
                                            == candidate.spec.partCount() - 1;
                            if (!startEndpoint && !endEndpoint) {
                                continue;
                            }

                            BlockPos endpointPos =
                                    candidate.spec.endpointPosition(
                                            candidate.root,
                                            candidate.facing,
                                            endpoint);
                            Direction outward =
                                    candidate.spec.rotateDirection(
                                            candidate.facing,
                                            endpoint.outward());
                            BlockPos connectedCell =
                                    endpointPos.relative(outward);
                            if (!connectedCell.equals(directRailPos)) {
                                continue;
                            }

                            double endpointProgress =
                                    startEndpoint ? 0.0D : total;
                            CurveSample endpointSample;
                            if (radius > 0.0D) {
                                endpointSample = sampleQuarterCurveAtProgress(
                                        candidate, endpointProgress, radius);
                            } else {
                                endpointSample = sampleFortyFiveCurveAtProgress(
                                        candidate, endpointProgress,
                                        targetFortyFive.lateral,
                                        targetFortyFive.forward);
                            }
                            double along =
                                    (cart.getX()
                                            - endpointSample.targetHorizontal.x)
                                                * endpointSample.tangent.x
                                    + (cart.getZ()
                                            - endpointSample.targetHorizontal.z)
                                                * endpointSample.tangent.z;
                            double expectedAlong = startEndpoint
                                    ? Mth.clamp(
                                            along,
                                            -POSE_ENDPOINT_EXTENSION,
                                            0.0D)
                                    : Mth.clamp(
                                            along,
                                            0.0D,
                                            POSE_ENDPOINT_EXTENSION);
                            Vec3 tangentPoint =
                                    endpointSample.targetHorizontal.add(
                                            endpointSample.tangent.scale(
                                                    expectedAlong));

                            double dxWorld = cart.getX() - tangentPoint.x;
                            double dzWorld = cart.getZ() - tangentPoint.z;
                            double distSq =
                                    dxWorld * dxWorld
                                            + dzWorld * dzWorld;
                            double perp =
                                    Math.sqrt(Math.max(0.0D, distSq));

                            if (perp > POSE_ENDPOINT_MAX_PERP) {
                                continue;
                            }

                            double extensionDistance =
                                    Math.abs(expectedAlong);
                            if (extensionDistance
                                    > POSE_ENDPOINT_EXTENSION + 1.0E-6D) {
                                continue;
                            }

                            if (distSq < bestDistanceSq) {
                                bestDistanceSq = distSq;
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    /**
     * Emulate TC4.5 TileTCRailGag multi-origin ownership for both active
     * Medium 45-degree switch -> Diagonal Straight handoffs. The diagonal
     * root is intentionally placed in the switch's first gag cell. Until the
     * mathematical arc reaches its real endpoint, the switch remains owner even
     * though the physical block under the cart belongs to the diagonal assembly.
     */
    private static Context findOriginalTraincraftSharedProxyOwner(
            AbstractMinecart cart,
            BlockPos base,
            Context directDiagonal,
            Vec3 handoffMotion) {
        if (!isDiagonalStraightFamily(directDiagonal.spec.id())) {
            return null;
        }

        // Forward exit already seeded this exact diagonal from the switch's
        // mathematical endpoint. Do not immediately reacquire the switch on the
        // next tick; let the pending root-owned linear handoff consume that
        // progress. Reverse entry has no pending handoff and therefore continues
        // into the topology lookup below.
        LinearRoute[] directRoutes = linearRoutes(directDiagonal);
        if (directRoutes.length > 0
                && pendingVirtualHandoffRoute(
                        cart, directDiagonal, cart.level().getGameTime(),
                        directRoutes.length) >= 0) {
            return null;
        }
        if (directRoutes.length == 0) {
            return null;
        }

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS;
                    dx <= ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS; dx++) {
                for (int dz = -ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS;
                        dz <= ORIGINAL_TC45_SHARED_OWNER_SEARCH_RADIUS; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    Context source = new Context(batch, state, pos);
                    if (!isOriginalTraincraftMediumFortyFiveSwitch(source.spec.id())
                            || !stateActive(source.state)) {
                        continue;
                    }

                    BlockPos sharedRoot = originalTraincraftSharedProxyRoot(source);
                    if (sharedRoot == null || !sharedRoot.equals(directDiagonal.root)) {
                        continue;
                    }

                    OffsetDefinition definition =
                            activeTargetFortyFiveSwitchDefinition(source);
                    if (definition == null) {
                        continue;
                    }

                    CurveSample sample = sampleFortyFiveCurve(
                            source, cart.getX(), cart.getZ(),
                            definition.lateral, definition.forward);
                    if (sample == null) {
                        continue;
                    }

                    double total = fortyFiveTotalLength(
                            source, definition.lateral, definition.forward);
                    CurveSample end = sampleFortyFiveCurveAtProgress(
                            source, total, definition.lateral, definition.forward);
                    if (end == null) {
                        continue;
                    }

                    double ex = cart.getX() - end.targetHorizontal.x;
                    double ez = cart.getZ() - end.targetHorizontal.z;
                    double along = ex * end.tangent.x + ez * end.tangent.z;
                    double perpX = ex - end.tangent.x * along;
                    double perpZ = ez - end.tangent.z * along;
                    double perpendicular = Math.hypot(perpX, perpZ);
                    double towardEnd = handoffMotion.x * end.tangent.x
                            + handoffMotion.z * end.tangent.z;

                    int routeIndex = virtualEndpointHandoffRoute(source, directDiagonal);
                    if (routeIndex < 0 || routeIndex >= directRoutes.length) {
                        routeIndex = 0;
                    }
                    LinearRoute sharedRoute = directRoutes[routeIndex];
                    double junctionProgress = sharedRoute.projectedProgress(
                            end.targetHorizontal.x, end.targetHorizontal.z);
                    double junctionPerpendicular = sharedRoute.perpendicularDistance(
                            end.targetHorizontal.x, end.targetHorizontal.z);
                    double cartRouteProgress = sharedRoute.projectedProgress(
                            cart.getX(), cart.getZ());
                    double cartRoutePerpendicular = sharedRoute.perpendicularDistance(
                            cart.getX(), cart.getZ());
                    double routeMotion = handoffMotion.x * sharedRoute.tangent.x
                            + handoffMotion.z * sharedRoute.tangent.z;

                    // STEP_9_3D_T10_R3_ORIGINAL_LASTTRACK_STICKY_OWNERSHIP
                    // TC4.5 did not re-pick an owner from whichever block happened
                    // to be under the bogie. EntityBogie kept lastTrack while the
                    // current gag still referenced that TileTCRail. Mirror that
                    // rule directly: if this exact switch already owns an active
                    // mathematical curve session, the modern diagonal root is only
                    // a shared gag/proxy cell and may not steal ownership before
                    // the stored 3.75-radius progress reaches the real endpoint.
                    var data = cart.getPersistentData();
                    long now = cart.level().getGameTime();
                    String sourceCurveKey = curveContextKey(source);
                    if (data.contains(CURVE_CONTEXT_KEY)
                            && data.contains(CURVE_PROGRESS_KEY)
                            && data.contains(CURVE_TICK_KEY)
                            && sourceCurveKey.equals(data.getString(CURVE_CONTEXT_KEY))) {
                        long lastCurveTick = data.getLong(CURVE_TICK_KEY);
                        if (now >= lastCurveTick
                                && now - lastCurveTick <= CURVE_SESSION_MAX_TICK_GAP
                                && data.getDouble(CURVE_PROGRESS_KEY) < total) {
                            return source;
                        }
                    }

                    // The original shared-gag junction is not the modern part-0
                    // block center. Project the TRUE switch arc endpoint onto the
                    // connected diagonal's mathematical centerline and use that
                    // topology-derived progress as the bidirectional ownership
                    // boundary. This is the missing piece behind the r2 reverse
                    // stop: the generic linear endpoint holder fired at progress
                    // 0.10 even though the real TC4.5 junction lies farther into
                    // the shared root cell.
                    if (junctionPerpendicular <= ORIGINAL_TC45_HANDOFF_MAX_PERP
                            && cartRoutePerpendicular <= LINEAR_INITIAL_CAPTURE_MAX_PERP
                            && routeMotion < -MOTION_EPSILON
                            && cartRouteProgress
                                    <= junctionProgress + ENDPOINT_RELEASE_DISTANCE
                            && cartRouteProgress
                                    >= junctionProgress - CURVE_REVERSE_ENTRY_TANGENT_EXTENSION) {
                        return source;
                    }

                    // TC4.5 ownership was topological: the switch root remains
                    // authoritative all the way to the real arc endpoint. Keep
                    // the projection fallback for first-contact cases that do not
                    // yet have a stored lastTrack-equivalent curve session.
                    if (sample.distanceToEnd > 0.0D) {
                        return source;
                    }

                    // Reverse/forward tangent fallbacks cover the tiny numerical
                    // offset between the exact 3.75-radius endpoint and the
                    // diagonal centerline. The topology-derived test above is the
                    // primary reverse path; these are only continuity guards.
                    if (perpendicular <= CURVE_REVERSE_ENTRY_MAX_PERP
                            && along >= -ENDPOINT_RELEASE_DISTANCE
                            && towardEnd < -MOTION_EPSILON
                            && along <= CURVE_REVERSE_ENTRY_TANGENT_EXTENSION) {
                        return source;
                    }

                    if (perpendicular <= ORIGINAL_TC45_HANDOFF_MAX_PERP
                            && along >= -ENDPOINT_RELEASE_DISTANCE
                            && towardEnd > MOTION_EPSILON
                            && along <= ORIGINAL_TC45_FORWARD_EXIT_TANGENT_EXTENSION) {
                        return source;
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos originalTraincraftSharedProxyRoot(Context source) {
        for (LegacyBatchTrackSpec.Endpoint endpoint : source.spec.endpoints()) {
            if (endpoint.part() != 4) {
                continue;
            }
            BlockPos segment = source.spec.offsetForPart(
                    source.root, source.facing, endpoint.part());
            Direction outward = source.spec.rotateDirection(
                    source.facing, endpoint.outward());
            return segment.relative(outward);
        }
        return null;
    }

    private static Context findOriginalTraincraftSharedDiagonal(
            AbstractMinecart cart, Context source) {
        BlockPos sharedRoot = originalTraincraftSharedProxyRoot(source);
        if (sharedRoot == null) {
            return null;
        }

        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = sharedRoot.offset(0, dy, 0);
            BlockState state = cart.level().getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                continue;
            }
            Context candidate = new Context(batch, state, pos);
            if (isDiagonalStraightFamily(candidate.spec.id())
                    && candidate.root.equals(sharedRoot)) {
                return candidate;
            }
        }
        return null;
    }

    private static Context findContext(AbstractMinecart cart, Vec3 handoffMotion) {
        BlockPos base = cart.blockPosition();
        BlockPos[] direct = new BlockPos[] {
                base,
                base.below(),
                base.above()
        };

        // STEP_9_3G_T4_R1_WHOLE_FAMILY_LASTTRACK_OWNERSHIP
        // A live non-Small turnout session outranks its compatibility guide and
        // any overlapping handoff cell until the selected analytic route has
        // actually crossed an external connector.
        Context latchedFamily = findLatchedFamilySwitchContext(cart, base);
        if (latchedFamily != null) {
            // STEP_9_3G_T4_R4_CONNECTED_FAMILY_HANDOFF
            //
            // The frozen Small r11 behavior allows a directly connected second
            // turnout to become the new owner as soon as the cart physically
            // enters that turnout's reciprocal endpoint cell. t4-r1 instead
            // returned the old FAMILY latch before inspecting the direct cell,
            // so toe-to-toe / endpoint-to-endpoint family switches could remain
            // owned by the previous turnout for the whole 0.60-block bridge.
            // That delays the next switch-state decision and is not equivalent
            // to the confirmed Small-switch topology.
            Context connectedFamily = findConnectedFamilySwitchHandoff(
                    cart, direct, latchedFamily, handoffMotion);
            if (connectedFamily != null) {
                clearFamilySwitchSession(cart, latchedFamily);
                return connectedFamily;
            }
            return latchedFamily;
        }

        // STEP_9_3E_T5_R7_SMALL_LASTTRACK_OWNERSHIP
        //
        // t5-r6 placement can put several restored OG Small L-footprints close
        // enough that a different Small's physical gag cell is directly under
        // the cart before the current mathematical diagonal reaches its endpoint.
        // TC4.5 kept lastTrack ownership in this situation. Preserve the exact
        // latched Small session until applyLinearDiagonal reaches a real connected
        // endpoint and clears it; only then may the next Small/crossing take over.
        Context latchedSmall = findLatchedLinearContext(cart, base);
        if (latchedSmall != null
                && "track_diagonal_straight_small".equals(latchedSmall.spec.id())) {
            // STEP_9_3E_T5_R8_CROSSING_ADJACENT_SMALL_BRIDGE_HANDOFF
            //
            // Fresh-world r7 traces proved ordinary repeated Small->Small pairs
            // work, but the first extension outside a crossing-owned Small has a
            // different OG compatibility spacing:
            //
            //   ordinary pair:       shared logical endpoint
            //   crossing first pair: candidate endpoint is one clockwise
            //                        cardinal cell from that logical endpoint
            //
            // Keep r7 lastTrack ownership everywhere else. Transfer only when
            // this exact one-cell bridge is reached, in the direction of travel.
            Context bridge = findOriginalSmallCrossingBridgeContext(
                    cart, base, latchedSmall, handoffMotion);
            if (bridge != null) {
                return bridge;
            }
            return latchedSmall;
        }

        boolean directRailSeen = false;
        for (BlockPos pos : direct) {
            BlockState state = cart.level().getBlockState(pos);
            if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
                Context directContext = new Context(batch, state, pos);
                String id = directContext.spec.id();

                // Step 9.3d-t8: TC4.5 gag cells could reference more than one
                // mathematical rail root. Our batch blocks cannot literally hold
                // multiple block entities in one cell, so emulate that ownership
                // rule here. When a Diagonal Straight root occupies the original
                // Medium 45-degree switch gag corridor, keep the active switch
                // as owner until the cart reaches the true 3.75-radius arc endpoint.
                // This prevents the physical diagonal proxy block from stealing the
                // cart before the curve mathematics have finished.
                if (isDiagonalStraightFamily(id)) {
                    Context sharedProxyOwner = findOriginalTraincraftSharedProxyOwner(
                            cart, base, directContext, handoffMotion);
                    if (sharedProxyOwner != null) {
                        return sharedProxyOwner;
                    }
                }

                // TC4.5 root ownership rule: while the bogie is physically on an
                // ACTIVE Medium 45 switch segment, the switch-owned 3.75-radius
                // mathematical route wins outright. Do not let a nearby diagonal
                // guide/root steal ownership early merely because its block is
                // closer. Once the cart actually reaches the shared proxy/root,
                // findOriginalTraincraftSharedProxyOwner() above keeps the switch
                // owner until the true arc endpoint, then the diagonal root takes
                // over. This mirrors TileTCRailGag -> TileTCRail ownership.
                if (activeTargetFortyFiveSwitchDefinition(directContext) != null) {
                    return directContext;
                }

                // Step 9.3f-t1: every physical cell of a standard Small
                // switch is a proxy for the same root-owned route. This must
                // happen before generic BaseRailBlock handling so inactive
                // branch cells cannot rotate the train independently.
                if (isTargetSmallSwitch(directContext)) {
                    return directContext;
                }

                // STEP_9_3G_T4_R2_WHOLE_FAMILY_DIRECT_CONTEXT_OWNERSHIP
                //
                // t4-r1 added the family movement/yaw samplers but missed the
                // direct-context admission hook here. That made every new family
                // branch unreachable: a Medium/Large/Very Large/45/Parallel gag
                // cell fell through as a generic BaseRailBlock, directRailSeen
                // became true, and findContext() returned null before
                // applyFamilySwitch() or continuousFacingYaw() could own it.
                //
                // Match the proven Small-switch ownership rule: every physical
                // family gag cell is a proxy for one root-owned turnout route.
                // Once admitted, findLatchedFamilySwitchContext() above preserves
                // ownership across guide gaps until a real external connector is
                // crossed.
                if (isTargetClassicFamilySwitch(directContext)
                        && canBeginFreshFamilySwitchOwnership(
                                cart, directContext, handoffMotion)) {
                    return directContext;
                }

                // Other 45-degree feeders/crossings keep the existing topology
                // handoff behavior. The focused original Medium 45 switches are
                // deliberately excluded above so their root owns the full arc.
                if (isDiagonalFeeder(directContext)
                        && isNearDiagonalFeederEnd(directContext, cart)
                        && isMovingTowardDiagonalHandoff(
                                directContext, cart, handoffMotion)) {
                    Context handoff = findNearbyLinearContext(cart, base, directContext, handoffMotion);
                    if (handoff != null) {
                        return handoff;
                    }
                }

                if (isContinuousCurve(directContext)
                        || isContinuousLinearDiagonal(id)
                        || isStraightSlope(id)) {
                    return directContext;
                }
            }
            if (state.getBlock() instanceof BaseRailBlock) {
                directRailSeen = true;
            }
        }

        // If a real rail directly owns the cart (for example normal track or an
        // inactive switch route), do not let a nearby continuous path recapture it.
        if (directRailSeen) {
            return null;
        }

        // Step 9.3c-t4-r7d retains r7c fallback: once a continuous diagonal route has been selected,
        // keep that exact crossing assembly as the owner while the visible route
        // passes through cells that are not occupied by the old Manhattan guide.
        // This is deliberately session-keyed; it cannot capture an unrelated
        // nearby crossing, and a true rail directly under the cart still wins.
        Context latched = findLatchedLinearContext(cart, base);
        if (latched != null) {
            return latched;
        }

        // A mathematically smooth centerline can pass through the diagonal half
        // of a block while the hidden Manhattan guide occupies a neighboring
        // cell. Search one cell around the cart for continuous flat geometry.
        Context best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    Context candidate = new Context(batch, state, pos);
                    String id = candidate.spec.id();
                    if (!isContinuousCurve(candidate)
                            && !isContinuousLinearDiagonal(id)) {
                        continue;
                    }

                    double cx = pos.getX() + 0.5D;
                    double cz = pos.getZ() + 0.5D;
                    double distSq = (cart.getX() - cx) * (cart.getX() - cx)
                            + (cart.getZ() - cz) * (cart.getZ() - cz);
                    if (distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        best = candidate;
                    }
                }
            }
        }

        return bestDistanceSq <= NEARBY_CONTEXT_MAX_DISTANCE_SQ ? best : null;
    }

    /**
     * Step 9.3c-t4-r7c live-session context recovery.
     *
     * r7b runtime traces proved the selected route remains correct but the cart
     * can reach roughly 1.6 blocks from the nearest hidden guide rail and lose
     * vanilla rail ownership before the rendered crossing rail ends. The t3
     * session already stores the exact assembly key, route and progress. Recover
     * only that same assembly inside a small bounded scan so applyLinearDiagonal
     * can continue advancing the visible centerline through the guide gap.
     */
    private static Context findLatchedLinearContext(AbstractMinecart cart,
                                                    BlockPos base) {
        var data = cart.getPersistentData();
        if (!data.contains(LINEAR_CONTEXT_KEY)
                || !data.contains(LINEAR_ROUTE_KEY)
                || !data.contains(LINEAR_PROGRESS_KEY)
                || !data.contains(LINEAR_TICK_KEY)) {
            return null;
        }

        long now = cart.level().getGameTime();
        long lastTick = data.getLong(LINEAR_TICK_KEY);
        if (now < lastTick || now - lastTick > LINEAR_SESSION_MAX_TICK_GAP) {
            return null;
        }

        String expectedContextKey = data.getString(LINEAR_CONTEXT_KEY);
        int routeIndex = data.getInt(LINEAR_ROUTE_KEY);
        Context best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (int dy = -LATCHED_LINEAR_CONTEXT_VERTICAL_RADIUS;
             dy <= LATCHED_LINEAR_CONTEXT_VERTICAL_RADIUS;
             dy++) {
            for (int dx = -LATCHED_LINEAR_CONTEXT_HORIZONTAL_RADIUS;
                 dx <= LATCHED_LINEAR_CONTEXT_HORIZONTAL_RADIUS;
                 dx++) {
                for (int dz = -LATCHED_LINEAR_CONTEXT_HORIZONTAL_RADIUS;
                     dz <= LATCHED_LINEAR_CONTEXT_HORIZONTAL_RADIUS;
                     dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    Context candidate = new Context(batch, state, pos);
                    if (!isContinuousLinearDiagonal(candidate.spec.id())
                            || !expectedContextKey.equals(linearContextKey(candidate))) {
                        continue;
                    }

                    LinearRoute[] routes = linearRoutes(candidate);
                    if (routeIndex < 0 || routeIndex >= routes.length) {
                        continue;
                    }

                    LinearRoute route = routes[routeIndex];
                    double progress = route.projectedProgress(cart.getX(), cart.getZ());
                    if (progress < -LINEAR_ENDPOINT_EXTENSION
                            || progress > route.length + LINEAR_ENDPOINT_EXTENSION) {
                        continue;
                    }

                    double cx = pos.getX() + 0.5D;
                    double cz = pos.getZ() + 0.5D;
                    double distSq = (cart.getX() - cx) * (cart.getX() - cx)
                            + (cart.getZ() - cz) * (cart.getZ() - cz);
                    if (distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Step 9.3e-t5-r8 fresh-world crossing-adjacent Small bridge.
     *
     * Crossing -> Small placement intentionally marks the first Small with
     * original_visual_root=true so its OBJ stays aligned with the centered
     * TC4.5 crossing. Repeated r6 extensions use normal visual ownership.
     * The first normal extension therefore does not share the exact logical
     * endpoint used by later Small->Small pairs: its endpoint is one clockwise
     * cardinal cell from the crossing-owned Small's logical endpoint.
     *
     * This is deliberately gated by the original_visual_root transition and
     * equal assembly facing. It cannot broaden ordinary Small proximity search
     * or connect another crossing arm.
     */
    private static boolean isOriginalSmallCrossingFirstExtensionPair(
            Context source,
            LegacyBatchTrackSpec.Endpoint sourceEndpoint,
            Context candidate,
            LegacyBatchTrackSpec.Endpoint candidateEndpoint) {
        if (source == null || candidate == null
                || sourceEndpoint == null || candidateEndpoint == null
                || !"track_diagonal_straight_small".equals(source.spec.id())
                || !"track_diagonal_straight_small".equals(candidate.spec.id())
                || source.facing != candidate.facing) {
            return false;
        }

        boolean sourceVisual = stateBooleanByName(
                source.state, "original_visual_root");
        boolean candidateVisual = stateBooleanByName(
                candidate.state, "original_visual_root");
        if (sourceVisual == candidateVisual) {
            return false;
        }

        Direction sourceOutward = source.spec.rotateDirection(
                source.facing, sourceEndpoint.outward());
        Direction candidateOutward = candidate.spec.rotateDirection(
                candidate.facing, candidateEndpoint.outward());
        if (candidateOutward != sourceOutward.getOpposite()) {
            return false;
        }

        BlockPos sourceLogical = source.spec.endpointPosition(
                source.root, source.facing, sourceEndpoint);
        BlockPos candidateLogical = candidate.spec.endpointPosition(
                candidate.root, candidate.facing, candidateEndpoint);

        return candidateLogical.equals(
                sourceLogical.relative(sourceOutward.getClockWise()));
    }

    private static boolean stateBooleanByName(BlockState state, String name) {
        if (state == null || name == null) {
            return false;
        }
        for (var entry : state.getValues().entrySet()) {
            if (name.equals(entry.getKey().getName())) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    /**
     * Find only the special crossing-owned Small <-> first normal extension
     * bridge. Ordinary Small->Small ownership remains exactly r7/t5-r4.
     */
    private static Context findOriginalSmallCrossingBridgeContext(
            AbstractMinecart cart,
            BlockPos base,
            Context source,
            Vec3 handoffMotion) {
        if (source == null
                || !"track_diagonal_straight_small".equals(source.spec.id())) {
            return null;
        }

        LinearRoute[] routes = linearRoutes(source);
        if (routes.length == 0) {
            return null;
        }

        LinearRoute route = routes[0];
        double motionAlong = handoffMotion.x * route.tangent.x
                + handoffMotion.z * route.tangent.z;
        if (Math.abs(motionAlong) <= VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED) {
            return null;
        }

        double progress = route.projectedProgress(cart.getX(), cart.getZ());
        boolean towardEnd = motionAlong > 0.0D;
        double endpointDistance = towardEnd
                ? Math.abs(route.length - progress)
                : Math.abs(progress);
        if (endpointDistance > SMALL_CROSSING_BRIDGE_HANDOFF_DISTANCE) {
            return null;
        }

        int sourceEndpointPart = linearEndpointPart(source, 0, towardEnd);
        if (sourceEndpointPart < 0) {
            return null;
        }

        LegacyBatchTrackSpec.Endpoint selectedSourceEndpoint = null;
        for (LegacyBatchTrackSpec.Endpoint endpoint : source.spec.endpoints()) {
            if (endpoint.part() == sourceEndpointPart) {
                selectedSourceEndpoint = endpoint;
                break;
            }
        }
        if (selectedSourceEndpoint == null) {
            return null;
        }

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)
                            || !"track_diagonal_straight_small".equals(
                                    batch.getSpec().id())) {
                        continue;
                    }

                    Context candidate = new Context(batch, state, pos);
                    if (candidate.root.equals(source.root)
                            && candidate.facing == source.facing) {
                        continue;
                    }

                    for (LegacyBatchTrackSpec.Endpoint candidateEndpoint
                            : candidate.spec.endpoints()) {
                        if (!isOriginalSmallCrossingFirstExtensionPair(
                                source, selectedSourceEndpoint,
                                candidate, candidateEndpoint)) {
                            continue;
                        }

                        armPendingVirtualHandoff(cart, candidate, 0);
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    // STEP_9_3E_T5_R4_EXACT_OG_SMALL_LOGICAL_HANDOFF
    //
    // t5-r3 placement restored the TC4.5 Small->Small topology: the next
    // Small's real/root cell starts ON the previous Small's empty logical
    // diagonal continuation point. That means Small->Small movement does NOT
    // have a one-cardinal-cell connector gap. Require that exact shared logical
    // point and never fall back to a merely-nearby Small assembly.
    private static Context findNearbyLinearContext(AbstractMinecart cart,
                                                   BlockPos base,
                                                   Context source,
                                                   Vec3 handoffMotion) {
        Context best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = cart.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    Context candidate = new Context(batch, state, pos);
                    if (!isContinuousLinearDiagonal(candidate.spec.id())) {
                        continue;
                    }
                    if (candidate.spec.id().equals(source.spec.id())
                            && candidate.root.equals(source.root)) {
                        continue;
                    }

                    // Step 9.3c-t4-r7b: prefer an exact connector-topology match
                    // through r7 logical endpoint metadata over proximity to the
                    // hidden staircase guide. This is what lets the active 45°
                    // feeder hand ownership to the Diagonal Crossing while the
                    // model remains at its visually correct physical root.
                    int virtualRoute = virtualEndpointHandoffRoute(source, candidate);
                    if (virtualRoute >= 0) {
                        // STEP_9_3E_T3_R1A_STRICT_CROSSING_HANDOFF_DIRECTION
                        //
                        // The t3-d1 flight recorder proved that a live crossing
                        // session could hand ownership to a connected Small
                        // Diagonal while the locomotive was still entering the
                        // crossing. It also proved that an outgoing Small could
                        // hand straight back to the crossing while moving away.
                        //
                        // Keep every non-crossing handoff unchanged. Where the
                        // diagonal crossing participates, require the exact
                        // connected endpoint AND motion toward the handoff.
                        if (!crossingHandoffDirectionMatches(
                                cart, source, candidate, handoffMotion)) {
                            continue;
                        }
                        armPendingVirtualHandoff(cart, candidate, virtualRoute);
                        return candidate;
                    }

                    // t5-r4: a Small->Small transition is topology-owned. The
                    // t5-r3 pieces share the exact OG logical diagonal point,
                    // so a failed exact match must NOT degrade to the generic
                    // nearest-linear search. That fallback is what can select a
                    // different nearby Small branch and produce a lateral snap.
                    boolean strictSmallPair =
                            "track_diagonal_straight_small".equals(source.spec.id())
                            && "track_diagonal_straight_small".equals(candidate.spec.id());
                    if (strictSmallPair) {
                        continue;
                    }

                    double distance = linearContextDistance(
                            candidate, cart.getX(), cart.getZ());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return bestDistance <= FEEDER_TO_LINEAR_MAX_PERP ? best : null;
    }

    /**
     * Step 9.3e-t3-r2 crossing + Small-to-Small directional handoff gate.
     *
     * TC4.5 crossing ownership is lastTrack-style and directional. A crossing
     * session may hand out only through the endpoint of its already-selected
     * route that the bogie is actually leaving. A Small Diagonal may hand into
     * the crossing only while moving toward the Small endpoint connected to it.
     *
     * The method is intentionally a no-op except for handoffs involving
     * track_diagonal_crossing or a Small-Diagonal-to-Small-Diagonal pair,
     * preserving the accepted Medium-45 and all other diagonal behavior.
     */
    private static boolean crossingHandoffDirectionMatches(
            AbstractMinecart cart,
            Context source,
            Context candidate,
            Vec3 handoffMotion) {
        boolean sourceCrossing =
                TARGET_DIAGONAL_CROSSING.equals(source.spec.id());
        boolean candidateCrossing =
                TARGET_DIAGONAL_CROSSING.equals(candidate.spec.id());
        boolean sourceSmall =
                "track_diagonal_straight_small".equals(source.spec.id());
        boolean candidateSmall =
                "track_diagonal_straight_small".equals(candidate.spec.id());
        boolean strictSmallPair = sourceSmall && candidateSmall;

        if (!sourceCrossing && !candidateCrossing && !strictSmallPair) {
            return true;
        }

        double horizontal = Math.hypot(handoffMotion.x, handoffMotion.z);
        if (horizontal <= VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED) {
            return false;
        }

        int requiredSourceEndpointPart = -1;

        if (sourceCrossing) {
            var data = cart.getPersistentData();
            String sourceKey = linearContextKey(source);
            if (!data.contains(LINEAR_CONTEXT_KEY)
                    || !data.contains(LINEAR_ROUTE_KEY)
                    || !data.contains(LINEAR_PROGRESS_KEY)
                    || !data.contains(LINEAR_TICK_KEY)
                    || !data.contains(LINEAR_SIGN_KEY)
                    || !sourceKey.equals(data.getString(LINEAR_CONTEXT_KEY))) {
                return false;
            }

            long now = cart.level().getGameTime();
            long lastTick = data.getLong(LINEAR_TICK_KEY);
            if (now < lastTick || now - lastTick > LINEAR_SESSION_MAX_TICK_GAP) {
                return false;
            }

            LinearRoute[] routes = linearRoutes(source);
            int routeIndex = data.getInt(LINEAR_ROUTE_KEY);
            if (routeIndex < 0 || routeIndex >= routes.length) {
                return false;
            }

            LinearRoute route = routes[routeIndex];
            double progress = data.getDouble(LINEAR_PROGRESS_KEY);
            double routeSign = data.getDouble(LINEAR_SIGN_KEY);
            double motionAlong = handoffMotion.x * route.tangent.x
                    + handoffMotion.z * route.tangent.z;

            boolean leavingStart = routeSign < 0.0D
                    && progress <= ENDPOINT_RELEASE_DISTANCE
                    && motionAlong < -VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED;
            boolean leavingEnd = routeSign > 0.0D
                    && progress >= route.length - ENDPOINT_RELEASE_DISTANCE
                    && motionAlong > VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED;

            if (!leavingStart && !leavingEnd) {
                return false;
            }

            requiredSourceEndpointPart = linearEndpointPart(
                    source, routeIndex, leavingEnd);
            if (requiredSourceEndpointPart < 0) {
                return false;
            }
        } else if (strictSmallPair) {
            // STEP_9_3E_T3_R2_STRICT_SMALL_TO_SMALL_HANDOFF_DIRECTION
            //
            // d2 proved that the generic topology bridge could choose the
            // opposite connector of a Small Diagonal.  Example: while a south-
            // facing Small was travelling toward its start/crossing end, the
            // bridge handed ownership back to the Small behind it.  Select the
            // source endpoint from actual travel along the source centerline,
            // then require the candidate to be connected to that exact endpoint.
            LinearRoute[] sourceRoutes = linearRoutes(source);
            if (sourceRoutes.length == 0) {
                return false;
            }

            LinearRoute sourceRoute = sourceRoutes[0];
            double motionAlong = handoffMotion.x * sourceRoute.tangent.x
                    + handoffMotion.z * sourceRoute.tangent.z;
            if (Math.abs(motionAlong) <= VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED) {
                return false;
            }

            boolean towardEnd = motionAlong > 0.0D;
            requiredSourceEndpointPart = linearEndpointPart(
                    source, 0, towardEnd);
            if (requiredSourceEndpointPart < 0) {
                return false;
            }
        }

        for (LegacyBatchTrackSpec.Endpoint sourceEndpoint : source.spec.endpoints()) {
            if (requiredSourceEndpointPart >= 0
                    && sourceEndpoint.part() != requiredSourceEndpointPart) {
                continue;
            }

            BlockPos sourceEndpointPos = source.spec.endpointPosition(
                    source.root, source.facing, sourceEndpoint);
            Direction sourceOutward = source.spec.rotateDirection(
                    source.facing, sourceEndpoint.outward());

            // t5-r4 / OG Small logical continuation:
            // t5-r3 places the next Small root directly on the source Small's
            // empty logical diagonal endpoint. For Small->Small there is no
            // extra cardinal connector cell between the two mathematical
            // routes. Crossing and all other families retain their established
            // one-cell connector semantics.
            BlockPos requiredCell = strictSmallPair
                    ? sourceEndpointPos
                    : sourceEndpointPos.relative(sourceOutward);

            for (LegacyBatchTrackSpec.Endpoint candidateEndpoint
                    : candidate.spec.endpoints()) {
                Direction candidateOutward = candidate.spec.rotateDirection(
                        candidate.facing, candidateEndpoint.outward());
                if (candidateOutward != sourceOutward.getOpposite()) {
                    continue;
                }

                BlockPos candidateEndpointPos = candidate.spec.endpointPosition(
                        candidate.root, candidate.facing, candidateEndpoint);
                boolean endpointMatches = candidateEndpointPos.equals(requiredCell);
                if (!endpointMatches
                        && strictSmallPair
                        && isOriginalSmallCrossingFirstExtensionPair(
                                source, sourceEndpoint,
                                candidate, candidateEndpoint)) {
                    endpointMatches = true;
                }
                if (!endpointMatches) {
                    continue;
                }

                // Crossing -> feeder: the stored crossing route/sign above
                // already selected the exact endpoint being left.
                if (sourceCrossing) {
                    return true;
                }

                // Small -> Crossing: the same connector is valid in both travel
                // directions, but only motion TOWARD this Small endpoint may
                // transfer ownership into the crossing.
                if (candidateCrossing
                        && isDiagonalStraightFamily(source.spec.id())) {
                    LinearRoute[] sourceRoutes = linearRoutes(source);
                    if (sourceRoutes.length == 0) {
                        return false;
                    }

                    LinearRoute sourceRoute = sourceRoutes[0];
                    int startPart = linearEndpointPart(source, 0, false);
                    int endPart = linearEndpointPart(source, 0, true);
                    double motionAlong = handoffMotion.x * sourceRoute.tangent.x
                            + handoffMotion.z * sourceRoute.tangent.z;

                    if (sourceEndpoint.part() == startPart) {
                        return motionAlong
                                < -VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED;
                    }
                    if (sourceEndpoint.part() == endPart) {
                        return motionAlong
                                > VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED;
                    }
                    return false;
                }

                // Preserve already-accepted non-Small feeders (notably the
                // Medium-45 machinery) when the crossing is the candidate.
                return true;
            }
        }

        return false;
    }

    /**
     * Step 9.3c-t4-r7b connector-topology bridge.
     *
     * Placement already uses LegacyBatchTrackSpec.endpointPosition(), which may
     * differ from the physical guide part. Movement must make the same decision
     * or the active 45-degree feeder can visually meet the crossing while the
     * t3 route selector still chooses by Manhattan-guide proximity alone.
     *
     * Returns the crossing linear-route index whose logical endpoint is exactly
     * in the source endpoint's connector cell with opposite outward direction.
     */
    private static int virtualEndpointHandoffRoute(Context source, Context candidate) {
        if (!isContinuousLinearDiagonal(candidate.spec.id())) {
            return -1;
        }

        // Step 9.3d-t5: original TC4.5 did not require the 45-degree
        // switch root/guide cell to be face-adjacent to a DIAGONAL root. Its
        // gag corridor kept the bogie owned until the 3.75-radius arc met the
        // mathematical 45-degree line. Recreate that topology numerically:
        // the switch endpoint must lie on the candidate route's backward
        // endpoint extension and both tangents must agree.
        OffsetDefinition originalSwitch =
                activeTargetFortyFiveSwitchDefinition(source);
        if (originalSwitch != null
                && isOriginalTraincraftMediumFortyFiveSwitch(source.spec.id())
                && isDiagonalStraightFamily(candidate.spec.id())) {
            double sourceTotal = fortyFiveTotalLength(
                    source, originalSwitch.lateral, originalSwitch.forward);
            CurveSample sourceEnd = sampleFortyFiveCurveAtProgress(
                    source, sourceTotal,
                    originalSwitch.lateral, originalSwitch.forward);
            if (sourceEnd != null) {
                LinearRoute[] routes = linearRoutes(candidate);
                for (int routeIndex = 0; routeIndex < routes.length; routeIndex++) {
                    LinearRoute route = routes[routeIndex];
                    double tangentDot = sourceEnd.tangent.x * route.tangent.x
                            + sourceEnd.tangent.z * route.tangent.z;
                    double projected = route.projectedProgress(
                            sourceEnd.targetHorizontal.x,
                            sourceEnd.targetHorizontal.z);
                    double perpendicular = route.perpendicularDistance(
                            sourceEnd.targetHorizontal.x,
                            sourceEnd.targetHorizontal.z);
                    if (Math.abs(tangentDot)
                                    >= ORIGINAL_TC45_HANDOFF_MIN_TANGENT_DOT
                            && projected >= -LINEAR_ENDPOINT_EXTENSION
                            && projected <= ENDPOINT_RELEASE_DISTANCE
                            && perpendicular <= ORIGINAL_TC45_HANDOFF_MAX_PERP) {
                        return routeIndex;
                    }
                }
            }
        }

        boolean strictSmallPair =
                "track_diagonal_straight_small".equals(source.spec.id())
                && "track_diagonal_straight_small".equals(candidate.spec.id());

        for (LegacyBatchTrackSpec.Endpoint sourceEndpoint : source.spec.endpoints()) {
            BlockPos sourceEndpointPos = source.spec.endpointPosition(
                    source.root, source.facing, sourceEndpoint);
            Direction sourceOutward = source.spec.rotateDirection(
                    source.facing, sourceEndpoint.outward());

            // t5-r4: Small->Small pieces installed by t5-r3 share the logical
            // diagonal endpoint itself. Do not add sourceOutward here or the
            // search skips the immediately-connected Small and can acquire a
            // farther assembly.
            BlockPos requiredCell = strictSmallPair
                    ? sourceEndpointPos
                    : sourceEndpointPos.relative(sourceOutward);

            for (LegacyBatchTrackSpec.Endpoint candidateEndpoint : candidate.spec.endpoints()) {
                Direction candidateOutward = candidate.spec.rotateDirection(
                        candidate.facing, candidateEndpoint.outward());
                if (candidateOutward != sourceOutward.getOpposite()) {
                    continue;
                }

                BlockPos logicalEndpoint = candidate.spec.endpointPosition(
                        candidate.root, candidate.facing, candidateEndpoint);
                boolean endpointMatches = logicalEndpoint.equals(requiredCell);
                if (!endpointMatches
                        && strictSmallPair
                        && isOriginalSmallCrossingFirstExtensionPair(
                                source, sourceEndpoint,
                                candidate, candidateEndpoint)) {
                    endpointMatches = true;
                }
                if (!endpointMatches) {
                    continue;
                }

                if (isDiagonalStraightFamily(candidate.spec.id())) {
                    return 0;
                }
                return crossingRouteIndexForEndpoint(
                        candidate, candidateEndpoint.part());
            }
        }

        return -1;
    }

    private static int crossingRouteIndexForEndpoint(Context context, int endpointPart) {
        if (!isCrossingDiagonalFamily(context.spec.id())) {
            return -1;
        }

        // Step 9.3e-t1: no endpoint is remapped by a visual-anchor exception.
        // TC4.5 keeps the incoming diagonal owner through the crossing, so each
        // endpoint belongs to the geometric corner-to-corner diagonal that
        // actually contains it.

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int part = 0; part < context.spec.partCount(); part++) {
            LegacyBatchTrackSpec.Point point = context.spec.pointForPart(part);
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }

        LegacyBatchTrackSpec.Point endpoint = context.spec.pointForPart(endpointPart);
        if ((endpoint.x == minX && endpoint.z == maxZ)
                || (endpoint.x == maxX && endpoint.z == minZ)) {
            return 0;
        }
        if ((endpoint.x == maxX && endpoint.z == maxZ)
                || (endpoint.x == minX && endpoint.z == minZ)) {
            return 1;
        }
        return -1;
    }

    private static void armPendingVirtualHandoff(AbstractMinecart cart,
                                                 Context context,
                                                 int routeIndex) {
        armPendingVirtualHandoff(cart, context, routeIndex, Double.NaN);
    }

    private static void armPendingVirtualHandoff(AbstractMinecart cart,
                                                 Context context,
                                                 int routeIndex,
                                                 double progress) {
        var data = cart.getPersistentData();
        data.putString(LINEAR_HANDOFF_CONTEXT_KEY, linearContextKey(context));
        data.putInt(LINEAR_HANDOFF_ROUTE_KEY, routeIndex);
        if (Double.isFinite(progress)) {
            data.putDouble(LINEAR_HANDOFF_PROGRESS_KEY, progress);
        } else {
            data.remove(LINEAR_HANDOFF_PROGRESS_KEY);
        }
        data.putLong(LINEAR_HANDOFF_TICK_KEY, cart.level().getGameTime());
    }

    private static int pendingVirtualHandoffRoute(AbstractMinecart cart,
                                                  Context context,
                                                  long now,
                                                  int routeCount) {
        var data = cart.getPersistentData();
        if (!data.contains(LINEAR_HANDOFF_CONTEXT_KEY)
                || !data.contains(LINEAR_HANDOFF_ROUTE_KEY)
                || !data.contains(LINEAR_HANDOFF_TICK_KEY)) {
            return -1;
        }
        if (!linearContextKey(context).equals(data.getString(LINEAR_HANDOFF_CONTEXT_KEY))) {
            return -1;
        }

        long armedTick = data.getLong(LINEAR_HANDOFF_TICK_KEY);
        int routeIndex = data.getInt(LINEAR_HANDOFF_ROUTE_KEY);
        if (routeIndex < 0
                || routeIndex >= routeCount
                || now < armedTick
                || now - armedTick > LINEAR_HANDOFF_MAX_TICK_GAP) {
            clearPendingVirtualHandoff(cart, context);
            return -1;
        }
        return routeIndex;
    }

    private static double pendingVirtualHandoffProgress(AbstractMinecart cart,
                                                        Context context,
                                                        long now) {
        var data = cart.getPersistentData();
        if (!data.contains(LINEAR_HANDOFF_CONTEXT_KEY)
                || !data.contains(LINEAR_HANDOFF_PROGRESS_KEY)
                || !data.contains(LINEAR_HANDOFF_TICK_KEY)
                || !linearContextKey(context).equals(
                        data.getString(LINEAR_HANDOFF_CONTEXT_KEY))) {
            return Double.NaN;
        }
        long armedTick = data.getLong(LINEAR_HANDOFF_TICK_KEY);
        if (now < armedTick || now - armedTick > LINEAR_HANDOFF_MAX_TICK_GAP) {
            return Double.NaN;
        }
        return data.getDouble(LINEAR_HANDOFF_PROGRESS_KEY);
    }

    private static void clearPendingVirtualHandoff(AbstractMinecart cart, Context context) {
        var data = cart.getPersistentData();
        if (!data.contains(LINEAR_HANDOFF_CONTEXT_KEY)) {
            return;
        }
        if (!linearContextKey(context).equals(data.getString(LINEAR_HANDOFF_CONTEXT_KEY))) {
            return;
        }
        data.remove(LINEAR_HANDOFF_CONTEXT_KEY);
        data.remove(LINEAR_HANDOFF_ROUTE_KEY);
        data.remove(LINEAR_HANDOFF_PROGRESS_KEY);
        data.remove(LINEAR_HANDOFF_TICK_KEY);
    }

    private static double linearContextDistance(Context context,
                                                double worldX,
                                                double worldZ) {
        double best = Double.POSITIVE_INFINITY;
        for (LinearRoute route : linearRoutes(context)) {
            double progress = route.projectedProgress(worldX, worldZ);
            if (progress < -LINEAR_ENDPOINT_EXTENSION
                    || progress > route.length + LINEAR_ENDPOINT_EXTENSION) {
                continue;
            }
            best = Math.min(best, route.perpendicularDistance(worldX, worldZ));
        }
        return best;
    }

    private static boolean isDiagonalFeeder(Context context) {
        String id = context.spec.id();
        return fortyFiveDefinition(id) != null
                || isDiagonalStraightFamily(id)
                || isCrossingDiagonalFamily(id)
                || (switchFortyFiveDefinition(id) != null && stateActive(context.state));
    }

    private static boolean isMovingTowardDiagonalHandoff(Context context,
                                                          AbstractMinecart cart,
                                                          Vec3 motion) {
        double horizontal = Math.hypot(motion.x, motion.z);
        if (horizontal <= VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED) {
            return false;
        }

        OffsetDefinition switchDefinition = switchFortyFiveDefinition(context.spec.id());
        if (switchDefinition != null && stateActive(context.state)) {
            double[] local = toCanonical(
                    context.root, context.facing, cart.getX(), cart.getZ());
            CurveSample sample = sampleFortyFiveCurve(
                    context,
                    local[0],
                    local[1],
                    switchDefinition.lateral,
                    switchDefinition.forward);
            if (sample == null) {
                return false;
            }
            double towardEnd = motion.x * sample.tangent.x
                    + motion.z * sample.tangent.z;
            return towardEnd > VIRTUAL_HANDOFF_MIN_HORIZONTAL_SPEED;
        }

        // Other diagonal feeders keep the t3 behavior once there is real motion.
        // The zero-speed guard is the critical r7b regression fix.
        return true;
    }

    private static boolean isNearDiagonalFeederEnd(Context context,
                                                   AbstractMinecart cart) {
        String id = context.spec.id();

        OffsetDefinition switchDefinition = switchFortyFiveDefinition(id);
        if (switchDefinition != null && stateActive(context.state)) {
            double[] local = toCanonical(
                    context.root, context.facing, cart.getX(), cart.getZ());
            CurveSample sample = sampleFortyFiveCurve(
                    context,
                    local[0],
                    local[1],
                    switchDefinition.lateral,
                    switchDefinition.forward);
            return sample != null && sample.distanceToEnd <= FEEDER_HANDOFF_DISTANCE;
        }

        OffsetDefinition fortyFive = fortyFiveDefinition(id);
        if (fortyFive != null) {
            CurveSample sample = sampleCurve(context, cart.getX(), cart.getZ());
            return sample != null
                    && Math.min(sample.distanceFromStart, sample.distanceToEnd)
                    <= FEEDER_HANDOFF_DISTANCE;
        }

        if (isCrossingDiagonalFamily(id)) {
            LinearRoute[] routes = linearRoutes(context);
            if (routes.length == 0) {
                return false;
            }

            var data = cart.getPersistentData();
            String contextKey = linearContextKey(context);
            if (data.contains(LINEAR_CONTEXT_KEY)
                    && data.contains(LINEAR_ROUTE_KEY)
                    && contextKey.equals(data.getString(LINEAR_CONTEXT_KEY))) {
                int routeIndex = data.getInt(LINEAR_ROUTE_KEY);
                if (routeIndex >= 0 && routeIndex < routes.length) {
                    LinearRoute route = routes[routeIndex];
                    double progress = data.contains(LINEAR_PROGRESS_KEY)
                            ? data.getDouble(LINEAR_PROGRESS_KEY)
                            : route.projectedProgress(cart.getX(), cart.getZ());
                    return Math.min(Math.abs(progress),
                            Math.abs(route.length - progress))
                            <= FEEDER_HANDOFF_DISTANCE;
                }
            }
        }

        if (isDiagonalStraightFamily(id)) {
            LinearRoute[] routes = linearRoutes(context);
            if (routes.length == 0) {
                return false;
            }
            LinearRoute route = routes[0];
            double progress = route.projectedProgress(cart.getX(), cart.getZ());
            return Math.min(Math.abs(progress), Math.abs(route.length - progress))
                    <= FEEDER_HANDOFF_DISTANCE;
        }

        return false;
    }

    private static boolean isTargetSmallSwitch(Context context) {
        if (context == null) {
            return false;
        }
        String id = context.spec.id();
        return TARGET_SMALL_LEFT_SWITCH.equals(id)
                || TARGET_SMALL_RIGHT_SWITCH.equals(id);
    }

    private static boolean isTargetStandardMediumFamilySwitch(Context context) {
        return context != null
                && isTargetStandardMediumFamilySwitch(context.spec.id());
    }

    private static boolean isTargetStandardMediumFamilySwitch(String id) {
        return "track_switch_medium_left".equals(id)
                || "track_switch_medium_right".equals(id);
    }

    private static boolean isTargetClassicFamilySwitch(Context context) {
        return context != null
                && isTargetClassicFamilySwitch(context.spec.id());
    }

    private static boolean isTargetClassicFamilySwitch(String id) {
        return switch (id) {
            case "track_switch_medium_left",
                 "track_switch_medium_right",
                 "track_switch_large_left",
                 "track_switch_large_right",
                 "track_switch_very_large_left",
                 "track_switch_very_large_right",
                 TARGET_45_MEDIUM_LEFT_SWITCH,
                 TARGET_45_MEDIUM_RIGHT_SWITCH,
                 "track_switch_parallel_left",
                 "track_switch_parallel_right" -> true;
            default -> false;
        };
    }

    private static boolean isTargetFortyFiveFamilySwitch(String id) {
        return TARGET_45_MEDIUM_LEFT_SWITCH.equals(id)
                || TARGET_45_MEDIUM_RIGHT_SWITCH.equals(id);
    }

    private static boolean isTargetParallelFamilySwitch(String id) {
        return "track_switch_parallel_left".equals(id)
                || "track_switch_parallel_right".equals(id);
    }

    /**
     * STEP_9_3G_T4_R8C_MEDIUM_HANDED_STATE_ROUTING
     *
     * STEP_9_3G_T4_R8E_MEDIUM_VERIFIED_STATE_POLARITY
     *
     * Runtime F3 verification on BOTH Medium handed pieces shows:
     *
     *   active=false -> STRAIGHT
     *   active=true  -> BRANCH / DIVERT
     *
     * This helper is consulted only when a NEW family-switch route genuinely
     * enters through the common/toe. For Standard Medium that common/toe is p10.
     * Trailing p4 straight and p0 branch entries remain physical-route-owned
     * and ignore switch state.
     *
     * Other family switches retain their existing polarity until Medium is
     * runtime-proven and the same behavior is ported one family at a time.
     */
    private static int familySwitchStateSelectedRoute(Context context) {
        String id = context.spec.id();

        if ("track_switch_medium_left".equals(id)
                || "track_switch_medium_right".equals(id)) {
            return stateActive(context.state)
                    ? FAMILY_SWITCH_ROUTE_BRANCH
                    : FAMILY_SWITCH_ROUTE_STRAIGHT;
        }

        return stateActive(context.state)
                ? FAMILY_SWITCH_ROUTE_BRANCH
                : FAMILY_SWITCH_ROUTE_STRAIGHT;
    }

    private static boolean stateActive(BlockState state) {
        for (var entry : state.getValues().entrySet()) {
            if ("active".equals(entry.getKey().getName())) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    private static boolean isStraightSlope(String id) {
        return "track_slope_small".equals(id)
                || "track_slope_long".equals(id)
                || "track_slope_very_long".equals(id);
    }

    private static boolean isContinuousLinearDiagonal(String id) {
        return isCrossingDiagonalFamily(id) || isDiagonalStraightFamily(id);
    }

    private static boolean isCrossingDiagonalFamily(String id) {
        return "track_diamond_crossing".equals(id)
                || "track_diamond_crossing_left".equals(id)
                || "track_double_diamond_crossing".equals(id)
                || "track_diagonal_crossing".equals(id)
                || "track_diagonal_two_ways_crossing".equals(id)
                || "track_diagonal_four_ways_crossing".equals(id)
                || "track_universal_crossing".equals(id);
    }

    private static boolean isDiagonalStraightFamily(String id) {
        return "track_diagonal_straight_small".equals(id)
                || "track_diagonal_straight_medium".equals(id);
    }

    private static boolean isContinuousCurve(String id) {
        return quarterCurveRadius(id) > 0.0D
                || fortyFiveDefinition(id) != null
                || parallelDefinition(id) != null;
    }

    private static boolean isContinuousCurve(Context context) {
        return isContinuousCurve(context.spec.id())
                || activeTargetFortyFiveSwitchDefinition(context) != null;
    }

    private static OffsetDefinition activeTargetFortyFiveSwitchDefinition(
            Context context) {
        if (context == null || !stateActive(context.state)) {
            return null;
        }
        return switch (context.spec.id()) {
            case TARGET_45_MEDIUM_LEFT_SWITCH -> new OffsetDefinition(-1.0D, 2.0D);
            case TARGET_45_MEDIUM_RIGHT_SWITCH -> new OffsetDefinition(1.0D, 2.0D);
            default -> null;
        };
    }

    private static boolean isOriginalTraincraftMediumFortyFiveSwitch(String id) {
        return TARGET_45_MEDIUM_LEFT_SWITCH.equals(id)
                || TARGET_45_MEDIUM_RIGHT_SWITCH.equals(id);
    }

    private static double quarterCurveRadius(String id) {
        return switch (id) {
            case "track_curve_big" -> 4.0D;
            case "track_curve_very_big" -> 9.0D;
            case "track_curve_super_big" -> 15.0D;
            case "track_curve_29x" -> 28.0D;
            case "track_curve_32x" -> 31.0D;
            default -> -1.0D;
        };
    }

    private static OffsetDefinition fortyFiveDefinition(String id) {
        return switch (id) {
            case "track_curve_45_medium_right" -> new OffsetDefinition(1.0D, 2.0D);
            case "track_curve_45_medium_left" -> new OffsetDefinition(-1.0D, 2.0D);
            case "track_curve_45_large_right" -> new OffsetDefinition(2.0D, 5.0D);
            case "track_curve_45_large_left" -> new OffsetDefinition(-2.0D, 5.0D);
            case "track_curve_45_very_large_right" -> new OffsetDefinition(3.0D, 7.0D);
            case "track_curve_45_very_large_left" -> new OffsetDefinition(-3.0D, 7.0D);
            case "track_curve_45_super_large_right" -> new OffsetDefinition(4.0D, 10.0D);
            case "track_curve_45_super_large_left" -> new OffsetDefinition(-4.0D, 10.0D);
            default -> null;
        };
    }

    private static OffsetDefinition switchFortyFiveDefinition(String id) {
        return switch (id) {
            case "track_switch_45_medium_right" -> new OffsetDefinition(1.0D, 2.0D);
            case "track_switch_45_medium_left" -> new OffsetDefinition(-1.0D, 2.0D);
            default -> null;
        };
    }

    private static OffsetDefinition parallelDefinition(String id) {
        return switch (id) {
            case "track_parallel_curve_small_right" -> new OffsetDefinition(1.0D, 7.0D);
            case "track_parallel_curve_small_left" -> new OffsetDefinition(-1.0D, 7.0D);
            case "track_parallel_curve_medium_right" -> new OffsetDefinition(2.0D, 11.0D);
            case "track_parallel_curve_medium_left" -> new OffsetDefinition(-2.0D, 11.0D);
            case "track_parallel_curve_large_right" -> new OffsetDefinition(3.0D, 15.0D);
            case "track_parallel_curve_large_left" -> new OffsetDefinition(-3.0D, 15.0D);
            default -> null;
        };
    }

    /** Convert world X/Z into canonical NORTH-facing coordinates around root center. */
    private static double[] toCanonical(BlockPos root,
                                        Direction facing,
                                        double worldX,
                                        double worldZ) {
        double dx = worldX - (root.getX() + 0.5D);
        double dz = worldZ - (root.getZ() + 0.5D);
        return switch (facing) {
            case NORTH -> new double[] { dx, dz };
            case EAST -> new double[] { dz, -dx };
            case SOUTH -> new double[] { -dx, -dz };
            case WEST -> new double[] { -dz, dx };
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };
    }

    /** Convert canonical NORTH-facing local X/Z into world position at root center. */
    private static Vec3 fromCanonical(BlockPos root,
                                      Direction facing,
                                      double localX,
                                      double localZ) {
        Vec3 rotated = rotateCanonicalVector(facing, localX, localZ);
        return new Vec3(
                root.getX() + 0.5D + rotated.x,
                root.getY() + RAIL_SURFACE_Y,
                root.getZ() + 0.5D + rotated.z);
    }

    private static Vec3 rotateCanonicalVector(Direction facing,
                                              double localX,
                                              double localZ) {
        return switch (facing) {
            case NORTH -> new Vec3(localX, 0.0D, localZ);
            case EAST -> new Vec3(-localZ, 0.0D, localX);
            case SOUTH -> new Vec3(-localX, 0.0D, -localZ);
            case WEST -> new Vec3(localZ, 0.0D, -localX);
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };
    }
}
