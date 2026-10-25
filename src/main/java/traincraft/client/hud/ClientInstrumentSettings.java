package traincraft.client.hud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;
import traincraft.Traincraft;

/** Client-local preferences; never synchronized to servers or worlds. */
public final class ClientInstrumentSettings {
    private static final String FILE = "tafaricraft-instruments-client.properties";
    private static boolean loaded;
    private static boolean enabled = true;
    private static boolean rightSide;

    private ClientInstrumentSettings() { }

    private static Path location() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static synchronized void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        Path file = location();
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            p.load(stream);
            enabled = Boolean.parseBoolean(p.getProperty("instrumentsEnabled", "true"));
            rightSide = "right".equalsIgnoreCase(p.getProperty("position", "left"));
        } catch (IOException ex) {
            Traincraft.LOGGER.warn("Could not read instrument settings {}", file, ex);
        }
    }

    public static synchronized boolean isEnabled() {
        loadIfNeeded();
        return enabled;
    }

    public static synchronized boolean isRightSide() {
        loadIfNeeded();
        return rightSide;
    }

    public static synchronized void setEnabled(boolean value) {
        loadIfNeeded();
        enabled = value;
        save();
    }

    public static synchronized void setRightSide(boolean value) {
        loadIfNeeded();
        rightSide = value;
        save();
    }

    private static void save() {
        Path file = location();
        Path temporary = file.resolveSibling(FILE + ".tmp");
        Properties p = new Properties();
        p.setProperty("instrumentsEnabled", Boolean.toString(enabled));
        p.setProperty("position", rightSide ? "right" : "left");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                p.store(stream, "TafariCraft - Trains local instrument HUD preferences");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Traincraft.LOGGER.warn("Could not save instrument settings {}", file, ex);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
