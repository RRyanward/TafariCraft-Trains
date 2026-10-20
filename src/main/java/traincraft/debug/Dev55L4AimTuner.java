package traincraft.debug;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** DEV55L4 review-only, per-model lamp-aim calibration. Never changes normal gameplay. */
public final class Dev55L4AimTuner {
    private Dev55L4AimTuner() { }
    private static final Logger LOG = LoggerFactory.getLogger("DEV55L4-AIM");
    private static final String PREFIX = "locomotive_steam_";
    private static final Set<String> FROZEN = Set.of(PREFIX + "262t", PREFIX + "vb_shay", PREFIX + "vb_shay_2");
    private static final Set<String> MODELS = Set.of(
            "4_4_0", "adler", "berkshire_1225", "berkshire_765", "br80", "br_britannia",
            "c11", "c41", "c41_080", "c41t", "cherepanov", "climax", "climax_2",
            "fowler", "glyn_042t", "gs4", "gwr_101_class", "gwr_42xx", "gwr_72xx",
            "j50", "lssp7", "milw_class_a", "pe", "rw_type_2", "rw_type_3",
            "s100_uk", "s100_us", "shay_3_truck", "skookum", "snow_plow",
            "southern1102", "star_class", "u57", "wwcp_062t");
    private static final ThreadLocal<Float> RENDER_DEGREES = ThreadLocal.withInitial(() -> 0F);
    private static final ThreadLocal<Float> MODEL_Y_SIGN = ThreadLocal.withInitial(() -> 1F);
    private static volatile Map<String,Float> values = Map.of();
    private static volatile long refreshAfterNs = 0L;
    private static final long REFRESH_NS = 350_000_000L;

    public static String normalize(String input) {
        if (input == null) return "";
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("traincraft:")) s = s.substring("traincraft:".length());
        if (s.equals("britannia")) s = "br_britannia";
        if (s.equals("wwcp_062")) s = "wwcp_062t";
        if (s.equals("snowplow")) s = "snow_plow";
        return s.startsWith(PREFIX) ? s : PREFIX + s;
    }
    public static boolean frozen(String name) { return FROZEN.contains(normalize(name)); }
    public static boolean supported(String name) {
        String id = normalize(name);
        return id.startsWith(PREFIX) && MODELS.contains(id.substring(PREFIX.length()));
    }
    private static Path path() { return FMLPaths.GAMEDIR.get().resolve("DEV55L4-LIGHT-AIM-TEST.csv"); }
    public static Path outputFile() { return path(); }
    private static void refresh() {
        long now = System.nanoTime();
        if (refreshAfterNs != 0L && now < refreshAfterNs) return;
        synchronized (Dev55L4AimTuner.class) {
            if (refreshAfterNs != 0L && now < refreshAfterNs) return;
            refreshAfterNs = now + REFRESH_NS;
            Map<String,Float> loaded = new HashMap<>();
            try {
                Path file = path();
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith("model,")) continue;
                        String[] cells = line.split(",", -1);
                        if (cells.length != 2) continue;
                        String model = normalize(cells[0]);
                        if (!supported(model) || frozen(model)) continue;
                        float degrees = Float.parseFloat(cells[1].trim());
                        if (Float.isFinite(degrees) && Math.abs(degrees) <= 20F) loaded.put(model,degrees);
                    }
                }
            } catch (Exception ex) {
                LOG.warn("[DEV55L4-AIM] Could not reload test settings: {}", ex.toString());
            }
            values = Map.copyOf(loaded);
        }
    }
    public static float get(String name) {
        refresh();
        return values.getOrDefault(normalize(name),0F);
    }
    public static synchronized boolean set(String name, float degrees) {
        String id = normalize(name);
        if (!supported(id) || frozen(id) || !Float.isFinite(degrees) || Math.abs(degrees) > 20F) return false;
        refresh();
        Map<String,Float> copy = new HashMap<>(values);
        if (Math.abs(degrees) < 0.001F) copy.remove(id); else copy.put(id,degrees);
        try {
            Path target = path();
            Files.createDirectories(target.getParent());
            StringBuilder data = new StringBuilder("model,degrees\r\n");
            copy.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    data.append(entry.getKey()).append(',').append(String.format(Locale.ROOT,"%.2f",entry.getValue())).append("\r\n"));
            Path pending = target.resolveSibling(target.getFileName() + ".pending");
            Files.writeString(pending, data.toString(), StandardCharsets.UTF_8);
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
            values = Map.copyOf(copy);
            refreshAfterNs = System.nanoTime() + REFRESH_NS;
            LOG.info("[DEV55L4-AIM] changed model={} degrees={} path={}",id,degrees,target);
            return true;
        } catch (Exception ex) {
            LOG.warn("[DEV55L4-AIM] Failed to write angle: {}",ex.toString());
            return false;
        }
    }
    /** Render thread: review-only marker comes from DEV55L3's hidden synced entity name. */
    public static void enter(Entity e, String model, float modelYUpInWorld) {
        float degrees=0F;
        if (e != null && e.getCustomName() != null
                && "DEV55L3_TEST_LAMPS".equals(e.getCustomName().getString())
                && supported(model) && !frozen(model)) {
            degrees=get(model);
        }
        RENDER_DEGREES.set(degrees);
        // Model-space Y is upside down for some legacy renderers (Berkshires, Adler).
        // The PoseStack Y-axis component supplies the correct physical sign.
        MODEL_Y_SIGN.set(modelYUpInWorld < -0.1F ? -1F : 1F);
        if (degrees != 0F && e != null && e.tickCount % 60 == 0)
            LOG.info("[DEV55L4-AIM] REVIEW model={} entity={} downwardDegrees={} downYPerBlock={}",
                    model,e.getId(),degrees,deltaWorldDownPerBlock());
    }
    /** Positive degrees = more downward aim. Mesh-Y and world rays use matching signed adjustment. */
    public static float deltaWorldDownPerBlock() {
        float degrees=RENDER_DEGREES.get();
        return degrees==0F ? 0F : (float)Math.tan(Math.toRadians(degrees));
    }
    public static float deltaModelYPerBlock() {
        return -deltaWorldDownPerBlock() * MODEL_Y_SIGN.get();
    }
}
