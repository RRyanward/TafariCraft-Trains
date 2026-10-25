package traincraft.villager;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.registry.TCBlocks;

/**
 * DEV56J14 - read-only railway staff diagnostics. DO NOT bind a workstation, refresh
 * a brain, change professions, spawn staff, edit terrain, or modify saved data here.
 * J10 station structures, J11 POI repair and J12 death/respawn remain untouched.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev56J14RailwayStaffProbe {
    private static final String PREFIX = "[DEV56J14] ";
    private static final String J12_HOME = "TC_DEV56_JIGSAW_HOME";
    private static final Map<UUID, String> last = new HashMap<>();
    private static final Map<UUID, String> preCare = new HashMap<>();
    private static final Map<UUID, Long> heartbeat = new HashMap<>();
    private static boolean watch = true;
    private Dev56J14RailwayStaffProbe() {}

    private static String role(Villager v) {
        String tag = v.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
        return RailwayVillagers.forEgg(tag) == null ? "" : tag;
    }

    private static Block desk(String role) {
        return switch (role) {
            case "station_chief" -> TCBlocks.STATION_OFFICE_DESK.get();
            case "train_conductor" -> TCBlocks.TICKET_BOOTH.get();
            case "railway_engineer" -> TCBlocks.RAILWAY_TOOLBENCH.get();
            case "track_worker" -> TCBlocks.TRACK_MAINTENANCE_BENCH.get();
            case "fuel_depot_operator" -> TCBlocks.FUEL_DEPOT_COUNTER.get();
            case "freight_yard_clerk" -> TCBlocks.FREIGHT_OFFICE_DESK.get();
            default -> null;
        };
    }

    private static boolean relevant(ServerLevel level, Villager v) {
        if (role(v).isEmpty()) return false;
        if (level.players().isEmpty()) return false;
        return level.players().stream().anyMatch(p -> p.blockPosition().distSqr(v.blockPosition()) <= 96.0 * 96.0);
    }

    private static String memory(Villager v) {
        return v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(pos -> pos.dimension().location() + "@" + pos.pos().toShortString())
                .orElse("NONE");
    }

    private static String core(Villager v) {
        return "prof=" + v.getVillagerData().getProfession().name()
                + " job=" + memory(v)
                + " pos=" + v.blockPosition().toShortString()
                + " j12=" + v.getPersistentData().getBoolean(Dev56J12StationStaffLifecycle.JIGSAW_MARKER);
    }

    private static String stateKey(Villager v) {
        return "prof=" + v.getVillagerData().getProfession().name() + " job=" + memory(v)
                + " j12=" + v.getPersistentData().getBoolean(Dev56J12StationStaffLifecycle.JIGSAW_MARKER);
    }

    private static String shortId(Villager v) { return v.getUUID().toString().substring(0, 8); }
    private static void log(String text) { Traincraft.LOGGER.info(PREFIX + text); }

    private static void snapshot(ServerLevel level, Villager v, String reason) {
        String role = role(v);
        if (role.isEmpty()) return;
        RegistryObject<PoiType> poi = RailwayVillagers.jobSite(role);
        RegistryObject<net.minecraft.world.entity.npc.VillagerProfession> profession = RailwayVillagers.forEgg(role);
        Block expected = desk(role);
        BlockPos center = v.blockPosition();
        String home = v.getPersistentData().contains(J12_HOME, Tag.TAG_LONG)
                ? BlockPos.of(v.getPersistentData().getLong(J12_HOME)).toShortString() : "NONE";
        String site = memory(v);
        String siteBlock = "NONE";
        String siteMatch = "NONE";
        var job = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (job.isPresent()) {
            GlobalPos position = job.get();
            if (!position.dimension().equals(level.dimension())) {
                siteBlock = "OTHER_DIMENSION";
            } else if (!level.hasChunkAt(position.pos())) {
                siteBlock = "UNLOADED";
            } else {
                siteBlock = level.getBlockState(position.pos()).getBlock().toString();
                siteMatch = expected != null && level.getBlockState(position.pos()).is(expected) ? "yes" : "no";
            }
        }
        long poiAny = -1L;
        long poiFree = -1L;
        long poiTaken = -1L;
        PoiManager manager = level.getPoiManager();
        if (poi != null) {
            poiAny = manager.getCountInRange(holder -> holder.value() == poi.get(), center, 24, PoiManager.Occupancy.ANY);
            poiFree = manager.getCountInRange(holder -> holder.value() == poi.get(), center, 24, PoiManager.Occupancy.HAS_SPACE);
            poiTaken = manager.getCountInRange(holder -> holder.value() == poi.get(), center, 24, PoiManager.Occupancy.IS_OCCUPIED);
        }
        int unloaded = 0;
        int deskCount = 0;
        int mapped = 0;
        int registered = 0;
        List<String> candidates = new ArrayList<>();
        if (expected != null && poi != null) {
            for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
                if (!level.hasChunkAt(candidate)) { unloaded++; continue; }
                if (!level.getBlockState(candidate).is(expected)) continue;
                deskCount++;
                boolean stateMapped = PoiTypes.forState(level.getBlockState(candidate))
                        .filter(holder -> holder.value() == poi.get()).isPresent();
                boolean known = manager.exists(candidate, holder -> holder.value() == poi.get());
                if (stateMapped) mapped++;
                if (known) registered++;
                if (candidates.size() < 8) candidates.add(candidate.toShortString()
                        + ":map=" + stateMapped + ":poi=" + known);
            }
        }
        log("SNAP reason=" + reason + " tick=" + level.getGameTime() + " uuid=" + shortId(v)
                + " role=" + role + " " + core(v) + " home=" + home
                + " expectedProf=" + (profession != null && v.getVillagerData().getProfession() == profession.get())
                + " siteBlock=" + siteBlock + " siteMatchesRole=" + siteMatch
                + " poiAny=" + poiAny + " poiFree=" + poiFree + " poiOccupied=" + poiTaken
                + " deskNear8=" + deskCount + " mapped=" + mapped + " registered=" + registered
                + " unloadedCells=" + unloaded + " nearDesks=" + String.join(";", candidates));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeCare(LivingEvent.LivingTickEvent event) {
        if (!watch || !(event.getEntity() instanceof Villager v)
                || !(v.level() instanceof ServerLevel level)
                || v.tickCount % 100 != 0 || !relevant(level, v)) return;
        preCare.put(v.getUUID(), stateKey(v));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterCare(LivingEvent.LivingTickEvent event) {
        if (!watch || !(event.getEntity() instanceof Villager v)
                || !(v.level() instanceof ServerLevel level)
                || v.tickCount % 20 != 0 || !relevant(level, v)) return;
        UUID id = v.getUUID();
        String now = stateKey(v);
        String before = preCare.remove(id);
        if (before != null && !before.equals(now)) {
            log("SAME_TICK_CHANGE uuid=" + shortId(v) + " role=" + role(v)
                    + " before={" + before + "} after={" + now + "}");
        }
        String previous = last.put(id, now);
        long tick = level.getGameTime();
        boolean periodic = tick - heartbeat.getOrDefault(id, -1000000L) >= 600;
        if (previous == null || !previous.equals(now) || periodic || before != null && !before.equals(now)) {
            heartbeat.put(id, tick);
            snapshot(level, v, previous == null ? "FIRST" : previous.equals(now) ? "HEARTBEAT_OR_CARE" : "STATE_CHANGE");
        }
        // Bound retained diagnostic state even in a large disposable testing world.
        if (last.size() > 512) { last.clear(); heartbeat.clear(); preCare.clear(); }
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager v)
                || !(event.getLevel() instanceof ServerLevel level) || role(v).isEmpty()) return;
        log("JOIN_PRE_SETUP tick=" + level.getGameTime() + " uuid=" + shortId(v)
                + " role=" + role(v) + " " + core(v)
                + " (J12 bind/trades may execute after this event)");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager v)
                || !(v.level() instanceof ServerLevel level) || role(v).isEmpty()) return;
        log("DEATH_PRE_LIFECYCLE tick=" + level.getGameTime() + " uuid=" + shortId(v)
                + " role=" + role(v) + " cause=" + event.getSource().getMsgId()
                + " " + core(v) + " (J12 STAFF_DEATH is separate)");
        last.remove(v.getUUID()); heartbeat.remove(v.getUUID()); preCare.remove(v.getUUID());
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tcstaffdiag")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> inspect(ctx.getSource()))
                .then(Commands.literal("watch")
                        .then(Commands.literal("on").executes(ctx -> setWatch(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setWatch(ctx.getSource(), false)))));
    }

    private static int setWatch(CommandSourceStack source, boolean enabled) {
        watch = enabled;
        if (enabled) { last.clear(); heartbeat.clear(); preCare.clear(); }
        source.sendSuccess(() -> Component.literal("[DEV56J14] automatic read-only staff diagnostics "
                + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var center = source.getPosition();
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(center, center).inflate(32)).stream()
                .filter(v -> !role(v).isEmpty())
                .sorted(Comparator.comparingDouble(v -> v.distanceToSqr(center)))
                .limit(20).toList();
        source.sendSuccess(() -> Component.literal("[DEV56J14] scanned 32 blocks, found "
                + villagers.size() + " railway villagers; details in latest.log; watch=" + watch), false);
        for (Villager v : villagers) {
            snapshot(level, v, "COMMAND");
            String msg = "uuid=" + shortId(v) + " role=" + role(v)
                    + " prof=" + v.getVillagerData().getProfession().name() + " job=" + memory(v)
                    + " J12=" + v.getPersistentData().getBoolean(Dev56J12StationStaffLifecycle.JIGSAW_MARKER);
            source.sendSuccess(() -> Component.literal(msg), false);
        }
        return villagers.size();
    }
}
