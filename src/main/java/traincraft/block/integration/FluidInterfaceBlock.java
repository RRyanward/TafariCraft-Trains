package traincraft.block.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Pipe-friendly fluid transfer buffer. Place exactly one block UNDER the Fluid Transfer Track. */
public final class FluidInterfaceBlock extends BaseEntityBlock {
    public FluidInterfaceBlock(Properties properties) { super(properties); }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidInterfaceBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (!(tile instanceof FluidInterfaceBlockEntity dock)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (FluidUtil.interactWithFluidHandler(player, hand, dock.getFluidHandler())) {
                dock.setChanged();
                return InteractionResult.CONSUME;
            }
            FluidStack fluid = dock.getTank().getFluid();
            String name = fluid.isEmpty() ? "Empty" : String.valueOf(ForgeRegistries.FLUIDS.getKey(fluid.getFluid()));
            player.displayClientMessage(Component.literal(
                    "TCT Fluid Interface: " + name + " - " + fluid.getAmount()
                            + "/" + FluidInterfaceBlockEntity.CAPACITY_MB
                            + " mB | Forge pipe on any side"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
