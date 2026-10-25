package traincraft.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;

/** Railway villager profession/workstation care. DEV56J15 modifies only J12-managed station workers.
 * J12 death/cooldown, vanilla villager AI, manual egg behavior and station generation stay unchanged.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RailwayVillagerCare {
    private RailwayVillagerCare() {}

    private static final String J12_MARKER = "TC_DEV56_JIGSAW_STAFF";
    private static final String J12_HOME = "TC_DEV56_JIGSAW_HOME";

    private static boolean j12Managed(Villager villager, String role) {
        return ("station_chief".equals(role) || "train_conductor".equals(role))
                && villager.getPersistentData().getBoolean(J12_MARKER)
                && villager.getPersistentData().contains(J12_HOME, Tag.TAG_LONG);
    }

    /** A correctly marked J12 worker must seek its station's desk, never a random village POI. */
    private static void bindJ12Station(ServerLevel level, Villager villager, String role, RegistryObject<PoiType> poi) {
        BlockPos home = BlockPos.of(villager.getPersistentData().getLong(J12_HOME));
        if (!level.hasChunkAt(home) || !level.hasChunkAt(home.above()) || !level.hasChunkAt(home.below())) return;
        PoiManager manager = level.getPoiManager();
        BlockPos chosen = null;
        int candidateCount = 0;
        // J12 home is a worker spawn point in the station, not the villager's wandering position.
        // A tight footprint ensures that a neighboring village or station's desk cannot be adopted.
        for (BlockPos cursor : BlockPos.betweenClosed(home.offset(-6, -3, -6), home.offset(6, 3, 6))) {
            if (!level.hasChunkAt(cursor)) continue;
            var mapped = PoiTypes.forState(level.getBlockState(cursor));
            if (mapped.isEmpty() || mapped.get().value() != poi.get()) continue;
            ++candidateCount;
            BlockPos site = cursor.immutable();
            if (chosen == null || site.distSqr(home) < chosen.distSqr(home)) chosen = site;
        }
        // Ambiguity could mean a manually edited site or another nearby station: fail closed.
        if (candidateCount != 1 || chosen == null) {
            if (villager.tickCount % 1200 == 0)
                RailwayVillagerDebug.report("J15_HOLD role=" + role + " reason=DESK_COUNT count=" + candidateCount
                        + " home=" + home.toShortString() + " uuid=" + villager.getUUID());
            return;
        }
        final BlockPos desk = chosen;
        if (!manager.exists(desk, holder -> holder.value() == poi.get())) {
            PoiTypes.forState(level.getBlockState(desk))
                    .filter(holder -> holder.value() == poi.get())
                    .ifPresent(holder -> manager.add(desk, holder));
            RailwayVillagerDebug.report("J15_POI_REGISTER role=" + role + " site=" + desk.toShortString());
        }
        if (!manager.exists(desk, holder -> holder.value() == poi.get())) return;

        // Never rewrite another loaded villager's current job-site assignment, nor create
        // duplicate J12 staff for the same role/home. In particular, do NOT release any POI.
        boolean competingOwner = level.getEntitiesOfClass(Villager.class, new AABB(desk).inflate(48, 24, 48))
                .stream().anyMatch(other -> other != villager && (
                    other.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                            .map(position -> position.dimension().equals(level.dimension()) && position.pos().equals(desk))
                            .orElse(false)
                    || (j12Managed(other, role)
                            && other.getPersistentData().getLong(J12_HOME) == home.asLong()
                            && role.equals(other.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG)))));
        if (competingOwner) {
            if (villager.tickCount % 1200 == 0)
                RailwayVillagerDebug.report("J15_HOLD role=" + role + " reason=OTHER_LOADED_OWNER site="
                        + desk.toShortString() + " uuid=" + villager.getUUID());
            return;
        }
        // Claim a free ticket through vanilla's POI manager when one exists.
        if (manager.getCountInRange(holder -> holder.value() == poi.get(), desk, 1,
                PoiManager.Occupancy.HAS_SPACE) > 0) {
            if (manager.take(holder -> holder.value() == poi.get(),
                    (holder, at) -> at.equals(desk), desk, 1).isPresent()) {
                villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), desk));
                RailwayVillagerDebug.report("J15_BIND role=" + role + " ticket=NEW site="
                        + desk.toShortString() + " uuid=" + villager.getUUID());
            }
            return;
        }
        // J11 sometimes already consumed this station role's *single* POI ticket, then
        // vanilla discarded JOB_SITE memory. For one uniquely marked J12 station worker,
        // reattach its memory to the existing reservation; NEVER release/take a second ticket.
        if (manager.getCountInRange(holder -> holder.value() == poi.get(), desk, 1,
                PoiManager.Occupancy.IS_OCCUPIED) == 1) {
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), desk));
            RailwayVillagerDebug.report("J15_BIND role=" + role + " ticket=EXISTING site="
                    + desk.toShortString() + " uuid=" + villager.getUUID());
        }
    }

    // DEV56J16: Respect missing player-removable station workstations. The exact J15
    // owned-ticket/POI binding path below remains unchanged whenever one desk exists.
    // Per-villager state is persisted solely to log one transition instead of spam.
    private static final String J16_BENCH_MISSING = "TC_DEV56_J16_BENCH_MISSING";

    private enum BenchStatus { READY, MISSING, UNLOADED, AMBIGUOUS }

    /** Inspect physical POI-compatible blocks near the saved J12 station home.
     * If any cell is unloaded, HOLD instead of treating an unloaded desk as broken.
     * This does not reserve, release, create, or destroy any POI ticket/block.
     */
    private static BenchStatus j16BenchStatus(ServerLevel level, Villager villager,
                                             RegistryObject<PoiType> poi) {
        BlockPos home = BlockPos.of(villager.getPersistentData().getLong(J12_HOME));
        int matched = 0;
        boolean missingChunk = false;
        for (BlockPos scan : BlockPos.betweenClosed(home.offset(-6, -3, -6), home.offset(6, 3, 6))) {
            if (!level.hasChunkAt(scan)) {
                missingChunk = true;
                continue;
            }
            if (PoiTypes.forState(level.getBlockState(scan))
                    .filter(holder -> holder.value() == poi.get()).isPresent()) ++matched;
        }
        if (missingChunk) return BenchStatus.UNLOADED;
        if (matched == 0) return BenchStatus.MISSING;
        if (matched == 1) return BenchStatus.READY;
        return BenchStatus.AMBIGUOUS; // never adopt a different or extra desk
    }

    /** A missing station desk is an intentional HOLD, never a profession-restore loop.
     * Vanilla may retain profession/offers after trading; don't forcibly clear them.
     * Stale JOB_SITE memory is invalidated only when its actual block is gone.
     */
    private static void careForJ12Bench(ServerLevel level, Villager villager, String role,
                                        RegistryObject<VillagerProfession> expected,
                                        RegistryObject<PoiType> poi) {
        BenchStatus status = j16BenchStatus(level, villager, poi);
        if (status == BenchStatus.UNLOADED || status == BenchStatus.AMBIGUOUS) return;
        boolean wasMissing = villager.getPersistentData().getBoolean(J16_BENCH_MISSING);
        if (status == BenchStatus.MISSING) {
            boolean erased = false;
            var memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (memory.isPresent()) {
                GlobalPos site = memory.get();
                if (site.dimension().equals(level.dimension()) && level.hasChunkAt(site.pos())
                        && PoiTypes.forState(level.getBlockState(site.pos()))
                                .filter(holder -> holder.value() == poi.get()).isEmpty()) {
                    villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                    erased = true;
                }
            }
            if (!wasMissing) {
                villager.getPersistentData().putBoolean(J16_BENCH_MISSING, true);
                RailwayVillagerDebug.report("J16_BENCH_MISSING role=" + role
                        + " home=" + BlockPos.of(villager.getPersistentData().getLong(J12_HOME)).toShortString()
                        + " erasedStaleJob=" + erased + " uuid=" + villager.getUUID());
            }
            // No RESTORE, no binding, no new trades, no death/respawn side effects.
            return;
        }
        if (wasMissing) {
            villager.getPersistentData().remove(J16_BENCH_MISSING);
            RailwayVillagerDebug.report("J16_BENCH_RETURNED role=" + role
                    + " home=" + BlockPos.of(villager.getPersistentData().getLong(J12_HOME)).toShortString()
                    + " uuid=" + villager.getUUID());
        }
        if (villager.getVillagerData().getProfession() != expected.get()) {
            villager.setVillagerData(villager.getVillagerData().setProfession(expected.get()));
            villager.refreshBrain(level);
            RailwayVillagerDebug.report("RESTORE role=" + role + " uuid=" + villager.getUUID());
        }
        bindWorkstation(level, villager, role);
        RailwayTrades.ensureNoviceOffers(villager);
    }

    /** Reserve one free matching POI ticket; J12 staff resolve around their saved station home. */
    public static void bindWorkstation(ServerLevel level, Villager villager, String role) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return;
        RegistryObject<PoiType> poi = RailwayVillagers.jobSite(role);
        if (poi == null) return;
        if (j12Managed(villager, role)) {
            bindJ12Station(level, villager, role, poi);
            return;
        }
        // Preserve exactly the accepted DEV56J11 behavior for eggs/legacy villagers.
        PoiManager manager = level.getPoiManager();
        BlockPos center = villager.blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            if (!level.hasChunkAt(candidate)) continue;
            if (manager.exists(candidate, holder -> holder.value() == poi.get())) continue;
            PoiTypes.forState(level.getBlockState(candidate))
                    .filter(holder -> holder.value() == poi.get())
                    .ifPresent(holder -> {
                        BlockPos site = candidate.immutable();
                        manager.add(site, holder);
                        RailwayVillagerDebug.report("POI_REPAIR role=" + role
                                + " pos=" + site.toShortString()
                                + " uuid=" + villager.getUUID());
                    });
        }
        manager.take(holder -> holder.value() == poi.get(),
                (holder, blockPos) -> true, center, 24)
                .ifPresent(blockPos -> villager.getBrain().setMemory(
                        MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), blockPos)));
    }

    // DEV56J17: Keep only naturally generated (J12-marked) staffed-station villagers
    // near their original station. Ordinary villagers and railway spawn eggs are excluded.
    // Vanilla AI still controls walking, sleeping, trading and normal activities inside the area.
    private static final double J17_SOFT_RADIUS_SQ = 12.0D * 12.0D;
    private static final double J17_HARD_RADIUS_SQ = 22.0D * 22.0D;
    private static final int J17_SOFT_HEIGHT = 5;
    private static final int J17_HARD_HEIGHT = 9;

    /** Only return to a safe, already-loaded floor near the saved J12 home.
     * Never create blocks, load chunks or teleport into an obstructed station.
     */
    private static BlockPos j17SafeReturn(ServerLevel level, BlockPos home) {
        final int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] offset : offsets) {
            BlockPos candidate = home.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(candidate) || !level.hasChunkAt(candidate.above())
                    || !level.hasChunkAt(candidate.below())
                    || !level.getWorldBorder().isWithinBounds(candidate)) continue;
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()
                    && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                return candidate;
            }
        }
        return null;
    }

    /** Periodic soft pathfinding, then emergency safe reposition for severe wanderers.
     * Skips mounted or sleeping workers. An existing far-away worker is rescued after
     * its chunk and its station are loaded; never load either chunk on purpose.
     */
    private static void j17StationBoundary(ServerLevel level, Villager villager, String role) {
        if (!j12Managed(villager, role) || villager.isPassenger() || villager.isSleeping()) return;
        BlockPos home = BlockPos.of(villager.getPersistentData().getLong(J12_HOME));
        if (!level.hasChunkAt(home)) return;
        double dx = villager.getX() - (home.getX() + 0.5D);
        double dz = villager.getZ() - (home.getZ() + 0.5D);
        double distSq = dx * dx + dz * dz;
        double dy = Math.abs(villager.getY() - home.getY());
        boolean beyondHard = distSq > J17_HARD_RADIUS_SQ || dy > J17_HARD_HEIGHT;
        if (beyondHard) {
            BlockPos safe = j17SafeReturn(level, home);
            if (safe == null) {
                if (villager.tickCount % 1200 == 0)
                    RailwayVillagerDebug.report("J17_HOLD role=" + role + " reason=NO_SAFE_LOADED_FLOOR home="
                            + home.toShortString() + " uuid=" + villager.getUUID());
                return;
            }
            BlockPos from = villager.blockPosition().immutable();
            villager.getNavigation().stop();
            villager.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
            RailwayVillagerDebug.report("J17_RETURN role=" + role + " from=" + from.toShortString()
                    + " to=" + safe.toShortString() + " home=" + home.toShortString()
                    + " uuid=" + villager.getUUID());
            return;
        }
        boolean beyondSoft = distSq > J17_SOFT_RADIUS_SQ || dy > J17_SOFT_HEIGHT;
        if (beyondSoft && villager.tickCount % 60 == 0) {
            villager.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            if (villager.tickCount % 1200 == 0)
                RailwayVillagerDebug.report("J17_GUIDE role=" + role + " pos="
                        + villager.blockPosition().toShortString() + " home=" + home.toShortString()
                        + " uuid=" + villager.getUUID());
        }
    }

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        // The boundary check is lighter and more frequent than the unchanged J16 care.
        // Preserve the five-second workstation/POI logic by returning at the same tick gate.
        if (villager.tickCount % 20 != 0) return;
        if (!villager.getPersistentData().contains(RailwayVillagerEgg.RAILWAY_ROLE_TAG, Tag.TAG_STRING)) return;
        String role = villager.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
        if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
            j17StationBoundary(level, villager, role);
        if (villager.tickCount % 100 != 0) return;
        RegistryObject<VillagerProfession> expected = RailwayVillagers.forEgg(role);
        if (expected == null) return;
        RegistryObject<PoiType> j16Poi = RailwayVillagers.jobSite(role);
        if (j12Managed(villager, role) && j16Poi != null) {
            careForJ12Bench(level, villager, role, expected, j16Poi);
            return;
        }
        if (villager.getVillagerData().getProfession() != expected.get()) {
            villager.setVillagerData(villager.getVillagerData().setProfession(expected.get()));
            villager.refreshBrain(level);
            RailwayVillagerDebug.report("RESTORE role=" + role + " uuid=" + villager.getUUID());
        }
        bindWorkstation(level, villager, role);
        RailwayTrades.ensureNoviceOffers(villager);
    }
}
