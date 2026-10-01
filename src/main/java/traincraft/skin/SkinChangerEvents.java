package traincraft.skin;

// TC_BATCH6_R2_LEGACY_SKIN_GUI

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.registry.TCItems;

/** Classic Traincraft Paintbrush/Skin Changer interaction. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkinChangerEvents {
    /** Retained so old r1 test entities do not crash when loaded. */
    public static final String SKIN_INDEX_TAG = "TafariCraftSkinIndex";
    public static final String SKIN_KEY_TAG = "TafariCraftSkinKey";

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(TCItems.SKIN_CHANGER.get())) {
            return;
        }

        Entity target = event.getTarget();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (!(target instanceof AbstractMinecart)
                || typeId == null
                || !Traincraft.MOD_ID.equals(typeId.getNamespace())
                || "tracks_builder".equals(typeId.getPath())) {
            return;
        }

        if (!event.getEntity().isShiftKeyDown()) {
            if (!event.getLevel().isClientSide) {
                event.getEntity().displayClientMessage(
                        Component.literal("Sneak + right-click rolling stock to open the skin menu."),
                        true);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }

        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            SkinSyncNetwork.openMenu(player, target);
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getTarget();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (!(target instanceof AbstractMinecart)
                || typeId == null
                || !Traincraft.MOD_ID.equals(typeId.getNamespace())) {
            return;
        }

        SkinSyncNetwork.sendToPlayer(
                player,
                target,
                target.getPersistentData().getString(SKIN_KEY_TAG));
    }

    private SkinChangerEvents() {}
}
