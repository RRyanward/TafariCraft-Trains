package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;
import java.nio.file.Files;
import java.util.Set;

/**
 * DEV55L13 experimental renderer ONLY for the marked 4-4-0 fleet-review preview.
 * Never changes normal trains, 262T, Berkshire or VB Shay beam geometry.
 * All ray tracing, the accepted 12-block cap, lens positions and optics live elsewhere.
 */
public final class Dev55L13SoftBeam {
    private Dev55L13SoftBeam() {}
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    // Reviewed steam marker-backed models; existing accepted profiles are excluded.
    private static final Set<String> FLEET = Set.of(
        "locomotive_steam_adler",
        "locomotive_steam_br80",
        "locomotive_steam_br_britannia",
        "locomotive_steam_c11",
        "locomotive_steam_c41_080",
        "locomotive_steam_c41t",
        "locomotive_steam_cherepanov",
        "locomotive_steam_climax",
        "locomotive_steam_climax_2",
        "locomotive_steam_fowler",
        "locomotive_steam_glyn_042t",
        "locomotive_steam_gs4",
        "locomotive_steam_gwr_101_class",
        "locomotive_steam_gwr_42xx",
        "locomotive_steam_gwr_72xx",
        "locomotive_steam_j50",
        "locomotive_steam_lssp7",
        "locomotive_steam_pe",
        "locomotive_steam_rw_type_2",
        "locomotive_steam_rw_type_3",
        "locomotive_steam_s100_uk",
        "locomotive_steam_s100_us",
        "locomotive_steam_shay_3_truck",
        "locomotive_steam_skookum",
        "locomotive_steam_snow_plow",
        "locomotive_steam_southern1102",
        "locomotive_steam_star_class",
        "locomotive_steam_u57",
        "locomotive_steam_wwcp_062t"
    );
    public static void enter(Entity entity,String model){
        boolean test=entity!=null && entity.getCustomName()!=null
            && "DEV55L3_TEST_LAMPS".equals(entity.getCustomName().getString());
        boolean accepted44="locomotive_steam_4_4_0".equals(model)
            && Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L13-SMOOTH-ON.flag"))
            && Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L12-HOTSPOT-ON.flag"));
        boolean fleet=FLEET.contains(model)
            && Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L14-FLEET-ON.flag"));
        ACTIVE.set(test && (accepted44 || fleet));
    }
    public static boolean active(){return ACTIVE.get();}
    private static void emit(VertexConsumer v,Matrix4f m,float x,float y,float z,int a){
        v.vertex(m,x,y,z).color(255,222,150,Math.max(0,Math.min(255,a))).endVertex();
    }
    // Draw two halves with a bright centerline and transparent feathered edges.
    // Vertex opacity varies smoothly ALONG the entire beam: no 65% alpha reset.
    private static void face(VertexConsumer v,Matrix4f m,
            float x0,float y0,float z0,int a0,
            float x1,float y1,float z1,int a1,
            float ex0,float ey0,float ez0,float ex1,float ey1,float ez1){
        emit(v,m,x0,y0,z0,a0);
        emit(v,m,ex0,ey0,ez0,0);
        emit(v,m,ex1,ey1,ez1,0);
        emit(v,m,x1,y1,z1,a1);
        emit(v,m,x1,y1,z1,a1);
        emit(v,m,ex1,ey1,ez1,0);
        emit(v,m,ex0,ey0,ez0,0);
        emit(v,m,x0,y0,z0,a0);
    }
    public static void ribbon(VertexConsumer v,Matrix4f m,
            float x0,float y0,float z0,float x1,float y1,float z1,
            float nX,float nZ,float halfWidth,float brightness){
        final float[] ts={0F,0.10F,0.25F,0.42F,0.60F,0.78F,0.90F,1F};
        final float[] opacity={0.55F,0.75F,0.90F,0.95F,0.91F,0.80F,0.52F,0.10F};
        for(int i=0;i<ts.length-1;i++){
            float t0=ts[i],t1=ts[i+1];
            float aX=x0+(x1-x0)*t0,aY=y0+(y1-y0)*t0,aZ=z0+(z1-z0)*t0;
            float bX=x0+(x1-x0)*t1,bY=y0+(y1-y0)*t1,bZ=z0+(z1-z0)*t1;
            float w0=0.065F+(halfWidth-0.065F)*(float)Math.sqrt(t0);
            float w1=0.065F+(halfWidth-0.065F)*(float)Math.sqrt(t1);
            int alpha0=(int)(44F*brightness*opacity[i]);
            int alpha1=(int)(44F*brightness*opacity[i+1]);
            face(v,m,aX,aY,aZ,alpha0,bX,bY,bZ,alpha1,
                aX+nX*w0,aY,aZ+nZ*w0,bX+nX*w1,bY,bZ+nZ*w1);
            face(v,m,aX,aY,aZ,alpha0,bX,bY,bZ,alpha1,
                aX-nX*w0,aY,aZ-nZ*w0,bX-nX*w1,bY,bZ-nZ*w1);
        }
    }
}
