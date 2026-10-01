package traincraft.chunk;

// TC_BATCH8_R1C_CHUNK_LOADER_ACTIVATOR

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * Server-authoritative moving chunk tickets for the classic TrainCraft
 * Chunk Loader Activator.
 *
 * A 2-chunk radius (5x5 / 25 chunks) follows the locomotive.  The extra
 * radius intentionally covers a practical attached consist and gives the
 * moving locomotive room to cross chunk boundaries without unloading cars
 * behind it.
 *
 * Tickets are fully ticking because the locomotive must continue moving
 * with no nearby player.  Tickets are owned by the locomotive UUID, so
 * overlapping enabled locomotives do not remove each other's chunks.
 */
public final class ChunkLoaderManager {
    public static final String ENABLED_TAG = "TafariCraftChunkLoading";
    public static final int CHUNK_RADIUS = 2;
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private static final Map<UUID, ActiveState> ACTIVE = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> PENDING_RESTORE = new HashMap<>();

    public static boolean isLocomotive(Entity entity) {
        if (!(entity instanceof AbstractMinecart)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null
                && Traincraft.MOD_ID.equals(id.getNamespace())
                && id.getPath().startsWith("locomotive_");
    }

    public static boolean isEnabled(Entity entity) {
        return entity.getPersistentData().getBoolean(ENABLED_TAG);
    }

    public static void setEnabled(AbstractMinecart locomotive, boolean enabled) {
        locomotive.getPersistentData().putBoolean(ENABLED_TAG, enabled);
        if (!(locomotive.level() instanceof ServerLevel level)) {
            return;
        }

        UUID owner = locomotive.getUUID();
        PENDING_RESTORE.remove(owner);

        if (enabled) {
            ActiveState state = ACTIVE.computeIfAbsent(
                    owner,
                    ignored -> new ActiveState(level.dimension()));
            refresh(level.getServer(), level, locomotive, owner, state);
        } else {
            release(owner, level.getServer());
        }
    }

    /**
     * EntityJoinLevelEvent can fire before the joining chunk is FULL.  Forge
     * explicitly warns against loading chunks from that event, so persisted ON
     * state is restored on the following server tick instead.
     */
    public static void queuePersistedRestore(AbstractMinecart locomotive) {
        if (!isEnabled(locomotive) || !(locomotive.level() instanceof ServerLevel level)) {
            return;
        }
        PENDING_RESTORE.put(locomotive.getUUID(), level.dimension());
    }

    public static void onEntityLeave(AbstractMinecart locomotive) {
        UUID owner = locomotive.getUUID();
        PENDING_RESTORE.remove(owner);
        if (locomotive.level() instanceof ServerLevel level) {
            release(owner, level.getServer());
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        if (!PENDING_RESTORE.isEmpty()) {
            ArrayList<Map.Entry<UUID, ResourceKey<Level>>> pending =
                    new ArrayList<>(PENDING_RESTORE.entrySet());
            PENDING_RESTORE.clear();

            for (Map.Entry<UUID, ResourceKey<Level>> entry : pending) {
                ServerLevel level = server.getLevel(entry.getValue());
                if (level == null) {
                    continue;
                }
                Entity entity = level.getEntity(entry.getKey());
                if (entity instanceof AbstractMinecart locomotive
                        && isLocomotive(locomotive)
                        && isEnabled(locomotive)) {
                    ActiveState state = ACTIVE.computeIfAbsent(
                            entry.getKey(),
                            ignored -> new ActiveState(level.dimension()));
                    refresh(server, level, locomotive, entry.getKey(), state);
                }
            }
        }

        Iterator<Map.Entry<UUID, ActiveState>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveState> entry = iterator.next();
            UUID owner = entry.getKey();
            ActiveState state = entry.getValue();

            ServerLevel level = server.getLevel(state.dimension);
            Entity entity = level == null ? null : level.getEntity(owner);

            if (!(entity instanceof AbstractMinecart locomotive)
                    || !locomotive.isAlive()
                    || !isLocomotive(locomotive)
                    || !isEnabled(locomotive)) {
                releaseState(server, owner, state);
                iterator.remove();
                continue;
            }

            refresh(server, level, locomotive, owner, state);
        }
    }

    public static void cleanupServer(MinecraftServer server) {
        ArrayList<Map.Entry<UUID, ActiveState>> states =
                new ArrayList<>(ACTIVE.entrySet());
        ACTIVE.clear();
        PENDING_RESTORE.clear();

        for (Map.Entry<UUID, ActiveState> entry : states) {
            releaseState(server, entry.getKey(), entry.getValue());
        }
    }

    private static void release(UUID owner, MinecraftServer server) {
        ActiveState state = ACTIVE.remove(owner);
        if (state != null) {
            releaseState(server, owner, state);
        }
    }

    private static void refresh(
            MinecraftServer server,
            ServerLevel level,
            AbstractMinecart locomotive,
            UUID owner,
            ActiveState state) {

        if (!state.dimension.equals(level.dimension())) {
            releaseState(server, owner, state);
            state.dimension = level.dimension();
        }

        ChunkPos center = locomotive.chunkPosition();
        Set<Long> desired = new HashSet<>();

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                desired.add(ChunkPos.asLong(center.x + dx, center.z + dz));
            }
        }

        // Add the new edge first so a moving train never opens a loading gap.
        for (long packed : desired) {
            if (state.chunks.contains(packed)) {
                continue;
            }
            ChunkPos pos = new ChunkPos(packed);
            ForgeChunkManager.forceChunk(
                    level,
                    Traincraft.MOD_ID,
                    owner,
                    pos.x,
                    pos.z,
                    true,
                    true);
        }

        // Then release chunks that fell behind the moving 5x5 window.
        for (long packed : new HashSet<>(state.chunks)) {
            if (desired.contains(packed)) {
                continue;
            }
            ChunkPos pos = new ChunkPos(packed);
            ForgeChunkManager.forceChunk(
                    level,
                    Traincraft.MOD_ID,
                    owner,
                    pos.x,
                    pos.z,
                    false,
                    true);
        }

        state.chunks.clear();
        state.chunks.addAll(desired);
    }

    private static void releaseState(
            MinecraftServer server,
            UUID owner,
            ActiveState state) {

        ServerLevel level = server.getLevel(state.dimension);
        if (level != null) {
            for (long packed : new HashSet<>(state.chunks)) {
                ChunkPos pos = new ChunkPos(packed);
                ForgeChunkManager.forceChunk(
                        level,
                        Traincraft.MOD_ID,
                        owner,
                        pos.x,
                        pos.z,
                        false,
                        true);
            }
        }
        state.chunks.clear();
    }

    private static final class ActiveState {
        private ResourceKey<Level> dimension;
        private final Set<Long> chunks = new HashSet<>();

        private ActiveState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private ChunkLoaderManager() {
    }
}
