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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 9.3g-t6-d4 â€” diagonal core/visual gallery.
 *
 * DIAGNOSTIC ONLY.
 *
 * This is the first visual audit after t6-s1 established one physical core for
 * every exposed endpoint.  It tests the two straight-diagonal families first:
 *
 *   Small  â€” NORTH/EAST/SOUTH/WEST
 *   Medium â€” NORTH/EAST/SOUTH/WEST
 *
 * Each family gets two rows:
 *   ITEM row = real item useOn() placement, normal p0 visual ownership.
 *   TRUE row = same physical assembly with original_visual_root=true forced
 *              deterministically so the far endpoint owner renders.
 *
 * Support colors:
 *   GOLD_BLOCK    = first exposed endpoint core
 *   EMERALD_BLOCK = second exposed endpoint core
 *   SMOOTH_STONE  = internal guide part
 *
 * The runtime report independently recomputes CORE_MATCH_COUNT for every
 * exposed endpoint in every example.  Every case must remain exactly 1.
 *
 * No production source/resource file is modified by this command.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackT6DiagonalCoreVisualGalleryCommand {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final int COLUMN_SPACING = 20;
    private static final int ROW_SPACING = 24;

    private record Family(
            String itemId,
            String blockId,
            String label) {
    }

    private record Row(
            Family family,
            boolean forceTrueVisualRoot,
            String label) {
    }

    private static final List<Family> FAMILIES = List.of(
            new Family(
                    "track_diagonal_straight_small",
                    "track_diagonal_straight_small_segment",
                    "Small"),
            new Family(
                    "track_diagonal_straight_medium",
                    "track_diagonal_straight_medium_segment",
                    "Medium")
    );

    private static final List<Row> ROWS = List.of(
            new Row(FAMILIES.get(0), false, "Small ITEM / p0 owner"),
            new Row(FAMILIES.get(0), true,  "Small TRUE / far owner"),
            new Row(FAMILIES.get(1), false, "Medium ITEM / p0 owner"),
            new Row(FAMILIES.get(1), true,  "Medium TRUE / far owner")
    );

    private static final List<Direction> FACINGS = List.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );

    private TrackT6DiagonalCoreVisualGalleryCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_t6_diagonal_visual_gallery")
                        .executes(context -> buildGallery(
                                context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 28;
        int railY = Math.max(
                minY + 1,
                Math.min(maxY, player.getBlockY() + 20));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 24,
                railY,
                player.getBlockZ() + 24);

        List<String> report = new ArrayList<>();
        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t6-d4");
        report.add("DIAGONAL ONE-CORE VISUAL GALLERY");
        report.add("WORLD_MUTATION=DIAGNOSTIC_GALLERY_ONLY");
        report.add("ROWS=Small ITEM; Small TRUE; Medium ITEM; Medium TRUE");
        report.add("COLUMNS=NORTH; EAST; SOUTH; WEST");
        report.add("");

        // Resolve blocks/items and prove all endpoint cores are structurally one.
        for (Row row : ROWS) {
            AbstractLegacyBatchRailBlock block =
                    resolveBatchBlock(row.family().blockId());
            if (block == null) {
                player.sendSystemMessage(Component.literal(
                        "t6-d4 missing/cannot-cast block: "
                                + row.family().blockId()));
                return 0;
            }

            LegacyBatchTrackSpec spec = block.getSpec();
            for (Direction facing : FACINGS) {
                for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                    int coreCount = coreMatchCount(
                            spec, BlockPos.ZERO, facing, endpoint);
                    if (coreCount != 1) {
                        player.sendSystemMessage(Component.literal(
                                "t6-d4 structural safety stop: "
                                        + spec.id()
                                        + " facing=" + facing.getName()
                                        + " endpointPart=" + endpoint.part()
                                        + " coreCount=" + coreCount));
                        return 0;
                    }
                }
            }
        }

        // Preflight every exact rail/support location. We will not overwrite an
        // existing user build.
        Set<BlockPos> writeSet = new HashSet<>();
        for (int rowIndex = 0; rowIndex < ROWS.size(); rowIndex++) {
            Row row = ROWS.get(rowIndex);
            AbstractLegacyBatchRailBlock block =
                    resolveBatchBlock(row.family().blockId());
            LegacyBatchTrackSpec spec = block.getSpec();

            for (int column = 0; column < FACINGS.size(); column++) {
                Direction facing = FACINGS.get(column);
                BlockPos root = rootFor(origin, rowIndex, column);

                for (int part = 0; part < spec.partCount(); part++) {
                    BlockPos rail = spec.offsetForPart(root, facing, part);
                    BlockPos support = rail.below();
                    writeSet.add(rail);
                    writeSet.add(support);
                }
            }
        }

        for (BlockPos pos : writeSet) {
            if (!level.getBlockState(pos).isAir()) {
                player.sendSystemMessage(Component.literal(
                        "TC t6 diagonal gallery aborted: target cell is not air at "
                                + pos
                                + ". Move to a fresh open area and rerun "
                                + "/tc_t6_diagonal_visual_gallery."));
                return 0;
            }
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setXRot(0.0F);

        int examplesPlaced = 0;
        int endpointCases = 0;
        int endpointCorePass = 0;

        for (int rowIndex = 0; rowIndex < ROWS.size(); rowIndex++) {
            Row row = ROWS.get(rowIndex);
            AbstractLegacyBatchRailBlock block =
                    resolveBatchBlock(row.family().blockId());
            LegacyBatchTrackSpec spec = block.getSpec();

            for (int column = 0; column < FACINGS.size(); column++) {
                Direction facing = FACINGS.get(column);
                BlockPos root = rootFor(origin, rowIndex, column);

                // Identify exposed endpoint part numbers.
                Set<Integer> endpointParts = new HashSet<>();
                for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                    endpointParts.add(endpoint.part());
                }

                // Support first. The first endpoint is gold and the final
                // endpoint is emerald; internal guide parts are smooth stone.
                int firstEndpointPart = spec.endpoints().get(0).part();
                int lastEndpointPart =
                        spec.endpoints().get(spec.endpoints().size() - 1).part();

                for (int part = 0; part < spec.partCount(); part++) {
                    BlockPos rail = spec.offsetForPart(root, facing, part);
                    BlockPos support = rail.below();

                    Block supportBlock = Blocks.SMOOTH_STONE;
                    if (part == firstEndpointPart) {
                        supportBlock = Blocks.GOLD_BLOCK;
                    } else if (part == lastEndpointPart) {
                        supportBlock = Blocks.EMERALD_BLOCK;
                    }

                    level.setBlock(
                            support,
                            supportBlock.defaultBlockState(),
                            2);
                }

                boolean placementOk;
                String placementMode;

                if (!row.forceTrueVisualRoot()) {
                    placementMode = "ITEM_USE_ON";
                    placementOk = placeViaRealItem(
                            level,
                            fake,
                            row.family(),
                            root,
                            facing);
                } else {
                    placementMode = "DIRECT_TRUE_VISUAL_OWNER";
                    placementOk = placeTrueVisualOwnership(
                            level,
                            block,
                            spec,
                            root,
                            facing);
                }

                if (!placementOk) {
                    player.sendSystemMessage(Component.literal(
                            "TC t6 diagonal gallery placement failed: row="
                                    + row.label()
                                    + " facing=" + facing.getName()));
                    return 0;
                }

                boolean exactAssembly = true;
                for (int part = 0; part < spec.partCount(); part++) {
                    BlockPos rail = spec.offsetForPart(root, facing, part);
                    if (level.getBlockState(rail).getBlock() != block) {
                        exactAssembly = false;
                        break;
                    }
                }

                if (!exactAssembly) {
                    player.sendSystemMessage(Component.literal(
                            "TC t6 diagonal gallery verification failed: "
                                    + spec.id()
                                    + " facing=" + facing.getName()
                                    + " mode=" + placementMode));
                    return 0;
                }

                examplesPlaced++;
                report.add("CASE"
                        + " row=" + row.label()
                        + " id=" + spec.id()
                        + " facing=" + facing.getName()
                        + " root=" + pos(root)
                        + " mode=" + placementMode
                        + " parts=" + spec.partCount());

                for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                    endpointCases++;
                    BlockPos logical =
                            spec.endpointPosition(root, facing, endpoint);
                    BlockPos physical =
                            spec.offsetForPart(root, facing, endpoint.part());
                    int coreCount =
                            coreMatchCount(spec, root, facing, endpoint);

                    if (coreCount == 1) {
                        endpointCorePass++;
                    }

                    report.add("  ENDPOINT"
                            + " part=" + endpoint.part()
                            + " logical=" + pos(logical)
                            + " physical=" + pos(physical)
                            + " CORE_MATCH_COUNT=" + coreCount
                            + " support="
                            + level.getBlockState(physical.below())
                                    .getBlock()
                                    .builtInRegistryHolder()
                                    .key()
                                    .location());
                }

                report.add("");
            }
        }

        boolean corePass =
                endpointCases == endpointCorePass;

        report.add("=== FINAL SUMMARY ===");
        report.add("EXAMPLES_PLACED=" + examplesPlaced);
        report.add("EXPECTED_EXAMPLES=16");
        report.add("ENDPOINT_CASES=" + endpointCases);
        report.add("ENDPOINT_CORE_ONE=" + endpointCorePass);
        report.add("ENDPOINT_CORE_NON_ONE="
                + (endpointCases - endpointCorePass));
        report.add("STRUCTURAL_RESULT="
                + (corePass ? "PASS" : "FAIL"));
        report.add("VISUAL_RESULT=MANUAL_SCREENSHOT_REVIEW_REQUIRED");
        report.add("RESULT="
                + (corePass
                ? "T6_D4_DIAGONAL_CORE_GALLERY_READY_FOR_VISUAL_REVIEW"
                : "FAIL"));

        String stamp = STAMP.format(LocalDateTime.now());
        Path output = Path.of(
                "t6-diagonal-core-visual-gallery-" + stamp + ".txt")
                .toAbsolutePath();

        try {
            Files.write(
                    output,
                    report,
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            player.sendSystemMessage(Component.literal(
                    "TC t6 diagonal gallery report write failed: " + ex));
            return 0;
        }

        // Teleport above/aside so all four rows are immediately visible.
        double centerX =
                origin.getX() + (COLUMN_SPACING * 1.5D);
        double centerZ =
                origin.getZ() + (ROW_SPACING * 1.5D);

        player.teleportTo(
                level,
                centerX,
                railY + 30.0D,
                centerZ,
                180.0F,
                70.0F);

        player.sendSystemMessage(Component.literal(
                "TC t6 diagonal core/visual gallery placed 16/16 examples."));
        player.sendSystemMessage(Component.literal(
                "Rows: Small ITEM | Small TRUE | Medium ITEM | Medium TRUE"));
        player.sendSystemMessage(Component.literal(
                "Columns: NORTH | EAST | SOUTH | WEST"));
        player.sendSystemMessage(Component.literal(
                "Gold/emerald supports are the two physical endpoint cores."));
        player.sendSystemMessage(Component.literal(
                "Take overhead + low side screenshots. Report: " + output));

        return Command.SINGLE_SUCCESS;
    }

    private static boolean placeViaRealItem(
            ServerLevel level,
            FakePlayer fake,
            Family family,
            BlockPos root,
            Direction facing) {

        Item item = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("traincraft", family.itemId()));
        if (item == null || item == Items.AIR) {
            return false;
        }

        fake.setPos(
                root.getX() + 0.5D,
                root.getY() + 2.0D,
                root.getZ() + 4.5D);
        fake.setXRot(0.0F);
        fake.setYRot(yawFor(facing));

        ItemStack stack = new ItemStack(item);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockPos support = root.below();
        BlockHitResult hit = new BlockHitResult(
                new Vec3(
                        support.getX() + 0.5D,
                        support.getY() + 1.0D,
                        support.getZ() + 0.5D),
                Direction.UP,
                support,
                false);

        InteractionResult result = item.useOn(
                new UseOnContext(
                        fake,
                        InteractionHand.MAIN_HAND,
                        hit));

        fake.setItemInHand(
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);

        return result.consumesAction();
    }

    private static boolean placeTrueVisualOwnership(
            ServerLevel level,
            AbstractLegacyBatchRailBlock block,
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing) {

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos rail = spec.offsetForPart(root, facing, part);
            BlockState state =
                    block.stateForPart(facing, part, false);
            state = withOriginalVisualRoot(state, true);
            if (!level.setBlock(rail, state, 3)) {
                return false;
            }
        }

        return true;
    }

    private static BlockState withOriginalVisualRoot(
            BlockState state,
            boolean value) {
        for (Property<?> property : state.getProperties()) {
            if ("original_visual_root".equals(property.getName())
                    && property instanceof BooleanProperty booleanProperty) {
                return state.setValue(booleanProperty, value);
            }
        }
        return state;
    }

    private static AbstractLegacyBatchRailBlock resolveBatchBlock(
            String blockId) {
        Block block = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("traincraft", blockId));
        if (block instanceof AbstractLegacyBatchRailBlock batch) {
            return batch;
        }
        return null;
    }

    private static int coreMatchCount(
            LegacyBatchTrackSpec spec,
            BlockPos root,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint) {
        BlockPos logical =
                spec.endpointPosition(root, facing, endpoint);
        int count = 0;

        for (int part = 0; part < spec.partCount(); part++) {
            BlockPos physical =
                    spec.offsetForPart(root, facing, part);
            if (physical.equals(logical)) {
                count++;
            }
        }

        return count;
    }

    private static BlockPos rootFor(
            BlockPos origin,
            int row,
            int column) {
        return origin.offset(
                column * COLUMN_SPACING,
                0,
                row * ROW_SPACING);
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 180.0F;
        };
    }

    private static String pos(BlockPos pos) {
        return "("
                + pos.getX() + ","
                + pos.getY() + ","
                + pos.getZ() + ")";
    }
}
