package traincraft.client.renderer.legacy;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traincraft.debug.Dev55L19Settings;
import java.nio.file.Files;
import java.util.*;

/** DEV58B10F3: exact user-approved Oct-23 viewer positions and vectors.
 * Unaltered angles, pitch, reach and model Y orientation. Switchable integrated
 * client/runtime TEST only; no model assets, worldgen, physics, or rods touched. */
public final class Dev58B10F3ViewerRuntime {
    private Dev58B10F3ViewerRuntime() {}
    private static final Logger LOG=LoggerFactory.getLogger("DEV58B10F3-VIEWER");
    private record Lamp(String type,String role,double x,double y,double z,String axis,
        double yaw,double pitch,double reach,double depth,double side,double height,int downSign){}
    private static final Map<String,List<Lamp>> MODELS=new HashMap<>();
    private static final Set<String> WARNED=new HashSet<>();
    private static void add(String model,Lamp lamp){MODELS.computeIfAbsent(model,k->new ArrayList<>()).add(lamp);}
    static {
        add("locomotive_diesel_gp7", new Lamp("headlight","right",-1.6875D,0.757434D,-0.316499D,"nx",10.5D,5.5D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_gp7", new Lamp("headlight","left",-1.6875D,0.737827D,0.308734D,"nx",-10.5D,5.5D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_gp7", new Lamp("headlight","center",-1.5D,1.686786D,-0.009028D,"nx",0.0D,9.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_gp7", new Lamp("tail","tail_left",1.6875D,0.749361D,0.315564D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_gp7", new Lamp("tail","tail_right",1.6875D,0.753798D,-0.30653D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_gp7", new Lamp("tail","tail_center",1.5D,1.697208D,0.002255D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_kof", new Lamp("headlight","left",-1.25D,0.817934D,0.496134D,"nx",-10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_kof", new Lamp("headlight","right",-1.25D,0.819607D,-0.501459D,"nx",10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_kof", new Lamp("headlight","center",-1.0D,1.509049D,-0.001376D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_kof", new Lamp("tail","tail_center",1.5D,1.377877D,0.003356D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_kof", new Lamp("tail","tail_left",1.5D,0.816859D,0.503682D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_kof", new Lamp("tail","tail_right",1.5D,0.818686D,-0.505985D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_shunter", new Lamp("headlight","right",-1.75D,0.788681D,-0.318767D,"nx",10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_shunter", new Lamp("headlight","left",-1.75D,0.774707D,0.310275D,"nx",-10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_shunter", new Lamp("headlight","center",-1.75D,1.838434D,-0.004246D,"nx",0.0D,9.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_diesel_shunter", new Lamp("tail","tail_left",1.8125D,0.852895D,0.507615D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_shunter", new Lamp("tail","tail_center",1.8125D,0.852383D,0.061931D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_shunter", new Lamp("tail","tail_center",1.8125D,1.78881D,0.06586D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_diesel_shunter", new Lamp("tail","tail_right",1.8125D,0.846146D,-0.490524D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_electric_br_e69", new Lamp("tail","tail_right",1.4375D,0.543402D,-0.343532D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_electric_br_e69", new Lamp("tail","tail_left",1.4375D,0.536385D,0.355936D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_electric_br_e69", new Lamp("headlight","right",-1.4375D,0.523809D,-0.34881D,"nx",0.0D,3.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_electric_br_e69", new Lamp("headlight","left",-1.4375D,0.522392D,0.344993D,"nx",0.0D,3.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_electric_minetrain", new Lamp("headlight","center",-0.625D,1.197463D,-0.004192D,"nx",0.0D,7.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_electric_tram", new Lamp("headlight","center",1.8125000000000002D,0.6944591050410903D,0.0024991639484166703D,"px",0.0D,4.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_electric_tram", new Lamp("tail","tail_center",-1.8124999999999998D,0.6837781226583668D,-0.00934984978824449D,"nx",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_electric_tram_ny", new Lamp("headlight","center",-2.0625D,1.819512445676985D,-0.008565199787796691D,"nx",0.0D,9.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_262t", new Lamp("headlight","center",-3.375313D,-0.75279D,0.032171D,"nx",0.0D,7.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_4_4_0", new Lamp("headlight","center",-0.006162D,1.63415D,-1.6875D,"nz",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_adler", new Lamp("headlight","right",-0.344213D,0.789464D,1.0625D,"pz",0.0D,5.0D,10.8D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_adler", new Lamp("headlight","left",0.352813D,0.782258D,1.0625D,"pz",0.0D,5.0D,10.8D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_1225", new Lamp("headlight","center",3.328438D,-1.588487D,-0.059682D,"px",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_1225", new Lamp("headlight","right",3.181563D,-1.995836D,0.348053D,"px",10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_1225", new Lamp("headlight","left",3.181563D,-1.998932D,-0.463596D,"px",-10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_765", new Lamp("headlight","center",3.328438D,-1.718112D,-0.060288D,"px",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_765", new Lamp("headlight","center",3.328438D,-1.589294D,-0.058337D,"px",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_765", new Lamp("headlight","right",3.181563D,-1.998565D,0.313493D,"px",10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_berkshire_765", new Lamp("headlight","left",3.181563D,-2.000301D,-0.437512D,"px",-10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_br80", new Lamp("headlight","center",-1.375312D,1.6935D,-0.010416D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_br80", new Lamp("tail","tail_center",1.750312D,1.502213D,0.008233D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_br_britannia", new Lamp("headlight","left",-2.1375D,-0.191765D,0.438153D,"nx",0.0D,4.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_br_britannia", new Lamp("headlight","right",-2.1375D,-0.198448D,-0.43782D,"nx",0.0D,4.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c11", new Lamp("headlight","center",-1.750312D,-1.025353D,-0.005798D,"nx",0.0D,10.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c11", new Lamp("headlight","left",-1.719062D,-0.994413D,0.402991D,"nx",-10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c11", new Lamp("headlight","right",-1.719062D,-0.994782D,-0.412562D,"nx",10.5D,11.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c11", new Lamp("tail","tail_right",2.25D,-0.539953D,-0.501507D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_c11", new Lamp("tail","tail_left",2.25D,-0.540068D,0.508529D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_c11", new Lamp("tail","tail_center",2.25D,-0.825848D,0.008067D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_c41", new Lamp("headlight","center",-0.687813D,-1.802353D,-0.009147D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c41_080", new Lamp("headlight","center",-0.687813D,-1.799863D,-0.009849D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_c41t", new Lamp("headlight","center",-0.625313D,-1.789045D,-0.00753D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_cherepanov", new Lamp("headlight","center",1.062812D,0.447098D,0.005887D,"px",0.0D,3.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_climax", new Lamp("headlight","center",-2.812813D,-0.863312D,-0.01203D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_climax", new Lamp("tail","tail_center",1.437812D,-0.803315D,0.002668D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_climax_2", new Lamp("headlight","center",-1.809577D,2.068704D,0.198205D,"nx",-55.0D,8.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_fowler", new Lamp("headlight","center",-0.65625D,-0.748443D,0.436333D,"nx",0.0D,5.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_glyn_042t", new Lamp("headlight","center",-2.812813D,-0.86543D,0.000464D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gs4", new Lamp("headlight","right",-6.628438D,-1.404947D,-0.343689D,"nx",10.5D,13.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gs4", new Lamp("headlight","left",-6.628438D,-1.405455D,0.340973D,"nx",-10.5D,13.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gs4", new Lamp("headlight","center",-6.847188D,-0.745653D,-0.003516D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gs4", new Lamp("headlight","center",-6.847188D,-0.982341D,-0.001035D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_101_class", new Lamp("headlight","center",-1.750312D,-0.907554D,-0.002456D,"nx",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_101_class", new Lamp("tail","tail_center",2.000313D,-0.78574D,0.002282D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_42xx", new Lamp("headlight","center",-2.844063D,-0.212417D,-0.00382D,"nx",0.0D,5.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_42xx", new Lamp("headlight","left",-2.844063D,-0.214745D,0.565957D,"nx",-10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_42xx", new Lamp("headlight","right",-2.844063D,-0.216506D,-0.504685D,"nx",10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_42xx", new Lamp("tail","tail_left",2.875313D,-0.392642D,0.569821D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_42xx", new Lamp("tail","tail_center",2.875313D,-0.397342D,-0.001232D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_42xx", new Lamp("tail","tail_right",2.875313D,-0.397321D,-0.502037D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_72xx", new Lamp("headlight","center",-3.156563D,-0.211671D,-0.006177D,"nx",0.0D,5.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_72xx", new Lamp("headlight","left",-3.156563D,-0.211258D,0.559345D,"nx",-10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_72xx", new Lamp("headlight","right",-3.156563D,-0.228569D,-0.512319D,"nx",10.5D,6.0D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_gwr_72xx", new Lamp("tail","tail_left",3.312813D,-0.395356D,0.566175D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_72xx", new Lamp("tail","tail_center",3.312813D,-0.399597D,0.002476D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_gwr_72xx", new Lamp("tail","tail_right",3.312813D,-0.391987D,-0.498847D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_j50", new Lamp("headlight","left",-2.0625D,-0.18376D,0.561418D,"nx",0.0D,5.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_j50", new Lamp("tail","tail_center",2.375D,-0.576288D,0.001601D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_lssp7", new Lamp("headlight","center",-0.562813D,-1.093166D,0.810054D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_milw_class_a", new Lamp("headlight","center",-5.646875D,-1.124936D,0.000971D,"nx",0.0D,9.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_pe", new Lamp("headlight","center",-1.9375D,-0.719886D,-0.00985D,"nx",0.0D,10.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_rw_type_2", new Lamp("headlight","center",-1.375312D,-0.998928D,-0.003624D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_rw_type_3", new Lamp("headlight","center",-1.875312D,-0.126954D,0.000573D,"nx",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_rw_type_3", new Lamp("tail","tail_center",2.125313D,-0.613778D,0.007319D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_s100_uk", new Lamp("headlight","right",-0.95D,-0.374098D,0.374609D,"nx",10.5D,4.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_s100_uk", new Lamp("headlight","left",-0.95D,-0.37451D,1.248909D,"nx",-10.5D,4.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_s100_uk", new Lamp("tail","tail_left",2.878125D,-0.44005D,1.12528D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_s100_uk", new Lamp("tail","tail_center",2.753125D,-1.188474D,0.811747D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_s100_uk", new Lamp("tail","tail_right",2.878125D,-0.434872D,0.502405D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_s100_uk", new Lamp("headlight","center",-0.903125D,-1.186379D,0.81163D,"nx",0.0D,8.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_s100_us", new Lamp("headlight","center",-0.9375D,-1.224456D,0.804171D,"nx",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_s100_us", new Lamp("tail","tail_center",2.75D,-1.229112D,0.813719D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_shay_3_truck", new Lamp("headlight","center",-2.53125D,-0.707357D,-0.185779D,"nx",0.0D,7.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_shay_3_truck", new Lamp("headlight","right",-2.469063D,-0.502855D,-0.410638D,"nx",10.5D,7.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_shay_3_truck", new Lamp("headlight","left",-2.469063D,-0.501823D,0.028349D,"nx",-10.5D,7.5D,10.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_skookum", new Lamp("headlight","center",-1.71875D,-1.094421D,0.055946D,"nx",0.0D,9.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_snow_plow", new Lamp("headlight","center",-2.9375D,-1.566356D,-0.004799D,"nx",0.0D,11.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_southern1102", new Lamp("headlight","center",-0.625313D,-1.779615D,-0.004847D,"nx",0.0D,9.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_star_class", new Lamp("headlight","center",-1.125312D,-0.530527D,-0.001355D,"nx",0.0D,6.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_u57", new Lamp("headlight","center",-0.007045D,1.942146D,4.375312D,"pz",0.0D,10.5D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_u57", new Lamp("headlight","right",-0.373836D,1.00558D,4.750312D,"pz",10.5D,6.5D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_u57", new Lamp("headlight","left",0.367022D,0.969763D,4.750312D,"pz",-10.5D,6.5D,10.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_vb_shay", new Lamp("headlight","center",-2.625313D,-0.858902D,-0.013742D,"nx",0.0D,8.5D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_vb_shay", new Lamp("tail","tail_center",2.437813D,0.24596D,0.009436D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_vb_shay_2", new Lamp("headlight","center",-1.466338D,2.196581D,0.197824D,"nx",-55.0D,10.0D,12.0D,0.0D,0.0D,0.0D,-1));
        add("locomotive_steam_vb_shay_2", new Lamp("tail","tail_center",1.6878125667572021D,2.1335761650360654D,0.0040959710661649254D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_wwcp_062t", new Lamp("headlight","right",-2.312813D,-0.088706D,-0.475094D,"nx",0.0D,7.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_wwcp_062t", new Lamp("headlight","left",-2.312813D,-0.090777D,0.462587D,"nx",0.0D,7.0D,12.0D,0.0D,0.0D,0.0D,1));
        add("locomotive_steam_wwcp_062t", new Lamp("tail","tail_left",3.250312566757202D,-0.20486599376539172D,0.47141137642627D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
        add("locomotive_steam_wwcp_062t", new Lamp("tail","tail_right",3.250312566757202D,-0.22262177159341554D,-0.4683603249328697D,"px",0.0D,0.0D,1.5D,0.0D,0.0D,0.0D,0));
    }
    private static boolean switchOn(){return Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV58B10F3-VIEWER-TEST-ON.flag"));}
    public static boolean active(Entity e,String rawId){return e!=null && e.isAlive()
        && switchOn() && Dev55L19Settings.fleetMode(e)
        && MODELS.containsKey(Dev55L19Settings.model(rawId));}
    // Exports and runtime deliberately share this exact algebra:
    // dx=baseX*cos(yaw)-baseZ*sin(yaw), dz=baseX*sin(yaw)+baseZ*cos(yaw)
    // start=(x+dx*depth-dz*side,y+height,z+dz*depth+dx*side)
    // end=start+(dx*cos(pitch),downSign*sin(pitch),dz*cos(pitch))*reach.
    private static float[] axis(String a){return switch(a){
        case "px" -> new float[]{1F,0F};case "nx" -> new float[]{-1F,0F};
        case "pz" -> new float[]{0F,1F};case "nz" -> new float[]{0F,-1F};
        default -> throw new IllegalArgumentException("invalid axis: "+a);};}
    private static Vector3f point(Lamp l){return new Vector3f((float)l.x,(float)l.y,(float)l.z);}
    private static Vec3 world(Entity e,Matrix4f world,Vector3f p){
        Vector3f v=world.transformPosition(new Vector3f(p));
        return e.position().add(v.x,v.y,v.z);
    }
    private static void vertex(VertexConsumer v,Matrix4f pose,Vector3f p,int red,int green,int blue,int alpha){
        v.vertex(pose,p.x,p.y,p.z).color(red,green,blue,alpha).endVertex();
    }
    private static void quad(VertexConsumer v,Matrix4f pose,Vector3f a,Vector3f b,Vector3f c,Vector3f d,
            int r,int g,int bl,int alphaA,int alphaB){
        vertex(v,pose,a,r,g,bl,alphaA);vertex(v,pose,b,r,g,bl,alphaA);
        vertex(v,pose,c,r,g,bl,alphaB);vertex(v,pose,d,r,g,bl,alphaB);
        vertex(v,pose,d,r,g,bl,alphaB);vertex(v,pose,c,r,g,bl,alphaB);
        vertex(v,pose,b,r,g,bl,alphaA);vertex(v,pose,a,r,g,bl,alphaA);
    }
    private static void lampFace(VertexConsumer v,Matrix4f pose,Vector3f origin,float dirX,float dirZ,
            boolean red,float brightness){
        float h=.067F;Vector3f p=new Vector3f(origin).add(dirX*.014F,0F,dirZ*.014F);
        Vector3f right=new Vector3f(-dirZ*h,0F,dirX*h);
        Vector3f up=new Vector3f(0,h,0);
        Vector3f a=new Vector3f(p).sub(right).sub(up),b=new Vector3f(p).add(right).sub(up);
        Vector3f c=new Vector3f(p).add(right).add(up),d=new Vector3f(p).sub(right).add(up);
        quad(v,pose,a,b,c,d,red?255:255,red?58:232,red?50:161,(int)(240*brightness),(int)(240*brightness));
    }
    private static double fraction(Entity e,Vec3 start,Vec3 end,double dx,double dz){
        double f=1D;double length=start.distanceTo(end);
        if(length<1e-5D)return 0D;
        for(double offset:new double[]{0D,-.26D,.26D}){
            Vec3 a=start.add(-dz*offset,0,dx*offset),b=end.add(-dz*offset,0,dx*offset);
            BlockHitResult block=e.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
            if(block.getType()==HitResult.Type.BLOCK)
                f=Math.min(f,a.distanceTo(block.getLocation())/Math.max(.001,a.distanceTo(b)));
            AABB scan=new AABB(a,b).inflate(.6D);
            for(Entity other:e.level().getEntities(e,scan,o->o!=e&&o.isAlive()&&!o.isSpectator()
                    && o instanceof LivingEntity&&!o.isPassenger())){
                var clip=other.getBoundingBox().inflate(.08D).clip(a,b);
                if(clip.isPresent())f=Math.min(f,a.distanceTo(clip.get())/Math.max(.001,a.distanceTo(b)));
            }
        }
        return Math.max(.015,Math.min(1D,f));
    }
    public static boolean render(Entity entity,String rawId,VertexConsumer v,Matrix4f pose,
            Matrix4f modelWorld,float brightness){
        String id=Dev55L19Settings.model(rawId);
        if(!active(entity,id))return false;
        if(modelWorld==null){warn(id,"missing model-to-world matrix");return false;}
        try{
            List<Dev55L23RayExport.Ray> publish=new ArrayList<>();
            Set<String> used=new HashSet<>();int headCount=0,tailCount=0;
            for(Lamp lamp:MODELS.get(id)){
                float[] basis=axis(lamp.axis);
                float yaw=(float)Math.toRadians(lamp.yaw);
                float dx=(float)(basis[0]*Math.cos(yaw)-basis[1]*Math.sin(yaw));
                float dz=(float)(basis[0]*Math.sin(yaw)+basis[1]*Math.cos(yaw));
                float ox=(float)(lamp.x+dx*lamp.depth-dz*lamp.side);
                float oy=(float)(lamp.y+lamp.height);
                float oz=(float)(lamp.z+dz*lamp.depth+dx*lamp.side);
                Vector3f from=new Vector3f(ox,oy,oz);
                boolean red=lamp.type.equals("tail");
                float pitch=(float)Math.toRadians(lamp.pitch);
                float reach=(float)lamp.reach;
                Vector3f end=new Vector3f(ox+dx*reach*(float)Math.cos(pitch),
                    oy+lamp.downSign*reach*(float)Math.sin(pitch),
                    oz+dz*reach*(float)Math.cos(pitch));
                // DEV58B10F4R1: tail lamps remain lens-anchored RED SQUARES.
                // Do not raycast or render a rear beam, even if a viewer tailReach
                // was recorded for its former preview. Preserve exact lamp XYZ.
                if(red){
                    lampFace(v,pose,from,dx,dz,true,brightness);
                    tailCount++;
                    continue;
                }
                Vec3 wStart=world(entity,modelWorld,from),wEnd=world(entity,modelWorld,end);
                Vector3f dirWorld=modelWorld.transformDirection(new Vector3f(dx,0,dz));
                double mag=Math.hypot(dirWorld.x,dirWorld.z);
                double f=fraction(entity,wStart,wEnd,mag>.001?dirWorld.x/mag:0,mag>.001?dirWorld.z/mag:0);
                Vector3f clipped=new Vector3f(from).lerp(end,(float)f);
                lampFace(v,pose,from,dx,dz,false,brightness);
                {
                    headCount++;
                    Dev55L13SoftBeam.ribbon(v,pose,from.x,from.y,from.z,clipped.x,clipped.y,clipped.z,
                        -dz,dx,lamp.role.equals("center")?1.05F:.90F,brightness);
                    // L23's guarded singleplayer endpoint protocol has at most 3 roles.
                    // Draw ALL real headlamps, including stacked centers, but publish
                    // each role only once to prevent malformed multi-center ray files.
                    if(used.add(lamp.role) && Set.of("center","left","right").contains(lamp.role))
                        publish.add(new Dev55L23RayExport.Ray(lamp.role,wStart,wStart.lerp(wEnd,f)));
                }
            }
            Dev55L23RayExport.publish(entity,id,publish);
            if(entity.tickCount%100==0)
                LOG.info("[DEV58B10F3-VIEWER-RUNTIME] model={} entity={} headlamps={} tails={} rays={} settings=EXACT_VIEWER pitch=EXPORTED reach=EXPORTED center12Override=OFF",
                    id,entity.getId(),headCount,tailCount,publish.size());
            return true;
        }catch(Exception e){warn(id,e.toString());return false;}
    }
    private static synchronized void warn(String id,String why){if(WARNED.add(id+why))
        LOG.error("[DEV58B10F3-VIEWER-HOLD] model={} reason={} (no unsafe override)",id,why);}
    public static int headlightCount(String id){return (int)MODELS.getOrDefault(Dev55L19Settings.model(id),List.of()).stream().filter(l->l.type.equals("headlight")).count();}
    public static int tailCount(String id){return (int)MODELS.getOrDefault(Dev55L19Settings.model(id),List.of()).stream().filter(l->l.type.equals("tail")).count();}
}
