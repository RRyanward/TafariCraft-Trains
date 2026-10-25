package traincraft.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.client.screen.TrainInstrumentSettingsScreen;

/** Adds an unobtrusive entry point to vanilla Minecraft Options. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainInstrumentOptionsButton {
    private TrainInstrumentOptionsButton() { }

    @SubscribeEvent
    public static void addOptionsButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen)) return;
        var parent = event.getScreen();
        int x = Math.max(4, parent.width - 166);
        event.addListener(Button.builder(
                Component.literal("TafariCraft Instruments"),
                button -> Minecraft.getInstance().setScreen(
                        new TrainInstrumentSettingsScreen(parent)))
                .bounds(x, 6, 162, 20).build());
    }
}
