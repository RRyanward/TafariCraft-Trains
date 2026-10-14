package traincraft.dev;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.registry.TCBlocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DEV38 r1a - repeatable fleet visual-offset calibration pass.
 *
 * One-command start:
 *   /tcdev offsets all
 *
 * The command restores the known-good pre-Builder calibration file, builds one
 * standard straight Normal Track reference stand, then presents one rolling
 * stock entity at a time.  Builder is deliberately excluded because it owns a
 * specialized rotor/body renderer and was not present in the known-good file.
 *
 * During the pass:
 *   /tcdev offsets up/down          +/- 0.05 block
 *   /tcdev offsets fineup/finedown +/- 0.01 block
 *   /tcdev offsets pass             lock current + advance
 *   /tcdev offsets prev             previous train
 *   /tcdev offsets status
 *   /tcdev offsets finish
 *
 * This touches only the persistent calibration TOML.  Movement, couplers,
 * physics, tracks, HUDs and train entity classes are not changed.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38OffsetCalibrationCommands {
    private static final String P = "TCDev38OffsetPass";
    private static final String KEY_INDEX = P + "Index";
    private static final String KEY_X = P + "X";
    private static final String KEY_Y = P + "Y";
    private static final String KEY_Z = P + "Z";
    private static final String KEY_DIR = P + "Dir";
    private static final String KEY_ENTITY = P + "Entity";
    private static final String TARGET_KEY = "TafariRollingStockVisualCalibrationTarget";
    private static final String TEST_ENTITY = "TC_DEV38_OFFSET_TEST_ENTITY";
    private static final String BASELINE_RESOURCE =
            "/data/traincraft/dev/pre_builder_calibration.toml";
    private static final String BASELINE_SHA =
            "27A1A6BE0BF0C38C0A392EDB538930920268D1ACE3172E0DC2EC7ACB44707C5A";

    private Dev38OffsetCalibrationCommands() {}

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tcdev")
                        .then(Commands.literal("offsets")
                                .then(Commands.literal("all")
                                        .executes(ctx -> startAll(ctx.getSource())))
                                .then(Commands.literal("baseline")
                                        .executes(ctx -> restoreBaseline(ctx.getSource())))
                                .then(Commands.literal("next")
                                        .executes(ctx -> move(ctx.getSource(), +1, false)))
                                .then(Commands.literal("prev")
                                        .executes(ctx -> move(ctx.getSource(), -1, false)))
                                .then(Commands.literal("pass")
                                        .executes(ctx -> passAndNext(ctx.getSource())))
                                .then(Commands.literal("up")
                                        .executes(ctx -> adjust(ctx.getSource(), +0.05D)))
                                .then(Commands.literal("down")
                                        .executes(ctx -> adjust(ctx.getSource(), -0.05D)))
                                .then(Commands.literal("fineup")
                                        .executes(ctx -> adjust(ctx.getSource(), +0.01D)))
                                .then(Commands.literal("finedown")
                                        .executes(ctx -> adjust(ctx.getSource(), -0.01D)))
                                .then(Commands.literal("lock")
                                        .executes(ctx -> lock(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource())))
                                .then(Commands.literal("report")
                                        .executes(ctx -> report(ctx.getSource())))
                                .then(Commands.literal("finish")
                                        .executes(ctx -> finish(ctx.getSource())))));
    }

    private static int startAll(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (restoreBaseline(source) == 0) {
            return 0;
        }

        Direction dir = player.getDirection();
        BlockPos center = player.blockPosition().relative(dir, 6).above(1);

        player.getPersistentData().putInt(KEY_X, center.getX());
        player.getPersistentData().putInt(KEY_Y, center.getY());
        player.getPersistentData().putInt(KEY_Z, center.getZ());
        player.getPersistentData().putString(KEY_DIR, dir.getName());
        player.getPersistentData().putInt(KEY_INDEX, 0);

        buildStand(player.serverLevel(), center, dir);

        List<String> ids = fleetIds();
        if (ids.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] No rolling-stock entity IDs were found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[TC OFFSET] Known-good pre-Builder baseline restored. "
                        + ids.size()
                        + " rolling-stock entries in this pass. "
                        + "Tracks Builder is intentionally isolated."), false);

        return spawnIndex(player, 0);
    }

    private static int restoreBaseline(CommandSourceStack source) {
        Path active = RollingStockCalibrationConfig.path();

        try (InputStream in =
                     Dev38OffsetCalibrationCommands.class.getResourceAsStream(
                             BASELINE_RESOURCE)) {

            if (in == null) {
                source.sendFailure(Component.literal(
                        "[TC OFFSET] Packaged pre-Builder calibration baseline is missing."));
                return 0;
            }

            Files.createDirectories(active.getParent());

            if (Files.exists(active)) {
                Path backupDir = active.getParent().resolve("tcdev-offset-backups");
                Files.createDirectories(backupDir);

                String stamp = LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

                Path backup = backupDir.resolve(
                        "tafaricraft-trains-calibration-before-dev38-" + stamp + ".toml");

                Files.copy(active, backup, StandardCopyOption.REPLACE_EXISTING);

                source.sendSuccess(() -> Component.literal(
                        "[TC OFFSET] Current calibration backed up: "
                                + backup.toAbsolutePath()), false);
            }

            Files.copy(in, active, StandardCopyOption.REPLACE_EXISTING);
            RollingStockCalibrationConfig.reload();

            source.sendSuccess(() -> Component.literal(
                    "[TC OFFSET] Restored pre-Builder fleet calibration "
                            + BASELINE_SHA.substring(0, 12)
                            + "... -> "
                            + active.toAbsolutePath()), false);

            return 1;
        }
        catch (IOException ex) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] Could not restore calibration baseline: "
                            + ex.getMessage()));
            return 0;
        }
    }

    private static void buildStand(ServerLevel level, BlockPos center, Direction dir) {
        RailShape shape = dir.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;

        BlockState rail = TCBlocks.TRACK_NORMAL.get().defaultBlockState();
        if (rail.hasProperty(RailBlock.SHAPE)) {
            rail = rail.setValue(RailBlock.SHAPE, shape);
        }

        for (int i = -7; i <= 7; i++) {
            BlockPos rp = center.relative(dir, i);
            level.setBlock(rp.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(rp, rail, 3);

            Direction side = dir.getClockWise();
            level.setBlock(rp.relative(side, 2).below(),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(rp.relative(side, -2).below(),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
    }

    private static int move(CommandSourceStack source, int delta, boolean lockFirst)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (lockFirst) {
            lockCurrent(player, false);
        }

        List<String> ids = fleetIds();
        if (ids.isEmpty()) return 0;

        int index = player.getPersistentData().getInt(KEY_INDEX);
        index = Math.floorMod(index + delta, ids.size());
        player.getPersistentData().putInt(KEY_INDEX, index);

        return spawnIndex(player, index);
    }

    private static int passAndNext(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!hasSession(player)) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] Start with /tcdev offsets all"));
            return 0;
        }

        lockCurrent(player, true);

        List<String> ids = fleetIds();
        int index = player.getPersistentData().getInt(KEY_INDEX);

        if (index + 1 >= ids.size()) {
            source.sendSuccess(() -> Component.literal(
                    "[TC OFFSET] Final rolling-stock entry passed. Writing report."), false);
            report(source);
            return finish(source);
        }

        player.getPersistentData().putInt(KEY_INDEX, index + 1);
        return spawnIndex(player, index + 1);
    }

    private static int spawnIndex(ServerPlayer player, int index) {
        List<String> ids = fleetIds();

        if (ids.isEmpty()) return 0;
        index = Math.max(0, Math.min(index, ids.size() - 1));

        removeCurrent(player);

        String id = ids.get(index);
        ResourceLocation key = new ResourceLocation(Traincraft.MOD_ID, id);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);

        if (type == null) {
            player.displayClientMessage(Component.literal(
                    "[TC OFFSET] Missing EntityType traincraft:" + id), false);
            return 0;
        }

        Entity entity = type.create(player.serverLevel());
        if (entity == null) {
            player.displayClientMessage(Component.literal(
                    "[TC OFFSET] Could not create traincraft:" + id), false);
            return 0;
        }

        BlockPos center = sessionCenter(player);
        Direction dir = sessionDirection(player);

        entity.moveTo(
                center.getX() + 0.5D,
                center.getY() + 0.10D,
                center.getZ() + 0.5D,
                dir.toYRot(),
                0.0F);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setYRot(dir.toYRot());
        entity.setXRot(0.0F);
        entity.setInvulnerable(true);
        entity.setGlowingTag(true);
        entity.getPersistentData().putBoolean(TEST_ENTITY, true);
        entity.setCustomName(Component.literal(
                String.format(Locale.ROOT,
                        "[%d/%d] %s", index + 1, ids.size(), id)));
        entity.setCustomNameVisible(true);

        if (!player.serverLevel().addFreshEntity(entity)) {
            player.displayClientMessage(Component.literal(
                    "[TC OFFSET] Server refused test entity traincraft:" + id), false);
            return 0;
        }

        player.getPersistentData().putInt(KEY_INDEX, index);
        player.getPersistentData().putUUID(KEY_ENTITY, entity.getUUID());

        // Reuse the existing visual-calibration target slot, so the normal
        // /tc visual commands and this DEV38 harness point at the same train.
        player.getPersistentData().putUUID(TARGET_KEY, entity.getUUID());

        double modelY = RollingStockCalibrationConfig.modelY(id, 0.0D);
        boolean locked = RollingStockCalibrationConfig.isLocked(id);

        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "[TC OFFSET %d/%d] %s  modelY=%.3f  locked=%s",
                index + 1, ids.size(), id, modelY, Boolean.toString(locked))), false);
        player.displayClientMessage(Component.literal(
                "Adjust: up/down=.05  fineup/finedown=.01  PASS: /tcdev offsets pass"), false);

        return 1;
    }

    private static int adjust(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = currentEntity(player);

        if (target == null) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] No active test train. Run /tcdev offsets all"));
            return 0;
        }

        String id = registryId(target);

        if (target instanceof LegacyPoweredLocomotive powered) {
            if (powered.isBuilder()) {
                source.sendFailure(Component.literal(
                        "[TC OFFSET] Tracks Builder is isolated from this fleet pass."));
                return 0;
            }
            powered.tcRemoteAdjustHeight(player, delta);
        }
        else {
            RollingStockCalibrationConfig.adjustModelY(id, delta);
        }

        double value = RollingStockCalibrationConfig.modelY(id, 0.0D);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[TC OFFSET] %s modelY -> %.3f", id, value)), false);
        return 1;
    }

    private static int lock(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return lockCurrent(player, true) ? 1 : 0;
    }

    private static boolean lockCurrent(ServerPlayer player, boolean message) {
        Entity target = currentEntity(player);
        if (target == null) return false;

        String id = registryId(target);

        if (target instanceof LegacyPoweredLocomotive powered) {
            if (powered.isBuilder()) return false;
            powered.tcLockCalibrationToConfig(player);
        }
        else {
            double y = RollingStockCalibrationConfig.modelY(id, 0.0D);
            RollingStockCalibrationConfig.setModelY(id, y, true);
        }

        if (message) {
            double y = RollingStockCalibrationConfig.modelY(id, 0.0D);
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "[TC OFFSET PASS] %s locked at modelY %.3f", id, y)), false);
        }
        return true;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!hasSession(player)) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] No active pass. Run /tcdev offsets all"));
            return 0;
        }

        Entity target = currentEntity(player);
        List<String> ids = fleetIds();
        int index = player.getPersistentData().getInt(KEY_INDEX);

        if (target == null) {
            return spawnIndex(player, Math.max(0, Math.min(index, ids.size() - 1)));
        }

        String id = registryId(target);
        double y = RollingStockCalibrationConfig.modelY(id, 0.0D);
        boolean locked = RollingStockCalibrationConfig.isLocked(id);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[TC OFFSET %d/%d] %s modelY=%.3f locked=%s",
                index + 1, ids.size(), id, y, Boolean.toString(locked))), false);
        return 1;
    }

    private static int report(CommandSourceStack source) {
        List<String> ids = fleetIds();
        Path out = FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("traincraft-dev38-offset-pass.txt");

        List<String> lines = new ArrayList<>();
        lines.add("TAFARICRAFT - TRAINS DEV38 r1a FLEET OFFSET PASS");
        lines.add("PRE_BUILDER_BASELINE_SHA256=" + BASELINE_SHA);
        lines.add("ROLLING_STOCK_COUNT=" + ids.size());
        lines.add("TRACKS_BUILDER=EXCLUDED_SPECIALIZED_RENDERER");
        lines.add("");

        for (String id : ids) {
            lines.add(String.format(Locale.ROOT,
                    "%s | hasModel=%s | modelY=%.4f | locked=%s",
                    id,
                    Boolean.toString(RollingStockCalibrationConfig.hasModel(id)),
                    RollingStockCalibrationConfig.modelY(id, 0.0D),
                    Boolean.toString(RollingStockCalibrationConfig.isLocked(id))));
        }

        try {
            Files.createDirectories(out.getParent());
            Files.write(out, lines, StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal(
                    "[TC OFFSET] Report: " + out.toAbsolutePath()), false);
            return 1;
        }
        catch (IOException ex) {
            source.sendFailure(Component.literal(
                    "[TC OFFSET] Could not write report: " + ex.getMessage()));
            return 0;
        }
    }

    private static int finish(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        removeCurrent(player);

        player.getPersistentData().remove(KEY_INDEX);
        player.getPersistentData().remove(KEY_X);
        player.getPersistentData().remove(KEY_Y);
        player.getPersistentData().remove(KEY_Z);
        player.getPersistentData().remove(KEY_DIR);
        player.getPersistentData().remove(KEY_ENTITY);
        player.getPersistentData().remove(TARGET_KEY);

        source.sendSuccess(() -> Component.literal(
                "[TC OFFSET] Fleet offset pass finished. Test stand left in place for inspection."), false);
        return 1;
    }

    private static void removeCurrent(ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(KEY_ENTITY)) return;

        UUID uuid = player.getPersistentData().getUUID(KEY_ENTITY);
        Entity old = player.serverLevel().getEntity(uuid);

        if (old != null
                && old.getPersistentData().getBoolean(TEST_ENTITY)) {
            old.discard();
        }

        player.getPersistentData().remove(KEY_ENTITY);
    }

    private static Entity currentEntity(ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(KEY_ENTITY)) return null;

        Entity entity = player.serverLevel().getEntity(
                player.getPersistentData().getUUID(KEY_ENTITY));

        if (entity == null
                || !entity.getPersistentData().getBoolean(TEST_ENTITY)) {
            return null;
        }

        return entity;
    }

    private static boolean hasSession(ServerPlayer player) {
        return player.getPersistentData().contains(KEY_X)
                && player.getPersistentData().contains(KEY_Y)
                && player.getPersistentData().contains(KEY_Z);
    }

    private static BlockPos sessionCenter(ServerPlayer player) {
        return new BlockPos(
                player.getPersistentData().getInt(KEY_X),
                player.getPersistentData().getInt(KEY_Y),
                player.getPersistentData().getInt(KEY_Z));
    }

    private static Direction sessionDirection(ServerPlayer player) {
        Direction d = Direction.byName(
                player.getPersistentData().getString(KEY_DIR));
        return d == null ? Direction.NORTH : d;
    }

    private static String registryId(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "unknown" : key.getPath();
    }

    private static List<String> fleetIds() {
        return ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .filter(key -> Traincraft.MOD_ID.equals(key.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(id -> !"tracks_builder".equals(id))
                .filter(Dev38OffsetCalibrationCommands::looksLikeRollingStock)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static boolean looksLikeRollingStock(String id) {
        return id.startsWith("locomotive_")
                || id.contains("tender")
                || id.endsWith("_car")
                || id.contains("caboose")
                || id.contains("coach")
                || id.startsWith("gs4_")
                || id.startsWith("milw_")
                || "freight_cart".equals(id)
                || "passenger_coach_blue".equals(id)
                || "livestock_car".equals(id)
                || "mob_transport_car".equals(id);
    }
}
