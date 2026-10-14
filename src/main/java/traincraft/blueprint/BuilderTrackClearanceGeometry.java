package traincraft.blueprint;

// TC_DEV34_R1B4_GEOMETRY_AWARE_TRACK_CLEARANCE
//
// Read-only Builder safety geometry. This intentionally does NOT modify
// LegacyContinuousTrackPath: accepted F9 movement/coupling behavior remains
// byte-for-byte frozen. The analytic curve constants/formulas here mirror the
// accepted F9 path only for clearance measurements.

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.block.track.LegacyMediumCurveRailBlock;

import java.util.HashSet;
import java.util.Set;

public final class BuilderTrackClearanceGeometry {
    private static final double RAIL_SURFACE_Y =
            0.0625D;

    private static final double ORIGINAL_TC45_SWITCH_RADIUS =
            3.75D;

    private static final double ORIGINAL_TC45_SWITCH_LEAD =
            0.50D;

    private static final double ORIGINAL_TC45_SWITCH_ANGLE =
            Math.PI * 0.25D;

    private BuilderTrackClearanceGeometry() {}

    private record OffsetDefinition(
            double lateral,
            double forward) {}

    // TC_DEV34_R1B6_BLUEPRINT_ROUTE_GEOMETRY
    //
    // Resolve stored Blueprint track item IDs back to the same accepted
    // LegacyBatchTrackSpec geometry used by physical world rails. This lets
    // BuilderStructurePlan measure the real curve/switch/crossing/slope path
    // instead of treating RouteExit as one long straight chord.
    private static final java.util.Map<String, LegacyBatchTrackSpec>
            PLANNED_SPECS =
                    buildPlannedSpecs();

    private static java.util.Map<String, LegacyBatchTrackSpec>
            buildPlannedSpecs() {
        java.util.Map<String, LegacyBatchTrackSpec> out =
                new java.util.HashMap<>();

        try {
            for (java.lang.reflect.Field field :
                    LegacyBatchTrackSpecs.class
                            .getFields()) {
                if (!java.lang.reflect.Modifier
                        .isStatic(
                                field.getModifiers())
                        || !LegacyBatchTrackSpec.class
                                .isAssignableFrom(
                                        field.getType())) {
                    continue;
                }

                Object value =
                        field.get(null);

                if (value
                        instanceof LegacyBatchTrackSpec spec) {
                    out.put(
                            spec.id(),
                            spec);
                }
            }
        }
        catch (IllegalAccessException ex) {
            throw new ExceptionInInitializerError(
                    ex);
        }

        return java.util.Map.copyOf(
                out);
    }

    private static String normalizePlannedTrackId(
            String trackItemId) {
        if (trackItemId == null
                || trackItemId.isBlank()) {
            return "";
        }

        ResourceLocation key =
                ResourceLocation.tryParse(
                        trackItemId);

        String id =
                key != null
                        ? key.getPath()
                        : trackItemId;

        // Lightweight Energy aliases are item-only variants of the same
        // accepted physical geometry.
        if (id.endsWith("_energy")) {
            id =
                    id.substring(
                            0,
                            id.length()
                                    - "_energy".length());
        }

        return id;
    }

    public static boolean supportsPlannedTrack(
            String trackItemId) {
        String id =
                normalizePlannedTrackId(
                        trackItemId);

        return "track_medium_curve"
                        .equals(id)
                || PLANNED_SPECS
                        .containsKey(id);
    }

    public static boolean plannedTrackTooClose(
            String trackItemId,
            boolean left,
            BlockPos root,
            Direction facing,
            double px,
            double py,
            double pz,
            double clearanceRadiusXZ,
            double clearanceBelow,
            double clearanceAbove) {
        if (root == null
                || facing == null) {
            return false;
        }

        String id =
                normalizePlannedTrackId(
                        trackItemId);

        if ("track_medium_curve"
                .equals(id)) {
            // Medium's LEFT/RIGHT selection changes which endpoint is used for
            // continuation; the physical seven-part curve geometry itself is
            // the same accepted assembly for the given root/facing.
            return mediumCurveTooClose(
                    root,
                    facing,
                    px,
                    py,
                    pz,
                    clearanceRadiusXZ,
                    clearanceBelow,
                    clearanceAbove);
        }

        LegacyBatchTrackSpec spec =
                PLANNED_SPECS
                        .get(id);

        if (spec == null) {
            return false;
        }

        return batchAssemblyTooClose(
                null,
                spec,
                root,
                facing,
                px,
                py,
                pz,
                clearanceRadiusXZ,
                clearanceBelow,
                clearanceAbove);
    }

    public static boolean tooClose(
            Level level,
            BlockPos candidate,
            double clearanceRadiusXZ,
            double clearanceBelow,
            double clearanceAbove) {
        if (level == null
                || candidate == null) {
            return false;
        }

        double px =
                candidate.getX() + 0.5D;
        double py =
                candidate.getY() + 0.5D;
        double pz =
                candidate.getZ() + 0.5D;

        // We only need to DISCOVER one cell of an assembly; from there its root
        // and complete mathematical path are reconstructed.
        int horizontalSearch =
                (int) Math.ceil(
                        clearanceRadiusXZ)
                        + 2;

        int verticalSearch =
                (int) Math.ceil(
                        Math.max(
                                clearanceBelow,
                                clearanceAbove))
                        + 2;

        Set<String> seenAssemblies =
                new HashSet<>();

        for (int dy = -verticalSearch;
             dy <= verticalSearch;
             dy++) {
            for (int dx = -horizontalSearch;
                 dx <= horizontalSearch;
                 dx++) {
                for (int dz = -horizontalSearch;
                     dz <= horizontalSearch;
                     dz++) {
                    BlockPos probe =
                            candidate.offset(
                                    dx,
                                    dy,
                                    dz);

                    BlockState state =
                            level.getBlockState(
                                    probe);

                    if (state.isAir()) {
                        continue;
                    }

                    if (state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch) {
                        BlockPos root =
                                batch.getAssemblyRoot(
                                        probe,
                                        state);

                        Direction facing =
                                batch.getAssemblyFacing(
                                        state);

                        LegacyBatchTrackSpec spec =
                                batch.getSpec();

                        String key =
                                "B|"
                                        + spec.id()
                                        + "|"
                                        + root.asLong()
                                        + "|"
                                        + facing.ordinal();

                        if (!seenAssemblies.add(key)) {
                            continue;
                        }

                        if (batchAssemblyTooClose(
                                state,
                                spec,
                                root,
                                facing,
                                px,
                                py,
                                pz,
                                clearanceRadiusXZ,
                                clearanceBelow,
                                clearanceAbove)) {
                            return true;
                        }

                        continue;
                    }

                    if (state.getBlock()
                            instanceof LegacyMediumCurveRailBlock) {
                        int part =
                                state.getValue(
                                        LegacyMediumCurveRailBlock.PART);

                        Direction facing =
                                state.getValue(
                                        LegacyMediumCurveRailBlock
                                                .ASSEMBLY_FACING);

                        BlockPos root =
                                probe.subtract(
                                        LegacyMediumCurveRailBlock
                                                .offsetForPart(
                                                        BlockPos.ZERO,
                                                        facing,
                                                        part));

                        String key =
                                "M|"
                                        + root.asLong()
                                        + "|"
                                        + facing.ordinal();

                        if (!seenAssemblies.add(key)) {
                            continue;
                        }

                        if (mediumCurveTooClose(
                                root,
                                facing,
                                px,
                                py,
                                pz,
                                clearanceRadiusXZ,
                                clearanceBelow,
                                clearanceAbove)) {
                            return true;
                        }

                        continue;
                    }

                    if (state.getBlock()
                            instanceof BaseRailBlock) {
                        // Ordinary one-cell rails are already dense enough that
                        // center-point clearance is a safe conservative envelope.
                        if (pointTooClose(
                                px,
                                py,
                                pz,
                                probe.getX() + 0.5D,
                                probe.getY() + RAIL_SURFACE_Y,
                                probe.getZ() + 0.5D,
                                clearanceRadiusXZ,
                                clearanceBelow,
                                clearanceAbove)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean batchAssemblyTooClose(
            BlockState state,
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        String id =
                spec.id();

        double quarterRadius =
                quarterCurveRadius(id);

        if (quarterRadius > 0.0D
                && quarterCurveTooClose(
                        root,
                        facing,
                        quarterRadius,
                        px,
                        py,
                        pz,
                        radius,
                        below,
                        above)) {
            return true;
        }

        OffsetDefinition fortyFive =
                fortyFiveDefinition(id);

        if (fortyFive != null
                && fittedFortyFiveTooClose(
                        root,
                        facing,
                        fortyFive.lateral(),
                        fortyFive.forward(),
                        px,
                        py,
                        pz,
                        radius,
                        below,
                        above)) {
            return true;
        }

        OffsetDefinition switchFortyFive =
                switchFortyFiveDefinition(id);

        if (switchFortyFive != null
                && originalSwitchFortyFiveTooClose(
                        root,
                        facing,
                        switchFortyFive.lateral(),
                        px,
                        py,
                        pz,
                        radius,
                        below,
                        above)) {
            return true;
        }

        OffsetDefinition parallel =
                parallelDefinition(id);

        if (parallel != null
                && parallelCurveTooClose(
                        root,
                        facing,
                        parallel.lateral(),
                        parallel.forward(),
                        px,
                        py,
                        pz,
                        radius,
                        below,
                        above)) {
            return true;
        }

        // Always include the assembly guide graph as a conservative union.
        // This protects switches' straight branch, crossings, diagonals,
        // ordinary multipart pieces, straight slopes and curved slopes.
        return guideGraphTooClose(
                spec,
                root,
                facing,
                px,
                py,
                pz,
                radius,
                below,
                above);
    }

    private static boolean quarterCurveTooClose(
            BlockPos root,
            Direction facing,
            double curveRadius,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        double railY =
                root.getY() + RAIL_SURFACE_Y;

        if (!verticalInside(
                py,
                railY,
                below,
                above)) {
            return false;
        }

        double[] local =
                toCanonical(
                        root,
                        facing,
                        px,
                        pz);

        // Exact accepted F9 quarter-circle projection.
        double t =
                Mth.clamp(
                        Math.atan2(
                                -local[1],
                                local[0]
                                        + curveRadius),
                        0.0D,
                        Math.PI * 0.5D);

        double tx =
                -curveRadius
                        + curveRadius
                                * Math.cos(t);

        double tz =
                -curveRadius
                        * Math.sin(t);

        return localDistance(
                local[0],
                local[1],
                tx,
                tz)
                <= radius;
    }

    private static boolean fittedFortyFiveTooClose(
            BlockPos root,
            Direction facing,
            double lateral,
            double forward,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        double railY =
                root.getY() + RAIL_SURFACE_Y;

        if (!verticalInside(
                py,
                railY,
                below,
                above)) {
            return false;
        }

        double absLateral =
                Math.abs(lateral);

        if (absLateral < 1.0E-9D
                || forward <= 0.0D) {
            return false;
        }

        double[] local =
                toCanonical(
                        root,
                        facing,
                        px,
                        pz);

        double side =
                Math.signum(lateral);

        double fittedRadius =
                (absLateral * absLateral
                        + forward * forward)
                        / (2.0D * absLateral);

        double thetaEnd =
                2.0D
                        * Math.atan2(
                                absLateral,
                                forward);

        double signedX =
                side * local[0];

        double t =
                Mth.clamp(
                        Math.atan2(
                                -local[1],
                                fittedRadius
                                        - signedX),
                        0.0D,
                        thetaEnd);

        double tx =
                side
                        * (fittedRadius
                                - fittedRadius
                                        * Math.cos(t));

        double tz =
                -fittedRadius
                        * Math.sin(t);

        return localDistance(
                local[0],
                local[1],
                tx,
                tz)
                <= radius;
    }

    private static boolean originalSwitchFortyFiveTooClose(
            BlockPos root,
            Direction facing,
            double lateral,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        double railY =
                root.getY() + RAIL_SURFACE_Y;

        if (!verticalInside(
                py,
                railY,
                below,
                above)) {
            return false;
        }

        double[] local =
                toCanonical(
                        root,
                        facing,
                        px,
                        pz);

        double side =
                Math.signum(lateral);

        // Exact original Traincraft straight lead.
        if (distancePointToSegmentXZ(
                local[0],
                local[1],
                0.0D,
                0.0D,
                0.0D,
                -ORIGINAL_TC45_SWITCH_LEAD)
                <= radius) {
            return true;
        }

        // Exact original Traincraft 3.75-radius, true-45 arc.
        double signedX =
                side * local[0];

        double t =
                Mth.clamp(
                        Math.atan2(
                                -(local[1]
                                        + ORIGINAL_TC45_SWITCH_LEAD),
                                ORIGINAL_TC45_SWITCH_RADIUS
                                        - signedX),
                        0.0D,
                        ORIGINAL_TC45_SWITCH_ANGLE);

        double tx =
                side
                        * (ORIGINAL_TC45_SWITCH_RADIUS
                                - ORIGINAL_TC45_SWITCH_RADIUS
                                        * Math.cos(t));

        double tz =
                -ORIGINAL_TC45_SWITCH_LEAD
                        - ORIGINAL_TC45_SWITCH_RADIUS
                                * Math.sin(t);

        return localDistance(
                local[0],
                local[1],
                tx,
                tz)
                <= radius;
    }

    private static boolean parallelCurveTooClose(
            BlockPos root,
            Direction facing,
            double lateral,
            double forward,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        double railY =
                root.getY() + RAIL_SURFACE_Y;

        if (!verticalInside(
                py,
                railY,
                below,
                above)
                || forward <= 0.0D) {
            return false;
        }

        double[] local =
                toCanonical(
                        root,
                        facing,
                        px,
                        pz);

        // Same cubic smoothstep centerline as accepted F9. Sampling at 1/8
        // block or better keeps error tiny relative to the 3.25-block train
        // envelope and is only used by editor/ghost safety queries.
        int steps =
                Math.max(
                        32,
                        (int) Math.ceil(
                                forward * 8.0D));

        double lastX =
                0.0D;

        double lastZ =
                0.0D;

        for (int i = 1;
             i <= steps;
             i++) {
            double s =
                    i / (double) steps;

            double smooth =
                    s * s
                            * (3.0D
                                    - 2.0D * s);

            double x =
                    lateral * smooth;

            double z =
                    -forward * s;

            if (distancePointToSegmentXZ(
                    local[0],
                    local[1],
                    lastX,
                    lastZ,
                    x,
                    z)
                    <= radius) {
                return true;
            }

            lastX = x;
            lastZ = z;
        }

        return false;
    }

    private static boolean mediumCurveTooClose(
            BlockPos root,
            Direction facing,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        double railY =
                root.getY() + RAIL_SURFACE_Y;

        if (!verticalInside(
                py,
                railY,
                below,
                above)) {
            return false;
        }

        double[] local =
                toCanonical(
                        root,
                        facing,
                        px,
                        pz);

        // Dedicated Medium Curve occupies endpoints (0,-3) -> (3,0):
        // a radius-3 quarter-circle about the assembly root.
        double t =
                Mth.clamp(
                        Math.atan2(
                                local[0],
                                -local[1]),
                        0.0D,
                        Math.PI * 0.5D);

        double tx =
                3.0D * Math.sin(t);

        double tz =
                -3.0D * Math.cos(t);

        if (localDistance(
                local[0],
                local[1],
                tx,
                tz)
                <= radius) {
            return true;
        }

        // Also protect the seven accepted guide cells/links in case the visible
        // legacy model and exact circle differ slightly at the middle samples.
        Vec3 previous = null;

        for (int part = 0;
             part < LegacyMediumCurveRailBlock.PART_COUNT;
             part++) {
            BlockPos pos =
                    LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    root,
                                    facing,
                                    part);

            Vec3 current =
                    new Vec3(
                            pos.getX() + 0.5D,
                            pos.getY() + RAIL_SURFACE_Y,
                            pos.getZ() + 0.5D);

            if (pointTooClose(
                    px,
                    py,
                    pz,
                    current.x,
                    current.y,
                    current.z,
                    radius,
                    below,
                    above)) {
                return true;
            }

            if (previous != null
                    && segmentTooClose(
                            px,
                            py,
                            pz,
                            previous,
                            current,
                            radius,
                            below,
                            above)) {
                return true;
            }

            previous = current;
        }

        return false;
    }

    private static boolean guideGraphTooClose(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing,
            double px,
            double py,
            double pz,
            double radius,
            double below,
            double above) {
        int count =
                spec.partCount();

        Vec3[] points =
                new Vec3[count];

        LegacyBatchTrackSpec.Point[] canonical =
                new LegacyBatchTrackSpec.Point[count];

        for (int i = 0;
             i < count;
             i++) {
            BlockPos pos =
                    spec.offsetForPart(
                            root,
                            facing,
                            i);

            points[i] =
                    new Vec3(
                            pos.getX() + 0.5D,
                            pos.getY() + RAIL_SURFACE_Y,
                            pos.getZ() + 0.5D);

            canonical[i] =
                    spec.pointForPart(i);

            if (pointTooClose(
                    px,
                    py,
                    pz,
                    points[i].x,
                    points[i].y,
                    points[i].z,
                    radius,
                    below,
                    above)) {
                return true;
            }
        }

        // Rebuild the local route graph from neighboring guide cells instead of
        // treating each cell as an isolated point. This fills the gaps visible
        // on large curves, crossings, diagonal pieces and slope transitions.
        for (int i = 0;
             i < count;
             i++) {
            for (int j = i + 1;
                 j < count;
                 j++) {
                int dx =
                        Math.abs(
                                canonical[i].x
                                        - canonical[j].x);

                int dy =
                        Math.abs(
                                canonical[i].y
                                        - canonical[j].y);

                int dz =
                        Math.abs(
                                canonical[i].z
                                        - canonical[j].z);

                if (dx > 1
                        || dy > 1
                        || dz > 1
                        || (dx + dy + dz) == 0) {
                    continue;
                }

                if (segmentTooClose(
                        px,
                        py,
                        pz,
                        points[i],
                        points[j],
                        radius,
                        below,
                        above)) {
                    return true;
                }
            }
        }

        // Some accepted diagonal pieces deliberately keep the logical connector
        // in an empty corner one cell beyond the physical proxy. Protect that
        // connector segment too.
        for (LegacyBatchTrackSpec.Endpoint endpoint :
                spec.endpoints()) {
            if (!endpoint.hasLogicalOffset()) {
                continue;
            }

            BlockPos physical =
                    spec.offsetForPart(
                            root,
                            facing,
                            endpoint.part());

            BlockPos logical =
                    spec.endpointPosition(
                            root,
                            facing,
                            endpoint);

            Vec3 a =
                    new Vec3(
                            physical.getX() + 0.5D,
                            physical.getY() + RAIL_SURFACE_Y,
                            physical.getZ() + 0.5D);

            Vec3 b =
                    new Vec3(
                            logical.getX() + 0.5D,
                            logical.getY() + RAIL_SURFACE_Y,
                            logical.getZ() + 0.5D);

            if (segmentTooClose(
                    px,
                    py,
                    pz,
                    a,
                    b,
                    radius,
                    below,
                    above)) {
                return true;
            }
        }

        return false;
    }

    private static boolean pointTooClose(
            double px,
            double py,
            double pz,
            double tx,
            double ty,
            double tz,
            double radius,
            double below,
            double above) {
        if (!verticalInside(
                py,
                ty,
                below,
                above)) {
            return false;
        }

        return Math.hypot(
                px - tx,
                pz - tz)
                <= radius;
    }

    private static boolean segmentTooClose(
            double px,
            double py,
            double pz,
            Vec3 a,
            Vec3 b,
            double radius,
            double below,
            double above) {
        double dx =
                b.x - a.x;

        double dz =
                b.z - a.z;

        double lengthSq =
                dx * dx + dz * dz;

        double t;

        if (lengthSq < 1.0E-9D) {
            t = 0.0D;
        }
        else {
            t =
                    ((px - a.x) * dx
                            + (pz - a.z) * dz)
                            / lengthSq;

            t =
                    Mth.clamp(
                            t,
                            0.0D,
                            1.0D);
        }

        double tx =
                a.x + dx * t;

        double tz =
                a.z + dz * t;

        double ty =
                a.y
                        + (b.y - a.y)
                                * t;

        return verticalInside(
                        py,
                        ty,
                        below,
                        above)
                && Math.hypot(
                                px - tx,
                                pz - tz)
                        <= radius;
    }

    private static double distancePointToSegmentXZ(
            double px,
            double pz,
            double ax,
            double az,
            double bx,
            double bz) {
        double dx =
                bx - ax;

        double dz =
                bz - az;

        double lengthSq =
                dx * dx + dz * dz;

        if (lengthSq < 1.0E-9D) {
            return Math.hypot(
                    px - ax,
                    pz - az);
        }

        double t =
                ((px - ax) * dx
                        + (pz - az) * dz)
                        / lengthSq;

        t =
                Mth.clamp(
                        t,
                        0.0D,
                        1.0D);

        return Math.hypot(
                px - (ax + dx * t),
                pz - (az + dz * t));
    }

    private static boolean verticalInside(
            double py,
            double railY,
            double below,
            double above) {
        return py
                        >= railY - below
                && py
                        <= railY + above;
    }

    private static double localDistance(
            double px,
            double pz,
            double tx,
            double tz) {
        return Math.hypot(
                px - tx,
                pz - tz);
    }

    private static double quarterCurveRadius(
            String id) {
        return switch (id) {
            case "track_curve_big" -> 4.0D;
            case "track_curve_very_big" -> 9.0D;
            case "track_curve_super_big" -> 15.0D;
            case "track_curve_29x" -> 28.0D;
            case "track_curve_32x" -> 31.0D;
            default -> -1.0D;
        };
    }

    private static OffsetDefinition fortyFiveDefinition(
            String id) {
        return switch (id) {
            case "track_curve_45_medium_right" ->
                    new OffsetDefinition(
                            1.0D,
                            2.0D);
            case "track_curve_45_medium_left" ->
                    new OffsetDefinition(
                            -1.0D,
                            2.0D);
            case "track_curve_45_large_right" ->
                    new OffsetDefinition(
                            2.0D,
                            5.0D);
            case "track_curve_45_large_left" ->
                    new OffsetDefinition(
                            -2.0D,
                            5.0D);
            case "track_curve_45_very_large_right" ->
                    new OffsetDefinition(
                            3.0D,
                            7.0D);
            case "track_curve_45_very_large_left" ->
                    new OffsetDefinition(
                            -3.0D,
                            7.0D);
            case "track_curve_45_super_large_right" ->
                    new OffsetDefinition(
                            4.0D,
                            10.0D);
            case "track_curve_45_super_large_left" ->
                    new OffsetDefinition(
                            -4.0D,
                            10.0D);
            default -> null;
        };
    }

    private static OffsetDefinition switchFortyFiveDefinition(
            String id) {
        return switch (id) {
            case "track_switch_45_medium_right" ->
                    new OffsetDefinition(
                            1.0D,
                            2.0D);
            case "track_switch_45_medium_left" ->
                    new OffsetDefinition(
                            -1.0D,
                            2.0D);
            default -> null;
        };
    }

    private static OffsetDefinition parallelDefinition(
            String id) {
        return switch (id) {
            case "track_parallel_curve_small_right" ->
                    new OffsetDefinition(
                            1.0D,
                            7.0D);
            case "track_parallel_curve_small_left" ->
                    new OffsetDefinition(
                            -1.0D,
                            7.0D);
            case "track_parallel_curve_medium_right" ->
                    new OffsetDefinition(
                            2.0D,
                            11.0D);
            case "track_parallel_curve_medium_left" ->
                    new OffsetDefinition(
                            -2.0D,
                            11.0D);
            case "track_parallel_curve_large_right" ->
                    new OffsetDefinition(
                            3.0D,
                            15.0D);
            case "track_parallel_curve_large_left" ->
                    new OffsetDefinition(
                            -3.0D,
                            15.0D);
            default -> null;
        };
    }

    private static double[] toCanonical(
            BlockPos root,
            Direction facing,
            double worldX,
            double worldZ) {
        double dx =
                worldX
                        - (root.getX() + 0.5D);

        double dz =
                worldZ
                        - (root.getZ() + 0.5D);

        return switch (facing) {
            case NORTH ->
                    new double[] {
                            dx,
                            dz
                    };
            case EAST ->
                    new double[] {
                            dz,
                            -dx
                    };
            case SOUTH ->
                    new double[] {
                            -dx,
                            -dz
                    };
            case WEST ->
                    new double[] {
                            -dz,
                            dx
                    };
            default ->
                    throw new IllegalArgumentException(
                            "Horizontal facing required: "
                                    + facing);
        };
    }
}
