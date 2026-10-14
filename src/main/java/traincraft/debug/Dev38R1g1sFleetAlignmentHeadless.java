package traincraft.debug;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

/**
 * TC_DEV38_R1G1S_AUTO_HEADLESS_FLEET_ALIGNMENT_SWEEP
 *
 * Command-free dedicated-server diagnostic. Enabled only by
 * TC_R1G1S_FLEET_HEADLESS=1. It does not register a command.
 *
 * IMPORTANT: a dedicated server cannot execute client renderers. This sweep
 * measures entity/path behavior and the read-only virtual-bogie geometry for
 * every registered Traincraft minecart. The installer writes a separate static
 * client-renderer source report to cover renderer transforms/model origins.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1g1sFleetAlignmentHeadless {
    private static final String ENV = "TC_R1G1S_FLEET_HEADLESS";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final double RAIL_Y = 0.0625D;
    private static final double[] SPACINGS =
            {0.30D, 0.60D, 1.00D, 1.30D, 1.50D, 2.00D};

    private static boolean ran;

    private Dev38R1g1sFleetAlignmentHeadless() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String enabled = System.getenv(ENV);
        if (!"1".equals(enabled) && !"true".equalsIgnoreCase(enabled)) {
            return;
        }
        if (ran) return;
        ran = true;

        MinecraftServer server = event.getServer();
        List<String> report = new ArrayList<>();
        Path output = null;
        boolean pass = false;

        try {
            pass = run(server, report);
        } catch (Throwable t) {
            report.add("STATUS=FAIL");
            report.add("EXCEPTION=" + t.getClass().getName());
            report.add("MESSAGE=" + String.valueOf(t.getMessage()));
            t.printStackTrace();
        } finally {
            try {
                Path dir = Path.of("logs", "traincraft-track-debug");
                Files.createDirectories(dir);
                output = dir.resolve(
                        "fleet-alignment-r1g1s-headless-"
                                + STAMP.format(LocalDateTime.now())
                                + ".txt")
                        .toAbsolutePath();
                Files.write(output, report, StandardCharsets.UTF_8);
            } catch (Throwable writeFailure) {
                writeFailure.printStackTrace();
                pass = false;
            }

            System.out.println(
                    "[TC-R1G1S-FLEET-ALIGNMENT] "
                            + (pass ? "COMPLETE" : "FAILED")
                            + " report=" + String.valueOf(output));
            server.halt(false);
        }
    }

    private static boolean run(MinecraftServer server, List<String> report)
            throws Exception {
        report.add("TAFARICRAFT - TRAINS DEV38 r1g1s");
        report.add("AUTO HEADLESS FLEET ALIGNMENT SWEEP");
        report.add("COMMANDS_REQUIRED=NO");
        report.add("CLIENT_RENDERING_AVAILABLE=NO");
        report.add("PURPOSE=separate server/path alignment from renderer/model-origin alignment");
        report.add("");

        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();

        int y = Math.max(
                level.getMinBuildHeight() + 16,
                Math.min(level.getMaxBuildHeight() - 20, spawn.getY() + 24));

        BlockPos curveRoot =
                new BlockPos(spawn.getX() + 128, y, spawn.getZ() + 128);
        BlockPos straightCenter = curveRoot.offset(-32, 0, 0);

        forceArea(level, curveRoot, 4);
        forceArea(level, straightCenter, 2);

        buildStraight(level, straightCenter);
        LegacyBatchTrackSpec curveSpec = LegacyBatchTrackSpecs.CURVE_BIG;
        AbstractLegacyBatchRailBlock curveBlock =
                resolveBatchBlock("track_curve_big_segment");
        if (curveBlock == null) {
            throw new IllegalStateException(
                    "traincraft:track_curve_big_segment is not an AbstractLegacyBatchRailBlock");
        }
        buildCurve(level, curveRoot, curveSpec, curveBlock);

        report.add("COURSE_STRAIGHT_CENTER=" + straightCenter);
        report.add("COURSE_CURVE_ROOT=" + curveRoot);
        report.add("COURSE_CURVE_ID=" + curveSpec.id());
        report.add("COURSE_CURVE_PARTS=" + curveSpec.partCount());
        report.add("");

        List<EntityType<?>> types =
                new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues());
        types.sort(Comparator.comparing(
                type -> String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type))));

        int created = 0;
        int minecarts = 0;
        int failures = 0;
        int poseSamples = 0;

        Set<Integer> curveSamples = new LinkedHashSet<>();
        int last = Math.max(0, curveSpec.partCount() - 1);
        curveSamples.add(0);
        curveSamples.add(last / 4);
        curveSamples.add(last / 2);
        curveSamples.add((last * 3) / 4);
        curveSamples.add(last);

        for (EntityType<?> type : types) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !"traincraft".equals(key.getNamespace())) {
                continue;
            }

            Entity raw;
            try {
                raw = type.create(level);
            } catch (Throwable t) {
                report.add("CREATE_FAIL id=" + key
                        + " exception=" + t.getClass().getSimpleName()
                        + " message=" + safe(t.getMessage()));
                failures++;
                continue;
            }

            if (raw == null) {
                report.add("CREATE_NULL id=" + key);
                failures++;
                continue;
            }
            created++;

            if (!(raw instanceof AbstractMinecart cart)) {
                report.add("SKIP_NON_MINECART id=" + key
                        + " class=" + raw.getClass().getName());
                raw.discard();
                continue;
            }
            minecarts++;

            report.add("================================================================");
            report.add("STOCK id=" + key
                    + " class=" + cart.getClass().getName()
                    + " bbWidth=" + f(cart.getBbWidth())
                    + " bbHeight=" + f(cart.getBbHeight()));

            try {
                probeStraight(level, cart, straightCenter, report);
            } catch (Throwable t) {
                report.add("STRAIGHT_PROBE_FAIL exception="
                        + t.getClass().getSimpleName()
                        + " message=" + safe(t.getMessage()));
                failures++;
            } finally {
                if (!cart.isRemoved()) {
                    cart.discard();
                }
            }

            for (int part : curveSamples) {
                Entity nextRaw;
                try {
                    nextRaw = type.create(level);
                } catch (Throwable t) {
                    report.add("CURVE_CREATE_FAIL part=" + part
                            + " exception=" + t.getClass().getSimpleName());
                    failures++;
                    continue;
                }
                if (!(nextRaw instanceof AbstractMinecart probe)) {
                    if (nextRaw != null) nextRaw.discard();
                    continue;
                }

                try {
                    poseSamples += probeCurve(
                            level, probe, curveRoot, curveSpec, part, report);
                } catch (Throwable t) {
                    report.add("CURVE_PROBE_FAIL part=" + part
                            + " exception=" + t.getClass().getSimpleName()
                            + " message=" + safe(t.getMessage()));
                    failures++;
                } finally {
                    if (!probe.isRemoved()) {
                        probe.discard();
                    }
                }
            }
        }

        report.add("");
        report.add("================================================================");
        report.add("SUMMARY");
        report.add("TRAINCRAFT_ENTITY_TYPES_CREATED=" + created);
        report.add("TRAINCRAFT_MINECART_TYPES_SWEPT=" + minecarts);
        report.add("VIRTUAL_BOGIE_SAMPLES=" + poseSamples);
        report.add("PROBE_FAILURES=" + failures);
        report.add("HEADLESS_VISUAL_LIMIT=renderer pixels cannot be observed on dedicated server");
        report.add("NEXT_INPUT=combine this report with DEV38-r1g1s-Renderer-Static-Sweep.txt");

        boolean pass = minecarts > 0 && poseSamples > 0;
        report.add("STATUS=" + (pass ? "COMPLETE" : "FAIL"));
        return pass;
    }

    private static void buildStraight(ServerLevel level, BlockPos center) {
        Block track = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("traincraft", "track_normal"));
        if (!(track instanceof BaseRailBlock)) {
            track = Blocks.RAIL;
        }

        for (int dx = -12; dx <= 12; dx++) {
            BlockPos rail = center.offset(dx, 0, 0);
            level.setBlock(rail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);

            BlockState state = track.defaultBlockState();
            if (state.hasProperty(RailBlock.SHAPE)) {
                state = state.setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
            }
            level.setBlock(rail, state, 3);
        }
    }

    private static void buildCurve(
            ServerLevel level,
            BlockPos root,
            LegacyBatchTrackSpec spec,
            AbstractLegacyBatchRailBlock block) {

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, Direction.NORTH, part);
            BlockPos support = spec.supportForPart(root, Direction.NORTH, part);
            level.setBlock(support, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(rail, Blocks.AIR.defaultBlockState(), 3);
        }

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, Direction.NORTH, part);
            level.setBlock(
                    rail,
                    block.stateForPart(Direction.NORTH, part, false),
                    3);
        }

        // Re-lock canonical states after neighbor negotiation.
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, Direction.NORTH, part);
            if (level.getBlockState(rail).getBlock() == block) {
                level.setBlock(
                        rail,
                        block.stateForPart(Direction.NORTH, part, false),
                        2);
            }
        }
    }

    private static void probeStraight(
            ServerLevel level,
            AbstractMinecart cart,
            BlockPos rail,
            List<String> report) {

        double sx = rail.getX() + 0.5D;
        double sy = rail.getY() + RAIL_Y;
        double sz = rail.getZ() + 0.5D;

        cart.moveTo(sx, sy, sz, -90.0F, 0.0F);
        cart.setYRot(-90.0F);
        cart.setXRot(0.0F);
        cart.setDeltaMovement(Vec3.ZERO);

        Vec3 before = cart.position();
        boolean added = level.addFreshEntity(cart);
        if (!added) {
            report.add("STRAIGHT added=false");
            return;
        }

        // One real entity tick with zero commanded motion. Any immediate
        // rail/path recentering is captured without needing player input.
        cart.tick();

        Vec3 after = cart.position();
        float bodyYaw = renderYaw(cart);

        report.add("STRAIGHT added=true"
                + " before=" + vec(before)
                + " after=" + vec(after)
                + " dx=" + f(after.x - sx)
                + " dz=" + f(after.z - sz)
                + " horizontalError=" + f(Math.hypot(after.x - sx, after.z - sz))
                + " bodyYaw=" + f(bodyYaw)
                + " motion=" + vec(cart.getDeltaMovement()));
    }

    private static int probeCurve(
            ServerLevel level,
            AbstractMinecart cart,
            BlockPos root,
            LegacyBatchTrackSpec spec,
            int part,
            List<String> report) {

        BlockPos rail = spec.offsetForPart(root, Direction.NORTH, part);
        float yaw = tangentYaw(spec, root, part);

        double x = rail.getX() + 0.5D;
        double y = rail.getY() + RAIL_Y;
        double z = rail.getZ() + 0.5D;

        cart.moveTo(x, y, z, yaw, 0.0F);
        cart.setYRot(yaw);
        cart.setXRot(0.0F);
        cart.setDeltaMovement(Vec3.ZERO);

        boolean added = level.addFreshEntity(cart);
        if (!added) {
            report.add("CURVE part=" + part + " added=false");
            return 0;
        }

        Vec3 before = cart.position();
        cart.tick();
        Vec3 after = cart.position();
        float bodyYaw = renderYaw(cart);

        report.add("CURVE part=" + part
                + " rail=" + rail
                + " guideYaw=" + f(yaw)
                + " bodyYaw=" + f(bodyYaw)
                + " before=" + vec(before)
                + " after=" + vec(after)
                + " tickShift=" + f(after.distanceTo(before)));

        int count = 0;
        for (double spacing : SPACINGS) {
            LegacyContinuousTrackPath.VirtualBogiePose pose =
                    LegacyContinuousTrackPath.virtualBogiePose(
                            cart, bodyYaw, spacing);

            if (pose == null) {
                report.add("POSE part=" + part
                        + " spacing=" + f(spacing)
                        + " result=null");
                continue;
            }

            double dx = pose.center().x - cart.getX();
            double dz = pose.center().z - cart.getZ();

            report.add("POSE part=" + part
                    + " spacing=" + f(spacing)
                    + " center=" + vec(pose.center())
                    + " centerDx=" + f(dx)
                    + " centerDz=" + f(dz)
                    + " centerShift=" + f(Math.hypot(dx, dz))
                    + " poseYaw=" + f(pose.yaw())
                    + " pitch=" + f(pose.pitch())
                    + " front=" + vec(pose.front())
                    + " rear=" + vec(pose.rear()));
            count++;
        }
        return count;
    }

    private static float tangentYaw(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            int part) {

        int prev = Math.max(0, part - 1);
        int next = Math.min(spec.partCount() - 1, part + 1);
        if (prev == next) return 0.0F;

        BlockPos a = spec.offsetForPart(root, Direction.NORTH, prev);
        BlockPos b = spec.offsetForPart(root, Direction.NORTH, next);

        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    private static float renderYaw(AbstractMinecart cart) {
        try {
            Method method = cart.getClass().getMethod(
                    "getRenderTrainFacingYaw", float.class);
            Object value = method.invoke(cart, 1.0F);
            if (value instanceof Number number) {
                return number.floatValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return cart.getYRot();
    }

    private static AbstractLegacyBatchRailBlock resolveBatchBlock(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("traincraft", id));
        return block instanceof AbstractLegacyBatchRailBlock batch
                ? batch
                : null;
    }

    private static void forceArea(ServerLevel level, BlockPos center, int radiusChunks) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
    }

    private static String vec(Vec3 v) {
        return "(" + f(v.x) + "," + f(v.y) + "," + f(v.z) + ")";
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String safe(String text) {
        return text == null ? "<null>" : text.replace('\n', ' ').replace('\r', ' ');
    }
}
