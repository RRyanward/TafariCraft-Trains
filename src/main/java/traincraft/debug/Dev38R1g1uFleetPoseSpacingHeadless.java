package traincraft.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.block.track.LegacyContinuousTrackPath;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.block.track.LegacyStraightAssemblyRailBlock;
import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;

/**
 * TC_DEV38_R1G1U_FLEET_POSE_SPACING_DEEP_SWEEP
 *
 * Command-free dedicated-server pre-release matrix.
 *
 * The matrix deliberately separates two questions:
 *  1. server/path geometry: tested here;
 *  2. rendered model origin/renderer transforms: emitted by the installer as
 *     static source reports because a dedicated server cannot load client code.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1g1uFleetPoseSpacingHeadless {
    private static final String ENV = "TC_R1G1U_DEEP_SWEEP";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final double RAIL_Y = 0.0625D;
    private static final double[] GEOMETRY_SPACINGS =
            {0.30D, 0.60D, 1.00D, 1.30D, 1.50D, 2.00D};
    private static final double[] FLEET_SPACINGS =
            {0.60D, 1.30D, 1.50D, 2.00D};

    private static long pairProbes;
    private static long pairFlags;
    private static long pairSkips;
    private static long poseSafetyFlags;

    private static boolean ran;

    private record SpecEntry(String fieldName, LegacyBatchTrackSpec spec) {}

    private Dev38R1g1uFleetPoseSpacingHeadless() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String enabled = System.getenv(ENV);
        if (!"1".equals(enabled) && !"true".equalsIgnoreCase(enabled)) return;
        if (ran) return;
        ran = true;

        MinecraftServer server = event.getServer();
        List<String> report = new ArrayList<>();
        Path output = null;
        boolean complete = false;

        try {
            complete = run(server, report);
        } catch (Throwable t) {
            report.add("STATUS=FAIL");
            report.add("EXCEPTION=" + t.getClass().getName());
            report.add("MESSAGE=" + safe(t.getMessage()));
            t.printStackTrace();
        } finally {
            try {
                Path dir = Path.of("logs", "traincraft-track-debug");
                Files.createDirectories(dir);
                output = dir.resolve(
                        "fleet-pose-spacing-r1g1u-headless-"
                                + STAMP.format(LocalDateTime.now())
                                + ".txt")
                        .toAbsolutePath();
                Files.write(output, report, StandardCharsets.UTF_8);
            } catch (Throwable writeFailure) {
                writeFailure.printStackTrace();
                complete = false;
            }

            System.out.println(
                    "[TC-R1G1U-DEEP-SWEEP] "
                            + (complete ? "COMPLETE" : "FAILED")
                            + " report=" + String.valueOf(output));
            server.halt(false);
        }
    }

    private static boolean run(MinecraftServer server, List<String> report)
            throws Exception {

        report.add("TAFARICRAFT - TRAINS DEV38 r1g1u");
        report.add("FLEET POSE + COUPLER SPACING DEEP SWEEP");
        report.add("COMMANDS_REQUIRED=NO");
        report.add("EXPECTED_ROSTER=111");
        report.add("CLIENT_RENDER_PIXELS_AVAILABLE=NO");
        report.add("PURPOSE=measure path-derived rigid-body pose limits and physical coupling spacing before renderer patch");
        report.add("PAIR_TICKS=8");
        report.add("POSE_SAFETY_SHIFT_LIMIT=0.20");
        report.add("POSE_SAFETY_YAW_DELTA_LIMIT=20.0");
        report.add("");

        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = Math.max(
                level.getMinBuildHeight() + 20,
                Math.min(level.getMaxBuildHeight() - 40, spawn.getY() + 32));
        BlockPos root = new BlockPos(spawn.getX() + 160, y, spawn.getZ() + 160);

        forceArea(level, root, 5);

        List<EntityType<?>> stocks = discoverStocks(level, report);
        EntityType<?> geometryProbe = findStock(stocks, "locomotive_steam_small");
        if (geometryProbe == null && !stocks.isEmpty()) geometryProbe = stocks.get(0);
        EntityType<?> spacingLead = findStock(stocks, "locomotive_steam_small");
        if (geometryProbe == null) {
            report.add("STATUS=FAIL");
            report.add("REASON=no Traincraft AbstractMinecart types were discovered");
            return false;
        }

        report.add("DISCOVERED_MINECART_ROSTER=" + stocks.size());
        report.add("ROSTER_MATCH_111=" + (stocks.size() == 111));
        report.add("GEOMETRY_PROBE="
                + ForgeRegistries.ENTITY_TYPES.getKey(geometryProbe));
        report.add("SPACING_LEAD="
                + String.valueOf(spacingLead == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(spacingLead)));
        report.add("");

        int failures = 0;
        long fleetProbes = 0L;
        long geometryProbes = 0L;
        int standaloneCourses = 0;
        int straightCourses = 0;
        int mediumCourses = 0;
        int batchCourses = 0;

        // -------------------------------------------------------------
        // 1. Every standalone Traincraft rail block.
        // -------------------------------------------------------------
        report.add("################################################################");
        report.add("SECTION=STANDALONE_RAIL_BLOCKS");

        List<Block> standalone = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null || !"traincraft".equals(key.getNamespace())) continue;
            if (!(block instanceof BaseRailBlock)) continue;
            if (key.getPath().endsWith("_segment")) continue;
            standalone.add(block);
        }
        standalone.sort(Comparator.comparing(
                b -> String.valueOf(ForgeRegistries.BLOCKS.getKey(b))));

        for (Block block : standalone) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            List<RailShape> shapes = possibleShapes(block.defaultBlockState());
            if (shapes.isEmpty()) continue;

            // Geometry probe covers every allowed shape in both directions.
            for (RailShape shape : shapes) {
                clearPad(level, root, 6, 4);
                BlockPos rail = root;
                placeStandalone(level, rail, block, shape);
                float yaw = yawForShape(shape);

                geometryProbes += probe(
                        level, geometryProbe, key + "/" + shape,
                        "STANDALONE_GEOMETRY", rail, yaw,
                        GEOMETRY_SPACINGS, report);
                geometryProbes += probe(
                        level, geometryProbe, key + "/" + shape,
                        "STANDALONE_GEOMETRY_REVERSE", rail, yaw + 180.0F,
                        GEOMETRY_SPACINGS, report);
                standaloneCourses++;
            }

            // Deep fleet pass: every stock on every allowed standalone shape.
            for (RailShape fleetShape : shapes) {
                clearPad(level, root, 6, 4);
                placeStandalone(level, root, block, fleetShape);
                float yaw = yawForShape(fleetShape);

                for (EntityType<?> stock : stocks) {
                    fleetProbes += probe(
                            level, stock, key + "/" + fleetShape,
                            "STANDALONE_FLEET", root, yaw,
                            FLEET_SPACINGS, report);
                    fleetProbes += probe(
                            level, stock, key + "/" + fleetShape,
                            "STANDALONE_FLEET_REVERSE", root, yaw + 180.0F,
                            FLEET_SPACINGS, report);
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Dedicated medium/long/very-long straight assemblies.
        // -------------------------------------------------------------
        report.add("");
        report.add("################################################################");
        report.add("SECTION=STRAIGHT_ASSEMBLIES");

        int[] lengths = {3, 6, 12};
        String[] straightIds = {
                "track_medium_straight_segment",
                "track_long_straight_segment",
                "track_very_long_straight_segment"
        };

        for (int s = 0; s < lengths.length; s++) {
            Block b = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("traincraft", straightIds[s]));
            if (!(b instanceof LegacyStraightAssemblyRailBlock block)) {
                report.add("COURSE_MISSING id=" + straightIds[s]);
                failures++;
                continue;
            }

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                clearPad(level, root, 24, 6);
                buildStraightAssembly(level, root, facing, lengths[s], block);

                int part = lengths[s] / 2;
                BlockPos rail = root.relative(facing, part);
                RailShape shape = facing.getAxis() == Direction.Axis.X
                        ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
                float yaw = yawForShape(shape);

                geometryProbes += probe(
                        level, geometryProbe,
                        straightIds[s] + "/facing=" + facing,
                        "STRAIGHT_ASSEMBLY_GEOMETRY", rail, yaw,
                        GEOMETRY_SPACINGS, report);
                geometryProbes += probe(
                        level, geometryProbe,
                        straightIds[s] + "/facing=" + facing,
                        "STRAIGHT_ASSEMBLY_GEOMETRY_REVERSE", rail, yaw + 180.0F,
                        GEOMETRY_SPACINGS, report);
                straightCourses++;
            }

            // Full fleet on both world axes and every assembly part.
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST)) {
                clearPad(level, root, 24, 6);
                buildStraightAssembly(level, root, facing, lengths[s], block);
                RailShape shape = facing.getAxis() == Direction.Axis.X
                        ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
                float yaw = yawForShape(shape);

                for (int part = 0; part < lengths[s]; part++) {
                    BlockPos rail = root.relative(facing, part);
                    String course = straightIds[s] + "/facing=" + facing + "/part=" + part;
                    for (EntityType<?> stock : stocks) {
                        fleetProbes += probe(
                                level, stock, course,
                                "STRAIGHT_ASSEMBLY_FLEET", rail, yaw,
                                FLEET_SPACINGS, report);
                        fleetProbes += probe(
                                level, stock, course,
                                "STRAIGHT_ASSEMBLY_FLEET_REVERSE", rail, yaw + 180.0F,
                                FLEET_SPACINGS, report);
                    }
                }

                if (spacingLead != null) {
                    for (int part : transitionParts(lengths[s])) {
                        int mate = nearestStraightPartnerPart(part, lengths[s]);
                        if (mate < 0) continue;
                        BlockPos leadRail = root.relative(facing, part);
                        BlockPos followerRail = root.relative(facing, mate);
                        for (EntityType<?> stock : stocks) {
                            pairProbe(level, spacingLead, stock,
                                    straightIds[s] + "/facing=" + facing + "/part=" + part,
                                    leadRail, yaw, followerRail, yaw, false, report);
                            pairProbe(level, spacingLead, stock,
                                    straightIds[s] + "/facing=" + facing + "/part=" + part,
                                    leadRail, yaw + 180.0F, followerRail, yaw + 180.0F, true, report);
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Dedicated 5-part Medium Curve.
        // -------------------------------------------------------------
        report.add("");
        report.add("################################################################");
        report.add("SECTION=MEDIUM_CURVE");

        Block mb = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("traincraft", "track_medium_curve_segment"));
        if (mb instanceof LegacyMediumCurveRailBlock medium) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                clearPad(level, root, 16, 6);
                buildMediumCurve(level, root, facing, medium);

                for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
                    BlockPos rail = LegacyMediumCurveRailBlock.offsetForPart(
                            root, facing, part);
                    RailShape shape = LegacyMediumCurveRailBlock.shapeForPart(
                            facing, part);
                    float yaw = yawForShape(shape);

                    geometryProbes += probe(
                            level, geometryProbe,
                            "track_medium_curve/facing=" + facing + "/part=" + part,
                            "MEDIUM_GEOMETRY", rail, yaw,
                            GEOMETRY_SPACINGS, report);
                    geometryProbes += probe(
                            level, geometryProbe,
                            "track_medium_curve/facing=" + facing + "/part=" + part,
                            "MEDIUM_GEOMETRY_REVERSE", rail, yaw + 180.0F,
                            GEOMETRY_SPACINGS, report);
                    mediumCourses++;
                }
            }

            for (Direction facing : List.of(Direction.NORTH, Direction.EAST)) {
                clearPad(level, root, 16, 6);
                buildMediumCurve(level, root, facing, medium);

                for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
                    BlockPos rail = LegacyMediumCurveRailBlock.offsetForPart(
                            root, facing, part);
                    RailShape shape = LegacyMediumCurveRailBlock.shapeForPart(
                            facing, part);
                    float yaw = yawForShape(shape);
                    String course = "track_medium_curve/facing=" + facing + "/part=" + part;

                    for (EntityType<?> stock : stocks) {
                        fleetProbes += probe(
                                level, stock, course,
                                "MEDIUM_FLEET", rail, yaw,
                                FLEET_SPACINGS, report);
                        fleetProbes += probe(
                                level, stock, course,
                                "MEDIUM_FLEET_REVERSE", rail, yaw + 180.0F,
                                FLEET_SPACINGS, report);
                    }
                }

                if (spacingLead != null) {
                    for (int part : transitionParts(LegacyMediumCurveRailBlock.PART_COUNT)) {
                        int mate = nearestMediumPartnerPart(root, facing, part);
                        if (mate < 0) continue;
                        BlockPos leadRail = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
                        BlockPos followerRail = LegacyMediumCurveRailBlock.offsetForPart(root, facing, mate);
                        float leadYaw = yawForShape(LegacyMediumCurveRailBlock.shapeForPart(facing, part));
                        float followerYaw = yawForShape(LegacyMediumCurveRailBlock.shapeForPart(facing, mate));
                        for (EntityType<?> stock : stocks) {
                            pairProbe(level, spacingLead, stock,
                                    "track_medium_curve/facing=" + facing + "/part=" + part,
                                    leadRail, leadYaw, followerRail, followerYaw, false, report);
                            pairProbe(level, spacingLead, stock,
                                    "track_medium_curve/facing=" + facing + "/part=" + part,
                                    leadRail, leadYaw + 180.0F, followerRail, followerYaw + 180.0F, true, report);
                        }
                    }
                }
            }
        } else {
            report.add("COURSE_MISSING id=track_medium_curve_segment");
            failures++;
        }

        // -------------------------------------------------------------
        // 4. EVERY LegacyBatchTrackSpecs definition, reflectively.
        //    This automatically includes newly-added release specs too.
        // -------------------------------------------------------------
        report.add("");
        report.add("################################################################");
        report.add("SECTION=ALL_LEGACY_BATCH_SPECS");

        List<SpecEntry> specs = discoverSpecs();
        report.add("BATCH_SPEC_COUNT=" + specs.size());

        for (SpecEntry entry : specs) {
            LegacyBatchTrackSpec spec = entry.spec();
            String id = spec.id();
            Block raw = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("traincraft", id + "_segment"));

            if (!(raw instanceof AbstractLegacyBatchRailBlock block)) {
                report.add("COURSE_MISSING field=" + entry.fieldName()
                        + " id=" + id + " expectedBlock=" + id + "_segment");
                failures++;
                continue;
            }

            boolean[] routeStates =
                    (spec.dynamicRouting() || id.contains("switch"))
                            ? new boolean[]{false, true}
                            : new boolean[]{false};

            Set<Integer> samples = sampleParts(spec.partCount());

            // Exhaustive geometry/orientation matrix with one probe stock.
            for (boolean alternate : routeStates) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    clearPad(level, root, 80, 12);
                    buildBatch(level, root, facing, spec, block, alternate);

                    for (int part : samples) {
                        BlockPos rail = spec.offsetForPart(root, facing, part);
                        RailShape shape = spec.shapeForPart(
                                facing, part, alternate);
                        float yaw = yawForShape(shape);

                        String course = id
                                + "/field=" + entry.fieldName()
                                + "/alt=" + alternate
                                + "/facing=" + facing
                                + "/part=" + part;

                        geometryProbes += probe(
                                level, geometryProbe, course,
                                "BATCH_GEOMETRY", rail, yaw,
                                GEOMETRY_SPACINGS, report);
                        geometryProbes += probe(
                                level, geometryProbe, course,
                                "BATCH_GEOMETRY_REVERSE", rail, yaw + 180.0F,
                                GEOMETRY_SPACINGS, report);
                        batchCourses++;
                    }
                }
            }

            // Whole fleet on N/S-axis and E/W-axis orientations at entry,
            // quarter, middle, three-quarter and exit samples.
            for (boolean alternate : routeStates) {
                for (Direction facing : List.of(Direction.NORTH, Direction.EAST)) {
                    clearPad(level, root, 80, 12);
                    buildBatch(level, root, facing, spec, block, alternate);

                    for (int part : samples) {
                        BlockPos rail = spec.offsetForPart(root, facing, part);
                        RailShape shape = spec.shapeForPart(
                                facing, part, alternate);
                        float yaw = yawForShape(shape);

                        String course = id
                                + "/alt=" + alternate
                                + "/facing=" + facing
                                + "/part=" + part;

                        for (EntityType<?> stock : stocks) {
                            fleetProbes += probe(
                                    level, stock, course,
                                    "BATCH_FLEET", rail, yaw,
                                    FLEET_SPACINGS, report);
                            fleetProbes += probe(
                                    level, stock, course,
                                    "BATCH_FLEET_REVERSE", rail, yaw + 180.0F,
                                    FLEET_SPACINGS, report);
                        }
                    }

                    if (spacingLead != null) {
                        for (int part : transitionParts(spec.partCount())) {
                            int mate = nearestBatchPartnerPart(root, facing, spec, part);
                            if (mate < 0) continue;
                            BlockPos leadRail = spec.offsetForPart(root, facing, part);
                            BlockPos followerRail = spec.offsetForPart(root, facing, mate);
                            float leadYaw = yawForShape(spec.shapeForPart(facing, part, alternate));
                            float followerYaw = yawForShape(spec.shapeForPart(facing, mate, alternate));
                            String pairCourse = id
                                    + "/alt=" + alternate
                                    + "/facing=" + facing
                                    + "/part=" + part
                                    + "/mate=" + mate;
                            for (EntityType<?> stock : stocks) {
                                pairProbe(level, spacingLead, stock, pairCourse,
                                        leadRail, leadYaw, followerRail, followerYaw, false, report);
                                pairProbe(level, spacingLead, stock, pairCourse,
                                        leadRail, leadYaw + 180.0F, followerRail, followerYaw + 180.0F, true, report);
                            }
                        }
                    }
                }
            }
        }

        report.add("");
        report.add("################################################################");
        report.add("SUMMARY");
        report.add("EXPECTED_ROSTER=111");
        report.add("DISCOVERED_MINECART_ROSTER=" + stocks.size());
        report.add("ROSTER_MATCH_111=" + (stocks.size() == 111));
        report.add("STANDALONE_RAIL_BLOCKS=" + standalone.size());
        report.add("BATCH_SPEC_COUNT=" + specs.size());
        report.add("STANDALONE_GEOMETRY_COURSES=" + standaloneCourses);
        report.add("STRAIGHT_GEOMETRY_COURSES=" + straightCourses);
        report.add("MEDIUM_GEOMETRY_COURSES=" + mediumCourses);
        report.add("BATCH_GEOMETRY_COURSES=" + batchCourses);
        report.add("GEOMETRY_PROBES=" + geometryProbes);
        report.add("FLEET_PROBES=" + fleetProbes);
        report.add("PAIR_SPACING_PROBES=" + pairProbes);
        report.add("PAIR_SPACING_FLAGS=" + pairFlags);
        report.add("PAIR_SPACING_SKIPS=" + pairSkips);
        report.add("POSE_SAFETY_FLAGS=" + poseSafetyFlags);
        report.add("COURSE_FAILURES=" + failures);
        report.add("CLIENT_RENDER_LIMIT=headless measures path pose and physical spacing; pixels/F5 HUD still require one client check");
        report.add("STATUS=COMPLETE");

        return !stocks.isEmpty() && !specs.isEmpty();
    }

    private static List<EntityType<?>> discoverStocks(
            ServerLevel level, List<String> report) {

        List<EntityType<?>> out = new ArrayList<>();
        List<EntityType<?>> all =
                new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues());
        all.sort(Comparator.comparing(
                type -> String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type))));

        report.add("################################################################");
        report.add("SECTION=ROSTER_DISCOVERY");

        for (EntityType<?> type : all) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !"traincraft".equals(key.getNamespace())) continue;

            Entity entity = null;
            try {
                entity = type.create(level);
                if (entity instanceof AbstractMinecart) {
                    out.add(type);
                    report.add("ROSTER id=" + key
                            + " class=" + entity.getClass().getName()
                            + " bbWidth=" + f(entity.getBbWidth())
                            + " bbHeight=" + f(entity.getBbHeight()));
                } else if (entity != null) {
                    report.add("SKIP_NON_MINECART id=" + key
                            + " class=" + entity.getClass().getName());
                } else {
                    report.add("CREATE_NULL id=" + key);
                }
            } catch (Throwable t) {
                report.add("CREATE_FAIL id=" + key
                        + " exception=" + t.getClass().getSimpleName()
                        + " message=" + safe(t.getMessage()));
            } finally {
                if (entity != null && !entity.isRemoved()) entity.discard();
            }
        }
        return out;
    }

    private static EntityType<?> findStock(
            List<EntityType<?>> stocks, String path) {
        for (EntityType<?> type : stocks) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key != null && path.equals(key.getPath())) return type;
        }
        return null;
    }

    private static List<SpecEntry> discoverSpecs() throws IllegalAccessException {
        List<SpecEntry> out = new ArrayList<>();
        for (Field field : LegacyBatchTrackSpecs.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!LegacyBatchTrackSpec.class.isAssignableFrom(field.getType())) continue;
            Object value = field.get(null);
            if (value instanceof LegacyBatchTrackSpec spec) {
                out.add(new SpecEntry(field.getName(), spec));
            }
        }
        out.sort(Comparator.comparing(e -> e.spec().id()));
        return out;
    }

    private static long probe(
            ServerLevel level,
            EntityType<?> type,
            String course,
            String mode,
            BlockPos rail,
            float yaw,
            double[] spacings,
            List<String> report) {

        ResourceLocation stockId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        Entity raw = null;
        try {
            raw = type.create(level);
            if (!(raw instanceof AbstractMinecart cart)) {
                report.add("PROBE_SKIP stock=" + stockId
                        + " course=" + course + " mode=" + mode
                        + " reason=not_minecart");
                return 0L;
            }

            double x = rail.getX() + 0.5D;
            double y = rail.getY() + RAIL_Y;
            double z = rail.getZ() + 0.5D;

            cart.moveTo(x, y, z, yaw, 0.0F);
            cart.setYRot(yaw);
            cart.setXRot(0.0F);
            cart.setDeltaMovement(Vec3.ZERO);

            Vec3 before = cart.position();
            if (!level.addFreshEntity(cart)) {
                report.add("PROBE_FAIL stock=" + stockId
                        + " course=" + course + " mode=" + mode
                        + " reason=addFreshEntity_false");
                return 1L;
            }

            cart.tick();

            Vec3 after = cart.position();
            double tickDx = after.x - before.x;
            double tickDz = after.z - before.z;
            double tickHorizontal = Math.hypot(tickDx, tickDz);
            float bodyYaw = renderYaw(cart);

            StringBuilder line = new StringBuilder();
            line.append("PROBE stock=").append(stockId)
                    .append(" course=").append(course)
                    .append(" mode=").append(mode)
                    .append(" rail=").append(rail)
                    .append(" spawnYaw=").append(f(yaw))
                    .append(" bodyYaw=").append(f(bodyYaw))
                    .append(" tickDx=").append(f(tickDx))
                    .append(" tickDz=").append(f(tickDz))
                    .append(" tickHorizontal=").append(f(tickHorizontal));

            for (double spacing : spacings) {
                LegacyContinuousTrackPath.VirtualBogiePose pose =
                        LegacyContinuousTrackPath.virtualBogiePose(
                                cart, bodyYaw, spacing);

                if (pose == null) {
                    line.append(" pose[").append(f(spacing)).append("]=null");
                    continue;
                }

                double dx = pose.center().x - cart.getX();
                double dz = pose.center().z - cart.getZ();
                double shift = Math.hypot(dx, dz);
                double yawDelta = Math.abs(net.minecraft.util.Mth.wrapDegrees(pose.yaw() - bodyYaw));
                line.append(" pose[").append(f(spacing)).append("]=")
                        .append("shift:").append(f(shift))
                        .append(",dx:").append(f(dx))
                        .append(",dz:").append(f(dz))
                        .append(",yaw:").append(f(pose.yaw()))
                        .append(",yawDelta:").append(f(yawDelta))
                        .append(",pitch:").append(f(pose.pitch()));

                if (shift > 0.20D || yawDelta > 20.0D) {
                    poseSafetyFlags++;
                    line.append(",unsafe:true");
                }
            }

            if (tickHorizontal > 0.10D) {
                line.append(" FLAG=TICK_HORIZONTAL_GT_0.10");
            }

            report.add(line.toString());
            return 1L;
        } catch (Throwable t) {
            report.add("PROBE_EXCEPTION stock=" + stockId
                    + " course=" + course + " mode=" + mode
                    + " exception=" + t.getClass().getSimpleName()
                    + " message=" + safe(t.getMessage()));
            return 1L;
        } finally {
            if (raw != null && !raw.isRemoved()) raw.discard();
        }
    }

    private static void pairProbe(
            ServerLevel level,
            EntityType<?> leaderType,
            EntityType<?> followerType,
            String course,
            BlockPos leaderRail,
            float leaderYaw,
            BlockPos followerRail,
            float followerYaw,
            boolean reverse,
            List<String> report) {

        pairProbes++;
        ResourceLocation leaderId = ForgeRegistries.ENTITY_TYPES.getKey(leaderType);
        ResourceLocation followerId = ForgeRegistries.ENTITY_TYPES.getKey(followerType);
        Entity leaderRaw = null;
        Entity followerRaw = null;

        try {
            leaderRaw = leaderType.create(level);
            followerRaw = followerType.create(level);

            if (!(leaderRaw instanceof AbstractMinecart leaderCart)
                    || !(followerRaw instanceof AbstractMinecart followerCart)
                    || !(leaderRaw instanceof CoupleableRollingStock leaderStock)
                    || !(followerRaw instanceof CoupleableRollingStock followerStock)) {
                pairSkips++;
                report.add("PAIR_SKIP lead=" + leaderId + " follower=" + followerId
                        + " course=" + course + " reason=not_coupleable_minecart");
                return;
            }

            leaderCart.moveTo(
                    leaderRail.getX() + 0.5D,
                    leaderRail.getY() + RAIL_Y,
                    leaderRail.getZ() + 0.5D,
                    leaderYaw, 0.0F);
            followerCart.moveTo(
                    followerRail.getX() + 0.5D,
                    followerRail.getY() + RAIL_Y,
                    followerRail.getZ() + 0.5D,
                    followerYaw, 0.0F);
            leaderCart.setYRot(leaderYaw);
            followerCart.setYRot(followerYaw);
            leaderCart.setXRot(0.0F);
            followerCart.setXRot(0.0F);

            if (!level.addFreshEntity(leaderCart) || !level.addFreshEntity(followerCart)) {
                pairFlags++;
                report.add("PAIR_FLAG lead=" + leaderId + " follower=" + followerId
                        + " course=" + course + " reason=addFreshEntity_false");
                return;
            }

            CouplingManager.cleanupStaleLinks(leaderRaw, leaderStock);
            CouplingManager.cleanupStaleLinks(followerRaw, followerStock);

            if (!CouplingManager.isSupportedPair(leaderRaw, followerRaw)) {
                pairSkips++;
                report.add("PAIR_SKIP lead=" + leaderId + " follower=" + followerId
                        + " course=" + course + " reason=unsupported_pair");
                return;
            }

            if (!CouplingManager.canLink(leaderRaw, leaderStock, followerRaw, followerStock)) {
                pairSkips++;
                report.add("PAIR_SKIP lead=" + leaderId + " follower=" + followerId
                        + " course=" + course + " reason=canLink_false"
                        + " initialCenter=" + f(leaderRaw.distanceTo(followerRaw)));
                return;
            }

            if (!CouplingManager.link(leaderRaw, leaderStock, followerRaw, followerStock)) {
                pairFlags++;
                report.add("PAIR_FLAG lead=" + leaderId + " follower=" + followerId
                        + " course=" + course + " reason=link_false");
                return;
            }

            double initialCenter = leaderRaw.distanceTo(followerRaw);
            RailShape leadShape = railShapeAt(level, leaderRail);
            RailShape followerShape = railShapeAt(level, followerRail);
            boolean curveBand = isCurveShape(leadShape) || isCurveShape(followerShape);

            double touchTarget = invokeDoubleOneEntity(
                    followerRaw, "getCouplerTouchTargetDistance", leaderRaw);
            double minBand = invokeDoubleOneEntity(
                    followerRaw,
                    curveBand ? "getPrimaryCurveCouplingMinDistance"
                            : "getPrimaryCouplingMinDistance",
                    leaderRaw);
            double maxBand = invokeDoubleOneEntity(
                    followerRaw,
                    curveBand ? "getPrimaryCurveCouplingMaxDistance"
                            : "getPrimaryCouplingMaxDistance",
                    leaderRaw);

            leaderCart.setDeltaMovement(motionForYaw(leaderYaw, 0.22D));
            followerCart.setDeltaMovement(motionForYaw(followerYaw, 0.22D));

            for (int i = 0; i < 8; i++) {
                leaderCart.tick();
                followerCart.tick();
            }

            double finalCenter = leaderRaw.distanceTo(followerRaw);
            double delta = finalCenter - initialCenter;
            double nubGap = Double.isFinite(touchTarget)
                    ? finalCenter - touchTarget : Double.NaN;
            boolean primaryOnFollower = leaderRaw.getUUID().equals(
                    followerStock.getCoupledRollingStockUuid());
            boolean secondaryOnFollower = leaderRaw.getUUID().equals(
                    followerStock.getSecondaryCoupledRollingStockUuid());

            boolean flagged = false;
            String reason = "OK";
            if (Double.isFinite(minBand) && Double.isFinite(maxBand)) {
                if (finalCenter < minBand - 0.20D) {
                    flagged = true;
                    reason = "COMPRESSED_BELOW_BAND";
                } else if (finalCenter > maxBand + 0.25D) {
                    flagged = true;
                    reason = "STRETCHED_ABOVE_BAND";
                }
            }
            if (Math.abs(delta) > 1.00D) {
                flagged = true;
                reason = "UNSTABLE_CENTER_DELTA";
            }
            if (flagged) pairFlags++;

            report.add((flagged ? "PAIR_FLAG" : "PAIR")
                    + " lead=" + leaderId
                    + " follower=" + followerId
                    + " course=" + course
                    + " reverse=" + reverse
                    + " leadShape=" + leadShape
                    + " followerShape=" + followerShape
                    + " curveBand=" + curveBand
                    + " initialCenter=" + f(initialCenter)
                    + " finalCenter=" + f(finalCenter)
                    + " centerDelta=" + f(delta)
                    + " touchTarget=" + fOrNaN(touchTarget)
                    + " nubGap=" + fOrNaN(nubGap)
                    + " minBand=" + fOrNaN(minBand)
                    + " maxBand=" + fOrNaN(maxBand)
                    + " followerPrimary=" + primaryOnFollower
                    + " followerSecondary=" + secondaryOnFollower
                    + " reason=" + reason);

            try {
                CouplingManager.unlinkPair(
                        leaderRaw, leaderStock, followerRaw, followerStock);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            pairFlags++;
            report.add("PAIR_EXCEPTION lead=" + leaderId
                    + " follower=" + followerId
                    + " course=" + course
                    + " reverse=" + reverse
                    + " exception=" + t.getClass().getSimpleName()
                    + " message=" + safe(t.getMessage()));
        } finally {
            if (leaderRaw != null && !leaderRaw.isRemoved()) leaderRaw.discard();
            if (followerRaw != null && !followerRaw.isRemoved()) followerRaw.discard();
        }
    }

    private static double invokeDoubleOneEntity(
            Entity target, String methodName, Entity argument) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName, Entity.class);
                method.setAccessible(true);
                Object value = method.invoke(target, argument);
                if (value instanceof Number number) return number.doubleValue();
                return Double.NaN;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static RailShape railShapeAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Property<?> property = state.getBlock().getStateDefinition().getProperty("shape");
        if (property == null) return null;
        Comparable<?> value = state.getValue((Property) property);
        return value instanceof RailShape shape ? shape : null;
    }

    private static boolean isCurveShape(RailShape shape) {
        return shape == RailShape.NORTH_EAST
                || shape == RailShape.NORTH_WEST
                || shape == RailShape.SOUTH_EAST
                || shape == RailShape.SOUTH_WEST;
    }

    private static Vec3 motionForYaw(float yaw, double speed) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians) * speed, 0.0D,
                Math.cos(radians) * speed);
    }

    private static Set<Integer> transitionParts(int count) {
        Set<Integer> out = new LinkedHashSet<>();
        if (count <= 0) return out;
        out.add(0);
        out.add(Math.max(0, count / 2));
        out.add(count - 1);
        return out;
    }

    private static int nearestStraightPartnerPart(int part, int count) {
        if (count < 2) return -1;
        int best = -1;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (i == part) continue;
            int d = Math.abs(Math.abs(i - part) - 2);
            if (d < bestDelta) {
                bestDelta = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestMediumPartnerPart(
            BlockPos root, Direction facing, int part) {
        BlockPos source = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
        int best = -1;
        double bestError = Double.POSITIVE_INFINITY;
        for (int i = 0; i < LegacyMediumCurveRailBlock.PART_COUNT; i++) {
            if (i == part) continue;
            BlockPos p = LegacyMediumCurveRailBlock.offsetForPart(root, facing, i);
            double distance = blockDistance(source, p);
            double error = Math.abs(distance - 2.0D);
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return best;
    }

    private static int nearestBatchPartnerPart(
            BlockPos root, Direction facing, LegacyBatchTrackSpec spec, int part) {
        BlockPos source = spec.offsetForPart(root, facing, part);
        int best = -1;
        double bestError = Double.POSITIVE_INFINITY;
        for (int i = 0; i < spec.partCount(); i++) {
            if (i == part) continue;
            BlockPos p = spec.offsetForPart(root, facing, i);
            double distance = blockDistance(source, p);
            if (distance > 6.0D) continue;
            double error = Math.abs(distance - 2.0D);
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return best;
    }

    private static double blockDistance(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() - b.getX();
        double dy = (double) a.getY() - b.getY();
        double dz = (double) a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String fOrNaN(double value) {
        return Double.isFinite(value) ? f(value) : "NaN";
    }

    private static void placeStandalone(
            ServerLevel level, BlockPos rail, Block block, RailShape shape) {

        level.setBlock(rail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        BlockState state = withShape(block.defaultBlockState(), shape);
        level.setBlock(rail, state, 2);
    }

    private static void buildStraightAssembly(
            ServerLevel level,
            BlockPos root,
            Direction facing,
            int length,
            LegacyStraightAssemblyRailBlock block) {

        RailShape shape = facing.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

        for (int i = 0; i < length; i++) {
            BlockPos rail = root.relative(facing, i);
            level.setBlock(rail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            BlockState state = block.defaultBlockState()
                    .setValue(RailBlock.SHAPE, shape)
                    .setValue(LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING, facing)
                    .setValue(LegacyStraightAssemblyRailBlock.PART, i);
            level.setBlock(rail, state, 2);
        }
    }

    private static void buildMediumCurve(
            ServerLevel level,
            BlockPos root,
            Direction facing,
            LegacyMediumCurveRailBlock block) {

        for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
            BlockPos rail = LegacyMediumCurveRailBlock.offsetForPart(
                    root, facing, part);
            level.setBlock(rail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);

            BlockState state = block.defaultBlockState()
                    .setValue(
                            RailBlock.SHAPE,
                            LegacyMediumCurveRailBlock.shapeForPart(facing, part))
                    .setValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING, facing)
                    .setValue(LegacyMediumCurveRailBlock.PART, part);
            level.setBlock(rail, state, 2);
        }
    }

    private static void buildBatch(
            ServerLevel level,
            BlockPos root,
            Direction facing,
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block,
            boolean alternate) {

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos support = spec.supportForPart(root, facing, part);
            level.setBlock(support, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        }

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, facing, part);
            level.setBlock(
                    rail,
                    block.stateForPart(facing, part, alternate),
                    2);
        }

        // Lock canonical route state after placement/neighbor negotiation.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, facing, part);
            if (level.getBlockState(rail).getBlock() == block) {
                level.setBlock(
                        rail,
                        block.stateForPart(facing, part, alternate),
                        2);
            }
        }
    }

    private static Set<Integer> sampleParts(int count) {
        Set<Integer> out = new LinkedHashSet<>();
        if (count <= 0) return out;
        int last = count - 1;
        out.add(0);
        out.add(last / 4);
        out.add(last / 2);
        out.add((last * 3) / 4);
        out.add(last);
        return out;
    }

    private static List<RailShape> possibleShapes(BlockState state) {
        Property<?> property = state.getBlock()
                .getStateDefinition().getProperty("shape");
        if (property == null) return List.of();

        List<RailShape> out = new ArrayList<>();
        for (Comparable<?> value : property.getPossibleValues()) {
            if (value instanceof RailShape shape) out.add(shape);
        }
        out.sort(Comparator.comparing(Enum::name));
        return out;
    }

    private static RailShape representativeShape(List<RailShape> shapes) {
        if (shapes.contains(RailShape.EAST_WEST)) return RailShape.EAST_WEST;
        if (shapes.contains(RailShape.NORTH_SOUTH)) return RailShape.NORTH_SOUTH;
        return shapes.get(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withShape(BlockState state, RailShape shape) {
        Property property = state.getBlock()
                .getStateDefinition().getProperty("shape");
        if (property == null || !property.getPossibleValues().contains(shape)) {
            return state;
        }
        return state.setValue(property, shape);
    }

    private static float yawForShape(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0.0F;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> -90.0F;
            case NORTH_EAST -> -45.0F;
            case NORTH_WEST -> 45.0F;
            case SOUTH_EAST -> -135.0F;
            case SOUTH_WEST -> 135.0F;
        };
    }

    private static float renderYaw(AbstractMinecart cart) {
        try {
            Method method = cart.getClass().getMethod(
                    "getRenderTrainFacingYaw", float.class);
            Object value = method.invoke(cart, 1.0F);
            if (value instanceof Number number) return number.floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return cart.getYRot();
    }

    private static void clearPad(
            ServerLevel level, BlockPos root, int horizontal, int vertical) {
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dz = -horizontal; dz <= horizontal; dz++) {
                for (int dy = -2; dy <= vertical; dy++) {
                    BlockPos p = root.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.getBlock() instanceof BaseRailBlock
                            || state.getBlock() == Blocks.SMOOTH_STONE) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void forceArea(
            ServerLevel level, BlockPos center, int radiusChunks) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String safe(String text) {
        return text == null
                ? "<null>"
                : text.replace('\n', ' ').replace('\r', ' ');
    }
}
