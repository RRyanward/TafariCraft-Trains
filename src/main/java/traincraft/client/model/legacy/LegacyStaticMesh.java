/*
 * Traincraft 1.20.1 legacy static-mesh bridge.
 * Step 8.4.0 Batch B1. Converts recovered CE 7.1 TMT geometry into a compact
 * resource while preserving box/shape-box UVs and stabilizing zero-thickness faces.
 */
package traincraft.client.model.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class LegacyStaticMesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x54434D31; // TCM1
    private static final int VERSION = 1;
    private static final int FLOATS_PER_VERTEX = 8;

    private final ResourceLocation source;
    private final float[] vertices;
    private final int vertexCount;
    private final float minX, minY, minZ, maxX, maxY, maxZ;
    private final int stabilizedZeroDimensions;

    private LegacyStaticMesh(ResourceLocation source, float[] vertices, int vertexCount,
                             float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ,
                             int stabilizedZeroDimensions) {
        this.source = source;
        this.vertices = vertices;
        this.vertexCount = vertexCount;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.stabilizedZeroDimensions = stabilizedZeroDimensions;
    }

    public static LegacyStaticMesh load(ResourceLocation location) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location)
                    .orElseThrow(() -> new IOException("Missing legacy mesh: " + location));
            try (InputStream raw = resource.open();
                 DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
                int magic = in.readInt();
                int version = in.readInt();
                if (magic != MAGIC || version != VERSION) {
                    throw new IOException("Unsupported legacy mesh header for " + location);
                }
                int vertexCount = in.readInt();
                if (vertexCount <= 0 || vertexCount > 2_000_000) {
                    throw new IOException("Invalid legacy mesh vertex count " + vertexCount + " for " + location);
                }
                float minX = in.readFloat(); float minY = in.readFloat(); float minZ = in.readFloat();
                float maxX = in.readFloat(); float maxY = in.readFloat(); float maxZ = in.readFloat();
                int stabilized = in.readInt();
                float[] data = new float[vertexCount * FLOATS_PER_VERTEX];
                for (int i = 0; i < data.length; i++) data[i] = in.readFloat();
                LegacyStaticMesh mesh = new LegacyStaticMesh(location, data, vertexCount,
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized);
                LOGGER.info("[TC-LEGACY-MESH] mesh={} vertices={} quads={} bounds=({},{},{})..({},{},{}) zeroThicknessStabilized={}",
                        location, vertexCount, vertexCount / 4,
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized);
                return mesh;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Traincraft legacy mesh " + location, ex);
        }
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < this.vertices.length; i += FLOATS_PER_VERTEX) {
            consumer.vertex(pose.pose(), vertices[i], vertices[i + 1], vertices[i + 2])
                    .color(255, 255, 255, 255)
                    .uv(vertices[i + 3], vertices[i + 4])
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(pose.normal(), vertices[i + 5], vertices[i + 6], vertices[i + 7])
                    .endVertex();
        }
    }

    public ResourceLocation source() { return source; }
    public int vertexCount() { return vertexCount; }
    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }
    public int stabilizedZeroDimensions() { return stabilizedZeroDimensions; }
}
