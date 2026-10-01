package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import traincraft.Traincraft;

import java.util.HashSet;
import java.util.Set;

/**
 * Batch 8 r3zf.
 *
 * The one-block Normal Track originally used a raw vanilla RailBlock. Vanilla
 * rail placement has no knowledge of Traincraft multi-block curve endpoints,
 * so a Normal Track placed immediately after one of those curves can choose the
 * wrong axis and visually "flip" the route.
 *
 * This block keeps vanilla RailBlock behavior everywhere except at a declared
 * open end of a Traincraft two-ended curve/curved-slope assembly. At that one
 * boundary cell, the existing assembly owns the continuation axis.
 */
public final class EndpointAwareNormalTrackBlock extends RailBlock implements net.minecraft.world.level.block.EntityBlock {

    public EndpointAwareNormalTrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = super.getStateForPlacement(context);
        if (base == null) {
            return null;
        }

        RailShape forced = continuationShape(
                context.getLevel(), context.getClickedPos());

        if (forced == null) {
            return base;
        }

        Traincraft.LOGGER.info(
                "[TC-R3ZF-NORMAL-ENDPOINT] place pos={} forcedShape={} playerFacing={}",
                context.getClickedPos(),
                forced.getSerializedName(),
                context.getHorizontalDirection());

        return base.setValue(SHAPE, forced);
    }

    @Override
    protected BlockState updateState(
            BlockState state,
            Level level,
            BlockPos pos,
            boolean forceUpdate) {
        RailShape forced = continuationShape(level, pos);
        if (forced != null) {
            // Keep the boundary rail tangent to the Traincraft assembly even
            // when vanilla rail-neighbor recalculation runs after placement.
            return state.setValue(SHAPE, forced);
        }
        return super.updateState(state, level, pos, forceUpdate);
    }

    private static RailShape continuationShape(Level level, BlockPos target) {
        Direction route = findBatchCurveRoute(level, target);
        if (route == null) {
            route = findLegacyMediumCurveRoute(level, target);
        }
        if (route == null) {
            return null;
        }

        return route.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;
    }

    /**
     * Finds a Traincraft batch curve whose declared outgoing endpoint requires
     * exactly this Normal Track cell. Scanning assembly roots rather than only
     * adjacent physical blocks also supports legacy logical/virtual endpoints.
     */
    private static Direction findBatchCurveRoute(Level level, BlockPos target) {
        Direction resolved = null;
        Set<String> seen = new HashSet<>();

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos probe = target.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);

                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    LegacyBatchTrackSpec spec = batch.getSpec();
                    if (!isCurveFamily(spec.id())
                            || spec.endpoints().size() != 2) {
                        continue;
                    }

                    BlockPos root = batch.getAssemblyRoot(probe, state);
                    Direction facing = batch.getAssemblyFacing(state);
                    String key = spec.id()
                            + "|" + root.asLong()
                            + "|" + facing.ordinal();

                    if (!seen.add(key)) {
                        continue;
                    }

                    for (LegacyBatchTrackSpec.Endpoint endpoint
                            : spec.endpoints()) {
                        BlockPos logical = spec.endpointPosition(
                                root, facing, endpoint);
                        Direction outward = spec.rotateDirection(
                                facing, endpoint.outward());
                        BlockPos required = logical.relative(outward);

                        if (!required.equals(target)) {
                            continue;
                        }

                        if (resolved != null && resolved != outward) {
                            // Multiple different routes want this cell. Do not
                            // guess; let vanilla placement handle ambiguity.
                            return null;
                        }
                        resolved = outward;
                    }
                }
            }
        }

        return resolved;
    }

    private static Direction findLegacyMediumCurveRoute(
            Level level,
            BlockPos target) {
        Direction resolved = null;

        for (Direction sourceToTarget : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos source = target
                        .relative(sourceToTarget.getOpposite())
                        .offset(0, dy, 0);
                BlockState state = level.getBlockState(source);

                if (!(state.getBlock()
                        instanceof LegacyMediumCurveRailBlock)) {
                    continue;
                }

                int part = state.getValue(LegacyMediumCurveRailBlock.PART);
                Direction facing = state.getValue(
                        LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                int last = LegacyMediumCurveRailBlock.PART_COUNT - 1;

                Direction outward = null;
                if (part == 0) {
                    outward = facing.getCounterClockWise();
                } else if (part == last) {
                    outward = facing.getOpposite();
                }

                if (outward != sourceToTarget) {
                    continue;
                }

                if (resolved != null && resolved != outward) {
                    return null;
                }
                resolved = outward;
            }
        }

        return resolved;
    }

    private static boolean isCurveFamily(String id) {
        return id != null
                && (id.startsWith("track_curve_")
                    || id.startsWith("track_parallel_curve_")
                    || id.startsWith("track_slope_curve_"));
    }

    // TC_BATCH8_R4A3F_LIGHTWEIGHT_NORMAL_ENERGY
    // One physical TRACK_NORMAL block supports either NORMAL or ENERGY material.
    // No duplicate Energy rail block/state/geometry is introduced.
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        return new TrackMaterialBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        net.minecraft.resources.ResourceLocation itemId =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        stack.getItem());

        TrackMaterial material =
                itemId != null
                        && traincraft.Traincraft.MOD_ID.equals(itemId.getNamespace())
                        && "track_energy".equals(itemId.getPath())
                ? TrackMaterial.ENERGY
                : TrackMaterial.NORMAL;

        net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                level.getBlockEntity(pos);

        if (blockEntity instanceof TrackMaterialBlockEntity materialData) {
            materialData.setMaterial(material);
        }
    }
}