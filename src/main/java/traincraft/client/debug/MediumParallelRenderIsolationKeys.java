package traincraft.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import traincraft.Traincraft;

/** DEV50b2r: F8 cycles client-only visual diagnostic modes; does not edit saves. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MediumParallelRenderIsolationKeys {
    private MediumParallelRenderIsolationKeys() { }
    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_F8) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        MediumParallelRenderIsolation.cycle();
    }
}
