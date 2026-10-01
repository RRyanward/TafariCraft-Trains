package traincraft.client.screen;

// TC_BATCH8_R3ZU_BLUEPRINT_NAMING_POPUP_CLIENT

import net.minecraft.client.Minecraft;

public final class TrackBlueprintNameClient {
    public static void open() {
        Minecraft mc =
                Minecraft.getInstance();

        if (mc.screen != null) {
            return;
        }

        mc.setScreen(
                new TrackBlueprintNameScreen(
                        "Blueprint"));
    }

    private TrackBlueprintNameClient() {}
}
