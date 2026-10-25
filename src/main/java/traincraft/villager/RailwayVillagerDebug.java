package traincraft.villager;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.registry.TCBlocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DEV50a3d: bounded, read-only live railway villager diagnostics. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RailwayVillagerDebug {
    private static final String PREFIX = "[TC-RAILWAY-VILLAGER] ";
    private static boolean trace = true;
    // Only interactions with six railway roles are recorded; no global villager spam.
    private static final Map<UUID, String> INTERACTIONS = new HashMap<>();
    private RailwayVillagerDebug() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // Adding this child under /tc merges with existing Brigadier children.
        dispatcher.register(Commands.literal("tc")
                .then(Commands.literal("villagerdebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> inspect(ctx.getSource()))
                        .then(Commands.literal("trace")
                                .then(Commands.literal("on").executes(ctx -> setTrace(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setTrace(ctx.getSource(), false))))));
        // Collision-free fallback if another /tc command tree gets overwritten.
        dispatcher.register(Commands.literal("tcvillagerdebug")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> inspect(ctx.getSource())));
    }

    private static int setTrace(CommandSourceStack source, boolean enabled) {
        trace = enabled;
        source.sendSuccess(() -> Component.literal("[TC villager] trace " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    public static void report(String message) {
        if (trace) Traincraft.LOGGER.info(PREFIX + message);
    }

    private static String roleOf(Villager villager) {
        String tag = villager.getPersistentData().contains(RailwayVillagerEgg.RAILWAY_ROLE_TAG, Tag.TAG_STRING)
                ? villager.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG) : "";
        if (RailwayVillagers.forEgg(tag) != null) return tag;
        String profession = villager.getVillagerData().getProfession().name();
        return RailwayVillagers.forEgg(profession) != null ? profession : "";
    }

    private static net.minecraft.world.level.block.Block workstation(String role) {
        return switch (role) {
            case "station_chief" -> TCBlocks.STATION_OFFICE_DESK.get();
            case "railway_engineer" -> TCBlocks.RAILWAY_TOOLBENCH.get();
            case "track_worker" -> TCBlocks.TRACK_MAINTENANCE_BENCH.get();
            case "train_conductor" -> TCBlocks.TICKET_BOOTH.get();
            case "fuel_depot_operator" -> TCBlocks.FUEL_DEPOT_COUNTER.get();
            case "freight_yard_clerk" -> TCBlocks.FREIGHT_OFFICE_DESK.get();
            default -> net.minecraft.world.level.block.Blocks.AIR;
        };
    }

    private static String details(ServerLevel level, Villager villager) {
        String role = roleOf(villager);
        if (role.isEmpty()) return "NON_RAILWAY";
        var profession = villager.getVillagerData().getProfession();
        var expected = RailwayVillagers.forEgg(role);
        var poi = RailwayVillagers.jobSite(role);
        String site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).map(BlockPos::toShortString).orElse("NONE");
        boolean listed = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolder(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                        new ResourceLocation(Traincraft.MOD_ID, role + "_poi")))
                .map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
        // No PoiManager.getCountInRange here: querying unloaded POI sections during
        // chunk/entity loading can indirectly request chunk work on the server thread.
        // The richer J14 /tcstaffdiag command remains available for POI ownership.
        // Verify POI recognition separately from registration: a placed workstation may
        // be present as a block but missing from PoiTypes' blockstate-to-POI mapping.
        int blocks = 0;
        int mapped = 0;
        int skippedCells = 0;
        var block = workstation(role);
        BlockPos center = villager.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            // getChunkNow returns null for chunks not FULLY loaded. Never use
            // level.getBlockState here: that can synchronously load a missing chunk.
            LevelChunk loaded = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            if (loaded == null) {
                skippedCells++;
                continue;
            }
            var state = loaded.getBlockState(p);
            if (state.is(block)) {
                blocks++;
                if (PoiTypes.forState(state)
                        .filter(h -> poi != null && h.value() == poi.get()).isPresent()) mapped++;
            }
        }
        return "uuid=" + villager.getUUID().toString().substring(0, 8)
                + " role=" + role + " profession=" + profession.name()
                + " correct=" + (expected != null && profession == expected.get())
                + " offers=" + villager.getOffers().size()
                + " jobSite=" + site
                + " POI-tag=" + listed + " POI-near24=SKIPPED_SAFE"
                + " blocks-near8=" + blocks + " mapped=" + mapped
                + " skippedUnloadedCells=" + skippedCells
                + " alive=" + villager.isAlive();
    }

    // Entity-only logging safe during EntityJoinLevelEvent and chunk promotion.
    // In particular: no block scans, POI queries, or chunk source reads here.
    private static String joinDetails(Villager villager) {
        String role = roleOf(villager);
        if (role.isEmpty()) return "NON_RAILWAY";
        String site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).map(BlockPos::toShortString).orElse("NONE");
        return "uuid=" + villager.getUUID().toString().substring(0, 8)
                + " role=" + role
                + " profession=" + villager.getVillagerData().getProfession().name()
                + " jobSite=" + site
                + " pos=" + villager.blockPosition().toShortString()
                + " diagnostic=ENTITY_ONLY_CHUNK_SAFE";
    }

    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var center = source.getPosition();
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(center, center).inflate(24.0));
        source.sendSuccess(() -> Component.literal(
                "[TC villagerdebug] scanning 24 blocks; logging=" + trace), false);
        int count = 0;
        for (Villager v : villagers) {
            String role = roleOf(v);
            if (role.isEmpty()) continue;
            count++;
            String detail = details(level, v);
            source.sendSuccess(() -> Component.literal(detail), false);
            report("COMMAND " + detail);
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("No railway villagers within 24 blocks."
                    + " Spawn the matching egg beside a placed workstation."), false);
        }
        return count;
    }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!roleOf(villager).isEmpty()) report("SPAWN_OR_LOAD " + joinDetails(villager));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)
                || event.getEntity().level().isClientSide()) return;
        if (roleOf(villager).isEmpty()) return;
        String line = "RIGHT_CLICK player=" + event.getEntity().getName().getString()
                + " hand=" + event.getHand() + " "
                + details((ServerLevel) villager.level(), villager);
        INTERACTIONS.put(event.getEntity().getUUID(), roleOf(villager));
        report(line);
    }

    @SubscribeEvent
    public static void onMerchantMenu(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu)) return;
        String role = INTERACTIONS.remove(event.getEntity().getUUID());
        if (role != null) report("MERCHANT_MENU_OPEN player="
                + event.getEntity().getName().getString() + " role=" + role);
    }
}
