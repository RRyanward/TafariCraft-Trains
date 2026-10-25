package traincraft.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import traincraft.lighting.LocomotiveLightMode;
import traincraft.network.TCNetwork;
import traincraft.network.packet.LocomotiveLightModePacket;

/** Third-person HUD displays a live mode + its L-key control. */
@Mod.EventBusSubscriber(modid="traincraft", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class LocomotiveLightControls {
    public static final KeyMapping LIGHT_MODE = new KeyMapping(
            "key.traincraft.cycle_locomotive_lights", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "key.categories.traincraft");
    private LocomotiveLightControls() {}
    @Mod.EventBusSubscriber(modid="traincraft", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){
            event.register(LIGHT_MODE);
        }
    }
    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || mc.options.getCameraType().isFirstPerson())return;
        while(LIGHT_MODE.consumeClick()){
            var e=mc.player.getVehicle();
            if(!LocomotiveLightMode.locomotive(e))continue;
            int next=(LocomotiveLightMode.mode(e)+1)%3;
            LocomotiveLightMode.syncClient(e.getUUID(),next);
            TCNetwork.CHANNEL.sendToServer(new LocomotiveLightModePacket(e.getId(),next));
        }
    }
}
