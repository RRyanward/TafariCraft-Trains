package traincraft.event;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.UUID;

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
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.network.RollingStockCalibrationNetwork;

/** Authoritative implementation for DEV53E3H compact seat calibration GUI. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TCTSeatAlignServerCommands {
    private static final double TARGET_RADIUS_SQUARED = 48.0D * 48.0D;
    private static final String TARGET_KEY = RollingStockSeatCalibrationEvents.TARGET_KEY;
    private static final String TEST_KEY = "TCTSeatAlignOneTestEntity";
    private static final String TEST_TAG = "TCT_SEAT_ROLLOUT";
    private static final String TEST_OWNER = "TCTSeatAlignOwner";

    private TCTSeatAlignServerCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tct")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("seat")
                .then(Commands.literal("target")
                    .then(Commands.argument("entity_id", IntegerArgumentType.integer(0))
                        .executes(ctx -> target(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "entity_id")))))
                .then(Commands.literal("preview")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1,3))
                        .executes(ctx -> preview(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("nudge")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1,3))
                        .then(Commands.argument("direction", StringArgumentType.word())
                            .then(Commands.argument("step", DoubleArgumentType.doubleArg(0.01D,0.25D))
                                .executes(ctx -> nudge(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx,"slot"),
                                    StringArgumentType.getString(ctx,"direction"),
                                    DoubleArgumentType.getDouble(ctx,"step")))))))
                .then(Commands.literal("save")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1,3))
                        .executes(ctx -> save(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx,"slot")))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1,3))
                        .executes(ctx -> reset(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx,"slot")))))
                .then(Commands.literal("ride")
                    .then(Commands.argument("entity_id", IntegerArgumentType.integer(0))
                        .executes(ctx -> ride(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "entity_id")))))
                .then(Commands.literal("auto")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player, 0);
                        return 1;
                    }))
                .then(Commands.literal("rollout")
                    .then(Commands.argument("variant", StringArgumentType.word())
                        .executes(ctx -> rollout(ctx.getSource(),
                            StringArgumentType.getString(ctx,"variant"), false)))))
                .then(Commands.literal("rollout_ride")
                    .then(Commands.argument("variant", StringArgumentType.word())
                        .executes(ctx -> rollout(ctx.getSource(),
                            StringArgumentType.getString(ctx,"variant"), true)))));
        // DEV53E3H3B: separate command root avoids Brigadier/client command-tree
        // collision with the locally registered /tct seat align GUI leaf.
        // The old command stays registered for backward compatibility; only
        // Fleet List uses this dedicated server-validated route.
        event.getDispatcher().register(Commands.literal("tctseatrolloutride")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("variant", StringArgumentType.word())
                .executes(ctx -> rollout(ctx.getSource(),
                    StringArgumentType.getString(ctx, "variant"), true))));
        Traincraft.LOGGER.info("[R53E3H3B] Fleet rollout command registered: /tctseatrolloutride <variant>");
    }

    private static boolean isStock(Entity e) {
        if (!(e instanceof AbstractMinecart)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && Traincraft.MOD_ID.equals(id.getNamespace())
                && !"tracks_builder".equals(id.getPath());
    }

    private static Entity getTarget(ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(TARGET_KEY)) return null;
        Entity e = player.serverLevel().getEntity(player.getPersistentData().getUUID(TARGET_KEY));
        if (e == null || !isStock(e) || player.distanceToSqr(e) > TARGET_RADIUS_SQUARED) return null;
        return e;
    }

    private static String id(Entity e) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return key == null ? "unknown" : key.getPath();
    }

    private static void msg(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[TCT SEAT] " + message));
    }

    private static int target(CommandSourceStack source, int entityId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity target = player.serverLevel().getEntity(entityId);
        if (!isStock(target) || player.distanceToSqr(target) > TARGET_RADIUS_SQUARED) {
            source.sendFailure(Component.literal("[TCT SEAT] Select a loaded train within 48 blocks."));
            return 0;
        }
        Entity prior = getTarget(player);
        if (prior != null && prior != target) prior.setGlowingTag(false);
        player.getPersistentData().putUUID(TARGET_KEY, target.getUUID());
        target.setGlowingTag(true);
        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player, 1);
        msg(player, "Selected " + id(target) + " #" + target.getId() + " (Seat 1)");
        return 1;
    }

    /** Move a Creative tester between preexisting nearby stock without
     * spawning, discarding or rewriting any saved train. */
    private static int ride(CommandSourceStack source, int entityId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.literal("[TCT SEAT] Ride Next requires Creative test mode."));
            return 0;
        }
        Entity next = player.serverLevel().getEntity(entityId);
        if (!isStock(next) || player.distanceToSqr(next) > TARGET_RADIUS_SQUARED) {
            source.sendFailure(Component.literal("[TCT SEAT] Next train is not loaded within 48 blocks."));
            return 0;
        }
        Entity previousRide = player.getVehicle();
        if (previousRide != next && !player.startRiding(next, true)) {
            // Normally startRiding keeps the previous vehicle on failure;
            // restore it defensively if another mod interrupted mounting.
            if (previousRide != null && player.getVehicle() != previousRide && previousRide.isAlive()) {
                player.startRiding(previousRide, true);
            }
            source.sendFailure(Component.literal("[TCT SEAT] Boarding failed. Original train kept."));
            return 0;
        }
        Entity previousTarget = getTarget(player);
        if (previousTarget != null && previousTarget != next) previousTarget.setGlowingTag(false);
        player.getPersistentData().putUUID(TARGET_KEY, next.getUUID());
        next.setGlowingTag(true);
        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player, 1);
        msg(player, "Now riding " + id(next) + " #" + next.getId()
                + ". Press Save only for an adjusted seat; Next skips unchanged cars.");
        return 1;
    }

    private static int preview(CommandSourceStack source, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity e = getTarget(player);
        if (e == null) {source.sendFailure(Component.literal("[TCT SEAT] Select Vehicle first."));return 0;}
        if (e instanceof LegacyPoweredLocomotive powered && !powered.isWoodenPassengerTram() && slot != 1) {
            source.sendFailure(Component.literal("[TCT SEAT] Powered locomotive driver seat is Seat 1."));
            return 0;
        }
        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player, slot);
        msg(player,"Previewing " + id(e) + " Seat " + slot);
        return 1;
    }

    private static int nudge(CommandSourceStack source, int slot, String direction, double step)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity e = getTarget(player);
        if (e == null) {source.sendFailure(Component.literal("[TCT SEAT] Select Vehicle first."));return 0;}
        double longitudinal = 0, y = 0, lateral = 0;
        switch (direction.toLowerCase(Locale.ROOT)) {
            case "front" -> longitudinal = step;
            case "back" -> longitudinal = -step;
            case "up" -> y = step;
            case "down" -> y = -step;
            case "left" -> lateral = -step;
            case "right" -> lateral = step;
            default -> {source.sendFailure(Component.literal("[TCT SEAT] Invalid direction."));return 0;}
        }
        if (e instanceof LegacyPoweredLocomotive powered && !powered.isWoodenPassengerTram()) {
            if (slot != 1) {source.sendFailure(Component.literal("[TCT SEAT] This train has one driver seat."));return 0;}
            // Never automatically pin from a distant editor; the player must board or /tct pin.
            if (!powered.tcCalibrationHasSeat() && player.getVehicle() != e) {
                source.sendFailure(Component.literal("[TCT SEAT] Mount the train or /tct pin before adjusting."));
                return 0;
            }
            if (longitudinal != 0) powered.tcRemoteAdjustSeatLong(player,longitudinal);
            if (y != 0) powered.tcRemoteAdjustSeatY(player,y);
            if (lateral != 0) powered.tcRemoteAdjustSeatLateral(player,lateral);
        } else {
            String key = id(e);
            // No editing when slot is unset? Seed only selected slot, never copy another seat.
            RollingStockCalibrationConfig.adjustSeat(key,slot,longitudinal,y,lateral);
            RollingStockCalibrationNetwork.sendToAll(player.getServer());
        }
        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player,slot);
        msg(player,String.format(Locale.ROOT,"%s Seat %d %s %+,.2f",id(e),slot,direction,step));
        return 1;
    }

    private static int save(CommandSourceStack source, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity e = getTarget(player);
        if (e == null) {source.sendFailure(Component.literal("[TCT SEAT] Select Vehicle first."));return 0;}
        if (e instanceof LegacyPoweredLocomotive powered && !powered.isWoodenPassengerTram()) {
            if (slot != 1 || !powered.tcCalibrationHasSeat()) {
                source.sendFailure(Component.literal("[TCT SEAT] Driver Seat 1 must be calibrated before Save."));return 0;
            }
            powered.tcLockCalibrationToConfig(player);
        } else {
            String key = id(e);
            if (!RollingStockCalibrationConfig.hasSeat(key,slot)) {
                source.sendFailure(Component.literal("[TCT SEAT] Seat has no position yet. Use arrows first."));return 0;
            }
            RollingStockCalibrationConfig.setSeat(key,slot,true,
                    RollingStockCalibrationConfig.seatLong(key,slot,0),
                    RollingStockCalibrationConfig.seatY(key,slot,0),
                    RollingStockCalibrationConfig.seatSide(key,slot,0),true);
            RollingStockCalibrationNetwork.sendToAll(player.getServer());
        }
        msg(player,"SAVED " + id(e) + " Seat " + slot + " to live config. Frozen baseline unchanged.");
        return 1;
    }

    private static int reset(CommandSourceStack source, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity e = getTarget(player);
        if (e == null) {source.sendFailure(Component.literal("[TCT SEAT] Select Vehicle first."));return 0;}
        if (e instanceof LegacyPoweredLocomotive powered && !powered.isWoodenPassengerTram()) {
            if (slot != 1) {source.sendFailure(Component.literal("[TCT SEAT] Only Seat 1 available."));return 0;}
            powered.tcRemoteClearSeat(player);
            // Reset is explicitly confirmed in GUI; persist the cleared physical seat.
            powered.tcLockCalibrationToConfig(player);
        } else {
            RollingStockCalibrationConfig.clearSeat(id(e),slot);
            RollingStockCalibrationNetwork.sendToAll(player.getServer());
        }
        msg(player,"RESET only " + id(e) + " Seat " + slot);
        return 1;
    }

    private static RailShape railShape(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) return null;
        return rail.getRailDirection(state,level,pos,null);
    }

    /** Find six connected, horizontal, straight preexisting rails; never places track. */
    private static BlockPos findRolloutRail(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        BlockPos best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos middle = base.offset(dx,dy,dz);
                    RailShape shape = railShape(level,middle);
                    Direction dir;
                    if (shape == RailShape.EAST_WEST) dir = Direction.EAST;
                    else if (shape == RailShape.NORTH_SOUTH) dir = Direction.SOUTH;
                    else continue;
                    boolean allSix = true;
                    for (int t = -2; t < 4; t++) {
                        if (railShape(level,middle.relative(dir,t)) != shape) {allSix=false;break;}
                    }
                    if (!allSix) continue;
                    boolean obstructed = false;
                    // R53E3H2: disambiguate Level#getEntities(Entity, AABB, Predicate)
                    // from the generic EntityTypeTest overload under Forge 1.20.1.
                    for (Entity occupant : level.getEntities((Entity) null,
                            new AABB(middle).inflate(3.0D,2.0D,3.0D),
                            e -> e instanceof AbstractMinecart)) {
                        if (!(occupant.getTags().contains(TEST_TAG)
                                && occupant.getPersistentData().hasUUID(TEST_OWNER)
                                && player.getUUID().equals(occupant.getPersistentData().getUUID(TEST_OWNER))
                                && player.getPersistentData().hasUUID(TEST_KEY)
                                && occupant.getUUID().equals(player.getPersistentData().getUUID(TEST_KEY)))) {
                            obstructed = true;
                            break;
                        }
                    }
                    if (obstructed) continue;
                    double distance = middle.distSqr(base);
                    if (distance < bestD) {best=middle;bestD=distance;}
                }
            }
        }
        return best;
    }

    private static int rollout(CommandSourceStack source, String variant, boolean rideAfterSpawn)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Traincraft.LOGGER.info("[R53E3H3B ROLLOUT REQUEST] player={} variant={} ride={}",
                player.getGameProfile().getName(), variant, rideAfterSpawn);
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.literal("[TCT SEAT] Fleet Rollout requires Creative mode."));return 0;
        }
        String name = variant.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]+") || "tracks_builder".equals(name)) return 0;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(Traincraft.MOD_ID,name));
        if (type == null) {source.sendFailure(Component.literal("[TCT SEAT] Unknown train variant."));return 0;}
        Entity created = type.create(player.serverLevel());
        if (!(created instanceof AbstractMinecart)) {
            source.sendFailure(Component.literal("[TCT SEAT] That is not rolling stock."));return 0;
        }
        BlockPos site = findRolloutRail(player);
        if (site == null) {
            source.sendFailure(Component.literal("[TCT SEAT] Need 6 empty contiguous STRAIGHT rails within 10 blocks. Nothing spawned."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RailShape shape = railShape(level,site);
        created.moveTo(site.getX()+0.5D,site.getY()+0.0625D,site.getZ()+0.5D,
                shape == RailShape.EAST_WEST ? 90.0F : 0.0F,0.0F);
        created.addTag(TEST_TAG);
        created.getPersistentData().putUUID(TEST_OWNER,player.getUUID());
        // Never remove the previous test train until the replacement is
        // successfully created AND, when requested, the player is riding it.
        Entity prior = player.getPersistentData().hasUUID(TEST_KEY)
                ? level.getEntity(player.getPersistentData().getUUID(TEST_KEY)) : null;
        Entity oldTarget = getTarget(player);
        Entity originalRide = player.getVehicle();
        if (!level.addFreshEntity(created)) {
            source.sendFailure(Component.literal("[TCT SEAT] Spawn blocked. Previous test train preserved."));return 0;
        }
        if (rideAfterSpawn && !player.startRiding(created, true)) {
            created.remove(Entity.RemovalReason.DISCARDED);
            if (originalRide != null && player.getVehicle() != originalRide && originalRide.isAlive()) {
                player.startRiding(originalRide, true);
            }
            source.sendFailure(Component.literal("[TCT SEAT] Cannot board this stock; previous ride preserved."));
            return 0;
        }
        if (prior != null && prior != created && prior.getTags().contains(TEST_TAG)
                && prior.getPersistentData().hasUUID(TEST_OWNER)
                && player.getUUID().equals(prior.getPersistentData().getUUID(TEST_OWNER))) {
            prior.remove(Entity.RemovalReason.DISCARDED);
        }
        player.getPersistentData().putUUID(TEST_KEY,created.getUUID());
        if (oldTarget != null && oldTarget != created && !oldTarget.isRemoved()) oldTarget.setGlowingTag(false);
        player.getPersistentData().putUUID(TARGET_KEY,created.getUUID());
        created.setGlowingTag(true);
        RollingStockSeatCalibrationEvents.tcDev38PreviewSeat(player,1);
        msg(player,"Rolled out ONE " + name + " on existing six-rail straight (#" + created.getId() + ")"
                + (rideAfterSpawn ? "; YOU ARE RIDING IT. Save only if the seat needed adjustment." : "."));
        return 1;
    }
}
