package traincraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R37: bounded, fresh-station-only cosmetic variants. No rail/path/locomotive logic.
 * All cells stay inside the existing worldgen clearance footprint (-10..10,-4..13).
 * This class does not generate stations independently; RailwayVillageWorldgen
 * calls it only after its existing full-footprint site safety check.
 */
public final class RailwayStationSurfaceVariants {
    public enum Kind { REGULAR, ABANDONED, OVERGROWN, CONSTRUCTION }
    private RailwayStationSurfaceVariants() {}

    // Stable per world seed and station position; never based on wall clock or reload order.
    public static Kind choose(long worldSeed, BlockPos station) {
        long h = worldSeed ^ Long.rotateLeft(station.asLong(), 23) ^ 0x2fa6c70b3d815e49L;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        int roll = (int)Long.remainderUnsigned(h ^ (h >>> 31), 100L);
        if (roll < 60) return Kind.REGULAR;
        if (roll < 75) return Kind.ABANDONED;
        if (roll < 90) return Kind.OVERGROWN;
        return Kind.CONSTRUCTION;
    }

    /** Staff never spawn at abandoned/overgrown stops. Construction is staffed. */
    public static boolean hasStaff(Kind kind) {
        return kind == Kind.REGULAR || kind == Kind.CONSTRUCTION;
    }

    private static void swap(ServerLevel level, BlockPos p, BlockState from, BlockState to) {
        if (level.getBlockState(p).is(from.getBlock())) level.setBlock(p, to, 2);
    }
    private static void emptyIf(ServerLevel level, BlockPos p, BlockState from) {
        swap(level, p, from, Blocks.AIR.defaultBlockState());
    }
    private static boolean isAny(BlockState state, Block... blocks) {
        for (Block block : blocks) if (state.is(block)) return true;
        return false;
    }
    private static void setIfAny(ServerLevel level, BlockPos p, BlockState to, Block... from) {
        BlockState current = level.getBlockState(p);
        if (isAny(current, from)) level.setBlock(p, to, 2);
    }
    private static void clearIfAny(ServerLevel level, BlockPos p, Block... from) {
        setIfAny(level, p, Blocks.AIR.defaultBlockState(), from);
    }
    private static void weatherPlatform(ServerLevel level, BlockPos p, boolean overgrown) {
        BlockState current = level.getBlockState(p);
        if (current.is(Blocks.STONE_BRICKS)) {
            level.setBlock(p, (overgrown ? Blocks.MOSS_BLOCK : Blocks.MOSSY_STONE_BRICKS).defaultBlockState(), 2);
        } else if (current.is(Blocks.COBBLESTONE)) {
            level.setBlock(p, (overgrown ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSSY_COBBLESTONE).defaultBlockState(), 2);
        } else if (current.is(Blocks.SMOOTH_SANDSTONE) || current.is(Blocks.SANDSTONE)) {
            level.setBlock(p, (overgrown ? Blocks.SANDSTONE : Blocks.CUT_SANDSTONE).defaultBlockState(), 2);
        }
    }
    private static void weatherRoof(ServerLevel level, BlockPos p) {
        BlockState current = level.getBlockState(p);
        if (current.is(Blocks.SPRUCE_PLANKS) || current.is(Blocks.OAK_PLANKS) || current.is(Blocks.ACACIA_PLANKS)) {
            level.setBlock(p, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
        } else if (current.is(Blocks.SMOOTH_SANDSTONE) || current.is(Blocks.SANDSTONE)) {
            level.setBlock(p, Blocks.RED_SANDSTONE.defaultBlockState(), 2);
        }
    }
    private static void weatherCanopy(ServerLevel level, BlockPos p) {
        BlockState current = level.getBlockState(p);
        if (current.is(Blocks.SPRUCE_SLAB) || current.is(Blocks.OAK_SLAB) || current.is(Blocks.ACACIA_SLAB)) {
            level.setBlock(p, Blocks.DARK_OAK_SLAB.defaultBlockState(), 2);
        } else if (current.is(Blocks.CUT_SANDSTONE_SLAB) || current.is(Blocks.SMOOTH_SANDSTONE_SLAB)) {
            level.setBlock(p, Blocks.RED_SANDSTONE_SLAB.defaultBlockState(), 2);
        }
    }
    private static void addIfAir(ServerLevel level, BlockPos p, BlockState to) {
        if (level.getBlockState(p).isAir()) level.setBlock(p, to, 2);
    }

    public static void decorate(ServerLevel level, BlockPos c, Kind kind) {
        if (kind == Kind.REGULAR) return;
        // IMPORTANT: no method in here addresses z=0 rail cells or any rail block.
        // Fresh-only, at most 32 decoration changes in the accepted cleared area.
        if (kind == Kind.ABANDONED || kind == Kind.OVERGROWN) {
            for (int x : new int[] {-5,-2,2,5}) {
                weatherPlatform(level,c.offset(x,-1,3), true);
                weatherPlatform(level,c.offset(x,-1,8), false);
            }
            for (int x : new int[] {-4,0,4})
                weatherRoof(level,c.offset(x,5,8));
            // No villager job sites or trading menus in deserted stations.
        }
        if (kind == Kind.ABANDONED) {
            // Former office fixtures removed only after fresh construction; no old saves touched.
            removeOfficeWorkstations(level,c);
            for (int x : new int[] {-5,0,5})
                weatherRoof(level,c.offset(x,5,7));
            for (int x : new int[] {-2,3})
                weatherCanopy(level,c.offset(x,5,3));
        } else if (kind == Kind.OVERGROWN) {
            removeOfficeWorkstations(level,c);
            // The original platform floor is a solid natural growth anchor.
            // Cosmetics-only: no rail (z=0), clearance or train control touched.
            for(int x:new int[]{-5,-3,3,5})
                weatherPlatform(level,c.offset(x,-1,3), true);
            for(int x:new int[]{-5,-3,3,5}) {
                addIfAir(level,c.offset(x,0,3),Blocks.MOSS_CARPET.defaultBlockState());
                addIfAir(level,c.offset(x,6,8),Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
            }
            for(int x:new int[]{-3,3})
                addIfAir(level,c.offset(x,6,10),Blocks.MOSS_CARPET.defaultBlockState());
        } else if (kind == Kind.CONSTRUCTION) {
            for(int x:new int[]{-3,0,3})
                clearIfAny(level,c.offset(x,5,8), Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.ACACIA_PLANKS,
                        Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.RED_SANDSTONE);
            // Scaffolding's vanilla default DISTANCE=7 is the falling/unsupported state.
            // Use distance=0 only when the base cell has solid support, and never
            // overwrite any pre-existing world block or auto-extend beyond the footprint.
            BlockPos scaffoldBase = c.offset(8,0,10);
            BlockState scaffoldSupport = level.getBlockState(scaffoldBase.below());
            if (scaffoldSupport.isFaceSturdy(level,scaffoldBase.below(),Direction.UP)) {
                for(int y=0;y<=3;y++) {
                    BlockState supportedScaffold = Blocks.SCAFFOLDING.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE,0)
                        .setValue(net.minecraft.world.level.block.ScaffoldingBlock.BOTTOM,y==0);
                    addIfAir(level,scaffoldBase.above(y),supportedScaffold);
                }
            }
            addIfAir(level,c.offset(-8,0,10),Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.UP));
            addIfAir(level,c.offset(8,0,7),Blocks.YELLOW_CONCRETE.defaultBlockState());
        }
    }

    private static void removeOfficeWorkstations(ServerLevel level, BlockPos c) {
        // These cells hold Traincraft workstations, not vanilla blocks.
        // Only apply to NEWly built stations, under the caller's clearance gate.
        // Workstation identity is verified by name to avoid a registry dependency here.
        emptyIf(level,c.offset(-4,0,9),traincraft.registry.TCBlocks.STATION_OFFICE_DESK.get().defaultBlockState());
        emptyIf(level,c.offset(0,0,9),traincraft.registry.TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState());
        emptyIf(level,c.offset(4,0,9),traincraft.registry.TCBlocks.TICKET_BOOTH.get().defaultBlockState());
    }
}
