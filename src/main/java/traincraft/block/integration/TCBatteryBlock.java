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
import org.jetbrains.annotations.Nullable;

/**
 * TC Battery.
 *
 * The old Traincraft releases used external RE/UE batteries rather than a
 * Traincraft battery block.  This port-added registry ID is therefore used as
 * the modern FE storage/buffer for TC rail electrification.
 */
public final class TCBatteryBlock extends BaseEntityBlock {
    public TCBatteryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TCBatteryBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TCBatteryBlockEntity battery) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal(
                        "TC Battery: " + battery.getEnergyStored() + " / "
                                + TCBatteryBlockEntity.CAPACITY + " FE"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
