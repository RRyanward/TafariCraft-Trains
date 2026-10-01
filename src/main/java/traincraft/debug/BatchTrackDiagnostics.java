package traincraft.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Step 9.3a-d1 read-only diagnostics.
 *
 * /tc_track_debug: static assembly/endpoint/model-bounds snapshot.
 * /tc_track_trace: 15-second rolling-stock motion trace around the player.
 * /tc_track_place_debug: arms exactly one full placement-decision trace.
 * /tc_track_visual_debug: one-shot visual endpoint/tangent geometry calibration.
 *
 * Neither command mutates rails, routing, rolling stock, or rendering.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BatchTrackDiagnostics {
    private static final int TRACE_TICKS = 20 * 15;
    private static final int TRACE_SAMPLE_INTERVAL = 2;
    private static final double TRACE_RADIUS = 48.0D;
    private static final Map<UUID, TraceSession> TRACES = new HashMap<>();
    private static final Set<UUID> PLACEMENT_DEBUG_ARMED = new HashSet<>();

    private BatchTrackDiagnostics() {}

    private static final class TraceSession {
        private final UUID playerId;
        private final List<String> report = new ArrayList<>();
        private int elapsed;

        private TraceSession(ServerPlayer player) {
            this.playerId = player.getUUID();
            report.add("TRAINCRAFT STEP 9.3a-d1 TRACK MOTION TRACE");
            report.add("player=" + player.getGameProfile().getName());
            report.add("dimension=" + player.serverLevel().dimension().location());
            report.add("durationTicks=" + TRACE_TICKS
                    + " sampleEveryTicks=" + TRACE_SAMPLE_INTERVAL
                    + " radius=" + TRACE_RADIUS);
            report.add("READ ONLY: no block/entity/routing changes.");
            report.add("");
        }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tc_track_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> inspect(context.getSource())));

        event.getDispatcher().register(Commands.literal("tc_track_trace")
                .requires(source -> source.hasPermission(2))
                .executes(context -> startTrace(context.getSource())));

        event.getDispatcher().register(Commands.literal("tc_track_place_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> armPlacementDebug(context.getSource())));

        event.getDispatcher().register(Commands.literal("tc_track_visual_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> visualEndpointDebug(context.getSource())));

        event.getDispatcher().register(Commands.literal("tc_track_transform_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> transformSearchDebug(context.getSource())));

        event.getDispatcher().register(Commands.literal("tc_track_component_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> routeComponentDebug(context.getSource())));
    }


    private static int armPlacementDebug(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            PLACEMENT_DEBUG_ARMED.add(player.getUUID());
            source.sendSuccess(() -> Component.literal(
                    "One-shot track placement debug armed. Make exactly one batch-track placement attempt."), false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "tc_track_place_debug must be run by a player: " + ex.getMessage()));
            return 0;
        }
    }

    public static boolean isPlacementDebugArmed(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && PLACEMENT_DEBUG_ARMED.contains(serverPlayer.getUUID());
    }

    /**
     * Completes exactly one armed placement diagnostic. The placement item owns
     * the candidate-by-candidate decision log; this method appends the actual
     * post-attempt local assembly geometry so logical connector math can be
     * separated from OBJ/render-anchor problems in one report.
     */
    public static void finishPlacementDebug(Player player,
                                            BlockPos center,
                                            List<String> report) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!PLACEMENT_DEBUG_ARMED.remove(serverPlayer.getUUID())) {
            return;
        }

        try {
            report.add("");
            report.add("=== POST-ATTEMPT LOCAL ASSEMBLY SNAPSHOT ===");
            appendLocalAssemblySnapshot(report, serverPlayer.serverLevel(), center, 12, 4);
            Path file = writeReport("placement-oneshot", report);
            serverPlayer.sendSystemMessage(Component.literal(
                    "One-shot track placement debug complete: " + file.toAbsolutePath()));
            for (String line : report) {
                LogUtils.getLogger().info("[TC-PLACEMENT-ONESHOT] {}", line);
            }
        } catch (Exception ex) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "Could not write one-shot placement debug: " + ex.getMessage()));
        }
    }

    private static void appendLocalAssemblySnapshot(List<String> report,
                                                    ServerLevel level,
                                                    BlockPos center,
                                                    int horizontalRadius,
                                                    int verticalRadius) {
        Set<String> assemblies = new HashSet<>();
        int count = 0;

        for (BlockPos scan : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (!level.hasChunkAt(scan)) continue;

            BlockState state = level.getBlockState(scan);
            if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock block)) {
                continue;
            }

            Direction facing = block.getAssemblyFacing(state);
            BlockPos root = block.getAssemblyRoot(scan, state);
            LegacyBatchTrackSpec spec = block.getSpec();
            String key = spec.id() + "|" + root + "|" + facing;
            if (!assemblies.add(key)) {
                continue;
            }

            count++;
            boolean active = state.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                    && state.getValue(LegacyBatchSwitchRailBlock.ACTIVE);
            report.add("POST_ASSEMBLY id=" + spec.id()
                    + " root=" + root
                    + " facing=" + facing
                    + " parts=" + spec.partCount()
                    + " active=" + active);
            appendGuideBounds(report, spec, root, facing);
            appendObjBounds(report, spec.id(), active);

            for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                BlockPos endpointPos = spec.offsetForPart(root, facing, endpoint.part());
                Direction outward = spec.rotateDirection(facing, endpoint.outward());
                BlockPos connectorCell = endpointPos.relative(outward);
                report.add("  POST_ENDPOINT part=" + endpoint.part()
                        + " pos=" + endpointPos
                        + " outward=" + outward
                        + " connectorCell=" + connectorCell
                        + " connectorState=" + level.getBlockState(connectorCell));
            }
        }

        report.add("POST_ASSEMBLY_COUNT=" + count
                + " center=" + center
                + " horizontalRadius=" + horizontalRadius
                + " verticalRadius=" + verticalRadius);
    }


    /**
     * Step 9.3c-t4-r3-d1: read-only one-shot geometry calibration for the
     * connected 45-degree Medium Left -> Diagonal Crossing join.
     *
     * This intentionally does not infer a fix from whole-model bounds. It
     * transforms the actual OBJ vertex clouds with the same four horizontal
     * blockstate rotations, measures the shared logical connector plane, then
     * reports local endpoint position/tangent evidence at multiple radii.
     */
    private static int visualEndpointDebug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        List<String> report = new ArrayList<>();
        report.add("TRAINCRAFT STEP 9.3c-t4-r3-d1 VISUAL ENDPOINT GEOMETRY CALIBRATION");
        report.add("dimension=" + level.dimension().location());
        report.add("scanCenter=" + center + " radius=24 horizontal,6 vertical");
        report.add("READ ONLY: OBJ/resource analysis + particles only; no block/entity/routing/render changes.");
        report.add("Target pair: track_switch_45_medium_left <-> track_diagonal_crossing");
        report.add("");

        try {
            List<VisualAssembly> assemblies = collectVisualAssemblies(level, center, 24, 6);
            List<VisualJoin> joins = new ArrayList<>();
            for (VisualAssembly sourceAssembly : assemblies) {
                if (!"track_switch_45_medium_left".equals(sourceAssembly.spec.id())) continue;
                for (VisualAssembly crossingAssembly : assemblies) {
                    if (!"track_diagonal_crossing".equals(crossingAssembly.spec.id())) continue;
                    collectReciprocalJoins(sourceAssembly, crossingAssembly, joins);
                }
            }

            report.add("ASSEMBLIES_SCANNED=" + assemblies.size());
            report.add("RECIPROCAL_JOIN_COUNT=" + joins.size());
            if (joins.isEmpty()) {
                report.add("RESULT=NO_RECIPROCAL_JOIN_FOUND");
                report.add("Run this while standing near the already-placed 45-degree Medium Left / Diagonal Crossing test join.");
                appendVisualAssemblyInventory(report, assemblies);
                Path file = writeReport("visual-endpoint", report);
                source.sendFailure(Component.literal("Visual endpoint debug found no reciprocal target join. Report: " + file.toAbsolutePath()));
                for (String line : report) LogUtils.getLogger().info("[TC-VISUAL-ENDPOINT] {}", line);
                return 0;
            }

            Vec3 playerPos = source.getPosition();
            VisualJoin chosen = joins.get(0);
            double best = chosen.boundaryPoint.distanceToSqr(playerPos);
            for (int i = 1; i < joins.size(); i++) {
                double distance = joins.get(i).boundaryPoint.distanceToSqr(playerPos);
                if (distance < best) {
                    best = distance;
                    chosen = joins.get(i);
                }
            }

            report.add("SELECTED_JOIN playerDistance=" + String.format(Locale.ROOT, "%.5f", Math.sqrt(best)));
            report.add("SOURCE id=" + chosen.source.spec.id()
                    + " root=" + chosen.source.root
                    + " facing=" + chosen.source.facing
                    + " active=" + chosen.source.active
                    + " endpointPart=" + chosen.sourceEndpoint.endpoint.part()
                    + " endpointPos=" + chosen.sourceEndpoint.pos
                    + " outward=" + chosen.sourceEndpoint.outward
                    + " connectorCell=" + chosen.sourceEndpoint.connectorCell);
            report.add("CROSSING id=" + chosen.crossing.spec.id()
                    + " root=" + chosen.crossing.root
                    + " facing=" + chosen.crossing.facing
                    + " endpointPart=" + chosen.crossingEndpoint.endpoint.part()
                    + " endpointPos=" + chosen.crossingEndpoint.pos
                    + " outward=" + chosen.crossingEndpoint.outward
                    + " connectorCell=" + chosen.crossingEndpoint.connectorCell);
            report.add(String.format(Locale.ROOT,
                    "LOGICAL_BOUNDARY point=(%.6f,%.6f,%.6f) axisCrossingToSource=(%d,%d) lateral=(%d,%d)",
                    chosen.boundaryPoint.x, chosen.boundaryPoint.y, chosen.boundaryPoint.z,
                    chosen.axisX, chosen.axisZ, chosen.lateralX, chosen.lateralZ));
            report.add("RECIPROCAL_LOGICAL_MATCH="
                    + (chosen.sourceEndpoint.connectorCell.equals(chosen.crossingEndpoint.pos)
                    && chosen.crossingEndpoint.connectorCell.equals(chosen.sourceEndpoint.pos)));
            report.add("");

            ObjGeometry sourceObj = loadObjGeometry(chosen.source);
            ObjGeometry crossingObj = loadObjGeometry(chosen.crossing);
            appendVisualGeometry(report, "SOURCE", chosen.source, sourceObj, chosen);
            report.add("");
            appendVisualGeometry(report, "CROSSING", chosen.crossing, crossingObj, chosen);
            report.add("");

            List<Vec3> sourceWorld = transformObjVertices(sourceObj.vertices, chosen.source);
            List<Vec3> crossingWorld = transformObjVertices(crossingObj.vertices, chosen.crossing);
            appendPairComparison(report, chosen, sourceWorld, crossingWorld);

            // Particle markers: logical boundary + local 24-nearest centroids.
            level.sendParticles(ParticleTypes.END_ROD,
                    chosen.boundaryPoint.x, chosen.boundaryPoint.y + 0.12D, chosen.boundaryPoint.z,
                    12, 0.03D, 0.03D, 0.03D, 0.0D);
            Vec3 sourceCentroid = nearestCentroid(sourceWorld, chosen.boundaryPoint, 24);
            Vec3 crossingCentroid = nearestCentroid(crossingWorld, chosen.boundaryPoint, 24);
            if (sourceCentroid != null) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        sourceCentroid.x, sourceCentroid.y + 0.12D, sourceCentroid.z,
                        8, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            if (crossingCentroid != null) {
                level.sendParticles(ParticleTypes.CRIT,
                        crossingCentroid.x, crossingCentroid.y + 0.12D, crossingCentroid.z,
                        8, 0.02D, 0.02D, 0.02D, 0.0D);
            }

            Path file = writeReport("visual-endpoint", report);
            source.sendSuccess(() -> Component.literal(
                    "Visual endpoint geometry debug complete. Report: " + file.toAbsolutePath()), false);
            for (String line : report) LogUtils.getLogger().info("[TC-VISUAL-ENDPOINT] {}", line);
            return 1;
        } catch (Exception ex) {
            report.add("ERROR=" + ex.getClass().getName() + ":" + ex.getMessage());
            try {
                Path file = writeReport("visual-endpoint", report);
                source.sendFailure(Component.literal("Visual endpoint debug failed; report: " + file.toAbsolutePath()));
            } catch (Exception ignored) {
                source.sendFailure(Component.literal("Visual endpoint debug failed: " + ex.getMessage()));
            }
            return 0;
        }
    }

    private static final class VisualAssembly {
        private final LegacyBatchTrackSpec spec;
        private final BlockPos root;
        private final Direction facing;
        private final boolean active;

        private VisualAssembly(LegacyBatchTrackSpec spec, BlockPos root, Direction facing, boolean active) {
            this.spec = spec;
            this.root = root;
            this.facing = facing;
            this.active = active;
        }
    }

    private static final class VisualEndpoint {
        private final VisualAssembly assembly;
        private final LegacyBatchTrackSpec.Endpoint endpoint;
        private final BlockPos pos;
        private final Direction outward;
        private final BlockPos connectorCell;

        private VisualEndpoint(VisualAssembly assembly, LegacyBatchTrackSpec.Endpoint endpoint) {
            this.assembly = assembly;
            this.endpoint = endpoint;
            this.pos = assembly.spec.offsetForPart(assembly.root, assembly.facing, endpoint.part());
            this.outward = assembly.spec.rotateDirection(assembly.facing, endpoint.outward());
            this.connectorCell = this.pos.relative(this.outward);
        }
    }

    private static final class VisualJoin {
        private final VisualAssembly source;
        private final VisualAssembly crossing;
        private final VisualEndpoint sourceEndpoint;
        private final VisualEndpoint crossingEndpoint;
        private final Vec3 boundaryPoint;
        private final int axisX;
        private final int axisZ;
        private final int lateralX;
        private final int lateralZ;

        private VisualJoin(VisualAssembly source,
                           VisualAssembly crossing,
                           VisualEndpoint sourceEndpoint,
                           VisualEndpoint crossingEndpoint) {
            this.source = source;
            this.crossing = crossing;
            this.sourceEndpoint = sourceEndpoint;
            this.crossingEndpoint = crossingEndpoint;
            Vec3 sourceCenter = Vec3.atCenterOf(sourceEndpoint.pos);
            Vec3 crossingCenter = Vec3.atCenterOf(crossingEndpoint.pos);
            this.boundaryPoint = new Vec3(
                    (sourceCenter.x + crossingCenter.x) * 0.5D,
                    Math.min(sourceEndpoint.pos.getY(), crossingEndpoint.pos.getY()) + 0.0625D,
                    (sourceCenter.z + crossingCenter.z) * 0.5D);
            this.axisX = crossingEndpoint.outward.getStepX();
            this.axisZ = crossingEndpoint.outward.getStepZ();
            this.lateralX = -this.axisZ;
            this.lateralZ = this.axisX;
        }
    }

    private static final class ObjGeometry {
        private final String resource;
        private final List<Vec3> vertices;

        private ObjGeometry(String resource, List<Vec3> vertices) {
            this.resource = resource;
            this.vertices = vertices;
        }
    }

    private static List<VisualAssembly> collectVisualAssemblies(ServerLevel level,
                                                                 BlockPos center,
                                                                 int horizontalRadius,
                                                                 int verticalRadius) {
        List<VisualAssembly> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BlockPos scan : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (!level.hasChunkAt(scan)) continue;
            BlockState state = level.getBlockState(scan);
            if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock block)) continue;
            Direction facing = block.getAssemblyFacing(state);
            BlockPos root = block.getAssemblyRoot(scan, state);
            LegacyBatchTrackSpec spec = block.getSpec();
            String key = spec.id() + "|" + root + "|" + facing;
            if (!seen.add(key)) continue;
            boolean active = state.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                    && state.getValue(LegacyBatchSwitchRailBlock.ACTIVE);
            result.add(new VisualAssembly(spec, root, facing, active));
        }
        return result;
    }

    private static void collectReciprocalJoins(VisualAssembly source,
                                                VisualAssembly crossing,
                                                List<VisualJoin> out) {
        for (LegacyBatchTrackSpec.Endpoint sourceEndpointDef : source.spec.endpoints()) {
            VisualEndpoint sourceEndpoint = new VisualEndpoint(source, sourceEndpointDef);
            for (LegacyBatchTrackSpec.Endpoint crossingEndpointDef : crossing.spec.endpoints()) {
                VisualEndpoint crossingEndpoint = new VisualEndpoint(crossing, crossingEndpointDef);
                if (sourceEndpoint.connectorCell.equals(crossingEndpoint.pos)
                        && crossingEndpoint.connectorCell.equals(sourceEndpoint.pos)) {
                    out.add(new VisualJoin(source, crossing, sourceEndpoint, crossingEndpoint));
                }
            }
        }
    }

    private static void appendVisualAssemblyInventory(List<String> report,
                                                       List<VisualAssembly> assemblies) {
        for (VisualAssembly assembly : assemblies) {
            report.add("NEARBY_ASSEMBLY id=" + assembly.spec.id()
                    + " root=" + assembly.root
                    + " facing=" + assembly.facing
                    + " active=" + assembly.active);
            for (LegacyBatchTrackSpec.Endpoint endpointDef : assembly.spec.endpoints()) {
                VisualEndpoint endpoint = new VisualEndpoint(assembly, endpointDef);
                report.add("  ENDPOINT part=" + endpoint.endpoint.part()
                        + " pos=" + endpoint.pos
                        + " outward=" + endpoint.outward
                        + " connectorCell=" + endpoint.connectorCell);
            }
        }
    }

    private static ObjGeometry loadObjGeometry(VisualAssembly assembly) throws Exception {
        List<String> candidates = new ArrayList<>();
        if (assembly.spec.id().startsWith("track_switch_")) {
            candidates.add("/assets/traincraft/models/block/batch/" + assembly.spec.id()
                    + (assembly.active ? "_active.obj" : "_inactive.obj"));
        }
        candidates.add("/assets/traincraft/models/block/batch/" + assembly.spec.id() + ".obj");

        for (String resource : candidates) {
            try (InputStream stream = BatchTrackDiagnostics.class.getResourceAsStream(resource)) {
                if (stream == null) continue;
                List<Vec3> vertices = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("v ")) continue;
                        String[] fields = line.split("\\s+");
                        if (fields.length < 4) continue;
                        vertices.add(new Vec3(
                                Double.parseDouble(fields[1]),
                                Double.parseDouble(fields[2]),
                                Double.parseDouble(fields[3])));
                    }
                }
                if (!vertices.isEmpty()) return new ObjGeometry(resource, vertices);
            }
        }
        throw new IllegalStateException("OBJ not found for " + assembly.spec.id() + " active=" + assembly.active);
    }

    private static List<Vec3> transformObjVertices(List<Vec3> raw, VisualAssembly assembly) {
        BlockPos anchor = assembly.spec.offsetForPart(assembly.root, assembly.facing, 0);
        List<Vec3> result = new ArrayList<>(raw.size());
        for (Vec3 vertex : raw) {
            double dx = vertex.x - 0.5D;
            double dz = vertex.z - 0.5D;
            int turns = switch (assembly.facing) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalArgumentException("Horizontal facing required: " + assembly.facing);
            };
            for (int i = 0; i < turns; i++) {
                double nextX = -dz;
                double nextZ = dx;
                dx = nextX;
                dz = nextZ;
            }
            result.add(new Vec3(
                    anchor.getX() + 0.5D + dx,
                    anchor.getY() + vertex.y,
                    anchor.getZ() + 0.5D + dz));
        }
        return result;
    }

    private static void appendVisualGeometry(List<String> report,
                                             String label,
                                             VisualAssembly assembly,
                                             ObjGeometry geometry,
                                             VisualJoin join) {
        List<Vec3> world = transformObjVertices(geometry.vertices, assembly);
        report.add(label + "_OBJ resource=" + geometry.resource
                + " vertices=" + geometry.vertices.size()
                + " modelAnchorPart0=" + assembly.spec.offsetForPart(assembly.root, assembly.facing, 0));
        appendBoundsLine(report, label + "_RAW_BOUNDS", geometry.vertices);
        appendBoundsLine(report, label + "_WORLD_BOUNDS", world);
        Vec3 nearest = nearestVertex(world, join.boundaryPoint);
        Vec3 c8 = nearestCentroid(world, join.boundaryPoint, 8);
        Vec3 c24 = nearestCentroid(world, join.boundaryPoint, 24);
        Vec3 c64 = nearestCentroid(world, join.boundaryPoint, 64);
        report.add(label + "_NEAREST_VERTEX " + formatPointDistance(nearest, join.boundaryPoint));
        report.add(label + "_NEAREST_CENTROID n=8 " + formatPointDistance(c8, join.boundaryPoint));
        report.add(label + "_NEAREST_CENTROID n=24 " + formatPointDistance(c24, join.boundaryPoint));
        report.add(label + "_NEAREST_CENTROID n=64 " + formatPointDistance(c64, join.boundaryPoint));

        boolean crossingSide = label.equals("CROSSING");
        for (double lateralBand : new double[]{0.20D, 0.35D, 0.50D, 0.80D}) {
            appendTipBand(report, label, world, join, crossingSide, lateralBand);
        }
        for (double radius : new double[]{0.25D, 0.40D, 0.60D, 0.90D}) {
            appendLocalPca(report, label, world, join.boundaryPoint, radius);
        }
    }

    private static void appendBoundsLine(List<String> report, String label, List<Vec3> points) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 p : points) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        report.add(String.format(Locale.ROOT,
                "%s x=[%.6f,%.6f] y=[%.6f,%.6f] z=[%.6f,%.6f] center=(%.6f,%.6f,%.6f)",
                label, minX, maxX, minY, maxY, minZ, maxZ,
                (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D));
    }

    private static Vec3 nearestVertex(List<Vec3> points, Vec3 target) {
        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Vec3 point : points) {
            double distance = point.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    private static Vec3 nearestCentroid(List<Vec3> points, Vec3 target, int count) {
        if (points.isEmpty()) return null;
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
        int use = Math.min(count, sorted.size());
        double x = 0.0D, y = 0.0D, z = 0.0D;
        for (int i = 0; i < use; i++) {
            Vec3 p = sorted.get(i);
            x += p.x; y += p.y; z += p.z;
        }
        return new Vec3(x / use, y / use, z / use);
    }

    private static String formatPointDistance(Vec3 point, Vec3 target) {
        if (point == null) return "NONE";
        return String.format(Locale.ROOT,
                "point=(%.6f,%.6f,%.6f) distance=%.6f delta=(%.6f,%.6f,%.6f)",
                point.x, point.y, point.z, Math.sqrt(point.distanceToSqr(target)),
                point.x - target.x, point.y - target.y, point.z - target.z);
    }

    private static void appendTipBand(List<String> report,
                                      String label,
                                      List<Vec3> points,
                                      VisualJoin join,
                                      boolean crossingSide,
                                      double lateralBand) {
        double minP = Double.POSITIVE_INFINITY;
        double maxP = Double.NEGATIVE_INFINITY;
        int count = 0;
        List<double[]> samples = new ArrayList<>();
        for (Vec3 point : points) {
            double dx = point.x - join.boundaryPoint.x;
            double dz = point.z - join.boundaryPoint.z;
            double p = dx * join.axisX + dz * join.axisZ;
            double lateral = dx * join.lateralX + dz * join.lateralZ;
            if (Math.abs(lateral) > lateralBand) continue;
            if (Math.abs(point.y - join.boundaryPoint.y) > 0.16D) continue;
            if (Math.abs(p) > 2.0D) continue;
            minP = Math.min(minP, p);
            maxP = Math.max(maxP, p);
            samples.add(new double[]{p, lateral, point.y});
            count++;
        }
        if (count == 0) {
            report.add(String.format(Locale.ROOT, "%s_TIP_BAND lateral<=%.2f count=0", label, lateralBand));
            return;
        }
        double tipP = crossingSide ? maxP : minP;
        double lateralSum = 0.0D;
        double ySum = 0.0D;
        double lateralMin = Double.POSITIVE_INFINITY;
        double lateralMax = Double.NEGATIVE_INFINITY;
        int tipCount = 0;
        for (double[] sample : samples) {
            if (Math.abs(sample[0] - tipP) > 0.03D) continue;
            lateralSum += sample[1];
            ySum += sample[2];
            lateralMin = Math.min(lateralMin, sample[1]);
            lateralMax = Math.max(lateralMax, sample[1]);
            tipCount++;
        }
        double lateralMean = tipCount == 0 ? Double.NaN : lateralSum / tipCount;
        double yMean = tipCount == 0 ? Double.NaN : ySum / tipCount;
        report.add(String.format(Locale.ROOT,
                "%s_TIP_BAND lateral<=%.2f count=%d pRange=[%.6f,%.6f] visualTipP=%.6f tipSamples=%d tipLateralMean=%.6f tipLateralRange=[%.6f,%.6f] tipYMean=%.6f",
                label, lateralBand, count, minP, maxP, tipP, tipCount,
                lateralMean, lateralMin, lateralMax, yMean));
    }

    private static void appendLocalPca(List<String> report,
                                       String label,
                                       List<Vec3> points,
                                       Vec3 center,
                                       double radius) {
        List<Vec3> local = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Vec3 point : points) {
            double dx = point.x - center.x;
            double dz = point.z - center.z;
            if (dx * dx + dz * dz > radiusSq) continue;
            if (Math.abs(point.y - center.y) > 0.18D) continue;
            local.add(point);
        }
        if (local.size() < 3) {
            report.add(String.format(Locale.ROOT, "%s_LOCAL_PCA radius=%.2f count=%d INSUFFICIENT", label, radius, local.size()));
            return;
        }
        double meanX = 0.0D, meanZ = 0.0D;
        for (Vec3 point : local) { meanX += point.x; meanZ += point.z; }
        meanX /= local.size(); meanZ /= local.size();
        double xx = 0.0D, zz = 0.0D, xz = 0.0D;
        for (Vec3 point : local) {
            double dx = point.x - meanX;
            double dz = point.z - meanZ;
            xx += dx * dx; zz += dz * dz; xz += dx * dz;
        }
        xx /= local.size(); zz /= local.size(); xz /= local.size();
        double trace = xx + zz;
        double root = Math.sqrt(Math.max(0.0D, (xx - zz) * (xx - zz) + 4.0D * xz * xz));
        double major = (trace + root) * 0.5D;
        double minor = (trace - root) * 0.5D;
        double angle = Math.toDegrees(0.5D * Math.atan2(2.0D * xz, xx - zz));
        if (angle < 0.0D) angle += 180.0D;
        double ratio = minor <= 1.0E-12D ? Double.POSITIVE_INFINITY : major / minor;
        report.add(String.format(Locale.ROOT,
                "%s_LOCAL_PCA radius=%.2f count=%d centroid=(%.6f,%.6f) majorAngleDeg=%.6f lambdaMajor=%.9f lambdaMinor=%.9f anisotropy=%.6f",
                label, radius, local.size(), meanX, meanZ, angle, major, minor, ratio));
    }

    private static void appendPairComparison(List<String> report,
                                             VisualJoin join,
                                             List<Vec3> sourceWorld,
                                             List<Vec3> crossingWorld) {
        report.add("=== PAIR COMPARISON ===");
        Vec3 source8 = nearestCentroid(sourceWorld, join.boundaryPoint, 8);
        Vec3 source24 = nearestCentroid(sourceWorld, join.boundaryPoint, 24);
        Vec3 source64 = nearestCentroid(sourceWorld, join.boundaryPoint, 64);
        Vec3 crossing8 = nearestCentroid(crossingWorld, join.boundaryPoint, 8);
        Vec3 crossing24 = nearestCentroid(crossingWorld, join.boundaryPoint, 24);
        Vec3 crossing64 = nearestCentroid(crossingWorld, join.boundaryPoint, 64);
        appendCentroidDelta(report, 8, source8, crossing8, join.crossing.facing);
        appendCentroidDelta(report, 24, source24, crossing24, join.crossing.facing);
        appendCentroidDelta(report, 64, source64, crossing64, join.crossing.facing);

        for (double band : new double[]{0.20D, 0.35D, 0.50D, 0.80D}) {
            TipPoint sourceTip = tipPoint(sourceWorld, join, false, band);
            TipPoint crossingTip = tipPoint(crossingWorld, join, true, band);
            if (sourceTip == null || crossingTip == null) {
                report.add(String.format(Locale.ROOT, "TIP_ALIGNMENT lateral<=%.2f unavailable", band));
                continue;
            }
            double worldDx = sourceTip.point.x - crossingTip.point.x;
            double worldDz = sourceTip.point.z - crossingTip.point.z;
            double[] raw = worldDeltaToRaw(worldDx, worldDz, join.crossing.facing);
            report.add(String.format(Locale.ROOT,
                    "TIP_ALIGNMENT lateral<=%.2f sourceTip=(%.6f,%.6f,%.6f) crossingTip=(%.6f,%.6f,%.6f) worldCorrectionCrossing=(dx=%.6f,dz=%.6f) rawObjCorrectionCrossing=(dx=%.6f,dz=%.6f)",
                    band,
                    sourceTip.point.x, sourceTip.point.y, sourceTip.point.z,
                    crossingTip.point.x, crossingTip.point.y, crossingTip.point.z,
                    worldDx, worldDz, raw[0], raw[1]));
        }

        report.add("ROTATION_SANITY: raw OBJ correction is reported in canonical model coordinates; blockstate rotation should rotate that correction with facing.");
        report.add("INTERPRETATION: prefer corrections stable across narrow 0.20/0.35 bands and consistent with local PCA tangent evidence; wide bands include sleepers/other crossing branches.");
    }

    private static final class TipPoint {
        private final Vec3 point;
        private TipPoint(Vec3 point) { this.point = point; }
    }

    private static TipPoint tipPoint(List<Vec3> points,
                                     VisualJoin join,
                                     boolean crossingSide,
                                     double lateralBand) {
        List<double[]> samples = new ArrayList<>();
        double tipP = crossingSide ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (Vec3 point : points) {
            double dx = point.x - join.boundaryPoint.x;
            double dz = point.z - join.boundaryPoint.z;
            double p = dx * join.axisX + dz * join.axisZ;
            double lateral = dx * join.lateralX + dz * join.lateralZ;
            if (Math.abs(lateral) > lateralBand) continue;
            if (Math.abs(point.y - join.boundaryPoint.y) > 0.16D) continue;
            if (Math.abs(p) > 2.0D) continue;
            samples.add(new double[]{p, lateral, point.y});
            tipP = crossingSide ? Math.max(tipP, p) : Math.min(tipP, p);
        }
        if (samples.isEmpty()) return null;
        double pSum = 0.0D, lateralSum = 0.0D, ySum = 0.0D;
        int count = 0;
        for (double[] sample : samples) {
            if (Math.abs(sample[0] - tipP) > 0.03D) continue;
            pSum += sample[0]; lateralSum += sample[1]; ySum += sample[2]; count++;
        }
        if (count == 0) return null;
        double p = pSum / count;
        double lateral = lateralSum / count;
        double x = join.boundaryPoint.x + p * join.axisX + lateral * join.lateralX;
        double z = join.boundaryPoint.z + p * join.axisZ + lateral * join.lateralZ;
        return new TipPoint(new Vec3(x, ySum / count, z));
    }

    private static void appendCentroidDelta(List<String> report,
                                            int count,
                                            Vec3 source,
                                            Vec3 crossing,
                                            Direction crossingFacing) {
        if (source == null || crossing == null) return;
        double worldDx = source.x - crossing.x;
        double worldDz = source.z - crossing.z;
        double[] raw = worldDeltaToRaw(worldDx, worldDz, crossingFacing);
        report.add(String.format(Locale.ROOT,
                "NEAREST_CENTROID_ALIGNMENT n=%d source=(%.6f,%.6f,%.6f) crossing=(%.6f,%.6f,%.6f) worldCorrectionCrossing=(dx=%.6f,dz=%.6f) rawObjCorrectionCrossing=(dx=%.6f,dz=%.6f)",
                count,
                source.x, source.y, source.z,
                crossing.x, crossing.y, crossing.z,
                worldDx, worldDz, raw[0], raw[1]));
    }

    private static double[] worldDeltaToRaw(double worldDx, double worldDz, Direction facing) {
        return switch (facing) {
            case NORTH -> new double[]{worldDx, worldDz};
            case EAST -> new double[]{worldDz, -worldDx};
            case SOUTH -> new double[]{-worldDx, -worldDz};
            case WEST -> new double[]{-worldDz, worldDx};
            default -> throw new IllegalArgumentException("Horizontal facing required: " + facing);
        };
    }


    /**
     * Step 9.3c-t4-r5-d1: read-only model-aware transform search.
     *
     * Searches every nearby Diagonal Crossing root in a +/-4 block X/Z window
     * and all four horizontal facings. No blocks are placed or changed.
     *
     * The report ranks candidates by local rendered-geometry gap and tangent
     * agreement against the already-known 45-degree Medium Left part-4 branch,
     * while separately reporting logical reciprocity and world placeability.
     */
    private static int transformSearchDebug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        List<String> report = new ArrayList<>();
        report.add("TRAINCRAFT STEP 9.3c-t4-r5-d1 MODEL-AWARE CONNECTOR TRANSFORM SEARCH");
        report.add("dimension=" + level.dimension().location());
        report.add("scanCenter=" + center + " radius=24 horizontal,6 vertical");
        report.add("READ ONLY: hypothetical root/facing transform search; no world changes.");
        report.add("Target pair: track_switch_45_medium_left part-4 branch <-> track_diagonal_crossing");
        report.add("");

        try {
            List<VisualAssembly> assemblies = collectVisualAssemblies(level, center, 24, 6);
            List<VisualJoin> joins = new ArrayList<>();
            for (VisualAssembly sourceAssembly : assemblies) {
                if (!"track_switch_45_medium_left".equals(sourceAssembly.spec.id())) continue;
                for (VisualAssembly crossingAssembly : assemblies) {
                    if (!"track_diagonal_crossing".equals(crossingAssembly.spec.id())) continue;
                    collectReciprocalJoins(sourceAssembly, crossingAssembly, joins);
                }
            }

            report.add("ASSEMBLIES_SCANNED=" + assemblies.size());
            report.add("RECIPROCAL_JOIN_COUNT=" + joins.size());
            if (joins.isEmpty()) {
                report.add("RESULT=NO_RECIPROCAL_REFERENCE_JOIN_FOUND");
                appendVisualAssemblyInventory(report, assemblies);
                Path file = writeReport("transform-search", report);
                source.sendFailure(Component.literal(
                        "Transform search found no reciprocal reference join. Report: " + file.toAbsolutePath()));
                for (String line : report) LogUtils.getLogger().info("[TC-TRANSFORM-SEARCH] {}", line);
                return 0;
            }

            Vec3 playerPos = source.getPosition();
            VisualJoin chosen = joins.get(0);
            double bestPlayerDistance = chosen.boundaryPoint.distanceToSqr(playerPos);
            for (int i = 1; i < joins.size(); i++) {
                double distance = joins.get(i).boundaryPoint.distanceToSqr(playerPos);
                if (distance < bestPlayerDistance) {
                    bestPlayerDistance = distance;
                    chosen = joins.get(i);
                }
            }

            report.add("REFERENCE_JOIN playerDistance="
                    + String.format(Locale.ROOT, "%.6f", Math.sqrt(bestPlayerDistance)));
            report.add("SOURCE id=" + chosen.source.spec.id()
                    + " root=" + chosen.source.root
                    + " facing=" + chosen.source.facing
                    + " active=" + chosen.source.active
                    + " endpointPart=" + chosen.sourceEndpoint.endpoint.part()
                    + " endpointPos=" + chosen.sourceEndpoint.pos
                    + " outward=" + chosen.sourceEndpoint.outward
                    + " connectorCell=" + chosen.sourceEndpoint.connectorCell);
            report.add("CURRENT_CROSSING root=" + chosen.crossing.root
                    + " facing=" + chosen.crossing.facing
                    + " endpointPart=" + chosen.crossingEndpoint.endpoint.part()
                    + " endpointPos=" + chosen.crossingEndpoint.pos
                    + " outward=" + chosen.crossingEndpoint.outward);
            report.add(String.format(Locale.ROOT,
                    "REFERENCE_BOUNDARY point=(%.6f,%.6f,%.6f)",
                    chosen.boundaryPoint.x, chosen.boundaryPoint.y, chosen.boundaryPoint.z));
            report.add("");

            ObjGeometry sourceObj = loadObjGeometry(chosen.source);
            ObjGeometry crossingObj = loadObjGeometry(chosen.crossing);
            List<Vec3> sourceWorld = transformObjVertices(sourceObj.vertices, chosen.source);
            Vec3 sourceCentroid = nearestCentroid(sourceWorld, chosen.boundaryPoint, 24);
            TipPoint sourceTipResult = tipPoint(sourceWorld, chosen, false, 0.20D);
            Vec3 sourceTip = sourceTipResult == null ? sourceCentroid : sourceTipResult.point;
            PcaResult sourcePca = computeLocalPca(sourceWorld, chosen.boundaryPoint, 0.40D);
            if (sourcePca == null) sourcePca = computeLocalPca(sourceWorld, chosen.boundaryPoint, 0.60D);

            report.add("SOURCE_REFERENCE centroid24=" + formatVec(sourceCentroid)
                    + " tip020=" + formatVec(sourceTip)
                    + " pca=" + formatPca(sourcePca));
            report.add("SEARCH_WINDOW rootDx=[-4,4] rootDz=[-4,4] y=current fourFacings=true totalCandidates=324");
            report.add("");

            Set<BlockPos> currentCrossingCells = new HashSet<>();
            for (int part = 0; part < chosen.crossing.spec.partCount(); part++) {
                currentCrossingCells.add(chosen.crossing.spec.offsetForPart(
                        chosen.crossing.root, chosen.crossing.facing, part));
            }

            List<TransformCandidate> results = new ArrayList<>();
            Direction[] facings = new Direction[] {
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
            };

            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos candidateRoot = chosen.crossing.root.offset(dx, 0, dz);
                    for (Direction facing : facings) {
                        VisualAssembly candidateAssembly = new VisualAssembly(
                                chosen.crossing.spec, candidateRoot, facing, false);
                        CandidatePlacementCheck placement = checkHypotheticalPlacement(
                                level, candidateAssembly, currentCrossingCells);

                        int reciprocalPart = -1;
                        int endpointAtRequiredPart = -1;
                        for (LegacyBatchTrackSpec.Endpoint endpointDef : candidateAssembly.spec.endpoints()) {
                            VisualEndpoint endpoint = new VisualEndpoint(candidateAssembly, endpointDef);
                            if (endpoint.pos.equals(chosen.sourceEndpoint.connectorCell)) {
                                endpointAtRequiredPart = endpoint.endpoint.part();
                            }
                            if (endpoint.pos.equals(chosen.sourceEndpoint.connectorCell)
                                    && endpoint.connectorCell.equals(chosen.sourceEndpoint.pos)) {
                                reciprocalPart = endpoint.endpoint.part();
                            }
                        }

                        List<Vec3> candidateWorld = transformObjVertices(crossingObj.vertices, candidateAssembly);
                        Vec3 candidateCentroid = nearestCentroid(candidateWorld, chosen.boundaryPoint, 24);
                        Vec3 candidateTipCentroid = nearestCentroid(candidateWorld, sourceTip, 16);
                        double centroidGap = horizontalDistance(sourceCentroid, candidateCentroid);
                        double tipGap = horizontalDistance(sourceTip, candidateTipCentroid);
                        PcaResult candidatePca = computeLocalPca(candidateWorld, chosen.boundaryPoint, 0.55D);
                        if (candidatePca == null) {
                            candidatePca = computeLocalPca(candidateWorld, chosen.boundaryPoint, 0.85D);
                        }
                        double tangentError = (sourcePca == null || candidatePca == null)
                                ? 90.0D
                                : lineAngleDifference(sourcePca.angleDeg, candidatePca.angleDeg);

                        double score = centroidGap + (tipGap * 0.50D) + (tangentError * 0.010D);
                        boolean current = candidateRoot.equals(chosen.crossing.root)
                                && facing == chosen.crossing.facing;
                        TransformCandidate candidate = new TransformCandidate(
                                candidateRoot, facing, placement.placeable,
                                placement.reason, reciprocalPart, endpointAtRequiredPart,
                                current, centroidGap, tipGap, candidatePca, tangentError,
                                score, candidateCentroid, candidateTipCentroid);
                        results.add(candidate);
                    }
                }
            }

            List<TransformCandidate> placeable = new ArrayList<>();
            List<TransformCandidate> reciprocal = new ArrayList<>();
            TransformCandidate currentCandidate = null;
            for (TransformCandidate candidate : results) {
                if (candidate.current) currentCandidate = candidate;
                if (candidate.placeable) placeable.add(candidate);
                if (candidate.placeable && candidate.reciprocalPart >= 0) reciprocal.add(candidate);
            }
            placeable.sort((a, b) -> Double.compare(a.score, b.score));
            reciprocal.sort((a, b) -> Double.compare(a.score, b.score));

            report.add("=== SUMMARY ===");
            report.add("TOTAL_CANDIDATES=" + results.size()
                    + " PLACEABLE=" + placeable.size()
                    + " PLACEABLE_RECIPROCAL=" + reciprocal.size());
            report.add("CURRENT=" + formatTransformCandidate(currentCandidate));
            report.add("BEST_VISUAL_PLACEABLE="
                    + (placeable.isEmpty() ? "NONE" : formatTransformCandidate(placeable.get(0))));
            report.add("BEST_RECIPROCAL_PLACEABLE="
                    + (reciprocal.isEmpty() ? "NONE" : formatTransformCandidate(reciprocal.get(0))));
            report.add("");

            report.add("=== TOP 24 PLACEABLE BY GEOMETRY/TANGENT SCORE ===");
            for (int i = 0; i < Math.min(24, placeable.size()); i++) {
                report.add("RANK=" + (i + 1) + " " + formatTransformCandidate(placeable.get(i)));
            }
            report.add("");

            report.add("=== TOP 16 RECIPROCAL PLACEABLE ===");
            for (int i = 0; i < Math.min(16, reciprocal.size()); i++) {
                report.add("RECIPROCAL_RANK=" + (i + 1) + " " + formatTransformCandidate(reciprocal.get(i)));
            }
            report.add("");

            report.add("=== FULL CANDIDATE MATRIX ===");
            for (TransformCandidate candidate : results) {
                report.add(formatTransformCandidate(candidate));
            }

            if (!placeable.isEmpty()) {
                TransformCandidate bestVisual = placeable.get(0);
                if (sourceCentroid != null) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            sourceCentroid.x, sourceCentroid.y + 0.15D, sourceCentroid.z,
                            10, 0.02D, 0.02D, 0.02D, 0.0D);
                }
                if (bestVisual.candidateCentroid != null) {
                    level.sendParticles(ParticleTypes.CRIT,
                            bestVisual.candidateCentroid.x,
                            bestVisual.candidateCentroid.y + 0.15D,
                            bestVisual.candidateCentroid.z,
                            10, 0.02D, 0.02D, 0.02D, 0.0D);
                }
            }

            Path file = writeReport("transform-search", report);
            source.sendSuccess(() -> Component.literal(
                    "Model-aware transform search complete. Report: " + file.toAbsolutePath()), false);
            for (String line : report) LogUtils.getLogger().info("[TC-TRANSFORM-SEARCH] {}", line);
            return 1;
        } catch (Exception ex) {
            report.add("ERROR=" + ex.getClass().getName() + ":" + ex.getMessage());
            try {
                Path file = writeReport("transform-search", report);
                source.sendFailure(Component.literal(
                        "Transform search failed; report: " + file.toAbsolutePath()));
            } catch (Exception ignored) {
                source.sendFailure(Component.literal("Transform search failed: " + ex.getMessage()));
            }
            return 0;
        }
    }

    private static final class CandidatePlacementCheck {
        private final boolean placeable;
        private final String reason;

        private CandidatePlacementCheck(boolean placeable, String reason) {
            this.placeable = placeable;
            this.reason = reason;
        }
    }

    private static final class PcaResult {
        private final int count;
        private final double centroidX;
        private final double centroidZ;
        private final double angleDeg;
        private final double anisotropy;

        private PcaResult(int count,
                          double centroidX,
                          double centroidZ,
                          double angleDeg,
                          double anisotropy) {
            this.count = count;
            this.centroidX = centroidX;
            this.centroidZ = centroidZ;
            this.angleDeg = angleDeg;
            this.anisotropy = anisotropy;
        }
    }

    private static final class TransformCandidate {
        private final BlockPos root;
        private final Direction facing;
        private final boolean placeable;
        private final String rejectReason;
        private final int reciprocalPart;
        private final int endpointAtRequiredPart;
        private final boolean current;
        private final double centroidGap;
        private final double tipGap;
        private final PcaResult pca;
        private final double tangentError;
        private final double score;
        private final Vec3 candidateCentroid;
        private final Vec3 candidateTipCentroid;

        private TransformCandidate(BlockPos root,
                                   Direction facing,
                                   boolean placeable,
                                   String rejectReason,
                                   int reciprocalPart,
                                   int endpointAtRequiredPart,
                                   boolean current,
                                   double centroidGap,
                                   double tipGap,
                                   PcaResult pca,
                                   double tangentError,
                                   double score,
                                   Vec3 candidateCentroid,
                                   Vec3 candidateTipCentroid) {
            this.root = root;
            this.facing = facing;
            this.placeable = placeable;
            this.rejectReason = rejectReason;
            this.reciprocalPart = reciprocalPart;
            this.endpointAtRequiredPart = endpointAtRequiredPart;
            this.current = current;
            this.centroidGap = centroidGap;
            this.tipGap = tipGap;
            this.pca = pca;
            this.tangentError = tangentError;
            this.score = score;
            this.candidateCentroid = candidateCentroid;
            this.candidateTipCentroid = candidateTipCentroid;
        }
    }

    private static CandidatePlacementCheck checkHypotheticalPlacement(ServerLevel level,
                                                                       VisualAssembly candidate,
                                                                       Set<BlockPos> currentCrossingCells) {
        Set<BlockPos> seen = new HashSet<>();
        for (int part = 0; part < candidate.spec.partCount(); part++) {
            BlockPos pos = candidate.spec.offsetForPart(candidate.root, candidate.facing, part);
            if (!seen.add(pos)) {
                return new CandidatePlacementCheck(false, "DUPLICATE_PART_CELL part=" + part + " pos=" + pos);
            }

            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !currentCrossingCells.contains(pos)) {
                return new CandidatePlacementCheck(false,
                        "BLOCKED part=" + part + " pos=" + pos + " state=" + state);
            }

            BlockPos supportPos = pos.below();
            BlockState support = level.getBlockState(supportPos);
            if (!support.isFaceSturdy(level, supportPos, Direction.UP)) {
                return new CandidatePlacementCheck(false,
                        "NO_SUPPORT part=" + part + " pos=" + pos + " below=" + support);
            }
        }
        return new CandidatePlacementCheck(true, "NONE");
    }

    private static PcaResult computeLocalPca(List<Vec3> points, Vec3 center, double radius) {
        List<Vec3> local = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Vec3 point : points) {
            double dx = point.x - center.x;
            double dz = point.z - center.z;
            if (dx * dx + dz * dz > radiusSq) continue;
            if (Math.abs(point.y - center.y) > 0.18D) continue;
            local.add(point);
        }
        if (local.size() < 3) return null;

        double meanX = 0.0D;
        double meanZ = 0.0D;
        for (Vec3 point : local) {
            meanX += point.x;
            meanZ += point.z;
        }
        meanX /= local.size();
        meanZ /= local.size();

        double xx = 0.0D;
        double zz = 0.0D;
        double xz = 0.0D;
        for (Vec3 point : local) {
            double dx = point.x - meanX;
            double dz = point.z - meanZ;
            xx += dx * dx;
            zz += dz * dz;
            xz += dx * dz;
        }
        xx /= local.size();
        zz /= local.size();
        xz /= local.size();

        double trace = xx + zz;
        double root = Math.sqrt(Math.max(0.0D,
                (xx - zz) * (xx - zz) + 4.0D * xz * xz));
        double major = (trace + root) * 0.5D;
        double minor = (trace - root) * 0.5D;
        double angle = Math.toDegrees(0.5D * Math.atan2(2.0D * xz, xx - zz));
        if (angle < 0.0D) angle += 180.0D;
        double ratio = minor <= 1.0E-12D ? Double.POSITIVE_INFINITY : major / minor;
        return new PcaResult(local.size(), meanX, meanZ, angle, ratio);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        if (a == null || b == null) return 999.0D;
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double lineAngleDifference(double a, double b) {
        double diff = Math.abs(a - b) % 180.0D;
        return diff > 90.0D ? 180.0D - diff : diff;
    }

    private static String formatVec(Vec3 point) {
        if (point == null) return "NONE";
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", point.x, point.y, point.z);
    }

    private static String formatPca(PcaResult pca) {
        if (pca == null) return "NONE";
        return String.format(Locale.ROOT,
                "count=%d centroid=(%.6f,%.6f) angleDeg=%.6f anisotropy=%.6f",
                pca.count, pca.centroidX, pca.centroidZ, pca.angleDeg, pca.anisotropy);
    }

    private static String formatTransformCandidate(TransformCandidate candidate) {
        if (candidate == null) return "NONE";
        return String.format(Locale.ROOT,
                "root=%s facing=%s current=%s placeable=%s reject=%s reciprocalPart=%d endpointAtRequiredPart=%d centroidGap=%.6f tipGap=%.6f tangentDeg=%s tangentError=%.6f score=%.6f centroid=%s tipCentroid=%s",
                candidate.root,
                candidate.facing,
                candidate.current,
                candidate.placeable,
                candidate.rejectReason,
                candidate.reciprocalPart,
                candidate.endpointAtRequiredPart,
                candidate.centroidGap,
                candidate.tipGap,
                candidate.pca == null ? "NONE" : String.format(Locale.ROOT, "%.6f", candidate.pca.angleDeg),
                candidate.tangentError,
                candidate.score,
                formatVec(candidate.candidateCentroid),
                formatVec(candidate.candidateTipCentroid));
    }


    /**
     * Step 9.3c-t4-r6-d1: route-component geometry map for the 45-degree
     * Medium Left part-4 branch and the Diagonal Crossing. Unlike the earlier
     * whole-OBJ transform search, this parser keeps Blender object sections and
     * face-connected components separate so unrelated crossing rails/sleepers
     * cannot silently win the geometry score.
     *
     * READ ONLY: no world, routing, placement, entity, or render mutation.
     */
    private static int routeComponentDebug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        List<String> report = new ArrayList<>();
        report.add("TRAINCRAFT STEP 9.3c-t4-r6-d1 CROSSING ROUTE-COMPONENT GEOMETRY MAP");
        report.add("dimension=" + level.dimension().location());
        report.add("scanCenter=" + center + " radius=24 horizontal,6 vertical");
        report.add("READ ONLY: OBJ object/face-component analysis + hypothetical transforms only; no world changes.");
        report.add("Target: track_switch_45_medium_left endpoint part 4 -> track_diagonal_crossing route component");
        report.add("");

        try {
            List<VisualAssembly> assemblies = collectVisualAssemblies(level, center, 24, 6);
            Vec3 playerPos = source.getPosition();
            VisualAssembly sourceAssembly = nearestAssemblyById(assemblies, "track_switch_45_medium_left", playerPos);
            VisualAssembly crossingAssembly = nearestAssemblyById(assemblies, "track_diagonal_crossing", playerPos);
            report.add("ASSEMBLIES_SCANNED=" + assemblies.size());
            if (sourceAssembly == null || crossingAssembly == null) {
                report.add("RESULT=TARGET_ASSEMBLY_MISSING sourceFound=" + (sourceAssembly != null)
                        + " crossingFound=" + (crossingAssembly != null));
                appendVisualAssemblyInventory(report, assemblies);
                Path file = writeReport("route-component", report);
                source.sendFailure(Component.literal("Route-component debug could not find both target assemblies. Report: " + file.toAbsolutePath()));
                for (String line : report) LogUtils.getLogger().info("[TC-ROUTE-COMPONENT] {}", line);
                return 0;
            }

            LegacyBatchTrackSpec.Endpoint sourceEndpointDef = findEndpointDefinition(sourceAssembly.spec, 4);
            if (sourceEndpointDef == null) {
                report.add("RESULT=SOURCE_ENDPOINT_PART4_MISSING");
                Path file = writeReport("route-component", report);
                source.sendFailure(Component.literal("Route-component debug: source part-4 endpoint missing. Report: " + file.toAbsolutePath()));
                return 0;
            }
            VisualEndpoint sourceEndpoint = new VisualEndpoint(sourceAssembly, sourceEndpointDef);
            Vec3 boundary = endpointBoundary(sourceEndpoint);

            report.add("SOURCE id=" + sourceAssembly.spec.id()
                    + " root=" + sourceAssembly.root
                    + " facing=" + sourceAssembly.facing
                    + " active=" + sourceAssembly.active
                    + " endpointPart=" + sourceEndpoint.endpoint.part()
                    + " endpointPos=" + sourceEndpoint.pos
                    + " outward=" + sourceEndpoint.outward
                    + " requiredCrossingEndpointCell=" + sourceEndpoint.connectorCell);
            report.add(String.format(Locale.ROOT,
                    "SOURCE_BOUNDARY point=(%.6f,%.6f,%.6f)", boundary.x, boundary.y, boundary.z));
            report.add("CURRENT_CROSSING root=" + crossingAssembly.root
                    + " facing=" + crossingAssembly.facing
                    + " parts=" + crossingAssembly.spec.partCount());

            int currentAtRequired = -1;
            int currentReciprocal = -1;
            for (LegacyBatchTrackSpec.Endpoint endpointDef : crossingAssembly.spec.endpoints()) {
                VisualEndpoint endpoint = new VisualEndpoint(crossingAssembly, endpointDef);
                if (endpoint.pos.equals(sourceEndpoint.connectorCell)) currentAtRequired = endpointDef.part();
                if (endpoint.pos.equals(sourceEndpoint.connectorCell)
                        && endpoint.connectorCell.equals(sourceEndpoint.pos)) currentReciprocal = endpointDef.part();
            }
            report.add("CURRENT_LOGICAL endpointAtRequiredPart=" + currentAtRequired
                    + " reciprocalPart=" + currentReciprocal
                    + " reciprocal=" + (currentReciprocal >= 0));
            report.add("");

            ObjGeometry sourceObj = loadObjGeometry(sourceAssembly);
            List<Vec3> sourceWorld = transformObjVertices(sourceObj.vertices, sourceAssembly);
            Vec3 sourceCentroid = nearestCentroid(sourceWorld, boundary, 24);
            PcaResult sourcePca = computeLocalPca(sourceWorld, boundary, 0.40D);
            if (sourcePca == null) sourcePca = computeLocalPca(sourceWorld, boundary, 0.65D);
            report.add("SOURCE_GEOMETRY resource=" + sourceObj.resource
                    + " centroid24=" + formatVec(sourceCentroid)
                    + " pca=" + formatPca(sourcePca));

            ObjFaceMesh mesh = loadObjFaceMesh(crossingAssembly);
            List<ObjFaceComponent> components = buildFaceComponents(mesh);
            report.add("CROSSING_MESH resource=" + mesh.resource
                    + " vertices=" + mesh.vertices.size()
                    + " faces=" + mesh.faces.size()
                    + " objectSections=" + mesh.objectOrder.size()
                    + " connectedFaceComponents=" + components.size());
            report.add("");

            report.add("=== OBJ OBJECT-SECTIONS AT CURRENT TRANSFORM ===");
            for (String objectName : mesh.objectOrder) {
                Set<Integer> ids = new HashSet<>();
                int faceCount = 0;
                for (ObjMeshFace face : mesh.faces) {
                    if (!objectName.equals(face.objectName)) continue;
                    faceCount++;
                    for (int index : face.vertexIndices) ids.add(index);
                }
                List<Vec3> world = transformVertexIds(mesh.vertices, ids, crossingAssembly);
                ComponentMeasure measure = measureComponent(world, crossingAssembly, sourceEndpoint, boundary, sourceCentroid, sourcePca);
                report.add("OBJECT name=" + objectName
                        + " faces=" + faceCount
                        + " vertices=" + ids.size()
                        + " " + formatComponentMeasure(measure));
            }
            report.add("");

            List<ComponentRank> currentRanks = new ArrayList<>();
            for (ObjFaceComponent component : components) {
                List<Vec3> world = transformVertexIds(mesh.vertices, component.vertexIndices, crossingAssembly);
                ComponentMeasure measure = measureComponent(world, crossingAssembly, sourceEndpoint, boundary, sourceCentroid, sourcePca);
                currentRanks.add(new ComponentRank(component, measure));
            }
            currentRanks.sort((a, b) -> Double.compare(a.measure.score, b.measure.score));
            report.add("=== TOP 40 CONNECTED-FACE COMPONENTS AT CURRENT TRANSFORM ===");
            for (int i = 0; i < Math.min(40, currentRanks.size()); i++) {
                ComponentRank rank = currentRanks.get(i);
                report.add("CURRENT_COMPONENT_RANK=" + (i + 1)
                        + " object=" + rank.component.objectName
                        + " component=" + rank.component.componentIndex
                        + " faces=" + rank.component.faceIndices.size()
                        + " vertices=" + rank.component.vertexIndices.size()
                        + " " + formatComponentMeasure(rank.measure));
            }
            report.add("");

            Set<BlockPos> currentCrossingCells = new HashSet<>();
            for (int part = 0; part < crossingAssembly.spec.partCount(); part++) {
                currentCrossingCells.add(crossingAssembly.spec.offsetForPart(
                        crossingAssembly.root, crossingAssembly.facing, part));
            }

            Direction[] facings = new Direction[] {
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
            };
            List<ComponentTransformRank> exactEndpointCandidates = new ArrayList<>();
            for (Direction facing : facings) {
                for (LegacyBatchTrackSpec.Endpoint endpointDef : crossingAssembly.spec.endpoints()) {
                    BlockPos candidateRoot = rootForEndpointAtCell(
                            crossingAssembly.spec, facing, endpointDef.part(), sourceEndpoint.connectorCell);
                    VisualAssembly candidate = new VisualAssembly(crossingAssembly.spec, candidateRoot, facing, false);
                    VisualEndpoint endpoint = new VisualEndpoint(candidate, endpointDef);
                    boolean reciprocal = endpoint.connectorCell.equals(sourceEndpoint.pos);
                    CandidatePlacementCheck placement = checkHypotheticalPlacement(level, candidate, currentCrossingCells);
                    ComponentTransformRank best = bestComponentForEndpoint(
                            mesh, components, candidate, endpointDef.part(), sourceEndpoint,
                            boundary, sourceCentroid, sourcePca, placement, reciprocal);
                    exactEndpointCandidates.add(best);
                }
            }
            exactEndpointCandidates.sort((a, b) -> Double.compare(a.score, b.score));
            report.add("=== EXACT-ENDPOINT COMPONENT MATRIX (16 endpoint/facing transforms) ===");
            for (int i = 0; i < exactEndpointCandidates.size(); i++) {
                report.add("EXACT_RANK=" + (i + 1) + " " + formatComponentTransformRank(exactEndpointCandidates.get(i)));
            }
            report.add("");

            List<ComponentTransformRank> windowRanks = new ArrayList<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos root = crossingAssembly.root.offset(dx, 0, dz);
                    for (Direction facing : facings) {
                        VisualAssembly candidate = new VisualAssembly(crossingAssembly.spec, root, facing, false);
                        CandidatePlacementCheck placement = checkHypotheticalPlacement(level, candidate, currentCrossingCells);
                        int endpointAtRequired = -1;
                        boolean reciprocal = false;
                        for (LegacyBatchTrackSpec.Endpoint endpointDef : candidate.spec.endpoints()) {
                            VisualEndpoint endpoint = new VisualEndpoint(candidate, endpointDef);
                            if (endpoint.pos.equals(sourceEndpoint.connectorCell)) {
                                endpointAtRequired = endpointDef.part();
                                if (endpoint.connectorCell.equals(sourceEndpoint.pos)) reciprocal = true;
                            }
                        }
                        ComponentTransformRank best = bestComponentAny(
                                mesh, components, candidate, endpointAtRequired, sourceEndpoint,
                                boundary, sourceCentroid, sourcePca, placement, reciprocal);
                        windowRanks.add(best);
                    }
                }
            }
            windowRanks.sort((a, b) -> Double.compare(a.score, b.score));
            report.add("=== TOP 32 COMPONENT-AWARE ROOT/FACING TRANSFORMS ===");
            for (int i = 0; i < Math.min(32, windowRanks.size()); i++) {
                report.add("WINDOW_RANK=" + (i + 1) + " " + formatComponentTransformRank(windowRanks.get(i)));
            }
            report.add("");

            report.add("=== TOP 24 LOGIC-PRESERVING COMPONENT-AWARE TRANSFORMS ===");
            int logicRank = 0;
            for (ComponentTransformRank rank : windowRanks) {
                if (!rank.placeable || !rank.reciprocal) continue;
                report.add("LOGIC_RANK=" + (++logicRank) + " " + formatComponentTransformRank(rank));
                if (logicRank >= 24) break;
            }
            if (logicRank == 0) report.add("LOGIC_RANK=NONE");
            report.add("");

            report.add("INTERPRETATION:");
            report.add("  endpointDistances lists the component's minimum rendered X/Z distance to each crossing endpoint boundary.");
            report.add("  assignedEndpoint is the nearest crossing endpoint boundary for that component under that transform.");
            report.add("  endpointTouch=true means the component comes within 0.70 blocks of the evaluated crossing endpoint boundary.");
            report.add("  Prefer a placeable reciprocal transform whose assigned route component also has low sourceGap and low tangentError.");
            report.add("  If every reciprocal transform maps endpoint metadata to the wrong/poor component while a non-reciprocal transform is excellent, fix crossing guide/endpoint metadata rather than moving the OBJ.");

            Path file = writeReport("route-component", report);
            source.sendSuccess(() -> Component.literal(
                    "Route-component geometry map complete. Report: " + file.toAbsolutePath()), false);
            for (String line : report) LogUtils.getLogger().info("[TC-ROUTE-COMPONENT] {}", line);
            return 1;
        } catch (Exception ex) {
            report.add("ERROR=" + ex.getClass().getName() + ":" + ex.getMessage());
            try {
                Path file = writeReport("route-component", report);
                source.sendFailure(Component.literal("Route-component debug failed; report: " + file.toAbsolutePath()));
            } catch (Exception ignored) {
                source.sendFailure(Component.literal("Route-component debug failed: " + ex.getMessage()));
            }
            return 0;
        }
    }

    private static final class ObjMeshFace {
        private final String objectName;
        private final int[] vertexIndices;

        private ObjMeshFace(String objectName, int[] vertexIndices) {
            this.objectName = objectName;
            this.vertexIndices = vertexIndices;
        }
    }

    private static final class ObjFaceMesh {
        private final String resource;
        private final List<Vec3> vertices;
        private final List<ObjMeshFace> faces;
        private final List<String> objectOrder;

        private ObjFaceMesh(String resource,
                            List<Vec3> vertices,
                            List<ObjMeshFace> faces,
                            List<String> objectOrder) {
            this.resource = resource;
            this.vertices = vertices;
            this.faces = faces;
            this.objectOrder = objectOrder;
        }
    }

    private static final class ObjFaceComponent {
        private final String objectName;
        private final int componentIndex;
        private final List<Integer> faceIndices;
        private final Set<Integer> vertexIndices;

        private ObjFaceComponent(String objectName,
                                 int componentIndex,
                                 List<Integer> faceIndices,
                                 Set<Integer> vertexIndices) {
            this.objectName = objectName;
            this.componentIndex = componentIndex;
            this.faceIndices = faceIndices;
            this.vertexIndices = vertexIndices;
        }
    }

    private static final class ComponentMeasure {
        private final double sourceGap;
        private final Vec3 nearestCentroid;
        private final PcaResult pca;
        private final double tangentError;
        private final int assignedEndpointPart;
        private final double assignedEndpointDistance;
        private final String endpointDistances;
        private final double score;

        private ComponentMeasure(double sourceGap,
                                 Vec3 nearestCentroid,
                                 PcaResult pca,
                                 double tangentError,
                                 int assignedEndpointPart,
                                 double assignedEndpointDistance,
                                 String endpointDistances,
                                 double score) {
            this.sourceGap = sourceGap;
            this.nearestCentroid = nearestCentroid;
            this.pca = pca;
            this.tangentError = tangentError;
            this.assignedEndpointPart = assignedEndpointPart;
            this.assignedEndpointDistance = assignedEndpointDistance;
            this.endpointDistances = endpointDistances;
            this.score = score;
        }
    }

    private static final class ComponentRank {
        private final ObjFaceComponent component;
        private final ComponentMeasure measure;

        private ComponentRank(ObjFaceComponent component, ComponentMeasure measure) {
            this.component = component;
            this.measure = measure;
        }
    }

    private static final class ComponentTransformRank {
        private final BlockPos root;
        private final Direction facing;
        private final boolean placeable;
        private final String rejectReason;
        private final int endpointPart;
        private final boolean reciprocal;
        private final ObjFaceComponent component;
        private final ComponentMeasure measure;
        private final boolean endpointTouch;
        private final double score;

        private ComponentTransformRank(BlockPos root,
                                       Direction facing,
                                       boolean placeable,
                                       String rejectReason,
                                       int endpointPart,
                                       boolean reciprocal,
                                       ObjFaceComponent component,
                                       ComponentMeasure measure,
                                       boolean endpointTouch,
                                       double score) {
            this.root = root;
            this.facing = facing;
            this.placeable = placeable;
            this.rejectReason = rejectReason;
            this.endpointPart = endpointPart;
            this.reciprocal = reciprocal;
            this.component = component;
            this.measure = measure;
            this.endpointTouch = endpointTouch;
            this.score = score;
        }
    }

    private static VisualAssembly nearestAssemblyById(List<VisualAssembly> assemblies,
                                                       String id,
                                                       Vec3 playerPos) {
        VisualAssembly best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (VisualAssembly assembly : assemblies) {
            if (!id.equals(assembly.spec.id())) continue;
            Vec3 anchor = Vec3.atCenterOf(assembly.spec.offsetForPart(assembly.root, assembly.facing, 0));
            double distance = anchor.distanceToSqr(playerPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = assembly;
            }
        }
        return best;
    }

    private static LegacyBatchTrackSpec.Endpoint findEndpointDefinition(LegacyBatchTrackSpec spec, int part) {
        for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
            if (endpoint.part() == part) return endpoint;
        }
        return null;
    }

    private static Vec3 endpointBoundary(VisualEndpoint endpoint) {
        Vec3 a = Vec3.atCenterOf(endpoint.pos);
        Vec3 b = Vec3.atCenterOf(endpoint.connectorCell);
        return new Vec3((a.x + b.x) * 0.5D,
                Math.min(endpoint.pos.getY(), endpoint.connectorCell.getY()) + 0.0625D,
                (a.z + b.z) * 0.5D);
    }

    private static BlockPos rootForEndpointAtCell(LegacyBatchTrackSpec spec,
                                                   Direction facing,
                                                   int endpointPart,
                                                   BlockPos requiredCell) {
        BlockPos offset = spec.offsetForPart(BlockPos.ZERO, facing, endpointPart);
        return requiredCell.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    private static ObjFaceMesh loadObjFaceMesh(VisualAssembly assembly) throws Exception {
        List<String> candidates = new ArrayList<>();
        if (assembly.spec.id().startsWith("track_switch_")) {
            candidates.add("/assets/traincraft/models/block/batch/" + assembly.spec.id()
                    + (assembly.active ? "_active.obj" : "_inactive.obj"));
        }
        candidates.add("/assets/traincraft/models/block/batch/" + assembly.spec.id() + ".obj");

        for (String resource : candidates) {
            try (InputStream stream = BatchTrackDiagnostics.class.getResourceAsStream(resource)) {
                if (stream == null) continue;
                List<Vec3> vertices = new ArrayList<>();
                List<ObjMeshFace> faces = new ArrayList<>();
                List<String> objectOrder = new ArrayList<>();
                Set<String> seenObjects = new HashSet<>();
                String objectName = "default";
                objectOrder.add(objectName);
                seenObjects.add(objectName);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("o ")) {
                            objectName = line.substring(2).trim();
                            if (objectName.isEmpty()) objectName = "default";
                            if (seenObjects.add(objectName)) objectOrder.add(objectName);
                            continue;
                        }
                        if (line.startsWith("v ")) {
                            String[] fields = line.split("\\s+");
                            if (fields.length >= 4) {
                                vertices.add(new Vec3(Double.parseDouble(fields[1]),
                                        Double.parseDouble(fields[2]), Double.parseDouble(fields[3])));
                            }
                            continue;
                        }
                        if (line.startsWith("f ")) {
                            String[] fields = line.split("\\s+");
                            if (fields.length < 4) continue;
                            int[] indices = new int[fields.length - 1];
                            boolean valid = true;
                            for (int i = 1; i < fields.length; i++) {
                                String token = fields[i];
                                int slash = token.indexOf('/');
                                String indexText = slash >= 0 ? token.substring(0, slash) : token;
                                try {
                                    int index = Integer.parseInt(indexText);
                                    if (index < 0) index = vertices.size() + index;
                                    else index = index - 1;
                                    if (index < 0 || index >= vertices.size()) {
                                        valid = false;
                                        break;
                                    }
                                    indices[i - 1] = index;
                                } catch (NumberFormatException ex) {
                                    valid = false;
                                    break;
                                }
                            }
                            if (valid) faces.add(new ObjMeshFace(objectName, indices));
                        }
                    }
                }
                if (!vertices.isEmpty() && !faces.isEmpty()) {
                    if (objectOrder.size() > 1 && "default".equals(objectOrder.get(0))) {
                        boolean hasDefaultFace = false;
                        for (ObjMeshFace face : faces) {
                            if ("default".equals(face.objectName)) { hasDefaultFace = true; break; }
                        }
                        if (!hasDefaultFace) objectOrder.remove(0);
                    }
                    return new ObjFaceMesh(resource, vertices, faces, objectOrder);
                }
            }
        }
        throw new IllegalStateException("Face-aware OBJ not found for " + assembly.spec.id());
    }

    private static List<ObjFaceComponent> buildFaceComponents(ObjFaceMesh mesh) {
        List<ObjFaceComponent> result = new ArrayList<>();
        for (String objectName : mesh.objectOrder) {
            List<Integer> objectFaces = new ArrayList<>();
            Map<Integer, List<Integer>> vertexToFaces = new HashMap<>();
            for (int faceIndex = 0; faceIndex < mesh.faces.size(); faceIndex++) {
                ObjMeshFace face = mesh.faces.get(faceIndex);
                if (!objectName.equals(face.objectName)) continue;
                objectFaces.add(faceIndex);
                for (int vertexIndex : face.vertexIndices) {
                    vertexToFaces.computeIfAbsent(vertexIndex, ignored -> new ArrayList<>()).add(faceIndex);
                }
            }

            Set<Integer> visited = new HashSet<>();
            int componentIndex = 0;
            for (int seed : objectFaces) {
                if (!visited.add(seed)) continue;
                java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
                queue.add(seed);
                List<Integer> faceIndices = new ArrayList<>();
                Set<Integer> vertexIndices = new HashSet<>();
                while (!queue.isEmpty()) {
                    int faceIndex = queue.removeFirst();
                    faceIndices.add(faceIndex);
                    ObjMeshFace face = mesh.faces.get(faceIndex);
                    for (int vertexIndex : face.vertexIndices) {
                        vertexIndices.add(vertexIndex);
                        List<Integer> linked = vertexToFaces.get(vertexIndex);
                        if (linked == null) continue;
                        for (int linkedFace : linked) {
                            if (visited.add(linkedFace)) queue.addLast(linkedFace);
                        }
                    }
                }
                result.add(new ObjFaceComponent(objectName, componentIndex++, faceIndices, vertexIndices));
            }
        }
        return result;
    }

    private static List<Vec3> transformVertexIds(List<Vec3> vertices,
                                                  Set<Integer> ids,
                                                  VisualAssembly assembly) {
        List<Vec3> raw = new ArrayList<>(ids.size());
        for (int index : ids) {
            if (index >= 0 && index < vertices.size()) raw.add(vertices.get(index));
        }
        return transformObjVertices(raw, assembly);
    }

    private static ComponentMeasure measureComponent(List<Vec3> world,
                                                     VisualAssembly crossing,
                                                     VisualEndpoint sourceEndpoint,
                                                     Vec3 sourceBoundary,
                                                     Vec3 sourceCentroid,
                                                     PcaResult sourcePca) {
        if (world.isEmpty()) {
            return new ComponentMeasure(999.0D, null, null, 90.0D, -1, 999.0D, "NONE", 999.0D);
        }
        double sourceGap = minHorizontalDistance(world, sourceBoundary);
        Vec3 centroid = nearestCentroid(world, sourceBoundary, Math.min(16, world.size()));
        PcaResult pca = computeLocalPca(world, sourceBoundary, 0.55D);
        if (pca == null) pca = computeLocalPca(world, sourceBoundary, 0.90D);
        double tangentError = (sourcePca == null || pca == null)
                ? 90.0D : lineAngleDifference(sourcePca.angleDeg, pca.angleDeg);

        int assignedPart = -1;
        double assignedDistance = Double.POSITIVE_INFINITY;
        List<String> endpointParts = new ArrayList<>();
        for (LegacyBatchTrackSpec.Endpoint endpointDef : crossing.spec.endpoints()) {
            VisualEndpoint endpoint = new VisualEndpoint(crossing, endpointDef);
            Vec3 endpointBoundary = endpointBoundary(endpoint);
            double distance = minHorizontalDistance(world, endpointBoundary);
            endpointParts.add(endpointDef.part() + ":" + String.format(Locale.ROOT, "%.6f", distance));
            if (distance < assignedDistance) {
                assignedDistance = distance;
                assignedPart = endpointDef.part();
            }
        }
        double centroidGap = horizontalDistance(sourceCentroid, centroid);
        double score = sourceGap + (centroidGap * 0.35D) + (tangentError * 0.010D);
        return new ComponentMeasure(sourceGap, centroid, pca, tangentError,
                assignedPart, assignedDistance, String.join(",", endpointParts), score);
    }

    private static double minHorizontalDistance(List<Vec3> points, Vec3 target) {
        double best = Double.POSITIVE_INFINITY;
        for (Vec3 point : points) {
            if (Math.abs(point.y - target.y) > 0.25D) continue;
            double dx = point.x - target.x;
            double dz = point.z - target.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < best) best = distance;
        }
        if (!Double.isFinite(best)) {
            for (Vec3 point : points) {
                double dx = point.x - target.x;
                double dz = point.z - target.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static ComponentTransformRank bestComponentForEndpoint(ObjFaceMesh mesh,
                                                                    List<ObjFaceComponent> components,
                                                                    VisualAssembly candidate,
                                                                    int endpointPart,
                                                                    VisualEndpoint sourceEndpoint,
                                                                    Vec3 sourceBoundary,
                                                                    Vec3 sourceCentroid,
                                                                    PcaResult sourcePca,
                                                                    CandidatePlacementCheck placement,
                                                                    boolean reciprocal) {
        LegacyBatchTrackSpec.Endpoint endpointDef = findEndpointDefinition(candidate.spec, endpointPart);
        VisualEndpoint evaluatedEndpoint = endpointDef == null ? null : new VisualEndpoint(candidate, endpointDef);
        ComponentTransformRank best = null;
        for (ObjFaceComponent component : components) {
            List<Vec3> world = transformVertexIds(mesh.vertices, component.vertexIndices, candidate);
            ComponentMeasure measure = measureComponent(world, candidate, sourceEndpoint,
                    sourceBoundary, sourceCentroid, sourcePca);
            double endpointDistance = evaluatedEndpoint == null
                    ? 999.0D : minHorizontalDistance(world, endpointBoundary(evaluatedEndpoint));
            boolean endpointTouch = endpointDistance <= 0.70D;
            double score = measure.score + (endpointTouch ? 0.0D : 2.0D + endpointDistance);
            if (!placement.placeable) score += 5.0D;
            ComponentTransformRank rank = new ComponentTransformRank(candidate.root, candidate.facing,
                    placement.placeable, placement.reason, endpointPart, reciprocal,
                    component, measure, endpointTouch, score);
            if (best == null || rank.score < best.score) best = rank;
        }
        return best == null
                ? new ComponentTransformRank(candidate.root, candidate.facing, placement.placeable,
                        placement.reason, endpointPart, reciprocal, null,
                        new ComponentMeasure(999.0D, null, null, 90.0D, -1, 999.0D, "NONE", 999.0D),
                        false, 999.0D)
                : best;
    }

    private static ComponentTransformRank bestComponentAny(ObjFaceMesh mesh,
                                                            List<ObjFaceComponent> components,
                                                            VisualAssembly candidate,
                                                            int endpointAtRequired,
                                                            VisualEndpoint sourceEndpoint,
                                                            Vec3 sourceBoundary,
                                                            Vec3 sourceCentroid,
                                                            PcaResult sourcePca,
                                                            CandidatePlacementCheck placement,
                                                            boolean reciprocal) {
        ComponentTransformRank best = null;
        for (ObjFaceComponent component : components) {
            List<Vec3> world = transformVertexIds(mesh.vertices, component.vertexIndices, candidate);
            ComponentMeasure measure = measureComponent(world, candidate, sourceEndpoint,
                    sourceBoundary, sourceCentroid, sourcePca);
            boolean endpointTouch = endpointAtRequired >= 0
                    && measure.assignedEndpointPart == endpointAtRequired
                    && measure.assignedEndpointDistance <= 0.70D;
            double score = measure.score + (endpointTouch ? 0.0D : 1.0D);
            if (!placement.placeable) score += 5.0D;
            ComponentTransformRank rank = new ComponentTransformRank(candidate.root, candidate.facing,
                    placement.placeable, placement.reason, endpointAtRequired, reciprocal,
                    component, measure, endpointTouch, score);
            if (best == null || rank.score < best.score) best = rank;
        }
        return best;
    }

    private static String formatComponentMeasure(ComponentMeasure measure) {
        return String.format(Locale.ROOT,
                "sourceGap=%.6f centroid=%s tangentDeg=%s tangentError=%.6f assignedEndpoint=%d assignedEndpointDistance=%.6f endpointDistances=[%s] score=%.6f",
                measure.sourceGap,
                formatVec(measure.nearestCentroid),
                measure.pca == null ? "NONE" : String.format(Locale.ROOT, "%.6f", measure.pca.angleDeg),
                measure.tangentError,
                measure.assignedEndpointPart,
                measure.assignedEndpointDistance,
                measure.endpointDistances,
                measure.score);
    }

    private static String formatComponentTransformRank(ComponentTransformRank rank) {
        String componentText = rank.component == null ? "NONE"
                : rank.component.objectName + "#" + rank.component.componentIndex
                + "(faces=" + rank.component.faceIndices.size()
                + ",verts=" + rank.component.vertexIndices.size() + ")";
        return String.format(Locale.ROOT,
                "root=%s facing=%s placeable=%s reject=%s endpointPart=%d reciprocal=%s component=%s endpointTouch=%s transformScore=%.6f %s",
                rank.root, rank.facing, rank.placeable, rank.rejectReason,
                rank.endpointPart, rank.reciprocal, componentText,
                rank.endpointTouch, rank.score, formatComponentMeasure(rank.measure));
    }

    private static int startTrace(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TraceSession session = new TraceSession(player);
            TRACES.put(player.getUUID(), session);
            source.sendSuccess(() -> Component.literal(
                    "Track trace armed for 15 seconds. Drive the locomotive through the bad track now."), false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "tc_track_trace must be run by a player: " + ex.getMessage()));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        TraceSession session = TRACES.get(player.getUUID());
        if (session == null) {
            return;
        }

        if ((session.elapsed % TRACE_SAMPLE_INTERVAL) == 0) {
            sampleTrace(player, session);
        }

        session.elapsed++;
        if (session.elapsed >= TRACE_TICKS) {
            TRACES.remove(session.playerId);
            finishTrace(player, session);
        }
    }

    private static void sampleTrace(ServerPlayer player, TraceSession session) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(TRACE_RADIUS, 16.0D, TRACE_RADIUS);
        List<Entity> tracked = level.getEntitiesOfClass(Entity.class, box, entity -> {
            if (entity == player) return false;
            if (entity instanceof AbstractMinecart) return true;
            Package pkg = entity.getClass().getPackage();
            return pkg != null && pkg.getName().startsWith("traincraft");
        });

        session.report.add(String.format(Locale.ROOT,
                "TICK=%d player=(%.3f,%.3f,%.3f) tracked=%d",
                session.elapsed, player.getX(), player.getY(), player.getZ(), tracked.size()));

        for (Entity entity : tracked) {
            ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String typeText = type == null ? entity.getClass().getName() : type.toString();
            double speed = Math.sqrt(entity.getDeltaMovement().x * entity.getDeltaMovement().x
                    + entity.getDeltaMovement().z * entity.getDeltaMovement().z);

            session.report.add(String.format(Locale.ROOT,
                    "  ENTITY id=%d type=%s class=%s pos=(%.5f,%.5f,%.5f) motion=(%.6f,%.6f,%.6f) horizontalSpeed=%.6f yaw=%.3f",
                    entity.getId(), typeText, entity.getClass().getName(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z,
                    speed, entity.getYRot()));

            appendNearestRail(session.report, level, entity);
        }
    }

    private static void appendNearestRail(List<String> report, ServerLevel level, Entity entity) {
        BlockPos base = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        BlockPos bestPos = null;
        BlockState bestState = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = -3; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BaseRailBlock)) continue;

                    double cx = pos.getX() + 0.5D;
                    double cy = pos.getY() + 0.125D;
                    double cz = pos.getZ() + 0.5D;
                    double ddx = entity.getX() - cx;
                    double ddy = entity.getY() - cy;
                    double ddz = entity.getZ() - cz;
                    double distance = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = pos.immutable();
                        bestState = state;
                    }
                }
            }
        }

        if (bestPos == null || bestState == null) {
            report.add("    NEAREST_RAIL none within 2 horizontal / 3 vertical blocks");
            return;
        }

        BaseRailBlock rail = (BaseRailBlock) bestState.getBlock();
        AbstractMinecart minecart = entity instanceof AbstractMinecart cart ? cart : null;
        String sampled;
        try {
            sampled = rail.getRailDirection(bestState, level, bestPos, minecart).getSerializedName();
        } catch (RuntimeException ex) {
            sampled = "ERROR_" + ex.getClass().getSimpleName();
        }

        report.add(String.format(Locale.ROOT,
                "    NEAREST_RAIL pos=%s centerDistance=%.5f sampled=%s state=%s",
                bestPos, Math.sqrt(bestDistance), sampled, bestState));

        if (bestState.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            int part = batch.getPart(bestState);
            Direction facing = batch.getAssemblyFacing(bestState);
            boolean active = bestState.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                    && bestState.getValue(LegacyBatchSwitchRailBlock.ACTIVE);
            RailShape primary = batch.getSpec().shapeForPart(facing, part, false);
            RailShape alternate = batch.getSpec().shapeForPart(facing, part, true);
            report.add("    BATCH id=" + batch.getSpec().id()
                    + " part=" + part
                    + " facing=" + facing
                    + " active=" + active
                    + " primary=" + primary.getSerializedName()
                    + " alternate=" + alternate.getSerializedName()
                    + " root=" + batch.getAssemblyRoot(bestPos, bestState));
        }
    }

    private static void finishTrace(ServerPlayer player, TraceSession session) {
        try {
            Path file = writeReport("track-trace", session.report);
            player.sendSystemMessage(Component.literal(
                    "Track trace complete: " + file.toAbsolutePath()));
            for (String line : session.report) {
                LogUtils.getLogger().info("[TC-BATCH-TRACE] {}", line);
            }
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal(
                    "Could not write track trace: " + ex.getMessage()));
        }
    }

    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());

        Set<String> assemblies = new HashSet<>();
        List<String> report = new ArrayList<>();
        report.add("TRAINCRAFT STEP 9.3a-d1 BATCH TRACK DIAGNOSTICS");
        report.add("dimension=" + level.dimension().location());
        report.add("scanCenter=" + center + " radius=48 horizontal,8 vertical");
        report.add("READ ONLY: particles/logs only; no block or entity changes.");

        for (BlockPos scan : BlockPos.betweenClosed(
                center.offset(-48, -8, -48),
                center.offset(48, 8, 48))) {
            if (!level.hasChunkAt(scan)) continue;

            BlockState state = level.getBlockState(scan);
            if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock block)) {
                continue;
            }

            Direction facing = block.getAssemblyFacing(state);
            BlockPos root = block.getAssemblyRoot(scan, state);
            String key = block.getSpec().id() + "|" + root + "|" + facing;

            if (!assemblies.add(key)) {
                continue;
            }

            boolean active = state.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                    && state.getValue(LegacyBatchSwitchRailBlock.ACTIVE);

            LegacyBatchTrackSpec spec = block.getSpec();
            report.add("");
            report.add("ASSEMBLY id=" + spec.id()
                    + " root=" + root
                    + " facing=" + facing
                    + " parts=" + spec.partCount()
                    + " dynamic=" + spec.dynamicRouting()
                    + " active=" + active);

            appendGuideBounds(report, spec, root, facing);
            appendObjBounds(report, spec.id(), active);

            for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                BlockPos endpointPos = spec.offsetForPart(root, facing, endpoint.part());
                Direction outward = spec.rotateDirection(facing, endpoint.outward());
                report.add("  ENDPOINT part=" + endpoint.part()
                        + " pos=" + endpointPos
                        + " outward=" + outward);
                appendEndpointNeighbor(report, level, endpointPos.relative(outward), "sameY");
                appendEndpointNeighbor(report, level, endpointPos.relative(outward).above(), "above");
                appendEndpointNeighbor(report, level, endpointPos.relative(outward).below(), "below");
            }

            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos pos = spec.offsetForPart(root, facing, part);
                if (!level.hasChunkAt(pos)) {
                    report.add("  PART " + part + " UNLOADED pos=" + pos);
                    continue;
                }

                BlockState actual = level.getBlockState(pos);
                RailShape selected = spec.shapeForPart(facing, part, active);
                RailShape primary = spec.shapeForPart(facing, part, false);
                RailShape alternate = spec.shapeForPart(facing, part, true);
                String sampledText = "not_rail";
                boolean owned = actual.getBlock() == block
                        && block.getPart(actual) == part
                        && block.getAssemblyFacing(actual) == facing;

                if (actual.getBlock() instanceof BaseRailBlock rail) {
                    try {
                        sampledText = rail.getRailDirection(
                                actual, level, pos, null).getSerializedName();
                    } catch (RuntimeException ex) {
                        sampledText = "ERROR_" + ex.getClass().getSimpleName();
                    }
                }

                BlockPos supportPos = pos.below();
                boolean sturdy = level.getBlockState(supportPos)
                        .isFaceSturdy(level, supportPos, Direction.UP);

                report.add("  PART " + part
                        + " pos=" + pos
                        + " owned=" + owned
                        + " selected=" + selected.getSerializedName()
                        + " primary=" + primary.getSerializedName()
                        + " alternate=" + alternate.getSerializedName()
                        + " sampledNullCart=" + sampledText
                        + " supportPos=" + supportPos
                        + " supportSturdy=" + sturdy
                        + " actual=" + actual);

                level.sendParticles(
                        ParticleTypes.END_ROD,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.20D,
                        pos.getZ() + 0.5D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        report.add("");
        report.add("ASSEMBLY_COUNT=" + assemblies.size());

        try {
            Path file = writeReport("batch-track", report);
            source.sendSuccess(
                    () -> Component.literal(
                            "Batch track debug: " + assemblies.size()
                                    + " assemblies. Report: "
                                    + file.toAbsolutePath()),
                    false);

            for (String line : report) {
                LogUtils.getLogger().info("[TC-BATCH-TRACK] {}", line);
            }
            return assemblies.size();
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "Could not write batch-track report: " + ex.getMessage()));
            return 0;
        }
    }

    private static void appendGuideBounds(List<String> report,
                                          LegacyBatchTrackSpec spec,
                                          BlockPos root,
                                          Direction facing) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos pos = spec.offsetForPart(root, facing, part);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        report.add("  GUIDE_BOUNDS min=(" + minX + "," + minY + "," + minZ
                + ") max=(" + maxX + "," + maxY + "," + maxZ + ")");
    }

    private static void appendEndpointNeighbor(List<String> report,
                                               ServerLevel level,
                                               BlockPos pos,
                                               String label) {
        BlockState state = level.getBlockState(pos);
        String sampled = "not_rail";
        if (state.getBlock() instanceof BaseRailBlock rail) {
            try {
                sampled = rail.getRailDirection(state, level, pos, null).getSerializedName();
            } catch (RuntimeException ex) {
                sampled = "ERROR_" + ex.getClass().getSimpleName();
            }
        }
        report.add("    NEIGHBOR " + label + " pos=" + pos
                + " sampled=" + sampled + " state=" + state);
    }

    private static void appendObjBounds(List<String> report, String id, boolean active) {
        List<String> candidates = new ArrayList<>();
        if (id.startsWith("track_switch_")) {
            candidates.add("/assets/traincraft/models/block/batch/" + id
                    + (active ? "_active.obj" : "_inactive.obj"));
        }
        candidates.add("/assets/traincraft/models/block/batch/" + id + ".obj");

        for (String resource : candidates) {
            try (InputStream stream = BatchTrackDiagnostics.class.getResourceAsStream(resource)) {
                if (stream == null) continue;

                double minX = Double.POSITIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                int vertices = 0;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("v ")) continue;
                        String[] parts = line.split("\\s+");
                        if (parts.length < 4) continue;
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double z = Double.parseDouble(parts[3]);
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                        vertices++;
                    }
                }

                report.add(String.format(Locale.ROOT,
                        "  OBJ resource=%s vertices=%d rawBoundsX=[%.5f,%.5f] rawBoundsY=[%.5f,%.5f] rawBoundsZ=[%.5f,%.5f]",
                        resource, vertices, minX, maxX, minY, maxY, minZ, maxZ));
                return;
            } catch (Exception ex) {
                report.add("  OBJ resource=" + resource
                        + " ERROR=" + ex.getClass().getSimpleName()
                        + ":" + ex.getMessage());
                return;
            }
        }

        report.add("  OBJ resource=NOT_FOUND for id=" + id + " active=" + active);
    }

    private static Path writeReport(String prefix, List<String> report) throws Exception {
        Path dir = Path.of("logs", "traincraft-track-debug");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path file = dir.resolve(prefix + "-" + stamp + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".txt");
        Files.write(file, report, StandardCharsets.UTF_8);
        return file;
    }
}
