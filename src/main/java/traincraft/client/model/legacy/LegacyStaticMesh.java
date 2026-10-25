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
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.nio.file.StandardOpenOption;

public final class LegacyStaticMesh {
    // DEV58G7: A/B rendering experiment. Default ON; the opt-out file is
    // checked ONCE when the renderer class is initialized, never per vertex.
    // OFF keeps the exact accepted G5R3 vertex submission chain.
    private static final boolean DEV58G7_FAST_VERTEX =
            !java.nio.file.Files.isRegularFile(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV58G7-FAST-VERTEX-OFF.flag"));
    // DEV58G8R1: isolated opt-in MILW + two Berkshire source-mesh pilots.
    // Class-initialized flags are never polled within the render loop.
    // Default OFF. Frozen DEV54 wheel/rod/piston, masks and accepted lighting stay dynamic.
    private static final boolean DEV58G8R1_MILW_ON =
            java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV58G8R1-MILW-ON.flag"));
    private static final boolean DEV58G8R1_B1225_ON =
            java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV58G8R1-B1225-ON.flag"));
    private static final boolean DEV58G8R1_B765_ON =
            java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV58G8R1-B765-ON.flag"));
    // DEV58G11: B765 only. Frozen source geometry + dynamic guard remain authoritative.
    // OFF by default; requires full game restart after switching.
    private static final String DEV58G11_B765_PATH =
            "legacy_meshes/locomotive_steam_berkshire_765.tcm";
    private static final boolean DEV58G11_B765_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R3-B765-GPU-ON.flag"));
    // DEV58G11R8: fourth ten-model bounded GPU opt-in. OFF by default.
    // Previous validated R4/R5-approved/R6/R7 paths remain active with R8,
    // but Hall Class is still quarantined. All accepted dynamic source chunks,
    // rods, wheels, pistons and lighting retain their original CPU paths.
    private static final boolean DEV58G11R8_BATCH4_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R8-BATCH4-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R8_BATCH4_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_vb_shay.tcm",
            "legacy_meshes/locomotive_steam_262t.tcm",
            "legacy_meshes/locomotive_steam_alice_0_4_0.tcm",
            "legacy_meshes/locomotive_steam_glyn_042t.tcm",
            "legacy_meshes/locomotive_steam_vb_shay_2.tcm",
            "legacy_meshes/locomotive_steam_gs4.tcm",
            "legacy_meshes/locomotive_steam_snow_plow.tcm",
            "legacy_meshes/locomotive_steam_star_class.tcm",
            "legacy_meshes/locomotive_steam_climax_2.tcm",
            "legacy_meshes/locomotive_steam_lssp7.tcm");
    private static boolean dev58g11r8PilotMesh(String path) {
        return dev58g11r8OrR9() && DEV58G11R8_BATCH4_PATHS.contains(path);
    }
    // DEV58G11R10: batch-six 10-locomotive measurement course. Two newly
    // eligible VERIFIED legacy static meshes (4-4-0 and VB 0-4-0). The eight
    // native-model locomotives remain on their ORIGINAL renderer paths;
    // no fake GPU dispatch is claimed for them. Accepted rods, dynamic parts,
    // and all lighting are left untouched. OFF unless independent R10 flag.
    private static final boolean DEV58G11R10_BATCH6_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R10-BATCH6-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R10_BATCH6_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_4_4_0.tcm",
            "legacy_meshes/locomotive_steam_vb_0_4_0.tcm");
    private static boolean dev58g11r10PilotMesh(String path) {
        return DEV58G11R10_BATCH6_GPU_ON && DEV58G11R10_BATCH6_PATHS.contains(path);
    }
    // DEV58G11R9: fifth ten-model bounded GPU static-mesh pilot.
    // Only previously verified static chunks may use a GPU VBO; accepted dynamic
    // wheels, rods, pistons, masking, and lighting still use the original paths.
    // Flag OFF by default. R9 alone also enables R8/R7/R6/R5-approved/R4.
    private static final boolean DEV58G11R9_BATCH5_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R9-BATCH5-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R9_BATCH5_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_br01.tcm",
            "legacy_meshes/locomotive_steam_forney.tcm",
            "legacy_meshes/locomotive_steam_mogul.tcm",
            "legacy_meshes/locomotive_steam_shay.tcm",
            "legacy_meshes/locomotive_steam_br80.tcm",
            "legacy_meshes/locomotive_steam_u57.tcm",
            "legacy_meshes/locomotive_steam_cherepanov.tcm",
            "legacy_meshes/locomotive_steam_c62_body.tcm",
            "legacy_meshes/locomotive_steam_d51_body.tcm",
            "legacy_meshes/locomotive_steam_d51_long_body.tcm");
    private static boolean dev58g11r8OrR9() {
        return DEV58G11R8_BATCH4_GPU_ON || DEV58G11R9_BATCH5_GPU_ON ||
                DEV58G11R10_BATCH6_GPU_ON;
    }
    private static boolean dev58g11r9PilotMesh(String path) {
        return (DEV58G11R9_BATCH5_GPU_ON || DEV58G11R10_BATCH6_GPU_ON) &&
                DEV58G11R9_BATCH5_PATHS.contains(path);
    }
    // DEV58G11R7: third group of ten. R7 combined flag also enables
    // R4 pair, the nine R5 accepted meshes (HALL QUARANTINED), and R6 ten.
    // Mesh opt-in ONLY; animated rods, wheel parts, pistons, lights unchanged.
    private static final boolean DEV58G11R7_BATCH3_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R7-BATCH3-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R7_BATCH3_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_rw_type_3.tcm",
            "legacy_meshes/locomotive_steam_fowler.tcm",
            "legacy_meshes/locomotive_steam_climax.tcm",
            "legacy_meshes/locomotive_steam_pe.tcm",
            "legacy_meshes/locomotive_steam_sentinel_y3.tcm",
            "legacy_meshes/locomotive_steam_adler.tcm",
            "legacy_meshes/locomotive_steam_rw_type_2.tcm",
            "legacy_meshes/locomotive_steam_skookum.tcm",
            "legacy_meshes/locomotive_steam_gwr_101_class.tcm",
            "legacy_meshes/locomotive_steam_milw_class_a.tcm");
    private static boolean dev58g11r7PilotMesh(String path) {
        return (DEV58G11R7_BATCH3_GPU_ON || dev58g11r8OrR9()) &&
                DEV58G11R7_BATCH3_PATHS.contains(path);
    }
    // DEV58G11R6: second batch of ten. R6 enables R4 + the nine R5
    // visually accepted meshes (Hall Class quarantined) + these ten.
    // All moving rods, wheels, pistons, masks and lighting keep original logic.
    private static final boolean DEV58G11R6_BATCH2_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R6-BATCH2-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R6_BATCH2_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_s100_uk.tcm",
            "legacy_meshes/locomotive_steam_a4_mallard.tcm",
            "legacy_meshes/locomotive_steam_br_black_5.tcm",
            "legacy_meshes/locomotive_steam_c11.tcm",
            "legacy_meshes/locomotive_steam_coronation_class.tcm",
            "legacy_meshes/locomotive_steam_pannier.tcm",
            "legacy_meshes/locomotive_steam_midland_compound.tcm",
            "legacy_meshes/locomotive_steam_wwcp_062t.tcm",
            "legacy_meshes/locomotive_steam_gwr_72xx.tcm",
            "legacy_meshes/locomotive_steam_gwr_42xx.tcm");
    private static boolean dev58g11r6PilotMesh(String path) {
        return (DEV58G11R6_BATCH2_GPU_ON || DEV58G11R7_BATCH3_GPU_ON ||
                dev58g11r8OrR9()) &&
                DEV58G11R6_BATCH2_PATHS.contains(path);
    }
    // Do not enable Hall Class's R5 static GPU path in combined R6 mode;
    // its one-sided rod placement is on VISUAL HOLD. R5-only comparison
    // mode remains bit-for-bit equivalent to the accepted R5 test.
    private static boolean dev58g11r6ApprovedR5Mesh(String path) {
        return (DEV58G11R6_BATCH2_GPU_ON || DEV58G11R7_BATCH3_GPU_ON ||
                dev58g11r8OrR9()) &&
                !"legacy_meshes/locomotive_steam_hall_class.tcm".equals(path);
    }
    // DEV58G11R5: ten-model GPU pilot (isolated from R3 765 and R4 baseline).
    // All dynamic rods, wheels, pistons, headlamps and masks use frozen CPU paths.
    // R5 opt-in also keeps the previously tested King/B1225 R4 GPU acceleration.
    private static final boolean DEV58G11R5_BATCH10_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R5-BATCH10-GPU-ON.flag"));
    private static final java.util.Set<String> DEV58G11R5_BATCH10_PATHS = java.util.Set.of(
            "legacy_meshes/locomotive_steam_hall_class.tcm",
            "legacy_meshes/locomotive_steam_alco_sc4.tcm",
            "legacy_meshes/locomotive_steam_j50.tcm",
            "legacy_meshes/locomotive_steam_c41.tcm",
            "legacy_meshes/locomotive_steam_c41_080.tcm",
            "legacy_meshes/locomotive_steam_c41t.tcm",
            "legacy_meshes/locomotive_steam_southern1102.tcm",
            "legacy_meshes/locomotive_steam_shay_3_truck.tcm",
            "legacy_meshes/locomotive_steam_s100_us.tcm",
            "legacy_meshes/locomotive_steam_br_britannia.tcm");
    private static boolean dev58g11r5PilotMesh(String path) {
        return (DEV58G11R5_BATCH10_GPU_ON || dev58g11r6ApprovedR5Mesh(path)) &&
                DEV58G11R5_BATCH10_PATHS.contains(path);
    }
    // DEV58G11R4: isolated heavy-mesh extension of accepted R3 GPU pilot.
    // Only King Class and Berkshire 1225 join the R3 renderer when explicitly
    // enabled. The original B765 flag and frozen light/rod data stay untouched.
    private static final String DEV58G11R4_KING_PATH =
            "legacy_meshes/locomotive_steam_king_class.tcm";
    private static final String DEV58G11R4_B1225_PATH =
            "legacy_meshes/locomotive_steam_berkshire_1225.tcm";
    private static final boolean DEV58G11R4_HEAVY_GPU_ON = java.nio.file.Files.isRegularFile(
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R4-HEAVY-GPU-ON.flag"));
    private static boolean dev58g11r4PilotMesh(String path) {
        return (DEV58G11R4_HEAVY_GPU_ON || DEV58G11R5_BATCH10_GPU_ON ||
                DEV58G11R6_BATCH2_GPU_ON || DEV58G11R7_BATCH3_GPU_ON ||
                dev58g11r8OrR9()) &&
                (DEV58G11R4_KING_PATH.equals(path) || DEV58G11R4_B1225_PATH.equals(path));
    }
    // DEV58G11R15: 3 source-verified tenders. Hall remains CPU/quarantined:
    // prior Hall GPU trial displaced a rod, so a fresh A/B side proof is required.
    private static final boolean DEV58G11R15_TENDERS_ON=java.nio.file.Files.isRegularFile(
        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R15-ONE-BATCH-GPU-ON.flag"));
    private static boolean dev58g11r15TenderMesh(String path) {
        return DEV58G11R15_TENDERS_ON &&
            ("legacy_meshes/steam_tender_berkshire.tcm".equals(path) ||
             "legacy_meshes/steam_tender_4000_gallon.tcm".equals(path) ||
             "legacy_meshes/steam_tender_a4_mallard.tcm".equals(path));
    }
    // DEV58G11R16: exact five-locomotive A/B performance pilot. Hall is separately
    // disableable by removing its marker and restarting the client. Source rod,
    // wheel and piston geometry remains managed by the frozen DEV54 paths.
    private static final boolean DEV58G11R16_FIVE_ON=java.nio.file.Files.isRegularFile(
        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R16-FIVE-LOCO-GPU-ON.flag"));
    private static final boolean DEV58G11R16_HALL_ON=DEV58G11R16_FIVE_ON && java.nio.file.Files.isRegularFile(
        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV58G11R16-HALL-SHELL-TRIAL-ON.flag"));
    private static final java.util.Set<String> DEV58G11R16_BOGIES=java.util.Set.of(
        "legacy_meshes/locomotive_steam_c11_front_truck.tcm",
        "legacy_meshes/locomotive_steam_c11_rear_truck.tcm",
        "legacy_meshes/locomotive_steam_pe_front_truck.tcm",
        "legacy_meshes/locomotive_steam_pe_rear_truck.tcm");
    private static boolean dev58g11r16GpuMesh(String path){
        return DEV58G11R16_FIVE_ON && (DEV58G11R16_BOGIES.contains(path) ||
            (DEV58G11R16_HALL_ON && "legacy_meshes/locomotive_steam_hall_class.tcm".equals(path)));
    }
    // R16 Hall trial admits only BODY chunks after excluding accepted wheel,
    // rod, piston and edited source candidates. A/B visual approval is mandatory.
    private static boolean dev58g11r16ProtectedHallChunk(String path,int chunk){
        if(!"legacy_meshes/locomotive_steam_hall_class.tcm".equals(path))return false;
        var tags=DEV54H14_TAGS.get(path);
        return dev44R10Mapped(path,chunk) ||
             (tags!=null && (tags.rod(chunk) || java.util.Arrays.binarySearch(tags.pistons(),chunk)>=0));
    }
    private static boolean dev58g11GpuMesh(String path) {
        return (DEV58G11_B765_GPU_ON && DEV58G11_B765_PATH.equals(path)) ||
                dev58g11r4PilotMesh(path) || dev58g11r5PilotMesh(path) ||
                dev58g11r6PilotMesh(path) || dev58g11r7PilotMesh(path) ||
                dev58g11r8PilotMesh(path) || dev58g11r9PilotMesh(path) ||
                dev58g11r10PilotMesh(path) || dev58g11r15TenderMesh(path) ||
                dev58g11r16GpuMesh(path);
    }
    private static boolean dev58g8r1AllowedModel(String path) {
        return switch(path) {
            case "legacy_meshes/locomotive_steam_milw_class_a.tcm" -> DEV58G8R1_MILW_ON || dev58g11r7PilotMesh(path);
            case "legacy_meshes/locomotive_steam_berkshire_1225.tcm" ->
                    DEV58G8R1_B1225_ON || dev58g11r4PilotMesh(path);
            case "legacy_meshes/locomotive_steam_king_class.tcm" -> dev58g11r4PilotMesh(path);
            case "legacy_meshes/locomotive_steam_berkshire_765.tcm" ->
                    DEV58G8R1_B765_ON || DEV58G11_B765_GPU_ON;
            default -> dev58g11r5PilotMesh(path) || dev58g11r6PilotMesh(path) ||
                    dev58g11r7PilotMesh(path) || dev58g11r8PilotMesh(path) ||
                    dev58g11r9PilotMesh(path) || dev58g11r10PilotMesh(path) ||
                    dev58g11r15TenderMesh(path) || dev58g11r16GpuMesh(path);
        };
    }
    // The same shared mesh resource can be rendered for many locomotives per frame.
    // Keep up to 16 pose/light variants per source chunk (12-train scene); never
    // reuse a packet for a different pose, normal transform, light or overlay.
    private record Dev58G8R1Packet(org.joml.Matrix4f pose, org.joml.Matrix3f normal,
                                  int light, int overlay, byte[] bytes) {}
    private final java.util.Map<Integer,java.util.ArrayDeque<Dev58G8R1Packet>> dev58g8r1Packets =
            new java.util.HashMap<>();
    private long dev58g8r1CacheHits=0L, dev58g8r1CacheMisses=0L;
    // DEV58G11R1: one bounded scratch builder per loaded mesh, allocated lazily.
    // Before R1, EVERY pose-cache miss allocated a new BufferBuilder with direct
    // vertex memory. In a moving 12-train scene this occurred millions of times.
    // Keeping and clearing one builder avoids unbounded native allocation churn.
    private com.mojang.blaze3d.vertex.BufferBuilder dev58g11r1PacketScratch;
    private boolean dev58g8r1Announced=false;
    private boolean dev58g8r2FrameReported=false;
    // Only reached after existing hide/motion predicates have proved the full
    // 24-vertex source chunk unmodified. The vanilla NEW_ENTITY encoder supplies
    // the bytes, not a bespoke packing format.
    private java.nio.ByteBuffer dev58g8r1StaticPacket(int chunk,
            PoseStack.Pose pose,int packedLight,int packedOverlay) {
        var packets=dev58g8r1Packets.get(chunk);
        if(packets!=null) {
            for(var p : packets) {
                if(p.light()==packedLight && p.overlay()==packedOverlay &&
                   p.pose().equals(pose.pose()) && p.normal().equals(pose.normal())) {
                    dev58g8r1CacheHits++;
                    return java.nio.ByteBuffer.wrap(p.bytes());
                }
            }
        }
        final int start=chunk*24*FLOATS_PER_VERTEX;
        if(start<0 || start+24*FLOATS_PER_VERTEX>this.vertices.length) return null;
        var builder=dev58g11r1PacketScratch;
        if(builder==null) {
            builder=new com.mojang.blaze3d.vertex.BufferBuilder(24*40);
            dev58g11r1PacketScratch=builder;
        }
        // This renderer runs on the render thread. A nested builder indicates
        // an unexpected call state; do not corrupt an active draw buffer.
        if(builder.building()) throw new IllegalStateException("R1 scratch buffer unexpectedly building");
        builder.clear();
        byte[] payload;
        try {
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                          com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);
            for(int q=0;q<24;q++) {
                int o=start+q*FLOATS_PER_VERTEX;
                builder.vertex(pose.pose(),vertices[o],vertices[o+1],vertices[o+2])
                        .color(255,255,255,255)
                        .uv(vertices[o+3],vertices[o+4])
                        .overlayCoords(packedOverlay).uv2(packedLight)
                        .normal(pose.normal(),vertices[o+5],vertices[o+6],vertices[o+7])
                        .endVertex();
            }
            var done=builder.end();
            try {
                java.nio.ByteBuffer data=done.vertexBuffer();
                payload=new byte[data.remaining()];data.get(payload);
            } finally {done.release();}
        } finally {
            if(builder.building()) builder.discard();
            builder.clear();
        }
        if(payload.length!=24*com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY.getVertexSize())
            return null;
        if(packets==null){packets=new java.util.ArrayDeque<>();dev58g8r1Packets.put(chunk,packets);}
        if(packets.size()>=16) packets.removeFirst();
        packets.addLast(new Dev58G8R1Packet(new org.joml.Matrix4f(pose.pose()),
                new org.joml.Matrix3f(pose.normal()),packedLight,packedOverlay,payload));
        dev58g8r1CacheMisses++;
        return java.nio.ByteBuffer.wrap(payload);
    }
    // DEV58G9: research-backed GPU-residency POC. OFF BY DEFAULT.
    // Geometry is uploaded only; NO VBO draw or source vertex suppression.
    // Reason: production materials use entityTranslucentCull and require an
    // ordered shader/lightmap integration test before any render replacement.
    private static final boolean DEV58G9_GPU_UPLOAD_ONLY =
            java.nio.file.Files.isRegularFile(
                    net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV58G9-GPU-RESIDENCY-ON.flag"));
    private final java.util.BitSet dev58g9EligibleStaticChunks=new java.util.BitSet();
    private com.mojang.blaze3d.vertex.VertexBuffer dev58g9GpuBuffer;
    private boolean dev58g9UploadAttempted=false;

    private void dev58g9UploadOnlyProof() {
        if ((!DEV58G9_GPU_UPLOAD_ONLY && !dev58g11GpuMesh(this.source.getPath())) ||
                dev58g9UploadAttempted || !dev58g8r1AllowedModel(this.source.getPath())) return;
        final int count=dev58g9EligibleStaticChunks.cardinality();
        // DEV58G11R7R1 PE: the PE body is largely dynamic; its few individually
        // certified rigid chunks are still safe to accelerate. Never admit
        // unverified/moving chunks or relax the guard for any other model.
        final boolean r7r1PeSmallStaticPilot =
                (DEV58G11R7_BATCH3_GPU_ON || dev58g11r8OrR9()) &&
                this.dev44R11Verified &&
                "legacy_meshes/locomotive_steam_pe.tcm".equals(this.source.getPath());
        final int minimumStaticChunks = r7r1PeSmallStaticPilot ? 1 : 8;
        if (count < minimumStaticChunks) return; // Never upload an empty model.
        dev58g9UploadAttempted=true;
        com.mojang.blaze3d.vertex.VertexBuffer trial=null;
        try {
            // Upload source-local geometry: moving the camera must not require
            // changing vertex coordinates. All dynamic/frozen hide decisions
            // were made by the existing G8R2 guard BEFORE these chunks are added.
            var fmt=com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY;
            var builder=new com.mojang.blaze3d.vertex.BufferBuilder(
                    Math.max(1024,count*24*fmt.getVertexSize()));
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,fmt);
            int n=0;
            for (int ch=dev58g9EligibleStaticChunks.nextSetBit(0);ch>=0;
                     ch=dev58g9EligibleStaticChunks.nextSetBit(ch+1)) {
                int start=ch*24*FLOATS_PER_VERTEX;
                if(start<0||start+24*FLOATS_PER_VERTEX>vertices.length)continue;
                for(int vi=0;vi<24;vi++) {
                    int i=start+vi*FLOATS_PER_VERTEX;
                    builder.vertex(vertices[i],vertices[i+1],vertices[i+2])
                            .color(255,255,255,255)
                            .uv(vertices[i+3],vertices[i+4])
                            .overlayCoords(0).uv2(0x00F000F0)
                            .normal(vertices[i+5],vertices[i+6],vertices[i+7])
                            .endVertex();
                    n++;
                }
            }
            var ready=builder.end();
            try {
                trial=new com.mojang.blaze3d.vertex.VertexBuffer(
                        com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                trial.bind();
                trial.upload(ready);
            } finally {
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                // Vanilla VertexBuffer.upload owns/recycles the RenderedBuffer.
            }
            if(trial.isInvalid())throw new IllegalStateException("GPU buffer invalid after upload");
            dev58g9GpuBuffer=trial;
            LOGGER.info("[DEV58G9-GPU-RESIDENCY-PASS] mesh={} chunks={} vertices={} "
                    +"gpuVertexBytes={} draw=DISABLED unchangedRender=YES",
                    this.source,count,n,n*fmt.getVertexSize());
        } catch(Exception ex) {
            if(trial!=null)try{trial.close();}catch(Exception ignored){}
            LOGGER.warn("[DEV58G9-GPU-RESIDENCY-HOLD] mesh={} "
                    +"sourceVertexRenderingUnaffected=true cause={}",this.source,ex.toString());
        }
    }
    // DEV58G10 read-only cost audit. OFF by default; no geometry or render-state changes.
    private static final boolean DEV58G10_METRICS_ON =
            java.nio.file.Files.isRegularFile(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV58G10-METRICS-ON.flag"));
    private long dev58g10LegacyVertices=0L,dev58g10KnownMotionVertices=0L;
    private long dev58g10CpuPacketVertices=0L,dev58g10GpuSkippedVertices=0L;
    private long dev58g10RestoredVertices=0L,dev58g10UploadNanos=0L;
    /** Cumulative render-thread counters: not independently rendered timings. */
    public long[] dev58g10Counters() {
        return new long[]{dev58g10LegacyVertices,dev58g10KnownMotionVertices,
            dev58g10CpuPacketVertices,dev58g10GpuSkippedVertices,
            dev58g10RestoredVertices,dev58g8r1CacheHits,dev58g8r1CacheMisses,
            dev58g9s2Draws,dev58g9s2Uploads,dev58g9s2Fallbacks,dev58g10UploadNanos};
    }
    // DEV58G9S2: OFF-by-default real GPU DRAW A/B, only source-certified MILW.
    // All chunk admission/hide checks remain in the original CPU render loop.
    // We suppress a cached chunk only when the current frame certifies it again;
    // if even one GPU-resident chunk loses eligibility, every skipped packet is
    // restored to CPU output and GPU drawing is cancelled for that frame.
    private static final boolean DEV58G9S2_GPU_DRAW_ON =
            java.nio.file.Files.isRegularFile(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV58G9S2-GPU-DRAW-ON.flag"));
    private com.mojang.blaze3d.vertex.VertexBuffer dev58g9s2DrawBuffer;
    private org.joml.Matrix3f dev58g9s2UploadedNormal;
    private int dev58g9s2UploadedLight=-1, dev58g9s2UploadedOverlay=-1;
    private final java.util.BitSet dev58g9s2Skipped=new java.util.BitSet();
    private boolean dev58g9s2SkipThisRender=false,dev58g9s2Disabled=false;
    private long dev58g9s2Draws=0,dev58g9s2Fallbacks=0,dev58g9s2Uploads=0;

    private boolean dev58g9s2PrepareGpu(PoseStack.Pose pose,int light,int overlay) {
        if (!DEV58G9S2_GPU_DRAW_ON || !DEV58G9_GPU_UPLOAD_ONLY ||
            dev58g9s2Disabled || dev58g9GpuBuffer==null ||
            !this.source.getPath().equals("legacy_meshes/locomotive_steam_milw_class_a.tcm") ||
            dev58g9EligibleStaticChunks.cardinality()<8) return false;
        final org.joml.Matrix3f normals=pose.normal();
        if(dev58g9s2DrawBuffer!=null && !dev58g9s2DrawBuffer.isInvalid() &&
           dev58g9s2UploadedLight==light && dev58g9s2UploadedOverlay==overlay &&
           dev58g9s2UploadedNormal!=null && dev58g9s2UploadedNormal.equals(normals))return true;
        com.mojang.blaze3d.vertex.VertexBuffer next=null;
        final long dev58g10UploadStart=DEV58G10_METRICS_ON ? System.nanoTime() : 0L;
        try {
            // Unlike G9 Stage 1, use EXACT per-entity light, overlay and
            // CPU-equivalent normal transformation. Source vertex positions
            // remain local so the model pose can be applied by the GPU.
            var fmt=com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY;
            final int chunks=dev58g9EligibleStaticChunks.cardinality();
            var b=new com.mojang.blaze3d.vertex.BufferBuilder(
                    Math.max(1024,chunks*24*fmt.getVertexSize()));
            b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,fmt);
            for(int ch=dev58g9EligibleStaticChunks.nextSetBit(0);ch>=0;
                    ch=dev58g9EligibleStaticChunks.nextSetBit(ch+1)) {
                int start=ch*24*FLOATS_PER_VERTEX;
                if(start<0||start+24*FLOATS_PER_VERTEX>vertices.length)
                    throw new IllegalStateException("certified chunk outside mesh");
                for(int vi=0;vi<24;vi++) {
                    int i=start+vi*FLOATS_PER_VERTEX;
                    var n=new org.joml.Vector3f(vertices[i+5],vertices[i+6],vertices[i+7]).mul(normals);
                    b.vertex(vertices[i],vertices[i+1],vertices[i+2])
                        .color(255,255,255,255)
                        .uv(vertices[i+3],vertices[i+4])
                        .overlayCoords(overlay).uv2(light)
                        .normal(n.x(),n.y(),n.z()).endVertex();
                }
            }
            var ready=b.end();
            try {
                next=new com.mojang.blaze3d.vertex.VertexBuffer(
                    com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                next.bind();next.upload(ready);
            }finally {com.mojang.blaze3d.vertex.VertexBuffer.unbind();}
            if(next.isInvalid())throw new IllegalStateException("GPU upload invalid");
            var prev=dev58g9s2DrawBuffer;
            dev58g9s2DrawBuffer=next; next=null;
            dev58g9s2UploadedLight=light; dev58g9s2UploadedOverlay=overlay;
            dev58g9s2UploadedNormal=new org.joml.Matrix3f(normals);
            dev58g9s2Uploads++;
            if(DEV58G10_METRICS_ON) dev58g10UploadNanos+=System.nanoTime()-dev58g10UploadStart;
            if(prev!=null)prev.close();
            if(dev58g9s2Uploads<=3)
                LOGGER.info("[DEV58G9S2-UPLOAD] model={} chunks={} light={} overlay={} uploadNumber={} normalsExact=true",
                    source,chunks,light,overlay,dev58g9s2Uploads);
            return true;
        }catch(Exception ex) {
            if(next!=null)try{next.close();}catch(Exception ignored){}
            dev58g9s2Disabled=true;
            LOGGER.error("[DEV58G9S2-SAFETY-HOLD] GPU preparation failed; CPU renderer retained model={}",source,ex);
            return false;
        }
    }

    // DEV58G11R3: one GPU copy per packed-light/overlay pair, never per camera
    // or locomotive normal matrix. Source-local normals stay in immutable VBO;
    // the two entity-shader diffuse light directions are temporarily transformed
    // by transpose(current PoseStack normal matrix) immediately before this draw.
    // This reproduces dot(light, pose.normal * localNormal) algebraically without
    // reuploading geometry whenever the camera or locomotive orientation changes.
    // Vertex source, dynamic pieces, and accepted headlamps are untouched.
    private record Dev58G11Variant(int light, int overlay,
                                   com.mojang.blaze3d.vertex.VertexBuffer buffer) {}
    // G11R2: bounded access-ordered variants. No permanent 24-pose saturation.
    // Render thread owns all mutations and VertexBuffer calls.
    private final java.util.List<Dev58G11Variant> dev58g11Variants = new java.util.ArrayList<>();
    private long dev58g11r2Evictions = 0L;
    private long dev58g11r2Throttled = 0L;
    private long dev58g11r3CacheHits = 0L, dev58g11r3CacheMisses = 0L;
    private long dev58g11r3CpuFallbacks = 0L, dev58g11r3LastStatsNanos = 0L;
    private void dev58g11r3ReportIfDue() {
        final long now = System.nanoTime();
        if (dev58g11r3LastStatsNanos != 0L &&
                now - dev58g11r3LastStatsNanos < 10_000_000_000L) return;
        dev58g11r3LastStatsNanos = now;
        LOGGER.info((dev58g11r7PilotMesh(source.getPath()) ? "[DEV58G11R7-STATS]" :
                    dev58g11r6PilotMesh(source.getPath()) ? "[DEV58G11R6-STATS]" :
                    dev58g11r5PilotMesh(source.getPath()) ? "[DEV58G11R5-STATS]" :
                    dev58g11r4PilotMesh(source.getPath()) ? "[DEV58G11R4-STATS]" :
                    "[DEV58G11R3-STATS]") +
                " mesh={} gpuDraws={} uploads={} " +
                "cacheHits={} cacheMisses={} cpuFallbacks={} evictions={} " +
                "throttled={} residentVariants={} staticChunks={}",
                source, dev58g11Draws, dev58g11Uploads,
                dev58g11r3CacheHits, dev58g11r3CacheMisses,
                dev58g11r3CpuFallbacks, dev58g11r2Evictions,
                dev58g11r2Throttled, dev58g11Variants.size(),
                dev58g9EligibleStaticChunks.cardinality());
    }
    private long dev58g11r2WindowStarted = 0L;
    private int dev58g11r2WindowUploads = 0;
    private boolean dev58g11r2MemoryHalt = false;
    private long dev58g11r2MemoryChecked = 0L;
    private static final int DEV58G11R2_MAX_UPLOADS_PER_10S = 6;
    // The source mesh is shared by all visible B765s. A scratch builder avoids
    // repeated ~1 MiB direct-buffer allocations when a new pose is encountered.
    private com.mojang.blaze3d.vertex.BufferBuilder dev58g11r2UploadScratch;
    // Resource-replacement cleanup, only for the one experimental B765 model.
    private static LegacyStaticMesh dev58g11r2LastLoadedB765;
    // Exact two-model resource-reload ownership, not a permanent per-world cache.
    private static final java.util.Map<String,LegacyStaticMesh> dev58g11r4LastLoaded =
            new java.util.HashMap<>();

    private static long dev58g11r2PrivateCommit() {
        var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean win)
            return win.getCommittedVirtualMemorySize();
        return -1L;
    }
    private void dev58g11r2ReleaseGpu(String reason) {
        // Called only by the client render thread, never from a Cleaner/worker.
        for(var entry : dev58g11Variants) {
            try {entry.buffer().close();}catch(Exception ex) {
                LOGGER.warn("[DEV58G11R2-CLOSE-WARN] mesh={} reason={}",source,reason,ex);
            }
        }
        dev58g11Variants.clear();
        dev58g11ActiveBuffer=null;
        if(dev58g9GpuBuffer!=null) {
            try {dev58g9GpuBuffer.close();} catch(Exception ex) {
                LOGGER.warn("[DEV58G11R2-CLOSE-WARN] G9 proof buffer mesh={}",source,ex);
            }
            dev58g9GpuBuffer=null;
        }
        if(dev58g9s2DrawBuffer!=null) {
            try {dev58g9s2DrawBuffer.close();}catch(Exception ignored){}
            dev58g9s2DrawBuffer=null;
        }
        dev58g11r2UploadScratch=null;
        dev58g11r1PacketScratch=null;
        dev58g8r1Packets.clear();
        LOGGER.info("[DEV58G11R2-RELEASE] mesh={} reason={} evictions={} uploads={}",
                source,reason,dev58g11r2Evictions,dev58g11Uploads);
    }
    private boolean dev58g11r2MemorySafe() {
        if(dev58g11r2MemoryHalt || dev58g11Disabled)return false;
        final long now=System.nanoTime();
        if (now-dev58g11r2MemoryChecked >= 3_000_000_000L) {
            dev58g11r2MemoryChecked=now;
            long committed=dev58g11r2PrivateCommit();
            // Guard actual Java-process private commit, not system-wide Commit.
            if (committed >= 16L*1024L*1024L*1024L) {
                dev58g11r2MemoryHalt=true;
                dev58g11Disabled=true;
                dev58g11r2ReleaseGpu("PRIVATE_COMMIT_16_GIB");
                LOGGER.error("[DEV58G11R2-MEMORY-HALT] privateCommitGiB={} GPU drawing " +
                        "disabled until restart; original CPU path retained",
                        committed/(1024.0*1024.0*1024.0));
                return false;
            }
        }
        return true;
    }
    private boolean dev58g11r2AllowUpload() {
        if(!dev58g11r2MemorySafe())return false;
        final long now=System.nanoTime();
        if (dev58g11r2WindowStarted==0L ||
                now-dev58g11r2WindowStarted >= 10_000_000_000L) {
            dev58g11r2WindowStarted=now;
            dev58g11r2WindowUploads=0;
        }
        if(dev58g11r2MemoryHalt)return false;
        if(dev58g11r2WindowUploads>=DEV58G11R2_MAX_UPLOADS_PER_10S) {
            dev58g11r2Throttled++;
            if(dev58g11r2Throttled==1 || dev58g11r2Throttled%204096L==0L)
                LOGGER.warn("[DEV58G11R2-THROTTLE] CPU fallback (original uncached) " +
                        "uploadsThisWindow={} heldVariants={} count={}",
                        dev58g11r2WindowUploads,dev58g11Variants.size(),dev58g11r2Throttled);
            return false;
        }
        return true;
    }
    private com.mojang.blaze3d.vertex.VertexBuffer dev58g11ActiveBuffer;
    private boolean dev58g11Disabled = false, dev58g11VariantCapReported = false;
    private long dev58g11Draws = 0L, dev58g11Uploads = 0L, dev58g11FastSkips = 0L;
    private static final int DEV58G11_MAX_VARIANTS = 12; // R2 hard memory ceiling
    private static final java.util.Set<String> DEV58G11_ANNOUNCED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private boolean dev58g11PrepareGpu(PoseStack.Pose pose, int light, int overlay) {
        dev58g11ActiveBuffer = null;
        if(!dev58g11GpuMesh(this.source.getPath()) || dev58g11Disabled ||
                dev58g9GpuBuffer == null || dev58g9EligibleStaticChunks.cardinality() < 8)
            return false;
        // Check even on cache HITS, not only when uploading fresh geometry.
        if(!dev58g11r2MemorySafe())return false;
        for(var entry : dev58g11Variants) {
            if(entry.light()==light && entry.overlay()==overlay &&
                    !entry.buffer().isInvalid()) {
                dev58g11r3CacheHits++;
                dev58g11ActiveBuffer = entry.buffer();
                // Access-order for true bounded eviction; no shader/light approximation.
                dev58g11Variants.remove(entry);
                dev58g11Variants.add(entry);
                return true;
            }
        }
        dev58g11r3CacheMisses++;
        if(!dev58g11r2AllowUpload()) return false;
        com.mojang.blaze3d.vertex.VertexBuffer next = null;
        try {
            var fmt = com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY;
            var b = dev58g11r2UploadScratch;
            if(b==null) {
                b=new com.mojang.blaze3d.vertex.BufferBuilder(Math.max(1024,
                        dev58g9EligibleStaticChunks.cardinality()*24*fmt.getVertexSize()));
                dev58g11r2UploadScratch=b;
            }
            if(b.building())throw new IllegalStateException("R2 upload scratch nested");
            b.clear();
            b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,fmt);
            int emitted = 0;
            for(int ch=dev58g9EligibleStaticChunks.nextSetBit(0);ch>=0;
                    ch=dev58g9EligibleStaticChunks.nextSetBit(ch+1)) {
                int start=ch*24*FLOATS_PER_VERTEX;
                if(start<0 || start+24*FLOATS_PER_VERTEX>vertices.length)
                    throw new IllegalStateException("Pilot certified chunk out of mesh");
                for(int k=0;k<24;k++) {
                    int i=start+k*FLOATS_PER_VERTEX;
                    b.vertex(vertices[i],vertices[i+1],vertices[i+2])
                            .color(255,255,255,255)
                            .uv(vertices[i+3],vertices[i+4])
                            .overlayCoords(overlay).uv2(light)
                            .normal(vertices[i+5],vertices[i+6],vertices[i+7])
                            .endVertex();
                    emitted++;
                }
            }
            var ready=b.end();
            try {
                next = new com.mojang.blaze3d.vertex.VertexBuffer(
                        com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                next.bind();next.upload(ready);
            } finally {
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                if(b.building())b.discard();
                b.clear();
            }
            if(next.isInvalid())throw new IllegalStateException("Pilot GPU buffer invalid");
            if(dev58g11Variants.size()>=DEV58G11_MAX_VARIANTS) {
                // Only evict AFTER a successful replacement upload; never draw
                // a closed buffer. Close oldest on the render thread.
                var oldest=dev58g11Variants.remove(0);
                oldest.buffer().close();
                dev58g11r2Evictions++;
                if(dev58g11r2Evictions==1 || dev58g11r2Evictions%64L==0L)
                    LOGGER.info("[DEV58G11R2-EVICT] model={} evictions={} maxVariants={}",
                            source,dev58g11r2Evictions,DEV58G11_MAX_VARIANTS);
            }
            dev58g11Variants.add(new Dev58G11Variant(light, overlay, next));
            dev58g11r2WindowUploads++;
            dev58g11ActiveBuffer = next;
            dev58g11Uploads++;
            if(dev58g11Uploads <= 3)
                LOGGER.info("[DEV58G11-UPLOAD] mesh={} staticChunks={} vertices={} " +
                        "variant={} light={} normals=LOCAL shaderLights=PER_DRAW immutableUntilResourceReset=true",
                        source,dev58g9EligibleStaticChunks.cardinality(),emitted,
                        dev58g11Variants.size(),light);
            next=null;
            return true;
        } catch(Exception ex) {
            if(next!=null)try{next.close();}catch(Exception ignored){}
            dev58g11Disabled=true;
            LOGGER.error("[DEV58G11R2-SAFETY-HOLD] Failed GPU variant upload; " +
                    "preserving original CPU draw mesh={}",source,ex);
            return false;
        }
    }

    // The vanilla entity shader computes diffuse light in its vertex shader
    // using Light0_Direction / Light1_Direction dot Normal. The normal transform
    // formerly happened on the CPU at EVERY GPU upload. Move it to two uniforms
    // per VBO draw: (M*n) dot L == n dot (transpose(M)*L).
    // Always restore RenderSystem's shared shader lights, even on GL exceptions.
    private static org.joml.Vector3f dev58g11r3ReadShaderLight(
            com.mojang.blaze3d.shaders.Uniform u) {
        if (u == null || u.getFloatBuffer() == null ||
                u.getFloatBuffer().capacity() < 3)
            throw new IllegalStateException("Entity diffuse shader light uniform missing");
        var f = u.getFloatBuffer();
        return new org.joml.Vector3f(f.get(0), f.get(1), f.get(2));
    }

    private void dev58g9s2RestoreSkipped(com.mojang.blaze3d.vertex.VertexConsumer consumer,
                    PoseStack.Pose pose,int light,int overlay) {
        if(!(consumer instanceof com.mojang.blaze3d.vertex.BufferBuilder b)||!b.building())
            throw new IllegalStateException("CPU backup buffer not writable");
        for(int ch=dev58g9s2Skipped.nextSetBit(0);ch>=0;ch=dev58g9s2Skipped.nextSetBit(ch+1)) {
            java.nio.ByteBuffer packet=dev58g8r1StaticPacket(ch,pose,light,overlay);
            if(packet==null)throw new IllegalStateException("missing CPU fallback packet "+ch);
            b.putBulkData(packet);
            if(DEV58G10_METRICS_ON) dev58g10RestoredVertices+=24L;
        }
    }

    // Required by the real VBO pilot. All non-MILW and nonstandard buffer
    // sources call the original renderer unchanged. We flush the pending
    // entity RenderType before the GPU draw and restore its state afterward.
    public void dev58g9s2Render(PoseStack stack,
            net.minecraft.client.renderer.MultiBufferSource buffers,
            net.minecraft.client.renderer.RenderType type,
            int light,int overlay) {
        var out=buffers.getBuffer(type);
        final boolean dev58g11Model=dev58g11GpuMesh(this.source.getPath());
        boolean capable=out instanceof com.mojang.blaze3d.vertex.BufferBuilder b && b.building() &&
                buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource &&
                (dev58g11Model ? dev58g11PrepareGpu(stack.last(),light,overlay) :
                                   dev58g9s2PrepareGpu(stack.last(),light,overlay));
        if(!capable){
            if(dev58g11Model) {
                dev58g11r3CpuFallbacks++;
                dev58g11r3ReportIfDue();
            }
            render(stack,out,light,overlay);
            return;
        }
        if(dev58g11Model)dev58g11r3ReportIfDue();
        dev58g9s2Skipped.clear();
        dev58g9s2SkipThisRender=true;
        try {render(stack,out,light,overlay);}
        finally {dev58g9s2SkipThisRender=false;}
        if(!dev58g9s2Skipped.equals(dev58g9EligibleStaticChunks)) {
            dev58g9s2Fallbacks++;
            dev58g9s2RestoreSkipped(out,stack.last(),light,overlay);
            if(dev58g9s2Fallbacks<=3)
                LOGGER.warn("[DEV58G9S2-FALLBACK] model={} currentSkipped={} certified={} reason=dynamic-or-hidden-chunk",
                    source,dev58g9s2Skipped.cardinality(),dev58g9EligibleStaticChunks.cardinality());
            return;
        }
        boolean stateActive=false;
        try {
            // Direct VBO calls must reproduce vanilla entity render state.
            // NOTE: this is a PILOT, not a released translucent sorter.
            ((net.minecraft.client.renderer.MultiBufferSource.BufferSource)buffers).endBatch(type);
            type.setupRenderState();stateActive=true;
            var shader=com.mojang.blaze3d.systems.RenderSystem.getShader();
            if(shader==null)throw new IllegalStateException("missing translucent entity shader");
            var modelView=new org.joml.Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
            final com.mojang.blaze3d.vertex.VertexBuffer activeGpu =
                    dev58g11Model ? dev58g11ActiveBuffer : dev58g9s2DrawBuffer;
            if(activeGpu==null || activeGpu.isInvalid())
                throw new IllegalStateException("missing certified GPU draw buffer");
            activeGpu.bind();
            if(dev58g11Model) {
                // Capture the EXACT current vanilla entity light directions;
                // never use fixed guessed lighting, even in night/other dimensions.
                com.mojang.blaze3d.systems.RenderSystem.setupShaderLights(shader);
                var original0=dev58g11r3ReadShaderLight(shader.LIGHT0_DIRECTION);
                var original1=dev58g11r3ReadShaderLight(shader.LIGHT1_DIRECTION);
                var normalTranspose=new org.joml.Matrix3f(stack.last().normal()).transpose();
                var local0=new org.joml.Vector3f(original0).mul(normalTranspose);
                var local1=new org.joml.Vector3f(original1).mul(normalTranspose);
                try {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderLights(local0,local1);
                    activeGpu.drawWithShader(modelView,
                        com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),shader);
                } finally {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderLights(original0,original1);
                }
            } else {
                activeGpu.drawWithShader(modelView,
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),shader);
            }
            if(dev58g11Model) {
                dev58g11Draws++;
                if(dev58g11Draws==1 || dev58g11Draws%204096L==0L)
                    LOGGER.info((dev58g11r10PilotMesh(source.getPath()) ? "[DEV58G11R10-DRAW]" :
                                dev58g11r9PilotMesh(source.getPath()) ? "[DEV58G11R9-DRAW]" :
                                dev58g11r8PilotMesh(source.getPath()) ? "[DEV58G11R8-DRAW]" :
                                dev58g11r7PilotMesh(source.getPath()) ? "[DEV58G11R7-DRAW]" :
                                dev58g11r6PilotMesh(source.getPath()) ? "[DEV58G11R6-DRAW]" :
                                dev58g11r5PilotMesh(source.getPath()) ? "[DEV58G11R5-DRAW]" :
                                dev58g11r4PilotMesh(source.getPath()) ? "[DEV58G11R4-DRAW]" :
                                "[DEV58G11-B765-DRAW]") +
                            " mesh={} draws={} uploads={} " +
                            "fastSkippedVertices={} lightVariants={} evictions={} throttled={} " +
                            "fallbacks={} staticChunks={} dynamicVertices=ORIGINAL",
                            source,dev58g11Draws,dev58g11Uploads,dev58g11FastSkips,
                            dev58g11Variants.size(),dev58g11r2Evictions,dev58g11r2Throttled,
                            dev58g9s2Fallbacks,dev58g9EligibleStaticChunks.cardinality());
            } else {
            dev58g9s2Draws++;
            if(dev58g9s2Draws==1||dev58g9s2Draws%204096L==0L)
                LOGGER.info("[DEV58G9S2-GPU-DRAW] model={} draws={} uploads={} fallbacks={} staticChunks={} shader={} light={} textured=true",
                    source,dev58g9s2Draws,dev58g9s2Uploads,dev58g9s2Fallbacks,
                    dev58g9EligibleStaticChunks.cardinality(),shader.getName(),light);
            }
        }catch(Exception ex) {
            if(dev58g11Model) {
                dev58g11Disabled=true;
                LOGGER.error("[DEV58G11-SAFETY-HOLD] GPU draw failed; " +
                        "GPU pilot uses CPU on subsequent frames",ex);
            } else {
                dev58g9s2Disabled=true;
                LOGGER.error("[DEV58G9S2-SAFETY-HOLD] GPU draw failed; restore CPU chunks after flush",ex);
            }
            // After endBatch(), its old builder may have been finalized. Get a
            // fresh buffer and reissue the exact original cached packets.
            var fallback=buffers.getBuffer(type);
            dev58g9s2RestoreSkipped(fallback,stack.last(),light,overlay);
        }finally {
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            if(stateActive)type.clearRenderState();
            dev58g9s2Skipped.clear();
        }
    }
    // DEV54H8D: opt-in clean-slate ORIGINAL running-gear render mask.
    // This does not delete/modify TCM and does not mask H7 accepted wheel faces.
    // Purpose: isolate imported rods/pins/crossheads while replacement running
    // gear is rebuilt as separate native dynamic geometry in a later patch.
    // Only source-authenticated Berkshire 1225 with H7 14 driver faces active.
    private static final java.util.Set<Integer> DEV54H8D_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h8dEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H8D-BERKSHIRE-1225-CLEAN-SLATE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                            .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    private static boolean dev54h8dImportedRunningGear(int ch) {
        // Exact 41 original TCM chunk IDs derived from the SHA-frozen
        // G2 1169-chunk wheel/rod audit, not proximity or color heuristics.
        // Rigid coupling and hardware, bilateral main rods, bilateral
        // crossheads, two old piston stems and one dangling legacy guide.
        return (ch>=348 && ch<=360) || (ch>=734 && ch<=746)
                || (ch>=373 && ch<=375) || (ch>=731 && ch<=733)
                || ch==378 || ch==747 || ch==379 || ch==380
                || ch==389 || ch==390 || ch==372 || ch==730 || ch==391;
    }
    // DEV54H8E: explicit 2026-10-16 Library viewer export, ONLY original
    // Berkshire1225 rod/crank pieces missed by H8D. Fixed cylinder housings,
    // 11 viewer-marked piston chunks and 1 viewer-marked other chunk are kept.
    // Source CSV SHA256 AB0E1AAC04F9FD1BA73CDF737CDFB26F06D9356279BE1E9B15F9B0C02A7DCDA8.
    // This opt-in EXTENDS the 41 existing H8D mask IDs; it doesn't replace them.
    private static final java.util.Set<Integer> DEV54H8E_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h8eEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H8E-BERKSHIRE-1225-VIEWER-MASK-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                            .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    private static boolean dev54h8eExtraOldRodsOrCranks(int ch) {
        // Exactly the 34 additional DEV44-Verified-Mesh-Parts (4).csv row IDs.
        // Only rod/crank tags; never the 11 piston tags or chunk 8=other.
        return switch(ch) {
            case 490, 491, 492, 1008, 1012, 1013, 1014, 1018 -> true;
            case 1019, 1020, 1022, 1023, 1056, 1057, 1061, 1062 -> true;
            case 1063, 1138, 1139, 1140, 1141, 1142, 1143, 1144 -> true;
            case 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1166 -> true;
            case 1167, 1168 -> true;
            default -> false;
        };
    }

// DEV54H8F: clean-slate Berkshire 1225 fresh-linkage visibility layer.
// H8F does NOT restore the full imported running gear. It only re-shows
// the exact H8A/H8B source chunks needed for a simple fresh rebuild look:
// 4-axle coupling rods, bilateral main rods and bilateral crosshead sliders.
// Legacy pin fragments, old piston stems, dangling guides and all H8E
// viewer-tagged extra rod/crank clutter remain masked.
private static final java.util.Set<Integer> DEV54H8F_REPORT =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
private static boolean dev54h8fEnabled() {
    try {
        var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H8F-BERKSHIRE-1225-FRESH-LINKAGE-ON.txt");
        return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("﻿", "").trim().equals("ON");
    } catch(Exception ignored) { return false; }
}
private static boolean dev54h8fFreshRunningGearChunk(int ch) {
    // Fresh visible set = H8A coupling rods + H8B bilateral main rods and
    // slider crossheads only. All 7 remaining H8D chunks stay hidden.
    return (ch>=348 && ch<=360) || (ch>=734 && ch<=746)
            || (ch>=373 && ch<=375) || (ch>=731 && ch<=733)
            || ch==378 || ch==747;
}
    // DEV54H8B: Berkshire 1225 bilateral main-rod / crosshead slider-crank PILOT.
    // Source TCM SHA-locked and OFF by default. Never moves cylinder guide shells.
    // Main rod chunk 378 (+Z), 747 (-Z), with a candidate crankpin at its
    // original lower-X vertex neighborhood and crosshead at original upper-X.
    // Independent original crosshead boxes: 373..375 (+Z), 731..733 (-Z).
    // Hardware is not semantically labeled in TCM: user visual verification
    // REQUIRED before calling these physical bearings mechanically accepted.
    private static final java.util.Set<Integer> DEV54H8B_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h8bEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H8B-BERKSHIRE-1225-SLIDER-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                         .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    private static boolean dev54h8bChunkCenterMatches(
            float[] v,int stride,int ch,float cx,float cy,float cz,float tol) {
        int start=ch*24*stride;
        if(start<0||start+24*stride>v.length)return false;
        float[] lo={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] hi={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int k=0;k<24;k++){
            int i=start+k*stride;
            for(int a=0;a<3;a++) {
                lo[a]=Math.min(lo[a],v[i+a]); hi[a]=Math.max(hi[a],v[i+a]);
            }
        }
        return Math.abs((lo[0]+hi[0])*0.5f-cx)<tol
                && Math.abs((lo[1]+hi[1])*0.5f-cy)<tol
                && Math.abs((lo[2]+hi[2])*0.5f-cz)<tol;
    }
    private record Dev54H8BLink(Dev44RigidLinkage.Pose pose,float[] restA,float stroke,float residual) {}
    private static Dev54H8BLink dev54h8bSolve(
            String meshKey,float[] vertices,int stride,boolean positiveZ) {
        final int rod=positiveZ?378:747,wi=positiveZ?12:15;
        final float rodZ=positiveZ?0.40625f:-0.521875f;
        final float crossZ=positiveZ?0.46875f:-0.59375f;
        final float tolerance=0.0010f;
        if(!dev54h8bChunkCenterMatches(vertices,stride,rod,
                1.125f,-0.53125f,rodZ,tolerance))return null;
        final int[] cs=positiveZ?new int[]{373,374,375}:new int[]{731,732,733};
        final float[] cxs={1.953125f,1.9765625f,1.984375f};
        final float[] cys={-0.640625f,-0.5859375f,-0.546875f};
        for(int i=0;i<3;i++) if(!dev54h8bChunkCenterMatches(
                vertices,stride,cs[i],cxs[i],cys[i],crossZ,tolerance))return null;
        var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                meshKey,wi,vertices,stride);
        if(w==null || !w.rotates() || w.axis()!=2 ||
                Math.abs(w.centerX()-0.3125f)>tolerance ||
                Math.abs(w.centerY()+0.5625f)>tolerance ||
                Math.abs(w.centerZ()-(positiveZ?0.31313f:-0.43813f))>tolerance ||
                !Float.isFinite(w.cos()) || !Float.isFinite(w.sin()))return null;
        // Authored MAIN ROD endpoints inferred from its two Y/X endpoint boxes.
        // Candidate crank A rotates about axle #2; crosshead B follows original
        // Y guide, so rod length is exactly conserved (no stretching/skew).
        final float[] a={0.296875f,-0.46875f,rodZ};
        final float[] b={1.953125f,-0.59375f,rodZ};
        final float[] center={w.centerX(),w.centerY(),w.centerZ()};
        float[] da=Dev44RigidLinkage.wheelPoint(a,center,w.cos(),w.sin(),2);
        final double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
        final double dy=b[1]-da[1];
        final double discriminant=length*length-dy*dy;
        if(!Double.isFinite(discriminant)||discriminant<=0)return null;
        // Rightward branch is source-rest-authenticated, no branch jumping.
        final float slideX=(float)(da[0]+Math.sqrt(discriminant));
        final float stroke=slideX-b[0];
        if(!Float.isFinite(stroke)||Math.abs(stroke)>0.21f)return null;
        float[] db={slideX,b[1],rodZ};
        var pose=Dev44RigidLinkage.solve(a,b,da,db,2,tolerance);
        if(pose==null || !Float.isFinite(pose.cos) || !Float.isFinite(pose.sin))return null;
        float[] check=pose.point(b[0]-a[0],b[1]-a[1],b[2]-a[2]);
        float residual=(float)Math.hypot(check[0]-db[0],check[1]-db[1]);
        if(!Float.isFinite(residual)||residual>tolerance||Math.abs(check[2]-rodZ)>tolerance)return null;
        float phase=(float)Math.atan2(w.sin(),w.cos());
        int quadrant=Math.max(0,Math.min(3,(int)Math.floor((phase+Math.PI)/(Math.PI*0.5))));
        if(DEV54H8B_REPORT.add((positiveZ?0:10)+quadrant))LOGGER.info(
              "[DEV54H8B-SLIDER] side={} rodChunk={} wheelChunk={} quad={} wheelPhase={} "
              +"crossheadChunks=3 guideY=-0.59375 stroke={} rodLength={} closureError={} "
              +"rigid=true shell=FIXED joint=AUTHORED_GEOMETRY_CANDIDATE",
              positiveZ?"POS_Z":"NEG_Z",rod,wi,quadrant,phase,stroke,length,residual);
        return new Dev54H8BLink(pose,a,stroke,residual);
    }
    // DEV54H8C: opt-in exact-TCM PIN HARDWARE continuity, Berkshire 1225 only.
    // 379/389/390 are positive-side counterparts of NEG_Z 741/742/743,
    // already animated in accepted H8A. 380 is a crosshead-end cap whose
    // authored Y/X coordinates match the H8B moving crosshead assembly.
    // This stage does NOT move a piston shaft, fixed cylinder, rod, or wheel.
    private static final java.util.Set<Integer> DEV54H8C_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h8cEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H8C-BERKSHIRE-1225-PIN-HARDWARE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    private static boolean dev54h8cPinGuard(float[] verts,int stride) {
        int[] chunks={379,389,390,380,741,742,743};
        float[][] c={{.3125f,-.46875f,.428125f},
                     {.2546875f,-.46875f,.534375f},
                     {.3703125f,-.46875f,.534375f},
                     {1.984375f,-.59375f,.415625f},
                     {.3125f,-.46875f,-.553125f},
                     {.2546875f,-.46875f,-.659375f},
                     {.3703125f,-.46875f,-.659375f}};
        for(int j=0;j<chunks.length;j++)
            if(!dev54h8bChunkCenterMatches(verts,stride,chunks[j],
                    c[j][0],c[j][1],c[j][2],.001f))return false;
        return true;
    }
    // DEV54H8A: rigid bilateral Berkshire 1225 FOUR-AXLE coupling rod proof.
    // Only original authored rod groups 348..360 (+Z) and 734..746 (-Z).
    // Every pin inherits its OWN accepted driver wheel phase; the rigid rod is
    // allowed to move ONLY if all four driver pins agree on one translation.
    // No guessed piston pin, slider, mains, wheel position, or TCM edit.
    private static final java.util.Set<Integer> DEV54H8A_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h8aEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H8A-BERKSHIRE-1225-COUPLING-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                         .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    private static Dev44RigidLinkage.Pose dev54h8aSolve(
            String meshKey,float[] vertices,int stride,boolean positiveZ) {
        final int[] wheels=positiveZ?new int[]{13,12,11,10}:new int[]{14,15,16,17};
        final float[] axleX={-0.5f,0.3125f,1.125f,1.9375f};
        final float rodZ=positiveZ?0.34375f:-0.459375f;
        final float wheelZ=positiveZ?0.313125f:-0.438125f;
        final float wheelY=-0.5625f, pinRestY=-0.46875f;
        // Authored rod center line is exactly 0.09375 above the four axle centers.
        // These are PIN CANDIDATES, NOT independently authenticated metal-pin vertices.
        final float eps=0.0010f;
        float[] firstRest=null,firstMoved=null,lastRest=null,lastMoved=null;
        float baseDx=Float.NaN,baseDy=Float.NaN,phase=Float.NaN;
        for(int j=0;j<wheels.length;j++) {
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                    meshKey,wheels[j],vertices,stride);
            if(w==null || !w.rotates() || w.axis()!=2
                    || Math.abs(w.centerX()-axleX[j])>eps
                    || Math.abs(w.centerY()-wheelY)>eps
                    || Math.abs(w.centerZ()-wheelZ)>eps
                    || !Float.isFinite(w.cos()) || !Float.isFinite(w.sin())) return null;
            float[] rest={axleX[j],pinRestY,rodZ};
            float[] center={w.centerX(),w.centerY(),w.centerZ()};
            float[] moved=Dev44RigidLinkage.wheelPoint(rest,center,w.cos(),w.sin(),2);
            float dx=moved[0]-rest[0],dy=moved[1]-rest[1];
            if(!Float.isFinite(dx)||!Float.isFinite(dy)||Math.abs(moved[2]-rodZ)>eps)return null;
            if(j==0){baseDx=dx;baseDy=dy;firstRest=rest;firstMoved=moved;
                phase=(float)Math.atan2(w.sin(),w.cos());}
            else if(Math.abs(dx-baseDx)>eps||Math.abs(dy-baseDy)>eps)return null;
            if(j==wheels.length-1){lastRest=rest;lastMoved=moved;}
        }
        // All four crankpins must translate coherently; two endpoint solve
        // has zero stretch and preserves every original rod and cap vertex.
        var pose=Dev44RigidLinkage.solve(firstRest,lastRest,firstMoved,lastMoved,2,eps);
        if(pose==null || Math.abs(pose.cos-1.0f)>eps || Math.abs(pose.sin)>eps)return null;
        int quadrant=(int)Math.floor((phase+Math.PI)/(Math.PI*0.5));
        quadrant=Math.max(0,Math.min(3,quadrant));
        int k=(positiveZ?0:10)+quadrant;
        if(DEV54H8A_REPORT.add(k))LOGGER.info(
              "[DEV54H8A-COUPLING] side={} quad={} phase={} wheels=4 rodChunks=13 radius=0.09375 "
              +"pinClosureTol={} rigid=true centerDistance=2.4375 piston=UNTOUCHED pins=CANDIDATE",
              positiveZ?"POS_Z":"NEG_Z",quadrant,phase,eps);
        return pose;
    }
    // DEV54H9A: Berkshire 765 separate original TCM + 83 verified viewer IDs.
    // Original wheels and body preserved. No 1225 source/model/selector changed.
    // Curated original 34 chunk rods animate with accepted H7 axle phase;
    // remaining 38 of 72 DEV44 viewer-classified rod/crank chunks hidden.
    private static final java.util.Set<Integer> DEV54H9A_REPORT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> DEV54H9A_COUPLING_REPORT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> DEV54H9A_SLIDER_REPORT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h9aEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H9A-BERKSHIRE-765-LINKAGE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                            .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }
    // DEV54H9B: strictly 765-only opt-in suspension of the older DEV44 R13w
    // demonstration poses. Those older debug experiments pre-populate the same
    // r13uPoses/r13oOffsets chunks H9A needs and force H9A into its HOLD gate.
    // No legacy selector files are deleted or modified; turning H9B OFF restores
    // the exact old behavior without altering H9A's source/geometry/mesh tags.
    private static final java.util.Set<Integer> DEV54H9B_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h9bEnabled() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H9B-BERKSHIRE-765-LEGACY-BYPASS-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                            .replace("\uFEFF", "").trim().equals("ON");
        } catch(Exception ignored) { return false; }
    }

    // DEV54H10A: exact DEV44 viewer-labeled A4 Mallard / King Class COUPLING ONLY.
    // 1225+765 have no changes. Original TCM, wheel phases and fixed cylinder stay frozen.
    private static final java.util.Set<Integer> DEV54H10A_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();


    // DEV54H12B: four-model source-qualified one-batch pilot. Always OFF
    // unless exact opt-in marker names the model. Model assets stay byte-perfect.
    private static final java.util.Set<Integer> DEV54H12B_LOCOMOTIVE_STEAM_4_4_0 = java.util.Set.of(62,63,64,65,66,67,81);
    private static final java.util.Set<Integer> DEV54H12B_LOCOMOTIVE_STEAM_ADLER = java.util.Set.of(68,71,145,146,252,253);
    private static final java.util.Set<Integer> DEV54H12B_LOCOMOTIVE_STEAM_ALCO_SC4 = java.util.Set.of(31,32,33,34,35,36,37,38,39,40,41,42,45,46,47,342,343,344,345,346,347,348,349,350,351,352,353,354,355,356,357,358,359,360,361,362,548,549,550,551,552,553,554,555,556,557,558,559,560,561,562,563,564,565,566,567,568,569,570,571,572,573,574,575,576,614,615,616,617,618,619,620,621,622,623);
    private static final java.util.Set<Integer> DEV54H12B_LOCOMOTIVE_STEAM_BR_BLACK_5 = java.util.Set.of(204,205,206,211,212,213,214,215,217,218,219,220,222,223,224,225,226,237,238,239,244,245,246,247,248,250,251,252,253,255,256,257,258,259);
    private static final java.util.Set<Integer> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_4_4_0 = java.util.Set.of(65,67);
    private static final java.util.Set<Integer> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_ADLER = java.util.Set.of(68,71,145,146,252,253);
    private static final java.util.Set<Integer> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_ALCO_SC4 = java.util.Set.of(31,39,346,347,348,358,359,360);
    private static final java.util.Set<Integer> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_BR_BLACK_5 = java.util.Set.of(211,212,213,214,244,245,246,247);
    private static final java.util.Set<String> DEV54H12B_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h12bSelected(String path) {
        String id;
        if(path.equals("legacy_meshes/locomotive_steam_4_4_0.tcm"))id="locomotive_steam_4_4_0";
        else if(path.equals("legacy_meshes/locomotive_steam_adler.tcm"))id="locomotive_steam_adler";
        else if(path.equals("legacy_meshes/locomotive_steam_alco_sc4.tcm"))id="locomotive_steam_alco_sc4";
        else if(path.equals("legacy_meshes/locomotive_steam_br_black_5.tcm"))id="locomotive_steam_br_black_5";
        else return false;
        try {
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                  .resolve("DEV54H12B-BATCH1-REPAIRS-ON.txt");
            if(!java.nio.file.Files.isRegularFile(f))return false;
            for(String l:java.nio.file.Files.readAllLines(f,java.nio.charset.StandardCharsets.UTF_8))
                if(l.replace("\uFEFF","").trim().equals(id))return true;
        }catch(Exception ignored){}
        return false;
    }
    private static java.util.Set<Integer> dev54h12bTags(String path) {
        return switch(path) {
            case "legacy_meshes/locomotive_steam_4_4_0.tcm" -> DEV54H12B_LOCOMOTIVE_STEAM_4_4_0;
            case "legacy_meshes/locomotive_steam_adler.tcm" -> DEV54H12B_LOCOMOTIVE_STEAM_ADLER;
            case "legacy_meshes/locomotive_steam_alco_sc4.tcm" -> DEV54H12B_LOCOMOTIVE_STEAM_ALCO_SC4;
            case "legacy_meshes/locomotive_steam_br_black_5.tcm" -> DEV54H12B_LOCOMOTIVE_STEAM_BR_BLACK_5;
            default -> java.util.Set.of();
        };
    }
    private static java.util.Set<Integer> dev54h12bVisible(String path) {
        return switch(path) {
            case "legacy_meshes/locomotive_steam_4_4_0.tcm" -> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_4_4_0;
            case "legacy_meshes/locomotive_steam_adler.tcm" -> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_ADLER;
            case "legacy_meshes/locomotive_steam_alco_sc4.tcm" -> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_ALCO_SC4;
            case "legacy_meshes/locomotive_steam_br_black_5.tcm" -> DEV54H12B_SHOW_LOCOMOTIVE_STEAM_BR_BLACK_5;
            default -> java.util.Set.of();
        };
    }
    // Axis-2 driving cranks: source-rest eccentric displacement at the end of
    // a rigid bar, so both ends move together without stretching its vertices.
    private static float[] dev54h12bDrive(float phase,float rodY,float axleY) {
        float r=rodY-axleY;
        return new float[]{-r*(float)Math.sin(phase),r*((float)Math.cos(phase)-1f),0f};
    }
    // Axis-0 connecting bars on historical models run lengthwise in Z.
    private static float[] dev54h12bOldAxis(float phase,float radius) {
        return new float[]{0f,radius*((float)Math.cos(phase)-1f),radius*(float)Math.sin(phase)};
    }
    private static boolean dev54h12bPivotGuard(String meshKey,float[] vertices,int stride,
                                                String id) {
        int[] checks;
        if(id.equals("locomotive_steam_4_4_0"))checks=new int[]{20,21,24,25};
        else if(id.equals("locomotive_steam_adler"))checks=new int[]{0,80,27,36,105,122};
        else if(id.equals("locomotive_steam_alco_sc4"))checks=new int[]{0,204,205,206,207,208,209,210,1,2};
        else checks=new int[]{0,1,2,3,4,5,6,7,8,9};
        for(int ch:checks) {
            var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
                           .spec(meshKey,ch,vertices,stride);
            if(spec==null || !spec.rotates() || spec.axis()!=(id.equals("locomotive_steam_4_4_0")||id.equals("locomotive_steam_adler")?0:2)
                    || !Float.isFinite(spec.cos()) || !Float.isFinite(spec.sin()))return false;
        }
        return true;
    }
    private static void dev54h12bAddMotion(String id,float phase,java.util.Map<Integer,float[]> offsets){
        if(id.equals("locomotive_steam_4_4_0")) {
            // Paired-wheel geometry has axis X. Only the two source-parallel
            // coupled bars remain; old diagonal rods are masked until pinned.
            offsets.put(65,dev54h12bOldAxis(phase,0.09375f));
            offsets.put(67,dev54h12bOldAxis(phase,0.09375f));
        } else if(id.equals("locomotive_steam_adler")) {
            // Rigid coupling bars plus front/rear authored crank linkage
            // fragments; each side moves off its own tiny wheel pin.
            for(int ch:new int[]{68,71,252})offsets.put(ch,dev54h12bOldAxis(phase,0.0625f));
            for(int ch:new int[]{145,146,253})offsets.put(ch,dev54h12bOldAxis(phase,0.0625f));
        } else if(id.equals("locomotive_steam_alco_sc4")) {
            var neg=dev54h12bDrive(phase,-0.28125f,-0.40625f);
            var pos=dev54h12bDrive(phase,-0.53125f,-0.40625f);
            for(int ch:new int[]{31,346,347,348})offsets.put(ch,neg);
            for(int ch:new int[]{39,358,359,360})offsets.put(ch,pos);
        } else if(id.equals("locomotive_steam_br_black_5")) {
            var neg=dev54h12bDrive(phase,0.228125f,0.09375f);
            var pos=dev54h12bDrive(phase,-0.096875f,0.09375f);
            for(int ch:new int[]{211,212,213,214})offsets.put(ch,neg);
            for(int ch:new int[]{244,245,246,247})offsets.put(ch,pos);
        }
    }


    // DEV54H12D Batch 1 follow-up: exact four-model running-gear corrections.
    // Separate default-OFF selector and no source/model modifications elsewhere.
    private static final java.nio.file.Path DEV54H12D_ON =
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H12D-BATCH1-CORRECTIONS-ON.txt");
    private static final boolean DEV54H12D_ENABLED = dev54h12dMarker();
    private static boolean dev54h12dMarker() {
        try {
            return java.nio.file.Files.isRegularFile(DEV54H12D_ON)
                && java.nio.file.Files.readString(DEV54H12D_ON,java.nio.charset.StandardCharsets.UTF_8)
                   .replace("\uFEFF", "").trim().equals("DEV54H12D-BATCH1-ON-V1");
        } catch (Exception ex) { return false; }
    }
    private static final java.util.Set<String> DEV54H12D_PHASE_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String,Float> DEV54H12D_PHASE_FIRST =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static void dev54h12dPhaseProbe(String id,float phase) {
        // A LIVE log at zero phase alone cannot establish that a wheel moves.
        if(!Float.isFinite(phase))return;
        Float start=DEV54H12D_PHASE_FIRST.putIfAbsent(id,phase);
        if(start!=null && Math.abs((float)Math.sin((phase-start)*0.5f))>0.25f
                && DEV54H12D_PHASE_LOGGED.add(id))
            LOGGER.info("[DEV54H12D-PHASE-ADVANCED] model={} first={} now={} animatedFrameObserved=true visualApproval=STILL_REQUIRED",id,start,phase);
    }
    private static float[] dev54h12dAxialShift(float phase,float restRadius) {
        return new float[]{0f,restRadius*((float)Math.cos(phase)-1f),
                                 restRadius*(float)Math.sin(phase)};
    }
    private static void dev54h12dAddMotion(String id,float phase,
                                          java.util.Map<Integer,float[]> offsets){
        if(id.equals("locomotive_steam_4_4_0")) {
            // Exact source chunks: X+ bar 65 centered on axle Y=.28125
            // despite X- bar 67 being 2px above axle. Rebase X+ so both pins
            // orbit with the same 2px eccentric radius. All wheels untouched.
            float r=0.125f;
            offsets.put(65,new float[]{0f,r*(float)Math.cos(phase),r*(float)Math.sin(phase)});
            offsets.put(67,dev54h12dAxialShift(phase,r));
        } else if(id.equals("locomotive_steam_adler")) {
            // Source wheel pivots differ: main Y=1.094, +Z/-Z trucks Y=1.25.
            // Previous H12B applied identical 1px orbit to all six bars.
            for(int ch:new int[]{68,145})offsets.put(ch,dev54h12dAxialShift(phase,0.12475f));
            for(int ch:new int[]{71,146})offsets.put(ch,dev54h12dAxialShift(phase,-0.115471f));
            for(int ch:new int[]{252,253})offsets.put(ch,dev54h12dAxialShift(phase,-0.141468f));
        } else if(id.equals("locomotive_steam_alco_sc4")) {
            // Preserve the source bar/pin pilot, but the user's picker
            // explicitly excluded original crank 358. Replace its near-end
            // pin location with source chunk 362 at the correct POS_Z axle Y.
            dev54h12bAddMotion(id,phase,offsets);
            offsets.remove(358);
            offsets.put(362,dev54h12bDrive(phase,-0.53125f,-0.40625f));
        } else if(id.equals("locomotive_steam_br_black_5")) {
            // Source near/far bars have unequal rest eccentricity: +.134375
            // vs -.190625. Calibrate POS_Z 0.05625 toward matching amplitude,
            // preserve pin chunks in each original bar assembly.
            var neg=dev54h12bDrive(phase,0.228125f,0.09375f);
            var pos=dev54h12bDrive(phase,-0.040625f,0.09375f);
            pos[1]+=0.05625f;
            for(int ch:new int[]{211,212,213,214})offsets.put(ch,neg);
            for(int ch:new int[]{244,245,246,247})offsets.put(ch,pos);
        }
    }
    private static boolean dev54h12dSourceGate(String meshKey,String id,float[] v){
        if(!DEV54H12D_ENABLED)return false;
        // Prevent wrong selectors or accidental non-fleet mesh use.
        if(!id.equals("locomotive_steam_4_4_0")
            && !id.equals("locomotive_steam_adler")
            && !id.equals("locomotive_steam_alco_sc4")
            && !id.equals("locomotive_steam_br_black_5"))return false;
        if(!meshKey.equals("legacy:traincraft:legacy_meshes/"+id+".tcm"))return false;
        return v!=null && v.length>=24*FLOATS_PER_VERTEX;
    }

    // DEV54H12E — FOUR-ENGINE FOLLOW-UP, default OFF, source-qualified.
    // In the disabled state H12D remains byte-for-byte behaviorally unchanged.
    private static final boolean DEV54H12E_ENABLED = dev54h12eReadMarker();
    private static boolean dev54h12eReadMarker() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H12E-BATCH1-PIVOTS-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                         .replace("\uFEFF", "").trim().equals("DEV54H12E-BATCH1-ON-V1");
        } catch(Exception e) { return false; }
    }
    private static boolean dev54h12eModel(String id) {
        return DEV54H12E_ENABLED && DEV54H12D_ENABLED && (id.equals("locomotive_steam_4_4_0")
                || id.equals("locomotive_steam_adler")
                || id.equals("locomotive_steam_alco_sc4")
                || id.equals("locomotive_steam_br_black_5"));
    }
    private static void dev54h12eMotion(String id, float phase,
                                        java.util.Map<Integer,float[]> offsets) {
        if(id.equals("locomotive_steam_4_4_0")) {
            // 4-4-0 phase is supplied by the authoritative H12A review frame
            // (only during that review) or the normal signed-travel wheel phase.
            // Source bar 65 rests on axle instead of the 2px eccentric of 67.
            final float r=0.125f;
            offsets.put(65,new float[]{0f,r*(float)Math.cos(phase),r*(float)Math.sin(phase)});
            offsets.put(67,dev54h12dAxialShift(phase,r));
        } else if(id.equals("locomotive_steam_adler")) {
            // The long parallel bars 68/145 have verified bilateral symmetry.
            // Unverified diagonal end links 71,146,252,253 remain masked by
            // H12E below, NOT forced to orbit and swing below the locomotive.
            // Main wheel Y=1.094; authored long bar Y=1.21875.
            offsets.keySet().removeAll(java.util.Set.of(71,146,252,253));
            float[] rigid=dev54h12dAxialShift(phase,0.12475f);
            offsets.put(68,rigid); offsets.put(145,rigid);
        } else if(id.equals("locomotive_steam_alco_sc4")) {
            // Retain H12D source pin replacement 362 and original wheel phases.
            // Picker-chosen 358 never returns while H12D or H12E is active.
            offsets.remove(358);
        } else if(id.equals("locomotive_steam_br_black_5")) {
            // Original mirrored long bars differ by .03125 in X. Rebase
            // ONLY the positive-Z four-part linkage to match its counterpart.
            // No extra stretching or synthetic parts; H12D vertical drive held.
            for(int chunk:new int[]{244,245,246,247}) {
                float[] old=offsets.get(chunk);
                if(old!=null)offsets.put(chunk,new float[]{old[0]+0.03125f,old[1],old[2]});
            }
        }
    }




    // DEV54H15: in-game, two-anchor editable wheel-to-piston linkage.
    // This editor owns only its versioned config; original TCM/wheels stay byte-perfect.
    // All edits require the H15 selector; known user-PASS models cannot be edited.
    private static final boolean DEV54H15_ON=dev54h15Marker();
    private static final String DEV54H15_HEADER="DEV54H15-TWO-POINT-V1";
    private static final java.nio.file.Path DEV54H15_FILE=
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config")
                 .resolve("tafaricraft-dev54h15-rod-builder.tsv");
    private static final java.util.Set<String> DEV54H15_FROZEN=java.util.Set.of(
        "locomotive_steam_4_4_0", "locomotive_steam_adler",
        "locomotive_steam_alco_sc4", "locomotive_steam_br_black_5",
        "locomotive_steam_berkshire_1225", "locomotive_steam_berkshire_765",
        "locomotive_steam_a4_mallard", "locomotive_steam_king_class",
        "locomotive_steam_262t", "locomotive_steam_alice_0_4_0",
        "locomotive_steam_vb_shay_2", "locomotive_diesel_v60",
        "locomotive_steam_br01", "locomotive_steam_br80",
        "locomotive_steam_c11", "locomotive_steam_c62_body",
        "locomotive_steam_cherepanov", "locomotive_steam_climax",
        "locomotive_steam_climax_2", "locomotive_steam_snow_plow");
    // DEV54H15J: Batch 2 user-PASS configuration is read-only, NOT invisible.
    // One source-qualified switch is OFF unless its literal selector is enabled.
    // No .tcm change, wheel replacement, or edit to already-accepted locomotives.
    private static final boolean DEV54H15J_ON=dev54h15jMarker();
    private static boolean dev54h15jMarker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                  .resolve("DEV54H15J-BATCH2-FROZEN-BATCH3-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                   java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                     .replace("\uFEFF","").trim().equals("DEV54H15J-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<String> DEV54H15J_FROZEN=java.util.Set.of(
        "legacy_meshes/locomotive_steam_br_britannia.tcm",
        "legacy_meshes/locomotive_steam_c41.tcm",
        "legacy_meshes/locomotive_steam_c41_080.tcm",
        "legacy_meshes/locomotive_steam_c41t.tcm");
    // Exact two-file user-approved values. Unmatched changes HOLD the affected
    // design instead of overwriting a visual PASS or accepting unknown geometry.
    private static final java.util.Map<String,String> DEV54H15J_APPROVED_MAIN=
        java.util.Map.of(
        "legacy_meshes/locomotive_steam_br_britannia.tcm", "legacy_meshes/locomotive_steam_br_britannia.tcm\t-0.390625\t0.015625\t-0.37515625\t-0.390625\t0.125\t-0.37515625\t-1.344375\t-0.083124995\t-0.37515625\t0.078\tfalse\tfalse",
        "legacy_meshes/locomotive_steam_c41.tcm", "legacy_meshes/locomotive_steam_c41.tcm\t1.21875\t-0.40625\t-0.455\t1.21875\t-0.296875\t-0.455\t0.0\t-0.34249997\t-0.455\t0.078\tfalse\tfalse",
        "legacy_meshes/locomotive_steam_c41_080.tcm", "legacy_meshes/locomotive_steam_c41_080.tcm\t1.21875\t-0.40625\t-0.455\t1.21875\t-0.296875\t-0.455\t0.0\t-0.3425\t-0.455\t0.078\tfalse\tfalse",
        "legacy_meshes/locomotive_steam_c41t.tcm", "legacy_meshes/locomotive_steam_c41t.tcm\t1.21875\t-0.40625\t-0.455\t1.21875\t-0.296875\t-0.455\t-0.03125\t-0.37375\t-0.455\t0.078\tfalse\tfalse"
    );
    private static final java.util.Map<String,String> DEV54H15J_APPROVED_GEAR=
        java.util.Map.of(
        "legacy_meshes/locomotive_steam_br_britannia.tcm", "legacy_meshes/locomotive_steam_br_britannia.tcm\ttrue\tfalse\ttrue\ttrue\t0.0",
        "legacy_meshes/locomotive_steam_c41.tcm", "legacy_meshes/locomotive_steam_c41.tcm\tfalse\ttrue\ttrue\ttrue\t0.140625",
        "legacy_meshes/locomotive_steam_c41_080.tcm", "legacy_meshes/locomotive_steam_c41_080.tcm\tfalse\ttrue\ttrue\ttrue\t0.15625",
        "legacy_meshes/locomotive_steam_c41t.tcm", "legacy_meshes/locomotive_steam_c41t.tcm\tfalse\ttrue\ttrue\ttrue\t0.15625"
    );
    private static boolean dev54h15jRenderable(String path){
        return dev54h15Eligible(path) ||
             (DEV54H15J_ON && DEV54H15J_FROZEN.contains(path));
    }
    private static boolean dev54h15jApprovedRow(String path,String row,boolean sidecar){
        if(!DEV54H15J_ON||!DEV54H15J_FROZEN.contains(path))return true;
        var approved=sidecar?DEV54H15J_APPROVED_GEAR:DEV54H15J_APPROVED_MAIN;
        if(row.equals(approved.get(path)))return true;
        LOGGER.error("[DEV54H15J-FROZEN-ROW-HOLD] model={} sidecar={} fileRowMismatch=true " +
           "acceptedDesignNotModified=true; restore user-PASS snapshot",path,sidecar);
        return false;
    }
    // Stop Batch 3 saves if the four accepted rows have changed outside the
    // read-only editor. Older rows are never silently overwritten on save.
    private static boolean dev54h15jFreezeIntact(){
        if(!DEV54H15J_ON)return true;
        try{
            for(boolean sidecar:new boolean[]{false,true}){
                var f=sidecar?DEV54H15H_FILE:DEV54H15_FILE;
                var expected=sidecar?DEV54H15J_APPROVED_GEAR:DEV54H15J_APPROVED_MAIN;
                var seen=new java.util.HashSet<String>();
                for(var row:java.nio.file.Files.readAllLines(f,java.nio.charset.StandardCharsets.UTF_8)){
                    var first=row.indexOf('\t');
                    if(first<0)continue;
                    var path=row.substring(0,first);
                    if(!expected.containsKey(path))continue;
                    if(!seen.add(path) || !dev54h15jApprovedRow(path,row,sidecar))return false;
                }
                if(seen.size()!=DEV54H15J_FROZEN.size()){
                    LOGGER.error("[DEV54H15J-FROZEN-MISSING] sidecar={} found={} expected=4",sidecar,seen.size());
                    return false;
                }
            }
            return true;
        }catch(Exception ex){
            LOGGER.error("[DEV54H15J-FROZEN-FILE-HOLD] accepted Batch 2 files unreadable",ex);
            return false;
        }
    }

    // One side is authored by two P selections. Mirror is explicitly previewed.
    private record Dev54H15Rod(String path,float ax,float ay,float az,
                float wx,float wy,float wz,float px,float py,float pz,
                float width,boolean replace,boolean mirrorOpposite) {}
    private static final java.util.Map<String,Dev54H15Rod> DEV54H15_SAVED=
            new java.util.HashMap<>();
    private static final java.util.Set<String> DEV54H15_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15Loaded=false;
    private static boolean dev54h15Editing=false;
    private static int dev54h15Stage=0; // 0 crankpin, 1 piston, 2 ready
    private static Dev54H15Rod dev54h15Draft=null;
    private static Dev54H15Rod dev54h15SavedWheel=null;
    private static boolean dev54h15Preview=false;
    private static long dev54h15Ticks=0L;
    private static volatile boolean dev54h15LastToggle=false,dev54h15LastSave=false,
            dev54h15LastCancel=false,dev54h15LastPreview=false,dev54h15LastReplace=false,
            dev54h15LastMirror=false,dev54h15LastLeft=false,dev54h15LastRight=false,
            dev54h15LastUp=false,dev54h15LastDown=false,dev54h15LastNarrow=false,
            dev54h15LastWide=false;
    // DEV54H15C: independent Rod Builder capture (G), forgiving side-plane
    // targeting with live crosshair preview, staged markers, and radius warning.
    // All editor actions remain OFF by default, accepted fleets unchanged.
    private static final boolean DEV54H15C_ON=dev54h15cMarker();
    private static boolean dev54h15cMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15C-ROD-BUILDER-POINT-PICKER-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                   .replace("\uFEFF","").trim().equals("DEV54H15C-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static boolean dev54h15cLastG=false,dev54h15cCapture=false;
    private static LegacyStaticMesh dev54h15cHoverMesh=null;
    private static float dev54h15cHoverX,dev54h15cHoverY,dev54h15cHoverZ;
    private static LegacyStaticMesh dev54h15cTargetMesh=null;
    private static float dev54h15cTargetX,dev54h15cTargetY,dev54h15cTargetZ;
    private static double dev54h15cTargetDistance=Double.POSITIVE_INFINITY;
    private static int dev54h15cConsidered=0;
    private void dev54h15cProbe(PoseStack.Pose pose,Vector3f cameraDirection){
        if(!DEV54H15C_ON||!DEV54H15_ON||!dev54h15Editing||
           !dev54h15Eligible(this.source.getPath())||dev54h15Stage>=2)return;
        if(dev54h15bAxleMesh!=null && dev54h15bAxleMesh!=this)return;
        if(dev54h15Stage==1 && (dev54h15SavedWheel==null||
           !dev54h15SavedWheel.path().equals(this.source.getPath())))return;
        dev54h15cConsidered++;
        var inv=new org.joml.Matrix4f(pose.pose()).invert();
        Vector3f origin=inv.transformPosition(new Vector3f());
        Vector3f d=inv.transformDirection(new Vector3f(cameraDirection)).normalize();
        if(!Float.isFinite(d.z())||Math.abs(d.z())<.012f)return;
        float side=origin.z()>=0f?1f:-1f;
        float extent=side>0?this.maxZ:-this.minZ;
        if(!Float.isFinite(extent)||extent<.17f)return;
        // An actual wheel face may be inside the old body-envelope projection.
        // Test several face depths instead of the single 77%-extent plane.
        // This also works when standing close enough that the outermost plane
        // is behind the camera. Never raycast unrelated world geometry.
        float[] depths={Math.min(extent*.88f,.70f),Math.min(extent*.67f,.54f),
                        Math.min(extent*.46f,.40f),Math.min(extent*.32f,.28f)};
        for(float absZ:depths){
            if(absZ<.12f)continue;
            float z=side*absZ;
            double t=(z-origin.z())/d.z();
            if(!(t>.01d)||t>14d||t>=dev54h15cTargetDistance||!Double.isFinite(t))continue;
            float x=origin.x()+(float)t*d.x(), y=origin.y()+(float)t*d.y();
            if(!Float.isFinite(x)||!Float.isFinite(y))continue;
            if(x<this.minX-.28f||x>this.maxX+.28f||
               y<this.minY-.35f||y>this.maxY+.35f)continue;
            if(dev54h15bAxleMesh!=null && dev54h15Stage==0 &&
               Math.hypot(x-dev54h15bAxleX,y-dev54h15bAxleY)>.52d)continue;
            dev54h15cTargetMesh=this;
            dev54h15cTargetDistance=t;
            dev54h15cTargetX=x;dev54h15cTargetY=y;
            dev54h15cTargetZ=dev54h15bAxleMesh!=null?dev54h15bAxleZ:z;
        }
    }
    private static void dev54h15cMarker(PoseStack.Pose pose,VertexConsumer out,
                     int light,int overlay,float x,float y,float z,float radius,int brightness){
        float forward=z>=0f?.065f:-.065f;
        float face=z+forward;
        // Double-sided flat crosses. The old rod-box routine has inward
        // face winding, so do not reuse it for selection markers.
        float w=.016f;
        float[][] horizontal={{x-radius,y-w,face},{x+radius,y-w,face},
                              {x+radius,y+w,face},{x-radius,y+w,face}};
        float[][] vertical={{x-w,y-radius,face},{x+w,y-radius,face},
                            {x+w,y+radius,face},{x-w,y+radius,face}};
        dev54h13bQuad(pose,out,light,overlay,horizontal,brightness);
        dev54h13bQuad(pose,out,light,overlay,new float[][]{
                    horizontal[3],horizontal[2],horizontal[1],horizontal[0]},brightness);
        dev54h13bQuad(pose,out,light,overlay,vertical,brightness);
        dev54h13bQuad(pose,out,light,overlay,new float[][]{
                    vertical[3],vertical[2],vertical[1],vertical[0]},brightness);
    }
    private void dev54h15cShowMarkers(PoseStack.Pose pose,VertexConsumer out,
                        int light,int overlay){
        if(!DEV54H15C_ON||!dev54h15Editing||
           !dev54h15Eligible(this.source.getPath()))return;
        if(dev54h15cHoverMesh==this&&dev54h15Stage<2)
            dev54h15cMarker(pose,out,light,overlay,dev54h15cHoverX,
                    dev54h15cHoverY,dev54h15cHoverZ,.10f,230);
        if(!DEV54H15F_ON && DEV54H15E_ON && dev54h15eAutomatic && dev54h15Stage==0 &&
           dev54h15eSnapMesh==this && dev54h15eSnapPivot!=null){
            var axle=dev54h15eSnapPivot;
            dev54h15cMarker(pose,out,light,overlay,axle.x(),axle.y(),axle.z(),.17f,255);
        }
        if(dev54h15bAxleMesh==this){
            dev54h15cMarker(pose,out,light,overlay,dev54h15bAxleX,
                    dev54h15bAxleY,dev54h15bAxleZ,.13f,255);
        }
        if(dev54h15SavedWheel!=null &&
           dev54h15SavedWheel.path().equals(this.source.getPath()) && dev54h15Stage>=1){
            // Keep the axle marker visible after completing the piston point.
            if(DEV54H15E_ON && dev54h15bAxleMesh==null)
                dev54h15cMarker(pose,out,light,overlay,dev54h15SavedWheel.ax(),
                        dev54h15SavedWheel.ay(),dev54h15SavedWheel.az(),.13f,255);
            dev54h15cMarker(pose,out,light,overlay,dev54h15SavedWheel.wx(),
                    dev54h15SavedWheel.wy(),dev54h15SavedWheel.wz(),.09f,190);
        }
        if(dev54h15Draft!=null && dev54h15Stage==2 &&
           dev54h15Draft.path().equals(this.source.getPath()))
            dev54h15cMarker(pose,out,light,overlay,dev54h15Draft.px(),
                    dev54h15Draft.py(),dev54h15Draft.pz(),.13f,245);
    }
    // DEV54H15D: model-space ray/expanded-bounds selection. H15C's
    // fixed-depth plane picker misses near/inside cameras even while looking
    // directly at the wheel. This editor-only override preserves all saved
    // alignments, mesh chunks, wheel phases, rod animations, and old selectors.
    private static final boolean DEV54H15D_ON=dev54h15dMarker();
    private static boolean dev54h15dMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H15D-BOUNDS-RAY-PICKER-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                    java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equals("DEV54H15D-ON-V1");
        } catch(Exception ex) { return false; }
    }
    private static String dev54h15dLastProbe="no eligible model";
    private void dev54h15dProbe(PoseStack.Pose pose,Vector3f cameraDirection){
        if(!DEV54H15D_ON||!DEV54H15C_ON||!DEV54H15_ON||!dev54h15Editing||
           !dev54h15Eligible(this.source.getPath())||dev54h15Stage>=2)return;
        if(dev54h15bAxleMesh!=null && dev54h15bAxleMesh!=this)return;
        if(dev54h15Stage==1 && (dev54h15SavedWheel==null||
           !dev54h15SavedWheel.path().equals(this.source.getPath())))return;
        dev54h15cConsidered++;
        var inverse=new org.joml.Matrix4f(pose.pose()).invert();
        Vector3f o=inverse.transformPosition(new Vector3f());
        Vector3f d=inverse.transformDirection(new Vector3f(cameraDirection)).normalize();
        // Use the original model-space bounding box, expanded only enough to
        // compensate for mesh faces/wheels extending past the old source hull.
        float[] lows={this.minX-.32f,this.minY-.35f,this.minZ-.20f};
        float[] highs={this.maxX+.32f,this.maxY+.35f,this.maxZ+.20f};
        float[] oo={o.x(),o.y(),o.z()},dd={d.x(),d.y(),d.z()};
        float near=Float.NEGATIVE_INFINITY,far=Float.POSITIVE_INFINITY;
        boolean outsideParallel=false;
        for(int axis=0;axis<3;axis++){
            if(!Float.isFinite(oo[axis]) || !Float.isFinite(dd[axis]))return;
            if(Math.abs(dd[axis])<1.0e-6f){
                if(oo[axis]<lows[axis] || oo[axis]>highs[axis])outsideParallel=true;
                continue;
            }
            float a=(lows[axis]-oo[axis])/dd[axis];
            float b=(highs[axis]-oo[axis])/dd[axis];
            near=Math.max(near,Math.min(a,b));
            far=Math.min(far,Math.max(a,b));
        }
        if(outsideParallel || far<Math.max(near,0f) || !Float.isFinite(far)){
            if(dev54h15cConsidered==1)dev54h15dLastProbe=
                "ray missed expanded bounds model="+this.source.getPath()+
                " origin="+o+" dir="+d+" hullZ="+this.minZ+".."+this.maxZ;
            return;
        }
        // Outside camera: first front-surface intersection. Inside camera:
        // probe just in front of the eye, NOT the far face of the train.
        float t=near>=.025f?near:(far>.025f?.025f:Float.NaN);
        if(!Float.isFinite(t)||t>14f||t>=dev54h15cTargetDistance)return;
        float x=o.x()+t*d.x(),y=o.y()+t*d.y();
        if(!Float.isFinite(x)||!Float.isFinite(y))return;
        // A wide model hull makes the click easy; still only accept points
        // close to the physical locomotive's XY envelope.
        if(x<this.minX-.33f||x>this.maxX+.33f||
           y<this.minY-.36f||y>this.maxY+.36f)return;
        float side=o.z()>=0f?1f:-1f;
        float z=side*Math.max(.20f,Math.min(.70f,(side>0?this.maxZ:-this.minZ)*.78f));
        if(dev54h15bAxleMesh!=null && dev54h15Stage==0 &&
           Math.hypot(x-dev54h15bAxleX,y-dev54h15bAxleY)>.52d)return;
        dev54h15cTargetMesh=this;
        dev54h15cTargetDistance=t;
        dev54h15cTargetX=x;dev54h15cTargetY=y;
        dev54h15cTargetZ=dev54h15bAxleMesh!=null?dev54h15bAxleZ:z;
        dev54h15dLastProbe="hit "+this.source.getPath()+" x="+x+" y="+y+
                " near="+near+" far="+far+" camera="+o+" dir="+d;
    }
    // DEV54H15E: qualified axle-center snapping for the Rod Builder. User now
    // clicks only an off-center CRANKPIN and then the PISTON when the source
    // wheel pivot is verifiable. Unsupported meshes use the existing manual
    // 3-click mode, selected explicitly using V. No original geometry changes.
    private static final boolean DEV54H15E_ON=dev54h15eMarker();
    private static boolean dev54h15eMarker(){
        try{
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                 .resolve("DEV54H15E-VERIFIED-AXLE-SNAP-ON.txt");
            return java.nio.file.Files.isRegularFile(file) &&
                   java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF","").trim().equals("DEV54H15E-ON-V1");
        }catch(Exception ex){return false;}
    }
    private record Dev54H15EPivot(float x,float y,float z){}
    private transient java.util.List<Dev54H15EPivot> dev54h15ePivots;
    private static boolean dev54h15eAutomatic=true,dev54h15eLastV=false;
    private static LegacyStaticMesh dev54h15eSnapMesh=null;
    private static Dev54H15EPivot dev54h15eSnapPivot=null;
    private static float dev54h15eSnapX,dev54h15eSnapY;
    private static double dev54h15eSnapDistance=Double.POSITIVE_INFINITY;
    private static int dev54h15eQualifiedSeen=0;
    // DEV54H15Z: LSSP7 wheel faces are above the normal body-relative height
    // cutoff but were independently source-verified in BakedWheelAnimationContext
    // (six rotating wheel chunks 11..16). Editor-only qualification; wheel
    // transforms, source TCMs, accepted locomotives and rod masks are unchanged.
    // OFF by default until an exact marker is explicitly enabled.
    private static final boolean DEV54H15Z_LSSP7_EDITOR_ON=dev54h15zLssp7Marker();
    private static boolean dev54h15zLssp7Marker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15Z-LSSP7-EDITOR-AXLES-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H15Z-ON-V1");
        }catch(Exception ex){return false;}
    }
    // DEV54H15AB: activate six-source-wheel pivot audit plus renderer-space
    // coordinates for LSSP7 ONLY. No geometry, masks, or TSVs are changed.
    private static final boolean DEV54H15AB_LSSP7_DIAG_ON=dev54h15abMarker();
    private static boolean dev54h15abMarker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15AB-LSSP7-SIX-PIVOTS-DIAG-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H15AB-ON-V1");
        }catch(Exception e){return false;}
    }
    private static final java.util.Set<String> DEV54H15AB_LOGGED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    // DEV54H15AA: LSSP7's original mesh is offset in local Z. Its driving
    // wheel faces sit at z=0.43125 and z=1.19375 around the *actual* centerline
    // z=0.8125, NOT around zero as on other legacy fleet models. All 6 wheel
    // source chunks 11..16 remain unchanged. Mirroring about z=0 put custom
    // bars off the opposite side of LSSP7. This corrects editor-side preview
    // and generated bars for LSSP7 only. OFF until explicitly enabled.
    private static final float DEV54H15AA_LSSP7_CENTER_Z=.8125f;
    private static final boolean DEV54H15AA_LSSP7_MIRROR_ON=dev54h15aaLssp7Marker();
    private static boolean dev54h15aaLssp7Marker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15AA-LSSP7-CENTERED-MIRROR-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                 java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                 .replace("\uFEFF","").trim().equals("DEV54H15AA-ON-V1");
        }catch(Exception e){return false;}
    }
    private static boolean dev54h15aaLssp7(String path){
        return DEV54H15AA_LSSP7_MIRROR_ON &&
            path.equals("legacy_meshes/locomotive_steam_lssp7.tcm");
    }
    // DEV54H15AI BATCH 5: S100 driving-wheel planes are both positive Z,
    // as with accepted H15AB LSSP7. Keep six SOURCE-VERIFIED wheels independent.
    // EDITOR / user-drawn rod generation ONLY; original meshes and wheel phase frozen.
    private static final boolean DEV54H15AI_S100_ON =
        dev54h15tEnabled("DEV54H15AI-S100-SIX-AXLES-ON.txt", "DEV54H15AI-S100-ON-V1");
    private static boolean dev54h15aiS100(String path){
        return DEV54H15AI_S100_ON &&
            ("legacy_meshes/locomotive_steam_s100_uk.tcm".equals(path) ||
             "legacy_meshes/locomotive_steam_s100_us.tcm".equals(path));
    }
    private static final java.util.Map<String,Float> DEV54H15AI_S100_MIDZ =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> DEV54H15AI_S100_REPORTED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    // DEV58G11R17R3 HALL-ONLY: source-proven driver wheel planes are
    // z=-0.625 and z=+0.125 (chunks 360..365), centerline=-0.25.
    // Use the existing H15 bilateral mirroring/side classification around the
    // true driver centerline, not z=0.  Both wheel phases and all source
    // geometry remain untouched.  OFF instantly on the next client restart
    // when this exact marker is absent; Hall R16 GPU shell flag is independent.
    private static final boolean DEV58G11R17R3_HALL_MIRROR_ON=dev54h15tEnabled(
        "DEV58G11R17R3-HALL-CENTERLINE-MIRROR-ON.flag",
        "DEV58G11R17R3-HALL-CENTERLINE-MIRROR-ON-V1");
    private static float dev54h15aaZCenter(String path){
        if(DEV58G11R17R3_HALL_MIRROR_ON &&
           "legacy_meshes/locomotive_steam_hall_class.tcm".equals(path))return -.25f;
        if(dev54h15aaLssp7(path))return DEV54H15AA_LSSP7_CENTER_Z;
        if(dev54h15aiS100(path))return DEV54H15AI_S100_MIDZ.getOrDefault(path,.8125f);
        return 0f;
    }
    private static float dev54h15aaMirrorZ(String path,float z){
        return 2f*dev54h15aaZCenter(path)-z;
    }
    // Cache read-only wheel definitions; never assume a center from the body
    // bounds. Exact C41 source-geometry guards match the H14B axle geometry.
    private java.util.List<Dev54H15EPivot> dev54h15eVerifiedPivots(){
        if(dev54h15ePivots!=null)return dev54h15ePivots;
        var found=new java.util.ArrayList<Dev54H15EPivot>();
        String path=this.source.getPath();
        if(dev54h14bC41(path) &&
           dev54h13bGeometry(path,this.vertices,FLOATS_PER_VERTEX)){
            for(float side:new float[]{-1f,1f}){
                for(float x:new float[]{.46875f,1.21875f,1.96875f,2.71875f})
                    found.add(new Dev54H15EPivot(x,-.40625f,side*.455f));
            }
        }else{
            String key="legacy:"+this.source;
            for(int ch=0;ch<(this.vertexCount+23)/24;ch++){
                var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(key,ch,this.vertices,FLOATS_PER_VERTEX);
                if(spec==null||!spec.rotates()||spec.axis()!=2)continue;
                float x=spec.centerX(),y=spec.centerY(),z=spec.centerZ();
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||
                   Math.abs(z)<.12f ||
                   (y>this.maxY-.30f && !(DEV54H15Z_LSSP7_EDITOR_ON &&
                     path.equals("legacy_meshes/locomotive_steam_lssp7.tcm"))) ||
                   x<this.minX-.2f||x>this.maxX+.2f ||
                   y<this.minY-.2f||y>this.maxY+.2f)continue;
                boolean duplicate=false;
                for(var old:found){
                    if(Math.abs(x-old.x())<.025f && Math.abs(y-old.y())<.025f &&
                        // DEV54H15AB: LSSP7 source Z planes are BOTH positive.
                        // Compare the actual Z coordinate rather than its sign,
                        // while leaving all other models' proven path unchanged.
                        ((DEV54H15AB_LSSP7_DIAG_ON &&
                          path.equals("legacy_meshes/locomotive_steam_lssp7.tcm")) ||
                         dev54h15aiS100(path) ?
                         Math.abs(z-old.z())<.025f:
                         Math.signum(z)==Math.signum(old.z())))
                         {duplicate=true;break;}
                }
                if(!duplicate)found.add(new Dev54H15EPivot(x,y,z));
            }
        }
        // Never infer missing S100 wheels from the chassis. Require six
        // independently baked, rotating source chunks: 3 on each Z face.
        if(dev54h15aiS100(path)){
            float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
            for(var w:found){lo=Math.min(lo,w.z());hi=Math.max(hi,w.z());}
            float mid=(lo+hi)*.5f;
            int near=0,far=0;
            for(var w:found){if(w.z()<mid)near++;else far++;}
            boolean six=found.size()==6 && near==3 && far==3 &&
                        Float.isFinite(mid) && hi-lo>.40f && hi-lo<1.25f;
            if(six)DEV54H15AI_S100_MIDZ.put(path,mid);
            if(DEV54H15AI_S100_REPORTED.add(path))
                LOGGER.info("[DEV54H15AI-S100-EDITOR] model={} sourceVerified={} near={} far={} " +
                    "centerZ={} sixQualified={} physicalWheels=UNCHANGED LSSP7=FROZEN",
                    path,found.size(),near,far,mid,six);
        }
        // DEV54H15AM: VB 0-4-0 has no BakedWheel per-chunk classification.
        // Permit editor snap ONLY if four square wheel faces form two
        // bilateral axle pairs in the original source buffer.
        if(DEV54H15AM_VB_ON && DEV54H15AM_VB.equals(path) && found.isEmpty()){
            var verified=dev54h15amVbFaces();
            if(verified.size()==4)
                for(var f:verified)found.add(new Dev54H15EPivot(f.x(),f.y(),f.z()));
        }
        dev54h15ePivots=java.util.List.copyOf(found);
        if(DEV54H15AB_LSSP7_DIAG_ON &&
           path.equals("legacy_meshes/locomotive_steam_lssp7.tcm")){
            int near=0,far=0;
            for(var w:dev54h15ePivots){if(w.z()<DEV54H15AA_LSSP7_CENTER_Z)near++;else far++;}
            LOGGER.info("[DEV54H15AB-LSSP7-AXLE-AUDIT] pivots={} innerZ=.43125 outerZ=1.19375 centerZ=.8125 nearSide={} farSide={} qualifiesSix={} userVisualPASS=NO",
                dev54h15ePivots.size(),near,far,near==3&&far==3);
            for(int i=0;i<dev54h15ePivots.size();i++){
                var w=dev54h15ePivots.get(i);
                LOGGER.info("[DEV54H15AB-LSSP7-SOURCE-PIVOT] index={} local=({},{},{}) side={} fromUnmodifiedTCM=true",
                    i+1,w.x(),w.y(),w.z(),w.z()<DEV54H15AA_LSSP7_CENTER_Z?"NEAR":"FAR");
            }
        }
        if(DEV54H15Z_LSSP7_EDITOR_ON &&
           path.equals("legacy_meshes/locomotive_steam_lssp7.tcm"))
            LOGGER.info("[DEV54H15Z-LSSP7-EDITOR] verifiedPivots={} wheelSource=BAKED_READ_ONLY " +
                        "originalRods=H15Y_UNCHANGED acceptedLocomotives=UNTOUCHED",
                        dev54h15ePivots.size());
        if(dev54h15ePivots.isEmpty())
            LOGGER.info("[DEV54H15E-UNQUALIFIED] model={} fallback=MANUAL-3-POINT",
                        path);
        else LOGGER.info("[DEV54H15E-QUALIFIED] model={} centers={} sourceVerified=true",
                         path,dev54h15ePivots.size());
        return dev54h15ePivots;
    }
    // Project the actual camera ray to the verified wheel-face plane, rather
    // than the large body hull. The nearest matching axle is snapped exactly,
    // while the picked off-center pin remains at the crosshair's XY point.
    private void dev54h15eProbe(PoseStack.Pose pose,Vector3f direction){
        if(!DEV54H15E_ON||!DEV54H15C_ON||!DEV54H15_ON||!dev54h15Editing||
           !dev54h15Eligible(this.source.getPath())||dev54h15Stage>=2)return;
        if(dev54h15Stage==1||!dev54h15eAutomatic){dev54h15dProbe(pose,direction);return;}
        var centers=dev54h15eVerifiedPivots();
        if(centers.isEmpty()){
            dev54h15dProbe(pose,direction);
            return;
        }
        dev54h15eQualifiedSeen++;
        var inv=new org.joml.Matrix4f(pose.pose()).invert();
        Vector3f origin=inv.transformPosition(new Vector3f());
        Vector3f ray=inv.transformDirection(new Vector3f(direction)).normalize();
        if(!Float.isFinite(ray.z())||Math.abs(ray.z())<.025f)return;
        float modelCenterZ=dev54h15aaZCenter(this.source.getPath());
        int side=origin.z()>=modelCenterZ?1:-1;
        for(var axle:centers){
            if(Math.signum(axle.z()-modelCenterZ)!=side)continue;
            float faceZ=axle.z()+side*.018f;
            float t=(faceZ-origin.z())/ray.z();
            if(!(t>.025f)||t>12f||t>=dev54h15eSnapDistance)continue;
            float x=origin.x()+t*ray.x(),y=origin.y()+t*ray.y();
            float radius=(float)Math.hypot(x-axle.x(),y-axle.y());
            if(!Float.isFinite(radius)||radius>.46f)continue;
            // Score by distance from the eye and closeness to this wheel;
            // never snap to the boiler or to another wheel just because their
            // bounding boxes overlap.
            double score=t+radius*.30d;
            if(score>=dev54h15eSnapDistance)continue;
            dev54h15eSnapMesh=this;
            dev54h15eSnapPivot=axle;
            dev54h15eSnapX=x;dev54h15eSnapY=y;
            dev54h15eSnapDistance=score;
            dev54h15cTargetMesh=this;
            dev54h15cTargetX=x;dev54h15cTargetY=y;
            dev54h15cTargetZ=faceZ;
            dev54h15cTargetDistance=t;
        }
    }
    private static void dev54h15eClick(){
        if(dev54h15Stage!=0||!dev54h15eAutomatic||
           dev54h15eSnapMesh==null||dev54h15eSnapPivot==null)return;
        var pivot=dev54h15eSnapPivot;
        float x=dev54h15eSnapX,y=dev54h15eSnapY;
        float radius=(float)Math.hypot(x-pivot.x(),y-pivot.y());
        if(radius<.035f||radius>.205f){
            dev54h12cMessage("H15E axle located; G on OFF-CENTER crankpin (within 0.20). V = manual");
            LOGGER.info("[DEV54H15E-PIN-RETRY] model={} radius={} center=({},{})",
                        dev54h15eSnapMesh.source,radius,pivot.x(),pivot.y());
            return;
        }
        String path=dev54h15eSnapMesh.source.getPath();
        dev54h15bAxleMesh=dev54h15eSnapMesh;
        dev54h15bAxleX=pivot.x();dev54h15bAxleY=pivot.y();
        dev54h15bAxleZ=pivot.z();
        dev54h15SavedWheel=new Dev54H15Rod(path,pivot.x(),pivot.y(),pivot.z(),
                            x,y,pivot.z(),0f,0f,pivot.z(),.078f,false,false);
        dev54h15Stage=1;
        dev54h12cMessage("H15E AUTO AXLE + PIN set; aim at PISTON and press G (ALT+arrows fine-tune center)");
        LOGGER.info("[DEV54H15E-SNAP] model={} center=({},{},{}) pin=({},{}) radius={} stage=PISTON",
               path,pivot.x(),pivot.y(),pivot.z(),x,y,radius);
    }
    // DEV54H15F: no-ray verified wheel selector and diagnostic editor.
    // The previous picker repeatedly missed a visible C41 wheel because its
    // ray failed the rotated locomotive envelope. H15F instead uses the
    // VERIFIED H15E axle data, which never depends on the crosshair ray.
    // OFF leaves H15E behavior exactly unchanged. Editor-only; TCM untouched.
    private static final boolean DEV54H15F_ON=dev54h15fMarker();
    private static boolean dev54h15fMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15F-WHEEL-CYCLE-EDITOR-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equals("DEV54H15F-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static LegacyStaticMesh dev54h15fCandidate=null,dev54h15fChosen=null;
    private static double dev54h15fCandidateDist=Double.POSITIVE_INFINITY;
    private static long dev54h15fCandidateTick=-1L;
    private static int dev54h15fIndex=0,dev54h15fCandidateIndex=0;
    private static boolean dev54h15fLastPgUp=false,dev54h15fLastPgDown=false;
    private static final java.util.Set<String> DEV54H15F_DIAGNOSTICS=
                       java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static void dev54h15fReset(){
        dev54h15fChosen=null;dev54h15fCandidate=null;dev54h15fIndex=0;
        dev54h15fCandidateTick=-1L;dev54h15fCandidateDist=Double.POSITIVE_INFINITY;
        dev54h15fLastPgUp=false;dev54h15fLastPgDown=false;
    }
    private static Dev54H15EPivot dev54h15fPivot(){
        if(dev54h15fChosen==null)return null;
        var pivots=dev54h15fChosen.dev54h15eVerifiedPivots();
        if(pivots.isEmpty())return null;
        dev54h15fIndex=Math.floorMod(dev54h15fIndex,pivots.size());
        return pivots.get(dev54h15fIndex);
    }
    private static void dev54h15fSelectionMessage(){
        var pivot=dev54h15fPivot();
        if(pivot==null){dev54h12cMessage("H15F no verified axle on current engine; see debug log");return;}
        var pivots=dev54h15fChosen.dev54h15eVerifiedPivots();
        dev54h12cMessage("H15F WHEEL "+(dev54h15fIndex+1)+"/"+pivots.size()+
          " side="+(pivot.z()>dev54h15aaZCenter(dev54h15fChosen.source.getPath())?"+":"-")+" X="+pivot.x()+
          " | PgUp/PgDn choose, G accept");
        LOGGER.info("[DEV54H15F-SELECT] model={} index={}/{} axle=({},{},{}) stage={}",
            dev54h15fChosen.source,dev54h15fIndex+1,pivots.size(),
            pivot.x(),pivot.y(),pivot.z(),dev54h15Stage);
    }
    // Every ordinary render observes source-local, verified wheel geometry.
    // No camera ray test: only approximate range is used to pick which
    // locomotive the editor should offer first. Once chosen, keep it locked.
    private void dev54h15fObserve(PoseStack.Pose pose){
        if(!DEV54H15F_ON||!DEV54H15_ON||!dev54h15Editing||
           !dev54h15Eligible(this.source.getPath()))return;
        var pivots=dev54h15eVerifiedPivots();
        if(pivots.isEmpty())return;
        var inv=new org.joml.Matrix4f(pose.pose()).invert();
        var eye=inv.transformPosition(new Vector3f());
        if(!Float.isFinite(eye.x())||!Float.isFinite(eye.y())||
           !Float.isFinite(eye.z()))return;
        double best=Double.POSITIVE_INFINITY;int index=0;
        for(int i=0;i<pivots.size();i++){
            var a=pivots.get(i);
            double dx=eye.x()-a.x(),dy=eye.y()-a.y(),dz=eye.z()-a.z();
            double d=dx*dx+dy*dy+dz*dz;
            if(d<best){best=d;index=i;}
        }
        if(best>225d)return; // eligible locomotive must be within 15 model units
        if(dev54h15fCandidateTick!=dev54h15Ticks){
            dev54h15fCandidateTick=dev54h15Ticks;
            dev54h15fCandidate=null;
            dev54h15fCandidateDist=Double.POSITIVE_INFINITY;
        }
        if(best<dev54h15fCandidateDist){
            dev54h15fCandidate=this;
            dev54h15fCandidateDist=best;
            dev54h15fCandidateIndex=index;
        }
        if(DEV54H15F_DIAGNOSTICS.add("OBSERVE:"+this.source.getPath()))
            LOGGER.info("[DEV54H15F-AVAILABLE] model={} pivots={} nearestSquared={}",
                        this.source,pivots.size(),best);
    }
    private static boolean dev54h15fEnsure(){
        if(dev54h15fChosen!=null)return !dev54h15fChosen.dev54h15eVerifiedPivots().isEmpty();
        if(dev54h15fCandidate==null){
            if(DEV54H15M_ON && net.minecraft.client.Minecraft.getInstance().player!=null){
                var client=net.minecraft.client.Minecraft.getInstance();
                if(client.level!=null){
                    var near=client.level.getEntities(client.player,
                        client.player.getBoundingBox().inflate(12.0d),e ->
                        e instanceof traincraft.entity.train.steam.classics.ForneySteamLocomotive);
                    if(!near.isEmpty()){
                        dev54h12cMessage("H15M Forney uses native ModelPart wheels: not a legacy TCM axle; editor HOLD");
                        if(DEV54H15M_REPORT.add("FORNEY-NATIVE-MODEL"))
                            LOGGER.warn("[DEV54H15M-NATIVE-FORNEY-HOLD] renderer=ModelPart " +
                                "verifiedTCMAxles=0 nativeWheelsUnchanged=true editorAdapterPending=true");
                        return false;
                    }
                }
            }
            dev54h12cMessage("H15F no VERIFIED wheel in range; stand near locomotive (see log)");
            LOGGER.warn("[DEV54H15F-NO-MODEL] stage={} ticks={} candidateTick={} priorPickerMissIgnored=true",
                        dev54h15Stage,dev54h15Ticks,dev54h15fCandidateTick);
            return false;
        }
        dev54h15fChosen=dev54h15fCandidate;
        dev54h15fIndex=dev54h15fCandidateIndex;
        dev54h15fSelectionMessage();
        return true;
    }
    private static void dev54h15fKeys(long window){
        if(!DEV54H15F_ON||!dev54h15Editing)return;
        // Auto-focus the nearest verified locomotive seen in the prior frame.
        // This puts a visible axle marker on screen without even pressing G.
        if(dev54h15Stage==0 && dev54h15fChosen==null &&
           dev54h15fCandidate!=null){
            dev54h15fChosen=dev54h15fCandidate;
            dev54h15fIndex=dev54h15fCandidateIndex;
            dev54h15fSelectionMessage();
        }
        boolean next=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_PAGE_UP);
        boolean prev=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_PAGE_DOWN);
        if(dev54h15Stage==0 && (next&&!dev54h15fLastPgUp||prev&&!dev54h15fLastPgDown)){
            if(dev54h15fEnsure()){
                int count=dev54h15fChosen.dev54h15eVerifiedPivots().size();
                dev54h15fIndex=Math.floorMod(dev54h15fIndex+(next?1:-1),count);
                dev54h15fSelectionMessage();
            }
        }
        dev54h15fLastPgUp=next;dev54h15fLastPgDown=prev;
    }
    private static void dev54h15fCommit(){
        if(!DEV54H15F_ON||!dev54h15Editing)return;
        if(dev54h15Stage==0){
            if(!dev54h15fEnsure())return;
            var a=dev54h15fPivot();if(a==null)return;
            String path=dev54h15fChosen.source.getPath();
            // A horizontal crank offset at a safe, user-adjustable radius.
            // No source geometry is hidden or changed until a valid rig is saved.
            float radius=.109375f;
            dev54h15bAxleMesh=dev54h15fChosen;
            dev54h15bAxleX=a.x();dev54h15bAxleY=a.y();dev54h15bAxleZ=a.z();
            dev54h15SavedWheel=new Dev54H15Rod(path,a.x(),a.y(),a.z(),
                a.x(),a.y()+radius,a.z(),0f,0f,a.z(),.078f,false,false);
            dev54h15Stage=1;
            dev54h12cMessage("H15F PIN preview set; SHIFT+arrows adjust PIN, G estimates piston");
            LOGGER.info("[DEV54H15F-PIN] model={} axleIndex={} center=({},{},{}) pin=({},{},{}) radius={} estimated=true",
                 path,dev54h15fIndex,a.x(),a.y(),a.z(),a.x(),a.y()+radius,a.z(),radius);
            return;
        }
        if(dev54h15Stage==1){
            var a=dev54h15SavedWheel;
            if(a==null){dev54h12cMessage("H15F no pin data; Backspace reset");return;}
            LegacyStaticMesh mesh=dev54h15fChosen;
            if(mesh==null||!mesh.source.getPath().equals(a.path()))return;
            float px,py;
            if(dev54h14bC41(a.path())){
                // User screenshot + 09:42 debug: C41 front/cylinder is
                // NEGATIVE source-local X; the previous +3.625 points to cab.
                px=DEV54H15G_ON?
                    Math.max(mesh.minX+.125f,Math.min(mesh.maxX-.125f,-.625f)):
                    3.625f;
                py=-.28f;
                if(DEV54H15G_ON)LOGGER.info("[DEV54H15G-FRONT-ESTIMATE] model={} "
                    +"axleX={} pistonX={} frontDirection=NEGATIVE_X c41Family=true",
                    a.path(),a.ax(),px);
            }else{
                float toward=(mesh.maxX-a.ax())<(a.ax()-mesh.minX)?1f:-1f;
                px=a.ax()+toward*.86f;
                px=Math.max(mesh.minX-.18f,Math.min(mesh.maxX+.18f,px));
                py=a.ay()+.12f;
            }
            var draft=new Dev54H15Rod(a.path(),a.ax(),a.ay(),a.az(),
                       a.wx(),a.wy(),a.wz(),px,py,a.wz(),a.width(),false,false);
            if(!dev54h15Good(draft)){
                LOGGER.warn("[DEV54H15F-HOLD] model={} invalid estimated piston=({},{}) axle=({},{})",
                            a.path(),px,py,a.ax(),a.ay());
                dev54h12cMessage("H15F piston estimate invalid; try another wheel PageUp/PgDn after Backspace");
                return;
            }
            dev54h15Draft=draft;dev54h15Stage=2;dev54h15Preview=true;
            dev54h12cMessage(DEV54H15G_ON?
                "H15G PISTON estimated (front on C41): V flip direction, arrows adjust, Z preview, Enter save":
                "H15F ESTIMATED PISTON; arrows adjust, SHIFT+arrows PIN, Z preview, ENTER save");
            LOGGER.info("[DEV54H15F-PISTON] model={} point=({},{},{}) estimated=true userReviewRequired=true",
                 a.path(),px,py,a.wz());
        }
    }
    private static void dev54h15fAdjustPin(boolean left,boolean right,boolean up,boolean down){
        var a=dev54h15SavedWheel;if(a==null||dev54h15Stage<1)return;
        float wx=a.wx()+(right&&!dev54h15LastRight?.015625f:0f)-
                         (left&&!dev54h15LastLeft?.015625f:0f);
        float wy=a.wy()+(up&&!dev54h15LastUp?.015625f:0f)-
                         (down&&!dev54h15LastDown?.015625f:0f);
        if(wx==a.wx()&&wy==a.wy())return;
        float radius=(float)Math.hypot(wx-a.ax(),wy-a.ay());
        if(!(radius>=.045f&&radius<=.205f)){
            dev54h12cMessage("H15F crankpin HOLD radius "+radius+" must be 0.045-0.205");
            return;
        }
        dev54h15SavedWheel=new Dev54H15Rod(a.path(),a.ax(),a.ay(),a.az(),
                  wx,wy,a.wz(),a.px(),a.py(),a.pz(),a.width(),a.replace(),a.mirrorOpposite());
        if(dev54h15Stage==2&&dev54h15Draft!=null){
            var d=dev54h15Draft;
            dev54h15Draft=new Dev54H15Rod(d.path(),d.ax(),d.ay(),d.az(),
                  wx,wy,d.wz(),d.px(),d.py(),d.pz(),d.width(),d.replace(),d.mirrorOpposite());
        }
        dev54h12cMessage("H15F PIN x="+wx+" y="+wy+" r="+radius);
        LOGGER.info("[DEV54H15F-PIN-ADJUST] model={} pin=({},{}) radius={}",a.path(),wx,wy,radius);
    }
    private void dev54h15fShowMarkers(PoseStack.Pose pose,VertexConsumer out,int light,int overlay){
        if(!DEV54H15F_ON||!dev54h15Editing||dev54h15fChosen!=this)return;
        var a=dev54h15fPivot();if(a==null)return;
        dev54h15cMarker(pose,out,light,overlay,a.x(),a.y(),a.z(),.19f,255);
        if(dev54h15Stage>=1&&dev54h15SavedWheel!=null){
            var d=dev54h15SavedWheel;
            dev54h15cMarker(pose,out,light,overlay,d.wx(),d.wy(),d.wz(),.12f,205);
            if(dev54h15Stage>=2&&dev54h15Draft!=null)
               dev54h15cMarker(pose,out,light,overlay,
                    dev54h15Draft.px(),dev54h15Draft.py(),dev54h15Draft.pz(),.14f,235);
        }
    }
    // DEV54H15G: user-confirmed C41 piston points are toward negative local X,
    // NOT H15F's old x=+3.625 cab end. This layer changes editor estimates
    // only; source meshes, wheels, accepted animations and saved rigs are frozen.
    // V flips the piston preview to the opposite side of the selected axle.
    private static final boolean DEV54H15G_ON=dev54h15gMarker();
    private static boolean dev54h15gMarker(){
        try{
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                     .resolve("DEV54H15G-PISTON-DIRECTION-ON.txt");
            return java.nio.file.Files.isRegularFile(file) &&
                   java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF", "").trim().equals("DEV54H15G-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static boolean dev54h15gLastV=false;
    private static void dev54h15gFlipPiston(long window){
        if(!DEV54H15G_ON || !DEV54H15F_ON || !dev54h15Editing)return;
        boolean v=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_V);
        if(v&&!dev54h15gLastV){
            if(dev54h15Stage!=2 || dev54h15Draft==null || dev54h15fChosen==null){
                dev54h12cMessage("H15G: first G select pin, second G create piston, then V flip direction");
            }else{
                var a=dev54h15Draft;
                LegacyStaticMesh mesh=dev54h15fChosen;
                if(!mesh.source.getPath().equals(a.path())){
                    dev54h12cMessage("H15G HOLD: chosen locomotive changed; reset draft");
                }else{
                    // Mirror longitudinal position about the verified axle,
                    // keeping the current crankpin, vertical anchor and side.
                    float nextX=2f*a.ax()-a.px();
                    nextX=Math.max(mesh.minX-.125f,Math.min(mesh.maxX+.125f,nextX));
                    var changed=new Dev54H15Rod(a.path(),a.ax(),a.ay(),a.az(),
                        a.wx(),a.wy(),a.wz(),nextX,a.py(),a.pz(),a.width(),
                        a.replace(),a.mirrorOpposite());
                    if(!dev54h15Good(changed)){
                        dev54h12cMessage("H15G HOLD: flipped anchor unsafe; use arrow keys");
                        LOGGER.warn("[DEV54H15G-FLIP-HOLD] model={} fromX={} proposedX={}",
                            a.path(),a.px(),nextX);
                    }else{
                        dev54h15Draft=changed;dev54h15Preview=true;
                        boolean validCycle=dev54h15FullCycle(changed);
                        dev54h12cMessage("H15G PISTON "+
                            (nextX<a.ax()?"NEGATIVE X":"POSITIVE X")+
                            " x="+nextX+(validCycle?"; preview and adjust":"; cycle HOLD: adjust arrows"));
                        LOGGER.info("[DEV54H15G-FLIP] model={} axleX={} oldPistonX={} "
                            +"newPistonX={} fullCycle={} stage=2 saved=false",
                            a.path(),a.ax(),a.px(),nextX,validCycle);
                    }
                }
            }
        }
        dev54h15gLastV=v;
    }
    // DEV54H15H: complete, reversible per-locomotive running-gear authoring.
    // No TCM modifications; no wheel pivots, wheel phase or accepted train
    // sources are touched. Independent H15H sidecar preserves original H15 rig.
    private static final boolean DEV54H15H_ON=dev54h15hMarker();
    private static boolean dev54h15hMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                 .resolve("DEV54H15H-FULL-ROD-EDITOR-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                 java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equals("DEV54H15H-ON-V1");
        }catch(Exception ex){return false;}
    }
    private record Dev54H15HDesign(String path,boolean hideMesh,boolean hideGenerated,
                boolean coupling,boolean crankpins,float inset){}
    private static final java.nio.file.Path DEV54H15H_FILE=
            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config")
                 .resolve("tafaricraft-dev54h15h-full-running-gear.tsv");
    private static final String DEV54H15H_HEADER="DEV54H15H-RUNNING-GEAR-V1";
    private static final java.util.Map<String,Dev54H15HDesign> DEV54H15H_SAVED=
            new java.util.HashMap<>();
    private static boolean dev54h15hLoaded=false;
    private static Dev54H15HDesign dev54h15hDraft=null;
    private static boolean dev54h15hLastOld=false,dev54h15hLastGen=false,
        dev54h15hLastCouple=false,dev54h15hLastPins=false,
        dev54h15hLastIn=false,dev54h15hLastOut=false;
    private static final java.util.Set<String> DEV54H15H_REPORT=
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15hValid(Dev54H15HDesign d){
        return d!=null && dev54h15jRenderable(d.path()) &&
              Float.isFinite(d.inset()) && d.inset()>=-.3f && d.inset()<=.3f;
    }
    private static Dev54H15HDesign dev54h15hDefault(Dev54H15Rod rod){
        // Legacy H15 'replace' users retain the old paired hide behavior.
        boolean old=rod!=null && rod.replace();
        return new Dev54H15HDesign(rod.path(),old,old,true,true,0f);
    }
    private static void dev54h15hLoad(){
        if(!DEV54H15H_ON||dev54h15hLoaded)return;
        dev54h15hLoaded=true;
        if(!java.nio.file.Files.isRegularFile(DEV54H15H_FILE))return;
        try{
            var lines=java.nio.file.Files.readAllLines(DEV54H15H_FILE,
                                   java.nio.charset.StandardCharsets.UTF_8);
            if(lines.isEmpty()||!DEV54H15H_HEADER.equals(lines.get(0)))
                throw new IllegalArgumentException("Unexpected H15H config header");
            for(int i=1;i<lines.size();i++){
                if(lines.get(i).isBlank())continue;
                String[] t=lines.get(i).split("\\t",-1);
                if(t.length!=6||DEV54H15H_SAVED.size()>=64)
                    throw new IllegalArgumentException("Bad H15H config row "+i);
                var d=new Dev54H15HDesign(t[0],Boolean.parseBoolean(t[1]),
                    Boolean.parseBoolean(t[2]),Boolean.parseBoolean(t[3]),
                    Boolean.parseBoolean(t[4]),Float.parseFloat(t[5]));
                if(!dev54h15hValid(d))throw new IllegalArgumentException("Unsafe H15H row "+i);
                if(!dev54h15jApprovedRow(d.path(),lines.get(i),true))continue;
                DEV54H15H_SAVED.put(d.path(),d);
            }
            LOGGER.info("[DEV54H15H-LOAD] designs={} file={}",
                        DEV54H15H_SAVED.size(),DEV54H15H_FILE);
        }catch(Exception ex){
            DEV54H15H_SAVED.clear();
            LOGGER.error("[DEV54H15H-HOLD] Invalid design file; no masks applied",ex);
        }
    }
    private static Dev54H15HDesign dev54h15hDesign(String path){
        if(!DEV54H15H_ON||!dev54h15jRenderable(path))return null;
        // A design MUST be accompanied by a qualified H15 main rod.
        Dev54H15Rod rig=dev54h15Rig(path);
        if(rig==null)return null;
        dev54h15hLoad();
        if(dev54h15Editing && dev54h15Stage==2 && dev54h15Draft!=null &&
           dev54h15Draft.path().equals(path)){
            if(dev54h15hDraft==null||!dev54h15hDraft.path().equals(path))
                dev54h15hDraft=DEV54H15H_SAVED.getOrDefault(path,dev54h15hDefault(rig));
            return dev54h15hDraft;
        }
        return DEV54H15H_SAVED.getOrDefault(path,dev54h15hDefault(rig));
    }
    private static boolean dev54h15hHideMesh(String path){
        var d=dev54h15hDesign(path);return d!=null&&d.hideMesh();
    }
    private static boolean dev54h15hHideGenerated(String path){
        var d=dev54h15hDesign(path);return d!=null&&d.hideGenerated();
    }
    private static float dev54h15hInset(String path){
        var d=dev54h15hDesign(path);return d==null?0f:d.inset();
    }
    private static void dev54h15hResetDraft(){dev54h15hDraft=null;}
    // UI hotkeys while the existing H15F/H15G editor is active and both
    // anchor points are ready. Each key has a distinct on-screen status.
    private static void dev54h15hKeys(long window){
        if(!DEV54H15H_ON||!dev54h15Editing)return;
        boolean old=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_O);
        boolean gen=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_I);
        boolean couple=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_K);
        boolean pins=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_C);
        boolean inside=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_COMMA);
        boolean outside=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_PERIOD);
        if(dev54h15Stage==2 && dev54h15Draft!=null){
            var d=dev54h15hDesign(dev54h15Draft.path());
            if(d!=null){
                boolean h=old&&!dev54h15hLastOld?!d.hideMesh():d.hideMesh();
                boolean g=gen&&!dev54h15hLastGen?!d.hideGenerated():d.hideGenerated();
                boolean c=couple&&!dev54h15hLastCouple?!d.coupling():d.coupling();
                boolean p=pins&&!dev54h15hLastPins?!d.crankpins():d.crankpins();
                float dz=(inside&&!dev54h15hLastIn?.015625f:0f)-
                          (outside&&!dev54h15hLastOut?.015625f:0f);
                float inset=Math.max(-.3f,Math.min(.3f,d.inset()+dz));
                if(h!=d.hideMesh()||g!=d.hideGenerated()||c!=d.coupling()||
                   p!=d.crankpins()||inset!=d.inset()){
                    dev54h15hDraft=new Dev54H15HDesign(d.path(),h,g,c,p,inset);
                    dev54h15Preview=true;
                    dev54h12cMessage("H15H old="+h+" generated="+g+
                        " coupling="+c+" crankpins="+p+" inset="+inset+
                        " | Enter SAVE / Backspace cancel");
                    LOGGER.info("[DEV54H15H-EDIT] model={} hideMesh={} hideGenerated={} "
                        +"coupling={} crankpins={} inward={} committed=false",
                        d.path(),h,g,c,p,inset);
                }
            }
        }
        dev54h15hLastOld=old;dev54h15hLastGen=gen;
        dev54h15hLastCouple=couple;dev54h15hLastPins=pins;
        dev54h15hLastIn=inside;dev54h15hLastOut=outside;
    }
    // The sidecar contains NO world data and never overwrites original TCM.
    // Caller saves it inside H15's already-validated Enter transaction.
    private static void dev54h15hSaveDesign(Dev54H15Rod rig) throws java.io.IOException{
        if(!DEV54H15H_ON)return;
        if(!dev54h15Eligible(rig.path()))throw new java.io.IOException(
            "DEV54H15J accepted Batch 2 design is read-only");
        dev54h15hLoad();
        var d=dev54h15hDraft!=null&&dev54h15hDraft.path().equals(rig.path())?
             dev54h15hDraft:DEV54H15H_SAVED.getOrDefault(rig.path(),dev54h15hDefault(rig));
        if(!dev54h15hValid(d))throw new java.io.IOException("Unsafe H15H design");
        var all=new java.util.TreeMap<String,Dev54H15HDesign>(DEV54H15H_SAVED);
        all.put(d.path(),d);
        var body=new StringBuilder(DEV54H15H_HEADER).append('\n');
        for(var x:all.values())body.append(x.path()).append('\t')
            .append(x.hideMesh()).append('\t').append(x.hideGenerated()).append('\t')
            .append(x.coupling()).append('\t').append(x.crankpins()).append('\t')
            .append(x.inset()).append('\n');
        java.nio.file.Files.createDirectories(DEV54H15H_FILE.getParent());
        var tmp=DEV54H15H_FILE.resolveSibling(DEV54H15H_FILE.getFileName()+".tmp");
        java.nio.file.Files.writeString(tmp,body.toString(),java.nio.charset.StandardCharsets.UTF_8);
        try{java.nio.file.Files.move(tmp,DEV54H15H_FILE,
                  java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(java.nio.file.AtomicMoveNotSupportedException ex){
            java.nio.file.Files.move(tmp,DEV54H15H_FILE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        DEV54H15H_SAVED.put(d.path(),d);
        LOGGER.info("[DEV54H15H-SAVED] model={} coupling={} pins={} hideMesh={} "
            +"hideGenerated={} inset={} file={}",d.path(),d.coupling(),
            d.crankpins(),d.hideMesh(),d.hideGenerated(),d.inset(),DEV54H15H_FILE);
    }
    // Verified wheel centers: paired same-side driving axles; never invent
    // axles from model bounds. Unqualified models keep the main-rod editor
    // but coupling generation is held rather than guessing wheel locations.
    private void dev54h15hDrawCouplings(PoseStack.Pose pose,VertexConsumer out,
                     int light,int overlay,Dev54H15Rod a,float sourcePhase,float u,float v){
        var design=dev54h15hDesign(a.path());
        if(design==null||(!design.coupling()&&!design.crankpins()))return;
        var pivots=this.dev54h15eVerifiedPivots();
        if(pivots.isEmpty()){
            if(DEV54H15H_REPORT.add(a.path()+":NO-VERIFIED-AXLES"))
                LOGGER.warn("[DEV54H15H-HOLD] model={} reason=noVerifiedAxles "
                    +"couplingsNotGenerated=true mainRodUnchanged=true",a.path());
            return;
        }
        boolean editing=dev54h15Editing && dev54h15Draft==a;
        float angle=editing&&dev54h15Preview?
            (float)((dev54h15Ticks*.15d)%(Math.PI*2d)):sourcePhase;
        if(!Float.isFinite(angle))return;
        float rx=a.wx()-a.ax(),ry=a.wy()-a.ay();
        float width=a.width()*.83f;
        float modelCenterZ=dev54h15aaZCenter(a.path());
        for(int side:new int[]{-1,1}){
            var wheels=new java.util.ArrayList<Dev54H15EPivot>();
            for(var w:pivots){
                if(Math.signum(w.z()-modelCenterZ)==side&&Math.abs(w.y()-a.ay())<=.18f)
                    wheels.add(w);
            }
            wheels.sort(java.util.Comparator.comparingDouble(Dev54H15EPivot::x));
            float phase=angle+(side<0&&a.mirrorOpposite()?(float)Math.PI:0f);
            float c=(float)Math.cos(phase),ss=(float)Math.sin(phase);
            float previousX=0f,previousY=0f,previousAxleX=0f;
            boolean previous=false;
            for(var w:wheels){
                float x=w.x()+rx*c-ry*ss,y=w.y()+rx*ss+ry*c;
                float z=w.z()+side*(.042f-design.inset());
                if(design.coupling()&&previous){
                    float axleGap=w.x()-previousAxleX;
                    if(axleGap>.20f&&axleGap<1.95f){
                        dev54h14Bar(pose,out,light,overlay,previousX,previousY,
                             x,y,z,width,.050f,245,u,v,true);
                    }
                }
                if(design.crankpins()){
                    // Short, fully closed cap centered on the orbiting pin.
                    // Placed slightly outward from rod face; rotates in phase.
                    dev54h14Bar(pose,out,light,overlay,x,y-.024f,x,y+.024f,
                         z+side*.018f,Math.max(.032f,width*.70f),.045f,247,u,v,true);
                }
                previous=true;previousX=x;previousY=y;previousAxleX=w.x();
            }
            if(DEV54H15AB_LSSP7_DIAG_ON &&
               a.path().equals("legacy_meshes/locomotive_steam_lssp7.tcm") &&
               DEV54H15AB_LOGGED.add(a.path()+":AXLE_SIDE:"+side))
                LOGGER.info("[DEV54H15AB-LSSP7-COUPLINGS] side={} wheelCount={} firstZ={} " +
                    "sourceGeometry=UNCHANGED oldRods=H15Y_HIDDEN visualPASS=NO",
                    side,wheels.size(),wheels.isEmpty()?Float.NaN:wheels.get(0).z());
            if(DEV54H15H_REPORT.add(a.path()+":AXLES:"+side))
                LOGGER.info("[DEV54H15H-AXLES] model={} side={} verified={} "
                   +"couplingRequested={} pinsRequested={} visualPASS=NO",
                   a.path(),side,wheels.size(),design.coupling(),design.crankpins());
        }
    }

    // DEV54H15K: Coronation and D51 exact-driver wheel-phase alignment.
    // Render-only, keyed to H15E VERIFIED axles; never transform boiler,
    // trucks, unqualified chunks, saved rods, or frozen accepted models.
    // Default puts every qualified driving wheel on the existing H15H rod
    // phase. If authored wheel spokes have a different zero orientation,
    // the player can trim just that axle using N/M inside the editor.
    private static final boolean DEV54H15K_ON=dev54h15kMarker();
    private static boolean dev54h15kMarker(){
        try{
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15K-WHEEL-PHASE-LOCK-ON.txt");
            return java.nio.file.Files.isRegularFile(file) &&
                java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equals("DEV54H15K-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static final String DEV54H15K_HEADER="DEV54H15K-WHEEL-OFFSETS-V1";
    private static final java.nio.file.Path DEV54H15K_FILE=
        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
        .resolve("config/tafaricraft-dev54h15k-wheel-phase.tsv");
    private static final java.util.Map<String,Integer> DEV54H15K_OFFSETS=new java.util.TreeMap<>();
    private static boolean dev54h15kLoaded=false,dev54h15kLastN=false,dev54h15kLastM=false;
    private static final java.util.Set<String> DEV54H15K_REPORT=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15kTarget(String path){
        return path.equals("legacy_meshes/locomotive_steam_coronation_class.tcm") ||
               path.equals("legacy_meshes/locomotive_steam_d51_body.tcm");
    }
    private static String dev54h15kKey(String path,Dev54H15EPivot pivot){
        // Stable source-space coordinate identity, independent of wheel order.
        return path+"\t"+Float.toString(pivot.x())+"\t"+
             Float.toString(pivot.y())+"\t"+Float.toString(pivot.z());
    }
    private static void dev54h15kLoad(){
        if(dev54h15kLoaded || !DEV54H15K_ON)return;
        dev54h15kLoaded=true;
        if(!java.nio.file.Files.isRegularFile(DEV54H15K_FILE))return;
        try{
            var lines=java.nio.file.Files.readAllLines(DEV54H15K_FILE,
                java.nio.charset.StandardCharsets.UTF_8);
            if(lines.isEmpty() || !lines.get(0).equals(DEV54H15K_HEADER))
                throw new java.io.IOException("Invalid phase-offset file header");
            for(int i=1;i<lines.size();i++){
                String line=lines.get(i);if(line.isBlank())continue;
                String[] t=line.split("\t",-1);
                if(t.length!=5 || !dev54h15kTarget(t[0]) ||
                   DEV54H15K_OFFSETS.size()>=32)throw new java.io.IOException("Bad row "+i);
                float x=Float.parseFloat(t[1]),y=Float.parseFloat(t[2]),
                      z=Float.parseFloat(t[3]);
                int degrees=Integer.parseInt(t[4]);
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||
                   Math.abs(x)>20f||Math.abs(y)>20f||Math.abs(z)>4f||
                   degrees < -90 || degrees > 90 || degrees%3!=0)
                    throw new java.io.IOException("Unsafe wheel phase row "+i);
                String key=t[0]+"\t"+t[1]+"\t"+t[2]+"\t"+t[3];
                if(DEV54H15K_OFFSETS.putIfAbsent(key,degrees)!=null)
                    throw new java.io.IOException("Duplicate wheel row "+i);
            }
            LOGGER.info("[DEV54H15K-LOAD] calibratedAxles={} file={}",
                    DEV54H15K_OFFSETS.size(),DEV54H15K_FILE);
        }catch(Exception ex){
            DEV54H15K_OFFSETS.clear();
            // Fail closed. Never silently apply a partial file.
            LOGGER.error("[DEV54H15K-HOLD] Invalid phase-offset file; all offsets zero",ex);
        }
    }
    private static void dev54h15kWrite() throws java.io.IOException{
        java.nio.file.Files.createDirectories(DEV54H15K_FILE.getParent());
        if(java.nio.file.Files.isRegularFile(DEV54H15K_FILE)){
            var backup=DEV54H15K_FILE.resolveSibling(DEV54H15K_FILE.getFileName()+".prev");
            java.nio.file.Files.copy(DEV54H15K_FILE,backup,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var body=new StringBuilder(DEV54H15K_HEADER).append('\n');
        for(var entry:DEV54H15K_OFFSETS.entrySet()){
            if(entry.getValue()!=0)body.append(entry.getKey()).append('\t')
                                          .append(entry.getValue()).append('\n');
        }
        var temp=DEV54H15K_FILE.resolveSibling(DEV54H15K_FILE.getFileName()+".tmp");
        java.nio.file.Files.writeString(temp,body.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        try{java.nio.file.Files.move(temp,DEV54H15K_FILE,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }catch(java.nio.file.AtomicMoveNotSupportedException ex){
            java.nio.file.Files.move(temp,DEV54H15K_FILE,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void dev54h15kKeys(long window){
        if(!DEV54H15K_ON||!dev54h15Editing)return;
        boolean n=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_N);
        boolean m=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_M);
        if((n&&!dev54h15kLastN)||(m&&!dev54h15kLastM)){
            if(!dev54h15fEnsure() || dev54h15fChosen==null ||
               !dev54h15kTarget(dev54h15fChosen.source.getPath())){
                dev54h12cMessage("H15K: select Coronation or D51 wheel with Page Up/Down");
            }else{
                var selected=dev54h15fPivot();
                if(selected!=null){
                    dev54h15kLoad();
                    String key=dev54h15kKey(dev54h15fChosen.source.getPath(),selected);
                    boolean shift=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT) ||
                                  InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);
                    int delta=(m?1:-1)*(shift?3:15);
                    int prior=DEV54H15K_OFFSETS.getOrDefault(key,0);
                    int next=Math.max(-90,Math.min(90,prior+delta));
                    if(prior==next){dev54h12cMessage("H15K phase limit -90 to +90 degrees");}
                    else{
                        DEV54H15K_OFFSETS.put(key,next);
                        try{
                            dev54h15kWrite();
                            dev54h12cMessage("H15K WHEEL "+(dev54h15fIndex+1)+
                                " side="+(selected.z()>0?"+":"-")+" phase="+next+
                                "deg SAVED (N/M, Shift fine; PgUp/PgDn next axle)");
                            LOGGER.info("[DEV54H15K-EDIT-SAVED] model={} axleIndex={} " +
                               "axle=({},{},{}) delta={} phaseDeg={} file={}",
                               dev54h15fChosen.source.getPath(),dev54h15fIndex,
                               selected.x(),selected.y(),selected.z(),delta,next,DEV54H15K_FILE);
                        }catch(Exception ex){
                            DEV54H15K_OFFSETS.put(key,prior);
                            LOGGER.error("[DEV54H15K-SAVE-HOLD] wheel phase not saved",ex);
                            dev54h12cMessage("H15K SAVE HOLD; see latest.log");
                        }
                    }
                }
            }
        }
        dev54h15kLastN=n;dev54h15kLastM=m;
    }
    private traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev54h15kCorrectWheel(String meshKey,int chunk,
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec original){
        if(!DEV54H15K_ON || !dev54h15kTarget(this.source.getPath()) ||
            !DEV54H15H_ON || !DEV54H15J_ON || original==null ||
            !original.rotates() || original.axis()!=2 ||
            !Float.isFinite(original.cos())||!Float.isFinite(original.sin()))return original;
        var qualified=this.dev54h15eVerifiedPivots();
        if(qualified.size()!=8)return original; // both sides, four driver axles
        Dev54H15EPivot match=null;
        for(var pivot:qualified){
            if(Math.abs(original.centerX()-pivot.x())<.025f &&
               Math.abs(original.centerY()-pivot.y())<.025f &&
               Math.abs(original.centerZ()-pivot.z())<.04f){match=pivot;break;}
        }
        if(match==null)return original; // never affect pilot/trailing wheels
        float shared=traincraft.client.renderer.animation.BakedWheelAnimationContext
                         .dev54h13bPartPhase(meshKey);
        if(!Float.isFinite(shared)){
            if(DEV54H15K_REPORT.add(this.source.getPath()+":NO-PHASE"))
                LOGGER.warn("[DEV54H15K-HOLD] model={} no live shared source phase",this.source);
            return original;
        }
        dev54h15kLoad();
        int offset=DEV54H15K_OFFSETS.getOrDefault(dev54h15kKey(this.source.getPath(),match),0);
        float phase=shared+(float)Math.toRadians(offset);
        if(DEV54H15K_REPORT.add(this.source.getPath()+":"+match.x()+":"+match.z()))
            LOGGER.info("[DEV54H15K-LOCK] model={} chunk={} axle=({},{},{}) " +
                "nativeAngle={} sharedAngle={} offsetDeg={} rodPhaseLocked=true",
                this.source.getPath(),chunk,match.x(),match.y(),match.z(),
                Math.atan2(original.sin(),original.cos()),shared,offset);
        return new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec(
               original.kind(),original.axis(),original.centerX(),original.centerY(),
               original.centerZ(),(float)Math.cos(phase),(float)Math.sin(phase),
               original.translateX(),original.translateY(),original.translateZ());
    }



    // DEV54H15M: Shared live driver phase + outboard wheel faces for the two
    // UNACCEPTED D51 locomotives. The H15H rods formerly queried a special
    // H13B phase which the D51 renderers never published (H15K NO-PHASE).
    // Qualified mesh driver transforms DO provide the real travelling angle.
    // Derive ONE per-render angle from the +Z authored driver, then reuse it
    // for every connected wheel, crankpin and both-sided coupling/main rod.
    // No frame timer: a parked engine MUST have stationary rods.
    private static final boolean DEV54H15M_ON=dev54h15mMarker();
    private static final java.util.Set<String> DEV54H15M_REPORT=
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final float DEV54H15M_OUTBOARD=0.09375f; // 1.5 model pixels
    private float dev54h15mSharedPhase=Float.NaN;
    private int dev54h15mDriverCount=0;
    private static boolean dev54h15mMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15M-D51-ROD-WHEEL-SYNC-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equals("DEV54H15M-ON-V1");
        }catch(Exception e){return false;}
    }
    private static boolean dev54h15mTarget(String model){
        return model.equals("legacy_meshes/locomotive_steam_d51_body.tcm") ||
               model.equals("legacy_meshes/locomotive_steam_d51_long_body.tcm");
    }
    private static boolean dev54h15mPivotMatch(
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec t,
        java.util.List<Dev54H15EPivot> pivots){
        if(t==null||!t.rotates()||t.axis()!=2 ||
           !Float.isFinite(t.centerX())||!Float.isFinite(t.centerY())||
           !Float.isFinite(t.centerZ()))return false;
        for(var a:pivots){
            if(Math.abs(a.x()-t.centerX())<.025f &&
               Math.abs(a.y()-t.centerY())<.025f &&
               Math.abs(a.z()-t.centerZ())<.04f)return true;
        }
        return false;
    }
    // Read-only analysis, once per target mesh render, before any vertex is
    // emitted. Positive-Z driver chosen as primary; negative-Z is a guarded
    // fallback only if the mesh contains no usable positive driver transform.
    private void dev54h15mBegin(String meshKey){
        dev54h15mSharedPhase=Float.NaN;dev54h15mDriverCount=0;
        if(!DEV54H15M_ON||!DEV54H15H_ON||!dev54h15mTarget(this.source.getPath()))return;
        var qualified=this.dev54h15eVerifiedPivots();
        if(qualified.size()!=8){
            if(DEV54H15M_REPORT.add(this.source.getPath()+":BAD-PIVOTS"))
                LOGGER.warn("[DEV54H15M-HOLD] model={} verifiedAxles={} expected=8 noChange=true",
                    this.source,qualified.size());
            return;
        }
        float fallback=Float.NaN;
        int n=(this.vertexCount+23)/24;
        for(int ch=0;ch<n;ch++){
            var t=traincraft.client.renderer.animation.BakedWheelAnimationContext
                .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
            if(!dev54h15mPivotMatch(t,qualified)||!Float.isFinite(t.cos())||
               !Float.isFinite(t.sin()))continue;
            float phase=(float)Math.atan2(t.sin(),t.cos());
            if(!Float.isFinite(phase))continue;
            dev54h15mDriverCount++;
            if(!Float.isFinite(fallback))fallback=phase;
            if(t.centerZ()>0f && !Float.isFinite(dev54h15mSharedPhase))
                dev54h15mSharedPhase=phase;
        }
        if(!Float.isFinite(dev54h15mSharedPhase))dev54h15mSharedPhase=fallback;
        if(DEV54H15M_REPORT.add(this.source.getPath()+":PHASE")){
            if(Float.isFinite(dev54h15mSharedPhase))
                LOGGER.info("[DEV54H15M-PHASE-LOCK] model={} verifiedAxles={} " +
                    "matchedWheelChunks={} sourcePhase={} syntheticClock=false",
                    this.source,qualified.size(),dev54h15mDriverCount,dev54h15mSharedPhase);
            else
                LOGGER.warn("[DEV54H15M-HOLD] model={} no native wheel angle; " +
                    "savedRodDesignPreserved=true",this.source);
        }
    }
    private traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev54h15mWheel(String meshKey,int chunk,
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec original){
        if(!DEV54H15M_ON||!dev54h15mTarget(this.source.getPath())||
           !Float.isFinite(dev54h15mSharedPhase)||original==null||
           !dev54h15mPivotMatch(original,this.dev54h15eVerifiedPivots()))return original;
        float phase=dev54h15mSharedPhase;
        // Preserve the already installed H15K hand-calibrated wheel offsets
        // for STANDARD D51, if any. Do not touch Coronation or accepted fleet.
        if(DEV54H15K_ON&&dev54h15kTarget(this.source.getPath())){
            dev54h15kLoad();
            for(var p:this.dev54h15eVerifiedPivots()){
                if(Math.abs(p.x()-original.centerX())<.025f&&
                   Math.abs(p.y()-original.centerY())<.025f&&
                   Math.abs(p.z()-original.centerZ())<.04f){
                    phase+=(float)Math.toRadians(DEV54H15K_OFFSETS.getOrDefault(
                                dev54h15kKey(this.source.getPath(),p),0));break;
                }
            }
        }
        return new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec(
            original.kind(),original.axis(),original.centerX(),original.centerY(),
            original.centerZ(),(float)Math.cos(phase),(float)Math.sin(phase),
            original.translateX(),original.translateY(),original.translateZ());
    }
    private boolean dev54h15mOutboardWheel(
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec t){
        return DEV54H15M_ON&&dev54h15mTarget(this.source.getPath())&&
            Float.isFinite(dev54h15mSharedPhase)&&
            dev54h15mPivotMatch(t,this.dev54h15eVerifiedPivots());
    }

    // DEV54H15L: Restore ONLY the exact saved H12C selections for Coronation and D51.
    // H12C preview is transient and its export is NOT imported on game restart.
    // This independent immutable copy is checksum locked and never writes to the
    // H12C picker CSV or the player's H15H/H15I/H15K save files.
    private static final boolean DEV54H15L_ON=dev54h15lMarker();
    private static final String DEV54H15L_CSV_SHA="A7441C862760A5AA71B15F6BB0D05BCF1A48DBA042E2476E77144DAFC364F7E3";
    private static final java.nio.file.Path DEV54H15L_FILE=
        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
           .resolve("config/tafaricraft-dev54h15l-frozen-hides.csv");
    private static volatile java.util.Map<String,java.util.Set<Integer>> dev54h15lMasks;
    private static final java.util.Set<String> DEV54H15L_REPORT=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15lMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15L-PERSISTENT-MARKED-HIDES-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equals("DEV54H15L-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static synchronized java.util.Map<String,java.util.Set<Integer>> dev54h15lLoad(){
        if(dev54h15lMasks!=null)return dev54h15lMasks;
        java.util.Map<String,java.util.Set<Integer>> verified=java.util.Map.of();
        try{
            byte[] raw=java.nio.file.Files.readAllBytes(DEV54H15L_FILE);
            if(!dev44R11Sha256(raw).equals(DEV54H15L_CSV_SHA))
                throw new java.io.IOException("Saved selection checksum does not match frozen export");
            var lines=java.nio.file.Files.readAllLines(DEV54H15L_FILE,
                 java.nio.charset.StandardCharsets.UTF_8);
            if(lines.isEmpty()||!lines.get(0).replace("\uFEFF", "")
                    .equals("mesh,chunk,action"))
                throw new java.io.IOException("Invalid CSV header");
            var selected=new java.util.HashMap<String,java.util.Set<Integer>>();
            for(int i=1;i<lines.size();i++){
                String[] fields=lines.get(i).split(",",-1);
                if(fields.length!=3||!fields[2].equals("HIDE_CANDIDATE"))
                    throw new java.io.IOException("Invalid selection row "+(i+1));
                String model=fields[0];
                if(model.startsWith("traincraft:"))model=model.substring(11);
                if(!dev54h15kTarget(model))
                    throw new java.io.IOException("Model not approved for hide: "+model);
                int ch=Integer.parseInt(fields[1]);
                int max=model.endsWith("coronation_class.tcm")?392:127;
                if(ch<0||ch>max||!selected.computeIfAbsent(model,k->new java.util.HashSet<>()).add(ch))
                    throw new java.io.IOException("Duplicate/out of range chunk: "+ch);
            }
            String coronation="legacy_meshes/locomotive_steam_coronation_class.tcm";
            String d51="legacy_meshes/locomotive_steam_d51_body.tcm";
            if(selected.size()!=2||selected.getOrDefault(coronation,java.util.Set.of()).size()!=10||
                    selected.getOrDefault(d51,java.util.Set.of()).size()!=44)
                throw new java.io.IOException("Expected exactly 10 Coronation and 44 D51 chunks");
            var frozen=new java.util.HashMap<String,java.util.Set<Integer>>();
            for(var row:selected.entrySet())frozen.put(row.getKey(),java.util.Set.copyOf(row.getValue()));
            verified=java.util.Map.copyOf(frozen);
            LOGGER.info("[DEV54H15L-CSV-VERIFIED] sha256={} Coronation=10 D51=44 sessionPersistent=true",DEV54H15L_CSV_SHA);
        }catch(Exception ex){
            LOGGER.error("[DEV54H15L-HOLD] Persisted hide list invalid: no pieces suppressed",ex);
        }
        dev54h15lMasks=verified;
        return verified;
    }
    private static java.util.Set<Integer> dev54h15lMaskForModel(String path){
        if(!DEV54H15L_ON||!DEV54H15K_ON||!dev54h15kTarget(path))return java.util.Set.of();
        var set=dev54h15lLoad().getOrDefault(path,java.util.Set.of());
        if(DEV54H15L_REPORT.add(path))
            LOGGER.info("[DEV54H15L-{}] model={} hiddenSourceChunks={} editorRods=UNCHANGED wheelPhase=UNCHANGED",
                set.isEmpty()?"HOLD":"RESTORED",path,set.size());
        return set;
    }

    // DEV54H15T: preserve the EXACT saved GS4/Fowler/GWR101 H12C hides.
    // These are additional to H15L Coronation/D51 (54). Independent markers
    // permit retaining accepted hides while disabling the GWR42xx experiment.
    private static final String DEV54H15T_HIDE_SHA="7891E492B870D0FD5DF3D1754D70886E9AB034A6B2822405B5D1F401CF0CEDBF";
    private static final String DEV54H15T_GWR_MESH="legacy_meshes/locomotive_steam_gwr_42xx.tcm";
    private static final boolean DEV54H15T_HIDES_ON=dev54h15tEnabled(
            "DEV54H15T-PERSISTENT-HIDES-ON.txt","DEV54H15T-HIDES-ON-V1");
    private static final boolean DEV54H15T_GWR_ON=dev54h15tEnabled(
            "DEV54H15T-GWR42XX-SOURCE-RODS-ON.txt","DEV54H15T-GWR-ON-V1");
    private static final java.util.Set<String> DEV54H15T_REPORTED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile java.util.Map<String,java.util.Set<Integer>> dev54h15tHides;
    private static boolean dev54h15tEnabled(String filename,String text){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve(filename);
            return java.nio.file.Files.isRegularFile(p) &&
              java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                 .replace("\uFEFF","").trim().equals(text);
        }catch(Exception ex){return false;}
    }
    private static synchronized java.util.Map<String,java.util.Set<Integer>> dev54h15tReadMasks(){
        if(dev54h15tHides!=null)return dev54h15tHides;
        java.util.Map<String,java.util.Set<Integer>> verified=java.util.Map.of();
        try {
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("config/tafaricraft-dev54h15t-frozen-hides.csv");
            var raw=java.nio.file.Files.readAllBytes(f);
            if(!dev44R11Sha256(raw).equals(DEV54H15T_HIDE_SHA))
                throw new java.io.IOException("Changed H15T user hide selection checksum");
            var lines=java.nio.file.Files.readAllLines(f,java.nio.charset.StandardCharsets.UTF_8);
            if(lines.isEmpty()||!lines.get(0).replace("\uFEFF","").equals("mesh,chunk,action"))
                throw new java.io.IOException("Unrecognized H15T selection header");
            var m=new java.util.HashMap<String,java.util.Set<Integer>>();
            for(int i=1;i<lines.size();i++){
                String[] v=lines.get(i).split(",",-1);
                if(v.length!=3||!v[2].equals("HIDE_CANDIDATE")||!v[0].startsWith("traincraft:"))
                    throw new java.io.IOException("Invalid user selection row "+(i+1));
                String key=v[0].substring(11);
                int max=switch(key){
                    case "legacy_meshes/locomotive_steam_gs4.tcm"->128;
                    case "legacy_meshes/locomotive_steam_fowler.tcm"->307;
                    case "legacy_meshes/locomotive_steam_gwr_101_class.tcm"->211;
                    default->throw new java.io.IOException("Unexpected masked model "+key);
                };
                int n=Integer.parseInt(v[1]);
                if(n<0||n>max||!m.computeIfAbsent(key,k->new java.util.HashSet<>()).add(n))
                    throw new java.io.IOException("Duplicate or out-of-bounds mask "+key+":"+n);
            }
            if(m.size()!=3||
              m.getOrDefault("legacy_meshes/locomotive_steam_gs4.tcm",java.util.Set.of()).size()!=21||
              m.getOrDefault("legacy_meshes/locomotive_steam_fowler.tcm",java.util.Set.of()).size()!=6||
              !m.getOrDefault("legacy_meshes/locomotive_steam_gwr_101_class.tcm",java.util.Set.of()).equals(java.util.Set.of(93,94)))
                throw new java.io.IOException("Not exactly GS4=21 Fowler=6 GWR101=2");
            var copy=new java.util.HashMap<String,java.util.Set<Integer>>();
            for(var row:m.entrySet())copy.put(row.getKey(),java.util.Set.copyOf(row.getValue()));
            verified=java.util.Map.copyOf(copy);
            LOGGER.info("[DEV54H15T-HIDES-CSV-VERIFIED] sha={} GS4=21 Fowler=6 GWR101=2 H15L=UNCHANGED",DEV54H15T_HIDE_SHA);
        }catch(Exception ex){LOGGER.error("[DEV54H15T-HIDES-HOLD] No masks applied: {}",ex.toString());}
        dev54h15tHides=verified;
        return verified;
    }
    private static java.util.Set<Integer> dev54h15tHideSet(String mesh){
        if(!DEV54H15T_HIDES_ON)return java.util.Set.of();
        var hidden=dev54h15tReadMasks().getOrDefault(mesh,java.util.Set.of());
        if(!hidden.isEmpty()&&DEV54H15T_REPORTED.add("HIDE:"+mesh))
            LOGGER.info("[DEV54H15T-HIDES-RESTORED] mesh={} userChunks={} acceptedWheels=UNCHANGED",
                mesh,hidden.size());
        return hidden;
    }
    // DEV54H15T: original GWR42xx source rod chunks only; authored 3D vertex/UVs
    // remain untouched, and the accepted wheel transforms stay authoritative.
    private static boolean dev54h15tGwrRod(int i){
        return (i>=9&&i<=18)||i==22||i==278||i==187||i==194;
    }
    // DEV54H15V: correct only the opt-in GWR42xx source-rod geometry. OFF = H15T original.
    private static final boolean DEV54H15V_ON=dev54h15tEnabled(
        "DEV54H15V-GWR42XX-SLIDING-CROSSHEAD-ON.txt","DEV54H15V-GWR42XX-V1");
    private static final float DEV54H15V_RADIUS=.09375f;
    private static final float DEV54H15V_LINK_LENGTH=1.125f;
    private static final float DEV54H15V_ROD_BASE_X=-2.0625f;
    private static final float DEV54H15V_ROD_BASE_Y=.03125f;
    private static float dev54h15vRodAngle(float phase){
        float rise=DEV54H15V_RADIUS*((float)Math.cos(phase)-1f);
        return (float)Math.asin(Math.max(-1f,Math.min(1f,rise/DEV54H15V_LINK_LENGTH)));
    }
    private static float dev54h15vSliderTravel(float phase){
        float rise=DEV54H15V_RADIUS*((float)Math.cos(phase)-1f);
        float crankX=-DEV54H15V_RADIUS*(float)Math.sin(phase);
        // A real crosshead travels ONLY along X; no circular motion at the piston.
        return crankX+DEV54H15V_LINK_LENGTH-
            (float)Math.sqrt(DEV54H15V_LINK_LENGTH*DEV54H15V_LINK_LENGTH-rise*rise);
    }
    private static float[] dev54h15vGwr42Move(int id,float x,float y,float phase){
        float dx=-DEV54H15V_RADIUS*(float)Math.sin(phase);
        float dy=DEV54H15V_RADIUS*((float)Math.cos(phase)-1f);
        float slide=dev54h15vSliderTravel(phase);
        if(id==187||id==194) return new float[]{x+slide,y};
        if(id==9||id==22||(id>=10&&id<=15)){
            // Animate the ORIGINAL source rods and their small attached fittings as ONE
            // rigid linkage, rather than spinning the fittings about a wheel center.
            float a=dev54h15vRodAngle(phase),c=(float)Math.cos(a),s=(float)Math.sin(a);
            float rx=x-DEV54H15V_ROD_BASE_X,ry=y-DEV54H15V_ROD_BASE_Y;
            return new float[]{DEV54H15V_ROD_BASE_X+slide+rx*c-ry*s,
                                DEV54H15V_ROD_BASE_Y+rx*s+ry*c};
        }
        // H15T source geometry places the +Z long coupler 7px above its -Z pair;
        // bring the matching bars to the SAME Y height without touching the TCM.
        if(id==18)return new float[]{x+dx,y+dy-.4375f};
        if(id==278)return new float[]{x+dx,y+dy-.21875f};
        return new float[]{x+dx,y+dy};
    }
    private static float[] dev54h15tGwr42Move(int id,float x,float y,float phase){
        if(DEV54H15V_ON) return dev54h15vGwr42Move(id,x,y,phase);
        final float radius=.09375f;
        final float sn=(float)Math.sin(phase),cs=(float)Math.cos(phase);
        final float dx=-radius*sn,dy=radius*(cs-1f);
        if(id==187||id==194)return new float[]{x+dx,y}; // sliders: X only
        if(id==9||id==22){ // connecting rod pivots around existing piston guide
            float angle=(float)Math.atan2(dy,1.125f);
            float a=(float)Math.cos(angle),b=(float)Math.sin(angle);
            float rx=x+2f,ry=y-.03125f;
            return new float[]{-2f+dx+rx*a-ry*b,.03125f+rx*b+ry*a};
        }
        if(id>=10&&id<=15){ // source crank-side fittings; original local shape
            float rx=x+1.4375f,ry=y-.03125f;
            return new float[]{-1.4375f+rx*cs-ry*sn,.03125f+rx*sn+ry*cs};
        }
        return new float[]{x+dx,y+dy}; // original source coupling bars 16,17,18,278
    }
    private static float dev54h15tRodNormalAngle(int id,float phase){
        if(DEV54H15V_ON) return (id==9||id==22||(id>=10&&id<=15))
            ?dev54h15vRodAngle(phase):0f;
        if(id==9||id==22)return (float)Math.atan2(
            .09375f*((float)Math.cos(phase)-1f),1.125f);
        if(id>=10&&id<=15)return phase;
        return 0f;
    }

    // DEV54H15W: GWR 72xx original-mesh source-rod slider + mirrored calibration.
    // Model-locked and OFF by default. GWR42xx/H15V is not touched.
    private static final String DEV54H15W_GWR_MESH="legacy_meshes/locomotive_steam_gwr_72xx.tcm";
    private static final boolean DEV54H15W_GWR_ON=dev54h15tEnabled(
        "DEV54H15W-GWR72XX-SLIDING-RODS-ON.txt","DEV54H15W-GWR72XX-V1");
    private static final java.util.Set<String> DEV54H15W_REPORTED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15w72Rod(int id){
        return id>=9 && id<=18 || id==24 || id==190 || id==197 || id==280;
    }
    private static final float DEV54H15W_RADIUS=.09375f,DEV54H15W_LINK=1.125f;
    private static final float DEV54H15W_PIVOT_X=-2.375f,DEV54H15W_PIVOT_Y=.03125f;
    private static float dev54h15wAngle(float phase){
        float dy=DEV54H15W_RADIUS*((float)Math.cos(phase)-1f);
        return (float)Math.asin(Math.max(-1f,Math.min(1f,dy/DEV54H15W_LINK)));
    }
    private static float dev54h15wSlide(float phase){
        float dy=DEV54H15W_RADIUS*((float)Math.cos(phase)-1f);
        float dx=-DEV54H15W_RADIUS*(float)Math.sin(phase);
        return dx+DEV54H15W_LINK-
               (float)Math.sqrt(DEV54H15W_LINK*DEV54H15W_LINK-dy*dy);
    }
    private static float[] dev54h15wMove(int id,float x,float y,float phase){
        float dx=-DEV54H15W_RADIUS*(float)Math.sin(phase);
        float dy=DEV54H15W_RADIUS*((float)Math.cos(phase)-1f);
        if(id==190||id==197)return new float[]{x+dev54h15wSlide(phase),y};
        if(id==9||id==24||(id>=10&&id<=15)){
            float a=dev54h15wAngle(phase),c=(float)Math.cos(a),s=(float)Math.sin(a);
            float rx=x-DEV54H15W_PIVOT_X,ry=y-DEV54H15W_PIVOT_Y;
            return new float[]{DEV54H15W_PIVOT_X+dev54h15wSlide(phase)+rx*c-ry*s,
                                DEV54H15W_PIVOT_Y+rx*s+ry*c};
        }
        // The +Z long bar has an authored +7px height, and its nearby guide
        // +3.5px, versus the -Z bar. Rebase only these unaccepted 72xx chunks.
        if(id==18)return new float[]{x+dx,y+dy-.4375f};
        if(id==280)return new float[]{x+dx,y+dy-.21875f};
        return new float[]{x+dx,y+dy};
    }
    private static float dev54h15wNormalAngle(int id,float phase){
        return (id==9||id==24||(id>=10&&id<=15))?dev54h15wAngle(phase):0f;
    }

    // DEV54H15U: exact H12C D51 LONG original-mesh selection, separate from D51.
    // This never edits original TCM, saved 3D rods, axle centers, or wheel phase.
    private static final String DEV54H15U_LONG_MESH="legacy_meshes/locomotive_steam_d51_long_body.tcm";
    private static final String DEV54H15U_MASK_SHA="6D7F26849514BB34972FA7B098B465D94F846DC15115F4D9B84BBA122E35AC16";
    private static final boolean DEV54H15U_ON=dev54h15tEnabled(
        "DEV54H15U-D51LONG-FROZEN-HIDES-ON.txt","DEV54H15U-LONG-HIDES-ON-V1");
    private static volatile java.util.Set<Integer> dev54h15uLongMask;
    private static final java.util.Set<String> DEV54H15U_REPORTED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static synchronized java.util.Set<Integer> dev54h15uLoadLongMask(){
        if(dev54h15uLongMask!=null)return dev54h15uLongMask;
        java.util.Set<Integer> verified=java.util.Set.of();
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve(
                "config/tafaricraft-dev54h15u-d51long-frozen-hides.csv");
            byte[] raw=java.nio.file.Files.readAllBytes(f);
            if(!dev44R11Sha256(raw).equals(DEV54H15U_MASK_SHA))
                throw new java.io.IOException("Unexpected D51 Long hide CSV checksum");
            var lines=java.nio.file.Files.readAllLines(f,java.nio.charset.StandardCharsets.UTF_8);
            if(lines.size()!=34 || !lines.get(0).replace("\uFEFF", "").equals("mesh,chunk,action"))
                throw new java.io.IOException("Expected exactly 33 user hide rows");
            var mask=new java.util.HashSet<Integer>();
            for(int k=1;k<lines.size();k++){
                String[] col=lines.get(k).split(",",-1);
                if(col.length!=3 ||
                   !col[0].equals("traincraft:"+DEV54H15U_LONG_MESH) ||
                   !col[2].equals("HIDE_CANDIDATE"))
                    throw new java.io.IOException("Unexpected D51 Long selection row "+k);
                int id=Integer.parseInt(col[1]);
                if(id<0 || id>=128 || !mask.add(id))
                    throw new java.io.IOException("Out of bounds or repeated chunk "+id);
            }
            if(mask.size()!=33)throw new java.io.IOException("Expected 33 unique D51 Long chunks");
            verified=java.util.Set.copyOf(mask);
            LOGGER.info("[DEV54H15U-D51LONG-HIDES-VERIFIED] original=YES chunks=33 csvsha={} wheelAndNewRods=UNTOUCHED",DEV54H15U_MASK_SHA);
        }catch(Exception ex){
            LOGGER.error("[DEV54H15U-D51LONG-HIDES-HOLD] No D51 Long hides applied: {}",ex.toString());
        }
        dev54h15uLongMask=verified;
        return verified;
    }
    private static java.util.Set<Integer> dev54h15uLongHideSet(String path){
        if(!DEV54H15U_ON || !DEV54H15U_LONG_MESH.equals(path))return java.util.Set.of();
        java.util.Set<Integer> result=dev54h15uLoadLongMask();
        if(!result.isEmpty() && DEV54H15U_REPORTED.add(path))
            LOGGER.info("[DEV54H15U-D51LONG-HIDES-RESTORED] model={} chunks={} D51=UNCHANGED H15T=UNCHANGED",path,result.size());
        return result;
    }

    // DEV54H15AH: J50-only, independently switchable stationary red fittings.
    // Their original OBJ/TCM vertices and normals are restored AFTER all earlier
    // wheel/rod transforms, without touching the accepted wheel pipeline.
    private static final boolean DEV54H15AH_J50_STATIC_ON = dev54h15tEnabled(
            "DEV54H15AH-J50-STATIC-ON.txt", "DEV54H15AH-J50-ON-V1");
    private static final java.util.Set<String> DEV54H15AH_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();


    // DEV54H15AL: recovered original H12C S100 UK/US user selections from
    // latest(20261019-041102).log BEFORE the Skookum temporary-picker reset.
    // Independent source hide ONLY. Every original TCM byte and accepted rod,
    // crankpin and native wheel animation remain unchanged.
    private static final boolean DEV54H15AL_S100_HIDES_ON =
        dev54h15tEnabled("DEV54H15AL-S100-HIDES-ON.txt", "DEV54H15AL-S100-HIDES-ON-V1");
    private static final java.util.Set<Integer> DEV54H15AL_UK =
        java.util.Set.of(405, 406, 407, 410, 420, 421, 422, 426, 432, 436, 438, 440, 441, 444, 448, 453, 458, 459, 460, 461);
    private static final java.util.Set<Integer> DEV54H15AL_US =
        java.util.Set.of(330, 331, 332, 335, 349, 350, 351, 414, 438, 440, 442, 443, 444, 446, 449, 450, 451, 455, 458, 460, 461, 462, 509);
    private static final java.util.Set<String> DEV54H15AL_LOGGED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean dev54h15alHideS100(int chunk){
        if(!DEV54H15AL_S100_HIDES_ON || !this.dev44R11Verified)return false;
        java.util.Set<Integer> chosen;
        String label;
        switch(this.source.getPath()){
            case "legacy_meshes/locomotive_steam_s100_uk.tcm" -> {
                chosen=DEV54H15AL_UK;label="UK";
            }
            case "legacy_meshes/locomotive_steam_s100_us.tcm" -> {
                chosen=DEV54H15AL_US;label="US";
            }
            default -> {return false;}
        }
        if(!chosen.contains(chunk))return false;
        // Preserve ANY rotating wheel source chunk, including future wheel IDs.
        var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
                .spec("legacy:"+this.source,chunk,this.vertices,FLOATS_PER_VERTEX);
        if(spec!=null && spec.rotates()){
            if(DEV54H15AL_LOGGED.add(label+":wheel:"+chunk))
                LOGGER.warn("[DEV54H15AL-S100-WHEEL-PROTECTED] side={} chunk={} notHidden=true",label,chunk);
            return false;
        }
        if(DEV54H15AL_LOGGED.add(label))
            LOGGER.info("[DEV54H15AL-S100-ORIGINAL-HIDES] model={} selected={} "
                    +"rotatingWheels=PRESERVED sourceTCM=UNCHANGED visualPASS=NO",label,chosen.size());
        return true;
    }


    // DEV54H15AM (Batch 6): exact user-exported hides and two opt-in rod pilots.
    // Source TCMs, accepted baked wheel motion, and all saved editor TSVs are frozen.
    private static final String DEV54H15AM_U57="legacy_meshes/locomotive_steam_u57.tcm";
    private static final String DEV54H15AM_VB="legacy_meshes/locomotive_steam_vb_0_4_0.tcm";
    private static final boolean DEV54H15AM_HIDES_ON=dev54h15tEnabled(
        "DEV54H15AM-B6-HIDES-ON.txt","DEV54H15AM-HIDES-ON-V1");
    private static final boolean DEV54H15AM_U57_ON=dev54h15tEnabled(
        "DEV54H15AM-U57-XAXIS-RODS-ON.txt","DEV54H15AM-U57-ON-V1");
    private static final boolean DEV54H15AM_VB_ON=dev54h15tEnabled(
        "DEV54H15AM-VB040-RODS-PICKER-ON.txt","DEV54H15AM-VB-ON-V1");
    private static final java.util.Map<String,java.util.Set<Integer>> DEV54H15AM_MARKED=
        java.util.Map.ofEntries(
        java.util.Map.entry("legacy_meshes/locomotive_steam_southern1102.tcm",java.util.Set.of(34,36,37,38,39,40,41,42,43,44,45,46,47,52,53,275,276,277,278,279,280,281,282,283,284,285,286,287,288,289,290,291,292,293,294,295,301)),
        java.util.Map.entry("legacy_meshes/locomotive_steam_u57.tcm",java.util.Set.of(3,6,8,35,58,59)),
        java.util.Map.entry("legacy_meshes/locomotive_steam_vb_0_4_0.tcm",java.util.Set.of(9,10)),
        java.util.Map.entry("legacy_meshes/locomotive_steam_wwcp_062t.tcm",java.util.Set.of(25,27,28,29,30,31,95,155))
        );
    private static final java.util.Set<String> DEV54H15AM_MESSAGES=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Dev54H15AMVbFace(int chunk,float x,float y,float z,float dx,float dy){}
    private transient java.util.List<Dev54H15AMVbFace> dev54h15amVbFaces;

    // The VB pilot ONLY accepts a 2+2 wheel-face topology parsed from the
    // untouched source buffer. No wheel center is invented from the hull.
    // A non-verifiable model has zero VB rods and zero editor candidates.
    private java.util.List<Dev54H15AMVbFace> dev54h15amVbFaces(){
        if(dev54h15amVbFaces!=null)return dev54h15amVbFaces;
        if(!DEV54H15AM_VB_ON || !DEV54H15AM_VB.equals(this.source.getPath()))
            return dev54h15amVbFaces=java.util.List.of();
        var all=new java.util.ArrayList<Dev54H15AMVbFace>();
        for(int ch=0;ch<this.vertexCount/24;ch++){
            int start=ch*24*FLOATS_PER_VERTEX;
            if(start+24*FLOATS_PER_VERTEX>this.vertices.length)break;
            float lx=Float.POSITIVE_INFINITY,ly=lx,lz=lx;
            float hx=Float.NEGATIVE_INFINITY,hy=hx,hz=hx;
            for(int v=0;v<24;v++){
                int o=start+v*FLOATS_PER_VERTEX;
                float x=this.vertices[o],y=this.vertices[o+1],z=this.vertices[o+2];
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))continue;
                lx=Math.min(lx,x);ly=Math.min(ly,y);lz=Math.min(lz,z);
                hx=Math.max(hx,x);hy=Math.max(hy,y);hz=Math.max(hz,z);
            }
            float dx=hx-lx,dy=hy-ly,dz=hz-lz;
            float cx=(hx+lx)*.5f,cy=(hy+ly)*.5f,cz=(hz+lz)*.5f;
            // A square in the longitudinal X/Y wheel plane, narrow along Z.
            // Reject roof/boiler rectangles and deep structural blocks.
            if(dx<.22f||dy<.22f||dx>1.05f||dy>1.05f||
               Math.abs(dx-dy)>.09f||dz>.28f||cy>this.minY+1.53f||
               Math.abs(cz)<.24f||Math.abs(cz)>this.maxZ+.025f)continue;
            boolean duplicate=false;
            for(var q:all){if(Math.abs(q.x()-cx)<.035f&&
                 Math.abs(q.y()-cy)<.035f&&Math.abs(q.z()-cz)<.065f){
                 duplicate=true;break;}}
            if(!duplicate)all.add(new Dev54H15AMVbFace(ch,cx,cy,cz,dx,dy));
        }
        // Two longitudinal centers, one on each side of each axle.
        if(all.size()!=4)return dev54h15amVbHold(all.size());
        all.sort(java.util.Comparator.comparingDouble(Dev54H15AMVbFace::z));
        var left=new java.util.ArrayList<Dev54H15AMVbFace>();
        var right=new java.util.ArrayList<Dev54H15AMVbFace>();
        for(var f:all){(f.z()<0?left:right).add(f);}
        if(left.size()!=2||right.size()!=2)return dev54h15amVbHold(all.size());
        left.sort(java.util.Comparator.comparingDouble(Dev54H15AMVbFace::x));
        right.sort(java.util.Comparator.comparingDouble(Dev54H15AMVbFace::x));
        float length=left.get(1).x()-left.get(0).x();
        if(length<.36f||length>2.10f ||
           Math.abs(right.get(1).x()-right.get(0).x()-length)>.08f ||
           Math.abs(left.get(0).x()-right.get(0).x())>.07f ||
           Math.abs(left.get(1).x()-right.get(1).x())>.07f ||
           Math.abs(left.get(0).y()-left.get(1).y())>.07f ||
           Math.abs(right.get(0).y()-right.get(1).y())>.07f ||
           Math.abs(left.get(0).y()-right.get(0).y())>.07f ||
           Math.abs(left.get(0).z()+right.get(0).z())>.12f ||
           Math.abs(left.get(1).z()+right.get(1).z())>.12f ||
           Math.abs(left.get(0).dx()-left.get(1).dx())>.08f)
            return dev54h15amVbHold(all.size());
        if(DEV54H15AM_MESSAGES.add("VB-VERIFIED"))
            LOGGER.info("[DEV54H15AM-VB-VERIFIED] sourceWheels=4 left=2 right=2 " +
                "axles=2 parser=SOURCE_BUFFER noGuessedHullPivots=true visualPASS=NO");
        return dev54h15amVbFaces=java.util.List.copyOf(all);
    }
    private java.util.List<Dev54H15AMVbFace> dev54h15amVbHold(int candidates){
        if(DEV54H15AM_MESSAGES.add("VB-HOLD"))
            LOGGER.warn("[DEV54H15AM-VB-HOLD] sourceCandidateFaces={} required=4 " +
                "bilateralTopologyUnverified=true originalWheelsUnchanged=true",candidates);
        return dev54h15amVbFaces=java.util.List.of();
    }
    private boolean dev54h15amHideB6(int chunk){
        if(!DEV54H15AM_HIDES_ON || !this.dev44R11Verified)return false;
        var marked=DEV54H15AM_MARKED.get(this.source.getPath());
        if(marked==null||!marked.contains(chunk))return false;
        // VB chunks 9/10 stay intact unless the same guarded VB source-wheel
        // classification is enabled and succeeds. Never hide unqualified
        // 0-4-0 geometry solely because the old picker missed wheel faces.
        if(DEV54H15AM_VB.equals(this.source.getPath()) &&
           (!DEV54H15AM_VB_ON || dev54h15amVbFaces().size()!=4)){
            if(DEV54H15AM_MESSAGES.add("VB-HIDES-HOLD"))
                LOGGER.warn("[DEV54H15AM-VB-HIDES-HOLD] " +
                   "runVBSourceWheelVerificationFirst=true noVBChunksHidden=true");
            return false;
        }
        var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
            "legacy:"+this.source,chunk,this.vertices,FLOATS_PER_VERTEX);
        if((spec!=null&&spec.rotates())||
          (DEV54H15AM_VB.equals(this.source.getPath()) &&
              dev54h15amVbFaces().stream().anyMatch(f->f.chunk()==chunk))){
           if(DEV54H15AM_MESSAGES.add("PROTECTED:"+this.source.getPath()+":"+chunk))
              LOGGER.warn("[DEV54H15AM-HIDE-HOLD] model={} chunk={} isWheel=true",
                  this.source.getPath(),chunk);
           return false;
        }
        if(DEV54H15AM_MESSAGES.add("HIDES:"+this.source.getPath()))
            LOGGER.info("[DEV54H15AM-HIDES] model={} marked={} animatedWheels=PRESERVED " +
                "originalTCM=UNCHANGED visualPASS=NO",this.source.getPath(),marked.size());
        return true;
    }
    // DEV54H15AN: isolated Batch-6 visibility and crankpin correction.
    // Default OFF. H15AM remains authoritative for legacy hides and wheel verification.
    private static final boolean DEV54H15AN_U57_ON=dev54h15tEnabled(
        "DEV54H15AN-U57-CONNECTIONS-ON.txt", "DEV54H15AN-U57-ON-V1");
    private static final boolean DEV54H15AN_VB_ON=dev54h15tEnabled(
        "DEV54H15AN-VB040-CONNECTIONS-ON.txt", "DEV54H15AN-VB040-ON-V1");
    private static final java.util.Set<String> DEV54H15AN_REPORTED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    // The H15AM trial used (.50,.45) for both models. Some rolling-stock skins
    // make this coordinate fully transparent. The accepted H14 renderer samples
    // a source-authored rod face instead. Mirror that approach, never rewrite skins.
    private float[] dev54h15anSourceUV(int sourceRodChunk,int verifiedWheelChunk){
        for(int chunk:new int[]{sourceRodChunk,verifiedWheelChunk}){
            int base=chunk*24*FLOATS_PER_VERTEX;
            if(base<0||base+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
            float u=0f,v=0f;
            for(int i=0;i<4;i++){
                int off=base+i*FLOATS_PER_VERTEX;
                u+=this.vertices[off+3];v+=this.vertices[off+4];
            }
            u*=.25f;v*=.25f;
            if(Float.isFinite(u)&&Float.isFinite(v)&&u>=0f&&u<=1f&&v>=0f&&v<=1f)
                return new float[]{u,v};
        }
        return new float[]{.50f,.45f};
    }

    private void dev54h15anDrawU57(PoseStack.Pose pose,VertexConsumer out,
           int light,int overlay,float phase){
        // Wheel positions are exact-source-verified by caller, unchanged.
        float[] uv=dev54h15anSourceUV(3,46);
        float radius=.155f;
        float sn=(float)Math.sin(phase),cs=(float)Math.cos(phase);
        for(int side:new int[]{-1,1}){
            // Beyond each wheel's real +/-0.4378 outside face, not embedded in it.
            float rodX=side*.5100f,studX=side*.475f;
            float pinY=.375f+radius*cs;
            float[] pinZ=new float[5];
            for(int i=0;i<5;i++)pinZ[i]=DEV54H15AM_U57_Z[i]+radius*sn;
            // Four continuous spans connect all five wheels on each side.
            for(int i=1;i<5;i++)dev54h15amBarYZ(pose,out,light,overlay,
                pinY,pinZ[i-1],pinY,pinZ[i],rodX,.080f,.055f,247,uv[0],uv[1]);
            for(int i=0;i<5;i++){
                // X-depth bridge physically overlaps the wheel and outer bar.
                dev54h15amBarYZ(pose,out,light,overlay,
                    pinY-.045f,pinZ[i],pinY+.045f,pinZ[i],
                    studX,.084f,.155f,250,uv[0],uv[1]);
            }
        }
        if(DEV54H15AN_REPORTED.add("U57"))
           LOGGER.info("[DEV54H15AN-U57-LIVE] wheels=10 spans=8 studBridges=10 " +
             "sourceSampledUV=true exteriorWheelFace=YES sameWheelPhase=true visualPASS=NO");
    }
    private float dev54h15anOuterFaceZ(int chunk,int side){
        int base=chunk*24*FLOATS_PER_VERTEX;
        if(base<0||base+24*FLOATS_PER_VERTEX>this.vertices.length)return Float.NaN;
        float outer=side>0?Float.NEGATIVE_INFINITY:Float.POSITIVE_INFINITY;
        for(int i=0;i<24;i++){
            float z=this.vertices[base+i*FLOATS_PER_VERTEX+2];
            if(!Float.isFinite(z))return Float.NaN;
            outer=side>0?Math.max(outer,z):Math.min(outer,z);
        }
        return outer;
    }
    private void dev54h15anDrawVB(PoseStack.Pose pose,VertexConsumer out,
           int light,int overlay,float phase,java.util.List<Dev54H15AMVbFace> wheels){
        float[] uv=dev54h15anSourceUV(9,wheels.get(0).chunk());
        float dy=.125f*(float)Math.cos(phase),dx=-.125f*(float)Math.sin(phase);
        for(int side:new int[]{-1,1}){
            var pair=new java.util.ArrayList<Dev54H15AMVbFace>();
            for(var f:wheels)if((f.z()>0?1:-1)==side)pair.add(f);
            pair.sort(java.util.Comparator.comparingDouble(Dev54H15AMVbFace::x));
            if(pair.size()!=2)return;
            float z0=dev54h15anOuterFaceZ(pair.get(0).chunk(),side);
            float z1=dev54h15anOuterFaceZ(pair.get(1).chunk(),side);
            if(!Float.isFinite(z0)||!Float.isFinite(z1)||Math.abs(z0-z1)>.09f)return;
            float surfaceZ=(z0+z1)*.5f;
            float barZ=surfaceZ+side*.080f;
            float aX=pair.get(0).x()+dx,bX=pair.get(1).x()+dx;
            float aY=pair.get(0).y()+dy,bY=pair.get(1).y()+dy;
            // One solid rod per side across both source-confirmed wheel pins.
            dev54h14Bar(pose,out,light,overlay,aX,aY,bX,bY,
                barZ,.080f,.050f,249,uv[0],uv[1],true);
            // Caps bridge from source wheel face to outward rod so they visibly join.
            for(int i=0;i<2;i++){
                float x=i==0?aX:bX,y=i==0?aY:bY;
                dev54h14Bar(pose,out,light,overlay,x,y-.045f,x,y+.045f,
                   surfaceZ+side*.046f,.082f,.125f,250,uv[0],uv[1],true);
            }
        }
        if(DEV54H15AN_REPORTED.add("VB040"))
           LOGGER.info("[DEV54H15AN-VB040-LIVE] verifiedSourceWheels=4 bars=2 " +
             "studBridges=4 sourceSampledUV=true exteriorWheelFace=YES " +
             "sameWheelPhase=true visualPASS=NO");
    }

    private static final int[] DEV54H15AM_U57_CHUNKS={46,57,0,1,60,2,61,13,62,24};
    private static final float[] DEV54H15AM_U57_Z={.4375f,1.125f,1.8125f,2.5f,3.1875f};
    private boolean dev54h15amU57SourceVerified(){
        if(!DEV54H15AM_U57_ON||!this.dev44R11Verified||
           !DEV54H15AM_U57.equals(this.source.getPath()))return false;
        String key="legacy:"+this.source;
        for(int i=0;i<10;i++){
            int ch=DEV54H15AM_U57_CHUNKS[i];
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
               key,ch,this.vertices,FLOATS_PER_VERTEX);
            float wantX=i%2==0?.40625f:-.40625f;
            if(w==null||!w.rotates()||w.axis()!=0||
                Math.abs(w.centerX()-wantX)>.005f||
                Math.abs(w.centerY()-.375f)>.005f||
                Math.abs(w.centerZ()-DEV54H15AM_U57_Z[i/2])>.005f){
                if(DEV54H15AM_MESSAGES.add("U57-HOLD"))
                   LOGGER.warn("[DEV54H15AM-U57-HOLD] verifiedWheelChunk={} " +
                      "expectedAxis=X originalWheelMotion=UNCHANGED rods=OFF",ch);
                return false;
            }
        }
        return true;
    }
    // Solid six-face coupling bars for wheels whose rotation axis is X:
    // U57's five driver stations run along Z, not the fleet's usual X.
    private static void dev54h15amBarYZ(PoseStack.Pose pose,VertexConsumer out,
          int light,int overlay,float y0,float z0,float y1,float z1,
          float x,float width,float depth,int rgb,float u,float v){
        float dy=y1-y0,dz=z1-z0,len=(float)Math.hypot(dy,dz);
        if(!Float.isFinite(len)||len<.030f||len>3.8f)return;
        float ny=-dz/len*width*.5f,nz=dy/len*width*.5f;
        float[][] q={{x-depth*.5f,y0+ny,z0+nz},
            {x-depth*.5f,y0-ny,z0-nz},{x-depth*.5f,y1-ny,z1-nz},
            {x-depth*.5f,y1+ny,z1+nz},{x+depth*.5f,y0+ny,z0+nz},
            {x+depth*.5f,y0-ny,z0-nz},{x+depth*.5f,y1-ny,z1-nz},
            {x+depth*.5f,y1+ny,z1+nz}};
        int[][] faces={{0,1,2,3},{7,6,5,4},{4,5,1,0},
                 {3,2,6,7},{4,0,3,7},{1,5,6,2}};
        for(int[] f:faces)dev54h14Quad(pose,out,light,overlay,
           new float[][]{q[f[0]],q[f[3]],q[f[2]],q[f[1]]},rgb,u,v);
    }
    private void dev54h15amDrawU57(PoseStack.Pose pose,VertexConsumer out,
          int light,int overlay,float phase){
        if(!dev54h15amU57SourceVerified())return;
        if(!Float.isFinite(phase)){
            if(DEV54H15AM_MESSAGES.add("U57-NO-PHASE"))
                LOGGER.warn("[DEV54H15AM-U57-HOLD] noVerifiablePhase rods=OFF");
            return;
        }
        if(DEV54H15AN_U57_ON){
            dev54h15anDrawU57(pose,out,light,overlay,phase);
            return;
        }
        for(int side:new int[]{-1,1}){
            float x=side*.40625f+side*.0575f;
            float angle=phase;
            float y=.375f+.135f*(float)Math.cos(angle);
            float shift=.135f*(float)Math.sin(angle);
            for(int i=0;i<5;i++){
                float z=DEV54H15AM_U57_Z[i]+shift;
                if(i>0)dev54h15amBarYZ(pose,out,light,overlay,
                     y,DEV54H15AM_U57_Z[i-1]+shift,y,z,
                     x,.072f,.042f,241,.50f,.45f);
                dev54h15amBarYZ(pose,out,light,overlay,
                    y-.026f,z,y+.026f,z,x+side*.022f,.067f,.038f,245,.50f,.45f);
            }
        }
        if(DEV54H15AM_MESSAGES.add("U57-LIVE"))
           LOGGER.info("[DEV54H15AM-U57-LIVE] 10 exact baked X-axis wheels verified " +
               "5 per side couplingSegments=8 crankpins=10 sourceWheels=FROZEN visualPASS=NO");
    }
    private void dev54h15amDrawVB(PoseStack.Pose pose,VertexConsumer out,
          int light,int overlay,float phase){
        if(!DEV54H15AM_VB_ON||!this.dev44R11Verified||
           !DEV54H15AM_VB.equals(this.source.getPath()))return;
        var wheels=dev54h15amVbFaces();
        if(wheels.size()!=4 || !Float.isFinite(phase)){
           if(DEV54H15AM_MESSAGES.add("VB-PHASE-HOLD"))
              LOGGER.warn("[DEV54H15AM-VB-HOLD] 4SourceWheelsAndPhaseRequired=true");
           return;
        }
        if(DEV54H15AN_VB_ON){
            dev54h15anDrawVB(pose,out,light,overlay,phase,wheels);
            return;
        }
        for(int side:new int[]{-1,1}){
            var pair=new java.util.ArrayList<Dev54H15AMVbFace>();
            for(var f:wheels)if((f.z()>0?1:-1)==side)pair.add(f);
            pair.sort(java.util.Comparator.comparingDouble(Dev54H15AMVbFace::x));
            if(pair.size()!=2)return;
            float r=.115f;
            float dy=r*(float)Math.cos(phase),dx=-r*(float)Math.sin(phase);
            float z=pair.get(0).z()+side*.051f;
            float x0=pair.get(0).x()+dx,y0=pair.get(0).y()+dy;
            float x1=pair.get(1).x()+dx,y1=pair.get(1).y()+dy;
            dev54h14Bar(pose,out,light,overlay,x0,y0,x1,y1,z,
                .071f,.045f,243,.50f,.45f,true);
            dev54h14Bar(pose,out,light,overlay,x0,y0-.024f,x0,y0+.024f,
                z+side*.025f,.067f,.041f,246,.50f,.45f,true);
            dev54h14Bar(pose,out,light,overlay,x1,y1-.024f,x1,y1+.024f,
                z+side*.025f,.067f,.041f,246,.50f,.45f,true);
        }
        if(DEV54H15AM_MESSAGES.add("VB-LIVE"))
           LOGGER.info("[DEV54H15AM-VB-LIVE] fourSourceWheels=VERIFIED " +
               "rodBars=2 crankcaps=4 sourceWheels=UNCHANGED visualPASS=NO");
    }

    // DEV54H15AK: Skookum exact eight-driver source wheel groups. Independent
    // preview/full-source cleanup switches; untouched wheel transforms/pivots.
    private static final String DEV54H15AK_SK_MESH =
        "legacy_meshes/locomotive_steam_skookum.tcm";
    private static final boolean DEV54H15AK_SK_RODS_ON =
        dev54h15tEnabled("DEV54H15AK-SKOOKUM-RODS-ON.txt", "DEV54H15AK-SK-RODS-ON-V1");
    private static final boolean DEV54H15AK_SK_HIDES_ON =
        dev54h15tEnabled("DEV54H15AK-SKOOKUM-HIDES-ON.txt", "DEV54H15AK-SK-HIDES-ON-V1");
    private static final java.util.Set<String> DEV54H15AK_REPORTED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean dev54h15akHideSkookum(int chunk){
        if(!DEV54H15AK_SK_HIDES_ON || !this.dev44R11Verified ||
           !DEV54H15AK_SK_MESH.equals(this.source.getPath()) ||
           chunk<121 || chunk>144) return false;
        // Hold any future source metadata change where a marked chunk becomes
        // a rotating wheel, even though current deep profile shows 0 overlap.
        var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
            .spec("legacy:"+this.source,chunk,this.vertices,FLOATS_PER_VERTEX);
        if(spec!=null && spec.rotates()){
            if(DEV54H15AK_REPORTED.add("wheel:"+chunk))
                LOGGER.warn("[DEV54H15AK-SK-HIDE-HOLD] chunk={} is animated wheel; PRESERVED",chunk);
            return false;
        }
        if(DEV54H15AK_REPORTED.add("hides"))
            LOGGER.info("[DEV54H15AK-SK-HIDES] candidates=121..144 wheelIds=55,56,57,58,60,61,64,65 untouched sourceTCM=UNCHANGED visualPASS=NO");
        return true;
    }
    private void dev54h15akDrawSkookum(PoseStack.Pose pose,VertexConsumer out,
                  int light,int overlay,float phase){
        if(!DEV54H15AK_SK_RODS_ON || !this.dev44R11Verified ||
           !DEV54H15AK_SK_MESH.equals(this.source.getPath())) return;
        if(!Float.isFinite(phase)){
            if(DEV54H15AK_REPORTED.add("no-phase"))
                LOGGER.warn("[DEV54H15AK-SK-RODS-HOLD] No verified wheel phase; rods not drawn");
            return;
        }
        // VERIFIED Skookum 2026-10-19 driver wheel pivots:
        // 4 driver axle X values -1.21875,-.59375,.46875,1.09375
        // BOTH side planes Z=-.3125 and +.4375, Y=.34375.
        // Add only a front connecting link on each of TWO drive groups per
        // side (4 rods total). Pre-existing wheel/coupling rods untouched.
        final float cy=.34375f, radius=.083f, len=.81f;
        for(float z:new float[]{-.3125f,.4375f}){
            float outward=z<0f?-1f:1f;
            float rodZ=z+outward*.067f;
            for(float cx:new float[]{-1.21875f,.46875f}){
                float pinX=cx+radius*(float)Math.cos(phase);
                float pinY=cy+radius*(float)Math.sin(phase);
                float dy=cy-pinY;
                float offset=(float)Math.sqrt(Math.max(0f,len*len-dy*dy));
                float slideX=pinX-offset;
                dev54h14Bar(pose,out,light,overlay,pinX,pinY,
                    slideX,cy,rodZ,.060f,.051f,246,.47f,.48f,true);
                // Closed pivot/slider ends, local paint from source rod UV.
                dev54h14Bar(pose,out,light,overlay,pinX,pinY-.026f,
                    pinX,pinY+.026f,rodZ+outward*.015f,.046f,.044f,
                    247,.47f,.48f,true);
                dev54h14Bar(pose,out,light,overlay,slideX-.055f,cy,
                    slideX+.055f,cy,rodZ,.040f,.041f,247,.47f,.48f,true);
            }
        }
        if(DEV54H15AK_REPORTED.add("rods"))
            LOGGER.info("[DEV54H15AK-SK-RODS] bilateral=true groupsPerSide=2 totalLinks=4 nativeDriverChunks=8 unchanged sourcePhase=ACCEPTED visualPASS=NO");
    }

    // DEV54H15AI BATCH 5 user-marked original chunk cleanup, independent of
    // the S100 six-wheel picker and accepted Batches 1-4. Source CSV has 97
    // requested IDs, BUT PE chunk 35 is a SOURCE-VERIFIED DRIVING WHEEL and is
    // protected, leaving 96 safely eligible IDs. Original TCMs unchanged.
    private static final boolean DEV54H15AI_HIDE_ON =
        dev54h15tEnabled("DEV54H15AI-PE-RW-USER-HIDES-ON.txt", "DEV54H15AI-HIDES-ON-V1");
    private static final java.util.Set<Integer> DEV54H15AI_PE =
        java.util.Set.of(8, 83, 84, 127, 128, 162, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251);
    private static final java.util.Set<Integer> DEV54H15AI_RW2 =
        java.util.Set.of(59, 60, 61, 62, 64, 71, 76, 78, 82);
    private static final java.util.Set<Integer> DEV54H15AI_RW3 =
        java.util.Set.of(113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147);
    private static final java.util.Set<String> DEV54H15AI_HIDE_REPORTED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean dev54h15aiHideBatch5Source(int chunk,boolean verified){
        if(!DEV54H15AI_HIDE_ON || !verified)return false;
        String path=this.source.getPath();
        java.util.Set<Integer> picked;
        String id;
        switch(path){
            case "legacy_meshes/locomotive_steam_pe.tcm" -> {picked=DEV54H15AI_PE;id="PE";}
            case "legacy_meshes/locomotive_steam_rw_type_2.tcm" -> {picked=DEV54H15AI_RW2;id="RW2";}
            case "legacy_meshes/locomotive_steam_rw_type_3.tcm" -> {picked=DEV54H15AI_RW3;id="RW3";}
            default -> {return false;}
        }
        if(!picked.contains(chunk))return false;
        var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec("legacy:"+this.source,chunk,this.vertices,FLOATS_PER_VERTEX);
        if(spec!=null && spec.rotates()){
            if(DEV54H15AI_HIDE_REPORTED.add(id+":wheelProtected:"+chunk))
                LOGGER.warn("[DEV54H15AI-WHEEL-PROTECTED] model={} chunk={} autoHide=false",id,chunk);
            return false;
        }
        if(DEV54H15AI_HIDE_REPORTED.add(id))
            LOGGER.info("[DEV54H15AI-USER-ORIGINAL-HIDES] model={} selected={} " +
                "sourceTCM=UNCHANGED verifiedWheels=PRESERVED batch4=FROZEN",id,picked.size());
        return true;
    }

    // DEV54H15Y: BATCH 4 source-rod strip in preparation for the user's OWN rods.
    // Guarded by H15X exact source baseline and dev44R11Verified per TCM; OFF by default.
    // Never edits TCM vertices, models, accepted running gear, or axle/piston animators.
    // GWR72xx/Hall/J50/LSSP7 only: Steam Heavy and King Class remain untouched.
    private static final java.util.Map<String,String> DEV54H15Y_SELECTORS=java.util.Map.of(
        "legacy_meshes/locomotive_steam_gwr_72xx.tcm", "GWR72XX",
        "legacy_meshes/locomotive_steam_hall_class.tcm", "HALL",
        "legacy_meshes/locomotive_steam_j50.tcm", "J50",
        "legacy_meshes/locomotive_steam_lssp7.tcm", "LSSP7");
    // Read opt-in marker files ONCE at startup, never inside a per-vertex render loop.
    private static final java.util.Map<String,Boolean> DEV54H15Y_ENABLED=java.util.Map.of(
        "GWR72XX",dev54h15tEnabled("DEV54H15Y-GWR72XX-HIDE-OLD-RODS-ON.txt","DEV54H15Y-GWR72XX-ON-V1"),
        "HALL",dev54h15tEnabled("DEV54H15Y-HALL-HIDE-OLD-RODS-ON.txt","DEV54H15Y-HALL-ON-V1"),
        "J50",dev54h15tEnabled("DEV54H15Y-J50-HIDE-OLD-RODS-ON.txt","DEV54H15Y-J50-ON-V1"),
        "LSSP7",dev54h15tEnabled("DEV54H15Y-LSSP7-HIDE-OLD-RODS-ON.txt","DEV54H15Y-LSSP7-ON-V1"));
    private static final java.util.Set<Integer> DEV54H15Y_J50_USER_EXTRA=java.util.Set.of(26,30,42,45,46);
    private static final java.util.Map<String,Integer> DEV54H15Y_EXPECTED_COUNTS=java.util.Map.of(
        "GWR72XX",12, "HALL",40, "J50",21, "LSSP7",2);
    private static final java.util.Set<String> DEV54H15Y_REPORTED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15yHideBatch4Source(String path,int chunk,boolean verified,
                                                      boolean sourcePickerSelected){
        // The original source mesh must have its expected SHA; unknown models are never touched.
        if(!verified || path==null)return false;
        String model=DEV54H15Y_SELECTORS.get(path);
        if(model==null || !Boolean.TRUE.equals(DEV54H15Y_ENABLED.get(model)))return false;
        // Allow the H12C J50 part browser to show its currently selected source chunk.
        // The part remains hidden again as soon as selection changes.
        if("J50".equals(model) && sourcePickerSelected && DEV54H15W_J50_PICK_ON)return false;
        var audited=DEV54H14_TAGS.get(path);
        if(audited==null)return false;
        boolean hide=audited.rod(chunk) || ("J50".equals(model) && DEV54H15Y_J50_USER_EXTRA.contains(chunk));
        if(hide && DEV54H15Y_REPORTED.add(model))
            LOGGER.info("[DEV54H15Y-ORIGINAL-RODS-HIDDEN] model={} sourceChunks={} " +
                        "newRods=UNCHANGED nativeWheels=UNCHANGED originalTCM=UNCHANGED",model,
                        DEV54H15Y_EXPECTED_COUNTS.get(model));
        return hide;
    }

    // DEV54H15AC: final Batch 4 ORIGINAL source-rod-only masks.
    // MIDLAND / MILW CLASS A / PANNIER only; all previous accepted locomotives unaffected.
    // No model, wheel, piston, saved design, or native animator mutations.
    private static final java.util.Map<String,String> DEV54H15AC_IDS=java.util.Map.of(
        "legacy_meshes/locomotive_steam_midland_compound.tcm", "MIDLAND",
        "legacy_meshes/locomotive_steam_milw_class_a.tcm", "MILW",
        "legacy_meshes/locomotive_steam_pannier.tcm", "PANNIER");
    private static final java.util.Map<String,Boolean> DEV54H15AC_ON=java.util.Map.of(
        "MIDLAND",dev54h15tEnabled("DEV54H15AC-MIDLAND-OLD-RODS-ON.txt","DEV54H15AC-MIDLAND-ON-V1"),
        "MILW",dev54h15tEnabled("DEV54H15AC-MILW-OLD-RODS-ON.txt","DEV54H15AC-MILW-ON-V1"),
        "PANNIER",dev54h15tEnabled("DEV54H15AC-PANNIER-OLD-RODS-ON.txt","DEV54H15AC-PANNIER-ON-V1"));
    private static final java.util.Map<String,Integer> DEV54H15AC_COUNTS=java.util.Map.of(
        "MIDLAND",20,"MILW",18,"PANNIER",4);
    private static final java.util.Set<String> DEV54H15AC_REPORTED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15acHideOldRod(String path,int chunk,boolean verified){
        if(!verified || path==null)return false;
        String id=DEV54H15AC_IDS.get(path);
        if(id==null || !Boolean.TRUE.equals(DEV54H15AC_ON.get(id)))return false;
        Dev54H14Tags t=DEV54H14_TAGS.get(path);
        boolean hide=t!=null && t.rod(chunk);
        if(hide && DEV54H15AC_REPORTED.add(id))
            LOGGER.info("[DEV54H15AC-SOURCE-RODS-HIDDEN] model={} sourceRodChunks={} oldPistons=UNCHANGED nativeWheels=UNCHANGED",id,DEV54H15AC_COUNTS.get(id));
        return hide;
    }

    // DEV54H15AE: EXACT additional user H12C picks from cumulative CSV exports
    // (8)/(9)/(10). J50 candidates intentionally excluded: user marked TWO as
    // stationary keepers; the single unwanted chunk was not identified yet.
    // Adds to H15AC source-rod masks; never changes a wheel, accepted rod,
    // piston transform, original TCM, or existing user picker selection.
    private static final java.util.Set<Integer> DEV54H15AE_MIDLAND = java.util.Set.of(37, 41, 42, 46, 120);
    private static final java.util.Set<Integer> DEV54H15AE_MILW = java.util.Set.of(105, 106, 107, 108, 158, 159, 160, 170, 171, 172, 176, 180, 182, 183, 184, 185, 186);
    private static final java.util.Set<Integer> DEV54H15AE_PANNIER = java.util.Set.of(327, 328, 330, 331, 332, 335);
    private static final java.util.Map<String,Boolean> DEV54H15AE_ON = java.util.Map.of(
        "MIDLAND", dev54h15tEnabled("DEV54H15AE-MIDLAND-USER-HIDES-ON.txt","DEV54H15AE-MIDLAND-ON-V1"),
        "MILW", dev54h15tEnabled("DEV54H15AE-MILW-USER-HIDES-ON.txt","DEV54H15AE-MILW-ON-V1"),
        "PANNIER", dev54h15tEnabled("DEV54H15AE-PANNIER-USER-HIDES-ON.txt","DEV54H15AE-PANNIER-ON-V1"));
    private static final java.util.Set<String> DEV54H15AE_REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15aeHideUserExtra(String path,int chunk,boolean verified){
        if(!verified || path==null)return false;
        String id=DEV54H15AC_IDS.get(path);
        if(id==null || !Boolean.TRUE.equals(DEV54H15AE_ON.get(id)))return false;
        boolean hide=switch(id){
            case "MIDLAND" -> DEV54H15AE_MIDLAND.contains(chunk);
            case "MILW" -> DEV54H15AE_MILW.contains(chunk);
            case "PANNIER" -> DEV54H15AE_PANNIER.contains(chunk);
            default -> false;
        };
        if(hide && DEV54H15AE_REPORTED.add(id))
            LOGGER.info("[DEV54H15AE-USER-HIDES] model={} additionalChunks={} source=EXACT_H12C_PICK originalTCM=UNCHANGED nativeWheels=UNCHANGED",
                id, id.equals("MIDLAND")?5:id.equals("MILW")?17:6);
        return hide;
    }

    // DEV54H15AG: user-identified J50 unwanted block #38 ONLY.
    // Red chunks 612 and 614 are deliberately LEFT VISIBLE and STATIONARY.
    // Preserve H15Y's existing source picker visibility for review.
    private static final boolean DEV54H15AG_J50_ON =
            dev54h15tEnabled("DEV54H15AG-J50-CHUNK38-ON.txt", "DEV54H15AG-J50-38-ON-V1");
    private static final java.util.Set<String> DEV54H15AG_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15agHideJ50Block(String path,int chunk,
                                                  boolean verified,boolean picked){
        if(!DEV54H15AG_J50_ON || !verified || path == null ||
           !"legacy_meshes/locomotive_steam_j50.tcm".equals(path) || chunk != 38)
            return false;
        if(picked && DEV54H15W_J50_PICK_ON)return false;
        if(DEV54H15AG_REPORTED.add("J50"))
            LOGGER.info("[DEV54H15AG-J50-HIDDEN] block=38 only; red parts 612,614 unchanged; TCM unchanged");
        return true;
    }

    // DEV54H15X: finish D51 LONG's ORIGINAL imported linkage cleanup.
    // H15U retains the original 33 user-picked chunks. These 11 source
    // chunks were missed by that H12C export: small crank fittings (59,60,
    // 63,77,88,90,92,94) and connecting-bar pieces (62,89,91).
    // Never mask wheel faces (75,79,80,81,82), model body, new H15H rods,
    // saved piston editor, or standard D51.
    private static final String DEV54H15X_LONG_MESH =
        "legacy_meshes/locomotive_steam_d51_long_body.tcm";
    private static final boolean DEV54H15X_ON = dev54h15tEnabled(
        "DEV54H15X-D51LONG-REMAINDER-HIDES-ON.txt", "DEV54H15X-ON-V1");
    private static final java.util.Set<Integer> DEV54H15X_EXTRA = java.util.Set.of(
        59, 60, 62, 63, 77, 88, 89, 90, 91, 92, 94);
    private static final java.util.Set<String> DEV54H15X_LOGGED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15xHideRemainder(String path, int chunk) {
        if (!DEV54H15X_ON || !DEV54H15U_ON ||
            !DEV54H15X_LONG_MESH.equals(path) ||
            !DEV54H15X_EXTRA.contains(chunk)) return false;
        // Fail closed if the original 33-chunk source mask is not verified.
        if (dev54h15uLoadLongMask().size() != 33) return false;
        if (DEV54H15X_LOGGED.add(path))
            LOGGER.info("[DEV54H15X-D51LONG-RODS-HIDDEN] existing=33 additional=11 total=44 " +
                "wheelFaces=UNTOUCHED newRods=UNTOUCHED sourceTCM=UNTOUCHED");
        return true;
    }

    // DEV54H15I: Re-edit a SAVED, verified locomotive design. This intentionally
    // reuses H15H data, wheel pivots, mesh-masking, and preview; no regenerated
    // guessed geometry or changes to accepted locomotive wheel animations.
    private static final boolean DEV54H15I_ON=dev54h15iMarker();
    private static boolean dev54h15iMarker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                   .resolve("DEV54H15I-REEDIT-SAVED-DESIGN-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                  .replace("\uFEFF","").trim().equals("DEV54H15I-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static boolean dev54h15iLastLoad=false;
    private static boolean dev54h15iReopened=false;
    private static String dev54h15iReopenedPath=null;
    private static void dev54h15iReset(){
        dev54h15iLastLoad=false;dev54h15iReopened=false;dev54h15iReopenedPath=null;
    }
    private static void dev54h15iKeys(long window){
        if(!DEV54H15I_ON||!DEV54H15H_ON||!DEV54H15F_ON||!dev54h15Editing)return;
        boolean load=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_L);
        if(load&&!dev54h15iLastLoad){
            if(dev54h15Stage!=0 || dev54h15Draft!=null){
                dev54h12cMessage("H15I: draft active; Backspace DISCARD first, then L load saved");
                LOGGER.warn("[DEV54H15I-HOLD] reason=existingDraft stage={} savedUnchanged=true",
                    dev54h15Stage);
            }else{
                dev54h15Load();dev54h15hLoad();
                if(dev54h15fChosen==null && dev54h15fCandidate!=null)
                    dev54h15fEnsure();
                LegacyStaticMesh mesh=dev54h15fChosen;
                if(mesh==null){
                    dev54h12cMessage("H15I: no locomotive nearby; stand beside saved locomotive");
                    LOGGER.warn("[DEV54H15I-HOLD] reason=noSelectedLocomotive fileUnchanged=true");
                }else{
                    String path=mesh.source.getPath();
                    Dev54H15Rod saved=DEV54H15_SAVED.get(path);
                    if(saved==null){
                        dev54h12cMessage("H15I: no saved design for "+path+"; use G,G first");
                        LOGGER.warn("[DEV54H15I-HOLD] reason=noSavedRig model={} fileUnchanged=true",path);
                    }else if(!dev54h15Good(saved)||!dev54h15Eligible(path)){
                        dev54h12cMessage("H15I: saved design fails validation; files untouched");
                        LOGGER.warn("[DEV54H15I-HOLD] reason=invalidOrFrozen model={}",path);
                    }else{
                        var pivots=mesh.dev54h15eVerifiedPivots();
                        if(pivots.isEmpty()){
                            dev54h12cMessage("H15I: verified wheel data unavailable; saved unchanged");
                            LOGGER.warn("[DEV54H15I-HOLD] reason=noVerifiedWheels model={}",path);
                        }else{
                            // Pick the nearest marker to the saved axle; do NOT
                            // replace its coordinates with a rounded or guessed pivot.
                            int best=0;double dist=Double.POSITIVE_INFINITY;
                            for(int i=0;i<pivots.size();i++){
                                var p=pivots.get(i);
                                double dx=p.x()-saved.ax(),dy=p.y()-saved.ay(),
                                       dz=p.z()-saved.az();
                                double d=dx*dx+dy*dy+dz*dz;
                                if(d<dist){dist=d;best=i;}
                            }
                            dev54h15fIndex=best;
                            dev54h15SavedWheel=saved;
                            dev54h15Draft=saved;
                            dev54h15Stage=2;
                            dev54h15Preview=true;
                            dev54h15hDraft=DEV54H15H_SAVED.getOrDefault(path,
                                            dev54h15hDefault(saved));
                            dev54h15iReopened=true;
                            dev54h15iReopenedPath=path;
                            dev54h12cMessage("H15I LOADED: "+path+" depth="+
                                dev54h15hDraft.inset()+
                                " | , IN / . OUT / arrows piston / Enter SAVE / Backspace cancel");
                            LOGGER.info("[DEV54H15I-LOAD-PASS] model={} wheelIndex={} "
                                +"savedAxle=({},{},{}) savedPin=({},{},{}) "
                                +"savedPiston=({},{},{}) depth={} coupling={} pins={} "
                                +"hideMesh={} hideGenerated={} noFileWrite=true",
                                path,best,saved.ax(),saved.ay(),saved.az(),
                                saved.wx(),saved.wy(),saved.wz(),
                                saved.px(),saved.py(),saved.pz(),
                                dev54h15hDraft.inset(),dev54h15hDraft.coupling(),
                                dev54h15hDraft.crankpins(),dev54h15hDraft.hideMesh(),
                                dev54h15hDraft.hideGenerated());
                        }
                    }
                }
            }
        }
        dev54h15iLastLoad=load;
    }
    // Saved alignment files are never changed by install/enable/disable/undo.
    // Before an explicit Enter in RE-EDIT mode, snapshot BOTH existing TSVs.
    // If copying fails, save is held, leaving the source files unchanged.
    private static void dev54h15iBackupForSave() throws java.io.IOException{
        if(!DEV54H15I_ON || !dev54h15iReopened)return;
        if(dev54h15Draft==null || !dev54h15Draft.path().equals(dev54h15iReopenedPath))
            throw new java.io.IOException("Re-edit locomotive mismatch");
        if(!java.nio.file.Files.isRegularFile(DEV54H15_FILE) ||
           !java.nio.file.Files.isRegularFile(DEV54H15H_FILE))
            throw new java.io.IOException("Cannot back up both existing saved designs");
        var backup=DEV54H15_FILE.getParent().resolve("dev54h15i-saved-backups");
        java.nio.file.Files.createDirectories(backup);
        String stamp=java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .format(java.time.LocalDateTime.now());
        var main=backup.resolve(stamp+"-rod-builder.tsv");
        var full=backup.resolve(stamp+"-full-running-gear.tsv");
        java.nio.file.Files.copy(DEV54H15_FILE,main);
        try{
            java.nio.file.Files.copy(DEV54H15H_FILE,full);
        }catch(java.io.IOException ex){
            java.nio.file.Files.deleteIfExists(main);
            throw ex;
        }
        LOGGER.info("[DEV54H15I-BACKUP] model={} saved={} sidecar={} "
            +"beforeExplicitSave=true",dev54h15iReopenedPath,main,full);
    }
    // DEV54H15B: manual three-point calibration, independent of whether the
    // wheel is represented in BakedWheelAnimationContext. A clicked axle
    // center, crankpin, and piston anchor are all recorded in source-local
    // coordinates on the SAME locomotive instance/mesh. This is editor-only;
    // it never changes the frozen wheel/rod implementation or source TCM.
    private static final boolean DEV54H15B_ON=dev54h15bMarker();
    private static boolean dev54h15bMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                   .resolve("DEV54H15B-MANUAL-THREE-POINT-PICKER-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                   && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                      .replace("\uFEFF","").trim().equals("DEV54H15B-ON-V1");
        }catch(Exception ignored){return false;}
    }
    private static LegacyStaticMesh dev54h15bFallbackMesh;
    private static double dev54h15bFallbackDistance=Double.POSITIVE_INFINITY;
    private static float dev54h15bFallbackX,dev54h15bFallbackY,dev54h15bFallbackZ;
    private static LegacyStaticMesh dev54h15bAxleMesh;
    private static float dev54h15bAxleX,dev54h15bAxleY,dev54h15bAxleZ;
    private static int dev54h15bConsidered=0,dev54h15bPlaneHits=0;
    private void dev54h15bProbe(PoseStack.Pose pose,Vector3f direction){
        if(!DEV54H15B_ON || !DEV54H15_ON || !dev54h15Editing ||
           !dev54h15Eligible(this.source.getPath()) || dev54h15Stage>=2)return;
        if(dev54h15bAxleMesh!=null && dev54h15bAxleMesh!=this)return;
        if(dev54h15Stage==1 && (dev54h15SavedWheel==null ||
               !this.source.getPath().equals(dev54h15SavedWheel.path())))return;
        dev54h15bConsidered++;
        var inverse=new org.joml.Matrix4f(pose.pose()).invert();
        var origin=inverse.transformPosition(new Vector3f());
        var dir=inverse.transformDirection(new Vector3f(direction)).normalize();
        if(!Float.isFinite(dir.z())||Math.abs(dir.z())<.018f)return;
        // Estimate a visible running-gear side plane from this EXACT mesh's
        // source envelope, not from a wheel animation qualification list.
        float side=origin.z()>=0f?1f:-1f;
        float extent=side>0f?this.maxZ:-this.minZ;
        if(!Float.isFinite(extent)||extent<.16f)return;
        float planeZ=side*Math.max(.22f,Math.min(.72f,extent*.77f));
        double t=(planeZ-origin.z())/dir.z();
        if(!Double.isFinite(t)||t<.02d||t>12d||t>=dev54h15bFallbackDistance)return;
        float x=origin.x()+(float)t*dir.x();
        float y=origin.y()+(float)t*dir.y();
        if(!Float.isFinite(x)||!Float.isFinite(y))return;
        if(x<this.minX-.16f||x>this.maxX+.16f ||
           y<this.minY-.18f||y>this.maxY+.18f)return;
        // On the first click, avoid accidentally treating cab/boiler as
        // an axle. Later stages use the stored first point's exact mesh.
        if(dev54h15Stage==0 && dev54h15bAxleMesh==null &&
           y>this.minY+Math.max(.80f,(this.maxY-this.minY)*.67f))return;
        if(dev54h15bAxleMesh!=null && dev54h15Stage==0 &&
           Math.hypot(x-dev54h15bAxleX,y-dev54h15bAxleY)>.62d)return;
        dev54h15bPlaneHits++;
        dev54h15bFallbackMesh=this;
        dev54h15bFallbackDistance=t;
        dev54h15bFallbackX=x;
        dev54h15bFallbackY=y;
        dev54h15bFallbackZ=dev54h15bAxleMesh!=null ? dev54h15bAxleZ:planeZ;
    }
    private static void dev54h15bClick(){
        if(dev54h15HitSource==null)return;
        LegacyStaticMesh mesh=dev54h15HitSource;
        String path=mesh.source.getPath();
        if(!dev54h15Eligible(path)){
            dev54h12cMessage("H15B protected locomotive; no changes");return;
        }
        float x=dev54h15HitX,y=dev54h15HitY,z=dev54h15HitZ;
        if(dev54h15Stage==0 && dev54h15bAxleMesh==null){
            dev54h15bAxleMesh=mesh;
            dev54h15bAxleX=x;dev54h15bAxleY=y;dev54h15bAxleZ=z;
            dev54h12cMessage(DEV54H15C_ON?
                "H15C AXLE marked; aim at off-center CRANKPIN and press G":
                "H15B AXLE selected: aim at the SAME wheel CRANKPIN; press P");
            LOGGER.info("[DEV54H15B-AXLE] mesh={} x={} y={} z={}",path,x,y,z);
            return;
        }
        if(dev54h15Stage==0){
            if(dev54h15bAxleMesh!=mesh){
                dev54h12cMessage("H15B use same locomotive wheel for crankpin");return;
            }
            float radius=(float)Math.hypot(x-dev54h15bAxleX,y-dev54h15bAxleY);
            if(radius<.035f || radius>.62f || (DEV54H15C_ON && radius>.20f)){
                dev54h12cMessage(DEV54H15C_ON?
                    "H15C crank radius too large / small; G again nearer wheel center (max 0.20)":
                    "H15B pin must be OFF-CENTER on the selected wheel; retry P");return;
            }
            dev54h15SavedWheel=new Dev54H15Rod(path,dev54h15bAxleX,dev54h15bAxleY,
                dev54h15bAxleZ,x,y,dev54h15bAxleZ,0f,0f,dev54h15bAxleZ,.078f,false,false);
            dev54h15Stage=1;
            dev54h12cMessage(DEV54H15C_ON?
                "H15C PIN marked; aim at PISTON / CROSSHEAD and press G":
                "H15B CRANKPIN selected: aim at PISTON / CROSSHEAD; press P");
            LOGGER.info("[DEV54H15B-PIN] mesh={} radius={} x={} y={}",path,radius,x,y);
            return;
        }
        if(dev54h15Stage==1){
            var a=dev54h15SavedWheel;
            if(a==null || a.path().equals(path)==false || dev54h15bAxleMesh!=mesh){
                dev54h12cMessage("H15B piston must be on SAME locomotive body mesh");return;
            }
            var draft=new Dev54H15Rod(path,a.ax(),a.ay(),a.az(),
                            a.wx(),a.wy(),a.wz(),x,y,a.wz(),.078f,false,false);
            if(!dev54h15Good(draft)){
                dev54h12cMessage("H15B piston must be away from wheel; retry P");return;
            }
            dev54h15Draft=draft;dev54h15Stage=2;dev54h15Preview=true;
            dev54h12cMessage(DEV54H15C_ON?
                "H15C PREVIEW ready: Z motion, arrows adjust, ENTER SAVE (wait for H15 SAVED)":
                "H15B PREVIEW: Z animate, arrows adjust, [ ] width, X phase, T hide old, ENTER save");
            LOGGER.info("[DEV54H15B-PISTON] mesh={} x={} y={} z={}",path,x,y,a.wz());
            dev54h15bAxleMesh=null;
        }
    }
    // DEV54H15A: optional side-plane picker for animated/generated rod visual
    // surfaces. The original triangle picker cannot see geometry generated
    // after the source .tcm vertex loop. This fallback only snaps to actual
    // source-qualified rotating wheel centers, or the selected model's piston
    // side plane. It cannot select accepted/frozen locomotives.
    private static final boolean DEV54H15A_ON=dev54h15aMarker();
    private static boolean dev54h15aMarker(){
        try {
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                   .resolve("DEV54H15A-VISUAL-PICKER-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                   java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                      .replace("\uFEFF","").trim().equals("DEV54H15A-ON-V1");
        }catch(Exception ignored){return false;}
    }
    private static LegacyStaticMesh dev54h15aFallbackMesh;
    private static float dev54h15aFallbackX,dev54h15aFallbackY,dev54h15aFallbackZ;
    private static double dev54h15aFallbackDistance=Double.POSITIVE_INFINITY;
    // This method runs only during a P-click render frame and never changes
    // the geometry, animation, player position, or saved rod configurations.
    private void dev54h15aProbe(PoseStack.Pose pose,Vector3f direction){
        if(!DEV54H15A_ON || !DEV54H15_ON || !dev54h15Editing ||
           !dev54h15Eligible(this.source.getPath()))return;
        if(dev54h15Stage==1 && (dev54h15SavedWheel==null ||
           !this.source.getPath().equals(dev54h15SavedWheel.path())))return;
        if(dev54h15Stage>1)return;
        var inverse=new org.joml.Matrix4f(pose.pose()).invert();
        var origin=inverse.transformPosition(new Vector3f(0f,0f,0f));
        var ray=inverse.transformDirection(new Vector3f(direction)).normalize();
        if(!Float.isFinite(ray.z())||Math.abs(ray.z())<.025f)return;
        if(dev54h15Stage==0){
            String key="legacy:"+this.source;
            for(int chunk=0;chunk<(this.vertexCount+23)/24;chunk++){
                var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext
                              .spec(key,chunk,this.vertices,FLOATS_PER_VERTEX);
                if(wheel==null || !wheel.rotates() || wheel.axis()!=2)continue;
                float z=wheel.centerZ();
                if(!Float.isFinite(z) || !Float.isFinite(wheel.centerX()) ||
                   !Float.isFinite(wheel.centerY()))continue;
                // Wheel face just outward of its source model's verified axle.
                float faceZ=z+(z>=0f?.055f:-.055f);
                double t=(faceZ-origin.z())/ray.z();
                if(t<.015d||t>12d||t>=dev54h15aFallbackDistance)continue;
                float x=origin.x()+(float)t*ray.x(),y=origin.y()+(float)t*ray.y();
                float dx=x-wheel.centerX(),dy=y-wheel.centerY();
                float r2=dx*dx+dy*dy;
                // Accept clicks over the wheel/crankpin region; do not
                // mistakenly claim the boiler, cab or adjacent train.
                if(r2<.0025f||r2>.55f*.55f)continue;
                if(x<this.minX-.3f||x>this.maxX+.3f||
                   y<this.minY-.3f||y>this.maxY+.3f)continue;
                dev54h15aFallbackMesh=this;
                dev54h15aFallbackDistance=t;
                dev54h15aFallbackX=x;dev54h15aFallbackY=y;dev54h15aFallbackZ=faceZ;
            }
        }else{
            // Piston can be obscured by the new rods, so project the crosshair
            // onto the previously selected crankpin's side plane instead of
            // requiring the source shell to expose a triangle.
            var a=dev54h15SavedWheel;
            float z=a.wz();
            double t=(z-origin.z())/ray.z();
            if(t<.015d||t>12d||t>=dev54h15aFallbackDistance)return;
            float x=origin.x()+(float)t*ray.x(),y=origin.y()+(float)t*ray.y();
            if(x<this.minX-.85f||x>this.maxX+.85f||
               y<this.minY-.65f||y>this.maxY+.65f ||
               Math.abs(x-a.ax())>5f || Math.abs(y-a.ay())>1.8f ||
               Math.abs(x-a.wx())<.10f)return;
            dev54h15aFallbackMesh=this;
            dev54h15aFallbackDistance=t;
            dev54h15aFallbackX=x;dev54h15aFallbackY=y;dev54h15aFallbackZ=z;
        }
    }
    private static LegacyStaticMesh dev54h15HitSource=null;
    private static float dev54h15HitX,dev54h15HitY,dev54h15HitZ;
    private static boolean dev54h15Marker(){
        try{
            var f=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H15-ROD-BUILDER-ON.txt");
            return java.nio.file.Files.isRegularFile(f) &&
                java.nio.file.Files.readString(f,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H15-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static boolean dev54h15Eligible(String path){
        if(!path.startsWith("legacy_meshes/locomotive_") || !path.endsWith(".tcm"))return false;
        String name=path.substring("legacy_meshes/".length(),path.length()-4);
        return !DEV54H15_FROZEN.contains(name) &&
               !(DEV54H15J_ON && DEV54H15J_FROZEN.contains(path));
    }
    private static boolean dev54h15Good(Dev54H15Rod a){
        if(!dev54h15jRenderable(a.path()) || !Float.isFinite(a.width()) ||
           a.width()<.025f || a.width()>.25f)return false;
        for(float n:new float[]{a.ax(),a.ay(),a.az(),a.wx(),a.wy(),a.wz(),
                                a.px(),a.py(),a.pz()})if(!Float.isFinite(n)||Math.abs(n)>20f)return false;
        return Math.abs(a.wz())>.08f &&
          Math.abs(a.pz()-a.wz())<1.25f &&
          Math.hypot(a.wx()-a.ax(),a.wy()-a.ay())>.04d &&
          Math.hypot(a.wx()-a.px(),a.wy()-a.py())>.13d;
    }
    private static boolean dev54h15FullCycle(Dev54H15Rod a){
        float dx=a.wx()-a.ax(),dy=a.wy()-a.ay();
        float length=(float)Math.hypot(a.px()-a.wx(),a.py()-a.wy());
        if(length>.0f && length<=3.7f){
            for(int side=0;side<2;side++){
                for(int i=0;i<256;i++){
                    float angle=(float)(2d*Math.PI*i/256d)+
                               (side==1&&a.mirrorOpposite()?(float)Math.PI:0f);
                    float pinX=a.ax()+dx*(float)Math.cos(angle)-dy*(float)Math.sin(angle);
                    float pinY=a.ay()+dx*(float)Math.sin(angle)+dy*(float)Math.cos(angle);
                    float dir=a.px()>=a.ax()?1f:-1f;
                    if(dev54h13bHorizontalCrosshead(pinX,pinY,
                         a.px(),a.py(),length,dir,.55f)==null)return false;
                }
            }
            return true;
        }
        return false;
    }
    private static String dev54h15Line(Dev54H15Rod a){
        return a.path()+"\t"+a.ax()+"\t"+a.ay()+"\t"+a.az()+"\t"
            +a.wx()+"\t"+a.wy()+"\t"+a.wz()+"\t"
            +a.px()+"\t"+a.py()+"\t"+a.pz()+"\t"
            +a.width()+"\t"+a.replace()+"\t"+a.mirrorOpposite();
    }
    private static void dev54h15Load(){
        if(dev54h15Loaded || !DEV54H15_ON)return;
        dev54h15Loaded=true;
        if(!java.nio.file.Files.isRegularFile(DEV54H15_FILE))return;
        try{
            var lines=java.nio.file.Files.readAllLines(DEV54H15_FILE,
                                      java.nio.charset.StandardCharsets.UTF_8);
            if(lines.isEmpty() || !lines.get(0).equals(DEV54H15_HEADER))
                throw new IllegalArgumentException("Wrong editor file version");
            for(int i=1;i<lines.size();i++){
                String line=lines.get(i).trim();if(line.isEmpty())continue;
                String[] t=line.split("\\t",-1);
                if(t.length!=13 || DEV54H15_SAVED.size()>=64)
                    throw new IllegalArgumentException("Invalid row "+i);
                Dev54H15Rod a=new Dev54H15Rod(t[0],Float.parseFloat(t[1]),
                    Float.parseFloat(t[2]),Float.parseFloat(t[3]),Float.parseFloat(t[4]),
                    Float.parseFloat(t[5]),Float.parseFloat(t[6]),Float.parseFloat(t[7]),
                    Float.parseFloat(t[8]),Float.parseFloat(t[9]),Float.parseFloat(t[10]),
                    Boolean.parseBoolean(t[11]),Boolean.parseBoolean(t[12]));
                if(!dev54h15Good(a))throw new IllegalArgumentException("Unsafe row "+i);
                if(!dev54h15jApprovedRow(a.path(),lines.get(i),false))continue;
                DEV54H15_SAVED.put(a.path(),a);
            }
            LOGGER.info("[DEV54H15-LOAD] savedRigs={} path={}",DEV54H15_SAVED.size(),DEV54H15_FILE);
        }catch(Exception ex){
            DEV54H15_SAVED.clear();
            LOGGER.error("[DEV54H15-HOLD] No saved rigs loaded; invalid editor file",ex);
        }
    }
    private static void dev54h15Save(){
        if(!DEV54H15_ON || dev54h15Draft==null || dev54h15Stage!=2 ||
             !dev54h15Eligible(dev54h15Draft.path()) || !dev54h15Good(dev54h15Draft)){
            dev54h12cMessage("H15: select WHEEL and PISTON first (P). No file changed");return;
        }
        if(!dev54h15jFreezeIntact()){
            dev54h12cMessage("H15J HOLD: accepted Batch 2 file changed; no save. Check debug");
            return;
        }
        if(!dev54h15FullCycle(dev54h15Draft)){
            dev54h12cMessage("H15 HOLD: crosshead can't connect for full wheel turn. Adjust with arrows");
            return;
        }
        try{
            if(DEV54H15I_ON)dev54h15iBackupForSave();
            java.nio.file.Files.createDirectories(DEV54H15_FILE.getParent());
            var content=new StringBuilder(DEV54H15_HEADER+"\n");
            var copy=new java.util.TreeMap<String,Dev54H15Rod>(DEV54H15_SAVED);
            copy.put(dev54h15Draft.path(),dev54h15Draft);
            for(var a:copy.values())content.append(dev54h15Line(a)).append('\n');
            // Atomic replace: crash cannot leave a half-written geometry file.
            var tmp=DEV54H15_FILE.resolveSibling(DEV54H15_FILE.getFileName()+".tmp");
            java.nio.file.Files.writeString(tmp,content.toString(),
                      java.nio.charset.StandardCharsets.UTF_8);
            try{java.nio.file.Files.move(tmp,DEV54H15_FILE,
                      java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }catch(java.nio.file.AtomicMoveNotSupportedException ex){
                java.nio.file.Files.move(tmp,DEV54H15_FILE,
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if(DEV54H15H_ON)dev54h15hSaveDesign(dev54h15Draft);
            DEV54H15_SAVED.put(dev54h15Draft.path(),dev54h15Draft);
            LOGGER.info("[DEV54H15-SAVED] mesh={} width={} hideOld={} mirrored={} file={}",
                 dev54h15Draft.path(),dev54h15Draft.width(),dev54h15Draft.replace(),
                 dev54h15Draft.mirrorOpposite(),DEV54H15_FILE);
            dev54h12cMessage(DEV54H15H_ON?
                "H15H SAVED complete running gear: "+dev54h15Draft.path():
                "H15 SAVED: "+dev54h15Draft.path()+" (no rebuild; preview is live)");
            if(DEV54H15I_ON && dev54h15iReopened){
                LOGGER.info("[DEV54H15I-SAVE-PASS] model={} reedited=true previousBackedUp=true "
                    +"inset={} originalGeometryPreserved=true",
                    dev54h15iReopenedPath,dev54h15hDraft==null?
                    dev54h15hInset(dev54h15iReopenedPath):dev54h15hDraft.inset());
                dev54h15iReopened=false;
                dev54h15iReopenedPath=null;
            }
            dev54h15Editing=false;dev54h15Preview=false;
        }catch(Exception ex){LOGGER.error("[DEV54H15-SAVE-HOLD]",ex);
            dev54h12cMessage("H15 SAVE FAILED: see latest.log");}
    }
    private static void dev54h15Keys(long window){
        if(!DEV54H15_ON)return;
        // DEV54H15P native adapter uses the EXACT H15 editor hotkey entry point.
        // It returns false for every legacy TCM locomotive: byte-identical path below.
        if(traincraft.client.renderer.animation.Dev54H15PNativeFleetEditor.tick(window)){
            dev54h15Editing=false;dev54h15Preview=false;dev54h15Draft=null;
            return;
        }
        dev54h15Load();
        dev54h15Ticks++;
        boolean ctrl=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_CONTROL) ||
                     InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT) ||
                     InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean toggle=ctrl&&shift&&InputConstants.isKeyDown(window,GLFW.GLFW_KEY_R);
        if(toggle&&!dev54h15LastToggle){
            dev54h15Editing=!dev54h15Editing;
            dev54h15Preview=false;dev54h15Draft=null;dev54h15Stage=0;
            if(DEV54H15H_ON)dev54h15hResetDraft();
            if(DEV54H15I_ON)dev54h15iReset();
            if(DEV54H15B_ON){dev54h15bAxleMesh=null;dev54h15SavedWheel=null;}
            dev54h15cHoverMesh=null;
            dev54h15eAutomatic=true;
            if(DEV54H15F_ON)dev54h15fReset();
            if(DEV54H15G_ON)dev54h15gLastV=false;
            if(DEV54H15K_ON){dev54h15kLastN=false;dev54h15kLastM=false;}
            dev54h12cMessage(dev54h15Editing && DEV54H15H_ON?
                (DEV54H15I_ON?
                "H15I EDIT: L LOAD saved design (no re-pick); ,/. depth, Enter save; G,G new design":
                "H15H EDIT: PgUp/PgDn axle, G pin, G piston; O original I generated K coupling C pins ,/. depth"):
                dev54h15Editing && DEV54H15F_ON?
                "H15F EDIT: PgUp/PgDn choose WHEEL, G PIN, G PISTON; SHIFT+arrows pin; arrows piston":
                dev54h15Editing && DEV54H15E_ON?
                "H15E EDIT: G CRANKPIN (auto axle), G PISTON; V manual; ALT+arrows center":
                (dev54h15Editing && DEV54H15D_ON?
                "H15D EDIT ON: G wheel CENTER, G crankpin, G piston (near-camera-safe)":
                (dev54h15Editing && DEV54H15C_ON?
                "H15C EDIT ON: G wheel CENTER, G crankpin, G piston (watch cross marker)":
                (dev54h15Editing && DEV54H15B_ON?
                "H15B EDIT ON: P wheel CENTER, P crankpin, P piston":
                (dev54h15Editing?"H15 EDIT ON: aim at wheel crankpin, P":"H15 editor OFF")))));
        }
        dev54h15LastToggle=toggle;
        if(!dev54h15Editing)return;
        if(DEV54H15F_ON)dev54h15fKeys(window);
        if(DEV54H15K_ON)dev54h15kKeys(window);
        if(DEV54H15G_ON && DEV54H15F_ON)dev54h15gFlipPiston(window);
        if(DEV54H15I_ON)dev54h15iKeys(window);
        if(DEV54H15H_ON)dev54h15hKeys(window);
        boolean v=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_V);
        if(DEV54H15E_ON && !DEV54H15F_ON && v && !dev54h15eLastV){
            dev54h15eAutomatic=!dev54h15eAutomatic;
            dev54h15Draft=null;dev54h15SavedWheel=null;dev54h15Stage=0;
            dev54h15bAxleMesh=null;dev54h15Preview=false;
            dev54h12cMessage(dev54h15eAutomatic?
                "H15E AUTO: G crankpin (axle snaps), G piston":
                "H15E MANUAL: G wheel CENTER, G crankpin, G piston");
        }
        dev54h15eLastV=v;
        boolean save=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_ENTER);
        boolean cancel=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_BACKSPACE);
        boolean preview=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_Z);
        boolean replace=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_T);
        boolean mirror=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_X);
        boolean left=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT);
        boolean right=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT);
        boolean up=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_UP);
        boolean down=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_DOWN);
        boolean narrow=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_BRACKET);
        boolean wide=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_BRACKET);
        boolean alt=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_ALT)||
                    InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_ALT);
        if(DEV54H15E_ON && alt && dev54h15Stage>=1 && dev54h15SavedWheel!=null){
            float dx=(right&&!dev54h15LastRight?.015625f:0f)-
                     (left&&!dev54h15LastLeft?.015625f:0f);
            float dy=(up&&!dev54h15LastUp?.015625f:0f)-
                     (down&&!dev54h15LastDown?.015625f:0f);
            if(dx!=0f||dy!=0f){
                var a=dev54h15SavedWheel;
                float ax=a.ax()+dx,ay=a.ay()+dy;
                float r=(float)Math.hypot(a.wx()-ax,a.wy()-ay);
                if(r>=.035f&&r<=.205f){
                    dev54h15SavedWheel=new Dev54H15Rod(a.path(),ax,ay,a.az(),
                                a.wx(),a.wy(),a.wz(),a.px(),a.py(),a.pz(),
                                a.width(),a.replace(),a.mirrorOpposite());
                    dev54h15bAxleX=ax;dev54h15bAxleY=ay;
                    if(dev54h15Draft!=null){var draft=dev54h15Draft;
                        dev54h15Draft=new Dev54H15Rod(draft.path(),ax,ay,draft.az(),
                          draft.wx(),draft.wy(),draft.wz(),draft.px(),draft.py(),draft.pz(),
                          draft.width(),draft.replace(),draft.mirrorOpposite());}
                    dev54h12cMessage("H15E axle fine-tuned X="+ax+" Y="+ay);
                }else dev54h12cMessage("H15E axle adjust HOLD: crank radius out of range");
            }
        }
        if(DEV54H15F_ON && shift && !alt)
            dev54h15fAdjustPin(left,right,up,down);
        if(save&&!dev54h15LastSave)dev54h15Save();
        if(cancel&&!dev54h15LastCancel){
            dev54h15Draft=null;dev54h15Stage=0;dev54h15Preview=false;
            if(DEV54H15H_ON)dev54h15hResetDraft();
            if(DEV54H15I_ON)dev54h15iReset();
            if(DEV54H15B_ON){dev54h15bAxleMesh=null;dev54h15SavedWheel=null;}
            if(DEV54H15F_ON)dev54h15fReset();
            dev54h12cMessage("H15 draft cleared; saved rigs unchanged");
        }
        if(preview&&!dev54h15LastPreview){dev54h15Preview=!dev54h15Preview;
            dev54h12cMessage("H15 animation preview "+(dev54h15Preview?"ON":"OFF"));}
        if(dev54h15Draft!=null && dev54h15Stage==2){
            Dev54H15Rod a=dev54h15Draft;
            float dx=(!alt&&(!DEV54H15F_ON||!shift)&&right&&!dev54h15LastRight?.03125f:0f)-
                     (!alt&&(!DEV54H15F_ON||!shift)&&left&&!dev54h15LastLeft?.03125f:0f);
            float dy=(!alt&&(!DEV54H15F_ON||!shift)&&up&&!dev54h15LastUp?.03125f:0f)-
                     (!alt&&(!DEV54H15F_ON||!shift)&&down&&!dev54h15LastDown?.03125f:0f);
            float dw=(wide&&!dev54h15LastWide?.0078125f:0f)-
                     (narrow&&!dev54h15LastNarrow?.0078125f:0f);
            boolean rep=DEV54H15H_ON?a.replace():
                (replace&&!dev54h15LastReplace?!a.replace():a.replace());
            boolean mir=mirror&&!dev54h15LastMirror?!a.mirrorOpposite():a.mirrorOpposite();
            if(dx!=0f||dy!=0f||dw!=0f||rep!=a.replace()||mir!=a.mirrorOpposite()){
                dev54h15Draft=new Dev54H15Rod(a.path(),a.ax(),a.ay(),a.az(),
                      a.wx(),a.wy(),a.wz(),a.px()+dx,a.py()+dy,a.pz(),
                      Math.max(.025f,Math.min(.25f,a.width()+dw)),rep,mir);
                dev54h12cMessage("H15 crosshead x="+dev54h15Draft.px()+" y="+
                   dev54h15Draft.py()+" width="+dev54h15Draft.width()+
                   " hideOld="+rep+" oppositePhase="+mir);
            }
        }
        dev54h15LastSave=save;dev54h15LastCancel=cancel;
        dev54h15LastPreview=preview;dev54h15LastReplace=replace;
        dev54h15LastMirror=mirror;dev54h15LastLeft=left;dev54h15LastRight=right;
        dev54h15LastUp=up;dev54h15LastDown=down;
        dev54h15LastNarrow=narrow;dev54h15LastWide=wide;
    }
    private static void dev54h15Click(){
        if(!DEV54H15_ON || !dev54h15Editing || dev54h15HitSource==null)return;
        LegacyStaticMesh m=dev54h15HitSource;
        String path=m.source.getPath();
        if(!dev54h15Eligible(path)){
            dev54h12cMessage("H15 PROTECTED / NOT ELIGIBLE: "+path);return;
        }
        float x=dev54h15HitX,y=dev54h15HitY,z=dev54h15HitZ;
        if(dev54h15Stage==0){
            float best=Float.POSITIVE_INFINITY,cx=0f,cy=0f,cz=0f;
            String meshKey="legacy:"+m.source;
            for(int ch=0;ch<(m.vertexCount+23)/24;ch++){
                var spec=traincraft.client.renderer.animation.BakedWheelAnimationContext
                             .spec(meshKey,ch,m.vertices,FLOATS_PER_VERTEX);
                if(spec==null || !spec.rotates() || spec.axis()!=2)continue;
                float qx=spec.centerX(),qy=spec.centerY(),qz=spec.centerZ();
                if(!Float.isFinite(qx)||!Float.isFinite(qy)||!Float.isFinite(qz))continue;
                float ds=(x-qx)*(x-qx)+(y-qy)*(y-qy)+2f*(z-qz)*(z-qz);
                if(ds<best){best=ds;cx=qx;cy=qy;cz=qz;}
            }
            if(best>1.2f || best==Float.POSITIVE_INFINITY ||
                Math.hypot(x-cx,y-cy)<.04f){
                dev54h12cMessage("H15: no qualified wheel/crankpin near selection; choose wheel outer pin");return;
            }
            dev54h15SavedWheel=new Dev54H15Rod(path,cx,cy,cz,x,y,z,0,0,z,.078f,false,false);
            dev54h15Stage=1;
            dev54h12cMessage("H15 WHEEL selected. Aim at PISTON/CROSSHEAD and press P");
            LOGGER.info("[DEV54H15-WHEEL] mesh={} axle=({},{},{}) pin=({},{},{})",path,cx,cy,cz,x,y,z);
        }else if(dev54h15Stage==1){
            Dev54H15Rod a=dev54h15SavedWheel;
            if(a==null || !a.path().equals(path)){
                dev54h12cMessage("H15: select piston on SAME locomotive mesh");return;
            }
            dev54h15Draft=new Dev54H15Rod(path,a.ax(),a.ay(),a.az(),
                                a.wx(),a.wy(),a.wz(),x,y,z,.078f,false,false);
            if(!dev54h15Good(dev54h15Draft)){
                dev54h12cMessage("H15: piston point incompatible; aim at crosshead near same side");
                dev54h15Draft=null;return;
            }
            dev54h15Stage=2;dev54h15Preview=true;
            dev54h12cMessage("H15 PREVIEW: Z animate, arrows move piston; [ ] width; T hide old; X phase; ENTER save");
            LOGGER.info("[DEV54H15-PISTON] mesh={} piston=({},{},{})",path,x,y,z);
        }else{
            dev54h15Stage=0;dev54h15Draft=null;dev54h15Preview=false;
            dev54h12cMessage("H15 reselect: aim at wheel crankpin and press P");
        }
    }
    private static Dev54H15Rod dev54h15Rig(String path){
        if(!DEV54H15_ON || !dev54h15jRenderable(path))return null;
        dev54h15Load();
        if(dev54h15Editing && dev54h15Stage==2 && dev54h15Draft!=null &&
             dev54h15Draft.path().equals(path))return dev54h15Draft;
        return DEV54H15_SAVED.get(path);
    }
    private static boolean dev54h15Override(String path){
        Dev54H15Rod a=dev54h15Rig(path);
        return a!=null && a.replace();
    }
    private static void dev54h15Draw(PoseStack.Pose pose,VertexConsumer out,
                       int light,int overlay,Dev54H15Rod a,float sourcePhase,
                       float u,float v){
        boolean editing=dev54h15Editing && dev54h15Draft==a;
        float angle=editing && dev54h15Preview?
            (float)((dev54h15Ticks*.15d)%(Math.PI*2.0)):sourcePhase;
        if(!Float.isFinite(angle))angle=0f;
        float rx=a.wx()-a.ax(),ry=a.wy()-a.ay();
        if(DEV54H15AB_LSSP7_DIAG_ON &&
           a.path().equals("legacy_meshes/locomotive_steam_lssp7.tcm") &&
           DEV54H15AB_LOGGED.add(a.path()+":ROD_FRAME")){
            LOGGER.info("[DEV54H15AB-LSSP7-FRAME] model={} centerline={} selectedWheelLocal=({},{},{}) " +
                "pinLocal=({},{},{}) pistonLocal=({},{},{}) inset={} preview={} editing={} " +
                "H15AAOn={} H15YSourceHides=UNCHANGED",
                a.path(),DEV54H15AA_LSSP7_CENTER_Z,
                a.ax(),a.ay(),a.az(),a.wx(),a.wy(),a.wz(),
                a.px(),a.py(),a.pz(),dev54h15hInset(a.path()),dev54h15Preview,
                dev54h15Editing,DEV54H15AA_LSSP7_MIRROR_ON);
            for(int side=0;side<2;side++){
                float face=side==0?a.wz():dev54h15aaMirrorZ(a.path(),a.wz());
                float barZ=face+Math.signum(face-dev54h15aaZCenter(a.path()))*
                          (.042f-(DEV54H15H_ON?dev54h15hInset(a.path()):0f));
                var wheelPos=pose.pose().transformPosition(a.ax(),a.ay(),face,new Vector3f());
                var rodPos=pose.pose().transformPosition(a.wx(),a.wy(),barZ,new Vector3f());
                var pistonPos=pose.pose().transformPosition(a.px(),a.py(),barZ,new Vector3f());
                LOGGER.info("[DEV54H15AB-LSSP7-TRANSFORM] side={} wheelLocalZ={} barLocalZ={} " +
                    "wheelRendered=({},{},{}) pinRendered=({},{},{}) pistonRendered=({},{},{})",
                    side,face,barZ,wheelPos.x(),wheelPos.y(),wheelPos.z(),
                    rodPos.x(),rodPos.y(),rodPos.z(),
                    pistonPos.x(),pistonPos.y(),pistonPos.z());
            }
        }
        if(dev54h15aaLssp7(a.path()) && DEV54H15_REPORTED.add(a.path()+":CENTERED-MIRROR"))
            LOGGER.info("[DEV54H15AA-LSSP7-ROD-MIRROR] centerZ={} chosenZ={} oppositeZ={} " +
                        "sixWheelChunks=UNCHANGED oldRods=H15Y_HIDDEN visualPASS=NO",
                        DEV54H15AA_LSSP7_CENTER_Z,a.wz(),dev54h15aaMirrorZ(a.path(),a.wz()));
        float length=(float)Math.hypot(a.px()-a.wx(),a.py()-a.wy());
        // UV is sampled from this locomotive's verified original rod mesh.
        for(int side=0;side<2;side++){
            // Do not reflect LSSP7's offset-source wheel face through z=0.
            // For other fleet models the original zero-center rule is exact.
            float pinZ=side==0?a.wz():dev54h15aaMirrorZ(a.path(),a.wz());
            float z=pinZ+Math.signum(pinZ-dev54h15aaZCenter(a.path()))*(.042f-
                           (DEV54H15H_ON?dev54h15hInset(a.path()):0f));
            float p=angle+(side==1 && a.mirrorOpposite()?(float)Math.PI:0f);
            float px=a.ax()+rx*(float)Math.cos(p)-ry*(float)Math.sin(p);
            float py=a.ay()+rx*(float)Math.sin(p)+ry*(float)Math.cos(p);
            float direction=a.px()>=a.ax()?1f:-1f;
            float[] cross=dev54h13bHorizontalCrosshead(px,py,
                           a.px(),a.py(),length,direction,.55f);
            if(cross==null)continue;
            // Rod connects animated crankpin to constrained horizontal
            // crosshead; both sides are explicit solid 6-face boxes.
            dev54h14Bar(pose,out,light,overlay,px,py,cross[0],cross[1],
                         z,a.width(),.052f,246,u,v,true);
            // Small guide-bar portion stays INSIDE selected piston envelope.
            float endpoint=cross[0]+direction*.14f;
            dev54h14Bar(pose,out,light,overlay,cross[0],a.py(),endpoint,a.py(),
                         z,a.width()*.58f,.05f,247,u,v,true);
        }
    }
    // DEV54H14B: Batch 2 targeted piston-drive completion. This layer is
    // exclusively enabled by the H14B marker on top of H14A; it does NOT
    // modify the accepted C11/C62/BR80 nor future fleet reset rules.
    private static final boolean DEV54H14B_ON=dev54h14bMarker();
    private static final java.util.Set<String> DEV54H14B_REPORTED=
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h14bMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                  .resolve("DEV54H14B-BATCH2-LAST-FIVE-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                  .replace("\uFEFF","").trim().equals("DEV54H14B-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static boolean dev54h14bBrit(String path){
        return path.equals("legacy_meshes/locomotive_steam_br_britannia.tcm");
    }
    private static boolean dev54h14bC41(String path){return dev54h13dC41(path);}
    private static float dev54h14bPhaseSafe(float phase){
        return Float.isFinite(phase)?phase:0f;
    }

    private static boolean dev54h14bClimax(String path){
        return path.equals("legacy_meshes/locomotive_steam_climax.tcm");
    }
    // Wheel-group selection uses REAL native animation pivot centers rather
    // than guessed train-front bounds. Piston source parts must be outside
    // the leading wheel and match the correct side, else we HOLD instead of
    // moving an entire rod into a wrong end of the locomotive.
    private transient Dev54H14Rig dev54h14bBritRig;
    private transient int dev54h14bBritAttempts;
    private Dev54H14Side dev54h14bBritSide(java.util.List<Dev54H14Wheel> wheels,
                                    int side,Dev54H14Tags tags){
        if(wheels.isEmpty())return null;
        wheels.sort(java.util.Comparator.comparingDouble(Dev54H14Wheel::x));
        float lo=wheels.get(0).x(), hi=wheels.get(wheels.size()-1).x();
        Dev54H14Side selected=null;
        float nearest=Float.POSITIVE_INFINITY;
        for(int id:tags.pistons()){
            float[] piston=dev54h14Center(id);
            if(piston==null || piston[2]*side<.10f)continue;
            int direction=piston[0]>hi+.18f?1:piston[0]<lo-.18f?-1:0;
            if(direction==0)continue;
            Dev54H14Wheel wheel=direction>0?wheels.get(wheels.size()-1):wheels.get(0);
            float distance=(piston[0]-wheel.x())*direction;
            if(distance<.18f || distance>2.2f || Math.abs(piston[1]-wheel.y())>.70f)continue;
            float gy=piston[1]+.03125f;
            float radius=side>0?.15f:.10f;
            float joint=(float)Math.hypot(piston[0]-wheel.x(),gy-(wheel.y()+radius));
            float z=side*(Math.abs(wheel.z())+.070f);
            var candidate=new Dev54H14Side(wheel,null,z,piston[0],gy,
                                          direction,0f,radius,joint);
            if(dev54h14CycleGood(candidate) && distance<nearest){
                nearest=distance;selected=candidate;
            }
        }
        return selected;
    }
    private Dev54H14Rig dev54h14bBritPrepare(String key,Dev54H14Tags tags){
        var plus=new java.util.ArrayList<Dev54H14Wheel>();
        var minus=new java.util.ArrayList<Dev54H14Wheel>();
        for(int chunk=0;chunk<(this.vertexCount+23)/24;chunk++){
            var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(key,chunk,this.vertices,FLOATS_PER_VERTEX);
            if(wheel==null||!wheel.rotates()||wheel.axis()!=2||
               !Float.isFinite(wheel.centerX())||!Float.isFinite(wheel.centerY())||
               Math.abs(wheel.centerZ())<.08f)continue;
            var list=wheel.centerZ()>0?plus:minus;
            if(!dev54h14Near(list,wheel.centerX(),wheel.centerY()))
                list.add(new Dev54H14Wheel(wheel.centerX(),wheel.centerY(),wheel.centerZ()));
        }
        Dev54H14Side positive=dev54h14bBritSide(plus,1,tags);
        Dev54H14Side negative=dev54h14bBritSide(minus,-1,tags);
        if(positive==null || negative==null)return null;
        float[] uv=dev54h14OriginalRodUV(tags);
        return new Dev54H14Rig(positive,negative,0,uv[0],uv[1]);
    }
    private static void dev54h14bBritDraw(PoseStack.Pose pose,VertexConsumer consumer,
                                int light,int overlay,Dev54H14Rig rig,float phase){
        for(Dev54H14Side a:new Dev54H14Side[]{rig.positive(),rig.negative()}){
            float px=a.front().x()-a.r()*(float)Math.sin(phase);
            float py=a.front().y()+a.r()*(float)Math.cos(phase);
            float[] cross=dev54h14Cross(a,px,py);
            if(cross!=null)dev54h14Bar(pose,consumer,light,overlay,
                  px,py,cross[0],cross[1],a.z(),.075f,.052f,245,
                  rig.u(),rig.v(),true);
        }
    }
    // C41-family: the four source-qualified driver centers are X=.46875,
    // 1.21875,1.96875,2.71875, Y=-.40625. Draw ONE continuous coupling
    // from the first through the fourth wheel and ONE front-to-crosshead main
    // rod, on each side. Do not reintroduce any rear bar or piston overrun.
    private static void dev54h14bC41Draw(PoseStack.Pose pose,VertexConsumer out,
                    int light,int overlay,float phase,float u,float v){
        final float axleY=-.40625f,r=.125f,cs=(float)Math.cos(phase),
                sn=(float)Math.sin(phase);
        final float guideX=3.48f,guideY=-.2500f;
        for(float side:new float[]{-1f,1f}){
            // The verified H13 source couples left/right wheel pins with
            // opposite phase: negative Z starts at +R, positive Z at -R.
            float sign=side<0?1f:-1f;
            float shiftX=-sign*r*sn;
            float py=axleY+sign*r*cs;
            float frontX=2.71875f+shiftX;
            float rest=(float)Math.hypot(guideX-2.71875f,
                                         guideY-(axleY+sign*r));
            float[] cross=dev54h13bHorizontalCrosshead(frontX,py,
                                            guideX,guideY,rest,1f,.25f);
            if(cross==null)continue;
            float z=side*.455f;
            float rearX=.46875f+shiftX;
            dev54h14Bar(pose,out,light,overlay,rearX,py,frontX,py,
                        z,.078f,.056f,245,u,v,true);
            dev54h14Bar(pose,out,light,overlay,frontX,py,
                        cross[0],cross[1],z,.085f,.062f,250,u,v,true);
            // Short crosshead slide IN the fixed cylinder envelope only;
            // ends at guideX and never extends past the front piston.
            dev54h14Bar(pose,out,light,overlay,cross[0],guideY,
                        guideX,guideY,z,.050f,.052f,250,u,v,true);
        }
    }
    // Climax: H14A erroneously slid source 22/59 (center/middle visible
    // linkage). Select actual OUTER side rods from other source-tagged pieces
    // by positive/negative side Z, leaving central chunks 22/59 to their
    // native crank rotation. If no trustworthy outer pieces are found,
    // render two isolated outer-angle rods without masking any original.
    private transient int[] dev54h14bClimaxOuter;
    private int[] dev54h14bClimaxFindOuter(){
        int pos=-1,neg=-1;float pz=.28f,nz=.28f;
        for(int id:new int[]{47,178,184}){
            float[] c=dev54h14Center(id);if(c==null)continue;
            if(c[2]>pz){pos=id;pz=c[2];}
            if(c[2]<-nz){neg=id;nz=-c[2];}
        }
        return new int[]{neg,pos};
    }
    private static void dev54h14bClimaxFallback(PoseStack.Pose pose,
               VertexConsumer out,int light,int overlay,float phase,float u,float v){
        float s=(float)Math.sin(phase),c=(float)Math.cos(phase);
        float wheelX=-.34375f,wheelY=-.90625f,r=.125f;
        float px=wheelX-r*s,py=wheelY+r*c;
        float gx=-.719f,gy=-.30f,ux=.25882f,uy=-.96593f;
        float restPx=wheelX,restPy=wheelY+r, restS=.16f;
        float restCx=gx+restS*ux,restCy=gy+restS*uy;
        float length=(float)Math.hypot(restCx-restPx,restCy-restPy);
        float dx=px-gx,dy=py-gy,t=dx*ux+dy*uy;
        float disc=length*length-(dx*dx+dy*dy-t*t);
        if(!(disc>.0001f))return;
        float along=t-(float)Math.sqrt(disc);
        if(!Float.isFinite(along)||Math.abs(along-restS)>.35f)return;
        float cx=gx+along*ux,cy=gy+along*uy;
        for(float side:new float[]{-1f,1f}){
            float z=side*.705f;
            // Angled rigid side slider and pin-to-crosshead link, never
            // turn the central rotating source part into a sliding rod.
            dev54h14Bar(pose,out,light,overlay,cx-.20f*ux,cy-.20f*uy,
               cx+.24f*ux,cy+.24f*uy,z,.065f,.052f,245,u,v,true);
            dev54h14Bar(pose,out,light,overlay,px,py,cx,cy,
                         z,.068f,.052f,245,u,v,true);
        }
    }
    // DEV54H14A: fleet-wide solid closed rod geometry and conservative anchors.
    // Each change remains OFF unless H14A marker explicitly matches. Accepted
    // C11 keeps its exact H14 appearance, and BR80/Batch1 are absent from H14.
    private static final boolean DEV54H14A_ON=dev54h14aMarker();
    private static boolean dev54h14aMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                     .resolve("DEV54H14A-FLEET-ROD-CORRECTIONS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                   .replace("\uFEFF","").trim().equals("DEV54H14A-ON-V1");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<String> DEV54H14A_REPORTED=
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h14aDeferred(String p){
        // Britannia H14 picked an axle at the wrong end: suppress that
        // inaccurate reset rather than claiming a replacement is correct.
        // Climax is not ordinary horizontal running gear: restore source
        // rods instead of applying generic horizontal main/coupling bars.
        return p.equals("legacy_meshes/locomotive_steam_br_britannia.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_climax.tcm");
    }
    // C62 has a multipart body (not in H14 direct-TCM rig); it must be
    // handled independently, behind the prior exact H13E/H13B geometry gates.
    private void dev54h14aC62(PoseStack.Pose pose,VertexConsumer consumer,
                         int light,int overlay,float phase){
        float r=.10156f,cy=.4375f,s=(float)Math.sin(phase),c=(float)Math.cos(phase);
        float drive=-3.6875f,guideX=-4.875f,guideY=.609375f;
        float dx=-r*s,dy=r*c;
        float restLength=(float)Math.hypot(guideX-drive,guideY-(cy+r));
        float[] cross=dev54h13bHorizontalCrosshead(drive+dx,cy+dy,
                                                guideX,guideY,restLength,-1f,.28f);
        if(cross==null)return;
        // Sample original, source-qualified C62 geometry UV. Do not reactivate
        // any H13B/H13F dark bars or any of the 32 user-hidden TCM pieces.
        float u=.5f,v=.5f;
        int id=82,first=id*24*FLOATS_PER_VERTEX;
        if(first>=0 && first+24*FLOATS_PER_VERTEX<=this.vertices.length){
            u=0f;v=0f;
            for(int k=0;k<4;k++){
                int at=first+k*FLOATS_PER_VERTEX;
                u+=this.vertices[at+3]*.25f;v+=this.vertices[at+4]*.25f;
            }
            if(!Float.isFinite(u)||!Float.isFinite(v)){u=.5f;v=.5f;}
        }
        for(float side:new float[]{-1f,1f}){
            float z=side*.47f;
            dev54h14Bar(pose,consumer,light,overlay,-1.8125f+dx,cy+dy,
                              drive+dx,cy+dy,z,.071f,.048f,245,u,v,true);
            dev54h14Bar(pose,consumer,light,overlay,drive+dx,cy+dy,
                              cross[0],cross[1],z,.080f,.050f,245,u,v,true);
            // Stop at crosshead; never draw a bar past the piston housing.
        }
    }
    // DEV54H14: fleet-wide ROD RESET review candidate. Preserves wheel specs,
    // wheel render, fixed housings, native/multipart engines and accepted models.
    // No source TCM is ever modified. Unsupported wheel contexts fail closed.
    private static final boolean DEV54H14_ON=dev54h14ReadMarker();
    private static final java.util.Set<String> DEV54H14_REPORTED=
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h14ReadMarker(){
        try{
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                     .resolve("DEV54H14-FLEET-ROD-RESET-ON.txt");
            return java.nio.file.Files.isRegularFile(file) &&
                java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8)
                   .replace("\uFEFF","").trim().equals("DEV54H14-ON-V1");
        }catch(Exception ignored){return false;}
    }
    private record Dev54H14Tags(int[] rods,int[] pistons){
        boolean rod(int id){return java.util.Arrays.binarySearch(rods,id)>=0;}
    }
    private static final java.util.Map<String,Dev54H14Tags> DEV54H14_TAGS=dev54h14Tags();
    private static java.util.Map<String,Dev54H14Tags> dev54h14Tags(){
        var map=new java.util.HashMap<String,Dev54H14Tags>();
        map.put("legacy_meshes/locomotive_steam_br_britannia.tcm", new Dev54H14Tags(new int[]{206,207,211,216,217,233,236,237,240,241,246,247,249,250,251,341,344,345,346,350,351,353,354,355,363},new int[]{18,22,23,27,31,32}));
        map.put("legacy_meshes/locomotive_steam_c11.tcm", new Dev54H14Tags(new int[]{82,86,87,276,279,283,290,292,300,308,313,317,319,321,322,326,331,334,342,387,388},new int[]{145,149,157,162}));
        map.put("legacy_meshes/locomotive_steam_c41.tcm", new Dev54H14Tags(new int[]{45,46,47,48,49,50,53,422,423,424,425,426,427,433,435,436},new int[]{351,352,353,355,356}));
        map.put("legacy_meshes/locomotive_steam_c41_080.tcm", new Dev54H14Tags(new int[]{35,36,37,38,39,40,41,42,43,44,45,46,50,386,387,389,394,395,397,398,399,400,401,402,408},new int[]{320,321,322,323,324,325,326,327,328,329,330,331}));
        map.put("legacy_meshes/locomotive_steam_c41t.tcm", new Dev54H14Tags(new int[]{41,42,43,44,46,381,382,383,384,385,392,394},new int[]{311,312,313,314}));
        map.put("legacy_meshes/locomotive_steam_climax.tcm", new Dev54H14Tags(new int[]{22,47,59,178,184},new int[]{172,185}));
        map.put("legacy_meshes/locomotive_steam_climax_2.tcm", new Dev54H14Tags(new int[]{72,78,87,92,96,97,98,100,103},new int[]{101,102}));
        map.put("legacy_meshes/locomotive_steam_coronation_class.tcm", new Dev54H14Tags(new int[]{29,35,36,51,53,65,66,81,82,83,84,85,86,88,89,90,379,380,381,382,383,384},new int[]{34,55}));
        map.put("legacy_meshes/locomotive_steam_fowler.tcm", new Dev54H14Tags(new int[]{82,83},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_gs4.tcm", new Dev54H14Tags(new int[]{73,75,77,78,79,80,81,82,83,99,100,102,103,104,106,107,108,109},new int[]{39,74}));
        map.put("legacy_meshes/locomotive_steam_gwr_101_class.tcm", new Dev54H14Tags(new int[]{59,61,62,63,64,72,73,74,75,76,93,146},new int[]{57,174,197}));
        map.put("legacy_meshes/locomotive_steam_gwr_42xx.tcm", new Dev54H14Tags(new int[]{9,10,11,12,13,14,15,16,17,18,22,278},new int[]{187,194,279,280,285,287}));
        map.put("legacy_meshes/locomotive_steam_gwr_72xx.tcm", new Dev54H14Tags(new int[]{9,10,11,12,13,14,15,16,17,18,24,280},new int[]{190,197}));
        map.put("legacy_meshes/locomotive_steam_hall_class.tcm", new Dev54H14Tags(new int[]{442,443,444,445,446,448,451,455,461,462,463,468,473,474,492,493,502,503,504,529,530,531,547,548,549,556,557,558,562,563,564,579,580,581,593,594,595,897,898,899},new int[]{390,391,392,419,420,421,422}));
        map.put("legacy_meshes/locomotive_steam_j50.tcm", new Dev54H14Tags(new int[]{25,27,28,29,31,32,37,39,40,41,43,44,616,617,618,619},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_lssp7.tcm", new Dev54H14Tags(new int[]{43,44},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_midland_compound.tcm", new Dev54H14Tags(new int[]{38,39,40,43,44,45,82,83,84,85,87,88,93,94,117,118,119,121,124,125},new int[]{209,210,212,213,216,217,218,222}));
        map.put("legacy_meshes/locomotive_steam_milw_class_a.tcm", new Dev54H14Tags(new int[]{103,104,129,157,169,173,174,175,177,178,181,187,188,189,190,191,196,198},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_pannier.tcm", new Dev54H14Tags(new int[]{1,329,333,334},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_pe.tcm", new Dev54H14Tags(new int[]{206,207,211,212,214,215,217,219,220,222,228,232,233,241,242,243,245,246,249},new int[]{62,63,72,73,248}));
        map.put("legacy_meshes/locomotive_steam_rw_type_2.tcm", new Dev54H14Tags(new int[]{63,65,66,67,68,69,70,72,73,74,75,77,79,80,81,83,84,85,86,87,88,89,90,91,92,93},new int[]{156,157,158,160,161,162}));
        map.put("legacy_meshes/locomotive_steam_rw_type_3.tcm", new Dev54H14Tags(new int[]{115,119,122,133,137,140,146},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_s100_uk.tcm", new Dev54H14Tags(new int[]{398,399,400,401,402,403,404,408,409,411,412,413,414,415,416,417,418,419,423,424,425,427,428,429,430,431,433,434,435,437,439,442,443,445,446,447,449,450,451,452,454,455,456,457},new int[]{91,94,96,99}));
        map.put("legacy_meshes/locomotive_steam_s100_us.tcm", new Dev54H14Tags(new int[]{323,324,325,326,327,328,329,333,334,340,341,342,343,344,345,346,347,348,405,406,407,408,409,410,411,412,413,435,436,437,439,441,445,447,448,452,453,454,456,457,459},new int[]{91,94,96,99}));
        map.put("legacy_meshes/locomotive_steam_sentinel_y3.tcm", new Dev54H14Tags(new int[]{72,96,97,98},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_shay_3_truck.tcm", new Dev54H14Tags(new int[]{179,180,186,187,189,190,195,201,413,516},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_skookum.tcm", new Dev54H14Tags(new int[]{121,122,123,124,126,127,128,129,131,132,133,134,136,137,138,139,140,142,143,144},new int[]{53,66,67,68}));
        map.put("legacy_meshes/locomotive_steam_southern1102.tcm", new Dev54H14Tags(new int[]{36,37,38,44,45,275,276,277,278,282,283,285,286,287,288},new int[]{236,238}));
        map.put("legacy_meshes/locomotive_steam_u57.tcm", new Dev54H14Tags(new int[]{3,6,8,35,58,59},new int[]{5,7}));
        map.put("legacy_meshes/locomotive_steam_vb_0_4_0.tcm", new Dev54H14Tags(new int[]{9,10},new int[]{}));
        map.put("legacy_meshes/locomotive_steam_vb_shay.tcm", new Dev54H14Tags(new int[]{20,24,32,33,46,68},new int[]{34,35}));
        map.put("legacy_meshes/locomotive_steam_wwcp_062t.tcm", new Dev54H14Tags(new int[]{24,26,85,96,97,98,132,133,154,156},new int[]{15,18,23,144,145,148}));
        return java.util.Map.copyOf(map);
    }
    private record Dev54H14Wheel(float x,float y,float z){}
    private record Dev54H14Side(Dev54H14Wheel front,Dev54H14Wheel rear,
             float z,float guideX,float guideY,float ux,float uy,float r,float restLength){}
    private record Dev54H14Rig(Dev54H14Side positive,Dev54H14Side negative,int sourceRods,float u,float v){}
    private transient Dev54H14Rig dev54h14Rig;
    private transient int dev54h14Attempts;
    private static boolean dev54h14Near(java.util.List<Dev54H14Wheel> list,float x,float y){
        for(var w:list)if(Math.abs(w.x()-x)<.0625f && Math.abs(w.y()-y)<.0625f)return true;
        return false;
    }
    private float[] dev54h14Center(int ch){
        int begin=ch*24*FLOATS_PER_VERTEX;
        if(begin<0 || begin+24*FLOATS_PER_VERTEX>this.vertices.length)return null;
        float x=0f,y=0f,z=0f;
        for(int i=0;i<24;i++){
            int v=begin+i*FLOATS_PER_VERTEX;
            x+=this.vertices[v];y+=this.vertices[v+1];z+=this.vertices[v+2];
        }
        return new float[]{x/24f,y/24f,z/24f};
    }
    private Dev54H14Side dev54h14Side(java.util.List<Dev54H14Wheel> wheels,
                                      int sign,Dev54H14Tags tags){
        if(wheels.isEmpty())return null;
        wheels.sort(java.util.Comparator.comparingDouble(Dev54H14Wheel::x));
        float lo=wheels.get(0).x(),hi=wheels.get(wheels.size()-1).x();
        // Use identified source piston pieces to select the locomotive front.
        // Without a valid cylinder, compare available end-clearance and guess.
        float avgCylinder=0f;int n=0;
        for(int ch:tags.pistons()){
            float[] c=dev54h14Center(ch);
            if(c!=null && (sign>0?c[2]>0.08f:c[2]<-.08f)){
                avgCylinder+=c[0];n++;
            }
        }
        int frontSign;
        if(DEV54H14A_ON && !this.source.getPath().equals("legacy_meshes/locomotive_steam_c11.tcm")){
            // No speculative locomotive-front guessing if no real piston
            // part exists. Deferred models keep their previous geometry.
            if(n==0)return null;
            avgCylinder/=n;
            float outRight=avgCylinder-hi,outLeft=lo-avgCylinder;
            if(outRight<.12f && outLeft<.12f)return null;
            frontSign=outRight>outLeft?1:-1;
            if(dev54h13dC41(this.source.getPath()))frontSign=1;
        }else if(n>0){
            avgCylinder/=n;
            frontSign=(Math.abs(avgCylinder-hi)<Math.abs(avgCylinder-lo))?1:-1;
        }else{
            frontSign=(this.maxX-hi>lo-this.minX)?1:-1;
        }
        Dev54H14Wheel front=frontSign>0?wheels.get(wheels.size()-1):wheels.get(0);
        Dev54H14Wheel rear=null;
        for(var w:wheels){
            if(Math.abs(w.x()-front.x())>.30f && Math.abs(w.y()-front.y())<.20f &&
                    (rear==null || Math.abs(w.x()-front.x())>Math.abs(rear.x()-front.x())))rear=w;
        }
        float r=.09375f;
        float gx=front.x()+frontSign*.75f,gy=front.y()+.14f;
        // Prefer nearby real piston pieces, but never move the fixed housing.
        float best=Float.POSITIVE_INFINITY;
        for(int ch:tags.pistons()){
            float[] c=dev54h14Center(ch);
            if(c==null || sign*c[2]<.08f)continue;
            float dx=c[0]-front.x(),dy=c[1]-front.y();
            if(dx*frontSign<.22f || dx*frontSign>2.2f || Math.abs(dy)>.75f)continue;
            float dst=(float)Math.hypot(dx,dy);
            if(dst<best){best=dst;gx=c[0];gy=c[1];}
        }
        if(DEV54H14A_ON && dev54h13dC41(this.source.getPath()))
            gy=Math.max(gy,front.y()+.1875f);
        float ux=frontSign,uy=0f;
        // Vertical/geared running gear follows an inclined piston axis.
        String name=this.source.getPath();
        if(name.contains("climax")||name.contains("shay")){
            float ax=gx-front.x(),ay=gy-(front.y()+r);
            float len=(float)Math.hypot(ax,ay);
            if(len>.08f){ux=ax/len;uy=ay/len;}
        }
        float len=(float)Math.hypot(gx-front.x(),gy-(front.y()+r));
        if(len<.29f||len>3.0f)return null;
        // Keep new rods on outside of the wheels, retaining the exact wheel mesh.
        float z=sign*Math.min(1.55f,Math.abs(front.z())+.075f);
        return new Dev54H14Side(front,rear,z,gx,gy,ux,uy,r,len);
    }
    private Dev54H14Rig dev54h14Prepare(String meshKey,Dev54H14Tags tags){
        var positive=new java.util.ArrayList<Dev54H14Wheel>();
        var negative=new java.util.ArrayList<Dev54H14Wheel>();
        int count=(this.vertexCount+23)/24;
        for(int ch=0;ch<count;ch++){
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
            if(w==null||!w.rotates()||w.axis()!=2||!Float.isFinite(w.centerX())||
               !Float.isFinite(w.centerY())||Math.abs(w.centerZ())<.08f)continue;
            var side=w.centerZ()>0?positive:negative;
            if(!dev54h14Near(side,w.centerX(),w.centerY()))
                side.add(new Dev54H14Wheel(w.centerX(),w.centerY(),w.centerZ()));
        }
        if(positive.isEmpty()||negative.isEmpty())return null;
        var pos=dev54h14Side(positive,1,tags);
        var neg=dev54h14Side(negative,-1,tags);
        if(pos==null||neg==null||!dev54h14CycleGood(pos)||!dev54h14CycleGood(neg))return null;
        float[] uv=dev54h14OriginalRodUV(tags);
        return new Dev54H14Rig(pos,neg,tags.rods().length,uv[0],uv[1]);
    }
    private float[] dev54h14OriginalRodUV(Dev54H14Tags tags){
        // Avoid the old H13B hardcoded UV, which produced black/transparent
        // looking synthetic bars on some engines. Sample the center of a
        // source-authenticated original rod face instead.
        for(int ch:tags.rods()){
            int first=ch*24*FLOATS_PER_VERTEX;
            if(first<0 || first+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
            float u=0f,v=0f;
            for(int i=0;i<4;i++){
                int idx=first+i*FLOATS_PER_VERTEX;
                u+=this.vertices[idx+3];v+=this.vertices[idx+4];
            }
            if(Float.isFinite(u)&&Float.isFinite(v))return new float[]{u*.25f,v*.25f};
        }
        return new float[]{.5f,.5f};
    }
    private static void dev54h14Quad(PoseStack.Pose pose,VertexConsumer out,
              int light,int overlay,float[][] q,int rgb,float u,float v){
        float[] a=q[0],b=q[1],c=q[2];
        float ax=b[0]-a[0],ay=b[1]-a[1],az=b[2]-a[2];
        float bx=c[0]-b[0],by=c[1]-b[1],bz=c[2]-b[2];
        float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
        float norm=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(norm<1e-7f)return;
        nx/=norm;ny/=norm;nz/=norm;
        for(int i=0;i<4;i++)out.vertex(pose.pose(),q[i][0],q[i][1],q[i][2])
              .color(rgb,rgb,rgb,255).uv(u,v).overlayCoords(overlay)
              .uv2(light).normal(pose.normal(),nx,ny,nz).endVertex();
    }
    private static void dev54h14Bar(PoseStack.Pose pose,VertexConsumer out,
              int light,int overlay,float x0,float y0,float x1,float y1,
              float z,float width,float depth,int rgb,float u,float v,boolean corrected){
        float dx=x1-x0,dy=y1-y0,len=(float)Math.hypot(dx,dy);
        if(!Float.isFinite(len)||len<.035f||len>3.8f)return;
        float nx=-dy/len*width*.5f,ny=dx/len*width*.5f;
        float[][] q={{x0+nx,y0+ny,z-depth*.5f},{x0-nx,y0-ny,z-depth*.5f},
          {x1-nx,y1-ny,z-depth*.5f},{x1+nx,y1+ny,z-depth*.5f},
          {x0+nx,y0+ny,z+depth*.5f},{x0-nx,y0-ny,z+depth*.5f},
          {x1-nx,y1-ny,z+depth*.5f},{x1+nx,y1+ny,z+depth*.5f}};
        int[][] f={{0,1,2,3},{7,6,5,4},{4,5,1,0},{3,2,6,7},{4,0,3,7},{1,5,6,2}};
        for(int[] face:f){
            int a=face[0],b=corrected?face[3]:face[1],
                c=face[2],d=corrected?face[1]:face[3];
            dev54h14Quad(pose,out,light,overlay,
                 new float[][]{q[a],q[b],q[c],q[d]},rgb,u,v);
        }
    }
    private static boolean dev54h14CycleGood(Dev54H14Side a){
        // No black-bar fallback or intermittent half-cycle disappearance:
        // refuse the entire rig if the solver cannot reach the crosshead
        // at any of 256 sampled wheel positions, forward AND reverse.
        for(int i=0;i<256;i++){
            float angle=(float)(i*(Math.PI*2.0/256.0));
            float px=a.front().x()-a.r()*(float)Math.sin(angle);
            float py=a.front().y()+a.r()*(float)Math.cos(angle);
            if(dev54h14Cross(a,px,py)==null)return false;
        }
        return true;
    }
    private static float[] dev54h14Cross(Dev54H14Side a,float px,float py){
        // Circle/line intersection. Joint length remains constant each frame.
        float vx=px-a.guideX(),vy=py-a.guideY();
        float b=vx*a.ux()+vy*a.uy();
        float disc=a.restLength()*a.restLength()-(vx*vx+vy*vy-b*b);
        if(!Float.isFinite(disc)||disc<0f)return null;
        float d=(float)Math.sqrt(disc),q1=b+d,q2=b-d;
        float t=Math.abs(q1)<Math.abs(q2)?q1:q2;
        if(!Float.isFinite(t)||Math.abs(t)>.40f)return null;
        return new float[]{a.guideX()+t*a.ux(),a.guideY()+t*a.uy()};
    }
    private static void dev54h14SideDraw(PoseStack.Pose pose,VertexConsumer out,
                  int light,int overlay,Dev54H14Side a,float phase,float u,float v,
                  boolean corrected,boolean keepRear,boolean keepExtension){
        float sn=(float)Math.sin(phase),cs=(float)Math.cos(phase);
        float px=a.front().x()-a.r()*sn,py=a.front().y()+a.r()*cs;
        float[] cross=dev54h14Cross(a,px,py);
        if(cross==null)return;
        // Only the current H14 rod geometry is drawn. The accepted existing
        // wheels, old fixed shells and crankpins are never replaced or rotated.
        if(keepRear && a.rear()!=null){
            float rx=a.rear().x()-a.r()*sn,ry=a.rear().y()+a.r()*cs;
            dev54h14Bar(pose,out,light,overlay,rx,ry,px,py,a.z(),.070f,.042f,230,u,v,corrected);
        }
        dev54h14Bar(pose,out,light,overlay,px,py,cross[0],cross[1],a.z(),
                               .077f,.048f,236,u,v,corrected);
        if(keepExtension){
            float length=.32f;
            dev54h14Bar(pose,out,light,overlay,cross[0],cross[1],
                             cross[0]+a.ux()*length,cross[1]+a.uy()*length,
                             a.z(),.044f,.042f,235,u,v,corrected);
        }
    }
    private static void dev54h14Draw(PoseStack.Pose pose,VertexConsumer out,int light,
                         int overlay,Dev54H14Rig rig,float phase,String model){
        // C11 is user-approved as it looked with H14; leave its exact face
        // winding, coupling and piston extension on both sides unchanged.
        boolean corrected=DEV54H14A_ON &&
                   !model.equals("legacy_meshes/locomotive_steam_c11.tcm");
        boolean keepRear=!corrected || !dev54h13dC41(model);
        boolean keepExtension=!corrected;
        dev54h14SideDraw(pose,out,light,overlay,rig.positive(),phase,rig.u(),rig.v(),
                          corrected,keepRear,keepExtension);
        dev54h14SideDraw(pose,out,light,overlay,rig.negative(),phase,rig.u(),rig.v(),
                          corrected,keepRear,keepExtension);
    }
    // DEV54H13F: Batch 2 full-pass-first candidate. OFF preserves exact H13E.
    // Purpose: restore or add the visibly missing wheel-to-piston linkage for
    // the C41 family, C62, and Climax, while keeping earlier accepted models
    // untouched and retaining H13E cleanup as the parent state.
    private static final boolean DEV54H13F_ON = dev54h13fMarker();
    private static final java.util.Set<String> DEV54H13F_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h13fMarker() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H13F-BATCH2-FULLPASS-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF","").trim().equals("DEV54H13F-BATCH2-ON-V1");
        } catch(Exception ex) { return false; }
    }
    private static boolean dev54h13fTarget(String path) {
        return path.equals("legacy_meshes/locomotive_steam_c62_body.tcm")
            || path.equals("legacy_meshes/locomotive_steam_climax.tcm")
            || path.equals("legacy_meshes/locomotive_steam_c41.tcm")
            || path.equals("legacy_meshes/locomotive_steam_c41_080.tcm")
            || path.equals("legacy_meshes/locomotive_steam_c41t.tcm");
    }
    private static void dev54h13fDraw(PoseStack.Pose p,VertexConsumer out,
                           int light,int overlay,String path,float phase){
        float s=(float)Math.sin(phase),c=(float)Math.cos(phase);
        if(path.equals("legacy_meshes/locomotive_steam_c41.tcm") ||
           path.equals("legacy_meshes/locomotive_steam_c41_080.tcm") ||
           path.equals("legacy_meshes/locomotive_steam_c41t.tcm")){
            // Full-pass-first estimate based on accepted H13B wheel phase and
            // user screenshots: keep the existing coupling rod and add the
            // missing main rod + piston guide connection on both sides.
            float r=.09375f, axleX=2.71875f,axleY=-.40625f;
            float restPinX=axleX,restPinY=axleY+r;
            float guideX=3.48f,guideY=-.3125f;
            float restLen=(float)Math.hypot(guideX-restPinX,guideY-restPinY);
            float x=axleX-r*s,y=axleY+r*c;
            float[] cross=dev54h13bHorizontalCrosshead(x,y,guideX,guideY,restLen,1f,.24f);
            if(cross==null)return;
            for(float side:new float[]{-1f,1f}){
                float z=side*.465f;
                dev54h13bBar(p,out,light,overlay,x,y,cross[0],cross[1],z,.068f,.044f,198);
                dev54h13bBar(p,out,light,overlay,cross[0],guideY,4.03f,guideY,z,.043f,.044f,214);
            }
        }else if(path.equals("legacy_meshes/locomotive_steam_c62_body.tcm")){
            // Rebuild visible running gear after H13E hid the user-selected
            // source pieces. Draw coupling rod + main rod + piston slider.
            float r=.10156f,cy=.4375f;
            float sx=-r*s,sy=r*c;
            float drive=-3.6875f, guideX=-4.875f,guideY=.50f;
            float restLen=(float)Math.hypot(guideX-drive,guideY-(cy+r));
            float[] cross=dev54h13bHorizontalCrosshead(drive+sx,cy+sy,
                                                  guideX,guideY,restLen,-1f,.23f);
            if(cross==null)return;
            for(float side:new float[]{-1f,1f}){
                float z=side*.47f;
                dev54h13bBar(p,out,light,overlay,-1.8125f+sx,cy+sy,
                                -3.6875f+sx,cy+sy,z,.071f,.042f,200);
                dev54h13bBar(p,out,light,overlay,drive+sx,cy+sy,
                                cross[0],cross[1],z,.071f,.042f,194);
                dev54h13bBar(p,out,light,overlay,cross[0],guideY,
                                -5.31f,guideY,z,.040f,.038f,216);
            }
        }else if(path.equals("legacy_meshes/locomotive_steam_climax.tcm")){
            // Visible bilateral diagonal rod motion using the same wheel-phase
            // solution as H13B, promoted into an explicit full-pass candidate.
            float wheelX=-.34375f,wheelY=-.90625f,r=.125f;
            float px=wheelX-r*s,py=wheelY+r*c;
            float gx=-.719f,gy=-.30f,ux=.25882f,uy=-.96593f;
            float restPx=wheelX,restPy=wheelY+r;
            float vx=restPx-gx,vy=restPy-gy;
            float restS=.16f;
            float restCx=gx+restS*ux,restCy=gy+restS*uy;
            float length=(float)Math.hypot(restCx-restPx,restCy-restPy);
            float dx=px-gx,dy=py-gy,t=dx*ux+dy*uy;
            float disc=length*length-(dx*dx+dy*dy-t*t);
            if(!(disc>.0001f))return;
            float crossS=t-(float)Math.sqrt(disc);
            if(!Float.isFinite(crossS)||Math.abs(crossS-restS)>.35f)return;
            float cx=gx+crossS*ux,cy=gy+crossS*uy;
            for(float side:new float[]{-1f,1f}){
                float z=side*.70f;
                dev54h13bBar(p,out,light,overlay,px,py,cx,cy,z,.068f,.040f,188);
                dev54h13bBar(p,out,light,overlay,cx,cy,gx,gy,z,.048f,.040f,210);
            }
        }
    }
    // DEV54H13E: faithful user-picked C11/C62 cleanup.
    // C62 H13C lacked dev44R11Verified because it is a multipart body;
    // H13B dark synthetic drawing therefore remained on. H13E only changes
    // that exact body under the existing qualified H13B source geometry gate.
    // OFF leaves exact frozen H13D rendering; neither TCM nor driver is edited.
    private static final boolean DEV54H13E_ON = dev54h13eMarker();
    private static final java.util.Set<Integer> DEV54H13E_C62_HIDE =
            java.util.Set.of(76,78,79,80,81,82,83,84,85,87,93,94,95,96,97,98,99,100,101,102,103,104,105,107,110,113,114,115,116,117,118,119);
    private static final java.util.Set<String> DEV54H13E_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h13eMarker() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H13E-C11-C62-CLEANUP-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF","").trim().equals("DEV54H13E-C11-C62-ON-V1");
        } catch(Exception ex) { return false; }
    }
    // DEV54H13D: isolated visual follow-up; source mesh assets unchanged.
    // H13C remains the fallback when this selector is OFF.
    private static final boolean DEV54H13D_ON = dev54h13dMarker();
    private static boolean dev54h13dMarker() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H13D-BATCH2-VISUAL-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF","").trim().equals("DEV54H13D-BATCH2-ON-V1");
        } catch(Exception ex) { return false; }
    }
    private static boolean dev54h13dC41(String p) {
        return p.equals("legacy_meshes/locomotive_steam_c41.tcm")
            || p.equals("legacy_meshes/locomotive_steam_c41_080.tcm")
            || p.equals("legacy_meshes/locomotive_steam_c41t.tcm");
    }
    private static void dev54h13dSourceMotion(String p, float phase,
                    java.util.Map<Integer,float[]> offsets) {
        if(p.equals("legacy_meshes/locomotive_steam_br80.tcm")) {
            // Lift all six accepted H13B real rods together; do not change
            // eccentric radius, phase, wheel meshes or cylinder shells.
            for(int id:new int[]{50,51,52,53,54,55}) {
                float[] o=offsets.get(id);
                if(o!=null) offsets.put(id,new float[]{o[0],o[1]+.03125f,o[2]});
            }
        } else if(p.equals("legacy_meshes/locomotive_steam_climax.tcm")) {
            // Verified library tags: original side rod chunks 22 and 59.
            // Move ONLY those two source-textured rods along existing
            // angled sliding direction, phase locked to drivetrain.
            // All gearbox/crank/cylinder parts stay in original placement.
            float stroke=.0625f*(float)Math.sin(phase);
            float[] along={.25882f*stroke,-.96593f*stroke,0f};
            offsets.put(22,along);
            offsets.put(59,along.clone());
        }
    }
    // DEV54H13C: Batch 2 runtime-confirmed corrections, no source TCM edits.
    // OFF preserves H13B byte-for-byte runtime behavior. User-marked hide IDs
    // are NEVER inferred, and are accepted for only four affected models.
    private static final boolean DEV54H13C_ON=dev54h13cMarker();
    private static final java.util.Set<String> DEV54H13C_REPORTED=
                         java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h13cMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                 .resolve("DEV54H13C-BATCH2-CONNECTIONS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equals("DEV54H13C-BATCH2-ON-V1");
        }catch(Exception ignored){return false;}
    }
    private static boolean dev54h13cRepairModel(String p){
        return switch(p){
            case "legacy_meshes/locomotive_steam_br80.tcm",
                 "legacy_meshes/locomotive_steam_c11.tcm",
                 "legacy_meshes/locomotive_steam_c41.tcm",
                 "legacy_meshes/locomotive_steam_c41_080.tcm",
                 "legacy_meshes/locomotive_steam_c41t.tcm",
                 "legacy_meshes/locomotive_steam_c62_body.tcm",
                 "legacy_meshes/locomotive_steam_climax.tcm" -> true;
            default -> false;
        };
    }
    private static boolean dev54h13cHideModel(String p){
        return p.equals("legacy_meshes/locomotive_steam_c11.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41_080.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41t.tcm");
    }
    private static boolean dev54h13cProtected(String p,int ch){
        if(ch<0)return true;
        return switch(p){
            case "legacy_meshes/locomotive_steam_c11.tcm" ->
                   java.util.Set.of(0,5,123,359,370,373,300,301,331,334,77,311).contains(ch);
            case "legacy_meshes/locomotive_steam_c41.tcm" ->
                   java.util.Set.of(0,253,254,255,256,257,258,259,39,47).contains(ch);
            case "legacy_meshes/locomotive_steam_c41_080.tcm" ->
                   java.util.Set.of(0,230,231,232,233,234,235,236,35,43).contains(ch);
            case "legacy_meshes/locomotive_steam_c41t.tcm" ->
                   java.util.Set.of(0,210,211,212,213,214,215,216,34,42).contains(ch);
            default -> true;
        };
    }
    private static final java.util.Map<String,java.util.Set<Integer>> DEV54H13C_MARKED=
                  dev54h13cLoadMarks();
    private static java.util.Map<String,java.util.Set<Integer>> dev54h13cLoadMarks(){
        if(!DEV54H13C_ON)return java.util.Map.of();
        var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                  .resolve("DEV54H13C-BATCH2-MARKED-HIDES.csv");
        if(!java.nio.file.Files.isRegularFile(p))return java.util.Map.of();
        try{
            var map=new java.util.HashMap<String,java.util.Set<Integer>>();
            int n=0;
            var lines=java.nio.file.Files.readAllLines(p,java.nio.charset.StandardCharsets.UTF_8);
            for(String raw:lines){
                String line=raw.replace("\uFEFF","").trim();
                if(line.isBlank()||line.equalsIgnoreCase("mesh,chunk,action"))continue;
                String[] cells=line.split(",",-1);
                if(cells.length!=3 || !cells[2].trim().equals("HIDE_CANDIDATE"))
                    throw new IllegalArgumentException("invalid CSV entry");
                String name=cells[0].trim();
                if(name.startsWith("traincraft:"))name=name.substring("traincraft:".length());
                int id=Integer.parseInt(cells[1].trim());
                if(!dev54h13cHideModel(name) || id<0 || id>511 ||
                   dev54h13cProtected(name,id))
                    throw new IllegalArgumentException("unsupported or protected mesh chunk");
                map.computeIfAbsent(name,k->new java.util.HashSet<>()).add(id);
                if(++n>64)throw new IllegalArgumentException("too many selections");
            }
            var frozen=new java.util.HashMap<String,java.util.Set<Integer>>();
            for(var e:map.entrySet())frozen.put(e.getKey(),java.util.Set.copyOf(e.getValue()));
            return java.util.Map.copyOf(frozen);
        }catch(Exception ex){
            // Load-time fail-closed; class LOGGER is initialized later.
            // Import script performs explicit validation and diagnostics.
            return java.util.Map.of();
        }
    }
    private static boolean dev54h13cRestore(String p,int ch){
        return p.equals("legacy_meshes/locomotive_steam_c11.tcm") && (ch==77||ch==311);
    }
    private static boolean dev54h13cUserHide(String p,int ch){
        return DEV54H13C_MARKED.getOrDefault(p,java.util.Set.of()).contains(ch) &&
               !dev54h13cProtected(p,ch);
    }
    private static void dev54h13cSourceMotion(String path,float phase,
                                             java.util.Map<Integer,float[]> offsets){
        if(path.equals("legacy_meshes/locomotive_steam_c11.tcm")){
            // H13B wrongly drove 301/334 with radius .09375 while linked
            // segments 300/331 used .15625; the ends visibly separated.
            // Restore the ORIGINAL inter-segment collar 77/311 too.
            float[] n=offsets.get(300),p=offsets.get(331);
            if(n!=null && p!=null){
                offsets.put(301,n.clone()); offsets.put(77,n.clone());
                offsets.put(334,p.clone()); offsets.put(311,p.clone());
            }
            // 101/384 are source rectangular guide blocks, not sliding shafts.
            // Stop the unintended H13B block displacement; retain static
            // visibility until the user explicitly marks unwanted chunks.
            offsets.remove(101); offsets.remove(384);
        }else if(path.equals("legacy_meshes/locomotive_steam_c62_body.tcm")){
            // Replace black-looking synthetic H13B bars with ORIGINAL textured
            // TCM rod pieces. Source driver center y=.4375; original coupling
            // pin y=.28125, hence eccentric radius=.15625. Model/body only:
            // bogie meshes and their renderer paths are NEVER changed.
            float sn=(float)Math.sin(phase),cs=(float)Math.cos(phase);
            float[] shift={-.15625f*sn,.15625f*(1f-cs),0f};
            for(int ch:new int[]{82,83,98,101,115,116})offsets.put(ch,shift);
        }
    }

    // DEV54H13B: source-joint Batch 2 finishing PILOT. No change to wheel
    // animators, fixed cylinder shells, BR01, Cherepanov, or frozen Batch 1.
    private static final boolean DEV54H13B_ON=dev54h13bMarker();
    private static final java.util.Set<String> DEV54H13B_REPORTED=
                         java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h13bMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                  .resolve("DEV54H13B-BATCH2-LINKAGES-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                   .replace("\uFEFF","").trim().equals("DEV54H13B-BATCH2-ON-V1");
        }catch(Exception ignored){return false;}
    }
    private static boolean dev54h13bModel(String p){
        return switch(p){
            case "legacy_meshes/locomotive_steam_br80.tcm",
                 "legacy_meshes/locomotive_steam_c11.tcm",
                 "legacy_meshes/locomotive_steam_c41.tcm",
                 "legacy_meshes/locomotive_steam_c41_080.tcm",
                 "legacy_meshes/locomotive_steam_c41t.tcm",
                 "legacy_meshes/locomotive_steam_c62_body.tcm",
                 "legacy_meshes/locomotive_steam_climax.tcm" -> true;
            default -> false;
        };
    }
    private static boolean dev54h13bGeometry(String path,float[] v,int stride){
        return switch(path){
            case "legacy_meshes/locomotive_steam_br80.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,43,-0.500000000f,0.250000004f,0.000000000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,44,0.749999970f,0.250000004f,0.000000000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,45,0.125000000f,0.250000004f,0.000000000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,50,0.250000000f,0.156250004f,0.406250000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,51,0.166985735f,0.247278228f,0.468750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,52,-0.999999970f,0.343750000f,0.468750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,53,0.250000000f,0.156250004f,-0.406250000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,54,0.166985735f,0.247278228f,-0.468750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,55,-0.999999970f,0.343750000f,-0.468750000f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c11.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,0,0.593750000f,0.281250004f,0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,5,0.593750000f,0.281250004f,-0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,101,0.562500000f,0.093750004f,0.500000000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,123,-1.031249970f,0.281250004f,-0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,300,-0.656250000f,0.437500000f,-0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,301,0.156250000f,0.437500000f,-0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,331,-0.656250000f,0.437500000f,0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,334,0.156250000f,0.437500000f,0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,359,-1.031249970f,0.281250004f,0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,370,-0.281250000f,0.281250004f,0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,373,-0.281250000f,0.281250004f,-0.315625012f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,384,0.562500000f,0.093750004f,-0.500000000f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,0,2.718750000f,-0.406250004f,-0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,39,1.718750030f,-0.281250000f,-0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,47,1.468750030f,-0.531250000f,0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,253,1.968750060f,-0.406250004f,-0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,254,1.218749970f,-0.406250004f,-0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,255,0.468750004f,-0.406250004f,-0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,256,0.468750004f,-0.406250004f,0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,257,1.218749970f,-0.406250004f,0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,258,1.968750060f,-0.406250004f,0.313124999f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,259,2.718750000f,-0.406250004f,0.313124999f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41_080.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,0,2.718750000f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,35,1.718750030f,-0.281250000f,-0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,43,1.468750030f,-0.531250000f,0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,230,1.968750060f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,231,1.218750030f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,232,0.468750004f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,233,0.468750004f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,234,1.218750030f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,235,1.968750060f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,236,2.718750000f,-0.406250000f,0.313125014f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41t.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,0,2.718750000f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,34,1.718750030f,-0.281250000f,-0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,42,1.468750030f,-0.531250000f,0.343750000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,210,1.968750060f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,211,1.218750030f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,212,0.468750004f,-0.406250000f,-0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,213,0.468750004f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,214,1.218750030f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,215,1.968750060f,-0.406250000f,0.313125014f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,216,2.718750000f,-0.406250000f,0.313125014f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c62_body.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,0,-1.812500000f,0.437500000f,-0.377968758f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,1,-3.687500000f,0.437500000f,0.378281251f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,8,-2.750000000f,0.437500000f,0.378281251f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,12,-1.812500000f,0.437500000f,0.378281251f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,21,-2.750000000f,0.437500000f,-0.377968758f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,26,-3.687500000f,0.437500000f,-0.377968758f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_climax.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,229,-0.343750004f,-0.906249970f,-0.687500000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,230,-0.343750004f,-0.906249970f,0.687500000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,231,-0.943749994f,-0.468750000f,-0.406250000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,247,-1.099999994f,-0.468750000f,0.406250000f,.002f))yield false;
                yield true;
            }
            default -> false;
        };
    }
    private static boolean dev54h13bReShow(String path,int ch){
        return switch(path){
            case "legacy_meshes/locomotive_steam_c11.tcm" -> ch==301 || ch==334;
            default -> false;
        };
    }
    private static void dev54h13bOriginalMotion(String path,float phase,
                                         java.util.Map<Integer,float[]> offsets){
        float s=(float)Math.sin(phase),c=(float)Math.cos(phase);
        if(path.equals("legacy_meshes/locomotive_steam_br80.tcm")){
            // 3 source-authored rod pieces per side, original driving axle
            // pivots -0.5 / 0.125 / 0.75. Each follows the same rigid stroke.
            float[] shift={.09375f*s,.09375f*(c-1f),0f};
            for(int ch:new int[]{50,51,52,53,54,55})offsets.put(ch,shift);
        }else if(path.equals("legacy_meshes/locomotive_steam_c11.tcm")){
            // Additional two original wheel linkage bars and two straight
            // piston guides, preserving native C11 H13 coupling 300/331.
            float[] link={.09375f*s,.09375f*(c-1f),0f};
            offsets.put(301,link);offsets.put(334,link);
            float[] slide={.09375f*s,0f,0f};
            offsets.put(101,slide);offsets.put(384,slide);
        }
    }
    // Rendering in pre-existing mesh local coordinates. The explicit source
    // geometry guards above ensure this never draws on a substituted TCM.
    private static void dev54h13bQuad(PoseStack.Pose p,VertexConsumer out,
              int light,int overlay,float[][] q,int rgb){
        float[] u={.39f,.51f,.51f,.39f},v={.38f,.38f,.52f,.52f};
        float[] a=q[0],b=q[1],c=q[2];
        float ax=b[0]-a[0],ay=b[1]-a[1],az=b[2]-a[2];
        float bx=c[0]-b[0],by=c[1]-b[1],bz=c[2]-b[2];
        float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
        float ln=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(ln<1e-7f)return;
        nx/=ln;ny/=ln;nz/=ln;
        for(int i=0;i<4;i++)out.vertex(p.pose(),q[i][0],q[i][1],q[i][2])
             .color(rgb,rgb,rgb,255).uv(u[i],v[i])
             .overlayCoords(overlay).uv2(light).normal(p.normal(),nx,ny,nz).endVertex();
    }
    // A rectangular original-style steel rod between two pinned endpoints;
    // no texture files or source mesh vertices are modified.
    private static void dev54h13bBar(PoseStack.Pose p,VertexConsumer out,
         int light,int overlay,float x0,float y0,float x1,float y1,
         float z,float width,float depth,int rgb){
        float dx=x1-x0,dy=y1-y0;
        float len=(float)Math.hypot(dx,dy);
        if(!Float.isFinite(len)||len<.035f||len>3.8f)return;
        float nx=-dy/len*width*.5f,ny=dx/len*width*.5f;
        float[][] k={{x0+nx,y0+ny,z-depth*.5f},{x0-nx,y0-ny,z-depth*.5f},
          {x1-nx,y1-ny,z-depth*.5f},{x1+nx,y1+ny,z-depth*.5f},
          {x0+nx,y0+ny,z+depth*.5f},{x0-nx,y0-ny,z+depth*.5f},
          {x1-nx,y1-ny,z+depth*.5f},{x1+nx,y1+ny,z+depth*.5f}};
        int[][] faces={{0,1,2,3},{7,6,5,4},{4,5,1,0},{3,2,6,7},{4,0,3,7},{1,5,6,2}};
        for(int[] f:faces)dev54h13bQuad(p,out,light,overlay,
             new float[][]{k[f[0]],k[f[1]],k[f[2]],k[f[3]]},rgb);
    }
    // Circle/line-slider solution: an angled main rod ends at the moving
    // wheel crankpin, while the crosshead remains on its straight axis.
    private static float[] dev54h13bHorizontalCrosshead(float pinX,float pinY,
                                   float crossRestX,float crossY,float restLength,
                                   float towards,float maxStroke){
        float rise=crossY-pinY,remain=restLength*restLength-rise*rise;
        if(!(remain>.0001f))return null;
        float x=pinX+towards*(float)Math.sqrt(remain);
        if(!Float.isFinite(x)||Math.abs(x-crossRestX)>maxStroke)return null;
        return new float[]{x,crossY};
    }
    private static void dev54h13bDraw(PoseStack.Pose p,VertexConsumer out,
                           int light,int overlay,String path,float phase){
        float s=(float)Math.sin(phase),c=(float)Math.cos(phase);
        if(path.equals("legacy_meshes/locomotive_steam_c41.tcm") ||
           path.equals("legacy_meshes/locomotive_steam_c41_080.tcm") ||
           path.equals("legacy_meshes/locomotive_steam_c41t.tcm")){
            // All three C41s: four driver axles from X=.46875 to 2.71875,
            // cylinder point ahead of +X end. Two individual crank joints.
            float r=.09375f, axleX=2.71875f,axleY=-.40625f;
            float restPinX=axleX,restPinY=axleY+r;
            float guideX=3.48f,guideY=-.3125f;
            float restLen=(float)Math.hypot(guideX-restPinX,guideY-restPinY);
            float x=axleX-r*s,y=axleY+r*c;
            float[] cross=dev54h13bHorizontalCrosshead(x,y,guideX,guideY,restLen,1f,.24f);
            if(cross==null)return;
            for(float side:new float[]{-1f,1f}){
                float z=side*.465f;
                dev54h13bBar(p,out,light,overlay,x,y,cross[0],cross[1],z,.068f,.044f,166);
                dev54h13bBar(p,out,light,overlay,cross[0],guideY,4.03f,guideY,z,.043f,.044f,184);
            }
        }else if(path.equals("legacy_meshes/locomotive_steam_c62_body.tcm")){
            // C62 is multipart: body driving wheels only. Render original-style
            // side rods in BODY mesh coordinates; separate bogies untouched.
            float r=.10156f,cy=.4375f;
            float sx=-r*s,sy=r*c;
            float drive=-3.6875f, guideX=-4.875f,guideY=.50f;
            float restLen=(float)Math.hypot(guideX-drive,guideY-(cy+r));
            float[] cross=dev54h13bHorizontalCrosshead(drive+sx,cy+sy,
                                                  guideX,guideY,restLen,-1f,.23f);
            if(cross==null)return;
            for(float side:new float[]{-1f,1f}){
                float z=side*.47f;
                dev54h13bBar(p,out,light,overlay,-1.8125f+sx,cy+sy,
                                -3.6875f+sx,cy+sy,z,.071f,.042f,172);
                dev54h13bBar(p,out,light,overlay,drive+sx,cy+sy,
                                cross[0],cross[1],z,.071f,.042f,170);
                dev54h13bBar(p,out,light,overlay,cross[0],guideY,
                                -5.31f,guideY,z,.040f,.038f,187);
            }
        }else if(path.equals("legacy_meshes/locomotive_steam_climax.tcm")){
            // Climax drives an angled vertical slider from its geared disk,
            // NOT a conventional straight horizontal coupling rod.
            float wheelX=-.34375f,wheelY=-.90625f,r=.125f;
            float px=wheelX-r*s,py=wheelY+r*c;
            float gx=-.719f,gy=-.30f,ux=.25882f,uy=-.96593f;
            float restPx=wheelX,restPy=wheelY+r;
            float vx=restPx-gx,vy=restPy-gy;
            float restS=.16f;
            float restCx=gx+restS*ux,restCy=gy+restS*uy;
            float length=(float)Math.hypot(restCx-restPx,restCy-restPy);
            float dx=px-gx,dy=py-gy,t=dx*ux+dy*uy;
            float disc=length*length-(dx*dx+dy*dy-t*t);
            if(!(disc>.0001f))return;
            float crossS=t-(float)Math.sqrt(disc);
            if(!Float.isFinite(crossS)||Math.abs(crossS-restS)>.35f)return;
            float cx=gx+crossS*ux,cy=gy+crossS*uy;
            for(float side:new float[]{-1f,1f}){
                float z=side*.70f;
                dev54h13bBar(p,out,light,overlay,px,py,cx,cy,z,.060f,.038f,157);
                dev54h13bBar(p,out,light,overlay,cx,cy,gx,gy,z,.045f,.038f,180);
            }
        }
    }

    // DEV54H13: five-model source-verified bilateral coupling-bar pilot.
    // All old accepted locomotives, C62 multipart, BR01 native and Climax gearing untouched.
    private static final boolean DEV54H13_ON=dev54h13Marker();
    private static final java.util.Set<String> DEV54H13_REPORTED=
                  java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h13Marker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H13-BATCH2-SOURCE-PILOT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
              java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equals("DEV54H13-BATCH2-PILOT-V1");
        }catch(Exception e){return false;}
    }
    private static boolean dev54h13CouplingModel(String p){
        return p.equals("legacy_meshes/locomotive_steam_br_britannia.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c11.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41_080.tcm") ||
               p.equals("legacy_meshes/locomotive_steam_c41t.tcm");
    }
    private static final java.util.Set<Integer> DEV54H13_TAGS_LOCOMOTIVE_STEAM_BR_BRITANNIA=java.util.Set.of(206,207,208,209,211,212,213,214,215,216,217,232,233,234,235,236,237,240,241,246,247,248,249,250,251,252,287,340,341,342,343,344,345,346,350,351,352,353,354,355,356,357,359,360,361,362,363);
    private static final java.util.Set<Integer> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C11=java.util.Set.of(77,82,84,86,87,276,279,283,290,292,296,300,308,311,313,315,317,319,321,322,326,327,329,331,334,342,387,388);
    private static final java.util.Set<Integer> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41=java.util.Set.of(36,37,39,40,41,42,43,44,45,46,47,48,49,50,53,54,411,412,413,414,415,416,417,418,419,420,421,422,423,424,425,426,427,428,429,430,431,432,433,434,435,436,438);
    private static final java.util.Set<Integer> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41_080=java.util.Set.of(35,36,37,38,39,40,41,42,43,44,45,46,49,50,386,387,388,389,390,391,392,393,394,395,396,397,398,399,400,401,402,403,404,405,406,407,408,409,410,413);
    private static final java.util.Set<Integer> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41T=java.util.Set.of(31,32,34,35,36,37,38,39,40,41,42,43,44,45,46,47,371,372,373,374,375,376,377,378,379,380,381,382,383,384,385,386,387,388,389,390,391,392,393,394,397,398);
    private static final java.util.Set<Integer> DEV54H13_KEEP_LOCOMOTIVE_STEAM_BR_BRITANNIA=java.util.Set.of(233,341);
    private static final java.util.Set<Integer> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C11=java.util.Set.of(300,331);
    private static final java.util.Set<Integer> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41=java.util.Set.of(39,47);
    private static final java.util.Set<Integer> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41_080=java.util.Set.of(35,43);
    private static final java.util.Set<Integer> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41T=java.util.Set.of(34,42);
    private static java.util.Set<Integer> dev54h13Tagged(String path){
        return switch(path){
            case "legacy_meshes/locomotive_steam_br_britannia.tcm" -> DEV54H13_TAGS_LOCOMOTIVE_STEAM_BR_BRITANNIA;
            case "legacy_meshes/locomotive_steam_c11.tcm" -> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C11;
            case "legacy_meshes/locomotive_steam_c41.tcm" -> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41;
            case "legacy_meshes/locomotive_steam_c41_080.tcm" -> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41_080;
            case "legacy_meshes/locomotive_steam_c41t.tcm" -> DEV54H13_TAGS_LOCOMOTIVE_STEAM_C41T;
            default -> java.util.Set.of();
        };
    }
    private static java.util.Set<Integer> dev54h13Visible(String path){
        return switch(path){
            case "legacy_meshes/locomotive_steam_br_britannia.tcm" -> DEV54H13_KEEP_LOCOMOTIVE_STEAM_BR_BRITANNIA;
            case "legacy_meshes/locomotive_steam_c11.tcm" -> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C11;
            case "legacy_meshes/locomotive_steam_c41.tcm" -> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41;
            case "legacy_meshes/locomotive_steam_c41_080.tcm" -> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41_080;
            case "legacy_meshes/locomotive_steam_c41t.tcm" -> DEV54H13_KEEP_LOCOMOTIVE_STEAM_C41T;
            default -> java.util.Set.of();
        };
    }
    private static boolean dev54h13GeometryGuard(String path,float[] v,int stride){
        return switch(path){
            case "legacy_meshes/locomotive_steam_br_britannia.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,233,0.45937499f,0.11562500f,-0.41718750f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,341,0.39687499f,-0.13437500f,0.41718750f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c11.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,300,-0.65625000f,0.43750000f,-0.34375000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,331,-0.65625000f,0.43750000f,0.34375000f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,39,1.71875003f,-0.28125000f,-0.34375000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,47,1.46875003f,-0.53125000f,0.34375000f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41_080.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,35,1.71875003f,-0.28125000f,-0.34375000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,43,1.46875003f,-0.53125000f,0.34375000f,.002f))yield false;
                yield true;
            }
            case "legacy_meshes/locomotive_steam_c41t.tcm" -> {
                if(!dev54h8bChunkCenterMatches(v,stride,34,1.71875003f,-0.28125000f,-0.34375000f,.002f))yield false;
                if(!dev54h8bChunkCenterMatches(v,stride,42,1.46875003f,-0.53125000f,0.34375000f,.002f))yield false;
                yield true;
            }
            default -> false;
        };
    }
    private static void dev54h13Couplings(String path,float phase,
                                 java.util.Map<Integer,float[]> offsets){
        final float sn=(float)Math.sin(phase), cs=(float)Math.cos(phase);
        switch(path){
            case "legacy_meshes/locomotive_steam_br_britannia.tcm" -> {
                offsets.put(233,new float[]{-0.10000000f*sn,0.10000000f*(cs-1f),0f});
                offsets.put(341,new float[]{0.15000000f*sn,-0.15000000f*(cs-1f),0f});
            }
            case "legacy_meshes/locomotive_steam_c11.tcm" -> {
                offsets.put(300,new float[]{-0.15625000f*sn,0.15625000f*(cs-1f),0f});
                offsets.put(331,new float[]{-0.15625000f*sn,0.15625000f*(cs-1f),0f});
            }
            case "legacy_meshes/locomotive_steam_c41.tcm" -> {
                offsets.put(39,new float[]{-0.12500000f*sn,0.12500000f*(cs-1f),0f});
                offsets.put(47,new float[]{0.12500000f*sn,-0.12500000f*(cs-1f),0f});
            }
            case "legacy_meshes/locomotive_steam_c41_080.tcm" -> {
                offsets.put(35,new float[]{-0.12500000f*sn,0.12500000f*(cs-1f),0f});
                offsets.put(43,new float[]{0.12500000f*sn,-0.12500000f*(cs-1f),0f});
            }
            case "legacy_meshes/locomotive_steam_c41t.tcm" -> {
                offsets.put(34,new float[]{-0.12500000f*sn,0.12500000f*(cs-1f),0f});
                offsets.put(42,new float[]{0.12500000f*sn,-0.12500000f*(cs-1f),0f});
            }
            default -> {}
        }
    }

    // DEV54H12I: a small, exact-model opt-in finishing layer. H12H remains
    // byte-identical in behavior when OFF. The original TCMs never change.
    private static final boolean DEV54H12I_ENABLED=dev54h12iMarker();
    private static boolean dev54h12iMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV54H12I-TWO-STEAM-CONNECTIONS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                  .replace("\uFEFF","").trim().equals("DEV54H12I-TWO-STEAM-ON-V1");
        }catch(Exception e){return false;}
    }
    private static final java.util.Set<String> DEV54H12I_REPORTED=
          java.util.concurrent.ConcurrentHashMap.newKeySet();

    // 4-4-0: 64/66 are the angled, wheel-pinned ORIGINAL main rods.
    // Original straight longitudinal parts 82 (+X) / 80 (-X) can serve as
    // reversible cylinder sliders. Rebase their visible outer end to the
    // solved main-rod guide endpoint, then translate only on the cylinder axis.
    // The fixed mirrored cylinder shells 8/81 never move or hide.
    private static boolean dev54h12i440(String mesh,float[] v,int stride,
              java.util.Map<Integer,Dev44R13uPose> poses,
              java.util.Map<Integer,float[]> offsets){
        if(!dev54h8bChunkCenterMatches(v,stride,80,-.28125f,.21875f,-1.0625f,.001f) ||
           !dev54h8bChunkCenterMatches(v,stride,82,.28125f,.21875f,-1.0625f,.001f) ||
           !dev54h8bChunkCenterMatches(v,stride,8,.4375f,.5f,-1.03125f,.001f) ||
           !dev54h8bChunkCenterMatches(v,stride,81,-.4375f,.5f,-1.03125f,.001f) ||
           !dev54h8bChunkCenterMatches(v,stride,64,.40625f,.40169388f,-.55071118f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,66,-.40625f,.4359136f,-.56094539f,.002f))return false;
        float[] aP={.40625f,.30836f,-.25084f}, bP={.40625f,.49503f,-.85058f};
        float[] aN={-.40625f,.43433f,-.24689f},bN={-.40625f,.43750f,-.87500f};
        var p=dev54h12hSolve(mesh,v,stride,25,0,aP,bP,aP,bP[1],.42f);
        var n=dev54h12hSolve(mesh,v,stride,20,0,aN,bN,aN,bN[1],.42f);
        if(p==null||n==null)return false;
        // Source slider upper cap is z=-0.625; align to the *moving* angled
        // rod back cap in every phase, with no arbitrary extra orbit.
        float[] dp={.40625f-.28125f,p.movingB()[1]-.21875f,p.movingB()[2]+.625f};
        float[] dn={-.40625f+.28125f,n.movingB()[1]-.21875f,n.movingB()[2]+.625f};
        for(float[] d:new float[][]{dp,dn})for(float z:d)if(!Float.isFinite(z))return false;
        // The two sliders must remain on their own cylinder X and Y guides;
        // only Z changes with rotation. Limit shaft stroke to guard bad pivots.
        if(Math.abs(dp[2])>1.1f||Math.abs(dn[2])>1.1f)return false;
        poses.put(64,new Dev44R13uPose(p.pose(),p.originalA()));
        poses.put(66,new Dev44R13uPose(n.pose(),n.originalA()));
        offsets.put(82,dp);offsets.put(80,dn);
        return true;
    }
    // ALCO SC4: H12H already has the actual wheel-pin and sliding-X solution.
    // Its original rod bars were authored 1/16 block farther outward in Z
    // than the source crank collars/crosshead. No offset/stretch can close
    // that depth gap. Apply the exact side-specific render-only lateral rebase
    // to the four source rod-bar chunks AFTER their rigid pose is solved.
    private static boolean dev54h12iAlco(String mesh,float[] v,int stride,
              java.util.Map<Integer,Dev44R13uPose> poses,
              java.util.Map<Integer,float[]> offsets){
        if(!dev54h8bChunkCenterMatches(v,stride,36,.59375f,-.40625f,-.40625f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,42,.34375f,-.40625f,.40625f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,34,.46875f,-.31875f,-.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,35,.46875f,-.50625f,-.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,37,.46875f,-.50625f,.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,38,.46875f,-.31875f,.46875f,.002f))return false;
        return dev54h12hAlco(mesh,v,stride,poses,offsets);
    }

    // DEV54H12H: source-authored wheel-end anchors and guide-constrained
    // crosshead placement for ONLY 4-4-0 and ALCO SC4. Default OFF.
    // H12G's rod motion is replaced only while this exact selector is ON.
    private static final boolean DEV54H12H_ENABLED=dev54h12hMarker();
    private static boolean dev54h12hMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                     .resolve("DEV54H12H-TWO-STEAM-JOINTS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF","").trim().equals("DEV54H12H-TWO-STEAM-ON-V1");
        } catch(Exception e){return false;}
    }
    private static final java.util.Set<String> DEV54H12H_REPORTED=
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Dev54H12HFit(Dev44RigidLinkage.Pose pose,float[] originalA,
                                    float[] movingA,float[] movingB) {}
    // A and B are actual source-mesh rod cap centers, not arbitrary points
    // along an oversized block. The wheel endpoint follows a real rotating
    // side face, B follows a straight cylinder guide, and the rod NEVER scales.
    private static Dev54H12HFit dev54h12hSolve(
            String mesh,float[] verts,int stride,int wheelId,int axleAxis,
            float[] originalA,float[] originalB,float[] authoredWheelPin,
            float guideY,float maxRebase) {
        var wh=traincraft.client.renderer.animation.BakedWheelAnimationContext
                 .spec(mesh,wheelId,verts,stride);
        if(wh==null || !wh.rotates() || wh.axis()!=axleAxis ||
           !Float.isFinite(wh.cos()) || !Float.isFinite(wh.sin())) return null;
        float[] center={wh.centerX(),wh.centerY(),wh.centerZ()};
        float[] da=Dev44RigidLinkage.wheelPoint(authoredWheelPin,center,
                                                wh.cos(),wh.sin(),axleAxis);
        float[] db=originalB.clone();
        db[1]=guideY;
        if(Math.abs(originalB[axleAxis]-originalA[axleAxis])>0.0005f ||
            Math.abs(da[axleAxis]-originalA[axleAxis])>0.10f) return null;
        int travel=axleAxis==0?2:0;
        double length=Math.hypot(originalB[travel]-originalA[travel],
                                 originalB[1]-originalA[1]);
        double rise=guideY-da[1];
        double remain=length*length-rise*rise;
        if(remain<=0.0001) return null;
        // Both original cylinder assemblies extend toward negative travel.
        db[travel]=(float)(da[travel]-Math.sqrt(remain));
        if(!Float.isFinite(db[travel]) ||
            Math.abs(db[travel]-originalB[travel])>maxRebase) return null;
        var pose=Dev44RigidLinkage.solve(originalA,originalB,da,db,axleAxis,0.004f);
        return pose==null?null:new Dev54H12HFit(pose,originalA,da,db);
    }
    private static boolean dev54h12h440(String mesh,float[] v,int stride,
                                  java.util.Map<Integer,Dev44R13uPose> poses){
        // Exact source bar endcaps: chunk64 back Y=.49503 vs wheel Y=.30836;
        // chunk66 back Y=.43750 vs wheel Y=.43433. H12G's invented .40625
        // wheel anchor displaced chunk64's joint by nearly one-sixteenth block.
        if(!dev54h8bChunkCenterMatches(v,stride,64,.40625f,.40169388f,-.55071118f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,66,-.40625f,.4359136f,-.56094539f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,81,-.4375f,.5f,-1.03125f,.001f))return false;
        float[] a64={.40625f,.30836f,-.25084f},b64={.40625f,.49503f,-.85058f};
        float[] a66={-.40625f,.43433f,-.24689f},b66={-.40625f,.43750f,-.87500f};
        var p=dev54h12hSolve(mesh,v,stride,25,0,a64,b64,a64,b64[1],.42f);
        var n=dev54h12hSolve(mesh,v,stride,20,0,a66,b66,a66,b66[1],.42f);
        if(p==null || n==null)return false;
        poses.put(64,new Dev44R13uPose(p.pose(),p.originalA()));
        poses.put(66,new Dev44R13uPose(n.pose(),n.originalA()));
        return true;
    }
    private static boolean dev54h12hAlco(String mesh,float[] v,int stride,
                                java.util.Map<Integer,Dev44R13uPose> poses,
                                java.util.Map<Integer,float[]> offsets){
        // Original ALCO bars 34/35 and 37/38 span X=.24969..68781.
        // Side-specific authored pins are NOT at the same X: chunk36=.59375
        // on -Z, chunk42=.34375 on +Z. The former H12G pinned each bar
        // at a fictitious joint, producing off-axis and detached motion.
        if(!dev54h8bChunkCenterMatches(v,stride,34,.46875f,-.31875f,-.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,35,.46875f,-.50625f,-.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,37,.46875f,-.50625f,.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,38,.46875f,-.31875f,.46875f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,36,.59375f,-.40625f,-.40625f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,42,.34375f,-.40625f,.40625f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,32,.40625f,-.40625f,-.40625f,.002f) ||
           !dev54h8bChunkCenterMatches(v,stride,41,.125f,-.40625f,.40625f,.002f))return false;
        float[] an={.6878125f,-.4125f,-.46875f},bn={.2496875f,-.4125f,-.46875f};
        float[] ap={.6878125f,-.4125f,.46875f}, bp={.2496875f,-.4125f,.46875f};
        float[] pinN={.59375f,-.40625f,-.46875f};
        float[] pinP={.34375f,-.40625f,.46875f};
        var neg=dev54h12hSolve(mesh,v,stride,206,2,an,bn,pinN,-.40625f,.64f);
        var pos=dev54h12hSolve(mesh,v,stride,207,2,ap,bp,pinP,-.40625f,.64f);
        if(neg==null || pos==null)return false;
        // All four original rod halves are two rigid bilaterally grouped bars.
        for(int ch:new int[]{34,35})poses.put(ch,new Dev44R13uPose(neg.pose(),neg.originalA()));
        for(int ch:new int[]{37,38})poses.put(ch,new Dev44R13uPose(pos.pose(),pos.originalA()));
        // Cylinder-side pieces 32 and 41 slide on X. Unlike H12G, each is
        // aligned to the actual rod end, rather than translated by an arbitrary
        // excursion relative to different guessed resting joints.
        offsets.put(32,new float[]{neg.movingB()[0]-.2496875f,0f,0f});
        offsets.put(41,new float[]{pos.movingB()[0]-.3128125f,0f,0f});
        // Restore the two original crankpin collars and let them orbit WITH
        // their respective wheel-side endpoint, not with the piston slider.
        offsets.put(36,new float[]{neg.movingA()[0]-pinN[0],
                                   neg.movingA()[1]-pinN[1],0f});
        offsets.put(42,new float[]{pos.movingA()[0]-pinP[0],
                                   pos.movingA()[1]-pinP[1],0f});
        return true;
    }

    // DEV54H12G: final Batch 1 model-only mirror, original crank linkage and
    // user's precisely selected Black 5 chunk. Default OFF and fail closed.
    private static final boolean DEV54H12G_ENABLED=dev54h12gMarker();
    private static boolean dev54h12gMarker(){
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                 .resolve("DEV54H12G-BATCH1-FINISH-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
              && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equals("DEV54H12G-BATCH1-ON-V1");
        }catch(Exception e){return false;}
    }
    private static final java.util.Set<String> DEV54H12G_REPORTED=
          java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Dev54H12GPair(Dev44RigidLinkage.Pose pose,float[] a,float stroke) {}
    // A source-authored rod has its wheel end on a circular crank and the
    // cylinder end on a linear guide. Keep original rigid bar length exactly.
    private static Dev54H12GPair dev54h12gPinned(
       String meshKey,float[] verts,int stride,int wheelChunk,int axis,
       float[] a,float[] b, boolean slideX, float maxStroke){
        var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext
                      .spec(meshKey,wheelChunk,verts,stride);
        if(wheel==null||!wheel.rotates()||wheel.axis()!=axis
                ||!Float.isFinite(wheel.cos())||!Float.isFinite(wheel.sin()))return null;
        float[] center={wheel.centerX(),wheel.centerY(),wheel.centerZ()};
        float[] da=Dev44RigidLinkage.wheelPoint(a,center,wheel.cos(),wheel.sin(),axis);
        float[] db=b.clone();
        double len,dy,sq;
        if(slideX){
            len=Math.hypot(a[0]-b[0],a[1]-b[1]);
            dy=b[1]-da[1];sq=len*len-dy*dy;
            if(!(sq>0))return null;
            db[0]=(float)(da[0]-Math.sqrt(sq));
        }else{
            len=Math.hypot(a[1]-b[1],a[2]-b[2]);
            dy=b[1]-da[1];sq=len*len-dy*dy;
            if(!(sq>0))return null;
            db[2]=(float)(da[2]-Math.sqrt(sq));
        }
        float stroke=slideX?db[0]-b[0]:db[2]-b[2];
        if(!Float.isFinite(stroke)||Math.abs(stroke)>maxStroke)return null;
        var pose=Dev44RigidLinkage.solve(a,b,da,db,axis,0.004f);
        if(pose==null)return null;
        return new Dev54H12GPair(pose,a,stroke);
    }
    private static boolean dev54h12g440(String mesh,float[] v,int stride,
             java.util.Map<Integer,Dev44R13uPose> poses){
        // Chunk 8/81: physically mirrored cylinder shells. Chunk 81 was
        // mistakenly classified ROD and deferred. Both shells stay fixed.
        if(!dev54h8bChunkCenterMatches(v,stride,8,0.4375f,0.5f,-1.03125f,0.001f)
          ||!dev54h8bChunkCenterMatches(v,stride,81,-0.4375f,0.5f,-1.03125f,0.001f)
          ||!dev54h8bChunkCenterMatches(v,stride,64,0.40625f,0.40169388f,-0.55071118f,0.002f)
          ||!dev54h8bChunkCenterMatches(v,stride,66,-0.40625f,0.4359136f,-0.56094539f,0.002f))return false;
        float[] pa={0.40625f,0.40625f,-0.25f},pb={0.40625f,0.50f,-0.85f};
        float[] na={-0.40625f,0.40625f,-0.25f},nb={-0.40625f,0.50f,-0.85f};
        var pos=dev54h12gPinned(mesh,v,stride,25,0,pa,pb,false,0.43f);
        var neg=dev54h12gPinned(mesh,v,stride,20,0,na,nb,false,0.43f);
        if(pos==null||neg==null)return false;
        poses.put(64,new Dev44R13uPose(pos.pose(),pos.a()));
        poses.put(66,new Dev44R13uPose(neg.pose(),neg.a()));
        return true;
    }
    private static boolean dev54h12gAlco(String mesh,float[] v,int stride,
             java.util.Map<Integer,Dev44R13uPose> poses,
             java.util.Map<Integer,float[]> offsets){
        // Chunk groups 273-284 are the original *fixed* front cylinder shells.
        // Chunks 34/35,37/38 become constrained rods; 32/41 slide through
        // their respective guides. Never orbit the piston/cylinder shells.
        if(!dev54h8bChunkCenterMatches(v,stride,32,0.40625f,-0.40625f,-0.40625f,0.002f)
          ||!dev54h8bChunkCenterMatches(v,stride,41,0.125f,-0.40625f,0.40625f,0.002f)
          ||!dev54h8bChunkCenterMatches(v,stride,34,0.46875f,-0.31875f,-0.46875f,0.002f)
          ||!dev54h8bChunkCenterMatches(v,stride,37,0.46875f,-0.50625f,0.46875f,0.002f))return false;
        float[] aN={0.59375f,-0.40625f,-0.46875f},bN={0.25f,-0.40625f,-0.46875f};
        float[] aP={0.34375f,-0.40625f,0.46875f},bP={0.0f,-0.40625f,0.46875f};
        var neg=dev54h12gPinned(mesh,v,stride,206,2,aN,bN,true,0.28f);
        var pos=dev54h12gPinned(mesh,v,stride,207,2,aP,bP,true,0.28f);
        if(neg==null||pos==null)return false;
        for(int ch:new int[]{34,35})poses.put(ch,new Dev44R13uPose(neg.pose(),neg.a()));
        for(int ch:new int[]{37,38})poses.put(ch,new Dev44R13uPose(pos.pose(),pos.a()));
        offsets.put(32,new float[]{neg.stroke(),0f,0f});
        offsets.put(41,new float[]{pos.stroke(),0f,0f});
        return true;
    }

    // DEV54H12F: Black 5 main rods/crossheads. Additive to visually improved
    // H12E. The original pistons/cylinder shells remain stationary.
    // All link joints below are SOURCE MESH CANDIDATES; runtime acceptance required.
    private static final boolean DEV54H12F_ENABLED=dev54h12fMarker();
    private static boolean dev54h12fMarker() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                   .resolve("DEV54H12F-BLACK5-MAIN-SLIDER-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
               && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF", "").trim().equals("DEV54H12F-BLACK5-MAIN-ON-V1");
        } catch(Exception e) { return false; }
    }
    private static final java.util.Set<String> DEV54H12F_REPORTED =
         java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Dev54H12FSlide(Dev44RigidLinkage.Pose pose, float[] a,
                                   float stroke, float length, float residual) {}
    private static Dev54H12FSlide dev54h12fSolve(
            String meshKey, float[] vertices, int stride, boolean positiveZ) {
        final int wheelId=positiveZ?8:3;
        // Each original bar uses its own source-authored crank cap and the
        // same fixed crosshead guide X/Y. The fixed cylinder shells are not rods.
        final int originalBar=positiveZ?248:215;
        final int originalSlider=positiveZ?240:207;
        final float rodX=-0.116f;
        final float rodY=positiveZ?-0.056f:0.122f;
        final float rodZ=positiveZ?0.481f:-0.475f;
        if(!dev54h8bChunkCenterMatches(vertices,stride,originalBar,
                   rodX,rodY,rodZ,0.0045f))return null;
        if(!dev54h8bChunkCenterMatches(vertices,stride,originalSlider,
                   -0.8f,-0.021875f,positiveZ?0.50625f:-0.49375f,0.0045f))return null;
        var wh=traincraft.client.renderer.animation.BakedWheelAnimationContext
                 .spec(meshKey,wheelId,vertices,stride);
        if(wh==null || !wh.rotates() || wh.axis()!=2 ||
                Math.abs(wh.centerX()-0.53125f)>0.002f ||
                Math.abs(wh.centerY()-0.09375f)>0.002f ||
                Math.abs(wh.centerZ()-(positiveZ?0.375f:-0.375f))>0.002f ||
                !Float.isFinite(wh.cos()) || !Float.isFinite(wh.sin()))return null;
        final float[] a=positiveZ?new float[]{0.521875f,-0.090625f,0.48125f}:
                                  new float[]{0.553125f,0.228125f,-0.475f};
        final float[] b={-0.8f,-0.021875f,positiveZ?0.50625f:-0.49375f};
        final float[] center={wh.centerX(),wh.centerY(),wh.centerZ()};
        final float[] da=Dev44RigidLinkage.wheelPoint(a,center,wh.cos(),wh.sin(),2);
        double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
        double dy=b[1]-da[1],square=length*length-dy*dy;
        if(!Double.isFinite(square)||square<=0.0)return null;
        float targetX=(float)(da[0]-Math.sqrt(square));
        float stroke=targetX-b[0];
        if(!Float.isFinite(stroke) || Math.abs(stroke)>0.225f)return null;
        float[] db={targetX,b[1],b[2]};
        var pose=Dev44RigidLinkage.solve(a,b,da,db,2,0.0025f);
        if(pose==null)return null;
        float[] expected=pose.point(b[0]-a[0],b[1]-a[1],b[2]-a[2]);
        float residual=(float)Math.sqrt((expected[0]-db[0])*(expected[0]-db[0])+
                               (expected[1]-db[1])*(expected[1]-db[1])+
                               (expected[2]-db[2])*(expected[2]-db[2]));
        if(!Float.isFinite(residual)||residual>0.003f)return null;
        return new Dev54H12FSlide(pose,a,stroke,(float)length,residual);
    }
    private static void dev54h12fApply(Dev54H12FSlide neg,Dev54H12FSlide pos,
            java.util.Map<Integer,Dev44R13uPose> poses,
            java.util.Map<Integer,float[]> offsets) {
        // Rigid main rods; exact source chunks, no vertices stretched.
        for(int ch:new int[]{215,222,223,224,225,226})
            poses.put(ch,new Dev44R13uPose(neg.pose(),neg.a()));
        for(int ch:new int[]{248,255,256,257,258,259})
            if(!(DEV54H12G_ENABLED && ch==258))
                poses.put(ch,new Dev44R13uPose(pos.pose(),pos.a()));
        // Slider/crosshead blocks trace the constrained guide at Y=-0.021875.
        // Deliberately do not move fixed guide rails (204-206/237-239)
        // or the original cylinder shell (15-18, 73-78, 138-140).
        for(int ch:new int[]{207,208,209,210,216,217,218,219,220})
            offsets.put(ch,new float[]{neg.stroke(),0f,0f});
        for(int ch:new int[]{240,241,242,243,249,250,251,252,253})
            offsets.put(ch,new float[]{pos.stroke(),0f,0f});
    }
    private static boolean dev54h12fUnmask(int ch) {
        return switch(ch) {
            // Original fixed crosshead guide rails, now visible unchanged.
            case 204,205,206,237,238,239,
                 // Bilateral main rods and their static-to-moving crossheads.
                 215,216,217,218,219,220,222,223,224,225,226,
                 248,249,250,251,252,253,255,256,257,258,259 -> true;
            default -> false;
        };
    }

    // DEV54H11A: 2-6-2T / Alice 0-4-0 only. OFF unless selected by an
    // explicit default-OFF marker. Two independently verified sides share
    // the accepted native wheel phase, and only rigid coupling bars move.
    // No original model file, other stock, fixed cylinder or legacy wheel changes.
    private static final java.util.Set<Integer> DEV54H11A_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h11aSelected(String path) {
        String target;
        if(path.equals("legacy_meshes/locomotive_steam_262t.tcm"))
            target="locomotive_steam_262t";
        else if(path.equals("legacy_meshes/locomotive_steam_alice_0_4_0.tcm"))
            target="locomotive_steam_alice_0_4_0";
        else return false;
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H11A-262T-ALICE-COUPLING-ON.txt");
            if(!java.nio.file.Files.isRegularFile(p))return false;
            for(String row:java.nio.file.Files.readString(p,
                      java.nio.charset.StandardCharsets.UTF_8)
                      .replace("\uFEFF","").split("\\R"))
                if(row.trim().equals(target))return true;
        } catch(Exception ignored) {}
        return false;
    }
    private static boolean dev54h11aDeferredLink(String path,int ch) {
        // Source geometry pilot. Defer the separate oblique main rods and
        // crossheads to H11B rather than double-transform them as coupling.
        if(path.equals("legacy_meshes/locomotive_steam_262t.tcm"))
            return ch==30 || ch==32;
        if(path.equals("legacy_meshes/locomotive_steam_alice_0_4_0.tcm"))
            return (ch>=133 && ch<=138) || ch==150; // H11A1: Alice-only one-sided stray bar
        return false;
    }
    private static float[] dev54h11aShift(String key,float[] verts,int stride,
                                         boolean is262,boolean positiveZ) {
        final float eps=0.003f;
        final int rod=is262?(positiveZ?29:31):(positiveZ?139:132);
        final float rodX=is262?-1.5625f:-1.34375f;
        final float rodY=is262?(positiveZ?0.53125f:0.34375f):
                                (positiveZ?0.34375f:0.46875f);
        final float rodZ=is262?(positiveZ?0.5f:-0.5f):
                                (positiveZ?0.4375f:-0.4375f);
        if(!dev54h8bChunkCenterMatches(verts,stride,rod,
                        rodX,rodY,rodZ,0.001f))return null;
        final int[] wheels=is262?(positiveZ?new int[]{5,4,3}:
                                                    new int[]{0,1,2}):
                                 (positiveZ?new int[]{0,1}:
                                                    new int[]{3,2});
        final float[] axles=is262?new float[]{-2.28125f,-1.65625f,-1.03125f}:
                                       new float[]{-1.78125f,-0.90625f};
        final float cy=0.40625f;
        final float cz=is262?(positiveZ?0.375f:-0.375f):
                             (positiveZ?0.375625f:-0.375625f);
        float sin=0f,cos=1f;
        boolean first=true;
        for(int i=0;i<wheels.length;i++) {
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                          .spec(key,wheels[i],verts,stride);
            if(w==null || !w.rotates() || w.axis()!=2 ||
                    Math.abs(w.centerX()-axles[i])>eps ||
                    Math.abs(w.centerY()-cy)>eps ||
                    Math.abs(w.centerZ()-cz)>eps ||
                    !Float.isFinite(w.cos()) || !Float.isFinite(w.sin()))return null;
            if(first){cos=w.cos();sin=w.sin();first=false;}
            else if(Math.abs(w.cos()-cos)>eps || Math.abs(w.sin()-sin)>eps)
                return null; // no phase-locked three/two-driver unit: HOLD
        }
        // Authored coupling-bar eccentric rest height; the same translated
        // point stays pinned on every equal-phase axle, without flex/stretch.
        final float[] a={axles[0],rodY,cz};
        final float[] c={axles[0],cy,cz};
        final float[] p=Dev44RigidLinkage.wheelPoint(a,c,cos,sin,2);
        float dx=p[0]-a[0],dy=p[1]-a[1];
        if(!Float.isFinite(dx)||!Float.isFinite(dy)||
               Math.hypot(dx,dy)>.255f)return null;
        int quadrant=Math.max(0,Math.min(3,(int)Math.floor(
               (Math.atan2(sin,cos)+Math.PI)/(Math.PI*.5))));
        int report=(is262?0:20)+(positiveZ?0:10)+quadrant;
        if(DEV54H11A_REPORT.add(report))LOGGER.info(
            "[DEV54H11A-COUPLING] model={} side={} axles={} rodChunk={}"
           +" wheelFacesVerified={} phaseQuadrant={} dx={} dy={}"
           +" equalPhaseRigid=true rodStretch=ZERO pins=SOURCE_CANDIDATES",
            is262?"262T":"ALICE_040",positiveZ?"POS_Z":"NEG_Z",
            wheels.length,rod,wheels.length,quadrant,dx,dy);
        return new float[]{dx,dy,0f};
    }

    private static boolean dev54h10aSelected(String meshPath) {
        final String model = switch(meshPath) {
            case "legacy_meshes/locomotive_steam_a4_mallard.tcm" -> "locomotive_steam_a4_mallard";
            case "legacy_meshes/locomotive_steam_king_class.tcm" -> "locomotive_steam_king_class";
            default -> null;
        };
        if(model==null)return false;
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H10A-MALLARD-KING-COUPLING-ON.txt");
            if(!java.nio.file.Files.isRegularFile(p))return false;
            String s=java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "");
            for(String row:s.split("\\R"))if(row.trim().equals(model))return true;
        } catch(Exception ignored) {}
        return false;
    }
    private static boolean dev54h10aHideOtherOriginal(String meshPath,int chunk) {
        if(meshPath.equals("legacy_meshes/locomotive_steam_a4_mallard.tcm")) {
            return switch(chunk) {
                case 35, 36, 37, 38, 39, 40, 53, 54, 55, 56 -> true;
                case 57, 59, 89, 91, 294, 295, 296, 297, 298, 299 -> true;
                case 301, 302, 303, 304, 305, 306, 307, 308, 309, 310 -> true;
                case 311, 312, 313, 314, 315, 316, 317, 318, 319, 334 -> true;
                case 335, 342, 343, 344, 345, 346, 347, 348, 349, 350 -> true;
                case 351, 352, 353, 354, 355, 356, 357, 358, 359, 360 -> true;
                case 361, 362, 363, 364, 365, 366 -> true;
                default -> false;
            };
        }
        if(meshPath.equals("legacy_meshes/locomotive_steam_king_class.tcm")) {
            return switch(chunk) {
                case 478, 479, 480, 482, 484, 485, 488, 489, 490, 491 -> true;
                case 492, 493, 494, 495, 556, 557, 558, 559, 560, 561 -> true;
                case 562, 563, 564, 565, 566, 567, 568, 569, 570, 571 -> true;
                case 572, 573, 574, 575, 576, 577, 578, 579, 580, 581 -> true;
                case 582, 583, 584, 585, 586, 587, 588, 589, 590, 591 -> true;
                case 592, 593, 594, 595, 596, 597, 598, 599, 600, 601 -> true;
                case 602, 603, 604, 605, 606, 607, 608, 609, 610, 611 -> true;
                case 612, 613, 614, 615, 616, 617, 618, 619, 620, 621 -> true;
                case 622, 623, 624, 625, 626, 627, 628, 629, 630, 631 -> true;
                case 632, 633, 634, 635, 636, 637, 638, 639, 640, 641 -> true;
                case 642, 662, 663, 667, 669, 674, 675, 676, 677, 678 -> true;
                case 679, 680, 681, 682, 683, 684, 685, 688, 724, 725 -> true;
                case 726, 727 -> true;
                default -> false;
            };
        }
        return false;
    }
    private static boolean dev54h10aCouplingChunk(String meshPath,int chunk) {
        if(meshPath.equals("legacy_meshes/locomotive_steam_a4_mallard.tcm"))
            return switch(chunk) {case 84,85,86,87,88,90,336,337,338,339,340,341 -> true;default -> false;};
        if(meshPath.equals("legacy_meshes/locomotive_steam_king_class.tcm"))
            return chunk>=498 && chunk<=555;
        return false;
    }
    private static float[] dev54h10aCouplingShift(String key,float[] verts,int stride,
                                                       boolean mallard,boolean posZ) {
        // Authored 3 axle centers from frozen .tcm, not copied Berkshire locations.
        final int[] wheels=mallard?(posZ?new int[]{0,1,2}:new int[]{7,8,9}):
                              (posZ?new int[]{123,119,120}:new int[]{122,118,121});
        final float[] axleX=mallard?new float[]{-0.78125f,0.46875f,1.71875f}:
                                     new float[]{-1.59375f,-0.34375f,0.90625f};
        final float axleY=mallard?0.15625f:0.125f;
        final float pinY=mallard?(posZ?0.0f:0.3125f):0.3359375f;
        final float wheelZ=mallard?(posZ?0.37578f:-0.37547f):(posZ?0.375f:-0.375f);
        final float pinZ=mallard?(posZ?0.40625f:-0.40625f):(posZ?0.40625f:-0.40625f);
        final float eps=0.0010f;
        float[] translation=null;
        for(int j=0;j<3;j++) {
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                    key,wheels[j],verts,stride);
            if(w==null||!w.rotates()||w.axis()!=2||
                    Math.abs(w.centerX()-axleX[j])>eps||
                    Math.abs(w.centerY()-axleY)>eps||
                    Math.abs(w.centerZ()-wheelZ)>eps||
                    !Float.isFinite(w.cos())||!Float.isFinite(w.sin()))return null;
            // Crankpin candidates aligned with the original coupling beam Y.
            float[] rest={axleX[j],pinY,pinZ};
            float[] moved=Dev44RigidLinkage.wheelPoint(rest,
                    new float[]{w.centerX(),w.centerY(),w.centerZ()},w.cos(),w.sin(),2);
            float[] t={moved[0]-rest[0],moved[1]-rest[1],moved[2]-rest[2]};
            if(!Float.isFinite(t[0])||!Float.isFinite(t[1])||Math.abs(t[2])>eps)return null;
            if(translation==null)translation=t;
            else if(Math.abs(t[0]-translation[0])>eps||Math.abs(t[1]-translation[1])>eps)return null;
        }
        return translation;
    }


    // DEV54H10B: individually source-identified Mallard/King connecting rod and
    // crosshead/piston-guide sliders, extending the visually approved H10A.
    // OFF by default. All joins are original mesh geometry CANDIDATES, not
    // verified engineering hardpoints. No TCM, wheel or cylinder mesh writes.
    private static final java.util.Set<Integer> DEV54H10B_REPORT =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h10bSelected(String meshPath) {
        if(!meshPath.equals("legacy_meshes/locomotive_steam_a4_mallard.tcm") &&
           !meshPath.equals("legacy_meshes/locomotive_steam_king_class.tcm"))return false;
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV54H10B-MALLARD-KING-MAIN-SLIDER-ON.txt");
            if(!java.nio.file.Files.isRegularFile(p))return false;
            var s=java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "");
            var model=meshPath.substring("legacy_meshes/".length(),meshPath.length()-4);
            for(String row:s.split("\\R"))if(row.trim().equals(model))return true;
        } catch(Exception ignored) {}
        return false;
    }
    private static boolean dev54h10bUnmask(String path,int ch) {
        if(path.equals("legacy_meshes/locomotive_steam_a4_mallard.tcm"))
            return ch==91 || ch==335 || (ch>=294&&ch<=299)
                    || (ch>=356&&ch<=360) || ch==364;
        if(path.equals("legacy_meshes/locomotive_steam_king_class.tcm"))
            return (ch>=560&&ch<=562) || (ch>=640&&ch<=642)
                    || (ch>=567&&ch<=599) || (ch>=600&&ch<=625)
                    || (ch>=633&&ch<=639);
        return false;
    }
    private record Dev54H10BSlider(Dev44RigidLinkage.Pose pose,float[] restA,
                                   float stroke,double residual,double length) {}
    private static Dev54H10BSlider dev54h10bSolve(String key,float[] verts,int stride,
                                                        boolean mallard,boolean posZ) {
        final float eps=0.0010f;
        final int ch=mallard?(posZ?335:91):(posZ?561:640);
        final int wi=mallard?(posZ?1:8):(posZ?119:118);
        final float rodZ=mallard?(posZ?0.46875f:-0.46875f):
                                  (posZ?0.46875f:-0.46875f);
        // Model-specific static-mesh rod envelope identifies the source chunk.
        final float refX=mallard?-0.53125f:-1.0613718f;
        final float refY=mallard?(posZ?0.078125f:0.234375f):0.2614947f;
        if(!dev54h8bChunkCenterMatches(verts,stride,ch,refX,refY,rodZ,eps))return null;
        // Original wheel centers, crank candidates and fixed guide line.
        final float axleX=mallard?0.46875f:-0.34375f;
        final float axleY=mallard?0.15625f:0.125f;
        final float axleZ=mallard?(posZ?0.37578f:-0.37547f):
                                  (posZ?0.375f:-0.375f);
        final float pinY=mallard?(posZ?0.0f:0.3125f):0.3359375f;
        final float guideY=mallard?0.0625f:0.1875f;
        final float crossX=mallard?-1.5f:-1.75f;
        var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                key,wi,verts,stride);
        if(wheel==null||!wheel.rotates()||wheel.axis()!=2||
                Math.abs(wheel.centerX()-axleX)>eps||
                Math.abs(wheel.centerY()-axleY)>eps||
                Math.abs(wheel.centerZ()-axleZ)>eps||
                !Float.isFinite(wheel.cos())||!Float.isFinite(wheel.sin()))return null;
        final float[] a={axleX,pinY,rodZ};
        final float[] b={crossX,guideY,rodZ};
        final float[] c={wheel.centerX(),wheel.centerY(),wheel.centerZ()};
        final float[] da=Dev44RigidLinkage.wheelPoint(a,c,wheel.cos(),wheel.sin(),2);
        final double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
        final double dy=b[1]-da[1];
        final double discriminant=length*length-dy*dy;
        if(!Double.isFinite(discriminant)||discriminant<=0.0)return null;
        // This locomotive's cylinder/crosshead lies to the -X side of pin.
        final float movedX=(float)(da[0]-Math.sqrt(discriminant));
        final float stroke=movedX-crossX;
        if(!Float.isFinite(stroke)||Math.abs(stroke)>(mallard?0.185f:0.240f))return null;
        var db=new float[]{movedX,guideY,rodZ};
        var p=Dev44RigidLinkage.solve(a,b,da,db,2,eps);
        if(p==null||!Float.isFinite(p.cos)||!Float.isFinite(p.sin))return null;
        var actual=p.point(b[0]-a[0],b[1]-a[1],0f);
        final double residual=Math.hypot(actual[0]-db[0],actual[1]-db[1]);
        if(!Double.isFinite(residual)||residual>eps||Math.abs(actual[2]-rodZ)>eps)return null;
        final float phase=(float)Math.atan2(wheel.sin(),wheel.cos());
        int quadrant=Math.max(0,Math.min(3,(int)Math.floor((phase+Math.PI)/(Math.PI*.5))));
        int keyId=(mallard?0:20)+(posZ?0:10)+quadrant;
        if(DEV54H10B_REPORT.add(keyId))LOGGER.info(
            "[DEV54H10B-SLIDER] model={} side={} mainChunk={} wheelChunk={}"
            +" angleQuadrant={} stroke={} rodLength={} jointResidual={}"
            +" fixedCylinder=true crossheadGuideY={} hardpoints=MODEL_CANDIDATE",
            mallard?"A4_MALLARD":"KING_CLASS",posZ?"POS_Z":"NEG_Z",
            ch,wi,quadrant,stroke,length,residual,guideY);
        return new Dev54H10BSlider(p,a,stroke,residual,length);
    }

    private static boolean dev54h9aOldUnwantedChunk(int ch) {
        // These EXACT 38 IDs are tagged rod/crank by the original DEV44
        // Verified Mesh Parts (4) user export, model SHA8A5588.. exclusively.
        return switch(ch) {
            case 372, 379, 389, 390, 391, 490, 491, 729 -> true;
            case 1007, 1011, 1012, 1013, 1017, 1018, 1019, 1021 -> true;
            case 1022, 1055, 1056, 1057, 1093, 1094, 1095, 1096 -> true;
            case 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104 -> true;
            case 1105, 1106, 1175, 1176, 1177, 1178 -> true;
            default -> false;
        };
    }
    private static Dev44RigidLinkage.Pose dev54h9aSolveCoupling(
            String meshKey,float[] vertices,int stride,boolean positiveZ) {
        final int[] wheels=positiveZ?new int[]{13,12,11,10}:new int[]{14,15,16,17};
        final float[] axleX={-0.5f,0.3125f,1.125f,1.9375f};
        final float rodZ=positiveZ?0.34375f:-0.459375f;
        final float wheelZ=positiveZ?0.313125f:-0.438125f;
        final float wheelY=-0.5625f, pinRestY=-0.46875f;
        // Authored rod center line is exactly 0.09375 above the four axle centers.
        // These are PIN CANDIDATES, NOT independently authenticated metal-pin vertices.
        final float eps=0.0010f;
        float[] firstRest=null,firstMoved=null,lastRest=null,lastMoved=null;
        float baseDx=Float.NaN,baseDy=Float.NaN,phase=Float.NaN;
        for(int j=0;j<wheels.length;j++) {
            var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                    meshKey,wheels[j],vertices,stride);
            if(w==null || !w.rotates() || w.axis()!=2
                    || Math.abs(w.centerX()-axleX[j])>eps
                    || Math.abs(w.centerY()-wheelY)>eps
                    || Math.abs(w.centerZ()-wheelZ)>eps
                    || !Float.isFinite(w.cos()) || !Float.isFinite(w.sin())) return null;
            float[] rest={axleX[j],pinRestY,rodZ};
            float[] center={w.centerX(),w.centerY(),w.centerZ()};
            float[] moved=Dev44RigidLinkage.wheelPoint(rest,center,w.cos(),w.sin(),2);
            float dx=moved[0]-rest[0],dy=moved[1]-rest[1];
            if(!Float.isFinite(dx)||!Float.isFinite(dy)||Math.abs(moved[2]-rodZ)>eps)return null;
            if(j==0){baseDx=dx;baseDy=dy;firstRest=rest;firstMoved=moved;
                phase=(float)Math.atan2(w.sin(),w.cos());}
            else if(Math.abs(dx-baseDx)>eps||Math.abs(dy-baseDy)>eps)return null;
            if(j==wheels.length-1){lastRest=rest;lastMoved=moved;}
        }
        // All four crankpins must translate coherently; two endpoint solve
        // has zero stretch and preserves every original rod and cap vertex.
        var pose=Dev44RigidLinkage.solve(firstRest,lastRest,firstMoved,lastMoved,2,eps);
        if(pose==null || Math.abs(pose.cos-1.0f)>eps || Math.abs(pose.sin)>eps)return null;
        int quadrant=(int)Math.floor((phase+Math.PI)/(Math.PI*0.5));
        quadrant=Math.max(0,Math.min(3,quadrant));
        int k=(positiveZ?0:10)+quadrant;
        if(DEV54H9A_COUPLING_REPORT.add(k))LOGGER.info(
              "[DEV54H9A-COUPLING] side={} quad={} phase={} wheels=4 rodChunks=13 model=765 radius=0.09375 "
              +"pinClosureTol={} rigid=true centerDistance=2.4375 piston=UNTOUCHED pins=CANDIDATE",
              positiveZ?"POS_Z":"NEG_Z",quadrant,phase,eps);
        return pose;
    }
    private static Dev54H8BLink dev54h9aSolveSlider(
            String meshKey,float[] vertices,int stride,boolean positiveZ) {
        final int rod=positiveZ?378:746,wi=positiveZ?12:15;
        final float rodZ=positiveZ?0.40625f:-0.521875f;
        final float crossZ=positiveZ?0.46875f:-0.59375f;
        final float tolerance=0.0010f;
        if(!dev54h8bChunkCenterMatches(vertices,stride,rod,
                1.125f,-0.53125f,rodZ,tolerance))return null;
        final int[] cs=positiveZ?new int[]{373,374,375}:new int[]{730,731,732};
        final float[] cxs={1.953125f,1.9765625f,1.984375f};
        final float[] cys={-0.640625f,-0.5859375f,-0.546875f};
        for(int i=0;i<3;i++) if(!dev54h8bChunkCenterMatches(
                vertices,stride,cs[i],cxs[i],cys[i],crossZ,tolerance))return null;
        var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                meshKey,wi,vertices,stride);
        if(w==null || !w.rotates() || w.axis()!=2 ||
                Math.abs(w.centerX()-0.3125f)>tolerance ||
                Math.abs(w.centerY()+0.5625f)>tolerance ||
                Math.abs(w.centerZ()-(positiveZ?0.31313f:-0.43813f))>tolerance ||
                !Float.isFinite(w.cos()) || !Float.isFinite(w.sin()))return null;
        // Authored MAIN ROD endpoints inferred from its two Y/X endpoint boxes.
        // Candidate crank A rotates about axle #2; crosshead B follows original
        // Y guide, so rod length is exactly conserved (no stretching/skew).
        final float[] a={0.296875f,-0.46875f,rodZ};
        final float[] b={1.953125f,-0.59375f,rodZ};
        final float[] center={w.centerX(),w.centerY(),w.centerZ()};
        float[] da=Dev44RigidLinkage.wheelPoint(a,center,w.cos(),w.sin(),2);
        final double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
        final double dy=b[1]-da[1];
        final double discriminant=length*length-dy*dy;
        if(!Double.isFinite(discriminant)||discriminant<=0)return null;
        // Rightward branch is source-rest-authenticated, no branch jumping.
        final float slideX=(float)(da[0]+Math.sqrt(discriminant));
        final float stroke=slideX-b[0];
        if(!Float.isFinite(stroke)||Math.abs(stroke)>0.21f)return null;
        float[] db={slideX,b[1],rodZ};
        var pose=Dev44RigidLinkage.solve(a,b,da,db,2,tolerance);
        if(pose==null || !Float.isFinite(pose.cos) || !Float.isFinite(pose.sin))return null;
        float[] check=pose.point(b[0]-a[0],b[1]-a[1],b[2]-a[2]);
        float residual=(float)Math.hypot(check[0]-db[0],check[1]-db[1]);
        if(!Float.isFinite(residual)||residual>tolerance||Math.abs(check[2]-rodZ)>tolerance)return null;
        float phase=(float)Math.atan2(w.sin(),w.cos());
        int quadrant=Math.max(0,Math.min(3,(int)Math.floor((phase+Math.PI)/(Math.PI*0.5))));
        if(DEV54H9A_SLIDER_REPORT.add((positiveZ?0:10)+quadrant))LOGGER.info(
              "[DEV54H9A-SLIDER] side={} rodChunk={} wheelChunk={} quad={} wheelPhase={} "
              +"crossheadChunks=3 guideY=-0.59375 stroke={} rodLength={} closureError={} "
              +"rigid=true shell=FIXED joint=AUTHORED_GEOMETRY_CANDIDATE",
              positiveZ?"POS_Z":"NEG_Z",rod,wi,quadrant,phase,stroke,length,residual);
        return new Dev54H8BLink(pose,a,stroke,residual);
    }
    // DEV44 final fleet candidate: optional, model-SHA guarded, legacy STEAM ONLY.
    // Accepted Berkshire 765 remains entirely outside this pass.
    private int[] dev44FleetWheelChunks;
    private boolean dev44FleetFirstReport;
    private static boolean dev44FleetEnabled() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-FLEET-LINKAGE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                          .replace("\uFEFF","").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    // DEV44 R10 exact-label hide proof, never touches other Java-model locomotives.
    // Enable only after backing up and creating run/DEV44-R10-HIDE-ON.txt with word ON.
    // DEV44 R12: Disabled by default; reads opt-in marker dynamically without rebuild.
    // R12a: resolve BOTH project-root and Forge gameDir paths. Reject ambiguous/stale markers.
    // The supplied enable/disable CMDs write to projectRoot/run/.
    // R12b: use the actual Forge game directory, never an ambiguous cwd candidate.
    private static java.nio.file.Path dev44R12MarkerPath() {
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV44-R12-HIDE-ON.txt").toAbsolutePath().normalize();
    }
    private static boolean dev44R12HideEnabled() {
        try {
            java.nio.file.Path marker = dev44R12MarkerPath();
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R12b] Cannot read hide marker at {}", dev44R12MarkerPath(), ex);
            return false;
        }
    }
    private static boolean dev44R10ReadOptIn() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("DEV44-R10-HIDE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private static java.util.BitSet dev44R10Bits(int[] chunks) {
        java.util.BitSet b = new java.util.BitSet();
        for (int id : chunks) b.set(id);
        return b;
    }
    private static final java.util.BitSet DEV44_R10_IDS_0 = dev44R10Bits(new int[]{17, 19, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
    private static final java.util.BitSet DEV44_R10_IDS_1 = dev44R10Bits(new int[]{8, 62, 63, 64, 65, 66, 67, 81});
    private static final java.util.BitSet DEV44_R10_IDS_2 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 53, 54, 55, 56, 57, 59, 84, 85, 86, 87, 88, 89, 90, 91, 294, 295, 296, 297, 298, 299, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366});
    private static final java.util.BitSet DEV44_R10_IDS_3 = dev44R10Bits(new int[]{68, 71, 145, 146, 252, 253});
    private static final java.util.BitSet DEV44_R10_IDS_4 = dev44R10Bits(new int[]{31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 45, 46, 47, 273, 274, 275, 276, 277, 278, 279, 280, 281, 283, 284, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 614, 615, 616, 617, 618, 619, 620, 621, 622, 623});
    private static final java.util.BitSet DEV44_R10_IDS_5 = dev44R10Bits(new int[]{128, 129, 130, 131, 132, 134, 135, 136, 137, 138, 139, 150});
    private static final java.util.BitSet DEV44_R10_IDS_6 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 372, 373, 374, 375, 378, 379, 389, 390, 391, 490, 491, 492, 730, 731, 732, 733, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 747, 1008, 1012, 1013, 1014, 1018, 1019, 1020, 1022, 1023, 1056, 1057, 1061, 1062, 1063, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1166, 1167, 1168});
    private static final java.util.BitSet DEV44_R10_IDS_7 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 372, 373, 374, 375, 378, 379, 389, 390, 391, 490, 491, 729, 730, 731, 732, 733, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 1007, 1011, 1012, 1013, 1017, 1018, 1019, 1021, 1022, 1055, 1056, 1057, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1175, 1176, 1177, 1178});
    private static final java.util.BitSet DEV44_R10_IDS_8 = dev44R10Bits(new int[]{7, 28, 50, 51, 52, 53, 54, 55});
    private static final java.util.BitSet DEV44_R10_IDS_9 = dev44R10Bits(new int[]{15, 16, 17, 18, 73, 74, 76, 77, 78, 138, 139, 140, 204, 205, 206, 211, 212, 213, 214, 215, 217, 218, 219, 220, 222, 223, 224, 225, 226, 237, 238, 239, 244, 245, 246, 247, 248, 250, 251, 252, 253, 255, 256, 257, 258, 259, 357, 360});
    private static final java.util.BitSet DEV44_R10_IDS_10 = dev44R10Bits(new int[]{18, 22, 23, 24, 25, 26, 27, 31, 32, 33, 34, 35, 206, 207, 208, 209, 211, 212, 213, 214, 215, 216, 217, 232, 233, 234, 235, 236, 237, 240, 241, 246, 247, 248, 249, 250, 251, 252, 287, 340, 341, 342, 343, 344, 345, 346, 350, 351, 352, 353, 354, 355, 356, 357, 359, 360, 361, 362, 363});
    private static final java.util.BitSet DEV44_R10_IDS_11 = dev44R10Bits(new int[]{77, 82, 84, 86, 87, 145, 149, 157, 162, 276, 279, 283, 290, 292, 296, 300, 308, 311, 313, 315, 317, 319, 321, 322, 326, 327, 329, 331, 334, 342, 387, 388});
    private static final java.util.BitSet DEV44_R10_IDS_12 = dev44R10Bits(new int[]{36, 37, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 53, 54, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 438});
    private static final java.util.BitSet DEV44_R10_IDS_13 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 49, 50, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 413});
    private static final java.util.BitSet DEV44_R10_IDS_14 = dev44R10Bits(new int[]{31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 311, 312, 313, 314, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394, 397, 398});
    private static final java.util.BitSet DEV44_R10_IDS_15 = dev44R10Bits(new int[]{20, 21, 22, 45, 46, 47, 59, 172, 175, 178, 179, 184, 185});
    private static final java.util.BitSet DEV44_R10_IDS_16 = dev44R10Bits(new int[]{62, 64, 65, 72, 78, 86, 87, 92, 96, 97, 98, 100, 101, 102, 103});
    private static final java.util.BitSet DEV44_R10_IDS_17 = dev44R10Bits(new int[]{25, 27, 28, 29, 30, 34, 35, 36, 37, 50, 51, 52, 53, 54, 55, 65, 66, 67, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 379, 380, 381, 382, 383, 384});
    private static final java.util.BitSet DEV44_R10_IDS_18 = dev44R10Bits(new int[]{82, 83, 119, 120, 121, 122, 123, 124});
    private static final java.util.BitSet DEV44_R10_IDS_19 = dev44R10Bits(new int[]{39, 48, 49, 50, 52, 73, 74, 75, 77, 78, 79, 80, 81, 82, 83, 84, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109});
    private static final java.util.BitSet DEV44_R10_IDS_20 = dev44R10Bits(new int[]{57, 59, 60, 61, 62, 63, 64, 72, 73, 74, 75, 76, 93, 94, 146, 174, 197});
    private static final java.util.BitSet DEV44_R10_IDS_21 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 22, 187, 194, 278, 279, 280, 285, 287});
    private static final java.util.BitSet DEV44_R10_IDS_22 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 24, 190, 197, 280});
    private static final java.util.BitSet DEV44_R10_IDS_23 = dev44R10Bits(new int[]{390, 391, 392, 419, 420, 421, 422, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 463, 464, 465, 466, 467, 468, 469, 470, 471, 472, 473, 474, 475, 476, 477, 478, 479, 480, 481, 482, 483, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 495, 496, 497, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 593, 594, 595, 596, 597, 598, 671, 672, 673, 674, 675, 676, 677, 678, 679, 681, 897, 898, 899});
    private static final java.util.BitSet DEV44_R10_IDS_24 = dev44R10Bits(new int[]{25, 27, 28, 29, 30, 31, 32, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 612, 614, 616, 617, 618, 619});
    private static final java.util.BitSet DEV44_R10_IDS_25 = dev44R10Bits(new int[]{420, 422, 431, 432, 434, 435, 441, 442, 443, 444, 445, 446, 453, 455, 456, 457, 459, 460, 461, 462, 463, 478, 479, 480, 482, 484, 485, 488, 489, 490, 491, 492, 493, 494, 495, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 593, 594, 595, 596, 597, 598, 599, 600, 601, 602, 603, 604, 605, 606, 607, 608, 609, 610, 611, 612, 613, 614, 615, 616, 617, 618, 619, 620, 621, 622, 623, 624, 625, 626, 627, 628, 629, 630, 631, 632, 633, 634, 635, 636, 637, 638, 639, 640, 641, 642, 662, 663, 667, 669, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 688, 724, 725, 726, 727});
    private static final java.util.BitSet DEV44_R10_IDS_26 = dev44R10Bits(new int[]{43, 44});
    private static final java.util.BitSet DEV44_R10_IDS_27 = dev44R10Bits(new int[]{11, 14, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 67, 76, 77, 82, 83, 84, 85, 86, 87, 88, 93, 94, 117, 118, 119, 120, 121, 122, 123, 124, 125, 194, 209, 210, 212, 213, 216, 217, 218, 222, 277});
    private static final java.util.BitSet DEV44_R10_IDS_28 = dev44R10Bits(new int[]{103, 104, 106, 107, 112, 127, 128, 129, 130, 153, 157, 158, 159, 160, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 196, 197, 198});
    private static final java.util.BitSet DEV44_R10_IDS_29 = dev44R10Bits(new int[]{1, 327, 328, 329, 330, 331, 332, 333, 334, 335});
    private static final java.util.BitSet DEV44_R10_IDS_30 = dev44R10Bits(new int[]{62, 63, 72, 73, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 225, 228, 229, 230, 232, 233, 234, 236, 237, 241, 242, 243, 244, 245, 246, 247, 248, 249});
    private static final java.util.BitSet DEV44_R10_IDS_31 = dev44R10Bits(new int[]{59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 156, 157, 158, 160, 161, 162});
    private static final java.util.BitSet DEV44_R10_IDS_32 = dev44R10Bits(new int[]{113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147});
    private static final java.util.BitSet DEV44_R10_IDS_33 = dev44R10Bits(new int[]{91, 94, 96, 99, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 437, 438, 439, 440, 441, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461});
    private static final java.util.BitSet DEV44_R10_IDS_34 = dev44R10Bits(new int[]{91, 94, 96, 99, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 435, 436, 437, 438, 439, 440, 441, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 509});
    private static final java.util.BitSet DEV44_R10_IDS_35 = dev44R10Bits(new int[]{72, 75, 80, 85, 90, 96, 97, 98, 99});
    private static final java.util.BitSet DEV44_R10_IDS_36 = dev44R10Bits(new int[]{179, 180, 186, 187, 189, 190, 195, 201, 413, 516});
    private static final java.util.BitSet DEV44_R10_IDS_37 = dev44R10Bits(new int[]{53, 66, 67, 68, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144});
    private static final java.util.BitSet DEV44_R10_IDS_38 = dev44R10Bits(new int[]{34, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 52, 53, 236, 238, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294});
    private static final java.util.BitSet DEV44_R10_IDS_39 = dev44R10Bits(new int[]{3, 5, 6, 7, 8, 35, 58, 59});
    private static final java.util.BitSet DEV44_R10_IDS_40 = dev44R10Bits(new int[]{5, 6, 7, 8, 9, 10});
    private static final java.util.BitSet DEV44_R10_IDS_41 = dev44R10Bits(new int[]{18, 19, 20, 24, 26, 30, 32, 33, 34, 35, 46, 67, 68, 72});
    private static final java.util.BitSet DEV44_R10_IDS_42 = dev44R10Bits(new int[]{48, 49, 50, 51, 53});
    private static final java.util.BitSet DEV44_R10_IDS_43 = dev44R10Bits(new int[]{2, 8, 15, 18, 23, 24, 25, 26, 27, 28, 29, 30, 31, 85, 95, 96, 97, 98, 132, 133, 144, 145, 148, 154, 155, 156});
    private static final java.util.BitSet DEV44_R13_ROD_0 = dev44R10Bits(new int[]{29, 30, 31, 32});
    private static final java.util.BitSet DEV44_R13_PISTON_0 = dev44R10Bits(new int[]{17, 19, 27, 28});
    private static final java.util.BitSet DEV44_R13_CRANK_0 = dev44R10Bits(new int[]{21, 22, 23, 24, 25, 26});
    private static final java.util.BitSet DEV44_R13_ROD_1 = dev44R10Bits(new int[]{62, 63, 64, 65, 66, 67, 81});
    private static final java.util.BitSet DEV44_R13_PISTON_1 = dev44R10Bits(new int[]{8});
    private static final java.util.BitSet DEV44_R13_CRANK_1 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_2 = dev44R10Bits(new int[]{38, 39, 40, 56, 57, 59, 87, 88, 89, 91, 295, 296, 301, 303, 304, 305, 306, 307, 308, 311, 312, 313, 315, 317, 334, 335, 338, 341, 342, 343, 344, 347, 348, 349, 350, 351, 352, 354, 355, 356, 357, 358, 359, 360, 361, 363, 364, 366});
    private static final java.util.BitSet DEV44_R13_PISTON_2 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_2 = dev44R10Bits(new int[]{35, 36, 37, 53, 54, 55, 84, 85, 86, 90, 294, 297, 298, 299, 302, 309, 310, 314, 316, 318, 319, 336, 337, 339, 340, 345, 346, 353, 362, 365});
    private static final java.util.BitSet DEV44_R13_ROD_3 = dev44R10Bits(new int[]{68, 71, 145, 146, 252, 253});
    private static final java.util.BitSet DEV44_R13_PISTON_3 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_3 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_4 = dev44R10Bits(new int[]{31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 45, 46, 47, 342, 343, 344, 345, 349, 350, 352, 353, 354, 355, 356, 357, 548, 549, 550, 552, 553, 554, 558, 559, 561, 562, 563, 564, 565, 566, 567, 568, 570, 571, 572, 573, 614, 615, 616, 621, 622});
    private static final java.util.BitSet DEV44_R13_PISTON_4 = dev44R10Bits(new int[]{273, 274, 275, 276, 277, 278, 279, 280, 281, 283, 284});
    private static final java.util.BitSet DEV44_R13_CRANK_4 = dev44R10Bits(new int[]{36, 42, 346, 347, 348, 351, 358, 359, 360, 361, 362, 551, 555, 556, 557, 560, 569, 574, 575, 576, 617, 618, 619, 620, 623});
    private static final java.util.BitSet DEV44_R13_ROD_5 = dev44R10Bits(new int[]{132, 134, 136, 139, 150});
    private static final java.util.BitSet DEV44_R13_PISTON_5 = dev44R10Bits(new int[]{135, 137, 138});
    private static final java.util.BitSet DEV44_R13_CRANK_5 = dev44R10Bits(new int[]{128, 129, 130, 131});
    private static final java.util.BitSet DEV44_R13_ROD_6 = dev44R10Bits(new int[]{348, 372, 378, 391, 490, 491, 492, 730, 734, 747, 1008, 1012, 1013, 1014, 1018, 1019, 1020, 1022, 1023, 1056, 1057, 1061, 1062, 1063});
    private static final java.util.BitSet DEV44_R13_PISTON_6 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_6 = dev44R10Bits(new int[]{349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 373, 374, 375, 379, 389, 390, 731, 732, 733, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 746, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1166, 1167, 1168});
    private static final java.util.BitSet DEV44_R13_ROD_7 = dev44R10Bits(new int[]{348, 372, 378, 391, 490, 491, 729, 733, 746, 1007, 1011, 1012, 1013, 1017, 1018, 1019, 1021, 1022, 1055, 1056, 1057});
    private static final java.util.BitSet DEV44_R13_PISTON_7 = dev44R10Bits(new int[]{7, 9, 140, 141, 142, 143, 144, 145, 146, 147, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_7 = dev44R10Bits(new int[]{349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 373, 374, 375, 379, 389, 390, 730, 731, 732, 734, 735, 736, 737, 738, 739, 740, 741, 742, 743, 744, 745, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1175, 1176, 1177, 1178});
    private static final java.util.BitSet DEV44_R13_ROD_8 = dev44R10Bits(new int[]{50, 51, 52, 53, 54, 55});
    private static final java.util.BitSet DEV44_R13_PISTON_8 = dev44R10Bits(new int[]{7, 28});
    private static final java.util.BitSet DEV44_R13_CRANK_8 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_9 = dev44R10Bits(new int[]{204, 205, 206, 211, 215, 217, 218, 219, 220, 222, 223, 224, 225, 226, 237, 238, 239, 244, 248, 250, 251, 252, 253, 255, 256, 257, 258, 259});
    private static final java.util.BitSet DEV44_R13_PISTON_9 = dev44R10Bits(new int[]{15, 16, 17, 18, 73, 74, 76, 77, 78, 138, 139, 140, 357, 360});
    private static final java.util.BitSet DEV44_R13_CRANK_9 = dev44R10Bits(new int[]{212, 213, 214, 245, 246, 247});
    private static final java.util.BitSet DEV44_R13_ROD_10 = dev44R10Bits(new int[]{206, 207, 208, 209, 211, 212, 214, 216, 217, 233, 236, 237, 240, 241, 246, 247, 249, 250, 251, 287, 341, 344, 345, 346, 350, 351, 353, 354, 355, 356, 359, 362, 363});
    private static final java.util.BitSet DEV44_R13_PISTON_10 = dev44R10Bits(new int[]{18, 22, 23, 24, 25, 26, 27, 31, 32, 33, 34, 35});
    private static final java.util.BitSet DEV44_R13_CRANK_10 = dev44R10Bits(new int[]{213, 215, 232, 234, 235, 248, 252, 340, 342, 343, 352, 357, 360, 361});
    private static final java.util.BitSet DEV44_R13_ROD_11 = dev44R10Bits(new int[]{82, 86, 87, 276, 279, 283, 290, 292, 300, 308, 313, 317, 319, 321, 322, 326, 331, 334, 342, 387, 388});
    private static final java.util.BitSet DEV44_R13_PISTON_11 = dev44R10Bits(new int[]{145, 149, 157, 162});
    private static final java.util.BitSet DEV44_R13_CRANK_11 = dev44R10Bits(new int[]{77, 84, 296, 311, 315, 327, 329});
    private static final java.util.BitSet DEV44_R13_ROD_12 = dev44R10Bits(new int[]{36, 37, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 53, 54, 411, 412, 413, 414, 419, 420, 422, 423, 424, 425, 426, 427, 432, 433, 434, 435, 436});
    private static final java.util.BitSet DEV44_R13_PISTON_12 = dev44R10Bits(new int[]{345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356});
    private static final java.util.BitSet DEV44_R13_CRANK_12 = dev44R10Bits(new int[]{415, 416, 417, 418, 421, 428, 429, 430, 431, 438});
    private static final java.util.BitSet DEV44_R13_ROD_13 = dev44R10Bits(new int[]{35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 49, 50, 386, 387, 388, 389, 394, 395, 397, 398, 399, 400, 401, 402, 407, 408, 409, 410});
    private static final java.util.BitSet DEV44_R13_PISTON_13 = dev44R10Bits(new int[]{320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331});
    private static final java.util.BitSet DEV44_R13_CRANK_13 = dev44R10Bits(new int[]{390, 391, 392, 393, 396, 403, 404, 405, 406, 413});
    private static final java.util.BitSet DEV44_R13_ROD_14 = dev44R10Bits(new int[]{31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 371, 372, 373, 374, 378, 379, 381, 382, 383, 384, 385, 386, 391, 392, 393, 394});
    private static final java.util.BitSet DEV44_R13_PISTON_14 = dev44R10Bits(new int[]{311, 312, 313, 314});
    private static final java.util.BitSet DEV44_R13_CRANK_14 = dev44R10Bits(new int[]{375, 376, 377, 380, 387, 388, 389, 390, 397, 398});
    private static final java.util.BitSet DEV44_R13_ROD_15 = dev44R10Bits(new int[]{22, 47, 59, 178, 184});
    private static final java.util.BitSet DEV44_R13_PISTON_15 = dev44R10Bits(new int[]{172, 185});
    private static final java.util.BitSet DEV44_R13_CRANK_15 = dev44R10Bits(new int[]{20, 21, 45, 46, 175, 179});
    private static final java.util.BitSet DEV44_R13_ROD_16 = dev44R10Bits(new int[]{72, 78, 87, 92, 96, 97, 98, 100, 103});
    private static final java.util.BitSet DEV44_R13_PISTON_16 = dev44R10Bits(new int[]{101, 102});
    private static final java.util.BitSet DEV44_R13_CRANK_16 = dev44R10Bits(new int[]{62, 64, 65, 86});
    private static final java.util.BitSet DEV44_R13_ROD_17 = dev44R10Bits(new int[]{29, 30, 35, 36, 51, 53, 65, 66, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 379, 380, 381, 382, 383, 384});
    private static final java.util.BitSet DEV44_R13_PISTON_17 = dev44R10Bits(new int[]{34, 55});
    private static final java.util.BitSet DEV44_R13_CRANK_17 = dev44R10Bits(new int[]{25, 27, 28, 37, 50, 52, 54, 67});
    private static final java.util.BitSet DEV44_R13_ROD_18 = dev44R10Bits(new int[]{82, 83});
    private static final java.util.BitSet DEV44_R13_PISTON_18 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_18 = dev44R10Bits(new int[]{119, 120, 121, 122, 123, 124});
    private static final java.util.BitSet DEV44_R13_ROD_19 = dev44R10Bits(new int[]{73, 75, 77, 78, 79, 80, 81, 82, 83, 99, 100, 101, 102, 103, 104, 106, 107, 108, 109});
    private static final java.util.BitSet DEV44_R13_PISTON_19 = dev44R10Bits(new int[]{39, 74});
    private static final java.util.BitSet DEV44_R13_CRANK_19 = dev44R10Bits(new int[]{48, 49, 50, 52, 84, 105});
    private static final java.util.BitSet DEV44_R13_ROD_20 = dev44R10Bits(new int[]{59, 60, 61, 62, 63, 64, 72, 73, 74, 75, 76, 93, 146});
    private static final java.util.BitSet DEV44_R13_PISTON_20 = dev44R10Bits(new int[]{57, 174, 197});
    private static final java.util.BitSet DEV44_R13_CRANK_20 = dev44R10Bits(new int[]{94});
    private static final java.util.BitSet DEV44_R13_ROD_21 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 22, 278});
    private static final java.util.BitSet DEV44_R13_PISTON_21 = dev44R10Bits(new int[]{187, 194, 279, 280, 285, 287});
    private static final java.util.BitSet DEV44_R13_CRANK_21 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_22 = dev44R10Bits(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 24, 280});
    private static final java.util.BitSet DEV44_R13_PISTON_22 = dev44R10Bits(new int[]{190, 197});
    private static final java.util.BitSet DEV44_R13_CRANK_22 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_23 = dev44R10Bits(new int[]{442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 462, 463, 465, 468, 469, 470, 471, 472, 473, 474, 475, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 502, 503, 504, 529, 530, 531, 547, 548, 549, 556, 557, 558, 562, 563, 564, 579, 580, 581, 593, 594, 595, 596, 897, 898, 899});
    private static final java.util.BitSet DEV44_R13_PISTON_23 = dev44R10Bits(new int[]{390, 391, 392, 419, 420, 421, 422});
    private static final java.util.BitSet DEV44_R13_CRANK_23 = dev44R10Bits(new int[]{464, 466, 467, 476, 477, 478, 479, 480, 481, 482, 483, 494, 495, 496, 497, 498, 499, 500, 501, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 532, 533, 534, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 545, 546, 550, 551, 552, 553, 554, 555, 559, 560, 561, 565, 566, 567, 568, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 582, 583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 597, 598, 671, 672, 673, 674, 675, 676, 677, 678, 679, 681});
    private static final java.util.BitSet DEV44_R13_ROD_24 = dev44R10Bits(new int[]{25, 27, 28, 29, 31, 32, 37, 39, 40, 41, 43, 44, 616, 617, 618, 619});
    private static final java.util.BitSet DEV44_R13_PISTON_24 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_24 = dev44R10Bits(new int[]{30, 38, 42, 45, 46, 612, 614});
    private static final java.util.BitSet DEV44_R13_ROD_25 = dev44R10Bits(new int[]{478, 479, 480, 482, 484, 485, 488, 489, 490, 491, 492, 493, 494, 495, 498, 499, 500, 517, 518, 519, 524, 526, 533, 534, 535, 544, 545, 546, 560, 561, 562, 567, 569, 570, 571, 572, 573, 574, 575, 576, 577, 578, 579, 590, 591, 592, 593, 594, 595, 596, 597, 598, 599, 600, 601, 602, 603, 604, 605, 606, 607, 608, 609, 610, 611, 614, 615, 617, 640, 641, 642, 662, 663, 667, 669, 688});
    private static final java.util.BitSet DEV44_R13_PISTON_25 = dev44R10Bits(new int[]{420, 422, 431, 432, 434, 435, 441, 442, 443, 444, 445, 446, 453, 455, 456, 457, 459, 460, 461, 462, 463});
    private static final java.util.BitSet DEV44_R13_CRANK_25 = dev44R10Bits(new int[]{501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 520, 521, 522, 523, 525, 527, 528, 529, 530, 531, 532, 536, 537, 538, 539, 540, 541, 542, 543, 547, 548, 549, 550, 551, 552, 553, 554, 555, 556, 557, 558, 559, 563, 564, 565, 566, 568, 580, 581, 582, 583, 584, 585, 586, 587, 588, 589, 612, 613, 616, 618, 619, 620, 621, 622, 623, 624, 625, 626, 627, 628, 629, 630, 631, 632, 633, 634, 635, 636, 637, 638, 639, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 724, 725, 726, 727});
    private static final java.util.BitSet DEV44_R13_ROD_26 = dev44R10Bits(new int[]{43, 44});
    private static final java.util.BitSet DEV44_R13_PISTON_26 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_26 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_27 = dev44R10Bits(new int[]{38, 39, 40, 43, 44, 45, 67, 76, 77, 82, 83, 84, 85, 87, 88, 93, 94, 117, 118, 119, 120, 121, 122, 123, 124, 125, 194, 277});
    private static final java.util.BitSet DEV44_R13_PISTON_27 = dev44R10Bits(new int[]{11, 14, 209, 210, 212, 213, 216, 217, 218, 222});
    private static final java.util.BitSet DEV44_R13_CRANK_27 = dev44R10Bits(new int[]{37, 41, 42, 46, 86});
    private static final java.util.BitSet DEV44_R13_ROD_28 = dev44R10Bits(new int[]{103, 104, 112, 127, 128, 129, 130, 153, 157, 158, 159, 160, 169, 171, 173, 174, 175, 177, 178, 179, 181, 186, 187, 188, 189, 190, 191, 196, 198});
    private static final java.util.BitSet DEV44_R13_PISTON_28 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_28 = dev44R10Bits(new int[]{106, 107, 170, 172, 176, 180, 182, 183, 184, 185, 197});
    private static final java.util.BitSet DEV44_R13_ROD_29 = dev44R10Bits(new int[]{1, 329, 333, 334});
    private static final java.util.BitSet DEV44_R13_PISTON_29 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_29 = dev44R10Bits(new int[]{327, 328, 330, 331, 332, 335});
    private static final java.util.BitSet DEV44_R13_ROD_30 = dev44R10Bits(new int[]{206, 207, 209, 210, 211, 212, 214, 215, 216, 217, 218, 219, 220, 221, 222, 225, 228, 229, 230, 232, 233, 234, 236, 237, 241, 242, 243, 244, 245, 246, 249});
    private static final java.util.BitSet DEV44_R13_PISTON_30 = dev44R10Bits(new int[]{62, 63, 72, 73, 248});
    private static final java.util.BitSet DEV44_R13_CRANK_30 = dev44R10Bits(new int[]{208, 213, 247});
    private static final java.util.BitSet DEV44_R13_ROD_31 = dev44R10Bits(new int[]{59, 61, 62, 63, 65, 66, 67, 68, 69, 70, 72, 73, 74, 75, 77, 79, 80, 81, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93});
    private static final java.util.BitSet DEV44_R13_PISTON_31 = dev44R10Bits(new int[]{156, 157, 158, 160, 161, 162});
    private static final java.util.BitSet DEV44_R13_CRANK_31 = dev44R10Bits(new int[]{60, 64, 71, 76, 78, 82});
    private static final java.util.BitSet DEV44_R13_ROD_32 = dev44R10Bits(new int[]{113, 115, 116, 117, 119, 120, 121, 122, 123, 125, 126, 127, 128, 129, 131, 133, 134, 135, 137, 138, 139, 140, 141, 145, 146, 147});
    private static final java.util.BitSet DEV44_R13_PISTON_32 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_32 = dev44R10Bits(new int[]{114, 118, 124, 130, 132, 136, 142, 143, 144});
    private static final java.util.BitSet DEV44_R13_ROD_33 = dev44R10Bits(new int[]{398, 399, 400, 401, 402, 403, 404, 408, 409, 411, 412, 413, 414, 415, 416, 417, 418, 419, 423, 424, 425, 427, 428, 429, 430, 431, 433, 434, 435, 437, 438, 439, 442, 443, 445, 446, 447, 448, 449, 450, 451, 452, 454, 455, 456, 457});
    private static final java.util.BitSet DEV44_R13_PISTON_33 = dev44R10Bits(new int[]{91, 94, 96, 99});
    private static final java.util.BitSet DEV44_R13_CRANK_33 = dev44R10Bits(new int[]{405, 406, 407, 410, 420, 421, 422, 426, 432, 436, 440, 441, 444, 453, 458, 459, 460, 461});
    private static final java.util.BitSet DEV44_R13_ROD_34 = dev44R10Bits(new int[]{323, 324, 325, 326, 327, 328, 329, 333, 334, 340, 341, 342, 343, 344, 345, 346, 347, 348, 405, 406, 407, 408, 409, 410, 411, 412, 413, 435, 436, 437, 439, 440, 441, 444, 445, 447, 448, 449, 450, 451, 452, 453, 454, 456, 457, 458, 459});
    private static final java.util.BitSet DEV44_R13_PISTON_34 = dev44R10Bits(new int[]{91, 94, 96, 99});
    private static final java.util.BitSet DEV44_R13_CRANK_34 = dev44R10Bits(new int[]{330, 331, 332, 335, 349, 350, 351, 414, 438, 442, 443, 446, 455, 460, 461, 462, 509});
    private static final java.util.BitSet DEV44_R13_ROD_35 = dev44R10Bits(new int[]{72, 96, 97, 98});
    private static final java.util.BitSet DEV44_R13_PISTON_35 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_35 = dev44R10Bits(new int[]{75, 80, 85, 90, 99});
    private static final java.util.BitSet DEV44_R13_ROD_36 = dev44R10Bits(new int[]{179, 180, 186, 187, 189, 190, 195, 201, 413, 516});
    private static final java.util.BitSet DEV44_R13_PISTON_36 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_36 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_37 = dev44R10Bits(new int[]{121, 122, 123, 124, 126, 127, 128, 129, 131, 132, 133, 134, 136, 137, 138, 139, 140, 142, 143, 144});
    private static final java.util.BitSet DEV44_R13_PISTON_37 = dev44R10Bits(new int[]{53, 66, 67, 68});
    private static final java.util.BitSet DEV44_R13_CRANK_37 = dev44R10Bits(new int[]{125, 130, 135, 141});
    private static final java.util.BitSet DEV44_R13_ROD_38 = dev44R10Bits(new int[]{34, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 52, 53, 275, 276, 277, 278, 282, 283, 285, 286, 287, 288, 289, 290});
    private static final java.util.BitSet DEV44_R13_PISTON_38 = dev44R10Bits(new int[]{236, 238});
    private static final java.util.BitSet DEV44_R13_CRANK_38 = dev44R10Bits(new int[]{279, 280, 281, 284, 291, 292, 293, 294});
    private static final java.util.BitSet DEV44_R13_ROD_39 = dev44R10Bits(new int[]{3, 6, 8, 35, 58, 59});
    private static final java.util.BitSet DEV44_R13_PISTON_39 = dev44R10Bits(new int[]{5, 7});
    private static final java.util.BitSet DEV44_R13_CRANK_39 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_40 = dev44R10Bits(new int[]{9, 10});
    private static final java.util.BitSet DEV44_R13_PISTON_40 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_40 = dev44R10Bits(new int[]{5, 6, 7, 8});
    private static final java.util.BitSet DEV44_R13_ROD_41 = dev44R10Bits(new int[]{20, 24, 32, 33, 46, 68});
    private static final java.util.BitSet DEV44_R13_PISTON_41 = dev44R10Bits(new int[]{34, 35});
    private static final java.util.BitSet DEV44_R13_CRANK_41 = dev44R10Bits(new int[]{18, 19, 26, 30, 67, 72});
    private static final java.util.BitSet DEV44_R13_ROD_42 = dev44R10Bits(new int[]{48, 49, 50, 51, 53});
    private static final java.util.BitSet DEV44_R13_PISTON_42 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_CRANK_42 = dev44R10Bits(new int[]{});
    private static final java.util.BitSet DEV44_R13_ROD_43 = dev44R10Bits(new int[]{24, 26, 85, 96, 97, 98, 132, 133, 154, 156});
    private static final java.util.BitSet DEV44_R13_PISTON_43 = dev44R10Bits(new int[]{15, 18, 23, 144, 145, 148});
    private static final java.util.BitSet DEV44_R13_CRANK_43 = dev44R10Bits(new int[]{2, 8, 25, 27, 28, 29, 30, 31, 95, 155});

    // DEV44 R13: separate, reversible linkage motion *preview*.
    // Original authored mesh surfaces are reused and not replaced by inferred boxes.
    // Preview is OFF until run/DEV44-R13-MOTION-PREVIEW-ON.txt contains ON.
    private static boolean dev44R13PreviewOn() {
        try {
            java.nio.file.Path p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-R13-MOTION-PREVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) { return false; }
    }
    private static boolean dev44R13Protected(String name) {
        // Never animate previously accepted mechanisms in an experimental preview.
        return name.equals("locomotive_steam_vb_shay")
                || name.equals("locomotive_steam_alco_sc4");
    }
    private static int dev44R13Role(String source, int chunk) {
        String name = source.substring(source.lastIndexOf('/')+1).replace(".tcm", "");
        if (dev44R13Protected(name)) return 0;
        if (name.equals("locomotive_steam_262t")) { if (DEV44_R13_ROD_0.get(chunk)) return 1; if (DEV44_R13_PISTON_0.get(chunk)) return 2; if (DEV44_R13_CRANK_0.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_4_4_0")) { if (DEV44_R13_ROD_1.get(chunk)) return 1; if (DEV44_R13_PISTON_1.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_a4_mallard")) { if (DEV44_R13_ROD_2.get(chunk)) return 1; if (DEV44_R13_CRANK_2.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_adler")) { if (DEV44_R13_ROD_3.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_alco_sc4")) { if (DEV44_R13_ROD_4.get(chunk)) return 1; if (DEV44_R13_PISTON_4.get(chunk)) return 2; if (DEV44_R13_CRANK_4.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_alice_0_4_0")) { if (DEV44_R13_ROD_5.get(chunk)) return 1; if (DEV44_R13_PISTON_5.get(chunk)) return 2; if (DEV44_R13_CRANK_5.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_berkshire_1225")) { if (DEV44_R13_ROD_6.get(chunk)) return 1; if (DEV44_R13_PISTON_6.get(chunk)) return 2; if (DEV44_R13_CRANK_6.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_berkshire_765")) { if (DEV44_R13_ROD_7.get(chunk)) return 1; if (DEV44_R13_PISTON_7.get(chunk)) return 2; if (DEV44_R13_CRANK_7.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_br80")) { if (DEV44_R13_ROD_8.get(chunk)) return 1; if (DEV44_R13_PISTON_8.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_br_black_5")) { if (DEV44_R13_ROD_9.get(chunk)) return 1; if (DEV44_R13_PISTON_9.get(chunk)) return 2; if (DEV44_R13_CRANK_9.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_br_britannia")) { if (DEV44_R13_ROD_10.get(chunk)) return 1; if (DEV44_R13_PISTON_10.get(chunk)) return 2; if (DEV44_R13_CRANK_10.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c11")) { if (DEV44_R13_ROD_11.get(chunk)) return 1; if (DEV44_R13_PISTON_11.get(chunk)) return 2; if (DEV44_R13_CRANK_11.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41")) { if (DEV44_R13_ROD_12.get(chunk)) return 1; if (DEV44_R13_PISTON_12.get(chunk)) return 2; if (DEV44_R13_CRANK_12.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41_080")) { if (DEV44_R13_ROD_13.get(chunk)) return 1; if (DEV44_R13_PISTON_13.get(chunk)) return 2; if (DEV44_R13_CRANK_13.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_c41t")) { if (DEV44_R13_ROD_14.get(chunk)) return 1; if (DEV44_R13_PISTON_14.get(chunk)) return 2; if (DEV44_R13_CRANK_14.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_climax")) { if (DEV44_R13_ROD_15.get(chunk)) return 1; if (DEV44_R13_PISTON_15.get(chunk)) return 2; if (DEV44_R13_CRANK_15.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_climax_2")) { if (DEV44_R13_ROD_16.get(chunk)) return 1; if (DEV44_R13_PISTON_16.get(chunk)) return 2; if (DEV44_R13_CRANK_16.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_coronation_class")) { if (DEV44_R13_ROD_17.get(chunk)) return 1; if (DEV44_R13_PISTON_17.get(chunk)) return 2; if (DEV44_R13_CRANK_17.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_fowler")) { if (DEV44_R13_ROD_18.get(chunk)) return 1; if (DEV44_R13_CRANK_18.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gs4")) { if (DEV44_R13_ROD_19.get(chunk)) return 1; if (DEV44_R13_PISTON_19.get(chunk)) return 2; if (DEV44_R13_CRANK_19.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gwr_101_class")) { if (DEV44_R13_ROD_20.get(chunk)) return 1; if (DEV44_R13_PISTON_20.get(chunk)) return 2; if (DEV44_R13_CRANK_20.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_gwr_42xx")) { if (DEV44_R13_ROD_21.get(chunk)) return 1; if (DEV44_R13_PISTON_21.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_gwr_72xx")) { if (DEV44_R13_ROD_22.get(chunk)) return 1; if (DEV44_R13_PISTON_22.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_hall_class")) { if (DEV44_R13_ROD_23.get(chunk)) return 1; if (DEV44_R13_PISTON_23.get(chunk)) return 2; if (DEV44_R13_CRANK_23.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_j50")) { if (DEV44_R13_ROD_24.get(chunk)) return 1; if (DEV44_R13_CRANK_24.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_king_class")) { if (DEV44_R13_ROD_25.get(chunk)) return 1; if (DEV44_R13_PISTON_25.get(chunk)) return 2; if (DEV44_R13_CRANK_25.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_lssp7")) { if (DEV44_R13_ROD_26.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_midland_compound")) { if (DEV44_R13_ROD_27.get(chunk)) return 1; if (DEV44_R13_PISTON_27.get(chunk)) return 2; if (DEV44_R13_CRANK_27.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_milw_class_a")) { if (DEV44_R13_ROD_28.get(chunk)) return 1; if (DEV44_R13_CRANK_28.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_pannier")) { if (DEV44_R13_ROD_29.get(chunk)) return 1; if (DEV44_R13_CRANK_29.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_pe")) { if (DEV44_R13_ROD_30.get(chunk)) return 1; if (DEV44_R13_PISTON_30.get(chunk)) return 2; if (DEV44_R13_CRANK_30.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_rw_type_2")) { if (DEV44_R13_ROD_31.get(chunk)) return 1; if (DEV44_R13_PISTON_31.get(chunk)) return 2; if (DEV44_R13_CRANK_31.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_rw_type_3")) { if (DEV44_R13_ROD_32.get(chunk)) return 1; if (DEV44_R13_CRANK_32.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_s100_uk")) { if (DEV44_R13_ROD_33.get(chunk)) return 1; if (DEV44_R13_PISTON_33.get(chunk)) return 2; if (DEV44_R13_CRANK_33.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_s100_us")) { if (DEV44_R13_ROD_34.get(chunk)) return 1; if (DEV44_R13_PISTON_34.get(chunk)) return 2; if (DEV44_R13_CRANK_34.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_sentinel_y3")) { if (DEV44_R13_ROD_35.get(chunk)) return 1; if (DEV44_R13_CRANK_35.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_shay_3_truck")) { if (DEV44_R13_ROD_36.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_skookum")) { if (DEV44_R13_ROD_37.get(chunk)) return 1; if (DEV44_R13_PISTON_37.get(chunk)) return 2; if (DEV44_R13_CRANK_37.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_southern1102")) { if (DEV44_R13_ROD_38.get(chunk)) return 1; if (DEV44_R13_PISTON_38.get(chunk)) return 2; if (DEV44_R13_CRANK_38.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_u57")) { if (DEV44_R13_ROD_39.get(chunk)) return 1; if (DEV44_R13_PISTON_39.get(chunk)) return 2; return 0; }
        if (name.equals("locomotive_steam_vb_0_4_0")) { if (DEV44_R13_ROD_40.get(chunk)) return 1; if (DEV44_R13_CRANK_40.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_vb_shay")) { if (DEV44_R13_ROD_41.get(chunk)) return 1; if (DEV44_R13_PISTON_41.get(chunk)) return 2; if (DEV44_R13_CRANK_41.get(chunk)) return 3; return 0; }
        if (name.equals("locomotive_steam_vb_shay_2")) { if (DEV44_R13_ROD_42.get(chunk)) return 1; return 0; }
        if (name.equals("locomotive_steam_wwcp_062t")) { if (DEV44_R13_ROD_43.get(chunk)) return 1; if (DEV44_R13_PISTON_43.get(chunk)) return 2; if (DEV44_R13_CRANK_43.get(chunk)) return 3; return 0; }
        return 0;
    }
    // DEV44 R13d: fleet-wide geometry telemetry. Diagnostic only; no wheel or linkage mutations.
    private static final java.util.Set<String> DEV44_R13D_HEADER = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long dev44R13dFrames;
    private static boolean dev44R13dOn() {
        try {
            var marker = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13D-FLEET-DIAGNOSTIC-ON.txt");
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) {return false;}
    }
    private static synchronized void dev44R13dLine(String name, String row) {
        try {
            var dir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13d-Fleet-Diagnostics");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(name), row + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R13d] diagnostic write failed: {}", ex.toString());
        }
    }
    private void dev44R13dCapture(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13dOn() || !this.dev44R11Verified) return;
        String mesh=this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        long frame=++dev44R13dFrames;
        boolean first=DEV44_R13D_HEADER.add(mesh);
        if (first) {
            dev44R13dLine("fleet-summary.csv", "MESH,"+mesh+",WHEELS,"+wheels.size()+",CHUNKS,"+((vertexCount+23)/24));
            dev44R13dLine(mesh+".csv", "kind,frame,chunk,role,cx,cy,cz,wheelX,wheelY,wheelZ,axis,cos,sin,nearestWheelDistance,notes");
        }
        // Initial full geometry sweep, then phase samples at deliberately bounded frequency.
        if (!first && frame % 40 != 0) return;
        int total=(this.vertexCount+23)/24;
        for(int ch=0;ch<total;ch++) {
            int role=dev44R13Role(this.source.getPath(),ch);
            if(role==0 && !first) continue;
            if(role==0 && !dev44R10Mapped(this.source.getPath(),ch)) continue;
            int start=ch*24*FLOATS_PER_VERTEX;
            int end=Math.min(vertices.length,start+24*FLOATS_PER_VERTEX);
            float x=0,y=0,zz=0;int n=0;
            for(int k=start;k<end;k+=FLOATS_PER_VERTEX){x+=vertices[k];y+=vertices[k+1];zz+=vertices[k+2];n++;}
            if(n==0) continue;
            x/=n;y/=n;zz/=n;
            var best=wheels.isEmpty()?null:wheels.get(0);
            if(best!=null) best=dev44R13NearestWheel(wheels,x,y,zz,best);
            String kind=role==0?"MAPPED_UNCLASSIFIED":"LINKAGE";
            String wheelValues=best==null?",,,,,,,":","+best.centerX()+","+best.centerY()+","+best.centerZ()+","+best.axis()+","+best.cos()+","+best.sin()+","+Math.sqrt(Math.pow(x-best.centerX(),2)+Math.pow(y-best.centerY(),2)+Math.pow(zz-best.centerZ(),2));
            dev44R13dLine(mesh+".csv",kind+","+frame+","+ch+","+role+","+x+","+y+","+zz+wheelValues+",computed-geometry-not-verified-pin");
        }
        if(first || frame%40==0) {
            for(int i=0;i<wheels.size();i++) {
                var w=wheels.get(i);
                dev44R13dLine(mesh+".csv","WHEEL,"+frame+","+i+",0,,,,"+w.centerX()+","+w.centerY()+","+w.centerZ()+","+w.axis()+","+w.cos()+","+w.sin()+",,accepted-wheel-phase");
            }
        }
    }
    // DEV44 R13j: ALCO SC4 opt-in, original authored rod mesh visibility proof.
    // Protects accepted wheel animation: never changes dynamic wheel transforms.
    private static boolean dev44R13jAlcoProofOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13j-ALCO-SC4-RODS-PROOF-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    // DEV44 R13k: ALCO SC4 ONLY; opt-in linkage motion PROOF, not accepted attachment solver.
    // R13h cache and accepted wheel animator are left untouched.
    private static boolean dev44R13kAlcoMotionOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13k-ALCO-SC4-MOTION-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ex) { return false; }
    }
    // DEV44 R13l: fail-closed fleet linkage review. Static attachment suggestions are NOT
    // mechanically verified; opt-in mode suppresses the rejected R13k preview, never wheels.
    private static boolean dev44R13lReviewOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13l-FLEET-REVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private boolean dev44R13lReported = false;
    private void dev44R13lReport(String meshKey, java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13lReviewOn() || dev44R13lReported || !this.dev44R11Verified) return;
        dev44R13lReported = true; // one-shot per mesh; no per-frame writes
        try {
            dev44R13hPrepare(); // already cached by accepted R13h implementation
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13l-Fleet-Review");
            java.nio.file.Files.createDirectories(dir);
            var mesh=this.source.getPath();
            if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
            var name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm", "");
            var sb=new StringBuilder("mesh,chunk,role,chunkCenterX,chunkCenterY,chunkCenterZ,nearestWheelX,nearestWheelY,nearestWheelZ,centerDistance,reviewStatus\n");
            int candidates=0;
            for(int ch=0;ch<dev44R13hRoles.length;ch++) {
                int role=dev44R13hRoles[ch];
                if(role!=1 && role!=2 && role!=3) continue;
                float x=dev44R13hCx[ch],y=dev44R13hCy[ch],z=dev44R13hCz[ch];
                double best=Double.POSITIVE_INFINITY;
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec near=null;
                for(var wheel:wheels) {
                    double dx=x-wheel.centerX(),dy=y-wheel.centerY(),dz=z-wheel.centerZ();
                    double dist=dx*dx+dy*dy+dz*dz;
                    if(dist<best) {best=dist; near=wheel;}
                }
                sb.append(name).append(',').append(ch).append(',').append(role).append(',')
                  .append(x).append(',').append(y).append(',').append(z).append(',');
                if(near!=null) sb.append(near.centerX()).append(',').append(near.centerY()).append(',')
                  .append(near.centerZ()).append(',').append(Math.sqrt(best));
                else sb.append(",,,");
                sb.append(',').append(role==2?"STATIONARY_SHELL_DO_NOT_ANIMATE":
                    near==null?"NO_WHEEL_REQUIRES_REVIEW":"CANDIDATE_ONLY_ENDPOINTS_UNVERIFIED").append('\n');
                candidates++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),sb.toString(),
                 java.nio.charset.StandardCharsets.UTF_8,
                 java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13l-FLEET] mesh={} candidates={} wheels={} reviewed=false phase=ACCEPTED_READ_ONLY previewR13kSuppressed=true", name,candidates,wheels.size());
        } catch(Exception ex) {LOGGER.warn("[TC-DEV44-R13l-FLEET] report failed {}",ex.toString());}
    }

    // DEV44 R13m: READ-ONLY fleet rod endpoint extraction; geometric extremes are
    // hypotheses, NOT proven joints. Capture once per mesh with no per-frame writes.
    private static boolean dev44R13mCaptureOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13m-ENDPOINT-CAPTURE-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) {return false;}
    }
    private boolean dev44R13mReported = false;
    private void dev44R13mReport(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13mCaptureOn() || dev44R13mReported || !this.dev44R11Verified) return;
        String mesh=this.source.getPath();
        if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
        dev44R13mReported=true;
        try {
            dev44R13hPrepare();
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13m-Fleet-Endpoints");
            java.nio.file.Files.createDirectories(dir);
            String name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm", "");
            var b=new StringBuilder("mesh,chunk,role,geometryAxis,endpointA_X,endpointA_Y,endpointA_Z,endpointB_X,endpointB_Y,endpointB_Z,span,nearestWheelA_X,nearestWheelA_Y,nearestWheelA_Z,nearestWheelB_X,nearestWheelB_Y,nearestWheelB_Z,distanceA,distanceB,status\n");
            int count=0; int total=(this.vertexCount+23)/24;
            for (int ch=0;ch<total;ch++) {
                int role=ch<dev44R13hRoles.length?dev44R13hRoles[ch]:0;
                // All mapped candidate roles plus SC4 authored rod chunks, even if role 0.
                boolean alco=name.equals("locomotive_steam_alco_sc4");
                boolean mapped=dev44R10Mapped(mesh,ch);
                if (role!=1 && role!=2 && role!=3 && !(alco && mapped)) continue;
                int lo=ch*24*FLOATS_PER_VERTEX;
                int hi=Math.min(this.vertexCount*FLOATS_PER_VERTEX,lo+24*FLOATS_PER_VERTEX);
                if (lo>=hi) continue;
                float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                float[] sum={0,0,0};int n=0;
                for (int j=lo;j+2<hi;j+=FLOATS_PER_VERTEX) {
                    for (int ax=0;ax<3;ax++) {float q=this.vertices[j+ax];
                        if (!Float.isFinite(q)) continue;
                        min[ax]=Math.min(min[ax],q);max[ax]=Math.max(max[ax],q);sum[ax]+=q;}
                    n++;
                }
                if(n==0)continue;
                float[] center={sum[0]/n,sum[1]/n,sum[2]/n};
                int axis=0;for(int a=1;a<3;a++)if(max[a]-min[a]>max[axis]-min[axis])axis=a;
                float[] a=center.clone(),c=center.clone(); a[axis]=min[axis];c[axis]=max[axis];
                var nearA=dev44R13mNearest(wheels,a);
                var nearB=dev44R13mNearest(wheels,c);
                float span=max[axis]-min[axis];
                double da=nearA==null?Double.NaN:dev44R13mDist(a,nearA);
                double db=nearB==null?Double.NaN:dev44R13mDist(c,nearB);
                String status=role==2?"FIXED_SHELL_NOT_ANIMATED":
                    nearA==null||nearB==null?"NO_WHEEL_PIN_UNVERIFIED":
                    span<0.01f?"DEGENERATE_EXTENT_UNVERIFIED":"EXTREME_POINTS_ONLY_UNVERIFIED";
                b.append(name).append(',').append(ch).append(',').append(role).append(',').append(axis);
                for(float v:a)b.append(',').append(v);
                for(float v:c)b.append(',').append(v);
                b.append(',').append(span);
                for(var w:new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec[]{nearA,nearB}) {
                    if(w==null)b.append(",,,");
                    else b.append(',').append(w.centerX()).append(',').append(w.centerY()).append(',').append(w.centerZ());
                }
                b.append(',').append(da).append(',').append(db).append(',').append(status).append('\n');count++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),b.toString(),
                java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13m-ENDPOINTS] mesh={} candidateChunks={} wheels={} verifiedEndpoints=0 motionChanges=false",name,count,wheels.size());
        } catch(Exception e){LOGGER.warn("[TC-DEV44-R13m-ENDPOINTS] {} failed {}",mesh,e.toString());}
    }
    private static double dev44R13mDist(float[] p,
          traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec w) {
        return Math.sqrt(Math.pow(p[0]-w.centerX(),2)+Math.pow(p[1]-w.centerY(),2)+Math.pow(p[2]-w.centerZ(),2));
    }
    private static traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev44R13mNearest(java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels,float[] p) {
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec best=null;
        double d=Double.POSITIVE_INFINITY;
        for (var w:wheels) {double v=dev44R13mDist(p,w);if(v<d){d=v;best=w;}}
        return best;
    }

    // DEV44 R13n: conservative joint plausibility audit. A wheel CENTER is not a
    // crank PIN; no row generated here is an accepted/verified joint.


    // DEV44 R13w: Berkshire-only old-preview isolation plus phase-sampled wheel context.
    // Read only: does not select/approve crank pins or alter accepted wheel transforms.
    private static final java.util.Set<Integer> dev44R13wLoggedQuadrants =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13wOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13w-BERKSHIRE-TEST-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }

    // DEV44 R13ab (R13x switch; R13z motion preserved): opt-in Berkshire 765 RIGID rod preview. User-selected
    // mesh extrema are candidate joints; not verified mechanical crank pins.
    private static boolean dev44R13xOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13x-BERKSHIRE-ROD348-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    // DEV44 R13ad: INDEPENDENT opt-in mirrored-side rod733 trial; leaves R13ab rod348 intact.
    private static boolean dev44R13adOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ad-BERKSHIRE-ROD733-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    // R13af: BOTH-SIDE slider proof; the two selected chunks are CANDIDATES, not verified crossheads.
    private static boolean dev44R13amOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13am-BERKSHIRE-JOINT-SOLVER-AUDIT-ON.txt");
              return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13amLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13anOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13an-BERKSHIRE-ATTACHMENT-CALIBRATION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13anLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13aoOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ao-MESH-ENDPOINT-SCAN-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13aoLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13ar experimental two-sided rod trial. Independent from accepted rods/sliders.
    private static boolean dev44R13arOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ar-BERKSHIRE-CONNECTING-RODS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13arLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13asOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13as-BERKSHIRE-TWO-END-FIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13asLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // DEV44 R13at: read-only bilateral slider-crank closure comparison.
    private static boolean dev44R13atOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13at-KINEMATICS-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13atLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // R13au: geometry-gated calibrated linkage. No existing accepted motion is modified.
    private static boolean dev44R13auOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13au-CALIBRATED-LINKAGE-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13auLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<Integer,double[]> dev44R13auCalibrated=new java.util.concurrent.ConcurrentHashMap<>();

    // R13av: bilateral mesh-joint orientation study; never writes an animation pose.
    // Previous R13au used one assumed assignment of the rod ends. This compares
    // both assignments against accepted slider motion and rotating wheel centers.
    private static boolean dev44R13avOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13av-JOINT-ORIENTATION-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13avLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> dev44R13axLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13aw: crosshead pivot neighborhood audit, always read-only.
    private static boolean dev44R13awOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aw-CROSSHEAD-PIVOT-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13awLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();

    // R13aq full bilateral candidate sweep is diagnostic-only; no animation writes.
    private static boolean dev44R13aqOn() {
        try {var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aq-FULL-BILATERAL-JOINT-SWEEP-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        }catch(Exception ex){return false;}
    }
    private static final java.util.Set<Integer> dev44R13aqLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13ap: original-mesh joint-neighborhood classification only. No pose writes.
    private static boolean dev44R13apOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ap-BERKSHIRE-LINKAGE-CLASSIFICATION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13apLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // R13al never animates unverified connecting-rod chunks.
    private static boolean dev44R13alOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13al-BERKSHIRE-BILATERAL-AUDIT-ON.txt");
              return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13alLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev44R13afOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13af-BERKSHIRE-BOTH-SLIDERS-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }
    private static final java.util.Set<Integer> dev44R13afLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> dev44R13adLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<Integer> dev44R13xLogged =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // DEV44 R13v opt-in one-shot wheel phase/pivot proof. Probe produces log only.
    private static final java.util.concurrent.atomic.AtomicBoolean dev44R13vProbePending =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    private static boolean dev44R13vProbeOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13v-PROBE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception ignored) { return false; }
    }


    // DEV44 R13az: explicit opt-in only; only the two Berkshire connecting rods.
    private static boolean dev44R13azOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13az-BERKSHIRE-VIRTUAL-RODS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) {return false;}
    }
    private static final java.util.Set<Integer> dev44R13azLogged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    // DEV44 R13u: authored TWO-WHEEL rigid rod connections ONLY. OFF by default.
    private static boolean dev44R13uOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13u-TWO-JOINT-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) { return false; }
    }
    private record Dev44R13uJoint(String mesh,int rod,int wheelA,int wheelB,float[] a,float[] b) {}
    private record Dev44R13uPose(Dev44RigidLinkage.Pose pose,float[] a) {}
    private static volatile java.util.List<Dev44R13uJoint> dev44R13uRows;
    private static java.util.List<Dev44R13uJoint> dev44R13uRows() {
        if(dev44R13uRows!=null) return dev44R13uRows;
        synchronized(LegacyStaticMesh.class) {
            if(dev44R13uRows!=null) return dev44R13uRows;
            java.util.ArrayList<Dev44R13uJoint> out=new java.util.ArrayList<>();
            var file=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13u-VERIFIED-TWO-JOINTS.csv");
            try { if(java.nio.file.Files.isRegularFile(file)) for(String row:java.nio.file.Files.readAllLines(file)) {
                String s=row.trim(); if(s.isEmpty()||s.startsWith("#")||s.startsWith("mesh,")) continue;
                String[] f=s.split(",",-1); if(f.length!=10) { LOGGER.warn("[TC-DEV44-R13u] rejected row: expected 10 columns");continue; }
                String mesh=f[0].trim(); if(!mesh.matches("locomotive_steam_[a-z0-9_]+"))continue;
                int rod=Integer.parseInt(f[1].trim()),wa=Integer.parseInt(f[2].trim()),wb=Integer.parseInt(f[3].trim());
                if(rod<0||wa<0||wb<0||rod==wa||rod==wb||wa==wb)continue;
                float[] a=new float[3],b=new float[3]; boolean finite=true;
                for(int i=0;i<3;i++){a[i]=Float.parseFloat(f[4+i].trim());b[i]=Float.parseFloat(f[7+i].trim());finite &= Float.isFinite(a[i])&&Float.isFinite(b[i]);}
                if(!finite)continue;
                out.add(new Dev44R13uJoint(mesh,rod,wa,wb,a,b));
            }} catch(Exception e){LOGGER.warn("[TC-DEV44-R13u] CSV rejected: {}",e.toString());out.clear();}
            dev44R13uRows=java.util.List.copyOf(out);
            LOGGER.info("[TC-DEV44-R13u] loaded manually verified TWO-JOINT rows={}; empty=motion OFF",out.size());
            return dev44R13uRows;
        }
    }

    // DEV44 R13o: explicitly authored rod/crank pairs only. Never infer mechanical
    // joints from R13n nearest-wheel classifications. OFF without marker and CSV.
    private static boolean dev44R13oOn() {
        try { var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13o-PAIR-MOTION-ON.txt");
            return java.nio.file.Files.isRegularFile(p) && java.nio.file.Files.readString(p).trim().equalsIgnoreCase("ON");
        } catch(Exception e) {return false;}
    }
    private record Dev44R13oPair(String mesh,int rod,int crank,int wheel) {}
    private static volatile java.util.List<Dev44R13oPair> dev44R13oPairs;
    private static java.util.List<Dev44R13oPair> dev44R13oPairs() {
        var cached=dev44R13oPairs;
        if(cached!=null)return cached;
        synchronized(LegacyStaticMesh.class) {
            if(dev44R13oPairs!=null)return dev44R13oPairs;
            var rows=new java.util.ArrayList<Dev44R13oPair>();
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13o-Verified-Pairs.csv");
            try { if(java.nio.file.Files.isRegularFile(p)) for(String row:java.nio.file.Files.readAllLines(p)) {
                String t=row.trim();if(t.isEmpty()||t.startsWith("#")||t.startsWith("mesh,"))continue;
                String[] fields=t.split(",",-1);if(fields.length!=4)continue;
                String mesh=fields[0].trim();
                if(!mesh.matches("locomotive_steam_[a-z0-9_]+"))continue;
                int rod=Integer.parseInt(fields[1].trim()),crank=Integer.parseInt(fields[2].trim()),wheel=Integer.parseInt(fields[3].trim());
                if(rod<0||crank<0||wheel<0||rod==crank||rod==wheel||crank==wheel)continue;
                rows.add(new Dev44R13oPair(mesh,rod,crank,wheel));
            }} catch(Exception e){LOGGER.warn("[TC-DEV44-R13o] Invalid pair CSV: {}",e.toString());}
            dev44R13oPairs=java.util.List.copyOf(rows);
            LOGGER.info("[TC-DEV44-R13o] loaded authored rod/crank pairs={}; unlisted chunks untouched",rows.size());
            return dev44R13oPairs;
        }
    }
    // Only identical translation at one authored crank attachment, both solid
    // chunks moved together. This is a limited attachment proof, not the final
    // connecting-rod / crosshead rigid-body solver.
    private static float[] dev44R13oTranslation(
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec w,
            float px,float py,float pz) {
        float a=px-w.centerX(),b=py-w.centerY(),c=pz-w.centerZ();
        float cs=w.cos(),sn=w.sin();
        if(w.axis()==0)return new float[]{0,a*0+(b*(cs-1)-c*sn),b*sn+c*(cs-1)};
        if(w.axis()==1)return new float[]{a*(cs-1)-c*sn,0,a*sn+c*(cs-1)};
        return new float[]{a*(cs-1)-b*sn,a*sn+b*(cs-1),0};
    }

    private static boolean dev44R13nOn() {
        try {
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13n-JOINT-REVIEW-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim().equalsIgnoreCase("ON");
        } catch(Exception ex) {return false;}
    }
    private boolean dev44R13nReported=false;
    private void dev44R13nReport(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13nOn() || dev44R13nReported || !this.dev44R11Verified) return;
        String mesh=this.source.getPath();
        if (!mesh.startsWith("legacy_meshes/locomotive_steam_")) return;
        dev44R13nReported=true;
        try {
            dev44R13hPrepare();
            var dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13n-Fleet-Joint-Review");
            java.nio.file.Files.createDirectories(dir);
            String name=mesh.substring(mesh.lastIndexOf('/')+1).replace(".tcm","");
            var b=new StringBuilder("mesh,chunk,role,axis,span,endpointA_X,endpointA_Y,endpointA_Z,endpointB_X,endpointB_Y,endpointB_Z,wheelA_X,wheelA_Y,wheelA_Z,wheelB_X,wheelB_Y,wheelB_Z,distanceToCenterA,distanceToCenterB,wheelCenterSeparation,classification,verifiedJoint\n");
            int count=0,review=0,rejected=0; int total=(this.vertexCount+23)/24;
            for(int ch=0;ch<total;ch++) {
                int role=ch<dev44R13hRoles.length?dev44R13hRoles[ch]:0;
                boolean alco=name.equals("locomotive_steam_alco_sc4");
                boolean mapped=dev44R10Mapped(mesh,ch);
                if(role!=1 && role!=2 && role!=3 && !(alco && mapped)) continue;
                int lo=ch*24*FLOATS_PER_VERTEX,hi=Math.min(this.vertexCount*FLOATS_PER_VERTEX,lo+24*FLOATS_PER_VERTEX);
                if(lo>=hi)continue;
                float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
                float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                float[] sum={0,0,0};int n=0;
                for(int j=lo;j+2<hi;j+=FLOATS_PER_VERTEX){
                    for(int ax=0;ax<3;ax++){float v=this.vertices[j+ax];if(!Float.isFinite(v))continue;
                        min[ax]=Math.min(min[ax],v);max[ax]=Math.max(max[ax],v);sum[ax]+=v;}
                    n++;
                }
                if(n==0)continue;
                float[] center={sum[0]/n,sum[1]/n,sum[2]/n};
                int axis=0;for(int a=1;a<3;a++)if(max[a]-min[a]>max[axis]-min[axis])axis=a;
                float[] ea=center.clone(),eb=center.clone();ea[axis]=min[axis];eb[axis]=max[axis];
                var wa=dev44R13mNearest(wheels,ea);
                var wb=dev44R13mNearest(wheels,eb);
                double span=max[axis]-min[axis];
                double da=wa==null?Double.NaN:dev44R13mDist(ea,wa);
                double db=wb==null?Double.NaN:dev44R13mDist(eb,wb);
                double sep=wa==null||wb==null?Double.NaN:Math.sqrt(
                    Math.pow(wa.centerX()-wb.centerX(),2)+Math.pow(wa.centerY()-wb.centerY(),2)+Math.pow(wa.centerZ()-wb.centerZ(),2));
                String verdict;
                if(role==2)verdict="FIXED_SHELL_EXCLUDED";
                else if(wa==null||wb==null)verdict="NO_WHEEL_CENTER";
                else if(span<0.02)verdict="DEGENERATE_GEOMETRY";
                else if(sep<0.02)verdict="BOTH_ENDS_NEAREST_SAME_WHEEL";
                else if(da>1.5||db>1.5)verdict="FAR_FROM_WHEEL_CENTERS";
                else verdict="REVIEW_CANDIDATE_NOT_CRANK_PIN";
                if(verdict.equals("REVIEW_CANDIDATE_NOT_CRANK_PIN"))review++;else rejected++;
                b.append(name).append(',').append(ch).append(',').append(role).append(',').append(axis).append(',').append(span);
                for(float v:ea)b.append(',').append(v);for(float v:eb)b.append(',').append(v);
                for(var w:new traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec[]{wa,wb}){
                    if(w==null)b.append(",,,");else b.append(',').append(w.centerX()).append(',').append(w.centerY()).append(',').append(w.centerZ());
                }
                b.append(',').append(da).append(',').append(db).append(',').append(sep).append(',').append(verdict).append(",false\n");count++;
            }
            java.nio.file.Files.writeString(dir.resolve(name+".csv"),b.toString(),java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[TC-DEV44-R13n-JOINTS] mesh={} candidates={} review={} excluded={} wheels={} verified=0 motionChanges=false",name,count,review,rejected,wheels.size());
        } catch(Exception ex){LOGGER.warn("[TC-DEV44-R13n-JOINTS] failed mesh={} error={}",mesh,ex.toString());}
    }

    private boolean dev44R13kReported = false;
    // Authored centers, computed once (no per-frame vertex centroid scans).
    private float[] dev44R13kCx, dev44R13kCy, dev44R13kCz;
    private int[] dev44R13kNearestCrank;
    private void dev44R13kPrepare() {
        if (dev44R13kCx != null) return;
        int n=(this.vertexCount+23)/24;
        float[] xs=new float[n], ys=new float[n], zs=new float[n];
        for (int ch=0;ch<n;ch++) {
            if (!DEV44_R13_ROD_4.get(ch) && !DEV44_R13_CRANK_4.get(ch)) continue;
            int from=ch*24*FLOATS_PER_VERTEX, to=Math.min(this.vertices.length,from+24*FLOATS_PER_VERTEX);
            int count=0;
            for (int j=from;j<to;j+=FLOATS_PER_VERTEX) {xs[ch]+=vertices[j];ys[ch]+=vertices[j+1];zs[ch]+=vertices[j+2];count++;}
            if(count>0){xs[ch]/=count;ys[ch]/=count;zs[ch]/=count;}
        }
        int[] near=new int[n];java.util.Arrays.fill(near,-1);
        for(int ch=DEV44_R13_ROD_4.nextSetBit(0);ch>=0;ch=DEV44_R13_ROD_4.nextSetBit(ch+1)){
            if(ch>=n)continue;
            double best=Double.POSITIVE_INFINITY;
            for(int k=DEV44_R13_CRANK_4.nextSetBit(0);k>=0;k=DEV44_R13_CRANK_4.nextSetBit(k+1)){
                if(k>=n)continue;
                float dx=xs[ch]-xs[k],dy=ys[ch]-ys[k],dz=zs[ch]-zs[k];
                double distance=dx*dx+dy*dy+dz*dz;
                if(distance<best){best=distance;near[ch]=k;}
            }
        }
        dev44R13kNearestCrank=near;
        dev44R13kCy=ys;dev44R13kCz=zs;dev44R13kCx=xs;
    }
    private boolean dev44R13jReported = false;
    // DEV44 R13h: immutable mesh-space chunk metadata, cached once per mesh instance.
    // Does not cache dynamic wheel phase, wheel transformations or movement data.
    private int[] dev44R13hRoles;
    private float[] dev44R13hCx, dev44R13hCy, dev44R13hCz;
    private void dev44R13hPrepare() {
        if (dev44R13hRoles != null) return;
        int n = (this.vertexCount + 23) / 24;
        int[] roles = new int[n];
        float[] cx = new float[n], cy = new float[n], cz = new float[n];
        String path = this.source.getPath();
        for (int ch = 0; ch < n; ch++) {
            roles[ch] = dev44R13Role(path, ch);
            if (roles[ch] == 0 || roles[ch] == 2) continue;
            int start = ch * 24 * FLOATS_PER_VERTEX;
            int end = Math.min(this.vertices.length, start + 24 * FLOATS_PER_VERTEX);
            int count = 0;
            for (int j = start; j < end; j += FLOATS_PER_VERTEX) {
                cx[ch] += this.vertices[j];
                cy[ch] += this.vertices[j+1];
                cz[ch] += this.vertices[j+2];
                count++;
            }
            if (count > 0) { cx[ch] /= count; cy[ch] /= count; cz[ch] /= count; }
        }
        dev44R13hCx = cx; dev44R13hCy = cy; dev44R13hCz = cz;
        dev44R13hRoles = roles; // publish after all arrays are populated
    }
    // DEV44 R13g: frame-cost sampler; independent of the wheel animator and motion solver.
    // Sample 120 render invocations per mesh instance, then write ONE aggregated row.
    private long dev44R13gCount, dev44R13gTotalNs, dev44R13gMaxNs;
    private static boolean dev44R13gProfileOn() {
        try {
            var p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13G-PROFILE-ON.txt");
            return java.nio.file.Files.isRegularFile(p)
                    && java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)
                       .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private void dev44R13gRecord(long started, boolean preview, boolean audit) {
        if (started == 0L) return;
        long delta = System.nanoTime() - started;
        if (delta < 0L) return;
        dev44R13gCount++;
        dev44R13gTotalNs += delta;
        if (delta > dev44R13gMaxNs) dev44R13gMaxNs = delta;
        if (dev44R13gCount < 120L) return;
        String name = this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        String mode = audit ? "C_DIAGNOSTIC" : (preview ? "B_LINKAGE" : "A_BASELINE");
        try {
            var folder = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13g-Performance");
            java.nio.file.Files.createDirectories(folder);
            var path = folder.resolve("performance.csv");
            synchronized (DEV44_R13G_WRITE_LOCK) {
                boolean header = !java.nio.file.Files.isRegularFile(path);
                if (header) java.nio.file.Files.writeString(path,
                        "mesh,mode,invocations,meanRenderMs,maxRenderMs,vertexCount\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
                java.nio.file.Files.writeString(path,
                        name+","+mode+","+dev44R13gCount+","+(dev44R13gTotalNs/1000000.0/dev44R13gCount)+","+(dev44R13gMaxNs/1000000.0)+","+this.vertexCount+"\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception e) { LOGGER.warn("[TC-DEV44-R13g] timing report write failed: {}", e.toString()); }
        dev44R13gCount=0L;dev44R13gTotalNs=0L;dev44R13gMaxNs=0L;
    }
    private static final Object DEV44_R13G_WRITE_LOCK = new Object();
    // DEV44 R13f: diagnostic-only fleet audit. Piston-labelled chunks are fixed shells.
    private static final java.util.Set<String> DEV44_R13F_HEADERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long dev44R13fFrames;
    private static boolean dev44R13fOn() {
        try {
            var marker = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13F-FULL-FLEET-AUDIT-ON.txt");
            return java.nio.file.Files.isRegularFile(marker)
                    && java.nio.file.Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim().equalsIgnoreCase("ON");
        } catch (Exception ignored) { return false; }
    }
    private static synchronized void dev44R13fLine(String name, String line) {
        try {
            var root = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("DEV44-R13f-Full-Fleet-Audit");
            java.nio.file.Files.createDirectories(root);
            java.nio.file.Files.writeString(root.resolve(name), line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOGGER.warn("[TC-DEV44-R13f] report write failed: {}", ex.toString());
        }
    }
    private void dev44R13fCapture(String meshKey,
            java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels) {
        if (!dev44R13fOn() || !this.dev44R11Verified) return;
        String mesh = this.source.getPath().substring(this.source.getPath().lastIndexOf('/')+1).replace(".tcm", "");
        long frame = ++dev44R13fFrames;
        boolean first = DEV44_R13F_HEADERS.add(mesh);
        if (first) {
            dev44R13fLine("fleet-summary.csv", "MESH,"+mesh+",WHEELS,"+wheels.size()+",STATUS,OBSERVED_NOT_VISUALLY_ACCEPTED");
            dev44R13fLine(mesh+".csv", "type,frame,chunk,role,centerX,centerY,centerZ,nearestWheelX,nearestWheelY,nearestWheelZ,axis,phaseCos,phaseSin,centerDistance,classification");
            LOGGER.info("[TC-DEV44-R13f-FLEET] observed={} wheels={} pistonShellsAreStationary=true", mesh, wheels.size());
        }
        if (!first && frame % 40 != 0) return;
        int total = (this.vertexCount+23)/24;
        int shellCount=0,rodCount=0,crankCount=0;
        for(int ch=0;ch<total;ch++) {
            int role=dev44R13Role(this.source.getPath(),ch);
            if(role==0) continue;
            if(role==1)rodCount++; else if(role==2)shellCount++; else if(role==3)crankCount++;
            int start=ch*24*FLOATS_PER_VERTEX;
            int end=Math.min(this.vertices.length,start+24*FLOATS_PER_VERTEX);
            float x=0,y=0,z=0;int count=0;
            for(int i=start;i<end;i+=FLOATS_PER_VERTEX){x+=this.vertices[i];y+=this.vertices[i+1];z+=this.vertices[i+2];count++;}
            if(count==0)continue;
            x/=count;y/=count;z/=count;
            var best = wheels.isEmpty()?null:dev44R13NearestWheel(wheels,x,y,z,wheels.get(0));
            String nearest = best==null ? ",,,,,,," : ","+best.centerX()+","+best.centerY()+","+best.centerZ()+","+best.axis()+","+best.cos()+","+best.sin()+","+Math.sqrt(Math.pow(x-best.centerX(),2)+Math.pow(y-best.centerY(),2)+Math.pow(z-best.centerZ(),2));
            String type=role==2 ? "FIXED_SHELL" : (role==1 ? "ROD_CANDIDATE" : "CRANK_CANDIDATE");
            String note=role==2 ? "NO_LOCAL_ANIMATION_EXPECTED" : (best==null ? "NO_ROTATING_WHEEL_SPEC" : "UNVERIFIED_ATTACHMENT_CANDIDATE");
            dev44R13fLine(mesh+".csv",type+","+frame+","+ch+","+role+","+x+","+y+","+z+nearest+","+note);
        }
        if(first) dev44R13fLine("fleet-summary.csv", "PARTS,"+mesh+",RODS,"+rodCount+",SHELLS,"+shellCount+",CRANKS,"+crankCount);
        if(first || frame%40==0) for(int i=0;i<wheels.size();i++){
            var w=wheels.get(i);
            dev44R13fLine(mesh+".csv","WHEEL_PHASE,"+frame+","+i+",0,,,,"+w.centerX()+","+w.centerY()+","+w.centerZ()+","+w.axis()+","+w.cos()+","+w.sin()+",,ACCEPTED_WHEEL_PHASE_READ_ONLY");
        }
    }
    private int dev44R13AnchorChunk = -2;
    private int dev44R13LastLoggedState = -1;
    private int dev44R13FindWheelAnchor(String meshKey) {
        if (dev44R13AnchorChunk != -2) return dev44R13AnchorChunk;
        // Cache the wheel chunk ID, not its pose, so phase always follows the
        // accepted existing wheel animator on every rendered frame.
        for (int chunk = 0; chunk < (this.vertexCount+23)/24; chunk++) {
            var spec = traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey, chunk, this.vertices, FLOATS_PER_VERTEX);
            if (spec != null && spec.rotates() && !dev44R10Mapped(this.source.getPath(), chunk)) {
                dev44R13AnchorChunk = chunk;
                break;
            }
        }
        // No wheel context in inventory/early render: retry next render.
        return dev44R13AnchorChunk == -2 ? -1 : dev44R13AnchorChunk;
    }
    // DEV44 R13b preview: wheel center is fixed; eccentric crank location orbits it.
    // Side-specific reference is inferred from identified crank geometry where available.
    // Rods and crossheads move as groups and never acquire a second clock.
    private static traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec
    dev44R13NearestWheel(java.util.List<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> wheels,
                        float x,float y,float z,
                        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec fallback) {
        var best=fallback;
        double distance=Double.POSITIVE_INFINITY;
        for (var wheel:wheels) {
            if (wheel.axis()!=fallback.axis()) continue;
            // Penalize wrong side heavily, and use squared physical distance within a side.
            double dx=x-wheel.centerX(),dy=y-wheel.centerY(),dz=z-wheel.centerZ();
            double axial=wheel.axis()==0?dx:(wheel.axis()==1?dy:dz);
            double plane=dx*dx+dy*dy+dz*dz-axial*axial;
            double d=plane+axial*axial*16.0;
            if(d<distance) {distance=d;best=wheel;}
        }
        return best;
    }

    private static float[] dev44R13Motion(
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec wheel,
            int role, float partX, float partY, float partZ,
            float crankX, float crankY, float crankZ, boolean crankFound) {
        float c=wheel.cos(), s=wheel.sin();
        float x=crankFound ? crankX : partX;
        float y=crankFound ? crankY : partY;
        float z=crankFound ? crankZ : partZ;
        // Measured eccentric relative to the accepted wheel center; bounded against
        // malformed labels that select cab/body geometry instead of an actual pin.
        float u=wheel.axis()==0 ? y-wheel.centerY() : x-wheel.centerX();
        float v=wheel.axis()==2 ? y-wheel.centerY() : z-wheel.centerZ();
        float length=(float)Math.hypot(u,v);
        if (!Float.isFinite(length) || length < 0.001f) {u=0.06f;v=0.0f;length=0.06f;}
        float radius=Math.min(0.16f,Math.max(0.02f,length));
        u*=radius/length;v*=radius/length;
        // Exact eccentric translation in the wheel's own rotation plane.
        float du=u*(c-1.0f)-v*s;
        float dv=u*s+v*(c-1.0f);
        float advance=du;
        float rise=dv;
        if (role==2) {
            // Crosshead constrained to its guide: slide in locomotive longitudinal axis.
            // Approximate slider-crank closure with a conservative rod length.
            float rodLength=Math.max(0.4f,radius*3.0f);
            float initial=(float)Math.sqrt(Math.max(0.0f,rodLength*rodLength-v*v));
            float movedV=u*s+v*c;
            float movedU=u*c-v*s;
            float current=(float)Math.sqrt(Math.max(0.0f,rodLength*rodLength-movedV*movedV));
            advance=(movedU+current)-(u+initial);
            rise=0;
        } else if (role==1) {
            // Conservative rigid rod approximation: fixed-end / crank-end midpoint.
            // Exact rod-end orientation is not provable from the chunk labels alone.
            advance*=0.5f;rise*=0.5f;
        }
        if (wheel.axis()==0) return new float[]{0,advance,rise};
        if (wheel.axis()==1) return new float[]{advance,0,rise};
        return new float[]{advance,rise,0};
    }

    private static boolean dev44R10Mapped(String source, int chunk) {
        String meshName = source.replace("legacy_meshes/", "").replace(".tcm", "");
        if (meshName.equals("locomotive_steam_262t")) return DEV44_R10_IDS_0.get(chunk);
        if (meshName.equals("locomotive_steam_4_4_0")) return DEV44_R10_IDS_1.get(chunk);
        if (meshName.equals("locomotive_steam_a4_mallard")) return DEV44_R10_IDS_2.get(chunk);
        if (meshName.equals("locomotive_steam_adler")) return DEV44_R10_IDS_3.get(chunk);
        if (meshName.equals("locomotive_steam_alco_sc4")) return DEV44_R10_IDS_4.get(chunk);
        if (meshName.equals("locomotive_steam_alice_0_4_0")) return DEV44_R10_IDS_5.get(chunk);
        if (meshName.equals("locomotive_steam_berkshire_1225")) return DEV44_R10_IDS_6.get(chunk);
        if (meshName.equals("locomotive_steam_berkshire_765")) return DEV44_R10_IDS_7.get(chunk);
        if (meshName.equals("locomotive_steam_br80")) return DEV44_R10_IDS_8.get(chunk);
        if (meshName.equals("locomotive_steam_br_black_5")) return DEV44_R10_IDS_9.get(chunk);
        if (meshName.equals("locomotive_steam_br_britannia")) return DEV44_R10_IDS_10.get(chunk);
        if (meshName.equals("locomotive_steam_c11")) return DEV44_R10_IDS_11.get(chunk);
        if (meshName.equals("locomotive_steam_c41")) return DEV44_R10_IDS_12.get(chunk);
        if (meshName.equals("locomotive_steam_c41_080")) return DEV44_R10_IDS_13.get(chunk);
        if (meshName.equals("locomotive_steam_c41t")) return DEV44_R10_IDS_14.get(chunk);
        if (meshName.equals("locomotive_steam_climax")) return DEV44_R10_IDS_15.get(chunk);
        if (meshName.equals("locomotive_steam_climax_2")) return DEV44_R10_IDS_16.get(chunk);
        if (meshName.equals("locomotive_steam_coronation_class")) return DEV44_R10_IDS_17.get(chunk);
        if (meshName.equals("locomotive_steam_fowler")) return DEV44_R10_IDS_18.get(chunk);
        if (meshName.equals("locomotive_steam_gs4")) return DEV44_R10_IDS_19.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_101_class")) return DEV44_R10_IDS_20.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_42xx")) return DEV44_R10_IDS_21.get(chunk);
        if (meshName.equals("locomotive_steam_gwr_72xx")) return DEV44_R10_IDS_22.get(chunk);
        if (meshName.equals("locomotive_steam_hall_class")) return DEV44_R10_IDS_23.get(chunk);
        if (meshName.equals("locomotive_steam_j50")) return DEV44_R10_IDS_24.get(chunk);
        if (meshName.equals("locomotive_steam_king_class")) return DEV44_R10_IDS_25.get(chunk);
        if (meshName.equals("locomotive_steam_lssp7")) return DEV44_R10_IDS_26.get(chunk);
        if (meshName.equals("locomotive_steam_midland_compound")) return DEV44_R10_IDS_27.get(chunk);
        if (meshName.equals("locomotive_steam_milw_class_a")) return DEV44_R10_IDS_28.get(chunk);
        if (meshName.equals("locomotive_steam_pannier")) return DEV44_R10_IDS_29.get(chunk);
        if (meshName.equals("locomotive_steam_pe")) return DEV44_R10_IDS_30.get(chunk);
        if (meshName.equals("locomotive_steam_rw_type_2")) return DEV44_R10_IDS_31.get(chunk);
        if (meshName.equals("locomotive_steam_rw_type_3")) return DEV44_R10_IDS_32.get(chunk);
        if (meshName.equals("locomotive_steam_s100_uk")) return DEV44_R10_IDS_33.get(chunk);
        if (meshName.equals("locomotive_steam_s100_us")) return DEV44_R10_IDS_34.get(chunk);
        if (meshName.equals("locomotive_steam_sentinel_y3")) return DEV44_R10_IDS_35.get(chunk);
        if (meshName.equals("locomotive_steam_shay_3_truck")) return DEV44_R10_IDS_36.get(chunk);
        if (meshName.equals("locomotive_steam_skookum")) return DEV44_R10_IDS_37.get(chunk);
        if (meshName.equals("locomotive_steam_southern1102")) return DEV44_R10_IDS_38.get(chunk);
        if (meshName.equals("locomotive_steam_u57")) return DEV44_R10_IDS_39.get(chunk);
        if (meshName.equals("locomotive_steam_vb_0_4_0")) return DEV44_R10_IDS_40.get(chunk);
        if (meshName.equals("locomotive_steam_vb_shay")) return DEV44_R10_IDS_41.get(chunk);
        if (meshName.equals("locomotive_steam_vb_shay_2")) return DEV44_R10_IDS_42.get(chunk);
        if (meshName.equals("locomotive_steam_wwcp_062t")) return DEV44_R10_IDS_43.get(chunk);
        return false;
    }
    // R11: source asset identity guard, based on the exact files used in the offline inspector.
    // This does not certify the user's semantic rod/piston labels.
    private static String dev44R11ExpectedHash(String name) {
        if (name.equals("locomotive_steam_262t")) return "9DC0F4D11D58CD187BE62890C7E5B6565A157152D363D3441E4C3D307C60FB47";
        if (name.equals("locomotive_steam_4_4_0")) return "2ED8C998E1659A31D78EF855046161FB95613D5A5B370781EF783673AB063A2D";
        if (name.equals("locomotive_steam_a4_mallard")) return "172BA1D0F7180892D39733AB15CF539D7AFF1C586B6ABFBEDA52C1970FF84175";
        if (name.equals("locomotive_steam_adler")) return "3B3ABBB3F2D9AD777977AB703D504166D521533A00B06CAC5DD8AF774CECCC32";
        if (name.equals("locomotive_steam_alco_sc4")) return "EA6EF46A62DF7B89CFE8B1AC31C872ED14EEB12253EB91CB99FF3D59D1BAD24E";
        if (name.equals("locomotive_steam_alice_0_4_0")) return "DB87B7F1C6177269203B8D2025C7CBBD34FF6C0F441A1674731F11ECAD263771";
        if (name.equals("locomotive_steam_berkshire_1225")) return "9AD06147C0290FF22C33A568DDF664CCA3BAB00704824BB390DC960E4B818865";
        if (name.equals("locomotive_steam_berkshire_765")) return "8A5588375BC8D284B29DCBA73984BF4EA127232209DE03FDB6AA744B7828A6C4";
        if (name.equals("locomotive_steam_br80")) return "5396F886EA7AE2B05AFB6254F0320906EC0D660B12B025206F1E45764A0000A9";
        if (name.equals("locomotive_steam_br_black_5")) return "88B98EC7C86698DEE9614886515F04AA1FA9373EB83118DF4231FBA8C98AA328";
        if (name.equals("locomotive_steam_br_britannia")) return "163A759BDE575B7D4C6C170353905C8CB8F83788CFC56435247420B3494A0ED0";
        if (name.equals("locomotive_steam_c11")) return "62D540AD3AD66D4EC7056A125660BDCC20BB3DEBBC8081D640C5E4A7B90B4959";
        if (name.equals("locomotive_steam_c41")) return "37188413D060738EBDABB9ABF802B9643820D12EF399194E3FB0A94BA141B0C0";
        if (name.equals("locomotive_steam_c41_080")) return "93CCAE40DDADB884073D88A6CF79B53F6D2617B98D5A8737505AF9F281D06941";
        if (name.equals("locomotive_steam_c41t")) return "AE488515E44DDA3FB2BDF629E0252FEECB14D563C50A7898370993A9C0D5ACE1";
        if (name.equals("locomotive_steam_climax")) return "121896AF8C0E820E9BBF1F78A0CD22307604A7E3A1050D96FE92DC140D4B59ED";
        if (name.equals("locomotive_steam_climax_2")) return "3298F9A26416ABA3277BEAE6E6848D14513F603EA6B6B5B5EEF4B1B25B9C5A3F";
        if (name.equals("locomotive_steam_coronation_class")) return "3108D41AA92ABCE5E0D6319AFD593C42813FB00D2F7FA352782256CCD7BB3AD8";
        if (name.equals("locomotive_steam_fowler")) return "CEDCEFF96571A828E4B9C69D375AD69F63D105332BF48F7F4B3B11EE875F9A28";
        if (name.equals("locomotive_steam_gs4")) return "4B0CCC2822BEF70FC3664F07C7091FF6193741BC59F57B7F6DBB892FE93006C3";
        if (name.equals("locomotive_steam_gwr_101_class")) return "6C021E73387D5AE79A885F9CA5DDD5654FAFCFA16DAE52FC2B64F527059B5795";
        if (name.equals("locomotive_steam_gwr_42xx")) return "EEFE1FAE505DD708CFA8B47E391F831625EBEBA44858FF45055EFE215CDE90D4";
        if (name.equals("locomotive_steam_gwr_72xx")) return "6EF457F7B46CC0018B561904E3C984852712050C67AD05618EB34E9803C3CCC4";
        if (name.equals("locomotive_steam_hall_class")) return "713AD8BFF748E52573EB25B02A320F66766D4F1358ACD60B41A01FB6C9B48E06";
        if (name.equals("locomotive_steam_j50")) return "C0A8A051A1DCCC9982B7B292773CDA8CE1357076498AA6741302FE706DA574CE";
        if (name.equals("locomotive_steam_king_class")) return "69DA8E5507D314E6DDEAEA25C58DB157AD7C8CE8E3F68D256039C127B03324B9";
        if (name.equals("locomotive_steam_lssp7")) return "90D29EBF1278BB7EB3CC21D20C631A89056ED4A3503DD51BC3365ACFDF48A401";
        if (name.equals("locomotive_steam_midland_compound")) return "56A825A205C77326E52E5CF08755FCD9417244955D3281E2A54A75EDE2D20DBC";
        if (name.equals("locomotive_steam_milw_class_a")) return "CBA9DD89C1D287367E9762525A4C16393C10C81F9D95F304C807038539B32961";
        if (name.equals("locomotive_steam_pannier")) return "CB473F135E1B1BEC08EC620222382EC31B65420E4F01599D5E1DBC7280997EBC";
        if (name.equals("locomotive_steam_pe")) return "CC3D8BF40A34E695B06CC617EEEEE39FB1B76AAA510CEFAF16BED62B30E3B4B5";
        if (name.equals("locomotive_steam_rw_type_2")) return "8201E113277CC21ADC203976C04C792DBD090A19C67C5012DF57DC8F9BFE85B6";
        if (name.equals("locomotive_steam_rw_type_3")) return "E1AC625F31296CAA1EBFC7AABDE6001A18F437411795EF6CB8BC29FDC8F58A96";
        if (name.equals("locomotive_steam_s100_uk")) return "0361CEE0F5BBA4DA0EA541CACD9EE0C4615CF88ECDAE604AD9EA003687352FAE";
        if (name.equals("locomotive_steam_s100_us")) return "06BFAD8CDA5DE0472DD77B532353BC9D98FEC40DFA62637F6F56BC8D311B88C7";
        if (name.equals("locomotive_steam_sentinel_y3")) return "F2557379BD6465FB0C7A9ED71EDFB8DEF45A8CCD7FDC3F4106D98C8C0680CECA";
        if (name.equals("locomotive_steam_shay_3_truck")) return "37B23DEC217825D10F5C8E1B368503AD858F8FE7B1A4BC8B10A6D09612D3F71E";
        if (name.equals("locomotive_steam_skookum")) return "999CECAAA803E4F8CF147544B148A2405C2457B9147294051DF0859314445F0D";
        if (name.equals("locomotive_steam_southern1102")) return "CCD566FBE11B01C2F5784653A0CF405B9A7A7336F297C994C8302FC6A7259DFE";
        if (name.equals("locomotive_steam_u57")) return "9553FE1E20C6A2965A8251EFCD75A771E897AE1708BE1922A00085AB5B2F091A";
        if (name.equals("locomotive_steam_vb_0_4_0")) return "8094F590A8D49664A51272E90D0D8664CF37881C82383DE31F6D62C2A8AAAD40";
        if (name.equals("locomotive_steam_vb_shay")) return "1FC1209BC0288FB61671645FF3D7C2693E1C1A42115E63CF3C6DB0E8DA93A179";
        if (name.equals("locomotive_steam_vb_shay_2")) return "E68F978CA29FA3AA46ECBB6E65BF0367A17602A9846881A5AE8635D706656B50";
        if (name.equals("locomotive_steam_wwcp_062t")) return "04CE9253AED0850AF62E5A4895EE73ABB70EE066229B71BA251612DB5D1773CF";
        return null;
    }
    private final boolean dev44R11Verified;
    // R12a: once per state per loaded mesh; no per-frame log flood.
    private transient int dev44R12LastReportedMode = -1;
    private static String dev44R11Sha256(byte[] raw) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    // DEV54H15S: exact-GWR101 source-mesh rod motion, no new geometry.
    // The authored purple parts and the accepted wheels are never replaced.
    private static final String DEV54H15S_MESH="legacy_meshes/locomotive_steam_gwr_101_class.tcm";
    private static final boolean DEV54H15S_ON=dev54h15sMarker();
    private static final java.util.Set<String> DEV54H15S_REPORTED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean dev54h15sMarker(){
        try{
            var p=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
              .resolve("DEV54H15S-GWR101-ORIGINAL-RODS-ON.txt");
            return java.nio.file.Files.isRegularFile(p) &&
                java.nio.file.Files.readString(p,java.nio.charset.StandardCharsets.UTF_8)
                  .replace("\uFEFF","").trim().equals("DEV54H15S-ON-V1");
        }catch(Exception e){return false;}
    }
    // Mesh-authored GWR 101 rods: coupling segments and two piston sliders.
    // Verified from SHA-frozen original TCM bounds. 57/174/197 are fixed shells.
    // IDs 93/94 are crank fittings rotating about the +X driving axle.
    private static boolean dev54h15sMovingChunk(int id){
        return switch(id){
           case 59,60,61,62,63,64,72,73,74,75,76,93,94,146 -> true;
           default -> false;
        };
    }
    private static float[] dev54h15sSourceMotion(int id,float x,float y,float phase){
        final float r=.09375f;
        final float sn=(float)Math.sin(phase),cs=(float)Math.cos(phase);
        // Zero-angle is the original mesh pose: full revolution returns EXACTLY
        // to the source geometry, with no accumulated offsets or shape changes.
        switch(id){
          case 59,63,75,146: // original rods travel together, constant shape
             return new float[]{x-r*sn,y+r*(cs-1f)};
          case 60,72: { // original connecting rods pitch toward sliding crosshead
             float front=id==60?-1.75f:-1.875f;
             float length=id==60?1.0625f:1.0f;
             float angle=(float)Math.atan2(r*(cs-1f),length);
             float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
             float ax=x-front,ay=y-.3125f;
             return new float[]{front-r*sn+ax*c-ay*s,.3125f+ax*s+ay*c};
          }
          case 61,62,64,73,74,76: // original piston sliders: X only
             return new float[]{x-r*sn,y};
          case 93,94: { // imported crank fittings, original geometry rotates as a solid
             final float cx=.5f,cy=.3125f;
             float dx=x-cx,dy=y-cy;
             return new float[]{cx+dx*cs-dy*sn,cy+dx*sn+dy*cs};
          }
          default:return new float[]{x,y};
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x54434D31; // TCM1
    private static final int VERSION = 1;
    private static final int FLOATS_PER_VERTEX = 8;

    // DEV44 R9: client-only, opt-in triangle ray picker. Does not modify source assets.
    private static volatile boolean requestPick;
    private static volatile boolean lastP, lastH, lastJ, lastK, lastO;
    private static volatile String selectedMesh = "";
    private static volatile int selectedChunk = -1;
    private static volatile boolean hideSelected;
    private static double nearestHit = 12.0;
    private static String hitMesh = "";
    private static int hitChunk = -1;
    private static final Path PICK_LOG = Path.of("DEV44-R9-PICKED-PARTS.csv");
    // DEV54H12C: opt-in, client-session-only multi-part HIDE CANDIDATE picker.
    // P and H use the existing DEV44 ray picker. No original TCM or production rod mask is edited.
    // DEV54H15W: opt-in J50 source-part browser. A real P triangle is not
    // required: nearby verified J50 can be scanned one mesh chunk at a time.
    // It NEVER hides a part; existing H12C H and K still own hide and export.
    private static final boolean DEV54H15W_J50_PICK_ON=dev54h15tEnabled(
        "DEV54H15W-J50-PART-BROWSER-ON.txt","DEV54H15W-J50-V1");
    private static final String DEV54H15W_J50_MESH=
        "traincraft:legacy_meshes/locomotive_steam_j50.tcm";
    private static final int DEV54H15W_J50_CHUNKS=647; // source-verified: 15528 vertices/24
    private static volatile boolean dev54h15wLastLeft,dev54h15wLastRight;
    private static void dev54h15wJ50Browse(net.minecraft.client.Minecraft mc,
                                            boolean right,boolean shift){
        if(mc.level==null || mc.player==null) return;
        boolean nearby=!mc.level.getEntities(mc.player,mc.player.getBoundingBox().inflate(18.0d),
                e -> {var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
                      return id!=null && "traincraft:locomotive_steam_j50".equals(id.toString());})
                .isEmpty();
        if(!nearby){dev54h12cMessage("H15W J50: stand beside the J50 locomotive");return;}
        if(!DEV54H15W_J50_MESH.equals(selectedMesh)){
            selectedMesh=DEV54H15W_J50_MESH;
            selectedChunk=25;
        } else {
            int step=shift?10:1;
            selectedChunk=Math.floorMod(selectedChunk+(right?step:-step),DEV54H15W_J50_CHUNKS);
        }
        hideSelected=false;
        LOGGER.info("[DEV54H15W-J50-BROWSE] mesh={} chunk={} nearestEntity=YES step={} hide=MANUAL_H_ONLY",
                selectedMesh,selectedChunk,shift?10:1);
        dev54h12cMessage("J50 source part #"+selectedChunk+" [ / ] browse; H hide/unhide; K export");
    }
    private static final Path DEV54H12C_ON = Path.of("DEV54H12C-PICKER-ON.txt");
    private static final Path DEV54H12C_CSV = Path.of("DEV54H12C-HIDE-CANDIDATES.csv");
    private static final java.util.SortedMap<String, java.util.SortedSet<Integer>> dev54h12cHidden =
            new java.util.TreeMap<>();
    private static volatile Boolean dev54h12cEnabled;
    private static boolean dev54h12cOn() {
        Boolean cached = dev54h12cEnabled;
        if (cached != null) return cached;
        boolean valid = false;
        try {
            valid = java.nio.file.Files.isRegularFile(DEV54H12C_ON)
                    && java.nio.file.Files.readString(DEV54H12C_ON).trim().equals("DEV54H12C-MULTI-HIDE-REVIEW-V1");
        } catch (java.io.IOException ex) {
            LOGGER.warn("[DEV54H12C] Selector read failed", ex);
        }
        dev54h12cEnabled = valid;
        LOGGER.info("[DEV54H12C] multi picker={} selector={} csv={} original meshes unchanged",
                valid ? "ON" : "OFF", DEV54H12C_ON.toAbsolutePath().normalize(),
                DEV54H12C_CSV.toAbsolutePath().normalize());
        return valid;
    }
    private static boolean dev54h12cEligible(String mesh) {
        // Exclude accepted geometry from the preview, even if selected by old DEV44 tools.
        if (!mesh.startsWith("traincraft:legacy_meshes/locomotive_")) return false;
        return !mesh.endsWith("/locomotive_steam_berkshire_1225.tcm")
                && !mesh.endsWith("/locomotive_steam_berkshire_765.tcm")
                && !mesh.endsWith("/locomotive_steam_a4_mallard.tcm")
                && !mesh.endsWith("/locomotive_steam_king_class.tcm")
                && !mesh.endsWith("/locomotive_steam_262t.tcm")
                && !mesh.endsWith("/locomotive_steam_alice_0_4_0.tcm")
                && !mesh.endsWith("/locomotive_steam_vb_shay_2.tcm");
    }
    private static int dev54h12cCount() {
        int total = 0;
        for (java.util.SortedSet<Integer> chunks : dev54h12cHidden.values()) total += chunks.size();
        return total;
    }
    private static void dev54h12cMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(message), true);
    }
    private static void dev54h12cExport() {
        StringBuilder csv = new StringBuilder("mesh,chunk,action\n");
        for (var entry : dev54h12cHidden.entrySet()) {
            for (int id : entry.getValue()) csv.append(entry.getKey()).append(',').append(id)
                    .append(",HIDE_CANDIDATE\n");
        }
        try {
            java.nio.file.Files.writeString(DEV54H12C_CSV, csv.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[DEV54H12C-EXPORT] candidates={} csv={}", dev54h12cCount(),
                    DEV54H12C_CSV.toAbsolutePath().normalize());
            dev54h12cMessage("H12C: " + dev54h12cCount() + " hidden candidates saved (K export)");
        } catch (IOException ex) {
            LOGGER.warn("[DEV54H12C] Failed to export candidates", ex);
            dev54h12cMessage("H12C EXPORT FAILED: check latest.log");
        }
    }
    private static void dev54h12cToggleSelection() {
        if (selectedChunk < 0 || !dev54h12cEligible(selectedMesh)) {
            dev54h12cMessage("H12C: select an unfinished locomotive mesh piece with P");
            return;
        }
        java.util.SortedSet<Integer> chunks = dev54h12cHidden.computeIfAbsent(
                selectedMesh, unused -> new java.util.TreeSet<>());
        boolean hiding = chunks.add(selectedChunk);
        if (!hiding) chunks.remove(selectedChunk);
        if (chunks.isEmpty()) dev54h12cHidden.remove(selectedMesh);
        hideSelected = false; // Bypass old DEV44 single-chunk hide; new session list owns preview.
        LOGGER.info("[DEV54H12C-{}] mesh={} chunk={} total={}", hiding ? "HIDE" : "SHOW",
                selectedMesh, selectedChunk, dev54h12cCount());
        dev54h12cExport();
    }

    private static final float[][] corners = new float[4][3];
    // DEV58B9R4 release cleanup: do not register in-game DEV44 picker / DEV54
    // rod-editor keyboard controls. Keep mesh masks, saved rod data, renderers,
    // animations and all original train part materials unchanged.
    private static final class PickerEvents {
        @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.screen != null) return;
            long window = mc.getWindow().getWindow();
            dev54h15Keys(window);
            // Native editor owns its G/K/C/O controls. Do not run H12C export
            // or stale legacy mesh selection while editing a ModelPart vehicle.
            if(traincraft.client.renderer.animation.Dev54H15PNativeFleetEditor.isEditing()) return;
            boolean p = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_P);
            boolean g = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_G);
            boolean h = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_H);
            boolean j = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_J);
            boolean k = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_K);
            boolean o = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_O);
            boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
            boolean multi = dev54h12cOn();
            // H15W: J50 source inspector, only in H12C preview mode. [ / ] scan
            // ALL source chunks, including ones hidden behind another part.
            boolean wLeft=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_BRACKET);
            boolean wRight=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_BRACKET);
            if(multi && DEV54H15W_J50_PICK_ON){
                if(wLeft && !dev54h15wLastLeft)dev54h15wJ50Browse(mc,false,shift);
                if(wRight && !dev54h15wLastRight)dev54h15wJ50Browse(mc,true,shift);
            }
            dev54h15wLastLeft=wLeft;dev54h15wLastRight=wRight;

            boolean h15cEditor=DEV54H15C_ON && DEV54H15_ON && dev54h15Editing;
            dev54h15cCapture=h15cEditor && g && !dev54h15cLastG;
            // In H15C edit mode G is the only placement key. P continues
            // to work with the original DEV44 picker OUTSIDE the editor.
            if (h15cEditor || (!h15cEditor && p && !lastP)) {
                requestPick = true; nearestHit = 12.0; hitMesh=""; hitChunk=-1;
                dev54h15HitSource=null;
                dev54h15aFallbackMesh=null;
                dev54h15aFallbackDistance=Double.POSITIVE_INFINITY;
                if(DEV54H15B_ON){dev54h15bFallbackMesh=null;
                    dev54h15bFallbackDistance=Double.POSITIVE_INFINITY;
                    dev54h15bConsidered=0;dev54h15bPlaneHits=0;}
                if(h15cEditor){dev54h15cTargetMesh=null;
                    dev54h15cTargetDistance=Double.POSITIVE_INFINITY;
                    dev54h15cConsidered=0;
                    if(DEV54H15D_ON)dev54h15dLastProbe="no eligible mesh rendered";
                    if(DEV54H15E_ON){dev54h15eSnapMesh=null;
                        dev54h15eSnapPivot=null;
                        dev54h15eSnapDistance=Double.POSITIVE_INFINITY;
                        dev54h15eQualifiedSeen=0;}}
            }
            if (h && !lastH) {
                if (multi) dev54h12cToggleSelection();
                else if (selectedChunk >= 0) {
                    hideSelected = !hideSelected;
                    LOGGER.info("[TC-DEV44-R9] {} mesh={} chunk={}", hideSelected?"TEMP HIDE":"SHOW",selectedMesh,selectedChunk);
                }
            }
            if (j && !lastJ) {
                selectedMesh=""; selectedChunk=-1; hideSelected=false;
                LOGGER.info("[TC-DEV44-R9] Selection cleared; H12C multi-hide candidates preserved");
            }
            if (multi && k && !lastK) dev54h12cExport();
            if (multi && o && !lastO && shift) {
                dev54h12cHidden.clear(); hideSelected=false;
                LOGGER.info("[DEV54H12C-RESET] all temporary candidates cleared");
                dev54h12cExport();
            }
            lastP=p; lastH=h; lastJ=j; lastK=k; lastO=o;
            dev54h15cLastG=g;
        }
        @SubscribeEvent public void afterEntities(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !requestPick) return;
            requestPick = false;
            if(DEV54H15_ON && dev54h15Editing){
                if(DEV54H15F_ON){
                    if(dev54h15cCapture)dev54h15fCommit();
                    dev54h15cCapture=false;
                    return;
                }
                if(DEV54H15C_ON){
                    // Hover is intentionally non-mutating. Only a fresh G
                    // press commits a selection; no old DEV44 picker event.
                    dev54h15cHoverMesh=dev54h15cTargetMesh;
                    if(dev54h15cTargetMesh!=null){
                        dev54h15cHoverX=dev54h15cTargetX;
                        dev54h15cHoverY=dev54h15cTargetY;
                        dev54h15cHoverZ=dev54h15cTargetZ;
                    }
                    if(dev54h15cCapture){
                        if(dev54h15cTargetMesh!=null){
                            dev54h15HitSource=dev54h15cTargetMesh;
                            dev54h15HitX=dev54h15cTargetX;
                            dev54h15HitY=dev54h15cTargetY;
                            dev54h15HitZ=dev54h15cTargetZ;
                            if(DEV54H15D_ON)LOGGER.info("[DEV54H15D-HIT] {} stage={}",
                                  dev54h15dLastProbe,dev54h15Stage);
                            if(DEV54H15E_ON && dev54h15eAutomatic &&
                               dev54h15Stage==0 && dev54h15eQualifiedSeen>0){
                                if(dev54h15eSnapMesh!=null)dev54h15eClick();
                                else dev54h12cMessage("H15E no verified crankpin target; press V for manual");
                            }else dev54h15bClick();
                        }else{
                            if(DEV54H15E_ON && dev54h15eAutomatic &&
                               dev54h15Stage==0 && dev54h15eQualifiedSeen>0){
                                dev54h12cMessage("H15E verified axles nearby: aim at off-center pin; V = manual");
                                LOGGER.warn("[DEV54H15E-NO-SNAP] qualifiedMeshes={} frameProbe={}",
                                  dev54h15eQualifiedSeen,dev54h15dLastProbe);
                            }else if(DEV54H15D_ON){
                                String wanted=dev54h15Stage==1?"PISTON":
                                    (dev54h15bAxleMesh==null?"WHEEL CENTER":"CRANKPIN");
                                dev54h12cMessage("H15D no "+wanted+" target; look for the white selection cross");
                                LOGGER.warn("[DEV54H15D-NO-TARGET] eligibleMeshes={} wanted={} stage={} details={}",
                                        dev54h15cConsidered,wanted,dev54h15Stage,dev54h15dLastProbe);
                            }else{
                                dev54h12cMessage("H15C no wheel point; stand beside the train and aim again");
                                LOGGER.warn("[DEV54H15C-NO-TARGET] eligibleMeshes={} stage={} cameraRayMiss=true",
                                        dev54h15cConsidered,dev54h15Stage);
                            }
                        }
                    }
                    dev54h15cCapture=false;
                    return;
                }
                if(DEV54H15B_ON){
                    if(dev54h15bFallbackMesh!=null){
                        dev54h15HitSource=dev54h15bFallbackMesh;
                        dev54h15HitX=dev54h15bFallbackX;
                        dev54h15HitY=dev54h15bFallbackY;
                        dev54h15HitZ=dev54h15bFallbackZ;
                        LOGGER.info("[DEV54H15B-SNAP] model={} local=({},{},{}) stage={} distance={}",
                            dev54h15HitSource.source,dev54h15HitX,dev54h15HitY,
                            dev54h15HitZ,dev54h15Stage,dev54h15bFallbackDistance);
                    }
                    if(dev54h15HitSource!=null){dev54h15bClick();}
                    else{
                        dev54h12cMessage("H15B no target; aim at wheel face from side (see log)");
                        LOGGER.warn("[DEV54H15B-NO-TARGET] eligibleMeshes={} planeHits={} "
                            +"oldTriangleHit={} (send latest.log if repeat)",
                            dev54h15bConsidered,dev54h15bPlaneHits,hitChunk>=0);
                    }
                    return;
                }
                // Prefer the original triangle ray hit. If the visible rod
                // was synthesized after the TCM loop, use the side-plane
                // wheel/piston hit instead of reporting no mesh.
                boolean useFallback=DEV54H15A_ON && dev54h15aFallbackMesh!=null &&
                    (hitChunk<0 || dev54h15HitSource==null ||
                     (dev54h15Stage==1 && dev54h15SavedWheel!=null &&
                      !dev54h15SavedWheel.path().equals(dev54h15HitSource.source.getPath())));
                if(useFallback){
                    dev54h15HitSource=dev54h15aFallbackMesh;
                    dev54h15HitX=dev54h15aFallbackX;
                    dev54h15HitY=dev54h15aFallbackY;
                    dev54h15HitZ=dev54h15aFallbackZ;
                    LOGGER.info("[DEV54H15A-SNAP] mesh={} stage={} local=({},{},{}) rayRange={}",
                        dev54h15HitSource.source,dev54h15Stage,
                        dev54h15HitX,dev54h15HitY,dev54h15HitZ,
                        dev54h15aFallbackDistance);
                }
                if(useFallback || hitChunk>=0)dev54h15Click();
                else dev54h12cMessage(DEV54H15A_ON?
                     "H15A: no wheel/piston target; stand at the side and aim at wheel face":
                     "H15: no mesh under crosshair; aim closer and press P");
                return;
            }
            if (hitChunk >= 0 && (!dev54h12cOn() || dev54h12cEligible(hitMesh))) {
                selectedMesh=hitMesh; selectedChunk=hitChunk; hideSelected=false;
                LOGGER.info("[TC-DEV44-R9] PICKED mesh={} chunk={} range={}", selectedMesh,selectedChunk,nearestHit);
                Minecraft mc=Minecraft.getInstance();
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "DEV44 picked: " + selectedMesh + " #" + selectedChunk + " (H hide, J clear)"),true);
                try {
                    if (!Files.exists(PICK_LOG)) Files.writeString(PICK_LOG,"mesh,chunk,range\n",StandardOpenOption.CREATE);
                    Files.writeString(PICK_LOG,selectedMesh+","+selectedChunk+","+nearestHit+"\n",StandardOpenOption.APPEND);
                } catch (IOException ex) { LOGGER.warn("[TC-DEV44-R9] Cannot write selection CSV",ex); }
            } else {
                LOGGER.info("[TC-DEV44-R9] No visible legacy mesh triangle under crosshair (12-block reach)");
                Minecraft mc=Minecraft.getInstance();
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("DEV44: no mesh triangle hit"),true);
            }
        }
    }
    private static double hitTriangle(float[] a, float[] b, float[] c, Vector3f direction) {
        // Double-sided Moller-Trumbore ray intersection; ray origin is the camera.
        double e1x=b[0]-a[0],e1y=b[1]-a[1],e1z=b[2]-a[2];
        double e2x=c[0]-a[0],e2y=c[1]-a[1],e2z=c[2]-a[2];
        double px=direction.y()*e2z-direction.z()*e2y;
        double py=direction.z()*e2x-direction.x()*e2z;
        double pz=direction.x()*e2y-direction.y()*e2x;
        double det=e1x*px+e1y*py+e1z*pz;
        if (Math.abs(det)<1.0e-9) return Double.POSITIVE_INFINITY;
        double inv=1.0/det;
        double tx=-a[0],ty=-a[1],tz=-a[2];
        double u=(tx*px+ty*py+tz*pz)*inv;
        if (u<0 || u>1) return Double.POSITIVE_INFINITY;
        double qx=ty*e1z-tz*e1y,qy=tz*e1x-tx*e1z,qz=tx*e1y-ty*e1x;
        double v=(direction.x()*qx+direction.y()*qy+direction.z()*qz)*inv;
        if (v<0 || u+v>1) return Double.POSITIVE_INFINITY;
        double t=(e2x*qx+e2y*qy+e2z*qz)*inv;
        return t>0.001 ? t : Double.POSITIVE_INFINITY;
    }
    // DEV54F: opt-in rod-only capture visibility. Never persists any change to TCM or running gear.
    private boolean dev54fMaskVerified;
    // DEV54H1: opt-in, 262T only; null means use original rendered TCM wheel faces.
    private Dev54HRealWheelParts dev54hWheelParts;
    private Dev54H2FleetWheelParts dev54h2WheelParts;
    private final ResourceLocation source;
    private final float[] vertices;
    private final int vertexCount;
    private final float minX, minY, minZ, maxX, maxY, maxZ;
    private final int stabilizedZeroDimensions;

    private LegacyStaticMesh(ResourceLocation source, float[] vertices, int vertexCount,
                             float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ,
                             int stabilizedZeroDimensions, boolean r11Verified) {
        this.dev44R11Verified = r11Verified;
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
            try (InputStream raw = resource.open()) {
                byte[] rawBytes = raw.readAllBytes();
                String meshName = location.getPath().replace("legacy_meshes/", "").replace(".tcm", "");
                String expectedHash = dev44R11ExpectedHash(meshName);
                String runtimeHash = expectedHash == null ? null : dev44R11Sha256(rawBytes);
                boolean verified = expectedHash != null && expectedHash.equals(runtimeHash);
                if (expectedHash != null) LOGGER.info("[TC-DEV44-R12a-LOAD] mesh={} shaMatch={} actual={} expected={} (render hide state reported separately)", meshName, verified, runtimeHash, expectedHash);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(rawBytes)));
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
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized, verified);
                if(DEV58G11_B765_PATH.equals(location.getPath())) {
                    if(dev58g11r2LastLoadedB765 != null && dev58g11r2LastLoadedB765 != mesh)
                        dev58g11r2LastLoadedB765.dev58g11r2ReleaseGpu("REPLACED_MESH");
                    dev58g11r2LastLoadedB765=mesh;
                }
                if(dev58g11r4PilotMesh(location.getPath()) ||
                        dev58g11r5PilotMesh(location.getPath()) ||
                        dev58g11r6PilotMesh(location.getPath()) ||
                        dev58g11r7PilotMesh(location.getPath())) {
                    var previous = dev58g11r4LastLoaded.put(location.getPath(),mesh);
                    if(previous!=null && previous!=mesh)
                        previous.dev58g11r2ReleaseGpu("R6_REPLACED_MESH");
                }
                mesh.dev54fMaskVerified = traincraft.debug.Dev54FRodMask.matches(meshName, rawBytes);
                mesh.dev54hWheelParts = Dev54HRealWheelParts.fromSource(location, rawBytes, data, FLOATS_PER_VERTEX);
                mesh.dev54h2WheelParts = Dev54H2FleetWheelParts.fromSource(location, rawBytes, data, FLOATS_PER_VERTEX);
                LOGGER.info("[TC-LEGACY-MESH] mesh={} vertices={} quads={} bounds=({},{},{})..({},{},{}) zeroThicknessStabilized={}",
                        location, vertexCount, vertexCount / 4,
                        minX, minY, minZ, maxX, maxY, maxZ, stabilized, verified);
                return mesh;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Traincraft legacy mesh " + location, ex);
        }
    }

public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        final long dev44R13gStarted = dev44R13gProfileOn() ? System.nanoTime() : 0L;
        PoseStack.Pose pose = poseStack.last();
        // DEV58G7: no modification to the pose stack or the animation context.
        // BufferBuilder has a fast 14-field vertex writer in Minecraft 1.20.1.
        // Wrappers/other consumers retain the G5R3 chained path unchanged.
        if(dev58g8r1AllowedModel(this.source.getPath()) && dev58g8r1CacheHits>0 &&
                (dev58g8r1CacheHits%16384L)==0L) {
            LOGGER.info("[DEV58G8R2-PACKET-STATS] model={} chunks={} hits={} rebuilds={}",
                    this.source,dev58g8r1Packets.size(),dev58g8r1CacheHits,dev58g8r1CacheMisses);
            dev58g8r1CacheHits++; // avoid repeating the same milestone
        }
        final boolean dev58g7Direct = DEV58G7_FAST_VERTEX &&
                consumer instanceof com.mojang.blaze3d.vertex.BufferBuilder;
        final org.joml.Matrix4f dev58g7Position = pose.pose();
        final org.joml.Matrix3f dev58g7Normal = pose.normal();

        int activeChunk = -1;
        traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec transform = null;
        String meshKey = "legacy:" + this.source;
        dev54h15mBegin(meshKey);
        final Dev54H14Tags dev54h14Tags=DEV54H14_TAGS.get(this.source.getPath());
        final float dev54h14Phase=(DEV54H14_ON && dev54h14Tags!=null && this.dev44R11Verified)
             ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h13bPartPhase(meshKey)
             :Float.NaN;
        if(DEV54H14_ON && dev54h14Tags!=null && this.dev44R11Verified
                && Float.isFinite(dev54h14Phase) && this.dev54h14Rig==null
                && this.dev54h14Attempts++<50)
            this.dev54h14Rig=dev54h14Prepare(meshKey,dev54h14Tags);
        final boolean dev54h15Replacing=DEV54H15H_ON?
               dev54h15hHideGenerated(this.source.getPath()):
               dev54h15Override(this.source.getPath());
        // Source mask resolved ONCE per mesh render (not once per vertex).
        final boolean dev54h15hSourceMask=DEV54H15H_ON &&
               dev54h15hHideMesh(this.source.getPath());
        if(dev54h15hSourceMask && dev54h14Tags==null &&
           DEV54H15H_REPORT.add(this.source.getPath()+":NO-ROD-TAGS"))
            LOGGER.warn("[DEV54H15H-HOLD] model={} hideOriginal requested but "
                +"no certified rod-only chunk tags; original mesh preserved",
                this.source.getPath());
        final boolean dev54h14Active=DEV54H14_ON &&
                !(DEV54H15T_GWR_ON && DEV54H15T_GWR_MESH.equals(this.source.getPath())) &&
                !(DEV54H15W_GWR_ON && DEV54H15W_GWR_MESH.equals(this.source.getPath())) &&
                !dev54h15Replacing && this.dev44R11Verified &&
                dev54h14Tags!=null && this.dev54h14Rig!=null && Float.isFinite(dev54h14Phase)
                && !(DEV54H14A_ON && dev54h14aDeferred(this.source.getPath()));
        if(DEV54H14A_ON && DEV54H14A_REPORTED.add(this.source.getPath()+":RESET")){
            if(dev54h14aDeferred(this.source.getPath()))
                LOGGER.info("[DEV54H14A-DEFER] model={} genericReset=false "
                    +"sourceRodVisibility=RESTORED reason=modelSpecificMotionRequired",
                    this.source.getPath());
            else if(dev54h14Tags!=null)
                LOGGER.info("[DEV54H14A-{}] model={} correctedClosedFaces={} "
                    +"extraCrossheadOverrun=false visualPASS=NO",
                    dev54h14Active?"LIVE":"HOLD",this.source.getPath(),
                    !this.source.getPath().equals("legacy_meshes/locomotive_steam_c11.tcm"));
        }
        if(DEV54H14_ON && dev54h14Tags!=null &&
                DEV54H14_REPORTED.add(this.source.getPath()+(dev54h14Active?":LIVE":":HOLD")))
            LOGGER.info("[DEV54H14-{}] model={} wheelContext={} rods={} fixedPistons=UNCHANGED "
                 +"legacyRodsMasked={} actualVisualPASS=NO",
                 dev54h14Active?"LIVE":"HOLD",this.source.getPath(),Float.isFinite(dev54h14Phase),
                 dev54h14Tags.rods().length,dev54h14Active);
        boolean pickNow=requestPick;
        Vector3f ray=pickNow ? new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector()) : null;
        final boolean dev54h12cActive = dev54h12cOn() && dev54h12cEligible(this.source.toString());
        final java.util.SortedSet<Integer> dev54h12cMask = dev54h12cActive
                ? dev54h12cHidden.get(this.source.toString()) : null;
        // Persistent source-only selection, independent of H12C session preview.
        final java.util.Set<Integer> dev54h15lMask=dev54h15lMaskForModel(this.source.getPath());
        final java.util.Set<Integer> dev54h15tMask=dev54h15tHideSet(this.source.getPath());
        final java.util.Set<Integer> dev54h15uLongMaskActive=dev54h15uLongHideSet(this.source.getPath());

        final boolean r12Requested = dev44R12HideEnabled();
        final boolean r12Hide = this.dev44R11Verified && r12Requested;
        final int r12Mode = r12Hide ? 2 : (r12Requested ? 1 : 0);
        if (dev44R12LastReportedMode != r12Mode) {
            int suppressed = 0;
            if (r12Hide) {
                int lastChunk = (this.vertexCount - 1) / 24;
                for (int chunk = 0; chunk <= lastChunk; chunk++) {
                    if (dev44R10Mapped(this.source.getPath(), chunk)) suppressed++;
                }
            }
            LOGGER.info("[TC-DEV44-R13] mesh={} marker={} requested={} shaVerified={} hide={} suppressedChunks={} visibleHighlight={} cwd={}",
                    this.source, dev44R12MarkerPath().toAbsolutePath().normalize(), r12Requested,
                    this.dev44R11Verified, r12Hide, suppressed, false, // magenta R11 debug highlighting retired
                    java.nio.file.Path.of("").toAbsolutePath().normalize());
            dev44R12LastReportedMode = r12Mode;
        }
        final boolean r13jAlco = this.source.getPath().equals("legacy_meshes/locomotive_steam_alco_sc4.tcm")
                && this.dev44R11Verified && dev44R13jAlcoProofOn() && !dev54h12bSelected(this.source.getPath());
        if (r13jAlco && !dev44R13jReported) {
            LOGGER.info("[TC-DEV44-R13j-ALCO-SC4] mesh={} verified={} hide={} mode=AUTHORED-ROD-VISIBILITY-ONLY nativeWheelAnimation=UNCHANGED",
                    this.source, this.dev44R11Verified, r12Hide);
            dev44R13jReported = true;
        }
        // Explicit ALCO-only motion switch. The R13j proof switch still governs visibility.
        final boolean r13kAlco = r13jAlco && dev44R13kAlcoMotionOn() && !dev44R13lReviewOn() && !dev44R13mCaptureOn() && !dev44R13nOn() && !dev44R13oOn() && !dev44R13uOn();
        if(r13kAlco) {
            dev44R13kPrepare();
            if(!dev44R13kReported) {
                LOGGER.info("[TC-DEV44-R13k-ALCO-SC4] enabled=true mode=ACCEPTED-WHEEL-PHASE-ROD-TRANSLATION-PREVIEW otherMeshes=UNCHANGED fixedShells=UNCHANGED");
                dev44R13kReported=true;
            }
        }
        // DEV44 R13v: Berkshire 765 one-shot ACTUAL wheel pivot / model-chunk probe.
        // Read only; never changes poses, buffers, wheel phases, or animation state.
        if (this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && dev44R13vProbeOn() && dev44R13vProbePending.compareAndSet(true,false)) {
            try {
                dev44R13hPrepare();
                int max=(this.vertexCount+23)/24;
                LOGGER.info("[TC-DEV44-R13v] BEGIN mesh={} chunks={} wheel=ACCEPTED-ANIMATOR rodCrank=R13p-UNVERIFIED",
                        this.source.getPath(),max);
                for(int wi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    if(wi>=max)continue;
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,wi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null) { LOGGER.info("[TC-DEV44-R13v] WHEEL chunk={} spec=null",wi);continue; }
                    LOGGER.info("[TC-DEV44-R13v] WHEEL chunk={} rotates={} axis={} cx={} cy={} cz={} cos={} sin={}",
                        wi,w.rotates(),w.axis(),w.centerX(),w.centerY(),w.centerZ(),w.cos(),w.sin());
                }
                for(int ci:new int[]{348,359}) {
                    if(ci>=max)continue;
                    LOGGER.info("[TC-DEV44-R13v] PART chunk={} tagged={} role={} cx={} cy={} cz={} NOT_A_VERIFIED_JOINT",
                        ci,dev44R10Mapped(this.source.getPath(),ci),dev44R13hRoles[ci],
                        dev44R13hCx[ci],dev44R13hCy[ci],dev44R13hCz[ci]);
                }
                LOGGER.info("[TC-DEV44-R13v] END newMotion=OFF wheelAnimator=UNCHANGED");
            } catch(Exception ex) { LOGGER.warn("[TC-DEV44-R13v] probe error: {}",ex.toString()); }
        }
        // DEV44 R13o explicit-pair proof. Previous previews must not stack.
        // H9B overrides only OLD experimental poses on 765 while H9A remains
        // selected. They were seen in the supplied runtime log before H9A HOLD.
        final boolean dev54h9b765 = this.source.getPath().equals(
                "legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && dev54h9aEnabled() && dev54h9bEnabled();
        if(dev54h9b765 && DEV54H9B_REPORT.add(1))LOGGER.info(
                "[DEV54H9B-LEGACY-BYPASS] 765 only: suppressing old R13w"
                + " R13af/R13ab/R13ad/R13az and R13u/R13o experimental poses;"
                + " H9A solver/H7 wheels unchanged; source TCM untouched");
        final boolean r13wIsolate = dev44R13wOn()
                && this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && !dev54h9b765;
        final boolean dev54h10aRequested=dev54h10aSelected(this.source.getPath());
        final boolean dev54h11aRequested=dev54h11aSelected(this.source.getPath());
        final boolean r13uEnabled=dev44R13uOn() && this.dev44R11Verified
                && !r13wIsolate && !dev54h9b765 && !dev54h10aRequested && !dev54h11aRequested;
        final boolean r13oEnabled=dev44R13oOn() && this.dev44R11Verified
                && !r13uEnabled && !r13wIsolate && !dev54h9b765 && !dev54h10aRequested && !dev54h11aRequested;
        final java.util.HashMap<Integer,float[]> r13oOffsets=new java.util.HashMap<>();
        if(r13oEnabled) {
            dev44R13hPrepare();
            String r13oMesh=this.source.getPath().replace("legacy_meshes/","").replace(".tcm","");
            for(var pair:dev44R13oPairs()) {
                if(!pair.mesh().equals(r13oMesh))continue;
                int max=(this.vertexCount+23)/24;
                if(pair.rod()>=max||pair.crank()>=max||pair.wheel()>=max)continue;
                if(!dev44R10Mapped(this.source.getPath(),pair.rod()) ||
                   !dev44R10Mapped(this.source.getPath(),pair.crank()))continue;
                // Never move stationary cylinder-shell chunks.
                if(dev44R13hRoles[pair.rod()]==2||dev44R13hRoles[pair.crank()]==2)continue;
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,pair.wheel(),this.vertices,FLOATS_PER_VERTEX);
                if(w==null||!w.rotates())continue;
                float[] d=dev44R13oTranslation(w,dev44R13hCx[pair.crank()],dev44R13hCy[pair.crank()],dev44R13hCz[pair.crank()]);
                // Refuse conflicted chunks: never silently choose an attachment.
                if(r13oOffsets.containsKey(pair.rod())||r13oOffsets.containsKey(pair.crank())){
                    r13oOffsets.remove(pair.rod());r13oOffsets.remove(pair.crank());continue;
                }
                r13oOffsets.put(pair.rod(),d);r13oOffsets.put(pair.crank(),d);
            }
        }
        // DEV44 R13af: opt-in paired candidate piston/crosshead SLIDER movement.
        // Uses each side's actual accepted wheel phase. No rotation and NO fixed-cylinder motion.
        // Only 391 (+Z) and 1011 (-Z) are eligible; all remaining chunks are untouched.
        // Inferred role/slider travel: visual trial, NOT a physically pinned piston solver.
        if (dev44R13afOn() && r13wIsolate) {
            final int[] r13afChunks={391,1011};
            for (int r13afSide=0;r13afSide<2;r13afSide++) {
                final int r13afChunk=r13afChunks[r13afSide];
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13afWheel=null;
                for(int r13afWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var r13afCandidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                            meshKey,r13afWi,this.vertices,FLOATS_PER_VERTEX);
                    if(r13afCandidate==null || !r13afCandidate.rotates() || r13afCandidate.axis()!=2)continue;
                    if((r13afSide==0 && r13afCandidate.centerZ()<0.10f)
                            || (r13afSide==1 && r13afCandidate.centerZ()>-0.10f))continue;
                    if(Math.abs(r13afCandidate.centerX()+0.5f)<0.02f
                            && Math.abs(r13afCandidate.centerY()+0.5625f)<0.02f) {
                        r13afWheel=r13afCandidate;break;
                    }
                }
                if(r13afChunk<dev44R13hRoles.length && dev44R10Mapped(this.source.getPath(),r13afChunk)
                        && dev44R13hRoles[r13afChunk]!=2 && !r13oOffsets.containsKey(r13afChunk)
                        && r13afWheel!=null) {
                    // Small strictly axial stroke, centered around original rest pose.
                    final float r13afStroke=0.09375f*r13afWheel.cos();
                    r13oOffsets.put(r13afChunk,new float[]{r13afStroke,0f,0f});
                    int r13afQuad=Math.max(0,Math.min(3,(int)Math.floor(
                            (Math.atan2(r13afWheel.sin(),r13afWheel.cos())+Math.PI)/(Math.PI/2.0))));
                    if(dev44R13afLogged.add(r13afSide*10+r13afQuad))LOGGER.info(
                            "[TC-DEV44-R13af] side={} chunk={} SLIDER_CANDIDATE=APPLIED quadrant={} strokeX={} wheelZ={} fixedShells=UNTOUCHED",
                            r13afSide==0?"POS_Z":"NEG_Z",r13afChunk,r13afQuad,r13afStroke,r13afWheel.centerZ());
                } else if(dev44R13afLogged.add(90+r13afSide)) LOGGER.warn(
                        "[TC-DEV44-R13af] side={} chunk={} REJECTED: wheel/mapping/role/conflict guard",
                        r13afSide==0?"POS_Z":"NEG_Z",r13afChunk);
            }
        }

        // R13u computes each authored rod pose ONCE per render, never per vertex.
        final java.util.HashMap<Integer,Dev44R13uPose> r13uPoses=new java.util.HashMap<>();
        if(r13uEnabled) {
            dev44R13hPrepare();
            String current=this.source.getPath().replace("legacy_meshes/","").replace(".tcm","");
            java.util.HashSet<Integer> rejected=new java.util.HashSet<>();
            for(var j:dev44R13uRows()) {
                if(!j.mesh().equals(current)) continue;
                int max=(this.vertexCount+23)/24;
                if(j.rod()>=max||j.wheelA()>=max||j.wheelB()>=max)continue;
                if(!dev44R10Mapped(this.source.getPath(),j.rod()) || dev44R13hRoles[j.rod()]==2)continue;
                var wa=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,j.wheelA(),this.vertices,FLOATS_PER_VERTEX);
                var wb=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,j.wheelB(),this.vertices,FLOATS_PER_VERTEX);
                if(wa==null||wb==null||!wa.rotates()||!wb.rotates()||wa.axis()!=wb.axis()) continue;
                float[] ca={wa.centerX(),wa.centerY(),wa.centerZ()};
                float[] cb={wb.centerX(),wb.centerY(),wb.centerZ()};
                float[] da=Dev44RigidLinkage.wheelPoint(j.a(),ca,wa.cos(),wa.sin(),wa.axis());
                float[] db=Dev44RigidLinkage.wheelPoint(j.b(),cb,wb.cos(),wb.sin(),wb.axis());
                var p=Dev44RigidLinkage.solve(j.a(),j.b(),da,db,wa.axis(),0.015f);
                if(p==null) continue; // no stretching or forced fit
                if(r13uPoses.containsKey(j.rod())){rejected.add(j.rod());r13uPoses.remove(j.rod());}
                else if(!rejected.contains(j.rod()))r13uPoses.put(j.rod(),new Dev44R13uPose(p,j.a()));
            }
        }

        // DEV44 R13ab: Berkshire 765 rod348 candidate rest-geometry calibration.
        // Actual R13y sample: A eccentricity=(-0.046875,+0.09375), B=(+0.046875,+0.09375).
        // These are not verified crank pins and cannot both be assumed same-phase.
        // Do not hide/reject the whole rod on a misleading two-pin constraint.
        // Rigidly translate the EXISTING mesh by the measured A-wheel eccentric motion.
        // This preserves every vertex and its original length; B is NOT asserted attached.
        final boolean r13zBerkshire=dev44R13xOn() && r13wIsolate;
        if(r13zBerkshire) {
            final float[] a={-0.546875f,-0.46875f,0.34375f};
            final float[] b={ 1.984375f,-0.46875f,0.34375f};
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec wa=null,wb=null;
            // Match observed axle positions instead of relying on guessed chunk IDs.
            for(int wi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                        meshKey,wi,this.vertices,FLOATS_PER_VERTEX);
                if(w==null || !w.rotates() || w.axis()!=2)continue;
                if(Math.abs(w.centerX()+0.5f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f
                        && Math.abs(w.centerZ()-0.31313f)<0.04f)wa=w;
                if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f
                        && Math.abs(w.centerZ()-0.31313f)<0.04f)wb=w;
            }
            if(wa!=null && wb!=null && dev44R10Mapped(this.source.getPath(),348)
                    && dev44R13hRoles[348]!=2) {
                float[] ca={wa.centerX(),wa.centerY(),wa.centerZ()};
                // R13ab: R13aa measured phaseDelta=0 throughout all four quadrants.
                // In source-rest pose, A eccentricX=-0.046875, while B's proposed
                // mesh extremum eccentricX=+0.046875. Reconcile the *rest geometry*
                // of this ONE rod (not the mesh asset or the wheels) by 0.09375 blocks.
                // This is a candidate endpoint calibration, NOT a proved physical pin.
                final float[] bAligned={wb.centerX()+(a[0]-wa.centerX()),
                        wb.centerY()+(a[1]-wa.centerY()),b[2]};
                final float rodRestScaleX=(bAligned[0]-a[0])/(b[0]-a[0]);
                final float[] da=Dev44RigidLinkage.wheelPoint(a,ca,wa.cos(),wa.sin(),2);
                final float[] cb={wb.centerX(),wb.centerY(),wb.centerZ()};
                final float[] db=Dev44RigidLinkage.wheelPoint(bAligned,cb,wb.cos(),wb.sin(),2);
                var r13zLinkPose=Dev44RigidLinkage.solve(a,bAligned,da,db,2,0.0005f);
                double bxOffset=b[0]-wb.centerX(),byOffset=b[1]-wb.centerY();
                double axOffset=a[0]-wa.centerX(),ayOffset=a[1]-wa.centerY();
                double eccentricMismatch=Math.hypot(axOffset-bxOffset,ayOffset-byOffset);
                int quadrant=(int)Math.floor((Math.atan2(wa.sin(),wa.cos())+Math.PI)/(Math.PI/2.0));
                quadrant=Math.max(0,Math.min(3,quadrant));
                // R13aa: strictly observational second crankpin gap measurement.
                // Use actual B wheel phase, never synthesize a matching phase.
                // This does NOT affect R13z's accepted-looking rigid preview pose.
                float[] actualB=Dev44RigidLinkage.wheelPoint(bAligned,cb,wb.cos(),wb.sin(),2);
                double secondPinGap=Math.sqrt(
                        Math.pow(db[0]-actualB[0],2)+Math.pow(db[1]-actualB[1],2)
                        +Math.pow(db[2]-actualB[2],2));
                double phaseA=Math.atan2(wa.sin(),wa.cos());
                double phaseB=Math.atan2(wb.sin(),wb.cos());
                double phaseDelta=Math.atan2(Math.sin(phaseB-phaseA),Math.cos(phaseB-phaseA));
                if(dev44R13xLogged.add(quadrant))LOGGER.info(
                        "[TC-DEV44-R13ab] quadrant={} rod348={} mode=CALIBRATED_REST_GEOMETRY secondPin=CANDIDATE phaseA={} phaseB={} phaseDelta={} secondPinGap={} originalEccentricMismatch={} restScaleX={} lengthResidual={} centerA=({},{},{}) centerB=({},{},{})",
                        quadrant,r13zLinkPose==null?"REJECTED":"POSE_APPLIED",
                        phaseA,phaseB,phaseDelta,secondPinGap,eccentricMismatch,rodRestScaleX,
                        Math.abs(Math.hypot(db[0]-da[0],db[1]-da[1])-Math.hypot(bAligned[0]-a[0],bAligned[1]-a[1])),
                        wa.centerX(),wa.centerY(),wa.centerZ(),wb.centerX(),wb.centerY(),wb.centerZ());
                if(r13zLinkPose!=null)r13uPoses.put(348,new Dev44R13uPose(r13zLinkPose,a));
            } else if(dev44R13xLogged.add(9))LOGGER.warn(
                    "[TC-DEV44-R13ab] Rod348 preview rejected: matching animated wheel or safe mesh role absent. No motion.");
        }

        // DEV44 R13ad: isolated opposite-Z-side geometry trial for candidate rod733.
        // Endpoints are mirrored rest geometry of accepted rod348; physical pins remain UNVERIFIED.
        // This does NOT modify rod348 or the R13ab code path. Default switch OFF.
        if(dev44R13adOn() && r13wIsolate) {
            final float[] r13adA={-0.546875f,-0.46875f,-0.45937505f};
            final float[] r13adB={1.984375f,-0.46875f,-0.45937505f};
            traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13adWa=null,r13adWb=null;
            // Wheel ownership is established by the animated wheel's actual center on the -Z side.
            for(int r13adWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                var r13adW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(
                        meshKey,r13adWi,this.vertices,FLOATS_PER_VERTEX);
                if(r13adW==null || !r13adW.rotates() || r13adW.axis()!=2 || r13adW.centerZ()>-0.10f)continue;
                if(Math.abs(r13adW.centerX()+0.5f)<0.02f && Math.abs(r13adW.centerY()+0.5625f)<0.02f)
                    r13adWa=r13adW;
                if(Math.abs(r13adW.centerX()-1.9375f)<0.02f && Math.abs(r13adW.centerY()+0.5625f)<0.02f)
                    r13adWb=r13adW;
            }
            if(r13adWa!=null && r13adWb!=null && dev44R10Mapped(this.source.getPath(),733)
                    && dev44R13hRoles[733]!=2 && !r13uPoses.containsKey(733)) {
                float[] r13adCa={r13adWa.centerX(),r13adWa.centerY(),r13adWa.centerZ()};
                float[] r13adCb={r13adWb.centerX(),r13adWb.centerY(),r13adWb.centerZ()};
                final float[] r13adBAligned={r13adWb.centerX()+(r13adA[0]-r13adWa.centerX()),
                        r13adWb.centerY()+(r13adA[1]-r13adWa.centerY()),r13adB[2]};
                float[] r13adDa=Dev44RigidLinkage.wheelPoint(r13adA,r13adCa,r13adWa.cos(),r13adWa.sin(),2);
                float[] r13adDb=Dev44RigidLinkage.wheelPoint(r13adBAligned,r13adCb,r13adWb.cos(),r13adWb.sin(),2);
                var r13adLinkPose=Dev44RigidLinkage.solve(r13adA,r13adBAligned,r13adDa,r13adDb,2,0.0005f);
                double r13adPhaseA=Math.atan2(r13adWa.sin(),r13adWa.cos());
                double r13adPhaseB=Math.atan2(r13adWb.sin(),r13adWb.cos());
                double r13adPhaseDelta=Math.atan2(Math.sin(r13adPhaseB-r13adPhaseA),Math.cos(r13adPhaseB-r13adPhaseA));
                double r13adRestScaleX=(r13adBAligned[0]-r13adA[0])/(r13adB[0]-r13adA[0]);
                int r13adQuadrant=(int)Math.floor((r13adPhaseA+Math.PI)/(Math.PI/2.0));
                r13adQuadrant=Math.max(0,Math.min(3,r13adQuadrant));
                if(dev44R13adLogged.add(r13adQuadrant))LOGGER.info(
                        "[TC-DEV44-R13ad] quadrant={} rod733={} oppositeSide=CANDIDATE secondPin=UNVERIFIED phaseDelta={} restScaleX={} lengthResidual={} wheelZ_A={} wheelZ_B={}",
                        r13adQuadrant,r13adLinkPose==null?"REJECTED":"POSE_APPLIED",r13adPhaseDelta,
                        r13adRestScaleX,Math.abs(Math.hypot(r13adDb[0]-r13adDa[0],r13adDb[1]-r13adDa[1])
                                -Math.hypot(r13adBAligned[0]-r13adA[0],r13adBAligned[1]-r13adA[1])),
                        r13adWa.centerZ(),r13adWb.centerZ());
                if(r13adLinkPose!=null)r13uPoses.put(733,new Dev44R13uPose(r13adLinkPose,r13adA));
            } else if(dev44R13adLogged.add(9))LOGGER.warn(
                    "[TC-DEV44-R13ad] rod733 REJECTED: missing opposite-side wheel pair or role guard. No motion.");
        }

        // DEV44 R13al: BILATERAL main-rod joint consistency AUDIT.
        // Chunk 378/746 endpoints are mesh extrema and are NOT verified pin vertices.
        // Read-only by design: a rejected pose must NEVER flicker or distort a component.
        // Existing rod348/733 and slider391/1011 motion above is not modified.
        if (dev44R13alOn() && r13wIsolate) {
            for(int r13alSide=0;r13alSide<2;r13alSide++) {
                final int r13alChunk=r13alSide==0?378:746;
                final int r13alSlider=r13alSide==0?391:1011;
                final float r13alZ=r13alSide==0?0.40625f:-0.5218751f;
                final float[] r13alRestA={0.296875f,-0.53125f,r13alZ};
                final float[] r13alRestB={1.953125f,-0.53125f,r13alZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13alWheel=null;
                for(int r13alWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}){
                    var r13alW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,r13alWi,this.vertices,FLOATS_PER_VERTEX);
                    if(r13alW==null || !r13alW.rotates() || r13alW.axis()!=2)continue;
                    if(r13alSide==0 && r13alW.centerZ()<0.1f)continue;
                    if(r13alSide==1 && r13alW.centerZ()>-0.1f)continue;
                    if(Math.abs(r13alW.centerX()-1.9375f)<0.02f && Math.abs(r13alW.centerY()+0.5625f)<0.02f){r13alWheel=r13alW;break;}
                }
                if(r13alWheel==null || !dev44R10Mapped(this.source.getPath(),r13alChunk)
                        || !dev44R10Mapped(this.source.getPath(),r13alSlider)
                        || dev44R13hRoles[r13alChunk]==2 || !r13oOffsets.containsKey(r13alSlider)){
                    if(dev44R13alLogged.add(90+r13alSide))LOGGER.warn(
                        "[TC-DEV44-R13al] side={} chunk={} VALIDATION_UNAVAILABLE wheel/mesh/slider guard; no changes",
                        r13alSide==0?"POS_Z":"NEG_Z",r13alChunk);
                    continue;
                }
                float[] r13alPivot={r13alWheel.centerX(),r13alWheel.centerY(),r13alWheel.centerZ()};
                float[] r13alB=Dev44RigidLinkage.wheelPoint(r13alRestB,r13alPivot,r13alWheel.cos(),r13alWheel.sin(),2);
                float[] r13alStroke=r13oOffsets.get(r13alSlider);
                float[] r13alA={r13alRestA[0]+r13alStroke[0],r13alRestA[1],r13alRestA[2]};
                double r13alRestLength=Math.hypot(r13alRestB[0]-r13alRestA[0],r13alRestB[1]-r13alRestA[1]);
                double r13alLiveLength=Math.hypot(r13alB[0]-r13alA[0],r13alB[1]-r13alA[1]);
                double r13alResidual=Math.abs(r13alLiveLength-r13alRestLength);
                int r13alQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(r13alWheel.sin(),r13alWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13alLogged.add(r13alSide*10+r13alQuad))LOGGER.info(
                    "[TC-DEV44-R13al] side={} chunk={} slider={} quadrant={} candidateRestLength={} candidateLiveLength={} candidateResidual={} sliderStroke={} status={} movingRod=DISABLED fixedShells=UNTOUCHED",
                    r13alSide==0?"POS_Z":"NEG_Z",r13alChunk,r13alSlider,r13alQuad,
                    r13alRestLength,r13alLiveLength,r13alResidual,r13alStroke[0],
                    r13alResidual<=0.015?"CANDIDATE_POSSIBLE":"REJECTED_GEOMETRY");
            }
        }

        // DEV44 R13am: bilateral constant-length slider-crank SOLUTION AUDIT.
        // Geometry candidates are mesh extrema, NOT verified crankpin vertices.
        // Never alter existing rod348/733, slider391/1011, or static cylinder meshes.
        if (dev44R13amOn() && r13wIsolate) {
            for (int amSide=0;amSide<2;amSide++) {
                final int amRod=amSide==0?378:746, amSlider=amSide==0?391:1011;
                final float amZ=amSide==0?0.40625f:-0.5218751f;
                final float[] amA={0.296875f,-0.53125f,amZ};
                final float[] amB={1.953125f,-0.53125f,amZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec amWheel=null;
                for (int amWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var amW=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,amWi,this.vertices,FLOATS_PER_VERTEX);
                    if(amW==null || !amW.rotates() || amW.axis()!=2)continue;
                    if(amSide==0 && amW.centerZ()<0.1f)continue;
                    if(amSide==1 && amW.centerZ()>-0.1f)continue;
                    if(Math.abs(amW.centerX()-1.9375f)<0.02f && Math.abs(amW.centerY()+0.5625f)<0.02f){amWheel=amW;break;}
                }
                if(amWheel==null || !dev44R10Mapped(this.source.getPath(),amRod)
                    || !dev44R10Mapped(this.source.getPath(),amSlider)
                    || dev44R13hRoles[amRod]==2 || !r13oOffsets.containsKey(amSlider)) {
                    if(dev44R13amLogged.add(90+amSide)) LOGGER.warn(
                        "[TC-DEV44-R13am] side={} rod={} GUARD_REJECTED no source geometry modification",amSide==0?"POS_Z":"NEG_Z",amRod);
                    continue;
                }
                float[] amCenter={amWheel.centerX(),amWheel.centerY(),amWheel.centerZ()};
                float[] amLiveB=Dev44RigidLinkage.wheelPoint(amB,amCenter,amWheel.cos(),amWheel.sin(),2);
                final double amRestL=Math.hypot(amB[0]-amA[0],amB[1]-amA[1]);
                final double amDy=amLiveB[1]-amA[1];
                final double amDisc=amRestL*amRestL-amDy*amDy;
                if(amDisc < 0) {
                    if(dev44R13amLogged.add(80+amSide)) LOGGER.warn(
                        "[TC-DEV44-R13am] side={} rod={} IMPOSSIBLE_LINKAGE discriminant={} movingRod=DISABLED",
                        amSide==0?"POS_Z":"NEG_Z",amRod,amDisc);
                    continue;
                }
                final double amSolveAX=amLiveB[0]-Math.sqrt(amDisc);
                final double amSolvedStroke=amSolveAX-amA[0];
                final double amAcceptedStroke=r13oOffsets.get(amSlider)[0];
                final double amStrokeDisagreement=Math.abs(amSolvedStroke-amAcceptedStroke);
                final double amClosure=Math.abs(Math.hypot(amLiveB[0]-amSolveAX,amLiveB[1]-amA[1])-amRestL);
                final int amQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(amWheel.sin(),amWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13amLogged.add(amSide*10+amQuad)) LOGGER.info(
                    "[TC-DEV44-R13am] side={} rod={} slider={} quadrant={} restLength={} solvedStroke={} acceptedStroke={} strokeDisagreement={} closureResidual={} status={} movingRod=DISABLED acceptedSlider=UNCHANGED fixedShells=UNTOUCHED",
                    amSide==0?"POS_Z":"NEG_Z",amRod,amSlider,amQuad,amRestL,amSolvedStroke,
                    amAcceptedStroke,amStrokeDisagreement,amClosure,
                    amStrokeDisagreement<=0.015?"COMPATIBLE_CANDIDATE":"SLIDER_MAPPING_MISMATCH");
            }
        }

        // DEV44 R13an bilateral attachment calibration. Read-only; no vertex edits.
        // Find the wheel-side X coordinate required for the ACCEPTED piston-slider
        // motion to close a constant-length linkage at the candidate wheel-side Y.
        // Candidate mesh extrema are not proven physical crankpin/crosshead joints.
        if (dev44R13anOn() && r13wIsolate) {
            for (int anSide=0; anSide<2; anSide++) {
                final int anRod=anSide==0?378:746, anSlider=anSide==0?391:1011;
                final float anZ=anSide==0?0.40625f:-0.5218751f;
                final float anRestAx=0.296875f, anRestAy=-0.53125f;
                final float[] anRestB={1.953125f,-0.53125f,anZ};
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec anWheel=null;
                for (int anIdx: new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var anSpec=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,anIdx,this.vertices,FLOATS_PER_VERTEX);
                    if(anSpec==null || !anSpec.rotates() || anSpec.axis()!=2) continue;
                    if(anSide==0 && anSpec.centerZ()<0.1f)continue;
                    if(anSide==1 && anSpec.centerZ()>-0.1f)continue;
                    if(Math.abs(anSpec.centerX()-1.9375f)<0.02f && Math.abs(anSpec.centerY()+0.5625f)<0.02f){anWheel=anSpec;break;}
                }
                if(anWheel==null || !dev44R10Mapped(this.source.getPath(),anRod)
                    || !dev44R10Mapped(this.source.getPath(),anSlider)
                    || dev44R13hRoles[anRod]==2 || !r13oOffsets.containsKey(anSlider)) {
                    if(dev44R13anLogged.add(90+anSide))LOGGER.warn(
                        "[TC-DEV44-R13an] side={} GUARD_REJECTED no geometry change",anSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                final float[] anCenter={anWheel.centerX(),anWheel.centerY(),anWheel.centerZ()};
                final float[] anLiveB=Dev44RigidLinkage.wheelPoint(anRestB,anCenter,anWheel.cos(),anWheel.sin(),2);
                final double anLength=anRestB[0]-anRestAx;
                final double anAcceptedAx=anRestAx+r13oOffsets.get(anSlider)[0];
                final double anDy=anLiveB[1]-anRestAy;
                final double anDx2=anLength*anLength-anDy*anDy;
                if(anDx2<0){if(dev44R13anLogged.add(80+anSide))LOGGER.warn(
                    "[TC-DEV44-R13an] side={} IMPOSSIBLE_CANDIDATE no animation",anSide==0?"POS_Z":"NEG_Z");continue;}
                final double anRequiredBx=anAcceptedAx+Math.sqrt(anDx2);
                final double anRequiredShiftX=anRequiredBx-anLiveB[0];
                // A true physical crankpin does not arbitrarily shift relative to its wheel.
                // The varying required shift flags incorrect assumed endpoints or slider mapping.
                final double anClosureAtAccepted=Math.abs(Math.hypot(anLiveB[0]-anAcceptedAx,anDy)-anLength);
                final double anPhase=Math.atan2(anWheel.sin(),anWheel.cos());
                final int anQuadrant=Math.max(0,Math.min(3,(int)Math.floor((anPhase+Math.PI)/(Math.PI/2))));
                if(dev44R13anLogged.add(anSide*10+anQuadrant)) LOGGER.info(
                    "[TC-DEV44-R13an] side={} rod={} slider={} quadrant={} phase={} acceptedSliderStroke={} candidateWheelPinX={} requiredWheelPinX={} requiredShiftX={} closureAtAccepted={} restLength={} status=ATTACHMENT_CALIBRATION_ONLY movingRod=DISABLED acceptedMotion=UNCHANGED",
                    anSide==0?"POS_Z":"NEG_Z",anRod,anSlider,anQuadrant,anPhase,
                    r13oOffsets.get(anSlider)[0],anLiveB[0],anRequiredBx,anRequiredShiftX,
                    anClosureAtAccepted,anLength);
            }
        }

        // DEV44 R13ao: identify actual mesh-end vertices for bilateral linkage candidates.
        // This runs once per candidate chunk and NEVER mutates geometry or accepted poses.
        // The crosshead and wheel pivot coordinates below are reference locations, not asserted joints.
        if (dev44R13aoOn() && r13wIsolate) {
            for (int aoSide=0; aoSide<2; aoSide++) {
                final int aoRod=aoSide==0?378:746, aoSlider=aoSide==0?391:1011;
                if (!dev44R10Mapped(this.source.getPath(),aoRod) || !dev44R10Mapped(this.source.getPath(),aoSlider)) continue;
                for (int aoChunk : new int[]{aoRod,aoSlider}) {
                    if (!dev44R13aoLogged.add(aoChunk)) continue;
                    int aoStart=aoChunk*24*FLOATS_PER_VERTEX;
                    int aoEnd=Math.min(this.vertices.length,aoStart+24*FLOATS_PER_VERTEX);
                    if (aoStart < 0 || aoEnd-aoStart < FLOATS_PER_VERTEX) continue;
                    float aoMinX=Float.POSITIVE_INFINITY,aoMaxX=Float.NEGATIVE_INFINITY;
                    for(int i=aoStart;i+2<aoEnd;i+=FLOATS_PER_VERTEX){
                        aoMinX=Math.min(aoMinX,this.vertices[i]);aoMaxX=Math.max(aoMaxX,this.vertices[i]);
                    }
                    float aoWheelX=1.9375f,aoWheelY=-0.5625f;
                    float aoSliderX=1.53125f,aoSliderY=-0.90625f;
                    java.util.TreeSet<String> aoRows=new java.util.TreeSet<>();
                    int aoCountNearLow=0,aoCountNearHigh=0;
                    float aoLowY=0,aoHighY=0,aoLowZ=0,aoHighZ=0;
                    float aoNearestWheel=Float.POSITIVE_INFINITY,aoNearestSlider=Float.POSITIVE_INFINITY;
                    String aoWheelPoint="",aoSliderPoint="";
                    for(int i=aoStart;i+2<aoEnd;i+=FLOATS_PER_VERTEX){
                        float x=this.vertices[i],y=this.vertices[i+1],z=this.vertices[i+2];
                        boolean low=Math.abs(x-aoMinX)<0.00782f;
                        boolean high=Math.abs(x-aoMaxX)<0.00782f;
                        if(low){aoCountNearLow++;aoLowY+=y;aoLowZ+=z;}
                        if(high){aoCountNearHigh++;aoHighY+=y;aoHighZ+=z;}
                        float wd=(x-aoWheelX)*(x-aoWheelX)+(y-aoWheelY)*(y-aoWheelY);
                        float sd=(x-aoSliderX)*(x-aoSliderX)+(y-aoSliderY)*(y-aoSliderY);
                        if(wd<aoNearestWheel){aoNearestWheel=wd;aoWheelPoint=x+","+y+","+z;}
                        if(sd<aoNearestSlider){aoNearestSlider=sd;aoSliderPoint=x+","+y+","+z;}
                        if(low||high){aoRows.add((low?"LOW":"HIGH")+","+x+","+y+","+z);}
                    }
                    if(aoCountNearLow==0||aoCountNearHigh==0)continue;
                    String aoPrefix=aoSide==0?"POS_Z":"NEG_Z";
                    LOGGER.info("[TC-DEV44-R13ao] side={} chunk={} count={} xMin={} xMax={} lowEndMeanYZ=({}, {}) highEndMeanYZ=({}, {}) nearestWheelVertex={} nearestSliderVertex={} role=MESH_ENDPOINT_CANDIDATES_ONLY poses=UNCHANGED",
                        aoPrefix,aoChunk,(aoEnd-aoStart)/FLOATS_PER_VERTEX,aoMinX,aoMaxX,
                        aoLowY/aoCountNearLow,aoLowZ/aoCountNearLow,aoHighY/aoCountNearHigh,aoHighZ/aoCountNearHigh,
                        aoWheelPoint,aoSliderPoint);
                    try {
                        java.nio.file.Path aoDir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ao-Berkshire-Mesh-Ends");
                        java.nio.file.Files.createDirectories(aoDir);
                        StringBuilder aoCsv=new StringBuilder("end,x,y,z\n");
                        for(String row:aoRows)aoCsv.append(row).append('\n');
                        java.nio.file.Files.writeString(aoDir.resolve("chunk-"+aoChunk+"-ends.csv"),aoCsv.toString(),java.nio.charset.StandardCharsets.UTF_8);
                    } catch(Exception ex) {LOGGER.warn("[TC-DEV44-R13ao] CSV write failed for chunk {}: {}",aoChunk,ex.toString());}
                }
            }
        }

        // DEV44 R13ap — candidate classification by proximity to ORIGINAL mesh end vertices.
        // All coordinates are unposed; distance is a lead, NOT a verified crankpin.
        if (dev44R13apOn() && r13wIsolate && dev44R13apLogged.add(1)) {
            final int[][] apSides = {{378,391,372,373,374,375,379,389,390,358,359,360,140,141,142,146,147,148},
                                      {746,1011,729,730,731,732,747,1009,1010,713,714,715,143,144,145}};
            try {
                java.nio.file.Path apDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13ap-Berkshire-Linkage-Classification");
                java.nio.file.Files.createDirectories(apDir);
                StringBuilder apReport = new StringBuilder("side,reference_chunk,reference_end,candidate_chunk,nearest_vertex_gap,reference_x,reference_y,reference_z,candidate_x,candidate_y,candidate_z,category,verification\n");
                for (int apSide=0; apSide<2; apSide++) {
                    int[] apChunks=apSides[apSide];
                    for (int apRefIndex=0;apRefIndex<2;apRefIndex++) {
                        int apRef=apChunks[apRefIndex];
                        if (!dev44R10Mapped(this.source.getPath(),apRef)) continue;
                        int apRefStart=apRef*24*FLOATS_PER_VERTEX;
                        if (apRefStart<0 || apRefStart+24*FLOATS_PER_VERTEX>this.vertices.length) continue;
                        float apMinX=Float.POSITIVE_INFINITY,apMaxX=Float.NEGATIVE_INFINITY;
                        for(int apI=0;apI<24;apI++) {
                            float apX=this.vertices[apRefStart+apI*FLOATS_PER_VERTEX];
                            apMinX=Math.min(apMinX,apX); apMaxX=Math.max(apMaxX,apX);
                        }
                        for(int apEnd=0;apEnd<2;apEnd++) {
                            float apTarget=apEnd==0?apMinX:apMaxX;
                            for(int apCi=2;apCi<apChunks.length;apCi++) {
                                int apCandidate=apChunks[apCi];
                                if(!dev44R10Mapped(this.source.getPath(),apCandidate)) continue;
                                int apStart=apCandidate*24*FLOATS_PER_VERTEX;
                                if(apStart<0 || apStart+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                                double apBest=Double.POSITIVE_INFINITY;
                                float apAX=0,apAY=0,apAZ=0,apBX=0,apBY=0,apBZ=0;
                                for(int apI=0;apI<24;apI++) {
                                    int apA=apRefStart+apI*FLOATS_PER_VERTEX;
                                    float apX=this.vertices[apA],apY=this.vertices[apA+1],apZ=this.vertices[apA+2];
                                    if(Math.abs(apX-apTarget)>0.00782f)continue;
                                    for(int apJ=0;apJ<24;apJ++) {
                                        int apB=apStart+apJ*FLOATS_PER_VERTEX;
                                        float apXX=this.vertices[apB],apYY=this.vertices[apB+1],apZZ=this.vertices[apB+2];
                                        double apDist=Math.sqrt((apX-apXX)*(apX-apXX)+(apY-apYY)*(apY-apYY)+(apZ-apZZ)*(apZ-apZZ));
                                        if(apDist<apBest){apBest=apDist;apAX=apX;apAY=apY;apAZ=apZ;apBX=apXX;apBY=apYY;apBZ=apZZ;}
                                    }
                                }
                                if(!Double.isFinite(apBest))continue;
                                String apCategory=apCi<12?"NEARBY_LINKAGE_OR_PIN":"FIXED_SHELL_GUARD";
                                apReport.append(apSide==0?"POS_Z":"NEG_Z").append(',').append(apRef).append(',').append(apEnd==0?"LOW":"HIGH").append(',')
                                    .append(apCandidate).append(',').append(apBest).append(',').append(apAX).append(',').append(apAY).append(',').append(apAZ).append(',')
                                    .append(apBX).append(',').append(apBY).append(',').append(apBZ).append(',').append(apCategory).append(",UNVERIFIED_CANDIDATE\n");
                            }
                        }
                    }
                }
                java.nio.file.Files.writeString(apDir.resolve("candidate-gaps.csv"),apReport.toString(),java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("[TC-DEV44-R13ap] classification CSV written; sides=2 candidate comparisons={} path={} status=CANDIDATES_ONLY animation=UNCHANGED",apReport.toString().split("\n").length-1,apDir);
            } catch(Exception apEx){ LOGGER.warn("[TC-DEV44-R13ap] classification failed: {}",apEx.toString()); }
        }

        // DEV44 R13aq: identical full-mesh search for both sides and both part classes.
        // Geometry only. One-time CSV, no interpolation, transformation, or animation writes.
        if (dev44R13aqOn() && r13wIsolate && dev44R13aqLogged.add(1)) {
            try {
                java.nio.file.Path aqDir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("DEV44-R13aq-Bilateral-Full-Sweep");
                java.nio.file.Files.createDirectories(aqDir);
                StringBuilder aqCSV=new StringBuilder("side,reference_chunk,reference_end,candidate_chunk,minimum_3d_vertex_gap,ref_x,ref_y,ref_z,candidate_x,candidate_y,candidate_z,classification\n");
                int[][] aqRefs={{378,391},{746,1011}};
                int aqLimit=Math.min(1200,this.vertices.length/(24*FLOATS_PER_VERTEX));
                int aqCount=0;
                for(int aqSide=0;aqSide<2;aqSide++)for(int aqR=0;aqR<2;aqR++){
                    int aqRef=aqRefs[aqSide][aqR];
                    if(!dev44R10Mapped(this.source.getPath(),aqRef))continue;
                    int aqOffset=aqRef*24*FLOATS_PER_VERTEX;
                    if(aqOffset+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                    float aqMin=Float.POSITIVE_INFINITY,aqMax=Float.NEGATIVE_INFINITY;
                    for(int i=0;i<24;i++){
                        float x=this.vertices[aqOffset+i*FLOATS_PER_VERTEX];
                        aqMin=Math.min(aqMin,x);aqMax=Math.max(aqMax,x);
                    }
                    for(int aqEnd=0;aqEnd<2;aqEnd++){
                        float aqTarget=aqEnd==0?aqMin:aqMax;
                        for(int aqC=0;aqC<aqLimit;aqC++){
                            if(aqC==aqRef || aqC==348 || aqC==733 || aqC==391 || aqC==1011 || aqC==378 || aqC==746)continue;
                            if(!dev44R10Mapped(this.source.getPath(),aqC))continue;
                            int aqCandidateOffset=aqC*24*FLOATS_PER_VERTEX;
                            double best=Double.POSITIVE_INFINITY;
                            float ax=0,ay=0,az=0,bx=0,by=0,bz=0;
                            for(int i=0;i<24;i++){
                                int a=aqOffset+i*FLOATS_PER_VERTEX;
                                float x=this.vertices[a],y=this.vertices[a+1],z=this.vertices[a+2];
                                if(Math.abs(x-aqTarget)>0.00782f)continue;
                                for(int j=0;j<24;j++){
                                    int b=aqCandidateOffset+j*FLOATS_PER_VERTEX;
                                    double dx=x-this.vertices[b],dy=y-this.vertices[b+1],dz=z-this.vertices[b+2];
                                    double d=dx*dx+dy*dy+dz*dz;
                                    if(d<best){best=d;ax=x;ay=y;az=z;bx=this.vertices[b];by=this.vertices[b+1];bz=this.vertices[b+2];}
                                }
                            }
                            if(!Double.isFinite(best))continue;
                            // All mapped chunks participate. Only spatially close candidates enter the CSV.
                            if(best>0.36)continue;
                            aqCSV.append(aqSide==0?"POS_Z":"NEG_Z").append(',').append(aqRef).append(',')
                                .append(aqEnd==0?"LOW":"HIGH").append(',').append(aqC).append(',')
                                .append(Math.sqrt(best)).append(',').append(ax).append(',').append(ay).append(',').append(az).append(',')
                                .append(bx).append(',').append(by).append(',').append(bz).append(",PROXIMITY_ONLY_UNVERIFIED\n");
                            aqCount++;
                        }
                    }
                }
                java.nio.file.Files.writeString(aqDir.resolve("full-bilateral-candidate-gaps.csv"),aqCSV.toString(),java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("[TC-DEV44-R13aq] full bilateral sweep written rows={} range=0..{} bothSides=YES fixedShells=UNTOUCHED connectingRods=DISABLED",aqCount,aqLimit-1);
            }catch(Exception e){LOGGER.warn("[TC-DEV44-R13aq] sweep failed: {}",e.toString());}
        }

        // R13as: two-end minimum-error rigid pose (experimental, opt-in).
        // Both desired attachment points are measured from the existing wheel and slider
        // movements. This fit minimizes the squared endpoint errors while retaining
        // exact rest-mesh rod length. It cannot imply zero-error mechanical closure.
        if (dev44R13asOn() && r13wIsolate) {
            for (int asSide=0;asSide<2;asSide++) {
                int asChunk=asSide==0?378:746;
                int asSlider=asSide==0?391:1011;
                if(asChunk>=dev44R13hRoles.length || !dev44R10Mapped(this.source.getPath(),asChunk)
                        || dev44R13hRoles[asChunk]==2 || r13uPoses.containsKey(asChunk)
                        || r13oOffsets.containsKey(asChunk) || !r13oOffsets.containsKey(asSlider)){
                    if(dev44R13asLogged.add(90+asSide))LOGGER.warn("[TC-DEV44-R13as] side={} status=GUARD_REJECTED",asSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec asWheel=null;
                for(int asWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,asWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2
                            || (asSide==0 && w.centerZ()<0.10f)
                            || (asSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f){asWheel=w;break;}
                }
                if(asWheel==null){if(dev44R13asLogged.add(80+asSide))LOGGER.warn("[TC-DEV44-R13as] side={} status=NO_WHEEL",asSide==0?"POS_Z":"NEG_Z");continue;}
                float asZ=asSide==0?0.40625f:-0.521875f;
                float[] asA={1.953125f,-0.59375f,asZ};
                float[] asB={0.296875f,-0.46875f,asZ};
                float[] asCenter={asWheel.centerX(),asWheel.centerY(),asWheel.centerZ()};
                float[] wheelTarget=Dev44RigidLinkage.wheelPoint(asA,asCenter,asWheel.cos(),asWheel.sin(),2);
                float asStroke=r13oOffsets.get(asSlider)[0];
                float[] sliderTarget={asB[0]+asStroke,asB[1],asZ};
                double restLength=Math.hypot(asB[0]-asA[0],asB[1]-asA[1]);
                double vx=sliderTarget[0]-wheelTarget[0],vy=sliderTarget[1]-wheelTarget[1];
                double targetLength=Math.hypot(vx,vy);
                if(!Double.isFinite(targetLength) || targetLength<0.1)continue;
                // Equal-weight Procrustes fit: share closure error between both ends.
                double ux=vx/targetLength,uy=vy/targetLength;
                double mx=(wheelTarget[0]+sliderTarget[0])*0.5;
                double my=(wheelTarget[1]+sliderTarget[1])*0.5;
                float[] fitA={(float)(mx-ux*restLength*0.5),(float)(my-uy*restLength*0.5),asZ};
                float[] fitB={(float)(mx+ux*restLength*0.5),(float)(my+uy*restLength*0.5),asZ};
                var asFitPose=Dev44RigidLinkage.solve(asA,asB,fitA,fitB,2,0.0005f);
                if(asFitPose!=null)r13uPoses.put(asChunk,new Dev44R13uPose(asFitPose,asA));
                double wheelGap=Math.hypot(fitA[0]-wheelTarget[0],fitA[1]-wheelTarget[1]);
                double sliderGap=Math.hypot(fitB[0]-sliderTarget[0],fitB[1]-sliderTarget[1]);
                int quadrant=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(asWheel.sin(),asWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(dev44R13asLogged.add(asSide*10+quadrant))LOGGER.info(
                        "[TC-DEV44-R13as] side={} rod={} quadrant={} pose={} wheelJointGap={} sliderJointGap={} totalClosureError={} rigidLength={} targetLength={} sliderStroke={} twoEndFit=EXPERIMENTAL",
                        asSide==0?"POS_Z":"NEG_Z",asChunk,quadrant,asFitPose==null?"REJECTED":"APPLIED",wheelGap,sliderGap,
                        Math.abs(restLength-targetLength),restLength,targetLength,asStroke);
            }
        }

        // R13at: true constant-length slider-crank stroke at the EXISTING wheel-pin
        // candidate and rest rod length. No animation writes, no moving rods,
        // no adjustment to accepted slider391/1011. Joints are NOT yet verified.
        if (dev44R13atOn() && r13wIsolate) {
            for (int atSide=0;atSide<2;atSide++) {
                int atRod=atSide==0?378:746;
                int atSlider=atSide==0?391:1011;
                if (!dev44R10Mapped(this.source.getPath(),atRod)
                        || !dev44R10Mapped(this.source.getPath(),atSlider)
                        || !r13oOffsets.containsKey(atSlider)) {
                    if(dev44R13atLogged.add(90+atSide))LOGGER.warn("[TC-DEV44-R13at] side={} status=GUARD_REJECTED",atSide==0?"POS_Z":"NEG_Z");
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec atWheel=null;
                for (int atWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var atCandidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,atWi,this.vertices,FLOATS_PER_VERTEX);
                    if(atCandidate==null || !atCandidate.rotates() || atCandidate.axis()!=2
                            || (atSide==0 && atCandidate.centerZ()<0.10f)
                            || (atSide==1 && atCandidate.centerZ()>-0.10f))continue;
                    if(Math.abs(atCandidate.centerX()-1.9375f)<0.02f && Math.abs(atCandidate.centerY()+0.5625f)<0.02f){atWheel=atCandidate;break;}
                }
                if(atWheel==null){if(dev44R13atLogged.add(80+atSide))LOGGER.warn("[TC-DEV44-R13at] side={} status=NO_WHEEL",atSide==0?"POS_Z":"NEG_Z");continue;}
                float atZ=atSide==0?0.40625f:-0.521875f;
                float[] atWheelRest={1.953125f,-0.59375f,atZ};
                float[] atSliderRest={0.296875f,-0.46875f,atZ};
                float[] atCenter={atWheel.centerX(),atWheel.centerY(),atWheel.centerZ()};
                float[] atPin=Dev44RigidLinkage.wheelPoint(atWheelRest,atCenter,atWheel.cos(),atWheel.sin(),2);
                double atRigidL=Math.hypot(atWheelRest[0]-atSliderRest[0],atWheelRest[1]-atSliderRest[1]);
                double atDy=atPin[1]-atSliderRest[1];
                double atDisc=atRigidL*atRigidL-atDy*atDy;
                double atAccepted=r13oOffsets.get(atSlider)[0];
                int atQuad=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(atWheel.sin(),atWheel.cos())+Math.PI)/(Math.PI/2.0))));
                if(atDisc<0 || !Double.isFinite(atDisc)){
                    if(dev44R13atLogged.add(atSide*10+atQuad))LOGGER.warn("[TC-DEV44-R13at] side={} rod={} quadrant={} status=NO_REAL_CLOSURE discriminant={} slider=UNCHANGED",atSide==0?"POS_Z":"NEG_Z",atRod,atQuad,atDisc);
                    continue;
                }
                double atSolvedX=atPin[0]-Math.sqrt(atDisc);
                double atSolvedStroke=atSolvedX-atSliderRest[0];
                double atDelta=atSolvedStroke-atAccepted;
                double atAcceptedX=atSliderRest[0]+atAccepted;
                double atAcceptedLength=Math.hypot(atPin[0]-atAcceptedX,atPin[1]-atSliderRest[1]);
                double atResidual=Math.abs(atAcceptedLength-atRigidL);
                if(dev44R13atLogged.add(atSide*10+atQuad))LOGGER.info(
                    "[TC-DEV44-R13at] side={} rod={} slider={} quadrant={} crankPinX={} crankPinY={} rigidLength={} solvedSliderStroke={} acceptedSliderStroke={} requiredCorrection={} acceptedClosureResidual={} status={} animation=UNCHANGED joints=UNVERIFIED",
                    atSide==0?"POS_Z":"NEG_Z",atRod,atSlider,atQuad,atPin[0],atPin[1],atRigidL,
                    atSolvedStroke,atAccepted,atDelta,atResidual,Math.abs(atDelta)<=0.015?"CLOSE_CANDIDATE":"STROKE_MISMATCH");
            }
        }

        // R13au: sweep candidate wheel-pin radius and phase against the accepted
        // slider stroke over 64 steps. A curve fit is NOT proof of a mesh joint:
        // require the ORIGINAL mesh pin (radius/phase) to agree as a separate gate.
        // If BOTH gates pass, solve a rigid pose of 378/746 while preserving all
        // wheel, side-rod and slider transforms. Otherwise NO rod animation.
        if (dev44R13auOn() && r13wIsolate) {
            for(int auSide=0;auSide<2;auSide++) {
                int auChunk=auSide==0?378:746,auSlider=auSide==0?391:1011;
                String auSideName=auSide==0?"POS_Z":"NEG_Z";
                if(!dev44R10Mapped(this.source.getPath(),auChunk)
                        || !dev44R10Mapped(this.source.getPath(),auSlider)
                        || r13uPoses.containsKey(auChunk) || r13oOffsets.containsKey(auChunk)
                        || !r13oOffsets.containsKey(auSlider)
                        || auChunk>=dev44R13hRoles.length || dev44R13hRoles[auChunk]==2) {
                    if(dev44R13auLogged.add(100+auSide))LOGGER.warn("[TC-DEV44-R13au] side={} status=GUARD_REJECTED",auSideName);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec auWheel=null;
                for(int auWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,auWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2 || (auSide==0 && w.centerZ()<0.10f)
                            || (auSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f) {auWheel=w;break;}
                }
                if(auWheel==null){if(dev44R13auLogged.add(110+auSide))LOGGER.warn("[TC-DEV44-R13au] side={} status=NO_WHEEL",auSideName);continue;}
                float auZ=auSide==0?0.40625f:-0.521875f;
                float[] auA={1.953125f,-0.59375f,auZ};
                float[] auB={0.296875f,-0.46875f,auZ};
                float[] auCenter={auWheel.centerX(),auWheel.centerY(),auWheel.centerZ()};
                double auL=Math.hypot(auB[0]-auA[0],auB[1]-auA[1]);
                double auRx=auA[0]-auCenter[0], auRy=auA[1]-auCenter[1];
                double auRestRadius=Math.hypot(auRx,auRy),auRestPhase=Math.atan2(auRy,auRx);
                double[] auCached=dev44R13auCalibrated.get(auSide);
                double auBestMax=auCached==null?Double.POSITIVE_INFINITY:auCached[0];
                double auBestR=auCached==null?Double.NaN:auCached[1];
                double auBestPhase=auCached==null?Double.NaN:auCached[2];
                // Test a bounded physical crank-radius domain with phase offsets.
                if(auCached==null) for(int auRi=0;auRi<=48;auRi++) {
                    double radius=auRi*0.003125;
                    for(int auPi=0;auPi<64;auPi++) {
                        double phaseOffset=-Math.PI+auPi*Math.PI/32.0;
                        double maxError=0;
                        for(int auS=0;auS<64;auS++) {
                            double phase=auS*Math.PI/32.0,phi=phase+phaseOffset;
                            double pinX=auCenter[0]+radius*Math.cos(phi);
                            double pinY=auCenter[1]+radius*Math.sin(phi);
                            double sliderX=auB[0]+0.09375*Math.cos(phase);
                            double length=Math.hypot(pinX-sliderX,pinY-auB[1]);
                            maxError=Math.max(maxError,Math.abs(length-auL));
                        }
                        if(maxError<auBestMax){auBestMax=maxError;auBestR=radius;auBestPhase=phaseOffset;}
                    }
                }
                if(auCached==null) dev44R13auCalibrated.put(auSide,new double[]{auBestMax,auBestR,auBestPhase});
                // Independent mesh-rest and full-cycle rigid closure tests.
                double auRestDiscrepancy=Math.hypot(auBestR*Math.cos(auBestPhase)-auRx,
                        auBestR*Math.sin(auBestPhase)-auRy);
                boolean auClosureGood=Double.isFinite(auBestMax)&&auBestMax<0.0015;
                boolean auRestGood=auRestDiscrepancy<0.002;
                boolean auEnabled=auClosureGood&&auRestGood;
                if(dev44R13auLogged.add(auSide)) LOGGER.info(
                    "[TC-DEV44-R13au] side={} rod={} samples=64 candidates=3136 restPinRadius={} bestRadius={} bestPhase={} fullCycleMaxClosureError={} restPinDiscrepancy={} closureGate={} restMeshGate={} status={} acceptedSlider=UNCHANGED joints=UNVERIFIED",
                    auSideName,auChunk,auRestRadius,auBestR,auBestPhase,auBestMax,auRestDiscrepancy,
                    auClosureGood?"PASS":"FAIL",auRestGood?"PASS":"FAIL",auEnabled?"CONDITIONAL_POSE_ENABLED":"SAFETY_HOLD");
                if(auEnabled){
                    float[] auTargetA=Dev44RigidLinkage.wheelPoint(auA,auCenter,auWheel.cos(),auWheel.sin(),2);
                    float[] auTargetB={auB[0]+r13oOffsets.get(auSlider)[0],auB[1],auZ};
                    if(Math.abs(Math.hypot(auTargetA[0]-auTargetB[0],auTargetA[1]-auTargetB[1])-auL)<0.0015){
                        var auPose=Dev44RigidLinkage.solve(auA,auB,auTargetA,auTargetB,2,0.0015f);
                        if(auPose!=null)r13uPoses.put(auChunk,new Dev44R13uPose(auPose,auA));
                    }
                }
            }
        }

        // DEV44 R13av: test both physically possible end assignments on the
        // frozen original mesh. These measurements do NOT certify joint centers.
        if (dev44R13avOn() && r13wIsolate) {
            for (int avSide=0;avSide<2;avSide++) {
                int avRod=avSide==0?378:746;
                int avSlider=avSide==0?391:1011;
                String avName=avSide==0?"POS_Z":"NEG_Z";
                if (!dev44R10Mapped(this.source.getPath(),avRod)
                    || !dev44R10Mapped(this.source.getPath(),avSlider)
                    || !r13oOffsets.containsKey(avSlider)) {
                    if(dev44R13avLogged.add(100+avSide))LOGGER.warn("[TC-DEV44-R13av] side={} status=GUARD_REJECTED",avName);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec avWheel=null;
                for (int avWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,avWi,this.vertices,FLOATS_PER_VERTEX);
                    if(w==null || !w.rotates() || w.axis()!=2 || (avSide==0 && w.centerZ()<0.10f)
                       || (avSide==1 && w.centerZ()>-0.10f))continue;
                    if(Math.abs(w.centerX()-1.9375f)<0.02f && Math.abs(w.centerY()+0.5625f)<0.02f) {avWheel=w;break;}
                }
                if(avWheel==null) {
                    if(dev44R13avLogged.add(110+avSide))LOGGER.warn("[TC-DEV44-R13av] side={} status=NO_WHEEL",avName);
                    continue;
                }
                double[][] avEnds={{1.953125,-0.59375},{0.296875,-0.46875}};
                // These are component neighborhood references, not measured pivot centers.
                // 491/1013: nearby wheel cap; 1007/1021: nearby slider-facing piece.
                double[] avWheelCap={1.9390143,-0.5223459};
                double[] avSliderNeighbor={1.3323448,-0.9897004};
                for (int avAssignment=0;avAssignment<2;avAssignment++) {
                    double[] avPin=avEnds[avAssignment],avCross=avEnds[1-avAssignment];
                    double avRadius=Math.hypot(avPin[0]-avWheel.centerX(),avPin[1]-avWheel.centerY());
                    double avMeshWheelGap=Math.hypot(avPin[0]-avWheelCap[0],avPin[1]-avWheelCap[1]);
                    double avMeshSliderGap=Math.hypot(avCross[0]-avSliderNeighbor[0],avCross[1]-avSliderNeighbor[1]);
                    double avL=Math.hypot(avPin[0]-avCross[0],avPin[1]-avCross[1]);
                    // Follow the exact wheel transform rather than fitting a new phase.
                    // Crosshead is constrained to remain horizontal at original Y.
                    double avMaxClosure=0, avSumClosure=0, avMaxRequiredShift=0;
                    boolean avReal=true;
                    for(int avStep=0;avStep<64;avStep++) {
                        double avTheta=avStep*Math.PI/32.0;
                        double avDX=avPin[0]-avWheel.centerX(),avDY=avPin[1]-avWheel.centerY();
                        double avPX=avWheel.centerX()+avDX*Math.cos(avTheta)-avDY*Math.sin(avTheta);
                        double avPY=avWheel.centerY()+avDX*Math.sin(avTheta)+avDY*Math.cos(avTheta);
                        double avAcceptedX=avCross[0]+0.09375*Math.cos(avTheta);
                        double avErr=Math.abs(Math.hypot(avPX-avAcceptedX,avPY-avCross[1])-avL);
                        avMaxClosure=Math.max(avMaxClosure,avErr);avSumClosure+=avErr;
                        double avRadicand=avL*avL-(avPY-avCross[1])*(avPY-avCross[1]);
                        if(avRadicand<0){avReal=false;continue;}
                        double avRequiredX=avPX+(avCross[0]<avPin[0]?-1:1)*Math.sqrt(avRadicand);
                        avMaxRequiredShift=Math.max(avMaxRequiredShift,Math.abs(avRequiredX-avAcceptedX));
                    }
                    if(dev44R13avLogged.add(avSide*10+avAssignment))LOGGER.info(
                        "[TC-DEV44-R13av] side={} rod={} assignment={} wheelEnd={} sliderEnd={} crankRadius={} meshWheelNeighborGap={} meshSliderNeighborGap={} maxAcceptedClosureError={} meanAcceptedClosureError={} maxRequiredSliderShift={} realSolution={} status=UNVERIFIED_NEIGHBORHOOD_ONLY animation=UNCHANGED",
                        avName,avRod,avAssignment,avAssignment==0?"HIGH_X":"LOW_X",avAssignment==0?"LOW_X":"HIGH_X",
                        avRadius,avMeshWheelGap,avMeshSliderGap,avMaxClosure,avSumClosure/64.0,avMaxRequiredShift,avReal);
                }
            }
        }

        // R13aw: independent neighborhood scan of frozen component extrema.
        // A low nearest-vertex gap is NOT evidence of a real pin/bearing center.
        // No writes to r13uPoses, wheel transforms, slider offsets or render geometry.
        if (dev44R13awOn() && r13wIsolate) {
            final int[] awChunks={391,1011,1007,1021,1104,1105,375,732,491,1013,140,143,144,145,146,147,148};
            for(int awSide=0;awSide<2;awSide++) {
                int awRod=awSide==0?378:746;
                int awSlider=awSide==0?391:1011;
                String awName=awSide==0?"POS_Z":"NEG_Z";
                if(!dev44R10Mapped(this.source.getPath(),awRod)||!dev44R10Mapped(this.source.getPath(),awSlider)) {
                    if(dev44R13awLogged.add(1000+awSide)) LOGGER.warn("[TC-DEV44-R13aw] side={} status=GUARD_REJECTED",awName);
                    continue;
                }
                // Rod endpoints are *bounding-extrema hypotheses*, not pivot locations.
                final double[] awRodLow={0.296875,-0.53125};
                final double[] awRodHigh={1.953125,-0.53125};
                int awFound=0;
                for(int awChunk:awChunks) {
                    if(!dev44R10Mapped(this.source.getPath(),awChunk)) continue;
                    // Actual mesh bounds, not assumed centers or nearest-vertex pairing.
                    awFound++;
                }
                if(dev44R13awLogged.add(awSide)) LOGGER.info(
                    "[TC-DEV44-R13aw] side={} rod={} slider={} candidateChunksMapped={} rodLowX={} rodLowY={} rodHighX={} rodHighY={} status=STATIC_EXTREMA_ONLY pivot=UNVERIFIED animation=UNCHANGED offlineCSV=REQUIRED",
                    awName,awRod,awSlider,awFound,awRodLow[0],awRodLow[1],awRodHigh[0],awRodHigh[1]);
            }
        }


        // R13ax: real vertex-cloud neighborhood (no guessed pivot, no pose writes).
        if (dev44R13awOn() && r13wIsolate && dev44R13axLogged.add(this.source.getPath())) {
            final int[][] axChunks={{378,391,375,348,352,353,354,379,389,1007,1101,1105,1106,140,143,144,145},
                                    {746,1011,732,733,729,730,731,734,735,736,1013,1021,1104,1176,140,143,145}};
            final String[] axSides={"POS_Z","NEG_Z"};
            for(int axSide=0;axSide<2;axSide++) {
                for(int axCh:axChunks[axSide]) {
                    int axOffset=axCh*24*FLOATS_PER_VERTEX;
                    if(axOffset<0 || axOffset+24*FLOATS_PER_VERTEX>this.vertices.length)continue;
                    float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
                    float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
                    float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
                    double sx=0,sy=0,sz=0,xx=0,yy=0,zz=0;
                    for(int axV=0;axV<24;axV++){
                        int axI=axOffset+axV*FLOATS_PER_VERTEX;
                        float x=this.vertices[axI],y=this.vertices[axI+1],z=this.vertices[axI+2];
                        minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                        minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                        minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                        sx+=x;sy+=y;sz+=z;xx+=x*x;yy+=y*y;zz+=z*z;
                    }
                    double cx=sx/24.0,cy=sy/24.0,cz=sz/24.0;
                    double vx=Math.max(0,xx/24.0-cx*cx),vy=Math.max(0,yy/24.0-cy*cy),vz=Math.max(0,zz/24.0-cz*cz);
                    LOGGER.info("[TC-DEV44-R13ax] side={} chunk={} centroidX={} centroidY={} centroidZ={} spanX={} spanY={} spanZ={} sigmaX={} sigmaY={} sigmaZ={} status=VERTEX_CLOUD_ONLY pivot=UNVERIFIED animation=UNCHANGED",
                        axSides[axSide],axCh,cx,cy,cz,maxX-minX,maxY-minY,maxZ-minZ,Math.sqrt(vx),Math.sqrt(vy),Math.sqrt(vz));
                }
            }
        }

        // DEV44 R13az: virtual-pin visual candidate, NOT a verified physical linkage.
        // The accepted slider and wheel rendering remain independent/unchanged.
        if (dev44R13azOn() && r13wIsolate) {
            final int[] azRods={378,746};
            for(int azSide=0;azSide<2;azSide++) {
                final int azRod=azRods[azSide], azSlider=azSide==0?391:1011;
                if(!dev44R10Mapped(this.source.getPath(),azRod)
                        || dev44R13hRoles[azRod]==2 || r13uPoses.containsKey(azRod)
                        || !r13oOffsets.containsKey(azSlider)) {
                    if(dev44R13azLogged.add(100+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD reason=MISSING_ACCEPTED_COMPONENT",azSide);
                    continue;
                }
                traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec azWheel=null;
                for(int azWi:new int[]{10,11,12,13,14,15,16,17,22,23,24,25,26,27}) {
                    var candidate=traincraft.client.renderer.animation.BakedWheelAnimationContext.spec(meshKey,azWi,this.vertices,FLOATS_PER_VERTEX);
                    if(candidate==null || !candidate.rotates() || candidate.axis()!=2
                            || (azSide==0 && candidate.centerZ()<0.10f)
                            || (azSide==1 && candidate.centerZ()>-0.10f))continue;
                    if(Math.abs(candidate.centerX()-1.9375f)<0.02f && Math.abs(candidate.centerY()+0.5625f)<0.02f) {azWheel=candidate;break;}
                }
                if(azWheel==null) {if(dev44R13azLogged.add(110+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD reason=WHEEL_NOT_FOUND",azSide);continue;}
                float azZ=azSide==0?0.40625f:-0.521875f;
                float[] azA={1.953125f,-0.59375f,azZ};
                float[] azB={0.296875f,-0.46875f,azZ};
                float[] azCenter={azWheel.centerX(),azWheel.centerY(),azWheel.centerZ()};
                double azLength=Math.hypot(azB[0]-azA[0],azB[1]-azA[1]);
                // Full revolution precheck using the same accepted sinusoidal slider phase.
                // Threshold is explicitly a VISUAL trial allowance, not a joint pass criterion.
                double azMaxGap=0;
                for(int azSample=0;azSample<128;azSample++) {
                    double azAngle=2*Math.PI*azSample/128.0;
                    float[] azTestWheel=Dev44RigidLinkage.wheelPoint(azA,azCenter,(float)Math.cos(azAngle),(float)Math.sin(azAngle),2);
                    double azSliderX=azB[0]+0.09375*Math.cos(azAngle);
                    azMaxGap=Math.max(azMaxGap,Math.abs(Math.hypot(azSliderX-azTestWheel[0],azB[1]-azTestWheel[1])-azLength));
                }
                if(!Double.isFinite(azMaxGap)||azMaxGap>0.115) {
                    if(dev44R13azLogged.add(120+azSide)) LOGGER.warn("[TC-DEV44-R13az] side={} status=GUARD_HOLD fullCycleMaxGap={} blocks threshold=0.115",azSide,azMaxGap);
                    continue;
                }
                float[] azDA=Dev44RigidLinkage.wheelPoint(azA,azCenter,azWheel.cos(),azWheel.sin(),2);
                float azBX=azB[0]+r13oOffsets.get(azSlider)[0];
                double azTarget=Math.hypot(azBX-azDA[0],azB[1]-azDA[1]);
                if(!Double.isFinite(azTarget)||azTarget<0.1)continue;
                float[] azDB={(float)(azDA[0]+(azBX-azDA[0])*azLength/azTarget),
                        (float)(azDA[1]+(azB[1]-azDA[1])*azLength/azTarget),azZ};
                var azSolved=Dev44RigidLinkage.solve(azA,azB,azDA,azDB,2,0.0005f);
                if(azSolved!=null) r13uPoses.put(azRod,new Dev44R13uPose(azSolved,azA));
                int azQuadrant=Math.max(0,Math.min(3,(int)Math.floor((Math.atan2(azWheel.sin(),azWheel.cos())+Math.PI)/(Math.PI/2))));
                if(dev44R13azLogged.add(azSide*10+azQuadrant))LOGGER.info("[TC-DEV44-R13az] side={} rod={} quadrant={} status={} fullCycleMaxGap={} currentGap={} trial=UNVERIFIED wheels=UNCHANGED slider=UNCHANGED",
                        azSide==0?"POS_Z":"NEG_Z",azRod,azQuadrant,azSolved==null?"POSE_REJECTED":"EXPERIMENTAL_POSE",azMaxGap,Math.abs(azTarget-azLength));
            }
        }

        // R13w suppresses the old approximate R13c preview ONLY on Berkshire 765.
        // New R13o/R13u motion is never enabled by this switch.
        final boolean r13wBerkshire = dev44R13wOn()
                && this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && !dev54h9b765;
        final boolean r13Preview = r12Hide && dev44R13PreviewOn() && !r13oEnabled && !r13uEnabled && !r13wBerkshire;
        if (r13wBerkshire) {
            var wheel=traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey,10,this.vertices,FLOATS_PER_VERTEX);
            if (wheel != null && wheel.rotates()) {
                double phase=Math.atan2(wheel.sin(),wheel.cos());
                int quadrant=(int)Math.floor(((phase + Math.PI) / (2.0*Math.PI))*4.0);
                quadrant=Math.max(0,Math.min(3,quadrant));
                if (dev44R13wLoggedQuadrants.add(quadrant)) {
                    dev44R13hPrepare();
                    var second=traincraft.client.renderer.animation.BakedWheelAnimationContext
                            .spec(meshKey,11,this.vertices,FLOATS_PER_VERTEX);
                    LOGGER.info("[TC-DEV44-R13w] SAMPLE quadrant={} phaseRadians={} wheel10Pivot=({},{},{}) cos={} sin={} wheel11Cos={} wheel11Sin={} rod348Center=({},{},{}) crank359Center=({},{},{}) R13cPreview=SUPPRESSED R13oR13u=NOT_ENABLED_BY_R13w joints=UNVERIFIED",
                            quadrant,phase,wheel.centerX(),wheel.centerY(),wheel.centerZ(),wheel.cos(),wheel.sin(),
                            second==null?Float.NaN:second.cos(),second==null?Float.NaN:second.sin(),
                            dev44R13hCx[348],dev44R13hCy[348],dev44R13hCz[348],
                            dev44R13hCx[359],dev44R13hCy[359],dev44R13hCz[359]);
                }
            }
        }
        final int r13Anchor = r13Preview ? dev44R13FindWheelAnchor(meshKey) : -1;
        final traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec r13Wheel =
                r13Anchor >= 0 ? traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey, r13Anchor, this.vertices, FLOATS_PER_VERTEX) : null;
        int r13LogState = r13Preview ? (r13Wheel == null ? 1 : 2) : 0;
        if (dev44R13LastLoggedState != r13LogState) {
            LOGGER.info("[TC-DEV44-R13c-PREVIEW] mesh={} enabled={} wheelAnchorChunk={} wheelFound={} phaseFromAcceptedWheel={} activeMode={}",
                    this.source, r13Preview, r13Anchor, r13Wheel != null,
                    r13Wheel != null, r13LogState);
            dev44R13LastLoggedState = r13LogState;
        }
        // Explicit fleet switch, never conflicts with earlier rod proof modes.
        // Only authenticated mesh bytes can opt into derived joint movement.
        final boolean dev44FleetActive = !(DEV54H15T_GWR_ON && this.dev44R11Verified &&
                DEV54H15T_GWR_MESH.equals(this.source.getPath())) &&
                !(DEV54H15W_GWR_ON && this.dev44R11Verified &&
                  DEV54H15W_GWR_MESH.equals(this.source.getPath())) &&
                !(DEV54H15S_ON && this.dev44R11Verified &&
                DEV54H15S_MESH.equals(this.source.getPath())) && !dev54h14Active &&
                !(DEV54H15H_ON && dev54h15hHideGenerated(this.source.getPath())) &&
                dev44FleetEnabled()
                && this.dev44R11Verified
                && this.source.getPath().startsWith("legacy_meshes/locomotive_steam_")
                && !this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                && !r13Preview && !r13oEnabled && !r13uEnabled && !r13kAlco
                && !dev54h10aRequested
                && !dev54h11aRequested
                // DEV54H12B1: the older opt-in DEV44 generic fleet preview
                // must not BLOCK the exact four-model H12B pilot when selected.
                // Other locomotives preserve the existing DEV44 preview.
                && !dev54h12bSelected(this.source.getPath())
                && !(DEV54H13_ON && dev54h13CouplingModel(this.source.getPath()))
                // H13C resolves H13B-HOLD: the older generic preview owned
                // BR80/Climax before H13B could animate their source rods.
                && !(DEV54H13C_ON && DEV54H13B_ON &&
                       (this.source.getPath().equals("legacy_meshes/locomotive_steam_br80.tcm") ||
                        this.source.getPath().equals("legacy_meshes/locomotive_steam_climax.tcm")))
                // H8A supersedes only the UNACCEPTED DEV44 preview for 1225.
                && !(dev54h8aEnabled() && this.source.getPath().equals(
                        "legacy_meshes/locomotive_steam_berkshire_1225.tcm"));
        // DEV54H15S source-authored GWR101 motion. Independent of H15H user rod saves.
        // This is intentionally not H14's procedural replacement rig.
        final float dev54h15sPhase=(DEV54H15S_ON && DEV54H15S_MESH.equals(this.source.getPath()))
            ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h13bPartPhase(meshKey)
            :Float.NaN;
        final boolean dev54h15sActive=DEV54H15S_ON && DEV54H15S_MESH.equals(this.source.getPath())
            && this.dev44R11Verified && Float.isFinite(dev54h15sPhase)
            && !r12Hide && !(this.dev54fMaskVerified && traincraft.debug.Dev54FRodMask.enabled()) && !r13Preview && !r13oEnabled && !r13uEnabled
            && !dev54h14Active && !dev54h15Replacing && !dev54h15hSourceMask;
        if(DEV54H15S_ON && DEV54H15S_MESH.equals(this.source.getPath())
              && DEV54H15S_REPORTED.add(dev54h15sActive?"LIVE":"HOLD")) {
            LOGGER.info("[DEV54H15S-{}] model={} verified={} phaseFinite={} "
               +"sourceParts=14 bothSides=true fixedCylinderShells=YES newRods=NO "
               +"sourceTCM=UNCHANGED wheelAnimation=UNCHANGED visualPASS=NO",
               dev54h15sActive?"LIVE":"HOLD",this.source.getPath(),this.dev44R11Verified,
               Float.isFinite(dev54h15sPhase));
        }
        // DEV54H15T: original GWR42xx geometry and its accepted driver phase.
        final float dev54h15tGwrPhase=(DEV54H15T_GWR_ON &&
                DEV54H15T_GWR_MESH.equals(this.source.getPath()))
            ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h13bPartPhase(meshKey)
            :Float.NaN;
        final boolean dev54h15tGwrLive=DEV54H15T_GWR_ON &&
            DEV54H15T_GWR_MESH.equals(this.source.getPath()) &&
            this.dev44R11Verified && Float.isFinite(dev54h15tGwrPhase) &&
            !r12Hide && !(this.dev54fMaskVerified && traincraft.debug.Dev54FRodMask.enabled()) &&
            !r13Preview && !r13oEnabled && !r13uEnabled && !dev54h14Active &&
            !dev54h15Replacing && !dev54h15hSourceMask;
        if(DEV54H15T_GWR_ON && DEV54H15T_GWR_MESH.equals(this.source.getPath()) &&
           DEV54H15T_REPORTED.add("GWR42:"+(dev54h15tGwrLive?"LIVE":"HOLD")))
            LOGGER.info("[DEV54H15T-GWR42-{}] shaVerified={} finiteWheelPhase={} " +
                  "sourceRods=12 sliders=2 bothSides=true newGeometry=NO fixedCylinders=YES " +
                  "acceptedWheelTransforms=UNCHANGED visualPASS=NO",
                  dev54h15tGwrLive?"LIVE":"HOLD",this.dev44R11Verified,
                  Float.isFinite(dev54h15tGwrPhase));
        if(DEV54H15V_ON && DEV54H15T_GWR_MESH.equals(this.source.getPath()) &&
            DEV54H15T_REPORTED.add("H15V:"+(dev54h15tGwrLive?"LIVE":"HOLD")))
            LOGGER.info("[DEV54H15V-GWR42XX-{}] mirroredCrosshead=SLIDE_X " +
                "linkage=RIGID_PIVOT positiveSideYRebased=YES " +
                "sourceMesh=UNMODIFIED wheels=UNMODIFIED",
                dev54h15tGwrLive?"LIVE":"HOLD");
        // DEV54H15W: only original GWR72xx rods, never replacement rods.
        final float dev54h15wPhase=(DEV54H15W_GWR_ON &&
                DEV54H15W_GWR_MESH.equals(this.source.getPath()))
            ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h13bPartPhase(meshKey)
            :Float.NaN;
        final boolean dev54h15wLive=DEV54H15W_GWR_ON &&
            DEV54H15W_GWR_MESH.equals(this.source.getPath()) &&
            this.dev44R11Verified && Float.isFinite(dev54h15wPhase) &&
            !r12Hide && !(this.dev54fMaskVerified && traincraft.debug.Dev54FRodMask.enabled()) &&
            !r13Preview && !r13oEnabled && !r13uEnabled && !dev54h14Active &&
            !dev54h15Replacing && !dev54h15hSourceMask;
        if(DEV54H15W_GWR_ON && DEV54H15W_GWR_MESH.equals(this.source.getPath()) &&
           DEV54H15W_REPORTED.add(dev54h15wLive?"LIVE":"HOLD"))
            LOGGER.info("[DEV54H15W-GWR72-{}] wheelPhaseFinite={} sourceVerified={} " +
                "sourceRods=12 crossheads=2 mirroredRebase=YES newGeometry=NO wheelAnimation=UNTOUCHED",
                dev54h15wLive?"LIVE":"HOLD",Float.isFinite(dev54h15wPhase),this.dev44R11Verified);
        // DEV44 R13c: collect accepted *driving wheel* pivots; a single first-wheel
        // pivot is not valid for engines with multiple axles or different wheel sizes.
        java.util.ArrayList<traincraft.client.renderer.animation.BakedWheelAnimationContext.TransformSpec> r13Wheels = new java.util.ArrayList<>();
        if (r13Wheel != null || dev44R13dOn() || dev44R13fOn() || r13kAlco || dev44R13mCaptureOn() || dev44R13nOn()) {
            for (int ch=0;ch<(this.vertexCount+23)/24;ch++) {
                if (dev44R10Mapped(this.source.getPath(),ch)) continue;
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                if (w!=null && w.rotates()) r13Wheels.add(w);
            }
        } else if (dev44FleetActive) {
            // The full scan happens only once per model, not on every render.
            if (dev44FleetWheelChunks==null || dev44FleetWheelChunks.length==0) {
                var found=new java.util.ArrayList<Integer>();
                for(int ch=0;ch<(this.vertexCount+23)/24;ch++) {
                    if(dev44R10Mapped(this.source.getPath(),ch)) continue;
                    var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                            .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                    if(w!=null && w.rotates())found.add(ch);
                }
                if(!found.isEmpty())dev44FleetWheelChunks=found.stream().mapToInt(Integer::intValue).toArray();
            }
            if(dev44FleetWheelChunks!=null) for(int ch:dev44FleetWheelChunks) {
                var w=traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey,ch,this.vertices,FLOATS_PER_VERTEX);
                if(w!=null && w.rotates())r13Wheels.add(w);
            }
        }
        // R13d: capture once per renderer invocation, before any linkage modifications.
        dev44R13dCapture(meshKey, r13Wheels);
        dev44R13fCapture(meshKey, r13Wheels);
        dev44R13lReport(meshKey, r13Wheels);
        dev44R13mReport(meshKey, r13Wheels);
        dev44R13nReport(meshKey, r13Wheels);
        if (r13Preview || dev44FleetActive) dev44R13hPrepare();
        // DEV54H5: VB Shay 2 has wheels only. DEV44 guessed two rods from
        // unlabeled mesh plates; never synthesize swept linkage for this model.
        // Native BakedWheel and source-exact H2/H5 wheel rotations remain unchanged.
        final boolean dev54h5Shay2WheelsOnly = "legacy_meshes/locomotive_steam_vb_shay_2.tcm".equals(this.source.getPath());
        final Dev44FleetSweptLinkage.Frame dev44FleetFrame = dev44FleetActive && !r13Wheels.isEmpty()
                && !dev54h5Shay2WheelsOnly
                ? Dev44FleetSweptLinkage.build(this.vertices,this.vertexCount,dev44R13hRoles,r13Wheels)
                : null;
        // DEV58G8R2: one-time guard audit; never log per frame or per vertex.
        if(dev58g8r1AllowedModel(this.source.getPath()) && !dev58g8r2FrameReported) {
            dev58g8r2FrameReported=true;
            LOGGER.info("[DEV58G8R2-FRAME-GATE] model={} linkageFramePresent={} "
                    +"dynamicChunksExcludedIndividually=true",this.source,dev44FleetFrame!=null);
        }
        if(dev44FleetFrame!=null && !dev44FleetFirstReport) {
            dev44FleetFirstReport=true;
            LOGGER.info("[TC-DEV44-FLEET-CANDIDATE] mesh={} rods={} cranks={} held={} state=VISUAL_CANDIDATE_NOT_RUNTIME_ACCEPTED wheels=UNTOUCHED pistons=STATIONARY",
                    this.source,dev44FleetFrame.rods,dev44FleetFrame.cranks,dev44FleetFrame.skipped);
        }
        // DEV54H5 wheel-only policy: report exactly once when old rod-guessing
        // preview is active; never synthesize parts absent from this Shay.
        if (dev54h5Shay2WheelsOnly && dev44FleetActive && !dev44FleetFirstReport) {
            dev44FleetFirstReport=true;
            LOGGER.info("[DEV54H5-SHAY2-WHEELS-ONLY] DEV44 guessed linkage suppressed; native wheels retained; rods=0 cranks=0 pistons=0");
        }
        // Group-authored linkage: one motion vector per role and side, per render.
        // Avoids independently orbiting hundreds of quads from the same steel bar.
        float[][] r13GroupSum = new float[8][3];
        int[] r13GroupN = new int[8];
        if (r13Wheel != null) {
            int totalChunks=(this.vertexCount+23)/24;
            for (int ch=0;ch<totalChunks;ch++) {
                int role=dev44R13hRoles[ch];
                if (role==0 || role==2) continue; // fixed cylinder shell has no moving group
                float ax=dev44R13hCx[ch], ay=dev44R13hCy[ch], az=dev44R13hCz[ch];
                float lateral=r13Wheel.axis()==0?ax:(r13Wheel.axis()==1?ay:az);
                float wheelLateral=r13Wheel.axis()==0?r13Wheel.centerX():(r13Wheel.axis()==1?r13Wheel.centerY():r13Wheel.centerZ());
                int group=role*2+(lateral>=wheelLateral?1:0);
                if(group>=r13GroupSum.length)continue;
                r13GroupSum[group][0]+=ax;r13GroupSum[group][1]+=ay;r13GroupSum[group][2]+=az;
                r13GroupN[group]++;
            }
        }
        // Side-specific crank reference. When labels are imperfect use the rod
        // group centroid only as a fallback; never shift the wheel center itself.
        float[][] r13CrankAnchor = new float[2][3];
        boolean[] r13HasCrank = new boolean[2];
        for (int side=0;side<2;side++) {
            int g=3*2+side;
            if(r13GroupN[g]>0) {
                for(int d=0;d<3;d++) r13CrankAnchor[side][d]=r13GroupSum[g][d]/r13GroupN[g];
                r13HasCrank[side]=true;
            }
        }
        int r13ActiveChunk=-1;
        int r13ActiveRole=0;
        float[] r13Offset=null;
         // DEV54F recording only: safe, SHA-verified, individually tagged rod chunks.
         // Keep all BakedWheelAnimationContext wheel chunks, as well as piston/crank tags.
         final boolean dev54fRodHide = this.dev54fMaskVerified && traincraft.debug.Dev54FRodMask.enabled();
         // Only when the full wheel set, asset SHA and production phase pass verification.
         final Dev54HRealWheelParts.Frame dev54hFrame = this.dev54hWheelParts != null && !dev54fRodHide
                 ? this.dev54hWheelParts.begin(meshKey, this.vertices, FLOATS_PER_VERTEX) : null;
         final Dev54H2FleetWheelParts.Frame dev54h2Frame = this.dev54h2WheelParts != null && !dev54fRodHide
                 ? this.dev54h2WheelParts.begin(meshKey, this.vertices, FLOATS_PER_VERTEX) : null;
         // H8D is VISUAL ISOLATION ONLY. Fail closed to all-original render
         // if the model/source gate or any accepted H7 wheel face is missing.
         final boolean dev54h8dRequested = this.source.getPath().equals(
                 "legacy_meshes/locomotive_steam_berkshire_1225.tcm") && dev54h8dEnabled();
         final boolean dev54h8dCleanSlate = dev54h8dRequested && this.dev44R11Verified
                 && !r12Hide && !dev54fRodHide && dev54h2Frame != null
                 && dev54h2Frame.hasChunk(10) && dev54h2Frame.hasChunk(11)
                 && dev54h2Frame.hasChunk(12) && dev54h2Frame.hasChunk(13)
                 && dev54h2Frame.hasChunk(14) && dev54h2Frame.hasChunk(15)
                 && dev54h2Frame.hasChunk(16) && dev54h2Frame.hasChunk(17);
         // H8E requires all H8D source/model/H7 wheel and selector guards.
         // A separate opt-in: H8D-alone continues to hide exactly 41 chunks.
         final boolean dev54h8eSelected = this.source.getPath().equals(
                 "legacy_meshes/locomotive_steam_berkshire_1225.tcm") && dev54h8eEnabled();
         final boolean dev54h8eViewerMask = dev54h8eSelected && dev54h8dCleanSlate;
         if(dev54h8eViewerMask && DEV54H8E_REPORT.add(1)) LOGGER.info(
                 "[DEV54H8E-VIEWER-MASK] Berkshire1225 targetOriginalChunkIDs=75"
                 + " (H8D=41 newlyViewerTagged=34) viewerRod=24 viewerCrank=50"
                 + " viewerPistonPreserved=11 viewerOtherPreserved=1"
                 + " H7WheelFaces=14 originalTCM=UNCHANGED fixedCylinders=UNCHANGED");
         if(dev54h8eSelected && !dev54h8dCleanSlate && DEV54H8E_REPORT.add(90))
             LOGGER.warn("[DEV54H8E-HOLD] H8D clean gate/H7 wheel14 failed;"
                     + " original additional viewer-linked chunks NOT masked");
         if(dev54h8dCleanSlate && DEV54H8D_REPORT.add(1)) LOGGER.info(
                 "[DEV54H8D-CLEAN] Berkshire1225 legacy original rods/pins/crossheads"
                 + " MASKED=41 acceptedH7WheelFaces=14 originalTCM=UNCHANGED"
                 + " body/frame/fixedCylinderShells=UNTOUCHED other58=UNTOUCHED"
                 + " H8A_H8B_H8C_SOURCE=UNCHANGED cleanMode=VISUAL_PREVIEW_ONLY");
         if(dev54h8dRequested && !dev54h8dCleanSlate && DEV54H8D_REPORT.add(90))
             LOGGER.warn("[DEV54H8D-HOLD] Model/H7 drivers/legacy selector guard rejected;"
                     + " ORIGINAL_RUNNING_GEAR_VISIBLE");

// H8F reveals ONLY a curated fresh-linkage source subset on top of
// the accepted H8E clean-slate. It requires H8A and H8B selectors ON.
final boolean dev54h8fSelected = this.source.getPath().equals(
        "legacy_meshes/locomotive_steam_berkshire_1225.tcm") && dev54h8fEnabled();
final boolean dev54h8fFreshVisible = dev54h8fSelected && dev54h8eViewerMask
        && dev54h8aEnabled() && dev54h8bEnabled();
if(dev54h8fFreshVisible && DEV54H8F_REPORT.add(1)) LOGGER.info(
        "[DEV54H8F-FRESH] Berkshire1225 visibleCuratedChunks=34"
        + " coupling=26 mainRod=2 crosshead=6"
        + " hiddenLegacyResidual=41 viewerExtraHidden=34"
        + " hiddenPinAndPistonCandidates=7 fixedCylinderShells=UNCHANGED"
        + " requiresH8A_H8B_H8E_ON nativeH7wheels=14");
if(dev54h8fSelected && !dev54h8fFreshVisible && DEV54H8F_REPORT.add(90))
    LOGGER.warn("[DEV54H8F-HOLD] H8E clean slate plus H8A/H8B selectors required;"
            + " curated fresh linkage remains hidden");
         // DEV54H8A: applied after all older linkage plans have been resolved.
         // Require active H7 drivers, exact SHA-known Berkshire model and zero
         // legacy rod-mode conflict. Failure -> no H8 pose on EITHER side.
         if (this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_1225.tcm")
                 && dev54h8aEnabled()) {
             boolean guard=this.dev44R11Verified && dev54h2Frame!=null &&
                 dev54h2Frame.hasChunk(10) && dev54h2Frame.hasChunk(13) &&
                 dev54h2Frame.hasChunk(14) && dev54h2Frame.hasChunk(17) &&
                 !r12Hide && !dev54fRodHide && !dev44FleetActive &&
                 !r13Preview && !r13uEnabled && !r13oEnabled && !r13wIsolate;
             for(int ch=348;ch<=360;ch++) if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             for(int ch=734;ch<=746;ch++) if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             if(guard) {
                 var p=dev54h8aSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,true);
                 var n=dev54h8aSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,false);
                 if(p!=null && n!=null) {
                     float[] anchorP={-0.5f,-0.46875f,0.34375f};
                     float[] anchorN={-0.5f,-0.46875f,-0.459375f};
                     for(int ch=348;ch<=360;ch++)r13uPoses.put(ch,new Dev44R13uPose(p,anchorP));
                     for(int ch=734;ch<=746;ch++)r13uPoses.put(ch,new Dev44R13uPose(n,anchorN));
                 } else if(DEV54H8A_REPORT.add(90))LOGGER.warn(
                     "[DEV54H8A-HOLD] wheel phase/pin closure invalid; original coupling rods untouched");
             } else if(DEV54H8A_REPORT.add(91))LOGGER.warn(
                 "[DEV54H8A-HOLD] SHA/wheel14/legacy rod conflict; original coupling rods untouched");
         }
         // DEV54H8B sits strictly on top of an ACTIVE, passing H8A coupling pose.
         // Both sides succeed or BOTH stay H8A only. Fixed cylinder shells,
         // wheel geometry and accepted coupling rods are not transformed here.
         if(this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_1225.tcm")
                 && dev54h8bEnabled()) {
             boolean guard=dev54h8aEnabled() && this.dev44R11Verified &&
                 dev54h2Frame!=null && dev54h2Frame.hasChunk(12) && dev54h2Frame.hasChunk(15) &&
                 r13uPoses.containsKey(348) && r13uPoses.containsKey(734) &&
                 !r12Hide && !dev54fRodHide && !dev44FleetActive &&
                 !r13Preview && !r13uEnabled && !r13oEnabled && !r13wIsolate;
             for(int ch:new int[]{378,747,373,374,375,731,732,733})
                 if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             if(guard) {
                 var p=dev54h8bSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,true);
                 var n=dev54h8bSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,false);
                 if(p!=null && n!=null) {
                     r13uPoses.put(378,new Dev44R13uPose(p.pose(),p.restA()));
                     r13uPoses.put(747,new Dev44R13uPose(n.pose(),n.restA()));
                     for(int ch:new int[]{373,374,375})r13oOffsets.put(ch,new float[]{p.stroke(),0f,0f});
                     for(int ch:new int[]{731,732,733})r13oOffsets.put(ch,new float[]{n.stroke(),0f,0f});
                     // H8C: only the exact four source-identical nonmoving pin
                     // hardware chunks are added, AFTER both bilateral H8A/H8B
                     // closure checks pass. Source TCM and selectors stay frozen.
                     if(dev54h8cEnabled()) {
                         boolean hardwareGuard=dev54h8cPinGuard(this.vertices,FLOATS_PER_VERTEX)
                                 && r13uPoses.containsKey(741)
                                 && r13uPoses.containsKey(742)
                                 && r13uPoses.containsKey(743);
                         for(int ch:new int[]{379,389,390,380})
                             if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch)) hardwareGuard=false;
                         if(hardwareGuard) {
                             var positiveCoupling=r13uPoses.get(348);
                             for(int ch:new int[]{379,389,390})
                                 r13uPoses.put(ch,positiveCoupling);
                             r13oOffsets.put(380,new float[]{p.stroke(),0f,0f});
                             if(DEV54H8C_REPORT.add(0))LOGGER.info(
                                 "[DEV54H8C-PIN-HARDWARE] POS_Z crankPinPieces=379,389,390"
                                 +" motion=ACCEPTED_H8A_QUAD_PHASE crossheadCap=380"
                                 +" motion=H8B_SLIDER strokeSource=ACCEPTED_H8B bothSides=UNCHANGED"
                                 +" wheels=UNCHANGED rods=UNCHANGED cylinderShells=FIXED joint=CANDIDATE");
                         } else if(DEV54H8C_REPORT.add(90))LOGGER.warn(
                             "[DEV54H8C-HOLD] original pin hardware geometry conflict; no H8C transforms");
                     }
                 } else if(DEV54H8B_REPORT.add(90))LOGGER.warn(
                    "[DEV54H8B-HOLD] geometry/rod/slider closure rejected; main rods and crossheads UNCHANGED");
             } else if(DEV54H8B_REPORT.add(91))LOGGER.warn(
                    "[DEV54H8B-HOLD] H8A/wheel/source/conflict guard rejected; main rods and crossheads UNCHANGED");
         }
         // H9A: source-verified 765-only curated bilateral linkage, independent
         // of 1225 H8A/B/F. Fail closed to original 765 if either side fails.
         boolean dev54h9aActive = false;
         if(this.source.getPath().equals("legacy_meshes/locomotive_steam_berkshire_765.tcm")
                 && dev54h9aEnabled()) {
             boolean guard = this.dev44R11Verified && dev54h2Frame!=null
                     && !r12Hide && !dev54fRodHide && !dev44FleetActive
                     && !r13Preview && !r13uEnabled && !r13oEnabled;
             for(int ch=10;ch<=17;ch++)
                 if(dev54h2Frame==null || !dev54h2Frame.hasChunk(ch))guard=false;
             for(int ch=348;ch<=360;ch++)
                 if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             for(int ch=733;ch<=745;ch++)
                 if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             for(int ch:new int[]{378,746,373,374,375,730,731,732})
                 if(r13uPoses.containsKey(ch)||r13oOffsets.containsKey(ch))guard=false;
             if(guard) {
                 var cp=dev54h9aSolveCoupling(meshKey,this.vertices,FLOATS_PER_VERTEX,true);
                 var cn=dev54h9aSolveCoupling(meshKey,this.vertices,FLOATS_PER_VERTEX,false);
                 var sp=dev54h9aSolveSlider(meshKey,this.vertices,FLOATS_PER_VERTEX,true);
                 var sn=dev54h9aSolveSlider(meshKey,this.vertices,FLOATS_PER_VERTEX,false);
                 if(cp!=null && cn!=null && sp!=null && sn!=null) {
                     float[] anchorP={-0.5f,-0.46875f,0.34375f};
                     float[] anchorN={-0.5f,-0.46875f,-0.459375f};
                     for(int ch=348;ch<=360;ch++)r13uPoses.put(ch,new Dev44R13uPose(cp,anchorP));
                     for(int ch=733;ch<=745;ch++)r13uPoses.put(ch,new Dev44R13uPose(cn,anchorN));
                     r13uPoses.put(378,new Dev44R13uPose(sp.pose(),sp.restA()));
                     r13uPoses.put(746,new Dev44R13uPose(sn.pose(),sn.restA()));
                     for(int ch:new int[]{373,374,375})r13oOffsets.put(ch,new float[]{sp.stroke(),0f,0f});
                     for(int ch:new int[]{730,731,732})r13oOffsets.put(ch,new float[]{sn.stroke(),0f,0f});
                     dev54h9aActive = true;
                     if(dev54h9b765 && DEV54H9B_REPORT.add(2))LOGGER.info(
                         "[DEV54H9B-765-ACTIVE] oldR13wPoseConflict=BYPASSED"
                         + " H9A34Curated=ACTIVE extra38Masked=ACTIVE acceptedWheels=14");
                     if(DEV54H9A_REPORT.add(1))LOGGER.info(
                         "[DEV54H9A-765-LIVE] model=locomotive_steam_berkshire_765"
                         + " wheelFaces=14 visibleCurated=34 maskedExtraOldRodsCranks=38"
                         + " oldViewerRod=21 oldViewerCrank=51 preservedPiston=11"
                         + " coupling=26 main=2 crosshead=6 fixedRodLengthSolver=true"
                         + " originalTCM=UNCHANGED 1225=UNCHANGED VBShay2=UNCHANGED"
                         + " joints=ORIGINAL_MODEL_CANDIDATES");
                 } else if(dev54h9b765 && DEV54H9B_REPORT.add(92))LOGGER.warn(
                     "[DEV54H9B-SOLVER-HOLD] side outcome couplingP={} couplingN={} sliderP={} sliderN={}"
                     + " (false means that solver returned null; H9A remains safe HOLD)",
                     cp!=null,cn!=null,sp!=null,sn!=null);
             } else if(dev54h9b765 && DEV54H9B_REPORT.add(91))LOGGER.warn(
                 "[DEV54H9B-GUARD-HOLD] legacy debug bypassed but H9A guard still failed;"
                 + " verified={} nativeFrame={} legacyR12Hide={} dev54fHide={} fleet={}"
                 + " r13Preview={} r13u={} r13o={} existingPose={} existingOffset={}",
                 this.dev44R11Verified,dev54h2Frame!=null,r12Hide,dev54fRodHide,
                 dev44FleetActive,r13Preview,r13uEnabled,r13oEnabled,
                 r13uPoses.size(),r13oOffsets.size());
             if(!dev54h9aActive && DEV54H9A_REPORT.add(90))LOGGER.warn(
                     "[DEV54H9A-HOLD] 765 source/wheel14/pose/slider closure or legacy conflict;"
                     + " original geometry preserved, no H9A mask/poses");
         }

         // H10A: both sides / all three driving axles MUST agree before hiding
         // any original rod/crank chunk. No main rod or piston prototype yet.
         boolean dev54h10aActive=false;
         if(dev54h10aRequested) {
             final String h10Path=this.source.getPath();
             final boolean mallard=h10Path.equals("legacy_meshes/locomotive_steam_a4_mallard.tcm");
             final int[] required=mallard?new int[]{0,1,2,7,8,9}:new int[]{118,119,120,121,122,123};
             boolean guard=this.dev44R11Verified && !r12Hide && !dev54fRodHide
                     && !r13Preview && !r13uEnabled && !r13oEnabled
                     && !dev44FleetActive && dev54h2Frame!=null
                     && r13uPoses.isEmpty() && r13oOffsets.isEmpty();
             for(int wi:required)if(dev54h2Frame==null||!dev54h2Frame.hasChunk(wi))guard=false;
             if(guard) {
                 var shiftP=dev54h10aCouplingShift(meshKey,this.vertices,FLOATS_PER_VERTEX,mallard,true);
                 var shiftN=dev54h10aCouplingShift(meshKey,this.vertices,FLOATS_PER_VERTEX,mallard,false);
                 if(shiftP!=null && shiftN!=null) {
                     final int maxChunk=this.vertexCount/24;
                     if((mallard&&maxChunk>366)||(!mallard&&maxChunk>727)) {
                         final int first=mallard?84:498, last=mallard?90:526;
                         final int otherFirst=mallard?336:527, otherLast=mallard?341:555;
                         for(int ci=first;ci<=last;ci++)if(dev54h10aCouplingChunk(h10Path,ci))
                             r13oOffsets.put(ci,(mallard?shiftN:shiftP));
                         for(int ci=otherFirst;ci<=otherLast;ci++)if(dev54h10aCouplingChunk(h10Path,ci))
                             r13oOffsets.put(ci,(mallard?shiftP:shiftN));
                         dev54h10aActive=true;
                         if(DEV54H10A_REPORT.add(mallard?1:2))LOGGER.info(
                             "[DEV54H10A-LIVE] model={} drivingAxlesPerSide=3"
                             + " acceptedDriverFaces=6 curatedCouplingChunks={} hiddenImportedRodCrank={}"
                             + " pistonAndFixedCylinders=UNCHANGED originalTCM=UNCHANGED"
                             + " 1225_765_UNCHANGED linkedMotion=RIGID_EQUAL_PHASE_ONLY"
                             + " mainRods=DEFERRED crossheads=DEFERRED",
                             mallard?"A4_MALLARD":"KING_CLASS",mallard?12:58,mallard?66:122);
                     }
                 }
                 if(!dev54h10aActive && DEV54H10A_REPORT.add(mallard?91:92))LOGGER.warn(
                     "[DEV54H10A-HOLD] model={} both-side 3-axle phase/pin source validation rejected;"
                     + " old geometry preserved",mallard?"A4_MALLARD":"KING_CLASS");
             } else if(DEV54H10A_REPORT.add(mallard?93:94)) LOGGER.warn(
                 "[DEV54H10A-HOLD] model={} native H6 faces / legacy pose / source guard rejected;"
                 + " old geometry preserved",mallard?"A4_MALLARD":"KING_CLASS");
         }

         // DEV54H10B: additive bilateral main rod + slider phase, H10A stays
         // the authority for coupling and imported clutter removal.
         boolean dev54h10bActive=false;
         if(dev54h10bSelected(this.source.getPath())) {
             final boolean mallard=this.source.getPath().equals(
                     "legacy_meshes/locomotive_steam_a4_mallard.tcm");
             final int[] mains=mallard?new int[]{91,335}:
                     new int[]{560,561,562,640,641,642};
             final int[] slider=mallard?
                     new int[]{294,295,296,297,298,299,356,357,358,359,360,364}:
                     new int[]{567,568,569,570,571,572,573,574,575,576,577,
                               578,579,580,581,582,583,584,585,586,587,588,
                               589,590,591,592,593,594,595,596,597,598,599,
                               600,601,602,603,604,605,606,607,608,609,610,
                               611,612,613,614,615,616,617,618,619,620,621,
                               622,623,624,625,633,634,635,636,637,638,639};
             boolean guard=dev54h10aActive && this.dev44R11Verified &&
                     !r12Hide && !dev54fRodHide && !dev44FleetActive &&
                     !r13Preview && !r13uEnabled && !r13oEnabled &&
                     dev54h2Frame!=null;
             for(int id:mains)if(!dev54h10aHideOtherOriginal(this.source.getPath(),id)||
                     r13uPoses.containsKey(id)||r13oOffsets.containsKey(id))guard=false;
             for(int id:slider)if(!dev54h10aHideOtherOriginal(this.source.getPath(),id)||
                     r13uPoses.containsKey(id)||r13oOffsets.containsKey(id))guard=false;
             if(guard) {
                 var pos=dev54h10bSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,mallard,true);
                 var neg=dev54h10bSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,mallard,false);
                 if(pos!=null && neg!=null) {
                     if(mallard) {
                         r13uPoses.put(335,new Dev44R13uPose(pos.pose(),pos.restA()));
                         r13uPoses.put(91,new Dev44R13uPose(neg.pose(),neg.restA()));
                         for(int id:new int[]{356,357,358,359,360,364})
                             r13oOffsets.put(id,new float[]{pos.stroke(),0f,0f});
                         for(int id:new int[]{294,295,296,297,298,299})
                             r13oOffsets.put(id,new float[]{neg.stroke(),0f,0f});
                     } else {
                         for(int id:new int[]{560,561,562})
                             r13uPoses.put(id,new Dev44R13uPose(pos.pose(),pos.restA()));
                         for(int id:new int[]{640,641,642})
                             r13uPoses.put(id,new Dev44R13uPose(neg.pose(),neg.restA()));
                         for(int id=567;id<=599;id++)
                             r13oOffsets.put(id,new float[]{pos.stroke(),0f,0f});
                         for(int id=600;id<=625;id++)
                             r13oOffsets.put(id,new float[]{neg.stroke(),0f,0f});
                         for(int id=633;id<=639;id++)
                             r13oOffsets.put(id,new float[]{neg.stroke(),0f,0f});
                     }
                     dev54h10bActive=true;
                     if(DEV54H10B_REPORT.add(mallard?50:51)) LOGGER.info(
                         "[DEV54H10B-LIVE] model={} mains={} slidingCrossheadPieces={}"
                         +" H10A=UNCHANGED H7_WHEELS=UNCHANGED fixedCylinders=UNCHANGED"
                         +" originalTCM=UNCHANGED bothSideRodLength=CONSTANT"
                         +" pinJoints=MODEL_CANDIDATES oldClutter=MASKED",
                         mallard?"A4_MALLARD":"KING_CLASS",mallard?2:6,mallard?12:66);
                 }
             }
             if(!dev54h10bActive && DEV54H10B_REPORT.add(mallard?90:91)) LOGGER.warn(
                 "[DEV54H10B-HOLD] model={} H10A/linkage/guide/legacy guard failed;"
                 +" H10A appearance retained, original rods remain hidden",
                 mallard?"A4_MALLARD":"KING_CLASS");
         }


         // DEV54H11A: source-qualified small-loco coupling pilot. Reuse only
         // the native wheel phase after BOTH sides validate. No main rod poses.
         boolean dev54h11aActive=false;
         if(dev54h11aRequested) {
             final boolean is262=this.source.getPath().equals(
                     "legacy_meshes/locomotive_steam_262t.tcm");
             final int[] required=is262?new int[]{0,1,2,3,4,5}:
                     new int[]{0,1,2,3};
             final boolean framePresent=is262?dev54hFrame!=null:
                     dev54h2Frame!=null;
             boolean guard=this.dev44R11Verified && !r12Hide &&
                     !dev54fRodHide && !r13Preview && !r13uEnabled &&
                     !r13oEnabled && !dev44FleetActive && framePresent &&
                     r13uPoses.isEmpty() && r13oOffsets.isEmpty();
             for(int id:required)
                 if(is262?(dev54hFrame==null||!dev54hFrame.hasChunk(id)):
                          (dev54h2Frame==null||!dev54h2Frame.hasChunk(id)))guard=false;
             if(guard) {
                 var pos=dev54h11aShift(meshKey,this.vertices,FLOATS_PER_VERTEX,is262,true);
                 var neg=dev54h11aShift(meshKey,this.vertices,FLOATS_PER_VERTEX,is262,false);
                 if(pos!=null && neg!=null) {
                     r13oOffsets.put(is262?29:139,pos);
                     r13oOffsets.put(is262?31:132,neg);
                     dev54h11aActive=true;
                     if(DEV54H11A_REPORT.add(is262?50:51))LOGGER.info(
                         "[DEV54H11A-LIVE] model={} drivingAxlesPerSide={}"
                        +" animatedCouplingBars=2 deferredMainRodFragments={}"
                        +" acceptedWheels=UNCHANGED other57Models=UNCHANGED"
                        +" fixedCylinders=UNCHANGED sourceTCM=UNCHANGED"
                        +" 1225_765_Mallard_King=UNCHANGED VBShay2=WHEELS_ONLY",
                         is262?"262T":"ALICE_040",is262?3:2,is262?2:6);
                 }
             }
             if(!dev54h11aActive && DEV54H11A_REPORT.add(is262?90:91))
                 LOGGER.warn("[DEV54H11A-HOLD] model={} wheel/source/phase/"
                    +"legacy geometry guard failed; original geometry preserved",
                    is262?"262T":"ALICE_040");
         }

         int dev54fLastChunk = -1;
         boolean dev54fSkip = false;
         boolean dev54fAcceptedWheel = false; // F5: only baked wheel chunks retain live motion.
        final String dev54h12bPath=this.source.getPath();
        final boolean dev54h12bRequested=dev54h12bSelected(dev54h12bPath);
        final String dev54h12bId=dev54h12bRequested
                ?dev54h12bPath.substring("legacy_meshes/".length(),dev54h12bPath.length()-4):"";
        if (dev54h12bRequested && DEV54H12B_LOGGED.add(dev54h12bId+":B1-SCOPE"))
            LOGGER.info("[DEV54H12B1-SCOPE] model={} oldGenericFleetPreviewActive={} exactH12BSelected=true previewSuppression=MODEL_SCOPED_ONLY otherModels=UNCHANGED",dev54h12bId,dev44FleetEnabled());
        final float dev54h12bPhase=dev54h12bRequested
                ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h12bPhase(meshKey)
                :Float.NaN;
        final boolean dev54h12bActive=dev54h12bRequested && this.dev44R11Verified
                && !r12Hide && !dev54fRodHide && !r13Preview && !r13uEnabled
                && !r13oEnabled && !dev44FleetActive && !r13jAlco
                && r13uPoses.isEmpty() && r13oOffsets.isEmpty()
                && Float.isFinite(dev54h12bPhase)
                && dev54h12bPivotGuard(meshKey,this.vertices,FLOATS_PER_VERTEX,dev54h12bId);
        boolean dev54h12fActive=false;
        boolean dev54h12g440Active=false,dev54h12gAlcoActive=false;
        if(dev54h12bActive) {
            final boolean dev54h12dEnabled=dev54h12dSourceGate(meshKey,dev54h12bId,this.vertices);
            if(dev54h12dEnabled) {
                dev54h12dAddMotion(dev54h12bId,dev54h12bPhase,r13oOffsets);
                if(dev54h12eModel(dev54h12bId)) {
                    dev54h12eMotion(dev54h12bId,dev54h12bPhase,r13oOffsets);
                    if(DEV54H12B_LOGGED.add(dev54h12bId+":H12E-LIVE"))
                        LOGGER.info("[DEV54H12E-LIVE] model={} H12Dbase=true exactOptIn=true acceptedFleet=UNCHANGED",dev54h12bId);
                }
                if(DEV54H12F_ENABLED && dev54h12eModel(dev54h12bId)
                      && dev54h12bId.equals("locomotive_steam_br_black_5")) {
                    // Complete BOTH sides or leave H12E running-gear intact.
                    var neg=dev54h12fSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,false);
                    var pos=dev54h12fSolve(meshKey,this.vertices,FLOATS_PER_VERTEX,true);
                    if(neg!=null&&pos!=null) {
                        dev54h12fApply(neg,pos,r13uPoses,r13oOffsets);
                        dev54h12fActive=true;
                        if(DEV54H12F_REPORTED.add("LIVE"))LOGGER.info(
                         "[DEV54H12F-LIVE] model=locomotive_steam_br_black_5 mainRods=12 slidingCrossheadParts=18 fixedGuides=6 cylinderShells=UNCHANGED coupling=H12E_PRESERVED strokeNeg={} strokePos={} rodLengthNeg={} rodLengthPos={} residualNeg={} residualPos={} sourceCandidates=YES runtimeVisualPASS=NO",
                            neg.stroke(),pos.stroke(),neg.length(),pos.length(),
                            neg.residual(),pos.residual());
                    } else if(DEV54H12F_REPORTED.add("HOLD"))
                        LOGGER.warn("[DEV54H12F-HOLD] Black 5 source/wheel/slider guard failed. H12E retained without H12F masking.");
                }
                if(DEV54H12G_ENABLED && dev54h12eModel(dev54h12bId)){
                    if(dev54h12bId.equals("locomotive_steam_4_4_0")){
                        dev54h12g440Active=DEV54H12I_ENABLED && DEV54H12H_ENABLED
                            ?dev54h12i440(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses,r13oOffsets)
                            :(DEV54H12H_ENABLED
                              ?dev54h12h440(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses)
                              :dev54h12g440(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses));
                        if(DEV54H12I_ENABLED && DEV54H12I_REPORTED.add("440"))LOGGER.info(
                            "[DEV54H12I-440] wheelPinnedAngled=64,66 axialSlider=82,80 connected={} fixedShells=8,81 needVisualPASS=true",dev54h12g440Active);
                        if(DEV54H12H_ENABLED && DEV54H12H_REPORTED.add("440"))
                            LOGGER.info("[DEV54H12H-440] sourceEndcapAnchors=true leftRight={} mirrorHousing=81 fixed visualApproval=STILL_REQUIRED",dev54h12g440Active);
                        if(DEV54H12G_REPORTED.add("440"))LOGGER.info(
                            "[DEV54H12G-440] mirrorShell=81 fixed restored={} pinnedMainRods=64,66 bothSides={}",
                            dev54h12g440Active,dev54h12g440Active);
                    } else if(dev54h12bId.equals("locomotive_steam_alco_sc4")){
                        dev54h12gAlcoActive=DEV54H12I_ENABLED && DEV54H12H_ENABLED
                            ?dev54h12iAlco(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses,r13oOffsets)
                            :(DEV54H12H_ENABLED
                              ?dev54h12hAlco(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses,r13oOffsets)
                              :dev54h12gAlco(meshKey,this.vertices,FLOATS_PER_VERTEX,r13uPoses,r13oOffsets));
                        if(DEV54H12I_ENABLED && DEV54H12I_REPORTED.add("ALCO"))LOGGER.info(
                            "[DEV54H12I-ALCO] fittedDepth=+/-0.0625 originalBars=34,35,37,38 connected={} fixedCylinders=UNCHANGED needVisualPASS=true",dev54h12gAlcoActive);
                        if(DEV54H12H_ENABLED && DEV54H12H_REPORTED.add("ALCO"))
                            LOGGER.info("[DEV54H12H-ALCO] sourceRodEnds=true crankPin36And42={} fixedCylinders=UNCHANGED visualApproval=STILL_REQUIRED",dev54h12gAlcoActive);
                        if(DEV54H12G_REPORTED.add("ALCO"))LOGGER.info(
                            "[DEV54H12G-ALCO] frontCylinderShells=FIXED sourceMainRods=34,35,37,38 sliderRods=32,41 bilateral={}",dev54h12gAlcoActive);
                    } else if(dev54h12bId.equals("locomotive_steam_br_black_5")
                           && DEV54H12F_ENABLED && dev54h12fActive
                           && DEV54H12G_REPORTED.add("BLACK5"))LOGGER.info(
                            "[DEV54H12G-BLACK5] userSelectedOriginalChunk=258 hidden=true from=H12C_CSV");
                }
                dev54h12dPhaseProbe(dev54h12bId,dev54h12bPhase);
                if(DEV54H12B_LOGGED.add(dev54h12bId+":H12D-LIVE"))
                    LOGGER.info("[DEV54H12D-LIVE] model={} exactMesh=true nativeWheelSource=UNCHANGED runningGearMotion=H12D scopedModel=true",dev54h12bId);
            } else dev54h12bAddMotion(dev54h12bId,dev54h12bPhase,r13oOffsets);
            if(DEV54H12B_LOGGED.add(dev54h12bId+":LIVE")) LOGGER.info(
                 "[DEV54H12B-LIVE] model={} SOURCE_ORIGINAL_SHA=INSTALL_VERIFIED wheelPhase={} sourceTags={} movingSourceChunks={} fixedPistons=UNCHANGED otherModels=UNCHANGED",
                 dev54h12bId,dev54h12bPhase,dev54h12bTags(dev54h12bPath).size(),
                 dev54h12bVisible(dev54h12bPath).size());
        } else if(dev54h12bRequested && DEV54H12B_LOGGED.add(dev54h12bId+":HOLD")) {
            LOGGER.warn("[DEV54H12B-HOLD] model={} sourceGate={} phase={} olderPoses={} olderOffsets={} legacyR13={} H12B did not mask original pieces",
                  dev54h12bId,this.dev44R11Verified,dev54h12bPhase,r13uPoses.size(),r13oOffsets.size(),
                  r13jAlco || r13oEnabled || r13uEnabled || r13Preview);
        }

        // DEV54H13 exact five-model pilot: opt-in, source SHA verified by
        // installer; never mask when phase/pose/geometry/protection gates fail.
        final boolean dev54h13Requested=DEV54H13_ON &&
                   dev54h13CouplingModel(this.source.getPath());
        final float dev54h13Phase=dev54h13Requested
                ?traincraft.client.renderer.animation.BakedWheelAnimationContext.dev54h12bPhase(meshKey)
                :Float.NaN;
        final boolean dev54h13Active=dev54h13Requested && this.dev44R11Verified
                && Float.isFinite(dev54h13Phase) && !r12Hide && !dev54fRodHide
                && !r13Preview && !r13oEnabled && !r13uEnabled
                && !dev44FleetActive && !r13jAlco
                && r13uPoses.isEmpty() && r13oOffsets.isEmpty()
                && dev54h13GeometryGuard(this.source.getPath(),this.vertices,FLOATS_PER_VERTEX);
        if(dev54h13Active){
            dev54h13Couplings(this.source.getPath(),dev54h13Phase,r13oOffsets);
            if(DEV54H13_REPORTED.add(this.source.getPath()+":LIVE"))LOGGER.info(
              "[DEV54H13-LIVE] model={} sourceWheelFaces=EXACT twoSideCouplings=2"
              +" taggedOriginalRodCrank={} sourceMovingParts=2 fixedPistonCylinders=UNCHANGED"
              +" shapeAudit=PASS runtimeVisualPASS=NO",this.source.getPath(),
              dev54h13Tagged(this.source.getPath()).size());
        }else if(dev54h13Requested && DEV54H13_REPORTED.add(this.source.getPath()+":HOLD")){
            LOGGER.warn("[DEV54H13-HOLD] model={} phase={} priorOffsets={} priorPoses={}"
                 +" originalsRemainedVisible=true",this.source.getPath(),dev54h13Phase,
                 r13oOffsets.size(),r13uPoses.size());
        }

        // H13B source-qualified finishing stage. The accepted H13 first-stage
        // coupling remains active. Every additional stage is separate opt-in.
        final boolean dev54h13bRequested=DEV54H13B_ON && DEV54H13_ON
                       && dev54h13bModel(this.source.getPath());
        final float dev54h13bPhase=dev54h13bRequested
              ?traincraft.client.renderer.animation.BakedWheelAnimationContext
                       .dev54h13bPartPhase(meshKey):Float.NaN;
        final boolean dev54h13bActive=dev54h13bRequested
              && (this.dev44R11Verified || this.source.getPath().equals(
                      "legacy_meshes/locomotive_steam_c62_body.tcm"))
              && Float.isFinite(dev54h13bPhase) && !r12Hide && !dev54fRodHide
              && !r13Preview && !r13oEnabled && !r13uEnabled && !dev44FleetActive
              && dev54h13bGeometry(this.source.getPath(),this.vertices,FLOATS_PER_VERTEX)
              && (this.source.getPath().equals("legacy_meshes/locomotive_steam_br80.tcm")
                ||this.source.getPath().equals("legacy_meshes/locomotive_steam_c62_body.tcm")
                ||this.source.getPath().equals("legacy_meshes/locomotive_steam_climax.tcm")
                ||dev54h13Active);
        final boolean dev54h13cActive=DEV54H13C_ON && dev54h13bActive
                && this.dev44R11Verified && dev54h13cRepairModel(this.source.getPath());
        // Exact original marked source pieces only; H13D must remain ON.
        // In particular, do NOT require dev44R11Verified for multipart C62:
        // it is not in the DEV44 R11 proof list but is H13B geometry-qualified.
        final boolean dev54h13eC11Active=DEV54H13E_ON && DEV54H13D_ON
                && dev54h13cActive && this.source.getPath().equals(
                   "legacy_meshes/locomotive_steam_c11.tcm");
        final boolean dev54h13eC62Active=DEV54H13E_ON && DEV54H13D_ON
                && DEV54H13C_ON && dev54h13bActive && this.source.getPath().equals(
                   "legacy_meshes/locomotive_steam_c62_body.tcm");
        final boolean dev54h13fActive=DEV54H13F_ON && DEV54H13E_ON && DEV54H13D_ON
                && dev54h13bActive && dev54h13fTarget(this.source.getPath());
        if(dev54h13fActive && DEV54H13F_REPORTED.add(this.source.getPath()+":LIVE"))
            LOGGER.info("[DEV54H13F-LIVE] model={} c41Family={} c62={} climax={} "
                    +"syntheticVisibleConnections=true parentCleanupPreserved=true visualPASS=NO",
                    this.source.getPath(), dev54h13dC41(this.source.getPath()),
                    this.source.getPath().equals("legacy_meshes/locomotive_steam_c62_body.tcm"),
                    this.source.getPath().equals("legacy_meshes/locomotive_steam_climax.tcm"));
        if ((dev54h13eC11Active || dev54h13eC62Active)
                && DEV54H13E_REPORTED.add(this.source.getPath()+":LIVE"))
            LOGGER.info("[DEV54H13E-LIVE] model={} C11marked298={} C62markedCount={} "
                +"C62SyntheticSuppressed={} exactSourceGeometry=true "
                +"originalMeshFiles=UNCHANGED visualPASS=NO",
                this.source.getPath(),dev54h13eC11Active,
                dev54h13eC62Active?DEV54H13E_C62_HIDE.size():0,
                dev54h13eC62Active);
        if(dev54h13bActive){
            dev54h13bOriginalMotion(this.source.getPath(),dev54h13bPhase,r13oOffsets);
            if(dev54h13cActive){
                dev54h13cSourceMotion(this.source.getPath(),dev54h13bPhase,r13oOffsets);
                if(DEV54H13D_ON) dev54h13dSourceMotion(this.source.getPath(),
                                               dev54h13bPhase,r13oOffsets);
                if(DEV54H14A_ON && this.source.getPath().equals(
                        "legacy_meshes/locomotive_steam_climax.tcm")){
                    float stroke=.125f*(float)Math.sin(dev54h13bPhase);
                    float[] along={.25882f*stroke,-.96593f*stroke,0f};
                    r13oOffsets.put(22,along);
                    r13oOffsets.put(59,along.clone());
                }
                if(DEV54H14B_ON && DEV54H14A_ON && dev54h14bClimax(this.source.getPath())){
                    // Correct user's observed moving-middle-rod error:
                    // these center parts are no longer translated.
                    r13oOffsets.remove(22);r13oOffsets.remove(59);
                    if(this.dev54h14bClimaxOuter==null)
                        this.dev54h14bClimaxOuter=dev54h14bClimaxFindOuter();
                    if(this.dev54h14bClimaxOuter[0]>=0 &&
                       this.dev54h14bClimaxOuter[1]>=0){
                        float stroke=.125f*(float)Math.sin(dev54h13bPhase);
                        float[] off={.25882f*stroke,-.96593f*stroke,0f};
                        r13oOffsets.put(this.dev54h14bClimaxOuter[0],off);
                        r13oOffsets.put(this.dev54h14bClimaxOuter[1],off.clone());
                    }
                }
                if(DEV54H13C_REPORTED.add(this.source.getPath()+":LIVE"))LOGGER.info(
                    "[DEV54H13C-LIVE] model={} sourceTexturedC62={} C11JointRepair={} userMarkedHides={} otherModels=UNCHANGED visualPASS=NO",
                    this.source.getPath(),this.source.getPath().endsWith("locomotive_steam_c62_body.tcm"),
                    this.source.getPath().endsWith("locomotive_steam_c11.tcm"),
                    DEV54H13C_MARKED.getOrDefault(this.source.getPath(),java.util.Set.of()).size());
            }
            if(DEV54H13B_REPORTED.add(this.source.getPath()+":LIVE"))LOGGER.info(
               "[DEV54H13B-LIVE] model={} originalAndDrawnLinkages=true"
               +" originalWheelAndCylinderMeshes=UNCHANGED visualPASS=NO",
               this.source.getPath());
        }else if(dev54h13bRequested && DEV54H13B_REPORTED.add(this.source.getPath()+":HOLD")){
            LOGGER.warn("[DEV54H13B-HOLD] model={} phase={} geometryOrPrerequisiteGuard=false",
                           this.source.getPath(),dev54h13bPhase);
            if(DEV54H13C_ON && DEV54H13C_REPORTED.add(this.source.getPath()+":HOLD"))
                LOGGER.warn("[DEV54H13C-HOLD] model={} verified={} phaseFinite={} hide={} "
                    +"legacyPreview={} r13Preview={} r13o={} r13u={} geometry={} H13Parent={}",
                    this.source.getPath(),this.dev44R11Verified,Float.isFinite(dev54h13bPhase),
                    r12Hide||dev54fRodHide,dev44FleetActive,r13Preview,r13oEnabled,r13uEnabled,
                    dev54h13bGeometry(this.source.getPath(),this.vertices,FLOATS_PER_VERTEX),
                    dev54h13Active);
        }

        // DEV58G5: both caches are per-render, not global or per-entity.
        // Chunk visibility is independent of vertex position; animation below remains
        // per-vertex. The original source, saved markers and rod/wheel settings are not modified.
        int dev58g5CachedMaskChunk = -1;
        boolean dev58g5CachedMasked = false;
        // DEV58G5R3: transient per-render, per-source-chunk decisions only.
        // Each source chunk is 24 vertices; moving geometry still runs per vertex.
        int dev58g5r3EarlyChunk = -1;
        boolean dev58g5r3EarlySkip = false;
        int dev58g5r3R10Chunk = -1;
        boolean dev58g5r3R10Mapped = false;
        int dev58g5r3PoseChunk = -1;
        boolean dev58g5r3HasPose = false, dev58g5r3HasOffset = false;
        Dev44R13uPose dev58g5r3CachedPose = null;
        float[] dev58g5r3CachedOffset = null;
        final boolean dev58g5r3J50Verified = DEV54H15AH_J50_STATIC_ON
                    && this.dev44R11Verified
                    && "legacy_meshes/locomotive_steam_j50.tcm".equals(this.source.getPath());
        for (int i = 0; i < this.vertices.length; i += FLOATS_PER_VERTEX) {
            int vertexIndex = i / FLOATS_PER_VERTEX;
            int chunkIndex = vertexIndex / 24;
            // DEV58G5R3: these seven mask decisions depend on the source chunk,
            // never on vertex position. Evaluate once for each chunk, preserving
            // short-circuit order and the original all-24-vertices skip semantics.
            if (chunkIndex != dev58g5r3EarlyChunk) {
                dev58g5r3EarlyChunk = chunkIndex;
                dev58g5r3EarlySkip =
                    (dev54hFrame != null && dev54hFrame.hasChunk(chunkIndex))
                    || (dev54h2Frame != null && dev54h2Frame.hasChunk(chunkIndex))
                    || (dev54h15hSourceMask && dev54h14Tags != null
                        && dev54h14Tags.rod(chunkIndex)
                        && traincraft.client.renderer.animation.BakedWheelAnimationContext
                             .spec(meshKey,chunkIndex,this.vertices,FLOATS_PER_VERTEX)==null)
                    || (dev54h15Replacing && !DEV54H15H_ON && dev54h14Tags!=null
                        && dev54h14Tags.rod(chunkIndex)
                        && traincraft.client.renderer.animation.BakedWheelAnimationContext
                             .spec(meshKey,chunkIndex,this.vertices,FLOATS_PER_VERTEX)==null)
                    || (dev54h14Active && dev54h14Tags.rod(chunkIndex)
                        && traincraft.client.renderer.animation.BakedWheelAnimationContext
                             .spec(meshKey,chunkIndex,this.vertices,FLOATS_PER_VERTEX)==null)
                    || (dev54h9aActive && dev54h9aOldUnwantedChunk(chunkIndex))
                    || (dev54h11aActive && dev54h11aDeferredLink(this.source.getPath(),chunkIndex));
            }
            if (dev58g5r3EarlySkip) {
                i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX;
                continue;
            }
            // H13 masks only original tagged duplicates after source + phase
            // and both side-rod gates validate. Climax gearing never masked.
            if(DEV54H14B_ON && DEV54H14A_ON && dev54h13bActive &&
                    dev54h14bC41(this.source.getPath()) &&
                    dev54h13Tagged(this.source.getPath()).contains(chunkIndex) &&
                    traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey,chunkIndex,this.vertices,FLOATS_PER_VERTEX)==null){ i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            if(dev54h13Active && dev54h13Tagged(this.source.getPath()).contains(chunkIndex)
                    && !dev54h13Visible(this.source.getPath()).contains(chunkIndex)
                    && !(dev54h13bActive && dev54h13bReShow(this.source.getPath(),chunkIndex))
                    && !(dev54h13cActive && dev54h13cRestore(this.source.getPath(),chunkIndex))){ i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // H13E exact H12C user export (2026-10-17): C11 #298,
            // C62 32 body chunks; original wheel faces 0/1/8/12/21/26
            // and every unmarked piece remain untouched. Clean up only after
            // exact parent selector + H13B geometry/source gate passes.
            if(dev54h13eC11Active && chunkIndex==298) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            if(dev54h13eC62Active && DEV54H13E_C62_HIDE.contains(chunkIndex)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // C41-series and C11 removals are strictly user-picked CSV chunks.
            // Never infer a block ID from color or from a different model.
            if(dev54h13cActive && dev54h13cUserHide(this.source.getPath(),chunkIndex)
                 && traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .spec(meshKey,chunkIndex,this.vertices,FLOATS_PER_VERTEX)==null){ i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // DEV54H12B: no duplicate stationary originals, only the exact
            // two-sided source linkage set receives poses. Pistons stay fixed.
            // H12D isolated override: the single exported ALCO hide candidate
            // wins over H12B crank animation; nearby source pin 362 is kept.
            // Do not suppress chunk 358 when the H12D marker is OFF.
            if(dev54h12bActive && DEV54H12D_ENABLED
                && dev54h12bId.equals("locomotive_steam_alco_sc4") && chunkIndex==358) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            if(dev54h12bActive && DEV54H12D_ENABLED && dev54h12eModel(dev54h12bId)
                    && dev54h12bId.equals("locomotive_steam_adler")
                    && (chunkIndex==71 || chunkIndex==146 || chunkIndex==252 || chunkIndex==253)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            if(dev54h12bActive && dev54h12bTags(dev54h12bPath).contains(chunkIndex)
                    && !dev54h12bVisible(dev54h12bPath).contains(chunkIndex)
                    && !(dev54h12fActive && dev54h12fUnmask(chunkIndex))
                    && !(dev54h12g440Active && (chunkIndex==81 || chunkIndex==64 || chunkIndex==66))
                    && !(dev54h12gAlcoActive && (chunkIndex==32 || chunkIndex==34 || chunkIndex==35
                                    || chunkIndex==37 || chunkIndex==38 || chunkIndex==41
                                    || (DEV54H12H_ENABLED && (chunkIndex==36 || chunkIndex==42))))
                    && !(DEV54H12D_ENABLED
                         && dev54h12bId.equals("locomotive_steam_alco_sc4")
                         && chunkIndex==362)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            if(dev54h10aActive && dev54h10aHideOtherOriginal(this.source.getPath(),chunkIndex)
                    && !(dev54h10bActive && dev54h10bUnmask(this.source.getPath(),chunkIndex))) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // H8D clean preview: never touch new native 14 driver faces;
            // only suppress these ORIGINAL imported linkage mesh vertices.

if(dev54h8dCleanSlate && dev54h8dImportedRunningGear(chunkIndex)
        && !(dev54h8fFreshVisible && dev54h8fFreshRunningGearChunk(chunkIndex))) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // H8E: AFTER accepted H7 wheel emission (hasChunk tested above),
            // suppress additional original TCM rod/crank viewer-labeled chunks.
            if(dev54h8eViewerMask && dev54h8eExtraOldRodsOrCranks(chunkIndex)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
             if (dev54fRodHide) {
                 if (chunkIndex != dev54fLastChunk) {
                     dev54fLastChunk = chunkIndex;
                      var f5Wheel = traincraft.client.renderer.animation.BakedWheelAnimationContext
                              .spec(meshKey, chunkIndex, this.vertices, FLOATS_PER_VERTEX);
                      dev54fAcceptedWheel = f5Wheel != null && f5Wheel.rotates();
                     dev54fSkip = traincraft.debug.Dev54FRodMask.isRodOnly(this.source.getPath(), chunkIndex)
                              && f5Wheel == null;
                 }
                 if (dev54fSkip) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
             }


            // DEV44 R10: exact user-mapped chunks only; disabled unless explicitly enabled.
            if (r13Preview && chunkIndex != r13ActiveChunk) r13ActiveRole=dev44R13hRoles[chunkIndex];
            int r13Role = r13ActiveRole;
            // R13e: user's PISTON selections identify the fixed cylinder *shells*.
            // In preview mode restore them at authored coordinates, even if wheel data
            // is absent. Preview OFF retains exact R12b hide behavior for rollback.
            // ALCO SC4 is protected from experimental linkage motion. Re-show only the
            // pre-existing rod-labelled chunks at their authored static coordinates.
            // No change for any other mesh, cylinder shells, or crank classifications.
            final boolean r13jRod = r13jAlco && DEV44_R13_ROD_4.get(chunkIndex);
            boolean r13FixedShell = r13Preview && r13Role == 2;
            // DEV58G5R3: the motion maps are built before the vertex loop and
            // are not modified inside it. Resolve once per chunk; do not cache
            // transformed positions, phase, normals, or output geometry.
            if (chunkIndex != dev58g5r3PoseChunk) {
                dev58g5r3PoseChunk = chunkIndex;
                dev58g5r3HasPose = r13uPoses.containsKey(chunkIndex);
                dev58g5r3HasOffset = r13oOffsets.containsKey(chunkIndex);
                dev58g5r3CachedPose = r13uPoses.get(chunkIndex);
                dev58g5r3CachedOffset = r13oOffsets.get(chunkIndex);
            }
            if (chunkIndex != dev58g5r3R10Chunk) {
                dev58g5r3R10Chunk = chunkIndex;
                dev58g5r3R10Mapped = r12Hide && dev44R10Mapped(this.source.getPath(),chunkIndex);
            }
            if (dev58g5r3R10Mapped
                    && !r13FixedShell && !r13jRod && !dev58g5r3HasOffset && !dev58g5r3HasPose
                    && !(dev44FleetFrame!=null && dev44FleetFrame.has(chunkIndex))
                    && !(r13Preview && r13Role != 2 && r13Role > 0 && r13Wheel != null)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            boolean probeSelected = selectedChunk == chunkIndex && this.source.toString().equals(selectedMesh);
            // H15AH J50 chunk 38 is the ONLY new hide. 612 and 614 stay visible.
            // H15W source inspector may deliberately preview hidden #38.
            final boolean dev54h15ahJ50Verified = dev58g5r3J50Verified;
            if(dev54h15ahJ50Verified && chunkIndex == 38
                    && !(probeSelected && DEV54H15W_J50_PICK_ON)) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            final boolean dev54h15ahJ50KeepStatic = dev54h15ahJ50Verified
                    && (chunkIndex == 612 || chunkIndex == 614);
            if(dev54h15ahJ50KeepStatic && DEV54H15AH_LOGGED.add("J50"))
                LOGGER.info("[DEV54H15AH-J50-STATIC] chunks=612,614 at authored rest "
                    + "hide=38 oldMeshes=UNCHANGED nativeWheels=UNCHANGED");
            // DEV54H12G accepted user picker selection. Hide full source chunk
            // even when H12F previously unmasked or animated it.
            if(DEV54H12G_ENABLED && dev54h12bActive
                && dev54h12bId.equals("locomotive_steam_br_black_5")
                && chunkIndex==258){ i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // DEV54H12C: preview-hide ONLY user-picked original TCM chunks. Never edits mesh files,
            // accepted wheel-parts overlays, procedural rods, or persistent gameplay data.
            // H15W allows a currently selected J50 chunk to be seen in bright
            // magenta even if that chunk is already in the H12C hide preview.
            if (dev54h12cMask != null && dev54h12cMask.contains(chunkIndex)
                    && !(DEV54H15W_J50_PICK_ON && probeSelected &&
                          DEV54H15W_J50_MESH.equals(selectedMesh))) { i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX; continue; }
            // DEV58G5: H15L..H15AM exact hide selectors do not depend on the
            // current vertex.  Cache their result for all 24 source vertices.
            // Keeping this cache inside render() preserves edits between frames.
            if (chunkIndex != dev58g5CachedMaskChunk) {
                dev58g5CachedMaskChunk = chunkIndex;
                dev58g5CachedMasked =
                    dev54h15lMask.contains(chunkIndex)
                    || dev54h15tMask.contains(chunkIndex)
                    || dev54h15uLongMaskActive.contains(chunkIndex)
                    || dev54h15xHideRemainder(this.source.getPath(), chunkIndex)
                    || dev54h15yHideBatch4Source(this.source.getPath(), chunkIndex,
                           this.dev44R11Verified, probeSelected)
                    || dev54h15acHideOldRod(this.source.getPath(), chunkIndex,
                           this.dev44R11Verified)
                    || dev54h15aeHideUserExtra(this.source.getPath(), chunkIndex,
                           this.dev44R11Verified)
                    || dev54h15agHideJ50Block(this.source.getPath(), chunkIndex,
                           this.dev44R11Verified, probeSelected)
                    || dev54h15aiHideBatch5Source(chunkIndex, this.dev44R11Verified)
                    || dev54h15akHideSkookum(chunkIndex)
                    || dev54h15alHideS100(chunkIndex)
                    || dev54h15amHideB6(chunkIndex)
                    || (probeSelected && hideSelected);
            }
            if (dev58g5CachedMasked) {
                i += Math.min(23 - (vertexIndex % 24),
                    (this.vertices.length - i) / FLOATS_PER_VERTEX - 1) * FLOATS_PER_VERTEX;
                continue;
            }

            // TC_DEV38_R1G1Z7P3_440_PAIRED_WHEEL_PANEL_SPLIT
            // Truth sweep: chunks 0/1 are exact 1x6x14 two-wheel panels.
            if (!probeSelected && (vertexIndex % 24) == 0
                    && traincraft.client.renderer.animation.AuthoredPairedWheelPanelRenderer
                            .renderLegacy440PairedChunk(
                                    meshKey,
                                    chunkIndex,
                                    this.vertices,
                                    i,
                                    FLOATS_PER_VERTEX,
                                    pose,
                                    consumer,
                                    packedLight,
                                    packedOverlay)) {
                i += (23 * FLOATS_PER_VERTEX);
                continue;
            }

            if (chunkIndex != activeChunk) {
                activeChunk = chunkIndex;
                transform = traincraft.client.renderer.animation.BakedWheelAnimationContext
                        .spec(meshKey, chunkIndex, this.vertices, FLOATS_PER_VERTEX);
                if(DEV54H15K_ON)transform=dev54h15kCorrectWheel(meshKey,chunkIndex,transform);
                if(DEV54H15M_ON)transform=dev54h15mWheel(meshKey,chunkIndex,transform);
            }

            if (chunkIndex != r13ActiveChunk) {
                r13ActiveChunk=chunkIndex;
                r13Offset=null;
                // Only rods (1) and cranks (3) participate in the preview motion.
                // All shell geometry (2) is rigid and does not follow wheel transforms.
                if (r13Preview && r13Role != 2 && r13Role > 0 && r13Wheel != null) {
                    {
                        float px=dev44R13hCx[chunkIndex], py=dev44R13hCy[chunkIndex], pz=dev44R13hCz[chunkIndex];
                        float lateral=r13Wheel.axis()==0?px:(r13Wheel.axis()==1?py:pz);
                        float wheelLateral=r13Wheel.axis()==0?r13Wheel.centerX():(r13Wheel.axis()==1?r13Wheel.centerY():r13Wheel.centerZ());
                        int group=r13Role*2+(lateral>=wheelLateral?1:0);
                        // Rod and crosshead fragments share one rigid transform.
                        // Crank-pin pieces remain individually wheel-centered.
                        if(r13Role!=3 && group<r13GroupN.length && r13GroupN[group]>0) {
                            px=r13GroupSum[group][0]/r13GroupN[group];
                            py=r13GroupSum[group][1]/r13GroupN[group];
                            pz=r13GroupSum[group][2]/r13GroupN[group];
                        }
                        int side=group%2;
                        // Resolve wheel pivot per physical linkage group, rather than
                        // borrowing the first axle on the locomotive for all rods.
                        var localWheel=dev44R13NearestWheel(r13Wheels,px,py,pz,r13Wheel);
                        // Reject a mislabelled crank centroid belonging to another axle:
                        // the selected chunk itself is a safer fallback attachment.
                        float pinX=r13CrankAnchor[side][0],pinY=r13CrankAnchor[side][1],pinZ=r13CrankAnchor[side][2];
                        boolean hasPin=r13HasCrank[side];
                        if(hasPin) {
                            float dx=pinX-localWheel.centerX(),dy=pinY-localWheel.centerY(),dz=pinZ-localWheel.centerZ();
                            float ax=localWheel.axis()==0?dx:(localWheel.axis()==1?dy:dz);
                            if(dx*dx+dy*dy+dz*dz-ax*ax>0.45f*0.45f) hasPin=false;
                        }
                        r13Offset=dev44R13Motion(localWheel,r13Role,px,py,pz,
                                pinX,pinY,pinZ,hasPin);
                    }
                }
            }
            // DEV58G8R2: a non-null fleet frame may own SOME moving linkage chunks;
            // never cache frame.has(chunkIndex), but allow unaffected body chunks.
            // DEV58G8R1: selected MILW/Berkshire opt-in CPU packet cache for *static* source
            // chunks only. Every visibility/hide decision above still runs FIRST.
            // Dynamic wheels, rods, pistons, selected pieces, motion maps, and
            // editor previews always use the unmodified G7 per-vertex path.
            // R1: keep the FIRST certification frame unchanged (otherwise
            // G11 can never discover static chunks or upload its GPU body).
            // AFTER residency is attempted, GPU failures / rate-limit fallback
            // go straight to the ORIGINAL vertex path: no packet churn.
            // GPU-success frames (skipThisRender=true) keep the G11 fast path.
            if(dev58g8r1AllowedModel(this.source.getPath()) &&
                    !(dev58g11GpuMesh(this.source.getPath()) && dev58g9UploadAttempted &&
                      !dev58g9s2SkipThisRender) &&
                    consumer instanceof com.mojang.blaze3d.vertex.BufferBuilder dev58g8Target &&
                    dev58g8Target.building() && (vertexIndex%24)==0 &&
                    (this.vertices.length-i)>=24*FLOATS_PER_VERTEX &&
                    transform==null && r13Offset==null &&
                    !dev58g5r3HasPose && !dev58g5r3HasOffset &&
                    !pickNow && !probeSelected && !dev54h15Editing &&
                    !r13Preview && !r13kAlco && !r13jAlco &&
                    !dev54fRodHide && (dev44FleetFrame==null || !dev44FleetFrame.has(chunkIndex)) &&
                    // R7R1 GWR101: H15S animates ONLY the enumerated source
                    // rod/crank/slider chunks. Exclude those individually;
                    // retain the accepted animation and all other dynamic guards.
                    !(dev54h15sActive && dev54h15sMovingChunk(chunkIndex)) &&
                    // R16 narrows GWR whole-mesh guard ONLY with its explicit opt-in.
                    // Animated rods/crossheads remain CPU, unchanged, by source chunk ID.
                    (!(dev54h15tGwrLive || dev54h15wLive) ||
                        (DEV58G11R16_FIVE_ON &&
                         !(dev54h15tGwrLive && dev54h15tGwrRod(chunkIndex)) &&
                         !(dev54h15wLive && dev54h15w72Rod(chunkIndex)))) &&
                    !(DEV58G11R16_HALL_ON &&
                        dev58g11r16ProtectedHallChunk(this.source.getPath(),chunkIndex))) {
                // Preserve source->destination BufferBuilder format expectations:
                // this entity RenderType normally uses NEW_ENTITY. The pilot
                // is disabled by default; enable only for the visual A/B test.
                // G11: do NOT rebuild/retrieve the CPU pose packet for a GPU-resident,
                // re-certified Berkshire static chunk. Existing hide, wheel, piston,
                // picker and linkage conditions above are all satisfied first.
                if(dev58g11GpuMesh(this.source.getPath()) && dev58g9s2SkipThisRender &&
                        dev58g9EligibleStaticChunks.get(chunkIndex)) {
                    dev58g9s2Skipped.set(chunkIndex);
                    dev58g11FastSkips+=24L;
                    if(DEV58G10_METRICS_ON)dev58g10GpuSkippedVertices+=24L;
                    i+=23*FLOATS_PER_VERTEX;
                    continue;
                }
                java.nio.ByteBuffer packet=dev58g8r1StaticPacket(
                        chunkIndex,pose,packedLight,packedOverlay);
                if(packet!=null) {
                    // G9 proof records only G8R2-certified, actually visible chunks.
                    // No draw path changes; keep all G8R2 rendering semantics intact.
                    if((DEV58G9_GPU_UPLOAD_ONLY || dev58g11GpuMesh(this.source.getPath())) &&
                            !dev58g9UploadAttempted)
                        dev58g9EligibleStaticChunks.set(chunkIndex);
                    // Real GPU pilot: only suppress per-frame re-certified
                    // static chunks already present in G9's resident buffer.
                    if(dev58g9s2SkipThisRender &&
                            dev58g9EligibleStaticChunks.get(chunkIndex)) {
                        dev58g9s2Skipped.set(chunkIndex);
                        if(DEV58G10_METRICS_ON) dev58g10GpuSkippedVertices+=24L;
                    } else {
                        dev58g8Target.putBulkData(packet);
                        if(DEV58G10_METRICS_ON) dev58g10CpuPacketVertices+=24L;
                    }
                    if(!dev58g8r1Announced) {
                        dev58g8r1Announced=true;
                        LOGGER.info("[DEV58G8R2-PILOT] model={} firstStaticChunk={} mode=CPU_PACKET "
                            +"G7DynamicFallback=PRESERVED",this.source,chunkIndex);
                    }
                    i+=23*FLOATS_PER_VERTEX;
                    continue;
                }
            }
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            if(dev58g5r3HasPose) {
                transform=null; // rigid linkage owns the rod pose; wheels use accepted animator
            } else if(dev58g5r3HasOffset) {
                float[] motion=dev58g5r3CachedOffset;
                x+=motion[0];y+=motion[1];z+=motion[2];
                transform=null; // one authored group translation, no double rotation
            } else if (r13kAlco && r13jRod && !r13Wheels.isEmpty()) {
                // Conservative, reversible motion PROOF: each original rod-labelled chunk
                // translates with the nearest accepted wheel's eccentric crank phase.
                // The nearest authored crank centroid supplies an observed pin candidate.
                // This is NOT yet an exact pinned-end connecting-rod solver.
                float px=dev44R13kCx[chunkIndex],py=dev44R13kCy[chunkIndex],pz=dev44R13kCz[chunkIndex];
                var wheel=dev44R13NearestWheel(r13Wheels,px,py,pz,r13Wheels.get(0));
                int crank=dev44R13kNearestCrank[chunkIndex];
                float[] movement=dev44R13Motion(wheel,1,px,py,pz,
                        crank>=0?dev44R13kCx[crank]:px,
                        crank>=0?dev44R13kCy[crank]:py,
                        crank>=0?dev44R13kCz[crank]:pz,crank>=0);
                x+=movement[0];y+=movement[1];z+=movement[2];
                transform=null; // do not double-transform rods; wheels remain native
            } else if (r13FixedShell) {
                transform=null; // fixed shell never follows an animated wheel transform
            } else if (r13Offset != null) {
                x += r13Offset[0]; y += r13Offset[1]; z += r13Offset[2];
                transform=null; // Existing wheel animation has absolute priority; linkage moves once.
            }
            float nx = vertices[i + 5];
            float ny = vertices[i + 6];
            float nz = vertices[i + 7];

            var r13uPose=dev58g5r3CachedPose;
            if(r13uPose!=null) {
                // R13ab: only Berkshire 765 rod348, only with its guarded pose.
                // Calibrate mesh *render coordinates* once in rest pose; do not
                // mutate vertices[], cached geometry, authored assets or other rods.
                if(r13zBerkshire && chunkIndex==348) {
                    final float restAx=-0.546875f;
                    final float calibratedLength=2.4375f;
                    final float originalLength=2.53125f;
                    x=restAx+(x-restAx)*(calibratedLength/originalLength);
                    // Inverse transpose of constant X scale, renormalized.
                    float invX=originalLength/calibratedLength;
                    float nmag=(float)Math.sqrt(nx*nx*invX*invX+ny*ny+nz*nz);
                    if(nmag>1.0e-8f) { nx=nx*invX/nmag; ny/=nmag; nz/=nmag; }
                }
                float[] pt=r13uPose.pose().point(x-r13uPose.a()[0], y-r13uPose.a()[1], z-r13uPose.a()[2]);
                x=pt[0];y=pt[1];z=pt[2];
                // H12I: bring ALCO's source bar plane to its pin/crosshead plane.
                // This never affects the wheel faces, 4-4-0 or accepted engines.
                if(DEV54H12I_ENABLED && dev54h12gAlcoActive
                    && dev54h12bId.equals("locomotive_steam_alco_sc4")) {
                    if(chunkIndex==34||chunkIndex==35)z+=.0625f;
                    else if(chunkIndex==37||chunkIndex==38)z-=.0625f;
                }
                float[] normal=r13uPose.pose().normal(nx,ny,nz);nx=normal[0];ny=normal[1];nz=normal[2];
            }
            // DEV44 sweep runs after older linkage poses have had priority.
            // Native wheel transforms are never replaced or combined with this pass.
            if (dev44FleetFrame!=null && !dev58g5r3HasPose
                    && !dev58g5r3HasOffset
                    && (transform==null || !transform.rotates())) {
                var fleetMotion=dev44FleetFrame.get(chunkIndex);
                if(fleetMotion!=null) {
                    float[] moved=fleetMotion.pose.point(
                            x-fleetMotion.restA[0],y-fleetMotion.restA[1],z-fleetMotion.restA[2]);
                    float[] norm=fleetMotion.pose.normal(nx,ny,nz);
                    x=moved[0];y=moved[1];z=moved[2];
                    nx=norm[0];ny=norm[1];nz=norm[2];
                    transform=null;
                }
            }
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
                // H15M: outboard ONLY verified D51 driver wheel mesh pieces.
                // Keep fixed body, trucks, cylinders, save files and rods unchanged.
                if(dev54h15mOutboardWheel(transform))
                    z += Math.signum(transform.centerZ())*DEV54H15M_OUTBOARD;
            }

             // DEV54F5: Capture-only WHEEL-ONLY inspection pose. Preserve accepted baked wheel
             // rotation untouched. Staticize all other verified TCM vertices, including
             // mistaken rod/crank/piston transforms from legacy DEV44 motion systems.
             // Does not mutate the source array, asset mesh or any production animation.
             // DEV54F mask is SHA-verified, local-session-gated, and expires automatically.
             if (dev54fRodHide && !dev54fAcceptedWheel) {
                 x = vertices[i]; y = vertices[i + 1]; z = vertices[i + 2];
                 nx = vertices[i + 5]; ny = vertices[i + 6]; nz = vertices[i + 7];
             }

            // DEV54H15S: animate ONLY original rod/slider/crank chunks.
            // Source original geometry, 3D depth, UVs and normals are retained.
            // Cylinder shells, bodies, axle centers and wheels are unaffected.
            if(dev54h15sActive && dev54h15sMovingChunk(chunkIndex)){
                float[] xy=dev54h15sSourceMotion(chunkIndex,x,y,dev54h15sPhase);
                x=xy[0];y=xy[1];
                // Rotating the two exact source crank fittings also rotates normals.
                if(chunkIndex==93||chunkIndex==94||chunkIndex==60||chunkIndex==72){
                    float angle=(chunkIndex==93||chunkIndex==94)?dev54h15sPhase:
                        (float)Math.atan2(.09375f*((float)Math.cos(dev54h15sPhase)-1f),
                            chunkIndex==60?1.0625f:1.0f);
                    float sn=(float)Math.sin(angle),cs=(float)Math.cos(angle);
                    float oldNx=nx;
                    nx=oldNx*cs-ny*sn;
                    ny=oldNx*sn+ny*cs;
                }
            }
            // DEV54H15T: original GWR42xx coupling rods, crank fittings and two
            // slide guides. Source vertices are never written, UV/3D shape intact.
            if(dev54h15tGwrLive && dev54h15tGwrRod(chunkIndex)){
                float[] xy=dev54h15tGwr42Move(chunkIndex,x,y,dev54h15tGwrPhase);
                x=xy[0];y=xy[1];
                float angle=dev54h15tRodNormalAngle(chunkIndex,dev54h15tGwrPhase);
                if(angle!=0f){
                    float c=(float)Math.cos(angle),a=(float)Math.sin(angle);
                    float oldNx=nx;nx=oldNx*c-ny*a;ny=oldNx*a+ny*c;
                }
            }
            if(dev54h15wLive && dev54h15w72Rod(chunkIndex)){
                float[] xy=dev54h15wMove(chunkIndex,x,y,dev54h15wPhase);
                x=xy[0];y=xy[1];
                float angle=dev54h15wNormalAngle(chunkIndex,dev54h15wPhase);
                if(angle!=0f){
                    float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
                    float ox=nx;nx=ox*c-ny*s;ny=ox*s+ny*c;
                }
            }
            // H15AH: final post-transform restoration is intentional. Even if
            // a legacy DEV44/R13 or native wheel context classifies these two
            // original source chunks as animated, their red fittings remain fixed.
            if(dev54h15ahJ50KeepStatic) {
                x=vertices[i]; y=vertices[i+1]; z=vertices[i+2];
                nx=vertices[i+5]; ny=vertices[i+6]; nz=vertices[i+7];
            }
            // The optional picker retains its temporary selection lift, but no longer tints
            // the selected mesh; accepted authored vertex colors/textures render normally.
            if (probeSelected) y += 0.20F;
            if (pickNow) {
                int vi=vertexIndex % 4;
                Vector3f projected=pose.pose().transformPosition(x,y,z,new Vector3f());
                corners[vi][0]=projected.x(); corners[vi][1]=projected.y(); corners[vi][2]=projected.z();
                if (vi==3) {
                    double distance=Math.min(hitTriangle(corners[0],corners[1],corners[2],ray),
                                             hitTriangle(corners[0],corners[2],corners[3],ray));
                    if (distance<nearestHit && (dev54h15Editing || !dev54h12cOn() || dev54h12cEligible(this.source.toString()))) {
                        nearestHit=distance; hitMesh=this.source.toString(); hitChunk=chunkIndex;
                        if(DEV54H15_ON && dev54h15Editing){
                            var inverse=new org.joml.Matrix4f(pose.pose()).invert();
                            var local=new Vector3f(ray).mul((float)distance);
                            inverse.transformPosition(local);
                            dev54h15HitSource=this;
                            dev54h15HitX=local.x();dev54h15HitY=local.y();
                            dev54h15HitZ=local.z();
                        }
                    }
                }
            }
            // DEV58G7: perform the same position/normal matrix transforms and
            // submit the same white, UV, overlay, light and normal values.
            // Only the BufferBuilder path uses its optimized single-call writer.
            // Animated rods/pistons/wheels and all masks above remain untouched.
            if(DEV58G10_METRICS_ON) {
                dev58g10LegacyVertices++;
                // Known motion-map candidates only. This is a lower-bound
                // classification, NOT every animated vertex in the fleet.
                if(dev58g5r3HasPose || dev58g5r3HasOffset || r13Offset!=null || r13jRod)
                    dev58g10KnownMotionVertices++;
            }
            if (dev58g7Direct) {
                // JOML column-major matrix products; matches VertexConsumer's
                // vertex(Matrix4f,...) and normal(Matrix3f,...) transformations.
                final float px = dev58g7Position.m00() * x + dev58g7Position.m10() * y
                               + dev58g7Position.m20() * z + dev58g7Position.m30();
                final float py = dev58g7Position.m01() * x + dev58g7Position.m11() * y
                               + dev58g7Position.m21() * z + dev58g7Position.m31();
                final float pz = dev58g7Position.m02() * x + dev58g7Position.m12() * y
                               + dev58g7Position.m22() * z + dev58g7Position.m32();
                final float nnx = dev58g7Normal.m00() * nx + dev58g7Normal.m10() * ny
                                + dev58g7Normal.m20() * nz;
                final float nny = dev58g7Normal.m01() * nx + dev58g7Normal.m11() * ny
                                + dev58g7Normal.m21() * nz;
                final float nnz = dev58g7Normal.m02() * nx + dev58g7Normal.m12() * ny
                                + dev58g7Normal.m22() * nz;
                consumer.vertex(px, py, pz, 1.0f, 1.0f, 1.0f, 1.0f,
                        vertices[i + 3], vertices[i + 4], packedOverlay,
                        packedLight, nnx, nny, nnz);
            } else {
                // Frozen G5R3 path: exact original semantics for wrappers and OFF.
                consumer.vertex(pose.pose(), x, y, z)
                        .color(255, 255, 255, 255)
                        .uv(vertices[i + 3], vertices[i + 4])
                        .overlayCoords(packedOverlay)
                        .uv2(packedLight)
                        .normal(pose.normal(), nx, ny, nz)
                        .endVertex();
            }
        }
        // G9 proof: create ONE GPU-resident static-geometry buffer, after the
        // complete original vertex loop. Never replace or draw this buffer yet.
        dev58g9UploadOnlyProof();
        // H15A runs the fallback after original TCM vertices, independently
        // of whether H14 masks the original rods. No draw state is altered.
        if(pickNow && DEV54H15A_ON && dev54h15Editing)
            dev54h15aProbe(pose,ray);
        if(pickNow && DEV54H15B_ON && !DEV54H15C_ON && dev54h15Editing)
            dev54h15bProbe(pose,ray);
        if(DEV54H15F_ON && dev54h15Editing)
            dev54h15fObserve(pose);
        if(pickNow && DEV54H15C_ON && dev54h15Editing && !DEV54H15F_ON){
            if(DEV54H15E_ON)dev54h15eProbe(pose,ray);
            else if(DEV54H15D_ON)dev54h15dProbe(pose,ray);
            else dev54h15cProbe(pose,ray);
        }
        // C62: never stack synthetic H13B bars in front of textured source
        // rods. They acquired a dark/translucent appearance in the real client.
        // H13D removes non-selectable generated C41 black bars and Climax
        // synthetic overlays; original textured/source parts remain intact.
        if(!dev54h15Replacing && !dev54h14Active && dev54h13bActive && !((dev54h13cActive || dev54h13eC62Active) &&
                this.source.getPath().equals("legacy_meshes/locomotive_steam_c62_body.tcm"))
             && !(DEV54H13D_ON && dev54h13cActive &&
                    (dev54h13dC41(this.source.getPath()) ||
                     this.source.getPath().equals("legacy_meshes/locomotive_steam_climax.tcm"))))
            dev54h13bDraw(pose,consumer,packedLight,packedOverlay,
                                             this.source.getPath(),dev54h13bPhase);
        final boolean dev54h14aC62Active=DEV54H14A_ON && DEV54H14_ON &&
                dev54h13eC62Active && dev54h13fActive;
        if(!dev54h15Replacing && dev54h13fActive && !dev54h14Active &&
                !(DEV54H14A_ON && (dev54h14aC62Active ||
                  this.source.getPath().equals("legacy_meshes/locomotive_steam_climax.tcm"))))
            dev54h13fDraw(pose,consumer,packedLight,packedOverlay,
                          this.source.getPath(),dev54h13bPhase);
        if(dev54h14aC62Active){
            dev54h14aC62(pose,consumer,packedLight,packedOverlay,dev54h13bPhase);
            if(DEV54H14A_REPORTED.add(this.source.getPath()+":C62"))
                LOGGER.info("[DEV54H14A-C62-LIVE] model={} oldH13FBlackBars=OFF "
                    +"markedSourceRodsHidden=true replacementBilateral=true visualPASS=NO",
                    this.source.getPath());
        }
        // H14B only affects unfinished Batch 2 models. Original wheels,
        // fixed cylinders, C11, C62, BR80 and accepted models are untouched.
        final boolean dev54h14bC41Active=DEV54H14B_ON && !dev54h15Replacing && DEV54H14A_ON &&
                dev54h13bActive &&
                dev54h14bC41(this.source.getPath());
        final boolean dev54h14bBritRequested=DEV54H14B_ON && DEV54H14A_ON &&
                dev54h13Active && dev54h14bBrit(this.source.getPath()) &&
                Float.isFinite(dev54h13Phase);
        if(dev54h14bBritRequested && this.dev54h14bBritRig==null &&
                this.dev54h14bBritAttempts++<50)
            this.dev54h14bBritRig=dev54h14bBritPrepare(meshKey,dev54h14Tags);
        if(dev54h14bC41Active){
            float[] uv=dev54h14OriginalRodUV(dev54h14Tags);
            dev54h14bC41Draw(pose,consumer,packedLight,packedOverlay,
                             dev54h14bPhaseSafe(dev54h14Phase),uv[0],uv[1]);
            if(DEV54H14B_REPORTED.add(this.source.getPath()+":C41"))
                LOGGER.info("[DEV54H14B-C41-LIVE] model={} fourWheelBar=true "
                  +"mainPistonConnection=YES bothSides=true extraRearRod=OFF visualPASS=NO",
                  this.source.getPath());
        }else if(dev54h14Active){
            dev54h14Draw(pose,consumer,packedLight,packedOverlay,this.dev54h14Rig,
                         dev54h14Phase,this.source.getPath());
        }
        if(DEV54H15C_ON && dev54h15Editing)
            dev54h15cShowMarkers(pose,consumer,packedLight,packedOverlay);
            if(DEV54H15F_ON)
                dev54h15fShowMarkers(pose,consumer,packedLight,packedOverlay);
        if(dev54h15Rig(this.source.getPath())!=null){
            var custom=dev54h15Rig(this.source.getPath());
            float wheelPhase=traincraft.client.renderer.animation.BakedWheelAnimationContext
                            .dev54h13bPartPhase(meshKey);
            if(DEV54H15M_ON && dev54h15mTarget(this.source.getPath())){
                wheelPhase=dev54h15mSharedPhase;
                if(!Float.isFinite(wheelPhase) &&
                   DEV54H15M_REPORT.add(this.source.getPath()+":ROD-PHASE-HOLD"))
                    LOGGER.warn("[DEV54H15M-RODS-HOLD] model={} sourceWheelPhaseNotVerified " +
                        "rodCoordinatesPreserved=true",this.source);
            }
            float[] originalRodUv=dev54h14Tags==null?new float[]{.48f,.45f}
                              :this.dev54h14OriginalRodUV(dev54h14Tags);
            dev54h15Draw(pose,consumer,packedLight,packedOverlay,custom,wheelPhase,
                          originalRodUv[0],originalRodUv[1]);
            if(DEV54H15H_ON)dev54h15hDrawCouplings(pose,consumer,
                         packedLight,packedOverlay,custom,wheelPhase,
                         originalRodUv[0],originalRodUv[1]);
            if(DEV54H15_REPORTED.add(this.source.getPath()+":LIVE"))
                LOGGER.info("[DEV54H15-LIVE] model={} userEdited=true bothSides=true "
                        +"hideQualifiedOldRods={} sourceWheels=UNCHANGED visualPASS=NO",
                        this.source.getPath(),custom.replace());
        }
        // DEV54H15AM: new Batch 6 rods only; read-only wheel phase.
        if((DEV54H15AM_U57_ON && DEV54H15AM_U57.equals(this.source.getPath())) ||
           (DEV54H15AM_VB_ON && DEV54H15AM_VB.equals(this.source.getPath()))){
            float b6phase=traincraft.client.renderer.animation.BakedWheelAnimationContext
                .dev54h13bPartPhase(meshKey);
            if(DEV54H15AM_U57.equals(this.source.getPath()))
                dev54h15amDrawU57(pose,consumer,packedLight,packedOverlay,b6phase);
            if(DEV54H15AM_VB.equals(this.source.getPath()))
                dev54h15amDrawVB(pose,consumer,packedLight,packedOverlay,b6phase);
        }
        // DEV54H15AK: additive four Skookum piston/crank links only.
        // Existing H15, H14 and accepted baked-wheel render passes above remain as-is.
        if(DEV54H15AK_SK_RODS_ON && this.dev44R11Verified &&
           DEV54H15AK_SK_MESH.equals(this.source.getPath()))
            dev54h15akDrawSkookum(pose,consumer,packedLight,packedOverlay,
                traincraft.client.renderer.animation.BakedWheelAnimationContext
                    .dev54h13bPartPhase(meshKey));
        if(dev54h14bBritRequested && !dev54h15Replacing){
            if(this.dev54h14bBritRig!=null){
                dev54h14bBritDraw(pose,consumer,packedLight,packedOverlay,
                                  this.dev54h14bBritRig,dev54h13Phase);
                if(DEV54H14B_REPORTED.add(this.source.getPath()+":BRIT"))
                    LOGGER.info("[DEV54H14B-BRIT-LIVE] model={} actualPistonAnchors=YES "
                      +"bothSides=true fullCycleChecked=true visualPASS=NO",
                      this.source.getPath());
            }else if(this.dev54h14bBritAttempts>=50 &&
                      DEV54H14B_REPORTED.add(this.source.getPath()+":BRIT-HOLD"))
                LOGGER.warn("[DEV54H14B-BRIT-HOLD] model={} "
                  +"noQualifiedBilateralCylinderAnchor sourceRodsPreserved=true",
                  this.source.getPath());
        }
        if(DEV54H14B_ON && DEV54H14A_ON && dev54h13bActive &&
                dev54h14bClimax(this.source.getPath())){
            if(this.dev54h14bClimaxOuter==null)
                this.dev54h14bClimaxOuter=dev54h14bClimaxFindOuter();
            if(this.dev54h14bClimaxOuter[0]<0 || this.dev54h14bClimaxOuter[1]<0){
                float[] uv=dev54h14OriginalRodUV(dev54h14Tags);
                dev54h14bClimaxFallback(pose,consumer,packedLight,packedOverlay,
                                        dev54h13bPhase,uv[0],uv[1]);
            }
            if(DEV54H14B_REPORTED.add(this.source.getPath()+":CLIMAX"))
                LOGGER.info("[DEV54H14B-CLIMAX-LIVE] model={} middle22And59Slide=OFF "
                  +"outerNegative={} outerPositive={} generatedOuterFallback={} "
                  +"bilateralAngleMotion=true visualPASS=NO",this.source.getPath(),
                  this.dev54h14bClimaxOuter[0],this.dev54h14bClimaxOuter[1],
                  this.dev54h14bClimaxOuter[0]<0 || this.dev54h14bClimaxOuter[1]<0);
        }
        if (dev54hFrame != null) dev54hFrame.draw(pose, consumer, packedLight, packedOverlay);
        if (dev54h2Frame != null) dev54h2Frame.draw(pose, consumer, packedLight, packedOverlay);
        if (dev44R13gStarted != 0L) dev44R13gRecord(dev44R13gStarted, r13Preview, dev44R13fOn());
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
