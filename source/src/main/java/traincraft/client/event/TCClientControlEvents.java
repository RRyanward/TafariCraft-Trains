/*
 * Traincraft 1.20.1 client driving and locomotive controls.
 * Distributed under LGPL-v3.0.
 */
package traincraft.client.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.client.TCKeyMappings;
import traincraft.client.screen.SteamLocomotiveScreen;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.network.TCNetwork;
import traincraft.network.packet.LocomotiveControlPacket;
import traincraft.network.packet.OpenSteamLocomotiveMenuPacket;
import traincraft.network.packet.ToggleHandBrakePacket;
import traincraft.network.packet.LocomotiveWhistlePacket;

/** Reads the classic Traincraft W/S/Space controls and R locomotive-GUI key. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TCClientControlEvents {
    private static int lastEntityId = -1;
    private static boolean lastForward;
    private static boolean lastReverse;
    private static boolean lastBrake;
    private static int keepAliveTicks;

    private TCClientControlEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            resetLocalState();
            return;
        }

        if (!(minecraft.player.getVehicle() instanceof SmallSteamLocomotive locomotive)) {
            resetLocalState();
            return;
        }

        // Known-working classic Traincraft interaction from Step 6.7: while seated
        // in the locomotive, press R to open its fuel/water/inventory GUI. Keep this
        // in ClientTick so KeyMapping.consumeClick() uses Minecraft's normal key
        // processing path and still honors rebinding.
        if (minecraft.screen == null && TCKeyMappings.OPEN_LOCOMOTIVE_GUI.consumeClick()) {
            Traincraft.LOGGER.debug("Traincraft locomotive GUI tick-key pressed for entity {}", locomotive.getId());
            TCNetwork.CHANNEL.sendToServer(new OpenSteamLocomotiveMenuPacket(locomotive.getId()));
        }

        // The steam locomotive screen handles W/S/Space/C/H itself so the driver
        // can keep operating the train while viewing fuel/water/steam. Do not send
        // the normal world-view false-state packet over those GUI-owned controls.
        if (minecraft.screen instanceof SteamLocomotiveScreen) {
            resetLocalState();
            return;
        }

        // Classic hand brake: C toggles a persistent locomotive parking brake.
        if (minecraft.screen == null && TCKeyMappings.HAND_BRAKE.consumeClick()) {
            TCNetwork.CHANNEL.sendToServer(new ToggleHandBrakePacket(locomotive.getId()));
        }

        // Classic Traincraft horn/whistle key: H. One packet per key press;
        // the server plays the locomotive-specific sound so nearby players hear it.
        if (minecraft.screen == null && TCKeyMappings.WHISTLE.consumeClick()) {
            TCNetwork.CHANNEL.sendToServer(new LocomotiveWhistlePacket(locomotive.getId()));
        }

        // Do not keep driving while chat/menu/inventory is open.
        boolean acceptsDrivingInput = minecraft.screen == null;
        boolean forward = acceptsDrivingInput && minecraft.options.keyUp.isDown();
        boolean reverse = acceptsDrivingInput && minecraft.options.keyDown.isDown();
        boolean brake = acceptsDrivingInput && minecraft.options.keyJump.isDown();
        int entityId = locomotive.getId();

        keepAliveTicks++;
        boolean changed = entityId != lastEntityId
                || forward != lastForward
                || reverse != lastReverse
                || brake != lastBrake;
        boolean keepAlive = keepAliveTicks >= 20;

        if (changed || keepAlive) {
            TCNetwork.CHANNEL.sendToServer(new LocomotiveControlPacket(entityId, forward, reverse, brake));
            lastEntityId = entityId;
            lastForward = forward;
            lastReverse = reverse;
            lastBrake = brake;
            keepAliveTicks = 0;
        }
    }


    private static void resetLocalState() {
        lastEntityId = -1;
        lastForward = false;
        lastReverse = false;
        lastBrake = false;
        keepAliveTicks = 0;
    }
}
