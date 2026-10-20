package traincraft.client.renderer.legacy;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;

/**
 * DEV55L18: user-marked, opposite-end red rear-lamp REVIEW CANDIDATES.
 * Strictly limited to no-fuel /tclightreview preview entities while the
 * DEV55L18 flag is present. No extra WHITE beams and no normal locomotive changes.
 * Model-space positions are from the user's DEV55L15 exported marker CSV.
 */
public final class Dev55L18RearLampReview {
    private Dev55L18RearLampReview() {}
    private static final Map<String,double[][]> REAR = new HashMap<>();
    static {
        REAR.put("locomotive_steam_br80", new double[][]{new double[]{1.750312D,1.502213D,0.008233D}});
        REAR.put("locomotive_steam_c11", new double[][]{new double[]{2.250000D,-0.539953D,-0.501507D}, new double[]{2.250000D,-0.540068D,0.508529D}, new double[]{2.250000D,-0.825848D,0.008067D}});
        REAR.put("locomotive_steam_climax", new double[][]{new double[]{1.437812D,-0.803315D,0.002668D}});
        REAR.put("locomotive_steam_gwr_101_class", new double[][]{new double[]{2.000313D,-0.785740D,0.002282D}});
        REAR.put("locomotive_steam_gwr_42xx", new double[][]{new double[]{2.875313D,-0.392642D,0.569821D}, new double[]{2.875313D,-0.397342D,-0.001232D}, new double[]{2.875313D,-0.397321D,-0.502037D}});
        REAR.put("locomotive_steam_gwr_72xx", new double[][]{new double[]{3.312813D,-0.395356D,0.566175D}, new double[]{3.312813D,-0.399597D,0.002476D}, new double[]{3.312813D,-0.391987D,-0.498847D}});
        REAR.put("locomotive_steam_j50", new double[][]{new double[]{2.375000D,-0.576288D,0.001601D}});
        REAR.put("locomotive_steam_rw_type_3", new double[][]{new double[]{2.125313D,-0.613778D,0.007319D}});
        REAR.put("locomotive_steam_s100_uk", new double[][]{new double[]{2.878125D,-0.440050D,1.125280D}, new double[]{2.753125D,-1.188474D,0.811747D}, new double[]{2.878125D,-0.434872D,0.502405D}});
        REAR.put("locomotive_steam_s100_us", new double[][]{new double[]{2.750000D,-1.229112D,0.813719D}});
        REAR.put("locomotive_steam_vb_shay", new double[][]{new double[]{2.437813D,0.245960D,0.009436D}});
        REAR.put("locomotive_steam_wwcp_062t", new double[][]{new double[]{3.250313D,-0.219188D,0.477019D}, new double[]{3.250313D,-0.217483D,-0.465607D}});
    }
    public static boolean active(Entity entity) {
        return entity!=null && entity.isAlive()
            && entity.getCustomName()!=null
            && "DEV55L3_TEST_LAMPS".equals(entity.getCustomName().getString())
            && Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("DEV55L18-REAR-PREVIEW-ON.flag"));
    }
    public static double[][] positions(String id) {
        return REAR.getOrDefault(id, new double[0][]);
    }
    public static int count(String id) {return positions(id).length;}
}
