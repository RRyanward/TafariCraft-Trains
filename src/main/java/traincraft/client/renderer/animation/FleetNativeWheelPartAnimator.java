package traincraft.client.renderer.animation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

/**
 * TC_DEV38_R1G1Z3A_BULK_NATIVE_WHEELS
 * TC_DEV38_R1G1Z4D_DEFERRED_BATCH_A
 * TC_DEV38_R1G1Z5C_AUTHORED_WHEEL_FACE_SPLIT_A
 *
 * Existing authored ModelParts only. No synthetic wheel geometry.
 * Pivot compensation keeps each audited geometric wheel center fixed.
 */
public final class FleetNativeWheelPartAnimator {
    private static final Map<Object, Map<String, ModelPart>> CACHE = new IdentityHashMap<>();

    // TC_DEV38_R1G1Z7N_FOCUSED_PHASE
    // Both unresolved locomotives have exact 9px / 0.5625-block main drivers.
    // radius = 4.5 / 16 = 0.28125 block.
    private static final double FOCUSED_DRIVER_RADIUS = 0.28125D;
    private static final double FOCUSED_TELEPORT_CUTOFF = 1.25D;
    private static final double FOCUSED_MIN_MOVE = 0.00001D;
    private static final Map<UUID, FocusPhaseState> FOCUSED_PHASE = new HashMap<>();
    private static final Map<Class<?>, Method> FOCUSED_YAW_METHODS = new HashMap<>();
    private static final Map<Class<?>, Boolean> FOCUSED_NO_YAW_METHOD = new HashMap<>();

    private FleetNativeWheelPartAnimator() {}

    public static void apply(Object renderer, Entity entity, float partialTick) {
        if (renderer == null || entity == null) return;

        // TC_DEV38_R1G1Z6A_REJECTED_OVERLAY_SUPPRESSOR
        // Audit behavior is disabled; only rejected r1g1z5c Forney/V60
        // temporary overlay children are suppressed.
        traincraft.client.renderer.debug.WheelPartProbe.applyRenderer(
                renderer,
                entity);

        float phase = (float) RollingStockWheelAnimation.sampleNativePhase(entity, partialTick);

        // TC_DEV38_R1G1Z7W5_STRUCTURAL_FACE_CONTEXT
        // Reuse production phase; never sample signed distance twice.
        StructuralWheelFaceOverlayRenderer.capture(entity, phase);

        // TC_DEV38_R1G1Z7N_FOCUSED_PHASE_SAMPLE
        String focusedEntityId = entityId(entity);
        float focusedPhase = focusedPhase(
                entity,
                partialTick,
                focusedEntityId,
                phase);

        // TC_DEV38_R1G1Z6A_BAKED_NATIVE_WHEEL_CONTEXT
        BakedWheelAnimationContext.begin(entity, focusedPhase);
        String rendererName = renderer.getClass().getSimpleName();
        Object model = rendererModel(renderer);
        if (model == null) return;

        // TC_DEV38_R1G1Z7Y3_PREPARE_AUTHORED_ALPHA_PANELS
        // Suppress a native source panel only after UV/alpha proof.
        StructuralWheelFaceOverlayRenderer.prepareNative(focusedEntityId, model);

        // TC_DEV38_R1G1Z7Y1_PREPARE_AUTHORED_ALPHA_PANELS
        // Suppress a native source panel only after UV/alpha proof.
        StructuralWheelFaceOverlayRenderer.prepareNative(focusedEntityId, model);

        // TC_DEV38_R1G1Z7N_FORNEY_ENTITY_DISPATCH
        // Forney is a native ModelPart locomotive. Use registry identity as
        // authoritative dispatch and preserve its existing exact part mapping.
        if ("locomotive_steam_forney".equals(focusedEntityId)) {
            applyForney(model, -focusedPhase);

            // TC_DEV38_R1G1Z7P3_FORNEY_PAIRED_WHEEL_PANEL_SPLIT
            // Hide only the four proven unsplit 1x6x14 carrier panels. Their
            // exact source polygons/UVs are rendered as 6+2+6 regions by the
            // renderer helper after the normal model pass.
            AuthoredPairedWheelPanelRenderer.prepareForney(model);
            return;
        }

        switch (rendererName) {
            case "ForneySteamLocomotiveRenderer" -> applyForney(model, -phase);
            case "HeavySteamLocomotiveRenderer" -> applyHeavySteam(model, phase);
            case "HeavySteamTenderRenderer" -> applyHeavyTender(model, phase);
            case "MogulSteamLocomotiveRenderer" -> applyMogul(model, -phase);
            case "ShaySteamLocomotiveRenderer" -> applyShay(model, phase);
            case "RecoveredPoweredLocomotiveRenderer" -> applyRecovered(model, phase);
            default -> { }
        }


    }

    private static String entityId(Entity entity) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                        .getKey(entity.getType());
        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return "";
        }
        return key.getPath();
    }

    private static float focusedPhase(
            Entity entity,
            float partialTick,
            String entityId,
            float sharedPhase) {

        if (!"locomotive_steam_4_4_0".equals(entityId)
                && !"locomotive_steam_forney".equals(entityId)) {
            return sharedPhase;
        }

        double x = net.minecraft.util.Mth.lerp(
                (double) partialTick,
                entity.xo,
                entity.getX());
        double z = net.minecraft.util.Mth.lerp(
                (double) partialTick,
                entity.zo,
                entity.getZ());

        FocusPhaseState state = FOCUSED_PHASE.computeIfAbsent(
                entity.getUUID(),
                ignored -> new FocusPhaseState(x, z));

        double dx = x - state.x;
        double dz = z - state.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance >= FOCUSED_MIN_MOVE
                && distance <= FOCUSED_TELEPORT_CUTOFF) {

            float railYaw = focusedYaw(entity, partialTick);
            double yawRad = Math.toRadians(railYaw);
            double forwardX = -Math.sin(yawRad);
            double forwardZ =  Math.cos(yawRad);
            double along = dx * forwardX + dz * forwardZ;

            if (Math.abs(along) < distance * 0.08D) {
                along = Math.copySign(distance, state.lastSign);
            } else {
                state.lastSign = along < 0.0D ? -1.0D : 1.0D;
            }

            state.phase += along / FOCUSED_DRIVER_RADIUS;
            state.phase = wrapFocusedRadians(state.phase);
        }

        state.x = x;
        state.z = z;

        if (FOCUSED_PHASE.size() > 128) {
            FOCUSED_PHASE.clear();
        }

        // Match RollingStockWheelAnimation.sampleNativePhase steam convention.
        return (float) -state.phase;
    }

    private static float focusedYaw(Entity entity, float partialTick) {
        Class<?> type = entity.getClass();

        Method cached = FOCUSED_YAW_METHODS.get(type);
        if (cached != null) {
            try {
                Object result = cached.invoke(entity, partialTick);
                if (result instanceof Number number) {
                    return number.floatValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through.
            }
        } else if (!FOCUSED_NO_YAW_METHOD.containsKey(type)) {
            Method found = findFocusedYawMethod(type);
            if (found != null) {
                FOCUSED_YAW_METHODS.put(type, found);
                try {
                    Object result = found.invoke(entity, partialTick);
                    if (result instanceof Number number) {
                        return number.floatValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall through.
                }
            } else {
                FOCUSED_NO_YAW_METHOD.put(type, Boolean.TRUE);
            }
        }

        return net.minecraft.util.Mth.rotLerp(
                partialTick,
                entity.yRotO,
                entity.getYRot());
    }

    private static Method findFocusedYawMethod(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method =
                        cursor.getDeclaredMethod(
                                "getRenderTrainFacingYaw",
                                float.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double wrapFocusedRadians(double value) {
        double tau = Math.PI * 2.0D;
        value %= tau;
        if (value > Math.PI) value -= tau;
        if (value < -Math.PI) value += tau;
        return value;
    }

    private static final class FocusPhaseState {
        private double x;
        private double z;
        private double phase;
        private double lastSign = 1.0D;

        private FocusPhaseState(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static void applyRecovered(Object model, float phase) {
        switch (model.getClass().getSimpleName()) {
            case "RecoveredModelGP40" -> applyRecoveredGp40(model, phase);
            case "RecoveredModelSD40" -> applyRecoveredSd40(model, phase);
            case "RecoveredModelSD70" -> applyRecoveredSd70(model, phase);
            case "RecoveredModelV60" -> applyRecoveredV60(model, phase);
            // TC_DEV38_R1G1Z7E_TRAM_NY_EXACT_WHEEL_REENABLE
            case "RecoveredModelTramNY" -> applyRecoveredTramNy(model, phase);
            default -> { }
        }
    }

    private static void applyForney(Object model, float phase) {
        // Existing four 9x9 main driver faces - runtime confirmed spinning.
        spinX(model, "part_020", -6.0F, 0.0F, -10.0F, 4.5F, 4.5F, phase);
        spinX(model, "part_021", -6.0F, 0.0F, 1.0F, 4.5F, 4.5F, phase);
        spinX(model, "part_024", 5.0F, 0.0F, 1.0F, 4.5F, 4.5F, phase);
        spinX(model, "part_025", 5.0F, 0.0F, -10.0F, 4.5F, 4.5F, phase);

        // TC_DEV38_R1G1Z7F_FORNEY_AUTHORED_6X6_PAIR
        // Genuine source-authored 1x6x6 side wheel faces at the same Y plane.
        // 6px wheel diameter vs 9px main driver => 1.5x angular speed.
        spinX(model, "part_000", -6.0F, 0.0F, -22.0F, 3.0F, 3.0F, phase * 1.5F);
        spinX(model, "part_001",  5.0F, 0.0F, -22.0F, 3.0F, 3.0F, phase * 1.5F);

        // r1g1z5c: actual rear side-panel wheel faces.
        // 6px rear wheels vs 9px drivers => 4.5 / 3.0 = 1.5x angle.
        spinX(model, "tc_forney_rear_left_0", -7.02F, 0.0F, 15.0F, 3.0F, 3.0F, phase * 1.5F);
        spinX(model, "tc_forney_rear_left_1", -7.02F, 0.0F, 23.0F, 3.0F, 3.0F, phase * 1.5F);
        spinX(model, "tc_forney_rear_right_0", 7.0F, 0.0F, 15.0F, 3.0F, 3.0F, phase * 1.5F);
        spinX(model, "tc_forney_rear_right_1", 7.0F, 0.0F, 23.0F, 3.0F, 3.0F, phase * 1.5F);
    }
    private static void applyHeavySteam(Object model, float phase) {
        spinZ(model, "part_000", -34F, -1F, -7F, 5.5F, 5.5F, phase);
        spinZ(model, "part_003", -34F, -1F, 6F, 5.5F, 5.5F, phase);
        spinZ(model, "part_014", -22F, -1F, 6F, 5.5F, 5.5F, phase);
        spinZ(model, "part_018", -10F, -1F, 6F, 5.5F, 5.5F, phase);
        spinZ(model, "part_034", -22F, -1F, -7F, 5.5F, 5.5F, phase);
        spinZ(model, "part_046", -10F, -1F, -7F, 5.5F, 5.5F, phase);
        spinZ(model, "part_001", -55F, -1F, -7F, 4F, 4F, phase);
        spinZ(model, "part_002", -43F, -1F, -7F, 4F, 4F, phase);
        spinZ(model, "part_050", -55F, -1F, 6F, 4F, 4F, phase);
        spinZ(model, "part_051", -43F, -1F, 6F, 4F, 4F, phase);
    }

    private static void applyHeavyTender(Object model, float phase) {
        spinZ(model, "part_001", -18F, -1F, -7F, 3F, 3F, phase);
        spinZ(model, "part_002", -11F, -1F, -7F, 3F, 3F, phase);
        spinZ(model, "part_013", -11F, -1F, 6F, 3F, 3F, phase);
        spinZ(model, "part_022", -18F, -1F, 6F, 3F, 3F, phase);
        spinZ(model, "part_023", 13F, -1F, -7F, 3F, 3F, phase);
        spinZ(model, "part_024", 6F, -1F, -7F, 3F, 3F, phase);
        spinZ(model, "part_025", 6F, -1F, 6F, 3F, 3F, phase);
        spinZ(model, "part_026", 13F, -1F, 6F, 3F, 3F, phase);
    }

    private static void applyMogul(Object model, float phase) {
        spinX(model, "part_020", -6F, 0F, -6F, 4.5F, 4.5F, phase);
        spinX(model, "part_021", -6F, 0F, 4F, 4.5F, 4.5F, phase);
        spinX(model, "part_024", 5F, 0F, 4F, 4.5F, 4.5F, phase);
        spinX(model, "part_025", 5F, 0F, -6F, 4.5F, 4.5F, phase);
        spinX(model, "part_072", -6F, 0F, -16F, 4.5F, 4.5F, phase);
        spinX(model, "part_073", 5F, 0F, -16F, 4.5F, 4.5F, phase);
        spinX(model, "part_000", -6F, 0F, -24F, 3F, 3F, phase);
        spinX(model, "part_001", 5F, 0F, -24F, 3F, 3F, phase);
    }

    private static void applyShay(Object model, float phase) {
        spinZ(model, "part_004", -24F, 0F, 5F, 3F, 3F, phase);
        spinZ(model, "part_006", -15F, 0F, 5F, 3F, 3F, phase);
        spinZ(model, "part_026", 9F, 0F, -5F, 3F, 3F, phase);
        spinZ(model, "part_027", 18F, 0F, -5F, 3F, 3F, phase);
        spinZ(model, "part_028", 18F, 0F, 5F, 3F, 3F, phase);
        spinZ(model, "part_029", 9F, 0F, 5F, 3F, 3F, phase);
        spinZ(model, "part_059", -15F, 0F, -5F, 3F, 3F, phase);
        spinZ(model, "part_066", -24F, 0F, -5F, 3F, 3F, phase);
    }

    private static void applyRecoveredGp40(Object model, float phase) {
        spinZ(model, "Shape17", -15F, 18F, 7F, 3F, 3F, phase);
        spinZ(model, "Shape18", -4F, 18F, -7F, 3F, 3F, phase);
        spinZ(model, "Shape43", -15F, 18F, -7F, 3F, 3F, phase);
        spinZ(model, "Shape46", -4F, 18F, 7F, 3F, 3F, phase);
        spinZ(model, "Shape49", 28F, 18F, 7F, 3F, 3F, phase);
        spinZ(model, "Shape67", 17F, 18F, -7F, 3F, 3F, phase);
        spinZ(model, "Shape70", 17F, 18F, 7F, 3F, 3F, phase);
        spinZ(model, "Shape8", 28F, 18F, -7F, 3F, 3F, phase);
    }

    private static void applyRecoveredHighSpeed(Object model, float phase) {
        spinZ(model, "box1", -19F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box16", -31F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box19", 23F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box21", 23F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box35", 11F, 0F, 4F, 4F, 3.5F, phase);
        spinZ(model, "box4", -31F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box5", 11F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box63", -19F, 0F, -5F, 4F, 3.5F, phase);
    }

    private static void applyRecoveredTram(Object model, float phase) {
        spinZ(model, "box1", -16F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box35", 8F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box5", 8F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box63", -16F, 0F, -5F, 4F, 3.5F, phase);
    }

    private static void applyRecoveredTramNy(Object model, float phase) {
        spinZ(model, "box0", -31F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box1", -17F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box16", -31F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box19", 23F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box21", 23F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box35", 9F, 0F, 5F, 4F, 3.5F, phase);
        spinZ(model, "box5", 9F, 0F, -5F, 4F, 3.5F, phase);
        spinZ(model, "box63", -17F, 0F, -5F, 4F, 3.5F, phase);
    }

    // DEV38 r1g1z4d: six low authored axle assemblies; model length axis = Z,
    // therefore axle axis = X.
    private static void applyRecoveredSd40(Object model, float phase) {
        // r1g1z5c: visible authored wheel faces only.
        // The old box1/box6/box7/box8/box14/box15 axle rods are intentionally static.
        spinX(model, "tc_wheel_left_f0", -6.02F, 0.0F, -30.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_f1", -6.02F, 0.0F, -23.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_f2", -6.02F, 0.0F, -16.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r0", -6.02F, 0.0F, 10.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r1", -6.02F, 0.0F, 17.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r2", -6.02F, 0.0F, 24.0F, 3.0F, 3.0F, phase);

        spinX(model, "tc_wheel_right_f0", 6.0F, 0.0F, -30.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_f1", 6.0F, 0.0F, -23.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_f2", 6.0F, 0.0F, -16.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r0", 6.0F, 0.0F, 10.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r1", 6.0F, 0.0F, 17.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r2", 6.0F, 0.0F, 24.0F, 3.0F, 3.0F, phase);
    }
    private static void applyRecoveredSd70(Object model, float phase) {
        // r1g1z5c: visible authored wheel faces only.
        // The old box1/box6/box7/box8/box14/box15 axle rods are intentionally static.
        spinX(model, "tc_wheel_left_f0", -6.02F, 0.0F, -30.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_f1", -6.02F, 0.0F, -23.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_f2", -6.02F, 0.0F, -16.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r0", -6.02F, 0.0F, 10.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r1", -6.02F, 0.0F, 17.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_left_r2", -6.02F, 0.0F, 24.0F, 3.0F, 3.0F, phase);

        spinX(model, "tc_wheel_right_f0", 6.0F, 0.0F, -30.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_f1", 6.0F, 0.0F, -23.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_f2", 6.0F, 0.0F, -16.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r0", 6.0F, 0.0F, 10.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r1", 6.0F, 0.0F, 17.0F, 3.0F, 3.0F, phase);
        spinX(model, "tc_wheel_right_r2", 6.0F, 0.0F, 24.0F, 3.0F, 3.0F, phase);
    }
    private static void applyRecoveredV60(Object model, float phase) {
        // r1g1z5c: visible authored wheel-row faces only.
        // The old box2/box3/box68 axle bars are intentionally static.
        spinZ(model, "tc_v60_wheel_neg_0", -19.0F, 2.0F, -4.02F, 4.0F, 4.0F, phase);
        spinZ(model, "tc_v60_wheel_neg_1", -7.0F, 2.0F, -4.02F, 4.0F, 4.0F, phase);
        spinZ(model, "tc_v60_wheel_neg_2", 11.0F, 2.0F, -4.02F, 4.0F, 4.0F, phase);

        spinZ(model, "tc_v60_wheel_pos_0", -19.0F, 2.0F, 4.0F, 4.0F, 4.0F, phase);
        spinZ(model, "tc_v60_wheel_pos_1", -7.0F, 2.0F, 4.0F, 4.0F, 4.0F, phase);
        spinZ(model, "tc_v60_wheel_pos_2", 11.0F, 2.0F, 4.0F, 4.0F, 4.0F, phase);
    }
    private static void spinZ(Object model, String name, float px, float py, float pz, float cx, float cy, float phase) {
        ModelPart part = resolve(model, name);
        if (part == null) return;
        float c = (float) Math.cos(phase);
        float s = (float) Math.sin(phase);
        part.x = px + cx - (c * cx - s * cy);
        part.y = py + cy - (s * cx + c * cy);
        part.z = pz;
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = phase;
    }

    private static void spinX(Object model, String name, float px, float py, float pz, float cy, float cz, float phase) {
        ModelPart part = resolve(model, name);
        if (part == null) return;
        float c = (float) Math.cos(phase);
        float s = (float) Math.sin(phase);
        part.x = px;
        part.y = py + cy - (c * cy - s * cz);
        part.z = pz + cz - (s * cy + c * cz);
        part.xRot = phase;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }

    private static Object rendererModel(Object renderer) {
        Object named = fieldValue(renderer, "model");
        if (named != null) return named;
        Class<?> type = renderer.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> ft = field.getType();
                if (!ModelPart.class.isAssignableFrom(ft) && !EntityModel.class.isAssignableFrom(ft)) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value != null) return value;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ModelPart resolve(Object model, String name) {
        Map<String, ModelPart> byName = CACHE.computeIfAbsent(model, ignored -> new java.util.HashMap<>());
        if (byName.containsKey(name)) return byName.get(name);
        ModelPart root;
        if (model instanceof ModelPart part) {
            root = part;
        } else {
            ModelPart direct = directModelPartField(model, name);
            if (direct != null) { byName.put(name, direct); return direct; }
            root = directModelPartField(model, "root");
            if (root == null) root = firstModelPartField(model);
        }
        ModelPart child = child(root, name);
        if (child != null) byName.put(name, child);
        return child;
    }

    private static Object fieldValue(Object object, String wanted) {
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(wanted);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static ModelPart directModelPartField(Object object, String wanted) {
        Object value = fieldValue(object, wanted);
        return value instanceof ModelPart part ? part : null;
    }

    private static ModelPart firstModelPartField(Object object) {
        Class<?> type = object.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value instanceof ModelPart part) return part;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ModelPart child(ModelPart root, String name) {
        if (root == null) return null;
        try { return root.getChild(name); }
        catch (RuntimeException ignored) { return null; }
    }
}
