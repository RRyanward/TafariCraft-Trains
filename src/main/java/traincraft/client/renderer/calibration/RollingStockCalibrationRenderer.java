package traincraft.client.renderer.calibration;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.client.renderer.animation.RollingStockWheelAnimation;

/**
 * Generic outer Y-offset + train-sized frustum bounds for TafariCraft rolling
 * stock.
 *
 * Vanilla entity culling uses the entity's small minecart-sized AABB. Our
 * locomotive/tender/car models are often several blocks longer/taller than
 * that, which made their meshes vanish at screen edges or while the camera was
 * physically inside the model.
 *
 * r1f12g keeps the actual collision/entity bounds unchanged and expands ONLY
 * the renderer's visibility test.
 */
public final class RollingStockCalibrationRenderer<T extends Entity>
        extends EntityRenderer<T> {

    private static final double ALWAYS_RENDER_NEAR_SQR = 20.0D * 20.0D;
    private static final double CULL_EXPAND_XZ = 12.0D;
    private static final double CULL_EXPAND_Y = 6.0D;

    // TC_BATCH8_R4A5G25H9_BUILDER_RENDER_SMOOTH
    //
    // Visual-only correction filter for the Tracks Builder. The accepted F9
    // path remains authoritative. When network/path ownership causes a large
    // one-tick position correction, hold the rendered model near its previous
    // visible world position and let it catch the authoritative entity at a
    // bounded visual speed. Physics, rail ownership, placement, collision and
    // server/client entity coordinates are never modified here.
    private static final java.util.Map<Integer, BuilderRenderSmoothingState>
            TC_BUILDER_RENDER_STATES =
                    new java.util.HashMap<>();
    private static final double TC_BUILDER_RENDER_MAX_CATCHUP_BPS = 4.50D;
    private static final double TC_BUILDER_RENDER_RESET_DISTANCE = 8.00D;
    private static final double TC_BUILDER_RENDER_CORRECTION_LOG = 0.75D;
    private final EntityRenderer<T> delegate;

    public static <T extends Entity> EntityRendererProvider<T> wrap(
            EntityRendererProvider<T> original) {
        return context -> new RollingStockCalibrationRenderer<>(
                context, original.create(context));
    }

    private RollingStockCalibrationRenderer(
            EntityRendererProvider.Context context,
            EntityRenderer<T> delegate) {
        super(context);
        this.delegate = delegate;
    }

    @Override
    public boolean shouldRender(
            T entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {

        double dx = entity.getX() - cameraX;
        double dy = entity.getY() - cameraY;
        double dz = entity.getZ() - cameraZ;

        // If the camera is near/inside a large train model, never let the tiny
        // entity AABB hide the mesh.
        if (dx * dx + dy * dy + dz * dz <= ALWAYS_RENDER_NEAR_SQR) {
            return true;
        }

        // Farther away, retain frustum culling but use rolling-stock-sized
        // visual bounds instead of the minecart-sized physics bounds.
        AABB visualBounds = entity.getBoundingBoxForCulling()
                .inflate(CULL_EXPAND_XZ, CULL_EXPAND_Y, CULL_EXPAND_XZ);

        return frustum.isVisible(visualBounds);
    }

    @Override
    public void render(
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {

        double y = 0.0D;

        // Prevent double movement on the accepted powered-locomotive path.
        if (!(entity instanceof LegacyPoweredLocomotive)) {
            ResourceLocation key =
                    ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

            if (key != null
                    && Traincraft.MOD_ID.equals(key.getNamespace())
                    && !"tracks_builder".equals(key.getPath())) {
                y = RollingStockCalibrationConfig.modelY(
                        key.getPath(), 0.0D);
            }
        }

        poseStack.pushPose();
        // TC_BATCH8_R4A5G25H9_BUILDER_RENDER_SMOOTH
        // World-space pose translation only. The entity itself is untouched.
        double[] tcBuilderOffset =
                tcBuilderRenderOffset(
                        entity,
                        partialTick);
        if (tcBuilderOffset != null) {
            poseStack.translate(
                    tcBuilderOffset[0],
                    0.0D,
                    tcBuilderOffset[1]);
        }

        if (Math.abs(y) > 0.000001D) {
            poseStack.translate(0.0D, y, 0.0D);
        }

        // TC_DEV38_R1G1Z3A_BULK_NATIVE_WHEEL_HOOK
        // Real authored ModelParts only; no synthetic overlay geometry.
        traincraft.client.renderer.animation.FleetNativeWheelPartAnimator.apply(
                delegate,
                entity,
                partialTick);
        // TC_DEV38_R1G1Z7Y14_DIRECT_DELEGATE_ROD_HOOK
        // Gated inside the helper to six exact DEV42-supported targets.
        traincraft.client.renderer.animation.FleetRodPistonPartAnimator.applyDelegate(
                delegate,
                entity,
                partialTick);
        delegate.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);

        // DEV38 r1g1c client-only all-fleet signed-distance wheel animation.
        RollingStockWheelAnimation.render(entity, partialTick, poseStack, buffer);

        poseStack.popPose();
    }

    private static double[] tcBuilderRenderOffset(
            Entity entity,
            float partialTick) {

        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES
                        .getKey(entity.getType());

        if (key == null
                || !Traincraft.MOD_ID.equals(key.getNamespace())
                || !"tracks_builder".equals(key.getPath())) {
            return null;
        }

        double alpha =
                Math.max(
                        0.0D,
                        Math.min(1.0D, partialTick));

        double targetX =
                entity.xOld
                        + (entity.getX() - entity.xOld)
                                * alpha;
        double targetZ =
                entity.zOld
                        + (entity.getZ() - entity.zOld)
                                * alpha;

        long now = System.nanoTime();
        BuilderRenderSmoothingState state =
                TC_BUILDER_RENDER_STATES.computeIfAbsent(
                        entity.getId(),
                        ignored ->
                                new BuilderRenderSmoothingState());

        if (!state.initialized
                || entity.tickCount < state.lastEntityTick) {
            state.initialized = true;
            state.visualX = targetX;
            state.visualZ = targetZ;
            state.lastActualX = entity.getX();
            state.lastActualZ = entity.getZ();
            state.lastEntityTick = entity.tickCount;
            state.lastNanos = now;
            return new double[] {0.0D, 0.0D};
        }

        if (entity.tickCount != state.lastEntityTick) {
            double actualDx =
                    entity.getX() - state.lastActualX;
            double actualDz =
                    entity.getZ() - state.lastActualZ;
            double actualCorrection =
                    Math.hypot(
                            actualDx,
                            actualDz);

            if (actualCorrection
                    >= TC_BUILDER_RENDER_CORRECTION_LOG) {
                org.slf4j.LoggerFactory
                        .getLogger(
                                "TafariCraftBuilderRenderSmooth")
                        .info(
                                "[TC-BUILDER-RENDER-SMOOTH] id={} tick={} correction={} from=({}, {}) to=({}, {}) visual=({}, {})",
                                entity.getId(),
                                entity.tickCount,
                                actualCorrection,
                                state.lastActualX,
                                state.lastActualZ,
                                entity.getX(),
                                entity.getZ(),
                                state.visualX,
                                state.visualZ);
            }

            state.lastActualX = entity.getX();
            state.lastActualZ = entity.getZ();
            state.lastEntityTick = entity.tickCount;
        }

        double distanceToTarget =
                Math.hypot(
                        targetX - state.visualX,
                        targetZ - state.visualZ);

        if (distanceToTarget
                > TC_BUILDER_RENDER_RESET_DISTANCE) {
            state.visualX = targetX;
            state.visualZ = targetZ;
        } else if (distanceToTarget > 1.0E-9D) {
            double elapsedSeconds =
                    state.lastNanos > 0L
                            ? (now - state.lastNanos)
                                    / 1_000_000_000.0D
                            : 0.0D;
            elapsedSeconds =
                    Math.max(
                            0.0D,
                            Math.min(
                                    0.050D,
                                    elapsedSeconds));

            double maxStep =
                    TC_BUILDER_RENDER_MAX_CATCHUP_BPS
                            * elapsedSeconds;

            if (maxStep >= distanceToTarget) {
                state.visualX = targetX;
                state.visualZ = targetZ;
            } else if (maxStep > 0.0D) {
                double scale =
                        maxStep / distanceToTarget;
                state.visualX +=
                        (targetX - state.visualX)
                                * scale;
                state.visualZ +=
                        (targetZ - state.visualZ)
                                * scale;
            }
        }

        state.lastNanos = now;

        return new double[] {
                state.visualX - targetX,
                state.visualZ - targetZ
        };
    }

    private static final class BuilderRenderSmoothingState {
        private boolean initialized;
        private double visualX;
        private double visualZ;
        private double lastActualX;
        private double lastActualZ;
        private int lastEntityTick;
        private long lastNanos;
    }
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return delegate.getTextureLocation(entity);
    }
}