package traincraft.dev51;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DEV51A: isolated, test-world-only diagnostic and guarded sand-clear for the
 * confirmed DEV50F1N station. No worldgen, track or renderer code is changed.
 * Intentionally requires an explicit preview followed by a separate confirm.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51StationAccess {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51A");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final int FLOOR_Y = 66;
    private static final int FOOT_Y = 67;
    private static final int HEAD_Y = 68;
    private static final int CEILING_Y = 69;
    private static final int MAX_LENGTH = 8;
    private static final int MAX_EDITS = 32;
    private static final long PREVIEW_TICKS = 2400; // two minutes at 20 ticks/second
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDO = new HashMap<>();

    private Dev51StationAccess() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("audit").executes(c -> audit(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place")
                        .then(Commands.literal("confirm").executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51")
                .requires(s -> s.hasPermission(2))
                .then(station));
        LOG.info("[TC-DEV51A] Commands registered, isolated DEV50E6 test-world guard enabled");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51A] /tcdev51 station audit | preview | place confirm | undo");
        say(s, "[DEV51A] Test world only; inspect the preview and world before place.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        ServerLevel level = s.getLevel();
        String name = level.getServer().getWorldData().getLevelName();
        if (!level.dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51A STOP] Only the DEV50E6 Terrain Profile Test overworld is allowed; found " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51A STOP] In-game player required. Console may not confirm changes.");
        return null;
    }

    private static int audit(CommandSourceStack s) {
        if (!allowed(s)) return 0;
        ServerLevel level = s.getLevel();
        Probe neg = probe(level, -1);
        Probe pos = probe(level, +1);
        say(s, "[DEV51A AUDIT] station=" + STATION.toShortString() + " rails=" + railCount(level)
                + " forcedChunks=0 worldChanged=false");
        report(s, neg);
        report(s, pos);
        LOG.info("[TC-DEV51A] AUDIT {} {} rails={} edits=0", neg.summary(), pos.summary(), railCount(level));
        return 1;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s)) return 0;
        ServerPlayer p = player(s);
        if (p == null) return 0;
        PREVIEWS.remove(p.getUUID());
        ServerLevel level = s.getLevel();
        Probe neg = probe(level, -1);
        Probe pos = probe(level, +1);
        report(s, neg);
        report(s, pos);
        Probe chosen = choose(neg, pos);
        if (chosen == null) {
            say(s, "[DEV51A STOP] No safe 2-wide supported sand-only exit to an open landing. NO CHANGES.");
            LOG.info("[TC-DEV51A] PREVIEW STOP {} {}", neg.summary(), pos.summary());
            return 0;
        }
        if (chosen.clear.isEmpty()) {
            say(s, "[DEV51A PASS] " + chosen.side + " corridor already clear; no edits proposed. Re-run /tcrailway village railway stationpathaudit to confirm village route.");
            return 1;
        }
        long railBefore = railFingerprint(level);
        if (railBefore == Long.MIN_VALUE || railCount(level) != 49) {
            say(s, "[DEV51A SAFETY STOP] Expected 49 fully loaded station rails; no changes.");
            return 0;
        }
        Plan plan = new Plan(level.dimension().location().toString(), level.getGameTime(), chosen,
                railBefore);
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51A PREVIEW] " + chosen.side + " length=" + chosen.length + " sandToAir="
                + chosen.clear.size() + " rails=" + railCount(level) + " 2-wide floor supported; no changes.");
        say(s, "[DEV51A PREVIEW] Confirm only if acceptable: /tcdev51 station place confirm (within 2 minutes).");
        LOG.info("[TC-DEV51A] PREVIEW {} length={} sand={} rails={} edits=0",
                chosen.side, chosen.length, chosen.clear.size(), railCount(level));
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s)) return 0;
        ServerPlayer p = player(s);
        if (p == null) return 0;
        Plan plan = PREVIEWS.remove(p.getUUID());
        ServerLevel level = s.getLevel();
        if (p.blockPosition().distSqr(STATION) > 48 * 48) {
            say(s, "[DEV51A SAFETY STOP] Stand within 48 blocks of the station; NO CHANGES.");
            return 0;
        }
        if (plan == null) {
            say(s, "[DEV51A STOP] Missing preview. Run /tcdev51 station preview first.");
            return 0;
        }
        if (!plan.dimension.equals(level.dimension().location().toString())
                || level.getGameTime() < plan.tick
                || level.getGameTime() - plan.tick > PREVIEW_TICKS
                || railFingerprint(level) != plan.railSignature
                || railCount(level) != 49
                || !samePreimage(level, plan)) {
            say(s, "[DEV51A SAFETY STOP] Preview expired or world/rails changed. NO CHANGES; preview again.");
            return 0;
        }
        if (UNDO.containsKey(p.getUUID())) {
            say(s, "[DEV51A SAFETY STOP] Existing undo pending; undo first. NO CHANGES.");
            return 0;
        }
        int applied = 0;
        try {
            List<BlockPos> topFirst = new ArrayList<>(plan.corridor.clear);
            topFirst.sort((first, second) -> Integer.compare(second.getY(), first.getY()));
            for (BlockPos pos : topFirst) {
                if (!isNaturalSand(level.getBlockState(pos))) {
                    throw new IllegalStateException("preimage changed at " + pos);
                }
                if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                    throw new IllegalStateException("setBlock rejected " + pos);
                }
                applied++;
            }
            if (railFingerprint(level) != plan.railSignature) {
                throw new IllegalStateException("protected rail fingerprint changed");
            }
            for (BlockPos pos : plan.corridor.clear) {
                if (!level.getBlockState(pos).isAir()) {
                    throw new IllegalStateException("unexpected block after clear at " + pos);
                }
            }
        } catch (RuntimeException ex) {
            // Best-effort immediate rollback, guarded against replacing non-air blocks.
            int restored = restore(level, plan, true);
            say(s, "[DEV51A STOP] Placement failed at edit=" + applied + "; restored=" + restored
                    + "/" + applied + ". Inspect before retrying.");
            LOG.error("[TC-DEV51A] PLACE FAILED, restore {} of {}: {}", restored, applied, ex.toString());
            return 0;
        }
        UNDO.put(p.getUUID(), plan);
        say(s, "[DEV51A PLACED] Removed " + applied + " preverified natural sand blocks; protected rails unchanged.");
        say(s, "[DEV51A] Same-session undo: /tcdev51 station undo . Re-run legacy stationpathaudit to test full route.");
        LOG.info("[TC-DEV51A] PLACED {} sand={} railsProtected=true forcedChunks=0", plan.corridor.side, applied);
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s)) return 0;
        ServerPlayer p = player(s);
        if (p == null) return 0;
        Plan plan = UNDO.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51A STOP] No same-session DEV51A placement to undo.");
            return 0;
        }
        ServerLevel level = s.getLevel();
        if (!plan.dimension.equals(level.dimension().location().toString())
                || railFingerprint(level) != plan.railSignature
                || !unchangedOutsideEdits(level, plan)) {
            say(s, "[DEV51A SAFETY STOP] Environment changed; undo refused to protect new blocks.");
            return 0;
        }
        for (BlockPos pos : plan.corridor.clear) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
                say(s, "[DEV51A SAFETY STOP] An edited cell was modified. Undo refused; do not force.");
                return 0;
            }
        }
        int n = restore(level, plan, false);
        if (n == plan.corridor.clear.size()) {
            UNDO.remove(p.getUUID());
            say(s, "[DEV51A UNDO PASS] Restored " + n + " original sand blocks. Rails unchanged.");
            LOG.info("[TC-DEV51A] UNDO PASS restored={}", n);
            return 1;
        }
        say(s, "[DEV51A WARNING] Partial undo (" + n + "). Inspect world; do not force.");
        return 0;
    }

    private static int restore(ServerLevel level, Plan plan, boolean onlyAir) {
        List<BlockPos> locations = new ArrayList<>(plan.corridor.clear);
        locations.sort((first, second) -> Integer.compare(first.getY(), second.getY()));
        int count = 0;
        for (BlockPos pos : locations) {
            if (!level.hasChunkAt(pos)) continue;
            if (onlyAir && !level.getBlockState(pos).isAir()) continue;
            BlockState before = plan.snap.get(pos);
            if (before != null && level.setBlock(pos, before, 3)) count++;
        }
        return count;
    }

    private static boolean samePreimage(ServerLevel level, Plan plan) {
        for (Map.Entry<BlockPos, BlockState> entry : plan.snap.entrySet()) {
            if (!level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).equals(entry.getValue())) return false;
        }
        return true;
    }

    private static boolean unchangedOutsideEdits(ServerLevel level, Plan plan) {
        for (Map.Entry<BlockPos, BlockState> entry : plan.snap.entrySet()) {
            if (plan.corridor.clear.contains(entry.getKey())) continue;
            if (!level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).equals(entry.getValue())) return false;
        }
        return true;
    }

    private static Probe choose(Probe first, Probe second) {
        if (!first.ok) return second.ok ? second : null;
        if (!second.ok) return first;
        if (first.clear.isEmpty()) return first;
        if (second.clear.isEmpty()) return second;
        if (first.clear.size() != second.clear.size()) {
            return first.clear.size() < second.clear.size() ? first : second;
        }
        return first.length <= second.length ? first : second;
    }

    private static Probe probe(ServerLevel level, int zStep) {
        String side = zStep == -1 ? "NEG_END" : "POS_END";
        int endZ = zStep == -1 ? 3157 : 3171;
        Map<BlockPos, BlockState> snap = new LinkedHashMap<>();
        List<BlockPos> clear = new ArrayList<>();
        for (int x = 93; x <= 94; x++) {
            for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                BlockPos pos = new BlockPos(x, y, endZ);
                if (!level.hasChunkAt(pos)) return Probe.fail(side, "PLATFORM_CHUNK_UNLOADED");
                snap.put(pos, level.getBlockState(pos));
            }
            if (!floorSafe(level, new BlockPos(x, FLOOR_Y, endZ)))
                return Probe.fail(side, "PLATFORM_FLOOR_UNSAFE");
            if (!level.getBlockState(new BlockPos(x, FOOT_Y, endZ)).isAir()
                    || !level.getBlockState(new BlockPos(x, HEAD_Y, endZ)).isAir())
                return Probe.fail(side, "PLATFORM_HEADROOM_OBSTRUCTED");
        }
        int consecutiveOpen = 0;
        String firstProblem = "NO_OPEN_LANDING_WITHIN_8_BLOCKS";
        for (int dist = 1; dist <= MAX_LENGTH; dist++) {
            int z = endZ + dist * zStep;
            boolean columnClear = true;
            for (int x = 93; x <= 94; x++) {
                BlockPos floor = new BlockPos(x, FLOOR_Y, z);
                BlockPos feet = new BlockPos(x, FOOT_Y, z);
                BlockPos head = new BlockPos(x, HEAD_Y, z);
                BlockPos cap = new BlockPos(x, CEILING_Y, z);
                if (!level.hasChunkAt(floor) || !level.hasChunkAt(feet)
                        || !level.hasChunkAt(head) || !level.hasChunkAt(cap))
                    return Probe.fail(side, "EXTERIOR_CHUNK_UNLOADED_AT_" + z);
                snap.put(floor, level.getBlockState(floor));
                snap.put(feet, level.getBlockState(feet));
                snap.put(head, level.getBlockState(head));
                snap.put(cap, level.getBlockState(cap));
                if (!floorSafe(level, floor))
                    return Probe.fail(side, "NO_SAFE_SUPPORTED_FLOOR_AT_" + x + "_" + z);
                if (isFallingMaterial(level.getBlockState(cap)))
                    return Probe.fail(side, "FALLING_CEILING_RISK_AT_" + x + "_" + z);
                for (BlockPos pos : List.of(feet, head)) {
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (!isNaturalSand(state) || level.getBlockEntity(pos) != null || isRail(state))
                        return Probe.fail(side, "PROTECTED_OBSTRUCTION_AT_" + pos.toShortString());
                    clear.add(pos);
                    columnClear = false;
                }
            }
            if (clear.size() > MAX_EDITS) return Probe.fail(side, "EDIT_CAP_EXCEEDED");
            consecutiveOpen = columnClear ? consecutiveOpen + 1 : 0;
            if (dist >= 3 && consecutiveOpen >= 2) {
                // All observed cells are snapshotted; two completely open columns
                // beyond potential clearing are required as the local landing.
                return Probe.ok(side, dist, List.copyOf(clear), snap);
            }
        }
        return Probe.fail(side, firstProblem);
    }

    private static boolean floorSafe(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getFluidState().isSource()
                && state.getFluidState().isEmpty() && !isRail(state)
                && state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    private static boolean isNaturalSand(BlockState s) {
        return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND);
    }

    private static boolean isFallingMaterial(BlockState s) {
        return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SUSPICIOUS_SAND) || s.is(Blocks.SUSPICIOUS_GRAVEL);
    }

    private static boolean isRail(BlockState s) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        String path = id.getPath();
        return s.is(net.minecraft.tags.BlockTags.RAILS)
                || path.contains("track") || path.contains("rail");
    }

    private static int railCount(ServerLevel level) {
        int count = 0;
        for (int z = STATION.getZ() - 24; z <= STATION.getZ() + 24; z++) {
            BlockPos at = new BlockPos(STATION.getX(), STATION.getY(), z);
            if (level.hasChunkAt(at) && isRail(level.getBlockState(at))) count++;
        }
        return count;
    }

    private static long railFingerprint(ServerLevel level) {
        // No chunks are loaded or forced by this bounded snapshot.
        long value = 1469598103934665603L;
        for (int z = STATION.getZ() - 24; z <= STATION.getZ() + 24; z++) {
            BlockPos at = new BlockPos(STATION.getX(), STATION.getY(), z);
            if (!level.hasChunkAt(at)) return Long.MIN_VALUE;
            BlockState state = level.getBlockState(at);
            value = (value ^ Block.getId(state)) * 1099511628211L;
        }
        return value;
    }

    private static void report(CommandSourceStack s, Probe probe) {
        say(s, "[DEV51A EXIT] " + probe.summary());
    }

    private static void say(CommandSourceStack s, String message) {
        s.sendSuccess(() -> Component.literal(message), false);
    }

    private static final class Probe {
        final String side;
        final boolean ok;
        final String why;
        final int length;
        final List<BlockPos> clear;
        final Map<BlockPos, BlockState> snap;

        private Probe(String side, boolean ok, String why, int length,
                      List<BlockPos> clear, Map<BlockPos, BlockState> snap) {
            this.side = side;
            this.ok = ok;
            this.why = why;
            this.length = length;
            this.clear = clear;
            this.snap = snap;
        }

        static Probe fail(String side, String why) {
            return new Probe(side, false, why, 0, List.of(), Map.of());
        }

        static Probe ok(String side, int length, List<BlockPos> clear,
                        Map<BlockPos, BlockState> snap) {
            return new Probe(side, true, "OPEN_LANDING", length, clear,
                    new LinkedHashMap<>(snap));
        }

        String summary() {
            return side + " " + (ok ? "SAFE_LOCAL_EXIT" : "BLOCKED")
                    + " reason=" + why + " length=" + length + " sandToAir=" + clear.size();
        }
    }

    private static final class Plan {
        final String dimension;
        final long tick;
        final Probe corridor;
        final long railSignature;
        final Map<BlockPos, BlockState> snap;

        Plan(String dimension, long tick, Probe corridor, long railSignature) {
            this.dimension = dimension;
            this.tick = tick;
            this.corridor = corridor;
            this.railSignature = railSignature;
            this.snap = corridor.snap;
        }
    }
}
