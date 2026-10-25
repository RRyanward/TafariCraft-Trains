package traincraft.item.track;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DEV58G12R7: Shared, conservative rail-footprint vegetation policy.
 * Used by native placement and the client ghost. Never clears live rails,
 * solid terrain, crops/saplings, tall flowers or fluid-filled cells.
 */
public final class TrackPlacementVegetation {
    private TrackPlacementVegetation() {}

    // Only these known vegetation states may be destroyed deliberately.
    // Keep ordinary replaceable non-plants distinct from vegetation.
    private static boolean isClearableVegetation(BlockState state) {
        return state.is(Blocks.TORCHFLOWER) || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(Blocks.GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
    }

    private static boolean isProtected(BlockState state) {
        return BaseRailBlock.isRail(state)
                || state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof SaplingBlock
                || state.is(Blocks.TORCHFLOWER_CROP)
                || state.is(Blocks.PITCHER_CROP)
                || state.is(Blocks.PITCHER_PLANT)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.PUMPKIN_STEM)
                || state.is(Blocks.ATTACHED_MELON_STEM)
                || state.is(Blocks.ATTACHED_PUMPKIN_STEM)
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.COCOA);
    }

    public static boolean canReplace(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        if (state.isAir()) return true;
        if (isProtected(state)) return false;
        return isClearableVegetation(state) || state.canBeReplaced();
    }

    public static boolean isPlant(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && !isProtected(state) && isClearableVegetation(state);
    }
}
