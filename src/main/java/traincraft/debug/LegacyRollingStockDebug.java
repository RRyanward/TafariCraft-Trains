/* Step 8.4.0 roster-port diagnostics. Intentionally ON during all rolling-stock batches. */
package traincraft.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import traincraft.client.model.legacy.LegacyStaticMesh;
import traincraft.entity.train.CoupleableRollingStock;

import java.util.Map;
import java.util.WeakHashMap;

public final class LegacyRollingStockDebug {
    public static final boolean ENABLED = true;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Entity, Integer> LAST_RENDER_TICK = new WeakHashMap<>();

    private LegacyRollingStockDebug() {}

    public static void logEntity(Entity entity, String stock) {
        if (!ENABLED || entity.level().isClientSide || entity.tickCount % 100 != 0) return;
        double h = Math.sqrt(entity.getDeltaMovement().x * entity.getDeltaMovement().x
                + entity.getDeltaMovement().z * entity.getDeltaMovement().z);
        boolean coupled = entity instanceof CoupleableRollingStock rolling && rolling.hasAnyCoupling();
        LOGGER.info("[TC-LEGACY-ENTITY] stock={} id={} tick={} pos=({},{},{}) motion=({},{},{}) speedKmh={} coupled={} railHere={} railBelow={}",
                stock, entity.getId(), entity.tickCount,
                fmt(entity.getX()), fmt(entity.getY()), fmt(entity.getZ()),
                fmt(entity.getDeltaMovement().x), fmt(entity.getDeltaMovement().y), fmt(entity.getDeltaMovement().z),
                fmt(h * 72.0D), coupled, block(entity, 0), block(entity, -1));
    }

    public static void logRender(Entity entity, String stock, float trainYaw,
                                 LegacyStaticMesh mesh,
                                 double tx, double ty, double tz,
                                 float rx, float ry, float rz,
                                 float sx, float sy, float sz,
                                 float transformedMinY, float transformedMaxY) {
        if (!ENABLED) return;
        synchronized (LAST_RENDER_TICK) {
            Integer previous = LAST_RENDER_TICK.get(entity);
            if (previous != null && previous == entity.tickCount) return;
            if (entity.tickCount % 100 != 0) return;
            LAST_RENDER_TICK.put(entity, entity.tickCount);
        }
        LOGGER.info("[TC-LEGACY-RENDER] stock={} id={} tick={} trainYaw={} translate=({},{},{}) rotateDeg=({},{},{}) scale=({},{},{}) localBounds=({},{},{})..({},{},{}) renderY=({},{}) vertices={} zeroThicknessStabilized={}",
                stock, entity.getId(), entity.tickCount, fmt(trainYaw),
                fmt(tx), fmt(ty), fmt(tz), fmt(rx), fmt(ry), fmt(rz), fmt(sx), fmt(sy), fmt(sz),
                fmt(mesh.minX()), fmt(mesh.minY()), fmt(mesh.minZ()),
                fmt(mesh.maxX()), fmt(mesh.maxY()), fmt(mesh.maxZ()),
                fmt(transformedMinY), fmt(transformedMaxY), mesh.vertexCount(), mesh.stabilizedZeroDimensions());
    }

    private static String block(Entity e, int dy) {
        BlockPos p = e.blockPosition().offset(0, dy, 0);
        return BuiltInRegistries.BLOCK.getKey(e.level().getBlockState(p).getBlock()).toString();
    }

    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }
}
