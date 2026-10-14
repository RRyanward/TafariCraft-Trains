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

/**
 * TC_DEV38_R1G1S1_RELEASE_ALIGNMENT_MATRIX
 *
 * Command-free dedicated-server pre-release matrix.
 *
 * The matrix deliberately separates two questions:
 *  1. server/path geometry: tested here;
 *  2. rendered model origin/renderer transforms: emitted by the installer as
 *     static source reports because a dedicated server cannot load client code.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1g1s1ReleaseAlignmentHeadless {
    private static final String ENV = "TC_R1G1S1_RELEASE_SWEEP";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final double RAIL_Y = 0.0625D;
    private static final double[] GEOMETRY_SPACINGS =
            {0.30D, 0.60D, 1.00D, 1.30D, 1.50D, 2.00D};
    private static final double[] FLEET_SPACINGS = {0.60D, 1.50D};

    private static boolean ran;

    private record SpecEntry(String fieldName, LegacyBatchTrackSpec spec) {}

    private Dev38R1g1s1ReleaseAlignmentHeadless() {}

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
                        "release-alignment-r1g1s1-headless-"
                                + STAMP.format(LocalDateTime.now())
                                + ".txt")
                        .toAbsolutePath();
                Files.write(output, report, StandardCharsets.UTF_8);
            } catch (Throwable writeFailure) {
                writeFailure.printStackTrace();
                complete = false;
            }

            System.out.println(
                    "[TC-R1G1S1-RELEASE-MATRIX] "
                            + (complete ? "COMPLETE" : "FAILED")
                            + " report=" + String.valueOf(output));
            server.halt(false);
        }
    }

    private static boolean run(MinecraftServer server, List<String> report)
            throws Exception {

        report.add("TAFARICRAFT - TRAINS DEV38 r1g1s1");
        report.add("RELEASE ALIGNMENT MATRIX");
        report.add("COMMANDS_REQUIRED=NO");
        report.add("EXPECTED_ROSTER=111");
        report.add("CLIENT_RENDER_PIXELS_AVAILABLE=NO");
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
        if (geometryProbe == null) {
            report.add("STATUS=FAIL");
            report.add("REASON=no Traincraft AbstractMinecart types were discovered");
            return false;
        }

        report.add("DISCOVERED_MINECART_ROSTER=" + stocks.size());
        report.add("ROSTER_MATCH_111=" + (stocks.size() == 111));
        report.add("GEOMETRY_PROBE="
                + ForgeRegistries.ENTITY_TYPES.getKey(geometryProbe));
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

            // Whole fleet uses one representative shape per standalone rail
            // so every rail BLOCK and every STOCK are crossed at least once.
            RailShape representative = representativeShape(shapes);
            clearPad(level, root, 6, 4);
            placeStandalone(level, root, block, representative);
            float yaw = yawForShape(representative);

            for (EntityType<?> stock : stocks) {
                fleetProbes += probe(
                        level, stock, key + "/" + representative,
                        "STANDALONE_FLEET", root, yaw,
                        FLEET_SPACINGS, report);
                fleetProbes += probe(
                        level, stock, key + "/" + representative,
                        "STANDALONE_FLEET_REVERSE", root, yaw + 180.0F,
                        FLEET_SPACINGS, report);
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

            // Full fleet on both world axes.
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST)) {
                clearPad(level, root, 24, 6);
                buildStraightAssembly(level, root, facing, lengths[s], block);
                int part = lengths[s] / 2;
                BlockPos rail = root.relative(facing, part);
                RailShape shape = facing.getAxis() == Direction.Axis.X
                        ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
                float yaw = yawForShape(shape);

                for (EntityType<?> stock : stocks) {
                    fleetProbes += probe(
                            level, stock,
                            straightIds[s] + "/facing=" + facing,
                            "STRAIGHT_ASSEMBLY_FLEET", rail, yaw,
                            FLEET_SPACINGS, report);
                    fleetProbes += probe(
                            level, stock,
                            straightIds[s] + "/facing=" + facing,
                            "STRAIGHT_ASSEMBLY_FLEET_REVERSE", rail, yaw + 180.0F,
                            FLEET_SPACINGS, report);
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
                int part = LegacyMediumCurveRailBlock.PART_COUNT / 2;
                BlockPos rail = LegacyMediumCurveRailBlock.offsetForPart(
                        root, facing, part);
                RailShape shape = LegacyMediumCurveRailBlock.shapeForPart(
                        facing, part);
                float yaw = yawForShape(shape);

                for (EntityType<?> stock : stocks) {
                    fleetProbes += probe(
                            level, stock,
                            "track_medium_curve/facing=" + facing,
                            "MEDIUM_FLEET", rail, yaw,
                            FLEET_SPACINGS, report);
                    fleetProbes += probe(
                            level, stock,
                            "track_medium_curve/facing=" + facing,
                            "MEDIUM_FLEET_REVERSE", rail, yaw + 180.0F,
                            FLEET_SPACINGS, report);
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

            // Whole fleet on N/S-axis and E/W-axis orientations.
            for (boolean alternate : routeStates) {
                for (Direction facing : List.of(Direction.NORTH, Direction.EAST)) {
                    clearPad(level, root, 80, 12);
                    buildBatch(level, root, facing, spec, block, alternate);

                    int part = Math.max(0, spec.partCount() / 2);
                    BlockPos rail = spec.offsetForPart(root, facing, part);
                    RailShape shape = spec.shapeForPart(
                            facing, part, alternate);
                    float yaw = yawForShape(shape);

                    String course = id
                            + "/alt=" + alternate
                            + "/facing=" + facing
                            + "/midPart=" + part;

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
        report.add("COURSE_FAILURES=" + failures);
        report.add("CLIENT_RENDER_LIMIT=visual/model-origin offsets require companion renderer report");
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
                line.append(" pose[").append(f(spacing)).append("]=")
                        .append("shift:").append(f(Math.hypot(dx, dz)))
                        .append(",dx:").append(f(dx))
                        .append(",dz:").append(f(dz))
                        .append(",yaw:").append(f(pose.yaw()))
                        .append(",pitch:").append(f(pose.pitch()));
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
