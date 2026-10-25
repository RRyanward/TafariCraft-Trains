package traincraft.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.registry.TCBlocks;

/** DEV56J12: Death-only, persistent cooldown replacement for NBT-marked jigsaw station staff.
 * Does not register manual eggs, create worldgen stations, or force-load any chunk.
 * Legacy RailwayVillageWorldgen station employees keep their existing separate timer.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56J12StationStaffLifecycle {
    private Dev56J12StationStaffLifecycle() {}
    public static final String JIGSAW_MARKER = "TC_DEV56_JIGSAW_STAFF";
    private static final String HOME = "TC_DEV56_JIGSAW_HOME";
    private static final String SAVE_NAME = "traincraft_dev56j12_jigsaw_staff";
    private static final int COOLDOWN_TICKS = 300 * 20; // Same five-minute default as managed legacy staff.
    private static final int CHECK_INTERVAL = 200; // Ten seconds; no active search between checks.

    private static boolean roleIsManaged(String role) {
        return "station_chief".equals(role) || "train_conductor".equals(role);
    }

    private static String role(Villager v) {
        CompoundTag nbt = v.getPersistentData();
        String role = nbt.getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
        return roleIsManaged(role) ? role : "";
    }

    private static boolean isMarked(Villager villager) {
        return villager.getPersistentData().getBoolean(JIGSAW_MARKER) && !role(villager).isEmpty();
    }

    private static BlockPos home(Villager villager) {
        CompoundTag tag = villager.getPersistentData();
        if (tag.contains(HOME, Tag.TAG_LONG)) return BlockPos.of(tag.getLong(HOME));
        BlockPos home = villager.blockPosition().immutable();
        tag.putLong(HOME, home.asLong());
        return home;
    }

    private static StaffData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StaffData::load, StaffData::new, SAVE_NAME);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || !(e.getEntity() instanceof Villager villager) || !isMarked(villager)) return;
        data(level).observe(home(villager), role(villager), villager.getUUID());
    }

    /** Zombie attacks kill railway villagers normally, rather than converting them.
     * Death registration below is still the only trigger for a replacement timer.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onConversion(LivingConversionEvent.Pre e) {
        if (!(e.getEntity() instanceof Villager villager)
                || e.getOutcome() != EntityType.ZOMBIE_VILLAGER) return;
        if (roleIsManaged(role(villager))) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD) || !isMarked(villager)) return;
        BlockPos home = home(villager);
        long due = level.getGameTime() + COOLDOWN_TICKS;
        if (data(level).markDeath(home, role(villager), villager.getUUID(), due)) {
            Traincraft.LOGGER.info("[DEV56J12] STAFF_DEATH role={} at={} dueTick={}",
                    role(villager), home, due);
        }
    }

    private static Block expectedDesk(String role) {
        return "station_chief".equals(role) ? TCBlocks.STATION_OFFICE_DESK.get() : TCBlocks.TICKET_BOOTH.get();
    }

    private static boolean stationIntact(ServerLevel level, Slot slot) {
        BlockPos p = slot.home;
        if (!level.hasChunkAt(p) || !level.hasChunkAt(p.above()) || !level.hasChunkAt(p.below())) return false;
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                || !level.getBlockState(p.below()).isSolidRender(level, p.below())) return false;
        // A deleted/converted station does not recreate villagers in empty terrain.
        boolean foundDesk = false;
        boolean foundStationRail = false;
        for (BlockPos scan : BlockPos.betweenClosed(p.offset(-7, -2, -7), p.offset(7, 2, 7))) {
            if (!level.hasChunkAt(scan)) continue;
            if (level.getBlockState(scan).is(expectedDesk(slot.role))) foundDesk = true;
            if (level.getBlockState(scan).is(TCBlocks.TRACK_STATION.get())) foundStationRail = true;
            if (foundDesk && foundStationRail) return true;
        }
        return false;
    }

    private static Villager existing(ServerLevel level, Slot slot) {
        return level.getEntitiesOfClass(Villager.class, new AABB(slot.home).inflate(16, 8, 16))
                .stream().filter(v -> isMarked(v)
                        && v.getPersistentData().contains(HOME, Tag.TAG_LONG)
                        && v.getPersistentData().getLong(HOME) == slot.home.asLong()
                        && slot.role.equals(role(v))).findFirst().orElse(null);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % CHECK_INTERVAL != 0 || level.players().isEmpty()) return;
        StaffData staff = data(level);
        int budget = 2;
        for (Slot slot : new ArrayList<>(staff.slots.values())) {
            if (budget == 0) break;
            if (slot.due <= 0 || level.getGameTime() < slot.due) continue;
            if (level.players().stream().noneMatch(p -> p.blockPosition().distSqr(slot.home) <= 96.0 * 96.0)) continue;
            if (!stationIntact(level, slot)) continue;
            // A living matching employee always wins; never spawn a duplicate.
            Villager alive = existing(level, slot);
            if (alive != null) {
                staff.observeReplacement(slot.home, slot.role, alive.getUUID());
                continue;
            }
            Villager replacement = EntityType.VILLAGER.create(level);
            if (replacement == null) continue;
            BlockPos p = slot.home;
            replacement.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180.0F, 0.0F);
            replacement.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null, null);
            replacement.setVillagerData(replacement.getVillagerData()
                    .setProfession(RailwayVillagers.forEgg(slot.role).get()).setLevel(1));
            replacement.refreshBrain(level);
            replacement.getPersistentData().putString(RailwayVillagerEgg.RAILWAY_ROLE_TAG, slot.role);
            replacement.getPersistentData().putBoolean(JIGSAW_MARKER, true);
            replacement.getPersistentData().putLong(HOME, p.asLong());
            replacement.setPersistenceRequired();
            if (!level.addFreshEntity(replacement)) continue;
            staff.observeReplacement(p, slot.role, replacement.getUUID());
            RailwayVillagerCare.bindWorkstation(level, replacement, slot.role);
            RailwayTrades.ensureNoviceOffers(replacement);
            budget--;
            Traincraft.LOGGER.info("[DEV56J12] STAFF_RESPAWN role={} home={} uuid={}",
                    slot.role, p, replacement.getUUID());
        }
    }

    private static final class Slot {
        final BlockPos home;
        final String role;
        final UUID uuid; // Present while alive; null only when death timer is armed.
        final long due;  // Nonzero only after a confirmed LivingDeathEvent.
        Slot(BlockPos home, String role, UUID uuid, long due) {
            this.home = home; this.role = role; this.uuid = uuid; this.due = due;
        }
    }

    private static final class StaffData extends SavedData {
        final Map<String, Slot> slots = new HashMap<>();
        private static String key(BlockPos p, String role) { return p.asLong() + ":" + role; }

        void observe(BlockPos home, String role, UUID uuid) {
            if (!roleIsManaged(role)) return;
            String key = key(home, role);
            Slot old = slots.get(key);
            if (old == null) {
                slots.put(key, new Slot(home, role, uuid, 0));
                setDirty();
            }
            // In particular, never clear a death timer in EntityJoinLevelEvent.
        }

        boolean markDeath(BlockPos home, String role, UUID uuid, long due) {
            if (!roleIsManaged(role)) return false;
            String key = key(home, role);
            Slot old = slots.get(key);
            if (old != null && (old.due > 0 || !uuid.equals(old.uuid))) return false;
            slots.put(key, new Slot(home, role, null, due));
            setDirty();
            return true;
        }

        void observeReplacement(BlockPos home, String role, UUID uuid) {
            slots.put(key(home, role), new Slot(home, role, uuid, 0));
            setDirty();
        }

        static StaffData load(CompoundTag tag) {
            StaffData saved = new StaffData();
            for (Tag element : tag.getList("Staff", Tag.TAG_COMPOUND)) {
                CompoundTag nbt = (CompoundTag) element;
                String role = nbt.getString("Role");
                if (!roleIsManaged(role) || !nbt.contains("Home", Tag.TAG_LONG)) continue;
                BlockPos home = BlockPos.of(nbt.getLong("Home"));
                UUID uuid = nbt.hasUUID("UUID") ? nbt.getUUID("UUID") : null;
                long due = nbt.getLong("Due");
                if ((uuid == null) == (due <= 0)) continue;
                saved.slots.put(key(home, role), new Slot(home, role, uuid, due));
            }
            return saved;
        }

        @Override public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Slot slot : slots.values()) {
                CompoundTag row = new CompoundTag();
                row.putLong("Home", slot.home.asLong());
                row.putString("Role", slot.role);
                if (slot.uuid != null) row.putUUID("UUID", slot.uuid);
                row.putLong("Due", slot.due);
                list.add(row);
            }
            tag.put("Staff", list);
            return tag;
        }
    }
}
