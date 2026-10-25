package traincraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEV56J21P3: guarded compact-rail parked locomotive placement at J20-generated village stations.
 * Chunk.Load only reads structure starts; all level/block/entity access occurs
 * later on server tick, after checking already-loaded FULL chunks with getChunkNow.
 * No additional chunk tickets, no block placement, no inter-village tracks.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56J21ParkedFleet {
    private static final String PREFIX = "[DEV56J21P3] ";
    private static final String NBT_KEY = "TafariCraftJ21ParkedStation";
    private static final String DATA_KEY = "tafaricraft_dev56j21_parked_v1";
    private static final List<String> VARIANTS = List.of("active_depot", "active_halt", "abandoned", "overgrown", "subway");
    private static final List<String> LOCOMOTIVES = List.of("locomotive_steam_small", "locomotive_steam_br80", "locomotive_steam_pannier", "locomotive_steam_j50");
    private static final Map<String, Site> PENDING = new ConcurrentHashMap<>();
    private static volatile boolean enabled = false;
    private static volatile int[] rates = {45, 25, 15, 10, 20};
    private static final AtomicInteger examined = new AtomicInteger();
    private static final AtomicInteger skipped = new AtomicInteger();
    private static final AtomicInteger spawned = new AtomicInteger();
    private static final AtomicInteger noTrack = new AtomicInteger();
    private static final AtomicInteger occupied = new AtomicInteger();

    private Dev56J21ParkedFleet() {}

    private static final class Site {
        final String key;
        final String variant;
        final BoundingBox bounds;
        long nextTick;
        int attempts;
        Site(String key, String variant, BoundingBox bounds) {
            this.key = key;
            this.variant = variant;
            this.bounds = bounds;
        }
        BlockPos center() { return new BlockPos((bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2, (bounds.minZ() + bounds.maxZ()) / 2); }
    }

    private static long mix(long v) {
        v ^= v >>> 33; v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33; v *= 0xc4ceb9fe1a85ec53L;
        return v ^ (v >>> 33);
    }

    private static int roll(ServerLevel level, Site site) {
        long salt = site.key.hashCode() * 0x9E3779B97F4A7C15L;
        return (int) Math.floorMod(mix(level.getSeed() ^ site.center().asLong() ^ salt), 100L);
    }

    private static int parseRate(List<String> lines, String key, int fallback) {
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("#") || stripped.startsWith(";")) continue;
            int pos = stripped.indexOf('=');
            if (pos < 0 || !key.equals(stripped.substring(0, pos).strip())) continue;
            String value = stripped.substring(pos + 1).split("[#;]", 2)[0].strip();
            int rate = Integer.parseInt(value);
            if (rate < 0 || rate > 100) throw new IllegalArgumentException(key + " must be 0..100");
            return rate;
        }
        return fallback;
    }

    @SubscribeEvent
    public static void onStart(ServerAboutToStartEvent event) {
        enabled = false;
        PENDING.clear();
        examined.set(0); skipped.set(0); spawned.set(0); noTrack.set(0); occupied.set(0);
        try {
            Path config = FMLPaths.CONFIGDIR.get().resolve("tafaricraft-dev56j21.toml").toAbsolutePath().normalize();
            if (!Files.isRegularFile(config)) {
                Traincraft.LOGGER.error(PREFIX + "PARKED_FLEET_DISABLED missing config={} (existing J20 worldgen remains intact)", config);
                return;
            }
            List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
            int[] next = new int[VARIANTS.size()];
            for (int i = 0; i < next.length; i++) next[i] = parseRate(lines, VARIANTS.get(i), rates[i]);
            rates = next;
            enabled = true;
            Traincraft.LOGGER.info(PREFIX + "READY variants={} spawnChances={} persist=SavedData loadedChunkOnly=true worldgen=J20_UNCHANGED subway=true railFit=FIVE_THEN_THREE",
                    VARIANTS, java.util.Arrays.toString(next));
        } catch (Exception ex) {
            Traincraft.LOGGER.error(PREFIX + "PARKED_FLEET_DISABLED invalid config, J20 worldgen unchanged", ex);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { enabled = false; PENDING.clear(); }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) PENDING.clear();
    }

    /** Do not load chunks or query world blocks/entities in this callback. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!enabled || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        ChunkAccess chunk = event.getChunk();
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start == null || !start.isValid()) continue;
            for (StructurePiece piece : start.getPieces()) {
                if (!(piece instanceof PoolElementStructurePiece pool)) continue;
                String id = String.valueOf(pool.getElement()).toLowerCase(Locale.ROOT);
                if (!id.contains("traincraft:dev56/")) continue;
                String variant = null;
                for (String name : VARIANTS) {
                    if (id.contains("/" + name)) { variant = name; break; }
                }
                if (variant == null) continue;
                BoundingBox box = piece.getBoundingBox();
                String key = variant + ":" + box.minX() + "," + box.minY() + "," + box.minZ()
                        + ":" + box.maxX() + "," + box.maxY() + "," + box.maxZ();
                PENDING.putIfAbsent(key, new Site(key, variant, box));
            }
        }
    }

    private static final class Processed extends SavedData {
        final Set<String> done = new HashSet<>();
        static Processed load(CompoundTag tag) {
            Processed data = new Processed();
            ListTag list = tag.getList("done", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.done.add(list.getString(i));
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String key : done) list.add(StringTag.valueOf(key));
            tag.put("done", list);
            return tag;
        }
        void mark(String key) { if (done.add(key)) setDirty(); }
    }

    private static boolean fullyLoaded(ServerLevel level, BoundingBox box) {
        int minX = (box.minX() - 3) >> 4, maxX = (box.maxX() + 3) >> 4;
        int minZ = (box.minZ() - 3) >> 4, maxZ = (box.maxZ() + 3) >> 4;
        if ((maxX - minX + 1) * (maxZ - minZ + 1) > 64) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        return true;
    }

    private static boolean isStraightRail(ServerLevel level, BlockPos pos, boolean alongX) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null || !"traincraft".equals(key.getNamespace())
                || !("track_normal".equals(key.getPath()) || "track_energy".equals(key.getPath()))) return false;
        for (var property : state.getProperties()) {
            if (!"shape".equals(property.getName())) continue;
            String shape = String.valueOf(state.getValue(property));
            return alongX ? "east_west".equals(shape) : "north_south".equals(shape);
        }
        return false;
    }

    private static boolean clearAbove(ServerLevel level, BlockPos pos, int headroom) {
        for (int dy = 1; dy <= headroom; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) return false;
        }
        return true;
    }

    /** 5 cells use the original full fleet; 3 cells restrict the locomotive roster. */
    private record Parking(BlockPos rail, boolean xAxis, int cells, int headroom) {}

    /** Exact placement check; does not read outside an already-loaded station piece. */
    private static boolean fits(ServerLevel level, BoundingBox b, BlockPos center,
                                boolean alongX, int cells, int headroom) {
        int radius = cells / 2;
        for (int i = -radius; i <= radius; i++) {
            BlockPos rail = center.offset(alongX ? i : 0, 0, alongX ? 0 : i);
            if (rail.getX() < b.minX() || rail.getX() > b.maxX()
                    || rail.getZ() < b.minZ() || rail.getZ() > b.maxZ()
                    || !isStraightRail(level, rail, alongX)
                    || !clearAbove(level, rail, headroom)) return false;
        }
        return true;
    }

    private static List<Parking> findFits(ServerLevel level, Site site, int cells, int headroom) {
        BoundingBox b = site.bounds;
        List<Parking> options = new ArrayList<>();
        if (b.maxX() - b.minX() > 56 || b.maxZ() - b.minZ() > 56
                || b.maxY() - b.minY() > 24) return options;
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
                BlockPos center = new BlockPos(x, y, z);
                for (boolean alongX : new boolean[]{true, false}) {
                    if (fits(level, b, center, alongX, cells, headroom)) {
                        options.add(new Parking(center, alongX, cells, headroom));
                    }
                }
            }
        }
        return options;
    }

    /** Searches only an already-loaded station. Prefer original 5+3, then compact 3+3, then short 3+2. */
    private static Parking chooseRail(ServerLevel level, Site site) {
        for (int[] profile : new int[][]{{5, 3}, {3, 3}, {3, 2}}) {
            List<Parking> options = findFits(level, site, profile[0], profile[1]);
            if (options.isEmpty()) continue;
            long selector = mix(level.getSeed() ^ site.center().asLong() ^ 0x59F3A8E61BD04A2CL);
            return options.get((int) Math.floorMod(selector, options.size()));
        }
        return null;
    }

    /** Read-only reasons for rejected stations, without chunk loads or block changes. */
    private static String railProbe(ServerLevel level, Site site) {
        if (!fullyLoaded(level, site.bounds)) return "chunksNotFullyLoaded=true";
        BoundingBox b = site.bounds;
        int straight = 0;
        int clearThree = 0;
        int clearTwo = 0;
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isStraightRail(level, pos, true) && !isStraightRail(level, pos, false)) continue;
                straight++;
                if (clearAbove(level, pos, 2)) clearTwo++;
                if (clearAbove(level, pos, 3)) clearThree++;
            }
        }
        return "straightRailCells=" + straight + " clear2=" + clearTwo + " clear3=" + clearThree
                + " fits5x3=" + findFits(level, site, 5, 3).size()
                + " fits3x3=" + findFits(level, site, 3, 3).size()
                + " fits3x2=" + findFits(level, site, 3, 2).size();
    }

    private static boolean collision(ServerLevel level, Parking p) {
        BlockPos pos = p.rail;
        AABB check = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 4, pos.getZ() + 2);
        return !level.getEntities((Entity) null, check).isEmpty();
    }

    private static boolean parkedEntityExists(ServerLevel level, Site site) {
        BoundingBox b = site.bounds;
        AABB area = new AABB(b.minX()-2, b.minY()-2, b.minZ()-2,
                b.maxX()+3, b.maxY()+5, b.maxZ()+3);
        for (Entity e : level.getEntities((Entity) null, area)) {
            if (site.key.equals(e.getPersistentData().getString(NBT_KEY))) return true;
        }
        return false;
    }

    private static boolean trySpawn(ServerLevel level, Site site, Parking rail) {
        int n = (int) Math.floorMod(mix(level.getSeed() ^ site.center().asLong()
                ^ 0x3366AABBFEE12211L), LOCOMOTIVES.size());
        // Compact station platforms cannot safely host the larger J50/Pannier models.
        List<String> eligible = rail.cells() == 5 ? LOCOMOTIVES
                : rail.headroom() == 3 ? List.of("locomotive_steam_small", "locomotive_steam_br80")
                : List.of("locomotive_steam_small");
        n = Math.floorMod(n, eligible.size());
        for (int offset = 0; offset < eligible.size(); offset++) {
            String name = eligible.get((n + offset) % eligible.size());
            ResourceLocation key = new ResourceLocation("traincraft", name);
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
            if (type == null) continue;
            Entity entity = type.create(level);
            if (entity == null) continue;
            BlockPos p = rail.rail;
            entity.moveTo(p.getX() + 0.5, p.getY() + 0.25, p.getZ() + 0.5, rail.xAxis ? 90.0F : 0.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.getPersistentData().putString(NBT_KEY, site.key);
            if (level.addFreshEntity(entity)) {
                Traincraft.LOGGER.info(PREFIX + "PARKED_SPAWN variant={} type={} rail={} xAxis={} cells={} headroom={} key={}",
                        site.variant, key, p, rail.xAxis, rail.cells(), rail.headroom(), site.key);
                return true;
            }
            return false;
        }
        Traincraft.LOGGER.error(PREFIX + "PARKED_HOLD no registered locomotive EntityType from {}", LOCOMOTIVES);
        return false;
    }

    /** All world reads and entity writes are delayed until server tick. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!enabled || event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || PENDING.isEmpty()) return;
        int processedThisTick = 0;
        for (Iterator<Map.Entry<String,Site>> it = PENDING.entrySet().iterator(); it.hasNext() && processedThisTick < 2;) {
            Map.Entry<String,Site> entry = it.next();
            Site site = entry.getValue();
            if (level.getGameTime() < site.nextTick) continue;
            site.nextTick = level.getGameTime() + 40;
            if (!fullyLoaded(level, site.bounds)) continue;
            BlockPos center = site.center();
            if (level.players().stream().noneMatch(p -> p.distanceToSqr(center.getX()+0.5,
                    center.getY()+0.5, center.getZ()+0.5) < 128.0 * 128.0)) continue;
            processedThisTick++;
            Processed data = level.getDataStorage().computeIfAbsent(Processed::load, Processed::new, DATA_KEY);
            if (data.done.contains(site.key)) { PENDING.remove(site.key); continue; }
            int rate = rates[VARIANTS.indexOf(site.variant)];
            int value = roll(level, site);
            examined.incrementAndGet();
            if (value >= rate) {
                data.mark(site.key);
                skipped.incrementAndGet();
                Traincraft.LOGGER.info(PREFIX + "PARKED_ROLL variant={} roll={} chance={} decision=NO_TRAIN key={}",
                        site.variant, value, rate, site.key);
                PENDING.remove(site.key);
                continue;
            }
            if (parkedEntityExists(level, site)) {
                data.mark(site.key);
                Traincraft.LOGGER.info(PREFIX + "PARKED_ALREADY_PRESENT variant={} key={}", site.variant, site.key);
                PENDING.remove(site.key);
                continue;
            }
            Parking rail = chooseRail(level, site);
            if (rail == null) {
                if (++site.attempts == 1) {
                    noTrack.incrementAndGet();
                    Traincraft.LOGGER.warn(PREFIX + "PARKED_HOLD no safe five/three-cell straight rail variant={} key={} probe=[{}]",
                            site.variant, site.key, railProbe(level, site));
                }
                continue;
            }
            if (collision(level, rail)) {
                if (++site.attempts == 1) occupied.incrementAndGet();
                continue;
            }
            if (trySpawn(level, site, rail)) {
                data.mark(site.key); spawned.incrementAndGet(); PENDING.remove(site.key);
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tcj21status")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    String summary = PREFIX + "STATUS enabled=" + enabled + " subway=true variants=" + VARIANTS
                            + " chances=" + java.util.Arrays.toString(rates) + " considered=" + examined.get()
                            + " skippedByRoll=" + skipped.get() + " spawned=" + spawned.get()
                            + " waiting=" + PENDING.size() + " trackHolds=" + noTrack.get()
                            + " occupiedHolds=" + occupied.get() + " placementChanceFromJ20Unchanged=true";
                    Traincraft.LOGGER.info(summary);
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(summary), false);
                    return 1;
                }));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tcj21probe")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    if (!enabled) {
                        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                PREFIX + "PROBE disabled"), false);
                        return 0;
                    }
                    ServerLevel level = context.getSource().getLevel();
                    Vec3 sourcePos = context.getSource().getPosition();
                    Site nearest = null;
                    double bestDistance = 256.0 * 256.0;
                    for (Site site : PENDING.values()) {
                        BlockPos pos = site.center();
                        double dist = sourcePos.distanceToSqr(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5);
                        if (dist < bestDistance) { bestDistance = dist; nearest = site; }
                    }
                    final Site target = nearest;
                    String result = target == null ? PREFIX + "PROBE no pending station within 256 blocks"
                            : PREFIX + "PROBE variant=" + target.variant + " center=" + target.center()
                              + " attempts=" + target.attempts + " " + railProbe(level, target);
                    Traincraft.LOGGER.info(result);
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(result), false);
                    return 1;
                }));
    }
}
