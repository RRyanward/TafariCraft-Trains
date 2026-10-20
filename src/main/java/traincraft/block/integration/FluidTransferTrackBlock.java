package traincraft.block.integration;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import traincraft.entity.train.freight.TctTanker;

/**
 * DEV47 fluid station track: same strict one-block-below interface contract
 * and stopped-car settle behavior as the accepted Cargo Transfer Track.
 * Pipes connect to the FLUID INTERFACE block below the rail, never to the moving entity.
 */
public final class FluidTransferTrackBlock extends RailBlock {
    public enum Mode { LOAD, UNLOAD }
    public static final int SETTLE_TICKS = 10;
    public static final int CYCLE_TICKS = 10;
    public static final int MB_PER_CYCLE = 1000;
    private static final double STOPPED_SPEED_SQR = 0.0001D;
    private final Mode mode;

    // DEV55S2: independent arrow direction; never use RailShape as the player-facing indicator.
    // player_oriented=false is the backward-compatible state for existing saved rails.
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty ARROW_FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty PLAYER_ORIENTED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("player_oriented");

    public FluidTransferTrackBlock(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        this.registerDefaultState(this.defaultBlockState()
                .setValue(ARROW_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(PLAYER_ORIENTED, false));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<
                    net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ARROW_FACING, PLAYER_ORIENTED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState rail = super.getStateForPlacement(context);
        // getHorizontalDirection() is the direction the player looks TOWARD.
        // Arrow therefore points AWAY from the player's standing position.
        return rail == null ? null : rail.setValue(ARROW_FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PLAYER_ORIENTED, true);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 2, 16);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState previous, boolean movedByPiston) {
        super.onPlace(state, level, pos, previous, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 1);
        AABB box = new AABB(pos.getX()-0.20D, pos.getY()-0.50D, pos.getZ()-0.20D,
                pos.getX()+1.20D, pos.getY()+1.75D, pos.getZ()+1.20D);
        List<TctTanker> tankers = level.getEntitiesOfClass(
                TctTanker.class, box, Entity::isAlive);
        for (TctTanker tanker : tankers) process(level, pos, tanker);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof TctTanker tanker)) return;
        level.scheduleTick(pos, this, 1);
        process(level, pos, tanker);
    }

    private void process(Level level, BlockPos pos, TctTanker tanker) {
        CompoundTag data = tanker.getPersistentData();
        String prefix = mode == Mode.LOAD ? "TCTFluidLoad" : "TCTFluidUnload";
        long here = pos.asLong();
        long gameTick = level.getGameTime();
        if (data.getLong(prefix+"LastTick") == gameTick) return;
        data.putLong(prefix+"LastTick", gameTick);

        if (tanker.getDeltaMovement().horizontalDistanceSqr() > STOPPED_SPEED_SQR) {
            data.putLong(prefix+"Pos", Long.MIN_VALUE);
            data.putInt(prefix+"Settle", 0);
            data.putInt(prefix+"Cycle", 0);
            return;
        }
        if (data.getLong(prefix+"Pos") != here) {
            data.putLong(prefix+"Pos", here);
            data.putInt(prefix+"Settle", 0);
            data.putInt(prefix+"Cycle", 0);
        }
        int settle = Math.min(SETTLE_TICKS, data.getInt(prefix+"Settle")+1);
        data.putInt(prefix+"Settle", settle);
        if (settle < SETTLE_TICKS) return;
        int cycle = data.getInt(prefix+"Cycle")+1;
        if (cycle < CYCLE_TICKS) {
            data.putInt(prefix+"Cycle", cycle);
            return;
        }
        data.putInt(prefix+"Cycle", 0);

        // Backward compatible: original direct-below dock takes priority.
        BlockEntity tile = level.getBlockEntity(pos.below());
        if (!(tile instanceof FluidInterfaceBlockEntity)) {
            BlockPos covering = pos.below();
            // The extra level must be a solid full-cube ground block (grass, dirt,
            // stone, etc.), not air, so unrelated underground ports are not used.
            if (!level.getBlockState(covering).isSolidRender(level, covering)) return;
            tile = level.getBlockEntity(pos.below(2));
        }
        if (!(tile instanceof FluidInterfaceBlockEntity dock)) return;
        IFluidHandler car = tanker.getFluidHandler();
        IFluidHandler buffer = dock.getFluidHandler();
        if (mode == Mode.LOAD) {
            FluidStack offered = buffer.drain(MB_PER_CYCLE, IFluidHandler.FluidAction.SIMULATE);
            if (!offered.isEmpty()) {
                net.minecraft.network.chat.Component rejection = tanker.fluidRejectionMessage(offered);
                if (rejection != null) {
                    // Rate-limited nearby feedback: no persistent chat spam and
                    // no messaging players outside a short station radius.
                    String key = "TCTFluidRejectionLastNotice";
                    long now = level.getGameTime();
                    CompoundTag notices = tanker.getPersistentData();
                    if (!notices.contains(key) || now - notices.getLong(key) >= 100) {
                        Player listener = level.getNearestPlayer(tanker, 8.0D);
                        if (listener != null) {
                            listener.displayClientMessage(rejection, true);
                            notices.putLong(key, now);
                        }
                    }
                    return;
                }
            }
        }
        int moved = mode == Mode.LOAD
                ? transfer(buffer, car, MB_PER_CYCLE)
                : transfer(car, buffer, MB_PER_CYCLE);
        if (moved > 0) {
            dock.setChanged();
            // DEV55S1: play only for mB actually moved (never for a rejected/idle dock).
            traincraft.audio.Dev55OperationalSounds.pump(level, tanker, mode == Mode.LOAD);
        }
    }

    private static int transfer(IFluidHandler source, IFluidHandler target, int limit) {
        FluidStack probe = source.drain(limit, IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty()) return 0;
        int accepted = target.fill(probe.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return 0;
        FluidStack drained = source.drain(Math.min(accepted, limit), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return 0;
        int placed = target.fill(drained.copy(), IFluidHandler.FluidAction.EXECUTE);
        // Single-threaded station tick should match simulation. If another mod rejects
        // fluid after simulation, attempt to put the remainder back into the source.
        if (placed < drained.getAmount()) {
            FluidStack refund = drained.copy();
            refund.setAmount(drained.getAmount() - placed);
            source.fill(refund, IFluidHandler.FluidAction.EXECUTE);
        }
        return placed;
    }
}
