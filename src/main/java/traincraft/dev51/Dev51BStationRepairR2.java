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
 * DEV51B R2 - multi-elevation stair discovery and conservative station-sand cleanup.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Every edit is shown in an explicit preview and verified again on confirmation.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationRepairR2 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R2");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 96;
    private static final int STAIR_LOG_LIMIT = 120;
    private static final int SAND_LOG_LIMIT = 120;
    private static final int PATH_LIMIT = 36;
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationRepairR2() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b2")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R2] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R2] /tcdev51b2 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R2] REPORT FIRST: do not confirm until preview and backup reviewed.");
        say(s, "[DEV51B-R2] DEV50E6-only, coordinate guarded. Preview and inspect before confirm.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R2 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R2 STOP] In-game operator required");
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
            say(s, "[DEV51B-R2 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R2 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R2 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R2 STOP] Previous DEV51B-R2 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R2 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R2] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R1 found no stairs. R2 must NOT offer confirmation for path-only edits.
        if (report.stairs == 0) {
            say(s, "[DEV51B-R2 HOLD] No verified buried stair lift; placement disabled. Send the survey log.");
            return 0;
        }
        if (!report.pathConnected) {
            say(s, "[DEV51B-R2 HOLD] Village connection not verified; placement disabled.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R2 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R2 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b2 station place confirm");
        LOG.info("[TC-DEV51B-R2] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R2 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R2 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
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
            say(s, "[DEV51B-R2 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R2] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R2 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b2 station undo");
        say(s, "[DEV51B-R2] Walk from platform to village and visually inspect raised stairs and sand clearance.");
        LOG.info("[TC-DEV51B-R2] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R2 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R2 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R2 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R2] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R2 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R2] UNDO PASS restored={}", restored);
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
        scanSand(l, r);
        planPath(l, r);
        return r;
    }

    private static void scanStairs(ServerLevel l, Report r) {
        // R1 checked ONLY y=66. R2 discovers stair blocks from y=63..69 and
        // logs non-eligible stairs as well. Do not infer heights from the video.
        // Exclude rail and locomotive infrastructure at x<=91.
        for (int x = 92; x <= 112; x++) for (int z = 3147; z <= 3181; z++)
            for (int y = 63; y <= 69; y++) {
                BlockPos lower = new BlockPos(x, y, z);
                if (!l.hasChunkAt(lower)) continue;
                BlockState stair = l.getBlockState(lower);
                if (!(stair.getBlock() instanceof StairBlock)) continue;
                r.stairFound++;
                BlockPos upper = lower.above();
                BlockPos lowerSupport = lower.below();
                int sandBesideUpper = 0;
                int sandBesideLower = 0;
                boolean neighborUnloaded = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos sideUp = upper.relative(d), sideLow = lower.relative(d);
                    if (!loaded(l, sideUp, sideLow)) { neighborUnloaded = true; break; }
                    if (isSand(l.getBlockState(sideUp))) sandBesideUpper++;
                    if (isSand(l.getBlockState(sideLow))) sandBesideLower++;
                }
                String reason = "NOT_BURIED";
                BlockState backing = stairBacking(stair);
                if (!loaded(l, upper, upper.above(), upper.above(2), lowerSupport) || neighborUnloaded) {
                    reason = "UNLOADED_NEIGHBOR";
                } else if (y < 64 || y > 67 || x > 109 || z < 3151 || z > 3177) {
                    reason = "OUTSIDE_CONSERVATIVE_LIFT_ZONE";
                } else if (backing == null || !stair.getFluidState().isEmpty()) {
                    reason = "UNKNOWN_STAIR_MATERIAL_OR_FLUID";
                } else if (l.getBlockEntity(lower) != null || l.getBlockEntity(upper) != null) {
                    reason = "BLOCK_ENTITY_PROTECTED";
                } else if (!l.getBlockState(lowerSupport).isFaceSturdy(l, lowerSupport, Direction.UP)) {
                    reason = "NO_SUPPORTED_FOUNDATION";
                } else {
                    BlockState occupied = l.getBlockState(upper);
                    boolean buried = (isSand(occupied) && sandBesideUpper >= 1)
                            || (occupied.isAir() && sandBesideUpper >= 2);
                    if (!buried) {
                        reason = "INSUFFICIENT_SAND_BURIAL_EVIDENCE";
                    } else if (!occupied.isAir() && !isSand(occupied)) {
                        reason = "LIFT_DESTINATION_OCCUPIED";
                    } else if (!l.getBlockState(upper.above()).isAir()
                            || !l.getBlockState(upper.above(2)).isAir()) {
                        reason = "FALLING_SAND_OR_OVERHEAD_OBSTRUCTION";
                    } else {
                        reason = "VERIFIED_LIFT_CANDIDATE";
                        // Preserve the EXACT old stair state, including facing, half,
                        // shape and waterlogged state (fluids are excluded above).
                        boolean a = add(r, l, lower, backing, "STAIR_SUPPORT_R2");
                        boolean b = add(r, l, upper, stair, "RAISED_STAIR_R2");
                        if (a && b) {
                            r.stairs++;
                            r.stairPositions.add(lower.toShortString() + " -> " + upper.toShortString()
                                    + " stair=" + id(stair) + " upperSandNeighbors=" + sandBesideUpper);
                        } else { reason = "EDIT_CONFLICT"; }
                    }
                }
                if (r.stairLogged < STAIR_LOG_LIMIT) {
                    LOG.info("[TC-DEV51B-R2] STAIR_SCAN pos={} type={} state={} upperSand={} lowerSand={} reason={}",
                            lower.toShortString(), id(stair), stair.toString(),
                            sandBesideUpper, sandBesideLower, reason);
                    r.stairLogged++;
                } else { r.stairLogTruncated = true; }
            }
        if (r.stairs > 10 || r.stairLogTruncated) r.unsafe = true;
    }

    private static BlockState stairBacking(BlockState stair) {
        // Exact vanilla identities only: do not silently replace modded,
        // mossy or red/smooth stairs with an incorrect support material.
        if (stair.is(Blocks.STONE_BRICK_STAIRS)) return Blocks.STONE_BRICKS.defaultBlockState();
        if (stair.is(Blocks.MOSSY_STONE_BRICK_STAIRS)) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (stair.is(Blocks.SANDSTONE_STAIRS)) return Blocks.SANDSTONE.defaultBlockState();
        if (stair.is(Blocks.SMOOTH_SANDSTONE_STAIRS)) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if (stair.is(Blocks.RED_SANDSTONE_STAIRS)) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (stair.is(Blocks.SMOOTH_RED_SANDSTONE_STAIRS)) return Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        if (stair.is(Blocks.COBBLESTONE_STAIRS)) return Blocks.COBBLESTONE.defaultBlockState();
        if (stair.is(Blocks.MOSSY_COBBLESTONE_STAIRS)) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (stair.is(Blocks.ACACIA_STAIRS)) return Blocks.ACACIA_PLANKS.defaultBlockState();
        if (stair.is(Blocks.POLISHED_ANDESITE_STAIRS)) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        return null; // unknown stairs: log geometry, NEVER move blindly
    }

    private static void scanSand(ServerLevel l, Report r) {
        // R2 scans manufactured station flooring at multiple elevations.
        // A sand block is only auto-cleared when it is directly on approved
        // manufactured floor with clear unsupported-sand constraints. Natural
        // dunes next to walls are LOGGED, not automatically excavated.
        for (int x = 92; x <= 111; x++) for (int z = 3149; z <= 3179; z++)
            for (int y = 63; y <= 69; y++) {
                BlockPos floor = new BlockPos(x, y, z);
                BlockPos feet = floor.above(), head = feet.above(), cap = head.above();
                if (!loaded(l, floor, feet, head, cap)) continue;
                BlockState base = l.getBlockState(floor);
                if (!manufacturedFloor(l, floor, base)) continue;
                BlockState bFeet = l.getBlockState(feet), bHead = l.getBlockState(head);
                if (!isSand(bFeet) && !isSand(bHead)) continue;
                String reason;
                if (isSand(l.getBlockState(cap))) {
                    reason = "DEEP_SAND_COLUMN_UNSAFE";
                    r.sandBlocked++;
                } else if (r.edits.containsKey(feet) || r.edits.containsKey(head)) {
                    reason = "STAIR_MOVE_ALREADY_PLANNED";
                } else if ((isSand(bHead) && !isSand(bFeet) && !bFeet.isAir())) {
                    reason = "NON_SAND_BLOCK_UNDER_SAND";
                    r.sandBlocked++;
                } else {
                    reason = "APPROVED_FLOOR_CLEARANCE";
                    if (isSand(bHead) && add(r, l, head, Blocks.AIR.defaultBlockState(), "STATION_SAND_R2")) r.sand++;
                    if (isSand(bFeet) && add(r, l, feet, Blocks.AIR.defaultBlockState(), "STATION_SAND_R2")) r.sand++;
                }
                if (r.sandLogged < SAND_LOG_LIMIT) {
                    LOG.info("[TC-DEV51B-R2] SAND_SCAN floor={} floorType={} feet={} head={} reason={}",
                            floor.toShortString(), id(base), id(bFeet), id(bHead), reason);
                    r.sandLogged++;
                } else { r.sandLogTruncated = true; }
            }
        if (r.sandLogTruncated) r.unsafe = true;
        // Record candidate natural sand beside the structure without planning
        // changes to it: these may be terrain-support blocks or dunes.
        int contact = 0;
        for (int x = 93; x <= 111; x++) for (int z = 3150; z <= 3178; z++)
            for (int y = 64; y <= 69; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!l.hasChunkAt(p) || !isSand(l.getBlockState(p))) continue;
                boolean touches = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (l.hasChunkAt(n) && manufacturedFloor(l, n, l.getBlockState(n))) {
                        touches = true; break;
                    }
                }
                if (touches) {
                    r.sandContacts++;
                    if (contact++ < 65) LOG.info("[TC-DEV51B-R2] SAND_CONTACT readOnlyPos={} adjacentToManufacturedBlock=true NO_EDIT", p.toShortString());
                }
            }
    }

    private static boolean manufacturedFloor(ServerLevel l, BlockPos floor, BlockState s) {
        if (s.isAir() || isSand(s) || isRail(s) || l.getBlockEntity(floor) != null
                || !s.isFaceSturdy(l, floor, Direction.UP)) return false;
        String path = id(s).getPath();
        return path.contains("stone_brick") || path.contains("cobblestone")
                || path.contains("sandstone") || path.equals("stone")
                || path.contains("acacia_planks") || path.contains("polished_andesite");
    }

    private static void planPath(ServerLevel l, Report r) {
        // Search only existing supported walkable terrain, and end only at an
        // actual vanilla village dirt-path block. No tunnel carving or bridges.
        BlockPos start = new BlockPos(94, 66, 3150);
        if (!walkable(l, start)) {
            r.pathReason = "STATION_END_NOT_OPEN_AT_94_66_3150";
            return;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        BlockPos goal = null;
        int[] dx = {1, 0, -1, 0};
        int[] dz = {0, -1, 0, 1};
        while (!queue.isEmpty() && visited.size() < 3500) {
            BlockPos cur = queue.removeFirst();
            if (!cur.equals(start) && l.getBlockState(cur).is(Blocks.DIRT_PATH)) {
                goal = cur;
                break;
            }
            for (int k = 0; k < 4; k++) {
                int x = cur.getX() + dx[k], z = cur.getZ() + dz[k];
                if (x < 92 || x > 127 || z < 3136 || z > 3192) continue;
                for (int delta : new int[]{0, 1, -1}) {
                    int y = cur.getY() + delta;
                    if (y < 63 || y > 69) continue;
                    BlockPos next = new BlockPos(x, y, z);
                    if (visited.contains(next) || !walkable(l, next)) continue;
                    // Up-one requires safe stepping headroom on the old column.
                    if (delta == 1 && !clearHead(l, cur.above(3))) continue;
                    visited.add(next);
                    parent.put(next, cur);
                    queue.addLast(next);
                }
            }
        }
        if (goal == null) {
            r.pathReason = "NO_LOADED_SAFE_ROUTE_TO_EXISTING_VILLAGE_PATH";
            return;
        }
        List<BlockPos> route = new ArrayList<>();
        for (BlockPos at = goal; at != null; at = parent.get(at)) route.add(at);
        Collections.reverse(route);
        if (route.size() - 1 > PATH_LIMIT) {
            r.pathReason = "VILLAGE_PATH_BEYOND_36_STEPS";
            return;
        }
        // A path can traverse existing station hardscape. Only touch natural
        // grass/dirt or sand floors. Never replace blocks from another mod.
        int editsBefore = r.edits.size();
        for (int i = 1; i < route.size() - 1; i++) {
            BlockPos pos = route.get(i);
            if (r.edits.containsKey(pos)) continue;
            BlockState current = l.getBlockState(pos);
            BlockState replacement = null;
            if (current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.DIRT)
                    || current.is(Blocks.COARSE_DIRT) || current.is(Blocks.PODZOL))
                replacement = Blocks.DIRT_PATH.defaultBlockState();
            else if (isSand(current))
                replacement = current.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState()
                        : Blocks.SANDSTONE.defaultBlockState();
            if (replacement != null && !add(r, l, pos, replacement, "VILLAGE_LINK")) {
                r.pathReason = "PROTECTED_PATH_EDIT_CONFLICT";
                return;
            }
        }
        r.pathConnected = true;
        r.pathTiles = r.edits.size() - editsBefore;
        r.pathReason = "REACHED_EXISTING_DIRT_PATH_AT_" + goal.toShortString();
        r.pathLength = route.size() - 1;
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
        say(s, "[DEV51B-R2 " + (preview ? "PREVIEW" : "SURVEY") + "] raisedStairs=" + r.stairs
                + " sandToAir=" + r.sand + " villagePathTiles=" + r.pathTiles
                + " totalEdits=" + r.edits.size());
        say(s, "[DEV51B-R2 SCAN] discoveredStairs=" + r.stairFound + " sandContactsReadOnly="
                + r.sandContacts + " ambiguousSandColumns=" + r.sandBlocked);
        for (String pos : r.stairPositions) say(s, "[DEV51B-R2 STAIR] " + pos + " (one block UP)");
        if (r.stairs == 0) say(s, "[DEV51B-R2 HOLD] No lift proved. Review STAIR_SCAN lines in latest.log.");
        say(s, "[DEV51B-R2 LINK] " + r.pathReason + " length=" + r.pathLength
                + " reachedVillagePath=" + r.pathConnected);
        if (preview) {
            // Every affected position and its exact original/new block is logged,
            // and first 14 are echoed to in-game chat for immediate inspection.
            int i = 0;
            for (Map.Entry<BlockPos, Edit> e : r.edits.entrySet()) {
                Edit edit = e.getValue();
                String description = e.getKey().toShortString() + " " + edit.why
                        + " " + id(edit.before) + " -> " + id(edit.after);
                LOG.info("[TC-DEV51B-R2] PROPOSED {}", description);
                if (i < 14) say(s, "[DEV51B-R2 EDIT] " + description);
                i++;
            }
            if (i > 14) say(s, "[DEV51B-R2] " + (i - 14) + " additional edits in latest.log");
        }
        if (r.unsafe) say(s, "[DEV51B-R2 SAFETY] PROPOSAL UNSAFE, placement disabled");
        if (r.stairLogTruncated || r.sandLogTruncated)
            say(s, "[DEV51B-R2 SAFETY] Scan log capped; no placement for ambiguous large station");
        if (!r.pathConnected) say(s, "[DEV51B-R2 WARNING] No complete village link planned; do not treat this as a connected route.");
        LOG.info("[TC-DEV51B-R2] {} stairs={} sand={} path={} connected={} pathReason={} edits={} unsafe={}",
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
