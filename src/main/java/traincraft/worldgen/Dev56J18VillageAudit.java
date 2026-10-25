package traincraft.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEV56J18: READ-ONLY village structure-start audit for the *existing* J5 injector.
 * No village mutations, no registry/pool writes, no world generation or chunk tickets.
 * J5's 12x experimental weights do NOT satisfy 35%-per-village or one-station cap.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56J18VillageAudit {
    private static final String PREFIX = "[DEV56J18] ";
    private static final String[] BIOMES = {"plains", "desert", "savanna", "taiga", "snowy"};
    private static final String[] VARIANTS = {"active_depot", "active_halt", "abandoned", "overgrown", "subway"};
    private static final int MAX_OBSERVED = 2048;
    private static final int SCAN_RADIUS_CHUNKS = 12;
    private static final Map<String, Village> OBSERVED = new LinkedHashMap<>();
    private static boolean watch = true;
    private static boolean capacityWarning;

    private Dev56J18VillageAudit() {}

    private record Village(String key, String biome, int centerX, int centerZ,
                           int pieces, List<String> stations, int unknownPieces) {
        int count() { return stations.size(); }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tcworldgenaudit")
            .requires(s -> s.hasPermission(2))
            .executes(c -> scan(c.getSource()))
            .then(Commands.literal("scan").executes(c -> scan(c.getSource())))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("watch")
                .then(Commands.literal("on").executes(c -> toggle(c.getSource(), true)))
                .then(Commands.literal("off").executes(c -> toggle(c.getSource(), false))))
            .then(Commands.literal("reset").executes(c -> reset(c.getSource()))));
    }

    private static String label(StructureStart start, Registry<Structure> structures) {
        ResourceLocation id = structures.getKey(start.getStructure());
        if (id == null || !"minecraft".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (!path.startsWith("village_")) return null;
        return id.toString();
    }

    private static Village describe(StructureStart start, String structureId, String dimension) {
        BoundingBox box = start.getBoundingBox();
        String key = dimension + ":" + structureId + ":" + box.minX() + ":" + box.minZ();
        String biome = structureId.substring(structureId.lastIndexOf('_') + 1);
        List<String> stations = new ArrayList<>();
        int unknown = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) continue;
            String element = String.valueOf(poolPiece.getElement());
            if (!element.contains("traincraft:dev56/")) continue;
            boolean matched = false;
            for (String b : BIOMES) {
                for (String variant : VARIANTS) {
                    if (element.contains("traincraft:dev56/" + b + "/" + variant)) {
                        BoundingBox bb = poolPiece.getBoundingBox();
                        stations.add(b + "/" + variant + "@" + bb.getCenter().getX()
                            + "," + bb.getCenter().getY() + "," + bb.getCenter().getZ());
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) unknown++;
        }
        return new Village(key, biome, box.getCenter().getX(), box.getCenter().getZ(),
                start.getPieces().size(), Collections.unmodifiableList(stations), unknown);
    }

    private static void observe(StructureStart start, Registry<Structure> structures, String dimension) {
        if (start == null || !start.isValid()) return;
        String id = label(start, structures);
        if (id == null) return;
        Village v = describe(start, id, dimension);
        if (OBSERVED.containsKey(v.key())) return;
        if (OBSERVED.size() >= MAX_OBSERVED) {
            if (!capacityWarning) {
                capacityWarning = true;
                Traincraft.LOGGER.warn(PREFIX + "OBSERVATION_LIMIT reached={} (in-memory only)", MAX_OBSERVED);
            }
            return;
        }
        OBSERVED.put(v.key(), v);
        String classification = v.unknownPieces() > 0 ? "UNKNOWN_ELEMENT"
                : v.count() == 0 ? "NONE" : v.count() == 1 ? "ONE" : "MULTIPLE_FAIL";
        Traincraft.LOGGER.info(PREFIX + "VILLAGE kind={} center={},{} pieces={} stationPieces={} "
                + "classification={} stations={} unknownDev56={} observed={}",
                id, v.centerX(), v.centerZ(), v.pieces(), v.count(), classification,
                v.stations(), v.unknownPieces(), OBSERVED.size());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!watch || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!Dev56JigsawVillagePool.isTestWorld(level.getServer())) return;
        ChunkAccess chunk = event.getChunk();
        if (chunk == null || chunk.getAllStarts().isEmpty()) return;
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        String dimension = level.dimension().location().toString();
        for (StructureStart s : chunk.getAllStarts().values()) observe(s, structures, dimension);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OBSERVED.clear();
        capacityWarning = false;
        watch = true;
    }

    private static int scan(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!Dev56JigsawVillagePool.isTestWorld(source.getServer())) {
            source.sendFailure(Component.literal(PREFIX
                + "SAFETY HOLD: this audit only scans the exact disposable DEV56 test saves. No changes."));
            return 0;
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int loaded = 0;
        int before = OBSERVED.size();
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!level.hasChunk(x, z)) continue; // explicitly no forced chunk load
                loaded++;
                for (StructureStart s : level.getChunk(x, z).getAllStarts().values()) {
                    observe(s, registry, level.dimension().location().toString());
                }
            }
        }
        String msg = PREFIX + "SCAN loadedChunks=" + loaded + "/625 radiusChunks=12 newVillages="
                + (OBSERVED.size() - before) + " noChunkGeneration=true " + summary();
        Traincraft.LOGGER.info(msg);
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static String summary() {
        int total = OBSERVED.size(), stationVillages = 0, dupes = 0, unknown = 0;
        int[] biomeCounts = new int[BIOMES.length];
        int[] variantCounts = new int[VARIANTS.length];
        for (Village v : OBSERVED.values()) {
            if (v.count() > 0) stationVillages++;
            if (v.count() > 1) dupes++;
            unknown += v.unknownPieces();
            for (int i = 0; i < BIOMES.length; i++) if (BIOMES[i].equals(v.biome())) biomeCounts[i]++;
            for (String station : v.stations()) for (int j = 0; j < VARIANTS.length; j++) {
                if (station.contains("/" + VARIANTS[j] + "@")) variantCounts[j]++;
            }
        }
        double observed = total == 0 ? 0d : 100d * stationVillages / total;
        StringBuilder stats = new StringBuilder("villages=").append(total)
                .append(" withStation=").append(stationVillages)
                .append(" observedPercent=").append(Math.round(observed * 10d) / 10d)
                .append(" maxOneViolations=").append(dupes)
                .append(" unknownDev56=").append(unknown)
                .append(" biomes=");
        for (int i = 0; i < BIOMES.length; i++) stats.append(BIOMES[i]).append(':').append(biomeCounts[i]).append(',');
        stats.append(" variants=");
        for (int i = 0; i < VARIANTS.length; i++) stats.append(VARIANTS[i]).append(':').append(variantCounts[i]).append(',');
        stats.append(" TARGET=35pct_cap1 ACTUAL_ENFORCEMENT=" + (Dev56J19VillageRules.isEnabled() ? "YES_NEW_STARTS_ONLY" : "NO") + " J5_MULTIPLIER=12 sampleLoadedNotFreshOnly=true");
        return stats.toString();
    }

    private static int status(CommandSourceStack source) {
        String msg = PREFIX + "STATUS testWorld=" + Dev56JigsawVillagePool.isTestWorld(source.getServer())
                + " watch=" + watch + " " + summary();
        Traincraft.LOGGER.info(msg);
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int toggle(CommandSourceStack source, boolean enabled) {
        watch = enabled;
        String msg = PREFIX + "WATCH=" + (watch ? "ON" : "OFF") + " onDemandScanStillAvailable=true";
        Traincraft.LOGGER.info(msg);
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        OBSERVED.clear();
        capacityWarning = false;
        String msg = PREFIX + "RESET volatileAuditCountersOnly=true worldUnchanged=true";
        Traincraft.LOGGER.info(msg);
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
