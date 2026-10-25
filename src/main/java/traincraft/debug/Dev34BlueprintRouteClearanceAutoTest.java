package traincraft.debug;

// TC_DEV34_R1B6H_TEMP_HEADLESS_BLUEPRINT_ROUTE_AUTOTEST

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.blueprint.BuilderStructurePlan;
import traincraft.blueprint.BuilderTrackClearanceGeometry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TEMPORARY r1b6h Blueprint-route clearance acceptance harness.
 *
 * Dedicated server only. No commands and no player required.
 *
 * For every current LegacyBatchTrackSpec and all four horizontal rotations:
 *  - place the real physical rail assembly in a temporary high-altitude pad,
 *  - create a synthetic one-segment Blueprint using the same track id/yaw,
 *  - compare BuilderStructurePlan.tooCloseRelative(...) against the already
 *    accepted r1b4 physical-world geometry result,
 *  - compare Normal and *_energy item ids,
 *  - probe real guide cells, logical connectors, outward connector cells and
 *    a dense 9x9 region around the old straight-chord midpoint so false
 *    positives inside large curves are detected.
 *
 * The same comparison is repeated for the dedicated Medium Curve.
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class Dev34BlueprintRouteClearanceAutoTest {
    private static final String PREFIX =
            "[TC-DEV34-BLUEPRINT-ROUTE] ";

    private static final int PAD_OFFSET_X =
            96;

    private static final int PAD_OFFSET_Z =
            96;

    private static final int PROBE_FORWARD_BLOCKS =
            3;

    private static final double FULL_RADIUS =
            BuilderStructurePlan.CLEARANCE_RADIUS_XZ
                    + Math.sqrt(0.5D);

    private static final double FULL_BELOW =
            BuilderStructurePlan.CLEARANCE_BELOW
                    + 0.5D;

    private static final double FULL_ABOVE =
            BuilderStructurePlan.CLEARANCE_ABOVE
                    + 0.5D;

    private Dev34BlueprintRouteClearanceAutoTest() {}

    private record Counters(
            int cases,
            int probes,
            int failures,
            int skipped,
            int safeProbes,
            int blockedProbes) {
        Counters addCase() {
            return new Counters(
                    cases + 1,
                    probes,
                    failures,
                    skipped,
                    safeProbes,
                    blockedProbes);
        }

        Counters addProbe(
                boolean passed,
                boolean expectedBlocked) {
            return new Counters(
                    cases,
                    probes + 1,
                    failures + (passed ? 0 : 1),
                    skipped,
                    safeProbes + (expectedBlocked ? 0 : 1),
                    blockedProbes + (expectedBlocked ? 1 : 0));
        }

        Counters addSkipped() {
            return new Counters(
                    cases,
                    probes,
                    failures,
                    skipped + 1,
                    safeProbes,
                    blockedProbes);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(
            ServerStartedEvent event) {
        if (!event.getServer()
                .isDedicatedServer()) {
            return;
        }

        ServerLevel level =
                event.getServer()
                        .overworld();

        List<String> report =
                new ArrayList<>();

        line(report,
                "========================================================================");
        line(report,
                " TAFARICRAFT - TRAINS DEV34 r1b6h BLUEPRINT ROUTE CLEARANCE AUTOTEST");
        line(report,
                " AUTO-RUN DEDICATED SERVER / NO COMMANDS / NO PLAYER");
        line(report,
                "========================================================================");
        line(report,
                "baseRadius=" + BuilderStructurePlan.CLEARANCE_RADIUS_XZ
                        + " fullBlockRadius=" + FULL_RADIUS
                        + " fullBelow=" + FULL_BELOW
                        + " fullAbove=" + FULL_ABOVE);

        int testY =
                Math.min(
                        level.getMaxBuildHeight() - 24,
                        Math.max(
                                level.getMinBuildHeight() + 96,
                                level.getSharedSpawnPos().getY() + 96));

        BlockPos base =
                new BlockPos(
                        level.getSharedSpawnPos().getX() + PAD_OFFSET_X,
                        testY,
                        level.getSharedSpawnPos().getZ() + PAD_OFFSET_Z);

        line(report,
                "testBase=" + base
                        + " dimension="
                        + level.dimension().location());

        Counters counters =
                new Counters(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0);

        try {
            List<LegacyBatchTrackSpec> specs =
                    allSpecs();

            line(report,
                    "discoveredBatchSpecs="
                            + specs.size());

            for (LegacyBatchTrackSpec spec :
                    specs) {
                for (Direction facing :
                        Direction.Plane.HORIZONTAL) {
                    counters =
                            counters.addCase();

                    counters =
                            testBatchCase(
                                    level,
                                    base,
                                    spec,
                                    facing,
                                    report,
                                    counters);
                }
            }

            for (Direction facing :
                    Direction.Plane.HORIZONTAL) {
                counters =
                        counters.addCase();

                counters =
                        testMediumCase(
                                level,
                                base,
                                facing,
                                false,
                                report,
                                counters);

                counters =
                        counters.addCase();

                counters =
                        testMediumCase(
                                level,
                                base,
                                facing,
                                true,
                                report,
                                counters);
            }
        }
        catch (Throwable ex) {
            line(report,
                    "HARNESS_EXCEPTION="
                            + ex.getClass().getName()
                            + ": "
                            + ex.getMessage());

            ex.printStackTrace();

            counters =
                    new Counters(
                            counters.cases(),
                            counters.probes(),
                            counters.failures() + 1,
                            counters.skipped(),
                            counters.safeProbes(),
                            counters.blockedProbes());
        }

        line(report, "");
        line(report,
                "========================================================================");
        line(report,
                " HEADLESS BLUEPRINT ROUTE CLEARANCE SUMMARY");
        line(report,
                "========================================================================");
        line(report,
                "cases=" + counters.cases());
        line(report,
                "probes=" + counters.probes());
        line(report,
                "safeProbes=" + counters.safeProbes());
        line(report,
                "blockedProbes=" + counters.blockedProbes());
        line(report,
                "failures=" + counters.failures());
        line(report,
                "skipped=" + counters.skipped());

        String status =
                counters.failures() == 0
                        && counters.safeProbes() > 0
                        ? "HEADLESS_BLUEPRINT_ROUTE_PASS"
                        : "HEADLESS_BLUEPRINT_ROUTE_FAIL";

        line(report,
                "STATUS=" + status);

        Path output =
                writeReport(report);

        System.out.println(
                PREFIX
                        + "============================================================");
        System.out.println(
                PREFIX
                        + "STATUS="
                        + status
                        + " cases="
                        + counters.cases()
                        + " probes="
                        + counters.probes()
                        + " safe="
                        + counters.safeProbes()
                        + " blocked="
                        + counters.blockedProbes()
                        + " failures="
                        + counters.failures()
                        + " skipped="
                        + counters.skipped());

        if (output != null) {
            System.out.println(
                    PREFIX
                            + "REPORT="
                            + output.toAbsolutePath());
        }

        System.out.println(
                PREFIX
                        + "TEMP TEST AREA RESTORED; SERVER MAY CONTINUE RUNNING.");
        System.out.println(
                PREFIX
                        + "============================================================");
    }

    private static Counters testBatchCase(
            ServerLevel level,
            BlockPos base,
            LegacyBatchTrackSpec spec,
            Direction facing,
            List<String> report,
            Counters counters) {
        Block registryBlock =
                ForgeRegistries.BLOCKS
                        .getValue(
                                new ResourceLocation(
                                        Traincraft.MOD_ID,
                                        spec.id() + "_segment"));

        if (!(registryBlock
                instanceof AbstractLegacyBatchRailBlock block)) {
            line(report,
                    "SKIP spec="
                            + spec.id()
                            + " facing="
                            + facing
                            + " reason=segment block missing/not batch");

            return counters.addSkipped();
        }

        Map<BlockPos, BlockState> original =
                new LinkedHashMap<>();

        int beforeFailures =
                counters.failures();

        try {
            BlockPos root =
                    base;

            placeBatch(
                    level,
                    root,
                    facing,
                    spec,
                    block,
                    original);

            BlockPos routeExitRel =
                    farthestEndpointRelative(
                            spec,
                            root,
                            facing);

            ItemStack normal =
                    blueprintFor(
                            "traincraft:" + spec.id(),
                            facing,
                            false,
                            routeExitRel);

            ItemStack energy =
                    blueprintFor(
                            "traincraft:" + spec.id() + "_energy",
                            facing,
                            false,
                            routeExitRel);

            Set<BlockPos> candidates =
                    batchCandidates(
                            root,
                            spec,
                            facing);

            for (BlockPos candidate :
                    candidates) {
                counters =
                        compareProbe(
                                level,
                                root,
                                candidate,
                                normal,
                                energy,
                                "spec="
                                        + spec.id()
                                        + " facing="
                                        + facing
                                        + " candidate="
                                        + candidate,
                                report,
                                counters);
            }

            if (counters.failures()
                    == beforeFailures) {
                line(report,
                        "PASS CASE spec="
                                + spec.id()
                                + " facing="
                                + facing
                                + " probes="
                                + candidates.size());
            }

            return counters;
        }
        finally {
            restore(
                    level,
                    original);
        }
    }

    private static Counters testMediumCase(
            ServerLevel level,
            BlockPos base,
            Direction facing,
            boolean left,
            List<String> report,
            Counters counters) {
        Block registryBlock =
                ForgeRegistries.BLOCKS
                        .getValue(
                                new ResourceLocation(
                                        Traincraft.MOD_ID,
                                        "track_medium_curve_segment"));

        if (!(registryBlock
                instanceof LegacyMediumCurveRailBlock block)) {
            line(report,
                    "SKIP medium facing="
                            + facing
                            + " left="
                            + left
                            + " reason=segment block missing");

            return counters.addSkipped();
        }

        Map<BlockPos, BlockState> original =
                new LinkedHashMap<>();

        int beforeFailures =
                counters.failures();

        try {
            BlockPos root =
                    base;

            for (int part = 0;
                 part < LegacyMediumCurveRailBlock.PART_COUNT;
                 part++) {
                BlockPos railPos =
                        LegacyMediumCurveRailBlock
                                .offsetForPart(
                                        root,
                                        facing,
                                        part);

                remember(
                        level,
                        railPos.below(),
                        original);
                remember(
                        level,
                        railPos,
                        original);

                level.setBlock(
                        railPos.below(),
                        Blocks.STONE.defaultBlockState(),
                        2);

                BlockState state =
                        block.defaultBlockState()
                                .setValue(
                                        RailBlock.SHAPE,
                                        LegacyMediumCurveRailBlock
                                                .shapeForPart(
                                                        facing,
                                                        part))
                                .setValue(
                                        LegacyMediumCurveRailBlock
                                                .ASSEMBLY_FACING,
                                        facing)
                                .setValue(
                                        LegacyMediumCurveRailBlock.PART,
                                        part);

                level.setBlock(
                        railPos,
                        state,
                        2);
            }

            int exitPart =
                    left
                            ? 0
                            : LegacyMediumCurveRailBlock.PART_COUNT - 1;

            BlockPos exit =
                    LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    root,
                                    facing,
                                    exitPart);

            BlockPos routeExitRel =
                    exit.subtract(root);

            ItemStack normal =
                    blueprintFor(
                            "traincraft:track_medium_curve",
                            facing,
                            left,
                            routeExitRel);

            ItemStack energy =
                    blueprintFor(
                            "traincraft:track_medium_curve_energy",
                            facing,
                            left,
                            routeExitRel);

            Set<BlockPos> candidates =
                    mediumCandidates(
                            root,
                            facing);

            for (BlockPos candidate :
                    candidates) {
                counters =
                        compareProbe(
                                level,
                                root,
                                candidate,
                                normal,
                                energy,
                                "spec=track_medium_curve"
                                        + " facing="
                                        + facing
                                        + " left="
                                        + left
                                        + " candidate="
                                        + candidate,
                                report,
                                counters);
            }

            if (counters.failures()
                    == beforeFailures) {
                line(report,
                        "PASS CASE spec=track_medium_curve"
                                + " facing="
                                + facing
                                + " left="
                                + left
                                + " probes="
                                + candidates.size());
            }

            return counters;
        }
        finally {
            restore(
                    level,
                    original);
        }
    }

    private static Counters compareProbe(
            ServerLevel level,
            BlockPos root,
            BlockPos candidate,
            ItemStack normalBlueprint,
            ItemStack energyBlueprint,
            String label,
            List<String> report,
            Counters counters) {
        boolean worldBlocked =
                BuilderTrackClearanceGeometry
                        .tooClose(
                                level,
                                candidate,
                                FULL_RADIUS,
                                FULL_BELOW,
                                FULL_ABOVE);

        BlockPos relative =
                candidate.subtract(root);

        boolean normalBlocked =
                BuilderStructurePlan
                        .tooCloseRelative(
                                normalBlueprint,
                                relative);

        boolean energyBlocked =
                BuilderStructurePlan
                        .tooCloseRelative(
                                energyBlueprint,
                                relative);

        boolean passed =
                worldBlocked == normalBlocked
                        && worldBlocked == energyBlocked;

        if (!passed) {
            line(report,
                    "FAIL "
                            + label
                            + " physical="
                            + worldBlocked
                            + " blueprintNormal="
                            + normalBlocked
                            + " blueprintEnergy="
                            + energyBlocked
                            + " relative="
                            + relative);
        }

        return counters.addProbe(
                passed,
                worldBlocked);
    }

    private static Set<BlockPos> batchCandidates(
            BlockPos root,
            LegacyBatchTrackSpec spec,
            Direction facing) {
        Set<BlockPos> out =
                new LinkedHashSet<>();

        int minX =
                Integer.MAX_VALUE;
        int minY =
                Integer.MAX_VALUE;
        int minZ =
                Integer.MAX_VALUE;
        int maxX =
                Integer.MIN_VALUE;
        int maxY =
                Integer.MIN_VALUE;
        int maxZ =
                Integer.MIN_VALUE;

        for (int part = 0;
             part < spec.partCount();
             part++) {
            BlockPos pos =
                    spec.offsetForPart(
                            root,
                            facing,
                            part);

            out.add(pos);

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        for (LegacyBatchTrackSpec.Endpoint endpoint :
                spec.endpoints()) {
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

            Direction outward =
                    spec.rotateDirection(
                            facing,
                            endpoint.outward());

            out.add(physical);
            out.add(logical);

            for (int step = 1;
                 step <= PROBE_FORWARD_BLOCKS;
                 step++) {
                out.add(
                        logical.relative(
                                outward,
                                step));
            }

            Direction right =
                    outward.getClockWise();

            for (int side = -2;
                 side <= 2;
                 side++) {
                if (side == 0) {
                    continue;
                }

                out.add(
                        logical.relative(
                                side > 0
                                        ? right
                                        : right.getOpposite(),
                                Math.abs(side)));
            }
        }

        // Dense probes around the old chord/bounding-box center. These are
        // essential: r1b5's old straight-chord approximation could report
        // blocked cells deep inside a large curve where the physical geometry
        // correctly reports safe.
        int centerX =
                (minX + maxX) / 2;
        int centerY =
                (minY + maxY) / 2;
        int centerZ =
                (minZ + maxZ) / 2;

        for (int dx = -4;
             dx <= 4;
             dx++) {
            for (int dz = -4;
                 dz <= 4;
                 dz++) {
                out.add(
                        new BlockPos(
                                centerX + dx,
                                centerY,
                                centerZ + dz));
            }
        }

        return out;
    }

    private static Set<BlockPos> mediumCandidates(
            BlockPos root,
            Direction facing) {
        Set<BlockPos> out =
                new LinkedHashSet<>();

        int minX =
                Integer.MAX_VALUE;
        int minY =
                Integer.MAX_VALUE;
        int minZ =
                Integer.MAX_VALUE;
        int maxX =
                Integer.MIN_VALUE;
        int maxY =
                Integer.MIN_VALUE;
        int maxZ =
                Integer.MIN_VALUE;

        for (int part = 0;
             part < LegacyMediumCurveRailBlock.PART_COUNT;
             part++) {
            BlockPos pos =
                    LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    root,
                                    facing,
                                    part);

            out.add(pos);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int centerX =
                (minX + maxX) / 2;
        int centerY =
                (minY + maxY) / 2;
        int centerZ =
                (minZ + maxZ) / 2;

        for (int dx = -4;
             dx <= 4;
             dx++) {
            for (int dz = -4;
                 dz <= 4;
                 dz++) {
                out.add(
                        new BlockPos(
                                centerX + dx,
                                centerY,
                                centerZ + dz));
            }
        }

        return out;
    }

    private static BlockPos farthestEndpointRelative(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing) {
        BlockPos best =
                BlockPos.ZERO;

        long bestDistance =
                Long.MIN_VALUE;

        for (LegacyBatchTrackSpec.Endpoint endpoint :
                spec.endpoints()) {
            BlockPos pos =
                    spec.endpointPosition(
                            root,
                            facing,
                            endpoint);

            BlockPos rel =
                    pos.subtract(root);

            long distance =
                    (long) rel.getX() * rel.getX()
                            + (long) rel.getY() * rel.getY()
                            + (long) rel.getZ() * rel.getZ();

            if (distance > bestDistance) {
                bestDistance = distance;
                best = rel;
            }
        }

        return best;
    }

    private static ItemStack blueprintFor(
            String itemId,
            Direction facing,
            boolean left,
            BlockPos routeExitRelative) {
        ItemStack blueprint =
                new ItemStack(
                        Items.PAPER);

        CompoundTag segment =
                new CompoundTag();

        segment.putInt("X", 0);
        segment.putInt("Y", 0);
        segment.putInt("Z", 0);
        segment.putString(
                "Item",
                itemId);
        segment.putFloat(
                "Yaw",
                facing.toYRot());
        segment.putBoolean(
                "Left",
                left);

        if (routeExitRelative != null) {
            segment.putInt(
                    "RouteExitX",
                    routeExitRelative.getX());
            segment.putInt(
                    "RouteExitY",
                    routeExitRelative.getY());
            segment.putInt(
                    "RouteExitZ",
                    routeExitRelative.getZ());
        }

        ListTag segments =
                new ListTag();

        segments.add(
                segment);

        CompoundTag data =
                new CompoundTag();

        data.put(
                "Segments",
                segments);

        CompoundTag root =
                new CompoundTag();

        root.put(
                "TafariTrackBlueprint",
                data);

        blueprint.setTag(
                root);

        return blueprint;
    }

    private static void placeBatch(
            ServerLevel level,
            BlockPos root,
            Direction facing,
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block,
            Map<BlockPos, BlockState> original) {
        for (int part = 0;
             part < spec.partCount();
             part++) {
            BlockPos railPos =
                    spec.offsetForPart(
                            root,
                            facing,
                            part);

            BlockPos supportPos =
                    railPos.below();

            remember(
                    level,
                    supportPos,
                    original);

            remember(
                    level,
                    railPos,
                    original);

            level.setBlock(
                    supportPos,
                    Blocks.STONE.defaultBlockState(),
                    2);

            level.setBlock(
                    railPos,
                    block.stateForPart(
                            facing,
                            part,
                            false),
                    2);
        }
    }

    private static void remember(
            ServerLevel level,
            BlockPos pos,
            Map<BlockPos, BlockState> original) {
        original.putIfAbsent(
                pos.immutable(),
                level.getBlockState(pos));
    }

    private static void restore(
            ServerLevel level,
            Map<BlockPos, BlockState> original) {
        List<Map.Entry<BlockPos, BlockState>> entries =
                new ArrayList<>(
                        original.entrySet());

        entries.sort(
                Comparator.comparingInt(
                        e -> -e.getKey().getY()));

        for (Map.Entry<BlockPos, BlockState> entry :
                entries) {
            level.setBlock(
                    entry.getKey(),
                    entry.getValue(),
                    2);
        }
    }

    private static List<LegacyBatchTrackSpec> allSpecs()
            throws IllegalAccessException {
        List<LegacyBatchTrackSpec> out =
                new ArrayList<>();

        for (Field field :
                LegacyBatchTrackSpecs.class
                        .getFields()) {
            if (!Modifier.isStatic(
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
                out.add(spec);
            }
        }

        out.sort(
                Comparator.comparing(
                        LegacyBatchTrackSpec::id));

        return out;
    }

    private static Path writeReport(
            List<String> report) {
        try {
            Path dir =
                    Path.of(
                            "logs",
                            "traincraft-dev34-headless");

            Files.createDirectories(
                    dir);

            String stamp =
                    LocalDateTime.now()
                            .format(
                                    DateTimeFormatter
                                            .ofPattern(
                                                    "yyyyMMdd-HHmmss-SSS"));

            Path output =
                    dir.resolve(
                            "blueprint-route-autotest-"
                                    + stamp
                                    + ".txt");

            Files.write(
                    output,
                    report,
                    StandardCharsets.UTF_8);

            return output;
        }
        catch (Exception ex) {
            System.err.println(
                    PREFIX
                            + "REPORT_WRITE_FAILED "
                            + ex);

            return null;
        }
    }

    private static void line(
            List<String> report,
            String value) {
        report.add(
                value);
    }
}

