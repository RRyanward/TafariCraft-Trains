package traincraft.block.integration;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import traincraft.entity.train.freight.FreightCart;

/**
 * Station transfer rail.
 *
 * DEV38 r1f2f:
 * A thin RailBlock does not reliably receive Entity#entityInside for the
 * Traincraft Freight Cart in every resting geometry. The rail therefore keeps
 * a lightweight one-tick scheduled server scan while loaded. It only examines
 * the exact rail cell and only processes a stopped Freight Cart whose body is
 * physically over that cell.
 *
 * Transfer contract is unchanged:
 * - Cargo Interface exactly one block below, same X/Z.
 * - Freight Cart must be essentially stopped.
 * - 10 tick settle.
 * - 10 tick transfer cycle.
 * - maximum 8 items per cycle.
 */
public final class CargoTransferTrackBlock extends RailBlock {
    public enum Mode { LOAD, UNLOAD }

    public static final int SETTLE_TICKS = 10;
    public static final int CYCLE_TICKS = 10;
    public static final int ITEMS_PER_CYCLE = 8;
    private static final double STOPPED_SPEED_SQR = 0.0001D; // 0.01 blocks/tick

    // Selection/highlight only. Vanilla RailBlock collision/path behavior stays.
    private static final net.minecraft.world.phys.shapes.VoxelShape RAIL_SELECTION_SHAPE =
            net.minecraft.world.level.block.Block.box(
                    0.0D, 0.0D, 0.0D,
                    16.0D, 2.0D, 16.0D);

    private final Mode mode;

    public CargoTransferTrackBlock(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return RAIL_SELECTION_SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Keep one small deterministic scan active while this station rail is loaded.
        level.scheduleTick(pos, this, 1);

        // Tight X/Z scan centered on this exact rail cell. Y is generous enough
        // for Traincraft's visual/entity offsets without reaching neighboring rails.
        AABB stationBox = new AABB(
                pos.getX() - 0.20D, pos.getY() - 0.50D, pos.getZ() - 0.20D,
                pos.getX() + 1.20D, pos.getY() + 1.75D, pos.getZ() + 1.20D);

        List<FreightCart> freight = level.getEntitiesOfClass(
                FreightCart.class,
                stationBox,
                cart -> cart.isAlive());

        for (FreightCart cart : freight) {
            processFreight(level, pos, cart);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);

        if (level.isClientSide || !(entity instanceof FreightCart freight)) return;

        // Keep entityInside as a compatibility wake-up path. processFreight has
        // a per-game-tick guard so this cannot double-count with scheduled tick.
        level.scheduleTick(pos, this, 1);
        processFreight(level, pos, freight);
    }

    private void processFreight(Level level, BlockPos pos, FreightCart freight) {
        CompoundTag data = freight.getPersistentData();
        String prefix = "TCTransfer" + (mode == Mode.LOAD ? "Load" : "Unload");
        long here = pos.asLong();

        // r1f2f de-dup: scheduled tick + entityInside may both see the same cart.
        long gameTick = level.getGameTime();
        if (data.getLong(prefix + "LastTick") == gameTick) return;
        data.putLong(prefix + "LastTick", gameTick);

        if (freight.getDeltaMovement().horizontalDistanceSqr() > STOPPED_SPEED_SQR) {
            data.putLong(prefix + "Pos", Long.MIN_VALUE);
            data.putInt(prefix + "Settle", 0);
            data.putInt(prefix + "Cycle", 0);
            return;
        }

        if (data.getLong(prefix + "Pos") != here) {
            data.putLong(prefix + "Pos", here);
            data.putInt(prefix + "Settle", 0);
            data.putInt(prefix + "Cycle", 0);
        }

        int settle = data.getInt(prefix + "Settle") + 1;
        data.putInt(prefix + "Settle", Math.min(SETTLE_TICKS, settle));
        if (settle < SETTLE_TICKS) return;

        int cycle = data.getInt(prefix + "Cycle") + 1;
        if (cycle < CYCLE_TICKS) {
            data.putInt(prefix + "Cycle", cycle);
            return;
        }
        data.putInt(prefix + "Cycle", 0);

        CargoInterfaceBlockEntity station = findInterface(level, pos);
        if (station == null) return;

        IItemHandler car = freight.getCargoInventory();
        IItemHandler buffer = station.getItems();

        int moved = mode == Mode.LOAD
                ? moveItems(buffer, car, ITEMS_PER_CYCLE)
                : moveItems(car, buffer, ITEMS_PER_CYCLE);

        if (moved > 0) {
            station.setChanged();
        }
    }

    private static CargoInterfaceBlockEntity findInterface(Level level, BlockPos railPos) {
        // TC_DEV38_R1F2F_STRICT_ONE_BLOCK_CARGO_INTERFACE
        BlockEntity direct = level.getBlockEntity(railPos.below());
        return direct instanceof CargoInterfaceBlockEntity cargo ? cargo : null;
    }

    private static int moveItems(IItemHandler source, IItemHandler destination, int limit) {
        int remaining = limit;
        int movedTotal = 0;

        for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
            ItemStack probe = source.extractItem(slot, remaining, true);
            if (probe.isEmpty()) continue;

            ItemStack simulatedRemainder =
                    ItemHandlerHelper.insertItemStacked(destination, probe.copy(), true);
            int movable = probe.getCount() - simulatedRemainder.getCount();
            if (movable <= 0) continue;

            ItemStack taken = source.extractItem(slot, movable, false);
            ItemStack leftover =
                    ItemHandlerHelper.insertItemStacked(destination, taken, false);

            int inserted = taken.getCount() - leftover.getCount();
            if (!leftover.isEmpty()) {
                ItemHandlerHelper.insertItemStacked(source, leftover, false);
            }

            movedTotal += inserted;
            remaining -= inserted;
        }

        return movedTotal;
    }
}
