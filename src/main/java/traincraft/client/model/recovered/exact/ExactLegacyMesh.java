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
    private final float[] data;

    private ExactLegacyMesh(float[] data) { this.data = data; }

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
                return new ExactLegacyMesh(data);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load exact legacy mesh " + path, ex);
        }
    }

    public void render(PoseStack stack, VertexConsumer out, int packedLight, int overlay) {
        PoseStack.Pose pose = stack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        for (int i = 0; i < data.length; i += FLOATS_PER_VERTEX) {
            int light = data[i + 8] > 0.5F ? FULL_BRIGHT : packedLight;
            out.vertex(matrix, data[i], data[i + 1], data[i + 2])
                    .color(255, 255, 255, 255)
                    .uv(data[i + 3], data[i + 4])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normal, data[i + 5], data[i + 6], data[i + 7])
                    .endVertex();
        }
    }
}