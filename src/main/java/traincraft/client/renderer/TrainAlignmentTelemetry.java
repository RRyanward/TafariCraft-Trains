/*
 * Temporary Step 8.3.1-r1 alignment telemetry.
 *
 * This is client-side diagnostics only. It does not modify train movement,
 * rail physics, model transforms or coupler behavior.
 *
 * r1 changes:
 * - use SLF4J so [TC-ALIGN] appears in run/logs/latest.log;
 * - use elapsed-tick throttling instead of requiring an exact tick % 100
 *   render frame;
 * - report flat-rail centerline error directly from the actual RailShape.
 */
package traincraft.client.renderer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

final class TrainAlignmentTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger("TraincraftAlignment");
    private static final Map<Integer, Integer> LAST_LOG_TICK = new ConcurrentHashMap<>();

    private TrainAlignmentTelemetry() {
    }

    static void log(
            Entity entity,
            String stockName,
            double visualBottomRelativeY,
            double visualCrossCenterRelative) {

        int tick = entity.tickCount;
        if (tick <= 0) {
            return;
        }

        Integer previous = LAST_LOG_TICK.get(entity.getId());
        if (previous != null && tick - previous < 100) {
            return;
        }
        LAST_LOG_TICK.put(entity.getId(), tick);

        BlockPos entityPos = entity.blockPosition();
        BlockPos railPos = null;
        BlockState railState = null;

        BlockState at = entity.level().getBlockState(entityPos);
        if (at.is(BlockTags.RAILS)) {
            railPos = entityPos;
            railState = at;
        } else {
            BlockPos below = entityPos.below();
            BlockState belowState = entity.level().getBlockState(below);
            if (belowState.is(BlockTags.RAILS)) {
                railPos = below;
                railState = belowState;
            }
        }

        double entityY = entity.getY();
        double visualBottomWorldY = entityY + visualBottomRelativeY;

        if (railPos == null || railState == null) {
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[TC-ALIGN] %s id=%d tick=%d pos=(%.4f,%.4f,%.4f) "
                            + "rail=NOT_FOUND visualBottomY=%.4f visualCrossOffset=%+.4f",
                    stockName,
                    entity.getId(),
                    tick,
                    entity.getX(), entityY, entity.getZ(),
                    visualBottomWorldY,
                    visualCrossCenterRelative));
            return;
        }

        RailShape shape = null;
        if (railState.getBlock() instanceof BaseRailBlock railBlock) {
            shape = railState.getValue(railBlock.getShapeProperty());
        }

        /*
         * Straight vanilla rail plate is 1/16 block high.
         * Step 8.3.1 calibration is intentionally performed on level,
         * non-ascending rail. Slopes/curves are still logged, but Y/center
         * values should not be used as the final straight-track calibration.
         */
        double railSurfaceY = railPos.getY() + 0.0625D;
        double yError = visualBottomWorldY - railSurfaceY;

        double railCenterX = railPos.getX() + 0.5D;
        double railCenterZ = railPos.getZ() + 0.5D;

        String centerAxis = "N/A";
        double visualCrossWorld = Double.NaN;
        double crossError = Double.NaN;

        if (shape == RailShape.NORTH_SOUTH) {
            centerAxis = "X";
            visualCrossWorld = entity.getX() + visualCrossCenterRelative;
            crossError = visualCrossWorld - railCenterX;
        } else if (shape == RailShape.EAST_WEST) {
            centerAxis = "Z";
            visualCrossWorld = entity.getZ() + visualCrossCenterRelative;
            crossError = visualCrossWorld - railCenterZ;
        }

        String line;
        if (Double.isFinite(crossError)) {
            line = String.format(
                    Locale.ROOT,
                    "[TC-ALIGN] %s id=%d tick=%d pos=(%.4f,%.4f,%.4f) "
                            + "rail=(%d,%d,%d) shape=%s "
                            + "visualBottomY=%.4f railSurfaceY=%.4f yError=%+.4f "
                            + "centerAxis=%s visualCross=%.4f railCenter=%.4f crossError=%+.4f",
                    stockName,
                    entity.getId(),
                    tick,
                    entity.getX(), entityY, entity.getZ(),
                    railPos.getX(), railPos.getY(), railPos.getZ(),
                    shape,
                    visualBottomWorldY, railSurfaceY, yError,
                    centerAxis,
                    visualCrossWorld,
                    "X".equals(centerAxis) ? railCenterX : railCenterZ,
                    crossError);
        } else {
            line = String.format(
                    Locale.ROOT,
                    "[TC-ALIGN] %s id=%d tick=%d pos=(%.4f,%.4f,%.4f) "
                            + "rail=(%d,%d,%d) shape=%s "
                            + "visualBottomY=%.4f railSurfaceY=%.4f yError=%+.4f "
                            + "crossError=N/A_NON_STRAIGHT",
                    stockName,
                    entity.getId(),
                    tick,
                    entity.getX(), entityY, entity.getZ(),
                    railPos.getX(), railPos.getY(), railPos.getZ(),
                    shape,
                    visualBottomWorldY, railSurfaceY, yError);
        }

        LOGGER.info(line);
    }
}
