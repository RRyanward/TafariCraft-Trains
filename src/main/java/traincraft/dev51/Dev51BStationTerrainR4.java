package traincraft.dev51;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DEV51B R4 - post-R3 stair restoration and terrain-first safe sand grading.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Every edit is shown in an explicit preview and verified again on confirmation.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationTerrainR4 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R4");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 96;
    private static final int STAIR_LOG_LIMIT = 12;
    private static final int SAND_LOG_LIMIT = 140;
    private static final int PATH_LIMIT = 36;
    private static final int EXPECTED_STAIR_COUNT = 6;
    private static final BlockPos[] KNOWN_STAIRS = {
            new BlockPos(94, 66, 3172), new BlockPos(94, 66, 3173),
            new BlockPos(101, 66, 3172), new BlockPos(101, 66, 3173),
            new BlockPos(103, 66, 3163), new BlockPos(103, 66, 3166)
    };
    private static final Direction[] EXPECTED_FACING = {
            Direction.EAST, Direction.EAST, Direction.WEST, Direction.WEST,
            Direction.SOUTH, Direction.NORTH
    };
    // Exact six tile path built by the R3 20-edit placement (2026-10-13 08:37:45).
    // R4 MUST preserve these, and refuse a partially reverted/foreign world.
    private static final BlockPos[] R3_PATH = {
            new BlockPos(94, 66, 3149), new BlockPos(94, 66, 3148),
            new BlockPos(94, 66, 3147), new BlockPos(94, 66, 3146),
            new BlockPos(94, 65, 3145), new BlockPos(94, 65, 3144)
    };
    private static final BlockPos[] R3_SAND_CLEARED = {
            new BlockPos(95, 67, 3163), new BlockPos(95, 67, 3165)
    };
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationTerrainR4() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b4")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R4] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R4] /tcdev51b4 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R4] SAVED R3 WORLD: backup first; preview the grade before any placement.");
        say(s, "[DEV51B-R4] DEV50E6-only, R3 20-edit postimages and 49 rails required.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R4 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R4 STOP] In-game operator required");
        return null;
    }

    private static boolean vicinity(ServerPlayer p) {
        return p.blockPosition().distSqr(STATION) <= (long) 64 * 64;
    }

    private static int survey(CommandSourceStack s) {
        if (!allowed(s)) return 0;
        if (!vicinityCheck(s)) return 0;
        long rails = rails(s.getLevel());
        long guard = regionFingerprint(s.getLevel());
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE) {
            say(s, "[DEV51B-R4 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R4 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R4 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R4 STOP] Previous DEV51B-R4 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R4 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R4] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R4 requires all six exact saved-R3 stair reversals before allowing
        // any sand grading; no partial staircase changes.
        if (report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R4 HOLD] Require six verified R3 stair reversals. Approved=" + report.stairs
                    + " of six. No partial stair relocation or sand/path changes allowed.");
            return 0;
        }
        if (!report.pathConnected || report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R4 HOLD] Village connection not verified; placement disabled.");
            return 0;
        }
        if (report.sand == 0) {
            say(s, "[DEV51B-R4 HOLD] No safely lowerable natural surface proven; do not restore stairs alone.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R4 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R4 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b4 station place confirm");
        LOG.info("[TC-DEV51B-R4] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R4 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R4 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> ordered = new ArrayList<>(plan.edits.entrySet());
        // Sand clearance first; then floor filling/raising (not a blind replace).
        ordered.sort((a, b) -> {
            int pa = a.getValue().after.isAir() ? 0 : 1;
            int pb = b.getValue().after.isAir() ? 0 : 1;
            if (pa != pb) return Integer.compare(pa, pb);
            // Remove upper sand first, preventing unsupported sand above it
            // from falling into a cell scheduled for clearing.
            if (pa == 0) return Integer.compare(b.getKey().getY(), a.getKey().getY());
            return Integer.compare(a.getKey().getY(), b.getKey().getY());
        });
        List<Map.Entry<BlockPos, Edit>> applied = new ArrayList<>();
        try {
            for (Map.Entry<BlockPos, Edit> step : ordered) {
                BlockPos pos = step.getKey();
                Edit edit = step.getValue();
                if (!l.hasChunkAt(pos) || !l.getBlockState(pos).equals(edit.before)
                        || isRail(edit.before) || isRail(edit.after)
                        || l.getBlockEntity(pos) != null)
                    throw new IllegalStateException("protected/changed edit at " + pos.toShortString());
                if (!l.setBlock(pos, edit.after, 3))
                    throw new IllegalStateException("setBlock rejected " + pos.toShortString());
                applied.add(step);
            }
            if (rails(l) != plan.rails || railCount(l) != 49 || !matches(l, plan.edits, false))
                throw new IllegalStateException("postcondition/rails mismatch");
        } catch (RuntimeException ex) {
            int restored = revert(l, applied);
            say(s, "[DEV51B-R4 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R4] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R4 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b4 station undo");
        say(s, "[DEV51B-R4] Inspect original stair height, one-block natural sand grading and preserved village path.");
        LOG.info("[TC-DEV51B-R4] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R4 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R4 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R4 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R4] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R4 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R4] UNDO PASS restored={}", restored);
        return 1;
    }

    private static int revert(ServerLevel l, List<Map.Entry<BlockPos, Edit>> applied) {
        List<Map.Entry<BlockPos, Edit>> copy = new ArrayList<>(applied);
        Collections.reverse(copy);
        int restored = 0;
        for (Map.Entry<BlockPos, Edit> e : copy) {
            if (l.hasChunkAt(e.getKey()) && l.getBlockState(e.getKey()).equals(e.getValue().after)
                    && l.setBlock(e.getKey(), e.getValue().before, 3)) restored++;
        }
        return restored;
    }

    private static boolean matches(ServerLevel l, Map<BlockPos, Edit> edits, boolean before) {
        for (Map.Entry<BlockPos, Edit> e : edits.entrySet()) {
            if (!l.hasChunkAt(e.getKey()) || !l.getBlockState(e.getKey())
                    .equals(before ? e.getValue().before : e.getValue().after)) return false;
        }
        return true;
    }

    private static Report makeReport(ServerLevel l) {
        Report r = new Report();
        scanStairs(l, r);
        verifyPathAndR3Clearance(l, r);
        scanSand(l, r);
        return r;
    }

    private static void scanStairs(ServerLevel l, Report r) {
        // R3 was PLACED and the user has now exited Minecraft. In-memory
        // /tcdev51b3 undo no longer exists. R4 explicitly requires the saved
        // R3 postimages, not the original six Y66 stairs.
        for (int i = 0; i < KNOWN_STAIRS.length; i++) {
            BlockPos lower = KNOWN_STAIRS[i];
            BlockPos upper = lower.above();
            BlockPos base = lower.below();
            BlockState expectedStair = Blocks.SANDSTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, EXPECTED_FACING[i]);
            String reason = "UNKNOWN";
            if (!loaded(l, lower, upper, base, upper.above(), upper.above(2))) {
                reason = "UNLOADED_STOP";
                r.unsafe = true;
            } else {
                BlockState below = l.getBlockState(lower);
                BlockState above = l.getBlockState(upper);
                if (!below.is(Blocks.SANDSTONE) || !above.equals(expectedStair)) {
                    reason = "R3_POSTIMAGE_MISMATCH:BELOW=" + id(below)
                            + ",ABOVE=" + above;
                    r.unsafe = true;
                } else if (l.getBlockEntity(lower) != null || l.getBlockEntity(upper) != null
                        || !l.getBlockState(base).isFaceSturdy(l, base, Direction.UP)) {
                    reason = "PROTECTED_BLOCK_ENTITY_OR_UNSUPPORTED_BASE";
                    r.unsafe = true;
                } else if (!l.getBlockState(upper.above()).isAir()
                        || !l.getBlockState(upper.above(2)).isAir()) {
                    reason = "NO_HEADROOM_FOR_ORIGINAL_STAIR";
                    r.unsafe = true;
                } else if (add(r, l, upper, Blocks.AIR.defaultBlockState(), "R3_STAIR_TOP_RESTORE_AIR")
                        && add(r, l, lower, expectedStair, "R3_STAIR_ORIGINAL_Y66")) {
                    reason = "VERIFIED_RESTORE_Y67_TO_Y66";
                    r.stairs++;
                    r.stairFound++;
                    r.stairPositions.add(upper.toShortString() + " -> "
                            + lower.toShortString() + " facing=" + EXPECTED_FACING[i].getName());
                } else {
                    reason = "EXACT_STAIR_EDIT_CONFLICT";
                    r.unsafe = true;
                }
            }
            LOG.info("[TC-DEV51B-R4] STAIR_RESTORE index={} original={} raised={} reason={}",
                    i, lower.toShortString(), upper.toShortString(), reason);
        }
    }

    private static void verifyPathAndR3Clearance(ServerLevel l, Report r) {
        // All 20 original R3 postimage cells must be identifiable before R4
        // can touch anything. Twelve are verified by scanStairs. The remaining
        // six path tiles and two already-cleared sand cells are frozen here.
        for (BlockPos p : R3_PATH) {
            if (!loaded(l, p, p.above(), p.above(2))
                    || !l.getBlockState(p).is(Blocks.SANDSTONE)
                    || !walkable(l, p) || l.getBlockEntity(p) != null) {
                r.unsafe = true;
                LOG.warn("[TC-DEV51B-R4] PATH_R3_MISMATCH at={} current={} - STOP",
                        p.toShortString(), l.hasChunkAt(p) ? id(l.getBlockState(p)) : "UNLOADED");
                r.pathReason = "R3_SAVED_PATH_NOT_IDENTICAL";
                return;
            }
            r.pathTiles++;
        }
        for (BlockPos p : R3_SAND_CLEARED) {
            if (!loaded(l, p) || !l.getBlockState(p).isAir()) {
                r.unsafe = true;
                LOG.warn("[TC-DEV51B-R4] SAND_R3_MISMATCH at={} - STOP", p.toShortString());
                r.pathReason = "R3_SAVED_SAND_NOT_IDENTICAL";
                return;
            }
        }
        BlockPos village = new BlockPos(95, 65, 3143);
        if (!loaded(l, village) || !l.getBlockState(village).is(Blocks.DIRT_PATH)) {
            r.pathReason = "EXISTING_VILLAGE_DIRT_PATH_MISSING";
            r.unsafe = true;
            return;
        }
        r.pathConnected = true;
        r.pathLength = 8;
        r.pathReason = "PRESERVE_R3_PATH_TO_95_65_3143_NO_PATH_EDITS";
        // Examine the cobble/dirt seam in the video, but do NOT carve or fill
        // it without an exact terrain postimage. Diagnostic data for R5.
        for (int x = 94; x <= 96; x++) for (int z = 3141; z <= 3145; z++) {
            StringBuilder b = new StringBuilder();
            for (int y = 63; y <= 67; y++) {
                BlockPos p = new BlockPos(x, y, z);
                b.append(y).append(":").append(l.hasChunkAt(p) ? id(l.getBlockState(p)) : "UNLOADED").append(" ");
            }
            LOG.info("[TC-DEV51B-R4] PATH_SEAM_SURVEY x={} z={} levels={}", x, z, b.toString());
        }
    }

    private static void scanSand(ServerLevel l, Report r) {
        // Grade ONLY exposed naturally supported top sand one block downward.
        // Y67 sand -> expose safe Y66 floor; Y66 sand -> expose safe Y65 floor.
        // No excavating Y65/below, carving columns, changing foundation, or
        // editing the six sandstone path tiles or station manufactured blocks.
        // A cell must be within a 3-block Manhattan radius of a known stair,
        // in the station-side strip. Every accepted cell is enumerated.
        int logged = 0;
        for (int x = 92; x <= 107; x++) for (int z = 3159; z <= 3177; z++)
            for (int y = 67; y >= 66; y--) {
                int min = Integer.MAX_VALUE;
                for (BlockPos stair : KNOWN_STAIRS) {
                    min = Math.min(min, Math.abs(x - stair.getX()) + Math.abs(z - stair.getZ()));
                }
                if (min < 1 || min > 3) continue;
                BlockPos p = new BlockPos(x, y, z);
                BlockPos support = p.below();
                BlockPos cap = p.above();
                BlockPos head = cap.above();
                if (!loaded(l, support, p, cap, head)) {
                    r.unsafe = true;
                    continue;
                }
                BlockState sand = l.getBlockState(p);
                if (!isSand(sand)) continue;
                String reason;
                BlockState below = l.getBlockState(support);
                boolean naturalSupport = isSand(below) || below.is(Blocks.SANDSTONE)
                        || below.is(Blocks.RED_SANDSTONE) || below.is(Blocks.DIRT)
                        || below.is(Blocks.STONE) || below.is(Blocks.GRASS_BLOCK);
                if (r.edits.containsKey(p) || r.edits.containsKey(support)) {
                    reason = "PROTECTED_R3_STAIR_EDIT";
                } else if (l.getBlockEntity(p) != null || l.getBlockEntity(support) != null) {
                    reason = "BLOCK_ENTITY_PROTECTED";
                    r.unsafe = true;
                } else if (!naturalSupport || !below.isFaceSturdy(l, support, Direction.UP)) {
                    reason = "UNSUPPORTED_OR_MANUFACTURED_BASE";
                    r.sandBlocked++;
                } else if (!l.getBlockState(cap).isAir() || !l.getBlockState(head).isAir()) {
                    reason = "COVERED_OR_STRUCTURAL_SAND_NO_EDIT";
                    r.sandBlocked++;
                } else if (nearRail(l, p)) {
                    reason = "NEAR_RAIL_PROTECTED";
                    r.sandBlocked++;
                } else {
                    // At least one horizontal contact must be open or a
                    // verified station surface; otherwise this is interior
                    // of a natural dune, and is not a station obstruction.
                    boolean interfaceFound = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos n = p.relative(d);
                        if (!l.hasChunkAt(n)) { r.unsafe = true; continue; }
                        BlockState other = l.getBlockState(n);
                        if (other.isAir() || manufacturedFloor(l, n, other)) interfaceFound = true;
                    }
                    if (!interfaceFound) {
                        reason = "NATURAL_DUNE_INTERIOR_HOLD";
                        r.sandBlocked++;
                    } else if (add(r, l, p, Blocks.AIR.defaultBlockState(), "GRADE_SURFACE_SAND_MINUS_1")) {
                        reason = "APPROVED_TOP_LAYER_ONLY";
                        r.sand++;
                    } else {
                        reason = "EDIT_CONFLICT";
                        r.unsafe = true;
                    }
                }
                r.sandContacts++;
                if (logged++ < SAND_LOG_LIMIT) {
                    LOG.info("[TC-DEV51B-R4] GRADE_SCAN pos={} current={} support={} cap={} "
                            + "distanceToStair={} reason={}", p.toShortString(), id(sand), id(below),
                            id(l.getBlockState(cap)), min, reason);
                } else {
                    r.sandLogTruncated = true;
                    r.unsafe = true;
                }
            }
    }

    private static boolean nearRail(ServerLevel l, BlockPos p) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos q = p.offset(dx, 0, dz);
            if (!l.hasChunkAt(q)) return true;
            if (isRail(l.getBlockState(q)) || isRail(l.getBlockState(q.above()))) return true;
        }
        return false;
    }

    private static boolean manufacturedFloor(ServerLevel l, BlockPos floor, BlockState s) {
        if (s.isAir() || isSand(s) || isRail(s) || l.getBlockEntity(floor) != null
                || !s.isFaceSturdy(l, floor, Direction.UP)) return false;
        String path = id(s).getPath();
        return path.contains("stone_brick") || path.contains("cobblestone")
                || path.contains("sandstone") || path.equals("stone")
                || path.contains("acacia_planks") || path.contains("polished_andesite");
    }

    private static boolean walkable(ServerLevel l, BlockPos floor) {
        BlockPos feet = floor.above(), head = feet.above();
        if (!loaded(l, floor, feet, head)) return false;
        BlockState s = l.getBlockState(floor);
        if (s.isAir() || isRail(s) || !s.getFluidState().isEmpty()
                || l.getBlockEntity(floor) != null
                || (!s.is(Blocks.DIRT_PATH) && !s.isFaceSturdy(l, floor, Direction.UP))
                || !clearHead(l, feet) || !clearHead(l, head)) return false;
        String p = id(s).getPath();
        return s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)
                || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || isSand(s)
                || p.contains("stone_brick") || p.contains("sandstone")
                || p.contains("cobblestone") || p.equals("stone") || p.equals("gravel");
    }

    private static boolean clearHead(ServerLevel l, BlockPos at) {
        return l.hasChunkAt(at) && l.getBlockState(at).isAir();
    }

    private static boolean add(Report r, ServerLevel l, BlockPos pos,
                               BlockState replacement, String why) {
        if (!l.hasChunkAt(pos) || (pos.getX() <= 90 || pos.getY() < 63 || pos.getY() > 72) || isRail(l.getBlockState(pos))
                || isRail(replacement) || l.getBlockEntity(pos) != null) {
            r.unsafe = true;
            return false;
        }
        BlockState existing = l.getBlockState(pos);
        if (existing.equals(replacement)) return true;
        Edit prior = r.edits.get(pos);
        if (prior != null) {
            if (!prior.after.equals(replacement)) r.unsafe = true;
            return prior.after.equals(replacement);
        }
        r.edits.put(pos.immutable(), new Edit(existing, replacement, why));
        if (r.edits.size() > LIMIT) r.unsafe = true;
        return true;
    }

    private static boolean loaded(ServerLevel l, BlockPos... positions) {
        for (BlockPos p : positions) if (!l.hasChunkAt(p)) return false;
        return true;
    }

    private static void printReport(CommandSourceStack s, Report r, boolean preview) {
        say(s, "[DEV51B-R4 " + (preview ? "PREVIEW" : "SURVEY") + "] restoredStairs=" + r.stairs
                + " sandToAir=" + r.sand + " preservedR3PathTiles=" + r.pathTiles
                + " totalEdits=" + r.edits.size());
        say(s, "[DEV51B-R4 SCAN] discoveredStairs=" + r.stairFound + " sandContactsReadOnly="
                + r.sandContacts + " sandColumnsHeld=" + r.sandBlocked);
        for (String pos : r.stairPositions) say(s, "[DEV51B-R4 STAIR] " + pos + " (RESTORE one block DOWN)");
        if (r.stairs != EXPECTED_STAIR_COUNT) say(s, "[DEV51B-R4 HOLD] Saved R3 stair restoration incomplete: "
                + r.stairs + "/6 proved. Review STAIR_RESTORE lines in latest.log.");
        say(s, "[DEV51B-R4 LINK] " + r.pathReason + " length=" + r.pathLength
                + " reachedVillagePath=" + r.pathConnected);
        if (preview) {
            // Every affected position and its exact original/new block is logged,
            // and first 14 are echoed to in-game chat for immediate inspection.
            int i = 0;
            for (Map.Entry<BlockPos, Edit> e : r.edits.entrySet()) {
                Edit edit = e.getValue();
                String description = e.getKey().toShortString() + " " + edit.why
                        + " " + id(edit.before) + " -> " + id(edit.after);
                LOG.info("[TC-DEV51B-R4] PROPOSED {}", description);
                if (i < 14) say(s, "[DEV51B-R4 EDIT] " + description);
                i++;
            }
            if (i > 14) say(s, "[DEV51B-R4] " + (i - 14) + " additional edits in latest.log");
        }
        if (r.unsafe) say(s, "[DEV51B-R4 SAFETY] PROPOSAL UNSAFE, placement disabled");
        if (r.stairLogTruncated || r.sandLogTruncated)
            say(s, "[DEV51B-R4 SAFETY] Scan log capped; no placement for ambiguous large station");
        if (!r.pathConnected) say(s, "[DEV51B-R4 WARNING] R3 village path cells did not match saved state; no placement.");
        say(s, "[DEV51B-R4 GUARD] Six exact R3 Y67->Y66 reversals required; grade only supported natural sand at Y66/Y67.");
        LOG.info("[TC-DEV51B-R4] {} stairs={} sand={} path={} connected={} pathReason={} edits={} unsafe={}",
                preview ? "PREVIEW" : "SURVEY", r.stairs, r.sand, r.pathTiles,
                r.pathConnected, r.pathReason, r.edits.size(), r.unsafe);
    }

    private static long rails(ServerLevel l) {
        long hash = 1469598103934665603L;
        for (int z = 3140; z <= 3188; z++) {
            BlockPos pos = new BlockPos(89, 67, z);
            if (!l.hasChunkAt(pos)) return Long.MIN_VALUE;
            hash = (hash ^ Block.getId(l.getBlockState(pos))) * 1099511628211L;
        }
        return hash;
    }

    private static int railCount(ServerLevel l) {
        int n = 0;
        for (int z = 3140; z <= 3188; z++) {
            BlockPos pos = new BlockPos(89, 67, z);
            if (l.hasChunkAt(pos) && isRail(l.getBlockState(pos))) n++;
        }
        return n;
    }

    private static long regionFingerprint(ServerLevel l) {
        long h = 1469598103934665603L;
        for (int x = 92; x <= 113; x++) for (int z = 3142; z <= 3182; z++)
            for (int y = 63; y <= 72; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!l.hasChunkAt(p)) return Long.MIN_VALUE;
                h = (h ^ Block.getId(l.getBlockState(p))) * 1099511628211L;
            }
        return h;
    }

    private static ResourceLocation id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean isSand(BlockState s) {
        return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND);
    }

    private static boolean isRail(BlockState s) {
        String p = id(s).getPath();
        return s.is(BlockTags.RAILS) || p.contains("track") || p.contains("rail");
    }

    private static void say(CommandSourceStack s, String msg) {
        s.sendSuccess(() -> Component.literal(msg), false);
    }

    private static final class Edit {
        final BlockState before, after;
        final String why;
        Edit(BlockState before, BlockState after, String why) {
            this.before = before;
            this.after = after;
            this.why = why;
        }
    }

    private static final class Report {
        final Map<BlockPos, Edit> edits = new LinkedHashMap<>();
        final List<String> stairPositions = new ArrayList<>();
        boolean unsafe = false, pathConnected = false;
        int stairs = 0, sand = 0, pathTiles = 0, pathLength = 0;
        int stairFound = 0, stairLogged = 0, sandBlocked = 0, sandLogged = 0, sandContacts = 0;
        boolean stairLogTruncated = false, sandLogTruncated = false;
        String pathReason = "NOT_EVALUATED";
    }

    private static final class Plan {
        final String dim;
        final long tick, rails, region;
        final Map<BlockPos, Edit> edits;
        Plan(String dim, long tick, long rails, long region, Map<BlockPos, Edit> edits) {
            this.dim = dim;
            this.tick = tick;
            this.rails = rails;
            this.region = region;
            this.edits = edits;
        }
    }
}
