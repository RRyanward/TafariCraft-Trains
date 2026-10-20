package traincraft.client.model.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import traincraft.client.renderer.animation.BakedWheelAnimationContext;

/**
 * DEV54H1: deliberately narrow 2-6-2T wheel-parts proof, OFF by default.
 * Each of the ten source-selected square wheel faces is replaced in memory
 * by an independent, textured, 24-segment circular wheel with sidewall.
 * The authored TCM, accepted phase, axle centers, chassis, rods and gameplay
 * remain untouched. One failed verification restores ALL original draw paths.
 * This is a visual candidate, not mechanical/fleet-wide acceptance.
 */
final class Dev54HRealWheelParts {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MESH_ID = "traincraft:legacy_meshes/locomotive_steam_262t.tcm";
    private static final String EXPECTED_ASSET_SHA = "9DC0F4D11D58CD187BE62890C7E5B6565A157152D363D3441E4C3D307C60FB47";
    private static final String MARKER = "DEV54H1-REAL-WHEELS-ON.txt";
    private static final int FIRST = 0;
    private static final int COUNT = 10;
    private static final int SEGMENTS = 24;
    private static final float DEPTH = 0.03125f;
    private static final float POSITION_TOLERANCE = 0.001f;
    private static final float[][] SOURCE_CENTERS = {
            {-2.281250f,0.406250f,-0.375000f},
            {-1.656250f,0.406250f,-0.375000f},
            {-1.031250f,0.406250f,-0.375000f},
            {-1.031250f,0.406250f,0.375000f},
            {-1.656250f,0.406250f,0.375000f},
            {-2.281250f,0.406250f,0.375000f},
            {-3.406250f,0.468750f,0.375000f},
            {-3.406250f,0.468750f,-0.375000f},
            {0.093750f,0.468750f,0.375000f},
            {0.093750f,0.468750f,-0.375000f}
    };

    private final WheelPart[] parts;
    private Boolean reportedState;

    private Dev54HRealWheelParts(WheelPart[] parts) {
        this.parts = parts;
    }

    static Dev54HRealWheelParts fromSource(ResourceLocation source, byte[] originalTcm,
                                            float[] vertices, int floatsPerVertex) {
        if (!MESH_ID.equals(source.toString())) return null;
        if (!EXPECTED_ASSET_SHA.equalsIgnoreCase(sha256(originalTcm))) {
            LOG.warn("[DEV54H1-HOLD] Unexpected 262T asset SHA. Original wheel drawing preserved.");
            return null;
        }
        if (floatsPerVertex != 8 || vertices.length < COUNT * 24 * floatsPerVertex) {
            LOG.warn("[DEV54H1-HOLD] Unexpected 262T vertex layout. Original preserved.");
            return null;
        }
        WheelPart[] selected = new WheelPart[COUNT];
        for (int i = FIRST; i < COUNT; i++) {
            selected[i] = WheelPart.extract(vertices, i, floatsPerVertex);
            if (selected[i] == null || !close(selected[i].cx, SOURCE_CENTERS[i][0])
                    || !close(selected[i].cy, SOURCE_CENTERS[i][1])
                    || !close(selected[i].cz, SOURCE_CENTERS[i][2])) {
                LOG.warn("[DEV54H1-HOLD] 262T wheel chunk {} failed geometry verification. Original preserved.", i);
                return null;
            }
        }
        LOG.info("[DEV54H1-READY] Verified exact TCM and {} separate circular wheel parts; default=OFF", COUNT);
        return new Dev54HRealWheelParts(selected);
    }

    Frame begin(String meshKey, float[] original, int floatsPerVertex) {
        if (!enabled()) {
            report(false);
            return null;
        }
        BakedWheelAnimationContext.TransformSpec[] phases =
                new BakedWheelAnimationContext.TransformSpec[COUNT];
        for (int i = FIRST; i < COUNT; i++) {
            var phase = BakedWheelAnimationContext.spec(meshKey, i, original, floatsPerVertex);
            if (phase == null || !phase.rotates() || phase.axis() != 2
                    || !close(phase.centerX(), parts[i].cx)
                    || !close(phase.centerY(), parts[i].cy)
                    || !close(phase.centerZ(), parts[i].cz)
                    || !Float.isFinite(phase.cos()) || !Float.isFinite(phase.sin())) {
                report(false);
                return null; // Fail closed: emit all ten original wheel chunks this frame.
            }
            phases[i] = phase;
        }
        report(true);
        return new Frame(parts, phases);
    }

    private void report(boolean active) {
        if (reportedState == null || reportedState.booleanValue() != active) {
            reportedState = active;
            LOG.info("[DEV54H1-WHEEL-PARTS] active={} model=262T parts=10 source=SHA_VERIFIED phase=ACCEPTED existingTCM=UNCHANGED",
                    active);
        }
    }

    private static boolean enabled() {
        try {
            var path = FMLPaths.GAMEDIR.get().resolve(MARKER);
            return Files.isRegularFile(path)
                    && "ON".equalsIgnoreCase(Files.readString(path, StandardCharsets.UTF_8)
                            .replace("\ufeff", "").trim());
        } catch (Exception ex) { return false; }
    }

    private static boolean close(float a, float b) {
        return Float.isFinite(a) && Math.abs(a - b) <= POSITION_TOLERANCE;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte item : digest) sb.append(String.format("%02X", item & 0xff));
            return sb.toString();
        } catch (Exception ex) { return ""; }
    }

    /** A complete, frozen 10-wheel frame: originals are suppressed ONLY for this frame. */
    static final class Frame {
        private final WheelPart[] parts;
        private final BakedWheelAnimationContext.TransformSpec[] phases;

        Frame(WheelPart[] parts, BakedWheelAnimationContext.TransformSpec[] phases) {
            this.parts = parts;
            this.phases = phases;
        }
        boolean hasChunk(int chunkIndex) { return chunkIndex >= FIRST && chunkIndex < COUNT; }
        void draw(PoseStack.Pose pose, VertexConsumer out, int packedLight, int packedOverlay) {
            for (int i = FIRST; i < COUNT; i++) {
                parts[i].draw(pose, out, packedLight, packedOverlay, phases[i]);
            }
        }
    }

    private static final class WheelPart {
        final float cx, cy, cz, radius;
        final UVFace negFace, posFace;

        private WheelPart(float cx, float cy, float cz, float radius, UVFace negFace, UVFace posFace) {
            this.cx = cx; this.cy = cy; this.cz = cz; this.radius = radius;
            this.negFace = negFace; this.posFace = posFace;
        }

        static WheelPart extract(float[] v, int chunk, int stride) {
            int base = chunk * 24 * stride;
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (int n = 0; n < 24; n++) {
                int j = base + n * stride;
                minX = Math.min(minX, v[j]); maxX = Math.max(maxX, v[j]);
                minY = Math.min(minY, v[j + 1]); maxY = Math.max(maxY, v[j + 1]);
                minZ = Math.min(minZ, v[j + 2]); maxZ = Math.max(maxZ, v[j + 2]);
            }
            float dx = maxX-minX, dy = maxY-minY, dz = maxZ-minZ;
            if (!Float.isFinite(dx) || dx < 0.20f || dx > 0.55f
                    || Math.abs(dx-dy) > 0.001f || dz > 0.002f) return null;
            UVFace neg = UVFace.from(v, base + 16*stride, stride, minX, minY, maxX, maxY);
            UVFace pos = UVFace.from(v, base + 20*stride, stride, minX, minY, maxX, maxY);
            if (neg == null || pos == null) return null;
            return new WheelPart((minX+maxX)*0.5f, (minY+maxY)*0.5f,
                    (minZ+maxZ)*0.5f, Math.min(dx,dy)*0.5f,neg,pos);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                  BakedWheelAnimationContext.TransformSpec phase) {
            // Separate wheel parts: source-measured axle, authored face UVs,
            // own closed cylindrical mesh, accepted existing wheel rotation.
            float cc = phase.cos(), ss = phase.sin();
            float z0 = cz - DEPTH * 0.5f, z1 = cz + DEPTH * 0.5f;
            for (int index = 0; index < SEGMENTS; index++) {
                double t0 = 2.0d * Math.PI * index / SEGMENTS;
                double t1 = 2.0d * Math.PI * (index + 1) / SEGMENTS;
                float x0 = (float)Math.cos(t0), y0 = (float)Math.sin(t0);
                float x1 = (float)Math.cos(t1), y1 = (float)Math.sin(t1);
                // UV coordinate stays with its physical wheel vertex as it turns.
                Vertex centerNeg = vertex(0,0,z0, 0,0,-1, negFace, cc,ss);
                Vertex n0 = vertex(x0,y0,z0,0,0,-1,negFace,cc,ss);
                Vertex n1 = vertex(x1,y1,z0,0,0,-1,negFace,cc,ss);
                emitQuad(pose,out,light,overlay,centerNeg,n1,n0,centerNeg);
                Vertex centerPos = vertex(0,0,z1,0,0,1,posFace,cc,ss);
                Vertex p0 = vertex(x0,y0,z1,0,0,1,posFace,cc,ss);
                Vertex p1 = vertex(x1,y1,z1,0,0,1,posFace,cc,ss);
                emitQuad(pose,out,light,overlay,centerPos,p0,p1,centerPos);
                // Narrow actual cylindrical sidewall (not a square texture card).
                float nx = (x0+x1)*0.5f, ny = (y0+y1)*0.5f;
                float magnitude = (float)Math.sqrt(nx*nx+ny*ny);
                nx /= magnitude; ny /= magnitude;
                Vertex s0 = vertex(x0,y0,z0,nx,ny,0,negFace,cc,ss);
                Vertex s1 = vertex(x1,y1,z0,nx,ny,0,negFace,cc,ss);
                Vertex s2 = vertex(x1,y1,z1,nx,ny,0,posFace,cc,ss);
                Vertex s3 = vertex(x0,y0,z1,nx,ny,0,posFace,cc,ss);
                emitQuad(pose,out,light,overlay,s0,s1,s2,s3);
            }
        }

        private Vertex vertex(float radialX, float radialY, float z,
                              float nx, float ny, float nz, UVFace face, float cc, float ss) {
            float px = radius*radialX, py = radius*radialY;
            float rx = px*cc - py*ss, ry = px*ss + py*cc;
            float rnx = nx*cc-ny*ss, rny = nx*ss+ny*cc;
            float[] uv = face.uv((radialX+1f)*0.5f, (radialY+1f)*0.5f);
            return new Vertex(cx+rx,cy+ry,z,uv[0],uv[1],rnx,rny,nz);
        }
    }

    private record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz) {}

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer out,
                                 int packedLight, int packedOverlay,
                                 Vertex a, Vertex b, Vertex c, Vertex d) {
        emit(pose,out,packedLight,packedOverlay,a);
        emit(pose,out,packedLight,packedOverlay,b);
        emit(pose,out,packedLight,packedOverlay,c);
        emit(pose,out,packedLight,packedOverlay,d);
    }
    private static void emit(PoseStack.Pose pose, VertexConsumer out,
                             int packedLight, int packedOverlay, Vertex v) {
        out.vertex(pose.pose(),v.x(),v.y(),v.z())
                .color(255,255,255,255)
                .uv(v.u(),v.v()).overlayCoords(packedOverlay).uv2(packedLight)
                .normal(pose.normal(),v.nx(),v.ny(),v.nz()).endVertex();
    }

    /** The original plate's two orthogonal UV-mapped faces supply texture data. */
    private static final class UVFace {
        final float[][] corners; // [bottom-left, bottom-right, top-left, top-right]
        private UVFace(float[][] corners) { this.corners = corners; }
        static UVFace from(float[] v, int base, int stride,
                           float minX, float minY, float maxX, float maxY) {
            float[][] corners = new float[4][];
            for (int k = 0; k < 4; k++) {
                int j = base + k*stride;
                float x=v[j], y=v[j+1];
                boolean right=Math.abs(x-maxX)<0.001f;
                boolean left=Math.abs(x-minX)<0.001f;
                boolean top=Math.abs(y-maxY)<0.001f;
                boolean bottom=Math.abs(y-minY)<0.001f;
                if ((!right && !left) || (!top && !bottom)) return null;
                int index=(right?1:0)+(top?2:0);
                if (corners[index]!=null) return null;
                corners[index]=new float[] {v[j+3],v[j+4]};
            }
            for (float[] c:corners) if (c==null || !Float.isFinite(c[0]) || !Float.isFinite(c[1])) return null;
            return new UVFace(corners);
        }
        float[] uv(float s,float t) {
            float a=(1-s)*(1-t), b=s*(1-t), c=(1-s)*t, d=s*t;
            return new float[] {
                corners[0][0]*a+corners[1][0]*b+corners[2][0]*c+corners[3][0]*d,
                corners[0][1]*a+corners[1][1]*b+corners[2][1]*c+corners[3][1]*d
            };
        }
    }
}
