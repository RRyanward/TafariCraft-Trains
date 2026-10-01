package traincraft.event;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// TafariCraft r1f6 seat-calibration pass.
// r1f5 exact selector/glow is preserved.
// Tracks Builder remains completely excluded from calibration.
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PoweredCalibrationEvents {
    private static final double SELECT_RADIUS = 96.0D;
    private static final String TARGET_KEY = "TafariPoweredCalibrationTarget";

    private PoweredCalibrationEvents() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tccal")
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("next")
                                .executes(ctx -> next(ctx.getSource())))
                        .then(Commands.literal("hup")
                                .executes(ctx -> height(ctx.getSource(), +0.05D)))
                        .then(Commands.literal("hdown")
                                .executes(ctx -> height(ctx.getSource(), -0.05D)))                        .then(Commands.literal("hreset")
                                .executes(ctx -> heightReset(ctx.getSource())))
                        .then(Commands.literal("lock")
                                .executes(ctx -> lockCalibration(ctx.getSource())))
                        .then(Commands.literal("apply")
                                .executes(ctx -> applyCalibration(ctx.getSource())))
                        .then(Commands.literal("reload")
                                .executes(ctx -> reloadCalibration(ctx.getSource())))
                        .then(Commands.literal("config")
                                .executes(ctx -> calibrationConfigPath(ctx.getSource())))
                        .then(Commands.literal("pin")
                                .executes(ctx -> pin(ctx.getSource())))
                        .then(Commands.literal("sup")
                                .executes(ctx -> seatY(ctx.getSource(), +0.05D)))
                        .then(Commands.literal("sdown")
                                .executes(ctx -> seatY(ctx.getSource(), -0.05D)))
                        .then(Commands.literal("sfront")
                                .executes(ctx -> seatLong(ctx.getSource(), +0.25D)))
                        .then(Commands.literal("sback")
                                .executes(ctx -> seatLong(ctx.getSource(), -0.25D)))
                        .then(Commands.literal("sleft")
                                .executes(ctx -> seatSide(ctx.getSource(), -0.10D)))
                        .then(Commands.literal("sright")
                                .executes(ctx -> seatSide(ctx.getSource(), +0.10D)))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("select")
                                .then(Commands.literal("vl10")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_electric_vl10")))
                                .then(Commands.literal("zeroed")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_electric_high_speed_zeroed")))
                                .then(Commands.literal("cart")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_electric_minetrain")))
                                .then(Commands.literal("bre69")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_electric_br_e69")))
                                .then(Commands.literal("sd70")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_diesel_sd70")))
                                .then(Commands.literal("shunter")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_diesel_shunter")))
                                .then(Commands.literal("kof")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_diesel_kof")))
                                .then(Commands.literal("gp7")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_diesel_gp7")))
                                .then(Commands.literal("chme3")
                                        .executes(ctx -> select(ctx.getSource(), "locomotive_diesel_chme3")))));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handleItem(event.getEntity(), event.getHand(),
                event.getEntity().getItemInHand(event.getHand()), event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleItem(event.getEntity(), event.getHand(),
                event.getEntity().getItemInHand(event.getHand()), event);
    }

    private static void handleItem(
            Player player,
            InteractionHand hand,
            ItemStack held,
            PlayerInteractEvent event) {

        if (player.level().isClientSide) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown()) return;

        boolean recognized =
                held.is(Items.COMPASS) ||
                held.is(Items.STICK) ||
                held.is(Items.FEATHER) ||
                held.is(Items.FLINT) ||
                held.is(Items.PAPER) ||
                held.is(Items.IRON_NUGGET) ||
                held.is(Items.COAL) ||
                held.is(Items.GOLD_NUGGET);

        if (!recognized) return;

        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (held.is(Items.COMPASS)) {
            cycleTarget(serverPlayer);
            consume(event);
            return;
        }

        LegacyPoweredLocomotive target = selectedTarget(serverPlayer);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.literal("[TC CAL] No selected train. Use /tccal select <name> or crouch + Compass."),
                    false);
            consume(event);
            return;
        }

        if (held.is(Items.STICK)) {
            target.tcRemotePinSeatFromPlayer(serverPlayer);
        } else if (held.is(Items.FEATHER)) {
            target.tcRemoteAdjustSeatY(serverPlayer, +0.05D);
        } else if (held.is(Items.FLINT)) {
            target.tcRemoteAdjustSeatY(serverPlayer, -0.05D);
        } else if (held.is(Items.PAPER)) {
            target.tcRemoteClearSeat(serverPlayer);
        } else if (held.is(Items.IRON_NUGGET)) {
            target.tcRemoteAdjustHeight(serverPlayer, +0.05D);
        } else if (held.is(Items.COAL)) {
            target.tcRemoteAdjustHeight(serverPlayer, -0.05D);
        } else if (held.is(Items.GOLD_NUGGET)) {
            target.tcRemoteResetHeight(serverPlayer);
        }

        showStatus(serverPlayer, target);
        consume(event);
    }

    private static int select(CommandSourceStack source, String variant)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = nearestVariant(player, variant);
        if (target == null) {
            source.sendFailure(Component.literal("[TC CAL] No loaded " + variant + " within 96 blocks."));
            return 0;
        }
        setTarget(player, target);
        showStatus(player, target);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = selectedTarget(player);
        if (target == null) {
            source.sendFailure(Component.literal("[TC CAL] No selected train."));
            return 0;
        }
        showStatus(player, target);
        return 1;
    }

    private static int next(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return cycleTarget(player) == null ? 0 : 1;
    }

    private static int height(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteAdjustHeight(player, delta);
        showStatus(player, target);
        return 1;
    }

    private static int heightReset(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteResetHeight(player);
        showStatus(player, target);
        return 1;
    }
    private static int lockCalibration(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcLockCalibrationToConfig(player);
        showStatus(player, target);
        return 1;
    }

    private static int applyCalibration(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcApplyCalibrationFromConfig(player);
        showStatus(player, target);
        return 1;
    }

    private static int reloadCalibration(CommandSourceStack source) {
        traincraft.config.RollingStockCalibrationConfig.reload();
        source.sendSuccess(
                () -> Component.literal(
                        "[TC CAL] Reloaded " +
                        traincraft.config.RollingStockCalibrationConfig.pathString()),
                false);
        return 1;
    }

    private static int calibrationConfigPath(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "[TC CAL CONFIG] " +
                        traincraft.config.RollingStockCalibrationConfig.pathString()),
                false);
        return 1;
    }
    private static int pin(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemotePinSeatFromPlayer(player);
        showStatus(player, target);
        return 1;
    }

    private static int seatY(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteAdjustSeatY(player, delta);
        showStatus(player, target);
        return 1;
    }

    private static int seatLong(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteAdjustSeatLong(player, delta);
        showStatus(player, target);
        return 1;
    }

    private static int seatSide(CommandSourceStack source, double delta)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteAdjustSeatLateral(player, delta);
        showStatus(player, target);
        return 1;
    }

    private static int clear(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LegacyPoweredLocomotive target = requireTarget(player);
        if (target == null) return 0;
        target.tcRemoteClearSeat(player);
        showStatus(player, target);
        return 1;
    }

    private static LegacyPoweredLocomotive requireTarget(ServerPlayer player) {
        LegacyPoweredLocomotive target = selectedTarget(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.literal("[TC CAL] No selected train. Use /tccal select <name>."),
                    false);
        }
        return target;
    }

    private static void showStatus(ServerPlayer player, LegacyPoweredLocomotive target) {
        double distance = Math.sqrt(player.distanceToSqr(target));
        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC CAL TARGET] %s  #%d  distance %.1f  GLOWING",
                        target.variantId(), target.getId(), distance)),
                false);
        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC CAL FACING] trainYaw %.1f  visibleYaw %.1f",
                        target.getTrainFacingYaw(),
                        target.getRenderTrainFacingYaw(1.0F))),
                false);
        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC CAL MODEL] base %.3f  adj %.3f  synced %.3f  total %.3f",
                        target.tcCalibrationBaseModelLift(),
                        target.tcCalibrationHeightAdjustment(),
                        target.tcCalibrationSyncedHeightAdjustment(),
                        target.tcCalibrationTotalModelLift())),
                false);
        if (target.tcCalibrationHasSeat()) {
            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "[TC CAL SEAT] long %.3f  feetY %.3f  side %.3f",
                            target.tcCalibrationSeatLong(),
                            target.tcCalibrationSeatFootY(),
                            target.tcCalibrationSeatLateral())),
                    false);
        } else {
            player.displayClientMessage(
                    Component.literal("[TC CAL SEAT] no seat pin yet"),
                    false);
        }
    }

    private static LegacyPoweredLocomotive cycleTarget(ServerPlayer player) {
        List<LegacyPoweredLocomotive> nearby = nearbyPowered(player);
        if (nearby.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("[TC CAL] No rideable powered locomotive within 96 blocks."),
                    false);
            return null;
        }

        nearby.sort(Comparator.comparingInt(Entity::getId));
        UUID current = player.getPersistentData().hasUUID(TARGET_KEY)
                ? player.getPersistentData().getUUID(TARGET_KEY)
                : null;

        int next = 0;
        if (current != null) {
            for (int i = 0; i < nearby.size(); i++) {
                if (nearby.get(i).getUUID().equals(current)) {
                    next = (i + 1) % nearby.size();
                    break;
                }
            }
        }

        LegacyPoweredLocomotive selected = nearby.get(next);
        setTarget(player, selected);
        showStatus(player, selected);
        return selected;
    }

    private static LegacyPoweredLocomotive nearestVariant(ServerPlayer player, String variant) {
        LegacyPoweredLocomotive best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LegacyPoweredLocomotive candidate : nearbyPowered(player)) {
            if (!variant.equals(candidate.variantId())) continue;
            double distance = player.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static void setTarget(ServerPlayer player, LegacyPoweredLocomotive target) {
        LegacyPoweredLocomotive old = selectedTarget(player);
        if (old != null && old != target) {
            old.tcSetCalibrationSelected(false);
        }
        player.getPersistentData().putUUID(TARGET_KEY, target.getUUID());
        target.tcSetCalibrationSelected(true);
    }

    private static LegacyPoweredLocomotive selectedTarget(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)) return null;
        if (!player.getPersistentData().hasUUID(TARGET_KEY)) return null;

        UUID uuid = player.getPersistentData().getUUID(TARGET_KEY);
        Entity entity = server.getEntity(uuid);
        if (!(entity instanceof LegacyPoweredLocomotive powered)) return null;
        if (powered.isBuilder()) {
            powered.tcSetCalibrationSelected(false);
            player.getPersistentData().remove(TARGET_KEY);
            return null;
        }
        return powered;
    }

    private static List<LegacyPoweredLocomotive> nearbyPowered(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                        LegacyPoweredLocomotive.class,
                        player.getBoundingBox().inflate(SELECT_RADIUS))
                .stream()
                .filter(train -> !train.isBuilder())
                .toList();
    }

    private static void consume(PlayerInteractEvent event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}