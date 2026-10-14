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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DEV51B R9: precise read-only proposal from R8 full survey and F3 locations 40-block radius saved-R7 station terrain survey.
 * NO setBlock/removeBlock, no chunk forcing, no placement/undo commands.
 * Classifies POSSIBLE holes and sand obstructions; does not call them safe edits.
 * Generates detailed TSV outside the world save, under the run/logs folder.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BStationGradePreviewR9 {
    private static final Logger LOG = LoggerFactory.getLogger("TC-DEV51B-R9");
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final BlockPos STATION = new BlockPos(89, 67, 3164);
    private static final int RADIUS = 40;
    private static final int MIN_Y = 62;
    private static final int MAX_Y = 72;
    private static final int DETAIL_LOG_LIMIT = 600;
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
    private Dev51BStationGradePreviewR9() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tcdev51b9")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("station")
                .then(Commands.literal("survey").executes(context -> survey(context.getSource())))
                .then(Commands.literal("preview").executes(context -> preview(context.getSource()))));
        event.getDispatcher().register(root);
    }

    private static int survey(CommandSourceStack s) {
        ServerLevel level = s.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)
                || !WORLD.equals(level.getServer().getWorldData().getLevelName())) {
            chat(s, "[DEV51B-R9 STOP] Only the named terrain test world in the Overworld. No scan run.");
            return 0;
        }
        if (!(s.getEntity() instanceof ServerPlayer player)
                || player.blockPosition().distSqr(STATION) > 64L * 64L) {
            chat(s, "[DEV51B-R9 STOP] Operator must stand within 64 blocks of 89 67 3164.");
            return 0;
        }
        LOG.info("[TC-DEV51B-R9] START world={} center={} radius={} yRange={}..{} action=READ_ONLY", WORLD, STATION, RADIUS, MIN_Y, MAX_Y);
        int stairsOk = 0, pathOk = 0, railOk = 0, unloadedRail = 0, slabsOk = 0;
        for (int i = 0; i < STAIRS.length; i++) {
            BlockPos p = STAIRS[i];
            BlockState expected = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, STAIR_FACING[i]);
            boolean ok = level.hasChunkAt(p) && level.getBlockState(p).equals(expected);
            if (ok) stairsOk++;
            LOG.info("[TC-DEV51B-R9] FROZEN_STAIR index={} pos={} expected={} actual={} match={}",
                i, xyz(p), expected, stateAt(level, p), ok);
        }
        for (BlockPos p : PATH) {
            boolean ok = level.hasChunkAt(p) && level.getBlockState(p).is(Blocks.SANDSTONE);
            if (ok) pathOk++;
            LOG.info("[TC-DEV51B-R9] FROZEN_PATH pos={} actual={} match={}", xyz(p), stateAt(level,p), ok);
        }
        BlockPos endpoint = new BlockPos(95,65,3143);
        boolean endpointOk = level.hasChunkAt(endpoint) && level.getBlockState(endpoint).is(Blocks.DIRT_PATH);
        LOG.info("[TC-DEV51B-R9] VILLAGE_ENDPOINT pos={} actual={} dirtPathMatch={}", xyz(endpoint), stateAt(level,endpoint), endpointOk);
        for (BlockPos p : R6_SLABS) {
            boolean ok = level.hasChunkAt(p) && level.getBlockState(p).is(Blocks.SANDSTONE_SLAB);
            if (ok) slabsOk++;
            LOG.info("[TC-DEV51B-R9] R6_SLAB pos={} actual={} match={}", xyz(p), stateAt(level,p), ok);
        }
        for (int z = 3140; z <= 3188; z++) {
            BlockPos p = new BlockPos(89, 67, z);
            if (!level.hasChunkAt(p)) unloadedRail++;
            else if (isRail(level.getBlockState(p))) railOk++;
            else LOG.warn("[TC-DEV51B-R9] RAIL_NOT_FOUND pos={} state={}", xyz(p), stateAt(level,p));
        }
        int r7Air = 0, r7Unexpected = 0, r7Unloaded = 0;
        for (int[] p : R7_REMOVED) {
            BlockPos pos = new BlockPos(p[0],p[1],p[2]);
            if (!level.hasChunkAt(pos)) { r7Unloaded++; continue; }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) r7Air++;
            else {
                r7Unexpected++;
                LOG.warn("[TC-DEV51B-R9] R7_POSTIMAGE_CHANGED pos={} actual={} expected=minecraft:air", xyz(pos), state);
            }
        }

        Map<Long, Cell> grid = new HashMap<>();
        int allColumns = 0, unloaded = 0;
        int[] areaColumns = new int[3];
        int[] areaUnloaded = new int[3];
        for (int z = STATION.getZ()-RADIUS; z <= STATION.getZ()+RADIUS; z++) {
            for (int x = STATION.getX()-RADIUS; x <= STATION.getX()+RADIUS; x++) {
                int dx=x-STATION.getX(), dz=z-STATION.getZ();
                if (dx*dx+dz*dz > RADIUS*RADIUS) continue;
                int area = areaOf(x,z);
                allColumns++;
                if (!level.hasChunkAt(new BlockPos(x,67,z))) {
                    unloaded++;
                    areaUnloaded[area]++;
                    continue;
                }
                Cell c = sample(level,x,z,area);
                grid.put(key(x,z),c);
                areaColumns[area]++;
            }
        }
        List<Cell> suspicious = new ArrayList<>();
        int[] pits = new int[3], raised = new int[3], islands = new int[3];
        int[] stairSand = new int[3], voids = new int[3], underfloor = new int[3];
        int[] sandColumns = new int[3], protectedCells = new int[3];
        int allSandBlocks = 0;
        for (Cell c : grid.values()) {
            if (c.sandBlocks > 0) {
                sandColumns[c.area]++;
                allSandBlocks += c.sandBlocks;
            }
            if (c.structural) protectedCells[c.area]++;
            classify(c,grid);
            if (c.pit) pits[c.area]++;
            if (c.raised) raised[c.area]++;
            if (c.island) islands[c.area]++;
            if (c.stairSand) stairSand[c.area]++;
            if (c.voidGround) voids[c.area]++;
            if (c.underfloorSand) underfloor[c.area]++;
            if (c.area!=2 && c.hasConcern()) suspicious.add(c);
        }
        suspicious.sort((a,b) -> {
            int cmp = Integer.compare(a.area,b.area);
            if (cmp!=0) return cmp;
            cmp = Integer.compare(a.z,b.z);
            if (cmp!=0) return cmp;
            return Integer.compare(a.x,b.x);
        });
        int logged = 0;
        for (Cell c : suspicious) {
            if (logged++ >= DETAIL_LOG_LIMIT) break;
            LOG.info("[TC-DEV51B-R9] CONCERN area={} x={} z={} flags={} floorY={} floor={} topSandY={} sandCount={} y64={} y65={} y66={} y67={} y68={} neighbors={} protection={}",
                areaName(c.area),c.x,c.z,c.flags(),c.floorY,c.floorName(),c.topSandY,c.sandBlocks,
                c.atName(64),c.atName(65),c.atName(66),c.atName(67),c.atName(68),
                c.neighborDetail, c.protection());
        }
        if (suspicious.size()>DETAIL_LOG_LIMIT)
            LOG.warn("[TC-DEV51B-R9] CONCERN_LOG_TRUNCATED shown={} total={} COMPLETE_TSV_HAS_ALL_CELLS",DETAIL_LOG_LIMIT,suspicious.size());
        for (int z=3138;z<=3181;z++) {
            int minX=(z<=3155?91:92), maxX=(z<=3155?100:108);
            StringBuilder row=new StringBuilder();
            for(int x=minX;x<=maxX;x++) {
                Cell c=grid.get(key(x,z));
                row.append(c==null?'?':c.symbol());
            }
            LOG.info("[TC-DEV51B-R9] SURFACE_MAP z={} x={}..{} symbols={} key=!pit ^raised SstairSand ~island vvoid uunderfloor #structure +slab .low_sand =other ?unloaded",z,minX,maxX,row);
        }
        int guardProblems = (stairsOk!=6?1:0)+(pathOk!=6?1:0)+(!endpointOk?1:0)
            +(slabsOk!=3?1:0)+(railOk!=49?1:0)+(unloadedRail!=0?1:0)
            +(r7Unexpected!=0?1:0)+(r7Unloaded!=0?1:0);
        for(int a=0;a<3;a++)
            LOG.info("[TC-DEV51B-R9] AREA area={} loadedColumns={} unloaded={} sandColumns={} suspectedPits={} raisedSand={} sandIslands={} stairSand={} voidFloor={} possibleUnderfloorSand={} structuralColumns={}",
                areaName(a),areaColumns[a],areaUnloaded[a],sandColumns[a],pits[a],raised[a],islands[a],stairSand[a],voids[a],underfloor[a],protectedCells[a]);
        LOG.info("[TC-DEV51B-R9] SAVED_BASELINE stairs={}/6 r3Path={}/6 endpoint={} r6Slabs={}/3 railway={}/49 railUnloaded={} r7Air={}/112 r7Unexpected={} r7Unloaded={} guardProblems={}",
            stairsOk,pathOk,endpointOk,slabsOk,railOk,unloadedRail,r7Air,r7Unexpected,r7Unloaded,guardProblems);
        LOG.info("[TC-DEV51B-R9] SURVEY_SUMMARY center={} radius={} y={}..{} columns={} loaded={} unloaded={} sampledSandBlocks={} coreAndPathConcerns={} logged={} readOnly=true forcedChunks=0 worldEdits=0",
            xyz(STATION), RADIUS,MIN_Y,MAX_Y,allColumns,grid.size(),unloaded,allSandBlocks,suspicious.size(),Math.min(DETAIL_LOG_LIMIT,suspicious.size()));
        Path report=writeReport(grid, allColumns, unloaded, guardProblems);
        if(report!=null) {
            LOG.info("[TC-DEV51B-R9] COMPLETE_REPORT path={} rows={} format=TSV includesEveryLoadedColumnAndY62..Y72", report.toAbsolutePath(), grid.size());
            chat(s,"[DEV51B-R9] Full TSV report: " + report.toAbsolutePath());
        } else {
            LOG.warn("[TC-DEV51B-R9] REPORT_NOT_WRITTEN; summary and concern details remain in latest/debug logs");
            chat(s,"[DEV51B-R9] TSV report unavailable; upload latest/debug logs.");
        }
        chat(s,"[DEV51B-R9] radius=40 y=62..72 scanned="+allColumns+" loaded="+grid.size()+" unloaded="+unloaded
            +" concerns="+suspicious.size()+" guardProblems="+guardProblems+" WORLD CHANGES=0");
        if (guardProblems!=0) {
            chat(s,"[DEV51B-R9 STOP] Saved-world baseline does not match. No preview accepted; do not force.");
            return 0;
        }
        chat(s,"[DEV51B-R9] Baseline survey passed. Run /tcdev51b9 station preview for exact 55-edit candidate. No world changes.");
        return 1;
    }

    private static Cell sample(ServerLevel level, int x,int z,int area) {
        Cell c=new Cell(x,z,area);
        for(int y=MIN_Y;y<=MAX_Y;y++) {
            BlockState state=level.getBlockState(new BlockPos(x,y,z));
            c.layers[y-MIN_Y]=id(state);
            if(isSand(state)) {
                c.sandBlocks++;
                c.topSandY=Math.max(c.topSandY,y);
            }
            if(y>=65 && y<=69 && !state.isAir() && !isGround(state)) c.structural=true;
        }
        for (int y=70;y>=63;y--) {
            if(!isGround(level.getBlockState(new BlockPos(x,y,z)))) continue;
            BlockState above=level.getBlockState(new BlockPos(x,y+1,z));
            if(!above.isAir()) continue;
            c.floorY=y;
            c.floorState=id(level.getBlockState(new BlockPos(x,y,z)));
            break;
        }
        c.air65=c.atName(65).equals("minecraft:air");
        c.air66=c.atName(66).equals("minecraft:air");
        c.air67=c.atName(67).equals("minecraft:air");
        c.railBuffer = x>=86 && x<=92 && z>=3138 && z<=3190;
        c.nearStair = closeToAny(x,z,STAIRS,2);
        c.nearPath = closeToAny(x,z,PATH,2);
        c.underfloorSand=c.area==0 && c.sandBlocks>0 && c.structural
            && (isSandName(c.atName(64)) || isSandName(c.atName(65)) || isSandName(c.atName(66)));
        return c;
    }
    private static void classify(Cell c,Map<Long,Cell> grid) {
        int high=0,low=0,neighbors=0,distinctLow=0;
        StringBuilder desc=new StringBuilder();
        for(Direction d : Direction.Plane.HORIZONTAL) {
            Cell next=grid.get(key(c.x+d.getStepX(),c.z+d.getStepZ()));
            if(next==null || next.floorY<0 || next.structural) continue;
            neighbors++;
            if(desc.length()>0) desc.append(';');
            desc.append(d.getName()).append(':').append(next.floorY);
            if(c.floorY>=0 && next.floorY >= c.floorY+1) high++;
            if(c.floorY>=0 && next.floorY <= c.floorY-1) {low++;distinctLow++;}
        }
        c.neighborDetail=desc.toString();
        if(c.area==2 || c.railBuffer || c.structural) return;
        // The scan is diagnostic: these are suspects, not authoritative excavation instructions.
        c.voidGround=c.floorY>=0 && c.floorY<=64 && c.air65 && c.air66 && high>=2;
        c.pit=c.floorY>=0 && c.floorY<=65 && high>=2 && !c.nearPath;
        c.raised=c.topSandY>=66 && (low>=1 || c.nearStair || c.nearPath);
        c.island=c.topSandY>=66 && c.floorY>=66 && distinctLow>=2;
        c.stairSand=c.topSandY>=66 && c.nearStair;
        // Do not count load-bearing natural sand below a building as safe to excavate.
    }
    private static Path writeReport(Map<Long,Cell> grid,int total,int skipped,int guards) {
        Path p=Path.of("logs", "DEV51B-R9-SURVEY-"+System.currentTimeMillis()+".tsv");
        try {
            Files.createDirectories(p.getParent());
            try(BufferedWriter out=Files.newBufferedWriter(p,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                out.write("# DEV51B R8 READ ONLY. center=89,67,3164 radius=40 y=62..72; worldEdits=0; forcedChunks=0\n");
                out.write("# ColumnsInRadius="+total+" unloaded="+skipped+" guardProblems="+guards+". No proposed edits or permissions to edit world.\n");
                out.write("area\tx\tz\tfloorY\tfloorBlock\ttopSandY\tsandCount\tflags\tprotection\tneighbors\ty62\ty63\ty64\ty65\ty66\ty67\ty68\ty69\ty70\ty71\ty72\n");
                List<Cell> ordered=new ArrayList<>(grid.values());
                ordered.sort((a,b)->{int q=Integer.compare(a.z,b.z);return q!=0?q:Integer.compare(a.x,b.x);});
                for(Cell c:ordered) {
                    out.write(areaName(c.area)+"\t"+c.x+"\t"+c.z+"\t"+c.floorY+"\t"+c.floorName()+"\t"+c.topSandY
                        +"\t"+c.sandBlocks+"\t"+c.flags()+"\t"+c.protection()+"\t"+c.neighborDetail);
                    for(String name:c.layers) out.write("\t"+name);
                    out.write('\n');
                }
            }
            return p;
        } catch(IOException | SecurityException e) {
            LOG.warn("[TC-DEV51B-R9] REPORT_WRITE_FAILED path={} reason={}",p,e.toString());
            return null;
        }
    }
    private static boolean isSand(BlockState s) { return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND); }
    private static boolean isSandName(String name) {return "minecraft:sand".equals(name)||"minecraft:red_sand".equals(name);}
    private static boolean isGround(BlockState s) {
        return isSand(s) || s.is(Blocks.SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE)
            || s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.SANDSTONE_SLAB) || s.is(Blocks.SANDSTONE_STAIRS)
            || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.DIRT)
            || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.GRAVEL)
            || s.is(Blocks.STONE) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.CLAY);
    }
    private static boolean isRail(BlockState s) {
        String path=BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return s.is(BlockTags.RAILS) || path.contains("track") || path.contains("rail");
    }
    private static String id(BlockState s) {
        ResourceLocation loc=BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return loc==null?"unknown":loc.toString();
    }
    private static int areaOf(int x,int z) {
        if(x>=92 && x<=108 && z>=3155 && z<=3181) return 0; // station grading footprint
        if(x>=91 && x<=100 && z>=3138 && z<=3154) return 1; // village path approach
        return 2; // context-only 40-radius landscape
    }
    private static String areaName(int n) {return n==0?"STATION":n==1?"VILLAGE_APPROACH":"CONTEXT";}
    private static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    private static boolean closeToAny(int x,int z,BlockPos[] points,int d) {
        for(BlockPos p:points) if(Math.abs(p.getX()-x)<=d && Math.abs(p.getZ()-z)<=d) return true;
        return false;
    }
    private static String xyz(BlockPos p) {return p.getX()+","+p.getY()+","+p.getZ();}
    private static String stateAt(ServerLevel l,BlockPos p) {
        return l.hasChunkAt(p)?l.getBlockState(p).toString():"UNLOADED";
    }
    private static void chat(CommandSourceStack s,String msg) {
        s.sendSuccess(()->Component.literal(msg),false);
    }
    private static final class Cell {
        final int x,z,area;
        final String[] layers=new String[MAX_Y-MIN_Y+1];
        int floorY=-1, topSandY=-1, sandBlocks;
        String floorState="none",neighborDetail="none";
        boolean structural,railBuffer,nearStair,nearPath,underfloorSand;
        boolean pit,raised,island,stairSand,voidGround,air65,air66,air67;
        Cell(int x,int z,int area){this.x=x;this.z=z;this.area=area;}
        String atName(int y){return layers[y-MIN_Y];}
        String floorName(){return floorState;}
        String protection(){return (structural?"STRUCTURE;":"")+(railBuffer?"RAIL_BUFFER;":"")
            +(nearStair?"NEAR_STAIR;":"")+(nearPath?"NEAR_PATH;":"")+(underfloorSand?"SUPPORT_SAND;":"");}
        boolean hasConcern(){return pit||raised||island||stairSand||voidGround||underfloorSand;}
        String flags(){return (pit?"SUSPECT_HOLE;":"")+(voidGround?"VOID_FLOOR;":"")
            +(raised?"RAISED_SAND;":"")+(island?"SAND_ISLAND;":"")
            +(stairSand?"NEAR_STAIR_SAND;":"")+(underfloorSand?"POSSIBLE_UNDERFLOOR_SAND_DO_NOT_DIG;":"")
            +(!hasConcern()?"NONE":"");}
        char symbol(){if(pit)return '!';if(voidGround)return 'v';if(stairSand)return 'S';
            if(island)return '~';if(raised)return '^';if(underfloorSand)return 'u';
            if(structural)return '#';if(floorState.endsWith("sandstone_slab"))return '+';
            if(floorY<=65)return '.';return '=';}
    }

    // Offline prepared from exact user-provided R8 5025-column TSV and F3 block positions.
    // Values: x,y,z, beforeAir(0)/beforeSand(1), afterAir(0)/afterSand(1), reasonCode.
    // Not one of these rows is applied to the game world by this class.
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

    private static int preview(CommandSourceStack source) {
        // Run all the full R8-equivalent saved-world checks before accepting any candidate.
        if (survey(source)!=1) return 0;
        ServerLevel level=source.getLevel();
        int problems=0;
        for(String[] row:R9_COLUMN_FINGERPRINTS) {
            int x=Integer.parseInt(row[0]),z=Integer.parseInt(row[1]);
            BlockPos base=new BlockPos(x,66,z);
            if(!level.hasChunkAt(base)) {
                problems++;
                LOG.warn("[TC-DEV51B-R9] SNAPSHOT_UNLOADED x={} z={}",x,z);
                continue;
            }
            for(int y=64;y<=68;y++) {
                String expected=row[y-62];
                String actual=id(level.getBlockState(new BlockPos(x,y,z)));
                if(!expected.equals(actual)) {
                    problems++;
                    LOG.warn("[TC-DEV51B-R9] SNAPSHOT_MISMATCH x={} y={} z={} expected={} actual={}",x,y,z,expected,actual);
                }
            }
        }
        for(int[] p:R9_PROPOSALS) {
            BlockPos pos=new BlockPos(p[0],p[1],p[2]);
            BlockState before=stateWithPlan(level,pos,false);
            String expected=p[3]==0?"minecraft:air":"minecraft:sand";
            if(!expected.equals(id(before))) {
                problems++;
                LOG.warn("[TC-DEV51B-R9] PREIMAGE_MISMATCH pos={} expected={} actual={}",xyz(pos),expected,id(before));
            }
            if(p[0]>=86 && p[0]<=92 && p[2]>=3138 && p[2]<=3190) {
                problems++; LOG.warn("[TC-DEV51B-R9] RAIL_BUFFER_EDIT_PROHIBITED pos={}",xyz(pos));
            }
            for(BlockPos stair:STAIRS) if(stair.equals(pos)) { problems++; LOG.warn("[TC-DEV51B-R9] STAIR_EDIT_PROHIBITED pos={}",xyz(pos)); }
            for(BlockPos tile:PATH) if(tile.equals(pos)) { problems++; LOG.warn("[TC-DEV51B-R9] PATH_EDIT_PROHIBITED pos={}",xyz(pos)); }
            for(BlockPos slab:R6_SLABS) if(slab.equals(pos)) { problems++; LOG.warn("[TC-DEV51B-R9] R6_SLAB_EDIT_PROHIBITED pos={}",xyz(pos)); }
            if(p[4]==1 && !isGround(level.getBlockState(pos.below())) && !willBeSand(pos.below())) {
                problems++;
                LOG.warn("[TC-DEV51B-R9] FILL_UNSUPPORTED pos={} beneath={}",xyz(pos),stateAt(level,pos.below()));
            }
            // No proposed removal may expose air underneath a non-air structure.
            if(p[4]==0 && !level.getBlockState(pos.above()).isAir()) {
                problems++;
                LOG.warn("[TC-DEV51B-R9] REMOVE_OVERHEAD_OBSTRUCTION pos={} above={}",xyz(pos),stateAt(level,pos.above()));
            }
        }
        // Test all impacted horizontal edges using virtual proposed states, never setBlock.
        int checked=0, beforeSteep=0, afterSteep=0, worsened=0, missingSurface=0;
        java.util.Set<Long> modified=new java.util.HashSet<>();
        for(int[] p:R9_PROPOSALS) modified.add(key(p[0],p[2]));
        for(long k:modified) {
            int x=(int)(k>>32),z=(int)k;
            double oldH=walkHeight(level,x,z,false),newH=walkHeight(level,x,z,true);
            if(Double.isNaN(oldH)||Double.isNaN(newH)) { missingSurface++; continue; }
            for(Direction d:Direction.Plane.HORIZONTAL) {
                int nx=x+d.getStepX(),nz=z+d.getStepZ();
                if(!level.hasChunkAt(new BlockPos(nx,66,nz))) { problems++; continue; }
                if(columnStructural(level,nx,nz)) continue;
                double neighborBefore=walkHeight(level,nx,nz,false), neighborAfter=walkHeight(level,nx,nz,true);
                if(Double.isNaN(neighborBefore)||Double.isNaN(neighborAfter)) continue;
                checked++;
                double before=Math.abs(oldH-neighborBefore),after=Math.abs(newH-neighborAfter);
                if(before>1.01)beforeSteep++;
                if(after>1.01)afterSteep++;
                if(after>before+0.01 && after>1.01) worsened++;
            }
        }
        if(worsened!=0) problems+=worsened;
        if(afterSteep>beforeSteep) problems++;
        int removals=0,stationFills=0,villageFills=0;
        for(int[] p:R9_PROPOSALS) {
            if(p[5]==0)removals++;
            else if(p[5]==1)stationFills++;
            else villageFills++;
            LOG.info("[TC-DEV51B-R9] PROPOSED pos={},{},{} {} -> {} type={} WORLD_EDITS=0",p[0],p[1],p[2],
                p[3]==0?"minecraft:air":"minecraft:sand",p[4]==0?"minecraft:air":"minecraft:sand",R9_REASONS[p[5]]);
        }
        LOG.info("[TC-DEV51B-R9] HELD_F3 x=102 y=66 z=3160 current=sand reason=Y66_GROUND_DO_NOT_REMOVE_WITHOUT_REPLACING_WALK_SURFACE");
        LOG.info("[TC-DEV51B-R9] HELD_F3 x=100 y=66 z=3169 current=sand reason=Y66_GROUND_DO_NOT_REMOVE_WITHOUT_REPLACING_WALK_SURFACE");
        LOG.info("[TC-DEV51B-R9] HELD_F3 x=104 y=66 z=3174 current=sand reason=Y66_GROUND_DO_NOT_REMOVE_WITHOUT_REPLACING_WALK_SURFACE");
        LOG.info("[TC-DEV51B-R9B] WALK_HEIGHT_CHECK minGroundY=63 maxGroundY=67 requiredAirAbove=2 ignoredElevatedStationBlocks=true");
        LOG.info("[TC-DEV51B-R9] POST_EDGE_CHECK modifiedColumns={} inspectedEdges={} preExistingSteep={} proposedSteep={} newUnsafeSteep={} missingSurfaces={}",
            modified.size(),checked,beforeSteep,afterSteep,worsened,missingSurface);
        Path report=writeR9Plan(removals,stationFills,villageFills,problems,checked,beforeSteep,afterSteep);
        if(report==null) problems++;
        LOG.info("[TC-DEV51B-R9] PREVIEW_SUMMARY removals={} stationFills={} villageFills={} total={} guardProblems={} railsFrozen=49 stairsFrozen=6 r7AirFrozen=112 previewOnly=true worldEdits=0 forcedChunks=0",
            removals,stationFills,villageFills,R9_PROPOSALS.length,problems);
        if(problems>0) {
            chat(source,"[DEV51B-R9 STOP] Preview blocked: "+problems+" guard problems. Do not force or place any edits.");
            return 0;
        }
        chat(source,"[DEV51B-R9 PREVIEW] "+R9_PROPOSALS.length+" exact edits: "+removals+" raised sand->air, "+stationFills+" station pit fills, "+villageFills+" village fills; guards PASS.");
        chat(source,"[DEV51B-R9] Proposed terrain steep edges before="+beforeSteep+" after="+afterSteep+" newUnsafe="+worsened+". NO WORLD CHANGES.");
        if(report!=null)chat(source,"[DEV51B-R9] Full proposed-edit report: "+report.toAbsolutePath());
        chat(source,"[DEV51B-R9] STOP AFTER PREVIEW. Send latest/debug logs and DEV51B-R9-PREVIEW-*.tsv; NO PLACE COMMAND exists.");
        return 1;
    }
    private static boolean willBeSand(BlockPos p) {
        for(int[] edit:R9_PROPOSALS) if(edit[0]==p.getX() && edit[1]==p.getY() && edit[2]==p.getZ())return edit[4]==1;
        return false;
    }
    private static BlockState stateWithPlan(ServerLevel level,BlockPos p,boolean projected) {
        if(projected)for(int[] edit:R9_PROPOSALS)
            if(edit[0]==p.getX() && edit[1]==p.getY() && edit[2]==p.getZ())
                return edit[4]==0?Blocks.AIR.defaultBlockState():Blocks.SAND.defaultBlockState();
        return level.getBlockState(p);
    }
    private static double walkHeight(ServerLevel l,int x,int z,boolean projected) {
        // R9b: the station's walkable terrain lies at Y63..67. Terracotta and
        // other overhead structure at Y70 are NOT pedestrian ground. Require
        // two air blocks of headroom for a real walking surface.
        for(int y=67;y>=63;y--) {
            BlockPos pos=new BlockPos(x,y,z);
            BlockState v=stateWithPlan(l,pos,projected),above=stateWithPlan(l,pos.above(),projected);
            if(!isGround(v)||!above.isAir()||!stateWithPlan(l,pos.above(2),projected).isAir())continue;
            if(v.is(Blocks.SANDSTONE_SLAB))return y+0.5;
            if(v.is(Blocks.SANDSTONE_STAIRS))return y+0.75;
            return y+1.0;
        }
        return Double.NaN;
    }
    private static boolean columnStructural(ServerLevel l,int x,int z) {
        for(int y=65;y<=69;y++) {
            BlockState s=l.getBlockState(new BlockPos(x,y,z));
            if(!s.isAir() && !isGround(s)) return true;
        }
        return false;
    }
    private static Path writeR9Plan(int rem,int sf,int vf,int problems,int edges,int pre,int post) {
        Path path=Path.of("logs","DEV51B-R9-PREVIEW-"+System.currentTimeMillis()+".tsv");
        try {
            Files.createDirectories(path.getParent());
            try(BufferedWriter out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                out.write("# R9 read-only target plan from R8 5025-column terrain scan and F3 recording. NO WORLD EDITS.\\n".replace("\\n","\n"));
                out.write("# changes="+R9_PROPOSALS.length+" removals="+rem+" stationFills="+sf+" villageFills="+vf+" guards="+problems+" beforeSteep="+pre+" afterSteep="+post+"\n");
                out.write("x\ty\tz\tbefore\tafter\treason\tbaselineY64\tbaselineY65\tbaselineY66\tbaselineY67\tbaselineY68\n");
                for(int[] p:R9_PROPOSALS) {
                    String[] snap=null;
                    for(String[] row:R9_COLUMN_FINGERPRINTS)
                        if(Integer.parseInt(row[0])==p[0] && Integer.parseInt(row[1])==p[2]) { snap=row;break; }
                    out.write(p[0]+"\t"+p[1]+"\t"+p[2]+"\t"+(p[3]==0?"minecraft:air":"minecraft:sand")+"\t"
                        +(p[4]==0?"minecraft:air":"minecraft:sand")+"\t"+R9_REASONS[p[5]]);
                    for(int n=2;n<=6;n++)out.write("\t"+(snap==null?"UNKNOWN":snap[n]));
                    out.write('\n');
                }
                out.write("# HELD_F3 positions: 102 66 3160; 100 66 3169; 104 66 3174. These are Y66 ground, removal would expose Y65 pits.\n");
            }
            LOG.info("[TC-DEV51B-R9] COMPLETE_PLAN path={} proposals={} unsafe={}",path.toAbsolutePath(),R9_PROPOSALS.length,problems);
            return path;
        } catch(IOException|SecurityException ex) {
            LOG.warn("[TC-DEV51B-R9] PLAN_WRITE_FAILED reason={}",ex.toString());
            return null;
        }
    }

}
