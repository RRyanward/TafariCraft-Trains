package traincraft.client.event;

// TC_BATCH8_R3P_BLUEPRINT_SCROLL_FAMILY_NAV

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.item.TrackBlueprintItem;
import traincraft.network.TrackBlueprintSelectionNetwork;

/**
 * Mouse-wheel Blueprint navigation:
 *   wheel       = previous / next track inside the current family
 *   Shift+wheel = previous / next family
 *
 * Scroll is canceled while editing a Blueprint so the hotbar does not move.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackBlueprintScrollInput {
    @SubscribeEvent
    public static void onScroll(
            InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null
                || mc.screen != null
                || event.getScrollDelta() == 0.0D) {
            return;
        }

        ItemStack blueprint =
                mc.player.getMainHandItem();

        if (!(blueprint.getItem()
                instanceof TrackBlueprintItem)) {
            return;
        }

        if (TrackBlueprintItem.isLocked(blueprint)) {
            return;
        }

        int direction =
                event.getScrollDelta() > 0.0D
                        ? -1
                        : 1;

        boolean familyOnly =
                mc.player.isShiftKeyDown();

        TrackBlueprintSelectionNetwork.sendCycle(
                direction,
                familyOnly);

        event.setCanceled(true);
    }

    private TrackBlueprintScrollInput() {}
}
