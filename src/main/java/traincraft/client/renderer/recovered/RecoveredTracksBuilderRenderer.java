package traincraft.client.renderer.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.Traincraft;
import traincraft.client.model.recovered.RecoveredBuilderRotorModel;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

import java.util.HashMap;
import java.util.Map;

/**
 * TC_DEV36_R1K29E_CLASSIC_ROTOR_RENDERER
 *
 * Builder-only renderer. It preserves the accepted r1k28 body transform and
 * adds Traincraft's original seven-piece cutter as an independent render layer.
 */
public final class RecoveredTracksBuilderRenderer
        extends EntityRenderer<LegacyPoweredLocomotive> {

    private static final Logger DEBUG =
            LoggerFactory.getLogger(
                    "TafariCraftBuilderDrill");

    private static final ResourceLocation BODY_TEXTURE =
            new ResourceLocation(
                    Traincraft.MOD_ID,
                    "textures/trains/builder2.png");

    private static final ResourceLocation ROTOR_TEXTURE =
            new ResourceLocation(
                    Traincraft.MOD_ID,
                    "textures/trains/builder_rotor.png");

    private final EntityModel<LegacyPoweredLocomotive> body;
    private final RecoveredBuilderRotorModel rotor;

    private final Map<Integer, RotorState> states =
            new HashMap<>();

    private static final class RotorState {
        float angle;
        long lastNanos;
        boolean lastActive;
        int lastSampleTick = Integer.MIN_VALUE;
    }

    public RecoveredTracksBuilderRenderer(
            EntityRendererProvider.Context context,
            EntityModel<LegacyPoweredLocomotive> body,
            RecoveredBuilderRotorModel rotor) {

        super(context);
        this.body = body;
        this.rotor = rotor;
        this.shadowRadius = 1.3F;
    }

    @Override
    public void render(
            LegacyPoweredLocomotive entity,
            float yaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {

        RotorState state =
                states.computeIfAbsent(
                        entity.getId(),
                        ignored -> new RotorState());

        long now =
                System.nanoTime();

        if (state.lastNanos == 0L) {
            state.lastNanos = now;
        }

        float elapsed =
                Math.min(
                        0.10F,
                        (now - state.lastNanos)
                                / 1_000_000_000.0F);

        state.lastNanos = now;

        boolean active =
                entity.isBuilderDrillActive();

        if (active) {
            // Roughly 1.25 turns/second, close to the classic visual speed.
            state.angle -=
                    elapsed * 7.8539816F;

            if (state.angle < -6.2831855F) {
                state.angle += 6.2831855F;
            }
        }

        if (state.lastActive != active) {
            DEBUG.info(
                    "[TC-DEV36-R1K29E][ROTOR] event=STATE entity={} tick={} active={} angle={}",
                    entity.getId(),
                    entity.tickCount,
                    active,
                    state.angle);

            state.lastActive = active;
        }

        if (active
                && entity.tickCount
                        - state.lastSampleTick
                        >= 20) {

            state.lastSampleTick =
                    entity.tickCount;

            DEBUG.info(
                    "[TC-DEV36-R1K29E][ROTOR] event=SPIN_SAMPLE entity={} tick={} active=true angle={}",
                    entity.getId(),
                    entity.tickCount,
                    state.angle);
        }

        rotor.setRotorAngle(
                state.angle);

        poseStack.pushPose();

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        270.0F
                                - entity.getRenderTrainFacingYaw(
                                        partialTicks)));

        // Exact accepted Tracks Builder transform from r1k28 registration.
        poseStack.translate(
                0.0D,
                -0.42D
                        + entity.getPoweredModelLift(),
                0.0D);

        VertexConsumer bodyVertices =
                buffers.getBuffer(
                        RenderType.entityTranslucentCull(
                                BODY_TEXTURE));

        body.renderToBuffer(
                poseStack,
                bodyVertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0F,
                1.0F,
                1.0F,
                1.0F);

        VertexConsumer rotorVertices =
                buffers.getBuffer(
                        RenderType.entityTranslucentCull(
                                ROTOR_TEXTURE));

        rotor.renderToBuffer(
                poseStack,
                rotorVertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0F,
                1.0F,
                1.0F,
                1.0F);

        poseStack.popPose();

        super.render(
                entity,
                yaw,
                partialTicks,
                poseStack,
                buffers,
                packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(
            LegacyPoweredLocomotive entity) {
        return BODY_TEXTURE;
    }
}
