package traincraft.lighting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** DEV55L1: distinct transient air light; never replaces an existing block. */
public final class Dev55FleetLightBlock extends Block {
    public Dev55FleetLightBlock() {
        super(BlockBehaviour.Properties.of().noCollission().noOcclusion()
            .lightLevel(s -> 13).noLootTable().replaceable());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState s,net.minecraft.world.level.BlockGetter l,BlockPos p,CollisionContext c) {return Shapes.empty();}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        Dev55FleetGroundLights.expire(level,pos,state);
    }
}
