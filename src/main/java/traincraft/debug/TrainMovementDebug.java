package traincraft.debug;

import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Step 7.3.2d movement flight recorder with stable travel-direction telemetry.
 *
 * Each client/server launch receives unique CSV filenames so separate tests do not
 * get mixed together.  The recorder changes no movement, coupling or rail state.
 */
public final class TrainMovementDebug {
    private static final Logger LOG = LoggerFactory.getLogger("TraincraftMovementDebug");
    private static final String SESSION = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .format(LocalDateTime.now());
    private static final Path GAME_DIR = FMLPaths.GAMEDIR.get();
    private static final Set<Path> INITIALIZED = new HashSet<>();

    public static final Path MOTION_FILE = GAME_DIR.resolve(
            "traincraft-rail-motion-" + SESSION + ".csv");
    public static final Path CONTROLLER_FILE = GAME_DIR.resolve(
            "traincraft-rail-controller-" + SESSION + ".csv");
    public static final Path PRIMARY_CONTROLLER_FILE = GAME_DIR.resolve(
            "traincraft-primary-controller-" + SESSION + ".csv");
    public static final Path GOVERNOR_FILE = GAME_DIR.resolve(
            "traincraft-loco-governor-" + SESSION + ".csv");

    private TrainMovementDebug() {
    }

    public static synchronized void append(Path file, String header, String row) {
        try {
            if (INITIALIZED.add(file)) {
                String preamble = "# Traincraft Step 7.3.2d movement debug session=" + SESSION
                        + System.lineSeparator()
                        + header + System.lineSeparator();
                Files.writeString(file, preamble,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            }

            Files.writeString(file, row + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            LOG.error("Failed to write Traincraft movement debug file {}", file, exception);
        }
    }
}
