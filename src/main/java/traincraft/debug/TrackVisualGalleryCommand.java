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
 * Step 9.3g-t5-v1 â€” temporary visual gallery.
 *
 * Uses the real registered track ITEMS and their useOn() code. It intentionally
 * spaces each assembly far enough apart that this gallery shows the individual
 * models/roots without cross-snapping between neighboring examples.
 *
 * Remove before release.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackVisualGalleryCommand {
    private static final int COLUMNS = 4;
    private static final int SPACING = 28;
    private static final int PLATFORM_RADIUS = 12;

    private static final List<String> IDS = List.of(
            "track_curve_45_medium_left",
            "track_curve_45_medium_right",
            "track_curve_45_large_left",
            "track_curve_45_large_right",

            "track_curve_45_very_large_left",
            "track_curve_45_very_large_right",
            "track_curve_45_super_large_left",
            "track_curve_45_super_large_right",

            "track_diagonal_straight_small",
            "track_diagonal_straight_medium",
            "track_switch_45_medium_left",
            "track_switch_45_medium_right",

            "track_diagonal_crossing",
            "track_diagonal_two_ways_crossing",
            "track_diagonal_four_ways_crossing",
            "track_diamond_crossing",

            "track_diamond_crossing_left",
            "track_double_diamond_crossing",
            "track_universal_crossing"
    );

    private TrackVisualGalleryCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_track_gallery")
                        .executes(context -> buildGallery(context.getSource().getPlayerOrException()))
        );
    }

    private static int buildGallery(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        int minY = level.getMinBuildHeight() + 8;
        int maxY = level.getMaxBuildHeight() - 28;
        int floorY = Math.max(minY, Math.min(maxY, player.getBlockY() + 28));

        BlockPos origin = new BlockPos(
                player.getBlockX() + 18,
                floorY,
                player.getBlockZ() + 18);

        // Preflight: do not overwrite an existing build.
        for (int i = 0; i < IDS.size(); i++) {
            BlockPos anchor = anchorFor(origin, i);
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    BlockPos floor = anchor.offset(dx, 0, dz);
                    for (int dy = 0; dy <= 3; dy++) {
                        BlockPos check = floor.above(dy);
                        if (!level.getBlockState(check).isAir()) {
                            player.sendSystemMessage(Component.literal(
                                    "TC gallery aborted: target area is not empty at " + check
                                            + ". Move to an open area and run /tc_track_gallery again."));
                            return 0;
                        }
                    }
                }
            }
        }

        // Build support pads.
        for (int i = 0; i < IDS.size(); i++) {
            BlockPos anchor = anchorFor(origin, i);
            for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
                for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                    BlockPos floor = anchor.offset(dx, 0, dz);
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
            }
        }

        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setYRot(180.0F); // NORTH
        fake.setXRot(0.0F);

        int placed = 0;
        for (int i = 0; i < IDS.size(); i++) {
            String id = IDS.get(i);
            BlockPos support = anchorFor(origin, i);

            Item item = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("traincraft", id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal(
                        "TC gallery missing item: traincraft:" + id));
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
                        "TC gallery placement failed: " + id + " result=" + result));
            }
        }

        int rows = (IDS.size() + COLUMNS - 1) / COLUMNS;
        double centerX = origin.getX() + ((COLUMNS - 1) * SPACING) / 2.0D;
        double centerZ = origin.getZ() + ((rows - 1) * SPACING) / 2.0D;

        player.teleportTo(
                level,
                centerX,
                floorY + 32.0D,
                centerZ,
                180.0F,
                72.0F);

        player.sendSystemMessage(Component.literal(
                "TC visual gallery placed " + placed + "/" + IDS.size()
                        + " tracks. You are above the gallery."));
        player.sendSystemMessage(Component.literal(
                "Row 1: 45 Medium L | 45 Medium R | 45 Large L | 45 Large R"));
        player.sendSystemMessage(Component.literal(
                "Row 2: 45 Very Large L | 45 Very Large R | 45 Super Large L | 45 Super Large R"));
        player.sendSystemMessage(Component.literal(
                "Row 3: Diagonal Small | Diagonal Medium | 45 Switch L | 45 Switch R"));
        player.sendSystemMessage(Component.literal(
                "Row 4: Diagonal Crossing | 2-Way | 4-Way | Diamond"));
        player.sendSystemMessage(Component.literal(
                "Row 5: Diamond Left | Double Diamond | Universal"));

        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos anchorFor(BlockPos origin, int index) {
        int col = index % COLUMNS;
        int row = index / COLUMNS;
        return origin.offset(col * SPACING, 0, row * SPACING);
    }
}