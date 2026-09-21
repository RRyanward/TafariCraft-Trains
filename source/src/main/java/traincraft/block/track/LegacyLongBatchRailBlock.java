package traincraft.block.track;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Long Step 9.3a guide block used only where a classic piece needs >32 cells. */
public final class LegacyLongBatchRailBlock extends AbstractLegacyBatchRailBlock {
    public static final IntegerProperty PART =
            IntegerProperty.create("assembly_part", 0, 63);

    public LegacyLongBatchRailBlock(BlockBehaviour.Properties properties,
                                    LegacyBatchTrackSpec spec,
                                    String dropItemId) {
        super(properties, spec, dropItemId);
    }

    @Override
    public IntegerProperty partProperty() {
        return PART;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, ASSEMBLY_FACING);
    }
}
