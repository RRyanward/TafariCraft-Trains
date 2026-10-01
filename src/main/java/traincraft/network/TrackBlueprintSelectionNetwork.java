package traincraft.network;

// TC_BATCH8_R3P_BLUEPRINT_SCROLL_FAMILY_NAV
// TC_BATCH8_R3ZU_BLUEPRINT_RENAME_PACKET

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import traincraft.Traincraft;
import traincraft.item.TrackBlueprintItem;

import java.util.function.Supplier;

/** Server-authoritative Blueprint selection + rename packets. */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrackBlueprintSelectionNetwork {
    private static final String VERSION = "1";
    private static SimpleChannel channel;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TrackBlueprintSelectionNetwork::init);
    }

    public static synchronized void init() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(
                        Traincraft.MOD_ID,
                        "blueprint_selection"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        channel.registerMessage(
                0,
                CyclePacket.class,
                CyclePacket::encode,
                CyclePacket::decode,
                CyclePacket::handle);

        channel.registerMessage(
                1,
                RenamePacket.class,
                RenamePacket::encode,
                RenamePacket::decode,
                RenamePacket::handle);
    }

    public static void sendCycle(int direction, boolean familyOnly) {
        init();
        channel.sendToServer(
                new CyclePacket(
                        direction < 0 ? -1 : 1,
                        familyOnly));
    }

    public static void sendRename(String name) {
        init();

        String safe =
                name == null
                        ? ""
                        : name.strip();

        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }

        channel.sendToServer(
                new RenamePacket(safe));
    }

    private static ItemStack heldBlueprint(ServerPlayer player) {
        ItemStack blueprint =
                player.getMainHandItem();

        if (!(blueprint.getItem()
                instanceof TrackBlueprintItem)) {
            blueprint =
                    player.getOffhandItem();
        }

        return blueprint;
    }

    private record CyclePacket(
            int direction,
            boolean familyOnly) {
        private static void encode(
                CyclePacket message,
                FriendlyByteBuf buffer) {
            buffer.writeByte(message.direction);
            buffer.writeBoolean(message.familyOnly);
        }

        private static CyclePacket decode(
                FriendlyByteBuf buffer) {
            int direction =
                    buffer.readByte() < 0 ? -1 : 1;
            boolean familyOnly =
                    buffer.readBoolean();

            return new CyclePacket(
                    direction,
                    familyOnly);
        }

        private static void handle(
                CyclePacket message,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            ServerPlayer player =
                    context.getSender();

            if (player != null) {
                context.enqueueWork(() -> {
                    ItemStack blueprint =
                            heldBlueprint(player);

                    if (!(blueprint.getItem()
                            instanceof TrackBlueprintItem)
                            || TrackBlueprintItem.isLocked(blueprint)) {
                        return;
                    }

                    TrackBlueprintItem.cycleSelection(
                            blueprint,
                            player,
                            message.direction,
                            message.familyOnly);
                });
            }

            context.setPacketHandled(true);
        }
    }

    private record RenamePacket(String name) {
        private static void encode(
                RenamePacket message,
                FriendlyByteBuf buffer) {
            buffer.writeUtf(
                    message.name == null
                            ? ""
                            : message.name,
                    40);
        }

        private static RenamePacket decode(
                FriendlyByteBuf buffer) {
            return new RenamePacket(
                    buffer.readUtf(40));
        }

        private static void handle(
                RenamePacket message,
                Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            ServerPlayer player =
                    context.getSender();

            if (player != null) {
                context.enqueueWork(() -> {
                    ItemStack blueprint =
                            heldBlueprint(player);

                    if (!(blueprint.getItem()
                            instanceof TrackBlueprintItem)) {
                        return;
                    }

                    String cleaned =
                            message.name == null
                                    ? ""
                                    : message.name
                                            .replaceAll("\\p{Cntrl}", "")
                                            .strip();

                    if (cleaned.length() > 40) {
                        cleaned =
                                cleaned.substring(0, 40);
                    }

                    if (cleaned.isBlank()
                            || "Blueprint".equals(cleaned)) {
                        blueprint.resetHoverName();
                    } else {
                        blueprint.setHoverName(
                                Component.literal(cleaned)
                                        .withStyle(
                                                ChatFormatting.AQUA));
                    }

                    player.getInventory().setChanged();
                    player.inventoryMenu.broadcastChanges();

                    if (player.containerMenu
                            != player.inventoryMenu) {
                        player.containerMenu.broadcastChanges();
                    }
                });
            }

            context.setPacketHandled(true);
        }
    }

    private TrackBlueprintSelectionNetwork() {}
}
