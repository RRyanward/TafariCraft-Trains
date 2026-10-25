package traincraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DEV58G11R15 guarded immutable-mesh cache for TCT tankers and transport cars.
 * This opt-in is independent from the accepted R10 locomotive GPU paths.
 * All geometry is uploaded with source-authored triangles as degenerate quads,
 * using identical texture coordinates, flat normals and colors. The four
 * wheel groups are drawn with their original per-axle pose matrices.
 * Falls back to the unchanged CPU renderer on any unavailable VBO state.
 */
public final class Dev58G11R15GpuBatch {
    private static final Logger LOG = LoggerFactory.getLogger(Dev58G11R15GpuBatch.class);
    private static final boolean ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R15-ONE-BATCH-GPU-ON.flag"));
    private static boolean disabled;
    private static long draws, uploads, fallbacks;
    private static long totalBufferBytes;
    private static final long BUFFER_BUDGET_BYTES = 160L * 1024L * 1024L;
    // R15R3: bounded immutable VBOs per packed-light variant. Never upload the
    // same mesh again merely because the next vehicle has different lighting.
    // A miss after the per-part/global budget is reached uses the original CPU
    // path; it must NOT evict/rebuild large mesh buffers every frame.
    private static final int MAX_LIGHT_VARIANTS_PER_PART = 4;
    private static long rejectedLightVariants;


    private Dev58G11R15GpuBatch() {}
    @FunctionalInterface public interface QuadEmitter {
        void emit(VertexConsumer output, int packedLight);
    }
    public static final class Part {
        final String label;
        final int count;
        final QuadEmitter emitter;
        private final java.util.LinkedHashMap<Integer, VertexBuffer> lightBuffers = new java.util.LinkedHashMap<>();
        private final java.util.LinkedHashMap<Integer, Long> lightBytes = new java.util.LinkedHashMap<>();
        public Part(String label, int quadVertices, QuadEmitter emitter) {
            if (quadVertices <= 0 || quadVertices % 4 != 0)
                throw new IllegalArgumentException("Invalid R15 quad vertex count: " + label);
            this.label = label;
            this.count = quadVertices;
            this.emitter = emitter;
        }
        private VertexBuffer bufferFor(int light) {
            VertexBuffer cached = lightBuffers.get(light);
            return cached != null && !cached.isInvalid() ? cached : null;
        }
        private boolean upload(int light) {
            VertexBuffer cached = bufferFor(light);
            if (cached != null) return true;
            // GL context rebuild may invalidate buffers. Account for the old
            // reservation; never reuse an invalid buffer.
            VertexBuffer invalid = lightBuffers.remove(light);
            if (invalid != null) {
                totalBufferBytes -= lightBytes.remove(light);
                invalid.close();
                disabled = true;
                LOG.error("[DEV58G11R15R3-CONTEXT-HOLD] part={} invalid cached VBO; GPU disabled for this run", label);
                return false;
            }
            long needed = (long)count * DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            if (needed <= 0L || needed > BUFFER_BUDGET_BYTES ||
                lightBuffers.size() >= MAX_LIGHT_VARIANTS_PER_PART ||
                totalBufferBytes + needed > BUFFER_BUDGET_BYTES) {
                rejectedLightVariants++;
                if (rejectedLightVariants <= 8L)
                    LOG.warn("[DEV58G11R15R3-CACHE-LIMIT] part={} light={} cachedVariants={} " +
                             "allocatedBytes={} requiredBytes={} CPU fallback (no upload churn)",
                             label, light, lightBuffers.size(), totalBufferBytes, needed);
                return false;
            }
            VertexBuffer next = null;
            BufferBuilder builder = new BufferBuilder((int)Math.min(Integer.MAX_VALUE, needed));
            try {
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                emitter.emit(builder, light);
                var ready = builder.end();
                next = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    next.bind();
                    next.upload(ready);
                } finally {
                    VertexBuffer.unbind();
                }
                if (next.isInvalid())
                    throw new IllegalStateException("R15R3 uploaded buffer invalid " + label);
                lightBuffers.put(light, next);
                lightBytes.put(light, needed);
                totalBufferBytes += needed;
                next = null;
                uploads++;
                if (uploads <= 24L || uploads % 32L == 0L)
                    LOG.info("[DEV58G11R15R3-UPLOAD] part={} light={} cachedVariants={} quads={} bytes={} totalBytes={}",
                            label, light, lightBuffers.size(), count / 4, needed, totalBufferBytes);
                return true;
            } catch (Exception ex) {
                LOG.error("[DEV58G11R15R3-UPLOAD-HOLD] part={} CPU fallback", label, ex);
                disabled = true;
                return false;
            } finally {
                if (next != null) next.close();
                if (builder.building()) builder.discard();
                builder.clear();
            }
        }
    }
    public static final class Draw {
        final Part part;
        final Matrix4f pose;
        final Matrix3f normals;
        public Draw(Part part, PoseStack.Pose source) {
            this.part = part;
            this.pose = new Matrix4f(source.pose());
            this.normals = new Matrix3f(source.normal());
        }
    }
    public static boolean draw(String key, MultiBufferSource source, RenderType type,
                               int packedLight, Draw... parts) {
        if (!ON || disabled || parts == null || parts.length == 0 ||
                !(source instanceof MultiBufferSource.BufferSource bufferSource)) return false;
        var consumer = source.getBuffer(type);
        if (!(consumer instanceof BufferBuilder bb) || !bb.building()) return false;
        // Upload every part BEFORE flushing/rendering; incomplete groups stay on CPU.
        for (Draw p : parts) {
            if (p == null || p.part == null || !p.part.upload(packedLight)) {
                fallbacks++;
                return false;
            }
        }
        boolean state = false;
        try {
            bufferSource.endBatch(type);
            type.setupRenderState();
            state = true;
            var shader = RenderSystem.getShader();
            if (shader == null) throw new IllegalStateException("R15 entity shader unavailable");
            var baseModel = new Matrix4f(RenderSystem.getModelViewMatrix());
            RenderSystem.setupShaderLights(shader);
            // Shader light directions are stateful: restore EXACT originals after each part.
            var oldLight0 = readLight(shader.LIGHT0_DIRECTION);
            var oldLight1 = readLight(shader.LIGHT1_DIRECTION);
            for (Draw item : parts) {
                var local0 = new Vector3f(oldLight0).mul(new Matrix3f(item.normals).transpose());
                var local1 = new Vector3f(oldLight1).mul(new Matrix3f(item.normals).transpose());
                try {
                    RenderSystem.setShaderLights(local0, local1);
                    VertexBuffer prepared = item.part.bufferFor(packedLight);
                    if (prepared == null)
                        throw new IllegalStateException("R15R3 missing prepared VBO: " + item.part.label);
                    prepared.bind();
                    prepared.drawWithShader(new Matrix4f(baseModel).mul(item.pose),
                            RenderSystem.getProjectionMatrix(), shader);
                } finally {
                    RenderSystem.setShaderLights(oldLight0, oldLight1);
                }
                draws++;
            }
            if (draws <= 12L || draws % 4096L == 0L)
                LOG.info("[DEV58G11R15-DRAW] model={} parts={} draws={} uploads={} fallbacks={} gpu=ON",
                        key, parts.length, draws, uploads, fallbacks);
            return true;
        } catch (Exception ex) {
            disabled = true;
            LOG.error("[DEV58G11R15-SAFETY-HOLD] GPU rendering disabled for this run; CPU fallback", ex);
            return false;
        } finally {
            VertexBuffer.unbind();
            if (state) type.clearRenderState();
        }
    }
    private static Vector3f readLight(com.mojang.blaze3d.shaders.Uniform uniform) {
        if (uniform == null || uniform.getFloatBuffer() == null ||
                uniform.getFloatBuffer().capacity() < 3)
            throw new IllegalStateException("R15 entity shader light uniform missing");
        java.nio.FloatBuffer fb = uniform.getFloatBuffer();
        return new Vector3f(fb.get(0), fb.get(1), fb.get(2));
    }
}
