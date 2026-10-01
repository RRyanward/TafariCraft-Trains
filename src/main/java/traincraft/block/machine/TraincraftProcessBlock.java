package traincraft.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCBlockEntities;

public final class TraincraftProcessBlock extends BaseEntityBlock {
    public enum Mode { DISTILLERY, HEARTH }

    private final Mode mode;

    public TraincraftProcessBlock(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TraincraftProcessBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity existing = level.getBlockEntity(pos);
        if (!(existing instanceof TraincraftProcessBlockEntity)) {
            TraincraftProcessBlockEntity created = new TraincraftProcessBlockEntity(pos, state);
            level.setBlockEntity(created);
            existing = created;
        }

        if (existing instanceof TraincraftProcessBlockEntity machine
                && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, machine, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(
                type,
                TCBlockEntities.PROCESS_MACHINE.get(),
                TraincraftProcessBlockEntity::serverTick);
    }
}