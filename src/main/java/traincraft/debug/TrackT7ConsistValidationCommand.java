package traincraft.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchSwitchRailBlock;
import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.tender.SteamTender;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Step 9.3g-t7-c1 — real rolling-stock consist validation foundation.
 *
 * This diagnostic does NOT spawn, move, couple, uncouple, refuel, brake, or
 * otherwise control rolling stock. It observes a real player-driven consist.
 *
 * Command:
 *   /tc_t7_consist_trace
 *   /tc_t7_consist_trace stop
 *
 * Start the command while riding (or standing close to) the lead Small Steam
 * Locomotive. The locomotive must already be mutually coupled to a SteamTender.
 * The trace records the whole loaded secondary-coupler chain for up to 60 seconds.
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class TrackT7ConsistValidationCommand {
    private static final int TRACE_TICKS = 20 * 60;
    private static final int DETAIL_INTERVAL_TICKS = 2;
    private static final double LOCOMOTIVE_SEARCH_RADIUS = 24.0D;
    private static final int MAX_CHAIN_VEHICLES = 12;

    private static final Map<UUID, TraceSession> SESSIONS = new HashMap<>();

    private TrackT7ConsistValidationCommand() {
    }

    private static final class TraceSession {
        private final UUID playerId;
        private final UUID locomotiveId;
        private final List<UUID> chainIds;
        private final List<String> chainRoles;
        private final List<PairStats> pairStats = new ArrayList<>();
        private final Map<UUID, EntityStats> entityStats = new LinkedHashMap<>();
        private final List<String> report = new ArrayList<>();
        private final Set<String> allBatchIds = new LinkedHashSet<>();
        private final Map<UUID, String> lastRailKeys = new HashMap<>();
        private final Map<String, Boolean> lastLinkState = new HashMap<>();

        private int elapsed;
        private int samples;
        private int mutualLinkFailureSamples;
        private int missingEntitySamples;
        private int offRailSamples;
        private boolean manualStop;

        private TraceSession(ServerPlayer player,
                             SmallSteamLocomotive locomotive,
                             List<Entity> chain) {
            this.playerId = player.getUUID();
            this.locomotiveId = locomotive.getUUID();

            List<UUID> ids = new ArrayList<>();
            List<String> roles = new ArrayList<>();

            for (int i = 0; i < chain.size(); i++) {
                Entity entity = chain.get(i);
                ids.add(entity.getUUID());
                String role = i == 0 ? "LOCOMOTIVE"
                        : i == 1 ? "TENDER_1"
                        : "CAR_" + i;
                roles.add(role);
                entityStats.put(entity.getUUID(), new EntityStats(role, entity));
            }

            this.chainIds = List.copyOf(ids);
            this.chainRoles = List.copyOf(roles);

            for (int i = 0; i + 1 < chain.size(); i++) {
                pairStats.add(new PairStats(
                        chainRoles.get(i),
                        chainRoles.get(i + 1),
                        chainIds.get(i),
                        chainIds.get(i + 1)));
            }

            report.add("TAFARICRAFT - TRAINS STEP 9.3g-t7-c1");
            report.add("REAL LOCOMOTIVE / CONSIST RUNTIME TRACE");
            report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
            report.add("MOVEMENT_AUTHORITY=PLAYER_DRIVEN_PRODUCTION_ENTITIES");
            report.add("TRACE_TICKS_MAX=" + TRACE_TICKS);
            report.add("DETAIL_INTERVAL_TICKS=" + DETAIL_INTERVAL_TICKS);
            report.add("PLAYER=" + player.getGameProfile().getName());
            report.add("DIMENSION=" + player.serverLevel().dimension().location());
            report.add("START_GAME_TIME=" + player.serverLevel().getGameTime());
            report.add("CHAIN_COUNT=" + chain.size());
            for (int i = 0; i < chain.size(); i++) {
                Entity entity = chain.get(i);
                ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                report.add("CHAIN index=" + i
                        + " role=" + chainRoles.get(i)
                        + " uuid=" + entity.getUUID()
                        + " entityId=" + entity.getId()
                        + " type=" + (type == null ? entity.getClass().getName() : type)
                        + " class=" + entity.getClass().getName());
            }
            report.add("");
            report.add("TRACE:");
        }
    }

    private static final class PairStats {
        private final String leaderRole;
        private final String followerRole;
        private final UUID leaderId;
        private final UUID followerId;

        private int samples;
        private int brokenSamples;
        private double minDistance3d = Double.POSITIVE_INFINITY;
        private double maxDistance3d = Double.NEGATIVE_INFINITY;
        private double minHorizontalDistance = Double.POSITIVE_INFINITY;
        private double maxHorizontalDistance = Double.NEGATIVE_INFINITY;
        private double maxSpeedDeltaKmh;

        private PairStats(String leaderRole,
                          String followerRole,
                          UUID leaderId,
                          UUID followerId) {
            this.leaderRole = leaderRole;
            this.followerRole = followerRole;
            this.leaderId = leaderId;
            this.followerId = followerId;
        }
    }

    private static final class EntityStats {
        private final String role;
        private final UUID entityId;
        private final String type;
        private final Set<String> batchIds = new LinkedHashSet<>();

        private int samples;
        private int offRailSamples;
        private double maxSpeedKmh;

        private EntityStats(String role, Entity entity) {
            this.role = role;
            this.entityId = entity.getUUID();
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            this.type = key == null ? entity.getClass().getName() : key.toString();
        }
    }

    private static final class RailSample {
        private final BlockPos pos;
        private final String key;
        private final String detail;
        private final String batchId;

        private RailSample(BlockPos pos,
                           String key,
                           String detail,
                           String batchId) {
            this.pos = pos;
            this.key = key;
            this.detail = detail;
            this.batchId = batchId;
        }
    }

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t7_consist_trace")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> start(context.getSource()))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource()))));
    }

    private static int start(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            if (SESSIONS.containsKey(player.getUUID())) {
                source.sendFailure(Component.literal(
                        "A t7 consist trace is already active. Use /tc_t7_consist_trace stop first."));
                return 0;
            }

            SmallSteamLocomotive locomotive = findLeadLocomotive(player);
            if (locomotive == null) {
                source.sendFailure(Component.literal(
                        "No Small Steam Locomotive found. Ride it or stand within "
                                + (int) LOCOMOTIVE_SEARCH_RADIUS + " blocks."));
                return 0;
            }

            List<Entity> chain = collectLoadedChain(locomotive);
            if (chain.size() < 2 || !(chain.get(1) instanceof SteamTender)) {
                source.sendFailure(Component.literal(
                        "The lead locomotive must already be mutually coupled to a loaded SteamTender."));
                return 0;
            }

            TraceSession session = new TraceSession(player, locomotive, chain);
            SESSIONS.put(player.getUUID(), session);

            source.sendSuccess(() -> Component.literal(
                    "t7 real-consist trace started for 60 seconds. "
                            + "Drive normally now. Chain vehicles=" + chain.size()
                            + ". Use /tc_t7_consist_trace stop to finish early."), false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "Could not start t7 consist trace: " + ex.getMessage()));
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TraceSession session = SESSIONS.remove(player.getUUID());
            if (session == null) {
                source.sendFailure(Component.literal("No t7 consist trace is active."));
                return 0;
            }

            session.manualStop = true;
            finish(player, session);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal(
                    "Could not stop t7 consist trace: " + ex.getMessage()));
            return 0;
        }
    }

    private static SmallSteamLocomotive findLeadLocomotive(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof SmallSteamLocomotive locomotive) {
            return locomotive;
        }

        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(
                LOCOMOTIVE_SEARCH_RADIUS,
                8.0D,
                LOCOMOTIVE_SEARCH_RADIUS);

        SmallSteamLocomotive best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (SmallSteamLocomotive locomotive
                : level.getEntitiesOfClass(SmallSteamLocomotive.class, box)) {
            double distance = player.distanceToSqr(locomotive);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = locomotive;
            }
        }

        return best;
    }

    private static List<Entity> collectLoadedChain(SmallSteamLocomotive locomotive) {
        List<Entity> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        Entity current = locomotive;
        chain.add(current);
        visited.add(current.getUUID());

        for (int hop = 1; hop < MAX_CHAIN_VEHICLES; hop++) {
            if (!(current instanceof CoupleableRollingStock stock)) {
                break;
            }

            Entity next;
            if (current instanceof SmallSteamLocomotive) {
                next = CouplingManager.resolve(current, stock);
            } else {
                next = CouplingManager.resolveSecondary(current, stock);
            }

            if (next == null || !visited.add(next.getUUID())) {
                break;
            }
            if (!(next instanceof CoupleableRollingStock nextStock)) {
                break;
            }
            if (!stock.isCoupledTo(next.getUUID())
                    || !nextStock.isCoupledTo(current.getUUID())) {
                break;
            }

            chain.add(next);
            current = next;
        }

        return chain;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        TraceSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        sample(player, session);
        session.elapsed++;

        if (session.elapsed >= TRACE_TICKS) {
            SESSIONS.remove(player.getUUID());
            finish(player, session);
        }
    }

    private static void sample(ServerPlayer player, TraceSession session) {
        ServerLevel level = player.serverLevel();
        session.samples++;

        List<Entity> entities = new ArrayList<>();
        for (UUID id : session.chainIds) {
            Entity entity = level.getEntity(id);
            entities.add(entity);
            if (entity == null || entity.isRemoved()) {
                session.missingEntitySamples++;
            }
        }

        if ((session.elapsed % DETAIL_INTERVAL_TICKS) == 0) {
            session.report.add("TICK=" + session.elapsed
                    + " gameTime=" + level.getGameTime());
        }

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            UUID expectedId = session.chainIds.get(i);
            EntityStats stats = session.entityStats.get(expectedId);

            if (entity == null || entity.isRemoved()) {
                if ((session.elapsed % DETAIL_INTERVAL_TICKS) == 0) {
                    session.report.add("  ENTITY role=" + session.chainRoles.get(i)
                            + " uuid=" + expectedId
                            + " MISSING_OR_REMOVED");
                }
                continue;
            }

            Vec3 motion = entity.getDeltaMovement();
            double horizontalSpeed = Math.hypot(motion.x, motion.z);
            double speedKmh = horizontalSpeed * 72.0D;

            stats.samples++;
            stats.maxSpeedKmh = Math.max(stats.maxSpeedKmh, speedKmh);

            RailSample rail = nearestRail(level, entity);
            if (rail == null) {
                stats.offRailSamples++;
                session.offRailSamples++;
            } else if (rail.batchId != null) {
                stats.batchIds.add(rail.batchId);
                session.allBatchIds.add(rail.batchId);
            }

            String railKey = rail == null ? "NONE" : rail.key;
            String previousKey = session.lastRailKeys.put(expectedId, railKey);
            if (previousKey == null || !previousKey.equals(railKey)) {
                session.report.add("  ROUTE_EVENT tick=" + session.elapsed
                        + " role=" + stats.role
                        + " from=" + (previousKey == null ? "START" : previousKey)
                        + " to=" + railKey
                        + (rail == null ? "" : " detail={" + rail.detail + "}"));
            }

            if ((session.elapsed % DETAIL_INTERVAL_TICKS) == 0) {
                session.report.add(String.format(Locale.ROOT,
                        "  ENTITY role=%s id=%d uuid=%s pos=(%.6f,%.6f,%.6f)"
                                + " motion=(%.6f,%.6f,%.6f) speedKmh=%.3f yaw=%.3f rail=%s",
                        stats.role,
                        entity.getId(),
                        entity.getUUID(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        motion.x,
                        motion.y,
                        motion.z,
                        speedKmh,
                        entity.getYRot(),
                        rail == null ? "NONE" : rail.detail));
            }
        }

        for (int i = 0; i < session.pairStats.size(); i++) {
            PairStats stats = session.pairStats.get(i);
            Entity leader = entities.get(i);
            Entity follower = entities.get(i + 1);

            if (leader == null || follower == null
                    || leader.isRemoved() || follower.isRemoved()) {
                stats.brokenSamples++;
                continue;
            }

            stats.samples++;

            boolean mutual = leader instanceof CoupleableRollingStock leaderStock
                    && follower instanceof CoupleableRollingStock followerStock
                    && leaderStock.isCoupledTo(follower.getUUID())
                    && followerStock.isCoupledTo(leader.getUUID());

            if (!mutual) {
                stats.brokenSamples++;
                session.mutualLinkFailureSamples++;
            }

            String linkKey = stats.leaderId + "->" + stats.followerId;
            Boolean oldLink = session.lastLinkState.put(linkKey, mutual);
            if (oldLink == null || oldLink.booleanValue() != mutual) {
                session.report.add("  LINK_EVENT tick=" + session.elapsed
                        + " pair=" + stats.leaderRole + "->" + stats.followerRole
                        + " mutual=" + mutual);
            }

            double dx = follower.getX() - leader.getX();
            double dy = follower.getY() - leader.getY();
            double dz = follower.getZ() - leader.getZ();
            double horizontalDistance = Math.hypot(dx, dz);
            double distance3d = Math.sqrt(dx * dx + dy * dy + dz * dz);

            stats.minDistance3d = Math.min(stats.minDistance3d, distance3d);
            stats.maxDistance3d = Math.max(stats.maxDistance3d, distance3d);
            stats.minHorizontalDistance = Math.min(stats.minHorizontalDistance, horizontalDistance);
            stats.maxHorizontalDistance = Math.max(stats.maxHorizontalDistance, horizontalDistance);

            double leaderKmh = Math.hypot(
                    leader.getDeltaMovement().x,
                    leader.getDeltaMovement().z) * 72.0D;
            double followerKmh = Math.hypot(
                    follower.getDeltaMovement().x,
                    follower.getDeltaMovement().z) * 72.0D;
            double speedDelta = Math.abs(leaderKmh - followerKmh);
            stats.maxSpeedDeltaKmh = Math.max(stats.maxSpeedDeltaKmh, speedDelta);

            if ((session.elapsed % DETAIL_INTERVAL_TICKS) == 0) {
                session.report.add(String.format(Locale.ROOT,
                        "  PAIR %s->%s mutual=%s distance3d=%.6f"
                                + " horizontalDistance=%.6f speedDeltaKmh=%.3f",
                        stats.leaderRole,
                        stats.followerRole,
                        mutual,
                        distance3d,
                        horizontalDistance,
                        speedDelta));
            }
        }
    }

    private static RailSample nearestRail(ServerLevel level, Entity entity) {
        BlockPos base = BlockPos.containing(
                entity.getX(),
                entity.getY(),
                entity.getZ());

        BlockPos bestPos = null;
        BlockState bestState = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int dy = -3; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BaseRailBlock)) {
                        continue;
                    }

                    double cx = pos.getX() + 0.5D;
                    double cy = pos.getY() + 0.125D;
                    double cz = pos.getZ() + 0.5D;
                    double ddx = entity.getX() - cx;
                    double ddy = entity.getY() - cy;
                    double ddz = entity.getZ() - cz;
                    double distance = ddx * ddx + ddy * ddy + ddz * ddz;

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = pos.immutable();
                        bestState = state;
                    }
                }
            }
        }

        if (bestPos == null || bestState == null) {
            return null;
        }

        BaseRailBlock rail = (BaseRailBlock) bestState.getBlock();
        AbstractMinecart cart = entity instanceof AbstractMinecart minecart ? minecart : null;

        RailShape sampledShape = null;
        try {
            sampledShape = rail.getRailDirection(
                    bestState,
                    level,
                    bestPos,
                    cart);
        } catch (RuntimeException ignored) {
        }

        ResourceLocation railKey = ForgeRegistries.BLOCKS.getKey(bestState.getBlock());
        String registryName = railKey == null
                ? bestState.getBlock().getClass().getName()
                : railKey.toString();

        if (bestState.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
            int part = batch.getPart(bestState);
            Direction facing = batch.getAssemblyFacing(bestState);
            boolean active = bestState.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                    && bestState.getValue(LegacyBatchSwitchRailBlock.ACTIVE);
            String batchId = batch.getSpec().id();
            BlockPos root = batch.getAssemblyRoot(bestPos, bestState);

            String key = "BATCH:"
                    + batchId
                    + ":root=" + root
                    + ":part=" + part
                    + ":active=" + active;

            String detail = "batchId=" + batchId
                    + ",part=" + part
                    + ",facing=" + facing
                    + ",active=" + active
                    + ",root=" + root
                    + ",railPos=" + bestPos
                    + ",shape=" + (sampledShape == null
                            ? "UNKNOWN"
                            : sampledShape.getSerializedName())
                    + ",centerDistance="
                    + String.format(Locale.ROOT, "%.5f", Math.sqrt(bestDistance));

            return new RailSample(bestPos, key, detail, batchId);
        }

        String shape = sampledShape == null
                ? "UNKNOWN"
                : sampledShape.getSerializedName();
        String key = "RAIL:" + registryName + ":" + bestPos + ":" + shape;
        String detail = "registry=" + registryName
                + ",railPos=" + bestPos
                + ",shape=" + shape
                + ",centerDistance="
                + String.format(Locale.ROOT, "%.5f", Math.sqrt(bestDistance));

        return new RailSample(bestPos, key, detail, null);
    }

    private static void finish(ServerPlayer player, TraceSession session) {
        try {
            session.report.add("");
            session.report.add("SUMMARY:");
            session.report.add("ELAPSED_TICKS=" + session.elapsed);
            session.report.add("SAMPLES=" + session.samples);
            session.report.add("MANUAL_STOP=" + session.manualStop);
            session.report.add("CHAIN_COUNT_START=" + session.chainIds.size());
            session.report.add("MUTUAL_LINK_FAILURE_SAMPLES=" + session.mutualLinkFailureSamples);
            session.report.add("MISSING_ENTITY_SAMPLES=" + session.missingEntitySamples);
            session.report.add("OFF_RAIL_SAMPLES_TOTAL=" + session.offRailSamples);
            session.report.add("BATCH_IDS_ENCOUNTERED=" + session.allBatchIds);

            for (EntityStats stats : session.entityStats.values()) {
                session.report.add(String.format(Locale.ROOT,
                        "ENTITY_SUMMARY role=%s uuid=%s type=%s samples=%d"
                                + " maxSpeedKmh=%.3f offRailSamples=%d batchIds=%s",
                        stats.role,
                        stats.entityId,
                        stats.type,
                        stats.samples,
                        stats.maxSpeedKmh,
                        stats.offRailSamples,
                        stats.batchIds));
            }

            for (PairStats stats : session.pairStats) {
                double min3d = stats.samples == 0 ? Double.NaN : stats.minDistance3d;
                double max3d = stats.samples == 0 ? Double.NaN : stats.maxDistance3d;
                double minHorizontal = stats.samples == 0
                        ? Double.NaN : stats.minHorizontalDistance;
                double maxHorizontal = stats.samples == 0
                        ? Double.NaN : stats.maxHorizontalDistance;

                session.report.add(String.format(Locale.ROOT,
                        "PAIR_SUMMARY pair=%s->%s samples=%d brokenSamples=%d"
                                + " minDistance3d=%.6f maxDistance3d=%.6f"
                                + " minHorizontalDistance=%.6f maxHorizontalDistance=%.6f"
                                + " maxSpeedDeltaKmh=%.3f",
                        stats.leaderRole,
                        stats.followerRole,
                        stats.samples,
                        stats.brokenSamples,
                        min3d,
                        max3d,
                        minHorizontal,
                        maxHorizontal,
                        stats.maxSpeedDeltaKmh));
            }

            boolean hardIntegrityFailure =
                    session.mutualLinkFailureSamples > 0
                            || session.missingEntitySamples > 0;

            session.report.add("HARD_INTEGRITY_FAILURE=" + hardIntegrityFailure);
            session.report.add("RESULT=T7_C1_REAL_CONSIST_TRACE_REVIEW_REQUIRED");

            Path file = writeReport(session.report);
            player.sendSystemMessage(Component.literal(
                    "t7 real-consist trace complete: " + file.toAbsolutePath()));
            player.sendSystemMessage(Component.literal(
                    "Upload the t7-real-consist-trace TXT for review."));

            for (String line : session.report) {
                LogUtils.getLogger().info("[TC-T7-CONSIST] {}", line);
            }
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal(
                    "Could not write t7 real-consist trace: " + ex.getMessage()));
        }
    }

    private static Path writeReport(List<String> report) throws Exception {
        Path dir = Path.of("logs", "traincraft-track-debug");
        Files.createDirectories(dir);

        String stamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));

        Path file = dir.resolve(
                "t7-real-consist-trace-"
                        + stamp
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8)
                        + ".txt");

        Files.write(file, report, StandardCharsets.UTF_8);
        return file;
    }
}
