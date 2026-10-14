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

/** Invisible, air-only, server-controlled light; never placed by a player. */
public final class Dev44TransientHeadlightBlock extends Block {
    public Dev44TransientHeadlightBlock() {
        super(BlockBehaviour.Properties.of().noCollission().noOcclusion()
                .lightLevel(state -> 13).noLootTable().replaceable());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                         BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Dev44HeadlightWorldLights.expire(level,pos,state);
    }
}
