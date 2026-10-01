package traincraft.chunk;

// TC_BATCH8_R1C_CHUNK_LOADER_ACTIVATOR

import java.util.ArrayList;
import java.util.UUID;

import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import traincraft.Traincraft;

/**
 * Crash/stale-ticket safety.
 *
 * Clean shutdown releases all active tickets.  If a prior run ended
 * unexpectedly, this validation callback removes this mod's persisted entity
 * tickets before Forge reinstates them.  The ON/OFF flag remains in the
 * locomotive's Forge persistent entity data and is safely reactivated after
 * that locomotive is loaded again.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChunkLoaderSetup {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                ForgeChunkManager.setForcedChunkLoadingCallback(
                        Traincraft.MOD_ID,
                        (level, ticketHelper) -> {
                            for (UUID owner :
                                    new ArrayList<>(ticketHelper.getEntityTickets().keySet())) {
                                ticketHelper.removeAllTickets(owner);
                            }
                        }));
    }

    private ChunkLoaderSetup() {
    }
}
