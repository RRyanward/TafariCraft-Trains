package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * TC_DEV38_R1G1Z5I_CLEAN_UNATTENDED_FULL_FLEET_WHEEL_AUDIT_YARD
 *
 * DEBUG ONLY.
 *
 * Creates a reusable 12-bay inspection yard. The full discovered Traincraft
 * minecart/rolling-stock roster can be audited page-by-page without another
 * rebuild. Selection teleports the player next to one subject so the client
 * WheelPartProbe automatically inspects only that stock.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38FleetWheelAuditYardCommand {
    private static final String TAG = "tc_dev38_r1g1z5f_fleet_audit";
    private static final int EXPECTED_ROSTER = 111;
    private static final int PAGE_SIZE = 12;
    private static final int COLS = 4;
    private static final int X_SPACING = 18;
    private static final int Z_SPACING = 24;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private Dev38FleetWheelAuditYardCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(
                Commands.literal("tc_fleetaudit")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx.getSource())))
                        .then(Commands.literal("next")
                                .executes(ctx -> selectDelta(ctx.getSource(), 1)))
                        .then(Commands.literal("prev")
                                .executes(ctx -> selectDelta(ctx.getSource(), -1)))
                        .then(Commands.literal("slot")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, PAGE_SIZE))
                                        .executes(ctx -> selectSlot(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slot")))))
                        .then(Commands.literal("page")
                                .then(Commands.literal("next")
                                        .executes(ctx -> pageDelta(ctx.getSource(), 1)))
                                .then(Commands.literal("prev")
                                        .executes(ctx -> pageDelta(ctx.getSource(), -1))))
                        .then(Commands.literal("respawn")
                                .executes(ctx -> respawn(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC fleet audit] full-fleet wheel/rod inspection yard");
        msg(source, "/tc_fleetaudit start - discover full roster and build page 1");
        msg(source, "/tc_fleetaudit next | prev - select adjacent stock; changes page automatically");
        msg(source, "/tc_fleetaudit slot <1-12> - select a bay on current page");
        msg(source, "/tc_fleetaudit page next | prev - change 12-stock page");
        msg(source, "/tc_fleetaudit respawn - rebuild current page");
        msg(source, "/tc_fleetaudit status - show page, selected stock and IDs");
        msg(source, "/tc_fleetaudit clear - remove audit entities/labels; platform remains");
        return 1;
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        Session previous = SESSIONS.remove(player.getUUID());
        if (previous != null) {
            restoreEnvironment(level, previous);
        }

        clearTagged(level);

        List<EntityType<?>> roster = discoverStocks(level);
        if (roster.isEmpty()) {
            msg(source, "[TC fleet audit] SAFETY STOP: no Traincraft rolling stock discovered.");
            return 0;
        }

        // Put the yard 32 blocks south of the player. Same Y keeps access easy.
        BlockPos origin = player.blockPosition().offset(-27, 0, 32);
        Session session = new Session(
                origin,
                roster,
                0,
                0,
                level.getDayTime(),
                level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
                level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE),
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING),
                level.isRaining(),
                level.isThundering());
        SESSIONS.put(player.getUUID(), session);

        lockEnvironment(level, session);

        System.out.println("[TC fleet audit] discovered roster=" + roster.size()
                + " (expected reference=" + EXPECTED_ROSTER + ")");
        if (roster.size() != EXPECTED_ROSTER) {
            System.out.println("[TC fleet audit] NOTE: roster count differs from historical 111; IDs are still sorted and auditable.");
        }

        buildPlatform(level, origin);
        spawnPage(source, player, session);
        return teleportToSelected(source, player, session);
    }

    private static int selectDelta(CommandSourceStack source, int delta)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());

        if (s == null) {
            msg(source, "[TC fleet audit] no session. Run /tc_fleetaudit start");
            return 0;
        }

        int size = s.roster.size();
        // TC_DEV38_R1G1Z5H_AUTO_END_CLAMP
        // Unattended client probe repeatedly issues "tc_fleetaudit next".
        // Do not wrap from the final roster entry back to entry 1.
        if (delta > 0 && s.selectedGlobal >= size - 1) {
            restoreEnvironment(player.serverLevel(), s);
            System.out.println("[TC fleet audit] AUTO COMPLETE - last roster entry reached; environment restored.");
            return 1;
        }
        if (delta < 0 && s.selectedGlobal <= 0) {
            msg(source, "[TC fleet audit] first roster entry reached.");
            return 1;
        }

        s.selectedGlobal = Math.max(
                0,
                Math.min(size - 1, s.selectedGlobal + delta));

        int wantedPage = s.selectedGlobal / PAGE_SIZE;
        if (wantedPage != s.page) {
            s.page = wantedPage;
            spawnPage(source, player, s);
        }

        return teleportToSelected(source, player, s);
    }

    private static int selectSlot(CommandSourceStack source, int oneBasedSlot)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());

        if (s == null) {
            msg(source, "[TC fleet audit] no session. Run /tc_fleetaudit start");
            return 0;
        }

        int global = s.page * PAGE_SIZE + (oneBasedSlot - 1);
        if (global >= s.roster.size()) {
            msg(source, "[TC fleet audit] that slot is empty on the final page.");
            return 0;
        }

        s.selectedGlobal = global;
        return teleportToSelected(source, player, s);
    }

    private static int pageDelta(CommandSourceStack source, int delta)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());

        if (s == null) {
            msg(source, "[TC fleet audit] no session. Run /tc_fleetaudit start");
            return 0;
        }

        int pages = pageCount(s.roster.size());
        s.page = Math.floorMod(s.page + delta, pages);
        s.selectedGlobal = s.page * PAGE_SIZE;

        spawnPage(source, player, s);
        return teleportToSelected(source, player, s);
    }

    private static int respawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());

        if (s == null) {
            msg(source, "[TC fleet audit] no session. Run /tc_fleetaudit start");
            return 0;
        }

        spawnPage(source, player, s);
        return teleportToSelected(source, player, s);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());

        if (s == null) {
            msg(source, "[TC fleet audit] no session.");
            return 0;
        }

        int pages = pageCount(s.roster.size());
        msg(source, "[TC fleet audit] roster=" + s.roster.size()
                + " page=" + (s.page + 1) + "/" + pages
                + " selected=" + (s.selectedGlobal + 1) + "/" + s.roster.size());

        int first = s.page * PAGE_SIZE;
        int last = Math.min(first + PAGE_SIZE, s.roster.size());

        for (int i = first; i < last; i++) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(s.roster.get(i));
            int slot = i - first + 1;
            String marker = i == s.selectedGlobal ? "  < SELECTED" : "";
            msg(source, "  [" + slot + "] "
                    + (id == null ? "<unknown>" : id.getPath())
                    + marker);
        }

        return 1;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            restoreEnvironment(player.serverLevel(), s);
        }
        clearTagged(player.serverLevel());
        msg(source, "[TC fleet audit] audit entities cleared; world time/weather/mob rules restored. Platform/rails left in place.");
        return 1;
    }

    private static void buildPlatform(ServerLevel level, BlockPos origin) {
        // 4 x 3 bays. Each bay gets an 11-long north/south vanilla rail,
        // a compact smooth-stone pad, and enough empty spacing for long stock.
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            BlockPos center = bayCenter(origin, slot);

            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos floor = center.offset(dx, -1, dz);
                    level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }

            for (int dz = -5; dz <= 5; dz++) {
                BlockPos rail = center.offset(0, 0, dz);
                level.setBlockAndUpdate(rail, Blocks.RAIL.defaultBlockState());
            }

            // Invisible camera perch. Player stands about five blocks above
            // rail level and six blocks east of the selected stock.
            BlockPos cameraPerch = center.offset(6, 4, 0);
            level.setBlockAndUpdate(
                    cameraPerch,
                    Blocks.BARRIER.defaultBlockState());
        }
    }

    private static void spawnPage(
            CommandSourceStack source,
            ServerPlayer player,
            Session s) {

        ServerLevel level = player.serverLevel();
        clearTagged(level);

        int first = s.page * PAGE_SIZE;
        int last = Math.min(first + PAGE_SIZE, s.roster.size());
        int spawned = 0;

        for (int i = first; i < last; i++) {
            int slot = i - first;
            BlockPos center = bayCenter(s.origin, slot);
            EntityType<?> type = s.roster.get(i);
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);

            Entity raw = null;
            try {
                raw = type.create(level);
                if (!(raw instanceof AbstractMinecart cart)) {
                    if (raw != null) raw.discard();
                    continue;
                }

                cart.setPos(center.getX() + 0.5D, center.getY() + 0.15D, center.getZ() + 0.5D);
                cart.setDeltaMovement(Vec3.ZERO);
                cart.setYRot(0.0F);
                cart.addTag(TAG);
                level.addFreshEntity(cart);
                spawned++;

                ArmorStand label = new ArmorStand(
                        level,
                        center.getX() + 0.5D,
                        center.getY() + 2.8D,
                        center.getZ() + 0.5D);

                label.setInvisible(true);
                label.setNoGravity(true);
                label.setInvulnerable(true);
                label.setSilent(true);
                label.setCustomNameVisible(true);
                label.setCustomName(Component.literal(
                        "[" + (slot + 1) + "] "
                        + (id == null ? "<unknown>" : id.getPath())));
                label.addTag(TAG);
                level.addFreshEntity(label);

            } catch (Throwable ex) {
                if (raw != null && !raw.isRemoved()) raw.discard();
                msg(source, "[TC fleet audit] spawn failed: "
                        + String.valueOf(id) + " -> "
                        + ex.getClass().getSimpleName());
            }
        }

        int pages = pageCount(s.roster.size());
        System.out.println("[TC fleet audit] page " + (s.page + 1) + "/" + pages
                + " spawned=" + spawned + "/" + (last - first));
    }

    private static int teleportToSelected(
            CommandSourceStack source,
            ServerPlayer player,
            Session s) {

        int pageFirst = s.page * PAGE_SIZE;
        int slot = s.selectedGlobal - pageFirst;

        if (slot < 0 || slot >= PAGE_SIZE) {
            msg(source, "[TC fleet audit] selected stock is not on current page.");
            return 0;
        }

        BlockPos center = bayCenter(s.origin, slot);
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(
                s.roster.get(s.selectedGlobal));

        // Keep the inspection yard visually clean. Mob spawning is disabled
        // while the audit is active; this removes hostile mobs that already
        // existed nearby (slimes included) before each selected stock.
        clearHostilesNearYard(player.serverLevel(), s);

        // Elevated side camera: invisible barrier perch at x+6/y+4, then
        // teleport just above it and look down at the selected stock.
        // The client probe radius is 10 blocks; neighboring 18-block bays stay
        // outside the activation radius.
        player.teleportTo(
                player.serverLevel(),
                center.getX() + 6.5D,
                center.getY() + 5.05D,
                center.getZ() + 0.5D,
                90.0F,
                28.0F);

        System.out.println("[TC fleet audit] selected "
                + (s.selectedGlobal + 1) + "/" + s.roster.size()
                + " : " + (id == null ? "<unknown>" : id.getPath()));

        return 1;
    }

    private static BlockPos bayCenter(BlockPos origin, int slot) {
        int col = slot % COLS;
        int row = slot / COLS;
        return origin.offset(col * X_SPACING, 0, row * Z_SPACING);
    }

    private static int pageCount(int rosterSize) {
        return Math.max(1, (rosterSize + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static List<EntityType<?>> discoverStocks(ServerLevel level) {
        List<EntityType<?>> out = new ArrayList<>();
        List<EntityType<?>> all = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues());

        all.sort(Comparator.comparing(type ->
                String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type))));

        for (EntityType<?> type : all) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !"traincraft".equals(key.getNamespace())) continue;

            Entity raw = null;
            try {
                raw = type.create(level);
                if (raw instanceof AbstractMinecart) {
                    out.add(type);
                }
            } catch (Throwable ignored) {
                // A failed constructor stays out of the roster and is visible
                // as a roster-count difference from the historical 111.
            } finally {
                if (raw != null) raw.discard();
            }
        }

        return out;
    }

    private static void lockEnvironment(ServerLevel level, Session s) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT)
                .set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE)
                .set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                .set(false, level.getServer());

        // Minecraft noon. With daylight/weather cycles disabled this stays
        // fixed for the entire unattended run.
        level.setDayTime(6000L);
        level.setWeatherParameters(6000, 0, false, false);

        System.out.println("[TC fleet audit] environment locked: noon, clear weather, mob spawning off.");
    }

    private static void restoreEnvironment(ServerLevel level, Session s) {
        if (s == null || s.environmentRestored) {
            return;
        }

        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT)
                .set(s.oldDaylightCycle, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE)
                .set(s.oldWeatherCycle, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                .set(s.oldMobSpawning, level.getServer());

        level.setDayTime(s.oldDayTime);
        level.setWeatherParameters(
                s.oldRaining ? 0 : 6000,
                s.oldRaining ? 6000 : 0,
                s.oldRaining,
                s.oldThundering);

        s.environmentRestored = true;
        System.out.println("[TC fleet audit] previous world time/weather/mob rules restored.");
    }

    private static void clearHostilesNearYard(ServerLevel level, Session s) {
        double minX = s.origin.getX() - 12.0D;
        double maxX = s.origin.getX() + (COLS - 1) * X_SPACING + 12.0D;
        double minY = s.origin.getY() - 8.0D;
        double maxY = s.origin.getY() + 16.0D;
        double minZ = s.origin.getZ() - 12.0D;
        double maxZ = s.origin.getZ() + 2 * Z_SPACING + 12.0D;

        List<Entity> remove = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Enemy)) {
                continue;
            }

            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            if (x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ) {
                remove.add(entity);
            }
        }

        for (Entity entity : remove) {
            entity.discard();
        }
    }

    private static void clearTagged(ServerLevel level) {
        List<Entity> remove = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(TAG)) {
                remove.add(entity);
            }
        }

        for (Entity entity : remove) {
            entity.discard();
        }
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static final class Session {
        final BlockPos origin;
        final List<EntityType<?>> roster;
        final long oldDayTime;
        final boolean oldDaylightCycle;
        final boolean oldWeatherCycle;
        final boolean oldMobSpawning;
        final boolean oldRaining;
        final boolean oldThundering;

        int page;
        int selectedGlobal;
        boolean environmentRestored;

        Session(
                BlockPos origin,
                List<EntityType<?>> roster,
                int page,
                int selectedGlobal,
                long oldDayTime,
                boolean oldDaylightCycle,
                boolean oldWeatherCycle,
                boolean oldMobSpawning,
                boolean oldRaining,
                boolean oldThundering) {
            this.origin = origin;
            this.roster = roster;
            this.page = page;
            this.selectedGlobal = selectedGlobal;
            this.oldDayTime = oldDayTime;
            this.oldDaylightCycle = oldDaylightCycle;
            this.oldWeatherCycle = oldWeatherCycle;
            this.oldMobSpawning = oldMobSpawning;
            this.oldRaining = oldRaining;
            this.oldThundering = oldThundering;
        }
    }
}
