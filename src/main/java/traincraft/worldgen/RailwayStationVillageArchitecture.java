package traincraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DEV51B R40L — fresh surface station architecture, inside the already
 * validated railway-station footprint x=-10..10, z=-4..13, y=0..6.
 *
 * NEVER edits the rails (z=0), station staff/job sites (y=0,z=9),
 * underground stations, terrain, or previously generated stations.
 * All decoration positions are fixed offsets, after new-station placement.
 */
public final class RailwayStationVillageArchitecture {
    private RailwayStationVillageArchitecture() {}

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }
    private static void airOnly(ServerLevel level, BlockPos c, int x, int y, int z, BlockState put) {
        BlockPos p = c.offset(x,y,z);
        if (level.hasChunkAt(p) && level.getBlockState(p).isAir()) level.setBlock(p,put,3);
    }
    private static void replace(ServerLevel level, BlockPos c, int x, int y, int z,
                                BlockState put, Block... from) {
        BlockPos p = c.offset(x,y,z);
        if (!level.hasChunkAt(p)) return;
        BlockState current=level.getBlockState(p);
        for (Block candidate : from) {
            if (current.is(candidate)) {
                level.setBlock(p,put,3);
                return;
            }
        }
    }
    private static void roofEdge(ServerLevel level, BlockPos c, Block block, Block old,
                                 boolean aged) {
        BlockState north=stair(aged?Blocks.DARK_OAK_STAIRS:block, Direction.NORTH);
        BlockState south=stair(aged?Blocks.DARK_OAK_STAIRS:block, Direction.SOUTH);
        for (int x=-6;x<=6;x++) {
            // Place on top of the sealed y=5 office ceiling, never replace it.
            airOnly(level,c,x,6,5,north);
            airOnly(level,c,x,6,11,south);
        }
    }
    private static void ridge(ServerLevel level, BlockPos c, Block stairs, Block slab,
                              int z,boolean aged) {
        Block stairBlock=aged?Blocks.DARK_OAK_STAIRS:stairs;
        Block slabBlock=aged?Blocks.DARK_OAK_SLAB:slab;
        for(int x=-6;x<=6;x++) {
            airOnly(level,c,x,6,z-1,stair(stairBlock,Direction.NORTH));
            airOnly(level,c,x,6,z,slabBlock.defaultBlockState());
            airOnly(level,c,x,6,z+1,stair(stairBlock,Direction.SOUTH));
        }
    }
    private static void timberCorners(ServerLevel level, BlockPos c, Block trim, Block wall) {
        for (int x:new int[]{-6,6}) for(int z:new int[]{5,11})
            for(int y=0;y<=4;y++) replace(level,c,x,y,z,trim.defaultBlockState(),wall);
    }
    private static void chimney(ServerLevel level, BlockPos c, int x,int z) {
        airOnly(level,c,x,6,z,Blocks.COBBLESTONE_WALL.defaultBlockState());
    }
    private static void plains(ServerLevel level,BlockPos c,boolean aged) {
        roofEdge(level,c,Blocks.OAK_STAIRS,Blocks.OAK_PLANKS,aged);
        ridge(level,c,Blocks.OAK_STAIRS,Blocks.OAK_SLAB,8,aged);
        // Distinct broad eaves: the original platform roof and rail clearance stay intact.
        for(int z=5;z<=11;z++) for(int x:new int[]{-7,7})
            airOnly(level,c,x,5,z,Blocks.OAK_SLAB.defaultBlockState());
        timberCorners(level,c,Blocks.STRIPPED_OAK_LOG,Blocks.OAK_PLANKS);
        for(int x:new int[]{-5,5})
            replace(level,c,x,3,5,Blocks.COBBLESTONE.defaultBlockState(),Blocks.OAK_PLANKS);
    }
    private static void desert(ServerLevel level,BlockPos c,boolean aged) {
        // Flat sandstone rooftop with a raised parapet: not a wooden gable.
        BlockState parapet=(aged?Blocks.RED_SANDSTONE_WALL:Blocks.SANDSTONE_WALL).defaultBlockState();
        for(int x=-6;x<=6;x++) {
            airOnly(level,c,x,6,5,parapet);
            airOnly(level,c,x,6,11,parapet);
        }
        for(int z=6;z<=10;z++) for(int x:new int[]{-6,6})
            airOnly(level,c,x,6,z,parapet);
        // Decorative keystone arch lintels above the existing glazed windows.
        for(int x:new int[]{-3,-2,2,3})
            replace(level,c,x,3,5,stair(Blocks.SMOOTH_SANDSTONE_STAIRS,Direction.NORTH),Blocks.SANDSTONE);
        for(int x:new int[]{-5,-1,1,5})
            replace(level,c,x,4,5,Blocks.CHISELED_SANDSTONE.defaultBlockState(),Blocks.SANDSTONE);
        // Pilasters give the office the same visual language as a desert village.
        timberCorners(level,c,Blocks.CUT_SANDSTONE,Blocks.SANDSTONE);
    }
    private static void savanna(ServerLevel level,BlockPos c,boolean aged) {
        // One-sided elevated acacia shed roof, deliberately off-center.
        Block roof=aged?Blocks.DARK_OAK_SLAB:Blocks.ACACIA_SLAB;
        for(int x=-6;x<=6;x++) for(int z=5;z<=7;z++)
            airOnly(level,c,x,6,z,roof.defaultBlockState());
        // Open-air waiting canopy: the rails are at z=0, all braces are z>=2.
        for(int x=-6;x<=6;x++) {
            replace(level,c,x,5,2,stair(Blocks.ACACIA_STAIRS,Direction.NORTH),Blocks.ACACIA_SLAB);
            replace(level,c,x,5,4,stair(Blocks.ACACIA_STAIRS,Direction.SOUTH),Blocks.ACACIA_SLAB);
        }
        for(int x:new int[]{-7,7})
            airOnly(level,c,x,3,3,Blocks.ACACIA_FENCE.defaultBlockState());
        for(int x:new int[]{-5,5})
            replace(level,c,x,3,5,Blocks.ORANGE_TERRACOTTA.defaultBlockState(),Blocks.ACACIA_PLANKS);
        timberCorners(level,c,Blocks.STRIPPED_ACACIA_LOG,Blocks.ACACIA_PLANKS);
    }
    private static void taiga(ServerLevel level,BlockPos c,boolean aged) {
        roofEdge(level,c,Blocks.SPRUCE_STAIRS,Blocks.SPRUCE_PLANKS,aged);
        ridge(level,c,Blocks.SPRUCE_STAIRS,Blocks.SPRUCE_SLAB,8,aged);
        for(int z=5;z<=11;z++) for(int x:new int[]{-7,7})
            airOnly(level,c,x,5,z,Blocks.SPRUCE_SLAB.defaultBlockState());
        timberCorners(level,c,Blocks.STRIPPED_SPRUCE_LOG,Blocks.SPRUCE_PLANKS);
        chimney(level,c,-4,10);
    }
    private static void snowy(ServerLevel level,BlockPos c,boolean aged) {
        // A stepped snow-loaded roof and sheltered platform rather than an exposed depot.
        for(int x=-6;x<=6;x++) {
            for(int z:new int[]{5,6,10,11})
                airOnly(level,c,x,6,z,Blocks.SNOW.defaultBlockState());
            for(int z=7;z<=9;z++)
                airOnly(level,c,x,6,z,Blocks.SNOW_BLOCK.defaultBlockState());
        }
        timberCorners(level,c,Blocks.STRIPPED_SPRUCE_LOG,Blocks.SPRUCE_PLANKS);
        for(int x:new int[]{-5,5})
            replace(level,c,x,3,5,Blocks.POLISHED_DIORITE.defaultBlockState(),Blocks.SPRUCE_PLANKS);
        chimney(level,c,4,10);
    }

    /** Called once, and only for fresh, just-built surface stations. */
    public static void decorate(ServerLevel level,BlockPos center,String style,String kind) {
        if (level==null || center==null || style==null) return;
        boolean aged="ABANDONED".equals(kind) || "OVERGROWN".equals(kind);
        switch(style) {
            case "DESERT" -> desert(level,center,aged);
            case "SAVANNA" -> savanna(level,center,aged);
            case "TAIGA" -> taiga(level,center,aged);
            case "SNOWY" -> snowy(level,center,aged);
            case "PLAINS" -> plains(level,center,aged);
            default -> { /* Fail closed for any unknown biome family. */ }
        }
    }
}
