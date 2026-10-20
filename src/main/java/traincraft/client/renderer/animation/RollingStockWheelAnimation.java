package traincraft.client.renderer.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/**
 * DEV38 r1g1f PAGE-1 STEAM wheel calibration layer.
 *
 * This deliberately does not mutate entity motion or train state. It samples
 * the client's interpolated world movement and accumulates wheel phase from
 * signed distance along the train's facing direction.
 *
 * The current port contains several rendering architectures (ModelPart,
 * recovered models, exact baked meshes, legacy TCM). r1g1f keeps the safe
 * rail-anchored animated wheel-face layer across all of them without rotating
 * a whole body mesh. Existing body geometry remains untouched.
 *
 * Steam rods/cranks are intentionally deferred until wheel placement is
 * runtime-proven.
 */
public final class RollingStockWheelAnimation {
    private static final int RIM_SEGMENTS = 24;
    private static final int SPOKES = 6;
    private static final double TELEPORT_CUTOFF = 1.25D;
    private static final double MIN_MOVE = 0.00001D;

    private static final Map<UUID, MotionState> MOTION = new HashMap<>();
    private static final Map<Class<?>, Method> YAW_METHODS = new HashMap<>();
    private static final Map<Class<?>, Boolean> NO_YAW_METHOD = new HashMap<>();

    private RollingStockWheelAnimation() {}

    // TC_DEV38_R1G1Q1_NATIVE_WHEEL_PHASE_API
    /**
     * Client-only wheel phase derived from actual signed horizontal travel.
     * Native renderer/model wheel parts should consume this value.
     */
    public static double sampleNativePhase(Entity entity, float partialTick) {
        final double dev54h2Phase = traincraft.debug.Dev54H2StationaryFleet.sampleOverrideOrNaN(entity, partialTick);
        if (Double.isFinite(dev54h2Phase)) return dev54h2Phase;
        // TC_DEV38_R1G1Z7Y18C_STATIONARY_PHASE_OVERRIDE_DEBUG_ONLY
        // SINGLE-ENTITY, INTEGRATED-CLIENT ONLY. Original algorithm untouched below.
        final double tc_y18c_debug_phase =
                traincraft.debug.Dev38StationaryRunningGearTest.sampleOverrideOrNaN(entity, partialTick);
        if (Double.isFinite(tc_y18c_debug_phase)) return tc_y18c_debug_phase;
        if (entity == null || entity.isInvisible()) return 0.0D;

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !Traincraft.MOD_ID.equals(key.getNamespace())) return 0.0D;

        String id = key.getPath();
        Profile profile = profile(id, entity);
        float railYaw = trainYaw(entity, partialTick);
        double phase = updatePhase(entity, partialTick, railYaw, profile.radius);

        // Preserve the runtime-proven steam direction correction.
        if (id.startsWith("locomotive_steam_")) {
            phase = -phase;
        }

        if (MOTION.size() > 1024) {
            MOTION.clear();
        }
        return phase;
    }

    /**
     * Compatibility entry point for the existing fleet hook.
     *
     * DEV38 r1g1q1 intentionally draws NO synthetic wheel geometry.
     * Keeping this method means we do not have to guess or alter the caller:
     * wherever the old overlay hook lives, it now only advances phase state.
     */
    public static void render(
            Entity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers) {
        sampleNativePhase(entity, partialTick);
    }
    private static double updatePhase(
            Entity entity,
            float partialTick,
            float railYaw,
            double radius) {

        double x = Mth.lerp((double) partialTick, entity.xo, entity.getX());
        double z = Mth.lerp((double) partialTick, entity.zo, entity.getZ());

        MotionState state = MOTION.computeIfAbsent(
                entity.getUUID(),
                ignored -> new MotionState(x, z));

        double dx = x - state.x;
        double dz = z - state.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance >= MIN_MOVE && distance <= TELEPORT_CUTOFF) {
            double yawRad = Math.toRadians(railYaw);
            double forwardX = -Math.sin(yawRad);
            double forwardZ =  Math.cos(yawRad);
            double along = dx * forwardX + dz * forwardZ;

            /*
             * On a sharp curve the interpolated render-facing yaw can briefly
             * be nearly perpendicular to the movement chord. Preserve the
             * previous sign rather than allowing a one-frame wheel reversal.
             */
            if (Math.abs(along) < distance * 0.08D) {
                along = Math.copySign(distance, state.lastSign);
            } else {
                state.lastSign = along < 0.0D ? -1.0D : 1.0D;
            }

            state.phase += along / Math.max(0.08D, radius);
            state.phase = wrapRadians(state.phase);
        }

        state.x = x;
        state.z = z;
        return state.phase;
    }

    private static double wrapRadians(double value) {
        double tau = Math.PI * 2.0D;
        value %= tau;
        if (value > Math.PI) value -= tau;
        if (value < -Math.PI) value += tau;
        return value;
    }

    private static float trainYaw(Entity entity, float partialTick) {
        Class<?> type = entity.getClass();

        Method cached = YAW_METHODS.get(type);
        if (cached != null) {
            try {
                Object result = cached.invoke(entity, partialTick);
                if (result instanceof Number number) {
                    return number.floatValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through to vanilla yaw below.
            }
        } else if (!NO_YAW_METHOD.containsKey(type)) {
            Method found = findYawMethod(type);
            if (found != null) {
                YAW_METHODS.put(type, found);
                try {
                    Object result = found.invoke(entity, partialTick);
                    if (result instanceof Number number) {
                        return number.floatValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall through.
                }
            } else {
                NO_YAW_METHOD.put(type, Boolean.TRUE);
            }
        }

        return Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
    }

    private static Method findYawMethod(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method =
                        cursor.getDeclaredMethod("getRenderTrainFacingYaw", float.class);
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

    private static Profile profile(String id, Entity entity) {
        boolean steamLoco = id.startsWith("locomotive_steam");
        boolean powered =
                id.startsWith("locomotive_diesel")
                || id.startsWith("locomotive_electric");
        boolean builder = "tracks_builder".equals(id);
        boolean tender = id.startsWith("steam_tender");
        boolean car =
                id.contains("freight")
                || id.contains("passenger")
                || id.contains("caboose")
                || id.contains("baggage")
                || id.contains("observatory")
                || id.contains("tail_car")
                || id.contains("livestock")
                || id.contains("transport_car");

        if (steamLoco) {
            double radius = steamRadius(id);
            double centerY = radius + steamCenterYOffset(id);
            double side = steamSide(id, entity);
            return new Profile(
                    steamHalfLength(id),
                    steamCenterX(id),
                    side,
                    centerY,
                    radius,
                    steamAxles(id));
        }

        double half = resolvedHalfLength(entity, fallbackHalfLength(id));

        if (powered || builder) {
            double radius = poweredRadius(id);
            double side = poweredSide(id, entity);
            double centerX = powered ? poweredCenterX(id) : 0.0D;
            double tunedHalf = poweredHalfLength(id, half);
            return new Profile(
                    tunedHalf,
                    centerX,
                    side,
                    radius + poweredCenterYOffset(id),
                    radius,
                    poweredAxles(id));
        }

        if (tender || car) {
            double radius = 0.225D;
            double side = utilitySide(id, entity);
            return new Profile(
                    half,
                    0.0D,
                    side,
                    radius + utilityCenterYOffset(id),
                    radius,
                    new double[] {-0.66D, -0.43D, 0.43D, 0.66D});
        }

        // Any future Traincraft rolling stock automatically receives the safe
        // utility-car wheel layout rather than silently having no animation.
        double radius = 0.225D;
        return new Profile(
                half,
                0.0D,
                utilitySide(id, entity),
                radius + utilityCenterYOffset(id),
                radius,
                new double[] {-0.62D, 0.62D});
    }

    /**
     * r1g1k longitudinal center corrections measured from the complete
     * one-stock-per-frame r1g1j capture. These move ONLY the independent
     * animated wheel layer; accepted body transforms remain untouched.
     */
    private static double steamCenterX(String id) {
        return switch (id) {
            case "locomotive_steam_262t" -> -0.65D;
            case "locomotive_steam_4_4_0" -> -0.45D;
            case "locomotive_steam_a4_mallard" -> -1.70D;
            case "locomotive_steam_adler" -> -0.30D;
            case "locomotive_steam_alco_sc4" -> -1.10D;
            case "locomotive_steam_alice_0_4_0" -> -0.55D;
            case "locomotive_steam_berkshire_1225" -> -1.40D;
            case "locomotive_steam_berkshire_765" -> -1.40D;
            case "locomotive_steam_br01" -> -1.20D;
            case "locomotive_steam_br80" -> -0.35D;
            case "locomotive_steam_br_black_5" -> -0.85D;
            case "locomotive_steam_br_britannia" -> -1.15D;
            case "locomotive_steam_c11" -> -0.85D;
            case "locomotive_steam_c41" -> -1.10D;
            case "locomotive_steam_c41_080" -> -1.00D;
            case "locomotive_steam_c41t" -> -1.00D;
            case "locomotive_steam_c62" -> -1.85D;
            case "locomotive_steam_cherepanov" -> -0.35D;
            case "locomotive_steam_climax" -> -0.40D;
            case "locomotive_steam_climax_2" -> 0.00D;
            case "locomotive_steam_coronation_class" -> -2.20D;
            case "locomotive_steam_d51" -> -1.90D;
            case "locomotive_steam_d51_long" -> -1.95D;
            case "locomotive_steam_forney" -> -0.60D;
            case "locomotive_steam_fowler" -> -0.95D;
            case "locomotive_steam_glyn_042t" -> -0.55D;
            case "locomotive_steam_gs4" -> -2.20D;
            case "locomotive_steam_gwr_101_class" -> -0.70D;
            case "locomotive_steam_gwr_42xx" -> -1.40D;
            case "locomotive_steam_gwr_72xx" -> -1.70D;
            case "locomotive_steam_hall_class" -> -1.45D;
            case "locomotive_steam_heavy" -> -0.90D;
            case "locomotive_steam_j50" -> -0.65D;
            case "locomotive_steam_king_class" -> -1.75D;
            case "locomotive_steam_lssp7" -> -0.25D;
            case "locomotive_steam_midland_compound" -> -1.10D;
            case "locomotive_steam_milw_class_a" -> -1.50D;
            case "locomotive_steam_mogul" -> -0.40D;
            case "locomotive_steam_pannier" -> -0.95D;
            case "locomotive_steam_pe" -> -0.80D;
            case "locomotive_steam_rw_type_2" -> -0.60D;
            case "locomotive_steam_rw_type_3" -> -0.65D;
            case "locomotive_steam_s100_uk" -> -0.55D;
            case "locomotive_steam_s100_us" -> -0.55D;
            case "locomotive_steam_sentinel_y3" -> -0.20D;
            case "locomotive_steam_shay" -> -0.25D;
            case "locomotive_steam_shay_3_truck" -> -0.50D;
            case "locomotive_steam_skookum" -> 0.20D;
            case "locomotive_steam_small" -> 0.00D;
            case "locomotive_steam_snow_plow" -> -1.55D;
            case "locomotive_steam_southern1102" -> -1.10D;
            case "locomotive_steam_star_class" -> -0.35D;
            case "locomotive_steam_u57" -> 0.00D;
            case "locomotive_steam_ussr" -> -1.05D;
            case "locomotive_steam_vb_0_4_0" -> -0.25D;
            case "locomotive_steam_vb_shay" -> -0.20D;
            case "locomotive_steam_vb_shay_2" -> -0.20D;
            case "locomotive_steam_wwcp_062t" -> -1.35D;
            default -> 0.0D;
        };
    }

    private static double poweredCenterX(String id) {
        return switch (id) {
            case "locomotive_diesel_cd742" -> -0.95D;
            case "locomotive_diesel_chme3" -> -1.25D;
            case "locomotive_diesel_gp7" -> -1.00D;
            case "locomotive_diesel_kof" -> -0.85D;
            case "locomotive_diesel_sd40" -> -1.20D;
            case "locomotive_diesel_sd70" -> -1.20D;
            case "locomotive_diesel_shunter" -> -0.90D;
            case "locomotive_diesel_v60" -> -1.15D;
            case "locomotive_electric_br_e69" -> 0.00D;
            case "locomotive_electric_high_speed_zeroed" -> -1.00D;
            case "locomotive_electric_minetrain" -> -0.80D;
            case "locomotive_electric_tram" -> -1.00D;
            case "locomotive_electric_tram_ny" -> -1.15D;
            case "locomotive_electric_vl10" -> -1.05D;
            default -> 0.0D;
        };
    }
    private static double steamSide(String id, Entity entity) {
        // r1g1c used a >=0.72 plane and the Page-1 videos showed the wheel
        // faces floating well outside the locomotives. Keep the marker just
        // outside the minecart-sized physics body and clamp it for consistency.
        double hinted = entity.getBbWidth() * 0.5D + 0.015D;
                double base = Mth.clamp(hinted, 0.50D, 0.58D);
        return switch (id) {
            case "locomotive_steam_262t",
                 "locomotive_steam_adler",
                 "locomotive_steam_alice_0_4_0",
                 "locomotive_steam_br80",
                 "locomotive_steam_lssp7",
                 "locomotive_steam_small",
                 "locomotive_steam_vb_0_4_0" -> Math.min(base, 0.53D);
            default -> base;
        };
    }


    // TC_DEV38_R1G1N2_SELECTIVE_WHEEL_HEIGHT_TUNE
    private static double steamCenterYOffset(String id) {
        return switch (id) {
            case "locomotive_steam_262t" -> 0.018D;
            case "locomotive_steam_a4_mallard" -> 0.014D;
            case "locomotive_steam_adler" -> 0.015D;
            case "locomotive_steam_alco_sc4" -> 0.018D;
            case "locomotive_steam_alice_0_4_0" -> 0.000D;
            case "locomotive_steam_small" -> 0.018D;
            default -> 0.040D;
        };
    }

    // TC_DEV38_R1G1P2_POWERED_WHEEL_GEOMETRY
    // r1g1o2 ruler pass: keep the proven travel/phase renderer, but stop
    // forcing every powered vehicle into the same four-axle geometry.
    private static double poweredRadius(String id) {
        return switch (id) {
            case "locomotive_diesel_kof" -> 0.240D;
            case "locomotive_diesel_shunter",
                 "locomotive_diesel_v60" -> 0.230D;
            case "locomotive_electric_br_e69" -> 0.220D;
            case "locomotive_electric_minetrain" -> 0.180D;
            default -> 0.270D;
        };
    }

    private static double poweredHalfLength(String id, double base) {
        return switch (id) {
            case "locomotive_diesel_kof" -> 1.400D;
            case "locomotive_electric_br_e69" -> 0.660D;
            case "locomotive_electric_minetrain" -> 0.300D;
            default -> base;
        };
    }

    private static double[] poweredAxles(String id) {
        return switch (id) {
            case "locomotive_diesel_kof" ->
                    new double[] {-0.70D, 0.70D};

            case "locomotive_electric_br_e69",
                 "locomotive_electric_minetrain" ->
                    new double[] {-1.00D, 1.00D};

            case "locomotive_diesel_chme3",
                 "locomotive_diesel_sd40",
                 "locomotive_diesel_sd70" ->
                    new double[] {
                            -0.74D, -0.58D, -0.42D,
                             0.42D,  0.58D,  0.74D
                    };

            case "locomotive_diesel_shunter",
                 "locomotive_diesel_v60" ->
                    new double[] {-0.68D, 0.00D, 0.68D};

            default ->
                    new double[] {-0.68D, -0.44D, 0.44D, 0.68D};
        };
    }
    private static double poweredSide(String id, Entity entity) {
        double base = Math.max(
                0.70D,
                entity.getBbWidth() * 0.5D + 0.12D);
        return switch (id) {
            case "locomotive_diesel_kof" -> 0.48D;
            case "locomotive_electric_br_e69" -> 0.46D;
            case "locomotive_electric_minetrain" -> 0.42D;
            case "locomotive_electric_tram",
                 "locomotive_electric_tram_ny" -> 0.58D;
            case "tracks_builder" -> 0.60D;
            default -> base;
        };
    }

    private static double poweredCenterYOffset(String id) {
        return switch (id) {
            case "locomotive_diesel_kof" -> -0.030D;
            case "locomotive_electric_br_e69" -> -0.420D;
            case "locomotive_electric_minetrain" -> -0.450D;
            case "locomotive_electric_tram",
                 "locomotive_electric_tram_ny" -> 0.020D;
            case "tracks_builder" -> 0.030D;
            default -> 0.050D;
        };
    }

    private static double utilitySide(String id, Entity entity) {
        double base = Math.max(
                0.68D,
                entity.getBbWidth() * 0.5D + 0.10D);
        return switch (id) {
            case "milw_baggage_car",
                 "milw_passenger_car",
                 "milw_tail_car",
                 "passenger_coach_blue" -> Math.min(base, 0.60D);
            default -> base;
        };
    }

    private static double utilityCenterYOffset(String id) {
        return switch (id) {
            case "caboose" -> 0.010D;
            case "gs4_baggage_car",
                 "gs4_observatory_car",
                 "gs4_passenger_car",
                 "gs4_tail_car" -> 0.025D;
            case "milw_baggage_car",
                 "milw_passenger_car",
                 "milw_tail_car" -> -0.020D;
            case "passenger_coach_blue" -> 0.000D;
            default -> 0.045D;
        };
    }
    private static double steamRadius(String id) {
        return switch (id) {
            case "locomotive_steam_a4_mallard",
                 "locomotive_steam_berkshire_1225",
                 "locomotive_steam_berkshire_765",
                 "locomotive_steam_br_britannia",
                 "locomotive_steam_br01",
                 "locomotive_steam_c62",
                 "locomotive_steam_coronation_class",
                 "locomotive_steam_d51",
                 "locomotive_steam_d51_long",
                 "locomotive_steam_gs4",
                 "locomotive_steam_heavy",
                 "locomotive_steam_king_class",
                 "locomotive_steam_u57" -> 0.290D;

            case "locomotive_steam_adler",
                 "locomotive_steam_alice_0_4_0",
                 "locomotive_steam_br80",
                 "locomotive_steam_cherepanov",
                 "locomotive_steam_climax",
                 "locomotive_steam_climax_2",
                 "locomotive_steam_forney",
                 "locomotive_steam_glyn_042t",
                 "locomotive_steam_j50",
                 "locomotive_steam_lssp7",
                 "locomotive_steam_pannier",
                 "locomotive_steam_rw_type_2",
                 "locomotive_steam_rw_type_3",
                 "locomotive_steam_s100_uk",
                 "locomotive_steam_s100_us",
                 "locomotive_steam_sentinel_y3",
                 "locomotive_steam_shay",
                 "locomotive_steam_shay_3_truck",
                 "locomotive_steam_small",
                 "locomotive_steam_vb_0_4_0",
                 "locomotive_steam_vb_shay",
                 "locomotive_steam_vb_shay_2" -> 0.215D;

            default -> 0.250D;
        };
    }

    private static double steamHalfLength(String id) {
        // Page-1 correction: these are wheel-group half extents, derived from
        // the current legacy mesh horizontal bounds where available. They
        // deliberately do not use coupler reach, which caused long/short steam
        // engines to share visibly wrong wheel spacing in r1g1c.
        return switch (id) {
            case "locomotive_steam_262t" -> 1.850D;
            case "locomotive_steam_4_4_0" -> 1.332D;
            case "locomotive_steam_a4_mallard" -> 3.088D;
            case "locomotive_steam_adler" -> 0.928D;
            case "locomotive_steam_alco_sc4" -> 2.271D;
            case "locomotive_steam_alice_0_4_0" -> 1.525D;
            case "locomotive_steam_berkshire_1225",
                 "locomotive_steam_berkshire_765" -> 3.050D;
            case "locomotive_steam_br_black_5" -> 1.865D;
            case "locomotive_steam_br_britannia" -> 2.166D;
            case "locomotive_steam_br01" -> 2.450D;
            case "locomotive_steam_br80" -> 1.400D;
            case "locomotive_steam_c11" -> 1.938D;
            case "locomotive_steam_c41" -> 2.126D;
            case "locomotive_steam_c41_080" -> 2.170D;
            case "locomotive_steam_c41t" -> 2.270D;
            case "locomotive_steam_c62" -> 2.800D;
            case "locomotive_steam_cherepanov" -> 0.950D;
            case "locomotive_steam_climax" -> 1.900D;
            case "locomotive_steam_climax_2" -> 1.861D;
            case "locomotive_steam_coronation_class" -> 3.138D;
            case "locomotive_steam_d51",
                 "locomotive_steam_d51_long" -> 2.725D;
            case "locomotive_steam_forney" -> 1.550D;
            case "locomotive_steam_fowler" -> 1.726D;
            case "locomotive_steam_glyn_042t" -> 1.600D;
            case "locomotive_steam_gs4" -> 3.175D;
            case "locomotive_steam_gwr_101_class" -> 1.625D;
            case "locomotive_steam_gwr_42xx" -> 2.375D;
            case "locomotive_steam_gwr_72xx" -> 2.675D;
            case "locomotive_steam_hall_class" -> 2.475D;
            case "locomotive_steam_heavy" -> 2.550D;
            case "locomotive_steam_j50" -> 1.938D;
            case "locomotive_steam_king_class" -> 2.763D;
            case "locomotive_steam_lssp7" -> 0.850D;
            case "locomotive_steam_midland_compound" -> 2.175D;
            case "locomotive_steam_milw_class_a" -> 2.738D;
            case "locomotive_steam_mogul" -> 1.900D;
            case "locomotive_steam_pannier" -> 2.600D;
            case "locomotive_steam_pe" -> 2.050D;
            case "locomotive_steam_rw_type_2" -> 1.325D;
            case "locomotive_steam_rw_type_3" -> 1.700D;
            case "locomotive_steam_s100_uk" -> 1.603D;
            case "locomotive_steam_s100_us" -> 1.600D;
            case "locomotive_steam_sentinel_y3" -> 1.225D;
            case "locomotive_steam_shay" -> 1.900D;
            case "locomotive_steam_shay_3_truck" -> 1.450D;
            case "locomotive_steam_skookum" -> 2.300D;
            case "locomotive_steam_small" -> 1.350D;
            case "locomotive_steam_snow_plow" -> 2.300D;
            case "locomotive_steam_southern1102" -> 2.101D;
            case "locomotive_steam_star_class" -> 1.256D;
            case "locomotive_steam_u57" -> 2.400D;
            case "locomotive_steam_ussr" -> 2.400D;
            case "locomotive_steam_vb_0_4_0" -> 1.000D;
            case "locomotive_steam_vb_shay" -> 2.200D;
            case "locomotive_steam_vb_shay_2" -> 1.831D;
            case "locomotive_steam_wwcp_062t" -> 2.325D;
            default -> 1.600D;
        };
    }

    private static double[] steamAxles(String id) {
        int count = switch (id) {
            case "locomotive_steam_alice_0_4_0",
                 "locomotive_steam_cherepanov",
                 "locomotive_steam_sentinel_y3",
                 "locomotive_steam_vb_0_4_0" -> 2;

            case "locomotive_steam_adler",
                 "locomotive_steam_br80",
                 "locomotive_steam_fowler",
                 "locomotive_steam_glyn_042t",
                 "locomotive_steam_gwr_101_class",
                 "locomotive_steam_j50",
                 "locomotive_steam_lssp7",
                 "locomotive_steam_pannier",
                 "locomotive_steam_rw_type_2",
                 "locomotive_steam_s100_uk",
                 "locomotive_steam_s100_us",
                 "locomotive_steam_small" -> 3;

            case "locomotive_steam_4_4_0",
                 "locomotive_steam_c41_080",
                 "locomotive_steam_climax",
                 "locomotive_steam_climax_2",
                 "locomotive_steam_forney",
                 "locomotive_steam_mogul",
                 "locomotive_steam_rw_type_3",
                 "locomotive_steam_shay",
                 "locomotive_steam_vb_shay",
                 "locomotive_steam_vb_shay_2",
                 "locomotive_steam_wwcp_062t" -> 4;

            case "locomotive_steam_262t",
                 "locomotive_steam_alco_sc4",
                 "locomotive_steam_br_black_5",
                 "locomotive_steam_c41",
                 "locomotive_steam_c41t",
                 "locomotive_steam_gwr_42xx",
                 "locomotive_steam_hall_class",
                 "locomotive_steam_king_class",
                 "locomotive_steam_midland_compound",
                 "locomotive_steam_milw_class_a",
                 "locomotive_steam_pe",
                 "locomotive_steam_southern1102",
                 "locomotive_steam_star_class",
                 "locomotive_steam_ussr" -> 5;

            case "locomotive_steam_a4_mallard",
                 "locomotive_steam_br_britannia",
                 "locomotive_steam_br01",
                 "locomotive_steam_c11",
                 "locomotive_steam_coronation_class",
                 "locomotive_steam_d51",
                 "locomotive_steam_d51_long",
                 "locomotive_steam_gwr_72xx",
                 "locomotive_steam_heavy",
                 "locomotive_steam_shay_3_truck",
                 "locomotive_steam_skookum",
                 "locomotive_steam_u57" -> 6;

            case "locomotive_steam_berkshire_1225",
                 "locomotive_steam_berkshire_765",
                 "locomotive_steam_c62" -> 7;

            case "locomotive_steam_gs4" -> 8;

            default -> 4;
        };

        return evenAxles(count);
    }

    private static double[] evenAxles(int count) {
        return switch (count) {
            case 2 -> new double[] {-1.00D, 1.00D};
            case 3 -> new double[] {-1.00D, 0.00D, 1.00D};
            case 4 -> new double[] {-1.00D, -0.34D, 0.34D, 1.00D};
            case 5 -> new double[] {-1.00D, -0.50D, 0.00D, 0.50D, 1.00D};
            case 6 -> new double[] {-1.00D, -0.60D, -0.20D, 0.20D, 0.60D, 1.00D};
            case 7 -> new double[] {-1.00D, -0.67D, -0.33D, 0.00D, 0.33D, 0.67D, 1.00D};
            case 8 -> new double[] {-1.00D, -0.71D, -0.43D, -0.14D, 0.14D, 0.43D, 0.71D, 1.00D};
            default -> new double[] {-0.70D, 0.70D};
        };
    }

    private static double fallbackHalfLength(String id) {
        if (id.contains("passenger")
                || id.contains("baggage")
                || id.contains("observatory")
                || id.contains("tail_car")) {
            return 1.55D;
        }
        if (id.startsWith("locomotive_diesel")
                || id.startsWith("locomotive_electric")) {
            return 1.70D;
        }
        if (id.startsWith("locomotive_steam")) {
            return 1.55D;
        }
        if (id.startsWith("steam_tender")) {
            return 1.25D;
        }
        return 1.15D;
    }

    private static double resolvedHalfLength(Entity entity, double fallback) {
        Double leading = invokeReach(entity, "getLeaderFacingCouplerReach");
        Double trailing = invokeReach(entity, "getTrailingCouplerReach");

        double resolved = fallback;
        if (leading != null && Double.isFinite(leading) && leading > 0.1D) {
            resolved = Math.max(resolved, Math.abs(leading));
        }
        if (trailing != null && Double.isFinite(trailing) && trailing > 0.1D) {
            resolved = Math.max(resolved, Math.abs(trailing));
        }

        return Mth.clamp(resolved, 0.80D, 3.00D);
    }

    private static Double invokeReach(Entity entity, String methodName) {
        Class<?> cursor = entity.getClass();
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(entity);
                if (result instanceof Number number) {
                    return number.doubleValue();
                }
                return null;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void drawWheelPair(
            VertexConsumer out,
            Matrix4f matrix,
            Matrix3f normal,
            double x,
            double y,
            double side,
            double radius,
            double phase) {

        // Offset the line layer slightly outward so the animated face is not
        // coplanar with the old static wheel texture/mesh.
        drawWheel(out, matrix, normal, x, y,  side + 0.008D, radius, phase,  1.0F);
        drawWheel(out, matrix, normal, x, y, -side - 0.008D, radius, phase, -1.0F);
    }

    private static void drawWheel(
            VertexConsumer out,
            Matrix4f matrix,
            Matrix3f normal,
            double cx,
            double cy,
            double z,
            double radius,
            double phase,
            float normalZ) {

        // Dark rim.
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0D * i) / RIM_SEGMENTS;
            double a1 = (Math.PI * 2.0D * (i + 1)) / RIM_SEGMENTS;
            line(
                    out, matrix, normal,
                    cx + Math.cos(a0) * radius,
                    cy + Math.sin(a0) * radius,
                    z,
                    cx + Math.cos(a1) * radius,
                    cy + Math.sin(a1) * radius,
                    z,
                    36, 36, 36, 255, normalZ);
        }

        // Bright rotating spokes. These are the actual visible motion cue.
        for (int i = 0; i < SPOKES; i++) {
            double a = phase + (Math.PI * 2.0D * i) / SPOKES;
            double inner = radius * 0.18D;
            double outer = radius * 0.92D;
            line(
                    out, matrix, normal,
                    cx + Math.cos(a) * inner,
                    cy + Math.sin(a) * inner,
                    z,
                    cx + Math.cos(a) * outer,
                    cy + Math.sin(a) * outer,
                    z,
                    188, 188, 188, 255, normalZ);
        }

        // Short rotating counter-spoke cross makes reverse rotation easy to see.
        double marker = phase + Math.PI / 6.0D;
        line(
                out, matrix, normal,
                cx,
                cy,
                z,
                cx + Math.cos(marker) * radius * 0.72D,
                cy + Math.sin(marker) * radius * 0.72D,
                z,
                232, 232, 232, 255, normalZ);
    }

    private static void line(
            VertexConsumer out,
            Matrix4f matrix,
            Matrix3f normal,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int r,
            int g,
            int b,
            int a,
            float normalZ) {

        out.vertex(matrix, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a)
                .normal(normal, 0.0F, 0.0F, normalZ)
                .endVertex();

        out.vertex(matrix, (float) x2, (float) y2, (float) z2)
                .color(r, g, b, a)
                .normal(normal, 0.0F, 0.0F, normalZ)
                .endVertex();
    }

    private record Profile(
            double halfLength,
            double centerX,
            double side,
            double centerY,
            double radius,
            double[] axles) {}

    private static final class MotionState {
        double x;
        double z;
        double phase;
        double lastSign = 1.0D;

        MotionState(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
