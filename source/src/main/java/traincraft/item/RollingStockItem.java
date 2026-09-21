/*
 * Traincraft 1.20.1 rolling-stock placement item.
 * Distributed under LGPL-v3.0.
 */
package traincraft.item;

import traincraft.Traincraft;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.function.Supplier;

/** Places Traincraft rolling stock onto rails and aligns it to the rail direction. */
public class RollingStockItem<T extends AbstractMinecart> extends Item {
    private final Supplier<EntityType<T>> entityType;
    private final boolean canonicalRailFacing;
    private final String placementDebugName;

    public RollingStockItem(Properties properties, Supplier<EntityType<T>> entityType) {
        this(properties, entityType, false, null);
    }

    /**
     * Placement-mode overload used by recovered legacy rolling stock.
     *
     * <p>Existing/frozen Traincraft stock keeps the original away-from-player
     * behavior through the two-argument constructor. Legacy batches can opt into
     * a deterministic rail direction so every model in a test lineup faces the
     * same way regardless of which side of the track the player was standing on.</p>
     */
    public RollingStockItem(Properties properties, Supplier<EntityType<T>> entityType,
                            boolean canonicalRailFacing, String placementDebugName) {
        super(properties);
        this.entityType = entityType;
        this.canonicalRailFacing = canonicalRailFacing;
        this.placementDebugName = placementDebugName;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos railPos = context.getClickedPos();
        BlockState railState = level.getBlockState(railPos);

        if (!BaseRailBlock.isRail(railState)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            T rollingStock = this.entityType.get().create(level);
            if (rollingStock == null) {
                return InteractionResult.FAIL;
            }

            BaseRailBlock railBlock = (BaseRailBlock) railState.getBlock();
            RailShape railShape = railBlock.getRailDirection(railState, level, railPos, rollingStock);

            double x = railPos.getX() + 0.5D;
            double y = railPos.getY() + (railShape.isAscending() ? 0.5625D : 0.0625D);
            double z = railPos.getZ() + 0.5D;

            // Keep the locomotive parallel to the rail, then choose the 180-degree
            // orientation whose visible FRONT points away from the placing player.
            // The original Traincraft JTMT model faces opposite Minecraft's entity
            // yaw direction, so this deliberately scores the rendered model front,
            // not merely the entity's logical forward vector.
            float yaw = this.canonicalRailFacing
                    ? canonicalRailYaw(railShape)
                    : railYawFacingAwayFromPlayer(railShape, context.getPlayer(), x, z, context.getRotation());
            rollingStock.moveTo(x, y, z, yaw, 0.0F);

            // STEP_9_3E_T5_R1_SMALL_DIAGONAL_PRESPAWN_ALIGNMENT
            // Align a freshly-created vehicle to the restored TC4.5 Small
            // Diagonal's mathematical 45-degree centerline before the entity is
            // added to the world. This prevents the continuous path helper from
            // visibly sliding a stationary locomotive sideways after placement.
            traincraft.block.track.LegacyContinuousTrackPath.PlacementPose
                    diagonalPlacement =
                    traincraft.block.track.LegacyContinuousTrackPath
                            .smallDiagonalPlacementPose(rollingStock, yaw);
            if (diagonalPlacement != null) {
                double provisionalX = x;
                double provisionalY = y;
                double provisionalZ = z;

                x = diagonalPlacement.position().x;
                y = diagonalPlacement.position().y;
                z = diagonalPlacement.position().z;
                yaw = diagonalPlacement.yaw();

                rollingStock.moveTo(x, y, z, yaw, 0.0F);

                Traincraft.LOGGER.info(
                        "[TC-DIAG-SPAWN] provisional=({},{},{}) aligned=({},{},{}) yaw={}",
                        String.format(java.util.Locale.ROOT, "%.3f", provisionalX),
                        String.format(java.util.Locale.ROOT, "%.3f", provisionalY),
                        String.format(java.util.Locale.ROOT, "%.3f", provisionalZ),
                        String.format(java.util.Locale.ROOT, "%.3f", x),
                        String.format(java.util.Locale.ROOT, "%.3f", y),
                        String.format(java.util.Locale.ROOT, "%.3f", z),
                        String.format(java.util.Locale.ROOT, "%.1f", yaw));
            }

            if (this.placementDebugName != null) {
                Player player = context.getPlayer();
                Traincraft.LOGGER.info(
                        "[TC-LEGACY-SPAWN] stock={} mode={} railShape={} chosenYaw={} playerYaw={} playerPos={} spawn=({},{},{})",
                        this.placementDebugName,
                        this.canonicalRailFacing ? "CANONICAL_RAIL" : "AWAY_FROM_PLAYER",
                        railShape,
                        yaw,
                        player == null ? context.getRotation() : player.getYRot(),
                        player == null ? "<none>" : String.format("(%.3f,%.3f,%.3f)", player.getX(), player.getY(), player.getZ()),
                        x, y, z);
            }

            // Do not place a second train inside an occupied/colliding space.
            if (!level.noCollision(rollingStock, rollingStock.getBoundingBox())) {
                return InteractionResult.FAIL;
            }

            level.addFreshEntity(rollingStock);

            ItemStack stack = context.getItemInHand();
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    /** Returns one stable orientation for each rail shape, independent of player position. */
    private static float canonicalRailYaw(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0.0F;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 90.0F;
            case SOUTH_EAST, NORTH_WEST -> 45.0F;
            case NORTH_EAST, SOUTH_WEST -> -45.0F;
        };
    }

    /**
     * Returns one of the two rail-parallel yaws so the rendered locomotive front
     * points away from the player who placed it.
     */
    private static float railYawFacingAwayFromPlayer(RailShape shape, Player player,
                                                      double spawnX, double spawnZ,
                                                      float fallbackPlayerYaw) {
        float axisYaw = switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0.0F;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 90.0F;
            case SOUTH_EAST, NORTH_WEST -> 45.0F;
            case NORTH_EAST, SOUTH_WEST -> -45.0F;
        };

        float first = Mth.wrapDegrees(axisYaw);
        float opposite = Mth.wrapDegrees(axisYaw + 180.0F);

        if (player == null) {
            // No player position is available. Preserve rail alignment and choose
            // the orientation whose rendered front points along the user's look.
            return renderedFrontDotLook(first, fallbackPlayerYaw) >= renderedFrontDotLook(opposite, fallbackPlayerYaw)
                    ? first : opposite;
        }

        // Vector from the player toward the spawn point. Continuing in this
        // direction means moving farther away from the player.
        double awayX = spawnX - player.getX();
        double awayZ = spawnZ - player.getZ();
        double horizontalDistanceSq = awayX * awayX + awayZ * awayZ;

        if (horizontalDistanceSq < 1.0E-6D) {
            // Extremely unlikely, but avoid an undefined positional direction.
            return renderedFrontDotLook(first, fallbackPlayerYaw) >= renderedFrontDotLook(opposite, fallbackPlayerYaw)
                    ? first : opposite;
        }

        double firstScore = renderedFrontDot(first, awayX, awayZ);
        double oppositeScore = renderedFrontDot(opposite, awayX, awayZ);

        // If the player is almost exactly beside the rail, both choices can be
        // nearly tied. In that case use the player's look direction as a stable
        // tie-breaker while still respecting the rendered model's front.
        if (Math.abs(firstScore - oppositeScore) < 1.0E-5D) {
            return renderedFrontDotLook(first, fallbackPlayerYaw) >= renderedFrontDotLook(opposite, fallbackPlayerYaw)
                    ? first : opposite;
        }

        return firstScore >= oppositeScore ? first : opposite;
    }

    /**
     * Dot product between the rendered model's front and a horizontal target
     * vector. The JTMT small-steam model's visible front is opposite Minecraft's
     * normal entity-forward direction for the same yaw.
     */
    private static double renderedFrontDot(float entityYaw, double targetX, double targetZ) {
        double radians = Math.toRadians(entityYaw);
        double frontX = Math.sin(radians);
        double frontZ = -Math.cos(radians);
        return frontX * targetX + frontZ * targetZ;
    }

    /** Scores the rendered model front against the player's horizontal look yaw. */
    private static double renderedFrontDotLook(float entityYaw, float playerYaw) {
        double lookRadians = Math.toRadians(playerYaw);
        double lookX = -Math.sin(lookRadians);
        double lookZ = Math.cos(lookRadians);
        return renderedFrontDot(entityYaw, lookX, lookZ);
    }
}
