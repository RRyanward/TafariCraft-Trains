package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.registry.TCBlocks;

/**
 * Step 9.3a shared ownership + rail-path foundation for original Traincraft
 * dedicated track pieces. Rendering may span many blocks, but every guide cell
 * is still a RailBlock so the frozen rolling-stock code continues to use the
 * vanilla/Forge rail API.
 */
public abstract class AbstractLegacyBatchRailBlock extends RailBlock {
    public static final DirectionProperty ASSEMBLY_FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SELECTION_SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final ResourceLocation NORMAL_TRACK_ITEM =
            new ResourceLocation("traincraft", "track_normal");
    private static final ThreadLocal<Boolean> INTERNAL_REMOVAL =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final LegacyBatchTrackSpec spec;
    private final ResourceLocation dropItemId;

    protected AbstractLegacyBatchRailBlock(BlockBehaviour.Properties properties,
                                           LegacyBatchTrackSpec spec,
                                           String dropItemId) {
        super(properties);
        this.spec = spec;
        this.dropItemId = new ResourceLocation(dropItemId);

        if (spec.partCount() > partProperty().getPossibleValues().size()) {
            throw new IllegalArgumentException(
                    "Track spec " + spec.id() + " has " + spec.partCount()
                            + " parts but block property supports only "
                            + partProperty().getPossibleValues().size());
        }

        registerDefaultState(defaultBlockState()
                .setValue(SHAPE, spec.shapeForPart(Direction.NORTH, 0, false))
                .setValue(partProperty(), 0)
                .setValue(ASSEMBLY_FACING, Direction.NORTH));
    }

    public final LegacyBatchTrackSpec getSpec() {
        return spec;
    }

    public abstract IntegerProperty partProperty();

    protected boolean alternateState(BlockState state) {
        return false;
    }

    public final int getPart(BlockState state) {
        return state.getValue(partProperty());
    }

    public final Direction getAssemblyFacing(BlockState state) {
        return state.getValue(ASSEMBLY_FACING);
    }

    public final BlockPos getAssemblyRoot(BlockPos pos, BlockState state) {
        Direction facing = getAssemblyFacing(state);
        int part = getPart(state);
        return pos.subtract(spec.offsetForPart(BlockPos.ZERO, facing, part));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SELECTION_SHAPE;
    }

    /**
     * Step 9.3b-t4: the one-block Normal straight still uses its original
     * BlockItem, so let a click on any batch assembly place that Normal rail
     * at the nearest open endpoint. This makes N/S/M/L all connect to switches
     * without deleting the hidden guide cells that the switch needs to route.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getMainHandItem();
        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (!NORMAL_TRACK_ITEM.equals(heldId)) {
            return InteractionResult.PASS;
        }

        Direction facing = getAssemblyFacing(state);
        BlockPos root = getAssemblyRoot(pos, state);
        Vec3 click = hit.getLocation();

        BlockPos bestSegment = null;
        Direction bestOutward = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
            BlockPos segment = spec.offsetForPart(root, facing, endpoint.part());
            Direction outward = spec.rotateDirection(facing, endpoint.outward());
            if (hasRailNeighbor(level, segment, outward)) {
                continue;
            }

            double cx = segment.getX() + 0.5D + outward.getStepX() * 0.5D;
            double cz = segment.getZ() + 0.5D + outward.getStepZ() * 0.5D;
            double dx = click.x - cx;
            double dz = click.z - cz;
            double distanceSq = dx * dx + dz * dz;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestSegment = segment;
                bestOutward = outward;
            }
        }

        if (bestSegment == null || bestOutward == null) {
            return InteractionResult.FAIL;
        }

        BlockPos target = bestSegment.relative(bestOutward);
        BlockState existing = level.getBlockState(target);
        BlockPos support = target.below();

        if ((!existing.isAir() && !existing.canBeReplaced())
                || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                || !player.mayUseItemAt(target, Direction.UP, held)) {
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        RailShape shape = bestOutward.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;
        BlockState normal = TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE, shape);
        level.setBlock(target, normal, 3);

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, target, SoundEvents.METAL_PLACE,
                SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private static boolean hasRailNeighbor(Level level, BlockPos pos, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (BaseRailBlock.isRail(level.getBlockState(neighbor))) {
            return true;
        }
        if (BaseRailBlock.isRail(level.getBlockState(neighbor.above()))) {
            return true;
        }
        return BaseRailBlock.isRail(level.getBlockState(neighbor.below()));
    }

    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos,
                                      AbstractMinecart cart) {
        if (state.getBlock() == this
                && state.hasProperty(partProperty())
                && state.hasProperty(ASSEMBLY_FACING)) {
            int part = getPart(state);
            if (part >= 0 && part < spec.partCount()) {
                Direction facing = getAssemblyFacing(state);
                if (spec.dynamicRouting()) {
                    long routeContext = getAssemblyRoot(pos, state).asLong();
                    return spec.dynamicShapeForPart(
                            facing, part, cart, alternateState(state), routeContext);
                }
                return spec.shapeForPart(
                        facing, part, alternateState(state));
            }
        }
        return super.getRailDirection(state, level, pos, cart);
    }


    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // The classic slope meshes are gradual, while vanilla rail physics can
        // only change one full block of Y on an ascending rail. r3 keeps the
        // invisible upper guide cells tied to the same flat support footprint.
        // Actual support validation for slopes is handled in neighborChanged()
        // below because BaseRailBlock's private shouldBeRemoved() bypasses this
        // virtual canSurvive() method for ascending-rail neighbor updates.
        if (spec.isSlope()) {
            return true;
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        if (spec.isSlope()) {
            // Batch slope guide states are already canonical. Vanilla
            // BaseRailBlock.onPlace() invokes RailState auto-negotiation, which
            // can rewrite the deliberately discrete one-block ascent while the
            // multi-cell assembly is still being constructed.
            return;
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block changedBlock, BlockPos changedPos,
                                boolean isMoving) {
        if (!spec.isSlope()) {
            super.neighborChanged(state, level, pos, changedBlock, changedPos, isMoving);
            return;
        }

        if (level.isClientSide || INTERNAL_REMOVAL.get()) {
            return;
        }

        BlockState current = level.getBlockState(pos);
        if (current.getBlock() != this) {
            return;
        }

        Direction facing = getAssemblyFacing(current);
        int part = getPart(current);
        BlockPos root = getAssemblyRoot(pos, current);
        BlockPos support = spec.supportForPart(root, facing, part);

        if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
            // Preserve normal rail-style support behavior, but validate against
            // Traincraft's flat classic-slope footprint instead of vanilla's
            // private ascending-rail side-support rule. Drop exactly one track
            // item and remove the complete owned assembly.
            removeAssembly(level, pos, current, true);
            INTERNAL_REMOVAL.set(Boolean.TRUE);
            try {
                level.removeBlock(pos, isMoving);
            } finally {
                INTERNAL_REMOVAL.set(Boolean.FALSE);
            }
        }
        // Otherwise intentionally ignore vanilla rail neighbor negotiation.
        // The batch item relocks every guide cell to spec state after placement.
    }
    /**
     * True when this guide cell no longer has its owning assembly root.
     *
     * Old development builds could leave invisible guide cells behind after
     * their visible root was removed. Placement code may safely replace only
     * those orphaned cells; live assemblies remain protected.
     */
    public final boolean isOrphanSegment(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() != this
                || !state.hasProperty(partProperty())
                || !state.hasProperty(ASSEMBLY_FACING)) {
            return false;
        }

        Direction facing = getAssemblyFacing(state);
        BlockPos root = getAssemblyRoot(pos, state);
        BlockState rootState = level.getBlockState(root);

        return rootState.getBlock() != this
                || !rootState.hasProperty(partProperty())
                || !rootState.hasProperty(ASSEMBLY_FACING)
                || getPart(rootState) != 0
                || getAssemblyFacing(rootState) != facing;
    }

    public BlockState stateForPart(Direction facing, int part, boolean alternate) {
        return defaultBlockState()
                .setValue(SHAPE, spec.shapeForPart(facing, part, alternate))
                .setValue(partProperty(), part)
                .setValue(ASSEMBLY_FACING, facing);
    }

    private boolean belongsToFacing(BlockState state, Direction facing) {
        return state.getBlock() == this
                && state.hasProperty(ASSEMBLY_FACING)
                && getAssemblyFacing(state) == facing;
    }

    private void removeAssembly(Level level, BlockPos brokenPos, BlockState brokenState,
                                boolean dropItem) {
        if (level.isClientSide || INTERNAL_REMOVAL.get()) {
            return;
        }

        Direction facing = getAssemblyFacing(brokenState);
        BlockPos root = getAssemblyRoot(brokenPos, brokenState);

        INTERNAL_REMOVAL.set(Boolean.TRUE);
        try {
            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos segmentPos = spec.offsetForPart(root, facing, part);
                if (segmentPos.equals(brokenPos)) {
                    continue;
                }

                BlockState segment = level.getBlockState(segmentPos);
                // Remove every cell of this block/facing at the assembly's
                // canonical occupied positions. Do not require the part index
                // to still be perfect: neighbor negotiation or an older dev
                // build may have left a stale guide state, and that was the
                // source of persistent invisible cells after breaking tracks.
                if (belongsToFacing(segment, facing)) {
                    level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }

            if (dropItem) {
                Item item = ForgeRegistries.ITEMS.getValue(dropItemId);
                if (item != null) {
                    Block.popResource(level, brokenPos, new ItemStack(item));
                }
            }
        } finally {
            INTERNAL_REMOVAL.set(Boolean.FALSE);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !INTERNAL_REMOVAL.get()) {
            removeAssembly(level, pos, state, !player.getAbilities().instabuild);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !INTERNAL_REMOVAL.get()
                && state.getBlock() != newState.getBlock()) {
            removeAssembly(level, pos, state, false);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        Item item = ForgeRegistries.ITEMS.getValue(dropItemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
