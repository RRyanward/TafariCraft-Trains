/*
 * Traincraft 1.20.1 hand-brake packet.
 * Distributed under LGPL-v3.0.
 */
package traincraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.util.function.Supplier;

/** Client -> server request to toggle the hand brake on the locomotive being driven. */
public final class ToggleHandBrakePacket {
    private final int entityId;

    public ToggleHandBrakePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(ToggleHandBrakePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
    }

    public static ToggleHandBrakePacket decode(FriendlyByteBuf buffer) {
        return new ToggleHandBrakePacket(buffer.readVarInt());
    }

    public static void handle(ToggleHandBrakePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }

            Entity target = sender.level().getEntity(message.entityId);
            if (!(target instanceof SmallSteamLocomotive locomotive)) {
                return;
            }

            if (sender.getVehicle() != locomotive || locomotive.getFirstPassenger() != sender) {
                return;
            }

            locomotive.toggleHandBrake(sender);
        });
        context.setPacketHandled(true);
    }
}
