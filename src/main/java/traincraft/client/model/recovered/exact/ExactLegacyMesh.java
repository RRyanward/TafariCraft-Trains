package traincraft.client.model.recovered.exact;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;

/** Runtime loader for v1j exact CustomModelRenderer meshes. Visual-only. */
public final class ExactLegacyMesh {
    private static final int MAGIC = 0x54434D58;
    private static final int FLOATS_PER_VERTEX = 9;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private final String id;
    private final float[] data;

    private ExactLegacyMesh(String id, float[] data) {
        this.id = id;
        this.data = data;
    }

    public static ExactLegacyMesh load(String id) {
        String path = "/assets/traincraft/legacy_exact/" + id + ".tcm";
        try (InputStream raw = ExactLegacyMesh.class.getResourceAsStream(path)) {
            if (raw == null) throw new IllegalStateException("Missing exact legacy mesh resource: " + path);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
                int magic = in.readInt();
                if (magic != MAGIC) throw new IllegalStateException("Bad exact legacy mesh magic: " + path);
                int vertices = in.readInt();
                if (vertices <= 0 || vertices > 200000 || (vertices & 3) != 0) {
                    throw new IllegalStateException("Invalid exact legacy vertex count " + vertices + " for " + path);
                }
                float[] data = new float[vertices * FLOATS_PER_VERTEX];
                for (int i = 0; i < data.length; i++) data[i] = in.readFloat();
                return new ExactLegacyMesh(id, data);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load exact legacy mesh " + path, ex);
        }
    }

public void render(PoseStack stack, VertexConsumer out, int packedLight, int overlay) {
        PoseStack.Pose pose = stack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        int activeChunk = -1;
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec transform = null;
        String meshKey = "exact:" + this.id;

        for (int i = 0; i < data.length; i += FLOATS_PER_VERTEX) {
            int vertexIndex = i / FLOATS_PER_VERTEX;
            int chunkIndex = vertexIndex / 24;

            if (chunkIndex != activeChunk) {
                activeChunk = chunkIndex;
                transform = traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey, chunkIndex, this.data, FLOATS_PER_VERTEX);
            }

            float x = data[i];
            float y = data[i + 1];
            float z = data[i + 2];

            float nx = data[i + 5];
            float ny = data[i + 6];
            float nz = data[i + 7];

            if (transform != null) {
                if (transform.rotates()) {
                    float dx = x - transform.centerX();
                    float dy = y - transform.centerY();
                    float dz = z - transform.centerZ();

                    if (transform.axis() == 0) {
                        y = transform.centerY()
                                + dy * transform.cos()
                                - dz * transform.sin();
                        z = transform.centerZ()
                                + dy * transform.sin()
                                + dz * transform.cos();

                        float rny = ny * transform.cos() - nz * transform.sin();
                        float rnz = ny * transform.sin() + nz * transform.cos();
                        ny = rny;
                        nz = rnz;
                    } else if (transform.axis() == 1) {
                        x = transform.centerX()
                                + dx * transform.cos()
                                - dz * transform.sin();
                        z = transform.centerZ()
                                + dx * transform.sin()
                                + dz * transform.cos();

                        float rnx = nx * transform.cos() - nz * transform.sin();
                        float rnz = nx * transform.sin() + nz * transform.cos();
                        nx = rnx;
                        nz = rnz;
                    } else {
                        x = transform.centerX()
                                + dx * transform.cos()
                                - dy * transform.sin();
                        y = transform.centerY()
                                + dx * transform.sin()
                                + dy * transform.cos();

                        float rnx = nx * transform.cos() - ny * transform.sin();
                        float rny = nx * transform.sin() + ny * transform.cos();
                        nx = rnx;
                        ny = rny;
                    }
                }

                x += transform.translateX();
                y += transform.translateY();
                z += transform.translateZ();
            }

            int light = data[i + 8] > 0.5F ? FULL_BRIGHT : packedLight;

            out.vertex(matrix, x, y, z)
                    .color(255, 255, 255, 255)
                    .uv(data[i + 3], data[i + 4])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normal, nx, ny, nz)
                    .endVertex();
        }
    }
}