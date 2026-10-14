package traincraft.dev51;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DEV51B R10: guarded, manual, 55-block terrain test placement ONLY.
 * This is NOT a world-generation change. Exact post-R7 preimages and R9b
 * walking-grade simulation are prerequisites to any block mutation.
 */
@Mod.EventBusSubscriber(modid="traincraft", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationGradePlacementR10 {
    private static final Logger LOG=LoggerFactory.getLogger("TC-DEV51B-R10");
    private static final String WORLD="DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION=new BlockPos(89,67,3164);
    private static final long PREVIEW_WINDOW_NS=120_000_000_000L;
    private static long lastPreviewAt=0L;
    private static UUID previewPlayer=null;
    private static ServerLevel previewLevel=null;
    private static ServerLevel undoLevel=null;
    private static UUID undoPlayer=null;
    private static List<BlockState> undoImages=null;
    private static final int[][] R7_REMOVED = {
        // Generated strictly from the 112 exact [TC-DEV51B-R7] PROPOSED records
        // in latest(20261013-190613).log. Each was sand -> air in R7.
                {93,66,3146},
        {93,66,3147},
        {93,66,3148},
        {93,66,3149},
        {93,66,3150},
        {94,66,3150},
        {93,66,3151},
        {94,66,3151},
        {95,67,3153},
        {96,67,3153},
        {95,67,3154},
        {96,67,3154},
        {97,67,3154},
        {97,67,3155},
        {95,67,3156},
        {95,67,3157},
        {95,67,3158},
        {96,67,3158},
        {95,67,3159},
        {96,67,3159},
        {95,67,3160},
        {96,67,3160},
        {98,67,3160},
        {99,67,3160},
        {100,67,3160},
        {101,67,3160},
        {95,67,3161},
        {96,67,3161},
        {97,67,3161},
        {102,66,3161},
        {95,67,3162},
        {96,67,3162},
        {105,66,3162},
        {106,67,3162},
        {107,67,3162},
        {96,67,3163},
        {105,66,3163},
        {106,67,3163},
        {106,66,3163},
        {107,67,3163},
        {105,66,3164},
        {106,67,3164},
        {107,67,3164},
        {96,67,3165},
        {105,66,3165},
        {106,67,3165},
        {107,67,3165},
        {95,67,3166},
        {96,67,3166},
        {105,66,3166},
        {106,67,3166},
        {106,66,3166},
        {107,67,3166},
        {95,67,3167},
        {96,67,3167},
        {97,67,3167},
        {105,66,3167},
        {106,67,3167},
        {107,67,3167},
        {95,67,3168},
        {96,67,3168},
        {102,66,3168},
        {106,67,3168},
        {107,67,3168},
        {95,67,3169},
        {96,67,3169},
        {97,67,3169},
        {98,67,3169},
        {99,67,3169},
        {101,66,3169},
        {95,66,3170},
        {96,67,3170},
        {97,67,3170},
        {98,67,3170},
        {99,67,3170},
        {100,66,3170},
        {101,66,3170},
        {102,66,3170},
        {95,66,3171},
        {97,67,3171},
        {98,67,3171},
        {99,66,3171},
        {100,66,3171},
        {95,66,3174},
        {96,66,3174},
        {97,67,3174},
        {98,67,3174},
        {99,66,3174},
        {100,66,3174},
        {103,66,3174},
        {93,66,3175},
        {94,66,3175},
        {95,66,3175},
        {96,67,3175},
        {97,67,3175},
        {98,67,3175},
        {99,67,3175},
        {100,66,3175},
        {101,66,3175},
        {102,66,3175},
        {94,66,3176},
        {95,67,3176},
        {96,67,3176},
        {97,67,3176},
        {98,67,3176},
        {99,67,3176},
        {100,67,3176},
        {101,66,3176},
        {95,67,3177},
        {96,67,3177},
        {99,67,3177},
        {100,67,3177}
    };
    private static final BlockPos[] STAIRS = {
        new BlockPos(94,66,3172), new BlockPos(94,66,3173),
        new BlockPos(101,66,3172), new BlockPos(101,66,3173),
        new BlockPos(103,66,3163), new BlockPos(103,66,3166)
    };
    private static final Direction[] STAIR_FACING = {
        Direction.EAST, Direction.EAST, Direction.WEST, Direction.WEST,
        Direction.SOUTH, Direction.NORTH
    };
    private static final BlockPos[] PATH = {
        new BlockPos(94,66,3149), new BlockPos(94,66,3148),
        new BlockPos(94,66,3147), new BlockPos(94,66,3146),
        new BlockPos(94,65,3145), new BlockPos(94,65,3144)
    };
    private static final BlockPos[] R6_SLABS = {
        new BlockPos(102,66,3164), new BlockPos(102,66,3165),
        new BlockPos(101,66,3167)
    };
    private static final int[][] R9_PROPOSALS = {
        {98,65,3144,0,1,3},
        {99,64,3144,0,1,2},
        {99,65,3144,0,1,3},
        {100,64,3144,0,1,2},
        {100,65,3144,0,1,3},
        {95,67,3155,1,0,0},
        {96,67,3155,1,0,0},
        {98,67,3155,1,0,0},
        {99,67,3155,1,0,0},
        {96,67,3156,1,0,0},
        {97,67,3156,1,0,0},
        {98,67,3156,1,0,0},
        {99,67,3156,1,0,0},
        {97,67,3157,1,0,0},
        {98,67,3157,1,0,0},
        {99,67,3157,1,0,0},
        {100,67,3157,1,0,0},
        {97,67,3158,1,0,0},
        {98,67,3158,1,0,0},
        {99,67,3158,1,0,0},
        {100,67,3158,1,0,0},
        {97,67,3159,1,0,0},
        {98,67,3159,1,0,0},
        {99,67,3159,1,0,0},
        {100,67,3159,1,0,0},
        {97,67,3160,1,0,0},
        {102,66,3161,0,1,1},
        {105,66,3162,0,1,1},
        {106,66,3163,0,1,1},
        {105,66,3164,0,1,1},
        {105,66,3165,0,1,1},
        {106,66,3166,0,1,1},
        {105,66,3167,0,1,1},
        {97,67,3168,1,0,0},
        {102,66,3168,0,1,1},
        {101,66,3169,0,1,1},
        {100,66,3170,0,1,1},
        {102,66,3170,0,1,1},
        {99,66,3171,0,1,1},
        {100,66,3171,0,1,1},
        {95,66,3174,0,1,1},
        {96,66,3174,0,1,1},
        {99,66,3174,0,1,1},
        {100,66,3174,0,1,1},
        {103,66,3174,0,1,1},
        {93,66,3175,0,1,1},
        {95,66,3175,0,1,1},
        {100,66,3175,0,1,1},
        {102,66,3175,0,1,1},
        {94,66,3176,0,1,1},
        {101,66,3176,0,1,1},
        {97,67,3177,1,0,0},
        {98,67,3177,1,0,0},
        {104,66,3177,0,1,1},
        {103,66,3179,0,1,1},
    };
    private static final String[] R9_REASONS = {"REMOVE_RAISED_SAND_TOP", "RESTORE_STATION_WALK_SURFACE", "SUPPORT_VILLAGE_DEPRESSION", "RESTORE_VILLAGE_DEPRESSION"};
    // Exact R8 baseline y64..68 snapshots. Protected structures cannot silently change.
    // x,z,y64,y65,y66,y67,y68 as block registry ID strings.
    private static final String[][] R9_COLUMN_FINGERPRINTS = {
        {"98","3144","minecraft:sand","minecraft:air","minecraft:air","minecraft:air","minecraft:air"},
        {"99","3144","minecraft:air","minecraft:air","minecraft:air","minecraft:air","minecraft:air"},
        {"100","3144","minecraft:air","minecraft:air","minecraft:air","minecraft:air","minecraft:air"},
        {"95","3155","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"96","3155","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3155","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"99","3155","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"96","3156","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"97","3156","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3156","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"99","3156","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"97","3157","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3157","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"99","3157","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"100","3157","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"97","3158","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3158","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"99","3158","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"100","3158","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"97","3159","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3159","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"99","3159","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"100","3159","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"97","3160","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"102","3161","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"105","3162","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"106","3163","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"105","3164","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"105","3165","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"106","3166","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"105","3167","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"97","3168","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"102","3168","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"101","3169","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"100","3170","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"102","3170","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"99","3171","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"100","3171","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"95","3174","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"96","3174","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"99","3174","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"100","3174","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"103","3174","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"93","3175","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"95","3175","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"100","3175","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"102","3175","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"94","3176","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"101","3176","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"97","3177","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"98","3177","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:sand","minecraft:air"},
        {"104","3177","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
        {"103","3179","minecraft:sand","minecraft:sand","minecraft:air","minecraft:air","minecraft:air"},
    };


    private static final int[][] PROPOSALS=R9_PROPOSALS;
    private Dev51BStationGradePlacementR10() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent ev) {
        LiteralArgumentBuilder<CommandSourceStack> root=Commands.literal("tcdev51b10")
          .requires(s->s.hasPermission(2))
          .then(Commands.literal("station")
            .then(Commands.literal("preview").executes(ctx->preview(ctx.getSource())))
            .then(Commands.literal("place")
              .then(Commands.literal("confirm").executes(ctx->place(ctx.getSource()))))
            .then(Commands.literal("undo").executes(ctx->undo(ctx.getSource())))
            .then(Commands.literal("status").executes(ctx->status(ctx.getSource()))));
        ev.getDispatcher().register(root);
    }

    private static ServerLevel validLevel(CommandSourceStack s) {
        ServerLevel l=s.getLevel();
        if(!l.dimension().equals(Level.OVERWORLD)
            || !WORLD.equals(l.getServer().getWorldData().getLevelName())
            || !(s.getEntity() instanceof ServerPlayer p)
            || p.blockPosition().distSqr(STATION)>64L*64L) {
            chat(s,"[R10 STOP] Wrong world, dimension, player, or too far from station; no changes.");
            return null;
        }
        return l;
    }
    private static int status(CommandSourceStack s) {
        ServerLevel l=validLevel(s); if(l==null)return 0;
        chat(s,"[DEV51B-R10] preview="+(previewLevel==l?"READY_OR_EXPIRED":"NONE")
            +" undo="+(undoLevel==l && undoImages!=null?"AVAILABLE_THIS_SESSION":"NONE")
            +" planned=55; use preview, place confirm, undo. Worldgen untouched.");
        return 1;
    }

    private static void clearPreview() {
        lastPreviewAt=0L;previewPlayer=null;previewLevel=null;
    }
    private static boolean protectedWorld(ServerLevel l, boolean after) {
        int problems=0;
        for(int i=0;i<STAIRS.length;i++) {
            BlockPos p=STAIRS[i];
            BlockState want=Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,STAIR_FACING[i]);
            if(!l.hasChunkAt(p)||!l.getBlockState(p).equals(want)) {
                problems++;LOG.error("[TC-DEV51B-R10] STAIR_GUARD pos={} expected={} actual={}",xyz(p),want,observed(l,p));
            }
        }
        for(BlockPos p:PATH) if(!l.hasChunkAt(p)||!l.getBlockState(p).is(Blocks.SANDSTONE)) {
            problems++;LOG.error("[TC-DEV51B-R10] PATH_GUARD pos={} actual={}",xyz(p),observed(l,p));
        }
        BlockPos endpoint=new BlockPos(95,65,3143);
        if(!l.hasChunkAt(endpoint)||!l.getBlockState(endpoint).is(Blocks.DIRT_PATH)) problems++;
        for(BlockPos p:R6_SLABS) if(!l.hasChunkAt(p)||!l.getBlockState(p).is(Blocks.SANDSTONE_SLAB)) {
            problems++;LOG.error("[TC-DEV51B-R10] R6_SLAB_GUARD pos={} actual={}",xyz(p),observed(l,p));
        }
        int rails=0;
        for(int z=3140;z<=3188;z++) {
            BlockPos p=new BlockPos(89,67,z);
            if(l.hasChunkAt(p)&&isRail(l.getBlockState(p)))rails++;
            else {problems++;LOG.error("[TC-DEV51B-R10] RAIL_GUARD pos={} actual={}",xyz(p),observed(l,p));}
        }
        for(int[] p:R7_REMOVED) {
            BlockPos pos=new BlockPos(p[0],p[1],p[2]);
            BlockState expected=after?expectedPlacedR7(pos):Blocks.AIR.defaultBlockState();
            if(!l.hasChunkAt(pos)||!l.getBlockState(pos).equals(expected)) {
                problems++;LOG.error("[TC-DEV51B-R10] R7_GUARD pos={} expected={} actual={}",xyz(pos),expected,observed(l,pos));
            }
        }
        LOG.info("[TC-DEV51B-R10] PROTECTED_GUARDS after={} stairs=6 paths=6 slabs=3 rails={}/49 savedR7=112 problems={}",after,rails,problems);
        return problems==0;
    }
    private static BlockState expectedPlacedR7(BlockPos pos) {
        for(int[] edit:PROPOSALS) if(edit[0]==pos.getX() && edit[1]==pos.getY() && edit[2]==pos.getZ())
            return edit[4]==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
    private static boolean snapshotMatches(ServerLevel l,boolean after) {
        int errors=0;
        for(String[] row:R9_COLUMN_FINGERPRINTS) {
            int x=Integer.parseInt(row[0]),z=Integer.parseInt(row[1]);
            BlockPos chk=new BlockPos(x,66,z);
            if(!l.hasChunkAt(chk)) { errors++; continue; }
            for(int y=64;y<=68;y++) {
                BlockPos p=new BlockPos(x,y,z);
                String expected=after?id(expectedAtAfter(p,row[y-62])):row[y-62];
                String observed=id(l.getBlockState(p));
                if(!expected.equals(observed)) {
                    errors++;LOG.error("[TC-DEV51B-R10] SNAPSHOT_MISMATCH after={} pos={} expected={} actual={}",after,xyz(p),expected,observed);
                }
            }
        }
        LOG.info("[TC-DEV51B-R10] SNAPSHOT_MATCH after={} columns={} errors={}",after,R9_COLUMN_FINGERPRINTS.length,errors);
        return errors==0;
    }
    private static BlockState expectedAtAfter(BlockPos pos,String baseline) {
        for(int[] edit:PROPOSALS) if(edit[0]==pos.getX()&&edit[1]==pos.getY()&&edit[2]==pos.getZ())
            return edit[4]==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
        return "minecraft:sand".equals(baseline)?Blocks.SAND.defaultBlockState():
            "minecraft:air".equals(baseline)?Blocks.AIR.defaultBlockState():Blocks.SANDSTONE.defaultBlockState();
    }
    private static boolean checkPlan(ServerLevel l,boolean after) {
        int problems=0; Set<BlockPos> seen=new HashSet<>();
        for(int[] p:PROPOSALS) {
            BlockPos pos=new BlockPos(p[0],p[1],p[2]);
            if(!seen.add(pos)) { problems++;LOG.error("[TC-DEV51B-R10] DUPLICATE_EDIT {}",xyz(pos)); }
            if(!l.hasChunkAt(pos)) { problems++;continue; }
            BlockState expected=(after?p[4]:p[3])==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
            if(!l.getBlockState(pos).equals(expected)) {
                problems++;LOG.error("[TC-DEV51B-R10] PRE_OR_POST_MISMATCH after={} pos={} expected={} actual={}",after,xyz(pos),expected,observed(l,pos));
            }
            if(p[0]>=86 && p[0]<=92 && p[2]>=3138 && p[2]<=3190)problems++;
            for(BlockPos s:STAIRS)if(s.equals(pos))problems++;
            for(BlockPos s:PATH)if(s.equals(pos))problems++;
            for(BlockPos s:R6_SLABS)if(s.equals(pos))problems++;
            if(!after) {
                if(p[4]==1 && !isGround(l.getBlockState(pos.below())) && !willBeSand(pos.below())) {
                    problems++;LOG.error("[TC-DEV51B-R10] UNSUPPORTED_FILL pos={}",xyz(pos));
                }
                if(p[4]==0 && !l.getBlockState(pos.above()).isAir()) {
                    problems++;LOG.error("[TC-DEV51B-R10] OBSTRUCTED_REMOVAL pos={}",xyz(pos));
                }
            }
        }
        if(PROPOSALS.length!=55 || seen.size()!=55)problems++;
        LOG.info("[TC-DEV51B-R10] PLAN_GUARDS after={} edits={} unique={} problems={}",after,PROPOSALS.length,seen.size(),problems);
        return problems==0;
    }
    private static boolean validateEdges(ServerLevel l,boolean projected) {
        Set<Long> keys=new HashSet<>();for(int[] p:PROPOSALS)keys.add(key(p[0],p[2]));
        int beforeSteep=0,afterSteep=0,worsened=0,missing=0,checked=0;
        for(long k:keys) {
            int x=(int)(k>>32),z=(int)k;
            double before=walkHeight(l,x,z,false),after=walkHeight(l,x,z,projected);
            if(Double.isNaN(before)||Double.isNaN(after)) {missing++;continue;}
            for(Direction d:Direction.Plane.HORIZONTAL) {
                int nx=x+d.getStepX(),nz=z+d.getStepZ();
                if(!l.hasChunkAt(new BlockPos(nx,66,nz))) {missing++;continue;}
                if(columnStructural(l,nx,nz))continue;
                double nb=walkHeight(l,nx,nz,false),na=walkHeight(l,nx,nz,projected);
                if(Double.isNaN(nb)||Double.isNaN(na))continue;
                checked++;
                double b=Math.abs(before-nb),a=Math.abs(after-na);
                if(b>1.01)beforeSteep++;
                if(a>1.01)afterSteep++;
                if(a>b+0.01 && a>1.01)worsened++;
            }
        }
        LOG.info("[TC-DEV51B-R10] EDGE_CHECK projected={} modifiedColumns={} inspectedEdges={} preSteep={} postSteep={} worsened={} missing={}",
            projected,keys.size(),checked,beforeSteep,afterSteep,worsened,missing);
        return missing==0 && worsened==0 && (projected?afterSteep<=beforeSteep:afterSteep==beforeSteep);
    }
    private static boolean preflight(ServerLevel l) {
        return protectedWorld(l,false) && snapshotMatches(l,false) && checkPlan(l,false) && validateEdges(l,true);
    }
    private static int preview(CommandSourceStack s) {
        ServerLevel l=validLevel(s);if(l==null)return 0;
        if(undoLevel!=null && undoImages!=null) {
            chat(s,"[R10 STOP] Already placed in this session; inspect or undo first.");return 0;
        }
        clearPreview();
        if(!preflight(l)) {
            chat(s,"[R10 STOP] Preview guard failed. NO WORLD CHANGES. Upload latest/debug logs.");return 0;
        }
        if(!(s.getEntity() instanceof ServerPlayer p))return 0;
        lastPreviewAt=System.nanoTime();previewLevel=l;previewPlayer=p.getUUID();
        LOG.info("[TC-DEV51B-R10] PREVIEW_PASS edits=55 remove=24 stationFill=26 villageFill=5 unsafe=0 rails=49 stairs=6 forcedChunks=0 worldEdits=0 previewAgeLimit=120s");
        chat(s,"[R10 PREVIEW PASS] 55 exact edits: 24 sand removals, 26 station fills, 5 village fills. No world changes.");
        chat(s,"[R10] BACK UP THE WORLD. If visually ready, within 120s run /tcdev51b10 station place confirm.");
        return 1;
    }
    private static Path writeBackup(List<BlockState> before) {
        Path path=Path.of("logs","DEV51B-R10-UNDO-BEFORE-"+System.currentTimeMillis()+".tsv");
        try {
            Files.createDirectories(path.getParent());
            StringBuilder b=new StringBuilder("# DEV51B R10 BEFORE-image. Human recovery reference; same-session undo only.\n");
            b.append("x\ty\tz\tbefore\tafter\treason\n");
            for(int i=0;i<PROPOSALS.length;i++) {
                int[] p=PROPOSALS[i];
                b.append(p[0]).append('\t').append(p[1]).append('\t').append(p[2]).append('\t')
                  .append(id(before.get(i))).append('\t').append(p[4]==0?"minecraft:air":"minecraft:sand")
                  .append('\t').append(R9_REASONS[p[5]]).append('\n');
            }
            Files.writeString(path,b.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            return path;
        }catch(IOException|SecurityException ex) {
            LOG.error("[TC-DEV51B-R10] BACKUP_WRITE_FAILED {}",ex.toString());return null;
        }
    }
    private static int place(CommandSourceStack s) {
        ServerLevel l=validLevel(s);if(l==null)return 0;
        if(undoLevel!=null && undoImages!=null) {
            chat(s,"[R10 STOP] Existing R10 placement has an available undo; no second placement.");return 0;
        }
        if(!(s.getEntity() instanceof ServerPlayer p))return 0;
        long elapsed=System.nanoTime()-lastPreviewAt;
        if(lastPreviewAt==0 || previewLevel!=l || !p.getUUID().equals(previewPlayer)
          || elapsed<0 || elapsed>PREVIEW_WINDOW_NS) {
            clearPreview();chat(s,"[R10 STOP] Preview missing/expired; run /tcdev51b10 station preview again.");return 0;
        }
        clearPreview();
        if(!preflight(l)) {chat(s,"[R10 STOP] World changed after preview; no blocks placed.");return 0;}
        List<BlockState> originals=new ArrayList<>();
        for(int[] e:PROPOSALS)originals.add(l.getBlockState(new BlockPos(e[0],e[1],e[2])));
        Path backup=writeBackup(originals);
        if(backup==null) {chat(s,"[R10 STOP] Cannot write before-image backup. Nothing placed.");return 0;}
        int wrote=0;
        try {
            // Place supported sand fills first, bottom-up, then remove raised sand.
            // Flag 2 updates clients without neighboring physics during this guarded transaction.
            for(int phase=0;phase<2;phase++) {
                for(int y=63;y<=68;y++)for(int[] edit:PROPOSALS) {
                    if(edit[1]!=y || (phase==0?edit[4]!=1:edit[4]!=0))continue;
                    BlockPos pos=new BlockPos(edit[0],edit[1],edit[2]);
                    BlockState want=edit[4]==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
                    if(!l.setBlock(pos,want,2) || !l.getBlockState(pos).equals(want))
                        throw new IllegalStateException("SetBlock failed at "+xyz(pos));
                    wrote++;
                }
            }
            if(wrote!=55 || !snapshotMatches(l,true) || !checkPlan(l,true)
                || !protectedWorld(l,true) || !walkablePost(l))
                throw new IllegalStateException("Post-place verification failed");
            undoImages=originals;undoLevel=l;undoPlayer=p.getUUID();
            LOG.info("[TC-DEV51B-R10] PLACED edits={} removes=24 stationFills=26 villageFills=5 protectedRails=49 stairs=6 backup={} forcedChunks=0",wrote,backup.toAbsolutePath());
            chat(s,"[R10 PLACED] 55 edits committed and postchecked. Inspect the station and village approach.");
            chat(s,"[R10] Same-session undo: /tcdev51b10 station undo BEFORE leaving this world.");
            return 1;
        }catch(Exception error) {
            LOG.error("[TC-DEV51B-R10] PLACEMENT_FAILED editsApplied={} reason={} -- attempting full rollback",wrote,error.toString());
            boolean restored=restore(l,originals);
            LOG.error("[TC-DEV51B-R10] ROLLBACK result={} exact55={} -- CHECK WORLD",restored,restored);
            chat(s,restored?"[R10 STOP] Placement failed; 55 preimages restored. Send logs.":
                "[R10 CRITICAL] Rollback verification FAILED; STOP and restore backed-up world!");
            return 0;
        }
    }
    private static boolean walkablePost(ServerLevel l) {
        // Check final visible heights against R9b's post-plan values as computed BEFORE editing.
        // All 53 modified columns must still have a two-air-headroom walking surface.
        Set<Long> keys=new HashSet<>();for(int[] e:PROPOSALS)keys.add(key(e[0],e[2]));
        int missing=0,steep=0,checked=0;
        for(long k:keys) {
            int x=(int)(k>>32),z=(int)k;
            double h=walkHeight(l,x,z,false);
            if(Double.isNaN(h)) {missing++;continue;}
            for(Direction d:Direction.Plane.HORIZONTAL) {
                int nx=x+d.getStepX(),nz=z+d.getStepZ();
                if(!l.hasChunkAt(new BlockPos(nx,66,nz))) {missing++;continue;}
                if(columnStructural(l,nx,nz))continue;
                double n=walkHeight(l,nx,nz,false);
                if(Double.isNaN(n))continue;
                checked++;if(Math.abs(h-n)>1.01)steep++;
            }
        }
        LOG.info("[TC-DEV51B-R10] POST_WALK_SURFACE modifiedColumns={} edges={} steep={} missing={}",keys.size(),checked,steep,missing);
        return missing==0 && steep==0;
    }
    private static boolean restore(ServerLevel l,List<BlockState> before) {
        if(before==null||before.size()!=PROPOSALS.length)return false;
        boolean all=true;
        for(int i=PROPOSALS.length-1;i>=0;i--) {
            int[] e=PROPOSALS[i];BlockPos p=new BlockPos(e[0],e[1],e[2]);
            try {
                if(!l.hasChunkAt(p)) {all=false;continue;}
                if(!l.getBlockState(p).equals(before.get(i)))l.setBlock(p,before.get(i),2);
                if(!l.getBlockState(p).equals(before.get(i)))all=false;
            }catch(Exception ex) {all=false;LOG.error("[TC-DEV51B-R10] ROLLBACK_CELL_FAILED pos={} {}",xyz(p),ex.toString());}
        }
        return all && snapshotMatches(l,false) && protectedWorld(l,false) && checkPlan(l,false);
    }
    private static int undo(CommandSourceStack s) {
        ServerLevel l=validLevel(s);if(l==null)return 0;
        if(!(s.getEntity() instanceof ServerPlayer p))return 0;
        if(undoImages==null || undoLevel!=l || !p.getUUID().equals(undoPlayer)) {
            chat(s,"[R10 STOP] No same-session undo for this player/world; restore world backup if session was closed.");return 0;
        }
        if(!checkPlan(l,true) || !snapshotMatches(l,true) || !protectedWorld(l,true)) {
            chat(s,"[R10 STOP] Placed terrain changed since R10; undo not automatic. Restore world backup if necessary.");return 0;
        }
        boolean restored=restore(l,undoImages);
        if(restored) {
            undoImages=null;undoLevel=null;undoPlayer=null;clearPreview();
            LOG.info("[TC-DEV51B-R10] UNDO_PASS restored=55 worldBaseline=R7 rails=49 stairs=6");
            chat(s,"[R10 UNDO PASS] All 55 blocks restored to saved R7 baseline; check appearance.");return 1;
        }
        LOG.error("[TC-DEV51B-R10] UNDO_FAILED restoration incomplete; restore backed-up world");
        chat(s,"[R10 CRITICAL] Undo incomplete. STOP; restore backed-up world.");return 0;
    }
    private static boolean willBeSand(BlockPos p) {
        for(int[] edit:PROPOSALS)if(edit[0]==p.getX()&&edit[1]==p.getY()&&edit[2]==p.getZ())return edit[4]==1;
        return false;
    }
    private static BlockState stateWithPlan(ServerLevel l,BlockPos pos,boolean projected) {
        if(projected)for(int[] e:PROPOSALS)if(e[0]==pos.getX()&&e[1]==pos.getY()&&e[2]==pos.getZ())
            return e[4]==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
        return l.getBlockState(pos);
    }
    private static double walkHeight(ServerLevel l,int x,int z,boolean projected) {
        for(int y=67;y>=63;y--) {
            BlockPos pos=new BlockPos(x,y,z);
            BlockState v=stateWithPlan(l,pos,projected);
            if(!isGround(v)||!stateWithPlan(l,pos.above(),projected).isAir()
               ||!stateWithPlan(l,pos.above(2),projected).isAir())continue;
            if(v.is(Blocks.SANDSTONE_SLAB))return y+0.5;
            if(v.is(Blocks.SANDSTONE_STAIRS))return y+0.75;
            return y+1.0;
        }
        return Double.NaN;
    }
    private static boolean columnStructural(ServerLevel l,int x,int z) {
        for(int y=65;y<=69;y++) {
            BlockState b=l.getBlockState(new BlockPos(x,y,z));
            if(!b.isAir()&&!isGround(b))return true;
        }
        return false;
    }
    private static boolean isGround(BlockState s) {
        return s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.SANDSTONE)
          ||s.is(Blocks.SMOOTH_SANDSTONE)||s.is(Blocks.CUT_SANDSTONE)
          ||s.is(Blocks.SANDSTONE_SLAB)||s.is(Blocks.SANDSTONE_STAIRS)
          ||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.DIRT_PATH)||s.is(Blocks.DIRT)
          ||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.GRAVEL)
          ||s.is(Blocks.STONE)||s.is(Blocks.TERRACOTTA)||s.is(Blocks.CLAY);
    }
    private static boolean isRail(BlockState b) {
        String path=BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
        return b.is(BlockTags.RAILS)||path.contains("track")||path.contains("rail");
    }
    private static String id(BlockState b) {return BuiltInRegistries.BLOCK.getKey(b.getBlock()).toString();}
    private static String xyz(BlockPos p) {return p.getX()+","+p.getY()+","+p.getZ();}
    private static String observed(ServerLevel l,BlockPos p) {return l.hasChunkAt(p)?l.getBlockState(p).toString():"UNLOADED";}
    private static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    private static void chat(CommandSourceStack s,String m) {s.sendSuccess(()->Component.literal(m),false);}
}
