package traincraft.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.client.screen.TCTSeatAlignScreen;

/** DEV53E3H: purely client-side command for a responsive editing screen. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TCTSeatAlignClientCommand {
    private TCTSeatAlignClientCommand() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        // Forge merges this leaf with the already registered server /tct tree.
        event.getDispatcher().register(Commands.literal("tct")
                .then(Commands.literal("seat")
                        .then(Commands.literal("align")
                                .executes(ctx -> {
                                    Minecraft minecraft = Minecraft.getInstance();
                                    minecraft.execute(() -> minecraft.setScreen(new TCTSeatAlignScreen()));
                                    return 1;
                                }))));
    }
}
