package traincraft.debug;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

import java.util.List;

// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class TrackVariantRegressionCommand {
    private static final List<String> TEST_ITEMS = List.of(
            "track_switch_small_right_energy",
            "track_switch_45_medium_right_energy",
            "track_switch_parallel_right_energy",
            "track_curve_45_medium_right_energy",
            "track_slope_small_energy",
            "track_crossing_x_energy",
            "track_crossing_approach",
            "crossing_gate_base"
    );

    private TrackVariantRegressionCommand() {}

    // TC_DEV38_R1D_COMMAND_DISABLED_RELEASE
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_track_variant_test")
                        .executes(ctx -> giveTestKit(ctx.getSource().getPlayerOrException()))
        );
    }

    private static int giveTestKit(ServerPlayer player) {
        int given = 0;

        for (String id : TEST_ITEMS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Traincraft.MOD_ID, id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                player.sendSystemMessage(Component.literal("[TC test] Missing item: " + id));
                continue;
            }

            ItemStack stack = new ItemStack(item);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            given++;
        }

        player.sendSystemMessage(Component.literal(
                "[TC test] Energy track: inactive must look like normal track; redstone one segment and confirm blue powered effect reaches a connected 20-block run."));
        player.sendSystemMessage(Component.literal(
                "[TC test] Switches: run a train through Small, 45 Medium and Parallel Energy switches on BOTH straight and diverging routes, forward and reverse."));
        player.sendSystemMessage(Component.literal(
                "[TC test] Crossing: place approach detector + crossing signal beside the switch/crossing route. Confirm lights/bell activate, arm lowers, stays down while occupied, then raises after clear."));
        player.sendSystemMessage(Component.literal(
                "[TC test] Tracks Builder: test Rail / Powered Rail / Detector Rail / Activator Rail, then a Tafari normal curve, Steel/Copper/Snow curve, Energy curve and switch."));

        return given;
    }
}
