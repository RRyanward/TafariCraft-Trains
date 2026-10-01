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
import net.minecraft.world.level.block.state.BlockState;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 9.3g-t5-v2 â€” temporary connected snap-pair gallery.
 *
 * Each case:
 *   1. Places source through the real registered item.
 *   2. Recovers the live source assembly/root/facing.
 *   3. Computes the exact selected source endpoint + outward required cell.
 *   4. Clicks the support below that required cell with the incoming track item.
 *   5. LegacyBatchTrackItem performs the actual snap/root/facing selection.
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackSnapPairGalleryCommand {
    private static final int COLUMNS = 5;
    private static final int SPACING = 46;
    private static final int PLATFORM_RADIUS = 18;
    private static final int SOURCE_SCAN_RADIUS = 18;

    private record PairCase(
            String label,
            String sourceId,
            int sourceEndpointPart,
            String incomingId) {
    }

    private record AssemblyInfo(
            AbstractLegacyBatchRailBlock block,
            BlockPos root,
            Direction facing) {
    }

    private static final List<PairCase> CASES = List.of(
            // Row 1 â€” 45 Medium switch branch both placement orders + diag/diag.
            new PairCase("45 Switch L -> Diag M",
                    "track_switch_45_medium_left", 4,
                    "track_diagonal_straight_medium"),
            new PairCase("Diag M -> 45 Switch L",
                    "track_diagonal_straight_medium", 6,
                    "track_switch_45_medium_left"),
            new PairCase("45 Switch R -> Diag M",
                    "track_switch_45_medium_right", 4,
                    "track_diagonal_straight_medium"),
            new PairCase("Diag M -> 45 Switch R",
                    "track_diagonal_straight_medium", 6,
                    "track_switch_45_medium_right"),
            new PairCase("Diag Small -> Diag Medium",
                    "track_diagonal_straight_small", 2,
                    "track_diagonal_straight_medium"),

            // Row 2 â€” Medium 45 curves in both placement orders.
            new PairCase("45 Med L -> Diag M",
                    "track_curve_45_medium_left", 3,
                    "track_diagonal_straight_medium"),
            new PairCase("Diag M -> 45 Med L",
                    "track_diagonal_straight_medium", 6,
                    "track_curve_45_medium_left"),
            new PairCase("45 Med R -> Diag Small",
                    "track_curve_45_medium_right", 3,
                    "track_diagonal_straight_small"),
            new PairCase("Diag Small -> 45 Med R",
                    "track_diagonal_straight_small", 2,
                    "track_curve_45_medium_right"),
            new PairCase("Diag Medium -> Diag Small",
                    "track_diagonal_straight_medium", 6,
                    "track_diagonal_straight_small"),

            // Row 3 â€” larger 45 curve families.
            new PairCase("45 Large L -> Diag M",
                    "track_curve_45_large_left", 7,
                    "track_diagonal_straight_medium"),
            new PairCase("45 Large R -> Diag M",
                    "track_curve_45_large_right", 7,
                    "track_diagonal_straight_medium"),
            new PairCase("45 Very Large L -> Diag M",
                    "track_curve_45_very_large_left", 10,
                    "track_diagonal_straight_medium"),
            new PairCase("45 Very Large R -> Diag M",
                    "track_curve_45_very_large_right", 10,
                    "track_diagonal_straight_medium"),
            new PairCase("45 Super Large L -> Diag M",
                    "track_curve_45_super_large_left", 14,
                    "track_diagonal_straight_medium"),

            // Row 4 â€” final 45 + centered crossing both orders + 2/4-way.
            new PairCase("45 Super Large R -> Diag M",
                    "track_curve_45_super_large_right", 14,
                    "track_diagonal_straight_medium"),
            new PairCase("Diagonal Crossing -> Small",
                    "track_diagonal_crossing", 1,
                    "track_diagonal_straight_small"),
            new PairCase("Small -> Diagonal Crossing",
                    "track_diagonal_straight_small", 2,
                    "track_diagonal_crossing"),
            new PairCase("2-Way -> Diag M",
                    "track_diagonal_two_ways_crossing", 1,
                    "track_diagonal_straight_medium"),
            new PairCase("4-Way -> Diag M",
                    "track_diagonal_four_ways_crossing", 1,
                    "track_diagonal_straight_medium"),

            // Row 5 â€” diamond family + universal.
            new PairCase("Diamond -> Diag M",
                    "track_diamond_crossing", 1,
                    "track_diagonal_straight_medium"),
            new PairCase("Diag M -> Diamond",
                    "track_diagonal_straight_medium", 6,
                    "track_diamond_crossing"),
            new PairCase("Diamond Left -> Diag M",
                    "track_diamond_crossing_left", 1,
                    "track_diagonal_straight_medium"),
            new PairCase("Double Diamond -> Diag M",
                    "track_double_diamond_crossing", 1,
                    "track_diagonal_straight_medium"),
            new PairCase("Universal -> Diag M",
                    "track_universal_crossing", 1,
                    "track_diagonal_straight_medium")
    );

    private TrackSnapPairGalleryCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_snap_pair_gallery")
                        .executes(context ->
                                buildGallery(context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 32;
        int floorY = Math.max(minY, Math.min(maxY, player.getBlockY() + 28));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 24,
                floorY,
                player.getBlockZ() + 24);

        // Do not overwrite an existing build.
        for (int i = 0; i < CASES.size(); i++) {
            BlockPos anchor = anchorFor(origin, i);
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    BlockPos floor = anchor.offset(dx, 0, dz);
                    for (int dy = 0; dy <= 3; dy++) {
                        BlockPos check = floor.above(dy);
                        if (!level.getBlockState(check).isAir()) {
                            player.sendSystemMessage(Component.literal(
                                    "TC pair gallery aborted: target area is not empty at "
                                            + check
                                            + ". Move to a large open area and run again."));
                            return 0;
                        }
                    }
                }
            }
        }

        // Support pads.
        for (int i = 0; i < CASES.size(); i++) {
            BlockPos anchor = anchorFor(origin, i);
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    level.setBlock(
                            anchor.offset(dx, 0, dz),
                            Blocks.SMOOTH_STONE.defaultBlockState(),
                            2);
                }
            }
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setXRot(0.0F);
        fake.setYRot(180.0F); // north-facing manual baseline

        // STEP_9_3G_T5_D2_VISUAL_OWNERSHIP_STATE_REPORT
        List<String> visualReport = new ArrayList<>();
                // STEP_9_3G_T5_D20_S4C_R3_VISUAL_REPORT_ALIGNMENT
        visualReport.add("TAFARICRAFT - TRAINS STEP 9.3g-t5-d20 s4c-r3 CONNECTED PAIR VISUAL STATE");
        visualReport.add("Cases=" + CASES.size());
        visualReport.add("");

        int passed = 0;
        int failed = 0;

        for (int i = 0; i < CASES.size(); i++) {
            PairCase test = CASES.get(i);
            BlockPos anchor = anchorFor(origin, i);

            boolean sourcePlaced = placeManual(
                    level, fake, test.sourceId(), anchor);

            if (!sourcePlaced) {
                failed++;
                player.sendSystemMessage(Component.literal(
                        "PAIR FAIL " + (i + 1) + ": " + test.label()
                                + " [source placement]"));
                continue;
            }

            AssemblyInfo source = findAssembly(
                    level, anchor.above(), test.sourceId());

            if (source == null) {
                failed++;
                player.sendSystemMessage(Component.literal(
                        "PAIR FAIL " + (i + 1) + ": " + test.label()
                                + " [source assembly not found]"));
                continue;
            }

            LegacyBatchTrackSpec.Endpoint endpoint = null;
            for (LegacyBatchTrackSpec.Endpoint candidate
                    : source.block().getSpec().endpoints()) {
                if (candidate.part() == test.sourceEndpointPart()) {
                    endpoint = candidate;
                    break;
                }
            }

            if (endpoint == null) {
                failed++;
                player.sendSystemMessage(Component.literal(
                        "PAIR FAIL " + (i + 1) + ": " + test.label()
                                + " [missing source endpoint p"
                                + test.sourceEndpointPart() + "]"));
                continue;
            }

            LegacyBatchTrackSpec sourceSpec = source.block().getSpec();
            BlockPos logicalSegment = sourceSpec.endpointPosition(
                    source.root(), source.facing(), endpoint);
            Direction outward = sourceSpec.rotateDirection(
                    source.facing(), endpoint.outward());

            // This is the exact ground-request cell LegacyBatchTrackItem expects:
            // connector.segment.relative(connector.outward).
            BlockPos requiredIncomingEndpoint =
                    logicalSegment.relative(outward);
            BlockPos clickSupport = requiredIncomingEndpoint.below();

            boolean incomingPlaced = placeAtSupport(
                    level, fake, test.incomingId(), clickSupport);

            if (!incomingPlaced) {
                failed++;
                player.sendSystemMessage(Component.literal(
                        "PAIR FAIL " + (i + 1) + ": " + test.label()
                                + " [incoming useOn rejected]"
                                + " sourceEndpoint=" + logicalSegment
                                + " outward=" + outward
                                + " required=" + requiredIncomingEndpoint));
                continue;
            }

            AssemblyInfo incoming = findAssembly(
                    level, requiredIncomingEndpoint, test.incomingId());

            if (incoming == null) {
                failed++;
                player.sendSystemMessage(Component.literal(
                        "PAIR FAIL " + (i + 1) + ": " + test.label()
                                + " [incoming assembly not found after CONSUME]"));
                continue;
            }

            appendVisualOwnershipReport(
                    visualReport, i + 1, test, level,
                    source, endpoint, logicalSegment, outward,
                    requiredIncomingEndpoint, incoming);
            passed++;
        }

        Path visualReportPath = null;
        try {
            Path reportDir = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(reportDir);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                    .format(LocalDateTime.now());
            visualReportPath = reportDir.resolve(
                    "snap-pair-visual-state-" + stamp + ".txt");
            visualReport.add("");
            visualReport.add("SUMMARY PASS=" + passed
                    + " FAIL=" + failed + " TOTAL=" + CASES.size());
            Files.write(visualReportPath, visualReport, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal(
                    "TC visual state report write failed: "
                            + ex.getClass().getSimpleName() + ": "
                            + String.valueOf(ex.getMessage())));
        }

        int rows = (CASES.size() + COLUMNS - 1) / COLUMNS;
        double centerX = origin.getX() + ((COLUMNS - 1) * SPACING) / 2.0D;
        double centerZ = origin.getZ() + ((rows - 1) * SPACING) / 2.0D;

        player.teleportTo(
                level,
                centerX,
                floorY + 38.0D,
                centerZ,
                180.0F,
                72.0F);

        player.sendSystemMessage(Component.literal(
                "TC connected snap-pair gallery: PASS=" + passed
                        + " FAIL=" + failed + " TOTAL=" + CASES.size()));
        if (visualReportPath != null) {
            Path finalVisualReportPath = visualReportPath;
            player.sendSystemMessage(Component.literal(
                    "TC visual state report: "
                            + finalVisualReportPath.toAbsolutePath()));
        }
        player.sendSystemMessage(Component.literal(
                "Row 1: SwL->M | M->SwL | SwR->M | M->SwR | Small->Medium"));
        player.sendSystemMessage(Component.literal(
                "Row 2: Med45L->M | M->Med45L | Med45R->Small | Small->Med45R | M->Small"));
        player.sendSystemMessage(Component.literal(
                "Row 3: LargeL->M | LargeR->M | VeryLargeL->M | VeryLargeR->M | SuperLargeL->M"));
        player.sendSystemMessage(Component.literal(
                "Row 4: SuperLargeR->M | DiagCross->Small | Small->DiagCross | 2Way->M | 4Way->M"));
        player.sendSystemMessage(Component.literal(
                "Row 5: Diamond->M | M->Diamond | DiamondLeft->M | DoubleDiamond->M | Universal->M"));

        return failed == 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static boolean placeManual(
            ServerLevel level,
            FakePlayer fake,
            String id,
            BlockPos support) {
        return placeAtSupport(level, fake, id, support);
    }

    private static boolean placeAtSupport(
            ServerLevel level,
            FakePlayer fake,
            String id,
            BlockPos support) {

        Item item = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("traincraft", id));
        if (item == null || item == Items.AIR) {
            return false;
        }

        fake.setPos(
                support.getX() + 0.5D,
                support.getY() + 2.0D,
                support.getZ() + 4.5D);
        fake.setXRot(0.0F);
        fake.setYRot(180.0F);

        ItemStack stack = new ItemStack(item);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockHitResult hit = new BlockHitResult(
                new Vec3(
                        support.getX() + 0.5D,
                        support.getY() + 1.0D,
                        support.getZ() + 0.5D),
                Direction.UP,
                support,
                false);

        InteractionResult result = item.useOn(
                new UseOnContext(fake, InteractionHand.MAIN_HAND, hit));

        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result.consumesAction();
    }

    private static AssemblyInfo findAssembly(
            ServerLevel level,
            BlockPos center,
            String id) {

        AssemblyInfo best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (int dx = -SOURCE_SCAN_RADIUS; dx <= SOURCE_SCAN_RADIUS; dx++) {
            for (int dz = -SOURCE_SCAN_RADIUS; dz <= SOURCE_SCAN_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos probe = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(probe);

                    if (!(state.getBlock()
                            instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }
                    if (!id.equals(batch.getSpec().id())) {
                        continue;
                    }

                    BlockPos root = batch.getAssemblyRoot(probe, state);
                    Direction facing = batch.getAssemblyFacing(state);

                    double distanceSq = root.distSqr(center);
                    if (distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        best = new AssemblyInfo(batch, root, facing);
                    }
                }
            }
        }

        return best;
    }

    // STEP_9_3G_T5_D2_VISUAL_OWNERSHIP_STATE_REPORT
    private static void appendVisualOwnershipReport(
            List<String> out,
            int index,
            PairCase test,
            ServerLevel level,
            AssemblyInfo source,
            LegacyBatchTrackSpec.Endpoint sourceEndpoint,
            BlockPos sourceLogicalSegment,
            Direction sourceOutward,
            BlockPos requiredIncomingEndpoint,
            AssemblyInfo incoming) {

        out.add("================================================================================");
        out.add("PAIR " + index + " " + test.label());
        out.add("SOURCE id=" + test.sourceId()
                + " root=" + source.root()
                + " facing=" + source.facing()
                + " endpointPart=" + sourceEndpoint.part()
                + " physicalEndpoint="
                + source.block().getSpec().offsetForPart(
                        source.root(), source.facing(), sourceEndpoint.part())
                + " logicalEndpoint=" + sourceLogicalSegment
                + " outward=" + sourceOutward
                + " requiredIncomingEndpoint=" + requiredIncomingEndpoint);

        appendDiagonalState(out, "SOURCE", level, source);

        LegacyBatchTrackSpec incomingSpec = incoming.block().getSpec();
        out.add("INCOMING id=" + test.incomingId()
                + " root=" + incoming.root()
                + " facing=" + incoming.facing());

        BlockPos reportRequired = correctedVisualReportRequiredCell(
                test, source, sourceEndpoint, requiredIncomingEndpoint);
        boolean r3Corrected = !reportRequired.equals(requiredIncomingEndpoint);
        out.add("REPORT_REQUIRED_RAW=" + requiredIncomingEndpoint
                + " effective=" + reportRequired
                + " mode=" + (r3Corrected
                        ? "S4C_R3_SMALL_P2_CORRECTED"
                        : "RAW_SOURCE_REQUIRED"));

        int matches = 0;
        for (LegacyBatchTrackSpec.Endpoint endpoint : incomingSpec.endpoints()) {
            BlockPos logical = incomingSpec.endpointPosition(
                    incoming.root(), incoming.facing(), endpoint);
            if (!logical.equals(reportRequired)) {
                continue;
            }
            matches++;
            Direction outward = incomingSpec.rotateDirection(
                    incoming.facing(), endpoint.outward());
            out.add("INCOMING_MATCH endpointPart=" + endpoint.part()
                    + " physicalEndpoint="
                    + incomingSpec.offsetForPart(
                            incoming.root(), incoming.facing(), endpoint.part())
                    + " logicalEndpoint=" + logical
                    + " outward=" + outward);
        }
        out.add("INCOMING_MATCH_COUNT=" + matches);

        String matchClass;
        if (matches > 0) {
            matchClass = r3Corrected
                    ? "S4C_R3_CORRECTED_EXACT_LOGICAL"
                    : "EXACT_LOGICAL";
        } else if (isS4cVisualOwnerGeometryPair(test)) {
            matchClass = "S4C_VISUAL_OWNER_GEOMETRY_EXPECTED";
        } else {
            matchClass = "UNRESOLVED_LOGICAL_MISMATCH";
        }
        out.add("INCOMING_MATCH_CLASS=" + matchClass);

        appendDiagonalState(out, "INCOMING", level, incoming);
        out.add("");
    }

    // STEP_9_3G_T5_D20_S4C_R3_VISUAL_REPORT_ALIGNMENT
    private static BlockPos correctedVisualReportRequiredCell(
            PairCase test,
            AssemblyInfo source,
            LegacyBatchTrackSpec.Endpoint sourceEndpoint,
            BlockPos rawRequired) {
        if (!"track_diagonal_straight_small".equals(test.sourceId())
                || sourceEndpoint.part() != 2
                || !"track_diagonal_crossing".equals(test.incomingId())) {
            return rawRequired;
        }

        int dx;
        int dz;
        switch (source.facing()) {
            case NORTH -> { dx = 1; dz = 1; }
            case EAST  -> { dx = -1; dz = 1; }
            case SOUTH -> { dx = -1; dz = -1; }
            case WEST  -> { dx = 1; dz = -1; }
            default -> { return rawRequired; }
        }
        return rawRequired.offset(dx, 0, dz);
    }

    private static boolean isS4cVisualOwnerGeometryPair(PairCase test) {
        boolean sourceCross = isS4cRestoredVisualCrossing(test.sourceId());
        boolean incomingCross = isS4cRestoredVisualCrossing(test.incomingId());
        boolean sourceMedium =
                "track_diagonal_straight_medium".equals(test.sourceId());
        boolean incomingMedium =
                "track_diagonal_straight_medium".equals(test.incomingId());
        return (sourceCross && incomingMedium)
                || (sourceMedium && incomingCross);
    }

    private static boolean isS4cRestoredVisualCrossing(String id) {
        return "track_diagonal_two_ways_crossing".equals(id)
                || "track_diagonal_four_ways_crossing".equals(id)
                || "track_diamond_crossing".equals(id)
                || "track_diamond_crossing_left".equals(id)
                || "track_double_diamond_crossing".equals(id)
                || "track_universal_crossing".equals(id);
    }

    private static void appendDiagonalState(
            List<String> out,
            String role,
            ServerLevel level,
            AssemblyInfo assembly) {

        String id = assembly.block().getSpec().id();
        int visualPart;
        if ("track_diagonal_straight_small".equals(id)) {
            visualPart = 2;
        } else if ("track_diagonal_straight_medium".equals(id)) {
            visualPart = 6;
        } else {
            out.add(role + "_DIAGONAL_VISUAL=N/A");
            return;
        }

        LegacyBatchTrackSpec spec = assembly.block().getSpec();
        BlockPos root = assembly.root();
        Direction facing = assembly.facing();
        BlockPos visualPos = spec.offsetForPart(root, facing, visualPart);
        BlockState visualState = level.getBlockState(visualPos);
        BlockState rootState = level.getBlockState(root);

        int flaggedStates = 0;
        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos pos = spec.offsetForPart(root, facing, part);
            BlockState state = level.getBlockState(pos);
            if (state.toString().contains("original_visual_root=true")) {
                flaggedStates++;
            }
        }

        out.add(role + "_DIAGONAL_VISUAL id=" + id
                + " root=" + root
                + " facing=" + facing
                + " visualPart=" + visualPart
                + " visualPos=" + visualPos
                + " visualDeltaFromRoot=("
                + (visualPos.getX() - root.getX()) + ","
                + (visualPos.getZ() - root.getZ()) + ")"
                + " flaggedStateCount=" + flaggedStates
                + " rootState=" + rootState
                + " visualState=" + visualState);
    }

    private static BlockPos anchorFor(BlockPos origin, int index) {
        int col = index % COLUMNS;
        int row = index / COLUMNS;
        return origin.offset(col * SPACING, 0, row * SPACING);
    }
}