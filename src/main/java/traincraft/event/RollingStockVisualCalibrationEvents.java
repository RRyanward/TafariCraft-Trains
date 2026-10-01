package traincraft.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Universal visual-alignment selector.
 *
 * Existing /tccal powered-locomotive seat commands are untouched.
 * This subcommand adds model-Y calibration for every Traincraft minecart-based
 * rolling-stock entity without editing coupling/movement classes.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RollingStockVisualCalibrationEvents {

    private static final double SELECT_RADIUS = 128.0D;
    private static final String TARGET_KEY =
            "TafariRollingStockVisualCalibrationTarget";

    private RollingStockVisualCalibrationEvents() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tccal")
                        .then(Commands.literal("visual")
                                .then(Commands.literal("select")
                                        .then(Commands.argument(
                                                        "registry_id",
                                                        StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (String id : suggestedIds()) {
                                                        builder.suggest(id);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> select(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(
                                                                ctx, "registry_id")))))
                                .then(Commands.literal("next")
                                        .executes(ctx -> next(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource())))
                                .then(Commands.literal("up")
                                        .executes(ctx -> adjust(
                                                ctx.getSource(), +0.05D)))
                                .then(Commands.literal("down")
                                        .executes(ctx -> adjust(
                                                ctx.getSource(), -0.05D)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> reset(ctx.getSource())))
                                .then(Commands.literal("lock")
                                        .executes(ctx -> lock(ctx.getSource())))
                                .then(Commands.literal("apply")
                                        .executes(ctx -> apply(ctx.getSource())))
                                .then(Commands.literal("reload")
                                        .executes(ctx -> reload(ctx.getSource())))
                                .then(Commands.literal("config")
                                        .executes(ctx -> config(ctx.getSource())))));
    }

    private static int select(CommandSourceStack source, String requested)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = normalize(requested);
        Entity target = nearestById(player, id);

        if (target == null) {
            source.sendFailure(Component.literal(
                    "[TC VISUAL] No loaded traincraft:" + id +
                    " within " + (int)SELECT_RADIUS + " blocks."));
            return 0;
        }

        setTarget(player, target);
        showStatus(player, target);
        return 1;
    }

    private static int next(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<Entity> nearby = nearbyRollingStock(player);

        if (nearby.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[TC VISUAL] No TafariCraft rolling stock within " +
                    (int)SELECT_RADIUS + " blocks."));
            return 0;
        }

        nearby.sort(
                Comparator.comparing(RollingStockVisualCalibrationEvents::registryId)
                        .thenComparingInt(Entity::getId));

        UUID current = player.getPersistentData().hasUUID(TARGET_KEY)
                ? player.getPersistentData().getUUID(TARGET_KEY)
                : null;

        int index = 0;
        if (current != null) {
            for (int i = 0; i < nearby.size(); i++) {
                if (nearby.get(i).getUUID().equals(current)) {
                    index = (i + 1) % nearby.size();
                    break;
                }
            }
        }

        Entity target = nearby.get(index);
        setTarget(player, target);
        showStatus(player, target);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;
        showStatus(player, target);
        return 1;
    }

    private static int adjust(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        if (target instanceof LegacyPoweredLocomotive powered) {
            if (powered.isBuilder()) {
                source.sendFailure(Component.literal(
                        "[TC VISUAL] Tracks Builder is excluded."));
                return 0;
            }
            powered.tcRemoteAdjustHeight(player, delta);
        } else {
            String id = registryId(target);
            double value =
                    RollingStockCalibrationConfig.adjustModelY(id, delta);
            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "[TC VISUAL] %s modelY -> %.3f",
                            id, value)),
                    false);
        }

        showStatus(player, target);
        return 1;
    }

    private static int reset(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        if (target instanceof LegacyPoweredLocomotive powered) {
            powered.tcRemoteResetHeight(player);
        } else {
            String id = registryId(target);
            RollingStockCalibrationConfig.setModelY(id, 0.0D, false);
        }

        showStatus(player, target);
        return 1;
    }

    private static int lock(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        if (target instanceof LegacyPoweredLocomotive powered) {
            powered.tcLockCalibrationToConfig(player);
        } else {
            String id = registryId(target);
            double value = RollingStockCalibrationConfig.modelY(id, 0.0D);
            RollingStockCalibrationConfig.setModelY(id, value, true);
            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "[TC VISUAL LOCKED] %s modelY %.3f",
                            id, value)),
                    false);
        }

        showStatus(player, target);
        return 1;
    }

    private static int apply(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        RollingStockCalibrationConfig.reload();

        if (target instanceof LegacyPoweredLocomotive powered) {
            powered.tcApplyCalibrationFromConfig(player);
        }

        showStatus(player, target);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        RollingStockCalibrationConfig.reload();
        source.sendSuccess(
                () -> Component.literal(
                        "[TC VISUAL] Reloaded " +
                        RollingStockCalibrationConfig.pathString()),
                false);
        return 1;
    }

    private static int config(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "[TC VISUAL CONFIG] " +
                        RollingStockCalibrationConfig.pathString()),
                false);
        return 1;
    }

    private static Entity requireTarget(ServerPlayer player) {
        Entity target = selectedTarget(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.literal(
                            "[TC VISUAL] No selected stock. Use " +
                            "/tccal visual select <registry_id> or " +
                            "/tccal visual next."),
                    false);
        }
        return target;
    }

    private static Entity nearestById(ServerPlayer player, String id) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : nearbyRollingStock(player)) {
            if (!id.equals(registryId(entity))) continue;

            double distance = player.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private static List<Entity> nearbyRollingStock(ServerPlayer player) {
        return new ArrayList<>(player.level().getEntities(
                player,
                player.getBoundingBox().inflate(SELECT_RADIUS),
                RollingStockVisualCalibrationEvents::isRollingStock));
    }

    private static boolean isRollingStock(Entity entity) {
        if (!(entity instanceof AbstractMinecart)) return false;

        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null
                && Traincraft.MOD_ID.equals(key.getNamespace())
                && !"tracks_builder".equals(key.getPath());
    }

    private static String registryId(Entity entity) {
        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "unknown" : key.getPath();
    }

    private static String normalize(String raw) {
        String id = raw.toLowerCase(Locale.ROOT).trim();
        if (id.startsWith(Traincraft.MOD_ID + ":")) {
            id = id.substring(Traincraft.MOD_ID.length() + 1);
        }
        return id;
    }

    private static void setTarget(ServerPlayer player, Entity target) {
        Entity old = selectedTarget(player);
        if (old != null && old != target) {
            old.setGlowingTag(false);
        }

        player.getPersistentData().putUUID(TARGET_KEY, target.getUUID());
        target.setGlowingTag(true);
    }

    private static Entity selectedTarget(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)) return null;
        if (!player.getPersistentData().hasUUID(TARGET_KEY)) return null;

        Entity entity =
                server.getEntity(player.getPersistentData().getUUID(TARGET_KEY));
        if (entity == null || !isRollingStock(entity)) {
            player.getPersistentData().remove(TARGET_KEY);
            return null;
        }
        return entity;
    }

    private static void showStatus(ServerPlayer player, Entity target) {
        String id = registryId(target);
        double modelY;

        if (target instanceof LegacyPoweredLocomotive powered) {
            modelY = powered.tcCalibrationHeightAdjustment();
        } else {
            modelY = RollingStockCalibrationConfig.modelY(id, 0.0D);
        }

        double distance = Math.sqrt(player.distanceToSqr(target));
        boolean locked = RollingStockCalibrationConfig.isLocked(id);

        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC VISUAL TARGET] %s #%d  modelY %.3f  locked=%s  distance %.1f  GLOWING",
                        id, target.getId(), modelY,
                        Boolean.toString(locked), distance)),
                false);
    }

    private static List<String> suggestedIds() {
        return ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .filter(key -> Traincraft.MOD_ID.equals(key.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(id -> !"tracks_builder".equals(id))
                .filter(RollingStockVisualCalibrationEvents::looksLikeRollingStockId)
                .sorted()
                .toList();
    }

    private static boolean looksLikeRollingStockId(String id) {
        return id.startsWith("locomotive_")
                || id.startsWith("steam_tender")
                || id.endsWith("_car")
                || id.contains("caboose")
                || id.contains("coach")
                || id.startsWith("gs4_")
                || id.startsWith("milw_")
                || "freight_cart".equals(id)
                || "passenger_coach_blue".equals(id)
                || "caboose".equals(id)
                || "livestock_car".equals(id)
                || "mob_transport_car".equals(id);
    }
}