package traincraft.client.renderer.animation;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * TC_DEV38_R1G1Z7Y3_CIRCULAR_WHEEL_MASK_STATIC_REMAINDER
 *
 * Final-five production candidate based on the source artwork itself.
 *
 * The old model stores several wheel rows as textured rectangular panels whose
 * non-wheel pixels are transparent.  Previous r1g1z7w7 synthetic circles were
 * rejected because they guessed where the wheel art lived inside those panels.
 *
 * This implementation does not invent wheel centers:
 *  - it reads the exact Traincraft PNG alpha mask already used by the renderer;
 *  - maps each target face's authored UVs back to texture pixels;
 *  - locates the expected wheel islands/bands from those alpha pixels;
 *  - clips the ORIGINAL polygon + UVs to those exact regions;
 *  - rotates only those clipped authored regions around their measured centers.
 *
 * The original native panel is suppressed only after a valid wheel-region set
 * is detected.  If detection fails, the original panel remains visible/static.
 *
 * Native source evidence:
 *  ChME3 : box25                       = proven wheel-art panel, 4 visible wheels
 *  V60   : box / box1                 = proven wheel-art panels, 4 wheels each
 *  BR01  : part_049 / part_057        = two 71x13 wheel-art side panels
 *  USSR  : part_018 / part_045        = two 54x9 five-driver side panels
 *
 * GP7 remains exact/baked.  Only the already-proven lower chunks 13/16/17 are
 * searched for a four-wheel alpha row.  Their original mesh remains static in
 * this candidate; the exact authored rotating region is drawn over it.
 */
public final class StructuralWheelFaceOverlayRenderer {
    private static final ThreadLocal<Entity> CURRENT_ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<Float> CURRENT_PHASE = new ThreadLocal<>();

    private static final int EXACT_STRIDE = 9;
    private static final int EXACT_BOX_VERTICES = 24;
    private static final int ALPHA_THRESHOLD = 24;
    // TC_DEV38_R1G1Z7Y3_CIRCULAR_CLIP
    // Offline DEV42 UV extraction shows rods/linkage share the same old
    // rectangular wheel panels.  Subdivide the panel and rotate only
    // circular wheel sectors; everything outside the circles stays static.
    // TC_DEV38_R1G1Z7Y7_FULL_WHEEL_OVERLAY_NO_CUTOFF
    // ChME3 is already accepted: preserve its exact r1g1z7y3 profile.
    private static final int CHME3_DONE_WHEEL_SLICES = 18;
    private static final float CHME3_DONE_RADIUS_SCALE = 0.485F;

    // Remaining four: fuller circle and finer tessellation.
    private static final int REMAINING_WHEEL_SLICES = 48;
    private static final float REMAINING_WHEEL_RADIUS_SCALE = 0.500F;

    // ModelPart coordinates are authored pixels and are divided by 16 later.
    // 0.025 px = 0.0015625 model units, matching the existing GP7 overlay lift.
    private static final float NATIVE_OVERLAY_OFFSET_PIXELS = 0.025F;
    private static final float GEOMETRY_EPSILON = 0.0005F;

    private static final Map<String, ResourceLocation> TEXTURES = Map.of(
            "locomotive_diesel_chme3",
            new ResourceLocation("traincraft", "textures/trains/chme3.png"),
            "locomotive_diesel_v60",
            new ResourceLocation("traincraft", "textures/trains/loco_v60_db_red.png"),
            "locomotive_steam_br01",
            new ResourceLocation("traincraft", "textures/trains/locobr01_db_black.png"),
            "locomotive_steam_ussr",
            new ResourceLocation("traincraft", "textures/trains/locoer_ussr.png"),
            "locomotive_diesel_gp7",
            new ResourceLocation("traincraft", "textures/trains/gp7_red.png"));

    private static final Map<String, AlphaMask> MASKS = new HashMap<>();
    private static final Set<String> MASK_FAILURES = new HashSet<>();
    private static final Set<String> LOGGED = new HashSet<>();
    private static final Map<Object, String> PREPARED = new IdentityHashMap<>();

    private static Field cubePolygonsField;
    private static Class<?> cachedCubeClass;

    private static Field polygonVerticesField;
    private static Field polygonNormalField;
    private static Class<?> cachedPolygonClass;

    private static Field vertexPosField;
    private static Field vertexUField;
    private static Field vertexVField;
    private static Class<?> cachedVertexClass;

    private StructuralWheelFaceOverlayRenderer() {}

    public static void capture(Entity entity, float phase) {
        CURRENT_ENTITY.set(entity);
        CURRENT_PHASE.set(phase);
    }

    /**
     * Called before the normal native ModelPart render.
     *
     * A target panel is hidden only if its exact authored UV face resolves the
     * expected number of wheel regions from the PNG alpha mask.  This makes a
     * texture/layout mismatch fail safe: the old static panel remains visible.
     */
    public static void prepareNative(String id, Object model) {
        if (id == null || model == null) return;
        if (!isNativeTarget(id)) return;

        String previous = PREPARED.get(model);
        if (id.equals(previous)) return;

        ModelPart root = root(model);
        AlphaMask mask = mask(id);
        if (root == null || mask == null) return;

        // TC_DEV38_R1G1Z7Y8_TEXEL_MASK_PREPARE
        // Use the original alpha-proven preparePart() suppression again.

        int prepared = 0;

        if ("locomotive_diesel_chme3".equals(id)) {
            prepared += preparePart(root, "box25", mask, 4);
        } else if ("locomotive_diesel_v60".equals(id)) {
            prepared += preparePart(root, "box", mask, 4);
            prepared += preparePart(root, "box1", mask, 4);
        } else if ("locomotive_steam_br01".equals(id)) {
            prepared += preparePart(root, "part_049", mask, 5);
            prepared += preparePart(root, "part_057", mask, 5);
        } else if ("locomotive_steam_ussr".equals(id)) {
            prepared += preparePart(root, "part_018", mask, 5);
            prepared += preparePart(root, "part_045", mask, 5);
        }

        if (prepared > 0) {
            PREPARED.put(model, id);
            logOnce(
                    id + ":prepare",
                    "[TC alpha-wheel] prepared " + id
                            + " authored wheel panel(s)=" + prepared);
        }
    }

    public static void renderNative(
            Object model,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        String id = activeId();
        if (!isNativeTarget(id)) return;

        ModelPart root = root(model);
        AlphaMask mask = mask(id);
        if (root == null || mask == null) return;

        float angle = phase();

        root.visit(poseStack, (pose, path, cubeIndex, cube) -> {
            String leaf = leaf(path);
            int expected = expectedNativeWheels(id, leaf);
            if (expected <= 0) return;

            // TC_DEV38_R1G1Z7Y8_NATIVE_SUPPRESSED_TEXEL_RECONSTRUCTION
            // prepareNative() suppresses only an alpha-proven target panel.
            ModelPart target = directChild(root, leaf);
            if (target == null || !target.skipDraw) return;

            renderCubeWheelRegions(
                    id,
                    leaf,
                    cube,
                    pose,
                    consumer,
                    packedLight,
                    packedOverlay,
                    mask,
                    expected,
                    angle,
                    true,
                    0.0F);
        });
    }

    public static void renderExact(
            Object mesh,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay) {

        if (!"locomotive_diesel_gp7".equals(activeId()) || mesh == null) return;

        AlphaMask mask = mask("locomotive_diesel_gp7");
        if (mask == null) return;

        String meshId = stringField(mesh, "id");
        float[] data = floatArrayField(mesh, "data");
        if (meshId == null || !meshId.toLowerCase(Locale.ROOT).contains("gp7")) return;
        if (data == null || data.length < (18 * EXACT_BOX_VERTICES * EXACT_STRIDE)) return;

        final int[] chunks = {13, 16, 17};
        final float angle = phase();
        final PoseStack.Pose pose = poseStack.last();

        int emittedFaces = 0;

        for (int chunk : chunks) {
            int startVertex = chunk * EXACT_BOX_VERTICES;
            int endVertex = startVertex + EXACT_BOX_VERTICES;
            if (endVertex * EXACT_STRIDE > data.length) continue;

            for (int q = startVertex; q + 3 < endVertex; q += 4) {
                List<V> face = new ArrayList<>(4);
                float nx = 0.0F;
                float ny = 0.0F;
                float nz = 0.0F;

                for (int vertex = q; vertex < q + 4; vertex++) {
                    int i = vertex * EXACT_STRIDE;
                    face.add(new V(
                            data[i],
                            data[i + 1],
                            data[i + 2],
                            data[i + 3],
                            data[i + 4]));
                    nx += data[i + 5];
                    ny += data[i + 6];
                    nz += data[i + 7];
                }

                nx *= 0.25F;
                ny *= 0.25F;
                nz *= 0.25F;

                if (Math.abs(nz) < 0.90F) continue;
                if (rangeX(face) < 2.0F || rangeY(face) < 0.35F) continue;

                List<WheelRegion> regions = detectWheelRegions(face, mask, 4);
                if (regions.size() != 4) continue;

                Vector3f normal = new Vector3f(nx, ny, nz);
                float zOffset = nz >= 0.0F ? 0.0015F : -0.0015F;

                for (WheelRegion region : regions) {
                    emitRotatedRegion(
                            face,
                            region,
                            angle,
                            normal,
                            pose,
                            consumer,
                            packedLight,
                            packedOverlay,
                            false,
                            zOffset);
                }

                emittedFaces++;
                logOnce(
                        "gp7:chunk:" + chunk + ":face:" + q,
                        "[TC alpha-wheel] GP7 authored wheel face chunk="
                                + chunk + " wheels=4");
            }
        }

        if (emittedFaces == 0) {
            logOnce(
                    "gp7:none",
                    "[TC alpha-wheel] GP7: no 4-wheel authored alpha face "
                            + "resolved in chunks 13/16/17; static mesh preserved.");
        }
    }

    private static int preparePart(
            ModelPart root,
            String name,
            AlphaMask mask,
            int expected) {

        ModelPart part = directChild(root, name);
        if (part == null) {
            logOnce(
                    "missing:" + name,
                    "[TC alpha-wheel] target part missing: " + name);
            return 0;
        }

        final boolean[] proven = {false};
        PoseStack probeStack = new PoseStack();

        part.visit(probeStack, (pose, path, cubeIndex, cube) -> {
            if (proven[0]) return;

            try {
                Object[] polygons = polygons(cube);
                if (polygons == null) return;

                for (Object polygon : polygons) {
                    Vector3f normal = polygonNormal(polygon);
                    if (normal == null || Math.abs(normal.z()) < 0.90F) continue;

                    List<V> face = faceVertices(polygon);
                    if (face == null) continue;

                    List<WheelRegion> regions =
                            detectWheelRegions(face, mask, expected);

                    if (regions.size() == expected) {
                        proven[0] = true;
                        return;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fail safe: leave the original panel visible.
            }
        });

        if (!proven[0]) {
            logOnce(
                    "unproven:" + name,
                    "[TC alpha-wheel] authored wheel panel not suppressed; "
                            + "alpha detection failed: " + name);
            return 0;
        }

        part.skipDraw = true;
        logOnce(
                "proven:" + name,
                "[TC alpha-wheel] authored wheel panel proven: "
                        + name + " wheels=" + expected);
        return 1;
    }

    // TC_DEV38_R1G1Z7Y7_NATIVE_OVERLAY_ONLY
    // Original panel remains fully visible/static. Only the detected circular
    // wheel artwork is redrawn slightly toward its face normal and rotated.
    private static void renderCubeWheelOverlayOnly(
            String id,
            String leaf,
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            AlphaMask mask,
            int expected,
            float angle) {

        try {
            Object[] polygons = polygons(cube);
            if (polygons == null) return;

            for (Object polygon : polygons) {
                Vector3f normal = polygonNormal(polygon);
                if (normal == null || Math.abs(normal.z()) < 0.90F) continue;

                List<V> face = faceVertices(polygon);
                if (face == null) continue;

                List<WheelRegion> regions =
                        detectWheelRegions(face, mask, expected);
                if (regions.size() != expected) continue;

                float zOffset = normal.z() >= 0.0F
                        ? NATIVE_OVERLAY_OFFSET_PIXELS
                        : -NATIVE_OVERLAY_OFFSET_PIXELS;

                for (WheelRegion region : regions) {
                    emitRotatedRegion(
                            face,
                            region,
                            angle,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            true,
                            zOffset);
                }

                logOnce(
                        id + ":" + leaf + ":full-overlay:"
                                + Math.round(minU(face) * mask.width)
                                + ":" + Math.round(minV(face) * mask.height),
                        "[TC alpha-wheel] full wheel overlay "
                                + id + " " + leaf
                                + " wheels=" + expected
                                + " static-base=yes");
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail safe: original static panel was never suppressed.
        }
    }
    private static void renderCubeWheelRegions(
            String id,
            String leaf,
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            AlphaMask mask,
            int expected,
            float angle,
            boolean pixelCoordinates,
            float zOffset) {

        try {
            Object[] polygons = polygons(cube);
            if (polygons == null) return;

            for (Object polygon : polygons) {
                Vector3f normal = polygonNormal(polygon);
                List<V> face = faceVertices(polygon);
                if (normal == null || face == null) continue;

                // The original ModelPart was suppressed only after alpha proof.
                // Re-emit every non-wheel face exactly static so thickness/end
                // geometry never disappears.
                if (Math.abs(normal.z()) < 0.90F) {
                    emitOriginalFace(
                            face,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            0.0F);
                    continue;
                }

                List<WheelRegion> regions =
                        detectWheelRegions(face, mask, expected);

                if (regions.size() != expected) {
                    // A second/opposite face may use a different UV layout.
                    // Preserve it exactly static instead of guessing.
                    emitOriginalFace(
                            face,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            0.0F);
                    continue;
                }

                if ("locomotive_diesel_chme3".equals(id)) {
                    // ChME3 is runtime accepted and must not change.
                    emitSplitWheelFace(
                            face,
                            regions,
                            angle,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            zOffset);
                } else if (isCompleteComponentWheelTarget(id)) {
                    // TC_DEV38_R1G1Z7Y10_COMPLETE_COMPONENT_QUADS
                    // V60 + USSR only: rotate each complete authored wheel
                    // component as one quad around its own component center.
                    emitCompleteComponentWheelFace(
                            face,
                            regions,
                            angle,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            zOffset);
                } else {
                    // BR01 is wheel PASS on r1g1z7y9. Preserve its exact path.
                    // GP7 does not use this native branch.
                    emitTexelMaskedWheelFace(
                            face,
                            regions,
                            angle,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            zOffset,
                            mask);
                }

                logOnce(
                        id + ":" + leaf + ":circle-render:"
                                + Math.round(minU(face) * mask.width)
                                + ":" + Math.round(minV(face) * mask.height),
                        "[TC alpha-wheel] circular split "
                                + id + " " + leaf
                                + " wheels=" + expected
                                + " static-remainder=yes");
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail safe: source panel was only suppressed after proof, and every
            // successfully visited polygon is reconstructed above.
        }
    }

    /**
     * Detect expected wheel islands from the exact texture pixels addressed by
     * this authored face.
     *
     * Sampling is performed in GEOMETRY order: each grid sample starts from a
     * face X/Y coordinate and uses the face's original UV interpolation to find
     * its PNG alpha.  UV flips therefore require no special case.
     */
    private static boolean isCompleteComponentWheelTarget(String id) {
        return "locomotive_diesel_v60".equals(id)
                || "locomotive_steam_ussr".equals(id);
    }

    // TC_DEV38_R1G1Z7Y10_COMPONENT_FLOOD_FILL
    // Work on the exact source-pixel grid addressed by a single authored face.
    // A wheel is selected as one connected, near-square opaque alpha island.
    private static List<WheelRegion> detectCompleteWheelComponents(
            List<V> face,
            AlphaMask mask,
            int expected) {

        if (face == null || face.size() != 4 || mask == null || expected <= 0) {
            return List.of();
        }

        int[] grid = sourceTexelGrid(face, mask);
        if (grid == null) return List.of();

        int gridW = grid[0];
        int gridH = grid[1];

        float faceMinX = minX(face);
        float faceMaxX = maxX(face);
        float faceMinY = minY(face);
        float faceMaxY = maxY(face);
        float faceW = faceMaxX - faceMinX;
        float faceH = faceMaxY - faceMinY;

        if (faceW <= GEOMETRY_EPSILON || faceH <= GEOMETRY_EPSILON) {
            return List.of();
        }

        boolean[][] opaque = new boolean[gridH][gridW];
        boolean[][] visited = new boolean[gridH][gridW];

        for (int gy = 0; gy < gridH; gy++) {
            float y = faceMinY + ((gy + 0.5F) / gridH) * faceH;

            for (int gx = 0; gx < gridW; gx++) {
                float x = faceMinX + ((gx + 0.5F) / gridW) * faceW;
                V uv = sampleFaceUvXY(face, x, y);

                if (uv != null && mask.opaque(uv.u, uv.v)) {
                    opaque[gy][gx] = true;
                }
            }
        }

        // int[] candidate layout:
        // [0]=score [1]=minX [2]=maxX [3]=minY [4]=maxY [5]=area
        List<int[]> candidates = new ArrayList<>();
        int capacity = gridW * gridH;
        int[] stackX = new int[capacity];
        int[] stackY = new int[capacity];

        for (int seedY = 0; seedY < gridH; seedY++) {
            for (int seedX = 0; seedX < gridW; seedX++) {
                if (!opaque[seedY][seedX] || visited[seedY][seedX]) continue;

                int top = 0;
                stackX[top] = seedX;
                stackY[top] = seedY;
                top++;
                visited[seedY][seedX] = true;

                int area = 0;
                int bx0 = seedX;
                int bx1 = seedX;
                int by0 = seedY;
                int by1 = seedY;

                while (top > 0) {
                    top--;
                    int x = stackX[top];
                    int y = stackY[top];

                    area++;
                    bx0 = Math.min(bx0, x);
                    bx1 = Math.max(bx1, x);
                    by0 = Math.min(by0, y);
                    by1 = Math.max(by1, y);

                    if (x > 0
                            && opaque[y][x - 1]
                            && !visited[y][x - 1]) {
                        visited[y][x - 1] = true;
                        stackX[top] = x - 1;
                        stackY[top] = y;
                        top++;
                    }
                    if (x + 1 < gridW
                            && opaque[y][x + 1]
                            && !visited[y][x + 1]) {
                        visited[y][x + 1] = true;
                        stackX[top] = x + 1;
                        stackY[top] = y;
                        top++;
                    }
                    if (y > 0
                            && opaque[y - 1][x]
                            && !visited[y - 1][x]) {
                        visited[y - 1][x] = true;
                        stackX[top] = x;
                        stackY[top] = y - 1;
                        top++;
                    }
                    if (y + 1 < gridH
                            && opaque[y + 1][x]
                            && !visited[y + 1][x]) {
                        visited[y + 1][x] = true;
                        stackX[top] = x;
                        stackY[top] = y + 1;
                        top++;
                    }
                }

                int boxW = bx1 - bx0 + 1;
                int boxH = by1 - by0 + 1;

                // V60's smaller wheel is still a true square component, while
                // the nearby linkage marks are narrow/small. USSR rod marks
                // are also thin and are rejected here.
                if (boxW < 3 || boxH < 3) continue;

                float aspect = boxW / (float) boxH;
                if (aspect < 0.55F || aspect > 1.80F) continue;

                float fill = area / (float) (boxW * boxH);
                if (fill < 0.22F) continue;

                int squarePenalty = Math.abs(boxW - boxH);
                int score = area * 100
                        + Math.min(boxW, boxH) * 25
                        - squarePenalty * 20;

                candidates.add(new int[]{
                        score, bx0, bx1, by0, by1, area
                });
            }
        }

        if (candidates.size() < expected) {
            logOnce(
                    activeId() + ":components-short",
                    "[TC component-wheel] "
                            + activeId()
                            + " complete components="
                            + candidates.size()
                            + " expected="
                            + expected);
            return List.of();
        }

        candidates.sort((a, b) -> Integer.compare(b[0], a[0]));

        List<WheelRegion> regions = new ArrayList<>(expected);

        for (int i = 0; i < expected; i++) {
            int[] component = candidates.get(i);

            float rx0 = faceMinX
                    + (component[1] / (float) gridW) * faceW;
            float rx1 = faceMinX
                    + ((component[2] + 1) / (float) gridW) * faceW;
            float ry0 = faceMinY
                    + (component[3] / (float) gridH) * faceH;
            float ry1 = faceMinY
                    + ((component[4] + 1) / (float) gridH) * faceH;

            float cx = (rx0 + rx1) * 0.5F;
            float cy = (ry0 + ry1) * 0.5F;

            regions.add(new WheelRegion(
                    rx0, rx1,
                    ry0, ry1,
                    cx, cy));
        }

        regions.sort(Comparator.comparingDouble(region -> region.centerX));

        logOnce(
                activeId() + ":components-ok:" + expected,
                "[TC component-wheel] "
                        + activeId()
                        + " resolved complete wheel components="
                        + regions.size());

        return regions;
    }

    // TC_DEV38_R1G1Z7Y10_COMPLETE_COMPONENT_RENDER
    // The source panel has already been safely suppressed by prepareNative().
    // Rebuild all static areas around each wheel rectangle, then rotate the
    // complete wheel rectangle around its own exact component center.
    private static void emitCompleteComponentWheelFace(
            List<V> source,
            List<WheelRegion> regions,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        if (source == null
                || source.size() != 4
                || regions == null
                || regions.isEmpty()) {
            return;
        }

        List<WheelRegion> ordered = new ArrayList<>(regions);
        ordered.sort(Comparator.comparingDouble(region -> region.centerX));

        float faceMinX = minX(source);
        float faceMaxX = maxX(source);
        float faceMinY = minY(source);
        float faceMaxY = maxY(source);

        float cursorX = faceMinX;

        for (WheelRegion region : ordered) {
            float rx0 = Math.max(faceMinX, region.minX);
            float rx1 = Math.min(faceMaxX, region.maxX);
            float ry0 = Math.max(faceMinY, region.minY);
            float ry1 = Math.min(faceMaxY, region.maxY);

            if (rx1 <= rx0 + GEOMETRY_EPSILON
                    || ry1 <= ry0 + GEOMETRY_EPSILON) {
                continue;
            }

            // Full-height static material before this wheel component.
            if (rx0 > cursorX + GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        cursorX, faceMinY,
                        rx0, faceMinY,
                        rx0, faceMaxY,
                        cursorX, faceMaxY,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
            }

            // Static authored pixels below the wheel's UV component rectangle.
            if (ry0 > faceMinY + GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        rx0, faceMinY,
                        rx1, faceMinY,
                        rx1, ry0,
                        rx0, ry0,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
            }

            // One complete rotating wheel component. Transparent pixels inside
            // the component's bounding rectangle stay transparent naturally.
            emitFaceBand(
                    source,
                    rx0, ry0,
                    rx1, ry0,
                    rx1, ry1,
                    rx0, ry1,
                    true,
                    region.centerX,
                    region.centerY,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates,
                    zOffset);

            // Static authored pixels above the wheel's UV component rectangle.
            if (ry1 < faceMaxY - GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        rx0, ry1,
                        rx1, ry1,
                        rx1, faceMaxY,
                        rx0, faceMaxY,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
            }

            cursorX = Math.max(cursorX, rx1);
        }

        // Static tail after the last wheel component.
        if (cursorX < faceMaxX - GEOMETRY_EPSILON) {
            emitFaceBand(
                    source,
                    cursorX, faceMinY,
                    faceMaxX, faceMinY,
                    faceMaxX, faceMaxY,
                    cursorX, faceMaxY,
                    false,
                    0.0F,
                    0.0F,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates);
        }
    }
    private static List<WheelRegion> detectWheelRegions(
            List<V> face,
            AlphaMask mask,
            int expected) {

        if (face == null || face.size() != 4 || mask == null || expected <= 0) {
            return List.of();
        }

        // TC_DEV38_R1G1Z7Y10_COMPLETE_COMPONENT_DETECTION
        // V60 and USSR UVs contain discrete complete wheel islands.  Do not
        // derive these wheels from column bands or texel-circle slices.
        if (isCompleteComponentWheelTarget(activeId())) {
            return detectCompleteWheelComponents(face, mask, expected);
        }

        float minX = minX(face);
        float maxX = maxX(face);
        float minY = minY(face);
        float maxY = maxY(face);
        float width = maxX - minX;
        float height = maxY - minY;

        if (width <= 0.0001F || height <= 0.0001F) return List.of();

        float midX = (minX + maxX) * 0.5F;
        float midY = (minY + maxY) * 0.5F;

        V ux0 = sampleFaceUvXY(face, minX, midY);
        V ux1 = sampleFaceUvXY(face, maxX, midY);
        V vy0 = sampleFaceUvXY(face, midX, minY);
        V vy1 = sampleFaceUvXY(face, midX, maxY);

        if (ux0 == null || ux1 == null || vy0 == null || vy1 == null) {
            return List.of();
        }

        int gridW = Math.max(
                4,
                Math.round((float) Math.hypot(
                        (ux1.u - ux0.u) * mask.width,
                        (ux1.v - ux0.v) * mask.height)));

        int gridH = Math.max(
                4,
                Math.round((float) Math.hypot(
                        (vy1.u - vy0.u) * mask.width,
                        (vy1.v - vy0.v) * mask.height)));

        if (gridW < expected * 3 || gridH < 4 || gridW > 256 || gridH > 128) {
            return List.of();
        }

        boolean[][] opaque = new boolean[gridH][gridW];
        int[] columns = new int[gridW];

        for (int gy = 0; gy < gridH; gy++) {
            float y = minY + ((gy + 0.5F) / gridH) * height;

            for (int gx = 0; gx < gridW; gx++) {
                float x = minX + ((gx + 0.5F) / gridW) * width;
                V uv = sampleFaceUvXY(face, x, y);
                if (uv == null) continue;

                if (mask.opaque(uv.u, uv.v)) {
                    opaque[gy][gx] = true;
                    columns[gx]++;
                }
            }
        }

        int[] score = smooth(columns);
        int[] centers = chooseCenters(score, expected);
        if (centers.length != expected) return List.of();

        List<WheelRegion> regions = new ArrayList<>(expected);

        for (int i = 0; i < centers.length; i++) {
            int left = i == 0
                    ? 0
                    : (centers[i - 1] + centers[i]) / 2;
            int right = i == centers.length - 1
                    ? gridW - 1
                    : (centers[i] + centers[i + 1]) / 2;

            int bx0 = gridW;
            int bx1 = -1;
            int by0 = gridH;
            int by1 = -1;

            for (int gy = 0; gy < gridH; gy++) {
                for (int gx = left; gx <= right; gx++) {
                    if (!opaque[gy][gx]) continue;
                    bx0 = Math.min(bx0, gx);
                    bx1 = Math.max(bx1, gx);
                    by0 = Math.min(by0, gy);
                    by1 = Math.max(by1, gy);
                }
            }

            if (bx1 < bx0 || by1 < by0) return List.of();

            int pixelW = bx1 - bx0 + 1;
            int pixelH = by1 - by0 + 1;

            // A real wheel island must occupy a meaningful fraction of the
            // authored wheel-row height.  Small leading/trailing drivers on
            // BR01 still clear the deliberately low 0.28 threshold.
            if (pixelW < 2
                    || pixelH < 2
                    || pixelH < Math.max(2, Math.round(gridH * 0.28F))) {
                return List.of();
            }

            // Pad by a quarter source pixel so edge alpha is not clipped while
            // staying well inside the midpoint boundary between wheel centers.
            float padX = 0.25F;
            float padY = 0.25F;

            float rx0 = minX
                    + (Math.max(left, bx0 - padX) / gridW) * width;
            float rx1 = minX
                    + (Math.min(right + 1.0F, bx1 + 1.0F + padX) / gridW) * width;
            float ry0 = minY
                    + (Math.max(0.0F, by0 - padY) / gridH) * height;
            float ry1 = minY
                    + (Math.min(gridH, by1 + 1.0F + padY) / gridH) * height;

            float cx = (rx0 + rx1) * 0.5F;
            float cy = (ry0 + ry1) * 0.5F;

            regions.add(new WheelRegion(rx0, rx1, ry0, ry1, cx, cy));
        }

        return regions;
    }

    private static int[] smooth(int[] columns) {
        int[] out = new int[columns.length];

        for (int x = 0; x < columns.length; x++) {
            int score = columns[x] * 3;
            if (x > 0) score += columns[x - 1] * 2;
            if (x + 1 < columns.length) score += columns[x + 1] * 2;
            if (x > 1) score += columns[x - 2];
            if (x + 2 < columns.length) score += columns[x + 2];
            out[x] = score;
        }

        return out;
    }

    private static int[] chooseCenters(int[] score, int expected) {
        if (score == null || score.length == 0 || expected <= 0) return new int[0];

        int[] working = score.clone();
        int[] selected = new int[expected];
        Arrays.fill(selected, -1);

        int minSeparation = Math.max(
                2,
                Math.round((score.length / (float) expected) * 0.62F));

        for (int n = 0; n < expected; n++) {
            int best = -1;
            int bestScore = 0;

            for (int x = 0; x < working.length; x++) {
                if (working[x] > bestScore) {
                    bestScore = working[x];
                    best = x;
                }
            }

            if (best < 0 || bestScore <= 0) return new int[0];

            selected[n] = best;

            int from = Math.max(0, best - minSeparation);
            int to = Math.min(working.length - 1, best + minSeparation);
            for (int x = from; x <= to; x++) working[x] = -1;
        }

        Arrays.sort(selected);

        // Refuse collapsed/duplicate selections.
        for (int i = 1; i < selected.length; i++) {
            if (selected[i] <= selected[i - 1]) return new int[0];
        }

        return selected;
    }

    private static void emitRotatedRegion(
            List<V> source,
            WheelRegion region,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        // TC_DEV38_R1G1Z7Y8_GP7_WHOLE_TEXEL_OVERLAY
        // Exact GP7 base mesh remains frozen/static. Overlay only whole source
        // texels belonging to this alpha-proven wheel; no geometric circle
        // boundary is allowed to slice a source pixel.
        AlphaMask mask = mask("locomotive_diesel_gp7");
        if (mask == null) return;

        emitTexelMaskedWheelRegion(
                source,
                region,
                angle,
                normal,
                pose,
                consumer,
                light,
                overlay,
                pixelCoordinates,
                zOffset,
                mask);
    }

    // TC_DEV38_R1G1Z7Y8_TEXEL_MASK_RENDERER
    private static void emitTexelMaskedWheelFace(
            List<V> source,
            List<WheelRegion> regions,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset,
            AlphaMask mask) {

        if (source == null
                || source.size() != 4
                || regions == null
                || regions.isEmpty()
                || mask == null) {
            return;
        }

        int[] grid = sourceTexelGrid(source, mask);
        if (grid == null) {
            emitOriginalFace(
                    source,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates,
                    0.0F);
            return;
        }

        int gridW = grid[0];
        int gridH = grid[1];
        List<Circle> circles = texelCircles(source, regions, gridW, gridH);
        if (circles.size() != regions.size()) {
            emitOriginalFace(
                    source,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates,
                    0.0F);
            return;
        }

        float faceMinX = minX(source);
        float faceMaxX = maxX(source);
        float faceMinY = minY(source);
        float faceMaxY = maxY(source);
        float faceW = faceMaxX - faceMinX;
        float faceH = faceMaxY - faceMinY;

        for (int gy = 0; gy < gridH; gy++) {
            float y0 = faceMinY + (gy / (float) gridH) * faceH;
            float y1 = faceMinY + ((gy + 1) / (float) gridH) * faceH;
            float ym = (y0 + y1) * 0.5F;

            int runStart = 0;
            int runOwner = texelWheelOwner(
                    circles,
                    faceMinX + (0.5F / gridW) * faceW,
                    ym);

            for (int gx = 1; gx <= gridW; gx++) {
                int owner = gx < gridW
                        ? texelWheelOwner(
                                circles,
                                faceMinX + ((gx + 0.5F) / gridW) * faceW,
                                ym)
                        : Integer.MIN_VALUE;

                if (owner == runOwner) continue;

                float x0 = faceMinX + (runStart / (float) gridW) * faceW;
                float x1 = faceMinX + (gx / (float) gridW) * faceW;

                if (runOwner >= 0) {
                    Circle circle = circles.get(runOwner);
                    emitFaceBand(
                            source,
                            x0, y0,
                            x1, y0,
                            x1, y1,
                            x0, y1,
                            true,
                            circle.centerX,
                            circle.centerY,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            zOffset);
                } else {
                    emitFaceBand(
                            source,
                            x0, y0,
                            x1, y0,
                            x1, y1,
                            x0, y1,
                            false,
                            0.0F,
                            0.0F,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates);
                }

                runStart = gx;
                runOwner = owner;
            }
        }
    }

    private static void emitTexelMaskedWheelRegion(
            List<V> source,
            WheelRegion region,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset,
            AlphaMask mask) {

        if (source == null
                || source.size() != 4
                || region == null
                || mask == null) {
            return;
        }

        int[] grid = sourceTexelGrid(source, mask);
        if (grid == null) return;

        int gridW = grid[0];
        int gridH = grid[1];
        Circle circle = texelCircleFor(source, region, gridW, gridH, null);
        if (circle == null) return;

        float faceMinX = minX(source);
        float faceMaxX = maxX(source);
        float faceMinY = minY(source);
        float faceMaxY = maxY(source);
        float faceW = faceMaxX - faceMinX;
        float faceH = faceMaxY - faceMinY;

        for (int gy = 0; gy < gridH; gy++) {
            float y0 = faceMinY + (gy / (float) gridH) * faceH;
            float y1 = faceMinY + ((gy + 1) / (float) gridH) * faceH;
            float ym = (y0 + y1) * 0.5F;

            int runStart = -1;

            for (int gx = 0; gx <= gridW; gx++) {
                boolean inside = false;

                if (gx < gridW) {
                    float xm = faceMinX + ((gx + 0.5F) / gridW) * faceW;
                    inside = insideTexelCircle(circle, xm, ym);
                }

                if (inside && runStart < 0) {
                    runStart = gx;
                    continue;
                }

                if ((!inside || gx == gridW) && runStart >= 0) {
                    float x0 = faceMinX + (runStart / (float) gridW) * faceW;
                    float x1 = faceMinX + (gx / (float) gridW) * faceW;

                    emitFaceBand(
                            source,
                            x0, y0,
                            x1, y0,
                            x1, y1,
                            x0, y1,
                            true,
                            circle.centerX,
                            circle.centerY,
                            normal,
                            pose,
                            consumer,
                            light,
                            overlay,
                            pixelCoordinates,
                            zOffset);

                    runStart = -1;
                }
            }
        }
    }

    private static int[] sourceTexelGrid(
            List<V> face,
            AlphaMask mask) {

        if (face == null || face.size() != 4 || mask == null) return null;

        float minX = minX(face);
        float maxX = maxX(face);
        float minY = minY(face);
        float maxY = maxY(face);
        float midX = (minX + maxX) * 0.5F;
        float midY = (minY + maxY) * 0.5F;

        V ux0 = sampleFaceUvXY(face, minX, midY);
        V ux1 = sampleFaceUvXY(face, maxX, midY);
        V vy0 = sampleFaceUvXY(face, midX, minY);
        V vy1 = sampleFaceUvXY(face, midX, maxY);

        if (ux0 == null || ux1 == null || vy0 == null || vy1 == null) {
            return null;
        }

        int gridW = Math.max(
                1,
                Math.round((float) Math.hypot(
                        (ux1.u - ux0.u) * mask.width,
                        (ux1.v - ux0.v) * mask.height)));

        int gridH = Math.max(
                1,
                Math.round((float) Math.hypot(
                        (vy1.u - vy0.u) * mask.width,
                        (vy1.v - vy0.v) * mask.height)));

        if (gridW < 2 || gridH < 2 || gridW > 256 || gridH > 128) {
            return null;
        }

        return new int[]{gridW, gridH};
    }

    private static List<Circle> texelCircles(
            List<V> source,
            List<WheelRegion> regions,
            int gridW,
            int gridH) {

        List<Circle> circles = new ArrayList<>(regions.size());
        String id = activeId();

        Float sharedCenterY = null;

        if ("locomotive_diesel_v60".equals(id)
                || "locomotive_steam_ussr".equals(id)) {

            // TC_DEV38_R1G1Z7Y9_SHARED_AXLE_ROW
            // V60's four equal wheels and USSR's five equal drivers share one
            // axle row. Use the median detected wheel Y so a single alpha/rod
            // outlier cannot phase one wheel into the body or make the row orbit.
            List<Float> ys = new ArrayList<>(regions.size());
            for (WheelRegion region : regions) {
                ys.add(region.centerY);
            }
            ys.sort(Float::compare);

            int n = ys.size();
            if (n > 0) {
                sharedCenterY = (n & 1) == 1
                        ? ys.get(n / 2)
                        : (ys.get(n / 2 - 1) + ys.get(n / 2)) * 0.5F;
            }
        }

        for (WheelRegion region : regions) {
            Circle circle = texelCircleFor(
                    source,
                    region,
                    gridW,
                    gridH,
                    sharedCenterY);
            if (circle != null) circles.add(circle);
        }

        circles.sort(Comparator.comparingDouble(circle -> circle.centerX));
        return circles;
    }

    private static Circle texelCircleFor(
            List<V> source,
            WheelRegion region,
            int gridW,
            int gridH,
            Float sharedCenterY) {

        if (source == null
                || source.size() != 4
                || region == null
                || gridW <= 0
                || gridH <= 0) {
            return null;
        }

        float faceMinX = minX(source);
        float faceMaxX = maxX(source);
        float faceMinY = minY(source);
        float faceMaxY = maxY(source);
        float faceW = faceMaxX - faceMinX;
        float faceH = faceMaxY - faceMinY;

        float regionW = Math.max(0.0F, region.maxX - region.minX);
        float regionH = Math.max(0.0F, region.maxY - region.minY);
        float diameter = Math.min(regionW, regionH);

        if (diameter <= GEOMETRY_EPSILON) return null;

        float texelW = faceW / gridW;
        float texelH = faceH / gridH;

        // The old smooth clip could slice straight through a source pixel.
        // Selecting whole texels and adding half the texel diagonal guarantees
        // that an authored circumference pixel is either entirely in or out.
        float halfTexelDiagonal =
                0.5F * (float) Math.hypot(texelW, texelH);

        float radius = diameter * 0.5F + halfTexelDiagonal;
        String id = activeId();
        float faceMidY = (faceMinY + faceMaxY) * 0.5F;

        final float centerY;

        if ("locomotive_diesel_v60".equals(id)
                || "locomotive_steam_ussr".equals(id)) {

            // TC_DEV38_R1G1Z7Y9_STABILIZED_SHARED_CENTER
            centerY = sharedCenterY != null
                    ? sharedCenterY.floatValue()
                    : region.centerY;

        } else if ("locomotive_steam_br01".equals(id)) {

            // TC_DEV38_R1G1Z7Y9_BR01_INDIVIDUAL_AXLES
            // Small leading/trailing wheels must rotate around their own
            // detected wheel centers rather than the large-driver centerline.
            centerY = region.centerY;

        } else {

            // GP7 is WHEEL PASS / DONE and ChME3 does not use this texel path.
            // Preserve the exact y8 midpoint policy for all other callers.
            centerY = faceMidY;
            float maxUsefulRadius = faceH * 0.5F + halfTexelDiagonal;
            radius = Math.min(radius, maxUsefulRadius);
        }

        if (radius <= GEOMETRY_EPSILON) return null;

        return new Circle(region.centerX, centerY, radius);
    }

    private static int texelWheelOwner(
            List<Circle> circles,
            float x,
            float y) {

        int owner = -1;
        float bestDistance = Float.POSITIVE_INFINITY;

        for (int i = 0; i < circles.size(); i++) {
            Circle circle = circles.get(i);
            float dx = x - circle.centerX;
            float dy = y - circle.centerY;
            float d2 = dx * dx + dy * dy;
            float r2 = circle.radius * circle.radius;

            if (d2 <= r2 && d2 < bestDistance) {
                owner = i;
                bestDistance = d2;
            }
        }

        return owner;
    }

    private static boolean insideTexelCircle(
            Circle circle,
            float x,
            float y) {

        float dx = x - circle.centerX;
        float dy = y - circle.centerY;
        return dx * dx + dy * dy
                <= circle.radius * circle.radius;
    }
    private static void emitSplitWheelFace(
            List<V> source,
            List<WheelRegion> regions,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        if (source == null || source.size() != 4 || regions == null) return;

        List<Circle> circles = new ArrayList<>();
        for (WheelRegion region : regions) {
            Circle circle = circleFor(source, region);
            if (circle != null) circles.add(circle);
        }

        if (circles.isEmpty()) {
            emitOriginalFace(
                    source,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates,
                    0.0F);
            return;
        }

        circles.sort(Comparator.comparingDouble(circle -> circle.centerX));

        float faceMinX = minX(source);
        float faceMaxX = maxX(source);
        float faceMinY = minY(source);
        float faceMaxY = maxY(source);

        List<Float> cuts = new ArrayList<>();
        cuts.add(faceMinX);
        cuts.add(faceMaxX);

        for (Circle circle : circles) {
            float left = Math.max(faceMinX, circle.centerX - circle.radius);
            float right = Math.min(faceMaxX, circle.centerX + circle.radius);

            for (int i = 0; i <= wheelSlices(); i++) {
                float t = i / (float) wheelSlices();
                cuts.add(left + (right - left) * t);
            }
        }

        cuts.sort(Float::compare);

        List<Float> unique = new ArrayList<>();
        for (Float value : cuts) {
            if (unique.isEmpty()
                    || Math.abs(value - unique.get(unique.size() - 1))
                            > GEOMETRY_EPSILON) {
                unique.add(value);
            }
        }

        for (int i = 0; i + 1 < unique.size(); i++) {
            float x0 = unique.get(i);
            float x1 = unique.get(i + 1);

            if (x1 - x0 <= GEOMETRY_EPSILON) continue;

            float mid = (x0 + x1) * 0.5F;
            Circle circle = containingCircle(circles, mid);

            if (circle == null) {
                emitFaceBand(
                        source,
                        x0, faceMinY,
                        x1, faceMinY,
                        x1, faceMaxY,
                        x0, faceMaxY,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
                continue;
            }

            float span0 = circleSpan(circle, x0);
            float span1 = circleSpan(circle, x1);

            // If this is the exact tangent edge, leave the whole sliver static.
            if (span0 <= GEOMETRY_EPSILON && span1 <= GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        x0, faceMinY,
                        x1, faceMinY,
                        x1, faceMaxY,
                        x0, faceMaxY,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
                continue;
            }

            float low0 = Math.max(faceMinY, circle.centerY - span0);
            float low1 = Math.max(faceMinY, circle.centerY - span1);
            float high0 = Math.min(faceMaxY, circle.centerY + span0);
            float high1 = Math.min(faceMaxY, circle.centerY + span1);

            // Static pixels below the wheel circle.
            if (Math.max(low0, low1) > faceMinY + GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        x0, faceMinY,
                        x1, faceMinY,
                        x1, low1,
                        x0, low0,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
            }

            // Only this circular strip rotates.
            if (Math.max(high0 - low0, high1 - low1) > GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        x0, low0,
                        x1, low1,
                        x1, high1,
                        x0, high0,
                        true,
                        circle.centerX,
                        circle.centerY,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates,
                        zOffset);
            }

            // Static pixels above the wheel circle.
            if (Math.min(high0, high1) < faceMaxY - GEOMETRY_EPSILON) {
                emitFaceBand(
                        source,
                        x0, high0,
                        x1, high1,
                        x1, faceMaxY,
                        x0, faceMaxY,
                        false,
                        0.0F,
                        0.0F,
                        normal,
                        pose,
                        consumer,
                        light,
                        overlay,
                        pixelCoordinates);
            }
        }
    }

    private static void emitCircleOnly(
            List<V> source,
            Circle circle,
            float angle,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        float left = circle.centerX - circle.radius;
        float right = circle.centerX + circle.radius;

        for (int i = 0; i < wheelSlices(); i++) {
            float t0 = i / (float) wheelSlices();
            float t1 = (i + 1) / (float) wheelSlices();
            float x0 = left + (right - left) * t0;
            float x1 = left + (right - left) * t1;

            float span0 = circleSpan(circle, x0);
            float span1 = circleSpan(circle, x1);
            if (span0 <= GEOMETRY_EPSILON && span1 <= GEOMETRY_EPSILON) continue;

            emitFaceBand(
                    source,
                    x0, circle.centerY - span0,
                    x1, circle.centerY - span1,
                    x1, circle.centerY + span1,
                    x0, circle.centerY + span0,
                    true,
                    circle.centerX,
                    circle.centerY,
                    normal,
                    pose,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates,
                    zOffset);
        }
    }

    private static int wheelSlices() {
        return "locomotive_diesel_chme3".equals(activeId())
                ? CHME3_DONE_WHEEL_SLICES
                : REMAINING_WHEEL_SLICES;
    }

    private static float wheelRadiusScale() {
        return "locomotive_diesel_chme3".equals(activeId())
                ? CHME3_DONE_RADIUS_SCALE
                : REMAINING_WHEEL_RADIUS_SCALE;
    }

    private static float wheelDiameter(float width, float height) {
        if ("locomotive_diesel_chme3".equals(activeId())) {
            // Accepted ChME3 math remains byte-equivalent in behavior.
            return Math.min(width, height);
        }

        // The remaining-wheel alpha boxes can be slightly narrower in X when
        // neighboring packed artwork meets the wheel edge. Averaging X/Y
        // restores the full circular face, while the existing vertical face
        // clamp below still prevents escape from the authored panel.
        return (width + height) * 0.5F;
    }
    private static Circle circleFor(List<V> source, WheelRegion region) {
        if (source == null || source.size() != 4 || region == null) return null;

        float width = Math.max(0.0F, region.maxX - region.minX);
        float height = Math.max(0.0F, region.maxY - region.minY);
        float diameter = wheelDiameter(width, height);

        if (diameter <= GEOMETRY_EPSILON) return null;

        float faceMidY = (minY(source) + maxY(source)) * 0.5F;
        float radius = diameter * wheelRadiusScale();

        // Clamp to the authored face so the reconstructed static remainder and
        // rotating wheel sector cannot leave the original panel.
        float maxRadiusY = Math.min(
                faceMidY - minY(source),
                maxY(source) - faceMidY);
        radius = Math.min(radius, maxRadiusY);

        if (radius <= GEOMETRY_EPSILON) return null;
        return new Circle(region.centerX, faceMidY, radius);
    }

    private static Circle containingCircle(List<Circle> circles, float x) {
        Circle best = null;
        float bestDistance = Float.POSITIVE_INFINITY;

        for (Circle circle : circles) {
            float distance = Math.abs(x - circle.centerX);
            if (distance <= circle.radius + GEOMETRY_EPSILON
                    && distance < bestDistance) {
                best = circle;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static float circleSpan(Circle circle, float x) {
        float dx = x - circle.centerX;
        float inside = circle.radius * circle.radius - dx * dx;
        if (inside <= 0.0F) return 0.0F;
        return (float) Math.sqrt(inside);
    }

    private static void emitOriginalFace(
            List<V> face,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        if (face == null || face.size() != 4) return;

        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        for (V vertex : face) {
            emitVertex(
                    vertex,
                    false,
                    0.0F,
                    0.0F,
                    0.0F,
                    normal,
                    matrix,
                    normalMatrix,
                    consumer,
                    light,
                    overlay,
                    pixelCoordinates);
        }
    }

    private static void emitFaceBand(
            List<V> source,
            float x0, float y0,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            boolean rotate,
            float centerX,
            float centerY,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates) {

        emitFaceBand(
                source,
                x0, y0,
                x1, y1,
                x2, y2,
                x3, y3,
                rotate,
                centerX,
                centerY,
                normal,
                pose,
                consumer,
                light,
                overlay,
                pixelCoordinates,
                0.0F);
    }

    private static void emitFaceBand(
            List<V> source,
            float x0, float y0,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            boolean rotate,
            float centerX,
            float centerY,
            Vector3f normal,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates,
            float zOffset) {

        V a = sampleFaceUvXY(source, x0, y0);
        V b = sampleFaceUvXY(source, x1, y1);
        V c = sampleFaceUvXY(source, x2, y2);
        V d = sampleFaceUvXY(source, x3, y3);

        if (a == null || b == null || c == null || d == null) return;

        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        // Preserve the source face's XY winding so culling behavior remains
        // identical to the original ModelPart polygon.
        boolean reverse = signedAreaXY(source) < 0.0F;

        if (!reverse) {
            emitVertex(a, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(b, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(c, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(d, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
        } else {
            emitVertex(d, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(c, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(b, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
            emitVertex(a, rotate, centerX, centerY, zOffset,
                    normal, matrix, normalMatrix, consumer,
                    light, overlay, pixelCoordinates);
        }
    }

    private static void emitVertex(
            V source,
            boolean rotate,
            float centerX,
            float centerY,
            float zOffset,
            Vector3f normal,
            Matrix4f matrix,
            Matrix3f normalMatrix,
            VertexConsumer consumer,
            int light,
            int overlay,
            boolean pixelCoordinates) {

        float x = source.x;
        float y = source.y;
        float z = source.z + zOffset;

        if (rotate) {
            float angle = phase();
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float dx = x - centerX;
            float dy = y - centerY;

            x = centerX + dx * cos - dy * sin;
            y = centerY + dx * sin + dy * cos;
        }

        if (pixelCoordinates) {
            x /= 16.0F;
            y /= 16.0F;
            z /= 16.0F;
        }

        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(source.u, source.v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normalMatrix, normal.x(), normal.y(), normal.z())
                .endVertex();
    }

    private static float signedAreaXY(List<V> face) {
        if (face == null || face.size() < 3) return 0.0F;

        float area = 0.0F;
        for (int i = 0; i < face.size(); i++) {
            V a = face.get(i);
            V b = face.get((i + 1) % face.size());
            area += a.x * b.y - b.x * a.y;
        }
        return area * 0.5F;
    }

    private static List<V> clipMinX(List<V> input, float limit) {
        return clip(input, 0, limit, true);
    }

    private static List<V> clipMaxX(List<V> input, float limit) {
        return clip(input, 0, limit, false);
    }

    private static List<V> clipMinY(List<V> input, float limit) {
        return clip(input, 1, limit, true);
    }

    private static List<V> clipMaxY(List<V> input, float limit) {
        return clip(input, 1, limit, false);
    }

    private static List<V> clip(
            List<V> input,
            int axis,
            float limit,
            boolean keepGreater) {

        List<V> output = new ArrayList<>();
        if (input == null || input.isEmpty()) return output;

        V previous = input.get(input.size() - 1);
        boolean previousInside = inside(previous, axis, limit, keepGreater);

        for (V current : input) {
            boolean currentInside = inside(current, axis, limit, keepGreater);

            if (currentInside != previousInside) {
                output.add(intersection(previous, current, axis, limit));
            }

            if (currentInside) output.add(current);

            previous = current;
            previousInside = currentInside;
        }

        return output;
    }

    private static boolean inside(
            V vertex,
            int axis,
            float limit,
            boolean keepGreater) {

        float value = axis == 0 ? vertex.x : vertex.y;
        return keepGreater ? value >= limit - 0.0001F : value <= limit + 0.0001F;
    }

    private static V intersection(V a, V b, int axis, float limit) {
        float av = axis == 0 ? a.x : a.y;
        float bv = axis == 0 ? b.x : b.y;
        float denominator = bv - av;
        float t = Math.abs(denominator) < 0.000001F
                ? 0.0F
                : (limit - av) / denominator;
        t = Math.max(0.0F, Math.min(1.0F, t));
        return lerp(a, b, t);
    }

    private static V lerp(V a, V b, float t) {
        return new V(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t,
                a.u + (b.u - a.u) * t,
                a.v + (b.v - a.v) * t);
    }

    private static AlphaMask mask(String id) {
        if (MASKS.containsKey(id)) return MASKS.get(id);
        if (MASK_FAILURES.contains(id)) return null;

        ResourceLocation texture = TEXTURES.get(id);
        if (texture == null) {
            MASK_FAILURES.add(id);
            return null;
        }

        try {
            Optional<Resource> found =
                    Minecraft.getInstance().getResourceManager().getResource(texture);
            if (found.isEmpty()) {
                MASK_FAILURES.add(id);
                logOnce(
                        "texture-missing:" + id,
                        "[TC alpha-wheel] texture missing: " + texture);
                return null;
            }

            try (InputStream input = found.get().open();
                 NativeImage image = NativeImage.read(input)) {

                int width = image.getWidth();
                int height = image.getHeight();
                byte[] alpha = new byte[width * height];

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        alpha[y * width + x] =
                                (byte) ((pixel >>> 24) & 0xFF);
                    }
                }

                AlphaMask mask = new AlphaMask(width, height, alpha);
                MASKS.put(id, mask);

                logOnce(
                        "texture-loaded:" + id,
                        "[TC alpha-wheel] loaded " + texture
                                + " " + width + "x" + height);
                return mask;
            }
        } catch (Exception ex) {
            MASK_FAILURES.add(id);
            logOnce(
                    "texture-error:" + id,
                    "[TC alpha-wheel] texture read failed " + texture
                            + ": " + ex.getClass().getSimpleName()
                            + " " + String.valueOf(ex.getMessage()));
            return null;
        }
    }

    private static boolean isStaticBaseOverlayTarget(String id) {
        return "locomotive_diesel_v60".equals(id)
                || "locomotive_steam_br01".equals(id)
                || "locomotive_steam_ussr".equals(id);
    }
    private static boolean isNativeTarget(String id) {
        return "locomotive_diesel_chme3".equals(id)
                || "locomotive_diesel_v60".equals(id)
                || "locomotive_steam_br01".equals(id)
                || "locomotive_steam_ussr".equals(id);
    }

    private static int expectedNativeWheels(String id, String leaf) {
        if ("locomotive_diesel_chme3".equals(id) && "box25".equals(leaf)) {
            return 4;
        }

        if ("locomotive_diesel_v60".equals(id)
                && ("box".equals(leaf) || "box1".equals(leaf))) {
            return 4;
        }

        if ("locomotive_steam_br01".equals(id)
                && ("part_049".equals(leaf) || "part_057".equals(leaf))) {
            return 5;
        }

        if ("locomotive_steam_ussr".equals(id)
                && ("part_018".equals(leaf) || "part_045".equals(leaf))) {
            return 5;
        }

        return 0;
    }

    private static String activeId() {
        Entity entity = CURRENT_ENTITY.get();
        if (entity == null) return "";

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "" : key.getPath();
    }

    private static float phase() {
        Float value = CURRENT_PHASE.get();
        return value == null ? 0.0F : value.floatValue();
    }

    private static ModelPart root(Object model) {
        if (model == null) return null;
        if (model instanceof ModelPart part) return part;

        Class<?> type = model.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) return part;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep searching exact ModelPart fields.
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private static ModelPart directChild(ModelPart root, String name) {
        if (root == null || name == null) return null;

        try {
            return root.getChild(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stringField(Object owner, String preferredName) {
        Object value = fieldValue(owner, preferredName, String.class);
        return value instanceof String string ? string : null;
    }

    private static float[] floatArrayField(Object owner, String preferredName) {
        Object value = fieldValue(owner, preferredName, float[].class);
        return value instanceof float[] array ? array : null;
    }

    private static Object fieldValue(
            Object owner,
            String preferredName,
            Class<?> wanted) {

        if (owner == null) return null;

        Class<?> type = owner.getClass();
        Field fallback = null;

        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != wanted) continue;

                try {
                    field.setAccessible(true);
                } catch (RuntimeException ignored) {
                    continue;
                }

                if (field.getName().equals(preferredName)) {
                    try {
                        return field.get(owner);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        return null;
                    }
                }

                if (fallback == null) fallback = field;
            }

            type = type.getSuperclass();
        }

        if (fallback != null) {
            try {
                return fallback.get(owner);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        return null;
    }

    private static List<V> faceVertices(Object polygon)
            throws ReflectiveOperationException {

        Object[] raw = polygonVertices(polygon);
        if (raw == null || raw.length != 4) return null;

        List<V> face = new ArrayList<>(4);

        for (Object vertex : raw) {
            Vector3f position = vertexPos(vertex);
            if (position == null) return null;

            face.add(new V(
                    position.x(),
                    position.y(),
                    position.z(),
                    vertexU(vertex),
                    vertexV(vertex)));
        }

        return face;
    }

    private static V sampleFaceUvXY(List<V> face, float x, float y) {
        if (face == null || face.size() != 4) return null;

        V base = face.get(0);
        V xEdge = null;
        V yEdge = null;

        for (int i = 1; i < face.size(); i++) {
            V candidate = face.get(i);

            if (xEdge == null
                    && Math.abs(candidate.y - base.y) < 0.001F
                    && Math.abs(candidate.x - base.x) > 0.001F) {
                xEdge = candidate;
            }

            if (yEdge == null
                    && Math.abs(candidate.x - base.x) < 0.001F
                    && Math.abs(candidate.y - base.y) > 0.001F) {
                yEdge = candidate;
            }
        }

        if (xEdge == null || yEdge == null) {
            for (V candidate : face) {
                V xe = null;
                V ye = null;

                for (V other : face) {
                    if (other == candidate) continue;

                    if (xe == null
                            && Math.abs(other.y - candidate.y) < 0.001F
                            && Math.abs(other.x - candidate.x) > 0.001F) {
                        xe = other;
                    }

                    if (ye == null
                            && Math.abs(other.x - candidate.x) < 0.001F
                            && Math.abs(other.y - candidate.y) > 0.001F) {
                        ye = other;
                    }
                }

                if (xe != null && ye != null) {
                    base = candidate;
                    xEdge = xe;
                    yEdge = ye;
                    break;
                }
            }
        }

        if (xEdge == null || yEdge == null) return null;

        float dx = xEdge.x - base.x;
        float dy = yEdge.y - base.y;

        if (Math.abs(dx) < 0.000001F || Math.abs(dy) < 0.000001F) {
            return null;
        }

        float fx = (x - base.x) / dx;
        float fy = (y - base.y) / dy;

        return new V(
                x,
                y,
                base.z,
                base.u
                        + fx * (xEdge.u - base.u)
                        + fy * (yEdge.u - base.u),
                base.v
                        + fx * (xEdge.v - base.v)
                        + fy * (yEdge.v - base.v));
    }

    private static Object[] polygons(ModelPart.Cube cube)
            throws ReflectiveOperationException {

        if (cube == null) return null;

        if (cachedCubeClass != cube.getClass()) {
            cachedCubeClass = cube.getClass();
            cubePolygonsField = findArrayField(cachedCubeClass, "polygons");
        }

        return cubePolygonsField == null
                ? null
                : (Object[]) cubePolygonsField.get(cube);
    }

    private static Object[] polygonVertices(Object polygon)
            throws ReflectiveOperationException {

        if (polygon == null) return null;

        if (cachedPolygonClass != polygon.getClass()) {
            cachedPolygonClass = polygon.getClass();
            polygonVerticesField =
                    findArrayField(cachedPolygonClass, "vertices");
            polygonNormalField =
                    findFieldByType(
                            cachedPolygonClass,
                            Vector3f.class,
                            "normal");
        }

        return polygonVerticesField == null
                ? null
                : (Object[]) polygonVerticesField.get(polygon);
    }

    private static Vector3f polygonNormal(Object polygon)
            throws ReflectiveOperationException {

        polygonVertices(polygon);
        return polygonNormalField == null
                ? null
                : (Vector3f) polygonNormalField.get(polygon);
    }

    private static Vector3f vertexPos(Object vertex)
            throws ReflectiveOperationException {

        cacheVertex(vertex);
        return vertexPosField == null
                ? null
                : (Vector3f) vertexPosField.get(vertex);
    }

    private static float vertexU(Object vertex)
            throws ReflectiveOperationException {

        cacheVertex(vertex);
        return vertexUField == null ? 0.0F : vertexUField.getFloat(vertex);
    }

    private static float vertexV(Object vertex)
            throws ReflectiveOperationException {

        cacheVertex(vertex);
        return vertexVField == null ? 0.0F : vertexVField.getFloat(vertex);
    }

    private static void cacheVertex(Object vertex) {
        if (vertex == null || cachedVertexClass == vertex.getClass()) return;

        cachedVertexClass = vertex.getClass();
        vertexPosField =
                findFieldByType(cachedVertexClass, Vector3f.class, "pos", "position");

        List<Field> floats = new ArrayList<>();

        for (Field field : cachedVertexClass.getDeclaredFields()) {
            if (field.getType() != float.class) continue;

            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
                continue;
            }

            floats.add(field);

            if ("u".equalsIgnoreCase(field.getName())) vertexUField = field;
            if ("v".equalsIgnoreCase(field.getName())) vertexVField = field;
        }

        if (vertexUField == null && floats.size() > 0) vertexUField = floats.get(0);
        if (vertexVField == null && floats.size() > 1) vertexVField = floats.get(1);
    }

    private static Field findArrayField(Class<?> type, String preferred) {
        Field fallback = null;

        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().isArray()) continue;

            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
                continue;
            }

            if (field.getName().equalsIgnoreCase(preferred)) return field;
            if (fallback == null) fallback = field;
        }

        return fallback;
    }

    private static Field findFieldByType(
            Class<?> type,
            Class<?> wanted,
            String... preferredNames) {

        Field fallback = null;

        for (Field field : type.getDeclaredFields()) {
            if (field.getType() != wanted) continue;

            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
                continue;
            }

            for (String preferred : preferredNames) {
                if (field.getName().equalsIgnoreCase(preferred)) return field;
            }

            if (fallback == null) fallback = field;
        }

        return fallback;
    }

    private static String leaf(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static void logOnce(String key, String message) {
        if (LOGGED.add(key)) System.out.println(message);
    }

    private static float minX(List<V> face) {
        float value = Float.POSITIVE_INFINITY;
        for (V vertex : face) value = Math.min(value, vertex.x);
        return value;
    }

    private static float maxX(List<V> face) {
        float value = Float.NEGATIVE_INFINITY;
        for (V vertex : face) value = Math.max(value, vertex.x);
        return value;
    }

    private static float minY(List<V> face) {
        float value = Float.POSITIVE_INFINITY;
        for (V vertex : face) value = Math.min(value, vertex.y);
        return value;
    }

    private static float maxY(List<V> face) {
        float value = Float.NEGATIVE_INFINITY;
        for (V vertex : face) value = Math.max(value, vertex.y);
        return value;
    }

    private static float minU(List<V> face) {
        float value = Float.POSITIVE_INFINITY;
        for (V vertex : face) value = Math.min(value, vertex.u);
        return value;
    }

    private static float maxU(List<V> face) {
        float value = Float.NEGATIVE_INFINITY;
        for (V vertex : face) value = Math.max(value, vertex.u);
        return value;
    }

    private static float minV(List<V> face) {
        float value = Float.POSITIVE_INFINITY;
        for (V vertex : face) value = Math.min(value, vertex.v);
        return value;
    }

    private static float maxV(List<V> face) {
        float value = Float.NEGATIVE_INFINITY;
        for (V vertex : face) value = Math.max(value, vertex.v);
        return value;
    }

    private static float rangeX(List<V> face) {
        return maxX(face) - minX(face);
    }

    private static float rangeY(List<V> face) {
        return maxY(face) - minY(face);
    }

    private static final class AlphaMask {
        final int width;
        final int height;
        final byte[] alpha;

        AlphaMask(int width, int height, byte[] alpha) {
            this.width = width;
            this.height = height;
            this.alpha = alpha;
        }

        boolean opaque(float u, float v) {
            int x = Math.min(
                    width - 1,
                    Math.max(0, (int) Math.floor(u * width)));
            int y = Math.min(
                    height - 1,
                    Math.max(0, (int) Math.floor(v * height)));
            return (alpha[y * width + x] & 0xFF) >= ALPHA_THRESHOLD;
        }
    }

    private static final class Circle {
        final float centerX;
        final float centerY;
        final float radius;

        Circle(float centerX, float centerY, float radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }
    }

    private static final class WheelRegion {
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;
        final float centerX;
        final float centerY;

        WheelRegion(
                float minX,
                float maxX,
                float minY,
                float maxY,
                float centerX,
                float centerY) {

            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    private static final class V {
        final float x;
        final float y;
        final float z;
        final float u;
        final float v;

        V(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }
}
