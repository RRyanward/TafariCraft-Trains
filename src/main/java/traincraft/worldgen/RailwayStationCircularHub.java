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
import net.minecraft.world.phys.AABB;
import traincraft.registry.TCBlocks;

/**
 * DEV51B R39: a circular, player-operated underground station with two side rooms.
 * Not an automated railway hub: no switches, generated connections, trains or dispatch.
 * Only NEW stations, entire excavation audited before any edit, no force-loaded chunks.
 */
public final class RailwayStationCircularHub {
    private RailwayStationCircularHub() {}
    public static final int DEPTH=12;
    public static final int RADIUS=12;
    private static final int SOUTH_STAIR_FIRST=13;
    private static final int SOUTH_STAIR_LAST=23;

    public static boolean selected(long seed,BlockPos surface) {
        long n=seed^Long.rotateLeft(surface.asLong(),29)^0x39c1acda2e7f9b15L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return Long.remainderUnsigned(n^(n>>>31),100L)<5;
    }
    public static BlockPos railCenter(BlockPos surface) {return surface.below(DEPTH);}
    private static boolean earth(BlockState b) {
        return b.is(Blocks.STONE)||b.is(Blocks.DEEPSLATE)||b.is(Blocks.TUFF)
                ||b.is(Blocks.GRANITE)||b.is(Blocks.ANDESITE)||b.is(Blocks.DIORITE)
                ||b.is(Blocks.DIRT)||b.is(Blocks.GRASS_BLOCK)||b.is(Blocks.COARSE_DIRT);
    }
    private static void put(Map<BlockPos,BlockState> m,BlockPos at,BlockState b) {m.put(at,b);}

    /** All changed cells, including air excavations, are enumerated in advance. */
    private static Map<BlockPos,BlockState> planned(BlockPos surface) {
        Map<BlockPos,BlockState> p=new LinkedHashMap<>();
        BlockPos c=railCenter(surface);
        // A genuinely circular 25-block diameter footprint (NOT a rounded rectangle).
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            int distance=x*x+z*z;
            if(distance>RADIUS*RADIUS)continue;
            for(int y=-1;y<=6;y++){
                boolean boundary=distance>=121;
                BlockState state=y==-1?Blocks.POLISHED_ANDESITE.defaultBlockState()
                    :y==6?Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                    :boundary?Blocks.STONE_BRICKS.defaultBlockState()
                    :Blocks.AIR.defaultBlockState();
                put(p,c.offset(x,y,z),state);
            }
        }
        // Branches NORTH, each an enclosed 7x8 passenger-service room.
        for(int westSign:new int[]{-1,1}){
            for(int x=2;x<=8;x++)for(int z=-18;z<=-11;z++)for(int y=-1;y<=6;y++){
                int bx=westSign*x;
                boolean wall=x==2||x==8||z==-18||z==-11;
                BlockState material=y==-1?Blocks.STONE_BRICKS.defaultBlockState()
                    :y==6?Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                    :wall?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState();
                put(p,c.offset(bx,y,z),material);
            }
            // Open an exact doorway through the circular ring and branch wall.
            int doorwayX=westSign*5;
            for(int z=-11;z<=-10;z++)for(int y=0;y<=3;y++)
                put(p,c.offset(doorwayX,y,z),Blocks.AIR.defaultBlockState());
            put(p,c.offset(doorwayX,4,-15),Blocks.SEA_LANTERN.defaultBlockState());
        }
        // Railway crosses the circular hall in a straight, usable 17-cell line.
        BlockState rail=TCBlocks.TRACK_NORMAL.get().defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        BlockState station=TCBlocks.TRACK_STATION.get().defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        for(int x=-8;x<=8;x++){
            put(p,c.offset(x,-1,0),Blocks.GRAVEL.defaultBlockState());
            put(p,c.offset(x,0,0),x==0?station:rail);
        }
        // Passenger platforms on both sides of the rails, no obstruction in the lane.
        for(int x=-7;x<=7;x++)for(int z:new int[]{-4,-3,-2,2,3,4})
            put(p,c.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());
        // Two distinct furnished branch rooms: ticket office and passenger waiting room.
        put(p,c.offset(-5,0,-16),TCBlocks.TICKET_BOOTH.get().defaultBlockState());
        put(p,c.offset(5,0,-16),TCBlocks.STATION_OFFICE_DESK.get().defaultBlockState());
        put(p,c.offset(5,0,-13),TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState());
        for(int x:new int[]{-8,0,8})for(int z:new int[]{-6,6})
            put(p,c.offset(x,5,z),Blocks.SEA_LANTERN.defaultBlockState());
        // Doorway at south edge, then 11 rising stairs with three-wide walking space.
        for(int x=-1;x<=1;x++)for(int z=11;z<=12;z++)for(int y=0;y<=3;y++)
            put(p,c.offset(x,y,z),Blocks.AIR.defaultBlockState());
        for(int z=SOUTH_STAIR_FIRST;z<=SOUTH_STAIR_LAST;z++){
            int stepY=surface.getY()-(SOUTH_STAIR_LAST-z)-1;
            for(int x=-1;x<=1;x++){
                BlockPos step=new BlockPos(surface.getX()+x,stepY,surface.getZ()+z);
                put(p,step,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
                for(int clearance=1;clearance<=3;clearance++)put(p,step.above(clearance),Blocks.AIR.defaultBlockState());
            }
            if(z==16||z==20)put(p,new BlockPos(surface.getX(),stepY+4,surface.getZ()+z),Blocks.SEA_LANTERN.defaultBlockState());
        }
        // Street-level landing and two lit stone columns, with no street-level train rails.
        for(int z=24;z<=25;z++)for(int x=-1;x<=1;x++)
            put(p,surface.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState());
        for(int x:new int[]{-2,2}){
            put(p,surface.offset(x,0,24),Blocks.STONE_BRICKS.defaultBlockState());
            put(p,surface.offset(x,1,24),Blocks.STONE_BRICKS.defaultBlockState());
            put(p,surface.offset(x,2,24),Blocks.SEA_LANTERN.defaultBlockState());
        }
        return p;
    }

    public static String problem(ServerLevel level,BlockPos surface) {
        if(surface.getY()<=level.getMinBuildHeight()+DEPTH+3 || surface.getY()>=level.getMaxBuildHeight()-9)
            return "WORLD_HEIGHT_LIMIT";
        Map<BlockPos,BlockState> footprint=planned(surface);
        for(BlockPos b:footprint.keySet()){
            if(!level.hasChunkAt(b))return "UNLOADED:"+b.toShortString();
            if(b.getY()<=level.getMinBuildHeight()+2 || b.getY()>=level.getMaxBuildHeight()-3)
                return "WORLD_LIMIT:"+b.toShortString();
            if(level.getBlockEntity(b)!=null)return "PROTECTED_BLOCK_ENTITY:"+b.toShortString();
            if(level.structureManager().getStructureWithPieceAt(b,StructureTags.VILLAGE).isValid())
                return "VILLAGE_STRUCTURE:"+b.toShortString();
            BlockState existing=level.getBlockState(b);
            if(!(b.getY()<surface.getY()?earth(existing):existing.isAir()))
                return "UNSAFE_EXCAVATION:"+b.toShortString();
        }
        // Surface terrain, including entry steps, must actually be flat.
        for(int x=-2;x<=2;x++)for(int z=23;z<=25;z++){
            BlockPos at=surface.offset(x,-1,z);
            if(!level.hasChunkAt(at) || !earth(level.getBlockState(at))
                    ||level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        at.getX(),at.getZ())!=surface.getY())
                return "ENTRANCE_TERRAIN:"+at.toShortString();
        }
        // Don't excavate through a mob or another player. Read-only query, no tickets.
        BlockPos c=railCenter(surface);
        AABB bounds=new AABB(c.offset(-14,-2,-20),surface.offset(15,9,27));
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds).isEmpty())
            return "LIVING_ENTITY_IN_EXCAVATION";
        return null;
    }
    /** Transactional placement. Rollback restores pre-existing block states on failure. */
    public static BlockPos build(ServerLevel level,BlockPos surface) {
        String hold=problem(level,surface);
        if(hold!=null)throw new IllegalStateException("R39_SAFETY_HOLD:"+hold);
        Map<BlockPos,BlockState> plan=planned(surface);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        for(BlockPos p:plan.keySet())before.put(p,level.getBlockState(p));
        try {
            for(var cell:plan.entrySet()){
                if(level.getBlockState(cell.getKey()).equals(cell.getValue()))continue;
                if(!level.setBlock(cell.getKey(),cell.getValue(),18))
                    throw new IllegalStateException("HUB_WRITE_FAILED:"+cell.getKey().toShortString());
            }
            BlockPos center=railCenter(surface);
            for(int x=-8;x<=8;x++){
                BlockPos pos=center.offset(x,0,0);
                if(!level.getBlockState(pos).is(x==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                        ||!level.getBlockState(pos.below()).is(Blocks.GRAVEL))
                    throw new IllegalStateException("HUB_RAIL_READBACK:"+x);
            }
            for(int z=SOUTH_STAIR_FIRST;z<=SOUTH_STAIR_LAST;z++){
                int y=surface.getY()-(SOUTH_STAIR_LAST-z)-1;
                if(!level.getBlockState(new BlockPos(surface.getX(),y,surface.getZ()+z)).is(Blocks.STONE_BRICK_STAIRS))
                    throw new IllegalStateException("HUB_STAIR_READBACK:"+z);
            }
            if(!level.getBlockState(center.offset(-5,0,-16)).is(TCBlocks.TICKET_BOOTH.get())
                ||!level.getBlockState(center.offset(5,0,-16)).is(TCBlocks.STATION_OFFICE_DESK.get()))
                throw new IllegalStateException("HUB_BRANCH_ROOMS_READBACK");
            return center;
        } catch(RuntimeException problem){
            List<Map.Entry<BlockPos,BlockState>> reverse=new ArrayList<>(before.entrySet());
            java.util.Collections.reverse(reverse);
            for(var old:reverse)level.setBlock(old.getKey(),old.getValue(),18);
            throw problem;
        }
    }
}
