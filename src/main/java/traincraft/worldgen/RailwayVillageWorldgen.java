/*
 * TafariCraft – Trains DEV50b1
 * Small, conservative railway station pilot for freshly generated village chunks.
 * Traincraft-derived project attribution remains with the parent project.
 */
package traincraft.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.RailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.block.decor.TrainBufferBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import traincraft.Traincraft;
import traincraft.registry.TCBlocks;
import traincraft.villager.RailwayTrades;
import traincraft.villager.RailwayVillagerCare;
import traincraft.villager.RailwayVillagerEgg;
import traincraft.villager.RailwayVillagers;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppingEvent;
import traincraft.entity.train.tender.SteamTender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * DEV50b1 PILOT: a single modest station per village, never forcibly replacing
 * existing buildings. Work happens after chunk loading, on the server tick.
 * The new-chunk event never reads or writes neighboring chunks.
 */
public final class RailwayVillageWorldgen {
    private static final Logger LOG = Traincraft.LOGGER;
    private static final String LOG_PREFIX = "[TC-DEV50B1] ";
    private static final String SAVE_NAME = "tafaricraft_railway_village_stations_v1";
    // Group fresh chunks into 4x4 chunk regions. The previous 2048-entry FIFO
    // filled with duplicate candidates and only drained 8/100 ticks, so village
    // detection did not catch up with terrain generation. Fresh-only tickets now
    // remain eligible for delayed village POI registration and player approach.
    private static final ConcurrentHashMap<Long, FreshRegion> FRESH_REGIONS = new ConcurrentHashMap<>();
    private static final int MAX_REGIONS = 4096;
    private static final long FRESH_LIFETIME = 20L * 60L * 8L; // eight minutes
    private static final AtomicLong CHUNKS_NEW = new AtomicLong();
    private static final AtomicLong REGION_ADDED = new AtomicLong();
    private static final AtomicLong REGION_DROPPED = new AtomicLong();
    private static final AtomicLong CHECKED = new AtomicLong();
    private static final AtomicLong HOMES_MISSING = new AtomicLong();
    private static final AtomicLong NONVILLAGE_HOMES = new AtomicLong();
    private static final AtomicLong SITE_MISSING = new AtomicLong();
    private static final AtomicLong ALREADY_STATION = new AtomicLong();
    private static final AtomicLong BUILT_AUTOMATIC = new AtomicLong();
    // DEV50e2: an isolated, exact-save-name pilot. This is NOT the general
    // terrain-spanning railway generator. Never act in Recovery or arbitrary saves.
    // DEV50e3 uses a NEW isolated save. No retrofits or experimental auto tracks
    // are permitted in DEV50e2's old test save or in valuable user worlds.
    private static final String DEV50E2_TEST_WORLD = "DEV50E6 Terrain Profile Test";
    // DEV50e4 is still isolated to an exact disposable save. The accepted
    // DEV50e1 automatic station pilot in all other saves is unchanged.
    // This phase fixes the rear-facing junction then adds READ-ONLY surveying
    // for a future four-stop village loop (including possible tunnels).
    // Keep the first pilot's WIDE_V2 track geometry but MOVE ALL junctions
    // to the outbound side of the station: the old -27-cell western lead
    // could return almost to the village center despite a zero-error rail audit.
    private static final int DEV50E3B_OUTBOUND_SHIFT = 36;
    private static final double DEV50E3B_VILLAGE_BUFFER = 48.0D;
    private static final double DEV50E3B_CLEARANCE_EDGE = 2.65D;
    private static final int[][] DEV50E3B_SITES = {
            {64,0},{-64,0},{0,64},{0,-64},
            {80,0},{-80,0},{0,80},{0,-80},
            {64,32},{64,-32},{-64,32},{-64,-32},
            {96,0},{-96,0},{0,96},{0,-96}
    };
    private static final Map<Long, PendingAutoNetwork> DEV50E2_PENDING = new LinkedHashMap<>();
    private static final AtomicLong DEV50E2_BUILT = new AtomicLong();
    private static final AtomicLong DEV50E2_REJECTED = new AtomicLong();
    private static final int DEV50E2_MAX_PENDING = 24;
    private static final int DEV50E2_MAX_RETRIES = 20;
    private static final class PendingAutoNetwork {
        final BlockPos station;
        final BlockPos village;
        final boolean west;
        final long born;
        long nextCheck;
        int checks;
        PendingAutoNetwork(BlockPos station, BlockPos village,boolean west, long tick) {
            this.station = station;
            this.village = village;
            this.west = west;
            this.born = tick;
            this.nextCheck = tick + 80;
        }
    }
    private static volatile boolean debugOverride = false;
    private static volatile String lastDecision = "none";
    private static long ticks;
    // DEV50c6: ephemeral, manual-only micro-departures. ZERO persistence,
    // automatic schedules, extra train entities or chunk tickets.
    private static final Map<Long, ManualDeparture> MANUAL_DEPARTURES = new HashMap<>();
    // DEV51B R27: no schedule, no locomotive controller and no persistence.
    // R27 observes the already accepted 0.75-block manual GP7/Blue Coach shunt.
    private static R27Observation r27Observation=null;
    private static String r27LastReport="NOT_RUN";
    // DEV51B R28: observation ONLY, never schedules or drives a locomotive.
    private static R28Cycle r28Cycle=null;
    private static String r28LastReport="NOT_RUN";
    private record R28Cycle(BlockPos station,UUID locomotive,UUID coach,UUID operator,
                            double startX,double startZ,long tick) {}

    private record R27Observation(BlockPos station,UUID locomotive,UUID operator,
                                 double x,double z,long tick) {}
    private static final String R27_COPY_FOLDER="DEV51B R27 Movement Copy";
    private static final String R27_COPY_MARKER="R27-THIS-IS-A-DISPOSABLE-COPY.txt";
    private static final String R27_COPY_MARKER_VALUE="R27 DISPOSABLE COPY CONFIRMED";

    private static final double TEST_SHUNT_BPT = 0.030D; // 2.16 km/h
    private static final double TEST_SHUNT_MAX_BLOCKS = 0.75D;
    // DEV50c7: server-tick END observes position AFTER rail motion. Stop with
    // two normal movement ticks still available, rather than testing at 0.75
    // and potentially issuing one extra tick because of update order/rounding.
    // This is only the manual shunt's controller, not shared train physics.
    private static final double TEST_SHUNT_STOP_RESERVE = 2.0D * TEST_SHUNT_BPT + 0.000001D;
    private static final int TEST_SHUNT_MAX_TICKS = 38; // <=1.9 sec
    private record ManualDeparture(BlockPos station,UUID locomotive,UUID coach,
                                   UUID operator,double startX,long startTick) {}

    private static final class FreshRegion {
        final ChunkPos chunk;
        final long born;
        long nextCheck;
        int checks;
        FreshRegion(ChunkPos chunk, long tick) { this.chunk = chunk; this.born = tick; this.nextCheck = tick + 60; }
    }
    private record VillageProbe(Optional<BlockPos> center, int homes, int clustered, boolean natural) {}
    private record SiteSearch(Optional<Site> site, Map<String,Integer> rejections) {}
    private record Dev50e3Choice(Site site, boolean west) {}

private enum VillageStyle { PLAINS, DESERT, SAVANNA, TAIGA, SNOWY }
private record SurfacePalette(BlockState platformBase, BlockState canopyRoof, BlockState post,
                              BlockState officeBase, BlockState officeRoof, BlockState officeWall,
                              BlockState door) {}

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue AUTOMATIC;
    private static final ForgeConfigSpec.BooleanValue F1C_AUTO_STRAIGHT;
    // DEV50f1f: explicit opt-in after the OOME / falling-through-map report.
    // Default OFF protects the first recovery launch in the disposable world.
    private static final ForgeConfigSpec.BooleanValue F1F_SAFE_AUTOMATIC;
    // R37: new station variants for player-operated railway worldgen; new stations only.
    private static final ForgeConfigSpec.BooleanValue R37_SURFACE_VARIETY;
    // R38: new-station-only underground station, no auto rail routes or AI.
    private static final ForgeConfigSpec.BooleanValue R38_SUBWAY;
    // R39: true circular underground station, no generated routes or AI.
    private static final ForgeConfigSpec.BooleanValue R39_CIRCULAR_HUB;
    private static final ForgeConfigSpec.IntValue F1D_STATION_CHANCE;
    private static final ForgeConfigSpec.IntValue F1D_ABANDONED_CHANCE;
    private static final ForgeConfigSpec.IntValue ONE_PER_RADIUS;
    private static final ForgeConfigSpec.IntValue VILLAGE_RANGE;
    private static final ForgeConfigSpec.BooleanValue WORKERS;
    private static final ForgeConfigSpec.BooleanValue LOG_ATTEMPTS;
    // DEV50e6b: optional seed-stable selector. Disabled by default to
    // preserve legacy DEV50e1 automatic station placement in existing worlds.
    private static final ForgeConfigSpec.BooleanValue SEEDED_VARIETY_FRESH;
    // DEV50b2h: managed slots only for villagers created by railway worldgen;
    // manual spawn eggs are never registered or respawned implicitly.
    private static final ForgeConfigSpec.BooleanValue STAFF_RESPAWN;
    private static final ForgeConfigSpec.IntValue STAFF_RESPAWN_SECONDS;
    private static final String STAFF_STATION_TAG = "TC_RAILWAY_WORLDGEN_STATION";
    private static final String STAFF_SLOT_ROLE_TAG = "TC_RAILWAY_WORLDGEN_STAFF_ROLE";
    static {
        ForgeConfigSpec.Builder cfg = new ForgeConfigSpec.Builder();
        cfg.push("smallVillageStations");
        R37_SURFACE_VARIETY = cfg.comment("R37: choose a seed-stable regular/abandoned/overgrown/construction surface station for NEW village placements. No retrofits, AI, route construction or terrain cuts. Disable for classic regular-only stations.")
                .define("enablePlayerOperatedSurfaceVariety", true);
        R38_SUBWAY = cfg.comment("R38: attempt safe bounded underground subway stations at 10 percent of eligible NEW station sites; blocked underground sites fall back to surface stations. No retrofits, automatic routes, trains or chunk tickets.")
                .define("enablePlayerOperatedSubwayStations", true);
        R39_CIRCULAR_HUB = cfg.comment("R39: attempt a circular underground hub with two branching rooms at 5 percent of eligible NEW sites. Player operated, safe excavation only; blocked sites fall back to existing R38/subway or R37/surface choice.")
                .define("enablePlayerOperatedCircularUndergroundHub", true);
        AUTOMATIC = cfg.comment("Place a small station only when newly generated chunks load. Existing chunks are not modified automatically.")
                .define("enabled", true);
        ONE_PER_RADIUS = cfg.comment("Minimum spacing between generated stations in the same dimension (blocks).")
                .defineInRange("minimumStationSpacing", 160, 96, 1024);
        VILLAGE_RANGE = cfg.comment("Search distance from a fresh chunk to nearby vanilla village beds (blocks).")
                .defineInRange("homePoiSearchRadius", 64, 32, 96);
        WORKERS = cfg.comment("Spawn a Station Chief and Train Conductor at generated stations.")
                .define("spawnRailwayWorkers", true);
        LOG_ATTEMPTS = cfg.comment("Extra diagnostic station placement attempts in latest.log.")
                .define("debugGeneration", false);
        cfg.pop();
        // DEV50f1c: strict experimental opt-in scope, only the disposable
        // integrated test world; no changes to the existing general pilot.
        cfg.push("dev50f1cStraightPilot");
        F1C_AUTO_STRAIGHT = cfg.comment("Attempt native straight rail stops beside confirmed NEW villages ONLY in the exact DEV50E6 Terrain Profile Test save. Enabled for isolated testing; fail-closed in all other worlds and dedicated servers. Uses the accepted 49-cell construction transaction, never special stop rails or train entities.")
                .define("enabledDisposableWorldOnly", true);
        F1F_SAFE_AUTOMATIC = cfg.comment("DEV50f1f SAFETY: OFF by default after Java heap-space OOME. Enable ONLY after backing up the disposable test save and confirming healthy memory. Uses one bounded candidate per 40 ticks, no forced chunks, and a loaded-footprint/memory gate. Never works in other worlds.")
                .define("safeAutoConstructionOptIn", false);
        F1D_STATION_CHANCE = cfg.comment("Percentage (0..100) of new villages eligible for a small railway station. A stable world-seed/village-position roll is used once per village; not rerolled on reload. 0=none, 100=all eligible subject to safety checks.")
                .defineInRange("stationSpawnChancePercent", 35, 0, 100);
        F1D_ABANDONED_CHANCE = cfg.comment("Percentage (0..100) of selected railway stations that are abandoned. Abandoned stations have weathered buildings and NO railway staff/service. 20 means about 7 of every 100 eligible villages at the default 35% station chance.")
                .defineInRange("abandonedAmongStationsPercent", 20, 0, 100);
        cfg.pop();
        cfg.push("villageRailwayVariety");
        SEEDED_VARIETY_FRESH = cfg.comment("OPT-IN only: for NEW fresh villages, seed-select 35% unmodified, 35% active-surface stations, 15% abandoned reserved, 10% construction reserved, 5% subway reserved. Reserved types produce NO station/tracks until those systems are safely implemented. Older saves retain accepted generation while this is false. Never retrofits.")
                .define("applySeededSelectionToFreshStations", false);
        cfg.pop();
        cfg.push("generatedRailwayStaff");
        STAFF_RESPAWN = cfg.comment("Replace tracked world-generated station workers only AFTER a recorded death; never infer death from an unloaded or wandering entity.")
                .define("respawnAfterDeath", true);
        STAFF_RESPAWN_SECONDS = cfg.comment("Cooldown after the death of a tracked station worker, real in-game seconds (20 ticks each). Default five minutes.")
                .defineInRange("respawnCooldownSeconds", 300, 30, 3600);
        cfg.pop();
        SPEC = cfg.build();
    }

    // R36 RELEASE SCOPE: villagers and station platforms only; players drive trains.
    // Keep legacy experimental code for rollback, but deny its entry paths.
    private static final boolean PLAYER_OPERATED_STATIONS_ONLY = true;

    private RailwayVillageWorldgen() {}

    /** Exactly one explicit registration from Traincraft's constructor. */
    public static void initialize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "tafaricraft-railway-worldgen.toml");
        MinecraftForge.EVENT_BUS.register(RailwayVillageWorldgen.class);
        LOG.info(LOG_PREFIX + "registered fresh-chunk station pilot; config=tafaricraft-railway-worldgen.toml");
    }

    private record Site(BlockPos center, BlockPos villageReference) {}

    private static boolean logging() { return LOG_ATTEMPTS.get() || debugOverride; }
    private static void decision(String reason) {
        lastDecision = reason;
        if (logging()) LOG.info(LOG_PREFIX + "WORLDGEN_DIAG " + reason);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        CHUNKS_NEW.incrementAndGet();
        ChunkPos chunk = event.getChunk().getPos();
        long key = ChunkPos.asLong(chunk.x >> 2, chunk.z >> 2);
        if (FRESH_REGIONS.containsKey(key)) return;
        if (FRESH_REGIONS.size() >= MAX_REGIONS) { REGION_DROPPED.incrementAndGet(); return; }
        if (FRESH_REGIONS.putIfAbsent(key, new FreshRegion(chunk, ticks)) == null) REGION_ADDED.incrementAndGet();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Brake before world-save when a user quits mid-test. No later resume.
        ServerLevel level=event.getServer().getLevel(Level.OVERWORLD);
        if (level!=null) {
            RailwayTurnoutNativeRoutingProbe.stopForShutdown(level);
            RailwayTurnoutMovementTest.stopForShutdown(level);
            for (ManualDeparture session:new ArrayList<>(MANUAL_DEPARTURES.values()))
                haltManualDeparture(level,session,"server stopping");
        }
        // R30n: if the user exits during a manual trial, freeze ONLY the isolated test GP7.
        if(level!=null){
            if(r30nLive!=null)dev51bR30nFreeze(level,r30nLive.engine());
            if(r30nLock!=null)dev51bR30nFreeze(level,r30nLock);
        }
        r30nLive=null;r30nLock=null;r30nLast="FAILED_SESSION_CLOSED_DO_NOT_ASSUME_STOP";
        MANUAL_DEPARTURES.clear();
        r27Observation=null;
        r28Cycle=null; // no restart, no persistent dispatch session
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MANUAL_DEPARTURES.clear();
        r27Observation=null;
        r27LastReport="NOT_RUN";
        r30nLive=null;r30nLock=null;r30nLast="HOLD_NOT_ARMED";
        r28Cycle=null;
        r28LastReport="NOT_RUN";
        RailwayTurnoutNativeRoutingProbe.clearAfterServerStop();
        RailwayTurnoutMovementTest.clearAfterServerStop();
        FRESH_REGIONS.clear();
        f1Preview=null;
        f1LastPlaced=null;
        F1C_AUTO_NEXT.clear();
        F1C_AUTO_ATTEMPTS.clear();
        F1F_SEARCH_TASKS.clear();
        f1iFinder=null; // no search state survives leaving the disposable world
        f1pSurvey=null; // R14 advisory survey stays memory-only and session-only
        f1hPending=null;
        f1hPendingLevel=null;
        f1hBuilt=null;
        f1hBuiltLevel=null;
        f1kPending=null;
        f1kPendingLevel=null;
        f1kBuilt=null;
        f1kBuiltLevel=null;
        F1F_LAST_GLOBAL_SCAN = -100000L;
        f1cAutoStopped=false;
        DEV50E2_PENDING.clear();
        DEV50E2_BUILT.set(0);
        DEV50E2_REJECTED.set(0);
        ticks = 0;
        debugOverride = false;
        lastDecision = "none";
        for (AtomicLong counter : new AtomicLong[]{CHUNKS_NEW,REGION_ADDED,REGION_DROPPED,
                CHECKED,HOMES_MISSING,NONVILLAGE_HOMES,SITE_MISSING,ALREADY_STATION,BUILT_AUTOMATIC})
            counter.set(0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ++ticks;
        // Bounded departure oversight runs EVERY tick, before the existing
        // 40-tick worldgen cadence. Never alter the accepted worldgen cadence.
        if (!MANUAL_DEPARTURES.isEmpty()) {
            ServerLevel activeLevel=event.getServer().getLevel(Level.OVERWORLD);
            if (activeLevel!=null) tickManualDepartures(activeLevel);
            else MANUAL_DEPARTURES.clear();
        }
        // R27 observes results AFTER the frozen manual departure watchdog.
        if(r27Observation!=null) {
            ServerLevel r27Level=event.getServer().getLevel(Level.OVERWORLD);
            if(r27Level!=null)dev51bR27ObserveTick(r27Level);
            else {r27LastReport="FAILED: TEST_WORLD_UNLOADED";r27Observation=null;}
        }
        // R28 only observes the already supervised R27 lifecycle AFTER its stop.
        if(r28Cycle!=null) {
            ServerLevel cycleLevel=event.getServer().getLevel(Level.OVERWORLD);
            if(cycleLevel!=null)dev51bR28ObserveTick(cycleLevel);
            else {r28LastReport="FAILED: WORLD_UNLOADED";r28Cycle=null;}
        }

        // DEV50d1: isolated-copy-only turnout probe. Existing DEV50c8
        // departure guard above remains completely independent and untouched.
        if (RailwayTurnoutMovementTest.isActive()) {
            ServerLevel testLevel=event.getServer().getLevel(Level.OVERWORLD);
            if (testLevel!=null) RailwayTurnoutMovementTest.tick(testLevel);
            else RailwayTurnoutMovementTest.clearAfterServerStop();
        }
        // DEV50d2: independent copy-only native locomotive routing probe.
        // Never replaces or overrides accepted FC12 / DEV50c8 movement physics.
        if (RailwayTurnoutNativeRoutingProbe.isActive()) {
            ServerLevel probeLevel=event.getServer().getLevel(Level.OVERWORLD);
            if (probeLevel!=null) RailwayTurnoutNativeRoutingProbe.tick(probeLevel);
            else RailwayTurnoutNativeRoutingProbe.clearAfterServerStop();
        }
        // R30m: passive, 600-tick read-only native buffer recorder; never drives or brakes a train.
        if(r30mLive!=null) {
            ServerLevel west=event.getServer().getLevel(Level.OVERWORLD);
            if(west!=null)dev51bR30mRecordTick(west);
            else {r30mResult="FAILED_WORLD_UNLOADED_NO_ROUTE_AUTHORITY";r30mLive=null;}
        }
        // R30n: isolated manual movement watchdog; emergency clamp, NEVER automatically drives.
        if(r30nLive!=null||r30nLock!=null) {
            ServerLevel trialLevel=event.getServer().getLevel(Level.OVERWORLD);
            if(trialLevel!=null)dev51bR30nTick(trialLevel);
            else {r30nLast="FAILED_WORLD_UNLOADED";r30nLive=null;r30nLock=null;}
        }
        // DEV50d3: headless dedicated-server automated test is inactive unless
        // the root save folder and isolated-copy marker both match exactly.
        // It never runs in integrated/client worlds or the recovery save.
        if (!PLAYER_OPERATED_STATIONS_ONLY) RailwayHeadlessTurnoutTest.tick(event.getServer());
        if (ticks % 40 != 0) return;
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null || level.players().isEmpty()) return;
        // DEV50f1j: read-only operator search runs independently of global
        // generation OFF. One candidate per 40 ticks; never adds chunk tickets.
        if (f1iFinder!=null) f1iFinderTick(level);
        if (f1pSurvey!=null && !f1pSurvey.finished) f1pSurveyTick(level);
        // Staff replacement must not depend on worldgen's new-station toggle.
        if (ticks % 200 == 0 && WORKERS.get() && STAFF_RESPAWN.get())
            checkRecordedStaffRespawns(level);
        // Test-world-only deferred network placements. Runs on the server tick
        // after all chunk generation has finished; never loads missing chunks.
        if (PLAYER_OPERATED_STATIONS_ONLY && !DEV50E2_PENDING.isEmpty()) DEV50E2_PENDING.clear();
        else if (!DEV50E2_PENDING.isEmpty()) tickDev50e2Networks(level);
        if (!AUTOMATIC.get()) return;
        // DEV50f1f: never inspect village POIs or station sites in the
        // disposable world until the operator EXPLICITLY opts in. Do not
        // keep a growing backlog of fresh-region events while disabled.
        if (dev50e2WorldAllowed(level) && F1C_AUTO_STRAIGHT.get() && !F1F_SAFE_AUTOMATIC.get()) {
            FRESH_REGIONS.clear();
            return;
        }
        // Search only recently generated regions near a player; never force-load
        // distant chunks. A region stays pending until its village POIs are ready.
        int budget = 8;
        for (var entry : FRESH_REGIONS.entrySet()) {
            FreshRegion region = entry.getValue();
            if (ticks - region.born > FRESH_LIFETIME) { FRESH_REGIONS.remove(entry.getKey(), region); continue; }
            if (ticks < region.nextCheck) continue;
            // DEV50e1: proximity is horizontal, but POI searches use 3-D range.
            // Sample actual terrain Y ONLY AFTER proving this chunk is loaded;
            // the former constant Y=70 missed villages on low-elevation worlds.
            BlockPos regionMid = region.chunk.getMiddleBlockPosition(70);
            if (level.players().stream().noneMatch(p ->
                    (double)(p.blockPosition().getX()-regionMid.getX())*(p.blockPosition().getX()-regionMid.getX())
                    + (double)(p.blockPosition().getZ()-regionMid.getZ())*(p.blockPosition().getZ()-regionMid.getZ()) < 192.0*192.0)) continue;
            if (!level.hasChunk(region.chunk.x, region.chunk.z)) continue;
            BlockPos origin = new BlockPos(regionMid.getX(),
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            regionMid.getX(), regionMid.getZ()), regionMid.getZ());
            if (budget-- <= 0) break;
            region.checks++;
            region.nextCheck = ticks + 200; // allow POIs and structure starts to finish loading
            CHECKED.incrementAndGet();
            VillageProbe probe = inspectVillage(level, origin, VILLAGE_RANGE.get() + 32);
            if (probe.center().isEmpty()) {
                if (probe.homes() < 3) HOMES_MISSING.incrementAndGet();
                else NONVILLAGE_HOMES.incrementAndGet();
                if (logging()) decision("region=" + region.chunk + " CHECK=" + region.checks
                        + " no_village homes=" + probe.homes() + " cluster=" + probe.clustered()
                        + " natural=" + probe.natural());
                continue;
            }
            BlockPos village = probe.center().get();
            if (!PLAYER_OPERATED_STATIONS_ONLY && SEEDED_VARIETY_FRESH.get() && !(dev50e2WorldAllowed(level) && F1C_AUTO_STRAIGHT.get())) {
                Optional<BlockPos> naturalAnchor=dev50e6bVillageStartAnchor(level,village,128);
                if(naturalAnchor.isEmpty()) {
                    if(logging()) LOG.info("[TC-DEV50E6B] FRESH_POLICY village={} result=NO_VERIFIED_LOADED_STRUCTURE skip=true noForceLoad=true",village);
                    // Do not discard the region: it can be rechecked when nearby
                    // vanilla village structure-start data becomes available.
                    continue;
                }
                E6bStyle style=dev50e6bStyle(level,naturalAnchor.get());
                if(style!=E6bStyle.ACTIVE_SURFACE) {
                    LOG.info("[TC-DEV50E6B] FRESH_POLICY village={} stableAnchor={} type={} action=LEAVE_VILLAGE_UNTOUCHED noStation=true noWorkers=true noTracks=true noService=true",
                            village,naturalAnchor.get(),style);
                    FRESH_REGIONS.remove(entry.getKey(),region);
                    continue;
                }
                LOG.info("[TC-DEV50E6B] FRESH_POLICY village={} stableAnchor={} type={} action=ALLOW_EXISTING_STATION_PILOT afterSiteSafety=true",
                        village,naturalAnchor.get(),style);
            }
            // DEV50f1c fresh-only auto straight pilot. In the exact disposable
            // save, this REPLACES the old experimental station+wide-network
            // path for new villages; existing-world generation is unaffected.
            // The construction helper validates every source cell and rolls
            // back the preimage on any unexpected mutation. No chunk loads.
            if (dev50e2WorldAllowed(level) && F1C_AUTO_STRAIGHT.get()) {
                // Incremental site work (one candidate per global 40 ticks),
                // not the old 160-candidate synchronous search.
                region.nextCheck = ticks + 40;
                if(f1cAutoFreshVillage(level,village))
                    FRESH_REGIONS.remove(entry.getKey(),region);
                continue;
            }
            StationData data = stations(level);
            if (data.near(village, ONE_PER_RADIUS.get())) {
                ALREADY_STATION.incrementAndGet();
                FRESH_REGIONS.remove(entry.getKey(), region);
                continue;
            }
            boolean networkPilot = !PLAYER_OPERATED_STATIONS_ONLY && dev50e2WorldAllowed(level);
            Site site;
            boolean routeWest = false;
            if (networkPilot) {
                // DEV50e3: choose a safe station site AND its full future
                // network before placing any blocks. Existing saves retain
                // the already-accepted DEV50e1 station selection unchanged.
                Dev50e3Choice choice = dev50e3ChooseSite(level, village);
                if (choice == null) {
                    SITE_MISSING.incrementAndGet();
                    if (logging()) decision("DEV50E3_NO_CLEAR_ROUTE village="+village);
                    continue;
                }
                site = choice.site();
                routeWest = choice.west();
            } else {
                SiteSearch sites = inspectSites(level, village);
                if (sites.site().isEmpty()) {
                    SITE_MISSING.incrementAndGet();
                    if (logging()) decision("village=" + village + " NO_SITE " + sites.rejections());
                    continue;
                }
                site = sites.site().get();
            }
            if (!safeFootprint(level, site.center())) { decision("site_changed=" + site.center()); continue; }
            // R38: the underground rail center, not the street entrance, must be saved.
            BlockPos placedCenter=site.center();
            boolean r38Built=false;
            boolean r39HubBuilt=false;
            // R39: try a bounded circular hub BEFORE R38's existing subway choice.
            // Read-only refusal -> normal R38/R37 fallback. Never fall back after a
            // construction exception because rollback may require operator inspection.
            if (R39_CIRCULAR_HUB.get() && RailwayStationCircularHub.selected(level.getSeed(),site.center())) {
                String hubHold=RailwayStationCircularHub.problem(level,site.center());
                if(hubHold==null) {
                    try {
                        placedCenter=RailwayStationCircularHub.build(level,site.center());
                        r39HubBuilt=true;
                    } catch(RuntimeException hubError) {
                        LOG.error("[TC-DEV51B-R39] CIRCULAR_HUB_BUILD_HOLD surface={} noFallbackAfterWrite=true",site.center(),hubError);
                        FRESH_REGIONS.remove(entry.getKey(),region);
                        continue;
                    }
                } else {
                    LOG.info("[TC-DEV51B-R39] CIRCULAR_HUB_SITE_HOLD surface={} reason={} fallback=R38_OR_R37",site.center(),hubHold);
                }
            }
            if (!r39HubBuilt && R38_SUBWAY.get() && RailwayStationSubway.selected(level.getSeed(), site.center())) {
                String r38Hold=RailwayStationSubway.problem(level, site.center());
                if (r38Hold==null) {
                    try {
                        placedCenter=RailwayStationSubway.build(level, site.center());
                        r38Built=true;
                    } catch (RuntimeException r38Error) {
                        // Do not place an alternative after an exceptional write/rollback.
                        LOG.error("[TC-DEV51B-R38] SUBWAY_BUILD_HOLD station={} noFallbackAfterWrite=true",site.center(),r38Error);
                        FRESH_REGIONS.remove(entry.getKey(), region);
                        continue;
                    }
                } else {
                    // Read-only refusal; ordinary accepted R37 surface station follows.
                    LOG.info("[TC-DEV51B-R38] SUBWAY_SITE_HOLD center={} reason={} fallback=R37_SURFACE",site.center(),r38Hold);
                }
            }
            if (r39HubBuilt) {
                if (WORKERS.get()) {
                    makeWorker(level,placedCenter.offset(-2,0,6),"station_chief",placedCenter);
                    makeWorker(level,placedCenter.offset(2,0,6),"train_conductor",placedCenter);
                    makeWorker(level,placedCenter.offset(0,0,6),"railway_engineer",placedCenter);
                }
                LOG.info("[TC-DEV51B-R39] NEW_CIRCULAR_HUB surface={} railCenter={} rails=17 branchRooms=2 stairs=11 playerOperated=true noAutoDispatch=true noNetworkGeneration=true",
                    site.center(),placedCenter);
            } else if (r38Built) {
                if (WORKERS.get()) {
                    makeWorker(level,placedCenter.offset(-2,0,5),"station_chief",placedCenter);
                    makeWorker(level,placedCenter.offset(2,0,5),"train_conductor",placedCenter);
                    makeWorker(level,placedCenter.offset(0,0,5),"railway_engineer",placedCenter);
                }
                LOG.info("[TC-DEV51B-R38] NEW_SUBWAY surface={} railCenter={} rails=17 stairs=9 playerOperated=true noAutoDispatch=true noNetworkGeneration=true",
                        site.center(),placedCenter);
            } else {
                RailwayStationSurfaceVariants.Kind r37Kind = R37_SURFACE_VARIETY.get()
                        ? RailwayStationSurfaceVariants.choose(level.getSeed(), site.center())
                        : RailwayStationSurfaceVariants.Kind.REGULAR;
                VillageStyle r40kStyle = chooseVillageStyle(level, site.center());
                placeStation(level, site, RailwayStationSurfaceVariants.hasStaff(r37Kind), r40kStyle);
                RailwayStationSurfaceVariants.decorate(level, site.center(), r37Kind);
                // R40L: new surface structures only. No retrofit, no underground changes.
                RailwayStationVillageArchitecture.decorate(level, site.center(), r40kStyle.name(), r37Kind.name());
                if (r40j4TestWorld(level)) r40j4AddSupports(level,site.center());
                LOG.info("[TC-DEV51B-R40L] NEW_STATION kind={} style={} at={} playerOperated=true rails=17 noAutoDispatch=true noNetworkGeneration=true",
                        r37Kind, r40kStyle, site.center());
            }
            data.add(placedCenter, true);
            // R36: do not create an unused automated passenger-service roster for NEW stations.
            BUILT_AUTOMATIC.incrementAndGet();
            decision("AUTO_SUCCESS station=" + site.center() + " village=" + village);
            LOG.info(LOG_PREFIX + "AUTOMATIC station={} village={} dimension={}",
                    site.center(), village, level.dimension().location());
            // Reuse the existing accepted WIDE_V2 network plan, but only in the
            // specifically named disposable DEV50e2 world. No retrofits.
            if (networkPilot) enqueueDev50e2(site.center(), village,routeWest);
            FRESH_REGIONS.remove(entry.getKey(), region);
        }
    }

    // DEV50f1c: fail closed. Restored disposable saves may have a different
    // FOLDER from their preserved in-game world name. Require exact project
    // run/saves parent, exact in-game display name, integrated Overworld and
    // explicitly exclude Recovery. Do not inspect/open other saves.
    private static boolean dev50e2WorldAllowed(ServerLevel level) {
        if (PLAYER_OPERATED_STATIONS_ONLY) return false; // R36 disable disposable automatic network/straight worldgen
        if (!level.dimension().equals(Level.OVERWORLD)
                || level.getServer().isDedicatedServer()) return false;
        try {
            java.nio.file.Path actualRoot=level.getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            java.nio.file.Path safeSaves=java.nio.file.Paths.get(
                    "D:/TafariCraft - Reborn Development/Traincraft","run","saves")
                    .toAbsolutePath().normalize();
            String name=level.getServer().getWorldData().getLevelName();
            return DEV50E2_TEST_WORLD.equals(name)
                    && actualRoot.getParent()!=null
                    && actualRoot.getParent().equals(safeSaves)
                    && !actualRoot.toString().toLowerCase(java.util.Locale.ROOT).contains("recovery");
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void enqueueDev50e2(BlockPos station, BlockPos village,boolean west) {
        if (DEV50E2_PENDING.size() >= DEV50E2_MAX_PENDING) {
            LOG.warn("[TC-DEV50E2] AUTO_NETWORK_QUEUE_FULL station={}",station);
            return;
        }
        DEV50E2_PENDING.putIfAbsent(station.asLong(),new PendingAutoNetwork(station,village,west,ticks));
        LOG.info("[TC-DEV50E3] AUTO_NETWORK_QUEUED station={} axis={} dueTick={}",
                station, west ? "WEST" : "EAST",ticks+80);
    }

    // DEV50e3: 180-degree reversal is built using native switch/curve parts
    // with WEST-facing states (not by copying and mirroring the baked parts).
    // This preserves the accepted manual EAST WIDE_V2 and its command path.
    private static BlockPos dev50e3At(BlockPos c,int x,int y,int z,boolean west) {
        return c.offset(west ? -x : x,y,west ? -z : z);
    }
    private static NetworkPlan dev50e3PlanNetwork(BlockPos c,boolean west) {
        if (!west) return planNetwork(c);
        Map<BlockPos,BlockState> supports=new LinkedHashMap<>();
        Map<BlockPos,BlockState> rails=new LinkedHashMap<>();
        Map<BlockPos,BlockState> decoration=new LinkedHashMap<>();
        Set<BlockPos> protectedRails=new LinkedHashSet<>();
        BlockState normal=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        for(int x=-27;x<=42;x++) {
            BlockPos p=dev50e3At(c,x,0,0,true);
            if(x>=-8 && x<=8){protectedRails.add(p);continue;}
            if(!inTurnout(x)) addTrack(rails,p,normal);
        }
        dev50e3AddSwitch(rails,dev50e3At(c,-19,0,0,true),
                TCBlocks.SWITCH_PARALLEL_LEFT_SEGMENT.get(),LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT);
        dev50e3AddSwitch(rails,dev50e3At(c,10,0,0,true),
                TCBlocks.SWITCH_PARALLEL_RIGHT_SEGMENT.get(),LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT);
        dev50e3AddCurve(rails,dev50e3At(c,-10,0,-3,true),false);
        for(int x=2;x<=24;x++) addTrack(rails,dev50e3At(c,x,0,-5,true),normal);
        decoration.put(dev50e3At(c,25,0,-5,true),TCBlocks.STOPPER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING,Direction.EAST)
                .setValue(TrainBufferBlock.ENERGY,false));
        dev50e3AddCurve(rails,dev50e3At(c,19,0,3,true),true);
        for(int x=31;x<=36;x++)addTrack(rails,dev50e3At(c,x,0,5,true),normal);
        for(int x=37;x<=40;x++)supports.put(dev50e3At(c,x,-1,5,true),Blocks.GRAVEL.defaultBlockState());
        decoration.put(dev50e3At(c,37,0,9,true),Blocks.BARREL.defaultBlockState()
                .setValue(BarrelBlock.FACING,Direction.UP));
        decoration.put(dev50e3At(c,40,0,9,true),Blocks.YELLOW_CONCRETE.defaultBlockState());
        decoration.put(dev50e3At(c,40,1,9,true),Blocks.TORCH.defaultBlockState());
        for(BlockPos p:rails.keySet())supports.put(p.below(),Blocks.GRAVEL.defaultBlockState());
        for(BlockPos p:decoration.keySet())if(p.getY()==c.getY())
            supports.putIfAbsent(p.below(),Blocks.GRAVEL.defaultBlockState());
        return new NetworkPlan(supports,rails,decoration,protectedRails);
    }
    // DEV50e3b: Use UNCHANGED accepted EAST/WEST native turnout/curve states,
    // but move the whole junction complex 36 blocks OUTWARD from the station.
    // Original layout x=-27..+42 becomes station-relative +9..+78.
    // The former remote "station reservation" at +28..+44 is now NORMAL rail.
    // The actual station's accepted 17 cells (-8..+8) stay protected.
    private static NetworkPlan dev50e3bOutboundNetwork(BlockPos station,boolean west) {
        BlockPos junctionCenter=dev50e3At(station,DEV50E3B_OUTBOUND_SHIFT,0,0,west);
        NetworkPlan junction=dev50e3PlanNetwork(junctionCenter,west);
        Map<BlockPos,BlockState> supports=new LinkedHashMap<>(junction.supports());
        Map<BlockPos,BlockState> rails=new LinkedHashMap<>(junction.rails());
        Map<BlockPos,BlockState> decoration=new LinkedHashMap<>(junction.decoration());
        Set<BlockPos> stationRails=new LinkedHashSet<>();
        BlockState normal=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        // Fill the 17 cells previously reserved for a nonexistent remote
        // station. All original switch and curve parts stay intact.
        for(BlockPos p:junction.protectedStationRails()) {
            if(rails.containsKey(p)) throw new IllegalStateException("outbound_rail_overlap:"+p);
            addTrack(rails,p,normal);
            supports.putIfAbsent(p.below(),Blocks.GRAVEL.defaultBlockState());
        }
        for(int x=-8;x<=8;x++) {
            BlockPos p=dev50e3At(station,x,0,0,west);
            if(rails.containsKey(p) || decoration.containsKey(p))
                throw new IllegalStateException("outbound_station_overlap:"+p);
            stationRails.add(p);
        }
        // The first moved rail is at x=+9 and touches the exact preserved
        // station rail endpoint at x=+8. No disconnected gap or backtrack.
        BlockPos start=dev50e3At(station,9,0,0,west);
        if(!rails.containsKey(start)) throw new IllegalStateException("outbound_join_missing:"+start);
        if(stationRails.size()!=17) throw new IllegalStateException("outbound_station_count");
        return new NetworkPlan(supports,rails,decoration,stationRails);
    }

    // Physical village exclusion is different from the Minecraft village
    // structure-piece check: empty grass/roads in the village are NOT safe rail
    // corridors just because there is no house at that specific block.
    // Enforce a conservative buffer including a provisional 2.65-block swept
    // radius, for the WHOLE station-to-junction-and-siding rail footprint.
    private static String dev50e3bVillageBuffer(BlockPos village,NetworkPlan plan) {
        double limit=DEV50E3B_VILLAGE_BUFFER+DEV50E3B_CLEARANCE_EDGE;
        double maxDistSq=limit*limit;
        Set<BlockPos> allRails=new LinkedHashSet<>(plan.rails().keySet());
        allRails.addAll(plan.protectedStationRails());
        for(BlockPos p:allRails) {
            double dx=p.getX()-village.getX(),dz=p.getZ()-village.getZ();
            if(dx*dx+dz*dz<maxDistSq) return "village_buffer:"+p.toShortString();
        }
        return null;
    }

    private static void dev50e3AddSwitch(Map<BlockPos,BlockState> rails,BlockPos root,
                                          net.minecraft.world.level.block.Block block,LegacyBatchTrackSpec spec) {
        if(!(block instanceof LegacyBatchSwitchRailBlock sw) || sw.getSpec()!=spec)
            throw new IllegalStateException("WEST switch registry mismatch: "+spec.id());
        for(int i=0;i<spec.partCount();i++) addTrack(rails,
                spec.offsetForPart(root,Direction.WEST,i),sw.stateForPart(Direction.WEST,i,false));
    }
    private static void dev50e3AddCurve(Map<BlockPos,BlockState> rails,BlockPos root,boolean positive) {
        LegacyBatchTrackSpec spec=positive ? LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT
                :LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT;
        net.minecraft.world.level.block.Block block=positive
                ?TCBlocks.PARALLEL_CURVE_MEDIUM_RIGHT_SEGMENT.get()
                :TCBlocks.PARALLEL_CURVE_MEDIUM_LEFT_SEGMENT.get();
        if(!(block instanceof traincraft.block.track.LegacyBatchRailBlock piece) || piece.getSpec()!=spec)
            throw new IllegalStateException("WEST curve registry mismatch: "+spec.id());
        for(int i=0;i<spec.partCount();i++)addTrack(rails,
                spec.offsetForPart(root,Direction.WEST,i),piece.stateForPart(Direction.WEST,i,false));
    }


    // DEV50e4: READ-ONLY terrain survey for the real longer-term goal: a closed
    // railway loop OUTSIDE a naturally generated village, with four potential
    // station stops and terrain/tunnel alternatives.  Never edits/loads chunks,
    // modifies saves, or claims corners/tunnel segments are train-ready.
    // Native quarter-turn geometry, full clearance and tunnel excavation must
    // be built and runtime-validated BEFORE a future loop-place command exists.
    private record LoopScout(int radius,int railY,int cells,int unloaded,int villageHits,
                             int fluidHits,int obstacles,int grades,int possibleTunnel,
                             int cornerHazards,int clearCells,int normalCells,int energyCandidates) {}

    private static LoopScout dev50e4Scout(ServerLevel level,BlockPos village,int r) {
        int centerY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                village.getX(),village.getZ());
        int cells=0,unloaded=0,villageHits=0,fluidHits=0,obstacles=0,grades=0;
        int tunnels=0,cornerHazards=0,clear=0,normals=0,energy=0;
        Set<BlockPos> perimeter=new LinkedHashSet<>();
        for(int d=-r;d<=r;d++) {
            perimeter.add(new BlockPos(village.getX()+d,centerY,village.getZ()-r));
            perimeter.add(new BlockPos(village.getX()+d,centerY,village.getZ()+r));
            perimeter.add(new BlockPos(village.getX()-r,centerY,village.getZ()+d));
            perimeter.add(new BlockPos(village.getX()+r,centerY,village.getZ()+d));
        }
        for(BlockPos at:perimeter) {
            cells++;
            // All readings are from already-loaded chunks. Never trigger
            // neighbor chunk generation merely to draw a proposed loop.
            if(!level.hasChunkAt(at)){unloaded++;continue;}
            boolean corner=(Math.abs(at.getX()-village.getX())>=r-6
                    &&Math.abs(at.getZ()-village.getZ())>=r-6);
            int naturalY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    at.getX(),at.getZ());
            if(naturalY!=centerY) grades++;
            if(corner && naturalY!=centerY)cornerHazards++;
            boolean structure=false,liquid=false,blocked=false;
            // Screening envelope: ~2.65 side blocks in bends / turnout
            // approaches, five free vertical blocks.  This is not a swept
            // geometry certificate for any individual rolling-stock model.
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                if(dx*dx+dz*dz>16) continue;
                int x=at.getX()+dx,z=at.getZ()+dz;
                BlockPos probe=new BlockPos(x,centerY,z);
                if(!level.hasChunkAt(probe)) {blocked=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(probe,StructureTags.VILLAGE).isValid())
                    structure=true;
                for(int h=0;h<=4;h++) {
                    BlockPos p=probe.above(h);
                    BlockState bs=level.getBlockState(p);
                    if(!bs.getFluidState().isEmpty())liquid=true;
                    if(!bs.getCollisionShape(level,p).isEmpty())blocked=true;
                }
            }
            if(structure)villageHits++;
            if(liquid)fluidHits++;
            if(blocked)obstacles++;
            if(!structure && !liquid && naturalY>=centerY+3 && naturalY<=centerY+12)
                tunnels++; // TUNNEL CANDIDATE only; no excavation authorized.
            if(!structure && !liquid && !blocked && naturalY==centerY)clear++;
            // Deterministic, world-seed-like variation for the future layout.
            // Only labels: never interchange unproven Normal/Energy block
            // states, switches or curved segments in real trains.
            int salt=at.getX()*73428767 ^ at.getZ()*912367 ^ r*31;
            if((salt&7)<2)energy++; else normals++;
        }
        return new LoopScout(r,centerY,cells,unloaded,villageHits,fluidHits,
                obstacles,grades,tunnels,cornerHazards,clear,normals,energy);
    }

    private static int dev50e4LoopSurvey(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6 READ-ONLY legacy loop survey permitted only in NEW disposable save: "
                    +DEV50E2_TEST_WORLD+". NO CHANGES."));return 0;
        }
        BlockPos player=BlockPos.containing(src.getPosition());
        VillageProbe probe=inspectVillageForPilot(level,player,128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No verified village structure near player. "
                    +"Run /tcrailway diagnose. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        // The final loop needs four actual stops and engineered 90-degree
        // corners; none are created or persisted by this command.
        LoopScout best=null;
        for(int radius:new int[]{72,88,104}) {
            LoopScout trial=dev50e4Scout(level,village,radius);
            LOG.info("[TC-DEV50E4] LOOP_SURVEY village={} radius={} railY={} cells={} loaded={} village={} fluid={} obstructed={} uneven={} tunnelCandidates={} cornerHazards={} clear={} proposedNormal={} proposedEnergy={}",
                    village,radius,trial.railY(),trial.cells(),trial.cells()-trial.unloaded(),
                    trial.villageHits(),trial.fluidHits(),trial.obstacles(),trial.grades(),
                    trial.possibleTunnel(),trial.cornerHazards(),trial.clearCells(),
                    trial.normalCells(),trial.energyCandidates());
            if(best==null ||
                    (trial.unloaded()*100+trial.villageHits()*100+trial.fluidHits()*100
                        +trial.cornerHazards()*20+trial.obstacles()*5+trial.grades())
                    <(best.unloaded()*100+best.villageHits()*100+best.fluidHits()*100
                        +best.cornerHazards()*20+best.obstacles()*5+best.grades()))best=trial;
        }
        final LoopScout selected=best;
        if(selected==null)return 0;
        src.sendSuccess(()->Component.literal("[DEV50e4] READ-ONLY village loop SURVEY; village="
                +village.toShortString()+" candidate radius="+selected.radius()+" railY="+selected.railY()
                +" perimeter="+selected.cells()+" loaded="+(selected.cells()-selected.unloaded())),false);
        src.sendSuccess(()->Component.literal("[DEV50e4] Obstructions="+selected.obstacles()
                +" village="+selected.villageHits()+" water="+selected.fluidHits()
                +" gradeCells="+selected.grades()+" possibleTunnelCells="+selected.possibleTunnel()
                +" cornerHazards="+selected.cornerHazards()),false);
        src.sendSuccess(()->Component.literal("[DEV50e4] Four proposed stops: E/W/N/S;"
                +" deterministic track variety candidates Normal="+selected.normalCells()
                +" Energy="+selected.energyCandidates()
                +" (NOT PLACED). Full corner/turnout/tunnel clearance NOT CERTIFIED."),false);
        src.sendSuccess(()->Component.literal("[DEV50e4] NO BLOCKS CHANGED."
                +" DO NOT excavate/drive a loop yet. Save both latest.log and debug.log."),false);
        return 1;
    }


    // DEV50e5: entirely READ-ONLY planning.  Route cells are 1-block SAMPLE
    // POINTS, not native legacy track connection promises. Slope blocks,
    // corners, sidings, subway portals and tunnels MUST NOT be placed by this
    // routine or counted as certified until their exact native model-aware
    // movement/clearance footprint has a passing runtime integration test.
    private record E5Route(int eastWestRadius,int northSouthRadius,int railY,
                           int cells,int unloaded,int village,int fluids,
                           int level,int gradeCandidates,int deepCuts,
                           int bridgeCandidates,int tunnelCandidates,
                           int cornerHazards,int obstructions,long penalty) {}
    private record E5Stop(String side,BlockPos proposed,int loaded,int structure,
                          int terrainDifference,int fluid,int obstructed,
                          boolean platformCandidate) {}
    private record E5Palette(int normal,int energy,int curve,int slope,
                             int switchParts,int parallel,int station,int total,
                             String curveSamples,String slopeSamples) {}

    private static String dev50e5Style(ServerLevel level,BlockPos village) {
        // Stable per world and village, not nondeterministic every server tick.
        long seed=level.getSeed() ^ (long)village.getX()*341873128712L
                ^ (long)village.getZ()*132897987541L;
        int bucket=(int)Math.floorMod(seed ^ (seed >>> 32),12L);
        if(bucket==0 || bucket==1)return "ABANDONED_CANDIDATE";
        if(bucket==2 || bucket==3)return "SUBWAY_CANDIDATE";
        if(bucket==4)return "CONSTRUCTION_CANDIDATE";
        return "ACTIVE_SURFACE_CANDIDATE";
    }

    private static E5Palette dev50e5TrackCatalog() {
        int normal=0,energy=0,curve=0,slope=0,switches=0,parallel=0,station=0,total=0;
        List<String> curves=new ArrayList<>(),slopes=new ArrayList<>();
        for(ResourceLocation id:BuiltInRegistries.BLOCK.keySet()) {
            if(!"traincraft".equals(id.getNamespace()))continue;
            String name=id.getPath().toLowerCase(java.util.Locale.ROOT);
            if(!name.contains("track") && !name.contains("rail") && !name.contains("curve")
                    && !name.contains("slope") && !name.contains("switch"))continue;
            total++;
            if(name.contains("slope") || name.contains("incline") || name.contains("gradient")) {
                slope++;if(slopes.size()<5)slopes.add(name);
            }
            if(name.contains("curve") || name.contains("turn_")) {
                curve++;if(curves.size()<5)curves.add(name);
            }
            if(name.contains("switch") || name.contains("turnout"))switches++;
            if(name.contains("parallel"))parallel++;
            if(name.contains("station"))station++;
            if(name.contains("energy") || name.contains("electric"))energy++;
            if(name.contains("normal"))normal++;
        }
        return new E5Palette(normal,energy,curve,slope,switches,parallel,station,total,
                String.join(",",curves),String.join(",",slopes));
    }

    // Compare rectangular loops without loading ANY extra chunks.  Each
    // segment uses a conservative fixed-Y envelope; slope/tunnel/bridge
    // opportunities are labels only, NEVER authoritative build permissions.
    private static E5Route dev50e5Assess(ServerLevel level,BlockPos village,int rx,int rz) {
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                village.getX(),village.getZ());
        Set<BlockPos> perimeter=new LinkedHashSet<>();
        for(int dx=-rx;dx<=rx;dx++) {
            perimeter.add(new BlockPos(village.getX()+dx,y,village.getZ()-rz));
            perimeter.add(new BlockPos(village.getX()+dx,y,village.getZ()+rz));
        }
        for(int dz=-rz;dz<=rz;dz++) {
            perimeter.add(new BlockPos(village.getX()-rx,y,village.getZ()+dz));
            perimeter.add(new BlockPos(village.getX()+rx,y,village.getZ()+dz));
        }
        int unloaded=0,villageHit=0,fluids=0,levelCells=0,grade=0,cuts=0,bridges=0,tunnels=0,corners=0,blocked=0;
        for(BlockPos p:perimeter) {
            if(!level.hasChunkAt(p)){unloaded++;continue;}
            int terrainY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            int delta=terrainY-y;
            boolean corner=Math.abs(Math.abs(p.getX()-village.getX())-rx)<=8
                    &&Math.abs(Math.abs(p.getZ()-village.getZ())-rz)<=8;
            boolean villageStructure=false,fluid=false,obstacle=false,missingNeighbor=false;
            // Static clearance SCREEN: 3-block horizontal corridor and 5
            // vertical air cells, including adjacent chunks.  This cannot
            // certify train-body sweeps around native 90-degree curve pieces.
            for(int sideways=-3;sideways<=3;sideways++) {
                // Scan normal to the side of this future horizontal/vertical
                // edge.  Corners need a separate future geometry solver.
                int sx=Math.abs(p.getX()-village.getX())==rx ? sideways:0;
                int sz=Math.abs(p.getX()-village.getX())==rx ? 0:sideways;
                BlockPos side=p.offset(sx,0,sz);
                if(!level.hasChunkAt(side)){missingNeighbor=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(side,StructureTags.VILLAGE).isValid())
                    villageStructure=true;
                for(int height=0;height<=4;height++) {
                    BlockPos cell=side.above(height);
                    BlockState bs=level.getBlockState(cell);
                    if(!bs.getFluidState().isEmpty())fluid=true;
                    if(!bs.getCollisionShape(level,cell).isEmpty())obstacle=true;
                }
            }
            if(missingNeighbor){unloaded++;continue;}
            if(villageStructure)villageHit++;
            if(fluid)fluids++;
            if(obstacle)blocked++;
            if(delta==0)levelCells++;
            if(Math.abs(delta)>0 && Math.abs(delta)<=3)grade++;
            if(delta>3)cuts++;
            if(delta< -3)bridges++;
            if(delta>=4 && delta<=16 && !fluid && !villageStructure)tunnels++;
            if(corner && (villageStructure || fluid || obstacle || delta!=0))corners++;
        }
        // Lower is better; loaded-village intersections are a HARD future
        // placement barrier regardless of total score.  No placement here.
        long penalty=(long)unloaded*500+(long)villageHit*500+(long)fluids*350
                +(long)corners*45+(long)blocked*12+(long)cuts*8
                +(long)bridges*7+(long)grade*2;
        return new E5Route(rx,rz,y,perimeter.size(),unloaded,villageHit,fluids,
                levelCells,grade,cuts,bridges,tunnels,corners,blocked,penalty);
    }

    private static E5Stop dev50e5Stop(ServerLevel level,BlockPos village,
                                      E5Route route,String side) {
        int x=village.getX(),z=village.getZ();
        if("N".equals(side))z-=route.northSouthRadius();
        if("S".equals(side))z+=route.northSouthRadius();
        if("E".equals(side))x+=route.eastWestRadius();
        if("W".equals(side))x-=route.eastWestRadius();
        BlockPos location=new BlockPos(x,route.railY(),z);
        int loaded=0,structures=0,differences=0,fluids=0,obstructed=0;
        boolean ns="E".equals(side)||"W".equals(side);
        // A 17-cell future stop, with platform space OUTSIDE the loop.
        // The physical station building is NOT relocated by this survey.
        int ox="E".equals(side)?4:"W".equals(side)?-4:0;
        int oz="S".equals(side)?4:"N".equals(side)?-4:0;
        for(int t=-8;t<=8;t++) {
            BlockPos rail=location.offset(ns?0:t,0,ns?t:0);
            BlockPos platform=rail.offset(ox,0,oz);
            if(!level.hasChunkAt(rail) || !level.hasChunkAt(platform))continue;
            loaded++;
            if(level.structureManager().getStructureWithPieceAt(rail,StructureTags.VILLAGE).isValid()
                    ||level.structureManager().getStructureWithPieceAt(platform,StructureTags.VILLAGE).isValid())
                structures++;
            if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    rail.getX(),rail.getZ())!=route.railY()
                    ||level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    platform.getX(),platform.getZ())!=route.railY())differences++;
            for(int h=0;h<=4;h++) {
                BlockPos cell=rail.above(h),platformCell=platform.above(h);
                BlockState bs=level.getBlockState(cell);
                if(!bs.getFluidState().isEmpty() || !level.getBlockState(platformCell).getFluidState().isEmpty())fluids++;
                if(!bs.getCollisionShape(level,cell).isEmpty())obstructed++;
            }
        }
        boolean possible=loaded==17 && structures==0 && differences==0
                && fluids==0 && obstructed==0;
        return new E5Stop(side,location,loaded,structures,differences,
                fluids,obstructed,possible);
    }


    private record E5Worksite(String side,BlockPos junction,BlockPos bufferedEnd,
                              BlockPos gradedEnd,int samples,int unloaded,
                              int villageHits,int fluidHits,int obstructed,
                              int gradeMismatch,boolean screenClear) {}

    private static E5Worksite dev50e5UnfinishedBranch(ServerLevel level,
            BlockPos village,E5Route route,String side,int laid,int grading) {
        // Junction is OFFSET from the passenger drop-off to keep a prospective
        // freight/construction spur separate from passenger platform space.
        int x=village.getX(),z=village.getZ(),dx=0,dz=0;
        switch(side) {
            case "N" -> { x+=24; z-=route.northSouthRadius(); dz=-1; }
            case "S" -> { x+=24; z+=route.northSouthRadius(); dz=1; }
            case "E" -> { x+=route.eastWestRadius(); z+=24; dx=1; }
            case "W" -> { x-=route.eastWestRadius(); z+=24; dx=-1; }
            default -> throw new IllegalArgumentException("Unexpected survey side");
        }
        BlockPos start=new BlockPos(x,route.railY(),z);
        BlockPos buffer=start.offset(dx*laid,0,dz*laid);
        BlockPos earthworks=start.offset(dx*(laid+grading),0,dz*(laid+grading));
        int samples=0,unloaded=0,buildings=0,fluids=0,blocked=0,gradeChanges=0;
        // Side/roof clearance sampling is deliberately conservative: this
        // survey is not a native turnout route certification or build permit.
        for(int step=0;step<=laid+grading;step++) {
            BlockPos axis=start.offset(dx*step,0,dz*step);
            samples++;
            boolean unseen=false,occupied=false,liquid=false,villageBlock=false;
            for(int sideOffset=-3;sideOffset<=3;sideOffset++) {
                BlockPos p=axis.offset(dz*sideOffset,0,dx*sideOffset);
                if(!level.hasChunkAt(p)){unseen=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())
                    villageBlock=true;
                for(int h=0;h<5;h++) {
                    BlockPos at=p.above(h);
                    BlockState state=level.getBlockState(at);
                    if(!state.getFluidState().isEmpty())liquid=true;
                    if(!state.getCollisionShape(level,at).isEmpty())occupied=true;
                }
            }
            if(unseen){unloaded++;continue;}
            if(villageBlock)buildings++;
            if(liquid)fluids++;
            if(occupied)blocked++;
            if(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    axis.getX(),axis.getZ())!=route.railY())gradeChanges++;
        }
        boolean screenClear=unloaded==0&&buildings==0&&fluids==0&&blocked==0&&gradeChanges==0;
        return new E5Worksite(side,start,buffer,earthworks,samples,unloaded,
                buildings,fluids,blocked,gradeChanges,screenClear);
    }

    private static int dev50e5Plan(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e5 READ-ONLY route planner allowed ONLY in fresh disposable save "
                    +DEV50E2_TEST_WORLD+". NO CHANGES."));return 0;
        }
        VillageProbe villageProbe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(villageProbe.center().isEmpty()) {
            src.sendFailure(Component.literal("No verified village structure visible in loaded chunks. Try /tcrailway diagnose. NO CHANGES."));return 0;
        }
        BlockPos village=villageProbe.center().get();
        E5Palette palette=dev50e5TrackCatalog();
        String style=dev50e5Style(level,village);
        Optional<BlockPos> main=stations(level).closest(village,160);
        String mainSide="S";
        if(main.isPresent()) {
            BlockPos station=main.get();
            int dx=station.getX()-village.getX(),dz=station.getZ()-village.getZ();
            mainSide=Math.abs(dx)>Math.abs(dz)?(dx>=0?"E":"W"):(dz>=0?"S":"N");
        }
        E5Route best=null;
        // Slightly non-square circuits are legitimate design alternatives.
        // The lower-level planner still must validate actual curve radii,
        // station connectors, slopes, bridge supports and tunnel lining.
        for(int[] dims:new int[][]{{72,72},{88,88},{104,88},{88,104},{104,104}}) {
            E5Route route=dev50e5Assess(level,village,dims[0],dims[1]);
            LOG.info("[TC-DEV50E5] ROUTE_CANDIDATE village={} radiusX={} radiusZ={} cells={} unloaded={} village={} fluid={} clearSurface={} grades={} cut={} bridge={} tunnelCandidates={} unsafeCorners={} obstructed={} penalty={} status=READ_ONLY_UNCERTIFIED",
                    village,route.eastWestRadius(),route.northSouthRadius(),route.cells(),route.unloaded(),
                    route.village(),route.fluids(),route.level(),route.gradeCandidates(),
                    route.deepCuts(),route.bridgeCandidates(),route.tunnelCandidates(),
                    route.cornerHazards(),route.obstructions(),route.penalty());
            if(best==null || route.penalty()<best.penalty())best=route;
        }
        if(best==null)return 0;
        final E5Route selected=best;
        final String stopMain=mainSide;
        final String mainLink=main.map(p->p.toShortString()).orElse("NOT_YET_BUILT");
        LOG.info("[TC-DEV50E5] ROUTE_PLAN village={} style={} staffPolicy={} mainStation={} mainSide={} radiusX={} radiusZ={} penalty={} terrainSurvey=READ_ONLY placement=DISABLED nativeCorners=UNCERTIFIED",
                village,style,"ABANDONED_CANDIDATE".equals(style)?"NO_WORKERS_NO_SERVICE_IF_BUILT":"UNDECIDED_ACTIVE_PROFILE",
                mainLink,stopMain,selected.eastWestRadius(),selected.northSouthRadius(),selected.penalty());
        for(String side:new String[]{"N","E","S","W"}) {
            E5Stop stop=dev50e5Stop(level,village,selected,side);
            String role=side.equals(mainSide)?"MAIN_STATION_CONNECTION_PROPOSAL":"SMALL_DROPOFF_PROPOSAL";
            LOG.info("[TC-DEV50E5] STOP_PROPOSAL role={} side={} position={} chunksLoaded={}/17 village={} gradeMismatch={} fluid={} overhead={} candidate={} NOT_PLACED",
                    role,side,stop.proposed(),stop.loaded(),stop.structure(),
                    stop.terrainDifference(),stop.fluid(),stop.obstructed(),stop.platformCandidate());
            final String line="[E5] "+side+" "+(side.equals(mainSide)?"main station link":"small drop-off")
                    +" at "+stop.proposed().toShortString()
                    +" site="+(stop.platformCandidate()?"SCREEN_CLEAR":"NEEDS_REROUTE")
                    +" (loaded="+stop.loaded()+"/17, village="+stop.structure()
                    +", grade="+stop.terrainDifference()+")";
            src.sendSuccess(()->Component.literal(line),false);
        }
        // Intentionally unfinished branches can be part of ACTIVE and
        // CONSTRUCTION style villages, but are NEVER treated as traversable
        // operational routes. Actual buffer stops, gravel, supplies and
        // staged rail sections need subsequent construction acceptance.
        long featureSeed=level.getSeed() ^ (long)village.getX()*73428767L
                ^ (long)village.getZ()*912367L;
        boolean wipRequested="CONSTRUCTION_CANDIDATE".equals(style)
                || Math.floorMod(featureSeed ^ (featureSeed>>>19),4L)==0;
        if(wipRequested) {
            String[] sides={"N","E","S","W"};
            int which=(int)Math.floorMod(featureSeed,4L);
            // Prefer a side other than the main station approach.
            if(sides[which].equals(mainSide))which=(which+1)%4;
            int laid=24,grading=16;
            E5Worksite wip=dev50e5UnfinishedBranch(level,village,selected,
                    sides[which],laid,grading);
            LOG.info("[TC-DEV50E5] UNFINISHED_BRANCH_PROPOSAL village={} style={} side={} junction={} buffer={} gradedEnd={} laidRailCells={} ballastOnlyCells={} samples={} unloaded={} village={} fluid={} obstructed={} grade={} clearanceScreen={} service=DISABLED status=READ_ONLY_NOT_PLACED",
                    village,style,wip.side(),wip.junction(),wip.bufferedEnd(),
                    wip.gradedEnd(),laid,grading,wip.samples(),wip.unloaded(),
                    wip.villageHits(),wip.fluidHits(),wip.obstructed(),
                    wip.gradeMismatch(),wip.screenClear()?"CLEAR":"NEEDS_REROUTE");
            src.sendSuccess(()->Component.literal("[E5] UNFINISHED SPUR idea: "+wip.side()
                    +" side: "+laid+" planned rail cells to buffer, "+grading
                    +" graded/ballast-only beyond, junction="+wip.junction().toShortString()
                    +"; screen="+(wip.screenClear()?"CLEAR":"NEEDS_REROUTE")
                    +". No construction blocks placed."),false);
        } else {
            LOG.info("[TC-DEV50E5] UNFINISHED_BRANCH_PROPOSAL village={} style={} requested=false status=SKIPPED_FOR_VARIETY",
                    village,style);
        }
        LOG.info("[TC-DEV50E5] TRACK_CATALOG registered={} normal={} energy={} curves={} slopes={} switches={} parallel={} station={} curveSamples={} slopeSamples={} compatibility=UNTESTED_CATALOG_ONLY",
                palette.total(),palette.normal(),palette.energy(),palette.curve(),palette.slope(),
                palette.switchParts(),palette.parallel(),palette.station(),
                palette.curveSamples(),palette.slopeSamples());
        src.sendSuccess(()->Component.literal("[E5] style="+style+" mainExisting="+mainLink
                +" village="+village.toShortString()+" rectangular loop="
                +selected.eastWestRadius()+"x"+selected.northSouthRadius()
                +" blocks; candidate route cells="+selected.cells()),false);
        src.sendSuccess(()->Component.literal("[E5] future track sections: straight, curves, slopes,"
                +" parallel sidings, switches; optional tunnel="+selected.tunnelCandidates()
                +", elevated="+selected.bridgeCandidates()+", grades="+selected.gradeCandidates()),false);
        src.sendSuccess(()->Component.literal("[E5] Future incomplete spurs MUST end at a buffer/closure and be EXCLUDED from train dispatch. Unfinished site plan is read-only."),false);
        src.sendSuccess(()->Component.literal("[E5] 0 blocks changed. Native corner/slope/tunnel"
                +" compatibility and FULL rolling-stock clearance NOT CERTIFIED."
                +" No abandoned staff or subway generated by this pilot."),false);
        return 1;
    }

    private static int dev50e5Catalog(CommandSourceStack src) {
        if(!dev50e2WorldAllowed(src.getLevel())) {
            src.sendFailure(Component.literal("DEV50e5 catalog is limited to the disposable planner world. NO CHANGES."));return 0;
        }
        E5Palette p=dev50e5TrackCatalog();
        src.sendSuccess(()->Component.literal("[E5] READ-ONLY registered track catalog: total="+p.total()
                +" normal="+p.normal()+" energy="+p.energy()+" curves="+p.curve()
                +" slopes="+p.slope()+" switches="+p.switchParts()
                +" parallel="+p.parallel()+" station="+p.station()),false);
        src.sendSuccess(()->Component.literal("[E5] sampled curves="+p.curveSamples()
                +" sampled slopes="+p.slopeSamples()+". Names do NOT prove connection compatibility."),false);
        return 1;
    }


    // DEV50e6 TERRAIN-RELATIVE SURVEY, strictly READ-ONLY. This replaces only
    // the primary /loop plan diagnostic. Legacy e5 plan remains available as
    // /loop legacyplan to compare failures without mutating any save blocks.
    // Planned rail heights are MEDIAN terrain sample profiles, not accepted
    // track-placement instructions. Native slopes/turns/bridge and tunnel
    // sections still require model-aware segment-connection validation.
    private record E6Route(int radiusX,int radiusZ,int cells,int unloaded,
                           int minSurfaceY,int maxSurfaceY,int maxProfileStep,
                           int stepOverOne,int gradeTooClose,int proposedGrades,
                           int villageConflict,int fluidHazards,int sideObstructed,
                           int cuts,int bridgeSupports,int tunnelScreen,
                           int cornersUncertified,long score) {}
    private record E6Stop(String side,BlockPos site,int loaded,int village,
                          int localRange,int gradeMismatch,int platformMismatch,
                          int fluid,int overhead,boolean levelCandidate) {}
    private record E6TrackCatalog(int blockIds,int curveIds,int slopeIds,
                                  int switchIds,int parallelIds,int stationIds,
                                  int energyNamedBlockIds,int energyNamedItemIds,
                                  int energyStatePropertyBlocks,int energyItemStateHints) {}

    // An ORDERED closed perimeter, with adjacent points exactly one block
    // apart. This gives a meaningful grade-run and corner-neighborhood audit.
    private static List<BlockPos> dev50e6Perimeter(BlockPos village,int rx,int rz) {
        List<BlockPos> pts=new ArrayList<>();
        int cx=village.getX(),cz=village.getZ();
        for(int x=-rx;x<=rx;x++)pts.add(new BlockPos(cx+x,0,cz-rz));
        for(int z=-rz+1;z<=rz;z++)pts.add(new BlockPos(cx+rx,0,cz+z));
        for(int x=rx-1;x>=-rx;x--)pts.add(new BlockPos(cx+x,0,cz+rz));
        for(int z=rz-1;z>-rz;z--)pts.add(new BlockPos(cx-rx,0,cz+z));
        return pts;
    }

    private static int dev50e6Median(int[] values,int center,int halfWindow) {
        int[] window=new int[halfWindow*2+1];int count=0;
        for(int d=-halfWindow;d<=halfWindow;d++) {
            int y=values[Math.floorMod(center+d,values.length)];
            if(y!=Integer.MIN_VALUE)window[count++]=y;
        }
        if(count==0)return Integer.MIN_VALUE;
        java.util.Arrays.sort(window,0,count);
        return window[count/2];
    }

    private static E6Route dev50e6Assess(ServerLevel level,BlockPos v,int rx,int rz) {
        List<BlockPos> path=dev50e6Perimeter(v,rx,rz);
        int n=path.size();int[] surface=new int[n],profile=new int[n];
        int unloaded=0,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(int i=0;i<n;i++) {
            BlockPos cell=path.get(i);
            if(!level.hasChunkAt(cell)) {surface[i]=Integer.MIN_VALUE;unloaded++;continue;}
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cell.getX(),cell.getZ());
            surface[i]=y;minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        for(int i=0;i<n;i++)profile[i]=surface[i]==Integer.MIN_VALUE
                ?Integer.MIN_VALUE:dev50e6Median(surface,i,8);
        int maxStep=0,oversized=0,tight=0,grade=0,lastChange=-99999,firstChange=-1;
        for(int i=0;i<n;i++) {
            int a=profile[i],b=profile[(i+1)%n];
            if(a==Integer.MIN_VALUE||b==Integer.MIN_VALUE)continue;
            int step=Math.abs(a-b);maxStep=Math.max(maxStep,step);
            if(step>1)oversized++;
            if(step>0) {
                grade++;
                if(i-lastChange<8)tight++;
                if(firstChange<0)firstChange=i;
                lastChange=i;
            }
        }
        if(firstChange>=0&&lastChange>=0&&firstChange+n-lastChange<8&&firstChange!=lastChange)tight++;
        int structure=0,fluid=0,overhead=0,cut=0,bridge=0,tunnel=0,corners=0;
        for(int i=0;i<n;i++) {
            BlockPos sample=path.get(i);
            int proposedY=profile[i];
            if(proposedY==Integer.MIN_VALUE)continue;
            int delta=surface[i]-proposedY;
            if(delta>=1)cut++;
            if(delta<=-2)bridge++;
            if(delta>=4)tunnel++;
            boolean atCorner=Math.abs(Math.abs(sample.getX()-v.getX())-rx)<=8
                    &&Math.abs(Math.abs(sample.getZ()-v.getZ())-rz)<=8;
            if(atCorner)corners++; // mandatory native 90-degree swept-footprint certification
            boolean blocked=false,wet=false,inVillage=false,edgeUnloaded=false;
            // Inspect a +/-3 block side envelope relative to local proposed
            // rail Y, not to the village center elevation.  Existing terrain
            // can be classified as a possible cut/tunnel; never excavated.
            boolean verticalEdge=Math.abs(sample.getX()-v.getX())==rx;
            for(int d=-3;d<=3;d++) {
                int sx=sample.getX()+(verticalEdge?d:0);
                int sz=sample.getZ()+(verticalEdge?0:d);
                BlockPos side=new BlockPos(sx,proposedY,sz);
                if(!level.hasChunkAt(side)){edgeUnloaded=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(side,StructureTags.VILLAGE).isValid()
                        ||level.structureManager().getStructureWithPieceAt(side.below(),StructureTags.VILLAGE).isValid())
                    inVillage=true;
                if(!level.getBlockState(side.below()).getFluidState().isEmpty())wet=true;
                for(int h=0;h<5;h++) {
                    BlockPos occupied=side.above(h);
                    BlockState state=level.getBlockState(occupied);
                    if(!state.getFluidState().isEmpty())wet=true;
                    if(!state.getCollisionShape(level,occupied).isEmpty())blocked=true;
                }
            }
            if(edgeUnloaded)unloaded++;
            if(inVillage)structure++;
            if(wet)fluid++;
            if(blocked)overhead++;
        }
        if(minY==Integer.MAX_VALUE)minY=0;
        if(maxY==Integer.MIN_VALUE)maxY=0;
        // Structural/fluid/unloaded/steep samples are HARD STOP signals for
        // eventual construction regardless of this comparative score.
        long score=(long)unloaded*700+(long)structure*1200+(long)fluid*700
                +(long)oversized*180+(long)tight*40+(long)overhead*18
                +(long)cut*5+(long)bridge*7+(long)grade*4;
        return new E6Route(rx,rz,n,unloaded,minY,maxY,maxStep,oversized,
                tight,grade,structure,fluid,overhead,cut,bridge,tunnel,corners,score);
    }

    private static E6Stop dev50e6Stop(ServerLevel level,BlockPos v,E6Route route,String side) {
        int x=v.getX(),z=v.getZ();
        if("N".equals(side))z-=route.radiusZ();
        if("S".equals(side))z+=route.radiusZ();
        if("E".equals(side))x+=route.radiusX();
        if("W".equals(side))x-=route.radiusX();
        boolean ns="E".equals(side)||"W".equals(side);
        int ox="E".equals(side)?4:"W".equals(side)?-4:0;
        int oz="S".equals(side)?4:"N".equals(side)?-4:0;
        int[] heights=new int[17], platformY=new int[17];
        int loaded=0,structure=0,fluid=0,overhead=0;
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int i=0;i<17;i++) {
            int t=i-8;
            BlockPos rail=new BlockPos(x+(ns?0:t),0,z+(ns?t:0));
            BlockPos platform=rail.offset(ox,0,oz);
            if(!level.hasChunkAt(rail)||!level.hasChunkAt(platform)) {
                heights[i]=platformY[i]=Integer.MIN_VALUE;continue;
            }
            loaded++;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,rail.getX(),rail.getZ());
            int py=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,platform.getX(),platform.getZ());
            heights[i]=y;platformY[i]=py;
            min=Math.min(min,y);max=Math.max(max,y);
        }
        int[] sorted=new int[17];int count=0;
        for(int h:heights)if(h!=Integer.MIN_VALUE)sorted[count++]=h;
        java.util.Arrays.sort(sorted,0,count);
        int stationY=count==0?v.getY():sorted[count/2];
        int gradeMismatch=0,platformMismatch=0;
        for(int i=0;i<17;i++) {
            if(heights[i]==Integer.MIN_VALUE)continue;
            if(Math.abs(heights[i]-stationY)>1)gradeMismatch++;
            if(Math.abs(platformY[i]-stationY)>1)platformMismatch++;
            int t=i-8;
            BlockPos rail=new BlockPos(x+(ns?0:t),stationY,z+(ns?t:0));
            BlockPos platform=rail.offset(ox,0,oz);
            if(level.structureManager().getStructureWithPieceAt(rail,StructureTags.VILLAGE).isValid()
                    ||level.structureManager().getStructureWithPieceAt(platform,StructureTags.VILLAGE).isValid())structure++;
            if(!level.getBlockState(rail.below()).getFluidState().isEmpty()
                    ||!level.getBlockState(platform.below()).getFluidState().isEmpty())fluid++;
            boolean blocked=false;
            for(int h=0;h<5;h++) {
                BlockPos a=rail.above(h),b=platform.above(h);
                BlockState sa=level.getBlockState(a),sb=level.getBlockState(b);
                if(!sa.getFluidState().isEmpty()||!sb.getFluidState().isEmpty())fluid++;
                if(!sa.getCollisionShape(level,a).isEmpty()||!sb.getCollisionShape(level,b).isEmpty())blocked=true;
            }
            if(blocked)overhead++;
        }
        int range=count==0?0:max-min;
        boolean candidate=loaded==17&&structure==0&&fluid==0&&overhead==0
                &&gradeMismatch==0&&platformMismatch==0&&range<=1;
        return new E6Stop(side,new BlockPos(x,stationY,z),loaded,structure,
                range,gradeMismatch,platformMismatch,fluid,overhead,candidate);
    }

    private static E6TrackCatalog dev50e6CatalogBlocks() {
        int total=0,curves=0,slopes=0,switches=0,parallel=0,station=0;
        int energyNames=0,energyItems=0,stateEnergy=0,energyHints=0;
        for(ResourceLocation key:BuiltInRegistries.BLOCK.keySet()) {
            if(!"traincraft".equals(key.getNamespace()))continue;
            String n=key.getPath().toLowerCase(java.util.Locale.ROOT);
            if(!n.contains("track")&&!n.contains("rail")&&!n.contains("slope")
                    &&!n.contains("curve")&&!n.contains("switch"))continue;
            total++;
            if(n.contains("curve")||n.contains("turn_"))curves++;
            if(n.contains("slope")||n.contains("incline"))slopes++;
            if(n.contains("switch")||n.contains("turnout"))switches++;
            if(n.contains("parallel"))parallel++;
            if(n.contains("station"))station++;
            if(n.contains("energy")||n.contains("electric"))energyNames++;
            boolean stateHasEnergy=false;
            for(net.minecraft.world.level.block.state.properties.Property<?> prop:
                    BuiltInRegistries.BLOCK.get(key).getStateDefinition().getProperties()) {
                String propName=prop.getName().toLowerCase(java.util.Locale.ROOT);
                if(propName.contains("energy")||propName.contains("electric"))stateHasEnergy=true;
            }
            if(stateHasEnergy)stateEnergy++;
        }
        for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()) {
            if(!"traincraft".equals(id.getNamespace()))continue;
            String name=id.getPath().toLowerCase(java.util.Locale.ROOT);
            if(name.contains("energy")||name.contains("electric")) {
                if(name.contains("track")||name.contains("rail"))energyItems++;
            }
            if(name.contains("track")||name.contains("rail"))energyHints++;
        }
        return new E6TrackCatalog(total,curves,slopes,switches,parallel,station,
                energyNames,energyItems,stateEnergy,energyHints);
    }

    private static int dev50e6Plan(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6 read-only terrain plan only in disposable world "
                    +DEV50E2_TEST_WORLD+". NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No loaded, verified vanilla village structure within 128 blocks. See /tcrailway diagnose. NO CHANGES."));return 0;
        }
        BlockPos v=probe.center().get();
        E6Route best=null;
        for(int[] dim:new int[][]{{72,72},{88,88},{104,88},{88,104},{104,104}}) {
            E6Route r=dev50e6Assess(level,v,dim[0],dim[1]);
            LOG.info("[TC-DEV50E6] TERRAIN_ROUTE village={} rx={} rz={} cells={} surfaceMinY={} surfaceMaxY={} maxStep={} stepGT1={} tightGrades={} proposedGradeSteps={} structure={} fluid={} occupiedEnvelope={} cutCells={} supportCells={} tunnelProspects={} cornersUncertified={} unloaded={} penalty={} verdict=READ_ONLY_UNCERTIFIED",
                    v,r.radiusX(),r.radiusZ(),r.cells(),r.minSurfaceY(),r.maxSurfaceY(),
                    r.maxProfileStep(),r.stepOverOne(),r.gradeTooClose(),r.proposedGrades(),
                    r.villageConflict(),r.fluidHazards(),r.sideObstructed(),r.cuts(),
                    r.bridgeSupports(),r.tunnelScreen(),r.cornersUncertified(),r.unloaded(),r.score());
            if(best==null||r.score()<best.score())best=r;
        }
        if(best==null)return 0;
        final E6Route chosen=best;
        String style=dev50e5Style(level,v);
        Optional<BlockPos> main=stations(level).closest(v,160);
        String mainSide="S";
        if(main.isPresent()) {
            BlockPos anchor=main.get();int dx=anchor.getX()-v.getX(),dz=anchor.getZ()-v.getZ();
            mainSide=Math.abs(dx)>Math.abs(dz)?(dx>=0?"E":"W"):(dz>=0?"S":"N");
        }
        LOG.info("[TC-DEV50E6] TERRAIN_PLAN village={} style={} staffPolicy={} mainAnchor={} mainSide={} rx={} rz={} fixedY=false stationHeight=LOCAL_MEDIAN_17 placement=DISABLED nativeCurveAndSlope=UNCERTIFIED",
                v,style,"ABANDONED_CANDIDATE".equals(style)?"NO_WORKERS_NO_SERVICE_IF_BUILT":"UNDECIDED_ACTIVE_PROFILE",
                main.map(BlockPos::toShortString).orElse("NONE"),mainSide,chosen.radiusX(),chosen.radiusZ());
        src.sendSuccess(()->Component.literal("[E6] TERRAIN route="+chosen.radiusX()+"x"+chosen.radiusZ()
                +" around village "+v.toShortString()+" style="+style),false);
        src.sendSuccess(()->Component.literal("[E6] local surface Y="+chosen.minSurfaceY()+".."+chosen.maxSurfaceY()
                +" maxProfileStep="+chosen.maxProfileStep()+" abrupt="+chosen.stepOverOne()
                +" shortSlopeRuns="+chosen.gradeTooClose()+"; curve corners NOT CERTIFIED"),false);
        src.sendSuccess(()->Component.literal("[E6] proposed supports="+chosen.bridgeSupports()
                +" shallow cuts="+chosen.cuts()+" tunnel prospects="+chosen.tunnelScreen()
                +" envelope blocked="+chosen.sideObstructed()+" village="+chosen.villageConflict()
                +" fluid="+chosen.fluidHazards()),false);
        for(String side:new String[]{"N","E","S","W"}) {
            E6Stop stop=dev50e6Stop(level,v,chosen,side);
            String role=side.equals(mainSide)?"MAIN_STATION_LINK":"SMALL_DROPOFF";
            LOG.info("[TC-DEV50E6] TERRAIN_STOP village={} role={} side={} site={} loaded={}/17 terrainRange={} gradeDeviation={} platformDeviation={} structures={} fluid={} overhead={} levelCandidate={} placement=DISABLED",
                    v,role,side,stop.site(),stop.loaded(),stop.localRange(),stop.gradeMismatch(),
                    stop.platformMismatch(),stop.village(),stop.fluid(),stop.overhead(),stop.levelCandidate());
            src.sendSuccess(()->Component.literal("[E6] "+role+" "+side+" y="+stop.site().getY()
                    +" gradeSpread="+stop.localRange()+" platformMismatch="+stop.platformMismatch()
                    +" screen="+(stop.levelCandidate()?"LEVEL_CANDIDATE":"NEEDS_REROUTE")),false);
        }
        LOG.info("[TC-DEV50E6] CONNECTOR mainStation={} side={} status=NOT_GEOMETRY_VERIFIED",main.map(BlockPos::toShortString).orElse("NONE"),mainSide);
        src.sendSuccess(()->Component.literal("[E6] No blocks changed. Native curves/slopes, grade run length, bridges, tunnels, stop connectors and full-train clearance NOT CERTIFIED."),false);
        return 1;
    }

    // DEV50e6b VARIETY + ADAPTIVE CORRIDOR SURVEY
    // This is planning, NOT a path-connected loop builder. Deterministic policy
    // is based on the world seed and a verified vanilla structure start's
    // bounding-box center, never player position, session time or RNG state.
    private enum E6bStyle { NATURAL_VANILLA, ACTIVE_SURFACE, ABANDONED_RESERVED,
                            CONSTRUCTION_RESERVED, SUBWAY_RESERVED }
    private record E6bCorridor(String side,int distance,int shift,int centerX,int centerZ,
                               int loaded,int samples,int structures,int fluids,
                               int blocked,int steep,int shortGrades,int cuts,
                               int supports,int minY,int maxY,long score) {}

    private static long dev50e6bMix(long z) {
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;
        return z^(z>>>31);
    }
    private static int dev50e6bRoll(ServerLevel level,BlockPos structureCenter) {
        long seed=level.getSeed() ^ 0x06b50e6b0f55L;
        seed^=((long)structureCenter.getX()*0x9e3779b97f4a7c15L);
        seed^=((long)structureCenter.getZ()*0xbf58476d1ce4e5b9L);
        return (int)Long.remainderUnsigned(dev50e6bMix(seed),100L);
    }
    private static E6bStyle dev50e6bStyle(ServerLevel level,BlockPos naturalCenter) {
        int roll=dev50e6bRoll(level,naturalCenter);
        if(roll<35)return E6bStyle.NATURAL_VANILLA;
        if(roll<70)return E6bStyle.ACTIVE_SURFACE;
        if(roll<85)return E6bStyle.ABANDONED_RESERVED;
        if(roll<95)return E6bStyle.CONSTRUCTION_RESERVED;
        return E6bStyle.SUBWAY_RESERVED;
    }

    // Loaded-only verified structure start. Never force chunk tickets or guess
    // that houses/villagers/beds imply a naturally generated village. This
    // anchor is used in the optional automatic selection and report commands.
    private static Optional<BlockPos> dev50e6bVillageStartAnchor(ServerLevel level,BlockPos near,int radius) {
        if(!level.dimension().equals(Level.OVERWORLD))return Optional.empty();
        int cx=near.getX()>>4,cz=near.getZ()>>4;
        int cr=(radius+47)/16;
        BlockPos best=null;
        double bestScore=Double.POSITIVE_INFINITY;
        for(int x=cx-cr;x<=cx+cr;x++)for(int z=cz-cr;z<=cz+cr;z++) {
            if(!level.hasChunk(x,z))continue;
            for(StructureStart start:level.getChunk(x,z).getAllStarts().values()) {
                if(start==null||!start.isValid())continue;
                ResourceLocation id=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .getKey(start.getStructure());
                if(id==null||!"minecraft".equals(id.getNamespace()))continue;
                String name=id.getPath();
                if(!("village_plains".equals(name)||"village_savanna".equals(name)
                        ||"village_desert".equals(name)||"village_taiga".equals(name)
                        ||"village_snowy".equals(name)))continue;
                BoundingBox b=start.getBoundingBox();
                int dx=Math.max(0,Math.max(b.minX()-near.getX(),near.getX()-b.maxX()));
                int dz=Math.max(0,Math.max(b.minZ()-near.getZ(),near.getZ()-b.maxZ()));
                double edge=(double)dx*dx+(double)dz*dz;
                if(edge>(double)radius*radius)continue;
                BlockPos p=b.getCenter();
                double direct=(double)(near.getX()-p.getX())*(near.getX()-p.getX())
                        +(double)(near.getZ()-p.getZ())*(near.getZ()-p.getZ());
                double score=edge*1000000.0D+direct;
                if(score<bestScore){bestScore=score;best=p;}
            }
        }
        return Optional.ofNullable(best);
    }

    private static int dev50e6bReport(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6b reporting is isolated to the existing disposable DEV50E6 Terrain Profile Test world. NO CHANGES."));
            return 0;
        }
        BlockPos player=BlockPos.containing(src.getPosition());
        VillageProbe probe=inspectVillageForPilot(level,player,128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No verified loaded vanilla village near player. NO CHANGES. Try /tcrailway diagnose."));return 0;
        }
        BlockPos v=probe.center().get();
        Optional<BlockPos> stable=dev50e6bVillageStartAnchor(level,v,128);
        if(stable.isEmpty()) {
            src.sendFailure(Component.literal("No loaded valid village STRUCTURE START for seed-stable variety. No random fallback. NO CHANGES."));return 0;
        }
        BlockPos anchor=stable.get();
        int roll=dev50e6bRoll(level,anchor);
        E6bStyle style=dev50e6bStyle(level,anchor);
        LOG.info("[TC-DEV50E6B] VILLAGE_REPORT center={} stableAnchor={} roll={} style={} weights=35/35/15/10/5 optedInFresh={} physicalVariants=ACTIVE_ONLY_WHEN_OPTED_IN actions=READ_ONLY",
                v,anchor,roll,style,SEEDED_VARIETY_FRESH.get());
        src.sendSuccess(()->Component.literal("[E6b] village="+v.toShortString()+" seedAnchor="+anchor.toShortString()
                +" roll="+roll+" style="+style),false);
        src.sendSuccess(()->Component.literal("[E6b] weights NATURAL=35 ACTIVE=35 ABANDONED=15 CONSTRUCTION=10 SUBWAY=5; stable by seed + village."),false);
        src.sendSuccess(()->Component.literal("[E6b] fresh-station selection optedIn="+SEEDED_VARIETY_FRESH.get()
                +"; inactive styles remain untouched, NOT constructed. No retrofit. REPORT is READ-ONLY."),false);
        return 1;
    }

    // Scan a 96-block straight corridor per side. Distances/offsets vary, so
    // the route can consider land to either side of a flooded or steep lane.
    // Important: chosen N/E/S/W corridors are INDEPENDENT and may not meet.
    // Track curves, grades, endpoints and full train sweep NOT certified.
    private static E6bCorridor dev50e6bAssessCorridor(ServerLevel level,BlockPos village,
                                                      String side,int distance,int lateral) {
        boolean eastWest="N".equals(side)||"S".equals(side);
        int cx=village.getX()+ (eastWest?lateral:("E".equals(side)?distance:-distance));
        int cz=village.getZ()+ (eastWest?("S".equals(side)?distance:-distance):lateral);
        int n=49,loaded=0,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        int[] heights=new int[n],profile=new int[n];
        for(int i=0;i<n;i++) {
            int t=(i-24)*2;
            int x=cx+(eastWest?t:0),z=cz+(eastWest?0:t);
            BlockPos p=new BlockPos(x,0,z);
            if(!level.hasChunkAt(p)){heights[i]=Integer.MIN_VALUE;continue;}
            int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            heights[i]=h;loaded++;
            minY=Math.min(minY,h);maxY=Math.max(maxY,h);
        }
        for(int i=0;i<n;i++) {
            profile[i]=heights[i]==Integer.MIN_VALUE?Integer.MIN_VALUE:dev50e6bMedianLinear(heights,i,4);
        }
        int structures=0,fluids=0,blocked=0,steep=0,shortGrades=0,cuts=0,supports=0;
        int lastGrade=-100;
        for(int i=0;i<n;i++) {
            int y=profile[i];if(y==Integer.MIN_VALUE)continue;
            int t=(i-24)*2;
            int x=cx+(eastWest?t:0),z=cz+(eastWest?0:t);
            if(i>0 && profile[i-1]!=Integer.MIN_VALUE) {
                int diff=Math.abs(y-profile[i-1]);
                if(diff>1)steep++;
                if(diff>0) {
                    if(i-lastGrade<4)shortGrades++;
                    lastGrade=i;
                }
            }
            if(heights[i]-y>2)cuts++;
            if(y-heights[i]>2)supports++;
            boolean structural=false,wet=false,occupied=false;
            for(int w=-3;w<=3;w++) {
                int xx=x+(eastWest?0:w),zz=z+(eastWest?w:0);
                BlockPos base=new BlockPos(xx,y,zz);
                if(!level.hasChunkAt(base)){occupied=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(base,StructureTags.VILLAGE).isValid()
                        ||level.structureManager().getStructureWithPieceAt(base.below(),StructureTags.VILLAGE).isValid())structural=true;
                if(!level.getBlockState(base.below()).getFluidState().isEmpty())wet=true;
                for(int k=0;k<5;k++) {
                    BlockPos air=base.above(k);
                    BlockState state=level.getBlockState(air);
                    if(!state.getFluidState().isEmpty())wet=true;
                    if(!state.getCollisionShape(level,air).isEmpty())occupied=true;
                }
            }
            if(structural)structures++;
            if(wet)fluids++;
            if(occupied)blocked++;
        }
        if(minY==Integer.MAX_VALUE){minY=0;maxY=0;}
        // Hard violations dominate score; ranking does NOT make them safe.
        long score=(long)(n-loaded)*100000L+(long)structures*40000L
                +(long)fluids*5000L+(long)steep*1000L+(long)shortGrades*350L
                +(long)blocked*80L+(long)cuts*8L+(long)supports*12L
                +(long)Math.abs(lateral)*4L+(long)distance;
        return new E6bCorridor(side,distance,lateral,cx,cz,loaded,n,structures,fluids,
                blocked,steep,shortGrades,cuts,supports,minY,maxY,score);
    }
    private static int dev50e6bMedianLinear(int[] values,int at,int radius) {
        int[] numbers=new int[radius*2+1];int n=0;
        for(int i=Math.max(0,at-radius);i<=Math.min(values.length-1,at+radius);i++) {
            if(values[i]!=Integer.MIN_VALUE)numbers[n++]=values[i];
        }
        if(n==0)return Integer.MIN_VALUE;
        java.util.Arrays.sort(numbers,0,n);return numbers[n/2];
    }

    private static E6Stop dev50e6bStop(ServerLevel level,E6bCorridor c) {
        boolean ew="N".equals(c.side())||"S".equals(c.side());
        int offsetX="E".equals(c.side())?4:"W".equals(c.side())?-4:0;
        int offsetZ="S".equals(c.side())?4:"N".equals(c.side())?-4:0;
        int[] height=new int[17],pad=new int[17];int loaded=0,structure=0,fluid=0,overhead=0;
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int i=0;i<17;i++) {
            int d=i-8,x=c.centerX()+(ew?d:0),z=c.centerZ()+(ew?0:d);
            BlockPos line=new BlockPos(x,0,z),p=new BlockPos(x+offsetX,0,z+offsetZ);
            if(!level.hasChunkAt(line)||!level.hasChunkAt(p)){
                height[i]=pad[i]=Integer.MIN_VALUE;continue;
            }
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            int py=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            height[i]=y;pad[i]=py;low=Math.min(low,y);high=Math.max(high,y);loaded++;
        }
        int[] sorted=new int[17];int count=0;
        for(int h:height)if(h!=Integer.MIN_VALUE)sorted[count++]=h;
        java.util.Arrays.sort(sorted,0,count);
        int y=count==0?0:sorted[count/2],gradeMismatch=0,platformMismatch=0;
        for(int i=0;i<17;i++) {
            if(height[i]==Integer.MIN_VALUE)continue;
            if(Math.abs(height[i]-y)>1)gradeMismatch++;
            if(Math.abs(pad[i]-y)>1)platformMismatch++;
            int d=i-8,x=c.centerX()+(ew?d:0),z=c.centerZ()+(ew?0:d);
            BlockPos rail=new BlockPos(x,y,z),platform=rail.offset(offsetX,0,offsetZ);
            if(level.structureManager().getStructureWithPieceAt(rail,StructureTags.VILLAGE).isValid()
                    ||level.structureManager().getStructureWithPieceAt(platform,StructureTags.VILLAGE).isValid())structure++;
            if(!level.getBlockState(rail.below()).getFluidState().isEmpty()
                    ||!level.getBlockState(platform.below()).getFluidState().isEmpty())fluid++;
            boolean occupied=false;
            for(int k=0;k<5;k++) {
                BlockPos a=rail.above(k),b=platform.above(k);
                BlockState aa=level.getBlockState(a),bb=level.getBlockState(b);
                if(!aa.getFluidState().isEmpty()||!bb.getFluidState().isEmpty())fluid++;
                if(!aa.getCollisionShape(level,a).isEmpty()||!bb.getCollisionShape(level,b).isEmpty())occupied=true;
            }
            if(occupied)overhead++;
        }
        int span=count==0?0:high-low;
        boolean levelCandidate=loaded==17&&structure==0&&fluid==0&&overhead==0
                &&gradeMismatch==0&&platformMismatch==0&&span<=1;
        return new E6Stop(c.side(),new BlockPos(c.centerX(),y,c.centerZ()),loaded,
                structure,span,gradeMismatch,platformMismatch,fluid,overhead,levelCandidate);
    }

    private static int dev50e6bAdaptive(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6b adaptive survey only in named disposable world. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No verified loaded vanilla village. /tcrailway diagnose. NO CHANGES."));return 0;
        }
        BlockPos v=probe.center().get();
        Optional<BlockPos> stable=dev50e6bVillageStartAnchor(level,v,128);
        if(stable.isEmpty()) {
            src.sendFailure(Component.literal("No verified loaded vanilla village start to seed variety. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,stable.get());
        if(style==E6bStyle.NATURAL_VANILLA) {
            LOG.info("[TC-DEV50E6B] ADAPTIVE village={} stableAnchor={} style={} selected=NATURAL_NO_TRACKS action=READ_ONLY noRails=true",v,stable.get(),style);
            src.sendSuccess(()->Component.literal("[E6b] NATURAL VANILLA village: NO station/track/staff/service proposals. No blocks changed."),false);
            return 1;
        }
        E6bCorridor[] chosen=new E6bCorridor[4];
        int k=0,candidates=0;
        for(String side:new String[]{"N","E","S","W"}) {
            E6bCorridor best=null;
            for(int distance:new int[]{64,80,96,112})for(int shift:new int[]{-16,0,16}) {
                E6bCorridor c=dev50e6bAssessCorridor(level,v,side,distance,shift);
                candidates++;
                if(best==null||c.score()<best.score())best=c;
            }
            if(best!=null){chosen[k]=best;final E6bCorridor b=best;
                E6Stop stop=dev50e6bStop(level,b);
                LOG.info("[TC-DEV50E6B] ADAPTIVE_CORRIDOR village={} style={} side={} distance={} lateral={} center={},{} loaded={}/{} structure={} fluids={} blocked={} abrupt={} tight={} cuts={} supports={} terrainY={}..{} penalty={} station={} stopLevelCandidate={} gradeMismatch={} platformMismatch={} status=SEGMENT_ONLY_UNCERTIFIED",
                        v,style,b.side(),b.distance(),b.shift(),b.centerX(),b.centerZ(),b.loaded(),b.samples(),
                        b.structures(),b.fluids(),b.blocked(),b.steep(),b.shortGrades(),b.cuts(),b.supports(),
                        b.minY(),b.maxY(),b.score(),stop.site(),stop.levelCandidate(),stop.gradeMismatch(),stop.platformMismatch());
                src.sendSuccess(()->Component.literal("[E6b] "+b.side()+" corridor dist="+b.distance()
                        +" shift="+b.shift()+" wet="+b.fluids()+" abrupt="+b.steep()
                        +" village="+b.structures()+" blocked="+b.blocked()
                        +" stop="+(stop.levelCandidate()?"LEVEL_CANDIDATE":"NOT_SAFE")),false);
            }
            k++;
        }
        LOG.info("[TC-DEV50E6B] ADAPTIVE_SUMMARY village={} stableAnchor={} style={} candidates={} selectedCorridors={} connectedLoop=false gradesNative=false sweptClearance=false placement=DISABLED chunksForced=0",
                v,stable.get(),style,candidates,k);
        final int surveyedCandidates = candidates;
        src.sendSuccess(()->Component.literal("[E6b] "+style+". Surveyed "+surveyedCandidates
                +" corridor choices; independently chosen sections are NOT connected. Bridges/tunnels/slopes/turnouts and FULL train clearance NOT CERTIFIED. 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6c: READ-ONLY closed centerline survey, never a native track builder.
    // The quarter-rounds are geometric proxies. They do NOT claim a matching
    // Traincraft WIDE_V2 curve, slope, switch or five-car swept clearance.
    private record E6cLoop(int n,int e,int s,int w,long coarseScore,int coarseUnloaded,
                           int coarseWet,int coarseSteps) {}
    private record E6cAudit(int samples,int unloaded,int structures,int wet,
                            int blocked,int tooSteep,int crampedGrades,
                            int cuts,int supports,int minY,int maxY,
                            int cornerSamples,boolean endCloses) {}

    // Walk clockwise along four straights, then four R=16 quarter-rounds;
    // all four bends meet their neighboring straights tangentially by design.
    // This is geometric centerline continuity, NOT certified native rail ports.
    private static List<BlockPos> dev50e6cOutline(BlockPos village,int n,int e,int s,int w) {
        final int x0=village.getX()-w,x1=village.getX()+e;
        final int z0=village.getZ()-n,z1=village.getZ()+s;
        final int r=16;
        List<BlockPos> points=new ArrayList<>();
        // Starts top-left after rounding, clockwise on the north straight.
        dev50e6cStraight(points,x0+r,z0,x1-r,z0);
        dev50e6cArc(points,x1-r,z0+r,r,-Math.PI/2.0,0.0);
        dev50e6cStraight(points,x1,z0+r,x1,z1-r);
        dev50e6cArc(points,x1-r,z1-r,r,0.0,Math.PI/2.0);
        dev50e6cStraight(points,x1-r,z1,x0+r,z1);
        dev50e6cArc(points,x0+r,z1-r,r,Math.PI/2.0,Math.PI);
        dev50e6cStraight(points,x0,z1-r,x0,z0+r);
        dev50e6cArc(points,x0+r,z0+r,r,Math.PI,Math.PI*3.0/2.0);
        // Explicitly close the polygon, with no teleport or cross-village link.
        if(!points.isEmpty())points.add(points.get(0));
        return points;
    }
    private static void dev50e6cAdd(List<BlockPos> points,int x,int z) {
        if(!points.isEmpty()) {
            BlockPos p=points.get(points.size()-1);
            if(p.getX()==x && p.getZ()==z)return;
        }
        points.add(new BlockPos(x,0,z));
    }
    private static void dev50e6cStraight(List<BlockPos> points,int ax,int az,int bx,int bz) {
        int len=Math.max(Math.abs(bx-ax),Math.abs(bz-az));
        int steps=Math.max(1,(len+3)/4);
        for(int i=0;i<=steps;i++) {
            int x=ax+(int)Math.round((bx-ax)*(double)i/steps);
            int z=az+(int)Math.round((bz-az)*(double)i/steps);
            dev50e6cAdd(points,x,z);
        }
    }
    private static void dev50e6cArc(List<BlockPos> points,int cx,int cz,int r,double start,double end) {
        // Six intervals on a 16-block-radius bend. Adjacent jumps <=5 blocks.
        for(int i=0;i<=6;i++) {
            double a=start+(end-start)*i/6.0;
            dev50e6cAdd(points,cx+(int)Math.round(r*Math.cos(a)),
                    cz+(int)Math.round(r*Math.sin(a)));
        }
    }
    private static E6cLoop dev50e6cCoarse(ServerLevel level,BlockPos v,
                                           int n,int e,int s,int w,Map<Long,int[]> cache) {
        List<BlockPos> path=dev50e6cOutline(v,n,e,s,w);
        int unloaded=0,wet=0,steps=0,priorY=Integer.MIN_VALUE;
        long score=0;
        // Every fourth survey point; only check already loaded chunks.
        for(int i=0;i<path.size();i+=4) {
            BlockPos xy=path.get(i);
            // Many permutations reuse the same 2D sample coordinates; cache
            // loaded-only height/wet samples to keep the test bounded.
            long key=xy.asLong();
            int[] sample=cache.get(key);
            if(sample==null) {
                if(!level.hasChunkAt(xy))sample=new int[]{Integer.MIN_VALUE,0};
                else {
                    int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,xy.getX(),xy.getZ());
                    BlockPos at=new BlockPos(xy.getX(),height-1,xy.getZ());
                    sample=new int[]{height,level.getBlockState(at).getFluidState().isEmpty()?0:1};
                }
                cache.put(key,sample);
            }
            if(sample[0]==Integer.MIN_VALUE) {unloaded++;score+=200000L;priorY=Integer.MIN_VALUE;continue;}
            int y=sample[0];
            if(priorY!=Integer.MIN_VALUE) {
                int difference=Math.abs(y-priorY);
                if(difference>1) {steps++;score+=(long)(difference-1)*650L;}
            }
            priorY=y;
            if(sample[1]!=0){wet++;score+=8000L;}
        }
        // Favor less intrusive perimeters among otherwise similar candidates.
        score+=(long)(n+e+s+w)*2L;
        return new E6cLoop(n,e,s,w,score,unloaded,wet,steps);
    }
    private static E6cAudit dev50e6cAudit(ServerLevel level,List<BlockPos> path) {
        int n=path.size(),unloaded=0,structure=0,wet=0,blocked=0,steep=0,shortGrades=0;
        int cuts=0,supports=0,low=Integer.MAX_VALUE,high=Integer.MIN_VALUE,corner=0;
        int[] ground=new int[n],profile=new int[n];
        for(int i=0;i<n;i++) {
            BlockPos p=path.get(i);
            if(!level.hasChunkAt(p)){ground[i]=Integer.MIN_VALUE;unloaded++;continue;}
            ground[i]=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
        }
        for(int i=0;i<n;i++) profile[i]=ground[i]==Integer.MIN_VALUE
                ?Integer.MIN_VALUE:dev50e6bMedianLinear(ground,i,2);
        int lastGrade=-100;
        // Exclude duplicate closing coordinate in the scan. All block calls
        // happen only after the chunk is known loaded, including the width.
        for(int i=0;i<n-1;i++) {
            if(profile[i]==Integer.MIN_VALUE)continue;
            int y=profile[i];low=Math.min(low,y);high=Math.max(high,y);
            if(i>0 && profile[i-1]!=Integer.MIN_VALUE) {
                int d=Math.abs(y-profile[i-1]);
                if(d>1)steep++;
                if(d>0){if(i-lastGrade<4)shortGrades++;lastGrade=i;}
            }
            if(ground[i]-y>2)cuts++;
            if(y-ground[i]>2)supports++;
            BlockPos current=path.get(i);
            BlockPos before=path.get(i==0?n-2:i-1);
            BlockPos after=path.get(i+1);
            double tx=after.getX()-before.getX(),tz=after.getZ()-before.getZ();
            double length=Math.sqrt(tx*tx+tz*tz);
            if(length<0.001){blocked++;continue;}
            // Rounded corners get the same 7-wide envelope as straights,
            // but this is NOT a train-body swept-volume certificate.
            if((Math.abs(tx)>0.01 && Math.abs(tz)>0.01))corner++;
            boolean hasStructure=false,hasFluid=false,occupied=false;
            for(int offset=-3;offset<=3;offset++) {
                int x=current.getX()+(int)Math.round(-tz/length*offset);
                int z=current.getZ()+(int)Math.round(tx/length*offset);
                BlockPos base=new BlockPos(x,y,z);
                if(!level.hasChunkAt(base)){occupied=true;continue;}
                if(level.structureManager().getStructureWithPieceAt(base,StructureTags.VILLAGE).isValid()
                        ||level.structureManager().getStructureWithPieceAt(base.below(),StructureTags.VILLAGE).isValid())hasStructure=true;
                if(!level.getBlockState(base.below()).getFluidState().isEmpty())hasFluid=true;
                for(int h=0;h<5;h++) {
                    BlockPos at=base.above(h);
                    BlockState state=level.getBlockState(at);
                    if(!state.getFluidState().isEmpty())hasFluid=true;
                    if(!state.getCollisionShape(level,at).isEmpty())occupied=true;
                }
            }
            if(hasStructure)structure++;
            if(hasFluid)wet++;
            if(occupied)blocked++;
        }
        if(low==Integer.MAX_VALUE){low=0;high=0;}
        boolean closed=n>=2 && path.get(0).equals(path.get(n-1));
        return new E6cAudit(n-1,unloaded,structure,wet,blocked,steep,shortGrades,
                cuts,supports,low,high,corner,closed);
    }

    private static int dev50e6cConnectCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6c connected-route survey only in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No loaded verified vanilla village within 128 blocks; NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> anchor=dev50e6bVillageStartAnchor(level,village,128);
        if(anchor.isEmpty()){
            src.sendFailure(Component.literal("Missing loaded natural village structure anchor; NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,anchor.get());
        if(style==E6bStyle.NATURAL_VANILLA) {
            LOG.info("[TC-DEV50E6C] CONNECT_SKIP village={} anchor={} type={} noTracks=true action=READ_ONLY",village,anchor.get(),style);
            src.sendSuccess(()->Component.literal("[E6c] NATURAL_VANILLA: no railway route or station proposals; 0 blocks changed."),false);
            return 1;
        }
        // 4^4 = 256 centered permutations, strict upper bound, never fetch
        // ungenerated chunks. Rank by terrain-only coarse sampling first.
        E6cLoop best=null;int candidates=0;
        Map<Long,int[]> terrainCache=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}) {
                E6cLoop route=dev50e6cCoarse(level,village,n,e,s,w,terrainCache);
                candidates++;
                if(best==null||route.coarseScore()<best.coarseScore())best=route;
            }
        if(best==null)return 0;
        List<BlockPos> centerline=dev50e6cOutline(village,best.n(),best.e(),best.s(),best.w());
        E6cAudit audit=dev50e6cAudit(level,centerline);
        boolean envelopeClear=audit.endCloses() && audit.unloaded()==0 && audit.structures()==0
                && audit.wet()==0 && audit.blocked()==0 && audit.tooSteep()==0
                && audit.crampedGrades()==0 && audit.cuts()==0 && audit.supports()==0;
        // A coarse geometric score is NOT a safety certificate: no Traincraft
        // WIDE_V2 mesh/ports, native slope assignment, curvature/swept-clearance
        // or train movement tested. Strictly DISABLED regardless of envelope.
        String verdict=envelopeClear?"GEOMETRIC_SCREEN_ONLY":"REJECTED_BY_ENVELOPE";
        LOG.info("[TC-DEV50E6C] CONNECT_CHECK village={} anchor={} style={} candidates={} bounds=N{} E{} S{} W{} centerlinePoints={} closedGeometric={} nativeTrackPorts=false nativeTurns=false nativeSlopes=false consistSweep=false score={} coarseUniqueSamples={} unloaded={} structure={} wet={} blocked={} abrupt={} tightGrades={} cuts={} supports={} terrainY={}..{} cornerSamples={} verdict={} placement=DISABLED chunksForced=0 blocksChanged=0",
                village,anchor.get(),style,candidates,best.n(),best.e(),best.s(),best.w(),
                audit.samples(),audit.endCloses(),best.coarseScore(),terrainCache.size(),audit.unloaded(),
                audit.structures(),audit.wet(),audit.blocked(),audit.tooSteep(),audit.crampedGrades(),
                audit.cuts(),audit.supports(),audit.minY(),audit.maxY(),audit.cornerSamples(),verdict);
        final E6cLoop chosen=best;
        src.sendSuccess(()->Component.literal("[E6c] "+style+": 256 closed centerline layouts evaluated; best N="+chosen.n()
                +" E="+chosen.e()+" S="+chosen.s()+" W="+chosen.w()+"; rounded geometric corners."),false);
        src.sendSuccess(()->Component.literal("[E6c] envelope: unloaded="+audit.unloaded()
                +" protected="+audit.structures()+" water="+audit.wet()+" blocked="+audit.blocked()
                +" abrupt="+audit.tooSteep()+" tightGrades="+audit.crampedGrades()),false);
        src.sendSuccess(()->Component.literal("[E6c] "+verdict+". GEOMETRIC loop closure="+audit.endCloses()
                +". Native rail connections, curve/slope geometry, and 5-car swept clearance NOT VERIFIED. 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6d: bounded, READ-ONLY detour feasibility screening for a real
    // centerline; a grid walk is not a native Traincraft track placement plan.
    // No world writes, no chunk loads, no async work, and no path stored to saves.
    private record E6dCell(int y,String reason) {
        boolean passable() { return reason==null; }
    }
    private record E6dState(int x,int z,int heading) {}
    private record E6dStep(E6dState state,int cost,int estimate) {}
    private record E6dSearch(List<BlockPos> path,int expanded,int probes,boolean exhausted) {}
    private record E6dShape(int bends,int duplicatePoints,int gaps,boolean closed) {}
    private record E6dNative(boolean normalRail,boolean curveLeft,boolean curveRight,
                             boolean switchLeft,boolean switchRight) {
        boolean catalogReady() {return normalRail&&curveLeft&&curveRight&&switchLeft&&switchRight;}
    }
    private static final int[] E6D_DX={1,0,-1,0};
    private static final int[] E6D_DZ={0,1,0,-1};

    private static E6dCell dev50e6dCell(ServerLevel level,BlockPos village,
                                        int x,int z,Map<Long,E6dCell> cache) {
        long key=new BlockPos(x,0,z).asLong();
        E6dCell old=cache.get(key);
        if(old!=null)return old;
        E6dCell result;
        long dx=x-village.getX(), dz=z-village.getZ();
        // A separate no-rails village protection ring. The original network
        // uses 48 + swept clearance; 56 is a conservative survey buffer.
        if(dx*dx+dz*dz<56L*56L) result=new E6dCell(0,"VILLAGE_BUFFER");
        else {
            BlockPos center=new BlockPos(x,0,z);
            if(!level.hasChunkAt(center))result=new E6dCell(0,"UNLOADED");
            else {
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                String failure=null;
                // The cross-shaped 7-wide envelope excludes obvious trackbed
                // hazards. Final whole-path audit is stricter; neither models
                // the accepted five-car swept train profile yet.
                for(int w=-3;w<=3 && failure==null;w++)for(int axis=0;axis<2;axis++) {
                    if(w==0&&axis==1)continue;
                    int xx=x+(axis==0?w:0),zz=z+(axis==1?w:0);
                    BlockPos p=new BlockPos(xx,y,zz);
                    if(!level.hasChunkAt(p)){failure="UNLOADED";break;}
                    if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid()
                            ||level.structureManager().getStructureWithPieceAt(p.below(),StructureTags.VILLAGE).isValid()) {
                        failure="STRUCTURE";break;
                    }
                    if(!level.getBlockState(p.below()).getFluidState().isEmpty()) {
                        failure="WATER";break;
                    }
                    for(int h=0;h<5;h++) {
                        BlockPos air=p.above(h);
                        BlockState state=level.getBlockState(air);
                        if(!state.getFluidState().isEmpty()){failure="WATER";break;}
                        if(!state.getCollisionShape(level,air).isEmpty()){failure="OBSTRUCTED";break;}
                    }
                    if(failure!=null)break;
                }
                result=new E6dCell(y,failure);
            }
        }
        cache.put(key,result);
        return result;
    }

    private static BlockPos dev50e6dAnchor(ServerLevel level,BlockPos village,
                                          String side,int radius,Map<Long,E6dCell> cache) {
        // Prefer each cardinal side's center, then slide along its edge and
        // finally outward. Everything remains a loaded-only survey.
        int[] shifts={0,8,-8,16,-16,24,-24,32,-32};
        for(int extra:new int[]{0,16,32})for(int shift:shifts) {
            int x=village.getX(),z=village.getZ();
            if("N".equals(side)){x+=shift;z-=radius+extra;}
            else if("E".equals(side)){x+=radius+extra;z+=shift;}
            else if("S".equals(side)){x+=shift;z+=radius+extra;}
            else {x-=radius+extra;z+=shift;}
            if(dev50e6dCell(level,village,x,z,cache).passable())return new BlockPos(x,0,z);
        }
        return null;
    }

    private static int dev50e6dHeuristic(int x,int z,BlockPos end) {
        return (Math.abs(end.getX()-x)+Math.abs(end.getZ()-z))/4*10;
    }
    private static E6dSearch dev50e6dRoute(ServerLevel level,BlockPos village,
                                           BlockPos start,BlockPos end,
                                           Map<Long,E6dCell> cells) {
        // A* considers 4-block cardinal centerline hops, penalizes bends and
        // grades, and refuses blocked, wet, village and unloaded nodes. The
        // resulting 90-degree grid turns are NOT mapped to native curves.
        java.util.PriorityQueue<E6dStep> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6dStep::estimate).thenComparingInt(E6dStep::cost));
        Map<E6dState,Integer> best=new HashMap<>();
        Map<E6dState,E6dState> parent=new HashMap<>();
        E6dState initial=new E6dState(start.getX(),start.getZ(),-1);
        best.put(initial,0);
        open.add(new E6dStep(initial,0,dev50e6dHeuristic(start.getX(),start.getZ(),end)));
        int expanded=0,maxExpanded=7000;
        final int maxX=Math.max(Math.abs(start.getX()-village.getX()),
                Math.abs(end.getX()-village.getX()))+40;
        final int maxZ=Math.max(Math.abs(start.getZ()-village.getZ()),
                Math.abs(end.getZ()-village.getZ()))+40;
        E6dState found=null;
        while(!open.isEmpty() && expanded<maxExpanded) {
            E6dStep current=open.poll();
            E6dState p=current.state();
            if(current.cost()!=best.getOrDefault(p,Integer.MAX_VALUE))continue;
            expanded++;
            if(p.x()==end.getX()&&p.z()==end.getZ()){found=p;break;}
            E6dCell ground=dev50e6dCell(level,village,p.x(),p.z(),cells);
            if(!ground.passable())continue;
            for(int d=0;d<4;d++) {
                if(p.heading()>=0 && ((d+2)%4)==p.heading())continue; // no reversal
                int x=p.x()+4*E6D_DX[d],z=p.z()+4*E6D_DZ[d];
                if(Math.abs(x-village.getX())>maxX||Math.abs(z-village.getZ())>maxZ)continue;
                E6dCell next=dev50e6dCell(level,village,x,z,cells);
                if(!next.passable())continue;
                int rise=Math.abs(next.y()-ground.y());
                if(rise>1)continue; // native grade pitch not guessed
                E6dState q=new E6dState(x,z,d);
                int bend=(p.heading()>=0&&p.heading()!=d)?60:0;
                int cost=current.cost()+10+bend+rise*30;
                if(cost>=best.getOrDefault(q,Integer.MAX_VALUE))continue;
                best.put(q,cost);parent.put(q,p);
                open.add(new E6dStep(q,cost,cost+dev50e6dHeuristic(x,z,end)));
            }
        }
        if(found==null)return new E6dSearch(List.of(),expanded,cells.size(),expanded>=maxExpanded);
        List<BlockPos> reverse=new ArrayList<>();
        for(E6dState at=found;at!=null;at=parent.get(at))reverse.add(new BlockPos(at.x(),0,at.z()));
        java.util.Collections.reverse(reverse);
        return new E6dSearch(reverse,expanded,cells.size(),false);
    }

    private static List<BlockPos> dev50e6dDense(List<BlockPos> sparse) {
        List<BlockPos> dense=new ArrayList<>();
        if(sparse.isEmpty())return dense;
        dense.add(sparse.get(0));
        for(int i=1;i<sparse.size();i++) {
            BlockPos a=sparse.get(i-1),b=sparse.get(i);
            int dx=Integer.signum(b.getX()-a.getX()),dz=Integer.signum(b.getZ()-a.getZ());
            int steps=Math.max(Math.abs(b.getX()-a.getX()),Math.abs(b.getZ()-a.getZ()));
            for(int n=1;n<=steps;n++)dense.add(new BlockPos(a.getX()+n*dx,0,a.getZ()+n*dz));
        }
        return dense;
    }
    private static E6dShape dev50e6dShape(List<BlockPos> points) {
        if(points.size()<5)return new E6dShape(0,0,0,false);
        boolean closed=points.get(0).equals(points.get(points.size()-1));
        Set<Long> unique=new java.util.HashSet<>();
        int bends=0,gaps=0,duplicates=0;
        int prevDx=0,prevDz=0;
        for(int i=0;i<points.size();i++) {
            BlockPos p=points.get(i);
            if(i<points.size()-1 && !unique.add(p.asLong()))duplicates++;
            if(i==0)continue;
            BlockPos last=points.get(i-1);
            int dx=p.getX()-last.getX(),dz=p.getZ()-last.getZ();
            if(Math.abs(dx)+Math.abs(dz)!=1){gaps++;continue;}
            if(i>1 && (dx!=prevDx||dz!=prevDz))bends++;
            prevDx=dx;prevDz=dz;
        }
        if(closed && points.size()>3) {
            BlockPos last=points.get(points.size()-2),first=points.get(0),next=points.get(1);
            if(first.getX()-last.getX()!=next.getX()-first.getX()
                    ||first.getZ()-last.getZ()!=next.getZ()-first.getZ())bends++;
        }
        return new E6dShape(bends,duplicates,gaps,closed);
    }
    private static E6dNative dev50e6dNative() {
        // Inspect the actual accepted block-spec registry, not invented ports.
        // Existing native S-curves are NOT 90-degree route corners. Do not
        // mislabel their registration as full loop connectivity certification.
        var left=TCBlocks.PARALLEL_CURVE_MEDIUM_LEFT_SEGMENT.get();
        var right=TCBlocks.PARALLEL_CURVE_MEDIUM_RIGHT_SEGMENT.get();
        var switchLeft=TCBlocks.SWITCH_PARALLEL_LEFT_SEGMENT.get();
        var switchRight=TCBlocks.SWITCH_PARALLEL_RIGHT_SEGMENT.get();
        return new E6dNative(TCBlocks.TRACK_NORMAL.get() instanceof RailBlock,
                left instanceof traincraft.block.track.LegacyBatchRailBlock l
                    && l.getSpec()==LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT,
                right instanceof traincraft.block.track.LegacyBatchRailBlock r
                    && r.getSpec()==LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT,
                switchLeft instanceof LegacyBatchSwitchRailBlock ls
                    && ls.getSpec()==LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT,
                switchRight instanceof LegacyBatchSwitchRailBlock rs
                    && rs.getSpec()==LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT);
    }
    private static int dev50e6dDetourCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6d detour survey only in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("No verified loaded natural village within 128 blocks. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> anchor=dev50e6bVillageStartAnchor(level,village,128);
        if(anchor.isEmpty()){
            src.sendFailure(Component.literal("No loaded vanilla village structure anchor. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,anchor.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6D] DETOUR_SKIP village={} style={} reason=NATURAL_NO_RAILS action=READ_ONLY",village,style);
            src.sendSuccess(()->Component.literal("[E6d] NATURAL_VANILLA: no railway proposal; 0 blocks changed."),false);
            return 1;
        }
        long tickStart=System.nanoTime();
        E6cLoop best=null;
        Map<Long,int[]> coarseCache=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}){
                E6cLoop c=dev50e6cCoarse(level,village,n,e,s,w,coarseCache);
                if(best==null||c.coarseScore()<best.coarseScore())best=c;
            }
        if(best==null)return 0;
        Map<Long,E6dCell> cells=new HashMap<>();
        BlockPos[] waypoints={dev50e6dAnchor(level,village,"N",best.n(),cells),
                dev50e6dAnchor(level,village,"E",best.e(),cells),
                dev50e6dAnchor(level,village,"S",best.s(),cells),
                dev50e6dAnchor(level,village,"W",best.w(),cells)};
        String[] sides={"N","E","S","W"};
        int missing=0,expanded=0,unrouted=0,completedLegs=0;
        for(int i=0;i<4;i++)if(waypoints[i]==null)missing++;
        List<BlockPos> sparse=new ArrayList<>();
        if(missing==0) {
            for(int i=0;i<4;i++){
                E6dSearch leg=dev50e6dRoute(level,village,waypoints[i],waypoints[(i+1)%4],cells);
                expanded+=leg.expanded();
                if(leg.path().isEmpty()){
                    unrouted++;
                    LOG.info("[TC-DEV50E6D] DETOUR_LEG village={} from={} to={} status=NO_LOADED_CLEAR_DETOUR expanded={} limitReached={}",
                            village,sides[i],sides[(i+1)%4],leg.expanded(),leg.exhausted());
                    break;
                }
                LOG.info("[TC-DEV50E6D] DETOUR_LEG village={} from={} to={} sparsePoints={} expanded={} status=CANDIDATE_ONLY",
                        village,sides[i],sides[(i+1)%4],leg.path().size(),leg.expanded());
                completedLegs++;
                for(int j=0;j<leg.path().size();j++)if(i==0||j>0)sparse.add(leg.path().get(j));
            }
        }
        boolean whole=missing==0&&unrouted==0&&sparse.size()>4;
        List<BlockPos> dense=whole?dev50e6dDense(sparse):List.of();
        E6dShape shape=dev50e6dShape(dense);
        E6cAudit audit=whole?dev50e6cAudit(level,dense):null;
        E6dNative nativeCatalog=dev50e6dNative();
        boolean envelope=whole && shape.closed() && shape.gaps()==0&&shape.duplicatePoints()==0
                && audit.unloaded()==0&&audit.structures()==0&&audit.wet()==0&&audit.blocked()==0
                && audit.tooSteep()==0&&audit.crampedGrades()==0&&audit.cuts()==0&&audit.supports()==0;
        // Absolutely no native route claim without exact 90-degree Traincraft
        // piece ports, geometric curve footprints, slopes and consist sweeps.
        String verdict=!whole?"NO_COMPLETE_LOADED_DETOUR":!envelope?"REJECTED_BY_DETAILED_ENVELOPE":"GEOMETRIC_SCREEN_ONLY";
        long elapsedMs=(System.nanoTime()-tickStart)/1000000L;
        LOG.info("[TC-DEV50E6D] DETOUR_CHECK village={} style={} candidates=256 anchorsFound={} routedLegs={} expanded={} cachedGround={} routePoints={} closed={} gaps={} selfOverlaps={} turns={} nativeCatalog={} native90TurnPlacement=false nativeSlopes=false fiveCarSweep=false clearEnvelope={} unloaded={} villageStructure={} water={} blocked={} abrupt={} tightGrades={} verdict={} elapsedMs={} placement=DISABLED chunksForced=0 blocksChanged=0",
                village,style,4-missing,completedLegs,expanded,cells.size(),
                dense.size(),shape.closed(),shape.gaps(),shape.duplicatePoints(),shape.bends(),nativeCatalog.catalogReady(),
                envelope,audit==null?-1:audit.unloaded(),audit==null?-1:audit.structures(),
                audit==null?-1:audit.wet(),audit==null?-1:audit.blocked(),audit==null?-1:audit.tooSteep(),
                audit==null?-1:audit.crampedGrades(),verdict,elapsedMs);
        final int foundAnchors=4-missing,attemptedNodes=expanded,storedSamples=cells.size(),routePoints=dense.size();
        src.sendSuccess(()->Component.literal("[E6d] "+style+": detour anchors="+foundAnchors+"/4; nodes explored="+attemptedNodes
                +" cached="+storedSamples+" closedCandidate="+shape.closed()+" samples="+routePoints),false);
        src.sendSuccess(()->Component.literal("[E6d] Native straight / S-curve / turnout registry="+nativeCatalog.catalogReady()
                +"; 90-degree corners, slopes and 5-car sweep NOT CERTIFIED."),false);
        src.sendSuccess(()->Component.literal("[E6d] "+verdict+"; no construction, no chunks forced, 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6e: READ-ONLY multi-anchor route diagnosis. Existing e6d solver,
    // worldgen, track geometry, and rolling-stock control remain unmodified.
    // All probe operations are restricted to already-loaded chunks and never
    // claim native track piece compatibility or a five-car swept certificate.
    private record E6eAnchor(BlockPos point,int extra,int shift,int neighbors) {}
    private record E6eSide(String side,int sampled,int viable,int isolated,
                           int villageBuffer,int unloaded,int villageStructure,
                           int wet,int blocked,List<E6eAnchor> selected) {}
    private record E6ePair(E6dSearch search,int expanded,boolean capped) {}
    private record E6eChoice(int[] picks,int legs,int cost,int overlaps) {}

    private static final String[] E6E_SIDES={"N","E","S","W"};
    // At most four candidate anchors/side => 64 A* searches and 256
    // complete combinations. These limits are deliberate server safeguards.
    private static E6eSide dev50e6eAnchors(ServerLevel level,BlockPos village,
                                             String side,int radius,
                                             Map<Long,E6dCell> cells) {
        final int[] shifts={0,8,-8,16,-16,24,-24,32,-32,40,-40,48,-48};
        final int[] extras={0,16,32,48,64};
        int attempted=0,viable=0,isolated=0,buffer=0,unloaded=0,structure=0,wet=0,blocked=0;
        List<E6eAnchor> passed=new ArrayList<>();
        for(int extra:extras)for(int shift:shifts) {
            int x=village.getX(),z=village.getZ();
            switch(side) {
                case "N" -> { x+=shift;z-=radius+extra; }
                case "E" -> { x+=radius+extra;z+=shift; }
                case "S" -> { x+=shift;z+=radius+extra; }
                default -> { x-=radius+extra;z+=shift; }
            }
            attempted++;
            E6dCell cell=dev50e6dCell(level,village,x,z,cells);
            if(!cell.passable()) {
                switch(cell.reason()) {
                    case "VILLAGE_BUFFER" -> buffer++;
                    case "UNLOADED" -> unloaded++;
                    case "STRUCTURE" -> structure++;
                    case "WATER" -> wet++;
                    default -> blocked++;
                }
                continue;
            }
            viable++;
            int exits=0;
            for(int d=0;d<4;d++) {
                E6dCell neighbor=dev50e6dCell(level,village,x+4*E6D_DX[d],z+4*E6D_DZ[d],cells);
                if(neighbor.passable() && Math.abs(cell.y()-neighbor.y())<=1)exits++;
            }
            if(exits==0){isolated++;continue;}
            passed.add(new E6eAnchor(new BlockPos(x,0,z),extra,shift,exits));
        }
        // Prefer well connected and nearby anchors, while retaining lateral
        // diversity. The candidate shortlist is bounded and deterministic.
        passed.sort(Comparator.comparingInt((E6eAnchor a)->
                a.extra()*2+Math.abs(a.shift())*2+(4-a.neighbors())*16)
                .thenComparingInt(E6eAnchor::extra)
                .thenComparingInt(a->Math.abs(a.shift()))
                .thenComparingInt(E6eAnchor::shift));
        List<E6eAnchor> selected=new ArrayList<>();
        for(E6eAnchor a:passed) {
            boolean duplicate=false;
            for(E6eAnchor existing:selected) {
                int dx=a.point().getX()-existing.point().getX();
                int dz=a.point().getZ()-existing.point().getZ();
                if(dx*dx+dz*dz<16*16){duplicate=true;break;}
            }
            if(!duplicate)selected.add(a);
            if(selected.size()==4)break;
        }
        return new E6eSide(side,attempted,viable,isolated,buffer,unloaded,structure,wet,blocked,
                List.copyOf(selected));
    }

    private static int dev50e6eHeuristic(int x,int z,BlockPos end) {
        return (Math.abs(end.getX()-x)+Math.abs(end.getZ()-z))/4*10;
    }
    private static E6ePair dev50e6eRoute(ServerLevel level,BlockPos village,
                                           BlockPos start,BlockPos end,
                                           Map<Long,E6dCell> cells) {
        // This search uses the original e6d conservative 7-wide, 5-high
        // collision screen. It widens the bounded search area and compares
        // alternative endpoints. No unsafe cell, grade or U-turn is allowed.
        java.util.PriorityQueue<E6dStep> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6dStep::estimate).thenComparingInt(E6dStep::cost));
        Map<E6dState,Integer> best=new HashMap<>();
        Map<E6dState,E6dState> parent=new HashMap<>();
        E6dState first=new E6dState(start.getX(),start.getZ(),-1);
        best.put(first,0);
        open.add(new E6dStep(first,0,dev50e6eHeuristic(start.getX(),start.getZ(),end)));
        int expanded=0;
        final int cap=1400;
        final int rangeX=Math.max(Math.abs(start.getX()-village.getX()),
                Math.abs(end.getX()-village.getX()))+64;
        final int rangeZ=Math.max(Math.abs(start.getZ()-village.getZ()),
                Math.abs(end.getZ()-village.getZ()))+64;
        E6dState found=null;
        while(!open.isEmpty() && expanded<cap) {
            E6dStep current=open.poll();
            E6dState p=current.state();
            if(current.cost()!=best.getOrDefault(p,Integer.MAX_VALUE))continue;
            expanded++;
            if(p.x()==end.getX() && p.z()==end.getZ()) {found=p;break;}
            E6dCell ground=dev50e6dCell(level,village,p.x(),p.z(),cells);
            if(!ground.passable())continue;
            for(int d=0;d<4;d++) {
                if(p.heading()>=0 && ((d+2)%4)==p.heading())continue;
                int x=p.x()+4*E6D_DX[d],z=p.z()+4*E6D_DZ[d];
                if(Math.abs(x-village.getX())>rangeX || Math.abs(z-village.getZ())>rangeZ)continue;
                E6dCell next=dev50e6dCell(level,village,x,z,cells);
                if(!next.passable())continue;
                int rise=Math.abs(next.y()-ground.y());
                if(rise>1)continue;
                E6dState q=new E6dState(x,z,d);
                int bend=(p.heading()>=0 && p.heading()!=d)?60:0;
                int cost=current.cost()+10+bend+rise*30;
                if(cost>=best.getOrDefault(q,Integer.MAX_VALUE))continue;
                best.put(q,cost);parent.put(q,p);
                open.add(new E6dStep(q,cost,cost+dev50e6eHeuristic(x,z,end)));
            }
        }
        if(found==null)return new E6ePair(new E6dSearch(List.of(),expanded,cells.size(),
                expanded>=cap),expanded,expanded>=cap);
        List<BlockPos> path=new ArrayList<>();
        for(E6dState at=found;at!=null;at=parent.get(at))path.add(new BlockPos(at.x(),0,at.z()));
        java.util.Collections.reverse(path);
        return new E6ePair(new E6dSearch(path,expanded,cells.size(),false),expanded,false);
    }

    private static List<BlockPos> dev50e6eJoin(E6ePair[][][] legs,int[] picks) {
        List<BlockPos> sparse=new ArrayList<>();
        for(int side=0;side<4;side++) {
            E6ePair edge=legs[side][picks[side]][picks[(side+1)%4]];
            if(edge==null || edge.search().path().isEmpty())return List.of();
            List<BlockPos> path=edge.search().path();
            for(int j=0;j<path.size();j++)if(side==0 || j>0)sparse.add(path.get(j));
        }
        return sparse;
    }
    private static int dev50e6eOverlaps(List<BlockPos> sparse) {
        Set<Long> seen=new java.util.HashSet<>();int duplicate=0;
        for(int i=0;i<sparse.size()-1;i++)if(!seen.add(sparse.get(i).asLong()))duplicate++;
        return duplicate;
    }
    private static int dev50e6eAnchorCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6e anchor survey only in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));
            return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No verified loaded vanilla village within 128 blocks. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> structureAnchor=dev50e6bVillageStartAnchor(level,village,128);
        if(structureAnchor.isEmpty()) {
            src.sendFailure(Component.literal("No loaded vanilla village structure anchor. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,structureAnchor.get());
        if(style==E6bStyle.NATURAL_VANILLA) {
            LOG.info("[TC-DEV50E6E] ANCHOR_SKIP village={} style={} reason=NATURAL_NO_RAILS action=READ_ONLY blocksChanged=0",
                    village,style);
            src.sendSuccess(()->Component.literal("[E6e] NATURAL_VANILLA: no route/station proposals; 0 blocks changed."),false);
            return 1;
        }
        long begin=System.nanoTime();
        E6cLoop best=null;
        Map<Long,int[]> terrainCache=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}) {
                E6cLoop candidate=dev50e6cCoarse(level,village,n,e,s,w,terrainCache);
                if(best==null||candidate.coarseScore()<best.coarseScore())best=candidate;
            }
        if(best==null)return 0;
        int[] offsets={best.n(),best.e(),best.s(),best.w()};
        Map<Long,E6dCell> cells=new HashMap<>();
        E6eSide[] sides=new E6eSide[4];
        int minCandidates=4;
        for(int i=0;i<4;i++) {
            sides[i]=dev50e6eAnchors(level,village,E6E_SIDES[i],offsets[i],cells);
            minCandidates=Math.min(minCandidates,sides[i].selected().size());
            E6eSide a=sides[i];
            LOG.info("[TC-DEV50E6E] ANCHOR_SCREEN village={} side={} radius={} attempts={} passable={} isolated={} villageBuffer={} unloaded={} structure={} water={} obstructed={} shortlisted={} selected={}",
                    village,a.side(),offsets[i],a.sampled(),a.viable(),a.isolated(),a.villageBuffer(),
                    a.unloaded(),a.villageStructure(),a.wet(),a.blocked(),a.selected().size(),
                    a.selected().stream().map(v->v.point().getX()+":"+v.point().getZ()+":shift"+v.shift()+":extra"+v.extra()+":exits"+v.neighbors()).toList());
        }
        // Pairwise route cache: each successful 4-leg choice uses the EXACT
        // same anchors for both its incoming and outgoing segments.
        E6ePair[][][] edges=new E6ePair[4][4][4];
        int tested=0,routed=0,nodes=0,capped=0;
        for(int side=0;side<4;side++) {
            List<E6eAnchor> from=sides[side].selected(),to=sides[(side+1)%4].selected();
            for(int a=0;a<from.size();a++)for(int b=0;b<to.size();b++) {
                E6ePair pair=dev50e6eRoute(level,village,from.get(a).point(),to.get(b).point(),cells);
                edges[side][a][b]=pair;
                tested++;nodes+=pair.expanded();
                if(pair.capped())capped++;
                if(!pair.search().path().isEmpty())routed++;
            }
        }
        E6eChoice bestChoice=null;
        for(int n=0;n<sides[0].selected().size();n++)for(int e=0;e<sides[1].selected().size();e++)
            for(int s=0;s<sides[2].selected().size();s++)for(int w=0;w<sides[3].selected().size();w++) {
                int[] picks={n,e,s,w};int legs=0,cost=0;
                for(int j=0;j<4;j++) {
                    E6ePair edge=edges[j][picks[j]][picks[(j+1)%4]];
                    if(edge!=null && !edge.search().path().isEmpty()) {
                        legs++;
                        cost+=edge.search().path().size();
                    }
                }
                List<BlockPos> sparse=legs==4?dev50e6eJoin(edges,picks):List.of();
                int overlap=legs==4?dev50e6eOverlaps(sparse):0;
                E6eChoice option=new E6eChoice(picks,legs,cost,overlap);
                if(bestChoice==null || option.legs()>bestChoice.legs()
                        || (option.legs()==bestChoice.legs() && option.overlaps()<bestChoice.overlaps())
                        || (option.legs()==bestChoice.legs() && option.overlaps()==bestChoice.overlaps()
                            && option.cost()<bestChoice.cost()))bestChoice=option;
            }
        int bestLegs=bestChoice==null?0:bestChoice.legs();
        boolean whole=bestLegs==4;
        List<BlockPos> sparse=whole?dev50e6eJoin(edges,bestChoice.picks()):List.of();
        List<BlockPos> dense=whole?dev50e6dDense(sparse):List.of();
        E6dShape shape=dev50e6dShape(dense);
        E6cAudit audit=whole?dev50e6cAudit(level,dense):null;
        E6dNative nativeCatalog=dev50e6dNative();
        boolean envelope=whole&&shape.closed()&&shape.gaps()==0&&shape.duplicatePoints()==0
                &&audit.unloaded()==0&&audit.structures()==0&&audit.wet()==0&&audit.blocked()==0
                &&audit.tooSteep()==0&&audit.crampedGrades()==0&&audit.cuts()==0&&audit.supports()==0;
        String verdict=!whole?"NO_COMPLETE_LOADED_DETOUR":!envelope?
                "REJECTED_BY_DETAILED_ENVELOPE":"GEOMETRIC_SCREEN_ONLY";
        long ms=(System.nanoTime()-begin)/1000000L;
        LOG.info("[TC-DEV50E6E] ANCHOR_SUMMARY village={} style={} minAnchorChoices={} pairSearches={} pairConnections={} expanded={} limited={} cachedGround={} bestRoutedLegs={} selectedCost={} selectedOverlaps={} routePoints={} closed={} selfOverlap={} turns={} nativeCatalog={} native90TurnPlacement=false nativeSlopes=false fiveCarSweep=false unloaded={} villageStructure={} water={} blocked={} abrupt={} tightGrades={} verdict={} elapsedMs={} placement=DISABLED chunksForced=0 blocksChanged=0",
                village,style,minCandidates,tested,routed,nodes,capped,cells.size(),bestLegs,
                bestChoice==null?-1:bestChoice.cost(),bestChoice==null?-1:bestChoice.overlaps(),
                dense.size(),shape.closed(),shape.duplicatePoints(),shape.bends(),nativeCatalog.catalogReady(),
                audit==null?-1:audit.unloaded(),audit==null?-1:audit.structures(),
                audit==null?-1:audit.wet(),audit==null?-1:audit.blocked(),audit==null?-1:audit.tooSteep(),
                audit==null?-1:audit.crampedGrades(),verdict,ms);
        final int legs=bestLegs,total=tested,ok=routed,exp=nodes,limits=capped,cache=cells.size();
        src.sendSuccess(()->Component.literal("[E6e] "+style+": anchor choices N/E/S/W="
                +sides[0].selected().size()+"/"+sides[1].selected().size()+"/"
                +sides[2].selected().size()+"/"+sides[3].selected().size()
                +"; best connected legs="+legs+"/4"),false);
        src.sendSuccess(()->Component.literal("[E6e] Pairs="+ok+"/"+total+" explored="+exp
                +" capped="+limits+" cachedGround="+cache+"; see log for per-side rejection reasons."),false);
        src.sendSuccess(()->Component.literal("[E6e] "+verdict+"; native 90-degree curves/slopes/five-car sweep UNVERIFIED."
                +" Construction DISABLED; 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6f: STRICTLY READ-ONLY pathfinding failure characterization.
    // Not a terrain modification, native track planner, grade authorizer,
    // bridge or tunnel planner, or five-car clearance certificate.
    private record E6fPoint(int x,int z,int direction) {}
    private record E6fQueue(E6fPoint point,int cost,int estimate) {}
    private record E6fProbe(boolean reached,int expanded,boolean capped,int nearestBlocks,
                             int[] rejected,int steps,int bends,int firstDirection,int lastDirection) {}
    private static final int[] E6F_DX={1,1,0,-1,-1,-1,0,1};
    private static final int[] E6F_DZ={0,1,1,1,0,-1,-1,-1};
    // Counters count REJECTED SEARCH TRANSITIONS, not unique physical blocks.
    // [0] buffer [1] unloaded [2] structure [3] fluid [4] collision,
    // [5] grade [6] diagonal flank [7] bounds [8] forbidden turn.
    private static String dev50e6fCounts(int[] r) {
        return "buffer="+r[0]+" unloaded="+r[1]+" structure="+r[2]
                +" fluid="+r[3]+" obstructed="+r[4]+" grade="+r[5]
                +" diagonalFlank="+r[6]+" bounds="+r[7]+" turn="+r[8];
    }
    private static void dev50e6fCountCell(int[] r,E6dCell cell) {
        switch(cell.reason()) {
            case "VILLAGE_BUFFER" -> r[0]++;
            case "UNLOADED" -> r[1]++;
            case "STRUCTURE" -> r[2]++;
            case "WATER" -> r[3]++;
            default -> r[4]++;
        }
    }
    private static int dev50e6fHeuristic(int x,int z,BlockPos end,boolean flexible) {
        int a=Math.abs(end.getX()-x)/4,b=Math.abs(end.getZ()-z)/4;
        return flexible?14*Math.min(a,b)+10*Math.abs(a-b):10*(a+b);
    }
    private static int dev50e6fClose(int x,int z,BlockPos goal) {
        return Math.abs(x-goal.getX())+Math.abs(z-goal.getZ());
    }
    private static E6fProbe dev50e6fProbe(ServerLevel level,BlockPos village,BlockPos start,
                                              BlockPos end,boolean flexible,
                                              Map<Long,E6dCell> groundCache) {
        int[] rejected=new int[9];
        // Grid mismatch would produce an impossible goal in the 4-block solver.
        if(Math.floorMod(start.getX()-end.getX(),4)!=0
                ||Math.floorMod(start.getZ()-end.getZ(),4)!=0) {
            return new E6fProbe(false,0,false,dev50e6fClose(start.getX(),start.getZ(),end),
                    rejected,0,0,-1,-1);
        }
        E6dCell initial=dev50e6dCell(level,village,start.getX(),start.getZ(),groundCache);
        E6dCell target=dev50e6dCell(level,village,end.getX(),end.getZ(),groundCache);
        if(!initial.passable()||!target.passable()) {
            if(!initial.passable())dev50e6fCountCell(rejected,initial);
            if(!target.passable())dev50e6fCountCell(rejected,target);
            return new E6fProbe(false,0,false,dev50e6fClose(start.getX(),start.getZ(),end),
                    rejected,0,0,-1,-1);
        }
        final int rangeX=Math.max(Math.abs(start.getX()-village.getX()),
                Math.abs(end.getX()-village.getX()))+64;
        final int rangeZ=Math.max(Math.abs(start.getZ()-village.getZ()),
                Math.abs(end.getZ()-village.getZ()))+64;
        final int maxNodes=flexible?1000:1400;
        java.util.PriorityQueue<E6fQueue> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6fQueue::estimate).thenComparingInt(E6fQueue::cost));
        Map<E6fPoint,Integer> best=new HashMap<>();
        Map<E6fPoint,E6fPoint> parent=new HashMap<>();
        E6fPoint seed=new E6fPoint(start.getX(),start.getZ(),-1);
        best.put(seed,0);open.add(new E6fQueue(seed,0,dev50e6fHeuristic(seed.x(),seed.z(),end,flexible)));
        int expanded=0,closest=dev50e6fClose(start.getX(),start.getZ(),end);
        E6fPoint found=null;
        while(!open.isEmpty()&&expanded<maxNodes) {
            E6fQueue current=open.poll();E6fPoint p=current.point();
            if(current.cost()!=best.getOrDefault(p,Integer.MAX_VALUE))continue;
            expanded++;
            closest=Math.min(closest,dev50e6fClose(p.x(),p.z(),end));
            if(p.x()==end.getX()&&p.z()==end.getZ()){found=p;break;}
            E6dCell from=dev50e6dCell(level,village,p.x(),p.z(),groundCache);
            if(!from.passable())continue;
            for(int d=0;d<8;d++) {
                if(!flexible&&(d%2)!=0)continue;
                int turn=p.direction()<0?0:Math.abs(d-p.direction());
                if(p.direction()>=0)turn=Math.min(turn,8-turn);
                // No immediate reversal; the diagonal probe permits headings
                // with up to a 90-degree change. Neither is certified rail geometry.
                if(turn>2){rejected[8]++;continue;}
                int x=p.x()+4*E6F_DX[d],z=p.z()+4*E6F_DZ[d];
                if(Math.abs(x-village.getX())>rangeX||Math.abs(z-village.getZ())>rangeZ) {
                    rejected[7]++;continue;
                }
                E6dCell to=dev50e6dCell(level,village,x,z,groundCache);
                if(!to.passable()){dev50e6fCountCell(rejected,to);continue;}
                if(Math.abs(to.y()-from.y())>1){rejected[5]++;continue;}
                if((d%2)!=0) {
                    // Diagonal transitions require BOTH adjoining axis-aligned
                    // cells to be clear and close in height: no corner-cutting.
                    E6dCell flankX=dev50e6dCell(level,village,x,p.z(),groundCache);
                    E6dCell flankZ=dev50e6dCell(level,village,p.x(),z,groundCache);
                    if(!flankX.passable()||!flankZ.passable()
                            ||Math.abs(flankX.y()-from.y())>1
                            ||Math.abs(flankZ.y()-from.y())>1
                            ||Math.abs(to.y()-flankX.y())>1
                            ||Math.abs(to.y()-flankZ.y())>1) {
                        rejected[6]++;continue;
                    }
                }
                E6fPoint q=new E6fPoint(x,z,d);
                int cost=current.cost()+((d%2)==0?10:14)+turn*30
                        +Math.abs(to.y()-from.y())*30;
                if(cost>=best.getOrDefault(q,Integer.MAX_VALUE))continue;
                best.put(q,cost);parent.put(q,p);
                open.add(new E6fQueue(q,cost,cost+dev50e6fHeuristic(x,z,end,flexible)));
            }
        }
        if(found==null)return new E6fProbe(false,expanded,expanded>=maxNodes,closest,
                rejected,0,0,-1,-1);
        List<E6fPoint> route=new ArrayList<>();
        for(E6fPoint a=found;a!=null;a=parent.get(a))route.add(a);
        java.util.Collections.reverse(route);
        int bends=0;
        for(int i=2;i<route.size();i++)if(route.get(i).direction()!=route.get(i-1).direction())bends++;
        return new E6fProbe(true,expanded,false,0,rejected,Math.max(0,route.size()-1),
                bends,route.size()>1?route.get(1).direction():-1,found.direction());
    }
    private static int dev50e6fPathCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6f pathcheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));
            return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("No verified loaded vanilla village within 128 blocks. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> anchor=dev50e6bVillageStartAnchor(level,village,128);
        if(anchor.isEmpty()){
            src.sendFailure(Component.literal("No verified loaded vanilla village structure anchor. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,anchor.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6F] PATH_SKIP village={} style={} reason=NATURAL_NO_TRACKS placement=DISABLED blocksChanged=0",village,style);
            src.sendSuccess(()->Component.literal("[E6f] NATURAL_VANILLA: no route proposals, 0 blocks changed."),false);
            return 1;
        }
        long begin=System.nanoTime();
        E6cLoop bestLoop=null;
        Map<Long,int[]> coarse=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}) {
                E6cLoop trial=dev50e6cCoarse(level,village,n,e,s,w,coarse);
                if(bestLoop==null||trial.coarseScore()<bestLoop.coarseScore())bestLoop=trial;
            }
        if(bestLoop==null)return 0;
        int[] offsets={bestLoop.n(),bestLoop.e(),bestLoop.s(),bestLoop.w()};
        Map<Long,E6dCell> cache=new HashMap<>();
        E6eSide[] sides=new E6eSide[4];
        for(int i=0;i<4;i++)sides[i]=dev50e6eAnchors(level,village,E6E_SIDES[i],offsets[i],cache);
        int tested=0,strictFound=0,flexFound=0,strictExpanded=0,flexExpanded=0;
        int strictCapped=0,flexCapped=0;
        for(int side=0;side<4;side++) {
            E6eSide from=sides[side],to=sides[(side+1)%4];
            int pairs=0,strictGood=0,flexGood=0,strictNear=Integer.MAX_VALUE,flexNear=Integer.MAX_VALUE;
            int nodesStrict=0,nodesFlex=0,capsStrict=0,capsFlex=0;
            int[] rejectionStrict=new int[9],rejectionFlex=new int[9];
            String example="none";
            for(int i=0;i<from.selected().size();i++)for(int j=0;j<to.selected().size();j++) {
                BlockPos a=from.selected().get(i).point(),b=to.selected().get(j).point();
                E6fProbe normal=dev50e6fProbe(level,village,a,b,false,cache);
                E6fProbe flex=dev50e6fProbe(level,village,a,b,true,cache);
                pairs++;tested++;
                if(normal.reached()){strictGood++;strictFound++;}
                if(flex.reached()){
                    flexGood++;flexFound++;
                    if("none".equals(example))example="anchors="+i+"->"+j+" firstHeading="
                            +flex.firstDirection()+" lastHeading="+flex.lastDirection()
                            +" steps="+flex.steps()+" bends="+flex.bends();
                }
                strictNear=Math.min(strictNear,normal.nearestBlocks());
                flexNear=Math.min(flexNear,flex.nearestBlocks());
                nodesStrict+=normal.expanded();nodesFlex+=flex.expanded();
                strictExpanded+=normal.expanded();flexExpanded+=flex.expanded();
                if(normal.capped()){capsStrict++;strictCapped++;}
                if(flex.capped()){capsFlex++;flexCapped++;}
                for(int k=0;k<9;k++){
                    rejectionStrict[k]+=normal.rejected()[k];
                    rejectionFlex[k]+=flex.rejected()[k];
                }
            }
            LOG.info("[TC-DEV50E6F] PATH_SIDE village={} leg={}->{} anchors={}/{} pairs={} strictFound={} diagonalFound={} strictNearestBlocks={} diagonalNearestBlocks={} strictExpanded={} diagonalExpanded={} strictCapped={} diagonalCapped={} strictRejections=[{}] diagonalRejections=[{}] diagonalExample={} nativeDiagonalRails=NOT_CERTIFIED",
                    village,from.side(),to.side(),from.selected().size(),to.selected().size(),pairs,
                    strictGood,flexGood,strictNear==Integer.MAX_VALUE?-1:strictNear,
                    flexNear==Integer.MAX_VALUE?-1:flexNear,nodesStrict,nodesFlex,capsStrict,capsFlex,
                    dev50e6fCounts(rejectionStrict),dev50e6fCounts(rejectionFlex),example);
            final String line="[E6f] "+from.side()+"->"+to.side()+" pairs="+pairs+" strict="+strictGood
                    +" diagonalProbe="+flexGood+" nearest="+(strictNear==Integer.MAX_VALUE?-1:strictNear)
                    +"/"+(flexNear==Integer.MAX_VALUE?-1:flexNear)+" blocks";
            src.sendSuccess(()->Component.literal(line),false);
        }
        int ms=(int)((System.nanoTime()-begin)/1000000L);
        E6dNative catalog=dev50e6dNative();
        String finding=flexFound>strictFound?"DIAGONAL_APPROACH_MORE_CONNECTED":
                strictFound>0?"SOME_CARDINAL_CONNECTIONS":"NO_LOADED_CONNECTION_FOUND";
        LOG.info("[TC-DEV50E6F] PATH_SUMMARY village={} style={} anchors=N{} E{} S{} W{} pairs={} strictSolved={} diagonalProbeSolved={} strictExpanded={} diagonalExpanded={} strictCapped={} diagonalCapped={} cachedGround={} nativeCatalog={} native90Turns=false nativeDiagonalRails=false nativeSlopes=false fiveCarSweep=false finding={} elapsedMs={} placement=DISABLED chunksForced=0 blocksChanged=0",
                village,style,sides[0].selected().size(),sides[1].selected().size(),
                sides[2].selected().size(),sides[3].selected().size(),tested,strictFound,flexFound,
                strictExpanded,flexExpanded,strictCapped,flexCapped,cache.size(),catalog.catalogReady(),
                finding,ms);
        final int seen=tested,cardinal=strictFound,diagonal=flexFound,time=ms;
        src.sendSuccess(()->Component.literal("[E6f] "+finding+"; " +cardinal+"/"+seen
                +" cardinal, "+diagonal+"/"+seen+" diagonal diagnostic; "+time+"ms."),false);
        src.sendSuccess(()->Component.literal("[E6f] Geometric probes ONLY; no native curve/grade/5-car certificate. Placement DISABLED; 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6g: READ-ONLY synthetic routing controls and live-world rule
    // ablation. These are DIAGNOSTIC GRID WALKS, not native track geometry.
    // The synthetic cases never access any ServerLevel, chunk or block.
    // Live probes reuse the accepted E6dCell screen, loaded chunks only.
    private interface E6gSurface { E6dCell at(int x,int z); }
    private record E6gRun(boolean reached,int expanded,boolean capped,int nearest,
                           int uniquePositions,int rejectedTurn,int rejectedGrade,
                           int rejectedBlocked,int rejectedBounds,int steps,int bends) {}
    private static E6gRun dev50e6gRoute(E6gSurface surface,int startX,int startZ,
                            int goalX,int goalZ,int minX,int maxX,int minZ,int maxZ,
                            int maxTurn,int maxRise,int cap) {
        // Eight-direction heading representation, cardinal-only transitions.
        // Turn <=2 means at most a 90-degree change. Turn <=4 permits a U-turn
        // as a DIAGNOSTIC only: no Traincraft native reverse curve is certified.
        java.util.PriorityQueue<E6fQueue> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6fQueue::estimate).thenComparingInt(E6fQueue::cost));
        Map<E6fPoint,Integer> best=new HashMap<>();
        Map<E6fPoint,E6fPoint> parent=new HashMap<>();
        java.util.Set<Long> locations=new java.util.HashSet<>();
        E6fPoint first=new E6fPoint(startX,startZ,-1);
        E6dCell start=surface.at(startX,startZ),target=surface.at(goalX,goalZ);
        int near=Math.abs(goalX-startX)+Math.abs(goalZ-startZ);
        if(Math.floorMod(startX-goalX,4)!=0 || Math.floorMod(startZ-goalZ,4)!=0
                || !start.passable() || !target.passable()) {
            return new E6gRun(false,0,false,near,0,0,0,
                    (!start.passable()?1:0)+(!target.passable()?1:0),0,0,0);
        }
        best.put(first,0);
        open.add(new E6fQueue(first,0,near/4*10));
        int expanded=0,turnRejected=0,gradeRejected=0,blockedRejected=0,boundRejected=0;
        E6fPoint finish=null;
        while(!open.isEmpty() && expanded<cap) {
            E6fQueue node=open.poll();E6fPoint p=node.point();
            if(node.cost()!=best.getOrDefault(p,Integer.MAX_VALUE))continue;
            expanded++;
            locations.add(new BlockPos(p.x(),0,p.z()).asLong());
            near=Math.min(near,Math.abs(goalX-p.x())+Math.abs(goalZ-p.z()));
            if(p.x()==goalX && p.z()==goalZ){finish=p;break;}
            E6dCell ground=surface.at(p.x(),p.z());
            if(!ground.passable())continue;
            for(int dir=0;dir<8;dir+=2) {
                int turn=p.direction()<0?0:Math.abs(dir-p.direction());
                if(p.direction()>=0)turn=Math.min(turn,8-turn);
                if(turn>maxTurn){turnRejected++;continue;}
                int x=p.x()+4*E6F_DX[dir],z=p.z()+4*E6F_DZ[dir];
                if(x<minX||x>maxX||z<minZ||z>maxZ){boundRejected++;continue;}
                E6dCell next=surface.at(x,z);
                if(!next.passable()){blockedRejected++;continue;}
                int rise=Math.abs(next.y()-ground.y());
                if(rise>maxRise){gradeRejected++;continue;}
                E6fPoint q=new E6fPoint(x,z,dir);
                int cost=node.cost()+10+turn*30+rise*30;
                if(cost>=best.getOrDefault(q,Integer.MAX_VALUE))continue;
                best.put(q,cost);parent.put(q,p);
                int h=(Math.abs(goalX-x)+Math.abs(goalZ-z))/4*10;
                open.add(new E6fQueue(q,cost,cost+h));
            }
        }
        int steps=0,bends=0;
        if(finish!=null) {
            List<E6fPoint> points=new ArrayList<>();
            for(E6fPoint a=finish;a!=null;a=parent.get(a))points.add(a);
            java.util.Collections.reverse(points);
            steps=Math.max(0,points.size()-1);
            for(int i=2;i<points.size();i++)if(points.get(i).direction()!=points.get(i-1).direction())bends++;
        }
        return new E6gRun(finish!=null,expanded,finish==null&&expanded>=cap,near,
                locations.size(),turnRejected,gradeRejected,blockedRejected,boundRejected,steps,bends);
    }
    private static int dev50e6gSolverCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6g solvercheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));
            return 0;
        }
        long begin=System.nanoTime();
        // Independent synthetic tests. NO level methods are called by these
        // surfaces; use exactly the same four-direction A* and heading state
        // as the live-world diagnostic below.
        E6gSurface flat=(x,z)->new E6dCell(64,null);
        E6gSurface detour=(x,z)->new E6dCell(64,x==32&&Math.abs(z)<=24?"OBSTRUCTED":null);
        E6gSurface sealed=(x,z)->new E6dCell(64,x==32?"OBSTRUCTED":null);
        E6gSurface terrace=(x,z)->new E6dCell(x>=32?66:64,null);
        E6gRun bend=dev50e6gRoute(flat,0,0,48,48,-16,80,-16,80,2,1,2400);
        E6gRun wall=dev50e6gRoute(detour,0,0,64,0,-16,80,-64,64,2,1,2400);
        E6gRun barrier=dev50e6gRoute(sealed,0,0,64,0,-16,80,-64,64,2,1,3000);
        E6gRun terraceStrict=dev50e6gRoute(terrace,0,0,64,0,-16,80,-32,32,2,1,1500);
        E6gRun terraceRelax=dev50e6gRoute(terrace,0,0,64,0,-16,80,-32,32,2,2,1500);
        boolean passed=bend.reached()&&wall.reached()&&!barrier.reached()
                &&!terraceStrict.reached()&&terraceRelax.reached();
        String[] labels={"FLAT_RIGHT_ANGLE","WALL_WITH_END_DETOUR","SEALED_BARRIER",
                "HEIGHT_STEP_STRICT","HEIGHT_STEP_RELAX_DIAGNOSTIC"};
        E6gRun[] synthetic={bend,wall,barrier,terraceStrict,terraceRelax};
        boolean[] expected={true,true,false,false,true};
        for(int i=0;i<synthetic.length;i++){
            E6gRun out=synthetic[i];
            LOG.info("[TC-DEV50E6G] SYNTH name={} expected={} reached={} result={} expanded={} uniquePositions={} nearestBlocks={} capped={} turnsRejected={} gradeRejected={} blockedRejected={} boundsRejected={} steps={} bends={} terrainSource=PURE_MEMORY placement=DISABLED",
                    labels[i],expected[i],out.reached(),expected[i]==out.reached()?"PASS":"FAIL",
                    out.expanded(),out.uniquePositions(),out.nearest(),out.capped(),out.rejectedTurn(),
                    out.rejectedGrade(),out.rejectedBlocked(),out.rejectedBounds(),out.steps(),out.bends());
        }
        if(!passed){
            LOG.error("[TC-DEV50E6G] SYNTH_SUMMARY result=FAIL placement=DISABLED chunksForced=0 blocksChanged=0");
            src.sendFailure(Component.literal("[E6g] SYNTHETIC TEST FAILED: report logs; no railway changes."));
            return 0;
        }
        LOG.info("[TC-DEV50E6G] SYNTH_SUMMARY result=PASS tests=5 headingState=TRACKED constructionCertified=false placement=DISABLED chunksForced=0 blocksChanged=0");
        src.sendSuccess(()->Component.literal("[E6g] Synthetic tests: 5/5 PASS (flat, obstacle detour, sealed barrier, elevation threshold)."),false);
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendSuccess(()->Component.literal("[E6g] No loaded vanilla village nearby: synthetic-only check complete; no changes."),false);
            return 1;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> anchor=dev50e6bVillageStartAnchor(level,village,128);
        if(anchor.isEmpty()){
            src.sendSuccess(()->Component.literal("[E6g] No verified loaded village structure anchor: synthetic-only check complete."),false);
            return 1;
        }
        E6bStyle style=dev50e6bStyle(level,anchor.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6G] REAL_SKIP village={} style=NATURAL_VANILLA reason=NO_TRACKS placement=DISABLED blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6g] Natural village skipped; no rails proposed."),false);
            return 1;
        }
        E6cLoop bestLoop=null;
        Map<Long,int[]> coarse=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}) {
                E6cLoop candidate=dev50e6cCoarse(level,village,n,e,s,w,coarse);
                if(bestLoop==null||candidate.coarseScore()<bestLoop.coarseScore())bestLoop=candidate;
            }
        if(bestLoop==null)return 0;
        int[] offsets={bestLoop.n(),bestLoop.e(),bestLoop.s(),bestLoop.w()};
        Map<Long,E6dCell> cache=new HashMap<>();
        E6eSide[] sides=new E6eSide[4];
        for(int side=0;side<4;side++){
            sides[side]=dev50e6eAnchors(level,village,E6E_SIDES[side],offsets[side],cache);
        }
        E6gSurface world=(x,z)->dev50e6dCell(level,village,x,z,cache);
        int pairCount=0,baseFound=0,turnFound=0,gradeFound=0,baseExpanded=0;
        int baseTurnRejected=0,baseGradeRejected=0,baseBlockedRejected=0;
        for(int side=0;side<4;side++) {
            E6eSide from=sides[side],to=sides[(side+1)%4];
            int pairs=0,base=0,turns=0,grades=0,nearest=Integer.MAX_VALUE,expanded=0;
            int rt=0,rg=0,rb=0,capCount=0;
            // Strictly capped: at most 2x2 anchor pairs per route leg, and
            // three bounded A* probes per pair. The latter are DIAGNOSTIC only.
            for(int i=0;i<Math.min(2,from.selected().size());i++)for(int j=0;j<Math.min(2,to.selected().size());j++){
                BlockPos a=from.selected().get(i).point(),b=to.selected().get(j).point();
                int rx=Math.max(Math.abs(a.getX()-village.getX()),Math.abs(b.getX()-village.getX()))+64;
                int rz=Math.max(Math.abs(a.getZ()-village.getZ()),Math.abs(b.getZ()-village.getZ()))+64;
                int loX=village.getX()-rx,hiX=village.getX()+rx;
                int loZ=village.getZ()-rz,hiZ=village.getZ()+rz;
                E6gRun strict=dev50e6gRoute(world,a.getX(),a.getZ(),b.getX(),b.getZ(),loX,hiX,loZ,hiZ,2,1,1400);
                E6gRun allowTurn=dev50e6gRoute(world,a.getX(),a.getZ(),b.getX(),b.getZ(),loX,hiX,loZ,hiZ,4,1,1400);
                E6gRun allowGrade=dev50e6gRoute(world,a.getX(),a.getZ(),b.getX(),b.getZ(),loX,hiX,loZ,hiZ,2,2,1400);
                pairs++;pairCount++;
                if(strict.reached()){base++;baseFound++;}
                if(allowTurn.reached()){turns++;turnFound++;}
                if(allowGrade.reached()){grades++;gradeFound++;}
                nearest=Math.min(nearest,strict.nearest());
                expanded+=strict.expanded();baseExpanded+=strict.expanded();
                rt+=strict.rejectedTurn();rg+=strict.rejectedGrade();rb+=strict.rejectedBlocked();
                if(strict.capped())capCount++;
            }
            baseTurnRejected+=rt;baseGradeRejected+=rg;baseBlockedRejected+=rb;
            LOG.info("[TC-DEV50E6G] REAL_SIDE village={} leg={}->{} pairs={} strictSolved={} allow180TurnDiagnosticSolved={} allow2HeightStepDiagnosticSolved={} strictExpanded={} strictCapped={} strictNearestBlocks={} strictRejectedTurn={} strictRejectedGrade={} strictRejectedBlocked={} nativeTrackGeometry=NOT_CERTIFIED placement=DISABLED blocksChanged=0",
                    village,from.side(),to.side(),pairs,base,turns,grades,expanded,capCount,
                    nearest==Integer.MAX_VALUE?-1:nearest,rt,rg,rb);
            final String line="[E6g] "+from.side()+"->"+to.side()+" pairs="+pairs+" strict="+base
                    +" turnRelax="+turns+" gradeRelax="+grades;
            src.sendSuccess(()->Component.literal(line),false);
        }
        int elapsed=(int)((System.nanoTime()-begin)/1000000L);
        LOG.info("[TC-DEV50E6G] SOLVER_SUMMARY village={} style={} synthetic=PASS realPairs={} strictSolved={} turnRelaxDiagnosticSolved={} gradeRelaxDiagnosticSolved={} strictExpanded={} strictRejectedTurn={} strictRejectedGrade={} strictRejectedBlocked={} cachedGround={} elapsedMs={} nativeTurns=false nativeSlopes=false fiveCarSweep=false placement=DISABLED chunksForced=0 blocksChanged=0",
                village,style,pairCount,baseFound,turnFound,gradeFound,baseExpanded,baseTurnRejected,
                baseGradeRejected,baseBlockedRejected,cache.size(),elapsed);
        final int n=pairCount,b=baseFound,t=turnFound,g=gradeFound;
        src.sendSuccess(()->Component.literal("[E6g] Synthetic PASS; real pairs="+n+" strict="+b
                +" turnRelax="+t+" gradeRelax="+g+". Diagnostic only; 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6h: READ-ONLY clearance blocker isolation. This does not relax
    // the accepted 7-wide railway envelope or propose any placed native track.
    // Centerline-only routes are COUNTERFACTUAL DIAGNOSTICS, never build plans.
    private record E6hHit(String kind,String blockId,int lateral,int height) {}
    private record E6hProfile(E6dCell center,E6dCell wide,E6hHit centerHit,
                              E6hHit firstWideHit) {
        boolean widthOnlyBlocked(){return center.passable()&&!wide.passable();}
    }
    private static E6hHit dev50e6hHit(ServerLevel level,int x,int y,int z,
                                       int lateralX,int lateralZ) {
        BlockPos p=new BlockPos(x+lateralX,y,z+lateralZ);
        int lateral=Math.max(Math.abs(lateralX),Math.abs(lateralZ));
        if(!level.hasChunkAt(p))return new E6hHit("UNLOADED","unloaded",lateral,-1);
        if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid()
                ||level.structureManager().getStructureWithPieceAt(p.below(),StructureTags.VILLAGE).isValid())
            return new E6hHit("VILLAGE_STRUCTURE","protected_village",lateral,-1);
        BlockState bed=level.getBlockState(p.below());
        if(!bed.getFluidState().isEmpty())
            return new E6hHit("WATER_OR_FLUID",
                    BuiltInRegistries.BLOCK.getKey(bed.getBlock()).toString(),lateral,-1);
        for(int h=0;h<5;h++){
            BlockPos air=p.above(h);
            BlockState state=level.getBlockState(air);
            if(!state.getFluidState().isEmpty())
                return new E6hHit("WATER_OR_FLUID",
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),lateral,h);
            if(state.getCollisionShape(level,air).isEmpty())continue;
            String kind;
            if(state.is(net.minecraft.tags.BlockTags.LOGS))kind="TREE_LOG";
            else if(state.is(net.minecraft.tags.BlockTags.LEAVES))kind="TREE_LEAVES";
            else if(state.is(Blocks.STONE)||state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)
                    ||state.is(Blocks.GRAVEL)||state.is(Blocks.SAND)||state.is(Blocks.SANDSTONE)
                    ||state.is(Blocks.DEEPSLATE)||state.is(Blocks.TUFF)
                    ||state.is(Blocks.COBBLESTONE)||state.is(Blocks.ANDESITE))
                kind="SOLID_TERRAIN";
            else kind=h>0?"OVERHEAD_OTHER_SOLID":"SURFACE_OTHER_SOLID";
            return new E6hHit(kind,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),lateral,h);
        }
        return new E6hHit("CLEAR","air",lateral,-1);
    }
    private static E6hProfile dev50e6hProfile(ServerLevel level,BlockPos village,int x,int z,
                                               Map<Long,E6dCell> wideCache,
                                               Map<Long,E6hProfile> profiles) {
        long key=new BlockPos(x,0,z).asLong();
        E6hProfile cached=profiles.get(key);
        if(cached!=null)return cached;
        // REUSE the exact accepted DEV50e6d wide envelope as ground truth.
        E6dCell wide=dev50e6dCell(level,village,x,z,wideCache);
        E6hHit nothing=new E6hHit("CLEAR","air",0,-1);
        E6hHit centerHit=nothing,wideHit=nothing;
        E6dCell center=wide;
        if("VILLAGE_BUFFER".equals(wide.reason())||"UNLOADED".equals(wide.reason())) {
            E6hHit guard=new E6hHit(wide.reason(),"protected_or_unloaded",0,-1);
            centerHit=guard;wideHit=guard;
        } else {
            // Height is identical to the wide screen. The ONLY difference is
            // lateral extent: (0,0) vs a 7-block cross section, 5 blocks high.
            centerHit=dev50e6hHit(level,x,wide.y(),z,0,0);
            center=new E6dCell(wide.y(),"CLEAR".equals(centerHit.kind())?null:centerHit.kind());
            if(!wide.passable()){
                outer: for(int w=-3;w<=3;w++)for(int axis=0;axis<2;axis++){
                    if(w==0&&axis==1)continue;
                    E6hHit hit=dev50e6hHit(level,x,wide.y(),z,axis==0?w:0,axis==1?w:0);
                    if(!"CLEAR".equals(hit.kind())) {wideHit=hit;break outer;}
                }
                if("CLEAR".equals(wideHit.kind()))
                    wideHit=new E6hHit("SCREEN_MISMATCH",wide.reason(),0,-1);
            }
        }
        E6hProfile out=new E6hProfile(center,wide,centerHit,wideHit);
        profiles.put(key,out);
        return out;
    }
    private static String dev50e6hTop(Map<String,Integer> m,int limit) {
        List<Map.Entry<String,Integer>> entries=new ArrayList<>(m.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String,Integer> a)->a.getValue()).reversed()
                .thenComparing(Map.Entry::getKey));
        StringBuilder result=new StringBuilder();
        for(int i=0;i<Math.min(limit,entries.size());i++) {
            Map.Entry<String,Integer> e=entries.get(i);
            if(i>0)result.append(",");
            result.append(e.getKey()).append(":").append(e.getValue());
        }
        return result.length()==0?"none":result.toString();
    }
    private static int dev50e6hClearanceCheck(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50e6h clearancecheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));
            return 0;
        }
        long begin=System.nanoTime();
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()) {
            src.sendFailure(Component.literal("[E6h] No loaded verified vanilla village nearby. READ-ONLY."));
            return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> anchor=dev50e6bVillageStartAnchor(level,village,128);
        if(anchor.isEmpty()){
            src.sendFailure(Component.literal("[E6h] No loaded natural village structure anchor. READ-ONLY."));
            return 0;
        }
        E6bStyle style=dev50e6bStyle(level,anchor.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6H] CLEARANCE_SKIP village={} style=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6h] Natural village skipped: no route proposed, 0 blocks changed."),false);
            return 1;
        }
        E6cLoop bestLoop=null;
        Map<Long,int[]> terrain=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}){
                E6cLoop candidate=dev50e6cCoarse(level,village,n,e,s,w,terrain);
                if(bestLoop==null||candidate.coarseScore()<bestLoop.coarseScore())bestLoop=candidate;
            }
        if(bestLoop==null)return 0;
        int[] offsets={bestLoop.n(),bestLoop.e(),bestLoop.s(),bestLoop.w()};
        Map<Long,E6dCell> wideCache=new HashMap<>();
        E6eSide[] sides=new E6eSide[4];
        for(int i=0;i<4;i++)sides[i]=dev50e6eAnchors(level,village,E6E_SIDES[i],offsets[i],wideCache);
        // Only cells actually explored by the capped pair probes appear in
        // this classification. Counts are unique sampled coordinates, not
        // blocked search transitions or total terrain blocks in the village.
        Map<Long,E6hProfile> profiles=new HashMap<>();
        E6gSurface wide=(x,z)->dev50e6hProfile(level,village,x,z,wideCache,profiles).wide();
        E6gSurface center=(x,z)->dev50e6hProfile(level,village,x,z,wideCache,profiles).center();
        int pairCount=0,wideConnected=0,centerConnected=0,centerOnlyPairs=0;
        int wideExpanded=0,centerExpanded=0;
        for(int side=0;side<4;side++) {
            E6eSide from=sides[side],to=sides[(side+1)%4];
            int pairs=0,wideSolved=0,centerSolved=0,centerOnly=0;
            int expandedWide=0,expandedCenter=0,nearWide=Integer.MAX_VALUE,nearCenter=Integer.MAX_VALUE;
            int cappedWide=0,cappedCenter=0;
            // Exactly the accepted E6g 2x2 anchor selection; same bounded
            // heading-aware A*, direction/grade rules and search extent.
            for(int i=0;i<Math.min(2,from.selected().size());i++)for(int j=0;j<Math.min(2,to.selected().size());j++){
                BlockPos a=from.selected().get(i).point(),b=to.selected().get(j).point();
                int rx=Math.max(Math.abs(a.getX()-village.getX()),Math.abs(b.getX()-village.getX()))+64;
                int rz=Math.max(Math.abs(a.getZ()-village.getZ()),Math.abs(b.getZ()-village.getZ()))+64;
                int loX=village.getX()-rx,hiX=village.getX()+rx;
                int loZ=village.getZ()-rz,hiZ=village.getZ()+rz;
                E6gRun full=dev50e6gRoute(wide,a.getX(),a.getZ(),b.getX(),b.getZ(),loX,hiX,loZ,hiZ,2,1,1400);
                E6gRun thin=dev50e6gRoute(center,a.getX(),a.getZ(),b.getX(),b.getZ(),loX,hiX,loZ,hiZ,2,1,1400);
                pairs++;pairCount++;
                if(full.reached()){wideSolved++;wideConnected++;}
                if(thin.reached()){centerSolved++;centerConnected++;}
                if(thin.reached()&&!full.reached()){centerOnly++;centerOnlyPairs++;}
                expandedWide+=full.expanded();expandedCenter+=thin.expanded();
                nearWide=Math.min(nearWide,full.nearest());nearCenter=Math.min(nearCenter,thin.nearest());
                if(full.capped())cappedWide++;
                if(thin.capped())cappedCenter++;
            }
            wideExpanded+=expandedWide;centerExpanded+=expandedCenter;
            LOG.info("[TC-DEV50E6H] CLEARANCE_SIDE village={} leg={}->{} pairs={} full7x5Solved={} center1x5DiagnosticSolved={} centerOnlyPairs={} expandedFull={} expandedCenter={} nearestFull={} nearestCenter={} cappedFull={} cappedCenter={} construction=DISABLED blocksChanged=0",
                    village,from.side(),to.side(),pairs,wideSolved,centerSolved,centerOnly,
                    expandedWide,expandedCenter,nearWide==Integer.MAX_VALUE?-1:nearWide,
                    nearCenter==Integer.MAX_VALUE?-1:nearCenter,cappedWide,cappedCenter);
            final String chat="[E6h] "+from.side()+"->"+to.side()+" pairs="+pairs
                    +" full="+wideSolved+" centerOnlyDiagnostic="+centerSolved;
            src.sendSuccess(()->Component.literal(chat),false);
        }
        int wideBlocked=0,centerBlocked=0,widthOnly=0,unloaded=0,mismatched=0;
        Map<String,Integer> classes=new HashMap<>(),blockIds=new HashMap<>();
        List<String> examples=new ArrayList<>();
        for(Map.Entry<Long,E6hProfile> sample:profiles.entrySet()){
            E6hProfile p=sample.getValue();
            if(!p.wide().passable()){
                wideBlocked++;
                E6hHit first=p.firstWideHit();
                classes.merge(first.kind(),1,Integer::sum);
                if(!"CLEAR".equals(first.kind())&&!"UNLOADED".equals(first.kind())
                        &&!"VILLAGE_BUFFER".equals(first.kind()))
                    blockIds.merge(first.blockId(),1,Integer::sum);
                if("UNLOADED".equals(first.kind()))unloaded++;
                if("SCREEN_MISMATCH".equals(first.kind()))mismatched++;
            }
            if(!p.center().passable())centerBlocked++;
            if(p.widthOnlyBlocked()){
                widthOnly++;
                if(examples.size()<4){
                    BlockPos spot=BlockPos.of(sample.getKey());
                    E6hHit first=p.firstWideHit();
                    examples.add(spot.getX()+":"+spot.getZ()+":y"+p.wide().y()
                            +":"+first.kind()+":"+first.blockId()+":lateral"+first.lateral()+":h"+first.height());
                }
            }
        }
        int elapsed=(int)((System.nanoTime()-begin)/1000000L);
        LOG.info("[TC-DEV50E6H] CLEARANCE_SUMMARY village={} style={} anchorBounds=N{} E{} S{} W{} pairs={} full7x5Solved={} center1x5DiagnosticSolved={} centerOnlyPairs={} uniqueProfileCells={} fullBlockedCells={} centerBlockedCells={} lateralOnlyBlockedCells={} unloadedCells={} classificationMismatches={} topBlockerKinds={} topBlockIds={} widthOnlyExamples={} expandedFull={} expandedCenter={} elapsedMs={} nativeTurns=false nativeSlopes=false fiveCarSweep=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,offsets[0],offsets[1],offsets[2],offsets[3],pairCount,
                wideConnected,centerConnected,centerOnlyPairs,profiles.size(),wideBlocked,
                centerBlocked,widthOnly,unloaded,mismatched,dev50e6hTop(classes,8),
                dev50e6hTop(blockIds,8),examples,wideExpanded,centerExpanded,elapsed);
        final int n=pairCount,f=wideConnected,c=centerConnected,w=widthOnly;
        src.sendSuccess(()->Component.literal("[E6h] 7-wide="+f+"/"+n+"; 1-wide diagnostic="+c+"/"+n
                +"; lateral-only blocked samples="+w+". See log for blocker types. 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6i: read-only hypothetical SMALL EARTHWORKS screening.
    // It is not a track-placement, slope, bridge, safety, or train-swept clearance certificate.
    // Earthwork quantities are samples of overlapping cross-shaped footprints, not
    // excavated physical block totals. NO world edits, NO block removal, NO chunk loading.
    private static final int E6I_MAX_CUT=2, E6I_MAX_FILL=2, E6I_MAX_VEG=8;
    private record E6iKey(int x,int z,int y) {}
    private record E6iScreen(String rejected,int cutSamples,int fillSamples,
                             int leafSamples,int logSamples,int supportSamples) {
        boolean passable(){return rejected==null;}
        int workCost(){return 2*cutSamples+3*fillSamples+leafSamples+2*logSamples+4*supportSamples;}
    }
    private record E6iState(int x,int z,int y,int heading) {}
    private record E6iQueue(E6iState state,int cost,int priority) {}
    private record E6iRoute(boolean reached,int expanded,boolean capped,int near,
                             int routeCells,int bends,long cutSamples,long fillSamples,
                             long leafSamples,long logSamples,long supportSamples,int cost) {}
    private static boolean dev50e6iNatural(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT)
                ||s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                ||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.GRAVEL)
                ||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)
                ||s.is(Blocks.TERRACOTTA)||s.is(Blocks.CLAY)
                ||s.is(Blocks.COBBLESTONE)||s.is(Blocks.TUFF)
                ||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)
                ||s.is(Blocks.DEEPSLATE)
                ||BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }
    private static E6iScreen dev50e6iScreen(ServerLevel level,BlockPos village,
                                             int x,int z,int bedY,
                                             Map<E6iKey,E6iScreen> cache) {
        E6iKey key=new E6iKey(x,z,bedY);
        E6iScreen memo=cache.get(key);
        if(memo!=null)return memo;
        E6iScreen result;
        long dx=(long)x-village.getX(),dz=(long)z-village.getZ();
        if(dx*dx+dz*dz<56L*56L) result=new E6iScreen("VILLAGE_BUFFER",0,0,0,0,0);
        else {
            int cut=0,fill=0,leaves=0,logs=0,supports=0;
            String failure=null;
            // Same 7-wide cross as E6d, but with an explicit planned bed
            // elevation. Individual natural-soil columns may be cut/filled by
            // at most TWO blocks. Native swept geometry is still UNVERIFIED.
            outer: for(int offset=-3;offset<=3;offset++)for(int axis=0;axis<2;axis++) {
                if(offset==0&&axis==1)continue;
                int xx=x+(axis==0?offset:0),zz=z+(axis==1?offset:0);
                BlockPos pos=new BlockPos(xx,bedY,zz);
                if(!level.hasChunkAt(pos)) {failure="UNLOADED";break outer;}
                int terrainY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,xx,zz);
                int cutDepth=Math.max(0,terrainY-bedY),fillDepth=Math.max(0,bedY-terrainY);
                if(cutDepth>E6I_MAX_CUT){failure="CUT_DEPTH";break outer;}
                if(fillDepth>E6I_MAX_FILL){failure="FILL_DEPTH";break outer;}
                // Both bridge footing and clearance must remain structure-free.
                // Inspect loaded positions only; a missing chunk fails closed.
                for(int h=-E6I_MAX_FILL-1;h<=4;h++){
                    BlockPos q=pos.above(h);
                    if(level.structureManager().getStructureWithPieceAt(q,StructureTags.VILLAGE).isValid()){
                        failure="VILLAGE_STRUCTURE";break outer;
                    }
                    BlockState state=level.getBlockState(q);
                    if(!state.getFluidState().isEmpty()){
                        failure="WATER_OR_FLUID";break outer;
                    }
                    if(h>=0&&!state.getCollisionShape(level,q).isEmpty()){
                        if(state.is(net.minecraft.tags.BlockTags.LEAVES))leaves++;
                        else if(state.is(net.minecraft.tags.BlockTags.LOGS))logs++;
                        else if(dev50e6iNatural(state)){
                            // These are hypothetical CUT cells, not authorization to dig.
                            if(h>=E6I_MAX_CUT){failure="CUT_DEPTH";break outer;}
                            cut++;
                        }else{failure="NON_NATURAL_BLOCK";break outer;}
                    }
                }
                if(leaves+logs>E6I_MAX_VEG){failure="VEGETATION_CAP";break outer;}
                // Fill or short embankment: count hypothetical space below
                // each sampled lane. Never classify water as a fillable bed.
                if(fillDepth>0){
                    fill+=fillDepth;
                    supports++;
                }
            }
            result=new E6iScreen(failure,cut,fill,leaves,logs,supports);
        }
        cache.put(key,result);
        return result;
    }
    private static E6iRoute dev50e6iRoute(ServerLevel level,BlockPos village,
                                           BlockPos a,BlockPos b,Map<E6iKey,E6iScreen> cache,
                                           int maxExpanded) {
        // Preserve the exact DEV50e6i entrypoint and natural-height endpoints.
        return dev50e6iRoute(level,village,a,b,cache,maxExpanded,null,null);
    }
    private static E6iRoute dev50e6iRoute(ServerLevel level,BlockPos village,
                                           BlockPos a,BlockPos b,Map<E6iKey,E6iScreen> cache,
                                           int maxExpanded,Integer startBed,Integer endBed) {
        int near=Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ());
        if(Math.floorMod(a.getX()-b.getX(),4)!=0||Math.floorMod(a.getZ()-b.getZ(),4)!=0)
            return new E6iRoute(false,0,false,near,0,0,0,0,0,0,0,0);
        int sy=startBed==null?level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,a.getX(),a.getZ()):startBed;
        int ey=endBed==null?level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,b.getX(),b.getZ()):endBed;
        if(!dev50e6iScreen(level,village,a.getX(),a.getZ(),sy,cache).passable()
                ||!dev50e6iScreen(level,village,b.getX(),b.getZ(),ey,cache).passable())
            return new E6iRoute(false,0,false,near,0,0,0,0,0,0,0,0);
        int rx=Math.max(Math.abs(a.getX()-village.getX()),Math.abs(b.getX()-village.getX()))+64;
        int rz=Math.max(Math.abs(a.getZ()-village.getZ()),Math.abs(b.getZ()-village.getZ()))+64;
        int loX=village.getX()-rx,hiX=village.getX()+rx,loZ=village.getZ()-rz,hiZ=village.getZ()+rz;
        java.util.PriorityQueue<E6iQueue> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6iQueue::priority).thenComparingInt(E6iQueue::cost));
        Map<E6iState,Integer> best=new HashMap<>();
        Map<E6iState,E6iState> parent=new HashMap<>();
        E6iState seed=new E6iState(a.getX(),a.getZ(),sy,-1);
        best.put(seed,0);open.add(new E6iQueue(seed,0,(near/4)*10));
        E6iState finish=null;int expanded=0;
        while(!open.isEmpty()&&expanded<maxExpanded){
            E6iQueue current=open.poll();E6iState p=current.state();
            if(current.cost()!=best.getOrDefault(p,Integer.MAX_VALUE))continue;
            expanded++;
            near=Math.min(near,Math.abs(p.x()-b.getX())+Math.abs(p.z()-b.getZ()));
            if(p.x()==b.getX()&&p.z()==b.getZ()&&p.y()==ey){finish=p;break;}
            for(int dir=0;dir<8;dir+=2){
                int turn=p.heading()<0?0:Math.abs(dir-p.heading());
                if(p.heading()>=0)turn=Math.min(turn,8-turn);
                if(turn>2)continue;
                int x=p.x()+4*E6F_DX[dir],z=p.z()+4*E6F_DZ[dir];
                if(x<loX||x>hiX||z<loZ||z>hiZ)continue;
                BlockPos check=new BlockPos(x,0,z);
                if(!level.hasChunkAt(check))continue;
                int natural=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                // The grade can rise/fall one block per FOUR horizontal blocks.
                // Alternate bed heights permit real (hypothetical) short cuts
                // and fills without relaxing the actual track-grade limit.
                for(int y=p.y()-1;y<=p.y()+1;y++){
                    if(y<natural-E6I_MAX_CUT||y>natural+E6I_MAX_FILL)continue;
                    if(x==b.getX()&&z==b.getZ()&&y!=ey)continue;
                    E6iScreen screen=dev50e6iScreen(level,village,x,z,y,cache);
                    if(!screen.passable())continue;
                    E6iState next=new E6iState(x,z,y,dir);
                    int cost=current.cost()+10+turn*25+Math.abs(y-p.y())*15
                            +screen.workCost();
                    if(cost>=best.getOrDefault(next,Integer.MAX_VALUE))continue;
                    best.put(next,cost);parent.put(next,p);
                    int remaining=(Math.abs(b.getX()-x)+Math.abs(b.getZ()-z))/4;
                    open.add(new E6iQueue(next,cost,cost+remaining*10));
                }
            }
        }
        if(finish==null)return new E6iRoute(false,expanded,expanded>=maxExpanded,near,0,0,0,0,0,0,0,0);
        List<E6iState> path=new ArrayList<>();
        for(E6iState p=finish;p!=null;p=parent.get(p))path.add(p);
        java.util.Collections.reverse(path);
        long cut=0,fill=0,leaf=0,log=0,support=0;int bends=0;
        for(int i=0;i<path.size();i++){
            E6iState p=path.get(i);
            E6iScreen s=dev50e6iScreen(level,village,p.x(),p.z(),p.y(),cache);
            cut+=s.cutSamples();fill+=s.fillSamples();leaf+=s.leafSamples();
            log+=s.logSamples();support+=s.supportSamples();
            if(i>1&&p.heading()!=path.get(i-1).heading())bends++;
        }
        return new E6iRoute(true,expanded,false,0,path.size(),bends,cut,fill,leaf,log,support,
                best.get(finish));
    }
    private static int dev50e6iEarthworksCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6i earthworkscheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));
            return 0;
        }
        long begin=System.nanoTime();
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6i] No loaded verified natural village nearby. READ-ONLY."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> naturalAnchor=dev50e6bVillageStartAnchor(level,village,128);
        if(naturalAnchor.isEmpty()){
            src.sendFailure(Component.literal("[E6i] No verified natural village structure anchor. READ-ONLY."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,naturalAnchor.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6I] EARTHWORKS_SKIP village={} style=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6i] Natural village skipped; 0 blocks changed."),false);
            return 1;
        }
        E6cLoop bestLoop=null;Map<Long,int[]> terrain=new HashMap<>();
        for(int n:new int[]{64,80,96,112})for(int e:new int[]{64,80,96,112})
            for(int s:new int[]{64,80,96,112})for(int w:new int[]{64,80,96,112}){
                E6cLoop choice=dev50e6cCoarse(level,village,n,e,s,w,terrain);
                if(bestLoop==null||choice.coarseScore()<bestLoop.coarseScore())bestLoop=choice;
            }
        if(bestLoop==null)return 0;
        int[] offsets={bestLoop.n(),bestLoop.e(),bestLoop.s(),bestLoop.w()};
        Map<Long,E6dCell> existing=new HashMap<>();
        E6eSide[] sides=new E6eSide[4];
        for(int i=0;i<4;i++)sides[i]=dev50e6eAnchors(level,village,E6E_SIDES[i],offsets[i],existing);
        Map<E6iKey,E6iScreen> cells=new HashMap<>();
        int totalPairs=0,passed=0,fullLegs=0,capped=0,expanded=0;
        long cuts=0,fills=0,leaves=0,logs=0,supports=0;
        for(int side=0;side<4;side++){
            E6eSide from=sides[side],to=sides[(side+1)%4];
            int pairs=0,solved=0,limited=0,nodes=0,closest=Integer.MAX_VALUE;
            E6iRoute preferred=null;
            for(int i=0;i<Math.min(2,from.selected().size());i++)for(int j=0;j<Math.min(2,to.selected().size());j++){
                BlockPos a=from.selected().get(i).point(),b=to.selected().get(j).point();
                E6iRoute result=dev50e6iRoute(level,village,a,b,cells,1800);
                pairs++;totalPairs++;nodes+=result.expanded();expanded+=result.expanded();
                closest=Math.min(closest,result.near());
                if(result.capped()){limited++;capped++;}
                if(result.reached()){
                    solved++;passed++;
                    if(preferred==null||result.cost()<preferred.cost())preferred=result;
                }
            }
            if(preferred!=null){
                fullLegs++;
                cuts+=preferred.cutSamples();fills+=preferred.fillSamples();
                leaves+=preferred.leafSamples();logs+=preferred.logSamples();supports+=preferred.supportSamples();
            }
            LOG.info("[TC-DEV50E6I] EARTHWORKS_SIDE village={} leg={}->{} pairs={} hypotheticalConnected={} expanded={} capped={} nearestBlocks={} chosenRouteCells={} chosenBends={} cutCellSamples={} fillCellSamples={} leafCellSamples={} logCellSamples={} shortSupportCellSamples={} construction=DISABLED blocksChanged=0",
                    village,from.side(),to.side(),pairs,solved,nodes,limited,
                    closest==Integer.MAX_VALUE?-1:closest,preferred==null?0:preferred.routeCells(),
                    preferred==null?0:preferred.bends(),preferred==null?0:preferred.cutSamples(),
                    preferred==null?0:preferred.fillSamples(),preferred==null?0:preferred.leafSamples(),
                    preferred==null?0:preferred.logSamples(),preferred==null?0:preferred.supportSamples());
            final String chat="[E6i] "+from.side()+"->"+to.side()+": hypothetical earthwork routes "
                    +solved+"/"+pairs+"; no construction.";
            src.sendSuccess(()->Component.literal(chat),false);
        }
        Map<String,Integer> rejected=new HashMap<>();int permitted=0;
        for(E6iScreen s:cells.values()){
            if(s.passable())permitted++;else rejected.merge(s.rejected(),1,Integer::sum);
        }
        int elapsed=(int)((System.nanoTime()-begin)/1000000L);
        String verdict=fullLegs==4?"FOUR_LEGS_HYPOTHETICAL_NOT_TRACK_CERTIFIED":"NO_COMPLETE_HYPOTHETICAL_LOOP";
        LOG.info("[TC-DEV50E6I] EARTHWORKS_SUMMARY village={} style={} anchorBounds=N{} E{} S{} W{} pairs={} pairConnections={} legsWithSomeConnection={} independentlyChosenLegsNotJoined=true globalClosed=false screenWidth=7cross height=5 maxCut={} maxFill={} maxVegPerCell={} sampledProfiles={} sampledPassable={} topRejections={} candidateChosenCutCellSamples={} candidateChosenFillCellSamples={} candidateChosenLeaves={} candidateChosenLogs={} candidateChosenSupportSamples={} searchNodes={} capped={} elapsedMs={} verdict={} nativeTurnPorts=false nativeSlopePorts=false fiveCarSweep=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,offsets[0],offsets[1],offsets[2],offsets[3],totalPairs,passed,fullLegs,
                E6I_MAX_CUT,E6I_MAX_FILL,E6I_MAX_VEG,cells.size(),permitted,
                dev50e6hTop(rejected,8),cuts,fills,leaves,logs,supports,expanded,capped,elapsed,verdict);
        final int candidates=passed,pairs=totalPairs,legs=fullLegs;
        src.sendSuccess(()->Component.literal("[E6i] Hypothetical earthworks: "+candidates+"/"+pairs
                +" pairs, "+legs+"/4 independent legs. NOT a connected native railway."),false);
        src.sendSuccess(()->Component.literal("[E6i] Up to 2-block cut/fill samples. No track certification; construction DISABLED, blocks changed=0."),false);
        return 1;
    }


    // DEV50e6j: MULTI-SCALE, READ-ONLY route viability.  Unlike e6i, choose
    // broad connected corridors before choosing ANY precise loop waypoints.
    // Eight-block coarse edges are only candidates if their 4-block midpoint
    // also passes the existing full 7-cross x 5-high earthworks clearance.
    // Coarse components are not a loop proof: no native track ports, junction
    // geometry, long-consist sweep, self-intersection or placement is certified.
    private static final int E6J_RADIUS=152, E6J_STRIDE=8, E6J_FINE_CAP=1500;
    private record E6jPoint(int x,int z,int y,int work,int sector) {
        BlockPos pos(){return new BlockPos(x,0,z);}
    }
    private record E6jIsland(int id,List<E6jPoint> points,int sectorMask,int minRadius,int maxRadius){}
    private static long dev50e6jKey(int x,int z){return new BlockPos(x,0,z).asLong();}
    private static int dev50e6jSector(int dx,int dz){
        // A quadrant is meaningful only well outside the village buffer.
        if(Math.abs(dx)<24||Math.abs(dz)<24)return -1;
        if(dx>=0)return dz<0?0:1; // NE, SE
        return dz>=0?2:3;         // SW, NW
    }
    private static E6jPoint dev50e6jCandidate(ServerLevel level,BlockPos village,int x,int z,
                                                Map<E6iKey,E6iScreen> screens){
        BlockPos at=new BlockPos(x,0,z);
        if(!level.hasChunkAt(at))return null;
        int dy=x-village.getX(),dz=z-village.getZ();
        if((long)dy*dy+(long)dz*dz<56L*56L)return null;
        int natural=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        E6jPoint best=null;
        for(int height=-1;height<=1;height++){
            int bed=natural+height;
            E6iScreen s=dev50e6iScreen(level,village,x,z,bed,screens);
            if(!s.passable())continue;
            E6jPoint p=new E6jPoint(x,z,bed,s.workCost(),dev50e6jSector(dy,dz));
            if(best==null||p.work()<best.work())best=p;
        }
        return best;
    }
    private static boolean dev50e6jLink(ServerLevel level,BlockPos village,E6jPoint a,
                                          E6jPoint b,Map<E6iKey,E6iScreen> screens){
        if(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())!=E6J_STRIDE)return false;
        if(Math.abs(a.y()-b.y())>2)return false;
        // Validate the intermediate, FOUR-block cross section as well as the
        // 8-block endpoints. A coarse edge cannot jump a sampled obstacle.
        int x=(a.x()+b.x())/2,z=(a.z()+b.z())/2;
        int y=(a.y()+b.y())/2;
        return dev50e6iScreen(level,village,x,z,y,screens).passable();
    }
    private static List<E6jIsland> dev50e6jIslands(ServerLevel level,BlockPos village,
                                                      Map<Long,E6jPoint> points,
                                                      Map<E6iKey,E6iScreen> screens){
        List<E6jIsland> islands=new ArrayList<>();
        Set<Long> seen=new java.util.HashSet<>();
        int[] dx={8,0,-8,0},dz={0,8,0,-8};
        for(E6jPoint start:points.values()){
            long key=dev50e6jKey(start.x(),start.z());
            if(!seen.add(key))continue;
            java.util.ArrayDeque<E6jPoint> pending=new java.util.ArrayDeque<>();
            List<E6jPoint> members=new ArrayList<>();
            int mask=0,lo=Integer.MAX_VALUE,hi=0;
            pending.add(start);
            while(!pending.isEmpty()){
                E6jPoint p=pending.removeFirst();members.add(p);
                if(p.sector()>=0)mask|=1<<p.sector();
                int r=Math.max(Math.abs(p.x()-village.getX()),Math.abs(p.z()-village.getZ()));
                lo=Math.min(lo,r);hi=Math.max(hi,r);
                for(int i=0;i<4;i++){
                    int nx=p.x()+dx[i],nz=p.z()+dz[i];
                    long nk=dev50e6jKey(nx,nz);
                    E6jPoint next=points.get(nk);
                    if(next!=null&&!seen.contains(nk)&&dev50e6jLink(level,village,p,next,screens)){
                        seen.add(nk);pending.addLast(next);
                    }
                }
            }
            islands.add(new E6jIsland(islands.size()+1,List.copyOf(members),mask,lo,hi));
        }
        islands.sort(Comparator.comparingInt((E6jIsland e)->-Integer.bitCount(e.sectorMask()))
                .thenComparingInt(e->-e.points().size()).thenComparingInt(E6jIsland::id));
        return islands;
    }
    private static E6jPoint[] dev50e6jWaypoints(E6jIsland island,BlockPos village,int radius){
        E6jPoint[] selected=new E6jPoint[4];
        int[] best={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE};
        for(E6jPoint p:island.points()){
            int quadrant=p.sector();if(quadrant<0)continue;
            int dx=Math.abs(p.x()-village.getX()),dz=Math.abs(p.z()-village.getZ());
            int r=Math.max(dx,dz);
            // Shift waypoints over both dimensions instead of requiring one
            // of the original four fixed N/E/S/W border coordinates.
            int cost=Math.abs(r-radius)*3+Math.abs(dx-dz)+Math.min(p.work(),150);
            if(cost<best[quadrant]){best[quadrant]=cost;selected[quadrant]=p;}
        }
        return selected;
    }
    private static String dev50e6jCoords(E6jPoint[] nodes){
        List<String> result=new ArrayList<>();
        for(E6jPoint p:nodes)result.add(p==null?"missing":p.x()+":"+p.y()+":"+p.z());
        return result.toString();
    }
    private static int dev50e6jCorridorCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6j corridorcheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6j] No verified loaded vanilla village nearby. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> realVillage=dev50e6bVillageStartAnchor(level,village,128);
        if(realVillage.isEmpty()){
            src.sendFailure(Component.literal("[E6j] No verified vanilla village structure anchor. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,realVillage.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6J] CORRIDOR_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6j] Natural vanilla village stays untouched."),false);return 1;
        }
        long start=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        Map<Long,E6jPoint> available=new java.util.LinkedHashMap<>();
        int sampled=0,missingChunk=0;
        // Bound both axis ranges and sample every eight blocks on the SAME
        // four-block lattice as the legacy fine planner. NO chunk tickets.
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=E6J_STRIDE)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=E6J_STRIDE){
                int x=village.getX()+dx,z=village.getZ()+dz;
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                sampled++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){missingChunk++;continue;}
                E6jPoint p=dev50e6jCandidate(level,village,x,z,screens);
                if(p!=null)available.put(dev50e6jKey(x,z),p);
            }
        List<E6jIsland> islands=dev50e6jIslands(level,village,available,screens);
        int spanning=0,bestLegs=0,bestSet=-1,bestIsland=-1,bestNodes=0,capped=0,expanded=0;
        int fineAttempts=0,fineSolved=0;
        List<String> islandInfo=new ArrayList<>();
        for(int i=0;i<Math.min(6,islands.size());i++){
            E6jIsland c=islands.get(i);
            islandInfo.add("id"+c.id()+":cells"+c.points().size()+":quadrants"+
                    Integer.bitCount(c.sectorMask())+":radius"+c.minRadius()+".."+c.maxRadius());
        }
        for(E6jIsland c:islands)if(c.sectorMask()==15)spanning++;
        // Only the best connected component is explored in detail, with two
        // different ring radii chosen from anywhere in its four quadrants.
        // The same waypoint serves both of its adjacent legs in a set.
        if(!islands.isEmpty()&&islands.get(0).sectorMask()==15){
            E6jIsland candidate=islands.get(0);
            for(int radius:new int[]{96,128}){
                E6jPoint[] nodes=dev50e6jWaypoints(candidate,village,radius);
                int legs=0,ex=0,limit=0;
                Map<E6iKey,E6iScreen> fineScreens=screens;
                for(int side=0;side<4;side++){
                    E6jPoint from=nodes[side],to=nodes[(side+1)%4];
                    if(from==null||to==null)continue;
                    // The earlier accepted fine solver tests 7-wide x5-high
                    // earthworks, 4-block cardinal moves, and grade <=1.
                    E6iRoute route=dev50e6iRoute(level,village,from.pos(),to.pos(),fineScreens,E6J_FINE_CAP,
                            from.y(),to.y());
                    fineAttempts++;ex+=route.expanded();expanded+=route.expanded();
                    if(route.capped()){limit++;capped++;}
                    if(route.reached()){legs++;fineSolved++;}
                    LOG.info("[TC-DEV50E6J] FINE_LEG village={} component={} ring={} fromQuadrant={} toQuadrant={} start={} end={} reached={} cells={} bends={} near={} expanded={} capped={} construction=DISABLED blocksChanged=0",
                            village,candidate.id(),radius,side,(side+1)%4,
                            from.pos(),to.pos(),route.reached(),route.routeCells(),route.bends(),
                            route.near(),route.expanded(),route.capped());
                }
                if(legs>bestLegs){bestLegs=legs;bestSet=radius;bestIsland=candidate.id();bestNodes=ex;}
                LOG.info("[TC-DEV50E6J] CORRIDOR_SET village={} component={} radius={} waypointCoords={} fineConnectedLegs={} of4 nodeExpansions={} limited={} joinedBySameWaypoints={} globalSimpleLoopProven=false nativePortsVerified=false construction=DISABLED blocksChanged=0",
                        village,candidate.id(),radius,dev50e6jCoords(nodes),legs,ex,limit,legs==4);
                if(legs==4)break; // still NOT native or simple-loop certified
            }
        }
        Map<String,Integer> rejects=new HashMap<>();int permitted=0;
        for(E6iScreen s:screens.values())if(s.passable())permitted++;
        else rejects.merge(s.rejected(),1,Integer::sum);
        int elapsed=(int)((System.nanoTime()-start)/1000000L);
        String verdict=bestLegs==4?"FOUR_FINE_LEGS_ONLY_NOT_LOOP_CERTIFIED"
                :spanning>0?"CONNECTED_COARSE_REGION_FINE_LEGS_INCOMPLETE"
                :"NO_FOUR_QUADRANT_COARSE_REGION";
        LOG.info("[TC-DEV50E6J] CORRIDOR_SUMMARY village={} style={} samples={} missingChunkCenters={} coarsePassable={} coarseComponents={} spanningFourQuadrants={} topComponents={} screenProfiles={} screenPassable={} screenRejects={} finePairAttempts={} finePairsSolved={} bestFineLegs={} bestRingRadius={} bestComponent={} bestSetExpanded={} totalFineExpanded={} capped={} elapsedMs={} verdict={} coarseIsNotNative=true globalSimpleLoopCertified=false nativeTurnPorts=false nativeSlopePorts=false fiveCarSweep=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,sampled,missingChunk,available.size(),islands.size(),spanning,
                islandInfo,screens.size(),permitted,dev50e6hTop(rejects,8),
                fineAttempts,fineSolved,bestLegs,bestSet,bestIsland,bestNodes,expanded,capped,elapsed,verdict);
        final String info="[E6j] Coarse regions="+islands.size()+" spanning all 4 quadrants="+spanning
                +". Fine route legs best="+bestLegs+"/4. See logs.";
        src.sendSuccess(()->Component.literal(info),false);
        src.sendSuccess(()->Component.literal("[E6j] Coarse regions do NOT certify connected native railway. Construction DISABLED; 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6k: CLOSED-CYCLE detection on the verified coarse corridor graph.
    // An ordinary connected component may be a TREE.  Its reach into four
    // quadrants does NOT imply a closed circuit surrounding the village.
    // Traverse directed planar half-edges to identify actual elementary faces,
    // remove spur backtracks, and check winding around the village center.
    // A matching face is only a HYPOTHETICAL eight-block grid cycle: validate
    // every intermediate FOUR-block bed/sample separately, WITHOUT world edits.
    // Native turn/slope blocks, their ports and five-car swept paths NOT certified.
    private static final int E6K_MIN_EDGES=24;
    private static final int[] E6K_DX={8,0,-8,0}, E6K_DZ={0,8,0,-8};
    private record E6kArrow(long origin,int direction) {}
    private record E6kCycle(List<E6jPoint> vertices,long signedArea2,int minRadius,
                            int maxRadius,int quadrants) {}
    private record E6kAudit(boolean passed,int fourBlockSteps,int bends,
                            long cutSamples,long fillSamples,long leafSamples,
                            long logSamples,long supportSamples,int workScore,String reason) {}
    private record E6kSurvey(int componentId,int cells,int edges,int leaves,int coreCells,
                             int faceWalks,int simpleCycles,int enclosingCycles,
                             E6kCycle chosen,E6kAudit audit) {}
    private static int dev50e6kPop(int mask){return Integer.bitCount(mask);}
    private static int dev50e6kDirection(E6jPoint a,E6jPoint b){
        if(b.x()>a.x())return 0;
        if(b.z()>a.z())return 1;
        if(b.x()<a.x())return 2;
        return 3;
    }
    private static long dev50e6kArea2(List<E6jPoint> points){
        long sum=0;
        for(int i=0;i<points.size();i++){
            E6jPoint a=points.get(i),b=points.get((i+1)%points.size());
            sum+=(long)a.x()*b.z()-(long)b.x()*a.z();
        }
        return sum;
    }
    private static boolean dev50e6kContains(List<E6jPoint> ring,int x,int z){
        boolean inside=false;
        for(int i=0,j=ring.size()-1;i<ring.size();j=i++){
            E6jPoint a=ring.get(i),b=ring.get(j);
            if((a.z()>z)!=(b.z()>z)){
                double crossed=a.x()+((double)z-a.z())*(b.x()-a.x())/(b.z()-a.z());
                if(crossed>x)inside=!inside;
            }
        }
        return inside;
    }
    private static E6kCycle dev50e6kCandidateCycle(List<E6jPoint> cycle,BlockPos village){
        if(cycle.size()<E6K_MIN_EDGES)return null;
        long area=dev50e6kArea2(cycle);
        // With coordinates [east,south,west,north], positive signed area is
        // the bounded right-hand face. The exterior is traced in reverse.
        if(area<=0)return null;
        if(!dev50e6kContains(cycle,village.getX(),village.getZ()))return null;
        int mask=0,lo=Integer.MAX_VALUE,hi=0;
        for(E6jPoint p:cycle){
            if(p.sector()>=0)mask|=1<<p.sector();
            int r=Math.max(Math.abs(p.x()-village.getX()),Math.abs(p.z()-village.getZ()));
            lo=Math.min(lo,r);hi=Math.max(hi,r);
        }
        if(mask!=15)return null;
        return new E6kCycle(List.copyOf(cycle),area,lo,hi,mask);
    }
    private static E6kAudit dev50e6kAudit(ServerLevel level,BlockPos village,E6kCycle cycle,
                                            Map<E6iKey,E6iScreen> screens){
        if(cycle==null)return new E6kAudit(false,0,0,0,0,0,0,0,0,"NO_ENCLOSING_FACE");
        int n=cycle.vertices().size(),bends=0,prev=-1;
        long cut=0,fill=0,leaf=0,log=0,support=0;
        int steps=0,score=0;
        Set<Long> seen=new java.util.HashSet<>();
        for(int i=0;i<n;i++){
            E6jPoint a=cycle.vertices().get(i),b=cycle.vertices().get((i+1)%n);
            if(!seen.add(dev50e6jKey(a.x(),a.z())))
                return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,"REPEATED_VERTEX");
            if(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())!=8)
                return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,"NON_CARDINAL_8_EDGE");
            int dir=dev50e6kDirection(a,b);
            if(prev>=0){int diff=Math.abs(dir-prev);if(Math.min(diff,4-diff)>1)
                return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,"U_TURN");
                if(diff!=0)bends++;
            }
            prev=dir;
            int mx=(a.x()+b.x())/2,mz=(a.z()+b.z())/2,my=(a.y()+b.y())/2;
            if(Math.abs(a.y()-my)>1||Math.abs(my-b.y())>1)
                return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,"FOUR_STEP_GRADE");
            for(int j=0;j<2;j++){
                int x=j==0?a.x():mx,z=j==0?a.z():mz,y=j==0?a.y():my;
                E6iScreen s=dev50e6iScreen(level,village,x,z,y,screens);
                if(!s.passable())return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,
                        "FULL_7x5_REJECT_"+s.rejected());
                cut+=s.cutSamples();fill+=s.fillSamples();leaf+=s.leafSamples();
                log+=s.logSamples();support+=s.supportSamples();score+=s.workCost();
                steps++;
            }
        }
        int first=dev50e6kDirection(cycle.vertices().get(n-1),cycle.vertices().get(0));
        int next=dev50e6kDirection(cycle.vertices().get(0),cycle.vertices().get(1));
        int turn=Math.abs(first-next);
        if(Math.min(turn,4-turn)>1)
            return new E6kAudit(false,steps,bends,cut,fill,leaf,log,support,score,"CLOSURE_U_TURN");
        if(first!=next)bends++;
        return new E6kAudit(true,steps,bends,cut,fill,leaf,log,support,score,"FOUR_BLOCK_SURVEY_ONLY");
    }
    private static E6kSurvey dev50e6kSurvey(ServerLevel level,BlockPos village,E6jIsland island,
                                              Map<E6iKey,E6iScreen> screens){
        Map<Long,E6jPoint> points=new java.util.LinkedHashMap<>();
        for(E6jPoint p:island.points())points.put(dev50e6jKey(p.x(),p.z()),p);
        Map<Long,Integer> adj=new HashMap<>();
        int degreeSum=0,leaves=0;
        for(E6jPoint p:island.points()){
            int mask=0;
            for(int d=0;d<4;d++){
                E6jPoint q=points.get(dev50e6jKey(p.x()+E6K_DX[d],p.z()+E6K_DZ[d]));
                if(q!=null&&dev50e6jLink(level,village,p,q,screens))mask|=1<<d;
            }
            adj.put(dev50e6jKey(p.x(),p.z()),mask);
            int degree=dev50e6kPop(mask);degreeSum+=degree;
            if(degree<=1)leaves++;
        }
        // Leaf-pruned 2-core: a nonempty 2-core is necessary but NOT sufficient
        // for a simple enclosing cycle. We still use planar face tracing.
        Map<Long,Integer> degree=new HashMap<>();
        java.util.ArrayDeque<Long> trim=new java.util.ArrayDeque<>();
        for(Map.Entry<Long,Integer> e:adj.entrySet()){
            int d=dev50e6kPop(e.getValue());degree.put(e.getKey(),d);
            if(d<2)trim.addLast(e.getKey());
        }
        Set<Long> removed=new java.util.HashSet<>();
        while(!trim.isEmpty()){
            long key=trim.removeFirst();
            if(!removed.add(key))continue;
            E6jPoint p=points.get(key);
            for(int dir=0;dir<4;dir++)if((adj.get(key)&(1<<dir))!=0){
                long other=dev50e6jKey(p.x()+E6K_DX[dir],p.z()+E6K_DZ[dir]);
                if(removed.contains(other))continue;
                int remain=degree.merge(other,-1,Integer::sum);
                if(remain<2)trim.addLast(other);
            }
        }
        Set<E6kArrow> traced=new java.util.HashSet<>();
        int faceWalks=0,simpleCycles=0,enclosing=0;
        E6kCycle best=null;E6kAudit bestAudit=null;
        int arrowLimit=degreeSum+8;
        // Deterministic traversal of every directed planar edge; each arrow
        // belongs to one face boundary. No exponential simple-cycle search.
        for(E6jPoint p:island.points()){
            long base=dev50e6jKey(p.x(),p.z());int mask=adj.get(base);
            for(int direction=0;direction<4;direction++)if((mask&(1<<direction))!=0){
                E6kArrow first=new E6kArrow(base,direction);
                if(traced.contains(first))continue;
                E6kArrow now=first;
                List<E6jPoint> walk=new ArrayList<>();
                boolean closed=false;
                for(int step=0;step<arrowLimit;step++){
                    if(traced.contains(now)){
                        closed=now.equals(first);break;
                    }
                    traced.add(now);
                    E6jPoint here=points.get(now.origin());
                    if(here==null)break;
                    walk.add(here);
                    long nextKey=dev50e6jKey(here.x()+E6K_DX[now.direction()],
                                                  here.z()+E6K_DZ[now.direction()]);
                    int nextMask=adj.getOrDefault(nextKey,0);
                    int reverse=(now.direction()+2)&3,nextDir=-1;
                    for(int shift=1;shift<=4;shift++){
                        int d=(reverse+shift)&3;
                        if((nextMask&(1<<d))!=0){nextDir=d;break;}
                    }
                    if(nextDir<0)break;
                    now=new E6kArrow(nextKey,nextDir);
                    if(now.equals(first)){closed=true;break;}
                }
                if(!closed)continue;
                faceWalks++;
                // Face walks around branch spurs can repeat vertices. Extract
                // simple cycles using stack-based loop erasure, O(walk length).
                List<E6jPoint> stack=new ArrayList<>();
                Map<Long,Integer> positions=new HashMap<>();
                for(int i=0;i<=walk.size();i++){
                    E6jPoint v=walk.get(i%walk.size());
                    long key=dev50e6jKey(v.x(),v.z());
                    Integer at=positions.get(key);
                    if(at==null){positions.put(key,stack.size());stack.add(v);continue;}
                    if(stack.size()-at>=4){
                        simpleCycles++;
                        E6kCycle candidate=dev50e6kCandidateCycle(
                                new ArrayList<>(stack.subList(at,stack.size())),village);
                        if(candidate!=null){
                            enclosing++;
                            E6kAudit audit=dev50e6kAudit(level,village,candidate,screens);
                            // Prefer a complete four-block screen, then fewer
                            // bends/total work; neither is native track proof.
                            long score=(long)candidate.vertices().size()*100
                                    +(audit==null?0:(long)audit.bends()*60)
                                    +(audit==null?0:audit.workScore()/30);
                            long bestScore=best==null?Long.MAX_VALUE
                                    :(long)best.vertices().size()*100+(long)bestAudit.bends()*60
                                    +bestAudit.workScore()/30;
                            if(best==null||(audit.passed()&&!bestAudit.passed())
                                    ||(audit.passed()==bestAudit.passed()&&score<bestScore)){
                                best=candidate;bestAudit=audit;
                            }
                        }
                    }
                    for(int drop=stack.size()-1;drop>at;drop--){
                        E6jPoint gone=stack.remove(drop);
                        positions.remove(dev50e6jKey(gone.x(),gone.z()));
                    }
                }
            }
        }
        return new E6kSurvey(island.id(),island.points().size(),degreeSum/2,leaves,
                island.points().size()-removed.size(),faceWalks,simpleCycles,enclosing,best,bestAudit);
    }
    private static int dev50e6kCycleCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6k cyclecheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6k] No verified loaded vanilla village nearby. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> origin=dev50e6bVillageStartAnchor(level,village,128);
        if(origin.isEmpty()){
            src.sendFailure(Component.literal("[E6k] No verified natural village structure anchor. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,origin.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6K] CYCLE_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6k] Natural vanilla village stays untouched."),false);return 1;
        }
        long start=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        Map<Long,E6jPoint> available=new java.util.LinkedHashMap<>();
        int sampled=0,unloadedCenters=0;
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=E6J_STRIDE)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=E6J_STRIDE){
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                int x=village.getX()+dx,z=village.getZ()+dz;sampled++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){unloadedCenters++;continue;}
                E6jPoint candidate=dev50e6jCandidate(level,village,x,z,screens);
                if(candidate!=null)available.put(dev50e6jKey(x,z),candidate);
            }
        List<E6jIsland> islands=dev50e6jIslands(level,village,available,screens);
        int fourSpan=0;
        for(E6jIsland island:islands)if(island.sectorMask()==15)fourSpan++;
        E6kSurvey best=null;
        int checked=0,totalFaces=0,totalCycles=0,totalEnclosing=0;
        for(E6jIsland island:islands){
            if(checked>=3)break;
            if(island.points().size()<E6K_MIN_EDGES)continue;
            E6kSurvey result=dev50e6kSurvey(level,village,island,screens);checked++;
            totalFaces+=result.faceWalks();totalCycles+=result.simpleCycles();
            totalEnclosing+=result.enclosingCycles();
            LOG.info("[TC-DEV50E6K] COMPONENT village={} id={} cells={} edges={} deadEnds={} twoCoreCells={} faceWalks={} simpleFaces={} enclosingCycles={} quadrantCoverage={} placement=DISABLED blocksChanged=0",
                    village,result.componentId(),result.cells(),result.edges(),result.leaves(),
                    result.coreCells(),result.faceWalks(),result.simpleCycles(),result.enclosingCycles(),
                    Integer.bitCount(island.sectorMask()));
            if(result.chosen()!=null&&(best==null||best.chosen()==null
                    ||(result.audit().passed()&&!best.audit().passed())
                    ||(result.audit().passed()==best.audit().passed()
                        &&result.chosen().vertices().size()<best.chosen().vertices().size())))best=result;
        }
        String verdict="NO_ENCLOSING_SIMPLE_CYCLE";
        if(best!=null&&best.chosen()!=null){
            E6kCycle cycle=best.chosen();
            E6kAudit audit=best.audit();
            verdict=audit.passed()?"ENCLOSING_4BLOCK_SURVEY_CYCLE_NOT_NATIVE"
                    :"ENCLOSING_COARSE_CYCLE_FINE_AUDIT_FAILED";
            LOG.info("[TC-DEV50E6K] CYCLE_CANDIDATE village={} component={} coarseVertices={} fourBlockSteps={} turns={} area2={} radius={}..{} quadrantCoverage={} fineScreenPass={} reason={} cutCellSamples={} fillCellSamples={} vegetationLeafSamples={} vegetationLogSamples={} supportSamples={} workScore={} nativeTurns=false nativeSlopes=false fiveCarSweep=false construction=DISABLED blocksChanged=0",
                    village,best.componentId(),cycle.vertices().size(),audit.fourBlockSteps(),
                    audit.bends(),cycle.signedArea2(),cycle.minRadius(),cycle.maxRadius(),
                    Integer.bitCount(cycle.quadrants()),audit.passed(),audit.reason(),
                    audit.cutSamples(),audit.fillSamples(),audit.leafSamples(),
                    audit.logSamples(),audit.supportSamples(),audit.workScore());
            // Bounded inspection points only: never claim construction approval.
            List<String> examples=new ArrayList<>();
            int n=cycle.vertices().size();
            for(int i=0;i<Math.min(8,n);i++){
                E6jPoint p=cycle.vertices().get(i*n/Math.min(8,n));
                examples.add(p.x()+":"+p.y()+":"+p.z());
            }
            LOG.info("[TC-DEV50E6K] CYCLE_SAMPLES village={} samplePoints={} samplesNotPlacementBlueprint=true",village,examples);
        }
        final int duration=(int)((System.nanoTime()-start)/1000000L);
        Map<String,Integer> rejects=new HashMap<>();int passableProfiles=0;
        for(E6iScreen s:screens.values())if(s.passable())passableProfiles++;
        else rejects.merge(s.rejected(),1,Integer::sum);
        final String finalVerdict=verdict;
        LOG.info("[TC-DEV50E6K] CYCLE_SUMMARY village={} style={} coarseSamples={} coarsePassable={} coarseComponents={} fourQuadrantComponents={} componentsInspected={} graphFaces={} simpleCycles={} enclosingCycles={} selectedComponent={} auditedFull4BlockCycle={} screenProfiles={} screenPassable={} rejectTop={} elapsedMs={} verdict={} native90Turns=false nativeSlopes=false fiveCarSweep=false globalNativeLoopCertified=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,sampled,available.size(),islands.size(),fourSpan,checked,totalFaces,
                totalCycles,totalEnclosing,best==null?-1:best.componentId(),
                best!=null&&best.audit().passed(),screens.size(),passableProfiles,
                dev50e6hTop(rejects,7),duration,finalVerdict);
        final int reportedFourSpan=fourSpan,reportedEnclosing=totalEnclosing;
        src.sendSuccess(()->Component.literal("[E6k] Coarse regions="+islands.size()
                +"; 4-quadrant regions="+reportedFourSpan+"; enclosing cycles="+reportedEnclosing),false);
        src.sendSuccess(()->Component.literal("[E6k] "+finalVerdict+". No native track approval; 0 blocks changed."),false);
        return 1;
    }


    // DEV50e6l: READ-ONLY missing-link isolation on hypothetical square
    // trackbed rings. Face boundaries alone may miss a larger union-of-faces
    // enclosure. These rings are DIAGNOSTIC geometries, not native rail curves.
    // NEVER excavate, bridge, place blocks, force chunks, or alter worldgen.
    private static final int[] E6L_RADII={64,80,96,112,128,144};
    private record E6lGap(int firstEdge,int edgeCount,int startX,int startZ,
                           int endX,int endZ,String principal,String review,
                           String detour){}
    private record E6lRing(int radius,int links,int clearLinks,int failedLinks,
                            int longestGap,List<E6lGap> gaps,Map<String,Integer> reasons,
                            int detours){}
    private record E6lFrontier(int x1,int z1,int x2,int z2,int distance,
                                int component1,int component2,String reason){}
    private static String dev50e6lCellFailure(ServerLevel level,BlockPos village,
                                               int x,int z,Map<E6iKey,E6iScreen> screens){
        if(!level.hasChunkAt(new BlockPos(x,0,z)))return "UNLOADED";
        int terrain=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        Map<String,Integer> counts=new HashMap<>();
        for(int delta=-1;delta<=1;delta++){
            E6iScreen check=dev50e6iScreen(level,village,x,z,terrain+delta,screens);
            if(check.passable())return "AVAILABLE_ALTERNATE_HEIGHT";
            counts.merge(check.rejected(),1,Integer::sum);
        }
        // Prefer safety-related reasons when the selected bed height is
        // ambiguous. This is an explanatory hint, not a ground truth repair.
        for(String priority:new String[]{"UNLOADED","VILLAGE_STRUCTURE",
                "VILLAGE_BUFFER","WATER_OR_FLUID","NON_NATURAL_BLOCK",
                "VEGETATION_CAP","CUT_DEPTH","FILL_DEPTH"})
            if(counts.containsKey(priority))return priority;
        return counts.isEmpty()?"NO_PASSABLE_BED":counts.keySet().iterator().next();
    }
    private static String dev50e6lEdgeFailure(ServerLevel level,BlockPos village,
                                               E6jPoint a,E6jPoint b,
                                               int ax,int az,int bx,int bz,
                                               Map<E6iKey,E6iScreen> screens){
        if(a==null)return "START_"+dev50e6lCellFailure(level,village,ax,az,screens);
        if(b==null)return "END_"+dev50e6lCellFailure(level,village,bx,bz,screens);
        if(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())!=8)return "NON_ADJACENT";
        if(Math.abs(a.y()-b.y())>2)return "BED_HEIGHT_MISMATCH";
        int mx=(ax+bx)/2,mz=(az+bz)/2,my=(a.y()+b.y())/2;
        E6iScreen s=dev50e6iScreen(level,village,mx,mz,my,screens);
        return s.passable()?"CLEAR":"MIDPOINT_"+s.rejected();
    }
    private static String dev50e6lReview(String reason){
        if(reason.contains("UNLOADED"))return "STOP_UNLOADED";
        if(reason.contains("VILLAGE_STRUCTURE")||reason.contains("VILLAGE_BUFFER"))
            return "NO_TOUCH_VILLAGE";
        if(reason.contains("NON_NATURAL_BLOCK"))return "NO_TOUCH_NON_NATURAL";
        if(reason.contains("WATER_OR_FLUID"))return "BRIDGE_DESIGN_REVIEW_ONLY";
        if(reason.contains("CUT_DEPTH"))return "DEEPER_CUT_REVIEW_ONLY";
        if(reason.contains("FILL_DEPTH"))return "EMBANKMENT_REVIEW_ONLY";
        if(reason.contains("VEGETATION_CAP"))return "VEGETATION_REVIEW_ONLY";
        if(reason.contains("BED_HEIGHT_MISMATCH"))return "ALT_BED_HEIGHT_REVIEW_ONLY";
        return "ALTERNATE_ROUTE_REVIEW_ONLY";
    }
    private static List<int[]> dev50e6lSquare(BlockPos village,int radius){
        List<int[]> ring=new ArrayList<>();
        int x=village.getX(),z=village.getZ();
        // Each side has radius/4 distinct vertices; corners are not repeated.
        for(int t=-radius;t<radius;t+=8)ring.add(new int[]{x+radius,z+t});
        for(int t=-radius;t<radius;t+=8)ring.add(new int[]{x-t,z+radius});
        for(int t=-radius;t<radius;t+=8)ring.add(new int[]{x-radius,z-t});
        for(int t=-radius;t<radius;t+=8)ring.add(new int[]{x+t,z-radius});
        return ring;
    }
    private static String dev50e6lDetour(ServerLevel level,BlockPos village,
                                          List<int[]> ring,int start,int length,int radius,
                                          Map<Long,E6jPoint> available,
                                          Map<E6iKey,E6iScreen> screens){
        if(length>6)return "NONE_LONG_GAP";
        int n=ring.size(),sideCount=radius/4;
        int end=(start+length)%n;
        // A one-cell outward/inward jog works only along one straight side.
        if(start/sideCount!=end/sideCount)return "NONE_CROSSES_CORNER";
        int side=start/sideCount;
        int dx=side==0?1:side==2?-1:0;
        int dz=side==1?1:side==3?-1:0;
        for(int sign:new int[]{1,-1}){
            E6jPoint prior=available.get(dev50e6jKey(ring.get(start)[0],ring.get(start)[1]));
            if(prior==null)continue;
            boolean good=true;
            for(int i=0;i<=length;i++){
                int[] p=ring.get((start+i)%n);
                E6jPoint shifted=available.get(dev50e6jKey(p[0]+sign*dx*8,p[1]+sign*dz*8));
                if(shifted==null){good=false;break;}
                if(!dev50e6jLink(level,village,prior,shifted,screens)){
                    good=false;break;
                }
                prior=shifted;
            }
            if(!good)continue;
            int[] last=ring.get(end);
            E6jPoint endpoint=available.get(dev50e6jKey(last[0],last[1]));
            if(endpoint!=null&&dev50e6jLink(level,village,prior,endpoint,screens))
                return sign>0?"OUTWARD_8BLOCK_JOG_SURVEY_ONLY":"INWARD_8BLOCK_JOG_SURVEY_ONLY";
        }
        return "NO_SINGLE_CELL_BYPASS";
    }
    private static E6lRing dev50e6lRing(ServerLevel level,BlockPos village,int radius,
                                          Map<Long,E6jPoint> available,
                                          Map<E6iKey,E6iScreen> screens){
        List<int[]> points=dev50e6lSquare(village,radius);
        int n=points.size();
        String[] failures=new String[n];int clear=0;
        Map<String,Integer> reasons=new HashMap<>();
        for(int i=0;i<n;i++){
            int[] a=points.get(i),b=points.get((i+1)%n);
            failures[i]=dev50e6lEdgeFailure(level,village,
                    available.get(dev50e6jKey(a[0],a[1])),
                    available.get(dev50e6jKey(b[0],b[1])),
                    a[0],a[1],b[0],b[1],screens);
            if(failures[i].equals("CLEAR"))clear++;
            else reasons.merge(failures[i],1,Integer::sum);
        }
        List<E6lGap> gaps=new ArrayList<>();int biggest=0,detours=0;
        if(clear==0){
            // An entirely blocked perimeter has no meaningful gap endpoints.
            int[] a=points.get(0);
            gaps.add(new E6lGap(0,n,a[0],a[1],a[0],a[1],
                    "ALL_LINKS_BLOCKED","ROUTE_REDESIGN_REQUIRED","NONE"));
            biggest=n;
        }else{
            int firstGood=0;
            while(!failures[firstGood].equals("CLEAR"))firstGood++;
            int pendingStart=-1,length=0;
            for(int k=1;k<=n;k++){
                int at=(firstGood+k)%n;
                boolean bad=!failures[at].equals("CLEAR");
                if(bad){if(pendingStart<0)pendingStart=at;length++;}
                else if(pendingStart>=0){
                    int[] a=points.get(pendingStart),b=points.get(at);
                    String reason=failures[pendingStart];
                    String detour=dev50e6lDetour(level,village,points,pendingStart,length,
                            radius,available,screens);
                    if(detour.contains("JOG_SURVEY_ONLY"))detours++;
                    gaps.add(new E6lGap(pendingStart,length,a[0],a[1],b[0],b[1],
                            reason,dev50e6lReview(reason),detour));
                    biggest=Math.max(biggest,length);
                    pendingStart=-1;length=0;
                }
            }
        }
        gaps.sort(Comparator.comparingInt(E6lGap::edgeCount)
                .thenComparingInt(E6lGap::firstEdge));
        return new E6lRing(radius,n,clear,n-clear,biggest,List.copyOf(gaps),reasons,detours);
    }
    private static List<E6lFrontier> dev50e6lFrontiers(ServerLevel level,
                                    BlockPos village,Map<Long,E6jPoint> available,
                                    List<E6jIsland> islands,Map<E6iKey,E6iScreen> screens){
        Map<Long,Integer> ids=new HashMap<>();
        for(E6jIsland island:islands)for(E6jPoint p:island.points())
            ids.put(dev50e6jKey(p.x(),p.z()),island.id());
        List<E6lFrontier> found=new ArrayList<>();
        // Straight 16- and 24-block gaps only; no imaginary diagonal rails.
        for(E6jPoint a:available.values())for(int direction:new int[]{0,1})
            for(int span:new int[]{16,24}){
                int bx=a.x()+(direction==0?span:0),bz=a.z()+(direction==1?span:0);
                E6jPoint b=available.get(dev50e6jKey(bx,bz));
                if(b==null)continue;
                int ai=ids.getOrDefault(dev50e6jKey(a.x(),a.z()),-1);
                int bi=ids.getOrDefault(dev50e6jKey(bx,bz),-1);
                if(ai<0||bi<0||ai==bi)continue;
                String reason="UNKNOWN_GAP";
                E6jPoint previous=a;
                for(int delta=8;delta<=span;delta+=8){
                    int x=a.x()+(direction==0?delta:0);
                    int z=a.z()+(direction==1?delta:0);
                    E6jPoint current=available.get(dev50e6jKey(x,z));
                    if(current==null){reason=dev50e6lCellFailure(level,village,x,z,screens);break;}
                    if(!dev50e6jLink(level,village,previous,current,screens)){
                        reason=dev50e6lEdgeFailure(level,village,previous,current,
                                previous.x(),previous.z(),x,z,screens);break;
                    }
                    previous=current;
                }
                found.add(new E6lFrontier(a.x(),a.z(),bx,bz,span,ai,bi,reason));
            }
        found.sort(Comparator.comparingInt(E6lFrontier::distance)
                .thenComparingInt(p->Math.abs(p.x1()-village.getX())+
                                    Math.abs(p.z1()-village.getZ())));
        return found;
    }
    private static int dev50e6lMissingLinksCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6l missinglinkscheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6l] No nearby verified loaded vanilla village. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> origin=dev50e6bVillageStartAnchor(level,village,128);
        if(origin.isEmpty()){
            src.sendFailure(Component.literal("[E6l] Village structure start unverified. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,origin.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6L] GAP_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6l] Natural vanilla village stays untouched."),false);return 1;
        }
        long begin=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        Map<Long,E6jPoint> available=new java.util.LinkedHashMap<>();
        int sampled=0,missing=0;
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=8)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=8){
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                int x=village.getX()+dx,z=village.getZ()+dz;
                sampled++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){missing++;continue;}
                E6jPoint p=dev50e6jCandidate(level,village,x,z,screens);
                if(p!=null)available.put(dev50e6jKey(x,z),p);
            }
        List<E6jIsland> islands=dev50e6jIslands(level,village,available,screens);
        List<E6lRing> rings=new ArrayList<>();
        for(int radius:E6L_RADII){
            E6lRing ring=dev50e6lRing(level,village,radius,available,screens);
            rings.add(ring);
            LOG.info("[TC-DEV50E6L] RING village={} radius={} links={} clearLinks={} failedLinks={} gapRuns={} longestGapEdges={} oneCellDetourRuns={} rejectionTop={} ringIsNative=false construction=DISABLED blocksChanged=0",
                    village,ring.radius(),ring.links(),ring.clearLinks(),ring.failedLinks(),
                    ring.gaps().size(),ring.longestGap(),ring.detours(),
                    dev50e6hTop(ring.reasons(),6));
        }
        rings.sort(Comparator.comparingInt(E6lRing::failedLinks)
                .thenComparingInt(E6lRing::longestGap)
                .thenComparingInt(E6lRing::radius));
        E6lRing primary=rings.get(0);
        // Always report a second perimeter at a distinct radius, to avoid
        // choosing one lucky ring and hiding a potentially simpler neighbor.
        for(int i=0;i<Math.min(2,rings.size());i++){
            E6lRing ring=rings.get(i);
            int shown=0;
            for(E6lGap gap:ring.gaps()){
                if(shown++>=8)break;
                LOG.info("[TC-DEV50E6L] GAP village={} radius={} from={}:{} to={}:{} failedEdges={} spanBlocksApprox={} firstReason={} option={} singleJog={} noWorldEdits=true",
                        village,ring.radius(),gap.startX(),gap.startZ(),gap.endX(),gap.endZ(),
                        gap.edgeCount(),gap.edgeCount()*8,gap.principal(),gap.review(),gap.detour());
            }
        }
        List<E6lFrontier> frontiers=dev50e6lFrontiers(level,village,available,islands,screens);
        int report=0;
        for(E6lFrontier f:frontiers){
            if(report++>=8)break;
            LOG.info("[TC-DEV50E6L] FRONTIER village={} from={}:{} to={}:{} betweenComponents={}->{} spanBlocks={} firstBarrier={} option={} onlyGraphGap=true noNativeApproval=true",
                    village,f.x1(),f.z1(),f.x2(),f.z2(),f.component1(),f.component2(),
                    f.distance(),f.reason(),dev50e6lReview(f.reason()));
        }
        Map<String,Integer> rejections=new HashMap<>();int pass=0;
        for(E6iScreen s:screens.values())if(s.passable())pass++;
        else rejections.merge(s.rejected(),1,Integer::sum);
        int elapsed=(int)((System.nanoTime()-begin)/1000000L);
        String verdict=primary.failedLinks()==0?"UNEXPECTED_CLOSED_SQUARE_SURVEY_ONLY"
                :"BROKEN_RINGS_GAPS_IDENTIFIED_NOT_TRACK_APPROVED";
        LOG.info("[TC-DEV50E6L] MISSING_LINK_SUMMARY village={} style={} surveyCenters={} missingChunks={} coarsePassable={} coarseComponents={} inspectedRings={} bestRingRadius={} bestRingClearLinks={} bestRingFailedLinks={} bestRingGapRuns={} bestRingLongestGapEdges={} bestRingSingleJogDetours={} componentFrontierCandidates={} samples={} samplePassable={} topRejects={} elapsedMs={} verdict={} noTerrainMutation=true noBridgesPlaced=true native90Turns=false nativeSlopes=false fiveCarSweep=false loopNotCertified=true construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,sampled,missing,available.size(),islands.size(),rings.size(),
                primary.radius(),primary.clearLinks(),primary.failedLinks(),primary.gaps().size(),
                primary.longestGap(),primary.detours(),frontiers.size(),screens.size(),pass,
                dev50e6hTop(rejections,6),elapsed,verdict);
        final int bestRadius=primary.radius(),bad=primary.failedLinks(),
                found=frontiers.size(),shortcuts=primary.detours();
        src.sendSuccess(()->Component.literal("[E6l] Best surveyed square radius="+bestRadius
                +"; blocked links="+bad+"; short bypass candidates="+shortcuts),false);
        src.sendSuccess(()->Component.literal("[E6l] Nearby component gaps="+found
                +". All options are DIAGNOSTIC; construction disabled; blocks changed=0."),false);
        return 1;
    }


    // DEV50e6m: READ-ONLY flexible cycle assembler. Instead of forcing a
    // square perimeter, search a seven-cross/five-high 8-block corridor graph
    // with SEVERAL independently screened bed-height choices at each location.
    // An enclosing loop must cross an east-going ray an odd number of times:
    // remove all such ray crossings, find an alternate path joining both
    // endpoints of a single removed crossing, then close the walk using that
    // crossing. This finds larger loops spanning MULTIPLE graph faces, which
    // e6k's single-face enumeration could not establish. A closed path is still
    // a SURVEY ONLY, not an approved native curve / grade / long-consist path.
    private static final int E6M_MAX_SEAMS=28, E6M_MAX_SEARCH=22000;
    private record E6mNode(int x,int z,int y,int work) {
        long key(){return new BlockPos(x,y,z).asLong();}
        E6jPoint surveyPoint(BlockPos village){return new E6jPoint(x,z,y,work,
                dev50e6jSector(x-village.getX(),z-village.getZ()));}
    }
    private record E6mSeam(int a,int b,int x,int estimate) {}
    private record E6mQueue(int state,int cost) {}
    private record E6mPath(List<Integer> nodes,int explored,int nearest,boolean capped,int score) {}
    private record E6mCandidate(List<E6jPoint> vertices,E6kAudit audit,
                                int seamX,int expanded,int searchScore) {}
    private static boolean dev50e6mRayCross(E6mNode a,E6mNode b,BlockPos village){
        return a.x()==b.x() && a.x()>=village.getX()+64
                && ((a.z()==village.getZ() && b.z()==village.getZ()+8)
                    ||(b.z()==village.getZ() && a.z()==village.getZ()+8));
    }
    private static int dev50e6mDirection(E6mNode a,E6mNode b){
        if(b.x()>a.x())return 0;
        if(b.z()>a.z())return 1;
        if(b.x()<a.x())return 2;
        return 3;
    }
    private static boolean dev50e6mLink(ServerLevel level,BlockPos village,
            E6mNode a,E6mNode b,Map<E6iKey,E6iScreen> screens){
        if(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())!=8)return false;
        if(Math.abs(a.y()-b.y())>2)return false;
        // One block / four horizontal blocks, including the shared midpoint.
        int midY=Math.floorDiv(a.y()+b.y(),2);
        if(Math.abs(a.y()-midY)>1||Math.abs(b.y()-midY)>1)return false;
        return dev50e6iScreen(level,village,(a.x()+b.x())/2,(a.z()+b.z())/2,
                midY,screens).passable();
    }
    private static E6mPath dev50e6mWalk(List<E6mNode> nodes,
            List<List<Integer>> links,BlockPos village,int start,int goal){
        // Heading-aware Dijkstra prohibits instantaneous 180-degree U turns.
        // The ray-crossing edge that would close the loop is explicitly absent
        // from the walked graph, so any successful path is a candidate cycle.
        int count=nodes.size()*5;
        int[] distance=new int[count],parent=new int[count];
        java.util.Arrays.fill(distance,Integer.MAX_VALUE);
        java.util.Arrays.fill(parent,-1);
        java.util.PriorityQueue<E6mQueue> open=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6mQueue::cost).thenComparingInt(E6mQueue::state));
        int seed=start*5+4;distance[seed]=0;open.add(new E6mQueue(seed,0));
        int explored=0,nearest=Integer.MAX_VALUE,finish=-1;
        while(!open.isEmpty() && explored<E6M_MAX_SEARCH){
            E6mQueue q=open.poll();int s=q.state();
            if(q.cost()!=distance[s])continue;
            int id=s/5,heading=s%5;
            E6mNode here=nodes.get(id);explored++;
            E6mNode target=nodes.get(goal);
            nearest=Math.min(nearest,Math.abs(here.x()-target.x())+Math.abs(here.z()-target.z()));
            if(id==goal){finish=s;break;}
            for(int next:links.get(id)){
                E6mNode there=nodes.get(next);
                if(dev50e6mRayCross(here,there,village))continue;
                int dir=dev50e6mDirection(here,there);
                if(heading!=4 && Math.abs(dir-heading)==2)continue;
                int ns=next*5+dir;
                int cost=q.cost()+100+there.work()*2
                        +Math.abs(there.y()-here.y())*14
                        +(heading==4||heading==dir?0:40);
                if(cost>=distance[ns])continue;
                distance[ns]=cost;parent[ns]=s;
                open.add(new E6mQueue(ns,cost));
            }
        }
        if(finish<0)return new E6mPath(List.of(),explored,
                nearest==Integer.MAX_VALUE?-1:nearest,explored>=E6M_MAX_SEARCH,0);
        List<Integer> path=new ArrayList<>();
        for(int p=finish;p!=-1;p=parent[p])path.add(p/5);
        java.util.Collections.reverse(path);
        return new E6mPath(List.copyOf(path),explored,0,false,distance[finish]);
    }
    private static E6mCandidate dev50e6mAssess(ServerLevel level,BlockPos village,
            List<E6mNode> nodes,E6mSeam seam,E6mPath path,
            Map<E6iKey,E6iScreen> screens){
        if(path.nodes().size()<E6K_MIN_EDGES)return null;
        Set<Long> flat=new java.util.HashSet<>();
        List<E6jPoint> ring=new ArrayList<>();
        for(int id:path.nodes()){
            E6mNode node=nodes.get(id);
            if(!flat.add(dev50e6jKey(node.x(),node.z())))return null;
            ring.add(node.surveyPoint(village));
        }
        // The final ring edge is the verified seam link; no repeated vertices.
        if(Math.abs(nodes.get(seam.a()).y()-nodes.get(seam.b()).y())>2)return null;
        if(dev50e6kArea2(ring)<0)java.util.Collections.reverse(ring);
        E6kCycle cycle=dev50e6kCandidateCycle(ring,village);
        if(cycle==null)return null;
        E6kAudit audit=dev50e6kAudit(level,village,cycle,screens);
        return new E6mCandidate(List.copyOf(ring),audit,seam.x(),
                path.explored(),path.score());
    }
    private static int dev50e6mFlexibleCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6m flexiblecheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6m] No verified loaded village nearby. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> origin=dev50e6bVillageStartAnchor(level,village,128);
        if(origin.isEmpty()){
            src.sendFailure(Component.literal("[E6m] No verified village structure start. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,origin.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6M] FLEXIBLE_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6m] Natural vanilla village stays untouched."),false);return 1;
        }
        long time=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        List<E6mNode> nodes=new ArrayList<>();
        Map<Long,List<Integer>> byXZ=new HashMap<>();
        int centers=0,unloaded=0;
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=8)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=8){
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                int x=village.getX()+dx,z=village.getZ()+dz;centers++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){unloaded++;continue;}
                int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int adjustment=-2;adjustment<=2;adjustment++){
                    int bed=ground+adjustment;
                    E6iScreen result=dev50e6iScreen(level,village,x,z,bed,screens);
                    if(!result.passable())continue;
                    int id=nodes.size();
                    E6mNode node=new E6mNode(x,z,bed,result.workCost());nodes.add(node);
                    byXZ.computeIfAbsent(dev50e6jKey(x,z),ignored->new ArrayList<>()).add(id);
                }
            }
        List<List<Integer>> adj=new ArrayList<>();
        for(int i=0;i<nodes.size();i++)adj.add(new ArrayList<>());
        int links=0,rayLinks=0;
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            for(int dir:new int[]{0,1}){
                int x=a.x()+(dir==0?8:0),z=a.z()+(dir==1?8:0);
                List<Integer> possible=byXZ.get(dev50e6jKey(x,z));
                if(possible==null)continue;
                for(int j:possible){
                    E6mNode b=nodes.get(j);
                    if(!dev50e6mLink(level,village,a,b,screens))continue;
                    adj.get(i).add(j);adj.get(j).add(i);links++;
                    if(dev50e6mRayCross(a,b,village))rayLinks++;
                }
            }
        }
        List<E6mSeam> seams=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            if(a.z()!=village.getZ()||a.x()<village.getX()+64)continue;
            for(int j:adj.get(i)){
                E6mNode b=nodes.get(j);
                if(dev50e6mRayCross(a,b,village)&&b.z()>a.z())
                    seams.add(new E6mSeam(i,j,a.x(),a.work()+b.work()
                            +8*Math.abs(a.y()-b.y())));
            }
        }
        seams.sort(Comparator.comparingInt(E6mSeam::estimate)
                .thenComparingInt(s->Math.abs(s.x()-village.getX()-96))
                .thenComparingInt(E6mSeam::x));
        int attempts=0,coarsePaths=0,finePass=0,expanded=0,nearest=Integer.MAX_VALUE;
        int rejectedProject=0,rejectedAudit=0,capped=0,quadrantsSeen=0;
        Map<Integer,Integer> bySeamX=new HashMap<>();
        E6mCandidate selected=null;
        for(E6mSeam seam:seams){
            if(attempts>=E6M_MAX_SEAMS)break;
            if(bySeamX.getOrDefault(seam.x(),0)>=2)continue;
            bySeamX.merge(seam.x(),1,Integer::sum);attempts++;
            E6mPath path=dev50e6mWalk(nodes,adj,village,seam.a(),seam.b());
            expanded+=path.explored();
            if(path.nearest()>=0)nearest=Math.min(nearest,path.nearest());
            if(path.capped())capped++;
            if(path.nodes().isEmpty())continue;
            coarsePaths++;
            E6mCandidate candidate=dev50e6mAssess(level,village,nodes,seam,path,screens);
            if(candidate==null){rejectedProject++;continue;}
            if(!candidate.audit().passed()){rejectedAudit++;continue;}
            finePass++;
            int score=candidate.vertices().size()*100
                    +candidate.audit().bends()*50+candidate.audit().workScore()/2;
            int existing=selected==null?Integer.MAX_VALUE
                    :selected.vertices().size()*100+selected.audit().bends()*50
                    +selected.audit().workScore()/2;
            if(selected==null||score<existing)selected=candidate;
        }
        if(selected!=null){
            E6kAudit a=selected.audit();
            List<E6jPoint> points=selected.vertices();
            List<String> examples=new ArrayList<>();
            for(int i=0;i<Math.min(12,points.size());i++){
                E6jPoint p=points.get(i*points.size()/Math.min(12,points.size()));
                examples.add(p.x()+":"+p.y()+":"+p.z());
            }
            LOG.info("[TC-DEV50E6M] FLEXIBLE_CANDIDATE village={} seamX={} coarseVertices={} fourBlockSteps={} bends={} cutSamples={} fillSamples={} leaves={} logs={} supports={} workScore={} examples={} planarSelfIntersection=false centerEnclosed=true fullScreenPass=true nativeTurnPorts=false nativeSlopePorts=false fiveCarSweep=false construction=DISABLED blocksChanged=0",
                    village,selected.seamX(),points.size(),a.fourBlockSteps(),a.bends(),
                    a.cutSamples(),a.fillSamples(),a.leafSamples(),a.logSamples(),
                    a.supportSamples(),a.workScore(),examples);
        }
        Map<String,Integer> rejections=new HashMap<>();int passableProfiles=0;
        for(E6iScreen s:screens.values())if(s.passable())passableProfiles++;
        else rejections.merge(s.rejected(),1,Integer::sum);
        String verdict=selected!=null?"ENCLOSING_4BLOCK_FLEXIBLE_SURVEY_NOT_NATIVE"
                :(seams.isEmpty()?"NO_EAST_RAY_CLOSURE_EDGES"
                :"NO_VALID_ENCLOSING_FLEXIBLE_SURVEY_CYCLE");
        int elapsed=(int)((System.nanoTime()-time)/1000000L);
        LOG.info("[TC-DEV50E6M] FLEXIBLE_SUMMARY village={} style={} coarseCenters={} unloadedCenters={} viableXY={} bedVariants={} allowedLinkVariants={} eastRayEdges={} seamCandidates={} seamsTried={} walkedCycles={} rejectedPlanarOrEnclosure={} rejectedFourBlockAudit={} auditedCyclesPassed={} searchExpanded={} capped={} nearestBlocks={} screenSamples={} passableScreenSamples={} topRejects={} elapsedMs={} verdict={} noBridgeApproval=true protectedVillageIntact=true native90Turns=false nativeSlopes=false fiveCarSweep=false globalNativeLoopCertified=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,centers,unloaded,byXZ.size(),nodes.size(),links,rayLinks,
                seams.size(),attempts,coarsePaths,rejectedProject,rejectedAudit,finePass,
                expanded,capped,nearest==Integer.MAX_VALUE?-1:nearest,screens.size(),
                passableProfiles,dev50e6hTop(rejections,7),elapsed,verdict);
        final String output=verdict;final int tried=attempts,found=finePass;
        src.sendSuccess(()->Component.literal("[E6m] Flexible enclosure: "+found
                +" screened survey cycles; "+tried+" closure candidates checked."),false);
        src.sendSuccess(()->Component.literal("[E6m] "+output+". NO native rail approval; blocks changed=0."),false);
        return 1;
    }

    // DEV50e6n: read-only diagnosis of why DEV50e6m's east-ray seam
    // cannot be closed. Never treats an alternative ray crossing, a short
    // elevation transition, or a hypothetical earthwork as build permission.
    private static final int E6N_MAX_SEAMS=16, E6N_MAX_DETAIL_LOG=8;
    private record E6nPartition(int[] group,int count,int largest) {}
    private record E6nAlternate(int otherRayCrossings,int steps,int firstRayX,int visited) {}
    private record E6nGap(E6mNode from,E6mNode to,int separation,String reason) {}
    private static E6nPartition dev50e6nPartition(List<E6mNode> nodes,
            List<List<Integer>> links,BlockPos village,boolean excludeRay) {
        int[] group=new int[nodes.size()];java.util.Arrays.fill(group,-1);
        int count=0,largest=0;
        java.util.ArrayDeque<Integer> queue=new java.util.ArrayDeque<>();
        for(int seed=0;seed<nodes.size();seed++){
            if(group[seed]!=-1)continue;
            group[seed]=count;queue.add(seed);int size=0;
            while(!queue.isEmpty()){
                int here=queue.removeFirst();size++;
                for(int next:links.get(here)){
                    if(group[next]!=-1)continue;
                    if(excludeRay && dev50e6mRayCross(nodes.get(here),nodes.get(next),village))continue;
                    group[next]=count;queue.addLast(next);
                }
            }
            largest=Math.max(largest,size);count++;
        }
        return new E6nPartition(group,count,largest);
    }
    private static E6nAlternate dev50e6nOtherRayPath(List<E6mNode> nodes,
            List<List<Integer>> links,BlockPos village,E6mSeam seam) {
        // 0/1 BFS: zero for ordinary edges, one for any OTHER east-ray edge.
        // The candidate seam itself is excluded. This graph test ignores
        // heading, full native track shapes and self intersections by design.
        int n=nodes.size(),start=seam.a(),target=seam.b();
        int[] dist=new int[n],prev=new int[n];
        java.util.Arrays.fill(dist,Integer.MAX_VALUE);
        java.util.Arrays.fill(prev,-1);
        java.util.ArrayDeque<Integer> deque=new java.util.ArrayDeque<>();
        dist[start]=0;deque.add(start);int visited=0;
        while(!deque.isEmpty()){
            int id=deque.removeFirst();visited++;
            for(int next:links.get(id)){
                if((id==start&&next==target)||(id==target&&next==start))continue;
                int w=dev50e6mRayCross(nodes.get(id),nodes.get(next),village)?1:0;
                if(dist[id]>=Integer.MAX_VALUE-2 || dist[id]+w>=dist[next])continue;
                dist[next]=dist[id]+w;prev[next]=id;
                if(w==0)deque.addFirst(next);else deque.addLast(next);
            }
        }
        if(dist[target]==Integer.MAX_VALUE)return new E6nAlternate(-1,-1,-1,visited);
        int firstRayX=-1,steps=0;
        for(int p=target;prev[p]!=-1;p=prev[p]){
            int parent=prev[p];steps++;
            if(dev50e6mRayCross(nodes.get(parent),nodes.get(p),village))
                firstRayX=nodes.get(p).x();
            if(steps>n+5)return new E6nAlternate(-2,-1,-1,visited);
        }
        return new E6nAlternate(dist[target],steps,firstRayX,visited);
    }
    private static boolean dev50e6nWouldCrossRay(E6mNode a,E6mNode b,BlockPos village){
        if(a.x()!=b.x() || a.x()<village.getX()+64)return false;
        int low=Math.min(a.z(),b.z()),high=Math.max(a.z(),b.z());
        return low<=village.getZ() && high>=village.getZ()+8;
    }
    private static E6nGap dev50e6nNearbyBarrier(ServerLevel level,BlockPos village,
            List<E6mNode> nodes,Map<Long,List<Integer>> byXZ,E6nPartition cut,
            int groupA,int groupB,Map<E6iKey,E6iScreen> screens){
        // Read-only bounded 8/16/24-block vicinity of TWO disconnected
        // components, not a new free-running A* or a proposed bridge.
        E6mNode aBest=null,bBest=null;int best=Integer.MAX_VALUE;
        for(int i=0;i<nodes.size();i++){
            if(cut.group()[i]!=groupA)continue;
            E6mNode a=nodes.get(i);
            for(int dx=-24;dx<=24;dx+=8)for(int dz=-24;dz<=24;dz+=8){
                int span=Math.abs(dx)+Math.abs(dz);
                if(span<8||span>24)continue;
                List<Integer> others=byXZ.get(dev50e6jKey(a.x()+dx,a.z()+dz));
                if(others==null)continue;
                for(int other:others){
                    if(cut.group()[other]!=groupB)continue;
                    E6mNode b=nodes.get(other);
                    if(dev50e6nWouldCrossRay(a,b,village))continue;
                    int score=span*10+Math.abs(a.y()-b.y())*3;
                    if(score<best){best=score;aBest=a;bBest=b;}
                }
            }
        }
        if(aBest==null)return null;
        int span=Math.abs(aBest.x()-bBest.x())+Math.abs(aBest.z()-bBest.z());
        String reason;
        if(Math.abs(aBest.y()-bBest.y())>span/4)reason="GRADE_TOO_STEEP";
        else if(aBest.x()!=bBest.x() && aBest.z()!=bBest.z())reason="BEND_OR_MULTI_SEGMENT_REQUIRES_ROUTE";
        else {
            int x=(aBest.x()+bBest.x())/2,z=(aBest.z()+bBest.z())/2;
            int y=Math.floorDiv(aBest.y()+bBest.y(),2);
            E6iScreen check=dev50e6iScreen(level,village,x,z,y,screens);
            reason=check.passable()?(span==8?"HEIGHT_OR_LINK_STATE_MISMATCH":"UNVALIDATED_MULTI_EDGE_CONNECTION")
                    :"MIDPOINT_"+check.rejected();
        }
        return new E6nGap(aBest,bBest,span,reason);
    }
    private static int dev50e6nClosureBarrierCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6n closurebarriercheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6n] No verified loaded village nearby. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> origin=dev50e6bVillageStartAnchor(level,village,128);
        if(origin.isEmpty()){
            src.sendFailure(Component.literal("[E6n] No verified village structure start. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,origin.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6N] CLOSURE_BARRIER_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6n] Natural vanilla village stays untouched."),false);return 1;
        }
        long started=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        List<E6mNode> nodes=new ArrayList<>();
        Map<Long,List<Integer>> byXZ=new HashMap<>();
        int surveyed=0,unloaded=0;
        // Keep DEV50e6m's exact five bed elevations and full 7-wide screen.
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=8)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=8){
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                int x=village.getX()+dx,z=village.getZ()+dz;surveyed++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){unloaded++;continue;}
                int terrain=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int adjust=-2;adjust<=2;adjust++){
                    int y=terrain+adjust;
                    E6iScreen screen=dev50e6iScreen(level,village,x,z,y,screens);
                    if(!screen.passable())continue;
                    int id=nodes.size();
                    nodes.add(new E6mNode(x,z,y,screen.workCost()));
                    byXZ.computeIfAbsent(dev50e6jKey(x,z),ignored->new ArrayList<>()).add(id);
                }
            }
        List<List<Integer>> links=new ArrayList<>();
        for(int i=0;i<nodes.size();i++)links.add(new ArrayList<>());
        int edges=0,rayEdges=0;
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            for(int axis=0;axis<2;axis++){
                List<Integer> candidates=byXZ.get(dev50e6jKey(a.x()+(axis==0?8:0),a.z()+(axis==1?8:0)));
                if(candidates==null)continue;
                for(int j:candidates){
                    E6mNode b=nodes.get(j);
                    if(!dev50e6mLink(level,village,a,b,screens))continue;
                    links.get(i).add(j);links.get(j).add(i);edges++;
                    if(dev50e6mRayCross(a,b,village))rayEdges++;
                }
            }
        }
        E6nPartition full=dev50e6nPartition(nodes,links,village,false);
        E6nPartition cut=dev50e6nPartition(nodes,links,village,true);
        List<E6mSeam> seams=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            if(a.z()!=village.getZ()||a.x()<village.getX()+64)continue;
            for(int j:links.get(i)){
                E6mNode b=nodes.get(j);
                if(dev50e6mRayCross(a,b,village)&&b.z()>a.z())
                    seams.add(new E6mSeam(i,j,a.x(),a.work()+b.work()+8*Math.abs(a.y()-b.y())));
            }
        }
        seams.sort(Comparator.comparingInt(E6mSeam::estimate)
                .thenComparingInt(s->Math.abs(s.x()-village.getX()-96))
                .thenComparingInt(E6mSeam::x));
        int tried=0,structuralBridge=0,needsOtherRay=0,cutConnected=0,headingFailed=0;
        int verifiedNoRayWalks=0,details=0,nearestGap=Integer.MAX_VALUE;
        Map<Integer,Integer> countAtX=new HashMap<>();
        for(E6mSeam seam:seams){
            if(tried>=E6N_MAX_SEAMS)break;
            if(countAtX.getOrDefault(seam.x(),0)>=2)continue;
            countAtX.merge(seam.x(),1,Integer::sum);tried++;
            int aGroup=cut.group()[seam.a()],bGroup=cut.group()[seam.b()];
            boolean connectedWithoutRay=aGroup==bGroup;
            if(connectedWithoutRay)cutConnected++;
            E6nAlternate alt=dev50e6nOtherRayPath(nodes,links,village,seam);
            if(alt.otherRayCrossings()<0)structuralBridge++;
            else if(alt.otherRayCrossings()>0)needsOtherRay++;
            E6mPath noRayWalk=connectedWithoutRay
                    ?dev50e6mWalk(nodes,links,village,seam.a(),seam.b())
                    :new E6mPath(List.of(),0,-1,false,0);
            if(connectedWithoutRay && noRayWalk.nodes().isEmpty())headingFailed++;
            if(!noRayWalk.nodes().isEmpty())verifiedNoRayWalks++;
            if(details<E6N_MAX_DETAIL_LOG){
                E6mNode a=nodes.get(seam.a()),b=nodes.get(seam.b());
                E6nGap gap=connectedWithoutRay?null:dev50e6nNearbyBarrier(
                        level,village,nodes,byXZ,cut,aGroup,bGroup,screens);
                if(gap!=null)nearestGap=Math.min(nearestGap,gap.separation());
                String diagnosis=connectedWithoutRay
                        ?(noRayWalk.nodes().isEmpty()?"NO_RAY_CONNECTED_BUT_HEADING_SEARCH_FAILED":"NO_RAY_WALK_EXISTS_NEEDS_CYCLE_AUDIT")
                        :(alt.otherRayCrossings()<0?"EDGE_IS_FULL_GRAPH_BRIDGE"
                        :"RAY_EXCLUSION_SEPARATES_ENDPOINTS");
                LOG.info("[TC-DEV50E6N] CLOSURE_SEAM village={} seamX={} seamStart={}:{}:{} seamEnd={}:{}:{} cutGroupA={} cutGroupB={} cutConnected={} minOtherRayCrossings={} alternateWalkEdges={} alternateRayX={} headingNoRayReached={} headingNoRayExpanded={} headingNoRayCapped={} nearestSafeGapBlocks={} gapFrom={} gapTo={} gapReason={} diagnosis={} noNativeApproval=true construction=DISABLED blocksChanged=0",
                        village,seam.x(),a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),
                        aGroup,bGroup,connectedWithoutRay,alt.otherRayCrossings(),alt.steps(),alt.firstRayX(),
                        !noRayWalk.nodes().isEmpty(),noRayWalk.explored(),noRayWalk.capped(),
                        gap==null?-1:gap.separation(),
                        gap==null?"NONE":gap.from().x()+":"+gap.from().y()+":"+gap.from().z(),
                        gap==null?"NONE":gap.to().x()+":"+gap.to().y()+":"+gap.to().z(),
                        gap==null?"NONE":gap.reason(),diagnosis);
                details++;
            }
        }
        String verdict=seams.isEmpty()?"NO_VALID_EAST_RAY_SEAMS"
                :(verifiedNoRayWalks>0?"RAY_FREE_ROUTE_EXISTS_REQUIRES_CYCLE_AUDIT"
                :(cutConnected>0?"HEADING_OR_PATH_RULE_ISOLATED"
                :(needsOtherRay>0?"RAY_FILTER_SPLITS_CLOSURE_ENDPOINTS"
                :"NO_ALTERNATE_GRAPH_RETURN_FOUND")));
        int elapsed=(int)((System.nanoTime()-started)/1000000L);
        LOG.info("[TC-DEV50E6N] CLOSURE_BARRIER_SUMMARY village={} style={} surveyedCenters={} unloadedCenters={} passableXY={} bedVariants={} graphLinks={} rayEdges={} fullComponents={} cutComponents={} largestFull={} largestCut={} candidateSeams={} seamsTried={} noRayConnectedSeams={} noRayHeadingWalks={} headingSearchFailed={} alternateOtherRayNeeded={} graphBridgeSeams={} nearestNonRayGapBlocks={} elapsedMs={} verdict={} noCandidateNativeCertificate=true noBridgeApproval=true native90Turns=false nativeSlopes=false fiveCarSweep=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,surveyed,unloaded,byXZ.size(),nodes.size(),edges,rayEdges,
                full.count(),cut.count(),full.largest(),cut.largest(),seams.size(),tried,
                cutConnected,verifiedNoRayWalks,headingFailed,needsOtherRay,structuralBridge,
                nearestGap==Integer.MAX_VALUE?-1:nearestGap,elapsed,verdict);
        final String chat="[E6n] Ray closure: "+cutConnected+"/"+tried
                +" graph-connected without east-ray; "+verifiedNoRayWalks+" heading walks.";
        final String result="[E6n] "+verdict+". Read-only; no native rail approval or block changes.";
        src.sendSuccess(()->Component.literal(chat),false);
        src.sendSuccess(()->Component.literal(result),false);
        return 1;
    }

    // DEV50e6o: Enclosing-bypass and far-side chokepoint DIAGNOSTIC ONLY.
    // Unlike E6n's minimum-crossing local bypass, a potential return must
    // reach the south, WEST and north gates before joining the east seam.
    // A closed, 7x5-screened ring is only a terrain SURVEY, not native rail.
    private static final int E6O_MAX_SEAMS=8, E6O_MAX_STATES_PER_SEAM=90000;
    private static final int E6O_SOUTH=1,E6O_WEST=2,E6O_NORTH=4;
    private record E6oCandidatePath(List<Integer> vertices,int expanded,boolean capped,
                                     int bestGateMask,int crossings,int repeatedXY,int closestXZ) {}
    private record E6oFarGap(E6mNode a,E6mNode b,int horizontal,String why) {}
    private static int dev50e6oGates(E6mNode node,BlockPos village){
        int mask=0;
        if(node.z()<=village.getZ()-40)mask|=E6O_SOUTH;
        if(node.x()<=village.getX()-48)mask|=E6O_WEST;
        if(node.z()>=village.getZ()+40)mask|=E6O_NORTH;
        return mask;
    }
    private static boolean dev50e6oTrueRayCross(E6mNode a,E6mNode b,BlockPos center){
        return a.x()==b.x() && a.x()>center.getX()
                && ((a.z()==center.getZ() && b.z()==center.getZ()+8)
                    ||(b.z()==center.getZ() && a.z()==center.getZ()+8));
    }
    private static int dev50e6oState(int id,int gates,int parity,int heading){
        return (((id*8+gates)*2+parity)*5+heading);
    }
    private static E6oCandidatePath dev50e6oEnclosingWalk(List<E6mNode> nodes,
            List<List<Integer>> links,BlockPos village,E6mSeam seam){
        // The endpoint seam itself is always prohibited in this return walk.
        // State records 3 far-side gates, ray parity and entry heading.
        // Even extra crossings + closing seam => ODD ray parity, which is
        // necessary but NOT sufficient to enclose the village. Audit follows.
        int states=nodes.size()*8*2*5;
        int[] dist=new int[states],prior=new int[states];
        java.util.Arrays.fill(dist,Integer.MAX_VALUE);
        java.util.Arrays.fill(prior,-1);
        java.util.PriorityQueue<E6mQueue> queue=new java.util.PriorityQueue<>(
                Comparator.comparingInt(E6mQueue::cost).thenComparingInt(E6mQueue::state));
        int firstGate=dev50e6oGates(nodes.get(seam.a()),village);
        int seed=dev50e6oState(seam.a(),firstGate,0,4);
        dist[seed]=0;queue.add(new E6mQueue(seed,0));
        int expanded=0,goal=-1,bestMask=firstGate,bestDistance=Integer.MAX_VALUE;
        E6mNode end=nodes.get(seam.b());
        while(!queue.isEmpty()&&expanded<E6O_MAX_STATES_PER_SEAM){
            E6mQueue entry=queue.poll();
            if(entry.cost()!=dist[entry.state()])continue;
            int sub=entry.state()/5;
            int heading=entry.state()%5;
            int parity=sub%2;
            int gates=(sub/2)%8;
            int id=(sub/16);
            E6mNode here=nodes.get(id);expanded++;
            if(Integer.bitCount(gates)>Integer.bitCount(bestMask))bestMask=gates;
            int remain=Math.abs(here.x()-end.x())+Math.abs(here.z()-end.z());
            if(remain<bestDistance)bestDistance=remain;
            if(id==seam.b()&&gates==7&&parity==0){goal=entry.state();break;}
            for(int next:links.get(id)){
                if((id==seam.a()&&next==seam.b())||(id==seam.b()&&next==seam.a()))continue;
                E6mNode other=nodes.get(next);
                int direction=dev50e6mDirection(here,other);
                if(heading!=4&&Math.abs(direction-heading)==2)continue;
                int ray=dev50e6oTrueRayCross(here,other,village)?1:0;
                int nm=gates|dev50e6oGates(other,village);
                int state=dev50e6oState(next,nm,parity^ray,direction);
                int weight=100+other.work()*2+Math.abs(here.y()-other.y())*14
                        +(heading==4||heading==direction?0:40)+ray*180;
                if(entry.cost()+weight>=dist[state])continue;
                dist[state]=entry.cost()+weight;prior[state]=entry.state();
                queue.add(new E6mQueue(state,dist[state]));
            }
        }
        if(goal<0)return new E6oCandidatePath(List.of(),expanded,
                expanded>=E6O_MAX_STATES_PER_SEAM,bestMask,-1,-1,
                bestDistance==Integer.MAX_VALUE?-1:bestDistance);
        List<Integer> walk=new ArrayList<>();
        for(int p=goal;p!=-1;p=prior[p]){
            walk.add(p/80);
            if(walk.size()>nodes.size()*8*2*5)return new E6oCandidatePath(
                    List.of(),expanded,false,bestMask,-1,-1,bestDistance);
        }
        java.util.Collections.reverse(walk);
        Set<Long> flat=new java.util.HashSet<>();
        int extraRay=0,repeat=0;
        for(int i=0;i<walk.size();i++){
            E6mNode p=nodes.get(walk.get(i));
            if(!flat.add(dev50e6jKey(p.x(),p.z())))repeat++;
            if(i>0&&dev50e6oTrueRayCross(nodes.get(walk.get(i-1)),p,village))extraRay++;
        }
        return new E6oCandidatePath(List.copyOf(walk),expanded,false,7,extraRay,repeat,0);
    }
    private static int[] dev50e6oReachGates(List<E6mNode> nodes,int[] labels,int group,
                                               BlockPos village){
        int[] counts=new int[3];
        for(int i=0;i<nodes.size();i++)if(labels[i]==group){
            int mask=dev50e6oGates(nodes.get(i),village);
            if((mask&E6O_SOUTH)!=0)counts[0]++;
            if((mask&E6O_WEST)!=0)counts[1]++;
            if((mask&E6O_NORTH)!=0)counts[2]++;
        }
        return counts;
    }
    private static E6oFarGap dev50e6oWestGap(ServerLevel level,BlockPos village,
            List<E6mNode> nodes,Map<Long,List<Integer>> byXZ,
            int[] groups,int first,int second,Map<E6iKey,E6iScreen> screens){
        // Look for a concrete short separation in the WEST half only.
        // Never pretend that a diagonal/long gap is an actual native link.
        if(first==second)return null;
        E6mNode left=null,right=null;int best=Integer.MAX_VALUE;
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            if(groups[i]!=first||a.x()>village.getX()-48)continue;
            for(int dx=-32;dx<=32;dx+=8)for(int dz=-32;dz<=32;dz+=8){
                int horizontal=Math.abs(dx)+Math.abs(dz);
                if(horizontal==0||horizontal>32||horizontal>best)continue;
                List<Integer> choices=byXZ.get(dev50e6jKey(a.x()+dx,a.z()+dz));
                if(choices==null)continue;
                for(int j:choices){
                    if(groups[j]!=second)continue;
                    E6mNode b=nodes.get(j);
                    if(b.x()>village.getX()-48)continue;
                    if(horizontal<best||horizontal==best&&
                            Math.abs(a.y()-b.y())<Math.abs(left.y()-right.y())){
                        left=a;right=b;best=horizontal;
                    }
                }
            }
        }
        if(left==null)return null;
        String why;
        if(best==8){
            why=dev50e6lEdgeFailure(level,village,left.surveyPoint(village),
                    right.surveyPoint(village),left.x(),left.z(),right.x(),right.z(),screens);
            if(why.equals("CLEAR"))why="LINK_NOT_ADMITTED_REQUIRES_EXACT_GRADE_REVIEW";
        }else if(left.x()!=right.x()&&left.z()!=right.z())
            why="MULTI_AXIS_GAP_REQUIRES_CARDINAL_ROUTE";
        else why="MULTI_SEGMENT_GAP_REQUIRES_INTERMEDIATE_BED_SURVEY";
        return new E6oFarGap(left,right,best,why);
    }
    private static int dev50e6oEnclosingBypassCheck(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("DEV50e6o enclosingbypasscheck ONLY in disposable DEV50E6 Terrain Profile Test. NO CHANGES."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,BlockPos.containing(src.getPosition()),128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[E6o1] No verified loaded village nearby. NO CHANGES."));return 0;
        }
        BlockPos village=probe.center().get();
        Optional<BlockPos> origin=dev50e6bVillageStartAnchor(level,village,128);
        if(origin.isEmpty()){
            src.sendFailure(Component.literal("[E6o1] Village structure start unverified. NO CHANGES."));return 0;
        }
        E6bStyle style=dev50e6bStyle(level,origin.get());
        if(style==E6bStyle.NATURAL_VANILLA){
            LOG.info("[TC-DEV50E6O1] ENCLOSING_BYPASS_SKIP village={} reason=NATURAL_VANILLA construction=DISABLED chunksForced=0 blocksChanged=0",village);
            src.sendSuccess(()->Component.literal("[E6o1] Natural vanilla village stays untouched."),false);return 1;
        }
        long begin=System.nanoTime();
        Map<E6iKey,E6iScreen> screens=new HashMap<>();
        List<E6mNode> nodes=new ArrayList<>();
        Map<Long,List<Integer>> byXZ=new HashMap<>();
        int sampled=0,unloaded=0;
        for(int dx=-E6J_RADIUS;dx<=E6J_RADIUS;dx+=8)
            for(int dz=-E6J_RADIUS;dz<=E6J_RADIUS;dz+=8){
                if((long)dx*dx+(long)dz*dz<56L*56L)continue;
                int x=village.getX()+dx,z=village.getZ()+dz;sampled++;
                if(!level.hasChunkAt(new BlockPos(x,0,z))){unloaded++;continue;}
                int terrain=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int adjust=-2;adjust<=2;adjust++){
                    int y=terrain+adjust;
                    E6iScreen screen=dev50e6iScreen(level,village,x,z,y,screens);
                    if(!screen.passable())continue;
                    int id=nodes.size();
                    nodes.add(new E6mNode(x,z,y,screen.workCost()));
                    byXZ.computeIfAbsent(dev50e6jKey(x,z),ignored->new ArrayList<>()).add(id);
                }
            }
        List<List<Integer>> links=new ArrayList<>();
        for(int i=0;i<nodes.size();i++)links.add(new ArrayList<>());
        int totalLinks=0,rayLinks=0;
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            for(int axis=0;axis<2;axis++){
                List<Integer> candidates=byXZ.get(dev50e6jKey(
                        a.x()+(axis==0?8:0),a.z()+(axis==1?8:0)));
                if(candidates==null)continue;
                for(int j:candidates){
                    E6mNode b=nodes.get(j);
                    if(!dev50e6mLink(level,village,a,b,screens))continue;
                    links.get(i).add(j);links.get(j).add(i);totalLinks++;
                    if(dev50e6mRayCross(a,b,village))rayLinks++;
                }
            }
        }
        E6nPartition full=dev50e6nPartition(nodes,links,village,false);
        E6nPartition cut=dev50e6nPartition(nodes,links,village,true);
        List<E6mSeam> seams=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){
            E6mNode a=nodes.get(i);
            if(a.z()!=village.getZ()||a.x()<village.getX()+64)continue;
            for(int j:links.get(i)){
                E6mNode b=nodes.get(j);
                if(dev50e6mRayCross(a,b,village)&&b.z()>a.z())
                    seams.add(new E6mSeam(i,j,a.x(),a.work()+b.work()+8*Math.abs(a.y()-b.y())));
            }
        }
        seams.sort(Comparator.comparingInt(E6mSeam::estimate)
                .thenComparingInt(s->Math.abs(s.x()-village.getX()-96))
                .thenComparingInt(E6mSeam::x));
        Set<Integer> usedX=new java.util.HashSet<>();
        int tried=0,farPaths=0,topologyRejected=0,fourBlockPassed=0;
        int sumExpanded=0,capped=0,nearWestGaps=0,closestWest=Integer.MAX_VALUE;
        E6mCandidate best=null;
        for(E6mSeam seam:seams){
            if(tried>=E6O_MAX_SEAMS)break;
            if(!usedX.add(seam.x()))continue;
            tried++;
            E6mNode a=nodes.get(seam.a()),b=nodes.get(seam.b());
            int groupA=cut.group()[seam.a()],groupB=cut.group()[seam.b()];
            int[] gatesA=dev50e6oReachGates(nodes,cut.group(),groupA,village);
            int[] gatesB=dev50e6oReachGates(nodes,cut.group(),groupB,village);
            E6oFarGap gap=dev50e6oWestGap(level,village,nodes,byXZ,cut.group(),
                    groupA,groupB,screens);
            if(gap!=null){nearWestGaps++;closestWest=Math.min(closestWest,gap.horizontal());}
            E6oCandidatePath path=dev50e6oEnclosingWalk(nodes,links,village,seam);
            sumExpanded+=path.expanded();if(path.capped())capped++;
            boolean routeExists=!path.vertices().isEmpty();
            E6mCandidate assessment=null;
            if(routeExists){
                farPaths++;
                assessment=dev50e6mAssess(level,village,nodes,seam,
                        new E6mPath(path.vertices(),path.expanded(),0,false,0),screens);
                if(assessment==null)topologyRejected++;
                else if(assessment.audit().passed()){
                    fourBlockPassed++;
                    if(best==null||assessment.audit().workScore()<best.audit().workScore())best=assessment;
                }
            }
            LOG.info("[TC-DEV50E6O1] FAR_SIDE_SEAM village={} seamX={} seamA={}:{}:{} seamB={}:{}:{} startCutGroup={} endCutGroup={} startGateCounts=South{}:West{}:North{} endGateCounts=South{}:West{}:North{} reachableWestGap={} gapFrom={} gapTo={} gapIssue={} farPathFound={} pathVertices={} crossedOtherRay={} repeatedXZ={} searchExpanded={} capped={} bestReachedGates={} topologyEnclosing={} fourBlockAudit={} reason={} noNativeApproval=true construction=DISABLED blocksChanged=0",
                    village,seam.x(),a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),
                    groupA,groupB,gatesA[0],gatesA[1],gatesA[2],
                    gatesB[0],gatesB[1],gatesB[2],gap==null?-1:gap.horizontal(),
                    gap==null?"NONE":gap.a().x()+":"+gap.a().y()+":"+gap.a().z(),
                    gap==null?"NONE":gap.b().x()+":"+gap.b().y()+":"+gap.b().z(),
                    gap==null?"NONE":gap.why(),routeExists,path.vertices().size(),
                    path.crossings(),path.repeatedXY(),path.expanded(),path.capped(),
                    path.bestGateMask(),assessment!=null,
                    assessment!=null&&assessment.audit().passed(),
                    !routeExists?"NO_PARITY_EVEN_NORTH_WEST_SOUTH_RETURN":
                    assessment==null?"NON_SIMPLE_OR_NOT_ENCLOSING":assessment.audit().reason());
        }
        String verdict=best!=null?"ENCLOSING_4BLOCK_BYPASS_SURVEY_NOT_NATIVE":
                farPaths>0?"FAR_SIDE_ROUTE_FOUND_BUT_ENCLOSURE_AUDIT_FAILED":
                capped>0?"FAR_SIDE_SEARCH_INCONCLUSIVE_NODE_CAP":
                "FAR_SIDE_CLOSURE_NOT_FOUND_WITHIN_SURVEY_GRAPH";
        int elapsed=(int)((System.nanoTime()-begin)/1000000L);
        LOG.info("[TC-DEV50E6O1] ENCLOSING_BYPASS_SUMMARY village={} style={} surveyedCenters={} unloadedCenters={} passableXY={} bedVariants={} admittedLinks={} rayEdges={} fullComponents={} cutComponents={} seamsAvailable={} seamsTested={} farSideEvenParityReturns={} topologyRejected={} audited4BlockPassed={} aggregateExpanded={} cappedSearches={} westGapCandidates={} nearestWestGapBlocks={} selectedSurveyVertices={} selectedFourBlockSteps={} selectedBends={} selectedWorkScore={} elapsedMs={} verdict={} native90Turns=false nativeSlopes=false fiveCarSweep=false nativeSimpleRailLoopCertified=false construction=DISABLED chunksForced=0 blocksChanged=0",
                village,style,sampled,unloaded,byXZ.size(),nodes.size(),totalLinks,rayLinks,
                full.count(),cut.count(),seams.size(),tried,farPaths,topologyRejected,
                fourBlockPassed,sumExpanded,capped,nearWestGaps,
                closestWest==Integer.MAX_VALUE?-1:closestWest,
                best==null?0:best.vertices().size(),
                best==null?0:best.audit().fourBlockSteps(),
                best==null?0:best.audit().bends(),
                best==null?0:best.audit().workScore(),elapsed,verdict);
        final String line="[E6o1] Enclosing-bypass: "+fourBlockPassed+" terrain-survey candidate(s) pass; "
                +farPaths+" far-side paths; "+tried+" seams.";
        final String last="[E6o1] "+verdict+". Construction disabled; native track not certified.";
        src.sendSuccess(()->Component.literal(line),false);
        src.sendSuccess(()->Component.literal(last),false);
        return 1;
    }

    private static int dev50e6Catalog(CommandSourceStack src) {
        if(!dev50e2WorldAllowed(src.getLevel())) {
            src.sendFailure(Component.literal("DEV50e6 catalog limited to the named disposable pilot world."));return 0;
        }
        E6TrackCatalog t=dev50e6CatalogBlocks();
        LOG.info("[TC-DEV50E6] TRACK_TYPE_CATALOG trackBlockIds={} curves={} slopes={} switches={} parallel={} station={} energyNamedBlocks={} energyNamedItems={} energyStateProperties={} trackItemHints={} compatibility=NOT_TESTED energyVariants=NOT_DETERMINED_BY_BLOCK_ID",
                t.blockIds(),t.curveIds(),t.slopeIds(),t.switchIds(),t.parallelIds(),
                t.stationIds(),t.energyNamedBlockIds(),t.energyNamedItemIds(),
                t.energyStatePropertyBlocks(),t.energyItemStateHints());
        src.sendSuccess(()->Component.literal("[E6] track block IDs="+t.blockIds()
                +" curves="+t.curveIds()+" slopes="+t.slopeIds()
                +" switches="+t.switchIds()+" parallel="+t.parallelIds()),false);
        src.sendSuccess(()->Component.literal("[E6] Energy separate named block IDs="+t.energyNamedBlockIds()
                +" energy-named items="+t.energyNamedItemIds()
                +" energy-property blocks="+t.energyStatePropertyBlocks()
                +". Shared-state/item-model Energy variants NOT verified by name scan."),false);
        return 1;
    }

    // Only the named disposable pilot world uses this site search. No blocks
    // are touched until the entire future track layout passes clearance.
    private static Dev50e3Choice dev50e3ChooseSite(ServerLevel level,BlockPos village) {
        Map<String,Integer> reasons=new LinkedHashMap<>();
        for(int[] offset:DEV50E3B_SITES) {
            int x=village.getX()+offset[0], z=village.getZ()+offset[1];
            BlockPos sample=new BlockPos(x,village.getY(),z);
            if(!level.hasChunkAt(sample)){reasons.merge("unloaded",1,Integer::sum);continue;}
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(y<=level.getMinBuildHeight()+2 || y>=level.getMaxBuildHeight()-8){
                reasons.merge("height",1,Integer::sum);continue;
            }
            BlockPos c=new BlockPos(x,y,z);
            String stationProblem=footprintProblem(level,c);
            if(stationProblem!=null){reasons.merge("station_"+stationProblem,1,Integer::sum);continue;}
            boolean outwardWest=c.getX()<village.getX();
            for(boolean west:new boolean[]{outwardWest,!outwardWest}) {
                NetworkPlan plan=dev50e3bOutboundNetwork(c,west);
                String problem=dev50e3bVillageBuffer(village,plan);
                if(problem==null) problem=dev50e3FutureTrackProblem(level,c,plan,west);
                if(problem==null){
                    LOG.info("[TC-DEV50E3B] SAFE_SITE station={} village={} axis={} rails={} outwardOnly=true villageBuffer={} clearance=STATIC_SCREEN_ONLY",
                            c,village,west?"WEST":"EAST",plan.rails().size(),(int)DEV50E3B_VILLAGE_BUFFER);
                    return new Dev50e3Choice(new Site(c,village),west);
                }
                reasons.merge(problem.split(":",2)[0],1,Integer::sum);
            }
        }
        if(logging()) LOG.info("[TC-DEV50E3B] NO_SAFE_SITE village={} reasons={}",village,reasons);
        return null;
    }

    // A pre-build version of networkProblem. The 17 reserved station rail
    // cells are expected to be AIR here and are constructed ONLY after this
    // read-only test. No chunk tickets, terrain clearing, or forced loads.
    private static String dev50e3FutureTrackProblem(ServerLevel level,BlockPos c,
                                                     NetworkPlan plan,boolean west) {
        for(BlockPos p:plan.protectedStationRails())
            if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir())
                return "reserved_station_track:"+p.toShortString();
        Set<BlockPos> targets=new LinkedHashSet<>(plan.supports().keySet());
        targets.addAll(plan.rails().keySet());
        targets.addAll(plan.decoration().keySet());
        for(BlockPos p:targets) {
            if(!level.hasChunkAt(p))return "unloaded:"+p.toShortString();
            if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())
                return "village_building:"+p.toShortString();
            int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            if(height!=c.getY())return "uneven:"+p.toShortString();
            BlockState state=level.getBlockState(p);
            if(p.getY()==c.getY()-1){
                if(!state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.DIRT))
                    return "protected_ground:"+p.toShortString();
            } else if(!state.isAir())return "obstructed:"+p.toShortString();
        }
        String clearance=dev50e3SweptClearance(level,c,plan,west);
        if(clearance!=null)return clearance;
        return dev50e3EntityIntersection(level,plan);
    }

    // Static PROVISIONAL envelope from the frozen DEV50b2d audit:
    // 1.50 half-width + 0.35 margin; +0.80 at turnouts, 4.25 high.
    // Not a fleet-wide locomotive, bogie, pantograph, or curve certification.
    // Screen BOTH unplaced and already-built layouts; reject unknown obstacles.
    private static String dev50e3SweptClearance(ServerLevel level,BlockPos c,
                                                 NetworkPlan plan,boolean west) {
        final double half=1.50D+0.35D,throatReserve=0.80D;
        final double minLaneCenters=2.0D*half;
        Set<BlockPos> allRails=new LinkedHashSet<>(plan.rails().keySet());
        allRails.addAll(plan.protectedStationRails());
        // Verify independent main/freight and main/construction lanes remain
        // five cell centers apart in either selected orientation.
        for(int x=DEV50E3B_OUTBOUND_SHIFT+2;x<=DEV50E3B_OUTBOUND_SHIFT+24;x++) {
            BlockPos main=dev50e3At(c,x,0,0,west),freight=dev50e3At(c,x,0,-5,west);
            if(!allRails.contains(main) || !allRails.contains(freight)
                    || Math.hypot(main.getX()-freight.getX(),main.getZ()-freight.getZ())<minLaneCenters)
                return "clearance_freight_spacing:"+main.toShortString();
        }
        for(int x=DEV50E3B_OUTBOUND_SHIFT+31;x<=DEV50E3B_OUTBOUND_SHIFT+36;x++) {
            BlockPos main=dev50e3At(c,x,0,0,west),spur=dev50e3At(c,x,0,5,west);
            if(!allRails.contains(main)||!allRails.contains(spur)
                    || Math.hypot(main.getX()-spur.getX(),main.getZ()-spur.getZ())<minLaneCenters)
                return "clearance_wip_spacing:"+main.toShortString();
        }
        Set<BlockPos> seen=new LinkedHashSet<>();
        BlockPos intentionalBuffer=dev50e3At(c,DEV50E3B_OUTBOUND_SHIFT+25,0,-5,west);
        for(BlockPos rail:allRails) {
            int along=west? c.getX()-rail.getX() : rail.getX()-c.getX();
            boolean throat=(along>=DEV50E3B_OUTBOUND_SHIFT-21 && along<=DEV50E3B_OUTBOUND_SHIFT-9)
                    ||(along>=DEV50E3B_OUTBOUND_SHIFT+8 && along<=DEV50E3B_OUTBOUND_SHIFT+32);
            double radius=half+(throat?throatReserve:0.0D);
            int scan=(int)Math.ceil(radius+1.0D);
            for(int dx=-scan;dx<=scan;dx++) for(int dz=-scan;dz<=scan;dz++) {
                double cx=rail.getX()+0.5D,cz=rail.getZ()+0.5D;
                int bx=rail.getX()+dx,bz=rail.getZ()+dz;
                double disX=Math.max(0.0D,Math.max(bx-cx,cx-(bx+1.0D)));
                double disZ=Math.max(0.0D,Math.max(bz-cz,cz-(bz+1.0D)));
                if(Math.hypot(disX,disZ)>radius)continue;
                for(int dy=0;dy<=4;dy++) {
                    BlockPos p=new BlockPos(bx,c.getY()+dy,bz);
                    if(!seen.add(p) || allRails.contains(p) || p.equals(intentionalBuffer))continue;
                    if(!level.hasChunkAt(p))return "clearance_unloaded:"+p.toShortString();
                    if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())
                        return "clearance_village:"+p.toShortString();
                    BlockState state=level.getBlockState(p);
                    if(!state.getCollisionShape(level,p).isEmpty() || !state.getFluidState().isEmpty())
                        return "clearance_obstacle:"+p.toShortString()+" block="+state.getBlock();
                }
            }
        }
        // Planned station-only features at y<=4: two original front fence
        // posts would intrude at z=2, so the pilot omits them (other worlds
        // remain untouched). All other new station walls/posts begin at z>=4.
        return null;
    }

    private static String dev50e3EntityIntersection(ServerLevel level,NetworkPlan plan) {
        Set<BlockPos> occupied=new LinkedHashSet<>(plan.rails().keySet());
        occupied.addAll(plan.decoration().keySet());
        for(BlockPos p:occupied) {
            AABB cell=new AABB(p);
            for(Entity entity:level.getEntitiesOfClass(Entity.class,cell)) {
                if(entity instanceof net.minecraft.world.entity.player.Player)continue;
                if(entity.getBoundingBox().intersects(cell))
                    return "entity:"+entity.getType()+" at="+entity.blockPosition().toShortString();
            }
        }
        return null;
    }

    /** A player is never an allowable overwrite target during automatic builds.
     * The older manual network command deliberately permits command operators. */
    private static String dev50e2PlayerIntersection(ServerLevel level,NetworkPlan plan) {
        for (ServerPlayer player:level.players()) {
            AABB body=player.getBoundingBox();
            for (BlockPos p:plan.rails().keySet())
                if (body.intersects(new AABB(p))) return "player_occupies:"+p.toShortString();
            for (BlockPos p:plan.decoration().keySet())
                if (body.intersects(new AABB(p))) return "player_occupies:"+p.toShortString();
        }
        return null;
    }

    /** Called only from the server's ordinary 40-tick worldgen cadence.
     * Does not issue chunk tickets, edit the station building or start trains. */
    private static void tickDev50e2Networks(ServerLevel level) {
        if (!dev50e2WorldAllowed(level)) {
            // Queue is session-local; never carry a test-world candidate to
            // another integrated server, even if server-stop was interrupted.
            DEV50E2_PENDING.clear();
            return;
        }
        // One candidate per 40-tick cadence to avoid worldgen spikes.
        for (var entry:new ArrayList<>(DEV50E2_PENDING.entrySet())) {
            PendingAutoNetwork pending=entry.getValue();
            if (ticks < pending.nextCheck) continue;
            BlockPos c=pending.station;
            if (ticks-pending.born > 20L*60L*8L || pending.checks>=DEV50E2_MAX_RETRIES) {
                DEV50E2_PENDING.remove(entry.getKey());
                DEV50E2_REJECTED.incrementAndGet();
                LOG.warn("[TC-DEV50E2] AUTO_NETWORK_EXPIRED station={} attempts={}",c,pending.checks);
                break;
            }
            if (level.players().stream().noneMatch(p -> {
                double dx=p.getX()-c.getX(),dz=p.getZ()-c.getZ();
                return dx*dx+dz*dz < 160.0D*160.0D;
            })) continue;
            pending.checks++;
            pending.nextCheck=ticks+200;
            StationData saved=stations(level);
            if (saved.hasNetwork(c)) {
                DEV50E2_PENDING.remove(entry.getKey());
                LOG.info("[TC-DEV50E2] AUTO_NETWORK_ALREADY_RECORDED station={}",c);
                break;
            }
            if (!level.hasChunkAt(c)) break;
            try {
                NetworkPlan plan=dev50e3bOutboundNetwork(c,pending.west);
                String reason=dev50e3bVillageBuffer(pending.village,plan);
                if(reason==null) reason=networkProblem(level,c,plan);
                if (reason==null) reason=dev50e3SweptClearance(level,c,plan,pending.west);
                if (reason==null) reason=dev50e3EntityIntersection(level,plan);
                if (reason==null) reason=dev50e2PlayerIntersection(level,plan);
                if (reason==null) {
                    dev50e2PlaceNetwork(level,c,plan,saved,pending.west);
                    DEV50E2_PENDING.remove(entry.getKey());
                    DEV50E2_BUILT.incrementAndGet();
                    LOG.info("[TC-DEV50E3B] AUTO_NETWORK_BUILT station={} axis={} rails={} supports={} decoration={} layout=WIDE_V2_OUTBOUND shift={} clearance=STATIC_SCREEN_ONLY",
                            c,pending.west ? "WEST":"EAST",plan.rails().size(),plan.supports().size(),plan.decoration().size(),DEV50E3B_OUTBOUND_SHIFT);
                } else if (reason.startsWith("unloaded:") || reason.startsWith("clearance_unloaded:")
                        || reason.startsWith("entity:") || reason.startsWith("player_occupies:")) {
                    LOG.info("[TC-DEV50E3] AUTO_NETWORK_WAIT station={} attempt={} reason={}",
                            c,pending.checks,reason);
                } else {
                    // Never flatten terrain, remove a house or modify existing rails.
                    DEV50E2_PENDING.remove(entry.getKey());
                    DEV50E2_REJECTED.incrementAndGet();
                    LOG.info("[TC-DEV50E3] AUTO_NETWORK_REJECT station={} axis={} reason={}",
                            c,pending.west ? "WEST":"EAST",reason);
                }
            } catch (RuntimeException error) {
                DEV50E2_PENDING.remove(entry.getKey());
                DEV50E2_REJECTED.incrementAndGet();
                LOG.error("[TC-DEV50E2] AUTO_NETWORK_ABORT station="+c,error);
            }
            break;
        }
    }

    /** Transactional build for the automatic pilot; the accepted manual
     * /tcrailway network place implementation below is deliberately untouched. */
    private static void dev50e2PlaceNetwork(ServerLevel level,BlockPos c,
                                             NetworkPlan plan,StationData saved,boolean west) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        for (BlockPos p:plan.supports().keySet()) before.put(p,level.getBlockState(p));
        for (BlockPos p:plan.rails().keySet()) before.putIfAbsent(p,level.getBlockState(p));
        for (BlockPos p:plan.decoration().keySet()) before.putIfAbsent(p,level.getBlockState(p));
        for (BlockPos p:plan.protectedStationRails()) before.putIfAbsent(p,level.getBlockState(p));
        try {
            plan.supports().forEach((p,s)->level.setBlock(p,s,3));
            plan.rails().forEach((p,s)->level.setBlock(p,s,3));
            plan.decoration().forEach((p,s)->level.setBlock(p,s,3));
            plan.rails().forEach((p,s)->level.setBlock(p,s,2));
            NetworkAudit audit=checkNetwork(level,plan);
            if (audit.wrong()!=0) throw new IllegalStateException("post-placement "+audit.firstMismatch());
            for (var entry:plan.supports().entrySet())
                if (!level.getBlockState(entry.getKey()).equals(entry.getValue()))
                    throw new IllegalStateException("support_mismatch "+entry.getKey());
            for (BlockPos p:plan.protectedStationRails())
                if (!level.getBlockState(p).equals(before.get(p)))
                    throw new IllegalStateException("station_rail_modified "+p);
            // Persist the accepted existing network record ONLY after all checks.
            saved.markWideNetwork(c);
            saved.markOutboundNetwork(c,west);
        } catch (RuntimeException error) {
            var reverse=new ArrayList<>(before.entrySet());
            java.util.Collections.reverse(reverse);
            for (var entry:reverse) level.setBlock(entry.getKey(),entry.getValue(),2);
            LOG.error("[TC-DEV50E2] AUTO_NETWORK_ROLLBACK station="+c,error);
            throw error;
        }
    }

    /** Require at least three village HOME POIs inside a real village structure. */
    private static VillageProbe inspectVillage(ServerLevel level, BlockPos near, int radius) {
        List<BlockPos> homes = level.getPoiManager()
                .getInRange(holder -> holder.is(PoiTypes.HOME), near, radius, PoiManager.Occupancy.ANY)
                .map(poi -> poi.getPos()).toList();
        if (homes.size() < 3) return new VillageProbe(Optional.empty(), homes.size(), homes.size(), false);
        BlockPos nearest = homes.stream().min(Comparator.comparingDouble(near::distSqr)).orElse(near);
        List<BlockPos> clustered = homes.stream().filter(p -> p.distSqr(nearest) < 64 * 64).toList();
        if (clustered.size() < 3) return new VillageProbe(Optional.empty(), homes.size(), clustered.size(), false);
        boolean naturalVillage = clustered.stream().limit(24).anyMatch(p ->
                level.structureManager().getStructureWithPieceAt(p, StructureTags.VILLAGE).isValid());
        if (!naturalVillage) return new VillageProbe(Optional.empty(), homes.size(), clustered.size(), false);
        int x = (int) Math.round(clustered.stream().mapToInt(BlockPos::getX).average().orElse(nearest.getX()));
        int z = (int) Math.round(clustered.stream().mapToInt(BlockPos::getZ).average().orElse(nearest.getZ()));
        return new VillageProbe(Optional.of(new BlockPos(x, nearest.getY(), z)), homes.size(), clustered.size(), true);
    }

    // DEV50e6a: test-save-only read-only village detector for /tcrailway loop
    // and /tcrailway diagnose. A fresh naturally generated village may have
    // fewer than three registered HOME POIs (the observed savanna village had
    // HOME_POIs=1), especially while villagers are still claiming beds.
    // Keep the DEV50e1 automatic worldgen detector and its eligibility EXACTLY
    // unchanged; never extend this fallback to automatic station placement.
    // A valid vanilla village STRUCTURE START is hard evidence of worldgen,
    // unlike beds, houses, villagers or grass paths placed by a player.
    private static VillageProbe inspectVillageForPilot(ServerLevel level, BlockPos near, int radius) {
        VillageProbe poi=inspectVillage(level,near,radius);
        if(poi.center().isPresent()) return poi;
        if(!dev50e2WorldAllowed(level)) return poi;
        if(!level.dimension().equals(Level.OVERWORLD)) return poi;
        // No force-loaded or generated chunks: only inspect starts in chunks
        // that already exist in memory. Search 2 chunks beyond the radius in
        // case the village start is just outside a nearby platform/house.
        int chunkRadius=(radius+47)/16;
        int cx=near.getX()>>4, cz=near.getZ()>>4;
        int loaded=0, starts=0, vanilla=0;
        BlockPos best=null;
        double score=Double.POSITIVE_INFINITY;
        ResourceLocation bestId=null;
        for(int x=cx-chunkRadius;x<=cx+chunkRadius;x++) {
            for(int z=cz-chunkRadius;z<=cz+chunkRadius;z++) {
                if(!level.hasChunk(x,z))continue;
                loaded++;
                for(StructureStart start:level.getChunk(x,z).getAllStarts().values()) {
                    if(start==null || !start.isValid())continue;
                    starts++;
                    ResourceLocation id=level.registryAccess()
                            .registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                    if(id==null || !"minecraft".equals(id.getNamespace()))continue;
                    String path=id.getPath();
                    if(!("village_plains".equals(path) || "village_desert".equals(path)
                            || "village_savanna".equals(path) || "village_taiga".equals(path)
                            || "village_snowy".equals(path)))continue;
                    vanilla++;
                    BoundingBox box=start.getBoundingBox();
                    // Distance to village BOUNDING BOX, not start-chunk center:
                    // large savanna villages may extend far from their start.
                    int dx=Math.max(0,Math.max(box.minX()-near.getX(),near.getX()-box.maxX()));
                    int dz=Math.max(0,Math.max(box.minZ()-near.getZ(),near.getZ()-box.maxZ()));
                    double edgeDistance=(double)dx*dx+(double)dz*dz;
                    if(edgeDistance>(double)radius*radius)continue;
                    BlockPos center=box.getCenter();
                    double centerDistance=(double)(near.getX()-center.getX())*(near.getX()-center.getX())
                            +(double)(near.getZ()-center.getZ())*(near.getZ()-center.getZ());
                    double candidateScore=edgeDistance*1000000.0D+centerDistance;
                    if(candidateScore>=score)continue;
                    // Reject a center in an unloaded chunk rather than
                    // forcing terrain generation during a diagnostic.
                    if(!level.hasChunk(center.getX()>>4,center.getZ()>>4))continue;
                    int yy=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            center.getX(),center.getZ());
                    best=new BlockPos(center.getX(),yy,center.getZ());
                    bestId=id;
                    score=candidateScore;
                }
            }
        }
        if(best!=null) {
            LOG.info("[TC-DEV50E6A] VILLAGE_DETECT source=LOADED_VANILLA_STRUCTURE_START "
                    +"id={} player={} center={} homes={} cluster={} chunks={} starts={} vanillaStarts={} "
                    +"actions=READ_ONLY worldgenPolicy=UNCHANGED",bestId,near,best,
                    poi.homes(),poi.clustered(),loaded,starts,vanilla);
            return new VillageProbe(Optional.of(best),poi.homes(),poi.clustered(),true);
        }
        LOG.info("[TC-DEV50E6A] VILLAGE_DETECT source=NONE player={} homes={} cluster={} "
                +"chunks={} starts={} vanillaStarts={} noForceLoad=true actions=READ_ONLY",
                near,poi.homes(),poi.clustered(),loaded,starts,vanilla);
        return poi;
    }

    private static Optional<BlockPos> villageCenter(ServerLevel level, BlockPos near, int radius) {
        return inspectVillage(level, near, radius).center();
    }
    private static final int[][] OFFSETS = {{36,0},{-36,0},{0,36},{0,-36},{48,0},{-48,0},{0,48},{0,-48},
                {40,40},{-40,40},{40,-40},{-40,-40},{56,0},{-56,0},{0,56},{0,-56}};
    // DEV51B R40J4: terrain-adaptive station foundations only in the exact
    // disposable R40 world. Never retrofits chunks, loads them, or controls trains.
    private static boolean r40j4TestWorld(ServerLevel level) {
        if (level == null || level.getServer() == null
                || level.getServer().isDedicatedServer()
                || !level.dimension().equals(Level.OVERWORLD)) return false;
        try {
            java.nio.file.Path save = level.getServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            java.nio.file.Path allowed = java.nio.file.Paths.get(
                    "D:/TafariCraft - Reborn Development/Traincraft", "run", "saves",
                    "DEV51B R40 Station Acceptance").toAbsolutePath().normalize();
            return save.equals(allowed)
                    && "DEV51B R40 Station Acceptance".equals(
                    level.getServer().getWorldData().getLevelName());
        } catch (RuntimeException badPath) { return false; }
    }
    private static final int[][] R40J4_EXTRA_OFFSETS = {
        {64,0},{-64,0},{0,64},{0,-64},
        {72,24},{-72,24},{72,-24},{-72,-24},
        {24,72},{-24,72},{24,-72},{-24,-72},
        {80,0},{-80,0},{0,80},{0,-80},
        {64,64},{-64,64},{64,-64},{-64,-64},
        {96,0},{-96,0},{0,96},{0,-96}
    };
    private static boolean r40j4NaturalFooting(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK);
    }
    private static int r40j4HighestLoadedY(ServerLevel level, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int dx=-10; dx<=10; dx++) for (int dz=-4; dz<=13; dz++) {
            int xx=x+dx,zz=z+dz;
            if (!level.hasChunk(xx>>4,zz>>4)) return Integer.MIN_VALUE;
            int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,xx,zz);
            if (h < level.getMinBuildHeight()+5 || h >= level.getMaxBuildHeight()-8)
                return Integer.MIN_VALUE;
            top=Math.max(top,h);
        }
        return top;
    }
    private static String r40j4FootprintProblem(ServerLevel level, BlockPos c) {
        final int y=c.getY();
        if (y < level.getMinBuildHeight()+5 || y >= level.getMaxBuildHeight()-8)
            return "height";
        for (int dx=-10; dx<=10; dx++) for (int dz=-4; dz<=13; dz++) {
            int x=c.getX()+dx,z=c.getZ()+dz;
            if (!level.hasChunk(x>>4,z>>4)) return "unloaded";
            int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if (h > y || h < y-4) return "slope_over_4";
            BlockPos footing=new BlockPos(x,h-1,z);
            BlockState top=level.getBlockState(footing);
            if (!r40j4NaturalFooting(top) || !top.getFluidState().isEmpty())
                return "unsafe_footing";
            // A pillar may not conceal a cave or be based on water/lava.
            for (int n=1; n<=3; n++) {
                BlockPos p=footing.below(n);
                if (p.getY()<level.getMinBuildHeight()) return "unsupported_cave";
                BlockState beneath=level.getBlockState(p);
                if (beneath.isAir() || !beneath.getFluidState().isEmpty()
                        || !beneath.isSolidRender(level,p)) return "unsupported_cave";
            }
            // Reject anything in the entire construction clearance volume,
            // including snow layers, foliage, builds, rails and village houses.
            for (int yy=h; yy<=y+6; yy++) {
                if (!level.getBlockState(new BlockPos(x,yy,z)).isAir())
                    return "obstructed";
            }
        }
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(c.offset(-10,0,-4),c.offset(11,7,14))).isEmpty())
            return "occupied_by_entity";
        return null;
    }
    private static SiteSearch r40j4InspectSites(ServerLevel level, BlockPos village) {
        Map<String,Integer> rejects=new HashMap<>();
        int checked=0;
        for (int pass=0;pass<2;pass++) {
            int[][] offsets=pass==0 ? OFFSETS : R40J4_EXTRA_OFFSETS;
            for (int[] offset: offsets) {
                int x=village.getX()+offset[0],z=village.getZ()+offset[1];
                int y=r40j4HighestLoadedY(level,x,z);
                if (y==Integer.MIN_VALUE) {
                    rejects.merge("missing_chunk_or_height",1,Integer::sum);
                    continue;
                }
                checked++;
                BlockPos candidate=new BlockPos(x,y,z);
                String hold=r40j4FootprintProblem(level,candidate);
                if (hold==null) {
                    LOG.info("[TC-DEV51B-R40J4] FOUNDATION_SITE village={} center={} checked={} maxRise=4 disposableOnly=true",village,candidate,checked);
                    return new SiteSearch(Optional.of(new Site(candidate,village)),rejects);
                }
                rejects.merge(hold,1,Integer::sum);
            }
        }
        LOG.info("[TC-DEV51B-R40J4] NO_SAFE_FOUNDATION village={} checked={} reasons={} writes=0",village,checked,rejects);
        return new SiteSearch(Optional.empty(),rejects);
    }
    private static boolean r40j4FloorCell(int dx,int dz) {
        return dz==0 && dx>=-8 && dx<=8
                || dz>=2 && dz<=4 && dx>=-8 && dx<=8
                || dz>=5 && dz<=11 && dx>=-6 && dx<=6;
    }
    // Only fills AIR underneath floors just built by the accepted surface builder.
    // Underground variants deliberately never enter this method.
    private static void r40j4AddSupports(ServerLevel level, BlockPos c) {
        if (!r40j4TestWorld(level) || !level.getBlockState(c).is(TCBlocks.TRACK_STATION.get()))
            return;
        int columns=0, blocks=0, held=0;
        for (int dx=-8;dx<=8;dx++) for (int dz=0;dz<=11;dz++) {
            if (!r40j4FloorCell(dx,dz)) continue;
            BlockPos floor=c.offset(dx,-1,dz);
            if (!level.hasChunkAt(floor)) { held++;continue; }
            BlockState floorState=level.getBlockState(floor);
            if (!floorState.is(Blocks.GRAVEL) && !floorState.is(Blocks.STONE_BRICKS)
                    && !floorState.is(Blocks.MOSS_BLOCK)) continue;
            int built=0;
            for (int depth=1;depth<=4;depth++) {
                BlockPos under=floor.below(depth);
                if (!level.hasChunkAt(under)) { held++;break; }
                BlockState existing=level.getBlockState(under);
                if (!existing.isAir()) break;
                if (!level.setBlock(under,Blocks.STONE_BRICKS.defaultBlockState(),3)) {
                    held++;break;
                }
                built++;blocks++;
            }
            if (built>0) columns++;
        }
        LOG.info("[TC-DEV51B-R40J4] FOUNDATION_BUILT center={} columns={} blocks={} writeHolds={} onlySurface=true",c,columns,blocks,held);
    }

    private static SiteSearch inspectSites(ServerLevel level, BlockPos village) {
        if (r40j4TestWorld(level)) return r40j4InspectSites(level,village);
        Map<String,Integer> rejections = new HashMap<>();
        for (int[] off : OFFSETS) {
            int x = village.getX() + off[0], z = village.getZ() + off[1];
            if (!level.hasChunkAt(new BlockPos(x, village.getY(), z))) {
                rejections.merge("unloaded",1,Integer::sum); continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight()+2 || y >= level.getMaxBuildHeight()-8) {
                rejections.merge("height",1,Integer::sum); continue;
            }
            BlockPos candidate = new BlockPos(x,y,z);
            String problem = footprintProblem(level,candidate);
            if (problem == null) return new SiteSearch(Optional.of(new Site(candidate,village)),rejections);
            rejections.merge(problem,1,Integer::sum);
        }
        return new SiteSearch(Optional.empty(), rejections);
    }
    private static Optional<Site> chooseSite(ServerLevel level, BlockPos village) {
        return inspectSites(level,village).site();
    }

    private static String footprintProblem(ServerLevel level, BlockPos c) {
        for (int dx = -10; dx <= 10; dx++) for (int dz = -4; dz <= 13; dz++) {
            BlockPos base = c.offset(dx, 0, dz);
            if (!level.hasChunkAt(base)) return "unloaded"; // Never load terrain during worldgen.
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) != c.getY()) return "uneven";
            BlockState ground = level.getBlockState(base.below());
            if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT)) return "occupied-ground";
            for (int dy = 0; dy <= 6; dy++) if (!level.getBlockState(base.above(dy)).isAir()) return "obstructed";
        }
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(c.offset(-10,0,-4), c.offset(11,7,14))).isEmpty()) return "occupied-by-entity";
        return null;
    }
    private static boolean safeFootprint(ServerLevel level, BlockPos c) {
        if (r40j4TestWorld(level)) return r40j4FootprintProblem(level,c)==null;
        return footprintProblem(level,c) == null;
    }


private static VillageStyle chooseVillageStyle(ServerLevel level, BlockPos c) {
    var biome = level.getBiome(c);
    if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) return VillageStyle.DESERT;
    if (biome.is(BiomeTags.IS_SAVANNA)) return VillageStyle.SAVANNA;
    if (biome.is(BiomeTags.IS_TAIGA)) return VillageStyle.TAIGA;
    if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) return VillageStyle.SNOWY;
    return VillageStyle.PLAINS;
}
private static SurfacePalette chooseSurfacePalette(VillageStyle style) {
    return switch (style) {
        case DESERT -> new SurfacePalette(
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.CUT_SANDSTONE_SLAB.defaultBlockState(),
                Blocks.SANDSTONE_WALL.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState(),
                Blocks.ACACIA_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
        );
        case SAVANNA -> new SurfacePalette(
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.ACACIA_SLAB.defaultBlockState(),
                Blocks.ACACIA_FENCE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.ACACIA_PLANKS.defaultBlockState(),
                Blocks.ACACIA_PLANKS.defaultBlockState(),
                Blocks.ACACIA_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
        );
        case TAIGA -> new SurfacePalette(
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.SPRUCE_SLAB.defaultBlockState(),
                Blocks.SPRUCE_FENCE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
        );
        case SNOWY -> new SurfacePalette(
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_SLAB.defaultBlockState(),
                Blocks.SPRUCE_FENCE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
        );
        case PLAINS -> new SurfacePalette(
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
        );
    };
}

private static void placeStation(ServerLevel level, Site site) {
    // Keep the exact existing manual build; only the disposable R40 world gets
    // the R40L visual layer for manual previews/placement. GameTests unchanged.
    VillageStyle style = chooseVillageStyle(level, site.center());
    placeStation(level, site, true, style);
    if (r40j4TestWorld(level)) {
        RailwayStationVillageArchitecture.decorate(level, site.center(), style.name(), "REGULAR");
    }
}
private static void placeStation(ServerLevel level, Site site, boolean spawnWorkers) {
    placeStation(level, site, spawnWorkers, chooseVillageStyle(level, site.center()));
}
private static void placeStation(ServerLevel level, Site site, boolean spawnWorkers, VillageStyle style) {
    BlockPos c = site.center();
    SurfacePalette palette = chooseSurfacePalette(style);
    BlockState rail = TCBlocks.TRACK_NORMAL.get().defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
    BlockState stationRail = TCBlocks.TRACK_STATION.get().defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
    for (int dx=-8; dx<=8; dx++) {
        level.setBlock(c.offset(dx,-1,0), Blocks.GRAVEL.defaultBlockState(), 3);
        level.setBlock(c.offset(dx,0,0), dx==0 ? stationRail : rail, 3);
    }
    buildEnclosedOffice(level,c,false,palette);
    if (dev50e2WorldAllowed(level)) {
        // DEV50e3 pilot-only: front posts can clip the historical test lead.
        for (int x:new int[]{-7,7}) for(int y=0;y<5;y++) {
            BlockPos post=c.offset(x,y,2);
            if (level.getBlockState(post).is(Blocks.OAK_FENCE)
                    || level.getBlockState(post).is(Blocks.SPRUCE_FENCE)
                    || level.getBlockState(post).is(Blocks.ACACIA_FENCE)
                    || level.getBlockState(post).is(Blocks.SANDSTONE_WALL))
                level.setBlock(post,Blocks.AIR.defaultBlockState(),3);
        }
    }
    if (WORKERS.get() && spawnWorkers) {
        makeWorker(level, c.offset(-2,0,8), "station_chief", c);
        makeWorker(level, c.offset(2,0,8), "train_conductor", c);
        makeWorker(level, c.offset(0,0,8), "railway_engineer", c);
    }
}

/** Ticket room with walls, windows, two workstations, a real door and lanterns. */
private static void buildEnclosedOffice(ServerLevel level, BlockPos c, boolean upgrade) {
    buildEnclosedOffice(level,c,upgrade,chooseSurfacePalette(chooseVillageStyle(level,c)));
}
private static void buildEnclosedOffice(ServerLevel level, BlockPos c, boolean upgrade, SurfacePalette palette) {
    if (upgrade) {
        // Remove ONLY original station blocks at known, exact locations.
        for (int x=-7;x<=7;x++) for(int z=3;z<=6;z++) {
            BlockPos roof = c.offset(x,5,z);
            if (level.getBlockState(roof).is(Blocks.SPRUCE_SLAB)) level.setBlock(roof,Blocks.AIR.defaultBlockState(),3);
        }
        for (int x : new int[]{-7,7}) for (int z : new int[]{3,6})
            for(int y=0;y<5;y++) {
                BlockPos post=c.offset(x,y,z);
                if (level.getBlockState(post).is(Blocks.OAK_FENCE)) level.setBlock(post,Blocks.AIR.defaultBlockState(),3);
            }
        for (int x : new int[]{-4,4}) {
            BlockPos bench=c.offset(x,0,4);
            if (level.getBlockState(bench).is(TCBlocks.STATION_OFFICE_DESK.get()) ||
                    level.getBlockState(bench).is(TCBlocks.TICKET_BOOTH.get()))
                level.setBlock(bench,Blocks.AIR.defaultBlockState(),3);
        }
    }
    // Covered platform between station room and railway.
    for(int x=-8;x<=8;x++) for(int z=2;z<=4;z++)
        level.setBlock(c.offset(x,-1,z),palette.platformBase(),3);
    for(int x=-7;x<=7;x++) for(int z=2;z<=4;z++)
        level.setBlock(c.offset(x,5,z),palette.canopyRoof(),3);
    for(int x : new int[]{-7,7}) for(int z : new int[]{2,4})
        for(int y=0;y<5;y++) level.setBlock(c.offset(x,y,z),palette.post(),3);
    // Fully enclosed 13 x 7 village-matched ticket office behind the platform.
    for (int x=-6;x<=6;x++) for(int z=5;z<=11;z++) {
        level.setBlock(c.offset(x,-1,z),palette.officeBase(),3);
        level.setBlock(c.offset(x,5,z),palette.officeRoof(),3);
        boolean wall = x==-6 || x==6 || z==5 || z==11;
        if (wall) for(int y=0;y<5;y++) {
            boolean window = y==2 && ((z==5 && (x==-3||x==-2||x==2||x==3))
                    || (z==11 && (x==-3||x==-2||x==2||x==3))
                    || ((x==-6||x==6) && (z==7||z==8||z==9)));
            level.setBlock(c.offset(x,y,z),window ? Blocks.GLASS.defaultBlockState() : palette.officeWall(),3);
        }
    }
    BlockState door = palette.door();
    level.setBlock(c.offset(0,0,5),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),3);
    level.setBlock(c.offset(0,1,5),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
    for(int x : new int[]{-4,4}) for(int z : new int[]{7,9}) {
        BlockPos lamp = c.offset(x,4,z);
        level.setBlock(lamp,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),3);
    }
    for(int x : new int[]{-5,5})
        level.setBlock(c.offset(x,4,3),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),3);
    level.setBlock(c.offset(-4,0,9),TCBlocks.STATION_OFFICE_DESK.get().defaultBlockState(),3);
    level.setBlock(c.offset(4,0,9),TCBlocks.TICKET_BOOTH.get().defaultBlockState(),3);
    // DEV50c1: one native job site for station engineer; leave all rails untouched.
    level.setBlock(c.offset(0,0,9),TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState(),3);
}

    private static boolean mayUpgrade(ServerLevel level, BlockPos c) {
        if (!level.getBlockState(c).is(TCBlocks.TRACK_STATION.get())) return false;
        if (!level.getBlockState(c.offset(-4,0,4)).is(TCBlocks.STATION_OFFICE_DESK.get())) return false;
        if (!level.getBlockState(c.offset(4,0,4)).is(TCBlocks.TICKET_BOOTH.get())) return false;
        // Check only the new extension; never replace an existing home or build.
        // Do not overwrite modifications to the old platform or shelter.
        for(int x=-8;x<=8;x++) for(int z=2;z<=6;z++) for(int y=0;y<=5;y++) {
            BlockPos p=c.offset(x,y,z);
            BlockState current=level.getBlockState(p);
            boolean originalPost=(x==-7||x==7) && (z==3||z==6) && y<=4;
            boolean originalRoof=x>=-7 && x<=7 && z>=3 && y==5;
            boolean originalDesk=y==0 && z==4 && x==-4;
            boolean originalTicket=y==0 && z==4 && x==4;
            if (current.isAir()) continue;
            if (originalPost && current.is(Blocks.OAK_FENCE)) continue;
            if (originalRoof && current.is(Blocks.SPRUCE_SLAB)) continue;
            if (originalDesk && current.is(TCBlocks.STATION_OFFICE_DESK.get())) continue;
            if (originalTicket && current.is(TCBlocks.TICKET_BOOTH.get())) continue;
            return false;
        }
        // The original station had no building here. Check the full room volume,
        // not just ground level, before placing walls and roof.
        for(int x=-6;x<=6;x++) for(int z=7;z<=11;z++) for(int y=0;y<=6;y++)
            if (!level.getBlockState(c.offset(x,y,z)).isAir()) return false;
        for(int x=-7;x<=7;x++) for(int z=7;z<=12;z++) {
            BlockPos p=c.offset(x,0,z);
            if (!level.hasChunkAt(p)) return false;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())!=c.getY()) return false;
            BlockState ground=level.getBlockState(p.below());
            if(!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT)) return false;
            for(int y=0;y<=6;y++) if(!level.getBlockState(p.above(y)).isAir()) return false;
        }
        return true;
    }
    private static void makeWorker(ServerLevel level, BlockPos pos, String role, BlockPos station) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;
        villager.moveTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 180.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        VillagerProfession profession = RailwayVillagers.forEgg(role).get();
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));
        villager.refreshBrain(level);
        villager.getPersistentData().putString(RailwayVillagerEgg.RAILWAY_ROLE_TAG, role);
        villager.getPersistentData().putLong(STAFF_STATION_TAG, station.asLong());
        villager.getPersistentData().putString(STAFF_SLOT_ROLE_TAG, role);
        villager.setPersistenceRequired();
        if (level.addFreshEntity(villager)) {
            stations(level).registerStaff(station, role, villager.getUUID());
            RailwayVillagerCare.bindWorkstation(level, villager, role);
            RailwayTrades.ensureNoviceOffers(villager);
        }
    }

    private static BlockPos staffSpawn(BlockPos station, String role) {
        return station.offset("station_chief".equals(role) ? -2 : "railway_engineer".equals(role) ? 0 : 2, 0, 8);
    }

    /** Only a death EVENT can arm the timer. Missing/unloaded villagers never do. */
    @SubscribeEvent
    public static void onGeneratedStaffDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) return;
        CompoundTag tag = villager.getPersistentData();
        if (!tag.contains(STAFF_STATION_TAG, Tag.TAG_LONG)) return;
        String role = tag.getString(STAFF_SLOT_ROLE_TAG);
        if (!"station_chief".equals(role) && !"train_conductor".equals(role) && !"railway_engineer".equals(role)) return;
        BlockPos station = BlockPos.of(tag.getLong(STAFF_STATION_TAG));
        StationData data = stations(level);
        if (data.recordStaffDeath(station, role, villager.getUUID(),
                level.getGameTime() + (long)STAFF_RESPAWN_SECONDS.get() * 20L)) {
            LOG.info(LOG_PREFIX + "STAFF_DEATH station={} role={} cause={} respawnDelay={}s",
                    station,role,event.getSource().getMsgId(),STAFF_RESPAWN_SECONDS.get());
        }
    }

    private static boolean staffSpawnSafe(ServerLevel level, BlockPos station, String role) {
        if (!level.hasChunkAt(station) || !level.getBlockState(station).is(TCBlocks.TRACK_STATION.get()))
            return false;
        BlockPos pos = staffSpawn(station,role);
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.offset(-2,0,0)) || !level.hasChunkAt(pos.offset(2,0,0)))
            return false;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir())
            return false;
        if (!level.getBlockState(pos.below()).isSolidRender(level,pos.below())) return false;
        BlockPos desk = station.offset("station_chief".equals(role) ? -4 : "railway_engineer".equals(role) ? 0 : 4, 0, 9);
        if (!level.getBlockState(desk).is("station_chief".equals(role)
                ? TCBlocks.STATION_OFFICE_DESK.get() : "railway_engineer".equals(role)
                ? TCBlocks.RAILWAY_TOOLBENCH.get() : TCBlocks.TICKET_BOOTH.get())) return false;
        AABB area = new AABB(pos).inflate(0.35, 0.5, 0.35);
        return level.getEntitiesOfClass(LivingEntity.class, area).isEmpty();
    }

    private static void checkRecordedStaffRespawns(ServerLevel level) {
        StationData data = stations(level);
        long now = level.getGameTime();
        int budget = 2;
        for (StaffSlot slot : new ArrayList<>(data.staff.values())) {
            if (budget <= 0) break;
            if (slot.respawnAt <= 0 || now < slot.respawnAt) continue;
            BlockPos center = slot.station;
            // Do not force-load distant chunks or generate staff at abandoned sites.
            if (level.players().stream().noneMatch(p -> p.blockPosition().distSqr(center) <= 96.0*96.0)) continue;
            if (!staffSpawnSafe(level,center,slot.role)) continue;
            // A moved/duplicate original still in this loaded station area wins.
            AABB scan = new AABB(center.offset(-16,-3,-16),center.offset(17,8,20));
            Optional<Villager> existing = level.getEntitiesOfClass(Villager.class,scan).stream()
                    .filter(v -> v.getPersistentData().contains(STAFF_STATION_TAG,Tag.TAG_LONG)
                            && v.getPersistentData().getLong(STAFF_STATION_TAG)==center.asLong()
                            && slot.role.equals(v.getPersistentData().getString(STAFF_SLOT_ROLE_TAG)))
                    .findFirst();
            if (existing.isPresent()) {
                data.registerStaff(center,slot.role,existing.get().getUUID());
                continue;
            }
            // A failed spawn retains the original due timer and can be retried.
            makeWorker(level,staffSpawn(center,slot.role),slot.role,center);
            StaffSlot updated=data.getStaff(center,slot.role);
            if (updated != null && updated.respawnAt == 0) {
                --budget;
                LOG.info(LOG_PREFIX + "STAFF_RESPAWN station={} role={} uuid={}",center,slot.role,updated.uuid);
            }
        }
    }

    private static StationData stations(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StationData::load, StationData::new, SAVE_NAME);
    }

    private static final class StaffSlot {
        final BlockPos station;
        final String role;
        final UUID uuid; // Never infer a death from an unloaded UUID.
        final long respawnAt; // Nonzero ONLY after a confirmed LivingDeathEvent.
        StaffSlot(BlockPos station,String role,UUID uuid,long respawnAt) {
            this.station=station;this.role=role;this.uuid=uuid;this.respawnAt=respawnAt;
        }
    }

    private static final class StationData extends SavedData {
        private final List<BlockPos> placed = new ArrayList<>();
        // R30 provenance for a SINGLE-Rail disposable test stop, never an operational station.
        private final List<BlockPos> r30TestStops = new ArrayList<>();
        private final List<BlockPos> enclosed = new ArrayList<>();
        private final List<BlockPos> networks = new ArrayList<>();
        // DEV50b2e: a second set, not a rewrite of legacy network records.
        private final List<BlockPos> wideNetworks = new ArrayList<>();
        // DEV50e3b only; absent in all accepted earlier saves. True = WEST.
        private final Map<Long,Boolean> outboundNetworks = new HashMap<>();
        private final Map<String, StaffSlot> staff = new HashMap<>();
        private final Map<Long, ServicePlan> services = new HashMap<>();
        // DEV50c4: Persist UUIDs. Never silently respawn after reload/death/unload.
        private final Map<Long, StagedConsist> staged = new HashMap<>();
        StagedConsist staged(BlockPos c) { return staged.get(c.asLong()); }
        void markStaged(BlockPos c, StagedConsist consist) { staged.put(c.asLong(),consist);setDirty(); }
        ServicePlan service(BlockPos c) { return services.get(c.asLong()); }
        void setService(BlockPos c, ServicePlan plan) { services.put(c.asLong(), plan); setDirty(); }
        private static String key(BlockPos station,String role) {return station.asLong()+":"+role;}
        StaffSlot getStaff(BlockPos station,String role){return staff.get(key(station,role));}
        void registerStaff(BlockPos station,String role,UUID uuid){
            staff.put(key(station,role),new StaffSlot(station,role,uuid,0));setDirty();
        }
        boolean recordStaffDeath(BlockPos station,String role,UUID uuid,long due){
            StaffSlot current=getStaff(station,role);
            if (current == null || current.uuid==null || !current.uuid.equals(uuid)) return false;
            staff.put(key(station,role),new StaffSlot(station,role,null,due));setDirty();return true;
        }
        static StationData load(CompoundTag tag) {
            StationData data = new StationData();
            for (long encoded : tag.getLongArray("Stations")) data.placed.add(BlockPos.of(encoded));
            for (long encoded : tag.getLongArray("R30TestStops")) {
                BlockPos p=BlockPos.of(encoded);
                if (data.placed.contains(p) && !data.r30TestStops.contains(p)) data.r30TestStops.add(p);
            }
            for (long encoded : tag.getLongArray("Enclosed")) data.enclosed.add(BlockPos.of(encoded));
            for (long encoded : tag.getLongArray("Networks")) data.networks.add(BlockPos.of(encoded));
            for (long encoded : tag.getLongArray("WideNetworks")) data.wideNetworks.add(BlockPos.of(encoded));
            for (Tag element : tag.getList("OutboundNetworks",Tag.TAG_COMPOUND)) {
                CompoundTag record=(CompoundTag)element;
                long at=record.getLong("Station");
                if(data.wideNetworks.contains(BlockPos.of(at)))
                    data.outboundNetworks.put(at,record.getBoolean("West"));
            }
            for (Tag element : tag.getList("RailwayStaff",Tag.TAG_COMPOUND)) {
                CompoundTag t=(CompoundTag)element;
                String role=t.getString("Role");
                if (!"station_chief".equals(role) && !"train_conductor".equals(role) && !"railway_engineer".equals(role)) continue;
                BlockPos station=BlockPos.of(t.getLong("Station"));
                UUID id=t.hasUUID("UUID")?t.getUUID("UUID"):null;
                long due=t.getLong("RespawnAt");
                if ((id!=null) != (due==0)) continue;
                data.staff.put(key(station,role),new StaffSlot(station,role,id,due));
            }
            for (Tag element : tag.getList("StationServices", Tag.TAG_COMPOUND)) {
                CompoundTag t=(CompoundTag)element;
                int index=t.getInt("Profile");
                int round=t.getInt("Rerolls");
                int coaches=t.getInt("Coaches");
                long at=t.getLong("Station");
                if (index < 0 || index >= SERVICE_PROFILES.length || round < 0 || coaches < 1 || coaches > 3) continue;
                if (data.placed.contains(BlockPos.of(at))) data.services.put(at,new ServicePlan(index,coaches,round));
            }
            for (Tag element : tag.getList("StationStagedConsists",Tag.TAG_COMPOUND)) {
                CompoundTag t=(CompoundTag)element;
                long at=t.getLong("Station");
                if (t.hasUUID("Locomotive") && t.hasUUID("Coach")
                        && data.placed.contains(BlockPos.of(at))) {
                    data.staged.put(at,new StagedConsist(t.getUUID("Locomotive"),t.getUUID("Coach")));
                }
            }
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("Stations", placed.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("R30TestStops", r30TestStops.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("Enclosed", enclosed.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("Networks", networks.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("WideNetworks", wideNetworks.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag outboundList=new ListTag();
            for(var entry:outboundNetworks.entrySet()) {
                CompoundTag t=new CompoundTag();
                t.putLong("Station",entry.getKey());
                t.putBoolean("West",entry.getValue());
                outboundList.add(t);
            }
            tag.put("OutboundNetworks",outboundList);
            ListTag staffList=new ListTag();
            for (StaffSlot slot : staff.values()) {
                CompoundTag item=new CompoundTag();
                item.putLong("Station",slot.station.asLong());
                item.putString("Role",slot.role);
                if (slot.uuid!=null) item.putUUID("UUID",slot.uuid);
                item.putLong("RespawnAt",slot.respawnAt);
                staffList.add(item);
            }
            tag.put("RailwayStaff",staffList);
            ListTag manifest = new ListTag();
            for (var entry:services.entrySet()) {
                CompoundTag t=new CompoundTag();
                t.putLong("Station",entry.getKey());
                t.putInt("Profile",entry.getValue().index());
                t.putInt("Coaches",entry.getValue().coaches());
                t.putInt("Rerolls",entry.getValue().rerolls());
                manifest.add(t);
            }
            tag.put("StationServices",manifest);
            ListTag stagedList=new ListTag();
            for (var entry:staged.entrySet()) {
                CompoundTag t=new CompoundTag();
                t.putLong("Station",entry.getKey());
                t.putUUID("Locomotive",entry.getValue().locomotive());
                t.putUUID("Coach",entry.getValue().coach());
                stagedList.add(t);
            }
            tag.put("StationStagedConsists",stagedList);
            return tag;
        }
        boolean near(BlockPos pos, int radius) {
            return placed.stream().anyMatch(p -> p.distSqr(pos) < (double) radius * radius);
        }
        boolean isR30TestStop(BlockPos p) {return r30TestStops.contains(p);}
        void recordR30TestStop(BlockPos p) {
            if(placed.contains(p) || !r30TestStops.isEmpty())
                throw new IllegalStateException("R30 stop record already present");
            placed.add(p);r30TestStops.add(p);setDirty();
        }
        void removeR30TestStop(BlockPos p) {
            if(!r30TestStops.contains(p) || !placed.contains(p) || services.containsKey(p.asLong())
                    || staged.containsKey(p.asLong()) || enclosed.contains(p) || networks.contains(p)
                    || wideNetworks.contains(p))throw new IllegalStateException("R30 stop provenance changed");
            for(StaffSlot s:staff.values()) if(s.station.equals(p))
                throw new IllegalStateException("R30 test stop contains staff");
            placed.remove(p);r30TestStops.remove(p);setDirty();
        }
        void add(BlockPos pos, boolean isEnclosed) {
            placed.add(pos);
            if (isEnclosed) enclosed.add(pos);
            setDirty();
        }
        boolean enclosed(BlockPos pos) { return enclosed.contains(pos); }
        void markEnclosed(BlockPos pos) { if (!enclosed.contains(pos)) { enclosed.add(pos); setDirty(); } }
        Optional<BlockPos> closest(BlockPos pos, int radius) {
            return placed.stream().filter(p->p.distSqr(pos)<(double)radius*radius)
                    .min(Comparator.comparingDouble(pos::distSqr));
        }
        int count() { return placed.size(); }
        boolean hasNetwork(BlockPos at) { return networks.contains(at); }
        void markNetwork(BlockPos at) { if (!networks.contains(at)) { networks.add(at); setDirty(); } }
        boolean hasWideNetwork(BlockPos at) { return wideNetworks.contains(at); }
        Boolean outboundWest(BlockPos at){ return outboundNetworks.get(at.asLong()); }
        void markOutboundNetwork(BlockPos at,boolean west) {
            if(!hasWideNetwork(at)) throw new IllegalStateException("unrecorded_outbound_network");
            outboundNetworks.put(at.asLong(),west);
            setDirty();
        }
        void markWideNetwork(BlockPos at) {
            if (!hasNetwork(at)) markNetwork(at);
            if (!wideNetworks.contains(at)) { wideNetworks.add(at); setDirty(); }
        }
        int networkCount() { return networks.size(); }
    }

    // DEV50c1 — Safe station passenger-service ROSTER, not an AI/autospawn controller.
    // IDs are the existing registered entities; coaches are deliberately not
    // created until validated coupler compatibility and complete rail geometry
    // are available in the subsequent stage.
    private record ServiceProfile(String name, String locomotive, String coach) {}
    private record ServicePlan(int index,int coaches,int rerolls) {}
    /** DEV50c4: this is an intentionally parked, user-commanded staging test. */
    private record StagedConsist(UUID locomotive,UUID coach) {}
    private static final ServiceProfile[] SERVICE_PROFILES = {
            new ServiceProfile("Local Diesel", "traincraft:locomotive_diesel_gp7", "traincraft:passenger_coach_blue"),
            new ServiceProfile("Regional Diesel", "traincraft:locomotive_diesel_cd742", "traincraft:passenger_coach_blue"),
            new ServiceProfile("Heritage Local", "traincraft:locomotive_steam_small", "traincraft:passenger_coach_blue"),
            new ServiceProfile("Electric Local", "traincraft:locomotive_electric_br_e69", "traincraft:passenger_coach_blue"),
            new ServiceProfile("Vintage Passenger", "traincraft:locomotive_steam_gs4", "traincraft:gs4_passenger_car")
    };
    private static ServicePlan chooseService(ServerLevel level, BlockPos c, int rerolls) {
        // Stable per station and world seed; reroll is explicit and persisted.
        java.util.Random random=new java.util.Random(level.getSeed() ^
                Long.rotateLeft(c.asLong(),19) ^ 0x6A09E667F3BCC909L);
        return new ServicePlan((random.nextInt(SERVICE_PROFILES.length)+rerolls)%SERVICE_PROFILES.length,
                1+(random.nextInt(3)+rerolls)%3,rerolls);
    }
    private static boolean serviceRegistryReady(ServicePlan plan) {
        ServiceProfile p=SERVICE_PROFILES[plan.index()];
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(new net.minecraft.resources.ResourceLocation(p.locomotive()))
                && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(new net.minecraft.resources.ResourceLocation(p.coach()));
    }
    private static String serviceSummary(BlockPos c, ServicePlan plan) {
        ServiceProfile p=SERVICE_PROFILES[plan.index()];
        return "station="+c.toShortString()+" service="+p.name()+" locomotive="+p.locomotive()
                +" coaches="+plan.coaches()+"x"+p.coach()+" rerolls="+plan.rerolls()
                +" [ROSTER PLAN; use /tcrailway service status to inspect staged vehicles; no AI driving]";
    }
    private static int serviceCommand(CommandSourceStack src,String action) {
        if (PLAYER_OPERATED_STATIONS_ONLY && !"preview".equals(action) && !"status".equals(action)) {
            src.sendFailure(Component.literal("Player-operated stations only: automatic service activation/reroll retired."));return 0;
        }
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) { src.sendFailure(Component.literal("Railway services are Overworld-only"));return 0; }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if (found.isEmpty()) { explainMissingNetworkStation(src,level); return 0; }
        BlockPos c=found.get();
        if (!level.hasChunkAt(c) || !level.getBlockState(c).is(TCBlocks.TRACK_STATION.get())) {
            src.sendFailure(Component.literal("Recorded station track missing or unloaded; NO CHANGES"));return 0;
        }
        ServicePlan existing=data.service(c);
        if ("preview".equals(action) || "status".equals(action)) {
            ServicePlan plan=existing==null?chooseService(level,c,0):existing;
            String state=existing==null?"PREVIEW (not active)":"SAVED";
            String info="[TC service "+state+"] "+serviceSummary(c,plan)+" registry="+(serviceRegistryReady(plan)?"OK":"MISSING");
            src.sendSuccess(()->Component.literal(info),false);
            if ("status".equals(action)) {
                StaffSlot slot=data.getStaff(c,"railway_engineer");
                String staff=slot==null?"NO ENGINEER ASSIGNED":slot.respawnAt>0?"engineer death logged; respawn pending":"engineer managed UUID="+slot.uuid;
                src.sendSuccess(()->Component.literal("[TC service staff] "+staff),false);
                StagedConsist staged=data.staged(c);
                String stagedInfo=staged==null?"NONE (no train deployed)":
                        "RECORDED loco="+staged.locomotive()+" loaded="+(level.getEntity(staged.locomotive())!=null)
                        +" coach="+staged.coach()+" loaded="+(level.getEntity(staged.coach())!=null);
                src.sendSuccess(()->Component.literal("[TC-DEV50C4 staged consist] "+stagedInfo),false);
            }
            LOG.info("[TC-DEV50C1] service={} {}",action,info);return 1;
        }
        if ("reroll".equals(action)) {
            if (data.staged(c)!=null) {src.sendFailure(Component.literal("Service already has a staged consist; roster is locked. NO CHANGES"));return 0;}
            if (existing==null) {src.sendFailure(Component.literal("Activate this station first: /tcrailway service activate"));return 0;}
            ServicePlan next=chooseService(level,c,existing.rerolls()+1);
            if (!serviceRegistryReady(next)) {src.sendFailure(Component.literal("Selected train IDs unavailable. NO CHANGES"));return 0;}
            data.setService(c,next);
            String info="[TC service reroll] "+serviceSummary(c,next);
            src.sendSuccess(()->Component.literal(info),true);
            LOG.info("[TC-DEV50C1] {}",info);return 1;
        }
        if (!"activate".equals(action)) return 0;
        if (!data.enclosed(c)) {src.sendFailure(Component.literal("Enclosed railway office required; use /tcrailway upgrade first"));return 0;}
        ServicePlan plan=existing==null?chooseService(level,c,0):existing;
        if (!serviceRegistryReady(plan)) {src.sendFailure(Component.literal("Train roster entity IDs not registered; no changes"));return 0;}
        BlockPos desk=c.offset(0,0,9), worker=staffSpawn(c,"railway_engineer");
        BlockState current=level.getBlockState(desk);
        boolean needsDesk=current.isAir();
        if (!needsDesk && !current.is(TCBlocks.RAILWAY_TOOLBENCH.get())) {
            src.sendFailure(Component.literal("Engineer workstation site modified; NO CHANGES"));return 0;
        }
        if (!level.hasChunkAt(desk) || !level.hasChunkAt(worker) || !level.getBlockState(desk.above()).isAir()
                || !level.getBlockState(worker).isAir() || !level.getBlockState(worker.above()).isAir()
                || !level.getBlockState(desk.below()).isSolidRender(level,desk.below())
                || !level.getBlockState(worker.below()).isSolidRender(level,worker.below())) {
            src.sendFailure(Component.literal("Engineer workstation/standing area obstructed or not loaded. NO CHANGES"));return 0;
        }
        if (needsDesk && !level.getEntitiesOfClass(LivingEntity.class,new AABB(desk).inflate(0.2,0.5,0.2)).isEmpty()) {
            src.sendFailure(Component.literal("Entity occupies workstation site. NO CHANGES"));return 0;
        }
        StaffSlot slot=data.getStaff(c,"railway_engineer");
        if (slot == null) {
            if (!level.getEntitiesOfClass(LivingEntity.class,new AABB(worker).inflate(0.3,0.5,0.3)).isEmpty()) {
                src.sendFailure(Component.literal("Engineer spawn space occupied. NO CHANGES"));return 0;
            }
            if (needsDesk) level.setBlock(desk,TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState(),3);
            if (!staffSpawnSafe(level,c,"railway_engineer")) {
                if (needsDesk) level.setBlock(desk,current,3);
                src.sendFailure(Component.literal("Engineer spawn check failed. Workbench rolled back"));return 0;
            }
            makeWorker(level,worker,"railway_engineer",c);
            if (data.getStaff(c,"railway_engineer")==null) {
                if (needsDesk) level.setBlock(desk,current,3);
                src.sendFailure(Component.literal("Engineer could not spawn. Workbench rolled back"));return 0;
            }
        } else if (needsDesk) {
            // Existing staff; workstation restoration only at the known reserved empty cell.
            level.setBlock(desk,TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState(),3);
        }
        if (existing==null) data.setService(c,plan);
        String info="[TC service ACTIVATED] "+serviceSummary(c,plan);
        src.sendSuccess(()->Component.literal(info),true);
        LOG.info("[TC-DEV50C1] {}",info);
        return 1;
    }

    /**
     * DEV50c5: READ-ONLY report for the actual persisted locomotive/coach link.
     * No cleanupStaleLinks, no link/unlink, no movement and no forced chunk loads.
     * Distinguish unloaded entities from broken coupling after save/reload.
     */
    /** DEV50c6: no AI, no saved departure flag. Only an explicitly armed,
     *   short GP7 + one coach shunt on the EXISTING 17-cell E/W platform.
     *   The accepted locomotive and coupler physics are not modified.
     */
    private static String manualDepartureGate(ServerLevel level,StationData data,BlockPos c,
                                              StagedConsist recorded,boolean requireParked) {
        if (recorded==null || !data.enclosed(c) || data.service(c)==null)
            return "No activated enclosed-office staged service";
        ServicePlan plan=data.service(c);
        if (plan.coaches()!=1 || !"traincraft:locomotive_diesel_gp7".equals(SERVICE_PROFILES[plan.index()].locomotive())
                || !"traincraft:passenger_coach_blue".equals(SERVICE_PROFILES[plan.index()].coach()))
            return "Only the GP7 + one Blue Coach is supported";
        Entity first=level.getEntity(recorded.locomotive()),second=level.getEntity(recorded.coach());
        if (!(first instanceof LegacyPoweredLocomotive loco) || !(second instanceof SteamTender coach)
                || first.isRemoved() || second.isRemoved()) return "Staged pair is missing/unloaded";
        if (!"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(first.getType()).toString())
                || !"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(second.getType()).toString()))
            return "Staged UUIDs no longer match GP7 and Blue Coach types";
        if (first.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=c.asLong()
                || second.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=c.asLong()
                || !"locomotive".equals(first.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                || !"coach_1".equals(second.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE")))
            return "Saved vehicle ownership mismatch";
        // DEV51B R27b: shared fail-closed facing check protects BOTH
        // supervised R27 entry and legacy manual departure command. Previous
        // R27 staged pairs were yaw +90 / visually reversed; never release
        // their handbrake even if the earlier R27 precheck had passed.
        String facingReject=Dev51BStagingFacingR27b.blocker(
                first.getX(),second.getX(),loco.getYRot(),loco.getTrainFacingYaw());
        if (facingReject!=null) return facingReject;
        // R27c: The accepted coupler keeps entity centers ~3.015 blocks apart,
        // but baked GP7/Blue Coach geometry may visually overlap. Fail closed
        // BEFORE handbrake release. No coupling distance/renderer/physics edits.
        String visualReject=Dev51BVisualClearanceR27c.blocker(
                first.getX(),first.getZ(),second.getX(),second.getZ(),
                loco.getYRot(),loco.getTrainFacingYaw(),second.getYRot());
        if (visualReject!=null) return visualReject;
        if (!CouplingManager.areMutuallyLinked(first,loco,second,coach)
                || first.distanceToSqr(second)>16.0D
                || first.distanceToSqr(second)<4.0D) return "Coupling broken or outside safe 2-to-4 block test envelope";
        if (!first.getPassengers().isEmpty() || !second.getPassengers().isEmpty())
            return "Manual test requires unoccupied locomotive and coach";
        // DEV50c8: fail BEFORE handbrake release if a complete bounded shunt
        // cannot fit inside BOTH accepted staging envelopes. At the second
        // DEV50c7 start (GP7 x=256.970), only 0.230 block remained before
        // the locomotive's x=257.200 boundary, so movement ended mid-test.
        // This is a start-only read-only check; the accepted per-tick guards,
        // power, rail physics, coupling, staff and save behavior are unchanged.
        if (requireParked) {
            double locomotiveRoom=c.getX()+6.2D-loco.getX();
            double coachRoom=c.getX()+4.0D-coach.getX();
            double available=Math.min(locomotiveRoom,coachRoom);
            if (!(available>=TEST_SHUNT_MAX_BLOCKS+0.000001D)) {
                LOG.info("[TC-DEV50C8] DEPARTURE_ROOM_REFUSED station={} locoX={} coachX={}"
                        +" available={} required={} NO_CHANGES",c,
                        String.format(java.util.Locale.ROOT,"%.3f",loco.getX()),
                        String.format(java.util.Locale.ROOT,"%.3f",coach.getX()),
                        String.format(java.util.Locale.ROOT,"%.3f",available),TEST_SHUNT_MAX_BLOCKS);
                return "Insufficient eastbound station staging room for a complete 0.75-block manual shunt"
                        +" (available="+String.format(java.util.Locale.ROOT,"%.3f",Math.max(0.0D,available))
                        +" blocks; requires at least 0.750). Train remains parked";
            }
        }
        if (Math.abs(loco.getZ()-(c.getZ()+0.5D))>0.35D
                || Math.abs(coach.getZ()-(c.getZ()+0.5D))>0.35D
                || Math.abs(loco.getY()-(c.getY()+0.0625D))>0.55D
                || Math.abs(coach.getY()-(c.getY()+0.0625D))>0.55D
                || loco.getX()<c.getX()+3.0D || loco.getX()>c.getX()+6.2D
                || coach.getX()<c.getX()-2.0D || coach.getX()>c.getX()+4.0D)
            return "Staged train left the short, level station staging envelope";
        BlockPos locoRail=loco.blockPosition(),coachRail=coach.blockPosition();
        for (BlockPos pos:new BlockPos[]{c,locoRail,coachRail,
                BlockPos.containing(loco.getX()+1.0D,c.getY(),c.getZ()+0.5D),
                BlockPos.containing(loco.getX()+2.0D,c.getY(),c.getZ()+0.5D)}) {
            if (!level.hasChunkAt(pos)) return "Platform rail chunk unloaded";
            BlockState rail=level.getBlockState(pos);
            if (!(rail.is(TCBlocks.TRACK_NORMAL.get()) || (pos.equals(c)&&rail.is(TCBlocks.TRACK_STATION.get())))
                    || !rail.hasProperty(RailBlock.SHAPE) || rail.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)
                return "Station rail/shape mismatch at "+pos.toShortString();
        }
        if (loco.getPowerAmount()<=0) return "GP7 has no diesel traction reserve. Add Diesel Fuel or Coal in its fuel slot, wait, retry";
        StaffSlot slot=data.getStaff(c,"railway_engineer");
        if (slot==null || slot.uuid==null || slot.respawnAt>0) return "No healthy tracked Engineer";
        Entity entity=level.getEntity(slot.uuid);
        if (!(entity instanceof Villager v) || v.isRemoved()
                || v.getVillagerData().getProfession()!=RailwayVillagers.RAILWAY_ENGINEER.get()
                || v.getOffers().size()<2
                || !"railway_engineer".equals(v.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))
                || !"railway_engineer".equals(v.getPersistentData().getString(STAFF_SLOT_ROLE_TAG))
                || v.getPersistentData().getLong(STAFF_STATION_TAG)!=c.asLong()
                || v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
                || !v.getBrain().getMemory(MemoryModuleType.JOB_SITE).get().dimension().equals(level.dimension())
                || !engineerSiteMatches(level,c,v.getBrain().getMemory(MemoryModuleType.JOB_SITE).get().pos()))
            return "Engineer profession/trades/owned workstation not healthy; run engineer probe";
        if (requireParked) {
            if (!loco.isHandBrakeApplied() || loco.getDeltaMovement().horizontalDistance()>0.01D
                    || coach.getDeltaMovement().horizontalDistance()>0.01D)
                return "Train is not parked with handbrake applied";
        } else if (loco.getDeltaMovement().horizontalDistance()>0.071D
                || coach.getDeltaMovement().horizontalDistance()>0.071D) {
            return "Speed exceeded 5 km/h hard guard";
        }
        // A train can only pass an empty track lane. Side-platform staff do not
        // block it. This AABB scans the next physical GP7 body sweep, not 17 cells.
        AABB ahead=new AABB(loco.getX()+0.65D,loco.getY()-0.35D,loco.getZ()-0.48D,
                loco.getX()+3.15D,loco.getY()+1.9D,loco.getZ()+0.48D);
        if (!level.getEntitiesOfClass(LivingEntity.class,ahead,e->!e.isRemoved()).isEmpty())
            return "Person or animal in locomotive's forward clearance lane";
        AABB platform=new AABB(c.offset(-8,-2,-1),c.offset(10,4,2));
        for (Entity other:level.getEntitiesOfClass(Entity.class,platform,
                e->e!=first && e!=second && !e.isRemoved()
                    && (e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
                       || e instanceof traincraft.entity.train.CoupleableRollingStock)))
            return "Additional rolling stock at "+other.blockPosition().toShortString();
        return null;
    }

    /** This is the accepted vanilla-facing handbrake API; its ServerPlayer
     * argument is unused in the frozen SmallSteam implementation. The hard
     * stop remains possible even if the initiating player logs out. */
    private static void parkStagedPair(ServerLevel level,StagedConsist pair,UUID operator) {
        if (pair==null) return;
        Entity first=level.getEntity(pair.locomotive()),second=level.getEntity(pair.coach());
        if (first instanceof SmallSteamLocomotive loco && !loco.isRemoved()) {
            loco.setDriverControls(false,false,true);
            if (!loco.isHandBrakeApplied())
                loco.toggleHandBrake(level.getServer().getPlayerList().getPlayer(operator));
            loco.setDeltaMovement(Vec3.ZERO);
        }
        if (second instanceof SteamTender coach && !coach.isRemoved()) coach.setDeltaMovement(Vec3.ZERO);
    }

    private static void haltManualDeparture(ServerLevel level,ManualDeparture session,String reason) {
        StagedConsist pair=new StagedConsist(session.locomotive(),session.coach());
        parkStagedPair(level,pair,session.operator());
        MANUAL_DEPARTURES.remove(session.station().asLong());
        LOG.info("[TC-DEV50C6] DEPARTURE_STOP station={} reason={} loco={} coach={}",
                session.station(),reason,session.locomotive(),session.coach());
        ServerPlayer player=level.getServer().getPlayerList().getPlayer(session.operator());
        if (player!=null) player.sendSystemMessage(Component.literal("[TC service departure] STOP: "+reason
                +". Parking brake applied; no schedule started."));
    }

    private static void tickManualDepartures(ServerLevel level) {
        for (ManualDeparture session:new ArrayList<>(MANUAL_DEPARTURES.values())) {
            if (MANUAL_DEPARTURES.get(session.station().asLong())!=session) continue;
            try {
                StationData data=stations(level);
                StagedConsist saved=data.staged(session.station());
                ServerPlayer operator=level.getServer().getPlayerList().getPlayer(session.operator());
                if (operator==null || operator.level()!=level ||
                        operator.distanceToSqr(session.station().getX()+0.5D,operator.getY(),
                                                session.station().getZ()+0.5D)>96.0D*96.0D) {
                    haltManualDeparture(level,session,"operator left the station/world");continue;
                }
                if (saved==null || !saved.locomotive().equals(session.locomotive())
                        || !saved.coach().equals(session.coach())) {
                    haltManualDeparture(level,session,"staged UUID ownership changed");continue;
                }
                Entity first=level.getEntity(saved.locomotive());
                if (!(first instanceof LegacyPoweredLocomotive loco)) {
                    haltManualDeparture(level,session,"locomotive unloaded or missing");continue;
                }
                double advanced=loco.getX()-session.startX();
                // DEV50c7: reserve two full 0.03-block ticks before the hard
                // 0.75-block ceiling. Never wait until the post-tick position
                // equals the ceiling: that overshot to 0.78 in DEV50c6.
                if (advanced >= TEST_SHUNT_MAX_BLOCKS - TEST_SHUNT_STOP_RESERVE) {
                    LOG.info("[TC-DEV50C7] DISTANCE_PRESTOP station={} moved={} ceiling={} reserve={}",
                            session.station(),String.format(java.util.Locale.ROOT,"%.3f",advanced),
                            TEST_SHUNT_MAX_BLOCKS,TEST_SHUNT_STOP_RESERVE);
                    haltManualDeparture(level,session,"preemptive distance guard ("+
                            String.format(java.util.Locale.ROOT,"%.2f",advanced)+" of 0.75 blocks)");continue;
                }
                if (advanced< -0.18D || level.getGameTime()-session.startTick()>=TEST_SHUNT_MAX_TICKS) {
                    haltManualDeparture(level,session,"short manual reverse/time safety limit ("+
                            String.format(java.util.Locale.ROOT,"%.2f",advanced)+" blocks)");continue;
                }
                String failure=manualDepartureGate(level,data,session.station(),saved,false);
                if (failure!=null) {haltManualDeparture(level,session,failure);continue;}
                // Gentle locomotive-only motion request. Existing per-entity
                // rail tick and FC12 coupler govern the coach; never setPos,
                // never teleport, never alter native path or coupler internals.
                Vec3 motion=loco.getDeltaMovement();
                loco.setDriverControls(false,false,false);
                loco.setDeltaMovement(TEST_SHUNT_BPT,motion.y,0.0D);
                if ((level.getGameTime()-session.startTick())%10==0)
                    LOG.info("[TC-DEV50C6] SHUNT_TICK station={} moved={} speedKmh={} coachGap={}",
                            session.station(),String.format(java.util.Locale.ROOT,"%.3f",advanced),
                            String.format(java.util.Locale.ROOT,"%.2f",loco.getSpeedKmh()),
                            String.format(java.util.Locale.ROOT,"%.3f",Math.sqrt(loco.distanceToSqr(level.getEntity(saved.coach())))));
            } catch(Exception error) {
                LOG.error("[TC-DEV50C6] SHUNT_EXCEPTION fail-closed station="+session.station(),error);
                haltManualDeparture(level,session,"safety monitor exception; send latest.log");
            }
        }
    }

    private static int manualDepartureCommand(CommandSourceStack src,String action) {
        if (PLAYER_OPERATED_STATIONS_ONLY && "start".equals(action)) {src.sendFailure(Component.literal("Service-controlled departure retired: players drive trains."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || !(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Passenger departure test requires an Overworld player. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if (found.isEmpty()) {src.sendFailure(Component.literal("No recorded railway station within 96 blocks. NO CHANGES"));return 0;}
        BlockPos c=found.get();
        ManualDeparture session=MANUAL_DEPARTURES.get(c.asLong());
        if ("status".equals(action)) {
            StagedConsist saved=data.staged(c);
            String info=session==null?"IDLE (no unattended departure; train state unchanged)":
                "MANUAL SHUNT ACTIVE since tick="+session.startTick()+" max=0.75 block/38 ticks";
            src.sendSuccess(()->Component.literal("[TC service departure] "+info
                    +" station="+c.toShortString()+" staged="+(saved!=null)),false);
            return 1;
        }
        if ("stop".equals(action)) {
            if (session!=null) haltManualDeparture(level,session,"manual emergency stop by "+player.getGameProfile().getName());
            else {
                StagedConsist saved=data.staged(c);
                if (saved==null) {src.sendFailure(Component.literal("No staged pair; NO CHANGES"));return 0;}
                Entity locomotive=level.getEntity(saved.locomotive()),coach=level.getEntity(saved.coach());
                if (!(locomotive instanceof LegacyPoweredLocomotive) || !(coach instanceof SteamTender)
                        || locomotive.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=c.asLong()
                        || coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=c.asLong()) {
                    src.sendFailure(Component.literal("Recorded train not loaded or owner mismatch. NO CHANGES"));return 0;
                }
                parkStagedPair(level,saved,player.getUUID());
            }
            src.sendSuccess(()->Component.literal("[TC service departure] EMERGENCY PARK command complete; no autopilot."),true);
            return 1;
        }
        if (!"start".equals(action)) return 0;
        if (!MANUAL_DEPARTURES.isEmpty()) {
            src.sendFailure(Component.literal("Another service shunt is already active. Run /tcrailway service departure stop. NO CHANGES"));return 0;
        }
        StagedConsist saved=data.staged(c);
        String failure=manualDepartureGate(level,data,c,saved,true);
        if (failure!=null) {src.sendFailure(Component.literal("Departure REFUSED: "+failure+". NO CHANGES"));return 0;}
        LegacyPoweredLocomotive locomotive=(LegacyPoweredLocomotive)level.getEntity(saved.locomotive());
        // Explicit invocation and successful preflight are the only route to
        // releasing the handbrake. No movement on any station-world load.
        locomotive.setDriverControls(false,false,false);
        locomotive.toggleHandBrake(player);
        if (locomotive.isHandBrakeApplied()) {
            src.sendFailure(Component.literal("Parking brake did not release. NO MOVEMENT"));return 0;
        }
        MANUAL_DEPARTURES.put(c.asLong(),new ManualDeparture(c,saved.locomotive(),saved.coach(),
                player.getUUID(),locomotive.getX(),level.getGameTime()));
        LOG.info("[TC-DEV50C6] DEPARTURE_START station={} loco={} coach={} engineerHealthy=true"
                +" bpt={} maxBlocks={} maxTicks={} operator={}",
                c,saved.locomotive(),saved.coach(),TEST_SHUNT_BPT,TEST_SHUNT_MAX_BLOCKS,
                TEST_SHUNT_MAX_TICKS,player.getUUID());
        src.sendSuccess(()->Component.literal("[TC service departure] MANUAL low-speed GP7 shunt armed:"
                +" max 0.75 block / 38 ticks. Emergency stop: /tcrailway service departure stop."
                +" No timetable or unattended driving."),true);
        return 1;
    }

    /** DEV50c9R: explicit one-shot, READ-ONLY runtime evidence collection.
     * Does not invoke departure, any POI repair, world edit or chunk ticket.
     */
    private static int inspectRuntimeRoute(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Runtime route diagnostic is Overworld-only; NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> closest=data.closest(BlockPos.containing(src.getPosition()),96);
        if (closest.isEmpty()) {
            src.sendFailure(Component.literal("No saved railway station within 96 blocks; NO CHANGES"));return 0;
        }
        BlockPos c=closest.get();
        StagedConsist pair=data.staged(c);
        if (pair==null) {
            src.sendFailure(Component.literal("No recorded GP7/Blue Coach staged UUIDs; NO CHANGES"));return 0;
        }
        StaffSlot chief=data.getStaff(c,"station_chief");
        StaffSlot conductor=data.getStaff(c,"train_conductor");
        StaffSlot engineer=data.getStaff(c,"railway_engineer");
        return RailwayServiceRuntimeDiagnostic.inspect(src,level,c,
                pair.locomotive(),pair.coach(),
                chief==null?null:chief.uuid,conductor==null?null:conductor.uuid,
                engineer==null?null:engineer.uuid,
                MANUAL_DEPARTURES.containsKey(c.asLong()));
    }

    /** DEV50d1: separate disposable-world ONLY manual probe. The frozen
     * DEV50c8 station shunt command and limits are not modified or reused. */
    private static int dev50d1TurnoutTest(CommandSourceStack src,String action) {
        if (PLAYER_OPERATED_STATIONS_ONLY && "start".equals(action)) {src.sendFailure(Component.literal("Automated turnout movement probes retired. Drive trains manually."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("DEV50d1 test requires Overworld. NO CHANGES")); return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> station=data.closest(BlockPos.containing(src.getPosition()),96);
        if (station.isEmpty()) {
            src.sendFailure(Component.literal("DEV50d1: no saved station within 96 blocks. NO CHANGES"));return 0;
        }
        StagedConsist pair=data.staged(station.get());
        if (pair==null) {
            src.sendFailure(Component.literal("DEV50d1: no recorded staged GP7/coach. NO CHANGES"));return 0;
        }
        return RailwayTurnoutMovementTest.command(src,level,station.get(),
                pair.locomotive(),pair.coach(),!MANUAL_DEPARTURES.isEmpty(),action);
    }

    private static int dev50d2TurnoutBranch(CommandSourceStack src,String action) {
        if (PLAYER_OPERATED_STATIONS_ONLY && "start".equals(action)) {src.sendFailure(Component.literal("Automated turnout movement probes retired. Drive trains manually."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("DEV50d2 requires Overworld. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> station=data.closest(BlockPos.containing(src.getPosition()),96);
        if (station.isEmpty()) {
            src.sendFailure(Component.literal("DEV50d2: no station within 96 blocks. NO CHANGES"));return 0;
        }
        StagedConsist pair=data.staged(station.get());
        if (pair==null) {
            src.sendFailure(Component.literal("DEV50d2: no staged GP7/coach. NO CHANGES"));return 0;
        }
        return RailwayTurnoutNativeRoutingProbe.command(src,level,station.get(),
            pair.locomotive(),pair.coach(),
            !MANUAL_DEPARTURES.isEmpty() || RailwayTurnoutMovementTest.isActive(), action);
    }

    private static int auditStagedService(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Passenger service audit is Overworld-only; NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if (found.isEmpty()) {src.sendFailure(Component.literal("No saved station within 96 blocks; NO CHANGES"));return 0;}
        BlockPos c=found.get();
        StagedConsist saved=data.staged(c);
        if (saved==null) {
            src.sendFailure(Component.literal("No staged locomotive/coach UUID pair recorded. NO CHANGES"));return 0;
        }
        Entity locomotive=level.getEntity(saved.locomotive());
        Entity coach=level.getEntity(saved.coach());
        if (locomotive==null || coach==null) {
            String result="INDETERMINATE (one or both vehicles UNLOADED; NOT a coupling failure) station="
                    +c.toShortString()+" locomotiveLoaded="+(locomotive!=null)+" coachLoaded="+(coach!=null);
            LOG.info("[TC-DEV50C5] AUDIT {}",result);
            src.sendSuccess(()->Component.literal("[TC service coupling] "+result),false);
            return 0;
        }
        boolean types=locomotive instanceof SmallSteamLocomotive
                && coach instanceof SteamTender
                && "traincraft:locomotive_diesel_gp7".equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(locomotive.getType()).toString())
                && "traincraft:passenger_coach_blue".equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(coach.getType()).toString());
        boolean ownership=locomotive.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")==c.asLong()
                && coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")==c.asLong()
                && "locomotive".equals(locomotive.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                && "coach_1".equals(coach.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"));
        boolean mutual=types && CouplingManager.areMutuallyLinked(locomotive,
                (traincraft.entity.train.CoupleableRollingStock)locomotive,coach,
                (traincraft.entity.train.CoupleableRollingStock)coach);
        double distance=Math.sqrt(locomotive.distanceToSqr(coach));
        boolean range=distance<=CouplingManager.MAX_LINK_DISTANCE;
        boolean loaded=!locomotive.isRemoved() && !coach.isRemoved();
        boolean parked=locomotive.getDeltaMovement().lengthSqr()<0.0004D
                && coach.getDeltaMovement().lengthSqr()<0.0004D;
        boolean brake=types && ((SmallSteamLocomotive)locomotive).isHandBrakeApplied();
        // Coupling PASS is separate from the parking observation. Read-only
        // movement/handbrake values are diagnostic, not reasons to claim the
        // saved UUID link has disappeared.
        boolean pass=loaded && types && ownership && mutual && range;
        String result=(pass?"PASS":"FAIL")+" station="+c.toShortString()
                +" types="+types+" ownerTags="+ownership+" mutuallyLinked="+mutual
                +" distance="+String.format(java.util.Locale.ROOT,"%.2f",distance)
                +" parked="+parked+" handbrake="+brake+" removed="+(!loaded);
        LOG.info("[TC-DEV50C5] COUPLING_AUDIT {} locomotive={} coach={}",
                result,saved.locomotive(),saved.coach());
        src.sendSuccess(()->Component.literal("[TC service coupling] "+result),false);
        if (!pass) src.sendSuccess(()->Component.literal(
                "Audit is READ ONLY: did NOT repair links or move trains. Save latest.log for diagnosis."),false);
        return pass?1:0;
    }

    /**
     * DEV50c5: explicit, single-station Railway Engineer adoption or hiring.
     * Never overwrite a managed worker, another station's staff, a villager's
     * profession, trades or brain. Staff continue normal Minecraft schedules.
     */
    /**
     * DEV50c5a: a successful take() is the ONLY way to claim a new toolbench.
     * Never release/steal a full POI whose former owner may be unloaded.
     * The optional second bench is placed at an exact, empty, accessible
     * location only after BOTH existing free POI options have been checked.
     */
    private record EngineerWorksite(BlockPos pos, boolean addedBlock) {}

    /** DEV50c5c: only reserve EXISTING registered free workbenches. A newly
     * placed bench is never claimed in the same server command/tick.
     * This protects unrelated POI owners and keeps recovery two-phase.
     */
    private static Optional<EngineerWorksite> claimEngineerWorksite(ServerLevel level, BlockPos station) {
        BlockPos[] candidates={station.offset(0,0,9),station.offset(-2,0,9),station.offset(2,0,9)};
        var poi=RailwayVillagers.jobSite("railway_engineer").get();
        for (BlockPos candidate:candidates) {
            if (!level.hasChunkAt(candidate) || !level.getBlockState(candidate).is(TCBlocks.RAILWAY_TOOLBENCH.get())) continue;
            Optional<BlockPos> taken=level.getPoiManager().take(h -> h.value()==poi,
                    (h,p) -> p.equals(candidate),candidate,1);
            if (taken.isPresent()) return Optional.of(new EngineerWorksite(candidate,false));
        }
        return Optional.empty();
    }

    /** DEV50c5c phase one: install ONE auxiliary bench in verified empty office
     * space, then RETURN. Registration and reservation are deliberately delayed
     * until the player invokes engineer repair on a subsequent tick.
     * Refuse ambiguous station staff, pre-existing auxiliary benches or
     * nearby loaded competing Engineers. Never release another villager's POI.
     */
    private static int prepareEngineerWorkbench(CommandSourceStack src, ServerLevel level,
                                                 BlockPos station, StaffSlot slot) {
        if (slot!=null) {
            if (slot.uuid==null || slot.respawnAt>0) {
                src.sendFailure(Component.literal("Engineer respawn/ownership pending; NO CHANGES"));return 0;
            }
            Entity entity=level.getEntity(slot.uuid);
            if (!(entity instanceof Villager tracked) || entity.isRemoved()) {
                src.sendFailure(Component.literal("Tracked Engineer must be loaded; NO CHANGES"));return 0;
            }
            CompoundTag data=tracked.getPersistentData();
            if (!"railway_engineer".equals(data.getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))
                    || !"railway_engineer".equals(data.getString(STAFF_SLOT_ROLE_TAG))
                    || !data.contains(STAFF_STATION_TAG,Tag.TAG_LONG)
                    || data.getLong(STAFF_STATION_TAG)!=station.asLong()
                    || tracked.blockPosition().distSqr(station)>48L*48L) {
                src.sendFailure(Component.literal("Tracked Engineer owner/role/distance mismatch; NO CHANGES"));return 0;
            }
        }
        AABB area=new AABB(station.offset(-48,-6,-48),station.offset(49,12,49));
        for (Villager other:level.getEntitiesOfClass(Villager.class,area)) {
            if (slot!=null && other.getUUID().equals(slot.uuid)) continue;
            if ("railway_engineer".equals(other.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))
                    && other.getPersistentData().getLong(STAFF_STATION_TAG)==station.asLong()) {
                src.sendFailure(Component.literal("Another station Engineer is nearby; NO CHANGES"));return 0;
            }
        }
        BlockPos primary=station.offset(0,0,9);
        if (!level.hasChunkAt(primary) || !level.getBlockState(primary).is(TCBlocks.RAILWAY_TOOLBENCH.get())) {
            src.sendFailure(Component.literal("Original station Engineer toolbench missing; NO CHANGES"));return 0;
        }
        var poi=RailwayVillagers.jobSite("railway_engineer").get();
        BlockPos[] positions={primary,station.offset(-2,0,9),station.offset(2,0,9)};
        // Don't add a second bench when a prepared bench already exists.
        // Check any preexisting FREE POI first, then inspect aux blocks.
        for (BlockPos p:positions) {
            if (!level.hasChunkAt(p)) {
                src.sendFailure(Component.literal("Engineer office chunk unloaded; NO CHANGES"));return 0;
            }
            if (!level.getBlockState(p).is(TCBlocks.RAILWAY_TOOLBENCH.get())) continue;
            boolean available=level.getPoiManager().getInRange(h -> h.value()==poi,p,1,
                    PoiManager.Occupancy.HAS_SPACE).anyMatch(rec -> rec.getPos().equals(p));
            if (available) {
                final BlockPos ready=p;
                src.sendSuccess(()->Component.literal("Engineer bench POI already FREE at "
                        +ready.toShortString()+". Run /tcrailway service engineer repair (or hire if unassigned). NO CHANGES"),false);
                return 1;
            }
        }
        for (int i=1;i<positions.length;i++) {
            BlockPos p=positions[i];
            if (level.getBlockState(p).is(TCBlocks.RAILWAY_TOOLBENCH.get())) {
                final BlockPos pending=p;
                src.sendFailure(Component.literal("Auxiliary Engineer bench already exists at "
                        +pending.toShortString()+" but has no FREE POI. Wait and run engineer probe; NO CHANGES"));
                return 0;
            }
        }
        for (int i=1;i<positions.length;i++) {
            BlockPos p=positions[i];
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                    || !level.getBlockState(p.below()).isSolidRender(level,p.below())
                    || !level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(p).inflate(0.15D,0.2D,0.15D)).isEmpty()) continue;
            if (!level.setBlock(p,TCBlocks.RAILWAY_TOOLBENCH.get().defaultBlockState(),3)
                    || !level.getBlockState(p).is(TCBlocks.RAILWAY_TOOLBENCH.get())) {
                src.sendFailure(Component.literal("Auxiliary toolbench placement failed. Inspect blocks; NO POI TAKEN"));return 0;
            }
            LOG.info("[TC-DEV50C5C] ENGINEER_BENCH_PREPARED station={} site={} existingEngineer={} reserveDeferred=true",
                    station,p,slot!=null?slot.uuid:"NONE");
            final BlockPos prepared=p;
            src.sendSuccess(()->Component.literal("Engineer auxiliary toolbench PREPARED at "
                    +prepared.toShortString()+". Wait 3 seconds, run /tcrailway service engineer probe; "
                    +"then /tcrailway service engineer repair. SAME UUID, NO POI TAKEN YET."),true);
            return 1;
        }
        src.sendFailure(Component.literal("No physically safe auxiliary toolbench site; NO CHANGES. Run engineer probe"));
        return 0;
    }

    /** Undo only a POI ticket and optional auxiliary desk created by this call. */
    private static void undoEngineerWorksite(ServerLevel level, EngineerWorksite site) {
        level.getPoiManager().release(site.pos());
        if (site.addedBlock()) level.setBlock(site.pos(),Blocks.AIR.defaultBlockState(),3);
    }

    private static boolean engineerSiteMatches(ServerLevel level, BlockPos station, BlockPos site) {
        if (!site.equals(station.offset(0,0,9))
                && !site.equals(station.offset(-2,0,9))
                && !site.equals(station.offset(2,0,9))) return false;
        return level.hasChunkAt(site) && level.getBlockState(site).is(TCBlocks.RAILWAY_TOOLBENCH.get());
    }

    /** DEV50c5b: READ ONLY. Explain which POI/physical clearance gate refused repair.
     * Never calls take(), release(), setBlock(), moves villagers, or modifies their brains.
     */
    private static int engineerProbe(CommandSourceStack src, ServerLevel level,
                                     BlockPos station, StaffSlot slot) {
        Entity ent=slot==null || slot.uuid==null ? null : level.getEntity(slot.uuid);
        if (!(ent instanceof Villager tracked)) {
            src.sendSuccess(()->Component.literal("[TC engineer probe] Tracked Engineer not loaded. NO CHANGES"),false);
            return 0;
        }
        BlockPos[] positions={station.offset(0,0,9),station.offset(-2,0,9),station.offset(2,0,9)};
        var poi=RailwayVillagers.jobSite("railway_engineer").get();
        String intro="uuid="+tracked.getUUID()+" position="+tracked.blockPosition().toShortString()
                +" profession="+tracked.getVillagerData().getProfession()
                +" offers="+tracked.getOffers().size()
                +" job="+tracked.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                        .map(m -> m.pos().toShortString()).orElse("NONE");
        src.sendSuccess(()->Component.literal("[TC engineer probe] "+intro),false);
        LOG.info("[TC-DEV50C5B] ENGINEER_PROBE station={} {}",station,intro);
        for (int i=0;i<positions.length;i++) {
            BlockPos p=positions[i];
            if (!level.hasChunkAt(p)) {
                final String missing="site="+p.toShortString()+" chunk=UNLOADED";
                src.sendSuccess(()->Component.literal("[TC engineer probe] "+missing),false);
                LOG.info("[TC-DEV50C5B] {}",missing);continue;
            }
            boolean workbench=level.getBlockState(p).is(TCBlocks.RAILWAY_TOOLBENCH.get());
            boolean air=level.getBlockState(p).isAir();
            boolean aboveAir=level.getBlockState(p.above()).isAir();
            boolean solidFloor=level.getBlockState(p.below()).isSolidRender(level,p.below());
            String block=level.getBlockState(p).getBlock().toString();
            long registered=level.getPoiManager().getInRange(h->h.value()==poi,p,1,PoiManager.Occupancy.ANY)
                    .filter(q->q.getPos().equals(p)).count();
            long free=level.getPoiManager().getInRange(h->h.value()==poi,p,1,PoiManager.Occupancy.HAS_SPACE)
                    .filter(q->q.getPos().equals(p)).count();
            List<LivingEntity> blockers=level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(p).inflate(0.15D,0.2D,0.15D));
            String occupants=blockers.stream().limit(3).map(v->
                    BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).toString()+"@"+v.blockPosition().toShortString())
                    .reduce((a,b)->a+","+b).orElse("NONE");
            String verdict=workbench?(free>0?"EXISTING_FREE_POI":"EXISTING_UNAVAILABLE_POI"):
                    i==0?"PRIMARY_MISSING":(air&&aboveAir&&solidFloor&&blockers.isEmpty()?"AUX_PHYSICALLY_FREE":"AUX_BLOCKED");
            String line="site="+p.toShortString()+" block="+block+" registeredPOI="+registered
                    +" freePOI="+free+" air="+air+" aboveAir="+aboveAir+" floorSolid="+solidFloor
                    +" entities="+occupants+" outcome="+verdict;
            src.sendSuccess(()->Component.literal("[TC engineer probe] "+line),false);
            LOG.info("[TC-DEV50C5B] ENGINEER_SITE_PROBE station={} {}",station,line);
        }
        AABB nearby=new AABB(station.offset(-16,-3,3),station.offset(17,8,18));
        for (Villager v:level.getEntitiesOfClass(Villager.class,nearby)) {
            var job=v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (v!=tracked && job.isEmpty()) continue;
            String line="villager="+v.getUUID()+" pos="+v.blockPosition().toShortString()
                    +" profession="+v.getVillagerData().getProfession()
                    +" site="+job.map(g->g.pos().toShortString()).orElse("NONE")
                    +" tracked="+(v==tracked);
            LOG.info("[TC-DEV50C5B] ENGINEER_VILLAGER_SITE {}",line);
        }
        src.sendSuccess(()->Component.literal("[TC engineer probe] READ ONLY. No blocks, POIs, villagers or trains changed."),false);
        return 1;
    }

    /** DEV50c5a: repair ONLY the exact already-tracked Engineer, never spawn/replace him. */
    private static int repairEngineer(CommandSourceStack src, ServerLevel level,
                                      StationData data, BlockPos station, StaffSlot slot) {
        if (slot==null || slot.uuid==null || slot.respawnAt>0) {
            src.sendFailure(Component.literal("No live tracked Engineer to repair; NO CHANGES"));return 0;
        }
        Entity entity=level.getEntity(slot.uuid);
        if (!(entity instanceof Villager villager) || entity.isRemoved()) {
            src.sendFailure(Component.literal("Tracked Engineer is not loaded; NO CHANGES and no duplicate spawned"));return 0;
        }
        CompoundTag tag=villager.getPersistentData();
        if (!"railway_engineer".equals(tag.getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))
                || !"railway_engineer".equals(tag.getString(STAFF_SLOT_ROLE_TAG))
                || !tag.contains(STAFF_STATION_TAG,Tag.TAG_LONG)
                || tag.getLong(STAFF_STATION_TAG)!=station.asLong()
                || villager.blockPosition().distSqr(station)>48L*48L) {
            src.sendFailure(Component.literal("Engineer role, owner or location does not match station; NO CHANGES"));return 0;
        }
        // Explicitly reject loaded competing Engineers: do not silently
        // hand out additional tickets if we cannot identify the owner.
        AABB area=new AABB(station.offset(-48,-6,-48),station.offset(49,12,49));
        for (Villager other:level.getEntitiesOfClass(Villager.class,area)) {
            if (other==villager || other.isRemoved()) continue;
            if ("railway_engineer".equals(other.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))
                    && other.getPersistentData().getLong(STAFF_STATION_TAG)==station.asLong()) {
                src.sendFailure(Component.literal("Another Engineer belonging to station is loaded; NO CHANGES"));return 0;
            }
        }
        Optional<net.minecraft.core.GlobalPos> oldJob=villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        BlockPos site=oldJob.filter(p -> p.dimension().equals(level.dimension())
                        && engineerSiteMatches(level,station,p.pos()))
                .map(net.minecraft.core.GlobalPos::pos).orElse(null);
        EngineerWorksite newClaim=null;
        if (site==null) {
            Optional<EngineerWorksite> claim=claimEngineerWorksite(level,station);
            if (claim.isEmpty()) {
                src.sendFailure(Component.literal("No registered FREE Engineer POI. Run engineer prepare, WAIT, probe, then repair. NO CHANGES"));return 0;
            }
            newClaim=claim.get();site=newClaim.pos();
        }
        // Profession + fresh novice offers are restored ONLY after a real
        // physical and reserved job site has been identified.
        var expected=RailwayVillagers.RAILWAY_ENGINEER.get();
        if (villager.getVillagerData().getProfession()!=expected) {
            villager.setVillagerData(villager.getVillagerData().setProfession(expected).setLevel(1));
            villager.refreshBrain(level);
        }
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                net.minecraft.core.GlobalPos.of(level.dimension(),site));
        RailwayTrades.ensureNoviceOffers(villager);
        villager.setPersistenceRequired();
        final BlockPos used=site;
        boolean healthy=villager.getVillagerData().getProfession()==expected
                && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                        .filter(p -> p.dimension().equals(level.dimension()) && p.pos().equals(used)).isPresent()
                && !villager.getOffers().isEmpty();
        if (!healthy) {
            // Never claim recovery if its immediate invariants do not hold.
            // Don't return the new ticket if this villager now has its memory.
            LOG.error("[TC-DEV50C5A] ENGINEER_REPAIR_INCOMPLETE uuid={} site={}",villager.getUUID(),site);
            src.sendFailure(Component.literal("Engineer recovery incomplete. SAVE LOG; no success claimed"));return 0;
        }
        LOG.info("[TC-DEV50C5A] ENGINEER_REPAIRED station={} uuid={} jobSite={} auxiliaryPlaced={} offers={}",
                station,villager.getUUID(),used,newClaim!=null && newClaim.addedBlock(),villager.getOffers().size());
        src.sendSuccess(()->Component.literal("[TC service engineer] REPAIRED original UUID="
                +villager.getUUID()+" jobSite="+used.toShortString()+" offers="+villager.getOffers().size()
                +". Train stayed parked. SAVE/RELOAD and check again."),true);
        return 1;
    }

    private static int engineerCommand(CommandSourceStack src,String action) {
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Engineer assignment is Overworld-only. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if (found.isEmpty()) {src.sendFailure(Component.literal("No railway station within 96 blocks. NO CHANGES"));return 0;}
        BlockPos c=found.get();
        StaffSlot recorded=data.getStaff(c,"railway_engineer");
        String role="railway_engineer";
        if ("status".equals(action)) {
            Entity e=recorded==null || recorded.uuid==null?null:level.getEntity(recorded.uuid);
            String integrity=e instanceof Villager v?" profession="+v.getVillagerData().getProfession()
                    +" offers="+v.getOffers().size()+" jobSite="
                    +v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                            .map(p -> p.pos().toShortString()).orElse("NONE"):"";
            String info=recorded==null?"UNASSIGNED":recorded.respawnAt>0
                    ?"death recorded; scheduled respawn pending":"TRACKED UUID="+recorded.uuid
                    +" loaded="+(e!=null)+integrity;
            src.sendSuccess(()->Component.literal("[TC service engineer] "+info),false);
            return 1;
        }
        if (!data.enclosed(c) || data.service(c)==null || !level.hasChunkAt(c)
                || !level.getBlockState(c).is(TCBlocks.TRACK_STATION.get())) {
            src.sendFailure(Component.literal("An activated enclosed-office railway station is required. NO CHANGES"));return 0;
        }
        if ("probe".equals(action)) return engineerProbe(src,level,c,recorded);
        if ("prepare".equals(action)) return prepareEngineerWorkbench(src,level,c,recorded);
        if ("repair".equals(action)) return repairEngineer(src,level,data,c,recorded);
        if (recorded!=null) {
            src.sendFailure(Component.literal("Engineer already managed or death-respawn pending. NO CHANGES. Use engineer repair if his trades disappeared"));return 0;
        }
        BlockPos desk=c.offset(0,0,9);
        if (!level.hasChunkAt(desk) || !level.getBlockState(desk).is(TCBlocks.RAILWAY_TOOLBENCH.get())) {
            src.sendFailure(Component.literal("Railway Engineer workbench at office is missing or unloaded. NO CHANGES"));return 0;
        }
        AABB scan=new AABB(c.offset(-24,-4,-24),c.offset(25,10,27));
        List<Villager> engineers=level.getEntitiesOfClass(Villager.class,scan,v ->
                !v.isRemoved() && role.equals(v.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG)));
        if ("assign".equals(action)) {
            if (engineers.size()!=1) {
                src.sendFailure(Component.literal("Expected exactly ONE nearby railway engineer, found "
                        +engineers.size()+". NO CHANGES"));return 0;
            }
            Villager v=engineers.get(0);
            CompoundTag tag=v.getPersistentData();
            if (!v.getVillagerData().getProfession().equals(RailwayVillagers.forEgg(role).get())
                    || (tag.contains(STAFF_STATION_TAG,Tag.TAG_LONG)
                            && tag.getLong(STAFF_STATION_TAG)!=c.asLong())
                    || tag.contains(STAFF_SLOT_ROLE_TAG,Tag.TAG_STRING)
                            && !role.equals(tag.getString(STAFF_SLOT_ROLE_TAG))) {
                src.sendFailure(Component.literal("Existing engineer has different job/station ownership. NO CHANGES"));return 0;
            }
            Optional<net.minecraft.core.GlobalPos> job=v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (job.isEmpty() || !job.get().dimension().equals(level.dimension())
                    || !engineerSiteMatches(level,c,job.get().pos())) {
                src.sendFailure(Component.literal("Engineer job-site memory does not match an office toolbench. NO CHANGES"));return 0;
            }
            tag.putLong(STAFF_STATION_TAG,c.asLong());
            tag.putString(STAFF_SLOT_ROLE_TAG,role);
            v.setPersistenceRequired();
            data.registerStaff(c,role,v.getUUID());
            LOG.info("[TC-DEV50C5A] ENGINEER_ASSIGNED station={} uuid={} method=adopt (trades untouched)",
                    c,v.getUUID());
            src.sendSuccess(()->Component.literal("Engineer assigned: "+v.getUUID()
                    +" (existing villager preserved; no job or trade reset)"),true);
            return 1;
        }
        if (!"hire".equals(action)) return 0;
        if (!engineers.isEmpty()) {
            src.sendFailure(Component.literal("Existing engineer(s) detected: "+engineers.size()
                    +". Use engineer assign if exactly one. NO CHANGES"));return 0;
        }
        if (!staffSpawnSafe(level,c,role)) {
            src.sendFailure(Component.literal("Office engineer standing area is occupied or blocked. NO CHANGES"));return 0;
        }
        Optional<EngineerWorksite> claimed=claimEngineerWorksite(level,c);
        if (claimed.isEmpty()) {
            src.sendFailure(Component.literal("No registered FREE Engineer POI. Run engineer prepare, WAIT, probe, then hire. NO ENGINEER SPAWNED"));return 0;
        }
        EngineerWorksite site=claimed.get();
        BlockPos worker=staffSpawn(c,role);
        Villager v=EntityType.VILLAGER.create(level);
        if (v==null) {
            undoEngineerWorksite(level,site);
            src.sendFailure(Component.literal("Engineer entity creation failed; POI reservation rolled back"));return 0;
        }
        v.moveTo(worker.getX()+0.5,worker.getY(),worker.getZ()+0.5,180.0F,0.0F);
        v.finalizeSpawn(level,level.getCurrentDifficultyAt(worker),MobSpawnType.STRUCTURE,null,null);
        v.setVillagerData(v.getVillagerData().setProfession(RailwayVillagers.RAILWAY_ENGINEER.get()).setLevel(1));
        v.refreshBrain(level);
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                net.minecraft.core.GlobalPos.of(level.dimension(),site.pos()));
        v.getPersistentData().putString(RailwayVillagerEgg.RAILWAY_ROLE_TAG,role);
        v.getPersistentData().putLong(STAFF_STATION_TAG,c.asLong());
        v.getPersistentData().putString(STAFF_SLOT_ROLE_TAG,role);
        v.setPersistenceRequired();
        if (!level.addFreshEntity(v)) {
            undoEngineerWorksite(level,site);
            src.sendFailure(Component.literal("Engineer spawn rejected; POI reservation rolled back. NO CHANGES"));return 0;
        }
        RailwayTrades.ensureNoviceOffers(v);
        data.registerStaff(c,role,v.getUUID());
        LOG.info("[TC-DEV50C5A] ENGINEER_HIRED station={} uuid={} pos={} jobSite={} auxiliaryPlaced={}",
                c,v.getUUID(),worker,site.pos(),site.addedBlock());
        src.sendSuccess(()->Component.literal("Railway Engineer hired with reserved POI "
                +site.pos().toShortString()+" UUID="+v.getUUID()+". Train remains parked."),true);
        return 1;
    }

    /**
     * DEV50c4a: Living-entity collision test for both proposed parked train
     * positions. Legacy train meshes are much longer than their entity hitbox;
     * only the longitudinal axis is expanded substantially. Platform NPCs a
     * full block away from the rail lane may continue normal work schedules.
     * This MUST be called before spawn and again after the coupler moves a car.
     */
    private static Optional<LivingEntity> stagingLivingObstruction(ServerLevel level,
                                                                   Entity lead,Entity coach) {
        AABB locoFootprint=lead.getBoundingBox().inflate(2.6D,0.45D,0.22D);
        AABB coachFootprint=coach.getBoundingBox().inflate(2.6D,0.45D,0.22D);
        Optional<LivingEntity> onLead=level.getEntitiesOfClass(LivingEntity.class,
                locoFootprint, e -> !e.isRemoved()).stream().findFirst();
        if (onLead.isPresent()) return onLead;
        return level.getEntitiesOfClass(LivingEntity.class,
                coachFootprint, e -> !e.isRemoved()).stream().findFirst();
    }

    /**
     * DEV50c4 – first physically placed station consist, manual command ONLY.
     * No schedules/AI/autodrive, no forced tickets, no automatic respawns. The
     * initial rollout only permits the exact already-tested GP7 + one blue coach
     * from the recovered station's persisted service plan.
     */
    private static int stagePassengerConsist(CommandSourceStack src) {
        if (PLAYER_OPERATED_STATIONS_ONLY) {src.sendFailure(Component.literal("Station auto-staging retired: place and drive your own train."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Station staging is Overworld-only. NO CHANGES"));return 0;
        }
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            src.sendFailure(Component.literal("A player must initiate the staging test. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if (found.isEmpty()) {src.sendFailure(Component.literal("No recorded station within 96 blocks. NO CHANGES"));return 0;}
        BlockPos c=found.get();
        ServicePlan plan=data.service(c);
        if (plan==null || !data.enclosed(c)) {
            src.sendFailure(Component.literal("An activated enclosed-office service is required. NO CHANGES"));return 0;
        }
        if (data.staged(c)!=null) {
            src.sendFailure(Component.literal("This station already staged its consist; no duplicate spawning. Use /tcrailway service status"));return 0;
        }
        if (plan.coaches()!=1 || !SERVICE_PROFILES[plan.index()].locomotive().equals("traincraft:locomotive_diesel_gp7")
                || !SERVICE_PROFILES[plan.index()].coach().equals("traincraft:passenger_coach_blue")) {
            src.sendFailure(Component.literal("DEV50c4 safety gate: staging currently supports only the saved GP7 + ONE Blue Coach service. NO CHANGES"));return 0;
        }
        if (!serviceRegistryReady(plan) || !level.hasChunkAt(c)
                || !level.getBlockState(c).is(TCBlocks.TRACK_STATION.get())) {
            src.sendFailure(Component.literal("Station rail or registered roster missing. NO CHANGES"));return 0;
        }
        // A whole 17-cell straight station platform is required. Do not assume
        // the wider network was placed, load neighboring chunks, or spawn on a
        // medium curve/switch/diagonal track without trajectory clearance.
        for (int dx=-8;dx<=8;dx++) {
            BlockPos rail=c.offset(dx,0,0);
            if (!level.hasChunkAt(rail)) {src.sendFailure(Component.literal("Staging rail chunk unloaded. NO CHANGES"));return 0;}
            BlockState state=level.getBlockState(rail);
            boolean correct=dx==0?state.is(TCBlocks.TRACK_STATION.get()):state.is(TCBlocks.TRACK_NORMAL.get());
            if (!correct || !state.hasProperty(RailBlock.SHAPE)
                    || state.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST
                    || !level.getBlockState(rail.above()).isAir()
                    || !level.getBlockState(rail.above(2)).isAir()) {
                src.sendFailure(Component.literal("Platform rail/air clearance changed at "+rail.toShortString()+". NO CHANGES"));return 0;
            }
        }
        AABB swept=new AABB(c.getX()-8.5,c.getY()-0.2,c.getZ()-1.0,
                             c.getX()+9.5,c.getY()+3.5,c.getZ()+2.0);
        // DEV50c4a: KEEP the entire 17-rail corridor protected against ANY
        // existing minecart or modded coupleable rolling stock. A nearby train
        // must never be ignored just because its narrow hitbox is outside the
        // two target vehicle footprints.
        Optional<Entity> parkedVehicle=level.getEntitiesOfClass(Entity.class,swept,
                e -> !e.isRemoved()
                        && (e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
                        || e instanceof traincraft.entity.train.CoupleableRollingStock))
                .stream().findFirst();
        if (parkedVehicle.isPresent()) {
            Entity blocked=parkedVehicle.get();
            LOG.warn("[TC-DEV50C4A] BLOCK_EXISTING_ROLLING_STOCK station={} type={} at={}",
                    c,BuiltInRegistries.ENTITY_TYPE.getKey(blocked.getType()),blocked.blockPosition());
            src.sendFailure(Component.literal("Existing rolling stock in station rail corridor at "
                    +blocked.blockPosition().toShortString()+". NO CHANGES"));return 0;
        }
        Entity locomotive=BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("traincraft:locomotive_diesel_gp7")).create(level);
        Entity coach=BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("traincraft:passenger_coach_blue")).create(level);
        if (!(locomotive instanceof SmallSteamLocomotive lead) || !(coach instanceof SteamTender trailing)
                || !CouplingManager.isSupportedPair(locomotive,coach)) {
            src.sendFailure(Component.literal("Entity class/coupler contract mismatch. NO CHANGES"));return 0;
        }
        // DEV51B R27d: GP7 EASTBOUND, one Blue Coach WEST. The first
        // 5.0-block pre-link separation is deliberate: the coach's *scoped*
        // StageSpacingR27d coupler hook settles it to approximately 3.795.
        // Put the lead at +5 (not +4.5) so the settled coach remains on the
        // NORMAL rail at +1 (not on the station-stop rail at x=center).
        // Lead +5 also leaves 1.2 blocks inside the existing +6.2 short-shunt
        // envelope, enough for a 0.75-block supervised move. All 17 track
        // cells remain exactly the same; no global coupler/physics edits.
        lead.moveTo(c.getX()+5.0D,c.getY()+0.0625D,c.getZ()+0.5D,-90.0F,0.0F);
        trailing.moveTo(c.getX(),c.getY()+0.0625D,c.getZ()+0.5D,-90.0F,0.0F);
        // Check model-aware footprints instead of every person standing along
        // the entire station platform. The extra X length covers legacy train
        // bodies (which extend beyond their narrow vanilla entity hitboxes);
        // the narrow Z clearance continues to block people actually on rails.
        // Both objects are not added to the world yet: failure has NO changes.
        Optional<LivingEntity> occupied=stagingLivingObstruction(level,lead,trailing);
        if (occupied.isPresent()) {
            LivingEntity blocker=occupied.get();
            LOG.warn("[TC-DEV50C4A] BLOCK_LIVING_IN_TRAIN_FOOTPRINT station={} type={} at={}",
                    c,BuiltInRegistries.ENTITY_TYPE.getKey(blocker.getType()),blocker.blockPosition());
            src.sendFailure(Component.literal("Train placement area occupied by "
                    +BuiltInRegistries.ENTITY_TYPE.getKey(blocker.getType())+" at "
                    +blocker.blockPosition().toShortString()+". Move off rail; NO CHANGES"));return 0;
        }
        lead.setDeltaMovement(Vec3.ZERO);trailing.setDeltaMovement(Vec3.ZERO);
        lead.setDriverControls(false,false,true);
        if (!lead.isHandBrakeApplied()) lead.toggleHandBrake(player);
        // Tags survive world saving and identify the exact managed pair.
        for (Entity train : new Entity[]{lead,trailing}) {
            train.getPersistentData().putLong("TC_RAILWAY_SERVICE_STATION",c.asLong());
            train.getPersistentData().putString("TC_RAILWAY_SERVICE_STAGE","DEV50c4");
        }
        lead.getPersistentData().putString("TC_RAILWAY_SERVICE_ROLE","locomotive");
        trailing.getPersistentData().putString("TC_RAILWAY_SERVICE_ROLE","coach_1");
        // Only THIS newly staged disposable-copy pair may use R27d's
        // 0.78-block coach-only coupler reach. The passenger subclass requires
        // BOTH matching tags + roles + same saved station + actual GP7 leader.
        lead.getPersistentData().putString("TC_DEV51B_R27D_SPACING",Dev51BStageSpacingR27d.SCOPE);
        trailing.getPersistentData().putString("TC_DEV51B_R27D_SPACING",Dev51BStageSpacingR27d.SCOPE);
        boolean addedLead=false,addedCoach=false,linked=false;
        try {
            addedLead=level.addFreshEntity(lead);
            if (addedLead) addedCoach=level.addFreshEntity(trailing);
            if (addedLead && addedCoach) linked=CouplingManager.link(lead,lead,trailing,trailing)
                    && CouplingManager.areMutuallyLinked(lead,lead,trailing,trailing);
            // The coupler may align the coach. Never accept an alignment off the
            // station rails, outside the corridor, or beyond the 8-block link.
            boolean inside=linked
                    && Math.abs(trailing.getZ()-(c.getZ()+0.5))<0.75
                    && trailing.getX()>c.getX()-7.5 && trailing.getX()<c.getX()+7.5
                    && level.getBlockState(trailing.blockPosition()).is(TCBlocks.TRACK_NORMAL.get())
                    && lead.distanceToSqr(trailing)<=CouplingManager.MAX_LINK_DISTANCE*CouplingManager.MAX_LINK_DISTANCE;
            // Existing coupling code can reposition the coach immediately.
            // Re-check the FINAL positions after alignment, before persisting a
            // consist, so a nearby pedestrian cannot be overlapped by a move.
            Optional<LivingEntity> postCoupleOccupant=stagingLivingObstruction(level,lead,trailing);
            // R27d: require the final COUPLED position to meet R27c's
            // conservative visible-body clearance, not just the 8-block link
            // limit. Otherwise rollback both new entities before saving UUIDs.
            Dev51BVisualClearanceR27c.Observation stagedGap=Dev51BVisualClearanceR27c.observe(
                    lead.getX(),lead.getZ(),trailing.getX(),trailing.getZ(),
                    lead.getYRot(),lead.getTrainFacingYaw(),trailing.getYRot());
            boolean physicalGap=Dev51BStageSpacingR27d.acceptFinalPosition(
                    lead.getX(),trailing.getX(),lead.getYRot(),trailing.getYRot());
            if (!stagedGap.clear() || !physicalGap) {
                LOG.warn("[TC-DEV51B-R27D] STAGE_GAP_REJECT station={} centerGap={} estimatedSeam={} "
                        +"gapReason={} safeRange={} noServiceSave=true",
                        c,stagedGap.centerGap(),stagedGap.estimatedSeam(),
                        stagedGap.reason(),physicalGap);
            }
            if (!inside || postCoupleOccupant.isPresent() || !stagedGap.clear() || !physicalGap) {
                if (postCoupleOccupant.isPresent()) {
                    LivingEntity blocker=postCoupleOccupant.get();
                    LOG.warn("[TC-DEV50C4A] POST_LINK_LIVING_COLLISION station={} type={} at={}",
                            c,BuiltInRegistries.ENTITY_TYPE.getKey(blocker.getType()),blocker.blockPosition());
                }
                if (linked) CouplingManager.unlinkPair(lead,lead,trailing,trailing);
                if (addedCoach) trailing.discard();
                if (addedLead) lead.discard();
                LOG.error("[TC-DEV50C4] SPAWN_ABORT station={} leadAdded={} coachAdded={} link={} inside={}",
                        c,addedLead,addedCoach,linked,inside);
                src.sendFailure(Component.literal("Staging or coupler audit failed; spawned vehicles removed. Send latest.log"));
                return 0;
            }
            // This record is written only after the complete two-entity audit.
            data.markStaged(c,new StagedConsist(lead.getUUID(),trailing.getUUID()));
            LOG.info("[TC-DEV50C4] STAGED_SUCCESS station={} locomotive={} coach={} linked={} parkedBrake={} "
                    +"locoPos={} coachPos={}",c,lead.getUUID(),trailing.getUUID(),linked,
                    lead.isHandBrakeApplied(),lead.position(),trailing.position());
            LOG.info("[TC-DEV51B-R27D] MODEL_GAP_STAGED station={} scope={} gp7X={} coachX={} gap={} "
                    +"estimatedSeam={} expectedTarget={} headlessOnly=false autoDispatch=false",
                    c,Dev51BStageSpacingR27d.SCOPE,lead.getX(),trailing.getX(),
                    stagedGap.centerGap(),stagedGap.estimatedSeam(),
                    Dev51BStageSpacingR27d.TARGET_CENTER_GAP);
            src.sendSuccess(()->Component.literal("Staged GP7 + Blue Coach, coupled and parked at "+c.toShortString()
                    +". No autopilot or departure is enabled."),true);
            return 1;
        } catch (Exception e) {
            if (linked) CouplingManager.unlinkPair(lead,lead,trailing,trailing);
            if (addedCoach) trailing.discard();
            if (addedLead) lead.discard();
            LOG.error("[TC-DEV50C4] SPAWN_EXCEPTION station="+c,e);
            src.sendFailure(Component.literal("Staging exception; rollback attempted. Save log; DO NOT RETRY"));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tcrailway")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    var src = ctx.getSource();
                    src.sendSuccess(() -> Component.literal("R36 playerOperatedOnly=" + PLAYER_OPERATED_STATIONS_ONLY + " stationGeneration=" + AUTOMATIC.get()
                            + " freshRegions=" + FRESH_REGIONS.size() + " stations=" + stations(src.getLevel()).count()), false);
                    return 1;
                }))
                .then(Commands.literal("debug")
                    .executes(ctx -> debugState(ctx.getSource()))
                    .then(Commands.literal("on").executes(ctx -> {
                        debugOverride=true;
                        ctx.getSource().sendSuccess(()->Component.literal("TC railway verbose diagnostics ON (session only)"),false);
                        return debugState(ctx.getSource());
                    }))
                    .then(Commands.literal("off").executes(ctx -> {
                        debugOverride=false;
                        ctx.getSource().sendSuccess(()->Component.literal("TC railway verbose diagnostics OFF"),false);
                        return debugState(ctx.getSource());
                    })))
                .then(Commands.literal("diagnose").executes(ctx -> diagnose(ctx.getSource())))
                .then(Commands.literal("autoscan").executes(ctx -> r40mAutoScan(ctx.getSource())))
                .then(Commands.literal("preview").executes(ctx -> commandSite(ctx.getSource(), false)))
                .then(Commands.literal("place").executes(ctx -> commandSite(ctx.getSource(), true)))
                .then(Commands.literal("upgrade").executes(ctx -> upgrade(ctx.getSource())))
                .then(Commands.literal("service")
                    .then(Commands.literal("preview").executes(ctx -> serviceCommand(ctx.getSource(),"preview")))
                    .then(Commands.literal("activate").executes(ctx -> serviceCommand(ctx.getSource(),"activate")))
                    .then(Commands.literal("status").executes(ctx -> serviceCommand(ctx.getSource(),"status")))
                    .then(Commands.literal("audit").executes(ctx -> auditStagedService(ctx.getSource())))
                    .then(Commands.literal("route")
                        .then(Commands.literal("diagnose").executes(ctx -> inspectRuntimeRoute(ctx.getSource()))))
                    // Explicit test-only command; guarded by exact disposable
                    // world folder, marker and train UUID/start-state checks.
                    .then(Commands.literal("turnoutbranch")
                        .then(Commands.literal("status").executes(ctx -> dev50d2TurnoutBranch(ctx.getSource(),"status")))
                        .then(Commands.literal("start").executes(ctx -> dev50d2TurnoutBranch(ctx.getSource(),"start")))
                        .then(Commands.literal("stop").executes(ctx -> dev50d2TurnoutBranch(ctx.getSource(),"stop"))))
                    .then(Commands.literal("turnouttest")
                        .then(Commands.literal("status").executes(ctx -> dev50d1TurnoutTest(ctx.getSource(),"status")))
                        .then(Commands.literal("start").executes(ctx -> dev50d1TurnoutTest(ctx.getSource(),"start")))
                        .then(Commands.literal("stop").executes(ctx -> dev50d1TurnoutTest(ctx.getSource(),"stop"))))
                    .then(Commands.literal("departure")
                        .then(Commands.literal("start").executes(ctx -> manualDepartureCommand(ctx.getSource(),"start")))
                        .then(Commands.literal("stop").executes(ctx -> manualDepartureCommand(ctx.getSource(),"stop")))
                        .then(Commands.literal("status").executes(ctx -> manualDepartureCommand(ctx.getSource(),"status"))))
                    .then(Commands.literal("engineer")
                        .then(Commands.literal("status").executes(ctx -> engineerCommand(ctx.getSource(),"status")))
                        .then(Commands.literal("assign").executes(ctx -> engineerCommand(ctx.getSource(),"assign")))
                        .then(Commands.literal("hire").executes(ctx -> engineerCommand(ctx.getSource(),"hire")))
                        .then(Commands.literal("repair").executes(ctx -> engineerCommand(ctx.getSource(),"repair")))
                        .then(Commands.literal("probe").executes(ctx -> engineerCommand(ctx.getSource(),"probe")))
                        .then(Commands.literal("prepare").executes(ctx -> engineerCommand(ctx.getSource(),"prepare"))))
                    .then(Commands.literal("reroll").executes(ctx -> serviceCommand(ctx.getSource(),"reroll")))
                    .then(Commands.literal("stage").executes(ctx -> stagePassengerConsist(ctx.getSource()))))
                .then(Commands.literal("staff")
                    .then(Commands.literal("status").executes(ctx -> staffCommand(ctx.getSource(),false)))
                    .then(Commands.literal("adopt").executes(ctx -> staffCommand(ctx.getSource(),true))))
                .then(Commands.literal("village")
                    .then(Commands.literal("report").executes(ctx -> dev50e6bReport(ctx.getSource())))
                    .then(Commands.literal("railway")
                        .then(Commands.literal("siteaudit").executes(ctx -> f1aSiteAudit(ctx.getSource())))
                        .then(Commands.literal("preview").executes(ctx -> f1Railway(ctx.getSource(),false)))
                        .then(Commands.literal("place").executes(ctx -> f1Railway(ctx.getSource(),true)))
                        .then(Commands.literal("undo").executes(ctx -> f1Undo(ctx.getSource())))
                        .then(Commands.literal("autostatus").executes(ctx -> f1cAutoStatus(ctx.getSource())))
                        .then(Commands.literal("stationtestsurvey").executes(ctx -> f1pSurveyStart(ctx.getSource())))
                        .then(Commands.literal("stationtestsurveystatus").executes(ctx -> f1pSurveyStatus(ctx.getSource())))
                        .then(Commands.literal("stationtestsurveystop").executes(ctx -> f1pSurveyStop(ctx.getSource())))
                        .then(Commands.literal("stationtestfind").executes(ctx -> f1iFindStart(ctx.getSource())))
                        .then(Commands.literal("stationtestfindstatus").executes(ctx -> f1iFindStatus(ctx.getSource())))
                        .then(Commands.literal("stationtestfindstop").executes(ctx -> f1iFindStop(ctx.getSource())))
                        .then(Commands.literal("stationtestpreview").executes(ctx -> f1hStationTestPreview(ctx.getSource())))
                        .then(Commands.literal("stationtestplace")
                            .then(Commands.literal("confirm").executes(ctx -> f1hStationTestPlace(ctx.getSource()))))
                        .then(Commands.literal("stationtestundo").executes(ctx -> f1hStationTestUndo(ctx.getSource())))
                        .then(Commands.literal("stationtestcancel").executes(ctx -> f1hStationTestCancel(ctx.getSource())))
                        .then(Commands.literal("stationaccesspreview").executes(ctx -> f1kAccessPreview(ctx.getSource())))
                        .then(Commands.literal("stationpathaudit").executes(ctx -> f1pPathAudit(ctx.getSource())))
                        .then(Commands.literal("stationaccessplace")
                            .then(Commands.literal("confirm").executes(ctx -> f1kAccessPlace(ctx.getSource()))))
                        .then(Commands.literal("stationaccessundo").executes(ctx -> f1kAccessUndo(ctx.getSource())))
                        .then(Commands.literal("stationaccesscancel").executes(ctx -> f1kAccessCancel(ctx.getSource())))
                        .then(Commands.literal("stationstairpreview").executes(ctx -> f1oStairPreview(ctx.getSource())))
                        .then(Commands.literal("stationstairplace")
                            .then(Commands.literal("confirm").executes(ctx -> f1oStairPlace(ctx.getSource()))))
                        .then(Commands.literal("stationstairundo").executes(ctx -> f1oStairUndo(ctx.getSource())))
                        .then(Commands.literal("stationstaircancel").executes(ctx -> f1oStairCancel(ctx.getSource())))
                        .then(Commands.literal("stationclearpreview").executes(ctx -> f1nClearPreview(ctx.getSource())))
                        .then(Commands.literal("stationclearplace")
                            .then(Commands.literal("confirm").executes(ctx -> f1nClearPlace(ctx.getSource()))))
                        .then(Commands.literal("stationclearundo").executes(ctx -> f1nClearUndo(ctx.getSource())))
                        .then(Commands.literal("stationclearcancel").executes(ctx -> f1nClearCancel(ctx.getSource())))
                        .then(Commands.literal("subwaypreview").executes(ctx -> f1gSubwayPreview(ctx.getSource())))))
                .then(Commands.literal("loop")
                    .then(Commands.literal("adaptiveplan").executes(ctx -> dev50e6bAdaptive(ctx.getSource())))
                    .then(Commands.literal("connectcheck").executes(ctx -> dev50e6cConnectCheck(ctx.getSource())))
                    .then(Commands.literal("detourcheck").executes(ctx -> dev50e6dDetourCheck(ctx.getSource())))
                    .then(Commands.literal("anchorcheck").executes(ctx -> dev50e6eAnchorCheck(ctx.getSource())))
                    .then(Commands.literal("pathcheck").executes(ctx -> dev50e6fPathCheck(ctx.getSource())))
                    .then(Commands.literal("solvercheck").executes(ctx -> dev50e6gSolverCheck(ctx.getSource())))
                    .then(Commands.literal("clearancecheck").executes(ctx -> dev50e6hClearanceCheck(ctx.getSource())))
                    .then(Commands.literal("earthworkscheck").executes(ctx -> dev50e6iEarthworksCheck(ctx.getSource())))
                    .then(Commands.literal("corridorcheck").executes(ctx -> dev50e6jCorridorCheck(ctx.getSource())))
                    .then(Commands.literal("cyclecheck").executes(ctx -> dev50e6kCycleCheck(ctx.getSource())))
                    .then(Commands.literal("missinglinkscheck").executes(ctx -> dev50e6lMissingLinksCheck(ctx.getSource())))
                    .then(Commands.literal("flexiblecheck").executes(ctx -> dev50e6mFlexibleCheck(ctx.getSource())))
                    .then(Commands.literal("closurebarriercheck").executes(ctx -> dev50e6nClosureBarrierCheck(ctx.getSource())))
                    .then(Commands.literal("enclosingbypasscheck").executes(ctx -> dev50e6oEnclosingBypassCheck(ctx.getSource())))
                    .then(Commands.literal("survey").executes(ctx -> dev50e4LoopSurvey(ctx.getSource())))
                    .then(Commands.literal("plan").executes(ctx -> dev50e6Plan(ctx.getSource())))
                    .then(Commands.literal("catalog").executes(ctx -> dev50e6Catalog(ctx.getSource())))
                    .then(Commands.literal("legacyplan").executes(ctx -> dev50e5Plan(ctx.getSource()))))
                .then(Commands.literal("network")
                    .then(Commands.literal("asbuiltrecovery")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30fRecoveryPreview(ctx.getSource()))))
                    .then(Commands.literal("asbuiltstorage")
                        .then(Commands.literal("preflight").executes(ctx -> dev51bR30gStoragePreflight(ctx.getSource()))))
                    .then(Commands.literal("asbuiltsaveddata")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30hSavedDataPreview(ctx.getSource()))))
                    .then(Commands.literal("asbuiltregister")
                        .then(Commands.literal("inspect").executes(ctx -> dev51bR30fRecoveryPreview(ctx.getSource(),3)))
                        .then(Commands.literal("commit")
                            .then(Commands.literal("e6a172d6337927d7ee6f28a8789eed05f18fefe6f0492f3a842d661751b9666d")
                                .executes(ctx -> dev51bR30fRecoveryPreview(ctx.getSource(),4)))))
                .then(Commands.literal("westconstruction")
                        .then(Commands.literal("preflight")
                            .executes(ctx -> dev51bR30jWestPreflight(ctx.getSource()))))
                .then(Commands.literal("westconstruct")
                        .then(Commands.literal("inspect")
                            .executes(ctx -> dev51bR30lWestConstruct(ctx.getSource(),false)))
                        .then(Commands.literal("commit")
                            .then(Commands.literal("5594db3ecfc31d207e14233de238561c14451fc0185da43b32c2db4c6ce5520d")
                                .executes(ctx -> dev51bR30lWestConstruct(ctx.getSource(),true)))))
                // R30o: non-mutating diagnosis of station rail at x=41. No lock release or bypass.
                // R30p: read-only station ACTIVE and native handler condition / session-lock audit.
                .then(Commands.literal("weststationaudit")
                    .then(Commands.literal("inspect").executes(ctx->dev51bR30pStationAudit(ctx.getSource()))))
                .then(Commands.literal("weststationdiag")
                    .then(Commands.literal("inspect").executes(ctx->dev51bR30oStationDiag(ctx.getSource()))))
                .then(Commands.literal("weststoptest")
                    .then(Commands.literal("inspect").executes(ctx->dev51bR30nCommand(ctx.getSource(),"inspect")))
                    .then(Commands.literal("arm").executes(ctx->dev51bR30nCommand(ctx.getSource(),"arm")))
                    .then(Commands.literal("report").executes(ctx->dev51bR30nCommand(ctx.getSource(),"report")))
                    .then(Commands.literal("cancel").executes(ctx->dev51bR30nCommand(ctx.getSource(),"cancel")))
                    .then(Commands.literal("release").executes(ctx->dev51bR30nCommand(ctx.getSource(),"release"))))
                .then(Commands.literal("weststop")
                        .then(Commands.literal("inspect").executes(ctx -> dev51bR30mBufferProbe(ctx.getSource(),"inspect")))
                        .then(Commands.literal("arm").executes(ctx -> dev51bR30mBufferProbe(ctx.getSource(),"arm")))
                        .then(Commands.literal("report").executes(ctx -> dev51bR30mBufferProbe(ctx.getSource(),"report")))
                        .then(Commands.literal("cancel").executes(ctx -> dev51bR30mBufferProbe(ctx.getSource(),"cancel"))))
                .then(Commands.literal("westterminal")
                        .then(Commands.literal("preview")
                            .executes(ctx -> dev51bR30kWestTerminalPreview(ctx.getSource()))))
                .then(Commands.literal("westbuildpreview")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30eWestConstructionPreview(ctx.getSource()))))
                    .then(Commands.literal("westdebug")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30dWestDebug(ctx.getSource()))))
                    .then(Commands.literal("alternatives")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30cAlternatives(ctx.getSource()))))
                    .then(Commands.literal("eastcorridor")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30bCorridor(ctx.getSource()))))
                    .then(Commands.literal("railsurvey")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30aSurvey(ctx.getSource()))))
                    .then(Commands.literal("secondstation")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR30SecondStop(ctx.getSource(),"preview")))
                        .then(Commands.literal("place")
                            .then(Commands.literal("confirm").executes(ctx -> dev51bR30SecondStop(ctx.getSource(),"place"))))
                        .then(Commands.literal("status").executes(ctx -> dev51bR30SecondStop(ctx.getSource(),"status")))
                        .then(Commands.literal("undo")
                            .then(Commands.literal("confirm").executes(ctx -> dev51bR30SecondStop(ctx.getSource(),"undo")))))
                    .then(Commands.literal("destinationaudit")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR29DestinationPreview(ctx.getSource()))))
                    .then(Commands.literal("scan").executes(ctx -> dev51bR18ReadOnlyNetworkScan(ctx.getSource())))
                    .then(Commands.literal("routeplan").executes(ctx -> dev51bR19ReadOnlyRoutePlan(ctx.getSource())))
                    .then(Commands.literal("nativeaudit").executes(ctx -> dev51bR20NativeAudit(ctx.getSource())))
                    .then(Commands.literal("routevalidate").executes(ctx -> dev51bR21ValidateRoute(ctx.getSource())))
                    .then(Commands.literal("dispatchsimulate").executes(ctx -> dev51bR22SimulateDispatch(ctx.getSource())))
                    .then(Commands.literal("interlocksimulate").executes(ctx -> dev51bR23R24InterlockSimulate(ctx.getSource())))
                    .then(Commands.literal("movementdryrun").executes(ctx -> dev51bR25R26DryRun(ctx.getSource(),false)))
                    .then(Commands.literal("villagerpilotstatus").executes(ctx -> dev51bR25R26DryRun(ctx.getSource(),true)))
                    .then(Commands.literal("stationcycle")
                        .then(Commands.literal("preview").executes(ctx -> dev51bR28Command(ctx.getSource(),"preview")))
                        .then(Commands.literal("start")
                            .then(Commands.literal("confirm").executes(ctx -> dev51bR28Command(ctx.getSource(),"start"))))
                        .then(Commands.literal("status").executes(ctx -> dev51bR28Command(ctx.getSource(),"status")))
                        .then(Commands.literal("abort").executes(ctx -> dev51bR28Command(ctx.getSource(),"abort"))))
                    .then(Commands.literal("supervisedtest")
                        .then(Commands.literal("status").executes(ctx -> dev51bR27Command(ctx.getSource(),"status")))
                        .then(Commands.literal("preview").executes(ctx -> dev51bR27Command(ctx.getSource(),"preview")))
                        .then(Commands.literal("start")
                            .then(Commands.literal("confirm").executes(ctx -> dev51bR27Command(ctx.getSource(),"start"))))
                        .then(Commands.literal("abort").executes(ctx -> dev51bR27Command(ctx.getSource(),"abort")))
                        .then(Commands.literal("clearance").executes(ctx -> dev51bR27cClearance(ctx.getSource())))
                        .then(Commands.literal("unstage")
                            .then(Commands.literal("confirm").executes(ctx -> dev51bR27Command(ctx.getSource(),"unstage")))))
                    .then(Commands.literal("preview").executes(ctx -> networkCommand(ctx.getSource(), false)))
                    .then(Commands.literal("place").executes(ctx -> networkCommand(ctx.getSource(), true)))
                    .then(Commands.literal("audit").executes(ctx -> auditNetwork(ctx.getSource())))
                    .then(Commands.literal("barrelfix").executes(ctx -> fixExistingNetworkBarrel(ctx.getSource())))
                    .then(Commands.literal("clearance").executes(ctx -> auditNetworkClearance(ctx.getSource())))
                    .then(Commands.literal("widen")
                        .then(Commands.literal("preview").executes(ctx -> widenNetwork(ctx.getSource(), false)))
                        .then(Commands.literal("apply").executes(ctx -> widenNetwork(ctx.getSource(), true))))));
    }



// DEV51B R30f: immutable AS-BUILT source network RECORD PROPOSAL, READ ONLY.
// Legacy StationData.Networks/WideNetworks mean old template construction;
// NEVER set those flags to claim the 9 observed rails are a built legacy network.
// No setDirty, save, setBlock, chunk ticket, dispatch, or train movement here.
// R30g delegates to the EXACT SAME read-only R30f survey; the storage
// preflight may inspect an in-memory candidate but NEVER writes SavedData.
private static int dev51bR30gStoragePreflight(CommandSourceStack src) {
    return dev51bR30fRecoveryPreview(src,true);
}
// R30h performs the identical live rail witness survey and additionally
// reads an isolated Minecraft SavedData key. No storage mutators or setDirty.
private static int dev51bR30hSavedDataPreview(CommandSourceStack src) {
    return dev51bR30fRecoveryPreview(src,2);
}
private static int dev51bR30fRecoveryPreview(CommandSourceStack src) {
    return dev51bR30fRecoveryPreview(src,false);
}
private static int dev51bR30fRecoveryPreview(CommandSourceStack src, boolean storagePreflight) {
    return dev51bR30fRecoveryPreview(src,storagePreflight?1:0);
}
private static int dev51bR30fRecoveryPreview(CommandSourceStack src, int readonlyMode) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30f] HOLD DISPOSABLE_WORLD_ONLY. Read-only; no changes."));
        return 0;
    }
    final BlockPos source=new BlockPos(61,-60,-3);
    final BlockPos second=new BlockPos(41,-60,-3);
    final BlockPos player=BlockPos.containing(src.getPosition());
    if(source.distSqr(player)>48L*48L) {
        src.sendFailure(Component.literal("[R30f] HOLD NEAR_ORIGINAL_STATION_REQUIRED. Read-only."));
        return 0;
    }
    StationData data=stations(level); // Read existing saved record; NEVER setDirty.
    boolean stationRecorded=data.placed.contains(source);
    boolean sourceIsTestStop=data.isR30TestStop(source);
    boolean legacyNetwork=data.hasNetwork(source);
    boolean legacyWide=data.hasWideNetwork(source);
    boolean legacyOutbound=data.outboundWest(source)!=null;
    boolean destinationSaved=data.placed.contains(second);
    java.util.List<Dev51BAsBuiltRecoveryR30f.Cell> witnesses=new java.util.ArrayList<>();
    for(int off=0;off<=Dev51BAsBuiltRecoveryR30f.NEW_LAST;off++) {
        BlockPos at=source.west(off);
        Dev51BAsBuiltRecoveryR30f.Kind result;
        String observed="UNLOADED",support="UNLOADED";
        boolean loaded=at.getY()-1>=level.getMinBuildHeight()
                &&at.getY()+4<level.getMaxBuildHeight();
        for(int dz=-1;dz<=1&&loaded;dz++) {
            BlockPos flank=at.offset(0,0,dz);
            if(!level.hasChunkAt(flank)||!level.hasChunkAt(flank.below())
                    ||!level.hasChunkAt(flank.above(4)))loaded=false;
        }
        if(!loaded) result=Dev51BAsBuiltRecoveryR30f.Kind.UNLOADED;
        else {
            BlockState state=level.getBlockState(at);
            BlockState below=level.getBlockState(at.below());
            observed=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            support=BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString();
            boolean sturdy=below.isFaceSturdy(level,at.below(),Direction.UP);
            boolean wet=!level.getFluidState(at.below()).isEmpty();
            boolean headroom=true,flanks=true;
            for(int h=0;h<=4;h++) {
                BlockPos above=at.above(h);
                if(!level.getFluidState(above).isEmpty())wet=true;
                if(h>0&&!level.getBlockState(above).isAir())headroom=false;
                if(off>Dev51BAsBuiltRecoveryR30f.SOURCE_LAST)
                    for(int dz=-1;dz<=1;dz+=2) {
                        BlockPos side=at.offset(0,h,dz);
                        if(!level.getBlockState(side).isAir()||!level.getFluidState(side).isEmpty())
                            flanks=false;
                    }
            }
            if(off<=Dev51BAsBuiltRecoveryR30f.SOURCE_LAST) {
                Dev51BRailSurveyR30a.Kind rail=dev51bR30aObserveRail(level,at).kind();
                boolean shape=state.hasProperty(RailBlock.SHAPE)
                        &&state.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
                if(!shape || !headroom || wet || !sturdy)
                    result=Dev51BAsBuiltRecoveryR30f.Kind.WRONG_RAIL;
                else if(rail==Dev51BRailSurveyR30a.Kind.STATION_EW)
                    result=Dev51BAsBuiltRecoveryR30f.Kind.STATION_EW;
                else if(rail==Dev51BRailSurveyR30a.Kind.NORMAL_EW)
                    result=Dev51BAsBuiltRecoveryR30f.Kind.NORMAL_EW;
                else result=Dev51BAsBuiltRecoveryR30f.Kind.WRONG_RAIL;
            } else {
                boolean entity=!level.getEntitiesOfClass(Entity.class,
                    new AABB(at.getX()-1.5,at.getY()-1,at.getZ()-1.5,
                             at.getX()+2.5,at.getY()+5,at.getZ()+2.5),
                    e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty();
                if(entity)result=Dev51BAsBuiltRecoveryR30f.Kind.ENTITY;
                else if(level.getBlockEntity(at)!=null)result=Dev51BAsBuiltRecoveryR30f.Kind.BLOCK_ENTITY;
                else if(wet)result=Dev51BAsBuiltRecoveryR30f.Kind.FLUID;
                else if(!state.isAir())result=Dev51BAsBuiltRecoveryR30f.Kind.BLOCKED;
                else if(!headroom)result=Dev51BAsBuiltRecoveryR30f.Kind.HEADROOM;
                else if(!flanks)result=Dev51BAsBuiltRecoveryR30f.Kind.SIDE_CLEARANCE;
                else if(!sturdy)result=Dev51BAsBuiltRecoveryR30f.Kind.MISSING_SUPPORT;
                else result=off==Dev51BAsBuiltRecoveryR30f.STATION_OFFSET
                        ?Dev51BAsBuiltRecoveryR30f.Kind.PROPOSED_STATION_AIR
                        :Dev51BAsBuiltRecoveryR30f.Kind.PROPOSED_NORMAL_AIR;
            }
        }
        witnesses.add(new Dev51BAsBuiltRecoveryR30f.Cell(off,result,observed,support));
        LOG.info("[TC-DEV51B-R30F] AS_BUILT_CELL offset={} pos={} kind={} block={} support={} existing={} proposedOnly={} noWrites=true",
                off,at,result,observed,support,off<=8,off>=9);
    }
    // No R30f SavedData schema exists yet: report an immutable proposal only.
    var report=Dev51BAsBuiltRecoveryR30f.inspect(witnesses,stationRecorded,
            sourceIsTestStop,legacyNetwork,legacyWide,legacyOutbound,destinationSaved,false);
    // R30i is the ONLY intentionally writable as-built SOURCE-ONLY record path.
    // Strict disposable-copy and near-source checks occurred above, before survey.
    // The 25-cell live witness MUST still match the frozen R30h acceptance hash.
    // No legacy network station flags, tracks, train controllers or dispatchers are touched.
    if(readonlyMode==3 || readonlyMode==4) {
        final Dev51BAsBuiltPersistenceR30g.Draft draft;
        try {
            draft=Dev51BAsBuiltPersistenceR30g.draftFor(report);
        }catch(RuntimeException rejected) {
            LOG.warn("[TC-DEV51B-R30I] REGISTRATION_HOLD witnessRejected={} noWrites=true",rejected.toString());
            src.sendFailure(Component.literal("[R30i] HOLD_LIVE_WITNESS_REJECTED. No record write."));
            return 0;
        }
        final boolean commit=readonlyMode==4;
        final Dev51BAsBuiltRegisteredDataR30i.Result result =
            Dev51BAsBuiltRegisteredDataR30i.execute(level,draft,commit);
        LOG.info("[TC-DEV51B-R30I] AS_BUILT_REGISTRATION command={} status={} sourceSha256={} " +
                "backupValid={} recordPresent={} diskLoaded={} dirty={} " +
                "recordWriteStaged={} blocksChanged=0 entitiesMoved=0 legacyMarkersTouched=false " +
                "railEdits=0 trainDrive=false automaticDispatch=false routeCertified=false",
                commit?"COMMIT":"INSPECT",result.status(),report.sourceFingerprint(),
                result.backupValid(),result.recordPresent(),result.diskLoaded(),
                result.dirty(),result.writeStaged());
        if(result.writeStaged())
            src.sendSuccess(()->Component.literal("[R30i] SOURCE-ONLY RECORD STAGED. Save and exit, reopen disposable copy, then /tcrailway network asbuiltregister inspect. NO RAILS/DRIVE/DISPATCH."),false);
        else if(result.diskLoaded()&&result.status().equals("SOURCE_RECORD_RELOADED_VALID_NO_ROUTE_AUTHORITY"))
            src.sendSuccess(()->Component.literal("[R30i] SOURCE-ONLY RECORD RELOADED AND VALID. Construction and driving still HOLD."),false);
        else if(result.status().equals("READY_SOURCE_ONLY_CONFIRM_REQUIRED"))
            src.sendSuccess(()->Component.literal("[R30i] Backup and witness valid. COMMIT requires explicit full SHA256 confirmation. NO WORLD CHANGES YET."),false);
        else src.sendFailure(Component.literal("[R30i] "+result.status()+". No new record written."));
        return result.writeStaged()||result.diskLoaded()||result.status().equals("READY_SOURCE_ONLY_CONFIRM_REQUIRED")?1:0;
    }
    // R30j prerequisite: an exact R30i SOURCE record loaded from disk and
    // matching the same read-only 25-cell live witness. Not legacy flags.
    if(readonlyMode==5) {
        final Dev51BAsBuiltPersistenceR30g.Draft draft;
        try { draft=Dev51BAsBuiltPersistenceR30g.draftFor(report); }
        catch(RuntimeException invalid) {
            LOG.warn("[TC-DEV51B-R30J] SOURCE_HOLD badLiveWitness={} worldEdits=0",invalid.toString());
            src.sendFailure(Component.literal("[R30j] HOLD_LIVE_SOURCE_WITNESS. NO BUILDS/DRIVE."));
            return 0;
        }
        final Dev51BAsBuiltRegisteredDataR30i.Result record=Dev51BAsBuiltRegisteredDataR30i.execute(level,draft,false);
        final boolean persisted=record.recordPresent()&&record.diskLoaded()&&!record.dirty()
                &&"SOURCE_RECORD_RELOADED_VALID_NO_ROUTE_AUTHORITY".equals(record.status());
        LOG.info("[TC-DEV51B-R30J] PERSISTED_SOURCE_GATE status={} persisted={} diskLoaded={} dirty={} sourceSha256={} legacyNetwork={} noWrites=true",
                record.status(),persisted,record.diskLoaded(),record.dirty(),report.sourceFingerprint(),legacyNetwork);
        if(!persisted) {
            src.sendFailure(Component.literal("[R30j] HOLD_PERSISTED_SOURCE_"+record.status()+". NO BUILDS/DRIVE."));
            return 0;
        }
        return 1;
    }
    LOG.info("[TC-DEV51B-R30F] AS_BUILT_RECORD_PREVIEW schema={} source={} existing={} proposedUnbuilt={} firstHold={} originalStationRail={} extraStationRails={} stationRecorded={} sourceTestStop={} legacyNetworks={} legacyWideNetworks={} legacyOutbound={} secondStationRecorded={} proposedSchemaStorageImplemented=false proposalPersisted=false legacyTemplateRebuilt=false reviewStatus={} sourceSha256={} fullSha256={} worldEdits=0 savedDataWrites=0 chunksForced=0 entitiesMoved=0 trainDrive=false automaticDispatch=false networkRegistered=false connectedRouteCertified=false",
            Dev51BAsBuiltRecoveryR30f.SCHEMA,source,report.existingRail(),
            report.proposedAir(),report.firstHold(),report.sourceStationRails(),
            report.additionalStationRails(),report.sourceStationRecorded(),report.isR30TestStop(),
            report.legacyNetworkRecorded(),report.legacyWideRecorded(),report.hasLegacyOutbound(),
            report.secondStationRecorded(),report.verdict(),report.sourceFingerprint(),report.fullFingerprint());
    src.sendSuccess(()->Component.literal("[R30f] 9 existing as-built rails x=53..61; 16 west rails x=37..52 remain UNBUILT."),false);
    src.sendSuccess(()->Component.literal("[R30f] legacyNetworks="+report.legacyNetworkRecorded()+
            " legacyWide="+report.legacyWideRecorded()+" secondStationRecorded="+report.secondStationRecorded()+
            " HOLD="+report.verdict()),false);
    src.sendSuccess(()->Component.literal("[R30f] As-built source witness="+report.sourceFingerprint().substring(0,16)+
            "; review only. No network record written, no rails placed or train movement."),false);
    if(readonlyMode==1) {
        Dev51BAsBuiltPersistenceR30g.Result preflight;
        try {
            preflight=Dev51BAsBuiltPersistenceR30g.preflight(report);
        }catch(IllegalArgumentException rejected) {
            LOG.warn("[TC-DEV51B-R30G] STORAGE_PREFLIGHT_REJECTED reason={} noWrites=true",rejected.getMessage());
            src.sendFailure(Component.literal("[R30g] HOLD STORAGE_PREFLIGHT_REJECTED. No record written."));
            return 0;
        }
        LOG.info("[TC-DEV51B-R30G] AS_BUILT_STORAGE_PREFLIGHT format={} version={} sourceSha256={} encodedBytes={} wireSha256={} roundTrip={} sourceMatch={} rollbackUnchanged={} storageImplemented=false savedDataWrites=0 legacyNetworkMarkersTouched=false blocksChanged=0 entitiesMoved=0 trainDrive=false automaticDispatch=false mayPersist=false mayBuild=false mayDrive=false mayDispatch=false status={}",
                Dev51BAsBuiltPersistenceR30g.SCHEMA,Dev51BAsBuiltPersistenceR30g.VERSION,
                report.sourceFingerprint(),preflight.encodedBytes(),preflight.formatSha256(),
                preflight.formatRoundTrip(),preflight.sourceWitnessMatches(),
                preflight.rollbackUnchanged(),preflight.verdict());
        src.sendSuccess(()->Component.literal("[R30g] In-memory format roundtrip="+preflight.formatRoundTrip()+
                " witnessMatch="+preflight.sourceWitnessMatches()+" rollback="+preflight.rollbackUnchanged()),false);
        src.sendSuccess(()->Component.literal("[R30g] "+preflight.verdict()+
                "; NO SavedData writes / network registration / rail placement / train driving."),false);
    }
    if(readonlyMode==2) {
        // Obtain draft ONLY from the newly re-read, complete 25-cell R30f witness.
        // Probe an isolated SavedData name; absence is a HOLD, never auto-create a
        // valid network. computeIfAbsent may cache a CLEAN empty object in RAM,
        // but never dirties or saves it. No legacy records are mutated.
        final Dev51BAsBuiltPersistenceR30g.Draft draft;
        try {
            draft=Dev51BAsBuiltPersistenceR30g.draftFor(report);
        } catch(IllegalArgumentException badWitness) {
            LOG.warn("[TC-DEV51B-R30H] SAVEDDATA_WITNESS_REJECTED reason={} noWrites=true",badWitness.getMessage());
            src.sendFailure(Component.literal("[R30h] HOLD_SOURCE_WITNESS_REJECTED. No SavedData write."));
            return 0;
        }
        final Dev51BAsBuiltSavedDataR30h.Survey storage;
        try {
            storage=Dev51BAsBuiltSavedDataR30h.inspect(level,draft);
        } catch(RuntimeException failure) {
            LOG.error("[TC-DEV51B-R30H] SAVEDDATA_READER_FAILED noWrites=true",failure);
            src.sendFailure(Component.literal("[R30h] HOLD_SAVEDDATA_READER_FAILED. No record written."));
            return 0;
        }
        LOG.info("[TC-DEV51B-R30H] SAVEDDATA_READONLY_PREVIEW format={} key={} sourceSha256={} nbtRoundTrip={} liveSourceMatch={} recordPresent={} recordValid={} storeDirty={} diskWrites=0 blocksChanged=0 entitiesMoved=0 legacyMarkersTouched=false trainDrive=false automaticDispatch=false mayRegister=false mayBuild=false mayDrive=false mayDispatch=false status={}",
                Dev51BAsBuiltPersistenceR30g.SCHEMA,Dev51BAsBuiltSavedDataR30h.KEY,
                report.sourceFingerprint(),storage.nbtRoundTrip(),storage.liveSourceMatches(),
                storage.recordPresent(),storage.recordValid(),storage.storageDirty(),storage.verdict());
        src.sendSuccess(()->Component.literal("[R30h] Native NBT roundtrip="+storage.nbtRoundTrip()+
                " sourceMatch="+storage.liveSourceMatches()+" recordPresent="+storage.recordPresent()),false);
        src.sendSuccess(()->Component.literal("[R30h] "+storage.verdict()+
                "; NO save / network registration / rail placement / train driving."),false);
    }
    return 1;
}

// R30j: a disk-reloaded R30i record is REQUIRED before the western survey.
// Both called audits are observation-only; permission to build remains false.
private static int dev51bR30jWestPreflight(CommandSourceStack src) {
    int verified=dev51bR30fRecoveryPreview(src,5);
    if(verified!=1)return 0;
    return dev51bR30eWestConstructionPreview(src,true);
}

private static int dev51bR30kWestTerminalPreview(CommandSourceStack src) {
    int verified=dev51bR30fRecoveryPreview(src,5);
    if(verified!=1)return 0;
    return dev51bR30eWestConstructionPreview(src,true,true);
}



// DEV51B R30m: passive observation of a separate test loco, no movement authorization.
private record R30mLive(java.util.UUID engine,long startTick,Dev51BWestStopSafetyR30m.Track track){}
private static R30mLive r30mLive;
private static String r30mResult="HOLD_NOT_ARMED";
private static String r30mLastUuid="none";
private static boolean dev51bR30mBuilt(ServerLevel level) {
    // Preexisting sources plus newly built 16 rails all must retain exact EW alignment.
    for(int x=37;x<=61;x++){
        BlockPos p=new BlockPos(x,-60,-3);
        if(!level.hasChunkAt(p))return false;
        BlockState s=level.getBlockState(p);
        if(!s.is(x==41||x==61?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                ||!s.hasProperty(RailBlock.SHAPE)||s.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)return false;
    }
    return true;
}
private static boolean dev51bR30mSourceDisk(ServerLevel level){
    try {
        var draft=new Dev51BAsBuiltPersistenceR30g.Draft(
            Dev51BAsBuiltPersistenceR30g.SCHEMA,1,"minecraft:overworld",61,-60,-3,53,61,9,
            "EAST_WEST",Dev51BAsBuiltPersistenceR30g.STATUS,
            Dev51BWestConstructR30l.SOURCE,false,false,false,false);
        var verified=Dev51BAsBuiltRegisteredDataR30i.execute(level,draft,false);
        return "SOURCE_RECORD_RELOADED_VALID_NO_ROUTE_AUTHORITY".equals(verified.status())
                &&verified.diskLoaded()&&!verified.dirty();
    }catch(Exception bad){LOG.warn("[TC-DEV51B-R30M] DISK_SOURCE_HOLD {}",bad.toString());return false;}
}
private static boolean dev51bR30mStopper(ServerLevel level){
    BlockState s=level.getBlockState(new BlockPos(36,-60,-3));
    return s.is(TCBlocks.STOPPER.get())&&s.hasProperty(HorizontalDirectionalBlock.FACING)
            &&s.getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST
            &&s.hasProperty(TrainBufferBlock.ENERGY)&&!s.getValue(TrainBufferBlock.ENERGY);
}
private static int dev51bR30mBufferProbe(CommandSourceStack src,String op) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)||!(src.getEntity() instanceof ServerPlayer)
       ||!dev51bR27Disposable(level)){
        src.sendFailure(Component.literal("[R30m] HOLD_DISPOSABLE_COPY_ONLY"));return 0;
    }
    if("report".equals(op)) {
        String status=r30mLive==null?r30mResult:"OBSERVATION_ACTIVE ticks="+(level.getGameTime()-r30mLive.startTick());
        LOG.info("[TC-DEV51B-R30M] REPORT status={} testLoco={} noDrive=true noDispatch=true",status,r30mLastUuid);
        src.sendSuccess(()->Component.literal("[R30m] "+status+"; no route/collision certification."),false);return 1;
    }
    if("cancel".equals(op)) {
        r30mLive=null;r30mResult="CANCELLED_NO_ROUTE_AUTHORITY";
        src.sendSuccess(()->Component.literal("[R30m] Observation cancelled. No train/blocks changed."),false);return 1;
    }
    if(new BlockPos(61,-60,-3).distSqr(BlockPos.containing(src.getPosition()))>64L*64L){
        src.sendFailure(Component.literal("[R30m] HOLD_NEAR_ORIGINAL_STATION_REQUIRED"));return 0;
    }
    boolean built=dev51bR30mBuilt(level);
    boolean sourceValid=dev51bR30mSourceDisk(level)
       &&stations(level).placed.contains(new BlockPos(61,-60,-3));
    boolean stopper=dev51bR30mStopper(level);
    BlockPos bufferPos=new BlockPos(36,-60,-3);
    boolean collision=stopper&&!level.getBlockState(bufferPos).getCollisionShape(level,bufferPos).isEmpty();
    // Excludes the accepted GP7/Blue Coach parked east at X66.690/62.894.
    var nearby=level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.AbstractMinecart.class,
          new AABB(34,-62,-5.5,60,-54,-0.5),e->!e.isRemoved());
    var test=level.getEntitiesOfClass(LegacyPoweredLocomotive.class,
          new AABB(43,-62,-5.5,53,-54,-0.5),e->!e.isRemoved());
    var loco=test.size()==1?test.get(0):null;
    int others=(int)nearby.stream().filter(e->loco==null||!e.getUUID().equals(loco.getUUID())).count();
    var gate=new Dev51BWestStopSafetyR30m.Gate(built,sourceValid,built,stopper,collision,
          test.size(),others,loco!=null&&loco.getDeltaMovement().horizontalDistance()<0.005,
          loco!=null&&loco.isHandBrakeApplied(),loco==null?0:loco.getX(),loco==null?0:loco.getZ());
    var result=Dev51BWestStopSafetyR30m.evaluate(gate);
    LOG.info("[TC-DEV51B-R30M] BUFFER_PROBE mode={} status={} sourceDisk={} built={} stopperEast={} collisionShape={} testLocos={} otherVehicles={} testX={} testZ={} parked={} handbrake={} noBlockEdits=true noMoves=true automaticDispatch=false stopCertified=false",
        op,result.status(),sourceValid,built,stopper,collision,test.size(),others,
        loco==null?"none":loco.getX(),loco==null?"none":loco.getZ(),gate.testParked(),gate.testBraked());
    if("inspect".equals(op)) {
        src.sendSuccess(()->Component.literal("[R30m] "+result.status()+"; collisionShape="+collision
            +" testLocos="+test.size()+" otherVehicles="+others+". NO DRIVE/DISPATCH."),false);
        return 1;
    }
    if(!"arm".equals(op)||!result.armAllowed()||loco==null||r30mLive!=null){
        src.sendFailure(Component.literal("[R30m] ARM REFUSED: "+(r30mLive!=null?"ALREADY_ACTIVE":result.status())
          +". No movement commanded."));return 0;
    }
    // Arm only in RAM. User, not mod, would control the isolated test loco in future trial.
    r30mLastUuid=loco.getUUID().toString();
    r30mResult="OBSERVATION_ACTIVE_NOT_COLLISION_CERTIFIED";
    r30mLive=new R30mLive(loco.getUUID(),level.getGameTime(),
       new Dev51BWestStopSafetyR30m.Track(false,false,false,false,loco.getX(),0,0));
    src.sendSuccess(()->Component.literal("[R30m] Passive 600-tick telemetry armed for "+r30mLastUuid
        +". It does NOT drive/stop train or authorize collision test. Inspect logs first."),false);
    LOG.info("[TC-DEV51B-R30M] ARMED loco={} duration=600 noDriver=true noBrakeOverride=true collisionCertified=false",r30mLastUuid);
    return 1;
}
private static void dev51bR30mRecordTick(ServerLevel level) {
    R30mLive live=r30mLive;if(live==null)return;
    Entity e=level.getEntity(live.engine());
    boolean present=e instanceof LegacyPoweredLocomotive &&!e.isRemoved();
    boolean source=dev51bR27Disposable(level)&&dev51bR30mSourceDisk(level);
    boolean blocks=dev51bR30mBuilt(level)&&dev51bR30mStopper(level);
    var other=level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.AbstractMinecart.class,
       new AABB(34,-62,-5.5,60,-54,-0.5),v->!v.isRemoved()&&!v.getUUID().equals(live.engine()));
    var sample=new Dev51BWestStopSafetyR30m.Sample(level.getGameTime()-live.startTick(),
        e==null?0:e.getX(),e==null?0:e.getZ(),e==null?0:e.getDeltaMovement().horizontalDistance(),
        present,!other.isEmpty(),source,blocks);
    var next=Dev51BWestStopSafetyR30m.step(live.track(),sample);
    if(next.breached()||next.speedExceeded()||sample.ticks()>=Dev51BWestStopSafetyR30m.MAX_TICKS){
        r30mResult=Dev51BWestStopSafetyR30m.report(next);
        LOG.info("[TC-DEV51B-R30M] TELEMETRY_RESULT {} loco={} ticks={} samples={} minX={} maxSpeedBpt={} approach={} moved={} BREACH={} stopCertified=false noMovementAuthority=true",
           r30mResult,live.engine(),sample.ticks(),next.samples(),next.minX(),next.maxSpeed(),
           next.approachObserved(),next.motionObserved(),next.breached());
        r30mLive=null;return;
    }
    r30mLive=new R30mLive(live.engine(),live.startTick(),next);
}

// R30p read-only station evidence. The local ClassicSpecialTrackBlock.java handler is
// separately verified by the R30p installer. This method does not call that handler.
private static int dev51bR30pStationAudit(CommandSourceStack src){
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
        ||!(src.getEntity() instanceof ServerPlayer)||!dev51bR27Disposable(level)){
        src.sendFailure(Component.literal("[R30p] HOLD_DISPOSABLE_COPY_ONLY"));return 0;
    }
    BlockPos west=new BlockPos(41,-60,-3),old=new BlockPos(61,-60,-3);
    if(!level.hasChunkAt(west)||!level.hasChunkAt(old)){
        src.sendFailure(Component.literal("[R30p] HOLD_STATION_CHUNKS_NOT_LOADED"));return 0;
    }
    BlockState ws=level.getBlockState(west),os=level.getBlockState(old);
    String wid=BuiltInRegistries.BLOCK.getKey(ws.getBlock()).toString();
    String oid=BuiltInRegistries.BLOCK.getKey(os.getBlock()).toString();
    boolean wc=ws.getBlock() instanceof traincraft.block.track.ClassicSpecialTrackBlock;
    Boolean wa=ws.getOptionalValue(traincraft.block.track.ClassicSpecialTrackBlock.ACTIVE).orElse(null);
    Boolean oa=os.getOptionalValue(traincraft.block.track.ClassicSpecialTrackBlock.ACTIVE).orElse(null);
    boolean ew=ws.hasProperty(RailBlock.SHAPE)&&ws.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
    boolean signal=level.hasNeighborSignal(west);
    int power=level.getBestNeighborSignal(west);
    int sourcePower=level.getBestNeighborSignal(old);
    var locos=level.getEntitiesOfClass(LegacyPoweredLocomotive.class,
        new AABB(37,-62,-5.5,53,-54,-.5),e->!e.isRemoved());
    LegacyPoweredLocomotive loco=locos.size()==1?locos.get(0):null;
    double x=loco==null?Double.NaN:loco.getX(),z=loco==null?Double.NaN:loco.getZ();
    double min=loco==null?Double.NaN:loco.getBoundingBox().minX;
    double max=loco==null?Double.NaN:loco.getBoundingBox().maxX;
    boolean stopped=loco!=null&&loco.getDeltaMovement().horizontalDistance()<.003;
    boolean handbrake=loco!=null&&loco.isHandBrakeApplied();
    boolean lock=loco!=null&&loco.getUUID().equals(r30nLock);
    var evidence=new Dev51BWestStationAuditR30p.Evidence(
       dev51bR27Disposable(level),dev51bR30mSourceDisk(level),dev51bR30mBuilt(level),
       dev51bR30mStopper(level),wid.equals("traincraft:track_station"),wc,ew,wa,
       signal,power,oid.equals("traincraft:track_station"),oa,sourcePower,
       locos.size(),handbrake,stopped,x,z,min,max,lock,r30nLive!=null,r30nLast);
    var verdict=Dev51BWestStationAuditR30p.inspect(evidence);
    LOG.info("[TC-DEV51B-R30P] STATION_AUDIT status={} x41id={} x41class={} x41state={} active={} neighborSignal={} bestSignal={} x61id={} x61state={} x61active={} x61bestSignal={} locoCount={} uuid={} x={} z={} bboxMinX={} bboxMaxX={} intersectsX41={} parked={} handbrake={} r30nSessionLock={} r30nTrialActive={} lastR30n={} lockPersistence=SESSION_ONLY_BY_R30N_SOURCE noPersistedLockProof=true sourceHandlerCondition=STATION_AND_NOT_HAS_NEIGHBOR_SIGNAL_HANDLER_SOURCE_CHECKED_BY_INSTALLER handlerApplicable={} originalSourceDisk={} railsBuilt={} bufferEast={} noBlockChanges=true noRedstoneChanges=true noTrainMove=true noRelease=true nativeStopCertified=false dispatch=false",
       verdict.status(),wid,ws.getBlock().getClass().getName(),ws,wa,signal,power,
       oid,os,oa,sourcePower,locos.size(),loco==null?"none":loco.getUUID(),x,z,min,max,
       verdict.stationOverlap(),stopped,handbrake,lock,r30nLive!=null,r30nLast,
       verdict.sourceHandlerApplicable(),evidence.sourceDisk(),evidence.built(),evidence.stopper());
    src.sendSuccess(()->Component.literal("[R30p] "+verdict.status()+"; active="+wa+
       "; signal="+power+"; overlapsRail="+verdict.stationOverlap()),false);
    src.sendSuccess(()->Component.literal("[R30p] R30n lock is SESSION-ONLY; currentLock="+lock+
       "; no driving, redstone, track edits, lock release or native buffer certification."),false);
    return 1;
}

// R30o: one-command read-only survey. Deliberately no command to release R30n lock,
// energize/bypass station rail, set locomotive velocity, or certify buffer collision.
private static int dev51bR30oStationDiag(CommandSourceStack src){
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
        ||!(src.getEntity() instanceof ServerPlayer)
        ||!dev51bR27Disposable(level)){
        src.sendFailure(Component.literal("[R30o] HOLD_DISPOSABLE_COPY_ONLY"));return 0;
    }
    BlockPos west=new BlockPos(41,-60,-3), old=new BlockPos(61,-60,-3);
    if(!level.hasChunkAt(west)||!level.hasChunkAt(old)){
        src.sendFailure(Component.literal("[R30o] HOLD_SOURCE_CHUNKS_NOT_LOADED"));return 0;
    }
    BlockState ws=level.getBlockState(west), os=level.getBlockState(old);
    String westId=BuiltInRegistries.BLOCK.getKey(ws.getBlock()).toString();
    String sourceId=BuiltInRegistries.BLOCK.getKey(os.getBlock()).toString();
    Boolean westPowered=ws.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED).orElse(null);
    Boolean sourcePowered=os.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED).orElse(null);
    boolean isStation=westId.equals("traincraft:track_station");
    boolean eastWest=ws.hasProperty(RailBlock.SHAPE) && ws.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
    int westSignal=level.getBestNeighborSignal(west);
    int oldSignal=level.getBestNeighborSignal(old);
    boolean westNeighbor=level.hasNeighborSignal(west),oldNeighbor=level.hasNeighborSignal(old);
    // Search includes the POST-trial x=42.43 position, excluded by the old pretrial x>=43 gate.
    var locos=level.getEntitiesOfClass(LegacyPoweredLocomotive.class,
        new AABB(37,-62,-5.5,53,-54,-.5),e->!e.isRemoved());
    LegacyPoweredLocomotive loco=locos.size()==1?locos.get(0):null;
    double x=loco==null?Double.NaN:loco.getX(),z=loco==null?Double.NaN:loco.getZ();
    double min=loco==null?Double.NaN:loco.getBoundingBox().minX;
    double max=loco==null?Double.NaN:loco.getBoundingBox().maxX;
    boolean stationary=loco!=null&&loco.getDeltaMovement().horizontalDistance()<.003;
    boolean handbrake=loco!=null&&loco.isHandBrakeApplied();
    boolean guardLock=loco!=null&&loco.getUUID().equals(r30nLock);
    var evidence=new Dev51BWestStationDiagnosisR30o.Evidence(
        dev51bR27Disposable(level),dev51bR30mSourceDisk(level),dev51bR30mBuilt(level),
        dev51bR30mStopper(level),isStation,eastWest,westPowered,westNeighbor,westSignal,
        locos.size(),handbrake,stationary,x,z,min,max,guardLock,r30nLast);
    var verdict=Dev51BWestStationDiagnosisR30o.inspect(evidence);
    LOG.info("[TC-DEV51B-R30O] STATION_DIAG status={} x41id={} x41class={} x41state={} x41powered={} x41directSignal={} x41bestNeighborSignal={} x41ew={} x61id={} x61class={} x61state={} x61powered={} x61directSignal={} x61bestSignal={} locoCount={} locoUuid={} locoX={} locoZ={} bboxMinX={} bboxMaxX={} bboxOverlapsStation={} parked={} handbrake={} r30nLock={} lastR30n={} previousTrialMinX=42.42952825689375 sourceDisk={} railsBuilt={} bufferEast={} blocksWritten=0 redstoneChanged=false trainMoved=false stopCertified=false routeAuthority=false dispatch=false",
        verdict.status(),westId,ws.getBlock().getClass().getName(),ws,westPowered,westNeighbor,westSignal,eastWest,
        sourceId,os.getBlock().getClass().getName(),os,sourcePowered,oldNeighbor,oldSignal,
        locos.size(),loco==null?"none":loco.getUUID(),x,z,min,max,verdict.overlap(),stationary,handbrake,
        guardLock,r30nLast,evidence.sourceDisk(),evidence.built(),evidence.bufferEast());
    src.sendSuccess(()->Component.literal("[R30o] "+verdict.status()+"; westStationPowered="+westPowered+
       "; signal="+westSignal+"; bboxOverlap="+verdict.overlap()+"; R30nLock="+guardLock),false);
    src.sendSuccess(()->Component.literal("[R30o] READ ONLY: no track change, lock release, train movement, native stop certification or dispatch. SEND LOG."),false);
    return 1;
}

// DEV51B R30n: guarded human-controlled, low-speed approach observer. NO automatic drive or dispatch.
private record R30nLive(java.util.UUID engine,java.util.UUID pilot,long start,
                        Dev51BWestLowSpeedR30n.Track track){}
private static R30nLive r30nLive;
private static java.util.UUID r30nLock;
private static String r30nLast="HOLD_NOT_ARMED";
private static double r30nLastMinX=48.5;
private static double r30nLastMaxSpeed=0;
private static boolean dev51bR30nBackup(ServerLevel level){
    try{
        java.nio.file.Path w=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath();
        return Dev51BWestTrialBackupR30n.verify(w.getParent().getParent().getParent(),w);
    }catch(Exception bad){LOG.warn("[TC-DEV51B-R30N] BACKUP_GATE_HOLD {}",bad.toString());return false;}
}
private static int dev51bR30nCommand(CommandSourceStack src,String mode){
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)||!(src.getEntity() instanceof ServerPlayer player)
            ||!dev51bR27Disposable(level)){
        src.sendFailure(Component.literal("[R30n] HOLD_DISPOSABLE_COPY_ONLY"));return 0;
    }
    if("report".equals(mode)){
        String result=r30nLive==null?r30nLast:
            "OBSERVATION_ACTIVE ticks="+(level.getGameTime()-r30nLive.start())
            +" minX="+r30nLive.track().minX()+" maxSpeedBpt="+r30nLive.track().maxSpeed();
        LOG.info("[TC-DEV51B-R30N] REPORT status={} guardLock={} minX={} maxSpeed={} nativeStopCertified=false",result,r30nLock!=null,r30nLastMinX,r30nLastMaxSpeed);
        src.sendSuccess(()->Component.literal("[R30n] "+result+"; emergencyLock="+(r30nLock!=null)+"; NO BUFFER CERTIFICATION."),false);
        return 1;
    }
    if("cancel".equals(mode)){
        if(r30nLive==null){src.sendFailure(Component.literal("[R30n] HOLD_NOT_ACTIVE"));return 0;}
        r30nLock=r30nLive.engine();r30nLive=null;r30nLast="CANCELLED_EMERGENCY_HOLD";
        src.sendSuccess(()->Component.literal("[R30n] Test cancelled; safety lock engaged. Apply handbrake, then release."),false);return 1;
    }
    if("release".equals(mode)){
        Entity locked=r30nLock==null?null:level.getEntity(r30nLock);
        if(r30nLive!=null||!(locked instanceof LegacyPoweredLocomotive loco)||!loco.isHandBrakeApplied()
             ||loco.getDeltaMovement().horizontalDistance()>.003||loco.getX()<38.05){
            src.sendFailure(Component.literal("[R30n] HOLD_RELEASE_REQUIRE_HAND_BRAKED_STATIONARY_SAFE_POSITION"));return 0;
        }
        r30nLock=null;
        src.sendSuccess(()->Component.literal("[R30n] Emergency lock released; train remains handbraked. DO NOT RETRY without review."),false);return 1;
    }
    if(new BlockPos(61,-60,-3).distSqr(BlockPos.containing(src.getPosition()))>64L*64L){
        src.sendFailure(Component.literal("[R30n] HOLD_NEAR_STATION_REQUIRED"));return 0;
    }
    var locos=level.getEntitiesOfClass(LegacyPoweredLocomotive.class,
        new AABB(43,-62,-5.5,53,-54,-0.5),e->!e.isRemoved());
    LegacyPoweredLocomotive test=locos.size()==1?locos.get(0):null;
    var others=level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.AbstractMinecart.class,
        new AABB(34,-62,-5.5,60,-54,-0.5),e->!e.isRemoved()&&(test==null||!e.getUUID().equals(test.getUUID())));
    boolean shape=dev51bR30mStopper(level)
       &&!level.getBlockState(new BlockPos(36,-60,-3)).getCollisionShape(level,new BlockPos(36,-60,-3)).isEmpty();
    boolean backup=dev51bR30nBackup(level);
    var gate=new Dev51BWestLowSpeedR30n.Gate(dev51bR27Disposable(level),dev51bR30mSourceDisk(level),
       dev51bR30mBuilt(level),dev51bR30mStopper(level),shape,backup,locos.size(),others.size(),
       test!=null&&test.getDeltaMovement().horizontalDistance()<.003,
       test!=null&&test.isHandBrakeApplied(),test==null?0:test.getX(),test==null?0:test.getZ());
    var result=Dev51BWestLowSpeedR30n.gate(gate);
    LOG.info("[TC-DEV51B-R30N] INSPECT mode={} status={} sourceDisk={} built={} stopperEast={} collision={} freshBackup={} testLocos={} otherVehicles={} parked={} handbrake={} x={} z={} watchX=38.0 maxSpeedBpt=0.075 originalConsistUnaffected=true noDispatch=true",
      mode,result.status(),gate.sourceDisk(),gate.built(),gate.stopper(),gate.collision(),gate.backup(),gate.testLocos(),gate.otherVehicles(),gate.parked(),gate.handbrake(),gate.x(),gate.z());
    if("inspect".equals(mode)){
        src.sendSuccess(()->Component.literal("[R30n] "+result.status()+"; backup="+backup+"; watchX=38.0; MAX 0.075 b/t. No movement authorized by inspect."),false);return 1;
    }
    if(!"arm".equals(mode)||r30nLive!=null||r30nLock!=null||r30mLive!=null||!result.armAllowed()||test==null){
        src.sendFailure(Component.literal("[R30n] ARM REFUSED: "+(r30nLock!=null?"EMERGENCY_LOCK_ACTIVE":result.status())));return 0;
    }
    r30nLive=new R30nLive(test.getUUID(),player.getUUID(),level.getGameTime(),Dev51BWestLowSpeedR30n.start(test.getX()));
    r30nLast="OBSERVATION_ACTIVE";
    LOG.info("[TC-DEV51B-R30N] ARMED loco={} pilot={} startX={} watchdogX=38.0 overspeedBpt=0.075 duration=800 autoDrive=false autoDispatch=false stopCertified=false",test.getUUID(),player.getUUID(),test.getX());
    src.sendSuccess(()->Component.literal("[R30n] GUARD ARMED for isolated test loco. Manual slow approach only; watchdog freezes at X=38.0 or overspeed. DO NOT treat stop as native buffer proof."),false);return 1;
}
private static void dev51bR30nFreeze(ServerLevel level,java.util.UUID uuid){
    Entity entity=level.getEntity(uuid);
    if(entity instanceof LegacyPoweredLocomotive loco &&!entity.isRemoved()){
        // Emergency containment applies ONLY to the UUID of the separate test loco.
        if(loco.getX()<=38.0)loco.setPos(38.25,loco.getY(),-2.5);
        loco.setDeltaMovement(Vec3.ZERO);
    }
}
private static void dev51bR30nTick(ServerLevel level){
    if(r30nLock!=null)dev51bR30nFreeze(level,r30nLock);
    R30nLive active=r30nLive;if(active==null)return;
    Entity entity=level.getEntity(active.engine());
    boolean present=entity instanceof LegacyPoweredLocomotive && !entity.isRemoved();
    boolean valid=dev51bR27Disposable(level)&&dev51bR30mSourceDisk(level);
    boolean track=dev51bR30mBuilt(level);
    boolean buffer=dev51bR30mStopper(level);
    var others=level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.AbstractMinecart.class,
       new AABB(34,-62,-5.5,60,-54,-0.5),e->!e.isRemoved()&&!e.getUUID().equals(active.engine()));
    var sample=new Dev51BWestLowSpeedR30n.Sample(level.getGameTime()-active.start(),
      present?entity.getX():active.track().minX(),present?entity.getZ():-2.5,
      present?entity.getDeltaMovement().horizontalDistance():0,
      present,others.isEmpty(),valid,track,buffer);
    var updated=Dev51BWestLowSpeedR30n.step(active.track(),sample);
    String decision=Dev51BWestLowSpeedR30n.report(updated,sample.ticks());
    if(Dev51BWestLowSpeedR30n.emergency(updated)){
        r30nLock=active.engine();dev51bR30nFreeze(level,r30nLock);
    }
    if(Dev51BWestLowSpeedR30n.emergency(updated)||updated.settled()||sample.ticks()>=Dev51BWestLowSpeedR30n.MAX_TICKS){
        // After ANY observed movement, keep the isolated locomotive frozen until the
        // operator reapplies its own handbrake and explicitly releases the lock.
        if(updated.moved()&&r30nLock==null){r30nLock=active.engine();dev51bR30nFreeze(level,r30nLock);}
        r30nLast=decision;r30nLastMinX=updated.minX();r30nLastMaxSpeed=updated.maxSpeed();
        LOG.info("[TC-DEV51B-R30N] RESULT status={} loco={} ticks={} samples={} startX={} minX={} maxSpeedBpt={} approached={} moved={} watchdog={} overspeed={} isolationBreach={} emergencyLock={} stopCertified=false noRouteAuthority=true",
          decision,active.engine(),sample.ticks(),updated.samples(),updated.startX(),updated.minX(),updated.maxSpeed(),updated.approached(),updated.moved(),updated.watchdog(),updated.overspeed(),updated.isolationBreach(),r30nLock!=null);
        r30nLive=null;return;
    }
    r30nLive=new R30nLive(active.engine(),active.pilot(),active.start(),updated);
}

// DEV51B R30l: only explicit guarded west-construction write path.
// The source record remains a 9-rail provenance record and is never mutated.
// R30j/R30k preflight methods remain frozen, and driving/dispatch NEVER enabled.
private static int dev51bR30lWestConstruct(CommandSourceStack src,boolean commit) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30l] HOLD_DISPOSABLE_COPY_ONLY"));return 0;
    }
    final BlockPos source=new BlockPos(61,-60,-3);
    if(source.distSqr(BlockPos.containing(src.getPosition()))>48L*48L) {
        src.sendFailure(Component.literal("[R30l] HOLD_NEAR_SOURCE_REQUIRED"));return 0;
    }
    StationData saved=stations(level);
    boolean sourceRecorded=saved.placed.contains(source) && !saved.isR30TestStop(source);
    boolean legacy=saved.hasNetwork(source)||saved.hasWideNetwork(source)||saved.outboundWest(source)!=null
            || saved.placed.contains(new BlockPos(41,-60,-3));
    // Construct same immutable schema as previously accepted R30i, BUT compare
    // only the nine original physical rails. The old R30f 16-AIR witness is
    // intentionally NOT invoked after the extension is placed.
    boolean nine=sourceRecorded;
    for(int x=53;x<=61;x++){
        BlockPos p=new BlockPos(x,-60,-3);
        if(!level.hasChunkAt(p)||!level.hasChunkAt(p.below())||!level.hasChunkAt(p.above(4))){nine=false;break;}
        BlockState s=level.getBlockState(p);
        if(!s.is(x==61?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                ||!s.hasProperty(RailBlock.SHAPE)
                ||s.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST
                ||!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))nine=false;
    }
    boolean persisted=false;
    try {
        var draft=new Dev51BAsBuiltPersistenceR30g.Draft(
                Dev51BAsBuiltPersistenceR30g.SCHEMA,1,"minecraft:overworld",61,-60,-3,53,61,9,
                "EAST_WEST",Dev51BAsBuiltPersistenceR30g.STATUS,
                Dev51BWestConstructR30l.SOURCE,false,false,false,false);
        var persistedRecord=Dev51BAsBuiltRegisteredDataR30i.execute(level,draft,false);
        persisted="SOURCE_RECORD_RELOADED_VALID_NO_ROUTE_AUTHORITY".equals(persistedRecord.status())
                &&persistedRecord.diskLoaded()&&!persistedRecord.dirty();
    }catch(RuntimeException e){LOG.warn("[TC-DEV51B-R30L] SOURCE_GATE_HOLD {}",e.toString());}
    boolean backup=false;
    try {
        var w=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath();
        backup=Dev51BWestBuildBackupR30l.verify(w.getParent().getParent().getParent(),w);
    }catch(Exception e){LOG.warn("[TC-DEV51B-R30L] BACKUP_GATE_HOLD {}",e.toString());}
    BlockState normalState=TCBlocks.TRACK_NORMAL.get().defaultBlockState();
    BlockState stationState=TCBlocks.TRACK_STATION.get().defaultBlockState();
    BlockState bufferState=TCBlocks.STOPPER.get().defaultBlockState();
    boolean registries=normalState.hasProperty(RailBlock.SHAPE)
            && stationState.hasProperty(RailBlock.SHAPE)
            && bufferState.getBlock() instanceof TrainBufferBlock
            && bufferState.hasProperty(HorizontalDirectionalBlock.FACING)
            && bufferState.hasProperty(TrainBufferBlock.ENERGY);
    if(registries){
        normalState=normalState.setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        stationState=stationState.setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        bufferState=bufferState.setValue(HorizontalDirectionalBlock.FACING,Direction.EAST)
                .setValue(TrainBufferBlock.ENERGY,false);
    }
    final BlockState[] planned=new BlockState[17];
    planned[0]=bufferState;
    Dev51BWestConstructR30l.Cell[] west=new Dev51BWestConstructR30l.Cell[16];
    java.util.List<BlockPos> edits=new java.util.ArrayList<>();
    edits.add(new BlockPos(36,-60,-3));
    Dev51BWestConstructR30l.Cell atBuffer=dev51bR30lCell(level,edits.get(0),bufferState,true);
    for(int i=0;i<16;i++) {
        int x=52-i; BlockPos p=new BlockPos(x,-60,-3); edits.add(p);
        planned[i+1]=x==41?stationState:normalState;
        west[i]=dev51bR30lCell(level,p,planned[i+1],false);
    }
    var future1=dev51bR30lCell(level,new BlockPos(35,-60,-3),null,false);
    var future2=dev51bR30lCell(level,new BlockPos(34,-60,-3),null,false);
    int clearSides=0;
    for(int dx=-4;dx<=4;dx++)for(int dz=-3;dz<=3;dz++){
        if(dz==0)continue;
        BlockPos side=new BlockPos(41+dx,-60,-3+dz);
        if(dev51bR30lCell(level,side,null,false)==Dev51BWestConstructR30l.Cell.CLEAR)clearSides++;
    }
    // Full 13x15 area must be loaded; never force new chunks.
    boolean loaded=true;
    for(int x=35;x<=47;x++)for(int z=-10;z<=4;z++)
        if(!level.hasChunkAt(new BlockPos(x,-60,z)))loaded=false;
    int villagers=loaded?level.getEntitiesOfClass(Villager.class,
            new AABB(35,-62,-10,48,-54,5),v->!v.isRemoved()).size():-1;
    int otherStations=0;
    for(BlockPos p:saved.placed)if(!p.equals(source)&&Math.abs(p.getX()-41)<=12
            &&Math.abs(p.getZ()+3)<=9&&Math.abs(p.getY()+60)<=6)otherStations++;
    var result=Dev51BWestConstructR30l.inspect(persisted,backup,registries,nine,west,
            atBuffer,future1,future2,clearSides,villagers,otherStations,legacy);
    LOG.info("[TC-DEV51B-R30L] WEST_CONSTRUCTION mode={} result={} backup={} sourceDisk={} nine={} clearSides={} villagers={} otherStations={} buffer={} west={} future1={} future2={} planSha256={} mayDrive=false dispatch=false",
            commit?"COMMIT":"INSPECT",result.status(),backup,persisted,nine,clearSides,
            villagers,otherStations,atBuffer,java.util.Arrays.toString(west),future1,future2,Dev51BWestConstructR30l.PLAN);
    if(!commit) {
        if(result.status().startsWith("READY_")||result.built())
            src.sendSuccess(()->Component.literal("[R30l] "+result.status()+". Rail/station/buffer status inspected; drive/dispatch HOLD."),false);
        else src.sendFailure(Component.literal("[R30l] "+result.status()+"; no changes."));
        return result.status().startsWith("READY_")||result.built()?1:0;
    }
    if(!result.safeToCommit()) {
        src.sendFailure(Component.literal("[R30l] COMMIT REFUSED: "+result.status()+". No edits."));return 0;
    }
    // Snapshot 17 exact preimages, then verify again immediately before setting.
    java.util.LinkedHashMap<BlockPos,BlockState> pre=new java.util.LinkedHashMap<>();
    for(BlockPos p:edits){
        if(!level.hasChunkAt(p)||!level.getBlockState(p).isAir()||level.getBlockEntity(p)!=null){
            src.sendFailure(Component.literal("[R30l] HOLD_LAST_MOMENT_OBSTRUCTION"));return 0;
        }
        pre.put(p,level.getBlockState(p));
    }
    int changed=0;
    try {
        // Rails laid from old network outward; buffer placed LAST.
        for(int i=1;i<17;i++){
            if(!level.setBlock(edits.get(i),planned[i],3))
                throw new IllegalStateException("RAIL_SET_BLOCK_FAILED_"+i);
            changed++;
        }
        if(!level.setBlock(edits.get(0),planned[0],3))
            throw new IllegalStateException("STOPPER_SET_BLOCK_FAILED");
        changed++;
        for(int i=0;i<17;i++){
            BlockState actual=level.getBlockState(edits.get(i));
            if(!actual.equals(planned[i]))throw new IllegalStateException("POST_AUDIT_FAILED_"+i);
        }
        // Detect any neighbor-update regression to the accepted nine source rails.
        for(int x=53;x<=61;x++){
            BlockPos oldRail=new BlockPos(x,-60,-3);
            BlockState observed=level.getBlockState(oldRail);
            if(!observed.is(x==61?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                    ||!observed.hasProperty(RailBlock.SHAPE)
                    ||observed.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)
                throw new IllegalStateException("SOURCE_RAIL_CHANGED_"+x);
        }
        // Never touch old nine rails, existing StationData or source record.
        LOG.info("[TC-DEV51B-R30L] CONSTRUCTION_STAGED placed=17 normal=15 stationRails=1 buffer=1 bufferPos=36,-60,-3 stationPos=41,-60,-3 sourceRailsUnchanged=true legacyNetworkUntouched=true trainDrive=false dispatch=false stopCollisionCertified=false saveReloadPending=true");
        src.sendSuccess(()->Component.literal("[R30l] 17 BLOCKS STAGED: 15 normal rails, 1 station rail at 41,-60,-3 and 1 east-facing buffer at 36,-60,-3. SAVE/EXIT/REOPEN. NO DRIVE/DISPATCH."),false);
        return 1;
    }catch(Exception failure) {
        int restored=0;
        for(var entry:pre.entrySet()){
            try {if(level.setBlock(entry.getKey(),entry.getValue(),3))restored++;}
            catch(Exception ignored){LOG.error("[TC-DEV51B-R30L] RESTORE_CELL_FAILED {}",entry.getKey(),ignored);}
        }
        LOG.error("[TC-DEV51B-R30L] CONSTRUCTION_ROLLBACK attempted={} restored={} originalChanges={} noAutoRetry=true",pre.size(),restored,changed,failure);
        src.sendFailure(Component.literal("[R30l] COMMIT FAILED; restored "+restored+"/17 preimage blocks. STOP/NO DRIVE. Offline backup retained."));
        return 0;
    }
}
private static Dev51BWestConstructR30l.Cell dev51bR30lCell(ServerLevel level,BlockPos p,
        BlockState expected,boolean buffer) {
    if(p.getY()-1<level.getMinBuildHeight()||p.getY()+4>=level.getMaxBuildHeight())
        return Dev51BWestConstructR30l.Cell.UNLOADED;
    for(int dz=-1;dz<=1;dz++)if(!level.hasChunkAt(p.offset(0,0,dz))
            ||!level.hasChunkAt(p.offset(0,-1,dz))
            ||!level.hasChunkAt(p.offset(0,4,dz)))return Dev51BWestConstructR30l.Cell.UNLOADED;
    BlockState found=level.getBlockState(p);
    if(expected!=null&&found.equals(expected))return buffer
            ?Dev51BWestConstructR30l.Cell.BUFFER_EAST
            :found.is(TCBlocks.TRACK_STATION.get())?Dev51BWestConstructR30l.Cell.STATION_EW
                :Dev51BWestConstructR30l.Cell.NORMAL_EW;
    if(!level.getFluidState(p).isEmpty()||!level.getFluidState(p.below()).isEmpty())
        return Dev51BWestConstructR30l.Cell.FLUID;
    if(!found.isAir())return Dev51BWestConstructR30l.Cell.OBSTRUCTED;
    if(level.getBlockEntity(p)!=null)return Dev51BWestConstructR30l.Cell.BLOCK_ENTITY;
    if(!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))
        return Dev51BWestConstructR30l.Cell.BAD_SUPPORT;
    for(int h=1;h<=4;h++){
        BlockPos above=p.above(h);
        if(!level.getFluidState(above).isEmpty())return Dev51BWestConstructR30l.Cell.FLUID;
        if(!level.getBlockState(above).isAir())return Dev51BWestConstructR30l.Cell.BAD_HEADROOM;
    }
    for(int h=0;h<=4;h++)for(int dz=-1;dz<=1;dz+=2){
        BlockPos flank=p.offset(0,h,dz);
        if(!level.getFluidState(flank).isEmpty())return Dev51BWestConstructR30l.Cell.FLUID;
        if(!level.getBlockState(flank).isAir())return Dev51BWestConstructR30l.Cell.BAD_FLANK;
    }
    if(!level.getEntitiesOfClass(Entity.class,
        new AABB(p.getX()-0.5,p.getY()-1,p.getZ()-1.5,p.getX()+1.5,p.getY()+5,p.getZ()+2.5),
        e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty())
        return Dev51BWestConstructR30l.Cell.ENTITY;
    return Dev51BWestConstructR30l.Cell.CLEAR;
}

// DEV51B R30k: read-only physical STOPPER site engineering.
// All 25 rails and the 54 station reserve cells were just observed by the R30j path.
// NO STOPPER/rail placement, SavedData changes, movement, or dispatch.
private static Dev51BWestTerminalR30k.Site dev51bR30kObserveTerminalCell(ServerLevel level,BlockPos p) {
    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
        if(!level.hasChunkAt(p.offset(dx,0,dz)))
            return Dev51BWestTerminalR30k.Site.UNLOADED;
    for(int h=0;h<=4;h++) {
        BlockPos upper=p.above(h);
        if(!level.hasChunkAt(upper))return Dev51BWestTerminalR30k.Site.UNLOADED;
    }
    if(!level.getFluidState(p).isEmpty()||!level.getFluidState(p.below()).isEmpty())
        return Dev51BWestTerminalR30k.Site.FLUID;
    if(!level.getBlockState(p).isAir())return Dev51BWestTerminalR30k.Site.BLOCKED;
    if(!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))
        return Dev51BWestTerminalR30k.Site.SUPPORT_MISSING;
    for(int h=1;h<=4;h++) {
        BlockPos above=p.above(h);
        if(!level.getFluidState(above).isEmpty())return Dev51BWestTerminalR30k.Site.FLUID;
        if(!level.getBlockState(above).isAir())return Dev51BWestTerminalR30k.Site.HEADROOM_BLOCKED;
    }
    for(int dz=-1;dz<=1;dz+=2)for(int h=0;h<=4;h++) {
        BlockPos flank=p.offset(0,h,dz);
        if(!level.getFluidState(flank).isEmpty())return Dev51BWestTerminalR30k.Site.FLUID;
        if(!level.getBlockState(flank).isAir())return Dev51BWestTerminalR30k.Site.FLANK_BLOCKED;
    }
    if(level.getBlockEntity(p)!=null)return Dev51BWestTerminalR30k.Site.BLOCK_ENTITY;
    if(!level.getEntitiesOfClass(Entity.class,
            new AABB(p.getX()-0.5,p.getY()-1,p.getZ()-1.5,
                     p.getX()+1.5,p.getY()+5,p.getZ()+2.5),
            e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty())
        return Dev51BWestTerminalR30k.Site.ENTITY;
    return Dev51BWestTerminalR30k.Site.AIR_SUPPORTED_CLEAR;
}
private static void dev51bR30kTerminalSurvey(CommandSourceStack src,BlockPos source,
        Dev51BWestPreflightR30j.Result west, String witness) {
    ServerLevel level=src.getLevel();
    // X36 = immediately west of the proposed last west rail X37.
    // X35/X34 reserved ONLY for possible future mainline continuation.
    BlockPos buffer=source.west(25);
    BlockPos continuation1=source.west(26);
    BlockPos continuation2=source.west(27);
    var atBuffer=dev51bR30kObserveTerminalCell(level,buffer);
    var atNext=dev51bR30kObserveTerminalCell(level,continuation1);
    var atFar=dev51bR30kObserveTerminalCell(level,continuation2);
    BlockState block=TCBlocks.STOPPER.get().defaultBlockState();
    boolean registryValid=block.getBlock() instanceof TrainBufferBlock
            &&block.hasProperty(HorizontalDirectionalBlock.FACING)
            &&block.hasProperty(TrainBufferBlock.ENERGY);
    boolean facingEast=registryValid
            &&block.setValue(HorizontalDirectionalBlock.FACING,Direction.EAST)
                    .getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST;
    var safety=Dev51BWestTerminalR30k.inspect(west.persistedSource(),witness,
            west.planSha256(),west.verdict(),registryValid,facingEast,atBuffer,atNext,atFar);
    LOG.info("[TC-DEV51B-R30K] BUFFER_CELL pos={} state={} proposedBlock=traincraft:stopper facing=EAST energy=false noPlacement=true",buffer,atBuffer);
    LOG.info("[TC-DEV51B-R30K] CONTINUATION_CELL pos={} state={} futureRailOnly=true noPlacement=true",continuation1,atNext);
    LOG.info("[TC-DEV51B-R30K] CONTINUATION_CELL pos={} state={} futureRailOnly=true noPlacement=true",continuation2,atFar);
    LOG.info("[TC-DEV51B-R30K] WEST_TERMINAL_ENGINEERING sourcePersisted={} sourceSha256={} planSha256={} proposedBuffer={} registryValid={} facesEast={} bufferSite={} continuation1={} continuation2={} candidateClear={} futureExtensionClear={} westStopInstalled=false collisionCertified=false brakingTested=false status={} blocksChanged=0 savedDataWrites=0 entitiesMoved=0 trainDrive=false automaticDispatch=false mayPlace=false mayDrive=false mayDispatch=false",
            safety.persistedSource(),witness,west.planSha256(),buffer,safety.bufferBlockRegistryValid(),
            safety.eastFacing(),safety.buffer(),safety.continuation1(),safety.continuation2(),
            safety.candidateClear(),safety.westwardExtensionReserved(),safety.status());
    src.sendSuccess(()->Component.literal("[R30k] Proposed buffer="+buffer.toShortString()
            +" faces EAST. Site="+safety.buffer()+" future west="+safety.westwardExtensionReserved()),false);
    src.sendSuccess(()->Component.literal("[R30k] "+safety.status()+"; PREVIEW ONLY. No buffer/rail/drive/dispatch."),false);
}

// DEV51B R30e: exact WEST railway rail-placement PLAN, READ ONLY.
// Never places rails or stations, calls setDirty, saves route data, or moves trains.
// IMPORTANT: no command in this section authorizes world edits or dispatch.
private static int dev51bR30eWestConstructionPreview(CommandSourceStack src) {
    return dev51bR30eWestConstructionPreview(src,false);
}

private static int dev51bR30eWestConstructionPreview(CommandSourceStack src,boolean r30j) {
    return dev51bR30eWestConstructionPreview(src,r30j,false);
}
private static int dev51bR30eWestConstructionPreview(CommandSourceStack src,boolean r30j,boolean r30k) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30e] HOLD DISPOSABLE_WORLD_ONLY. No changes."));return 0;
    }
    StationData saved=stations(level);
    BlockPos player=BlockPos.containing(src.getPosition());
    BlockPos source=saved.closest(player,48).orElse(null);
    if(source!=null && saved.isR30TestStop(source))source=null;
    if(source==null)for(BlockPos test:saved.placed)
        if(!saved.isR30TestStop(test)&&test.distSqr(player)<=48L*48L) {
            source=test;break;
        }
    // Do not transfer this fixed-coordinate, axis-specific plan to another station.
    if(source==null||source.getX()!=61||source.getY()!=-60||source.getZ()!=-3
            ||!saved.placed.contains(source)) {
        src.sendFailure(Component.literal("[R30e] HOLD EXPECTED_ORIGINAL_STATION_61_-60_-3_WITHIN_48. No changes."));return 0;
    }
    boolean railRegistryReady=TCBlocks.TRACK_NORMAL.get().defaultBlockState().hasProperty(RailBlock.SHAPE)
            &&TCBlocks.TRACK_STATION.get().defaultBlockState().hasProperty(RailBlock.SHAPE);
    String normalId=BuiltInRegistries.BLOCK.getKey(TCBlocks.TRACK_NORMAL.get()).toString();
    String stationId=BuiltInRegistries.BLOCK.getKey(TCBlocks.TRACK_STATION.get()).toString();
    java.util.List<Dev51BWestConstructionPlanR30e.Rail> route=new java.util.ArrayList<>();
    java.util.List<Dev51BWestConstructionPlanR30e.Site> sites=new java.util.ArrayList<>();
    for(int off=0;off<=Dev51BWestConstructionPlanR30e.LAST_NEW;off++) {
        BlockPos at=source.west(off);
        Dev51BWestConstructionPlanR30e.RailStatus status;
        String observed="UNLOADED",foundation="UNLOADED",entityTypeIds="[]";
        boolean loaded=at.getY()-1>=level.getMinBuildHeight()
                &&at.getY()+4<level.getMaxBuildHeight();
        for(int dz=-1;dz<=1&&loaded;dz++) {
            BlockPos flank=at.offset(0,0,dz);
            if(!level.hasChunkAt(flank)||!level.hasChunkAt(flank.below())
                    ||!level.hasChunkAt(flank.above(4)))loaded=false;
        }
        if(!loaded)status=Dev51BWestConstructionPlanR30e.RailStatus.UNLOADED;
        else {
            BlockState state=level.getBlockState(at);
            observed=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            BlockState below=level.getBlockState(at.below());
            foundation=BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString();
            boolean supported=below.isFaceSturdy(level,at.below(),Direction.UP);
            boolean fluids=!level.getFluidState(at.below()).isEmpty();
            boolean headroom=true,flankClear=true;
            for(int h=0;h<=4;h++) {
                BlockPos above=at.above(h);
                if(!level.getFluidState(above).isEmpty())fluids=true;
                if(h>0&&!level.getBlockState(above).isAir())headroom=false;
                if(off>Dev51BWestConstructionPlanR30e.ORIGINAL_END)
                    for(int dz=-1;dz<=1;dz+=2) {
                        BlockPos side=at.offset(0,h,dz);
                        if(!level.getBlockState(side).isAir()||!level.getFluidState(side).isEmpty())
                            flankClear=false;
                    }
            }
            java.util.List<String> occupancy=off<=Dev51BWestConstructionPlanR30e.ORIGINAL_END
                    ?java.util.List.of():level.getEntitiesOfClass(Entity.class,
                        new AABB(at.getX()-1.5,at.getY()-1.0,at.getZ()-1.5,
                                 at.getX()+2.5,at.getY()+5.0,at.getZ()+2.5),
                        e->!e.isRemoved()&&!(e instanceof ServerPlayer)).stream()
                        .map(e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                        .distinct().sorted().toList();
            entityTypeIds=occupancy.toString();
            if(off<=Dev51BWestConstructionPlanR30e.ORIGINAL_END) {
                Dev51BRailSurveyR30a.Kind rail=dev51bR30aObserveRail(level,at).kind();
                boolean correct=(rail==Dev51BRailSurveyR30a.Kind.NORMAL_EW
                                ||rail==Dev51BRailSurveyR30a.Kind.STATION_EW)
                        &&state.hasProperty(RailBlock.SHAPE)
                        &&state.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
                status=correct&&headroom&&!fluids&&supported
                        ?Dev51BWestConstructionPlanR30e.RailStatus.EXISTING_EW
                        :Dev51BWestConstructionPlanR30e.RailStatus.ORIGINAL_RAIL_MISMATCH;
            } else if(!occupancy.isEmpty())status=Dev51BWestConstructionPlanR30e.RailStatus.ENTITY;
            else if(level.getBlockEntity(at)!=null)status=Dev51BWestConstructionPlanR30e.RailStatus.BLOCK_ENTITY;
            else if(fluids)status=Dev51BWestConstructionPlanR30e.RailStatus.FLUID;
            else if(!state.isAir())status=Dev51BWestConstructionPlanR30e.RailStatus.BLOCKED;
            else if(!headroom)status=Dev51BWestConstructionPlanR30e.RailStatus.HEADROOM_BLOCKED;
            else if(!flankClear)status=Dev51BWestConstructionPlanR30e.RailStatus.SIDE_BLOCKED;
            else if(!supported)status=Dev51BWestConstructionPlanR30e.RailStatus.SUPPORT_MISSING;
            else status=off==Dev51BWestConstructionPlanR30e.STATION_OFFSET
                    ?Dev51BWestConstructionPlanR30e.RailStatus.AIR_STATION_EW
                    :Dev51BWestConstructionPlanR30e.RailStatus.AIR_NORMAL_EW;
        }
        route.add(new Dev51BWestConstructionPlanR30e.Rail(off,status,observed,foundation,entityTypeIds));
        if(off>=Dev51BWestConstructionPlanR30e.FIRST_NEW) {
            String proposal=off==Dev51BWestConstructionPlanR30e.STATION_OFFSET?stationId:normalId;
            LOG.info("[TC-DEV51B-R30E] RAIL_EDIT_CANDIDATE offset={} pos={} observed={} below={} proposed={} proposedRailShape=EAST_WEST result={} entities={} worldEdits=0",
                    off,at,observed,foundation,proposal,status,entityTypeIds);
        }else if(status!=Dev51BWestConstructionPlanR30e.RailStatus.EXISTING_EW)
            LOG.info("[TC-DEV51B-R30E] SOURCE_RAIL_HOLD offset={} pos={} status={} center={} noWrites=true",
                    off,at,status,observed);
    }
    int destX=source.getX()-Dev51BWestConstructionPlanR30e.STATION_OFFSET;
    // Station SITE RESERVATION (not a built station blueprint): 9x7 around X=41.
    // All 54 side cells observed. No terrain grading, platforms, roofs or entrances planned.
    for(int along=-4;along<=4;along++)for(int lateral=-3;lateral<=3;lateral++) {
        if(lateral==0)continue;
        BlockPos at=new BlockPos(destX+along,source.getY(),source.getZ()+lateral);
        Dev51BWestConstructionPlanR30e.SiteStatus status;
        String observed="UNLOADED";
        if(at.getY()-1<level.getMinBuildHeight()||at.getY()+4>=level.getMaxBuildHeight()
                ||!level.hasChunkAt(at)||!level.hasChunkAt(at.below())||!level.hasChunkAt(at.above(4)))
            status=Dev51BWestConstructionPlanR30e.SiteStatus.UNLOADED;
        else {
            BlockState state=level.getBlockState(at);
            observed=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean empty=true,dry=level.getFluidState(at.below()).isEmpty();
            for(int h=0;h<=4;h++) {
                if(!level.getBlockState(at.above(h)).isAir())empty=false;
                if(!level.getFluidState(at.above(h)).isEmpty())dry=false;
            }
            boolean entityFree=level.getEntitiesOfClass(Entity.class,
                new AABB(at.getX(),at.getY()-1,at.getZ(),at.getX()+1,at.getY()+5,at.getZ()+1),
                e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty();
            if(!entityFree)status=Dev51BWestConstructionPlanR30e.SiteStatus.ENTITY;
            else if(level.getBlockEntity(at)!=null)status=Dev51BWestConstructionPlanR30e.SiteStatus.BLOCK_ENTITY;
            else if(!dry)status=Dev51BWestConstructionPlanR30e.SiteStatus.FLUID;
            else if(!empty)status=Dev51BWestConstructionPlanR30e.SiteStatus.BLOCKED;
            else if(!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP))
                status=Dev51BWestConstructionPlanR30e.SiteStatus.SUPPORT_MISSING;
            else status=Dev51BWestConstructionPlanR30e.SiteStatus.CLEAR_SUPPORTED;
        }
        sites.add(new Dev51BWestConstructionPlanR30e.Site(along,lateral,status,observed));
        if(status!=Dev51BWestConstructionPlanR30e.SiteStatus.CLEAR_SUPPORTED)
            LOG.info("[TC-DEV51B-R30E] STATION_RESERVE_HOLD pos={} status={} block={} noWrites=true",at,status,observed);
    }
    // Complete 13x15 villager scan, guarded against unloaded chunks.
    boolean villagerAreaLoaded=source.getY()-2>=level.getMinBuildHeight()
            &&source.getY()+6<level.getMaxBuildHeight();
    for(int xx=destX-6;xx<=destX+6&&villagerAreaLoaded;xx++)
        for(int zz=source.getZ()-7;zz<=source.getZ()+7;zz++)
            if(!level.hasChunkAt(new BlockPos(xx,source.getY(),zz))) {villagerAreaLoaded=false;break;}
    int villagers=villagerAreaLoaded?level.getEntitiesOfClass(Villager.class,
            new AABB(destX-6,source.getY()-2,source.getZ()-7,
                     destX+7,source.getY()+6,source.getZ()+8),v->!v.isRemoved()).size():-1;
    int otherStations=0;
    for(BlockPos p:saved.placed)if(!p.equals(source)
            &&Math.abs(p.getX()-destX)<=12&&Math.abs(p.getZ()-source.getZ())<=9
            &&Math.abs(p.getY()-source.getY())<=6)otherStations++;
    BlockPos end=source.west(25);
    Dev51BWestConstructionPlanR30e.WestEnd endKind;
    if(!level.hasChunkAt(end))endKind=Dev51BWestConstructionPlanR30e.WestEnd.UNLOADED;
    else if(level.getBlockState(end).isAir())endKind=Dev51BWestConstructionPlanR30e.WestEnd.OPEN_AIR;
    else if(dev51bR30aObserveRail(level,end).kind()==Dev51BRailSurveyR30a.Kind.NORMAL_EW)
        endKind=Dev51BWestConstructionPlanR30e.WestEnd.EXISTING_EW_RAIL;
    else endKind=Dev51BWestConstructionPlanR30e.WestEnd.BLOCKED;
    var report=Dev51BWestConstructionPlanR30e.evaluate(route,sites,villagers,otherStations,
            saved.hasNetwork(source),railRegistryReady,endKind);
    LOG.info("[TC-DEV51B-R30E] WEST_CONSTRUCTION_PLAN source={} station={},-60,-3 requiredRailEdits={} normal={} stationRail={} sourceRails={} routeHolds={} reserveClear={} reserveHolds={} villagers={} otherStations={} sourceNetworkRecorded={} terminal={} siteIsArchitecturalBlueprint=false status={} fingerprint={} eastTownTreePreserved=true blocksChanged=0 entitiesMoved=0 trainDrive=false automaticDispatch=false buildApproved=false connectedRouteCertified=false",
            source,destX,Dev51BWestConstructionPlanR30e.NEW_COUNT,report.normalRail(),
            report.stationRail(),report.originalRail(),report.routeHolds(),report.clearSide(),
            report.sideHolds(),villagers,otherStations,report.networkRecorded(),report.westEnd(),
            report.verdict(),report.fingerprint());
    if(r30j) {
        // The persisted R30i source record passed just before this survey.
        // R30e legacy hasNetwork() remains false and must NOT be toggled.
        Dev51BWestPreflightR30j.Result safety=Dev51BWestPreflightR30j.inspect(
                true,report.originalRail(),report.normalRail(),report.stationRail(),
                report.routeHolds(),report.clearSide(),report.sideHolds(),villagers,
                otherStations,report.westEnd().name(),report.fingerprint());
        LOG.info("[TC-DEV51B-R30J] WEST_CONSTRUCTION_PREFLIGHT persistedR30i={} original={} normal={} stationRails={} proposed=16 station={} reserveClear={} reserveHolds={} villagers={} otherStations={} westEnd={} sourceLegacyNetwork={} planSha256={} status={} blocksChanged=0 savedDataWrites=0 entitiesMoved=0 trainDrive=false automaticDispatch=false buildAllowed=false routeCertified=false",
                safety.persistedSource(),safety.existingRails(),safety.normalRails(),
                safety.stationRails(),new BlockPos(destX,source.getY(),source.getZ()),
                safety.clearSide(),safety.sideHolds(),villagers,otherStations,
                safety.westEnd(),saved.hasNetwork(source),report.fingerprint(),safety.verdict());
        if(r30k)dev51bR30kTerminalSurvey(src,source,safety,
                Dev51BWestTerminalR30k.EXPECTED_SOURCE);
        src.sendSuccess(()->Component.literal("[R30j] Persisted source verified; west x=37..52 normal="
                +safety.normalRails()+" station="+safety.stationRails()+" @41,-60,-3."),false);
        src.sendSuccess(()->Component.literal("[R30j] 9x7 station reservation only; west terminal="
                +safety.westEnd()+". "+safety.verdict()),false);
        src.sendSuccess(()->Component.literal("[R30j] PREVIEW ONLY. 0 rails built, no buffer/drive/dispatch. East tree untouched."),false);
        return 1;
    }
    final String message="[R30e] WEST PLAN x=37..52: "+report.normalRail()+" normal + "
            +report.stationRail()+" station rail @41,-60,-3; hold="+report.verdict();
    src.sendSuccess(()->Component.literal(message),false);
    src.sendSuccess(()->Component.literal("[R30e] Station site 9x7 reserved only (NOT architectural plan). West end="+report.westEnd()+"; plan SHA256="+report.fingerprint().substring(0,16)),false);
    src.sendSuccess(()->Component.literal("[R30e] REVIEW ONLY / NO BLOCK EDITS / NO DRIVE / NO DISPATCH. Logs show 16 exact rail cells."),false);
    return 1;
}

// DEV51B R30d: READ-ONLY exact WEST corridor engineering debug.
// The 16 proposed rail cells and 54 lateral station cells are observations,
// never a permit to build, change network records, dispatch, or move trains.
private static int dev51bR30dWestDebug(CommandSourceStack src) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30d] HOLD DISPOSABLE_WORLD_ONLY. NO CHANGES."));
        return 0;
    }
    StationData saved=stations(level);
    BlockPos player=BlockPos.containing(src.getPosition());
    BlockPos source=saved.closest(player,48).orElse(null);
    if(source!=null && saved.isR30TestStop(source)) source=null;
    if(source==null)for(BlockPos candidate:saved.placed) {
        if(!saved.isR30TestStop(candidate) && candidate.distSqr(player)<=48L*48L) {
            source=candidate;break;
        }
    }
    if(source==null) {
        src.sendFailure(Component.literal("[R30d] HOLD NO_SOURCE_STATION_WITHIN_48. NO CHANGES."));
        return 0;
    }
    // R30c established this as a strictly EAST/WEST horizontal trial rail.
    // No diagonal, corner, slope, switch or legacy template is interpreted.
    int candidateX=source.getX()-Dev51BWestSiteDebugR30d.STATION_OFFSET;
    java.util.List<Dev51BWestSiteDebugR30d.RouteCell> railCells=new java.util.ArrayList<>();
    java.util.List<Dev51BWestSiteDebugR30d.SideCell> sides=new java.util.ArrayList<>();
    for(int off=0;off<=Dev51BWestSiteDebugR30d.WEST_END_OFFSET;off++) {
        BlockPos at=source.west(off);
        Dev51BWestSiteDebugR30d.RouteKind kind;
        String observed="UNLOADED";
        if(at.getY()-1<level.getMinBuildHeight()||at.getY()+4>=level.getMaxBuildHeight()
                ||!level.hasChunkAt(at)||!level.hasChunkAt(at.above(4))
                ||!level.hasChunkAt(at.below())
                ||!level.hasChunkAt(at.north())||!level.hasChunkAt(at.south())) {
            kind=Dev51BWestSiteDebugR30d.RouteKind.UNLOADED;
        } else {
            BlockState center=level.getBlockState(at);
            observed=BuiltInRegistries.BLOCK.getKey(center.getBlock()).toString();
            Dev51BRailSurveyR30a.Kind rail=dev51bR30aObserveRail(level,at).kind();
            boolean support=level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP);
            boolean dry=true;
            boolean ceilingClear=true;
            for(int height=-1;height<=4;height++) {
                if(!level.getFluidState(at.above(height)).isEmpty())dry=false;
                if(height>0 && !level.getBlockState(at.above(height)).isAir())ceilingClear=false;
            }
            boolean widthClear=true;
            // A conservative 3-block lateral and 5-block high sweep, including
            // flanking block columns. No bricks/vegetation are cleared.
            if(off>Dev51BWestSiteDebugR30d.ORIGINAL_RAIL_END) {
                for(int lat=-1;lat<=1;lat+=2)for(int height=0;height<=4;height++) {
                    BlockPos flank=at.offset(0,height,lat);
                    if(!level.getBlockState(flank).isAir()
                            ||!level.getFluidState(flank).isEmpty())widthClear=false;
                }
            }
            boolean entityClear=off<=Dev51BWestSiteDebugR30d.ORIGINAL_RAIL_END
                    ||level.getEntitiesOfClass(Entity.class,
                        new AABB(at.getX()-1.5,at.getY()-1.0,at.getZ()-1.5,
                                 at.getX()+2.5,at.getY()+5.0,at.getZ()+2.5),
                        e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty();
            if(off<=Dev51BWestSiteDebugR30d.ORIGINAL_RAIL_END) {
                kind=(rail==Dev51BRailSurveyR30a.Kind.NORMAL_EW
                            ||rail==Dev51BRailSurveyR30a.Kind.STATION_EW)
                            &&dry&&ceilingClear
                        ?Dev51BWestSiteDebugR30d.RouteKind.EXISTING_EW_RAIL
                        :Dev51BWestSiteDebugR30d.RouteKind.BAD_ORIGINAL_RAIL;
            } else if(!entityClear)kind=Dev51BWestSiteDebugR30d.RouteKind.ENTITY_IN_SWEEP;
            else if(!dry)kind=Dev51BWestSiteDebugR30d.RouteKind.FLUID;
            else if(rail!=Dev51BRailSurveyR30a.Kind.AIR)
                kind=Dev51BWestSiteDebugR30d.RouteKind.BLOCKED_TRACK_CELL;
            else if(!ceilingClear)kind=Dev51BWestSiteDebugR30d.RouteKind.HEADROOM_BLOCKED;
            else if(!widthClear)kind=Dev51BWestSiteDebugR30d.RouteKind.SIDE_CLEARANCE_BLOCKED;
            else if(!support)kind=Dev51BWestSiteDebugR30d.RouteKind.FOUNDATION_MISSING;
            else kind=off==Dev51BWestSiteDebugR30d.STATION_OFFSET
                ?Dev51BWestSiteDebugR30d.RouteKind.PLACE_STATION_EW_CANDIDATE
                :Dev51BWestSiteDebugR30d.RouteKind.PLACE_NORMAL_EW_CANDIDATE;
        }
        railCells.add(new Dev51BWestSiteDebugR30d.RouteCell(off,kind,observed));
        if(off>=Dev51BWestSiteDebugR30d.FIRST_NEW_RAIL) {
            String below=level.hasChunkAt(at.below())
                    ?BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.below()).getBlock()).toString():"UNLOADED";
            String aboveOne=level.hasChunkAt(at.above())
                    ?BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.above()).getBlock()).toString():"UNLOADED";
            String aboveTwo=level.hasChunkAt(at.above(2))
                    ?BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.above(2)).getBlock()).toString():"UNLOADED";
            java.util.List<String> entityTypes=level.hasChunkAt(at)
                    ?level.getEntitiesOfClass(Entity.class,
                        new AABB(at.getX()-1.5,at.getY()-1.0,at.getZ()-1.5,
                                 at.getX()+2.5,at.getY()+5.0,at.getZ()+2.5),
                        e->!e.isRemoved()&&!(e instanceof ServerPlayer)).stream()
                        .map(e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                        .distinct().sorted().toList():java.util.List.of();
            LOG.info("[TC-DEV51B-R30D] RAIL offset={} pos={} survey={} center={} below={} above1={} above2={} nearbyEntityTypes={} noWrites=true",
                    off,at,kind,observed,below,aboveOne,aboveTwo,entityTypes);
        } else if(kind!=Dev51BWestSiteDebugR30d.RouteKind.EXISTING_EW_RAIL) {
            LOG.info("[TC-DEV51B-R30D] APPROACH_HOLD offset={} pos={} survey={} block={}",
                    off,at,kind,observed);
        }
    }
    boolean outside=source.getY()-1<level.getMinBuildHeight()
            ||source.getY()+4>=level.getMaxBuildHeight();
    for(int along=-4;along<=4;along++)for(int lateral=-3;lateral<=3;lateral++) {
        if(lateral==0)continue;
        BlockPos at=new BlockPos(candidateX+along,source.getY(),source.getZ()+lateral);
        Dev51BWestSiteDebugR30d.SideKind kind;
        String id="UNLOADED";
        if(outside||!level.hasChunkAt(at)||!level.hasChunkAt(at.above(4))
                ||!level.hasChunkAt(at.below())) {
            kind=Dev51BWestSiteDebugR30d.SideKind.UNLOADED;
        } else {
            BlockState center=level.getBlockState(at);
            id=BuiltInRegistries.BLOCK.getKey(center.getBlock()).toString();
            boolean dry=true,empty=true;
            for(int h=0;h<=4;h++) {
                if(!level.getBlockState(at.above(h)).isAir())empty=false;
                if(!level.getFluidState(at.above(h)).isEmpty())dry=false;
            }
            if(!level.getFluidState(at.below()).isEmpty())dry=false;
            boolean entClear=level.getEntitiesOfClass(Entity.class,
                new AABB(at.getX(),at.getY()-1.0,at.getZ(),
                         at.getX()+1.0,at.getY()+5.0,at.getZ()+1.0),
                e->!e.isRemoved()&&!(e instanceof ServerPlayer)).isEmpty();
            if(!entClear)kind=Dev51BWestSiteDebugR30d.SideKind.ENTITY;
            else if(!dry)kind=Dev51BWestSiteDebugR30d.SideKind.FLUID;
            else if(level.getBlockEntity(at)!=null)kind=Dev51BWestSiteDebugR30d.SideKind.BLOCK_ENTITY;
            else if(!empty)kind=Dev51BWestSiteDebugR30d.SideKind.BLOCKED;
            else if(!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP))
                kind=Dev51BWestSiteDebugR30d.SideKind.SUPPORT_MISSING;
            else kind=Dev51BWestSiteDebugR30d.SideKind.CLEAR_SUPPORTED;
        }
        sides.add(new Dev51BWestSiteDebugR30d.SideCell(along,lateral,kind,id));
        if(kind!=Dev51BWestSiteDebugR30d.SideKind.CLEAR_SUPPORTED)
            LOG.info("[TC-DEV51B-R30D] FOOTPRINT_HOLD pos={} reason={} observed={} noWrites=true",at,kind,id);
    }
    // Check the 13x15 villager envelope without loading any chunks.
    int villagers=-1;
    BlockPos min=new BlockPos(candidateX-6,source.getY()-2,source.getZ()-7);
    BlockPos max=new BlockPos(candidateX+6,source.getY()+5,source.getZ()+7);
    if(!outside && level.hasChunkAt(min)&&level.hasChunkAt(max)
            &&level.hasChunkAt(new BlockPos(min.getX(),min.getY(),max.getZ()))
            &&level.hasChunkAt(new BlockPos(max.getX(),min.getY(),min.getZ())))
        villagers=level.getEntitiesOfClass(Villager.class,
                new AABB(candidateX-6,source.getY()-2,source.getZ()-7,
                         candidateX+7,source.getY()+6,source.getZ()+8),
                v->!v.isRemoved()).size();
    int nearbyStations=0;
    for(BlockPos station:saved.placed)if(!station.equals(source)
            &&Math.abs(station.getX()-candidateX)<=12
            &&Math.abs(station.getZ()-source.getZ())<=9
            &&Math.abs(station.getY()-source.getY())<=6)nearbyStations++;
    var report=Dev51BWestSiteDebugR30d.evaluate(railCells,sides,
            Math.max(0,villagers),nearbyStations);
    String status=villagers<0?"HOLD_UNLOADED_VILLAGER_ENVELOPE":report.result();
    LOG.info("[TC-DEV51B-R30D] TOWN_EAST_PRESERVED eastTreeLoggedByR30c=75,-60,-3 protectedDirection=EAST surveyDirection=WEST noWrites=true");
    LOG.info("[TC-DEV51B-R30D] WEST_DEBUG source={} station={},{},{} railConstructionX={}..{} originalRail={} normalCandidates={} stationCandidates={} routeIssues={} sideClear={} sideIssues={} nearbyVillagers={} protectedStations={} villagerScanLoaded={} networkRecorded={} status={} blockEdits=0 entitiesMoved=0 chunksForced=0 writeApproved=false driveApproved=false connectedRouteCertified=false",
            source,candidateX,source.getY(),source.getZ(),source.getX()-24,source.getX()-9,
            report.originalRail(),report.plannedNormal(),report.plannedStation(),report.routeIssues(),
            report.footprintClear(),report.footprintIssues(),villagers,nearbyStations,
            villagers>=0,saved.hasNetwork(source),status);
    final String summary="[R30d] WEST "+candidateX+","+source.getY()+","+source.getZ()
            +" rails x="+(source.getX()-24)+".."+(source.getX()-9)
            +" normal="+report.plannedNormal()+" station="+report.plannedStation();
    final String hold="[R30d] "+status+" routeIssues="+report.routeIssues()
            +" sideIssues="+report.footprintIssues()+" villagers="+villagers
            +" protectedStations="+nearbyStations;
    src.sendSuccess(()->Component.literal(summary),false);
    src.sendSuccess(()->Component.literal(hold),false);
    src.sendSuccess(()->Component.literal("[R30d] READ-ONLY: eastern town/tree protected. No construction/drive/dispatch; logs have every proposed cell."),false);
    return 1;
}

// DEV51B R30c: a bounded READ-ONLY alternative-station comparison and
// exact per-cell engineering diagnosis. Never constructs/clears world blocks,
// never commands train movement, and never persists proposed sites.
private static int dev51bR30cAlternatives(CommandSourceStack src) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30c] HOLD: Requires marked disposable DEV51B R27 Movement Copy Overworld. NO CHANGES."));
        return 0;
    }
    StationData saved=stations(level);
    BlockPos player=BlockPos.containing(src.getPosition());
    BlockPos source=saved.closest(player,48).orElse(null);
    if(source!=null && saved.isR30TestStop(source))source=null;
    if(source==null) {
        for(BlockPos candidate:saved.placed) {
            if(!saved.isR30TestStop(candidate) && candidate.distSqr(player)<=48L*48L) {
                source=candidate;break;
            }
        }
    }
    if(source==null) {
        src.sendFailure(Component.literal("[R30c] HOLD NO_RECORDED_SOURCE_WITHIN_48. NO CHANGES."));
        return 0;
    }
    java.util.Map<Dev51BAlternativeSitesR30c.Direction,java.util.Map<Integer,Dev51BAlternativeSitesR30c.Site>> sites
        =new java.util.EnumMap<>(Dev51BAlternativeSitesR30c.Direction.class);
    java.util.List<Dev51BAlternativeSitesR30c.Cell> east=new java.util.ArrayList<>();
    java.util.List<Dev51BAlternativeSitesR30c.Cell> west=new java.util.ArrayList<>();
    int diagnosed=0;
    for(Dev51BAlternativeSitesR30c.Direction side:Dev51BAlternativeSitesR30c.Direction.values()) {
        java.util.List<Dev51BAlternativeSitesR30c.Cell> observed
                =side==Dev51BAlternativeSitesR30c.Direction.EAST?east:west;
        for(int offset=0;offset<=Dev51BAlternativeSitesR30c.LAST_OFFSET;offset++) {
            BlockPos at=source.offset(side.sign*offset,0,0);
            Dev51BRailSurveyR30a.Cell rail=dev51bR30aObserveRail(level,at);
            if(rail.kind()==Dev51BRailSurveyR30a.Kind.UNLOADED) {
                observed.add(new Dev51BAlternativeSitesR30c.Cell(Dev51BEastCorridorR30b.Finding.UNLOADED));
                if(offset>=9)LOG.info("[TC-DEV51B-R30C] BLOCKER side={} offset={} pos={} reason=UNLOADED noForcedChunk=true",side,offset,at);
                continue;
            }
            // Identical centerline tests to R30b. Parked R27d/R28 train
            // entities are excluded from EXISTING approach cells only.
            boolean dry=level.getFluidState(at).isEmpty()
                    && level.getFluidState(at.above()).isEmpty()
                    && level.getFluidState(at.above(2)).isEmpty()
                    && level.getFluidState(at.below()).isEmpty();
            boolean sturdy=level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP);
            java.util.List<Entity> occupying=offset<=Dev51BAlternativeSitesR30c.EXISTING_LAST
                    ?java.util.List.of()
                    :level.getEntitiesOfClass(Entity.class,
                        new AABB(at.getX()-0.35,at.getY()-1.0,at.getZ()-0.8,
                                 at.getX()+1.35,at.getY()+3.0,at.getZ()+1.8),
                        ent->!ent.isRemoved() && !(ent instanceof ServerPlayer));
            var finding=Dev51BEastCorridorR30b.classify(new Dev51BEastCorridorR30b.Cell(
                    rail.kind(),rail.clearTwoAbove(),sturdy,dry,occupying.isEmpty()));
            observed.add(new Dev51BAlternativeSitesR30c.Cell(finding));
            if(offset>=9 && finding!=Dev51BEastCorridorR30b.Finding.EMPTY_WITH_SUPPORT
                    && finding!=Dev51BEastCorridorR30b.Finding.EXISTING_RAIL) {
                String entityTypes=occupying.stream().limit(4)
                    .map(e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                    .collect(java.util.stream.Collectors.joining(","));
                LOG.info("[TC-DEV51B-R30C] BLOCKER side={} offset={} pos={} reason={} block={} below={} above={} above2={} entities={} totalEntities={} support={} dry={}  noWrites=true",
                        side,offset,at,finding,
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()),
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.below()).getBlock()),
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.above()).getBlock()),
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.above(2)).getBlock()),
                        entityTypes,occupying.size(),sturdy,dry);
                diagnosed++;
            }
        }
        java.util.Map<Integer,Dev51BAlternativeSitesR30c.Site> byCenter=new java.util.LinkedHashMap<>();
        for(int center:Dev51BAlternativeSitesR30c.CENTERS) {
            int x=source.getX()+side.sign*center;
            int sideOccupied=0;
            boolean unloaded=false;
            boolean outOfBounds=source.getY()-1<level.getMinBuildHeight()
                    ||source.getY()+2>=level.getMaxBuildHeight();
            // Site-only side clearance: 9 longitudinal x 7 lateral cells,
            // the center rail lane is excluded. No chunk loads requested.
            for(int along=-4;along<=4;along++) for(int lateral=-3;lateral<=3;lateral++) {
                if(lateral==0)continue;
                BlockPos test=new BlockPos(x+along,source.getY(),source.getZ()+lateral);
                if(outOfBounds || !level.hasChunkAt(test) || !level.hasChunkAt(test.above(2))
                        || !level.hasChunkAt(test.below())) {
                    unloaded=true;continue;
                }
                if(!level.getBlockState(test).isAir() || !level.getBlockState(test.above()).isAir()
                        || !level.getBlockState(test.above(2)).isAir()
                        || !level.getFluidState(test).isEmpty())sideOccupied++;
            }
            int villagers=0;
            // Entity search is allowed only when the WHOLE bounded area is
            // already loaded; an unloaded area remains an explicit HOLD.
            if(!unloaded && !outOfBounds) {
                BlockPos min=new BlockPos(x-6,source.getY(),source.getZ()-7);
                BlockPos max=new BlockPos(x+6,source.getY()+3,source.getZ()+7);
                if(!level.hasChunkAt(min)||!level.hasChunkAt(max)
                        || !level.hasChunkAt(new BlockPos(min.getX(),min.getY(),max.getZ()))
                        || !level.hasChunkAt(new BlockPos(max.getX(),min.getY(),min.getZ()))) unloaded=true;
                else villagers=level.getEntitiesOfClass(Villager.class,
                    new AABB(x-6,source.getY()-2,source.getZ()-7,
                             x+7,source.getY()+5,source.getZ()+8),
                    v->!v.isRemoved()).size();
            }
            Dev51BAlternativeSitesR30c.SiteRisk risk=outOfBounds
                    ?Dev51BAlternativeSitesR30c.SiteRisk.OUT_OF_BOUNDS
                    :unloaded?Dev51BAlternativeSitesR30c.SiteRisk.UNLOADED
                    :villagers>0?Dev51BAlternativeSitesR30c.SiteRisk.NEARBY_VILLAGERS
                    :sideOccupied>0?Dev51BAlternativeSitesR30c.SiteRisk.SIDE_OCCUPIED
                    :Dev51BAlternativeSitesR30c.SiteRisk.CLEAR_OBSERVED;
            byCenter.put(center,new Dev51BAlternativeSitesR30c.Site(risk,villagers,sideOccupied));
            LOG.info("[TC-DEV51B-R30C] SITE side={} pos={},{},{} centerOffset={} siteRisk={} nearbyVillagers={} occupiedSideCells={} sideCells=54 noWrites=true",
                    side,x,source.getY(),source.getZ(),center,risk,villagers,sideOccupied);
        }
        sites.put(side,byCenter);
    }
    var report=Dev51BAlternativeSitesR30c.evaluate(east,west,sites);
    LOG.info("[TC-DEV51B-R30C] ALTERNATIVE_SURVEY source={} world={} recordedNetwork={} observedEachDirection=45 blockersLogged={} candidates=6 autoDispatch=false blockEdits=0 entitiesMoved=0 chunksForced=0 writeApproved=false driveApproved=false connectedRouteCertified=false",
            source,level.dimension().location(),saved.hasNetwork(source),diagnosed);
    for(int i=0;i<report.ranked().size();i++) {
        var candidate=report.ranked().get(i);
        int cx=Dev51BAlternativeSitesR30c.worldX(source.getX(),candidate.direction(),candidate.centerOffset());
        String first=candidate.firstIssueOffset()<0?"NONE":
                    Dev51BAlternativeSitesR30c.worldX(source.getX(),candidate.direction(),candidate.firstIssueOffset())
                        +","+source.getY()+","+source.getZ()+":"+candidate.firstIssue();
        LOG.info("[TC-DEV51B-R30C] RANK rank={} side={} site={},{},{} status={} score={} existingRail={} supportedEmpty={} missingFoundation={} entities={} headroom={} occupied={} fluids={} unloaded={} firstIssue={} sideSiteRisk={} nearbyVillagers={} sideOccupied={} writesApproved=false",
                i+1,candidate.direction(),cx,source.getY(),source.getZ(),candidate.reviewStatus(),
                candidate.priorityScore(),candidate.existingRail(),candidate.emptySupported(),
                candidate.missingSupport(),candidate.entities(),candidate.overhead(),
                candidate.occupied(),candidate.fluids(),candidate.unloaded(),first,
                candidate.site().risk(),candidate.site().nearbyVillagers(),candidate.site().sideOccupiedCells());
        if(i<3) {
            final String display="[R30c] #"+(i+1)+" "+candidate.direction()+" site="+cx+","+source.getY()+","+source.getZ()
                    +" "+candidate.reviewStatus()+" score="+candidate.priorityScore()+" first="+first;
            src.sendSuccess(()->Component.literal(display),false);
        }
    }
    src.sendSuccess(()->Component.literal("[R30c] 6 candidates compared. Full obstruction and entity types in latest.log. ALL REVIEW ONLY."),false);
    src.sendSuccess(()->Component.literal("[R30c] NO WORLD EDITS / NO TRAIN DRIVE / NO AUTO DISPATCH. No connected route certified."),false);
    return 1;
}

// DEV51B R30b: read-only eastward corridor site feasibility. The measured
// R30a line ends after offset +8; this checks +9..+36 for a future nine-rail
// stopping area around +32. It NEVER constructs rails or authorizes driving.
private static int dev51bR30bCorridor(CommandSourceStack src) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30b] HOLD: Requires marked disposable DEV51B R27 Movement Copy Overworld. NO CHANGES."));
        return 0;
    }
    StationData saved=stations(level);
    BlockPos player=BlockPos.containing(src.getPosition());
    BlockPos source=saved.closest(player,48).orElse(null);
    if(source!=null && saved.isR30TestStop(source)) {
        source=null;
        for(BlockPos candidate:saved.placed) {
            if(!saved.isR30TestStop(candidate) && candidate.distSqr(player)<=48L*48L) {
                source=candidate;break;
            }
        }
    }
    if(source==null) {
        src.sendFailure(Component.literal("[R30b] HOLD NO_RECORDED_SOURCE_WITHIN_48. NO CHANGES."));
        return 0;
    }
    java.util.List<Dev51BEastCorridorR30b.Cell> cells=new java.util.ArrayList<>();
    for(int offset=0;offset<=Dev51BEastCorridorR30b.LAST_OFFSET;offset++) {
        BlockPos at=source.offset(offset,0,0);
        Dev51BRailSurveyR30a.Cell asBuilt=dev51bR30aObserveRail(level,at);
        if(asBuilt.kind()==Dev51BRailSurveyR30a.Kind.UNLOADED) {
            cells.add(new Dev51BEastCorridorR30b.Cell(asBuilt.kind(),false,false,false,false));
            continue;
        }
        // Read only already-loaded blocks. Do not ticket/unload any chunks.
        boolean dry=level.getFluidState(at).isEmpty()
                && level.getFluidState(at.above()).isEmpty()
                && level.getFluidState(at.above(2)).isEmpty()
                && level.getFluidState(at.below()).isEmpty();
        boolean sturdy=level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP);
        // Existing accepted staging can contain parked trains. R30b needs
        // entity clearance only on a possible NEW segment, offsets 9..36.
        boolean clearEntities=offset<Dev51BEastCorridorR30b.EXISTING_FIRST_EXTENSION
                || level.getEntitiesOfClass(Entity.class,
                    new AABB(at.getX()-0.35,at.getY()-1.0,at.getZ()-0.8,
                             at.getX()+1.35,at.getY()+3.0,at.getZ()+1.8),
                    ent->!ent.isRemoved() && !(ent instanceof ServerPlayer)).isEmpty();
        cells.add(new Dev51BEastCorridorR30b.Cell(asBuilt.kind(),
                asBuilt.clearTwoAbove(),sturdy,dry,clearEntities));
    }
    Dev51BEastCorridorR30b.Report report=Dev51BEastCorridorR30b.inspect(cells);
    boolean recorded=saved.hasNetwork(source);
    int xBegin=source.getX()+Dev51BEastCorridorR30b.EXISTING_FIRST_EXTENSION;
    int xEnd=source.getX()+Dev51BEastCorridorR30b.LAST_OFFSET;
    int xStop=source.getX()+Dev51BEastCorridorR30b.DESTINATION_OFFSET;
    String issue=report.firstIssueOffset()<0?"NONE":
            (source.getX()+report.firstIssueOffset()+","+source.getY()+","+source.getZ()+":"+report.firstIssue());
    LOG.info("[TC-DEV51B-R30B] EAST_CORRIDOR_AUDIT source={} proposedStop={} corridorX={}..{} observed={} oldRails={} fillableAir={} blocked={} unloaded={} firstIssue={} reason={} networkRecorded={} geometryOnly={} autoDispatch=false blockEdits=0 entitiesMoved=0 chunksForced=0",
            source,xStop,xBegin,xEnd,report.observed(),report.existingRailCount(),
            report.buildableEmptyCount(),report.blockedCount(),report.unloadedCount(),
            issue,report.reason(),recorded,report.corridorGeometryReady());
    LOG.info("[TC-DEV51B-R30B] EAST_CORRIDOR_SPANS offsets={} writesApproved=false driveApproved=false destinationRouteCertified=false",
            Dev51BEastCorridorR30b.compactRanges(report.ranges(),40));
    final String line1="[R30b] EAST site="+xStop+","+source.getY()+","+source.getZ()+
            " surveyX="+xBegin+".."+xEnd+" existingRail="+report.existingRailCount()+
            " emptySupported="+report.buildableEmptyCount()+" blocked="+report.blockedCount()+
            " unloaded="+report.unloadedCount();
    final String line2="[R30b] "+(report.corridorGeometryReady()?"GEOMETRY_ONLY":"HOLD")+
            " "+report.reason()+" firstIssue="+issue+". NO PLACEMENT.";
    final String line3="[R30b] SavedNetwork="+recorded+
            ". NO WORLD WRITES, NO TRAIN MOVEMENT, NO AUTOMATIC DISPATCH.";
    src.sendSuccess(()->Component.literal(line1),false);
    src.sendSuccess(()->Component.literal(line2),false);
    src.sendSuccess(()->Component.literal(line3),false);
    return 1;
}

// DEV51B R30a: READ-ONLY AS-BUILT survey of the two directions from the
// source station rail. Never trusts the unrecorded LEGACY_V1 network template,
// never force-loads chunks or creates persistent route/survey records.
private static Dev51BRailSurveyR30a.Cell dev51bR30aObserveRail(ServerLevel level,BlockPos at) {
    if(at.getY()-1 < level.getMinBuildHeight() || at.getY()+2 >= level.getMaxBuildHeight()
            || !level.hasChunkAt(at) || !level.hasChunkAt(at.above(2))
            || !level.hasChunkAt(at.below())) {
        return new Dev51BRailSurveyR30a.Cell(Dev51BRailSurveyR30a.Kind.UNLOADED,false,false);
    }
    BlockState block=level.getBlockState(at);
    boolean airClear=level.getBlockState(at.above()).isAir()
            && level.getBlockState(at.above(2)).isAir();
    boolean foundation=!level.getBlockState(at.below()).isAir();
    Dev51BRailSurveyR30a.Kind kind;
    if(block.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock
            && block.hasProperty(RailBlock.SHAPE)) {
        RailShape shape=block.getValue(RailBlock.SHAPE);
        if(shape==RailShape.EAST_WEST) {
            if(block.is(TCBlocks.TRACK_NORMAL.get())) kind=Dev51BRailSurveyR30a.Kind.NORMAL_EW;
            else if(block.is(TCBlocks.TRACK_STATION.get())) kind=Dev51BRailSurveyR30a.Kind.STATION_EW;
            else kind=Dev51BRailSurveyR30a.Kind.OTHER_EW_RAIL;
        } else kind=Dev51BRailSurveyR30a.Kind.TURN_OR_SLOPE;
    } else if(block.isAir()) {
        BlockState above=level.getBlockState(at.above());
        BlockState below=level.getBlockState(at.below());
        if(above.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock
                || below.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock)
            kind=Dev51BRailSurveyR30a.Kind.OFF_LEVEL_RAIL;
        else kind=Dev51BRailSurveyR30a.Kind.AIR;
    } else {
        ResourceLocation id=BuiltInRegistries.BLOCK.getKey(block.getBlock());
        String name=id.getPath().toLowerCase(java.util.Locale.ROOT);
        boolean complex=id.getNamespace().equals("traincraft")
                && (name.contains("rail")||name.contains("track")
                    ||name.contains("switch")||name.contains("turnout")||name.contains("curve"));
        kind=complex?Dev51BRailSurveyR30a.Kind.NATIVE_OR_SPECIAL:
                Dev51BRailSurveyR30a.Kind.BLOCKED;
    }
    return new Dev51BRailSurveyR30a.Cell(kind,airClear,foundation);
}
private static int dev51bR30aSurvey(CommandSourceStack src) {
    ServerLevel level=src.getLevel();
    if(!level.dimension().equals(Level.OVERWORLD)
            || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30a] HOLD: Requires marked disposable DEV51B R27 Movement Copy Overworld. NO CHANGES."));
        return 0;
    }
    StationData saved=stations(level); // read-only station SavedData, NEVER setDirty
    BlockPos player=BlockPos.containing(src.getPosition());
    BlockPos source=saved.closest(player,48).orElse(null);
    if(source!=null && saved.isR30TestStop(source)) {
        source=null;
        for(BlockPos candidate:saved.placed) {
            if(!saved.isR30TestStop(candidate) && candidate.distSqr(player)<=48L*48L) {
                source=candidate;break;
            }
        }
    }
    if(source==null) {
        src.sendFailure(Component.literal("[R30a] HOLD NO_RECORDED_SOURCE_STATION_WITHIN_48. NO CHANGES."));
        return 0;
    }
    final BlockPos from=source;
    java.util.List<Dev51BRailSurveyR30a.Cell> east=new java.util.ArrayList<>();
    java.util.List<Dev51BRailSurveyR30a.Cell> west=new java.util.ArrayList<>();
    for(int step=0;step<=Dev51BRailSurveyR30a.RADIUS;step++) {
        east.add(dev51bR30aObserveRail(level,from.offset(step,0,0)));
        west.add(dev51bR30aObserveRail(level,from.offset(-step,0,0)));
    }
    Dev51BRailSurveyR30a.Summary e=Dev51BRailSurveyR30a.inspect("EAST",east);
    Dev51BRailSurveyR30a.Summary w=Dev51BRailSurveyR30a.inspect("WEST",west);
    boolean networkRecorded=saved.hasNetwork(from);
    LOG.info("[TC-DEV51B-R30A] AS_BUILT_SURVEY source={} radius={} recordedNetwork={} stationRecords={} scanOnly=true worldEdits=0 trainMoved=false autoDispatch=false chunksForced=0",
            from,Dev51BRailSurveyR30a.RADIUS,networkRecorded,saved.count());
    for(Dev51BRailSurveyR30a.Summary s:java.util.List.of(e,w)) {
        boolean toEast=s.side().equals("EAST");
        int gapX=s.firstBarrierOffset()<0?Integer.MIN_VALUE:from.getX()+(toEast?1:-1)*s.firstBarrierOffset();
        int candidateX=s.firstGeometryCandidate()<0?Integer.MIN_VALUE:
                from.getX()+(toEast?1:-1)*s.firstGeometryCandidate();
        String gap=s.firstBarrierOffset()<0?"NOT_OBSERVED":
                (gapX+","+from.getY()+","+from.getZ()+":"+s.firstBarrier());
        String geom=s.firstGeometryCandidate()<0?"NONE":
                (candidateX+","+from.getY()+","+from.getZ()+":GEOMETRY_ONLY");
        LOG.info("[TC-DEV51B-R30A] SIDE={} continuous={} firstBarrier={} candidate={} spans={} driveAuthorized=false writeAuthorized=false",
                s.side(),s.continuousStraight(),gap,geom,
                Dev51BRailSurveyR30a.compactSpans(s.spans(),25));
        src.sendSuccess(()->Component.literal("[R30a] "+s.side()+" continuousStraight="+
                s.continuousStraight()+" firstBarrier="+gap+" geometricStop="+geom),false);
        if(s.firstGeometryCandidate()>=0) {
            BlockPos site=from.offset((toEast?1:-1)*s.firstGeometryCandidate(),0,0);
            // Read-only ENTITY/OTHER-STATION observation for the suggested geometry.
            boolean occupied=!level.getEntitiesOfClass(Entity.class,
                    new AABB(site.getX()-10,site.getY()-1,site.getZ()-3,
                             site.getX()+11,site.getY()+4,site.getZ()+4),
                    ent->!ent.isRemoved() && !(ent instanceof ServerPlayer)).isEmpty();
            boolean nearOther=false;
            for(BlockPos recorded:saved.placed)
                if(!recorded.equals(from) && recorded.distSqr(site)<24L*24L)nearOther=true;
            final boolean occupiedAtSite=occupied;
            final boolean otherStationNearSite=nearOther;
            LOG.info("[TC-DEV51B-R30A] GEOMETRY_{} position={} entitiesPresent={} otherStationWithin24={} requiresNetworkProof=true sitePlacementAuthorized=false routeCertified=false",
                    s.side(),site,occupied,nearOther);
            src.sendSuccess(()->Component.literal("[R30a] "+s.side()+
                    " geometry only: entitiesPresent="+occupiedAtSite+
                    " otherStationWithin24="+otherStationNearSite+"; no placement approved"),false);
        }
    }
    src.sendSuccess(()->Component.literal("[R30a] AS-BUILT ONLY; networkRecorded="+networkRecorded+
            ". Existing blocks observed, never repaired. NO PLACEMENT, NO TRAIN MOVEMENT, NO DISPATCH."),false);
    return 1;
}

// DEV51B R30: A minimally invasive second recorded test STOP on the existing
// straight mainline. One normal rail -> one station rail in the disposable
// R27 copy only. No new mainline, architecture, workers, service, dispatch,
// occupancy ticket or locomotive movement. Does not certify a complete route.
private static int dev51bR30SecondStop(CommandSourceStack src,String mode) {
    ServerLevel level=src.getLevel();
    StationData data=stations(level);
    BlockPos playerPos=BlockPos.containing(src.getPosition());
    BlockPos source=data.closest(playerPos,48).orElse(null);
    if(source==null || data.count()<1) {
        src.sendFailure(Component.literal("[R30] HOLD NO_SOURCE_STATION_WITHIN_48. NO CHANGES."));return 0;
    }
    // A recorded R30 test stop must never become its own 'source'.
    if(data.isR30TestStop(source)) {
        BlockPos other=null;
        for(BlockPos p:data.placed)if(!data.isR30TestStop(p)) {other=p;break;}
        if(other==null || other.distSqr(playerPos)>48L*48L) {
            src.sendFailure(Component.literal("[R30] HOLD STAND_NEAR_ORIGINAL_SOURCE. NO CHANGES."));return 0;
        }
        source=other;
    }
    final BlockPos from=source;
    final BlockPos stop=from.offset(Dev51BSecondStationR30.EAST_OFFSET,0,0);
    if("status".equals(mode)) {
        boolean recorded=data.isR30TestStop(stop);
        String block=level.hasChunkAt(stop)?String.valueOf(level.getBlockState(stop)):"UNLOADED";
        src.sendSuccess(()->Component.literal("[R30] STOP STATUS source="+from.toShortString()+
                " candidate="+stop.toShortString()+" recorded="+recorded+
                " block="+block+" autoDispatch=false driveAuthorized=false"),false);
        return 1;
    }
    if(!level.dimension().equals(Level.OVERWORLD) || !(src.getEntity() instanceof ServerPlayer)
            || !dev51bR27Disposable(level)) {
        src.sendFailure(Component.literal("[R30] HOLD: Requires a player in the marked DEV51B R27 Movement Copy (Overworld). NO CHANGES."));return 0;
    }
    if("undo".equals(mode)) {
        boolean provenance=data.isR30TestStop(stop);
        boolean exact=level.hasChunkAt(stop)
                && level.getBlockState(stop).is(TCBlocks.TRACK_STATION.get())
                && level.getBlockState(stop).hasProperty(RailBlock.SHAPE)
                && level.getBlockState(stop).getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
        boolean occupied=!level.getEntitiesOfClass(Entity.class,
                new AABB(stop.getX()-10,stop.getY()-1,stop.getZ()-3,
                        stop.getX()+11,stop.getY()+4,stop.getZ()+4),
                e->!e.isRemoved() && !(e instanceof net.minecraft.server.level.ServerPlayer)).isEmpty();
        boolean noJobs=data.service(stop)==null && data.staged(stop)==null
                && !data.enclosed(stop) && !data.hasNetwork(stop) && !data.hasWideNetwork(stop)
                && data.staff.values().stream().noneMatch(s->s.station.equals(stop));
        boolean allowed=Dev51BSecondStationR30.canUndo(true,true,true,provenance,
                data.placed.contains(stop),exact,noJobs,!occupied,level.hasChunkAt(stop));
        if(!allowed) {
            src.sendFailure(Component.literal("[R30] HOLD UNDO_SITE_NOT_EXACT_OR_OCCUPIED. NO CHANGES."));return 0;
        }
        BlockState before=level.getBlockState(stop);
        BlockState normal=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
        if(!level.setBlock(stop,normal,3) || !level.getBlockState(stop).equals(normal)) {
            level.setBlock(stop,before,3);
            src.sendFailure(Component.literal("[R30] HOLD UNDO_BLOCK_WRITE_FAILED. NO RECORD CHANGES."));return 0;
        }
        try {data.removeR30TestStop(stop);}catch(RuntimeException e){
            level.setBlock(stop,before,3);
            src.sendFailure(Component.literal("[R30] HOLD UNDO_ROLLED_BACK. NO RECORD CHANGES."));return 0;
        }
        LOG.info("[TC-DEV51B-R30] TEST_STOP_UNDO source={} stop={} restoredNormalRail=true savedMarkerRemoved=true trainMoved=false edits=1 autoDispatch=false",from,stop);
        src.sendSuccess(()->Component.literal("[R30] TEST STOP UNDO PASS. One original normal rail restored and R30 record removed. NO TRAINS MOVED."),true);
        return 1;
    }
    if(!"preview".equals(mode) && !"place".equals(mode))return 0;
    boolean chunks=true,straight=true,clear=true,foundation=true;
    for(int dx=-Dev51BSecondStationR30.STRAIGHT_HALF_WIDTH;
            dx<=Dev51BSecondStationR30.STRAIGHT_HALF_WIDTH;dx++) {
        BlockPos p=stop.offset(dx,0,0);
        // The entire check must be observational; never force-load chunks.
        if(!level.hasChunkAt(p) || !level.hasChunkAt(p.above(2)) || !level.hasChunkAt(p.below())) {
            chunks=false;straight=false;clear=false;foundation=false;continue;
        }
        BlockState b=level.getBlockState(p);
        if(!b.is(TCBlocks.TRACK_NORMAL.get()) || !b.hasProperty(RailBlock.SHAPE)
                || b.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)straight=false;
        if(!level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir())clear=false;
        if(level.getBlockState(p.below()).isAir())foundation=false;
    }
    // Far wider than the expected rail model and its occupancy footprint.
    boolean entityFree=chunks && level.getEntitiesOfClass(Entity.class,
            new AABB(stop.getX()-10,stop.getY()-1,stop.getZ()-3,
                    stop.getX()+11,stop.getY()+4,stop.getZ()+4),
            e->!e.isRemoved() && !(e instanceof net.minecraft.server.level.ServerPlayer)).isEmpty();
    boolean alone=true;
    for(BlockPos p:data.placed)if(!p.equals(from) && p.distSqr(stop)<24L*24L)alone=false;
    boolean sourceRail=level.hasChunkAt(from) && level.getBlockState(from).is(TCBlocks.TRACK_STATION.get());
    Dev51BSecondStationR30.Result check=Dev51BSecondStationR30.check(true,true,true,
            data.count(),!data.r30TestStops.isEmpty(),data.placed.contains(from),
            data.hasNetwork(from),sourceRail,true,chunks,straight,clear,foundation,entityFree,alone);
    LOG.info("[TC-DEV51B-R30] TEST_STOP_{} source={} stop={} result={} reason={} siteLoaded={} normalRailNine={} clear={} supports={} entityFree={} worldEdits={} trainMoved=false autoDispatch=false",
            mode.toUpperCase(java.util.Locale.ROOT),from,stop,check.verdict(),check.reason(),
            chunks,straight,clear,foundation,entityFree,"place".equals(mode)&&check.mayPlace()?1:0);
    if(!check.mayPlace()) {
        src.sendFailure(Component.literal("[R30] HOLD "+check.reason()+" at "+stop.toShortString()+". NO CHANGES."));return 0;
    }
    if("preview".equals(mode)) {
        src.sendSuccess(()->Component.literal("[R30] PREVIEW READY: one EXISTING normal rail at "+stop.toShortString()+
                " can become a recorded test-platform stop, 32 blocks east of "+from.toShortString()),false);
        src.sendSuccess(()->Component.literal("[R30] No rail extension/building; one rail + saved station record on /tcrailway network secondstation place confirm ONLY IN DISPOSABLE COPY. No driving permission."),false);
        return 1;
    }
    BlockState before=level.getBlockState(stop);
    BlockState replacement=TCBlocks.TRACK_STATION.get().defaultBlockState()
            .setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
    if(!level.setBlock(stop,replacement,3) || !level.getBlockState(stop).equals(replacement)) {
        level.setBlock(stop,before,3);
        src.sendFailure(Component.literal("[R30] HOLD SITE_RAIL_WRITE_FAILED; reverted. NO RECORD."));return 0;
    }
    try {data.recordR30TestStop(stop);}catch(RuntimeException err){
        level.setBlock(stop,before,3);
        src.sendFailure(Component.literal("[R30] HOLD SAVED_RECORD_FAILED; reverted block."));return 0;
    }
    LOG.info("[TC-DEV51B-R30] TEST_STOP_PLACED source={} stop={} sourceUnchanged=true newRecordedStation=true blockEdits=1 trainMoved=false autoDispatch=false routeCertified=false",from,stop);
    src.sendSuccess(()->Component.literal("[R30] TEST STOP PLACED at "+stop.toShortString()+
            ". Source station untouched. Now /tcrailway network destinationaudit preview; HOLD on native turnout is expected. NO DRIVING."),true);
    return 1;
}

    // DEV51B R29: read-only observation of two DISTINCT saved stations on
    // fully loaded reciprocal single-cell tracks. Does not move trains or
    // issue dispatch authority. No forced chunks, block writes or reservations.
    private static int dev51bR29DestinationPreview(CommandSourceStack source) {
        ServerLevel level=source.getLevel();
        BlockPos player=BlockPos.containing(source.getPosition());
        StationData records=stations(level); // SavedData read; no setDirty().
        BlockPos station=records.closest(player,Dev51BDestinationR29.MAX_SOURCE_RADIUS).orElse(null);
        if(station==null){
            LOG.info("[TC-DEV51B-R29] DEST_AUDIT HOLD=NO_SOURCE_STATION_NEAR_PLAYER player={} stationRecords={} trainMoved=false worldEdits=0 chunksForced=0",player,records.count());
            source.sendFailure(Component.literal("[R29] HOLD: NO_SOURCE_STATION_NEAR_PLAYER. Stand within 48 blocks of a recorded station. NO MOVEMENT."));
            return 0;
        }
        java.util.List<BlockPos> targets=records.placed.stream().filter(p ->
                !p.equals(station) && (long)(p.getX()-station.getX())*(p.getX()-station.getX())
                +(long)(p.getZ()-station.getZ())*(p.getZ()-station.getZ())
                >=(long)Dev51BDestinationR29.MIN_DISTINCT_HORIZONTAL*Dev51BDestinationR29.MIN_DISTINCT_HORIZONTAL)
                .sorted(java.util.Comparator.comparingDouble(station::distSqr)).toList();
        if(targets.isEmpty()){
            LOG.info("[TC-DEV51B-R29] DEST_AUDIT source={} recordedStations={} HOLD=NO_DISTINCT_RECORDED_DESTINATION worldEdits=0 chunksForced=0 trainsMoved=0",station,records.count());
            source.sendFailure(Component.literal("[R29] HOLD: NO_DISTINCT_RECORDED_DESTINATION. Only one recorded station/platform; no route to validate. NO MOVEMENT."));
            return 0;
        }
        // Intentionally select just the nearest distinct recorded station.
        // Never silently pick a farther destination if this one is unsuitable.
        BlockPos target=targets.get(0);
        String early=null;
        if(Math.abs((long)target.getX()-station.getX())>Dev51BDestinationR29.MAX_ROUTE_RADIUS
                ||Math.abs((long)target.getZ()-station.getZ())>Dev51BDestinationR29.MAX_ROUTE_RADIUS
                ||Math.abs((long)target.getY()-station.getY())>24)early="DESTINATION_OUTSIDE_96_BLOCK_SCAN";
        else if(!level.hasChunkAt(station))early="SOURCE_STATION_CHUNK_UNLOADED";
        else if(!level.hasChunkAt(target))early="DESTINATION_CHUNK_NOT_LOADED";
        else if(!level.getBlockState(station).is(TCBlocks.TRACK_STATION.get()))early="SOURCE_RECORDED_STATION_RAIL_MISSING";
        else if(!level.getBlockState(target).is(TCBlocks.TRACK_STATION.get()))early="DESTINATION_RECORDED_STATION_RAIL_MISSING";
        if(early!=null){
            String hold=early;
            LOG.info("[TC-DEV51B-R29] DEST_AUDIT source={} target={} savedStations={} outcome=HOLD reason={} trainsMoved=0 worldEdits=0 chunksForced=0 autoDispatch=false",station,target,records.count(),hold);
            source.sendFailure(Component.literal("[R29] HOLD: "+hold+" source="+station.toShortString()+" target="+target.toShortString()+". NO MOVEMENT."));
            return 0;
        }
        Dev51BTrackGraphR18.Pos from=new Dev51BTrackGraphR18.Pos(station.getX(),station.getY(),station.getZ());
        Dev51BTrackGraphR18.Pos to=new Dev51BTrackGraphR18.Pos(target.getX(),target.getY(),target.getZ());
        Dev51BTrackGraphR18.Reader reader=at->dev51bR18Observe(level,at);
        if(reader.read(from).kind()!=Dev51BTrackGraphR18.Kind.PLAIN ||
                reader.read(to).kind()!=Dev51BTrackGraphR18.Kind.PLAIN){
            source.sendFailure(Component.literal("[R29] HOLD: Unsupported station rail shape. NO MOVEMENT."));
            LOG.info("[TC-DEV51B-R29] DEST_AUDIT source={} target={} outcome=HOLD reason=UNSUPPORTED_STATION_RAIL worldEdits=0",station,target);
            return 0;
        }
        Dev51BTrackGraphR18.Result graph=Dev51BTrackGraphR18.scan(reader,from);
        java.util.List<Dev51BRoutePlannerR19.Anchor> anchors=java.util.List.of(
                new Dev51BRoutePlannerR19.Anchor("recorded_source",from,0,0),
                new Dev51BRoutePlannerR19.Anchor("recorded_destination",to,0,0));
        Dev51BRoutePlannerR19.Plan plan=Dev51BRoutePlannerR19.plan(reader,graph,anchors);
        Dev51BRouteSafetyR21.Report safety=Dev51BRouteSafetyR21.inspect(reader,graph,plan,pos -> {
            BlockPos rail=new BlockPos(pos.x(),pos.y(),pos.z());
            if(rail.getY()<level.getMinBuildHeight()+1||rail.getY()>=level.getMaxBuildHeight()-2
                    ||!level.hasChunkAt(rail)||!level.hasChunkAt(rail.above(2)))
                return Dev51BRouteSafetyR21.Witness.UNKNOWN;
            BlockState state=level.getBlockState(rail);
            if(state.getBlock() instanceof traincraft.block.track.AbstractLegacyBatchRailBlock ||
                    state.getBlock() instanceof traincraft.block.track.LegacyMediumCurveRailBlock ||
                    state.getBlock() instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock)
                return Dev51BRouteSafetyR21.Witness.NATIVE_OR_DYNAMIC;
            ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String path=id.getPath().toLowerCase(java.util.Locale.ROOT);
            if(id.getNamespace().equals("traincraft") &&
                    (path.contains("switch")||path.contains("turnout")||path.contains("_segment")))
                return Dev51BRouteSafetyR21.Witness.NATIVE_OR_DYNAMIC;
            if(!(state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock))
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            if(level.getBlockState(rail.below()).isAir())
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            BlockPos above=rail.above(), top=rail.above(2);
            if(!level.getBlockState(above).getCollisionShape(level,above).isEmpty() ||
                    !level.getBlockState(top).getCollisionShape(level,top).isEmpty())
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            return Dev51BRouteSafetyR21.Witness.CLEAR;
        });
        Dev51BDestinationR29.Report result=Dev51BDestinationR29.inspect(graph,plan,safety,
                "recorded_source","recorded_destination",true,true,true,true,true,true,true);
        LOG.info("[TC-DEV51B-R29] DEST_AUDIT source={} destination={} sourceRecorded=true destinationRecorded=true observedNodes={} edges={} clipped={} unloaded={} complex={} ambiguous={} links={} clear={} blocked={} unknown={} native={} connected={} manualReview={} driveAuthorized=false reason={} verdict={} loadedOnly=true signalProven=false switchProven=false occupancyProven=false trainMoved=false worldEdits=0 chunksForced=0",
                station,target,graph.nodes(),graph.edges(),graph.clippedPorts(),graph.unloadedBoundaries(),
                graph.complexBoundaries(),graph.ambiguousPorts(),result.railLinks(),result.clearCells(),
                result.blockedCells(),result.unknownCells(),result.nativeCells(),result.topologyConnected(),
                result.manualReviewCandidate(),result.blockers(),result.verdict());
        source.sendSuccess(()->Component.literal("[R29] "+result.verdict()+" from="+station.toShortString()+
                " to="+target.toShortString()+" links="+result.railLinks()+" nodes="+graph.nodes()),false);
        source.sendSuccess(()->Component.literal("[R29] Clear="+result.clearCells()+" blocked="+result.blockedCells()+
                " unknown="+result.unknownCells()+" native="+result.nativeCells()+" holds="+result.blockers()),false);
        source.sendSuccess(()->Component.literal("[R29] READ ONLY; loaded 1-cell rails only. No native switches, signals, train-width or occupancy certified. NO TRAVEL / AUTODISPATCH."),false);
        return result.manualReviewCandidate()?1:0;
    }

    // DEV51B R20: loaded-chunk-only native multi-block assembly audit. No
    // block writes, chunk tickets, train movement, dispatcher or route promotion.
    private static int dev51bR20NativeAudit(CommandSourceStack source) {
        ServerLevel level=source.getLevel();
        BlockPos player=BlockPos.containing(source.getPosition());
        java.util.Map<String,Dev51BNativeConnectivityR20.Piece> pieces=new java.util.TreeMap<>();
        boolean clipped=false;
        int nearbySpecial=0,unknownNative=0;
        // Read nearby loaded positions only. Individual owners may extend outside
        // this window; all of their declared parts are then checked explicitly.
        outer: for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-24;dy<=12;dy++) {
            BlockPos at=player.offset(dx,dy,dz);
            if(at.getY()<level.getMinBuildHeight()||at.getY()>=level.getMaxBuildHeight()||!level.hasChunkAt(at))continue;
            BlockState state=level.getBlockState(at);
            net.minecraft.world.level.block.Block block=state.getBlock();
            boolean batch=block instanceof traincraft.block.track.AbstractLegacyBatchRailBlock;
            boolean straight=block instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock;
            boolean curve=block instanceof traincraft.block.track.LegacyMediumCurveRailBlock;
            if(!batch&&!straight&&!curve){
                if(block instanceof traincraft.block.track.ClassicSpecialTrackBlock)nearbySpecial++;
                else if(block instanceof net.minecraft.world.level.block.BaseRailBlock
                        && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("traincraft")
                        && !(state.hasProperty(RailBlock.SHAPE))) unknownNative++;
                continue;
            }
            if(Math.abs(dx)==12||Math.abs(dz)==12)clipped=true;
            BlockPos root;
            net.minecraft.core.Direction facing;
            int count;
            java.util.List<Dev51BNativeConnectivityR20.Port> ports=new java.util.ArrayList<>();
            java.util.List<Dev51BNativeConnectivityR20.Expected> expected=new java.util.ArrayList<>();
            boolean dynamic=false;
            if(batch){
                var nativeBlock=(traincraft.block.track.AbstractLegacyBatchRailBlock)block;
                if(!state.hasProperty(nativeBlock.partProperty())||
                        !state.hasProperty(traincraft.block.track.AbstractLegacyBatchRailBlock.ASSEMBLY_FACING)){
                    unknownNative++;continue;
                }
                var spec=nativeBlock.getSpec();
                root=nativeBlock.getAssemblyRoot(at,state);
                facing=nativeBlock.getAssemblyFacing(state);
                count=spec.partCount();dynamic=spec.dynamicRouting();
                for(int i=0;i<count;i++){
                    BlockPos loc=spec.offsetForPart(root,facing,i);
                    expected.add(new Dev51BNativeConnectivityR20.Expected(i,
                            new Dev51BNativeConnectivityR20.Pos(loc.getX(),loc.getY(),loc.getZ())));
                }
                for(var endpoint:spec.endpoints()){
                    BlockPos loc=spec.endpointPosition(root,facing,endpoint);
                    net.minecraft.core.Direction dir=spec.rotateDirection(facing,endpoint.outward());
                    ports.add(new Dev51BNativeConnectivityR20.Port(
                            new Dev51BNativeConnectivityR20.Pos(loc.getX(),loc.getY(),loc.getZ()),
                            Dev51BNativeConnectivityR20.Facing.valueOf(dir.name()),endpoint.hasLogicalOffset()));
                }
            } else if(straight){
                if(!state.hasProperty(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART)||
                        !state.hasProperty(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING)){
                    unknownNative++;continue;
                }
                int part=state.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART);
                facing=state.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
                root=at.relative(facing.getOpposite(),part);
                String id=BuiltInRegistries.BLOCK.getKey(block).getPath();
                count=id.equals("track_medium_straight_segment")?3:
                        id.equals("track_long_straight_segment")?6:
                        id.equals("track_very_long_straight_segment")?12:0;
                if(count==0){unknownNative++;continue;}
                for(int i=0;i<count;i++){
                    BlockPos loc=root.relative(facing,i);
                    expected.add(new Dev51BNativeConnectivityR20.Expected(i,
                            new Dev51BNativeConnectivityR20.Pos(loc.getX(),loc.getY(),loc.getZ())));
                }
                BlockPos end=root.relative(facing,count-1);
                ports.add(new Dev51BNativeConnectivityR20.Port(
                        new Dev51BNativeConnectivityR20.Pos(root.getX(),root.getY(),root.getZ()),
                        Dev51BNativeConnectivityR20.Facing.valueOf(facing.getOpposite().name()),false));
                ports.add(new Dev51BNativeConnectivityR20.Port(
                        new Dev51BNativeConnectivityR20.Pos(end.getX(),end.getY(),end.getZ()),
                        Dev51BNativeConnectivityR20.Facing.valueOf(facing.name()),false));
            } else {
                if(!state.hasProperty(traincraft.block.track.LegacyMediumCurveRailBlock.PART)||
                        !state.hasProperty(traincraft.block.track.LegacyMediumCurveRailBlock.ASSEMBLY_FACING)){
                    unknownNative++;continue;
                }
                int part=state.getValue(traincraft.block.track.LegacyMediumCurveRailBlock.PART);
                facing=state.getValue(traincraft.block.track.LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                // Legacy 5/6 part curve states are migration-only, not new assemblies.
                if(part>=traincraft.block.track.LegacyMediumCurveRailBlock.PART_COUNT){unknownNative++;continue;}
                root=at.subtract(traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(BlockPos.ZERO,facing,part));
                count=traincraft.block.track.LegacyMediumCurveRailBlock.PART_COUNT;
                for(int i=0;i<count;i++){
                    BlockPos loc=traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(root,facing,i);
                    expected.add(new Dev51BNativeConnectivityR20.Expected(i,
                            new Dev51BNativeConnectivityR20.Pos(loc.getX(),loc.getY(),loc.getZ())));
                }
                // No exposed route endpoint API on this legacy curve: do NOT
                // guess real entry/exit headings. Mark placeholder ports unresolved.
                BlockPos first=traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(root,facing,0);
                BlockPos last=traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(root,facing,4);
                ports.add(new Dev51BNativeConnectivityR20.Port(
                        new Dev51BNativeConnectivityR20.Pos(first.getX(),first.getY(),first.getZ()),
                        Dev51BNativeConnectivityR20.Facing.valueOf(facing.name()),true));
                ports.add(new Dev51BNativeConnectivityR20.Port(
                        new Dev51BNativeConnectivityR20.Pos(last.getX(),last.getY(),last.getZ()),
                        Dev51BNativeConnectivityR20.Facing.valueOf(facing.name()),true));
            }
            if(count<1||count>Dev51BNativeConnectivityR20.MAX_PARTS){unknownNative++;continue;}
            String key=BuiltInRegistries.BLOCK.getKey(block)+"@"+root.asLong()+"/"+facing.name();
            if(!pieces.containsKey(key)){
                if(pieces.size()>=Dev51BNativeConnectivityR20.MAX_ASSEMBLIES){clipped=true;break outer;}
                pieces.put(key,new Dev51BNativeConnectivityR20.Piece(key,
                        BuiltInRegistries.BLOCK.getKey(block).getPath(),dynamic,expected,ports));
            }
        }
        // Part states and neighbor probes are evaluated only on already-loaded chunks.
        Dev51BNativeConnectivityR20.Audit result=Dev51BNativeConnectivityR20.audit(
                pieces.values(),(owner,part,p)->{
                    BlockPos pos=new BlockPos(p.x(),p.y(),p.z());
                    if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||
                            !level.hasChunkAt(pos))return Dev51BNativeConnectivityR20.State.UNLOADED;
                    BlockState got=level.getBlockState(pos);
                    String blockId=BuiltInRegistries.BLOCK.getKey(got.getBlock()).toString();
                    if(!owner.startsWith(blockId+"@"))return got.isAir()?
                            Dev51BNativeConnectivityR20.State.MISSING:Dev51BNativeConnectivityR20.State.FOREIGN;
                    net.minecraft.world.level.block.Block b=got.getBlock();
                    if(b instanceof traincraft.block.track.AbstractLegacyBatchRailBlock batch){
                        if(!got.hasProperty(batch.partProperty())||
                                !got.hasProperty(traincraft.block.track.AbstractLegacyBatchRailBlock.ASSEMBLY_FACING))
                            return Dev51BNativeConnectivityR20.State.FOREIGN;
                        String exact=blockId+"@"+batch.getAssemblyRoot(pos,got).asLong()+"/"+
                                batch.getAssemblyFacing(got).name();
                        return batch.getPart(got)==part&&owner.equals(exact)?
                                Dev51BNativeConnectivityR20.State.MATCH:Dev51BNativeConnectivityR20.State.FOREIGN;
                    }
                    if(b instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock){
                        if(!got.hasProperty(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART)||
                                !got.hasProperty(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING))
                            return Dev51BNativeConnectivityR20.State.FOREIGN;
                        int i=got.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART);
                        var dir=got.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
                        String exact=blockId+"@"+pos.relative(dir.getOpposite(),i).asLong()+"/"+dir.name();
                        return i==part&&owner.equals(exact)?Dev51BNativeConnectivityR20.State.MATCH:
                                Dev51BNativeConnectivityR20.State.FOREIGN;
                    }
                    if(b instanceof traincraft.block.track.LegacyMediumCurveRailBlock){
                        if(!got.hasProperty(traincraft.block.track.LegacyMediumCurveRailBlock.PART)||
                                !got.hasProperty(traincraft.block.track.LegacyMediumCurveRailBlock.ASSEMBLY_FACING))
                            return Dev51BNativeConnectivityR20.State.FOREIGN;
                        int i=got.getValue(traincraft.block.track.LegacyMediumCurveRailBlock.PART);
                        var dir=got.getValue(traincraft.block.track.LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                        String exact=blockId+"@"+pos.subtract(traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(
                                BlockPos.ZERO,dir,i)).asLong()+"/"+dir.name();
                        return i==part&&owner.equals(exact)?Dev51BNativeConnectivityR20.State.MATCH:
                                Dev51BNativeConnectivityR20.State.FOREIGN;
                    }
                    return Dev51BNativeConnectivityR20.State.FOREIGN;
                },p->{
                    BlockPos pos=new BlockPos(p.x(),p.y(),p.z());
                    if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||
                            !level.hasChunkAt(pos))return Dev51BNativeConnectivityR20.Neighbor.other(
                                    Dev51BNativeConnectivityR20.NeighborKind.UNLOADED);
                    BlockState state=level.getBlockState(pos);
                    if(state.getBlock() instanceof traincraft.block.track.AbstractLegacyBatchRailBlock||
                            state.getBlock() instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock||
                            state.getBlock() instanceof traincraft.block.track.LegacyMediumCurveRailBlock)
                        return Dev51BNativeConnectivityR20.Neighbor.other(Dev51BNativeConnectivityR20.NeighborKind.COMPLEX);
                    if(state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock&&
                            state.hasProperty(RailBlock.SHAPE)){
                        var shape=Dev51BTrackGraphR18.Shape.valueOf(state.getValue(RailBlock.SHAPE).name());
                        java.util.EnumSet<Dev51BNativeConnectivityR20.Facing> ports=
                                java.util.EnumSet.noneOf(Dev51BNativeConnectivityR20.Facing.class);
                        for(var dir:Dev51BNativeConnectivityR20.Facing.values())
                            if(Dev51BTrackGraphR18.hasPort(shape,Dev51BTrackGraphR18.Dir.valueOf(dir.name())))
                                ports.add(dir);
                        return new Dev51BNativeConnectivityR20.Neighbor(
                                Dev51BNativeConnectivityR20.NeighborKind.PLAIN,ports);
                    }
                    return Dev51BNativeConnectivityR20.Neighbor.other(
                            Dev51BNativeConnectivityR20.NeighborKind.EMPTY);
                },clipped);
        LOG.info("[TC-DEV51B-R20] NATIVE_AUDIT player={} pieces={} complete={} incomplete={} foreignParts={} dynamic={} endpoints={} reciprocalNativePairs={} plainContacts={} open={} unknown={} logicalOffsets={} nearbySpecial={} otherNative={} clipped={} verdict={} previewOnly=true trainDispatch=false trainsMoved=0 forcedChunks=0 worldEdits=0",
                player,result.pieces(),result.verified(),result.incomplete(),result.foreign(),
                result.dynamicPieces(),result.endpointCount(),result.nativePairs(),result.plainContacts(),
                result.openEnds(),result.unknownNeighbors(),result.logicalOffsetPorts(),
                nearbySpecial,unknownNative,result.capReached(),result.verdict());
        for(String note:result.warnings())LOG.info("[TC-DEV51B-R20] PART_WARNING {}",note);
        source.sendSuccess(()->Component.literal("[R20] Native assemblies="+result.pieces()+
                " complete="+result.verified()+" incomplete="+result.incomplete()+
                " native-adjacencies="+result.nativePairs()+" plain-adjacencies="+result.plainContacts()),false);
        source.sendSuccess(()->Component.literal("[R20] Unknown endpoints="+result.unknownNeighbors()+
                " dynamic="+result.dynamicPieces()+" foreign parts="+result.foreign()+
                ". "+result.verdict()+"; NOT train-drive certified. NO CHANGES."),false);
        return 1;
    }

    // DEV51B R19: observational candidate paths BETWEEN nearby station markers.
    // No block or NBT writes, no force-loading, NO native turnout routing or train AI.
    private static int dev51bR19ReadOnlyRoutePlan(CommandSourceStack source) {
        ServerLevel level=source.getLevel();
        BlockPos player=BlockPos.containing(source.getPosition());
        BlockPos seed=null;
        long bestCost=Long.MAX_VALUE;
        int complexNear=0;
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-24;dy<=12;dy++) {
            BlockPos at=player.offset(dx,dy,dz);
            if(at.getY()<level.getMinBuildHeight()||at.getY()>=level.getMaxBuildHeight()
                    ||!level.hasChunkAt(at))continue;
            Dev51BTrackGraphR18.Cell cell=dev51bR18Observe(level,
                    new Dev51BTrackGraphR18.Pos(at.getX(),at.getY(),at.getZ()));
            if(cell.kind()==Dev51BTrackGraphR18.Kind.COMPLEX)complexNear++;
            if(cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN)continue;
            long distance=(long)dx*dx+(long)dz*dz+(long)dy*dy/4L;
            if(distance<bestCost){bestCost=distance;seed=at;}
        }
        if(seed==null) {
            int count=complexNear;
            LOG.info("[TC-DEV51B-R19] ROUTE_NO_SEED player={} complexNearby={} source=LOADED_ONLY worldEdits=0 trainsMoved=0",player,count);
            source.sendFailure(Component.literal("[R19] No supported single-cell rails nearby. Complex rail cells="+count+". NOT a route failure; no changes."));
            return 0;
        }
        Dev51BTrackGraphR18.Pos start=new Dev51BTrackGraphR18.Pos(seed.getX(),seed.getY(),seed.getZ());
        Dev51BTrackGraphR18.Reader observer=at->dev51bR18Observe(level,at);
        Dev51BTrackGraphR18.Result graph=Dev51BTrackGraphR18.scan(observer,start);
        java.util.List<Dev51BRoutePlannerR19.Anchor> anchors=new java.util.ArrayList<>();
        java.util.List<Dev51BTrackGraphR18.Pos> markers=new java.util.ArrayList<>();
        for(long stored:f1cMarkers(level).byVillage.values()) {
            BlockPos marker=BlockPos.of(stored);
            if(!level.hasChunkAt(marker))continue;
            Dev51BTrackGraphR18.Pos pos=new Dev51BTrackGraphR18.Pos(marker.getX(),marker.getY(),marker.getZ());
            boolean near=false;
            for(Dev51BTrackGraphR18.Pos node:graph.observedNodes()) {
                if(Math.abs((long)pos.x()-node.x())<=9 && Math.abs((long)pos.z()-node.z())<=9
                        &&Math.abs((long)pos.y()-node.y())<=3){near=true;break;}
            }
            if(!near)continue;
            if(markers.contains(pos))continue;
            markers.add(pos);
            anchors.add(new Dev51BRoutePlannerR19.Anchor("marker_"+marker.asLong(),pos,9,3));
        }
        // Exact station cells are useful when no saved marker identifies the
        // same station. Adjacent station-cell groups are not distinct stations.
        java.util.List<Dev51BTrackGraphR18.Pos> stations=new java.util.ArrayList<>();
        for(Dev51BTrackGraphR18.Pos node:graph.observedNodes()) {
            Dev51BTrackGraphR18.Cell cell=observer.read(node);
            if(cell==null||!cell.station())continue;
            boolean covered=false;
            for(Dev51BTrackGraphR18.Pos marker:markers)
                if(Math.abs((long)marker.x()-node.x())<=9 && Math.abs((long)marker.z()-node.z())<=9
                        &&Math.abs((long)marker.y()-node.y())<=3){covered=true;break;}
            for(Dev51BTrackGraphR18.Pos station:stations)
                if(Math.abs((long)station.x()-node.x())<=6 && Math.abs((long)station.z()-node.z())<=6
                        &&Math.abs((long)station.y()-node.y())<=2){covered=true;break;}
            if(covered)continue;
            stations.add(node);
            anchors.add(new Dev51BRoutePlannerR19.Anchor("station_"+node.x()+"_"+node.y()+"_"+node.z(),node,0,0));
        }
        Dev51BRoutePlannerR19.Plan plan=Dev51BRoutePlannerR19.plan(observer,graph,anchors);
        LOG.info("[TC-DEV51B-R19] ROUTE_PLAN seed={} nodes={} edges={} stationMarkers={} stationCells={} anchors={} matched={} unresolved={} pairs={} anchorCap={} pairCap={} consistent={} partial={} verdict={} nativeRoutingVerified=false trainServiceEnabled=false trainsMoved=0 chunksForced=0 worldEdits=0",
                seed,graph.nodes(),graph.edges(),markers.size(),stations.size(),plan.inputAnchors(),
                plan.matchedAnchors(),plan.unresolvedAnchors(),plan.routes().size(),plan.anchorLimitReached(),
                plan.pairLimitReached(),plan.graphConsistent(),plan.partialNetwork(),plan.verdict());
        int display=0;
        for(Dev51BRoutePlannerR19.Match match:plan.matches()) {
            if(display++>=16)break;
            LOG.info("[TC-DEV51B-R19] ANCHOR id={} marker={} rail={} verdict={}",
                    match.id(),match.station(),match.rail(),match.matchStatus());
        }
        display=0;
        for(Dev51BRoutePlannerR19.Route route:plan.routes()) {
            if(display++>=Dev51BRoutePlannerR19.MAX_PAIRS)break;
            LOG.info("[TC-DEV51B-R19] ROUTE_CANDIDATE from={} to={} links={} turns={} slopes={} fingerprint={} samples={} verdict={} trainDispatch=false",
                    route.from(),route.to(),route.railLinks(),route.turns(),route.slopes(),
                    Long.toUnsignedString(route.pathFingerprint(),16),route.checkpoints(),route.verdict());
        }
        source.sendSuccess(()->Component.literal("[R19] "+plan.verdict()+". "+plan.matchedAnchors()+
                " station anchors, "+plan.routes().size()+" candidate pairs; "+plan.scannedNodes()+" rails observed."),false);
        source.sendSuccess(()->Component.literal("[R19] Partial="+plan.partialNetwork()+
                " ambiguous/unmatched anchors="+plan.unresolvedAnchors()+
                ". Loaded-only TOPOLOGY; no trains dispatched, no world edits."),false);
        if(!plan.routes().isEmpty()) {
            Dev51BRoutePlannerR19.Route first=plan.routes().get(0);
            source.sendSuccess(()->Component.literal("[R19] First: "+first.from()+" -> "+first.to()+
                    " links="+first.railLinks()+" turns="+first.turns()+" slopes="+first.slopes()+
                    " "+first.verdict()),false);
        }
        return 1;
    }

    // DEV51B R21: READ-ONLY route stop/continuity/clearance audit. The R19
    // graph is reconstructed and checked before reporting observational status.
    // Native turnout movement and railway villagers remain DISABLED.
    private static int dev51bR21ValidateRoute(CommandSourceStack source) {
        return dev51bR21ValidateRoute(source,false);
    }

    // R22 is read-only: reuses exactly the observed R21 route report. The
    // simulation cannot issue a real dispatch token or interact with trains.
    private static int dev51bR22SimulateDispatch(CommandSourceStack source) {
        return dev51bR21ValidateRoute(source,true);
    }

    // DEV51B R23/R24: bundled, read-only reservation + interlocking simulation.
    private static int dev51bR23R24InterlockSimulate(CommandSourceStack source) {
        return dev51bR21ValidateRoute(source,false,true);
    }

    private static int dev51bR21ValidateRoute(CommandSourceStack source,boolean simulateDispatch) {
        return dev51bR21ValidateRoute(source,simulateDispatch,false);
    }
    // DEV51B R27 supervised shunt: opt-in ONLY on a named disposable save copy
    // with a manually-created marker. Reuses accepted DEV50c8 shunt and brake.
    private static boolean dev51bR27Disposable(ServerLevel level) {
        try {
            java.nio.file.Path root=level.getServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath();
            java.nio.file.Path folder=root.getFileName();
            if(folder==null||!R27_COPY_FOLDER.equals(folder.toString()))return false;
            java.nio.file.Path marker=root.resolve(R27_COPY_MARKER);
            if(!java.nio.file.Files.isRegularFile(marker,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS))return false;
            return R27_COPY_MARKER_VALUE.equals(java.nio.file.Files.readString(marker,
                    java.nio.charset.StandardCharsets.UTF_8).trim());
        }catch(java.io.IOException|SecurityException e){return false;}
    }

    private static void dev51bR27ObserveTick(ServerLevel level) {
        R27Observation obs=r27Observation;
        if(obs==null)return;
        // Operator may revoke the disposable marker while the shunt is active.
        // Stop without waiting for the 42-tick watchdog.
        if(!dev51bR27Disposable(level)) {
            ManualDeparture session=MANUAL_DEPARTURES.get(obs.station().asLong());
            if(session!=null && session.locomotive().equals(obs.locomotive()))
                haltManualDeparture(level,session,"R27 disposable marker revoked");
            r27LastReport="FAILED: DISPOSABLE_MARKER_REVOKED";
            LOG.warn("[TC-DEV51B-R27] {}",r27LastReport);
            r27Observation=null;
            return;
        }
        Entity entity=level.getEntity(obs.locomotive());
        ManualDeparture running=MANUAL_DEPARTURES.get(obs.station().asLong());
        boolean active=running!=null && running.locomotive().equals(obs.locomotive())
                && running.operator().equals(obs.operator());
        double dx=entity==null?0:entity.getX()-obs.x();
        double dz=entity==null?0:entity.getZ()-obs.z();
        double speed=entity==null?0:entity.getDeltaMovement().horizontalDistance();
        boolean brake=entity instanceof LegacyPoweredLocomotive loco && loco.isHandBrakeApplied();
        Dev51BSupervisedShuntR27.Verdict verdict=Dev51BSupervisedShuntR27.audit(
                new Dev51BSupervisedShuntR27.Sample(level.getGameTime()-obs.tick(),dx,dz,speed,
                        brake,active,entity instanceof LegacyPoweredLocomotive && !entity.isRemoved(),
                        MANUAL_DEPARTURES.size()>(active?1:0)));
        if(!verdict.finished())return;
        // The existing tested parking API is responsible for all stopping.
        // If the frozen departure guard already parked, do not alter anything.
        if(verdict.emergencyStopRequired()) {
            if(active)haltManualDeparture(level,running,"R27 supervised emergency stop: "+verdict.reason());
            else {
                StagedConsist staged=stations(level).staged(obs.station());
                if(staged!=null && staged.locomotive().equals(obs.locomotive()))
                    parkStagedPair(level,staged,obs.operator());
            }
        }
        r27LastReport=verdict.state()+" reason="+verdict.reason()
                +" dx="+String.format(java.util.Locale.ROOT,"%.3f",dx)
                +" dz="+String.format(java.util.Locale.ROOT,"%.3f",dz)
                +" speedBpt="+String.format(java.util.Locale.ROOT,"%.4f",speed)
                +" ticks="+(level.getGameTime()-obs.tick())
                +" loco="+obs.locomotive();
        LOG.info("[TC-DEV51B-R27] SUPERVISED_SHUNT_RESULT {} automaticDriverEnabled=false worldEdits=0",r27LastReport);
        ServerPlayer operator=level.getServer().getPlayerList().getPlayer(obs.operator());
        if(operator!=null)operator.sendSystemMessage(Component.literal("[R27] "+r27LastReport));
        r27Observation=null;
    }

    /** DEV51B R28: same-station micro departure / parked-arrival supervisor.
     * No additional movement, no station-to-station route, no AI and no save flags.
     * R27 remains the ONLY active driver/controller for this test.
     */
    private static int dev51bR28Command(CommandSourceStack src,String action) {
        if(!(src.getEntity() instanceof ServerPlayer player)
                || !src.getLevel().dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("[R28] Integrated Overworld player required. NO CHANGES"));return 0;
        }
        ServerLevel level=src.getLevel();
        if("status".equals(action)) {
            src.sendSuccess(()->Component.literal("[R28] "+(r28Cycle==null?"IDLE":"ACTIVE station="+r28Cycle.station())
                    +" last="+r28LastReport+"; same-station micro-cycle only; auto-driving disabled"),false);
            return 1;
        }
        if("abort".equals(action)) {
            R28Cycle old=r28Cycle;
            if(old==null){src.sendFailure(Component.literal("[R28] No active station cycle"));return 0;}
            // R27 owns braking. It must remain abortable even if the world
            // marker was revoked after the run began.
            if(r27Observation!=null)dev51bR27Command(src,"abort");
            StagedConsist pair=stations(level).staged(old.station());
            if(pair!=null && pair.locomotive().equals(old.locomotive())
                    && pair.coach().equals(old.coach()))parkStagedPair(level,pair,old.operator());
            r28LastReport="ABORTED_BY_OPERATOR";
            r28Cycle=null;
            src.sendSuccess(()->Component.literal("[R28] ABORT: parked if loaded. No automatic departure."),true);
            return 1;
        }
        if(r28Cycle!=null){src.sendFailure(Component.literal("[R28] Another station cycle is active"));return 0;}
        if(!dev51bR27Disposable(level)) {
            src.sendFailure(Component.literal("[R28] REFUSED: exact disposable R27 world+marker required. NO CHANGES"));return 0;
        }
        // The R27 preview is the AUTHORITATIVE safety gate. Do not substitute
        // an R28 approximation for the accepted engineer, fuel, track,
        // handbrake, clearance, spacing, ownership and 0.75-block room gates.
        if("preview".equals(action)) {
            if(dev51bR27Command(src,"preview")!=1)return 0;
            src.sendSuccess(()->Component.literal("[R28] MICRO-CYCLE PREVIEW PASS: supervised 0.75-block"
                    +" departure followed by parked-arrival audit at SAME station."
                    +" No second-station route, timetable, AI or auto-dispatch."
                    +" Start only with /tcrailway network stationcycle start confirm;"
                    +" emergency /tcrailway network stationcycle abort."),false);
            return 1;
        }
        if(!"start".equals(action))return 0;
        // Capture only the exact R27-approved staged pair. The accepted R27
        // controller owns all 24-38 ticks of actual movement and stopping.
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if(found.isEmpty()){src.sendFailure(Component.literal("[R28] No station nearby"));return 0;}
        BlockPos station=found.get();
        StagedConsist pair=data.staged(station);
        if(pair==null){src.sendFailure(Component.literal("[R28] No staged pair"));return 0;}
        if(dev51bR27Command(src,"start")!=1 || r27Observation==null)return 0;
        R27Observation r=r27Observation;
        if(!r.station().equals(station) || !r.locomotive().equals(pair.locomotive())) {
            dev51bR27Command(src,"abort");
            src.sendFailure(Component.literal("[R28] R27 owner mismatch; parked and aborted"));return 0;
        }
        r28Cycle=new R28Cycle(station,pair.locomotive(),pair.coach(),player.getUUID(),
                r.x(),r.z(),r.tick());
        r28LastReport="RUNNING_SUPERVISED_MICRO_CYCLE";
        LOG.info("[TC-DEV51B-R28] MICRO_CYCLE_START station={} gp7={} coach={} "
                +"maxBlocks=0.75 maxTicks=38 automaticDriverEnabled=false worldEdits=0",
                station,pair.locomotive(),pair.coach());
        src.sendSuccess(()->Component.literal("[R28] SUPERVISED MICRO-CYCLE ARMED."
                +" R27 controls the move; R28 checks parked arrival. No automatic driving."),false);
        return 1;
    }

    private static void dev51bR28ObserveTick(ServerLevel level) {
        R28Cycle cycle=r28Cycle;
        if(cycle==null)return;
        // Never continue after a cancelled/unloaded R27 observation.
        if(r27Observation!=null)return;
        StagedConsist recorded=stations(level).staged(cycle.station());
        Entity first=level.getEntity(cycle.locomotive());
        Entity second=level.getEntity(cycle.coach());
        boolean owned=recorded!=null && cycle.locomotive().equals(recorded.locomotive())
                && cycle.coach().equals(recorded.coach());
        boolean loaded=first instanceof LegacyPoweredLocomotive && second instanceof SteamTender
                && !first.isRemoved() && !second.isRemoved();
        boolean linked=loaded && CouplingManager.areMutuallyLinked(first,
                (LegacyPoweredLocomotive)first,second,(SteamTender)second);
        boolean brake=first instanceof LegacyPoweredLocomotive loco && loco.isHandBrakeApplied();
        double gap=loaded?Dev51BVisualClearanceR27c.observe(first.getX(),first.getZ(),
                second.getX(),second.getZ(),first.getYRot(),
                ((LegacyPoweredLocomotive)first).getTrainFacingYaw(),second.getYRot()).estimatedSeam():Double.NaN;
        boolean noOther=MANUAL_DEPARTURES.isEmpty();
        long elapsed=level.getGameTime()-cycle.tick();
        Dev51BStationCycleR28.Verdict verdict=Dev51BStationCycleR28.audit(
                new Dev51BStationCycleR28.Arrival(elapsed,
                        first==null?Double.NaN:first.getX()-cycle.startX(),
                        first==null?Double.NaN:first.getZ()-cycle.startZ(),
                        first==null?Double.NaN:first.getDeltaMovement().horizontalDistance(),
                        second==null?Double.NaN:second.getDeltaMovement().horizontalDistance(),
                        gap,dev51bR27Disposable(level),owned,linked,brake,loaded,noOther,
                        r27LastReport.startsWith("SUPERVISED_SHUNT_PASS"),true));
        if(!verdict.passed() && recorded!=null && owned)parkStagedPair(level,recorded,cycle.operator());
        final String prefix=verdict.passed()?"MICRO_STATION_DEPART_AND_PARK_PASS":"MICRO_STATION_CYCLE_REJECT";
        r28LastReport=prefix+" reason="+verdict.reason()
                +" dx="+String.format(java.util.Locale.ROOT,"%.3f",first==null?Double.NaN:first.getX()-cycle.startX())
                +" seam="+String.format(java.util.Locale.ROOT,"%.3f",gap)
                +" ticks="+elapsed+" sameStation=true autoDispatch=false worldEdits=0";
        LOG.info("[TC-DEV51B-R28] {}",r28LastReport);
        ServerPlayer operator=level.getServer().getPlayerList().getPlayer(cycle.operator());
        if(operator!=null)operator.sendSystemMessage(Component.literal("[R28] "+r28LastReport));
        r28Cycle=null;
    }

    /** R27c READ-ONLY measurement. No staging, changes, or movement. */
    private static int dev51bR27cClearance(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)
                || !(src.getEntity() instanceof ServerPlayer)
                || !dev51bR27Disposable(level)) {
            src.sendFailure(Component.literal("[R27c] CLEARANCE REFUSED: confirmed disposable Overworld copy and operator required. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> near=data.closest(BlockPos.containing(src.getPosition()),96);
        if (near.isEmpty()) {
            src.sendFailure(Component.literal("[R27c] CLEARANCE REFUSED: no recorded station within 96 blocks. NO CHANGES"));return 0;
        }
        StagedConsist pair=data.staged(near.get());
        if (pair==null) {
            src.sendFailure(Component.literal("[R27c] CLEARANCE: no saved staged pair. NO CHANGES"));return 0;
        }
        Entity first=level.getEntity(pair.locomotive()),second=level.getEntity(pair.coach());
        if (!(first instanceof LegacyPoweredLocomotive loco) || !(second instanceof SteamTender coach)
                || first.isRemoved() || second.isRemoved()
                || !"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(first.getType()).toString())
                || !"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(second.getType()).toString())) {
            src.sendFailure(Component.literal("[R27c] CLEARANCE REFUSED: exact staged GP7 + Blue Coach not loaded. NO CHANGES"));return 0;
        }
        Dev51BVisualClearanceR27c.Observation observation=Dev51BVisualClearanceR27c.observe(
                first.getX(),first.getZ(),second.getX(),second.getZ(),
                loco.getYRot(),loco.getTrainFacingYaw(),second.getYRot());
        boolean linked=CouplingManager.areMutuallyLinked(first,loco,second,coach);
        String status=observation.reason();
        String line="[R27c] VISUAL CLEARANCE " + status
                + " station=" + near.get().toShortString()
                + " loco=(" + String.format(java.util.Locale.ROOT,"%.3f",first.getX())
                + "," + String.format(java.util.Locale.ROOT,"%.3f",first.getZ()) + ")"
                + " coach=(" + String.format(java.util.Locale.ROOT,"%.3f",second.getX())
                + "," + String.format(java.util.Locale.ROOT,"%.3f",second.getZ()) + ")"
                + " gap=" + String.format(java.util.Locale.ROOT,"%.3f",observation.centerGap())
                + " estimatedSeam=" + String.format(java.util.Locale.ROOT,"%.3f",observation.estimatedSeam())
                + " yaw=(" + loco.getYRot() + "," + loco.getTrainFacingYaw() + "," + second.getYRot() + ")"
                + " linked=" + linked + " handbrake=" + loco.isHandBrakeApplied()
                + " noMovement=true noWorldEdits=true";
        LOG.warn("[TC-DEV51B-R27C] {}",line);
        src.sendSuccess(()->Component.literal(line),false);
        return 1;
    }

    /** R27b: EXPLICIT, disposable-save-only retirement of an incorrectly
     * oriented recorded stage. Safety gates run before the first mutation.
     * No tracks, station architecture, train physics or AI are modified.
     * Does NOT respawn vehicles; /tcrailway service stage does that separately.
     */
    private static int dev51bR27bUnstage(CommandSourceStack src,ServerPlayer player,ServerLevel level) {
        if(!dev51bR27Disposable(level)) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: exact disposable copy and marker required. NO CHANGES"));return 0;
        }
        if(r27Observation!=null||!MANUAL_DEPARTURES.isEmpty()||RailwayTurnoutMovementTest.isActive()) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: movement/test active. NO CHANGES"));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if(found.isEmpty()) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: no recorded station within 96 blocks. NO CHANGES"));return 0;
        }
        BlockPos station=found.get();
        StagedConsist recorded=data.staged(station);
        ServicePlan plan=data.service(station);
        if(recorded==null||plan==null||!data.enclosed(station)
                ||plan.coaches()!=1||!"traincraft:locomotive_diesel_gp7".equals(SERVICE_PROFILES[plan.index()].locomotive())
                ||!"traincraft:passenger_coach_blue".equals(SERVICE_PROFILES[plan.index()].coach())) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: saved GP7 + one-coach service required. NO CHANGES"));return 0;
        }
        Entity first=level.getEntity(recorded.locomotive());
        Entity second=level.getEntity(recorded.coach());
        if(!(first instanceof LegacyPoweredLocomotive loco)||!(second instanceof SteamTender coach)
                ||first.isRemoved()||second.isRemoved()
                ||!"traincraft:locomotive_diesel_gp7".equals(BuiltInRegistries.ENTITY_TYPE.getKey(first.getType()).toString())
                ||!"traincraft:passenger_coach_blue".equals(BuiltInRegistries.ENTITY_TYPE.getKey(second.getType()).toString())) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: both recorded train entities must be loaded and correct. NO CHANGES"));return 0;
        }
        if(first.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=station.asLong()
                ||second.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")!=station.asLong()
                ||!"locomotive".equals(first.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                ||!"coach_1".equals(second.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                ||!CouplingManager.areMutuallyLinked(first,loco,second,coach)) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: exact recorded ownership and coupling required. NO CHANGES"));return 0;
        }
        double distance=Math.sqrt(first.distanceToSqr(second));
        if(distance<2.0D||distance>4.0D||first.getX()<=second.getX()
                ||Math.abs(first.getZ()-(station.getZ()+0.5D))>0.35D
                ||Math.abs(second.getZ()-(station.getZ()+0.5D))>0.35D
                ||!level.hasChunkAt(first.blockPosition())||!level.hasChunkAt(second.blockPosition())
                ||!level.getBlockState(first.blockPosition()).is(TCBlocks.TRACK_NORMAL.get())
                ||!level.getBlockState(second.blockPosition()).is(TCBlocks.TRACK_NORMAL.get())
                ||!first.getPassengers().isEmpty()||!second.getPassengers().isEmpty()
                ||!loco.isHandBrakeApplied()
                ||first.getDeltaMovement().horizontalDistance()>0.001D
                ||second.getDeltaMovement().horizontalDistance()>0.001D
                ||stagingLivingObstruction(level,loco,coach).isPresent()) {
            src.sendFailure(Component.literal("[R27b] UNSTAGE REFUSED: pair must be parked, unoccupied, aligned on loaded rails and unobstructed. NO CHANGES"));return 0;
        }
        // All checks passed; only the exact two saved vehicle UUIDs are
        // unlinked/retired, and ONLY from the explicitly confirmed test copy.
        CouplingManager.unlinkPair(loco,loco,coach,coach);
        loco.discard();coach.discard();
        data.staged.remove(station.asLong());data.setDirty();
        LOG.info("[TC-DEV51B-R27B] EXPLICIT_UNSTAGE station={} gp7={} coach={} operator={} scope=DISPOSABLE_COPY onlyTwoEntities=true worldBlocksChanged=0 autoDispatch=false",
                station,recorded.locomotive(),recorded.coach(),player.getUUID());
        src.sendSuccess(()->Component.literal("[R27b] UNSTAGE PASS: old reversed GP7/coach retired from DISPOSABLE COPY only. Saved Local Diesel roster unchanged. Now run /tcrailway service stage to create a fresh east-facing pair. NO AUTOMATIC MOVEMENT."),true);
        return 1;
    }

    private static int dev51bR27Command(CommandSourceStack src,String action) {
        if(!(src.getEntity() instanceof ServerPlayer player)||
                !src.getLevel().dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("[R27] Overworld player required. NO CHANGES"));return 0;
        }
        ServerLevel level=src.getLevel();
        if("status".equals(action)) {
            String report=(r27Observation==null?"IDLE":"RUNNING station="+r27Observation.station())
                    +" last="+r27LastReport;
            src.sendSuccess(()->Component.literal("[R27] "+report),false);return 1;
        }
        // Stop must remain usable even after the marker is removed mid-shunt.
        if("abort".equals(action)) {
            R27Observation obs=r27Observation;
            if(obs==null){src.sendFailure(Component.literal("[R27] No R27 shunt active"));return 0;}
            ManualDeparture session=MANUAL_DEPARTURES.get(obs.station().asLong());
            if(session!=null && session.locomotive().equals(obs.locomotive()))
                haltManualDeparture(level,session,"R27 operator ABORT");
            else {
                StagedConsist staged=stations(level).staged(obs.station());
                if(staged!=null && staged.locomotive().equals(obs.locomotive()))
                    parkStagedPair(level,staged,obs.operator());
            }
            r27LastReport="ABORTED_BY_OPERATOR";
            r27Observation=null;
            src.sendSuccess(()->Component.literal("[R27] ABORT: frozen handbrake/park invoked if session active. No automatic dispatch."),true);
            return 1;
        }
        // R27b unstage is a separate, explicit, restricted operation on a
        // disposable save. Never attempt to relink/teleport the old reversed
        // pair or automatically spawn replacements; the player may then use
        // the existing well-guarded `/tcrailway service stage` command.
        if("unstage".equals(action)) return dev51bR27bUnstage(src,player,level);
        if(!dev51bR27Disposable(level)) {
            src.sendFailure(Component.literal("[R27] REFUSED: exact disposable world folder + confirmation marker required. Never run in original/recovery worlds."));return 0;
        }
        StationData data=stations(level);
        Optional<BlockPos> found=data.closest(BlockPos.containing(src.getPosition()),96);
        if(found.isEmpty()){
            src.sendFailure(Component.literal("[R27] No recorded station within 96 blocks in this copy."));return 0;
        }
        BlockPos station=found.get();
        StagedConsist pair=data.staged(station);
        String failure=manualDepartureGate(level,data,station,pair,true);
        if(r27Observation!=null||!MANUAL_DEPARTURES.isEmpty())failure="Another departure/test active";
        if(failure!=null){
            LOG.info("[TC-DEV51B-R27] SUPERVISED_PRECHECK_REJECT station={} reason={} noMovement=true",station,failure);
            src.sendFailure(Component.literal("[R27] REFUSED: "+failure+". NO CHANGES"));return 0;
        }
        if("preview".equals(action)) {
            src.sendSuccess(()->Component.literal("[R27] PRECHECK PASS: GP7 + Blue Coach staged at "
                    +station.toShortString()+" in disposable copy. The pre-existing manual departure is limited"
                    +" to 0.75 block/38 ticks. START ONLY with /tcrailway network supervisedtest start confirm;"
                    +" emergency /tcrailway network supervisedtest abort."),false);return 1;
        }
        if(!"start".equals(action))return 0;
        Entity entity=level.getEntity(pair.locomotive());
        if(!(entity instanceof LegacyPoweredLocomotive))return 0;
        double startX=entity.getX(),startZ=entity.getZ();
        long startTick=level.getGameTime();
        // Trusted frozen subsystem does ALL movement, controls and braking.
        int result=manualDepartureCommand(src,"start");
        ManualDeparture running=MANUAL_DEPARTURES.get(station.asLong());
        if(result!=1||running==null||!running.locomotive().equals(pair.locomotive())){
            parkStagedPair(level,pair,player.getUUID());
            src.sendFailure(Component.literal("[R27] Frozen departure refused; pair re-parked; no R27 observer armed."));return 0;
        }
        r27Observation=new R27Observation(station,pair.locomotive(),player.getUUID(),startX,startZ,startTick);
        r27LastReport="RUNNING";
        LOG.info("[TC-DEV51B-R27] SUPERVISED_START station={} loco={} driverAutomation=false worldEdit=false",station,pair.locomotive());
        return 1;
    }

    // R25/R26: opt-in read-only diagnostics. No movement, spawn, AI, or station writes.
    private static int dev51bR25R26DryRun(CommandSourceStack source,boolean includeCrew) {
        return dev51bR21ValidateRoute(source,false,false,true,includeCrew);
    }

    private static int dev51bR21ValidateRoute(CommandSourceStack source,boolean simulateDispatch,
                                               boolean simulateInterlocking) {
        return dev51bR21ValidateRoute(source,simulateDispatch,simulateInterlocking,false,false);
    }
    private static int dev51bR21ValidateRoute(CommandSourceStack source,boolean simulateDispatch,
                                               boolean simulateInterlocking,
                                               boolean movementDryRun,boolean crewDryRun) {
        ServerLevel level=source.getLevel();
        BlockPos player=BlockPos.containing(source.getPosition());
        BlockPos seed=null;
        long bestCost=Long.MAX_VALUE;
        int complexNear=0;
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-24;dy<=12;dy++) {
            BlockPos at=player.offset(dx,dy,dz);
            if(at.getY()<level.getMinBuildHeight()||at.getY()>=level.getMaxBuildHeight()
                    ||!level.hasChunkAt(at))continue;
            Dev51BTrackGraphR18.Cell cell=dev51bR18Observe(level,
                    new Dev51BTrackGraphR18.Pos(at.getX(),at.getY(),at.getZ()));
            if(cell.kind()==Dev51BTrackGraphR18.Kind.COMPLEX)complexNear++;
            if(cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN)continue;
            long distance=(long)dx*dx+(long)dz*dz+(long)dy*dy/4L;
            if(distance<bestCost){bestCost=distance;seed=at;}
        }
        if(seed==null) {
            int count=complexNear;
            LOG.info("[TC-DEV51B-R21] ROUTE_NO_SEED player={} complexNearby={} source=LOADED_ONLY worldEdits=0 trainsMoved=0",player,count);
            source.sendFailure(Component.literal("[R21] No supported single-cell rails nearby. Complex rail cells="+count+". NOT a route failure; no changes."));
            return 0;
        }
        Dev51BTrackGraphR18.Pos start=new Dev51BTrackGraphR18.Pos(seed.getX(),seed.getY(),seed.getZ());
        Dev51BTrackGraphR18.Reader observer=at->dev51bR18Observe(level,at);
        Dev51BTrackGraphR18.Result graph=Dev51BTrackGraphR18.scan(observer,start);
        java.util.List<Dev51BRoutePlannerR19.Anchor> anchors=new java.util.ArrayList<>();
        java.util.List<Dev51BTrackGraphR18.Pos> markers=new java.util.ArrayList<>();
        for(long stored:f1cMarkers(level).byVillage.values()) {
            BlockPos marker=BlockPos.of(stored);
            if(!level.hasChunkAt(marker))continue;
            Dev51BTrackGraphR18.Pos pos=new Dev51BTrackGraphR18.Pos(marker.getX(),marker.getY(),marker.getZ());
            boolean near=false;
            for(Dev51BTrackGraphR18.Pos node:graph.observedNodes()) {
                if(Math.abs((long)pos.x()-node.x())<=9 && Math.abs((long)pos.z()-node.z())<=9
                        &&Math.abs((long)pos.y()-node.y())<=3){near=true;break;}
            }
            if(!near)continue;
            if(markers.contains(pos))continue;
            markers.add(pos);
            anchors.add(new Dev51BRoutePlannerR19.Anchor("marker_"+marker.asLong(),pos,9,3));
        }
        // Exact station cells are useful when no saved marker identifies the
        // same station. Adjacent station-cell groups are not distinct stations.
        java.util.List<Dev51BTrackGraphR18.Pos> stations=new java.util.ArrayList<>();
        for(Dev51BTrackGraphR18.Pos node:graph.observedNodes()) {
            Dev51BTrackGraphR18.Cell cell=observer.read(node);
            if(cell==null||!cell.station())continue;
            boolean covered=false;
            for(Dev51BTrackGraphR18.Pos marker:markers)
                if(Math.abs((long)marker.x()-node.x())<=9 && Math.abs((long)marker.z()-node.z())<=9
                        &&Math.abs((long)marker.y()-node.y())<=3){covered=true;break;}
            for(Dev51BTrackGraphR18.Pos station:stations)
                if(Math.abs((long)station.x()-node.x())<=6 && Math.abs((long)station.z()-node.z())<=6
                        &&Math.abs((long)station.y()-node.y())<=2){covered=true;break;}
            if(covered)continue;
            stations.add(node);
            anchors.add(new Dev51BRoutePlannerR19.Anchor("station_"+node.x()+"_"+node.y()+"_"+node.z(),node,0,0));
        }
        Dev51BRoutePlannerR19.Plan plan=Dev51BRoutePlannerR19.plan(observer,graph,anchors);
        Dev51BRouteSafetyR21.Report report=Dev51BRouteSafetyR21.inspect(observer,graph,plan,pos -> {
            BlockPos rail=new BlockPos(pos.x(),pos.y(),pos.z());
            if(rail.getY()<level.getMinBuildHeight()+1||rail.getY()>=level.getMaxBuildHeight()-2||
                    !level.hasChunkAt(rail)||!level.hasChunkAt(rail.above(2)))
                return Dev51BRouteSafetyR21.Witness.UNKNOWN;
            BlockState state=level.getBlockState(rail);
            if(state.getBlock() instanceof traincraft.block.track.AbstractLegacyBatchRailBlock ||
                    state.getBlock() instanceof traincraft.block.track.LegacyMediumCurveRailBlock ||
                    state.getBlock() instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock)
                return Dev51BRouteSafetyR21.Witness.NATIVE_OR_DYNAMIC;
            ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String name=id.getPath().toLowerCase(java.util.Locale.ROOT);
            if(id.getNamespace().equals("traincraft") &&
                    (name.contains("switch")||name.contains("turnout")||name.contains("_segment")))
                return Dev51BRouteSafetyR21.Witness.NATIVE_OR_DYNAMIC;
            if(!(state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock))
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            if(level.getBlockState(rail.below()).isAir())
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            BlockPos a=rail.above(),b=rail.above(2);
            if(!level.getBlockState(a).getCollisionShape(level,a).isEmpty() ||
                    !level.getBlockState(b).getCollisionShape(level,b).isEmpty())
                return Dev51BRouteSafetyR21.Witness.BLOCKED;
            return Dev51BRouteSafetyR21.Witness.CLEAR;
        });
        LOG.info("[TC-DEV51B-R21] ROUTE_VALIDATION seed={} observedRails={} anchors={} candidatePairs={} reviewOnly={} held={} unknown={} consistent={} partial={} dispatchApproved=false verdict={} forcedChunks=0 blocksWritten=0 trainsMoved=0",
                seed,graph.nodes(),plan.matchedAnchors(),report.candidates(),report.routeReviewOnly(),report.blocked(),
                report.totalUnknown(),report.graphConsistent(),report.networkPartial(),report.verdict());
        for(Dev51BRouteSafetyR21.CheckedRoute candidate:report.routes()){
            LOG.info("[TC-DEV51B-R21] CANDIDATE from={} to={} links={} turns={} slopes={} clear={} blocked={} unknown={} native={} stopFrom={} stopTo={} verdict={} reasons={} trainDispatch=false",
                    candidate.from(),candidate.to(),candidate.links(),candidate.turns(),candidate.slopes(),
                    candidate.observedClear(),candidate.blocked(),candidate.unknown(),candidate.nativeCells(),
                    candidate.sourceStopObserved(),candidate.destinationStopObserved(),candidate.verdict(),candidate.reasons());
        }
        source.sendSuccess(()->Component.literal("[R21] "+report.verdict()+". "+report.candidates()+
                " candidates, "+report.routeReviewOnly()+" observational reviews, "+report.blocked()+" held."),false);
        source.sendSuccess(()->Component.literal("[R21] Partial="+report.networkPartial()+
                ", unknown="+report.totalUnknown()+
                ". NEVER drive-certified. No world edits, no train movement."),false);
        if(!report.routes().isEmpty()){
            Dev51BRouteSafetyR21.CheckedRoute first=report.routes().get(0);
            source.sendSuccess(()->Component.literal("[R21] First route: "+first.from()+" -> "+first.to()+
                    " "+first.verdict()+" reasons="+first.reasons()),false);
        }
        if(simulateDispatch)dev51bR22ReportDispatchSimulation(source,level,plan,report);
        if(simulateInterlocking)dev51bR23R24ReportInterlocking(source,observer,graph,plan,report);
        if(movementDryRun)dev51bR25R26ReportDryRun(source,level,observer,graph,plan,report,crewDryRun);
        return 1;
    }

    // R25/R26: one combined read-only route + locomotive stopping + crew
    // assessment. Uses loaded-only station observations. There is deliberately
    // NO tick hook, entity velocity, AI goal, switch operation, saved lease,
    // chunk force, block mutation, or dispatch method anywhere in this probe.
    private static void dev51bR25R26ReportDryRun(CommandSourceStack source,ServerLevel level,
            Dev51BTrackGraphR18.Reader reader,Dev51BTrackGraphR18.Result graph,
            Dev51BRoutePlannerR19.Plan plan,Dev51BRouteSafetyR21.Report safety,
            boolean includeCrew) {
        var reservation=Dev51BReservationR23.inspect(reader,graph,plan,safety,java.util.Map.of());
        var interlock=Dev51BInterlockingR24.inspect(safety,reservation,java.util.Map.of());
        // Hypothetical 2-block/s brake probe with 1 block/s^2 deceleration;
        // not an empirical train performance measurement or speed command.
        var braking=new Dev51BMovementEnvelopeR25.Braking(2.0,1.0,1.0,2.0);
        var movement=Dev51BMovementEnvelopeR25.examine(safety,interlock,braking,java.util.Map.of());
        LOG.info("[TC-DEV51B-R25] MOVEMENT_DRYRUN routes={} hypotheticalStopBlocks={} reviewed={} held={} drivingEnabled=false controlWitnesses=NONE worldEdits=0 trainsMoved=0 chunksForced=0",
                movement.candidates(),Dev51BMovementEnvelopeR25.stoppingDistance(braking),
                movement.reviewOnly(),movement.held());
        for(var candidate:movement.results()){
            LOG.info("[TC-DEV51B-R25] ROUTE from={} to={} links={} stopDistance={} envelopeFits={} blockers={} movementAuthorized=false",
                    candidate.from(),candidate.to(),candidate.railLinks(),
                    candidate.stoppingDistanceBlocks(),candidate.hypotheticalEnvelopeFits(),candidate.blockers());
        }
        source.sendSuccess(()->Component.literal("[R25] Route movement dry-run: "+movement.candidates()+
                " candidates, "+movement.held()+" held, hypothetical stop="+
                Dev51BMovementEnvelopeR25.stoppingDistance(braking)+" blocks. NO MOVEMENT."),false);
        if(!includeCrew)return;
        var stored=stations(level); // read-only station SavedData inspection
        java.util.Map<String,Dev51BVillagerPilotR26.Crew> crew=new java.util.HashMap<>();
        for(var match:plan.matches()) {
            var rail=match.rail();
            if(rail==null)continue;
            BlockPos railPos=new BlockPos(rail.x(),rail.y(),rail.z());
            BlockPos origin=stored.closest(railPos,16).orElse(null);
            boolean loaded=origin!=null&&level.hasChunkAt(origin);
            var assigned=origin==null?null:stored.getStaff(origin,"railway_engineer");
            boolean engineerRecorded=assigned!=null&&assigned.uuid!=null&&assigned.respawnAt==0;
            boolean engineerLoaded=loaded&&engineerRecorded&&level.getEntity(assigned.uuid) instanceof Villager;
            var staged=origin==null?null:stored.staged(origin);
            boolean loco=loaded&&staged!=null&&level.getEntity(staged.locomotive())!=null;
            boolean coach=loaded&&staged!=null&&level.getEntity(staged.coach())!=null;
            // Destination loaded observation is separate and conservative.
            boolean destinationLoaded=false;
            for(var other:plan.matches()){
                if(match.id().equals(other.id()))continue;
                var p=other.rail();if(p==null)continue;
                var stop=stored.closest(new BlockPos(p.x(),p.y(),p.z()),16).orElse(null);
                if(stop!=null&&level.hasChunkAt(stop)){destinationLoaded=true;break;}
            }
            crew.put(match.id(),new Dev51BVillagerPilotR26.Crew(
                    loaded,destinationLoaded,engineerRecorded,engineerLoaded,
                    false,false,loco,coach,false,false));
        }
        var pilot=Dev51BVillagerPilotR26.assess(movement,interlock,crew);
        LOG.info("[TC-DEV51B-R26] VILLAGER_PILOT_STATUS routes={} held={} supervisedCandidates={} automationEnabled=false noAIInstalled=true noTrainsSpawned=true noTrainsMoved=true worldEdits=0",
                pilot.routes(),pilot.held(),pilot.supervisedCandidates());
        for(var decision:pilot.decisions()){
            LOG.info("[TC-DEV51B-R26] CREW_ROUTE from={} to={} blockers={} villagerDispatch=false",
                    decision.from(),decision.to(),decision.blockers());
        }
        source.sendSuccess(()->Component.literal("[R26] Villager pilot readiness: "+pilot.routes()+
                " routes; "+pilot.held()+" held. AI / dispatch DISABLED. No world changes."),false);
        if(!pilot.decisions().isEmpty()){
            var first=pilot.decisions().get(0);
            source.sendSuccess(()->Component.literal("[R26] First blockers: "+first.blockers()),false);
        }
    }

    // DEV51B R23 + R24: reconstruct full loaded-only candidate routes before
    // comparing potential reservations. Does NOT persist a lease or certify a
    // signal, native switch, level crossing, braking distance or train separation.
    private static void dev51bR23R24ReportInterlocking(CommandSourceStack source,
            Dev51BTrackGraphR18.Reader reader,Dev51BTrackGraphR18.Result graph,
            Dev51BRoutePlannerR19.Plan plan,Dev51BRouteSafetyR21.Report safety) {
        var leases=Dev51BReservationR23.inspect(reader,graph,plan,safety,
                java.util.Map.of()); // No persistent leases exist in R23.
        var interlock=Dev51BInterlockingR24.inspect(safety,leases,
                java.util.Map.of()); // No live signals or turnout proof in R24.
        LOG.info("[TC-DEV51B-R23/R24] INTERLOCK_SIM pairs={} reconstructed={} conflicts={} reservationsHeld={} interlockHeld={} movementEnabled=false leaseIssued=false liveSignalsProven=false worldEdits=0 trainsMoved=0 chunksForced=0",
                leases.pairs(),leases.reconstructed(),leases.conflicted(),leases.held(),interlock.held());
        for(int i=0;i<leases.candidates().size();i++){
            var candidate=leases.candidates().get(i);
            var control=interlock.decisions().get(i);
            LOG.info("[TC-DEV51B-R23/R24] ROUTE from={} to={} fullPath={} pathCells={} conflicts={} reservationBlockers={} interlockingBlockers={} movementAllowed=false",
                    candidate.from(),candidate.to(),candidate.fullPathVerified(),candidate.fullPathCells(),
                    candidate.conflicts(),candidate.blockers(),control.blockers());
        }
        source.sendSuccess(()->Component.literal("[R23/R24] Routes="+leases.pairs()+
                ", full-path verified="+leases.reconstructed()+
                ", reservation conflicts="+leases.conflicted()+
                ", interlocking held="+interlock.held()),false);
        source.sendSuccess(()->Component.literal("[R23/R24] READ ONLY. Unknown signals, turnout alignment, live occupancy and braking. No leases, trains or world changes."),false);
    }

    // DEV51B R22: SIMULATION ONLY; no movement calls, no saved departure state,
    // no occupation lease, no redstone operations and no world writes.
    private static void dev51bR22ReportDispatchSimulation(CommandSourceStack source,ServerLevel level,
            Dev51BRoutePlannerR19.Plan plan,Dev51BRouteSafetyR21.Report prior) {
        StationData saved=stations(level); // read-only SavedData access (no setDirty)
        java.util.Map<String,Dev51BDispatchSimulationR22.Station> stationStates=new java.util.HashMap<>();
        java.util.Map<String,Dev51BDispatchSimulationR22.Consist> consists=new java.util.HashMap<>();
        for(Dev51BRoutePlannerR19.Match match:plan.matches()) {
            Dev51BTrackGraphR18.Pos at=match.rail();
            if(at==null)continue;
            BlockPos rail=new BlockPos(at.x(),at.y(),at.z());
            Optional<BlockPos> found=saved.closest(rail,16);
            BlockPos station=found.orElse(null);
            boolean loaded=station!=null && level.hasChunkAt(station);
            boolean stationRail=loaded && level.getBlockState(station).is(TCBlocks.TRACK_STATION.get());
            ServicePlan service=station==null?null:saved.service(station);
            StaffSlot engineer=station==null?null:saved.getStaff(station,"railway_engineer");
            boolean staffRecorded=engineer!=null && engineer.uuid!=null && engineer.respawnAt==0;
            boolean staffLoaded=staffRecorded && level.getEntity(engineer.uuid) instanceof Villager;
            stationStates.put(match.id(),new Dev51BDispatchSimulationR22.Station(
                    service!=null,loaded,stationRail,staffRecorded,staffLoaded));
            StagedConsist staged=station==null?null:saved.staged(station);
            if(service==null || service.index()<0 || service.index()>=SERVICE_PROFILES.length)continue;
            ServiceProfile profile=SERVICE_PROFILES[service.index()];
            Entity loco=staged==null?null:level.getEntity(staged.locomotive());
            Entity coach=staged==null?null:level.getEntity(staged.coach());
            boolean locoType=loco!=null && profile.locomotive().equals(
                    BuiltInRegistries.ENTITY_TYPE.getKey(loco.getType()).toString());
            boolean coachType=coach!=null && profile.coach().equals(
                    BuiltInRegistries.ENTITY_TYPE.getKey(coach.getType()).toString());
            consists.put(match.id(),new Dev51BDispatchSimulationR22.Consist(
                    profile.locomotive(),profile.coach(),service.coaches(),staged==null?0:1,
                    BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(profile.locomotive())),
                    BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(profile.coach())),
                    locoType,coachType,false)); // coupler verification reserved for R23
        }
        // Conservative: no global occupancy reservation, native turnout state,
        // signalling proof, brake-distance proof or collision lock exists yet.
        var report=Dev51BDispatchSimulationR22.evaluate(prior,stationStates,consists,
                java.util.Map.of(),new Dev51BDispatchSimulationR22.Controls(false,false,false,false));
        LOG.info("[TC-DEV51B-R22] DISPATCH_SIM candidatePairs={} reviewOnly={} held={} occupancyUnknown={} dispatchEnabled=false verdict={} worldEdits=0 trainsMoved=0 forcedChunks=0",
                report.candidatePairs(),report.reviewOnly(),report.held(),report.unknownOccupancy(),report.verdict());
        for(Dev51BDispatchSimulationR22.Decision decision:report.decisions()) {
            LOG.info("[TC-DEV51B-R22] SIM_CANDIDATE from={} to={} verdict={} blockers={} dispatchApproved=false trainSpawned=false",
                    decision.from(),decision.to(),decision.verdict(),decision.blockers());
        }
        source.sendSuccess(()->Component.literal("[R22] "+report.verdict()+"; "+report.held()+
                " held / "+report.candidatePairs()+" candidates. TRAIN DISPATCH DISABLED."),false);
        if(!report.decisions().isEmpty()) {
            var first=report.decisions().get(0);
            source.sendSuccess(()->Component.literal("[R22] "+first.from()+" -> "+first.to()+
                " reasons="+first.blockers()),false);
        }
    }

    // DEV51B R18: READ-ONLY observation of player-built, reciprocal 1-cell rails.
    // This does not certify native multi-block rail routing or enable train AI.
    private static Dev51BTrackGraphR18.Cell dev51bR18Observe(ServerLevel level,
                                                               Dev51BTrackGraphR18.Pos p) {
        BlockPos block=new BlockPos(p.x(),p.y(),p.z());
        if(p.y()<level.getMinBuildHeight() || p.y()>=level.getMaxBuildHeight()
                ||!level.hasChunkAt(block))
            return Dev51BTrackGraphR18.Cell.other(Dev51BTrackGraphR18.Kind.UNLOADED,"");
        BlockState state=level.getBlockState(block);
        if(state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock
                && state.hasProperty(RailBlock.SHAPE)) {
            // RailShape name alignment is checked by the headless test helper.
            Dev51BTrackGraphR18.Shape shape=Dev51BTrackGraphR18.Shape.valueOf(
                    state.getValue(RailBlock.SHAPE).name());
            return Dev51BTrackGraphR18.Cell.plain(shape,state.is(TCBlocks.TRACK_STATION.get()));
        }
        ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name=id.getPath().toLowerCase(java.util.Locale.ROOT);
        // Unsupported native Traincraft/specialty rails are reported at graph
        // boundaries, NEVER treated as plain reciprocal connections.
        boolean complex=state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock
                || id.getNamespace().equals("traincraft")
                && (name.contains("rail")||name.contains("track")||name.contains("switch")
                        ||name.contains("turnout")||name.contains("curve"));
        return Dev51BTrackGraphR18.Cell.other(complex?
                Dev51BTrackGraphR18.Kind.COMPLEX:Dev51BTrackGraphR18.Kind.EMPTY,name);
    }

    private static int dev51bR18ReadOnlyNetworkScan(CommandSourceStack source) {
        ServerLevel level=source.getLevel();
        BlockPos player=BlockPos.containing(source.getPosition());
        BlockPos seed=null;
        long bestCost=Long.MAX_VALUE;
        int unsupportedNear=0;
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++) {
            for(int dy=-24;dy<=12;dy++) {
                BlockPos at=player.offset(dx,dy,dz);
                if(at.getY()<level.getMinBuildHeight()||at.getY()>=level.getMaxBuildHeight()
                        ||!level.hasChunkAt(at))continue;
                Dev51BTrackGraphR18.Cell cell=dev51bR18Observe(level,
                        new Dev51BTrackGraphR18.Pos(at.getX(),at.getY(),at.getZ()));
                if(cell.kind()==Dev51BTrackGraphR18.Kind.COMPLEX)unsupportedNear++;
                if(cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN)continue;
                long cost=(long)dx*dx+(long)dz*dz+(long)dy*dy/4L;
                if(cost<bestCost){bestCost=cost;seed=at;}
            }
        }
        if(seed==null) {
            int count=unsupportedNear;
            source.sendFailure(Component.literal("[R18] No supported 1-cell rails within search area; nearby complex rail cells="+count+". NO CHANGES."));
            LOG.info("[TC-DEV51B-R18] SCAN_NO_SEED player={} unsupportedCells={} loadedOnly=true worldEdits=0",player,count);
            return 0;
        }
        Dev51BTrackGraphR18.Pos start=new Dev51BTrackGraphR18.Pos(seed.getX(),seed.getY(),seed.getZ());
        Dev51BTrackGraphR18.Result result=Dev51BTrackGraphR18.scan(
                at->dev51bR18Observe(level,at),start);
        int markerCount=0;
        for(long stored:f1cMarkers(level).byVillage.values()) {
            BlockPos marker=BlockPos.of(stored);
            if(!level.hasChunkAt(marker))continue;
            for(Dev51BTrackGraphR18.Pos node:result.observedNodes()) {
                if(Math.abs(marker.getX()-node.x())<=9&&Math.abs(marker.getY()-node.y())<=3
                        &&Math.abs(marker.getZ()-node.z())<=9){markerCount++;break;}
            }
        }
        int markers=markerCount;
        LOG.info("[TC-DEV51B-R18] PLAYER_TRACK_SCAN seed={} nodes={} reciprocalEdges={} openPorts={} bends={} slopes={} stationCells={} stationMarkers={} complexBoundaries={} switches={} complexTurns={} unloaded={} ambiguous={} clipped={} capped={} verdict={} topologyOnly=true nativeRouteCertified=false trainsMoved=0 AIEnabled=false chunksForced=0 worldEdits=0",
                seed,result.nodes(),result.edges(),result.openPorts(),result.curves(),result.slopes(),
                result.stationCells(),markers,result.complexBoundaries(),result.switchCells(),
                result.turnCells(),result.unloadedBoundaries(),result.ambiguousPorts(),
                result.clippedPorts(),result.capReached(),result.verdict());
        source.sendSuccess(()->Component.literal("[R18] "+result.nodes()+" observed 1-cell rails, "
                +result.edges()+" reciprocal links, "+result.openPorts()+" unlinked ports. "
                +result.verdict()),false);
        source.sendSuccess(()->Component.literal("[R18] Bends="+result.curves()+" slopes="+result.slopes()
                +" station cells="+result.stationCells()+" marked stops="+markers
                +" complex neighbors="+result.complexBoundaries()+" switches="+result.switchCells()),false);
        source.sendSuccess(()->Component.literal("[R18] Unloaded="+result.unloadedBoundaries()
                +" ambiguous="+result.ambiguousPorts()+" range clipped="+result.clippedPorts()
                +". Loaded-only read-only scan; NOT train drive certification."),false);
        return 1;
    }

    // DEV50f1: Construction-first pivot. A REAL but deliberately short native
    // straight rail stop, not a hypothetical loop. Explicit operator action,
    // disposable test world ONLY, and no curves, slopes, trains or chunk tickets.
    private static final String F1_LOG = "[TC-DEV50F1K]";
    // Hard quarantine: failed DEV50f1 transaction at -726,70,2052. Its
    // original in-memory snapshot was not persisted; never reuse this area.
    private static final int F1A_Q_MIN_X=-758, F1A_Q_MAX_X=-694;
    private static final int F1A_Q_MIN_Z=2041, F1A_Q_MAX_Z=2063;
    private static boolean f1aAuditedThisSession;
    // Only fresh village detections in the exact disposable save can use
    // automatic construction. Never force-load chunks or retrofit old worlds.
    private static final Map<Long,Long> F1C_AUTO_NEXT=new HashMap<>();
    private static final Map<Long,Integer> F1C_AUTO_ATTEMPTS=new HashMap<>();
    private static final int F1F_MAX_SITES=36;
    private static final long F1F_MIN_HEAP_HEADROOM=768L*1024L*1024L;
    private static final Map<Long,F1fSafeSearch> F1F_SEARCH_TASKS=new HashMap<>();
    private static long F1F_LAST_GLOBAL_SCAN=-100000L;
    private static final class F1fSafeSearch {
        int cursor, missingChunks, rejected, stableChecks;
        long lastStableTick=-1L;
        BlockPos lastPlayer;
    }
    private static volatile boolean f1cAutoStopped=false;
    private static final int F1C_MAX_TRIES_PER_VILLAGE=5;
    private static final String F1C_MARKER_SAVE="tafaricraft_dev50f1c_straight_village_markers";

    // Read-only inspection of the previous failed build footprint. Matching
    // vanilla terrain is NOT proof of exact rollback: no durable pre-image
    // from the previous session exists. No automatic repair or block writes.
    private static int f1aSiteAudit(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50f1b site audit only in the disposable "
                    +DEV50E2_TEST_WORLD+". No changes."));return 0;
        }
        int rails=0,stops=0,gravel=0,dirt=0,unloaded=0,positions=0;
        int anomalous=0;
        for(int x=-750;x<=-702;x++)for(int z=2049;z<=2055;z++){
            BlockPos check=new BlockPos(x,70,z);
            if(!level.hasChunkAt(check)) {unloaded++;continue;}
            for(int y=68;y<=74;y++) {
                BlockPos at=new BlockPos(x,y,z);
                if(!level.hasChunkAt(at)) {unloaded++;continue;}
                BlockState state=level.getBlockState(at);
                positions++;
                if(state.is(TCBlocks.TRACK_NORMAL.get()))rails++;
                if(state.is(TCBlocks.TRACK_STATION.get()))stops++;
                if(y==69&&state.is(Blocks.GRAVEL))gravel++;
                if(y==68&&state.is(Blocks.DIRT))dirt++;
                if(level.getBlockEntity(at)!=null)anomalous++;
            }
        }
        BlockPos failedAt=new BlockPos(-750,71,2049);
        String atFailure=level.hasChunkAt(failedAt)
                ?String.valueOf(level.getBlockState(failedAt)) :"UNLOADED";
        f1aAuditedThisSession=unloaded==0;
        String verdict=unloaded>0?"INCOMPLETE_UNLOADED":
                rails+stops>0?"NATIVE_RAIL_REMAINS_FROM_UNKNOWN_SOURCE":
                "NO_RAIL_OBSERVED_BUT_ORIGINAL_TERRAIN_UNVERIFIABLE";
        LOG.warn(F1_LOG+" SITE_INTEGRITY_AUDIT center=-726:70:2052 scanned={} unloaded={} normalRails={} "
                        +"stationRails={} gravelAtBed={} dirtAtSupport={} blockEntities={} failedAt={} "
                        +"verdict={} originalSnapshotPersisted=false exactRollbackCertifiable=false "
                        +"quarantineX=[{},{}] quarantineZ=[{},{}] construction=DISABLED blocksChanged=0",
                positions,unloaded,rails,stops,gravel,dirt,anomalous,atFailure,verdict,
                F1A_Q_MIN_X,F1A_Q_MAX_X,F1A_Q_MIN_Z,F1A_Q_MAX_Z);
        final String auditMessage="[F1a AUDIT] "+verdict+"; remaining native rails="
                +(rails+stops)+", gravel bed="+gravel+", unloaded="+unloaded
                +". Previous footprint QUARANTINED. Original terrain cannot be certified.";
        src.sendSuccess(()->Component.literal(auditMessage),false);
        return unloaded>0?0:1;
    }

    private static boolean f1aOverlapsQuarantine(BlockPos center,boolean alongX,int length) {
        int half=length/2;
        int minX=center.getX()-(alongX?half:3), maxX=center.getX()+(alongX?half:3);
        int minZ=center.getZ()-(alongX?3:half), maxZ=center.getZ()+(alongX?3:half);
        return maxX>=F1A_Q_MIN_X&&minX<=F1A_Q_MAX_X
                &&maxZ>=F1A_Q_MIN_Z&&minZ<=F1A_Q_MAX_Z;
    }

    // Minecraft's setBlock boolean only says whether it changed the requested
    // cell; already-matching blocks can yield false. Verify the state itself.
    private static boolean f1aSetExact(ServerLevel level,BlockPos at,BlockState target) {
        if(!level.hasChunkAt(at))return false;
        if(level.getBlockState(at).equals(target))return true;
        level.setBlock(at,target,2);
        return level.getBlockState(at).equals(target);
    }

    // DEV50f1b: Minecraft can remove tall-grass halves, flowers and similar
    // fragile foliage as a CONSEQUENCE of an approved block edit. Do not
    // treat this predictable in-footprint cascade as outside interference.
    // Only known, non-fluid plant/replaceable originals can receive this
    // exception, and only in positions already present in the preview's
    // exact preimage/target map. No other change is excused.
    private static boolean f1bFragileVegetation(BlockState original) {
        return original.is(Blocks.TALL_GRASS) || original.is(Blocks.LARGE_FERN)
                || original.is(Blocks.GRASS) || original.is(Blocks.FERN)
                || original.is(Blocks.DEAD_BUSH) || original.is(Blocks.VINE)
                || original.is(Blocks.SUNFLOWER) || original.is(Blocks.LILAC)
                || original.is(Blocks.ROSE_BUSH) || original.is(Blocks.PEONY)
                || original.is(Blocks.SWEET_BERRY_BUSH)
                || original.is(net.minecraft.tags.BlockTags.FLOWERS)
                || (!original.isAir() && original.getFluidState().isEmpty()
                        && original.canBeReplaced());
    }

    // Prepare only vegetation the approved plan was ALREADY going to replace.
    // Clear it before dirt/gravel/rail changes can make double-plant halves
    // pop spontaneously. Explicitly check each resulting state. A cascade to
    // air is accepted only when the original was recorded as safe vegetation.
    private static void f1bPreclearVegetation(ServerLevel level,F1Proposal p) {
        int removed=0,alreadyAir=0;
        for(var entry:p.changes().entrySet()) {
            BlockPos at=entry.getKey();
            BlockState original=p.before().get(at);
            if(original==null || !f1bFragileVegetation(original))continue;
            if(!level.hasChunkAt(at))
                throw new IllegalStateException("VEGETATION_CHUNK_UNLOADED "+at.toShortString());
            BlockState now=level.getBlockState(at);
            if(now.isAir()) {alreadyAir++;continue;}
            if(!now.equals(original))
                throw new IllegalStateException("VEGETATION_INTERFERENCE "+at.toShortString()
                        +" expectedBefore="+original+" actual="+now);
            if(!f1aSetExact(level,at,Blocks.AIR.defaultBlockState()))
                throw new IllegalStateException("VEGETATION_CLEAR_FAILED "+at.toShortString()
                        +" actual="+level.getBlockState(at));
            removed++;
        }
        LOG.info(F1_LOG+" VEGETATION_PRECLEAR center={} removed={} cascadingAlreadyAir={} "
                        +"recordedCellsOnly=true noUncheckedOverrides=true",p.center(),removed,alreadyAir);
    }

    private static int f1aWritePhase(BlockState target){
        if(target.is(Blocks.DIRT))return 0;  // structural fill first
        if(target.isAir())return 1;          // cut / clearance next
        if(target.is(Blocks.GRAVEL))return 2;// ballast after support/clearance
        return 3;                           // native rail last
    }
    private static void f1aApply(ServerLevel level,F1Proposal p) {
        // Place-time preflight already compared ALL originals with the preview.
        // Clear approved fragile plants first, before supports invalidate the
        // attached lower/upper half through Minecraft's neighbor updates.
        f1bPreclearVegetation(level,p);
        for(int phase=0;phase<=3;phase++)for(var entry:p.changes().entrySet()){
            BlockPos at=entry.getKey();BlockState target=entry.getValue();
            if(f1aWritePhase(target)!=phase)continue;
            BlockState now=level.getBlockState(at);
            BlockState original=p.before().get(at);
            // Some vegetation may already have become air during preclear.
            // A missing recorded fragile plant is expected; an unexpected
            // missing solid, rail, structure or other block is NOT.
            boolean approvedFoliageAir=now.isAir() && f1bFragileVegetation(original);
            if(!now.equals(original)&&!now.equals(target)&&!approvedFoliageAir)
                throw new IllegalStateException("INTERFERENCE "+at.toShortString()
                        +" expectedBefore="+original+" actual="+now);
            if(!f1aSetExact(level,at,target))
                throw new IllegalStateException("SET_STATE_MISMATCH "+at.toShortString()
                        +" wanted="+target+" actual="+level.getBlockState(at));
        }
        // A rail/block update may cause a neighbor change. Verify ALL
        // recorded cells, not just writes, before reporting PLACED.
        for(var entry:p.before().entrySet()){
            BlockState target=p.changes().getOrDefault(entry.getKey(),entry.getValue());
            if(!level.hasChunkAt(entry.getKey())
                    ||!level.getBlockState(entry.getKey()).equals(target))
                throw new IllegalStateException("POST_AUDIT "+entry.getKey().toShortString());
        }
    }
    private static boolean f1aRestore(ServerLevel level,F1Proposal p,String reason) {
        List<Map.Entry<BlockPos,BlockState>> reverse=new ArrayList<>(p.before().entrySet());
        java.util.Collections.reverse(reverse);
        int mismatches=0,writeFailures=0,unloaded=0;
        // Passes account for neighboring plant/support changes; never claim
        // success from setBlock return values. Limit passes to avoid churn.
        for(int pass=0;pass<3;pass++){
            boolean any=false;
            for(var entry:reverse){
                BlockPos at=entry.getKey();
                if(!level.hasChunkAt(at)){unloaded++;continue;}
                if(!level.getBlockState(at).equals(entry.getValue())){
                    any=true;
                    if(!f1aSetExact(level,at,entry.getValue()))writeFailures++;
                }
            }
            if(!any)break;
        }
        for(var entry:reverse){
            if(!level.hasChunkAt(entry.getKey())||
                    !level.getBlockState(entry.getKey()).equals(entry.getValue()))mismatches++;
        }
        boolean verified=mismatches==0&&unloaded==0;
        LOG.warn(F1_LOG+" RESTORE_AUDIT reason={} restoredAll={} mismatches={} "
                        +"writeFailures={} unloaded={} preimageCells={} previousFailureSiteExcluded=true",
                reason,verified,mismatches,writeFailures,unloaded,p.before().size());
        return verified;
    }

    private static F1Proposal f1Preview;
    private static F1Proposal f1LastPlaced;
    private record F1Proposal(BlockPos village,BlockPos center,boolean alongX,int length,
                              int cutBlocks,int fillBlocks,int changeCount,long createdTick,
                              Map<BlockPos,BlockState> before,Map<BlockPos,BlockState> changes) {}
    private record F1Try(F1Proposal proposal,String reason) {}
    private static final int[] F1_RADII={64,72,80,96,112,128,144};
    private static final int[] F1_LATERAL={0,16,-16,32,-32};
    private static final int[] F1_LENGTHS={49,33,25,17};
    // DEV51B-R15: the established platform is 15 blocks long (-7..+7).
    // New builds receive one NORMAL through-rail beyond EACH platform end,
    // for a 17-block starter line players may extend. Existing 49-rail
    // stations, saved path specimens and legacy retrofit audits are frozen.
    private static final int F1R15_NEW_STATION_RAILS=17;
    // DEV50f1d automatic station sites are intentionally near the village.
    // Do not select the distant 112-144 block candidates from f1c.
    private static final int[] F1D_NEAR_RADII={60,64,72,80,88,96};
    private static final int[] F1D_LATERAL={0,8,-8,16,-16,24,-24};

    // Writes are planned in memory; ALL touched cells have the original block
    // state recorded, and ANY existing tile entity or unsupported material stops.
    private static boolean f1PlanCell(ServerLevel level,BlockPos p,BlockState target,
                                      Map<BlockPos,BlockState> before,
                                      Map<BlockPos,BlockState> changes) {
        if(!level.hasChunkAt(p))return false;
        if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())return false;
        BlockState old=level.getBlockState(p);
        if(!old.getFluidState().isEmpty() || level.getBlockEntity(p)!=null)return false;
        before.putIfAbsent(p,old);
        if(!old.equals(target))changes.put(p,target);
        return true;
    }
    private static boolean f1NaturalOrEmpty(BlockState state){
        return state.isAir() || dev50e6iNatural(state) ||
                (state.canBeReplaced() && state.getFluidState().isEmpty());
    }
    private static F1Try f1Try(ServerLevel level,BlockPos village,BlockPos c,
                               boolean alongX,int length) {
        if(f1aOverlapsQuarantine(c,alongX,length))
            return new F1Try(null,"PRIOR_FAILED_SITE_QUARANTINE");
        if(c.getY()<level.getMinBuildHeight()+5 ||c.getY()>level.getMaxBuildHeight()-9)
            return new F1Try(null,"HEIGHT_LIMIT");
        int cut=0,fill=0;
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        final int half=length/2;
        for(int along=-half;along<=half;along++)for(int side=-3;side<=3;side++) {
            int x=c.getX()+(alongX?along:side);
            int z=c.getZ()+(alongX?side:along);
            long vx=(long)x-village.getX(),vz=(long)z-village.getZ();
            if(vx*vx+vz*vz<56L*56L)return new F1Try(null,"VILLAGE_BUFFER");
            BlockPos column=new BlockPos(x,c.getY(),z);
            if(!level.hasChunkAt(column))return new F1Try(null,"CHUNK_UNLOADED");
            int naturalY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            int delta=naturalY-c.getY();
            if(delta>2)return new F1Try(null,"CUT_OVER_2");
            if(delta< -2)return new F1Try(null,"FILL_OVER_2");
            // Screen untouched footing, proposed ballast and 5 clear blocks.
            int bottom=Math.min(naturalY-1,c.getY()-3);
            for(int y=bottom;y<=c.getY()+4;y++) {
                BlockPos p=new BlockPos(x,y,z);
                if(!level.hasChunkAt(p))return new F1Try(null,"CHUNK_UNLOADED");
                if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())
                    return new F1Try(null,"VILLAGE_STRUCTURE");
                BlockState old=level.getBlockState(p);
                if(!old.getFluidState().isEmpty())return new F1Try(null,"WATER_OR_FLUID");
                if(level.getBlockEntity(p)!=null)return new F1Try(null,"BLOCK_ENTITY");
                if(y>=c.getY()-1 && !f1NaturalOrEmpty(old))return new F1Try(null,"PLAYER_OR_UNSAFE_BLOCK");
                if(y< c.getY()-1 && y>=naturalY && !f1NaturalOrEmpty(old))
                    return new F1Try(null,"FILL_OBSTRUCTION");
            }
            // Existing terrain is retained below the graded bed, but any
            // shallow exposed air pocket is supported with ordinary dirt.
            for(int y=naturalY;y<c.getY()-1;y++) {
                BlockPos p=new BlockPos(x,y,z);
                if(!f1PlanCell(level,p,Blocks.DIRT.defaultBlockState(),before,changes))
                    return new F1Try(null,"SUPPORT_REJECTED");
                fill++;
            }
            BlockPos ballast=new BlockPos(x,c.getY()-1,z);
            if(!f1PlanCell(level,ballast,Blocks.GRAVEL.defaultBlockState(),before,changes))
                return new F1Try(null,"BALLAST_REJECTED");
            // Clear the ENTIRE seven-wide / five-high corridor, not just the
            // centre rail column. Only non-fluid natural or replaceable cells.
            for(int y=c.getY();y<=c.getY()+4;y++) {
                BlockPos p=new BlockPos(x,y,z);
                BlockState old=level.getBlockState(p);
                if(!old.isAir()) {
                    if(!f1PlanCell(level,p,Blocks.AIR.defaultBlockState(),before,changes))
                        return new F1Try(null,"CLEARANCE_REJECTED");
                    if(y<naturalY)cut++;
                }
            }
        }
        BlockState ordinary=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE,alongX?RailShape.EAST_WEST:RailShape.NORTH_SOUTH);
        // DEV50f1c: the station is a SAVED MARKER separate from the rail
        // surface; an unpowered track_station physically stops locomotives.
        // A through-line must consist of NORMAL rails end-to-end.
        for(int d=-half;d<=half;d++){
            BlockPos p=c.offset(alongX?d:0,0,alongX?0:d);
            if(!f1PlanCell(level,p,ordinary,before,changes))
                return new F1Try(null,"RAIL_REJECTED");
        }
        if(changes.size()>2300)return new F1Try(null,"CHANGE_LIMIT");
        // Player, villagers, mobs AND all other entities must remain outside
        // every cell we will replace at track height or higher.
        for(Entity entity:level.getEntitiesOfClass(Entity.class,
                new AABB(c.offset(alongX?-half-4:-4,-2,alongX?-4:-half-4),
                         c.offset(alongX?half+5:5,7,alongX?5:half+5)))){
            if(entity.isRemoved())continue;
            AABB body=entity.getBoundingBox();
            for(BlockPos target:changes.keySet()){
                if(target.getY()>=c.getY() && body.intersects(new AABB(target)))
                    return new F1Try(null,"ENTITY_INTERSECTION");
            }
        }
        return new F1Try(new F1Proposal(village,c,alongX,length,cut,fill,changes.size(),ticks,
                java.util.Collections.unmodifiableMap(before),
                java.util.Collections.unmodifiableMap(changes)),null);
    }
    private static F1Try f1Find(ServerLevel level,BlockPos village){
        return f1Find(level,village,false,false);
    }
    private static F1Try f1Find(ServerLevel level,BlockPos village,boolean buildStation,boolean abandoned){
        Map<String,Integer> failed=new LinkedHashMap<>();
        int examined=0;
        int[] lengths=buildStation?new int[]{F1R15_NEW_STATION_RAILS}:F1_LENGTHS;
        int[] radii=buildStation?F1D_NEAR_RADII:F1_RADII;
        int[] lateral=buildStation?F1D_LATERAL:F1_LATERAL;
        for(int length:lengths)for(int r:radii)for(int cardinal=0;cardinal<4;cardinal++)
            for(int offset:lateral)for(int yShift:new int[]{0,1,-1}) {
                int dx=cardinal==0?r:cardinal==1?-r:offset;
                int dz=cardinal==2?r:cardinal==3?-r:offset;
                if(cardinal<=1)dz=offset;else dx=offset;
                int x=village.getX()+dx,z=village.getZ()+dz;
                BlockPos sample=new BlockPos(x,village.getY(),z);
                if(!level.hasChunkAt(sample)){failed.merge("CHUNK_UNLOADED",1,Integer::sum);continue;}
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+yShift;
                boolean tangent=cardinal<=1?false:true;
                if(buildStation && examined>=160){
                    LOG.info(F1_LOG+" NEAR_SEARCH_CAP village={} examined={} noChanges=true",village,examined);
                    return new F1Try(null,"NEAR_SEARCH_LIMIT_160");
                }
                F1Try result=f1Try(level,village,new BlockPos(x,y,z),tangent,length);
                if(result.proposal()!=null && buildStation)
                    result=f1dDecorate(level,result.proposal(),abandoned);
                examined++;
                if(result.proposal()!=null){
                    LOG.info(F1_LOG+" SELECT village={} center={} orientation={} rails={} cut={} fill={} changes={} attempts={} failed={} nearbyStationCandidate={}",
                            village,result.proposal().center(),tangent?"X":"Z",length,
                            result.proposal().cutBlocks(),result.proposal().fillBlocks(),
                            result.proposal().changeCount(),examined,failed,buildStation);
                    return result;
                }
                failed.merge(result.reason(),1,Integer::sum);
            }
        LOG.info(F1_LOG+" NO_SITE village={} attempts={} topReasons={} construction=NO",village,examined,failed);
        return new F1Try(null,"NO_SAFE_STRAIGHT_SITE: "+failed);
    }
    private static int f1Railway(CommandSourceStack src,boolean place){
        if (PLAYER_OPERATED_STATIONS_ONLY && place) {src.sendFailure(Component.literal("Experimental railway corridor construction retired."));return 0;}
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50f1 test-only railway placement allowed ONLY in "
                    +DEV50E2_TEST_WORLD+". No changes."));return 0;
        }
        if(!f1aAuditedThisSession){
            src.sendFailure(Component.literal("DEV50f1b SAFETY GATE: First run "
                    +"/tcrailway village railway siteaudit in this session. "
                    +"Prior failed site remains quarantined; no changes."));return 0;
        }
        BlockPos player=BlockPos.containing(src.getPosition());
        VillageProbe found=inspectVillageForPilot(level,player,128);
        if(found.center().isEmpty()){
            src.sendFailure(Component.literal("No confirmed, loaded natural vanilla village nearby. No changes."));return 0;
        }
        BlockPos village=found.center().get();
        if(!place){
            f1Preview=null;
            F1Try foundSite=f1Find(level,village);
            if(foundSite.proposal()==null){
                src.sendFailure(Component.literal("No safe straight corridor found: "+foundSite.reason()+
                        ". No changes; DO NOT force."));return 0;
            }
            f1Preview=foundSite.proposal();
            F1Proposal p=f1Preview;
            src.sendSuccess(()->Component.literal("[F1 PREVIEW] Real native straight rail stop at "+p.center().toShortString()
                    +" axis="+(p.alongX()?"X":"Z")+" rails="+p.length()
                    +" groundChanges="+p.changeCount()+" cut="+p.cutBlocks()+" fill="+p.fillBlocks()
                    +" normalRailsOnly=true stationMarker=SEPARATE; run /tcrailway village railway place within 2 minutes"),false);
            LOG.info(F1_LOG+" PREVIEW village={} center={} rails={} axis={} cut={} fill={} blocksPlanned={} "
                    +"normalRailsOnly=true stationMarker=SAVED_ONLY noSpecialStopRail=true "
                    +"noNativeCurves=true noNativeSlopes=true noTrains=true worldChanged=false",
                    p.village(),p.center(),p.length(),p.alongX()?"X":"Z",
                    p.cutBlocks(),p.fillBlocks(),p.changeCount());
            return 1;
        }
        F1Proposal p=f1Preview;
        if(p==null||!p.village().equals(village)||ticks-p.createdTick()>2400||ticks<p.createdTick()){
            f1Preview=null;
            src.sendFailure(Component.literal("No current preview for this village (2-minute expiry). No changes."));return 0;
        }
        // Preview is single-use. A failed recheck does not authorize retrying
        // against an unverified changed world: a fresh preview is mandatory.
        f1Preview=null;
        F1Try again=f1Try(level,p.village(),p.center(),p.alongX(),p.length());
        if(again.proposal()==null || !again.proposal().changes().equals(p.changes())
                || !again.proposal().before().equals(p.before())) {
            src.sendFailure(Component.literal("Railway changed since preview: "
                    +(again.reason()==null?"DIFFERENT_BLOCKS":again.reason())+". No changes."));return 0;
        }
        for(var entry:p.before().entrySet()){
            if(!level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).equals(entry.getValue())){
                src.sendFailure(Component.literal("Original block changed: "+entry.getKey().toShortString()+". No changes."));return 0;
            }
        }
        try {
            // Transaction stages: supports, cut clearance, gravel, rails.
            // Verify resulting block states; setBlock(false) alone is not an error.
            f1aApply(level,p);
            if(f1cVerifyThroughLine(level,p)!=p.length())
                throw new IllegalStateException("NORMAL_RAIL_THROUGH_AUDIT_FAILED at "+p.center());
            f1LastPlaced=p;
            f1cMarkers(level).add(p.village(),p.center());
            LOG.info(F1_LOG+" PLACED village={} center={} rails={} axis={} changed={} cut={} fill={} "
                    +"nativeStraight=true normalRailsVerified={} specialStationRails=0 stationMarker=SAVED_ONLY "
                    +"nativeCurve=false nativeSlope=false trainService=false "
                    +"testWorldOnly=true construction=EXPLICIT_MANUAL",
                    village,p.center(),p.length(),p.alongX()?"X":"Z",p.changeCount(),p.cutBlocks(),p.fillBlocks(),p.length());
            src.sendSuccess(()->Component.literal("[F1 PLACED] Native straight rail stop built at "+p.center().toShortString()
                    +" with "+p.length()+" NORMAL rails; no station stop rail. No curves, AI, train or network generated. See latest.log."),true);
            return 1;
        }catch(RuntimeException error){
            boolean restored=f1aRestore(level,p,"PLACE_FAILURE");
            LOG.error(F1_LOG+" ROLLBACK village="+village+" fullRestore="+restored,error);
            src.sendFailure(Component.literal("Placement FAILED; rollback "+(restored?"verified":"INCOMPLETE")
                    +". Do not retry; send logs."));return 0;
        }
    }

    // Same-session undo for an exact, unchanged DEV50f1 manual placement.
    // Rebooting does not carry a risky block-state snapshot across sessions.
    private static int f1Undo(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level) || f1LastPlaced==null){
            src.sendFailure(Component.literal("No same-session DEV50f1 build to undo in the disposable test world."));return 0;
        }
        F1Proposal p=f1LastPlaced;
        for(var entry:p.changes().entrySet()){
            BlockPos at=entry.getKey();
            if(!level.hasChunkAt(at) || !level.getBlockState(at).equals(entry.getValue())){
                src.sendFailure(Component.literal("F1 undo SAFETY STOP: changed rail/earthwork at "
                        +at.toShortString()+"; no changes."));return 0;
            }
        }
        for(Entity entity:level.getEntitiesOfClass(Entity.class,
                new AABB(p.center().offset(p.alongX()?-p.length()/2-4:-4,-2,
                                p.alongX()?-4:-p.length()/2-4),
                         p.center().offset(p.alongX()?p.length()/2+5:5,7,
                                p.alongX()?5:p.length()/2+5)))){
            if(entity.isRemoved())continue;
            for(BlockPos changed:p.changes().keySet()){
                if(changed.getY()>=p.center().getY()
                        && entity.getBoundingBox().intersects(new AABB(changed))){
                    src.sendFailure(Component.literal("F1 undo blocked by entity at "
                            +entity.blockPosition().toShortString()+"; no changes."));return 0;
                }
            }
        }
        boolean pass=f1aRestore(level,p,"OPERATOR_UNDO");
        if(!pass){
            LOG.error(F1_LOG+" UNDO_FAILURE site={} noFurtherAutomaticActions=true",p.center());
            src.sendFailure(Component.literal("DEV50f1 undo incomplete. Stop and send logs."));return 0;
        }
        f1LastPlaced=null;
        f1cMarkers(level).removeExact(p.village(),p.center());
        LOG.info(F1_LOG+" UNDO_OK site={} restored={} testWorldOnly=true",p.center(),p.before().size());
        src.sendSuccess(()->Component.literal("DEV50f1 same-session rail stop restored to prebuild blocks."),true);
        return 1;
    }

    // DEV50f1c: station identity stored separately. Never add a control
    // track_station to an unrestricted route or infer stop policy from a rail.
    private static final class F1cMarkerData extends SavedData {
        private final Map<Long,Long> byVillage=new LinkedHashMap<>();
        static F1cMarkerData load(CompoundTag tag) {
            F1cMarkerData data=new F1cMarkerData();
            long[] villages=tag.getLongArray("VillageKeys");
            long[] centers=tag.getLongArray("StopCenters");
            if(villages.length==centers.length) {
                for(int i=0;i<villages.length;i++)
                    data.byVillage.put(villages[i],centers[i]);
            }
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            long[] villages=new long[byVillage.size()];
            long[] centers=new long[byVillage.size()];
            int i=0;
            for(var entry:byVillage.entrySet()) {
                villages[i]=entry.getKey();centers[i]=entry.getValue();i++;
            }
            tag.putLongArray("VillageKeys",villages);
            tag.putLongArray("StopCenters",centers);
            return tag;
        }
        boolean has(BlockPos village) {return byVillage.containsKey(village.asLong());}
        boolean near(BlockPos village,int radius) {
            for(long key:byVillage.keySet()) {
                BlockPos candidate=BlockPos.of(key);
                long dx=(long)candidate.getX()-village.getX();
                long dz=(long)candidate.getZ()-village.getZ();
                if(dx*dx+dz*dz < (long)radius*radius) return true;
            }
            return false;
        }
        void add(BlockPos village,BlockPos center) {
            byVillage.put(village.asLong(),center.asLong());setDirty();
        }
        void removeExact(BlockPos village,BlockPos center) {
            if(byVillage.getOrDefault(village.asLong(),Long.MIN_VALUE)==center.asLong()) {
                byVillage.remove(village.asLong());setDirty();
            }
        }
        int count(){return byVillage.size();}
    }
    private static F1cMarkerData f1cMarkers(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(F1cMarkerData::load,F1cMarkerData::new,F1C_MARKER_SAVE);
    }
    // Positive verification after the ordinary block-state POST_AUDIT:
    // exactly the named native normal rails all the way through center.
    private static int f1cVerifyThroughLine(ServerLevel level,F1Proposal p) {
        int valid=0;
        RailShape expected=p.alongX()?RailShape.EAST_WEST:RailShape.NORTH_SOUTH;
        for(int d=-p.length()/2;d<=p.length()/2;d++) {
            BlockPos at=p.center().offset(p.alongX()?d:0,0,p.alongX()?0:d);
            if(!level.hasChunkAt(at))return -1;
            BlockState state=level.getBlockState(at);
            if(!state.is(TCBlocks.TRACK_NORMAL.get()) || !state.getValue(RailBlock.SHAPE).equals(expected))
                return valid;
            valid++;
        }
        return valid;
    }
    // Read-only detection of F1b-era straight stops which predate the new
    // persistent marker. Probe only the 140 known placement anchors, not all
    // nearby terrain. Five contiguous rails distinguish a full line from an
    // isolated decorative/control block. Missing chunks are never loaded.
    private static boolean f1cPreviouslyConstructedRailNearby(ServerLevel level,BlockPos village) {
        for(int r:F1_RADII)for(int cardinal=0;cardinal<4;cardinal++)
            for(int lateral:F1_LATERAL) {
                int x=village.getX()+(cardinal==0?r:cardinal==1?-r:lateral);
                int z=village.getZ()+(cardinal==2?r:cardinal==3?-r:lateral);
                if(cardinal<=1)z=village.getZ()+lateral;
                else x=village.getX()+lateral;
                boolean alongX=cardinal>1;
                BlockPos sample=new BlockPos(x,village.getY(),z);
                if(!level.hasChunkAt(sample))continue;
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int shift=-2;shift<=2;shift++) {
                    int contiguous=0;
                    for(int d=-2;d<=2;d++) {
                        BlockPos at=new BlockPos(x+(alongX?d:0),y+shift,z+(alongX?0:d));
                        if(!level.hasChunkAt(at))break;
                        BlockState block=level.getBlockState(at);
                        if(block.is(TCBlocks.TRACK_NORMAL.get())
                                || block.is(TCBlocks.TRACK_STATION.get()))contiguous++;
                        else break;
                    }
                    if(contiguous==5)return true;
                }
            }
        return false;
    }
    // Persist both selected and deliberately skipped villages. It prevents
    // multiple nearby fresh chunks from rerolling a village on reload.
    private static final String F1D_POLICY_SAVE="tafaricraft_dev50f1d_village_station_policy";
    private static final class F1dPolicyData extends SavedData {
        private final Map<Long,Boolean> chosen=new LinkedHashMap<>();
        // Persist final safe-search exhaustion, including across fresh nearby
        // chunk notifications, so one village is NOT searched over and over.
        private final Set<Long> exhausted=new LinkedHashSet<>();
        static F1dPolicyData load(CompoundTag tag){
            F1dPolicyData d=new F1dPolicyData();
            long[] keys=tag.getLongArray("VillageKeys");
            long[] values=tag.getLongArray("Selected");
            if(keys.length==values.length)for(int i=0;i<keys.length;i++)
                d.chosen.put(keys[i],values[i]!=0L);
            for(long key:tag.getLongArray("Dev50f1fExhaustedVillages"))d.exhausted.add(key);
            return d;
        }
        @Override public CompoundTag save(CompoundTag tag){
            long[] keys=new long[chosen.size()],values=new long[chosen.size()];
            int n=0;for(var e:chosen.entrySet()){
                keys[n]=e.getKey();values[n]=e.getValue()?1:0;n++;
            }
            tag.putLongArray("VillageKeys",keys);
            tag.putLongArray("Selected",values);
            long[] done=new long[exhausted.size()];
            int j=0;for(long key:exhausted)done[j++]=key;
            tag.putLongArray("Dev50f1fExhaustedVillages",done);
            return tag;
        }
        boolean hasExact(BlockPos p){return chosen.containsKey(p.asLong());}
        boolean isSelected(BlockPos p){return chosen.getOrDefault(p.asLong(),false);}
        boolean hasNearby(BlockPos p,int radius){
            long rr=(long)radius*radius;
            for(long key:chosen.keySet()){
                BlockPos prev=BlockPos.of(key);
                long dx=prev.getX()-p.getX(),dz=prev.getZ()-p.getZ();
                if(dx*dx+dz*dz<rr)return true;
            }
            return false;
        }
        void record(BlockPos p,boolean selected){chosen.put(p.asLong(),selected);setDirty();}
        boolean exhausted(BlockPos p){return exhausted.contains(p.asLong());}
        void exhaust(BlockPos p){if(exhausted.add(p.asLong()))setDirty();}
        int exhaustedCount(){return exhausted.size();}
        int count(){return chosen.size();}
    }
    private static F1dPolicyData f1dPolicy(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(
                F1dPolicyData::load,F1dPolicyData::new,F1D_POLICY_SAVE);
    }
    // DEV50f1d - deterministic percentage gates. No RNG consumption from the
    // global server/world RNG, hence repeated chunk notifications or restarts
    // cannot change a village's eligibility or abandoned status.
    private static long f1dMix(long a){
        a^=a>>>30; a*=0xbf58476d1ce4e5b9L;
        a^=a>>>27; a*=0x94d049bb133111ebL;
        return a^(a>>>31);
    }
    private static int f1dPercentRoll(ServerLevel level,BlockPos village,long salt){
        return (int)Math.floorMod(f1dMix(level.getSeed()^village.asLong()^salt),100L);
    }
    private static String f1dStyle(ServerLevel level,BlockPos village){
        String name=level.getBiome(village).unwrapKey()
                .map(key->key.location().getPath()).orElse("plains");
        if(name.contains("desert"))return "DESERT";
        if(name.contains("savanna"))return "SAVANNA";
        if(name.contains("snowy")||name.contains("ice"))return "SNOWY";
        if(name.contains("taiga"))return "TAIGA";
        return "PLAINS";
    }
    private record F1dPalette(BlockState floor,BlockState wall,BlockState roof,
                              BlockState platform,BlockState window) {}
    private static F1dPalette f1dPalette(String style,boolean abandoned){
        BlockState floor=Blocks.OAK_PLANKS.defaultBlockState();
        BlockState wall=Blocks.OAK_PLANKS.defaultBlockState();
        BlockState roof=Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState platform=Blocks.STONE_BRICKS.defaultBlockState();
        switch(style){
            case "DESERT" -> {
                floor=Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                wall=Blocks.SANDSTONE.defaultBlockState();
                roof=Blocks.CUT_SANDSTONE.defaultBlockState();
                platform=Blocks.SANDSTONE.defaultBlockState();
            }
            case "SAVANNA" -> {
                floor=Blocks.ACACIA_PLANKS.defaultBlockState();
                wall=Blocks.ACACIA_PLANKS.defaultBlockState();
                roof=Blocks.TERRACOTTA.defaultBlockState();
            }
            case "TAIGA" -> {
                floor=Blocks.SPRUCE_PLANKS.defaultBlockState();
                wall=Blocks.SPRUCE_PLANKS.defaultBlockState();
                roof=Blocks.COBBLESTONE.defaultBlockState();
            }
            case "SNOWY" -> {
                floor=Blocks.SPRUCE_PLANKS.defaultBlockState();
                wall=Blocks.STONE_BRICKS.defaultBlockState();
                roof=Blocks.SPRUCE_PLANKS.defaultBlockState();
            }
            default -> {}
        }
        if(abandoned){
            if(style.equals("DESERT"))wall=Blocks.CHISELED_SANDSTONE.defaultBlockState();
            else wall=Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            platform=Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return new F1dPalette(floor,wall,roof,platform,Blocks.GLASS.defaultBlockState());
    }
    private static BlockPos f1dAt(F1Proposal p,int along,int side){
        int towardVillage=p.alongX()
                ?Integer.compare(p.village().getZ(),p.center().getZ())
                :Integer.compare(p.village().getX(),p.center().getX());
        if(towardVillage==0)towardVillage=1;
        return p.center().offset(p.alongX()?along:side*towardVillage,0,
                                 p.alongX()?side*towardVillage:along);
    }
    // DEV50f1g: TEN genuinely different above-ground footprints, deterministic
    // per world seed + village anchor. This is not a new spawn chance roll:
    // 35% village chance and 20% conditional abandoned chance stay independent.
    // Every design fits the SAME guarded 15x12 building-side strip; no larger
    // track corridor, no world edits outside the accepted transaction.
    private enum F1eDesign {
        WAYSIDE, DEPOT, COVERED_HALT, WATCH_TOWER,
        LONGHOUSE, L_WING, TWIN_LODGE, TICKET_KIOSK,
        ARCADE, GABLE_HALL
    }
    private static F1eDesign f1eDesign(ServerLevel level,BlockPos village) {
        F1eDesign[] values=F1eDesign.values();
        return values[f1dPercentRoll(level,village,0x35A9C71DD26B4E08L)%values.length];
    }
    private static BlockState f1ePost(String style) {
        return switch(style) {
            case "DESERT" -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case "SAVANNA" -> Blocks.ACACIA_LOG.defaultBlockState();
            case "TAIGA", "SNOWY" -> Blocks.SPRUCE_LOG.defaultBlockState();
            default -> Blocks.OAK_LOG.defaultBlockState();
        };
    }
    private static BlockState f1eRoofTrim(String style) {
        return switch(style) {
            case "DESERT" -> Blocks.CUT_SANDSTONE.defaultBlockState();
            case "SAVANNA" -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case "TAIGA" -> Blocks.COBBLESTONE.defaultBlockState();
            case "SNOWY" -> Blocks.STONE_BRICKS.defaultBlockState();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
    }
    private static boolean f1eHut(F1eDesign design,int along,int side) {
        int a=Math.abs(along);
        return switch(design) {
            case WAYSIDE -> a<=3 && side>=8 && side<=12;
            case DEPOT -> a<=5 && side>=7 && side<=12;
            case COVERED_HALT -> a<=2 && side>=9 && side<=12;
            case WATCH_TOWER -> a<=2 && side>=8 && side<=12;
            case LONGHOUSE -> a<=6 && side>=9 && side<=11;
            case L_WING -> (a<=4 && side>=10 && side<=12)
                    ||(along>=-4 && along<=0 && side>=8 && side<=11);
            case TWIN_LODGE -> ((along>=-6 && along<=-2)
                    ||(along>=2 && along<=6)) && side>=9 && side<=12;
            case TICKET_KIOSK -> a<=1 && side>=10 && side<=12;
            case ARCADE -> a<=6 && side>=9 && side<=12;
            case GABLE_HALL -> a<=4 && side>=8 && side<=12;
        };
    }
    private static boolean f1eRoof(F1eDesign design,int along,int side) {
        int a=Math.abs(along);
        return switch(design) {
            case WAYSIDE -> a<=4 && side>=7 && side<=13;
            case DEPOT -> a<=6 && side>=6 && side<=13;
            case COVERED_HALT -> a<=3 && side>=8 && side<=13;
            case WATCH_TOWER -> a<=3 && side>=7 && side<=13;
            case LONGHOUSE -> a<=7 && side>=8 && side<=12;
            case L_WING -> (a<=5 && side>=9 && side<=13)
                    ||(along>=-5 && along<=1 && side>=7 && side<=12);
            case TWIN_LODGE -> ((along>=-7 && along<=-1)
                    ||(along>=1 && along<=7)) && side>=8 && side<=13;
            case TICKET_KIOSK -> a<=2 && side>=9 && side<=13;
            case ARCADE -> a<=7 && side>=8 && side<=13;
            case GABLE_HALL -> a<=5 && side>=7 && side<=13;
        };
    }
    private static int f1eFront(F1eDesign design) {
        return switch(design) {
            case DEPOT -> 7;
            case COVERED_HALT, LONGHOUSE, TWIN_LODGE, ARCADE -> 9;
            case TICKET_KIOSK -> 10;
            default -> 8;
        };
    }
    private static int f1eHalf(F1eDesign design) {
        return switch(design) {
            case WAYSIDE -> 3;
            case DEPOT -> 5;
            case LONGHOUSE, ARCADE -> 6;
            case L_WING, GABLE_HALL -> 4;
            case TWIN_LODGE -> 6;
            case TICKET_KIOSK -> 1;
            default -> 2;
        };
    }
    private static boolean f1gDoor(F1eDesign design,int along,int side) {
        if(side!=f1eFront(design))return false;
        return switch(design) {
            case TWIN_LODGE -> along==-4 || along==4;
            case L_WING -> along==-2;
            default -> along==0;
        };
    }
    private static boolean f1gCanopy(F1eDesign design,int along,int side){
        int a=Math.abs(along);
        return switch(design){
            case COVERED_HALT -> a<=7 && side>=4 && side<=7;
            case TICKET_KIOSK -> a<=6 && side>=5 && side<=8;
            case ARCADE -> a<=6 && side==7;
            default -> false;
        };
    }
    private static boolean f1gRidge(F1eDesign design,int along,int side){
        int a=Math.abs(along);
        return switch(design){
            case WAYSIDE -> a<=3 && side>=9 && side<=11;
            case DEPOT -> a<=5 && side>=9 && side<=10;
            case WATCH_TOWER -> a<=1 && side>=9 && side<=11;
            case LONGHOUSE -> a<=6 && side==10;
            case L_WING -> (a<=4 && side==11)
                    ||(along>=-4 && along<=0 && side==9);
            case TWIN_LODGE -> ((along>=-6 && along<=-2)
                    ||(along>=2 && along<=6)) && side==10;
            case ARCADE -> a<=6 && side>=10 && side<=11;
            case GABLE_HALL -> a<=4 && side>=9 && side<=11;
            default -> false;
        };
    }
    private static int f1gRoofLevel(F1eDesign design){
        return switch(design){
            case WATCH_TOWER -> 6;
            case LONGHOUSE, GABLE_HALL -> 5;
            case TICKET_KIOSK -> 3;
            default -> 4;
        };
    }
    // Underground styles are intentionally SCHEMATICS FOR THE READ-ONLY
    // feasibility command. They never get added to f1dDecorate's surface
    // world-edit transaction, never tunnel and never spawn train entities.
    private enum F1gSubwayDesign { ISLAND_PLATFORM, SIDE_PLATFORMS, VAULTED_HALL }
    private static F1gSubwayDesign f1gSubwayDesign(ServerLevel level,BlockPos at){
        F1gSubwayDesign[] all=F1gSubwayDesign.values();
        return all[f1dPercentRoll(level,at,0x14E2C99A7B54151DL)%all.length];
    }
    private static int f1gSubwayPreview(CommandSourceStack src){
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)){
            src.sendFailure(Component.literal("Subway preview is READ-ONLY and restricted to DEV50E6 Terrain Profile Test."));
            return 0;
        }
        BlockPos origin=BlockPos.containing(src.getPosition());
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){
            src.sendSuccess(()->Component.literal("[F1G SUBWAY] LOW_MEMORY: survey skipped. No changes."),false);
            return 1;
        }
        // Read-only; no tickets or forced chunk loads. Check a 9x17 candidate
        // plus a buffer before examining the blocks / heightmap.
        for(int dx=-6;dx<=6;dx+=4)for(int dz=-10;dz<=10;dz+=4)
            if(!level.hasChunkAt(origin.offset(dx,0,dz))){
                src.sendSuccess(()->Component.literal("[F1G SUBWAY] DEFERRED_UNLOADED. Read-only; 0 blocks changed."),false);
                LOG.info(F1_LOG+" SUBWAY_SURVEY pos={} verdict=UNLOADED noChunksForced=true blocksChanged=0",origin);
                return 1;
            }
        int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                origin.getX(),origin.getZ());
        int bottom=surface-15;
        if(bottom<level.getMinBuildHeight()+8){
            src.sendSuccess(()->Component.literal("[F1G SUBWAY] REJECT_DEPTH: insufficient underground space. No changes."),false);
            return 1;
        }
        int wet=0,caves=0,blockEntities=0,structure=0,samples=0;
        // Room envelope ONLY: 9 wide x 17 long x 6 high. This is not yet
        // shaft/track/tunnel certification; no excavation may be enabled.
        for(int dx=-4;dx<=4;dx++)for(int dz=-8;dz<=8;dz++)
            for(int dy=0;dy<6;dy++){
                BlockPos p=new BlockPos(origin.getX()+dx,bottom+dy,origin.getZ()+dz);
                BlockState block=level.getBlockState(p);
                samples++;
                if(!block.getFluidState().isEmpty())wet++;
                if(block.isAir())caves++;
                if(level.getBlockEntity(p)!=null)blockEntities++;
                if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())structure++;
            }
        String verdict=(wet>0||blockEntities>0||structure>0)?"REJECT_PROTECTED_OR_WET"
                :caves>0?"NEEDS_CAVE_GEOMETRY_REVIEW":"ROOM_CLEARANCE_SURVEY_ONLY";
        final String result="[F1G SUBWAY] "+verdict+" variant="+f1gSubwayDesign(level,origin)
                +" candidate="+origin.getX()+","+bottom+","+origin.getZ()
                +" samples="+samples+" fluids="+wet+" caves="+caves
                +" tileEntities="+blockEntities+" villagePieces="+structure
                +" entrances/tunnels/railPorts=UNVERIFIED generation=DISABLED changes=0";
        LOG.info(F1_LOG+" SUBWAY_SURVEY {}",result);
        src.sendSuccess(()->Component.literal(result),false);
        return 1;
    }
    // DEV51B-R13: accepted DEV51B R10/R11 desert pedestrian geometry,
    // generalized to NEW stations. The saved 64-edit reference is a SPECIMEN,
    // NOT a command to replay absolute XYZs into an arbitrary village.
    // Only the fresh F1d preview transaction can invoke this; no retrofits.
    // Do not assume existing hut footprints are vacant: the walk loop is
    // OUTSIDE the ten approved hut envelopes and native rail corridor.
    private static String dev51bR13PlanDesertApproach(ServerLevel level,F1Proposal base,
            Map<BlockPos,BlockState> before,Map<BlockPos,BlockState> changes) {
        final int railY=base.center().getY(),floorY=railY-1;
        int columns=0,looseCut=0;
        for(int along=-9;along<=9;along++)for(int side=4;side<=15;side++) {
            // Two-block-wide approaches at BOTH platform ends, joined by
            // a two-wide outer walk beyond the furthest building roof.
            if(Math.abs(along)<8 && side<14)continue;
            BlockPos column=f1dAt(base,along,side);
            if(!level.hasChunkAt(column))return "R13_CHUNK_UNLOADED";
            if(f1aOverlapsQuarantine(column,base.alongX(),1))return "R13_QUARANTINE";
            long dx=(long)column.getX()-base.village().getX();
            long dz=(long)column.getZ()-base.village().getZ();
            if(dx*dx+dz*dz<40L*40L)return "R13_NEAR_VILLAGE_CORE";
            int groundY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    column.getX(),column.getZ());
            if(groundY<floorY-2||groundY>floorY+2)return "R13_GRADE_OVER_TWO";
            // The path's footprint must be solid and natural (or empty above
            // ground). Reject protected structures, tiles, fluids, other
            // stations and unowned custom blocks rather than destroying them.
            BlockPos support=column.atY(floorY-3);
            if(!level.hasChunkAt(support))return "R13_SUPPORT_UNLOADED";
            BlockState under=level.getBlockState(support);
            if(!under.getFluidState().isEmpty() || under.isAir()
                    || !f1NaturalOrEmpty(under) || level.getBlockEntity(support)!=null)
                return "R13_UNSAFE_FOUNDATION";
            for(int y=floorY-2;y<=railY+3;y++){
                BlockPos at=column.atY(y);
                if(!level.hasChunkAt(at))return "R13_COLUMN_UNLOADED";
                if(level.structureManager().getStructureWithPieceAt(at,StructureTags.VILLAGE).isValid())
                    return "R13_VILLAGE_STRUCTURE";
                BlockState old=level.getBlockState(at);
                if(!old.getFluidState().isEmpty()||level.getBlockEntity(at)!=null)
                    return "R13_FLUID_OR_BLOCK_ENTITY";
                if(!f1NaturalOrEmpty(old) && !before.containsKey(at))
                    return "R13_NON_NATURAL_BLOCK";
                if(y==railY+3&&!old.isAir())return "R13_OVERHEAD_OBSTRUCTION";
            }
            // Fill shallow depressions and any one-cell void immediately
            // under the new pedestrian floor. The y=floorY-3 witness must
            // already be solid natural terrain, so no floating sidewalks.
            BlockPos shallow=column.atY(floorY-2);
            if(level.getBlockState(shallow).isAir()
                    && !f1PlanCell(level,shallow,Blocks.DIRT.defaultBlockState(),before,changes))
                return "R13_SHALLOW_VOID_REJECTED";
            // The old state of each target is recorded by f1PlanCell.
            for(int y=groundY;y<floorY;y++)
                if(!f1PlanCell(level,column.atY(y),Blocks.DIRT.defaultBlockState(),before,changes))
                    return "R13_SUPPORT_REJECTED";
            // The accepted desert template uses sandstone near the platform
            // and ordinary sand on the outer shoulder, NEVER raised slabs or
            // stairs across the pedestrian entrance.
            BlockState surface=side<=12?Blocks.SANDSTONE.defaultBlockState()
                    :Blocks.SAND.defaultBlockState();
            if(!f1PlanCell(level,column.atY(floorY),surface,before,changes))
                return "R13_SURFACE_REJECTED";
            for(int y=railY;y<=railY+2;y++){
                BlockPos head=column.atY(y);
                if(!level.getBlockState(head).isAir())looseCut++;
                if(!f1PlanCell(level,head,Blocks.AIR.defaultBlockState(),before,changes))
                    return "R13_HEADROOM_REJECTED";
            }
            columns++;
        }
        LOG.info("[TC-DEV51B-R13] DESERT_APPROACH_PLAN center={} columns={} looseHeadroom={}"
                +" normalRailCorridorUntouched=true manualReferenceEdits=64"
                +" exactBeforeStatesRecorded=true newStationOnly=true worldEdits=0",
                base.center(),columns,looseCut);
        return null;
    }

    // DEV51B R16: a proposed compact desert station is not safe merely
    // because the 17-rail bed is flat. The R15 photographed failure was a
    // trench directly against station walls that R15's rail cut/fill counters
    // did not include. Grade a guarded, walkable two-step shoulder AROUND the
    // new building shell. No reference-world, existing-station or service edit.
    // All cells are exact-previewed, exact-preimage-checked and undoable using
    // the unchanged f1dDecorate/F1H transaction. Fail closed on structures,
    // block entities, fluids, exposed caves, old player blocks and steep sand.
    private static String dev51bR16DesertPerimeter(ServerLevel level,F1Proposal base,
            F1eDesign design,Map<BlockPos,BlockState> before,
            Map<BlockPos,BlockState> changes){
        final int floorY=base.center().getY()-1,railY=floorY+1;
        int innerColumns=0,outerColumns=0,supportCells=0,clearedCells=0;
        for(int along=-9;along<=9;along++)for(int side=6;side<=15;side++){
            if(f1eHut(design,along,side))continue;
            boolean inner=f1eHut(design,along-1,side)
                    ||f1eHut(design,along+1,side)
                    ||f1eHut(design,along,side-1)
                    ||f1eHut(design,along,side+1);
            boolean outer=false;
            if(!inner)for(int da=-2;da<=2 && !outer;da++)
                for(int ds=-2;ds<=2;ds++)if(Math.abs(da)+Math.abs(ds)<=2
                        &&f1eHut(design,along+da,side+ds)){
                    outer=true;break;
                }
            if(!inner&&!outer)continue;
            BlockPos col=f1dAt(base,along,side);
            if(!level.hasChunkAt(col))return "R16_PERIMETER_CHUNK_UNLOADED";
            if(f1aOverlapsQuarantine(col,base.alongX(),1))return "R16_PERIMETER_QUARANTINE";
            long dx=(long)col.getX()-base.village().getX();
            long dz=(long)col.getZ()-base.village().getZ();
            if(dx*dx+dz*dz<40L*40L)return "R16_PERIMETER_VILLAGE_BUFFER";
            // The inner ring aligns with the building's finished floor, and
            // the outer ring is a single sand step lower. This stops a 2-3
            // deep moat from appearing against sandstone walls or doorways.
            int surfaceY=inner?floorY:floorY-1;
            int groundY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    col.getX(),col.getZ());
            if(groundY<surfaceY-2||groundY>surfaceY+2)
                return "R16_PERIMETER_GRADE_OVER_TWO";
            BlockPos witness=col.atY(surfaceY-3);
            if(!level.hasChunkAt(witness))return "R16_PERIMETER_SUPPORT_UNLOADED";
            BlockState baseState=level.getBlockState(witness);
            if(baseState.isAir()||!baseState.getFluidState().isEmpty()
                    ||!f1NaturalOrEmpty(baseState)||level.getBlockEntity(witness)!=null)
                return "R16_PERIMETER_UNSUPPORTED_GROUND";
            // Do not modify ANY user-built/protected terrain. Skip planned
            // building posts when clearing upper air so original architecture
            // remains intact; their foundation may still be made safe.
            for(int y=surfaceY-2;y<=railY+2;y++){
                BlockPos at=col.atY(y);
                if(!level.hasChunkAt(at))return "R16_PERIMETER_COLUMN_UNLOADED";
                if(level.structureManager().getStructureWithPieceAt(at,StructureTags.VILLAGE).isValid())
                    return "R16_PERIMETER_VILLAGE_STRUCTURE";
                BlockState old=level.getBlockState(at);
                if(!old.getFluidState().isEmpty()||level.getBlockEntity(at)!=null)
                    return "R16_PERIMETER_FLUID_OR_BLOCK_ENTITY";
                if(!f1NaturalOrEmpty(old))return "R16_PERIMETER_NON_NATURAL";
            }
            for(int y=groundY;y<surfaceY;y++){
                BlockPos at=col.atY(y);
                BlockState planned=changes.get(at);
                if(planned!=null&&!planned.equals(Blocks.DIRT.defaultBlockState())
                        &&!planned.equals(Blocks.SAND.defaultBlockState())
                        &&!planned.equals(Blocks.SANDSTONE.defaultBlockState()))
                    return "R16_PERIMETER_PROPOSED_SUPPORT_CONFLICT";
                if(!f1PlanCell(level,at,Blocks.DIRT.defaultBlockState(),before,changes))
                    return "R16_PERIMETER_SUPPORT_REJECTED";
                supportCells++;
            }
            BlockPos surface=col.atY(surfaceY);
            BlockState surfaceType=inner||side<=12?Blocks.SANDSTONE.defaultBlockState()
                    :Blocks.SAND.defaultBlockState();
            BlockState existingPlan=changes.get(surface);
            if(existingPlan!=null&&!existingPlan.equals(surfaceType)){
                // A legitimate, previously planned platform or hut floor
                // takes precedence. A non-floor piece here is unsafe.
                if(!existingPlan.equals(Blocks.SMOOTH_SANDSTONE.defaultBlockState()))
                    return "R16_PERIMETER_PROPOSED_SURFACE_CONFLICT";
            }else if(!f1PlanCell(level,surface,surfaceType,before,changes))
                return "R16_PERIMETER_SURFACE_REJECTED";
            for(int y=surfaceY+1;y<=railY+2;y++){
                BlockPos air=col.atY(y);
                BlockState proposed=changes.get(air);
                if(proposed!=null&&!proposed.isAir()){
                    // A canopy/porch pillar must never be erased to clear
                    // room; it was approved by the 10-design architect.
                    continue;
                }
                if(!level.getBlockState(air).isAir())clearedCells++;
                if(!f1PlanCell(level,air,Blocks.AIR.defaultBlockState(),before,changes))
                    return "R16_PERIMETER_HEADROOM_REJECTED";
            }
            if(inner)innerColumns++;else outerColumns++;
        }
        LOG.info("[TC-DEV51B-R16] PERIMETER_PLAN center={} design={}"
                +" inner={} outer={} supports={} cleared={} cutFillRailCountersSeparate=true"
                +" exactPreimages=true structureProtected=true railCorridorUntouched=true"
                +" newDesertOnly=true worldEdits=0",
                base.center(),design,innerColumns,outerColumns,supportCells,clearedCells);
        return null;
    }

    // DEV51B R17: photograph-driven follow-up to R16. R16 laid a solid
    // surface around the walls but some columns retained *air underneath*
    // and some outside edges still descended into 1-2 block pockets.
    // Reconcile the FINISHED R16+R13+station plan, never the raw terrain
    // independently. This helper has NO setBlock calls. It records every
    // filled cell via f1PlanCell and uses the existing exact-state preview,
    // place-time preimage verification and same-session undo. Do not touch
    // the accepted DEV50E6 reference structure, player objects, original
    // stations, rail bed or the designs of any of the ten station variants.
    private static String dev51bR17SealDesertEdges(ServerLevel level,F1Proposal base,
            F1eDesign design,Map<BlockPos,BlockState> before,
            Map<BlockPos,BlockState> changes){
        final int floorY=base.center().getY()-1;
        // A surface is only recognized if this very transaction explicitly
        // planned it, including when it equals the original block state.
        // Thus ordinary sand outside the actual platform/building/paths does
        // not silently become a new foundation or a construction target.
        Map<BlockPos,Integer> surfaced=new LinkedHashMap<>();
        for(int along=-10;along<=10;along++)for(int side=4;side<=16;side++){
            BlockPos col=f1dAt(base,along,side);
            BlockPos full=col.atY(floorY);
            BlockState fullState=changes.getOrDefault(full,level.getBlockState(full));
            if(before.containsKey(full)&&dev51bR17Paved(fullState)){
                surfaced.put(col,floorY);
                continue;
            }
            // R16's OUTER ring is one natural sand step lower.
            BlockPos lower=col.atY(floorY-1);
            BlockState lowState=changes.getOrDefault(lower,level.getBlockState(lower));
            if(before.containsKey(lower)&&dev51bR17Paved(lowState)
                    &&!f1eHut(design,along,side))surfaced.put(col,floorY-1);
        }
        if(surfaced.isEmpty())return "R17_NO_DESERT_STATION_FLOORS";
        int sealed=0,checked=0,outerScanned=0,outerFilled=0,softCleared=0;
        // Any planned floor must have a two-deep solid footing. R16 could
        // leave air beneath a surface because its fill loop only ran when
        // the ORIGINAL heightmap was lower than the finished floor.
        for(var entry:surfaced.entrySet()){
            BlockPos column=entry.getKey();int surfaceY=entry.getValue();
            if(!level.hasChunkAt(column))return "R17_FOOTING_UNLOADED";
            BlockPos witness=column.atY(surfaceY-3);
            if(!dev51bR17SafeNaturalCell(level,witness))
                return "R17_FOOTING_UNSAFE_WITNESS";
            BlockState under=level.getBlockState(witness);
            if(under.isAir()||under.canBeReplaced())return "R17_FOOTING_DEEP_VOID";
            for(int y=surfaceY-2;y<surfaceY;y++){
                BlockPos cell=column.atY(y);
                if(!dev51bR17SafeNaturalCell(level,cell))return "R17_FOOTING_PROTECTED";
                BlockState planned=changes.getOrDefault(cell,level.getBlockState(cell));
                if(planned.isAir()||planned.canBeReplaced()){
                    // Never overwrite a previously planned non-air station
                    // detail, protected player block or fluid. Only shallow
                    // natural voids may be sealed with sandstone subsoil.
                    if(changes.containsKey(cell)&&!changes.get(cell).isAir())
                        return "R17_FOOTING_PLAN_CONFLICT";
                    BlockState fill=Blocks.SANDSTONE.defaultBlockState();
                    if(!f1PlanCell(level,cell,fill,before,changes))
                        return "R17_FOOTING_PLAN_REJECTED";
                    sealed++;
                }else if(!f1NaturalOrEmpty(planned)){
                    // The original sandstone/sandstone-stair station footings
                    // are allowed only when explicitly part of this plan.
                    if(!before.containsKey(cell))return "R17_FOOTING_NON_NATURAL";
                }
            }
            checked++;
        }
        // Every exposed perimeter edge gets a read-only depth check.
        // Only a one-block safety shoulder is created for a low pocket,
        // limited to natural terrain within the original +/-2 rule.
        // Deep caves, changed material, structures, liquids or steep slopes
        // block the proposal rather than causing an unbounded fill/clear.
        for(var entry:surfaced.entrySet()){
            BlockPos start=entry.getKey();int currentY=entry.getValue();
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(Math.abs(dx)+Math.abs(dz)!=1)continue;
                BlockPos col=start.offset(dx,0,dz);
                int along=base.alongX()?col.getX()-base.center().getX()
                        :col.getZ()-base.center().getZ();
                int toward=base.alongX()
                        ?Integer.compare(base.village().getZ(),base.center().getZ())
                        :Integer.compare(base.village().getX(),base.center().getX());
                if(toward==0)toward=1;
                int side=base.alongX()?(col.getZ()-base.center().getZ())*toward
                        :(col.getX()-base.center().getX())*toward;
                if(along< -11||along>11||side<4||side>17)continue;
                if(surfaced.containsKey(col))continue;
                // A building or planned rail/canopy piece must never be
                // rewritten by an outside shoulder; these get validated by
                // the station's own earlier full 3D layout plan.
                if(f1eHut(design,along,side))return "R17_UNPLANNED_HUT_EDGE";
                if(!level.hasChunkAt(col))return "R17_EDGE_UNLOADED";
                int naturalTop=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        col.getX(),col.getZ());
                int safeShoulderY=currentY-1;
                if(naturalTop<safeShoulderY-1)
                    return "R17_EDGE_DEEP_HOLE";
                if(naturalTop>currentY+2)
                    return "R17_EDGE_STEEP_DUNE";
                outerScanned++;
                if(naturalTop>=currentY)continue;
                // The near-flat one-block margin may be filled, but it
                // cannot be extended indefinitely from this new surface:
                // only ORIGINAL planned floors seed the one-cell edge scan.
                long vx=(long)col.getX()-base.village().getX();
                long vz=(long)col.getZ()-base.village().getZ();
                if(vx*vx+vz*vz<40L*40L)return "R17_EDGE_VILLAGE_BUFFER";
                if(f1aOverlapsQuarantine(col,base.alongX(),1))
                    return "R17_EDGE_QUARANTINE";
                BlockPos witness=col.atY(safeShoulderY-2);
                if(!dev51bR17SafeNaturalCell(level,witness))
                    return "R17_EDGE_UNSAFE_WITNESS";
                BlockState support=level.getBlockState(witness);
                if(support.isAir()||support.canBeReplaced())
                    return "R17_EDGE_UNSUPPORTED";
                for(int y=safeShoulderY-1;y<=currentY+2;y++){
                    if(!dev51bR17SafeNaturalCell(level,col.atY(y)))
                        return "R17_EDGE_PROTECTED";
                }
                for(int y=safeShoulderY-1;y<safeShoulderY;y++){
                    BlockPos under=col.atY(y);
                    BlockState target=changes.getOrDefault(under,level.getBlockState(under));
                    if(target.isAir()||target.canBeReplaced()){
                        if(changes.containsKey(under))return "R17_EDGE_PLAN_CONFLICT";
                        if(!f1PlanCell(level,under,Blocks.SANDSTONE.defaultBlockState(),before,changes))
                            return "R17_EDGE_FILL_REJECTED";
                        outerFilled++;
                    }
                }
                BlockPos top=col.atY(safeShoulderY);
                // Do not override a previous hut/path or even an air plan.
                if(changes.containsKey(top))continue;
                if(!f1NaturalOrEmpty(level.getBlockState(top)))
                    return "R17_EDGE_UNSAFE_SURFACE";
                if(!f1PlanCell(level,top,Blocks.SAND.defaultBlockState(),before,changes))
                    return "R17_EDGE_SURFACE_REJECTED";
                outerFilled++;
                for(int y=safeShoulderY+1;y<=currentY+2;y++){
                    BlockPos head=col.atY(y);
                    if(changes.containsKey(head))continue;
                    BlockState old=level.getBlockState(head);
                    if(old.isAir())continue;
                    if(!f1NaturalOrEmpty(old))return "R17_EDGE_UNSAFE_HEADROOM";
                    // Safely clear only two blocks of ordinary loose cover.
                    if(y>safeShoulderY+2)return "R17_EDGE_TALL_OBSTRUCTION";
                    if(!f1PlanCell(level,head,Blocks.AIR.defaultBlockState(),before,changes))
                        return "R17_EDGE_CLEAR_REJECTED";
                    softCleared++;
                }
            }
        }
        LOG.info("[TC-DEV51B-R17] FOOTING_EDGE_PLAN center={} design={} pavedColumns={}"
                +" sealedShallowVoids={} rimChecks={} rimFillCells={} naturalHeadroom={}"
                +" noDeepCaveFills=true onlyNaturalBlocks=true railUntouched=true"
                +" originalStationUntouched=true exactPreimages=true worldEdits=0",
                base.center(),design,checked,sealed,outerScanned,outerFilled,softCleared);
        return null;
    }

    private static boolean dev51bR17Paved(BlockState state){
        return state.is(Blocks.SANDSTONE)||state.is(Blocks.SAND)
                ||state.is(Blocks.SMOOTH_SANDSTONE)||state.is(Blocks.CUT_SANDSTONE);
    }

    private static boolean dev51bR17SafeNaturalCell(ServerLevel level,BlockPos cell){
        if(!level.hasChunkAt(cell))return false;
        if(level.structureManager().getStructureWithPieceAt(cell,StructureTags.VILLAGE).isValid())
            return false;
        BlockState old=level.getBlockState(cell);
        return old.getFluidState().isEmpty()&&level.getBlockEntity(cell)==null
                &&f1NaturalOrEmpty(old);
    }

    private static F1Try f1dDecorate(ServerLevel level,F1Proposal base,boolean abandoned){
        String style=f1dStyle(level,base.village());
        F1eDesign design=f1eDesign(level,base.village());
        F1dPalette palette=f1dPalette(style,abandoned);
        BlockState post=f1ePost(style),trim=f1eRoofTrim(style);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>(base.before());
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>(base.changes());
        final int railY=base.center().getY(),floorY=railY-1;
        final int bodyTop=f1gRoofLevel(design);
        final int roofY=railY+f1gRoofLevel(design);
        // All designs fit in the same fixed 15 x 12 rectangle and carry the
        // ORIGINAL exact before-state / no-structure / no-player-block audit.
        // Only natural ground with <=2 blocks of grading is permitted.
        for(int along=-7;along<=7;along++)for(int side=2;side<=13;side++){
            int abs=Math.abs(along);
            boolean platform=side<=5;
            // F1N: make the actual doorway approach a protected, graded
            // walking surface too (including the asymmetric / twin doors).
            // This is part of the original exact-preview transaction.
            boolean walkway=side>=6 && side<f1eFront(design)
                    && (f1gDoor(design,along,f1eFront(design))
                        || (along==0 && side<=7));
            boolean hut=f1eHut(design,along,side);
            boolean roof=f1eRoof(design,along,side);
            boolean canopy=f1gCanopy(design,along,side);
            boolean porch=(design==F1eDesign.WAYSIDE&&side==7&&abs==3)
                    ||(design==F1eDesign.GABLE_HALL&&side==7&&abs==4);
            boolean depotAwning=(design==F1eDesign.DEPOT&&abs<=5&&side==6)
                    ||(design==F1eDesign.L_WING&&along>=-4&&along<=0&&side==7);
            if(!platform&&!walkway&&!hut&&!roof&&!canopy&&!porch&&!depotAwning)continue;
            BlockPos at=f1dAt(base,along,side);
            int x=at.getX(),z=at.getZ();
            if(!level.hasChunkAt(at))return new F1Try(null,"BUILDING_CHUNK_UNLOADED");
            if(f1aOverlapsQuarantine(at,base.alongX(),1))
                return new F1Try(null,"BUILDING_QUARANTINE");
            long dx=x-base.village().getX(),dz=z-base.village().getZ();
            if(dx*dx+dz*dz<40L*40L)return new F1Try(null,"BUILDING_NEAR_VILLAGE_CORE");
            int groundY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(groundY<floorY-2||groundY>floorY+2)return new F1Try(null,"BUILDING_TERRAIN_GRADE");
            for(int y=Math.min(groundY,floorY-2);y<=railY+9;y++){
                BlockPos cell=at.atY(y);
                if(!level.hasChunkAt(cell))return new F1Try(null,"BUILDING_CHUNK_UNLOADED");
                if(level.structureManager().getStructureWithPieceAt(cell,StructureTags.VILLAGE).isValid())
                    return new F1Try(null,"BUILDING_VILLAGE_STRUCTURE");
                BlockState existing=level.getBlockState(cell);
                if(!existing.getFluidState().isEmpty()||level.getBlockEntity(cell)!=null)
                    return new F1Try(null,"BUILDING_FLUID_OR_ENTITY");
                if(y>=floorY&&!f1NaturalOrEmpty(existing)&&!before.containsKey(cell))
                    return new F1Try(null,"BUILDING_UNSAFE_BLOCK");
            }
            if(platform||walkway||canopy){
                if(side>=4)for(int y=groundY;y<floorY;y++)
                    if(!f1PlanCell(level,at.atY(y),Blocks.DIRT.defaultBlockState(),before,changes))
                        return new F1Try(null,"PLATFORM_SUPPORT_REJECTED");
                if((platform||walkway)&&!f1PlanCell(level,at.atY(floorY),palette.platform(),before,changes))
                    return new F1Try(null,"PLATFORM_REJECTED");
                // F1N: the floor alone is insufficient when the natural sand
                // heightmap sits 1-2 blocks above it. Reserve three air cells
                // above every approved walking surface. The final bench and
                // architecture stages below may deliberately occupy cells;
                // every one remains in the same preimage / change map.
                if(platform||walkway)for(int y=railY;y<=railY+2;y++){
                    BlockPos head=at.atY(y);
                    if(!f1PlanCell(level,head,Blocks.AIR.defaultBlockState(),before,changes))
                        return new F1Try(null,"PLATFORM_HEADROOM_REJECTED");
                }
            }
            if(hut){
                for(int y=groundY;y<floorY;y++)
                    if(!f1PlanCell(level,at.atY(y),Blocks.DIRT.defaultBlockState(),before,changes))
                        return new F1Try(null,"HUT_SUPPORT_REJECTED");
                if(!f1PlanCell(level,at.atY(floorY),palette.floor(),before,changes))
                    return new F1Try(null,"HUT_FLOOR_REJECTED");
                int half=f1eHalf(design),front=f1eFront(design);
                for(int h=0;h<bodyTop;h++){
                    // New asymmetric floorplans use their own boundary; the
                    // original four keep the accepted rectangular outline.
                    boolean shaped=design==F1eDesign.L_WING||design==F1eDesign.TWIN_LODGE;
                    boolean west=!f1eHut(design,along-1,side);
                    boolean east=!f1eHut(design,along+1,side);
                    boolean near=!f1eHut(design,along,side-1);
                    boolean far=!f1eHut(design,along,side+1);
                    boolean outer=shaped?(west||east||near||far)
                            :(abs==half||side==front||side==12);
                    boolean doorway=f1gDoor(design,along,side)&&h<=1;
                    boolean window=(h==2||(design==F1eDesign.WATCH_TOWER&&h==4))
                            &&outer&&!doorway&&((along+side)%3==0);
                    boolean corner=shaped?((west||east)&&(near||far))
                            :(abs==half&&(side==front||side==12));
                    BlockState target=(!outer||doorway)?Blocks.AIR.defaultBlockState()
                            :window?palette.window():corner?post:palette.wall();
                    if(abandoned && outer&&!doorway&&!window&&!corner
                            && h==2 && (abs*3+side)%7==0)
                        target=Blocks.AIR.defaultBlockState();
                    if(!f1PlanCell(level,at.atY(railY+h),target,before,changes))
                        return new F1Try(null,"HUT_WALL_REJECTED");
                }
                if(abandoned && along==-1 && side==11)
                    if(!f1PlanCell(level,at.atY(railY+1),Blocks.COBWEB.defaultBlockState(),before,changes))
                        return new F1Try(null,"HUT_COBWEB_REJECTED");
            }
            // Distinct rooflines are real layered block geometry, not just
            // different colors. No beam, rail, switch or train code is touched.
            if(roof){
                if(!f1PlanCell(level,at.atY(roofY),palette.roof(),before,changes))
                    return new F1Try(null,"HUT_ROOF_REJECTED");
                boolean ridge=f1gRidge(design,along,side);
                if(ridge && !f1PlanCell(level,at.atY(roofY+1),trim,before,changes))
                    return new F1Try(null,"RIDGE_REJECTED");
            }
            if(canopy){
                if(!f1PlanCell(level,at.atY(railY+3),palette.roof(),before,changes))
                    return new F1Try(null,"HALT_CANOPY_REJECTED");
                if((abs==7||along==0)&&side==7
                        ||(design==F1eDesign.TICKET_KIOSK&&abs==6&&side==8)
                        ||(design==F1eDesign.ARCADE&&side==7&&along%3==0)){
                    for(int h=0;h<3;h++)
                        if(!f1PlanCell(level,at.atY(railY+h),post,before,changes))
                            return new F1Try(null,"HALT_PILLAR_REJECTED");
                }
            }
            if(porch){
                for(int h=0;h<3;h++)
                    if(!f1PlanCell(level,at.atY(railY+h),post,before,changes))
                        return new F1Try(null,"PORCH_POST_REJECTED");
            }
            if(depotAwning)
                if(!f1PlanCell(level,at.atY(railY+3),trim,before,changes))
                    return new F1Try(null,"DEPOT_AWNING_REJECTED");
            // The tower receives a second-story sill; wayside/depot/covered
            // layouts have no accidental high tower geometry.
            if(design==F1eDesign.WATCH_TOWER&&hut&&abs==2&&side==10)
                if(!f1PlanCell(level,at.atY(railY+3),trim,before,changes))
                    return new F1Try(null,"TOWER_BAND_REJECTED");
            // Put simple bench blocks at two platform ends; do not enter the
            // rail corridor. Abandoned benches become weathered stone.
            if(platform&&abs==6&&side==5)
                if(!f1PlanCell(level,at.atY(railY),abandoned?palette.platform():palette.floor(),before,changes))
                    return new F1Try(null,"PLATFORM_BENCH_REJECTED");
            if(roof||canopy||depotAwning){
                int occupiedTop=roof?roofY:railY+3;
                if(roof&&f1gRidge(design,along,side))occupiedTop=roofY+1;
                for(int y=occupiedTop+1;y<=railY+9;y++)
                    if(!level.getBlockState(at.atY(y)).isAir())
                        return new F1Try(null,"HUT_OVERHEAD_OBSTRUCTION");
            }
        }
        // R13 is DESERT, non-abandoned and NEW-BUILD only. The source's
        // exact original/target map, place-time preview recheck, entity
        // exclusions and same-session undo already govern these extra cells.
        if(style.equals("DESERT") && !abandoned){
            // R16 precedes the R13 outer footpath: the two safety plans then
            // share the same map, original-state checks and rollback journal.
            String perimeterFailure=dev51bR16DesertPerimeter(level,base,design,before,changes);
            if(perimeterFailure!=null)return new F1Try(null,perimeterFailure);
            String failure=dev51bR13PlanDesertApproach(level,base,before,changes);
            if(failure!=null)return new F1Try(null,failure);
            // R17 runs last, after all station/pillar/platform, perimeter,
            // and walkway surfaces have been decided. It only seals shallow
            // voids that would otherwise leave visible open trenches.
            String edgeFailure=dev51bR17SealDesertEdges(level,base,design,before,changes);
            if(edgeFailure!=null)return new F1Try(null,edgeFailure);
        }
        if(changes.size()>3500)return new F1Try(null,"STATION_CHANGE_LIMIT");
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(BlockPos cell:changes.keySet()){
            minX=Math.min(minX,cell.getX());minY=Math.min(minY,cell.getY());minZ=Math.min(minZ,cell.getZ());
            maxX=Math.max(maxX,cell.getX());maxY=Math.max(maxY,cell.getY());maxZ=Math.max(maxZ,cell.getZ());
        }
        for(Entity entity:level.getEntitiesOfClass(Entity.class,
                new AABB(minX-1,minY-1,minZ-1,maxX+2,maxY+2,maxZ+2))){
            if(entity.isRemoved())continue;
            for(BlockPos at:changes.keySet())
                if(at.getY()>=floorY && entity.getBoundingBox().intersects(new AABB(at)))
                    return new F1Try(null,"BUILDING_ENTITY_INTERSECTION");
        }
        return new F1Try(new F1Proposal(base.village(),base.center(),base.alongX(),
                base.length(),base.cutBlocks(),base.fillBlocks(),changes.size(),ticks,
                java.util.Collections.unmodifiableMap(before),
                java.util.Collections.unmodifiableMap(changes)),null);
    }
    // DEV50f1h: One deliberate surface station test at the OPERATOR-selected
    // coordinate, not an auto search. A test can bypass the village's normal
    // percentage roll, but never changes or rerolls its persistent policy.
    // Initial command is entirely read-only; no chunk tickets, no excavation.
    private static F1Proposal f1hPending;
    private static java.lang.ref.WeakReference<ServerLevel> f1hPendingLevel;
    private static F1Proposal f1hBuilt;
    private static java.lang.ref.WeakReference<ServerLevel> f1hBuiltLevel;
    private static long f1hLastPreviewTick=-100000L;
    private static final int F1H_MAX_CHANGES=1200;
    private static final long F1H_PREVIEW_TICKS=2400L;
    private static String f1hSummary(ServerLevel level,F1Proposal p,boolean abandoned){
        return "center="+p.center().toShortString()+" village="+p.village().toShortString()
                +" axis="+(p.alongX()?"X":"Z")+" rails="+p.length()
                +" design="+f1eDesign(level,p.village())
                +" biomeStyle="+f1dStyle(level,p.village())
                +" abandoned="+abandoned+" changed="+p.changeCount()
                +" cut="+p.cutBlocks()+" fill="+p.fillBlocks();
    }
    private static boolean f1hEligibleWorld(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel())){
            src.sendFailure(Component.literal("[F1H SAFETY STOP] Only in the exact disposable DEV50E6 Terrain Profile Test world. NO changes."));
            return false;
        }
        if(AUTOMATIC.get()&&F1C_AUTO_STRAIGHT.get()&&F1F_SAFE_AUTOMATIC.get()){
            src.sendFailure(Component.literal("[F1H SAFETY STOP] Disable automatic station construction first. No changes."));
            return false;
        }
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){
            src.sendFailure(Component.literal("[F1H SAFETY STOP] Java heap headroom under 768 MiB. No changes."));
            return false;
        }
        return true;
    }
    private static int f1hStationTestPreview(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        // Manual preview overrides a running read-only automatic site finder.
        f1iFinder=null;
        f1hPending=null;
        f1hPendingLevel=null;
        if(f1hBuilt!=null && f1hBuiltLevel!=null
                && f1hBuiltLevel.get()==src.getLevel()){
            src.sendFailure(Component.literal("[F1H] One station at a time. Undo the previous same-session test first. No changes."));return 0;
        }
        if(ticks-f1hLastPreviewTick<40){
            src.sendFailure(Component.literal("[F1H] Wait 2 seconds between previews to protect chunk loading. No changes."));return 0;
        }
        f1hLastPreviewTick=ticks;
        ServerLevel level=src.getLevel();
        BlockPos player=BlockPos.containing(src.getPosition());
        if(!level.hasChunkAt(player)){
            src.sendFailure(Component.literal("[F1H] Player chunk not loaded. No changes."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,player,128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[F1H] No confirmed loaded natural village within 128 blocks. No changes."));return 0;
        }
        BlockPos village=probe.center().get();
        long vx=(long)player.getX()-village.getX(),vz=(long)player.getZ()-village.getZ();
        long rr=vx*vx+vz*vz;
        if(rr<56L*56L||rr>104L*104L){
            src.sendFailure(Component.literal("[F1H] Fly above a flat site 56-104 blocks from village center; current horizontal distance squared="+rr+". No changes."));return 0;
        }
        if(f1cMarkers(level).near(village,ONE_PER_RADIUS.get())
                ||stations(level).near(village,ONE_PER_RADIUS.get())
                ||f1cPreviouslyConstructedRailNearby(level,village)){
            src.sendFailure(Component.literal("[F1H] Existing station/rail marker nearby. Choose another village. No changes."));return 0;
        }
        // Use the operator's X,Z, not a loop over remote terrain. No force
        // loads, and test preview never records a new natural-spawn roll.
        boolean alongX=Math.abs(vz)>=Math.abs(vx);
        if(!f1fChunksReady(level,player,alongX)){
            src.sendFailure(Component.literal("[F1H] Compact 17-rail+station footprint not loaded. Stay nearby until chunks settle. No changes."));return 0;
        }
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,player.getX(),player.getZ());
        if(src.getPosition().y<y+12){
            src.sendFailure(Component.literal("[F1H] Fly at least 12 blocks ABOVE the proposed ground so the player cannot intersect construction. No changes."));return 0;
        }
        boolean abandoned=f1dPercentRoll(level,village,0x69B7E41DA2CC05F1L)
                <F1D_ABANDONED_CHANCE.get();
        BlockPos center=new BlockPos(player.getX(),y,player.getZ());
        F1Try result=f1Try(level,village,center,alongX,F1R15_NEW_STATION_RAILS);
        if(result.proposal()!=null)result=f1dDecorate(level,result.proposal(),abandoned);
        if(result.proposal()==null){
            LOG.info(F1_LOG+" STATION_TEST_PREVIEW_REJECT village={} center={} reason={} blocksChanged=0 noForcedChunks=true",
                    village,center,result.reason());
            src.sendFailure(Component.literal("[F1H] Site rejected: "+result.reason()+". Move to a different flat location and preview again. No changes."));
            return 0;
        }
        F1Proposal p=result.proposal();
        if(p.length()!=F1R15_NEW_STATION_RAILS){
            src.sendFailure(Component.literal("[R15] Compact rail count mismatch. No changes."));return 0;
        }
        if(p.changeCount()>F1H_MAX_CHANGES){
            src.sendFailure(Component.literal("[F1H] Safe edit budget exceeded: "+p.changeCount()+" / 1200; no changes."));return 0;
        }
        f1hPending=p;
        f1hPendingLevel=new java.lang.ref.WeakReference<>(level);
        final String details=f1hSummary(level,p,abandoned);
        LOG.info("[TC-DEV51B-R15] COMPACT_STATION_PREVIEW railBlocks={} platformBlocks=15 extensionEnds=2 worldEdits=0",p.length());
        LOG.info("[TC-DEV51B-R16] SAFE_PERIMETER_PREVIEW station={} exactPreimages=true"
                +" terrainApron=PLANNED approval=MANUAL_ONLY worldEdits=0",p.center());
        LOG.info("[TC-DEV51B-R17] GUARDED_SURFACE_PREVIEW station={} railBlocks=17"
                +" platformBlocks=15 terrainAudit=FOOTING_AND_EDGE_PLAN exactPreimages=true"
                +" approval=MANUAL_ONLY worldEdits=0",p.center());
        LOG.info(F1_LOG+" STATION_TEST_PREVIEW {} forcedPreview=true naturalChancePolicyUnchanged=true noForcedChunks=true worldChanged=false subwayConstruction=DISABLED",details);
        src.sendSuccess(()->Component.literal("[F1H PREVIEW ONLY] "+details+". No blocks changed. Confirm within 2 min with /tcrailway village railway stationtestplace confirm; or /tcrailway village railway stationtestcancel."),false);
        return 1;
    }
    private static int f1hStationTestPlace(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        F1Proposal p=f1hPending;
        boolean sameWorld=f1hPendingLevel!=null && f1hPendingLevel.get()==level;
        // Single-use authorization; failures require a fresh read-only preview.
        f1hPending=null;
        f1hPendingLevel=null;
        if(!sameWorld || p==null || (p!=null && p.length()!=F1R15_NEW_STATION_RAILS) || ticks<p.createdTick() || ticks-p.createdTick()>F1H_PREVIEW_TICKS){
            src.sendFailure(Component.literal("[F1H] Preview missing/expired. Run stationtestpreview. No changes."));return 0;
        }
        if(!f1fChunksReady(level,p.center(),p.alongX())
                ||f1cMarkers(level).near(p.village(),ONE_PER_RADIUS.get())
                ||stations(level).near(p.village(),ONE_PER_RADIUS.get())
                ||f1cPreviouslyConstructedRailNearby(level,p.village())){
            src.sendFailure(Component.literal("[F1H] Chunks or station uniqueness changed. No changes."));return 0;
        }
        // Recompute the complete native track, platform and 10-design building
        // transaction, including player/entity and roof clearance, then compare
        // EVERY original cell and proposed edit to the previously viewed plan.
        boolean abandoned=f1dPercentRoll(level,p.village(),0x69B7E41DA2CC05F1L)
                <F1D_ABANDONED_CHANCE.get();
        F1Try current=f1Try(level,p.village(),p.center(),p.alongX(),F1R15_NEW_STATION_RAILS);
        if(current.proposal()!=null)current=f1dDecorate(level,current.proposal(),abandoned);
        if(current.proposal()==null || !current.proposal().changes().equals(p.changes())
                ||!current.proposal().before().equals(p.before())
                ||current.proposal().changeCount()>F1H_MAX_CHANGES){
            src.sendFailure(Component.literal("[F1H] Preview stale, unsafe or changed ("+(current.reason()==null?"PLAN_MISMATCH":current.reason())+"). No changes."));return 0;
        }
        for(var entry:p.before().entrySet()){
            if(!level.hasChunkAt(entry.getKey()) || !level.getBlockState(entry.getKey()).equals(entry.getValue())){
                src.sendFailure(Component.literal("[F1H] Original block changed at "+entry.getKey().toShortString()+". No changes."));return 0;
            }
        }
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){
            src.sendFailure(Component.literal("[F1H] Heap safety stop before placement. No changes."));return 0;
        }
        try {
            f1aApply(level,p);
            int rails=f1cVerifyThroughLine(level,p);
            if(rails!=F1R15_NEW_STATION_RAILS)throw new IllegalStateException("COMPACT_RAIL_POST_AUDIT="+rails);
            f1cMarkers(level).add(p.village(),p.center());
            f1hBuilt=p;
            f1hBuiltLevel=new java.lang.ref.WeakReference<>(level);
            final String details=f1hSummary(level,p,abandoned);
            LOG.info(F1_LOG+" STATION_TEST_PLACED {} railsVerified=17 platformBlocks=15 extensionEnds=2 stationStopRails=0 fullPreimageChecked=true surfaceArchitectures=10 subwayConstruction=DISABLED testWorldOnly=true",details);
            src.sendSuccess(()->Component.literal("[F1H PLACED] "+details+". All 17 normal rails verified; both ends can be extended. Inspect visually; same-session undo: /tcrailway village railway stationtestundo."),true);
            return 1;
        } catch(RuntimeException error){
            boolean restored=f1aRestore(level,p,"F1H_TEST_PLACE_FAILURE");
            f1cAutoStopped=true;
            LOG.error(F1_LOG+" STATION_TEST_ROLLBACK center="+p.center()+" restored="+restored+" noAutoRetry=true",error);
            src.sendFailure(Component.literal("[F1H FAILURE] Construction failed; rollback "+(restored?"verified":"INCOMPLETE")+". Stop and send logs."));
            return 0;
        }
    }
    private static int f1hStationTestCancel(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel())){
            src.sendFailure(Component.literal("[F1H] Test-world-only command."));return 0;
        }
        f1hPending=null;
        f1hPendingLevel=null;
        f1iFinder=null;
        src.sendSuccess(()->Component.literal("[F1J] Pending preview and site search canceled. No changes."),false);
        return 1;
    }
    private static int f1hStationTestUndo(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        // A separately confirmed access path must be undone first; do not
        // dismantle the station while leaving a path pointing into empty space.
        if(f1kBuilt!=null && f1kBuiltLevel!=null && f1kBuiltLevel.get()==level){
            src.sendFailure(Component.literal("[F1H] Undo stationaccessundo before removing its station. No changes."));return 0;
        }
        F1Proposal p=f1hBuilt;
        if(p==null || f1hBuiltLevel==null || f1hBuiltLevel.get()!=level){
            src.sendFailure(Component.literal("[F1H] No same-session station test to undo. No changes."));return 0;
        }
        // Require all recorded cells to match the exact post-build state,
        // including untouched preimage cells, so undo cannot overwrite later
        // player edits or natural neighbor changes beyond the proposed edits.
        for(var entry:p.before().entrySet()){
            BlockPos at=entry.getKey();
            BlockState expected=p.changes().getOrDefault(at,entry.getValue());
            if(!level.hasChunkAt(at) || !level.getBlockState(at).equals(expected)){
                src.sendFailure(Component.literal("[F1H] Undo unsafe: post-build cell changed at "+at.toShortString()+". No changes."));return 0;
            }
        }
        // Never restore underneath players/mobs in ANY building/platform cell.
        for(Entity entity:level.getEntitiesOfClass(Entity.class,
                new AABB(p.center()).inflate(44,13,44))){
            if(entity.isRemoved())continue;
            for(BlockPos at:p.changes().keySet()){
                if(at.getY()>=p.center().getY()-1 && entity.getBoundingBox().intersects(new AABB(at))){
                    src.sendFailure(Component.literal("[F1H] Undo blocked by entity at "+entity.blockPosition().toShortString()+". Move away. No changes."));return 0;
                }
            }
        }
        boolean restored=f1aRestore(level,p,"F1H_TEST_UNDO");
        if(!restored){
            f1cAutoStopped=true;
            LOG.error(F1_LOG+" STATION_TEST_UNDO_INCOMPLETE center={} action=STOP",p.center());
            src.sendFailure(Component.literal("[F1H] Undo INCOMPLETE. Stop and send logs."));return 0;
        }
        f1cMarkers(level).removeExact(p.village(),p.center());
        f1hBuilt=null;
        f1hBuiltLevel=null;
        LOG.info(F1_LOG+" STATION_TEST_UNDO_OK center={} restoredCells={} noAutoChanges=true",p.center(),p.before().size());
        src.sendSuccess(()->Component.literal("[F1H] Same-session surface station test restored and marker cleared."),true);
        return 1;
    }


    // DEV50f1m: guarded station-to-VANILLA-VILLAGE-PATH connection with
    // multiple exact platform exits and a read-only per-exit diagnostic. This feature can
    // safely assist a previously placed station WITHOUT rebuilding the station.
    // It is never invoked from a chunk event, worldgen or automatic placement.
    // A read-only route finder examines at most 3600 loaded surface cells;
    // construction needs a separate, exact-plan, two-minute confirmation.
    private static F1kTrail f1kPending;
    private static java.lang.ref.WeakReference<ServerLevel> f1kPendingLevel;
    private static F1kTrail f1kBuilt;
    private static java.lang.ref.WeakReference<ServerLevel> f1kBuiltLevel;
    private static long f1kLastPreviewTick=-100000L;
    private static final String F1M_LOG="[TC-DEV50F1M]";
    private static final int F1K_MAX_ROUTE_CELLS=3600;
    private static final int F1K_MAX_STEPS=144;
    private static final int F1K_MAX_EDITS=430;
    private record F1kStation(BlockPos village,BlockPos center,boolean alongX) {}
    private record F1kNode(int x,int z,int top,int parent,int steps,int cost,
                           int travelled,int lastDx,int lastDz) {}
    private record F1kTrail(F1Proposal edit,int tiles,BlockPos endpoint,
                            boolean meetsVillagePath,BlockPos villagePathBlock,
                            BlockState villagePathState,int stairCount,int explored,
                            BlockPos platformAccessBlock,BlockState platformAccessState,
                            int platformGrade) {}
    private record F1mEntrance(BlockPos platform,BlockState platformState,
                               int x,int z,int surfaceTop,int grade,String label) {}
    private record F1kResult(F1kTrail trail,String reason) {}

    private static F1kStation f1kNearbyStation(ServerLevel level,BlockPos player){
        F1kStation selected=null;
        long nearest=128L*128L;
        for(var entry:f1cMarkers(level).byVillage.entrySet()){
            BlockPos village=BlockPos.of(entry.getKey());
            BlockPos center=BlockPos.of(entry.getValue());
            long dx=(long)player.getX()-center.getX();
            long dz=(long)player.getZ()-center.getZ();
            long dist=dx*dx+dz*dz;
            if(dist>=nearest || !level.hasChunkAt(center))continue;
            // The saved marker alone does not prove an intact station:
            // check all 49 native normal rails and their shape in loaded chunks.
            F1Proposal x=new F1Proposal(village,center,true,49,0,0,0,ticks,Map.of(),Map.of());
            F1Proposal z=new F1Proposal(village,center,false,49,0,0,0,ticks,Map.of(),Map.of());
            boolean xOkay=f1cVerifyThroughLine(level,x)==49;
            boolean zOkay=f1cVerifyThroughLine(level,z)==49;
            if(xOkay==zOkay)continue;
            selected=new F1kStation(village,center,xOkay);
            nearest=dist;
        }
        return selected;
    }

    private static boolean f1kSurfaceAllowed(BlockState state){
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.TERRACOTTA);
    }
    private static BlockState f1kTrailMaterial(BlockState natural,String style){
        if(natural.is(Blocks.DIRT_PATH))return natural;
        if(natural.is(Blocks.SAND) || natural.is(Blocks.SANDSTONE))
            return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if(natural.is(Blocks.RED_SAND) || natural.is(Blocks.RED_SANDSTONE))
            return Blocks.CUT_RED_SANDSTONE.defaultBlockState();
        if("SNOWY".equals(style))return Blocks.COBBLESTONE.defaultBlockState();
        if(natural.is(Blocks.STONE) || natural.is(Blocks.GRAVEL))
            return Blocks.GRAVEL.defaultBlockState();
        return Blocks.DIRT_PATH.defaultBlockState();
    }
    private static int f1kAlong(F1kStation station,int x,int z){
        return station.alongX()?x-station.center().getX():z-station.center().getZ();
    }
    private static int f1kSide(F1kStation station,int x,int z){
        int sign=station.alongX()
                ?Integer.compare(station.village().getZ(),station.center().getZ())
                :Integer.compare(station.village().getX(),station.center().getX());
        if(sign==0)sign=1;
        return sign*(station.alongX()?z-station.center().getZ():x-station.center().getX());
    }
    private static int f1kWalkableHeight(ServerLevel level,F1kStation station,int x,int z){
        BlockPos candidate=new BlockPos(x,station.center().getY(),z);
        if(!level.hasChunkAt(candidate))return Integer.MIN_VALUE;
        if(x>=F1A_Q_MIN_X-2 && x<=F1A_Q_MAX_X+2
                && z>=F1A_Q_MIN_Z-2 && z<=F1A_Q_MAX_Z+2)return Integer.MIN_VALUE;
        int along=f1kAlong(station,x,z),side=f1kSide(station,x,z);
        // Never enter the rail envelope or the original 15x12 station strip.
        if(Math.abs(along)<=24 && Math.abs(side)<=3)return Integer.MIN_VALUE;
        if(Math.abs(along)<=7 && side>=2 && side<=13)return Integer.MIN_VALUE;
        long dx=(long)x-station.village().getX(),dz=(long)z-station.village().getZ();
        // Walking near the village is allowed only on natural cells outside
        // protected structures; a vanilla road is read as a separate, immutable
        // terminal below. Never carve through village pieces or any building.
        int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        if(top<level.getMinBuildHeight()+5 || top>level.getMaxBuildHeight()-5
                || Math.abs(top-station.center().getY())>11)return Integer.MIN_VALUE;
        BlockPos surface=new BlockPos(x,top-1,z);
        if(!level.hasChunkAt(surface))return Integer.MIN_VALUE;
        if(level.structureManager().getStructureWithPieceAt(surface,StructureTags.VILLAGE).isValid())
            return Integer.MIN_VALUE;
        BlockState original=level.getBlockState(surface);
        if(!f1kSurfaceAllowed(original) || original.is(Blocks.DIRT_PATH)
                || !original.getFluidState().isEmpty()
                || level.getBlockEntity(surface)!=null)return Integer.MIN_VALUE;
        for(int dy=0;dy<=2;dy++){
            BlockPos air=new BlockPos(x,top+dy,z);
            if(!level.hasChunkAt(air)
                    || level.structureManager().getStructureWithPieceAt(air,StructureTags.VILLAGE).isValid())
                return Integer.MIN_VALUE;
            BlockState state=level.getBlockState(air);
            if(!state.getFluidState().isEmpty() || level.getBlockEntity(air)!=null
                    || (!state.isAir()&&!state.canBeReplaced()))return Integer.MIN_VALUE;
        }
        return top;
    }
    private static boolean f1kEntitiesClear(ServerLevel level,Map<BlockPos,BlockState> changes){
        if(changes.isEmpty())return true;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(BlockPos p:changes.keySet()){
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
        }
        for(Entity entity:level.getEntitiesOfClass(Entity.class,
                new AABB(minX-1,minY-2,minZ-1,maxX+2,maxY+5,maxZ+2))){
            if(entity.isRemoved())continue;
            for(BlockPos p:changes.keySet()){
                if(entity.getBoundingBox().intersects(new AABB(p.getX(),p.getY(),p.getZ(),
                        p.getX()+1,p.getY()+3,p.getZ()+1)))return false;
            }
        }
        return true;
    }
    // A terminal is a genuine existing village path, with at least one more
    // neighboring path cell: this is NOT a mere village-distance milestone.
    // The village path itself and every structure piece remain read-only.
    private static BlockPos f1lVillagePathAnchor(ServerLevel level,F1kStation station,
                                                  int x,int z,int fromTop){
        BlockPos probe=new BlockPos(x,station.center().getY(),z);
        if(!level.hasChunkAt(probe))return null;
        if(x>=F1A_Q_MIN_X-2 && x<=F1A_Q_MAX_X+2
                && z>=F1A_Q_MIN_Z-2 && z<=F1A_Q_MAX_Z+2)return null;
        long vx=(long)x-station.village().getX(),vz=(long)z-station.village().getZ();
        // F1P: old F1M synthetic paths reached ~59 blocks from village center.
        // Treat only the core road as a safe village anchor; otherwise the
        // second search can misidentify an older station footpath as a road.
        // Prefer fail-closed NO_VERIFIED_VILLAGE_PATH over a false connection.
        if(vx*vx+vz*vz>40L*40L)return null;
        int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        // Existing village road may be at the same level or one above the
        // proposed approach. Descending onto a lower road would require
        // changing the road into a stair, which is not allowed.
        if(h<fromTop || h>fromTop+1 || Math.abs(h-station.center().getY())>11)return null;
        BlockPos road=new BlockPos(x,h-1,z);
        BlockState original=level.getBlockState(road);
        if(!original.is(Blocks.DIRT_PATH) || !original.getFluidState().isEmpty()
                || level.getBlockEntity(road)!=null)return null;
        for(int dy=1;dy<=2;dy++){
            BlockPos head=road.above(dy);
            if(!level.hasChunkAt(head) || !level.getBlockState(head).isAir())return null;
        }
        int connected=0;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int xx=x+d[0],zz=z+d[1];
            if(!level.hasChunkAt(new BlockPos(xx,h,zz)))continue;
            int hh=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,xx,zz);
            if(Math.abs(hh-h)>1)continue;
            if(level.getBlockState(new BlockPos(xx,hh-1,zz)).is(Blocks.DIRT_PATH))connected++;
        }
        return connected>0?road:null;
    }
    // Minecraft cannot walk up a one-block vertical soil ledge without a
    // step. Build a bottom-half staircase on the LOWER tile, facing the
    // HIGHER neighbor. If one lower tile needs contradictory stair facings,
    // reject the whole plan before any write.
    private static net.minecraft.core.Direction f1lFacing(F1kNode low,int highX,int highZ){
        if(highX>low.x())return net.minecraft.core.Direction.EAST;
        if(highX<low.x())return net.minecraft.core.Direction.WEST;
        if(highZ>low.z())return net.minecraft.core.Direction.SOUTH;
        return net.minecraft.core.Direction.NORTH;
    }
    private static BlockState f1lStair(BlockState natural,String style,
                                       net.minecraft.core.Direction facing){
        net.minecraft.world.level.block.Block stair=natural.is(Blocks.SAND)
                ||natural.is(Blocks.SANDSTONE)?Blocks.SANDSTONE_STAIRS:
                natural.is(Blocks.RED_SAND)||natural.is(Blocks.RED_SANDSTONE)
                    ?Blocks.RED_SANDSTONE_STAIRS:
                "SNOWY".equals(style)?Blocks.COBBLESTONE_STAIRS:
                natural.is(Blocks.STONE)||natural.is(Blocks.GRAVEL)
                    ?Blocks.STONE_BRICK_STAIRS:Blocks.COBBLESTONE_STAIRS;
        return stair.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING,facing)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF,
                        net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,
                        net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT);
    }
    // Read-only, exact geometry probing: two platform ends x two stone-platform
    // edge cells (side 4/5). The platform itself is immutable. The outside
    // tile can be at platform walking height, or ONE BELOW with a stair placed
    // only on the outside soil, facing the immutable platform edge.
    private static java.util.List<F1mEntrance> f1mEntrances(ServerLevel level,
                                                              F1kStation station){
        java.util.ArrayList<F1mEntrance> usable=new java.util.ArrayList<>();
        F1Proposal nativeLine=new F1Proposal(station.village(),station.center(),station.alongX(),
                49,0,0,0,ticks,Map.of(),Map.of());
        for(int end:new int[]{-1,1})for(int side:new int[]{4,5}){
            BlockPos platform=f1dAt(nativeLine,end*7,side);
            BlockPos floor=platform.atY(station.center().getY()-1);
            BlockPos outside=f1dAt(nativeLine,end*8,side);
            String label=(end<0?"NEG":"POS")+"_END_SIDE_"+side;
            String why=null;
            BlockState state=Blocks.AIR.defaultBlockState();
            int ht=Integer.MIN_VALUE;
            if(!level.hasChunkAt(floor)||!level.hasChunkAt(outside)){
                why="UNLOADED";
            }else{
                state=level.getBlockState(floor);
                if(!(state.is(Blocks.STONE_BRICKS)||state.is(Blocks.MOSSY_STONE_BRICKS)
                        ||state.is(Blocks.SANDSTONE))){
                    why="PLATFORM_FLOOR_NOT_FOUND";
                }else if(!level.getBlockState(floor.above()).isAir()
                        || !level.getBlockState(floor.above(2)).isAir()){
                    why="PLATFORM_ENTRANCE_OBSTRUCTED";
                }else{
                    ht=f1kWalkableHeight(level,station,outside.getX(),outside.getZ());
                    if(ht==Integer.MIN_VALUE)why="EXTERIOR_UNSAFE_OR_UNLOADED";
                    else if(ht>station.center().getY())why="EXTERIOR_ABOVE_PLATFORM";
                    else if(ht<station.center().getY()-1)why="EXTERIOR_TOO_LOW";
                }
            }
            if(why==null){
                int grade=station.center().getY()-ht;
                usable.add(new F1mEntrance(floor,state,outside.getX(),outside.getZ(),
                        ht,grade,label));
                LOG.info(F1M_LOG+" PLATFORM_EXIT_PROBE label={} platform={} outside={},{} platformWalkY={} outsideWalkY={} grade={} result=ACCEPT noChanges=true chunksForced=0",
                        label,floor,outside.getX(),outside.getZ(),station.center().getY(),ht,grade);
            }else{
                LOG.info(F1M_LOG+" PLATFORM_EXIT_PROBE label={} platform={} outside={},{} platformWalkY={} outsideWalkY={} reason={} noChanges=true chunksForced=0",
                        label,floor,outside.getX(),outside.getZ(),station.center().getY(),
                        ht==Integer.MIN_VALUE?"UNAVAILABLE":Integer.toString(ht),why);
            }
        }
        return usable;
    }
    private static F1kResult f1kPlan(ServerLevel level,F1kStation station){
        BlockPos center=station.center(),village=station.village();
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM)return new F1kResult(null,"LOW_MEMORY");
        java.util.List<F1mEntrance> entrances=f1mEntrances(level,station);
        if(entrances.isEmpty())return new F1kResult(null,"NO_SAFE_PLATFORM_ENTRY_CHECK_PLATFORM_EXIT_PROBES");
        java.util.ArrayList<F1kNode> nodes=new java.util.ArrayList<>();
        java.util.PriorityQueue<Integer> queue=new java.util.PriorityQueue<>(
                java.util.Comparator.comparingInt(i->nodes.get(i).cost()));
        java.util.HashMap<Long,Integer> best=new java.util.HashMap<>();
        java.util.HashMap<Long,F1mEntrance> starts=new java.util.HashMap<>();
        // All accepted exterior tiles share one bounded search budget. Never
        // trigger separate 3600-cell searches for four different entrances.
        for(F1mEntrance entrance:entrances){
            long key=ChunkPos.asLong(entrance.x(),entrance.z());
            // F1P: separate accumulated movement cost from the admissible
            // distance hint. Previously the score forgot previous turns and
            // elevation costs, allowing zig-zags to dominate a direct route.
            int cost=(Math.abs(entrance.x()-village.getX())
                    +Math.abs(entrance.z()-village.getZ()))*4 + entrance.grade()*14;
            Integer old=best.get(key);
            if(old!=null && nodes.get(old).cost()<=cost)continue;
            int id=nodes.size();
            nodes.add(new F1kNode(entrance.x(),entrance.z(),entrance.surfaceTop(),-1,1,cost,entrance.grade()*14,0,0));
            best.put(key,id);starts.put(key,entrance);queue.add(id);
        }
        int finish=-1,expanded=0;
        BlockPos roadAnchor=null;
        int[][] steps={{1,0},{-1,0},{0,1},{0,-1}};
        while(!queue.isEmpty() && expanded<F1K_MAX_ROUTE_CELLS){
            int id=queue.remove();
            F1kNode n=nodes.get(id);
            long key=ChunkPos.asLong(n.x(),n.z());
            if(best.getOrDefault(key,Integer.MAX_VALUE)!=id)continue;
            expanded++;
            // Do not stop simply because the route approaches the village.
            // Prove it actually meets an extant connected dirt-path cell.
            if(n.steps()>=8){
                for(int[] d:steps){
                    BlockPos found=f1lVillagePathAnchor(level,station,
                            n.x()+d[0],n.z()+d[1],n.top());
                    if(found!=null){finish=id;roadAnchor=found;break;}
                }
                if(finish>=0)break;
            }
            if(n.steps()>=F1K_MAX_STEPS)continue;
            for(int[] step:steps){
                int nx=n.x()+step[0],nz=n.z()+step[1];
                int ht=f1kWalkableHeight(level,station,nx,nz);
                if(ht==Integer.MIN_VALUE || Math.abs(ht-n.top())>1)continue;
                long hash=ChunkPos.asLong(nx,nz);
                int grade=Math.abs(ht-n.top());
                int distance=Math.abs(nx-village.getX())+Math.abs(nz-village.getZ());
                // F1P: a truly accumulated route cost. A turn is allowed but
                // costs more than a straight step; modest, safe single-step
                // elevation changes are cheaper than long sideways detours.
                // Hard limits (one-block grades, loaded chunks, safe surfaces)
                // are unchanged.  Do NOT excavate/force an unsafe shortcut.
                boolean turning=(n.lastDx()!=0||n.lastDz()!=0)
                        &&(n.lastDx()!=step[0]||n.lastDz()!=step[1]);
                int oldDistance=Math.abs(n.x()-village.getX())
                        +Math.abs(n.z()-village.getZ());
                int travelled=n.travelled()+12+grade*14+(turning?19:0)
                        +(distance>oldDistance?8:0);
                int nextScore=travelled+distance*4;
                Integer previous=best.get(hash);
                if(previous!=null && nodes.get(previous).cost()<=nextScore)continue;
                int ni=nodes.size();
                nodes.add(new F1kNode(nx,nz,ht,id,n.steps()+1,nextScore,travelled,step[0],step[1]));
                queue.add(ni);best.put(hash,ni);
            }
        }
        if(finish<0 || roadAnchor==null)
            return new F1kResult(null,"NO_VERIFIED_VILLAGE_PATH_WITHIN_BOUNDED_SEARCH");
        java.util.ArrayList<F1kNode> route=new java.util.ArrayList<>();
        for(int i=finish;i>=0;i=nodes.get(i).parent())route.add(nodes.get(i));
        java.util.Collections.reverse(route);
        F1kNode beginning=route.get(0);
        F1mEntrance entrance=starts.get(ChunkPos.asLong(beginning.x(),beginning.z()));
        if(entrance==null)return new F1kResult(null,"PLATFORM_ENTRY_LOST");
        F1kNode end=route.get(route.size()-1);
        int roadTop=roadAnchor.getY()+1;
        java.util.HashMap<BlockPos,net.minecraft.core.Direction> stairFaces=new java.util.HashMap<>();
        if(entrance.grade()==1){
            BlockPos outsideFloor=new BlockPos(beginning.x(),beginning.top(),beginning.z());
            stairFaces.put(outsideFloor,f1lFacing(beginning,
                    entrance.platform().getX(),entrance.platform().getZ()));
        }
        for(int i=1;i<route.size();i++){
            F1kNode prev=route.get(i-1),next=route.get(i);
            if(prev.top()==next.top())continue;
            F1kNode low=prev.top()<next.top()?prev:next;
            F1kNode high=prev.top()<next.top()?next:prev;
            BlockPos p=new BlockPos(low.x(),low.top(),low.z());
            var facing=f1lFacing(low,high.x(),high.z());
            var oldFace=stairFaces.putIfAbsent(p,facing);
            if(oldFace!=null && oldFace!=facing)
                return new F1kResult(null,"CONFLICTING_SLOPE_STAIRS");
        }
        if(roadTop==end.top()+1){
            BlockPos p=new BlockPos(end.x(),end.top(),end.z());
            var facing=f1lFacing(end,roadAnchor.getX(),roadAnchor.getZ());
            var oldFace=stairFaces.putIfAbsent(p,facing);
            if(oldFace!=null && oldFace!=facing)
                return new F1kResult(null,"CONFLICTING_LAST_STAIR");
        }
        // The existing village road is NEVER in this proposed write map.
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        String style=f1dStyle(level,village);
        for(F1kNode n:route){
            BlockPos floor=new BlockPos(n.x(),n.top()-1,n.z());
            BlockState natural=level.getBlockState(floor);
            // F1O: keep the lower soil/path floor as a support. A stair placed
            // in this block is embedded one block too low. The stair itself
            // belongs in the AIR CELL AT walking height (n.top()), rising to
            // the adjacent higher tile. Preview/undo record both exact cells.
            if(!f1PlanCell(level,floor,f1kTrailMaterial(natural,style),before,changes))
                return new F1kResult(null,"FOOTPATH_FLOOR_UNSAFE");
            for(int y=n.top();y<=n.top()+2;y++){
                BlockPos plant=new BlockPos(n.x(),y,n.z());
                var facing=stairFaces.get(plant);
                if(facing!=null){
                    BlockState state=level.getBlockState(plant);
                    if(!state.isAir()&&!state.canBeReplaced())
                        return new F1kResult(null,"STAIR_HEAD_CELL_OCCUPIED");
                    if(!f1PlanCell(level,plant,f1lStair(natural,style,facing),before,changes))
                        return new F1kResult(null,"FOOTPATH_STAIR_UNSAFE");
                }else if(!level.getBlockState(plant).isAir()
                        && !f1PlanCell(level,plant,Blocks.AIR.defaultBlockState(),before,changes))
                    return new F1kResult(null,"FOOTPATH_CLEARANCE_UNSAFE");
            }
        }
        if(changes.isEmpty() || changes.size()>F1K_MAX_EDITS)
            return new F1kResult(null,"FOOTPATH_EDIT_LIMIT_OR_EMPTY");
        if(!f1kEntitiesClear(level,changes))return new F1kResult(null,"ENTITY_ON_FOOTPATH");
        F1Proposal edit=new F1Proposal(village,center,station.alongX(),route.size(),0,0,
                changes.size(),ticks,java.util.Collections.unmodifiableMap(before),
                java.util.Collections.unmodifiableMap(changes));
        BlockState anchored=level.getBlockState(roadAnchor);
        if(!anchored.is(Blocks.DIRT_PATH) || changes.containsKey(roadAnchor))
            return new F1kResult(null,"VILLAGE_ROAD_ANCHOR_CHANGED");
        if(changes.containsKey(entrance.platform())
                || !level.getBlockState(entrance.platform()).equals(entrance.platformState()))
            return new F1kResult(null,"PLATFORM_ENTRY_CHANGED");
        return new F1kResult(new F1kTrail(edit,route.size(),
                new BlockPos(end.x(),end.top(),end.z()),true,
                roadAnchor,anchored,stairFaces.size(),expanded,
                entrance.platform(),entrance.platformState(),entrance.grade()),null);
    }
    private static int f1kSideSign(F1kStation s){
        int direction=s.alongX()
                ?Integer.compare(s.village().getZ(),s.center().getZ())
                :Integer.compare(s.village().getX(),s.center().getX());
        return direction==0?1:direction;
    }
    // DEV50f1p: read-only geometry audit for a saved station. It must never
    // mutate an existing footpath; older path cells have no durable block
    // preimage and must not be automatically deleted/rerouted.
    private static int f1pPathAudit(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        F1kStation station=f1kNearbyStation(level,BlockPos.containing(src.getPosition()));
        if(station==null){
            src.sendFailure(Component.literal("[F1P] No intact saved 49-rail station in loaded chunks."));return 0;
        }
        F1kResult result=f1kPlan(level,station);
        if(result.trail()==null){
            final String reason=result.reason();
            LOG.info("[TC-DEV50F1P] DIRECT_PATH_AUDIT station={} result={} edits=0 chunksForced=0",
                    station.center(),reason);
            src.sendFailure(Component.literal("[F1P PATH AUDIT] No NEW safe direct route: "+reason
                    +". Old walkway untouched."));return 0;
        }
        F1kTrail route=result.trail();
        LOG.info("[TC-DEV50F1P] DIRECT_PATH_AUDIT station={} routeTiles={} stairs={} explored={} edits={} villageRoad={} result=READ_ONLY oldWalkwayUntouched=true chunksForced=0",
                station.center(),route.tiles(),route.stairCount(),route.explored(),
                route.edit().changeCount(),route.villagePathBlock());
        src.sendSuccess(()->Component.literal("[F1P PATH AUDIT] New-route candidate="
                +route.tiles()+" tiles, stairs="+route.stairCount()+", prospective edits="
                +route.edit().changeCount()+", joins village at "
                +route.villagePathBlock().toShortString()+". NO changes."),false);
        return 1;
    }
    private static int f1kAccessPreview(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        if(f1kBuilt!=null && f1kBuiltLevel!=null && f1kBuiltLevel.get()==level){
            src.sendFailure(Component.literal("[F1M] A station access test is already present this session. Use stationaccessundo first."));return 0;
        }
        if(ticks-f1kLastPreviewTick<40){
            src.sendFailure(Component.literal("[F1M] Wait 2 seconds before retrying. No changes."));return 0;
        }
        f1kLastPreviewTick=ticks;
        f1kPending=null;f1kPendingLevel=null;
        F1kStation station=f1kNearbyStation(level,BlockPos.containing(src.getPosition()));
        if(station==null){
            src.sendFailure(Component.literal("[F1M] No verified, already-built, saved 49-rail station within 128 blocks. No changes."));return 0;
        }
        F1kResult result=f1kPlan(level,station);
        if(result.trail()==null){
            final String reason=result.reason();
            LOG.info(F1M_LOG+" VILLAGE_PATH_REJECT station={} reason={} noChanges=true noForcedChunks=true",station.center(),reason);
            src.sendFailure(Component.literal("[F1M] Access path not safe: "+reason+". No changes."));return 0;
        }
        f1kPending=result.trail();f1kPendingLevel=new java.lang.ref.WeakReference<>(level);
        F1kTrail trail=result.trail();
        LOG.info(F1M_LOG+" VILLAGE_PATH_PREVIEW station={} tiles={} end={} villagePath={} joinsExistingVillagePath=true platformEntry={} platformGrade={} stairs={} explored={} edits={} construction=NO worldChanged=false chunksForced=0",
                station.center(),trail.tiles(),trail.endpoint(),trail.villagePathBlock(),
                trail.platformAccessBlock(),trail.platformGrade(),
                trail.stairCount(),trail.explored(),trail.edit().changeCount());
        src.sendSuccess(()->Component.literal("[F1M VILLAGE PATH PREVIEW] tiles="+trail.tiles()
                +" approachEnd="+trail.endpoint().toShortString()
                +" villageRoad="+trail.villagePathBlock().toShortString()
                +" joinsExistingVillagePath=true platformEntry="+trail.platformAccessBlock().toShortString()
                +" platformGrade="+trail.platformGrade()
                +" stairs="+trail.stairCount()
                +" edits="+trail.edit().changeCount()
                +". READ-ONLY. Inspect and confirm within two minutes with /tcrailway village railway stationaccessplace confirm."),false);
        return 1;
    }
    private static int f1kAccessPlace(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        F1kTrail plan=f1kPending;
        boolean same=plan!=null && f1kPendingLevel!=null && f1kPendingLevel.get()==level;
        f1kPending=null;f1kPendingLevel=null;
        if(!same || ticks<plan.edit().createdTick() || ticks-plan.edit().createdTick()>2400){
            src.sendFailure(Component.literal("[F1M] Access preview missing or expired. No changes."));return 0;
        }
        F1Proposal old=plan.edit();
        F1kStation station=f1kNearbyStation(level,old.center());
        if(station==null || !station.center().equals(old.center())
                || !station.village().equals(old.village()) || station.alongX()!=old.alongX()){
            src.sendFailure(Component.literal("[F1M] Saved station/49 rails changed. No changes."));return 0;
        }
        F1kResult recheck=f1kPlan(level,station);
        if(recheck.trail()==null || !recheck.trail().edit().before().equals(old.before())
                || !recheck.trail().edit().changes().equals(old.changes())
                || recheck.trail().tiles()!=plan.tiles()
                || !recheck.trail().villagePathBlock().equals(plan.villagePathBlock())
                || !recheck.trail().villagePathState().equals(plan.villagePathState())
                || recheck.trail().stairCount()!=plan.stairCount()
                || !recheck.trail().platformAccessBlock().equals(plan.platformAccessBlock())
                || !recheck.trail().platformAccessState().equals(plan.platformAccessState())
                || recheck.trail().platformGrade()!=plan.platformGrade()) {
            src.sendFailure(Component.literal("[F1M] Footpath preview changed or is unsafe. No changes."));return 0;
        }
        for(var entry:old.before().entrySet()){
            if(!level.hasChunkAt(entry.getKey()) || !level.getBlockState(entry.getKey()).equals(entry.getValue())){
                src.sendFailure(Component.literal("[F1M] Block changed after preview. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,old.changes())){
            src.sendFailure(Component.literal("[F1M] Entity intersecting proposed access path. No changes."));return 0;
        }
        if(!level.hasChunkAt(plan.villagePathBlock())
                || !level.getBlockState(plan.villagePathBlock()).equals(plan.villagePathState())
                || old.changes().containsKey(plan.villagePathBlock())
                || !level.hasChunkAt(plan.platformAccessBlock())
                || !level.getBlockState(plan.platformAccessBlock()).equals(plan.platformAccessState())
                || old.changes().containsKey(plan.platformAccessBlock())){
            src.sendFailure(Component.literal("[F1M] Village road or platform entry changed or is in the write plan. No changes."));return 0;
        }
        try {
            f1aApply(level,old);
            F1Proposal nativeLine=new F1Proposal(station.village(),station.center(),station.alongX(),49,
                    0,0,0,ticks,Map.of(),Map.of());
            if(f1cVerifyThroughLine(level,nativeLine)!=49)
                throw new IllegalStateException("STATION_RAIL_POST_AUDIT_FAILED");
            if(!level.hasChunkAt(plan.villagePathBlock())
                    || !level.getBlockState(plan.villagePathBlock()).equals(plan.villagePathState()))
                throw new IllegalStateException("VILLAGE_ROAD_CHANGED_AFTER_BUILD");
            if(!level.hasChunkAt(plan.platformAccessBlock())
                    || !level.getBlockState(plan.platformAccessBlock()).equals(plan.platformAccessState()))
                throw new IllegalStateException("PLATFORM_ENTRY_CHANGED_AFTER_BUILD");
            f1kBuilt=plan;f1kBuiltLevel=new java.lang.ref.WeakReference<>(level);
            LOG.info(F1M_LOG+" VILLAGE_PATH_PLACED station={} tiles={} end={} villagePath={} platformEntry={} platformGrade={} stairs={} edits={} connected=true villageRoadUnchanged=true railsVerified=49 allCellsAudited=true",
                    old.center(),plan.tiles(),plan.endpoint(),plan.villagePathBlock(),
                    plan.platformAccessBlock(),plan.platformGrade(),
                    plan.stairCount(),old.changeCount());
            src.sendSuccess(()->Component.literal("[F1M VILLAGE PATH PLACED] "+plan.tiles()
                    +" footpath tiles reach existing village road. Station rails and village road untouched. Same-session undo: /tcrailway village railway stationaccessundo"),true);
            return 1;
        }catch(RuntimeException error){
            boolean restored=f1aRestore(level,old,"F1M_ACCESS_FAILURE");
            f1cAutoStopped=true;
            LOG.error(F1_LOG+" STATION_ACCESS_FAILED restored="+restored+" autoStopped=true",error);
            src.sendFailure(Component.literal("[F1M FAILURE] Footpath placement failed. Rollback "+(restored?"verified":"INCOMPLETE")+". Stop and send logs."));return 0;
        }
    }
    private static int f1kAccessUndo(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        F1kTrail trail=f1kBuilt;
        if(trail==null || f1kBuiltLevel==null || f1kBuiltLevel.get()!=level){
            src.sendFailure(Component.literal("[F1M] No same-session footpath placement. No changes."));return 0;
        }
        F1Proposal edit=trail.edit();
        if(!level.hasChunkAt(trail.villagePathBlock())
                || !level.getBlockState(trail.villagePathBlock()).equals(trail.villagePathState())
                || !level.hasChunkAt(trail.platformAccessBlock())
                || !level.getBlockState(trail.platformAccessBlock()).equals(trail.platformAccessState())){
            src.sendFailure(Component.literal("[F1M] Undo blocked: platform entry or existing village road has changed. No changes."));return 0;
        }
        for(var entry:edit.before().entrySet()){
            BlockPos cell=entry.getKey();
            BlockState expected=edit.changes().getOrDefault(cell,entry.getValue());
            if(!level.hasChunkAt(cell)||!level.getBlockState(cell).equals(expected)){
                src.sendFailure(Component.literal("[F1M] Undo blocked: modified or unloaded trail cell. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,edit.changes())){
            src.sendFailure(Component.literal("[F1M] Undo blocked: entity on footpath. No changes."));return 0;
        }
        boolean okay=f1aRestore(level,edit,"F1M_ACCESS_UNDO");
        if(!okay){
            f1cAutoStopped=true;
            src.sendFailure(Component.literal("[F1M] Undo incomplete. STOP and send logs."));return 0;
        }
        f1kBuilt=null;f1kBuiltLevel=null;
        LOG.info(F1_LOG+" STATION_ACCESS_UNDO_OK station={} cells={}",edit.center(),edit.before().size());
        src.sendSuccess(()->Component.literal("[F1M] Footpath restored; existing 49-rail station was never edited."),true);
        return 1;
    }
    private static int f1kAccessCancel(CommandSourceStack src){
        f1kPending=null;f1kPendingLevel=null;
        src.sendSuccess(()->Component.literal("[F1M] Read-only footpath preview canceled. No changes."),false);
        return 1;
    }

    // DEV50f1n: read-only SAVED-station buried-sand clearance diagnostic.
    // This repairs only the previously built station in the disposable world,
    // without replaying 709 original block edits or rebuilding its 49 rails.
    // One operator-initiated preview, explicit short-lived confirmation, and
    // same-session exact-state undo. Never invoked during world generation.
    private static final String F1N_LOG="[TC-DEV50F1N]";
    private static F1Proposal f1nPending;
    private static java.lang.ref.WeakReference<ServerLevel> f1nPendingLevel;
    private static F1Proposal f1nBuilt;
    private static java.lang.ref.WeakReference<ServerLevel> f1nBuiltLevel;
    private static final int F1N_MAX_CLEAR=192;
    private static F1Try f1nClearPlan(ServerLevel level,F1kStation station){
        F1Proposal nativeLine=new F1Proposal(station.village(),station.center(),
                station.alongX(),49,0,0,0,ticks,Map.of(),Map.of());
        F1eDesign design=f1eDesign(level,station.village());
        final int railY=station.center().getY(),floorY=railY-1;
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        int doorwaySide=f1eFront(design);
        for(int along=-7;along<=7;along++)for(int side=4;side<=13;side++){
            if(f1eHut(design,along,side))continue;
            boolean platform=side==4||side==5;
            boolean apron=side>=6&&side<doorwaySide
                    && (f1gDoor(design,along,doorwaySide)
                        || (along==0&&side<=7));
            boolean besideHut=f1eHut(design,along+1,side)
                    ||f1eHut(design,along-1,side)
                    ||f1eHut(design,along,side+1)
                    ||f1eHut(design,along,side-1);
            if(!platform&&!apron&&!besideHut)continue;
            BlockPos col=f1dAt(nativeLine,along,side);
            BlockPos ground=col.atY(floorY);
            if(!level.hasChunkAt(ground)||f1aOverlapsQuarantine(col,station.alongX(),1))
                return new F1Try(null,"UNLOADED_OR_QUARANTINED");
            BlockState support=level.getBlockState(ground);
            if(support.isAir()||!support.getFluidState().isEmpty()
                    ||level.getBlockEntity(ground)!=null)continue;
            // Do not leave an unrecorded gravity-block column suspended
            // over the proposed void; that could fall outside the snapshot.
            BlockState overhead=level.getBlockState(col.atY(railY+3));
            if(overhead.is(Blocks.SAND)||overhead.is(Blocks.RED_SAND)
                    ||overhead.is(Blocks.GRAVEL))
                return new F1Try(null,"UNSUPPORTED_GRAVITY_COLUMN");
            // Only remove explicit natural sand above supported walking cells.
            // Leave every wall, log, platform, stair, path, rail, crop, fluid,
            // structure piece and non-sand block completely untouched.
            for(int y=railY;y<=railY+2;y++){
                BlockPos cut=col.atY(y);
                if(!level.hasChunkAt(cut))return new F1Try(null,"UNLOADED");
                BlockState old=level.getBlockState(cut);
                if(!old.is(Blocks.SAND)&&!old.is(Blocks.RED_SAND))continue;
                if(!f1PlanCell(level,cut,Blocks.AIR.defaultBlockState(),before,changes))
                    return new F1Try(null,"PROTECTED_SAND_OR_STRUCTURE");
                if(changes.size()>F1N_MAX_CLEAR)return new F1Try(null,"CLEARANCE_EDIT_LIMIT");
            }
        }
        if(changes.isEmpty())return new F1Try(null,"NO_SAFE_SAND_TO_CLEAR");
        if(!f1kEntitiesClear(level,changes))return new F1Try(null,"ENTITY_IN_CLEARANCE");
        return new F1Try(new F1Proposal(station.village(),station.center(),
                station.alongX(),49,changes.size(),0,changes.size(),ticks,
                java.util.Collections.unmodifiableMap(before),
                java.util.Collections.unmodifiableMap(changes)),null);
    }
    private static int f1nClearPreview(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        f1nPending=null;f1nPendingLevel=null;
        ServerLevel level=src.getLevel();
        if(f1nBuilt!=null&&f1nBuiltLevel!=null&&f1nBuiltLevel.get()==level){
            src.sendFailure(Component.literal("[F1N] Same-session repair already placed; undo or restart before repeating. No changes."));return 0;
        }
        F1kStation station=f1kNearbyStation(level,BlockPos.containing(src.getPosition()));
        if(station==null){src.sendFailure(Component.literal("[F1N] No intact saved 49-rail station within 128 blocks. No changes."));return 0;}
        F1Try trial=f1nClearPlan(level,station);
        if(trial.proposal()==null){
            LOG.info(F1N_LOG+" CLEAR_REJECT center={} reason={} changed=0",station.center(),trial.reason());
            src.sendFailure(Component.literal("[F1N] Cleanup rejected: "+trial.reason()+". No changes."));return 0;
        }
        F1Proposal plan=trial.proposal();
        f1nPending=plan;f1nPendingLevel=new java.lang.ref.WeakReference<>(level);
        LOG.info(F1N_LOG+" CLEAR_PREVIEW center={} sandCells={} exactPreimage=true railsVerified=49 source=DEV50F1M worldChanged=false chunksForced=0",plan.center(),plan.changes().size());
        src.sendSuccess(()->Component.literal("[F1N CLEAR PREVIEW] station="+plan.center().toShortString()
                +" naturalSandToAir="+plan.changes().size()
                +". No changes. Inspect and use /tcrailway village railway stationclearplace confirm within 2 min ONLY if acceptable."),false);
        return 1;
    }
    private static int f1nClearPlace(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();
        F1Proposal plan=f1nPending;
        boolean same=plan!=null&&f1nPendingLevel!=null&&f1nPendingLevel.get()==level;
        f1nPending=null;f1nPendingLevel=null;
        if(!same||ticks<plan.createdTick()||ticks-plan.createdTick()>2400){
            src.sendFailure(Component.literal("[F1N] No current exact clearance preview. No changes."));return 0;
        }
        F1kStation station=f1kNearbyStation(level,plan.center());
        if(station==null||!station.center().equals(plan.center())
                ||!station.village().equals(plan.village())||station.alongX()!=plan.alongX()){
            src.sendFailure(Component.literal("[F1N] Saved station/49 rails changed. No changes."));return 0;
        }
        F1Try current=f1nClearPlan(level,station);
        if(current.proposal()==null||!current.proposal().before().equals(plan.before())
                ||!current.proposal().changes().equals(plan.changes())){
            src.sendFailure(Component.literal("[F1N] Clearance preview stale or unsafe. No changes."));return 0;
        }
        for(var entry:plan.before().entrySet()){
            if(!level.hasChunkAt(entry.getKey())
                    ||!level.getBlockState(entry.getKey()).equals(entry.getValue())){
                src.sendFailure(Component.literal("[F1N] Recorded block changed since preview. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,plan.changes())){
            src.sendFailure(Component.literal("[F1N] Entity in clearance. No changes."));return 0;
        }
        try{
            f1aApply(level,plan);
            F1Proposal line=new F1Proposal(station.village(),station.center(),station.alongX(),49,0,0,0,ticks,Map.of(),Map.of());
            if(f1cVerifyThroughLine(level,line)!=49)
                throw new IllegalStateException("49_RAIL_POST_AUDIT_FAILED");
            f1nBuilt=plan;f1nBuiltLevel=new java.lang.ref.WeakReference<>(level);
            LOG.info(F1N_LOG+" CLEAR_PLACED station={} sandCells={} railsVerified=49 verified=true noForcedChunks=true",plan.center(),plan.changes().size());
            src.sendSuccess(()->Component.literal("[F1N CLEAR PLACED] Removed "+plan.changes().size()
                    +" approved buried sand blocks; all 49 rails intact. Same-session undo: /tcrailway village railway stationclearundo"),true);
            return 1;
        }catch(RuntimeException error){
            boolean restored=f1aRestore(level,plan,"F1N_CLEAR_FAILED");
            f1cAutoStopped=true;
            LOG.error(F1N_LOG+" CLEAR_FAILED restored={} constructionStopped=true",restored,error);
            src.sendFailure(Component.literal("[F1N FAILURE] Rollback "+(restored?"verified":"INCOMPLETE")+". Stop and send logs."));return 0;
        }
    }
    private static int f1nClearUndo(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();F1Proposal plan=f1nBuilt;
        if(plan==null||f1nBuiltLevel==null||f1nBuiltLevel.get()!=level){
            src.sendFailure(Component.literal("[F1N] No same-session clearance repair to undo. No changes."));return 0;
        }
        for(var entry:plan.before().entrySet()){
            BlockPos p=entry.getKey();
            if(!level.hasChunkAt(p)||!level.getBlockState(p).equals(
                    plan.changes().getOrDefault(p,entry.getValue()))){
                src.sendFailure(Component.literal("[F1N] Undo blocked: intervening world change. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,plan.changes())){
            src.sendFailure(Component.literal("[F1N] Undo blocked by entity. No changes."));return 0;
        }
        if(!f1aRestore(level,plan,"F1N_CLEAR_UNDO")){
            src.sendFailure(Component.literal("[F1N] Undo incomplete. STOP and send logs."));return 0;
        }
        f1nBuilt=null;f1nBuiltLevel=null;
        LOG.info(F1N_LOG+" CLEAR_UNDO_OK station={} sandRestored={}",plan.center(),plan.changes().size());
        src.sendSuccess(()->Component.literal("[F1N] Sand clearance reverted; station and 49 rails unchanged."),true);
        return 1;
    }
    private static int f1nClearCancel(CommandSourceStack src){
        f1nPending=null;f1nPendingLevel=null;
        src.sendSuccess(()->Component.literal("[F1N] Pending station clearance preview canceled. No changes."),false);
        return 1;
    }


    // DEV50f1o: exact, two-cell saved-stair repair. Never run automatically.
    // Use a crosshair on the specific low stair; no village or rail scan writes.
    // The earlier F1M algorithm put bottom-half stairs IN the lower floor tile.
    // Correct stair base is one block ABOVE a full supporting path tile.
    private static final String F1O_LOG="[TC-DEV50F1O]";
    private static F1Proposal f1oPending;
    private static java.lang.ref.WeakReference<ServerLevel> f1oPendingLevel;
    private static F1Proposal f1oBuilt;
    private static java.lang.ref.WeakReference<ServerLevel> f1oBuiltLevel;
    private static final java.util.ArrayDeque<F1Proposal> f1oEarlier=new java.util.ArrayDeque<>();
    private static boolean f1oIsStair(BlockState b){
        return b.is(Blocks.COBBLESTONE_STAIRS)||b.is(Blocks.STONE_BRICK_STAIRS)
                ||b.is(Blocks.SANDSTONE_STAIRS)||b.is(Blocks.RED_SANDSTONE_STAIRS);
    }
    private static boolean f1oIsWalkSurface(BlockState b){
        return b.is(Blocks.DIRT_PATH)||b.is(Blocks.SMOOTH_SANDSTONE)
                ||b.is(Blocks.CUT_RED_SANDSTONE)||b.is(Blocks.COBBLESTONE)
                ||b.is(Blocks.GRAVEL)||b.is(Blocks.SANDSTONE)
                ||b.is(Blocks.STONE_BRICKS)||b.is(Blocks.MOSSY_STONE_BRICKS);
    }
    private static BlockState f1oLowerFloor(BlockState old,BlockState nearby){
        if(old.is(Blocks.SANDSTONE_STAIRS))return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if(old.is(Blocks.RED_SANDSTONE_STAIRS))return Blocks.CUT_RED_SANDSTONE.defaultBlockState();
        if(old.is(Blocks.STONE_BRICK_STAIRS))return Blocks.GRAVEL.defaultBlockState();
        // Follow the surrounding path, including snowy cobble where applicable.
        return nearby.is(Blocks.COBBLESTONE)?Blocks.COBBLESTONE.defaultBlockState()
                :Blocks.DIRT_PATH.defaultBlockState();
    }
    private static F1Try f1oStairPlan(ServerLevel level,F1kStation station,BlockPos at){
        if(!level.hasChunkAt(at)||!level.hasChunkAt(at.above(3))
                ||f1aOverlapsQuarantine(at,station.alongX(),1))return new F1Try(null,"UNLOADED_OR_QUARANTINED");
        long dx=(long)at.getX()-station.center().getX(),dz=(long)at.getZ()-station.center().getZ();
        if(dx*dx+dz*dz>128L*128L || Math.abs(at.getY()-station.center().getY())>12)
            return new F1Try(null,"NOT_NEAR_SAVED_STATION");
        int side=f1kSide(station,at.getX(),at.getZ());
        int along=f1kAlong(station,at.getX(),at.getZ());
        // Keep the original rail/covered station footprint and platform off-limits.
        if((Math.abs(along)<=24&&Math.abs(side)<=3)
                ||(Math.abs(along)<=7&&side>=2&&side<=13))
            return new F1Try(null,"STATION_OR_RAIL_PROTECTED");
        BlockState old=level.getBlockState(at);
        if(!f1oIsStair(old))return new F1Try(null,"LOOK_AT_A_LOW_FOOTPATH_STAIR");
        if(!old.hasProperty(net.minecraft.world.level.block.StairBlock.HALF)
                ||old.getValue(net.minecraft.world.level.block.StairBlock.HALF)
                  !=net.minecraft.world.level.block.state.properties.Half.BOTTOM)
            return new F1Try(null,"STAIR_NOT_BOTTOM_HALF");
        if(level.getBlockEntity(at)!=null||!old.getFluidState().isEmpty())
            return new F1Try(null,"UNSAFE_STAIR");
        // F1P: do not trust the old facing. The old generator could turn
        // stairs sideways. Infer the unique HIGH neighbor from the actual
        // adjacent, connected walkway geometry; fail CLOSED if ambiguous.
        net.minecraft.core.Direction facing=null;
        int highCount=0;
        for(net.minecraft.core.Direction d:new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST}){
            BlockPos high=at.relative(d).above();
            if(!level.hasChunkAt(high))return new F1Try(null,"HIGH_NEIGHBOR_UNLOADED");
            BlockState higher=level.getBlockState(high);
            if(f1oIsWalkSurface(higher)){
                facing=d;highCount++;
            }
        }
        if(highCount!=1)return new F1Try(null,"AMBIGUOUS_HIGH_NEIGHBOR_COUNT_"+highCount);
        // Confirm a lower path neighbor too, excluding the inferred uphill
        // side. This is not a general-purpose rotate arbitrary stairs command.
        boolean lowerPath=false;
        BlockState neighbor=Blocks.AIR.defaultBlockState();
        for(net.minecraft.core.Direction d:new net.minecraft.core.Direction[]{
                net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST}){
            if(d==facing)continue;
            BlockPos p=at.relative(d);
            if(!level.hasChunkAt(p))return new F1Try(null,"ADJACENT_UNLOADED");
            BlockState b=level.getBlockState(p);
            if(f1oIsWalkSurface(b)){lowerPath=true;neighbor=b;}
        }
        if(!lowerPath)return new F1Try(null,"NO_LOWER_PATH_NEIGHBOR");
        BlockPos head=at.above();
        if(!level.getBlockState(head).isAir()
                ||!level.getBlockState(head.above()).isAir()
                ||!level.getBlockState(head.above(2)).isAir())
            return new F1Try(null,"STAIR_RAISE_NEEDS_CLEAR_AIR");
        if(level.structureManager().getStructureWithPieceAt(at,StructureTags.VILLAGE).isValid()
                ||level.structureManager().getStructureWithPieceAt(head,StructureTags.VILLAGE).isValid())
            return new F1Try(null,"VILLAGE_STRUCTURE_PROTECTED");
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        // F1P: raise the formerly buried stair AND repair its facing.
        // Guard both exact cells; never alter a railway/platform/structure.
        BlockState support=f1oLowerFloor(old,neighbor);
        BlockState stair=old.setValue(net.minecraft.world.level.block.StairBlock.HALF,
                net.minecraft.world.level.block.state.properties.Half.BOTTOM)
                .setValue(net.minecraft.world.level.block.StairBlock.FACING,facing)
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,
                        net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT);
        if(!f1PlanCell(level,at,support,before,changes)
                ||!f1PlanCell(level,head,stair,before,changes)
                ||changes.size()!=2)return new F1Try(null,"STAIR_REPAIR_CELLS_UNSAFE");
        if(!f1kEntitiesClear(level,changes))return new F1Try(null,"ENTITY_IN_REPAIR_AREA");
        return new F1Try(new F1Proposal(station.village(),station.center(),station.alongX(),2,
                0,0,2,ticks,java.util.Collections.unmodifiableMap(before),
                java.util.Collections.unmodifiableMap(changes)),null);
    }
    private static int f1oStairPreview(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        f1oPending=null;f1oPendingLevel=null;
        ServerLevel level=src.getLevel();
        if(f1oBuilt!=null&&f1oBuiltLevel!=null&&f1oBuiltLevel.get()!=level){
            f1oBuilt=null;f1oBuiltLevel=null;f1oEarlier.clear();
        }
        if(f1oEarlier.size()>=15){
            src.sendFailure(Component.literal("[F1O] Session repair limit (16) reached. No changes."));return 0;
        }
        net.minecraft.server.level.ServerPlayer player;
        try{player=src.getPlayerOrException();}catch(Exception e){
            src.sendFailure(Component.literal("[F1O] Player must look at the low stair."));return 0;
        }
        net.minecraft.world.phys.HitResult ray=player.pick(8.0D,0.0F,false);
        if(!(ray instanceof net.minecraft.world.phys.BlockHitResult hit)
                ||ray.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK){
            src.sendFailure(Component.literal("[F1O] Aim at the misplaced stair within 8 blocks. No changes."));return 0;
        }
        BlockPos at=hit.getBlockPos();
        F1kStation station=f1kNearbyStation(level,BlockPos.containing(src.getPosition()));
        if(station==null){src.sendFailure(Component.literal("[F1O] No intact station and 49 rails near player. No changes."));return 0;}
        F1Try trial=f1oStairPlan(level,station,at);
        if(trial.proposal()==null){
            LOG.info(F1O_LOG+" STAIR_REJECT target={} reason={} changed=0",at,trial.reason());
            src.sendFailure(Component.literal("[F1O] Stair repair rejected: "+trial.reason()+". No changes."));return 0;
        }
        F1Proposal plan=trial.proposal();
        f1oPending=plan;f1oPendingLevel=new java.lang.ref.WeakReference<>(level);
        LOG.info("[TC-DEV50F1P] STAIR_PREVIEW stair={} moveTo={} oldFacing={} correctedFacing={} supportingFloor={} changeCells=2 railsVerified=49 worldChanged=false chunksForced=0",
                at,at.above(),level.getBlockState(at).getValue(net.minecraft.world.level.block.StairBlock.FACING),
                plan.changes().get(at.above()).getValue(net.minecraft.world.level.block.StairBlock.FACING),
                plan.changes().get(at));
        src.sendSuccess(()->Component.literal("[F1O STAIR PREVIEW] "+at.toShortString()
                +" -> "+at.above().toShortString()+" (up 1); two guarded edits. No changes."
                +" After review: /tcrailway village railway stationstairplace confirm within two minutes."),false);
        return 1;
    }
    private static int f1oStairPlace(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();F1Proposal plan=f1oPending;
        boolean valid=plan!=null&&f1oPendingLevel!=null&&f1oPendingLevel.get()==level;
        f1oPending=null;f1oPendingLevel=null;
        if(!valid||ticks<plan.createdTick()||ticks-plan.createdTick()>2400){
            src.sendFailure(Component.literal("[F1O] No current exact stair preview. No changes."));return 0;
        }
        F1kStation station=f1kNearbyStation(level,plan.center());
        if(station==null||!station.center().equals(plan.center())
                ||!station.village().equals(plan.village())||station.alongX()!=plan.alongX()){
            src.sendFailure(Component.literal("[F1O] Station/rails changed. No changes."));return 0;
        }
        BlockPos at=null;
        for(BlockPos p:plan.before().keySet())if(f1oIsStair(plan.before().get(p)))at=p;
        if(at==null){src.sendFailure(Component.literal("[F1O] Target stair missing. No changes."));return 0;}
        F1Try now=f1oStairPlan(level,station,at);
        if(now.proposal()==null||!now.proposal().before().equals(plan.before())
                ||!now.proposal().changes().equals(plan.changes())){
            src.sendFailure(Component.literal("[F1O] Stair preview changed or is unsafe. No changes."));return 0;
        }
        for(var entry:plan.before().entrySet()){
            if(!level.hasChunkAt(entry.getKey())
                    ||!level.getBlockState(entry.getKey()).equals(entry.getValue())){
                src.sendFailure(Component.literal("[F1O] Intervening change. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,plan.changes())){
            src.sendFailure(Component.literal("[F1O] Player or entity too close to stair. Move away. No changes."));return 0;
        }
        try{
            f1aApply(level,plan);
            F1Proposal line=new F1Proposal(station.village(),station.center(),station.alongX(),
                    49,0,0,0,ticks,Map.of(),Map.of());
            if(f1cVerifyThroughLine(level,line)!=49)
                throw new IllegalStateException("49_RAIL_POST_AUDIT_FAILED");
            if(f1oBuilt!=null&&f1oBuiltLevel!=null&&f1oBuiltLevel.get()==level)
                f1oEarlier.addLast(f1oBuilt);
            f1oBuilt=plan;f1oBuiltLevel=new java.lang.ref.WeakReference<>(level);
            LOG.info("[TC-DEV50F1P] STAIR_PLACED stairRaisedFrom={} stairRaisedTo={} cells=2 facingCorrected=true railsVerified=49",
                    at,at.above());
            src.sendSuccess(()->Component.literal("[F1P STAIR PLACED] Raised stair by one block and corrected facing."
                    +" Same-session undo: /tcrailway village railway stationstairundo"),true);
            return 1;
        }catch(RuntimeException error){
            boolean restored=f1aRestore(level,plan,"F1O_STAIR_PLACE_FAILURE");
            f1cAutoStopped=true;
            LOG.error(F1O_LOG+" STAIR_FAILURE rollbackVerified="+restored,error);
            src.sendFailure(Component.literal("[F1O] Placement failed; rollback "
                    +(restored?"verified":"INCOMPLETE")+". Stop and send logs."));return 0;
        }
    }
    private static int f1oStairUndo(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        ServerLevel level=src.getLevel();F1Proposal plan=f1oBuilt;
        if(plan==null||f1oBuiltLevel==null||f1oBuiltLevel.get()!=level){
            src.sendFailure(Component.literal("[F1O] No same-session stair repair. No changes."));return 0;
        }
        for(var entry:plan.before().entrySet()){
            BlockPos p=entry.getKey();
            if(!level.hasChunkAt(p)||!level.getBlockState(p).equals(
                    plan.changes().getOrDefault(p,entry.getValue()))){
                src.sendFailure(Component.literal("[F1O] Undo blocked by a changed cell. No changes."));return 0;
            }
        }
        if(!f1kEntitiesClear(level,plan.changes())){
            src.sendFailure(Component.literal("[F1O] Move clear of repaired stair before undo. No changes."));return 0;
        }
        if(!f1aRestore(level,plan,"F1O_STAIR_UNDO")){
            src.sendFailure(Component.literal("[F1O] Undo incomplete. Stop and send logs."));return 0;
        }
        f1oBuilt=f1oEarlier.pollLast();
        if(f1oBuilt==null)f1oBuiltLevel=null;
        LOG.info(F1O_LOG+" STAIR_UNDO_OK restoredCells=2 remainingRepairs={}",
                f1oEarlier.size()+(f1oBuilt==null?0:1));
        src.sendSuccess(()->Component.literal("[F1O] Stair repair safely undone. No rails changed."),true);
        return 1;
    }
    private static int f1oStairCancel(CommandSourceStack src){
        f1oPending=null;f1oPendingLevel=null;
        src.sendSuccess(()->Component.literal("[F1O] Stair preview canceled. No changes."),false);
        return 1;
    }

    // DEV51B R14: bounded, entirely read-only loaded-terrain survey.
    // This does not authorize construction, queue a site, or create a preview.
    // All shortlisted sites are screened by the UNMODIFIED f1Try+f1dDecorate
    // transaction validators. Full manual stationtestpreview is STILL REQUIRED.
    private static final String F1P_LOG="[TC-DEV51B-R14]";
    private static final int F1P_CAP=96;
    private static final int F1P_RESULTS=3;
    private static final long F1P_TIMEOUT=20L*60L*6L;
    private record F1pScored(BlockPos center,boolean alongX,int score,int wet,int heightSpan) {}
    private record F1pAccepted(BlockPos center,boolean alongX,int edits,int score) {}
    private static final class F1pSurvey {
        final ServerLevel level;
        final BlockPos village;
        final UUID operator;
        final long started=ticks;
        final F1fSafeSearch stable=new F1fSafeSearch();
        final Map<String,Integer> reasons=new LinkedHashMap<>();
        final java.util.List<F1pScored> ranked=new java.util.ArrayList<>();
        final java.util.List<F1pAccepted> accepted=new java.util.ArrayList<>();
        int scouted,checked,unloaded;
        boolean finished=false;
        F1pSurvey(ServerLevel level,BlockPos village,UUID operator){
            this.level=level;this.village=village;this.operator=operator;
        }
    }
    private static F1pSurvey f1pSurvey;
    private static void f1pNote(F1pSurvey s,String kind,String info){
        LOG.info(F1P_LOG+" {} village={} scouted={}/{} validated={}/{} unloaded={} reasons={} {}"
                +" savedWorldChanged=false chunksForced=0 previewsIssued=0",
                kind,s.village,s.scouted,F1P_CAP,s.checked,s.ranked.size(),
                s.unloaded,s.reasons,info);
        ServerPlayer player=s.level.getServer().getPlayerList().getPlayer(s.operator);
        if(player!=null)player.sendSystemMessage(Component.literal("[R14 SURVEY] "+kind+" "+info
                +" scouted="+s.scouted+" checked="+s.checked
                +" unloaded="+s.unloaded+". World unchanged."));
    }
    private static int f1pSurveyStart(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        if(!(src.getEntity() instanceof ServerPlayer player)){
            src.sendFailure(Component.literal("[R14] A player must run the survey. No changes."));return 0;
        }
        if(f1iFinder!=null || f1hPending!=null){
            src.sendFailure(Component.literal("[R14] Stop existing F1J finder or cancel pending F1H preview first. No changes."));return 0;
        }
        if(f1hBuilt!=null && f1hBuiltLevel!=null && f1hBuiltLevel.get()==src.getLevel()){
            src.sendFailure(Component.literal("[R14] An active test station exists. Do not survey over it. No changes."));return 0;
        }
        if(f1pSurvey!=null && !f1pSurvey.finished){
            src.sendFailure(Component.literal("[R14] Survey already running. Use stationtestsurveystatus or stationtestsurveystop."));return 0;
        }
        ServerLevel level=src.getLevel();
        BlockPos at=player.blockPosition();
        if(!level.hasChunkAt(at)){
            src.sendFailure(Component.literal("[R14] Player chunk not loaded. No changes."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,at,128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[R14] No loaded natural village within 128 blocks. No changes."));return 0;
        }
        BlockPos village=probe.center().get();
        if(!"DESERT".equals(f1dStyle(level,village))){
            src.sendFailure(Component.literal("[R14] R13 pathway targets a desert village, not "+f1dStyle(level,village)+". No changes."));return 0;
        }
        boolean abandoned=f1dPercentRoll(level,village,0x69B7E41DA2CC05F1L)
                <F1D_ABANDONED_CHANCE.get();
        if(abandoned){
            src.sendFailure(Component.literal("[R14] This village's station variant is abandoned; R13 flat living-station approach is not applicable. Choose a different village."));return 0;
        }
        if(f1cMarkers(level).near(village,ONE_PER_RADIUS.get())
                ||stations(level).near(village,ONE_PER_RADIUS.get())
                ||f1cPreviouslyConstructedRailNearby(level,village)){
            src.sendFailure(Component.literal("[R14] Existing station/rail marker near village. No changes."));return 0;
        }
        f1pSurvey=new F1pSurvey(level,village,player.getUUID());
        f1pNote(f1pSurvey,"START","Stand still near the village while loaded-only scouting completes. Up to 96 positions; R17 checks station footings, wall/corner edges, R16 apron and 17 rails; full validation runs one site per two seconds.");
        return 1;
    }
    private static int f1pSurveyStatus(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel()))return 0;
        F1pSurvey s=f1pSurvey;
        if(s==null){
            src.sendSuccess(()->Component.literal("[R14] No survey in this session. Run stationtestsurvey."),false);return 1;
        }
        final String summary="village="+s.village.toShortString()+" finished="+s.finished
                +" scouted="+s.scouted+"/"+F1P_CAP+" checked="+s.checked+"/"+s.ranked.size()
                +" candidates="+s.accepted.size()+" unloaded="+s.unloaded
                +" failures="+s.reasons;
        src.sendSuccess(()->Component.literal("[R14] "+summary+"; no world edits."),false);
        for(int i=0;i<s.accepted.size();i++){
            F1pAccepted a=s.accepted.get(i);
            final String item="Candidate "+(i+1)+" X="+a.center.getX()+" Z="+a.center.getZ()
                    +" groundY="+a.center.getY()+" track="+(a.alongX?"X":"Z")
                    +" edits="+a.edits+" score="+a.score;
            src.sendSuccess(()->Component.literal("[R14] "+item),false);
        }
        return 1;
    }
    private static int f1pSurveyStop(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel()))return 0;
        f1pSurvey=null;
        src.sendSuccess(()->Component.literal("[R14] Advisory survey cleared. No blocks changed."),false);
        return 1;
    }
    // EXACTLY the current F1H manual preview height/axis, not F1J's optional
    // +/-2 rail-bed height shift. Screening the compact 17x7 corridor prevents
    // candidates already known to fail the native FILL_OVER_2/CUT_OVER_2 gate.
    private static F1pScored f1pTerrain(ServerLevel level,BlockPos village,int x,int z,boolean alongX,
                                         Map<String,Integer> reasons){
        int groundY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,score=0,wet=0;
        for(int along=-F1R15_NEW_STATION_RAILS/2;along<=F1R15_NEW_STATION_RAILS/2;along++)for(int side=-3;side<=3;side++){
            int sx=x+(alongX?along:side),sz=z+(alongX?side:along);
            long vx=(long)sx-village.getX(),vz=(long)sz-village.getZ();
            if(vx*vx+vz*vz<56L*56L){reasons.merge("VILLAGE_BUFFER",1,Integer::sum);return null;}
            int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz);
            min=Math.min(min,h);max=Math.max(max,h);score+=Math.abs(h-groundY);
            if(!level.getBlockState(new BlockPos(sx,h-1,sz)).getFluidState().isEmpty())wet++;
        }
        if(min<groundY-2){reasons.merge("FILL_OVER_2",1,Integer::sum);return null;}
        if(max>groundY+2){reasons.merge("CUT_OVER_2",1,Integer::sum);return null;}
        if(wet>0){reasons.merge("WET_SURFACE",1,Integer::sum);return null;}
        return new F1pScored(new BlockPos(x,groundY,z),alongX,score+(max-min)*10,wet,max-min);
    }
    private static void f1pFinish(F1pSurvey s,String why){
        s.finished=true;
        f1pNote(s,"COMPLETE",why+"; safe candidates="+s.accepted.size()
                +"; checks were read-only; manual stationtestpreview still required.");
        for(int i=0;i<s.accepted.size();i++){
            F1pAccepted a=s.accepted.get(i);
            String msg="CANDIDATE_"+(i+1)+" center="+a.center+" axis="+(a.alongX?"X":"Z")
                    +" edits="+a.edits+" terrainScore="+a.score
                    +" teleport=/tp @s "+a.center.getX()+" "+(a.center.getY()+15)
                    +" "+a.center.getZ()
                    +" then /tcrailway village railway stationtestpreview";
            f1pNote(s,"READY",msg);
        }
        if(s.accepted.isEmpty())f1pNote(s,"NO_SITE",
                "No fully approved loaded site. Check unload/rejection summary and retry near another flat desert village.");
    }
    private static void f1pSurveyTick(ServerLevel level){
        F1pSurvey s=f1pSurvey;
        if(s==null||s.finished)return;
        if(s.level!=level || !dev50e2WorldAllowed(level)
                || (AUTOMATIC.get()&&F1C_AUTO_STRAIGHT.get()&&F1F_SAFE_AUTOMATIC.get())
                || f1iFinder!=null || f1hPending!=null){
            f1pFinish(s,"SAFETY_STOP_WORLD_OR_CONFIG");return;
        }
        if(ticks-s.started>F1P_TIMEOUT){f1pFinish(s,"TIMEOUT");return;}
        if(level.getServer().getPlayerList().getPlayer(s.operator)==null){
            f1pFinish(s,"OPERATOR_LEFT");return;
        }
        if(!f1fPlayerStableNear(level,s.village,s.stable))return;
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){f1pFinish(s,"LOW_MEMORY");return;}
        final int[] radii={72,84,96};
        final int[] offsets={-24,-18,-12,-6,6,12,18,24};
        if(s.scouted<F1P_CAP){
            for(int k=0;k<4&&s.scouted<F1P_CAP;k++){
                int idx=s.scouted++,direction=idx%4;
                int radius=radii[(idx/4)%3],offset=offsets[idx/12];
                int x=s.village.getX()+(direction==0?radius:direction==1?-radius:offset);
                int z=s.village.getZ()+(direction==2?radius:direction==3?-radius:offset);
                boolean alongX=direction>=2;
                long vx=(long)x-s.village.getX(),vz=(long)z-s.village.getZ();
                if(vx*vx+vz*vz>104L*104L){s.reasons.merge("TOO_FAR",1,Integer::sum);continue;}
                if(!f1fChunksReady(level,new BlockPos(x,s.village.getY(),z),alongX)){
                    s.unloaded++;s.reasons.merge("CHUNKS_UNLOADED",1,Integer::sum);continue;
                }
                F1pScored site=f1pTerrain(level,s.village,x,z,alongX,s.reasons);
                if(site!=null)s.ranked.add(site);
            }
            if(s.scouted==F1P_CAP){
                s.ranked.sort(java.util.Comparator.comparingInt(F1pScored::score));
                f1pNote(s,"SCOUT_DONE","Loaded flat dry sites="+s.ranked.size()
                        +"; starting complete 17-rail + R17 edge/footing + R16 perimeter + R13 path validation.");
                if(s.ranked.isEmpty())f1pFinish(s,"NO_LOADED_FLAT_SITES");
            }
            return;
        }
        if(s.checked>=s.ranked.size()){
            f1pFinish(s,"ALL_CANDIDATES_CHECKED");return;
        }
        F1pScored c=s.ranked.get(s.checked++);
        if(!f1fChunksReady(level,c.center(),c.alongX())){
            s.unloaded++;s.reasons.merge("CHUNKS_UNLOADED_RECHECK",1,Integer::sum);
        }else{
            // Same railY, orientation, footprint and non-abandoned design used
            // by the manual preview, including all R13 pedestrian earthwork.
            F1Try trial=f1Try(level,s.village,c.center(),c.alongX(),F1R15_NEW_STATION_RAILS);
            if(trial.proposal()!=null)trial=f1dDecorate(level,trial.proposal(),false);
            if(trial.proposal()!=null && trial.proposal().changeCount()>F1H_MAX_CHANGES)
                trial=new F1Try(null,"CHANGE_BUDGET_1200");
            if(trial.proposal()==null){
                s.reasons.merge(trial.reason()==null?"UNSPECIFIED":trial.reason(),1,Integer::sum);
            }else{
                F1pAccepted chosen=new F1pAccepted(c.center(),c.alongX(),trial.proposal().changeCount(),c.score());
                s.accepted.add(chosen);
                f1pNote(s,"SAFE_SITE_FOUND", "compactRails="+F1R15_NEW_STATION_RAILS+" platformBlocks=15 X="+chosen.center.getX()+" Z="+chosen.center.getZ()
                        +" groundY="+chosen.center.getY()+" axis="+(chosen.alongX?"X":"Z")
                        +" edits="+chosen.edits+" advisoryOnly=true");
            }
        }
        if(s.accepted.size()>=F1P_RESULTS)f1pFinish(s,"THREE_SITES_FOUND");
        else if(s.checked>=s.ranked.size())f1pFinish(s,"ALL_CANDIDATES_CHECKED");
        else if(s.checked%12==0)f1pNote(s,"VALIDATING","Checked="+s.checked
                +" of "+s.ranked.size()+" ranked sites; best grade first.");
    }

    // DEV50f1j: terrain-ranked read-only search. This is a NON-BUILDING bounded
    // read-only finder for an existing confirmed loaded natural village.
    // It never changes the 35% natural roll or any persisted village policy.
    private static F1iStationFinder f1iFinder;
    private static final int F1I_FIND_CANDIDATES=96;
    private static final long F1I_FIND_TIMEOUT_TICKS=20L*60L*8L;
    private static final class F1iStationFinder {
        final ServerLevel level;
        final BlockPos village;
        final UUID operator;
        final long startedTick;
        final F1fSafeSearch stable=new F1fSafeSearch();
        final Map<String,Integer> reasons=new LinkedHashMap<>();
        int checked,unloaded,scouted,latestHeightAdjust;
        int latestTerrainScore;
        String latestTrial="none";
        final java.util.List<F1jSite> ranked=new java.util.ArrayList<>();
        F1iStationFinder(ServerLevel level,BlockPos village,UUID operator){
            this.level=level;this.village=village;this.operator=operator;
            this.startedTick=ticks;
        }
    }
    private static void f1iReport(F1iStationFinder t,String text){
        LOG.info(F1_LOG+" FINDER "+text+" village="+t.village+" checked="+t.checked+
                " unloaded="+t.unloaded+" rejections="+t.reasons+
                " worldChanged=false chunksForced=0");
        ServerPlayer player=t.level.getServer().getPlayerList().getPlayer(t.operator);
        if(player!=null)player.sendSystemMessage(Component.literal("[F1J FINDER] "+text+
                " checked="+t.checked+" unloaded="+t.unloaded+
                " reasons="+t.reasons+". No blocks changed."));
    }
    private static int f1iFindStart(CommandSourceStack src){
        if(!f1hEligibleWorld(src))return 0;
        if(!(src.getEntity() instanceof ServerPlayer operator)){
            src.sendFailure(Component.literal("[F1J] A player must initiate the finder. No changes."));return 0;
        }
        if(f1hBuilt!=null && f1hBuiltLevel!=null && f1hBuiltLevel.get()==src.getLevel()){
            src.sendFailure(Component.literal("[F1J] Undo your existing station test first. No changes."));return 0;
        }
        if(f1iFinder!=null){
            src.sendFailure(Component.literal("[F1J] Finder already active. Use stationtestfindstatus or stationtestfindstop. No changes."));return 0;
        }
        ServerLevel level=src.getLevel();
        BlockPos player=operator.blockPosition();
        if(!level.hasChunkAt(player)){
            src.sendFailure(Component.literal("[F1J] Your chunk is unloaded. No changes."));return 0;
        }
        VillageProbe probe=inspectVillageForPilot(level,player,128);
        if(probe.center().isEmpty()){
            src.sendFailure(Component.literal("[F1J] Stand near a loaded natural village (within 128 blocks). No changes."));return 0;
        }
        BlockPos village=probe.center().get();
        if(f1cMarkers(level).near(village,ONE_PER_RADIUS.get())
                || stations(level).near(village,ONE_PER_RADIUS.get())
                || f1cPreviouslyConstructedRailNearby(level,village)){
            src.sendFailure(Component.literal("[F1J] Village already has rail/station marker. Choose another. No changes."));return 0;
        }
        // Starting another finder discards the OLD preview, never the blocks.
        f1hPending=null;
        f1hPendingLevel=null;
        f1iFinder=new F1iStationFinder(level,village,operator.getUUID());
        LOG.info(F1_LOG+" FIND_START village={} cap={} onePer40Ticks=true construction=DISABLED worldChanged=false",village,F1I_FIND_CANDIDATES);
        src.sendSuccess(()->Component.literal("[F1J] Finder started for village "+village.toShortString()+
                ". STAY STILL for 10 seconds. First a loaded-only terrain survey (~48 seconds), then dry/flat-first validation, one full site every 2 seconds. 96 candidates maximum. No blocks placed. Check stationtestfindstatus."),false);
        return 1;
    }
    private static int f1iFindStatus(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel()))return 0;
        F1iStationFinder finder=f1iFinder;
        if(finder==null){
            src.sendSuccess(()->Component.literal("[F1J] Finder idle. If a preview was found, use /tcrailway village railway stationtestplace confirm within 2 minutes."),false);
            return 1;
        }
        final String report="[F1J] Searching village="+finder.village.toShortString()+
                " surveyed="+finder.scouted+"/"+F1I_FIND_CANDIDATES+
                " validated="+finder.checked+"/"+finder.ranked.size()+" unloaded="+finder.unloaded+
                " stableChecks="+finder.stable.stableChecks+" reasons="+finder.reasons+
                " latest="+finder.latestTrial+" bedAdjust="+finder.latestHeightAdjust+
                " terrainScore="+finder.latestTerrainScore+" blockChanges=0";
        src.sendSuccess(()->Component.literal(report),false);
        return 1;
    }
    private static int f1iFindStop(CommandSourceStack src){
        if(!dev50e2WorldAllowed(src.getLevel()))return 0;
        f1iFinder=null;
        src.sendSuccess(()->Component.literal("[F1J] Site finder stopped. No blocks changed."),false);
        return 1;
    }
    // DEV50f1j: terrain-aware read-only rail-bed elevation selection.
    // This is deliberately a bounded estimate, NOT permission to modify land:
    // f1Try + f1dDecorate still audit every individual block before a preview.
    // Heights/fluids are read ONLY after the full candidate footprint passed
    // f1fChunksReady; no chunk tickets, async work or forced chunk generation.
    private record F1jGrade(int railY,int terrainScore,int heightAdjust,int wetSamples) {}
    // Preview prioritizes dry, flat sites; the full plan validator remains
    // authoritative and never accepts a candidate just because it ranks well.
    private record F1jSite(int x,int z,boolean alongX,F1jGrade grade) {
        int priority(){return grade.terrainScore()+grade.wetSamples()*500;}
    }
    private static F1jGrade f1jPickRailHeight(ServerLevel level,int x,int z,boolean alongX){
        int centerY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        // 9 longitudinal samples * 6 cross-track / station samples = 54.
        final int[] offsets={-3,0,3,5,9,13};
        int[] sampledY=new int[9*offsets.length];
        int wet=0,k=0;
        for(int forward=-24;forward<=24;forward+=6){
            for(int cross:offsets){
                int sx=x+(alongX?forward:cross);
                int sz=z+(alongX?cross:forward);
                int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz);
                sampledY[k++]=h;
                if(!level.getBlockState(new BlockPos(sx,h-1,sz)).getFluidState().isEmpty())wet++;
            }
        }
        // No more than +/-2 blocks elevation adjustment from the candidate
        // center ground. Different headings are separate bounded candidates.
        int selected=centerY,best=Integer.MAX_VALUE;
        for(int candidateY=centerY-2;candidateY<=centerY+2;candidateY++){
            int score=Math.abs(candidateY-centerY)*4;
            for(int i=0;i<sampledY.length;i++){
                int h=sampledY[i];
                int side=offsets[i%offsets.length];
                int deviation=side<=3?Math.abs(h-candidateY):Math.abs(h-(candidateY-1));
                // Grade is an estimate. Exact grade, liquids, villages,
                // blocks and buildings are revalidated by f1Try/f1dDecorate.
                score+=deviation;
                if(deviation>2)score+=120*(deviation-2);
            }
            if(score<best){best=score;selected=candidateY;}
        }
        return new F1jGrade(selected,best,selected-centerY,wet);
    }

    private static void f1iFinderTick(ServerLevel level){
        F1iStationFinder t=f1iFinder;
        if(t==null)return;
        if(t.level!=level || !dev50e2WorldAllowed(level)
                || (AUTOMATIC.get() && F1C_AUTO_STRAIGHT.get() && F1F_SAFE_AUTOMATIC.get())){
            f1iFinder=null;
            f1iReport(t,"SAFETY_STOP: world/config changed");return;
        }
        if(ticks-t.startedTick>F1I_FIND_TIMEOUT_TICKS){
            f1iFinder=null;
            f1iReport(t,"TIMEOUT: no valid site, try a different village");return;
        }
        if(level.getServer().getPlayerList().getPlayer(t.operator)==null){
            f1iFinder=null;return;
        }
        if(!f1fPlayerStableNear(level,t.village,t.stable))return;
        if(f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){
            f1iFinder=null;
            f1iReport(t,"MEMORY_SAFETY_STOP: less than 768 MiB heap headroom");return;
        }
        // Phase A: bounded lightweight scout of 96 positions, four per 40
        // ticks (216 height/fluid samples). All positions must be loaded.
        // Rank dry/flat sites before doing expensive structure verification.
        // Phase B: validate ONE fully audited proposal per 40 ticks.
        // There is NEVER a batch of heavy building plans in a single tick.
        final int[] radii={80,92,104};
        final int[] lateral={-18,-6,6,18};
        if(t.scouted<F1I_FIND_CANDIDATES){
            int count=0;
            while(count++<4 && t.scouted<F1I_FIND_CANDIDATES){
                int index=t.scouted++;
                int axis=index%2;
                int direction=(index/2)%4;
                int radius=radii[(index/8)%radii.length];
                int offset=lateral[(index/24)%lateral.length];
                int dx=direction==0?radius:direction==1?-radius:offset;
                int dz=direction==2?radius:direction==3?-radius:offset;
                if(direction<2)dz=offset;else dx=offset;
                int x=t.village.getX()+dx,z=t.village.getZ()+dz;
                boolean alongX=axis==0;
                if(!f1fChunksReady(level,new BlockPos(x,t.village.getY(),z),alongX)){
                    t.unloaded++;
                    t.reasons.merge("CHUNK_UNLOADED",1,Integer::sum);
                    continue;
                }
                F1jGrade grade=f1jPickRailHeight(level,x,z,alongX);
                t.ranked.add(new F1jSite(x,z,alongX,grade));
            }
            if(t.scouted==F1I_FIND_CANDIDATES){
                t.ranked.sort(java.util.Comparator.comparingInt(F1jSite::priority));
                LOG.info(F1_LOG+" TERRAIN_SCOUT_DONE village={} surveyed={} loaded={} unloaded={} firstPriority={} noForcedChunks=true blockChanges=0",
                        t.village,t.scouted,t.ranked.size(),t.unloaded,
                        t.ranked.isEmpty()?"none":t.ranked.get(0).priority());
                f1iReport(t,"SCOUT_COMPLETE: starting dry/flat-first full validation");
            }else if(t.scouted%24==0){
                f1iReport(t,"TERRAIN_SCOUT_PROGRESS");
            }
            return;
        }
        if(t.checked>=t.ranked.size()){
            f1iFinder=null;
            f1iReport(t,"NO_SITE: all loaded terrain-ranked candidates rejected; send latest.log");
            return;
        }
        F1jSite candidate=t.ranked.get(t.checked++);
        int x=candidate.x(),z=candidate.z();
        boolean alongX=candidate.alongX();
        F1jGrade grade=candidate.grade();
        t.latestTrial=x+","+z+" axis="+(alongX?"X":"Z");
        t.latestHeightAdjust=grade.heightAdjust();
        t.latestTerrainScore=grade.terrainScore();
        BlockPos center=new BlockPos(x,grade.railY(),z);
        if(!f1fChunksReady(level,center,alongX)){
            t.unloaded++;
            t.reasons.merge("CHUNK_UNLOADED_AFTER_SCOUT",1,Integer::sum);
        }else{
            boolean abandoned=f1dPercentRoll(level,t.village,0x69B7E41DA2CC05F1L)
                    <F1D_ABANDONED_CHANCE.get();
            F1Try result=f1Try(level,t.village,center,alongX,F1R15_NEW_STATION_RAILS);
            if(result.proposal()!=null)result=f1dDecorate(level,result.proposal(),abandoned);
            if(result.proposal()!=null && result.proposal().changeCount()>F1H_MAX_CHANGES)
                result=new F1Try(null,"CHANGE_BUDGET_1200");
            if(result.proposal()==null){
                String reason=result.reason()==null?"UNSPECIFIED":result.reason();
                t.reasons.merge(reason,1,Integer::sum);
                LOG.info(F1_LOG+" FIND_TRIAL checked={}/{} {} y={} heightAdjust={} terrainScore={} wetSamples={} reason={} noChanges=true chunksForced=0",
                        t.checked,t.ranked.size(),t.latestTrial,grade.railY(),
                        grade.heightAdjust(),grade.terrainScore(),grade.wetSamples(),reason);
            }else{
                f1hPending=result.proposal();
                f1hPendingLevel=new java.lang.ref.WeakReference<>(level);
                f1iFinder=null;
                final String details=f1hSummary(level,result.proposal(),abandoned);
                LOG.info(F1_LOG+" FIND_PREVIEW_READY {} surveyed={} validated={} heightAdjust={} terrainScore={} wetSamples={} construction=NO worldChanged=false naturalRollUnchanged=true",
                        details,t.scouted,t.checked,grade.heightAdjust(),grade.terrainScore(),grade.wetSamples());
                ServerPlayer user=level.getServer().getPlayerList().getPlayer(t.operator);
                if(user!=null)user.sendSystemMessage(Component.literal("[F1J PREVIEW READY] "+details+
                        ". No blocks changed. Review position/design; ONLY if acceptable run /tcrailway village railway stationtestplace confirm within 2 minutes. Or use stationtestcancel."));
                return;
            }
        }
        if(t.checked%8==0)f1iReport(t,"VALIDATION_PROGRESS");
        if(t.checked>=t.ranked.size()){
            f1iFinder=null;
            f1iReport(t,"NO_SITE: all loaded terrain-ranked candidates rejected; send latest.log");
        }
    }

    // DEV50f1f safety-first replacement for the former synchronous 160-site
    // sweep and five full retries. It never forces a chunk, never loads/reads
    // another save, and is INACTIVE unless explicitly enabled in the exact
    // disposable integrated test world. One candidate per 40 server ticks,
    // globally, AFTER the player remains nearby/still for 10 seconds.
    private static long f1fHeapHeadroom(){
        Runtime vm=Runtime.getRuntime();
        return vm.maxMemory()-(vm.totalMemory()-vm.freeMemory());
    }
    private static boolean f1fChunksReady(ServerLevel level,BlockPos c,boolean alongX){
        int dx=alongX?29:18, dz=alongX?18:29;
        for(int cx=(c.getX()-dx)>>4;cx<=((c.getX()+dx)>>4);cx++)
            for(int cz=(c.getZ()-dz)>>4;cz<=((c.getZ()+dz)>>4);cz++)
                if(!level.hasChunk(cx,cz))return false;
        return true;
    }
    private static boolean f1fPlayerStableNear(ServerLevel level,BlockPos village,F1fSafeSearch task){
        var candidate=level.players().stream().filter(player->{
            long dx=(long)player.blockPosition().getX()-village.getX();
            long dz=(long)player.blockPosition().getZ()-village.getZ();
            return dx*dx+dz*dz<144L*144L;
        }).findFirst();
        if(candidate.isEmpty()){
            task.stableChecks=0;task.lastPlayer=null;
            return false;
        }
        BlockPos playerPos=candidate.get().blockPosition();
        // A station search must not run during teleport, flight, or when
        // lighting/world streaming is actively catching up around the player.
        if(task.lastStableTick!=ticks){
            if(task.lastPlayer==null ||
                    Math.abs(playerPos.getX()-task.lastPlayer.getX())>2 ||
                    Math.abs(playerPos.getZ()-task.lastPlayer.getZ())>2 ||
                    Math.abs(playerPos.getY()-task.lastPlayer.getY())>3)
                task.stableChecks=0;
            else task.stableChecks++;
            task.lastPlayer=playerPos;
            task.lastStableTick=ticks;
        }
        return task.stableChecks>=5;
    }
    private static boolean f1cAutoFreshVillage(ServerLevel level,BlockPos village) {
        if(!dev50e2WorldAllowed(level) || !F1C_AUTO_STRAIGHT.get() ||
                !F1F_SAFE_AUTOMATIC.get() || !AUTOMATIC.get())return true;
        F1cMarkerData markers=f1cMarkers(level);
        if(markers.near(village,ONE_PER_RADIUS.get()))return true;
        F1dPolicyData policy=f1dPolicy(level);
        if(policy.exhausted(village))return true;
        if(!policy.hasExact(village)&&policy.hasNearby(village,ONE_PER_RADIUS.get()))return true;
        if(f1cPreviouslyConstructedRailNearby(level,village) ||
                stations(level).near(village,ONE_PER_RADIUS.get()))return true;
        if(f1cAutoStopped)return true;
        int roll=f1dPercentRoll(level,village,0x2E15C6A1D9B47F03L);
        int chance=F1D_STATION_CHANCE.get();
        if(!policy.hasExact(village))policy.record(village,roll<chance);
        if(!policy.isSelected(village)){
            LOG.info(F1_LOG+" AUTO_CHANCE_SKIP village={} roll={} stationPercent={} abandonedPercent={} persistedDecision=true blocksChanged=0",
                    village,roll,chance,F1D_ABANDONED_CHANCE.get());
            return true;
        }
        final boolean abandoned=f1dPercentRoll(level,village,0x69B7E41DA2CC05F1L)
                <F1D_ABANDONED_CHANCE.get();
        long key=village.asLong();
        F1fSafeSearch task=F1F_SEARCH_TASKS.computeIfAbsent(key,k->new F1fSafeSearch());
        if(!f1fPlayerStableNear(level,village,task))return false;
        // Global slow cadence; eight new regions cannot trigger eight heavy
        // searches in the same tick. No queued work or chunk tickets.
        if(ticks-F1F_LAST_GLOBAL_SCAN<40)return false;
        long free=f1fHeapHeadroom();
        if(free<F1F_MIN_HEAP_HEADROOM){
            f1cAutoStopped=true;
            LOG.error(F1_LOG+" AUTO_MEMORY_SAFETY_STOP village={} remainingHeadroomMiB={} requiredMiB=768 sessionDisabled=true noChanges=true",
                    village,free/(1024L*1024L));
            return true;
        }
        F1F_LAST_GLOBAL_SCAN=ticks;
        // Interleave cardinal directions; four directions x three distances x
        // three offsets. Each site is visited AT MOST ONCE per village.
        final int[] radii={56,72,88};
        final int[] offsets={0,8,-8};
        while(task.cursor<F1F_MAX_SITES){
            int index=task.cursor++;
            int direction=index%4, radius=radii[(index/4)%3];
            int offset=offsets[(index/12)%3];
            int dx=direction==0?radius:direction==1?-radius:offset;
            int dz=direction==2?radius:direction==3?-radius:offset;
            if(direction<2)dz=offset;else dx=offset;
            int x=village.getX()+dx,z=village.getZ()+dz;
            BlockPos sample=new BlockPos(x,village.getY(),z);
            boolean alongX=direction>=2;
            // Probe the entire railway + station rectangle using hasChunk,
            // which never requests generation, BEFORE any heightmap/block read.
            if(!f1fChunksReady(level,sample,alongX)){
                task.missingChunks++;
                if(task.cursor%6==0)
                    LOG.info(F1_LOG+" SAFE_SITE_PROGRESS village={} checked={} unloaded={} rejected={} worldChanged=false",
                            village,task.cursor,task.missingChunks,task.rejected);
                continue;
            }
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            F1Try found=f1Try(level,village,new BlockPos(x,y,z),alongX,F1R15_NEW_STATION_RAILS);
            if(found.proposal()!=null)found=f1dDecorate(level,found.proposal(),abandoned);
            F1C_AUTO_ATTEMPTS.put(key,task.cursor);
            if(found.proposal()==null){
                task.rejected++;
                LOG.info(F1_LOG+" SAFE_SITE_REJECT village={} candidate={}/{} site={}:{} reason={} unloaded={} noChanges=true",
                        village,task.cursor,F1F_MAX_SITES,x,z,found.reason(),task.missingChunks);
                break; // ONE expensive candidate evaluation per global cadence.
            }
            F1Proposal p=found.proposal();
            if(p.length()!=F1R15_NEW_STATION_RAILS || p.changeCount()>1200 || f1fHeapHeadroom()<F1F_MIN_HEAP_HEADROOM){
                task.rejected++;
                LOG.info(F1_LOG+" SAFE_PROPOSAL_REJECT village={} rails={} blockChanges={} heapHeadroomMiB={} noChanges=true",
                        village,p.length(),p.changeCount(),f1fHeapHeadroom()/(1024L*1024L));
                break;
            }
            // Preserve the DEV50f1e exact block pre-image and entity check.
            for(var entry:p.before().entrySet()){
                if(!level.hasChunkAt(entry.getKey()) ||
                        !level.getBlockState(entry.getKey()).equals(entry.getValue())){
                    task.rejected++;
                    LOG.warn(F1_LOG+" SAFE_PREIMAGE_CHANGED village={} at={} noChanges=true",village,entry.getKey());
                    return false;
                }
            }
            for(Entity entity:level.getEntitiesOfClass(Entity.class,
                    new AABB(p.center().offset(p.alongX()?-p.length()/2-5:-17,-3,
                                    p.alongX()?-17:-p.length()/2-5),
                             p.center().offset(p.alongX()?p.length()/2+6:18,9,
                                    p.alongX()?18:p.length()/2+6)))){
                if(entity.isRemoved())continue;
                for(BlockPos at:p.changes().keySet())
                    if(at.getY()>=p.center().getY() &&
                            entity.getBoundingBox().intersects(new AABB(at))){
                        task.rejected++;
                        LOG.info(F1_LOG+" SAFE_ENTITY_INTERSECTION village={} noChanges=true",village);
                        return false;
                    }
            }
            try {
                f1aApply(level,p);
                int rails=f1cVerifyThroughLine(level,p);
                if(rails!=F1R15_NEW_STATION_RAILS)throw new IllegalStateException("COMPACT_RAIL_VERIFICATION="+rails);
                markers.add(village,p.center());
                F1F_SEARCH_TASKS.remove(key);
                LOG.info(F1_LOG+" AUTO_PLACED village={} center={} axis={} normalRails=17 platformBlocks=15 extensionEnds=2 specialStationRails=0 changed={} abandoned={} buildingDesign={} villageStyle={} safeSearch=true candidates={} unloaded={} blockChangesLimit=1200 surfaceArchitectures=10 subwayConstruction=DISABLED",
                        village,p.center(),p.alongX()?"X":"Z",p.changeCount(),abandoned,
                        f1eDesign(level,village),f1dStyle(level,village),task.cursor,task.missingChunks);
                return true;
            } catch(RuntimeException error){
                boolean restored=f1aRestore(level,p,"F1F_SAFE_AUTO_FAILURE");
                f1cAutoStopped=true;
                F1F_SEARCH_TASKS.remove(key);
                LOG.error(F1_LOG+" SAFE_AUTO_ROLLBACK village="+village+" fullRestore="+restored+" sessionDisabled=true",error);
                return true;
            }
        }
        if(task.cursor>=F1F_MAX_SITES){
            policy.exhaust(village);
            F1F_SEARCH_TASKS.remove(key);
            LOG.info(F1_LOG+" AUTO_EXHAUSTED_PERSISTED village={} attemptedCandidates={} missingFootprints={} rejected={} noRetryOnReload=true worldChanged=false",
                    village,task.cursor,task.missingChunks,task.rejected);
            return true;
        }
        return false;
    }
    private static int f1cAutoStatus(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if(!dev50e2WorldAllowed(level)) {
            src.sendFailure(Component.literal("DEV50f1c auto status only in the exact disposable test world."));
            return 0;
        }
        int count=f1cMarkers(level).count();
        final String report="[F1C AUTO] newVillageRailPilot="+(AUTOMATIC.get()&&F1C_AUTO_STRAIGHT.get()&&F1F_SAFE_AUTOMATIC.get())
                +" markers="+count+" attemptedVillages="+F1C_AUTO_ATTEMPTS.size()
                +" sessionStopped="+f1cAutoStopped
                +" f1fSafeAutoOptIn="+F1F_SAFE_AUTOMATIC.get()
                +" heapHeadroomMiB="+(f1fHeapHeadroom()/(1024L*1024L))
                +" requiredHeapHeadroomMiB=768 maxCandidatesPerVillage="+F1F_MAX_SITES
                +" pendingSafeSearchVillages="+F1F_SEARCH_TASKS.size()
                +" persistentlyExhausted="+f1dPolicy(level).exhaustedCount()
                +" freshVillagesOnly=true stationStopRails=NONE"
                +" stationSpawnChancePercent="+F1D_STATION_CHANCE.get()
                +" abandonedAmongStationsPercent="+F1D_ABANDONED_CHANCE.get()
                +" style=BIOME_VARIANTS_10 stationTest=TERRAIN_AWARE_DUAL_AXIS_FINDER_AND_CONFIRM subwayArchitectures=3 subwayConstruction=DISABLED_READ_ONLY nearbySiteLimit=88"
                +" villagePolicyDecisions="+f1dPolicy(level).count();
        LOG.info(F1_LOG+" AUTO_STATUS {}",report);
        src.sendSuccess(()->Component.literal(report),false);
        return 1;
    }

    private static int debugState(CommandSourceStack src) {
        String detail="newChunks="+CHUNKS_NEW.get()+" regionsAdded="+REGION_ADDED.get()
                +" active="+FRESH_REGIONS.size()+" dropped="+REGION_DROPPED.get()
                +" checked="+CHECKED.get()+" homesMissing="+HOMES_MISSING.get()
                +" notNatural="+NONVILLAGE_HOMES.get()+" noSite="+SITE_MISSING.get()
                +" alreadyStation="+ALREADY_STATION.get()+" autoBuilt="+BUILT_AUTOMATIC.get()
                +" e2NetworkQueued="+DEV50E2_PENDING.size()+" e2NetworkBuilt="+DEV50E2_BUILT.get()
                +" e2NetworkRejected="+DEV50E2_REJECTED.get();
        LOG.info(LOG_PREFIX+"WORLDGEN_COUNTERS "+detail+" last="+lastDecision);
        src.sendSuccess(()->Component.literal("[TC railway debug] "+detail),false);
        src.sendSuccess(()->Component.literal("[TC railway debug] last="+lastDecision+"; verbose="+logging()),false);
        return 1;
    }

    // DEV51B R40M: explicit, READ-ONLY, loaded-only village site survey.
    // It neither creates/loads distant chunks nor alters station records or terrain.
    // Strictly limited to the disposable R40 acceptance save.
    private record R40MFoundVillage(BlockPos structureCenter, String villageType) {}

    private static int r40mAutoScan(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!r40j4TestWorld(level)) {
            src.sendFailure(Component.literal("[R40M] Autoscan is available ONLY in DEV51B R40 Station Acceptance."));
            return 0;
        }
        BlockPos player = BlockPos.containing(src.getPosition());
        final int chunkRadius = 24; // search existing, currently LOADED chunks only
        final int maxReported = 12;
        int pcx = player.getX() >> 4, pcz = player.getZ() >> 4;
        int loadedChunks = 0, unloadedSkipped = 0, vanillaStarts = 0;
        Map<String, R40MFoundVillage> found = new LinkedHashMap<>();
        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                if (!level.hasChunk(cx, cz)) { unloadedSkipped++; continue; }
                loadedChunks++;
                // Existing code already uses hasChunk + getChunk for this exact
                // read-only StructureStart API. Never call getChunk if absent.
                for (StructureStart start : level.getChunk(cx, cz).getAllStarts().values()) {
                    if (start == null || !start.isValid()) continue;
                    ResourceLocation id = level.registryAccess()
                            .registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                    if (id == null || !"minecraft".equals(id.getNamespace())) continue;
                    String type = id.getPath();
                    if (!("village_plains".equals(type) || "village_desert".equals(type)
                            || "village_savanna".equals(type) || "village_taiga".equals(type)
                            || "village_snowy".equals(type))) continue;
                    vanillaStarts++;
                    BlockPos c = start.getBoundingBox().getCenter();
                    String key = type + ":" + c.getX() + ":" + c.getZ();
                    found.putIfAbsent(key, new R40MFoundVillage(c, type));
                }
            }
        }
        List<R40MFoundVillage> ranked = new ArrayList<>(found.values());
        ranked.sort(Comparator.comparingDouble(v ->
                Math.pow((double) v.structureCenter().getX() - player.getX(), 2)
                + Math.pow((double) v.structureCenter().getZ() - player.getZ(), 2)));
        int safe = 0, noSafe = 0, held = 0, reported = 0;
        List<String> results = new ArrayList<>();
        for (R40MFoundVillage v : ranked) {
            if (reported >= maxReported) break;
            reported++;
            BlockPos anchor = v.structureCenter();
            String status;
            if (!level.hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
                held++;
                status = "HOLD_UNLOADED_CENTER";
            } else {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        anchor.getX(), anchor.getZ());
                BlockPos village = new BlockPos(anchor.getX(), y, anchor.getZ());
                // Same exact 40-candidate R40J4 foundation rules as /tcrailway diagnose.
                SiteSearch site = inspectSites(level, village);
                if (site.site().isPresent()) {
                    safe++;
                    BlockPos candidate = site.site().get().center();
                    status = "SAFE at " + candidate.toShortString();
                } else {
                    Map<String, Integer> rejects = site.rejections();
                    // A partial loaded area is not proof terrain is unsuitable.
                    boolean missing = rejects.containsKey("missing_chunk_or_height")
                            || rejects.containsKey("unloaded");
                    if (missing) { held++; status = "HOLD_PARTIAL rejects=" + rejects; }
                    else { noSafe++; status = "NO_SAFE_SITE rejects=" + rejects; }
                }
            }
            results.add("[R40M] " + reported + " " + v.villageType()
                    + " center=" + anchor.getX() + "," + anchor.getZ()
                    + " " + status);
        }
        String summary = "[R40M] loaded=" + loadedChunks + " skipped=" + unloadedSkipped
                + " villageStarts=" + vanillaStarts + " distinct=" + found.size()
                + " shown=" + reported + " SAFE=" + safe + " NO_SAFE=" + noSafe
                + " HOLD=" + held + " writes=0 newChunks=0";
        LOG.info("[TC-DEV51B-R40M] AUTOSCAN {}", summary);
        src.sendSuccess(() -> Component.literal(summary), false);
        for (String result : results) {
            LOG.info("[TC-DEV51B-R40M] AUTOSCAN {}", result);
            src.sendSuccess(() -> Component.literal(result), false);
        }
        if (found.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "[R40M] No village structure starts in loaded chunks. Scan a different loaded area; this does not search unexplored terrain."), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                    "[R40M] READ-ONLY. SAFE is terrain eligibility, not guaranteed natural spawning. Nothing built or teleported."), false);
        }
        return 1;
    }

    private static int diagnose(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Railway diagnostic only supports the Overworld")); return 0;
        }
        BlockPos player=BlockPos.containing(src.getPosition());
        VillageProbe probe=dev50e2WorldAllowed(level)
                ? inspectVillageForPilot(level,player,96)
                : inspectVillage(level,player,96);
        String result="player="+player.toShortString()+" HOME_POIs="+probe.homes()
                +" cluster="+probe.clustered()+" vanillaStructure="+probe.natural()
                +" village="+probe.center().map(BlockPos::toShortString).orElse("NONE");
        LOG.info(LOG_PREFIX+"WORLDGEN_DIAG "+result);
        src.sendSuccess(()->Component.literal("[TC railway diagnose] "+result),false);
        if (probe.center().isPresent()) {
            SiteSearch sites=inspectSites(level,probe.center().get());
            String sitesText="safeSite="+sites.site().map(s->s.center().toShortString()).orElse("NONE")
                    +" rejectedOffsets="+sites.rejections()+" alreadyRecorded="
                    +stations(level).near(probe.center().get(),ONE_PER_RADIUS.get());
            LOG.info(LOG_PREFIX+"WORLDGEN_DIAG "+sitesText);
            src.sendSuccess(()->Component.literal("[TC railway diagnose] "+sitesText),false);
        }
        return 1;
    }

    // Legacy DEV50b1/b1a station inhabitants were not recorded at creation.
    // Adoption is explicit to avoid claiming villagers spawned by players.
    private static int staffCommand(CommandSourceStack src,boolean adopt) {
        ServerLevel level=src.getLevel();
        StationData data=stations(level);
        Optional<BlockPos> closest=data.closest(BlockPos.containing(src.getPosition()),96);
        if (closest.isEmpty()) {src.sendFailure(Component.literal("No recorded railway station within 96 blocks"));return 0;}
        BlockPos c=closest.get();
        int registered=0;
        if(adopt){
            AABB scan=new AABB(c.offset(-15,-2,-10),c.offset(16,8,18));
            for(String role:new String[]{"station_chief","train_conductor","railway_engineer"}){
                if(data.getStaff(c,role)!=null) continue;
                for(Villager v:level.getEntitiesOfClass(Villager.class,scan)){
                    if(!role.equals(v.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG))) continue;
                    if(v.getPersistentData().contains(STAFF_STATION_TAG,Tag.TAG_LONG)) continue;
                    if(!v.getVillagerData().getProfession().equals(RailwayVillagers.forEgg(role).get())) continue;
                    v.getPersistentData().putLong(STAFF_STATION_TAG,c.asLong());
                    v.getPersistentData().putString(STAFF_SLOT_ROLE_TAG,role);
                    v.setPersistenceRequired();
                    data.registerStaff(c,role,v.getUUID());
                    registered++;
                    break;
                }
            }
        }
        StringBuilder states=new StringBuilder();
        for(String role:new String[]{"station_chief","train_conductor","railway_engineer"}){
            StaffSlot slot=data.getStaff(c,role);
            String state=slot==null?"unmanaged":slot.respawnAt>0
                    ?"death recorded, due in "+Math.max(0,(slot.respawnAt-level.getGameTime())/20)+"s"
                    :"tracked uuid="+slot.uuid;
            states.append(" ").append(role).append(": ").append(state).append(";");
        }
        final int count=registered;
        final String message="station="+c.toShortString()+" adopted="+count+states;
        src.sendSuccess(()->Component.literal("[TC railway staff] "+message),false);
        LOG.info(LOG_PREFIX+"STAFF_STATUS "+message);
        return 1;
    }

    /**
     * DEV50c3: Transfer a reserved old workplace to the new enclosed office in
     * the same command tick. This is deliberately not a generic villager reset:
     * do not erase careers/trades, refresh brains or claim unrelated villagers.
     */
    private static boolean transferOfficeWorksite(ServerLevel level, Villager villager,
                                                   String role, BlockPos station) {
        BlockPos newSite = station.offset("station_chief".equals(role) ? -4 : 4, 0, 9);
        var expectedBlock = "station_chief".equals(role)
                ? TCBlocks.STATION_OFFICE_DESK.get() : TCBlocks.TICKET_BOOTH.get();
        var expectedPoi = RailwayVillagers.jobSite(role);
        if (expectedPoi == null || !level.getBlockState(newSite).is(expectedBlock)) return false;
        // The source desk was removed by the office builder in this very tick;
        // the destination POI must be claimed before vanilla AI gets a chance
        // to tick again. Never search for some other villager's workstation.
        Optional<BlockPos> reserved = level.getPoiManager().take(
                holder -> holder.value() == expectedPoi.get(),
                (holder, pos) -> pos.equals(newSite), newSite, 1);
        if (reserved.isEmpty()) return false;
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                net.minecraft.core.GlobalPos.of(level.dimension(), newSite));
        return true;
    }

    private static int upgrade(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        StationData data=stations(level);
        Optional<BlockPos> recorded=data.closest(BlockPos.containing(src.getPosition()),64);
        if (recorded.isEmpty()) {src.sendFailure(Component.literal("No recorded pilot station within 64 blocks"));return 0;}
        BlockPos c=recorded.get();
        if (data.enclosed(c)) {src.sendFailure(Component.literal("Station already has an enclosed office"));return 0;}
        if (!mayUpgrade(level,c)) {
            src.sendFailure(Component.literal("Existing station differs from DEV50b1 layout, or room extension is obstructed. NO BLOCKS CHANGED."));
            return 0;
        }
        // Fail closed on absent, distant, duplicated or modified old staff.
        // Legacy station workers had no station UUID record, but DID have a
        // precise old workstation at z=4; this is stronger than proximity.
        Map<String,Villager> moving = new HashMap<>();
        AABB scan = new AABB(c.offset(-14,-2,-12),c.offset(15,8,20));
        for (Villager villager : level.getEntitiesOfClass(Villager.class,scan)) {
            String role=villager.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
            if (!"station_chief".equals(role) && !"train_conductor".equals(role)) continue;
            if (villager.getPersistentData().contains(STAFF_STATION_TAG,Tag.TAG_LONG)
                    && villager.getPersistentData().getLong(STAFF_STATION_TAG)!=c.asLong()) continue;
            BlockPos oldSite=c.offset("station_chief".equals(role) ? -4 : 4,0,4);
            boolean atOldDesk=villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(p -> p.dimension().equals(level.dimension()) && p.pos().equals(oldSite)).isPresent();
            StaffSlot slot=data.getStaff(c,role);
            boolean recordedWorker=slot!=null && villager.getUUID().equals(slot.uuid);
            if (!atOldDesk && !recordedWorker) continue; // not proven to be THIS station's worker
            if (moving.containsKey(role) || villager.getVillagerData().getProfession()!=RailwayVillagers.forEgg(role).get()
                    || villager.getOffers().isEmpty()) {
                src.sendFailure(Component.literal("Station staff ambiguous or damaged ("+role+"); office NOT upgraded"));
                return 0;
            }
            moving.put(role,villager);
        }
        if (moving.size()==1 || (WORKERS.get() && moving.size()!=2)) {
            src.sendFailure(Component.literal("Railway staff not both safely identified. Wait until daytime and both are at their old desks; NO CHANGES."));
            return 0;
        }
        // Never enclose or teleport an unrelated entity inside a solid wall.
        AABB newRoom = new AABB(c.offset(-6,0,5),c.offset(7,5,12));
        for (LivingEntity occupant : level.getEntitiesOfClass(LivingEntity.class,newRoom)) {
            if (!moving.containsValue(occupant)) {
                src.sendFailure(Component.literal("New office contains a non-staff entity; NO CHANGES."));
                return 0;
            }
        }
        for (Map.Entry<String,Villager> entry : moving.entrySet()) {
            BlockPos indoors=c.offset("station_chief".equals(entry.getKey()) ? -2 : 2,0,8);
            entry.getValue().moveTo(indoors.getX()+0.5,indoors.getY(),indoors.getZ()+0.5,
                    entry.getValue().getYRot(),entry.getValue().getXRot());
            // DO NOT erase the old JOB_SITE here: we must keep the profession
            // protected until the new workstation is constructed and reserved.
        }
        buildEnclosedOffice(level,c,true);
        boolean complete=true;
        for (Map.Entry<String,Villager> entry : moving.entrySet()) {
            boolean linked=transferOfficeWorksite(level,entry.getValue(),entry.getKey(),c);
            LOG.info("[TC-DEV50C3] OFFICE_JOB_TRANSFER station={} role={} uuid={} newJobSite={} reserved={}",
                    c,entry.getKey(),entry.getValue().getUUID(),
                    c.offset("station_chief".equals(entry.getKey()) ? -4 : 4,0,9),linked);
            complete &= linked;
        }
        if (!complete) {
            // Be explicit that building already happened, and do not claim a
            // successful transaction. No other saved station records changed.
            LOG.error("[TC-DEV50C3] OFFICE_JOB_TRANSFER_INCOMPLETE station={}; inspect staff/POIs before playing",c);
            src.sendFailure(Component.literal("Office built but workstation POI transfer failed. SAVE/EXIT AND SEND LOG; no success recorded."));
            return 0;
        }
        data.markEnclosed(c);
        decision("UPGRADED station="+c);
        src.sendSuccess(()->Component.literal("Enclosed ticket office, lanterns and linked railway jobs installed at "+c.toShortString()),true);
        return 1;
    }

    private static int commandSite(CommandSourceStack src, boolean place) {
        ServerLevel level = src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("DEV50b1a stations only support the Overworld")); return 0;
        }
        BlockPos origin=BlockPos.containing(src.getPosition());
        VillageProbe probe=inspectVillage(level,origin,96);
        if (probe.center().isEmpty()) {
            src.sendFailure(Component.literal("No natural village identified: homes="+probe.homes()
                    +" clustered="+probe.clustered()+" villageStructure="+probe.natural()
                    +". Run /tcrailway diagnose."));return 0;
        }
        StationData saved=stations(level);
        if(saved.near(probe.center().get(),ONE_PER_RADIUS.get())) {
            src.sendFailure(Component.literal("Station already recorded; use /tcrailway upgrade if it was built by DEV50b1."));return 0;
        }
        SiteSearch sites=inspectSites(level,probe.center().get());
        if(sites.site().isEmpty()) {
            src.sendFailure(Component.literal("No safe fully loaded station site: "+sites.rejections()));return 0;
        }
        Site chosen=sites.site().get();
        if(!place) {
            src.sendSuccess(()->Component.literal("Safe enclosed railway station site "+chosen.center().toShortString()
                    +"; use /tcrailway place"),false);return 1;
        }
        if(!safeFootprint(level,chosen.center())) {
            src.sendFailure(Component.literal("Terrain changed; cancelled safely"));return 0;
        }
        placeStation(level,chosen);
        if (r40j4TestWorld(level)) r40j4AddSupports(level,chosen.center());
        saved.add(chosen.center(),true);
        saved.setService(chosen.center(),chooseService(level,chosen.center(),0));
        LOG.info(LOG_PREFIX+"COMMAND station={} village={}",chosen.center(),chosen.villageReference());
        src.sendSuccess(()->Component.literal("Built enclosed railway station at "+chosen.center().toShortString()),true);
        return 1;
    }

    // DEV50b2a: original MANUAL network implementation remains unchanged.
    // DEV50e2 reuses its exact plan through a separate, guarded deferred pilot;
    // never place the full network inside a ChunkEvent callback.
    // Station ownership is stored with the already accepted station save data.
    private record NetworkPlan(Map<BlockPos, BlockState> supports,
                               Map<BlockPos, BlockState> rails,
                               Map<BlockPos, BlockState> decoration,
                               Set<BlockPos> protectedStationRails) {}
    private record NetworkAudit(int total, int wrong, String firstMismatch) {}

    private static boolean inTurnout(int x) {
        return (x >= -19 && x <= -11) || (x >= 10 && x <= 18);
    }

    private static void addTrack(Map<BlockPos, BlockState> rails, BlockPos p, BlockState s) {
        BlockState old = rails.putIfAbsent(p, s);
        if (old != null && !old.equals(s)) throw new IllegalStateException("Track design overlap: " + p);
    }

    /** The full route is independent of how the station building is styled. */
    private static NetworkPlan planLegacyNetwork(BlockPos c) {
        Map<BlockPos, BlockState> supports = new LinkedHashMap<>();
        Map<BlockPos, BlockState> rails = new LinkedHashMap<>();
        Map<BlockPos, BlockState> decoration = new LinkedHashMap<>();
        Set<BlockPos> existing = new LinkedHashSet<>();
        BlockState normal = TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
        // 57-block main with the already-built 17-block station segment.
        for (int x = -27; x <= 29; x++) {
            BlockPos p = c.offset(x, 0, 0);
            if (x >= -8 && x <= 8) { existing.add(p); continue; }
            if (!inTurnout(x)) addTrack(rails, p, normal);
        }
        // Both turnouts use accepted dedicated GUIDE-cell geometry, not a
        // decorative 1-block rail bend. Left points toward the freight spur;
        // right points toward unfinished engineering works.
        addSwitch(rails, c.offset(-19, 0, 0),
                TCBlocks.SWITCH_PARALLEL_LEFT_SEGMENT.get(), LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT);
        addSwitch(rails, c.offset(10, 0, 0),
                TCBlocks.SWITCH_PARALLEL_RIGHT_SEGMENT.get(), LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT);
        // A real connected freight siding with a physical Train Buffer.
        for (int x=-10;x<=24;x++) addTrack(rails, c.offset(x,0,-3),normal);
        decoration.put(c.offset(25,0,-3), TCBlocks.STOPPER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)
                .setValue(TrainBufferBlock.ENERGY, false));
        // Work-in-progress spur: short rail end, then ballasted-but-unlaid
        // grade, materials, and a safety light. No false claim of connectivity.
        for (int x=19;x<=24;x++) addTrack(rails,c.offset(x,0,3),normal);
        for (int x=25;x<=29;x++) supports.put(c.offset(x,-1,3),Blocks.GRAVEL.defaultBlockState());
        decoration.put(c.offset(27,0,4), Blocks.BARREL.defaultBlockState()
                .setValue(BarrelBlock.FACING, Direction.UP));
        decoration.put(c.offset(29,0,4), Blocks.YELLOW_CONCRETE.defaultBlockState());
        decoration.put(c.offset(29,1,4), Blocks.TORCH.defaultBlockState());
        for (BlockPos p : rails.keySet()) supports.put(p.below(),Blocks.GRAVEL.defaultBlockState());
        // Buffer and service markers need solid support, too.
        for (BlockPos p : decoration.keySet()) if (p.getY()==c.getY())
            supports.putIfAbsent(p.below(),Blocks.GRAVEL.defaultBlockState());
        return new NetworkPlan(supports,rails,decoration,existing);
    }

    // DEV50b2e: the OLD plan remains exact for previously placed stations.
    // The switch branch exits +/-3 cells from the main. We never stretch or
    // teleport that connector to five cells: use the accepted medium parallel
    // S-curve assemblies to shift its exit gradually by another two cells.
    private static NetworkPlan planNetwork(BlockPos c) {
        Map<BlockPos, BlockState> supports = new LinkedHashMap<>();
        Map<BlockPos, BlockState> rails = new LinkedHashMap<>();
        Map<BlockPos, BlockState> decoration = new LinkedHashMap<>();
        Set<BlockPos> existing = new LinkedHashSet<>();
        BlockState normal = TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                .setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
        // Longer main line maintains a separate operating track alongside
        // the construction spur after the medium S-curve handoff.
        for (int x=-27; x<=42; x++) {
            BlockPos p=c.offset(x,0,0);
            if (x>=-8 && x<=8) { existing.add(p); continue; }
            if (!inTurnout(x)) addTrack(rails,p,normal);
        }
        // Keep both proven, immutable switch footprints and routing states.
        addSwitch(rails,c.offset(-19,0,0),TCBlocks.SWITCH_PARALLEL_LEFT_SEGMENT.get(),
                LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT);
        addSwitch(rails,c.offset(10,0,0),TCBlocks.SWITCH_PARALLEL_RIGHT_SEGMENT.get(),
                LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT);
        // The freight turnout ends at x=-11,z=-3. Curve starts adjacent,
        // at -10,-3, and ends at +1,-5. From x=2 both rails run parallel,
        // fully five blocks apart.
        addParallelTransition(rails,c.offset(-10,0,-3),false);
        for(int x=2;x<=24;x++) addTrack(rails,c.offset(x,0,-5),normal);
        decoration.put(c.offset(25,0,-5),TCBlocks.STOPPER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING,Direction.WEST)
                .setValue(TrainBufferBlock.ENERGY,false));
        // Construction branch similarly exits at +18,+3, uses an actual
        // accepted S-curve 19..30, then ends on a five-cell offset.
        addParallelTransition(rails,c.offset(19,0,3),true);
        for(int x=31;x<=36;x++) addTrack(rails,c.offset(x,0,5),normal);
        for(int x=37;x<=40;x++) supports.put(c.offset(x,-1,5),Blocks.GRAVEL.defaultBlockState());
        // Store decorative materials off the *future* rail swept corridor.
        decoration.put(c.offset(37,0,9),Blocks.BARREL.defaultBlockState()
                .setValue(BarrelBlock.FACING,Direction.UP));
        decoration.put(c.offset(40,0,9),Blocks.YELLOW_CONCRETE.defaultBlockState());
        decoration.put(c.offset(40,1,9),Blocks.TORCH.defaultBlockState());
        for(BlockPos p:rails.keySet()) supports.put(p.below(),Blocks.GRAVEL.defaultBlockState());
        for(BlockPos p:decoration.keySet()) if(p.getY()==c.getY())
            supports.putIfAbsent(p.below(),Blocks.GRAVEL.defaultBlockState());
        return new NetworkPlan(supports,rails,decoration,existing);
    }

    private static void addParallelTransition(Map<BlockPos,BlockState> rails,
                                              BlockPos root,boolean positive) {
        LegacyBatchTrackSpec spec = positive
                ? LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT
                : LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT;
        net.minecraft.world.level.block.Block block = positive
                ? TCBlocks.PARALLEL_CURVE_MEDIUM_RIGHT_SEGMENT.get()
                : TCBlocks.PARALLEL_CURVE_MEDIUM_LEFT_SEGMENT.get();
        if (!(block instanceof traincraft.block.track.LegacyBatchRailBlock piece)
                || piece.getSpec()!=spec)
            throw new IllegalStateException("Parallel-curve registry mismatch: " + spec.id());
        for(int part=0;part<spec.partCount();part++)
            addTrack(rails,spec.offsetForPart(root,Direction.EAST,part),
                    piece.stateForPart(Direction.EAST,part,false));
    }

    private static void addSwitch(Map<BlockPos,BlockState> target, BlockPos root,
                                  net.minecraft.world.level.block.Block block,
                                  LegacyBatchTrackSpec spec) {
        if (!(block instanceof LegacyBatchSwitchRailBlock sw))
            throw new IllegalStateException("Expected accepted switch: " + spec.id());
        if (sw.getSpec() != spec) throw new IllegalStateException("Turnout registry mismatch: " + spec.id());
        for (int i=0;i<spec.partCount();i++)
            addTrack(target, spec.offsetForPart(root,Direction.EAST,i),
                    sw.stateForPart(Direction.EAST,i,false));
    }

    // Preflight every target before changing a single block. Nothing outside
    // a loaded, single-level, unoccupied rural corridor can be overwritten.
    private static String networkProblem(ServerLevel level,BlockPos c,NetworkPlan plan) {
        if (!level.getBlockState(c).is(TCBlocks.TRACK_STATION.get())) return "missing_station_track";
        for (BlockPos p : plan.protectedStationRails())
            if (!level.getBlockState(p).is(p.equals(c) ? TCBlocks.TRACK_STATION.get() : TCBlocks.TRACK_NORMAL.get()))
                return "existing_station_rail_changed:"+p.toShortString();
        Set<BlockPos> targets = new LinkedHashSet<>(plan.supports().keySet());
        targets.addAll(plan.rails().keySet());
        targets.addAll(plan.decoration().keySet());
        for (BlockPos p : targets) {
            if (!level.hasChunkAt(p)) return "unloaded:"+p.toShortString();
            if (level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid())
                return "village_building:"+p.toShortString();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            if (y!=c.getY()) return "uneven:"+p.toShortString()+" height="+y;
            // No conversion of player blocks, fluid, existing rails or POIs.
            if (p.getY()==c.getY()-1) {
                BlockState ground=level.getBlockState(p);
                if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT))
                    return "protected_ground:"+p.toShortString();
            } else if (!level.getBlockState(p).isAir()) return "obstructed:"+p.toShortString();
        }
        // DEV50b2b: test the actual placement cells, not an encompassing
        // rectangle containing the existing staffed station. Railway workers
        // walking on the platform are safe; any mob/train occupying a real
        // rail or decoration target still blocks the entire placement.
        // (Ground support below a mob's feet does not intersect its AABB.)
        net.minecraft.world.phys.AABB vicinity = new net.minecraft.world.phys.AABB(
                c.offset(-29, 0, -7), c.offset(45, 6, 13));
        Set<BlockPos> occupiedTargets = new LinkedHashSet<>(plan.rails().keySet());
        occupiedTargets.addAll(plan.decoration().keySet());
        for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class, vicinity)) {
            // Preserve the DEV50b2a player exemption for manual testing.
            if (entity instanceof net.minecraft.world.entity.player.Player) continue;
            net.minecraft.world.phys.AABB body = entity.getBoundingBox();
            for (BlockPos p : occupiedTargets) {
                if (body.intersects(new net.minecraft.world.phys.AABB(p))) {
                    return "entity:" + entity.getType() + " at="
                            + entity.blockPosition().toShortString()
                            + " overlaps_target=" + p.toShortString();
                }
            }
        }
        return null;
    }

    private static Optional<BlockPos> nearbyStation(ServerLevel level,BlockPos pos) {
        return stations(level).closest(pos,96);
    }

    // DEV50b2b: a distance failure must tell the tester which station to
    // approach. This is advisory only: it never relaxes the 96-block guard.
    private static void explainMissingNetworkStation(CommandSourceStack src, ServerLevel level) {
        BlockPos from = BlockPos.containing(src.getPosition());
        Optional<BlockPos> nearest = stations(level).placed.stream()
                .min(Comparator.comparingDouble(from::distSqr));
        if (nearest.isPresent()) {
            BlockPos p = nearest.get();
            int distance = (int)Math.round(Math.sqrt(from.distSqr(p)));
            src.sendFailure(Component.literal("No recorded railway station within 96 blocks. Nearest="
                    + p.toShortString() + " (about " + distance
                    + " blocks away). Move within 96 blocks and retry."));
        } else {
            src.sendFailure(Component.literal("No recorded railway station in this dimension. "
                    + "Build one safely with /tcrailway preview and /tcrailway place."));
        }
    }

    private static int networkCommand(CommandSourceStack src,boolean place) {
        if (PLAYER_OPERATED_STATIONS_ONLY) {src.sendFailure(Component.literal("Generated network construction retired. Lay tracks yourself; network audit remains available."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Surface network test is Overworld-only"));return 0;
        }
        Optional<BlockPos> near=nearbyStation(level,BlockPos.containing(src.getPosition()));
        if (near.isEmpty()) {explainMissingNetworkStation(src,level);return 0;}
        BlockPos c=near.get();
        if (stations(level).hasNetwork(c)) {
            src.sendFailure(Component.literal("Network already recorded at "+c.toShortString()+"; use network audit"));return 0;
        }
        NetworkPlan plan=planNetwork(c);
        String reason=networkProblem(level,c,plan);
        if (reason!=null) {
            decision("NETWORK_REJECT station="+c+" reason="+reason);
            src.sendFailure(Component.literal("Network blocked safely: "+reason));return 0;
        }
        if (!place) {
            src.sendSuccess(()->Component.literal("Network site safe: "+c.toShortString()+
                    " rails="+plan.rails().size()+" supports="+plan.supports().size()+
                    " main=70 blocks sidings=5-block-center WIP=unfinished; /tcrailway network place"),false);
            return 1;
        }
        // Immediate best-effort rollback on any post-preflight exception.
        Map<BlockPos,BlockState> before = new LinkedHashMap<>();
        try {
            for (BlockPos p:plan.supports().keySet()) before.put(p,level.getBlockState(p));
            for (BlockPos p:plan.rails().keySet()) before.putIfAbsent(p,level.getBlockState(p));
            for (BlockPos p:plan.decoration().keySet()) before.putIfAbsent(p,level.getBlockState(p));
            for (BlockPos p:plan.protectedStationRails()) before.putIfAbsent(p,level.getBlockState(p));
            // Flag 2 avoids vanilla rail neighbor-shape renegotiation while
            // assembling the multi-cell turnout guide path.
            plan.supports().forEach((p,s)->level.setBlock(p,s,3));
            plan.rails().forEach((p,s)->level.setBlock(p,s,3));
            plan.decoration().forEach((p,s)->level.setBlock(p,s,3));
            // Match the accepted LegacyBatchTrackItem two-pass canonical
            // state lock, without changing its source code.
            plan.rails().forEach((p,s)->level.setBlock(p,s,2));
            NetworkAudit audit=checkNetwork(level,plan);
            if (audit.wrong()!=0) throw new IllegalStateException("post-placement "+audit.firstMismatch());
            for (BlockPos p:plan.protectedStationRails())
                if (!level.getBlockState(p).equals(before.get(p)))
                    throw new IllegalStateException("original station rail changed: "+p);
            stations(level).markWideNetwork(c);
            decision("NETWORK_SUCCESS station="+c+" tracks="+plan.rails().size());
            LOG.info(LOG_PREFIX+"NETWORK_BUILT station={} rails={} supports={} main=57 freight=true wip=true",
                    c,plan.rails().size(),plan.supports().size());
            src.sendSuccess(()->Component.literal("Placed WIDE surface railway: original turnouts, medium S-curves, 5-block-separated sidings"),true);
            return 1;
        } catch (RuntimeException ex) {
            List<Map.Entry<BlockPos,BlockState>> reverse=new ArrayList<>(before.entrySet());
            java.util.Collections.reverse(reverse);
            for (var entry:reverse) level.setBlock(entry.getKey(),entry.getValue(),2);
            LOG.error(LOG_PREFIX+"NETWORK_ROLLBACK station="+c,ex);
            src.sendFailure(Component.literal("Network placement rolled back: "+ex.getMessage()));return 0;
        }
    }

    // DEV50b2f: vanilla BarrelBlock.OPEN changes when a player opens the lid.
    // A lid animation is NOT a changed railway prop. Ignore only that bit,
    // while still strictly requiring the same vanilla block, FACING value,
    // and every other BlockState property. All rail states stay exact.
    private static boolean matchesNetworkCell(BlockState actual, BlockState expected) {
        if (actual.equals(expected)) return true;
        return expected.is(Blocks.BARREL)
                && actual.is(Blocks.BARREL)
                && actual.setValue(BarrelBlock.OPEN, expected.getValue(BarrelBlock.OPEN)).equals(expected);
    }

    private static NetworkAudit checkNetwork(ServerLevel level,NetworkPlan plan) {
        int wrong=0;String first="none";
        for (var entry:plan.rails().entrySet()) {
            BlockState actual=level.getBlockState(entry.getKey());
            BlockState expected=entry.getValue();
            if (!actual.equals(expected)) {
                wrong++;if(wrong==1)first="rail="+entry.getKey().toShortString()+" expected="+expected+" actual="+actual;
            }
        }
        for (var entry:plan.decoration().entrySet()) {
            BlockState actual = level.getBlockState(entry.getKey());
            BlockState expected = entry.getValue();
            if (!matchesNetworkCell(actual, expected)) {
                wrong++;
                if (wrong == 1) first = "decoration=" + entry.getKey().toShortString()
                        + " expected=" + expected + " actual=" + actual;
            }
        }
        return new NetworkAudit(plan.rails().size()+plan.decoration().size(),wrong,first);
    }

    // DEV50b2c: repair the single vanilla barrel prop placed by DEV50b2a/b
    // in worlds where the railway network already exists. Do not touch any
    // other barrel, container inventory, track, villager or station block.
    private static int fixExistingNetworkBarrel(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Network barrel repair is Overworld-only"));
            return 0;
        }
        Optional<BlockPos> near = nearbyStation(level, BlockPos.containing(src.getPosition()));
        if (near.isEmpty()) { explainMissingNetworkStation(src, level); return 0; }
        BlockPos center = near.get();
        if (!stations(level).hasNetwork(center)) {
            src.sendFailure(Component.literal("No recorded surface network at " + center.toShortString()));
            return 0;
        }
        BlockPos barrelPos = center.offset(
                stations(level).hasWideNetwork(center) ? 37 : 27,
                0, stations(level).hasWideNetwork(center) ? 9 : 4);
        if (!level.hasChunkAt(barrelPos)) {
            src.sendFailure(Component.literal("Construction barrel chunk is not loaded: " + barrelPos.toShortString()));
            return 0;
        }
        BlockState current = level.getBlockState(barrelPos);
        if (!current.is(Blocks.BARREL)) {
            src.sendFailure(Component.literal("Safety stop: construction prop is not a vanilla barrel at "
                    + barrelPos.toShortString() + "; nothing changed"));
            return 0;
        }
        if (current.getValue(BarrelBlock.FACING) == Direction.UP) {
            src.sendSuccess(() -> Component.literal("Construction barrel already upright at "
                    + barrelPos.toShortString()), false);
            return 1;
        }
        BlockState upright = current.setValue(BarrelBlock.FACING, Direction.UP);
        if (!level.setBlock(barrelPos, upright, 3) || !level.getBlockState(barrelPos).equals(upright)) {
            level.setBlock(barrelPos, current, 3);
            src.sendFailure(Component.literal("Barrel rotation failed; original orientation restored"));
            return 0;
        }
        LOG.info(LOG_PREFIX + "NETWORK_BARREL_UPRIGHT station={} barrel={}", center, barrelPos);
        src.sendSuccess(() -> Component.literal("Vanilla construction barrel rotated upright at "
                + barrelPos.toShortString() + "; /tcrailway network audit"), true);
        return 1;
    }

    private static NetworkPlan dev50e3bAuditedNetwork(StationData data,BlockPos station) {
        Boolean outboundWest=data.outboundWest(station);
        if(outboundWest!=null) return dev50e3bOutboundNetwork(station,outboundWest);
        return data.hasWideNetwork(station) ? planNetwork(station) : planLegacyNetwork(station);
    }

    // DEV50b2d: read-only clearance screen, intentionally separate from
    // the block-state audit. Never certify full fleet geometry from entity
    // collision hitboxes: many legacy models are wider/longer than those.
    private static int auditNetworkClearance(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Rail clearance screen is Overworld-only"));
            return 0;
        }
        Optional<BlockPos> near = nearbyStation(level, BlockPos.containing(src.getPosition()));
        if (near.isEmpty()) { explainMissingNetworkStation(src, level); return 0; }
        BlockPos center = near.get();
        if (!stations(level).hasNetwork(center)) {
            src.sendFailure(Component.literal("No recorded surface network at " + center.toShortString()
                    + "; run /tcrailway network audit first"));
            return 0;
        }
        boolean wide = stations(level).hasWideNetwork(center);
        NetworkPlan plan = dev50e3bAuditedNetwork(stations(level),center);
        NetworkAudit audit = checkNetwork(level, plan);
        Set<BlockPos> trackCells = new LinkedHashSet<>(plan.rails().keySet());
        trackCells.addAll(plan.protectedStationRails());
        return RailwayNetworkClearanceAudit.inspect(src, level, center, trackCells, audit.wrong(), wide);
    }

    // DEV50b2e: explicitly requested, guarded, manual-only in-world widening.
    // Nonempty barrel inventories, modified originals, missing chunks, builds,
    // villager/train intersection or a failed post-audit all STOP before save.
    // An old world remains LEGACY_V1 until /tcrailway network widen apply.
    private static int widenNetwork(CommandSourceStack src, boolean apply) {
        if (PLAYER_OPERATED_STATIONS_ONLY && apply) {src.sendFailure(Component.literal("Automatic network widening retired. Existing tracks stay unchanged."));return 0;}
        ServerLevel level=src.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Widening is Overworld-only")); return 0;
        }
        Optional<BlockPos> near=nearbyStation(level,BlockPos.containing(src.getPosition()));
        if(near.isEmpty()){explainMissingNetworkStation(src,level);return 0;}
        BlockPos c=near.get();
        StationData saved=stations(level);
        if(!saved.hasNetwork(c) || saved.hasWideNetwork(c)) {
            src.sendFailure(Component.literal("Requires recorded LEGACY_V1 railway; already wide or no network at "+c.toShortString()));
            return 0;
        }
        NetworkPlan old=planLegacyNetwork(c), next=planNetwork(c);
        NetworkAudit original=checkNetwork(level,old);
        if(original.wrong()!=0) {
            src.sendFailure(Component.literal("Safety stop: legacy audit mismatches="+original.wrong()
                    +" first="+original.firstMismatch()+"; no changes"));return 0;
        }
        for(BlockPos p:old.protectedStationRails()) {
            if(!level.getBlockState(p).is(p.equals(c)?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())) {
                src.sendFailure(Component.literal("Protected platform track changed: "+p.toShortString()));return 0;
            }
        }
        BlockPos barrel=c.offset(27,0,4);
        net.minecraft.world.level.block.entity.BlockEntity be=level.getBlockEntity(barrel);
        if(!(be instanceof net.minecraft.world.Container container)) {
            src.sendFailure(Component.literal("Expected original empty vanilla barrel inventory at "+barrel.toShortString()));return 0;
        }
        for(int slot=0;slot<container.getContainerSize();slot++) {
            if(!container.getItem(slot).isEmpty()) {
                src.sendFailure(Component.literal("Construction barrel is not empty; remove items before widening. NO CHANGES"));return 0;
            }
        }
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        // Track bodies that are obsolete become air; old ballast remains
        // untouched as a visible but no-longer-operational trackbed.
        for(BlockPos p:old.rails().keySet()) if(!next.rails().containsKey(p))
            changes.put(p,Blocks.AIR.defaultBlockState());
        for(BlockPos p:old.decoration().keySet()) if(!next.decoration().containsKey(p))
            changes.put(p,Blocks.AIR.defaultBlockState());
        next.rails().forEach((p,s)->changes.put(p,s));
        next.decoration().forEach((p,s)->changes.put(p,s));
        for(var entry:next.supports().entrySet())
            if(!old.supports().containsKey(entry.getKey())) changes.put(entry.getKey(),entry.getValue());
        // Exact baseline guard: no edits to protected old rail props;
        // never overwrite an unknown block or an existing rail.
        Set<BlockPos> originalCells=new LinkedHashSet<>(old.rails().keySet());
        originalCells.addAll(old.decoration().keySet());
        for(var entry:changes.entrySet()) {
            BlockPos p=entry.getKey();
            if(!level.hasChunkAt(p)) {
                src.sendFailure(Component.literal("Safety stop: unloaded "+p.toShortString()));return 0;
            }
            if(level.structureManager().getStructureWithPieceAt(p,StructureTags.VILLAGE).isValid()) {
                src.sendFailure(Component.literal("Safety stop: village structure "+p.toShortString()));return 0;
            }
            BlockState actual=level.getBlockState(p);
            if(originalCells.contains(p)) {
                BlockState expected=old.rails().containsKey(p)
                        ?old.rails().get(p):old.decoration().get(p);
                // Match all rail/prop states exactly, apart from an OPEN lid
                // on the verified vanilla barrel. Inventory guard above stays strict.
                if(!matchesNetworkCell(actual, expected)) {
                    src.sendFailure(Component.literal("Safety stop: modified original "
                            +p.toShortString()+" expected="+expected+" actual="+actual));return 0;
                }
            }else if(p.getY()==c.getY()-1) {
                if(!actual.is(Blocks.GRASS_BLOCK) && !actual.is(Blocks.DIRT)) {
                    src.sendFailure(Component.literal("Safety stop: protected foundation "+p.toShortString()));return 0;
                }
            }else if(!actual.isAir()) {
                src.sendFailure(Component.literal("Safety stop: obstructed "+p.toShortString()+" "+actual));return 0;
            }
        }
        // Don't relocate track or props through trains, villagers or animals.
        // As with old manual place, players executing the command are exempt.
        for(net.minecraft.world.entity.Entity entity:level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(c.offset(-29,0,-8),c.offset(45,6,13)))) {
            if(entity instanceof net.minecraft.world.entity.player.Player)continue;
            net.minecraft.world.phys.AABB body=entity.getBoundingBox();
            for(BlockPos p:changes.keySet()) {
                if(p.getY()>=c.getY() && body.intersects(new net.minecraft.world.phys.AABB(p))) {
                    src.sendFailure(Component.literal("Safety stop: entity "+entity.getType()
                            +" intersects "+p.toShortString()));return 0;
                }
            }
        }
        if(!apply) {
            src.sendSuccess(()->Component.literal("WIDE_V2 preview safe at "+c.toShortString()
                    +" changedCells="+changes.size()+" freight/works separation=5 blocks;"
                    +" apply only in a BACKED-UP TEST WORLD with /tcrailway network widen apply"),false);
            return 1;
        }
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        for(BlockPos p:changes.keySet())before.put(p,level.getBlockState(p));
        try {
            // Strip only exact original guide cells and decor, never a POI or
            // house. Then place additional ballast and the new accepted routes.
            for(var entry:changes.entrySet()) if(entry.getValue().isAir())
                level.setBlock(entry.getKey(),entry.getValue(),3);
            for(var entry:changes.entrySet()) if(entry.getKey().getY()==c.getY()-1)
                level.setBlock(entry.getKey(),entry.getValue(),3);
            for(var entry:changes.entrySet()) if(!entry.getValue().isAir()
                    && entry.getKey().getY()!=c.getY()-1)
                level.setBlock(entry.getKey(),entry.getValue(),3);
            next.rails().forEach((p,s)->level.setBlock(p,s,2));
            NetworkAudit after=checkNetwork(level,next);
            if(after.wrong()!=0)throw new IllegalStateException("post-widen mismatch "+after.firstMismatch());
            for(BlockPos p:next.protectedStationRails())
                if(!level.getBlockState(p).is(p.equals(c)?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get()))
                    throw new IllegalStateException("protected station rail changed "+p);
            saved.markWideNetwork(c);
            LOG.info(LOG_PREFIX+"NETWORK_WIDEN_SUCCESS station={} cells={} rails={}",c,changes.size(),next.rails().size());
            src.sendSuccess(()->Component.literal("WIDE_V2 railway complete at "+c.toShortString()
                    +"; use /tcrailway network audit and /tcrailway network clearance"),true);
            return 1;
        }catch(RuntimeException ex) {
            List<Map.Entry<BlockPos,BlockState>> reverse=new ArrayList<>(before.entrySet());
            java.util.Collections.reverse(reverse);
            for(var entry:reverse)level.setBlock(entry.getKey(),entry.getValue(),2);
            // Barrel is refused unless empty; its inventory can be restored
            // by the original vanilla block if the world update failed.
            LOG.error(LOG_PREFIX+"NETWORK_WIDEN_ROLLBACK station="+c,ex);
            src.sendFailure(Component.literal("Widening rolled back: "+ex.getMessage()));return 0;
        }
    }

    private static int auditNetwork(CommandSourceStack src) {
        ServerLevel level=src.getLevel();
        Optional<BlockPos> near=nearbyStation(level,BlockPos.containing(src.getPosition()));
        if (near.isEmpty()) {explainMissingNetworkStation(src,level);return 0;}
        BlockPos c=near.get();
        boolean recorded=stations(level).hasNetwork(c);
        boolean wide=stations(level).hasWideNetwork(c);
        NetworkPlan plan=dev50e3bAuditedNetwork(stations(level),c);
        NetworkAudit audit=checkNetwork(level,plan);
        String layout=stations(level).outboundWest(c)!=null ? "WIDE_V2_OUTBOUND" : wide?"WIDE_V2":"LEGACY_V1";
        src.sendSuccess(()->Component.literal("Rail network station="+c.toShortString()
                +" recorded="+recorded+" layout="+layout+" cells="+audit.total()
                +" mismatches="+audit.wrong()+" first="+audit.firstMismatch()),false);
        LOG.info(LOG_PREFIX+"NETWORK_AUDIT station={} recorded={} tested={} wrong={} first={}",
                c,recorded,audit.total(),audit.wrong(),audit.firstMismatch());
        return audit.wrong()==0 && recorded ? 1 : 0;
    }

}
