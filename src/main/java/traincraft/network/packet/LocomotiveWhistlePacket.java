/*
 * Traincraft 1.20.1 locomotive whistle packet.
 * Distributed under LGPL-v3.0.
 */
package traincraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.util.function.Supplier;

/** Client -> server request to sound the locomotive whistle. */
public final class LocomotiveWhistlePacket {
    private final int entityId;

    public LocomotiveWhistlePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(LocomotiveWhistlePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
    }

    public static LocomotiveWhistlePacket decode(FriendlyByteBuf buffer) {
        return new LocomotiveWhistlePacket(buffer.readVarInt());
    }

    public static void handle(LocomotiveWhistlePacket message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
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

            // Only the player actually driving this locomotive can sound it.
            if (sender.getVehicle() != locomotive || locomotive.getFirstPassenger() != sender) {
                return;
            }

            locomotive.playWhistle(sender);
        });
        context.setPacketHandled(true);
    }
}
