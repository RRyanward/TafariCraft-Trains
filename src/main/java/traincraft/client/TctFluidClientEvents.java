package traincraft.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import traincraft.client.screen.TctFluidScreen;
import traincraft.registry.TCMenus;

@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TctFluidClientEvents {
    private TctFluidClientEvents() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(TCMenus.TCT_TANKER.get(), TctFluidScreen::new));
    }
}
