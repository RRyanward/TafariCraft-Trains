package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * DEV38 r1g1z TEMP wheel-validation harness.
 *
 * One client session can cycle the full dynamically discovered Traincraft
 * AbstractMinecart roster over the accepted T7 course.
 *
 * Remove this class before the public release command-surface freeze.
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class Dev38R1g1zWheelTestCommand {
    private static final String TAG = "tc_dev38_r1g1z_wheeltest";
    private static final int EXPECTED_ROSTER = 111;
    // TC_DEV38_R1G1Z4F_WORKING_SET_2X4
    private static final int BATCH_SIZE = 4;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private Dev38R1g1zWheelTestCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(
                Commands.literal("tc_wheeltest")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("all").executes(ctx -> start(ctx.getSource())))
                        // TC_DEV38_R1G1Z4D_WHEEL_FOCUS
                        .then(Commands.literal("focus").executes(ctx -> focus(ctx.getSource())))
                        .then(Commands.literal("next").executes(ctx -> step(ctx.getSource(), 1)))
                        .then(Commands.literal("prev").executes(ctx -> step(ctx.getSource(), -1)))
                        .then(Commands.literal("respawn").executes(ctx -> respawn(ctx.getSource())))
                        .then(Commands.literal("forward").executes(ctx -> push(ctx.getSource(), -0.22D)))
                        .then(Commands.literal("reverse").executes(ctx -> push(ctx.getSource(), 0.22D)))
                        .then(Commands.literal("stop").executes(ctx -> push(ctx.getSource(), 0.0D)))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC wheeltest] full-fleet T7 wheel validation");
        msg(source, "/tc_wheeltest all - build accepted T7 course + discover roster + spawn batch 1");
        msg(source, "/tc_wheeltest focus - exact working set: 8 subjects, 2 batches of 4");
        msg(source, "/tc_wheeltest next | prev - replace current subjects with adjacent batch");
        msg(source, "/tc_wheeltest respawn - respawn current batch at the T7 starter");
        msg(source, "/tc_wheeltest forward | reverse | stop - give current subjects a starter-lane motion pulse");
        msg(source, "/tc_wheeltest status - show batch + subject IDs");
        msg(source, "/tc_wheeltest clear - remove wheel-test subjects");
        msg(source, "Batch size=" + BATCH_SIZE + "; expected Traincraft roster=" + EXPECTED_ROSTER);
        return 1;
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        clearTagged(level, player.blockPosition());
        BlockPos starterSouth = player.blockPosition().offset(48, 20, 48);

        // Reuse the accepted real-placement T7 builder instead of duplicating
        // any track-placement logic here.
        int courseResult;
        try {
            courseResult = source.getServer().getCommands().getDispatcher()
                    .execute("tc_t7_full_test_track", source);
        } catch (Exception ex) {
            msg(source, "[TC wheeltest] T7 builder failed: " + ex.getClass().getSimpleName()
                    + " " + String.valueOf(ex.getMessage()));
            return 0;
        }

        List<EntityType<?>> roster = discoverStocks(level);
        if (roster.isEmpty()) {
            msg(source, "[TC wheeltest] no Traincraft AbstractMinecart entity types discovered.");
            return 0;
        }

        Session s = new Session(starterSouth, roster, 0);
        SESSIONS.put(player.getUUID(), s);
        msg(source, "[TC wheeltest] T7 result=" + courseResult
                + " roster=" + roster.size()
                + (roster.size() == EXPECTED_ROSTER ? " (111/111)" : " (EXPECTED 111)"));
        return spawnCurrent(source, player, s);
    }

private static int focus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session current = SESSIONS.get(player.getUUID());
        if (current == null) {
            msg(source, "[TC wheeltest] no active session. Run /tc_wheeltest all first.");
            return 0;
        }

        // TC_DEV38_R1G1Z4F_WORKING_SET_2X4
        // Exact ordered working set:
        //   batch 1 = Forney / SD40 / SD70 / V60
        //   batch 2 = ChME3 / GP7 / Kof / Class 08
        List<EntityType<?>> focusRoster = new ArrayList<>();

        addExactWorking(focusRoster, current.roster, "locomotive_steam_forney");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_sd40");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_sd70");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_v60");

        addExactWorking(focusRoster, current.roster, "locomotive_diesel_chme3");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_gp7");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_kof");
        addExactWorking(focusRoster, current.roster, "locomotive_diesel_shunter");

        if (focusRoster.size() != 8) {
            msg(source, "[TC wheeltest] SAFETY STOP: working roster resolved "
                    + focusRoster.size() + "/8 subjects.");
            msg(source, "[TC wheeltest] Expected exact IDs: locomotive_steam_forney, "
                    + "locomotive_diesel_sd40, locomotive_diesel_sd70, locomotive_diesel_v60, "
                    + "locomotive_diesel_chme3, locomotive_diesel_gp7, locomotive_diesel_kof, "
                    + "locomotive_diesel_shunter");
            return 0;
        }

        Session focus = new Session(current.starterSouth, focusRoster, 0);
        SESSIONS.put(player.getUUID(), focus);

        msg(source, "[TC wheeltest] r1g1z4f working set=8, batch size=4, total batches=2");
        msg(source, "[TC wheeltest] batch 1: Forney | SD40 | SD70 | V60");
        msg(source, "[TC wheeltest] batch 2: ChME3 | GP7 | Kof | Class 08");
        return spawnCurrent(source, player, focus);
    }

    private static void addExactWorking(
            List<EntityType<?>> out,
            List<EntityType<?>> source,
            String wantedPath) {
        for (EntityType<?> type : source) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || !"traincraft".equals(id.getNamespace())) continue;
            if (wantedPath.equals(id.getPath())) {
                out.add(type);
                return;
            }
        }
    }
    private static int step(CommandSourceStack source, int direction) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            msg(source, "[TC wheeltest] no active session. Run /tc_wheeltest all");
            return 0;
        }

        int batches = batchCount(s.roster.size());
        s.batch = Math.max(0, Math.min(batches - 1, s.batch + direction));
        return spawnCurrent(source, player, s);
    }

    private static int respawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            msg(source, "[TC wheeltest] no active session. Run /tc_wheeltest all");
            return 0;
        }
        return spawnCurrent(source, player, s);
    }

    private static int spawnCurrent(CommandSourceStack source, ServerPlayer player, Session s) {
        ServerLevel level = player.serverLevel();
        clearTagged(level, s.starterSouth);

        int first = s.batch * BATCH_SIZE;
        int lastExclusive = Math.min(first + BATCH_SIZE, s.roster.size());
        if (first >= lastExclusive) {
            msg(source, "[TC wheeltest] empty batch.");
            return 0;
        }

        List<String> ids = new ArrayList<>();
        int spawned = 0;

        // c2-r3 T7 has a 24-rail vanilla starter extending north from
        // starterSouth. Spacing 7 keeps long locomotives from overlapping.
        final int[] northOffsets = {2, 9, 16, 23};

        for (int i = first; i < lastExclusive; i++) {
            EntityType<?> type = s.roster.get(i);
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) continue;

            Entity raw;
            try {
                raw = type.create(level);
            } catch (Throwable t) {
                msg(source, "[TC wheeltest] CREATE_FAIL " + key + " "
                        + t.getClass().getSimpleName());
                continue;
            }
            if (!(raw instanceof AbstractMinecart cart)) {
                if (raw != null) raw.discard();
                continue;
            }

            int slot = i - first;
            BlockPos rail = s.starterSouth.offset(0, 0, -northOffsets[slot]);
            cart.moveTo(rail.getX() + 0.5D, rail.getY() + 0.15D, rail.getZ() + 0.5D,
                    180.0F, 0.0F);
            cart.setDeltaMovement(0.0D, 0.0D, 0.0D);
            cart.addTag(TAG);
            cart.setCustomName(Component.literal(key.getPath()));
            cart.setCustomNameVisible(true);
            cart.setGlowingTag(true);

            if (level.addFreshEntity(cart)) {
                ids.add(key.getPath());
                spawned++;
            }
        }

        int batches = batchCount(s.roster.size());
        msg(source, "[TC wheeltest] batch " + (s.batch + 1) + "/" + batches
                + " subjects " + (first + 1) + "-" + lastExclusive
                + " of " + s.roster.size());
        msg(source, "[TC wheeltest] " + String.join(" | ", ids));
        msg(source, "[TC wheeltest] inspect at rest, then use forward/reverse pulses or drive a powered subject.");
        return spawned;
    }

    private static int push(CommandSourceStack source, double zSpeed) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            msg(source, "[TC wheeltest] no active session.");
            return 0;
        }

        int changed = 0;
        for (Entity e : tagged(player.serverLevel(), s.starterSouth)) {
            if (e instanceof AbstractMinecart cart) {
                cart.setDeltaMovement(0.0D, 0.0D, zSpeed);
                changed++;
            }
        }
        msg(source, "[TC wheeltest] motion=" + zSpeed + " subjects=" + changed);
        return changed;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            msg(source, "[TC wheeltest] no active session.");
            return 0;
        }

        int first = s.batch * BATCH_SIZE;
        int lastExclusive = Math.min(first + BATCH_SIZE, s.roster.size());
        List<String> ids = new ArrayList<>();
        for (int i = first; i < lastExclusive; i++) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(s.roster.get(i));
            if (key != null) ids.add(key.getPath());
        }

        msg(source, "[TC wheeltest] batch " + (s.batch + 1) + "/" + batchCount(s.roster.size())
                + " roster=" + s.roster.size()
                + " liveSubjects=" + tagged(player.serverLevel(), s.starterSouth).size());
        msg(source, "[TC wheeltest] " + String.join(" | ", ids));
        return 1;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session s = SESSIONS.remove(player.getUUID());
        BlockPos center = s != null ? s.starterSouth : player.blockPosition();
        int removed = clearTagged(player.serverLevel(), center);
        msg(source, "[TC wheeltest] cleared " + removed + " temporary subjects.");
        return removed;
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
                if (raw instanceof AbstractMinecart) out.add(type);
            } catch (Throwable ignored) {
                // A failed constructor stays out of the test roster and will
                // make the visible roster count differ from 111.
            } finally {
                if (raw != null) raw.discard();
            }
        }
        return out;
    }

    private static int batchCount(int rosterSize) {
        return Math.max(1, (rosterSize + BATCH_SIZE - 1) / BATCH_SIZE);
    }

    private static List<Entity> tagged(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(180.0D, 80.0D, 180.0D);
        return level.getEntities((Entity) null, box, e -> e.getTags().contains(TAG));
    }

    private static int clearTagged(ServerLevel level, BlockPos center) {
        List<Entity> found = tagged(level, center);
        for (Entity e : found) e.discard();
        return found.size();
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static final class Session {
        final BlockPos starterSouth;
        final List<EntityType<?>> roster;
        int batch;

        Session(BlockPos starterSouth, List<EntityType<?>> roster, int batch) {
            this.starterSouth = starterSouth;
            this.roster = roster;
            this.batch = batch;
        }
    }

    // TC_DEV38_R1G1Z7Y18C1_COMMAND_BRIDGE
    // The new class was not auto-subscribed by the Forge dev-launch scanner.
    // This existing debug command IS confirmed subscribed in runtime debug.log.
    // Delegate only the registration/tick events; never alter existing wheel code.
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tcY18c1BridgeCommands(net.minecraftforge.event.RegisterCommandsEvent e) {
        traincraft.debug.Dev38StationaryRunningGearTest.onRegister(e);
        com.mojang.logging.LogUtils.getLogger().info(
                "[TC-Y18C1-COMMAND-REGISTERED] /tc_gearfreeze via existing Forge debug subscriber");
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tcY18c1BridgeTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        traincraft.debug.Dev38StationaryRunningGearTest.onServerTick(e);
    }
}