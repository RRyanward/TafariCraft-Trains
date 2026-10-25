package traincraft.debug;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DEV58B3: non-spawning sunset for the former DEV55 Creative lighting viewer.
 * No preview creation, lighting tuning, renderer hooks or on-tick work.
 * Cleanup is limited to existing DEV55 review-marked preview entities in LOADED chunks.
 * This class is intentionally transitional: unloadable/offline saves cannot be swept here.
 */
@Mod.EventBusSubscriber(modid = "traincraft")
public final class Dev55FleetLightReviewCommand {
    private Dev55FleetLightReviewCommand() {}
    private static final String MARKER = "DEV55LIGHT_REVIEW";
    private static final String OWNER = "DEV55LIGHT_REVIEW_OWNER";
    private static final String NAME = "DEV55L3_TEST_LAMPS";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tclightreview")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(ctx -> inspect(ctx.getSource(), null, false)))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
            .then(Commands.literal("sweep").executes(ctx -> inspect(ctx.getSource(), null, true))));
    }

    private static boolean isPreview(Entity entity) {
        return entity.getPersistentData().getBoolean(MARKER)
            && entity.getPersistentData().hasUUID(OWNER)
            && entity.getCustomName() != null
            && NAME.equals(entity.getCustomName().getString());
    }

    private static int stop(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return inspect(source, player.getUUID(), true);
    }

    private static int inspect(CommandSourceStack source, UUID owner, boolean purge) {
        int found = 0;
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            // Collect before discarding; do not mutate the live entity iteration.
            List<Entity> pending = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!isPreview(entity)) continue;
                if (owner != null && !owner.equals(entity.getPersistentData().getUUID(OWNER))) continue;
                found++;
                if (purge) pending.add(entity);
            }
            for (Entity entity : pending) {
                if (entity.isAlive() && isPreview(entity)) {
                    entity.discard();
                    removed++;
                }
            }
        }
        final int matchCount = found;
        final int removedCount = removed;
        final String mode = purge ? "cleanup" : "status";
        source.sendSuccess(() -> Component.literal("[DEV58B3 " + mode + "] "
            + "loadedPreviewMarkers=" + matchCount + " removed=" + removedCount
            + "; only loaded dimensions/chunks inspected; other saves are not scanned."), false);
        return 1;
    }
}
