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
 * DEV51B R3 - six exact stair height checks and surface-only station sand clearance.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Every edit is shown in an explicit preview and verified again on confirmation.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationRepairR3 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R3");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 80;
    private static final int STAIR_LOG_LIMIT = 12;
    private static final int SAND_LOG_LIMIT = 110;
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
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationRepairR3() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b3")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R3] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R3] /tcdev51b3 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R3] REPORT FIRST: do not confirm until preview and backup reviewed.");
        say(s, "[DEV51B-R3] DEV50E6-only, coordinate guarded. Preview and inspect before confirm.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R3 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R3 STOP] In-game operator required");
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
            say(s, "[DEV51B-R3 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R3 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R3 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R3 STOP] Previous DEV51B-R3 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R3 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R3] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R2 found six exact stairs but zero approved lifts. R3 requires all six
        // exact +1 moves; no partial stair group placement is permitted.
        if (report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R3 HOLD] Require six verified +1 stair moves. Approved=" + report.stairs
                    + " of six. No partial stair relocation or sand/path changes allowed.");
            return 0;
        }
        if (!report.pathConnected || report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R3 HOLD] Village connection not verified; placement disabled.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R3 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R3 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b3 station place confirm");
        LOG.info("[TC-DEV51B-R3] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R3 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R3 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
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
            say(s, "[DEV51B-R3 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R3] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R3 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b3 station undo");
        say(s, "[DEV51B-R3] Walk from platform to village and visually inspect raised stairs and sand clearance.");
        LOG.info("[TC-DEV51B-R3] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R3 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R3 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R3 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R3] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R3 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R3] UNDO PASS restored={}", restored);
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
        // EXACT coordinates were read from R2 runtime logs, not inferred from
        // visual footage. No nearby/foreign stairs will be moved by this patch.
        // A lift is accepted only when the old stair state, landing/support,
        // replacement air, two-block headroom and walk surface can be proved.
        // Snapshot the observations as a complete line for each stair.
        for (int i = 0; i < KNOWN_STAIRS.length; i++) {
            BlockPos lower = KNOWN_STAIRS[i];
            BlockPos upper = lower.above();
            BlockPos foundation = lower.below();
            BlockPos secondHead = upper.above();
            BlockPos thirdHead = secondHead.above();
            String reason = "UNSET";
            BlockState stair = Blocks.AIR.defaultBlockState();
            BlockState oldDestination = Blocks.AIR.defaultBlockState();
            int neighborPlatformY67 = 0;
            int neighborWalkY66 = 0;
            int neighborSandY67 = 0;
            StringBuilder neighborDetails = new StringBuilder();
            if (!loaded(l, lower, upper, foundation, secondHead, thirdHead)) {
                reason = "STAIR_OR_DESTINATION_UNLOADED";
                r.unsafe = true;
            } else {
                stair = l.getBlockState(lower);
                oldDestination = l.getBlockState(upper);
                if (stair.getBlock() instanceof StairBlock) r.stairFound++;
                boolean neighborsLoaded = true;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos p66 = lower.relative(side);
                    BlockPos p67 = upper.relative(side);
                    BlockPos p68 = p67.above();
                    BlockPos p69 = p68.above();
                    if (!loaded(l, p66, p67, p68, p69)) {
                        neighborsLoaded = false;
                        neighborDetails.append(side.getName()).append(":UNLOADED;");
                        continue;
                    }
                    BlockState b66 = l.getBlockState(p66);
                    BlockState b67 = l.getBlockState(p67);
                    BlockState b68 = l.getBlockState(p68);
                    BlockState b69 = l.getBlockState(p69);
                    boolean platform67 = manufacturedFloor(l, p67, b67)
                            && b68.isAir() && b69.isAir();
                    boolean walk66 = !b66.isAir() && !isRail(b66)
                            && b66.isFaceSturdy(l, p66, Direction.UP)
                            && b67.isAir() && b68.isAir();
                    if (platform67) neighborPlatformY67++;
                    if (walk66) neighborWalkY66++;
                    if (isSand(b67)) neighborSandY67++;
                    neighborDetails.append(side.getName()).append("[66=")
                            .append(id(b66)).append(",67=").append(id(b67))
                            .append(",68=").append(id(b68)).append(",69=")
                            .append(id(b69)).append("];");
                }
                BlockState backing = stairBacking(stair);
                if (!neighborsLoaded) reason = "UNLOADED_ADJACENT_COLUMN";
                else if (!(stair.getBlock() instanceof StairBlock)
                        || !stair.hasProperty(StairBlock.FACING)
                        || stair.getValue(StairBlock.FACING) != EXPECTED_FACING[i]) {
                    reason = "EXPECTED_SANDSTONE_STAIR_FACING_MISMATCH";
                } else if (!stair.is(Blocks.SANDSTONE_STAIRS)) {
                    reason = "STAIR_MATERIAL_NOT_EXPECTED_SANDSTONE";
                } else if (backing == null || !stair.getFluidState().isEmpty()) {
                    reason = "UNSUPPORTED_STAIR_OR_FLUID";
                } else if (l.getBlockEntity(lower) != null || l.getBlockEntity(upper) != null) {
                    reason = "BLOCK_ENTITY_PROTECTED";
                } else if (!oldDestination.isAir()) {
                    reason = "LIFT_DESTINATION_NOT_AIR";
                } else if (!l.getBlockState(secondHead).isAir()
                        || !l.getBlockState(thirdHead).isAir()) {
                    reason = "OVERHEAD_NOT_AIR_TWO_BLOCK_HEADROOM";
                } else if (!l.getBlockState(foundation).isFaceSturdy(l, foundation, Direction.UP)
                        || l.getBlockEntity(foundation) != null) {
                    reason = "STANDING_STAIR_HAS_NO_SAFE_FOUNDATION";
                } else if (neighborPlatformY67 == 0 && neighborWalkY66 == 0) {
                    // Require an adjacent supported approach at Y66 or a proven
                    // manufactured platform at Y67. A raised half-step can
                    // meet a Y66 approach without a full-block jump.
                    reason = "NO_ADJACENT_WALKABLE_STATION_APPROACH_Y66_OR_Y67";
                } else {
                    // Preserve full block state (facing/half/shape); old lower
                    // position becomes matching solid backing. Insertion order
                    // guarantees lower support is installed before raised stair.
                    if (add(r, l, lower, backing, "EXACT_STAIR_SUPPORT_R3")
                            && add(r, l, upper, stair, "EXACT_STAIR_LIFT_R3")) {
                        reason = "VERIFIED_ONE_BLOCK_LIFT";
                        r.stairs++;
                        r.stairPositions.add(lower.toShortString() + " -> "
                                + upper.toShortString() + " facing="
                                + EXPECTED_FACING[i].getName() + " preserved=true");
                    } else reason = "STAIR_EDIT_CONFLICT";
                }
                if (reason.startsWith("UNLOADED") || reason.equals("STAIR_EDIT_CONFLICT")) r.unsafe = true;
            }
            LOG.info("[TC-DEV51B-R3] STAIR_EXACT index={} pos={} target={} before={} destination={} "
                            + "facingExpected={} platformY67={} lowerWalkY66={} sandY67={} reason={} neighbors={}",
                    i, lower.toShortString(), upper.toShortString(), stair.toString(),
                    oldDestination.toString(), EXPECTED_FACING[i].getName(), neighborPlatformY67,
                    neighborWalkY66, neighborSandY67, reason, neighborDetails.toString());
            r.stairLogged++;
        }
        if (r.stairFound != EXPECTED_STAIR_COUNT) {
            r.unsafe = true;
            LOG.warn("[TC-DEV51B-R3] STAIR_EXPECTED_MISMATCH found={} expected=6, placement disabled", r.stairFound);
        }
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
        // R2 preview included sand at Y64 and Y65 on deep structural floors,
        // which could produce buried voids or collapses. R3 NEVER clears a
        // sand block below Y67. Sand near walls and all deep columns are
        // diagnostics only. Surface sand directly over verified manufactured
        // station floors is considered if its headspace is clear.
        for (int x = 92; x <= 111; x++) for (int z = 3149; z <= 3179; z++)
            for (int y = 63; y <= 70; y++) {
                BlockPos floor = new BlockPos(x, y, z);
                BlockPos sandPos = floor.above();
                BlockPos cap = sandPos.above();
                if (!loaded(l, floor, sandPos, cap)) continue;
                BlockState base = l.getBlockState(floor);
                if (!manufacturedFloor(l, floor, base)) continue;
                BlockState top = l.getBlockState(sandPos);
                if (!isSand(top)) continue;
                String reason;
                if (sandPos.getY() < 67) {
                    reason = "DEEP_SAND_UNDER_SURFACE_NO_EDIT";
                    r.sandBlocked++;
                } else if (r.edits.containsKey(sandPos) || r.edits.containsKey(floor)) {
                    reason = "STAIR_DESTINATION_OR_SUPPORT_NO_EDIT";
                } else if (!l.getBlockState(cap).isAir()) {
                    reason = "SAND_COLUMN_ABOVE_NO_EDIT";
                    r.sandBlocked++;
                } else if (l.getBlockEntity(sandPos) != null) {
                    reason = "BLOCK_ENTITY_NO_EDIT";
                    r.unsafe = true;
                } else {
                    boolean clearLanding = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos side = sandPos.relative(d);
                        if (!l.hasChunkAt(side)) continue;
                        // Air next to the exposed sand and a solid factory-built
                        // floor below is proof this is surface obstruction,
                        // not load-bearing natural terrain.
                        if (l.getBlockState(side).isAir()) { clearLanding = true; break; }
                    }
                    if (!clearLanding) {
                        reason = "ENCLOSED_OR_POSSIBLY_SUPPORTING_SAND_NO_EDIT";
                        r.sandBlocked++;
                    } else if (add(r, l, sandPos, Blocks.AIR.defaultBlockState(), "SURFACE_STATION_SAND_R3")) {
                        reason = "PROPOSED_EXPOSE_MANUFACTURED_SURFACE";
                        r.sand++;
                    } else reason = "PROTECTED_SAND_EDIT_CONFLICT";
                }
                if (r.sandLogged < SAND_LOG_LIMIT) {
                    LOG.info("[TC-DEV51B-R3] SAND_FLOOR floor={} base={} sand={} "
                                    + "cap={} reason={}", floor.toShortString(), id(base),
                            sandPos.toShortString(), id(l.getBlockState(cap)), reason);
                    r.sandLogged++;
                } else r.sandLogTruncated = true;
            }
        // Expose exact read-only sand contacts to distinguish surface dune
        // support vs station covering, but do not excavate sand against walls.
        int contactLog = 0;
        for (int x = 93; x <= 111; x++) for (int z = 3150; z <= 3178; z++)
            for (int y = 64; y <= 71; y++) {
                BlockPos sand = new BlockPos(x, y, z);
                if (!l.hasChunkAt(sand) || !isSand(l.getBlockState(sand))) continue;
                boolean touches = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos neighbor = sand.relative(d);
                    if (l.hasChunkAt(neighbor)
                            && manufacturedFloor(l, neighbor, l.getBlockState(neighbor))) {
                        touches = true; break;
                    }
                }
                if (touches) {
                    r.sandContacts++;
                    if (contactLog++ < 100) LOG.info("[TC-DEV51B-R3] SAND_WALL_CONTACT pos={} "
                            + "adjacentToStation=true edit=NONE", sand.toShortString());
                }
            }
        if (r.sandLogTruncated) r.unsafe = true;
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
        say(s, "[DEV51B-R3 " + (preview ? "PREVIEW" : "SURVEY") + "] raisedStairs=" + r.stairs
                + " sandToAir=" + r.sand + " villagePathTiles=" + r.pathTiles
                + " totalEdits=" + r.edits.size());
        say(s, "[DEV51B-R3 SCAN] discoveredStairs=" + r.stairFound + " sandContactsReadOnly="
                + r.sandContacts + " sandColumnsHeld=" + r.sandBlocked);
        for (String pos : r.stairPositions) say(s, "[DEV51B-R3 STAIR] " + pos + " (one block UP)");
        if (r.stairs != EXPECTED_STAIR_COUNT) say(s, "[DEV51B-R3 HOLD] Stair alignment incomplete: "
                + r.stairs + "/6 proved. Review STAIR_EXACT lines in latest.log.");
        say(s, "[DEV51B-R3 LINK] " + r.pathReason + " length=" + r.pathLength
                + " reachedVillagePath=" + r.pathConnected);
        if (preview) {
            // Every affected position and its exact original/new block is logged,
            // and first 14 are echoed to in-game chat for immediate inspection.
            int i = 0;
            for (Map.Entry<BlockPos, Edit> e : r.edits.entrySet()) {
                Edit edit = e.getValue();
                String description = e.getKey().toShortString() + " " + edit.why
                        + " " + id(edit.before) + " -> " + id(edit.after);
                LOG.info("[TC-DEV51B-R3] PROPOSED {}", description);
                if (i < 14) say(s, "[DEV51B-R3 EDIT] " + description);
                i++;
            }
            if (i > 14) say(s, "[DEV51B-R3] " + (i - 14) + " additional edits in latest.log");
        }
        if (r.unsafe) say(s, "[DEV51B-R3 SAFETY] PROPOSAL UNSAFE, placement disabled");
        if (r.stairLogTruncated || r.sandLogTruncated)
            say(s, "[DEV51B-R3 SAFETY] Scan log capped; no placement for ambiguous large station");
        if (!r.pathConnected) say(s, "[DEV51B-R3 WARNING] No complete village link planned; do not treat this as a connected route.");
        say(s, "[DEV51B-R3 GUARD] Six exact Y66->Y67 stair moves required; deep sand under Y67 never excavated.");
        LOG.info("[TC-DEV51B-R3] {} stairs={} sand={} path={} connected={} pathReason={} edits={} unsafe={}",
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
