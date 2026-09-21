package traincraft.block.track;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Standard Step 9.3a guide block: up to 32 occupied cells. */
public class LegacyBatchRailBlock extends AbstractLegacyBatchRailBlock {
    public static final IntegerProperty PART =
            IntegerProperty.create("assembly_part", 0, 31);

    // Step 9.3d-t8v3: visual ownership can differ from logical/movement
    // ownership for the original TC4.5 shared-gag Small Diagonal handoff.
    // False is the default for every ordinary batch piece.
    public static final BooleanProperty ORIGINAL_VISUAL_ROOT =
            BooleanProperty.create("original_visual_root");

    public LegacyBatchRailBlock(BlockBehaviour.Properties properties,
                                LegacyBatchTrackSpec spec,
                                String dropItemId) {
        super(properties, spec, dropItemId);
    }

    @Override
    public IntegerProperty partProperty() {
        return PART;
    }

    /**
     * Step 9.3d-t8v3 - preserve the proven logical assembly root while
     * carrying a render-only marker through every occupied guide state.
     * The three-argument stateForPart path is unchanged for all normal pieces.
     */
    public BlockState stateForPart(Direction facing, int part, boolean alternate,
                                   boolean originalVisualRoot) {
        return stateForPart(facing, part, alternate)
                .setValue(ORIGINAL_VISUAL_ROOT, originalVisualRoot);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, ASSEMBLY_FACING, ORIGINAL_VISUAL_ROOT);
    }
}
