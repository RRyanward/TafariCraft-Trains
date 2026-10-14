package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Step 9.3g-t6-s4-d1 — complete 50-spec visual inspection gallery.
 *
 * Diagnostic only. Uses the REAL registered track items and their normal useOn()
 * placement code, but supplies only the exact support cells required by each spec.
 *
 * Command:
 *   /tc_all_tracks_gallery
 *
 * The gallery is intentionally very widely spaced so huge meshes (29x / 32x)
 * can be inspected for render-culling without overlapping neighboring examples.
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackAllRailsVisualGalleryCommand {
    private static final int COLUMNS = 5;
    private static final int SPACING = 96;
    private static final int START_OFFSET = 48;
    private static final int GALLERY_Y_OFFSET = 16;

    private record Entry(String id, LegacyBatchTrackSpec spec) {
    }

    private static final List<Entry> ENTRIES = List.of(
            // Row 1 — long quarter curves (includes the current 29x culling suspect)
            new Entry("track_curve_big", LegacyBatchTrackSpecs.CURVE_BIG),
            new Entry("track_curve_very_big", LegacyBatchTrackSpecs.CURVE_VERY_BIG),
            new Entry("track_curve_super_big", LegacyBatchTrackSpecs.CURVE_SUPER_BIG),
            new Entry("track_curve_29x", LegacyBatchTrackSpecs.CURVE_29X),
            new Entry("track_curve_32x", LegacyBatchTrackSpecs.CURVE_32X),

            // Row 2 — 45-degree curves
            new Entry("track_curve_45_medium_right", LegacyBatchTrackSpecs.CURVE_45_MEDIUM_RIGHT),
            new Entry("track_curve_45_medium_left", LegacyBatchTrackSpecs.CURVE_45_MEDIUM_LEFT),
            new Entry("track_curve_45_large_right", LegacyBatchTrackSpecs.CURVE_45_LARGE_RIGHT),
            new Entry("track_curve_45_large_left", LegacyBatchTrackSpecs.CURVE_45_LARGE_LEFT),
            new Entry("track_curve_45_very_large_right", LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_RIGHT),

            // Row 3 — remaining 45-degree + Parallel/S
            new Entry("track_curve_45_very_large_left", LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_LEFT),
            new Entry("track_curve_45_super_large_right", LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_RIGHT),
            new Entry("track_curve_45_super_large_left", LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_LEFT),
            new Entry("track_parallel_curve_small_right", LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_RIGHT),
            new Entry("track_parallel_curve_small_left", LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_LEFT),

            // Row 4 — Parallel/S + straight slopes
            new Entry("track_parallel_curve_medium_right", LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT),
            new Entry("track_parallel_curve_medium_left", LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT),
            new Entry("track_parallel_curve_large_right", LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_RIGHT),
            new Entry("track_parallel_curve_large_left", LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_LEFT),
            new Entry("track_slope_small", LegacyBatchTrackSpecs.SLOPE_SMALL),

            // Row 5 — slopes
            new Entry("track_slope_long", LegacyBatchTrackSpecs.SLOPE_LONG),
            new Entry("track_slope_very_long", LegacyBatchTrackSpecs.SLOPE_VERY_LONG),
            new Entry("track_slope_curve_large_right", LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_RIGHT),
            new Entry("track_slope_curve_large_left", LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_LEFT),
            new Entry("track_slope_curve_very_large_right", LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_RIGHT),

            // Row 6 — remaining curved slopes + crossings
            new Entry("track_slope_curve_very_large_left", LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_LEFT),
            new Entry("track_slope_curve_super_large_right", LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_RIGHT),
            new Entry("track_slope_curve_super_large_left", LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_LEFT),
            new Entry("track_crossing_x", LegacyBatchTrackSpecs.CROSSING_X),
            new Entry("track_diamond_crossing", LegacyBatchTrackSpecs.DIAMOND_CROSSING),

            // Row 7 — crossings
            new Entry("track_diamond_crossing_left", LegacyBatchTrackSpecs.DIAMOND_CROSSING_LEFT),
            new Entry("track_double_diamond_crossing", LegacyBatchTrackSpecs.DOUBLE_DIAMOND_CROSSING),
            new Entry("track_diagonal_crossing", LegacyBatchTrackSpecs.DIAGONAL_CROSSING),
            new Entry("track_diagonal_two_ways_crossing", LegacyBatchTrackSpecs.DIAGONAL_TWO_WAYS_CROSSING),
            new Entry("track_diagonal_four_ways_crossing", LegacyBatchTrackSpecs.DIAGONAL_FOUR_WAYS_CROSSING),

            // Row 8 — universal + classic switches
            new Entry("track_universal_crossing", LegacyBatchTrackSpecs.UNIVERSAL_CROSSING),
            new Entry("track_switch_small_right", LegacyBatchTrackSpecs.SWITCH_SMALL_RIGHT),
            new Entry("track_switch_small_left", LegacyBatchTrackSpecs.SWITCH_SMALL_LEFT),
            new Entry("track_switch_medium_right", LegacyBatchTrackSpecs.SWITCH_MEDIUM_RIGHT),
            new Entry("track_switch_medium_left", LegacyBatchTrackSpecs.SWITCH_MEDIUM_LEFT),

            // Row 9 — classic / 45 switches
            new Entry("track_switch_large_right", LegacyBatchTrackSpecs.SWITCH_LARGE_RIGHT),
            new Entry("track_switch_large_left", LegacyBatchTrackSpecs.SWITCH_LARGE_LEFT),
            new Entry("track_switch_very_large_right", LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_RIGHT),
            new Entry("track_switch_very_large_left", LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_LEFT),
            new Entry("track_switch_45_medium_right", LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_RIGHT),

            // Row 10 — remaining switches + diagonal straights
            new Entry("track_switch_45_medium_left", LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_LEFT),
            new Entry("track_switch_parallel_right", LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT),
            new Entry("track_switch_parallel_left", LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT),
            new Entry("track_diagonal_straight_small", LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_SMALL),
            new Entry("track_diagonal_straight_medium", LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM)
    );

    private TrackAllRailsVisualGalleryCommand() {
    }

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_all_tracks_gallery")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> buildGallery(context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        if (ENTRIES.size() != 50) {
            player.sendSystemMessage(Component.literal(
                    "TC all-tracks gallery internal error: expected 50 specs, found "
                            + ENTRIES.size()));
            return 0;
        }

        int minY = level.getMinBuildHeight() + 12;
        int maxY = level.getMaxBuildHeight() - 12;
        int supportY = Math.max(
                minY,
                Math.min(maxY, player.getBlockY() + GALLERY_Y_OFFSET));

        BlockPos origin = new BlockPos(
                player.getBlockX() + START_OFFSET,
                supportY,
                player.getBlockZ() + START_OFFSET);

        Direction facing = Direction.NORTH;

        // Preflight only the exact guide/support footprint plus the gold root marker.
        // This avoids overwriting a build while not demanding enormous empty pads.
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            BlockPos root = rootFor(origin, i);

            for (int part = 0; part < entry.spec().partCount(); part++) {
                BlockPos railPos = entry.spec().offsetForPart(root, facing, part);
                BlockPos supportPos = entry.spec().supportForPart(root, facing, part);

                if (!level.getBlockState(railPos).isAir()) {
                    player.sendSystemMessage(Component.literal(
                            "TC all-tracks gallery aborted: rail footprint not empty for "
                                    + entry.id() + " at " + railPos));
                    return 0;
                }

                // Support may already be solid terrain; that is safe.
                if (supportPos.equals(railPos)) {
                    player.sendSystemMessage(Component.literal(
                            "TC all-tracks gallery internal support overlap: "
                                    + entry.id() + " at " + supportPos));
                    return 0;
                }
            }

            BlockPos marker = markerFor(root);
            if (!level.getBlockState(marker).isAir()) {
                player.sendSystemMessage(Component.literal(
                        "TC all-tracks gallery aborted: marker cell not empty for "
                                + entry.id() + " at " + marker));
                return 0;
            }
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setYRot(180.0F); // NORTH
        fake.setXRot(0.0F);

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t6-s4-d1");
        report.add("COMPLETE 50-SPEC VISUAL INSPECTION GALLERY");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PLACEMENT_MODE=REAL_REGISTERED_ITEMS_USE_ON");
        report.add("ORIENTATION=NORTH");
        report.add("COLUMNS=" + COLUMNS);
        report.add("SPACING=" + SPACING);
        report.add("ORIGIN_SUPPORT=" + origin);
        report.add("SPEC_COUNT=" + ENTRIES.size());
        report.add("");

        int placed = 0;

        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            LegacyBatchTrackSpec spec = entry.spec();
            BlockPos root = rootFor(origin, i);

            // Supply exactly the sturdy supports the production item checks.
            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos supportPos = spec.supportForPart(root, facing, part);
                if (!level.getBlockState(supportPos)
                        .isFaceSturdy(level, supportPos, Direction.UP)) {
                    level.setBlock(
                            supportPos,
                            Blocks.SMOOTH_STONE.defaultBlockState(),
                            2);
                }
            }

            // Gold block marks the canonical logical root area without touching rails.
            BlockPos marker = markerFor(root);
            level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 2);

            LegacyBatchTrackSpec.Endpoint primaryEndpoint = spec.endpoints().get(0);
            BlockPos manualAnchor =
                    "track_diagonal_crossing".equals(spec.id())
                            ? root
                            : spec.offsetForPart(
                                    root,
                                    facing,
                                    primaryEndpoint.part());
            BlockPos clickedSupport = manualAnchor.below();

            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("traincraft", entry.id()));

            if (item == null || item == Items.AIR) {
                report.add("ENTRY index=" + i
                        + " row=" + (i / COLUMNS + 1)
                        + " col=" + (i % COLUMNS + 1)
                        + " id=" + entry.id()
                        + " root=" + root
                        + " RESULT=MISSING_ITEM");
                continue;
            }

            fake.setPos(
                    clickedSupport.getX() + 0.5D,
                    clickedSupport.getY() + 2.0D,
                    clickedSupport.getZ() + 4.5D);
            fake.setYRot(180.0F);
            fake.setXRot(0.0F);

            ItemStack stack = new ItemStack(item);
            fake.setItemInHand(InteractionHand.MAIN_HAND, stack);

            BlockHitResult hit = new BlockHitResult(
                    new Vec3(
                            clickedSupport.getX() + 0.5D,
                            clickedSupport.getY() + 1.0D,
                            clickedSupport.getZ() + 0.5D),
                    Direction.UP,
                    clickedSupport,
                    false);

            InteractionResult result = item.useOn(
                    new UseOnContext(fake, InteractionHand.MAIN_HAND, hit));

            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            int validParts = 0;
            List<String> wrongParts = new ArrayList<>();

            for (int part = 0; part < spec.partCount(); part++) {
                BlockPos railPos = spec.offsetForPart(root, facing, part);
                if (level.getBlockState(railPos).getBlock()
                        instanceof AbstractLegacyBatchRailBlock batch
                        && entry.id().equals(batch.getSpec().id())) {
                    validParts++;
                } else {
                    wrongParts.add(part + "@" + railPos);
                }
            }

            boolean success =
                    result.consumesAction()
                            && validParts == spec.partCount();

            if (success) {
                placed++;
            }

            Bounds bounds = boundsFor(spec, root, facing);

            report.add(String.format(Locale.ROOT,
                    "ENTRY index=%02d row=%d col=%d id=%s root=%s marker=%s"
                            + " parts=%d validParts=%d"
                            + " bounds=[x:%d..%d y:%d..%d z:%d..%d]"
                            + " result=%s consumes=%s success=%s wrongParts=%s",
                    i,
                    i / COLUMNS + 1,
                    i % COLUMNS + 1,
                    entry.id(),
                    root,
                    marker,
                    spec.partCount(),
                    validParts,
                    bounds.minX,
                    bounds.maxX,
                    bounds.minY,
                    bounds.maxY,
                    bounds.minZ,
                    bounds.maxZ,
                    result,
                    result.consumesAction(),
                    success,
                    wrongParts));
        }

        report.add("");
        report.add("PLACED=" + placed);
        report.add("EXPECTED=" + ENTRIES.size());
        report.add("RESULT=" + (placed == ENTRIES.size()
                ? "T6_S4_D1_ALL_TRACKS_GALLERY_READY"
                : "T6_S4_D1_GALLERY_REVIEW_REQUIRED"));

        Path reportFile = writeReport(report);

        // Start above the first row, centered near the three largest long curves.
        BlockPos first = rootFor(origin, 0);
        BlockPos fifth = rootFor(origin, 4);
        double viewX = (first.getX() + fifth.getX()) / 2.0D;
        double viewZ = first.getZ() - 46.0D;

        player.teleportTo(
                level,
                viewX,
                supportY + 42.0D,
                viewZ,
                180.0F,
                58.0F);

        player.sendSystemMessage(Component.literal(
                "TC all-tracks gallery placed " + placed + "/"
                        + ENTRIES.size() + " pieces."));
        player.sendSystemMessage(Component.literal(
                "Row 1 contains Big | Very Big | Super Big | 29x | 32x."));
        player.sendSystemMessage(Component.literal(
                "Each track has a GOLD block near its canonical root. "
                        + "Rows run +Z; columns run +X."));
        player.sendSystemMessage(Component.literal(
                "Gallery map/report: " + reportFile.toAbsolutePath()));
        player.sendSystemMessage(Component.literal(
                "Inspect by flying/walking close to each piece and rotating the camera. "
                        + "Note any track that pops in/out or vanishes while standing on it."));

        return placed == ENTRIES.size()
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private static BlockPos rootFor(BlockPos origin, int index) {
        int col = index % COLUMNS;
        int row = index / COLUMNS;
        return origin.offset(
                col * SPACING,
                1,
                row * SPACING);
    }

    private static BlockPos markerFor(BlockPos root) {
        return root.offset(-5, -1, 5);
    }

    private static Bounds boundsFor(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos pos = spec.offsetForPart(root, facing, part);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record Bounds(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
    }

    private static Path writeReport(List<String> report) {
        try {
            Path dir = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(dir);

            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));

            Path file = dir.resolve(
                    "t6-all-tracks-visual-gallery-"
                            + stamp
                            + "-"
                            + UUID.randomUUID().toString().substring(0, 8)
                            + ".txt");

            Files.write(file, report, StandardCharsets.UTF_8);
            return file;
        } catch (Exception ex) {
            throw new RuntimeException("Could not write all-tracks gallery report", ex);
        }
    }
}
