package traincraft.skin;

// TC_BATCH6_R2_LEGACY_SKIN_GUI

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.registry.TCItems;

import java.util.Locale;
import java.util.function.Supplier;

/** Server-authoritative network for the classic Skin Changer selector. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SkinSyncNetwork {
    private static final String VERSION = "2";
    private static SimpleChannel channel;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SkinSyncNetwork::init);
    }

    public static synchronized void init() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Traincraft.MOD_ID, "skin_sync"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        channel.registerMessage(
                0, SkinSyncPacket.class,
                SkinSyncPacket::encode, SkinSyncPacket::decode, SkinSyncPacket::handle);
        channel.registerMessage(
                1, OpenSkinMenuPacket.class,
                OpenSkinMenuPacket::encode, OpenSkinMenuPacket::decode, OpenSkinMenuPacket::handle);
        channel.registerMessage(
                2, SelectSkinPacket.class,
                SelectSkinPacket::encode, SelectSkinPacket::decode, SelectSkinPacket::handle);
    }

    public static void openMenu(ServerPlayer player, Entity entity) {
        init();
        channel.send(PacketDistributor.PLAYER.with(() -> player), new OpenSkinMenuPacket(entity.getId()));
    }

    public static void sendToTrackingAndSelf(Entity entity, String skinKey) {
        init();
        channel.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new SkinSyncPacket(entity.getId(), skinKey));
    }

    public static void sendToPlayer(ServerPlayer player, Entity entity, String skinKey) {
        init();
        channel.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SkinSyncPacket(entity.getId(), skinKey));
    }

    public static void sendSelectionToServer(int entityId, String skinKey) {
        init();
        channel.sendToServer(new SelectSkinPacket(entityId, skinKey));
    }

    private static boolean validKey(String skinKey) {
        return skinKey != null
                && skinKey.length() <= 32
                && skinKey.matches("[a-z0-9_\\-]*");
    }

    private static boolean isTraincraftRollingStock(Entity target) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return target instanceof AbstractMinecart
                && typeId != null
                && Traincraft.MOD_ID.equals(typeId.getNamespace())
                && !"tracks_builder".equals(typeId.getPath());
    }

    private static String friendly(String key) {
        if (key == null || key.isEmpty()) {
            return "Original";
        }
        String[] pieces = key.split("_");
        StringBuilder out = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(piece.substring(0, 1).toUpperCase(Locale.ROOT));
            if (piece.length() > 1) out.append(piece.substring(1));
        }
        return out.toString();
    }

    private static final class SkinSyncPacket {
        private final int entityId;
        private final String skinKey;

        private SkinSyncPacket(int entityId, String skinKey) {
            this.entityId = entityId;
            this.skinKey = skinKey == null ? "" : skinKey;
        }

        private static void encode(SkinSyncPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
            buffer.writeUtf(message.skinKey, 32);
        }

        private static SkinSyncPacket decode(FriendlyByteBuf buffer) {
            return new SkinSyncPacket(buffer.readVarInt(), buffer.readUtf(32));
        }

        private static void handle(SkinSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> traincraft.client.skin.ClientRollingStockSkinManager.applySyncedSkin(
                            message.entityId, message.skinKey)));
            context.setPacketHandled(true);
        }
    }

    private static final class OpenSkinMenuPacket {
        private final int entityId;

        private OpenSkinMenuPacket(int entityId) {
            this.entityId = entityId;
        }

        private static void encode(OpenSkinMenuPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
        }

        private static OpenSkinMenuPacket decode(FriendlyByteBuf buffer) {
            return new OpenSkinMenuPacket(buffer.readVarInt());
        }

        private static void handle(OpenSkinMenuPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> traincraft.client.skin.ClientRollingStockSkinManager.openMenu(message.entityId)));
            context.setPacketHandled(true);
        }
    }

    private static final class SelectSkinPacket {
        private final int entityId;
        private final String skinKey;

        private SelectSkinPacket(int entityId, String skinKey) {
            this.entityId = entityId;
            this.skinKey = skinKey == null ? "" : skinKey;
        }

        private static void encode(SelectSkinPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
            buffer.writeUtf(message.skinKey, 32);
        }

        private static SelectSkinPacket decode(FriendlyByteBuf buffer) {
            return new SelectSkinPacket(buffer.readVarInt(), buffer.readUtf(32));
        }

        private static void handle(SelectSkinPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !validKey(message.skinKey)) {
                    return;
                }
                if (!player.getMainHandItem().is(TCItems.SKIN_CHANGER.get())
                        && !player.getOffhandItem().is(TCItems.SKIN_CHANGER.get())) {
                    return;
                }

                Entity target = player.serverLevel().getEntity(message.entityId);
                if (target == null || !isTraincraftRollingStock(target)) {
                    return;
                }
                if (player.distanceToSqr(target) > 100.0D) {
                    return;
                }

                target.getPersistentData().putString(SkinChangerEvents.SKIN_KEY_TAG, message.skinKey);
                target.getPersistentData().remove(SkinChangerEvents.SKIN_INDEX_TAG);
                sendToTrackingAndSelf(target, message.skinKey);
                player.displayClientMessage(
                        Component.literal("Skin: " + friendly(message.skinKey)),
                        true);
            });
            context.setPacketHandled(true);
        }
    }

    private SkinSyncNetwork() {}
}
