package traincraft.client.renderer.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import traincraft.Traincraft;
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;

/**
 * DEV38 y18s - Forney rear wheel circular-face overlay.
 *
 * The legacy rear truck is authored as rigid 1x6x14 side panels:
 *   +X outer carrier part_055: x=6, y=0, z=15
 *   -X outer carrier part_072: x=-7, y=0, z=15
 *
 * Each panel contains two 6px wheel paintings in a 14px longitudinal span.
 * Their exact model-space centers are therefore:
 *   y = 3, z = 18 and z = 26
 *
 * The carrier panels remain static. Only transparent circular wheel-face quads
 * are rendered 0.03px outside the carrier and rotated about axle X from the
 * accepted native wheel phase.
 */
public final class ForneyRearWheelOverlayRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Traincraft.MOD_ID, "textures/trains/forney_rear_wheel_overlay.png");

    private static final float OUTER_X = 7.03F;
    private static final float CENTER_Y = 3.0F;
    private static final float REAR_Z_A = 18.0F;
    private static final float REAR_Z_B = 26.0F;
    private static final float RADIUS = 3.03F / 16.0F;

    private ForneyRearWheelOverlayRenderer() {
    }

    public static void render(
            ForneySteamLocomotive entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {

        float phase = (float) RollingStockWheelAnimation.sampleNativePhase(entity, partialTicks);

        // Right/outboard face.
        renderWheel(poseStack, buffers, packedLight,
                OUTER_X, CENTER_Y, REAR_Z_A, phase, false, 0);
        renderWheel(poseStack, buffers, packedLight,
                OUTER_X, CENTER_Y, REAR_Z_B, phase, false, 1);

        // Left/outboard face. Mirror texture U so authored wheel artwork reads
        // naturally from the opposite side while retaining the same axle phase.
        renderWheel(poseStack, buffers, packedLight,
                -OUTER_X, CENTER_Y, REAR_Z_A, phase, true, 0);
        renderWheel(poseStack, buffers, packedLight,
                -OUTER_X, CENTER_Y, REAR_Z_B, phase, true, 1);
    }

    private static void renderWheel(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            float xPx,
            float yPx,
            float zPx,
            float phase,
            boolean mirror,
            int wheelIndex) {

        poseStack.pushPose();
        poseStack.translate(xPx / 16.0D, yPx / 16.0D, zPx / 16.0D);
        poseStack.mulPose(Axis.XP.rotation(phase));

        VertexConsumer vertices =
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        // Texture packs wheel A in left half, wheel B in right half.
        float u0 = wheelIndex == 0 ? 0.0F : 0.5F;
        float u1 = wheelIndex == 0 ? 0.5F : 1.0F;
        if (mirror) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }

        float nx = xPx >= 0.0F ? 1.0F : -1.0F;

        vertex(vertices, matrix, normal, 0.0F, -RADIUS, -RADIUS,
                u0, 0.0F, packedLight, nx);
        vertex(vertices, matrix, normal, 0.0F, -RADIUS, RADIUS,
                u1, 0.0F, packedLight, nx);
        vertex(vertices, matrix, normal, 0.0F, RADIUS, RADIUS,
                u1, 1.0F, packedLight, nx);
        vertex(vertices, matrix, normal, 0.0F, RADIUS, -RADIUS,
                u0, 1.0F, packedLight, nx);

        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer vertices,
            Matrix4f matrix,
            Matrix3f normal,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight,
            float normalX) {

        vertices.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, normalX, 0.0F, 0.0F)
                .endVertex();
    }
}