package traincraft.chunk;

// TC_BATCH8_R1C_CHUNK_LOADER_ACTIVATOR

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.registry.TCItems;

/**
 * Classic TrainCraft interaction:
 * right-click a locomotive with the Chunk Loader Activator to toggle it.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkLoaderEvents {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!held.is(TCItems.CHUNK_LOADER_ACTIVATOR.get())) {
            return;
        }

        Entity target = event.getTarget();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());

        // Holding the dedicated tool against TafariCraft rolling stock should
        // never accidentally mount/open the car when the target is not a loco.
        if (target instanceof AbstractMinecart
                && typeId != null
                && Traincraft.MOD_ID.equals(typeId.getNamespace())
                && !ChunkLoaderManager.isLocomotive(target)) {
            if (!event.getLevel().isClientSide) {
                event.getEntity().displayClientMessage(
                        Component.literal("Chunk Loader Activator only works on locomotives."),
                        true);
            }
            event.setCancellationResult(
                    InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
            return;
        }

        if (!(target instanceof AbstractMinecart locomotive)
                || !ChunkLoaderManager.isLocomotive(target)) {
            return;
        }

        if (!event.getLevel().isClientSide) {
            boolean enable = !ChunkLoaderManager.isEnabled(locomotive);
            ChunkLoaderManager.setEnabled(locomotive, enable);

            event.getEntity().displayClientMessage(
                    Component.literal(enable ? "Chunk Loading: ON" : "Chunk Loading: OFF"),
                    true);

            // Classic tool durability: 10 uses. Creative mode does not consume it.
            if (!event.getEntity().getAbilities().instabuild) {
                held.hurtAndBreak(
                        1,
                        event.getEntity(),
                        player -> player.broadcastBreakEvent(event.getHand()));
            }
        }

        event.setCancellationResult(
                InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof AbstractMinecart locomotive)
                || !ChunkLoaderManager.isLocomotive(locomotive)
                || !ChunkLoaderManager.isEnabled(locomotive)) {
            return;
        }

        // Do not force chunks here. Forge warns that EntityJoinLevelEvent may
        // happen before the joining chunk reaches FULL status.
        ChunkLoaderManager.queuePersistedRestore(locomotive);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof AbstractMinecart locomotive)
                || !ChunkLoaderManager.isLocomotive(locomotive)) {
            return;
        }
        ChunkLoaderManager.onEntityLeave(locomotive);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ChunkLoaderManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ChunkLoaderManager.cleanupServer(event.getServer());
    }

    private ChunkLoaderEvents() {
    }
}
