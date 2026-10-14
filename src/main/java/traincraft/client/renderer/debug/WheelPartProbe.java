package traincraft.client.renderer.debug;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * TC_DEV38_R1G1Z7M2_PASSIVE_REJECTED_OVERLAY_SUPPRESSOR
 *
 * Production-validation-safe replacement for the r1g1z7l2 full-sweep probe.
 *
 * No pulling, blinking, logging, auto-advance, commands, or world mutation.
 * It only keeps the already-rejected experimental Forney/V60 helper overlays
 * hidden while the authored geometry is validated normally.
 */
public final class WheelPartProbe {
    private WheelPartProbe() {}

    public static void applyRenderer(Object renderer, Entity entity) {
        if (renderer == null || entity == null) return;

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !"traincraft".equals(key.getNamespace())) return;

        String id = key.getPath();
        String[] rejected;

        if ("locomotive_steam_forney".equals(id)) {
            rejected = new String[] {
                    "tc_forney_rear_left_0",
                    "tc_forney_rear_left_1",
                    "tc_forney_rear_right_0",
                    "tc_forney_rear_right_1"
            };
        } else if ("locomotive_diesel_v60".equals(id)) {
            rejected = new String[] {
                    "tc_v60_wheel_neg_0",
                    "tc_v60_wheel_neg_1",
                    "tc_v60_wheel_neg_2",
                    "tc_v60_wheel_pos_0",
                    "tc_v60_wheel_pos_1",
                    "tc_v60_wheel_pos_2"
            };
        } else {
            return;
        }

        Object model = rendererModel(renderer);
        ModelPart root = root(model);
        if (root == null) return;

        Map<String, ModelPart> children = children(root);
        if (children.isEmpty()) return;

        for (String name : rejected) {
            ModelPart part = children.get(name);
            if (part != null) {
                part.visible = false;
            }
        }
    }

    private static Object rendererModel(Object renderer) {
        Object named = fieldValue(renderer, "model");
        if (named != null) return named;

        Class<?> type = renderer.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value == null) continue;

                    if (value instanceof EntityModel<?> || value instanceof ModelPart) {
                        return value;
                    }

                    if (root(value) != null) {
                        return value;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue.
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private static Object fieldValue(Object value, String name) {
        if (value == null) return null;

        Class<?> type = value.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        return null;
    }

    private static ModelPart root(Object model) {
        if (model == null) return null;
        if (model instanceof ModelPart part) return part;

        Class<?> type = model.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("root");
                if (ModelPart.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) return part;
                }
            } catch (NoSuchFieldException ignored) {
                // Try generic ModelPart fields below.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try generic ModelPart fields below.
            }

            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) return part;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue.
                }
            }

            type = type.getSuperclass();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart root) {
        Class<?> type = root.getClass();

        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) continue;

                    boolean good = true;
                    for (Map.Entry<?, ?> entry : raw.entrySet()) {
                        if (!(entry.getKey() instanceof String)
                                || !(entry.getValue() instanceof ModelPart)) {
                            good = false;
                            break;
                        }
                    }

                    if (good) {
                        return (Map<String, ModelPart>) raw;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try another Map field.
                }
            }

            type = type.getSuperclass();
        }

        return Map.of();
    }
}