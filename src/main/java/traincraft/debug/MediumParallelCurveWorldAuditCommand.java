package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DEV50b2o temporary read-only live-world diagnostic.
 * Does not modify blocks, visuals, model resources, train pathing, or worldgen.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MediumParallelCurveWorldAuditCommand {
    private MediumParallelCurveWorldAuditCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tc_parallel_debug")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> audit(ctx.getSource().getPlayerOrException())));
    }

    private static boolean isTarget(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !Traincraft.MOD_ID.equals(id.getNamespace())) return false;
        return "track_parallel_curve_medium_left_segment".equals(id.getPath())
                || "track_parallel_curve_medium_right_segment".equals(id.getPath());
    }

    private static String at(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int audit(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        Map<String, Assembly> roots = new LinkedHashMap<>();
        int radius = 34;
        int observed = 0;
        int unloaded = 0;
        // Read-only scan; never loads new chunks and never scans the whole world.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) { unloaded++; continue; }
                    BlockState state = level.getBlockState(pos);
                    if (!isTarget(state) || !(state.getBlock() instanceof AbstractLegacyBatchRailBlock rail)) continue;
                    observed++;
                    Direction face = rail.getAssemblyFacing(state);
                    BlockPos root = rail.getAssemblyRoot(pos, state);
                    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    String key = id + "|" + at(root) + "|" + face;
                    Assembly group = roots.computeIfAbsent(key, unused -> new Assembly(root, face, id, rail));
                    group.seen++;
                }
            }
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2O] START player={} center={} scannedRadius={} foundCells={} assemblies={} unloadedPositions={}",
                player.getGameProfile().getName(), at(center), radius, observed, roots.size(), unloaded);
        int mismatches = 0;
        int groups = 0;
        for (Assembly group : roots.values()) {
            groups++;
            if (groups > 12) {
                Traincraft.LOGGER.warn("[TC-DEV50B2O] Truncated after 12 assemblies; move closer and run again");
                break;
            }
            int ok = 0;
            int bad = 0;
            int parts = group.block.getSpec().partCount();
            Traincraft.LOGGER.warn("[TC-DEV50B2O] ASSEMBLY id={} root={} facing={} seenInScan={} expectedParts={}",
                    group.id, at(group.root), group.facing, group.seen, parts);
            for (int p = 0; p < parts; p++) {
                BlockPos expected = group.block.getSpec().offsetForPart(group.root, group.facing, p);
                if (!level.hasChunkAt(expected)) {
                    bad++;
                    Traincraft.LOGGER.warn("[TC-DEV50B2O] PART={} pos={} CHUNK_UNLOADED", p, at(expected));
                    continue;
                }
                BlockState state = level.getBlockState(expected);
                ResourceLocation actualId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                boolean sameType = state.getBlock() == group.block;
                boolean matches = sameType
                        && group.block.getPart(state) == p
                        && group.block.getAssemblyFacing(state) == group.facing
                        && group.block.getAssemblyRoot(expected, state).equals(group.root);
                if (matches) ok++; else bad++;
                Traincraft.LOGGER.warn("[TC-DEV50B2O] PART={} pos={} expected={} facing={} actual={} state={} verdict={}",
                        p, at(expected), group.id, group.facing, actualId, state, matches ? "OK" : "MISMATCH");
            }
            mismatches += bad;
            Traincraft.LOGGER.warn("[TC-DEV50B2O] ASSEMBLY_RESULT id={} root={} intactParts={} mismatches={}",
                    group.id, at(group.root), ok, bad);
        }
        if (roots.isEmpty()) {
            player.sendSystemMessage(Component.literal("TC parallel debug: no Medium Parallel Curve found within 34 blocks. Stand near the affected curve."));
        } else {
            player.sendSystemMessage(Component.literal("TC parallel debug: " + roots.size() + " assemblies, " + mismatches
                    + " missing/mismatched cells. Detailed [TC-DEV50B2O] in latest.log; client mesh probe logs automatically nearby."));
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2O] END assemblies={} mismatchedCells={} -- READ ONLY", roots.size(), mismatches);
        return Command.SINGLE_SUCCESS;
    }

    private static final class Assembly {
        final BlockPos root;
        final Direction facing;
        final ResourceLocation id;
        final AbstractLegacyBatchRailBlock block;
        int seen;
        Assembly(BlockPos root, Direction facing, ResourceLocation id, AbstractLegacyBatchRailBlock block) {
            this.root = root;
            this.facing = facing;
            this.id = id;
            this.block = block;
        }
    }
}
