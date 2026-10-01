package traincraft.client.renderer.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import traincraft.Traincraft;
import traincraft.entity.train.freight.FreightCart;

/**
 * TC_CONCEPT_TRANSPORT_CAGE_R1F19A
 *
 * Purpose-built Minecraft-style transport cage matching the approved concept:
 * cyan + brown Mob/Prisoner Car and yellow + brown Livestock Car.
 *
 * This is geometry, not a recolor of the legacy stockcar mesh. Openings are
 * physically open so actual passenger heads/bodies remain visible.
 */
public final class ConceptTransportCarRenderer<T extends FreightCart> extends EntityRenderer<T> {
    private static final ResourceLocation WHITE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/misc/transport_white.png");

    private static final int DARK = 0x171717;
    private static final int DARK_METAL = 0x34383E;
    private static final int METAL = 0xAAB2B8;
    private static final int BROWN = 0x6B3B18;
    private static final int BROWN_DARK = 0x45240F;
    private static final int CYAN = 0x12B8C5;
    private static final int CYAN_DARK = 0x078895;
    private static final int YELLOW = 0xF0B817;
    private static final int YELLOW_DARK = 0xB97B09;

    private final boolean prisoner;

    public ConceptTransportCarRenderer(EntityRendererProvider.Context context, boolean prisoner) {
        super(context);
        this.prisoner = prisoner;
        this.shadowRadius = 1.15F;
    }


    /*
     * TC_BATCH7_R5C_TRANSPORT_STABLE_TANGENT_YAW
     *
     * Render-only orientation owner for the yellow Livestock and cyan
     * Mob/Prisoner concept cars.
     *
     * r5b used entity yaw as the 180-degree tie-break reference. These transport
     * entities can receive a 180-degree body-yaw update while still travelling
     * along the same straight rail, so using current entity yaw allowed the
     * rendered shell to flip too.
     *
     * r5c keeps a weak per-entity visual-yaw history. While moving, it chooses
     * the motion tangent or its 180-degree equivalent whichever is nearest the
     * LAST RENDERED orientation. At low speed it holds that visual orientation.
     * This changes no entity yaw, rail path, speed, coupling or server state.
     */
    private static final java.util.WeakHashMap<FreightCart, Float>
            LAST_TRANSPORT_VISUAL_YAW = new java.util.WeakHashMap<>();
    /*
     * TC_BATCH8_R2_TRANSPORT_INITIAL_RAIL_ALIGNMENT
     *
     * Placement-only visual seed for Livestock + Mob/Prisoner transport cars.
     *
     * r5c remains the owner of moving/stopped visual continuity. This helper is
     * consulted only when a newly placed transport car is nearly stationary and
     * LAST_TRANSPORT_VISUAL_YAW has no value yet. It reads the rail directly
     * under the car and returns the nearest rail-parallel yaw. It changes no
     * entity yRot, motion, coupling, spacing, path math or server state.
     */
    private static float initialRailAlignedYaw(FreightCart entity, float referenceYaw) {
        net.minecraft.core.BlockPos railPos = entity.blockPosition();
        net.minecraft.world.level.block.state.BlockState railState =
                entity.level().getBlockState(railPos);

        if (!net.minecraft.world.level.block.BaseRailBlock.isRail(railState)) {
            railPos = railPos.below();
            railState = entity.level().getBlockState(railPos);
        }

        if (!net.minecraft.world.level.block.BaseRailBlock.isRail(railState)) {
            return referenceYaw;
        }

        net.minecraft.world.level.block.BaseRailBlock railBlock =
                (net.minecraft.world.level.block.BaseRailBlock) railState.getBlock();
        net.minecraft.world.level.block.state.properties.RailShape railShape =
                railBlock.getRailDirection(
                        railState,
                        entity.level(),
                        railPos,
                        entity);

        float axisYaw = switch (railShape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0.0F;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 90.0F;
            case SOUTH_EAST, NORTH_WEST -> 45.0F;
            case NORTH_EAST, SOUTH_WEST -> -45.0F;
        };

        float oppositeYaw = Mth.wrapDegrees(axisYaw + 180.0F);
        float axisDelta = Math.abs(Mth.wrapDegrees(axisYaw - referenceYaw));
        float oppositeDelta = Math.abs(Mth.wrapDegrees(oppositeYaw - referenceYaw));

        return axisDelta <= oppositeDelta
                ? axisYaw
                : oppositeYaw;
    }

    private static float transportVisualYaw(FreightCart entity, float partialTicks) {
        float entityYaw = Mth.rotLerp(
                partialTicks,
                entity.yRotO,
                entity.getYRot());

        Float remembered = LAST_TRANSPORT_VISUAL_YAW.get(entity);
        float referenceYaw = remembered != null
                ? remembered
                : entityYaw;

        double dx = entity.getDeltaMovement().x;
        double dz = entity.getDeltaMovement().z;
        double horizontalSq = dx * dx + dz * dz;

        if (horizontalSq < 0.000025D) {
            float heldYaw = remembered != null
                    ? remembered
                    : initialRailAlignedYaw(entity, entityYaw);
            LAST_TRANSPORT_VISUAL_YAW.put(entity, heldYaw);
            return heldYaw;
        }

        float motionYaw = (float) (
                Mth.atan2(-dx, dz) * (180.0D / Math.PI));
        float oppositeYaw = Mth.wrapDegrees(motionYaw + 180.0F);

        float motionDelta = Math.abs(
                Mth.wrapDegrees(motionYaw - referenceYaw));
        float oppositeDelta = Math.abs(
                Mth.wrapDegrees(oppositeYaw - referenceYaw));

        float targetYaw = motionDelta <= oppositeDelta
                ? motionYaw
                : oppositeYaw;

        // Unwrap around the remembered orientation before smoothing, preventing
        // +179/-179 representation changes from appearing as a full spin.
        float unwrappedTarget = referenceYaw
                + Mth.wrapDegrees(targetYaw - referenceYaw);

        float visualYaw = Mth.rotLerp(
                0.40F,
                referenceYaw,
                unwrappedTarget);

        LAST_TRANSPORT_VISUAL_YAW.put(entity, visualYaw);
        return visualYaw;
    }
    @Override
    public void render(T entity, float yaw, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int packedLight) {
        float railYaw = transportVisualYaw(entity, partialTicks);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(270.0F - railYaw));
        pose.translate(0.0D, 0.22D, 0.0D);

        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));

        int body = prisoner ? CYAN : YELLOW;
        int bodyDark = prisoner ? CYAN_DARK : YELLOW_DARK;

        // --- underframe / couplers ---
        box(pose, out, packedLight, -1.72F, -0.30F, -0.72F, 1.72F, -0.10F, 0.72F, DARK);
        box(pose, out, packedLight, -1.66F, -0.10F, -0.74F, 1.66F, 0.07F, 0.74F, BROWN_DARK);
        box(pose, out, packedLight, -1.93F, -0.23F, -0.12F, -1.66F, -0.08F, 0.12F, DARK_METAL);
        box(pose, out, packedLight, 1.66F, -0.23F, -0.12F, 1.93F, -0.08F, 0.12F, DARK_METAL);

        // --- wheels / bogies ---
        box(pose, out, packedLight, -1.30F, -0.53F, -0.82F, -0.82F, -0.18F, -0.62F, DARK);
        box(pose, out, packedLight, -1.30F, -0.53F, 0.62F, -0.82F, -0.18F, 0.82F, DARK);
        box(pose, out, packedLight, 0.82F, -0.53F, -0.82F, 1.30F, -0.18F, -0.62F, DARK);
        box(pose, out, packedLight, 0.82F, -0.53F, 0.62F, 1.30F, -0.18F, 0.82F, DARK);
        box(pose, out, packedLight, -1.34F, -0.24F, -0.60F, -0.78F, -0.09F, 0.60F, DARK_METAL);
        box(pose, out, packedLight, 0.78F, -0.24F, -0.60F, 1.34F, -0.09F, 0.60F, DARK_METAL);

        // --- roof and colored fascia ---
        box(pose, out, packedLight, -1.74F, 1.57F, -0.78F, 1.74F, 1.77F, 0.78F, body);
        box(pose, out, packedLight, -1.62F, 1.42F, -0.73F, 1.62F, 1.57F, 0.73F, BROWN);
        box(pose, out, packedLight, -1.58F, 1.19F, -0.735F, 1.58F, 1.40F, -0.635F, body);
        box(pose, out, packedLight, -1.58F, 1.19F, 0.635F, 1.58F, 1.40F, 0.735F, body);

        // --- lower painted boards ---
        box(pose, out, packedLight, -1.58F, 0.12F, -0.735F, 1.58F, 0.35F, -0.635F, bodyDark);
        box(pose, out, packedLight, -1.58F, 0.12F, 0.635F, 1.58F, 0.35F, 0.735F, bodyDark);
        box(pose, out, packedLight, -1.635F, 0.12F, -0.62F, -1.535F, 0.35F, 0.62F, bodyDark);
        box(pose, out, packedLight, 1.535F, 0.12F, -0.62F, 1.635F, 0.35F, 0.62F, bodyDark);

        // --- strong brown structural frame ---
        post(pose, out, packedLight, -1.54F, -0.67F);
        post(pose, out, packedLight, -1.54F, 0.67F);
        post(pose, out, packedLight, 1.54F, -0.67F);
        post(pose, out, packedLight, 1.54F, 0.67F);

        sidePost(pose, out, packedLight, -0.62F, -0.685F);
        sidePost(pose, out, packedLight, 0.62F, -0.685F);
        sidePost(pose, out, packedLight, -0.62F, 0.685F);
        sidePost(pose, out, packedLight, 0.62F, 0.685F);

        // side mid rails
        box(pose, out, packedLight, -1.50F, 0.69F, -0.72F, 1.50F, 0.84F, -0.64F, BROWN);
        box(pose, out, packedLight, -1.50F, 0.69F, 0.64F, 1.50F, 0.84F, 0.72F, BROWN);

        // end framing
        box(pose, out, packedLight, -1.63F, 0.68F, -0.60F, -1.53F, 0.84F, 0.60F, BROWN);
        box(pose, out, packedLight, 1.53F, 0.68F, -0.60F, 1.63F, 0.84F, 0.60F, BROWN);

        // --- center doors on both sides ---
        door(pose, out, packedLight, -0.685F, body, bodyDark);
        door(pose, out, packedLight, 0.685F, body, bodyDark);

        // --- open cage bars ---
        if (prisoner) {
            // More iron in the Mob/Prisoner car.
            for (float x : new float[]{-1.26F, -1.05F, -0.33F, 0.33F, 1.05F, 1.26F}) {
                barSide(pose, out, packedLight, x, -0.705F, METAL);
                barSide(pose, out, packedLight, x, 0.705F, METAL);
            }
            for (float z : new float[]{-0.43F, -0.20F, 0.20F, 0.43F}) {
                barEnd(pose, out, packedLight, -1.585F, z, METAL);
                barEnd(pose, out, packedLight, 1.585F, z, METAL);
            }
        } else {
            // Livestock stays more wooden but still visibly caged.
            for (float x : new float[]{-1.18F, -0.94F, 0.94F, 1.18F}) {
                barSide(pose, out, packedLight, x, -0.705F, METAL);
                barSide(pose, out, packedLight, x, 0.705F, METAL);
            }
            for (float z : new float[]{-0.40F, 0.0F, 0.40F}) {
                barEnd(pose, out, packedLight, -1.585F, z, BROWN);
                barEnd(pose, out, packedLight, 1.585F, z, BROWN);
            }
        }

        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, packedLight);
    }

    private static void post(PoseStack pose, VertexConsumer out, int light, float x, float z) {
        box(pose, out, light, x - 0.09F, 0.05F, z - 0.07F, x + 0.09F, 1.56F, z + 0.07F, BROWN);
    }

    private static void sidePost(PoseStack pose, VertexConsumer out, int light, float x, float z) {
        box(pose, out, light, x - 0.07F, 0.26F, z - 0.055F, x + 0.07F, 1.43F, z + 0.055F, BROWN);
    }

    private static void door(PoseStack pose, VertexConsumer out, int light, float z, int body, int bodyDark) {
        box(pose, out, light, -0.34F, 0.12F, z - 0.055F, -0.22F, 1.43F, z + 0.055F, BROWN_DARK);
        box(pose, out, light, 0.22F, 0.12F, z - 0.055F, 0.34F, 1.43F, z + 0.055F, BROWN_DARK);
        box(pose, out, light, -0.34F, 1.30F, z - 0.055F, 0.34F, 1.43F, z + 0.055F, BROWN_DARK);
        box(pose, out, light, -0.25F, 0.16F, z - 0.060F, 0.25F, 0.50F, z + 0.060F, bodyDark);
        box(pose, out, light, -0.25F, 0.50F, z - 0.060F, 0.25F, 0.60F, z + 0.060F, body);
        box(pose, out, light, 0.18F, 0.72F, z - 0.075F, 0.28F, 0.82F, z + 0.075F, METAL);
    }

    private static void barSide(PoseStack pose, VertexConsumer out, int light, float x, float z, int color) {
        box(pose, out, light, x - 0.025F, 0.36F, z - 0.035F, x + 0.025F, 1.18F, z + 0.035F, color);
    }

    private static void barEnd(PoseStack pose, VertexConsumer out, int light, float x, float z, int color) {
        box(pose, out, light, x - 0.035F, 0.36F, z - 0.025F, x + 0.035F, 1.18F, z + 0.025F, color);
    }

    private static void box(PoseStack pose, VertexConsumer out, int light,
            float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        PoseStack.Pose p = pose.last();

        // -Z
        quad(out, p, light, rgb,
                x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, 0,0,-1);
        // +Z
        quad(out, p, light, rgb,
                x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, 0,0,1);
        // -X
        quad(out, p, light, rgb,
                x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, -1,0,0);
        // +X
        quad(out, p, light, rgb,
                x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, 1,0,0);
        // -Y
        quad(out, p, light, rgb,
                x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, 0,-1,0);
        // +Y
        quad(out, p, light, rgb,
                x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, 0,1,0);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose p, int light, int rgb,
            float ax,float ay,float az,
            float bx,float by,float bz,
            float cx,float cy,float cz,
            float dx,float dy,float dz,
            float nx,float ny,float nz) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();

        v(out,m,n,light,r,g,b,ax,ay,az,0,0,nx,ny,nz);
        v(out,m,n,light,r,g,b,bx,by,bz,1,0,nx,ny,nz);
        v(out,m,n,light,r,g,b,cx,cy,cz,1,1,nx,ny,nz);
        v(out,m,n,light,r,g,b,dx,dy,dz,0,1,nx,ny,nz);
    }

    private static void v(VertexConsumer out, Matrix4f m, Matrix3f n, int light,
            int r,int g,int b,
            float x,float y,float z,float u,float v,
            float nx,float ny,float nz) {
        out.vertex(m, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return WHITE;
    }
}
