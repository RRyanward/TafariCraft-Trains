package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import traincraft.Traincraft;

/**
 * TC_DEV38_R1G1Z7B_REMAINING_FLEET_ONE_PASS
 * TC_DEV38_R1G1Z7C_CONSERVATIVE_STATIC_SAFETY
 *
 * One command, one in-game pass:
 *   /tc_runninggear start
 *
 * Accepted/locked and intentionally wheel-less stock is removed from the
 * validation roster. One remaining vehicle is shown at a time and is driven:
 *
 *   REST -> FORWARD -> STOP -> REVERSE -> STOP -> NEXT
 *
 * Report only failures:
 *   backwards / no wheels / wrong part / rods / pistons.
 *
 * DEBUG/VALIDATION ONLY. Remove before public release.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38RemainingRunningGearRunCommand {
    private static final String TAG = "tc_dev38_r1g1z7_runninggear";
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    // TC_DEV38_R1G1Z7L1_TEARDOWN_YARD
    // Fixed 4x2 station yard. Each remaining locomotive is held static in its
    // own position while the already-existing visual isolation diagnostics run.
    private static final String YARD_TAG = "tc_dev38_r1g1z7l1_teardown";
    private static final int YARD_SPACING = 18;
    private static final Map<UUID, TeardownYard> YARDS = new ConcurrentHashMap<>();
    // TC_DEV38_R1G1Z7L2_FULL_SWEEP
    private static final String SWEEP_TAG = "tc_dev38_r1g1z7l2_full_sweep";
    private static final Map<UUID, SweepSession> SWEEPS = new ConcurrentHashMap<>();
    private static final List<String> SWEEP_ORDER = List.of(
            "locomotive_diesel_chme3",
            "locomotive_diesel_gp7",
            "locomotive_diesel_v60",
            "locomotive_steam_4_4_0",
            "locomotive_steam_adler",
            "locomotive_steam_br01",
            "locomotive_steam_forney",
            "locomotive_steam_ussr");

    private static final int REST_TICKS = 20;
    private static final int FORWARD_TICKS = 55;
    private static final int STOP1_TICKS = 15;
    private static final int REVERSE_TICKS = 55;
    private static final int STOP2_TICKS = 15;
    private static final int TOTAL_TICKS =
            REST_TICKS + FORWARD_TICKS + STOP1_TICKS + REVERSE_TICKS + STOP2_TICKS;

    private static final double RUN_SPEED = 0.12D;

    // TC_DEV38_R1G1Z7D1_STATUS_LISTS
    // Wheel-only PASS means it no longer needs the wheel validation loop.
    private static final Set<String> WHEEL_PASSED = Set.of(
            "locomotive_diesel_shunter",
            "locomotive_electric_minetrain");

    // Wheel pass is sufficiently complete; keep these for a later exact
    // authored rod/piston/crank/crosshead phase instead of wheel retesting.
    private static final Set<String> ROD_PISTON_FOLLOWUP = Set.of(
            "locomotive_steam_vb_shay_2");

    // TC_DEV38_R1G1Z7N_AUTHORITATIVE_2_VALIDATION
    // Adler wheels runtime-PASS on r1g1z7m2 -> rods/pistons follow-up.
    // Remaining normal wheel validation:
    private static final Set<String> DEEP_DEBUG_TARGETS = Set.of(
            "locomotive_steam_4_4_0",
            "locomotive_steam_forney");

    private static final Set<String> OMIT = Set.of(
            // Runtime-locked wheel implementations.
            "locomotive_steam_small",
            "freight_cart",
            "passenger_coach_blue",
            "caboose",
            "locomotive_diesel_sd40",
            "locomotive_diesel_sd70",

            // Intentional glide / no-conventional-wheel exclusions.
            "gs4_passenger_car",
            "gs4_tail_car",
            "gs4_baggage_car",
            "gs4_observatory_car",
            "locomotive_electric_high_speed_zeroed",
            "locomotive_electric_br_e69",
            "locomotive_electric_vl10",
            "tracks_builder",
            "locomotive_steam_snow_plow",
            "mob_transport_car",

            // TC_DEV38_R1G1Z7D1_WHEEL_PASSED
            // Runtime-confirmed wheel-only stock: do not show again.
            "locomotive_diesel_shunter",
            "locomotive_electric_minetrain",

            // Wheel work is complete enough to leave the wheel pass.
            // This steam stock moves to the rod/piston follow-up list below.
            "locomotive_steam_vb_shay_2");

    private Dev38RemainingRunningGearRunCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("tc_runninggear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("sweep")
                                .executes(ctx -> sweep(ctx.getSource())))
                        .then(Commands.literal("sweepclear")
                                .executes(ctx -> sweepClear(ctx.getSource())))
                        // Internal client-driven advance. User normally never types this.
                        .then(Commands.literal("sweep_next")
                                .executes(ctx -> sweepNext(ctx.getSource())))
                        .then(Commands.literal("yard")
                                .executes(ctx -> yard(ctx.getSource())))
                        .then(Commands.literal("yardclear")
                                .executes(ctx -> yardClear(ctx.getSource())))
                        .then(Commands.literal("tp")
                                .then(Commands.literal("chme3")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_diesel_chme3")))
                                .then(Commands.literal("gp7")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_diesel_gp7")))
                                .then(Commands.literal("v60")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_diesel_v60")))
                                .then(Commands.literal("440")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_steam_4_4_0")))
                                .then(Commands.literal("adler")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_steam_adler")))
                                .then(Commands.literal("br01")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_steam_br01")))
                                .then(Commands.literal("forney")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_steam_forney")))
                                .then(Commands.literal("ussr")
                                        .executes(ctx -> teleportYard(
                                                ctx.getSource(),
                                                "locomotive_steam_ussr")))));
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // TC_DEV38_R1G1Z7L2_FULL_SWEEP
        for (SweepSession sweepSession : new ArrayList<>(SWEEPS.values())) {
            ServerPlayer sweepPlayer =
                    server.getPlayerList().getPlayer(sweepSession.playerId);
            if (sweepPlayer == null) continue;

            Entity sweepEntity =
                    sweepPlayer.serverLevel().getEntity(sweepSession.subjectId);
            if (sweepEntity == null || sweepEntity.isRemoved()) continue;

            sweepEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
            sweepEntity.setNoGravity(true);
            sweepEntity.moveTo(
                    sweepSession.x,
                    sweepSession.y,
                    sweepSession.z,
                    sweepSession.yaw,
                    0.0F);
        }
        // TC_DEV38_R1G1Z7L1_TEARDOWN_YARD
        // Freeze only teardown-tagged debug stock. This does not affect normal
        // trains, F9/path, physics or the automatic /tc_runninggear start pass.
        for (TeardownYard yard : new ArrayList<>(YARDS.values())) {
            ServerPlayer yardPlayer = server.getPlayerList().getPlayer(yard.playerId);
            if (yardPlayer == null) continue;
            ServerLevel yardLevel = yardPlayer.serverLevel();
            for (Entity entity : yardLevel.getAllEntities()) {
                if (!entity.getTags().contains(YARD_TAG)) continue;
                entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
                entity.setNoGravity(true);
            }
        }

        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) continue;

            ServerLevel level = player.serverLevel();
            AbstractMinecart cart = findSubject(level, session.subjectId);

            if (cart == null || cart.isRemoved()) {
                if (!spawnCurrent(player, session)) {
                    finish(player, session, "unable to spawn subject");
                }
                continue;
            }

            // TC_DEV38_R1G1Z7W9_TRACK_MOVING_SUBJECT

            // TC_DEV38_R1G1Z7Y6_FREE_CAMERA_TICK - no forced view tracking


            int t = session.stageTick++;

            if (t < REST_TICKS) {
                cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
                announce(player, session, "REST");
                continue;
            }

            if (t < REST_TICKS + FORWARD_TICKS) {
                cart.setDeltaMovement(0.0D, 0.0D, -RUN_SPEED);
                announce(player, session, "FORWARD");
                continue;
            }

            if (t < REST_TICKS + FORWARD_TICKS + STOP1_TICKS) {
                cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
                announce(player, session, "STOP");
                continue;
            }

            if (t < REST_TICKS + FORWARD_TICKS + STOP1_TICKS + REVERSE_TICKS) {
                cart.setDeltaMovement(0.0D, 0.0D, RUN_SPEED);
                announce(player, session, "REVERSE");
                continue;
            }

            if (t < TOTAL_TICKS) {
                cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
                announce(player, session, "STOP");
                continue;
            }

            cart.discard();
            session.subjectId = -1;
            session.index++;
            session.stageTick = 0;
            session.lastStage = "";

            if (session.index >= session.roster.size()) {
                finish(player, session, "COMPLETE");
            } else if (!spawnCurrent(player, session)) {
                finish(player, session, "unable to spawn next subject");
            }
        }
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC runninggear] /tc_runninggear yard");
        msg(source, "[TC runninggear] spawns all 8 unresolved trains at separate teardown stations");
        msg(source, "[TC runninggear] tp: chme3 | gp7 | v60 | 440 | adler | br01 | forney | ussr");
        msg(source, "[TC runninggear] example: /tc_runninggear tp gp7");
        msg(source, "[TC runninggear] /tc_runninggear yardclear");
        msg(source, "[TC runninggear] /tc_runninggear start still runs the 8-train motion pass");
        return 1;
    }
    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        stopExisting(player, false);
        clearYard(player, false);

        BlockPos original = player.blockPosition();
        BlockPos starterSouth = original.offset(48, 20, 48);

        try {
            source.getServer().getCommands().getDispatcher()
                    .execute("tc_t7_full_test_track", source);
        } catch (Exception ex) {
            msg(source, "[TC runninggear] T7 builder failed: "
                    + ex.getClass().getSimpleName()
                    + " "
                    + String.valueOf(ex.getMessage()));
            return 0;
        }

        List<EntityType<?>> roster = discover(level);
        if (roster.isEmpty()) {
            msg(source, "[TC runninggear] no remaining rolling stock discovered.");
            return 0;
        }

        Session session = new Session(
                player.getUUID(),
                original,
                starterSouth,
                roster);
        SESSIONS.put(player.getUUID(), session);

        // Camera position: beside the middle of the 24-rail starter, slightly
        // elevated. Does not touch train/body/path logic.
        BlockPos camera = starterSouth.offset(5, 3, -9);
        player.teleportTo(
                camera.getX() + 0.5D,
                camera.getY() + 0.2D,
                camera.getZ() + 0.5D);

        msg(source, "[TC runninggear] AUTHORITATIVE wheel FAIL roster=" + roster.size());
        msg(source, "[TC runninggear] tenders + locked/no-wheel/pass stock removed.");
        msg(source, "[TC runninggear] wheel-pass removed=" + WHEEL_PASSED.size());
        msg(source, "[TC runninggear] rod/piston follow-up=" + ROD_PISTON_FOLLOWUP.size());
        msg(source, "[TC runninggear] guided camera: REST/FORWARD/STOP/REVERSE/STOP/NEXT.");

        if (!spawnCurrent(player, session)) {
            finish(player, session, "unable to spawn first subject");
            return 0;
        }

        return roster.size();
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return stopExisting(player, true);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            msg(source, "[TC runninggear] no active pass.");
            return 0;
        }

        String id = currentId(session);
        msg(source, "[TC runninggear] "
                + (session.index + 1)
                + "/"
                + session.roster.size()
                + " "
                + id
                + " tick="
                + session.stageTick);
        return 1;
    }

    // TC_DEV38_R1G1Z7L2_FULL_SWEEP
    private static int sweep(CommandSourceStack source)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        stopExisting(player, false);
        clearYard(player, false);
        clearSweep(player);

        List<EntityType<?>> roster = discover(level);
        roster.sort(Comparator.comparingInt(type -> {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) return Integer.MAX_VALUE;
            int index = SWEEP_ORDER.indexOf(key.getPath());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));

        if (roster.size() != 8) {
            msg(source, "[TC sweep] SAFETY STOP: expected 8 unresolved trains, found "
                    + roster.size());
            return 0;
        }

        var look = player.getLookAngle();
        double hx = look.x;
        double hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 0.0001D) {
            hx = 0.0D;
            hz = 1.0D;
            len = 1.0D;
        }
        hx /= len;
        hz /= len;

        // Six blocks directly in front, broadside to the player's view.
        double x = player.getX() + hx * 6.0D;
        double y = player.getY() - 0.35D;
        double z = player.getZ() + hz * 6.0D;
        float yaw = player.getYRot() + 90.0F;

        SweepSession sweepSession = new SweepSession(
                player.getUUID(),
                roster,
                x,
                y,
                z,
                yaw);

        SWEEPS.put(player.getUUID(), sweepSession);

        if (!spawnSweepCurrent(player, sweepSession)) {
            SWEEPS.remove(player.getUUID());
            msg(source, "[TC sweep] unable to spawn first locomotive.");
            return 0;
        }

        msg(source, "[TC sweep] STARTED - 8 locomotives / every authored part or mesh chunk.");
        msg(source, "[TC sweep] Keep recording. No more commands are needed.");
        msg(source, "[TC sweep] Stop only when action bar says TC AUTO COMPLETE.");
        return 1;
    }

    private static int sweepNext(CommandSourceStack source)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        SweepSession sweepSession = SWEEPS.get(player.getUUID());

        if (sweepSession == null) {
            return 0;
        }

        // Final train remains visible. The client notices no entity change and
        // displays AUTO COMPLETE after its grace period.
        if (sweepSession.index + 1 >= sweepSession.roster.size()) {
            msg(source, "[TC sweep] final locomotive complete.");
            return 0;
        }

        clearSweepTagged(player.serverLevel());
        sweepSession.index++;
        sweepSession.subjectId = -1;

        if (!spawnSweepCurrent(player, sweepSession)) {
            msg(source, "[TC sweep] failed to spawn next locomotive.");
            return 0;
        }

        return 1;
    }

    private static int sweepClear(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return clearSweep(player);
    }

    private static int clearSweep(ServerPlayer player) {
        SweepSession removed = SWEEPS.remove(player.getUUID());
        clearSweepTagged(player.serverLevel());
        return removed == null ? 0 : 1;
    }

    private static void clearSweepTagged(ServerLevel level) {
        List<Entity> remove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(SWEEP_TAG)) {
                remove.add(entity);
            }
        }
        for (Entity entity : remove) {
            entity.discard();
        }
    }

    private static boolean spawnSweepCurrent(
            ServerPlayer player,
            SweepSession sweepSession) {

        if (sweepSession.index < 0
                || sweepSession.index >= sweepSession.roster.size()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        EntityType<?> type = sweepSession.roster.get(sweepSession.index);
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null) return false;

        Entity raw;
        try {
            raw = type.create(level);
        } catch (Throwable t) {
            raw = null;
        }

        if (!(raw instanceof AbstractMinecart cart)) {
            if (raw != null) raw.discard();
            return false;
        }

        cart.moveTo(
                sweepSession.x,
                sweepSession.y,
                sweepSession.z,
                sweepSession.yaw,
                0.0F);
        cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
        cart.setNoGravity(true);
        cart.setInvulnerable(true);
        cart.addTag(SWEEP_TAG);
        cart.setCustomName(Component.literal(
                "[" + (sweepSession.index + 1)
                        + "/"
                        + sweepSession.roster.size()
                        + "] "
                        + key.getPath()));
        cart.setCustomNameVisible(true);
        cart.setGlowingTag(false);

        if (!level.addFreshEntity(cart)) {
            cart.discard();
            return false;
        }

        sweepSession.subjectId = cart.getId();

        player.displayClientMessage(
                Component.literal(
                        "[TC sweep "
                                + (sweepSession.index + 1)
                                + "/"
                                + sweepSession.roster.size()
                                + "] "
                                + key.getPath()),
                false);

        return true;
    }
    // TC_DEV38_R1G1Z7L1_TEARDOWN_YARD
    private static int yard(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        stopExisting(player, false);
        clearYard(player, false);

        List<EntityType<?>> roster = discover(level);
        if (roster.isEmpty()) {
            msg(source, "[TC teardown] no unresolved rolling stock discovered.");
            return 0;
        }

        BlockPos original = player.blockPosition();
        // Elevated enough to avoid most terrain while staying close enough for
        // easy visual inspection. Debug entities have no gravity.
        BlockPos base = original.offset(0, 8, 0);

        TeardownYard yard = new TeardownYard(
                player.getUUID(),
                original,
                base,
                roster);
        YARDS.put(player.getUUID(), yard);

        int spawned = 0;
        for (int i = 0; i < roster.size(); i++) {
            if (spawnYardEntity(level, yard, i)) spawned++;
        }

        msg(source, "[TC teardown] spawned=" + spawned + "/" + roster.size());
        msg(source, "[TC teardown] stations are 18 blocks apart in a 4x2 grid.");
        msg(source, "[TC teardown] use /tc_runninggear tp <name>.");
        msg(source, "[TC teardown] chme3 gp7 v60 440 adler br01 forney ussr");

        if (!roster.isEmpty()) {
            ResourceLocation firstKey =
                    ForgeRegistries.ENTITY_TYPES.getKey(roster.get(0));
            if (firstKey != null) {
                teleportYard(source, firstKey.getPath());
            }
        }

        return spawned;
    }

    private static int yardClear(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return clearYard(player, true);
    }

    private static int teleportYard(
            CommandSourceStack source,
            String entityId) throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        TeardownYard yard = YARDS.get(player.getUUID());
        if (yard == null) {
            msg(source, "[TC teardown] no yard. Run /tc_runninggear yard first.");
            return 0;
        }

        for (int i = 0; i < yard.roster.size(); i++) {
            ResourceLocation key =
                    ForgeRegistries.ENTITY_TYPES.getKey(yard.roster.get(i));
            if (key == null || !entityId.equals(key.getPath())) continue;

            BlockPos station = stationPos(yard.base, i);
            // Stand beside and slightly above the locomotive.
            player.teleportTo(
                    station.getX() + 0.5D,
                    station.getY() + 2.5D,
                    station.getZ() + 7.5D);
            player.setYRot(180.0F);
            player.setXRot(12.0F);

            msg(source, "[TC teardown] station "
                    + (i + 1)
                    + "/"
                    + yard.roster.size()
                    + " "
                    + key.getPath());
            return 1;
        }

        msg(source, "[TC teardown] target not in current 8-train yard: " + entityId);
        return 0;
    }

    private static BlockPos stationPos(BlockPos base, int index) {
        int col = Math.floorMod(index, 4);
        int row = Math.floorDiv(index, 4);
        return base.offset(col * YARD_SPACING, 0, row * YARD_SPACING);
    }

    private static boolean spawnYardEntity(
            ServerLevel level,
            TeardownYard yard,
            int index) {

        if (index < 0 || index >= yard.roster.size()) return false;

        EntityType<?> type = yard.roster.get(index);
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null) return false;

        Entity raw;
        try {
            raw = type.create(level);
        } catch (Throwable t) {
            raw = null;
        }

        if (!(raw instanceof AbstractMinecart cart)) {
            if (raw != null) raw.discard();
            return false;
        }

        BlockPos station = stationPos(yard.base, index);
        cart.moveTo(
                station.getX() + 0.5D,
                station.getY() + 0.5D,
                station.getZ() + 0.5D,
                180.0F,
                0.0F);
        cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
        cart.setNoGravity(true);
        cart.addTag(YARD_TAG);
        cart.setCustomName(Component.literal(
                "[" + (index + 1) + "] " + key.getPath()));
        cart.setCustomNameVisible(true);
        cart.setGlowingTag(false);

        if (!level.addFreshEntity(cart)) {
            cart.discard();
            return false;
        }

        return true;
    }

    private static int clearYard(ServerPlayer player, boolean restore) {
        TeardownYard yard = YARDS.remove(player.getUUID());

        List<Entity> remove = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity.getTags().contains(YARD_TAG)) remove.add(entity);
        }
        for (Entity entity : remove) entity.discard();

        if (yard != null && restore) {
            player.teleportTo(
                    yard.original.getX() + 0.5D,
                    yard.original.getY() + 0.1D,
                    yard.original.getZ() + 0.5D);
        }

        if (yard != null) {
            player.displayClientMessage(
                    Component.literal("[TC teardown] yard cleared."),
                    true);
            return 1;
        }

        return 0;
    }
    private static boolean spawnCurrent(ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();

        clearTagged(level, session.starterSouth);

        if (session.index < 0 || session.index >= session.roster.size()) {
            return false;
        }

        EntityType<?> type = session.roster.get(session.index);
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null) return false;

        Entity raw;
        try {
            raw = type.create(level);
        } catch (Throwable t) {
            raw = null;
        }

        if (!(raw instanceof AbstractMinecart cart)) {
            if (raw != null) raw.discard();
            session.index++;
            if (session.index >= session.roster.size()) return false;
            return spawnCurrent(player, session);
        }

        BlockPos rail = session.starterSouth.offset(0, 0, -9);
        cart.moveTo(
                rail.getX() + 0.5D,
                rail.getY() + 0.15D,
                rail.getZ() + 0.5D,
                180.0F,
                0.0F);
        cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
        cart.addTag(TAG);
        cart.setCustomName(Component.literal(key.getPath()));
        cart.setCustomNameVisible(true);
        // TC_DEV38_R1G1Z7D3_NO_GLOW
        // Glow can reveal/outline a transparent square carrier and mimic a block.
        cart.setGlowingTag(false);

        if (!level.addFreshEntity(cart)) {
            cart.discard();
            session.index++;
            if (session.index >= session.roster.size()) return false;
            return spawnCurrent(player, session);
        }

        session.subjectId = cart.getId();
        session.stageTick = 0;
        session.lastStage = "";


        // TC_DEV38_R1G1Z7W9_FOCUS_NEW_SUBJECT

        // TC_DEV38_R1G1Z7Y6_FREE_CAMERA_NEW_SUBJECT - no teleport/aim

        player.displayClientMessage(
                Component.literal(
                        "[TC "
                                + (session.index + 1)
                                + "/"
                                + session.roster.size()
                                + "] "
                                + key.getPath()
                                + " - REST"),
                true);

        return true;
    }

    // TC_DEV38_R1G1Z7W9_GUIDED_FINAL_FIVE_ROSTER
    // TC_DEV38_R1G1Z7Y6_REMAINING_FOUR_FREE_CAMERA
    // TC_DEV38_R1G1Z7Y9_REMAINING_THREE_AXLE_CENTER
    // TC_DEV38_R1G1Z7Y10_REMAINING_TWO_COMPONENT_WHEELS
    // TC_DEV38_R1G1Z7Y11_FULL_ROD_PISTON_RUNTIME_ROSTER
    // TC_DEV38_R1G1Z7Y12_DEV42_EXACT_BATCH1_ROSTER
    // TC_DEV38_R1G1Z7Y13A_SHAY_DONE_NO_RODS_PISTONS
    // Shay removed from rod/piston testing: DONE / no rods-pistons on model.
    // Narrowed to 7 models with exact uploaded DEV42 ModelPart evidence.
    // 46 wheel-passed follow-up locomotives; wheels remain frozen.
    // BR01 WHEEL PASS / DONE -> rods/pistons; removed from wheel testing.
    // GP7 WHEEL PASS / DONE; removed from further wheel testing.
    // ChME3 WHEEL PASS / DONE; no rods on this model.
    private static List<EntityType<?>> discover(ServerLevel level) {
        List<EntityType<?>> out = new ArrayList<>();

        String[] ids = {
            "locomotive_steam_heavy",  // DEV42 HeavySteamLocomotiveModel
            "locomotive_steam_mogul",  // DEV42 MogulSteamLocomotiveModel
            "locomotive_steam_forney", // DEV42 ForneySteamLocomotiveModel
            "locomotive_diesel_v60",   // DEV42 RecoveredModelV60
            "locomotive_steam_br01",   // DEV42 Br01SteamLocomotiveModel
            "locomotive_steam_ussr"    // DEV42 UssrSteamLocomotiveModel
        };

        for (String id : ids) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation(Traincraft.MOD_ID, id));
            if (type == null) continue;

            Entity raw = null;
            try {
                raw = type.create(level);
                if (raw instanceof AbstractMinecart) {
                    out.add(type);
                }
            } catch (Throwable ignored) {
                // Skip only a genuinely unconstructable registration.
            } finally {
                if (raw != null) raw.discard();
            }
        }

        return out;
    }

    // TC_DEV38_R1G1Z7W9_GUIDED_CAMERA
    // Player is parked beside the starter and the client view tracks the
    // active locomotive while the existing motion pass drives it.
    private static void focusCamera(
            ServerPlayer player,
            Session session,
            AbstractMinecart cart,
            boolean teleport) {

        if (teleport) {
            BlockPos camera = session.starterSouth.offset(5, 3, -9);
            player.teleportTo(
                    camera.getX() + 0.5D,
                    camera.getY() + 0.2D,
                    camera.getZ() + 0.5D);
        }

        player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                new Vec3(
                        cart.getX(),
                        cart.getY() + 1.0D,
                        cart.getZ()));
    }
    private static AbstractMinecart findSubject(ServerLevel level, int id) {
        if (id < 0) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof AbstractMinecart cart ? cart : null;
    }

    private static void announce(
            ServerPlayer player,
            Session session,
            String stage) {
        if (stage.equals(session.lastStage)) return;
        session.lastStage = stage;

        player.displayClientMessage(
                Component.literal(
                        "[TC "
                                + (session.index + 1)
                                + "/"
                                + session.roster.size()
                                + "] "
                                + currentId(session)
                                + " - "
                                + stage),
                true);
    }

    private static String currentId(Session session) {
        if (session.index < 0 || session.index >= session.roster.size()) {
            return "<none>";
        }
        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(session.roster.get(session.index));
        return key == null ? "<unknown>" : key.getPath();
    }

    private static int stopExisting(ServerPlayer player, boolean restore) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return 0;

        clearTagged(player.serverLevel(), session.starterSouth);

        if (restore) {
            player.teleportTo(
                    session.original.getX() + 0.5D,
                    session.original.getY() + 0.1D,
                    session.original.getZ() + 0.5D);
        }

        player.displayClientMessage(
                Component.literal("[TC runninggear] stopped."),
                true);
        return 1;
    }

    private static void finish(
            ServerPlayer player,
            Session session,
            String reason) {
        clearTagged(player.serverLevel(), session.starterSouth);
        SESSIONS.remove(session.playerId);

        player.displayClientMessage(
                Component.literal("[TC runninggear] " + reason),
                false);

        player.teleportTo(
                session.original.getX() + 0.5D,
                session.original.getY() + 0.1D,
                session.original.getZ() + 0.5D);
    }

    private static void clearTagged(ServerLevel level, BlockPos center) {
        List<Entity> remove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(TAG)) remove.add(entity);
        }
        for (Entity entity : remove) entity.discard();
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static final class SweepSession {
        final UUID playerId;
        final List<EntityType<?>> roster;
        final double x;
        final double y;
        final double z;
        final float yaw;
        int index;
        int subjectId;

        SweepSession(
                UUID playerId,
                List<EntityType<?>> roster,
                double x,
                double y,
                double z,
                float yaw) {
            this.playerId = playerId;
            this.roster = roster;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.index = 0;
            this.subjectId = -1;
        }
    }
    private static final class TeardownYard {
        final UUID playerId;
        final BlockPos original;
        final BlockPos base;
        final List<EntityType<?>> roster;

        TeardownYard(
                UUID playerId,
                BlockPos original,
                BlockPos base,
                List<EntityType<?>> roster) {
            this.playerId = playerId;
            this.original = original;
            this.base = base;
            this.roster = roster;
        }
    }
    private static final class Session {
        final UUID playerId;
        final BlockPos original;
        final BlockPos starterSouth;
        final List<EntityType<?>> roster;
        int index;
        int subjectId;
        int stageTick;
        String lastStage;

        Session(
                UUID playerId,
                BlockPos original,
                BlockPos starterSouth,
                List<EntityType<?>> roster) {
            this.playerId = playerId;
            this.original = original;
            this.starterSouth = starterSouth;
            this.roster = roster;
            this.index = 0;
            this.subjectId = -1;
            this.stageTick = 0;
            this.lastStage = "";
        }
    }
}
