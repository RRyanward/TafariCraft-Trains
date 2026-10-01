package traincraft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v1j-r6 animated railroad crossing signal/gate.
 *
 * Warning logic:
 *   - Crossing Approach Detector Track hit => lights + bell ON immediately.
 *   - Arm then lowers in steps.
 *   - While a train is approaching/occupying the crossing, warning stays ON.
 *   - After the train clears, arm raises in steps while lights + bell stay ON.
 *   - The instant ARM reaches fully UP, ACTIVE becomes false and bell/lights stop.
 *
 * The existing automatic roughly-24-block approach scan remains as a fallback.
 * Redstone can also force the crossing active.
 *
 * ARM states: 3=fully up, 2=45 degrees, 1=22.5 degrees, 0=down.
 */
public final class RailroadCrossingSignalBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty ARM = IntegerProperty.create("arm", 0, 3);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty FLASH = BooleanProperty.create("flash");

    private static final VoxelShape SHAPE = box(5.5D, 0.0D, 5.5D, 10.5D, 16.0D, 10.5D);
    private static final int STEP_TICKS = 4;
    private static final long DETECTOR_LATCH_TICKS = 240L; // 12 s; approach scan takes over as the train closes in.
    private static final int DETECTOR_SEARCH_RADIUS = 40;
    private static final int DETECTOR_VERTICAL_RADIUS = 6;
    private static final int MAX_CLUSTER_SIGNALS = 4;
    private static final Map<String, Long> DETECTOR_LATCH_UNTIL = new ConcurrentHashMap<>();

    public RailroadCrossingSignalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ARM, 3)
                .setValue(ACTIVE, false)
                .setValue(FLASH, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ARM, 3)
                .setValue(ACTIVE, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARM, ACTIVE, FLASH);
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
        return SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() != this) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    /** Called by Crossing Approach Detector Track the instant a cart touches it. */
    public static void triggerNearby(ServerLevel level, BlockPos detectorPos) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos min = detectorPos.offset(-DETECTOR_SEARCH_RADIUS, -DETECTOR_VERTICAL_RADIUS, -DETECTOR_SEARCH_RADIUS);
        BlockPos max = detectorPos.offset(DETECTOR_SEARCH_RADIUS, DETECTOR_VERTICAL_RADIUS, DETECTOR_SEARCH_RADIUS);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof RailroadCrossingSignalBlock) found.add(cursor.immutable());
        }
        if (found.isEmpty()) return;

        found.sort(Comparator.comparingDouble(p -> p.distSqr(detectorPos)));
        double nearestDistance = Math.sqrt(found.get(0).distSqr(detectorPos));
        double clusterLimitSq = (nearestDistance + 8.0D) * (nearestDistance + 8.0D);
        int triggered = 0;
        for (BlockPos signalPos : found) {
            if (signalPos.distSqr(detectorPos) > clusterLimitSq) break;
            BlockState state = level.getBlockState(signalPos);
            if (state.getBlock() instanceof RailroadCrossingSignalBlock crossing) {
                crossing.triggerFromDetector(level, signalPos, state);
                triggered++;
                if (triggered >= MAX_CLUSTER_SIGNALS) break;
            }
        }
    }

    private void triggerFromDetector(ServerLevel level, BlockPos pos, BlockState state) {
        String key = latchKey(level, pos);
        long now = level.getGameTime();
        Long oldUntil = DETECTOR_LATCH_UNTIL.get(key);
        boolean alreadyLatched = oldUntil != null && oldUntil >= now;
        long newUntil = Math.max(oldUntil == null ? 0L : oldUntil, now + DETECTOR_LATCH_TICKS);
        DETECTOR_LATCH_UNTIL.put(key, newUntil);

        // Immediate warning: no four-tick polling delay when the detector rail is hit.
        if (!state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, true), 3);
        }
        if (!alreadyLatched) {
            playCrossingBell(level, pos); // first bell sounds on the exact detector hit.
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getBlock() != this) return;

        boolean detectorDemand = detectorLatchActive(level, pos);
        boolean demand = level.hasNeighborSignal(pos)
                || detectorDemand
                || hasApproachingTrain(level, pos, state.getValue(FACING));

        int arm = state.getValue(ARM);
        int nextArm = arm;
        boolean warning;

        if (demand) {
            warning = true;
            if (arm > 0) nextArm = arm - 1;
        } else if (arm < 3) {
            nextArm = arm + 1;
            // Keep lights and bell operating throughout the raise cycle.
            // Shut them off on the same tick the arm reaches fully UP.
            warning = nextArm < 3;
        } else {
            warning = false;
        }

        BlockState next = state;
        if (next.getValue(ACTIVE) != warning) next = next.setValue(ACTIVE, warning);
        if (nextArm != arm) next = next.setValue(ARM, nextArm);

        boolean flash = warning && (((level.getGameTime() / 7L) & 1L) != 0L);
        if (next.getValue(FLASH) != flash) next = next.setValue(FLASH, flash);
        if (next != state) level.setBlock(pos, next, 3);

        // Roughly two crossing-bell strikes per second while ACTIVE.  ACTIVE remains
        // true during lowering, down/occupied, and raising, then becomes false at full UP.
        if (warning && (level.getGameTime() % 10L) < STEP_TICKS) playCrossingBell(level, pos);

        // TC_R2C_CROSSING_WARNING_EFFECTS
        CrossingSignalWarningEffects.emit(level, pos, next);

        level.scheduleTick(pos, this, STEP_TICKS);
    }

    private static void playCrossingBell(ServerLevel level, BlockPos pos) {{
        // r2c4 custom bell is handled only by CrossingSignalWarningEffects.
    }}

    private static boolean detectorLatchActive(ServerLevel level, BlockPos pos) {
        String key = latchKey(level, pos);
        Long until = DETECTOR_LATCH_UNTIL.get(key);
        if (until == null) return false;
        if (until < level.getGameTime()) {
            DETECTOR_LATCH_UNTIL.remove(key);
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
                cart -> cart.isAlive() && (
                        cart.getDeltaMovement().horizontalDistanceSqr() > 2.5E-5D
                                || cart.distanceToSqr(
                                        pos.getX() + 0.5D,
                                        pos.getY() + 0.5D,
                                        pos.getZ() + 0.5D) < 64.0D))
                .isEmpty();
    }
}