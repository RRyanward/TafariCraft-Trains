package traincraft.network;

// TC_DEV38_R1D_SERVER_AUTHORITATIVE_CALIBRATION

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Server-authoritative rolling-stock calibration.
 *
 * The server owns config/tafaricraft-trains-calibration.toml.
 * A full snapshot is sent on login and after server-side edits.
 *
 * This is deliberately independent of train movement/coupling/network code.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RollingStockCalibrationNetwork {
    private static final String VERSION = "1";
    private static final int MAX_TOML_CHARS = 262144;
    private static SimpleChannel channel;

    private RollingStockCalibrationNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(RollingStockCalibrationNetwork::init);
    }

    public static synchronized void init() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Traincraft.MOD_ID, "calibration_sync"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        channel.registerMessage(
                0,
                SnapshotPacket.class,
                SnapshotPacket::encode,
                SnapshotPacket::decode,
                SnapshotPacket::handle);
    }

    public static String readServerSnapshot() {
        try {
            Path path = RollingStockCalibrationConfig.path();

            if (!Files.isRegularFile(path)) {
                // reload() creates the packaged factory defaults on a fresh
                // server before the first snapshot is read.
                RollingStockCalibrationConfig.reload();
            }

            if (!Files.isRegularFile(path)) {
                return "";
            }

            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to read server rolling-stock calibration", ex);
        }
    }

    public static void sendToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        String snapshot = readServerSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        init();
        channel.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SnapshotPacket(snapshot));
    }

    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }

        String snapshot = readServerSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        init();
        SnapshotPacket packet = new SnapshotPacket(snapshot);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            channel.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet);
        }
    }

    private record SnapshotPacket(String toml) {
        private static void encode(
                SnapshotPacket message,
                FriendlyByteBuf buffer) {

            buffer.writeUtf(
                    message.toml == null ? "" : message.toml,
                    MAX_TOML_CHARS);
        }

        private static SnapshotPacket decode(FriendlyByteBuf buffer) {
            return new SnapshotPacket(
                    buffer.readUtf(MAX_TOML_CHARS));
        }

        private static void handle(
                SnapshotPacket message,
                Supplier<NetworkEvent.Context> contextSupplier) {

            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> applyClientSnapshot(message.toml)));

            context.setPacketHandled(true);
        }
    }

    private static void applyClientSnapshot(String toml) {
        if (toml == null || toml.isBlank()) {
            return;
        }

        Path path = RollingStockCalibrationConfig.path();
        Path parent = path.getParent();
        Path temp = path.resolveSibling(path.getFileName() + ".server-sync.tmp");

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(
                    temp,
                    toml,
                    StandardCharsets.UTF_8);

            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            RollingStockCalibrationConfig.reload();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to apply server rolling-stock calibration", ex);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Best-effort cleanup only.
            }
        }
    }
}
