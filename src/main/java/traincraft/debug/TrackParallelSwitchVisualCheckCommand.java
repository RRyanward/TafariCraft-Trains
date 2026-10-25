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
import traincraft.block.track.LegacyBatchSwitchRailBlock;
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
 * Step 9.3g-t6-s4-d2 — Parallel-switch visual orientation isolator.
 *
 * Places BOTH Parallel Right/Left in all four facings and in both inactive
 * and active states. The real registered item/useOn placement path is used.
 *
 * Support colors:
 *   smooth stone = canonical straight/common x=0 cells
 *   cyan concrete = canonical offset/branch cells (x != 0)
 *
 * Marker:
 *   RED base  = Parallel Right
 *   BLUE base = Parallel Left
 *   IRON top  = inactive/straight
 *   GOLD top  = active/diverging
 *
 * Command:
 *   /tc_parallel_switch_visual_check
 */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class TrackParallelSwitchVisualCheckCommand {
    private static final int COLUMN_SPACING = 34;
    private static final int ROW_SPACING = 34;
    private static final int START_OFFSET = 28;
    private static final int Y_OFFSET = 12;

    private record CaseDef(
            String id,
            LegacyBatchTrackSpec spec,
            boolean right,
            Direction facing,
            boolean active) {
    }

    private static final List<CaseDef> CASES = buildCases();

    private TrackParallelSwitchVisualCheckCommand() {
    }

    private static List<CaseDef> buildCases() {
        List<CaseDef> out = new ArrayList<>();
        Direction[] facings = {
                Direction.NORTH,
                Direction.EAST,
                Direction.SOUTH,
                Direction.WEST
        };

        // row 1: Right inactive
        for (Direction facing : facings) {
            out.add(new CaseDef(
                    "track_switch_parallel_right",
                    LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT,
                    true,
                    facing,
                    false));
        }

        // row 2: Right active
        for (Direction facing : facings) {
            out.add(new CaseDef(
                    "track_switch_parallel_right",
                    LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT,
                    true,
                    facing,
                    true));
        }

        // row 3: Left inactive
        for (Direction facing : facings) {
            out.add(new CaseDef(
                    "track_switch_parallel_left",
                    LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT,
                    false,
                    facing,
                    false));
        }

        // row 4: Left active
        for (Direction facing : facings) {
            out.add(new CaseDef(
                    "track_switch_parallel_left",
                    LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT,
                    false,
                    facing,
                    true));
        }

        return List.copyOf(out);
    }

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_parallel_switch_visual_check")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> build(
                                context.getSource().getPlayerOrException()))
        );
    }

    private static int build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int supportY = Math.max(
                level.getMinBuildHeight() + 12,
                Math.min(
                        level.getMaxBuildHeight() - 12,
                        player.getBlockY() + Y_OFFSET));

        BlockPos origin = new BlockPos(
                player.getBlockX() + START_OFFSET,
                supportY,
                player.getBlockZ() + START_OFFSET);

        // Preflight the exact case footprints plus markers.
        for (int i = 0; i < CASES.size(); i++) {
            CaseDef c = CASES.get(i);
            BlockPos root = rootFor(origin, i);

            for (int part = 0; part < c.spec().partCount(); part++) {
                BlockPos railPos = c.spec().offsetForPart(
                        root,
                        c.facing(),
                        part);

                if (!level.getBlockState(railPos).isAir()) {
                    player.sendSystemMessage(Component.literal(
                            "Parallel-switch visual check aborted: occupied rail cell "
                                    + railPos + " for case " + caseLabel(i, c)));
                    return 0;
                }
            }

            BlockPos markerBase = markerBase(root);
            if (!level.getBlockState(markerBase).isAir()
                    || !level.getBlockState(markerBase.above()).isAir()) {
                player.sendSystemMessage(Component.literal(
                        "Parallel-switch visual check aborted: marker area occupied at "
                                + markerBase));
                return 0;
            }
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        List<String> report = new ArrayList<>();

        report.add("TAFARICRAFT - TRAINS STEP 9.3g-t6-s4-d2");
        report.add("PARALLEL SWITCH VISUAL ORIENTATION ISOLATOR");
        report.add("PROJECT_MUTATION=TEMPORARY_DIAGNOSTIC_ONLY");
        report.add("PLACEMENT_MODE=REAL_REGISTERED_ITEMS_USE_ON");
        report.add("CASE_COUNT=" + CASES.size());
        report.add("SUPPORT_STRAIGHT_COMMON=SMOOTH_STONE");
        report.add("SUPPORT_OFFSET_BRANCH=CYAN_CONCRETE");
        report.add("MARKER_RIGHT=RED_CONCRETE");
        report.add("MARKER_LEFT=BLUE_CONCRETE");
        report.add("MARKER_INACTIVE=IRON_BLOCK");
        report.add("MARKER_ACTIVE=GOLD_BLOCK");
        report.add("");

        int successCount = 0;

        for (int i = 0; i < CASES.size(); i++) {
            CaseDef c = CASES.get(i);
            BlockPos root = rootFor(origin, i);

            // Color the exact production support footprint. Canonical x==0 is
            // the straight/common spine. Non-zero canonical x is the offset
            // branch footprint. This makes a mirrored/rotated visual obvious.
            for (int part = 0; part < c.spec().partCount(); part++) {
                BlockPos support = c.spec().supportForPart(
                        root,
                        c.facing(),
                        part);

                int canonicalX = c.spec().pointForPart(part).x;
                BlockState supportState = canonicalX == 0
                        ? Blocks.SMOOTH_STONE.defaultBlockState()
                        : Blocks.CYAN_CONCRETE.defaultBlockState();

                level.setBlock(support, supportState, 2);
            }

            BlockPos markerBase = markerBase(root);
            level.setBlock(
                    markerBase,
                    c.right()
                            ? Blocks.RED_CONCRETE.defaultBlockState()
                            : Blocks.BLUE_CONCRETE.defaultBlockState(),
                    2);
            level.setBlock(
                    markerBase.above(),
                    c.active()
                            ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.IRON_BLOCK.defaultBlockState(),
                    2);

            LegacyBatchTrackSpec.Endpoint primaryEndpoint =
                    c.spec().endpoints().get(0);

            BlockPos anchor = c.spec().offsetForPart(
                    root,
                    c.facing(),
                    primaryEndpoint.part());
            BlockPos clickedSupport = anchor.below();

            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("traincraft", c.id()));

            if (item == null || item == Items.AIR) {
                report.add("CASE index=" + i
                        + " label=" + caseLabel(i, c)
                        + " RESULT=MISSING_ITEM");
                continue;
            }

            fake.setPos(
                    clickedSupport.getX() + 0.5D,
                    clickedSupport.getY() + 2.0D,
                    clickedSupport.getZ() + 4.5D);
            fake.setYRot(c.facing().toYRot());
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
                    new UseOnContext(
                            fake,
                            InteractionHand.MAIN_HAND,
                            hit));

            fake.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY);

            int valid = 0;
            List<String> wrong = new ArrayList<>();

            for (int part = 0; part < c.spec().partCount(); part++) {
                BlockPos railPos = c.spec().offsetForPart(
                        root,
                        c.facing(),
                        part);
                BlockState state = level.getBlockState(railPos);

                if (state.getBlock()
                        instanceof LegacyBatchSwitchRailBlock switchBlock
                        && c.id().equals(switchBlock.getSpec().id())) {
                    valid++;
                } else {
                    wrong.add(part + "@" + railPos);
                }
            }

            // Convert the full correctly placed assembly to ACTIVE only after
            // validating the real item placement.
            if (valid == c.spec().partCount() && c.active()) {
                for (int part = 0; part < c.spec().partCount(); part++) {
                    BlockPos railPos = c.spec().offsetForPart(
                            root,
                            c.facing(),
                            part);
                    BlockState state = level.getBlockState(railPos);

                    if (state.getBlock()
                            instanceof LegacyBatchSwitchRailBlock switchBlock) {
                        level.setBlock(
                                railPos,
                                switchBlock.stateForPart(
                                        c.facing(),
                                        part,
                                        true),
                                3);
                    }
                }
            }

            int activeParts = 0;
            for (int part = 0; part < c.spec().partCount(); part++) {
                BlockPos railPos = c.spec().offsetForPart(
                        root,
                        c.facing(),
                        part);
                BlockState state = level.getBlockState(railPos);

                if (state.hasProperty(LegacyBatchSwitchRailBlock.ACTIVE)
                        && state.getValue(LegacyBatchSwitchRailBlock.ACTIVE)
                        == c.active()) {
                    activeParts++;
                }
            }

            boolean success =
                    result.consumesAction()
                            && valid == c.spec().partCount()
                            && activeParts == c.spec().partCount();

            if (success) {
                successCount++;
            }

            report.add(String.format(Locale.ROOT,
                    "CASE index=%02d row=%d col=%d id=%s facing=%s active=%s"
                            + " root=%s marker=%s parts=%d validParts=%d"
                            + " requestedStateParts=%d useOn=%s success=%s wrong=%s",
                    i,
                    i / 4 + 1,
                    i % 4 + 1,
                    c.id(),
                    c.facing(),
                    c.active(),
                    root,
                    markerBase,
                    c.spec().partCount(),
                    valid,
                    activeParts,
                    result,
                    success,
                    wrong));
        }

        report.add("");
        report.add("SUCCESS_CASES=" + successCount);
        report.add("EXPECTED_CASES=" + CASES.size());
        report.add("RESULT=" + (successCount == CASES.size()
                ? "T6_S4_D2_PARALLEL_SWITCH_VISUAL_CHECK_READY"
                : "T6_S4_D2_PARALLEL_SWITCH_VISUAL_CHECK_REVIEW_REQUIRED"));

        Path reportPath = writeReport(report);

        // View the grid from above. Columns are N/E/S/W.
        double centerX = origin.getX() + (3 * COLUMN_SPACING) / 2.0D;
        double centerZ = origin.getZ() + (3 * ROW_SPACING) / 2.0D;

        player.teleportTo(
                level,
                centerX,
                supportY + 38.0D,
                centerZ - 30.0D,
                180.0F,
                55.0F);

        player.sendSystemMessage(Component.literal(
                "Parallel-switch visual check placed "
                        + successCount + "/" + CASES.size() + " cases."));
        player.sendSystemMessage(Component.literal(
                "Columns left->right: NORTH | EAST | SOUTH | WEST."));
        player.sendSystemMessage(Component.literal(
                "Rows top->bottom: RIGHT inactive | RIGHT active | LEFT inactive | LEFT active."));
        player.sendSystemMessage(Component.literal(
                "CYAN supports are the logical offset/branch cells. "
                        + "The rendered curved branch should follow them."));
        player.sendSystemMessage(Component.literal(
                "Marker base: RED=Right, BLUE=Left. "
                        + "Marker top: IRON=inactive, GOLD=active."));
        player.sendSystemMessage(Component.literal(
                "Report: " + reportPath.toAbsolutePath()));

        return successCount == CASES.size()
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private static String caseLabel(int index, CaseDef c) {
        return "index=" + index
                + " id=" + c.id()
                + " facing=" + c.facing()
                + " active=" + c.active();
    }

    private static BlockPos rootFor(BlockPos origin, int index) {
        int col = index % 4;
        int row = index / 4;
        return origin.offset(
                col * COLUMN_SPACING,
                1,
                row * ROW_SPACING);
    }

    private static BlockPos markerBase(BlockPos root) {
        return root.offset(-6, -1, 5);
    }

    private static Path writeReport(List<String> report) {
        try {
            Path dir = Path.of("logs", "traincraft-track-debug");
            Files.createDirectories(dir);

            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));

            Path file = dir.resolve(
                    "t6-parallel-switch-visual-check-"
                            + stamp
                            + "-"
                            + UUID.randomUUID().toString().substring(0, 8)
                            + ".txt");

            Files.write(file, report, StandardCharsets.UTF_8);
            return file;
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Could not write parallel-switch visual report",
                    ex);
        }
    }
}
