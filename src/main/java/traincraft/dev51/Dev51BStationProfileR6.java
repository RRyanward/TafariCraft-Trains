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
 * DEV51B R6 - saved-R5 contiguous terrain-height profile around station with protected access route.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Plans coherent surface-height transitions; never edits stair, rail, path, wood, or station flooring.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationProfileR6 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R6");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 56;
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
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationProfileR6() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b6")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R6] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R6] /tcdev51b6 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R6] SAVED R5 WORLD: 15 R5 postimages, six stairs, all 49 rails and village link frozen.");
        say(s, "[DEV51B-R6] DEV50E6 only; read-only continuous surface profile first; keep structures and paths frozen.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R6 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R6 STOP] In-game operator required");
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
            say(s, "[DEV51B-R6 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R6 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R6 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R6 STOP] Previous DEV51B-R6 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R6 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R6] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R6 requires all six original-height Y66 stairs frozen before allowing
        // any grading; no staircase changes.
        if (report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R6 HOLD] Require six verified original-height stairs. Found=" + report.stairs
                    + " of six. No stair changes or partial terrain edits allowed.");
            return 0;
        }
        if (!report.pathConnected || report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R6 HOLD] Existing village path endpoint or six tiles changed; placement disabled.");
            return 0;
        }
        if (report.sand == 0) {
            say(s, "[DEV51B-R6 HOLD] No coherent, supported surface-height repairs proven; NO WORLD CHANGES.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R6 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R6 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b6 station place confirm");
        LOG.info("[TC-DEV51B-R6] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R6 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R6 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> ordered = new ArrayList<>(plan.edits.entrySet());
        // R6 only infills air with natural sand / bottom sandstone slabs; never removes protected floors.
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
            say(s, "[DEV51B-R6 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R6] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R6 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b6 station undo");
        say(s, "[DEV51B-R6] Inspect continuous side approach and natural-sand transition; preserve stairs and village route.");
        LOG.info("[TC-DEV51B-R6] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R6 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R6 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R6 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R6] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R6 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R6] UNDO PASS restored={}", restored);
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
        // R5 PLACED 15 additions and user exited. Must find ORIGINAL Y66 stairs,
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
            LOG.info("[TC-DEV51B-R6] STAIR_FROZEN index={} pos={} y66={} y67={} expected={} verdict={}",
                    i, floor.toShortString(), l.hasChunkAt(floor) ? l.getBlockState(floor) : "UNLOADED",
                    l.hasChunkAt(above) ? id(l.getBlockState(above)) : "UNLOADED", expected,
                    pass ? "Y66_INTACT_NO_EDIT" : "R5_STAIR_POSTIMAGE_MISMATCH_STOP");
        }
    }

    private static void verifyPathAndR3Clearance(ServerLevel l, Report r) {
        // Never alter the six R3 path tiles, the village endpoint or R3 surface clears.
        for (BlockPos p : R3_PATH) {
            if (!loaded(l,p,p.above(),p.above(2)) || !l.getBlockState(p).is(Blocks.SANDSTONE)
                || !walkable(l,p) || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R3_PATH_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R6] PATH_MISMATCH at={}",p.toShortString()); return;
            }
            r.pathTiles++;
        }
        for (BlockPos p : R3_SAND_CLEARED) {
            if(!loaded(l,p) || !l.getBlockState(p).isAir()) {
                r.unsafe=true; r.pathReason="R3_CLEARANCE_CHANGED_STOP"; return;
            }
        }
        for (BlockPos p : R4_CLEARED) {
            BlockState expected=r5Postimage(p);
            if(expected==null) expected=Blocks.AIR.defaultBlockState();
            if(!loaded(l,p,p.above(),p.below()) || !l.getBlockState(p).equals(expected)
                    || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R4_R5_SAVED_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R6] POSTIMAGE_MISMATCH pos={} expected={} found={}",
                    p.toShortString(),id(expected),l.hasChunkAt(p)?id(l.getBlockState(p)):"UNLOADED"); return;
            }
        }
        for(int i=0;i<R5_POS.length;i++) {
            BlockPos p=R5_POS[i];
            if(!loaded(l,p,p.above(),p.below()) || !l.getBlockState(p).equals(R5_STATE[i])
                    || l.getBlockEntity(p)!=null) {
                r.unsafe=true; r.pathReason="R5_PLACED_POSTIMAGE_CHANGED_STOP";
                LOG.warn("[TC-DEV51B-R6] R5_MISMATCH {} expected={}",p.toShortString(),id(R5_STATE[i]));return;
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
        LOG.info("[TC-DEV51B-R6] ROUTE_CHECK start=94,66,3149 village=95,65,3143 connected={} steps={} preserveTiles={}",
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

    private static void scanSand(ServerLevel l, Report r) {
        // Survey a full contiguous station-side surface belt, not only R4 coordinates.
        // Only natural sand-supported Y66 AIR columns within the already disturbed
        // station access zone qualify for fill. R6 never touches Y65 soil, Y67 terrain,
        // track, manufactured floor, station structure, stairs or village path.
        Map<BlockPos,BlockState> staged=new LinkedHashMap<>();
        Map<BlockPos,String> reasons=new LinkedHashMap<>();
        Map<BlockPos,Double> profiles=new LinkedHashMap<>();
        for(int x=93;x<=106;x++) for(int z=3160;z<=3176;z++) {
            BlockPos p=new BlockPos(x,66,z);
            if(!loaded(l,p.below(),p,p.above(),p.above(2))) {
                r.unsafe=true; continue;
            }
            profiles.put(p,topHeight(l,p,staged));
        }
        if(r.unsafe)return;
        // Bridge whole 1-3-cell gaps in one coherent operation. Independent
        // per-cell checks fail when two air cells separate existing slab anchors;
        // both must be considered simultaneously or neither is connected.
        for(int orientation=0;orientation<2;orientation++) {
            int outerStart=orientation==0?93:3160;
            int outerEnd=orientation==0?106:3176;
            int innerStart=orientation==0?3160:93;
            int innerEnd=orientation==0?3176:106;
            for(int outer=outerStart;outer<=outerEnd;outer++) {
                int step=innerStart;
                while(step<=innerEnd) {
                    BlockPos p=orientation==0?new BlockPos(outer,66,step):new BlockPos(step,66,outer);
                    if(!isCandidateAir(l,p) || staged.containsKey(p)) {step++;continue;}
                    int first=step;
                    while(step<=innerEnd) {
                        BlockPos q=orientation==0?new BlockPos(outer,66,step):new BlockPos(step,66,outer);
                        if(!isCandidateAir(l,q) || staged.containsKey(q))break;
                        step++;
                    }
                    int last=step-1, span=last-first+1;
                    if(span>3 || first==innerStart || last==innerEnd)continue;
                    BlockPos before=orientation==0?new BlockPos(outer,66,first-1):new BlockPos(first-1,66,outer);
                    BlockPos after=orientation==0?new BlockPos(outer,66,last+1):new BlockPos(last+1,66,outer);
                    double hb=topHeight(l,before,staged),ha=topHeight(l,after,staged);
                    if(hb<66.49 || hb>67.01 || ha<66.49 || ha>67.01 || Math.abs(hb-ha)>.5)continue;
                    boolean qualify=true;
                    for(int k=first;k<=last;k++) {
                        BlockPos q=orientation==0?new BlockPos(outer,66,k):new BlockPos(k,66,outer);
                        if(protectedStructureCore(q) || nearRail(l,q) || distanceToStair(q)>6
                          || !(closeToKnownGrade(q,2) || closeToSavedR5(q,2)))qualify=false;
                    }
                    if(!qualify)continue;
                    // If either endpoint is a half-step or stair, continue it
                    // with bottom sandstone slabs rather than a full-height wall.
                    boolean half=hb<=66.75 || ha<=66.75;
                    BlockState target=half?Blocks.SANDSTONE_SLAB.defaultBlockState():Blocks.SAND.defaultBlockState();
                    for(int k=first;k<=last;k++) {
                        BlockPos q=orientation==0?new BlockPos(outer,66,k):new BlockPos(k,66,outer);
                        staged.put(q,target);
                        reasons.put(q,half?"CONTIGUOUS_HALF_STEP_SPAN_R6":"CONTIGUOUS_SAND_SPAN_R6");
                    }
                    LOG.info("[TC-DEV51B-R6] CONNECTED_SPAN axis={} fixed={} start={} end={} length={} heightBefore={} heightAfter={} material={}",
                             orientation==0?"Z":"X",outer,first,last,span,hb,ha,id(target));
                }
            }
        }
        // Two expansion rounds: first join supported gaps between intact anchors,
        // then consider cells between newly verified continuous landing segments.
        // This forms a coherent surface instead of isolated one-cell fills.
        for(int round=0;round<3;round++) {
            int accepted=0;
            for(int x=93;x<=106;x++) for(int z=3160;z<=3176;z++) {
                BlockPos p=new BlockPos(x,66,z);
                if(staged.containsKey(p))continue;
                if (!l.getBlockState(p).isAir() || !l.getBlockState(p.above()).isAir()
                    || !l.getBlockState(p.above(2)).isAir())continue;
                if (!isSand(l.getBlockState(p.below())) || l.getBlockEntity(p.below())!=null
                    || l.getBlockEntity(p)!=null || nearRail(l,p))continue;
                if (protectedStructureCore(p)) continue;
                int stairDist=distanceToStair(p);
                boolean nearR4=closeToKnownGrade(p,2);
                boolean nearR5=closeToSavedR5(p,2);
                // No automatic sand build beyond the documented station-side grading.
                if(!(nearR4 || nearR5))continue;
                // Limit grading to rows with an existing station approach anchor;
                // never extend the natural dunes by flooding the whole sand field.
                if(stairDist>6 && !nearR5)continue;
                int anchors=0, natural=0, manufactured=0, opposed=0;
                double min=99,max=-99;
                boolean north=false,south=false,east=false,west=false;
                for(Direction d:Direction.Plane.HORIZONTAL) {
                    BlockPos q=p.relative(d);
                    if(!profiles.containsKey(q))continue;
                    double h=topHeight(l,q,staged);
                    if(h<65.99 || h>67.01)continue;
                    if(h>=66.49) {
                        anchors++; min=Math.min(min,h);max=Math.max(max,h);
                        if(d==Direction.NORTH) north=true;
                        if(d==Direction.SOUTH) south=true;
                        if(d==Direction.EAST) east=true;
                        if(d==Direction.WEST) west=true;
                        BlockState sq=staged.containsKey(q)?staged.get(q):l.getBlockState(q);
                        if(isSand(sq))natural++;
                        if(isStationEdge(sq))manufactured++;
                    }
                }
                if(north&&south)opposed++;
                if(east&&west)opposed++;
                boolean origin=(nearR4 || nearR5) && (opposed>0 || (anchors>=3) ||
                      (anchors>=2 && manufactured>=1 && stairDist<=4));
                if(!origin)continue;
                // Surface target determined by neighbors. Stations keep a half-step
                // if any adjacent manufactured/stair landing is within reach; where
                // only natural sand is present, restore the natural sand ground.
                boolean land=(manufactured>0 || stairDist<=2 || (max-min)>=0.4);
                BlockState target=land?Blocks.SANDSTONE_SLAB.defaultBlockState():Blocks.SAND.defaultBlockState();
                double expected=land?66.5:67.0;
                if (Math.abs(expected-min)>0.75 || Math.abs(expected-max)>0.75) continue;
                staged.put(p,target);
                reasons.put(p,land?"PROFILE_CONNECTED_HALF_STEP_R6":"PROFILE_NATURAL_SAND_FILL_R6");
                accepted++;
            }
            LOG.info("[TC-DEV51B-R6] PROFILE_PASS round={} newlyConnected={} running={} maxEdits={}",
                     round,accepted,staged.size(),LIMIT);
            if(accepted==0)break;
        }
        // Reject orphaned half-steps and fills. At least two adjacent accessible
        // surfaces must remain after all staged changes (no island slabs).
        for(Map.Entry<BlockPos,BlockState> step:staged.entrySet()) {
            BlockPos p=step.getKey();int adjacent=0;boolean built=false;
            double self=step.getValue().is(Blocks.SANDSTONE_SLAB)?66.5:67.0;
            for(Direction d:Direction.Plane.HORIZONTAL) {
                BlockPos q=p.relative(d);
                double neighbor=topHeight(l,q,staged);
                if(neighbor>=66.49 && Math.abs(self-neighbor)<=0.75) {
                    adjacent++;
                    BlockState state=staged.containsKey(q)?staged.get(q):l.getBlockState(q);
                    if(isStationEdge(state))built=true;
                }
            }
            if(adjacent<2 || (step.getValue().is(Blocks.SANDSTONE_SLAB) && !built)) {
                LOG.info("[TC-DEV51B-R6] PROFILE_HELD pos={} reason=ORPHAN_OR_UNSUPPORTED height={} neighbors={}",
                         p.toShortString(),self,adjacent);
                r.sandBlocked++;continue;
            }
            if(add(r,l,p,step.getValue(),reasons.get(p)))r.sand++;
        }
        if(r.edits.size()>LIMIT)r.unsafe=true;
        // Print contiguous top-height rows including unmodified held cells;
        // these are ground truth for R7 if any remaining terrain is ambiguous.
        int connected=0, holes=0;
        Map<BlockPos,BlockState> approvedStates=toStateMap(r.edits);
        for(int z=3160;z<=3176;z++) {
            StringBuilder line=new StringBuilder();
            for(int x=93;x<=106;x++) {
                BlockPos p=new BlockPos(x,66,z);
                double ht=topHeight(l,p,approvedStates);
                char ch=ht>=67.99?'^':ht>=66.99?'#':ht>=66.49?'+':ht>=65.99?'.':'?';
                line.append(ch);
                if(ch=='.')holes++;else if(ch=='#'||ch=='+')connected++;
            }
            LOG.info("[TC-DEV51B-R6] HEIGHT_PROFILE z={} x93..106={} key(^>=68,#=67,+=66.5,.=66)",z,line);
        }
        LOG.info("[TC-DEV51B-R6] PROFILE_SUMMARY staged={} approved={} held={} surfaceCells={} lowerCells={} 49railsProtected",
                staged.size(),r.sand,r.sandBlocked,connected,holes);
    }

    private static Map<BlockPos,BlockState> toStateMap(Map<BlockPos,Edit> edits) {
        Map<BlockPos,BlockState> m=new HashMap<>();
        for(Map.Entry<BlockPos,Edit> e:edits.entrySet())m.put(e.getKey(),e.getValue().after);
        return m;
    }
    private static boolean isCandidateAir(ServerLevel l,BlockPos p) {
        return loaded(l,p.below(),p,p.above(),p.above(2))
          && l.getBlockState(p).isAir() && l.getBlockState(p.above()).isAir()
          && l.getBlockState(p.above(2)).isAir() && isSand(l.getBlockState(p.below()))
          && l.getBlockEntity(p)==null && l.getBlockEntity(p.below())==null;
    }
    private static boolean protectedStructureCore(BlockPos p) {
        // Building interior exclusion, x97-101 and z3162-3166. Only outer
        // sand walkways qualify; this must never fill a wooden room interior.
        return p.getX()>=97 && p.getX()<=101 && p.getZ()>=3162 && p.getZ()<=3166;
    }
    private static int distanceToStair(BlockPos p) {
        int minimum=Integer.MAX_VALUE;
        for(BlockPos s:KNOWN_STAIRS)
            minimum=Math.min(minimum,Math.abs(p.getX()-s.getX())+Math.abs(p.getZ()-s.getZ()));
        return minimum;
    }
    private static boolean closeToKnownGrade(BlockPos p,int distance) {
        for(BlockPos q:R4_CLEARED) if(q.getY()==66 && Math.abs(q.getX()-p.getX())+
              Math.abs(q.getZ()-p.getZ())<=distance)return true;
        return false;
    }
    private static boolean closeToSavedR5(BlockPos p,int distance) {
        for(BlockPos q:R5_POS) if(Math.abs(q.getX()-p.getX())+
              Math.abs(q.getZ()-p.getZ())<=distance)return true;
        return false;
    }
    private static boolean isStationEdge(BlockState state) {
        if(state.is(Blocks.SANDSTONE_SLAB) || state.is(Blocks.SANDSTONE_STAIRS)
           || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
           || state.is(Blocks.STONE_BRICKS))return true;
        String path=id(state).getPath();
        return path.contains("sandstone") || path.contains("stone_brick");
    }
    private static double topHeight(ServerLevel l,BlockPos column,
                                    Map<BlockPos,BlockState> staged) {
        int x=column.getX(),z=column.getZ();
        for(int y=68;y>=65;y--) {
            BlockPos p=new BlockPos(x,y,z);
            if(!l.hasChunkAt(p))return -1;
            BlockState state=staged.containsKey(p)?staged.get(p):l.getBlockState(p);
            if(state.isAir() || !state.getFluidState().isEmpty())continue;
            if(isRail(state) || l.getBlockEntity(p)!=null)return -1;
            if(!loaded(l,p.above(),p.above(2)) || !l.getBlockState(p.above()).isAir()
               || !l.getBlockState(p.above(2)).isAir())return -1;
            if(state.is(Blocks.SANDSTONE_SLAB))return y+0.5;
            if(state.is(Blocks.SANDSTONE_STAIRS))return y+0.75;
            if(state.isFaceSturdy(l,p,Direction.UP))return y+1.0;
            return -1;
        }
        return -1;
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
        say(s,"[DEV51B-R6 "+(preview?"PREVIEW":"SURVEY")+"] originalY66Stairs="+r.stairs
            +" profileFills="+r.sand+" held="+r.sandBlocked
            +" existingPathTiles="+r.pathTiles+" routeBfs="+r.pathConnected
            +" totalEdits="+r.edits.size());
        say(s,"[DEV51B-R6 GUARD] savedR5Postimages=15 and savedR4Footprint=34 protected; railCount=49; station floors untouched.");
        say(s,"[DEV51B-R6 WALK] "+r.pathReason+" steps="+r.pathLength);
        if(preview)for(Map.Entry<BlockPos,Edit> entry:r.edits.entrySet()) {
            BlockPos p=entry.getKey();Edit e=entry.getValue();
            String str=p.toShortString()+" "+e.why+" "+id(e.before)+" -> "+id(e.after);
            LOG.info("[TC-DEV51B-R6] PROPOSED {}",str);
            say(s,"[DEV51B-R6 EDIT] "+str);
        }
        if(r.unsafe) say(s,"[DEV51B-R6 SAFETY STOP] Saved world/route/rails mismatch or >56 edits. No placement.");
        if(r.sand==0)say(s,"[DEV51B-R6 HOLD] No verified continuous repair; no world changes.");
        LOG.info("[TC-DEV51B-R6] {} stairs={} grade={} held={} path={} walkingRoute={} steps={} edits={} unsafe={}",
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
