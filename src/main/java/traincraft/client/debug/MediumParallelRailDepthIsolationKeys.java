package traincraft.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import traincraft.Traincraft;

/** DEV50b2w: F10 cycles read-only parallel-curve face groups. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MediumParallelRailDepthIsolationKeys {
    private MediumParallelRailDepthIsolationKeys() { }
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction()!=GLFW.GLFW_PRESS || event.getKey()!=GLFW.GLFW_KEY_F10) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.player==null || mc.screen!=null) return;
        MediumParallelRailDepthIsolation.cycle();
    }
}
