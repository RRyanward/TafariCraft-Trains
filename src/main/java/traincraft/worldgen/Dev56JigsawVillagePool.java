/*
 * TafariCraft - Trains DEV56J2; Minecraft 1.20.1 / Forge 47.
 * Native vanilla jigsaw-pool injection in tightly guarded disposable worlds.
 * Recovery from external Structure Pool API 1.2.1 development mixin crash:
 *   structure_pool.mixins.json:StructurePoolAccessor f_210560_ InvalidAccessorException
 * No third-party mixins, no production saves, no automatic chunk scanning, no lighting edits.
 * The J5 test replaces 25 large footprints with compact 11x13 station pieces.
 */
package traincraft.worldgen;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Native registry-pool augmentation. Does NOT require the third-party Structure Pool API.
 * The API's bundled mixin crashes ForgeGradle userdev before world load.
 *
 * Both private collections are staged before any mutation; all five pools
 * are rolled back on failure to avoid half-injected registry state.
 * Only the precise disposable integrated test world is ever targeted.
 *
 * Caveat: weighted pool entries do not promise 35 percent of villages, and
 * vanilla's native jigsaw mechanism does NOT enforce one station per village.
 * Those questions require actual world tests before production rollout.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56JigsawVillagePool {
    public static final String WORLD = "DEV56 Pool Test J5";
    public static final String J19_WORLD = "DEV56 J19 Village Test";
    private static final String J4_TEST_WORLD = "DEV56 Pool Test J4";
    private static final String J3_TEST_WORLD = "DEV56 Pool Test J3";
    // Diagnostic-only multiplier; exactly 1 in J4/J3/J2 worlds, never any production world.
    private static final int J5_MULTIPLIER = 1;
    private static final String EARLIER_TEST_WORLD = "DEV56 Pool Test";
    private static final String ROOT = "D:/TafariCraft - Reborn Development/Traincraft";
    private static final String[] BIOMES = { "plains", "desert", "savanna", "taiga", "snowy" };
    private static final String[] VARIANTS = {
            "active_depot", "active_halt", "abandoned", "overgrown", "subway"
    };
    private static final int[] WEIGHTS = { 12, 12, 8, 6, 4 }; // J26R1: Subway ~9.5% of weighted candidates; original 35% gate unchanged
    private static final int EXPECTED = BIOMES.length * VARIANTS.length;
    private static boolean active;
    private static boolean complete;
    private static int count;
    private static String last = "NOT_STARTED";
    private static int weightMultiplier = 1;

    private Dev56JigsawVillagePool() {}

    public static boolean isTestWorld(MinecraftServer server) {
        return server != null;
    }

    private record StagedPool(StructureTemplatePool pool, Object priorRaw, Object priorElements,
                              List<Pair<StructurePoolElement, Integer>> nextRaw,
                              ObjectArrayList<StructurePoolElement> nextElements) {}

    private static Field requireField(String deobfuscated, String srg) throws ReflectiveOperationException {
        Class<?> type = StructureTemplatePool.class;
        Field field;
        try {
            field = type.getDeclaredField(deobfuscated);
        } catch (NoSuchFieldException ignored) {
            field = type.getDeclaredField(srg);
        }
        if (!field.trySetAccessible()) {
            throw new IllegalAccessException("Access denied to StructureTemplatePool." + field.getName());
        }
        return field;
    }

    private static List<Pair<StructurePoolElement, Integer>> copyRaw(Object value)
            throws ReflectiveOperationException {
        if (!(value instanceof List<?> list)) {
            throw new ReflectiveOperationException("vanilla rawTemplates not a List");
        }
        List<Pair<StructurePoolElement, Integer>> copy = new ArrayList<>(list.size() + 5);
        for (Object item : list) {
            if (!(item instanceof Pair<?, ?> pair)
                    || !(pair.getFirst() instanceof StructurePoolElement)
                    || !(pair.getSecond() instanceof Integer)) {
                throw new ReflectiveOperationException("vanilla rawTemplates element type changed");
            }
            @SuppressWarnings("unchecked")
            Pair<StructurePoolElement, Integer> entry = (Pair<StructurePoolElement, Integer>) item;
            copy.add(entry);
        }
        return copy;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        active = isTestWorld(server);
        weightMultiplier = active && (WORLD.equals(server.getWorldData().getLevelName()) || J19_WORLD.equals(server.getWorldData().getLevelName())) ? J5_MULTIPLIER : 1;
        complete = false;
        count = 0;
        last = active ? "BEGIN" : "NOT_TEST_WORLD";
        if (!active) return;

        // Accessors are needed only for this one test world, never at mod startup.
        List<StagedPool> staged = new ArrayList<>(BIOMES.length);
        Field rawField = null;
        Field elementsField = null;
        try {
            Registry<StructureTemplatePool> registry = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            rawField = requireField("rawTemplates", "f_210559_");
            elementsField = requireField("templates", "f_210560_");
            if (!List.class.isAssignableFrom(rawField.getType())
                    || !ObjectArrayList.class.isAssignableFrom(elementsField.getType())) {
                throw new ReflectiveOperationException("Unexpected vanilla pool field types");
            }

            // PREPARE: do not change any registry data until ALL 25 NBT resources,
            // all five vanilla pools, and both internal list types are confirmed.
            for (String biome : BIOMES) {
                ResourceLocation poolId = new ResourceLocation("minecraft", "village/" + biome + "/houses");
                StructureTemplatePool pool = registry.get(poolId);
                if (pool == null) throw new ReflectiveOperationException("Missing vanilla pool " + poolId);
                Object oldRaw = rawField.get(pool);
                Object oldExpanded = elementsField.get(pool);
                if (!(oldExpanded instanceof ObjectArrayList<?> items)) {
                    throw new ReflectiveOperationException("Unexpected vanilla jigsaw elements for " + poolId);
                }
                List<Pair<StructurePoolElement, Integer>> nextRaw = copyRaw(oldRaw);
                ObjectArrayList<StructurePoolElement> nextExpanded = new ObjectArrayList<>();
                for (Object obj : items) {
                    if (!(obj instanceof StructurePoolElement e)) {
                        throw new ReflectiveOperationException("Unexpected element for " + poolId);
                    }
                    nextExpanded.add(e);
                }
                for (int v = 0; v < VARIANTS.length; v++) {
                    String variant = VARIANTS[v];
                    ResourceLocation id = new ResourceLocation("traincraft", "dev56/" + biome + "/" + variant);
                    String resource = "/data/traincraft/structures/dev56/" + biome + "/" + variant + ".nbt";
                    try (var stream = Dev56JigsawVillagePool.class.getResourceAsStream(resource)) {
                        if (stream == null) throw new ReflectiveOperationException("Missing station structure " + resource);
                    }
                    StructurePoolElement element = StructurePoolElement.single(id.toString())
                            .apply(StructureTemplatePool.Projection.RIGID);
                    int injectedWeight = WEIGHTS[v] * weightMultiplier;
                    nextRaw.add(Pair.of(element, injectedWeight));
                    for (int repeat = 0; repeat < injectedWeight; repeat++) nextExpanded.add(element);
                }
                staged.add(new StagedPool(pool, oldRaw, oldExpanded, nextRaw, nextExpanded));
            }

            // COMMIT: keep both raw pool definitions and expanded weighted lists in sync.
            // On any failed field set, restore original references in reverse order.
            int attempted = 0;
            try {
                for (StagedPool update : staged) {
                    attempted++;
                    rawField.set(update.pool(), update.nextRaw());
                    elementsField.set(update.pool(), update.nextElements());
                }
            } catch (ReflectiveOperationException | RuntimeException commitFailure) {
                for (int i = attempted - 1; i >= 0; i--) {
                    try {
                        StagedPool prior = staged.get(i);
                        rawField.set(prior.pool(), prior.priorRaw());
                        elementsField.set(prior.pool(), prior.priorElements());
                    } catch (ReflectiveOperationException | RuntimeException rollbackFailure) {
                        Traincraft.LOGGER.error("[TC-DEV56J2] ROLLBACK_FAILURE pool={} STOP WORLD TEST",
                                staged.get(i).pool(), rollbackFailure);
                    }
                }
                throw commitFailure;
            }
            count = EXPECTED;
            complete = true;
            last = "NATIVE_INJECTED";
            Traincraft.LOGGER.info("[TC-DEV56J5] NATIVE_POOL_INJECTED entries={} pools=5 " +
                    "weights=12,12,8,6,4 multiplier={} oldScanner=OFF world={} " +
                    "selectionNotProofOfPlacement=true J19NewWorldHasFinalGate=true legacyPerVillageLimit=false",
                    count, weightMultiplier, server.getWorldData().getLevelName());
        } catch (Exception failure) {
            last = "NATIVE_HOLD:" + failure.getClass().getSimpleName() + ":" + failure.getMessage();
            Traincraft.LOGGER.error("[TC-DEV56J2] NATIVE_POOL_HOLD entries=0/{} " +
                    "world={} noFallback=true reason={}", EXPECTED, WORLD, last, failure);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        active = false;
        complete = false;
        count = 0;
        last = "SERVER_STOPPED";
        weightMultiplier = 1;
    }

    /**
     * On-demand observation only: inspects structure starts already present in LOADED
     * chunks near the player. No chunk tickets, worldgen calls, or writes.
     * Successful pool registration cannot diagnose a rejected jigsaw candidate;
     * this reports what survived into a village's saved piece list.
     */
    private static String loadedVillageReport(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        StructureStart nearest = null;
        ResourceLocation nearestId = null;
        double best = Double.POSITIVE_INFINITY;
        Set<String> seen = new HashSet<>();
        int inspected = 0;
        int cx = here.getX() >> 4;
        int cz = here.getZ() >> 4;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!level.hasChunk(x, z)) continue;
                inspected++;
                for (StructureStart start : level.getChunk(x, z).getAllStarts().values()) {
                    if (start == null || !start.isValid()) continue;
                    ResourceLocation key = structures.getKey(start.getStructure());
                    if (key == null || !"minecraft".equals(key.getNamespace())
                            || !key.getPath().startsWith("village_")) continue;
                    BoundingBox box = start.getBoundingBox();
                    String distinct = key + ":" + box.minX() + ":" + box.minZ();
                    if (!seen.add(distinct)) continue;
                    double dX = box.getCenter().getX() - here.getX();
                    double dZ = box.getCenter().getZ() - here.getZ();
                    double distance = dX * dX + dZ * dZ;
                    if (distance < best) {
                        best = distance;
                        nearest = start;
                        nearestId = key;
                    }
                }
            }
        }
        if (nearest == null) {
            return "[DEV56 J5 PIECES] loadedVillage=NONE loadedChunks=" + inspected
                    + " scanRadius=192 blocks. Stand inside a naturally generated village and retry.";
        }
        int jigsawPieces = 0;
        int dev56Pieces = 0;
        String stationInfo = "NONE";
        String example = "none";
        for (StructurePiece piece : nearest.getPieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                jigsawPieces++;
                String id = String.valueOf(poolPiece.getElement());
                if (id.contains("traincraft:dev56")) {
                    dev56Pieces++;
                    if (stationInfo.equals("NONE")) {
                        var bb = poolPiece.getBoundingBox();
                        stationInfo = id + "@" + bb.getCenter().getX() + "," + bb.getCenter().getY() + "," + bb.getCenter().getZ();
                        if (stationInfo.length() > 140) stationInfo = stationInfo.substring(0, 140);
                    }
                }
                if (example.equals("none")) {
                    example = id.length() > 95 ? id.substring(0, 95) : id;
                }
            }
        }
        BlockPos anchor = nearest.getBoundingBox().getCenter();
        return "[DEV56 J5 PIECES] village=" + nearestId + " center=" + anchor.getX()
                + "," + anchor.getZ() + " totalPieces=" + nearest.getPieces().size()
                + " jigsawPieces=" + jigsawPieces + " dev56Pieces=" + dev56Pieces
                + " loadedChunks=" + inspected + " station=" + stationInfo + " sample=" + example;
    }

    /** Read-only debug status; J4 village-piece report is on demand only. */
    public static int status(CommandSourceStack source) {
        boolean rightWorld = isTestWorld(source.getServer());
        String displayName = source.getServer().getWorldData().getLevelName();
        String message = "[DEV56 J5] testWorld=" + rightWorld + " world=" + displayName
                + " poolInjection=" + (complete ? "NATIVE_INJECTED" : last)
                + " templates=COMPACT_11x13_ROOF_V2"
                + " entries=" + count + "/" + EXPECTED
                + " pools=plains,desert,savanna,taiga,snowy"
                + " variantRatio=28.6/28.6/19.0/14.3/9.5"
                + " selectionMultiplier=" + weightMultiplier
                + " 35PercentPerVillage=" + (J19_WORLD.equals(displayName) ? "J19_SEED_GATE_CANDIDATE_FIT_DEPENDENT" : "NOT_GUARANTEED")
                + " nativePerVillageCap=" + (J19_WORLD.equals(displayName) ? "J19_STRUCT_START_ONE" : "NOT_ENFORCED")
                + " oldPostgenScanner=" + (rightWorld ? "OFF" : "NORMAL");
        source.sendSuccess(() -> Component.literal(message), false);
        if (rightWorld && complete && WORLD.equals(displayName)) {
            try {
                String report = loadedVillageReport(source);
                source.sendSuccess(() -> Component.literal(report), false);
                Traincraft.LOGGER.info("{}", report);
            } catch (RuntimeException ex) {
                Traincraft.LOGGER.warn("[TC-DEV56J5] DIAGNOSTIC_ONLY_FAILURE: {}", ex.toString());
                source.sendFailure(Component.literal("[DEV56 J5] Read-only village inspector unavailable: " + ex));
            }
        }
        return 1;
    }
}
