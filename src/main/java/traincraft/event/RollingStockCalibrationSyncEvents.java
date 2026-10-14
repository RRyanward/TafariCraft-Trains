package traincraft.event;

// TC_DEV38_R1D_SERVER_AUTHORITATIVE_CALIBRATION

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.network.RollingStockCalibrationNetwork;

/**
 * Publishes server calibration on login and whenever an OP changes /tc visual.
 *
 * The source TOML is compared once per second. A changed snapshot is pushed to
 * all connected clients, so body and seat visuals remain server-owned.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RollingStockCalibrationSyncEvents {
    private static String lastSnapshot = "";
    private static int tickCounter = 0;

    private RollingStockCalibrationSyncEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RollingStockCalibrationNetwork.sendToPlayer(player);
        lastSnapshot =
                RollingStockCalibrationNetwork.readServerSnapshot();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        String snapshot =
                RollingStockCalibrationNetwork.readServerSnapshot();

        if (snapshot.isEmpty() || snapshot.equals(lastSnapshot)) {
            return;
        }

        lastSnapshot = snapshot;
        RollingStockCalibrationNetwork.sendToAll(event.getServer());
    }
}
