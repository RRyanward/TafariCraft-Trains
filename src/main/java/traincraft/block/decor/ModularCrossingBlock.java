package traincraft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import traincraft.registry.TCSounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TafariCraft modular railroad crossing controller.
 *
 * BASE = controller + audio owner
 * POLE/CROSSBUCK = visual
 * LAMPS = follows ACTIVE/FLASH
 * GATE = follows ARM
 * BELL = decorative visual only
 */
public final class ModularCrossingBlock extends HorizontalDirectionalBlock {
    public enum Type {
        BASE, POLE, LAMPS, CROSSBUCK, GATE, BELL
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty FLASH = BooleanProperty.create("flash");
    public static final IntegerProperty ARM = IntegerProperty.create("arm", 0, 3);

    private static final int STEP_TICKS = 4;
    private static final long DETECTOR_LATCH_TICKS = 240L;
    private static final int DETECTOR_SEARCH_RADIUS = 40;
    private static final int DETECTOR_VERTICAL_RADIUS = 6;
    private static final int COMPONENT_SYNC_RADIUS = 12;

    private static final Map<String, Long> BASE_LATCH_UNTIL = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_BELL_TICK = new ConcurrentHashMap<>();

    private final Type type;

    public ModularCrossingBlock(Type type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
                .setValue(FLASH, false)
                .setValue(ARM, 3));
    }

    public Type getType() {
        return type;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, type == Type.CROSSBUCK ? context.getHorizontalDirection() : context.getHorizontalDirection().getOpposite())
                .setValue(ACTIVE, false)
                .setValue(FLASH, false)
                .setValue(ARM, 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, FLASH, ARM);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (type) {
            case BASE -> box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D);
            case POLE -> box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
            case LAMPS, CROSSBUCK, BELL -> box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
            case GATE -> box(3.0D, 0.0D, 3.0D, 16.0D, 16.0D, 13.0D);
        };
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && oldState.getBlock() != this) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    public static void triggerNearbyBases(ServerLevel level, BlockPos detectorPos) {
        List<BlockPos> bases = new ArrayList<>();

        BlockPos min = detectorPos.offset(
                -DETECTOR_SEARCH_RADIUS,
                -DETECTOR_VERTICAL_RADIUS,
                -DETECTOR_SEARCH_RADIUS);
        BlockPos max = detectorPos.offset(
                DETECTOR_SEARCH_RADIUS,
                DETECTOR_VERTICAL_RADIUS,
                DETECTOR_SEARCH_RADIUS);

        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof ModularCrossingBlock block
                    && block.type == Type.BASE) {
                bases.add(cursor.immutable());
            }
        }

        if (bases.isEmpty()) return;

        bases.sort(Comparator.comparingDouble(p -> p.distSqr(detectorPos)));

        double nearest = Math.sqrt(bases.get(0).distSqr(detectorPos));
        double clusterLimitSq = (nearest + 8.0D) * (nearest + 8.0D);

        int triggered = 0;
        for (BlockPos basePos : bases) {
            if (basePos.distSqr(detectorPos) > clusterLimitSq) break;

            String key = latchKey(level, basePos);
            long now = level.getGameTime();
            long old = BASE_LATCH_UNTIL.getOrDefault(key, 0L);
            BASE_LATCH_UNTIL.put(key, Math.max(old, now + DETECTOR_LATCH_TICKS));

            BlockState baseState = level.getBlockState(basePos);
            if (baseState.getBlock() instanceof ModularCrossingBlock base) {
                level.scheduleTick(basePos, base, 1);
            }

            triggered++;
            if (triggered >= 4) break;
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getBlock() != this) return;

        if (type == Type.BASE) {
            tickBase(state, level, pos);
        } else {
            tickComponent(state, level, pos);
        }

        level.scheduleTick(pos, this, STEP_TICKS);
    }

    private void tickBase(BlockState state, ServerLevel level, BlockPos pos) {
        boolean demand = level.hasNeighborSignal(pos)
                || latchActive(level, pos)
                || hasApproachingTrain(level, pos, state.getValue(FACING));

        int arm = state.getValue(ARM);
        int nextArm = arm;
        boolean active;

        if (demand) {
            active = true;
            if (arm > 0) nextArm = arm - 1;
        } else if (arm < 3) {
            nextArm = arm + 1;
            active = nextArm < 3;
        } else {
            active = false;
        }

        boolean flash = active && (((level.getGameTime() / 7L) & 1L) != 0L);

        BlockState next = state;
        if (next.getValue(ACTIVE) != active) next = next.setValue(ACTIVE, active);
        if (next.getValue(FLASH) != flash) next = next.setValue(FLASH, flash);
        if (next.getValue(ARM) != nextArm) next = next.setValue(ARM, nextArm);

        if (next != state) {
            level.setBlock(pos, next, 3);
        }

        updateBaseBell(level, pos, active);
    }

    /**
     * The controller Base owns the warning audio, so the sound never depends on
     * the decorative Bell piece finding/synchronizing with the Base.
     */
    private void updateBaseBell(ServerLevel level, BlockPos pos, boolean active) {
        String key = latchKey(level, pos) + ":base-bell";

        if (!active) {
            NEXT_BELL_TICK.remove(key);
            return;
        }

        long now = level.getGameTime();
        Long due = NEXT_BELL_TICK.get(key);

        if (due == null || now >= due) {
            level.playSound(
                    null,
                    pos,
                    TCSounds.MODULAR_CROSSING_BELL.get(),
                    SoundSource.BLOCKS,
                    1.75F,
                    1.0F);

            NEXT_BELL_TICK.put(key, now + 8L);
        }
    }

    private void tickComponent(BlockState state, ServerLevel level, BlockPos pos) {
        BaseState base = nearestBase(level, pos);

        boolean active = base != null && base.active();
        boolean flash = base != null && base.flash();
        int arm = base != null ? base.arm() : 3;

        BlockState next = state;
        if (next.getValue(ACTIVE) != active) next = next.setValue(ACTIVE, active);
        if (next.getValue(FLASH) != flash) next = next.setValue(FLASH, flash);
        if (next.getValue(ARM) != arm) next = next.setValue(ARM, arm);

        if (next != state) {
            level.setBlock(pos, next, 3);
        }

        // BELL is visual-only. The BASE owns the actual warning audio.
    }

    private static BaseState nearestBase(ServerLevel level, BlockPos pos) {
        BlockState bestState = null;
        double bestDistance = Double.MAX_VALUE;

        BlockPos min = pos.offset(
                -COMPONENT_SYNC_RADIUS,
                -COMPONENT_SYNC_RADIUS,
                -COMPONENT_SYNC_RADIUS);
        BlockPos max = pos.offset(
                COMPONENT_SYNC_RADIUS,
                COMPONENT_SYNC_RADIUS,
                COMPONENT_SYNC_RADIUS);

        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState candidate = level.getBlockState(cursor);

            if (!(candidate.getBlock() instanceof ModularCrossingBlock block)
                    || block.type != Type.BASE) {
                continue;
            }

            double distance = cursor.distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestState = candidate;
            }
        }

        if (bestState == null) return null;

        return new BaseState(
                bestState.getValue(ACTIVE),
                bestState.getValue(FLASH),
                bestState.getValue(ARM));
    }

    private static boolean latchActive(ServerLevel level, BlockPos pos) {
        String key = latchKey(level, pos);
        Long until = BASE_LATCH_UNTIL.get(key);

        if (until == null) return false;

        if (until < level.getGameTime()) {
            BASE_LATCH_UNTIL.remove(key);
            return false;
        }

        return true;
    }

    private static String latchKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    private static boolean hasApproachingTrain(ServerLevel level, BlockPos pos, Direction facing) {
        Direction.Axis railAxis = facing.getClockWise().getAxis();
        double xReach = railAxis == Direction.Axis.X ? 24.0D : 4.0D;
        double zReach = railAxis == Direction.Axis.Z ? 24.0D : 4.0D;

        AABB approach = new AABB(pos).inflate(xReach, 4.0D, zReach);

        return !level.getEntitiesOfClass(
                AbstractMinecart.class,
                approach,
                cart -> cart.isAlive()
                        && (cart.getDeltaMovement().horizontalDistanceSqr() > 2.5E-5D
                        || cart.distanceToSqr(
                                pos.getX() + 0.5D,
                                pos.getY() + 0.5D,
                                pos.getZ() + 0.5D) < 64.0D))
                .isEmpty();
    }

    private record BaseState(boolean active, boolean flash, int arm) {
    }
}

