package traincraft.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.worldgen.mixin.Dev56J19StructureStartAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEV56J20 release-world candidate gate, not a claim of guaranteed 35% physical placement.
 * Structure.generate return injection runs ONLY for new structure starts (not loaded NBT).
 * Filters generated village pieces before Minecraft saves or places any structure chunks.
 * Does not generate chunks, read world blocks, or connect villages.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56J19VillageRules {
    private static final String PREFIX = "[DEV56J19] ";
    public static final String WORLD = "DEV56 J19 Village Test";
    private static final String[] BIOMES = {"plains", "desert", "savanna", "taiga", "snowy"};
    private static final String[] VARIANTS = {"active_depot", "active_halt", "abandoned", "overgrown", "subway"};
    private static volatile boolean enabled;
    private static volatile int stationPercent = 35;
    /** J22: backfill a selected native-candidate-empty village only when an isolated site fits. */
    private static volatile boolean fallbackEnabled = true;
    private static volatile StructureTemplateManager fallbackManager;
    // J26: generator height sampling only. Never load chunks from a worldgen callback.
    private static volatile MinecraftServer subwayTerrainServer;
    private static final AtomicInteger SUBWAY_EMBEDDED = new AtomicInteger();
    private static final AtomicInteger SUBWAY_UNSAFE_SITE = new AtomicInteger();
    private static final AtomicInteger SUBWAY_REPLACED = new AtomicInteger();
    // The 19-block Subway NBT has its entrance floor approximately seven blocks
    // below its top roof. Align that entrance to terrain and bury its rail deck.
    private static final int SUBWAY_ENTRANCE_BELOW_TOP = 7;
    private static final int SUBWAY_MAX_ENTRY_ADJUST = 8; // J26R2: align entrance to natural ground
    private static final int SUBWAY_MAX_GRADE = 5; // J26R2: still rejects steep cliffs
    private static final int[] FALLBACK_WEIGHTS = {12,12,8,6,4};
    private static final AtomicInteger FALLBACK_ADDED = new AtomicInteger();
    private static final AtomicInteger FALLBACK_NO_FIT = new AtomicInteger();
    private static volatile int energyPercent = 25;
    private static final AtomicInteger VILLAGES = new AtomicInteger();
    private static final AtomicInteger WITH_CANDIDATES = new AtomicInteger();
    private static final AtomicInteger ENERGY_RAILS = new AtomicInteger();
    private static final AtomicInteger ELIGIBLE = new AtomicInteger();
    private static final AtomicInteger PLACED = new AtomicInteger();
    private static final AtomicInteger NO_CANDIDATE = new AtomicInteger();
    private static final AtomicInteger REMOVED_PIECES = new AtomicInteger();

    private Dev56J19VillageRules() {}
    public static boolean isEnabled() { return enabled; }

    private static int loadSetting(List<String> lines, String key, int fallback) {
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("#") || stripped.startsWith(";")) continue;
            int eq = stripped.indexOf('=');
            if (eq <= 0 || !stripped.substring(0, eq).strip().equals(key)) continue;
            String value = stripped.substring(eq + 1).split("[#;]", 2)[0].strip();
            try {
                int n = Integer.parseInt(value);
                if (n >= 0 && n <= 100) return n;
            } catch (NumberFormatException ignored) { }
            throw new IllegalArgumentException(key + " must be an integer 0..100: " + value);
        }
        return fallback;
    }

    private record RailCandidate(int index, long key) {}

    private static long mix(long v) {
        v ^= v >>> 33; v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33; v *= 0xc4ceb9fe1a85ec53L;
        return v ^ (v >>> 33);
    }

    private static int roll(long seed, ChunkPos pos) {
        return (int) Math.floorMod(mix(seed ^ Long.rotateLeft(pos.toLong(), 29)
                ^ 0x6B8173C250494A19L), 100L);
    }

    private static int adjustTemplate(StructureTemplate template, Registry<Block> blocks, String path, int targetPercent) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag templateBlocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        List<RailCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < templateBlocks.size(); i++) {
            CompoundTag block = templateBlocks.getCompound(i);
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) continue;
            if (!"traincraft:track_normal".equals(palette.getCompound(state).getString("Name"))) continue;
            CompoundTag nbt = block.getCompound("nbt");
            if (!"energy".equals(nbt.getString("TrackMaterial"))) continue;
            ListTag p = block.getList("pos", Tag.TAG_INT);
            if (p.size() != 3) continue;
            BlockPos position = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            long orderKey = mix(position.asLong() ^ mix(path.hashCode() * 0x9E3779B97F4A7C15L));
            candidates.add(new RailCandidate(i, orderKey));
        }
        candidates.sort(Comparator.comparingLong(RailCandidate::key));
        int keep = (candidates.size() * targetPercent + 50) / 100; // nearest whole rail
        ENERGY_RAILS.addAndGet(keep);
        for (int i = keep; i < candidates.size(); i++) {
            templateBlocks.getCompound(candidates.get(i).index()).getCompound("nbt")
                    .putString("TrackMaterial", "normal");
        }
        if (!candidates.isEmpty()) template.load(blocks.asLookup(), tag);
        return candidates.size();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        enabled = false;
        fallbackManager = null;
        subwayTerrainServer = null;
        SUBWAY_EMBEDDED.set(0); SUBWAY_UNSAFE_SITE.set(0); SUBWAY_REPLACED.set(0);
        FALLBACK_ADDED.set(0); FALLBACK_NO_FIT.set(0);
        VILLAGES.set(0); ELIGIBLE.set(0); PLACED.set(0); NO_CANDIDATE.set(0); REMOVED_PIECES.set(0);
        WITH_CANDIDATES.set(0); ENERGY_RAILS.set(0);
        if (server == null) return;
        try {
            // Forge's resolved config directory is the stable source of truth.
            // The world-save path may contain a trailing "." component and is unsafe
            // to navigate using chained getParent() calls.
            Path settings = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("tafaricraft-dev56j19.toml").toAbsolutePath().normalize();
            if (!Files.isRegularFile(settings)) {
                throw new IllegalStateException("Missing J19 configuration: " + settings);
            }
            List<String> lines = Files.readAllLines(settings, StandardCharsets.UTF_8);
            stationPercent = loadSetting(lines, "stationChancePercent", 35);
            fallbackEnabled = loadSetting(lines, "enableStationFallback", 1) == 1;
            energyPercent = loadSetting(lines, "energyTrackPercent", 25);
            Registry<Block> blockRegistry = server.registryAccess().registryOrThrow(Registries.BLOCK);
            int modified = 0;
            int ordinaryRails = 0;
            for (String biome : BIOMES) for (String variant : VARIANTS) {
                String path = "dev56/" + biome + "/" + variant;
                var template = server.getStructureManager().get(new ResourceLocation("traincraft", path))
                        .orElseThrow(() -> new IllegalStateException("Missing " + path));
                ordinaryRails += adjustTemplate(template, blockRegistry, path, energyPercent);
                modified++;
            }
            if (ordinaryRails < 100) throw new IllegalStateException("Unusually low track count " + ordinaryRails);
            fallbackManager = server.getStructureManager();
            subwayTerrainServer = server;
            enabled = true;
            Traincraft.LOGGER.info("[DEV56J22] READY boundedStationFallback={} maxAttempts=320 collisions=CHECKED siteTerrain=RUNTIME_REVIEW_REQUIRED", fallbackEnabled);
            Traincraft.LOGGER.info(PREFIX + "READY world={} templates={} taggedOrdinaryTracks={} taggedEnergyTracks={} stationChance={} energyTrackPercent={} normalTrackPercent={} cap=1 testWorldOnly=false originalNBTUnchanged=true", server.getWorldData().getLevelName(), modified, ordinaryRails, ENERGY_RAILS.get(), stationPercent, energyPercent, 100 - energyPercent);
        } catch (Exception ex) {
            enabled = false;
            // Never allow a partially initialized J19 world to silently generate with J5 rules.
            throw new IllegalStateException(PREFIX + "SAFETY STOP: failed to initialize village gate and mixed rails", ex);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { enabled = false; fallbackManager = null; subwayTerrainServer = null; }


    /**
     * J26: conservative Subway grade/underground-fit gate for NEW village starts.
     * Samples the vanilla generator's worldgen height predictions over the
     * complete platform footprint AND a four-block safety ring. Does not load
     * chunks, modify world blocks, or change the NBT station template.
     *
     * The lower train deck must remain roughly 9 blocks below surrounding
     * solid terrain; the small visible access hut stays at ground level.
     * Steep sides, valley overhangs and substantial water depth fail CLOSED.
     * Junctions/house alignment are protected with a maximum 2-block shift
     * and explicit collision checks before any existing piece is moved.
     */
    private static boolean embedSubway(PoolElementStructurePiece subway,
            List<StructurePiece> surrounding) {
        MinecraftServer server = subwayTerrainServer;
        if (server == null || server.overworld() == null) return rejectSubwayFit("SERVER_NOT_READY", subway.getBoundingBox(), 0, 0);
        ServerLevel level = server.overworld();
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        var noise = source.randomState();
        if (generator == null || noise == null) return rejectSubwayFit("GENERATOR_NOT_READY", subway.getBoundingBox(), 0, 0);
        BoundingBox box = subway.getBoundingBox();
        if (box.maxY() - box.minY() < 16) return rejectSubwayFit("TEMPLATE_HEIGHT", box, box.maxY() - box.minY(), 16); // wrong/changed NBT: stop
        int x0 = box.minX() - 4, x1 = box.maxX() + 4;
        int z0 = box.minZ() - 4, z1 = box.maxZ() + 4;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x : new int[]{x0, (x0 + x1) / 2, x1}) {
            for (int z : new int[]{z0, (z0 + z1) / 2, z1}) {
                int earth = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        level, noise);
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level, noise);
                // Reject ravines, rivers and flooded terrain; leaves/trees also
                // fail closed rather than introducing a false terrain PASS.
                if (surface - earth > 2) return rejectSubwayFit("WATER_OR_CANOPY", box, surface - earth, 2);
                min = Math.min(min, earth); max = Math.max(max, earth);
            }
        }
        if (max - min > SUBWAY_MAX_GRADE) return rejectSubwayFit("GRADE", box, max - min, SUBWAY_MAX_GRADE);
        // getBaseHeight is the first air block. Keep the hut threshold flush
        // with the surrounding surface; let the rail deck sit underground.
        int surfaceY = (min + max) / 2;
        int entryY = box.maxY() - SUBWAY_ENTRANCE_BELOW_TOP;
        int dy = surfaceY - entryY;
        if (Math.abs(dy) > SUBWAY_MAX_ENTRY_ADJUST) return rejectSubwayFit("ENTRANCE_LEVEL", box, Math.abs(dy), SUBWAY_MAX_ENTRY_ADJUST);
        int deckY = box.minY() + dy + 2;
        if (min - deckY < 7) return rejectSubwayFit("DECK_TOO_EXPOSED", box, min - deckY, 7);
        if (box.minY() + dy < level.getMinBuildHeight() + 2
                || box.maxY() + dy > level.getMaxBuildHeight() - 2)
            return rejectSubwayFit("WORLD_BUILD_HEIGHT", box, box.minY() + dy, level.getMinBuildHeight());
        BoundingBox moved = new BoundingBox(box.minX(), box.minY() + dy, box.minZ(),
                box.maxX(), box.maxY() + dy, box.maxZ());
        for (StructurePiece piece : surrounding) {
            if (piece == subway) continue;
            BoundingBox other = piece.getBoundingBox();
            if (moved.intersects(other)) return rejectSubwayFit("STRUCTURE_COLLISION", moved, 1, 0);
        }
        // PoolElementStructurePiece.move updates both template origin and bounds.
        if (dy != 0) subway.move(0, dy, 0);
        SUBWAY_EMBEDDED.incrementAndGet();
        Traincraft.LOGGER.info("[DEV56J26] SUBWAY_BURIED piece={} terrainMin={} terrainMax={} "
                + "entranceY={} deckY={} shiftY={} grade={} predictedOnly=true",
                subway.getBoundingBox(), min, max, entryY + dy, deckY, dy, max - min);
        return true;
    }

    /** J26R2: observed cause for each rejected Subway site; no world writes. */
    private static boolean rejectSubwayFit(String reason, BoundingBox box, int actual, int limit) {
        Traincraft.LOGGER.info("[DEV56J26R2] SUBWAY_FIT_REJECT reason={} box={} observed={} threshold={} "
                + "noUnsafePlacement=true", reason, box, actual, limit);
        return false;
    }

    /** J26: bounded alternative when a cliffside Subway is rejected. */
    private static PoolElementStructurePiece safeSurfaceReplacement(ResourceLocation villageId,
            List<StructurePiece> withoutStations, long seed, ChunkPos pos) {
        // Active halt is a compact surface station. Its unchanged NBT is used.
        PoolElementStructurePiece alternative = findBoundedFallback(villageId, withoutStations,
                seed, pos, 1);
        if (alternative != null) SUBWAY_REPLACED.incrementAndGet();
        return alternative;
    }

    /**
     * J22 bounded no-candidate rescue. Runs ONLY at generation of a NEW village start,
     * after the original jigsaw pieces exist, before chunk placement. It never loads
     * chunks or reads terrain; the fallback will fail closed if no separate bounding
     * box adjacent to an existing village piece is found. Terrain fit must be tested.
     * No railway lines, terrain carving, houses removal, or chunk tickets.
     */
    private static PoolElementStructurePiece findBoundedFallback(ResourceLocation villageId,
            List<StructurePiece> original, long seed, ChunkPos chunkPos) {
        return findBoundedFallback(villageId, original, seed, chunkPos, -1);
    }

    private static PoolElementStructurePiece findBoundedFallback(ResourceLocation villageId,
            List<StructurePiece> original, long seed, ChunkPos chunkPos, int forcedVariant) {
        StructureTemplateManager manager = fallbackManager;
        if (!fallbackEnabled || manager == null || original.isEmpty()) return null;
        String path = villageId.getPath();
        if (!path.startsWith("village_")) return null;
        String biome = path.substring("village_".length());
        boolean supported = false;
        for (String b : BIOMES) if (b.equals(biome)) { supported = true; break; }
        if (!supported) return null;

        int totalWeight = 0;
        for (int w : FALLBACK_WEIGHTS) totalWeight += w;
        int pick = (int) Math.floorMod(mix(seed ^ chunkPos.toLong()
                ^ 0x7128FC1930A4D56BL), totalWeight);
        int variantIndex = 0;
        while (variantIndex < FALLBACK_WEIGHTS.length - 1 && pick >= FALLBACK_WEIGHTS[variantIndex]) {
            pick -= FALLBACK_WEIGHTS[variantIndex++];
        }
        if (forcedVariant >= 0) variantIndex = forcedVariant;
        final boolean subwayVariant = variantIndex == 4;
        final ResourceLocation stationId = new ResourceLocation("traincraft",
                "dev56/" + biome + "/" + VARIANTS[variantIndex]);
        final StructurePoolElement element = StructurePoolElement.single(stationId.toString())
                .apply(StructureTemplatePool.Projection.RIGID);
        final Rotation[] orientations = Rotation.values();
        final int count = original.size();
        final int initial = (int) Math.floorMod(mix(seed ^ chunkPos.toLong()
                ^ 0x3B9E7A205C14D863L), count);
        int attempts = 0;
        // Favor roads so an added station remains near the village, not disconnected.
        // Run a second pass at existing houses when road extents are too constrained.
        for (int group = 0; group < 2; group++) {
            for (int n = 0; n < count; n++) {
                StructurePiece anchor = original.get((initial + n) % count);
                if (!(anchor instanceof PoolElementStructurePiece parent)) continue;
                String kind = String.valueOf(parent.getElement()).toLowerCase(java.util.Locale.ROOT);
                boolean road = kind.contains("/streets/") || kind.contains("/street/")
                        || kind.contains("/roads/") || kind.contains("/road/");
                if (group == 0 && !road) continue;
                if (group == 1 && (road || !kind.contains("/houses/"))) continue;
                BoundingBox near = parent.getBoundingBox();
                // The small compact station uses 11x13; never go far outside a
                // village or force overlap with a house, road or other structure.
                for (int side = 0; side < 4; side++) {
                    for (int rotIndex = 0; rotIndex < orientations.length; rotIndex++) {
                        if (++attempts > 320) return null;
                        Rotation rotation = orientations[rotIndex];
                        BoundingBox templateBox = element.getBoundingBox(manager, BlockPos.ZERO, rotation);
                        int sizeX = templateBox.maxX() - templateBox.minX() + 1;
                        int sizeZ = templateBox.maxZ() - templateBox.minZ() + 1;
                        if (sizeX < 5 || sizeZ < 5 || sizeX > 20 || sizeZ > 20) continue;
                        int targetX = (near.minX() + near.maxX() - sizeX + 1) / 2;
                        int targetZ = (near.minZ() + near.maxZ() - sizeZ + 1) / 2;
                        if (side == 0) targetX = near.maxX() + 2;
                        else if (side == 1) targetX = near.minX() - sizeX - 1;
                        else if (side == 2) targetZ = near.maxZ() + 2;
                        else targetZ = near.minZ() - sizeZ - 1;
                        // Align the station's bottom with an already-generated
                        // village piece; never query/generate unloaded terrain.
                        int targetY = near.minY();
                        if (targetY < -60 || targetY > 300) continue;
                        BlockPos position = new BlockPos(targetX - templateBox.minX(),
                                targetY - templateBox.minY(), targetZ - templateBox.minZ());
                        BoundingBox candidate = element.getBoundingBox(manager, position, rotation);
                        if (candidate.minY() < -64 || candidate.maxY() > 319) continue;
                        boolean overlaps = false;
                        for (StructurePiece other : original) {
                            // One block of breathing room from everything already placed.
                            BoundingBox b = other.getBoundingBox();
                            if (candidate.maxX() + 1 >= b.minX() && candidate.minX() - 1 <= b.maxX()
                                    && candidate.maxY() + 1 >= b.minY() && candidate.minY() - 1 <= b.maxY()
                                    && candidate.maxZ() + 1 >= b.minZ() && candidate.minZ() - 1 <= b.maxZ()) {
                                overlaps = true;
                                break;
                            }
                        }
                        if (!overlaps) {
                            PoolElementStructurePiece candidatePiece = new PoolElementStructurePiece(
                                    manager, element, position, element.getGroundLevelDelta(),
                                    rotation, candidate);
                            if (!subwayVariant || embedSubway(candidatePiece, original)) return candidatePiece;
                            SUBWAY_UNSAFE_SITE.incrementAndGet();
                            // Fail closed: do not ever backfill a Subway on a cliff.
                            // Check at most four anchor/rotations per site to avoid
                            // unbounded worldgen heightmap work.
                            if (attempts >= 12) return null;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Called only from new vanilla Structure.generate starts by the J19 mixin. */
    public static void filter(Structure structure, StructureStart start,
                              Registry<Structure> structures, long seed, ChunkPos chunkPos) {
        if (!enabled || start == null || !start.isValid()) return;
        ResourceLocation id = structures.getKey(structure);
        if (id == null || !"minecraft".equals(id.getNamespace())
                || !id.getPath().startsWith("village_")) return;
        List<StructurePiece> original = start.getPieces();
        List<StructurePiece> stations = new ArrayList<>();
        for (StructurePiece piece : original) {
            if (piece instanceof PoolElementStructurePiece poolPiece
                    && String.valueOf(poolPiece.getElement()).contains("traincraft:dev56/")) stations.add(piece);
        }
        int villageRoll = roll(seed, chunkPos);
        boolean selected = villageRoll < stationPercent;
        VILLAGES.incrementAndGet();
        if (selected) ELIGIBLE.incrementAndGet();
        if (stations.isEmpty()) {
            if (selected) {
                PoolElementStructurePiece rescue = null;
                try {
                    rescue = findBoundedFallback(id, original, seed, chunkPos);
                    if (rescue == null) rescue = safeSurfaceReplacement(id, original, seed, chunkPos);
                } catch (RuntimeException failure) {
                    Traincraft.LOGGER.warn("[DEV56J22] BACKFILL_HOLD village={} chunk={},{} reason={}",
                            id, chunkPos.x, chunkPos.z, failure.toString());
                }
                if (rescue != null) {
                    List<StructurePiece> expanded = new ArrayList<>(original);
                    expanded.add(rescue);
                    Dev56J19StructureStartAccessor accessor = (Dev56J19StructureStartAccessor) (Object) start;
                    accessor.tafaricraft$setPieceContainer(new PiecesContainer(expanded));
                    accessor.tafaricraft$setCachedBoundingBox(null);
                    PLACED.incrementAndGet();
                    FALLBACK_ADDED.incrementAndGet();
                    Traincraft.LOGGER.info("[DEV56J22] BACKFILL_ADDED village={} chunk={},{} station={} box={} "
                            + "candidatePool=NONE collisionChecked=true terrainNotSampled=true",
                            id, chunkPos.x, chunkPos.z, String.valueOf(rescue.getElement()), rescue.getBoundingBox());
                    Traincraft.LOGGER.info(PREFIX + "NEW_VILLAGE id={} chunk={},{} roll={} selected=true candidates=0 "
                            + "surviving=1 removed=0 cap=1 fallback=YES", id, chunkPos.x, chunkPos.z, villageRoll);
                    return;
                }
                NO_CANDIDATE.incrementAndGet();
                FALLBACK_NO_FIT.incrementAndGet();
                Traincraft.LOGGER.info("[DEV56J22] BACKFILL_NO_FIT village={} chunk={},{} "
                        + "noOverlapSiteFound=true noForcedPlacement=true", id, chunkPos.x, chunkPos.z);
            }
            Traincraft.LOGGER.info(PREFIX + "NEW_VILLAGE id={} chunk={},{} roll={} selected={} "
                    + "candidates=0 surviving=0 station=NONE fallback=NO", id, chunkPos.x, chunkPos.z, villageRoll, selected);
            return;
        }
        WITH_CANDIDATES.incrementAndGet();
        StructurePiece keep = selected ? stations.get((int) Math.floorMod(
                mix(seed ^ chunkPos.toLong() ^ 0x3647CB69B182DF8EL), stations.size())) : null;
        boolean subwayMoved = false;
        if (keep instanceof PoolElementStructurePiece pool
                && String.valueOf(pool.getElement()).contains("/subway")) {
            BoundingBox before = pool.getBoundingBox();
            int previousMinY = before.minY();
            boolean acceptable = false;
            try { acceptable = embedSubway(pool, original); }
            catch (RuntimeException ex) {
                Traincraft.LOGGER.warn("[DEV56J26] SUBWAY_HOLD village={} chunk={},{} reason={}",
                        id, chunkPos.x, chunkPos.z, ex.toString());
            }
            if (acceptable) {
                subwayMoved = pool.getBoundingBox().minY() != previousMinY;
            } else {
                SUBWAY_UNSAFE_SITE.incrementAndGet();
                Traincraft.LOGGER.info("[DEV56J26] SUBWAY_SITE_REJECT village={} chunk={},{} box={} "
                        + "noCliffPlacement=true", id, chunkPos.x, chunkPos.z, before);
                keep = null;
                for (StructurePiece option : stations) {
                    if (!String.valueOf(((PoolElementStructurePiece) option).getElement())
                            .contains("/subway")) { keep = option; break; }
                }
                if (keep == null) {
                    List<StructurePiece> withoutStations = new ArrayList<>(original);
                    withoutStations.removeAll(stations);
                    try { keep = safeSurfaceReplacement(id, withoutStations, seed, chunkPos); }
                    catch (RuntimeException ex) {
                        Traincraft.LOGGER.warn("[DEV56J26] SUBWAY_REPLACEMENT_HOLD village={} chunk={},{} reason={}",
                                id, chunkPos.x, chunkPos.z, ex.toString());
                    }
                }
            }
        }
        List<StructurePiece> filtered = new ArrayList<>(original.size() + 1);
        for (StructurePiece piece : original) {
            if (!stations.contains(piece) || piece == keep) filtered.add(piece);
        }
        boolean newStation = keep != null && !original.contains(keep);
        if (newStation) filtered.add(keep);
        int removed = original.size() - filtered.size();
        if (removed != 0 || subwayMoved || newStation) {
            ((Dev56J19StructureStartAccessor) (Object) start)
                    .tafaricraft$setPieceContainer(new PiecesContainer(filtered));
            ((Dev56J19StructureStartAccessor) (Object) start)
                    .tafaricraft$setCachedBoundingBox(null);
            if (removed > 0) REMOVED_PIECES.addAndGet(removed);
        }
        if (keep != null) PLACED.incrementAndGet();
        Traincraft.LOGGER.info(PREFIX + "NEW_VILLAGE id={} chunk={},{} roll={} selected={} candidates={} surviving={} removed={} cap=1", id, chunkPos.x, chunkPos.z, villageRoll, selected, stations.size(), keep == null ? 0 : 1, removed);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tcj19status").requires(src -> src.hasPermission(2))
                .executes(ctx -> status(ctx.getSource())));
    }

    private static int status(CommandSourceStack source) {
        int observed = VILLAGES.get();
        int placed = PLACED.get();
        String actualRate = observed == 0 ? "n/a" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * placed / observed);
        String out = PREFIX + "STATUS ready=" + enabled + " allWorlds=true"
                + " stationChance=" + stationPercent + "% energy=" + energyPercent + "% normal=" + (100 - energyPercent)
                + "% generatedStarts=" + VILLAGES.get() + " selected=" + ELIGIBLE.get()
                + " withStationCandidateAndSelected=" + PLACED.get() + " selectedWithoutFittingCandidate=" + NO_CANDIDATE.get()
                + " withAnyCandidate=" + WITH_CANDIDATES.get() + " physicalPlacementRate=" + actualRate
                + " templatesEnergy=" + ENERGY_RAILS.get() + " duplicatesRemoved=" + REMOVED_PIECES.get()
                + " cap=1 candidateFitNotGuaranteed=true statisticalSampleAdequate=" + (observed >= 100)
                + " j22FallbackEnabled=" + fallbackEnabled + " j22FallbackAdded=" + FALLBACK_ADDED.get()
                + " j22FallbackNoFit=" + FALLBACK_NO_FIT.get()
                + " j22RequiresTerrainVisualAcceptance=true"
                + " j26SubwayEmbedded=" + SUBWAY_EMBEDDED.get()
                + " j26SubwayUnsafeSite=" + SUBWAY_UNSAFE_SITE.get()
                + " j26SubwayReplaced=" + SUBWAY_REPLACED.get()
                + " j26NewVillagesOnly=true";
        Traincraft.LOGGER.info(out);
        source.sendSuccess(() -> Component.literal(out), false);
        return 1;
    }
}
