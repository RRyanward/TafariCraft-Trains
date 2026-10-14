package traincraft.client.renderer.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.entity.train.freight.FreightCart;
import traincraft.client.renderer.animation.RollingStockWheelAnimation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Locale;

/** DEV50d3U guarded replacement-car renderer. No entity, rail, cargo or coupling state writes. */
public final class Dev50D3UReplacementMeshes {
    private static final Logger LOG=LoggerFactory.getLogger("TC-DEV50D3U-MESH");
    private static final ResourceLocation LIVESTOCK_TEXTURE = new ResourceLocation("traincraft", "textures/trains/dev50d3u_livestock_atlas.png");
    private static final ResourceLocation MARSHAL_TEXTURE = new ResourceLocation("traincraft", "textures/trains/dev50d3u_marshal_atlas.png");
    private static Mesh livestock, marshal;
    private static boolean livestockLoaded, marshalLoaded;
    private Dev50D3UReplacementMeshes() {}

    private static final class Mesh {
        final int n;
        final float[] data; // xyz uv normal (8 floats per vertex)
        final byte[] wheel; // zero=static, 1..4=running wheel axle
        final float[] pivots; // xyz x 4
        Mesh(int n, float[] data, byte[] wheel, float[] pivots) {
            this.n=n;this.data=data;this.wheel=wheel;this.pivots=pivots;
        }
    }
    private static Mesh load(String id) {
        String resource="assets/traincraft/dev50d3u/"+id+"_mesh.tcu";
        try(InputStream raw=Dev50D3UReplacementMeshes.class.getClassLoader().getResourceAsStream(resource)) {
            if(raw==null) throw new IllegalStateException("Missing resource "+resource);
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(raw,1<<17))) {
                if(in.readInt()!=0x54435531) throw new IllegalStateException("Unexpected DEV50d3U mesh signature");
                int n=in.readInt();
                if(n<=0||n%3!=0||n>600000)throw new IllegalStateException("Unsafe vertex count "+n);
                float[] pivots=new float[12];for(int i=0;i<12;i++)pivots[i]=in.readFloat();
                float[] data=new float[n*8];byte[] wheel=new byte[n];
                for(int i=0;i<n;i++){
                    for(int k=0;k<8;k++){
                        float v=in.readFloat();if(!Float.isFinite(v))throw new IllegalStateException("Non-finite mesh vertex");
                        data[i*8+k]=v;
                    }
                    wheel[i]=in.readByte();if(wheel[i]<0||wheel[i]>4)throw new IllegalStateException("Unknown wheel part");
                }
                if(in.read()!=-1)throw new IllegalStateException("Extra mesh bytes "+id);
                LOG.info("[TC-DEV50D3U] loaded={} triangles={} wheelGroups=4",id,n/3);
                return new Mesh(n,data,wheel,pivots);
            }
        }catch(Exception e) {LOG.error("[TC-DEV50D3U] replacement {} not loadable; original transport cage renderer remains active",id,e);return null;}
    }
    public static boolean render(FreightCart entity, float partialTicks, PoseStack pose,
                                 MultiBufferSource buffers,int packedLight, boolean prisoner) {
        if(prisoner) {if(!marshalLoaded){marshal=load("marshal");marshalLoaded=true;}}
        else {if(!livestockLoaded){livestock=load("livestock");livestockLoaded=true;}}
        Mesh m=prisoner?marshal:livestock;
        if(m==null)return false;
        ResourceLocation texture=prisoner?MARSHAL_TEXTURE:LIVESTOCK_TEXTURE;
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        float phase=(float)RollingStockWheelAnimation.sampleNativePhase(entity,partialTicks);
        float cos=(float)Math.cos(phase),sin=(float)Math.sin(phase);
        Matrix4f xform=pose.last().pose();Matrix3f normalMatrix=pose.last().normal();
        // VertexConsumer uses QUADS for entityCutoutNoCull. Each independent input triangle
        // is emitted as a degenerate quad, never combining unrelated body/wheel polygons.
        for(int i=0;i<m.n;i+=3){
            for(int j=0;j<4;j++){
                int vertexIndex=i+Math.min(j,2);
                int b=vertexIndex*8;
                float x=m.data[b],y=m.data[b+1],z=m.data[b+2];
                float nx=m.data[b+5],ny=m.data[b+6],nz=m.data[b+7];
                int group=m.wheel[vertexIndex]&0xff;
                if(group!=0){
                    int o=(group-1)*3;float cx=m.pivots[o],cy=m.pivots[o+1];
                    float lx=x-cx,ly=y-cy;
                    x=cx+cos*lx-sin*ly;y=cy+sin*lx+cos*ly;
                    float xx=nx,yy=ny;nx=cos*xx-sin*yy;ny=sin*xx+cos*yy;
                }
                out.vertex(xform,x,y,z).color(255,255,255,255)
                    .uv(m.data[b+3],m.data[b+4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                    .normal(normalMatrix,nx,ny,nz).endVertex();
            }
        }
        return true;
    }
}
