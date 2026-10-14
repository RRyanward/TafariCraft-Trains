package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * TC_DEV38_R1G1Z7Y18C_STATIONARY_RUNNING_GEAR_DEBUG_ONLY
 * ONE unoccupied and nearly stopped train at a time; integrated singleplayer
 * only. Teleports that engine back to its recorded origin after its server
 * tick; all movement/network/gameplay systems otherwise remain untouched.
 * Shared-phase override exists ONLY while this session is active on this UUID.
 * Do not use on release servers or consists. Gravity is never changed.
 * TC_DEV38_R1G1Z7Y18H_SIX_TRAIN_BATCH_HARNESS: read-only batch orchestration only.
 * TC_DEV38_R1G1Z7Y18I_AUTO_SPAWN_SWAP: only Heavy Steam must be placed;
 * the remaining five test locomotives are temporary entities spawned one-at-a-time
 * at the exact Heavy anchor and discarded immediately after their test leg.
 */
// TC_DEV38_R1G1Z7Y18C1_COMMAND_BRIDGE: registered via existing Forge auto-subscriber
public final class Dev38StationaryRunningGearTest {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Set<String> TARGETS = Set.of(
            "locomotive_steam_heavy",
            "locomotive_steam_mogul",
            "locomotive_steam_forney",
            "locomotive_diesel_v60",
            "locomotive_steam_br01",
            "locomotive_steam_ussr");
    private static final double SELECT_RANGE = 24.0;
    private static final long REST_TICKS = 40L;
    private static final long FORWARD_TICKS = 160L;
    private static final long STOP_TICKS = 40L;
    private static final long REVERSE_TICKS = 160L;
    private static final long FINAL_TICKS = 40L;
    private static final long TOTAL_TICKS =
            REST_TICKS + FORWARD_TICKS + STOP_TICKS + REVERSE_TICKS + FINAL_TICKS;
    private static final double TICKS_PER_REV = 40.0; // 4 complete revs / direction
    // DEV38 y18v: accepted locomotives are removed from active batch legs.
    // Heavy remains an anchor only; Mogul + Forney are DONE/FROZEN.
    private static final List<String> BATCH_ORDER = List.of(
            "locomotive_diesel_v60",
            "locomotive_steam_br01",
            "locomotive_steam_ussr");
    private static final List<String> FLEET_IDS = List.of(
            "locomotive_steam_262t",
            "locomotive_steam_4_4_0",
            "locomotive_steam_a4_mallard",
            "locomotive_steam_adler",
            "locomotive_steam_alco_sc4",
            "locomotive_steam_alice_0_4_0",
            "locomotive_steam_berkshire_1225",
            "locomotive_steam_berkshire_765",
            "locomotive_steam_br_black_5",
            "locomotive_steam_br_britannia",
            "locomotive_steam_br80",
            "locomotive_steam_c11",
            "locomotive_steam_c41",
            "locomotive_steam_c41_080",
            "locomotive_steam_c41t",
            "locomotive_steam_c62",
            "locomotive_steam_cherepanov",
            "locomotive_steam_climax",
            "locomotive_steam_climax_2",
            "locomotive_steam_coronation_class",
            "locomotive_steam_d51",
            "locomotive_steam_d51_long",
            "locomotive_steam_fowler",
            "locomotive_steam_glyn_042t",
            "locomotive_steam_gs4",
            "locomotive_steam_gwr_101_class",
            "locomotive_steam_gwr_42xx",
            "locomotive_steam_gwr_72xx",
            "locomotive_steam_hall_class",
            "locomotive_steam_j50",
            "locomotive_steam_king_class",
            "locomotive_steam_lssp7",
            "locomotive_steam_midland_compound",
            "locomotive_steam_milw_class_a",
            "locomotive_steam_pannier",
            "locomotive_steam_pe",
            "locomotive_steam_rw_type_2",
            "locomotive_steam_rw_type_3",
            "locomotive_steam_s100_uk",
            "locomotive_steam_s100_us",
            "locomotive_steam_sentinel_y3",
            "locomotive_steam_shay_3_truck",
            "locomotive_steam_skookum",
            "locomotive_steam_small",
            "locomotive_steam_snow_plow",
            "locomotive_steam_southern1102",
            "locomotive_steam_star_class",
            "locomotive_steam_u57",
            "locomotive_steam_vb_0_4_0",
            "locomotive_steam_vb_shay",
            "locomotive_steam_vb_shay_2",
            "locomotive_steam_wwcp_062t"
    );
    // DEV44 full-run excludes four deferred locomotive IDs without changing the grouped tests.
    private static final Set<String> FULL_RUN_DEFERRED = Set.of(
            "locomotive_steam_c62", "locomotive_steam_d51",
            "locomotive_steam_d51_long", "locomotive_steam_small");
    private static final int FLEET_GROUP_SIZE = 8;
    private static final long BATCH_GAP_TICKS = 20L;
    private static volatile Session current;
    private static volatile BatchSession batch;

    private Dev38StationaryRunningGearTest() {}
    // TC_DEV44_R13AJ_EXACT_BERKSHIRE_BRIDGE - isolated, UUID-checked DEV44 entry.
    // Preserve legacy tc_gearfreeze target filtering and all existing wheel logic.
    public static int startExactDev44BerkshireReference(CommandSourceStack source, Entity fixture)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (source.getServer().isDedicatedServer()) {
            reply(source, "SAFETY STOP: DEV44 Berkshire reference is integrated-server only.");
            return 0;
        }
        if (current != null || batch != null) {
            reply(source, "SAFETY STOP: another running-gear test is already active.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();
        if (fixture == null || !fixture.isAlive()
                || fixture.level() != world
                || world.getEntity(fixture.getUUID()) != fixture) {
            reply(source, "SAFETY STOP: reference fixture is missing from this world.");
            return 0;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(fixture.getType());
        if (key == null || !"traincraft".equals(key.getNamespace())
                || !"locomotive_steam_berkshire_765".equals(key.getPath())) {
            reply(source, "SAFETY STOP: exact fixture is not Berkshire 765.");
            return 0;
        }
        if (fixture.isPassenger() || !fixture.getPassengers().isEmpty()
                || fixture.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            reply(source, "SAFETY STOP: Berkshire must be unoccupied and stationary.");
            return 0;
        }
        Session state = new Session(world, player, fixture, key.getPath());
        current = state;
        pin(fixture, state);
        LOG.info("[TC-DEV44-R13aj-EXACT-START] id={} uuid={} pos={} tick={} totalTicks={} safety=INTEGRATED_SINGLE_ENTITY",
                state.name, state.uuid, state.origin, state.startTick, TOTAL_TICKS);
        reply(source, "R13aj EXACT UUID START " + state.uuid
                + " | 2s REST, 8s FORWARD, 2s STOP, 8s REVERSE, 2s FINAL STOP.");
        return 1;
    }

    /** Read-only status; does not claim that a rendered wheel moved. */
    public static String dev44BerkshireReferenceStatus(UUID expected) {
        Session s = current;
        if (s == null) return "INACTIVE";
        if (expected == null || !expected.equals(s.uuid)) return "OTHER_SESSION";
        long elapsed = s.world.getGameTime() - s.startTick;
        return "ACTIVE elapsed=" + elapsed + "/" + TOTAL_TICKS
                + " stage=" + stage(elapsed) + " virtualPhase=" + phaseAt(elapsed);
    }


    private static final class Session {
        final ServerLevel world;
        final ServerPlayer owner;
        final UUID uuid;
        final String name;
        final Vec3 origin;
        final float yaw;
        final float pitch;
        final long startTick;
        int lastStage = -1;

        Session(ServerLevel world, ServerPlayer owner, Entity e, String name) {
            this.world = world;
            this.owner = owner;
            this.uuid = e.getUUID();
            this.name = name;
            this.origin = e.position();
            this.yaw = e.getYRot();
            this.pitch = e.getXRot();
            this.startTick = world.getGameTime();
        }
    }

    private static final class SavedTrain {
        final UUID uuid;
        final String name;
        final Vec3 origin;
        final float yaw;
        final float pitch;

        SavedTrain(Entity e, String name) {
            this.uuid = e.getUUID();
            this.name = name;
            this.origin = e.position();
            this.yaw = e.getYRot();
            this.pitch = e.getXRot();
        }
    }

    private static final class BatchSession {
        final ServerLevel world;
        final ServerPlayer owner;
        final SavedTrain heavy;
        final Vec3 anchor;
        final Vec3 heavyHolding;
        final float anchorYaw;
        final float anchorPitch;
        final List<String> order;
        final boolean fleetMode;
        int index = 0;
        long nextLegTick = 0L;
        UUID spawnedUuid;
        String spawnedName;

        BatchSession(ServerLevel world, ServerPlayer owner, SavedTrain heavy, List<String> order, boolean fleetMode) {
            this.order = order;
            this.fleetMode = fleetMode;
            this.world = world;
            this.owner = owner;
            this.heavy = heavy;
            this.anchor = heavy.origin;
            this.heavyHolding = heavy.origin.add(0.0, 64.0, 0.0);
            this.anchorYaw = heavy.yaw;
            this.anchorPitch = heavy.pitch;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tc_gearfreeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource()))));
        d.register(Commands.literal("tc_fleetgear")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> fleetStart(ctx.getSource(), 0))
                        .then(Commands.argument("group", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 7))
                                .executes(ctx -> fleetStart(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "group")))))
                .then(Commands.literal("status").executes(ctx -> batchStatus(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> batchStop(ctx.getSource()))));
        d.register(Commands.literal("tc_fleetyard")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create").executes(ctx -> yardCreate(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> yardClear(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> yardReset(ctx.getSource())))
                .then(Commands.literal("tp")
                        .then(Commands.argument("row", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> yardTeleport(ctx.getSource(),
                                  com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"row"))))));
        d.register(Commands.literal("tc_gearbatch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> batchStart(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> batchStatus(ctx.getSource())))
                .then(Commands.literal("stop")
                        .executes(ctx -> batchStop(ctx.getSource()))));
    }

    // DEV44 R9: non-destructive inspection staging. Tagged entities only; no terrain edits.
    private static final String YARD_TAG = "tc_dev44_r9_yard";
    private static Vec3 yardOrigin;
    private static ServerLevel yardWorld;
    private static final int YARD_COLUMNS = 6;
    private static final double YARD_SPACING_X = 16.0;
    private static final double YARD_SPACING_Z = 11.0;

    private static List<String> yardIds() {
        List<String> ids = new ArrayList<>();
        for (String id : FLEET_IDS) if (!FULL_RUN_DEFERRED.contains(id)) ids.add(id);
        return ids;
    }

    private static void yardMessage(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal("[DEV44 YARD] " + msg), false);
    }

    private static int yardCreate(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (yardWorld != null && yardOrigin != null) {
            yardMessage(source, "Yard already active. Run /tc_fleetyard clear first."); return 0;
        }
        if (batch != null) {
            yardMessage(source, "Stop /tc_fleetgear before spawning inspection yard."); return 0;
        }
        Vec3 base = player.position().add(12, 2, 12);
        List<String> ids = yardIds();
        if (ids.size() != 48) {
            yardMessage(source, "SAFETY STOP: expected 48 eligible locomotives, got " + ids.size()); return 0;
        }
        // Prevalidate entity registrations: no partial spawn if any type is absent.
        for (String id : ids) {
            if (ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft", id)) == null) {
                yardMessage(source, "SAFETY STOP: unknown locomotive entity: " + id); return 0;
            }
        }
        int spawned = 0;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft", id));
            Entity train = type.create(level);
            if (train == null) {
                yardMessage(source, "Could not create " + id + "; clearing partial yard.");
                yardWorld = level; yardOrigin = base; yardClear(source); return 0;
            }
            Vec3 pos = base.add((i % YARD_COLUMNS) * YARD_SPACING_X, 0, (i / YARD_COLUMNS) * YARD_SPACING_Z);
            train.moveTo(pos.x, pos.y, pos.z, 0, 0);
            train.setNoGravity(true);
            train.setDeltaMovement(Vec3.ZERO);
            train.addTag(YARD_TAG);
            train.addTag("tc_dev44_id_" + i);
            if (!level.addFreshEntity(train)) {
                yardWorld = level; yardOrigin = base; yardClear(source);
                yardMessage(source, "Spawn rejected for " + id + "; partial yard cleared."); return 0;
            }
            spawned++;
        }
        yardWorld = level;
        yardOrigin = base;
        yardMessage(source, "Created " + spawned + " labeled-by-index tagged locomotives in 8 rows x 6. No blocks changed. Use /tc_fleetyard tp 1-8.");
        return 1;
    }

    private static int yardClear(CommandSourceStack source) {
        ServerLevel level = yardWorld;
        if (level == null || yardOrigin == null) {
            yardMessage(source, "No yard recorded for this session."); return 0;
        }
        int count=0;
        for (Entity train : level.getEntities((Entity)null,
                new AABB(yardOrigin.add(-12,-20,-12),yardOrigin.add(110,25,110)),
                entity -> entity.getTags().contains(YARD_TAG))) {
            train.discard(); count++;
        }
        yardOrigin=null; yardWorld=null;
        yardMessage(source,"Removed " + count + " tagged yard locomotives; no normal trains touched.");
        return 1;
    }

    private static int yardReset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        yardClear(source); return yardCreate(source);
    }

    private static int yardTeleport(CommandSourceStack source, int row) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        if (yardOrigin==null || yardWorld!=player.serverLevel()) {
            yardMessage(source,"Create the yard in this dimension first."); return 0;
        }
        Vec3 target=yardOrigin.add(-5, 0, (row-1)*YARD_SPACING_Z);
        player.teleportTo(target.x,target.y,target.z);
        yardMessage(source,"Inspection row " + row + " / 8, locomotives " + ((row-1)*6+1) + "-" + (row*6));
        return 1;
    }

    private static void reply(CommandSourceStack source, String s) {
        source.sendSuccess(() -> Component.literal("[TC-Y18C-FREEZE] " + s), false);
    }

    private static void replyBatch(CommandSourceStack source, String s) {
        source.sendSuccess(() -> Component.literal("[TC-Y18V-BATCH] " + s), false);
    }

    private static String supportedType(Entity e) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (key == null || !"traincraft".equals(key.getNamespace())
                || !(TARGETS.contains(key.getPath()) || FLEET_IDS.contains(key.getPath()))) return null;
        return key.getPath();
    }

    private static int start(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (source.getServer().isDedicatedServer()) {
            reply(source, "SAFETY STOP: singleplayer integrated-server only.");
            return 0;
        }
        if (current != null || batch != null) {
            reply(source, "SAFETY STOP: session already active. Use /tc_gearfreeze stop or /tc_gearbatch stop.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(SELECT_RANGE);
        Entity chosen = null;
        String chosenName = null;
        double best = SELECT_RANGE * SELECT_RANGE;
        for (Entity e : world.getEntitiesOfClass(Entity.class, box, e -> e.isAlive())) {
            String name = supportedType(e);
            if (name == null || !TARGETS.contains(name)) continue;
            double dist = player.distanceToSqr(e);
            if (dist < best) {
                best = dist;
                chosen = e;
                chosenName = name;
            }
        }
        if (chosen == null) {
            reply(source, "No supported locomotive within 24 blocks. Park one first.");
            return 0;
        }
        if (chosen.isPassenger() || !chosen.getPassengers().isEmpty()
                || chosen.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            reply(source, "SAFETY STOP: locomotive must be stationary and unoccupied.");
            return 0;
        }
        Session state = new Session(world, player, chosen, chosenName);
        // Freeze is applied only to this entity. Other trains are unaffected.
        current = state;
        pin(chosen, state);
        LOG.info("[TC-Y18C-FREEZE-START] id={} uuid={} pos={} tick={} totalTicks={} camera=STATIONARY world=INTEGRATED",
                state.name, state.uuid, state.origin, state.startTick, TOTAL_TICKS);
        reply(source, "START " + state.name
                + " | 2s REST, 8s FORWARD, 2s STOP, 8s REVERSE, 2s STOP"
                + " | Train pinned; hold camera still for side close-up.");
        return 1;
    }

    private static int batchStart(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (source.getServer().isDedicatedServer()) {
            replyBatch(source, "SAFETY STOP: singleplayer integrated-server only.");
            return 0;
        }
        if (current != null || batch != null) {
            replyBatch(source, "SAFETY STOP: another running-gear session is active.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();

        AABB box = player.getBoundingBox().inflate(SELECT_RANGE);
        List<Entity> heavyMatches = new ArrayList<>();
        for (Entity e : world.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
            String name = supportedType(e);
            if ("locomotive_steam_heavy".equals(name)) heavyMatches.add(e);
        }
        if (heavyMatches.size() != 1) {
            replyBatch(source, "SAFETY STOP: need exactly one Heavy Steam within 24 blocks; found "
                    + heavyMatches.size() + ".");
            return 0;
        }
        Entity heavyEntity = heavyMatches.get(0);
        if (heavyEntity.isPassenger() || !heavyEntity.getPassengers().isEmpty()
                || heavyEntity.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            replyBatch(source, "SAFETY STOP: Heavy Steam must be stationary and unoccupied.");
            return 0;
        }

        SavedTrain heavy = new SavedTrain(heavyEntity, "locomotive_steam_heavy");
        BatchSession b = new BatchSession(world, player, heavy, BATCH_ORDER, false);
        batch = b;
        LOG.info("[TC-Y18V-BATCH-START] mode=AUTO_SPAWN_REMAINING_THREE count={} anchor={} order={}",
                b.order.size(), b.anchor, BATCH_ORDER);
        replyBatch(source, "START remaining-three batch at Heavy Steam anchor. Keep camera fixed."
                + " DONE/FROZEN skipped: Heavy, Mogul, Forney."
                + " Order: V60, BR01, USSR 0-5-0."
                + " Heavy is anchor-only; all three test locomotives are temporary.");
        startBatchLeg(b);
        return 1;
    }

    private static int fleetStart(CommandSourceStack source, int group)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (source.getServer().isDedicatedServer()) {
            replyBatch(source, "SAFETY STOP: singleplayer integrated-server only.");
            return 0;
        }
        if (current != null || batch != null) {
            replyBatch(source, "SAFETY STOP: another running-gear session is active.");
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();

        AABB box = player.getBoundingBox().inflate(SELECT_RANGE);
        List<Entity> heavyMatches = new ArrayList<>();
        for (Entity e : world.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
            String name = supportedType(e);
            if ("locomotive_steam_heavy".equals(name)) heavyMatches.add(e);
        }
        if (heavyMatches.size() != 1) {
            replyBatch(source, "SAFETY STOP: need exactly one Heavy Steam within 24 blocks; found "
                    + heavyMatches.size() + ".");
            return 0;
        }
        Entity heavyEntity = heavyMatches.get(0);
        if (heavyEntity.isPassenger() || !heavyEntity.getPassengers().isEmpty()
                || heavyEntity.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            replyBatch(source, "SAFETY STOP: Heavy Steam must be stationary and unoccupied.");
            return 0;
        }

        List<String> groupOrder;
        if (group == 0) {
            groupOrder = new ArrayList<>();
            for (String id : FLEET_IDS) {
                if (!FULL_RUN_DEFERRED.contains(id)) groupOrder.add(id);
            }
        } else {
            int from = (group - 1) * FLEET_GROUP_SIZE;
            int to = Math.min(FLEET_IDS.size(), from + FLEET_GROUP_SIZE);
            groupOrder = FLEET_IDS.subList(from, to);
        }
        SavedTrain heavy = new SavedTrain(heavyEntity, "locomotive_steam_heavy");
        BatchSession b = new BatchSession(world, player, heavy, groupOrder, true);
        batch = b;
        LOG.info("[TC-DEV44-FLEET-START] mode={} group={} count={} order={}",
                group == 0 ? "FULL_48" : "GROUP", group, groupOrder.size(), groupOrder);
        replyBatch(source, (group == 0 ? "FULL FLEET 48 (4 deferred)" : "FLEET GROUP " + group + "/7")
                + " | " + groupOrder.size()
                + " temporary steam locomotives, Heavy anchor only. Capture video."
                + " Automatic forward/reverse phase; visual acceptance required.");
        startBatchLeg(b);
        return 1;
    }

    private static int batchStatus(CommandSourceStack source) {
        BatchSession b = batch;
        if (b == null) {
            replyBatch(source, "INACTIVE. Park exactly one Heavy Steam within 24 blocks,"
                    + " position the camera, then use /tc_gearbatch start.");
            return 1;
        }
        int shown = Math.min(b.index + 1, b.order.size());
        String active = current != null ? current.name : "transition";
        replyBatch(source, "ACTIVE " + shown + "/" + b.order.size() + " active=" + active);
        return 1;
    }

    private static int batchStop(CommandSourceStack source) {
        BatchSession b = batch;
        if (b == null) {
            replyBatch(source, "Already inactive.");
            return 1;
        }
        if (source.getServer().isDedicatedServer()) {
            replyBatch(source, "SAFETY STOP: not supported on a dedicated server.");
            return 0;
        }
        abortBatch(b, "MANUAL_STOP");
        replyBatch(source, "Stopped batch; temporary locomotive discarded and Heavy Steam restored.");
        return 1;
    }

    private static void startBatchLeg(BatchSession b) {
        if (batch != b || current != null) return;
        if (b.index >= b.order.size()) {
            completeBatch(b);
            return;
        }

        String id = b.order.get(b.index);

        // DEV38 y18v: Heavy is anchor-only. Every active leg is a temporary
        // remaining locomotive, including index 0 (V60).
        Entity heavyEntity = b.world.getEntity(b.heavy.uuid);
        if (heavyEntity == null || !heavyEntity.isAlive()) {
            abortBatch(b, "ANCHOR_HEAVY_MISSING");
            return;
        }
        placeEntity(heavyEntity, b.heavyHolding, b.heavy.yaw, b.heavy.pitch);

        Entity e = spawnTemporary(b, id);
        if (e == null) {
            abortBatch(b, "SPAWN_FAILED_" + id);
            return;
        }

        if (b.fleetMode) LOG.info("[TC-DEV44-FLEET-LEG] index={} id={} stage=SPAWNED", b.index + 1, id);
        Session s = new Session(b.world, b.owner, e, id);
        current = s;
        pin(e, s);
        LOG.info("[TC-Y18V-BATCH-LEG-START] index={}/{} id={} uuid={} anchor={} temporary=true totalTicks={}",
                b.index + 1, b.order.size(), id, e.getUUID(), b.anchor, TOTAL_TICKS);
        if (b.owner != null) b.owner.sendSystemMessage(Component.literal(
                "[TC-Y18V-BATCH] " + (b.index + 1) + "/" + b.order.size() + " " + id
                        + " | REST 2s, FORWARD 8s, STOP 2s, REVERSE 8s, STOP 2s"));
    }

    private static Entity spawnTemporary(BatchSession b, String id) {
        ResourceLocation key = new ResourceLocation("traincraft", id);
        net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
        if (type == null) {
            LOG.error("[TC-Y18V-BATCH-SPAWN] id={} result=NO_ENTITY_TYPE", id);
            return null;
        }
        Entity e = type.create(b.world);
        if (e == null) {
            LOG.error("[TC-Y18V-BATCH-SPAWN] id={} result=CREATE_NULL", id);
            return null;
        }
        placeEntity(e, b.anchor, b.anchorYaw, b.anchorPitch);
        if (!b.world.addFreshEntity(e)) {
            e.discard();
            LOG.error("[TC-Y18V-BATCH-SPAWN] id={} result=ADD_REJECTED", id);
            return null;
        }
        b.spawnedUuid = e.getUUID();
        b.spawnedName = id;
        LOG.info("[TC-Y18V-BATCH-SPAWN] id={} uuid={} pos={} temporary=true",
                id, e.getUUID(), b.anchor);
        return e;
    }

    private static void discardTemporary(BatchSession b) {
        UUID uuid = b.spawnedUuid;
        String name = b.spawnedName;
        b.spawnedUuid = null;
        b.spawnedName = null;
        if (uuid == null) return;
        Entity e = b.world.getEntity(uuid);
        if (e != null) {
            e.setDeltaMovement(Vec3.ZERO);
            e.discard();
        }
        LOG.info("[TC-Y18V-BATCH-DISCARD] id={} uuid={} discarded=true",
                name == null ? "unknown" : name, uuid);
    }

    private static void holdHeavy(BatchSession b) {
        if (b == null) return;
        // DEV38 y18v: Heavy never receives a leg; hold it clear for the whole batch.
        Entity heavy = b.world.getEntity(b.heavy.uuid);
        if (heavy != null && heavy.isAlive()) {
            placeEntity(heavy, b.heavyHolding, b.heavy.yaw, b.heavy.pitch);
        }
    }

    private static void placeEntity(Entity e, Vec3 pos, float yaw, float pitch) {
        e.setDeltaMovement(Vec3.ZERO);
        e.setPos(pos.x, pos.y, pos.z);
        e.xo = pos.x;
        e.yo = pos.y;
        e.zo = pos.z;
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.yRotO = yaw;
        e.xRotO = pitch;
    }

    private static void restoreHeavy(BatchSession b) {
        Entity e = b.world.getEntity(b.heavy.uuid);
        if (e == null) return;
        placeEntity(e, b.heavy.origin, b.heavy.yaw, b.heavy.pitch);
    }

    private static void afterBatchLeg(Session s, String reason) {
        BatchSession b = batch;
        if (b == null || b.index >= b.order.size()) return;
        String id = b.order.get(b.index);
        if (!id.equals(s.name)) return;

        // DEV38 y18v: every active leg is a temporary remaining locomotive.
        discardTemporary(b);

        if (!"AUTO_COMPLETE".equals(reason)) {
            abortBatch(b, "LEG_" + reason);
            return;
        }

        LOG.info("[TC-Y18V-BATCH-LEG-DONE] index={}/{} id={} result=AUTO_COMPLETE temporary=true",
                b.index + 1, b.order.size(), id);
        if (b.fleetMode) LOG.info("[TC-DEV44-FLEET-LEG] index={} id={} stage=COMPLETE", b.index + 1, id);
        b.index++;
        if (b.index >= b.order.size()) {
            completeBatch(b);
            return;
        }
        b.nextLegTick = b.world.getGameTime() + BATCH_GAP_TICKS;
        String next = b.order.get(b.index);
        if (b.owner != null) b.owner.sendSystemMessage(Component.literal(
                "[TC-Y18V-BATCH] DONE " + id + " -> auto-spawn " + next
                        + " in 1 second (visual review pending)."));
    }

    private static void completeBatch(BatchSession b) {
        if (batch != b) return;
        discardTemporary(b);
        restoreHeavy(b);
        batch = null;
        LOG.info("[TC-Y18V-BATCH-DONE] result=AUTO_COMPLETE count={} order={} temporaryDiscarded=true heavyRestored=true",
                b.order.size(), BATCH_ORDER);
        if (b.owner != null) b.owner.sendSystemMessage(Component.literal(
                b.fleetMode
                        ? "[TC-DEV44-FLEET] RUN COMPLETE: " + b.order.size()
                            + " locomotive cycles; temporary entities removed; Heavy restored. REVIEW VIDEO before marking PASS."
                        : "[TC-Y18V-BATCH] COMPLETE: V60, BR01 and USSR 0-5-0 finished;"
                            + " temporary locomotives discarded; Heavy Steam anchor restored."
                            + " Mogul + Forney remained skipped/frozen."));
    }

    private static void abortBatch(BatchSession b, String reason) {
        if (batch != b) return;
        Session s = current;
        current = null;
        discardTemporary(b);
        restoreHeavy(b);
        batch = null;
        LOG.info("[TC-Y18V-BATCH-ABORT] reason={} active={} temporaryDiscarded=true heavyRestored=true",
                reason, s == null ? "none" : s.name);
        if (b.owner != null) b.owner.sendSystemMessage(Component.literal(
                "[TC-Y18V-BATCH] ABORT: " + reason
                        + "; temporary locomotive discarded and Heavy Steam restored."));
    }

    private static int status(CommandSourceStack source) {
        Session s = current;
        if (s == null) {
            reply(source, "INACTIVE. /tc_gearfreeze start near an uncoupled parked engine.");
        } else {
            long elapsed = s.world.getGameTime() - s.startTick;
            reply(source, "ACTIVE " + s.name + " elapsed=" + elapsed
                    + "/" + TOTAL_TICKS + " ticks, stage=" + stage(elapsed));
        }
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        BatchSession b = batch;
        if (b != null) {
            abortBatch(b, "MANUAL_STOP_VIA_GEARFREEZE");
            reply(source, "Stopped active batch; temporary locomotive discarded and Heavy Steam restored.");
            return 1;
        }
        Session s = current;
        if (s == null) {
            reply(source, "Already inactive.");
            return 1;
        }
        if (source.getServer().isDedicatedServer()) {
            reply(source, "SAFETY STOP: not supported on a dedicated server.");
            return 0;
        }
        finish(s, "MANUAL_STOP");
        reply(source, "Stopped stationary debug hold. Normal wheel phase resumed.");
        return 1;
    }

    private static String stage(long elapsed) {
        if (elapsed < REST_TICKS) return "REST";
        if (elapsed < REST_TICKS + FORWARD_TICKS) return "FORWARD";
        if (elapsed < REST_TICKS + FORWARD_TICKS + STOP_TICKS) return "STOP_FWD";
        if (elapsed < REST_TICKS + FORWARD_TICKS + STOP_TICKS + REVERSE_TICKS)
            return "REVERSE";
        return "FINAL_STOP";
    }

    // Returns radians, matching the existing native-phase range (-PI, PI).
    // Forward uses the observed negative sign; reverse retraces the same four
    // rotations, avoiding direction jumps at either stop point.
    private static double phaseAt(double ticks) {
        double t = Math.max(0.0, Math.min(TOTAL_TICKS, ticks));
        double raw;
        double fwdEnd = REST_TICKS + FORWARD_TICKS;
        double reverseStart = fwdEnd + STOP_TICKS;
        double reverseEnd = reverseStart + REVERSE_TICKS;
        if (t < REST_TICKS) raw = 0.0;
        else if (t < fwdEnd) raw = -2.0 * Math.PI * (t - REST_TICKS) / TICKS_PER_REV;
        else if (t < reverseStart) raw = -2.0 * Math.PI * FORWARD_TICKS / TICKS_PER_REV;
        else if (t < reverseEnd) raw = -2.0 * Math.PI * (FORWARD_TICKS - (t - reverseStart)) / TICKS_PER_REV;
        else raw = 0.0;
        return Math.IEEEremainder(raw, 2.0 * Math.PI);
    }

    /** Called exclusively by the TEMPORARY debug guard in native phase entry. */
    public static double sampleOverrideOrNaN(Entity entity, float partialTick) {
        Session s = current;
        if (s == null || entity == null || !entity.level().isClientSide
                || !s.uuid.equals(entity.getUUID())) return Double.NaN;
        float fractional = Float.isFinite(partialTick)
                ? Math.min(1.0f, Math.max(0.0f, partialTick)) : 0.0f;
        double elapsed = entity.level().getGameTime() - s.startTick + fractional;
        if (elapsed < 0.0 || elapsed > TOTAL_TICKS + 2.0) return Double.NaN;
        return phaseAt(elapsed);
    }

    private static void pin(Entity e, Session s) {
        // Keep 1.20.1 position + interpolation history identical at every tick.
        e.setDeltaMovement(Vec3.ZERO);
        e.setPos(s.origin.x, s.origin.y, s.origin.z);
        e.xo = s.origin.x;
        e.yo = s.origin.y;
        e.zo = s.origin.z;
        e.setYRot(s.yaw);
        e.setXRot(s.pitch);
        e.yRotO = s.yaw;
        e.xRotO = s.pitch;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Session s = current;
        BatchSession b = batch;
        if (b != null) holdHeavy(b);
        if (s == null && b != null && b.world.getGameTime() >= b.nextLegTick) {
            startBatchLeg(b);
            s = current;
        }
        if (s == null) return;
        Entity e = s.world.getEntity(s.uuid);
        if (e == null || !e.isAlive()) {
            finish(s, "ENTITY_MISSING");
            return;
        }
        pin(e, s);
        long elapsed = s.world.getGameTime() - s.startTick;
        if (elapsed >= TOTAL_TICKS) {
            finish(s, "AUTO_COMPLETE");
            return;
        }
        int stage = elapsed < REST_TICKS ? 0
                : elapsed < REST_TICKS + FORWARD_TICKS ? 1
                : elapsed < REST_TICKS + FORWARD_TICKS + STOP_TICKS ? 2
                : elapsed < REST_TICKS + FORWARD_TICKS + STOP_TICKS + REVERSE_TICKS ? 3 : 4;
        if (stage != s.lastStage) {
            s.lastStage = stage;
            LOG.info("[TC-Y18C-FREEZE-STAGE] id={} stage={} elapsedTicks={} virtualPhase={} pos={} ",
                    s.name, stage(elapsed), elapsed, phaseAt(elapsed), e.position());
            if (s.owner != null) s.owner.sendSystemMessage(Component.literal(
                    "[TC-Y18C-FREEZE] " + stage(elapsed)));
        }
    }

    private static void finish(Session s, String reason) {
        if (current != s) return;
        Entity e = s.world.getEntity(s.uuid);
        if (e != null) {
            pin(e, s);
            e.setDeltaMovement(Vec3.ZERO);
        }
        current = null;
        LOG.info("[TC-Y18C-FREEZE-DONE] id={} reason={} gravityUnchanged=true pos={}",
                s.name, reason, s.origin);
        if (s.owner != null) s.owner.sendSystemMessage(Component.literal(
                "[TC-Y18C-FREEZE] DONE: " + reason + ". Normal wheel phase restored."));
        afterBatchLeg(s, reason);
    }
}
