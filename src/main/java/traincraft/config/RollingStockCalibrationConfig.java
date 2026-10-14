package traincraft.config;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * TafariCraft - Trains persistent rolling-stock alignment database.
 *
 * Supports:
 *   - model visual Y
 *   - seat 1
 *   - seat 2
 *   - seat 3
 *
 * Old seat_enabled/seat_long/seat_y/seat_side keys remain Seat 1 so existing
 * powered-locomotive calibration files stay compatible.
 */
public final class RollingStockCalibrationConfig {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("TafariCraftCalibrationConfig");

    private static final String FILE_NAME =
            "tafaricraft-trains-calibration.toml";

    private static final Map<String, Entry> ENTRIES = new TreeMap<>();
    private static boolean loaded = false;

    private RollingStockCalibrationConfig() {}

    private static final class Entry {
        boolean locked;
        boolean modelEnabled;
        double modelY;

        // Seat 1 keeps the original field names for backward compatibility.
        boolean seatEnabled;
        double seatLong;
        double seatY;
        double seatSide;

        boolean seat2Enabled;
        double seat2Long;
        double seat2Y;
        double seat2Side;

        boolean seat3Enabled;
        double seat3Long;
        double seat3Y;
        double seat3Side;
    }

    public static synchronized Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static synchronized String pathString() {
        return path().toAbsolutePath().toString();
    }

    public static synchronized void reload() {
        loaded = false;
        ENTRIES.clear();
        ensureLoaded();
    }

    public static synchronized boolean hasEntry(String id) {
        ensureLoaded();
        return ENTRIES.containsKey(id);
    }

    public static synchronized boolean isLocked(String id) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        return e != null && e.locked;
    }

    public static synchronized boolean hasModel(String id) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        return e != null && e.modelEnabled;
    }

    public static synchronized double modelY(String id, double fallback) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        return e != null && e.modelEnabled ? e.modelY : fallback;
    }

    public static synchronized double setModelY(
            String id, double value, boolean locked) {
        ensureLoaded();
        Entry e = ENTRIES.computeIfAbsent(id, ignored -> new Entry());
        double clamped = clamp(value, -3.0D, 3.0D);
        e.modelEnabled = true;
        e.modelY = clamped;
        e.locked = locked;
        writeFile();

        LOG.info(String.format(Locale.ROOT,
                "[TC-CAL-CONFIG-MODEL] id=%s modelY=%.4f locked=%s path=%s",
                id, clamped, Boolean.toString(locked), pathString()));
        return clamped;
    }

    public static synchronized double adjustModelY(String id, double delta) {
        ensureLoaded();
        Entry e = ENTRIES.computeIfAbsent(id, ignored -> new Entry());
        double before = e.modelEnabled ? e.modelY : 0.0D;
        return setModelY(id, before + delta, false);
    }

    // Backward-compatible Seat 1 API used by the accepted powered-locomotive code.
    public static synchronized boolean hasSeat(String id) {
        return hasSeat(id, 1);
    }

    public static synchronized double seatLong(String id, double fallback) {
        return seatLong(id, 1, fallback);
    }

    public static synchronized double seatY(String id, double fallback) {
        return seatY(id, 1, fallback);
    }

    public static synchronized double seatSide(String id, double fallback) {
        return seatSide(id, 1, fallback);
    }

    // Universal Seat 1/2/3 API.
    public static synchronized boolean hasSeat(String id, int slot) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        if (e == null) return false;
        return switch (normalizeSlot(slot)) {
            case 1 -> e.seatEnabled;
            case 2 -> e.seat2Enabled;
            case 3 -> e.seat3Enabled;
            default -> false;
        };
    }

    public static synchronized double seatLong(
            String id, int slot, double fallback) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        if (e == null) return fallback;
        return switch (normalizeSlot(slot)) {
            case 1 -> e.seatEnabled ? e.seatLong : fallback;
            case 2 -> e.seat2Enabled ? e.seat2Long : fallback;
            case 3 -> e.seat3Enabled ? e.seat3Long : fallback;
            default -> fallback;
        };
    }

    public static synchronized double seatY(
            String id, int slot, double fallback) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        if (e == null) return fallback;
        return switch (normalizeSlot(slot)) {
            case 1 -> e.seatEnabled ? e.seatY : fallback;
            case 2 -> e.seat2Enabled ? e.seat2Y : fallback;
            case 3 -> e.seat3Enabled ? e.seat3Y : fallback;
            default -> fallback;
        };
    }

    public static synchronized double seatSide(
            String id, int slot, double fallback) {
        ensureLoaded();
        Entry e = ENTRIES.get(id);
        if (e == null) return fallback;
        return switch (normalizeSlot(slot)) {
            case 1 -> e.seatEnabled ? e.seatSide : fallback;
            case 2 -> e.seat2Enabled ? e.seat2Side : fallback;
            case 3 -> e.seat3Enabled ? e.seat3Side : fallback;
            default -> fallback;
        };
    }

    public static synchronized void setSeat(
            String id,
            int slot,
            boolean enabled,
            double longitudinal,
            double y,
            double side,
            boolean locked) {
        ensureLoaded();
        Entry e = ENTRIES.computeIfAbsent(id, ignored -> new Entry());

        int s = normalizeSlot(slot);
        longitudinal = clamp(longitudinal, -12.0D, 12.0D);
        y = clamp(y, -5.0D, 5.0D);
        side = clamp(side, -6.0D, 6.0D);

        switch (s) {
            case 1 -> {
                e.seatEnabled = enabled;
                e.seatLong = longitudinal;
                e.seatY = y;
                e.seatSide = side;
            }
            case 2 -> {
                e.seat2Enabled = enabled;
                e.seat2Long = longitudinal;
                e.seat2Y = y;
                e.seat2Side = side;
            }
            case 3 -> {
                e.seat3Enabled = enabled;
                e.seat3Long = longitudinal;
                e.seat3Y = y;
                e.seat3Side = side;
            }
            default -> throw new IllegalArgumentException("seat slot");
        }

        e.locked = locked;
        writeFile();

        LOG.info(String.format(Locale.ROOT,
                "[TC-CAL-CONFIG-SEAT] id=%s slot=%d enabled=%s long=%.4f y=%.4f side=%.4f locked=%s",
                id, s, Boolean.toString(enabled),
                longitudinal, y, side, Boolean.toString(locked)));
    }

    public static synchronized void adjustSeat(
            String id,
            int slot,
            double deltaLong,
            double deltaY,
            double deltaSide) {
        ensureLoaded();
        int s = normalizeSlot(slot);

        double oldLong = seatLong(id, s, 0.0D);
        double oldY = seatY(id, s, 0.0D);
        double oldSide = seatSide(id, s, 0.0D);

        setSeat(id, s, true,
                oldLong + deltaLong,
                oldY + deltaY,
                oldSide + deltaSide,
                false);
    }

    public static synchronized void clearSeat(String id, int slot) {
        setSeat(id, normalizeSlot(slot), false,
                0.0D, 0.0D, 0.0D, false);
    }

    /**
     * Original API retained so LegacyPoweredLocomotive does not need to change.
     * This writes Seat 1 and leaves Seat 2/3 untouched.
     */
    public static synchronized void save(
            String id,
            double modelY,
            boolean seatEnabled,
            double seatLong,
            double seatY,
            double seatSide,
            boolean locked) {

        ensureLoaded();

        Entry e = ENTRIES.computeIfAbsent(id, ignored -> new Entry());
        e.locked = locked;
        e.modelEnabled = true;
        e.modelY = clamp(modelY, -3.0D, 3.0D);
        e.seatEnabled = seatEnabled;
        e.seatLong = clamp(seatLong, -12.0D, 12.0D);
        e.seatY = clamp(seatY, -5.0D, 5.0D);
        e.seatSide = clamp(seatSide, -6.0D, 6.0D);

        writeFile();

        LOG.info(String.format(Locale.ROOT,
                "[TC-CAL-CONFIG-SAVE] id=%s locked=%s modelY=%.4f seat1=%s long=%.4f y=%.4f side=%.4f path=%s",
                id, Boolean.toString(locked), modelY,
                Boolean.toString(seatEnabled),
                seatLong, seatY, seatSide, pathString()));
    }

    private static int normalizeSlot(int slot) {
        if (slot < 1 || slot > 3) {
            throw new IllegalArgumentException(
                    "Seat slot must be 1, 2, or 3; got " + slot);
        }
        return slot;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        ENTRIES.clear();

        Path path = path();
        if (!Files.isRegularFile(path)) {
            // TC_DEV38_R1D_RELEASE_FACTORY_DEFAULTS
            // Fresh installs receive the exact runtime-accepted BODY + SEATS
            // baseline. Existing config files are never replaced here.
            if (installBundledReleaseDefaults(path)) {
                loaded = false;
                ENTRIES.clear();
                ensureLoaded();
                LOG.info("[TC-CAL-CONFIG] installed release factory defaults at {}",
                        path.toAbsolutePath());
                return;
            }

            // Safety fallback only if the packaged resource is unexpectedly
            // unavailable.
            seedApprovedDefaults();
            writeFile();
            LOG.info("[TC-CAL-CONFIG] created {}", path.toAbsolutePath());
            return;
        }

        Entry current = null;
        try {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[calibration.") && line.endsWith("]")) {
                    String id = line.substring(
                            "[calibration.".length(),
                            line.length() - 1).trim();
                    current = id.isEmpty()
                            ? null
                            : ENTRIES.computeIfAbsent(id, ignored -> new Entry());
                    continue;
                }

                if (current == null) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                try {
                    switch (key) {
                        case "locked" -> current.locked = Boolean.parseBoolean(value);
                        case "model_enabled" -> current.modelEnabled = Boolean.parseBoolean(value);
                        case "model_y" -> current.modelY = Double.parseDouble(value);

                        case "seat_enabled" -> current.seatEnabled = Boolean.parseBoolean(value);
                        case "seat_long" -> current.seatLong = Double.parseDouble(value);
                        case "seat_y" -> current.seatY = Double.parseDouble(value);
                        case "seat_side" -> current.seatSide = Double.parseDouble(value);

                        case "seat2_enabled" -> current.seat2Enabled = Boolean.parseBoolean(value);
                        case "seat2_long" -> current.seat2Long = Double.parseDouble(value);
                        case "seat2_y" -> current.seat2Y = Double.parseDouble(value);
                        case "seat2_side" -> current.seat2Side = Double.parseDouble(value);

                        case "seat3_enabled" -> current.seat3Enabled = Boolean.parseBoolean(value);
                        case "seat3_long" -> current.seat3Long = Double.parseDouble(value);
                        case "seat3_y" -> current.seat3Y = Double.parseDouble(value);
                        case "seat3_side" -> current.seat3Side = Double.parseDouble(value);

                        default -> {
                            // Ignore unknown future keys.
                        }
                    }
                } catch (RuntimeException badValue) {
                    LOG.warn("[TC-CAL-CONFIG] ignoring invalid {}={} in {}",
                            key, value, path.toAbsolutePath());
                }
            }
        } catch (IOException ex) {
            LOG.error("[TC-CAL-CONFIG] failed reading {}",
                    path.toAbsolutePath(), ex);
        }

        LOG.info("[TC-CAL-CONFIG] loaded {} entries from {}",
                ENTRIES.size(), path.toAbsolutePath());
    }

    private static boolean installBundledReleaseDefaults(Path path) {
        final String resource =
                "/data/traincraft/calibration/default_rolling_stock_calibration.toml";

        try (InputStream input =
                     RollingStockCalibrationConfig.class.getResourceAsStream(resource)) {

            if (input == null) {
                LOG.error("[TC-CAL-CONFIG] missing bundled release defaults {}", resource);
                return false;
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            LOG.error("[TC-CAL-CONFIG] failed installing bundled release defaults to {}",
                    path.toAbsolutePath(), ex);
            return false;
        }
    }
    private static void seedApprovedDefaults() {
        putSeat1Only("locomotive_diesel_cd742",
                0.4900D, -1.3491D, 0.0115D, true);
        putSeat1Only("locomotive_diesel_v60",
                0.1093D, -0.1425D, -0.0189D, true);
        putSeat1Only("locomotive_electric_tram",
                0.0090D, -0.7425D, -0.0173D, true);
        putSeat1Only("locomotive_electric_tram_ny",
                0.4900D, -0.0239D, 0.0586D, true);

        putFullSeat1("locomotive_electric_vl10",
                0.0000D, 2.5130D, -0.1000D, 0.0289D, true);
        putFullSeat1("locomotive_electric_high_speed_zeroed",
                -0.0500D, 2.3008D, 0.1375D, 0.0568D, true);
        putFullSeat1("locomotive_electric_minetrain",
                0.4500D, 0.0361D, -0.0625D, 0.0380D, true);
    }

    private static void putSeat1Only(
            String id, double seatLong, double seatY, double seatSide,
            boolean locked) {
        Entry e = new Entry();
        e.locked = locked;
        e.modelEnabled = false;
        e.modelY = 0.0D;
        e.seatEnabled = true;
        e.seatLong = seatLong;
        e.seatY = seatY;
        e.seatSide = seatSide;
        ENTRIES.put(id, e);
    }

    private static void putFullSeat1(
            String id, double modelY,
            double seatLong, double seatY, double seatSide,
            boolean locked) {
        Entry e = new Entry();
        e.locked = locked;
        e.modelEnabled = true;
        e.modelY = modelY;
        e.seatEnabled = true;
        e.seatLong = seatLong;
        e.seatY = seatY;
        e.seatSide = seatSide;
        ENTRIES.put(id, e);
    }

    private static void writeFile() {
        Path path = path();
        StringBuilder out = new StringBuilder(8192);

        out.append("# TafariCraft - Trains rolling-stock calibration\n");
        out.append("# Generated/updated by the in-game /tccal calibration tool.\n");
        out.append("# model_y is the visual body Y offset.\n");
        out.append("# Seat 1 uses the legacy seat_* names for backward compatibility.\n");
        out.append("# Seat 2 uses seat2_* and Seat 3 uses seat3_*.\n\n");

        for (Map.Entry<String, Entry> row : ENTRIES.entrySet()) {
            Entry e = row.getValue();
            out.append("[calibration.").append(row.getKey()).append("]\n");
            out.append("locked = ").append(e.locked).append('\n');
            out.append("model_enabled = ").append(e.modelEnabled).append('\n');
            out.append(String.format(Locale.ROOT, "model_y = %.4f%n", e.modelY));

            out.append("seat_enabled = ").append(e.seatEnabled).append('\n');
            out.append(String.format(Locale.ROOT, "seat_long = %.4f%n", e.seatLong));
            out.append(String.format(Locale.ROOT, "seat_y = %.4f%n", e.seatY));
            out.append(String.format(Locale.ROOT, "seat_side = %.4f%n", e.seatSide));

            out.append("seat2_enabled = ").append(e.seat2Enabled).append('\n');
            out.append(String.format(Locale.ROOT, "seat2_long = %.4f%n", e.seat2Long));
            out.append(String.format(Locale.ROOT, "seat2_y = %.4f%n", e.seat2Y));
            out.append(String.format(Locale.ROOT, "seat2_side = %.4f%n", e.seat2Side));

            out.append("seat3_enabled = ").append(e.seat3Enabled).append('\n');
            out.append(String.format(Locale.ROOT, "seat3_long = %.4f%n", e.seat3Long));
            out.append(String.format(Locale.ROOT, "seat3_y = %.4f%n", e.seat3Y));
            out.append(String.format(Locale.ROOT, "seat3_side = %.4f%n%n", e.seat3Side));
        }

        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(temp, out.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not write calibration config: " +
                    path.toAbsolutePath(), ex);
        }
    }
}