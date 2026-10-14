package traincraft.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * DEV38 r1g1c TEMPORARY wheel-placement showroom.
 *
 * Spawns static, unlinked rolling stock in side-on grids for screenshots.
 * This command never changes movement, coupling, renderer calibration, wheel
 * animation, track logic, cargo, power, seats, or any production config.
 *
 * REMOVE BEFORE RELEASE.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1gFleetWheelShowroomCommand {
    private static final String TAG = "tc_r1g_fleet";
    private static final String NBT = "TCDev38R1gFleetShowroom";

    private static final int ALL_COLUMNS = 8;
    private static final int CATEGORY_COLUMNS = 6;
    private static final int COLUMN_SPACING = 10;
    private static final int ROW_SPACING = 6;

    private Dev38R1gFleetWheelShowroomCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_r1g_fleet")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("all")
                                .executes(ctx -> spawn(ctx.getSource(), Group.ALL, false)))
                        .then(Commands.literal("all_labels")
                                .executes(ctx -> spawn(ctx.getSource(), Group.ALL, true)))
                        .then(Commands.literal("steam")
                                .executes(ctx -> spawn(ctx.getSource(), Group.STEAM, false)))
                        .then(Commands.literal("tenders")
                                .executes(ctx -> spawn(ctx.getSource(), Group.TENDERS, false)))
                        .then(Commands.literal("powered")
                                .executes(ctx -> spawn(ctx.getSource(), Group.POWERED, false)))
                        .then(Commands.literal("cars")
                                .executes(ctx -> spawn(ctx.getSource(), Group.CARS, false)))
                        .then(Commands.literal("labels")
                                .then(Commands.literal("on")
                                        .executes(ctx -> labels(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> labels(ctx.getSource(), false))))
                        .then(Commands.literal("page")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> page(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "number")))))
                        .then(Commands.literal("count")
                                .executes(ctx -> count(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC r1g FLEET] wheel-placement showroom (TEMP DEBUG)");
        msg(source, "/tc_r1g_fleet all - entire rolling-stock fleet in one compact grid.");
        msg(source, "/tc_r1g_fleet page 1..4 - 1=steam, 2=tenders, 3=powered/builder, 4=cars.");
        msg(source, "/tc_r1g_fleet steam | tenders | powered | cars - category-only close grid.");
        msg(source, "/tc_r1g_fleet labels on|off - show/hide registry IDs above trains.");
        msg(source, "/tc_r1g_fleet count - report discovered rolling-stock count.");
        msg(source, "/tc_r1g_fleet clear - remove only showroom entities.");
        msg(source, "Run this in the flat DEV world. Trains are static, unlinked, invulnerable and no-gravity.");
        return 1;
    }

    private static int page(CommandSourceStack source, int page) {
        Group group = switch (page) {
            case 1 -> Group.STEAM;
            case 2 -> Group.TENDERS;
            case 3 -> Group.POWERED;
            default -> Group.CARS;
        };
        return spawn(source, group, false);
    }

    private static int count(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<Entry> fleet = discover(level, Group.ALL);
        long steam = fleet.stream().filter(e -> groupFor(e.id()) == Group.STEAM).count();
        long tenders = fleet.stream().filter(e -> groupFor(e.id()) == Group.TENDERS).count();
        long powered = fleet.stream().filter(e -> groupFor(e.id()) == Group.POWERED).count();
        long cars = fleet.stream().filter(e -> groupFor(e.id()) == Group.CARS).count();

        msg(source, String.format(Locale.ROOT,
                "[TC r1g FLEET] total=%d steam=%d tenders=%d powered/builder=%d cars=%d",
                fleet.size(), steam, tenders, powered, cars));

        if (fleet.size() != 111) {
            msg(source, "[TC r1g FLEET] NOTE: wheel audit had 111 wrapped renderer registrations; "
                    + "runtime discovery differs, so send this count back.");
        }
        return fleet.size();
    }

    private static int spawn(CommandSourceStack source, Group wanted, boolean showLabels) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("[TC r1g FLEET] Player-only command."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        clearNear(level, player.position(), 160.0D);

        List<Entry> fleet = discover(level, wanted);
        if (fleet.isEmpty()) {
            source.sendFailure(Component.literal("[TC r1g FLEET] No rolling stock discovered."));
            return 0;
        }

        Direction view = player.getDirection();
        if (!view.getAxis().isHorizontal()) view = Direction.NORTH;
        Direction right = view.getClockWise();

        int columns = wanted == Group.ALL ? ALL_COLUMNS : CATEGORY_COLUMNS;
        int rows = (fleet.size() + columns - 1) / columns;

        // Put the first row 10 blocks in front of the player and center the
        // columns across the player's view. All trains point left/right, so the
        // player sees their broad side immediately.
        BlockPos origin = player.blockPosition()
                .relative(view, 10)
                .relative(right, -((columns - 1) * COLUMN_SPACING) / 2);

        int spawned = 0;
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < fleet.size(); i++) {
            Entry entry = fleet.get(i);
            int col = i % columns;
            int row = i / columns;

            BlockPos cell = origin
                    .relative(right, col * COLUMN_SPACING)
                    .relative(view, row * ROW_SPACING);

            Entity entity = entry.type().create(level);
            if (entity == null) {
                failures.add(entry.id().getPath() + "(create=null)");
                continue;
            }

            double x = cell.getX() + 0.5D;
            double y = player.getY() + 0.05D;
            double z = cell.getZ() + 0.5D;
            float yaw = right.toYRot();

            entity.moveTo(x, y, z, yaw, 0.0F);
            entity.setYRot(yaw);
            entity.setXRot(0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.addTag(TAG);
            entity.getPersistentData().putBoolean(NBT, true);
            entity.setCustomName(Component.literal(entry.id().getPath()));
            entity.setCustomNameVisible(showLabels);

            if (level.addFreshEntity(entity)) {
                spawned++;
            } else {
                failures.add(entry.id().getPath() + "(add=false)");
            }
        }

        msg(source, String.format(Locale.ROOT,
                "[TC r1g FLEET] spawned=%d requested=%d group=%s grid=%dx%d labels=%s",
                spawned, fleet.size(), wanted.name().toLowerCase(Locale.ROOT),
                columns, rows, Boolean.toString(showLabels)));
        msg(source, "Camera tip: fly up/back 15-30 blocks. Use F1 for clean screenshots.");
        msg(source, "For closer shots: /tc_r1g_fleet page 1, then 2, 3, 4.");

        if (!failures.isEmpty()) {
            msg(source, "[TC r1g FLEET] spawn failures=" + failures.size()
                    + " first=" + failures.get(0));
        }

        return spawned;
    }

    private static int labels(CommandSourceStack source, boolean visible) {
        ServerLevel level = source.getLevel();
        Vec3 center = source.getPosition();
        int changed = 0;

        for (Entity entity : showroomEntities(level, center, 180.0D)) {
            entity.setCustomNameVisible(visible);
            changed++;
        }

        msg(source, "[TC r1g FLEET] labels=" + visible + " entities=" + changed);
        return changed;
    }

    private static int clear(CommandSourceStack source) {
        Vec3 center = source.getPosition();
        int removed = clearNear(source.getLevel(), center, 180.0D);
        msg(source, "[TC r1g FLEET] cleared=" + removed);
        return removed;
    }

    private static int clearNear(ServerLevel level, Vec3 center, double radius) {
        List<Entity> entities = showroomEntities(level, center, radius);
        for (Entity entity : entities) {
            entity.discard();
        }
        return entities.size();
    }

    private static List<Entity> showroomEntities(
            ServerLevel level,
            Vec3 center,
            double radius) {

        AABB area = new AABB(
                center.x - radius, center.y - 48.0D, center.z - radius,
                center.x + radius, center.y + 48.0D, center.z + radius);

        return level.getEntities(
                (Entity) null,
                area,
                entity -> entity.getPersistentData().getBoolean(NBT)
                        || entity.getTags().contains(TAG));
    }

    private static List<Entry> discover(ServerLevel level, Group wanted) {
        List<Entry> result = new ArrayList<>();

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
            Group group = groupFor(id);
            probe.discard();

            if (!rolling) continue;
            if (wanted != Group.ALL && group != wanted) continue;

            result.add(new Entry(id, type));
        }

        result.sort(Comparator
                .comparingInt((Entry e) -> groupFor(e.id()).order)
                .thenComparing(e -> e.id().getPath()));

        return result;
    }

    private static boolean isRollingStock(ResourceLocation id, Entity probe) {
        String path = id.getPath();
        String className = probe.getClass().getName();

        // Primary truth: current train entity packages.
        if (className.startsWith("traincraft.entity.train.")) {
            return true;
        }

        // Fallback covers special renderer-backed legacy cars if their class was
        // retained outside the modern train package.
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

        if (path.startsWith("locomotive_steam_")) {
            return Group.STEAM;
        }

        if (path.contains("tender")) {
            return Group.TENDERS;
        }

        if (path.startsWith("locomotive_diesel_")
                || path.startsWith("locomotive_electric_")
                || "tracks_builder".equals(path)) {
            return Group.POWERED;
        }

        return Group.CARS;
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private enum Group {
        ALL(-1),
        STEAM(0),
        TENDERS(1),
        POWERED(2),
        CARS(3);

        final int order;

        Group(int order) {
            this.order = order;
        }
    }

    private record Entry(ResourceLocation id, EntityType<?> type) {}
}
