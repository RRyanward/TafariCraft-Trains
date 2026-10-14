package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.TrackMaterialBlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * r2e8 temporary curved-slope runtime diagnostic.
 *
 * READ ONLY:
 * - does not place/remove/update blocks
 * - does not alter movement/path/render code
 * - only scans nearby existing batch-track assemblies and writes a report
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurvedSlopeRuntimeDebugCommand {
    private static final int RADIUS = 48;
    private static final int Y_RADIUS = 10;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private CurvedSlopeRuntimeDebugCommand() {}

    private record AssemblyKey(
            String specId,
            BlockPos root,
            Direction facing) {
    }

    private record Candidate(
            AssemblyKey key,
            AbstractLegacyBatchRailBlock block,
            double distanceSq) {
    }

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_curved_slope_debug")
                        .executes(ctx -> run(ctx.getSource().getPlayerOrException()))
        );
    }

    private static int run(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        Map<AssemblyKey, Candidate> assemblies = new LinkedHashMap<>();

        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int y = Math.max(level.getMinBuildHeight(), center.getY() - Y_RADIUS);
                 y <= Math.min(level.getMaxBuildHeight() - 1, center.getY() + Y_RADIUS);
                 y++) {
                for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    String specId = batch.getSpec().id();
                    if (!specId.startsWith("track_slope_curve_")) {
                        continue;
                    }

                    BlockPos root = batch.getAssemblyRoot(pos, state).immutable();
                    Direction facing = batch.getAssemblyFacing(state);
                    AssemblyKey key = new AssemblyKey(specId, root, facing);

                    double dx = root.getX() + 0.5D - player.getX();
                    double dy = root.getY() + 0.5D - player.getY();
                    double dz = root.getZ() + 0.5D - player.getZ();
                    double d2 = dx * dx + dy * dy + dz * dz;

                    Candidate current = assemblies.get(key);
                    if (current == null || d2 < current.distanceSq()) {
                        assemblies.put(key, new Candidate(key, batch, d2));
                    }
                }
            }
        }

        if (assemblies.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "[TC curved slope debug] No curved-slope assembly found within "
                            + RADIUS + " blocks."));
            return 0;
        }

        Candidate candidate = assemblies.values().stream()
                .min(Comparator.comparingDouble(Candidate::distanceSq))
                .orElseThrow();

        List<String> report = buildReport(player, candidate);

        try {
            Path directory = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(directory);

            Path output = directory.resolve(
                    "curved-slope-runtime-" + STAMP.format(LocalDateTime.now()) + ".txt")
                    .toAbsolutePath();

            Files.write(output, report, StandardCharsets.UTF_8);

            player.sendSystemMessage(Component.literal(
                    "[TC curved slope debug] Captured "
                            + candidate.key().specId()
                            + " root=" + pos(candidate.key().root())
                            + " facing=" + candidate.key().facing().getName()));
            player.sendSystemMessage(Component.literal(
                    "[TC curved slope debug] Report: " + output));

            Traincraft.LOGGER.info(
                    "[TC-CURVED-SLOPE-DEBUG] report={} spec={} root={} facing={}",
                    output,
                    candidate.key().specId(),
                    candidate.key().root(),
                    candidate.key().facing());

            return Command.SINGLE_SUCCESS;
        } catch (IOException ex) {
            Traincraft.LOGGER.error(
                    "[TC-CURVED-SLOPE-DEBUG] Failed to write report", ex);
            player.sendSystemMessage(Component.literal(
                    "[TC curved slope debug] Failed to write report: "
                            + ex.getMessage()));
            return 0;
        }
    }

    private static List<String> buildReport(
            ServerPlayer player,
            Candidate candidate) {

        AssemblyKey key = candidate.key();
        AbstractLegacyBatchRailBlock batch = candidate.block();
        LegacyBatchTrackSpec spec = batch.getSpec();
        ServerLevel level = player.serverLevel();

        List<String> out = new ArrayList<>();

        out.add("========================================================================");
        out.add("TAFARICRAFT - TRAINS r2e8 CURVED-SLOPE RUNTIME DEBUG");
        out.add("MUTATION=NONE");
        out.add("========================================================================");
        out.add("player=" + vec(player.getX(), player.getY(), player.getZ()));
        out.add("spec=" + key.specId());
        out.add("root=" + pos(key.root()));
        out.add("facing=" + key.facing().getName());
        out.add("partCount=" + spec.partCount());
        out.add("distanceSq=" + String.format(Locale.ROOT, "%.6f", candidate.distanceSq()));

        ResourceLocation rootBlockId = ForgeRegistries.BLOCKS.getKey(
                level.getBlockState(key.root()).getBlock());
        out.add("rootBlockId=" + String.valueOf(rootBlockId));
        out.add("rootState=" + level.getBlockState(key.root()));

        BlockEntity rootEntity = level.getBlockEntity(key.root());
        out.add("rootBlockEntity="
                + (rootEntity == null ? "<none>" : rootEntity.getClass().getName()));

        if (rootEntity instanceof TrackMaterialBlockEntity materialEntity) {
            out.add("rootMaterial=" + materialEntity.getMaterial());
        } else {
            out.add("rootMaterial=<not TrackMaterialBlockEntity>");
        }

        out.add("");
        out.add("EXPECTED_PARTS");
        out.add("format: part | world | relRoot | blockId | state | returnedRoot | returnedFacing | sameBatchBlock");

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos world = spec.offsetForPart(
                    key.root(), key.facing(), part).immutable();

            int relX = world.getX() - key.root().getX();
            int relY = world.getY() - key.root().getY();
            int relZ = world.getZ() - key.root().getZ();

            BlockState actual = level.getBlockState(world);
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(actual.getBlock());

            String returnedRoot = "<not batch>";
            String returnedFacing = "<not batch>";
            boolean sameBatch = false;

            if (actual.getBlock() instanceof AbstractLegacyBatchRailBlock actualBatch) {
                returnedRoot = pos(
                        actualBatch.getAssemblyRoot(world, actual).immutable());
                returnedFacing =
                        actualBatch.getAssemblyFacing(actual).getName();
                sameBatch = actual.getBlock() == batch;
            }

            out.add(String.format(
                    Locale.ROOT,
                    "%02d | %s | (%+d,%+d,%+d) | %s | %s | %s | %s | %s",
                    part,
                    pos(world),
                    relX, relY, relZ,
                    String.valueOf(id),
                    actual,
                    returnedRoot,
                    returnedFacing,
                    sameBatch));
        }

        out.add("");
        out.add("ENDPOINTS");
        for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
            BlockPos endpointPos =
                    spec.endpointPosition(key.root(), key.facing(), endpoint);
            Direction outward =
                    spec.rotateDirection(key.facing(), endpoint.outward());

            out.add(String.format(
                    Locale.ROOT,
                    "part=%d world=%s outward=%s",
                    endpoint.part(),
                    pos(endpointPos),
                    outward.getName()));
        }

        out.add("");
        out.add("NEARBY_CURVED_SLOPE_SEGMENTS_AROUND_ROOT");
        out.add("format: world | relRoot | blockId | state | resolvedSpec | resolvedRoot | resolvedFacing");

        for (int dx = -18; dx <= 18; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -18; dz <= 18; dz++) {
                    BlockPos probe = key.root().offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);

                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock other)) {
                        continue;
                    }
                    if (!other.getSpec().id().startsWith("track_slope_curve_")) {
                        continue;
                    }

                    ResourceLocation id =
                            ForgeRegistries.BLOCKS.getKey(state.getBlock());

                    out.add(String.format(
                            Locale.ROOT,
                            "%s | (%+d,%+d,%+d) | %s | %s | %s | %s | %s",
                            pos(probe),
                            dx, dy, dz,
                            String.valueOf(id),
                            state,
                            other.getSpec().id(),
                            pos(other.getAssemblyRoot(probe, state)),
                            other.getAssemblyFacing(state).getName()));
                }
            }
        }

        out.add("");
        out.add("========================================================================");
        out.add("END");
        out.add("========================================================================");

        return out;
    }

    private static String pos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String vec(double x, double y, double z) {
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", x, y, z);
    }
}
