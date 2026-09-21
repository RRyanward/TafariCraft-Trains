package traincraft.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import traincraft.client.screen.TenderScreen;
import traincraft.registry.TCMenus;

/**
 * Step 7.1.1a: Forge 47.3.22-compatible client registration for the classic
 * tender GUI. Forge 1.20.1 / 47.3.22 does not provide RegisterMenuScreensEvent,
 * so registration is performed during FMLClientSetupEvent instead.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TenderClientEvents {
    private TenderClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(TCMenus.TENDER.get(), TenderScreen::new)
        );
    }
}
