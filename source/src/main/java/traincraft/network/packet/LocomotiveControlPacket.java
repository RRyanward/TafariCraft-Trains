/*
 * Traincraft 1.20.1 locomotive control packet.
 * Distributed under LGPL-v3.0.
 */
package traincraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.util.function.Supplier;

/** Client -> server input for the locomotive the player is currently driving. */
public final class LocomotiveControlPacket {
    private final int entityId;
    private final boolean forward;
    private final boolean reverse;
    private final boolean brake;

    public LocomotiveControlPacket(int entityId, boolean forward, boolean reverse, boolean brake) {
        this.entityId = entityId;
        this.forward = forward;
        this.reverse = reverse;
        this.brake = brake;
    }

    public static void encode(LocomotiveControlPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeBoolean(message.forward);
        buffer.writeBoolean(message.reverse);
        buffer.writeBoolean(message.brake);
    }

    public static LocomotiveControlPacket decode(FriendlyByteBuf buffer) {
        return new LocomotiveControlPacket(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    public static void handle(LocomotiveControlPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            // Clients may control only the locomotive they are physically driving.
            if (sender.getVehicle() != locomotive || locomotive.getFirstPassenger() != sender) {
                return;
            }

            locomotive.setDriverControls(message.forward, message.reverse, message.brake);
        });

        context.setPacketHandled(true);
    }
}
