package traincraft.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * DEV38 r1g1i TEMPORARY automatic fleet screenshot + single-loco focus harness.
 *
 * Existing r1g batch capture remains available.
 * New focus mode:
 *   /tc_r1g_capture page1_single
 *   /tc_r1g_capture focus <1..N>
 *   /tc_r1g_capture next
 *   /tc_r1g_capture prev
 *   /tc_r1g_capture shot
 *   /tc_r1g_capture auto on|off
 *
 * Focus mode spawns exactly one steam locomotive at a deterministic position
 * and locks the player to a much closer side-on calibration camera.
 *
 * Production train systems are not touched.
 * REMOVE THIS CLASS BEFORE RELEASE.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1gAutoCaptureCommand {
    private static final String TAG_CAPTURE = "tc_r1g_capture";
    private static final String TAG_SHOWROOM = "tc_r1g_fleet";
    private static final String PROTOCOL = "1";

    // Existing r1g1h close fleet capture.
    private static final int BATCH_SIZE = 1;
    private static final double STOCK_SPACING = 10.75D;
    private static final int CAMERA_DISTANCE = 14;
    private static final double CAMERA_Y = 0.85D;
    private static final float CAMERA_PITCH = 0.0F;
    private static final int SETTLE_TICKS = 55;
    private static final int POST_CAPTURE_TICKS = 14;

    // r1g1i single-locomotive calibration camera.
    private static final int SINGLE_CAMERA_DISTANCE = 14;
    private static final double SINGLE_CAMERA_Y = 0.85D;
    private static final float SINGLE_CAMERA_PITCH = 0.0F;
    private static final int FOCUS_SETTLE_TICKS = 45;

    private static final Map<UUID, CaptureSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, FocusSession> FOCUS_SESSIONS = new HashMap<>();

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Traincraft.MOD_ID, "r1g_capture"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    static {
        CHANNEL.registerMessage(
                0,
                CapturePacket.class,
                CapturePacket::encode,
                CapturePacket::decode,
                CapturePacket::handle);
    }

    private Dev38R1gAutoCaptureCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_r1g_capture")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("page1")
                                .executes(ctx -> start(ctx.getSource(), CaptureMode.PAGE1)))
                        .then(Commands.literal("all")
                                .executes(ctx -> start(ctx.getSource(), CaptureMode.ALL)))
                        .then(Commands.literal("page1_single")
                                .executes(ctx -> startFocus(ctx.getSource())))
                        .then(Commands.literal("focus")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1, 999))
                                        .executes(ctx -> focusIndex(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "number")))))
                        .then(Commands.literal("next")
                                .executes(ctx -> focusStep(ctx.getSource(), 1)))
                        .then(Commands.literal("prev")
                                .executes(ctx -> focusStep(ctx.getSource(), -1)))
                        .then(Commands.literal("shot")
                                .executes(ctx -> focusShot(ctx.getSource())))
                        .then(Commands.literal("auto")
                                .then(Commands.literal("on")
                                        .executes(ctx -> focusAuto(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> focusAuto(ctx.getSource(), false))))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource()))));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();

        if (!SESSIONS.isEmpty()) {
            Iterator<Map.Entry<UUID, CaptureSession>> it = SESSIONS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, CaptureSession> entry = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

                if (player == null) {
                    it.remove();
                    continue;
                }

                CaptureSession session = entry.getValue();
                if (tickSession(player, session)) {
                    it.remove();
                }
            }
        }

        if (!FOCUS_SESSIONS.isEmpty()) {
            Iterator<Map.Entry<UUID, FocusSession>> it =
                    FOCUS_SESSIONS.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, FocusSession> entry = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

                if (player == null) {
                    it.remove();
                    continue;
                }

                tickFocus(player, entry.getValue());
            }
        }
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC r1g CAPTURE] automatic wheel screenshot harness (TEMP DEBUG)");
        msg(source, "/tc_r1g_capture page1 - automatic ONE-steam-loco-at-a-time screenshots.");
        msg(source, "/tc_r1g_capture page1_single - one steam loco at close calibration camera.");
        msg(source, "/tc_r1g_capture focus <n> - jump to exact Page-1 steam slot.");
        msg(source, "/tc_r1g_capture next | prev - move one locomotive.");
        msg(source, "/tc_r1g_capture shot - save current focused locomotive PNG.");
        msg(source, "/tc_r1g_capture auto on|off - auto-shot after focus changes.");
        msg(source, "/tc_r1g_capture all - automatic ENTIRE FLEET, ONE stock per screenshot.");
        msg(source, "/tc_r1g_capture status - current batch/focus.");
        msg(source, "/tc_r1g_capture stop - restore original position and clear debug stock.");
        msg(source, "PNG files are written automatically to run\\screenshots.");
        return 1;
    }

    private static int start(CommandSourceStack source, CaptureMode mode)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();

        FocusSession focus = FOCUS_SESSIONS.remove(player.getUUID());
        if (focus != null) {
            finishFocus(player, focus, true);
        }

        CaptureSession prior = SESSIONS.remove(player.getUUID());
        if (prior != null) {
            finish(player, prior, true);
        }

        ServerLevel level = player.serverLevel();
        Vec3 originalPos = player.position();
        float originalYaw = player.getYRot();
        float originalPitch = player.getXRot();
        boolean originalNoGravity = player.isNoGravity();

        Direction view = player.getDirection();
        if (!view.getAxis().isHorizontal()) view = Direction.NORTH;

        BlockPos anchor = player.blockPosition();
        List<CaptureBatch> batches = makeBatches(level, mode);

        if (batches.isEmpty()) {
            source.sendFailure(Component.literal("[TC r1g CAPTURE] No matching rolling stock found."));
            return 0;
        }

        clearDebugStock(level, anchor, 190.0D);

        CaptureSession session = new CaptureSession(
                player.getUUID(),
                originalPos,
                originalYaw,
                originalPitch,
                originalNoGravity,
                anchor,
                view,
                batches);

        SESSIONS.put(player.getUUID(), session);
        prepareBatch(player, session);

        msg(source, String.format(Locale.ROOT,
                "[TC r1g CAPTURE] START %s: %d automatic screenshot batch(es).",
                mode == CaptureMode.PAGE1 ? "PAGE 1 / STEAM" : "ALL FLEET",
                batches.size()));
        msg(source, "Do not move or press F1/F5 while automatic capture is running.");
        return batches.size();
    }

    private static int startFocus(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();

        CaptureSession capture = SESSIONS.remove(player.getUUID());
        if (capture != null) {
            finish(player, capture, true);
        }

        FocusSession prior = FOCUS_SESSIONS.remove(player.getUUID());
        if (prior != null) {
            finishFocus(player, prior, true);
        }

        ServerLevel level = player.serverLevel();
        List<Entry> steam = discover(level).get(Group.STEAM);
        if (steam == null || steam.isEmpty()) {
            source.sendFailure(Component.literal("[TC r1g CAPTURE] No steam locomotives found."));
            return 0;
        }

        Direction view = player.getDirection();
        if (!view.getAxis().isHorizontal()) view = Direction.NORTH;

        FocusSession focus = new FocusSession(
                player.getUUID(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.isNoGravity(),
                player.blockPosition(),
                view,
                new ArrayList<>(steam));

        FOCUS_SESSIONS.put(player.getUUID(), focus);
        spawnFocus(player, focus);

        msg(source, String.format(Locale.ROOT,
                "[TC r1g FOCUS] START Page 1 single-loco mode: %d steam locomotives.",
                steam.size()));
        msg(source, "[TC r1g FOCUS] Use next/prev/focus <n>/shot/auto on|off.");
        return steam.size();
    }

    private static int focusIndex(CommandSourceStack source, int oneBased) {
        ServerPlayer player = playerOrFail(source);
        if (player == null) return 0;

        FocusSession focus = FOCUS_SESSIONS.get(player.getUUID());
        if (focus == null) {
            source.sendFailure(Component.literal(
                    "[TC r1g FOCUS] Start with /tc_r1g_capture page1_single."));
            return 0;
        }

        if (oneBased < 1 || oneBased > focus.entries.size()) {
            source.sendFailure(Component.literal(String.format(
                    Locale.ROOT,
                    "[TC r1g FOCUS] Valid range is 1..%d.",
                    focus.entries.size())));
            return 0;
        }

        focus.index = oneBased - 1;
        spawnFocus(player, focus);
        return oneBased;
    }

    private static int focusStep(CommandSourceStack source, int direction) {
        ServerPlayer player = playerOrFail(source);
        if (player == null) return 0;

        FocusSession focus = FOCUS_SESSIONS.get(player.getUUID());
        if (focus == null) {
            source.sendFailure(Component.literal(
                    "[TC r1g FOCUS] Start with /tc_r1g_capture page1_single."));
            return 0;
        }

        int size = focus.entries.size();
        focus.index = Math.floorMod(focus.index + direction, size);
        spawnFocus(player, focus);
        return focus.index + 1;
    }

    private static int focusShot(CommandSourceStack source) {
        ServerPlayer player = playerOrFail(source);
        if (player == null) return 0;

        FocusSession focus = FOCUS_SESSIONS.get(player.getUUID());
        if (focus == null) {
            source.sendFailure(Component.literal(
                    "[TC r1g FOCUS] Start with /tc_r1g_capture page1_single."));
            return 0;
        }

        sendFocusShot(player, focus);
        msg(source, "[TC r1g FOCUS] screenshot requested: " + focusFileName(focus));
        return focus.index + 1;
    }

    private static int focusAuto(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = playerOrFail(source);
        if (player == null) return 0;

        FocusSession focus = FOCUS_SESSIONS.get(player.getUUID());
        if (focus == null) {
            source.sendFailure(Component.literal(
                    "[TC r1g FOCUS] Start with /tc_r1g_capture page1_single."));
            return 0;
        }

        focus.autoShot = enabled;
        focus.pendingShotTicks = enabled ? FOCUS_SETTLE_TICKS : -1;

        msg(source, "[TC r1g FOCUS] auto-shot=" + enabled);
        if (enabled) {
            msg(source, "[TC r1g FOCUS] current locomotive will be captured after settling.");
        }
        return enabled ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TC r1g CAPTURE] Player-only command."));
            return 0;
        }

        FocusSession focus = FOCUS_SESSIONS.get(player.getUUID());
        if (focus != null) {
            Entry entry = focus.entries.get(focus.index);
            msg(source, String.format(Locale.ROOT,
                    "[TC r1g FOCUS] %d/%d id=%s auto=%s pendingTicks=%d file=%s",
                    focus.index + 1,
                    focus.entries.size(),
                    entry.id.getPath(),
                    Boolean.toString(focus.autoShot),
                    focus.pendingShotTicks,
                    focusFileName(focus)));
            return focus.index + 1;
        }

        CaptureSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            msg(source, "[TC r1g CAPTURE] idle.");
            return 0;
        }

        CaptureBatch batch = session.batches.get(session.batchIndex);
        msg(source, String.format(Locale.ROOT,
                "[TC r1g CAPTURE] batch %d/%d file=%s stage=%s ticks=%d",
                session.batchIndex + 1,
                session.batches.size(),
                batch.fileName,
                session.stage.name().toLowerCase(Locale.ROOT),
                session.ticks));
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TC r1g CAPTURE] Player-only command."));
            return 0;
        }

        boolean stopped = false;

        FocusSession focus = FOCUS_SESSIONS.remove(player.getUUID());
        if (focus != null) {
            finishFocus(player, focus, true);
            stopped = true;
        }

        CaptureSession capture = SESSIONS.remove(player.getUUID());
        if (capture != null) {
            finish(player, capture, true);
            stopped = true;
        }

        if (!stopped) {
            msg(source, "[TC r1g CAPTURE] nothing to stop.");
            return 0;
        }

        msg(source, "[TC r1g CAPTURE] stopped; original position restored.");
        return 1;
    }

    private static ServerPlayer playerOrFail(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("[TC r1g CAPTURE] Player-only command."));
        return null;
    }

    private static boolean tickSession(ServerPlayer player, CaptureSession session) {
        if (session.ticks > 0) {
            session.ticks--;

            if (session.stage == Stage.SETTLING && session.ticks == 8) {
                applyCamera(player, session);
            }
            return false;
        }

        if (session.stage == Stage.SETTLING) {
            CaptureBatch batch = session.batches.get(session.batchIndex);

            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CapturePacket(batch.fileName));

            msg(player.createCommandSourceStack(), String.format(Locale.ROOT,
                    "[TC r1g CAPTURE] shot %d/%d -> %s",
                    session.batchIndex + 1,
                    session.batches.size(),
                    batch.fileName));

            session.stage = Stage.POST_CAPTURE;
            session.ticks = POST_CAPTURE_TICKS;
            return false;
        }

        if (session.batchIndex + 1 < session.batches.size()) {
            session.batchIndex++;
            prepareBatch(player, session);
            return false;
        }

        finish(player, session, false);
        msg(player.createCommandSourceStack(), String.format(Locale.ROOT,
                "[TC r1g CAPTURE] COMPLETE: %d PNG(s) saved under run\\screenshots.",
                session.batches.size()));
        return true;
    }

    private static void tickFocus(ServerPlayer player, FocusSession focus) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        if (focus.pendingShotTicks < 0) return;

        if (focus.pendingShotTicks > 0) {
            focus.pendingShotTicks--;

            if (focus.pendingShotTicks == 8) {
                applyFocusCamera(player, focus);
            }
            return;
        }

        sendFocusShot(player, focus);
        focus.pendingShotTicks = -1;
    }

    private static void prepareBatch(ServerPlayer player, CaptureSession session) {
        ServerLevel level = player.serverLevel();
        clearDebugStock(level, session.anchor, 190.0D);

        CaptureBatch batch = session.batches.get(session.batchIndex);

        Direction view = session.view;
        Direction right = view.getClockWise();

        BlockPos centerBlock = session.anchor.relative(view, CAMERA_DISTANCE);
        double centerX = centerBlock.getX() + 0.5D;
        double centerZ = centerBlock.getZ() + 0.5D;
        double stockY = session.anchor.getY() + 0.05D;

        int spawned = 0;
        List<String> names = new ArrayList<>();

        for (int i = 0; i < batch.entries.size(); i++) {
            Entry entry = batch.entries.get(i);
            double centeredIndex = i - (batch.entries.size() - 1) / 2.0D;
            double lateral = centeredIndex * STOCK_SPACING;

            double x = centerX + right.getStepX() * lateral;
            double z = centerZ + right.getStepZ() * lateral;

            Entity entity = entry.type.create(level);
            if (entity == null) continue;

            float stockYaw = right.toYRot();
            entity.moveTo(x, stockY, z, stockYaw, 0.0F);
            entity.setYRot(stockYaw);
            entity.setXRot(0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.addTag(TAG_CAPTURE);
            entity.setCustomName(Component.literal(entry.id.getPath()));
            entity.setCustomNameVisible(false);

            if (level.addFreshEntity(entity)) {
                spawned++;
                names.add(entry.id.getPath());
            }
        }

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        applyCamera(player, session);

        session.stage = Stage.SETTLING;
        session.ticks = SETTLE_TICKS;

        msg(player.createCommandSourceStack(), String.format(Locale.ROOT,
                "[TC r1g CAPTURE] preparing %s (%d stock): %s",
                batch.fileName,
                spawned,
                String.join(", ", names)));
    }

    private static void spawnFocus(ServerPlayer player, FocusSession focus) {
        ServerLevel level = player.serverLevel();
        clearDebugStock(level, focus.anchor, 190.0D);

        Entry entry = focus.entries.get(focus.index);
        Direction right = focus.view.getClockWise();

        BlockPos stockBlock = focus.anchor.relative(focus.view, SINGLE_CAMERA_DISTANCE);
        double stockX = stockBlock.getX() + 0.5D;
        double stockY = focus.anchor.getY() + 0.05D;
        double stockZ = stockBlock.getZ() + 0.5D;

        Entity entity = entry.type.create(level);
        if (entity == null) {
            player.createCommandSourceStack().sendFailure(Component.literal(
                    "[TC r1g FOCUS] Entity create returned null for " + entry.id));
            return;
        }

        float stockYaw = right.toYRot();
        entity.moveTo(stockX, stockY, stockZ, stockYaw, 0.0F);
        entity.setYRot(stockYaw);
        entity.setXRot(0.0F);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.addTag(TAG_CAPTURE);
        entity.setCustomName(Component.literal(entry.id.getPath()));
        entity.setCustomNameVisible(false);

        if (!level.addFreshEntity(entity)) {
            player.createCommandSourceStack().sendFailure(Component.literal(
                    "[TC r1g FOCUS] Failed to add " + entry.id));
            return;
        }

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        applyFocusCamera(player, focus);

        focus.pendingShotTicks = focus.autoShot ? FOCUS_SETTLE_TICKS : -1;

        msg(player.createCommandSourceStack(), String.format(Locale.ROOT,
                "[TC r1g FOCUS] %d/%d %s auto=%s",
                focus.index + 1,
                focus.entries.size(),
                entry.id.getPath(),
                Boolean.toString(focus.autoShot)));
    }

    private static void applyCamera(ServerPlayer player, CaptureSession session) {
        double x = session.anchor.getX() + 0.5D;
        double y = session.anchor.getY() + CAMERA_Y;
        double z = session.anchor.getZ() + 0.5D;

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.connection.teleport(
                x,
                y,
                z,
                session.view.toYRot(),
                CAMERA_PITCH);
    }

    private static void applyFocusCamera(ServerPlayer player, FocusSession focus) {
        double x = focus.anchor.getX() + 0.5D;
        double y = focus.anchor.getY() + SINGLE_CAMERA_Y;
        double z = focus.anchor.getZ() + 0.5D;

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.connection.teleport(
                x,
                y,
                z,
                focus.view.toYRot(),
                SINGLE_CAMERA_PITCH);
    }

    private static void sendFocusShot(ServerPlayer player, FocusSession focus) {
        applyFocusCamera(player, focus);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CapturePacket(focusFileName(focus)));
    }

    private static String focusFileName(FocusSession focus) {
        Entry entry = focus.entries.get(focus.index);
        return String.format(
                Locale.ROOT,
                "tc-r1g-focus-page1-%02d-of-%02d-%s.png",
                focus.index + 1,
                focus.entries.size(),
                entry.id.getPath());
    }

    private static void finish(ServerPlayer player, CaptureSession session, boolean aborted) {
        ServerLevel level = player.serverLevel();
        clearDebugStock(level, session.anchor, 190.0D);

        player.setNoGravity(session.originalNoGravity);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.connection.teleport(
                session.originalPos.x,
                session.originalPos.y,
                session.originalPos.z,
                session.originalYaw,
                session.originalPitch);

        if (aborted) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CapturePacket(""));
        }
    }

    private static void finishFocus(ServerPlayer player, FocusSession focus, boolean cancelClient) {
        clearDebugStock(player.serverLevel(), focus.anchor, 190.0D);

        player.setNoGravity(focus.originalNoGravity);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.connection.teleport(
                focus.originalPos.x,
                focus.originalPos.y,
                focus.originalPos.z,
                focus.originalYaw,
                focus.originalPitch);

        if (cancelClient) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CapturePacket(""));
        }
    }

    private static List<CaptureBatch> makeBatches(ServerLevel level, CaptureMode mode) {
        Map<Group, List<Entry>> groups = discover(level);
        List<CaptureBatch> result = new ArrayList<>();

        if (mode == CaptureMode.PAGE1) {
            appendBatches(result, "page1-steam", groups.get(Group.STEAM));
            return result;
        }

        appendBatches(result, "page1-steam", groups.get(Group.STEAM));
        appendBatches(result, "page2-tenders", groups.get(Group.TENDERS));
        appendBatches(result, "page3-powered", groups.get(Group.POWERED));
        appendBatches(result, "page4-cars", groups.get(Group.CARS));
        return result;
    }

    private static void appendBatches(
            List<CaptureBatch> output,
            String label,
            List<Entry> entries) {

        if (entries == null || entries.isEmpty()) return;

        int total = (entries.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        for (int start = 0, index = 1; start < entries.size(); start += BATCH_SIZE, index++) {
            int end = Math.min(entries.size(), start + BATCH_SIZE);
            List<Entry> slice = new ArrayList<>(entries.subList(start, end));
            String fileName = String.format(
                    Locale.ROOT,
                    "tc-r1g-%s-%02d-of-%02d-%s.png",
                    label,
                    index,
                    total,
                    slice.get(0).id.getPath());
            output.add(new CaptureBatch(fileName, slice));
        }
    }

    private static Map<Group, List<Entry>> discover(ServerLevel level) {
        Map<Group, List<Entry>> groups = new EnumMap<>(Group.class);
        for (Group group : Group.values()) groups.put(group, new ArrayList<>());

        for (ResourceLocation id : ForgeRegistries.ENTITY_TYPES.getKeys()) {
            if (!Traincraft.MOD_ID.equals(id.getNamespace())) continue;

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) continue;

            Entity probe;
            try {
                probe = type.create(level);
            } catch (RuntimeException ex) {
                continue;
            }

            if (probe == null) continue;
            boolean rolling = isRollingStock(id, probe);
            probe.discard();

            if (!rolling) continue;
            groups.get(groupFor(id)).add(new Entry(id, type));
        }

        Comparator<Entry> byId = Comparator.comparing(e -> e.id.getPath());
        for (List<Entry> entries : groups.values()) {
            entries.sort(byId);
        }
        return groups;
    }

    private static boolean isRollingStock(ResourceLocation id, Entity probe) {
        String path = id.getPath();
        String className = probe.getClass().getName();

        if (className.startsWith("traincraft.entity.train.")) return true;

        return path.startsWith("locomotive_")
                || path.startsWith("steam_tender")
                || path.contains("_tender")
                || path.endsWith("_car")
                || path.contains("passenger")
                || "freight_cart".equals(path)
                || "caboose".equals(path)
                || "tracks_builder".equals(path)
                || path.contains("livestock")
                || path.contains("transport_car");
    }

    private static Group groupFor(ResourceLocation id) {
        String path = id.getPath();

        if (path.startsWith("locomotive_steam_")) return Group.STEAM;
        if (path.contains("tender")) return Group.TENDERS;

        if (path.startsWith("locomotive_diesel_")
                || path.startsWith("locomotive_electric_")
                || "tracks_builder".equals(path)) {
            return Group.POWERED;
        }

        return Group.CARS;
    }

    private static int clearDebugStock(ServerLevel level, BlockPos anchor, double radius) {
        Vec3 center = Vec3.atCenterOf(anchor);
        AABB area = new AABB(
                center.x - radius, center.y - 64.0D, center.z - radius,
                center.x + radius, center.y + 64.0D, center.z + radius);

        List<Entity> entities = level.getEntities(
                (Entity) null,
                area,
                entity -> entity.getTags().contains(TAG_CAPTURE)
                        || entity.getTags().contains(TAG_SHOWROOM));

        for (Entity entity : entities) {
            entity.discard();
        }
        return entities.size();
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private enum CaptureMode {
        PAGE1,
        ALL
    }

    private enum Group {
        STEAM,
        TENDERS,
        POWERED,
        CARS
    }

    private enum Stage {
        SETTLING,
        POST_CAPTURE
    }

    private record Entry(ResourceLocation id, EntityType<?> type) {}

    private record CaptureBatch(String fileName, List<Entry> entries) {}

    private static final class CaptureSession {
        final UUID playerId;
        final Vec3 originalPos;
        final float originalYaw;
        final float originalPitch;
        final boolean originalNoGravity;
        final BlockPos anchor;
        final Direction view;
        final List<CaptureBatch> batches;

        int batchIndex;
        int ticks;
        Stage stage;

        CaptureSession(
                UUID playerId,
                Vec3 originalPos,
                float originalYaw,
                float originalPitch,
                boolean originalNoGravity,
                BlockPos anchor,
                Direction view,
                List<CaptureBatch> batches) {
            this.playerId = playerId;
            this.originalPos = originalPos;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.originalNoGravity = originalNoGravity;
            this.anchor = anchor;
            this.view = view;
            this.batches = batches;
        }
    }

    private static final class FocusSession {
        final UUID playerId;
        final Vec3 originalPos;
        final float originalYaw;
        final float originalPitch;
        final boolean originalNoGravity;
        final BlockPos anchor;
        final Direction view;
        final List<Entry> entries;

        int index;
        boolean autoShot;
        int pendingShotTicks = -1;

        FocusSession(
                UUID playerId,
                Vec3 originalPos,
                float originalYaw,
                float originalPitch,
                boolean originalNoGravity,
                BlockPos anchor,
                Direction view,
                List<Entry> entries) {
            this.playerId = playerId;
            this.originalPos = originalPos;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.originalNoGravity = originalNoGravity;
            this.anchor = anchor;
            this.view = view;
            this.entries = entries;
        }
    }

    public record CapturePacket(String fileName) {
        static void encode(CapturePacket packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.fileName, 160);
        }

        static CapturePacket decode(FriendlyByteBuf buffer) {
            return new CapturePacket(buffer.readUtf(160));
        }

        static void handle(
                CapturePacket packet,
                Supplier<NetworkEvent.Context> contextSupplier) {

            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> Dev38R1gAutoCaptureClient.queue(packet.fileName)));
            context.setPacketHandled(true);
        }
    }
}
