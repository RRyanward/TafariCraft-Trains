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

import java.util.List;

/**
 * Step 9.3g-t5-d21 â€” temporary Parallel/S-curve visual gallery.
 *
 * Visual-only diagnostic:
 * - places the six parallel curve pieces through their real registered items;
 * - keeps Left/Right variants side-by-side for direct comparison;
 * - does not alter placement, routing, models, blockstates, or rolling stock.
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackParallelVisualGalleryCommand {
    private static final int COLUMNS = 2;
    private static final int SPACING = 52;
    private static final int PLATFORM_RADIUS = 20;

    private static final List<String> IDS = List.of(
            "track_parallel_curve_small_left",
            "track_parallel_curve_small_right",
            "track_parallel_curve_medium_left",
            "track_parallel_curve_medium_right",
            "track_parallel_curve_large_left",
            "track_parallel_curve_large_right"
    );

    private TrackParallelVisualGalleryCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_parallel_gallery")
                        .executes(context ->
                                buildGallery(context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 40;
        int floorY = Math.max(minY, Math.min(maxY, player.getBlockY() + 28));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 26,
                floorY,
                player.getBlockZ() + 26);

        // Do not overwrite anything. The support area is deliberately larger
        // than the 3x15 canonical Large parallel footprint.
        for (int i = 0; i < IDS.size(); i++) {
            BlockPos anchor = anchorFor(origin, i);
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    BlockPos floor = anchor.offset(dx, 0, dz);
                    for (int dy = 0; dy <= 4; dy++) {
                        BlockPos check = floor.above(dy);
                        if (!level.getBlockState(check).isAir()) {
                            player.sendSystemMessage(Component.literal(
                                    "TC parallel gallery aborted: target area is not empty at "
                                            + check
                                            + ". Move to a fresh open area and run "
                                            + "/tc_parallel_gallery again."));
                            return 0;
                        }
                    }
                }
            }
        }

        // Build isolated support pads.
        for (int i = 0; i < IDS.size(); i++) {
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
        fake.setYRot(180.0F);
        fake.setXRot(0.0F);

        int placed = 0;
        for (int i = 0; i < IDS.size(); i++) {
            String id = IDS.get(i);
            BlockPos support = anchorFor(origin, i);

            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("traincraft", id));
            if (item == null || item == Items.AIR) {
                player.sendSystemMessage(Component.literal(
                        "TC parallel gallery missing item: traincraft:" + id));
                continue;
            }

            fake.setPos(
                    support.getX() + 0.5D,
                    support.getY() + 2.0D,
                    support.getZ() + 4.5D);
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

            if (result.consumesAction()) {
                placed++;
            } else {
                player.sendSystemMessage(Component.literal(
                        "TC parallel gallery placement failed: "
                                + id + " result=" + result));
            }
        }

        int rows = (IDS.size() + COLUMNS - 1) / COLUMNS;
        double centerX =
                origin.getX() + ((COLUMNS - 1) * SPACING) / 2.0D;
        double centerZ =
                origin.getZ() + ((rows - 1) * SPACING) / 2.0D;

        player.teleportTo(
                level,
                centerX,
                floorY + 44.0D,
                centerZ,
                180.0F,
                72.0F);

        player.sendSystemMessage(Component.literal(
                "TC parallel visual gallery placed "
                        + placed + "/" + IDS.size()
                        + " tracks. You are above the gallery."));
        player.sendSystemMessage(Component.literal(
                "Row 1: Parallel Small LEFT | Parallel Small RIGHT"));
        player.sendSystemMessage(Component.literal(
                "Row 2: Parallel Medium LEFT | Parallel Medium RIGHT"));
        player.sendSystemMessage(Component.literal(
                "Row 3: Parallel Large LEFT | Parallel Large RIGHT"));
        player.sendSystemMessage(Component.literal(
                "Visual test only. Traversal remains deferred to the headless harness."));

        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos anchorFor(BlockPos origin, int index) {
        int col = index % COLUMNS;
        int row = index / COLUMNS;
        return origin.offset(col * SPACING, 0, row * SPACING);
    }
}
