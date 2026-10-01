package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step 9.3g-t7-d6-r2 — full-course baked seam audit.
 *
 * TEMPORARY CLIENT DIAGNOSTIC ONLY.
 *
 * Reads the newest t7-full-test-track report, resolves the exact 12 assemblies
 * that the real item/useOn course builder placed, and measures the FINAL BAKED
 * client vertices at every external seam.
 *
 * This distinguishes:
 *   - topology/snap discontinuity (endpoint cells are not adjacent), from
 *   - visual mesh shortfall (logical cells are adjacent but baked geometry
 *     stops before the half-cell seam).
 *
 * No world, source, route, topology, coupling, blockstate, OBJ, or model
 * mutation is performed.
 */
@Mod.EventBusSubscriber(
        modid = "traincraft",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT7CourseSeamAuditCommand {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final Pattern STEP = Pattern.compile(
            "^STEP index=(\\d+) label=(.*?) id=(\\S+) "
                    + "root=BlockPos\\{x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)\\} "
                    + "facing=(\\w+) validParts=(\\d+) attachedPart=(\\d+) "
                    + "exitPart=(\\d+) active=(\\S+) result=(\\S+).*$");

    private static final Pattern STARTER = Pattern.compile(
            "^STARTER northEnd=BlockPos\\{x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)\\} "
                    + "outward=(\\w+).*$");

    private static final Pattern RUNOUT = Pattern.compile(
            "^RUNOUT start=BlockPos\\{x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)\\} "
                    + "end=BlockPos\\{x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)\\} "
                    + "outward=(\\w+).*$");

    private static final double LOCAL_VERTEX_RADIUS = 3.25D;
    private static final double FLAG_GAP = 0.20D;
    private static final double WATCH_GAP = 0.06D;

    private TrackT7CourseSeamAuditCommand() {
    }

    private record StepInfo(
            int index,
            String label,
            String id,
            BlockPos root,
            Direction facing,
            int attachedPart,
            int exitPart) {
    }

    private record Course(
            Path report,
            BlockPos starterNorthEnd,
            BlockPos runoutStart,
            List<StepInfo> steps) {
    }

    private record Assembly(
            StepInfo step,
            AbstractLegacyBatchRailBlock block,
            LegacyBatchTrackSpec spec,
            BlockPos attachedCell,
            BlockPos exitCell,
            List<Vec3> vertices) {
    }

    private record SeamResult(
            int index,
            String from,
            String to,
            BlockPos fromCell,
            BlockPos toCell,
            double centerDistance,
            double sourceMaxT,
            double targetMinT,
            double seamT,
            double sourceShortfall,
            double targetShortfall,
            double visibleGap,
            String classification) {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_course_seam_audit")
                        .executes(context -> run())
        );
    }

    private static int run() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

        if (level == null || minecraft.player == null) {
            return Command.SINGLE_SUCCESS;
        }

        try {
            Course course = loadLatestCourseReport();
            List<Assembly> assemblies = resolveAssemblies(level, course);
            List<SeamResult> seams = analyze(level, course, assemblies);

            Path outputDir = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(outputDir);

            Path output = outputDir.resolve(
                    "t7-course-seam-audit-" + STAMP.format(LocalDateTime.now()) + ".txt");

            List<String> out = new ArrayList<>();
            out.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-d6-r1");
            out.add("FULL-COURSE FINAL BAKED SEAM AUDIT - EMBEDDED C2-R3 COURSE");
            out.add("MUTATION=NONE");
            out.add("COURSE_REPORT_REFERENCE=" + course.report());
            out.add("COURSE_STEP_COUNT=" + course.steps().size());
            out.add("SEAM_COUNT=" + seams.size());
            out.add(String.format(Locale.ROOT,
                    "THRESHOLDS watch>%.3f flag>%.3f", WATCH_GAP, FLAG_GAP));
            out.add("");

            int pass = 0;
            int watch = 0;
            int flag = 0;
            int topology = 0;

            for (SeamResult seam : seams) {
                switch (seam.classification()) {
                    case "PASS" -> pass++;
                    case "WATCH_VISUAL_GAP" -> watch++;
                    case "FLAG_VISUAL_GAP" -> flag++;
                    default -> topology++;
                }

                out.add(String.format(
                        Locale.ROOT,
                        "SEAM index=%02d from=%s to=%s fromCell=%s toCell=%s "
                                + "centerDistance=%.6f seamT=%.6f "
                                + "sourceMaxT=%.6f targetMinT=%.6f "
                                + "sourceShortfall=%.6f targetShortfall=%.6f "
                                + "visibleGap=%.6f classification=%s",
                        seam.index(),
                        seam.from(),
                        seam.to(),
                        pos(seam.fromCell()),
                        pos(seam.toCell()),
                        seam.centerDistance(),
                        seam.seamT(),
                        seam.sourceMaxT(),
                        seam.targetMinT(),
                        seam.sourceShortfall(),
                        seam.targetShortfall(),
                        seam.visibleGap(),
                        seam.classification()));
            }

            out.add("");
            out.add("PASS_SEAMS=" + pass);
            out.add("WATCH_SEAMS=" + watch);
            out.add("FLAG_SEAMS=" + flag);
            out.add("TOPOLOGY_SEAMS=" + topology);

            String result;
            if (topology > 0) {
                result = "T7_D6_TOPOLOGY_DISCONTINUITY_FOUND";
            } else if (flag > 0 || watch > 0) {
                result = "T7_D6_VISUAL_SEAM_SHORTFALL_PROVEN";
            } else {
                result = "T7_D6_ALL_COURSE_SEAMS_BAKED_CONTINUOUS";
            }
            out.add("RESULT=" + result);

            Files.write(output, out, StandardCharsets.UTF_8);

            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "TC t7 seam audit complete: " + result
                                    + " report=" + output.toAbsolutePath()));

        } catch (Throwable t) {
            minecraft.player.sendSystemMessage(
                    Component.literal(
                            "TC t7 seam audit failed: "
                                    + t.getClass().getSimpleName()
                                    + ": " + String.valueOf(t.getMessage())));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static Course loadLatestCourseReport() {
        // Step 9.3g-t7-d6-r2:
        // Client/server filesystems are separate in this runtime. Embed the
        // exact latest c2-r3 course supplied after the current world build.
        // resolveAssemblies() still verifies every root/spec/facing directly
        // against the live client world before any seam measurement.
        List<StepInfo> steps = List.of(
                new StepInfo(
                        0,
                        "Standard Medium LEFT - real snap p0, curved branch to common p10",
                        "track_switch_medium_left",
                        new BlockPos(43, -16, 41),
                        Direction.NORTH,
                        0,
                        10),
                new StepInfo(
                        1,
                        "Quarter curve",
                        "track_curve_big",
                        new BlockPos(38, -16, 37),
                        Direction.WEST,
                        0,
                        8),
                new StepInfo(
                        2,
                        "45 Medium switch - common p0 to diverging p4",
                        "track_switch_45_medium_right",
                        new BlockPos(34, -16, 42),
                        Direction.SOUTH,
                        0,
                        4),
                new StepInfo(
                        3,
                        "Medium diagonal after 45 switch",
                        "track_diagonal_straight_medium",
                        new BlockPos(32, -16, 45),
                        Direction.SOUTH,
                        0,
                        6),
                new StepInfo(
                        4,
                        "Diamond crossing - stay on incoming diagonal route",
                        "track_diamond_crossing",
                        new BlockPos(27, -16, 50),
                        Direction.SOUTH,
                        1,
                        2),
                new StepInfo(
                        5,
                        "Medium diagonal between crossings",
                        "track_diagonal_straight_medium",
                        new BlockPos(22, -16, 55),
                        Direction.NORTH,
                        6,
                        0),
                new StepInfo(
                        6,
                        "Original centered Diagonal crossing - stay on incoming route",
                        "track_diagonal_crossing",
                        new BlockPos(20, -16, 57),
                        Direction.SOUTH,
                        1,
                        4),
                new StepInfo(
                        7,
                        "Medium diagonal leaving crossing",
                        "track_diagonal_straight_medium",
                        new BlockPos(18, -16, 59),
                        Direction.SOUTH,
                        0,
                        6),
                new StepInfo(
                        8,
                        "Standalone 45 curve back to cardinal",
                        "track_curve_45_medium_left",
                        new BlockPos(12, -16, 64),
                        Direction.EAST,
                        3,
                        0),
                new StepInfo(
                        9,
                        "Parallel switch - common p0 to diverging far branch",
                        "track_switch_parallel_right",
                        new BlockPos(11, -16, 64),
                        Direction.WEST,
                        0,
                        17),
                new StepInfo(
                        10,
                        "Parallel/S curve",
                        "track_parallel_curve_medium_left",
                        new BlockPos(2, -16, 61),
                        Direction.WEST,
                        0,
                        13),
                new StepInfo(
                        11,
                        "Long straight slope",
                        "track_slope_long",
                        new BlockPos(-10, -16, 63),
                        Direction.WEST,
                        0,
                        11));

        return new Course(
                Path.of("embedded-c2-r3-course-20260922-153717-006-97b0c9cb.txt"),
                new BlockPos(43, -16, 42),
                new BlockPos(-22, -15, 63),
                steps);
    }
    private static List<Assembly> resolveAssemblies(
            ClientLevel level,
            Course course) {

        List<Assembly> out = new ArrayList<>();

        for (StepInfo step : course.steps()) {
            var state = level.getBlockState(step.root());
            if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                throw new IllegalStateException(
                        "course root no longer contains batch rail: "
                                + step.id() + " root=" + pos(step.root())
                                + " state=" + state);
            }

            LegacyBatchTrackSpec spec = batch.getSpec();
            if (!step.id().equals(spec.id())) {
                throw new IllegalStateException(
                        "course root spec mismatch expected="
                                + step.id() + " actual=" + spec.id());
            }

            Direction facing = batch.getAssemblyFacing(state);
            if (facing != step.facing()) {
                throw new IllegalStateException(
                        "course root facing mismatch id=" + step.id()
                                + " report=" + step.facing()
                                + " world=" + facing);
            }

            if (step.attachedPart() < 0
                    || step.attachedPart() >= spec.partCount()
                    || step.exitPart() < 0
                    || step.exitPart() >= spec.partCount()) {
                throw new IllegalStateException(
                        "course report part index out of range for " + step.id());
            }

            BlockPos attached = spec.offsetForPart(
                    step.root(), step.facing(), step.attachedPart());
            BlockPos exit = spec.offsetForPart(
                    step.root(), step.facing(), step.exitPart());

            List<Vec3> vertices = bakedAssemblyVertices(
                    level, batch, spec, step.root(), step.facing());

            if (vertices.isEmpty()) {
                throw new IllegalStateException(
                        "no baked vertices found for " + step.id()
                                + " root=" + pos(step.root()));
            }

            out.add(new Assembly(
                    step, batch, spec, attached, exit, vertices));
        }

        return List.copyOf(out);
    }

    private static List<SeamResult> analyze(
            ClientLevel level,
            Course course,
            List<Assembly> assemblies) {

        List<SeamResult> out = new ArrayList<>();
        int seamIndex = 0;

        Assembly first = assemblies.get(0);
        out.add(measure(
                seamIndex++,
                "starter_vanilla",
                first.step().id(),
                course.starterNorthEnd(),
                first.attachedCell(),
                bakedBlockVertices(level, course.starterNorthEnd()),
                first.vertices()));

        for (int i = 0; i + 1 < assemblies.size(); i++) {
            Assembly from = assemblies.get(i);
            Assembly to = assemblies.get(i + 1);

            out.add(measure(
                    seamIndex++,
                    from.step().id(),
                    to.step().id(),
                    from.exitCell(),
                    to.attachedCell(),
                    from.vertices(),
                    to.vertices()));
        }

        Assembly last = assemblies.get(assemblies.size() - 1);
        out.add(measure(
                seamIndex,
                last.step().id(),
                "runout_vanilla",
                last.exitCell(),
                course.runoutStart(),
                last.vertices(),
                bakedBlockVertices(level, course.runoutStart())));

        return List.copyOf(out);
    }

    private static SeamResult measure(
            int index,
            String from,
            String to,
            BlockPos fromCell,
            BlockPos toCell,
            List<Vec3> fromVertices,
            List<Vec3> toVertices) {

        Vec3 a = center(fromCell);
        Vec3 b = center(toCell);
        Vec3 delta = b.subtract(a);
        double length = delta.length();

        if (length <= 1.0E-9D) {
            return new SeamResult(
                    index, from, to, fromCell, toCell,
                    0.0D,
                    Double.NaN, Double.NaN, 0.0D,
                    Double.NaN, Double.NaN, Double.NaN,
                    "TOPOLOGY_SAME_CELL");
        }

        // Real snapped external endpoints should be neighboring cells.
        if (length > 1.75D) {
            return new SeamResult(
                    index, from, to, fromCell, toCell,
                    length,
                    Double.NaN, Double.NaN, length * 0.5D,
                    Double.NaN, Double.NaN, Double.NaN,
                    "TOPOLOGY_CELL_GAP");
        }

        Vec3 unit = delta.scale(1.0D / length);
        Vec3 midpoint = a.add(delta.scale(0.5D));
        double seamT = length * 0.5D;

        double sourceMaxT = Double.NEGATIVE_INFINITY;
        double targetMinT = Double.POSITIVE_INFINITY;

        for (Vec3 vertex : fromVertices) {
            if (vertex.distanceTo(midpoint) > LOCAL_VERTEX_RADIUS) {
                continue;
            }
            double t = vertex.subtract(a).dot(unit);
            sourceMaxT = Math.max(sourceMaxT, t);
        }

        for (Vec3 vertex : toVertices) {
            if (vertex.distanceTo(midpoint) > LOCAL_VERTEX_RADIUS) {
                continue;
            }
            double t = vertex.subtract(a).dot(unit);
            targetMinT = Math.min(targetMinT, t);
        }

        if (!Double.isFinite(sourceMaxT) || !Double.isFinite(targetMinT)) {
            return new SeamResult(
                    index, from, to, fromCell, toCell,
                    length,
                    sourceMaxT, targetMinT, seamT,
                    Double.NaN, Double.NaN, Double.NaN,
                    "NO_LOCAL_BAKED_GEOMETRY");
        }

        double sourceShortfall = seamT - sourceMaxT;
        double targetShortfall = targetMinT - seamT;
        double gap = targetMinT - sourceMaxT;

        String classification;
        if (gap > FLAG_GAP) {
            classification = "FLAG_VISUAL_GAP";
        } else if (gap > WATCH_GAP) {
            classification = "WATCH_VISUAL_GAP";
        } else {
            classification = "PASS";
        }

        return new SeamResult(
                index, from, to, fromCell, toCell,
                length,
                sourceMaxT, targetMinT, seamT,
                sourceShortfall, targetShortfall,
                gap, classification);
    }

    private static List<Vec3> bakedAssemblyVertices(
            ClientLevel level,
            AbstractLegacyBatchRailBlock batch,
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing) {

        Map<BlockPos, Boolean> seen = new HashMap<>();
        List<Vec3> out = new ArrayList<>();

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos pos = spec.offsetForPart(root, facing, part);
            if (seen.put(pos, Boolean.TRUE) != null) {
                continue;
            }

            var state = level.getBlockState(pos);
            if (state.getBlock() != batch) {
                continue;
            }

            out.addAll(bakedBlockVertices(level, pos));
        }

        return List.copyOf(out);
    }

    private static List<Vec3> bakedBlockVertices(
            ClientLevel level,
            BlockPos pos) {

        var state = level.getBlockState(pos);
        BakedModel model = Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(state);

        List<Vec3> out = new ArrayList<>();
        addQuadVertices(out, pos, model, state, null);

        for (Direction side : Direction.values()) {
            addQuadVertices(out, pos, model, state, side);
        }

        return List.copyOf(out);
    }

    private static void addQuadVertices(
            List<Vec3> out,
            BlockPos pos,
            BakedModel model,
            net.minecraft.world.level.block.state.BlockState state,
            Direction side) {

        RandomSource random = RandomSource.create(state.getSeed(pos));
        List<BakedQuad> quads = model.getQuads(state, side, random);

        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            if (data.length < 12 || data.length % 4 != 0) {
                continue;
            }

            int stride = data.length / 4;
            if (stride < 3) {
                continue;
            }

            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * stride;
                double x = Float.intBitsToFloat(data[base]);
                double y = Float.intBitsToFloat(data[base + 1]);
                double z = Float.intBitsToFloat(data[base + 2]);

                out.add(new Vec3(
                        pos.getX() + x,
                        pos.getY() + y,
                        pos.getZ() + z));
            }
        }
    }

    private static BlockPos blockPos(String x, String y, String z) {
        return new BlockPos(
                Integer.parseInt(x),
                Integer.parseInt(y),
                Integer.parseInt(z));
    }

    private static Direction direction(String name) {
        Direction direction = Direction.byName(name);
        if (direction == null) {
            throw new IllegalArgumentException("unknown direction: " + name);
        }
        return direction;
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D);
    }

    private static String pos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }
}
