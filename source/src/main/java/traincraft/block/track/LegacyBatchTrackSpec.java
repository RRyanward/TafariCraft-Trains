package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable guide-path definition for one original Traincraft dedicated piece.
 *
 * Coordinates are canonical for FACING=NORTH. Rotating the assembly rotates
 * both occupied cells and canonical RailShapes together.
 */
public final class LegacyBatchTrackSpec {
    public static final class Point {
        public final int x;
        public final int y;
        public final int z;

        public Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Point withY(int newY) {
            return new Point(x, newY, z);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Point p)) return false;
            return x == p.x && y == p.y && z == p.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }

    public static final class Cell {
        private final Point point;
        private final RailShape primary;
        private final RailShape alternate;

        private Cell(Point point, RailShape primary, RailShape alternate) {
            this.point = point;
            this.primary = primary;
            this.alternate = alternate == null ? primary : alternate;
        }

        public Point point() {
            return point;
        }

        public RailShape primary() {
            return primary;
        }

        public RailShape alternate() {
            return alternate;
        }
    }

    public static final class Endpoint {
        private final int part;
        private final Direction outward;
        private final Point logicalOffset;

        private Endpoint(int part, Direction outward) {
            this(part, outward, new Point(0, 0, 0));
        }

        private Endpoint(int part, Direction outward, Point logicalOffset) {
            this.part = part;
            this.outward = outward;
            this.logicalOffset = logicalOffset;
        }

        public int part() {
            return part;
        }

        public Direction outward() {
            return outward;
        }

        public Point logicalOffset() {
            return logicalOffset;
        }

        public boolean hasLogicalOffset() {
            return logicalOffset.x != 0 || logicalOffset.y != 0 || logicalOffset.z != 0;
        }
    }

    private static final class MutableCell {
        private final Point point;
        private RailShape primary;
        private RailShape alternate;

        private MutableCell(Point point) {
            this.point = point;
        }
    }

    private static final class EndpointKey {
        private final Point point;
        private final Direction outward;

        private EndpointKey(Point point, Direction outward) {
            this.point = point;
            this.outward = outward;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EndpointKey key)) return false;
            return point.equals(key.point) && outward == key.outward;
        }

        @Override
        public int hashCode() {
            return Objects.hash(point, outward);
        }
    }

    private final String id;
    private final List<Cell> cells;
    private final List<Endpoint> endpoints;
    private final boolean dynamicRouting;

    private LegacyBatchTrackSpec(String id,
                                 List<Cell> cells,
                                 List<Endpoint> endpoints,
                                 boolean dynamicRouting) {
        this.id = id;
        this.cells = List.copyOf(cells);
        this.endpoints = List.copyOf(endpoints);
        this.dynamicRouting = dynamicRouting;

        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Empty batch track spec: " + id);
        }
        if (cells.size() > 64) {
            throw new IllegalArgumentException(
                    "Batch track spec exceeds 64 guide cells: " + id + " -> " + cells.size());
        }
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("No endpoints for batch track spec: " + id);
        }
    }

    public String id() {
        return id;
    }

    public int partCount() {
        return cells.size();
    }

    public List<Endpoint> endpoints() {
        return endpoints;
    }

    public boolean dynamicRouting() {
        return dynamicRouting;
    }

    /**
     * All original straight/curved slope meshes rise one block over their
     * footprint. Step 9.3a-r2 keeps their support footprint on one base level
     * while the invisible guide rail changes elevation near the midpoint.
     */
    public boolean isSlope() {
        return id.startsWith("track_slope");
    }

    public BlockPos supportForPart(BlockPos root, Direction facing, int part) {
        BlockPos railPos = offsetForPart(root, facing, part);
        int raised = isSlope() ? pointForPart(part).y : 0;
        return railPos.below(1 + raised);
    }

    public Point pointForPart(int part) {
        return cells.get(part).point();
    }

    public BlockPos offsetForPart(BlockPos root, Direction facing, int part) {
        Point p = pointForPart(part);
        int rx;
        int rz;

        switch (facing) {
            case NORTH -> {
                rx = p.x;
                rz = p.z;
            }
            case EAST -> {
                rx = -p.z;
                rz = p.x;
            }
            case SOUTH -> {
                rx = -p.x;
                rz = -p.z;
            }
            case WEST -> {
                rx = p.z;
                rz = -p.x;
            }
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        }

        return root.offset(rx, p.y, rz);
    }

    /**
     * Logical endpoint position used by connector placement. Most endpoints
     * live directly on their guide-rail part, so this is identical to
     * offsetForPart(). A small number of original Traincraft meshes expose a
     * rendered connector one guide cell away from that physical part; those
     * endpoints carry a canonical logicalOffset that rotates with FACING.
     */
    public BlockPos endpointPosition(BlockPos root, Direction facing, Endpoint endpoint) {
        BlockPos physical = offsetForPart(root, facing, endpoint.part());
        Point delta = endpoint.logicalOffset();
        int rx;
        int rz;

        switch (facing) {
            case NORTH -> {
                rx = delta.x;
                rz = delta.z;
            }
            case EAST -> {
                rx = -delta.z;
                rz = delta.x;
            }
            case SOUTH -> {
                rx = -delta.x;
                rz = -delta.z;
            }
            case WEST -> {
                rx = delta.z;
                rz = -delta.x;
            }
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        }

        return physical.offset(rx, delta.y, rz);
    }

    public BlockPos endpointPositionForPart(BlockPos root, Direction facing, int part) {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.part() == part) {
                return endpointPosition(root, facing, endpoint);
            }
        }
        return offsetForPart(root, facing, part);
    }

    public Direction rotateDirection(Direction facing, Direction canonical) {
        Direction result = canonical;
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };

        for (int i = 0; i < turns; i++) {
            result = result.getClockWise();
        }
        return result;
    }

    public RailShape shapeForPart(Direction facing, int part, boolean alternate) {
        Cell cell = cells.get(part);
        RailShape canonical = alternate ? cell.alternate() : cell.primary();
        return rotateShape(canonical, facing);
    }

    public RailShape dynamicShapeForPart(Direction facing, int part,
                                         AbstractMinecart cart,
                                         boolean alternateFallback,
                                         long routeContext) {
        Cell cell = cells.get(part);
        RailShape primary = rotateShape(cell.primary(), facing);
        RailShape alternate = rotateShape(cell.alternate(), facing);

        if (cart == null || primary == alternate) {
            return alternateFallback ? alternate : primary;
        }

        // Step 9.3a-r2: latch the route for one continuous traversal of a
        // crossing assembly. The d1 trace proved that recalculating from tiny
        // near-zero drift during a stop/reverse can rotate an E/W locomotive
        // onto N/S by 90 degrees. A short timestamp lets a later, genuinely
        // new entry choose a different route without losing the stop/reverse
        // latch while the cart remains on this assembly.
        String keyBase = "tc_batch_route_" + id + "_" + Long.toUnsignedString(routeContext);
        String routeKey = keyBase + "_alt";
        String tickKey = keyBase + "_tick";
        var data = cart.getPersistentData();
        long now = cart.level().getGameTime();
        boolean hasRoute = data.contains(routeKey) && data.contains(tickKey);
        long lastTick = hasRoute ? data.getLong(tickKey) : Long.MIN_VALUE;
        boolean sameTraversal = hasRoute && now >= lastTick && now - lastTick <= 5L;

        if (sameTraversal) {
            data.putLong(tickKey, now);
            return data.getBoolean(routeKey) ? alternate : primary;
        }

        Vec3 motion = cart.getDeltaMovement();
        double mx = motion.x;
        double mz = motion.z;
        double speedSq = mx * mx + mz * mz;

        if (speedSq < 1.0E-10D) {
            // A cart placed or stopped directly on a crossing has no useful
            // delta movement yet. Its yaw still identifies the intended axis.
            double yaw = Math.toRadians(cart.getYRot());
            mx = -Math.sin(yaw);
            mz = Math.cos(yaw);
        }

        double p = alignment(primary, mx, mz);
        double a = alignment(alternate, mx, mz);
        boolean useAlternate = a > p + 1.0E-6D;
        if (Math.abs(a - p) <= 1.0E-6D) {
            useAlternate = alternateFallback;
        }

        data.putBoolean(routeKey, useAlternate);
        data.putLong(tickKey, now);
        return useAlternate ? alternate : primary;
    }

    private static double alignment(RailShape shape, double mx, double mz) {
        double ax;
        double az;

        switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> {
                ax = 0.0D;
                az = 1.0D;
            }
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> {
                ax = 1.0D;
                az = 0.0D;
            }
            case NORTH_EAST, SOUTH_WEST -> {
                ax = 1.0D;
                az = -1.0D;
            }
            case NORTH_WEST, SOUTH_EAST -> {
                ax = 1.0D;
                az = 1.0D;
            }
            default -> {
                return 0.0D;
            }
        }

        double len = Math.sqrt(ax * ax + az * az);
        ax /= len;
        az /= len;
        double motionLen = Math.sqrt(mx * mx + mz * mz);
        return Math.abs((mx * ax + mz * az) / motionLen);
    }

    private static RailShape rotateShape(RailShape shape, Direction facing) {
        RailShape result = shape;
        int turns = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };

        for (int i = 0; i < turns; i++) {
            result = rotateClockwise(result);
        }
        return result;
    }

    private static RailShape rotateClockwise(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> RailShape.EAST_WEST;
            case EAST_WEST -> RailShape.NORTH_SOUTH;
            case SOUTH_EAST -> RailShape.SOUTH_WEST;
            case SOUTH_WEST -> RailShape.NORTH_WEST;
            case NORTH_WEST -> RailShape.NORTH_EAST;
            case NORTH_EAST -> RailShape.SOUTH_EAST;
            case ASCENDING_NORTH -> RailShape.ASCENDING_EAST;
            case ASCENDING_EAST -> RailShape.ASCENDING_SOUTH;
            case ASCENDING_SOUTH -> RailShape.ASCENDING_WEST;
            case ASCENDING_WEST -> RailShape.ASCENDING_NORTH;
        };
    }

    public static LegacyBatchTrackSpec oneRoute(String id, List<Point> path) {
        return fromRoutes(id, path, null, false);
    }

    /**
     * Step 9.3g-t6-s1 canonical one-core Small Diagonal.
     *
     * The old compatibility topology kept physical p2 at (1,0) and exposed a
     * virtual logical endpoint at (1,-1). t6 makes the physical endpoint core
     * authoritative instead: p2 now occupies the actual diagonal route end.
     *
     *   canonical NORTH
     *
     *     p1 guide (0,-1) ---- p2 core/end (1,-1)
     *          |                         ^
     *          |                         | true route NORTH_EAST
     *     p0 core (0,0)
     *
     * p0 -> p2 remains the exact (+1,-1) diagonal route. The assembly remains
     * three parts and the existing p2 NORTH_WEST guide shape still joins p1
     * from the west while leaving its north arm available for rail support.
     */
    // STEP_9_3G_T6_S1_SMALL_DIAGONAL_PHYSICAL_P2_CORE
    public static LegacyBatchTrackSpec originalSmallDiagonal(String id) {
        List<Cell> cells = List.of(
                new Cell(
                        new Point(0, 0, 0),
                        RailShape.NORTH_SOUTH,
                        RailShape.NORTH_SOUTH),
                new Cell(
                        new Point(0, 0, -1),
                        RailShape.SOUTH_EAST,
                        RailShape.SOUTH_EAST),
                new Cell(
                        new Point(1, 0, -1),
                        RailShape.NORTH_WEST,
                        RailShape.NORTH_WEST));

        List<Endpoint> endpoints = List.of(
                new Endpoint(0, Direction.SOUTH),
                new Endpoint(2, Direction.NORTH));

        return new LegacyBatchTrackSpec(id, cells, endpoints, false);
    }

    public static LegacyBatchTrackSpec switchRoutes(String id,
                                                    List<Point> primary,
                                                    List<Point> alternate) {
        return fromRoutes(id, primary, alternate, false);
    }

    public static LegacyBatchTrackSpec crossingRoutes(String id,
                                                      List<Point> primary,
                                                      List<Point> alternate) {
        return fromRoutes(id, primary, alternate, true);
    }

    /**
     * TC4.5 DIAGONAL_TWO_WAYS_CROSSING topology.
     *
     * Original Traincraft placed one real crossing owner at the center and
     * eight linked SMALL_DIAGONAL_STRAIGHT-style proxy rails around it.  The
     * modern compatibility block therefore occupies a centered 3x3 square,
     * with part 0 deliberately reserved for the central visual/owner rail.
     * The two canonical Manhattan guide routes are only collision/lookup
     * scaffolding; LegacyContinuousTrackPath owns the actual 45-degree motion.
     */
    public static LegacyBatchTrackSpec originalDiagonalCrossing(String id) {
        List<Point> primaryPath = List.of(
                new Point(-1, 0, 1),
                new Point(-1, 0, 0),
                new Point(0, 0, 0),
                new Point(1, 0, 0),
                new Point(1, 0, -1));
        List<Point> alternatePath = List.of(
                new Point(1, 0, 1),
                new Point(0, 0, 1),
                new Point(0, 0, 0),
                new Point(0, 0, -1),
                new Point(-1, 0, -1));

        validatePath(primaryPath, id + " primary");
        validatePath(alternatePath, id + " alternate");

        LinkedHashMap<Point, MutableCell> merged = new LinkedHashMap<>();
        addRoute(merged, primaryPath, false);
        addRoute(merged, alternatePath, true);

        // Part 0 is the TC4.5 owner/model anchor.  The remaining eight cells
        // are linked proxy/gag rails around it.  Keep this order stable because
        // blockstate rendering keys the pristine crossing model from part 0.
        List<Point> orderedPoints = List.of(
                new Point(0, 0, 0),
                new Point(-1, 0, 1),
                new Point(-1, 0, 0),
                new Point(1, 0, 0),
                new Point(1, 0, -1),
                new Point(1, 0, 1),
                new Point(0, 0, 1),
                new Point(0, 0, -1),
                new Point(-1, 0, -1));

        List<Cell> cells = new ArrayList<>();
        Map<Point, Integer> partByPoint = new LinkedHashMap<>();
        int index = 0;
        for (Point point : orderedPoints) {
            MutableCell mutable = merged.get(point);
            if (mutable == null) {
                throw new IllegalStateException(
                        "Missing OG diagonal crossing proxy cell: " + id + " " + point);
            }

            RailShape primary = mutable.primary != null
                    ? mutable.primary
                    : mutable.alternate;
            RailShape alternate = mutable.alternate != null
                    ? mutable.alternate
                    : primary;
            cells.add(new Cell(point, primary, alternate));
            partByPoint.put(point, index++);
        }

        Set<EndpointKey> endpointKeys = new LinkedHashSet<>();
        addEndpoints(endpointKeys, primaryPath);
        addEndpoints(endpointKeys, alternatePath);

        List<Endpoint> endpoints = new ArrayList<>();
        for (EndpointKey key : endpointKeys) {
            Integer part = partByPoint.get(key.point);
            if (part == null) {
                throw new IllegalStateException(
                        "Missing OG diagonal crossing endpoint cell: " + id + " " + key.point);
            }
            endpoints.add(new Endpoint(part, key.outward));
        }

        return new LegacyBatchTrackSpec(id, cells, endpoints, true);
    }

    /**
     * Step 9.3g-t5-s4c: exact TC4.5 crossing owner/proxy topology.
     *
     * Every family keeps one real/model owner at canonical (0,0). Only cells
     * that TC4.5 actually placed are retained:
     *
     *   Right Diamond   = owner + N/S + SW/NE                 (5 cells)
     *   Left Diamond    = owner + N/S + SE/NW                 (5 cells)
     *   Double Diamond  = owner + N/S + both diagonal pairs   (7 cells)
     *   Diagonal 2-Way  = complete 3x3 proxy square           (9 cells)
     *   Four-Way        = complete 3x3 proxy square           (9 cells)
     *   Universal       = complete 3x3 proxy square           (9 cells)
     *
     * The Diagonal 2-Way is the one special case whose four cardinal cells
     * are gags/proxies rather than exposed routes. This matches TC4.5's
     * diagonalTwoWaysCrossing() placement routine.
     *
     * Endpoint order is route-paired. LegacyContinuousTrackPath and the s1
     * eight-way snap classifier consume each consecutive pair as one route.
     */
    public static LegacyBatchTrackSpec originalTraincraftCrossing(
            String id,
            boolean northSouth,
            boolean eastWest,
            boolean southWestNorthEast,
            boolean southEastNorthWest) {
        LegacyBatchTrackSpec proxyBase = originalDiagonalCrossing(id);

        Point center = new Point(0, 0, 0);
        Point north = new Point(0, 0, -1);
        Point south = new Point(0, 0, 1);
        Point west = new Point(-1, 0, 0);
        Point east = new Point(1, 0, 0);
        Point northWest = new Point(-1, 0, -1);
        Point northEast = new Point(1, 0, -1);
        Point southWest = new Point(-1, 0, 1);
        Point southEast = new Point(1, 0, 1);

        Set<Point> occupied = new LinkedHashSet<>();
        occupied.add(center);

        if (southWestNorthEast) {
            occupied.add(southWest);
            occupied.add(northEast);
        }
        if (southEastNorthWest) {
            occupied.add(southEast);
            occupied.add(northWest);
        }
        if (northSouth) {
            occupied.add(north);
            occupied.add(south);
        }
        if (eastWest) {
            occupied.add(west);
            occupied.add(east);
        }

        // TC4.5 Diagonal Two-Ways places four additional cardinal gag cells
        // even though only the two diagonal routes are externally connectable.
        if ("track_diagonal_two_ways_crossing".equals(id)) {
            occupied.add(north);
            occupied.add(south);
            occupied.add(west);
            occupied.add(east);
        }

        List<Cell> cells = new ArrayList<>();
        Map<Point, Integer> partByPoint = new LinkedHashMap<>();
        for (Cell cell : proxyBase.cells) {
            if (!occupied.contains(cell.point())) {
                continue;
            }
            partByPoint.put(cell.point(), cells.size());
            cells.add(cell);
        }

        List<Endpoint> endpoints = new ArrayList<>();
        if (southWestNorthEast) {
            addOriginalCrossingRoute(
                    endpoints, partByPoint,
                    southWest, Direction.SOUTH,
                    northEast, Direction.NORTH,
                    id);
        }
        if (southEastNorthWest) {
            addOriginalCrossingRoute(
                    endpoints, partByPoint,
                    southEast, Direction.EAST,
                    northWest, Direction.WEST,
                    id);
        }
        if (northSouth) {
            addOriginalCrossingRoute(
                    endpoints, partByPoint,
                    north, Direction.NORTH,
                    south, Direction.SOUTH,
                    id);
        }
        if (eastWest) {
            addOriginalCrossingRoute(
                    endpoints, partByPoint,
                    west, Direction.WEST,
                    east, Direction.EAST,
                    id);
        }

        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Original crossing must expose at least one route: " + id);
        }

        return new LegacyBatchTrackSpec(id, cells, endpoints, true);
    }

    private static void addOriginalCrossingRoute(
            List<Endpoint> endpoints,
            Map<Point, Integer> partByPoint,
            Point first,
            Direction firstOutward,
            Point second,
            Direction secondOutward,
            String id) {
        Integer firstPart = partByPoint.get(first);
        Integer secondPart = partByPoint.get(second);
        if (firstPart == null || secondPart == null) {
            throw new IllegalStateException(
                    "Missing original crossing route cell: " + id
                            + " first=" + first + " second=" + second);
        }
        endpoints.add(new Endpoint(firstPart, firstOutward));
        endpoints.add(new Endpoint(secondPart, secondOutward));
    }

    public static LegacyBatchTrackSpec singleDynamic(String id,
                                                     RailShape primary,
                                                     RailShape alternate) {
        Point p = new Point(0, 0, 0);
        List<Cell> cells = List.of(new Cell(p, primary, alternate));
        List<Endpoint> endpoints = List.of(
                new Endpoint(0, Direction.NORTH),
                new Endpoint(0, Direction.SOUTH),
                new Endpoint(0, Direction.WEST),
                new Endpoint(0, Direction.EAST));
        return new LegacyBatchTrackSpec(id, cells, endpoints, true);
    }

    private static LegacyBatchTrackSpec fromRoutes(String id,
                                                   List<Point> primaryPath,
                                                   List<Point> alternatePath,
                                                   boolean dynamicRouting) {
        validatePath(primaryPath, id + " primary");
        if (alternatePath != null) {
            validatePath(alternatePath, id + " alternate");
        }

        LinkedHashMap<Point, MutableCell> merged = new LinkedHashMap<>();

        addRoute(merged, primaryPath, false);
        if (alternatePath != null) {
            addRoute(merged, alternatePath, true);
        }

        List<Cell> cells = new ArrayList<>();
        Map<Point, Integer> partByPoint = new LinkedHashMap<>();
        int index = 0;

        for (MutableCell mutable : merged.values()) {
            RailShape primary = mutable.primary != null
                    ? mutable.primary
                    : mutable.alternate;
            RailShape alternate = mutable.alternate != null
                    ? mutable.alternate
                    : primary;

            cells.add(new Cell(mutable.point, primary, alternate));
            partByPoint.put(mutable.point, index++);
        }

        Set<EndpointKey> endpointKeys = new LinkedHashSet<>();
        addEndpoints(endpointKeys, primaryPath);
        if (alternatePath != null) {
            addEndpoints(endpointKeys, alternatePath);
        }

        List<Endpoint> endpoints = new ArrayList<>();
        for (EndpointKey key : endpointKeys) {
            Integer part = partByPoint.get(key.point);
            if (part != null) {
                endpoints.add(new Endpoint(part, key.outward));
            }
        }

        return new LegacyBatchTrackSpec(id, cells, endpoints, dynamicRouting);
    }

    private static void addRoute(LinkedHashMap<Point, MutableCell> merged,
                                 List<Point> path,
                                 boolean alternate) {
        for (int i = 0; i < path.size(); i++) {
            Point point = path.get(i);
            RailShape shape = shapeAt(path, i);
            MutableCell cell = merged.computeIfAbsent(point, MutableCell::new);
            if (alternate) {
                cell.alternate = shape;
            } else {
                cell.primary = shape;
            }
        }
    }

    private static void addEndpoints(Set<EndpointKey> endpoints, List<Point> path) {
        if (path.size() == 1) {
            return;
        }

        Point first = path.get(0);
        Point second = path.get(1);
        Point beforeLast = path.get(path.size() - 2);
        Point last = path.get(path.size() - 1);

        Direction firstInternal = directionBetween(first, second);
        Direction lastInternal = directionBetween(last, beforeLast);

        endpoints.add(new EndpointKey(first, firstInternal.getOpposite()));
        endpoints.add(new EndpointKey(last, lastInternal.getOpposite()));
    }

    private static RailShape shapeAt(List<Point> path, int index) {
        Point current = path.get(index);

        Point prev = index > 0 ? path.get(index - 1) : null;
        Point next = index + 1 < path.size() ? path.get(index + 1) : null;

        if (next != null && next.y > current.y) {
            return ascending(directionBetween(current, next));
        }
        if (prev != null && prev.y > current.y) {
            return ascending(directionBetween(current, prev));
        }

        Direction a;
        Direction b;

        if (prev == null && next != null) {
            a = directionBetween(current, next);
            b = a.getOpposite();
        } else if (next == null && prev != null) {
            a = directionBetween(current, prev);
            b = a.getOpposite();
        } else if (prev != null && next != null) {
            a = directionBetween(current, prev);
            b = directionBetween(current, next);
        } else {
            return RailShape.NORTH_SOUTH;
        }

        return shapeForDirections(a, b);
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            default -> throw new IllegalArgumentException("Horizontal direction required: " + direction);
        };
    }

    private static RailShape shapeForDirections(Direction a, Direction b) {
        if ((a == Direction.NORTH && b == Direction.SOUTH)
                || (a == Direction.SOUTH && b == Direction.NORTH)) {
            return RailShape.NORTH_SOUTH;
        }
        if ((a == Direction.WEST && b == Direction.EAST)
                || (a == Direction.EAST && b == Direction.WEST)) {
            return RailShape.EAST_WEST;
        }

        if ((a == Direction.NORTH && b == Direction.EAST)
                || (a == Direction.EAST && b == Direction.NORTH)) {
            return RailShape.NORTH_EAST;
        }
        if ((a == Direction.NORTH && b == Direction.WEST)
                || (a == Direction.WEST && b == Direction.NORTH)) {
            return RailShape.NORTH_WEST;
        }
        if ((a == Direction.SOUTH && b == Direction.EAST)
                || (a == Direction.EAST && b == Direction.SOUTH)) {
            return RailShape.SOUTH_EAST;
        }
        if ((a == Direction.SOUTH && b == Direction.WEST)
                || (a == Direction.WEST && b == Direction.SOUTH)) {
            return RailShape.SOUTH_WEST;
        }

        throw new IllegalArgumentException("Unsupported rail turn: " + a + " / " + b);
    }

    private static Direction directionBetween(Point from, Point to) {
        int dx = Integer.compare(to.x, from.x);
        int dz = Integer.compare(to.z, from.z);

        if (dx != 0 && dz != 0) {
            throw new IllegalArgumentException(
                    "Guide path contains diagonal cell jump: " + from + " -> " + to);
        }

        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;

        throw new IllegalArgumentException(
                "Guide path contains vertical-only or duplicate step: " + from + " -> " + to);
    }

    private static void validatePath(List<Point> path, String label) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Empty path: " + label);
        }

        for (int i = 1; i < path.size(); i++) {
            Point a = path.get(i - 1);
            Point b = path.get(i);
            int horizontal = Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
            int vertical = Math.abs(a.y - b.y);

            if (horizontal != 1 || vertical > 1) {
                throw new IllegalArgumentException(
                        "Non-adjacent guide path in " + label + ": " + a + " -> " + b);
            }
        }
    }
}
