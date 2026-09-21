/*
 * Traincraft 1.20.1 networking bootstrap.
 * Distributed under LGPL-v3.0.
 */
package traincraft.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import traincraft.Traincraft;
import traincraft.network.packet.LocomotiveControlPacket;
import traincraft.network.packet.OpenSteamLocomotiveMenuPacket;
import traincraft.network.packet.ToggleHandBrakePacket;
import traincraft.network.packet.LocomotiveWhistlePacket;

/** Network channel used by the 1.20.1 Traincraft port. */
public final class TCNetwork {
    private static final String PROTOCOL_VERSION = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Traincraft.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private TCNetwork() {
    }

    /** Registers all packets. Call exactly once during mod construction. */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                LocomotiveControlPacket.class,
                LocomotiveControlPacket::encode,
                LocomotiveControlPacket::decode,
                LocomotiveControlPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                OpenSteamLocomotiveMenuPacket.class,
                OpenSteamLocomotiveMenuPacket::encode,
                OpenSteamLocomotiveMenuPacket::decode,
                OpenSteamLocomotiveMenuPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                ToggleHandBrakePacket.class,
                ToggleHandBrakePacket::encode,
                ToggleHandBrakePacket::decode,
                ToggleHandBrakePacket::handle
        );
        CHANNEL.registerMessage(
                id,
                LocomotiveWhistlePacket.class,
                LocomotiveWhistlePacket::encode,
                LocomotiveWhistlePacket::decode,
                LocomotiveWhistlePacket::handle
        );
    }
}
