package traincraft.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.tender.SteamTender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * r1f12f universal visual/seat calibration controls.
 *
 * Main simple interface:
 *
 *   /tc visual select <registry_id>
 *   /tc visual next
 *   /tc visual body
 *   /tc visual seat 1|2|3
 *   /tc visual up
 *   /tc visual down
 *   /tc visual front
 *   /tc visual back
 *   /tc visual left
 *   /tc visual right
 *   /tc visual lock
 *   /tc visual auto
 *
 * In BODY mode, up/down adjusts the rolling-stock model.
 * In SEAT mode, all six directions adjust the active rider seat.
 *
 * The old /tccal visual seat/sfront/... aliases remain available.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RollingStockSeatCalibrationEvents {

    public static final String TARGET_KEY =
            "TafariRollingStockVisualCalibrationTarget";

    private static final double SELECT_RADIUS = 128.0D;

    /**
     * Physical-client runClient uses an integrated server in the same JVM.
     * Keeping the preview slot here lets the client render overlay react
     * immediately without editing frozen rolling-stock entity classes.
     *
     * Normal gameplay does not require this map: riders use passenger order
     * Seat 1 -> Seat 2 -> Seat 3.
     */
    private static final Map<UUID, Integer> PREVIEW_SLOTS =
            new ConcurrentHashMap<>();

    private RollingStockSeatCalibrationEvents() {}


    /**
     * DEV38 seat-pass bridge.
     * slot 1..3 selects the existing accepted seat preview; slot <= 0 clears it.
     * Test harness only -- normal riding behavior is unchanged.
     */
    public static void tcDev38PreviewSeat(
            ServerPlayer player,
            int slot) {
        if (slot <= 0) {
            PREVIEW_SLOTS.remove(player.getUUID());
            return;
        }
        PREVIEW_SLOTS.put(
                player.getUUID(),
                Math.max(1, Math.min(3, slot)));
    }
    /**
     * DEV38 r1c2: true only while the temporary seat-perfect-pass preview is active.
     * Normal gameplay does not set this map entry.
     */
    public static boolean tcDev38HasPreview(java.util.UUID playerId) {
        return PREVIEW_SLOTS.containsKey(playerId);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc")
                        // TC_DEV38_R1D_OP_ONLY_VISUAL
                        .requires(source -> source.hasPermission(2))
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

                                // BODY commands stay simple and unambiguous.
                                // These always move/reset the train body, never a seat.
                                .then(Commands.literal("up")
                                        .executes(ctx -> bodyDirection(
                                                ctx.getSource(),
                                                Direction.UP)))
                                .then(Commands.literal("down")
                                        .executes(ctx -> bodyDirection(
                                                ctx.getSource(),
                                                Direction.DOWN)))
                                .then(Commands.literal("body")
                                        .executes(ctx -> bodyMode(ctx.getSource()))
                                        .then(Commands.literal("up")
                                                .executes(ctx -> bodyDirection(
                                                        ctx.getSource(),
                                                        Direction.UP)))
                                        .then(Commands.literal("down")
                                                .executes(ctx -> bodyDirection(
                                                        ctx.getSource(),
                                                        Direction.DOWN)))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> bodyReset(
                                                        ctx.getSource()))))

                                // SEAT commands are now all underneath the slot.
                                //
                                // /tc visual seat 1 up
                                // /tc visual seat 1 down
                                // /tc visual seat 1 front
                                // /tc visual seat 1 back
                                // /tc visual seat 1 left
                                // /tc visual seat 1 right
                                //
                                // same for Seat 2 and Seat 3.
                                .then(Commands.literal("seat")
                                        .then(Commands.argument(
                                                        "slot",
                                                        IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> selectSeat(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                ctx, "slot")))
                                                .then(Commands.literal("up")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.UP)))
                                                .then(Commands.literal("down")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.DOWN)))
                                                .then(Commands.literal("front")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.FRONT)))
                                                .then(Commands.literal("back")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.BACK)))
                                                .then(Commands.literal("left")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.LEFT)))
                                                .then(Commands.literal("right")
                                                        .executes(ctx -> seatDirection(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                                Direction.RIGHT)))
                                                .then(Commands.literal("capture")
                                                        .executes(ctx -> seatCapture(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"))))
                                                .then(Commands.literal("reset")
                                                        .executes(ctx -> seatReset(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"))))
                                                .then(Commands.literal("status")
                                                        .executes(ctx -> seatSlotStatus(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"))))))

                                .then(Commands.literal("lock")
                                        .executes(ctx -> lock(ctx.getSource())))
                                .then(Commands.literal("auto")
                                        .executes(ctx -> auto(ctx.getSource())))
                                .then(Commands.literal("reload")
                                        .executes(ctx -> reload(ctx.getSource())))
                                .then(Commands.literal("config")
                                        .executes(ctx -> config(ctx.getSource())))));
    }
    private enum Direction {
        UP, DOWN, FRONT, BACK, LEFT, RIGHT
    }

    /**
     * Top-level /tc visual up/down ALWAYS means body movement.
     * It temporarily ignores any selected seat preview, then restores it.
     */
    private static int bodyDirection(
            CommandSourceStack source,
            Direction direction)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Integer oldSlot = PREVIEW_SLOTS.remove(player.getUUID());

        try {
            return direction(source, direction);
        } finally {
            if (oldSlot != null) {
                PREVIEW_SLOTS.put(player.getUUID(), oldSlot);
            }
        }
    }

    private static int bodyReset(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Integer oldSlot = PREVIEW_SLOTS.remove(player.getUUID());

        try {
            return reset(source);
        } finally {
            if (oldSlot != null) {
                PREVIEW_SLOTS.put(player.getUUID(), oldSlot);
            }
        }
    }

    /**
     * Seat movement is explicit: /tc visual seat <1|2|3> <direction>.
     * Selecting a seat slot is done automatically by the command itself.
     */
    private static int seatDirection(
            CommandSourceStack source,
            int slot,
            Direction direction)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        PREVIEW_SLOTS.put(player.getUUID(), clampSlot(slot));
        return direction(source, direction);
    }

    private static int seatCapture(
            CommandSourceStack source,
            int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        PREVIEW_SLOTS.put(player.getUUID(), clampSlot(slot));
        return capture(source);
    }

    private static int seatReset(
            CommandSourceStack source,
            int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        PREVIEW_SLOTS.put(player.getUUID(), clampSlot(slot));
        return reset(source);
    }

    private static int seatSlotStatus(
            CommandSourceStack source,
            int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        int s = clampSlot(slot);
        PREVIEW_SLOTS.put(player.getUUID(), s);
        showSeatStatus(player, target, s);
        return 1;
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
        PREVIEW_SLOTS.remove(player.getUUID());
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
                Comparator.comparing(
                                RollingStockSeatCalibrationEvents::registryId)
                        .thenComparingInt(Entity::getId));

        Entity current = selectedTarget(player);
        int index = 0;
        if (current != null) {
            for (int i = 0; i < nearby.size(); i++) {
                if (nearby.get(i).getUUID().equals(current.getUUID())) {
                    index = (i + 1) % nearby.size();
                    break;
                }
            }
        }

        Entity target = nearby.get(index);
        setTarget(player, target);
        PREVIEW_SLOTS.remove(player.getUUID());
        showStatus(player, target);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        showStatus(player, target);
        if (PREVIEW_SLOTS.containsKey(player.getUUID())) {
            showSeatStatus(
                    player, target, PREVIEW_SLOTS.get(player.getUUID()));
        }
        return 1;
    }

    private static int bodyMode(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        PREVIEW_SLOTS.remove(player.getUUID());
        player.displayClientMessage(
                Component.literal(
                        "[TC VISUAL] BODY mode. /tc visual up/down moves the train model."),
                false);
        showStatus(player, target);
        return 1;
    }

    private static int selectSeat(CommandSourceStack source, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        int s = clampSlot(slot);

        if (target instanceof LegacyPoweredLocomotive) {
            if (s != 1) {
                source.sendFailure(Component.literal(
                        "[TC SEAT] Current powered locomotives use one driver seat. Select Seat 1."));
                return 0;
            }
        }

        PREVIEW_SLOTS.put(player.getUUID(), s);

        // Make the simple workflow immediate: if the rider is already mounted
        // and this seat has never been set, capture the current riding position
        // automatically as the starting point.
        if (!(target instanceof LegacyPoweredLocomotive)
                && player.getVehicle() == target
                && !RollingStockCalibrationConfig.hasSeat(
                        registryId(target), s)) {
            captureSeatFromCurrentRide(player, target, s, false);
        }

        player.displayClientMessage(
                Component.literal(
                        "[TC VISUAL] SEAT " + s +
                        " mode. Use /tc visual seat " + s + " up/down/front/back/left/right."),
                false);
        showSeatStatus(player, target, s);
        return 1;
    }

    private static int direction(
            CommandSourceStack source,
            Direction direction)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        Integer slot = PREVIEW_SLOTS.get(player.getUUID());

        // BODY mode: only vertical visual model movement is meaningful.
        if (slot == null) {
            if (direction != Direction.UP && direction != Direction.DOWN) {
                source.sendFailure(Component.literal(
                        "[TC VISUAL] Select /tc visual seat 1, 2, or 3 first for front/back/left/right."));
                return 0;
            }

            double delta =
                    direction == Direction.UP ? +0.05D : -0.05D;

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
                                "[TC VISUAL BODY] %s Y %.3f",
                                id, value)),
                        false);
            }

            return 1;
        }

        // SEAT mode.
        if (target instanceof LegacyPoweredLocomotive powered) {
            if (slot != 1) return 0;

            switch (direction) {
                case UP -> powered.tcRemoteAdjustSeatY(player, +0.05D);
                case DOWN -> powered.tcRemoteAdjustSeatY(player, -0.05D);
                case FRONT -> powered.tcRemoteAdjustSeatLong(player, +0.05D);
                case BACK -> powered.tcRemoteAdjustSeatLong(player, -0.05D);
                case LEFT -> powered.tcRemoteAdjustSeatLateral(player, -0.05D);
                case RIGHT -> powered.tcRemoteAdjustSeatLateral(player, +0.05D);
            }
            return 1;
        }

        String id = registryId(target);

        if (!RollingStockCalibrationConfig.hasSeat(id, slot)) {
            /*
             * TC_TRANSPORT_OCCUPANT_SEAT_VISUAL_R1F20D
             *
             * Transport cages have real animal/mob passengers, so do not force
             * the command-running player to mount the car merely to create a
             * starting point. Seed that slot from the cage's real default.
             */
            if (target instanceof traincraft.entity.train.freight.LivestockCar livestock) {
                livestock.tcEnsureCalibrationSeat(slot);
            } else if (target instanceof traincraft.entity.train.freight.MobTransportCar mobTransport) {
                mobTransport.tcEnsureCalibrationSeat(slot);
            } else if (player.getVehicle() == target) {
                captureSeatFromCurrentRide(
                        player, target, slot, false);
            } else {
                source.sendFailure(Component.literal(
                        "[TC SEAT] Mount the selected stock once so Seat " +
                        slot + " has a starting position."));
                return 0;
            }
        }

        double dLong = 0.0D;
        double dY = 0.0D;
        double dSide = 0.0D;

        switch (direction) {
            case UP -> dY = +0.05D;
            case DOWN -> dY = -0.05D;
            case FRONT -> dLong = +0.05D;
            case BACK -> dLong = -0.05D;
            case LEFT -> dSide = -0.05D;
            case RIGHT -> dSide = +0.05D;
        }

        RollingStockCalibrationConfig.adjustSeat(
                id, slot, dLong, dY, dSide);

        showSeatStatus(player, target, slot);
        return 1;
    }

    private static int capture(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        int slot = PREVIEW_SLOTS.getOrDefault(
                player.getUUID(), 1);

        if (target instanceof LegacyPoweredLocomotive powered) {
            if (slot != 1) {
                source.sendFailure(Component.literal(
                        "[TC SEAT] Powered locomotive uses Seat 1."));
                return 0;
            }
            powered.tcRemotePinSeatFromPlayer(player);
            return 1;
        }

        if (player.getVehicle() != target) {
            source.sendFailure(Component.literal(
                    "[TC SEAT] Ride the selected stock first, then use /tc visual capture."));
            return 0;
        }

        captureSeatFromCurrentRide(player, target, slot, true);
        return 1;
    }

    private static void captureSeatFromCurrentRide(
            ServerPlayer player,
            Entity target,
            int slot,
            boolean announce) {

        double yaw = Math.toRadians(facingYaw(target));
        double frontX = Math.sin(yaw);
        double frontZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();

        double longitudinal = dx * frontX + dz * frontZ;
        double side = dx * rightX + dz * rightZ;

        // Seat Y is relative to the VISUALLY shifted model body.
        double modelY =
                RollingStockCalibrationConfig.modelY(
                        registryId(target), 0.0D);
        double y =
                player.getY() - target.getY() - modelY;

        RollingStockCalibrationConfig.setSeat(
                registryId(target),
                slot,
                true,
                longitudinal,
                y,
                side,
                false);

        if (announce) {
            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "[TC SEAT CAPTURED] %s Seat %d  long %.3f  y %.3f  side %.3f",
                            registryId(target), slot,
                            longitudinal, y, side)),
                    false);
        }
    }

    private static int reset(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        Integer slot = PREVIEW_SLOTS.get(player.getUUID());

        if (slot != null) {
            if (target instanceof LegacyPoweredLocomotive powered) {
                powered.tcRemoteClearSeat(player);
            } else {
                RollingStockCalibrationConfig.clearSeat(
                        registryId(target), slot);
            }
            player.displayClientMessage(
                    Component.literal(
                            "[TC VISUAL] Reset Seat " + slot + "."),
                    false);
            return 1;
        }

        if (target instanceof LegacyPoweredLocomotive powered) {
            powered.tcRemoteResetHeight(player);
        } else {
            RollingStockCalibrationConfig.setModelY(
                    registryId(target), 0.0D, false);
        }
        player.displayClientMessage(
                Component.literal("[TC VISUAL] Reset body Y."),
                false);
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
            double value =
                    RollingStockCalibrationConfig.modelY(id, 0.0D);
            RollingStockCalibrationConfig.setModelY(
                    id, value, true);

            player.displayClientMessage(
                    Component.literal(String.format(Locale.ROOT,
                            "[TC VISUAL LOCKED] %s modelY %.3f + Seat 1/2/3 config",
                            id, value)),
                    false);
        }

        showStatus(player, target);
        return 1;
    }

    private static int auto(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PREVIEW_SLOTS.remove(player.getUUID());

        player.displayClientMessage(
                Component.literal(
                        "[TC VISUAL] AUTO/BODY mode. Riders now use normal Seat 1 -> Seat 2 -> Seat 3 passenger order."),
                false);
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

    // Old r1f12d seat aliases.
    private static int adjustSeatLegacy(
            CommandSourceStack source,
            double dLong,
            double dY,
            double dSide)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        int slot = PREVIEW_SLOTS.getOrDefault(
                player.getUUID(), 1);
        PREVIEW_SLOTS.put(player.getUUID(), slot);

        if (target instanceof LegacyPoweredLocomotive powered) {
            if (dLong != 0.0D) {
                powered.tcRemoteAdjustSeatLong(player, dLong);
            }
            if (dY != 0.0D) {
                powered.tcRemoteAdjustSeatY(player, dY);
            }
            if (dSide != 0.0D) {
                powered.tcRemoteAdjustSeatLateral(player, dSide);
            }
            return 1;
        }

        String id = registryId(target);
        if (!RollingStockCalibrationConfig.hasSeat(id, slot)) {
            if (player.getVehicle() == target) {
                captureSeatFromCurrentRide(
                        player, target, slot, false);
            } else {
                source.sendFailure(Component.literal(
                        "[TC SEAT] Mount selected stock first."));
                return 0;
            }
        }

        RollingStockCalibrationConfig.adjustSeat(
                id, slot, dLong, dY, dSide);
        showSeatStatus(player, target, slot);
        return 1;
    }

    private static int resetSeatOnly(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        int slot = PREVIEW_SLOTS.getOrDefault(
                player.getUUID(), 1);

        if (target instanceof LegacyPoweredLocomotive powered) {
            powered.tcRemoteClearSeat(player);
        } else {
            RollingStockCalibrationConfig.clearSeat(
                    registryId(target), slot);
        }
        return 1;
    }

    private static int seatStatus(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = requireTarget(player);
        if (target == null) return 0;

        showSeatStatus(player, target, 1);
        showSeatStatus(player, target, 2);
        showSeatStatus(player, target, 3);
        return 1;
    }

    private static void showStatus(
            ServerPlayer player,
            Entity target) {
        String id = registryId(target);

        double modelY;
        if (target instanceof LegacyPoweredLocomotive powered) {
            modelY = powered.tcCalibrationHeightAdjustment();
        } else {
            modelY =
                    RollingStockCalibrationConfig.modelY(
                            id, 0.0D);
        }

        Integer slot = PREVIEW_SLOTS.get(player.getUUID());
        String mode =
                slot == null ? "BODY" : "SEAT " + slot;

        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC VISUAL TARGET] %s #%d  modelY %.3f  mode=%s  locked=%s  GLOWING",
                        id, target.getId(), modelY, mode,
                        Boolean.toString(
                                RollingStockCalibrationConfig.isLocked(id)))),
                false);
    }

    private static void showSeatStatus(
            ServerPlayer player,
            Entity target,
            int slot) {
        String id = registryId(target);

        boolean enabled;
        double longitudinal;
        double y;
        double side;

        // TC_DEV38_R1F2F_POWERED_SEAT_STATUS
        // Powered locomotives use their synchronized physical Seat 1 backend,
        // not the shared TOML seat table. Report the values that the seat
        // direction commands actually modify so BR E69/KOF/etc. no longer
        // misleadingly display enabled=false / 0 / 0 / 0.
        if (target instanceof LegacyPoweredLocomotive powered && slot == 1) {
            enabled = powered.tcCalibrationHasSeat();
            longitudinal = powered.tcCalibrationSeatLong();
            y = powered.tcCalibrationSeatFootY();
            side = powered.tcCalibrationSeatLateral();
        } else {
            enabled = RollingStockCalibrationConfig.hasSeat(id, slot);
            longitudinal = RollingStockCalibrationConfig.seatLong(
                    id, slot, 0.0D);
            y = RollingStockCalibrationConfig.seatY(
                    id, slot, 0.0D);
            side = RollingStockCalibrationConfig.seatSide(
                    id, slot, 0.0D);
        }

        player.displayClientMessage(
                Component.literal(String.format(Locale.ROOT,
                        "[TC SEAT %d] %s enabled=%s  front/back %.3f  up/down %.3f  left/right %.3f",
                        slot, id,
                        Boolean.toString(enabled),
                        longitudinal, y, side)),
                false);
    }
    public static int previewSeatFor(
            UUID playerUuid,
            int normalSlot) {
        return clampSlot(
                PREVIEW_SLOTS.getOrDefault(
                        playerUuid, normalSlot));
    }

    public static boolean hasPreviewSeat(UUID playerUuid) {
        return PREVIEW_SLOTS.containsKey(playerUuid);
    }

    private static Entity requireTarget(ServerPlayer player) {
        Entity target = selectedTarget(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.literal(
                            "[TC VISUAL] Nothing selected. Use /tc visual select <name> or /tc visual next."),
                    false);
        }
        return target;
    }

    private static Entity nearestById(
            ServerPlayer player,
            String id) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : nearbyRollingStock(player)) {
            if (!id.equals(registryId(entity))) continue;

            double distance =
                    player.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private static List<Entity> nearbyRollingStock(
            ServerPlayer player) {
        return new ArrayList<>(
                player.level().getEntities(
                        player,
                        player.getBoundingBox().inflate(
                                SELECT_RADIUS),
                        RollingStockSeatCalibrationEvents::isRollingStock));
    }

    private static Entity selectedTarget(
            ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)) {
            return null;
        }
        if (!player.getPersistentData().hasUUID(TARGET_KEY)) {
            return null;
        }

        Entity target =
                server.getEntity(
                        player.getPersistentData().getUUID(TARGET_KEY));
        if (target == null || !isRollingStock(target)) {
            player.getPersistentData().remove(TARGET_KEY);
            return null;
        }
        return target;
    }

    private static void setTarget(
            ServerPlayer player,
            Entity target) {
        Entity old = selectedTarget(player);
        if (old != null && old != target) {
            old.setGlowingTag(false);
        }

        player.getPersistentData().putUUID(
                TARGET_KEY, target.getUUID());
        target.setGlowingTag(true);
    }

    private static boolean isRollingStock(Entity entity) {
        if (!(entity instanceof AbstractMinecart)) {
            return false;
        }

        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(
                        entity.getType());

        return key != null
                && Traincraft.MOD_ID.equals(
                        key.getNamespace())
                && !"tracks_builder".equals(key.getPath());
    }

    private static String registryId(Entity entity) {
        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(
                        entity.getType());
        return key == null
                ? "unknown"
                : key.getPath();
    }

    private static String normalize(String raw) {
        String id =
                raw.toLowerCase(Locale.ROOT).trim();

        if (id.startsWith(
                Traincraft.MOD_ID + ":")) {
            id = id.substring(
                    Traincraft.MOD_ID.length() + 1);
        }
        return id;
    }

    private static double facingYaw(Entity vehicle) {
        if (vehicle instanceof LegacyPoweredLocomotive powered) {
            return powered.getRenderTrainFacingYaw(1.0F);
        }
        if (vehicle instanceof SmallSteamLocomotive locomotive) {
            return locomotive.getTrainFacingYaw();
        }
        if (vehicle instanceof SteamTender tender) {
            return tender.getTrainFacingYaw();
        }
        return vehicle.getYRot();
    }

    private static int clampSlot(int slot) {
        return Math.max(1, Math.min(3, slot));
    }

    private static List<String> suggestedIds() {
        return ForgeRegistries.ENTITY_TYPES.getKeys()
                .stream()
                .filter(key ->
                        Traincraft.MOD_ID.equals(
                                key.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(id ->
                        !"tracks_builder".equals(id))
                .filter(
                        RollingStockSeatCalibrationEvents::
                                looksLikeRollingStockId)
                .sorted()
                .toList();
    }

    private static boolean looksLikeRollingStockId(
            String id) {
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