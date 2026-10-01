/*
 * Traincraft 1.20.1 key mappings.
 * Distributed under LGPL-v3.0.
 */
package traincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client key bindings matching the classic Traincraft controls. */
public final class TCKeyMappings {
    public static final KeyMapping OPEN_LOCOMOTIVE_GUI = new KeyMapping(
            "key.traincraft.open_locomotive_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.traincraft"
    );

    public static final KeyMapping HAND_BRAKE = new KeyMapping(
            "key.traincraft.hand_brake",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.traincraft"
    );

    public static final KeyMapping WHISTLE = new KeyMapping(
            "key.traincraft.whistle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.traincraft"
    );

    private TCKeyMappings() {
    }
}
