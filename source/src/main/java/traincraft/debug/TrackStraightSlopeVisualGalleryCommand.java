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
 * Step 9.3g-t5-d23 â€” temporary straight-slope visual gallery.
 *
 * Visual-only diagnostic:
 * - Small / Long / Very Long straight slopes.
 * - Uses the real registered items and their useOn() path.
 * - Builds the required support under the raised far endpoint so the slope
 *   assembly is not invalidated by missing support.
 * - Does not alter placement, routing, models, blockstates, or rolling stock.
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackStraightSlopeVisualGalleryCommand {
    private static final int SPACING_Z = 34;
    private static final int HALF_WIDTH = 4;

    private record SlopeCase(String id, String label, int blocks) {
    }

    private static final List<SlopeCase> CASES = List.of(
            new SlopeCase("track_slope_small", "Small Slope", 6),
            new SlopeCase("track_slope_long", "Long Slope", 12),
            new SlopeCase("track_slope_very_long", "Very Long Slope", 18)
    );

    private TrackStraightSlopeVisualGalleryCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_straight_slope_gallery")
                        .executes(context ->
                                buildGallery(context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 20;
        int floorY = Math.max(minY, Math.min(maxY, player.getBlockY() + 18));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 12,
                floorY,
                player.getBlockZ() + 16);

        // Preflight the exact three runway volumes. Do not overwrite builds.
        for (int i = 0; i < CASES.size(); i++) {
            SlopeCase test = CASES.get(i);
            BlockPos anchor = anchorFor(origin, i);

            for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
                for (int z = -test.blocks() - 3; z <= 3; z++) {
                    for (int y = 0; y <= 4; y++) {
                        BlockPos check = anchor.offset(x, y, z);
                        if (!level.getBlockState(check).isAir()) {
                            player.sendSystemMessage(Component.literal(
                                    "TC straight-slope gallery aborted: target area is not empty at "
                                            + check
                                            + ". Move to a fresh open area and run "
                                            + "/tc_straight_slope_gallery again."));
                            return 0;
                        }
                    }
                }
            }
        }

        // Build narrow isolated support runways.
        //
        // Canonical NORTH straight-slope guides:
        //   p0..p(n-2): y=0
        //   p(n-1):    y=1
        //
        // Since item placement puts p0 one block above clicked support, all
        // ordinary cells need support at floorY and the final raised endpoint
        // needs an extra support block at floorY+1.
        for (int i = 0; i < CASES.size(); i++) {
            SlopeCase test = CASES.get(i);
            BlockPos anchor = anchorFor(origin, i);

            for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
                for (int z = -test.blocks() - 2; z <= 2; z++) {
                    level.setBlock(
                            anchor.offset(x, 0, z),
                            Blocks.SMOOTH_STONE.defaultBlockState(),
                            2);
                }
            }

            BlockPos farSupport = anchor.offset(0, 1, -(test.blocks() - 1));
            level.setBlock(
                    farSupport,
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    2);
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setXRot(0.0F);
        fake.setYRot(180.0F);

        int placed = 0;
        for (int i = 0; i < CASES.size(); i++) {
            SlopeCase test = CASES.get(i);
            BlockPos support = anchorFor(origin, i);

            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("traincraft", test.id()));
            if (item == null || item == Items.AIR) {
                player.sendSystemMessage(Component.literal(
                        "TC straight-slope gallery missing item: traincraft:"
                                + test.id()));
                continue;
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

            if (result.consumesAction()) {
                placed++;
            } else {
                player.sendSystemMessage(Component.literal(
                        "TC straight-slope gallery placement failed: "
                                + test.label()
                                + " result=" + result));
            }
        }

        // Put the player to the east side and slightly above the middle slope.
        // This gives an immediate side-biased view of the one-block rise.
        BlockPos middle = anchorFor(origin, 1);
        player.teleportTo(
                level,
                middle.getX() + 14.0D,
                floorY + 7.0D,
                middle.getZ() - 5.0D,
                90.0F,
                24.0F);

        player.sendSystemMessage(Component.literal(
                "TC straight-slope visual gallery placed "
                        + placed + "/" + CASES.size() + " tracks."));
        player.sendSystemMessage(Component.literal(
                "North row: Small | middle row: Long | south row: Very Long"));
        player.sendSystemMessage(Component.literal(
                "Check from the SIDE as well as overhead: smooth rail deck, "
                        + "one-block total rise, no floating/detached end."));
        player.sendSystemMessage(Component.literal(
                "Visual test only. Traversal remains deferred to the headless harness."));

        return placed == CASES.size() ? Command.SINGLE_SUCCESS : 0;
    }

    private static BlockPos anchorFor(BlockPos origin, int index) {
        return origin.offset(0, 0, index * SPACING_Z);
    }
}
