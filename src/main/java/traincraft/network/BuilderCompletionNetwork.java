package traincraft.network;

// TC_BATCH8_R4A5G25H7_RESPONSIVE_BUILDER_COMPLETE_POPUP

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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

/**
 * One-way server -> client signal for the Tracks Builder completion overlay.
 *
 * The server remains authoritative for who is inside the completed Blueprint
 * construction grid. Only those players receive this packet.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BuilderCompletionNetwork {
    private static final String VERSION = "1";
    private static SimpleChannel channel;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BuilderCompletionNetwork::init);
    }

    public static synchronized void init() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(
                        Traincraft.MOD_ID,
                        "builder_completion"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        channel.registerMessage(
                0,
                CompletionPacket.class,
                CompletionPacket::encode,
                CompletionPacket::decode,
                CompletionPacket::handle);
    }

    public static void sendToPlayer(
            ServerPlayer player,
            int total) {

        init();

        channel.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CompletionPacket(
                        Math.max(0, total)));
    }

    private record CompletionPacket(int total) {
        private static void encode(
                CompletionPacket message,
                FriendlyByteBuf buffer) {

            buffer.writeVarInt(
                    Math.max(0, message.total));
        }

        private static CompletionPacket decode(
                FriendlyByteBuf buffer) {

            return new CompletionPacket(
                    Math.max(0, buffer.readVarInt()));
        }

        private static void handle(
                CompletionPacket message,
                Supplier<NetworkEvent.Context> contextSupplier) {

            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () ->
                                    traincraft.client.event.BuilderCompletionOverlay
                                            .show(message.total)));

            context.setPacketHandled(true);
        }
    }

    private BuilderCompletionNetwork() {}
}
