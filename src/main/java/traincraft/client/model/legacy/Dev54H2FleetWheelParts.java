package traincraft.client.model.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import traincraft.client.renderer.animation.BakedWheelAnimationContext;

/** DEV54H2: opt-in source-verified subset only. 59-roster ledger; never infer unproven wheel chunks. */
final class Dev54H2FleetWheelParts {
  private static final Logger LOG = LogUtils.getLogger();
  private static final String MARKER = "DEV54H2-REAL-WHEELS-ON.txt";
  private static final int SEGMENTS = 24;
  // DEV54H5: smoother cylinder on the eight proven VB Shay 2 wheels only.
  // Centers, radius, UV, independent axle phase and all other models remain exact.
  private static final int VB_SHAY_2_SEGMENTS = 48;
  private static final float DEPTH = 0.03125f;
  private static final float POSITION_TOLERANCE = 0.001f;
  private record Config(String sha, int[] chunks, float[][] centers) {}
  private static final Map<String,Config> CONFIG = Map.ofEntries(
    Map.entry("traincraft:legacy_meshes/locomotive_steam_a4_mallard.tcm",new Config("172BA1D0F7180892D39733AB15CF539D7AFF1C586B6ABFBEDA52C1970FF84175",new int[]{3, 4, 5, 6, 10, 11},new float[][]{
       {-2.593750f, 0.406250f, 0.375780f},
       {-1.718750f, 0.406250f, 0.375780f},
       {-1.718750f, 0.406250f, -0.375470f},
       {-2.593750f, 0.406250f, -0.375470f},
       {3.343750f, 0.406250f, 0.374530f},
       {3.343750f, 0.406250f, -0.374220f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_alice_0_4_0.tcm",new Config("DB87B7F1C6177269203B8D2025C7CBBD34FF6C0F441A1674731F11ECAD263771",new int[]{0, 1, 2, 3},new float[][]{
       {-1.781250f, 0.406250f, 0.375630f},
       {-0.906250f, 0.406250f, 0.375630f},
       {-0.906250f, 0.406250f, -0.375630f},
       {-1.781250f, 0.406250f, -0.375630f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_berkshire_1225.tcm",new Config("9AD06147C0290FF22C33A568DDF664CCA3BAB00704824BB390DC960E4B818865",new int[]{22, 23, 24, 25, 26, 27},new float[][]{
       {-1.500000f, -0.375000f, -0.438130f},
       {-2.343750f, -0.375000f, -0.438130f},
       {-2.343750f, -0.375000f, 0.313130f},
       {-1.500000f, -0.375000f, 0.313130f},
       {3.187500f, -0.375000f, 0.313130f},
       {3.187500f, -0.375000f, -0.438130f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_berkshire_765.tcm",new Config("8A5588375BC8D284B29DCBA73984BF4EA127232209DE03FDB6AA744B7828A6C4",new int[]{22, 23, 24, 25, 26, 27},new float[][]{
       {-1.500000f, -0.375000f, -0.438130f},
       {-2.343750f, -0.375000f, -0.438130f},
       {-2.343750f, -0.375000f, 0.313130f},
       {-1.500000f, -0.375000f, 0.313130f},
       {3.187500f, -0.375000f, 0.313130f},
       {3.187500f, -0.375000f, -0.438130f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_king_class.tcm",new Config("69DA8E5507D314E6DDEAEA25C58DB157AD7C8CE8E3F68D256039C127B03324B9",new int[]{124, 125, 126, 127},new float[][]{
       {-2.406250f, 0.406250f, -0.375000f},
       {-2.406250f, 0.406250f, 0.375000f},
       {-3.593750f, 0.406250f, -0.375000f},
       {-3.593750f, 0.406250f, 0.375000f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_vb_shay_2.tcm",new Config("E68F978CA29FA3AA46ECBB6E65BF0367A17602A9846881A5AE8635D706656B50",new int[]{0, 2, 19, 20, 21, 22, 30, 32},new float[][]{
       {-1.313000f, 0.875000f, 0.313000f},
       {-0.750000f, 0.875000f, 0.313000f},
       {0.750000f, 0.875000f, -0.313000f},
       {1.313000f, 0.875000f, -0.313000f},
       {1.313000f, 0.875000f, 0.313000f},
       {0.750000f, 0.875000f, 0.313000f},
       {-0.750000f, 0.875000f, -0.313000f},
       {-1.313000f, 0.875000f, -0.313000f}
    }))
  );
  // DEV54H6 STAGED: opt-in full driver-face sets for A4 / King + first Cherepanov 4-wheel set.
  // Exact source TCM SHA/UV/axis verified. No rod/piston or body changes.
  private static final String H6_MARKER = "DEV54H6-EXPANDED-WHEEL-MODELS-ON.txt";
  private static final Map<String,Config> H6_CONFIG = Map.ofEntries(
    Map.entry("traincraft:legacy_meshes/locomotive_steam_a4_mallard.tcm",new Config("172BA1D0F7180892D39733AB15CF539D7AFF1C586B6ABFBEDA52C1970FF84175",new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},new float[][]{
       {-0.781250f, 0.156250f, 0.375780f},
       {0.468750f, 0.156250f, 0.375780f},
       {1.718750f, 0.156250f, 0.375780f},
       {-2.593750f, 0.406250f, 0.375780f},
       {-1.718750f, 0.406250f, 0.375780f},
       {-1.718750f, 0.406250f, -0.375470f},
       {-2.593750f, 0.406250f, -0.375470f},
       {-0.781250f, 0.156250f, -0.375470f},
       {0.468750f, 0.156250f, -0.375470f},
       {1.718750f, 0.156250f, -0.375470f},
       {3.343750f, 0.406250f, 0.374530f},
       {3.343750f, 0.406250f, -0.374220f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_king_class.tcm",new Config("69DA8E5507D314E6DDEAEA25C58DB157AD7C8CE8E3F68D256039C127B03324B9",new int[]{118, 119, 120, 121, 122, 123, 124, 125, 126, 127},new float[][]{
       {-0.343750f, 0.125000f, -0.375000f},
       {-0.343750f, 0.125000f, 0.375000f},
       {0.906250f, 0.125000f, 0.375000f},
       {0.906250f, 0.125000f, -0.375000f},
       {-1.593750f, 0.125000f, -0.375000f},
       {-1.593750f, 0.125000f, 0.375000f},
       {-2.406250f, 0.406250f, -0.375000f},
       {-2.406250f, 0.406250f, 0.375000f},
       {-3.593750f, 0.406250f, -0.375000f},
       {-3.593750f, 0.406250f, 0.375000f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_cherepanov.tcm",new Config("9A9C904AA183A0D3A4B74756FC16A0BF96A82CA0D4172238E8BC01F967833713",new int[]{2, 17, 18, 19},new float[][]{
       {0.625000f, 0.312500f, -0.343750f},
       {-0.187500f, 0.312500f, -0.343750f},
       {0.625000f, 0.312500f, 0.343750f},
       {-0.187500f, 0.312500f, 0.343750f}
    }))
  );
  private static boolean h6Enabled(String id) {
    try {
      var path=FMLPaths.GAMEDIR.get().resolve(H6_MARKER);
      if (!Files.isRegularFile(path)) return false;
      String data=Files.readString(path, StandardCharsets.UTF_8).replace("\ufeff", "");
      for (String line:data.split("\\R")) if(id.equalsIgnoreCase(line.trim())) return true;
    } catch (Exception ignored) {}
    return false;
  }
  // DEV54H7: verified eight added driving faces on each Berkshire, 6 -> 14 each.
  // New selector OFF unless explicitly enabled. Do not touch H6, rods, pistons or source TCMs.
  private static final String H7_MARKER = "DEV54H7-BERKSHIRE-DRIVERS-ON.txt";
  private static final Map<String,Config> H7_CONFIG = Map.ofEntries(
    Map.entry("traincraft:legacy_meshes/locomotive_steam_berkshire_1225.tcm",new Config("9AD06147C0290FF22C33A568DDF664CCA3BAB00704824BB390DC960E4B818865",new int[]{10, 11, 12, 13, 14, 15, 16, 17, 22, 23, 24, 25, 26, 27},new float[][]{
       {1.937500f, -0.562500f, 0.313125f},
       {1.125000f, -0.562500f, 0.313125f},
       {0.312500f, -0.562500f, 0.313125f},
       {-0.500000f, -0.562500f, 0.313125f},
       {-0.500000f, -0.562500f, -0.438125f},
       {0.312500f, -0.562500f, -0.438125f},
       {1.125000f, -0.562500f, -0.438125f},
       {1.937500f, -0.562500f, -0.438125f},
       {-1.500000f, -0.375000f, -0.438125f},
       {-2.343750f, -0.375000f, -0.438125f},
       {-2.343750f, -0.375000f, 0.313125f},
       {-1.500000f, -0.375000f, 0.313125f},
       {3.187500f, -0.375000f, 0.313125f},
       {3.187500f, -0.375000f, -0.438125f}
    })),
    Map.entry("traincraft:legacy_meshes/locomotive_steam_berkshire_765.tcm",new Config("8A5588375BC8D284B29DCBA73984BF4EA127232209DE03FDB6AA744B7828A6C4",new int[]{10, 11, 12, 13, 14, 15, 16, 17, 22, 23, 24, 25, 26, 27},new float[][]{
       {1.937500f, -0.562500f, 0.313125f},
       {1.125000f, -0.562500f, 0.313125f},
       {0.312500f, -0.562500f, 0.313125f},
       {-0.500000f, -0.562500f, 0.313125f},
       {-0.500000f, -0.562500f, -0.438125f},
       {0.312500f, -0.562500f, -0.438125f},
       {1.125000f, -0.562500f, -0.438125f},
       {1.937500f, -0.562500f, -0.438125f},
       {-1.500000f, -0.375000f, -0.438125f},
       {-2.343750f, -0.375000f, -0.438125f},
       {-2.343750f, -0.375000f, 0.313125f},
       {-1.500000f, -0.375000f, 0.313125f},
       {3.187500f, -0.375000f, 0.313125f},
       {3.187500f, -0.375000f, -0.438125f}
    }))
  );
  private static boolean h7Enabled(String id) {
    try {
      var path=FMLPaths.GAMEDIR.get().resolve(H7_MARKER);
      if (!Files.isRegularFile(path)) return false;
      String data=Files.readString(path, StandardCharsets.UTF_8).replace("\ufeff", "");
      for (String line:data.split("\\R")) if(id.equalsIgnoreCase(line.trim())) return true;
    } catch (Exception ignored) {}
    return false;
  }
  private final String id;
  private final int[] chunks;
  private final WheelPart[] parts;
  private Boolean lastReported;
  private Dev54H2FleetWheelParts(String id, int[] chunks, WheelPart[] parts) {
     this.id=id; this.chunks=chunks; this.parts=parts;
  }
  static Dev54H2FleetWheelParts fromSource(ResourceLocation source, byte[] raw, float[] verts, int stride) {
     Config cfg=CONFIG.get(source.toString());
     // H6 is entirely opt-in. If not enabled, existing H5 extraction is unchanged.
     Config extra=H6_CONFIG.get(source.toString());
     if (extra != null && h6Enabled(source.getPath().replace("legacy_meshes/", "").replace(".tcm", ""))) cfg=extra;
     Config h7=H7_CONFIG.get(source.toString());
     if (h7 != null && h7Enabled(source.getPath().replace("legacy_meshes/", "").replace(".tcm", ""))) cfg=h7;
     if (cfg==null) return null;
     if (!cfg.sha().equalsIgnoreCase(sha256(raw)) || stride!=8) {
         LOG.warn("[DEV54H2-HOLD] {} SHA/layout mismatch; original wheels unchanged", source); return null;
     }
     WheelPart[] parts=new WheelPart[cfg.chunks().length];
     for (int i=0;i<parts.length;i++) {
         int chunk=cfg.chunks()[i];
         if (chunk<0 || (long)(chunk+1)*24*stride>verts.length) return null;
         boolean cherepanov="locomotive_steam_cherepanov".equals(source.getPath().replace("legacy_meshes/", "").replace(".tcm", ""));
         boolean expanded=(extra != null && cfg==extra) || (h7 != null && cfg==h7);
         WheelPart w=WheelPart.extract(verts,chunk,stride, expanded, cherepanov);
         float[] c=cfg.centers()[i];
         if (w==null || !close(w.cx,c[0]) || !close(w.cy,c[1]) || !close(w.cz,c[2])) {
             LOG.warn("[DEV54H2-HOLD] {} invalid wheel face chunk {}; original preserved",source,chunk); return null;
         }
         parts[i]=w;
     }
     LOG.info("[DEV54H2-READY] model={} source-verified wheel faces={} partial-model-possible=true",source,parts.length);
     if (extra != null && cfg==extra) LOG.info("[DEV54H6-READY] model={} all={} source-verified wheel faces; H5 fallback available via H6 marker OFF",source,parts.length);
     if (h7 != null && cfg==h7) LOG.info("[DEV54H7-READY] model={} wheels=14 source-verified; H2 six-face fallback via H7 marker OFF",source);
     return new Dev54H2FleetWheelParts(source.getPath().replace("legacy_meshes/","").replace(".tcm",""),cfg.chunks(),parts);
  }
  Frame begin(String meshKey,float[] original,int stride) {
    if (!enabled(id) && !("locomotive_steam_cherepanov".equals(id) && h6Enabled(id))) { report(false); return null; }
    BakedWheelAnimationContext.TransformSpec[] phases=new BakedWheelAnimationContext.TransformSpec[parts.length];
    for(int i=0;i<parts.length;i++) {
      var phase=BakedWheelAnimationContext.spec(meshKey,chunks[i],original,stride);
      if(phase==null || !phase.rotates() || phase.axis()!=2
          || !close(phase.centerX(),parts[i].cx) || !close(phase.centerY(),parts[i].cy)
          || !close(phase.centerZ(),parts[i].cz)
          || !Float.isFinite(phase.cos()) || !Float.isFinite(phase.sin())) {
          report(false); return null; // All source original chunks this frame, no partial switching.
      }
      phases[i]=phase;
    }
    report(true);return new Frame(chunks,parts,phases,
      "locomotive_steam_vb_shay_2".equals(id) ? VB_SHAY_2_SEGMENTS : SEGMENTS);
  }
  private void report(boolean on) {
     if(lastReported==null || lastReported.booleanValue()!=on) {
        lastReported=on;
        LOG.info("[DEV54H2-WHEEL-PARTS] model={} active={} replacedFaces={} originalOtherGeometry=true",id,on,parts.length);
        if (on && "locomotive_steam_vb_shay_2".equals(id))
          LOG.info("[DEV54H5-SHAY2-WHEELS-ONLY] 8 source-matched faces, four axles, 48 segments, no added rods/cranks/pistons");
     }
  }
  private static boolean enabled(String id) {
    try {
      var path=FMLPaths.GAMEDIR.get().resolve(MARKER);
      if(!Files.isRegularFile(path))return false;
      String text=Files.readString(path,StandardCharsets.UTF_8).replace("\ufeff","");
      for(String item:text.split("\\R"))if(id.equalsIgnoreCase(item.trim()))return true;
    } catch(Exception ignored) {}
    return false;
  }
  private static boolean close(float a,float b) {return Float.isFinite(a)&&Math.abs(a-b)<=POSITION_TOLERANCE;}
  private static String sha256(byte[] d) {
    try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(d);
      StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02X",b&255));return s.toString();
    } catch(Exception e) {return "";}
  }
  static final class Frame {
     private final int[] chunks;
     private final WheelPart[] parts;
     private final BakedWheelAnimationContext.TransformSpec[] phases;
     private final int segments;
     Frame(int[] c,WheelPart[] p,BakedWheelAnimationContext.TransformSpec[] ph,int n){chunks=c;parts=p;phases=ph;segments=n;}
     boolean hasChunk(int chunk) {for(int c:chunks)if(chunk==c)return true;return false;}
     void draw(PoseStack.Pose pose,VertexConsumer out,int light,int overlay) {
        for(int i=0;i<parts.length;i++)parts[i].draw(pose,out,light,overlay,phases[i],segments);
     }
  }
    private static final class WheelPart {
        final float cx, cy, cz, radius, depth;
        final UVFace negFace, posFace;

        private WheelPart(float cx, float cy, float cz, float radius, float depth, UVFace negFace, UVFace posFace) {
            this.cx = cx; this.cy = cy; this.cz = cz; this.radius = radius; this.depth=depth;
            this.negFace = negFace; this.posFace = posFace;
        }

        static WheelPart extract(float[] v, int chunk, int stride, boolean h6Expanded, boolean cherepanov) {
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
            // H6 larger driver discs are specific SHA-gated chunks only. Other faces
            // keep their exact H5 size limits. Cherepanov is a genuine thick wheel.
            if (!Float.isFinite(dx) || dx < 0.20f || dx > (h6Expanded ? 1.05f : 0.55f)
                    || Math.abs(dx-dy) > 0.001f
                    || (cherepanov ? Math.abs(dz-0.063125f)>0.0005f : dz > 0.002f)) return null;
            UVFace neg = UVFace.from(v, base + 16*stride, stride, minX, minY, maxX, maxY);
            UVFace pos = UVFace.from(v, base + 20*stride, stride, minX, minY, maxX, maxY);
            if (neg == null || pos == null) return null;
            return new WheelPart((minX+maxX)*0.5f, (minY+maxY)*0.5f,
                    (minZ+maxZ)*0.5f, Math.min(dx,dy)*0.5f,
                    cherepanov ? dz : DEPTH,neg,pos);
        }

        void draw(PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                  BakedWheelAnimationContext.TransformSpec phase, int segments) {
            // Separate wheel parts: source-measured axle, authored face UVs,
            // own closed cylindrical mesh, accepted existing wheel rotation.
            float cc = phase.cos(), ss = phase.sin();
            float z0 = cz - depth * 0.5f, z1 = cz + depth * 0.5f;
            for (int index = 0; index < segments; index++) {
                double t0 = 2.0d * Math.PI * index / segments;
                double t1 = 2.0d * Math.PI * (index + 1) / segments;
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
