package traincraft.debug;

// TC_DEV34_R1C1A_TEMP_INFRASTRUCTURE_PROFILE_HEADLESS

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.blueprint.BuilderStructurePlan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TEMP r1c1a profile-only acceptance test.
 * Auto-runs on dedicated server start; no commands/player/world mutation.
 *
 * Confirms the accepted ordinary-block train-clearance height is unchanged:
 * flat relative Y4 is blocked and relative Y5 is safe.
 *
 * Builder roadbed one block below the rail is handled by the infrastructure
 * exception, not by relaxing the general ordinary-block safety profile.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev34InfrastructureProfileHeadless {
    private Dev34InfrastructureProfileHeadless() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }

        List<String> report = new ArrayList<>();

        ItemStack blueprint = synthetic32x();

        boolean y4 =
                BuilderStructurePlan.tooCloseRelative(
                        blueprint,
                        new BlockPos(0, 4, 0));

        boolean y5 =
                BuilderStructurePlan.tooCloseRelative(
                        blueprint,
                        new BlockPos(0, 5, 0));

        boolean pass =
                BuilderStructurePlan.CLEARANCE_ABOVE == 4.25D
                        && y4
                        && !y5;

        report.add("TAFARICRAFT DEV34 r1c1a INFRASTRUCTURE PROFILE");
        report.add("CLEARANCE_ABOVE=" + BuilderStructurePlan.CLEARANCE_ABOVE);
        report.add("flat-relative-Y4-blocked=" + y4);
        report.add("flat-relative-Y5-blocked=" + y5);
        report.add("EXPECTED=Y4 blocked, Y5 safe => accepted 4.75 effective clearance preserved");
        report.add("STATUS=" + (pass
                ? "HEADLESS_INFRASTRUCTURE_PROFILE_PASS"
                : "HEADLESS_INFRASTRUCTURE_PROFILE_FAIL"));

        try {
            Path dir = Path.of("logs", "traincraft-dev34-headless");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path file = dir.resolve(
                    "infrastructure-profile-autotest-" + stamp + ".txt");
            Files.write(file, report, StandardCharsets.UTF_8);
            System.out.println(
                    "[TC-DEV34-INFRASTRUCTURE] "
                            + report.get(report.size() - 1)
                            + " REPORT="
                            + file.toAbsolutePath());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static ItemStack synthetic32x() {
        ItemStack blueprint = new ItemStack(Items.PAPER);

        CompoundTag segment = new CompoundTag();
        segment.putInt("X", 0);
        segment.putInt("Y", 0);
        segment.putInt("Z", 0);
        segment.putString("Item", "traincraft:track_curve_32x");
        segment.putFloat("Yaw", Direction.WEST.toYRot());
        segment.putBoolean("Left", false);

        ListTag segments = new ListTag();
        segments.add(segment);

        CompoundTag data = new CompoundTag();
        data.put("Segments", segments);

        CompoundTag root = new CompoundTag();
        root.put("TafariTrackBlueprint", data);
        blueprint.setTag(root);

        return blueprint;
    }
}

