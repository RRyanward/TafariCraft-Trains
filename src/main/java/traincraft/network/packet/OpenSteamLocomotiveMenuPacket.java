/* Client -> server request to open the GUI for the locomotive the player is riding. */
package traincraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import traincraft.Traincraft;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.util.function.Supplier;

public final class OpenSteamLocomotiveMenuPacket {
    private final int entityId;

    public OpenSteamLocomotiveMenuPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenSteamLocomotiveMenuPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
    }

    public static OpenSteamLocomotiveMenuPacket decode(FriendlyByteBuf buffer) {
        return new OpenSteamLocomotiveMenuPacket(buffer.readVarInt());
    }

    public static void handle(OpenSteamLocomotiveMenuPacket message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            // Never trust the client-side entity id to identify the server vehicle.
            // Entity ids can differ between the logical client and server. The
            // authoritative answer is the ServerPlayer's current vehicle.
            if (!(player.getVehicle() instanceof SmallSteamLocomotive locomotive)) {
                Traincraft.LOGGER.debug(
                        "Traincraft GUI request rejected: player {} is not riding a steam locomotive (client id {})",
                        player.getGameProfile().getName(), message.entityId);
                return;
            }

            Traincraft.LOGGER.debug(
                    "Traincraft GUI request accepted for player {}: client locomotive id {}, server locomotive id {}",
                    player.getGameProfile().getName(), message.entityId, locomotive.getId());

            // Open the menu for the server-authoritative locomotive the player is
            // actually riding. This keeps R secure without depending on entity-id
            // equality or first-passenger ordering.
            locomotive.openSteamMenu(player);
        });
        context.setPacketHandled(true);
    }
}
