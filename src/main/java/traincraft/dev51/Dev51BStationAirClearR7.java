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
 * DEV51B R7 - saved-R6 station-underfloor / path-shoulder natural sand -> air clearing.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Only previews exact natural sand removal above verified supported lower ground; never edits stairs, rail, path, wood, or station flooring.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationAirClearR7 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R7");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 320;
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
    // Exact six tile path built by the R3 20-edit placement (2026-10-13 08:37:45).
    // R5 MUST preserve these, and refuse a partially reverted/foreign world.
    private static final BlockPos[] R3_PATH = {
            new BlockPos(94, 66, 3149), new BlockPos(94, 66, 3148),
            new BlockPos(94, 66, 3147), new BlockPos(94, 66, 3146),
            new BlockPos(94, 65, 3145), new BlockPos(94, 65, 3144)
    };
    private static final BlockPos[] R3_SAND_CLEARED = {
            new BlockPos(95, 67, 3163), new BlockPos(95, 67, 3165)
    };
    // Exact top-sand positions cleared by the successful DEV51B R4 46-edit placement.
    // Saved R4 postimages at ALL 34 positions must remain air for R5 placement.
    private static final BlockPos[] R4_CLEARED = {
            new BlockPos(93, 66, 3174),
            new BlockPos(94, 66, 3174),
            new BlockPos(95, 67, 3170),
            new BlockPos(95, 67, 3171),
            new BlockPos(95, 67, 3174),
            new BlockPos(95, 67, 3175),
            new BlockPos(96, 67, 3174),
            new BlockPos(99, 67, 3171),
            new BlockPos(99, 67, 3174),
            new BlockPos(100, 67, 3170),
            new BlockPos(100, 67, 3171),
            new BlockPos(100, 67, 3174),
            new BlockPos(100, 67, 3175),
            new BlockPos(101, 66, 3167),
            new BlockPos(101, 66, 3171),
            new BlockPos(101, 66, 3174),
            new BlockPos(102, 66, 3161),
            new BlockPos(102, 66, 3162),
            new BlockPos(102, 66, 3163),
            new BlockPos(102, 66, 3164),
            new BlockPos(102, 66, 3165),
            new BlockPos(102, 66, 3166),
            new BlockPos(102, 66, 3167),
            new BlockPos(102, 66, 3168),
            new BlockPos(102, 66, 3170),
            new BlockPos(102, 66, 3171),
            new BlockPos(102, 66, 3174),
            new BlockPos(103, 66, 3174),
            new BlockPos(105, 66, 3162),
            new BlockPos(105, 67, 3163),
            new BlockPos(105, 67, 3164),
            new BlockPos(105, 67, 3165),
            new BlockPos(105, 67, 3166),
            new BlockPos(105, 67, 3167)
    };
    // 15 saved R5 postimages, evidenced by the successful 09:22:48 R5 PLACED log.
    // R6 refuses to operate unless all fifteen exactly match. No same-session undo assumed.
    private static final BlockPos[] R5_POS = {
            new BlockPos(93, 66, 3174),
            new BlockPos(94, 66, 3174),
            new BlockPos(101, 66, 3171),
            new BlockPos(101, 66, 3174),
            new BlockPos(102, 66, 3161),
            new BlockPos(102, 66, 3162),
            new BlockPos(102, 66, 3163),
            new BlockPos(102, 66, 3166),
            new BlockPos(102, 66, 3167),
            new BlockPos(102, 66, 3168),
            new BlockPos(102, 66, 3170),
            new BlockPos(102, 66, 3171),
            new BlockPos(102, 66, 3174),
            new BlockPos(103, 66, 3174),
            new BlockPos(105, 66, 3162)
    };
    private static final BlockState[] R5_STATE = {
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SANDSTONE_SLAB.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.SAND.defaultBlockState()
    };
    private static BlockState r5Postimage(BlockPos pos) {
        for (int i=0; i<R5_POS.length; i++) if (R5_POS[i].equals(pos)) return R5_STATE[i];
        return null;
    }
    // Three R6 placed sandstone slabs, verified by 09:55:38 placement log.
    private static final BlockPos[] R6_POS = {
        new BlockPos(102,66,3164), new BlockPos(102,66,3165), new BlockPos(101,66,3167)
    };
    private static BlockState r6Postimage(BlockPos pos) {
        for (BlockPos q : R6_POS) if (q.equals(pos)) return Blocks.SANDSTONE_SLAB.defaultBlockState();
        return null;
    }
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationAirClearR7() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b7")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R7] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R7] /tcdev51b7 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R7] SAVED R6 WORLD: 3 R6 slabs, 15 R5 postimages, 6 stairs, 49 rails and village path frozen.");
        say(s, "[DEV51B-R7] DEV50E6 only. Sand -> air near station and path; preview exact removals FIRST.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R7 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R7 STOP] In-game operator required");
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
            say(s, "[DEV51B-R7 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R7 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R7 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R7 STOP] Previous DEV51B-R7 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R7 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R7] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R7 requires all six original-height Y66 stairs frozen before allowing
        // any grading; no staircase changes.
        if (report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R7 HOLD] Require six verified original-height stairs. Found=" + report.stairs
                    + " of six. No stair changes or partial terrain edits allowed.");
            return 0;
        }
        if (!report.pathConnected || report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R7 HOLD] Existing village path endpoint or six tiles changed; placement disabled.");
            return 0;
        }
        if (report.sand == 0) {
            say(s, "[DEV51B-R7 HOLD] No verified obstructing sand removal; NO WORLD CHANGES.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R7 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R7 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b7 station place confirm");
        LOG.info("[TC-DEV51B-R7] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R7 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R7 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> ordered = new ArrayList<>(plan.edits.entrySet());
        // R7 ONLY removes exposed natural sand down to verified supported ground; no block additions.
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
            say(s, "[DEV51B-R7 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R7] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R7 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b7 station undo");
        say(s, "[DEV51B-R7] Inspect actual station sand clearing, lowered path shoulders and protected stairs/rails.");
        LOG.info("[TC-DEV51B-R7] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R7 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R7 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R7 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R7] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R7 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R7] UNDO PASS restored={}", restored);
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
        // R6 PLACED 3 slabs and user exited. Must find ORIGINAL Y66 stairs,
        // NOT R3 Y67 stairs, plus empty block above each. Absolutely NO edits.
        for (int i = 0; i < KNOWN_STAIRS.length; i++) {
            BlockPos floor = KNOWN_STAIRS[i];
            BlockPos above = floor.above();
            BlockPos base = floor.below();
            BlockState expected = Blocks.SANDSTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, EXPECTED_FACING[i]);
            boolean pass = loaded(l, floor, above, above.above(), base)
                    && l.getBlockState(floor).equals(expected)
                    && l.getBlockState(above).isAir()
                    && l.getBlockState(above.above()).isAir()
                    && l.getBlockState(base).isFaceSturdy(l, base, Direction.UP)
                    && l.getBlockEntity(floor) == null;
            if (pass) { r.stairs++; r.stairFound++; }
            else r.unsafe = true;
            LOG.info("[TC-DEV51B-R7] STAIR_FROZEN index={} pos={} y66={} y67={} expected={} verdict={}",
                    i, floor.toShortString(), l.hasChunkAt(floor) ? l.getBlockState(floor) : "UNLOADED",
                    l.hasChunkAt(above) ? id(l.getBlockState(above)) : "UNLOADED", expected,
                    pass ? "Y66_INTACT_NO_EDIT" : "R6_STAIR_POSTIMAGE_MISMATCH_STOP");
        }
    }

    private static void verifyPathAndR3Clearance(ServerLevel l, Report r) {
        // Never alter the six R3 path tiles, the village endpoint or R3 surface clears.
        for (BlockPos p : R3_PATH) {
            if (!loaded(l,p,p.above(),p.above(2)) || !l.getBlockState(p).is(Blocks.SANDSTONE)
                || !walkable(l,p) || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R3_PATH_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R7] PATH_MISMATCH at={}",p.toShortString()); return;
            }
            r.pathTiles++;
        }
        for (BlockPos p : R3_SAND_CLEARED) {
            if(!loaded(l,p) || !l.getBlockState(p).isAir()) {
                r.unsafe=true; r.pathReason="R3_CLEARANCE_CHANGED_STOP"; return;
            }
        }
        for (BlockPos p : R4_CLEARED) {
            BlockState expected=r6Postimage(p);
            if(expected==null) expected=r5Postimage(p);
            if(expected==null) expected=Blocks.AIR.defaultBlockState();
            if(!loaded(l,p,p.above(),p.below()) || !l.getBlockState(p).equals(expected)
                    || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R4_R5_SAVED_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R7] POSTIMAGE_MISMATCH pos={} expected={} found={}",
                    p.toShortString(),id(expected),l.hasChunkAt(p)?id(l.getBlockState(p)):"UNLOADED"); return;
            }
        }
        for(int i=0;i<R5_POS.length;i++) {
            BlockPos p=R5_POS[i];
            if(!loaded(l,p,p.above(),p.below()) || !l.getBlockState(p).equals(R5_STATE[i])
                    || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R5_PLACED_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R7] R5_MISMATCH {} expected={}",p.toShortString(),id(R5_STATE[i]));return;
            }
        }
        for (BlockPos p : R6_POS) {
            if (!loaded(l,p,p.above(),p.below()) ||
                !l.getBlockState(p).equals(Blocks.SANDSTONE_SLAB.defaultBlockState()) ||
                l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R6_SAVED_THREE_SLAB_POSTIMAGE_STOP";
                LOG.warn("[TC-DEV51B-R7] R6_MISMATCH at={}",p.toShortString()); return;
            }
        }
        BlockPos village=new BlockPos(95,65,3143);
        if(!loaded(l,village,village.above(),village.above(2)) || !l.getBlockState(village).is(Blocks.DIRT_PATH)
           || !walkable(l,village)) {
            r.unsafe=true;r.pathReason="VILLAGE_PATH_ENDPOINT_CHANGED_STOP";return;
        }
        r.pathLength=verifyContinuousVillageRoute(l, new BlockPos(94,66,3149),village);
        r.pathConnected=r.pathLength>0;
        r.pathReason=r.pathConnected?"PATH_BFS_WALKABLE_TO_EXISTING_VILLAGE":"PATH_GAP_NO_VERIFIED_WALK_ROUTE";
        if (!r.pathConnected) r.unsafe=true;
        LOG.info("[TC-DEV51B-R7] ROUTE_CHECK start=94,66,3149 village=95,65,3143 connected={} steps={} preserveTiles={}",
                r.pathConnected,r.pathLength,r.pathTiles);
    }

    // Read-only 4-neighbor stepping path, at most one whole block height change,
    // two-block air headspace and sturdy support. This verifies an actual continuous
    // route rather than simply finding the village endpoint.
    private static int verifyContinuousVillageRoute(ServerLevel l, BlockPos start, BlockPos goal) {
        if(!walkable(l,start) || !walkable(l,goal)) return -1;
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        Map<BlockPos,Integer> distance=new HashMap<>();
        queue.add(start);distance.put(start,0);
        while(!queue.isEmpty() && distance.size()<500) {
            BlockPos current=queue.removeFirst();int depth=distance.get(current);
            if(current.equals(goal))return depth;
            for(Direction dir:Direction.Plane.HORIZONTAL) {
                for(int dy=-1;dy<=1;dy++) {
                    BlockPos n=current.relative(dir).offset(0,dy,0);
                    if(n.getX()<92 || n.getX()>102 || n.getZ()<3142 || n.getZ()>3154
                        || n.getY()<64 || n.getY()>67 || distance.containsKey(n)) continue;
                    if(!loaded(l,n,n.above(),n.above(2)) || !walkable(l,n))continue;
                    distance.put(n,depth+1);queue.add(n);
                }
            }
        }
        return -1;
    }

    // Terrain-first R7: removes sand above a VERIFIED supported surface.
    // No manufactured blocks are replaced by air, nor any foundational Y65 block.
    // Specific physical repair areas: station apron x93..107/z3159..3177 and
    // linked village-approach shoulders x92..97/z3144..3159.
    private static void scanSand(ServerLevel l, Report r) {
        int stationRemoved=0, approachRemoved=0, held=0;
        int raisedTop=0, lowTarget=0, y68Held=0, foundationHeld=0;
        int columns=0;
        for (int z=3144;z<=3177;z++) for (int x=92;x<=107;x++) {
            boolean approach=(z<=3159 && x>=92 && x<=97);
            boolean apron=(z>=3159 && z<=3177 && x>=93 && x<=107);
            if(!approach && !apron)continue;
            BlockPos base=new BlockPos(x,65,z);
            if(!loaded(l,base,base.above(),base.above(2),base.above(3),base.above(4),base.above(5))) {
                r.unsafe=true;LOG.warn("[TC-DEV51B-R7] MISSING_CHUNK x={} z={}",x,z);continue;
            }
            // No rail edit, near-rail edit, or foreign block entity;
            // never excavate under a building post or rail-supporting surface.
            if(nearRail(l,base.above(2)))continue;
            BlockPos top=null;
            for (int y=68;y>=66;y--) {
                BlockPos at=new BlockPos(x,y,z);
                if (!l.getBlockState(at).isAir()) {top=at;break;}
            }
            if(top==null)continue;
            BlockState upper=l.getBlockState(top);
            if(!isSand(upper))continue;
            if(!l.getBlockState(top.above()).isAir() || !l.getBlockState(top.above(2)).isAir()){
                held++;LOG.info("[TC-DEV51B-R7] HOLD_TOP_BLOCKED at={}",top.toShortString());continue;
            }
            int topY=top.getY();
            if(topY==68)raisedTop++;
            boolean pad=manufacturedFloor(l,base,l.getBlockState(base));
            int stairDistance=distanceToStair(top);
            boolean villageShoulder=(approach && z<=3151 && Math.abs(x-94)<=2);
            // Clear to Y65 only near the original Y66 stairs, manufactured
            // station underfloor, or existing low village link. Farther out
            // retain natural Y66 sand to avoid a deep trench in surrounding dunes.
            int targetY=(pad || stairDistance<=3 || villageShoulder)?65:66;
            BlockPos target=new BlockPos(x,targetY,z);
            BlockState foundation=l.getBlockState(target);
            if(targetY==65 && (!foundation.isFaceSturdy(l,target,Direction.UP)
                    || isRail(foundation) || l.getBlockEntity(target)!=null)) {
                held++;foundationHeld++;LOG.info("[TC-DEV51B-R7] HOLD_SUPPORT at={} base={}",top.toShortString(),id(foundation));continue;
            }
            if(targetY==66 && (l.getBlockState(base.above()).isAir() ||
                 !l.getBlockState(base.above()).isFaceSturdy(l,base.above(),Direction.UP)
                 || l.getBlockEntity(base.above())!=null)) {
                held++;foundationHeld++;continue;
            }
            // A tall natural dune requires more than two removals: do NOT
            // leave a vertical cut or hollow out a 3-block column on speculation.
            int depth=topY-targetY;
            if(depth<=0 || depth>2) {if(depth>2){held++;y68Held++;}continue;}
            boolean compatible=true;
            for(int y=targetY+1;y<=topY;y++) {
                BlockPos at=new BlockPos(x,y,z);
                if(!isSand(l.getBlockState(at)) || l.getBlockEntity(at)!=null)compatible=false;
            }
            if(!compatible){held++;continue;}
            // Restrict to the visibly obstructed station and its path shoulders.
            // On outer edges, require proximity to existing station infrastructure
            // or an R4 excavation (to avoid flattening remote dunes).
            if(!pad && !villageShoulder && stairDistance>5 &&
                !closeToKnownGrade(top,3) && !closeToSavedR5(top,3) &&
                !adjacentBuiltSurface(l,top)){
                held++;continue;
            }
            // Verify that exposed ground does not become a floating walkway.
            // Where there is built flooring at Y66, only remove Y67+; never
            // target that sandstone/stair/slab/wood block itself.
            int priorCount=r.edits.size();
            for(int y=topY;y>targetY;y--) {
                BlockPos at=new BlockPos(x,y,z);
                if(!isSand(l.getBlockState(at)) || isRail(l.getBlockState(at)) ||
                   l.getBlockEntity(at)!=null){r.unsafe=true;break;}
                String why=(pad?"EXPOSE_STATION_FLOOR_SAND_TO_AIR":
                    villageShoulder?"LOWER_VILLAGE_PATH_SHOULDER_TO_AIR":
                    (targetY==65?"CLEAR_ENTRANCE_SAND_TO_AIR":"LOWER_HIGH_SAND_BANK_TO_AIR"));
                if(!add(r,l,at,Blocks.AIR.defaultBlockState(),why))r.unsafe=true;
            }
            int delta=r.edits.size()-priorCount;
            if(delta>0){columns++; if(approach)approachRemoved+=delta;else stationRemoved+=delta;
                if(targetY==65)lowTarget+=delta;
                LOG.info("[TC-DEV51B-R7] GRADE_COLUMN area={} x={} z={} naturalTopY={} targetSurfaceBlockY={} edits={} exposed={} stairsDistance={} underfloor={}",
                    approach?"VILLAGE_SHOULDER":"STATION_APRON",x,z,topY,targetY,delta,id(foundation),stairDistance,pad);
            }
        }
        r.sand=r.edits.size();r.sandBlocked=held;
        LOG.info("[TC-DEV51B-R7] AIR_PROFILE_SUMMARY columns={} stationAir={} villageShoulderAir={} totalSandToAir={} loweredToY65={} tallTopY68Seen={} tallHeld={} foundationHeld={} otherHeld={} rails=49",columns,stationRemoved,approachRemoved,r.sand,lowTarget,raisedTop,y68Held,foundationHeld,held);
        // Print a compact after-profile for visibility. No edits are applied.
        for(int z=3160;z<=3177;z++) {
            StringBuilder before=new StringBuilder(),after=new StringBuilder();
            for(int x=93;x<=107;x++){
                int oldY=surfaceY(l,x,z,Collections.emptyMap());
                int newY=surfaceY(l,x,z,toStateMap(r.edits));
                before.append(profileChar(oldY));after.append(profileChar(newY));
            }
            LOG.info("[TC-DEV51B-R7] GRADE_ROW z={} x93..107 BEFORE={} AFTER={} legend:8=Y68 7=Y67 6=Y66 5=Y65 ?=unknown",z,before,after);
        }
        if (r.edits.size()>LIMIT)r.unsafe=true;
    }

    // Horizontal proximity checks used by the R7 air-only grading planner.
    // Do not infer a safe vertical excavation depth from proximity alone:
    // scanSand separately checks the full sand column and supported target.
    private static int horizontalManhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static int distanceToStair(BlockPos pos) {
        int nearest = Integer.MAX_VALUE;
        for (BlockPos stair : KNOWN_STAIRS) {
            nearest = Math.min(nearest, horizontalManhattan(pos, stair));
        }
        return nearest;
    }

    private static boolean closeToKnownGrade(BlockPos pos, int radius) {
        if (radius < 0) return false;
        for (BlockPos cleared : R4_CLEARED) {
            if (horizontalManhattan(pos, cleared) <= radius) return true;
        }
        return false;
    }

    private static boolean closeToSavedR5(BlockPos pos, int radius) {
        if (radius < 0) return false;
        for (BlockPos repaired : R5_POS) {
            if (horizontalManhattan(pos, repaired) <= radius) return true;
        }
        return false;
    }

    private static boolean adjacentBuiltSurface(ServerLevel l, BlockPos pos) {
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            if(Math.abs(dx)+Math.abs(dz)>2)continue;
            for(int y=65;y<=67;y++) {
                BlockPos q=new BlockPos(pos.getX()+dx,y,pos.getZ()+dz);
                if(!l.hasChunkAt(q))continue;
                BlockState s=l.getBlockState(q);
                if(manufacturedFloor(l,q,s) || s.is(Blocks.SANDSTONE_STAIRS))return true;
            }
        }
        return false;
    }
    private static int surfaceY(ServerLevel l,int x,int z,Map<BlockPos,BlockState> staged) {
        for(int y=68;y>=65;y--) {
            BlockPos p=new BlockPos(x,y,z);
            if(!l.hasChunkAt(p))return -1;
            BlockState st=staged.containsKey(p)?staged.get(p):l.getBlockState(p);
            if(!st.isAir())return y;
        }
        return -1;
    }
    private static char profileChar(int y) {return y>=65 && y<=68?(char)('0'+y-60):'?';}
    private static Map<BlockPos,BlockState> toStateMap(Map<BlockPos,Edit> edits) {
        Map<BlockPos,BlockState> out=new HashMap<>();
        for(Map.Entry<BlockPos,Edit> entry:edits.entrySet())out.put(entry.getKey(),entry.getValue().after);
        return out;
    }

    private static boolean isSafeExposedTop(ServerLevel l,BlockPos q,BlockState state) {
        if (state.isAir() || isRail(state) || l.getBlockEntity(q)!=null
                || !state.getFluidState().isEmpty()) return false;
        if (!(state.isFaceSturdy(l,q,Direction.UP) || state.is(Blocks.SANDSTONE_STAIRS))) return false;
        if (!clearHead(l,q.above()) || !clearHead(l,q.above(2))) return false;
        ResourceLocation name=id(state);
        String path=name.getPath();
        return isSand(state) || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SANDSTONE_STAIRS) || path.contains("sandstone");
    }

    private static boolean nearRail(ServerLevel l, BlockPos p) {
        for (int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
          for(int dy=-1;dy<=2;dy++) {
            BlockPos q=p.offset(dx,dy,dz);
            if(!l.hasChunkAt(q) || isRail(l.getBlockState(q))) return true;
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
        say(s,"[DEV51B-R7 "+(preview?"PREVIEW":"SURVEY")+"] originalY66Stairs="+r.stairs
            +" sandToAir="+r.sand+" held="+r.sandBlocked
            +" existingPathTiles="+r.pathTiles+" routeBfs="+r.pathConnected
            +" totalEdits="+r.edits.size());
        say(s,"[DEV51B-R7 GUARD] savedR6Slabs=3/R5Postimages=15/R4Footprint=34 verified; 49 rails; stairs, path, station floor protected.");
        say(s,"[DEV51B-R7 WALK] "+r.pathReason+" steps="+r.pathLength);
        if(preview)for(Map.Entry<BlockPos,Edit> entry:r.edits.entrySet()) {
            BlockPos p=entry.getKey();Edit e=entry.getValue();
            String str=p.toShortString()+" "+e.why+" "+id(e.before)+" -> "+id(e.after);
            LOG.info("[TC-DEV51B-R7] PROPOSED {}",str);
            say(s,"[DEV51B-R7 EDIT] "+str);
        }
        if(r.unsafe) say(s,"[DEV51B-R7 SAFETY STOP] Saved world/route/rails mismatch or >320 edits. No placement.");
        if(r.sand==0)say(s,"[DEV51B-R7 HOLD] No proved high sand to remove; no world changes.");
        LOG.info("[TC-DEV51B-R7] {} stairs={} grade={} held={} path={} walkingRoute={} steps={} edits={} unsafe={}",
            preview?"PREVIEW":"SURVEY",r.stairs,r.sand,r.sandBlocked,r.pathTiles,r.pathConnected,
            r.pathLength,r.edits.size(),r.unsafe);
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
