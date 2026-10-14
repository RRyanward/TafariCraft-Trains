package traincraft.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

/**
 * CLIENT-ONLY half of the DEV38 r1g automatic capture harness.
 *
 * Server controls scene/camera. Client hides HUD, forces first person,
 * waits for rendered frames and writes the vanilla PNG.
 *
 * REMOVE BEFORE RELEASE.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1gAutoCaptureClient {
    private static String pendingFile;
    private static int captureDelay = -1;
    private static int restoreDelay = -1;
    private static boolean savedHideGui;
    private static CameraType savedCamera = CameraType.FIRST_PERSON;

    private Dev38R1gAutoCaptureClient() {}

    public static void queue(String fileName) {
        Minecraft mc = Minecraft.getInstance();

        if (fileName == null || fileName.isBlank()) {
            cancelAndRestore(mc);
            return;
        }

        savedHideGui = mc.options.hideGui;
        savedCamera = mc.options.getCameraType();

        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);

        pendingFile = fileName;
        captureDelay = 3;
        restoreDelay = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancelAndRestore(mc);
            return;
        }

        if (pendingFile != null) {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);

            if (captureDelay > 0) {
                captureDelay--;
                return;
            }

            String file = pendingFile;
            pendingFile = null;
            captureDelay = -1;

            Screenshot.grab(
                    mc.gameDirectory,
                    file,
                    mc.getMainRenderTarget(),
                    message -> mc.execute(() ->
                            mc.gui.getChat().addMessage(Component.literal(
                                    "[TC r1g CAPTURE] " + message.getString()))));

            restoreDelay = 2;
            return;
        }

        if (restoreDelay >= 0) {
            if (restoreDelay > 0) {
                restoreDelay--;
                return;
            }

            restoreDelay = -1;
            mc.options.hideGui = savedHideGui;
            mc.options.setCameraType(savedCamera);
        }
    }

    private static void cancelAndRestore(Minecraft mc) {
        pendingFile = null;
        captureDelay = -1;
        restoreDelay = -1;

        if (mc != null) {
            mc.options.hideGui = savedHideGui;
            mc.options.setCameraType(savedCamera);
        }
    }
}
