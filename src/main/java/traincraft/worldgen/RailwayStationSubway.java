package traincraft.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import traincraft.registry.TCBlocks;

/**
 * DEV51B R38 - a player-operated, bounded underground station (NOT a route generator).
 * Strictly NEW-station-only. Natural material preflight over every excavation cell,
 * no caves/fluids/ores/BE/village structures, no forced chunks, no automated services.
 * Stops/refuses before editing if any required chunk is unloaded or terrain is unsafe.
 */
public final class RailwayStationSubway {
    private RailwayStationSubway() {}
    public static final int DEPTH = 9;

    /** Stable variety roll; chance applies within existing eligible NEW station sites only. */
    public static boolean selected(long seed, BlockPos surface) {
        long z = seed ^ Long.rotateLeft(surface.asLong(), 17) ^ 0x387e9a5c6bc1d247L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return Long.remainderUnsigned(z ^ (z >>> 31), 100L) < 10;
    }

    public static BlockPos railCenter(BlockPos surface) { return surface.below(DEPTH); }

    private static boolean earth(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.GRANITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT);
    }

    private static boolean safeTarget(ServerLevel level, BlockPos p, int surfaceY) {
        if (!level.hasChunkAt(p) || p.getY() <= level.getMinBuildHeight() + 2
                || p.getY() >= level.getMaxBuildHeight() - 3) return false;
        if (level.getBlockEntity(p) != null) return false;
        if (level.structureManager().getStructureWithPieceAt(p, StructureTags.VILLAGE).isValid()) return false;
        BlockState before = level.getBlockState(p);
        // Fresh, flat exterior headspace is expected to be air only at/above surface level.
        return p.getY() >= surfaceY ? before.isAir() : earth(before);
    }

    /**
     * Read-only site and full excavation audit. Returns a reason on rejection.
     * Existing station footprint was independently checked by the caller.
     */
    public static String problem(ServerLevel level, BlockPos surface) {
        if (surface.getY() <= level.getMinBuildHeight() + DEPTH + 3) return "DEPTH_OR_WORLD_LIMIT";
        Map<BlockPos,BlockState> footprint = planned(surface);
        for (BlockPos p : footprint.keySet()) {
            if (!safeTarget(level, p, surface.getY()))
                return "UNSAFE_EXCAVATION:" + p.toShortString();
        }
        // Exterior entrance must remain flat and unloaded/protected chunks must HOLD.
        for (int z=14;z<=19;z++) for (int x=-2;x<=2;x++) {
            BlockPos ground=surface.offset(x,-1,z);
            if (!level.hasChunkAt(ground)
                    || level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            ground.getX(),ground.getZ()) != surface.getY()
                    || !earth(level.getBlockState(ground))) return "ENTRANCE_TERRAIN:"+ground.toShortString();
        }
        return null;
    }

    private static void put(Map<BlockPos,BlockState> plan,BlockPos p,BlockState state) {
        plan.put(p, state);
    }

    /** All modified positions are enumerated before touching any live chunk. */
    private static Map<BlockPos,BlockState> planned(BlockPos surface) {
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
        BlockPos c=railCenter(surface);
        // Fully enclosed rectangular subway room. Floor -1, track 0, roof +6.
        for(int x=-10;x<=10;x++) for(int z=-4;z<=8;z++) for(int y=-1;y<=6;y++) {
            boolean floor=y==-1, roof=y==6, wall=x==-10||x==10||z==-4||z==8;
            BlockState material=floor?Blocks.STONE_BRICKS.defaultBlockState():
                    roof?Blocks.DEEPSLATE_BRICKS.defaultBlockState():
                    wall?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState();
            put(plan,c.offset(x,y,z),material);
        }
        // A clear corridor at the SOUTH wall leading into the graded staircase.
        for(int x=-1;x<=1;x++) for(int y=0;y<=3;y++) put(plan,c.offset(x,y,8),Blocks.AIR.defaultBlockState());
        BlockState rail=TCBlocks.TRACK_NORMAL.get().defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        BlockState station=TCBlocks.TRACK_STATION.get().defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        for(int x=-8;x<=8;x++) {
            put(plan,c.offset(x,-1,0),Blocks.GRAVEL.defaultBlockState());
            put(plan,c.offset(x,0,0),x==0?station:rail);
        }
        // Off-rail, raised passenger platform; workstation bays remain player-accessible.
        for(int x=-8;x<=8;x++) for(int z=2;z<=5;z++)
            put(plan,c.offset(x,-1,z),Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(plan,c.offset(-5,0,6),TCBlocks.STATION_OFFICE_DESK.get().defaultBlockState());
        put(plan,c.offset(0,0,6),TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState());
        put(plan,c.offset(5,0,6),TCBlocks.TICKET_BOOTH.get().defaultBlockState());
        for(int x:new int[]{-7,7})for(int z:new int[]{-2,3,6})
            put(plan,c.offset(x,4,z),Blocks.SEA_LANTERN.defaultBlockState());
        // One block of rise per southward cell. Keep 3 blocks head clearance.
        for(int z=9;z<=17;z++) {
            int stairY=surface.getY()-(17-z);
            for(int x=-1;x<=1;x++) {
                BlockPos step=new BlockPos(surface.getX()+x,stairY,surface.getZ()+z);
                put(plan,step,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
                put(plan,step.above(),Blocks.AIR.defaultBlockState());
                put(plan,step.above(2),Blocks.AIR.defaultBlockState());
            }
            if(z==11||z==14)put(plan,new BlockPos(surface.getX(),stairY+3,surface.getZ()+z),
                    Blocks.SEA_LANTERN.defaultBlockState());
        }
        // Flat safe surface landing and two lantern pillars; no station rail at grade.
        for(int z=18;z<=19;z++) for(int x=-1;x<=1;x++)
            put(plan,surface.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState());
        for(int x:new int[]{-2,2}) {
            put(plan,surface.offset(x,0,18),Blocks.STONE_BRICKS.defaultBlockState());
            put(plan,surface.offset(x,1,18),Blocks.STONE_BRICKS.defaultBlockState());
            put(plan,surface.offset(x,2,18),Blocks.SEA_LANTERN.defaultBlockState());
        }
        return plan;
    }

    /** One guarded placement transaction; no scheduled edits, no entities or train movement. */
    public static BlockPos build(ServerLevel level,BlockPos surface) {
        String reason=problem(level,surface);
        if(reason!=null) throw new IllegalStateException("R38_SAFETY_HOLD:"+reason);
        Map<BlockPos,BlockState> plan=planned(surface);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        for(BlockPos p:plan.keySet())before.put(p,level.getBlockState(p));
        try {
            // Interior before rails and the final staircase; all intended positions were preflighted.
            for(Map.Entry<BlockPos,BlockState> e:plan.entrySet()) {
                // Air headspace and some naturally equal cells need no write;
                // vanilla Level#setBlock may return false for an unchanged state.
                if(level.getBlockState(e.getKey()).equals(e.getValue())) continue;
                if(!level.setBlock(e.getKey(),e.getValue(),18))
                    throw new IllegalStateException("WRITE_FAILED:"+e.getKey().toShortString());
            }
            BlockPos c=railCenter(surface);
            for(int x=-8;x<=8;x++) {
                BlockPos rail=c.offset(x,0,0);
                if(!level.getBlockState(rail).is(x==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                        || !level.getBlockState(rail.below()).is(Blocks.GRAVEL))
                    throw new IllegalStateException("RAIL_READBACK_FAILED:"+x);
            }
            for(int z=9;z<=17;z++) {
                BlockPos step=surface.offset(0,-(17-z),z);
                if(!level.getBlockState(step).is(Blocks.STONE_BRICK_STAIRS))
                    throw new IllegalStateException("STAIR_READBACK_FAILED:"+z);
            }
            return c;
        }catch(RuntimeException e) {
            List<Map.Entry<BlockPos,BlockState>> old=new ArrayList<>(before.entrySet());
            java.util.Collections.reverse(old);
            for(Map.Entry<BlockPos,BlockState> prior:old)
                level.setBlock(prior.getKey(),prior.getValue(),18);
            throw e;
        }
    }
}
