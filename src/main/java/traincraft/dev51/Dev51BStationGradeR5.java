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
 * DEV51B R5 - saved-R4 surface recontouring with no structure/stair/path edits.
 * Adds commands only, does not modify world-generation, train, track or renderer code.
 * Only preverified shallow natural pits may be infilled in a preview-and-confirm flow.
 * No unverified blocks are placed; planner refuses ambiguous/unsafe situations.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationGradeR5 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R5");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final long PREVIEW_TICKS = 2400L;
    private static final int LIMIT = 40;
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
    private static final Map<UUID, Plan> PREVIEWS = new HashMap<>();
    private static final Map<UUID, Plan> UNDOS = new HashMap<>();
    private Dev51BStationGradeR5() { }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> station = Commands.literal("station")
                .then(Commands.literal("survey").executes(c -> survey(c.getSource())))
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").then(Commands.literal("confirm")
                        .executes(c -> place(c.getSource()))))
                .then(Commands.literal("undo").executes(c -> undo(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource())));
        event.getDispatcher().register(Commands.literal("tcdev51b5")
                .requires(s -> s.hasPermission(2)).then(station));
        LOG.info("[TC-DEV51B-R5] Registered isolated station survey/preview/confirm/undo");
    }

    private static int help(CommandSourceStack s) {
        say(s, "[DEV51B-R5] /tcdev51b5 station survey | preview | place confirm | undo");
        say(s, "[DEV51B-R5] SAVED R4 WORLD: original Y66 stairs and six path tiles are frozen. Preview only first.");
        say(s, "[DEV51B-R5] DEV50E6 only; verify R4 46-edit postimages and 49 rails before any placement.");
        return 1;
    }

    private static boolean allowed(CommandSourceStack s) {
        String name = s.getLevel().getServer().getWorldData().getLevelName();
        if (!s.getLevel().dimension().equals(Level.OVERWORLD) || !WORLD.equals(name)) {
            say(s, "[DEV51B-R5 STOP] Test world only. Detected: " + name);
            return false;
        }
        return true;
    }

    private static ServerPlayer player(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return p;
        say(s, "[DEV51B-R5 STOP] In-game operator required");
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
            say(s, "[DEV51B-R5 SURVEY] STOP: guarded station area not fully loaded; no forced chunks");
            return 0;
        }
        Report r = makeReport(s.getLevel());
        printReport(s, r, false);
        say(s, "[DEV51B-R5 SURVEY] rails=" + railCount(s.getLevel()) + " changes=0 forcedChunks=0");
        return 1;
    }

    private static boolean vicinityCheck(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null || !vicinity(p)) {
            say(s, "[DEV51B-R5 STOP] Stand within 64 blocks of 89 67 3164");
            return false;
        }
        return true;
    }

    private static int preview(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        PREVIEWS.remove(p.getUUID());
        if (UNDOS.containsKey(p.getUUID())) {
            say(s, "[DEV51B-R5 STOP] Previous DEV51B-R5 undo pending; undo before planning again");
            return 0;
        }
        ServerLevel l = s.getLevel();
        long rails = rails(l);
        long guard = regionFingerprint(l);
        if (rails == Long.MIN_VALUE || guard == Long.MIN_VALUE || railCount(l) != 49) {
            say(s, "[DEV51B-R5 SAFETY STOP] 49 loaded rails and station footprint required; no changes");
            return 0;
        }
        Report report = makeReport(l);
        printReport(s, report, true);
        if (report.edits.isEmpty()) {
            say(s, "[DEV51B-R5] No automatically provable edits. Nothing will be changed.");
            return 0;
        }
        // R5 requires all six original-height Y66 stairs frozen before allowing
        // any grading; no staircase changes.
        if (report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R5 HOLD] Require six verified original-height stairs. Found=" + report.stairs
                    + " of six. No stair changes or partial terrain edits allowed.");
            return 0;
        }
        if (!report.pathConnected || report.stairs != EXPECTED_STAIR_COUNT) {
            say(s, "[DEV51B-R5 HOLD] Existing village path endpoint or six tiles changed; placement disabled.");
            return 0;
        }
        if (report.sand == 0) {
            say(s, "[DEV51B-R5 HOLD] No supported shallow pit fills proven; NO WORLD CHANGES.");
            return 0;
        }
        if (report.unsafe || report.edits.size() > LIMIT) {
            say(s, "[DEV51B-R5 SAFETY STOP] Ambiguous/over-limit edits; no placement offered");
            return 0;
        }
        Plan plan = new Plan(l.dimension().location().toString(), l.getGameTime(),
                rails, guard, new LinkedHashMap<>(report.edits));
        PREVIEWS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R5 PREVIEW] " + report.edits.size() + " exact edits; no world changes. "
                + "Confirm in 2 minutes ONLY after inspecting report: /tcdev51b5 station place confirm");
        LOG.info("[TC-DEV51B-R5] PREVIEW edits={} stairs={} sand={} path={} connected={} rails=49 noChanges=true",
                report.edits.size(), report.stairs, report.sand, report.pathTiles, report.pathConnected);
        return 1;
    }

    private static int place(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = PREVIEWS.remove(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R5 STOP] Run station preview first");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (UNDOS.containsKey(p.getUUID()) || !plan.dim.equals(l.dimension().location().toString())
                || l.getGameTime() < plan.tick || l.getGameTime() - plan.tick > PREVIEW_TICKS
                || rails(l) != plan.rails || regionFingerprint(l) != plan.region
                || railCount(l) != 49 || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R5 SAFETY STOP] Preview expired or protected world changed; NO CHANGES");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> ordered = new ArrayList<>(plan.edits.entrySet());
        // All R5 edits are additions to verified air pits (no stair/rail or path removals).
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
            say(s, "[DEV51B-R5 SAFETY STOP] Placement aborted. Reverted " + restored + "/"
                    + applied.size() + " applied edits; inspect world before retrying");
            LOG.error("[TC-DEV51B-R5] PLACE FAILED applied={} reverted={} reason={}",
                    applied.size(), restored, ex.toString());
            return 0;
        }
        UNDOS.put(p.getUUID(), plan);
        say(s, "[DEV51B-R5 PLACED] " + applied.size() + " guarded edits; all 49 rails unchanged."
                + " Same-session undo: /tcdev51b5 station undo");
        say(s, "[DEV51B-R5] Inspect restored stairs, soft pit fills, clear entrances, and existing village route.");
        LOG.info("[TC-DEV51B-R5] PLACED edits={} railsProtected=true forcedChunks=0", applied.size());
        return 1;
    }

    private static int undo(CommandSourceStack s) {
        if (!allowed(s) || !vicinityCheck(s)) return 0;
        ServerPlayer p = player(s);
        Plan plan = UNDOS.get(p.getUUID());
        if (plan == null) {
            say(s, "[DEV51B-R5 STOP] No same-session placement to undo");
            return 0;
        }
        ServerLevel l = s.getLevel();
        if (!plan.dim.equals(l.dimension().location().toString())
                || rails(l) != plan.rails || railCount(l) != 49
                || !matches(l, plan.edits, false)) {
            say(s, "[DEV51B-R5 SAFETY STOP] Rails/edited cells changed; undo refused to protect new blocks");
            return 0;
        }
        List<Map.Entry<BlockPos, Edit>> steps = new ArrayList<>(plan.edits.entrySet());
        int restored = revert(l, steps);
        if (restored != steps.size() || rails(l) != plan.rails || !matches(l, plan.edits, true)) {
            say(s, "[DEV51B-R5 WARNING] Undo partial; inspect carefully; do not force");
            LOG.error("[TC-DEV51B-R5] UNDO PARTIAL restored={} expected={}", restored, steps.size());
            return 0;
        }
        UNDOS.remove(p.getUUID());
        say(s, "[DEV51B-R5 UNDO PASS] Restored " + restored + " original blocks; rails preserved");
        LOG.info("[TC-DEV51B-R5] UNDO PASS restored={}", restored);
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
        // R4 PLACED 46 changes and user exited. Must find ORIGINAL Y66 stairs,
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
            LOG.info("[TC-DEV51B-R5] STAIR_FROZEN index={} pos={} y66={} y67={} expected={} verdict={}",
                    i, floor.toShortString(), l.hasChunkAt(floor) ? l.getBlockState(floor) : "UNLOADED",
                    l.hasChunkAt(above) ? id(l.getBlockState(above)) : "UNLOADED", expected,
                    pass ? "Y66_INTACT_NO_EDIT" : "R4_POSTIMAGE_MISMATCH_STOP");
        }
    }

    private static void verifyPathAndR3Clearance(ServerLevel l, Report r) {
        // R3 path, R3 sand clearances and exact saved-R4 AIR postimages.
        // Every source must match before even proposing a terrain infill.
        for (BlockPos p : R3_PATH) {
            if (!loaded(l, p, p.above(), p.above(2))
                    || !l.getBlockState(p).is(Blocks.SANDSTONE)
                    || !walkable(l, p) || l.getBlockEntity(p) != null) {
                r.pathReason = "R3_PATH_CHANGED_STOP";
                r.unsafe = true;
                LOG.warn("[TC-DEV51B-R5] FROZEN_PATH_MISMATCH at={}", p.toShortString());
                return;
            }
            r.pathTiles++;
        }
        for (BlockPos p : R3_SAND_CLEARED) {
            if (!loaded(l, p) || !l.getBlockState(p).isAir()) {
                r.pathReason = "R3_STATION_CLEARANCE_CHANGED_STOP";
                r.unsafe = true;
                LOG.warn("[TC-DEV51B-R5] FROZEN_SAND_MISMATCH at={}", p.toShortString());
                return;
            }
        }
        for (BlockPos p : R4_CLEARED) {
            if (!loaded(l, p, p.above(), p.below()) || !l.getBlockState(p).isAir()
                    || l.getBlockEntity(p) != null) {
                r.pathReason = "R4_SAVED_GRADE_MISMATCH_STOP";
                r.unsafe = true;
                LOG.warn("[TC-DEV51B-R5] R4_SAVED_POSTIMAGE_MISMATCH at={}", p.toShortString());
                return;
            }
        }
        BlockPos village = new BlockPos(95, 65, 3143);
        if (!loaded(l, village, village.above(), village.above(2))
                || !l.getBlockState(village).is(Blocks.DIRT_PATH) || !walkable(l, village)) {
            r.pathReason = "VILLAGE_PATH_CHANGED_STOP";
            r.unsafe = true;
            return;
        }
        r.pathConnected = true; // R3 known sequence present; NOT a player pathfinding claim.
        r.pathLength = 8;
        r.pathReason = "SIX_R3_TILES_AND_VILLAGE_ENDPOINT_FROZEN_NO_PATH_EDITS";
        // The video shows a cobblestone/dirt transition. R5 only SURVEYS it:
        // no replacing paths, village dirt, grass, or gravel.
        for (int x=94; x<=96; x++) for (int z=3141; z<=3145; z++) {
            StringBuilder line = new StringBuilder();
            for (int y=64; y<=67; y++) {
                BlockPos p = new BlockPos(x,y,z);
                line.append(y).append(":")
                        .append(l.hasChunkAt(p) ? id(l.getBlockState(p)) : "UNLOADED").append(" ");
            }
            LOG.info("[TC-DEV51B-R5] VILLAGE_SEAM_READONLY x={} z={} layers={}", x,z,line);
        }
    }

    private static void scanSand(ServerLevel l, Report r) {
        // R4 lowered exposed sand on purpose, but several Y66 cuts left sharp
        // square pits. Never put a full sand block directly against a Y66 stair.
        // Fill ONLY verified R4-cleared Y66 cells with supporting natural sand
        // at Y65, overhead clearance, and an exposed walkable approach.
        // Half-height sandstone slabs near the entrance soften a single step;
        // farther isolated pits may be closed using natural sand. ALL proposed
        // cells are logged and guarded by preimage equality at confirm time.
        for (BlockPos p : R4_CLEARED) {
            int min = Integer.MAX_VALUE;
            for (BlockPos stair : KNOWN_STAIRS)
                min=Math.min(min,Math.abs(p.getX()-stair.getX())+Math.abs(p.getZ()-stair.getZ()));
            if (p.getY() != 66) {
                LOG.info("[TC-DEV51B-R5] R4_EDGE pos={} decision=KEEP_Y67_GRADE_NOT_A_PIT",p.toShortString());
                continue;
            }
            BlockPos support = p.below();
            BlockPos head1 = p.above();
            BlockPos head2 = p.above(2);
            BlockState below = l.getBlockState(support);
            String reason;
            if (!loaded(l,p,support,head1,head2)) {
                r.unsafe = true;
                reason="UNLOADED_STOP";
            } else if (!l.getBlockState(p).isAir() || !l.getBlockState(head1).isAir()
                    || !l.getBlockState(head2).isAir()) {
                r.unsafe = true;
                reason="R4_EXACT_POSTIMAGE_CHANGED_STOP";
            } else if (!isSand(below) || !below.isFaceSturdy(l,support,Direction.UP)
                    || l.getBlockEntity(support)!=null) {
                reason="NO_NATURAL_SAND_FLOOR_HOLD";
                r.sandBlocked++;
            } else if (nearRail(l,p)) {
                reason="RAIL_BUFFER_HOLD";
                r.sandBlocked++;
            } else {
                int compatible=0, stairContacts=0, manufacturedContacts=0;
                boolean n=false,s=false,e=false,w=false;
                for (Direction d: Direction.Plane.HORIZONTAL) {
                    BlockPos q=p.relative(d);
                    if (!loaded(l,q,q.above(),q.above(2))) {
                        r.unsafe=true;
                        continue;
                    }
                    BlockState state=l.getBlockState(q);
                    if (state.is(Blocks.SANDSTONE_STAIRS)) stairContacts++;
                    if (manufacturedFloor(l,q,state)) manufacturedContacts++;
                    if (isSafeExposedTop(l,q,state)) {
                        compatible++;
                        if (d==Direction.NORTH) n=true;
                        if (d==Direction.SOUTH) s=true;
                        if (d==Direction.EAST) e=true;
                        if (d==Direction.WEST) w=true;
                    }
                }
                r.sandContacts++;
                if (r.unsafe) reason="UNLOADED_NEIGHBORS_STOP";
                else if (min<=2 && (stairContacts>0 || manufacturedContacts>0) && compatible>=1) {
                    // Below the top of a full sand block: natural-looking
                    // sandstone half-step is lower than re-stacking a dune.
                    BlockState half=Blocks.SANDSTONE_SLAB.defaultBlockState();
                    if (add(r,l,p,half,"STATION_EDGE_HALF_STEP_R5")) {
                        r.sand++;
                        reason="APPROVED_ENTRANCE_HALF_STEP";
                    } else reason="CONFLICT_STOP";
                } else if (min>=3 && compatible>=2 && ((n&&s)||(e&&w)||compatible>=3)) {
                    // Only close natural holes boxed by neighboring walkable
                    // tops. Never stack sand within 2 blocks of any stair.
                    if (add(r,l,p,Blocks.SAND.defaultBlockState(),"ISOLATED_PIT_NATURAL_FILL_R5")) {
                        r.sand++;
                        reason="APPROVED_PIT_FILL";
                    } else reason="CONFLICT_STOP";
                } else {
                    reason="NO_PROVEN_WALKABLE_BLEND_HOLD";
                    r.sandBlocked++;
                }
                LOG.info("[TC-DEV51B-R5] GRADE_CANDIDATE pos={} distance={} neighbors={} stairs={} manufactured={} decision={}",
                        p.toShortString(),min,compatible,stairContacts,manufacturedContacts,reason);
                continue;
            }
            LOG.info("[TC-DEV51B-R5] GRADE_CANDIDATE pos={} distance={} decision={}",
                    p.toShortString(),min,reason);
        }
        // Read-only topographic belt around station; all surfaces incl. legacy
        // holes are inspected. R5 does NOT guess at new non-R4 air positions.
        for(int x=93;x<=106;x++) for(int z=3160;z<=3176;z++) {
            StringBuilder b=new StringBuilder();
            for(int y=65;y<=68;y++) {
                BlockPos p=new BlockPos(x,y,z);
                b.append(y).append('=')
                        .append(l.hasChunkAt(p)?id(l.getBlockState(p)):"UNLOADED").append(' ');
            }
            LOG.info("[TC-DEV51B-R5] SURFACE_PROFILE x={} z={} layers={}",x,z,b);
        }
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
        say(s, "[DEV51B-R5 " + (preview ? "PREVIEW" : "SURVEY")
                + "] frozenY66Stairs="+r.stairs+" proposedPitOrSlabFills="+r.sand
                + " preservedPath="+r.pathTiles+" totalEdits="+r.edits.size());
        say(s, "[DEV51B-R5 HELD] uncertainCells="+r.sandBlocked
                + " verifiedR4ClearedCells=34 (16 Y67 retained)");
        say(s, "[DEV51B-R5 LINK] "+r.pathReason+" pathTiles="+r.pathTiles
                + " endpointVerified="+r.pathConnected);
        if(preview) {
            for(Map.Entry<BlockPos,Edit> entry:r.edits.entrySet()) {
                BlockPos p=entry.getKey(); Edit e=entry.getValue();
                String str=p.toShortString()+" "+e.why+" "+id(e.before)+" -> "+id(e.after);
                LOG.info("[TC-DEV51B-R5] PROPOSED {}",str);
                say(s,"[DEV51B-R5 EDIT] "+str);
            }
        }
        if(r.unsafe) say(s,"[DEV51B-R5 SAFETY STOP] Saved world mismatch or unloaded blocks: no placement.");
        if(r.sand==0) say(s,"[DEV51B-R5 HOLD] No shallow pit can be safely fixed from available evidence.");
        say(s,"[DEV51B-R5 GUARD] All six stairs remain at Y66; no existing path, station, or rail edit.");
        LOG.info("[TC-DEV51B-R5] {} frozenStairs={} fills={} path={} endpoint={} edits={} unsafe={}",
                preview?"PREVIEW":"SURVEY",r.stairs,r.sand,r.pathTiles,r.pathConnected,r.edits.size(),r.unsafe);
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
