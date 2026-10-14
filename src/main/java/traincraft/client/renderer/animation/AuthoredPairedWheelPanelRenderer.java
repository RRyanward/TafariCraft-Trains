package traincraft.client.renderer.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * TC_DEV38_R1G1Z7P3_EXACT_PAIRED_WHEEL_PANEL_SPLIT
 *
 * Exact structural split for authored 1x6x14 paired-wheel panels.
 *
 * Truth-sweep proof:
 * - 4-4-0 legacy chunks 0 / 1 each carry two front-truck wheels.
 * - Forney part_052 / 053 / 055 / 072 each carry two trailing-truck wheels.
 *
 * The source panel's original vertices and UVs are clipped into:
 *   z 0..6   = wheel A
 *   z 6..8   = static authored gap
 *   z 8..14  = wheel B
 *
 * UVs are interpolated directly from the original authored polygons. No
 * replacement wheel texture coordinates are invented.
 *
 * Wheel diameter is 6 authored pixels, versus the existing 9-pixel main
 * drivers, therefore angular phase is 4.5/3.0 = 1.5x main-driver phase.
 */
public final class AuthoredPairedWheelPanelRenderer {
    private static final String LEGACY_440 =
            "legacy:traincraft:legacy_meshes/locomotive_steam_4_4_0.tcm";

    private static final String[] FORNEY_PANELS = {
            "part_052",
            "part_053",
            "part_055",
            "part_072"
    };

    private static Field cubePolygonsField;
    private static Class<?> cachedCubeClass;

    private static Field polygonVerticesField;
    private static Field polygonNormalField;
    private static Class<?> cachedPolygonClass;

    private static Field vertexPosField;
    private static Field vertexUField;
    private static Field vertexVField;
    private static Class<?> cachedVertexClass;

    private AuthoredPairedWheelPanelRenderer() {}

    /**
     * Called from FleetNativeWheelPartAnimator before Forney's normal model
     * render. The exact carrier panels remain in the baked model, but their
     * own cube draw is suppressed so the split renderer below is the only
     * visible copy.
     */
    public static void prepareForney(Object model) {
        ModelPart root = root(model);
        if (root == null) return;

        for (String name : FORNEY_PANELS) {
            ModelPart part = child(root, name);
            if (part != null) {
                part.skipDraw = false;
            }
        }

        // Historical rejected overlays must never become visible again.
        for (String name : new String[] {
                "tc_forney_rear_left_0",
                "tc_forney_rear_left_1",
                "tc_forney_rear_right_0",
                "tc_forney_rear_right_1"
        }) {
            ModelPart part = child(root, name);
            if (part != null) part.visible = false;
        }
    }

    /**
     * Draw the four exact Forney paired-wheel panels after the normal model
     * render, using the same pose stack, texture buffer, light and overlay.
     */    /**
     * TC_DEV38_R1G1Z7R3_FORNEY_REAR_CIRCULAR_FACE_OVERLAY
     *
     * Keep the original rear panels static and overlay only the two circular
     * wheel faces per outside panel. This removes the rotating square slab
     * artifact while preserving the authored carrier/bar geometry.
     */
    public static void renderForney(
            Object model,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        ModelPart root = root(model);
        if (root == null) return;

        ModelPart mainDriver = child(root, "part_020");
        if (mainDriver == null) return;

        final float smallWheelAngle = mainDriver.xRot * 1.5F;

        root.visit(poseStack, (pose, path, cubeIndex, cube) -> {
            String leaf = leaf(path);

            // Only the two OUTSIDE authored faces get the animated overlay.
            // part_052 / part_053 remain completely static inner carriers.
            if (!"part_055".equals(leaf) && !"part_072".equals(leaf)) {
                return;
            }

            renderForneyRearWheelDisc(
                    cube,
                    pose,
                    consumer,
                    packedLight,
                    packedOverlay,
                    leaf,
                    3.0F,
                    smallWheelAngle);

            renderForneyRearWheelDisc(
                    cube,
                    pose,
                    consumer,
                    packedLight,
                    packedOverlay,
                    leaf,
                    11.0F,
                    smallWheelAngle);
        });
    }

    /**
     * Intercepts only 4-4-0 chunks 0/1. Returns true when this method emitted
     * the complete exact split panel and the caller must skip the original
     * unsplit 24-vertex chunk.
     */
    public static boolean renderLegacy440PairedChunk(
            String meshKey,
            int chunkIndex,
            float[] data,
            int baseFloat,
            int stride,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        if (!LEGACY_440.equals(meshKey)
                || (chunkIndex != 0 && chunkIndex != 1)
                || data == null
                || stride < 8
                || baseFloat < 0
                || baseFloat + (24 * stride) > data.length) {
            return false;
        }

        float angle = BakedWheelAnimationContext.exact440PairedWheelPhase();
        if (Float.isNaN(angle)) return false;

        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        for (int vertex = 0; vertex < 24; vertex++) {
            int p = baseFloat + vertex * stride;
            minY = Math.min(minY, data[p + 1]);
            maxY = Math.max(maxY, data[p + 1]);
            minZ = Math.min(minZ, data[p + 2]);
        }

        float centerY = (minY + maxY) * 0.5F;

        for (int face = 0; face < 6; face++) {
            int faceBase = baseFloat + face * 4 * stride;
            List<V> original = new ArrayList<>(4);

            float nx = data[faceBase + 5];
            float ny = data[faceBase + 6];
            float nz = data[faceBase + 7];

            for (int vertex = 0; vertex < 4; vertex++) {
                int p = faceBase + vertex * stride;
                original.add(new V(
                        data[p],
                        data[p + 1],
                        data[p + 2],
                        data[p + 3],
                        data[p + 4]));
            }

            emitClipped(
                    original, nx, ny, nz,
                    minZ, minZ + 0.375F,
                    centerY, minZ + 0.1875F,
                    angle,
                    false,
                    pose, consumer, packedLight, packedOverlay);

            // Exact static 2px / 0.125-block authored gap.
            emitClipped(
                    original, nx, ny, nz,
                    minZ + 0.375F, minZ + 0.500F,
                    0.0F, 0.0F,
                    0.0F,
                    false,
                    pose, consumer, packedLight, packedOverlay);

            emitClipped(
                    original, nx, ny, nz,
                    minZ + 0.500F, minZ + 0.875F,
                    centerY, minZ + 0.6875F,
                    angle,
                    false,
                    pose, consumer, packedLight, packedOverlay);
        }

        return true;
    }

    private static void renderForneyRearWheelDisc(
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            String leaf,
            float localCenterZ,
            float angle) {

        try {
            Object[] sourcePolygons = polygons(cube);
            if (sourcePolygons == null) return;

            final boolean rightOutside = "part_055".equals(leaf);
            final boolean leftOutside = "part_072".equals(leaf);
            if (!rightOutside && !leftOutside) return;

            for (Object polygon : sourcePolygons) {
                Vector3f sourceNormal = polygonNormal(polygon);
                if (sourceNormal == null) continue;

                // Use only the authored OUTER X-facing side:
                //   part_055 at +X, part_072 at -X.
                if (rightOutside && sourceNormal.x() < 0.90F) continue;
                if (leftOutside && sourceNormal.x() > -0.90F) continue;

                Object[] sourceVertices = polygonVertices(polygon);
                if (sourceVertices == null || sourceVertices.length != 4) {
                    continue;
                }

                List<V> face = new ArrayList<>(4);
                for (Object sourceVertex : sourceVertices) {
                    Vector3f p = vertexPos(sourceVertex);
                    if (p == null) {
                        face.clear();
                        break;
                    }
                    face.add(new V(
                            p.x(),
                            p.y(),
                            p.z(),
                            vertexU(sourceVertex),
                            vertexV(sourceVertex)));
                }

                if (face.size() != 4) continue;

                float minY = Float.POSITIVE_INFINITY;
                float maxY = Float.NEGATIVE_INFINITY;
                float minZ = Float.POSITIVE_INFINITY;
                float maxZ = Float.NEGATIVE_INFINITY;
                float faceX = 0.0F;

                for (V v : face) {
                    minY = Math.min(minY, v.y);
                    maxY = Math.max(maxY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxZ = Math.max(maxZ, v.z);
                    faceX += v.x;
                }
                faceX /= face.size();

                // Exact authored panel contract: 1 x 6 x 14 pixels.
                if (Math.abs((maxY - minY) - 6.0F) > 0.001F
                        || Math.abs((maxZ - minZ) - 14.0F) > 0.001F) {
                    continue;
                }

                float centerY = (minY + maxY) * 0.5F;
                float centerZ = minZ + localCenterZ;

                emitForneyWheelDisc(
                        face,
                        faceX,
                        centerY,
                        centerZ,
                        3.0F,
                        angle,
                        sourceNormal,
                        pose,
                        consumer,
                        packedLight,
                        packedOverlay);

                // Exactly one authored outside polygon is required.
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Exact-only behavior: emit nothing rather than fall back to a
            // guessed panel or guessed UV mapping.
        }
    }

    /**
     * Emits a 24-segment circular wheel face.
     *
     * Geometry stays circular while vertex positions rotate around the X axis.
     * The original authored UV coordinate attached to each source point moves
     * with that vertex, so the wheel artwork visibly spins without rotating a
     * square 1x6x6 carrier slab.
     */
    private static void emitForneyWheelDisc(
            List<V> face,
            float sourceFaceX,
            float centerY,
            float centerZ,
            float radius,
            float angle,
            Vector3f sourceNormal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        V centerUv = sampleFaceUv(face, centerY, centerZ);
        if (centerUv == null) return;

        final int segments = 24;
        final float twoPi = (float) (Math.PI * 2.0D);

        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        // Small authored-space offset avoids z-fighting with the now-static
        // original panel. 0.02 pixel = 0.00125 block.
        float drawX = sourceFaceX + (sourceNormal.x() >= 0.0F ? 0.02F : -0.02F);

        for (int i = 0; i < segments; i++) {
            float t0 = twoPi * ((float) i / (float) segments);
            float t1 = twoPi * ((float) (i + 1) / (float) segments);

            // UVs come from the UNROTATED authored wheel square.
            float srcY0 = centerY + (float) Math.sin(t0) * radius;
            float srcZ0 = centerZ + (float) Math.cos(t0) * radius;
            float srcY1 = centerY + (float) Math.sin(t1) * radius;
            float srcZ1 = centerZ + (float) Math.cos(t1) * radius;

            V uv0 = sampleFaceUv(face, srcY0, srcZ0);
            V uv1 = sampleFaceUv(face, srcY1, srcZ1);
            if (uv0 == null || uv1 == null) return;

            // Geometry rotates, but a circle's outline remains circular.
            float geoY0 = centerY + (float) Math.sin(t0 + angle) * radius;
            float geoZ0 = centerZ + (float) Math.cos(t0 + angle) * radius;
            float geoY1 = centerY + (float) Math.sin(t1 + angle) * radius;
            float geoZ1 = centerZ + (float) Math.cos(t1 + angle) * radius;

            if (sourceNormal.x() >= 0.0F) {
                // Winding for +X outer face.
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, centerY, centerZ,
                        centerUv.u, centerUv.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, geoY1, geoZ1,
                        uv1.u, uv1.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, geoY0, geoZ0,
                        uv0.u, uv0.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, centerY, centerZ,
                        centerUv.u, centerUv.v,
                        sourceNormal,
                        packedLight, packedOverlay);
            } else {
                // Winding for -X outer face.
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, centerY, centerZ,
                        centerUv.u, centerUv.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, geoY0, geoZ0,
                        uv0.u, uv0.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, geoY1, geoZ1,
                        uv1.u, uv1.v,
                        sourceNormal,
                        packedLight, packedOverlay);
                emitDiscVertex(
                        consumer, matrix, normalMatrix,
                        drawX, centerY, centerZ,
                        centerUv.u, centerUv.v,
                        sourceNormal,
                        packedLight, packedOverlay);
            }
        }
    }

    private static void emitDiscVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normalMatrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            Vector3f sourceNormal,
            int packedLight,
            int packedOverlay) {

        consumer.vertex(matrix, x / 16.0F, y / 16.0F, z / 16.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(
                        normalMatrix,
                        sourceNormal.x(),
                        sourceNormal.y(),
                        sourceNormal.z())
                .endVertex();
    }

    /**
     * Exact bilinear sample of the original axis-aligned ModelPart face UVs.
     */
    private static V sampleFaceUv(List<V> face, float y, float z) {
        if (face == null || face.size() != 4) return null;

        V base = face.get(0);
        V yEdge = null;
        V zEdge = null;

        for (int i = 1; i < face.size(); i++) {
            V candidate = face.get(i);

            if (yEdge == null
                    && Math.abs(candidate.z - base.z) < 0.001F
                    && Math.abs(candidate.y - base.y) > 0.001F) {
                yEdge = candidate;
            }

            if (zEdge == null
                    && Math.abs(candidate.y - base.y) < 0.001F
                    && Math.abs(candidate.z - base.z) > 0.001F) {
                zEdge = candidate;
            }
        }

        if (yEdge == null || zEdge == null) return null;

        float dy = yEdge.y - base.y;
        float dz = zEdge.z - base.z;
        if (Math.abs(dy) < 0.000001F || Math.abs(dz) < 0.000001F) {
            return null;
        }

        float fy = (y - base.y) / dy;
        float fz = (z - base.z) / dz;

        return new V(
                base.x,
                y,
                z,
                base.u
                        + fy * (yEdge.u - base.u)
                        + fz * (zEdge.u - base.u),
                base.v
                        + fy * (yEdge.v - base.v)
                        + fz * (zEdge.v - base.v));
    }
    private static void renderModelPartCubeRegion(
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            float zMin,
            float zMax,
            float centerY,
            float centerZ,
            float angle) {

        try {
            Object[] polygons = polygons(cube);
            if (polygons == null) return;

            for (Object polygon : polygons) {
                Object[] vertices = polygonVertices(polygon);
                Vector3f normal = polygonNormal(polygon);
                if (vertices == null || vertices.length != 4 || normal == null) {
                    continue;
                }

                List<V> original = new ArrayList<>(4);
                for (Object vertex : vertices) {
                    Vector3f pos = vertexPos(vertex);
                    if (pos == null) {
                        original.clear();
                        break;
                    }
                    original.add(new V(
                            pos.x(),
                            pos.y(),
                            pos.z(),
                            vertexU(vertex),
                            vertexV(vertex)));
                }

                if (original.size() != 4) continue;

                emitClipped(
                        original,
                        normal.x(), normal.y(), normal.z(),
                        zMin, zMax,
                        centerY, centerZ,
                        angle,
                        true,
                        pose, consumer, packedLight, packedOverlay);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Safety behavior: if exact authored polygon access is unavailable,
            // emit nothing rather than inventing/falling back to bad geometry.
        }
    }

    private static void emitClipped(
            List<V> source,
            float nx,
            float ny,
            float nz,
            float zMin,
            float zMax,
            float centerY,
            float centerZ,
            float angle,
            boolean pixelCoordinates,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        List<V> clipped = clipMinZ(source, zMin);
        clipped = clipMaxZ(clipped, zMax);

        // All source faces are quads and axis-aligned clipping preserves quads.
        // Refuse unexpected topology rather than emitting malformed vertices.
        if (clipped.size() != 4) return;

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float rny = ny * cos - nz * sin;
        float rnz = ny * sin + nz * cos;

        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        for (V v : clipped) {
            float x = v.x;
            float y = v.y;
            float z = v.z;

            if (angle != 0.0F) {
                float dy = y - centerY;
                float dz = z - centerZ;
                y = centerY + dy * cos - dz * sin;
                z = centerZ + dy * sin + dz * cos;
            }

            if (pixelCoordinates) {
                x /= 16.0F;
                y /= 16.0F;
                z /= 16.0F;
            }

            consumer.vertex(matrix, x, y, z)
                    .color(255, 255, 255, 255)
                    .uv(v.u, v.v)
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(normal, nx, rny, rnz)
                    .endVertex();
        }
    }

    private static List<V> clipMinZ(List<V> input, float min) {
        return clip(input, min, true);
    }

    private static List<V> clipMaxZ(List<V> input, float max) {
        return clip(input, max, false);
    }

    private static List<V> clip(List<V> input, float plane, boolean keepGreater) {
        List<V> output = new ArrayList<>(6);
        if (input.isEmpty()) return output;

        V previous = input.get(input.size() - 1);
        boolean previousInside = inside(previous.z, plane, keepGreater);

        for (V current : input) {
            boolean currentInside = inside(current.z, plane, keepGreater);

            if (currentInside != previousInside) {
                float dz = current.z - previous.z;
                if (Math.abs(dz) > 0.000001F) {
                    float t = (plane - previous.z) / dz;
                    output.add(lerp(previous, current, t));
                }
            }

            if (currentInside) {
                output.add(current);
            }

            previous = current;
            previousInside = currentInside;
        }

        return output;
    }

    private static boolean inside(float z, float plane, boolean keepGreater) {
        return keepGreater ? z >= plane - 0.00001F : z <= plane + 0.00001F;
    }

    private static V lerp(V a, V b, float t) {
        return new V(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t,
                a.u + (b.u - a.u) * t,
                a.v + (b.v - a.v) * t);
    }

    private static boolean isForneyPanel(String name) {
        for (String panel : FORNEY_PANELS) {
            if (panel.equals(name)) return true;
        }
        return false;
    }

    private static String leaf(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static ModelPart root(Object model) {
        if (model == null) return null;

        // TC_DEV38_R1G1Z7R2_DIRECT_MODELPART_ROOT
        // ForneySteamLocomotiveRenderer passes the already-baked ModelPart
        // directly. Accept it as the root instead of reflectively searching
        // inside ModelPart and silently returning null.
        if (model instanceof ModelPart part) return part;

        Class<?> type = model.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("root");
                field.setAccessible(true);
                Object value = field.get(model);
                if (value instanceof ModelPart part) return part;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try generic ModelPart fields below.
            }

            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) return part;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue.
                }
            }

            type = type.getSuperclass();
        }

        return null;
    }

    private static ModelPart child(ModelPart root, String name) {
        try {
            return root.getChild(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object[] polygons(ModelPart.Cube cube)
            throws ReflectiveOperationException {
        Class<?> type = cube.getClass();
        if (cubePolygonsField == null || cachedCubeClass != type) {
            cubePolygonsField = field(type, "polygons", true, null);
            cachedCubeClass = type;
        }
        Object value = cubePolygonsField.get(cube);
        return value instanceof Object[] array ? array : null;
    }

    private static Object[] polygonVertices(Object polygon)
            throws ReflectiveOperationException {
        Class<?> type = polygon.getClass();
        if (polygonVerticesField == null || cachedPolygonClass != type) {
            polygonVerticesField = field(type, "vertices", true, null);
            polygonNormalField = field(type, "normal", false, Vector3f.class);
            cachedPolygonClass = type;
        }
        Object value = polygonVerticesField.get(polygon);
        return value instanceof Object[] array ? array : null;
    }

    private static Vector3f polygonNormal(Object polygon)
            throws ReflectiveOperationException {
        if (polygonNormalField == null || cachedPolygonClass != polygon.getClass()) {
            polygonVertices(polygon);
        }
        Object value = polygonNormalField.get(polygon);
        return value instanceof Vector3f vector ? vector : null;
    }

    private static Vector3f vertexPos(Object vertex)
            throws ReflectiveOperationException {
        ensureVertexFields(vertex);
        Object value = vertexPosField.get(vertex);
        return value instanceof Vector3f vector ? vector : null;
    }

    private static float vertexU(Object vertex)
            throws ReflectiveOperationException {
        ensureVertexFields(vertex);
        return vertexUField.getFloat(vertex);
    }

    private static float vertexV(Object vertex)
            throws ReflectiveOperationException {
        ensureVertexFields(vertex);
        return vertexVField.getFloat(vertex);
    }

    private static void ensureVertexFields(Object vertex)
            throws ReflectiveOperationException {
        Class<?> type = vertex.getClass();
        if (vertexPosField != null && cachedVertexClass == type) return;

        vertexPosField = field(type, "pos", false, Vector3f.class);
        vertexUField = field(type, "u", false, Float.TYPE);
        vertexVField = field(type, "v", false, Float.TYPE);
        cachedVertexClass = type;
    }

    private static Field field(
            Class<?> type,
            String preferredName,
            boolean requireArray,
            Class<?> requiredType)
            throws ReflectiveOperationException {

        try {
            Field preferred = type.getDeclaredField(preferredName);
            preferred.setAccessible(true);
            return preferred;
        } catch (NoSuchFieldException ignored) {
            // Fall back by shape/type.
        }

        for (Field candidate : type.getDeclaredFields()) {
            if (requireArray && !candidate.getType().isArray()) continue;
            if (requiredType != null && candidate.getType() != requiredType) continue;
            candidate.setAccessible(true);
            return candidate;
        }

        throw new NoSuchFieldException(
                type.getName() + "." + preferredName);
    }

    private static final class V {
        private final float x;
        private final float y;
        private final float z;
        private final float u;
        private final float v;

        private V(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }
}
